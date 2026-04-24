package me.katze.powerac.api.event;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public class PowerACDetectEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String playerName;
    private final String playerUuid;
    private final String module;
    private final String reason;
    private final double probability;
    private final double violationLevel;
    private final double addedViolationLevel;
    private final int detectionCount;

    public PowerACDetectEvent(
        String playerName,
        String playerUuid,
        String module,
        String reason,
        double probability,
        double violationLevel,
        double addedViolationLevel,
        int detectionCount
    ) {
        super(false);
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.module = module;
        this.reason = reason;
        this.probability = probability;
        this.violationLevel = violationLevel;
        this.addedViolationLevel = addedViolationLevel;
        this.detectionCount = detectionCount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
