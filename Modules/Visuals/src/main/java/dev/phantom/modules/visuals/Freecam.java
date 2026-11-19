package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Detaches the camera from the player. Purely client-side: the player's body stays still and the
 * server sees nothing different. Camera position is interpolated between ticks for smoothness.
 */
public final class Freecam extends ClientModule {
    private final NumberSetting speed = add(new NumberSetting("Speed", "Camera speed (blocks per tick)", 1.0, 0.1, 5.0, 0.1));
    private double x, y, z, px, py, pz;
    private float yaw, pitch;
    private boolean prevCulling = true;
    private boolean active;

    public Freecam() {
        super("Freecam", "Fly the camera freely around the world", Category.VISUALS);
        on(TickEvent.class, e -> tick());
    }

    @Override
    protected void onEnable() {
        if (!inWorld()) {
            setEnabled(false);
            return;
        }
        Vec3d pos = mc.gameRenderer.getCamera().getPos();
        x = px = pos.x;
        y = py = pos.y;
        z = pz = pos.z;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        prevCulling = mc.chunkCullingEnabled;
        mc.chunkCullingEnabled = false;
        active = true;
    }

    @Override
    protected void onDisable() {
        if (active) mc.chunkCullingEnabled = prevCulling;
        active = false;
    }

    private void tick() {
        px = x;
        py = y;
        pz = z;
        if (mc.currentScreen != null) return;
        var o = mc.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        double v = (o.jumpKey.isPressed() ? 1 : 0) - (o.sneakKey.isPressed() ? 1 : 0);
        double sp = speed.getDouble() * (o.sprintKey.isPressed() ? 2 : 1);
        double yr = Math.toRadians(yaw);
        double mx = s * Math.cos(yr) - f * Math.sin(yr);
        double mz = f * Math.cos(yr) + s * Math.sin(yr);
        double len = Math.sqrt(mx * mx + mz * mz);
        if (len > 0) {
            x += mx / len * sp;
            z += mz / len * sp;
        }
        y += v * sp;
    }

    /** Called from the mouse-look hook instead of rotating the player. */
    public void look(double dx, double dy) {
        yaw += (float) dx * 0.15f;
        pitch = MathHelper.clamp(pitch + (float) dy * 0.15f, -90f, 90f);
    }

    public double x(float t) {
        return MathHelper.lerp(t, px, x);
    }

    public double y(float t) {
        return MathHelper.lerp(t, py, y);
    }

    public double z(float t) {
        return MathHelper.lerp(t, pz, z);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}
