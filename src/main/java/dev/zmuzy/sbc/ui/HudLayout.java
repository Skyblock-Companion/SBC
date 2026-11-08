// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// lays out the hud. cards say their zone, size and if they have anything to show
// bars sit on the hotbar, small plates and the pet next to it, the rest stacks in the corners
public final class HudLayout {
    public enum Zone { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT, HOTBAR_LEFT, HOTBAR_RIGHT }

    // one card as placed right now (screen px, scaled). element null = the old vitals frame slot
    public static final class Placed {
        public final String id, title;
        public final HudElement element;
        public int x, y, w, h;
        public float scale;
        public boolean visible, pinned;
        // kept wider than its content, see steadyWidth
        boolean held;

        Placed(String id, String title, HudElement element) {
            this.id = id;
            this.title = title;
            this.element = element;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    private static final List<HudElement> ELEMENTS = new ArrayList<>();
    private static final Map<Zone, Integer> BOTTOM = new EnumMap<>(Zone.class);
    private static final Map<String, long[]> MEAS = new HashMap<>();
    private static final int GAP = 6;
    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;
    private HudLayout() {}

    public static void add(HudElement e) {
        ELEMENTS.removeIf(x -> x.id.equals(e.id));
        ELEMENTS.add(e);
        ELEMENTS.sort(Comparator.comparingInt((HudElement x) -> x.priority));
        Feature f = e.feature();
        if (f != null && f.option("show") == null)
            f.option(dev.zmuzy.sbc.feature.Option.choice(f.id + ".show", "Show", "When needed: only with something to show. Always: kept on screen. Never: hidden while the feature stays on.", "When needed", "When needed", "Always", "Never"));

        // a card that belongs in another one goes there as soon as both are here
        if (dev.zmuzy.sbc.ui.hud.HudMerges.involves(e.id)) dev.zmuzy.sbc.ui.hud.HudMerges.link();
    }

    public static List<HudElement> elements() {
        return ELEMENTS;
    }

    public static HudElement element(String id) {
        for (HudElement e : ELEMENTS) if (e.id.equals(id)) return e;
        return null;
    }

    // screen y under the last card of a zone, 0 = empty. valid after render
    public static int bottom(Zone z) {
        return BOTTOM.getOrDefault(z, 0);
    }

    // --- per card prefs ---

    public static java.util.Map<String, float[]> prefs() {
        return Config.I.hudPrefs;
    }

    public static java.util.Map<String, int[]> nudges() {
        return Config.I.zoneNudge;
    }

    private static float[] pref(String id, boolean create) {
        float[] p = prefs().get(id);
        if (p == null && create) {
            p = new float[]{0, 0, 0, defaultScale(id)};
            prefs().put(id, p);
        }
        return p;
    }

    private static float defaultScale(String id) {
        HudElement e = element(id);
        return e == null ? 1f : e.defaultScale();
    }

    public static float scaleOf(String id) {
        float[] p = pref(id, false);
        return p == null || p.length < 4 ? defaultScale(id) : Math.max(MIN_SCALE, Math.min(MAX_SCALE, p[3]));
    }

    // corner text size, ~3/4 of the game text. picked per gui scale so a font pixel is 1.5 or a whole number of screen px
    // any other ratio makes the letters uneven
    public static float infoScale() {
        return infoScale(guiScale());
    }

    private static int guiScale() {
        if (previewGui > 0) return previewGui;
        try {
            return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        } catch (Throwable t) {
            return 2;
        }
    }

    static float infoScale(int gui) {
        if (gui <= 1) return 1f;
        return Math.round(gui * 0.72f * 2) / 2f / gui;
    }

    // when stuff doesnt fit the biggest sizes shrink first, one clean step at a time like infoScale. so the
    // scoreboard gives way before the small ones
    static float nextLimit(float limit) {
        return Math.max(1f, (float) Math.ceil(limit * 2 - 1.001f) / 2f);
    }

    private static final long HOLD_MS = 8000;
    private static final Map<String, long[]> HELD = new HashMap<>();

    // grows at once, narrows after a while, so a timer going 10m 0s -> 9m 59s doesnt make the whole column breathe
    // only for cards that can draw at any width, the editor always measures fresh
    private static int steadyWidth(HudElement e, int measured, boolean editing) {
        if (editing || !e.stretchable()) return measured;
        long[] held = HELD.computeIfAbsent(e.id, k -> new long[2]);
        long now = System.currentTimeMillis();
        if (measured >= held[0] || now - held[1] > HOLD_MS) {
            held[0] = measured;
            held[1] = now;
        }
        return (int) held[0];
    }

    // scoreboard size, closer to the game text, same pixel grid
    public static float largeScale() {
        return largeScale(guiScale());
    }

    static float largeScale(int gui) {
        if (gui <= 1) return 1f;
        return Math.round(gui * 0.9f * 2) / 2f / gui;
    }

    public static boolean pinned(String id) {
        float[] p = pref(id, false);
        return p != null && p.length >= 3 && p[0] > 0;
    }

    public static float pinX(String id) {
        return pref(id, true)[1];
    }

    public static float pinY(String id) {
        return pref(id, true)[2];
    }

    public static void pin(String id, int x, int y) {
        float[] p = pref(id, true);
        p[0] = 1;
        p[1] = x;
        p[2] = y;
    }

    // back to automatic: it arranges itself again, its size stays
    public static void unpin(String id) {
        float[] p = pref(id, false);
        if (p != null && p.length >= 3) p[0] = 0;
    }

    public static void setScale(String id, float s) {
        pref(id, true)[3] = Math.round(Math.max(MIN_SCALE, Math.min(MAX_SCALE, s)) * 20) / 20f;
    }

    public static void reset(String id) {
        prefs().remove(id);
    }

    public static void resetAll() {
        prefs().clear();
        Config.I.hudAll = 1f;
        Config.I.hudScale = 1f;
        Config.I.hudX = 6;
        nudges().clear();
    }

    public static void setAll(float s) {
        Config.I.hudAll = Math.round(Math.max(0.5f, Math.min(2f, s)) * 20) / 20f;
    }

    public static String titleOf(String id) {
        HudElement e = element(id);
        if (e != null) return e.title;
        return id.equals("vitals") ? "Health, mana and hotbar" : id;
    }

    // --- measuring and placing ---

    private static boolean shows(HudElement e) {
        try {
            return e.shows();
        } catch (Throwable t) {
            return false;
        }
    }

    // visible / w / h re-read ~10x a sec, not every frame
    private static long[] measure(HudElement e, boolean fresh) {
        long[] m = MEAS.computeIfAbsent(e.id, k -> new long[4]);
        long now = System.currentTimeMillis();
        if (fresh || now - m[0] > 100) {
            m[0] = now;
            m[1] = shows(e) ? 1 : 0;
            // only measure cards that are on screen (or in the editor). measuring builds all the content
            // and doing it for every card, shown or not, costs more than the whole hud
            if (m[1] == 0 && !e.demo) {
                m[2] = m[3] = 0;
                return m;
            }
            try {
                // with the members it carries under its own lines
                m[2] = e.wholeWidth();
                m[3] = e.wholeHeight();
            } catch (Throwable t) {
                m[1] = 0;
                m[2] = m[3] = 0;
            }
        }
        return m;
    }

    private static int[] nudge(Zone z) {
        int[] n = nudges().get(z.name());
        return n == null || n.length < 2 ? new int[]{0, 0} : n;
    }

    // on = every card of every island in the editor
    public static boolean editorAllIslands;
    // null = this island or everything
    public static HudGroups.Group editorGroup;
    // gap between the stacks above the hotbar, and between the hotbar and the first one
    private static final int BOTTOM_GAP = 2;
    private static final Map<String, Integer> COLUMNS = new HashMap<>();
    private static String columnsFor = "";

    // tests only: just these cards in the editor (whole screen pictures)
    static java.util.Set<String> previewOnly;
    // tests only: gui scale (0 = window) + chat size at rest (null = the games)
    static int previewGui;
    static int[] previewChat;
    static boolean previewShrink;

    // right on the hotbar unless some vanilla thing is still in between
    private static int hotbarStackBase(int sw, int sh) {
        if (previewOnly != null) return sh - 22 - BOTTOM_GAP;
        if (!dev.zmuzy.sbc.ui.hud.BarsElement.hidesVanilla()) return sh - 60; // hearts, hunger, armor
        if (!dev.zmuzy.sbc.ui.hud.SkillElement.hidesXpBar()) return sh - 36; // xp bar + its number
        return sh - 22 - BOTTOM_GAP;
    }

    // how far up the vanilla item name + action bar text must go to not sit on our bars. valid after one layout pass
    public static int vanillaTextLift(int sh) {
        if (!(dev.zmuzy.sbc.core.GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)) return 0;
        int top = bottom(Zone.BOTTOM_CENTER);
        if (top <= 0) return 0;
        return Math.max(0, sh - 50 - (top - BOTTOM_GAP));
    }

    // normally only cards with something to show. editing = all that belong here, empty ones get demo content
    private static List<Placed> lastLayout;
    private static int lastW, lastH;
    private static long lastAt;
    private static float lastAll, lastScale;

    // while playing we reuse the last 50ms result. sizes only get measured every 100ms anyway so doing it every
    // frame was the same answer for free lag
    public static List<Placed> layout(int sw, int sh, boolean editing) {
        if (editing || previewOnly != null) return work(sw, sh, editing);
        long now = System.currentTimeMillis();
        if (lastLayout != null && sw == lastW && sh == lastH && now - lastAt < 50 && lastAll == Config.I.hudAll && lastScale == Config.I.hudScale) return lastLayout;
        lastLayout = work(sw, sh, false);
        lastW = sw;
        lastH = sh;
        lastAt = now;
        lastAll = Config.I.hudAll;
        lastScale = Config.I.hudScale;
        return lastLayout;
    }

    private static List<Placed> work(int sw, int sh, boolean editing) {
        List<Placed> out = new ArrayList<>();
        float all = Config.I.hudAll;
        int[] ndL = nudge(Zone.TOP_LEFT), ndR = nudge(Zone.TOP_RIGHT);
        // bottom left stack starts above chat so nothing lands on the chat lines
        int chatH = 0, chatRestH = 0, chatW = 0;
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            boolean focused = mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;
            double chatScale = mc.options.chatScale().get();
            double hv = focused ? mc.options.chatHeightFocused().get() : mc.options.chatHeightUnfocused().get();
            chatH = (int) Math.ceil(net.minecraft.client.gui.components.ChatComponent.getHeight(hv) * chatScale);
            // keep the sides clear of chat at rest, opening chat for a sec shouldnt shuffle the screen
            chatRestH = (int) Math.ceil(net.minecraft.client.gui.components.ChatComponent.getHeight(mc.options.chatHeightUnfocused().get()) * chatScale);
            chatW = (int) Math.ceil(net.minecraft.client.gui.components.ChatComponent.getWidth(mc.options.chatWidth().get()) * chatScale) + 8;
        } catch (Throwable ignored) {
        }
        if (previewChat != null) {
            chatH = chatRestH = previewChat[0];
            chatW = previewChat[1];
        }
        List<HudPacker.Rect> taken = new ArrayList<>();
        taken.add(new HudPacker.Rect(0, sh - 40 - chatRestH, chatW, chatRestH + 40));
        taken.add(new HudPacker.Rect(sw / 2 - 93, sh - 24, 186, 24));
        int blY = sh - 40 - chatH, bcY = hotbarStackBase(sw, sh), brY = sh - 4;
        // next to the hotbar. offhand slot stays free when somethings in it
        int hotbarLeft = sw / 2 - 91 - 3 - (offhandShown() ? 29 : 0), hotbarRight = sw / 2 + 91 + 3;
        boolean anyB = false, anyC = false;
        List<Placed> sides = new ArrayList<>(), corner = new ArrayList<>(), parts = new ArrayList<>();
        for (HudElement e : ELEMENTS) {
            e.demo = editing;
            e.forcedWidth = 0;
            if (previewOnly != null && !previewOnly.contains(e.id)) continue;
            // its lines are in its host card
            if (editing && e.folded()) continue;
            long[] m = measure(e, editing);
            boolean vis = m[1] == 1;
            if (!vis && !editing) continue;
            if (previewOnly == null) {
                if (editing && e.feature() != null && !e.feature().enabled()) continue;
                if (editing && editorGroup != null) {
                    if (!HudGroups.shows(editorGroup, e, vis || e.active())) continue;
                } else if (editing && !editorAllIslands && !vis && !e.active()) continue;
            }
            int wd = (int) m[2], ht = (int) m[3];
            if (wd <= 0) wd = 120;
            if (ht <= 0) ht = 40;
            float sc = (e.zone == Zone.TOP_LEFT ? Config.I.hudScale : 1f) * all * scaleOf(e.id);
            Placed p = new Placed(e.id, e.title, e);
            if ((e.zone == Zone.TOP_LEFT || e.zone == Zone.TOP_RIGHT) && !pinned(e.id)) {
                int steady = steadyWidth(e, wd, editing);
                p.held = steady != wd;
                wd = steady;
            }
            p.visible = vis;
            p.scale = sc;
            p.w = Math.max(8, Math.round(wd * sc));
            p.h = Math.max(8, Math.round(ht * sc));
            if (pinned(e.id)) {
                p.pinned = true;
                p.x = Math.max(-p.w + 24, Math.min(sw - 24, Math.round(pinX(e.id))));
                p.y = Math.max(-p.h + 12, Math.min(sh - 12, Math.round(pinY(e.id))));
                taken.add(new HudPacker.Rect(p.x, p.y, p.w, p.h));
            } else if (e.homeAt(sw, sh) != null) {
                if (!editing) continue;
                int[] home = e.homeAt(sw, sh);
                p.x = home[0];
                p.y = home[1];
            } else if (e.partOf() != null) {
                // at home its drawn by its own card, the editor just needs to know where to grab it
                if (!editing) continue;
                parts.add(p);
            } else {
                switch (e.zone) {
                    case TOP_LEFT, TOP_RIGHT -> sides.add(p);
                    case BOTTOM_LEFT -> {
                        blY -= p.h;
                        p.x = 8;
                        p.y = blY;
                        blY -= GAP;
                        anyB = true;
                        taken.add(new HudPacker.Rect(p.x, p.y, p.w, p.h));
                    }
                    case BOTTOM_CENTER -> {
                        // hotbar width, stacking up from it
                        bcY -= p.h;
                        p.x = sw / 2 - p.w / 2;
                        p.y = bcY;
                        bcY -= BOTTOM_GAP;
                        anyC = true;
                        taken.add(new HudPacker.Rect(p.x, p.y, p.w, p.h));
                    }
                    case BOTTOM_RIGHT -> corner.add(p);
                    case HOTBAR_LEFT -> {
                        hotbarLeft -= p.w;
                        p.x = hotbarLeft;
                        p.y = sh - p.h;
                        hotbarLeft -= 3;
                        taken.add(new HudPacker.Rect(p.x, p.y, p.w, p.h));
                    }
                    case HOTBAR_RIGHT -> {
                        if (hotbarRight + p.w > sw - 4) { // no room next to the hotbar on a narrow window
                            corner.add(p);
                            break;
                        }
                        p.x = hotbarRight;
                        p.y = sh - p.h;
                        hotbarRight += p.w + 3;
                        taken.add(new HudPacker.Rect(p.x, p.y, p.w, p.h));
                    }
                }
            }
            out.add(p);
        }
        // free corner like chat on the other side. narrow screen = above whatever is next to the hotbar
        // the right column goes first, if it reaches down this far the corner moves next to it
        int columnH = 4, columnW = 0;
        for (Placed p : sides) {
            if (p.element.zone != Zone.TOP_RIGHT) continue;
            columnH += p.h + HudPacker.GAP;
            columnW = Math.max(columnW, p.w);
        }
        for (Placed p : corner) {
            p.x = sw - p.w - 4;
            int beside = sw - 4 - columnW - HudPacker.GAP - p.w;
            if (columnH > brY - p.h && columnH <= sh && beside > sw / 2 + 95) p.x = beside;
            brY -= p.h;
            for (HudPacker.Rect r : taken) if (r.hits(p.x, brY, p.w, p.h)) brY = Math.min(brY, r.y() - HudPacker.GAP - p.h);
            p.y = brY;
            brY -= HudPacker.GAP;
            taken.add(new HudPacker.Rect(p.x, p.y, p.w, p.h));
        }
        for (Placed p : parts) {
            for (Placed o : out) {
                if (o.element != p.element.partOf()) continue;
                int[] at = p.element.partAt();
                p.x = o.x + Math.round(at[0] * o.scale);
                p.y = o.y + Math.round(at[1] * o.scale);
                break;
            }
        }
        int bottomL = 0, bottomR = 0;
        if (!sides.isEmpty()) {
            String size = sw + "x" + sh + " " + all + " " + dev.zmuzy.sbc.core.GameState.island;
            if (!size.equals(columnsFor)) {
                columnsFor = size;
                COLUMNS.clear();
            }
            // right sides own cards first, the ones spilling over from the left go under them
            sides.sort(Comparator.comparing((Placed p) -> p.element.zone != Zone.TOP_RIGHT));
            boolean mayShrink = !editing || previewShrink;
            int gui = Math.max(1, guiScale());
            float top = 0;
            for (Placed p : sides) top = Math.max(top, p.scale * gui);
            for (float limit = top; ; limit = nextLimit(limit)) {
                HudPacker packer = new HudPacker(sw, sh, Config.I.hudX - 2 + ndL[0], 4 + ndL[1], sw - 4 + ndR[0], 4 + ndR[1], taken);
                List<HudPacker.Box> boxes = new ArrayList<>();
                for (Placed p : sides) {
                    float k = Math.min(p.scale, limit / gui) / p.scale;
                    boxes.add(new HudPacker.Box(p.id, p.element.zone == Zone.TOP_RIGHT, Math.round(p.w * k), Math.round(p.h * k), p.element.stretchable()));
                }
                boolean fits = packer.pack(boxes, editing || limit < top ? null : COLUMNS);
                if (!fits && mayShrink && limit > 1f) continue;
                for (int i = 0; i < sides.size(); i++) {
                    Placed p = sides.get(i);
                    HudPacker.Box b = boxes.get(i);
                    p.x = b.x;
                    p.y = b.y;
                    p.w = b.w;
                    p.h = b.h;
                    p.scale = Math.min(p.scale, limit / gui);
                    if (b.stretched || p.held) p.element.forcedWidth = Math.round(b.w / p.scale);
                }
                bottomL = packer.bottom(false);
                bottomR = packer.bottom(true);
                break;
            }
        }
        BOTTOM.clear();
        if (bottomL > 0) BOTTOM.put(Zone.TOP_LEFT, bottomL);
        if (bottomR > 0) BOTTOM.put(Zone.TOP_RIGHT, bottomR);
        if (anyB) BOTTOM.put(Zone.BOTTOM_LEFT, blY + GAP);
        if (anyC) BOTTOM.put(Zone.BOTTOM_CENTER, bcY + BOTTOM_GAP);
        return out;
    }

