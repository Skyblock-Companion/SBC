// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.render.GuiRenderer;

// /sbc perf record: the gui drawn (the hud picture's own draw is counted with the hud)
@Mixin(GuiRenderer.class)
public abstract class ProbeGuiRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void sbc$renderIn(CallbackInfo ci) {
        if (!FrameProbe.on || io.muzy.sbc.ui.HudTexture.redirect != null) return;
        try {
            FrameProbe.enter(FrameProbe.GUI_DRAW);
        } catch (Throwable t) {
            Safe.caught("ProbeGuiRendererMixin.renderIn", t);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void sbc$renderOut(CallbackInfo ci) {
        if (!FrameProbe.on || io.muzy.sbc.ui.HudTexture.redirect != null) return;
        try {
            FrameProbe.exit(FrameProbe.GUI_DRAW);
        } catch (Throwable t) {
            Safe.caught("ProbeGuiRendererMixin.renderOut", t);
        }
    }
}
