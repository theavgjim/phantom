package dev.phantom.hud.elements;

import dev.phantom.core.module.Module;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.util.Animation;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Animated list of enabled modules, sorted by width. Aligns to whichever side it is placed on. */
public final class ModuleListElement extends ScreenElement {
    private static final float ROW = 11;
    private final BooleanSetting suffixes = add(new BooleanSetting("Suffixes", "Show module status text", true));
    private final BooleanSetting bar = add(new BooleanSetting("Accent Bar", "", true));
    private final Map<Module, Animation> anims = new HashMap<>();
    private long last = System.nanoTime();

    public ModuleListElement() {
        super("modulelist", "Module List", 1.0, 0.0, true);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        long now = System.nanoTime();
        double dt = (now - last) / 1e9;
        last = now;
        var style = style();
        boolean shadow = style.shadow.isOn();

        record Row(String name, String suffix, float width, float progress) {
        }
        List<Row> rows = new ArrayList<>();
        for (Module m : client().core().modules().all()) {
            if (m.isHiddenFromList() || !m.isToggleable()) continue;
            Animation a = anims.computeIfAbsent(m, k -> new Animation(m.isEnabled() ? 1 : 0, 14));
            a.target(m.isEnabled() ? 1 : 0).update(dt);
            if (a.get() < 0.01) continue;
            String suffix = suffixes.isOn() ? m.getSuffix() : null;
            float w = Render2D.width(m.getName()) + (suffix != null ? Render2D.width(" " + suffix) : 0);
            rows.add(new Row(m.getName(), suffix, w, a.getFloat()));
        }
        if (rows.isEmpty() && editor) rows.add(new Row("Module List", null, Render2D.width("Module List"), 1));
        rows.sort((a, b) -> Float.compare(b.width, a.width));

        float maxW = 0;
        for (Row r : rows) maxW = Math.max(maxW, r.width + 8);
        width = Math.max(40, maxW);
        float y = 0;
        int i = 0;
        for (Row r : rows) {
            float rowW = r.width + 8;
            float slide = (1 - r.progress) * (rowW + 4);
            float x = right ? width - rowW + slide : -slide;
            float h = ROW * r.progress;
            int accent = style.accent(i * 0.08f);
            Render2D.rect(ctx, x, y, rowW, h, Colors.fade(style.backgroundColor(), r.progress));
            if (bar.isOn()) Render2D.rect(ctx, right ? x + rowW - 1.5f : x, y, 1.5f, h, Colors.fade(accent, r.progress));
            float tx = x + (right ? 3 : 4.5f);
            Render2D.text(ctx, r.name, tx, y + 1.5f, Colors.fade(accent, r.progress), shadow);
            if (r.suffix != null) {
                Render2D.text(ctx, " " + r.suffix, tx + Render2D.width(r.name), y + 1.5f,
                        Colors.fade(client().theme().textDim(), r.progress), shadow);
            }
            y += h;
            i++;
        }
        height = Math.max(ROW, y);
    }
}
