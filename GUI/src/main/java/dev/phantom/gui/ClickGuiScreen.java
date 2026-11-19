package dev.phantom.gui;

import dev.phantom.core.module.Category;
import dev.phantom.core.module.Module;
import dev.phantom.core.theme.Theme;
import dev.phantom.integration.PhantomClient;
import dev.phantom.integration.guard.PrivateWorldGuard;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import dev.phantom.modules.misc.ClickGuiModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Phantom ClickGUI: category sidebar, search, animated module cards with inline settings,
 * keybind editor, theme selector, profile manager and a shortcut to the HUD editor.
 */
public final class ClickGuiScreen extends Screen {
    enum Section { MODULES, THEMES, PROFILES }

    private static Section lastSection = Section.MODULES;
    private static Category lastCategory = Category.COMBAT;

    private final PhantomClient client = PhantomClient.get();
    private final ClickGuiModule settings = client.core().modules().get(ClickGuiModule.class);
    private final Ui ui = new Ui();
    private final TextInput search = new TextInput(32, "Search modules...");
    private final ThemesPanel themesPanel = new ThemesPanel();
    private final ProfilesPanel profilesPanel = new ProfilesPanel();
    private final Map<Module, Float> measured = new HashMap<>();
    private Section section = lastSection;
    private Category category = lastCategory;
    private float openProgress;
    private boolean closing;
    private float scroll, scrollTarget, contentHeight;
    private long lastFrame = System.nanoTime();
    private String contentKey = "";
    /** Vertical offset of the content while it transitions in. */
    private float contentShift;

    // layout (scaled GUI px), recomputed every frame
    private float wx, wy, ww, wh, cx, cy, cw, ch;

    public ClickGuiScreen() {
        super(Text.literal("Phantom"));
        search.onChange = s -> scrollTarget = 0;
    }

    @Override
    protected void init() {
        profilesPanel.refresh();
        client.keys().releaseHeld();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // We draw our own dimmed backdrop in render().
    }

    @Override
    public void close() {
        closing = true;
        ui.focus = null;
        ui.listening = null;
        ui.openDropdown = null;
        lastSection = section;
        lastCategory = category;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        float speed = settings.animationSpeed.getFloat();
        openProgress += ((closing ? 0 : 1) - openProgress) * (1 - (float) Math.exp(-speed * dt));
        if (closing && openProgress < 0.03f) {
            super.close();
            return;
        }
        Theme t = client.theme();
        ui.begin(mouseX, mouseY, dt);
        ui.alpha = Math.min(1, openProgress * 1.15f);

        ctx.fill(0, 0, width, height, Colors.withAlpha(0x05050A, (int) (settings.dim.getInt() * ui.alpha)));

        ww = Math.min(width - 24, 500);
        wh = Math.min(height - 24, 310);
        wx = (width - ww) / 2f;
        // Slide up slightly while opening.
        wy = (height - wh) / 2f + (1 - openProgress) * 12;
        float r = settings.radius.getFloat();
        float side = 116;

        Render2D.roundedOutline(ctx, wx, wy, ww, wh, r, 1, ui.c(t.outline()));
        Render2D.roundedRect(ctx, wx, wy, ww, wh, r, ui.c(t.panel()));
        Render2D.roundedRect(ctx, wx, wy, side, wh, r, ui.c(t.background()));
        Render2D.rect(ctx, wx + side - r, wy, r, wh, ui.c(t.background()));
        Render2D.rect(ctx, wx + side, wy + 8, 1, wh - 16, ui.c(t.outline()));

        drawSidebar(ctx, t, side);

        cx = wx + side + 12;
        cw = ww - side - 24;
        cy = wy + 36;
        ch = wh - 36 - 20;

        // Crossfade + slide whenever the visible content changes (category, section or search).
        String key = section + ":" + (search.text.isBlank() ? category.name() : "search");
        if (!key.equals(contentKey)) {
            contentKey = key;
            ui.snapAnim("content", 0);
        }
        float content = ui.anim("content", 1, settings.animationSpeed.getDouble() * 0.9);
        float eased = 1 - (1 - content) * (1 - content) * (1 - content);
        float baseAlpha = ui.alpha;
        ui.alpha = baseAlpha * eased;
        contentShift = (1 - eased) * 10;

        switch (section) {
            case MODULES -> drawModules(ctx, t);
            case THEMES -> drawScrollable(ctx, t, "Themes", "Appearance and colours",
                    (y) -> themesPanel.render(ui, ctx, t, cx, y, cw));
            case PROFILES -> drawScrollable(ctx, t, "Profiles", "Profile: " + client.core().config().activeProfile(),
                    (y) -> profilesPanel.render(ui, ctx, t, cx, y, cw));
        }
        ui.alpha = baseAlpha;

        String hint = section == Section.MODULES
                ? "LMB expand  ·  RMB toggle  ·  MMB bind  ·  RMB slider = reset"
                : "Changes are saved automatically";
        Render2D.text(ctx, hint, cx, wy + wh - 13, ui.c(Colors.withAlpha(t.textDim(), 160)), false, 0.75f);

        ui.end();
    }

