package dev.phantom.hud;

import dev.phantom.core.hud.HudElement;
import dev.phantom.core.hud.HudRegistry;
import dev.phantom.hud.elements.CoordinatesElement;
import dev.phantom.hud.elements.DirectionElement;
import dev.phantom.hud.elements.FpsElement;
import dev.phantom.hud.elements.ModuleListElement;
import dev.phantom.hud.elements.NotificationsElement;
import dev.phantom.hud.elements.TargetInfoElement;
import dev.phantom.hud.elements.WatermarkElement;
import dev.phantom.integration.util.Colors;
import dev.phantom.modules.misc.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Lays out and draws HUD elements from their stored (fractional) positions. */
public final class HudManager {
    private final HudRegistry registry;

    public HudManager(HudRegistry registry) {
        this.registry = registry;
    }

    public void registerDefaults() {
        registry.register(new WatermarkElement());
        registry.register(new FpsElement());
        registry.register(new CoordinatesElement());
        registry.register(new DirectionElement());
        registry.register(new ModuleListElement());
        registry.register(new TargetInfoElement());
        registry.register(new NotificationsElement());
    }

    public List<ScreenElement> elements() {
        List<ScreenElement> out = new ArrayList<>();
        for (HudElement e : registry.all()) if (e instanceof ScreenElement se) out.add(se);
        return out;
    }

    public void render(DrawContext ctx, float tickDelta, boolean editor) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HudModule hud = ScreenElement.style();
        if (!editor && (hud == null || !hud.isEnabled() || mc.player == null)) return;
        float sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        for (ScreenElement e : elements()) {
            boolean show = editor || (e.visible.isOn() && e.isActive());
            if (!show) continue;
            float s = e.scale.getFloat();
            float w = e.width() * s, h = e.height() * s;
            float x = clamp((float) (e.x() * sw), 0, Math.max(0, sw - w));
            float y = clamp((float) (e.y() * sh), 0, Math.max(0, sh - h));
            e.screenX = x;
            e.screenY = y;
            e.screenW = w;
            e.screenH = h;
            boolean right = x + w / 2 > sw / 2;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x, y, 0);
            ctx.getMatrices().scale(s, s, 1);
            if (editor && !e.visible.isOn()) {
                // Hidden elements are drawn faintly in the editor so they can be re-enabled.
                e.render(ctx, tickDelta, true, right);
                ctx.fill(0, 0, (int) Math.ceil(e.width()), (int) Math.ceil(e.height()), Colors.withAlpha(0x000000, 150));
            } else {
                e.render(ctx, tickDelta, editor, right);
            }
            ctx.getMatrices().pop();
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
