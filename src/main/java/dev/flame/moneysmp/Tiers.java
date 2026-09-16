package dev.flame.moneysmp;

import java.util.List;

final class Tiers {
    // highest first; the auction walks this backwards
    static final List<String> NAMES = List.of("S", "A", "B", "C", "D", "E", "F");

    private Tiers() {}

    static String color(String tier) {
        return switch (tier) {
            case "S" -> "&6";
            case "A" -> "&c";
            case "B" -> "&e";
            case "C" -> "&a";
            case "D" -> "&b";
            case "E" -> "&d";
            case "F" -> "&7";
            default -> "&7";
        };
    }

    static String prefix(String tier) {
        return "&8[" + color(tier) + "&l" + tier + "&8] &r";
    }

    static String normalise(String in) {
        String t = in.toUpperCase();
        return NAMES.contains(t) ? t : null;
    }
}
