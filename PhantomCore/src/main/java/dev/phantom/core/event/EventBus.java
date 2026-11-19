package dev.phantom.core.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal, allocation-free (on post) event bus.
 *
 * <p>Dispatch is by exact event class. Modules only subscribe while they are enabled, so a
 * disabled module costs nothing per frame/tick. Listener lists are copy-on-write: subscribing is
 * rare, posting is hot.</p>
 */
public final class EventBus {
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> Subscription subscribe(Class<T> type, Consumer<? super T> listener) {
        Consumer<Object> raw = (Consumer<Object>) listener;
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(raw);
        return () -> {
            List<Consumer<Object>> list = listeners.get(type);
            if (list != null) list.remove(raw);
        };
    }

    /** Posts an event to every listener of its exact class. Returns the event for chaining. */
    public <T> T post(T event) {
        List<Consumer<Object>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) return event;
        for (Consumer<Object> l : list) {
            l.accept(event);
        }
        return event;
    }

    public boolean hasListeners(Class<?> type) {
        List<Consumer<Object>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    public int listenerCount() {
        int n = 0;
        for (List<Consumer<Object>> l : listeners.values()) n += l.size();
        return n;
    }

    @FunctionalInterface
    public interface Subscription {
        void unsubscribe();
    }
}