    private void drawSidebar(DrawContext ctx, Theme t, float side) {
        float x = wx + 10, y = wy + 12;
        Render2D.text(ctx, "PHANTOM", x, y, ui.c(t.accent()), true, 1.35f);
        Render2D.text(ctx, "client " + client.core().version(), x, y + 13, ui.c(t.textDim()), false, 0.7f);

        y = wy + 48;
        float itemH = wh < 250 ? 16 : 18, itemW = side - 16;
        // Animated selection pill.
        int selIndex = section == Section.MODULES && search.text.isEmpty() ? category.ordinal()
                : section == Section.THEMES ? Category.values().length + 1
                : section == Section.PROFILES ? Category.values().length + 2 : -1;
        if (selIndex >= 0) {
            float slotY = y + selIndex * (itemH + 2) + (selIndex > Category.values().length - 1 ? 8 : 0);
            float sy = ui.anim("sideSel", slotY, 18);
            Render2D.roundedRect(ctx, x - 2, sy, itemW, itemH, 4, ui.c(t.panelRaised()));
            Render2D.roundedRect(ctx, x - 2, sy + 4, 2, itemH - 8, 1, ui.c(t.accent()));
        }
        for (Category c : Category.values()) {
            boolean sel = section == Section.MODULES && category == c && search.text.isEmpty();
            int count = (int) client.core().modules().byCategory(c).stream().filter(Module::isEnabled).count();
            sideItem(ctx, t, "cat:" + c, c.icon(), c.displayName(), count > 0 ? Integer.toString(count) : "", x, y, itemW, itemH, sel, () -> {
                section = Section.MODULES;
                category = c;
                search.set("");
                scrollTarget = 0;
            });
            y += itemH + 2;
        }
        y += 8;
        Render2D.rect(ctx, x + 2, y - 5, itemW - 8, 1, ui.c(t.outline()));
        sideItem(ctx, t, "hud", "▣", "HUD Editor", "", x, y, itemW, itemH, false,
                () -> client.mc().setScreen(new HudEditorScreen(this)));
        y += itemH + 2;
        sideItem(ctx, t, "themes", "◐", "Themes", "", x, y, itemW, itemH, section == Section.THEMES, () -> {
            section = Section.THEMES;
            scrollTarget = 0;
        });
        y += itemH + 2;
        sideItem(ctx, t, "profiles", "☰", "Profiles", "", x, y, itemW, itemH, section == Section.PROFILES, () -> {
            section = Section.PROFILES;
            scrollTarget = 0;
            profilesPanel.refresh();
        });

        // Context status under the logo.
        PrivateWorldGuard.Context ctxState = client.guard().context();
        String status;
        int color;
        switch (ctxState) {
            case PRIVATE_WORLD -> {
                status = "Private world";
                color = 0xFF66BB6A;
            }
            case REMOTE_SERVER -> {
                status = "Server: training locked";
                color = 0xFFFFA726;
            }
            default -> {
                status = "Main menu";
                color = t.textDim();
            }
        }
        float by = wy + 34;
        Render2D.circle(ctx, x + 2.5f, by + 2.5f, 2f, ui.c(color));
        String line = status + "  \u00B7  " + client.core().config().activeProfile();
        Render2D.text(ctx, Render2D.trim(line, (int) ((itemW - 8) / 0.7f)), x + 7, by, ui.c(t.textDim()), false, 0.7f);
    }

