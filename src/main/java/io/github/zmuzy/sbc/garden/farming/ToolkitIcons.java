// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.farming;

import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.garden.milestones.CropType;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import net.minecraft.client.Minecraft;

// from skyhanni ToolkitCropReplacer: in the farming toolkit, which crop each tool is for
// skyhanni swaps the tool picture for its crop, here the tool stays and the crop sits in its corner so
// nothing in hypixel's menu gets replaced
public final class ToolkitIcons {
    public static final String ID = "garden_toolkit_icons";

    private ToolkitIcons() {}

    static void mark(MenuView v) {
        var level = Minecraft.getInstance().level;
        boolean night = level != null && level.getOverworldClockTime() % 24000 >= 12000;
        for (int slot = 10; slot <= 24; slot++) {
            if (slot > 16 && slot < 20) continue;
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            CropType crop = CropType.fromToolId(it.id, night);
            if (crop != null) v.mark(slot).badge = LegacyItems.stack(crop.icon());
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Farming Toolkit crops", "In the Farming Toolkit, each tool's crop in its corner.", "SkyHanni: Farming Toolkit").when(GardenPack::skyblock));
        MenuMarks.add(ID, Patterns.get("SH.garden.pests-api.toolkit.inventory"), ToolkitIcons::mark);
    }
}
