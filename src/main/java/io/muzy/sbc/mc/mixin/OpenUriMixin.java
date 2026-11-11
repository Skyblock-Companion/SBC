// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.ui.BrowserScreen;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
import com.mojang.blaze3d.Blaze3D;
//?} else {
/*import net.minecraft.util.Util;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;

// skyblock wiki links from chat or any mod open in game while playing. every other link is the games business
//? if >=26.3 {
@Mixin(Blaze3D.class)
public abstract class OpenUriMixin {
//?} else {
/*@Mixin(Util.OS.class)
public class OpenUriMixin {
*///?}
    @Inject(method = "openUri(Ljava/net/URI;)V", at = @At("HEAD"), cancellable = true, require = 0)
    //? if >=26.3 {
    private static void sbc$openInGame(URI uri, CallbackInfo ci) {
    //?} else {
    /*private void sbc$openInGame(URI uri, CallbackInfo ci) {
    *///?}
        if (Safe.resting("OpenUriMixin.openInGame")) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            if (!Config.I.inGameBrowser || mc.level == null || !io.muzy.sbc.web.WebPage.isWiki(uri.toString()) || !(scheme.equals("http") || scheme.equals("https"))) return;
            ci.cancel();
            mc.execute(() -> mc.gui.setScreen(new BrowserScreen(mc.gui.screen(), uri.toString())));
        } catch (Throwable t) {
            Safe.caught("OpenUriMixin.openInGame", t);
        }
    }
}
