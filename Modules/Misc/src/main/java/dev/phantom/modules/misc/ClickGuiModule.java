package dev.phantom.modules.misc;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.core.setting.Setting;
import dev.phantom.core.theme.ThemeManager;
import dev.phantom.gui.ClickGuiScreen;
import dev.phantom.integration.module.ClientModule;
import org.lwjgl.glfw.GLFW;

/** Opens the ClickGUI (default: Right Shift) and holds GUI appearance settings. */
public final class ClickGuiModule extends ClientModule {
    public final ModeSetting theme = add(new ModeSetting("Theme", "Colour theme", ThemeManager.DEFAULT,
            () -> client().core().themes().names()));
    public final NumberSetting animationSpeed = add(new NumberSetting("Animation Speed", "How fast panels animate", 14, 4, 30, 1));
    public final NumberSetting radius = add(new NumberSetting("Corner Radius", "Roundness of panels", 6, 0, 10, 1, "px"));
    public final NumberSetting dim = add(new NumberSetting("Background Dim", "Darken the game behind the GUI", 110, 0, 220, 5));
    public final BooleanSetting descriptions = add(new BooleanSetting("Descriptions", "Show module descriptions", true));

    public ClickGuiModule() {
        super("ClickGUI", "Open the Phantom interface", Category.MISC);
        notToggleable();
        hideFromList();
        defaultKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public void onActivate() {
        if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
    }

    @Override
    protected void onSettingChanged(Setting<?> s) {
        if (s == theme) client().core().themes().select(theme.get());
    }
}
