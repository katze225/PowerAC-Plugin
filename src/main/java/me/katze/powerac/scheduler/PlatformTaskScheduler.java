package me.katze.powerac.scheduler;

import java.util.UUID;
import org.bukkit.entity.Player;

public interface PlatformTaskScheduler {

    boolean isFolia();

    TaskHandle runGlobal(Runnable task);

    TaskHandle runGlobalLater(Runnable task, long delayTicks);

    TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks);

    TaskHandle runAsync(Runnable task);

    TaskHandle runPlayer(Player player, Runnable task);

    TaskHandle runPlayer(UUID playerId, Runnable task);
}
