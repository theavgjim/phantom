package dev.phantom.integration.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Remembers the most relevant combat target for TargetHUD (aura/aim target, else crosshair). */
public final class TargetTracker {
    private static final long HOLD_MS = 2000;
    private final MinecraftClient mc;
    private LivingEntity target;
    private long lastSeen;

    public TargetTracker(MinecraftClient mc) {
        this.mc = mc;
    }

    public void set(LivingEntity entity) {
        if (entity == null) return;
        target = entity;
        lastSeen = System.currentTimeMillis();
    }

    public void onTick() {
        Entity crosshair = mc.targetedEntity;
        if (crosshair instanceof LivingEntity le && le.isAlive()) set(le);
        if (target != null && (!target.isAlive() || target.isRemoved()
                || System.currentTimeMillis() - lastSeen > HOLD_MS)) {
            target = null;
        }
    }

    public LivingEntity get() {
        return target;
    }

    public void clear() {
        target = null;
    }
}
