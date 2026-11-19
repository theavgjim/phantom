package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.Render3DEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.Colors;
import dev.phantom.integration.world.EntityCache;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class Esp extends ClientModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "How boxes are drawn", "Outline", "Outline", "Filled", "Both"));
    private final EntityFilter filter = new EntityFilter(true, true, false, true, false);
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 96, 8, 256, 1, "m"));
    private final NumberSetting fillAlpha = add(new NumberSetting("Fill Opacity", "Opacity of filled boxes", 50, 5, 200, 1));
    private final NumberSetting lineWidth = add(new NumberSetting("Line Width", "Outline thickness", 1.5, 1, 4, 0.5));

    public Esp() {
        super("ESP", "Draws boxes around entities through walls", Category.VISUALS);
        add(filter.players);
        add(filter.hostile);
        add(filter.passive);
        add(filter.dummies);
        add(filter.items);
        on(Render3DEvent.class, this::render);
    }

    private void render(Render3DEvent e) {
        double r2 = range.getDouble() * range.getDouble();
        boolean outline = !mode.is("Filled"), fill = !mode.is("Outline");
        e.renderer.setLineWidth(lineWidth.getFloat());
        for (EntityCache.Entry entry : client().entities().get()) {
            if (entry.distanceSq() > r2) break;
            if (!filter.test(entry.kind())) continue;
            Box box = lerpedBox(entry.entity(), e.tickDelta);
            int color = EspColors.of(entry.kind());
            if (fill) e.renderer.filledBox(box, Colors.withAlpha(color, fillAlpha.getInt()));
            if (outline) e.renderer.box(box, color);
        }
    }

    static Box lerpedBox(Entity entity, float tickDelta) {
        Vec3d lerp = entity.getLerpedPos(tickDelta);
        return entity.getBoundingBox().offset(lerp.subtract(entity.getPos()));
    }
}
