package me.katze.powerac.player;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import me.katze.powerac.manager.ModuleManager;
import me.katze.powerac.manager.ViolationManager;
import me.katze.powerac.module.impl.RotationModule;
import me.katze.powerac.tracker.Tracker;
import me.katze.powerac.tracker.impl.ActionTracker;
import me.katze.powerac.tracker.impl.RotationTracker;

@Getter
@Setter
public class PowerPlayer {

    private final UUID uuid;
    private String name;
    private int violationLevel;
    private int detectionCount;
    private int ticksExisted;
    private int entityId = -1;
    private String brand = "unknown";
    private ClientVersion clientVersion;
    private User user;
    private final ModuleManager moduleManager;
    private final ViolationManager violationManager;
    private final Map<Class<? extends Tracker>, Tracker> trackersByType = new HashMap<>();
    private final List<Tracker> trackers = new ArrayList<>();

    private RotationTracker rotationTracker;
    private ActionTracker actionTracker;

    public PowerPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.moduleManager = new ModuleManager(this);
        this.violationManager = new ViolationManager(this);
    }

    public int addCheckViolation(String check, int amount) {
        return addViolation(amount);
    }

    public int addViolation(int amount) {
        if (amount <= 0) {
            return violationLevel;
        }
        violationLevel += amount;
        return violationLevel;
    }

    public int getCheckViolationLevel(String check) {
        return violationLevel;
    }

    public void resetCheckViolationLevel(String check) {
        violationLevel = 0;
    }

    public int incrementDetectionCount() {
        detectionCount++;
        return detectionCount;
    }

    public int getViolationLevel() {
        return violationLevel;
    }

    public void resetViolationLevels() {
        violationLevel = 0;
    }

    public <T extends Tracker> void addTracker(T tracker) {
        if (tracker == null) {
            return;
        }
        trackersByType.put(tracker.getClass(), tracker);
        if (!trackers.contains(tracker)) {
            trackers.add(tracker);
        }

        if (tracker instanceof RotationTracker) {
            this.rotationTracker = (RotationTracker) tracker;
        } else if (tracker instanceof ActionTracker) {
            this.actionTracker = (ActionTracker) tracker;
        }
    }

    public <T extends Tracker> T getTracker(Class<T> trackerClass) {
        if (trackerClass == null) {
            return null;
        }
        Tracker tracker = trackersByType.get(trackerClass);
        if (trackerClass.isInstance(tracker)) {
            return trackerClass.cast(tracker);
        }
        return null;
    }

    public Collection<Tracker> getTrackers() {
        return Collections.unmodifiableList(trackers);
    }

    public RotationModule getRotationModule() {
        return moduleManager.getModule(RotationModule.class);
    }
}
