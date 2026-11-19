package dev.phantom.core.module;

public enum Category {
    COMBAT("Combat", "⚔"),
    VISUALS("Visuals", "◉"),
    MOVEMENT("Movement", "➤"),
    PLAYER("Player", "☺"),
    MISC("Misc", "✱");

    private final String displayName;
    private final String icon;

    Category(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    /** A single glyph available in Minecraft's default font. */
    public String icon() {
        return icon;
    }
}
