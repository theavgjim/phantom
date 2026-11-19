package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.Render2DEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import dev.phantom.integration.world.EntityCache;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** Distance labels under entities plus screen-edge markers for entities that are off-screen. */
public final class DistanceIndicators extends ClientModule {
    private final EntityFilter filter = new EntityFilter(true, true, false, true, false);
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 64, 8, 256, 1, "m"));
    private final BooleanSetting edge = add(new BooleanSetting("Edge Markers", "Markers at the screen edge for off-screen entities", true));
    private final float[] screen = new float[2];

    public DistanceIndicators() {
        super("DistanceIndicators", "Shows how far entities are, even off-screen", Category.VISUALS);
        add(filter.players);
        add(filter.hostile);
        add(filter.passive);
        add(filter.dummies);
        on(Render2DEvent.class, this::render);
    }

    private void render(Render2DEvent e) {
        var proj = client().projection();
        if (!proj.isValid()) return;
        var ctx = e.context;
        float sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        float cx = sw / 2f, cy = sh / 2f, margin = 14;
        double max = range.getDouble();
        for (EntityCache.Entry entry : client().entities().get()) {
            if (entry.distanceSq() > max * max) break;
            if (!filter.test(entry.kind())) continue;
            var ent = entry.entity();
            Vec3d p = ent.getLerpedPos(e.tickDelta);
            boolean front = proj.project(p.x, p.y + ent.getHeight() / 2, p.z, screen);
            String label = String.format(Locale.ROOT, "%.0fm", Math.sqrt(entry.distanceSq()));
            int color = EspColors.of(entry.kind());
            boolean onScreen = front && screen[0] >= 0 && screen[0] <= sw && screen[1] >= 0 && screen[1] <= sh;
            if (onScreen) {
                if (proj.project(p.x, p.y, p.z, screen)) {
                    Render2D.centeredText(ctx, label, screen[0], screen[1] + 2, Colors.withAlpha(color, 230), true);
                }
            } else if (edge.isOn()) {
                float dx = screen[0] - cx, dy = screen[1] - cy;
                if (!front) {
                    dx = -dx;
                    dy = -dy;
                }
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 0.001f) continue;
                dx /= len;
                dy /= len;
                float kx = dx == 0 ? Float.MAX_VALUE : (cx - margin) / Math.abs(dx);
                float ky = dy == 0 ? Float.MAX_VALUE : (cy - margin) / Math.abs(dy);
                float k = Math.min(kx, ky);
                float mx = cx + dx * k, my = cy + dy * k;
                Render2D.circle(ctx, mx, my, 4, Colors.withAlpha(color, 210));
                Render2D.centeredText(ctx, label, mx, my + (dy > 0 ? -14 : 6), color, true);
            }
        }
    }
}
