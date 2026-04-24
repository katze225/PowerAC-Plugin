package me.katze.powerac.object.socket;

import lombok.Getter;

@Getter
public final class SocketEnvelope {

    private String type;

    public String getTypeOrEmpty() {
        return type == null ? "" : type;
    }
}
