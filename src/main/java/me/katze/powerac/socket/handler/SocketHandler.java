package me.katze.powerac.socket.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.katze.powerac.socket.SocketClient;

public abstract class SocketHandler implements ISocketHandler {

    protected static final Gson GSON = new Gson();

    protected final <T> T parse(JsonObject payload, Class<T> type) {
        return GSON.fromJson(payload, type);
    }

    @Override
    public abstract String getType();

    @Override
    public abstract void handle(SocketClient client, JsonObject payload);
}
