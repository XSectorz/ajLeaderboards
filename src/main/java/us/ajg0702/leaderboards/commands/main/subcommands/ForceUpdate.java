package us.ajg0702.leaderboards.commands.main.subcommands;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import us.ajg0702.commands.CommandSender;
import us.ajg0702.commands.SubCommand;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.cache.Cache;

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
            sender.sendMessage(message("<red>No boards found! Add boards first:"));
            sender.sendMessage(message("<yellow>/ajlb add vault_eco_balance"));
            sender.sendMessage(message("<yellow>/ajlb add statistic_player_kills"));
            sender.sendMessage(message("<gray>etc..."));
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            sender.sendMessage(message("<red>No online players to update."));
            return;
        }

        sender.sendMessage(message("<yellow>Force updating <white>" + players.size() +
                "<yellow> players on <white>" + boards.size() + "<yellow> boards..."));
        sender.sendMessage(message("<gray>Boards: <white>" + String.join(", ", boards)));

        plugin.getScheduler().runTaskAsynchronously(() -> {
            // First, diagnose PAPI for the first player
            Player testPlayer = players.get(0);
            sender.sendMessage(message(""));
            sender.sendMessage(message("<yellow>--- PAPI Diagnostic for <white>" + testPlayer.getName() + " <yellow>---"));

            for (String board : boards) {
                String placeholder = "%" + Cache.alternatePlaceholders(board) + "%";
                String raw;
                try {
                    raw = PlaceholderAPI.setPlaceholders(testPlayer, placeholder).replaceAll(",", "");
                } catch (Exception e) {
                    sender.sendMessage(message("<red>  " + board + " <dark_gray>-> <red>ERROR: " + e.getMessage()));
                    continue;
                }

                // Check if PAPI returned the placeholder itself (not installed)
                if (raw.equals(placeholder) || raw.equals("%" + board + "%")) {
                    sender.sendMessage(message("<red>  " + board + " <dark_gray>-> <red>" + raw + " <gray>(PAPI expansion not installed!)"));
                    continue;
                }

                // Try parsing as number
                try {
                    double value = plugin.getPlaceholderFormatter().toDouble(raw, board);
                    if (value == 0 && plugin.getAConfig().getBoolean("require-zero-validation")) {
                        sender.sendMessage(message("<yellow>  " + board + " <dark_gray>-> <yellow>" + raw + " <gray>(value=0, skipped by zero-validation on 1st run)"));
                    } else {
                        sender.sendMessage(message("<green>  " + board + " <dark_gray>-> <green>" + raw + " <gray>(OK, value=" + value + ")"));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(message("<red>  " + board + " <dark_gray>-> <red>" + raw + " <gray>(not a number!)"));
                }
            }

            sender.sendMessage(message(""));
            sender.sendMessage(message("<yellow>Updating all players..."));

            // Now do the actual update
            int updated = 0;
            for (Player p : players) {
                if (plugin.isShuttingDown()) return;
                if (!p.isOnline()) continue;
                try {
                    plugin.getCache().updatePlayerStats(p);
                    updated++;
                } catch (Exception e) {
                    sender.sendMessage(message("<red>Error updating " + p.getName() + ": " + e.getMessage()));
                }
            }

            sender.sendMessage(message("<green>Updated <white>" + updated +
                    "<green> players on <white>" + boards.size() + "<green> boards."));

            // Run a second pass for zero-validated boards
            if (plugin.getAConfig().getBoolean("require-zero-validation")) {
                sender.sendMessage(message("<yellow>Running 2nd pass (zero-validation)..."));
                for (Player p : players) {
                    if (plugin.isShuttingDown()) return;
                    if (!p.isOnline()) continue;
                    try {
                        plugin.getCache().updatePlayerStats(p);
                    } catch (Exception ignored) {}
                }
                sender.sendMessage(message("<green>2nd pass complete."));
            }

            // Refresh Redis cache
            if (plugin.getRedisCache() != null && plugin.getRedisCache().isEnabled() && plugin.getRedisCache().isWriter()) {
                sender.sendMessage(message("<yellow>Refreshing Redis cache..."));
                plugin.getRedisCache().refreshAll();
                sender.sendMessage(message("<green>Redis cache refreshed."));
            }
        });
    }
}
