package dev.phantom.hud.elements;

import dev.phantom.core.setting.ModeSetting;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;

/** Compass strip (or plain text) showing the direction you are facing. */
public final class DirectionElement extends ScreenElement {
    private static final String[] NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private final ModeSetting mode = add(new ModeSetting("Style", "", "Compass", "Compass", "Text"));

    public DirectionElement() {
        super("direction", "Direction", 0.5, 0.006, true);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        var style = style();
        float yaw = mc.player != null ? MathHelper.wrapDegrees(mc.player.getYaw(tickDelta)) : 0;
        if (mode.is("Text")) {
            int idx = Math.floorMod(Math.round(yaw / 45f), 8);
            String axis = switch (NAMES[idx]) {
                case "N" -> " (-Z)";
                case "S" -> " (+Z)";
                case "E" -> " (+X)";
                case "W" -> " (-X)";
                default -> "";
            };
            String s = NAMES[idx] + axis + String.format(Locale.ROOT, "  %.1f°", yaw);
            width = Render2D.width(s) + 8;
            height = 13;
            Render2D.roundedRect(ctx, 0, 0, width, height, 3, style.backgroundColor());
            Render2D.text(ctx, s, 4, 3, style.textColor.argb(), style.shadow.isOn());
            return;
        }
        width = 140;
        height = 16;
        Render2D.roundedRect(ctx, 0, 0, width, height, 4, style.backgroundColor());
        float center = width / 2f, pxPerDeg = 1.1f;
        for (int deg = -180; deg < 540; deg += 15) {
            float diff = MathHelper.wrapDegrees(deg - yaw);
            float x = center + diff * pxPerDeg;
            if (x < 6 || x > width - 6) continue;
            float fade = 1f - Math.abs(diff) / (width / 2f / pxPerDeg);
            if (deg % 45 == 0) {
                String n = NAMES[Math.floorMod(deg / 45, 8)];
                int c = n.length() == 1 ? style.textColor.argb() : client().theme().textDim();
                Render2D.centeredText(ctx, n, x, 4, Colors.fade(c, fade), false);
            } else {
                Render2D.rect(ctx, x - 0.5f, 6, 1, 4, Colors.fade(client().theme().textDim(), fade));
            }
        }
        Render2D.rect(ctx, center - 0.5f, 1, 1, 2.5f, style.accent(0));
        Render2D.rect(ctx, center - 0.5f, height - 3.5f, 1, 2.5f, style.accent(0));
    }
}
