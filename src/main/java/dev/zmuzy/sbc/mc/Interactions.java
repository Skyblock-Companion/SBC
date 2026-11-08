// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc;

import dev.zmuzy.sbc.core.Safe;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// the local players clicks, like skyhanni BlockClickEvent / ItemClickEvent. block click (left/right + pos)
// and item click (left/right, main hand)
// built on fabrics interaction callbacks, they fire client side before the action is sent. a listener can cancel
public final class Interactions {
    public interface BlockClick {
        // pos = clicked block, int coords
        void on(Vec3 pos, boolean rightClick);
    }

    public interface ItemClick {
        // true cancels the click
        boolean on(boolean rightClick);
    }

    public interface SlotClick {
        // slot can be null for clicks outside, slotId is -999 outside. true eats the click
        boolean on(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button);
    }

    private static final List<BlockClick> BLOCK = new CopyOnWriteArrayList<>();
    private static final List<ItemClick> ITEM = new CopyOnWriteArrayList<>();
    private static final List<SlotClick> SLOT = new CopyOnWriteArrayList<>();
    private static boolean registered;
    private static net.minecraft.world.inventory.ContainerInput clickInput = net.minecraft.world.inventory.ContainerInput.PICKUP;

    private Interactions() {}

    public static void onSlotClick(SlotClick l) {
        SLOT.add(l);
    }

    // called by ContainerClickMixin, true cancels
    public static boolean slotClick(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button, net.minecraft.world.inventory.ContainerInput input) {
        clickInput = input;
        boolean cancel = false;
        for (SlotClick l : SLOT) {
            try {
                cancel |= l.on(screen, slot, slotId, button);
            } catch (Throwable t) {
                dev.zmuzy.sbc.SbcMod.LOG.warn("slot click listener failed", t);
            }
        }
        return cancel;
    }

    // skyhanni SlotClickEvent.clickType for a SlotClick listener: how the slot being handled got clicked, like
    // QUICK_MOVE for shift click
    public static net.minecraft.world.inventory.ContainerInput clickInput() {
        return clickInput;
    }

    // called by LeftClickMixin for an attack key press the game will act on. skyhanni handleClick fires the item
    // click for a miss, block and mob alike
    // blocks already fire it from AttackBlockCallback below (which also sees the crosshair moving to another
    // block while held), so only the rest here
    public static void leftClick(net.minecraft.world.phys.HitResult hit) {
        if (hit != null && hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) item(false);
    }

    public static void onBlockClick(BlockClick l) {
        register();
        BLOCK.add(l);
    }

    public static void onItemClick(ItemClick l) {
        register();
        ITEM.add(l);
    }

    private static boolean item(boolean right) {
        boolean cancel = false;
        for (ItemClick l : ITEM) {
            try {
                cancel |= l.on(right);
            } catch (Throwable t) {
                dev.zmuzy.sbc.SbcMod.LOG.warn("item click listener failed", t);
            }
        }
        return cancel;
    }

    private static void block(Vec3 pos, boolean right) {
        for (BlockClick l : BLOCK) {
            try {
                l.on(pos, right);
            } catch (Throwable t) {
                dev.zmuzy.sbc.SbcMod.LOG.warn("block click listener failed", t);
            }
        }
    }

    private static synchronized void register() {
        if (registered) return;
        registered = true;
        Safe.useBlock("Interactions.useBlock", (player, level, hand, hit) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            var p = hit.getBlockPos();
            block(new Vec3(p.getX(), p.getY(), p.getZ()), true);
            return item(true) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        Safe.useItem("Interactions.useItem", (player, level, hand) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            return item(true) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        Safe.attackBlock("Interactions.attackBlock", (player, level, hand, pos, dir) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            block(new Vec3(pos.getX(), pos.getY(), pos.getZ()), false);
            item(false);
            return InteractionResult.PASS;
        });
    }
}
