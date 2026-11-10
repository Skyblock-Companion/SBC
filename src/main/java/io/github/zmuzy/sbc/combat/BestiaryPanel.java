// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.combat;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;

// panel next to the bestiary menu: each family or category with progress, sorted how you pick. from skyhanni
// BestiaryApi + BestiaryData
// their buttons are settings here. overall progress hint goes on the side panel too, slots go green when
// maxed and red on the eye that has to be on
public final class BestiaryPanel {
    public static final String ID = "bestiary";
    private static final Patterns.P TITLE = Patterns.p("SH.combat.bestiary.data.title.colorless"), SEARCH = Patterns.p("SH.combat.bestiary.data.title.search-results.colorless"), TIER = Patterns.p("SH.combat.bestiary.data.tierprogress.colorless"),
            OVERALL = Patterns.p("SH.combat.bestiary.data.overallprogress.colorless"), PROGRESS = Patterns.p("SH.combat.bestiary.data.progress.colorless"), BAR = Patterns.p("SH.combat.bestiary.data.progress.bar.line"),
            LEVEL = Patterns.p("SH.combat.bestiary.data.mob.level.colorless"), KILLS = Patterns.p("SH.combat.bestiary.data.kills.line.colorless"), LOCKED = Patterns.p("SH.combat.bestiary.data.progress.not-unlocked-family.colorless"),
            SHOWN = Patterns.p("SH.combat.bestiary.data.progress.overall-shown"), FOUND = Patterns.p("SH.combat.bestiary.data.family.found.colorless"), DONE = Patterns.p("SH.combat.bestiary.data.family.completed.colorless"),
            DONE_0 = Patterns.p("SH.combat.bestiary.completed.0"), DONE_1 = Patterns.p("SH.combat.bestiary.completed.1"), HIDDEN = Patterns.p("SH.combat.bestiary.hiddenprogress");
    private static final int OVERALL_SLOT = 52;
    private static final String[] CATEGORY_TITLES = {"SH.combat.bestiary.data.category-of-category.colorless.0", "SH.combat.bestiary.data.category-of-category.colorless.1", "SH.combat.bestiary.data.category-of-category.colorless.2"};

    // from skyhanni BestiaryMob
    record Mob(String name, int level, long killToMax, long totalKills, long neededForNext, long currentToNext, long realKills) {
        long neededToMax() {
            return Math.max(0, killToMax - realKills);
        }

        long neededToNext() {
            return Math.max(0, neededForNext - currentToNext);
        }

        double percentToMax() {
            return killToMax == 0 ? 0 : realKills / (double) killToMax;
        }

        double percentToTier() {
            return neededForNext == 0 ? 1 : currentToNext / (double) neededForNext;
        }
    }

    // from skyhanni Category
    record Category(String name, long found, long total, long completed) {}

    private static long num(String s) {
        return (long) SlayerFeatures.shortNumber(s);
    }

    // from skyhanni parseMobFromStack
    static Mob parseMob(String cleanName, List<String> cleanLore) {
        Matcher lv = LEVEL.matcher(cleanName);
        if (!lv.matches()) return null;
        String levelText = lv.group("level");
        int level = levelText == null ? 0 : levelText.matches("\\d+") ? Integer.parseInt(levelText) : GameState.roman(levelText);
        long toMax = 0, total = 0, toTier = 0, current = 0, real = 0;
        boolean unlocked = true;
        for (int i = 0; i < cleanLore.size(); i++) {
            String line = cleanLore.get(i);
            if (LOCKED.matcher(line).matches()) unlocked = false;
            Matcher k = KILLS.matcher(line);
            if (k.find()) real = num(k.group("kills"));
            if (!BAR.matcher(line).matches() || i == 0) continue;
            String prev = cleanLore.get(i - 1), progress = line.substring(line.lastIndexOf(' ') + 1);
            Matcher p = PROGRESS.matcher(progress);
            if (!p.matches()) continue;
            if (TIER.matcher(prev).matches()) {
                toTier = num(p.group("needed"));
                current = num(p.group("current"));
            } else if (OVERALL.matcher(prev).matches()) {
                toMax = num(p.group("needed"));
                total = num(p.group("current"));
            }
        }
        if (toMax == 0 && toTier == 0 && unlocked) return null;
        return new Mob(lv.group("name"), level, toMax, total, toTier, current, real);
    }

    // from skyhanni parseCategoryFromStack
    static Category parseCategory(String cleanName, List<String> cleanLore) {
        long found = 0, total = 0, done = 0;
        for (int i = 1; i < cleanLore.size(); i++) {
            String line = cleanLore.get(i);
            if (!line.startsWith(" ".repeat(20))) continue;
            Matcher p = PROGRESS.matcher(line.substring(line.lastIndexOf(' ') + 1));
            if (!p.matches()) continue;
            String prev = cleanLore.get(i - 1);
            if (FOUND.matcher(prev).matches()) {
                found = num(p.group("current"));
                total = num(p.group("needed"));
            } else if (DONE.matcher(prev).matches()) done = num(p.group("current"));
        }
        return new Category(cleanName, found, total, done);
    }