    private void sideItem(DrawContext ctx, Theme t, String id, String icon, String label, String badge,
                          float x, float y, float w, float h, boolean selected, Runnable action) {
        boolean hover = ui.hovered(x - 2, y, w, h);
        float hv = ui.anim("side:" + id, hover && !selected ? 1 : 0, 18);
        if (hv > 0.01f) Render2D.roundedRect(ctx, x - 2, y, w, h, 4, ui.c(Colors.withAlpha(t.panelRaised(), (int) (160 * hv))));
        int tc = selected ? t.text() : Colors.lerp(t.textDim(), t.text(), hv);
        Render2D.text(ctx, icon, x + 5, y + 5, ui.c(selected ? t.accent() : tc), false);
        Render2D.text(ctx, label, x + 18 + hv * 1.5f, y + 5, ui.c(tc), false);
        if (!badge.isEmpty()) {
            float bw = Render2D.width(badge) + 6;
            Render2D.roundedRect(ctx, x + w - bw - 6, y + 4, bw, 10, 5, ui.c(Colors.withAlpha(t.accent(), 90)));
            Render2D.centeredText(ctx, badge, x + w - bw / 2 - 6, y + 5, ui.c(t.text()), false);
        }
        ui.hit(x - 2, y, w, h, (mx, my, b) -> {
            if (b == 0) action.run();
        });
    }

    // ------------------------------------------------------------------ modules section

    private void drawModules(DrawContext ctx, Theme t) {
        boolean searching = !search.text.isBlank();
        List<Module> list = searching ? client.core().modules().search(search.text) : client.core().modules().byCategory(category);
        String title = searching ? "Search" : category.displayName();
        Render2D.text(ctx, title, cx, wy + 12, ui.c(t.text()), false, 1.25f);
        Render2D.text(ctx, list.size() + (list.size() == 1 ? " module" : " modules"), cx, wy + 24, ui.c(t.textDim()), false, 0.75f);
        Widgets.textField(ui, ctx, t, cx + cw - 130, wy + 10, 130, 15, search);
        if (search.text.isEmpty() && ui.focus != search) {
            Render2D.text(ctx, "⌕", cx + cw - 12, wy + 13.5f, ui.c(t.textDim()), false);
        }

        updateScroll();
        ui.pushClip(ctx, cx - 4, cy, cw + 8, ch);
        float y = cy - scroll + contentShift;
        float start = y;
        for (Module m : list) {
            y += drawCard(ctx, t, m, cx, y, cw) + 5;
        }
        if (list.isEmpty()) {
            Render2D.centeredText(ctx, "No modules match \"" + search.text + "\"", cx + cw / 2, cy + 30, ui.c(t.textDim()), false);
        }
        contentHeight = y - start;
        ui.popClip(ctx);
        drawScrollbar(ctx, t);
    }

