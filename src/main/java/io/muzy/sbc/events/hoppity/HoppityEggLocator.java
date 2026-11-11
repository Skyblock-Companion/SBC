// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import io.muzy.sbc.events.diana.BezierFitter;
import io.muzy.sbc.events.diana.DianaData;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

// from skyhanni HoppityEggLocator: the egglocator's HAPPY_VILLAGER particles (speed 0, count 1) trace a
// curve, its landing point (ParticlePathBezierFitter) snaps to the nearest known spot within 5 blocks
// also draws a shared egg, every spot on the island when theres no locator in the hotbar, and nearby dupes
public final class HoppityEggLocator {
    public static final String LOCATOR_ITEM = "EGGLOCATOR";
    private static final double MAX_GUESS_DISTANCE = 5.0;

    private static long lastClick = HoppityEnv.FAR_PAST;
    private static boolean drawLocations;
    private static boolean warningPending;
    public static Vec3 sharedEggLocation;
    public static List<Vec3> possibleEggLocations = List.of();
    public static HoppityEggType currentEggType;
    public static String currentEggNote;
    private static final BezierFitter bezierFitter = new BezierFitter(3);
    private HoppityEggLocator() {}

    public static void onEggFound(HoppityEggType type) {
        if (type.isResetting()) resetData();
    }

    public static void onWorldChange() {
        resetData();
    }

    public static void resetData() {
        possibleEggLocations = List.of();
        drawLocations = false;
        sharedEggLocation = null;
        currentEggType = null;
        currentEggNote = null;
        bezierFitter.reset();
        warningPending = false;
        lastClick = HoppityEnv.FAR_PAST;
    }

    public static void onEggSpawned(HoppityEggType type) {
        if (type == currentEggType) resetData();
    }

    private static AABB blockBox(Vec3 v) {
        double x = Math.floor(v.x), y = Math.floor(v.y), z = Math.floor(v.z);
        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }

    private static Vec3 center(Vec3 v) {
        return new Vec3(Math.floor(v.x) + 0.5, Math.floor(v.y) + 0.5, Math.floor(v.z) + 0.5);
    }

    // HoppityEggLocator.onRenderWorld
    public static void render(WorldDraw w) {
        if (!isEnabled()) return;
        if (drawLocations) {
            drawGuessLocations(w);
            return;
        }
        if (sharedEggLocation != null && Hoppity.b(Hoppity.EGGS, "shared", true)) {
            drawEggWaypoint(w, sharedEggLocation, "§aShared Egg");
            return;
        }
        Set<Vec3> islandEggsLocations = HoppityEggLocations.islandLocations();
        if (shouldShowAllEggs()) {
            for (Vec3 egg : islandEggsLocations) {
                if (Hoppity.b(Hoppity.EGGS, "hideDuplicates", false) && HoppityEggLocations.hasCollectedEgg(egg)) continue;
                drawEggWaypoint(w, egg, "§aEgg");
            }
            return;
        }
        drawDuplicateEggs(w, islandEggsLocations);
    }

    private static void drawGuessLocations(WorldDraw w) {
        int i = 0;
        for (Vec3 egg : possibleEggLocations) {
            i++;
            String name = possibleEggLocations.size() == 1 ? "§aGuess" : "§aGuess #" + i;
            drawEggWaypoint(w, egg, name);
            if (Hoppity.b(Hoppity.EGGS, "showLine", true)) {
                Vec3 look = w.mc.player == null ? new Vec3(0, 0, 1) : w.mc.player.getLookAngle();
                Vec3 start = w.eye.add(look.scale(2));
                w.line(start, center(egg), 0xFF55FF55, 2, false);
            }
        }
    }

    private static void drawDuplicateEggs(WorldDraw w, Set<Vec3> islandEggsLocations) {
        if (!Hoppity.b(Hoppity.EGGS, "highlightDuplicates", true)) return;
        if (!Hoppity.b(Hoppity.EGGS, "showNearbyDuplicates", true)) return;
        if (HoppityEggLocations.foundAllOnThisIsland()) return;
        Vec3 player = HoppityEnv.world.playerPos();
        for (Vec3 egg : islandEggsLocations) {
            double dist = egg.distanceTo(player);
            if (dist < 10 && HoppityEggLocations.hasCollectedEgg(egg)) {
                int alpha = (int) (Math.min(0.5, (10 - dist) / 10) * 255);
                w.box(blockBox(egg), (alpha << 24) | 0xFF5555, true, false);
                w.label("§cDuplicate Location!", center(egg).add(0, 1, 0), true);
            }
        }
    }

