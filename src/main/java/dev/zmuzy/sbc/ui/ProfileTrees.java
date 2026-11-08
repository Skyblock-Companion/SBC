// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker, SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.game.GameProfile;
import dev.zmuzy.sbc.core.game.HeartTrees;
import dev.zmuzy.sbc.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

// trees tab: hotm + hotf, every perk with its level and what the tree adds up to. from skyblocker MiningPage
// + SkillTreeNodeWidget
final class ProfileTrees {
    private static final int MOUNTAIN = 0xFF4FD8C4, FOREST = 0xFF81C784;
    private static final int CELL = 19, STEP = CELL + 2, TREE_W = HeartTrees.COLUMNS * STEP;
    // under this width the numbers go below the tree, not beside
    private static final int BESIDE_MIN = 118;
    private ProfileTrees() {}

    private record Row(String label, String value, int color) {}

    static int page(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int gap = MenuUi.GAP;
        List<HeartTrees.Node> mountain = HeartTrees.mountain(d), forest = HeartTrees.forest(d);
        List<Row> mountainRows = mountainRows(d, mountain), forestRows = forestRows(d, forest);
        boolean two = w >= 2 * (TREE_W + 12) + gap;
        int col = two ? (w - gap) / 2 : w;
        int hm = height(mountain, mountainRows, col), hf = height(forest, forestRows, two ? w - col - gap : w);
        if (two) hm = hf = Math.max(hm, hf);
        card(ui, x, y, col, hm, "Heart of the Mountain", "iron_pickaxe", MOUNTAIN, d, d.hotm, d.hotmSlot, mountain, mountainRows);
        if (two) {
            card(ui, x + col + gap, y, w - col - gap, hf, "Heart of the Forest", "oak_sapling", FOREST, d, d.hotf, d.hotfSlot, forest, forestRows);
            return hm + gap;
        }
        card(ui, x, y + hm + gap, w, hf, "Heart of the Forest", "oak_sapling", FOREST, d, d.hotf, d.hotfSlot, forest, forestRows);
        return hm + gap + hf + gap;
    }

    private static int treeRows(List<HeartTrees.Node> nodes) {
        int rows = 0;
        for (HeartTrees.Node n : nodes) rows = Math.max(rows, n.y() + 1);
        return rows;
    }

    private static boolean unread(List<HeartTrees.Node> nodes) {
        return nodes.stream().anyMatch(n -> n.state() == HeartTrees.State.UNREAD);
    }

    // rows, legend, and what to open while part of the tree is unread
    private static int notes(List<HeartTrees.Node> nodes, List<Row> rows) {
        return rows.size() * 11 + 4 * 11 + 6 + (unread(nodes) ? 22 : 0);
    }

    private static int height(List<HeartTrees.Node> nodes, List<Row> rows, int w) {
        int tree = treeRows(nodes) * STEP;
        return MenuUi.TOP + (w - TREE_W - 22 >= BESIDE_MIN ? Math.max(tree, notes(nodes, rows) + 22) : tree + 6 + notes(nodes, rows) + 22) + 4;
    }

    private static void card(MenuUi ui, int x, int y, int w, int h, String title, String icon, int accent, Profile.Data d, Profile.Level tier, int slot, List<HeartTrees.Node> nodes, List<Row> rows) {
        int top = ui.card(x, y, w, h, title, icon, slot > 1 ? "Tree " + slot : null, accent);
        int tx = x + 6, ty = y + top;
        for (HeartTrees.Node n : nodes) cell(ui, tx + n.x() * STEP, ty + n.y() * STEP, n, title);
        boolean beside = w - TREE_W - 22 >= BESIDE_MIN;
        int nx = beside ? tx + TREE_W + 8 : tx, ny = beside ? ty : ty + treeRows(nodes) * STEP + 6, nw = beside ? x + w - 6 - nx : w - 12;
        if (tier != null) PagesMore.heroLevel(ui, nx, ny - 1, nw + 6, tier, "Tier ", accent);
        else ui.textScaled("Tier ?", nx + 6, ny - 1, 1.25f, Ui.DIM, true);
        ny += 20;
        for (Row r : rows) {
            ui.row(r.label(), r.value(), nx + 6, ny, nw - 6, r.color());
            ny += 11;
        }
        ny += 4;
        legend(ui, nx + 6, ny, "coal", "Not bought");
        legend(ui, nx + 6, ny + 11, "emerald", "Bought");
        legend(ui, nx + 6, ny + 22, "diamond", "At its maximum");
        legend(ui, nx + 6, ny + 33, "barrier", "Turned off");
        if (unread(nodes)) ProfileFromGame.hintLine(ui, "Open " + title + " and look at every tier.", nx + 6, ny + 46, nw - 6);
    }

    private static void legend(MenuUi ui, int x, int y, String icon, String text) {
        ui.icon(icon, x, y - 1, 0.5f);
        ui.text(text, x + 11, y, Ui.DIM);
    }

    // skyhanni HotxFeatures colors: gray locked, green on, red off, gold maxed
    private static int tint(HeartTrees.State s) {
        return switch (s) {
            case UNREAD -> 0x20000000;
            case LOCKED -> 0x18FFFFFF;
            case ON -> Ui.alpha(Ui.GREEN, 0.22f);
            case OFF -> Ui.alpha(Ui.RED, 0.28f);
            case MAX -> Ui.alpha(Ui.GOLD, 0.28f);
        };
    }

