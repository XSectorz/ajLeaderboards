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
        if (!(event.getInventory().getHolder() instanceof LeaderboardHolder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        TimedType clickedType = LeaderboardGUI.getTimeTypeForSlot(slot);
        if (clickedType != null) {
            LeaderboardHolder holder = (LeaderboardHolder) event.getInventory().getHolder();
            if (holder.getCurrentType() != clickedType) {
                player.closeInventory();
                LeaderboardGUI.open(player, clickedType, plugin);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof LeaderboardHolder) {
            event.setCancelled(true);
        }
    }
}
