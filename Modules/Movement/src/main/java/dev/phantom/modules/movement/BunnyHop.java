package dev.phantom.modules.movement;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.MovementUtil;

/** Automatically jumps while moving, with optional air-speed boost. */
public final class BunnyHop extends ClientModule {
    private final NumberSetting airBoost = add(new NumberSetting("Air Boost", "Air speed relative to sprinting (1 = none)", 1.0, 1.0, 2.5, 0.05, "x"));

    public BunnyHop() {
        super("BunnyHop", "Auto-jump while moving (private worlds)", Category.MOVEMENT);
        privateWorldOnly();
        on(TickEvent.class, e -> {
            var p = mc.player;
            if (!MovementUtil.isMoving(p) || p.isSneaking() || p.isTouchingWater() || p.getAbilities().flying) return;
            if (p.isOnGround()) {
                p.jump();
            } else if (airBoost.getDouble() > 1.0) {
                double target = MovementUtil.BASE_SPEED * airBoost.getDouble();
                MovementUtil.setHorizontalSpeed(p, Math.max(MovementUtil.horizontalSpeed(p), target));
            }
        });
    }
}
