// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.greenhouse;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;

// from skyhanni HarvestableHighlight: in crop diagnostics, Growth Status and Water Status items in colour
public final class CropDiagnostics {
    public static final String ID = "garden_crop_diagnostics";
    private static final int GROWTH_SLOT = 24, WATER_SLOT = 21;

    private CropDiagnostics() {}

    // growth status beacon colour: green = harvestable, yellow = says what it drops, else red. 0 = no beacon
    static int growth(MenuView.Item beacon) {
        if (beacon.empty() || !beacon.model.equals("beacon")) return 0;
        int color = MenuView.RED;
        for (String line : beacon.cleanLore) {
            if (line.equals("Status: Harvestable")) color = MenuView.GREEN;
            else if (line.startsWith("Drops: ") || line.startsWith("Rewards: ")) color = MenuView.YELLOW;
        }
        return color;
    }

    // green for a crop with enough water or needs none, else 0
    static int water(MenuView.Item bucket) {
        if (bucket.empty() || !bucket.model.equals("water_bucket")) return 0;
        for (String line : bucket.cleanLore) if (line.equals("This crop has enough water to") || line.equals("This crop doesn't need water!")) return MenuView.GREEN;
        return 0;
    }

    static void mark(MenuView v) {
        if (MenuMarks.setting(ID, "growth")) {
            int c = growth(v.item(GROWTH_SLOT));
            if (c != 0) v.back(GROWTH_SLOT, c);
        }
        if (MenuMarks.setting(ID, "water")) {
            int c = water(v.item(WATER_SLOT));
            if (c != 0) v.back(WATER_SLOT, c);
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Crop Diagnostics colours", "In the Crop Diagnostics menu: the Growth Status is green when the crop can be harvested, and the Water Status green when it has enough water.", "SkyHanni: Highlight Harvestable Status")
                .when(GardenPack::garden)
                .option(Option.bool("garden_crop_diagnostics.growth", "Colour the Growth Status", "", true))
                .option(Option.bool("garden_crop_diagnostics.water", "Colour the Water Status", "", true)));
        MenuMarks.add(ID, Patterns.get("SH.garden.greenhouse.growthcycle.inventory"), CropDiagnostics::mark);
    }
}
