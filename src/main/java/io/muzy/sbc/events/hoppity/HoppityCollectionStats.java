// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni HoppityCollectionStats, the part without neu data
// "Hoppity's Collection" pages give every rabbit's found state + dupe count (saved per profile as
// rabbitCounts), the found/total line, resident and hotspot rabbits of the island, and what to highlight
// (requirement met / not, factory or shop milestone, stray, abi, residents, hotspots)
public final class HoppityCollectionStats {
    static final Patterns.P PAGE = Patterns.p("SH.misc.chocolatefactory.collection.page.current");
    static final Patterns.P DUPLICATES_FOUND = Patterns.p("SH.misc.chocolatefactory.collection.duplicates.found");
    static final Patterns.P RABBIT_NOT_FOUND = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.notfound");
    static final Patterns.P RABBITS_FOUND = Patterns.p("SH.misc.chocolatefactory.collection.rabbits.found.colorless");
    static final Patterns.P REQUIREMENT_MET = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.requirement.met");
    static final Patterns.P REQUIREMENT_NOT_MET = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.requirement.notmet");
    static final Patterns.P REQUIREMENT_AMOUNT = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.requirement.notmet.amount");
    static final Patterns.P LOCATION_REQUIREMENT = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.requirement.location");
    static final Patterns.P RESIDENT = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.requirement.resident");
    static final Patterns.P HOTSPOT = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.hotspot");
    static final Patterns.P MILESTONE_LORE = Patterns.p("SH.misc.chocolatefactory.collection.rabbit.milestone.lore");
    // the 3 patterns skyhanni keeps in its own RepoPattern group (rabbit.requirement.factory/shop/stray)
    static final java.util.regex.Pattern FACTORY_MILESTONE = java.util.regex.Pattern.compile("§6Factory Milestones§7.");
    static final java.util.regex.Pattern SHOP_MILESTONE = java.util.regex.Pattern.compile("§6Shop Milestones§7.");
    static final java.util.regex.Pattern STRAY_RABBIT = java.util.regex.Pattern.compile("(?:§.)+Obtained by finding a §6Golden Stray");

    public enum Highlight {
        ABI(0xFF00AA00), FACTORY(0xFFFFFF55), MET(0xFF55FF55), NOT_MET(0xFFFF5555), SHOP(0xFFFFAA00), STRAYS(0xFF00AAAA), RESIDENTS(0xFF55FFFF), HOTSPOTS(0xFFFF55FF);

        public final int color;

        Highlight(int color) {
            this.color = color;
        }
    }

    // LocationRabbit: needs 15 unique egg spots on an island
    public record LocationRabbit(String locationName, int loreFoundCount, int requiredCount) {
        public boolean hasMetRequirements() {
            return loreFoundCount >= requiredCount;
        }
    }

    public static boolean inInventory;
    public static int foundOnPage, totalOnPage;
    private static final Map<String, Integer> highlightByName = new LinkedHashMap<>();
    private static final Map<String, LocationRabbit> locationRabbits = new LinkedHashMap<>();
    // island -> rabbit -> found (resident rabbits seen in the menu)
    private static final Map<String, Map<String, Boolean>> residentRabbits = new LinkedHashMap<>();
    private static final Map<String, Map<String, Boolean>> hotspotRabbits = new LinkedHashMap<>();

    private HoppityCollectionStats() {}

    // every rabbit the collection showed so far (skyhanni counts neu's list, we count what the menu showed)
    public static int knownRabbitCount() {
        return Math.max(HoppityEnv.profile().rabbitCounts.size(), 0);
    }

    public static boolean hasFoundRabbit(String rabbit) {
        Integer n = HoppityEnv.profile().rabbitCounts.get(rabbit);
        return n != null && n > 0;
    }

    // getTypeCountSnapshot: unique, dupe and stray so far. strays arent told apart here, the collection doesnt say
    static int[] typeCountSnapshot() {
        int uniques = 0, dupes = 0;
        for (int n : HoppityEnv.profile().rabbitCounts.values()) {
            uniques++;
            dupes += n - 1;
        }
        return new int[]{uniques, dupes, 0};
    }

    public static int getRabbitCount(String rabbit) {
        Integer n = HoppityEnv.profile().rabbitCounts.get(GameState.strip(rabbit));
        return n == null ? 0 : n;
    }

    public static Integer highlightFor(String legacyName) {
        return highlightByName.get(legacyName);
    }

    public static Map<String, LocationRabbit> locationRabbits() {
        return locationRabbits;
    }

