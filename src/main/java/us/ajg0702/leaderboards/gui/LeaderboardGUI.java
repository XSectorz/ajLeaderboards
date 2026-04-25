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
    static final CategoryDef[] CATEGORIES = {
        new CategoryDef("money",        "\u00A76\u00A7lMONEY",         "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E23\u0E27\u0E22\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "vault_eco_balance",        Material.EMERALD,          11),
        new CategoryDef("kills",        "\u00A7c\u00A7lKILLS",         "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E06\u0E48\u0E32\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",         "statistic_player_kills",   Material.IRON_SWORD,       12),
        new CategoryDef("deaths",       "\u00A74\u00A7lDEATHS",        "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E15\u0E32\u0E22\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "statistic_deaths",         Material.SKELETON_SKULL,   13),
        new CategoryDef("playtime",     "\u00A7b\u00A7lPLAYTIME",      "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E40\u0E25\u0E48\u0E19\u0E19\u0E32\u0E19\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "statistic_play_one_minute", Material.COMPASS,         14),
        new CategoryDef("fishing",      "\u00A79\u00A7lFISHING",       "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E15\u0E01\u0E1B\u0E25\u0E32\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "statistic_fish_caught",    Material.FISHING_ROD,      15),
        new CategoryDef("blocks_break", "\u00A7e\u00A7lBLOCKS BREAK",  "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E02\u0E38\u0E14\u0E1A\u0E25\u0E47\u0E2D\u0E01\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",             "statistic_mine_block",     Material.DIAMOND_PICKAXE,  20),
        new CategoryDef("blocks_place", "\u00A7a\u00A7lBLOCKS PLACE",  "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E27\u0E32\u0E07\u0E1A\u0E25\u0E47\u0E2D\u0E01\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",             "statistic_use_item",       Material.BRICKS,           21),
        new CategoryDef("mobs_kill",    "\u00A7d\u00A7lMOBS KILL",     "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E06\u0E48\u0E32\u0E21\u0E2D\u0E1A\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "statistic_mob_kills",      Material.ZOMBIE_HEAD,      22),
        new CategoryDef("sell",         "\u00A76\u00A7lSELL",          "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E02\u0E32\u0E22\u0E02\u0E2D\u0E07\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 null,                       Material.GOLD_INGOT,       23),
        new CategoryDef("buy",          "\u00A73\u00A7lBUY",           "\u00A77\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E0B\u0E37\u0E49\u0E2D\u0E02\u0E2D\u0E07\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 null,                       Material.DIAMOND,          24)
    };

    // Bottom row items
    static final int TIME_TOGGLE_SLOT = 30;   // HOPPER — time type toggle
    static final int REFRESH_INFO_SLOT = 32;  // CLOCK — refresh info

    private static final TimedType[] TIME_CYCLE = {TimedType.ALLTIME, TimedType.DAILY, TimedType.WEEKLY, TimedType.MONTHLY};
    private static final String[] TIME_LABELS = {
        "\u0E17\u0E31\u0E49\u0E07\u0E2B\u0E21\u0E14",
        "\u0E23\u0E32\u0E22\u0E27\u0E31\u0E19",
        "\u0E23\u0E32\u0E22\u0E2A\u0E31\u0E1B\u0E14\u0E32\u0E2B\u0E4C",
        "\u0E23\u0E32\u0E22\u0E40\u0E14\u0E37\u0E2D\u0E19"
    };
    private static final String[] TIME_COLORS = {"\u00A76", "\u00A7e", "\u00A7b", "\u00A7d"};

    private static final int INVENTORY_SIZE = 36;
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

            LeaderboardRedisCache redisCache = plugin.getRedisCache();

            // Add category items
            for (CategoryDef cat : CATEGORIES) {
                List<String> lore = buildCategoryLore(cat, type, player, plugin, redisCache);
                ItemStack item = createItem(cat.icon, cat.displayName, lore);
                inv.setItem(cat.slot, item);
            }

            // HOPPER — time type toggle (slot 30)
            inv.setItem(TIME_TOGGLE_SLOT, buildTimeToggleItem(type));

            // CLOCK — refresh info (slot 32)
            inv.setItem(REFRESH_INFO_SLOT, buildRefreshInfoItem(redisCache));

            // Open on correct thread
            if (CompatScheduler.isFolia()) {
                plugin.getScheduler().runSync(player.getLocation(), () -> {
                    if (player.isOnline()) player.openInventory(inv);
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.openInventory(inv);
                });
            }
        });
    }

    // ==================== TIME TOGGLE (HOPPER) ====================

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

        ItemStack item = createItem(Material.HOPPER, currentLabel, lore);
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

    // ==================== REFRESH INFO (CLOCK) ====================

    private static ItemStack buildRefreshInfoItem(LeaderboardRedisCache redisCache) {
        List<String> lore = new ArrayList<>();
        lore.add("");

        if (redisCache != null && redisCache.isEnabled()) {
            long lastRefresh = redisCache.getLastRefreshTime();
            String lastRefreshText;
            if (lastRefresh == 0) {
                lastRefreshText = "\u00A77\u0E22\u0E31\u0E07\u0E44\u0E21\u0E48\u0E40\u0E04\u0E22\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A"; // ยังไม่เคยรีเฟรช
            } else {
                lastRefreshText = "\u00A7f" + formatRelativeTime(lastRefresh);
            }

            lore.add("\u00A77\u0E2A\u0E16\u0E32\u0E19\u0E30: \u00A7a\u0E40\u0E0A\u0E37\u0E48\u0E2D\u0E21\u0E15\u0E48\u0E2D Redis \u0E41\u0E25\u0E49\u0E27"); // สถานะ: เชื่อมต่อ Redis แล้ว
            lore.add("\u00A77\u0E2D\u0E31\u0E1E\u0E40\u0E14\u0E17\u0E25\u0E48\u0E32\u0E2A\u0E38\u0E14: " + lastRefreshText); // อัพเดทล่าสุด:
            lore.add("\u00A77\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A\u0E17\u0E38\u0E01: \u00A7f" + redisCache.getRefreshIntervalMinutes() + " \u0E19\u0E32\u0E17\u0E35"); // รีเฟรชทุก: X นาที
        } else {
            lore.add("\u00A77\u0E42\u0E2B\u0E25\u0E14\u0E08\u0E32\u0E01\u0E10\u0E32\u0E19\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E42\u0E14\u0E22\u0E15\u0E23\u0E07"); // โหลดจากฐานข้อมูลโดยตรง
        }

        return createItem(Material.CLOCK,
                "\u00A7b\u00A7l\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25", // §b§lรีเฟรชข้อมูล
                lore);
    }

    private static String formatRelativeTime(long epochMillis) {
        long diff = System.currentTimeMillis() - epochMillis;
        long minutes = diff / 60000;
        if (minutes < 1) return "\u0E40\u0E21\u0E37\u0E48\u0E2D\u0E2A\u0E31\u0E01\u0E04\u0E23\u0E39\u0E48"; // เมื่อสักครู่
        if (minutes < 60) return minutes + " \u0E19\u0E32\u0E17\u0E35\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27"; // นาทีที่แล้ว
        long hours = minutes / 60;
        return hours + " \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27"; // ชั่วโมงที่แล้ว
    }

    // ==================== CATEGORY LORE ====================

    private static List<String> buildCategoryLore(CategoryDef cat, TimedType type, Player player,
                                                   LeaderboardPlugin plugin, LeaderboardRedisCache redisCache) {
        List<String> lore = new ArrayList<>();
        lore.add(cat.description);
        lore.add("");

        if (cat.boardName == null) {
            lore.addAll(getMockData(cat.id));
            lore.add("");
            lore.add("\u00A7a\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: \u00A7fN/A");
            lore.add("\u00A78(\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E15\u0E31\u0E27\u0E2D\u0E22\u0E48\u0E32\u0E07)");
            return lore;
        }

        // === Top 10 ===
        boolean usedRedis = false;
        if (redisCache != null && redisCache.isEnabled()) {
            List<LeaderboardRedisCache.CachedEntry> top10 = redisCache.getTop10(cat.boardName, type);
            if (top10 != null) {
                usedRedis = true;
                for (LeaderboardRedisCache.CachedEntry e : top10) {
                    String score = StatEntry.formatDouble(e.score);
                    lore.add("\u00A7e#" + e.position + " \u00A7f" + e.name + " \u00A77- \u00A7a" + score);
                }
                for (int pos = top10.size() + 1; pos <= 10; pos++) {
                    lore.add("\u00A7e#" + pos + " \u00A77- \u00A78\u0E44\u0E21\u0E48\u0E21\u0E35\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25");
                }
            }
        }

        if (!usedRedis) {
            // Fallback: TopManager cache → DB
            for (int pos = 1; pos <= 10; pos++) {
                StatEntry entry = plugin.getTopManager().getCachedStat(pos, cat.boardName, type);
                if (entry == null || !entry.hasPlayer()) {
                    entry = plugin.getCache().getStat(pos, cat.boardName, type);
                }
                if (entry != null && entry.hasPlayer()) {
                    String score = StatEntry.formatDouble(entry.getScore());
                    lore.add("\u00A7e#" + pos + " \u00A7f" + entry.getPlayerName() + " \u00A77- \u00A7a" + score);
                } else {
                    lore.add("\u00A7e#" + pos + " \u00A77- \u00A78\u0E44\u0E21\u0E48\u0E21\u0E35\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25");
                }
            }
        }

        lore.add("");

        // === Player position ===
        String posText = null;
        if (redisCache != null && redisCache.isEnabled()) {
            LeaderboardRedisCache.CachedEntry playerPos = redisCache.getPlayerPosition(player, cat.boardName, type);
            if (playerPos != null && playerPos.position > 0) {
                posText = NumberFormat.getNumberInstance(Locale.US).format(playerPos.position);
            }
        }

        if (posText == null) {
            // Fallback
            StatEntry playerEntry = plugin.getTopManager().getCachedStatEntry(player, cat.boardName, type, false);
            if (playerEntry == null || !playerEntry.hasPlayer() || playerEntry.getPosition() <= 0) {
                playerEntry = plugin.getCache().getStatEntry(player, cat.boardName, type);
            }
            if (playerEntry != null && playerEntry.hasPlayer() && playerEntry.getPosition() > 0) {
                posText = NumberFormat.getNumberInstance(Locale.US).format(playerEntry.getPosition());
            }
        }

        if (posText != null) {
            lore.add("\u00A7a\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: \u00A7f#" + posText);
        } else {
            lore.add("\u00A7a\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: \u00A7fN/A");
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

    // ==================== UTILS ====================

    static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
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
