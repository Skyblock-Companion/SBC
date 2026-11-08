// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.calc.SignCalculator;
import dev.zmuzy.sbc.item.market.BazaarSign;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// like skyblocker AbstractSignEditScreenMixin: sign calculator, enter closes a number sign, max in the
// quantity sign, quick buttons
@Mixin(AbstractSignEditScreen.class)
public abstract class SignInputMixin extends Screen {
    @Shadow
    @Final
    private String[] messages;

    @Shadow
    private int line;

    @Shadow
    private TextFieldHelper signField;

    @Shadow
    public abstract void onClose();

    protected SignInputMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void sbc$init(CallbackInfo ci) {
        if (Safe.resting("SignInputMixin.init")) return;
        try {
            for (Button b : BazaarSign.buttons(this.width, messages, this::onClose)) addRenderableWidget(b);
            for (Button b : dev.zmuzy.sbc.garden.farming.FarmingSigns.buttons(messages)) addRenderableWidget(b);
        } catch (Throwable t) {
            Safe.caught("SignInputMixin.init", t);
        }
    }

    @Inject(method = "charTyped", at = @At("TAIL"), require = 0)
    private void sbc$charTyped(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("SignInputMixin.charTyped")) return;
        try {
            if (event.codepoint() == ' ') BazaarSign.expandMax(signField, messages, line);
        } catch (Throwable t) {
            Safe.caught("SignInputMixin.charTyped", t);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void sbc$draw(GuiGraphicsExtractor g, int mx, int my, float a, CallbackInfo ci) {
        if (Safe.resting("SignInputMixin.draw")) return;
        try {
            if (SignCalculator.inSkyBlock() && SignCalculator.isInputSign(messages)) SignCalculator.draw(g, this.font, messages[0], g.guiWidth() / 2, 55);
        } catch (Throwable t) {
            Safe.caught("SignInputMixin.draw", t);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), require = 0)
    private void sbc$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("SignInputMixin.keyPressed")) return;
        try {
            if (input.isConfirmation() && SignCalculator.closesOnEnter(messages)) this.onClose();
        } catch (Throwable t) {
            Safe.caught("SignInputMixin.keyPressed", t);
        }
    }

    @Inject(method = "onDone", at = @At("HEAD"), require = 0)
    private void sbc$finish(CallbackInfo ci) {
        if (Safe.resting("SignInputMixin.finish")) return;
        try {
            SignCalculator.finish(messages);
        } catch (Throwable t) {
            Safe.caught("SignInputMixin.finish", t);
        }
    }
}
