package me.katze.powerac.api.event;

import lombok.Getter;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public class PowerACAlertEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String playerName;
    private final String playerUuid;
    private final String module;
    private final String reason;
    private final String formattedMessage;
    private final int audienceCount;
    private final boolean sentToConsole;

    public PowerACAlertEvent(
        String playerName,
        String playerUuid,
        String module,
        String reason,
        String formattedMessage,
        int audienceCount,
        boolean sentToConsole
    ) {
        super(false);
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.module = module;
        this.reason = reason;
        this.formattedMessage = formattedMessage;
        this.audienceCount = audienceCount;
        this.sentToConsole = sentToConsole;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