    private static void drawEggWaypoint(WorldDraw w, Vec3 location, String label) {
        boolean shouldMarkDuplicate = Hoppity.b(Hoppity.EGGS, "highlightDuplicates", true) && HoppityEggLocations.hasCollectedEgg(location) && !HoppityEggLocations.foundAllOnThisIsland();
        String text = shouldMarkDuplicate ? label + " §c(Duplicate Location)" : label;
        // HoppityEggLocator.drawEggWaypoint: drawWaypointFilled(seeThroughBlocks = true, beacon = true) + drawDynamicText
        if (!shouldMarkDuplicate) w.waypointBox(location, Hoppity.color(Hoppity.EGGS, "color", 0xFF00FF00), true, true, 0);
        else w.waypointBox(location, 0xFFFF5555, false, false, 0);
        w.dynamicText(center(location).add(0, 1, 0), text, 1.5, 0);
        w.dynamicText(center(location).add(0, 1, 0), "§e" + Math.round(center(location).distanceTo(w.eye)) + " blocks", 1.0, -1);
    }

    private static boolean shouldShowAllEggs() {
        return Hoppity.b(Hoppity.EGGS, "showAll", false) && !HoppityEnv.world.locatorInHotbar() && HoppityEggType.anyEggsUnclaimed();
    }

    // HoppityEggLocator.onParticle: HAPPY_VILLAGER (speed 0, count 1) within 5s of a locator use
    public static void onParticle(String type, double x, double y, double z, int count, float speed, boolean fishingHookNearby) {
        if (!isEnabled()) return;
        if (!type.equals("minecraft:happy_villager") || speed != 0f || count != 1) return;
        if (HoppityEnv.passedSince(lastClick) > 5000) return;
        Vec3 location = new Vec3(x, y, z);
        // endCondition: a fishing hook within 0.3 blocks ends the curve (skyhanni checks before adding)
        if (!bezierFitter.isEmpty() && fishingHookNearby) return;
        if (!bezierFitter.tryAdd(location, 3.0)) return;
        Vec3 guess = guessEggLocation();
        if (guess == null) return;
        possibleEggLocations = List.of(guess);
        drawLocations = true;
        warningPending = false;
    }

    // HoppityEggLocator.onItemClick: right click with the egglocator
    public static void onItemClick(boolean rightClick) {
        if (!rightClick || !HoppityEnv.world.locatorInHand()) return;
        if (!isEnabled()) return;
        if (HoppityEnv.passedSince(lastClick) < 5000) return;
        long clickTime = HoppityEnv.now();
        lastClick = clickTime;
        warningPending = true;
        HoppityApi.mythicRabbitCheck();
        bezierFitter.reset();
        Hoppity.later(5500, () -> warnIfNoGuessFound(clickTime));
    }

    private static void warnIfNoGuessFound(long clickTime) {
        if (lastClick != clickTime) return;
        if (!warningPending) return;
        if (bezierFitter.isEmpty()) return;
        if (HoppityEggLocations.islandLocations().isEmpty()) return;
        HoppityEnv.world.chat("Egglocator result was unreliable, please use it again!");
    }

    // landing point snapped to the nearest known spot (within 5 blocks, inside the island)
    static Vec3 guessEggLocation() {
        Vec3 guess = bezierFitter.solve();
        if (guess == null) return null;
        if (!DianaData.inBounds(HoppityEnv.world.island(), guess)) return null;
        Vec3 closest = null;
        double bestD = Double.MAX_VALUE;
        for (Vec3 egg : HoppityEggLocations.islandLocations()) {
            double d = egg.distanceToSqr(guess);
            if (d < bestD) {
                bestD = d;
                closest = egg;
            }
        }
        if (closest == null) return null;
        return closest.distanceTo(guess) <= MAX_GUESS_DISTANCE ? closest : null;
    }

    public static boolean isValidEggLocation(Vec3 location) {
        for (Vec3 egg : HoppityEggLocations.islandLocations()) if (egg.distanceTo(location) < 5.0) return true;
        return false;
    }

    public static boolean isEnabled() {
        return HoppityEnv.world.inSkyBlock() && Hoppity.eggsEnabled() && !"GARDEN".equals(HoppityEnv.world.island()) && !HoppityEnv.world.isBusy() && HoppityTime.isHoppityEvent();
    }
}