    private float drawCard(DrawContext ctx, Theme t, Module m, float x, float y, float w) {
        String key = m.getName();
        float headerH = 30;
        float exp = ui.anim("exp:" + key, ui.expanded.contains(m) ? 1 : 0, settings.animationSpeed.getDouble());
        float body = measured.getOrDefault(m, 0f);
        float h = headerH + body * exp;
        if (!ui.visible(y, h)) return h;

        boolean hover = ui.hovered(x, y, w, headerH);
        float hv = ui.anim("card:" + key, hover ? 1 : 0, 18);
        float on = ui.anim("on:" + key, m.isEnabled() ? 1 : 0, 14);
        float r = Math.min(settings.radius.getFloat(), 6);
        int bg = Colors.lerp(t.panelRaised(), Colors.brighten(t.panelRaised(), 0.05f), hv);
        Render2D.roundedRect(ctx, x, y, w, h, r, ui.c(bg));
        if (on > 0.01f) {
            Render2D.roundedRect(ctx, x, y, w, headerH, r, ui.c(Colors.withAlpha(t.accent(), (int) (28 * on))), ui.c(Colors.withAlpha(t.accent(), 0)));
            Render2D.roundedRect(ctx, x + 1, y + 7, 2, headerH - 14, 1, ui.c(Colors.fade(t.accent(), on)));
        }

        // Body click = expand, right = toggle, middle = keybind.
        ui.hit(x, y, w, headerH, (mx, my, b) -> {
            if (b == 0) {
                if (!ui.expanded.remove(m)) ui.expanded.add(m);
            } else if (b == 1) {
                m.toggle();
            } else if (b == 2) {
                ui.listening = m;
            }
        });

        float nx = x + 10;
        int nameColor = Colors.lerp(t.text(), Colors.brighten(t.accent(), 0.35f), on);
        Render2D.text(ctx, m.getName(), nx, y + (settings.descriptions.isOn() ? 6 : 11), ui.c(nameColor), false);
        float badgeX = nx + Render2D.width(m.getName()) + 5;
        if (m.isPrivateWorldOnly()) {
            String sp = "PRIVATE";
            float bw = Render2D.width(sp) * 0.65f + 6;
            Render2D.roundedRect(ctx, badgeX, y + (settings.descriptions.isOn() ? 6 : 11), bw, 8, 3, ui.c(Colors.withAlpha(0xFFFFA726, 50)));
            Render2D.text(ctx, sp, badgeX + 3, y + (settings.descriptions.isOn() ? 7.5f : 12.5f), ui.c(0xFFFFB74D), false, 0.65f);
        }
        float rightW = 70;
        if (settings.descriptions.isOn()) {
            Render2D.text(ctx, Render2D.trim(m.getDescription(), (int) ((w - rightW - 20) / 0.8f)), nx, y + 17,
                    ui.c(t.textDim()), false, 0.8f);
        }

        // Keybind chip.
        String keyName = ui.listening == m ? "..." : m.getKey() == Module.KEY_NONE ? "" : keyName(m.getKey());
        float sx = x + w - 34;
        if (!keyName.isEmpty()) {
            float kw = Render2D.width(keyName) + 8;
            Render2D.roundedRect(ctx, sx - kw - 6, y + 9.5f, kw, 11, 3, ui.c(t.background()));
            Render2D.centeredText(ctx, keyName, sx - kw / 2 - 6, y + 11, ui.c(ui.listening == m ? t.accent() : t.textDim()), false);
        }
        if (m.isToggleable()) {
            Widgets.toggle(ui, ctx, t, "mod:" + key, sx, y + 9.5f, m.isEnabled(), m::toggle);
        } else {
            Render2D.text(ctx, "⚙", sx + 7, y + 10.5f, ui.c(t.textDim()), false);
        }

        // Expanded settings.
        if (exp > 0.001f) {
            ui.pushClip(ctx, x, y + headerH, w, body * exp);
            float sy = y + headerH;
            float inner = drawModuleSettings(ctx, t, m, x + 10, sy, w - 20);
            measured.put(m, inner + 8);
            ui.popClip(ctx);
        }
        return h;
    }

