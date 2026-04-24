package me.katze.powerac.api;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PowerACPlayerSnapshot {

    private final UUID uuid;
    private final String name;
    private final double violationLevel;
    private final int detectionCount;
    private final boolean alertsEnabled;
    private final boolean online;

}
