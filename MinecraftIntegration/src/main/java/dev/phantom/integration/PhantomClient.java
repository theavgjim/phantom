package dev.phantom.integration;

import dev.phantom.config.ConfigManager;
import dev.phantom.core.PhantomCore;
import dev.phantom.core.ipc.StatusWriter;
import dev.phantom.core.module.Module;
import dev.phantom.core.module.ModuleManager;
import dev.phantom.core.theme.Theme;
import dev.phantom.hud.HudManager;
import dev.phantom.integration.compat.VersionCheck;
import dev.phantom.integration.event.AttackEntityEvent;
import dev.phantom.integration.event.Render2DEvent;
import dev.phantom.integration.event.Render3DEvent;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.config.ServerProfileStore;
import dev.phantom.integration.event.WorldChangeEvent;
import dev.phantom.integration.guard.PolicyGuard;
import dev.phantom.integration.server.PolicyPayload;
import dev.phantom.integration.server.ReconnectHandler;
import dev.phantom.integration.server.ServerSession;
import dev.phantom.integration.util.PerfMonitor;
import dev.phantom.modules.misc.Reconnect;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import dev.phantom.integration.input.KeyHandler;
import dev.phantom.integration.render.Projection;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.render.Render3D;
import dev.phantom.integration.util.Notifications;
import dev.phantom.integration.world.EntityCache;
import dev.phantom.integration.world.TargetTracker;
import dev.phantom.modules.misc.NotificationsModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runtime that connects {@link PhantomCore} to Minecraft: lifecycle, tick/render/input hooks,
 * private-world policy, config sync, launcher status and orderly shutdown.
 */
public final class PhantomClient {
    public static final Logger LOG = LoggerFactory.getLogger("Phantom");
    private static PhantomClient instance;

    private final MinecraftClient mc;
    private final PhantomCore core;
    private final ServerSession session;
    private final PolicyGuard guard;
    private final ReconnectHandler reconnect;
    private final PerfMonitor perf = new PerfMonitor();
    private Object lastWorld;
    private boolean enforcing;
    private final KeyHandler keys;
    private final EntityCache entities;
    private final TargetTracker targets;
    private final Notifications notifications = new Notifications();
    private final Projection projection = new Projection();
    private final Render3D render3d = new Render3D();
    private final HudManager hud;
    private final StatusWriter status;
    private final VersionCheck.Report versionReport;

    private volatile String statusContext = "menu";
    private volatile int statusFps;
    private volatile List<String> statusModules = List.of();
    private long lastFrameNanos = System.nanoTime();
    private boolean shutDown;

    private PhantomClient(MinecraftClient mc) {
        this.mc = mc;
        String version = FabricLoader.getInstance().getModContainer("phantom")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        this.versionReport = VersionCheck.run(LOG);
        Path configRoot = FabricLoader.getInstance().getConfigDir().resolve("phantom");
        this.core = new PhantomCore(version, configRoot, mc);
        ServerProfileStore servers = new ServerProfileStore(configRoot);
        servers.load();
        this.session = new ServerSession(mc, servers, msg -> notifications.push("Server", msg, Notifications.Kind.INFO));
        this.guard = new PolicyGuard(mc, session);
        this.reconnect = new ReconnectHandler(mc, session, () -> core.modules().get(Reconnect.class));
        this.keys = new KeyHandler(mc, core.modules());
        this.entities = new EntityCache(mc);
        this.targets = new TargetTracker(mc);
        this.hud = new HudManager(core.hud());
        this.status = new StatusWriter(configRoot);
    }

    // ------------------------------------------------------------------ bootstrap

    static void bootstrap() {
        if (instance != null) return;
        long t0 = System.nanoTime();
        MinecraftClient mc = MinecraftClient.getInstance();
        PhantomClient c = new PhantomClient(mc);
        instance = c;
        c.init();
        LOG.info("Phantom {} initialised in {} ms ({} modules)", c.core.version(),
                (System.nanoTime() - t0) / 1_000_000, c.core.modules().all().size());
    }

