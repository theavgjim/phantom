package dev.phantom.integration.mixin;

import dev.phantom.integration.Hooks;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {
    /** NoSlow: skip the item-use movement slowdown inside tickMovement. */
    @Redirect(method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"),
            require = 1)
    private boolean phantom$usingItem(ClientPlayerEntity self) {
        return !Hooks.noSlowActive() && self.isUsingItem();
    }
}
