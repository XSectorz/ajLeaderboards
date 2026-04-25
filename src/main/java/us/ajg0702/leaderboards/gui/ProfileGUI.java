package us.ajg0702.leaderboards.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.boards.StatEntry;
import us.ajg0702.leaderboards.boards.TimedType;
import us.ajg0702.utils.foliacompat.CompatScheduler;

import java.text.NumberFormat;
import java.util.*;

public class ProfileGUI {

    private static final int INVENTORY_SIZE = 36; // 4 rows
    // Slots matching the leaderboard layout
    private static final int[] CATEGORY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

    public static void open(Player viewer, OfflinePlayer target, LeaderboardPlugin plugin) {
        String targetName = target.getName() != null ? target.getName() : "???";

        plugin.getScheduler().runTaskAsynchronously(() -> {
            ProfileHolder holder = new ProfileHolder(target);
            Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE,
                    "\u00A78\u00A7lSTATS \u00A78- \u00A7f" + targetName.toUpperCase());

            // Fill background
            ItemStack filler = LeaderboardGUI.createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                inv.setItem(i, filler);
            }

            // Add category items with player stats
            LeaderboardGUI.CategoryDef[] categories = LeaderboardGUI.CATEGORIES;
            for (int i = 0; i < categories.length; i++) {
                LeaderboardGUI.CategoryDef cat = categories[i];
                List<String> lore = buildProfileLore(cat, target, plugin);
                ItemStack item = LeaderboardGUI.createItem(cat.icon, cat.displayName, lore);
                inv.setItem(CATEGORY_SLOTS[i], item);
            }

            // Open inventory on the correct thread
            if (CompatScheduler.isFolia()) {
                plugin.getScheduler().runSync(viewer.getLocation(), () -> {
                    if (viewer.isOnline()) {
                        viewer.openInventory(inv);
                    }
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (viewer.isOnline()) {
                        viewer.openInventory(inv);
                    }
                });
            }
        });
    }

    private static List<String> buildProfileLore(LeaderboardGUI.CategoryDef cat, OfflinePlayer target, LeaderboardPlugin plugin) {
        List<String> lore = new ArrayList<>();

        if (cat.boardName == null) {
            // Mock data for sell/buy
            lore.add("\u00A7fN/A \u00A77#N/A");
            lore.add("\u00A78(\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E15\u0E31\u0E27\u0E2D\u0E22\u0E48\u0E32\u0E07)"); // (ข้อมูลตัวอย่าง)
        } else {
            // Cache first, DB fallback (already on async thread)
            StatEntry entry = plugin.getTopManager().getCachedStatEntry(target, cat.boardName, TimedType.ALLTIME, false);
            if (entry == null || !entry.hasPlayer() || entry.getPosition() <= 0) {
                entry = plugin.getCache().getStatEntry(target, cat.boardName, TimedType.ALLTIME);
            }
            if (entry != null && entry.hasPlayer() && entry.getPosition() > 0) {
                String value = StatEntry.formatDouble(entry.getScore());
                String pos = NumberFormat.getNumberInstance(Locale.US).format(entry.getPosition());
                lore.add("\u00A7f" + value + " \u00A77#" + pos);
            } else {
                lore.add("\u00A7fN/A \u00A77#N/A");
            }
        }

        return lore;
    }
}
