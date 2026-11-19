package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.world.EntityCache;
import net.minecraft.entity.Entity;

/** Entity highlighting via the vanilla glowing outline shader, coloured by entity kind. */
public final class Highlight extends ClientModule {
    private final EntityFilter filter = new EntityFilter(true, true, false, true, true);
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 64, 8, 256, 1, "m"));

    public Highlight() {
        super("Highlight", "Glowing outlines around entities", Category.VISUALS);
        add(filter.players);
        add(filter.hostile);
        add(filter.passive);
        add(filter.dummies);
        add(filter.items);
    }

    public boolean matches(Entity entity) {
        if (mc.player == null || entity == mc.player) return false;
        if (!filter.test(EntityCache.kindOf(entity))) return false;
        double r = range.getDouble();
        return entity.squaredDistanceTo(mc.player) <= r * r;
    }

    public int colorFor(Entity entity) {
        return EspColors.of(EntityCache.kindOf(entity)) & 0xFFFFFF;
    }
}
