package dev.phantom.integration.compat;

import dev.phantom.core.compat.Compatibility;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import org.slf4j.Logger;

import java.io.InputStream;

/** Checks the running game against the bundled compatibility table. */
public final class VersionCheck {
    private VersionCheck() {
    }

    public record Report(Compatibility table, String minecraft, String loader, int java, Compatibility.Result result) {
    }

    public static Report run(Logger log) {
        String mc = SharedConstants.getGameVersion().getName();
        String loader = FabricLoader.getInstance().getModContainer("fabricloader")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
        int java = Runtime.version().feature();
        Compatibility table = null;
        Compatibility.Result result;
        try (InputStream in = VersionCheck.class.getResourceAsStream("/phantom/compatibility.json")) {
            if (in == null) throw new java.io.IOException("compatibility.json missing from jar");
            table = Compatibility.load(in);
            result = table.check(mc, loader, java);
        } catch (Exception e) {
            result = new Compatibility.Result(false, "Compatibility table unreadable: " + e.getMessage());
        }
        if (result.compatible()) {
            log.info("Version check passed: {} (Fabric Loader {}, Java {})", result.message(), loader, java);
        } else {
            log.warn("Version check FAILED: {} - Phantom may not work correctly", result.message());
        }
        return new Report(table, mc, loader, java, result);
    }
}
