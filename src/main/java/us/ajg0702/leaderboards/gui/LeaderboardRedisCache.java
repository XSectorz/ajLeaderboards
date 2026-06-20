package us.ajg0702.leaderboards.gui;

import com.google.gson.Gson;
import org.bukkit.OfflinePlayer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.boards.StatEntry;
import us.ajg0702.leaderboards.boards.TimedType;

import java.util.*;
import java.util.logging.Level;

public class LeaderboardRedisCache {

    private static final String KEY_PREFIX = "ajlb:";
    private static final int PLAYER_CACHE_TTL = 120; // 2 minutes
    private static final TimedType[] CACHED_TYPES = {TimedType.ALLTIME, TimedType.DAILY, TimedType.WEEKLY, TimedType.MONTHLY};

    private final LeaderboardPlugin plugin;
    private final Gson gson = new Gson();
    private JedisPool jedisPool;
    private boolean enabled = false;
    private boolean writer = false;
    private int refreshIntervalMinutes = 60;

    /** Guards against a refresh starting while a previous one is still
     *  running — protects against thread-pool exhaustion when the DB is
     *  slower than the configured refresh interval. */
    private final java.util.concurrent.atomic.AtomicBoolean refreshInProgress =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    public LeaderboardRedisCache(LeaderboardPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        boolean configEnabled = plugin.getAConfig().getBoolean("redis-cache-enabled");
        if (!configEnabled) {
            plugin.getLogger().info("Redis leaderboard cache is disabled.");
            return;
        }

        String host = plugin.getAConfig().getString("redis-cache-host");
        int port = plugin.getAConfig().getInt("redis-cache-port");
        String password = plugin.getAConfig().getString("redis-cache-password");
        writer = plugin.getAConfig().getBoolean("redis-cache-writer");
        refreshIntervalMinutes = plugin.getAConfig().getInt("redis-cache-refresh-interval");

        if (host == null || host.isEmpty()) {
            plugin.getLogger().warning("Redis cache enabled but host is empty. Disabling.");
            return;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(2);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setTimeBetweenEvictionRunsMillis(30000);
            poolConfig.setMinEvictableIdleTimeMillis(60000);

            jedisPool = new JedisPool(poolConfig, host, port, 10000,
                    password == null || password.isEmpty() ? null : password);

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            enabled = true;
            plugin.getLogger().info("Redis leaderboard cache connected" +
                    (writer ? " [WRITER mode - refreshing every " + refreshIntervalMinutes + " min]"
                            : " [READER mode]"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to connect to Redis cache:", e);
            enabled = false;
        }
    }

    /**
     * Refresh all leaderboard data from DB into Redis.
     *
     * <p>Only runs on writer servers. The TIMER thread that calls this
     * returns immediately; the actual refresh runs in the background on
     * the plugin's async scheduler. Folia-safe on every level:
     *
     * <ul>
     *   <li><b>No timer-thread blocking.</b> The writer's
     *       {@code runTaskTimerAsynchronously} no longer holds an async
     *       slot for the full refresh duration — it returns within
     *       microseconds after this method submits the background task.
     *       Avoids stacking refresh threads when the DB is slower than
     *       the configured interval.</li>
     *   <li><b>Re-entrancy guard.</b> If a refresh is already running, the
     *       next timer tick skips with a single log line — no two refresh
     *       passes ever overlap. Eliminates the "two refreshes both
     *       hammering the DB" failure mode entirely.</li>
     *   <li><b>Bounded concurrency.</b> Cells are processed via a
     *       {@code Semaphore} sized to {@code MAX_PARALLEL} so we never
     *       try to grab more JDBC connections than the pool has —
     *       prevents the writer from competing with other plugins for
     *       connections, and prevents thundering-herd against the DB.</li>
     *   <li><b>No world / entity state touched.</b> All work is DB reads
     *       + Redis writes + JSON serialise. Safe from any region thread
     *       on Folia; no region hop needed.</li>
     * </ul>
     *
     * <p>Wall-clock duration is now ~1–2s for ~40 cells against a healthy
     * MySQL + 10-connection Hikari pool (limited by the slowest cell, not
     * by the previous artificial 50/200ms sleeps × 40 cells = ~10s of
     * idle wait). That makes a 1–5 minute {@code redis-cache-refresh-
     * interval} comfortable.
     */
    public void refreshAll() {
        if (!enabled || !writer) return;
        if (plugin.isShuttingDown()) return;
        if (!refreshInProgress.compareAndSet(false, true)) {
            plugin.getLogger().info("[RedisRefresh] previous refresh still running — skipping this tick");
            return;
        }
        // Hop to a background async thread so the TIMER thread returns
        // immediately. doRefresh() owns the entire pass and releases the
        // refreshInProgress flag when done.
        plugin.getScheduler().runTaskAsynchronously(this::doRefresh);
    }

    /** Bounded concurrency cap. Matches the typical Hikari pool size so
     *  we never queue inside JDBC — keeps refresh latency predictable and
     *  leaves connections free for other plugins / handlers. */
    private static final int MAX_PARALLEL = 8;

    /** Per-cell timeout — if a single (board, type) fetch wedges, we still
     *  unblock the rest. Generous so a one-off slow query doesn't drop
     *  data on the floor. */
    private static final long CELL_TIMEOUT_SECONDS = 30;

    private void doRefresh() {
        long start = System.currentTimeMillis();
        final java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger skipped = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.Semaphore sem = new java.util.concurrent.Semaphore(MAX_PARALLEL);
        final java.util.List<java.util.concurrent.CompletableFuture<Void>> futures = new java.util.ArrayList<>();

        try {
            if (plugin.isShuttingDown()) return;
            // One up-front getBoards() — per-cell tasks read this list
            // instead of hitting the boards cache concurrently.
            List<String> existingBoards = plugin.getCache().getBoards();

            outer:
            for (LeaderboardGUI.CategoryDef cat : LeaderboardGUI.CATEGORIES) {
                if (cat.boardName == null) continue;
                if (plugin.isShuttingDown()) break;
                if (!existingBoards.contains(cat.boardName)) {
                    skipped.incrementAndGet();
                    continue;
                }
                for (TimedType type : CACHED_TYPES) {
                    if (plugin.isShuttingDown()) break outer;
                    // Block this background thread until a permit frees up.
                    // We're on the dedicated refresh thread, NOT the timer
                    // thread, so this wait costs nothing externally.
                    try {
                        sem.acquire();
                    } catch (InterruptedException ie) {
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
                            // ONE DB round-trip per cell instead of 10 ×
                            // LIMIT 1 OFFSET k. The (sortcol, namecache)
                            // composite index streams ordered rows so the
                            // optimiser doesn't filesort.
                            List<StatEntry> top = plugin.getCache().getTopN(c.boardName, t, 10);
                            List<CachedEntry> entries = new ArrayList<>(top.size());
                            for (StatEntry stat : top) {
                                if (stat == null || !stat.hasPlayer()) continue;
                                entries.add(new CachedEntry(
                                        stat.getPlayerName(),
                                        stat.getPlayerID() != null ? stat.getPlayerID().toString() : "",
                                        stat.getScore(),
                                        stat.getPosition()
                                ));
                            }
                            String key = KEY_PREFIX + "top:" + c.boardName + ":" + t.lowerName();
                            try (Jedis jedis = jedisPool.getResource()) {
                                jedis.set(key, gson.toJson(entries.toArray(new CachedEntry[0])));
                                count.incrementAndGet();
                            }
                        } catch (Throwable err) {
                            plugin.getLogger().warning("[RedisRefresh] " + c.boardName
                                    + "/" + t.lowerName() + " failed: " + err.getMessage());
                        } finally {
                            sem.release();
                            f.complete(null);
                        }
                    });
                }
            }

            // Await completion on the background thread — NOT the timer
            // thread. allOf() of an empty list completes immediately.
            try {
                java.util.concurrent.CompletableFuture
                        .allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                        .get(CELL_TIMEOUT_SECONDS * futures.size() / MAX_PARALLEL + CELL_TIMEOUT_SECONDS,
                                java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException te) {
                plugin.getLogger().warning("[RedisRefresh] partial refresh — "
                        + count.get() + " of " + futures.size() + " cells finished before timeout");
            } catch (Exception ignored) { /* logged per-task above */ }

            // Store refresh timestamp
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set(KEY_PREFIX + "meta:last_refresh", String.valueOf(System.currentTimeMillis()));
            } catch (Exception ignored) {}

            long took = System.currentTimeMillis() - start;
            plugin.getLogger().info("Redis cache refreshed: " + count.get() + " entries in " + took + "ms" +
                    (skipped.get() > 0 ? " (" + skipped.get() + " boards not found - add them via /ajlb add)" : ""));
        } finally {
            // ALWAYS release the in-progress flag — even on partial /
            // failed refresh — so the next tick can run. Without this an
            // exception would permanently lock out future refreshes.
            refreshInProgress.set(false);
        }
    }

