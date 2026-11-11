// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.milestones;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Store;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// from skyhanni CropMilestonesApi: per crop milestone counter (per profile), tier math over Garden.json
// crop_milestones (tier amounts, total for a tier, current tier, progress, overflow past the last tier) and
// level ups
public final class CropMilestonesApi {
    public static Map<CropType, List<Integer>> cropMilestoneRepoData = new EnumMap<>(CropType.class);
    public static boolean inaccurateMilestone, missingMilestoneRepoData;
    private static Integer maxTier;
    private static final Map<CropType, Integer> tierCache = new EnumMap<>(CropType.class);
    private static final Map<CropType, Long> progressCache = new EnumMap<>(CropType.class);
    private static final Map<CropType, Long> maxMilestoneValue = new EnumMap<>(CropType.class);
    // where the counters live, swappable in tests
    public static Supplier<Store.GardenProfile> storage = () -> new Store.GardenProfile();
    public static Runnable onUpdate = () -> {};
    private static boolean loaded;

    private CropMilestonesApi() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (var in = io.muzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<CropType, List<Integer>> data = new EnumMap<>(CropType.class);
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("crop_milestones").entrySet()) {
                CropType crop;
                try {
                    crop = CropType.valueOf(e.getKey());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                List<Integer> tiers = new ArrayList<>();
                for (JsonElement t : e.getValue().getAsJsonArray()) tiers.add(t.getAsInt());
                data.put(crop, tiers);
            }
            cropMilestoneRepoData = data;
            missingMilestoneRepoData = false;
            clearMilestoneCache();
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad Garden.json", e);
        }
    }

    private static Map<String, Long> counters() {
        return storage.get().cropMilestoneCounter;
    }

    public static Long getMilestoneCounter(CropType crop) {
        return counters().get(crop.name());
    }

    public static boolean isMaxMilestone(CropType crop) {
        Long counter = getMilestoneCounter(crop);
        return counter != null && counter >= getMaxedMilestoneAmount(crop);
    }

    public static Integer getCurrentMilestoneTier(CropType crop) {
        Integer t = tierCache.get(crop);
        if (t != null) return t;
        Long counter = getMilestoneCounter(crop);
        if (counter == null) return null;
        t = milestoneTierFromCropCount(crop, counter);
        tierCache.put(crop, t);
        return t;
    }

    public static int getMaxTier() {
        if (maxTier != null) return maxTier;
        for (List<Integer> l : cropMilestoneRepoData.values()) return l.size();
        missingMilestoneRepoData = true;
        return 0;
    }

    public static long getMaxedMilestoneAmount(CropType crop) {
        return maxMilestoneValue.computeIfAbsent(crop, c -> {
            long sum = 0;
            for (int v : getMilestoneTiersList(c)) sum += v;
            return sum;
        });
    }

    public static Long milestoneProgressToNextTier(CropType crop) {
        Long p = progressCache.get(crop);
        if (p != null) return p;
        p = milestoneCalculateTierProgress(crop);
        if (p != null) progressCache.put(crop, p);
        return p;
    }

    public static Double percentToNextMilestone(CropType crop) {
        Long progress = milestoneProgressToNextTier(crop);
        Long limit = milestoneNextTierAmount(crop);
        if (progress == null || limit == null || limit == 0) return null;
        return progress.doubleValue() / limit;
    }

    public static Long milestoneNextTierAmount(CropType crop) {
        Integer tier = getCurrentMilestoneTier(crop);
        return tier == null ? null : milestoneTierAmount(crop, tier + 1);
    }

    // crops for just that tier (milestone 46 is 3M)
    public static long milestoneTierAmount(CropType crop, int tier) {
        if (tier <= 0) return 0;
        int overflowTier = Math.min(tier - 1, getMaxTier() - 1);
        List<Integer> data = getMilestoneTiersList(crop);
        if (overflowTier < 0 || overflowTier >= data.size()) return 0;
        return data.get(overflowTier);
    }

    public static long milestoneTotalCropsForTier(CropType crop, int requestedTier) {
        if (requestedTier == 0) return 0;
        long totalCrops = 0;
        int tier = 0;
        List<Integer> cropMilestone = getMilestoneTiersList(crop);
        int definedTiers = cropMilestone.size();
        if (definedTiers == 0) return 0;
        if (requestedTier <= definedTiers) {
            for (int tierCrops : cropMilestone) {
                totalCrops += tierCrops;
                tier++;
                if (tier == requestedTier) return totalCrops;
            }
            return totalCrops;
        }
        for (int tierCrops : cropMilestone) totalCrops += tierCrops;
        int additionalTiers = requestedTier - definedTiers;
        long lastIncrement = cropMilestone.get(cropMilestone.size() - 1);
        return totalCrops + lastIncrement * additionalTiers;
    }

    // addMilestoneCounter: counts crops, re-levels, tells the display
    public static void addMilestoneCounter(CropType crop, long counter, boolean sendLevelUp) {
        if (counter == 0) return;
        progressCache.merge(crop, counter, Long::sum);
        long old = getMilestoneCounter(crop) == null ? 0 : getMilestoneCounter(crop);
        counters().put(crop.name(), old + counter);
        Store.markDirty();
        milestoneCheckProgress(crop, sendLevelUp);
        onUpdate.run();
    }

    private static void milestoneCheckProgress(CropType crop, boolean sendLevelUp) {
        Long tierProgress = milestoneProgressToNextTier(crop);
        Long tierCutoff = milestoneNextTierAmount(crop);
        if (tierProgress == null || tierCutoff == null) return;
        if (tierProgress >= tierCutoff) {
            Integer oldLevel = getCurrentMilestoneTier(crop);
            Long counter = getMilestoneCounter(crop);
            if (oldLevel == null || counter == null) return;
            int newLevel = milestoneTierFromCropCount(crop, counter);
            if (sendLevelUp && newLevel > getMaxTier()) overflowLevelUp = new int[]{crop.ordinal(), Math.max(oldLevel, getMaxTier()), newLevel};
            tierCache.put(crop, newLevel);
            Long p = milestoneCalculateTierProgress(crop);
            if (p != null) progressCache.put(crop, p);
            return;
        }
        if (tierProgress < 0) {
            Long counter = getMilestoneCounter(crop);
            if (counter == null) return;
            tierCache.put(crop, milestoneTierFromCropCount(crop, counter));
            Long p = milestoneCalculateTierProgress(crop);
            if (p != null) progressCache.put(crop, p);
        }
    }

    // {crop ordinal, old level, new level} of the last overflow level up (onOverflowLevelUp), the glue consumes it
    public static int[] overflowLevelUp;

    static List<Integer> getMilestoneTiersList(CropType crop) {
        load();
        List<Integer> l = cropMilestoneRepoData.get(crop);
        if (l == null) {
            missingMilestoneRepoData = true;
            return List.of();
        }
        return l;
    }

    public static int milestoneTierFromCropCount(CropType crop, long count) {
        int tier = 0;
        long totalCrops = 0;
        List<Integer> cropMilestone = getMilestoneTiersList(crop);
        if (cropMilestone.isEmpty()) return 0;
        long maxMilestoneAmount = getMaxedMilestoneAmount(crop);
        int last = cropMilestone.get(cropMilestone.size() - 1);
        if (count < maxMilestoneAmount) {
            for (int tierCrops : cropMilestone) {
                totalCrops += tierCrops;
                if (totalCrops >= count) return tier;
                tier++;
            }
            return tier;
        }
        tier = getMaxTier();
        totalCrops = count - maxMilestoneAmount;
        return tier + (int) Math.floorDiv(totalCrops, last);
    }

    private static Long milestoneCalculateTierProgress(CropType crop) {
        Long progress = getMilestoneCounter(crop);
        Integer startTier = getCurrentMilestoneTier(crop);
        if (progress == null || startTier == null) return null;
        return progress - milestoneTotalCropsForTier(crop, startTier);
    }

    public static void clearMilestoneCache() {
        tierCache.clear();
        progressCache.clear();
        maxMilestoneValue.clear();
        maxTier = null;
    }

    public static void resetMilestones() {
        counters().clear();
        clearMilestoneCache();
        Store.markDirty();
    }
}
