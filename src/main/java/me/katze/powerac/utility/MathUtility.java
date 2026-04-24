package me.katze.powerac.utility;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MathUtility {

    public int floorDouble(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public double hypot(double... values) {
        double total = 0D;
        for (double value : values) {
            total += value * value;
        }
        return Math.sqrt(total);
    }

    public float wrapYaw(float yaw) {
        float wrapped = yaw % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
