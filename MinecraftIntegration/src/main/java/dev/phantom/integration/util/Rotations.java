package dev.phantom.integration.util;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Rotations {
    private Rotations() {
    }

    /** {yaw, pitch} in degrees that look from {@code from} to {@code to}. */
    public static float[] toward(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90f, 90f)};
    }

    /** Absolute angle between the player's view and the direction to {@code to}. */
    public static float angleTo(ClientPlayerEntity p, Vec3d to) {
        float[] r = toward(p.getEyePos(), to);
        float dy = MathHelper.wrapDegrees(r[0] - p.getYaw());
        float dp = r[1] - p.getPitch();
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    /** Rotates the player's (visible) view toward {@code target} by at most {@code maxStep} degrees. */
    public static void stepToward(ClientPlayerEntity p, float[] target, float maxStep) {
        float dy = MathHelper.wrapDegrees(target[0] - p.getYaw());
        float dp = target[1] - p.getPitch();
        float len = (float) Math.sqrt(dy * dy + dp * dp);
        if (len > maxStep && len > 0) {
            dy *= maxStep / len;
            dp *= maxStep / len;
        }
        p.setYaw(p.getYaw() + dy);
        p.setPitch(MathHelper.clamp(p.getPitch() + dp, -90f, 90f));
    }
}
