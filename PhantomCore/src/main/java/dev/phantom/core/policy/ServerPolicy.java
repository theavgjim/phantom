package dev.phantom.core.policy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a server permits. Sent by the server on the {@code phantom:policy} plugin channel as JSON:
 *
 * <pre>{"version":1,"allow":["ESP","Fly"],"deny":["Zoom"],"message":"Build server rules"}</pre>
 *
 * <ul>
 *   <li>{@code allow}: restricted modules the server explicitly permits ({@code "*"} = all).</li>
 *   <li>{@code deny}: any module (even cosmetic ones) the server forbids. Deny always wins.</li>
 * </ul>
 * A server that sends nothing gets {@link #DEFAULT}: cosmetic modules only.
 */
public record ServerPolicy(Set<String> allow, Set<String> deny, String message, String source) {
    public static final int MAX_JSON_LENGTH = 16_384;
    private static final int MAX_ENTRIES = 128;

    public static final ServerPolicy DEFAULT = new ServerPolicy(Set.of(), Set.of(), "", "default");

    public ServerPolicy {
        allow = Collections.unmodifiableSet(normalize(allow));
        deny = Collections.unmodifiableSet(normalize(deny));
        message = message == null ? "" : message.length() > 200 ? message.substring(0, 200) : message;
        source = source == null ? "unknown" : source;
    }

    public boolean allows(String module) {
        String k = key(module);
        return allow.contains("*") || allow.contains(k);
    }

    public boolean denies(String module) {
        String k = key(module);
        return deny.contains("*") || deny.contains(k);
    }

    public boolean isDefault() {
        return this == DEFAULT || (allow.isEmpty() && deny.isEmpty() && "default".equals(source));
    }

    /** Parses a server payload. Malformed or oversized input throws, and the caller keeps the default policy. */
    public static ServerPolicy parse(String json, String source) {
        if (json == null || json.length() > MAX_JSON_LENGTH) throw new IllegalArgumentException("Policy payload too large");
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Policy must be a JSON object");
        JsonObject o = root.getAsJsonObject();
        return new ServerPolicy(list(o, "allow"), list(o, "deny"),
                o.has("message") && o.get("message").isJsonPrimitive() ? o.get("message").getAsString() : "", source);
    }

    private static Set<String> list(JsonObject o, String key) {
        Set<String> out = new LinkedHashSet<>();
        JsonElement e = o.get(key);
        if (e == null) return out;
        if (!e.isJsonArray()) throw new IllegalArgumentException("'" + key + "' must be an array");
        JsonArray arr = e.getAsJsonArray();
        if (arr.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many entries in '" + key + "'");
        for (JsonElement v : arr) if (v.isJsonPrimitive()) out.add(v.getAsString());
        return out;
    }

    private static Set<String> normalize(Set<String> in) {
        Set<String> out = new LinkedHashSet<>();
        if (in != null) for (String s : in) if (s != null && !s.isBlank()) out.add(key(s));
        return out;
    }

    /** Module names are matched case-insensitively, ignoring spaces ("ESP Colors" == "espcolors"). */
    public static String key(String name) {
        return name.replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