    private float drawModuleSettings(DrawContext ctx, Theme t, Module m, float x, float y, float w) {
        float start = y;
        Render2D.rect(ctx, x, y, w, 1, ui.c(t.outline()));
        y += 5;
        if (m.isPrivateWorldOnly()) {
            Render2D.text(ctx, "Runs only in singleplayer or your own LAN world.", x, y, ui.c(0xFFFFB74D), false, 0.8f);
            y += 11;
        }
        // Keybind editor row.
        Render2D.text(ctx, m.isHoldToActivate() ? "Keybind (hold)" : "Keybind", x, y + 3, ui.c(t.text()), false);
        String kn = ui.listening == m ? "Press a key..." : m.getKey() == Module.KEY_NONE ? "None" : keyName(m.getKey());
        float bw = Math.max(50, Render2D.width(kn) + 12);
        Widgets.button(ui, ctx, t, "bind:" + m.getName(), x + w - bw - 44, y, bw, 13, kn, ui.listening == m, () -> ui.listening = m);
        Widgets.button(ui, ctx, t, "reset:" + m.getName(), x + w - 40, y, 40, 13, "Reset", false, m::resetToDefaults);
        y += 18;
        y += SettingsRenderer.render(ui, ctx, t, m.getSettings(), x, y, w);
        return y - start;
    }

    // ------------------------------------------------------------------ generic scrollable section

    private interface Body {
        float draw(float y);
    }

    private void drawScrollable(DrawContext ctx, Theme t, String title, String subtitle, Body body) {
        Render2D.text(ctx, title, cx, wy + 12, ui.c(t.text()), false, 1.25f);
        Render2D.text(ctx, subtitle, cx, wy + 24, ui.c(t.textDim()), false, 0.75f);
        updateScroll();
        ui.pushClip(ctx, cx - 4, cy, cw + 8, ch);
        contentHeight = body.draw(cy - scroll + contentShift);
        ui.popClip(ctx);
        drawScrollbar(ctx, t);
    }

    private void updateScroll() {
        float max = Math.max(0, contentHeight - ch);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scroll += (scrollTarget - scroll) * (1 - (float) Math.exp(-18 * ui.dt));
        if (Math.abs(scrollTarget - scroll) < 0.1f) scroll = scrollTarget;
    }

    private void drawScrollbar(DrawContext ctx, Theme t) {
        if (contentHeight <= ch) return;
        float barH = Math.max(20, ch * ch / contentHeight);
        float barY = cy + (ch - barH) * (scroll / (contentHeight - ch));
        Render2D.roundedRect(ctx, cx + cw + 5, barY, 2, barH, 1, ui.c(Colors.withAlpha(t.textDim(), 120)));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        ui.listening = null;
        TextInput before = ui.focus;
        ui.focus = null;
        if (ui.click(mouseX, mouseY, button)) return true;
        if (before != null && ui.focus == null) return true;
        // Click outside the window closes the GUI.
        if (mouseX < wx || mouseX > wx + ww || mouseY < wy || mouseY > wy + wh) close();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (ui.drag != null) {
            ui.drag.onDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        ui.drag = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        ui.openDropdown = null;
        if (mouseX >= cx - 4 && mouseX <= cx + cw + 8 && mouseY >= cy && mouseY <= cy + ch) {
            scrollTarget -= (float) vertical * 22;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ui.listening instanceof Module) {
            bindListening(keyCode);
            return true;
        }
        if (ui.focus != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ui.focus = null;
                return true;
            }
            if (ui.focus.keyPressed(keyCode, modifiers)) return true;
        }
        if (ui.openDropdown != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            ui.openDropdown = null;
            return true;
        }
        // Ctrl+F focuses search; typing anywhere also starts a search.
        if (keyCode == GLFW.GLFW_KEY_F && hasControlDown()) {
            section = Section.MODULES;
            ui.focus = search;
            return true;
        }
        if (keyCode == settings.getKey() && keyCode != GLFW.GLFW_KEY_UNKNOWN) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (ui.focus != null) {
            ui.focus.charTyped(chr);
            return true;
        }
        if (section == Section.MODULES && Character.isLetterOrDigit(chr)) {
            ui.focus = search;
            search.charTyped(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void bindListening(int keyCode) {
        Module m = (Module) ui.listening;
        ui.listening = null;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            m.setKey(Module.KEY_NONE);
        } else if (keyCode >= 0) {
            m.setKey(keyCode);
        }
    }

    static String keyName(int key) {
        String s = InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString();
        return s.length() > 12 ? s.substring(0, 12) : s;
    }
}
