// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.ui.BrowserScreen;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.Blaze3D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

// skyblock wiki links from chat or any mod open in game while playing. every other link is the games business
@Mixin(Blaze3D.class)
public abstract class OpenUriMixin {
    @Inject(method = "openUri(Ljava/net/URI;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sbc$openInGame(URI uri, CallbackInfo ci) {
        if (Safe.resting("OpenUriMixin.openInGame")) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!Config.I.inGameBrowser || mc.level == null || !dev.zmuzy.sbc.web.WebPage.isWiki(uri.toString()) || !(scheme.equals("http") || scheme.equals("https"))) return;
            ci.cancel();
            mc.execute(() -> mc.gui.setScreen(new BrowserScreen(mc.gui.screen(), uri.toString())));
        } catch (Throwable t) {
            Safe.caught("OpenUriMixin.openInGame", t);
        }
    }
}
