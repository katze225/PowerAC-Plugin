package me.katze.powerac.object.socket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class AuthMessage {

    private final String type;
    private final String apikey;
    private final String plugin_version;
    private final String minecraft_version;
}
