package dev.phantom.modules.combat;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.event.WorldChangeEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.Rotations;
import dev.phantom.integration.world.EntityCache;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;

import java.util.Comparator;

/**
 * Combat testing: attacks training dummies / mobs in range. Private worlds only; never targets
 * players. Rotations are applied to the visible camera (nothing is hidden).
 */
public final class KillAura extends ClientModule {
    private final CombatTargets targets = new CombatTargets();
    private final NumberSetting range = add(new NumberSetting("Range", "Attack range", 3.0, 1.0, 6.0, 0.1, "m"));
    private final ModeSetting priority = add(new ModeSetting("Priority", "Which target first", "Distance", "Distance", "Health", "Angle"));
    private final BooleanSetting cooldown = add(new BooleanSetting("Full Cooldown", "Wait for a fully charged attack", true));
    private final NumberSetting cps = add(new NumberSetting("CPS", "Attacks per second when not waiting for cooldown", 8, 1, 20, 1)
            .visibleWhen(() -> !cooldown().isOn()));
    private final BooleanSetting rotate = add(new BooleanSetting("Look At Target", "Turn the camera toward the target", true));
    private final BooleanSetting lineOfSight = add(new BooleanSetting("Line Of Sight", "Only attack visible targets", true));
    private LivingEntity target;
    private long lastAttack;

    public KillAura() {
        super("KillAura", "Combat testing against dummies and mobs in private worlds", Category.COMBAT);
        restricted();
        add(targets.dummies);
        add(targets.hostile);
        add(targets.passive);
        on(TickEvent.class, e -> tick());
        on(WorldChangeEvent.class, e -> target = null);
    }

    private BooleanSetting cooldown() {
        return cooldown;
    }

    private void tick() {
        var p = mc.player;
        if (mc.currentScreen != null || p.isSpectator()) return;
        double r = range.getDouble();
        Comparator<EntityCache.Entry> order = switch (priority.get()) {
            case "Health" -> Comparator.comparingDouble(en -> ((LivingEntity) en.entity()).getHealth());
            case "Angle" -> Comparator.comparingDouble(en -> Rotations.angleTo(p, en.entity().getBoundingBox().getCenter()));
            default -> Comparator.comparingDouble(EntityCache.Entry::distanceSq);
        };
        target = client().entities().get().stream()
                .filter(en -> en.distanceSq() <= r * r)
                .filter(en -> en.entity() instanceof LivingEntity le && le.isAlive() && !le.isInvulnerable())
                .filter(en -> targets.test(en.kind()))
                .filter(en -> !lineOfSight.isOn() || p.canSee(en.entity()))
                .min(order)
                .map(en -> (LivingEntity) en.entity())
                .orElse(null);
        if (target == null) return;
        client().targets().set(target);

        if (rotate.isOn()) {
            float[] rot = Rotations.toward(p.getEyePos(), target.getBoundingBox().getCenter());
            Rotations.stepToward(p, rot, 60f);
        }
        boolean ready = cooldown.isOn()
                ? p.getAttackCooldownProgress(0.5f) >= 1f
                : System.currentTimeMillis() - lastAttack >= 1000L / cps.getInt();
        if (ready) {
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
            lastAttack = System.currentTimeMillis();
        }
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    public LivingEntity target() {
        return isEnabled() ? target : null;
    }

    @Override
    public String getSuffix() {
        return range.format();
    }
}
