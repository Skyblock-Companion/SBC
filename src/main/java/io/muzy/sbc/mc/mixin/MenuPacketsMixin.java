// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.game.MenuPackets;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// the packets skyhanni OtherInventoryData listens to
// each of these methods first moves itself to the game thread by throwing on the network thread
// so RETURN only runs once, on the game thread, with the menu already changed
@Mixin(ClientPacketListener.class)
public class MenuPacketsMixin {
    private static final Map<MenuType<?>, Integer> SBC$SLOT_COUNT = Map.ofEntries(
            Map.entry(MenuType.ANVIL, 3),
            Map.entry(MenuType.BEACON, 1),
            Map.entry(MenuType.BLAST_FURNACE, 3),
            Map.entry(MenuType.BREWING_STAND, 5),
            Map.entry(MenuType.CARTOGRAPHY_TABLE, 2),
            Map.entry(MenuType.CRAFTING, 9),
            Map.entry(MenuType.CRAFTER_3x3, 9),
            Map.entry(MenuType.ENCHANTMENT, 2),
            Map.entry(MenuType.FURNACE, 3),
            Map.entry(MenuType.GENERIC_3x3, 9),
            Map.entry(MenuType.GENERIC_9x1, 9),
            Map.entry(MenuType.GENERIC_9x2, 18),
            Map.entry(MenuType.GENERIC_9x3, 27),
            Map.entry(MenuType.GENERIC_9x4, 36),
            Map.entry(MenuType.GENERIC_9x5, 45),
            Map.entry(MenuType.GENERIC_9x6, 54),
            Map.entry(MenuType.GRINDSTONE, 3),
            Map.entry(MenuType.HOPPER, 5),
            Map.entry(MenuType.LECTERN, 1),
            Map.entry(MenuType.LOOM, 3),
            Map.entry(MenuType.MERCHANT, 3),
            Map.entry(MenuType.SHULKER_BOX, 27),
            Map.entry(MenuType.SMITHING, 3),
            Map.entry(MenuType.SMOKER, 3),
            Map.entry(MenuType.STONECUTTER, 1));

    @Inject(method = "handleOpenScreen", at = @At("RETURN"))
    private void sbc$open(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (Safe.resting("MenuPacketsMixin.open")) return;
        try {
            MenuPackets.open(packet.getContainerId(), SBC$SLOT_COUNT.getOrDefault(packet.getType(), -1));
        } catch (Throwable t) {
            Safe.caught("MenuPacketsMixin.open", t);
        }
    }

    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void sbc$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (Safe.resting("MenuPacketsMixin.content")) return;
        try {
            List<Boolean> filled = new ArrayList<>(packet.items().size());
            for (ItemStack stack : packet.items()) filled.add(!stack.isEmpty());
            MenuPackets.content(packet.containerId(), filled);
        } catch (Throwable t) {
            Safe.caught("MenuPacketsMixin.content", t);
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void sbc$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (Safe.resting("MenuPacketsMixin.slot")) return;
        try {
            MenuPackets.slot(packet.getContainerId(), packet.getSlot(), !packet.getItem().isEmpty());
        } catch (Throwable t) {
            Safe.caught("MenuPacketsMixin.slot", t);
        }
    }

    @Inject(method = "handleContainerClose", at = @At("RETURN"))
    private void sbc$close(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        if (Safe.resting("MenuPacketsMixin.close")) return;
        try {
            MenuPackets.close();
        } catch (Throwable t) {
            Safe.caught("MenuPacketsMixin.close", t);
        }
    }
}
