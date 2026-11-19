package dev.phantom.selftest;

import dev.phantom.integration.server.PolicyPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated-server side of the self-test: behaves like a server plugin that speaks phantom:policy.
 * It first sends a malformed and an oversized payload (the client must ignore both without
 * disconnecting), then the real policy given in -Dphantom.selftest.policy.
 */
public final class SelfTestServer implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("Phantom/TestServer");

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        String policy = System.getProperty("phantom.selftest.policy");
        if (policy == null) return;
        PayloadTypeRegistry.playS2C().register(PolicyPayload.ID, PolicyPayload.CODEC);
        // tools/compat-test.sh asks for a clean shutdown by creating this file in the run directory.
        java.nio.file.Path stopFile = java.nio.file.Path.of("phantom-stop");
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 == 0 && java.nio.file.Files.exists(stopFile)) {
                try {
                    java.nio.file.Files.deleteIfExists(stopFile);
                } catch (java.io.IOException ignored) {
                }
                LOG.info("Stop requested by the test harness");
                server.stop(false);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (!ServerPlayNetworking.canSend(handler.player, PolicyPayload.ID)) {
                LOG.info("{} does not speak phantom:policy", handler.player.getName().getString());
                return;
            }
            ServerPlayNetworking.send(handler.player, new PolicyPayload("{not valid json"));
            ServerPlayNetworking.send(handler.player, new PolicyPayload("{\"allow\":[\"" + "x".repeat(20_000) + "\"]}"));
            ServerPlayNetworking.send(handler.player, new PolicyPayload(policy));
            LOG.info("Sent phantom:policy to {}: {}", handler.player.getName().getString(), policy);
        }));
    }
}
