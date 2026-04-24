package me.katze.powerac.object;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AIMovementData {

    private final float deltaX;
    private final float deltaY;
    private final float deltaZ;
    private final float deltaXZ;
    private final float accelXZ;
    private final float accelY;

    private final int onGroundClient;
    private final int onGroundServer;
    private final int collidedHorizontally;
    private final int onClimbable;

    private final int inWater;
    private final int inLava;
    private final int isSwimming;

    private final int sprinting;
    private final int sneaking;
    private final int isGliding;
    private final int isFlying;

    private final float velocityX;
    private final float velocityY;
    private final float velocityZ;
    private final float velocityXZ;
    private final float horizontalVelocityRatio;
    private final float verticalVelocityRatio;
    private final int ticksSinceVelocity;

    private final int speedLevel;
    private final int slownessLevel;
    private final int jumpBoostLevel;
    private final int slowFallingLevel;
    private final int levitationLevel;
    private final int dolphinsGrace;

    private final float movementSpeedAttr;
    private final float knockbackResistance;

    private final float friction;
    private final int blockUnderType;
    private final int collidedVerticallyUp;
    private final int vehicleType;

    private final float deltaYaw;
    private final float moveAngleDiff;

    private final int useTicks;
    private final int usedItemCategory;
    private final int riptideTicks;
    private final int airTicks;
    private final int groundTicks;
    private final int waterTicks;
    private final int climbTicks;
    private final float fallDistance;
    private final int waterSurfaceState;
    private final float deltaTime;
}
