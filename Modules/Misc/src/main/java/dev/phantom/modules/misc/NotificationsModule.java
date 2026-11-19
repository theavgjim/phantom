package dev.phantom.modules.misc;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.core.setting.Setting;
import dev.phantom.integration.module.ClientModule;

/** Toasts for module toggles. Warnings (e.g. private-world refusals) are always shown. */
public final class NotificationsModule extends ClientModule {
    public final BooleanSetting toggleMessages = add(new BooleanSetting("Toggle Messages", "Toast when a module is toggled", true));
    public final NumberSetting duration = add(new NumberSetting("Duration", "Seconds on screen", 2.5, 1, 8, 0.5, "s"));

    public NotificationsModule() {
        super("Notifications", "On-screen toasts", Category.MISC);
        enabledByDefault();
        hideFromList();
    }

    @Override
    protected void onSettingChanged(Setting<?> s) {
        if (s == duration) client().notifications().setDefaultDuration((long) (duration.getDouble() * 1000));
    }
}
