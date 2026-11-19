package dev.phantom.modules.combat;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.integration.event.AttackEntityEvent;
import dev.phantom.integration.event.Render2DEvent;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.render.Render2D;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Critical-hit demonstration: shows when the vanilla critical conditions are met (falling, not
 * sprinting, charged attack...) and counts how many of your hits were critical. "Jump" mode makes
 * the player hop while attacking so the falling phase lines up. Private worlds only.
 */
public final class Criticals extends ClientModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Indicator only, or auto-jump when attacking", "Indicator", "Indicator", "Jump"));
    private final BooleanSetting indicator = add(new BooleanSetting("Crosshair Indicator", "Show CRIT under the crosshair when a hit would crit", true));
    private int hits;
    private int crits;

    public Criticals() {
        super("Criticals", "Critical-hit demonstration and stats (private worlds)", Category.COMBAT);
        privateWorldOnly();
        on(AttackEntityEvent.class, e -> {
            if (!(e.target instanceof LivingEntity)) return;
            hits++;
            if (wouldCrit(mc.player)) crits++;
        });
        on(TickEvent.class, e -> {
            if (!mode.is("Jump")) return;
            var p = mc.player;
            if (mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity && p.isOnGround()
                    && !p.isTouchingWater() && !p.isClimbing()) {
                p.jump();
            }
        });
        on(Render2DEvent.class, e -> {
            if (!indicator.isOn() || !wouldCrit(mc.player)) return;
            float cx = mc.getWindow().getScaledWidth() / 2f, cy = mc.getWindow().getScaledHeight() / 2f;
            Render2D.centeredText(e.context, "CRIT", cx, cy + 8, client().theme().accent(), true);
        });
    }

    @Override
    protected void onEnable() {
        hits = 0;
        crits = 0;
    }

    /** Mirrors the vanilla critical-hit conditions in PlayerEntity#attack. */
    public static boolean wouldCrit(ClientPlayerEntity p) {
        return p.getAttackCooldownProgress(0.5f) > 0.9f
                && p.fallDistance > 0.0f
                && !p.isOnGround()
                && !p.isClimbing()
                && !p.isTouchingWater()
                && !p.hasStatusEffect(StatusEffects.BLINDNESS)
                && !p.hasVehicle()
                && !p.isSprinting();
    }

    @Override
    public String getSuffix() {
        return hits == 0 ? mode.get() : crits + "/" + hits;
    }
}
