package me.katze.powerac.module;

import me.katze.powerac.PowerAC;
import me.katze.powerac.manager.PlayerManager;
import me.katze.powerac.player.PowerPlayer;

public abstract class Module implements IModule {

    private final PowerAC plugin;
    private final PlayerManager playerManager;
    private final PowerPlayer player;
    private final String key;
    private final String name;

    protected Module(PowerAC plugin, PlayerManager playerManager, PowerPlayer player) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.player = player;

        ModuleInfo moduleInfo = getClass().getAnnotation(ModuleInfo.class);
        if (moduleInfo == null) {
            throw new IllegalStateException(
                "Module annotation is missing for " + getClass().getName()
            );
        }

        String resolvedName = moduleInfo.value() == null ? "" : moduleInfo.value().trim();
        if (resolvedName.isEmpty()) {
            throw new IllegalStateException(
                "Module name is empty for " + getClass().getName()
            );
        }

        this.name = resolvedName;
        this.key = normalizeKey(resolvedName);
    }

    @Override
    public final String getKey() {
        return key;
    }

    @Override
    public final String getName() {
        return name;
    }

    public final PowerAC getPlugin() {
        return plugin;
    }

    public final PlayerManager getPlayerManager() {
        return playerManager;
    }

    public final PowerPlayer getPlayer() {
        return player;
    }

    private String normalizeKey(String moduleName) {
        StringBuilder builder = new StringBuilder(moduleName.length());
        for (int i = 0; i < moduleName.length(); i++) {
            char current = moduleName.charAt(i);
            if (Character.isLetterOrDigit(current)) {
                builder.append(Character.toLowerCase(current));
            }
        }
        if (builder.length() == 0) {
            throw new IllegalStateException(
                "Unable to generate module key for " + getClass().getName()
            );
        }
        return builder.toString();
    }
}
