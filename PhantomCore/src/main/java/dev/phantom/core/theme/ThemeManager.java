package dev.phantom.core.theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ThemeManager {
    public static final String DEFAULT = "Phantom";

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private Theme current;

    public ThemeManager() {
        register(new Theme("Phantom", 0xFF8B5CF6, 0xFFC084FC, 0xF00E0E14, 0xF516161F, 0xFF1F1F2B, 0xFF2C2C3B, 0xFFEDEDF5, 0xFF8A8AA0));
        register(new Theme("Ocean", 0xFF0EA5E9, 0xFF22D3EE, 0xF00B1118, 0xF5101821, 0xFF16222E, 0xFF22313F, 0xFFE6F1F8, 0xFF7E93A6));
        register(new Theme("Crimson", 0xFFEF4444, 0xFFF97316, 0xF0130C0D, 0xF51B1213, 0xFF26191A, 0xFF382527, 0xFFF6EAEA, 0xFFA08A8C));
        register(new Theme("Mint", 0xFF10B981, 0xFF84CC16, 0xF00B1310, 0xF5111B17, 0xFF17261F, 0xFF22362C, 0xFFE8F6EF, 0xFF7FA192));
        register(new Theme("Sunset", 0xFFF59E0B, 0xFFEC4899, 0xF014100B, 0xF51C1710, 0xFF282016, 0xFF3A2F20, 0xFFF8F1E6, 0xFFA59781));
        register(new Theme("Mono", 0xFFE5E5E5, 0xFFA3A3A3, 0xF00D0D0D, 0xF5151515, 0xFF1E1E1E, 0xFF2E2E2E, 0xFFF5F5F5, 0xFF8C8C8C));
        current = themes.get(DEFAULT);
    }

    public void register(Theme theme) {
        themes.put(theme.name(), theme);
    }

    public List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(themes.keySet()));
    }

    public List<Theme> all() {
        return List.copyOf(themes.values());
    }

    public Theme current() {
        return current;
    }

    public void select(String name) {
        Theme t = themes.get(name);
        if (t != null) current = t;
    }
}
