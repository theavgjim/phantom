package dev.phantom.selftest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.phantom.core.module.Category;
import dev.phantom.core.module.Module;
import dev.phantom.core.module.ModuleManager;
import dev.phantom.gui.ClickGuiScreen;
import dev.phantom.gui.HudEditorScreen;
import dev.phantom.integration.PhantomClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Scripted end-to-end check of Phantom inside a real client. Dev-only. */
public final class SelfTest implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("Phantom/SelfTest");
    private final List<String[]> results = new ArrayList<>();
    private final List<String> moduleErrors = new ArrayList<>();
    private int stage;
    private int wait;
    private float zombieHealthBefore;

    @Override
    public void onInitializeClient() {
        if (!Boolean.getBoolean("phantom.selftest")) return;
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void check(String name, boolean ok, String detail) {
        results.add(new String[]{name, ok ? "PASS" : "FAIL", detail});
        LOG.info("[{}] {} - {}", ok ? "PASS" : "FAIL", name, detail);
    }

    private void shot(MinecraftClient mc, String name) {
        ScreenshotRecorder.saveScreenshot(mc.runDirectory, name + ".png", mc.getFramebuffer(), t -> {
        });
    }

    private void cmd(MinecraftClient mc, String command) {
        mc.player.networkHandler.sendChatCommand(command);
    }

    private ModuleManager modules() {
        return PhantomClient.get().core().modules();
    }

    private void enable(String... names) {
        for (String n : names) {
            Module m = modules().get(n);
            String refused = m.setEnabled(true);
            if (refused != null) check("enable " + n, false, refused);
        }
    }

    private void tick(MinecraftClient mc) {
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            step(mc);
        } catch (Throwable t) {
            LOG.error("Self-test crashed at stage {}", stage, t);
            check("stage " + stage, false, t.toString());
            finish(mc);
        }
    }

    private void step(MinecraftClient mc) throws Exception {
        switch (stage) {
            case 0 -> {
                if (mc.getOverlay() != null) return;
                if (mc.currentScreen != null && !(mc.currentScreen instanceof TitleScreen)
                        && mc.currentScreen.getClass().getSimpleName().contains("Onboarding")) {
                    mc.options.onboardAccessibility = false;
                    mc.options.write();
                    mc.setScreen(new TitleScreen());
                    return;
                }
                if (!(mc.currentScreen instanceof TitleScreen)) return;
                modules().addListener(new ModuleManager.Listener() {
                    @Override
                    public void onError(Module module, RuntimeException error) {
                        moduleErrors.add(module.getName() + ": " + error);
                    }
                });
                check("client boot", true, "Phantom " + PhantomClient.get().core().version() + ", "
                        + modules().all().size() + " modules, version check: " + PhantomClient.get().versionReport().result().message());
                // Force every mixin target to load and apply now; throws if any injection fails.
                MixinEnvironment.getCurrentEnvironment().audit();
                check("mixin audit", true, "all mixins applied");
                mc.setScreen(new ClickGuiScreen());
                stage = 1;
                wait = 30;
            }
            case 1 -> {
                shot(mc, "01-clickgui-menu");
                mc.setScreen(new TitleScreen());
                stage = 2;
                wait = 5;
            }
            case 2 -> {
                LevelInfo info = new LevelInfo("PhantomSelfTest", GameMode.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), DataConfiguration.SAFE_MODE);
                mc.createIntegratedServerLoader().createAndStart("phantom-selftest-" + System.currentTimeMillis(), info,
                        new GeneratorOptions(42L, false, false),
                        drm -> drm.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder(),
                        new TitleScreen());
                stage = 3;
            }
            case 3 -> {
                if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
                check("world join", PhantomClient.get().guard().context().name().equals("PRIVATE_WORLD"),
                        "guard context = " + PhantomClient.get().guard().context());
                cmd(mc, "time set day");
                cmd(mc, "gamerule doDaylightCycle false");
                cmd(mc, "tp @s 0 -60 0 0 0");
                stage = 4;
                wait = 40;
            }
            case 4 -> {
                cmd(mc, "summon armor_stand 1.5 -60 2.5");
                cmd(mc, "summon zombie -1.5 -60 2 {NoAI:1b,PersistenceRequired:1b}");
                cmd(mc, "summon cow 0 -60 7 {NoAI:1b}");
                cmd(mc, "summon zombie 6 -60 14 {NoAI:1b,PersistenceRequired:1b}");
                cmd(mc, "effect give @e[type=zombie] fire_resistance infinite 0 true");
                cmd(mc, "setblock 4 -60 -1 chest");
                cmd(mc, "setblock 5 -60 -1 barrel");
                cmd(mc, "setblock -4 -60 -1 ender_chest");
                cmd(mc, "setblock -3 -61 2 diamond_ore");
                cmd(mc, "setblock 3 -61 6 ancient_debris");
                mc.player.setYaw(0);
                mc.player.setPitch(15);
                stage = 5;
                wait = 30;
            }
            case 5 -> {
                enable("ESP", "Tracers", "Nametags", "StorageESP", "BlockESP", "Highlight", "DistanceIndicators", "Reach", "Fullbright");
                stage = 6;
                wait = 60;
            }
            case 6 -> {
                shot(mc, "02-visuals");
                check("visual modules", moduleErrors.isEmpty(), moduleErrors.isEmpty() ? "no errors" : moduleErrors.toString());
                check("storage esp found containers", "3".equals(modules().get("StorageESP").getSuffix()),
                        "found " + modules().get("StorageESP").getSuffix());
                check("block esp found ores", Integer.parseInt(modules().get("BlockESP").getSuffix()) >= 2,
                        "found " + modules().get("BlockESP").getSuffix());
                check("fullbright gamma", mc.options.getGamma().getValue() > 1.0, "gamma=" + mc.options.getGamma().getValue());
                ZombieEntity z = nearestZombie(mc);
                zombieHealthBefore = z == null ? -1 : z.getHealth();
                check("test zombie unharmed before aura", z != null && z.getHealth() == z.getMaxHealth(), "health " + zombieHealthBefore);
                modules().get("ESP").setEnabled(false);
                modules().get("Tracers").setEnabled(false);
                enable("KillAura", "Criticals", "AimBot");
                stage = 7;
                wait = 80;
            }
            case 7 -> {
                shot(mc, "03-combat-targethud");
                ZombieEntity z = nearestZombie(mc);
                boolean standGone = mc.world.getEntitiesByClass(ArmorStandEntity.class, mc.player.getBoundingBox().expand(8), e -> true).isEmpty();
                boolean zombieHit = z == null || z.getHealth() < zombieHealthBefore;
                check("killaura hits dummy", standGone, standGone ? "armor stand destroyed" : "armor stand still present");
                check("killaura hits mob", zombieHit, "zombie health " + zombieHealthBefore + " -> " + (z == null ? "dead" : z.getHealth()));
                check("killaura ignores far mob", farZombieAlive(mc), "zombie at 15m untouched");
                modules().get("KillAura").setEnabled(false);
                modules().get("AimBot").setEnabled(false);
                enable("Fly", "Speed");
                stage = 8;
                wait = 10;
            }
            case 8 -> {
                check("fly grants flight", mc.player.getAbilities().allowFlying, "allowFlying=" + mc.player.getAbilities().allowFlying);
                modules().get("Fly").setEnabled(false);
                modules().get("Speed").setEnabled(false);
                enable("Freecam");
                stage = 9;
                wait = 20;
            }
            case 9 -> {
                boolean detached = mc.gameRenderer.getCamera().isThirdPerson() && !mc.chunkCullingEnabled;
                shot(mc, "04-freecam");
                modules().get("Freecam").setEnabled(false);
                check("freecam detaches camera", detached && mc.chunkCullingEnabled,
                        "camera third-person while on, culling restored after");
                ClickGuiScreen gui = new ClickGuiScreen();
                mc.setScreen(gui);
                setField(gui, "category", Category.VISUALS);
                uiExpanded(gui).add(modules().get("ESP"));
                stage = 10;
                wait = 40;
            }
            case 10 -> {
                shot(mc, "05-clickgui-visuals");
                Screen gui = mc.currentScreen;
                setField(gui, "section", enumValue(gui, "THEMES"));
                stage = 11;
                wait = 25;
            }
            case 11 -> {
                shot(mc, "06-clickgui-themes");
                setField(mc.currentScreen, "section", enumValue(mc.currentScreen, "PROFILES"));
                stage = 12;
                wait = 25;
            }
            case 12 -> {
                shot(mc, "07-clickgui-profiles");
                mc.setScreen(new HudEditorScreen(null));
                stage = 13;
                wait = 25;
            }
            case 13 -> {
                shot(mc, "08-hud-editor");
                mc.setScreen(null);
                var cfg = PhantomClient.get().core().config();
                cfg.saveNow();
                String json = Files.readString(cfg.profileFile(cfg.activeProfile()), StandardCharsets.UTF_8);
                check("config persisted", json.contains("\"StorageESP\": {\n      \"enabled\": true"), cfg.profileFile(cfg.activeProfile()).toString());
                cfg.createProfile("SelfTest Copy", true);
                cfg.switchProfile("SelfTest Copy");
                boolean kept = modules().get("StorageESP").isEnabled();
                cfg.switchProfile("default");
                check("profile switch", kept, "copied profile keeps module state");
                check("no module errors (singleplayer)", moduleErrors.isEmpty(), moduleErrors.toString());
                if (VANILLA == null) {
                    finish(mc);
                } else {
                    leaveWorld(mc);
                    stage = 20;
                    wait = 20;
                }
            }
            // ============================== multiplayer ==============================
            case 20 -> {
                if (!waitFor(mc.world == null && mc.getServer() == null && mc.currentScreen instanceof TitleScreen, 600, "leave singleplayer")) return;
                connect(mc, VANILLA);
                stage = 21;
            }
            case 21 -> {
                if (!waitFor(inWorld(mc), 1200, "join vanilla server")) return;
                stage = 22;
                wait = 60;
            }
            case 22 -> {
                var session = PhantomClient.get().session();
                check("mp: vanilla server detected", session.isRemote() && "vanilla".equals(session.brand())
                                && PhantomClient.get().guard().context().name().equals("REMOTE_SERVER"),
                        "address=" + session.address() + " brand=" + session.brand() + " protocol=" + session.serverProtocol());
                Module storage = modules().get("StorageESP");
                check("mp: restricted module paused on join", storage.isSuspended() && !storage.isEnabled(),
                        "StorageESP: " + storage.getSuspendedReason());
                check("mp: restricted module refused", modules().get("ESP").setEnabled(true) != null, "ESP refused under default rules");
                Module zoom = modules().get("Zoom");
                zoom.setEnabled(true);
                boolean zoomOk = zoom.isEnabled();
                zoom.setEnabled(false);
                check("mp: cosmetic modules allowed", zoomOk && modules().get("Fullbright").isEnabled() && modules().get("HUD").isEnabled(),
                        "Zoom, Fullbright, HUD usable");
                shot(mc, "10-mp-vanilla-hud");
                ClickGuiScreen gui = new ClickGuiScreen();
                mc.setScreen(gui);
                setField(gui, "category", Category.VISUALS);
                stage = 23;
                wait = 40;
            }
            case 23 -> {
                shot(mc, "11-mp-vanilla-clickgui");
                setField(mc.currentScreen, "section", enumValue(mc.currentScreen, "SERVERS"));
                stage = 24;
                wait = 30;
            }
            case 24 -> {
                shot(mc, "12-mp-servers-panel");
                mc.setScreen(null);
                stableSince = System.currentTimeMillis();
                stage = 25;
                wait = 200; // stay connected for 10 s
            }
            case 25 -> {
                check("mp: stable session (10 s, no disconnect)", inWorld(mc) && moduleErrors.isEmpty(),
                        "connected " + (System.currentTimeMillis() - stableSince) / 1000 + " s, Phantom cost " +
                                String.format(java.util.Locale.ROOT, "%.2f ms/frame", PhantomClient.get().perf().frameCost()));
                // Kick through the server console to exercise the Reconnect button.
                Files.writeString(Path.of(CONSOLE), "kick PhantomTester Phantom reconnect test\n",
                        java.nio.file.StandardOpenOption.APPEND);
                stage = 26;
            }
            case 26 -> {
                if (!waitFor(mc.currentScreen instanceof DisconnectedScreen, 400, "kick -> disconnect screen")) return;
                ButtonWidget reconnect = findButton(mc.currentScreen, "Reconnect to");
                check("mp: reconnect button on disconnect screen", reconnect != null, reconnect == null ? "missing" : reconnect.getMessage().getString());
                if (reconnect != null) reconnect.onPress();
                stage = 27;
            }
            case 27 -> {
                if (!waitFor(inWorld(mc), 1200, "reconnect")) return;
                var p = PhantomClient.get().session().profile();
                check("mp: reconnect rejoins server", p != null && p.joins >= 2, "joins=" + (p == null ? 0 : p.joins));
                leaveWorld(mc);
                stage = 28;
            }
            case 28 -> {
                if (!waitFor(mc.world == null && mc.currentScreen instanceof TitleScreen, 400, "leave vanilla server")) return;
                connect(mc, POLICY);
                stage = 29;
            }
            case 29 -> {
                if (!waitFor(inWorld(mc) && !PhantomClient.get().session().policy().isDefault(), 1200, "join policy server + receive policy")) return;
                stage = 30;
                wait = 40;
            }
            case 30 -> {
                var session = PhantomClient.get().session();
                var policy = session.policy();
                check("mp: malformed/oversized payloads ignored, valid policy applied", inWorld(mc) && policy.allows("ESP") && policy.denies("Zoom"),
                        "allow=" + policy.allow() + " deny=" + policy.deny());
                check("mp: server-granted module allowed", modules().get("ESP").setEnabled(true) == null && modules().get("ESP").isEnabled(), "ESP on");
                check("mp: server-denied cosmetic refused", modules().get("Zoom").setEnabled(true) != null, "Zoom refused");
                check("mp: not-granted module still locked", modules().get("KillAura").setEnabled(true) != null
                        && modules().get("StorageESP").isSuspended(), "KillAura refused, StorageESP paused");
                session.store().setBlocked(session.address(), "ESP", true);
                stage = 31;
                wait = 5;
            }
            case 31 -> {
                Module esp = modules().get("ESP");
                boolean blocked = esp.isSuspended() && "Blocked in your profile for this server".equals(esp.getSuspendedReason());
                var session = PhantomClient.get().session();
                session.store().setBlocked(session.address(), "ESP", false);
                check("mp: user per-server block pauses a granted module", blocked, String.valueOf(esp.getSuspendedReason()));
                stage = 32;
                wait = 20;
            }
            case 32 -> {
                check("mp: unblock resumes it", modules().get("ESP").isEnabled(), "ESP resumed");
                shot(mc, "13-mp-policy-server");
                // Server switch: back to the vanilla server. The grant must not carry over.
                leaveWorld(mc);
                stage = 33;
            }
            case 33 -> {
                if (!waitFor(mc.world == null && mc.currentScreen instanceof TitleScreen, 400, "leave policy server")) return;
                connect(mc, VANILLA);
                stage = 34;
            }
            case 34 -> {
                if (!waitFor(inWorld(mc), 1200, "rejoin vanilla server")) return;
                stage = 35;
                wait = 20;
            }
            case 35 -> {
                var session = PhantomClient.get().session();
                check("mp: server switch resets policy", session.policy().isDefault() && modules().get("ESP").isSuspended(),
                        "ESP " + modules().get("ESP").getSuspendedReason());
                leaveWorld(mc);
                stage = 36;
            }
            case 36 -> {
                if (!waitFor(mc.world == null && mc.currentScreen instanceof TitleScreen, 400, "leave vanilla server (2)")) return;
                check("mp: paused modules resume after leaving", modules().get("ESP").isEnabled() && modules().get("StorageESP").isEnabled()
                        && !modules().get("ESP").isSuspended(), "ESP and StorageESP back on in the menu");
                if (OLD == null) {
                    finishMultiplayer(mc);
                    return;
                }
                connect(mc, OLD);
                stage = 37;
            }
            case 37 -> {
                if (!waitFor(mc.currentScreen instanceof DisconnectedScreen, 600, "old-version server rejects client")) return;
                String text = screenText(mc.currentScreen);
                check("mp: different-version server handled", text.contains("different Minecraft version") || text.contains("Phantom supports"),
                        text.length() > 160 ? text.substring(0, 160) : text);
                shot(mc, "14-mp-version-mismatch");
                finishMultiplayer(mc);
            }
            default -> {
            }
        }
    }

    private static final String VANILLA = System.getProperty("phantom.selftest.vanilla");
    private static final String POLICY = System.getProperty("phantom.selftest.policyServer");
    private static final String OLD = System.getProperty("phantom.selftest.oldServer");
    private static final String CONSOLE = System.getProperty("phantom.selftest.consoleFile");
    private long stableSince;
    private int waited;

    /** Waits for a condition; on timeout records a failure and jumps to the report. */
    private boolean waitFor(boolean condition, int timeoutTicks, String what) {
        if (condition) {
            waited = 0;
            return true;
        }
        if (++waited > timeoutTicks) {
            waited = 0;
            MinecraftClient mc = MinecraftClient.getInstance();
            check("wait: " + what, false, "timed out after " + timeoutTicks / 20 + " s (screen="
                    + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName()) + ")");
            finishMultiplayer(mc);
        }
        return false;
    }

    private static boolean inWorld(MinecraftClient mc) {
        return mc.player != null && mc.world != null && mc.getNetworkHandler() != null && mc.currentScreen == null;
    }

    private static void connect(MinecraftClient mc, String address) {
        ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(address),
                new ServerInfo("Phantom test " + address, address, ServerInfo.ServerType.OTHER), false, null);
    }

    private static void leaveWorld(MinecraftClient mc) {
        if (mc.world != null) mc.world.disconnect();
        mc.disconnect(new TitleScreen());
    }

    private static ButtonWidget findButton(Screen screen, String prefix) {
        for (var e : screen.children()) {
            if (e instanceof ButtonWidget b && b.getMessage().getString().startsWith(prefix)) return b;
        }
        return null;
    }

    private static String screenText(Screen screen) {
        StringBuilder sb = new StringBuilder();
        for (var e : screen.children()) if (e instanceof ClickableWidget w) sb.append(w.getMessage().getString()).append(" | ");
        return sb.toString();
    }

    private void finishMultiplayer(MinecraftClient mc) {
        if (stage == 99) return;
        check("no module errors (whole run)", moduleErrors.isEmpty(), moduleErrors.toString());
        finish(mc);
    }

    private ZombieEntity nearestZombie(MinecraftClient mc) {
        return mc.world.getEntitiesByClass(ZombieEntity.class, mc.player.getBoundingBox().expand(6), LivingEntity::isAlive)
                .stream().findFirst().orElse(null);
    }

    private boolean farZombieAlive(MinecraftClient mc) {
        return mc.world.getEntitiesByClass(ZombieEntity.class, mc.player.getBoundingBox().expand(20),
                z -> z.squaredDistanceTo(mc.player) > 100 && z.getHealth() >= z.getMaxHealth()).size() == 1;
    }

    private static void setField(Object o, String name, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, value);
    }

    private static Object enumValue(Object screen, String name) throws Exception {
        Field f = screen.getClass().getDeclaredField("section");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object v = Enum.valueOf((Class) f.getType(), name);
        return v;
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> uiExpanded(Object gui) throws Exception {
        Field f = gui.getClass().getDeclaredField("ui");
        f.setAccessible(true);
        Object ui = f.get(gui);
        return (Set<Object>) ui.getClass().getField("expanded").get(ui);
    }

    private void finish(MinecraftClient mc) {
        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        int failed = 0;
        for (String[] r : results) {
            JsonObject o = new JsonObject();
            o.addProperty("check", r[0]);
            o.addProperty("result", r[1]);
            o.addProperty("detail", r[2]);
            arr.add(o);
            if (r[1].equals("FAIL")) failed++;
        }
        out.addProperty("passed", results.size() - failed);
        out.addProperty("failed", failed);
        out.add("checks", arr);
        try {
            Files.writeString(Path.of(mc.runDirectory.getPath(), "phantom-selftest.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (Exception e) {
            LOG.error("Could not write report", e);
        }
        LOG.info("SELFTEST COMPLETE: {} passed, {} failed", results.size() - failed, failed);
        stage = 99;
        mc.scheduleStop();
    }
}
