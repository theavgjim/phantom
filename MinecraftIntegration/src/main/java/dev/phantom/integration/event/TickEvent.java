package dev.phantom.integration.event;

/** Posted at the start of every client tick (20 Hz) while a world is loaded. */
public final class TickEvent {
    public static final TickEvent INSTANCE = new TickEvent();

    private TickEvent() {
    }
}
