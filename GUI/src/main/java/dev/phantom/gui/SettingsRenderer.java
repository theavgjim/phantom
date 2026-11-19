package dev.phantom.gui;

import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ColorSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.core.setting.Setting;
import dev.phantom.core.setting.StringListSetting;
import dev.phantom.core.setting.StringSetting;
import dev.phantom.core.theme.Theme;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.List;
import java.util.Locale;

/** Draws editable rows for any list of settings (modules in the ClickGUI, elements in the HUD editor). */
public final class SettingsRenderer {
    private SettingsRenderer() {
    }

    /** @return total height used */
    public static float render(Ui ui, DrawContext ctx, Theme t, List<Setting<?>> settings, float x, float y, float w) {
        float start = y;
        for (Setting<?> s : settings) {
            if (!s.isVisible()) continue;
            float h;
            if (s instanceof BooleanSetting b) h = bool(ui, ctx, t, b, x, y, w);
            else if (s instanceof NumberSetting n) h = number(ui, ctx, t, n, x, y, w);
            else if (s instanceof ModeSetting m) h = mode(ui, ctx, t, m, x, y, w);
            else if (s instanceof ColorSetting c) h = color(ui, ctx, t, c, x, y, w);
            else if (s instanceof StringListSetting l) h = stringList(ui, ctx, t, l, x, y, w);
            else if (s instanceof StringSetting str) h = string(ui, ctx, t, str, x, y, w);
            else h = 0;
            y += h;
        }
        return y - start;
    }

    private static void label(Ui ui, DrawContext ctx, Theme t, Setting<?> s, float x, float y, float maxW) {
        Render2D.text(ctx, Render2D.trim(s.getName(), (int) maxW), x, y, ui.c(t.text()), false);
        if (!s.getDescription().isEmpty() && ui.hovered(x, y - 2, Math.min(maxW, Render2D.width(s.getName())), 12)) {
            String d = s.getDescription();
            ui.defer(() -> tooltip(ctx, t, ui, d));
        }
    }

    private static void tooltip(DrawContext ctx, Theme t, Ui ui, String text) {
        float w = Render2D.width(text) + 8;
        float x = (float) ui.mx + 8, y = (float) ui.my - 14;
        Render2D.roundedRect(ctx, x, y, w, 12, 3, ui.c(t.panelRaised()));
        Render2D.text(ctx, text, x + 4, y + 2, ui.c(t.textDim()), false);
    }

    private static float bool(Ui ui, DrawContext ctx, Theme t, BooleanSetting s, float x, float y, float w) {
        label(ui, ctx, t, s, x, y + 4, w - 30);
        Widgets.toggle(ui, ctx, t, "set:" + System.identityHashCode(s), x + w - 22, y + 2.5f, s.isOn(), s::toggle);
        return 16;
    }

    private static float number(Ui ui, DrawContext ctx, Theme t, NumberSetting s, float x, float y, float w) {
        label(ui, ctx, t, s, x, y + 2, w - 40);
        String v = s.format();
        Render2D.text(ctx, v, x + w - Render2D.width(v), y + 2, ui.c(t.accent()), false);
        float ty = y + 14, th = 3;
        float frac = ui.anim("num:" + System.identityHashCode(s), s.fraction(), 22);
        Render2D.roundedRect(ctx, x, ty, w, th, 1.5f, ui.c(t.outline()));
        Render2D.roundedRect(ctx, x, ty, Math.max(th, w * frac), th, 1.5f, ui.c(t.accent()), ui.c(t.accentSecondary()));
        boolean hover = ui.hovered(x - 2, ty - 5, w + 4, th + 10);
        Render2D.circle(ctx, x + w * frac, ty + th / 2f, hover || ui.drag != null ? 4 : 3.2f, ui.c(0xFFFFFFFF));
        ui.hit(x - 3, ty - 5, w + 6, th + 10, (mx, my, b) -> {
            if (b != 0) {
                if (b == 1) s.reset();
                return;
            }
            Ui.DragHandler d = (dx, dy) -> s.setFraction((dx - x) / w);
            d.onDrag(mx, my);
            ui.drag = d;
        });
        return 24;
    }

