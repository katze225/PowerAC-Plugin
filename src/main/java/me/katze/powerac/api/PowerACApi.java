package me.katze.powerac.api;

import java.util.UUID;
import org.bukkit.entity.Player;

public interface PowerACApi {

    PowerACPlayerSnapshot getPlayerData(UUID uuid);

    PowerACPlayerSnapshot getPlayerData(Player player);
}
