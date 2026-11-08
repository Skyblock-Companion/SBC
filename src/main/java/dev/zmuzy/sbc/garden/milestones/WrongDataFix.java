// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.milestones;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.TabWidgets;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni WrongDataFix: keeps the counter honest from the "Crop Milestones" menu (§7Total:
// §a36,967,397), the GARDEN MILESTONE level up line, and the crop milestones tab widget (" Mushroom 21:
// 56.6%" / " Potato 46: MAX")
public final class WrongDataFix {
    static final Patterns.P CROP = Patterns.p("SH.data.garden.milestone.crop");
    static final Patterns.P TOTAL = Patterns.p("SH.data.garden.milestone.total");
    static final Patterns.P TAB_PERCENT = Patterns.p("SH.data.garden.milestone.tablist.percent-no-color");
    static final Patterns.P TAB_MAX = Patterns.p("SH.data.garden.milestone.tablist.max-no-color");
    static final Patterns.P LEVEL_UP = Patterns.p("SH.data.garden.milestone.levelup");

    private static final Map<CropType, Long> tabListCropProgress = new EnumMap<>(CropType.class);
    // not in skyhanni: when no counter is known at all (first use, nothing from the menu yet) we take the tab
    // value as the counter so the display starts right away. skyhanni waits for /cropmilestones
    public static boolean seedFromTabList = true;

    private WrongDataFix() {}

    // getCropTypeByLore: crop of a Crop Milestones menu item
    public static CropType getCropTypeByLore(List<String> lore) {
        for (String l : lore) {
            Matcher m = CROP.matcher(l);
            if (m.matches()) return CropType.getByNameOrNull(m.group("name"));
        }
        return null;
    }

    // onInventoryFullyOpened("Crop Milestones"): each item's formatted lore
    public static void onCropMilestonesMenu(List<List<String>> itemLores) {
        CropMilestonesApi.clearMilestoneCache();
        for (List<String> lore : itemLores) {
            CropType crop = getCropTypeByLore(lore);
            if (crop == null) continue;
            for (String l : lore) {
                Matcher m = TOTAL.matcher(l);
                if (!m.matches()) continue;
                long old = CropMilestonesApi.getMilestoneCounter(crop) == null ? 0 : CropMilestonesApi.getMilestoneCounter(crop);
                long amount = Long.parseLong(m.group("name").replace(",", ""));
                forceUpdateMilestone(crop, amount - old);
            }
        }
        CropMilestonesApi.inaccurateMilestone = false;
    }

    // formatted line
    public static boolean onChat(String formatted) {
        Matcher m = LEVEL_UP.matcher(formatted);
        if (!m.matches()) return false;
        CropType crop = CropType.getByNameOrNull(m.group("crop"));
        if (crop == null) return true;
        int tier = dev.zmuzy.sbc.item.enchants.EnchantParser.level(m.group("tier").trim());
        long crops = CropMilestonesApi.milestoneTotalCropsForTier(crop, tier);
        changedValue(crop, crops, "level up chat message", 0);
        return true;
    }

    // onWidgetUpdate(CROP_MILESTONE): the widget's plain lines
    public static void onWidget(List<String> lines) {
        for (String l : lines) {
            Matcher m = TAB_PERCENT.matcher(l);
            if (m.matches()) {
                checkTabDifference(m.group("crop"), Integer.parseInt(m.group("tier")), Double.parseDouble(m.group("percentage")));
                break;
            }
        }
        for (String l : lines) {
            Matcher m = TAB_MAX.matcher(l);
            if (m.matches()) {
                setCropToMaxTier(m.group("crop"), Integer.parseInt(m.group("tier")));
                break;
            }
        }
    }

    // reads CROP_MILESTONE out of the plain tab list
    public static void onTab(List<String> tab) {
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!w.def().id().equals("CROP_MILESTONE")) continue;
            List<String> lines = new java.util.ArrayList<>();
            for (int i : w.lines()) lines.add(tab.get(i));
            onWidget(lines);
            return;
        }
    }

    private static void changedValue(CropType crop, long tabListValue, String source, int minDiff) {
        Long calculated = CropMilestonesApi.getMilestoneCounter(crop);
        if (calculated == null) {
            if (seedFromTabList) forceUpdateMilestone(crop, tabListValue);
            return;
        }
        long diff = tabListValue - calculated;
        if (diff >= minDiff) forceUpdateMilestone(crop, diff);
    }

    private static void checkTabDifference(String cropName, int tier, double percentage) {
        CropType crop = CropType.getByNameOrNull(cropName.trim());
        if (crop == null) return;
        long baseCrops = CropMilestonesApi.milestoneTotalCropsForTier(crop, tier);
        long next = CropMilestonesApi.milestoneTotalCropsForTier(crop, tier + 1);
        long progressCrops = next - baseCrops;
        double progress = progressCrops * (percentage / 100);
        double smallestPercentage = progressCrops * 0.0005;
        long newValue = (long) (baseCrops + progress - smallestPercentage);
        Long known = tabListCropProgress.get(crop);
        if (known == null && seedFromTabList && CropMilestonesApi.getMilestoneCounter(crop) == null) changedValue(crop, newValue, "tab list (first read)", 0);
        else if (known != null && known != newValue) changedValue(crop, newValue, "tab list", (int) smallestPercentage);
        tabListCropProgress.put(crop, newValue);
    }

    private static void setCropToMaxTier(String cropName, int tier) {
        CropType crop = CropType.getByNameOrNull(cropName.trim());
        if (crop == null) return;
        long cropAmount = CropMilestonesApi.milestoneTotalCropsForTier(crop, tier);
        if (!CropMilestonesApi.isMaxMilestone(crop)) {
            long old = CropMilestonesApi.getMilestoneCounter(crop) == null ? 0 : CropMilestonesApi.getMilestoneCounter(crop);
            long change = cropAmount - old;
            if (change < 0) return;
            forceUpdateMilestone(crop, change);
        }
    }

    private static void forceUpdateMilestone(CropType crop, long amount) {
        if (amount == 0) return;
        CropMilestonesApi.addMilestoneCounter(crop, amount, false);
    }

    public static void reset() {
        tabListCropProgress.clear();
    }

    static String strip(String s) {
        return GameState.strip(s);
    }
}
