package dev.phantom.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

/** ARGB colour, serialised as {@code "#AARRGGBB"}. */
public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, String description, int argb) {
        super(name, description, argb);
    }

    public int argb() {
        return value;
    }

    public float red() {
        return ((value >> 16) & 0xFF) / 255f;
    }

    public float green() {
        return ((value >> 8) & 0xFF) / 255f;
    }

    public float blue() {
        return (value & 0xFF) / 255f;
    }

    public float alpha() {
        return ((value >>> 24) & 0xFF) / 255f;
    }

    /** Returns {hue, saturation, brightness} each 0..1. */
    public float[] hsb() {
        return rgbToHsb((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF);
    }

    public void setHsba(float h, float s, float b, float a) {
        int rgb = hsbToRgb(h, s, b);
        int alpha = Math.round(Math.max(0, Math.min(1, a)) * 255);
        set((alpha << 24) | (rgb & 0xFFFFFF));
    }

    public static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0 -> { r = (int) (brightness * 255 + .5f); g = (int) (t * 255 + .5f); b = (int) (p * 255 + .5f); }
                case 1 -> { r = (int) (q * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (p * 255 + .5f); }
                case 2 -> { r = (int) (p * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (t * 255 + .5f); }
                case 3 -> { r = (int) (p * 255 + .5f); g = (int) (q * 255 + .5f); b = (int) (brightness * 255 + .5f); }
                case 4 -> { r = (int) (t * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (brightness * 255 + .5f); }
                default -> { r = (int) (brightness * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (q * 255 + .5f); }
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static float[] rgbToHsb(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        float brightness = max / 255f;
        float saturation = max == 0 ? 0 : (float) (max - min) / max;
        float hue;
        if (saturation == 0) {
            hue = 0;
        } else {
            float rc = (float) (max - r) / (max - min);
            float gc = (float) (max - g) / (max - min);
            float bc = (float) (max - b) / (max - min);
            if (r == max) hue = bc - gc;
            else if (g == max) hue = 2.0f + rc - bc;
            else hue = 4.0f + gc - rc;
            hue /= 6.0f;
            if (hue < 0) hue += 1.0f;
        }
        return new float[]{hue, saturation, brightness};
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", value));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return;
        if (json.getAsJsonPrimitive().isNumber()) {
            set(json.getAsInt());
            return;
        }
        String s = json.getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            long v = Long.parseLong(s, 16);
            if (s.length() == 6) v |= 0xFF000000L;
            set((int) v);
        } catch (NumberFormatException ignored) {
            // keep current value
        }
    }
}
