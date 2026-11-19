package dev.phantom.hud.elements;

import dev.phantom.core.util.Animation;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import dev.phantom.integration.util.Notifications;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/** Toast stack. Grows upward when placed in the bottom half, downward otherwise. */
public final class NotificationsElement extends ScreenElement {
    private static final float W = 150, H = 26, GAP = 4;

    public NotificationsElement() {
        super("notifications", "Notifications", 1.0, 1.0, true);
        width = W;
        height = H;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        List<Notifications.Toast> toasts = client().notifications().active();
        width = W;
        if (toasts.isEmpty()) {
            height = H;
            if (editor) drawToast(ctx, 0, "Notifications", "Toasts appear here", Notifications.Kind.INFO, 1, 0.5f);
            return;
        }
        boolean bottom = screenY + screenH / 2 > mc.getWindow().getScaledHeight() / 2f;
        height = toasts.size() * (H + GAP) - GAP;
        for (int i = 0; i < toasts.size(); i++) {
            Notifications.Toast t = toasts.get(i);
            float life = t.life();
            // ease in over the first 8 %, out over the last 10 %
            float in = (float) Animation.easeOutCubic(Math.min(1, life / 0.08f));
            float out = life > 0.9f ? (float) Animation.easeOutCubic((1 - life) / 0.1f) : 1;
            float vis = Math.min(in, out);
            float y = bottom ? height - (i + 1) * (H + GAP) + GAP : i * (H + GAP);
            float slide = (1 - vis) * (W + 10) * (right ? 1 : -1);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(slide, 0, 0);
            drawToast(ctx, y, t.title, t.message, t.kind, vis, 1 - life);
            ctx.getMatrices().pop();
        }
    }

    private void drawToast(DrawContext ctx, float y, String title, String msg, Notifications.Kind kind, float alpha, float remaining) {
        var theme = client().theme();
        int accent = switch (kind) {
            case SUCCESS -> 0xFF66BB6A;
            case WARNING -> 0xFFFFA726;
            case ERROR -> 0xFFEF5350;
            case INFO -> theme.accent();
        };
        Render2D.roundedRect(ctx, 0, y, W, H, 5, Colors.fade(theme.panel(), alpha));
        Render2D.roundedRect(ctx, 4, y + 5, 2.5f, H - 10, 1.2f, Colors.fade(accent, alpha));
        Render2D.text(ctx, Render2D.trim(title, (int) W - 16), 11, y + 4, Colors.fade(theme.text(), alpha), false);
        Render2D.text(ctx, Render2D.trim(msg, (int) W - 16), 11, y + 14, Colors.fade(theme.textDim(), alpha), false);
        Render2D.rect(ctx, 6, y + H - 2, (W - 12) * Math.max(0, remaining), 1, Colors.fade(accent, alpha * 0.7f));
    }
}
