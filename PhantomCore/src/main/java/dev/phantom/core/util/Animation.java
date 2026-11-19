package dev.phantom.core.util;

/**
 * Frame-rate independent exponential smoothing: the value approaches the target by
 * {@code 1 - e^(-speed * dt)} each update, so it looks identical at 30 or 300 FPS.
 */
public final class Animation {
    private double value;
    private double target;
    private double speed;

    public Animation(double initial, double speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public double update(double dtSeconds) {
        double k = 1 - Math.exp(-speed * Math.max(0, Math.min(dtSeconds, 0.25)));
        value += (target - value) * k;
        if (Math.abs(target - value) < 1e-4) value = target;
        return value;
    }

    public Animation target(double t) {
        this.target = t;
        return this;
    }

    public void snap(double v) {
        this.value = v;
        this.target = v;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double get() {
        return value;
    }

    public float getFloat() {
        return (float) value;
    }

    public double target() {
        return target;
    }

    public boolean settled() {
        return value == target;
    }

    public static double easeOutCubic(double t) {
        double c = Math.max(0, Math.min(1, t));
        return 1 - Math.pow(1 - c, 3);
    }
}
