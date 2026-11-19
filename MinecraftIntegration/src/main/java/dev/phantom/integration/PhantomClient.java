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
import dev.phantom.integration.guard.PrivateWorldGuard;
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
    private final PrivateWorldGuard guard;
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
        this.guard = new PrivateWorldGuard(mc);
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
                if (n != null && n.isEnabled() && n.toggleMessages.isOn() && core.config().isLoaded() && !m.isHiddenFromList()) {
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
        statusFps = mc.getCurrentFps();
        PrivateWorldGuard.Context ctx = guard.context();
        statusContext = switch (ctx) {
            case MENU -> "menu";
            case PRIVATE_WORLD -> "private-world";
            case REMOTE_SERVER -> "remote-server";
        };
        if (mc.player == null || mc.world == null) {
            entities.clear();
            targets.clear();
            projection.invalidate();
            return;
        }
        guard.enforce(core.modules(), m -> notifications.push(m.getName(),
                "Disabled: " + PrivateWorldGuard.REASON, Notifications.Kind.WARNING));
        entities.onTick();
        targets.onTick();
        core.events().post(TickEvent.INSTANCE);
    }

    private void onWorldRender(WorldRenderContext context) {
        if (mc.player == null) return;
        long now = System.nanoTime();
        float frameTime = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        var cam = context.camera();
        projection.capture(context.positionMatrix(), context.projectionMatrix(), cam.getPos());
        if (!core.events().hasListeners(Render3DEvent.class)) return;

        render3d.begin(cam.getPos(), context.positionMatrix());
        Render3DEvent e = Render3DEvent.INSTANCE;
        e.renderer = render3d;
        e.camera = cam;
        e.cameraPos = cam.getPos();
        e.tickDelta = context.tickCounter().getTickDelta(true);
        e.frameTime = frameTime;
        core.events().post(e);
        render3d.flush();
    }

    private void onHudRender(DrawContext ctx, RenderTickCounter counter) {
        if (mc.options.hudHidden) return;
        float tickDelta = counter.getTickDelta(true);
        // Vanilla HUD layers (chat in particular) leave depth behind at high GUI z. Phantom flushes its
        // own batches with depth testing off (Render2D.flush) so its text can never be occluded.
        Render2D.flush(ctx);
        if (mc.player != null) {
            Render2DEvent e = Render2DEvent.INSTANCE;
            e.context = ctx;
            e.tickDelta = tickDelta;
            core.events().post(e);
        }
        if (!(mc.currentScreen instanceof dev.phantom.gui.HudEditorScreen)) {
            hud.render(ctx, tickDelta, false);
        }
        Render2D.flush(ctx);
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
        status.write(new StatusWriter.Status(!shutDown, core.version(), versionReport.minecraft(),
                core.config().activeProfile(), statusContext, statusFps, statusModules));
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

    public PrivateWorldGuard guard() {
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
