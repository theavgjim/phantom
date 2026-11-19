package dev.phantom.integration.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs code against the local player's server-side entity on the integrated (singleplayer)
 * server. Only works in private worlds, which is exactly where movement testing is allowed.
 */
public final class IntegratedServerAccess {
    private IntegratedServerAccess() {
    }

    public static boolean run(Consumer<ServerPlayerEntity> action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        IntegratedServer server = mc.getServer();
        if (server == null || mc.player == null) return false;
        UUID id = mc.player.getUuid();
        server.execute(() -> {
            ServerPlayerEntity sp = server.getPlayerManager().getPlayer(id);
            if (sp != null) action.accept(sp);
        });
        return true;
    }
}
