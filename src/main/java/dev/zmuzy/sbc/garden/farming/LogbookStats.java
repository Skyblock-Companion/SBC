// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni LogBookStats: next to the visitor's logbook, how many visitors came, accepted, denied, summed
// over the pages you opened
public final class LogbookStats {
    public static final String ID = "garden_logbook";
    private static final Map<String, long[]> LOGGED = new HashMap<>();
    private static final Panel PANEL = new Panel();
    private static String lastProfile = "";

    private LogbookStats() {}

    private static long number(String s) {
        return Long.parseLong(s.replace(",", "").replace(".", ""));
    }

    // one visitor page into the totals
    static void read(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty() || it.clean.isEmpty()) continue;
            Matcher visited = it.firstClean(Patterns.get("SH.garden.inventory.logbook.visited.colorless"));
            if (visited == null) continue;
            Matcher accepted = it.firstClean(Patterns.get("SH.garden.inventory.logbook.accepted.colorless"));
            LOGGED.put(it.clean, new long[]{number(visited.group("timesVisited")), accepted == null ? 0 : number(accepted.group("timesAccepted"))});
        }
    }

    static long[] totals() {
        long visited = 0, accepted = 0;
        for (long[] v : LOGGED.values()) {
            visited += v[0];
            accepted += v[1];
        }
        return new long[]{visited, accepted};
    }

    // VisitorApi.getVisitors from the tab's "Visitors: (3)"
    private static int visitingNow() {
        for (String l : GameState.tab) {
            String t = GameState.strip(l).trim();
            if (t.startsWith("Visitors:")) {
                String n = t.replaceAll("\\D", "");
                return n.isEmpty() ? 0 : Integer.parseInt(n);
            }
        }
        return 0;
    }

    private static final class Panel implements SideHelper.Content {
        @Override
        public String title() {
            return "Logbook stats";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("writable_book");
        }

        @Override
        public int accent() {
            return 0xFFFFC857;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Totals"};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            if (!GameState.profileName.equals(lastProfile)) {
                lastProfile = GameState.profileName;
                LOGGED.clear();
            }
            MenuView v = MenuMarks.current();
            if (v != null) read(v);
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            return 0;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            long[] t = totals();
            long denied = t[0] - t[1] - visitingNow();
            Modern.row(ui, "Times visited", "§b" + String.format(Locale.ROOT, "%,d", t[0]), x, y, w, Modern.LABEL, Modern.INK);
            Modern.row(ui, "Times accepted", "§a" + String.format(Locale.ROOT, "%,d", t[1]), x, y + 10, w, Modern.LABEL, Modern.INK);
            Modern.row(ui, "Times denied", "§c" + String.format(Locale.ROOT, "%,d", denied), x, y + 20, w, Modern.LABEL, Modern.INK);
            return 32;
        }
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID) || !GardenPack.garden()) return null;
        return Patterns.get("SH.garden.inventory.logbook.inventory-name").matcher(GameState.strip(s.getTitle().getString()).trim()).matches() ? PANEL : null;
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Logbook stats", "Beside the Visitor's Logbook: how many visitors came, were accepted and were denied, over the pages you opened.", "SkyHanni: Log Book Stats").when(GardenPack::garden));
        SideHelper.add(LogbookStats::panel);
    }
}
