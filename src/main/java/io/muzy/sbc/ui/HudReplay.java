// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

import java.util.ArrayList;
import java.util.List;

// hud drawn once every 50ms instead of every frame. drawing it = cards working out lines + glyph prep + item models
// (~5ms a frame with a full garden hud). we keep the finished pieces and put them back on the frames between
// looks the same, numbers lag 50ms max. editor, menus and sliding cards draw live
public final class HudReplay {
    public static final int ITEM = 0, TEXT = 1, ELEMENT = 2, BLIT = 3, GLYPH = 4, PICTURE = 5, UP = 6, STRATUM = 7, BLUR = 8;
    static final long KEEP_MS = 50;

    public static volatile boolean recording;
    private static List<Object[]> taking = new ArrayList<>(), kept = List.of();
    private static long keptAt;
    private static String keptFor = "";
    private static boolean broken;

    private HudReplay() {}

    private static long replayed, drawnFresh;

    // how many frames put the kept hud back, for the performance report
    public static String share() {
        long all = replayed + drawnFresh;
        return all == 0 ? "-" : Math.round(100.0 * replayed / all) + "%" + (broken ? " (off: not heard)" : "");
    }

    public static void seen(int kind, Object piece) {
        taking.add(new Object[]{kind, piece});
    }

    // the kept pieces for this screen size and state, if still fresh: put back, true. otherwise false and draw
    public static boolean replay(GuiGraphicsExtractor g, String key) {
        if (broken || kept.isEmpty() || !key.equals(keptFor) || System.currentTimeMillis() - keptAt >= KEEP_MS) {
            drawnFresh++;
            return false;
        }
        replayed++;
        GuiRenderState s = ((io.muzy.sbc.mc.mixin.GuiGraphicsAccessor) g).sbc$guiRenderState();
        for (Object[] op : kept) {
            switch ((int) op[0]) {
                case ITEM -> s.addItem((GuiItemRenderState) op[1]);
                case TEXT -> s.addText((GuiTextRenderState) op[1]);
                case ELEMENT -> s.addGuiElement((GuiElementRenderState) op[1]);
                case BLIT -> s.addBlitToCurrentLayer((BlitRenderState) op[1]);
                case GLYPH -> s.addGlyphToCurrentLayer((GuiElementRenderState) op[1]);
                case PICTURE -> s.addPicturesInPictureState((PictureInPictureRenderState) op[1]);
                case UP -> s.up();
                case STRATUM -> s.nextStratum();
                case BLUR -> s.blurBeforeThisStratum();
                default -> {
                }
            }
        }
        return true;
    }

    public static void begin() {
        taking = new ArrayList<>();
        recording = true;
    }

    // keep what was drawn. still moving (sliding, fading) = keep nothing, redraw next frame
    // drew stuff but heard none of it = the listener isnt in, never replay or the hud vanishes
    public static void end(String key, boolean moving, int drawnByUs) {
        recording = false;
        if (drawnByUs > 0 && taking.isEmpty()) broken = true;
        if (moving || broken) {
            kept = List.of();
            return;
        }
        kept = taking;
        keptAt = System.currentTimeMillis();
        keptFor = key;
    }

    // something changed how the hud should look (editor closed, a setting), draw fresh
    public static void forget() {
        kept = List.of();
        HudTexture.forget();
    }
}