    private static boolean inner(int slot) {
        return slot > 9 && slot < 44 && slot % 9 != 0 && slot % 9 != 8;
    }

    private static boolean title(String t) {
        return categoryTitle(t) || TITLE.matcher(t).matches() || SEARCH.matcher(t).matches();
    }

    // from skyhanni isBestiaryGui. "Bazaar ➜ Farming" looks the same, so a parent thats not Bestiary needs slot 4
    // to mention families or kills
    static boolean isBestiary(String t, List<String> slotFour) {
        if (categoryTitle(t) || SEARCH.matcher(t).matches()) return true;
        Matcher m = TITLE.matcher(t);
        if (!m.matches()) return false;
        if (m.group("parent").trim().equals("Bestiary")) return true;
        for (String l : slotFour) if (FOUND.matcher(l).matches() || KILLS.matcher(l).matches()) return true;
        return false;
    }

    private static boolean categoryTitle(String t) {
        for (String key : CATEGORY_TITLES) if (Patterns.get(key).matcher(t).matches()) return true;
        return false;
    }

    // from skyhanni onBackgroundDrawn
    private static void marks(MenuView v) {
        if (!isBestiary(v.title, v.item(4).cleanLore)) return;
        boolean shown = shownIn(v);
        for (int i = 0; i < v.top; i++) {
            if (v.items[i].empty()) continue;
            for (String l : v.items[i].cleanLore) {
                if (DONE_0.matcher(l).matches() || DONE_1.matcher(l).matches()) v.back(i, MenuView.GREEN);
                else if (!shown && HIDDEN.matcher(l).matches()) v.back(i, MenuView.RED);
            }
        }
    }

    // from skyhanni isOverallProgressEnabled
    private static boolean shownIn(MenuView v) {
        if (v.top > OVERALL_SLOT && v.items[OVERALL_SLOT].model.contains("ender_eye")) {
            for (String l : v.items[OVERALL_SLOT].cleanLore) if (SHOWN.matcher(l).matches()) return true;
            return false;
        }
        for (int i = 0; i < v.top; i++) {
            if (!inner(i)) continue;
            boolean tier = false, overall = false;
            for (String l : v.items[i].cleanLore) {
                tier |= TIER.matcher(l).matches();
                overall |= OVERALL.matcher(l).matches();
            }
            if (tier && !overall) return false;
        }
        return true;
    }

    private static final class Panel implements SideHelper.Content {
        boolean shown = true;
        final List<Mob> mobs = new ArrayList<>();
        final List<Category> categories = new ArrayList<>();

        @Override public String title() {
            return "Bestiary";
        }

        @Override public ItemStack icon() {
            return new ItemStack(Items.WRITABLE_BOOK);
        }

        @Override public int accent() {
            return Modern.GOOD;
        }

        @Override public String[] tabs() {
            return new String[]{mobs.isEmpty() ? "Categories " + categories.size() : "Families " + mobs.size()};
        }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            var slots = screen.getMenu().slots;
            int size = Math.max(0, slots.size() - 36);
            MenuView.Item[] items = new MenuView.Item[size];
            for (int i = 0; i < size; i++) items[i] = MenuView.Item.of(slots.get(i).getItem());
            String t = GameState.strip(screen.getTitle().getString()).trim();
            shown = shownIn(new MenuView(t, items, size));
            mobs.clear();
            categories.clear();
            for (int i = 0; i < size; i++) {
                if (!inner(i) || items[i].empty() || items[i].clean.isBlank()) continue;
                if (categoryTitle(t)) {
                    Category c = parseCategory(items[i].clean, items[i].cleanLore);
                    if (c.total() > 0) categories.add(c);
                } else {
                    Mob m = parseMob(items[i].clean, items[i].cleanLore);
                    if (m != null) mobs.add(m);
                }
            }
            sort(mobs, Combat.choice(ID, "sort", "Closest to max"));
        }

        private String n(long v) {
            return Combat.choice(ID, "numbers", "Short").equals("Short") ? Combat.shortNumber(v) : String.format(Locale.ROOT, "%,d", v);
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            if (!shown) {
                SideHelper.tile(ui, x, y, w, "Overall Progress", "turned off", "Turn it on with the red Eye of Ender.", Modern.BAD);
                return 26;
            }
            int maxed = 0;
            for (Mob m : mobs) if (m.percentToMax() >= 1) maxed++;
            SideHelper.tile(ui, x, y, w, mobs.isEmpty() ? "Categories" : "Maxed", mobs.isEmpty() ? String.valueOf(categories.size()) : maxed + "/" + mobs.size(), "", Modern.GOOD);
            return 26;
        }

