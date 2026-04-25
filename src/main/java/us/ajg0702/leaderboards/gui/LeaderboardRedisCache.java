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

    public LeaderboardRedisCache(LeaderboardPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        boolean configEnabled = plugin.getAConfig().getBoolean("redis-cache.enabled");
        if (!configEnabled) {
            plugin.getLogger().info("Redis leaderboard cache is disabled.");
            return;
        }

        String host = plugin.getAConfig().getString("redis-cache.host");
        int port = plugin.getAConfig().getInt("redis-cache.port");
        String password = plugin.getAConfig().getString("redis-cache.password");
        writer = plugin.getAConfig().getBoolean("redis-cache.writer");
        refreshIntervalMinutes = plugin.getAConfig().getInt("redis-cache.refresh-interval");

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
     * Only runs on writer servers.
     */
    public void refreshAll() {
        if (!enabled || !writer) return;

        long start = System.currentTimeMillis();
        int count = 0;

        for (LeaderboardGUI.CategoryDef cat : LeaderboardGUI.CATEGORIES) {
            if (cat.boardName == null) continue;

            for (TimedType type : CACHED_TYPES) {
                List<CachedEntry> entries = new ArrayList<>();
                for (int pos = 1; pos <= 10; pos++) {
                    try {
                        StatEntry stat = plugin.getCache().getStat(pos, cat.boardName, type);
                        if (stat != null && stat.hasPlayer()) {
                            entries.add(new CachedEntry(
                                    stat.getPlayerName(),
                                    stat.getPlayerID() != null ? stat.getPlayerID().toString() : "",
                                    stat.getScore(),
                                    pos
                            ));
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error fetching stat for " + cat.boardName + " pos " + pos + ": " + e.getMessage());
                    }
                }

                String key = KEY_PREFIX + "top:" + cat.boardName + ":" + type.lowerName();
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.set(key, gson.toJson(entries.toArray(new CachedEntry[0])));
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to write Redis cache: " + key);
                }
            }
        }

        // Store refresh timestamp
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(KEY_PREFIX + "meta:last_refresh", String.valueOf(System.currentTimeMillis()));
        } catch (Exception ignored) {}

        long took = System.currentTimeMillis() - start;
        plugin.getLogger().info("Redis cache refreshed: " + count + " boards in " + took + "ms");
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
     * Checks Redis cache first, then queries DB and caches the result.
     */
    public CachedEntry getPlayerPosition(OfflinePlayer player, String board, TimedType type) {
        if (!enabled) return null;

        String uuid = player.getUniqueId().toString();
        String key = KEY_PREFIX + "player:" + uuid + ":" + board + ":" + type.lowerName();

        // Try Redis cache
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(key);
            if (json != null) {
                return gson.fromJson(json, CachedEntry.class);
            }
        } catch (Exception ignored) {}

        // Cache miss — query DB and store with TTL
        try {
            StatEntry entry = plugin.getCache().getStatEntry(player, board, type);
            if (entry != null && entry.hasPlayer() && entry.getPosition() > 0) {
                CachedEntry cached = new CachedEntry(
                        entry.getPlayerName(),
                        uuid,
                        entry.getScore(),
                        entry.getPosition()
                );
                try (Jedis jedis = jedisPool.getResource()) {
                    jedis.setex(key, PLAYER_CACHE_TTL, gson.toJson(cached));
                } catch (Exception ignored) {}
                return cached;
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
