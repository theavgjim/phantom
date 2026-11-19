package dev.phantom.integration.module;

import dev.phantom.core.module.Category;
import dev.phantom.core.module.Module;
import dev.phantom.integration.PhantomClient;
import net.minecraft.client.MinecraftClient;

/** Convenience base for modules that talk to Minecraft. */
public abstract class ClientModule extends Module {
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    protected ClientModule(String name, String description, Category category) {
        super(name, description, category);
    }

    protected final PhantomClient client() {
        return PhantomClient.get();
    }

    protected final boolean inWorld() {
        return mc.player != null && mc.world != null;
    }
}
