package me.katze.powerac.socket.handler;

import com.google.gson.JsonObject;
import me.katze.powerac.socket.SocketClient;

public interface ISocketHandler {
    String getType();
    void handle(SocketClient client, JsonObject payload);
}
