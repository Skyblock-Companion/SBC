// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.guard;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.mc.Interactions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Set;

// from skyblocker ConsumableProtection: right click with one of these in hand does nothing, air or block
// (theyre heads, placing one eats it too)
// they take ages to mature so one misclick is a loss you cant undo. only stops your own click
public final class ConsumableProtection {
    public static final String ID = "consumable_protection";
    // same 5 items as skyblocker
    static final Set<String> PROTECTED_CONSUMABLES = Set.of("NEW_BOTTLE_OF_JYRRE", "DARK_CACAO_TRUFFLE", "DISCRITE", "MOBY_DUCK", "ROSEWATER_FLASK");

    private ConsumableProtection() {}

    static boolean isProtected(String skyblockId) {
        return !skyblockId.isEmpty() && PROTECTED_CONSUMABLES.contains(skyblockId);
    }

    private static boolean on() {
        return Features.on(ID) && (GameState.inSkyBlock || SbcMod.DEV);
    }

    private static boolean blocks(ItemStack stack) {
        return on() && isProtected(Gain.sbId(stack));
    }

    // Interactions only reports the main hand, skyblocker checks whichever hand clicked
    private static InteractionResult offHand(Player player, Level level, InteractionHand hand) {
        if (level.isClientSide() && hand == InteractionHand.OFF_HAND && blocks(player.getItemInHand(hand))) return InteractionResult.FAIL;
        return InteractionResult.PASS;
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "Consumable protection", "Right clicking does nothing while you hold a Bottle of Jyrre, Dark Cacao Truffle, Discrite, Moby Duck or Rosewater Flask, so a slip cannot eat what took weeks to mature.", "Skyblocker: consumable protection")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Interactions.onItemClick(right -> {
            var mc = Minecraft.getInstance();
            return right && mc.player != null && blocks(mc.player.getMainHandItem());
        });
        Safe.useItem("ConsumableProtection.offHand", ConsumableProtection::offHand);
        Safe.useBlock("ConsumableProtection.useBlock", (player, level, hand, hit) -> offHand(player, level, hand));
    }
}
