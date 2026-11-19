package dev.phantom.core.setting;

import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A typed, JSON-serialisable, observable module option. */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    private final T defaultValue;
    protected T value;
    private BooleanSupplier visibility = () -> true;
    private Consumer<Setting<?>> changeListener = s -> {
    };

    protected Setting(String name, String description, T defaultValue) {
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public T get() {
        return value;
    }

    public final T getDefault() {
        return defaultValue;
    }

    public final void set(T newValue) {
        T sanitized = sanitize(newValue);
        if (Objects.equals(sanitized, value)) return;
        value = sanitized;
        changeListener.accept(this);
    }

    public final void reset() {
        set(defaultValue);
    }

    /** Clamp / validate a candidate value. */
    protected T sanitize(T candidate) {
        return candidate == null ? defaultValue : candidate;
    }

    public abstract JsonElement toJson();

    /** Loads a value; malformed input falls back to the current value. */
    public abstract void fromJson(JsonElement json);

    /** Show this setting in the GUI only when the condition holds. */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return (S) this;
    }

    public boolean isVisible() {
        return visibility.getAsBoolean();
    }

    public void setChangeListener(Consumer<Setting<?>> listener) {
        this.changeListener = listener == null ? s -> {
        } : listener;
    }
}
