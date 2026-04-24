package me.katze.powerac.scheduler;

import org.bukkit.scheduler.BukkitTask;

public final class BukkitTaskHandle implements TaskHandle {

    private final BukkitTask task;

    public BukkitTaskHandle(BukkitTask task) {
        this.task = task;
    }

    @Override
    public void cancel() {
        if (task != null) {
            task.cancel();
        }
    }
}
