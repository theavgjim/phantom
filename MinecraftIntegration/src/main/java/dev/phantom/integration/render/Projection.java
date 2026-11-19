package dev.phantom.integration.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Captures the world view/projection matrices each frame so HUD-layer code (nametags, distance
 * indicators) can place 2D text over 3D positions.
 */
public final class Projection {
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f proj = new Matrix4f();
    private final Vector4f tmp = new Vector4f();
    private double cx, cy, cz;
    private boolean valid;

    public void capture(Matrix4f viewRotation, Matrix4f projection, Vec3d camera) {
        view.set(viewRotation);
        proj.set(projection);
        cx = camera.x;
        cy = camera.y;
        cz = camera.z;
        valid = true;
    }

    public void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    /**
     * Projects a world position into scaled GUI coordinates.
     *
     * @param out receives {x, y}; if the point is behind the camera, it receives the mirrored
     *            direction (useful for edge indicators)
     * @return {@code true} if the point is in front of the camera
     */
    public boolean project(double x, double y, double z, float[] out) {
        if (!valid) return false;
        tmp.set((float) (x - cx), (float) (y - cy), (float) (z - cz), 1f);
        tmp.mul(view);
        tmp.mul(proj);
        boolean inFront = tmp.w > 0.0001f;
        float w = Math.abs(tmp.w) < 0.0001f ? 0.0001f : Math.abs(tmp.w);
        float ndcX = tmp.x / w;
        float ndcY = tmp.y / w;
        var window = MinecraftClient.getInstance().getWindow();
        out[0] = (ndcX * 0.5f + 0.5f) * window.getScaledWidth();
        out[1] = (1f - (ndcY * 0.5f + 0.5f)) * window.getScaledHeight();
        return inFront;
    }

    public double distanceToCamera(double x, double y, double z) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
