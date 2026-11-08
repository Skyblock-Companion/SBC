// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.hud;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemIcons;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// bottom strip: a plate on each side of the hotbar with 2 stats each + the pet next to the right one. all
// hotbar height so it reads as one bar
public final class HotbarElements {
    private HotbarElements() {}

    private static final int HEIGHT = 22, EDGE = 2, INSET = 3, ICON_GAP = 2, TOP_ROW = 1, BOTTOM_ROW = 13;
    private static final int[] EQUIPMENT_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34};

    private record Stat(String name, char icon, String plainIcon, int color, boolean percent, String sample) {}

    private static final String DEFAULT_LEFT = "Vitality,Speed", DEFAULT_RIGHT = "Attack Speed,Ferocity";

    // every stat, from skyhanni SkyblockStat, with hypixels icon + the old one for when its pack is off
    private static final List<Stat> STATS = List.of(
            new Stat("Health", '\uE010', "❤", 0xFFFF5555, false, "4,240"),
            new Stat("Defense", '\uE008', "❈", 0xFF55FF55, false, "1,024"),
            new Stat("True Defense", '\uE027', "❂", 0xFFE8F0F8, false, "48"),
            new Stat("Strength", '\uE00D', "❁", 0xFFFF5555, false, "612"),
            new Stat("Crit Chance", '\uE02C', "☣", 0xFF7C7CFF, true, "78%"),
            new Stat("Crit Damage", '\uE007', "☠", 0xFF7C7CFF, true, "421%"),
            new Stat("Attack Speed", '\uE001', "⚔", 0xFFFFFF55, true, "100%"),
            new Stat("Ferocity", '\uE00B', "⫽", 0xFFFF5555, false, "55"),
            new Stat("Swing Range", '\uE024', "Ⓢ", 0xFFFFFF55, false, "3"),
            new Stat("Intelligence", '\uE003', "✎", 0xFF55FFFF, false, "1,840"),
            new Stat("Ability Damage", '\uE002', "๑", 0xFFFF5555, true, "12%"),
            new Stat("Mana Regen", '\uE004', "✎", 0xFF55FFFF, false, "4"),
            new Stat("Health Regen", '\uE011', "❣", 0xFFFF5555, false, "100"),
            new Stat("Vitality", '\uE028', "♨", 0xFFE04848, false, "120"),
            new Stat("Mending", '\uE014', "☄", 0xFF55FF55, false, "100"),
            new Stat("Speed", '\uE022', "✦", 0xFFE8F0F8, false, "400"),
            new Stat("Magic Find", '\uE01A', "✯", 0xFF55FFFF, false, "118"),
            new Stat("Pet Luck", '\uE013', "♣", 0xFFFF55FF, false, "42"),
            new Stat("Breaking Power", '\uE005', "Ⓟ", 0xFF22C55E, false, "9"),
            new Stat("Mining Speed", '\uE015', "⸕", 0xFFFFAA00, false, "3,204"),
            new Stat("Mining Spread", '\uE016', "▚", 0xFFFFFF55, false, "40"),
            new Stat("Gemstone Spread", '\uE00F', "▚", 0xFFFFFF55, false, "20"),
            new Stat("Pristine", '\uE01C', "✧", 0xFFC060E0, false, "6"),
            new Stat("Mining Fortune", '\uE053', "☘", 0xFFFFAA00, false, "812"),
            new Stat("Ore Fortune", '\uE053', "☘", 0xFFFFAA00, false, "120"),
            new Stat("Block Fortune", '\uE053', "☘", 0xFFFFAA00, false, "90"),
            new Stat("Dwarven Metal Fortune", '\uE053', "☘", 0xFFFFAA00, false, "60"),
            new Stat("Gemstone Fortune", '\uE053', "☘", 0xFFFFAA00, false, "60"),
            new Stat("Farming Fortune", '\uE051', "☘", 0xFFFFAA00, false, "379"),
            new Stat("Wheat Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Carrot Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Potato Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Pumpkin Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Sugar Cane Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Melon Slice Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Cactus Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Cocoa Beans Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Mushroom Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Nether Wart Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Sunflower Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Moonflower Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Wild Rose Fortune", '\uE051', "☘", 0xFFFFAA00, false, "61"),
            new Stat("Bonus Pest Chance", '\uE019', "ൠ", 0xFF22C55E, false, "162"),
            new Stat("Overbloom", '\uE02B', "✧", 0xFFFFFF55, false, "12"),
            new Stat("Sweep", '\uE023', "∮", 0xFF22C55E, false, "24"),
            new Stat("Foraging Fortune", '\uE054', "☘", 0xFFFFAA00, false, "204"),
            new Stat("Fig Fortune", '\uE054', "☘", 0xFFFFAA00, false, "40"),
            new Stat("Mangrove Fortune", '\uE054', "☘", 0xFFFFAA00, false, "40"),
            new Stat("Helix Fortune", '\uE054', "☘", 0xFFFFAA00, false, "40"),
            new Stat("Timber", '\uE02E', "⚒", 0xFFE04848, false, "12"),
            new Stat("Fishing Speed", '\uE00C', "☂", 0xFF55FFFF, false, "120"),
            new Stat("Sea Creature Chance", '\uE021', "α", 0xFF22B8B8, true, "24%"),
            new Stat("Double Hook Chance", '\uE009', "⚓", 0xFF7C7CFF, true, "8%"),
            new Stat("Trophy Fish Chance", '\uE02A', "♔", 0xFFFFAA00, true, "12%"),
            new Stat("Treasure Chance", '\uE025', "⛃", 0xFFFFAA00, true, "6%"),
            new Stat("Heat Resistance", '\uE012', "♨", 0xFFFF5555, false, "20"),
            new Stat("Cold Resistance", '\uE006', "❄", 0xFF55FFFF, false, "40"),
            new Stat("Respiration", '\uE01D', "⚶", 0xFF22B8B8, false, "2"),
            new Stat("Pressure Resistance", '\uE01B', "❍", 0xFF7C7CFF, false, "2"),
            new Stat("Fear", '\uE00A', "☠", 0xFFC060E0, false, "10"),
            new Stat("Tracking", '\uE077', "⌖", 0xFFFF55FF, false, "12"),
            new Stat("Pull", '\uE02D', "ᛷ", 0xFF55FFFF, false, "8"),
            new Stat("Hunting Fortune", '\uE05B', "☘", 0xFFFF55FF, false, "64"),
            new Stat("Charm Chance", '\u2763', "❣", 0xFF55FFFF, true, "4%"),
            new Stat("Combat Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Farming Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Fishing Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Mining Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Foraging Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Enchanting Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Alchemy Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Carpentry Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Runecrafting Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Taming Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Social Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Hunting Wisdom", '\u262F', "☯", 0xFF22B8B8, false, "40"),
            new Stat("Rift Time", '\uE020', "ф", 0xFF55FF55, false, "120"),
            new Stat("Rift Damage", '\uE01E', "❁", 0xFFC060E0, false, "24"),
            new Stat("Hearts", '\uE01F', "❤", 0xFFFF5555, false, "12"));

    private static Stat stat(String name) {
        for (Stat s : STATS) if (s.name.equalsIgnoreCase(name)) return s;
        return null;
    }

    public static List<String> statNames() {
        List<String> out = new java.util.ArrayList<>();
        for (Stat s : STATS) out.add(s.name);
        return out;
    }

    private static boolean opt(String feature, String key, boolean def) {
        var f = Features.get(feature);
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static boolean strip() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (GameState.inSkyBlock || SbcMod.DEV);
    }

    private static String number(String raw, boolean percent) {
        String v = raw.replace(",", "").replace("%", "").trim();
        try {
            String n = String.format(Locale.ROOT, "%,d", Math.round(Double.parseDouble(v)));
            return percent ? n + "%" : n;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    // where a stats number comes from, null till something said it
    private static String value(Stat s) {
        if (s.name.equals("Health") && Vitals.hpMax() > 0) return number(String.valueOf(Vitals.hpMax()), false);
        if (s.name.equals("Defense") && Vitals.defense > 0) return number(String.valueOf(Vitals.defense), false);
        // what is left of it over what you have, like the health and mana bars
        if (s.name.equals("Vitality") && Vitals.vitalityMax > 0) return number(String.valueOf(Vitals.vitality), false) + "/" + number(String.valueOf(Vitals.vitalityMax), false);
        if (s.name.equals("Speed")) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                double speed = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * 1000; // from skyhanni PlayerUtils.getWalkSpeed
                if (speed > 0) return number(String.valueOf(speed), false);
            }
        }
        String v = Vitals.stats.get(s.name);
        if (v == null) v = Config.I.statMemory.get(GameState.profileName + "/" + s.name);
        return v == null ? null : number(v, s.percent);
    }

    // plate next to the hotbar: your stats, 2 per column, first ones closest to the hotbar. numbers outside, icons inside
    private static final class Plate extends HudElement {
        private static final int COLUMN_GAP = 6, MAX = 12;
        private final String option;
        private final boolean left;
        private String parsedFrom;
        private List<Stat> parsed = List.of();

        Plate(String id, String title, String option, boolean left) {
            super(id, title, "HUD", left ? Zone.HOTBAR_LEFT : Zone.HOTBAR_RIGHT, 10, "hotbar_stats");
            this.option = option;
            this.left = left;
        }

        private String chosen() {
            var f = Features.get("hotbar_stats");
            var o = f == null ? null : f.option(option);
            return o != null ? o.text() : left ? DEFAULT_LEFT : DEFAULT_RIGHT;
        }

        private List<Stat> stats() {
            String csv = chosen();
            if (csv.equals(parsedFrom)) return parsed;
            List<Stat> out = new java.util.ArrayList<>();
            for (String name : csv.split(",")) {
                Stat st = stat(name.trim());
                if (st != null && !out.contains(st) && out.size() < MAX) out.add(st);
            }
            parsedFrom = csv;
            return parsed = out;
        }

        @Override
        public List<String> pickable() {
            return statNames();
        }

        @Override
        public boolean picked(String name) {
            for (Stat st : stats()) if (st.name.equals(name)) return true;
            return false;
        }

        // newly ticked stats go at the end, away from the hotbar
        @Override
        public void pick(String name, boolean on) {
            List<String> names = new java.util.ArrayList<>();
            for (Stat st : stats()) if (!st.name.equals(name)) names.add(st.name);
            if (on) names.add(name);
            var f = Features.get("hotbar_stats");
            var o = f == null ? null : f.option(option);
            if (o != null) o.set(String.join(",", names));
        }

        @Override
        public boolean visible() {
            return strip() && !stats().isEmpty();
        }

        @Override
        public boolean supportsAlways() {
            return false;
        }

        @Override
        public boolean stretchable() {
            return false;
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        public List<Row> demo() {
            return List.of();
        }

        private String shown(Stat s) {
            String v = value(s);
            if (v == null) v = demo ? s.sample : "-";
            return opt("hotbar_stats", "names", false) ? s.name + " " + v : v;
        }

        private String icon(Stat s) {
            return opt("hotbar_stats", "plainIcons", false) ? s.plainIcon : String.valueOf(s.icon);
        }

        // the 2 icons of a column stack so the numbers line up
        private int iconColumn(Ui ui, Stat a, Stat b) {
            return Math.max(ui.width(icon(a)), b == null ? 0 : ui.width(icon(b)));
        }

        private int columnWidth(Ui ui, Stat a, Stat b) {
            return iconColumn(ui, a, b) + ICON_GAP + Math.max(ui.width(shown(a)), b == null ? 0 : ui.width(shown(b)));
        }

        @Override
        public int width() {
            Ui ui = Ui.MEASURE;
            List<Stat> all = stats();
            if (all.isEmpty()) return 40;
            int w = 2 * INSET + EDGE;
            for (int i = 0; i < all.size(); i += 2) w += columnWidth(ui, all.get(i), i + 1 < all.size() ? all.get(i + 1) : null) + (i > 0 ? COLUMN_GAP : 0);
            return Math.max(40, w);
        }

        @Override
        public int height() {
            return HEIGHT;
        }

        // number gets a tint of its stat color, a name stays a label
        private void row(Ui ui, Stat s, int columnX, int columnW, int iconW, int y) {
            String icon = icon(s), number = value(s);
            boolean known = number != null || demo;
            if (number == null) number = demo ? s.sample : "-";
            String name = opt("hotbar_stats", "names", false) ? s.name + " " : "";
            int rw = ui.width(name) + ui.width(number);
            int numberColor = known ? Modern.lighten(s.color | 0xFF000000, 0.55f) : Modern.DIM;
            int ix, tx;
            if (left) {
                ix = columnX + columnW - iconW;
                tx = ix - ICON_GAP - rw;
            } else {
                ix = columnX;
                tx = ix + iconW + ICON_GAP;
            }
            ui.textShadow(icon, ix + (iconW - ui.width(icon)) / 2, y, s.color);
            tx = BarsElement.piece(ui, name, tx, y, Modern.LABEL);
            BarsElement.piece(ui, number, tx, y, numberColor);
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            int w = width();
            Theme.current().panel(ui, x, y, w, HEIGHT, style());
            List<Stat> all = stats();
            if (all.isEmpty()) return;
            // the hotbar side carries the colors of the 2 stats next to it
            int ex = left ? x + w - EDGE : x;
            ui.rect(ex, y + 1, EDGE, 10, Ui.alpha(all.get(0).color | 0xFF000000, 0.9f));
            if (all.size() > 1) ui.rect(ex, y + 11, EDGE, 10, Ui.alpha(all.get(1).color | 0xFF000000, 0.9f));
            Modern.rule(ui, x + (left ? 3 : EDGE + 1), y + TOP_ROW + 9, w - EDGE - 4);
            int edge = left ? x + w - EDGE - INSET : x + EDGE + INSET;
            for (int i = 0; i < all.size(); i += 2) {
                Stat a = all.get(i), b = i + 1 < all.size() ? all.get(i + 1) : null;
                int cw = columnWidth(ui, a, b), iw = iconColumn(ui, a, b), cx = left ? edge - cw : edge;
                row(ui, a, cx, cw, iw, y + TOP_ROW);
                if (b != null) row(ui, b, cx, cw, iw, y + BOTTOM_ROW);
                edge = left ? cx - COLUMN_GAP : cx + cw + COLUMN_GAP;
                if (i + 2 < all.size()) ui.rect(left ? cx - COLUMN_GAP / 2 : cx + cw + COLUMN_GAP / 2, y + 2, 1, HEIGHT - 4, 0x1CFFFFFF);
            }
        }
    }

    private static final int XP_NOW = 0xFFC4F58C, XP_NEED = Modern.GOLD;
    private static String petIconKey = "";
    private static ItemStack petIcon = ItemStack.EMPTY;

    private static ItemStack petIcon(String name, String tier) {
        String key = name + "/" + tier;
        if (!key.equals(petIconKey)) {
            petIconKey = key;
            petIcon = ItemIcons.pet(name.toUpperCase(Locale.ROOT).replace(' ', '_'), tier.isEmpty() ? "COMMON" : tier);
        }
        return petIcon;
    }

    // rare/epic colors are hard to read on glass so the name gets a lighter shade
    private static int readable(int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return 0.299 * r + 0.587 * g + 0.114 * b < 128 ? Modern.lighten(color, 0.38f) : color;
    }

    // equipped pet: head, name in rarity color, level, how far to next
    private static final class PetCard extends HudElement {
        PetCard() {
            super("pet_equipped", "Equipped pet", "HUD", Zone.HOTBAR_RIGHT, 20, "pet_equipped");
        }

        @Override
        public boolean visible() {
            return strip() && !Vitals.petName.isEmpty();
        }

        @Override
        public boolean supportsAlways() {
            return false;
        }

        @Override
        public boolean stretchable() {
            return false;
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        public List<Row> demo() {
            return List.of();
        }

        private String name() {
            return Vitals.petName.isEmpty() && demo ? "Elephant" : Vitals.petName;
        }

        private String level() {
            return "Lvl " + (Vitals.petName.isEmpty() && demo ? 95 : Vitals.petLevel);
        }

        private boolean sample() {
            return Vitals.petName.isEmpty() && demo;
        }

        private String xp() {
            return sample() ? "550,162/1.4M" : Vitals.petXp;
        }

        // % from the tab list, else from its numbers, -1 = neither
        private double pct() {
            double pct = sample() ? 39.7 : Vitals.petXpPct;
            String xp = xp();
            int slash = xp.indexOf('/');
            if (pct < 0 && slash > 0) {
                double now = RowText.number(xp.substring(0, slash)), of = RowText.number(xp.substring(slash + 1));
                if (now >= 0 && of > 0) pct = Math.min(100, 100 * now / of);
            }
            return pct;
        }

        private static final int TEXT_X = 24, RIGHT = 6, PET_H = 28, PET_TOP = 4, PET_METER = 14, PET_BOTTOM = 18;

        // xp numbers + % under the meter
        private int xpLineWidth(Ui ui) {
            double pct = pct();
            if (pct >= 100) return ui.width("MAX LEVEL") + 6 + ui.width("100%");
            int n = xp().isEmpty() ? ui.width("-") : ui.width(xp());
            return pct < 0 ? n : n + 6 + ui.width(String.format(Locale.ROOT, "%.1f%%", pct));
        }

        @Override
        public int width() {
            Ui ui = Ui.MEASURE;
            return Math.max(96, TEXT_X + Math.max(ui.width(name()) + 6 + ui.width(level()), xpLineWidth(ui)) + RIGHT);
        }

        @Override
        public int height() {
            return PET_H;
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            Theme t = Theme.current();
            int w = width();
            t.panel(ui, x, y, w, PET_H, style());
            String tier = sample() ? "LEGENDARY" : Vitals.petTier;
            int color = sample() ? 0xFFFFAA00 : Vitals.petColor != 0 ? 0xFF000000 | Vitals.petColor : Modern.INK;
            Modern.edge(ui, x, y, PET_H, color);
            ui.icon(petIcon(name(), tier), x + 5, y + (PET_H - 16) / 2, 1f);
            int tx = x + TEXT_X, tw = w - TEXT_X - RIGHT;

            // rows line up with the plate stats, meter where their hairline is
            String level = level(), word = "Lvl ", number = level.substring(word.length());
            int lw = ui.width(level);
            BarsElement.piece(ui, ui.ellipsize(name(), tw - lw - 4), tx, y + PET_TOP, readable(color));
            int lx = BarsElement.piece(ui, word, tx + tw - lw, y + PET_TOP, Modern.LABEL);
            BarsElement.piece(ui, number, lx, y + PET_TOP, Modern.INK);

            double pct = pct();
            Modern.meter(ui, tx, y + PET_METER, tw, Modern.METER, pct < 0 ? 0 : pct / 100.0, color);

            int ly = y + PET_BOTTOM;
            if (pct >= 100) {
                BarsElement.piece(ui, "MAX LEVEL", tx, ly, Modern.GOLD);
                ui.textShadow("100%", tx + tw - ui.width("100%"), ly, Modern.INK);
                return;
            }
            String xp = xp();
            int slash = xp.indexOf('/');
            if (slash > 0) {
                int nx = BarsElement.piece(ui, xp.substring(0, slash), tx, ly, XP_NOW);
                nx = BarsElement.piece(ui, "/", nx, ly, Modern.DIM);
                BarsElement.piece(ui, xp.substring(slash + 1), nx, ly, XP_NEED);
            } else BarsElement.piece(ui, xp.isEmpty() ? "-" : xp, tx, ly, xp.isEmpty() ? Modern.DIM : XP_NOW);
            if (pct >= 0) {
                String p = String.format(Locale.ROOT, "%.1f%%", pct);
                ui.textShadow(p, tx + tw - ui.width(p), ly, Modern.INK);
            }
        }

    }

    // from skyhanni SkyblockStat onSkyblockMenu/onStatsMenu: what a menu said sticks till something newer
    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.player.tickCount % 10 != 0) return;
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || !(screen.getMenu() instanceof ChestMenu menu)) return;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        int[] slots = title.equals("SkyBlock Menu") ? new int[]{13} : title.equals("Stats & Equipment") ? EQUIPMENT_SLOTS : null;
        if (slots == null) return;
        List<String> lines = new ArrayList<>();
        for (int slot : slots) {
            if (slot >= menu.slots.size()) continue;
            var lore = menu.slots.get(slot).getItem().get(DataComponents.LORE);
            if (lore != null) for (Component line : lore.lines()) lines.add(GameState.strip(line.getString()));
        }
        boolean changed = false;
        for (Map.Entry<String, String> e : Vitals.statsInLore(lines).entrySet()) {
            String key = GameState.profileName + "/" + e.getKey();
            if (!e.getValue().equals(Config.I.statMemory.get(key))) {
                Config.I.statMemory.put(key, e.getValue());
                changed = true;
            }
            Vitals.stats.put(e.getKey(), e.getValue());
        }
        if (changed) Config.save();
    }

    public static void register() {
        Features.add(new Feature("hotbar_stats", "HUD", "Stats beside the hotbar", "Vitality and Speed left of the hotbar, Attack Speed and Ferocity right of it. Hypixel only sends the stats you pick for its Stats tab widget; the others show what the SkyBlock Menu said the last time you opened it.", "")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tick(HotbarElements::tick)
                .option(Option.text("hotbar_stats.left", "Left of the hotbar", "Names with commas between them, nearest to the hotbar first, or tick them below.", DEFAULT_LEFT))
                .option(Option.text("hotbar_stats.right", "Right of the hotbar", "Names with commas between them, nearest to the hotbar first, or tick them below.", DEFAULT_RIGHT))
                .option(Option.bool("names", "Names next to the numbers", "", false))
                .option(Option.bool("plainIcons", "Plain icons", "For when Hypixel's own icons show as squares, without its resource pack.", false)));
        Features.add(new Feature("pet_equipped", "HUD", "Equipped pet", "Your pet right of the hotbar: head, name in its rarity's colour, level and progress to the next level. Reads Hypixel's Pet tab widget.", "")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        HudLayout.add(new Plate("hotbar_stats_left", "Stats left of the hotbar", "left", true));
        HudLayout.add(new Plate("hotbar_stats_right", "Stats right of the hotbar", "right", false));
        HudLayout.add(new PetCard());
    }
}
