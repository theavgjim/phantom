package dev.phantom.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.phantom.config.ConfigManager;
import dev.phantom.config.ConfigSerializer;
import dev.phantom.core.event.EventBus;
import dev.phantom.core.hud.HudElement;
import dev.phantom.core.hud.HudRegistry;
import dev.phantom.core.module.ModuleManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConfigManagerTest {
    static final class Fps extends HudElement {
        Fps() {
            super("fps", "FPS", 0.01, 0.1, true);
        }
    }

    @TempDir
    Path dir;

    private record Env(ModuleManager mm, HudRegistry hud, TestModules.Counter counter, Fps fps, ConfigManager cfg) {
    }

    private Env env() {
        ModuleManager mm = new ModuleManager(new EventBus());
        TestModules.Counter c = mm.register(new TestModules.Counter());
        HudRegistry hud = new HudRegistry();
        Fps fps = hud.register(new Fps());
        ConfigManager cfg = new ConfigManager(dir, mm, hud, Runnable::run);
        mm.addListener(new ModuleManager.Listener() {
            @Override
            public void onChanged(dev.phantom.core.module.Module module) {
                cfg.markDirty();
            }
        });
        hud.setChangeListener(cfg::markDirty);
        return new Env(mm, hud, c, fps, cfg);
    }

    @Test
    void roundTripPreservesEverything() {
        Env a = env();
        a.counter.setEnabled(true);
        a.counter.setKey(82);
        a.counter.range.set(4.5);
        a.counter.mode.set("C");
        a.counter.color.set(0x80FF0000);
        a.counter.blocks.setFromText("minecraft:diamond_ore, Minecraft:Ancient_Debris");
        a.fps.setPosition(0.5, 0.25);
        a.fps.scale.set(1.5);
        JsonObject json = ConfigSerializer.snapshot(a.mm, a.hud);

        Env b = env();
        ConfigSerializer.apply(JsonParser.parseString(json.toString()).getAsJsonObject(), b.mm, b.hud);
        assertTrue(b.counter.isEnabled());
        assertEquals(82, b.counter.getKey());
        assertEquals(4.5, b.counter.range.get());
        assertEquals("C", b.counter.mode.get());
        assertEquals(0x80FF0000, b.counter.color.get());
        assertEquals(List.of("minecraft:diamond_ore", "minecraft:ancient_debris"), b.counter.blocks.get());
        assertEquals(0.5, b.fps.x());
        assertEquals(1.5, b.fps.scale.get());
    }

    @Test
    void malformedValuesFallBackToDefaults() {
        Env e = env();
        JsonObject root = JsonParser.parseString("""
                {"modules":{"Counter":{"enabled":"yes","key":"x","settings":{"Range":"far","Color":"#zz","Mode":5}}},
                 "hud":{"fps":{"x":"left"}}}""").getAsJsonObject();
        ConfigSerializer.apply(root, e.mm, e.hud);
        assertFalse(e.counter.isEnabled());
        assertEquals(3.0, e.counter.range.get());
        assertEquals(0xFF112233, e.counter.color.get());
        assertEquals(0.01, e.fps.x());
    }

    @Test
    void initSaveSwitchExportImport() throws Exception {
        Env e = env();
        e.cfg.initAsync().get(5, TimeUnit.SECONDS);
        assertEquals("default", e.cfg.activeProfile());

        e.counter.range.set(5.0);
        assertTrue(e.cfg.isDirty());
        e.cfg.saveNow();
        assertTrue(Files.readString(e.cfg.profileFile("default")).contains("\"Range\": 5.0"));

        e.cfg.createProfile("PvE Test", false);
        e.cfg.switchProfile("PvE Test");
        assertEquals(3.0, e.counter.range.get(), "new profile starts from defaults");
        e.cfg.switchProfile("default");
        assertEquals(5.0, e.counter.range.get());

        Path exported = e.cfg.exportProfile("default");
        Path dropped = e.cfg.importDir().resolve(exported.getFileName());
        Files.copy(exported, dropped);
        String imported = e.cfg.importProfile(dropped);
        assertEquals("default 2", imported);
        assertTrue(e.cfg.listProfiles().containsAll(List.of("default", "default 2", "PvE Test")));

        assertThrows(IllegalArgumentException.class, () -> e.cfg.deleteProfile("default"));
        e.cfg.deleteProfile("PvE Test");
        assertFalse(e.cfg.listProfiles().contains("PvE Test"));
        assertFalse(Files.list(e.cfg.backupsDir()).toList().isEmpty(), "delete leaves a backup");
        e.cfg.close();
    }

    @Test
    void launcherRequestSelectsProfileOnce() throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("launcher.json"), "{\"activeProfile\":\"Creative\"}");
        Env e = env();
        e.cfg.initAsync().get(5, TimeUnit.SECONDS);
        assertEquals("Creative", e.cfg.activeProfile());
        assertFalse(Files.exists(dir.resolve("launcher.json")));
        e.cfg.close();
        assertTrue(Files.readString(dir.resolve("phantom.json")).contains("Creative"));
    }

    @Test
    void importRejectsNonProfiles() throws Exception {
        Env e = env();
        Path bad = dir.resolve("bad.json");
        Files.writeString(bad, "{\"hello\":1}");
        assertThrows(java.io.IOException.class, () -> e.cfg.importProfile(bad));
    }
}
