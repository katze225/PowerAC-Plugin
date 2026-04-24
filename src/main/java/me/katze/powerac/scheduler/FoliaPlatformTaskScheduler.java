package me.katze.powerac.scheduler;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;
import me.katze.powerac.PowerAC;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class FoliaPlatformTaskScheduler implements PlatformTaskScheduler {

    private final PowerAC plugin;
    private final Object globalRegionScheduler;
    private final Object asyncScheduler;

    public FoliaPlatformTaskScheduler(PowerAC plugin) {
        this.plugin = plugin;
        this.globalRegionScheduler = invokeNoArgs(Bukkit.class, null, "getGlobalRegionScheduler");
        this.asyncScheduler = invokeNoArgs(Bukkit.class, null, "getAsyncScheduler");
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public TaskHandle runGlobal(Runnable task) {
        Object scheduled = invoke(
            globalRegionScheduler,
            "run",
            plugin,
            consumer(task)
        );
        return scheduled == null ? TaskHandle.NO_OP : new ReflectiveTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runGlobalLater(Runnable task, long delayTicks) {
        Object scheduled = invoke(
            globalRegionScheduler,
            "runDelayed",
            plugin,
            consumer(task),
            Long.valueOf(delayTicks)
        );
        return scheduled == null ? TaskHandle.NO_OP : new ReflectiveTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runGlobalTimer(Runnable task, long delayTicks, long periodTicks) {
        Object scheduled = invoke(
            globalRegionScheduler,
            "runAtFixedRate",
            plugin,
            consumer(task),
            Long.valueOf(delayTicks),
            Long.valueOf(periodTicks)
        );
        return scheduled == null ? TaskHandle.NO_OP : new ReflectiveTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runAsync(Runnable task) {
        Object scheduled = invoke(asyncScheduler, "runNow", plugin, consumer(task));
        return scheduled == null ? TaskHandle.NO_OP : new ReflectiveTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runPlayer(Player player, Runnable task) {
        if (player == null || !player.isOnline()) {
            return TaskHandle.NO_OP;
        }

        Object entityScheduler = invokeNoArgs(player.getClass(), player, "getScheduler");
        if (entityScheduler == null) {
            return runGlobal(task);
        }

        Object scheduled = invoke(
            entityScheduler,
            "run",
            plugin,
            consumer(task),
            null
        );
        return scheduled == null ? TaskHandle.NO_OP : new ReflectiveTaskHandle(scheduled);
    }

    @Override
    public TaskHandle runPlayer(UUID playerId, Runnable task) {
        if (playerId == null) {
            return TaskHandle.NO_OP;
        }

        return runGlobal(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                runPlayer(player, task);
            }
        });
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }

    private static Object invokeNoArgs(Class<?> type, Object target, String methodName) {
        try {
            Method method = type.getMethod(methodName);
            return method.invoke(target);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }

        Method method = findMethod(target.getClass(), methodName, args.length);
        if (method == null) {
            return null;
        }

        try {
            return method.invoke(target, args);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, int argumentCount) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.getParameterTypes().length == argumentCount) {
                return method;
            }
        }
        return null;
    }
}
