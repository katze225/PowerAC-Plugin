package me.katze.powerac.object.socket;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class ServerStatusMessage {

    private final String type;
    private final String server_uuid;
    private final String plugin_version;
    private final String minecraft_version;
    private final int player_count;
    private final List<SocketPlayerInfo> players;
}
