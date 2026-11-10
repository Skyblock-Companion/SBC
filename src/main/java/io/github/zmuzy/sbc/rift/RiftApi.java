// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

// from skyhanni rift/RiftApi.kt: where in the rift you are (scoreboard area) + a few shared flags
public final class RiftApi {
    public static boolean inRiftRace, trackingButtons, allButtonsHit;

    private RiftApi() {}

    public static boolean inRift() {
        return "THE_RIFT".equals(RiftEnv.world.island());
    }

    private static String area() {
        return RiftEnv.world.area();
    }

    public static boolean inMirrorVerse() {
        return area().equals("Mirrorverse");
    }

    public static boolean inLivingCave() {
        return area().equals("Living Cave");
    }

    public static boolean inLivingStillness() {
        return area().equals("Living Stillness");
    }

    public static boolean inStillgoreChateau() {
        String a = area();
        return a.equals("Stillgore Château") || a.equals("Oubliette");
    }

    public static boolean inColosseum() {
        return area().equals("Colosseum");
    }

    public static boolean inDreadfarm() {
        return area().equals("Dreadfarm");
    }

    public static boolean inWestVillage() {
        String a = area();
        return a.equals("West Village") || a.equals("Infested House");
    }

    public static boolean inMountainTop() {
        return switch (area()) {
            case "Continuum", "The Mountaintop", "Trial Grounds", "Time-Torn Isles", "Wizardman Bureau", "Wizard Brawl", "Walk of Fame", "Time Chamber" -> true;
            default -> false;
        };
    }

    public static void onWorldChange() {
        inRiftRace = false;
    }
}
