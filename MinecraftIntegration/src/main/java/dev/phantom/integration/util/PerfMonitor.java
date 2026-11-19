package dev.phantom.integration.util;

/** Exponential moving averages of the time Phantom itself spends per tick and per frame. */
public final class PerfMonitor {
    public enum Section { TICK, WORLD, HUD }

    private final double[] avgMs = new double[Section.values().length];
    private final double[] peakMs = new double[Section.values().length];
    private final long[] start = new long[Section.values().length];

    public void begin(Section s) {
        start[s.ordinal()] = System.nanoTime();
    }

    public void end(Section s) {
        double ms = (System.nanoTime() - start[s.ordinal()]) / 1e6;
        int i = s.ordinal();
        avgMs[i] = avgMs[i] == 0 ? ms : avgMs[i] * 0.95 + ms * 0.05;
        peakMs[i] = Math.max(ms, peakMs[i] * 0.995);
    }

    public double average(Section s) {
        return avgMs[s.ordinal()];
    }

    public double peak(Section s) {
        return peakMs[s.ordinal()];
    }

    /** Average Phantom cost per rendered frame (world + HUD) in ms. */
    public double frameCost() {
        return average(Section.WORLD) + average(Section.HUD);
    }
}
