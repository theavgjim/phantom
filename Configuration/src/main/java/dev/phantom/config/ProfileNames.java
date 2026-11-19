package dev.phantom.config;

import java.util.regex.Pattern;

public final class ProfileNames {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9 _\\-]{1,32}");

    private ProfileNames() {
    }

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches() && !name.isBlank()
                && !name.startsWith(" ") && !name.endsWith(" ");
    }

    /** Turns arbitrary text (e.g. an imported file name) into a valid profile name. */
    public static String sanitize(String raw) {
        String s = raw == null ? "" : raw.replaceAll("[^A-Za-z0-9 _\\-]", "_").trim();
        if (s.length() > 32) s = s.substring(0, 32).trim();
        return s.isEmpty() ? "profile" : s;
    }
}
