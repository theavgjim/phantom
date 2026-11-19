package dev.phantom.integration.mixin;

import dev.phantom.integration.Hooks;
import dev.phantom.modules.visuals.Freecam;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void phantom$freecam(BlockView area, Entity focused, boolean thirdPerson, boolean inverseView,
                                 float tickDelta, CallbackInfo ci) {
        Freecam fc = Hooks.activeFreecam();
        if (fc == null) return;
        setRotation(fc.yaw(), fc.pitch());
        setPos(fc.x(tickDelta), fc.y(tickDelta), fc.z(tickDelta));
    }

    /** Lets the world renderer draw the player's own body while the camera is detached. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void phantom$thirdPerson(CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.activeFreecam() != null) cir.setReturnValue(true);
    }
}
