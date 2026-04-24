package me.katze.powerac.api;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import me.katze.powerac.PowerAC;
import me.katze.powerac.manager.PlayerManager;
import me.katze.powerac.player.PowerPlayer;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public class PowerACApiImpl implements PowerACApi {

    private final PowerAC plugin;
    private final PlayerManager playerManager;

    @Override
    public PowerACPlayerSnapshot getPlayerData(UUID uuid) {
        PowerPlayer player = playerManager.get(uuid);
        if (player == null) {
            return null;
        }
        return new PowerACPlayerSnapshot(
            player.getUuid(),
            player.getName(),
            player.getViolationLevel(),
            player.getDetectionCount(),
            playerManager.isAlertsEnabled(uuid),
            plugin.getServer().getPlayer(uuid) != null
        );
    }

    @Override
    public PowerACPlayerSnapshot getPlayerData(Player player) {
        if (player == null) {
            return null;
        }
        return getPlayerData(player.getUniqueId());
    }
}
