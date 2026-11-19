package dev.phantom.integration;

import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint (declared in fabric.mod.json). */
public final class PhantomClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PhantomClient.bootstrap();
    }
}
