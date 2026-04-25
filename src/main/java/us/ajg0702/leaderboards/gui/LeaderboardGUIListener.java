package us.ajg0702.leaderboards.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import us.ajg0702.leaderboards.LeaderboardPlugin;
import us.ajg0702.leaderboards.boards.TimedType;

public class LeaderboardGUIListener implements Listener {

    private final LeaderboardPlugin plugin;

    public LeaderboardGUIListener(LeaderboardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof LeaderboardHolder) {
            event.setCancelled(true);

            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();

            int slot = event.getRawSlot();
            if (slot < 0 || slot >= event.getInventory().getSize()) return;

            // Time toggle button clicked
            if (slot == LeaderboardGUI.TIME_TOGGLE_SLOT) {
                LeaderboardHolder holder = (LeaderboardHolder) event.getInventory().getHolder();
                TimedType next = LeaderboardGUI.getNextTimeType(holder.getCurrentType());
                player.closeInventory();
                LeaderboardGUI.open(player, next, plugin);
            }
        }

        if (event.getInventory().getHolder() instanceof ProfileHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LeaderboardHolder
                || event.getInventory().getHolder() instanceof ProfileHolder) {
            event.setCancelled(true);
        }
    }
}
