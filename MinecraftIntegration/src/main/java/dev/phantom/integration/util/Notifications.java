package dev.phantom.integration.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Toast queue consumed by the HUD notifications element. */
public final class Notifications {
    public enum Kind { INFO, SUCCESS, WARNING, ERROR }

    public static final class Toast {
        public final String title;
        public final String message;
        public final Kind kind;
        public final long created = System.currentTimeMillis();
        public final long durationMs;
        public float slide;

        Toast(String title, String message, Kind kind, long durationMs) {
            this.title = title;
            this.message = message;
            this.kind = kind;
            this.durationMs = durationMs;
        }

        public float life() {
            return (System.currentTimeMillis() - created) / (float) durationMs;
        }
    }

    private static final int MAX = 6;
    private final Deque<Toast> toasts = new ArrayDeque<>();
    private long defaultDurationMs = 2500;

    public synchronized void push(String title, String message, Kind kind) {
        // Collapse duplicates (e.g. repeated refusals) instead of stacking them.
        toasts.removeIf(t -> t.title.equals(title) && t.message.equals(message));
        toasts.addFirst(new Toast(title, message, kind, kind == Kind.WARNING || kind == Kind.ERROR ? defaultDurationMs * 2 : defaultDurationMs));
        while (toasts.size() > MAX) toasts.removeLast();
    }

    public synchronized List<Toast> active() {
        toasts.removeIf(t -> t.life() >= 1f);
        return new ArrayList<>(toasts);
    }

    public void setDefaultDuration(long ms) {
        this.defaultDurationMs = ms;
    }
}
