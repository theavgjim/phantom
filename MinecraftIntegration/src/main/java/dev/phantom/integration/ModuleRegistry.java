package dev.phantom.integration;

import dev.phantom.core.module.ModuleManager;
import dev.phantom.modules.combat.AimBot;
import dev.phantom.modules.combat.Criticals;
import dev.phantom.modules.combat.KillAura;
import dev.phantom.modules.combat.Reach;
import dev.phantom.modules.combat.TargetHud;
import dev.phantom.modules.misc.ClickGuiModule;
import dev.phantom.modules.misc.HudModule;
import dev.phantom.modules.misc.NotificationsModule;
import dev.phantom.modules.misc.Zoom;
import dev.phantom.modules.movement.BunnyHop;
import dev.phantom.modules.movement.Fly;
import dev.phantom.modules.movement.NoSlow;
import dev.phantom.modules.movement.Speed;
import dev.phantom.modules.movement.Spider;
import dev.phantom.modules.player.AutoSprint;
import dev.phantom.modules.player.AutoTool;
import dev.phantom.modules.visuals.BlockEsp;
import dev.phantom.modules.visuals.DistanceIndicators;
import dev.phantom.modules.visuals.Esp;
import dev.phantom.modules.visuals.EspColors;
import dev.phantom.modules.visuals.Freecam;
import dev.phantom.modules.visuals.Fullbright;
import dev.phantom.modules.visuals.Highlight;
import dev.phantom.modules.visuals.Nametags;
import dev.phantom.modules.visuals.StorageEsp;
import dev.phantom.modules.visuals.Tracers;

/** Single place where every module is registered. Order = default display order in the GUI. */
final class ModuleRegistry {
    private ModuleRegistry() {
    }

    static void registerAll(ModuleManager m) {
        // Combat (private worlds only)
        m.register(new KillAura());
        m.register(new AimBot());
        m.register(new Criticals());
        m.register(new Reach());
        m.register(new TargetHud());
        // Visuals
        m.register(new EspColors());
        m.register(new Esp());
        m.register(new StorageEsp());
        m.register(new BlockEsp());
        m.register(new Tracers());
        m.register(new Nametags());
        m.register(new Highlight());
        m.register(new DistanceIndicators());
        m.register(new Fullbright());
        m.register(new Freecam());
        // Movement (private worlds only)
        m.register(new Fly());
        m.register(new Speed());
        m.register(new BunnyHop());
        m.register(new Spider());
        m.register(new NoSlow());
        // Player
        m.register(new AutoSprint());
        m.register(new AutoTool());
        // Misc
        m.register(new ClickGuiModule());
        m.register(new HudModule());
        m.register(new NotificationsModule());
        m.register(new Zoom());
    }
}
