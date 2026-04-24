package me.katze.powerac.scheduler;

import java.util.UUID;
import me.katze.powerac.PowerAC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

public final class BukkitPlatformTaskScheduler implements PlatformTaskScheduler {

    private final PowerAC plugin;
    private final BukkitScheduler scheduler;

    public BukkitPlatformTaskScheduler(PowerAC plugin) {
        this.plugin = plugin;
        this.scheduler = Bukkit.getScheduler();
    }

    @Override
    public boolean isFolia() {
        return false;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        return new BukkitTaskHandle(scheduler.runTask(plugin, task));
    }

    @Override
    public TaskHandle runGlobalLater(Runnable task, long delayTicks) {
        return new BukkitTaskHandle(scheduler.runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        return new BukkitTaskHandle(
            scheduler.runTaskTimer(plugin, task, delayTicks, periodTicks)
        );
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        return new BukkitTaskHandle(scheduler.runTaskAsynchronously(plugin, task));
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return TaskHandle.NO_OP;
        }
        return runGlobal(task);
    }

    @Override
    public TaskHandle runPlayer(UUID playerId, Runnable task) {
        if (playerId == null) {
            return TaskHandle.NO_OP;
        }
        return runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                task.run();
            }
        });
    }
}