    private static float mode(Ui ui, DrawContext ctx, Theme t, ModeSetting s, float x, float y, float w) {
        float bw = Math.min(100, w * 0.5f), bx = x + w - bw, bh = 13;
        label(ui, ctx, t, s, x, y + 4, w - bw - 6);
        boolean open = ui.openDropdown == s;
        boolean hover = ui.hovered(bx, y + 1, bw, bh);
        Render2D.roundedRect(ctx, bx, y + 1, bw, bh, 3, ui.c(hover || open ? Colors.brighten(t.panelRaised(), 0.08f) : t.background()));
        Render2D.text(ctx, Render2D.trim(s.get(), (int) bw - 16), bx + 5, y + 3.5f, ui.c(t.text()), false);
        Render2D.text(ctx, open ? "▴" : "▾", bx + bw - 9, y + 3.5f, ui.c(t.textDim()), false);
        ui.hit(bx, y + 1, bw, bh, (mx, my, b) -> {
            if (b == 1) s.cycle(1);
            else ui.openDropdown = open ? null : s;
        });
        if (open) {
            float oy = y + bh + 3;
            ui.defer(() -> {
                List<String> opts = s.getOptions();
                float lh = 12, ph = opts.size() * lh + 4;
                Render2D.roundedOutline(ctx, bx, oy, bw, ph, 4, 1, ui.c(t.outline()));
                Render2D.roundedRect(ctx, bx, oy, bw, ph, 4, ui.c(t.panel()));
                float iy = oy + 2;
                for (String o : opts) {
                    boolean sel = o.equalsIgnoreCase(s.get());
                    boolean hv = ui.hovered(bx + 2, iy, bw - 4, lh);
                    if (hv || sel) Render2D.roundedRect(ctx, bx + 2, iy, bw - 4, lh, 3, ui.c(sel ? Colors.withAlpha(t.accent(), 70) : t.panelRaised()));
                    Render2D.text(ctx, Render2D.trim(o, (int) bw - 12), bx + 6, iy + 2, ui.c(sel ? t.text() : t.textDim()), false);
                    final String opt = o;
                    ui.hit(bx + 2, iy, bw - 4, lh, (mx, my, b) -> {
                        s.set(opt);
                        ui.openDropdown = null;
                    });
                    iy += lh;
                }
            });
        }
        return 17;
    }

    private static float color(Ui ui, DrawContext ctx, Theme t, ColorSetting s, float x, float y, float w) {
        label(ui, ctx, t, s, x, y + 4, w - 80);
        boolean open = ui.expanded.contains(s);
        String hex = String.format(Locale.ROOT, "#%08X", s.argb());
        float sx = x + w - 22;
        Render2D.text(ctx, hex, sx - 4 - Render2D.width(hex), y + 4, ui.c(t.textDim()), false);
        Render2D.roundedOutline(ctx, sx, y + 2, 22, 11, 3, 1, ui.c(t.outline()));
        Render2D.roundedRect(ctx, sx, y + 2, 22, 11, 3, ui.c(s.argb() | 0xFF000000), ui.c(s.argb()));
        ui.hit(x, y, w, 15, (mx, my, b) -> {
            if (b == 1) s.reset();
            else if (!ui.expanded.remove(s)) ui.expanded.add(s);
        });
        float h = 16;
        float open01 = ui.anim("col:" + System.identityHashCode(s), open ? 1 : 0, 18);
        if (open01 < 0.01f) return h;
        float[] hsb = s.hsb();
        float a = s.alpha();
        float rowH = 11;
        float py = y + h;
        int rgb = s.argb() | 0xFF000000;
        ui.pushClip(ctx, x - 2, py, w + 4, rowH * 4 * open01 + 2);
        py = hueRow(ui, ctx, t, s, x, py, w, hsb, a);
        py = gradRow(ui, ctx, t, "S", x, py, w, hsb[1], 0xFFFFFFFF, ColorSetting.hsbToRgb(hsb[0], 1, hsb[2]),
                f -> s.setHsba(hsb[0], f, hsb[2], a));
        py = gradRow(ui, ctx, t, "B", x, py, w, hsb[2], 0xFF000000, ColorSetting.hsbToRgb(hsb[0], hsb[1], 1),
                f -> s.setHsba(hsb[0], hsb[1], f, a));
        gradRow(ui, ctx, t, "A", x, py, w, a, rgb & 0x00FFFFFF, rgb, f -> s.setHsba(hsb[0], hsb[1], hsb[2], f));
        ui.popClip(ctx);
        return h + rowH * 4 * open01 + 2;
    }

