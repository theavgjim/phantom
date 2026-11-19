package dev.phantom.modules.misc;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ColorSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.module.ClientModule;
import dev.phantom.integration.util.Colors;

/** Master switch and colour scheme for the HUD. Element positions are edited in the HUD editor. */
public final class HudModule extends ClientModule {
    public final ModeSetting colorMode = add(new ModeSetting("Color Mode", "Accent colour source", "Theme", "Theme", "Static", "Rainbow"));
    public final ColorSetting staticColor = add(new ColorSetting("Static Color", "Used in Static mode", 0xFF8B5CF6)
            .visibleWhen(() -> colorMode().is("Static")));
    public final NumberSetting rainbowSpeed = add(new NumberSetting("Rainbow Speed", "", 1.0, 0.1, 5.0, 0.1)
            .visibleWhen(() -> colorMode().is("Rainbow")));
    public final ColorSetting textColor = add(new ColorSetting("Text Color", "Primary HUD text", 0xFFEDEDF5));
    public final NumberSetting background = add(new NumberSetting("Background", "Background opacity", 120, 0, 255, 5));
    public final BooleanSetting shadow = add(new BooleanSetting("Text Shadow", "", true));

    public HudModule() {
        super("HUD", "Heads-up display", Category.MISC);
        enabledByDefault();
        hideFromList();
    }

    private ModeSetting colorMode() {
        return colorMode;
    }

    /** Accent colour for a line/element; {@code offset} staggers rainbow mode. */
    public int accent(float offset) {
        return switch (colorMode.get()) {
            case "Static" -> staticColor.argb();
            case "Rainbow" -> Colors.rainbow(offset, rainbowSpeed.getFloat(), 0.6f);
            default -> Colors.lerp(client().theme().accent(), client().theme().accentSecondary(),
                    (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 600.0 + offset * 6)));
        };
    }

    public int backgroundColor() {
        return Colors.withAlpha(client().theme().background(), background.getInt());
    }
}
