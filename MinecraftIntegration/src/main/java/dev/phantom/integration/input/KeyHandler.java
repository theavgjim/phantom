package dev.phantom.integration.input;

import dev.phantom.core.module.Module;
import dev.phantom.core.module.ModuleManager;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/** Routes raw keyboard events (from {@code KeyboardMixin}) to module keybinds. */
public final class KeyHandler {
    private final MinecraftClient mc;
    private final ModuleManager modules;

    public KeyHandler(MinecraftClient mc, ModuleManager modules) {
        this.mc = mc;
        this.modules = modules;
    }

    public void onKey(int key, int action) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || action == GLFW.GLFW_REPEAT) return;
        // Keybinds only fire in-game (no screen open), and never while F3 debug combos are held.
        if (mc.currentScreen != null || mc.getOverlay() != null) return;
        if (InputUtilCompat.isF3Held(mc)) return;

        for (Module m : modules.all()) {
            if (m.getKey() != key) continue;
            if (m.isHoldToActivate()) {
                m.setEnabled(action == GLFW.GLFW_PRESS);
            } else if (action == GLFW.GLFW_PRESS) {
                if (m.isToggleable()) m.toggle();
                else m.onActivate();
            }
        }
    }

    /** Releases hold-to-activate modules when a screen opens over the game. */
    public void releaseHeld() {
        for (Module m : modules.all()) if (m.isHoldToActivate() && m.isEnabled()) m.setEnabled(false);
    }

    static final class InputUtilCompat {
        static boolean isF3Held(MinecraftClient mc) {
            return net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow().getHandle(), GLFW.GLFW_KEY_F3);
        }
    }
}
