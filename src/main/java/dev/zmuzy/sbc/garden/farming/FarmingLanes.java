// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.CropType;
import dev.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni lane: farm two layers with /sbc lanedetection on and the mod knows where your lane turns
// then a card with distance + time to the lane end, a warning a few secs before, and corners as waypoints.
// only tells you, turning is still on you
public final class FarmingLanes {
    public static final String ID = "garden_lanes";
    private static final int NORTH_SOUTH = 0, EAST_WEST = 1;
    // {direction, min, max}, null = none
    private static double[] current;
    private static boolean detection;
    private static Vec3 start, lastLocation, potentialEnd;
    private static CropType detectCrop;
    private static double maxDistance, currentDistance, lastSpeed, position = Double.NaN;
    private static int lastDirection, sameSpeed;
    private static long lastNoLane, lastFarming, lastSound, titleUntil, timeRemaining = -1;
    private static boolean normal;
    private static String state = "";

    private FarmingLanes() {}

    private static boolean timeFlower(CropType c) {
        return c == CropType.SUNFLOWER || c == CropType.MOONFLOWER;
    }

    // createLane
    static double[] lane(Vec3 a, Vec3 b) {
        int dir = Math.abs(a.z - b.z) > Math.abs(a.x - b.x) ? NORTH_SOUTH : EAST_WEST;
        double va = value(dir, a), vb = value(dir, b);
        return new double[]{dir, Math.min(va, vb), Math.max(va, vb)};
    }

    private static double value(int direction, Vec3 at) {
        return direction == NORTH_SOUTH ? at.z : at.x;
    }

    private static void saveLane(Vec3 a, Vec3 b, CropType crop) {
        double[] lane = lane(a, b);
        var lanes = GardenMilestones.profile().farmingLanes;
        if (timeFlower(crop)) {
            lanes.put(CropType.SUNFLOWER.name(), lane);
            lanes.put(CropType.MOONFLOWER.name(), lane);
        } else lanes.put(crop.name(), lane);
        Store.markDirty();
        current = lane;
        GardenPack.chat("§a" + crop.cropName + " lane saved! The lane features work now.");
        resetDetection();
    }

    private static void resetDetection() {
        start = null;
        lastLocation = null;
        potentialEnd = null;
        detectCrop = null;
        maxDistance = 0;
        detection = false;
    }

    // FarmingLaneCreator.onCropClick + FarmingLaneApi.onCropClick
    private static void onCrop(CropType crop) {
        var mc = Minecraft.getInstance();
        if (!Features.on(ID) || !GardenPack.garden() || mc.player == null) return;
        if (detection) {
            Vec3 location = mc.player.position();
            if (lastLocation == null) {
                start = location;
                maxDistance = 0;
                lastLocation = location;
                detectCrop = crop;
            } else if (detectCrop != crop && !(timeFlower(detectCrop) && timeFlower(crop))) {
                GardenPack.chat("§7Different crop broken, stopping lane detection.");
                resetDetection();
            } else if (lastLocation.distanceTo(location) >= 0.5) {
                lastLocation = location;
                double distance = start.distanceTo(location);
                if (distance > maxDistance) {
                    maxDistance = distance;
                    potentialEnd = null;
                } else if (potentialEnd == null) potentialEnd = location;
                else if (potentialEnd.distanceTo(location) > 2) saveLane(start, potentialEnd, detectCrop);
            }
        }
        if (!GardenMilestones.hasToolInHand()) return;
        double[] lane = GardenMilestones.profile().farmingLanes.get(crop.name());
        long now = System.currentTimeMillis();
        if (lane == null && current == null && !detection && !GardenPack.choice(ID, "ignored", "").toLowerCase(Locale.ROOT).contains(crop.cropName.toLowerCase(Locale.ROOT)) && now - lastNoLane >= 30_000) {
            lastNoLane = now;
            GardenPack.chat("§7No " + crop.cropName + " lane defined yet. Use §e/sbc lanedetection");
        }
        current = lane;
    }

