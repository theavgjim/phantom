package dev.phantom.core;

import dev.phantom.core.module.Category;
import dev.phantom.core.module.Module;
import dev.phantom.core.setting.BooleanSetting;
import dev.phantom.core.setting.ColorSetting;
import dev.phantom.core.setting.ModeSetting;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.core.setting.StringListSetting;

import java.util.List;

final class TestModules {
    static final class Tick {
    }

    static final class Counter extends Module {
        final NumberSetting range = add(new NumberSetting("Range", "", 3.0, 1.0, 6.0, 0.1));
        final BooleanSetting flag = add(new BooleanSetting("Flag", "", true));
        final ModeSetting mode = add(new ModeSetting("Mode", "", "A", "A", "B", "C"));
        final ColorSetting color = add(new ColorSetting("Color", "", 0xFF112233));
        final StringListSetting blocks = add(new StringListSetting("Blocks", "", List.of("minecraft:stone")));
        int ticks;
        int enables;

        Counter() {
            super("Counter", "Counts ticks", Category.MISC);
            on(Tick.class, t -> ticks++);
        }

        @Override
        protected void onEnable() {
            enables++;
        }
    }

    static final class Restricted extends Module {
        Restricted() {
            super("Restricted", "Private only", Category.COMBAT);
            privateWorldOnly();
        }
    }

    private TestModules() {
    }
}
