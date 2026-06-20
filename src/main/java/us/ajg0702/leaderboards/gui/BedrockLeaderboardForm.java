package us.ajg0702.leaderboards.gui;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.boards.StatEntry;
import us.ajg0702.leaderboards.boards.TimedType;

import java.text.NumberFormat;
import java.util.*;

/**
 * Bedrock Edition leaderboard using Floodgate Cumulus SimpleForm.
 *
 * Rules (matching the XSTeams Bedrock guidelines):
 *   - No emojis in any text. Bedrock's default font renders them as ☐.
 *   - Navigation buttons (back, toggle sort) have NO image — plain text only,
 *     per user request.
 *   - Every FormImage.Type.PATH texture must be a known-valid Bedrock vanilla
 *     resource pack path. {@code textures/items/skull} and the various
 *     {@code skull_*} item textures do not exist in Bedrock vanilla, so deaths
 *     and mobs_kill use {@code bone} / {@code rotten_flesh} instead.
 */
public class BedrockLeaderboardForm {

    // Bedrock texture paths — verified against vanilla resource pack
    private static final Map<String, String> TEXTURES = new LinkedHashMap<>();
    static {
        TEXTURES.put("money",        "textures/items/emerald");
        TEXTURES.put("kills",        "textures/items/iron_sword");
        TEXTURES.put("deaths",       "textures/items/bone");           // was skull (invalid)
        TEXTURES.put("playtime",     "textures/items/compass_item");
        TEXTURES.put("fishing",      "textures/items/fishing_rod_uncast");
        TEXTURES.put("blocks_break", "textures/items/diamond_pickaxe");
        TEXTURES.put("blocks_place", "textures/blocks/brick");
        TEXTURES.put("mobs_kill",    "textures/items/rotten_flesh");   // was skull_zombie (invalid)
        TEXTURES.put("sell",         "textures/items/gold_ingot");
        TEXTURES.put("buy",          "textures/items/diamond");
    }

    private static final String[] TIME_LABELS = {
        "ทั้งหมด",
        "รายวัน",
        "รายสัปดาห์",
        "รายเดือน"
    };
    private static final TimedType[] TIME_TYPES = {TimedType.ALLTIME, TimedType.DAILY, TimedType.WEEKLY, TimedType.MONTHLY};

    // Category display names (clean Thai + English)
    private static final String[] CAT_NAMES = {
        "เงิน (Money)",
        "ฆ่าผู้เล่น (Kills)",
        "ตาย (Deaths)",
        "เวลาเล่น (Playtime)",
        "ตกปลา (Fishing)",
        "ขุดบล็อก (Blocks Break)",
        "วางบล็อก (Blocks Place)",
        "ฆ่ามอบ (Mobs Kill)",
        "ขายของ (Sell)",
        "ซื้อของ (Buy)"
    };

    // Shared formatter — NumberFormat instances aren't thread-safe; we
    // synchronise the per-call format on this single instance to avoid
    // constructing a new one per /leaderboard open.
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static String formatLong(long v) {
        synchronized (NUMBER_FORMAT) { return NUMBER_FORMAT.format(v); }
    }