    public static void render(Ui ui, GuiGraphicsExtractor g, int sw) {
        render(ui, g, sw, g.guiHeight());
    }

    public static void render(Ui ui, GuiGraphicsExtractor g, int sw, int sh) {
        render(ui, g, sw, sh, false);
    }

    // cards that belong inside a menu (only drawn over container screens)
    public static final java.util.Set<String> MENU_ELEMENTS = java.util.Set.of("hoppity_cf_stats", "hoppity_collection", "stray_tracker", "cf_stray_timer", "cf_reminder");

    public static void render(Ui ui, GuiGraphicsExtractor g, int sw, int sh, boolean editing) {
        render(ui, g, sw, sh, editing, false);
    }

    private static boolean offhandShown() {
        try {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            return player != null && !player.getOffhandItem().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    // x, y, when it showed up, when it was last drawn
    private static final Map<String, double[]> MOTION = new HashMap<>();
    private static final double APPEAR_MS = 180, EASE_MS = 70, COME_BACK_MS = 3000, SLIDE = 10;

    // index 2 = how visible
    private static float[] motion(Placed p) {
        long now = System.currentTimeMillis();
        double[] m = MOTION.get(p.id);
        if (m == null || now - m[3] > COME_BACK_MS) {
            // just showed up, slides in from its own side
            Zone z = p.element.zone;
            double dx = z == Zone.TOP_LEFT ? -SLIDE : z == Zone.TOP_RIGHT || z == Zone.BOTTOM_RIGHT ? SLIDE : 0;
            double dy = z == Zone.BOTTOM_CENTER || z == Zone.HOTBAR_LEFT || z == Zone.HOTBAR_RIGHT ? SLIDE / 2 : 0;
            m = new double[]{p.x + dx, p.y + dy, now, now};
            MOTION.put(p.id, m);
        }
        double k = 1 - Math.exp(-(now - m[3]) / EASE_MS);
        m[0] += (p.x - m[0]) * k;
        m[1] += (p.y - m[1]) * k;
        if (Math.abs(p.x - m[0]) < 0.5) m[0] = p.x;
        if (Math.abs(p.y - m[1]) < 0.5) m[1] = p.y;
        m[3] = now;
        return new float[]{(float) m[0], (float) m[1], (float) Math.min(1, (now - m[2]) / APPEAR_MS)};
    }

    // a card slid or faded this frame, so draw the hud fresh next frame instead of replaying
    public static boolean moving;

    public static void render(Ui ui, GuiGraphicsExtractor g, int sw, int sh, boolean editing, boolean menuOnly) {
        moving = false;
        for (Placed p : layout(sw, sh, editing)) {
            HudElement e = p.element;
            if (e == null) continue;
            boolean menuEl = MENU_ELEMENTS.contains(e.id);
            if (!editing && (menuOnly ? !menuEl && !Config.I.hudInInventory : menuEl)) continue;
            // each card on its own frame layer. placing a piece means checking every piece in its layer
            // one layer for all cards = every rect checked against all the others. cards never overlap so it looks the same
            g.nextStratum();
            ui.newLayer();
            float[] at = editing || p.pinned || !Config.I.hudAnimations ? new float[]{p.x, p.y, 1f} : motion(p);
            if (at[2] < 1f || at[0] != p.x || at[1] != p.y) moving = true;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(at[0], at[1]);
            pose.scale(p.scale, p.scale);
            ui.resetText();
            ui.opacity = at[2];
            long t0 = dev.zmuzy.sbc.core.Perf.begin();
            Feature f = e.feature();
            if (f != null) Features.guard(f, () -> e.drawWhole(ui, g, 0, 0));
            else {
                try {
                    e.drawWhole(ui, g, 0, 0);
                } catch (Throwable t) {
                    dev.zmuzy.sbc.SbcMod.LOG.warn("HUD element {} failed", e.id, t);
                }
            }
            if (dev.zmuzy.sbc.core.Perf.enabled) dev.zmuzy.sbc.core.Perf.end("hud:" + e.id, t0);
            ui.opacity = 1f;
            pose.popMatrix();
        }
    }
}
