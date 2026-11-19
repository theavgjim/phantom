package dev.phantom.modules.player;

import dev.phantom.core.module.Category;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;

/** Keeps you sprinting while walking forward (same rules as vanilla toggle-sprint). */
public final class AutoSprint extends ClientModule {
    public AutoSprint() {
        super("AutoSprint", "Sprint automatically when moving forward", Category.PLAYER);
        on(TickEvent.class, e -> {
            var p = mc.player;
            // Exactly vanilla's sprint conditions, so the sprint state sent is one a normal client could send.
            if (p.input.movementForward > 0 && !p.horizontalCollision && !p.isSneaking() && !p.isUsingItem()
                    && !p.isSprinting() && !p.hasVehicle() && !p.isFallFlying()
                    && !p.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                    && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying)) {
                p.setSprinting(true);
            }
        });
    }
}
