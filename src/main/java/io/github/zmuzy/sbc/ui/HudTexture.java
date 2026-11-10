// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.mc.mixin.GameRendererAccess;
import io.github.zmuzy.sbc.mc.mixin.GuiGraphicsAccessor;
import io.github.zmuzy.sbc.mc.mixin.GuiRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

// the hud as one picture, drawn into its own texture 20x a sec and shown as one quad in between
// same trick as vanillas picture-in-picture for entities in menus. one picture per name (hud, hud over a
// menu, tab list)
public final class HudTexture {
    static final long KEEP_MS = 50;
    private static final Vector4f CLEAR = new Vector4f(0, 0, 0, 0);

    // the gui renderer draws here instead of the screen while set
    public static volatile RenderTarget redirect;

    private static final class Picture {
        TextureTarget target;
        GuiRenderState state;
        GuiRenderer renderer;
        long drawnAt;
        String drawnFor = "";
        boolean have;
    }

    private static final Map<String, Picture> PICTURES = new HashMap<>();
    private static long fresh, shown;
    private static boolean broken;
    private static Boolean hooked;

    private HudTexture() {}

    public static boolean on() {
        if (broken) return false;
        var f = Features.get("appearance");
        var o = f == null ? null : f.option("appearance.hud_texture");
        return (o == null || o.bool()) && hooked();
    }

    // the mixin that points the gui renderer at our texture has to be there or the hud vanishes
    private static boolean hooked() {
        if (hooked == null) {
            boolean found = false;
            for (var m : GuiRenderer.class.getDeclaredMethods()) if (m.getName().contains("sbc$hudTarget")) found = true;
            hooked = found;
        }
        return hooked;
    }

    // share of frames since the last resetShare. "off" while the setting is off, so a recording made with it
    // off never shows the frames from before
    public static String share() {
        if (!on()) return "off";
        long all = fresh + shown;
        return all == 0 ? "-" : Math.round(100.0 * shown / all) + "%";
    }

    public static void resetShare() {
        fresh = shown = 0;
    }

    public static void forget() {
        for (Picture p : PICTURES.values()) p.have = false;
    }

    private static Picture ensure(Minecraft mc, String name, int w, int h) {
        Picture p = PICTURES.computeIfAbsent(name, k -> new Picture());
        if (p.target != null && (p.target.width != w || p.target.height != h)) {
            p.target.destroyBuffers();
            p.target = null;
        }
        if (p.target == null) p.target = new TextureTarget("sbc " + name, w, h, com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM, com.mojang.renderpearl.api.GpuFormat.D32_FLOAT);
        if (p.renderer == null) {
            p.state = new GuiRenderState();
            GuiRenderer own = ((GameRendererAccess) mc.gameRenderer).sbc$guiRenderer();
            var pips = new ArrayList<>(((GuiRendererAccess) own).sbc$pictureInPictureRenderers().values());
            p.renderer = new GuiRenderer(p.state, mc.gameRenderer.featureRenderDispatcher(), pips);
        }
        return p;
    }

    public static boolean show(GuiGraphicsExtractor g, Ui ui, String key, int sw, int sh, boolean moving, Consumer<GuiGraphicsExtractor> drawer) {
        return show("hud", g, ui, key, sw, sh, moving, drawer);
    }

    // draws what drawer draws as a picture on g. ui (if given) draws on the picture and gets g back after
    // false = cant do it this way, draw live
    public static boolean show(String name, GuiGraphicsExtractor g, Ui ui, String key, int sw, int sh, boolean moving, Consumer<GuiGraphicsExtractor> drawer) {
        if (!on()) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.gameRenderer.mainRenderTarget();
            if (main == null || main.width <= 0 || main.height <= 0) return false;
            long now = System.currentTimeMillis();
            Picture p = ensure(mc, name, main.width, main.height);
            if (!p.have || moving || !key.equals(p.drawnFor) || now - p.drawnAt >= KEEP_MS) {
                io.github.zmuzy.sbc.core.FrameProbe.hudRedrawn();
                io.github.zmuzy.sbc.core.FrameProbe.enter(io.github.zmuzy.sbc.core.FrameProbe.HUD_PICTURE);
                GuiGraphicsExtractor mine = new GuiGraphicsExtractor(mc, p.state, sw, sh);
                if (ui != null) ui.begin(mine, -1, -1);
                try {
                    drawer.accept(mine);
                } finally {
                    if (ui != null) ui.begin(g, -1, -1);
                }
                var encoder = RenderSystem.getDevice().createCommandEncoder();
                encoder.clearColorAndDepthTextures(p.target.getColorTexture(), CLEAR, p.target.getDepthTexture(), 1.0);
                redirect = p.target;
                try {
                    p.renderer.render();
                } finally {
                    redirect = null;
                }
                p.drawnAt = now;
                p.drawnFor = key;
                p.have = true;
                fresh++;
                io.github.zmuzy.sbc.core.FrameProbe.exit(io.github.zmuzy.sbc.core.FrameProbe.HUD_PICTURE);
            } else shown++;
            GuiRenderState real = ((GuiGraphicsAccessor) g).sbc$guiRenderState();
            real.nextStratum();
            real.addBlitToCurrentLayer(new BlitRenderState(RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                    TextureSetup.singleTexture(p.target.getColorTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                    new Matrix3x2f(), 0, 0, sw, sh, 0f, 1f, 1f, 0f, -1, null));
            return true;
        } catch (Throwable t) {
            broken = true;
            redirect = null;
            forget();
            Safe.caught("HudTexture.show", t);
            return false;
        }
    }
}
