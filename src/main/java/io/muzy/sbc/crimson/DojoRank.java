// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.crimson;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni DojoRankDisplay: next to the Challenges menu, your rank and score per test, your belt and
// points to the next one (Belts.json)
public final class DojoRank {
    public static final String ID = "crimson_dojo_rank";
    static final Patterns.P NAME = Patterns.p("SH.inventory.dojo.rankdisplay.name"), RANK = Patterns.p("SH.inventory.dojo.rankdisplay.rank");

    // one test: name + colour, rank + colour, score
    record Test(String name, String rank, int score) {}

    // your belt, the next one (null at the top), points to it and how far along
    record Belt(String current, String next, int needed, double progress) {}

    private DojoRank() {}

    // drawDisplay's inner part
    static Test test(String name, List<String> lore) {
        Matcher n = NAME.matcher(name);
        if (!n.matches()) return null;
        for (String l : lore) {
            Matcher r = RANK.matcher(l);
            if (r.matches()) return new Test(n.group("color") + n.group("name"), r.group("rank"), Integer.parseInt(r.group("score")));
        }
        return null;
    }

    // currentBelt and nextBelt. skyhanni's next belt wraps to the first at the top, which then needs no points
    static Belt belt(Map<String, Integer> belts, int total) {
        String current = null, next = null;
        int from = 0, to = 0;
        for (var e : belts.entrySet()) {
            if (total >= e.getValue()) {
                current = e.getKey();
                from = e.getValue();
            } else {
                next = e.getKey();
                to = e.getValue();
                break;
            }
        }
        if (current == null) current = belts.isEmpty() ? "" : belts.keySet().iterator().next();
        if (next == null) return new Belt(current, null, 0, 1);
        return new Belt(current, next, to - total, to == from ? 1 : (total - from) / (double) (to - from));
    }

    private static Map<String, Integer> beltData() {
        Map<String, Integer> out = new LinkedHashMap<>();
        var o = Repo.constant("Belts.json");
        if (o == null || !o.has("belts")) return out;
        for (var e : o.getAsJsonObject("belts").entrySet()) out.put(e.getKey(), e.getValue().getAsInt());
        return out;
    }

    private static final class Panel implements SideHelper.Content {
        private final List<Test> tests = new ArrayList<>();
        private Map<String, Integer> belts;
        private int total;

        @Override public String title() {
            return "Dojo rank";
        }

        @Override public ItemStack icon() {
            return LegacyItems.stack("iron_sword");
        }

        @Override public int accent() {
            return 0xFFFFC857;
        }

        @Override public String[] tabs() {
            return new String[]{"Tests"};
        }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            if (belts == null || belts.isEmpty()) belts = beltData();
            tests.clear();
            total = 0;
            var mc = Minecraft.getInstance();
            for (Slot s : screen.getMenu().slots) {
                if (s.container == mc.player.getInventory() || s.getItem().isEmpty()) continue;
                Test t = test(LegacyText.name(s.getItem()), LegacyText.lore(s.getItem()));
                if (t == null) continue;
                tests.add(t);
                total += t.score();
            }
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            if (belts == null || belts.isEmpty()) return SideHelper.emptyText(ui, "No belt data yet.", x, y, w);
            Belt b = belt(belts, total);
            SideHelper.tile(ui, x, y, w, "Total score", String.format(Locale.ROOT, "%,d", total), b.current(), Modern.GOLD);
            if (b.next() == null) return 28;
            Modern.row(ui, b.next(), String.format(Locale.ROOT, "§6%,d §7more", b.needed()), x, y + 28, w, Modern.LABEL, Modern.INK);
            Modern.meter(ui, x, y + 38, w, Modern.METER, b.progress(), Modern.GOLD);
            return 44;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            if (tests.isEmpty()) return SideHelper.emptyText(ui, "No tests here.", x, y, w);
            int at = y;
            for (Test t : tests) {
                Modern.row(ui, t.name(), t.rank() + " §7(" + (t.score() < 100 ? "§c" : "§a") + String.format(Locale.ROOT, "%,d", t.score()) + "§7)", x, at, w, Modern.LABEL, Modern.INK);
                at += 10;
            }
            return at - y;
        }
    }

    private static final Panel PANEL = new Panel();

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID) || !Crimson.crimson() || !GameState.area.equals("Dojo")) return null;
        return s.getTitle().getString().equals("Challenges") ? PANEL : null;
    }

    public static void register() {
        Features.add(new Feature(ID, "Crimson Isle", "Dojo rank", "Beside the Challenges menu: your rank and score in each test, your total, your belt and the points to the next one.", "SkyHanni: Dojo Rank Display")
                .when(Crimson::crimson));
        SideHelper.add(DojoRank::panel);
    }
}
