// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.garden.milestones.CropMilestonesApi;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.WrongDataFix;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

// from skyhanni GardenInventoryNumbers, GardenCropMilestoneInventory, GardenInventoryTooltipOverflow: the
// crop milestones menu
// tier on each crop, the average, the road to the last tier in the tooltip and past it
public final class MilestoneMenu {
    public static final String ID = "garden_milestone_menu";
    private static final String TITLE = "Crop Milestones";

    private MilestoneMenu() {}

    // GardenCropMilestoneInventory.updateAverage over all crops, unknown tier counts as nothing
    static double average(boolean overflow) {
        double sum = 0;
        for (CropType c : CropType.values()) {
            Integer tier = CropMilestonesApi.getCurrentMilestoneTier(c);
            if (tier != null) sum += overflow ? tier : Math.min(tier, 46);
        }
        return Math.round(sum / CropType.values().length * 100) / 100.0;
    }

    // StringUtils.progressBar, 20 chars
    static String bar(double p, int width) {
        int done = (int) Math.round(Math.max(0, Math.min(1, p)) * width);
        return "§2§l§m" + " ".repeat(done) + "§f§l§m" + " ".repeat(width - done) + "§r";
    }

    static void mark(MenuView v) {
        boolean overflow = MenuMarks.setting(ID, "overflow_numbers");
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            CropType crop = WrongDataFix.getCropTypeByLore(it.lore);
            Integer tier = crop == null ? null : CropMilestonesApi.getCurrentMilestoneTier(crop);
            if (tier == null || !MenuMarks.setting(ID, "tiers")) continue;
            v.number(slot, String.valueOf(overflow ? tier : Math.min(tier, CropMilestonesApi.getMaxTier())));
        }
        if (MenuMarks.setting(ID, "average")) v.label(2, "§6Average Crop Milestone: §e" + String.format(Locale.ROOT, "%.2f", average(overflow)), -50, -38, 1f);
    }

    // addMaxMilestoneProgress + the overflow tooltip
    private static void tooltip(ItemStack stack, List<Component> lines) {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen == null || !TITLE.equals(GameState.strip(screen.getTitle().getString()).trim())) return;
        CropType crop = WrongDataFix.getCropTypeByLore(LegacyText.lore(stack));
        Integer tier = crop == null ? null : CropMilestonesApi.getCurrentMilestoneTier(crop);
        if (tier == null) return;
        if (GardenPack.on(ID, "total") && tier < 20) { // hypixel shows the road to the last tier itself from 20 on
            int maxTier = CropMilestonesApi.getMaxTier();
            long max = CropMilestonesApi.milestoneTotalCropsForTier(crop, maxTier);
            Long counter = CropMilestonesApi.getMilestoneCounter(crop);
            int index = -1;
            for (int i = 0; i < lines.size(); i++) if (lines.get(i).getString().equals("Rewards:")) index = i;
            if (index >= 0 && counter != null && max > 0) {
                double p = (double) counter / max;
                lines.add(index, Component.literal(" "));
                lines.add(index, Component.literal(bar(p, 19) + " §e" + String.format(Locale.ROOT, "%,d", counter) + "§6/§e" + io.muzy.sbc.core.Prices.coins(max)));
                lines.add(index, Component.literal("§7Progress to Tier " + maxTier + ": §e" + String.format(Locale.ROOT, "%.1f%%", p * 100)));
            }
        }
        if (GardenPack.on(ID, "overflow_tip")) overflowTip(crop, tier, lines);
    }

    private static void overflowTip(CropType crop, int tier, List<Component> lines) {
        Long have = CropMilestonesApi.milestoneProgressToNextTier(crop), need = CropMilestonesApi.milestoneNextTierAmount(crop);
        if (have == null || need == null || need == 0) return;
        double p = (double) have / need;
        boolean next = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).getString();
            if (line.contains("Max tier reached!")) {
                lines.set(i, Component.literal("§7Progress to tier " + (tier + 1) + ": §e" + String.format(Locale.ROOT, "%.1f%%", p * 100)));
                next = true;
            } else if (next && line.contains("                    ")) {
                lines.set(i, Component.literal(bar(p, 20) + " §e" + String.format(Locale.ROOT, "%,d", have) + "§6/§e" + String.format(Locale.ROOT, "%,d", need)));
                return;
            }
        }
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Crop Milestones menu", "In the Crop Milestones menu: the tier on each crop, the average of them, and in the tooltip the road to the last tier.", "SkyHanni: Numbers")
                .when(GardenPack::garden).tooltip(MilestoneMenu::tooltip)
                .option(Option.bool("garden_milestone_menu.tiers", "Tier on each crop", "", true))
                .option(Option.bool("garden_milestone_menu.average", "Average milestone", "", true))
                .option(Option.bool("garden_milestone_menu.total", "Road to the last tier in the tooltip", "Hypixel shows it itself from tier 20 on.", true))
                .option(Option.bool("garden_milestone_menu.overflow_numbers", "Overflow tiers on the crops", "Tiers past the last one, and they count in the average.", false))
                .option(Option.bool("garden_milestone_menu.overflow_tip", "Overflow progress in the tooltip", "Instead of Max tier reached.", false)));
        MenuMarks.add(ID, TITLE::equals, MilestoneMenu::mark);
    }
}
