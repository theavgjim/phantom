package dev.phantom.modules.movement;

import dev.phantom.core.module.Category;
import dev.phantom.integration.module.ClientModule;

/** Removes the slowdown while eating, drinking, blocking or drawing a bow (see ClientPlayerEntityMixin). */
public final class NoSlow extends ClientModule {
    public NoSlow() {
        super("NoSlow", "No slowdown while using items (private worlds)", Category.MOVEMENT);
        restricted();
    }
}
