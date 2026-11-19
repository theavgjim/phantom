package dev.phantom.gui;

import dev.phantom.core.theme.Theme;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.PhantomClient;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Drag-and-drop HUD layout editor with edge/centre snapping, per-element scale (mouse wheel),
 * visibility (right click) and a settings panel for colours and options.
 */
public final class HudEditorScreen extends Screen {
    private static final float SNAP = 5;
    private final Screen parent;
    private final PhantomClient client = PhantomClient.get();
    private final Ui ui = new Ui();
    private ScreenElement selected;
    private ScreenElement dragging;
    private float offX, offY;
    private boolean snap = true;
    private float guideX = -1, guideY = -1;
    private long lastFrame = System.nanoTime();

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Phantom HUD Editor"));
        this.parent = parent;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void close() {
        client.mc().setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        Theme t = client.theme();
        ui.begin(mouseX, mouseY, dt);

        ctx.fill(0, 0, width, height, 0x50000000);
        // Subtle grid
        for (int gx = 0; gx < width; gx += 20) ctx.fill(gx, 0, gx + 1, height, 0x10FFFFFF);
        for (int gy = 0; gy < height; gy += 20) ctx.fill(0, gy, width, gy + 1, 0x10FFFFFF);
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x20FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x20FFFFFF);

        client.hud().render(ctx, delta, true);

        List<ScreenElement> elements = client.hud().elements();
        ScreenElement hovered = pick(mouseX, mouseY);
        for (ScreenElement e : elements) {
            boolean sel = e == selected, hov = e == hovered;
            int c = sel ? t.accent() : hov ? Colors.brighten(t.accent(), 0.3f) : Colors.withAlpha(t.textDim(), 140);
            outline(ctx, e.screenX - 1, e.screenY - 1, e.screenW + 2, e.screenH + 2, c);
            if (sel || hov) {
                String label = e.displayName() + (e.visible.isOn() ? "" : " (hidden)") + "  " + Math.round(e.scale.getDouble() * 100) + "%";
                float ly = e.screenY > 12 ? e.screenY - 11 : e.screenY + e.screenH + 2;
                float lw = Render2D.width(label) + 6;
                float lx = Math.min(e.screenX, width - lw);
                Render2D.roundedRect(ctx, lx, ly, lw, 10, 3, Colors.withAlpha(t.background(), 220));
                Render2D.text(ctx, label, lx + 3, ly + 1, t.text(), false);
            }
        }
        if (dragging != null && snap) {
            if (guideX >= 0) ctx.fill((int) guideX, 0, (int) guideX + 1, height, t.accent());
            if (guideY >= 0) ctx.fill(0, (int) guideY, width, (int) guideY + 1, t.accent());
        }

