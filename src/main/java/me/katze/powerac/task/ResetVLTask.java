package me.katze.powerac.task;

import lombok.RequiredArgsConstructor;
import me.katze.powerac.manager.PlayerManager;

@RequiredArgsConstructor
public class ResetVLTask implements Runnable {

    private final PlayerManager playerManager;

    @Override
    public void run() {
        if (playerManager != null) {
            playerManager.clearAllViolationLevels();
        }
    }
}
