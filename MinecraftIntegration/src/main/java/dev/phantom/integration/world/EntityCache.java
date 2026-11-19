package dev.phantom.integration.world;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * One shared, lazily rebuilt entity list per tick. ESP, Tracers, Nametags, KillAura, AimBot, Reach
 * and Highlight all read it, so the world entity list is walked at most once per tick - and not at
 * all when none of those modules is enabled.
 */
public final class EntityCache {
    public enum Kind { PLAYER, HOSTILE, PASSIVE, DUMMY, ITEM, OTHER }

    public record Entry(Entity entity, Kind kind, double distanceSq) {
    }

    private static final double MAX_RANGE_SQ = 256 * 256;
    private final MinecraftClient mc;
    private final List<Entry> entries = new ArrayList<>();
    private long tick;
    private long builtAt = -1;

    public EntityCache(MinecraftClient mc) {
        this.mc = mc;
    }

    public void onTick() {
        tick++;
    }

    public void clear() {
        entries.clear();
        builtAt = -1;
    }

    public List<Entry> get() {
        if (builtAt != tick) rebuild();
        return Collections.unmodifiableList(entries);
    }

    private void rebuild() {
        builtAt = tick;
        entries.clear();
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || e.isRemoved()) continue;
            double d = e.squaredDistanceTo(mc.player);
            if (d > MAX_RANGE_SQ) continue;
            entries.add(new Entry(e, kindOf(e), d));
        }
        entries.sort((a, b) -> Double.compare(a.distanceSq, b.distanceSq));
    }

    /** Nearest living entity matching the filter within {@code range} blocks. */
    public LivingEntity nearest(double range, Predicate<Entry> filter) {
        double r2 = range * range;
        for (Entry e : get()) {
            if (e.distanceSq > r2) break; // sorted by distance
            if (e.entity instanceof LivingEntity le && le.isAlive() && filter.test(e)) return le;
        }
        return null;
    }

    public static Kind kindOf(Entity e) {
        if (e instanceof PlayerEntity) return Kind.PLAYER;
        if (e instanceof ArmorStandEntity) return Kind.DUMMY;
        if (e instanceof Monster) return Kind.HOSTILE;
        if (e instanceof MobEntity) return Kind.PASSIVE;
        if (e instanceof ItemEntity) return Kind.ITEM;
        return Kind.OTHER;
    }
}
