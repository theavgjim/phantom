package dev.phantom.modules.movement;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.MovementUtil;

/** Faster walking for traversal testing in private worlds. */
public final class Speed extends ClientModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Ground = only while grounded, Strafe = also mid-air", "Ground", "Ground", "Strafe"));
    private final NumberSetting multiplier = add(new NumberSetting("Multiplier", "Speed relative to sprinting", 1.5, 1.0, 4.0, 0.1, "x"));

    public Speed() {
        super("Speed", "Move faster (private worlds)", Category.MOVEMENT);
        restricted();
        on(TickEvent.class, e -> {
            var p = mc.player;
            if (!MovementUtil.isMoving(p) || p.isSneaking() || p.isTouchingWater() || p.isInLava() || p.getAbilities().flying) return;
            if (mode.is("Ground") && !p.isOnGround()) return;
            MovementUtil.setHorizontalSpeed(p, MovementUtil.BASE_SPEED * multiplier.getDouble());
        });
    }

    @Override
    public String getSuffix() {
        return multiplier.format();
    }
}
