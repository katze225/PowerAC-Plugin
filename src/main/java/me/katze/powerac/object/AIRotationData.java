package me.katze.powerac.object;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AIRotationData {

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

    public static AIRotationData from(
        AIRotationData previousSample,
        float prevYaw,
        float prevPitch,
        float currYaw,
        float currPitch,
        long timeSinceLastAttack,
        long deltaTimeMs
    ) {
        float deltaTime = deltaTimeMs / 1000f;

        float deltaYawRaw = currYaw - prevYaw;
        float deltaPitchRaw = currPitch - prevPitch;

        while (deltaYawRaw > 180f) deltaYawRaw -= 360f;
        while (deltaYawRaw < -180f) deltaYawRaw += 360f;
        while (deltaPitchRaw > 180f) deltaPitchRaw -= 360f;
        while (deltaPitchRaw < -180f) deltaPitchRaw += 360f;

        float deltaYawAbs = Math.abs(deltaYawRaw);
        float deltaPitchAbs = Math.abs(deltaPitchRaw);

        float yawSpeed = safeDiv(deltaYawAbs, deltaTime);
        float pitchSpeed = safeDiv(deltaPitchAbs, deltaTime);

        float prevYawSpeed = 0f;
        float prevPitchSpeed = 0f;
        if (previousSample != null) {
            prevYawSpeed = safeDiv(
                previousSample.getDeltaYaw(),
                Math.max(0.001f, previousSample.getDeltaTime())
            );
            prevPitchSpeed = safeDiv(
                previousSample.getDeltaPitch(),
                Math.max(0.001f, previousSample.getDeltaTime())
            );
        }

        float yawAccel = Math.abs(safeDiv(yawSpeed - prevYawSpeed, deltaTime));
        float pitchAccel = Math.abs(
            safeDiv(pitchSpeed - prevPitchSpeed, deltaTime)
        );

        float jerkDeltaTime = deltaTime;
        if (previousSample != null) {
            jerkDeltaTime = Math.max(
                0.001f,
                (previousSample.getDeltaTime() + deltaTime) * 0.5f
            );
        }

        float yawJerk = 0f;
        float pitchJerk = 0f;
        if (previousSample != null) {
            yawJerk = Math.abs(
                safeDiv(yawAccel - previousSample.getAccelYaw(), jerkDeltaTime)
            );
            pitchJerk = Math.abs(
                safeDiv(
                    pitchAccel - previousSample.getAccelPitch(),
                    jerkDeltaTime
                )
            );
        }

        float prevDeltaYaw =
            previousSample == null ? 0f : previousSample.getDeltaYaw();
        float prevDeltaPitch =
            previousSample == null ? 0f : previousSample.getDeltaPitch();

        float gcdErrorYaw = computeGcdError(deltaYawAbs, prevDeltaYaw);
        float gcdErrorPitch = computeGcdError(deltaPitchAbs, prevDeltaPitch);

        int attackFlag = timeSinceLastAttack <= 50L ? 1 : 0;

        return new AIRotationData(
            deltaYawAbs,
            deltaPitchAbs,
            yawAccel,
            pitchAccel,
            yawJerk,
            pitchJerk,
            gcdErrorYaw,
            gcdErrorPitch,
            deltaTime,
            attackFlag
        );
    }

    private static float safeDiv(float value, float denom) {
        if (denom <= 1e-6f) {
            return 0f;
        }
        float result = value / denom;
        if (Float.isNaN(result) || Float.isInfinite(result)) {
            return 0f;
        }
        return result;
    }

    private static float computeGcdError(
        float currentValue,
        float referenceValue
    ) {
        float currentAbs = Math.abs(currentValue);
        float referenceAbs = Math.abs(referenceValue);
        if (currentAbs < 1e-6f || referenceAbs < 1e-6f) {
            return 0f;
        }

        final int scale = 10_000;
        int currentScaled = Math.max(1, Math.round(currentAbs * scale));
        int referenceScaled = Math.max(1, Math.round(referenceAbs * scale));

        int gcd = gcdInt(currentScaled, referenceScaled);
        if (gcd <= 1) {
            return 0f;
        }

        float step = gcd / (float) scale;
        if (step < 1e-6f) {
            return 0f;
        }

        float ratio = currentAbs / step;
        float base = (float) Math.floor(ratio);
        float remainder = currentAbs - step * base;
        float wrappedRemainder = Math.min(remainder, step - remainder);
        if (
            Float.isNaN(wrappedRemainder) || Float.isInfinite(wrappedRemainder)
        ) {
            return 0f;
        }
        return Math.abs(wrappedRemainder);
    }

    private static int gcdInt(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            int tmp = a % b;
            a = b;
            b = tmp;
        }
        return a;
    }
}