    // InventoryFullyOpenedEvent: a "Hoppity's Collection" page (plain title, items as legacy name + lore)
    public static void onInventoryOpened(String titlePlain, Map<Integer, ChocolateFactory.Item> items) {
        Matcher page = PAGE.matcher(titlePlain);
        if (!page.matches()) {
            highlightByName.clear();
            inInventory = false;
            return;
        }
        inInventory = true;
        for (Map.Entry<Integer, ChocolateFactory.Item> e : items.entrySet()) {
            ChocolateFactory.Item it = e.getValue();
            if (it.lore().isEmpty()) continue;
            // found/total sits in a lore line of the page's info item
            for (String l : it.lore()) {
                Matcher m = RABBITS_FOUND.matcher(GameState.strip(l));
                if (m.matches()) {
                    foundOnPage = Integer.parseInt(m.group("current"));
                    totalOnPage = Integer.parseInt(m.group("total"));
                }
            }
            logRabbit(it);
            setResidentData(it);
            setHotspotData(it);
            filterToHighlight(it);
        }
    }

    // HoppityCollectionStats.logRabbits: found rabbits with dupe count (count = dupes + 1)
    private static void logRabbit(ChocolateFactory.Item it) {
        String name = GameState.strip(it.name()).trim();
        if (name.isEmpty()) return;
        boolean found = true;
        int duplicates = -1;
        for (String l : it.lore()) {
            if (RABBIT_NOT_FOUND.matcher(l).matches()) found = false;
            Matcher m = DUPLICATES_FOUND.matcher(l);
            if (m.matches()) duplicates = (int) HoppityEnv.parseLong(m.group("duplicates"));
        }
        if (duplicates < 0) return; // not a rabbit item
        HoppityEnv.profile().rabbitCounts.put(name, found ? duplicates + 1 : 0);
        for (String l : it.lore()) {
            Matcher m = LOCATION_REQUIREMENT.matcher(GameState.strip(l));
            if (!m.matches()) continue;
            int acquired = 0, required = 15;
            for (String l2 : it.lore()) {
                Matcher a = REQUIREMENT_AMOUNT.matcher(l2);
                if (a.matches()) {
                    acquired = (int) HoppityEnv.parseLong(a.group("acquired"));
                    required = (int) HoppityEnv.parseLong(a.group("required"));
                }
            }
            locationRabbits.put(name, new LocationRabbit(m.group("location"), acquired, required));
        }
    }

    private static void setResidentData(ChocolateFactory.Item it) {
        for (String l : it.lore()) {
            Matcher m = RESIDENT.matcher(l);
            if (!m.matches()) continue;
            String island = m.group("island");
            boolean found = true;
            for (String l2 : it.lore()) if (RABBIT_NOT_FOUND.matcher(l2).matches()) found = false;
            residentRabbits.computeIfAbsent(island, k -> new LinkedHashMap<>()).put(GameState.strip(it.name()).trim(), found);
            return;
        }
    }

    private static void setHotspotData(ChocolateFactory.Item it) {
        for (String l : it.lore()) {
            Matcher m = HOTSPOT.matcher(l);
            if (!m.matches()) continue;
            String island = m.group("location");
            boolean found = true;
            for (String l2 : it.lore()) if (RABBIT_NOT_FOUND.matcher(l2).matches()) found = false;
            hotspotRabbits.computeIfAbsent(island, k -> new LinkedHashMap<>()).put(GameState.strip(it.name()).trim(), found);
            return;
        }
    }

    // hypixel's island names in lore -> our island ids (IslandType.getByNameOrNull)
    static String islandId(String displayName) {
        return switch (displayName) {
            case "Hub" -> "HUB";
            case "The Park" -> "THE_PARK";
            case "Gold Mine" -> "GOLD_MINES";
            case "Deep Caverns" -> "DEEP_CAVERNS";
            case "Dwarven Mines" -> "DWARVEN_MINES";
            case "Crystal Hollows" -> "CRYSTAL_HOLLOWS";
            case "The Farming Islands" -> "THE_FARMING_ISLANDS";
            case "Spider's Den" -> "SPIDER_DEN";
            case "The End" -> "THE_END";
            case "Crimson Isle" -> "CRIMSON_ISLE";
            case "Dungeon Hub" -> "DUNGEON_HUB";
            case "Backwater Bayou" -> "BACKWATER_BAYOU";
            case "Galatea" -> "GALATEA";
            case "Lotus Atoll" -> "LOTUS_ATOLL";
            case "Torrhus Canyon" -> "TORRHUS_CANYON";
            default -> displayName.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        };
    }

    static boolean wants(Highlight h) {
        return Hoppity.b(Hoppity.CF, "highlight." + h.name().toLowerCase(java.util.Locale.ROOT), h == Highlight.MET || h == Highlight.RESIDENTS || h == Highlight.HOTSPOTS);
    }

