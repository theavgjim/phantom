package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.Render3DEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.Colors;
import dev.phantom.integration.world.EntityCache;
import net.minecraft.util.math.Vec3d;

public final class Tracers extends ClientModule {
    private final EntityFilter filter = new EntityFilter(true, true, false, false, false);
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 128, 8, 256, 1, "m"));
    private final BooleanSetting distanceFade = add(new BooleanSetting("Distance Fade", "Fade lines with distance", true));
    private final BooleanSetting body = add(new BooleanSetting("Body Line", "Also draw a vertical line through the target", false));

    public Tracers() {
        super("Tracers", "Draws lines from the crosshair to entities", Category.VISUALS);
        add(filter.players);
        add(filter.hostile);
        add(filter.passive);
        add(filter.dummies);
        add(filter.items);
        on(Render3DEvent.class, this::render);
    }

    private void render(Render3DEvent e) {
        double max = range.getDouble();
        Vec3d start = Vec3d.fromPolar(e.camera.getPitch(), e.camera.getYaw());
        for (EntityCache.Entry entry : client().entities().get()) {
            if (entry.distanceSq() > max * max) break;
            if (!filter.test(entry.kind())) continue;
            var ent = entry.entity();
            Vec3d p = ent.getLerpedPos(e.tickDelta);
            double midY = p.y + ent.getHeight() / 2.0;
            int color = EspColors.of(entry.kind());
            if (distanceFade.isOn()) {
                float f = 1f - (float) (Math.sqrt(entry.distanceSq()) / max) * 0.75f;
                color = Colors.fade(color, f);
            }
            e.renderer.lineFromCamera(start, p.x, midY, p.z, Colors.fade(color, 0.6f), color);
            if (body.isOn()) e.renderer.line(p.x, p.y, p.z, p.x, p.y + ent.getHeight(), p.z, color);
        }
    }
}
