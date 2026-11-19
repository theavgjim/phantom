package dev.phantom.integration;

import dev.phantom.modules.misc.Zoom;
import dev.phantom.modules.movement.NoSlow;
import dev.phantom.modules.visuals.Freecam;
import dev.phantom.modules.visuals.Highlight;
import net.minecraft.entity.Entity;

/**
 * The only entry points mixins use. Keeps mixin classes tiny and makes every hook a cheap
 * "is the module on?" check when Phantom features are disabled.
 */
public final class Hooks {
    static Freecam freecam;
    static Highlight highlight;
    static NoSlow noSlow;
    static Zoom zoom;

    private Hooks() {
    }

    static void bind(PhantomClient client) {
        var m = client.core().modules();
        freecam = m.get(Freecam.class);
        highlight = m.get(Highlight.class);
        noSlow = m.get(NoSlow.class);
        zoom = m.get(Zoom.class);
    }

    public static void onKey(long window, int key, int action) {
        PhantomClient c = PhantomClient.getOrNull();
        if (c != null && window == c.mc().getWindow().getHandle()) c.keys().onKey(key, action);
    }

    public static Freecam activeFreecam() {
        return freecam != null && freecam.isEnabled() ? freecam : null;
    }

    public static boolean shouldOutline(Entity entity) {
        return highlight != null && highlight.isEnabled() && highlight.matches(entity);
    }

    public static int outlineColor(Entity entity) {
        return highlight.colorFor(entity);
    }

    public static boolean noSlowActive() {
        return noSlow != null && noSlow.isEnabled();
    }

    public static double modifyFov(double fov) {
        return zoom != null ? zoom.apply(fov) : fov;
    }

    public static void onAttack(Entity target) {
        PhantomClient c = PhantomClient.getOrNull();
        if (c != null) c.onAttack(target);
    }
}
