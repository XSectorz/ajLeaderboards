package us.ajg0702.leaderboards.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import us.ajg0702.leaderboards.boards.TimedType;

public class LeaderboardHolder implements InventoryHolder {

    private TimedType currentType;

    public LeaderboardHolder(TimedType type) {
        this.currentType = type;
    }

    public TimedType getCurrentType() {
        return currentType;
    }

    public void setCurrentType(TimedType type) {
        this.currentType = type;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
