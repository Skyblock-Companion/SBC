// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// slide in cards top right
public final class Toasts {
    // layout gets worked out once per screen width, not every frame
    private static final class Toast {
        final String title, body, icon;
        final int color;
        final long start;
        long life = LIFE;
        int laidOutFor = -1, w, h;
        double progress = -1;
        List<String> titleRows = List.of(), bodyRows = List.of();

        Toast(String title, String body, int color, long start, String icon) {
            this.title = title;
            this.body = body;
            this.color = color;
            this.start = start;
            this.icon = icon;
        }
    }

    private static final List<Toast> list = new ArrayList<>();
    private static final long LIFE = 6000;
    private static final int MIN_W = 120, MAX_W = 240, TILE = 14, TEXT_X = 22, MARGIN = 6, MAX_BODY_ROWS = 5;
    private static final Pattern A_OF_B = Pattern.compile("(?<![\\d.,])(\\d[\\d,]*(?:\\.\\d+)?[kKmMbB]?) of (\\d[\\d,]*(?:\\.\\d+)?[kKmMbB]?)(?![\\d/:])");
    private Toasts() {}

    public static synchronized void push(String title, String body, int color) {
        add(title, body, color, System.currentTimeMillis());
        if (Config.I.sounds) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.35f, 1.6f);
            });
        }
    }

    // card with how long it stays + an item. no item = the words pick one
    public static synchronized void push(String title, String body, int color, long lifeMs, String icon) {
        add(title, body, color, System.currentTimeMillis());
        Toast t = list.get(list.size() - 1);
        t.life = lifeMs;
        if (icon != null && !icon.isBlank()) list.set(list.size() - 1, withIcon(t, icon));
    }

    private static Toast withIcon(Toast t, String icon) {
        Toast out = new Toast(t.title, t.body, t.color, t.start, icon);
        out.life = t.life;
        return out;
    }

    private static void add(String title, String body, int color, long start) {
        String b = body == null ? "" : body;
        list.add(new Toast(title, b, color, start, iconFor(title, b)));
        while (list.size() > 4) list.remove(0);
    }

    // for pictures: a card thats already slid in
    static synchronized void addForTest(String title, String body, int color, long ageMs) {
        add(title, body, color, System.currentTimeMillis() - ageMs);
    }

    static synchronized void clearForTest() {
        list.clear();
    }

    // what a card is about from its words. first match wins so longer phrases go first
    private static final String[][] ICONS = {
            {"pest", "silverfish_spawn_egg"}, {"composter", "composter"}, {"honeyhive", "honeycomb"}, {"phantom", "phantom_membrane"},
            {"reptile", "turtle_egg"}, {"time warp", "clock"}, {"kicked", "barrier"}, {"cake", "cake"}, {"minion", "armor_stand"},
            {"eyedrops", "ender_eye"}, {"piggy", "gold_nugget"}, {"npc sell", "gold_ingot"}, {"low health", "golden_apple"},
            {"shiny orb", "ender_pearl"}, {"sack", "bundle"}, {"protect", "shield"}, {"slot", "tripwire_hook"}, {"too cold", "packed_ice"},
            {"navigating", "compass"}, {"arrived", "compass"}, {"already here", "compass"}, {"no location", "compass"},
            {"new best", "netherite_sword"}, {"slain", "netherite_sword"}, {"slayer", "netherite_sword"}, {"broodmother", "spider_eye"},
            {"dragon", "dragon_head"}, {"inquisitor", "golden_shovel"}, {"primal", "carved_pumpkin"}, {"quick maths", "book"}, {"say in chat", "book"},
            {"wiki", "book"}, {"performance", "redstone"}, {"patterns", "paper"}, {"dump", "paper"}, {"minimap", "filled_map"},
            {"plan", "writable_book"}, {"routine", "writable_book"}, {"task", "writable_book"}, {"next:", "writable_book"},
            {"time's up", "clock"}, {"reset in", "clock"}, {"ready", "clock"}, {"skyblock companion", "nether_star"}, {"you need", "chest"},
            {"chat", "paper"}, {"learned", "knowledge_book"}, {"teaching", "knowledge_book"}, {"hidden", "ender_eye"}, {"edit hud", "item_frame"},
    };

    private static String iconFor(String title, String body) {
        if (body.contains(" catch - ")) return "minecraft:fishing_rod";
        String t = title.toLowerCase(Locale.ROOT);
        for (String[] i : ICONS) if (t.contains(i[0])) return "minecraft:" + i[1];
        return "";
    }

    // random card: a mark that says what kind of news its color is
    private static String markFor(int color) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        if (r > 150 && g < 130 && b < 170 && r > b) return "!";
        if (g > r && g > b) return "✔";
        if (r > 180 && g > 120 && b < 150) return "★";
        if (r > 180 && b > 180) return "★";
        return "i";
    }

    // how far the numbers in the text are: "1,204 / 12.4k", "462,500,000 of 500M sold". -1 = none
    static double progress(String body) {
        Matcher of = A_OF_B.matcher(body);
        return RowText.shape(of.find() ? of.group(1) + "/" + of.group(2) : body, "").progress();
    }

    // x out of y also shows the percent
    static String withPercent(String body) {
        double p = progress(body);
        return p < 0 || body.indexOf('%') >= 0 ? body : body + "  §7" + RowText.percent(p);
    }

    public static synchronized void render(Ui ui, int screenW) {
        render(ui, screenW, 8);
    }

    public static synchronized void render(Ui ui, int screenW, int startY) {
        long now = System.currentTimeMillis();
        list.removeIf(t -> now - t.start > t.life);
        if (list.isEmpty()) return;
        ui.resetText();
        int y = startY;
        for (Toast t : list) {
            long age = now - t.start;
            float in = Math.min(1, age / 250f), out = Math.min(1, Math.max(0, (t.life - age) / 300f));
            float k = Math.min(in, out);
            float ease = 1 - (1 - k) * (1 - k) * (1 - k);
            float life = 1 - (float) age / t.life;
            y += modernCard(ui, t, screenW, y, k, ease, life);
        }
    }

    private static void layOut(Ui ui, Toast t, int screenW) {
        if (t.laidOutFor == screenW) return;
        t.laidOutFor = screenW;
        String said = withPercent(t.body);
        int maxText = Math.max(60, Math.min(MAX_W, screenW - 16) - TEXT_X - MARGIN);
        int textW = Dialogue.narrowest(ui, t.title, maxText);
        if (!said.isEmpty()) textW = Math.max(textW, Dialogue.narrowest(ui, said, maxText));
        t.titleRows = Dialogue.wrap(ui, t.title, textW);
        List<String> body = said.isEmpty() ? List.of() : Dialogue.wrap(ui, said, textW);
        if (body.size() > MAX_BODY_ROWS) {
            // an errors whole stack must not eat the screen
            body = new ArrayList<>(body.subList(0, MAX_BODY_ROWS));
            body.set(MAX_BODY_ROWS - 1, ui.ellipsize(body.get(MAX_BODY_ROWS - 1) + "…", textW));
        }
        t.bodyRows = body;
        t.progress = progress(t.body);
        int w = Math.max(MIN_W, textW + TEXT_X + MARGIN);
        t.w = w + (w & 1);
        t.h = Math.max(20, textHeight(t) + 8);
    }

    // rows + the meter under them when theres a target
    private static int textHeight(Toast t) {
        return (t.titleRows.size() + t.bodyRows.size()) * Modern.ROW + (t.progress >= 0 ? Modern.METER_BLOCK : 0);
    }

    private static int modernCard(Ui ui, Toast t, int screenW, int y, float k, float ease, float life) {
        layOut(ui, t, screenW);
        int w = t.w, h = t.h;
        int x = screenW - 8 - (int) (w * ease) - (int) ((1 - ease) * 12);
        int c = t.color | 0xFF000000;

        float was = ui.opacity;
        ui.opacity = was * k;
        Modern.card(ui, x, y, w, h, Math.max(0.86f, Config.I.classicAlpha));
        ui.gradH(x + 2, y + 1, w * 2 / 3, h - 2, Ui.alpha(c, 0.16f), Ui.alpha(c, 0f));
        Modern.edge(ui, x, y, h, c);

        int tx = x + 4, ty = y + (h - TILE) / 2;
        ui.rect(tx, ty, TILE, TILE, Ui.alpha(c, 0.22f));
        ui.rect(tx, ty + TILE - 1, TILE, 1, Ui.alpha(c, 0.8f));
        if (!t.icon.isEmpty()) {
            // items cant fade so its only there while the card mostly is
            if (k > 0.6f) ui.icon(LegacyItems.stack(t.icon), tx + 1, ty + 1, 0.75f);
        } else {
            String mark = markFor(c);
            ui.textShadow(mark, tx + (TILE - ui.width(mark)) / 2, ty + 3, Modern.lighten(c, 0.35f));
        }

        int cy = y + (h - textHeight(t)) / 2 + 1;
        for (String row : t.titleRows) {
            ui.textShadow(row, x + TEXT_X, cy, Modern.lighten(c, 0.35f));
            cy += Modern.ROW;
        }
        for (String row : t.bodyRows) {
            ui.textShadow(row, x + TEXT_X, cy, Modern.LABEL);
            cy += Modern.ROW;
        }
        if (t.progress >= 0) Modern.meter(ui, x + TEXT_X, cy, w - TEXT_X - MARGIN, Modern.METER, t.progress, c);
        ui.rect(x + 2, y + h - 2, (int) ((w - 3) * life), 1, Ui.alpha(c, 0.7f));
        ui.opacity = was;
        return h + 4;
    }

}
