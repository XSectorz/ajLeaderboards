package us.ajg0702.leaderboards.gui;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.exceptions.JedisConnectionException;
import us.ajg0702.leaderboards.LeaderboardPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

public class UUIDLookup {

    private static final String BUNGEE_CHANNEL = "xsserverutils:channel_bungeecord";
    private static final String RESPONSE_PREFIX = "ajleaderboards:uuid_response_";
    private static final long TIMEOUT_SECONDS = 5;

    private final LeaderboardPlugin plugin;
    private JedisPool jedisPool;
    private boolean enabled = false;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, CompletableFuture<UUID>> pendingLookups = new ConcurrentHashMap<>();
    private Thread subscriberThread;
    private volatile boolean running = false;
    private String responseChannel;

    public UUIDLookup(LeaderboardPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        String host = plugin.getAConfig().getString("redis.host");
        int port = plugin.getAConfig().getInt("redis.port");
        String password = plugin.getAConfig().getString("redis.password");

        if (host == null || host.isEmpty()) {
            plugin.getLogger().info("Redis not configured for UUID lookup. Skipping.");
            return;
        }

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);

            jedisPool = new JedisPool(poolConfig, host, port, 10000,
                    password == null || password.isEmpty() ? null : password);

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
            }

            responseChannel = RESPONSE_PREFIX + plugin.getAConfig().getString("redis.server-name");
            enabled = true;
            running = true;
            startSubscriber();
            plugin.getLogger().info("Redis UUID lookup enabled (channel: " + responseChannel + ")");
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to connect to Redis for UUID lookup:", e);
            enabled = false;
        }
    }

    private void startSubscriber() {
        subscriberThread = new Thread(() -> {
            while (running) {
                try (Jedis jedis = jedisPool.getResource()) {
                    JedisPubSub pubSub = new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            // UUID_RESPONSE<SPLIT>playerName<SPLIT>uuid_or_NOT_FOUND
                            String[] parts = message.split("<SPLIT>");
                            if (parts.length >= 3 && "UUID_RESPONSE".equals(parts[0])) {
                                String playerName = parts[1].toLowerCase();
                                String uuidStr = parts[2];

                                CompletableFuture<UUID> future = pendingLookups.remove(playerName);
                                if (future != null) {
                                    if ("NOT_FOUND".equals(uuidStr)) {
                                        future.complete(null);
                                    } else {
                                        try {
                                            future.complete(UUID.fromString(uuidStr));
                                        } catch (IllegalArgumentException e) {
                                            future.complete(null);
                                        }
                                    }
                                }
                            }
                        }
                    };
                    jedis.subscribe(pubSub, responseChannel);
                } catch (JedisConnectionException e) {
                    if (running) {
                        plugin.getLogger().warning("Redis UUID subscriber connection lost, retrying...");
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        plugin.getLogger().log(Level.WARNING, "Redis UUID subscriber error:", e);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        }, "AJLB-UUID-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    /**
     * Look up a UUID by player name via Redis (xsserverutils BungeeCord).
     * Returns null if not found or Redis is not enabled.
     * This method blocks for up to TIMEOUT_SECONDS.
     */
    public UUID lookupUUID(String playerName) {
        if (!enabled || jedisPool == null) return null;

        CompletableFuture<UUID> future = new CompletableFuture<>();
        pendingLookups.put(playerName.toLowerCase(), future);

        // Send request to BungeeCord
        executor.submit(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                // GET_UUID<SPLIT>responseChannel<SPLIT>playerName
                jedis.publish(BUNGEE_CHANNEL, "GET_UUID<SPLIT>" + responseChannel + "<SPLIT>" + playerName);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send UUID lookup request:", e);
                future.complete(null);
            }
        });

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pendingLookups.remove(playerName.toLowerCase());
            return null;
        } catch (Exception e) {
            pendingLookups.remove(playerName.toLowerCase());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        running = false;
        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }
        executor.shutdownNow();
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}
