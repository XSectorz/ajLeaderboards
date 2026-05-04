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
import us.ajg0702.utils.foliacompat.Task;

import java.text.NumberFormat;
import java.util.*;

public class LeaderboardGUI {

    // ==================== CATEGORY DEFINITIONS ====================
    // Boards that store time in ticks (statistic_play_one_minute)
    private static final Set<String> TIME_BOARDS = new HashSet<>(Collections.singletonList("statistic_play_one_minute"));

    // Hex color helper: "4ADE80" → "§x§4§A§D§E§8§0"
    private static String hex(String h) {
        StringBuilder sb = new StringBuilder("\u00A7x");
        for (char c : h.toCharArray()) sb.append("\u00A7").append(c);
        return sb.toString();
    }

    // Category colors (hex)
    private static final String C_MONEY  = hex("4ADE80"); // green
    private static final String C_KILLS  = hex("F87171"); // red
    private static final String C_DEATHS = hex("A1A1AA"); // zinc
    private static final String C_PLAY   = hex("38BDF8"); // sky blue
    private static final String C_FISH   = hex("818CF8"); // indigo
    private static final String C_BREAK  = hex("FBBF24"); // amber
    private static final String C_PLACE  = hex("34D399"); // emerald
    private static final String C_MOBS   = hex("F472B6"); // pink
    private static final String C_SELL   = hex("FB923C"); // orange
    private static final String C_BUY    = hex("22D3EE"); // cyan

    // Description color
    private static final String C_DESC = hex("94A3B8"); // slate gray

