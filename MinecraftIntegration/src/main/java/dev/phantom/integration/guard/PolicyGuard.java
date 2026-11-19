package dev.phantom.integration.guard;

import dev.phantom.core.module.ActivationGuard;
import dev.phantom.core.module.Module;
import dev.phantom.core.module.ModuleManager;
import dev.phantom.core.policy.PolicyEvaluator;
import dev.phantom.core.policy.PolicyEvaluator.Context;
import dev.phantom.integration.server.ServerSession;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies {@link PolicyEvaluator} to the live game: restricted modules run in private worlds and
 * only where a server permits them; anything a server denies is paused. Paused modules resume
 * automatically when the context allows them again. Purely client-side: nothing is hidden from,
 * sent to, or worked around on the server.
 */
public final class PolicyGuard implements ActivationGuard {
    private final MinecraftClient mc;
    private final ServerSession session;

    public PolicyGuard(MinecraftClient mc, ServerSession session) {
        this.mc = mc;
        this.session = session;
    }

    public Context context() {
        if (mc.world == null) return Context.MENU;
        return mc.isIntegratedServerRunning() ? Context.PRIVATE_WORLD : Context.REMOTE_SERVER;
    }

    @Override
    public String deny(Module module) {
        return PolicyEvaluator.deny(module, context(), session.policy(), session.blocked());
    }

    public record Change(List<Module> paused, List<Module> resumed) {
        public boolean isEmpty() {
            return paused.isEmpty() && resumed.isEmpty();
        }
    }

    /** Pauses what is not allowed and resumes what is allowed again. Cheap: one pass over ~30 modules. */
    public Change enforce(ModuleManager modules) {
        List<Module> paused = new ArrayList<>(), resumed = new ArrayList<>();
        for (Module m : modules.all()) {
            if (m.isEnabled()) {
                String reason = deny(m);
                if (reason != null) {
                    m.suspend(reason);
                    paused.add(m);
                }
            } else if (m.isSuspended() && m.resume()) {
                resumed.add(m);
            }
        }
        return new Change(paused, resumed);
    }

    /** Modules that cannot run right now (for GUI badges and the HUD). */
    public List<Module> locked(ModuleManager modules) {
        List<Module> out = new ArrayList<>();
        if (context() != Context.REMOTE_SERVER) return out;
        for (Module m : modules.all()) if (m.isToggleable() && deny(m) != null) out.add(m);
        return out;
    }
}
