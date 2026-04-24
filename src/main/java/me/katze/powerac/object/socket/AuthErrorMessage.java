package me.katze.powerac.object.socket;

import lombok.Getter;

@Getter
public final class AuthErrorMessage {

    private String type;
    private String message;

    public String getMessageOrDefault() {
        return message == null ? "unknown error" : message;
    }
}
