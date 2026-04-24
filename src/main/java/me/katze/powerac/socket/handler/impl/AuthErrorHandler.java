package me.katze.powerac.socket.handler.impl;

import com.google.gson.JsonObject;
import me.katze.powerac.object.socket.AuthErrorMessage;
import me.katze.powerac.socket.SocketClient;
import me.katze.powerac.socket.handler.SocketHandler;

public final class AuthErrorHandler extends SocketHandler {

    @Override
    public String getType() {
        return "auth_error";
    }

    @Override
    public void handle(SocketClient client, JsonObject payload) {
        AuthErrorMessage message = parse(payload, AuthErrorMessage.class);
        client.handleAuthError(message.getMessageOrDefault());
    }
}
