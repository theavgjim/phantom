package dev.phantom.core.ipc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Client -> launcher channel. The client periodically writes {@code status.json} into its
 * config directory; the launcher polls it to show "client running", the active profile, etc.
 * File based so there are no open ports and no background sockets.
 */
public final class StatusWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public StatusWriter(Path configRoot) {
        this.file = configRoot.resolve("status.json");
    }

    public void write(Status status) {
        JsonObject o = new JsonObject();
        o.addProperty("running", status.running());
        o.addProperty("pid", ProcessHandle.current().pid());
        o.addProperty("clientVersion", status.clientVersion());
        o.addProperty("minecraftVersion", status.minecraftVersion());
        o.addProperty("profile", status.profile());
        o.addProperty("context", status.context());
        o.addProperty("fps", status.fps());
        o.addProperty("timestamp", System.currentTimeMillis());
        JsonArray mods = new JsonArray();
        for (String m : status.enabledModules()) mods.add(m);
        o.add("enabledModules", mods);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("status.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Status is best effort; never disturb the game for it.
        }
    }

    public Path file() {
        return file;
    }

    public record Status(boolean running, String clientVersion, String minecraftVersion, String profile,
                         String context, int fps, List<String> enabledModules) {
    }
}
