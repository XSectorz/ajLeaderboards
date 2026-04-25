package us.ajg0702.leaderboards.commands.main.subcommands;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import us.ajg0702.commands.CommandSender;
import us.ajg0702.commands.SubCommand;
import us.ajg0702.leaderboards.LeaderboardPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static us.ajg0702.leaderboards.LeaderboardPlugin.message;

public class ForceUpdate extends SubCommand {
    private final LeaderboardPlugin plugin;

    public ForceUpdate(LeaderboardPlugin plugin) {
        super("forceupdate", Collections.singletonList("fu"), "ajleaderboards.use",
                "Force update all player stats into database and refresh Redis cache");
        this.plugin = plugin;
    }

    @Override
    public List<String> autoComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    @Override
    public void execute(CommandSender sender, String[] args, String label) {
        List<String> boards = plugin.getTopManager().getBoards();
        if (boards.isEmpty()) {
            sender.sendMessage(message("<red>No boards found. Add boards first with /ajlb add <placeholder>"));
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(message("<red>No online players to update."));
            return;
        }

        sender.sendMessage(message("<yellow>Force updating <white>" + players.size() +
                "<yellow> players on <white>" + boards.size() + "<yellow> boards..."));

        plugin.getScheduler().runTaskAsynchronously(() -> {
            int updated = 0;
            for (Player p : players) {
                if (plugin.isShuttingDown()) return;
                if (!p.isOnline()) continue;
                try {
                    plugin.getCache().updatePlayerStats(p);
                    updated++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Error updating stats for " + p.getName() + ": " + e.getMessage());
                }
            }

            int finalUpdated = updated;
            sender.sendMessage(message("<green>Updated <white>" + finalUpdated +
                    "<green> players on <white>" + boards.size() + "<green> boards."));

            // Also refresh Redis cache if writer mode is enabled
            if (plugin.getRedisCache() != null && plugin.getRedisCache().isEnabled() && plugin.getRedisCache().isWriter()) {
                sender.sendMessage(message("<yellow>Refreshing Redis cache..."));
                plugin.getRedisCache().refreshAll();
                sender.sendMessage(message("<green>Redis cache refreshed."));
            }
        });
    }
}
