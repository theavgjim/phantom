package dev.phantom.gui;

import dev.phantom.config.ConfigManager;
import dev.phantom.config.ProfileNames;
import dev.phantom.core.theme.Theme;
import dev.phantom.integration.PhantomClient;
import dev.phantom.integration.render.Render2D;
import dev.phantom.integration.util.Colors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Create / load / duplicate / export / delete profiles, import, and backups. */
final class ProfilesPanel {
    private final TextInput newName = new TextInput(32, "New profile name");
    private List<String> profiles = List.of();
    private List<Path> imports = List.of();
    private String message = "";
    private int messageColor;
    private String confirmDelete;

    void refresh() {
        ConfigManager cfg = PhantomClient.get().core().config();
        profiles = cfg.listProfiles();
        imports = cfg.importCandidates();
    }

    private void ok(String m) {
        message = m;
        messageColor = 0xFF66BB6A;
    }

    private void fail(String m) {
        message = m;
        messageColor = 0xFFEF5350;
    }

    private void guarded(Action a) {
        try {
            a.run();
        } catch (Exception e) {
            fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        refresh();
    }

    private interface Action {
        void run() throws Exception;
    }

    float render(Ui ui, DrawContext ctx, Theme t, float x, float y, float w) {
        ConfigManager cfg = PhantomClient.get().core().config();
        float start = y;

        // Create
        Widgets.sectionTitle(ui, ctx, t, "New profile", x, y);
        y += 10;
        float bw = 58;
        Widgets.textField(ui, ctx, t, x, y, w - bw * 2 - 8, 15, newName);
        Widgets.button(ui, ctx, t, "p:create", x + w - bw * 2 - 4, y + 1, bw, 13, "Defaults", false, () -> guarded(() -> {
            create(cfg, false);
        }));
        Widgets.button(ui, ctx, t, "p:copy", x + w - bw, y + 1, bw, 13, "Save as", true, () -> guarded(() -> {
            create(cfg, true);
        }));
        y += 22;

        // List
        Widgets.sectionTitle(ui, ctx, t, "Profiles (" + profiles.size() + ")", x, y);
        y += 10;
        for (String p : profiles) {
            boolean active = p.equals(cfg.activeProfile());
            float rh = 20;
            boolean hover = ui.hovered(x, y, w, rh);
            Render2D.roundedRect(ctx, x, y, w, rh, 4, ui.c(hover ? Colors.brighten(t.panelRaised(), 0.04f) : t.panelRaised()));
            if (active) Render2D.roundedRect(ctx, x + 1, y + 5, 2, rh - 10, 1, ui.c(t.accent()));
            Render2D.text(ctx, Render2D.trim(p, (int) (w - 220)), x + 8, y + 6, ui.c(t.text()), false);
            if (active) {
                float px = x + 12 + Render2D.width(Render2D.trim(p, (int) (w - 220)));
                Render2D.roundedRect(ctx, px, y + 6, 32, 9, 4, ui.c(Colors.withAlpha(t.accent(), 80)));
                Render2D.text(ctx, "active", px + 4, y + 7.5f, ui.c(t.text()), false, 0.7f);
            }
            float bx = x + w - 4;
            float b = 40;
            bx -= b;
            boolean confirming = p.equals(confirmDelete);
            if (!active) {
                Widgets.button(ui, ctx, t, "p:del:" + p, bx, y + 3.5f, b, 13, confirming ? "Sure?" : "Delete", confirming, () -> {
                    if (!p.equals(confirmDelete)) {
                        confirmDelete = p;
                        return;
                    }
                    confirmDelete = null;
                    guarded(() -> {
                        cfg.deleteProfile(p);
                        ok("Deleted '" + p + "' (a backup was kept)");
                    });
                });
            }
            bx -= b + 3;
            Widgets.button(ui, ctx, t, "p:exp:" + p, bx, y + 3.5f, b, 13, "Export", false, () -> guarded(() -> {
                Path out = cfg.exportProfile(p);
                ok("Exported to exports/" + out.getFileName());
            }));
            bx -= b + 3;
            Widgets.button(ui, ctx, t, "p:dup:" + p, bx, y + 3.5f, b, 13, "Copy", false, () -> guarded(() -> {
                String name = uniqueName(p + " copy");
                if (active) cfg.saveNow();
                Files.copy(cfg.profileFile(p), cfg.profileFile(name));
                ok("Copied to '" + name + "'");
            }));
            if (!active) {
                bx -= b + 3;
                Widgets.button(ui, ctx, t, "p:load:" + p, bx, y + 3.5f, b, 13, "Load", true, () -> guarded(() -> {
                    cfg.switchProfile(p);
                    ok("Loaded '" + p + "'");
                }));
            }
            y += rh + 3;
        }
        y += 6;

        // Import
        Widgets.sectionTitle(ui, ctx, t, "Import", x, y);
        y += 10;
        if (imports.isEmpty()) {
            Render2D.text(ctx, "Drop .json profiles into the import folder, then press Refresh.", x, y + 2, ui.c(t.textDim()), false, 0.8f);
            y += 12;
        }
        for (Path file : imports) {
            String n = file.getFileName().toString();
            Render2D.text(ctx, Render2D.trim(n, (int) w - 60), x + 2, y + 3, ui.c(t.text()), false);
            Widgets.button(ui, ctx, t, "p:imp:" + n, x + w - 50, y, 50, 13, "Import", true, () -> guarded(() -> {
                String name = cfg.importProfile(file);
                Files.deleteIfExists(file);
                ok("Imported as '" + name + "'");
            }));
            y += 16;
        }
        float qb = (w - 9) / 4f;
        Widgets.button(ui, ctx, t, "p:openimp", x, y, qb, 13, "Import dir", false, () -> open(cfg.importDir()));
        Widgets.button(ui, ctx, t, "p:openexp", x + qb + 3, y, qb, 13, "Exports", false, () -> open(cfg.exportsDir()));
        Widgets.button(ui, ctx, t, "p:openroot", x + (qb + 3) * 2, y, qb, 13, "Config", false, () -> open(cfg.root()));
        Widgets.button(ui, ctx, t, "p:refresh", x + (qb + 3) * 3, y, qb, 13, "Refresh", false, () -> {
            refresh();
            ok("Refreshed");
        });
        y += 22;

        // Backups
        Widgets.sectionTitle(ui, ctx, t, "Backups", x, y);
        y += 10;
        float hb = (w - 6) / 3f;
        Widgets.button(ui, ctx, t, "p:backup", x, y, hb, 13, "Backup now", false, () -> guarded(() -> {
            Path p = cfg.backupNow();
            ok(p == null ? "Nothing to back up yet" : "Backup saved: " + p.getFileName());
        }));
        Widgets.button(ui, ctx, t, "p:restore", x + hb + 3, y, hb, 13, "Restore latest", false, () -> guarded(() -> {
            if (cfg.restoreLatestBackup()) ok("Restored the latest backup");
            else fail("No backups for this profile yet");
        }));
        Widgets.button(ui, ctx, t, "p:openbak", x + (hb + 3) * 2, y, hb, 13, "Open backups", false, () -> open(cfg.backupsDir()));
        y += 20;

        if (!message.isEmpty()) {
            Render2D.text(ctx, Render2D.trim(message, (int) w), x, y, ui.c(messageColor), false);
            y += 12;
        }
        return y - start;
    }

    private void create(ConfigManager cfg, boolean copy) {
        String name = newName.text.trim();
        if (!ProfileNames.isValid(name)) {
            fail("Use 1-32 letters, digits, spaces, '-' or '_'");
            return;
        }
        cfg.createProfile(name, copy);
        newName.set("");
        ok((copy ? "Saved current settings as '" : "Created '") + name + "'");
    }

    private String uniqueName(String base) {
        String b = ProfileNames.sanitize(base);
        String n = b;
        for (int i = 2; profiles.contains(n); i++) n = ProfileNames.sanitize(b.substring(0, Math.min(b.length(), 28)) + " " + i);
        return n;
    }

    private void open(Path dir) {
        try {
            Files.createDirectories(dir);
            Util.getOperatingSystem().open(dir.toFile());
        } catch (Exception e) {
            fail("Could not open folder: " + e.getMessage());
        }
    }
}
