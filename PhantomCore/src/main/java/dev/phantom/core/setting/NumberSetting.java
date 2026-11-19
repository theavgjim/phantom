package dev.phantom.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A bounded number shown as a slider. {@code step} controls rounding and display precision. */
public final class NumberSetting extends Setting<Double> {
    private final double min;
    private final double max;
    private final double step;
    private final String unit;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        this(name, description, defaultValue, min, max, step, "");
    }

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String unit) {
        super(name, description, defaultValue);
        if (min >= max) throw new IllegalArgumentException("min must be < max");
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit == null ? "" : unit;
        this.value = sanitize(defaultValue);
    }

    @Override
    protected Double sanitize(Double candidate) {
        if (candidate == null || candidate.isNaN()) return getDefault();
        double v = Math.max(min, Math.min(max, candidate));
        if (step > 0) {
            v = min + Math.round((v - min) / step) * step;
            v = Math.max(min, Math.min(max, v));
            // Kill floating point noise like 0.30000000000000004
            v = Math.round(v * 1e6) / 1e6;
        }
        return v;
    }

    public double getDouble() {
        return value;
    }

    public float getFloat() {
        return value.floatValue();
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }

    public double getStep() {
        return step;
    }

    /** 0..1 position of the current value on the slider. */
    public double fraction() {
        return (value - min) / (max - min);
    }

    public void setFraction(double f) {
        set(min + Math.max(0, Math.min(1, f)) * (max - min));
    }

    public String format() {
        String s;
        if (step >= 1) s = Long.toString(Math.round(value));
        else if (step >= 0.1) s = String.format(java.util.Locale.ROOT, "%.1f", value);
        else s = String.format(java.util.Locale.ROOT, "%.2f", value);
        return unit.isEmpty() ? s : s + unit;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) {
            set(json.getAsDouble());
        }
    }
}
