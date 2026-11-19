package dev.phantom.core.event;

/** Base class for events whose default behaviour a listener may cancel. */
public abstract class Cancellable {
    private boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
    }

    protected void reset() {
        this.cancelled = false;
    }
}
