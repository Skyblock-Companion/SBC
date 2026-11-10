// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.ui.AutoStrata;
import io.github.zmuzy.sbc.ui.HudReplay;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// records what the hud puts in the frame so the next frames can put the same pieces back (HudReplay)
// only listens while the hud draws, any other call is one boolean check
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateRecordMixin {
    @Inject(method = "addItem", at = @At("HEAD"), require = 0)
    private void sbc$item(GuiItemRenderState s, CallbackInfo ci) {
        if (!HudReplay.recording && AutoStrata.every == 0 && !io.github.zmuzy.sbc.core.FrameProbe.on) return;
        try {
            io.github.zmuzy.sbc.core.FrameProbe.count(io.github.zmuzy.sbc.core.FrameProbe.C_ITEMS, 1);
            AutoStrata.piece((GuiRenderState) (Object) this);
            if (HudReplay.recording) HudReplay.seen(HudReplay.ITEM, s);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.item", t);
        }
    }

    @Inject(method = "addText", at = @At("HEAD"), require = 0)
    private void sbc$text(GuiTextRenderState s, CallbackInfo ci) {
        if (!HudReplay.recording && AutoStrata.every == 0 && !io.github.zmuzy.sbc.core.FrameProbe.on) return;
        try {
            io.github.zmuzy.sbc.core.FrameProbe.count(io.github.zmuzy.sbc.core.FrameProbe.C_TEXT, 1);
            AutoStrata.piece((GuiRenderState) (Object) this);
            if (HudReplay.recording) HudReplay.seen(HudReplay.TEXT, s);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.text", t);
        }
    }

    @Inject(method = "addGuiElement", at = @At("HEAD"), require = 0)
    private void sbc$element(GuiElementRenderState s, CallbackInfo ci) {
        if (!HudReplay.recording && AutoStrata.every == 0 && !io.github.zmuzy.sbc.core.FrameProbe.on) return;
        try {
            io.github.zmuzy.sbc.core.FrameProbe.count(io.github.zmuzy.sbc.core.FrameProbe.C_GUI, 1);
            AutoStrata.piece((GuiRenderState) (Object) this);
            if (HudReplay.recording) HudReplay.seen(HudReplay.ELEMENT, s);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.element", t);
        }
    }

    @Inject(method = "addBlitToCurrentLayer", at = @At("HEAD"), require = 0)
    private void sbc$blit(BlitRenderState s, CallbackInfo ci) {
        if (!HudReplay.recording) return;
        try {
            HudReplay.seen(HudReplay.BLIT, s);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.blit", t);
        }
    }

    @Inject(method = "addGlyphToCurrentLayer", at = @At("HEAD"), require = 0)
    private void sbc$glyph(GuiElementRenderState s, CallbackInfo ci) {
        if (!HudReplay.recording) return;
        try {
            HudReplay.seen(HudReplay.GLYPH, s);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.glyph", t);
        }
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"), require = 0)
    private void sbc$picture(PictureInPictureRenderState s, CallbackInfo ci) {
        if (!HudReplay.recording) return;
        try {
            HudReplay.seen(HudReplay.PICTURE, s);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.picture", t);
        }
    }

    @Inject(method = "up", at = @At("HEAD"), require = 0)
    private void sbc$up(CallbackInfo ci) {
        if (!HudReplay.recording) return;
        try {
            HudReplay.seen(HudReplay.UP, null);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.up", t);
        }
    }

    @Inject(method = "nextStratum", at = @At("HEAD"), require = 0)
    private void sbc$stratum(CallbackInfo ci) {
        if (!HudReplay.recording) return;
        try {
            HudReplay.seen(HudReplay.STRATUM, null);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.stratum", t);
        }
    }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"), require = 0)
    private void sbc$blur(CallbackInfo ci) {
        if (!HudReplay.recording) return;
        try {
            HudReplay.seen(HudReplay.BLUR, null);
        } catch (Throwable t) {
            Safe.caught("GuiRenderStateRecordMixin.blur", t);
        }
    }
}
