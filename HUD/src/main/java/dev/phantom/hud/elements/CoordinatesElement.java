package dev.phantom.hud.elements;

import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.world.World;

import java.util.Locale;

public final class CoordinatesElement extends ScreenElement {
    private final BooleanSetting netherCoords = add(new BooleanSetting("Nether Coords", "Show converted Overworld/Nether position", true));
    private final BooleanSetting decimals = add(new BooleanSetting("Decimals", "Show one decimal place", false));

    public CoordinatesElement() {
        super("coordinates", "Coordinates", 0.004, 1.0, true);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        var style = style();
        double x = 0, y = 64, z = 0;
        boolean nether = false, overworld = true;
        if (mc.player != null) {
            var p = mc.player.getLerpedPos(tickDelta);
            x = p.x;
            y = p.y;
            z = p.z;
            nether = mc.world.getRegistryKey() == World.NETHER;
            overworld = mc.world.getRegistryKey() == World.OVERWORLD;
        }
        String fmt = decimals.isOn() ? "%.1f %.1f %.1f" : "%.0f %.0f %.0f";
        String main = String.format(Locale.ROOT, fmt, x, y, z);
        String second = null;
        if (netherCoords.isOn() && (nether || overworld)) {
            double f = nether ? 8 : 0.125;
            second = (nether ? "Overworld " : "Nether ") + String.format(Locale.ROOT, "%.0f %.0f", x * f, z * f);
        }
        String label = "XYZ ";
        int w1 = Render2D.width(label + main), w2 = second == null ? 0 : Render2D.width(second);
        width = Math.max(w1, w2) + 8;
        height = second == null ? 13 : 23;
        Render2D.roundedRect(ctx, 0, 0, width, height, 3, style.backgroundColor());
        Render2D.text(ctx, label, 4, 3, style.accent(0.2f), style.shadow.isOn());
        Render2D.text(ctx, main, 4 + Render2D.width(label), 3, style.textColor.argb(), style.shadow.isOn());
        if (second != null) Render2D.text(ctx, second, 4, 13, client().theme().textDim(), style.shadow.isOn());
    }
}
