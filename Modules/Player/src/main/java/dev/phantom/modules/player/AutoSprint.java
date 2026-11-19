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
            if (p.input.movementForward > 0 && !p.horizontalCollision && !p.isSneaking() && !p.isUsingItem()
                    && (p.getHungerManager().getFoodLevel() > 6 || p.getAbilities().allowFlying)) {
                p.setSprinting(true);
            }
        });
    }
}
