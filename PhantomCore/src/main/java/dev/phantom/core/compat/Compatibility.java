package dev.phantom.core.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phantom.core.util.Version;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parsed form of {@code compatibility.json} - the single source of truth shared by the mod and
 * the Windows launcher.
 */
public final class Compatibility {
    private final String clientVersion;
    private final List<Entry> entries;

    private Compatibility(String clientVersion, List<Entry> entries) {
        this.clientVersion = clientVersion;
        this.entries = entries;
    }

    public static Compatibility load(InputStream in) throws IOException {
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(r).getAsJsonObject());
        } catch (RuntimeException e) {
            throw new IOException("Invalid compatibility.json", e);
        }
    }

    public static Compatibility parse(JsonObject root) {
        List<Entry> list = new ArrayList<>();
        for (JsonElement e : root.getAsJsonArray("supported")) {
            JsonObject o = e.getAsJsonObject();
            list.add(new Entry(
                    o.get("minecraft").getAsString(),
                    o.get("fabricLoader").getAsString(),
                    o.get("java").getAsInt(),
                    o.has("status") ? o.get("status").getAsString() : "supported"));
        }
        return new Compatibility(root.get("clientVersion").getAsString(), List.copyOf(list));
    }

    public String clientVersion() {
        return clientVersion;
    }

    public List<Entry> entries() {
        return entries;
    }

    public Entry find(String minecraftVersion) {
        for (Entry e : entries) if (e.minecraft().equals(minecraftVersion)) return e;
        return null;
    }

    public Result check(String minecraftVersion, String loaderVersion, int javaMajor) {
        Entry e = find(minecraftVersion);
        if (e == null) return new Result(false, "Minecraft " + minecraftVersion + " is not supported");
        if (loaderVersion != null && !Version.parse(loaderVersion).atLeast(Version.parse(e.fabricLoader()))) {
            return new Result(false, "Fabric Loader " + e.fabricLoader() + "+ required (found " + loaderVersion + ")");
        }
        if (javaMajor > 0 && javaMajor < e.java()) {
            return new Result(false, "Java " + e.java() + "+ required (found " + javaMajor + ")");
        }
        return new Result(true, "Minecraft " + minecraftVersion + " is " + e.status());
    }

    public record Entry(String minecraft, String fabricLoader, int java, String status) {
    }

    public record Result(boolean compatible, String message) {
    }
}
