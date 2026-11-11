// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.milestones;

import java.util.HashMap;
import java.util.Map;

// from skyhanni GardenCropBreakTracker's counting: the held tool's counter (cultivating "farmed_cultivating",
// hoe "levelable_exp", or the old "mined_crops") is kept per item uuid
// each increase goes to the crop being farmed (cultivating counts wheat + seeds together so wheat gets 40%)
public final class CropBreakCounter {
    private static final Map<String, Long> counterData = new HashMap<>();
    private static final Map<CropType, Integer> cropMap = new HashMap<>();

    private CropBreakCounter() {}

    // OwnInventoryItemUpdateEvent for the held tool: uuid + counter. crop = last broken crop or the tool's crop
    public static void onToolCounter(String uuid, Long counter, CropType crop) {
        if (uuid == null || uuid.isEmpty() || counter == null) return;
        Long old = counterData.get(uuid);
        counterData.put(uuid, counter);
        if (old == null || crop == null) return;
        long added = counter - old;
        if (added <= 0) return;
        if (crop == CropType.WHEAT) added = (long) (added * 0.4f);
        cropMap.merge(crop, (int) added, Integer::sum);
    }

    // every 5 ticks: flush to the milestone counter (CropCollectionType.BREAKING_CROPS adds to milestones)
    public static void flush() {
        if (cropMap.isEmpty()) return;
        for (var e : new HashMap<>(cropMap).entrySet()) {
            cropMap.remove(e.getKey());
            CropMilestonesApi.addMilestoneCounter(e.getKey(), e.getValue(), true);
        }
    }

    public static void reset() {
        counterData.clear();
        cropMap.clear();
    }
}
