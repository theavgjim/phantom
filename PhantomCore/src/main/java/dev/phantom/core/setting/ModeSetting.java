package dev.phantom.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;
import java.util.function.Supplier;

/** One-of-many choice, rendered as a dropdown. Options may be dynamic (e.g. theme names). */
public final class ModeSetting extends Setting<String> {
    private final Supplier<List<String>> options;

    public ModeSetting(String name, String description, String defaultValue, String... options) {
        this(name, description, defaultValue, () -> List.of(options));
    }

    public ModeSetting(String name, String description, String defaultValue, Supplier<List<String>> options) {
        super(name, description, defaultValue);
        this.options = options;
    }

    public List<String> getOptions() {
        return options.get();
    }

    public boolean is(String mode) {
        return value.equalsIgnoreCase(mode);
    }

    public void cycle(int direction) {
        List<String> opts = getOptions();
        int i = opts.indexOf(value);
        int n = opts.size();
        set(opts.get(((i + direction) % n + n) % n));
    }

    @Override
    protected String sanitize(String candidate) {
        if (candidate == null) return getDefault();
        for (String o : getOptions()) if (o.equalsIgnoreCase(candidate)) return o;
        return value == null ? getDefault() : value;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) set(json.getAsString());
    }
}
