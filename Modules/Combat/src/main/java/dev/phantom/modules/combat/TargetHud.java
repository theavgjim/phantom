package dev.phantom.modules.combat;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.integration.module.ClientModule;

/** Enables the Target HUD element (health, distance and armor of your current target). */
public final class TargetHud extends ClientModule {
    public final BooleanSetting armor = add(new BooleanSetting("Armor", "Show armor points", true));
    public final BooleanSetting distance = add(new BooleanSetting("Distance", "Show distance", true));
    public final BooleanSetting head = add(new BooleanSetting("Player Head", "Show the player's face", true));

    public TargetHud() {
        super("TargetHUD", "Shows information about the entity you are fighting", Category.COMBAT);
        enabledByDefault();
    }
}
