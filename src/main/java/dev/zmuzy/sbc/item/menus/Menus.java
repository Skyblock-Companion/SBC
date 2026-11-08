// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.menus;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

// helpers on hypixels own menus. everything in this package registers here so the shared files only need one line
public final class Menus {
    private static final BooleanSupplier SKYBLOCK = () -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV;

    private Menus() {}

    // skyhanni AnvilCombineHelper, skyblocker AnvilHelper
    static void anvil(MenuView v) {
        MenuView.Item left = v.item(29), right = v.item(33);
        if (MenuMarks.on("anvil_helper", "anvil_helper.combine")) {
            boolean hasLeft = !left.empty(), hasRight = !right.empty();
            if (hasLeft != hasRight) {
                String id = hasLeft ? left.id : right.id;
                if (!id.isEmpty()) for (int slot = v.top; slot < v.itemsLength(); slot++) if (id.equals(v.items[slot].id)) v.back(slot, MenuView.GREEN);
            }
        }
        if (MenuMarks.on("anvil_helper", "anvil_helper.warn_levels") && left.model.equals("enchanted_book") && right.model.equals("enchanted_book") && unequal(left, right)) v.back(13, MenuView.RED);
    }

    private static boolean unequal(MenuView.Item a, MenuView.Item b) {
        if (!a.tag.contains("enchantments") || !b.tag.contains("enchantments")) return false;
        var first = a.tag.getCompoundOrEmpty("enchantments");
        var second = b.tag.getCompoundOrEmpty("enchantments");
        for (String id : second.keySet()) if (first.getIntOr(id, 0) != second.getIntOr(id, 0)) return true;
        return false;
    }

    // from Builtin
    public static void register() {
        Features.add(new Feature("anvil_helper", "Menus", "Anvil helper", "In an Anvil: what can combine with the item you put in is green, and the middle turns red if two books have enchantments of different levels.", "SkyHanni: Anvil Combine Helper · Skyblocker: Anvil Helper")
                .when(SKYBLOCK)
                .option(Option.bool("anvil_helper.combine", "Highlight what combines", "", true))
                .option(Option.bool("anvil_helper.warn_levels", "Warn on different levels", "", true)));
        MenuMarks.add("anvil_helper", Patterns.get("SH.api.anvil.inventory"), Menus::anvil);

        ReforgeHelper.register(SKYBLOCK);
        MenuHighlights.register(SKYBLOCK);
        AttributeShards.register(SKYBLOCK);
        EssenceShop.register(SKYBLOCK);
        SlotTexts.register(SKYBLOCK);
        SlotBackgrounds.options();
        ItemMarks.register(SKYBLOCK);
        NotClickable.register(SKYBLOCK);
        CakeTracker.register(SKYBLOCK);
        TamingSixty.register(SKYBLOCK);
        MenuExtras.register(SKYBLOCK);
        Experiments.register(SKYBLOCK);
        ImprovedMenus.register();
    }

    // for pictures: each side panel by name, after a view of its menu went to MenuMarks.showForTest
    public static dev.zmuzy.sbc.ui.SideHelper.Content panelForTest(String name, MenuView view) {
        return switch (name) {
            case "reforge" -> {
                ReforgeHelper.mark(view);
                yield new ReforgePanel();
            }
            case "shards" -> {
                AttributeShards.attributeMenu(view);
                yield AttributeShards.overlayForTest();
            }
            case "box" -> {
                AttributeShards.huntingBox(view);
                yield AttributeShards.boxForTest();
            }
            case "essence" -> EssenceShop.panelForTest();
            case "experiments" -> Experiments.panelForTest();
            default -> CakeTracker.panelForTest();
        };
    }

    // from SbcMod, where the keys get made
    public static void keys(KeyMapping.Category category) {
        AttributeShards.trackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.track_shard", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), category));
        SlotTexts.key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.slot_text", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), category));
        dev.zmuzy.sbc.areas.HuntHelpers.keys(category);
        MenuExtras.focusKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.focus_mode", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), category));
    }
}
