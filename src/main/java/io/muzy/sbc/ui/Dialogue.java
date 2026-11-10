// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// npc talk as a dialogue box instead of chat. "[NPC] Name: text" goes in the box
// hypixels clickable answers become numbered options, mouse or 1-9, runs the same command the chat button would
public final class Dialogue {
    public record Option(String label, String command) {}

    private static final io.muzy.sbc.core.Patterns.P NPC_LINE = io.muzy.sbc.core.Patterns.p("Dialogue.NPC_LINE");
    private static final int KEEP = 3;

    static String npc = "";
    // lines this npc said this talk, including the ones that scrolled out
    static int spoken;
    static final List<String> lines = new ArrayList<>();
    static long lineAt, lastLineAt;
    static List<Option> options = List.of();
    static long optionsAt;
    // closed with esc, dont pop up again for the same question
    static boolean dismissed;
    // the npc in front of you. its a real entity so we can draw it as the portrait
    static net.minecraft.world.entity.LivingEntity speaker;
    private Dialogue() {}

    private static boolean enabled() {
        return Config.I.npcDialogue && GameState.inSkyBlock;
    }

    // true = npc line or answer, keep it out of chat
    public static boolean onMessage(Component message) {
        if (!enabled()) return false;
        long now = System.currentTimeMillis();
        String plain = GameState.strip(message.getString()).trim();
        Matcher m = NPC_LINE.matcher(plain);
        if (m.find()) {
            String who = m.group(1).trim(), text = m.group(2).trim();
            if (!who.equals(npc) || now - lastLineAt > 25_000) {
                lines.clear();
                spoken = 0;
                options = List.of();
            }
            if (!who.equals(npc) || speaker == null || speaker.isRemoved()) speaker = findSpeaker();
            npc = who;
            lines.add(text);
            spoken++;
            while (lines.size() > KEEP) lines.remove(0);
            lineAt = now;
            lastLineAt = now;
            return Config.I.hideNpcChat;
        }
        List<Option> found = new ArrayList<>();
        walk(message, found);
        if (!found.isEmpty()) {
            options = found;
            optionsAt = now;
            dismissed = false;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                var s = mc.gui.screen();
                if (s == null || s instanceof net.minecraft.client.gui.screens.ChatScreen) mc.gui.setScreen(new DialogueScreen());
            });
            return Config.I.hideNpcChat;
        }
        // "Select an option:" has no button of its own
        return Config.I.hideNpcChat && plain.startsWith("Select an option") && now - lastLineAt < 20_000;
    }

    // what youre looking at, else the closest person-ish entity a few blocks away
    private static net.minecraft.world.entity.LivingEntity findSpeaker() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        if (mc.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity le && !(le instanceof net.minecraft.world.entity.decoration.ArmorStand) && le != mc.player) return le;
        net.minecraft.world.entity.LivingEntity best = null;
        double bd = 100;
        for (var e : mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(8))) {
            if (e == mc.player || e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
            double d = e.distanceToSqr(mc.player) - (e instanceof net.minecraft.client.player.AbstractClientPlayer ? 6 : 0);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    private static void walk(Component c, List<Option> out) {
        ClickEvent ce = c.getStyle().getClickEvent();
        if (ce instanceof ClickEvent.RunCommand rc && rc.command().contains("selectnpcoption")) {
            String label = GameState.strip(c.getString()).trim();
            if (label.startsWith("[") && label.endsWith("]") && label.length() > 2) label = label.substring(1, label.length() - 1).trim();
            if (!label.isEmpty()) out.add(new Option(label, rc.command()));
            return;
        }
        for (Component s : c.getSiblings()) walk(s, out);
    }

    public static boolean hasOptions() {
        return !options.isEmpty() && System.currentTimeMillis() - optionsAt < 60_000;
    }

    public static boolean visible() {
        if (!enabled()) return false;
        if (hasOptions() && !dismissed) return true;
        if (lines.isEmpty()) return false;
        String last = lines.get(lines.size() - 1);
        return System.currentTimeMillis() - lastLineAt < lifeOf(last);
    }

    private static long lifeOf(String line) {
        return 4500 + line.length() * 55L;
    }

    public static void skip() {
        lineAt = 0;
    }

    public static void choose(int index) {
        if (index < 0 || index >= options.size()) return;
        String cmd = options.get(index).command();
        options = List.of();
        var mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    public static void dismiss() {
        dismissed = true;
        lines.clear();
        spoken = 0;
    }

    // --- measuring: box is as wide as the text ---

    // word wrap to maxW, a word too long for a row gets cut
    static List<String> wrap(Ui ui, String s, int maxW) {
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String word : s.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (ui.width(test) <= maxW) {
                cur = test;
                continue;
            }
            if (!cur.isEmpty()) out.add(cur);
            cur = word;
            while (cur.length() > 1 && ui.width(cur) > maxW) {
                int n = cur.length() - 1;
                while (n > 1 && ui.width(cur.substring(0, n)) > maxW) n--;
                out.add(cur.substring(0, n));
                cur = cur.substring(n);
            }
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    // narrowest width with the same row count, so text wraps into even rows and not 1 full row + a lonely word
    static int narrowest(Ui ui, String s, int maxW) {
        int whole = ui.width(s);
        if (whole <= maxW) return whole;
        int rows = wrap(ui, s, maxW).size();
        int lo = Math.max(1, whole / rows), hi = maxW;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (wrap(ui, s, mid).size() <= rows) hi = mid;
            else lo = mid + 1;
        }
        int widest = 0;
        for (String row : wrap(ui, s, lo)) widest = Math.max(widest, ui.width(row));
        return widest;
    }

    // portrait column, then the text column + margins
    private static final int WELL = 44, COLUMN = WELL + 4, INSET = 4, MARGIN = Modern.PAD, CHROME = COLUMN + INSET + MARGIN;
    // short line shouldnt look cramped. past max it wraps, ~55 chars a row
    static final int MIN_TEXT = 88, MAX_TEXT = 300;
    private static final int MIN_HEIGHT = 58, MAX_EARLIER = 6, MARK = 10, KEY = 14, ANSWER_TEXT = KEY + 4, ANSWER_GAP = 2;
    static final int PORTRAIT = 92;
    private static final String ASK = "Select an option";
    private static final int ACCENT = Modern.GOLD;

    // box size for what it says now + where the text breaks
    record Fit(int textW, int width, int height, List<List<String>> earlier, List<String> current, List<List<String>> answers, boolean keys) {
        int textHeight() {
            int h = current.size() * Modern.ROW;
            for (List<String> rows : earlier) h += rows.size() * Modern.ROW + 2;
            return h;
        }
    }

    private static int answerHeight(int rows) {
        return rows * Modern.ROW + 4;
    }

    private static String keysFor(int answers) {
        return answers <= 1 ? "1" : "1-" + Math.min(9, answers);
    }

    private static int keysWidth(Ui ui, int answers) {
        return ui.width(keysFor(answers)) + 3 + ui.width("pick") + 8 + ui.width("Esc") + 3 + ui.width("close") + 8 + ui.width("Space") + 3 + ui.width("skip");
    }

    static Fit fit(Ui ui, String name, String tag, List<String> said, List<Option> answers, int screenW, boolean keys) {
        int max = Math.max(60, Math.min(MAX_TEXT, screenW - 24 - CHROME));
        int need = Math.max(MIN_TEXT, ui.width(name) + (tag.isEmpty() ? 0 : ui.width(tag) + 8) + 4);
        for (int i = 0; i < said.size(); i++) {
            int n = narrowest(ui, said.get(i), max);
            // last line keeps room for the "that was all" mark
            need = Math.max(need, i == said.size() - 1 ? n + MARK : n);
        }
        int count = Math.min(9, answers.size());
        int answerMax = max - ANSWER_TEXT - 2;
        for (int i = 0; i < count; i++) need = Math.max(need, narrowest(ui, answers.get(i).label(), answerMax) + ANSWER_TEXT + 2);
        if (count > 0) need = Math.max(need, ui.width(ASK) + 28);
        if (keys) need = Math.max(need, keysWidth(ui, count));
        int textW = Math.min(max, need);

        List<List<String>> earlier = new ArrayList<>();
        int earlierRows = 0;
        for (int i = 0; i < said.size() - 1; i++) {
            List<String> rows = new ArrayList<>(wrap(ui, said.get(i), textW));
            earlierRows += rows.size();
            earlier.add(rows);
        }
        while (earlierRows > MAX_EARLIER && !earlier.isEmpty()) {
            List<String> first = earlier.get(0);
            first.remove(0);
            earlierRows--;
            if (first.isEmpty()) earlier.remove(0);
        }
        List<String> current = said.isEmpty() ? List.of() : wrap(ui, said.get(said.size() - 1), textW);
        List<List<String>> answerRows = new ArrayList<>();
        int answerH = 0;
        for (int i = 0; i < count; i++) {
            List<String> rows = wrap(ui, answers.get(i).label(), textW - ANSWER_TEXT - 2);
            answerRows.add(rows);
            answerH += answerHeight(rows.size()) + (i > 0 ? ANSWER_GAP : 0);
        }
        int h = Modern.BAND + 2;
        for (List<String> rows : earlier) h += rows.size() * Modern.ROW + 2;
        h += current.size() * Modern.ROW;
        if (count > 0) h += 4 + Modern.ROW + 1 + answerH;
        if (keys) h += 3 + Modern.ROW;
        h += 4;
        h = Math.max(MIN_HEIGHT, h + (h & 1));
        return new Fit(textW, textW + CHROME, h, earlier, current, answerRows, keys);
    }

    private static String fitKey = "";
    private static Fit fitted;

    // only changes when the words do, so its worked out once per text
    static Fit current(Ui ui, int W, boolean interactive) {
        List<Option> shown = shown(interactive);
        String k = wordsKey(W, shown);
        if (!k.equals(fitKey) || fitted == null) {
            fitKey = k;
            fitted = fit(ui, npc, tag(), lines, shown, W, !shown.isEmpty());
        }
        return fitted;
    }

    private static List<Option> shown(boolean interactive) {
        return interactive && hasOptions() ? options : List.of();
    }

    // everything the box size depends on
    private static String wordsKey(int W, List<Option> shown) {
        StringBuilder key = new StringBuilder().append(W).append('|').append(npc).append('|').append(tag());
        for (String l : lines) key.append('\n').append(l);
        for (Option o : shown) key.append('\t').append(o.label());
        return key.toString();
    }

    private static String tag() {
        return spoken > 1 ? "§7Line " + spoken : "";
    }

    // skip() sets the start to 0 so this can be way past an int
    static int typedChars(long sinceLine, int length) {
        return (int) Math.min(length, Math.max(0, sinceLine / 16));
    }

    // --- the classic box ---

    // off for pictures, nothing to animate there
    static boolean animate = true;
    private static float boxW;
    private static long lastFrame, openedAt;

    // card width on its way to what the words need. grows at once (rows are already wrapped for the new width)
    // but shrinks slowly
    static float nextWidth(float was, int target, long frameMs) {
        if (target >= was) return target;
        float w = was + (target - was) * Math.min(1f, frameMs * 0.02f);
        return w - target < 1f ? target : w;
    }

    private static void frame(Ui ui, int x, int y, int w, int h, int c) {
        ui.rect(x + 1, y, w - 2, 1, c);
        ui.rect(x + 1, y + h - 1, w - 2, 1, c);
        ui.rect(x, y + 1, 1, h - 2, c);
        ui.rect(x + w - 1, y + 1, 1, h - 2, c);
    }

    private static void drawModern(Ui ui, int W, int H, boolean interactive) {
        long now = System.currentTimeMillis();
        Fit f = current(ui, W, interactive);
        boolean opening = !animate || now - lastFrame > 1000;
        if (opening) {
            openedAt = now;
            boxW = f.width();
        } else {
            boxW = nextWidth(boxW, f.width(), now - lastFrame);
        }
        lastFrame = now;
        float in = animate ? Math.min(1f, (now - openedAt) / 120f) : 1f;
        int w = Math.round(boxW), h = f.height();
        int x = (W - w) / 2, y = Math.max(4, H - 96 - h) + Math.round((1 - in) * 5);
        float was = ui.opacity;
        ui.opacity = was * in;

        ui.shadow(x, y, w, h, 0);
        Modern.card(ui, x, y, w, h, Math.max(0.86f, Config.I.classicAlpha));
        frame(ui, x, y, w, h, Ui.alpha(ACCENT, 0.45f));
        portrait(ui, x + 2, y + 2, WELL, h - 4);
        int colX = x + COLUMN, tx = colX + INSET, textW = f.textW();
        Modern.band(ui, npc, null, tag(), ACCENT, colX, y, x + w - colX);

        String cur = lines.isEmpty() ? "" : lines.get(lines.size() - 1);
        int typed = typedChars(now - lineAt, cur.length());
        boolean done = typed >= cur.length();
        boolean answering = !f.answers().isEmpty();

        int cy = y + Modern.BAND + 2;
        if (!answering) cy += Math.max(0, (h - Modern.BAND - 2 - 4 - f.textHeight()) / 2);
        for (List<String> rows : f.earlier()) {
            for (String row : rows) {
                ui.textShadow(row, tx, cy, Modern.DIM);
                cy += Modern.ROW;
            }
            cy += 2;
        }
        // rows come from the whole line, so a word doesnt jump rows while its typed
        int left = typed;
        String lastRow = "";
        for (String row : f.current()) {
            // a word cut across rows has no space to count, so the count alone can come up short
            int n = done ? row.length() : Math.min(left, row.length());
            if (n > 0) ui.textShadow(row.substring(0, n), tx, cy, Modern.INK);
            left -= n + 1;
            lastRow = row;
            cy += Modern.ROW;
        }
        if (done && !hasOptions() && (now / 500) % 2 == 0) {
            int mx = tx + ui.width(lastRow) + 3;
            if (mx + 7 <= x + w - 3) ui.textShadow("▼", mx, cy - Modern.ROW, ACCENT);
        }

        if (answering) {
            cy += 4;
            String secs = Math.max(0, (60_000 - (now - optionsAt)) / 1000) + "s";
            Modern.section(ui, ASK, tx, cy, textW - ui.width(secs) - 3, ACCENT);
            ui.textShadow(secs, tx + textW - ui.width(secs), cy, Modern.DIM);
            cy += Modern.ROW + 1;
            answers(ui, f, tx, cy, textW);
        }
        if (f.keys()) {
            int kx = key(ui, keysFor(f.answers().size()), "pick", tx, y + h - 4 - Modern.ROW);
            kx = key(ui, "Esc", "close", kx, y + h - 4 - Modern.ROW);
            if (!done) key(ui, "Space", "skip", kx, y + h - 4 - Modern.ROW);
        }

        // time left: answers while they wait, else till the line goes away
        double life = hasOptions() ? 1 - (now - optionsAt) / 60_000.0 : 1 - (double) (now - lastLineAt) / lifeOf(cur);
        int fill = (int) Math.round((w - 2) * Math.max(0, Math.min(1, life)));
        ui.rect(x + 1, y + h - 2, fill, 1, Ui.alpha(ACCENT, 0.7f));
        ui.opacity = was;
    }

    private static int key(Ui ui, String key, String what, int x, int y) {
        ui.textShadow(key, x, y, Modern.LABEL);
        x += ui.width(key) + 3;
        ui.textShadow(what, x, y, Modern.DIM);
        return x + ui.width(what) + 8;
    }

    private static void answers(Ui ui, Fit f, int x, int y, int w) {
        for (int i = 0; i < f.answers().size(); i++) {
            List<String> rows = f.answers().get(i);
            int h = answerHeight(rows.size());
            float hv = ui.hoverAnim("dlg" + i, x, y, w, h);
            ui.rect(x, y, w, h, 0x0CFFFFFF);
            if (hv > 0) ui.rect(x, y, w, h, Ui.alpha(ACCENT, 0.16f * hv));
            ui.rect(x, y, KEY, h, Ui.alpha(ACCENT, 0.20f + 0.30f * hv));
            ui.rect(x, y + h - 1, KEY, 1, Ui.alpha(ACCENT, 0.8f));
            String n = String.valueOf(i + 1);
            ui.textShadow(n, x + (KEY - ui.width(n)) / 2, y + (h - 8) / 2, Ui.lerpColor(Modern.lighten(ACCENT, 0.35f), 0xFFFFFFFF, hv));
            int ty = y + 3;
            for (String row : rows) {
                ui.textShadow(row, x + ANSWER_TEXT, ty, Ui.lerpColor(Modern.INK, ACCENT, hv));
                ty += Modern.ROW;
            }
            final int idx = i;
            ui.click(x, y, w, h, () -> choose(idx));
            y += h + ANSWER_GAP;
        }
    }

    // speaker in a lit well, no speaker = first letter of the name
    private static void portrait(Ui ui, int x, int y, int w, int h) {
        DripKit.edge(ui, x - 1, y - 1, w + 2, h + 2, 0x90000000);
        ui.rect(x, y, w, h, 0x59000000);
        ui.gradV(x, y, w, h, Ui.alpha(ACCENT, 0.22f), Ui.alpha(ACCENT, 0.03f));
        var who = speaker;
        if (who != null && !who.isRemoved()) {
            try {
                // stands on the floor of the well, same look however tall the box is
                int mh = Math.min(h, PORTRAIT), my = y + h - mh;
                float size = Math.min(mh * 0.80f / Math.max(1.0f, who.getBbHeight()), w * 0.62f / Math.max(0.6f, who.getBbWidth()));
                net.minecraft.client.gui.screens.inventory.InventoryScreen.extractEntityInInventoryFollowsMouse(ui.g, x, my, x + w, y + h,
                        Math.max(10, Math.round(size)), 0.0625f, x + w / 2f + 18, my + mh / 2f - 8, who);
                return;
            } catch (Throwable ignored) {
                speaker = null;
            }
        }
        String initial = npc.isEmpty() ? "?" : npc.substring(0, 1).toUpperCase(Locale.ROOT);
        ui.textScaled(initial, x + w / 2f - ui.width(initial) * 1.5f, y + h / 2f - 12, 3f, Modern.lighten(ACCENT, 0.2f), true);
    }

    // dialogue near the bottom. interactive = clickable options, on the hud (no cursor) just the talk box
    public static void draw(Ui ui, int W, int H, boolean interactive) {
        if (!visible()) return;
        ui.resetText();
        drawModern(ui, W, H, interactive);
    }

    // dims the world behind the answers so the box stands out
    static void backdrop(Ui ui, int W, int H) {
        ui.gradV(0, H / 2, W, H / 2, 0x00000000, 0x70000000);
    }


}
