package dev.phantom.integration.util;

public final class Colors {
    private Colors() {
    }

    public static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** Multiplies the existing alpha by {@code factor} (0..1). */
    public static int fade(int argb, float factor) {
        return withAlpha(argb, Math.round(alpha(argb) * Math.max(0, Math.min(1, factor))));
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = alpha(a), ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = alpha(b), br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int brighten(int argb, float amount) {
        return lerp(argb, withAlpha(0xFFFFFF, alpha(argb)), amount);
    }

    public static int rainbow(float offset, float speed, float saturation) {
        float hue = ((System.currentTimeMillis() % 100000L) / 1000f * speed * 0.1f + offset) % 1f;
        return java.awt.Color.HSBtoRGB(hue, saturation, 1f) | 0xFF000000;
    }

    /** Green -> yellow -> red by fraction (1 = full health). */
    public static int health(float fraction) {
        fraction = Math.max(0, Math.min(1, fraction));
        return java.awt.Color.HSBtoRGB(fraction / 3f, 0.75f, 0.95f) | 0xFF000000;
    }
}
