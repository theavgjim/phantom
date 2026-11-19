package dev.phantom.modules.misc;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.module.ClientModule;

/**
 * Settings for the Reconnect button Phantom adds to the disconnect screen. Auto-reconnect is off
 * by default, only reacts to lost connections (never kicks, bans or whitelists), waits between
 * attempts and gives up after a few tries.
 */
public final class Reconnect extends ClientModule {
    public final BooleanSetting auto = add(new BooleanSetting("Auto Reconnect", "Rejoin automatically after a lost connection (never after a kick or ban)", false));
    public final NumberSetting delay = add(new NumberSetting("Delay", "Seconds before the first attempt (doubles each try)", 10, 5, 60, 1, "s"));
    public final NumberSetting attempts = add(new NumberSetting("Max Attempts", "Give up after this many tries", 3, 1, 5, 1));

    public Reconnect() {
        super("Reconnect", "Reconnect button and optional auto-reconnect", Category.MISC);
        notToggleable();
        hideFromList();
    }
}
