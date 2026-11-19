package dev.phantom.integration.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Batched world-space shape renderer. Every module adds shapes during {@code Render3DEvent};
 * all lines and all fills are then drawn with exactly two draw calls per frame, see-through,
 * using the vanilla position-colour shader. Vertices are camera relative to keep float precision.
 */
public final class Render3D implements AutoCloseable {
    private final BufferAllocator lineAlloc = new BufferAllocator(1 << 18);
    private final BufferAllocator quadAlloc = new BufferAllocator(1 << 18);
    private BufferBuilder lines;
    private BufferBuilder quads;
    private double cx, cy, cz;
    private final Matrix4f view = new Matrix4f();
    private float lineWidth = 1.5f;
    private boolean closed;

    public void begin(Vec3d camera, Matrix4f viewRotation) {
        this.cx = camera.x;
        this.cy = camera.y;
        this.cz = camera.z;
        this.view.set(viewRotation);
        this.lines = null;
        this.quads = null;
    }

    public void setLineWidth(float w) {
        this.lineWidth = Math.max(1f, Math.min(5f, w));
    }

    private BufferBuilder lines() {
        if (lines == null) lines = new BufferBuilder(lineAlloc, VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        return lines;
    }

    private BufferBuilder quads() {
        if (quads == null) quads = new BufferBuilder(quadAlloc, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        return quads;
    }

    // ------------------------------------------------------------------ shapes (world coordinates)

    public void line(double x1, double y1, double z1, double x2, double y2, double z2, int color) {
        BufferBuilder b = lines();
        b.vertex((float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).color(color);
        b.vertex((float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).color(color);
    }

    /** Line whose start is given relative to the camera (e.g. a point just in front of the crosshair). */
    public void lineFromCamera(Vec3d startRelative, double x2, double y2, double z2, int startColor, int endColor) {
        BufferBuilder b = lines();
        b.vertex((float) startRelative.x, (float) startRelative.y, (float) startRelative.z).color(startColor);
        b.vertex((float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).color(endColor);
    }

    public void box(Box box, int color) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ, x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        line(x1, y1, z1, x2, y1, z1, color);
        line(x2, y1, z1, x2, y1, z2, color);
        line(x2, y1, z2, x1, y1, z2, color);
        line(x1, y1, z2, x1, y1, z1, color);
        line(x1, y2, z1, x2, y2, z1, color);
        line(x2, y2, z1, x2, y2, z2, color);
        line(x2, y2, z2, x1, y2, z2, color);
        line(x1, y2, z2, x1, y2, z1, color);
        line(x1, y1, z1, x1, y2, z1, color);
        line(x2, y1, z1, x2, y2, z1, color);
        line(x2, y1, z2, x2, y2, z2, color);
        line(x1, y1, z2, x1, y2, z2, color);
    }

    public void filledBox(Box box, int color) {
        BufferBuilder b = quads();
        float x1 = (float) (box.minX - cx), y1 = (float) (box.minY - cy), z1 = (float) (box.minZ - cz);
        float x2 = (float) (box.maxX - cx), y2 = (float) (box.maxY - cy), z2 = (float) (box.maxZ - cz);
        // bottom, top, north, south, west, east
        quad(b, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, color);
        quad(b, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, color);
        quad(b, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, color);
        quad(b, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, color);
        quad(b, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, color);
        quad(b, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, color);
    }

    private static void quad(BufferBuilder b, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        b.vertex(ax, ay, az).color(color);
        b.vertex(bx, by, bz).color(color);
        b.vertex(cx, cy, cz).color(color);
        b.vertex(dx, dy, dz).color(color);
    }

    /** Horizontal circle (e.g. reach radius) at height {@code y}. */
    public void circle(double x, double y, double z, double radius, int segments, int color) {
        double step = Math.PI * 2 / segments;
        for (int i = 0; i < segments; i++) {
            double a0 = i * step, a1 = (i + 1) * step;
            line(x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius,
                    x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius, color);
        }
    }

    // ------------------------------------------------------------------ flush

    public void flush() {
        if (closed || (lines == null && quads == null)) return;
        BuiltBuffer quadBuf = quads != null ? quads.endNullable() : null;
        BuiltBuffer lineBuf = lines != null ? lines.endNullable() : null;
        lines = null;
        quads = null;
        if (quadBuf == null && lineBuf == null) return;

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.set(view);
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        try {
            if (quadBuf != null) BufferRenderer.drawWithGlobalProgram(quadBuf);
            if (lineBuf != null) {
                RenderSystem.lineWidth(lineWidth);
                BufferRenderer.drawWithGlobalProgram(lineBuf);
            }
        } finally {
            // Always hand the GL state back exactly as vanilla expects it.
            RenderSystem.lineWidth(1f);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            mv.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        lineAlloc.close();
        quadAlloc.close();
    }
}
