package dev.phantom.modules.misc;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.core.util.Animation;
import dev.phantom.integration.module.ClientModule;
import org.lwjgl.glfw.GLFW;

/** Hold-to-zoom (default C) with smooth transition. */
public final class Zoom extends ClientModule {
    private final NumberSetting factor = add(new NumberSetting("Factor", "Zoom amount", 4, 1.5, 10, 0.5, "x"));
    private final BooleanSetting smoothCamera = add(new BooleanSetting("Cinematic Camera", "Smooth mouse while zoomed", true));
    private final Animation anim = new Animation(1, 12);
    private long last = System.nanoTime();
    private boolean prevSmooth;

    public Zoom() {
        super("Zoom", "Hold to zoom in", Category.MISC);
        holdToActivate();
        hideFromList();
        defaultKey(GLFW.GLFW_KEY_C);
    }

    @Override
    protected void onEnable() {
        prevSmooth = mc.options.smoothCameraEnabled;
        if (smoothCamera.isOn()) mc.options.smoothCameraEnabled = true;
    }

    @Override
    protected void onDisable() {
        mc.options.smoothCameraEnabled = prevSmooth;
    }

    /** Called from GameRenderer#getFov every frame. */
    public double apply(double fov) {
        long now = System.nanoTime();
        double dt = (now - last) / 1e9;
        last = now;
        anim.target(isEnabled() ? factor.getDouble() : 1).update(dt);
        return anim.get() <= 1.0001 ? fov : fov / anim.get();
    }
}