    private static float hueRow(Ui ui, DrawContext ctx, Theme t, ColorSetting s, float x, float y, float w, float[] hsb, float a) {
        float tx = x + 10, tw = w - 10;
        Render2D.text(ctx, "H", x, y + 1.5f, ui.c(t.textDim()), false, 0.75f);
        for (int i = 0; i < 6; i++) {
            Render2D.gradientH(ctx, tx + tw * i / 6f, y + 3, tw / 6f + 0.5f, 5,
                    ui.c(ColorSetting.hsbToRgb(i / 6f, 1, 1)), ui.c(ColorSetting.hsbToRgb((i + 1) / 6f, 1, 1)));
        }
        knob(ui, ctx, tx + tw * hsb[0], y + 5.5f);
        ui.hit(tx - 2, y, tw + 4, 11, (mx, my, b) -> {
            Ui.DragHandler d = (dx, dy) -> s.setHsba((float) Math.max(0, Math.min(0.9999, (dx - tx) / tw)), Math.max(hsb[1], 0.01f), Math.max(hsb[2], 0.05f), a);
            d.onDrag(mx, my);
            ui.drag = d;
        });
        return y + 11;
    }

    private static float gradRow(Ui ui, DrawContext ctx, Theme t, String label, float x, float y, float w, float value,
                                 int from, int to, java.util.function.Consumer<Float> setter) {
        float tx = x + 10, tw = w - 10;
        Render2D.text(ctx, label, x, y + 1.5f, ui.c(t.textDim()), false, 0.75f);
        Render2D.gradientH(ctx, tx, y + 3, tw, 5, ui.c(from), ui.c(to));
        knob(ui, ctx, tx + tw * value, y + 5.5f);
        ui.hit(tx - 2, y, tw + 4, 11, (mx, my, b) -> {
            Ui.DragHandler d = (dx, dy) -> setter.accept((float) Math.max(0, Math.min(1, (dx - tx) / tw)));
            d.onDrag(mx, my);
            ui.drag = d;
        });
        return y + 11;
    }

    private static void knob(Ui ui, DrawContext ctx, float x, float y) {
        Render2D.circle(ctx, x, y, 3.5f, ui.c(0xFF000000));
        Render2D.circle(ctx, x, y, 2.6f, ui.c(0xFFFFFFFF));
    }

    private static float string(Ui ui, DrawContext ctx, Theme t, StringSetting s, float x, float y, float w) {
        float fw = Math.min(120, w * 0.55f);
        label(ui, ctx, t, s, x, y + 4, w - fw - 6);
        TextInput in = ui.inputs.computeIfAbsent(s, k -> {
            TextInput ti = new TextInput(s.getMaxLength(), s.getDefault());
            ti.onChange = s::set;
            return ti;
        });
        if (ui.focus != in) in.set(s.get());
        Widgets.textField(ui, ctx, t, x + w - fw, y + 1, fw, 13, in);
        return 17;
    }

    private static float stringList(Ui ui, DrawContext ctx, Theme t, StringListSetting s, float x, float y, float w) {
        label(ui, ctx, t, s, x, y + 2, w);
        TextInput in = ui.inputs.computeIfAbsent(s, k -> {
            TextInput ti = new TextInput(512, "id, id, ...");
            ti.onSubmit = text -> {
                s.setFromText(text);
                ui.focus = null;
            };
            return ti;
        });
        if (ui.focus != in) in.set(s.asText());
        Widgets.textField(ui, ctx, t, x, y + 12, w, 14, in);
        if (ui.focus == in) {
            Render2D.text(ctx, "Enter to apply", x + w - Render2D.width("Enter to apply") * 0.75f, y + 3, ui.c(t.textDim()), false, 0.75f);
        }
        return 30;
    }
}
