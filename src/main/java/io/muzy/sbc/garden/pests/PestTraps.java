// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.pests;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

// from skyhanni PestTrapApi + PestTrapFeatures: warns when a pest trap is full, has no bait or wasnt placed,
// from the tab's Pest Traps widget
// the pests card already shows the widget, this tells you when you're not looking
// PestTrapFeatures.onKeybind: its release key clicks hypixel's Release All button, only when you press it.
// unbound and off by default
public final class PestTraps {
    public static final String ID = "garden_pest_traps";
    private static long nextWarning, nextRead, enteredAt;
    private static String lastIsland = "";
    private static final java.util.Set<String> TRAP_MENUS = java.util.Set.of("Pest Trap", "Mouse Trap", "Vermin Trap");
    private static final int RELEASE_SLOT = 16;
    private static KeyMapping releaseKey;

    private PestTraps() {}

    // getTrapIndexSet: "#1, #2" -> numbers, "None" = none
    static List<Integer> numbers(String traps) {
        List<Integer> out = new ArrayList<>();
        if (traps == null) return out;
        for (String p : GameState.strip(traps).replace("#", "").split(", ")) {
            try {
                out.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    // active warning lines for the tab as it is
    static List<String> warnings(List<String> tab, boolean full, boolean bait, boolean unplaced) {
        List<String> out = new ArrayList<>();
        int placed = -1, max = 3;
        List<Integer> fullTraps = List.of(), noBait = List.of();
        for (String l : tab) {
            String t = GameState.strip(l);
            Matcher m = Patterns.get("SH.tab.widgetcomponent.enum.pest.traps").matcher(t);
            if (m.matches()) {
                placed = Integer.parseInt(m.group("count"));
                max = Integer.parseInt(m.group("max"));
            }
            m = Patterns.get("SH.tab.widgetcomponent.enum.full.traps").matcher(t);
            if (m.matches()) fullTraps = numbers(m.group("traps"));
            m = Patterns.get("SH.tab.widgetcomponent.enum.no.bait").matcher(t);
            if (m.matches()) noBait = numbers(m.group("traps"));
        }
        if (full && !fullTraps.isEmpty()) out.add("§cFull " + (fullTraps.size() == 1 ? "Trap" : "Traps") + ": §f" + list(fullTraps));
        if (bait && !noBait.isEmpty()) out.add("§cNo Bait " + (noBait.size() == 1 ? "Trap" : "Traps") + ": §f" + list(noBait));
        if (unplaced && placed >= 0 && placed < max) {
            int left = max - placed;
            out.add("§aUnplaced " + (left == 1 ? "Trap" : "Traps") + ": §c" + placed + "§4/§c" + max + " §a" + (placed == 1 ? "Trap" : "Traps") + " Placed");
        }
        return out;
    }

    private static String list(List<Integer> traps) {
        StringBuilder s = new StringBuilder();
        for (int t : traps) s.append(s.isEmpty() ? "" : "§7, ").append("§a#").append(t);
        return s.toString();
    }

    static boolean comingBack() {
        return lastIsland.isEmpty();
    }

    static void beenHereForTest() {
        lastIsland = "GARDEN";
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (!GameState.island.equals(lastIsland)) {
            lastIsland = GameState.island;
            enteredAt = now;
        }
        // onWidgetUpdate waits 5s after you arrive, what the widget said before is about before you came
        if (now < nextRead || now - enteredAt < 5000) return;
        nextRead = now + 1000;
        if (now < nextWarning) return;
        List<String> active = warnings(GameState.tab, GardenPack.on(ID, "full"), GardenPack.on(ID, "no_bait"), GardenPack.on(ID, "unplaced"));
        if (active.isEmpty()) return;
        // virtualReminderInterval: never more often than every 10s
        nextWarning = now + Math.max(10, GardenPack.number(ID, "interval", 30)) * 1000L;
        if (GardenPack.on(ID, "sound")) GardenPack.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f);
        String how = GardenPack.choice(ID, "how", "Title");
        if (how.equals("Title") || how.equals("Both")) GardenPack.title(active.get(0), 3);
        if (how.equals("Chat") || how.equals("Both")) for (String w : active) GardenPack.chat(w);
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Pest trap warnings", "Tells you again and again that a trap is full or out of bait, until you dealt with it. Needs the Pest Traps widget in your tab list.", "SkyHanni: Pest Trap")
                .when(GardenPack::garden).tick(PestTraps::tick).left(() -> {
                    // next garden visit counts as coming again
                    if (!GardenPack.garden()) lastIsland = "";
                })
                .option(Option.bool("garden_pest_traps.full", "A trap is full", "", true))
                .option(Option.bool("garden_pest_traps.no_bait", "A trap has no bait", "", true))
                .option(Option.bool("garden_pest_traps.unplaced", "A trap is not placed", "", false))
                .option(Option.choice("garden_pest_traps.how", "Warn with", "", "Title", "Title", "Chat", "Both"))
                .option(Option.integer("garden_pest_traps.interval", "Warn each (s)", "At least 10.", 30, 10, 600))
                .option(Option.bool("garden_pest_traps.sound", "With a sound", "", true))
                .option(Option.bool("garden_pest_traps.release_key", "Release key", "The Pest trap: release all key in the game's Controls clicks Release All in an open trap, only when you press it.", false)));
    }

    public static void keys(KeyMapping.Category category) {
        releaseKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.pest_trap_release", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    // PestTrapApi.inventoryNames
    static boolean isTrapMenu(String title) {
        return TRAP_MENUS.contains(title);
    }

    // PestTrapFeatures.onKeybind. false = the key did its job
    public static boolean key(Screen s, KeyEvent event) {
        if (releaseKey == null || !releaseKey.matches(event) || !(s instanceof AbstractContainerScreen<?> cs) || !Features.on(ID) || !GardenPack.on(ID, "release_key")) return true;
        if (!isTrapMenu(GameState.strip(s.getTitle().getString()).trim())) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, RELEASE_SLOT, 0, ContainerInput.PICKUP, mc.player);
        return false;
    }
}
