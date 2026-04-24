package me.katze.powerac.object.socket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class TrainClearMessage {

    private final String type;
    private final String check_type;
    private final String request_id;
}
