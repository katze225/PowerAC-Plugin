package me.katze.powerac.object.socket;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class AuthInfo {

    private final String key;
    private final String serverUuid;
    private final String plan;
    private final String subscriptionExpiresAt;
    private final Integer playerLimit;
    private final Integer requestsPerHour;
    private final String latestVersion;
    private final String accountId;
}
