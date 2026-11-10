// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.item.tooltip.FancyTooltip;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

// what vanilla would add under a skyblock item (enchants, attributes, trims...) stays out of its card
@Mixin(ItemStack.class)
public class HideVanillaTooltipMixin {
    @Unique
    private boolean sbc$hides(DataComponentType<?> type) {
        if (Safe.resting("HideVanillaTooltipMixin.hides")) return false;
        try {
            if (!FancyTooltip.hidesVanilla() || Gain.sbId((ItemStack) (Object) this).isEmpty()) return false;
            return type == DataComponents.ENCHANTMENTS || type == DataComponents.ATTRIBUTE_MODIFIERS || type == DataComponents.UNBREAKABLE
                    || type == DataComponents.FIREWORKS || type == DataComponents.FIREWORK_EXPLOSION || type == DataComponents.TRIM
                    || type == DataComponents.POTION_CONTENTS || type == DataComponents.JUKEBOX_PLAYABLE || type == DataComponents.STORED_ENCHANTMENTS;
        } catch (Throwable t) {
            Safe.caught("HideVanillaTooltipMixin.hides", t);
            return false;
        }
    }

    @Inject(method = "addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$standard(DataComponentType<?> type, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> consumer, TooltipFlag flag, CallbackInfo ci) {
        if (sbc$hides(type)) ci.cancel();
    }

    @Inject(method = "addUnitComponentToTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$unit(DataComponentType<?> type, Component component, TooltipDisplay display, Consumer<Component> consumer, CallbackInfo ci) {
        if (sbc$hides(type)) ci.cancel();
    }

    @Inject(method = "addAttributeTooltips", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$attributes(Consumer<Component> consumer, TooltipDisplay display, Player player, CallbackInfo ci) {
        if (sbc$hides(DataComponents.ATTRIBUTE_MODIFIERS)) ci.cancel();
    }
}
