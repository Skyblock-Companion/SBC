// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.farming;

import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.garden.milestones.CropMilestonesApi;
import io.github.zmuzy.sbc.garden.milestones.CropType;
import io.github.zmuzy.sbc.garden.milestones.GardenCropMilestoneDisplay;
import io.github.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import io.github.zmuzy.sbc.garden.milestones.GardenMilestones;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

// from skyhanni CustomGoals + GardenCropMilestoneDisplay tryWarn and its highest time unit, the crop
// milestone card settings
// a goal is a tier per crop, the card counts toward it instead of the next tier. one number per crop (0 =
// none) instead of skyhanni's crop list + a number each
public final class MilestoneGoals {
    private static final String ID = GardenMilestones.MILESTONES;
    private static int warnedTier = -1;
    private static long warnUntil, nextWarn;
    private static String warnName = "";

    private MilestoneGoals() {}

    // Duration.format(biggestUnit): "1h 30m", or with minutes as highest "90m"
    static String format(long ms, String highest) {
        long s = Math.max(0, ms / 1000);
        long[] sizes = {365 * 86400L, 86400L, 3600L, 60L, 1L};
        String[] names = {"y", "d", "h", "m", "s"};
        int start = switch (highest) {
            case "Day" -> 1;
            case "Hour" -> 2;
            case "Minute" -> 3;
            case "Second" -> 4;
            default -> 0;
        };
        StringBuilder out = new StringBuilder();
        int parts = 0;
        for (int i = start; i < sizes.length && parts < 2; i++) {
            long n = s / sizes[i];
            if (n == 0 && !(i == sizes.length - 1 && parts == 0)) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(n).append(names[i]);
            s -= n * sizes[i];
            parts++;
        }
        return out.toString();
    }

    private static String key(CropType crop) {
        return "goal_" + crop.name().toLowerCase(Locale.ROOT);
    }

    // tryWarn: 6s before the tier while breaking crops, once per tier, unless the counter is a guess
    private static void tierSoon(long ms, CropType crop, int tier) {
        long now = System.currentTimeMillis();
        if (!GardenPack.on(ID, "warnClose") || now - GardenCropSpeed.lastBrokenTime >= 500 || ms > 6000) {
            warnUntil = 0;
            return;
        }
        Integer current = CropMilestonesApi.getCurrentMilestoneTier(crop);
        if (current == null || current == warnedTier || CropMilestonesApi.inaccurateMilestone) return;
        warnedTier = current;
        warnUntil = now + ms;
        warnName = "§b" + crop.cropName + " " + tier;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (warnUntil <= now || now < nextWarn) return;
        nextWarn = now + 1000;
        GardenPack.title(warnName + " in " + (warnUntil - now + 999) / 1000 + "s", 1);
        GardenPack.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f);
    }

    public static void register() {
        Feature f = Features.get(ID);
        if (f == null) return;
        f.option(Option.bool("garden_crop_milestones.warnClose", "Warn when a tier is close", "A title and a sound in the last seconds before the next tier, for switching to your best equipment in time.", true));
        f.option(Option.choice("garden_crop_milestones.timeFormat", "Highest time unit", "1h 30m with hours, 90m with minutes.", "Year", "Year", "Day", "Hour", "Minute", "Second"));
        String goal = "The tier the card counts towards for this crop. 0 is the next tier.";
        f.option(Option.integer("garden_crop_milestones.goal_wheat", "Wheat goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_carrot", "Carrot goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_potato", "Potato goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_nether_wart", "Nether Wart goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_pumpkin", "Pumpkin goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_melon", "Melon goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_cocoa_beans", "Cocoa Beans goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_sugar_cane", "Sugar Cane goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_cactus", "Cactus goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_mushroom", "Mushroom goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_sunflower", "Sunflower goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_moonflower", "Moonflower goal", goal, 0, 0, 500));
        f.option(Option.integer("garden_crop_milestones.goal_wild_rose", "Wild Rose goal", goal, 0, 0, 500));
        GardenCropMilestoneDisplay.customGoal = crop -> GardenPack.number(ID, key(crop), 0);
        GardenCropMilestoneDisplay.timeFormat = ms -> format(ms, GardenPack.choice(ID, "timeFormat", "Year"));
        GardenCropMilestoneDisplay.tierSoon = MilestoneGoals::tierSoon;
        Safe.endTick("MilestoneGoals.tick", MilestoneGoals::tick);
    }
}