    // calculateMovementState
    private static void movement(double speed) {
        if (lastSpeed != speed) {
            lastSpeed = speed;
            sameSpeed = 0;
        }
        sameSpeed++;
        normal = false;
        if (speed == 0 && sameSpeed > 1) state = "§ePaused";
        else if (speed < 1 && sameSpeed > 5) state = "§cToo slow!";
        else if (sameSpeed < 6) state = "§aCalculating..";
        else {
            state = "";
            normal = true;
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || !GardenPack.garden() || current == null) return;
        long now = System.currentTimeMillis();
        int dir = (int) current[0];
        double at = dir == NORTH_SOUTH ? mc.player.getZ() : mc.player.getX();
        if (at < current[1] || at > current[2]) {
            timeRemaining = -1;
            return;
        }
        if (Double.isNaN(position)) {
            position = at;
            return;
        }
        double diff = position - at;
        position = at;
        int direction = diff > 0 ? 1 : diff < 0 ? -1 : 0;
        if (direction == 1) currentDistance = Math.abs(current[1] - at);
        else if (direction == -1) currentDistance = Math.abs(current[2] - at);
        if (direction != lastDirection) {
            lastFarming = 0;
            lastDirection = direction;
        }
        if (!GardenMilestones.isCurrentlyFarming()) return;
        // MovementSpeedDisplay.bpsMoveSpeed: last tick's distance x 20
        double speed = Math.round(Math.sqrt(mc.player.distanceToSqr(mc.player.xOld, mc.player.yOld, mc.player.zOld)) * 20 * 100) / 100.0;
        movement(speed);
        if (!normal) return;
        timeRemaining = (long) (currentDistance / speed * 1000);
        long warnAt = GardenPack.number(ID, "seconds", 5) * 1000L;
        if (timeRemaining >= warnAt) {
            lastFarming = now;
            return;
        }
        if (now - lastFarming >= warnAt || !GardenPack.on(ID, "notify")) return;
        if (now >= titleUntil) {
            titleUntil = now + warnAt;
            GardenPack.title(GardenPack.choice(ID, "text", "&eLane Switch incoming.").replace("&", "§"), (int) (warnAt / 1000));
        }
        if (now - lastSound >= GardenPack.number(ID, "repeat_ticks", 20) * 50L) {
            lastSound = now;
            GardenPack.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f);
        }
    }

    private static void draw(WorldDraw w) {
        if (detection) {
            if (start != null) w.waypoint(start, 0xFFFFFFFF, "start", true, false);
            if (potentialEnd != null) w.waypoint(potentialEnd, 0xFFFFFFFF, "potential end", true, false);
        }
        if (current == null || !GardenPack.on(ID, "corners") || w.mc.player == null) return;
        boolean ns = current[0] == NORTH_SOUTH;
        Vec3 p = w.mc.player.position();
        double y = Math.min(p.y, 76);
        w.waypoint(ns ? new Vec3(p.x, y, current[1]) : new Vec3(current[1], y, p.z), 0xFFFFFF55, "§eLane Corner", true, false);
        w.waypoint(ns ? new Vec3(p.x, y, current[2]) : new Vec3(current[2], y, p.z), 0xFFFFFF55, "§eLane Corner", true, false);
    }

    private static List<RichCard.Line> lines(double distance, String time) {
        return List.of(RichCard.Line.row("Distance", "§e" + String.format(Locale.ROOT, "%.1f", distance)), RichCard.Line.row("Time", time));
    }

    private static final RichCard CARD = new RichCard(ID, "Farming lane", "Garden", Zone.TOP_LEFT, 39, ID) {
        @Override
        public boolean visible() {
            return GardenPack.garden() && current != null && GardenPack.on(ID, "distance") && timeRemaining >= 0 && GardenMilestones.isCurrentlyFarming();
        }

        @Override
        protected Content live() {
            return new Content("", lines(currentDistance, (normal ? "§b" : "§8") + SbTime.dur(Math.max(0, timeRemaining)) + (state.isEmpty() ? "" : " " + state)));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(61.4, "§b12s"));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Distance", String.format(Locale.ROOT, "%.1f", currentDistance), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Distance", "61.4", 0), new Row("Time", "12s", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Farming lanes", "Learns where your lane turns with /sbc lanedetection. Then the distance and time to its end, a warning shortly before, and its corners in the world.", "SkyHanni: Farming Lane")
                .when(GardenPack::garden).defaultOff().tick(FarmingLanes::tick).world(FarmingLanes::draw)
                .option(Option.bool("garden_lanes.distance", "Distance and time card", "", true))
                .option(Option.bool("garden_lanes.corners", "Corners as waypoints", "", false))
                .option(Option.bool("garden_lanes.notify", "Warn before the end", "A title and a sound.", true))
                .option(Option.integer("garden_lanes.seconds", "Warn this long before (s)", "", 5, 1, 30))
                .option(Option.text("garden_lanes.text", "Warning text", "Use & for colours.", "&eLane Switch incoming."))
                .option(Option.integer("garden_lanes.repeat_ticks", "Repeat the sound each (ticks)", "20 is once a second.", 20, 1, 100))
                .option(Option.text("garden_lanes.ignored", "Crops without a lane", "Names of crops, with commas, you do not want to be told about.", "")));
        GardenCropSpeed.onCrop(FarmingLanes::onCrop);
        Safe.commands("FarmingLanes.commands", (d, r) -> d.register(literal("sbc").then(literal("lanedetection").executes(c -> {
            if (!Features.on(ID)) {
                GardenPack.chat("§cTurn on Farming lanes in the settings first.");
                return 1;
            }
            detection = !detection;
            GardenPack.chat(detection ? "§7Enabled lane detection. Farm two layers to find the lane's ends." : "§7Stopped lane detection.");
            return 1;
        }))));
        dev.zmuzy.sbc.core.Commands.describe("lanedetection", "Learn where your farming lane turns: farm two layers");
        HudLayout.add(CARD);
    }
}
