package me.katze.powerac.utility;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PacketUtility {

    public boolean isFlying(PacketTypeCommon type) {
        return type == PacketType.Play.Client.PLAYER_FLYING ||
            type == PacketType.Play.Client.PLAYER_POSITION ||
            type == PacketType.Play.Client.PLAYER_ROTATION ||
            type == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION;
    }
}
