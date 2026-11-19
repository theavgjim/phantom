package dev.phantom.modules.visuals;

import dev.phantom.core.module.Category;
import dev.phantom.core.setting.NumberSetting;
import dev.phantom.integration.event.TickEvent;
import dev.phantom.integration.mixin.SimpleOptionAccessor;
import dev.phantom.integration.module.ClientModule;

/** Raises gamma beyond the vanilla maximum; the previous value is restored on disable/shutdown. */
public final class Fullbright extends ClientModule {
    private final NumberSetting gamma = add(new NumberSetting("Brightness", "Gamma while enabled", 12, 1, 16, 0.5));
    private Double previous;

    public Fullbright() {
        super("Fullbright", "See clearly in the dark", Category.VISUALS);
        on(TickEvent.class, e -> apply());
    }

    @Override
    protected void onEnable() {
        if (previous == null) previous = mc.options.getGamma().getValue();
        apply();
    }

    private void apply() {
        Double target = gamma.getDouble();
        if (!target.equals(mc.options.getGamma().getValue())) {
            ((SimpleOptionAccessor) (Object) mc.options.getGamma()).setPhantomRawValue(target);
        }
    }

    @Override
    protected void onDisable() {
        double restore = previous != null ? previous : 1.0;
        ((SimpleOptionAccessor) (Object) mc.options.getGamma()).setPhantomRawValue(Math.max(0, Math.min(1, restore)));
        previous = null;
    }
}
