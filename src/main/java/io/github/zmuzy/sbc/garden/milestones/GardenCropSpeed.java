// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.milestones;

import io.github.zmuzy.sbc.core.Store;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// from skyhanni GardenCropSpeed: crop clicks per sec averaged over the last secs (skips the first ones,
// capped at 20), reset after a set pause
// per crop crops/s and the latest blocks/s kept per profile
public final class GardenCropSpeed {
    private GardenCropSpeed() {}

    public static CropType lastBrokenCrop;
    public static long lastBrokenTime;
    public static double averageBlocksPerSecond;
    // since the game started
    public static long sessionBlocks;
    private static List<Integer> blockZoomies = new ArrayList<>();
    private static int blocksBroken, secondsStopped;

    // config.cropMilestones.blocksBrokenResetTime, seconds
    public static int blocksBrokenResetTime = 5;
    public static Supplier<Store.GardenProfile> storage = () -> new Store.GardenProfile();
    public static Supplier<CropType> currentlyFarmedCrop = () -> null;

    private static final List<java.util.function.Consumer<CropType>> CROP_LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    // CropClickEvent for whoever else wants it
    public static void onCrop(java.util.function.Consumer<CropType> listener) {
        CROP_LISTENERS.add(listener);
    }

    public static void onProfileJoin() {
        lastBrokenCrop = null;
    }

    public static void onGardenToolChange() {
        resetSpeed();
    }

    // CropClickEvent
    public static void onCropClick(CropType crop, long now) {
        for (java.util.function.Consumer<CropType> l : CROP_LISTENERS) l.accept(crop);
        lastBrokenCrop = crop;
        lastBrokenTime = now;
        blocksBroken++;
        sessionBlocks++;
    }

    // once a sec (checkSpeed)
    public static void checkSpeed() {
        int broken = blocksBroken;
        blocksBroken = 0;
        if (broken == 0) {
            if (blockZoomies.isEmpty()) return;
            secondsStopped++;
            return;
        }
        if (secondsStopped >= blocksBrokenResetTime) resetSpeed();
        List<Integer> list = new ArrayList<>(blockZoomies);
        while (secondsStopped > 0) {
            list.add(0);
            secondsStopped--;
        }
        list.add(broken);
        if (list.size() == 2) {
            list.remove(0);
            list.add(broken);
        }
        blockZoomies = list;
        if (list.size() > 5) averageBlocksPerSecond = Math.min(20.0, average(list.subList(3, list.size())));
        else if (list.size() > 1) averageBlocksPerSecond = Math.min(20.0, average(list.subList(1, list.size())));
        else averageBlocksPerSecond = 0;
        CropType crop = currentlyFarmedCrop.get();
        if (crop != null && averageBlocksPerSecond > 1) storage.get().latestBlocksPerSecond.put(crop.name(), averageBlocksPerSecond);
    }

    private static double average(List<Integer> l) {
        double s = 0;
        for (int v : l) s += v;
        return l.isEmpty() ? 0 : s / l.size();
    }

    // getRecentBPS: avg of the last 5 full seconds
    public static double getRecentBPS() {
        int size = blockZoomies.size();
        if (size <= 1) return 0;
        int start = size >= 6 ? size - 6 : 0;
        List<Integer> valid = blockZoomies.subList(start, size);
        return average(valid.subList(0, valid.size() - 1));
    }

    public static void resetSpeed() {
        averageBlocksPerSecond = 0;
        blockZoomies = new ArrayList<>();
        secondsStopped = 0;
    }

    public static Integer getSpeed(CropType crop) {
        return storage.get().cropsPerSecond.get(crop.name());
    }

    public static void setSpeed(CropType crop, int speed) {
        storage.get().cropsPerSecond.put(crop.name(), speed);
        Store.markDirty();
    }

    public static boolean isSpeedDataEmpty() {
        int sum = 0;
        for (int v : storage.get().cropsPerSecond.values()) sum += v;
        return sum == 0;
    }
}