    public static boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Throwable ignored) {
            return player.getName().startsWith(".");
        }
    }

    // ==================== Main Category Form ====================

    public static void open(Player player, TimedType type, LeaderboardPlugin plugin) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title("Leaderboard - " + getTimeLabel(type));

        LeaderboardGUI.CategoryDef[] cats = LeaderboardGUI.CATEGORIES;

        for (int i = 0; i < cats.length; i++) {
            LeaderboardGUI.CategoryDef cat = cats[i];
            String texture = TEXTURES.getOrDefault(cat.id, "textures/items/paper");

            // Plain-text label: name + (optional) top-1 preview. No emojis.
            StringBuilder label = new StringBuilder();
            label.append(CAT_NAMES[i]);
            String preview = getTop1Preview(cat, type, plugin);
            if (preview != null) {
                label.append("\n").append(preview);
            }

            builder.button(label.toString(), FormImage.Type.PATH, texture);
        }

        // Time-filter (toggle sort) button — no image, plain text per user request.
        builder.button("เปลี่ยนช่วงเวลา  [" + getTimeLabel(type) + "]");

        builder.closedOrInvalidResultHandler(() -> {});
        builder.validResultHandler(response -> {
            int idx = response.clickedButtonId();
            if (idx >= 0 && idx < cats.length) {
                plugin.getScheduler().runTaskAsynchronously(() ->
                        openDetail(player, cats[idx], type, plugin));
            } else if (idx == cats.length) {
                TimedType next = cycleTime(type);
                plugin.getScheduler().runTaskAsynchronously(() -> open(player, next, plugin));
            }
        });

        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(builder.build());
    }

    // ==================== Category Detail Form ====================

    private static void openDetail(Player player, LeaderboardGUI.CategoryDef cat,
                                    TimedType type, LeaderboardPlugin plugin) {
        LeaderboardRedisCache redis = plugin.getRedisCache();
        LeaderboardSnapshotCache snapshot = plugin.getSnapshotCache();

        int catIdx = getCatIndex(cat.id);
        String catName = catIdx >= 0 ? CAT_NAMES[catIdx] : cat.id;

        // Build ranking content as plain text — no color codes, no emojis, no fancy dashes.
        StringBuilder content = new StringBuilder();
        content.append("===== ").append(getTimeLabel(type)).append(" =====\n\n");

        List<RankEntry> top10 = fetchTop10(cat, type, plugin, redis, snapshot);

        if (top10.isEmpty()) {
            content.append("ยังไม่มีข้อมูล\n");
        } else {
            for (RankEntry e : top10) {
                String score = LeaderboardGUI.formatScore(e.score, cat.boardName);
                content.append("#").append(e.position).append("  ").append(e.name);
                content.append("\n          ").append(score).append("\n");
            }
        }

        // Plain ASCII separator instead of unicode box-drawing — Bedrock-safe.
        content.append("\n---------------\n");
        String playerPos = fetchPlayerPos(player, cat, type, plugin, redis, snapshot);
        content.append("อันดับของคุณ: ");
        content.append(playerPos != null ? "#" + playerPos : "N/A");

        SimpleForm form = SimpleForm.builder()
                .title(catName + " - " + getTimeLabel(type))
                .content(content.toString())
                // Per user request: back + toggle sort buttons carry no image.
                .button("กลับ")
                .button("เปลี่ยนช่วงเวลา  [" + getTimeLabel(cycleTime(type)) + "]")
                .closedOrInvalidResultHandler(() -> {})
                .validResultHandler(response -> {
                    int idx = response.clickedButtonId();
                    if (idx == 0) {
                        open(player, type, plugin);
                    } else if (idx == 1) {
                        TimedType next = cycleTime(type);
                        plugin.getScheduler().runTaskAsynchronously(() ->
                                openDetail(player, cat, next, plugin));
                    }
                })
                .build();

        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }

    // ==================== Data Fetching (Redis -> Snapshot -> DB) ====================

    private static List<RankEntry> fetchTop10(LeaderboardGUI.CategoryDef cat, TimedType type,
                                               LeaderboardPlugin plugin,
                                               LeaderboardRedisCache redis,
                                               LeaderboardSnapshotCache snapshot) {
        List<RankEntry> result = new ArrayList<>();

        if (redis != null && redis.isEnabled()) {
            List<LeaderboardRedisCache.CachedEntry> top = redis.getTop10(cat.boardName, type);
            if (top != null && !top.isEmpty()) {
                for (LeaderboardRedisCache.CachedEntry e : top)
                    result.add(new RankEntry(e.name, e.score, e.position));
                return result;
            }
        }

        if (snapshot != null) {
            List<LeaderboardSnapshotCache.SnapshotEntry> top = snapshot.getTop10(cat.boardName, type);
            if (top != null && !top.isEmpty()) {
                for (LeaderboardSnapshotCache.SnapshotEntry e : top)
                    result.add(new RankEntry(e.name, e.score, e.position));
                return result;
            }
        }

        if (cat.boardName != null && plugin.getTopManager().boardExists(cat.boardName)) {
            for (int pos = 1; pos <= 10; pos++) {
                StatEntry entry = plugin.getTopManager().getCachedStat(pos, cat.boardName, type);
                if (entry == null || !entry.hasPlayer())
                    entry = plugin.getCache().getStat(pos, cat.boardName, type);
                if (entry != null && entry.hasPlayer())
                    result.add(new RankEntry(entry.getPlayerName(), entry.getScore(), pos));
            }
        }
        return result;
    }

    private static String fetchPlayerPos(Player player, LeaderboardGUI.CategoryDef cat,
                                          TimedType type, LeaderboardPlugin plugin,
                                          LeaderboardRedisCache redis,
                                          LeaderboardSnapshotCache snapshot) {
        if (redis != null && redis.isEnabled()) {
            LeaderboardRedisCache.CachedEntry pos = redis.getPlayerPosition(player, cat.boardName, type);
            if (pos != null && pos.position > 0)
                return formatLong(pos.position);
        }

        if (snapshot != null) {
            LeaderboardSnapshotCache.SnapshotEntry pos = snapshot.getPlayerPosition(player.getUniqueId(), cat.boardName, type);
            if (pos != null && pos.position > 0)
                return formatLong(pos.position);
        }

        if (cat.boardName != null && plugin.getTopManager().boardExists(cat.boardName)) {
            StatEntry entry = plugin.getTopManager().getCachedStatEntry(player, cat.boardName, type, false);
            if (entry != null && entry.hasPlayer() && entry.getPosition() > 0)
                return formatLong(entry.getPosition());
        }
        return null;
    }

    // ==================== Helpers ====================

    private static String getTop1Preview(LeaderboardGUI.CategoryDef cat, TimedType type, LeaderboardPlugin plugin) {
        if (cat.boardName == null) return null;
        LeaderboardSnapshotCache sc = plugin.getSnapshotCache();
        if (sc != null) {
            List<LeaderboardSnapshotCache.SnapshotEntry> top = sc.getTop10(cat.boardName, type);
            if (top != null && !top.isEmpty()) {
                LeaderboardSnapshotCache.SnapshotEntry e = top.get(0);
                return "#1 " + e.name + "  " + LeaderboardGUI.formatScore(e.score, cat.boardName);
            }
        }
        return null;
    }

    private static String getTimeLabel(TimedType type) {
        for (int i = 0; i < TIME_TYPES.length; i++)
            if (TIME_TYPES[i] == type) return TIME_LABELS[i];
        return TIME_LABELS[0];
    }

    private static TimedType cycleTime(TimedType current) {
        for (int i = 0; i < TIME_TYPES.length; i++)
            if (TIME_TYPES[i] == current) return TIME_TYPES[(i + 1) % TIME_TYPES.length];
        return TimedType.ALLTIME;
    }

    private static int getCatIndex(String catId) {
        LeaderboardGUI.CategoryDef[] cats = LeaderboardGUI.CATEGORIES;
        for (int i = 0; i < cats.length; i++)
            if (cats[i].id.equals(catId)) return i;
        return -1;
    }

    private static class RankEntry {
        final String name;
        final double score;
        final int position;
        RankEntry(String name, double score, int position) {
            this.name = name;
            this.score = score;
            this.position = position;
        }
    }
}
