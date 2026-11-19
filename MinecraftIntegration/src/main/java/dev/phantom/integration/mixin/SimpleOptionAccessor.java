package dev.phantom.integration.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Fullbright writes gamma directly (bypassing the 0..1 validator) and restores it on disable. */
@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    @Accessor("value")
    void setPhantomRawValue(Object value);
}
