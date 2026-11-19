package dev.phantom.modules.movement;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.IntegratedServerAccess;
import dev.phantom.integration.util.MovementUtil;
import net.minecraft.util.math.Vec3d;

/**
 * Flight for testing builds and terrain in private worlds.
 * "Abilities" grants real flight on the integrated server (like creative); "Velocity" drives the
 * player directly. Private-world only.
 */
public final class Fly extends ClientModule {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Abilities = creative-style flight, Velocity = direct control", "Abilities", "Abilities", "Velocity"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "Flight speed", 1.0, 0.1, 5.0, 0.1));
    private final BooleanSetting noFallDamage = add(new BooleanSetting("Safe Landing", "Reset fall distance while flying (integrated server)", true));

    public Fly() {
        super("Fly", "Fly in creative-style for testing in private worlds", Category.MOVEMENT);
        restricted();
        on(TickEvent.class, e -> tick());
    }

    @Override
    protected void onEnable() {
        if (inWorld() && mode.is("Abilities")) grant(true);
        if (inWorld() && !mc.isIntegratedServerRunning()) {
            // Only reachable when this server's policy allows Fly. The server's own movement rules still apply.
            client().notifications().push("Fly", "Allowed by this server; it also needs allow-flight on the server",
                    dev.phantom.integration.util.Notifications.Kind.INFO);
        }
    }

    @Override
    protected void onDisable() {
        if (!inWorld()) return;
        if (!mc.player.isCreative() && !mc.player.isSpectator()) grant(false);
        mc.player.getAbilities().setFlySpeed(0.05f);
    }

    @Override
    protected void onSettingChanged(dev.phantom.core.setting.Setting<?> s) {
        if (isEnabled() && inWorld() && s == mode) grant(mode.is("Abilities") || mc.player.isCreative());
    }

    private void grant(boolean allow) {
        var abilities = mc.player.getAbilities();
        abilities.allowFlying = allow || mc.player.isCreative() || mc.player.isSpectator();
        if (!allow) abilities.flying = mc.player.isSpectator();
        IntegratedServerAccess.run(sp -> {
            boolean creativeLike = sp.isCreative() || sp.isSpectator();
            sp.getAbilities().allowFlying = allow || creativeLike;
            if (!allow && !sp.isSpectator()) sp.getAbilities().flying = false;
            sp.sendAbilitiesUpdate();
        });
    }

    private void tick() {
        var p = mc.player;
        if (mode.is("Abilities")) {
            p.getAbilities().allowFlying = true;
            p.getAbilities().setFlySpeed(0.05f * speed.getFloat());
        } else {
            double s = speed.getDouble() * 0.5;
            double v = (p.input.jumping ? s : 0) - (p.input.sneaking ? s : 0);
            Vec3d dir = MovementUtil.inputDirection(p);
            p.setVelocity(dir.x * s, v, dir.z * s);
        }
        if (noFallDamage.isOn()) {
            p.fallDistance = 0;
            IntegratedServerAccess.run(sp -> sp.fallDistance = 0);
        }
    }

    @Override
    public String getSuffix() {
        return mode.get();
    }
}
