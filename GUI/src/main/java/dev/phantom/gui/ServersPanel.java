package dev.phantom.gui;

import dev.phantom.config.ServerProfileStore;
import dev.phantom.core.module.Module;
import dev.phantom.core.policy.PolicyEvaluator;
import dev.phantom.core.policy.ServerPolicy;
import dev.phantom.core.theme.Theme;
import dev.phantom.integration.PhantomClient;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.server.ServerSession;
import dev.phantom.integration.util.Colors;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Current connection, server policy, per-server profile (config profile + extra blocks), known servers. */
final class ServersPanel {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
    private String confirmForget;
    private long copied;

    float render(Ui ui, DrawContext ctx, Theme t, float x, float y, float w) {
        PhantomClient client = PhantomClient.get();
        ServerSession s = client.session();
        float start = y;

        Widgets.sectionTitle(ui, ctx, t, "Current connection", x, y);
        y += 10;
        List<String[]> rows = new ArrayList<>();
        switch (s.kind()) {
            case NONE -> rows.add(new String[]{"Status", "Not connected"});
            case SINGLEPLAYER -> rows.add(new String[]{"Singleplayer", s.name() + "  ·  every module available"});
            case LAN_HOST -> rows.add(new String[]{"LAN host", s.name() + "  ·  your world, every module available"});
            case REMOTE -> {
                rows.add(new String[]{"Server", s.name() + (s.name().equals(s.address()) ? "" : "  (" + s.address() + ")")});
                rows.add(new String[]{"Software", s.brand().isEmpty() ? "unknown" : s.brand()});
                int ours = SharedConstants.getProtocolVersion();
                String proto = s.serverProtocol() == ours ? "protocol " + ours + " (matches)" : "protocol " + s.serverProtocol() + " (translated by the server)";
                rows.add(new String[]{"Version", (s.version().isEmpty() ? "-" : s.version()) + "  ·  " + proto});
                long secs = Duration.ofMillis(System.currentTimeMillis() - s.joinedAt()).toSeconds();
                rows.add(new String[]{"Session", (s.latency() >= 0 ? s.latency() + " ms ping  ·  " : "") + (secs / 60) + "m " + (secs % 60) + "s"});
                ServerPolicy p = s.policy();
                rows.add(new String[]{"Policy", p.isDefault() ? "none sent - default rules (cosmetic modules only)" : "phantom:policy received"});
                if (!p.isDefault()) {
                    if (!p.message().isBlank()) rows.add(new String[]{"Message", p.message()});
                    rows.add(new String[]{"Allows", p.allow().isEmpty() ? "-" : String.join(", ", p.allow())});
                    rows.add(new String[]{"Denies", p.deny().isEmpty() ? "-" : String.join(", ", p.deny())});
                }
            }
        }
        float cardH = rows.size() * 11 + 8;
        Render2D.roundedRect(ctx, x, y, w, cardH, 5, ui.c(t.panelRaised()));
        float ry = y + 5;
        for (String[] r : rows) {
            Render2D.text(ctx, r[0], x + 8, ry, ui.c(t.textDim()), false, 0.85f);
            Render2D.text(ctx, Render2D.trim(r[1], (int) ((w - 80) / 0.85f)), x + 70, ry, ui.c(t.text()), false, 0.85f);
            ry += 11;
        }
        y += cardH + 4;
        Widgets.button(ui, ctx, t, "srv:diag", x, y, 120, 13, copied > System.currentTimeMillis() ? "Copied!" : "Copy diagnostics", false, () -> {
            net.minecraft.client.MinecraftClient.getInstance().keyboard.setClipboard(dev.phantom.integration.server.Diagnostics.report(client));
            copied = System.currentTimeMillis() + 1500;
        });
        Render2D.text(ctx, "Paste it into a bug report or a message to the server staff.", x + 126, y + 3.5f, ui.c(t.textDim()), false, 0.75f);
        y += 21;

        if (s.isRemote() && s.profile() != null) {
            ServerProfileStore.Profile prof = s.profile();
            Widgets.sectionTitle(ui, ctx, t, "This server's profile", x, y);
            y += 10;
            // Config profile to load automatically on join (cycles through: none, profiles...).
            List<String> choices = new ArrayList<>();
            choices.add("(keep current)");
            choices.addAll(client.core().config().listProfiles());
            String current = prof.configProfile == null ? choices.get(0) : prof.configProfile;
            Render2D.text(ctx, "Profile on join", x, y + 3, ui.c(t.text()), false);
            float bw = Math.min(140, w * 0.5f);
            Widgets.button(ui, ctx, t, "srv:profile", x + w - bw, y, bw, 13, current + "  ▸", false, () -> {
                int i = (choices.indexOf(current) + 1) % choices.size();
                prof.configProfile = i == 0 ? null : choices.get(i);
                s.store().save();
            });
            y += 18;
            Render2D.text(ctx, "Turn modules off for this server (you can only restrict, never unlock):", x, y, ui.c(t.textDim()), false, 0.8f);
            y += 11;
            List<Module> usable = new ArrayList<>();
            for (Module m : client.core().modules().all()) {
                if (!m.isToggleable()) continue;
                String r = PolicyEvaluator.deny(m, PolicyEvaluator.Context.REMOTE_SERVER, s.policy(), java.util.Set.of());
                if (r == null) usable.add(m);
            }
            int cols = 2;
            float colW = (w - 6) / cols;
            for (int i = 0; i < usable.size(); i++) {
                Module m = usable.get(i);
                float mx = x + (i % cols) * (colW + 6), my = y + (i / cols) * 15;
                boolean allowed = !prof.blocked.contains(ServerPolicy.key(m.getName()));
                Render2D.text(ctx, m.getName(), mx, my + 3, ui.c(allowed ? t.text() : t.textDim()), false, 0.9f);
                Widgets.toggle(ui, ctx, t, "srv:blk:" + m.getName(), mx + colW - 24, my + 1, allowed, () -> {
                    s.store().setBlocked(s.address(), m.getName(), allowed);
                    s.store().save();
                });
            }
            y += ((usable.size() + cols - 1) / cols) * 15 + 8;
        }

        List<ServerProfileStore.Profile> known = s.store().all();
        Widgets.sectionTitle(ui, ctx, t, "Known servers (" + known.size() + ")", x, y);
        y += 10;
        if (known.isEmpty()) {
            Render2D.text(ctx, "Servers you join appear here with their detected software and version.", x, y + 2, ui.c(t.textDim()), false, 0.8f);
            y += 12;
        }
        for (ServerProfileStore.Profile p : known) {
            float rh = 24;
            boolean here = s.isRemote() && p.address.equals(s.address());
            Render2D.roundedRect(ctx, x, y, w, rh, 4, ui.c(t.panelRaised()));
            if (here) Render2D.roundedRect(ctx, x + 1, y + 6, 2, rh - 12, 1, ui.c(t.accent()));
            String title = (p.name == null || p.name.isBlank() ? p.address : p.name) + (here ? "  ·  connected" : "");
            Render2D.text(ctx, Render2D.trim(title, (int) w - 70), x + 8, y + 3, ui.c(t.text()), false);
            String sub = (p.brand.isBlank() ? "?" : p.brand) + (p.version.isBlank() ? "" : "  ·  " + p.version)
                    + "  ·  " + p.joins + " joins" + (p.lastJoined > 0 ? "  ·  " + DATE.format(Instant.ofEpochMilli(p.lastJoined)) : "")
                    + (p.configProfile != null ? "  ·  profile " + p.configProfile : "")
                    + (p.blocked.isEmpty() ? "" : "  ·  " + p.blocked.size() + " blocked");
            Render2D.text(ctx, Render2D.trim(sub, (int) ((w - 70) / 0.75f)), x + 8, y + 13, ui.c(t.textDim()), false, 0.75f);
            if (!here) {
                boolean confirming = p.address.equals(confirmForget);
                Widgets.button(ui, ctx, t, "srv:forget:" + p.address, x + w - 52, y + 5.5f, 46, 13, confirming ? "Sure?" : "Forget", confirming, () -> {
                    if (!p.address.equals(confirmForget)) {
                        confirmForget = p.address;
                        return;
                    }
                    confirmForget = null;
                    s.store().remove(p.address);
                    s.store().save();
                });
            }
            y += rh + 3;
        }
        y += 4;
        Render2D.text(ctx, "Restricted modules (ESP, Freecam, KillAura, Fly...) only run on servers that allow them.",
                x, y, ui.c(Colors.withAlpha(t.textDim(), 200)), false, 0.75f);
        y += 10;
        return y - start;
    }
}
