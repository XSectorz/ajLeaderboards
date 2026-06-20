package us.ajg0702.leaderboards;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;


import static us.ajg0702.leaderboards.LeaderboardPlugin.message;

public class Listeners implements Listener {

    private final LeaderboardPlugin plugin;

    public Listeners(LeaderboardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if(plugin.getCache().getMethod().getName().equals("sqlite") && e.getPlayer().hasPermission("ajleaderboards.use")) {
            plugin.getScheduler().runTaskLaterAsynchronously(() -> {
                plugin.getAdventure().player(e.getPlayer())
                        .sendMessage(message(
                                "\n&6[ajLeaderboards] &cSQLite is not recommended and will be removed! &7Please switch to h2 for a faster (and more stable) cache storage.\n" +
                                        "&cSQLite support will be removed in the future!\n" +
                                        "&7See how to switch without losing data " +
                                        "<hover:show_text:'<yellow>Click to go to https://wiki.ajg0702.us/ajleaderboards/moving-storage-methods'>" +
                                        "<click:open_url:'https://wiki.ajg0702.us/ajleaderboards/moving-storage-methods'>" +
                                        "<white><underlined>here (click me)" +
                                        "</click>" +
                                        "</hover>\n"
                        ));
            }, 40);
        }
        // Pre-warm the snapshot cache's player-position entries so the
        // player's first /leaderboard skips the DB-fallback path. Without
        // this, the first open serialised one DB query per board (~10) for
        // the player position — the bulk of the first-open latency.
        //
        // IMPORTANT: this MUST happen BEFORE the update-stats / update-on-
        // join early-returns. The prefetch is a read-only cache fill, not
        // a stat write — it should run regardless of whether the server is
        // configured to also write stats on join. Previously the prefetch
        // was unreachable when update-on-join=false, leaving cold-cache
        // first-opens slow on servers that intentionally disable join-time
        // stat updates to reduce DB pressure.
        //
        // Fire-and-forget on the async scheduler (Folia-aware via
        // CompatScheduler) so we don't block the join sequence.
        if (plugin.getSnapshotCache() != null) {
            plugin.getScheduler().runTaskLaterAsynchronously(
                    () -> plugin.getSnapshotCache().prefetchPlayer(e.getPlayer().getUniqueId()),
                    20L);   // 1s delay so we don't fight join-time DB writes
        }

        if(!plugin.getAConfig().getBoolean("update-stats")) return;
        if(!plugin.getAConfig().getBoolean("update-on-join")) return;
        plugin.getScheduler().runTaskAsynchronously(() -> plugin.getCache().updatePlayerStats(e.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.getCache().cleanPlayer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST) // Lowest is run first
    public void onQuitFirst(PlayerQuitEvent e) {
        if(!plugin.getAConfig().getBoolean("update-stats")) return;
        if(!plugin.getAConfig().getBoolean("update-on-leave")) return;
        plugin.getCache().updatePlayerStats(e.getPlayer());
    }
}
