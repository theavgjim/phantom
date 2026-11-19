package dev.phantom.integration.event;

/** Posted when the client world instance changes: join, leave, dimension change or proxy server switch. */
public final class WorldChangeEvent {
    public static final WorldChangeEvent INSTANCE = new WorldChangeEvent();

    private WorldChangeEvent() {
    }
}
