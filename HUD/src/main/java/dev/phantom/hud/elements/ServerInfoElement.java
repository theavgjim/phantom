package dev.phantom.hud.elements;

import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.hud.ScreenElement;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.server.ServerSession;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Server name, detected brand/version, ping, locked-module count and Phantom's own frame cost. */
public final class ServerInfoElement extends ScreenElement {
    private final BooleanSetting ping = add(new BooleanSetting("Ping", "", true));
    private final BooleanSetting brand = add(new BooleanSetting("Brand & Version", "", true));
    private final BooleanSetting perf = add(new BooleanSetting("Phantom Cost", "Time Phantom adds per frame", false));

    public ServerInfoElement() {
        super("server", "Server Info", 0.004, 0.11, true);
    }

    @Override
    public void render(DrawContext ctx, float tickDelta, boolean editor, boolean right) {
        var style = style();
        ServerSession s = client().session();
        List<String[]> lines = new ArrayList<>(); // {label, value}
        switch (s.kind()) {
            case REMOTE -> {
                lines.add(new String[]{"Server ", s.name()});
                if (brand.isOn()) {
                    String b = s.brand().isEmpty() ? "?" : s.brand();
                    lines.add(new String[]{"", b + (s.version().isEmpty() ? "" : "  ·  " + s.version())});
                }
                int locked = client().guard().locked(client().core().modules()).size();
                String p = ping.isOn() && s.latency() >= 0 ? s.latency() + " ms" : "";
                String policy = s.policy().isDefault() ? "default rules" : "server policy";
                lines.add(new String[]{"", (p.isEmpty() ? "" : p + "  ·  ") + policy + (locked > 0 ? "  ·  " + locked + " locked" : "")});
            }
            case SINGLEPLAYER -> lines.add(new String[]{"World ", s.name()});
            case LAN_HOST -> lines.add(new String[]{"LAN host ", s.name()});
            default -> lines.add(new String[]{"Server ", editor ? "play.example.net" : "-"});
        }
        if (perf.isOn()) {
            lines.add(new String[]{"Phantom ", String.format(Locale.ROOT, "%.2f ms/frame", client().perf().frameCost())});
        }
        float w = 0;
        for (String[] l : lines) w = Math.max(w, Render2D.width(l[0] + l[1]));
        width = w + 8;
        height = lines.size() * 10 + 3;
        Render2D.roundedRect(ctx, 0, 0, width, height, 3, style.backgroundColor());
        float y = 2.5f;
        for (String[] l : lines) {
            Render2D.text(ctx, l[0], 4, y, style.accent(0.15f), style.shadow.isOn());
            Render2D.text(ctx, Render2D.trim(l[1], 220), 4 + Render2D.width(l[0]), y,
                    l[0].isEmpty() ? client().theme().textDim() : style.textColor.argb(), style.shadow.isOn());
            y += 10;
        }
    }
}
