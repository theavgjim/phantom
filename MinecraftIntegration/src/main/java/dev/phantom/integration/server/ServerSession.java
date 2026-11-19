package dev.phantom.integration.server;

import dev.phantom.config.ServerProfileStore;
import dev.phantom.core.policy.ServerPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.function.Consumer;

/**
 * What Phantom knows about the current connection: server identity, detected brand / version,
 * the policy the server sent, and the user's per-server profile.
 */
public final class ServerSession {
    private static final Logger LOG = LoggerFactory.getLogger("Phantom/Server");

    public enum Kind { NONE, SINGLEPLAYER, LAN_HOST, REMOTE }

    public record Target(ServerAddress address, ServerInfo info) {
    }

    private final MinecraftClient mc;
    private final ServerProfileStore store;
    private final Consumer<String> notifier;

    private Kind kind = Kind.NONE;
    private String address = "";
    private String name = "";
    private String brand;
    private String version = "";
    private int serverProtocol;
    private long joinedAt;
    private ServerPolicy policy = ServerPolicy.DEFAULT;
    private ServerProfileStore.Profile profile;
    private Target lastTarget;
    private int policyGeneration;

    public ServerSession(MinecraftClient mc, ServerProfileStore store, Consumer<String> notifier) {
        this.mc = mc;
        this.store = store;
        this.notifier = notifier;
    }

    /** ConnectScreenMixin: remembers where we are connecting, for reconnecting and version hints. */
    public void onConnectAttempt(ServerAddress address, ServerInfo info) {
        lastTarget = new Target(address, info);
    }

    /** Play phase started. Also fires after a proxy (Velocity/BungeeCord) moves us to another backend. */
    public void onJoin() {
        policy = ServerPolicy.DEFAULT; // each server (and each proxy backend) must state its own policy
        policyGeneration++;
        joinedAt = System.currentTimeMillis();
        brand = brandNow();
        if (mc.isIntegratedServerRunning()) {
            kind = mc.getServer() != null && mc.getServer().isRemote() ? Kind.LAN_HOST : Kind.SINGLEPLAYER;
            address = kind == Kind.LAN_HOST ? "lan-host" : "singleplayer";
            name = mc.getServer() != null ? mc.getServer().getSaveProperties().getLevelName() : "Singleplayer";
            version = SharedConstants.getGameVersion().getName();
            serverProtocol = SharedConstants.getProtocolVersion();
            profile = null;
            LOG.info("Joined {} '{}'", kind == Kind.LAN_HOST ? "own LAN world" : "singleplayer world", name);
            return;
        }
        kind = Kind.REMOTE;
        ServerInfo info = mc.getCurrentServerEntry();
        address = info != null ? ServerProfileStore.normalize(info.address) : "unknown";
        name = info != null && info.name != null ? info.name : address;
        version = info != null && info.version != null ? info.version.getString() : "";
        // Having joined, the server speaks our protocol (natively or via ViaVersion).
        serverProtocol = info != null && info.protocolVersion > 0 ? info.protocolVersion : SharedConstants.getProtocolVersion();
        profile = store.recordJoin(address, name, brand == null ? "" : brand, version, serverProtocol, joinedAt);
        store.save();
        LOG.info("Joined server {} (brand={}, version='{}', protocol={})", address, brand, version, serverProtocol);
    }

    public void onDisconnect() {
        if (kind == Kind.REMOTE) LOG.info("Left server {} after {} s", address, (System.currentTimeMillis() - joinedAt) / 1000);
        kind = Kind.NONE;
        policy = ServerPolicy.DEFAULT;
        policyGeneration++;
        profile = null;
        brand = null;
        store.save();
    }

    /** Called every tick; picks up the brand once it arrives and notices proxy backend switches. */
    public void tick() {
        if (kind == Kind.NONE) return;
        String now = brandNow();
        if (now == null || now.equals(brand)) return;
        boolean switched = brand != null && kind == Kind.REMOTE;
        brand = now;
        if (profile != null) {
            profile.brand = now;
            store.save();
        }
        if (switched) {
            LOG.info("Server brand changed to '{}' (proxy switch) - policy reset", now);
            policy = ServerPolicy.DEFAULT;
            policyGeneration++;
            notifier.accept("Switched server: " + now);
        }
    }

    /** phantom:policy received. Invalid payloads are logged and ignored (the default policy stays). */
    public void onPolicy(String json) {
        if (kind != Kind.REMOTE) return;
        if (json == null) {
            LOG.warn("Ignored oversized policy payload from {}", address);
            return;
        }
        try {
            policy = ServerPolicy.parse(json, "phantom:policy");
            policyGeneration++;
            store.recordPolicy(address, policy);
            store.save();
            LOG.info("Server policy from {}: allow={} deny={}", address, policy.allow(), policy.deny());
            String msg = policy.message().isBlank() ? "" : " - " + policy.message();
            notifier.accept("Server policy received" + msg);
        } catch (RuntimeException e) {
            LOG.warn("Ignored invalid policy payload from {}: {}", address, e.getMessage());
        }
    }

    private String brandNow() {
        var handler = mc.getNetworkHandler();
        return handler == null ? null : handler.getBrand();
    }

    // ------------------------------------------------------------------ accessors

    public Kind kind() {
        return kind;
    }

    public boolean isRemote() {
        return kind == Kind.REMOTE;
    }

    public String address() {
        return address;
    }

    public String name() {
        return name;
    }

    public String brand() {
        return brand == null ? "" : brand;
    }

    public String version() {
        return version;
    }

    public int serverProtocol() {
        return serverProtocol;
    }

    public ServerPolicy policy() {
        return policy;
    }

    /** Changes whenever the policy may have changed (join, leave, payload, proxy switch). */
    public int policyGeneration() {
        return policyGeneration;
    }

    public Set<String> blocked() {
        return profile == null ? Set.of() : profile.blocked;
    }

    public ServerProfileStore.Profile profile() {
        return profile;
    }

    public ServerProfileStore store() {
        return store;
    }

    public Target lastTarget() {
        return lastTarget;
    }

    public long joinedAt() {
        return joinedAt;
    }

    /** Ping to the server in ms from the player list, or -1. */
    public int latency() {
        var h = mc.getNetworkHandler();
        if (h == null || mc.player == null) return -1;
        var entry = h.getPlayerListEntry(mc.player.getUuid());
        return entry == null ? -1 : entry.getLatency();
    }

    public static boolean protocolMatches(int protocol) {
        return protocol <= 0 || protocol == SharedConstants.getProtocolVersion();
    }
}
