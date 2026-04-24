package me.katze.powerac.task;

import lombok.RequiredArgsConstructor;
import me.katze.powerac.manager.PerformanceManager;

@RequiredArgsConstructor
public class PerformanceTask implements Runnable {

    private final PerformanceManager performanceManager;

    @Override
    public void run() {
        if (performanceManager != null) {
            performanceManager.refreshTpsCache();
        }
    }
}
