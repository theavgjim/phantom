package dev.phantom.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.phantom.core.hud.HudElement;
import dev.phantom.core.hud.HudRegistry;
import dev.phantom.core.module.Module;
import dev.phantom.core.module.ModuleManager;
import dev.phantom.core.setting.Setting;

import java.time.Instant;

/** Converts live module / HUD state to and from the profile JSON schema (see Documentation). */
public final class ConfigSerializer {
    public static final int SCHEMA = 1;

    private ConfigSerializer() {
    }

    public static JsonObject snapshot(ModuleManager modules, HudRegistry hud) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("savedAt", Instant.now().toString());

        JsonObject mods = new JsonObject();
        for (Module m : modules.all()) {
            JsonObject o = new JsonObject();
            if (m.isToggleable()) o.addProperty("enabled", m.isEnabled());
            o.addProperty("key", m.getKey());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : m.getSettings()) settings.add(s.getName(), s.toJson());
            o.add("settings", settings);
            mods.add(m.getName(), o);
        }
        root.add("modules", mods);

        JsonObject hudObj = new JsonObject();
        for (HudElement e : hud.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("x", e.x());
            o.addProperty("y", e.y());
            JsonObject settings = new JsonObject();
            for (Setting<?> s : e.settings()) settings.add(s.getName(), s.toJson());
            o.add("settings", settings);
            hudObj.add(e.id(), o);
        }
        root.add("hud", hudObj);
        return root;
    }

    /**
     * Resets everything to defaults, then overlays the given profile. Unknown keys are ignored and
     * malformed values keep their defaults, so old or hand-edited profiles never crash the client.
     */
    public static void apply(JsonObject root, ModuleManager modules, HudRegistry hud) {
        for (Module m : modules.all()) m.resetToDefaults();
        for (HudElement e : hud.all()) {
            e.resetPosition();
            for (Setting<?> s : e.settings()) s.reset();
        }
        if (root == null) return;

        JsonObject mods = object(root, "modules");
        if (mods != null) {
            for (Module m : modules.all()) {
                JsonObject o = object(mods, m.getName());
                if (o == null) continue;
                JsonObject settings = object(o, "settings");
                if (settings != null) {
                    for (Setting<?> s : m.getSettings()) s.fromJson(settings.get(s.getName()));
                }
                JsonElement key = o.get("key");
                if (key != null && key.isJsonPrimitive() && key.getAsJsonPrimitive().isNumber()) {
                    m.setKey(key.getAsInt());
                }
                JsonElement en = o.get("enabled");
                if (en != null && en.isJsonPrimitive() && en.getAsJsonPrimitive().isBoolean()) {
                    m.restoreEnabled(en.getAsBoolean());
                }
            }
        }

        JsonObject hudObj = object(root, "hud");
        if (hudObj != null) {
            for (HudElement e : hud.all()) {
                JsonObject o = object(hudObj, e.id());
                if (o == null) continue;
                JsonObject settings = object(o, "settings");
                if (settings != null) {
                    for (Setting<?> s : e.settings()) s.fromJson(settings.get(s.getName()));
                }
                double x = number(o, "x", e.x());
                double y = number(o, "y", e.y());
                e.setPosition(x, y);
            }
        }
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static double number(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }
}
