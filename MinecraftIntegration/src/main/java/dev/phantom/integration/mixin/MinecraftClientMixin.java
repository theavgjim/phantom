package dev.phantom.integration.mixin;

import dev.phantom.integration.Hooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /** Entity highlighting: reuse the vanilla glowing-outline pass. */
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true)
    private void phantom$hasOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.shouldOutline(entity)) cir.setReturnValue(true);
    }
}
