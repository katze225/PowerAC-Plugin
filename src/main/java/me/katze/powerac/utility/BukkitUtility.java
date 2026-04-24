package me.katze.powerac.utility;

import org.bukkit.Bukkit;

public final class BukkitUtility {

    private BukkitUtility() {}

    public static double resolveCurrentTps(double fallback) {
        try {
            double[] values = Bukkit.getServer().getTPS();
            if (values == null || values.length == 0 || !Double.isFinite(values[0])) {
                return fallback;
            }
            return values[0];
        } catch (Throwable throwable) {
            return fallback;
        }
    }
}
