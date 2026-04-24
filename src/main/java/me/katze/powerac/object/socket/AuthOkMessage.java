package me.katze.powerac.object.socket;

import lombok.Getter;

@Getter
public final class AuthOkMessage {

    private String type;
    private String key;
    private String server_uuid;
    private String plan;
    private String subscription_expires_at;
    private Integer player_limit;
    private Integer requests_per_hour;
    private String latest_version;
    private String account_id;
}
