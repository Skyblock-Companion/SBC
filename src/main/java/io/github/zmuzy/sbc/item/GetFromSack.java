// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.item;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// one key over an item runs "/gfs ID amount" for it: a full stack, or just enough to top up the stack under the mouse
// only sent when you press the key, once per press
public final class GetFromSack {
    private GetFromSack() {}

    public static void press(Slot hovered, boolean oneStack) {
        Minecraft mc = Minecraft.getInstance();
        if (hovered == null || mc.getConnection() == null) return;
        ItemStack st = hovered.getItem();
        String id = Gain.sbId(st);
        if (id.isEmpty()) return;
        // skyhanni GfSKeybind: "fills your inventory with the item you are hovering over"
        int amount = io.github.zmuzy.sbc.item.market.Market.b("gfs_key", "gfs_key.fill", false) ? 9999 : oneStack ? st.getMaxStackSize() : Math.max(1, st.getMaxStackSize() - st.getCount());
        mc.getConnection().sendCommand("gfs " + id + " " + amount);
        Toasts.push("Get from sack", amount + " x " + st.getHoverName().getString(), 0xFF39D0FF);
    }
}
