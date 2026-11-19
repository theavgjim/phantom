package dev.phantom.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import dev.phantom.core.policy.ServerPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-server compatibility profiles ({@code servers.json}): what Phantom last saw on each server,
 * the config profile to load there, and extra modules the user blocked there.
 *
 * <p>Server <em>grants</em> are deliberately not stored: a server must send its policy again on
 * every connection, so a cached permission can never outlive a server changing its rules.</p>
 */
public final class ServerProfileStore {
    private static final Logger LOG = LoggerFactory.getLogger("Phantom/Servers");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int MAX_SERVERS = 200;

    public static final class Profile {
        public String address = "";
        public String name = "";
        public String brand = "";
        public String version = "";
        public int protocol;
        public long lastJoined;
        public int joins;
        /** Config profile to switch to when joining, or null to keep the current one. */
        public String configProfile;
        /** Module keys (see {@link ServerPolicy#key}) the user turned off for this server. */
        public Set<String> blocked = new LinkedHashSet<>();
        /** Informational: what the server said last time (never used to grant anything). */
        public String lastPolicySource = "default";
        public List<String> lastAllowed = new ArrayList<>();
        public List<String> lastDenied = new ArrayList<>();
    }

    private final Path file;
    private final Map<String, Profile> profiles = new LinkedHashMap<>();

    public ServerProfileStore(Path configRoot) {
        this.file = configRoot.resolve("servers.json");
    }

    /** "Play.Example.net:25565" -> "play.example.net"; other ports are kept. */
    public static String normalize(String address) {
        if (address == null) return "";
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.endsWith(".")) a = a.substring(0, a.length() - 1);
        if (a.endsWith(":25565")) a = a.substring(0, a.length() - 6);
        return a;
    }

    public synchronized void load() {
        profiles.clear();
        if (!Files.isRegularFile(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (!JsonParser.parseString(json).isJsonArray()) throw new IOException("expected an array");
            List<Profile> list = GSON.fromJson(json, new TypeToken<List<Profile>>() {
            }.getType());
            if (list != null) for (Profile p : list) {
                if (p == null || p.address == null || p.address.isBlank()) continue;
                if (p.blocked == null) p.blocked = new LinkedHashSet<>();
                if (p.lastAllowed == null) p.lastAllowed = new ArrayList<>();
                if (p.lastDenied == null) p.lastDenied = new ArrayList<>();
                if (p.configProfile != null && !ProfileNames.isValid(p.configProfile)) p.configProfile = null;
                profiles.put(normalize(p.address), p);
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Ignoring unreadable {}: {}", file, e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("servers.json.tmp");
            Files.writeString(tmp, GSON.toJson(new ArrayList<>(profiles.values())), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("Could not save {}", file, e);
        }
    }

    /** Returns the profile for this address, creating it if needed. */
    public synchronized Profile get(String address) {
        String key = normalize(address);
        Profile p = profiles.get(key);
        if (p == null) {
            p = new Profile();
            p.address = key;
            profiles.put(key, p);
            prune();
        }
        return p;
    }

    public synchronized Profile find(String address) {
        return profiles.get(normalize(address));
    }

    public synchronized List<Profile> all() {
        List<Profile> out = new ArrayList<>(profiles.values());
        out.sort(Comparator.comparingLong((Profile p) -> p.lastJoined).reversed());
        return out;
    }

    public synchronized void remove(String address) {
        profiles.remove(normalize(address));
    }

    /** Records a join and the observed server details. */
    public synchronized Profile recordJoin(String address, String name, String brand, String version, int protocol, long now) {
        Profile p = get(address);
        if (name != null && !name.isBlank()) p.name = name;
        if (brand != null) p.brand = brand;
        if (version != null && !version.isBlank()) p.version = version;
        if (protocol > 0) p.protocol = protocol;
        p.lastJoined = now;
        p.joins++;
        return p;
    }

    public synchronized void recordPolicy(String address, ServerPolicy policy) {
        Profile p = get(address);
        p.lastPolicySource = policy.source();
        p.lastAllowed = new ArrayList<>(policy.allow());
        p.lastDenied = new ArrayList<>(policy.deny());
    }

    public synchronized void setBlocked(String address, String moduleName, boolean blocked) {
        Profile p = get(address);
        String k = ServerPolicy.key(moduleName);
        if (blocked) p.blocked.add(k);
        else p.blocked.remove(k);
    }

    private void prune() {
        if (profiles.size() <= MAX_SERVERS) return;
        all().stream().skip(MAX_SERVERS).map(p -> normalize(p.address)).toList().forEach(profiles::remove);
    }

    public Path file() {
        return file;
    }
}
