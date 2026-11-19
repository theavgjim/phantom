package dev.phantom.integration.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * GUI-space primitives with sub-pixel (float) geometry: rounded rectangles, circles and
 * horizontal gradients. Each call first flushes the DrawContext batch so draw order is preserved.
 */
public final class Render2D {
    private Render2D() {
    }

    /**
     * Flushes batched DrawContext content (mostly text) with depth testing off, so Phantom's 2D
     * layers draw in plain painter's order regardless of the depth vanilla HUD layers left behind.
     */
    public static void flush(DrawContext ctx) {
        RenderSystem.disableDepthTest();
        ctx.draw();
        RenderSystem.enableDepthTest();
    }

    private static BufferBuilder begin(DrawContext ctx, VertexFormat.DrawMode mode) {
        flush(ctx);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    private static void end(BufferBuilder b) {
        var built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void rect(DrawContext ctx, float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.QUADS);
        b.vertex(m, x, y, 0).color(color);
        b.vertex(m, x, y + h, 0).color(color);
        b.vertex(m, x + w, y + h, 0).color(color);
        b.vertex(m, x + w, y, 0).color(color);
        end(b);
    }

    public static void gradientH(DrawContext ctx, float x, float y, float w, float h, int left, int right) {
        if (w <= 0 || h <= 0) return;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.QUADS);
        b.vertex(m, x, y, 0).color(left);
        b.vertex(m, x, y + h, 0).color(left);
        b.vertex(m, x + w, y + h, 0).color(right);
        b.vertex(m, x + w, y, 0).color(right);
        end(b);
    }

    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int color) {
        roundedRect(ctx, x, y, w, h, r, color, color);
    }

    /** Rounded rectangle with a horizontal colour gradient. */
    public static void roundedRect(DrawContext ctx, float x, float y, float w, float h, float r, int left, int right) {
        if (w <= 0 || h <= 0 || (Colors.alpha(left) == 0 && Colors.alpha(right) == 0)) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        if (r < 0.5f) {
            gradientH(ctx, x, y, w, h, left, right);
            return;
        }
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, x + w / 2f, y + h / 2f, 0).color(Colors.lerp(left, right, 0.5f));
        int seg = Math.max(4, Math.min(16, (int) (r * 1.5f)));
        corner(b, m, x + w - r, y + r, r, -90, seg, x, w, left, right);
        corner(b, m, x + w - r, y + h - r, r, 0, seg, x, w, left, right);
        corner(b, m, x + r, y + h - r, r, 90, seg, x, w, left, right);
        corner(b, m, x + r, y + r, r, 180, seg, x, w, left, right);
        // close the fan
        float px = x + w - r, py = y;
        b.vertex(m, px, py, 0).color(Colors.lerp(left, right, (px - x) / w));
        end(b);
    }

    private static void corner(BufferBuilder b, Matrix4f m, float cx, float cy, float r, float startDeg, int seg,
                               float x, float w, int left, int right) {
        for (int i = 0; i <= seg; i++) {
            double a = Math.toRadians(startDeg + 90.0 * i / seg);
            float px = cx + (float) Math.cos(a) * r;
            float py = cy + (float) Math.sin(a) * r;
            b.vertex(m, px, py, 0).color(Colors.lerp(left, right, (px - x) / w));
        }
    }

    /** Outline drawn as a slightly larger rounded rect behind the fill; call before the fill. */
    public static void roundedOutline(DrawContext ctx, float x, float y, float w, float h, float r, float thickness, int color) {
        roundedRect(ctx, x - thickness, y - thickness, w + thickness * 2, h + thickness * 2, r + thickness, color);
    }

    public static void circle(DrawContext ctx, float cx, float cy, float radius, int color) {
        if (Colors.alpha(color) == 0) return;
        Matrix4f m = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder b = begin(ctx, VertexFormat.DrawMode.TRIANGLE_FAN);
        b.vertex(m, cx, cy, 0).color(color);
        int seg = Math.max(8, Math.min(32, (int) (radius * 3)));
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI * 2 * i / seg;
            b.vertex(m, cx + (float) Math.cos(a) * radius, cy + (float) Math.sin(a) * radius, 0).color(color);
        }
        end(b);
    }

    // ------------------------------------------------------------------ text

    public static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static int width(String s) {
        return font().getWidth(s);
    }

    /**
     * Draws text with the see-through text layer: no depth test, no depth write. Vanilla's normal
     * text layer depth-tests against whatever HUD layers (e.g. chat) drew earlier at a higher GUI z,
     * which would punch holes in Phantom's HUD text. Painter's order is what we want here.
     */
    public static void text(DrawContext ctx, String s, float x, float y, int color, boolean shadow) {
        // Vanilla treats near-zero alpha as opaque; skip instead to keep fades clean.
        if (Colors.alpha(color) < 8 || s.isEmpty()) return;
        font().draw(s, x, y, color, shadow, ctx.getMatrices().peek().getPositionMatrix(), ctx.getVertexConsumers(),
                TextRenderer.TextLayerType.SEE_THROUGH, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    public static void text(DrawContext ctx, String s, float x, float y, int color, boolean shadow, float scale) {
        if (scale == 1f) {
            text(ctx, s, x, y, color, shadow);
            return;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        text(ctx, s, 0, 0, color, shadow);
        ctx.getMatrices().pop();
    }

    public static void centeredText(DrawContext ctx, String s, float cx, float y, int color, boolean shadow) {
        text(ctx, s, cx - width(s) / 2f, y, color, shadow);
    }

    /** Truncates with an ellipsis to fit {@code maxWidth}. */
    public static String trim(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        String ell = "...";
        int w = maxWidth - width(ell);
        return font().trimToWidth(s, Math.max(0, w)) + ell;
    }
}
