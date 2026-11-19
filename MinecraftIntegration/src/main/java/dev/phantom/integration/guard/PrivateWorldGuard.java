package dev.phantom.integration.guard;

import dev.phantom.core.module.ActivationGuard;
import dev.phantom.core.module.Module;
import dev.phantom.core.module.ModuleManager;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Policy: modules flagged {@code privateWorldOnly} (combat training and movement testing) may only
 * run while the game is connected to its own integrated server - a singleplayer world, or a world
 * the player is hosting over LAN. On any remote server they are refused and switched off.
 *
 * <p>This is a usage restriction, not a hiding mechanism: nothing is concealed from servers.</p>
 */
public final class PrivateWorldGuard implements ActivationGuard {
    public static final String REASON = "Only available in singleplayer / private worlds";

    public enum Context { MENU, PRIVATE_WORLD, REMOTE_SERVER }

    private final MinecraftClient mc;

    public PrivateWorldGuard(MinecraftClient mc) {
        this.mc = mc;
    }

    public Context context() {
        if (mc.world == null) return Context.MENU;
        return mc.isIntegratedServerRunning() ? Context.PRIVATE_WORLD : Context.REMOTE_SERVER;
    }

    @Override
    public String deny(Module module) {
        if (module.isPrivateWorldOnly() && context() == Context.REMOTE_SERVER) return REASON;
        return null;
    }

    /** Called every tick before modules run. Returns the modules it switched off. */
    public List<Module> enforce(ModuleManager modules, Consumer<Module> onDisabled) {
        if (context() != Context.REMOTE_SERVER) return List.of();
        List<Module> off = new ArrayList<>();
        for (Module m : modules.all()) {
            if (m.isPrivateWorldOnly() && m.isEnabled()) {
                m.setEnabled(false);
                off.add(m);
                onDisabled.accept(m);
            }
        }
        return off;
    }
}
