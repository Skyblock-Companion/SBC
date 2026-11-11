// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.milestones;

import io.muzy.sbc.events.hoppity.HoppityEnv;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// from skyhanni GardenCropMilestoneDisplay (drawProgressDisplay) + GardenBestCropTime: milestone lines for a
// crop and the time to next tier list over all crops
public final class GardenCropMilestoneDisplay {
    public static boolean showMaxTier, overflowDisplay;
    public static int blocksBrokenPrecision = 2;
    // CustomGoals: tier this crop is farmed toward, 0 = next one
    public static java.util.function.ToIntFunction<CropType> customGoal = c -> 0;
    // config.cropMilestones.highestTimeFormat
    public static java.util.function.LongFunction<String> timeFormat = ms -> HoppityEnv.format(ms, 2);
    // tryWarn: ms left, the crop, the tier it reaches
    public static TierSoon tierSoon = (ms, crop, tier) -> {};
    // config.cropMilestones.next.bestType: rank by garden xp per time instead of by time
    public static boolean bestByGardenExp;

    public interface TierSoon {
        void left(long ms, CropType crop, int tier);
    }

    // GardenBestCropTime.timeTillNextCrop
    public static final Map<CropType, Long> timeTillNextCrop = new EnumMap<>(CropType.class);

    private GardenCropMilestoneDisplay() {}

