package me.katze.powerac.object.socket;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class AiCheckMessage {

    private final String type;
    private final String check_type;
    private final String request_id;
    private final String player_name;
    private final String player_uuid;
    private final Boolean is_legit;
    private final Boolean use_global_model;
    private final Boolean use_private_model;
    private final List<?> history;
}
