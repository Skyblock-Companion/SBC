// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// port of skyhanni rift/everywhere/EnigmaSoulWaypoints.kt: in "Rift Guide ➜ Enigma Souls" clicking a soul
// toggles its waypoint (click gets swallowed), slot 31 toggles all
public final class EnigmaSoulWaypoints {
    static final Patterns.P TITLE = Patterns.p("SH.rift.everywhere.enigma-souls.title");
    static final Patterns.P NOT_COMPLETED = Patterns.p("SH.rift.everywhere.enigma-souls.not-completed");
    static final Patterns.P GUIDE_AREA = Patterns.p("SH.rift.everywhere.enigma-souls.guide-area");
    static final Patterns.P FOUND = Patterns.p("SH.rift.everywhere.enigma-souls.found");
    static final Patterns.P INVENTORY = Patterns.p("SH.rift.everywhere.enigma-souls.inventory");

    // one menu item: clean name + clean lore
    public record Item(String cleanName, List<String> cleanLore) {}

    static boolean inInventory;
    static final Map<String, List<String>> trackedSouls = new LinkedHashMap<>();
    static final List<String> inventoryUnfound = new ArrayList<>();
    static boolean adding = true;
    static String selectedArea;

    public static int color = 0xFFAA00AA;
    // from skyblocker EnigmaSouls: mark every soul, not just tracked ones
    public static boolean markAll, markFound;

    private EnigmaSoulWaypoints() {}

    public static boolean isEnabled() {
        return RiftApi.inRift();
    }

    public static Map<String, List<String>> tracked() {
        return trackedSouls;
    }

    // from skyhanni onInventoryFullyOpened: slot -> item (clean name + lore)
    public static void onInventoryFullyOpened(String inventoryName, Map<Integer, Item> items) {
        inInventory = false;
        if (!INVENTORY.matcher(inventoryName).matches()) return;
        inInventory = true;
        inventoryUnfound.clear();
        for (Item st : items.values()) {
            if (st.cleanLore().isEmpty()) continue;
            String last = st.cleanLore().get(st.cleanLore().size() - 1);
            if (!NOT_COMPLETED.matcher(last).matches()) continue;
            Matcher m = TITLE.matcher(st.cleanName());
            if (m.matches()) inventoryUnfound.add(m.group("name"));
        }
        // from skyhanni getSelectedArea: slot 39s first lore line "To Rift Guide ➜ <area>"
        Item area = items.get(39);
        selectedArea = null;
        if (area != null && !area.cleanLore().isEmpty()) {
            Matcher m = GUIDE_AREA.matcher(area.cleanLore().get(0));
            if (m.matches()) selectedArea = m.group("area");
        }
    }

    public static void onInventoryClose() {
        inInventory = false;
        inventoryUnfound.clear();
        adding = true;
    }

    // from skyhanni onSlotClick. true = swallow the click (skyhanni makes it a pick block)
    public static boolean onSlotClick(int slotId, String cleanItemName) {
        if (!inInventory || !isEnabled()) return false;
        String area = selectedArea;
        if (area == null) return false;
        boolean handled = false;
        if (slotId == 31 && !inventoryUnfound.isEmpty()) {
            handled = true;
            if (inventoryUnfound.contains("Buttons")) RiftApi.trackingButtons = !RiftApi.trackingButtons;
            if (adding) {
                trackedSouls.computeIfAbsent(area, k -> new ArrayList<>()).addAll(inventoryUnfound);
                adding = false;
            } else {
                List<String> t = trackedSouls.get(area);
                if (t != null) t.removeAll(inventoryUnfound);
                adding = true;
            }
        }
        if (cleanItemName == null) return handled;
        Matcher m = TITLE.matcher(cleanItemName);
        if (!m.matches()) return handled;
        String name = m.group("name");
        Map<String, Vec3> souls = RiftData.soulLocations.get(area);
        if (souls == null || !souls.containsKey(name)) return true;
        if (name.equals("Buttons")) RiftApi.trackingButtons = !RiftApi.trackingButtons;
        List<String> t = trackedSouls.computeIfAbsent(area, k -> new ArrayList<>());
        if (!t.contains(name)) {
            RiftEnv.world.chat("§5Tracking the " + name + " Enigma Soul!");
            t.add(name);
            if (!(name.equals("Buttons") && WoodenButtonsHelper.showButtons())) RiftEnv.world.pathFind(name + " Enigma Soul", souls.get(name));
        } else {
            t.remove(name);
            RiftEnv.world.chat("§5No longer tracking the " + name + " Enigma Soul!");
            RiftEnv.world.stopPath();
        }
        return true;
    }

