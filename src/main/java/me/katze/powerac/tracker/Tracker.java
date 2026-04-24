package me.katze.powerac.tracker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import me.katze.powerac.player.PowerPlayer;

@Getter
@RequiredArgsConstructor
public class Tracker {

    protected final PowerPlayer player;

    public void registerIncomingPreHandler(PacketReceiveEvent event) {
    }

    public void registerIncomingPostHandler(PacketReceiveEvent event) {
    }

    public void registerOutgoingPreHandler(PacketSendEvent event) {
    }

    public void registerOutgoingPostHandler(PacketSendEvent event) {
    }
}