    static String sep(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // drawProgressDisplay. farmingFortune = tab's Farming Fortune
    // (FarmingFortuneDisplay.getCurrentFarmingFortune), speed = GardenCropSpeed.averageBlocksPerSecond
    // counterKnown = held tool has a readable counter (else "Inaccurate!")
    public static List<String> drawProgressDisplay(CropType crop, boolean isCurrentlyFarmed, double farmingFortune, double speed, boolean counterKnown) {
        List<String> out = new ArrayList<>();
        if (CropMilestonesApi.missingMilestoneRepoData) {
            out.add("§cMissing Milestone Repo Data!");
            return out;
        }
        Long counter = CropMilestonesApi.getMilestoneCounter(crop);
        Integer currentTier = CropMilestonesApi.getCurrentMilestoneTier(crop);
        if (counter == null || currentTier == null) {
            if (CropMilestonesApi.inaccurateMilestone) out.add("§cOpen §e/cropmilestones §cto update!");
            return out;
        }
        int maxTier = CropMilestonesApi.getMaxTier();
        boolean useMaxTier = currentTier < maxTier && showMaxTier;
        int nextTier = useMaxTier ? maxTier : currentTier + 1;
        int goal = customGoal.applyAsInt(crop);
        boolean useGoal = goal > 0 && goal > nextTier;
        if (useGoal) nextTier = goal;
        long have, need;
        if (useGoal) {
            have = counter;
            need = CropMilestonesApi.milestoneTotalCropsForTier(crop, goal);
        } else if (useMaxTier) {
            have = counter;
            need = CropMilestonesApi.getMaxedMilestoneAmount(crop);
        } else {
            Long h = CropMilestonesApi.milestoneProgressToNextTier(crop);
            Long n = CropMilestonesApi.milestoneNextTierAmount(crop);
            if (h == null || n == null) return out;
            have = h;
            need = n;
        }
        boolean maxed = CropMilestonesApi.isMaxMilestone(crop);
        out.add("§6Crop Milestones");
        out.add(maxed && !overflowDisplay ? "§7" + crop.cropName + " §eMAXED" : "§7" + crop.cropName + " §8" + currentTier + "➜§3" + nextTier);
        out.add(maxed && !overflowDisplay ? "§7Counter: §e" + sep(counter) : "§e" + sep(have) + "§8/§e" + sep(need));
        int farmingFortuneSpeed = (int) Math.round((100.0 + farmingFortune) * crop.baseDrops * speed / 100 * 10) / 10;
        if (farmingFortuneSpeed > 0 && isCurrentlyFarmed) {
            GardenCropSpeed.setSpeed(crop, farmingFortuneSpeed);
            if (!maxed || overflowDisplay) {
                long missing = need - have;
                long missingMs = missing / farmingFortuneSpeed * 1000L;
                timeTillNextCrop.put(crop, missingMs);
                tierSoon.left(missingMs, crop, nextTier);
                String speedText = "§7In §b" + timeFormat.apply(missingMs);
                out.add(counterKnown ? speedText : speedText + " §7Inaccurate!");
            }
            out.add("§7Crops/Minute§8: §e" + sep(farmingFortuneSpeed * 60L));
            out.add("§7Blocks/Second§8: §e" + String.format(Locale.ROOT, "%." + blocksBrokenPrecision + "f", speed));
        }
        if (CropMilestonesApi.inaccurateMilestone) out.add("§cOpen §e/cropmilestones §cto update!");
        return out;
    }

    // GardenBestCropTime.updateTimeTillNextCrop
    public static void updateTimeTillNextCrop() {
        for (CropType crop : CropType.values()) {
            if (CropMilestonesApi.isMaxMilestone(crop)) continue;
            Integer speed = GardenCropSpeed.getSpeed(crop);
            if (speed == null || speed == 0) continue;
            Integer currentTier = CropMilestonesApi.getCurrentMilestoneTier(crop);
            Long have = CropMilestonesApi.milestoneProgressToNextTier(crop);
            if (currentTier == null || have == null) return;
            long need = showMaxTier ? CropMilestonesApi.milestoneTotalCropsForTier(crop, currentTier) : CropMilestonesApi.milestoneTierAmount(crop, currentTier + 1);
            long missing = need - have;
            timeTillNextCrop.put(crop, missing / speed * 1000L);
        }
    }

    // GardenBestCropTime.getGardenExpForTier
    static int gardenExpForTier(int tier) {
        return tier > 30 ? 300 : tier * 10;
    }

    // GardenBestCropTime.drawBestDisplay
    public static List<String> drawBestDisplay(CropType currentCrop, int showOnlyBest, boolean showCurrent) {
        List<String> out = new ArrayList<>();
        updateTimeTillNextCrop();
        out.add(bestByGardenExp ? "§eBest Crop Time §7(§2Garden Experience§7)" : "§eBest Crop Time §7(§bSkyBlock Level§7)");
        List<Map.Entry<CropType, Long>> sorted = new ArrayList<>(timeTillNextCrop.entrySet());
        sorted.removeIf(e -> CropMilestonesApi.isMaxMilestone(e.getKey()));
        if (bestByGardenExp) {
            sorted.removeIf(e -> CropMilestonesApi.getCurrentMilestoneTier(e.getKey()) == null);
            sorted.sort(java.util.Comparator.comparingLong(e -> e.getValue() / gardenExpForTier(CropMilestonesApi.getCurrentMilestoneTier(e.getKey()) + 1)));
        } else sorted.sort(Map.Entry.comparingByValue());
        if (sorted.isEmpty()) {
            out.add("§cFarm crops to add them to this list!");
            return out;
        }
        int index = 0;
        for (var e : sorted) {
            index++;
            CropType crop = e.getKey();
            Integer currentTier = CropMilestonesApi.getCurrentMilestoneTier(crop);
            if (currentTier == null) continue;
            boolean isCurrent = crop == currentCrop;
            if (index > showOnlyBest && (!showCurrent || !isCurrent)) continue;
            int nextTier = showMaxTier ? 46 : currentTier + 1;
            String color = isCurrent ? "§e" : "§7";
            // GardenNextJacobContest.isNextCrop
            if (io.muzy.sbc.garden.contest.NextContest.isNextCrop(crop)) color += "§n";
            out.add("§7" + index + "# " + color + crop.cropName + " " + currentTier + "➜" + nextTier + "§r §b" + timeFormat.apply(e.getValue()));
        }
        return out;
    }
}
