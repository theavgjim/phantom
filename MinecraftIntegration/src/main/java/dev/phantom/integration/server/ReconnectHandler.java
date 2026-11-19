package dev.phantom.integration.server;

import dev.phantom.integration.mixin.DisconnectedScreenAccessor;
import dev.phantom.modules.misc.Reconnect;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.function.Supplier;

/** Adds "Reconnect" (and a clear version hint) to the vanilla disconnect screen. */
public final class ReconnectHandler {
    private static final String[] NEVER_AUTO = {"kick", "ban", "whitelist", "white-list", "white list", "not allowed",
            "outdated", "incompatible", "duplicate", "logged in from another location", "full"};
    private static final String[] CONNECTION_LOSS = {"timed out", "timeout", "connection", "closed", "restart",
            "lost", "end of stream", "reset", "refused", "io.netty", "unreachable", "shutting down"};

    private final MinecraftClient mc;
    private final ServerSession session;
    private final Supplier<Reconnect> settings;
    private int attempts;

    public ReconnectHandler(MinecraftClient mc, ServerSession session, Supplier<Reconnect> settings) {
        this.mc = mc;
        this.session = session;
        this.settings = settings;
    }

    public void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof DisconnectedScreen ds) decorate(ds, w, h);
        });
    }

    /** A successful join resets the auto-reconnect budget. */
    public void onJoin() {
        attempts = 0;
    }

    private void decorate(DisconnectedScreen screen, int width, int height) {
        ServerSession.Target target = session.lastTarget();
        if (target == null) return;
        String reason = reasonOf(screen);
        int bottom = 0;
        for (ClickableWidget b : Screens.getButtons(screen)) bottom = Math.max(bottom, b.getY() + b.getHeight());
        int y = Math.min(height - 46, bottom + 4);

        ServerInfo info = target.info();
        String label = info != null && info.name != null && !info.name.isBlank() ? info.name : target.address().getAddress();
        String lower = reason.toLowerCase(Locale.ROOT);
        boolean versionReject = lower.contains("incompatible") || lower.contains("outdated");
        if ((info != null && !ServerSession.protocolMatches(info.protocolVersion)) || versionReject) {
            String v = info != null && info.version != null && !info.version.getString().isBlank()
                    ? info.version.getString() : "a different Minecraft version";
            Text hint = Text.literal("This server runs " + v + ". Phantom supports Minecraft "
                    + SharedConstants.getGameVersion().getName() + " (or servers with ViaVersion).").formatted(Formatting.GOLD);
            TextWidget tw = new TextWidget(0, y + 26, width, 12, hint, mc.textRenderer);
            Screens.getButtons(screen).add(tw);
        }

        ButtonWidget button = ButtonWidget.builder(Text.literal("Reconnect to " + trim(label)), b -> reconnect(target))
                .dimensions(width / 2 - 100, y, 200, 20).build();
        Screens.getButtons(screen).add(button);

        Reconnect cfg = settings.get();
        if (cfg != null && cfg.auto.isOn() && attempts < cfg.attempts.getInt() && isConnectionLoss(reason)) {
            int delayTicks = (int) (cfg.delay.getDouble() * 20 * Math.pow(2, attempts));
            int[] left = {delayTicks};
            ScreenEvents.afterTick(screen).register(s -> {
                if (--left[0] <= 0) {
                    attempts++;
                    reconnect(target);
                } else if (left[0] % 20 == 0) {
                    button.setMessage(Text.literal("Reconnecting in " + (left[0] / 20) + "s (attempt " + (attempts + 1)
                            + "/" + cfg.attempts.getInt() + ") - click to go now"));
                }
            });
        }
    }

    public void reconnect(ServerSession.Target target) {
        ServerInfo info = target.info() != null ? target.info()
                : new ServerInfo(target.address().getAddress(), target.address().getAddress() + ":" + target.address().getPort(), ServerInfo.ServerType.OTHER);
        Screen parent = new MultiplayerScreen(new TitleScreen());
        ConnectScreen.connect(parent, mc, target.address(), info, false, null);
    }

    static boolean isConnectionLoss(String reason) {
        String r = reason.toLowerCase(Locale.ROOT);
        for (String s : NEVER_AUTO) if (r.contains(s)) return false;
        for (String s : CONNECTION_LOSS) if (r.contains(s)) return true;
        return false;
    }

    private static String reasonOf(DisconnectedScreen screen) {
        try {
            return ((DisconnectedScreenAccessor) screen).getPhantomInfo().reason().getString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String trim(String s) {
        return s.length() > 24 ? s.substring(0, 23) + "..." : s;
    }
}
