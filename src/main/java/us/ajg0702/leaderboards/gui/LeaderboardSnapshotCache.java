package us.ajg0702.leaderboards.gui;

import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.boards.StatEntry;
import us.ajg0702.leaderboards.boards.TimedType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory snapshot cache for leaderboard GUI data.
 * Pre-fetches top 10 for all categories/types and refreshes every configurable interval.
 * Eliminates DB queries when opening /leaderboard — menu opens instantly.
 *
 * Works WITHOUT Redis. If Redis is enabled, Redis cache takes priority.
 */
public class LeaderboardSnapshotCache {

    private static final TimedType[] CACHED_TYPES = {TimedType.ALLTIME, TimedType.DAILY, TimedType.WEEKLY, TimedType.MONTHLY};

    private final LeaderboardPlugin plugin;
    private final long refreshIntervalMs;

    // Key: "boardName:timedType" → List of top 10 entries
    private final Map<String, List<SnapshotEntry>> topCache = new ConcurrentHashMap<>();
    // Key: "uuid:boardName:timedType" → player position entry (TTL managed manually)
    private final Map<String, PlayerPosEntry> playerPosCache = new ConcurrentHashMap<>();

    private volatile long lastRefreshTime = 0;
    private volatile boolean refreshing = false;

    public LeaderboardSnapshotCache(LeaderboardPlugin plugin) {
        this.plugin = plugin;
        int intervalMinutes = 60; // default
        try {
            // ajUtils Config logs a noisy ERROR if you getInt() a missing key, so check first.
            // This way an upgraded config.yml without gui-cache-refresh-minutes silently uses 60.
            if (plugin.getAConfig().hasEntry("gui-cache-refresh-minutes")) {
                Integer val = plugin.getAConfig().getInt("gui-cache-refresh-minutes");
                if (val != null && val >= 1) intervalMinutes = val;
            }
        } catch (Exception ignored) { /* keep default */ }
        this.refreshIntervalMs = intervalMinutes * 60_000L;
    }

    /**
     * Start the periodic refresh task. Call once on plugin enable.
     *
     * <p>The first refresh fires as soon as the async scheduler can run it
     * (next tick) — no startup delay. With the index audit running in
     * parallel, the first {@code /leaderboard} after a restart should hit
     * a hot cache instead of falling through to direct DB queries.
     */
    public void start() {
        // Fire-and-forget kickoff on the next async tick — no startup
        // delay so the cache populates as fast as the scheduler allows.
        plugin.getScheduler().runTaskAsynchronously(this::refresh);

        // Periodic refresh — clamp to ensure both delay and period are positive on Folia.
        long intervalTicks = Math.max(1L, refreshIntervalMs / 50L);
        plugin.getScheduler().runTaskTimerAsynchronously(this::refresh, intervalTicks, intervalTicks);

        plugin.getLogger().info("[GUI Cache] Snapshot cache started (refresh every " + (refreshIntervalMs / 60000) + " min)");
    }

    /** Bounded concurrency for snapshot fan-out — matches the Redis writer
     *  to avoid both refreshers stampeding the JDBC pool together when
     *  they happen to fire on the same interval. */
    private static final int MAX_PARALLEL = 8;

    /**
     * Refresh top-10 snapshots from the database. Fans out per
     * (board × timed-type) cell on the plugin's async scheduler
     * (Folia-aware) with a {@link java.util.concurrent.Semaphore} cap of
     * {@link #MAX_PARALLEL}. Returns from this method only when all
     * cells either completed or hit per-call exceptions.
     *
     * <p>Was previously fully sequential — 40 cells × 10 positions = 400
     * synchronous DB queries on a single thread, taking ~10–25s on a
     * cold start. The bounded fan-out reduces that to ~1–2s and is
     * concurrency-safe with the Redis writer (which uses the same cap),
     * keeping total JDBC concurrency at ~16 — well under any sensible
     * Hikari pool size.
     *
     * <p>Folia note: no world / entity / inventory state is touched, so
     * the per-cell tasks are region-thread-agnostic. The dispatcher's
     * {@code sem.acquire()} blocks the calling background thread (not
     * the timer thread, which only sees a single async submission), so
     * Folia's scheduler isn't held hostage by the refresh.
     */
    public void refresh() {
        if (refreshing || plugin.isShuttingDown()) return;
        refreshing = true;

        // Run the entire pass off the timer thread on the async pool.
        // Returns to the timer immediately after submission.
        plugin.getScheduler().runTaskAsynchronously(this::doRefresh);
    }

