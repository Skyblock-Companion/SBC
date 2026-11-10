// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.farming;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.garden.milestones.GardenMilestones;
import io.github.zmuzy.sbc.mc.KeyHeld;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// from skyhanni GardenCustomKeybinds + MixinKeyMapping: holding a farming tool on your garden, the game's
// move/attack/use/jump/sneak keys answer to other keys
// presses nothing, only your own keys do something else. the other keys are the "Farming:" ones in Controls,
// an unbound one leaves the game's own, so no need for "Disable All" / "Set Default" buttons
public final class FarmingKeys {
    public static final String ID = "garden_farming_keys";
    // game key -> ours
    private static final Map<KeyMapping, KeyMapping> MAP = new LinkedHashMap<>();
    private static final Map<KeyMapping, Boolean> WAS_HELD = new java.util.HashMap<>();
    // pressedToggleKeys: game toggle keys whose key of ours already toggled and is still down
    private static final Set<KeyMapping> PRESSED_TOGGLE = new HashSet<>();
    private static KeyMapping attack, use, left, right, forward, back, jump, sneak;
    private static long lastSign;
    private static boolean active;
    private static String bindings;

    private FarmingKeys() {}

    private static boolean holding(Minecraft mc) {
        if (mc.player == null) return false;
        if (GardenMilestones.hasToolInHand()) return true;
        if (GardenPack.on(ID, "mousemat") && GardenPack.mousematInHand()) return true;
        if (GardenPack.on(ID, "vacuum") && GardenPack.vacuumInHand()) return true;
        if (GardenPack.on(ID, "rod") && mc.player.getMainHandItem().is(Items.FISHING_ROD)) return true;
        // GardenApi.hasActiveSunsGrasp: the gloves and an empty hand. what you wear only the equipment menu tells
        if (!GardenPack.on(ID, "suns_grasp") || !mc.player.getMainHandItem().isEmpty()) return false;
        for (var st : io.github.zmuzy.sbc.ui.panels.EquipmentCache.current()) if ("SUNS_GRASP".equals(Gain.sbId(st))) return true;
        return false;
    }

    private static void tick(Minecraft mc) {
        if (mc.gui.screen() instanceof AbstractSignEditScreen) lastSign = System.currentTimeMillis();
        boolean feature = Features.on(ID);
        if (feature) bindingsChanged();
        boolean now = feature && GardenPack.garden() && mc.gui.screen() == null && holding(mc) && System.currentTimeMillis() - lastSign > 300
                && !(GardenPack.on(ID, "exclude_barn") && io.github.zmuzy.sbc.garden.plot.GardenPlotInfo.onUnfarmablePlot(mc.player.getX(), mc.player.getZ()));
        // updateActiveState: nothing gets released here. our key is read as is, and the game's keys answer for
        // themselves again the moment this stops
        if (now != active) {
            active = now;
            PRESSED_TOGGLE.clear();
            if (now) primeToggles();
        }
        if (!feature) {
            WAS_HELD.clear();
            return;
        }
        for (KeyMapping ours : MAP.values()) {
            if (ours.isUnbound()) continue;
            WAS_HELD.put(ours, syncedWasHeld(now, KeyHeld.held(ours), WAS_HELD.getOrDefault(ours, false)));
        }
    }

    // what our key counts as a moment ago
    // consumeClick only runs while this is active and the game asks, which it doesnt always. so a released key
    // counts as released whenever that happens, and a key already down when this starts isnt a new press
    static boolean syncedWasHeld(boolean active, boolean held, boolean was) {
        return active ? was && held : held;
    }

    // update: game only lets go of what it holds when a binding changes, a key thats down now might answer to another one
    private static void bindingsChanged() {
        if (MAP.isEmpty()) return;
        StringBuilder s = new StringBuilder();
        for (KeyMapping ours : MAP.values()) s.append(ours.saveString()).append(';');
        String now = s.toString();
        if (now.equals(bindings)) return;
        boolean first = bindings == null;
        bindings = now;
        if (first) return;
        PRESSED_TOGGLE.clear();
        active = false;
        KeyMapping.releaseAll();
    }

    private static boolean toggle(KeyMapping key) {
        return key instanceof ToggleKeyMapping t && t.needsToggle.getAsBoolean();
    }

    // isRemappedFrom: our key is different from the game's own, which already toggles by itself
    private static boolean remapped(KeyMapping key, KeyMapping ours) {
        return !KeyMappingHelper.getBoundKeyOf(key).equals(KeyMappingHelper.getBoundKeyOf(ours));
    }

