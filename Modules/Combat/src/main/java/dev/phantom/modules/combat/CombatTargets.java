package dev.phantom.modules.combat;

import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.integration.world.EntityCache;

/**
 * Target selection for combat training. Players are deliberately not an option: training
 * modules act on mobs and armor-stand dummies in private worlds only.
 */
public final class CombatTargets {
    public final BooleanSetting dummies = new BooleanSetting("Dummies", "Armor stands used as training dummies", true);
    public final BooleanSetting hostile = new BooleanSetting("Hostile Mobs", "Monsters", true);
    public final BooleanSetting passive = new BooleanSetting("Passive Mobs", "Animals and other mobs", false);

    public boolean test(EntityCache.Kind kind) {
        return switch (kind) {
            case DUMMY -> dummies.isOn();
            case HOSTILE -> hostile.isOn();
            case PASSIVE -> passive.isOn();
            default -> false;
        };
    }
}
