// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden;

import io.muzy.sbc.garden.milestones.GardenCropSpeed;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// what visitor, composter, contest and plot stuff keeps per profile, skyhanni ProfileSpecificStorage.GardenStorage
// sits in one field of Store.GardenProfile
public final class GardenData {
    // VisitorDrops
    public int acceptedVisitors, deniedVisitors, copper, farmingExp, gardenExp, bits, mithrilPowder, gemstonePowder;
    public long coinsSpent;
    // greenhouse mutations you analyzed, lower case names
    public java.util.Set<String> analyzedMutations = new java.util.HashSet<>();
    public Map<String, Long> acceptedRarities = new HashMap<>();
    public Map<String, Integer> rewardsCount = new HashMap<>();

    // visitors logbook as last read, name -> {visited, accepted} + which pages
    public Map<String, int[]> logbook = new HashMap<>();
    public Set<Integer> logbookPages = new LinkedHashSet<>();
    public int logbookPageCount;

    public Set<String> ignoredVisitors = new LinkedHashSet<>();
    public Set<String> charmedVisitors = new LinkedHashSet<>();
    // NpcVisitorFix: where the real jacob and anita of the barn stand, x y z
    public Map<String, double[]> npcVisitorLocations = new HashMap<>();
    // GardenVisitorTimer
    public long visitorInterval = 15 * 60000L, nextSixthVisitorArrival;

    // composter
    public Map<String, Integer> composterUpgrades = new HashMap<>();
    public String composterOrganicItem = "", composterFuelItem = "";
    public long composterEmptyTime, informedAboutLowMatter, informedAboutLowFuel, lastComposterEmptyWarningTime;
    // warn once when the composter runs out, until it runs again
    public boolean composterEmptyWarned;

    // contests
    public Map<String, Double> personalBestFF = new HashMap<>();
    public Map<String, Double> latestTrueFF = new HashMap<>();

    public int anitaUpgrade;

    // GardenPlotApi.PlotData by plot number
    public Map<Integer, Plot> plots = new HashMap<>();

    public static final class Plot {
        public boolean locked = true, pasting, uncleared, greenhouse;
        public String name = "", sprayType = "";
        public long sprayExpiry;
    }

    public static GardenData get() {
        var profile = GardenCropSpeed.storage.get();
        if (profile.data == null) profile.data = new GardenData();
        return profile.data;
    }

    public void resetDrops() {
        acceptedVisitors = deniedVisitors = copper = farmingExp = gardenExp = bits = mithrilPowder = gemstonePowder = 0;
        coinsSpent = 0;
        acceptedRarities.clear();
        rewardsCount.clear();
    }
}
