package us.ajg0702.leaderboards.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import us.ajg0702.leaderboards.boards.TimedType;
import us.ajg0702.utils.foliacompat.Task;

public class LeaderboardHolder implements InventoryHolder {

    private TimedType currentType;
    private Task refreshTask;

    public LeaderboardHolder(TimedType type) {
        this.currentType = type;
    }

    public TimedType getCurrentType() {
        return currentType;
    }

    public void setCurrentType(TimedType type) {
        this.currentType = type;
    }

    public Task getRefreshTask() {
        return refreshTask;
    }

    public void setRefreshTask(Task task) {
        this.refreshTask = task;
    }

    public void cancelRefreshTask() {
        if (refreshTask != null) {
            try {
                refreshTask.cancel();
            } catch (Exception ignored) {}
            refreshTask = null;
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
