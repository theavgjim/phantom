package dev.phantom.modules.player;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/** Switches to the fastest hotbar tool for the block being mined. */
public final class AutoTool extends ClientModule {
    private final BooleanSetting switchBack = add(new BooleanSetting("Switch Back", "Return to the previous slot when done mining", true));
    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", "Selects the best tool while mining", Category.PLAYER);
        on(TickEvent.class, e -> tick());
    }

    private void tick() {
        var p = mc.player;
        boolean mining = mc.options.attackKey.isPressed() && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            if (switchBack.isOn() && previousSlot >= 0) p.getInventory().selectedSlot = previousSlot;
            previousSlot = -1;
            return;
        }
        BlockState state = mc.world.getBlockState(((BlockHitResult) mc.crosshairTarget).getBlockPos());
        int best = p.getInventory().selectedSlot;
        float bestSpeed = p.getInventory().getStack(best).getMiningSpeedMultiplier(state);
        for (int i = 0; i < 9; i++) {
            float s = p.getInventory().getStack(i).getMiningSpeedMultiplier(state);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = i;
            }
        }
        if (best != p.getInventory().selectedSlot) {
            if (previousSlot < 0) previousSlot = p.getInventory().selectedSlot;
            p.getInventory().selectedSlot = best;
        }
    }
}
