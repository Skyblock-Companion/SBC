// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// from skyblocker EquipmentLayerRendererMixin: armor model of one item
@Mixin(EquipmentLayerRenderer.class)
public abstract class CustomLooksArmorMixin {
    @ModifyVariable(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private ResourceKey<EquipmentAsset> sbc$armor(ResourceKey<EquipmentAsset> asset, @Local(argsOnly = true) ItemStack stack) {
        if (Safe.resting("CustomLooksArmorMixin.armor")) return asset;
        try {
            return io.github.zmuzy.sbc.item.custom.CustomLooks.armorModel(asset, stack);
        } catch (Throwable t) {
            Safe.caught("CustomLooksArmorMixin.armor", t);
            return asset;
        }
    }
}
