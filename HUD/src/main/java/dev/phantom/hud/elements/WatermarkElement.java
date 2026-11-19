package dev.phantom.hud.elements;

import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import net.minecraft.client.gui.DrawContext;

public final class WatermarkElement extends ScreenElement {
    private final BooleanSetting version = add(new BooleanSetting("Show Version", "", true));
    private final BooleanSetting profile = add(new BooleanSetting("Show Profile", "", false));

    public WatermarkElement() {
        super("watermark", "Watermark", 0.004, 0.006, true);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        var style = style();
        String title = "Phantom";
        String tail = (version.isOn() ? " " + client().core().version() : "")
                + (profile.isOn() ? "  ·  " + client().core().config().activeProfile() : "");
        int tw = Render2D.width(title), w2 = Render2D.width(tail);
        width = tw + w2 + 14;
        height = 15;
        float r = 4;
        Render2D.roundedRect(ctx, 0, 0, width, height, r, style.backgroundColor());
        Render2D.roundedRect(ctx, 3, 3.5f, 2, 8, 1, style.accent(0), style.accent(0.3f));
        Render2D.text(ctx, title, 8, 4, style.accent(0), style.shadow.isOn());
        Render2D.text(ctx, tail, 8 + tw, 4, client().theme().textDim(), style.shadow.isOn());
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
