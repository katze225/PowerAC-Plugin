package me.katze.powerac.manager;

import lombok.Getter;
import me.katze.powerac.PowerAC;
import me.katze.powerac.utility.BukkitUtility;

@Getter
public class PerformanceManager {

    private final PowerAC plugin;
    private double minTpsToCheck;
    private boolean checksPausedByTps;
    private double cachedTps = 20.0D;

    public PerformanceManager(PowerAC plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        minTpsToCheck = plugin.getConfigManager().getMinTpsToCheck();
        checksPausedByTps = false;
        refreshTpsCache();
    }

    public void refreshTpsCache() {
        cachedTps = BukkitUtility.resolveCurrentTps(20.0D);
    }

    public boolean isChecksPausedByTps() {
        if (minTpsToCheck <= 0D) {
            return false;
        }

        double currentTps = cachedTps;
        double disableThreshold = Math.max(0D, minTpsToCheck - 0.1D);

        if (checksPausedByTps) {
            if (currentTps >= minTpsToCheck) {
                checksPausedByTps = false;
            }
        } else if (currentTps < disableThreshold) {
            checksPausedByTps = true;
        }

        return checksPausedByTps;
    }
}
