package dev.phantom.modules.movement;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.MovementUtil;

/** Climb walls like a spider when walking into them. */
public final class Spider extends ClientModule {
    private final NumberSetting speed = add(new NumberSetting("Climb Speed", "Vertical speed", 0.2, 0.05, 0.6, 0.05));

    public Spider() {
        super("Spider", "Climb walls (private worlds)", Category.MOVEMENT);
        restricted();
        on(TickEvent.class, e -> {
            var p = mc.player;
            if (!p.horizontalCollision || !MovementUtil.isMoving(p)) return;
            var v = p.getVelocity();
            p.setVelocity(v.x, p.isSneaking() ? 0 : speed.getDouble(), v.z);
            p.fallDistance = 0;
        });
    }
}