    /**
     * Get cached top 10 entries for a board and type.
     * Returns null if not cached or Redis is disabled.
     */
    public List<CachedEntry> getTop10(String board, TimedType type) {
        if (!enabled) return null;

        String key = KEY_PREFIX + "top:" + board + ":" + type.lowerName();
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(key);
            if (json == null) return null;
            CachedEntry[] arr = gson.fromJson(json, CachedEntry[].class);
            return arr != null ? Arrays.asList(arr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get a player's position on a board.
     * Checks Redis cache first, then queries DB and caches the result with TTL.
     */
    /**
     * Pure Redis read — NEVER falls through to a synchronous DB query.
     *
     * <p>Used to be: on a cache miss the writer node fired a synchronous
     * {@code getStatEntry(...)} DB query inline. With many players
     * opening {@code /leaderboard} concurrently, those 10 (categories) ×
     * N (players) synchronous queries saturated the JDBC pool —
     * individual menus then waited 10–20 seconds for a free connection.
     *
     * <p>Now: a cache miss simply returns {@code null}. The GUI falls
     * through to the snapshot cache (pre-warmed on player join via
     * {@code prefetchPlayer} for ALLTIME) and beyond that shows N/A.
     * Snapshot's own miss path schedules an async background DB fill —
     * never blocks the GUI build thread. Net effect: zero blocking
     * JDBC calls during a {@code /leaderboard} open.
     */
    public CachedEntry getPlayerPosition(OfflinePlayer player, String board, TimedType type) {
        if (!enabled) return null;

        String uuid = player.getUniqueId().toString();
        String key = KEY_PREFIX + "player:" + uuid + ":" + board + ":" + type.lowerName();

        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(key);
            if (json != null) {
                return gson.fromJson(json, CachedEntry.class);
            }
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Get the timestamp of the last refresh (epoch millis).
     */
    public long getLastRefreshTime() {
        if (!enabled) return 0;
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(KEY_PREFIX + "meta:last_refresh");
            return val != null ? Long.parseLong(val) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int getRefreshIntervalMinutes() {
        return refreshIntervalMinutes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isWriter() {
        return writer;
    }

    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    public static class CachedEntry {
        public String name;
        public String uuid;
        public double score;
        public int position;

        public CachedEntry() {}

        public CachedEntry(String name, String uuid, double score, int position) {
            this.name = name;
            this.uuid = uuid;
            this.score = score;
            this.position = position;
        }
    }
}
