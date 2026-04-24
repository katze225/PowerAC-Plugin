package me.katze.powerac.socket.handler.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.katze.powerac.socket.SocketClient;
import me.katze.powerac.socket.handler.ISocketHandler;

public final class ServerStatusOkHandler implements ISocketHandler {

    private int getOptionalInt(JsonObject payload, String key, int fallback) {
        JsonElement value = payload.get(key);
        if (value == null || value.isJsonNull()) {
            return fallback;
        }
        try {
            return value.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    @Override
    public void handle(SocketClient client, JsonObject payload) {
        int playerLimit = getOptionalInt(payload, "player_limit", 0);
        int requestsPerHour = getOptionalInt(payload, "requests_per_hour", 0);
        client.handleServerStatusOk(playerLimit, requestsPerHour);
    }

    @Override
    public String getType() {
        return "server_status_ok";
    }
}