    // from skyhanni onBackgroundDrawn: highlight tracked souls + slot 31 in remove mode
    public static boolean highlight(int slotId, String cleanItemName) {
        if (!isEnabled() || !inInventory || selectedArea == null) return false;
        if (slotId == 31 && !adding) return true;
        List<String> t = trackedSouls.get(selectedArea);
        if (t == null || cleanItemName == null) return false;
        Matcher m = TITLE.matcher(cleanItemName);
        return m.matches() && t.contains(m.group("name"));
    }

    public static void render(WorldDraw w) {
        if (!isEnabled()) return;
        if (markAll) renderAll(w);
        for (var area : trackedSouls.entrySet()) {
            Map<String, Vec3> souls = RiftData.soulLocations.get(area.getKey());
            if (souls == null) continue;
            for (String name : area.getValue()) {
                Vec3 position = souls.get(name);
                if (position == null) continue;
                String label = name.endsWith(" Soul") ? name.substring(0, name.length() - 5) : name;
                // like skyhannis drawWaypointFilled(seeThroughBlocks, beacon) + drawDynamicText(position.up(), ..., 1.5)
                w.waypoint(position, color, "§5" + label + " Soul", true, true);
            }
        }
    }

    // from skyblocker EnigmaSouls.extractRendering: green = still to find, red = found
    private static void renderAll(WorldDraw w) {
        List<String> found = RiftEnv.profile().foundEnigmaSouls;
        for (var area : RiftData.soulLocations.values()) {
            for (var soul : area.entrySet()) {
                boolean done = found.contains(soul.getKey());
                if (done && !markFound) continue;
                w.waypoint(soul.getValue(), done ? 0xFFFF5555 : 0xFF55FF55, (done ? "§c" : "§a") + soul.getKey(), false, true);
            }
        }
    }

    // from skyhanni markClosestSoul: within 4 blocks
    public static boolean markClosest(boolean asFound) {
        Vec3 player = RiftEnv.world.playerPos();
        String closest = null;
        double best = 4;
        for (var area : RiftData.soulLocations.values())
            for (var soul : area.entrySet()) {
                double d = soul.getValue().add(0.5, 0.5, 0.5).distanceTo(player);
                if (d <= best) {
                    best = d;
                    closest = soul.getKey();
                }
            }
        if (closest == null) return false;
        List<String> found = RiftEnv.profile().foundEnigmaSouls;
        if (asFound && !found.contains(closest)) found.add(closest);
        if (!asFound) found.remove(closest);
        return true;
    }

    public static void markEvery(boolean asFound) {
        List<String> found = RiftEnv.profile().foundEnigmaSouls;
        found.clear();
        if (!asFound) return;
        for (var area : RiftData.soulLocations.values()) found.addAll(area.keySet());
    }

    // from skyhanni onChat: a found soul hides the closest tracked one (within 8 blocks)
    public static boolean onChat(String clean) {
        if (!isEnabled()) return false;
        if (!FOUND.matcher(clean.trim()).matches()) return false;
        markClosest(true);
        hideClosestSoul();
        return true;
    }

    private static void hideClosestSoul() {
        String closestSoul = "", closestArea = "";
        double closestDistance = 8.0;
        Vec3 player = RiftEnv.world.playerPos();
        for (var area : RiftData.soulLocations.entrySet())
            for (var soul : area.getValue().entrySet()) {
                double d = soul.getValue().distanceTo(player);
                if (d < closestDistance) {
                    closestSoul = soul.getKey();
                    closestArea = area.getKey();
                    closestDistance = d;
                }
            }
        List<String> t = trackedSouls.get(closestArea);
        if (t != null && t.contains(closestSoul)) {
            t.remove(closestSoul);
            RiftEnv.world.chat("§5Found the " + closestSoul + " Enigma Soul!");
            if (closestSoul.equals("Buttons")) RiftApi.trackingButtons = false;
        }
    }

    public static void reset() {
        trackedSouls.clear();
        inventoryUnfound.clear();
        inInventory = false;
        adding = true;
        selectedArea = null;
    }
}
