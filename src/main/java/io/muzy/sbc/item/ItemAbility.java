// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// from skyhanni ItemAbility: every ability with a cooldown, what its on, how long it runs
public enum ItemAbility {
    WITHER_IMPACT(5, false, true),
    WITHER_SHIELD_SCROLL(10, true, true),
    SHADOW_WARP_SCROLL(10),
    IMPLOSION_SCROLL(10),
    GYROKINETIC_WAND_LEFT(30, true, false, "GYROKINETIC_WAND"),
    GYROKINETIC_WAND_RIGHT(10, false, false, "GYROKINETIC_WAND"),
    GIANTS_SWORD(30),
    ICE_SPRAY_WAND(5, "STARRED_ICE_SPRAY_WAND"),
    RAGNAROCK_AXE(20),
    WAND_OF_ATONEMENT(7, "WAND_OF_HEALING", "WAND_OF_MENDING", "WAND_OF_RESTORATION"),
    SOS_FLARE(10),
    ALERT_FLARE(20, "WARNING_FLARE"),

    GOLEM_SWORD(3),
    END_STONE_SWORD(5),
    SOUL_ESOWARD(20),
    PIGMAN_SWORD(5),
    EMBER_ROD(30),
    STAFF_OF_THE_VOLCANO(30),
    STARLIGHT_WAND(2),
    VOODOO_DOLL(5),
    WEIRD_TUBA(20),
    WEIRDER_TUBA(30),
    FIRE_FREEZE_STAFF(10),
    SWORD_OF_BAD_HEALTH(5),
    WITHER_CLOAK(10),
    HOLY_ICE(4),
    VOODOO_DOLL_WILTED(3),
    FIRE_FURY_STAFF(20),
    SHADOW_FURY(15, "STARRED_SHADOW_FURY"),
    ROYAL_PIGEON(5),
    WAND_OF_STRENGTH(10),
    TACTICAL_INSERTION(20),
    TOTEM_OF_CORRUPTION(20),
    ENRAGER(20),

    // no sound to tell them by, the action bar names them
    ENDER_BOW("Ender Warp", 5, false, "Ender Bow"),
    LIVID_DAGGER("Throw", 5, false, "Livid Dagger"),
    FIRE_VEIL("Fire Veil", 5, false, "Fire Veil Wand"),
    INK_WAND("Ink Bomb", 30, false, "Ink Wand"),
    ROGUE_SWORD("Speed Boost", 30, true, "Rogue Sword"),
    TALBOTS_THEODOLITE("Track", 10, false, "Talbot's Theodolite"),
    ATOMSPLIT_KATANA("Soulcry", 4, true, "Atomsplit Katana", "Vorpal Katana", "Voidedge Katana"),

    // no steady sound either
    ECHO("Echo", 3, false, "Ancestral Spade");

    private static final Set<ItemAbility> WITHER_SCROLLS = EnumSet.of(WITHER_SHIELD_SCROLL, SHADOW_WARP_SCROLL, IMPLOSION_SCROLL);

    // name the action bar gives it, empty for the sound ones
    public final String abilityName;
    public final boolean alternativePosition;
    // told by sound + click, not the action bar
    public final boolean newVariant;
    public final List<String> internalNames = new ArrayList<>();
    public final List<String> itemNames = new ArrayList<>();
    private final int seconds;
    private final boolean ignoreMage;

    long lastActivation, lastItemClick;
    // color code while its in its own phase: ragnarock casting, a shield up
    char specialColor;

    ItemAbility(int seconds, String... otherIds) {
        this(seconds, false, false, otherIds);
    }

    ItemAbility(int seconds, boolean alternativePosition, boolean ignoreMage, String... otherIds) {
        abilityName = "";
        this.seconds = seconds;
        this.alternativePosition = alternativePosition;
        this.ignoreMage = ignoreMage;
        newVariant = true;
        internalNames.addAll(List.of(otherIds));
        internalNames.add(name());
    }

    ItemAbility(String abilityName, int seconds, boolean ignoreMage, String... itemNames) {
        this.abilityName = abilityName;
        this.seconds = seconds;
        alternativePosition = false;
        this.ignoreMage = ignoreMage;
        newVariant = false;
        this.itemNames.addAll(List.of(itemNames));
    }

    public void activate() {
        activate((char) 0, seconds * 1000);
    }

    // runs for customMs from now, whatever its usual time is
    public void activate(char color, int customMs) {
        specialColor = color;
        lastActivation = System.currentTimeMillis() - (seconds * 1000L - customMs);
    }

    // cooldown in ms. some arent really cooldowns but effects over time, nothing shortens those
    public long cooldownMs() {
        if (this == WAND_OF_ATONEMENT || this == RAGNAROCK_AXE) return seconds * 1000L;
        return Math.round(seconds * 1000.0 * multiplier());
    }

    double multiplier() {
        return ignoreMage ? 1 : AbilityCooldowns.mageMultiplier();
    }

    public long leftMs() {
        return lastActivation + cooldownMs() - System.currentTimeMillis();
    }

    public boolean onCooldown() {
        return lastActivation != 0 && leftMs() > 0;
    }

    // text on the item: tenths at the end, whole secs before that
    public String durationText() {
        long left = leftMs();
        return left < 1600 ? String.format(Locale.ROOT, "%.1f", left / 1000.0) : String.valueOf(left / 1000 + 1);
    }

    public void setItemClick() {
        lastItemClick = System.currentTimeMillis();
    }

    // a sound only means this ability if the item got clicked right before
    void sound() {
        if (System.currentTimeMillis() - lastItemClick < 400) activate();
    }

    void reset() {
        lastActivation = 0;
        specialColor = 0;
    }

    // how the card names it
    public String title() {
        if (!abilityName.isEmpty()) return abilityName;
        switch (this) {
            case GIANTS_SWORD:
                return "Giant's Sword";
            case GYROKINETIC_WAND_LEFT:
                return "Gyro Left";
            case GYROKINETIC_WAND_RIGHT:
                return "Gyro Right";
            case TALBOTS_THEODOLITE:
                return "Talbot's Theodolite";
            default:
                break;
        }
        StringBuilder out = new StringBuilder();
        for (String word : name().replace("_SCROLL", "").split("_")) {
            String w = word.toLowerCase(Locale.ROOT);
            if (out.length() > 0) out.append(' ');
            out.append(w.equals("of") || w.equals("the") ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return out.toString();
    }

    public static ItemAbility byInternalName(String id) {
        for (ItemAbility a : values()) if (a.newVariant && a.internalNames.contains(id)) return a;
        return null;
    }

    // what a weapons scrolls give it. all 3 wither scrolls together = wither impact
    public static Set<ItemAbility> ofScrolls(List<String> scrolls) {
        Set<ItemAbility> out = EnumSet.noneOf(ItemAbility.class);
        if (scrolls == null) return out;
        for (ItemAbility a : WITHER_SCROLLS) if (scrolls.contains(a.name())) out.add(a);
        if (out.size() == 3) {
            out.clear();
            out.add(WITHER_IMPACT);
        }
        return out;
    }
}
