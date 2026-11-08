// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.ui.TextOrder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// text(String) gets its visual order from TextOrder. optional
@Mixin(GuiGraphicsExtractor.class)
public abstract class TextOrderMixin {
    @WrapOperation(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;getVisualOrder(Lnet/minecraft/network/chat/FormattedText;)Lnet/minecraft/util/FormattedCharSequence;"),
            require = 0)
    private FormattedCharSequence sbc$order(Language language, FormattedText text, Operation<FormattedCharSequence> original,
                                            @Local(argsOnly = true) String s) {
        try {
            return TextOrder.get(language, s, k -> original.call(language, text));
        } catch (Throwable t) {
            Safe.caught("TextOrderMixin.order", t);
            return original.call(language, text);
        }
    }
}
