package dev.phantom.integration.event;

import dev.phantom.integration.render.Render3D;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

/** Posted once per frame after the world is drawn. Shapes added to {@link #renderer} are batched. */
public final class Render3DEvent {
    public static final Render3DEvent INSTANCE = new Render3DEvent();

    public Render3D renderer;
    public Camera camera;
    public Vec3d cameraPos;
    public float tickDelta;
    /** Seconds since the previous frame (for smooth, frame-rate independent motion). */
    public float frameTime;

    private Render3DEvent() {
    }
}
