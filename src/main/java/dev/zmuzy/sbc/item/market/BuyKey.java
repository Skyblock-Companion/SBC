// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.item.market;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.mc.mixin.ContainerAccess;
import dev.zmuzy.sbc.ui.ItemList;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

// j over an item in any menu or a line of the item list adds one to the buy list
public final class BuyKey {
    private static KeyMapping key;

    private BuyKey() {}

    public static void keys(KeyMapping.Category category) {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.buy_list", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, category));
    }

    // false = taken
    public static boolean key(Screen s, KeyEvent event) {
        if (key == null || !key.matches(event)) return true;
        String id = ItemList.hoveredId();
        if (id == null && s instanceof ContainerAccess a) {
            var slot = a.sbc$hovered();
            if (slot != null && !slot.getItem().isEmpty()) id = Gain.sbId(slot.getItem());
        }
        if (id == null || id.isEmpty()) return true;
        int n = MoreMarket.add(id, 1);
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §7Buy list: §f" + GameState.strip(ItemRepo.name(id)) + " §7x" + n + "§7. It shows beside every menu till you clear it."));
        return false;
    }
}
