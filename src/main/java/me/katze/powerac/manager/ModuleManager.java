package me.katze.powerac.manager;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import lombok.Getter;
import me.katze.powerac.module.IModule;
import me.katze.powerac.player.PowerPlayer;

@Getter
public final class ModuleManager {

    private final PowerPlayer player;
    private final Map<Class<? extends IModule>, IModule> modulesByType = new LinkedHashMap<>();
    private final Map<String, IModule> modulesByKey = new LinkedHashMap<>();

    public ModuleManager(PowerPlayer player) {
        this.player = player;
    }

    public <T extends IModule> void addModule(T module) {
        if (module == null) {
            return;
        }
        modulesByType.put(module.getClass(), module);
        modulesByKey.put(module.getKey().toLowerCase(), module);
    }

    public <T extends IModule> T getModule(Class<T> moduleClass) {
        if (moduleClass == null) {
            return null;
        }
        IModule module = modulesByType.get(moduleClass);
        if (moduleClass.isInstance(module)) {
            return moduleClass.cast(module);
        }
        return null;
    }

    public IModule getModule(String key) {
        if (key == null) {
            return null;
        }
        return modulesByKey.get(key.toLowerCase());
    }

    public Collection<IModule> getModules() {
        return modulesByType.values();
    }

    public void handlePacketReceive(PacketReceiveEvent event) {
        for (IModule module : modulesByType.values()) {
            module.onPacketReceive(event);
        }
    }

    public void handlePacketSend(PacketSendEvent event) {
        for (IModule module : modulesByType.values()) {
            module.onPacketSend(event);
        }
    }
}
