// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.CropType;
import dev.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni GardenStartLocation: where the farm of the held tool starts and where you last farmed with
// it, as waypoints
// set by the first crop you break with a tool, or /sbc cropstartlocation
public final class CropStartLocation {
    public static final String ID = "garden_crop_start";
    private static boolean showLast;

    private CropStartLocation() {}

    private static Store.GardenProfile store() {
        return GardenMilestones.profile();
    }

    private static double[] here() {
        var p = Minecraft.getInstance().player;
        return new double[]{p.getX(), p.getY(), p.getZ()};
    }

    private static void setCommand() {
        if (!GardenPack.garden()) {
            GardenPack.chat("§cThis only works on your Garden.");
            return;
        }
        if (!Features.on(ID)) {
            GardenPack.chat("§cTurn on Crop start location in the settings first.");
            return;
        }
        CropType crop = GardenMilestones.getCurrentlyFarmedCrop();
        if (crop == null) {
            GardenPack.chat("§cHold a crop's own farming tool in your hand.");
            return;
        }
        store().cropStartLocations.put(crop.name(), here());
        Store.markDirty();
        GardenPack.chat("§7You changed your start location for " + crop.cropName + ".");
    }

    // onCropClick
    private static void onCrop(CropType broken) {
        if (!Features.on(ID) || !GardenPack.garden() || !GardenMilestones.hasToolInHand() || Minecraft.getInstance().player == null) return;
        CropType crop = GardenMilestones.getCurrentlyFarmedCrop();
        if (crop == null || crop != GardenCropSpeed.lastBrokenCrop) return;
        if (!store().cropStartLocations.containsKey(crop.name())) {
            store().cropStartLocations.put(crop.name(), here());
            GardenPack.chat("§7Set your start location for " + crop.cropName + ".");
        }
        double[] h = here();
        store().cropLastFarmedLocations.put(crop.name(), new double[]{Math.floor(h[0]), Math.floor(h[1]), Math.floor(h[2])});
        Store.markDirty();
        showLast = false;
    }

    private static void draw(WorldDraw w) {
        CropType crop = GardenMilestones.cropInHand();
        if (crop == null) return;
        String mode = GardenPack.choice(ID, "mode", "Start");
        boolean both = mode.equals("Both");
        if (!mode.equals("Last farmed")) {
            double[] s = store().cropStartLocations.get(crop.name());
            if (s != null) w.waypoint(new Vec3(s[0], s[1], s[2]), 0xFFFFFFFF, "§b" + crop.cropName + (both ? " §aStart" : ""), false, false);
        }
        if (!mode.equals("Start")) {
            double[] l = store().cropLastFarmedLocations.get(crop.name());
            if (l == null) return;
            Vec3 at = new Vec3(l[0], l[1], l[2]);
            // only once you walked away from it
            if (at.distanceToSqr(w.mc.player.position()) >= 100) showLast = true;
            if (showLast) w.waypoint(at, 0xFFFF55FF, "§b" + crop.cropName + (both ? " §eLast farmed" : ""), true, true);
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Crop start location", "A waypoint at the start of the farm of the tool in your hand, and where you last farmed with it. Set it with /sbc cropstartlocation, or by breaking a first crop.", "SkyHanni: Crop Start Location")
                .when(GardenPack::garden).defaultOff().world(CropStartLocation::draw)
                .option(Option.choice("garden_crop_start.mode", "Show", "", "Start", "Start", "Last farmed", "Both")));
        GardenCropSpeed.onCrop(CropStartLocation::onCrop);
        Safe.commands("CropStartLocation.commands", (d, r) -> d.register(literal("sbc").then(literal("cropstartlocation").executes(c -> {
            setCommand();
            return 1;
        }))));
        dev.zmuzy.sbc.core.Commands.describe("cropstartlocation", "Set where the farm of the held tool starts");
    }
}
