package dev.phantom.integration.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class MovementUtil {
    /** Vanilla sprinting ground speed in blocks/tick. */
    public static final double BASE_SPEED = 0.2873;

    private MovementUtil() {
    }

    public static boolean isMoving(ClientPlayerEntity p) {
        return p.input != null && (p.input.movementForward != 0 || p.input.movementSideways != 0);
    }

    /** Horizontal unit vector of the current movement input, rotated by the player's yaw. */
    public static Vec3d inputDirection(ClientPlayerEntity p) {
        float f = p.input.movementForward, s = p.input.movementSideways;
        if (f == 0 && s == 0) return Vec3d.ZERO;
        double yaw = Math.toRadians(p.getYaw());
        double x = s * Math.cos(yaw) - f * Math.sin(yaw);
        double z = f * Math.cos(yaw) + s * Math.sin(yaw);
        double len = Math.sqrt(x * x + z * z);
        return new Vec3d(x / len, 0, z / len);
    }

    public static void setHorizontalSpeed(ClientPlayerEntity p, double speed) {
        Vec3d dir = inputDirection(p);
        Vec3d v = p.getVelocity();
        if (dir == Vec3d.ZERO) {
            p.setVelocity(0, v.y, 0);
        } else {
            p.setVelocity(dir.x * speed, v.y, dir.z * speed);
        }
    }

    public static double horizontalSpeed(ClientPlayerEntity p) {
        Vec3d v = p.getVelocity();
        return Math.sqrt(v.x * v.x + v.z * v.z);
    }
}
