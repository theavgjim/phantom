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
import net.minecraft.client.gui.screen.Screen;
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
                check("no module errors", moduleErrors.isEmpty(), moduleErrors.toString());
                finish(mc);
            }
            default -> {
            }
        }
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
