package us.ajg0702.leaderboards.commands.main.subcommands;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import us.ajg0702.commands.CommandSender;
import us.ajg0702.commands.SubCommand;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.boards.StatEntry;
import us.ajg0702.leaderboards.boards.TimedType;
import us.ajg0702.leaderboards.cache.Cache;
import us.ajg0702.leaderboards.gui.LeaderboardGUI;
import us.ajg0702.leaderboards.gui.LeaderboardRedisCache;

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
            Player testPlayer = players.get(0);

            // === STEP 1: PAPI Diagnostic ===
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
                if (raw.equals(placeholder) || raw.equals("%" + board + "%")) {
                    sender.sendMessage(message("<red>  " + board + " <dark_gray>-> <red>" + raw + " <gray>(PAPI expansion not installed!)"));
                    continue;
                }
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

            // === STEP 2: Update all players ===
            sender.sendMessage(message(""));
            sender.sendMessage(message("<yellow>Updating all players (debug=true)..."));

            // Temporarily enable update-de-bug for detailed logging
            for (Player p : players) {
                if (plugin.isShuttingDown()) return;
                if (!p.isOnline()) continue;
                for (String board : boards) {
                    try {
                        // Direct test: manually insert via updateStat
                        plugin.getCache().updateStat(board, p);
                        sender.sendMessage(message("<gray>  updateStat(" + board + ", " + p.getName() + ") completed"));
                    } catch (Exception e) {
                        sender.sendMessage(message("<red>  updateStat(" + board + ", " + p.getName() + ") ERROR: " + e.getMessage()));
                    }
                }
            }

            // 2nd pass for zero-validation
            if (plugin.getAConfig().getBoolean("require-zero-validation")) {
                sender.sendMessage(message("<yellow>Running 2nd pass (zero-validation)..."));
                for (Player p : players) {
                    if (plugin.isShuttingDown()) return;
                    if (!p.isOnline()) continue;
                    plugin.getCache().updatePlayerStats(p);
                }
            }
            sender.sendMessage(message("<green>Players updated."));

            // Show DB method info
            sender.sendMessage(message("<gray>DB method: " + plugin.getCache().getMethod().getName() +
                    ", table prefix: '" + plugin.getCache().getTablePrefix() + "'"));

            // === STEP 3: Verify DB read ===
            sender.sendMessage(message(""));
            sender.sendMessage(message("<yellow>--- DB Verification ---"));
            for (String board : boards) {
                StatEntry entry = plugin.getCache().getStat(1, board, TimedType.ALLTIME);
                if (entry != null && entry.hasPlayer()) {
                    sender.sendMessage(message("<green>  " + board + " #1: " + entry.getPlayerName() + " = " + StatEntry.formatDouble(entry.getScore())));
                } else {
                    String reason = entry != null ? entry.getPlayerName() : "null";
                    sender.sendMessage(message("<red>  " + board + " #1: no data <gray>(" + reason + ")"));
                }
            }

            // === STEP 4: Refresh Redis cache ===
            LeaderboardRedisCache rc = plugin.getRedisCache();
            if (rc != null && rc.isEnabled() && rc.isWriter()) {
                sender.sendMessage(message(""));
                sender.sendMessage(message("<yellow>Refreshing Redis cache..."));
                rc.refreshAll();
                sender.sendMessage(message("<green>Redis cache refreshed."));

                // Verify Redis read
                sender.sendMessage(message("<yellow>--- Redis Verification ---"));
                for (LeaderboardGUI.CategoryDef cat : LeaderboardGUI.CATEGORIES) {
                    if (cat.boardName == null) continue;
                    List<LeaderboardRedisCache.CachedEntry> top10 = rc.getTop10(cat.boardName, TimedType.ALLTIME);
                    if (top10 != null && !top10.isEmpty()) {
                        LeaderboardRedisCache.CachedEntry first = top10.get(0);
                        sender.sendMessage(message("<green>  " + cat.boardName + ": " + top10.size() + " entries, #1=" + first.name + " (" + StatEntry.formatDouble(first.score) + ")"));
                    } else {
                        sender.sendMessage(message("<red>  " + cat.boardName + ": " + (top10 == null ? "null (key not found)" : "empty list")));
                    }
                }
            } else {
                sender.sendMessage(message(""));
                sender.sendMessage(message("<gray>Redis cache: " + (rc == null ? "not initialized" : rc.isEnabled() ? "enabled but not writer" : "disabled")));
            }

            sender.sendMessage(message(""));
            sender.sendMessage(message("<green>Force update complete!"));
        });
    }
}