    public static final CategoryDef[] CATEGORIES = {
        new CategoryDef("money",        C_MONEY + "\u1D0D\u1D0F\u0274\u1D07\u028F",                                       C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E23\u0E27\u0E22\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "vault_eco_balance",        Material.EMERALD,          11),
        new CategoryDef("kills",        C_KILLS + "\u1D0B\u026A\u029F\u029F\ua731",                                        C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E06\u0E48\u0E32\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",         "statistic_player_kills",   Material.IRON_SWORD,       12),
        new CategoryDef("deaths",       C_DEATHS + "\u1D05\u1D07\u1D00\u1D1B\u029C\ua731",                                 C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E15\u0E32\u0E22\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "statistic_deaths",         Material.SKELETON_SKULL,   13),
        new CategoryDef("playtime",     C_PLAY + "\u1D18\u029F\u1D00\u028F\u1D1B\u026A\u1D0D\u1D07",                       C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E40\u0E25\u0E48\u0E19\u0E19\u0E32\u0E19\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                     "statistic_play_one_minute", Material.COMPASS,         14),
        new CategoryDef("fishing",      C_FISH + "\ua730\u026A\ua731\u029C\u026A\u0274\u0262",                              C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E15\u0E01\u0E1B\u0E25\u0E32\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "statistic_fish_caught",    Material.FISHING_ROD,      15),
        new CategoryDef("blocks_break", C_BREAK + "\u0299\u029F\u1D0F\u1D04\u1D0B\ua731 \u0299\u0280\u1D07\u1D00\u1D0B",   C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E02\u0E38\u0E14\u0E1A\u0E25\u0E47\u0E2D\u0E01\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",             "statistic_mine_block",     Material.DIAMOND_PICKAXE,  20),
        new CategoryDef("blocks_place", C_PLACE + "\u0299\u029F\u1D0F\u1D04\u1D0B\ua731 \u1D18\u029F\u1D00\u1D04\u1D07",   C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E27\u0E32\u0E07\u0E1A\u0E25\u0E47\u0E2D\u0E01\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",             "statistic_use_item",       Material.BRICKS,           21),
        new CategoryDef("mobs_kill",    C_MOBS + "\u1D0D\u1D0F\u0299\ua731 \u1D0B\u026A\u029F\u029F",                      C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E06\u0E48\u0E32\u0E21\u0E2D\u0E1A\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "statistic_mob_kills",      Material.ZOMBIE_HEAD,      22),
        new CategoryDef("sell",         C_SELL + "\ua731\u1D07\u029F\u029F",                                                C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E02\u0E32\u0E22\u0E02\u0E2D\u0E07\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "xssmpshop_sell_total",      Material.GOLD_INGOT,       23),
        new CategoryDef("buy",          C_BUY + "\u0299\u1D1C\u028F",                                                      C_DESC + "\u0E08\u0E31\u0E14\u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E1C\u0E39\u0E49\u0E40\u0E25\u0E48\u0E19\u0E17\u0E35\u0E48\u0E0B\u0E37\u0E49\u0E2D\u0E02\u0E2D\u0E07\u0E21\u0E32\u0E01\u0E17\u0E35\u0E48\u0E2A\u0E38\u0E14",                 "xssmpshop_buy_total",       Material.DIAMOND,          24)
    };

    // Bottom row items
    static final int TIME_TOGGLE_SLOT = 30;
    static final int REFRESH_INFO_SLOT = 32;

    private static final TimedType[] TIME_CYCLE = {TimedType.ALLTIME, TimedType.DAILY, TimedType.WEEKLY, TimedType.MONTHLY};
    private static final String[] TIME_LABELS = {
        "\u0E17\u0E31\u0E49\u0E07\u0E2B\u0E21\u0E14",
        "\u0E23\u0E32\u0E22\u0E27\u0E31\u0E19",
        "\u0E23\u0E32\u0E22\u0E2A\u0E31\u0E1B\u0E14\u0E32\u0E2B\u0E4C",
        "\u0E23\u0E32\u0E22\u0E40\u0E14\u0E37\u0E2D\u0E19"
    };
    // Time type hex colors
    private static final String[] TIME_COLORS = {
        hex("4ADE80"),  // ทั้งหมด — green
        hex("FACC15"),  // รายวัน — yellow
        hex("38BDF8"),  // รายสัปดาห์ — sky blue
        hex("FB7185")   // รายเดือน — rose
    };

    private static final int INVENTORY_SIZE = 36;
    private static final String INVENTORY_TITLE = "\u00A78\u029F\u1D07\u1D00\u1D05\u1D07\u0280\u0299\u1D0F\u1D00\u0280\u1D05";

    public static void open(Player player, TimedType type, LeaderboardPlugin plugin) {
        plugin.getScheduler().runTaskAsynchronously(() -> {
            LeaderboardHolder holder = new LeaderboardHolder(type);
            Inventory inv = Bukkit.createInventory(holder, INVENTORY_SIZE, INVENTORY_TITLE);

            fillInventory(inv, type, player, plugin);

            Runnable openAndSchedule = () -> {
                if (!player.isOnline()) return;
                player.openInventory(inv);
                // Start 1-second refresh for the clock item
                startClockRefresh(player, inv, holder, plugin);
            };

            if (CompatScheduler.isFolia()) {
                plugin.getScheduler().runSync(player.getLocation(), openAndSchedule);
            } else {
                Bukkit.getScheduler().runTask(plugin, openAndSchedule);
            }
        });
    }

    /**
     * Start a repeating task that updates only the CLOCK item every second.
     */
    private static void startClockRefresh(Player player, Inventory inv, LeaderboardHolder holder, LeaderboardPlugin plugin) {
        Task task = plugin.getScheduler().runTaskTimerAsynchronously(() -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != inv) {
                // Player closed the inventory — cancel
                Task t = holder.getRefreshTask();
                if (t != null) t.cancel();
                return;
            }
            ItemStack clockItem = buildRefreshInfoItem(plugin.getRedisCache(), plugin);
            Runnable apply = () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory() == inv) {
                    inv.setItem(REFRESH_INFO_SLOT, clockItem);
                }
            };
            if (CompatScheduler.isFolia()) {
                plugin.getScheduler().runSync(player.getLocation(), apply);
            } else {
                Bukkit.getScheduler().runTask(plugin, apply);
            }
        }, 20L, 20L); // every 1 second
        holder.setRefreshTask(task);
    }

    /**
     * Update the already-open inventory in-place (async build, sync apply).
     */
    static void updateInventory(Player player, Inventory inv, TimedType type, LeaderboardPlugin plugin) {
        plugin.getScheduler().runTaskAsynchronously(() -> {
            final ItemStack[] built = new ItemStack[INVENTORY_SIZE];
            buildItems(built, type, player, plugin);

            Runnable applyTask = () -> {
                if (!player.isOnline()) return;
                for (int i = 0; i < INVENTORY_SIZE; i++) {
                    inv.setItem(i, built[i]);
                }
                player.updateInventory();
            };

            if (CompatScheduler.isFolia()) {
                plugin.getScheduler().runSync(player.getLocation(), applyTask);
            } else {
                Bukkit.getScheduler().runTask(plugin, applyTask);
            }
        });
    }

    private static void fillInventory(Inventory inv, TimedType type, Player player, LeaderboardPlugin plugin) {
        ItemStack[] built = new ItemStack[INVENTORY_SIZE];
        buildItems(built, type, player, plugin);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            inv.setItem(i, built[i]);
        }
    }

    private static void buildItems(ItemStack[] slots, TimedType type, Player player, LeaderboardPlugin plugin) {
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = filler;
        }

        LeaderboardRedisCache redisCache = plugin.getRedisCache();

        for (CategoryDef cat : CATEGORIES) {
            List<String> lore = buildCategoryLore(cat, type, player, plugin, redisCache);
            slots[cat.slot] = createItem(cat.icon, cat.displayName, lore);
        }

        slots[TIME_TOGGLE_SLOT] = buildTimeToggleItem(type);
        slots[REFRESH_INFO_SLOT] = buildRefreshInfoItem(redisCache, plugin);
    }

    // ==================== SCORE FORMATTING ====================

    /**
     * Format score for display. Uses time format for time-based boards (ticks → Thai time string).
     */
    static String formatScore(double score, String boardName) {
        if (TIME_BOARDS.contains(boardName)) {
            return formatTicksToTime(score);
        }
        return StatEntry.formatDouble(score);
    }

    /**
     * Format Minecraft ticks to Thai time string.
     * statistic_play_one_minute stores ticks (20 ticks = 1 second)
     */
    private static String formatTicksToTime(double ticks) {
        long totalSeconds = Math.round(ticks / 20.0);
        long months = totalSeconds / 2592000; // ~30 days
        long weeks = (totalSeconds % 2592000) / 604800;
        long days = (totalSeconds % 604800) / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (months > 0) {
            return months + " \u0E40\u0E14\u0E37\u0E2D\u0E19 " + weeks + " \u0E2A\u0E31\u0E1B\u0E14\u0E32\u0E2B\u0E4C"; // X เดือน Y สัปดาห์
        } else if (weeks > 0) {
            return weeks + " \u0E2A\u0E31\u0E1B\u0E14\u0E32\u0E2B\u0E4C " + days + " \u0E27\u0E31\u0E19"; // X สัปดาห์ Y วัน
        } else if (days > 0) {
            return days + " \u0E27\u0E31\u0E19 " + hours + " \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07"; // X วัน Y ชั่วโมง
        } else if (hours > 0) {
            return hours + " \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07 " + minutes + " \u0E19\u0E32\u0E17\u0E35"; // X ชั่วโมง Y นาที
        } else {
            return minutes + " \u0E19\u0E32\u0E17\u0E35 " + seconds + " \u0E27\u0E34\u0E19\u0E32\u0E17\u0E35"; // X นาที Y วินาที
        }
    }

    // ==================== TIME TOGGLE (HOPPER) ====================

    @SuppressWarnings("deprecation")
    private static ItemStack buildTimeToggleItem(TimedType current) {
        int idx = getTimeIndex(current);
        // Small caps title: §fꜱᴏʀᴛ
        String title = "\u00A7f\ua731\u1D0F\u0280\u1D1B";

        List<String> lore = new ArrayList<>();
        lore.add("");
        for (int i = 0; i < TIME_CYCLE.length; i++) {
            if (i == idx) {
                // Selected: colored bullet ● with label
                lore.add(" " + TIME_COLORS[i] + "\u25CF " + TIME_LABELS[i]);
            } else {
                // Unselected: dark gray bullet ○ with gray label
                lore.add(" \u00A78\u25CB \u00A77" + TIME_LABELS[i]);
            }
        }
        lore.add("");
        lore.add("\u00A78\u0E04\u0E25\u0E34\u0E01\u0E40\u0E1E\u0E37\u0E48\u0E2D\u0E40\u0E1B\u0E25\u0E35\u0E48\u0E22\u0E19"); // §8คลิกเพื่อเปลี่ยน

        ItemStack item = createItem(Material.HOPPER, title, lore);
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

    private static final String C_CLOCK_TITLE = hex("67E8F9"); // cyan-300
    private static final String C_LABEL = hex("94A3B8");      // slate-400
    private static final String C_VALUE = hex("E2E8F0");      // slate-200
    private static final String C_ACCENT = hex("4ADE80");     // green-400
    private static final String C_MUTED = hex("64748B");      // slate-500

    private static ItemStack buildRefreshInfoItem(LeaderboardRedisCache redisCache, LeaderboardPlugin plugin) {
        String title = C_CLOCK_TITLE + "\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25"; // รีเฟรชข้อมูล

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (redisCache != null && redisCache.isEnabled()) {
            long lastRefresh = redisCache.getLastRefreshTime();
            int intervalMin = redisCache.getRefreshIntervalMinutes();

            if (lastRefresh == 0) {
                lore.add(C_LABEL + "\u0E23\u0E2D\u0E01\u0E32\u0E23\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A\u0E04\u0E23\u0E31\u0E49\u0E07\u0E41\u0E23\u0E01...");
            } else {
                long nextRefresh = lastRefresh + (intervalMin * 60L * 1000L);
                long remaining = nextRefresh - System.currentTimeMillis();

                if (remaining <= 0) {
                    lore.add(C_ACCENT + "\u0E01\u0E33\u0E25\u0E31\u0E07\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A...");
                } else {
                    lore.add(C_LABEL + "\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A\u0E16\u0E31\u0E14\u0E44\u0E1B\u0E43\u0E19: " + C_VALUE + formatCountdown(remaining));
                }
                lore.add(C_LABEL + "\u0E2D\u0E31\u0E1E\u0E40\u0E14\u0E17\u0E25\u0E48\u0E32\u0E2A\u0E38\u0E14: " + C_VALUE + formatRelativeTime(lastRefresh));
            }
        } else {
            int statRefreshTicks = plugin.getAConfig().getInt("stat-refresh");
            int statRefreshSec = statRefreshTicks / 20;
            lore.add(C_LABEL + "\u0E23\u0E35\u0E40\u0E1F\u0E23\u0E0A\u0E17\u0E38\u0E01: " + C_VALUE + statRefreshSec + " \u0E27\u0E34\u0E19\u0E32\u0E17\u0E35");
            lore.add(C_MUTED + "\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E2D\u0E31\u0E1E\u0E40\u0E14\u0E17\u0E2D\u0E31\u0E15\u0E42\u0E19\u0E21\u0E31\u0E15\u0E34");
        }

        return createItem(Material.CLOCK, title, lore);
    }

    private static String formatCountdown(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return hours + " \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07 " + minutes + " \u0E19\u0E32\u0E17\u0E35";
        } else if (minutes > 0) {
            return minutes + " \u0E19\u0E32\u0E17\u0E35 " + seconds + " \u0E27\u0E34\u0E19\u0E32\u0E17\u0E35";
        } else {
            return seconds + " \u0E27\u0E34\u0E19\u0E32\u0E17\u0E35";
        }
    }

    private static String formatRelativeTime(long epochMillis) {
        long diff = System.currentTimeMillis() - epochMillis;
        long minutes = diff / 60000;
        if (minutes < 1) return "\u0E40\u0E21\u0E37\u0E48\u0E2D\u0E2A\u0E31\u0E01\u0E04\u0E23\u0E39\u0E48";
        if (minutes < 60) return minutes + " \u0E19\u0E32\u0E17\u0E35\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27";
        long hours = minutes / 60;
        return hours + " \u0E0A\u0E31\u0E48\u0E27\u0E42\u0E21\u0E07\u0E17\u0E35\u0E48\u0E41\u0E25\u0E49\u0E27";
    }

    // ==================== LORE COLORS (hex) ====================
    private static final String L_RANK  = hex("FBBF24"); // amber — #1, #2
    private static final String L_NAME  = hex("F1F5F9"); // slate-100 — player name
    private static final String L_SEP   = hex("64748B"); // slate-500 — dash
    private static final String L_SCORE = hex("4ADE80"); // green-400 — score value
    private static final String L_EMPTY = hex("475569"); // slate-600 — no data
    private static final String L_POS   = hex("38BDF8"); // sky-400 — your position arrow
    private static final String L_MOCK  = hex("475569"); // slate-600 — mock label

    // ==================== CATEGORY LORE ====================

    private static List<String> buildCategoryLore(CategoryDef cat, TimedType type, Player player,
                                                   LeaderboardPlugin plugin, LeaderboardRedisCache redisCache) {
        List<String> lore = new ArrayList<>();
        lore.add(cat.description);
        lore.add("");

        if (cat.boardName == null) {
            lore.addAll(getMockData(cat.id));
            lore.add("");
            lore.add(L_POS + "\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: " + L_NAME + "N/A");
            lore.add(L_MOCK + "(\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25\u0E15\u0E31\u0E27\u0E2D\u0E22\u0E48\u0E32\u0E07)");
            return lore;
        }

        // === Top 10 ===
        boolean usedRedis = false;
        if (redisCache != null && redisCache.isEnabled()) {
            List<LeaderboardRedisCache.CachedEntry> top10 = redisCache.getTop10(cat.boardName, type);
            if (top10 != null && !top10.isEmpty()) {
                usedRedis = true;
                for (LeaderboardRedisCache.CachedEntry e : top10) {
                    String score = formatScore(e.score, cat.boardName);
                    lore.add(L_RANK + "#" + e.position + " " + L_NAME + e.name + " " + L_SEP + "- " + L_SCORE + score);
                }
                for (int pos = top10.size() + 1; pos <= 10; pos++) {
                    lore.add(L_RANK + "#" + pos + " " + L_SEP + "- " + L_EMPTY + "\u0E44\u0E21\u0E48\u0E21\u0E35\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25");
                }
            }
        }

        if (!usedRedis) {
            boolean boardExists = plugin.getTopManager().boardExists(cat.boardName);
            for (int pos = 1; pos <= 10; pos++) {
                if (!boardExists) {
                    lore.add(L_RANK + "#" + pos + " " + L_SEP + "- " + L_EMPTY + "\u0E44\u0E21\u0E48\u0E21\u0E35\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25");
                    continue;
                }
                StatEntry entry = plugin.getTopManager().getCachedStat(pos, cat.boardName, type);
                if (entry == null || !entry.hasPlayer()) {
                    entry = plugin.getCache().getStat(pos, cat.boardName, type);
                }
                if (entry != null && entry.hasPlayer()) {
                    String score = formatScore(entry.getScore(), cat.boardName);
                    lore.add(L_RANK + "#" + pos + " " + L_NAME + entry.getPlayerName() + " " + L_SEP + "- " + L_SCORE + score);
                } else {
                    lore.add(L_RANK + "#" + pos + " " + L_SEP + "- " + L_EMPTY + "\u0E44\u0E21\u0E48\u0E21\u0E35\u0E02\u0E49\u0E2D\u0E21\u0E39\u0E25");
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

        if (posText == null && plugin.getTopManager().boardExists(cat.boardName)) {
            StatEntry playerEntry = plugin.getTopManager().getCachedStatEntry(player, cat.boardName, type, false);
            if (playerEntry == null || !playerEntry.hasPlayer() || playerEntry.getPosition() <= 0) {
                playerEntry = plugin.getCache().getStatEntry(player, cat.boardName, type);
            }
            if (playerEntry != null && playerEntry.hasPlayer() && playerEntry.getPosition() > 0) {
                posText = NumberFormat.getNumberInstance(Locale.US).format(playerEntry.getPosition());
            }
        }

        if (posText != null) {
            lore.add(L_POS + "\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: " + L_NAME + "#" + posText);
        } else {
            lore.add(L_POS + "\u2192 \u0E2D\u0E31\u0E19\u0E14\u0E31\u0E1A\u0E02\u0E2D\u0E07\u0E04\u0E38\u0E13\u0E2D\u0E22\u0E39\u0E48\u0E17\u0E35\u0E48: " + L_NAME + "N/A");
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
            lines.add(L_RANK + "#" + (i + 1) + " " + L_NAME + mockEntries[i][0] + " " + L_SEP + "- " + L_SCORE + mockEntries[i][1]);
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

    public static class CategoryDef {
        public final String id;
        public final String displayName;
        public final String description;
        public final String boardName;
        public final Material icon;
        public final int slot;

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
