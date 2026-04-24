package me.katze.powerac.scheduler;

import me.katze.powerac.PowerAC;

public final class TaskSchedulers {

    private TaskSchedulers() {
    }

    public static PlatformTaskScheduler create(PowerAC plugin) {
        return isFoliaAvailable()
            ? new FoliaPlatformTaskScheduler(plugin)
            : new BukkitPlatformTaskScheduler(plugin);
    }

    private static boolean isFoliaAvailable() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
