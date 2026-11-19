package dev.phantom.hud;

import dev.phantom.core.hud.HudElement;
import dev.phantom.integration.PhantomClient;
import dev.phantom.modules.misc.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** A HUD element that knows how to draw itself at local (0,0) and reports its size. */
public abstract class ScreenElement extends HudElement {
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    protected float width = 40;
    protected float height = 10;
    /** Last on-screen placement (scaled GUI px), used by the HUD editor. */
    public float screenX, screenY, screenW, screenH;

    protected ScreenElement(String id, String name, double x, double y, boolean visible) {
        super(id, name, x, y, visible);
    }

    /**
     * @param editor       true inside the HUD editor (draw a preview even without live data)
     * @param rightAligned true when the element sits on the right half of the screen
     */
    public abstract void render(DrawContext ctx, float tickDelta, boolean editor, boolean rightAligned);

    /** Whether there is anything to show outside the editor (e.g. TargetHUD needs a target). */
    public boolean isActive() {
        return mc.player != null;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    protected static HudModule style() {
        return PhantomClient.get().core().modules().get(HudModule.class);
    }

    protected static PhantomClient client() {
        return PhantomClient.get();
    }
}
