package me.katze.powerac.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.event.UserLoginEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import lombok.Getter;
import me.katze.powerac.PowerAC;
import me.katze.powerac.manager.PlayerManager;
import me.katze.powerac.utility.StringUtility;

@Getter
public class ConnectionPacket extends PacketListenerAbstract {

    private final PowerAC plugin;
    private final PlayerManager playerManager;

    public ConnectionPacket(PowerAC plugin, PlayerManager playerManager) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Login.Server.LOGIN_SUCCESS) {
            playerManager.ensureTrackedPlayer(event.getUser());
        }
    }

    @Override
    public void onUserLogin(UserLoginEvent event) {
        User user = event.getUser();
        if (user == null) {
            return;
        }
        if (plugin.getSocketClient().isAuthenticated() && playerManager.isPlayerLimitReached()) {
            user.sendMessage(StringUtility.getString(plugin.getPlayerLimitKickMessage()));
            user.closeConnection();
            return;
        }

        playerManager.handleJoin(user);
        plugin.getSocketClient().syncServerState();
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        if (event.getUser() == null || event.getUser().getUUID() == null) {
            return;
        }
        playerManager.handleQuit(event.getUser().getUUID());
        plugin.getSocketClient().syncServerState();
    }
}
