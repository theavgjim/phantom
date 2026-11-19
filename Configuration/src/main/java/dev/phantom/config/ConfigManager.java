package dev.phantom.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phantom.core.hud.HudRegistry;
import dev.phantom.core.module.ModuleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JSON profile storage.
 *
 * <pre>
 * &lt;root&gt;/phantom.json         meta: active profile, schema
 * &lt;root&gt;/launcher.json        one-shot request written by PhantomClient.exe (profile to load)
 * &lt;root&gt;/profiles/*.json      one file per profile
 * &lt;root&gt;/backups/             rolling backups
 * &lt;root&gt;/exports/  import/    import / export drop folders
 * </pre>
 *
 * <p>Threading: all disk IO happens on a single daemon thread. Reading live module state
 * (snapshot) and applying a profile always happen on the game thread via {@code mainThread}.</p>
 */
public final class ConfigManager implements AutoCloseable {
    public static final String DEFAULT_PROFILE = "default";
    private static final Logger LOG = LoggerFactory.getLogger("Phantom/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final long AUTOSAVE_PERIOD_MS = 2000;

    private final Path root;
    private final Path profilesDir;
    private final Path exportsDir;
    private final Path importDir;
    private final Path metaFile;
    private final Path launcherFile;
    private final BackupManager backups;
    private final ModuleManager modules;
    private final HudRegistry hud;
    private final Executor mainThread;
    private final ScheduledExecutorService io;
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final List<Listener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private volatile String activeProfile = DEFAULT_PROFILE;
    private volatile boolean applying;
    private volatile boolean loaded;
    private volatile boolean closed;

    public ConfigManager(Path root, ModuleManager modules, HudRegistry hud, Executor mainThread) {
        this.root = root;
        this.profilesDir = root.resolve("profiles");
        this.exportsDir = root.resolve("exports");
        this.importDir = root.resolve("import");
        this.metaFile = root.resolve("phantom.json");
        this.launcherFile = root.resolve("launcher.json");
        this.backups = new BackupManager(root.resolve("backups"));
        this.modules = modules;
        this.hud = hud;
        this.mainThread = mainThread;
        this.io = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Phantom-Config");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
    }

    // ------------------------------------------------------------------ startup

    /**
     * Reads the active profile off-thread, applies it on the game thread, then starts autosave.
     * The game keeps initialising while the file is read.
     */
    public CompletableFuture<Void> initAsync() {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        Files.createDirectories(profilesDir);
                        Files.createDirectories(exportsDir);
                        Files.createDirectories(importDir);
                        activeProfile = resolveStartupProfile();
                        return readProfile(activeProfile);
                    } catch (IOException e) {
                        LOG.error("Failed to prepare config directory {}", root, e);
                        return null;
                    }
                }, io)
                .thenAcceptAsync(json -> {
                    applyNow(json);
                    loaded = true;
                    LOG.info("Loaded profile '{}'", activeProfile);
                    for (Listener l : listeners) l.onProfileLoaded(activeProfile);
                }, mainThread)
                .thenRun(() -> {
                    writeMetaQuietly();
                    io.scheduleWithFixedDelay(this::autosaveTick, AUTOSAVE_PERIOD_MS, AUTOSAVE_PERIOD_MS, TimeUnit.MILLISECONDS);
                });
    }

    private String resolveStartupProfile() {
        // 1. A one-shot request from the launcher's configuration selector wins.
        JsonObject req = readJson(launcherFile);
        if (req != null && req.has("activeProfile")) {
            String p = req.get("activeProfile").getAsString();
            try {
                Files.deleteIfExists(launcherFile);
            } catch (IOException ignored) {
            }
            if (ProfileNames.isValid(p)) {
                LOG.info("Launcher selected profile '{}'", p);
                return p;
            }
        }
        // 2. Last used profile.
        JsonObject meta = readJson(metaFile);
        if (meta != null && meta.has("activeProfile")) {
            String p = meta.get("activeProfile").getAsString();
            if (ProfileNames.isValid(p)) return p;
        }
        return DEFAULT_PROFILE;
    }

    // ------------------------------------------------------------------ saving

    /** Called whenever persisted state changes; the autosave thread picks it up. */
    public void markDirty() {
        if (!applying && loaded) dirty.set(true);
    }

    public boolean isDirty() {
        return dirty.get();
    }

    private void autosaveTick() {
        if (closed || !dirty.getAndSet(false)) return;
        String profile = activeProfile;
        CompletableFuture.supplyAsync(() -> ConfigSerializer.snapshot(modules, hud), mainThread)
                .thenAcceptAsync(json -> write(profile, json), io)
                .exceptionally(t -> {
                    LOG.error("Autosave failed", t);
                    dirty.set(true);
                    return null;
                });
    }

    /** Synchronous save. Must be called on the game thread. */
    public void saveNow() {
        if (!loaded) return;
        dirty.set(false);
        write(activeProfile, ConfigSerializer.snapshot(modules, hud));
    }

    private synchronized void write(String profile, JsonObject json) {
        Path file = profileFile(profile);
        try {
            Files.createDirectories(profilesDir);
            backups.maybeBackup(profile, file);
            atomicWrite(file, GSON.toJson(json));
            for (Listener l : listeners) l.onSaved(profile);
        } catch (IOException e) {
            LOG.error("Could not save profile '{}'", profile, e);
            for (Listener l : listeners) l.onError("Could not save profile: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ profiles

    public String activeProfile() {
        return activeProfile;
    }

    public List<String> listProfiles() {
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(profilesDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(profilesDir, "*.json")) {
                for (Path p : ds) {
                    String n = p.getFileName().toString();
                    out.add(n.substring(0, n.length() - 5));
                }
            } catch (IOException e) {
                LOG.warn("Could not list profiles", e);
            }
        }
        if (!out.contains(activeProfile)) out.add(activeProfile);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Game thread. Saves the current profile, then loads {@code name} (created from defaults if new). */
    public void switchProfile(String name) {
        requireValid(name);
        saveNow();
        activeProfile = name;
        applyNow(readProfile(name));
        saveNow();
        writeMetaQuietly();
        for (Listener l : listeners) l.onProfileLoaded(name);
    }

    /** Game thread. Creates a profile from the current state ({@code copyCurrent}) or defaults. */
    public void createProfile(String name, boolean copyCurrent) {
        requireValid(name);
        if (Files.exists(profileFile(name))) throw new IllegalArgumentException("Profile already exists");
        JsonObject json = copyCurrent ? ConfigSerializer.snapshot(modules, hud) : new JsonObject();
        write(name, json);
    }

    public void deleteProfile(String name) throws IOException {
        requireValid(name);
        if (name.equals(activeProfile)) throw new IllegalArgumentException("Cannot delete the active profile");
        Path file = profileFile(name);
        backups.backup(name, file);
        Files.deleteIfExists(file);
    }

    /** Game thread. Writes a timestamped copy of the profile to {@code exports/}. */
    public Path exportProfile(String name) throws IOException {
        requireValid(name);
        if (name.equals(activeProfile)) saveNow();
        Path src = profileFile(name);
        if (!Files.exists(src)) throw new IOException("Profile file not found");
        Files.createDirectories(exportsDir);
        Path dst = exportsDir.resolve(name + "_" + LocalDateTime.now().format(STAMP) + ".json");
        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        return dst;
    }

    /** Validates and copies an external JSON file into {@code profiles/}. Returns the new profile name. */
    public String importProfile(Path file) throws IOException {
        JsonObject json = readJson(file);
        if (json == null) throw new IOException("Not a valid JSON object");
        if (!json.has("modules") && !json.has("hud")) throw new IOException("Not a Phantom profile");
        String base = file.getFileName().toString();
        if (base.toLowerCase().endsWith(".json")) base = base.substring(0, base.length() - 5);
        base = ProfileNames.sanitize(base.replaceAll("_\\d{8}-\\d{6}$", ""));
        String name = base;
        for (int i = 2; Files.exists(profileFile(name)); i++) {
            String suffix = " " + i;
            name = (base.length() + suffix.length() > 32 ? base.substring(0, 32 - suffix.length()) : base) + suffix;
        }
        Files.createDirectories(profilesDir);
        atomicWrite(profileFile(name), GSON.toJson(json));
        return name;
    }

    public List<Path> importCandidates() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(importDir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(importDir, "*.json")) {
            for (Path p : ds) out.add(p);
        } catch (IOException ignored) {
        }
        return out;
    }

    /** Game thread. Immediate backup of the active profile. */
    public Path backupNow() throws IOException {
        saveNow();
        return backups.backup(activeProfile, profileFile(activeProfile));
    }

    /** Game thread. Replaces the active profile with its newest backup. */
    public boolean restoreLatestBackup() throws IOException {
        List<Path> list = backups.list(activeProfile);
        if (list.isEmpty()) return false;
        JsonObject json = readJson(list.get(0));
        if (json == null) return false;
        applyNow(json);
        saveNow();
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private void applyNow(JsonObject json) {
        applying = true;
        try {
            ConfigSerializer.apply(json, modules, hud);
        } finally {
            applying = false;
        }
    }

    private JsonObject readProfile(String name) {
        return readJson(profileFile(name));
    }

    private static JsonObject readJson(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Ignoring unreadable JSON file {}: {}", file, ex.getMessage());
            return null;
        }
    }

    private void writeMetaQuietly() {
        JsonObject meta = new JsonObject();
        meta.addProperty("schema", ConfigSerializer.SCHEMA);
        meta.addProperty("activeProfile", activeProfile);
        try {
            atomicWrite(metaFile, GSON.toJson(meta));
        } catch (IOException e) {
            LOG.warn("Could not write {}", metaFile, e);
        }
    }

    private static void atomicWrite(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireValid(String name) {
        if (!ProfileNames.isValid(name)) {
            throw new IllegalArgumentException("Profile names use 1-32 letters, digits, spaces, '-' or '_'");
        }
    }

    public Path profileFile(String name) {
        return profilesDir.resolve(name + ".json");
    }

    public Path root() {
        return root;
    }

    public Path exportsDir() {
        return exportsDir;
    }

    public Path importDir() {
        return importDir;
    }

    public Path backupsDir() {
        return backups.dir();
    }

    public boolean isLoaded() {
        return loaded;
    }

    /** Runs a task on the config IO thread (used for low-frequency status heartbeats). */
    public void scheduleIo(Runnable task, long periodMs) {
        io.scheduleWithFixedDelay(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.debug("Background task failed", e);
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    /** Game thread. Flushes pending changes and stops the IO thread. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        try {
            if (loaded) saveNow();
            writeMetaQuietly();
        } finally {
            io.shutdown();
            try {
                if (!io.awaitTermination(2, TimeUnit.SECONDS)) io.shutdownNow();
            } catch (InterruptedException e) {
                io.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public interface Listener {
        default void onProfileLoaded(String profile) {
        }

        default void onSaved(String profile) {
        }

        default void onError(String message) {
        }
    }
}