        private int line(Ui ui, String name, String value, int color, int x, int y, int w) {
            int vw = ui.width(value);
            ui.textShadow(ui.ellipsize(name, w - vw - 6), x + 2, y, Modern.INK);
            ui.textShadow(value, x + w - 2 - vw, y, color);
            return 11;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (Category c : categories) {
                String info = c.completed() == c.total() ? "Completed" : c.found() == c.total() ? c.completed() + "/" + c.total() + " done" : c.found() + "/" + c.total() + " found, " + c.completed() + " done";
                y += line(ui, c.name(), info, c.completed() == c.total() ? Modern.GOOD : Modern.LABEL, x, y, w);
            }
            String sort = Combat.choice(ID, "sort", "Closest to max");
            boolean roman = Combat.opt(ID, "roman"), hide = Combat.opt(ID, "hide_maxed");
            for (Mob m : mobs) {
                String name = m.name() + " " + (roman && m.level() > 0 ? toRoman(m.level()) : String.valueOf(m.level()));
                if (m.realKills() == 0) {
                    y += line(ui, name, "not unlocked", Modern.BAD, x, y, w);
                    continue;
                }
                boolean maxed = m.percentToMax() >= 1;
                if (maxed && hide) continue;
                y += line(ui, name, maxed ? "MAXED " + n(m.realKills()) : value(m, sort), maxed ? Modern.GOOD : Modern.LABEL, x, y, w);
            }
            if (y == y0) y += SideHelper.emptyText(ui, "Nothing to show on this page.", x, y, w);
            return y - y0;
        }

        private String value(Mob m, String sort) {
            return switch (sort) {
                case "Closest to next level" -> n(m.currentToNext()) + "/" + n(m.neededForNext()) + " " + Math.round(m.percentToTier() * 100) + "%";
                case "Fewest kills", "Most kills" -> n(m.realKills()) + " kills";
                case "Least to max", "Most to max" -> n(m.neededToMax()) + " to go";
                case "Least to next level", "Most to next level" -> n(m.neededToNext()) + " to go";
                default -> n(m.totalKills()) + "/" + n(m.killToMax()) + " " + Math.round(m.percentToMax() * 100) + "%";
            };
        }
    }

    // skyhanni sortMobList, furthest from done first like theirs
    static void sort(List<Mob> mobs, String by) {
        Comparator<Mob> c = switch (by) {
            case "Closest to next level" -> Comparator.comparingDouble(Mob::percentToTier);
            case "Fewest kills" -> Comparator.comparingLong(Mob::realKills);
            case "Most kills" -> Comparator.comparingLong(Mob::realKills).reversed();
            case "Least to max" -> Comparator.comparingLong(Mob::neededToMax);
            case "Most to max" -> Comparator.comparingLong(Mob::neededToMax).reversed();
            case "Least to next level" -> Comparator.comparingLong(Mob::neededToNext);
            case "Most to next level" -> Comparator.comparingLong(Mob::neededToNext).reversed();
            default -> Comparator.comparingDouble(Mob::percentToMax);
        };
        mobs.sort(c);
    }

    private static String toRoman(int n) {
        int[] v = {50, 40, 10, 9, 5, 4, 1};
        String[] s = {"L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                out.append(s[i]);
                n -= v[i];
            }
        }
        return out.toString();
    }

    private static final Panel PANEL = new Panel();

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID)) return null;
        String t = GameState.strip(s.getTitle().getString()).trim();
        if (!title(t) || !(s instanceof AbstractContainerScreen<?> c) || c.getMenu().slots.size() <= 4) return null;
        List<String> four = new ArrayList<>();
        for (String l : io.github.zmuzy.sbc.core.LegacyText.lore(c.getMenu().getSlot(4).getItem())) four.add(GameState.strip(l));
        return isBestiary(t, four) ? PANEL : null;
    }

    static void register() {
        Features.add(new Feature(ID, "Combat", "Bestiary helper", "Beside the Bestiary: every family with its progress, sorted as you choose, and which are maxed.", "SkyHanni: Bestiary Data").when(Combat::sb)
                .option(Option.choice("bestiary.sort", "Sort by", "", "Closest to max", "Closest to max", "Closest to next level", "Fewest kills", "Most kills", "Least to max", "Most to max", "Least to next level", "Most to next level"))
                .option(Option.choice("bestiary.numbers", "Numbers", "", "Short", "Short", "Long"))
                .option(Option.bool("bestiary.roman", "Roman levels", "", true))
                .option(Option.bool("bestiary.hide_maxed", "Hide maxed", "", false)));
        SideHelper.add(BestiaryPanel::panel);
        MenuMarks.add(ID, (Predicate<String>) BestiaryPanel::title, BestiaryPanel::marks);
    }
}
