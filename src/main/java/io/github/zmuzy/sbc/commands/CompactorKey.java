// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemInfo;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.data.DataFiles;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.KeyHeld;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// from skyhanni CompactorGfSKeybind + CompactorCraftApi. while the key is held your inventory shows what can
// reach its next craft step
// green = only needs more of itself, grey = the rest. left click grabs whats missing with /gfs
// every click on your own items is eaten while the key is down so nothing gets picked up by accident. key
// starts unbound
public final class CompactorKey {
    public static final String ID = "gfs_compactor_key";
    private static KeyMapping key;

    // from skyhanni CompactorCraftApi.Upgrade
    record Upgrade(String base, int baseAmount, String result) {}

    private static Map<String, Upgrade> upgrades;
    private static final Map<String, Long> sent = new HashMap<>();

    private CompactorKey() {}

    public static void keys(KeyMapping.Category category) {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.gfs_compactor", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    // biggest craft per base. a tie at the top is unclear so none. pure
    static Map<String, Upgrade> pick(List<Upgrade> candidates) {
        Map<String, List<Upgrade>> byBase = new HashMap<>();
        for (Upgrade u : candidates) byBase.computeIfAbsent(u.base(), k -> new ArrayList<>()).add(u);
        Map<String, Upgrade> out = new HashMap<>();
        for (Map.Entry<String, List<Upgrade>> e : byBase.entrySet()) {
            int highest = 0;
            for (Upgrade u : e.getValue()) highest = Math.max(highest, u.baseAmount());
            Upgrade only = null;
            int tied = 0;
            for (Upgrade u : e.getValue()) {
                if (u.baseAmount() == highest) {
                    only = u;
                    tied++;
                }
            }
            if (tied == 1) out.put(e.getKey(), only);
        }
        return out;
    }

    // crafting recipes of one item kind, more than 1 of it, both sides have a sack
    private static Map<String, Upgrade> upgrades() {
        if (upgrades != null) return upgrades;
        JsonObject sacks = DataFiles.object(DataFiles.Source.NEU, "sacks.json");
        if (sacks == null || !sacks.has("sacks") || ItemRepo.ids().isEmpty()) return Map.of();
        Set<String> inSacks = new HashSet<>();
        for (Map.Entry<String, JsonElement> s : sacks.getAsJsonObject("sacks").entrySet())
            for (JsonElement id : s.getValue().getAsJsonObject().getAsJsonArray("contents")) inSacks.add(id.getAsString());
        List<Upgrade> candidates = new ArrayList<>();
        for (String result : inSacks) {
            for (ItemInfo.Recipe r : ItemInfo.recipes(result)) {
                if (!r.type().isEmpty() && !r.type().equalsIgnoreCase("crafting")) continue;
                String base = null;
                int amount = 0;
                boolean single = true;
                for (ItemInfo.Part p : r.parts()) {
                    if (base != null && !base.equals(p.id())) single = false;
                    base = p.id();
                    amount += p.count();
                }
                if (single && base != null && amount > 1 && inSacks.contains(base)) candidates.add(new Upgrade(base, amount, result));
            }
        }
        if (candidates.isEmpty()) return Map.of();
        return upgrades = pick(candidates);
    }

    private static boolean active() {
        return key != null && Features.on(ID) && KeyHeld.held(key);
    }

    private static int inInventory(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && id.equals(Gain.sbId(st))) n += st.getCount();
        }
        return n;
    }

    private static boolean pending(String id) {
        Long at = sent.get(id);
        return at != null && System.currentTimeMillis() - at < 3000;
    }

    // called per slot as the game draws them
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (!active() || !(slot.container instanceof Inventory)) return;
        String id = slot.getItem().isEmpty() ? "" : Gain.sbId(slot.getItem());
        if (id.isEmpty()) return;
        Upgrade u = upgrades().get(id);
        int x = slot.x, y = slot.y;
        if (u != null && pending(id)) {
            g.fill(x, y, x + 16, y + 16, 0x82FFFF55);
        } else if (u != null && u.baseAmount() > inInventory(id)) {
            int c = 0xC855FF55;
            g.fill(x, y, x + 16, y + 1, c);
            g.fill(x, y + 15, x + 16, y + 16, c);
            g.fill(x, y + 1, x + 1, y + 15, c);
            g.fill(x + 15, y + 1, x + 16, y + 15, c);
        } else {
            g.fill(x, y, x + 16, y + 16, 0x82555555);
        }
    }

    private static boolean onClick(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        if (!active() || slot == null || !(slot.container instanceof Inventory)) return false;
        if (button != 0) return true;
        String id = slot.getItem().isEmpty() ? "" : Gain.sbId(slot.getItem());
        Upgrade u = id.isEmpty() ? null : upgrades().get(id);
        if (u == null || pending(id)) return true;
        int missing = u.baseAmount() - inInventory(id);
        if (missing <= 0) return true;
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) {
            conn.sendCommand("gfs " + GameState.strip(ItemRepo.name(id)) + " " + missing);
            sent.put(id, System.currentTimeMillis());
        }
        return true;
    }

    // what a click would do, under the item lines
    private static void tooltip(ItemStack stack, List<net.minecraft.network.chat.Component> lines) {
        if (!active() || stack.isEmpty()) return;
        String id = Gain.sbId(stack);
        if (id.isEmpty()) return;
        Upgrade u = upgrades().get(id);
        String text;
        if (u == null) text = "§7Cannot be crafted into another item";
        else if (pending(id)) text = "§7Already requested";
        else {
            int missing = u.baseAmount() - inInventory(id);
            String result = GameState.strip(ItemRepo.name(u.result()));
            text = missing > 0 ? "§eClick to grab §ax" + missing + " §emore for " + result : "§7Already enough for " + result;
        }
        lines.add(net.minecraft.network.chat.Component.literal(text));
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "Get what a craft is missing", "While the key in Controls is held: a green frame on items that lack only more of themselves for their next craft, and a left click takes what is missing from your sacks. Your own items cannot be picked up while it is held. It starts unbound.", "SkyHanni: Compactor GfS Keybind")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .tooltip(CompactorKey::tooltip));
        Interactions.onSlotClick(CompactorKey::onClick);
    }
}
