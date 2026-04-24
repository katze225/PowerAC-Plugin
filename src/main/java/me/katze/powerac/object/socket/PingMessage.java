package me.katze.powerac.object.socket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class PingMessage {

    private final String type;
    private final long ts;
}
