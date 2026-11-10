// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.chat;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.ElementStyle;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// hold a key to read the latest chat on a small plate without opening chat (you keep moving)
// only lines that really reached chat, so filtered stuff stays hidden
public final class Peek {
    private static final int MAX_KEPT = 120, SHOWN = 14, WIDTH = 330, MIN_WIDTH = 200, AGE = 18, ACCENT = 0xFF7FD4FF;
    // where a row starts, after the age column
    private static final int TEXT_X = Modern.PAD + AGE + 4;

    private record Said(Component text, long at) {}

    // a long message takes several rows, only the first shows its age
    private record Row(FormattedCharSequence text, long at, boolean first, boolean odd) {}

    public interface RowPainter {
        void paint(FormattedCharSequence row, int x, int y);
    }

    private static final ArrayDeque<Said> KEPT = new ArrayDeque<>();
    private static int shownMessages;
    private Peek() {}

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return SbcMod.peekKey != null && SbcMod.peekKey.isDown() && mc.gui.screen() == null && mc.player != null;
    }

    private static List<Row> rows() {
        var font = Minecraft.getInstance().font;
        int wrap = WIDTH - TEXT_X - Modern.PAD;
        List<Row> out = new ArrayList<>();
        int message = 0;
        for (var it = KEPT.descendingIterator(); it.hasNext() && out.size() < SHOWN; message++) {
            Said s = it.next();
            List<FormattedCharSequence> split = font.split(s.text(), wrap);
            // blank spacer line says nothing so no age either
            boolean said = !s.text().getString().isBlank();
            for (int i = split.size() - 1; i >= 0 && out.size() < SHOWN; i--) out.add(new Row(split.get(i), s.at(), i == 0 && said, message % 2 == 1));
        }
        shownMessages = message;
        Collections.reverse(out);
        return out;
    }

    public static int width() {
        return width(rows());
    }

    // only as wide as its rows, up to the wrap width
    private static int width(List<Row> rows) {
        var font = Minecraft.getInstance().font;
        int widest = 0;
        for (Row r : rows) widest = Math.max(widest, font.width(r.text()));
        int w = Math.max(MIN_WIDTH, Math.min(WIDTH, TEXT_X + widest + Modern.PAD));
        return w + (w & 1);
    }

    public static int height() {
        int n = Math.max(1, rows().size());
        return Modern.BAND + 2 + n * Modern.ROW + 2;
    }

    public static void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        var font = Minecraft.getInstance().font;
        int color = Ui.alpha(0xFFFFFFFF, ui.opacity);
        paint(ui, x, y, (row, rx, ry) -> g.text(font, row, rx, ry, color, true));
    }

    // rows go through the painter bc a picture without the game draws them its own way
    public static void paint(Ui ui, int x, int y, RowPainter painter) {
        List<Row> rows = rows();
        int w = width(rows), h = Modern.BAND + 2 + Math.max(1, rows.size()) * Modern.ROW + 2;
        Theme.current().panel(ui, x, y, w, h, ElementStyle.of("peek"));
        String tag = rows.isEmpty() ? "" : "§7" + shownMessages + " of " + KEPT.size() + " lines";
        int cy = y + Modern.band(ui, "Chat", LegacyItems.stack("minecraft:spyglass"), tag, ACCENT, x, y, w);
        if (rows.isEmpty()) {
            ui.textShadow("Nothing in chat yet", x + Modern.PAD, cy, Modern.DIM);
            return;
        }
        long now = System.currentTimeMillis();
        for (Row r : rows) {
            if (r.odd()) ui.rect(x + 1, cy - 1, w - 2, Modern.ROW, 0x0AFFFFFF);
            if (r.first()) {
                long age = now - r.at();
                String a = age(age);
                ui.textShadow(a, x + Modern.PAD + AGE - ui.width(a), cy, age < 10_000 ? ACCENT : Modern.DIM);
            }
            painter.paint(r.text(), x + TEXT_X, cy);
            cy += Modern.ROW;
        }
    }

    private static String age(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        if (s < 3600) return s / 60 + "m";
        return s / 3600 + "h";
    }

    // for pictures: lines + how long ago each came
    public static void fillForTest(List<Component> lines, long[] agesMs) {
        KEPT.clear();
        long now = System.currentTimeMillis();
        for (int i = 0; i < lines.size(); i++) KEPT.addLast(new Said(lines.get(i), now - agesMs[i]));
    }

    public static void register() {
        Feature f = Features.add(new Feature("chat_peek", "Chat", "Peek chat", "Hold the Peek Chat key (see Controls) to read the latest chat lines without opening chat.", "SkyHanni: peek chat")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        ChatBus.register(1000, "peek", f, line -> {
            KEPT.addLast(new Said(line.component(), System.currentTimeMillis()));
            while (KEPT.size() > MAX_KEPT) KEPT.removeFirst();
            return ChatBus.PASS;
        });
    }
}
