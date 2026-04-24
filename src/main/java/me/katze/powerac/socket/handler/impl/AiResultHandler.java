package me.katze.powerac.socket.handler.impl;

import com.google.gson.JsonObject;
import me.katze.powerac.object.socket.AiResultMessage;
import me.katze.powerac.socket.SocketClient;
import me.katze.powerac.socket.handler.SocketHandler;

public final class AiResultHandler extends SocketHandler {

    @Override
    public String getType() {
        return "ai_result";
    }

    @Override
    public void handle(SocketClient client, JsonObject payload) {
        AiResultMessage message = parse(payload, AiResultMessage.class);
        client.handleAiResult(
            message.getRequestIdOrEmpty(),
            message.getStatusOrDefault(),
            message.isDetected(),
            message.isLimitReached(),
            message.getProbabilityOrUnknown(),
            message.getReasonOrEmpty(),
            message.getCheckUuidOrEmpty(),
            message.getResultsOrEmpty()
        );
    }
}
