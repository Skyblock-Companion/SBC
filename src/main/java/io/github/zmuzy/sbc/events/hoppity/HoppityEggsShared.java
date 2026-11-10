// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.hoppity;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import net.minecraft.world.phys.Vec3;

import java.util.regex.Matcher;

// from skyhanni HoppityEggsShared: "[SkyHanni] Lunch Chocolate Egg located at x: .., y: .., z: .." lines from
// others become the shared egg waypoint (if that meal is still unclaimed and the spot is known)
// our own share says the same without the other mod's name and gets read back here too. that mod only reads
// lines with its tag so its users see ours as a plain line with coords, not a waypoint
public final class HoppityEggsShared {
    // matches: CalMWolfs: [SkyHanni] Breakfast Chocolate Egg located at x: 142, y: 71, z: -453 (hidden note)
    static final Patterns.P SHARED_EGG = Patterns.p("SH.misc.chocolatefactory.egg.shared");
    // same words after the speaker's name, without the tag
    static final java.util.regex.Pattern SHARED_EGG_OURS = java.util.regex.Pattern.compile("[^:]*: (?<meal>\\w+) Chocolate Egg located at x: (?<x>-?\\d+), y: (?<y>-?\\d+), z: (?<z>-?\\d+)(?: \\((?<note>.*)\\))?");

    private static Vec3 pendingLocation;
    private static HoppityEggType pendingMeal;
    private static String pendingNote;

    private HoppityEggsShared() {}

    // HoppityEggsShared.onChat, stripped line
    public static void onChat(String plain) {
        if (!isEnabled()) return;
        Matcher m = SHARED_EGG.matcher(plain);
        if (!m.matches()) m = SHARED_EGG_OURS.matcher(plain);
        if (!m.matches()) return;
        Vec3 eggLocation;
        try {
            eggLocation = new Vec3(Integer.parseInt(m.group("x")), Integer.parseInt(m.group("y")), Integer.parseInt(m.group("z")));
        } catch (NumberFormatException e) {
            return;
        }
        HoppityEggType meal = HoppityEggType.byMeal(m.group("meal"));
        if (meal == null) return;
        String note = m.group("note");
        if (meal.isClaimed()) return;
        if (!HoppityEggLocator.isValidEggLocation(eggLocation)) return;
        HoppityEggLocator.sharedEggLocation = eggLocation;
        HoppityEggLocator.currentEggType = meal;
        HoppityEggLocator.currentEggNote = note;
    }

    // the egg you just found, kept until you click the share prompt
    static void remember(Vec3 playerLocation, HoppityEggType meal, String note) {
        pendingLocation = playerLocation;
        pendingMeal = meal;
        pendingNote = note;
    }

    // /sbc shareegg: HoppityEggsShared.shareNearbyEggLocation for the remembered find
    public static boolean shareRemembered() {
        if (pendingLocation == null || pendingMeal == null) return false;
        shareNearbyEggLocation(pendingLocation, pendingMeal, pendingNote == null ? "" : pendingNote);
        pendingLocation = null;
        pendingMeal = null;
        pendingNote = null;
        return true;
    }

    public static void shareNearbyEggLocation(Vec3 playerLocation, HoppityEggType meal, String note) {
        if (!isEnabled()) return;
        Vec3 closest = null;
        double best = Double.MAX_VALUE;
        for (Vec3 egg : HoppityEggLocations.islandLocations()) {
            double d = egg.distanceTo(playerLocation);
            if (d < best) {
                best = d;
                closest = egg;
            }
        }
        if (closest == null) return;
        String location = "x: " + (int) closest.x + ", y: " + (int) closest.y + ", z: " + (int) closest.z;
        HoppityEnv.world.sendCommand("ac " + meal.mealName + " Chocolate Egg located at " + location + " (" + GameState.strip(note) + ")");
    }

    public static boolean isEnabled() {
        return HoppityEnv.world.inSkyBlock() && Hoppity.eggsEnabled() && Hoppity.b(Hoppity.EGGS, "shared", true);
    }

    public static void reset() {
        pendingLocation = null;
        pendingMeal = null;
        pendingNote = null;
    }
}
