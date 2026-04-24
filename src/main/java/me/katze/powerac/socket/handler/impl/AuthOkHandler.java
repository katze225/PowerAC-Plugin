package me.katze.powerac.socket.handler.impl;

import com.google.gson.JsonObject;
import me.katze.powerac.object.socket.AuthInfo;
import me.katze.powerac.object.socket.AuthOkMessage;
import me.katze.powerac.socket.SocketClient;
import me.katze.powerac.socket.handler.SocketHandler;

public final class AuthOkHandler extends SocketHandler {

    @Override
    public String getType() {
        return "auth_ok";
    }

    @Override
    public void handle(SocketClient client, JsonObject payload) {
        AuthOkMessage message = parse(payload, AuthOkMessage.class);
        client.handleAuthOk(
            new AuthInfo(
                message.getKey(),
                message.getServer_uuid(),
                message.getPlan(),
                message.getSubscription_expires_at(),
                message.getPlayer_limit(),
                message.getRequests_per_hour(),
                message.getLatest_version(),
                message.getAccount_id()
            )
        );
    }
}
