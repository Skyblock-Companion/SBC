// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.menus;

import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.item.tooltip.StatIcons;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// skyhanni reforge overlay as a side panel: reforges + stones in 2 lists, stat chips sort them
final class ReforgePanel implements SideHelper.Content {
    private static final int ROW = 12;

    @Override
    public String title() {
        return "Reforge Helper";
    }

    @Override
    public ItemStack icon() {
        return LegacyItems.stack("anvil");
    }

    @Override
    public int accent() {
        return 0xFFFFB454;
    }

    private static boolean hex() {
        MenuView v = MenuMarks.current();
        return v != null && ReforgeHelper.isHex(v.title);
    }

    @Override
    public String[] tabs() {
        boolean hex = hex();
        int reforges = ReforgeHelper.options(false, hex).size(), stones = ReforgeHelper.options(true, hex).size();
        return new String[]{"Reforges " + reforges, "Stones " + stones};
    }

    @Override
    public void refresh(AbstractContainerScreen<?> screen) {
        MenuMarks.current();
    }

    private static String pretty(String stat) {
        String[] words = stat.toLowerCase(Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder();
        for (String w : words) b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        return b.toString();
    }

    private static String number(double v) {
        String s = v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
        return v >= 0 ? "+" + s : s;
    }

    // "❤" in its color for a stat key
    private static String icon(String stat) {
        StatIcons.Stat s = StatIcons.of(pretty(stat));
        return s == null ? "§7" + pretty(stat) : s.icon();
    }

    private static int iconColor(String stat) {
        StatIcons.Stat s = StatIcons.of(pretty(stat));
        return s == null ? Modern.LABEL : s.color();
    }

    @Override
    public int summary(Ui ui, int x, int y, int w) {
        ReforgeHelper.Reforge current = ReforgeHelper.current;
        if (ReforgeHelper.itemId.isEmpty()) {
            ui.textShadow(ui.ellipsize("Put an item in the menu.", w), x, y + 2, Modern.DIM);
            return 14;
        }
        int half = (w - 3) / 2;
        SideHelper.tile(ui, x, y, half, "Now", current == null ? "None" : current.name, "", 0xFFFFD166);
        SideHelper.tile(ui, x + half + 3, y, w - half - 3, "Chosen", ReforgeHelper.chosen == null ? "None" : ReforgeHelper.chosen.name, "", 0xFF7DD3FC);
        int cy = y + 26, cx = x;
        // sort chips: D = default order, then every stat these reforges give
        cx += chip(ui, cx, cy, "D", Modern.LABEL, null, "Default order");
        for (String stat : ReforgeHelper.statTypes()) {
            String icon = icon(stat);
            int cw = ui.width(icon) + 6;
            if (cx + cw > x + w) {
                cx = x;
                cy += 13;
            }
            cx += chip(ui, cx, cy, icon, iconColor(stat), stat, pretty(stat));
        }
        return cy + 13 - y;
    }

    private static int chip(Ui ui, int x, int y, String text, int color, String stat, String tip) {
        int w = ui.width(text) + 6;
        boolean on = java.util.Objects.equals(ReforgeHelper.sortBy, stat);
        ui.rect(x, y, w, 11, on ? 0x66FFFFFF : 0x44000000);
        ui.textShadow(text, x + 3, y + 2, color);
        if (ui.hover(x, y, w, 11)) ui.tooltip("§6Sort by", "§f" + tip, on ? "" : "§eClick to apply");
        ui.click(x, y, w, 11, () -> ReforgeHelper.sort(stat));
        return w + 2;
    }

    private static int nameColor(ReforgeHelper.Reforge r) {
        if (ReforgeHelper.current == r) return 0xFFFFAA00;
        if (ReforgeHelper.chosen == r) return 0xFF00AAAA;
        return r.isStone() ? 0xFF5555FF : Modern.LABEL;
    }

    private static List<String> tip(ReforgeHelper.Reforge r) {
        String rarity = ReforgeHelper.rarity;
        List<String> out = new ArrayList<>();
        out.add("§6" + r.name);
        boolean diff = ReforgeHelper.current != null && ReforgeHelper.current != r && MenuMarks.on("reforge_helper", "reforge_helper.show_diff");
        Map<String, Double> stats = r.stats.get(rarity);
        if (stats != null) {
            for (Map.Entry<String, Double> s : stats.entrySet()) {
                String line = "§9" + number(s.getValue());
                if (diff) {
                    double d = s.getValue() - ReforgeHelper.current.stat(rarity, s.getKey());
                    line += (d < 0 ? " §c" : " §a") + number(d);
                }
                out.add(line + " §7" + icon(s.getKey()) + " " + pretty(s.getKey()));
            }
        }
        if (diff && ReforgeHelper.current.ability.get(rarity) != null) {
            out.add("§cRemoves Effect:");
            out.add("§7" + ReforgeHelper.current.ability.get(rarity));
        }
        if (r.ability.get(rarity) != null) {
            out.add(ReforgeHelper.current == r ? "§aEffect:" : "§aAdds Effect:");
            out.add("§7" + r.ability.get(rarity));
        }
        out.add("");
        out.add(ReforgeHelper.current == r ? "§3Currently applied" : ReforgeHelper.chosen == r ? "§eClick to clear" : "§eClick to choose");
        return out;
    }

    // stat the row shows: the sorted one, or the reforges biggest
    private static String briefStat(ReforgeHelper.Reforge r) {
        Map<String, Double> stats = r.stats.get(ReforgeHelper.rarity);
        if (stats == null || stats.isEmpty()) return null;
        String key = ReforgeHelper.sortBy;
        if (key == null || !stats.containsKey(key)) {
            double best = -Double.MAX_VALUE;
            for (Map.Entry<String, Double> s : stats.entrySet()) {
                if (s.getValue() > best) {
                    best = s.getValue();
                    key = s.getKey();
                }
            }
        }
        return key;
    }

    @Override
    public int list(Ui ui, int tab, int x, int y, int w) {
        boolean hex = hex();
        List<ReforgeHelper.Reforge> rows = ReforgeHelper.options(tab == 1, hex);
        int y0 = y;
        ReforgeHelper.Reforge over = null;
        for (ReforgeHelper.Reforge r : rows) {
            boolean hv = ui.hover(x, y, w, ROW);
            if (hv) {
                over = r;
                ui.rect(x, y, w, ROW, 0x22FFFFFF);
                List<String> tip = tip(r);
                ui.tooltip(tip.toArray(new String[0]));
            }
            ui.textShadow(ui.ellipsize(r.name, w - 40), x + 2, y + 2, nameColor(r));
            String stat = briefStat(r);
            if (stat != null) {
                String icon = icon(stat), value = number(r.stat(ReforgeHelper.rarity, stat));
                int iw = ui.width(icon), vw = ui.width(value);
                ui.textShadow(icon, x + w - 2 - iw, y + 2, iconColor(stat));
                ui.textShadow(value, x + w - 3 - iw - vw, y + 2, Modern.INK);
            }
            ui.click(x, y, w, ROW, () -> ReforgeHelper.choose(r));
            y += ROW;
        }
        ReforgeHelper.hover(over);
        if (rows.isEmpty()) y += SideHelper.emptyText(ui, ReforgeHelper.itemId.isEmpty() ? "No item yet." : tab == 1 && !hex ? "Stones are for the Hex." : "No reforge fits this item.", x, y, w);
        return y - y0;
    }

    static Set<String> statsForTest() {
        return ReforgeHelper.statTypes();
    }
}
