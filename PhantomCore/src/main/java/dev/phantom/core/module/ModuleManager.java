package dev.phantom.core.module;

import dev.phantom.core.event.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry of all modules plus change notifications for config/GUI/HUD. */
public final class ModuleManager {
    private final EventBus bus;
    private final Map<String, Module> byName = new LinkedHashMap<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile ActivationGuard guard = ActivationGuard.ALLOW_ALL;

    public ModuleManager(EventBus bus) {
        this.bus = bus;
        for (Category c : Category.values()) byCategory.put(c, new ArrayList<>());
    }

    public <M extends Module> M register(M module) {
        String key = module.getName().toLowerCase(Locale.ROOT);
        if (byName.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate module: " + module.getName());
        }
        byName.put(key, module);
        byCategory.get(module.getCategory()).add(module);
        module.bind(this);
        return module;
    }

    public Module get(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    public <M extends Module> M get(Class<M> type) {
        for (Module m : byName.values()) if (type.isInstance(m)) return (M) m;
        return null;
    }

    public List<Module> all() {
        return Collections.unmodifiableList(new ArrayList<>(byName.values()));
    }

    public List<Module> byCategory(Category category) {
        return Collections.unmodifiableList(byCategory.get(category));
    }

    public List<Module> enabled() {
        List<Module> out = new ArrayList<>();
        for (Module m : byName.values()) if (m.isEnabled()) out.add(m);
        return out;
    }

    /** Case-insensitive search across module names, descriptions and categories. */
    public List<Module> search(String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all();
        List<Module> out = new ArrayList<>();
        for (Module m : byName.values()) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)
                    || m.getDescription().toLowerCase(Locale.ROOT).contains(q)
                    || m.getCategory().displayName().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(m);
            }
        }
        return out;
    }

    /** Disables every module; used on shutdown so modules can restore game state. */
    public void disableAll() {
        for (Module m : byName.values()) if (m.isEnabled()) m.setEnabled(false);
    }

    public EventBus bus() {
        return bus;
    }

    public ActivationGuard guard() {
        return guard;
    }

    public void setGuard(ActivationGuard guard) {
        this.guard = guard == null ? ActivationGuard.ALLOW_ALL : guard;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    void fireToggled(Module m) {
        for (Listener l : listeners) l.onToggled(m);
    }

    void fireChanged(Module m) {
        for (Listener l : listeners) l.onChanged(m);
    }

    void fireRefused(Module m, String reason) {
        for (Listener l : listeners) l.onRefused(m, reason);
    }

    void fireError(Module m, RuntimeException e) {
        for (Listener l : listeners) l.onError(m, e);
    }

    public interface Listener {
        default void onToggled(Module module) {
        }

        /** Any persisted state changed (enabled flag, keybind or a setting). */
        default void onChanged(Module module) {
        }

        default void onRefused(Module module, String reason) {
        }

        default void onError(Module module, RuntimeException error) {
        }
    }
}
