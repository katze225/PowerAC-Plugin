package me.katze.powerac.object.socket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.katze.powerac.object.AIRotationData;

@Getter
@AllArgsConstructor
public final class AiRotationSample {

    private final float deltaYaw;
    private final float deltaPitch;
    private final float accelYaw;
    private final float accelPitch;
    private final float jerkYaw;
    private final float jerkPitch;
    private final float gcdErrorYaw;
    private final float gcdErrorPitch;
    private final float deltaTime;
    private final int attackFlag;

    public static AiRotationSample from(AIRotationData data) {
        return new AiRotationSample(
            data.getDeltaYaw(),
            data.getDeltaPitch(),
            data.getAccelYaw(),
            data.getAccelPitch(),
            data.getJerkYaw(),
            data.getJerkPitch(),
            data.getGcdErrorYaw(),
            data.getGcdErrorPitch(),
            data.getDeltaTime(),
            data.getAttackFlag()
        );
    }
}
