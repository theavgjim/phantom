package dev.phantom.integration.mixin;

import dev.phantom.integration.PhantomClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes (never alters) connection attempts so Phantom can offer Reconnect and version hints. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"))
    private static void phantom$connect(Screen parent, MinecraftClient client, ServerAddress address, ServerInfo info,
                                        boolean quickPlay, CookieStorage cookies, CallbackInfo ci) {
        PhantomClient c = PhantomClient.getOrNull();
        if (c != null) c.session().onConnectAttempt(address, info);
    }
}
