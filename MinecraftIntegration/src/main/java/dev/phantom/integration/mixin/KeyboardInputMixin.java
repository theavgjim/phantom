package dev.phantom.integration.mixin;

import dev.phantom.integration.Hooks;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    /** Freecam: movement keys steer the camera, so the player body stands still. */
    @Inject(method = "tick", at = @At("TAIL"))
    private void phantom$freezeInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (Hooks.activeFreecam() == null) return;
        Input in = (Input) (Object) this;
        in.movementForward = 0;
        in.movementSideways = 0;
        in.pressingForward = in.pressingBack = in.pressingLeft = in.pressingRight = false;
        in.jumping = false;
        in.sneaking = false;
    }
}
