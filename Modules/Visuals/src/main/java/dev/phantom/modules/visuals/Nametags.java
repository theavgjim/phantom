package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.Render2DEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import dev.phantom.integration.world.EntityCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clean nametags with health and distance, drawn in the HUD layer above entities. */
public final class Nametags extends ClientModule {
    private final EntityFilter filter = new EntityFilter(true, false, false, true, false);
    private final NumberSetting range = add(new NumberSetting("Range", "Maximum distance", 64, 8, 256, 1, "m"));
    private final NumberSetting scale = add(new NumberSetting("Scale", "Tag size", 1.0, 0.5, 2.0, 0.05, "x"));
    private final BooleanSetting health = add(new BooleanSetting("Health", "Show health", true));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", "Show distance", true));
    private final float[] screen = new float[2];

    public Nametags() {
        super("Nametags", "Readable nametags with health and distance", Category.VISUALS);
        restricted();
        add(filter.players);
        add(filter.hostile);
        add(filter.passive);
        add(filter.dummies);
        on(Render2DEvent.class, this::render);
    }

    private void render(Render2DEvent e) {
        var proj = client().projection();
        if (!proj.isValid()) return;
        double r2 = range.getDouble() * range.getDouble();
        List<EntityCache.Entry> list = new ArrayList<>();
        for (EntityCache.Entry entry : client().entities().get()) {
            if (entry.distanceSq() > r2) break;
            if (entry.entity() instanceof LivingEntity && filter.test(entry.kind())) list.add(entry);
        }
        DrawContext ctx = e.context;
        var theme = client().theme();
        // Far tags first so near ones draw on top.
        for (int i = list.size() - 1; i >= 0; i--) {
            EntityCache.Entry entry = list.get(i);
            LivingEntity le = (LivingEntity) entry.entity();
            Vec3d p = le.getLerpedPos(e.tickDelta);
            if (!proj.project(p.x, p.y + le.getHeight() + 0.55, p.z, screen)) continue;

            String name = le.getName().getString();
            String hp = health.isOn() ? String.format(Locale.ROOT, " %.1f", le.getHealth() + le.getAbsorptionAmount()) : "";
            String dist = distance.isOn() ? String.format(Locale.ROOT, " %.0fm", Math.sqrt(entry.distanceSq())) : "";
            int w = Render2D.width(name) + Render2D.width(hp) + Render2D.width(dist);
            float s = scale.getFloat();

            ctx.getMatrices().push();
            ctx.getMatrices().translate(screen[0], screen[1], 0);
            ctx.getMatrices().scale(s, s, 1);
            float x = -w / 2f - 4, y = -12;
            Render2D.roundedRect(ctx, x, y, w + 8, 12, 3, Colors.withAlpha(theme.background(), 170));
            Render2D.rect(ctx, x + 2, y + 11, w + 4, 1, EspColors.of(entry.kind()));
            float tx = x + 4;
            Render2D.text(ctx, name, tx, y + 2, theme.text(), true);
            tx += Render2D.width(name);
            if (!hp.isEmpty()) {
                Render2D.text(ctx, hp, tx, y + 2, Colors.health(le.getHealth() / le.getMaxHealth()), true);
                tx += Render2D.width(hp);
            }
            if (!dist.isEmpty()) Render2D.text(ctx, dist, tx, y + 2, theme.textDim(), true);
            ctx.getMatrices().pop();
        }
    }
}