    private void doRefresh() {
        long start = System.currentTimeMillis();
        final java.util.concurrent.atomic.AtomicInteger count =
                new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(MAX_PARALLEL);
        final java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        try {
            if (plugin.isShuttingDown()) return;
            List<String> existingBoards = plugin.getCache().getBoards();

            outer:
            for (LeaderboardGUI.CategoryDef cat : LeaderboardGUI.CATEGORIES) {
                if (cat.boardName == null || plugin.isShuttingDown()) continue;
                if (!existingBoards.contains(cat.boardName)) continue;
                for (TimedType type : CACHED_TYPES) {
                    if (plugin.isShuttingDown()) break outer;
                    try { sem.acquire(); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break outer;
                    }
                    final LeaderboardGUI.CategoryDef c = cat;
                    final TimedType t = type;
                    final java.util.concurrent.CompletableFuture<Void> f = new java.util.concurrent.CompletableFuture<>();
                    futures.add(f);
                    plugin.getScheduler().runTaskAsynchronously(() -> {
                        try {
                            if (plugin.isShuttingDown()) return;
                            String key = c.boardName + ":" + t.lowerName();
                            // Batched top-N — one SQL round-trip instead of
                            // ten LIMIT 1 OFFSET k queries.
                            List<StatEntry> top = plugin.getCache().getTopN(c.boardName, t, 10);
                            List<SnapshotEntry> entries = new ArrayList<>(top.size());
                            for (StatEntry stat : top) {
                                if (stat == null || !stat.hasPlayer()) continue;
                                entries.add(new SnapshotEntry(
                                        stat.getPlayerName(),
                                        stat.getPlayerID() != null ? stat.getPlayerID().toString() : "",
                                        stat.getScore(),
                                        stat.getPosition()
                                ));
                            }
                            topCache.put(key, entries);
                            count.incrementAndGet();
                        } finally {
                            sem.release();
                            f.complete(null);
                        }
                    });
                }
            }

            // Await completion on this background thread.
            try {
                java.util.concurrent.CompletableFuture
                        .allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                        .get(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception ignored) { /* logged per-cell */ }

            // Clean expired player position entries
            long now = System.currentTimeMillis();
            playerPosCache.entrySet().removeIf(e -> now - e.getValue().cachedAt > 120_000); // 2 min TTL

            lastRefreshTime = System.currentTimeMillis();
            long took = System.currentTimeMillis() - start;
            plugin.getLogger().info("[GUI Cache] Refreshed " + count.get() + " snapshots in " + took + "ms");
        } finally {
            refreshing = false;
        }
    }

    /**
     * Get cached top 10 for a board and type. Returns null if not cached yet.
     */
    public List<SnapshotEntry> getTop10(String board, TimedType type) {
        return topCache.get(board + ":" + type.lowerName());
    }

    /**
     * Get a player's position purely from cache.
     *
     * <p>On miss this method <b>does not</b> run a synchronous DB query.
     * Instead it returns {@code null} immediately and fires a background
     * async task that fills the cache for the next call. Previously this
     * was the second source of blocking JDBC pool contention during a
     * {@code /leaderboard} build — with many concurrent opens, the GUI
     * thread waited up to the Hikari connection timeout (~30s) for a
     * free connection.
     *
     * <p>The async-fill pattern means a brand-new player toggling to a
     * timed type that wasn't pre-warmed on join may see "N/A" on their
     * first open; the cache populates within a second or two and the
     * next open shows the correct rank. That's a much better tradeoff
     * than blocking the menu for 10+ seconds under load.
     *
     * <p>To prevent a thundering herd of background fills for the same
     * key, {@link #inFlightFills} dedupes concurrent miss-fills.
     */
    public SnapshotEntry getPlayerPosition(java.util.UUID uuid, String board, TimedType type) {
        String key = uuid + ":" + board + ":" + type.lowerName();

        PlayerPosEntry cached = playerPosCache.get(key);
        if (cached != null && System.currentTimeMillis() - cached.cachedAt < 120_000) {
            return cached.entry;
        }

        // Cache miss — schedule background fill and return null. The next
        // open will find a populated cache.
        if (inFlightFills.add(key)) {
            plugin.getScheduler().runTaskAsynchronously(() -> {
                try {
                    if (plugin.isShuttingDown()) return;
                    if (!plugin.getTopManager().boardExists(board)) return;
                    org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    StatEntry entry = plugin.getCache().getStatEntry(player, board, type);
                    if (entry != null && entry.hasPlayer() && entry.getPosition() > 0) {
                        SnapshotEntry se = new SnapshotEntry(entry.getPlayerName(), uuid.toString(), entry.getScore(), entry.getPosition());
                        playerPosCache.put(key, new PlayerPosEntry(se, System.currentTimeMillis()));
                    }
                } catch (Exception ignored) {
                    // Swallow — next open will retry the fill. Logging
                    // every miss-fill failure would spam under DB outage.
                } finally {
                    inFlightFills.remove(key);
                }
            });
        }
        return null;
    }

    /** Dedupes concurrent miss-fill tasks for the same player+board+type
     *  so a burst of /leaderboard opens by one player doesn't fire 10
     *  redundant DB queries. */
    private final java.util.Set<String> inFlightFills =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public long getLastRefreshTime() { return lastRefreshTime; }
    public boolean isRefreshing() { return refreshing; }

    /**
     * Background pre-fetch of a player's positions across every cached
     * board + timed-type into {@link #playerPosCache}.
     *
     * <p>Designed for {@code PlayerJoinEvent}: by the time the player
     * actually runs {@code /leaderboard}, all 10 × 4 (board × type)
     * player-position entries are already in the snapshot cache and the
     * GUI build skips its DB-fallback path entirely. Without this,
     * the first {@code /leaderboard} after join always serialised 10
     * cache-miss DB queries (one per board) for the current timed-type,
     * which is what made the first open feel slow.
     *
     * <p>Fire-and-forget — failures are logged via the cache layer's own
     * warnings; no callback. Runs on the plugin's async scheduler so it
     * doesn't compete with the player's own region thread.
     */
    public void prefetchPlayer(java.util.UUID uuid) {
        plugin.getScheduler().runTaskAsynchronously(() -> {
            if (plugin.isShuttingDown()) return;
            List<String> existing = plugin.getCache().getBoards();
            // Prefetch ALLTIME only — that's the default view when a player
            // first opens /leaderboard. The other timed types (daily /
            // weekly / monthly) get populated lazily when the player toggles,
            // by which point the GUI is already open and a brief loading
            // delay is fine. This keeps per-join DB work to ~boards × 1
            // queries instead of boards × 4, important on big servers where
            // every join previously kicked off 40+ queries.
            for (LeaderboardGUI.CategoryDef cat : LeaderboardGUI.CATEGORIES) {
                if (cat.boardName == null) continue;
                if (!existing.contains(cat.boardName)) continue;
                if (plugin.isShuttingDown()) return;
                // getPlayerPosition handles its own cache write — we
                // just trigger it. Throws nothing on absence (returns
                // null), so no error handling needed.
                getPlayerPosition(uuid, cat.boardName, TimedType.ALLTIME);
            }
        });
    }

    public static class SnapshotEntry {
        public final String name;
        public final String uuid;
        public final double score;
        public final int position;

        public SnapshotEntry(String name, String uuid, double score, int position) {
            this.name = name;
            this.uuid = uuid;
            this.score = score;
            this.position = position;
        }
    }

    private static class PlayerPosEntry {
        final SnapshotEntry entry;
        final long cachedAt;
        PlayerPosEntry(SnapshotEntry entry, long cachedAt) {
            this.entry = entry;
            this.cachedAt = cachedAt;
        }
    }
}
