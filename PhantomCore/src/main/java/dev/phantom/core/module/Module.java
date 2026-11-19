package dev.phantom.core.module;

import dev.phantom.core.event.EventBus;
import dev.phantom.core.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base class for every feature.
 *
 * <p>Modules declare their settings in the constructor and register event handlers with
 * {@link #on(Class, Consumer)}. Handlers are attached to the {@link EventBus} only while the
 * module is enabled.</p>
 */
public abstract class Module {
    public static final int KEY_NONE = -1;

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final List<Handler<?>> handlers = new ArrayList<>();
    private final List<EventBus.Subscription> active = new ArrayList<>();

    private boolean enabled;
    private boolean defaultEnabled;
    private int key = KEY_NONE;
    private int defaultKey = KEY_NONE;
    private boolean privateWorldOnly;
    private boolean toggleable = true;
    private boolean holdToActivate;
    private boolean hiddenFromList;
    private ModuleManager manager;

    protected Module(String name, String description, Category category) {
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.category = Objects.requireNonNull(category);
    }

    // ------------------------------------------------------------------ declaration helpers

    protected final <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        setting.setChangeListener(s -> {
            onSettingChanged(s);
            if (manager != null) manager.fireChanged(this);
        });
        return setting;
    }

    protected final <T> void on(Class<T> type, Consumer<? super T> handler) {
        handlers.add(new Handler<>(type, handler));
    }

    /** Restricts the module to private worlds (singleplayer / own LAN host). */
    protected final void privateWorldOnly() {
        this.privateWorldOnly = true;
    }

    /** Settings-only or action modules (e.g. ClickGUI) that are never "on". */
    protected final void notToggleable() {
        this.toggleable = false;
    }

    /** Keybind enables while held and disables on release (e.g. Zoom). */
    protected final void holdToActivate() {
        this.holdToActivate = true;
    }

    protected final void hideFromList() {
        this.hiddenFromList = true;
    }

    protected final void defaultKey(int key) {
        this.key = key;
        this.defaultKey = key;
    }

    protected final void enabledByDefault() {
        this.enabled = true;
        this.defaultEnabled = true;
    }

    /** Restores settings, keybind and enabled state to their declared defaults. */
    public final void resetToDefaults() {
        for (Setting<?> s : settings) s.reset();
        key = defaultKey;
        restoreEnabled(defaultEnabled);
    }

    // ------------------------------------------------------------------ lifecycle hooks

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    /** Called for non-toggleable modules when their keybind is pressed. */
    public void onActivate() {
    }

    protected void onSettingChanged(Setting<?> setting) {
    }

    /** Short status text shown next to the module name in the HUD module list. */
    public String getSuffix() {
        return null;
    }

    // ------------------------------------------------------------------ state

    public final boolean isEnabled() {
        return enabled;
    }

    /**
     * Requests a state change. Enabling may be refused by the manager's {@link ActivationGuard}.
     *
     * @return {@code null} on success, otherwise the reason the change was refused.
     */
    public final String setEnabled(boolean value) {
        if (!toggleable || value == enabled) return null;
        if (value && manager != null) {
            String reason = manager.guard().deny(this);
            if (reason != null) {
                manager.fireRefused(this, reason);
                return reason;
            }
        }
        enabled = value;
        if (value) {
            attach();
            safely(this::onEnable);
        } else {
            detach();
            safely(this::onDisable);
        }
        if (manager != null) {
            manager.fireToggled(this);
            manager.fireChanged(this);
        }
        return null;
    }

    public final String toggle() {
        return setEnabled(!enabled);
    }

    /** Sets state from a configuration file. Guard refusals are silent here. */
    public final void restoreEnabled(boolean value) {
        if (!toggleable || value == enabled) return;
        if (value && manager != null && manager.guard().deny(this) != null) return;
        enabled = value;
        if (value) {
            attach();
            safely(this::onEnable);
        } else {
            detach();
            safely(this::onDisable);
        }
        if (manager != null) manager.fireToggled(this);
    }

    private void attach() {
        if (manager == null) return;
        for (Handler<?> h : handlers) active.add(h.subscribe(manager.bus()));
    }

    private void detach() {
        for (EventBus.Subscription s : active) s.unsubscribe();
        active.clear();
    }

    private void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            if (manager != null) manager.fireError(this, e);
            else throw e;
        }
    }

    void bind(ModuleManager manager) {
        this.manager = manager;
        if (enabled) attach();
    }

    // ------------------------------------------------------------------ accessors

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public final Category getCategory() {
        return category;
    }

    public final List<Setting<?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    public final Setting<?> getSetting(String name) {
        for (Setting<?> s : settings) if (s.getName().equalsIgnoreCase(name)) return s;
        return null;
    }

    public final int getKey() {
        return key;
    }

    public final void setKey(int key) {
        if (this.key == key) return;
        this.key = key;
        if (manager != null) manager.fireChanged(this);
    }

    public final boolean isPrivateWorldOnly() {
        return privateWorldOnly;
    }

    public final boolean isToggleable() {
        return toggleable;
    }

    public final boolean isHoldToActivate() {
        return holdToActivate;
    }

    public final boolean isHiddenFromList() {
        return hiddenFromList;
    }

    @Override
    public String toString() {
        return name;
    }

    private record Handler<T>(Class<T> type, Consumer<? super T> consumer) {
        EventBus.Subscription subscribe(EventBus bus) {
            return bus.subscribe(type, consumer);
        }
    }
}
