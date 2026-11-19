package dev.phantom.hud.elements;

import dev.phantom.core.util.Animation;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import dev.phantom.modules.combat.TargetHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

/** Target information: face, name, animated health bar, distance and armor. */
public final class TargetInfoElement extends ScreenElement {
    private final Animation healthAnim = new Animation(1, 8);
    private LivingEntity lastTarget;
    private long last = System.nanoTime();

    public TargetInfoElement() {
        super("target", "Target Info", 0.53, 0.56, true);
        width = 128;
        height = 38;
    }

    private TargetHud module() {
        return client().core().modules().get(TargetHud.class);
    }

    @Override
    public boolean isActive() {
        TargetHud m = module();
        return m != null && m.isEnabled() && client().targets().get() != null;
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        long now = System.nanoTime();
        double dt = (now - last) / 1e9;
        last = now;
        LivingEntity t = client().targets().get();
        if (t == null && editor) t = mc.player;
        width = 128;
        height = 38;
        var style = style();
        var theme = client().theme();
        Render2D.roundedRect(ctx, 0, 0, width, height, 5, style.backgroundColor());
        if (t == null) {
            Render2D.text(ctx, "No target", 8, 15, theme.textDim(), false);
            return;
        }
        TargetHud m = module();
        float frac = Math.max(0, Math.min(1, t.getHealth() / t.getMaxHealth()));
        if (t != lastTarget) {
            healthAnim.snap(frac);
            lastTarget = t;
        }
        healthAnim.target(frac).update(dt);

        float textX = 38;
        boolean face = m.head.isOn() && t instanceof AbstractClientPlayerEntity;
        // Shapes first...
        if (!face) Render2D.roundedRect(ctx, 5, 5, 28, 28, 4, theme.panelRaised());
        float barX = textX, barY = 28, barW = width - textX - 6, barH = 4;
        Render2D.roundedRect(ctx, barX, barY, barW, barH, 2, Colors.withAlpha(theme.panelRaised(), 255));
        int hc = Colors.health(frac);
        Render2D.roundedRect(ctx, barX, barY, barW * healthAnim.getFloat(), barH, 2, Colors.brighten(hc, 0.15f), hc);
        if (t.hurtTime > 0) {
            Render2D.roundedRect(ctx, 0, 0, width, height, 5, Colors.withAlpha(0xFFEF5350, t.hurtTime * 6));
        }
        // ...then textures and text, so nothing drawn afterwards can cover them.
        if (face) {
            PlayerSkinDrawer.draw(ctx, ((AbstractClientPlayerEntity) t).getSkinTextures(), 5, 5, 28);
        } else {
            String letter = t.getName().getString().substring(0, 1).toUpperCase(Locale.ROOT);
            Render2D.centeredText(ctx, letter, 19, 15, style.accent(0), false);
        }
        String name = Render2D.trim(t.getName().getString(), (int) (width - textX - 6));
        Render2D.text(ctx, name, textX, 6, style.textColor.argb(), style.shadow.isOn());
        String info = String.format(Locale.ROOT, "%.1f HP", t.getHealth() + t.getAbsorptionAmount());
        if (m.distance.isOn() && mc.player != null && t != mc.player) {
            info += String.format(Locale.ROOT, "  %.1fm", mc.player.distanceTo(t));
        }
        if (m.armor.isOn() && t.getArmor() > 0) info += "  ⛨" + t.getArmor();
        Render2D.text(ctx, info, textX, 16, theme.textDim(), false);
        Render2D.flush(ctx);
    }
}
