package dev.phantom.core.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Ordered list of unique, trimmed, lower-case identifiers (e.g. block ids for BlockESP). */
public final class StringListSetting extends Setting<List<String>> {
    public StringListSetting(String name, String description, List<String> defaults) {
        super(name, description, List.copyOf(defaults));
    }

    @Override
    protected List<String> sanitize(List<String> candidate) {
        if (candidate == null) return getDefault();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : candidate) {
            if (s == null) continue;
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) set.add(t);
        }
        return Collections.unmodifiableList(new ArrayList<>(set));
    }

    /** Parses a comma/space separated string as typed into the GUI. */
    public void setFromText(String text) {
        List<String> parts = new ArrayList<>();
        for (String p : text.split("[,\\s]+")) parts.add(p);
        set(parts);
    }

    public String asText() {
        return String.join(", ", value);
    }

    @Override
    public JsonElement toJson() {
        JsonArray arr = new JsonArray();
        for (String s : value) arr.add(s);
        return arr;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonArray()) return;
        List<String> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray()) if (e.isJsonPrimitive()) out.add(e.getAsString());
        set(out);
    }
}