    private void init() {
        ModuleManager modules = core.modules();
        ModuleRegistry.registerAll(modules);
        hud.registerDefaults();
        modules.setGuard(guard);
        Hooks.bind(this);

        modules.addListener(new ModuleManager.Listener() {
            @Override
            public void onToggled(Module m) {
                refreshStatusModules();
                NotificationsModule n = modules.get(NotificationsModule.class);
                if (!enforcing && n != null && n.isEnabled() && n.toggleMessages.isOn() && core.config().isLoaded() && !m.isHiddenFromList()) {
                    notifications.push(m.getName(), m.isEnabled() ? "Enabled" : "Disabled",
                            m.isEnabled() ? Notifications.Kind.SUCCESS : Notifications.Kind.INFO);
                }
            }

            @Override
            public void onRefused(Module m, String reason) {
                notifications.push(m.getName(), reason, Notifications.Kind.WARNING);
            }

            @Override
            public void onError(Module m, RuntimeException error) {
                LOG.error("Module {} failed", m.getName(), error);
                notifications.push(m.getName(), "Error: " + error.getClass().getSimpleName(), Notifications.Kind.ERROR);
            }
        });
        core.config().addListener(new ConfigManager.Listener() {
            @Override
            public void onProfileLoaded(String profile) {
                refreshStatusModules();
            }

            @Override
            public void onError(String message) {
                mc.execute(() -> notifications.push("Config", message, Notifications.Kind.ERROR));
            }
        });

        // Asynchronous initialisation: the profile is read off-thread while the game keeps loading.
        core.config().initAsync().exceptionally(t -> {
            LOG.error("Configuration failed to load; running with defaults", t);
            return null;
        });
        core.config().scheduleIo(this::writeStatus, 5000);

        // Multiplayer: session tracking, the optional phantom:policy channel and Reconnect.
        PayloadTypeRegistry.playS2C().register(PolicyPayload.ID, PolicyPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(PolicyPayload.ID, (payload, context) -> session.onPolicy(payload.json()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onMainThread(this::onJoin));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onMainThread(session::onDisconnect));
        reconnect.register();

        ClientTickEvents.START_CLIENT_TICK.register(c -> onTick());
        WorldRenderEvents.LAST.register(this::onWorldRender);
        HudRenderCallback.EVENT.register(this::onHudRender);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> shutdown());
        if (!versionReport.result().compatible()) {
            mc.execute(() -> notifications.push("Version", versionReport.result().message(), Notifications.Kind.ERROR));
        }
    }

    // ------------------------------------------------------------------ hooks

    private void onTick() {
        perf.begin(PerfMonitor.Section.TICK);
        try {
            tickInner();
        } catch (RuntimeException e) {
            // Never let Phantom take the game down; modules that throw are switched off individually.
            LOG.error("Phantom tick failed", e);
        } finally {
            perf.end(PerfMonitor.Section.TICK);
        }
    }

    private void tickInner() {
        statusFps = mc.getCurrentFps();
        statusContext = switch (guard.context()) {
            case MENU -> "menu";
            case PRIVATE_WORLD -> "private-world";
            case REMOTE_SERVER -> "remote-server";
        };
        session.tick();
        if (mc.world != lastWorld) {
            // Join, leave, dimension change or proxy switch: drop every per-world cache.
            lastWorld = mc.world;
            entities.clear();
            targets.clear();
            projection.invalidate();
            core.events().post(WorldChangeEvent.INSTANCE);
        }
        PolicyGuard.Change change = enforce();
        if (!change.paused().isEmpty()) {
            Module first = change.paused().get(0);
            String names = change.paused().size() == 1 ? first.getName() : change.paused().size() + " modules";
            notifications.push(names + " paused", first.getSuspendedReason(), Notifications.Kind.WARNING);
        }
        if (!change.resumed().isEmpty() && core.config().isLoaded()) {
            notifications.push(change.resumed().size() == 1 ? change.resumed().get(0).getName() : change.resumed().size() + " modules",
                    "Resumed", Notifications.Kind.SUCCESS);
        }
        if (mc.player == null || mc.world == null) return;
        entities.onTick();
        targets.onTick();
        core.events().post(TickEvent.INSTANCE);
    }

    private void onJoin() {
        session.onJoin();
        reconnect.onJoin();
        var profile = session.profile();
        if (profile != null && profile.configProfile != null && !profile.configProfile.equals(core.config().activeProfile())
                && core.config().listProfiles().contains(profile.configProfile)) {
            core.config().switchProfile(profile.configProfile);
            notifications.push("Profile", "Loaded '" + profile.configProfile + "' for " + session.name(), Notifications.Kind.INFO);
        }
        enforce();
    }

    /** Policy enforcement; its own pause/resume toasts replace the per-module toggle toasts. */
    private PolicyGuard.Change enforce() {
        enforcing = true;
        try {
            return guard.enforce(core.modules());
        } finally {
            enforcing = false;
        }
    }

