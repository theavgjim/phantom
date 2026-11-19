package dev.phantom.integration.server;

import dev.phantom.core.module.Module;
import dev.phantom.integration.PhantomClient;
import net.minecraft.SharedConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Human-readable compatibility report for the current connection (copied from the Servers page, logged on join). */
public final class Diagnostics {
    private Diagnostics() {
    }

    public static String report(PhantomClient c) {
        ServerSession s = c.session();
        StringBuilder sb = new StringBuilder();
        sb.append("Phantom Client ").append(c.core().version())
                .append(" | Minecraft ").append(SharedConstants.getGameVersion().getName())
                .append(" (protocol ").append(SharedConstants.getProtocolVersion()).append(")\n");
        sb.append("Context: ").append(c.guard().context()).append(" (").append(s.kind()).append(")\n");
        if (s.isRemote()) {
            sb.append("Server: ").append(s.name()).append(" [").append(s.address()).append("]\n");
            sb.append("Software: ").append(s.brand().isEmpty() ? "unknown" : s.brand())
                    .append(" | version: ").append(s.version().isEmpty() ? "-" : s.version())
                    .append(" | server protocol: ").append(s.serverProtocol()).append('\n');
            sb.append("Ping: ").append(s.latency()).append(" ms\n");
            var p = s.policy();
            sb.append("Policy: ").append(p.isDefault() ? "none sent (default rules: cosmetic modules only)" : "phantom:policy")
                    .append(p.message().isBlank() ? "" : " - \"" + p.message() + "\"").append('\n');
            if (!p.isDefault()) sb.append("  allow=").append(p.allow()).append(" deny=").append(p.deny()).append('\n');
            sb.append("Your blocks here: ").append(s.blocked().isEmpty() ? "none" : s.blocked()).append('\n');
        }
        List<String> locked = new ArrayList<>(), paused = new ArrayList<>(), on = new ArrayList<>();
        for (Module m : c.core().modules().all()) {
            if (!m.isToggleable()) continue;
            String reason = c.guard().deny(m);
            if (m.isSuspended()) paused.add(m.getName() + " (" + m.getSuspendedReason() + ")");
            else if (reason != null) locked.add(m.getName() + " (" + reason + ")");
            else if (m.isEnabled()) on.add(m.getName());
        }
        sb.append("Enabled: ").append(on.isEmpty() ? "none" : String.join(", ", on)).append('\n');
        sb.append("Paused (you had them on): ").append(paused.isEmpty() ? "none" : String.join(", ", paused)).append('\n');
        sb.append("Locked here: ").append(locked.isEmpty() ? "none" : String.join(", ", locked)).append('\n');
        sb.append(String.format(Locale.ROOT, "Phantom cost: %.2f ms/frame, %.2f ms/tick%n",
                c.perf().frameCost(), c.perf().average(dev.phantom.integration.util.PerfMonitor.Section.TICK)));
        sb.append("Network: Phantom sends no packets of its own; it only listens on phantom:policy.\n");
        return sb.toString();
    }

    /** One line for the log (shown in the launcher console). */
    public static String summary(PhantomClient c) {
        ServerSession s = c.session();
        List<String> locked = new ArrayList<>();
        for (Module m : c.guard().locked(c.core().modules())) locked.add(m.getName());
        return "Server " + s.address() + " (" + (s.brand().isEmpty() ? "?" : s.brand()) + "), policy: "
                + (s.policy().isDefault() ? "default" : "phantom:policy") + ", locked: "
                + (locked.isEmpty() ? "none" : String.join(", ", locked));
    }
}
