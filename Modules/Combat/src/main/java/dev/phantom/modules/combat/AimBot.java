package dev.phantom.modules.combat;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.Render3DEvent;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.Rotations;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Aim training: smoothly assists the crosshair toward dummies / mobs inside a field of view and
 * measures how much of the time the crosshair actually stays on target. Private worlds only.
 */
public final class AimBot extends ClientModule {
    private final CombatTargets targets = new CombatTargets();
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum target distance", 6, 2, 32, 0.5, "m"));
    private final NumberSetting fov = add(new NumberSetting("FOV", "Only assist toward targets inside this cone", 60, 5, 180, 1, "°"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Turn speed in degrees per second", 180, 20, 720, 10));
    private final BooleanSetting onlyWhileAttacking = add(new BooleanSetting("Only While Attacking", "Assist only while the attack key is held", false));
    private final BooleanSetting stats = add(new BooleanSetting("Training Stats", "Show on-target percentage in the module list", true));
    private LivingEntity target;
    private int samples;
    private int onTarget;

    public AimBot() {
        super("AimBot", "Aim training assist toward dummies and mobs in private worlds", Category.COMBAT);
        privateWorldOnly();
        add(targets.dummies);
        add(targets.hostile);
        add(targets.passive);
        on(TickEvent.class, e -> tick());
        on(Render3DEvent.class, this::frame);
    }

    @Override
    protected void onEnable() {
        samples = 0;
        onTarget = 0;
    }

    private void tick() {
        var p = mc.player;
        double r = range.getDouble();
        float maxAngle = fov.getFloat() / 2f;
        target = null;
        float best = Float.MAX_VALUE;
        for (var en : client().entities().get()) {
            if (en.distanceSq() > r * r) break;
            if (!(en.entity() instanceof LivingEntity le) || !le.isAlive() || !targets.test(en.kind())) continue;
            if (!p.canSee(le)) continue;
            float a = Rotations.angleTo(p, aimPoint(le, 1f));
            if (a <= maxAngle && a < best) {
                best = a;
                target = le;
            }
        }
        if (target != null) {
            client().targets().set(target);
            samples++;
            if (mc.targetedEntity == target) onTarget++;
        }
    }

    private void frame(Render3DEvent e) {
        if (target == null || mc.currentScreen != null) return;
        if (onlyWhileAttacking.isOn() && !mc.options.attackKey.isPressed()) return;
        float[] rot = Rotations.toward(mc.player.getEyePos(), aimPoint(target, e.tickDelta));
        Rotations.stepToward(mc.player, rot, speed.getFloat() * e.frameTime);
    }

    private static Vec3d aimPoint(LivingEntity le, float tickDelta) {
        Vec3d p = le.getLerpedPos(tickDelta);
        return new Vec3d(p.x, p.y + le.getHeight() * 0.6, p.z);
    }

    @Override
    public String getSuffix() {
        if (!stats.isOn() || samples == 0) return null;
        return String.format(Locale.ROOT, "%d%%", Math.round(onTarget * 100f / samples));
    }
}
