package dev.phantom.integration.event;

import net.minecraft.client.gui.DrawContext;

/** Posted once per frame in the HUD pass, before HUD elements are drawn. */
public final class Render2DEvent {
    public static final Render2DEvent INSTANCE = new Render2DEvent();

    public DrawContext context;
    public float tickDelta;

    private Render2DEvent() {
    }
}
