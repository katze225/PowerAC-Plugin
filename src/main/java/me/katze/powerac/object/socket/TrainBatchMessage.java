package me.katze.powerac.object.socket;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class TrainBatchMessage {

    private final String type;
    private final String check_type;
    private final String request_id;
    private final String player_name;
    private final String player_uuid;
    private final int label;
    private final List<?> history;
}
