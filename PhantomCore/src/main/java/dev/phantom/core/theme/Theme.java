package dev.phantom.core.theme;

/** Colour palette for ClickGUI and HUD (ARGB ints). */
public record Theme(
        String name,
        int accent,
        int accentSecondary,
        int background,
        int panel,
        int panelRaised,
        int outline,
        int text,
        int textDim) {
}
