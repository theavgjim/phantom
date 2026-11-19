package dev.phantom.modules.visuals;

import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.integration.world.EntityCache;

/** Reusable set of "which entity kinds" toggles. */
public final class EntityFilter {
    public final BooleanSetting players;
    public final BooleanSetting hostile;
    public final BooleanSetting passive;
    public final BooleanSetting dummies;
    public final BooleanSetting items;

    public EntityFilter(boolean players, boolean hostile, boolean passive, boolean dummies, boolean items) {
        this.players = new BooleanSetting("Players", "Include other players", players);
        this.hostile = new BooleanSetting("Hostile", "Include monsters", hostile);
        this.passive = new BooleanSetting("Passive", "Include animals / other mobs", passive);
        this.dummies = new BooleanSetting("Dummies", "Include armor stands", dummies);
        this.items = new BooleanSetting("Items", "Include dropped items", items);
    }

    public boolean test(EntityCache.Kind kind) {
        return switch (kind) {
            case PLAYER -> players.isOn();
            case HOSTILE -> hostile.isOn();
            case PASSIVE -> passive.isOn();
            case DUMMY -> dummies.isOn();
            case ITEM -> items.isOn();
            case OTHER -> false;
        };
    }
}
