package me.katze.powerac.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import java.util.UUID;
import lombok.Getter;
import me.katze.powerac.manager.PlayerManager;
import me.katze.powerac.player.PowerPlayer;
import me.katze.powerac.tracker.Tracker;

@Getter
public final class TrackerPacket extends PacketListenerAbstract {

    private final PlayerManager playerManager;

    public TrackerPacket(PlayerManager playerManager) {
        super(PacketListenerPriority.HIGHEST);
        this.playerManager = playerManager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getUser() == null) return;

        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;

        PowerPlayer player = playerManager.get(uuid);
        if (player == null) {
            player = playerManager.ensureTrackedPlayer(event.getUser());
        }
        if (player == null || player.getTrackers() == null) {
            return;
        }

        for (Tracker tracker : player.getTrackers()) {
            tracker.registerIncomingPreHandler(event);
        }
        if (player.getModuleManager() != null) {
            player.getModuleManager().handlePacketReceive(event);
        }
        for (Tracker tracker : player.getTrackers()) {
            tracker.registerIncomingPostHandler(event);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getUser() == null) return;

        UUID uuid = event.getUser().getUUID();
        if (uuid == null) return;

        PowerPlayer player = playerManager.get(uuid);
        if (player == null) {
            player = playerManager.ensureTrackedPlayer(event.getUser());
        }
        if (player == null || player.getTrackers() == null) {
            return;
        }

        for (Tracker tracker : player.getTrackers()) {
            tracker.registerOutgoingPreHandler(event);
        }
        if (player.getModuleManager() != null) {
            player.getModuleManager().handlePacketSend(event);
        }
        for (Tracker tracker : player.getTrackers()) {
            tracker.registerOutgoingPostHandler(event);
        }
    }
}