    // primePressedToggleKeys: our key already down when this starts doesnt toggle until it goes up and down again
    private static void primeToggles() {
        for (var e : MAP.entrySet()) {
            KeyMapping key = e.getKey(), ours = e.getValue();
            if (!ours.isUnbound() && toggle(key) && remapped(key, ours) && KeyHeld.held(ours)) PRESSED_TOGGLE.add(key);
        }
    }

    // updateToggleState, down = the game key's own state
    // setDown asks isDown again and by then the key is marked, so it answers with the real state. careful with
    // the order here
    private static boolean updateToggle(KeyMapping key, KeyMapping ours, boolean down) {
        if (!KeyHeld.held(ours)) {
            PRESSED_TOGGLE.remove(key);
            return down;
        }
        if (!PRESSED_TOGGLE.add(key)) return down;
        key.setDown(true);
        return !down;
    }

    // consumeToggleClick
    private static boolean consumeToggle(KeyMapping key, KeyMapping ours) {
        if (!KeyHeld.held(ours)) {
            PRESSED_TOGGLE.remove(key);
            return false;
        }
        if (!PRESSED_TOGGLE.add(key)) return false;
        key.setDown(true);
        return true;
    }

    // isKeyDown, null = leave the key alone. a key one of ours took doesnt also do its own thing. down = the
    // key's own state
    public static Boolean isDown(KeyMapping key, boolean down) {
        if (!active || MAP.isEmpty() || MAP.containsValue(key)) return null;
        KeyMapping ours = MAP.get(key);
        if (ours == null) return taken(key) ? false : null;
        if (ours.isUnbound()) return null;
        if (!toggle(key)) return KeyHeld.held(ours);
        return remapped(key, ours) ? updateToggle(key, ours, down) : down;
    }

    // isKeyPressed: our press counts once
    public static Boolean consumeClick(KeyMapping key) {
        if (!active || MAP.isEmpty() || MAP.containsValue(key)) return null;
        KeyMapping ours = MAP.get(key);
        if (ours == null) return taken(key) ? false : null;
        if (ours.isUnbound()) return null;
        if (toggle(key) && remapped(key, ours)) return consumeToggle(key, ours);
        boolean now = KeyHeld.held(ours), was = WAS_HELD.getOrDefault(ours, false);
        WAS_HELD.put(ours, now);
        return now && !was;
    }

    private static boolean taken(KeyMapping key) {
        InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
        for (KeyMapping o : MAP.values()) if (!o.isUnbound() && KeyMappingHelper.getBoundKeyOf(o).equals(bound)) return true;
        return false;
    }

    public static void keys(KeyMapping.Category category) {
        attack = key("attack", category);
        use = key("use", category);
        left = key("left", category);
        right = key("right", category);
        forward = key("forward", category);
        back = key("back", category);
        jump = key("jump", category);
        sneak = key("sneak", category);
    }

    private static KeyMapping key(String name, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.farming_" + name, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    private static void map(Minecraft mc) {
        if (!MAP.isEmpty() || attack == null || mc.options == null) return;
        MAP.put(mc.options.keyAttack, attack);
        MAP.put(mc.options.keyUse, use);
        MAP.put(mc.options.keyLeft, left);
        MAP.put(mc.options.keyRight, right);
        MAP.put(mc.options.keyUp, forward);
        MAP.put(mc.options.keyDown, back);
        MAP.put(mc.options.keyJump, jump);
        MAP.put(mc.options.keyShift, sneak);
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Farming keys", "While you hold a farming tool on your Garden, the game's movement and click keys answer to the Farming keys you set in the game's Controls. A Farming key left unbound changes nothing.", "SkyHanni: Custom Keybinds")
                .when(GardenPack::garden).defaultOff()
                .option(Option.bool("garden_farming_keys.exclude_barn", "Not on plots you cannot farm", "Not on the barn plot or in a greenhouse. A greenhouse is known once you have opened Configure Plots.", false))
                .option(Option.bool("garden_farming_keys.mousemat", "Also with a Squeaky Mousemat", "", false))
                .option(Option.bool("garden_farming_keys.vacuum", "Also with a vacuum", "", false))
                .option(Option.bool("garden_farming_keys.rod", "Also with a fishing rod", "", false))
                .option(Option.bool("garden_farming_keys.suns_grasp", "Also with an empty hand and Sun's Grasp", "", true)));
        Safe.endTick("FarmingKeys.endTick", mc -> {
            map(mc);
            tick(mc);
        });
    }
}
