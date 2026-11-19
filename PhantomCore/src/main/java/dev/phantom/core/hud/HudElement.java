package dev.phantom.core.hud;

import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.core.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent model of a HUD element: position (as a fraction of the screen so layouts survive
 * resolution / GUI-scale changes), scale, visibility and element specific settings.
 * Rendering lives in the Minecraft integration.
 */
public abstract class HudElement {
    private final String id;
    private final String displayName;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final double defaultX;
    private final double defaultY;
    private double x;
    private double y;
    private Runnable changeListener = () -> {
    };

    public final BooleanSetting visible;
    public final NumberSetting scale;

    protected HudElement(String id, String displayName, double defaultX, double defaultY, boolean visibleByDefault) {
        this.id = id;
        this.displayName = displayName;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.x = defaultX;
        this.y = defaultY;
        this.visible = add(new BooleanSetting("Visible", "Show this element", visibleByDefault));
        this.scale = add(new NumberSetting("Scale", "Element size", 1.0, 0.5, 2.5, 0.05, "x"));
    }

    protected final <S extends Setting<?>> S add(S s) {
        settings.add(s);
        s.setChangeListener(x -> changeListener.run());
        return s;
    }

    public final String id() {
        return id;
    }

    public final String displayName() {
        return displayName;
    }

    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(settings);
    }

    public final double x() {
        return x;
    }

    public final double y() {
        return y;
    }

    /** Position as screen fractions, clamped to [0,1]. */
    public final void setPosition(double fx, double fy) {
        double nx = clamp(fx), ny = clamp(fy);
        if (nx == x && ny == y) return;
        x = nx;
        y = ny;
        changeListener.run();
    }

    public final void resetPosition() {
        setPosition(defaultX, defaultY);
        scale.reset();
    }

    public final void setChangeListener(Runnable r) {
        this.changeListener = r == null ? () -> {
        } : r;
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 0 : Math.max(0, Math.min(1, v));
    }
}
