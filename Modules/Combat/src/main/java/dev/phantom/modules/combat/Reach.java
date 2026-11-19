package dev.phantom.modules.combat;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ColorSetting;
import dev.phantom.integration.event.Render3DEvent;
import dev.phantom.integration.module.ClientModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Attack-range visualisation. Draws your real entity interaction range (an attribute in 1.21) as
 * a ring and outlines entities you can currently reach. It does not change the range.
 */
public final class Reach extends ClientModule {
    private final ColorSetting ringColor = add(new ColorSetting("Ring Color", "Entity reach ring", 0xFF8B5CF6));
    private final BooleanSetting blockRing = add(new BooleanSetting("Block Range", "Also show block interaction range", false));
    private final ColorSetting blockColor = add(new ColorSetting("Block Ring Color", "", 0xFF26A69A));
    private final BooleanSetting markInRange = add(new BooleanSetting("Mark In Range", "Outline entities you can hit", true));
    private final ColorSetting inRangeColor = add(new ColorSetting("In-Range Color", "", 0xFF66BB6A));

    public Reach() {
        super("Reach", "Visualises your attack range", Category.COMBAT);
        on(Render3DEvent.class, this::render);
    }

    private void render(Render3DEvent e) {
        var p = mc.player;
        Vec3d pos = p.getLerpedPos(e.tickDelta);
        double range = p.getEntityInteractionRange();
        e.renderer.circle(pos.x, pos.y + 0.05, pos.z, range, 64, ringColor.argb());
        if (blockRing.isOn()) e.renderer.circle(pos.x, pos.y + 0.05, pos.z, p.getBlockInteractionRange(), 64, blockColor.argb());
        if (!markInRange.isOn()) return;
        Vec3d eye = p.getCameraPosVec(e.tickDelta);
        for (var en : client().entities().get()) {
            if (en.distanceSq() > (range + 3) * (range + 3)) break;
            if (!(en.entity() instanceof LivingEntity le) || !le.isAlive()) continue;
            Box box = le.getBoundingBox();
            // Same test vanilla uses: distance from the eye to the closest point of the hitbox.
            if (box.squaredMagnitude(eye) <= range * range) {
                Vec3d lerp = le.getLerpedPos(e.tickDelta);
                e.renderer.box(box.offset(lerp.subtract(le.getPos())), inRangeColor.argb());
            }
        }
    }

    @Override
    public String getSuffix() {
        return mc.player == null ? null : String.format(Locale.ROOT, "%.1f", mc.player.getEntityInteractionRange());
    }
}
