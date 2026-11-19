package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.ColorSetting;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.world.EntityCache;

/** Shared, user-editable colours for ESP, Tracers, Highlight, Nametags and distance indicators. */
public final class EspColors extends ClientModule {
    private static EspColors instance;

    public final ColorSetting players = add(new ColorSetting("Players", "Other players", 0xFF4FC3F7));
    public final ColorSetting hostile = add(new ColorSetting("Hostile", "Monsters", 0xFFEF5350));
    public final ColorSetting passive = add(new ColorSetting("Passive", "Animals and other mobs", 0xFF66BB6A));
    public final ColorSetting dummies = add(new ColorSetting("Dummies", "Armor stands (training dummies)", 0xFFFFCA28));
    public final ColorSetting items = add(new ColorSetting("Items", "Dropped items", 0xFFE0E0E0));
    public final ColorSetting other = add(new ColorSetting("Other", "Everything else", 0xFFB39DDB));

    public EspColors() {
        super("ESP Colors", "Custom colours used by all ESP-style visuals", Category.VISUALS);
        notToggleable();
        hideFromList();
        instance = this;
    }

    public static int of(EntityCache.Kind kind) {
        EspColors c = instance;
        if (c == null) return 0xFFFFFFFF;
        return switch (kind) {
            case PLAYER -> c.players.argb();
            case HOSTILE -> c.hostile.argb();
            case PASSIVE -> c.passive.argb();
            case DUMMY -> c.dummies.argb();
            case ITEM -> c.items.argb();
            case OTHER -> c.other.argb();
        };
    }
}
