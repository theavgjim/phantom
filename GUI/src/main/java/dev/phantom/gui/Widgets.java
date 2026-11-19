package dev.phantom.gui;

import dev.phantom.core.theme.Theme;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.gui.DrawContext;

/** Reusable controls drawn with the active theme. */
public final class Widgets {
    private Widgets() {
    }

    public static void button(Ui ui, DrawContext ctx, Theme t, String id, float x, float y, float w, float h,
                              String label, boolean primary, Runnable action) {
        boolean hover = ui.hovered(x, y, w, h);
        float hv = ui.anim("btn:" + id, hover ? 1 : 0, 18);
        int base = primary ? t.accent() : t.background();
        int bg = Colors.lerp(base, Colors.brighten(base, 0.12f), hv);
        Render2D.roundedRect(ctx, x, y, w, h, Math.min(4, h / 2), ui.c(bg));
        int tc = primary ? 0xFFFFFFFF : Colors.lerp(t.textDim(), t.text(), hv);
        Render2D.centeredText(ctx, label, x + w / 2f, y + (h - 8) / 2f, ui.c(tc), false);
        ui.hit(x, y, w, h, (mx, my, b) -> {
            if (b == 0) action.run();
        });
    }

    /** Animated on/off switch, 22x11. */
    public static void toggle(Ui ui, DrawContext ctx, Theme t, String id, float x, float y, boolean on, Runnable action) {
        float w = 22, h = 11;
        float p = ui.anim("tg:" + id, on ? 1 : 0, 16);
        boolean hover = ui.hovered(x, y, w, h);
        int track = Colors.lerp(t.outline(), t.accent(), p);
        if (hover) track = Colors.brighten(track, 0.08f);
        Render2D.roundedRect(ctx, x, y, w, h, h / 2, ui.c(track));
        float kx = x + 2 + p * (w - h);
        Render2D.circle(ctx, kx + (h - 4) / 2f, y + h / 2f, (h - 4) / 2f, ui.c(0xFFFFFFFF));
        ui.hit(x - 2, y - 2, w + 4, h + 4, (mx, my, b) -> {
            if (b == 0) action.run();
        });
    }

    public static void textField(Ui ui, DrawContext ctx, Theme t, float x, float y, float w, float h, TextInput input) {
        boolean focused = ui.focus == input;
        boolean hover = ui.hovered(x, y, w, h);
        float f = ui.anim("tf:" + System.identityHashCode(input), focused ? 1 : 0, 16);
        Render2D.roundedOutline(ctx, x, y, w, h, 4, 1, ui.c(Colors.lerp(hover ? Colors.brighten(t.outline(), 0.1f) : t.outline(), t.accent(), f)));
        Render2D.roundedRect(ctx, x, y, w, h, 4, ui.c(t.background()));
        float ty = y + (h - 8) / 2f;
        int maxW = (int) w - 10;
        if (input.text.isEmpty() && !focused) {
            Render2D.text(ctx, Render2D.trim(input.placeholder, maxW), x + 5, ty, ui.c(t.textDim()), false);
        } else {
            String shown = input.text;
            // Keep the end of long text visible while typing.
            while (Render2D.width(shown) > maxW && !shown.isEmpty()) shown = shown.substring(1);
            Render2D.text(ctx, shown, x + 5, ty, ui.c(t.text()), false);
            if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
                Render2D.rect(ctx, x + 5 + Render2D.width(shown) + 1, ty - 1, 1, 10, ui.c(t.accent()));
            }
        }
        ui.hit(x, y, w, h, (mx, my, b) -> ui.focus = input);
    }

    public static void sectionTitle(Ui ui, DrawContext ctx, Theme t, String text, float x, float y) {
        Render2D.text(ctx, text.toUpperCase(java.util.Locale.ROOT), x, y, ui.c(t.textDim()), false, 0.75f);
    }
}
