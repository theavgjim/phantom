package dev.phantom.core.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudRegistry {
    private final Map<String, HudElement> elements = new LinkedHashMap<>();
    private Runnable changeListener = () -> {
    };

    public <E extends HudElement> E register(E element) {
        elements.put(element.id(), element);
        element.setChangeListener(() -> changeListener.run());
        return element;
    }

    public HudElement get(String id) {
        return elements.get(id);
    }

    public List<HudElement> all() {
        return Collections.unmodifiableList(new ArrayList<>(elements.values()));
    }

    public void setChangeListener(Runnable r) {
        this.changeListener = r == null ? () -> {
        } : r;
    }
}