    private static void filterToHighlight(ChocolateFactory.Item it) {
        List<String> lore = it.lore();
        if (lore.isEmpty()) return;
        boolean notFound = false;
        for (String l : lore) if (RABBIT_NOT_FOUND.matcher(l).matches()) notFound = true;
        if (!notFound && !Hoppity.b(Hoppity.CF, "highlightFoundRabbits", false)) return;
        String name = it.name();
        if (highlightByName.containsKey(name)) return;
        if (name.equals("§aAbi") && wants(Highlight.ABI)) {
            highlightByName.put(name, Highlight.ABI.color);
            return;
        }
        if (any(lore, FACTORY_MILESTONE) && wants(Highlight.FACTORY)) highlightByName.put(name, Highlight.FACTORY.color);
        else if (any(lore, REQUIREMENT_MET.pattern()) && wants(Highlight.MET)) highlightByName.put(name, Highlight.MET.color);
        else if (any(lore, REQUIREMENT_NOT_MET.pattern()) && wants(Highlight.NOT_MET)) highlightByName.put(name, Highlight.NOT_MET.color);
        else if (any(lore, SHOP_MILESTONE) && wants(Highlight.SHOP)) highlightByName.put(name, Highlight.SHOP.color);
        else if (any(lore, STRAY_RABBIT) && wants(Highlight.STRAYS)) highlightByName.put(name, Highlight.STRAYS.color);
        for (String l : lore) {
            Matcher m = RESIDENT.matcher(l);
            if (m.matches() && islandId(m.group("island")).equals(HoppityEnv.world.island()) && wants(Highlight.RESIDENTS)) highlightByName.put(name, Highlight.RESIDENTS.color);
            m = HOTSPOT.matcher(l);
            if (m.matches() && islandId(m.group("location")).equals(HoppityEnv.world.island()) && wants(Highlight.HOTSPOTS)) highlightByName.put(name, Highlight.HOTSPOTS.color);
        }
    }

    private static boolean any(List<String> lore, java.util.regex.Pattern p) {
        for (String l : lore) if (p.matcher(l).matches()) return true;
        return false;
    }

    public static void onInventoryClose() {
        inInventory = false;
    }

    // summary lines for the collection hud: found/total, resident + hotspot rabbits here
    public static List<String> summaryLines() {
        List<String> out = new ArrayList<>();
        if (!inInventory) return out;
        if (totalOnPage > 0) out.add("§eRabbits found: §a" + foundOnPage + "§7/§a" + totalOnPage);
        int dupes = 0;
        for (int n : HoppityEnv.profile().rabbitCounts.values()) if (n > 1) dupes += n - 1;
        out.add("§eDuplicates (seen pages): §a" + dupes);
        String island = HoppityEnv.world.island();
        for (Map.Entry<String, Map<String, Boolean>> e : residentRabbits.entrySet()) {
            if (!islandId(e.getKey()).equals(island)) continue;
            int found = 0;
            for (boolean b : e.getValue().values()) if (b) found++;
            out.add("§bResidents here: §a" + found + "§7/§a" + e.getValue().size());
        }
        for (Map.Entry<String, Map<String, Boolean>> e : hotspotRabbits.entrySet()) {
            if (!islandId(e.getKey()).equals(island)) continue;
            int found = 0;
            for (boolean b : e.getValue().values()) if (b) found++;
            out.add("§dHotspot here: §a" + found + "§7/§a" + e.getValue().size());
        }
        return out;
    }

    // {found, total} per island entry here, residents
    static List<int[]> residentsHere() {
        return here(residentRabbits);
    }

    // {found, total} per island entry here, hotspots
    static List<int[]> hotspotsHere() {
        return here(hotspotRabbits);
    }

    private static List<int[]> here(Map<String, Map<String, Boolean>> byIsland) {
        List<int[]> out = new ArrayList<>();
        String island = HoppityEnv.world.island();
        for (Map.Entry<String, Map<String, Boolean>> e : byIsland.entrySet()) {
            if (!islandId(e.getKey()).equals(island)) continue;
            int found = 0;
            for (boolean b : e.getValue().values()) if (b) found++;
            out.add(new int[]{found, e.getValue().size()});
        }
        return out;
    }

    // panel next to the collection: {name, found, total} per island, what a location rabbit still needs
    static List<Object[]> perIsland(boolean hotspots) {
        List<Object[]> out = new ArrayList<>();
        (hotspots ? hotspotRabbits : residentRabbits).forEach((island, rabbits) -> {
            int found = 0;
            for (boolean b : rabbits.values()) if (b) found++;
            out.add(new Object[]{island, found, rabbits.size()});
        });
        return out;
    }

    public static void reset() {
        inInventory = false;
        foundOnPage = totalOnPage = 0;
        highlightByName.clear();
        locationRabbits.clear();
        residentRabbits.clear();
        hotspotRabbits.clear();
    }
}
