package us.ajg0702.leaderboards.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.boards.StatEntry;
import us.ajg0702.leaderboards.boards.TimedType;
import us.ajg0702.utils.foliacompat.CompatScheduler;

import java.text.NumberFormat;
import java.util.*;

public class LeaderboardGUI {

    // ==================== CATEGORY DEFINITIONS ====================
    // Modify board names here to match your ajLeaderboards board names.
    // Board name = the PAPI placeholder you added via /ajlb add <placeholder>
    // Set boardName to null for mock/placeholder categories (e.g. Sell, Buy)

    static final CategoryDef[] CATEGORIES = {
        new CategoryDef("money",        "\u00A76\u00A7lMONEY",         "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E23\u0E27\u0E22\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "vault_eco_balance",        Material.EMERALD,          11),
        new CategoryDef("kills",        "\u00A7c\u00A7lKILLS",         "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E06\u0E48\u0E32\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",         "statistic_player_kills",   Material.IRON_SWORD,       12),
        new CategoryDef("deaths",       "\u00A74\u00A7lDEATHS",        "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E15\u0E32\u0E22\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "statistic_deaths",         Material.SKELETON_SKULL,   13),
        new CategoryDef("playtime",     "\u00A7b\u00A7lPLAYTIME",      "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E40\u0E25\u0E48\u0E19\u0E19\u0E32\u0E19\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "statistic_play_one_minute", Material.CLOCK,           14),
        new CategoryDef("fishing",      "\u00A79\u00A7lFISHING",       "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E15\u0E01\u0E1B\u0E25\u0E32\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "statistic_fish_caught",    Material.FISHING_ROD,      15),
        new CategoryDef("blocks_break", "\u00A7e\u00A7lBLOCKS BREAK",  "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E02\u0E38\u0E14\u0E1A\u0E25\u0E47\u0E2D\u0E01\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",             "statistic_mine_block",     Material.DIAMOND_PICKAXE,  20),
        new CategoryDef("blocks_place", "\u00A7a\u00A7lBLOCKS PLACE",  "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E27\u0E32\u0E07\u0E1A\u0E25\u0E47\u0E2D\u0E01\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",             "statistic_use_item",       Material.BRICKS,           21),
        new CategoryDef("mobs_kill",    "\u00A7d\u00A7lMOBS KILL",     "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E06\u0E48\u0E32\u0E21\u0E2D\u0E1A\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "statistic_mob_kills",      Material.ZOMBIE_HEAD,      22),
        new CategoryDef("sell",         "\u00A76\u00A7lSELL",          "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E02\u0E32\u0E22\u0E02\u0E2D\u0E07\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 null,                       Material.GOLD_INGOT,       23),
        new CategoryDef("buy",          "\u00A73\u00A7lBUY",           "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E0B\u0E37\u0E49\u0E2D\u0E02\u0E2D\u0E07\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 null,                       Material.DIAMOND,          24)
    };

    // Time type toggle config - single CLOCK item at slot 31 (bottom row center)
    static final int TIME_TOGGLE_SLOT = 31;
    private static final TimedType[] TIME_CYCLE = {TimedType.ALLTIME, TimedType.DAILY, TimedType.WEEKLY, TimedType.MONTHLY};
    private static final String[] TIME_LABELS = {
        "\u0E17\u0E31\u0E49\u0E07\u0E2B\u0E21\u0E14",           // ทั้งหมด
        "\u0E23\u0E32\u0E22\u0E27\u0E31\u0E19",                 // รายวัน
        "\u0E23\u0E32\u0E22\u0E2A\u0E31\u0E1B\u0E14\u0E32\u0E2B\u0E4C",   // รายสัปดาห์
        "\u0E23\u0E32\u0E22\u0E40\u0E14\u0E37\u0E2D\u0E19"      // รายเดือน
    };
    private static final String[] TIME_COLORS = {"\u00A76", "\u00A7e", "\u00A7b", "\u00A7d"};

    private static final int INVENTORY_SIZE = 36; // 4 rows
    private static final String INVENTORY_TITLE = "\u00A78\u00A7lLEADERBOARD";

    public static void open(Player player, TimedType type, LeaderboardPlugin plugin) {
        plugin.getScheduler().runTaskAsynchronously(() -> {
            LeaderboardHolder holder = new LeaderboardHolder(type);
            Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE, INVENTORY_TITLE);

            // Fill background
            ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
            for (int i = 0; i < INVENTORY_SIZE; i++) {
                inv.setItem(i, filler);
            }

            // Add category items with lore
            for (CategoryDef cat : CATEGORIES) {
                List<String> lore = buildCategoryLore(cat, type, player, plugin);
                ItemStack item = createItem(cat.icon, cat.displayName, lore);
                inv.setItem(cat.slot, item);
            }

            // Add single time toggle button (CLOCK)
            inv.setItem(TIME_TOGGLE_SLOT, buildTimeToggleItem(type));

            // Open inventory on the correct thread
            if (CompatScheduler.isFolia()) {
                plugin.getScheduler().runSync(player.getLocation(), () -> {
                    if (player.isOnline()) {
                        player.openInventory(inv);
                    }
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.openInventory(inv);
                    }
                });
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static ItemStack buildTimeToggleItem(TimedType current) {
        int idx = getTimeIndex(current);
        String currentLabel = TIME_COLORS[idx] + "\u00A7l" + TIME_LABELS[idx];

        List<String> lore = new ArrayList<>();
        lore.add("");
        for (int i = 0; i < TIME_CYCLE.length; i++) {
            if (i == idx) {
                lore.add(" \u00A7a\u25B6 " + TIME_COLORS[i] + TIME_LABELS[i] + " \u00A7a\u25C0");
            } else {
                lore.add(" \u00A78  " + TIME_LABELS[i]);
            }
        }
        lore.add("");
        lore.add("\u00A7e\u0E04\u0E25\u0E34\u0E01\u0E40\u0E1E\u0E37\u0E48\u0E2D\u0E40\u0E1B\u0E25\u0E35\u0E48\u0E22\u0E19"); // คลิกเพื่อเปลี่ยน

        ItemStack item = createItem(Material.CLOCK, currentLabel, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    static TimedType getNextTimeType(TimedType current) {
        int idx = getTimeIndex(current);
        return TIME_CYCLE[(idx + 1) % TIME_CYCLE.length];
    }

    private static int getTimeIndex(TimedType type) {
        for (int i = 0; i < TIME_CYCLE.length; i++) {
            if (TIME_CYCLE[i] == type) return i;
        }
        return 0;
    }

    private static List<String> buildCategoryLore(CategoryDef cat, TimedType type, Player player, LeaderboardPlugin plugin) {
        List<String> lore = new ArrayList<>();
        lore.add(cat.description);
        lore.add("");

        if (cat.boardName == null) {
            // Mock data for sell/buy
            lore.addAll(getMockData(cat.id));
            lore.add("");
            lore.add("\u00A7a\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: \u00A7fN/A"); // → อันดับของคุณอยู่ที่: N/A
            lore.add("\u00A78(\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E15\u0E31\u0E27\u0E2D\u0E22\u0E48\u0E32\u0E07)"); // (ข้อมูลตัวอย่าง)
        } else {
            // Try TopManager cache first, fallback to DB only if cache returns loading
            for (int pos = 1; pos <= 10; pos++) {
                StatEntry entry = plugin.getTopManager().getCachedStat(pos, cat.boardName, type);
                if (entry == null || !entry.hasPlayer()) {
                    // Cache miss — fetch from DB directly (we're on async thread)
                    entry = plugin.getCache().getStat(pos, cat.boardName, type);
                }
                if (entry != null && entry.hasPlayer()) {
                    String score = StatEntry.formatDouble(entry.getScore());
                    lore.add("\u00A7e#" + pos + " \u00A7f" + entry.getPlayerName() + " \u00A77- \u00A7a" + score);
                } else {
                    lore.add("\u00A7e#" + pos + " \u00A77- \u00A78\u0E44\u0E21\u0E48\u0E21\u0E35\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25"); // ไม่มีข้อมูล
                }
            }

            lore.add("");

            // Player's own position — cache first, DB fallback
            StatEntry playerEntry = plugin.getTopManager().getCachedStatEntry(player, cat.boardName, type, false);
            if (playerEntry == null || !playerEntry.hasPlayer() || playerEntry.getPosition() <= 0) {
                playerEntry = plugin.getCache().getStatEntry(player, cat.boardName, type);
            }
            if (playerEntry != null && playerEntry.hasPlayer() && playerEntry.getPosition() > 0) {
                String posFormatted = NumberFormat.getNumberInstance(Locale.US).format(playerEntry.getPosition());
                lore.add("\u00A7a\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: \u00A7f#" + posFormatted);
            } else {
                lore.add("\u00A7a\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: \u00A7fN/A");
            }
        }

        return lore;
    }

    private static List<String> getMockData(String catId) {
        List<String> lines = new ArrayList<>();
        String[][] mockEntries;
        if ("sell".equals(catId)) {
            mockEntries = new String[][]{
                {"xNightShade", "1.52M"}, {"CraftMaster99", "1.24M"}, {"PixelDragon", "980.5k"},
                {"SkyBlaze_TH", "753.2k"}, {"DiamondKing", "621.8k"}, {"NetherWolf", "518.3k"},
                {"RedstoneGuru", "432.1k"}, {"EmeraldHunt", "351.7k"}, {"BlockSmith", "284.9k"},
                {"StarMiner", "213.4k"}
            };
        } else {
            mockEntries = new String[][]{
                {"AquaTrader", "2.15M"}, {"GoldRush_X", "1.83M"}, {"MarketKing", "1.42M"},
                {"ShopMaster", "1.11M"}, {"BuyerPro", "892.3k"}, {"TradeWind", "724.6k"},
                {"CoinFlip", "581.2k"}, {"DealMaker", "453.8k"}, {"BargainHunt", "327.1k"},
                {"SpendWise", "251.5k"}
            };
        }
        for (int i = 0; i < mockEntries.length; i++) {
            lines.add("\u00A7e#" + (i + 1) + " \u00A7f" + mockEntries[i][0] + " \u00A77- \u00A7a" + mockEntries[i][1]);
        }
        return lines;
    }

    static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    static class CategoryDef {
        final String id;
        final String displayName;
        final String description;
        final String boardName;
        final Material icon;
        final int slot;

        CategoryDef(String id, String displayName, String description, String boardName, Material icon, int slot) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.boardName = boardName;
            this.icon = icon;
            this.slot = slot;
        }
    }
}