        drawToolbar(ctx, t);
        if (selected != null) drawPanel(ctx, t);
        ui.end();
    }

    private void drawToolbar(DrawContext ctx, Theme t) {
        float w = 252, h = 22, x = (width - w) / 2f, y = height - h - 8;
        Render2D.roundedOutline(ctx, x, y, w, h, 6, 1, t.outline());
        Render2D.roundedRect(ctx, x, y, w, h, 6, t.panel());
        Widgets.button(ui, ctx, t, "he:done", x + 4, y + 4, 56, 14, "Done", true, this::close);
        Widgets.button(ui, ctx, t, "he:snap", x + 64, y + 4, 60, 14, snap ? "Snap: On" : "Snap: Off", false, () -> snap = !snap);
        Widgets.button(ui, ctx, t, "he:reset", x + 128, y + 4, 60, 14, "Reset all", false, () -> {
            for (ScreenElement e : client.hud().elements()) {
                e.resetPosition();
                e.visible.reset();
            }
        });
        Render2D.text(ctx, "RMB hide", x + 194, y + 4, t.textDim(), false, 0.7f);
        Render2D.text(ctx, "Wheel scale", x + 194, y + 11, t.textDim(), false, 0.7f);
    }

    private void drawPanel(DrawContext ctx, Theme t) {
        float pw = 150;
        boolean left = selected.screenX + selected.screenW / 2 > width / 2f;
        float px = left ? 8 : width - pw - 8, py = 8;
        float ph = Math.min(height - 46, 260);
        Render2D.roundedOutline(ctx, px, py, pw, ph, 6, 1, t.outline());
        Render2D.roundedRect(ctx, px, py, pw, ph, 6, t.panel());
        Render2D.text(ctx, selected.displayName(), px + 8, py + 7, t.text(), false);
        Widgets.button(ui, ctx, t, "he:resetone", px + pw - 48, py + 5, 40, 12, "Reset", false, selected::resetPosition);
        ui.pushClip(ctx, px, py + 22, pw, ph - 26);
        SettingsRenderer.render(ui, ctx, t, selected.settings(), px + 8, py + 24, pw - 16);
        ui.popClip(ctx);
        ui.hit(px, py, pw, ph, (mx, my, b) -> {
        }); // swallow clicks on the panel background
    }

    private static void outline(DrawContext ctx, float x, float y, float w, float h, int c) {
        int x1 = (int) x, y1 = (int) y, x2 = (int) Math.ceil(x + w), y2 = (int) Math.ceil(y + h);
        ctx.fill(x1, y1, x2, y1 + 1, c);
        ctx.fill(x1, y2 - 1, x2, y2, c);
        ctx.fill(x1, y1, x1 + 1, y2, c);
        ctx.fill(x2 - 1, y1, x2, y2, c);
    }

    private ScreenElement pick(double mx, double my) {
        List<ScreenElement> list = client.hud().elements();
        for (int i = list.size() - 1; i >= 0; i--) {
            ScreenElement e = list.get(i);
            if (mx >= e.screenX && mx <= e.screenX + e.screenW && my >= e.screenY && my <= e.screenY + e.screenH) return e;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ui.focus = null;
        if (ui.click(mx, my, button)) return true;
        ScreenElement e = pick(mx, my);
        if (e == null) {
            selected = null;
            return true;
        }
        if (button == 1) {
            e.visible.toggle();
            return true;
        }
        selected = e;
        dragging = e;
        offX = (float) mx - e.screenX;
        offY = (float) my - e.screenY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (ui.drag != null) {
            ui.drag.onDrag(mx, my);
            return true;
        }
        if (dragging == null) return true;
        float w = dragging.screenW, h = dragging.screenH;
        float x = (float) mx - offX, y = (float) my - offY;
        guideX = guideY = -1;
        double fx, fy;
        if (snap) {
            if (Math.abs(x) < SNAP) x = 0;
            if (Math.abs(y) < SNAP) y = 0;
            if (Math.abs(x + w / 2 - width / 2f) < SNAP) {
                x = width / 2f - w / 2;
                guideX = width / 2f;
            }
            if (Math.abs(y + h / 2 - height / 2f) < SNAP) {
                y = height / 2f - h / 2;
                guideY = height / 2f;
            }
        }
        // Elements snapped to the right/bottom edge stay anchored there when their size changes.
        fx = snap && x + w > width - SNAP ? 1.0 : x / width;
        fy = snap && y + h > height - SNAP ? 1.0 : y / height;
        dragging.setPosition(fx, fy);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        ui.drag = null;
        guideX = guideY = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        ScreenElement e = pick(mx, my);
        if (e != null) e.scale.set(e.scale.getDouble() + Math.signum(v) * 0.05);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ui.focus != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                ui.focus = null;
                return true;
            }
            if (ui.focus.keyPressed(keyCode, modifiers)) return true;
        }
        if (selected != null && keyCode == GLFW.GLFW_KEY_DELETE) {
            selected.visible.toggle();
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
        return super.charTyped(chr, modifiers);
    }
}
