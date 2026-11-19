package dev.phantom.hud.elements;

import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public final class FpsElement extends ScreenElement {
    public FpsElement() {
        super("fps", "FPS Counter", 0.004, 0.078, true);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        var style = style();
        int fps = mc.getCurrentFps();
        String label = "FPS ";
        String value = Integer.toString(fps);
        width = Render2D.width(label + value) + 8;
        height = 13;
        Render2D.roundedRect(ctx, 0, 0, width, height, 3, style.backgroundColor());
        Render2D.text(ctx, label, 4, 3, style.accent(0.1f), style.shadow.isOn());
        int color = fps >= 60 ? style.textColor.argb() : fps >= 30 ? 0xFFFFCA28 : 0xFFEF5350;
        Render2D.text(ctx, value, 4 + Render2D.width(label), 3, color, style.shadow.isOn());
    }
}
