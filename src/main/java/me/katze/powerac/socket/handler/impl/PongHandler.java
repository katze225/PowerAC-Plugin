package me.katze.powerac.socket.handler.impl;

import com.google.gson.JsonObject;
import me.katze.powerac.socket.SocketClient;
import me.katze.powerac.socket.handler.SocketHandler;

public final class PongHandler extends SocketHandler {

    @Override
    public String getType() {
        return "pong";
    }

    @Override
    public void handle(SocketClient client, JsonObject payload) {
        client.handlePong();
    }
}
