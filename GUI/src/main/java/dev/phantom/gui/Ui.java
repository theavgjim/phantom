package dev.phantom.gui;

import dev.phantom.core.util.Animation;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immediate-mode interaction state. Widgets register hit areas while drawing; clicks are resolved
 * against the areas registered in the previous frame (top-most first). Also owns clipping,
 * per-widget animations, text focus, key capture and modal popups.
 */
public final class Ui {
    @FunctionalInterface
    public interface ClickHandler {
        void onClick(double mx, double my, int button);
    }

    @FunctionalInterface
    public interface DragHandler {
        void onDrag(double mx, double my);
    }

    private record Hit(float x, float y, float w, float h, float cx1, float cy1, float cx2, float cy2,
                       boolean modal, ClickHandler handler) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h
                    && mx >= cx1 && mx < cx2 && my >= cy1 && my < cy2;
        }
    }

    private List<Hit> hits = new ArrayList<>();
    private List<Hit> building = new ArrayList<>();
    private final Deque<float[]> clips = new ArrayDeque<>();
    private final Map<String, Animation> anims = new HashMap<>();
    private final List<Runnable> deferred = new ArrayList<>();
    private boolean modalDrawing;
    private boolean modalOpen;

    public double mx, my;
    public float dt;
    /** Global opacity (open/close fade). */
    public float alpha = 1f;
    public DragHandler drag;
    public TextInput focus;
    /** Object waiting for a key press (e.g. a module whose keybind is being edited). */
    public Object listening;
    /** Key of the dropdown currently open, if any. */
    public Object openDropdown;
    public final java.util.Set<Object> expanded = new java.util.HashSet<>();
    public final Map<Object, TextInput> inputs = new HashMap<>();

    public void begin(double mouseX, double mouseY, float frameTime) {
        mx = mouseX;
        my = mouseY;
        dt = frameTime;
        building = new ArrayList<>();
        clips.clear();
        deferred.clear();
        modalOpen = openDropdown != null;
    }

    /** Draws deferred (popup) content above everything, then publishes this frame's hit areas. */
    public void end() {
        modalDrawing = true;
        for (Runnable r : new ArrayList<>(deferred)) r.run();
        modalDrawing = false;
        hits = building;
    }

    /** Queue modal content (dropdown lists) to draw on top at the end of the frame. */
    public void defer(Runnable r) {
        deferred.add(r);
    }

    // ------------------------------------------------------------------ hits

    public void hit(float x, float y, float w, float h, ClickHandler handler) {
        float[] c = clips.peek();
        if (c == null) c = new float[]{-1e9f, -1e9f, 1e9f, 1e9f};
        if (modalDrawing) c = new float[]{-1e9f, -1e9f, 1e9f, 1e9f};
        building.add(new Hit(x, y, w, h, c[0], c[1], c[2], c[3], modalDrawing, handler));
    }

    public boolean hovered(float x, float y, float w, float h) {
        if (modalOpen && !modalDrawing) return false;
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        float[] c = clips.peek();
        return c == null || modalDrawing || (mx >= c[0] && mx < c[2] && my >= c[1] && my < c[3]);
    }

    /** @return true if a widget consumed the click */
    public boolean click(double x, double y, int button) {
        boolean anyModal = false;
        for (Hit h : hits) if (h.modal) anyModal = true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (anyModal && !h.modal) continue;
            if (h.contains(x, y)) {
                h.handler.onClick(x, y, button);
                return true;
            }
        }
        if (anyModal || openDropdown != null) {
            openDropdown = null;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ clipping

    public void pushClip(DrawContext ctx, float x, float y, float w, float h) {
        float[] parent = clips.peek();
        float x1 = x, y1 = y, x2 = x + w, y2 = y + h;
        if (parent != null) {
            x1 = Math.max(x1, parent[0]);
            y1 = Math.max(y1, parent[1]);
            x2 = Math.min(x2, parent[2]);
            y2 = Math.min(y2, parent[3]);
        }
        clips.push(new float[]{x1, y1, x2, y2});
        ctx.draw();
        ctx.enableScissor((int) Math.floor(x1), (int) Math.floor(y1), (int) Math.ceil(Math.max(x1, x2)), (int) Math.ceil(Math.max(y1, y2)));
    }

    public void popClip(DrawContext ctx) {
        ctx.draw();
        ctx.disableScissor();
        clips.pop();
    }

    public boolean visible(float y, float h) {
        float[] c = clips.peek();
        return c == null || (y + h >= c[1] && y <= c[3]);
    }

    // ------------------------------------------------------------------ helpers

    public float anim(String key, double target, double speed) {
        Animation a = anims.computeIfAbsent(key, k -> new Animation(target, speed));
        a.setSpeed(speed);
        return (float) a.target(target).update(dt);
    }

    public void snapAnim(String key, double value) {
        anims.computeIfAbsent(key, k -> new Animation(value, 10)).snap(value);
    }

    public int c(int color) {
        return Colors.fade(color, alpha);
    }

    public boolean isModalOpen() {
        return modalOpen;
    }
}
