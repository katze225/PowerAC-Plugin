package me.katze.powerac.tracker.impl;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import lombok.Getter;
import me.katze.powerac.player.PowerPlayer;
import me.katze.powerac.tracker.Tracker;

@Getter
public class ActionTracker extends Tracker {

    private long lastAttackTimeMs;
    private int lastAttackedEntityId = -1;

    public ActionTracker(PowerPlayer player) {
        super(player);
    }

    @Override
    public void registerIncomingPreHandler(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) {
            return;
        }

        WrapperPlayClientInteractEntity wrapper =
            new WrapperPlayClientInteractEntity(event);
        if (
            wrapper.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK
        ) {
            lastAttackTimeMs = System.currentTimeMillis();
            lastAttackedEntityId = wrapper.getEntityId();
        }
    }

    public void markAttack(long now) {
        this.lastAttackTimeMs = now;
    }

    public long getTimeSinceLastAttack(long now) {
        return lastAttackTimeMs == 0L ? Long.MAX_VALUE : now - lastAttackTimeMs;
    }
}
