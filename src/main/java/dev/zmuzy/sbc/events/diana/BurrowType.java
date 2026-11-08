// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.diana;

import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;

// from skyhanni BurrowType: the 3 kinds + unknown, with their colours from settings
public enum BurrowType {
    START("Start", "startColor", 0xFF55FF55), // LorenzColor.GREEN
    MOB("Mob", "mobColor", 0xFFFF5555), // LorenzColor.RED
    TREASURE("Treasure", "treasureColor", 0xFFFFAA00), // LorenzColor.GOLD
    UNKNOWN("Unknown?!", null, 0xFFFFFFFF); // LorenzColor.WHITE

    public final String text;
    private final String optionKey;
    private final int defaultColor;

    BurrowType(String text, String optionKey, int defaultColor) {
        this.text = text;
        this.optionKey = optionKey;
        this.defaultColor = defaultColor;
    }

    // ARGB from the burrow colour options (skyhanni BurrowCustomization start/mob/treasureBurrowColor)
    public int color() {
        if (optionKey == null) return defaultColor;
        Feature f = Features.get(Diana.BURROWS);
        var o = f == null ? null : f.option(optionKey);
        return o == null ? defaultColor : o.color();
    }

    // closest legacy colour code, for the floating text
    public String code() {
        return switch (this) {
            case START -> "§a";
            case MOB -> "§c";
            case TREASURE -> "§6";
            case UNKNOWN -> "§f";
        };
    }

    @Override
    public String toString() {
        return text;
    }
}
