package dev.phantom.integration.mixin;

import dev.phantom.integration.Hooks;
import dev.phantom.modules.visuals.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void phantom$outlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (Hooks.shouldOutline(self)) cir.setReturnValue(Hooks.outlineColor(self));
    }

    /** While Freecam is on, mouse look rotates the free camera instead of the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void phantom$look(double dx, double dy, CallbackInfo ci) {
        Freecam fc = Hooks.activeFreecam();
        if (fc != null && (Object) this == MinecraftClient.getInstance().player) {
            fc.look(dx, dy);
            ci.cancel();
        }
    }
}
