package us.ajg0702.leaderboards.gui;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ProfileHolder implements InventoryHolder {

    private final OfflinePlayer target;

    public ProfileHolder(OfflinePlayer target) {
        this.target = target;
    }

    public OfflinePlayer getTarget() {
        return target;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
