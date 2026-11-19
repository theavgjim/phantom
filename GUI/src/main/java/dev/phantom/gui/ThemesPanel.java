package dev.phantom.gui;

import dev.phantom.core.setting.Setting;
import dev.phantom.core.theme.Theme;
import dev.phantom.integration.PhantomClient;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import dev.phantom.modules.misc.ClickGuiModule;
import dev.phantom.modules.misc.HudModule;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** Theme gallery plus GUI appearance and HUD colour settings. */
final class ThemesPanel {
    float render(Ui ui, DrawContext ctx, Theme current, float x, float y, float w) {
        PhantomClient client = PhantomClient.get();
        ClickGuiModule gui = client.core().modules().get(ClickGuiModule.class);
        HudModule hud = client.core().modules().get(HudModule.class);
        float start = y;

        Widgets.sectionTitle(ui, ctx, current, "Theme", x, y);
        y += 10;
        int cols = w > 300 ? 3 : 2;
        float gap = 6, cardW = (w - gap * (cols - 1)) / cols, cardH = 44;
        List<Theme> themes = client.core().themes().all();
        for (int i = 0; i < themes.size(); i++) {
            Theme th = themes.get(i);
            float tx = x + (i % cols) * (cardW + gap);
            float ty = y + (i / cols) * (cardH + gap);
            boolean sel = th.name().equals(current.name());
            boolean hover = ui.hovered(tx, ty, cardW, cardH);
            float hv = ui.anim("theme:" + th.name(), hover || sel ? 1 : 0, 16);
            Render2D.roundedOutline(ctx, tx, ty, cardW, cardH, 5, 1, ui.c(Colors.lerp(current.outline(), th.accent(), hv)));
            Render2D.roundedRect(ctx, tx, ty, cardW, cardH, 5, ui.c(th.panel()));
            Render2D.roundedRect(ctx, tx + 6, ty + 6, cardW - 12, 14, 3, ui.c(th.accent()), ui.c(th.accentSecondary()));
            Render2D.text(ctx, th.name(), tx + 7, ty + 27, ui.c(th.text()), false);
            float sx = tx + cardW - 10;
            for (int c : new int[]{th.textDim(), th.panelRaised(), th.background()}) {
                Render2D.circle(ctx, sx, ty + 31, 3, ui.c(c));
                sx -= 8;
            }
            if (sel) Render2D.text(ctx, "✔", tx + cardW - 14, ty + 9, ui.c(0xFFFFFFFF), false);
            ui.hit(tx, ty, cardW, cardH, (mx, my, b) -> gui.theme.set(th.name()));
        }
        y += ((themes.size() + cols - 1) / cols) * (cardH + gap) + 8;

        Widgets.sectionTitle(ui, ctx, current, "Interface", x, y);
        y += 11;
        List<Setting<?>> guiSettings = new ArrayList<>(gui.getSettings());
        guiSettings.remove(gui.theme);
        y += SettingsRenderer.render(ui, ctx, current, guiSettings, x, y, w);
        y += 8;

        Widgets.sectionTitle(ui, ctx, current, "HUD Colors", x, y);
        y += 11;
        y += SettingsRenderer.render(ui, ctx, current, hud.getSettings(), x, y, w);
        return y - start + 6;
    }
}
