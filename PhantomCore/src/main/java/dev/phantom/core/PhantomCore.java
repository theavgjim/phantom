package dev.phantom.core;

import dev.phantom.config.ConfigManager;
import dev.phantom.core.event.EventBus;
import dev.phantom.core.hud.HudRegistry;
import dev.phantom.core.module.ModuleManager;
import dev.phantom.core.theme.ThemeManager;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/** Wires the Minecraft-agnostic services together. One instance per client session. */
public final class PhantomCore {
    public static final String NAME = "Phantom";

    private final EventBus eventBus = new EventBus();
    private final ModuleManager modules = new ModuleManager(eventBus);
    private final ThemeManager themes = new ThemeManager();
    private final HudRegistry hud = new HudRegistry();
    private final ConfigManager config;
    private final String version;

    public PhantomCore(String version, Path configRoot, Executor mainThread) {
        this.version = version;
        this.config = new ConfigManager(configRoot, modules, hud, mainThread);
        modules.addListener(new ModuleManager.Listener() {
            @Override
            public void onChanged(dev.phantom.core.module.Module module) {
                config.markDirty();
            }
        });
        hud.setChangeListener(config::markDirty);
    }

    public EventBus events() {
        return eventBus;
    }

    public ModuleManager modules() {
        return modules;
    }

    public ThemeManager themes() {
        return themes;
    }

    public HudRegistry hud() {
        return hud;
    }

    public ConfigManager config() {
        return config;
    }

    public String version() {
        return version;
    }
}
