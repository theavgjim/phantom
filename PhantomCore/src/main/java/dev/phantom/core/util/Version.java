package dev.phantom.core.util;

import java.util.ArrayList;
import java.util.List;

/** Lenient dotted version (e.g. "1.21.1", "0.16.14", "1.0.0-beta.2"). */
public record Version(List<Integer> parts, String qualifier) implements Comparable<Version> {

    public static Version parse(String text) {
        String s = text.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        String qualifier = "";
        int dash = s.indexOf('-');
        int plus = s.indexOf('+');
        int cut = dash >= 0 ? dash : plus;
        if (cut >= 0) {
            qualifier = s.substring(cut + 1);
            s = s.substring(0, cut);
        }
        List<Integer> parts = new ArrayList<>();
        for (String p : s.split("\\.")) {
            try {
                parts.add(Integer.parseInt(p.replaceAll("[^0-9]", "")));
            } catch (NumberFormatException e) {
                parts.add(0);
            }
        }
        return new Version(List.copyOf(parts), qualifier);
    }

    @Override
    public int compareTo(Version o) {
        int n = Math.max(parts.size(), o.parts.size());
        for (int i = 0; i < n; i++) {
            int a = i < parts.size() ? parts.get(i) : 0;
            int b = i < o.parts.size() ? o.parts.get(i) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        // A release ranks above a pre-release with the same numbers.
        if (qualifier.isEmpty() != o.qualifier.isEmpty()) return qualifier.isEmpty() ? 1 : -1;
        return qualifier.compareTo(o.qualifier);
    }

    public boolean atLeast(Version other) {
        return compareTo(other) >= 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(parts.get(i));
        }
        if (!qualifier.isEmpty()) sb.append('-').append(qualifier);
        return sb.toString();
    }
}