    private static void cell(MenuUi ui, int cx, int cy, HeartTrees.Node n, String tree) {
        ui.rect(cx, cy, CELL, CELL, tint(n.state()));
        if (n.state() == HeartTrees.State.UNREAD) ui.textCenter("?", cx + CELL / 2, cy + 6, Ui.DIM);
        else {
            ui.icon(n.icon(), cx + 2, cy + 2, 0.9375f);
            if (n.max() > 1 && n.level() > 0) {
                String level = String.valueOf(n.level());
                ui.textShadow(level, cx + CELL - ui.width(level), cy + 11, n.state() == HeartTrees.State.MAX ? Ui.GOLD : Ui.TEXT);
            }
        }
        ui.tip(cx, cy, CELL, CELL, () -> tip(n, tree));
    }

    // perk tooltip + what it cost (the /hotm and /hotf tooltips say it too)
    private static String[] tip(HeartTrees.Node n, String tree) {
        List<String> lines = new ArrayList<>(n.tooltip());
        String spent = n.state() == HeartTrees.State.UNREAD ? null : tree.endsWith("Mountain") ? dev.zmuzy.sbc.mining.HotmPerks.spentLine(n.name(), n.level()) : whispers(n);
        if (spent != null) lines.add(spent);
        return lines.toArray(new String[0]);
    }

    private static String whispers(HeartTrees.Node n) {
        for (Object[] perk : HeartTrees.HOTF_PERKS) {
            if (!perk[0].equals(n.name()) || ((Number) perk[2]).doubleValue() == 0) continue;
            double exp = ((Number) perk[2]).doubleValue();
            long spent = dev.zmuzy.sbc.areas.ForageHelpers.totalCost(exp, n.level()), all = dev.zmuzy.sbc.areas.ForageHelpers.totalCost(exp, n.max());
            return "§7Whispers spent: §e" + MenuUi.num(spent) + "§7/§e" + MenuUi.num(all) + " §7(§e" + MenuUi.pct(all <= 0 ? 1 : (double) spent / all) + "§7)";
        }
        return null;
    }

    private static Row bought(List<HeartTrees.Node> nodes) {
        long read = nodes.stream().filter(n -> n.state() != HeartTrees.State.UNREAD).count();
        long have = nodes.stream().filter(n -> n.level() > 0).count();
        return new Row("Perks", have + "/" + read + " · " + MenuUi.pct(read <= 0 ? 0 : (double) have / read), Ui.TEXT);
    }

    private static Row levels(List<HeartTrees.Node> nodes) {
        long have = nodes.stream().filter(n -> n.state() != HeartTrees.State.UNREAD).mapToLong(n -> Math.min(n.level(), n.max())).sum();
        long all = nodes.stream().filter(n -> n.state() != HeartTrees.State.UNREAD).mapToLong(HeartTrees.Node::max).sum();
        return new Row("Levels", MenuUi.num(have) + "/" + MenuUi.num(all), Ui.CYAN);
    }

    private static Row levelsDone(List<HeartTrees.Node> nodes) {
        long have = nodes.stream().filter(n -> n.state() != HeartTrees.State.UNREAD).mapToLong(n -> Math.min(n.level(), n.max())).sum();
        long all = nodes.stream().filter(n -> n.state() != HeartTrees.State.UNREAD).mapToLong(HeartTrees.Node::max).sum();
        return new Row("Of all levels", MenuUi.pct(all <= 0 ? 0 : (double) have / all), Ui.CYAN);
    }

    private static void common(List<Row> rows, List<HeartTrees.Node> nodes, int spent, int left, String ability) {
        rows.add(bought(nodes));
        rows.add(levels(nodes));
        rows.add(levelsDone(nodes));
        rows.add(new Row("At maximum", String.valueOf(nodes.stream().filter(n -> n.state() == HeartTrees.State.MAX).count()), Ui.GOLD));
        rows.add(new Row("Turned off", String.valueOf(nodes.stream().filter(n -> n.state() == HeartTrees.State.OFF).count()), Ui.TEXT));
        if (spent >= 0) rows.add(new Row("Tokens spent", String.valueOf(spent), MenuUi.tone(MenuUi.PURPLE)));
        if (left >= 0) rows.add(new Row("Tokens to spend", String.valueOf(left), MenuUi.tone(MenuUi.PURPLE)));
        if (!ability.isEmpty()) rows.add(new Row("Ability", abilityName(nodes, ability), Ui.ORANGE));
    }

    private static String abilityName(List<HeartTrees.Node> nodes, String ability) {
        for (HeartTrees.Node n : nodes) if (n.key().equals(ability)) return n.name();
        return GameState.pretty(ability);
    }

    private static List<Row> mountainRows(Profile.Data d, List<HeartTrees.Node> nodes) {
        List<Row> rows = new ArrayList<>();
        common(rows, nodes, d.hotmTokensSpent, d.hotmTokensLeft, d.hotmAbility);
        if (d.knows(GameProfile.POWDER)) {
            rows.add(new Row("Mithril", d.mithrilPowder < 0 ? "?" : Prices.coins(d.mithrilPowder), MenuUi.tone(0xFF5FEA8C)));
            rows.add(new Row("Gemstone", d.gemstonePowder < 0 ? "?" : Prices.coins(d.gemstonePowder), MenuUi.tone(0xFFEC7DD8)));
            rows.add(new Row("Glacite", d.glacitePowder < 0 ? "?" : Prices.coins(d.glacitePowder), MenuUi.tone(0xFF9BE7FF)));
        }
        return rows;
    }

    private static List<Row> forestRows(Profile.Data d, List<HeartTrees.Node> nodes) {
        List<Row> rows = new ArrayList<>();
        common(rows, nodes, d.hotfTokensSpent, d.hotfTokensLeft, d.hotfAbility);
        if (d.hotfXp > 0) rows.add(new Row("HOTF XP", Prices.coins(d.hotfXp), Ui.TEXT));
        return rows;
    }
}