    private void onMainThread(Runnable r) {
        if (mc.isOnThread()) r.run();
        else mc.execute(r);
    }

    private void onWorldRender(WorldRenderContext context) {
        if (mc.player == null) return;
        long now = System.nanoTime();
        float frameTime = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        var cam = context.camera();
        projection.capture(context.positionMatrix(), context.projectionMatrix(), cam.getPos());
        if (!core.events().hasListeners(Render3DEvent.class)) return;

        perf.begin(PerfMonitor.Section.WORLD);
        try {
            render3d.begin(cam.getPos(), context.positionMatrix());
            Render3DEvent e = Render3DEvent.INSTANCE;
            e.renderer = render3d;
            e.camera = cam;
            e.cameraPos = cam.getPos();
            e.tickDelta = context.tickCounter().getTickDelta(true);
            e.frameTime = frameTime;
            core.events().post(e);
            render3d.flush();
        } catch (RuntimeException ex) {
            LOG.error("Phantom world render failed", ex);
        } finally {
            perf.end(PerfMonitor.Section.WORLD);
        }
    }

    private void onHudRender(DrawContext ctx, RenderTickCounter counter) {
        if (mc.options.hudHidden) return;
        float tickDelta = counter.getTickDelta(true);
        // Vanilla HUD layers (chat in particular) leave depth behind at high GUI z. Phantom flushes its
        // own batches with depth testing off (Render2D.flush) so its text can never be occluded.
        perf.begin(PerfMonitor.Section.HUD);
        Render2D.flush(ctx);
        try {
            if (mc.player != null) {
                Render2DEvent e = Render2DEvent.INSTANCE;
                e.context = ctx;
                e.tickDelta = tickDelta;
                core.events().post(e);
            }
            if (!(mc.currentScreen instanceof dev.phantom.gui.HudEditorScreen)) {
                hud.render(ctx, tickDelta, false);
            }
        } catch (RuntimeException ex) {
            LOG.error("Phantom HUD render failed", ex);
        }
        Render2D.flush(ctx);
        perf.end(PerfMonitor.Section.HUD);
    }

    void onAttack(Entity target) {
        AttackEntityEvent e = AttackEntityEvent.INSTANCE;
        e.target = target;
        core.events().post(e);
    }

    // ------------------------------------------------------------------ status / shutdown

    private void refreshStatusModules() {
        List<String> names = new ArrayList<>();
        for (Module m : core.modules().enabled()) names.add(m.getName());
        statusModules = List.copyOf(names);
    }

    private void writeStatus() {
        String server = session.isRemote() ? session.address() + (session.brand().isEmpty() ? "" : " (" + session.brand() + ")") : "";
        status.write(new StatusWriter.Status(!shutDown, core.version(), versionReport.minecraft(),
                core.config().activeProfile(), statusContext, statusFps, statusModules, server));
    }

    /** Safe shutdown: persist state first, then let modules restore game settings, then free GPU buffers. */
    private void shutdown() {
        if (shutDown) return;
        shutDown = true;
        LOG.info("Shutting down Phantom");
        try {
            core.config().close();
        } catch (RuntimeException e) {
            LOG.error("Config flush failed", e);
        }
        try {
            core.modules().disableAll();
        } catch (RuntimeException e) {
            LOG.error("Module shutdown failed", e);
        }
        writeStatus();
        render3d.close();
    }

    // ------------------------------------------------------------------ accessors

    public static PhantomClient get() {
        if (instance == null) throw new IllegalStateException("Phantom not initialised");
        return instance;
    }

    public static PhantomClient getOrNull() {
        return instance;
    }

    public MinecraftClient mc() {
        return mc;
    }

    public PhantomCore core() {
        return core;
    }

    public Theme theme() {
        return core.themes().current();
    }

    public ServerSession session() {
        return session;
    }

    public PerfMonitor perf() {
        return perf;
    }

    public ReconnectHandler reconnect() {
        return reconnect;
    }

    public PolicyGuard guard() {
        return guard;
    }

    public KeyHandler keys() {
        return keys;
    }

    public EntityCache entities() {
        return entities;
    }

    public TargetTracker targets() {
        return targets;
    }

    public Notifications notifications() {
        return notifications;
    }

    public Projection projection() {
        return projection;
    }

    public HudManager hud() {
        return hud;
    }

    public VersionCheck.Report versionReport() {
        return versionReport;
    }
}
