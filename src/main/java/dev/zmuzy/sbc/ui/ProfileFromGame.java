// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.game.GameProfile;
import dev.zmuzy.sbc.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// profile page + dashboard card for a profile read from the game (core/game/GameProfile)
// same cards as the api path in PagesMore, anything not read yet says what to open
final class ProfileFromGame {
    private static final int PROFILE = 0xFF5CD8FF, DUNGEON = 0xFFFF5D73, NETWORTH = 0xFFFFD166, FISH = 0xFF4FC3F7;

    private ProfileFromGame() {}

    private static String coins(double v) {
        return Prices.coins(v);
    }

    private static String ofKnown(Profile.Data d, String key, String value) {
        return d.knows(key) ? value : "?";
    }

    private static String coinsOr(double v) {
        return v < 0 ? "?" : coins(v);
    }

    // what to open, wraps instead of getting cut
    static void hintLine(MenuUi ui, String hint, int x, int y, int w) {
        ui.rect(x, y + 2, 3, 3, Ui.ORANGE);
        ui.wrap(hint, x + 7, y, w - 7, Ui.MUTED);
    }

    // card with only "open X" in it, for a tab with nothing yet
    private static int hintCard(MenuUi ui, int x, int y, int w, String title, String icon, int accent, String... keys) {
        List<String> hints = new ArrayList<>();
        for (String key : keys) {
            String h = GameProfile.hint(key);
            if (!h.isEmpty() && !hints.contains(h)) hints.add(h);
        }
        int h = MenuUi.TOP + hints.size() * 11 + 4;
        int top = ui.card(x, y, w, h, title, icon, "Not read yet", accent);
        int ry = y + top;
        for (String hint : hints) {
            hintLine(ui, hint, x + 6, ry, w - 12);
            ry += 11;
        }
        return h + MenuUi.GAP;
    }

    // still to open, under whatever we know
    private static int stillToOpen(MenuUi ui, int x, int y, int w, Profile.Data d, String... keys) {
        List<String> open = new ArrayList<>();
        for (String key : keys) if (!d.knows(key)) open.add(key);
        return open.isEmpty() ? 0 : hintCard(ui, x, y, w, "Still to open", "spyglass", Ui.ORANGE, open.toArray(new String[0]));
    }

    // line for planning pages: what they skip bc it wasnt read yet
    static int notice(MenuUi ui, int x, int y, int w, Profile.Data d, String... keys) {
        if (d == null || !d.fromGame) return 0;
        for (String key : keys) {
            if (d.knows(key)) continue;
            ui.edgeCard(x, y, w, 18, Ui.ORANGE);
            hintLine(ui, "Not in this plan yet. " + GameProfile.hint(key), x + 8, y + 5, w - 14);
            return 18 + MenuUi.GAP;
        }
        return 0;
    }

    // --- dashboard card ---

    // same layout as PagesMain.profileCard
    static int card(MenuUi ui, int x, int y, int w, Profile.Data pd) {
        int h = 76;
        String title = pd.player + " · " + pd.profileName + (pd.gameMode.isEmpty() ? "" : " · " + pd.gameMode);
        int top = ui.card(x, y, w, h, title, "player_head", "Open profile ›", PROFILE);
        int lvl = pd.sbLevel(), xpIn = pd.sbXp < 0 ? 0 : (int) (pd.sbXp % 100);
        ui.textScaled(lvl < 0 ? "Level ?" : "Level " + lvl, x + 6, y + top + 2, 1.5f, Ui.CYAN, true);
        ui.meter(x + 6, y + top + 17, 120, 3, xpIn / 100.0, 0xFF5CD8FF);
        if (lvl >= 0) ui.text(xpIn + "/100 XP · " + xpIn + "%", x + 6, y + top + 23, Ui.MUTED);
        List<String> missing = GameProfile.missing(pd);
        if (!missing.isEmpty()) {
            ui.text(ui.ellipsize(missing.size() + " left to open", 122), x + 6, y + top + 33, Ui.ORANGE);
            ui.tip(x + 6, y + top + 31, 122, 11, missing.toArray(new String[0]));
        }
        int tx = x + 134, gap = 3, tw = (w - 134 - 6 - 3 * gap) / 4, th = 26;
        ui.tile(tx, y + top, tw, th, "Skill average", pd.skillAverage < 0 ? "?" : String.format(Locale.ROOT, "%.1f", pd.skillAverage), null, Ui.TEXT, -1, 0);
        ui.tile(tx + tw + gap, y + top, tw, th, "Catacombs", levelText(pd.catacombs), null, MenuUi.tone(DUNGEON), meter(pd.catacombs), DUNGEON);
        ui.tile(tx + 2 * (tw + gap), y + top, tw, th, "Networth", ofKnown(pd, GameProfile.NETWORTH, coins(pd.networth())), null, Ui.GOLD, -1, 0);
        ui.tile(tx + 3 * (tw + gap), y + top, tw, th, "Magical Power", ofKnown(pd, GameProfile.MAGICAL_POWER, MenuUi.num(pd.magicalPower)), null, MenuUi.tone(MenuUi.PURPLE), -1, 0);
        int y2 = y + top + th + 2;
        ui.tile(tx, y2, tw, th, "Slayers", slayerLevels(pd), null, MenuUi.tone(0xFFFF5252), -1, 0);
        ui.tile(tx + tw + gap, y2, tw, th, "HOTM", levelText(pd.hotm), null, MenuUi.tone(0xFF4FD8C4), meter(pd.hotm), 0xFF4FD8C4);
        ui.tile(tx + 2 * (tw + gap), y2, tw, th, "HOTF", levelText(pd.hotf), null, MenuUi.tone(0xFF81C784), meter(pd.hotf), 0xFF81C784);
        ui.tile(tx + 3 * (tw + gap), y2, tw, th, "Fairy souls", ofKnown(pd, GameProfile.FAIRY_SOULS, String.valueOf(pd.fairySouls)), null, Ui.PINK, -1, 0);
        ui.click(x, y, w, h, () -> ui.goTo(7));
        return h;
    }

    private static String levelText(Profile.Level l) {
        if (l == null) return "?";
        return l.progress() < 0 ? String.valueOf(l.level()) : l.level() + " · " + MenuUi.pct(l.progress());
    }

    private static double meter(Profile.Level l) {
        return l == null || l.progress() < 0 ? -1 : l.progress();
    }

    // "9/8/?/7/6/?" in the order the legend under it says
    private static String slayerLevels(Profile.Data d) {
        if (d.slayers.isEmpty()) return "?";
        StringBuilder b = new StringBuilder();
        for (String name : new String[]{"Revenant", "Tarantula", "Sven", "Voidgloom", "Inferno", "Riftstalker"}) {
            Profile.Level l = d.slayers.get(name);
            b.append(b.isEmpty() ? "" : "/").append(l == null ? "?" : String.valueOf(l.level()));
        }
        return b.toString();
    }

    // --- the page ---

    private static String statusShown = "";
    private static long statusAt;

    // next to the search box, like why /pv opened your own profile. shows for 10 sec
    private static int statusRow(MenuUi ui, int x, int y, int w) {
        String status = Profile.status;
        if (status == null || status.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        if (!status.equals(statusShown)) {
            statusShown = status;
            statusAt = now;
        } else if (now - statusAt > 10_000) {
            Profile.status = "";
            statusShown = "";
            return 0;
        }
        ui.edgeCard(x, y, w, 18, Ui.ORANGE);
        hintLine(ui, status, x + 8, y + 5, w - 14);
        return 18 + MenuUi.GAP;
    }

    // PagesMore.profile header + tabs
    static int page(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        int h = 60;
        ui.edgeCard(x, y, w, h, PROFILE);
        ui.gradH(x + 2, y + 1, w / 2, h - 2, Ui.alpha(MenuUi.tone(PROFILE), 0.14f), Ui.alpha(MenuUi.tone(PROFILE), 0.0f));
        final Profile.Data shown = d;
        ui.paint(x + 3, y + 2, 46, h - 4, (u, px, py, pw, ph) -> PlayerModel.draw(u, shown, px, py, pw, ph));
        ui.textScaled(d.player, x + 52, y + 5, 1.5f, Ui.TEXT, true);
        ui.text(d.profileName + (d.gameMode.isEmpty() ? "" : " · " + d.gameMode), x + 52, y + 18, Ui.MUTED);
        int lvl = d.sbLevel(), into = d.sbXp < 0 ? 0 : (int) (d.sbXp % 100);
        ui.text(lvl < 0 ? "Level ?" : "Level " + lvl, x + 52, y + 29, Ui.CYAN);
        if (lvl >= 0) ui.text(into + "/100 XP · " + into + "%", x + 52 + ui.width("Level " + lvl) + 6, y + 29, Ui.MUTED);
        ui.meter(x + 52, y + 40, 166, 3, into / 100.0, 0xFF5CD8FF);
        if (lvl < 0) ui.text(ui.ellipsize(GameProfile.hint(GameProfile.SB_XP), 166), x + 52, y + 47, Ui.DIM);
        int colW = 150, cr = x + w - 6, cl = cr - colW, ml = cl - 10 - colW;
        ui.row("Networth", ofKnown(d, GameProfile.NETWORTH, coins(d.networth())), cl, y + 5, colW, Ui.GREEN);
        ui.row("Purse", coinsOr(d.purse), cl, y + 18, colW, Ui.GOLD);
        ui.row("Bank", coinsOr(d.bank), cl, y + 31, colW, Ui.GOLD);
        ui.row("Magical Power", ofKnown(d, GameProfile.MAGICAL_POWER, MenuUi.num(d.magicalPower)), cl, y + 44, colW, MenuUi.tone(MenuUi.PURPLE));
        if (ml > x + 224) {
            ui.row("Skill average", d.skillAverage < 0 ? "?" : String.format(Locale.ROOT, "%.2f", d.skillAverage), ml, y + 5, colW, Ui.TEXT);
            ui.row("Catacombs", levelText(d.catacombs), ml, y + 18, colW, MenuUi.tone(DUNGEON));
            ui.row("Fairy souls", ofKnown(d, GameProfile.FAIRY_SOULS, String.valueOf(d.fairySouls)), ml, y + 31, colW, Ui.PINK);
        } else {
            ui.row("Skill avg", d.skillAverage < 0 ? "?" : String.format(Locale.ROOT, "%.2f", d.skillAverage), cl - 86, y + 5, 80, Ui.TEXT);
            ui.row("Cata", d.catacombs == null ? "?" : String.valueOf(d.catacombs.level()), cl - 86, y + 18, 80, MenuUi.tone(DUNGEON));
            ui.row("Souls", ofKnown(d, GameProfile.FAIRY_SOULS, String.valueOf(d.fairySouls)), cl - 86, y + 31, 80, Ui.PINK);
        }
        y += h + gap;
        y += statusRow(ui, x, y, w);
        y += PagesMore.subTabs(ui, x, y, w) + gap;
        y += switch (PagesMore.sub()) {
            case 1 -> networth(ui, x, y, w, d);
            case 2 -> skills(ui, x, y, w, d);
            case 3 -> slayers(ui, x, y, w, d);
            case 4 -> dungeons(ui, x, y, w, d);
            case 5 -> pets(ui, x, y, w, d);
            case 6 -> items(ui, x, y, w, d);
            case 7 -> accessories(ui, x, y, w, d);
            case 8 -> collections(ui, x, y, w, d);
            case 9 -> moreStats(ui, x, y, w, d);
            case 10 -> ProfileTrees.page(ui, x, y, w, d);
            default -> overview(ui, x, y, w, d);
        };
        return y - y0;
    }

    private static int overview(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y, gap = MenuUi.GAP;
        int col = (w - 2 * gap) / 3, h = 66;
        int x2 = x + col + gap, x3 = x2 + col + gap, c3 = w - 2 * (col + gap);

        int t = ui.card(x, y, col, h, "Heart of the Mountain", "iron_pickaxe", null, 0xFF4FD8C4);
        if (d.hotm != null) PagesMore.heroLevel(ui, x, y + t, col, d.hotm, "Tier ", 0xFF4FD8C4);
        else ui.textScaled("Tier ?", x + 6, y + t, 1.25f, Ui.DIM, true);
        if (d.knows(GameProfile.POWDER)) {
            ui.row("Mithril", coinsOr(d.mithrilPowder), x + 6, y + t + 17, col - 12, MenuUi.tone(0xFF5FEA8C));
            ui.row("Gemstone", coinsOr(d.gemstonePowder), x + 6, y + t + 27, col - 12, MenuUi.tone(0xFFEC7DD8));
            ui.row("Glacite", coinsOr(d.glacitePowder), x + 6, y + t + 37, col - 12, MenuUi.tone(0xFF9BE7FF));
        } else hintLine(ui, GameProfile.hint(GameProfile.POWDER), x + 6, y + t + 17, col - 12);

        t = ui.card(x2, y, col, h, "Heart of the Forest", "oak_sapling", null, 0xFF81C784);
        if (d.hotf != null) PagesMore.heroLevel(ui, x2, y + t, col, d.hotf, "Tier ", 0xFF81C784);
        else {
            ui.textScaled("Tier ?", x2 + 6, y + t, 1.25f, Ui.DIM, true);
            hintLine(ui, GameProfile.hint(GameProfile.HOTF), x2 + 6, y + t + 17, col - 12);
        }

        t = ui.card(x3, y, c3, h, "Crimson Isle", "blaze_powder", null, 0xFFFF6B4A);
        if (d.knows(GameProfile.FACTION)) {
            ui.textScaled(d.faction, x3 + 6, y + t, 1.25f, Ui.ORANGE, true);
            if (d.magesRep > 0 || d.faction.equals("Mage")) ui.row("Mage rep", MenuUi.num(d.magesRep), x3 + 6, y + t + 17, c3 - 12, MenuUi.tone(0xFF7FB3FF));
            if (d.barbRep > 0 || d.faction.equals("Barbarian")) ui.row("Barbarian rep", MenuUi.num(d.barbRep), x3 + 6, y + t + 27, c3 - 12, MenuUi.tone(0xFFFF6B4A));
        } else {
            ui.textScaled("Faction ?", x3 + 6, y + t, 1.25f, Ui.DIM, true);
            hintLine(ui, GameProfile.hint(GameProfile.FACTION), x3 + 6, y + t + 17, c3 - 12);
        }
        y += h + gap;

        t = ui.card(x, y, col, h, "Magical Power", "enchanted_book", null, MenuUi.PURPLE);
        if (d.knows(GameProfile.MAGICAL_POWER)) {
            ui.textScaled(MenuUi.num(d.magicalPower), x + 6, y + t, 1.5f, MenuUi.tone(MenuUi.PURPLE), true);
            ui.text("As your bag says it", x + 6, y + t + 17, Ui.DIM);
        } else {
            ui.textScaled("?", x + 6, y + t, 1.5f, Ui.DIM, true);
            hintLine(ui, GameProfile.hint(GameProfile.MAGICAL_POWER), x + 6, y + t + 17, col - 12);
        }

        // the game never shows crafted minions as a number, so we show what we track
        t = ui.card(x2, y, col, h, "Minions", "hopper", null, 0xFFFFB454);
        ui.textScaled(Store.I.minions.size() + " tracked", x2 + 6, y + t, 1.25f, Ui.TEXT, true);
        ui.text("Minions the mod has seen", x2 + 6, y + t + 17, Ui.DIM);

        t = ui.card(x3, y, c3, h, "Pets", "bone", null, 0xFFFFB454);
        if (d.knows(GameProfile.PETS)) {
            double petWorth = d.pets.stream().mapToDouble(Profile.Pet::value).sum();
            ui.textScaled(d.pets.size() + (d.pets.size() == 1 ? " pet" : " pets"), x3 + 6, y + t, 1.25f, Ui.TEXT, true);
            Profile.Pet active = d.pets.stream().filter(Profile.Pet::active).findFirst().orElse(null);
            if (active != null) ui.row("Active", "[" + active.level() + "] " + active.name(), x3 + 6, y + t + 17, c3 - 12, Ui.MUTED, MenuUi.tone(PagesMore.rarity(active.tier())));
            if (petWorth > 0) ui.row("Worth", coins(petWorth), x3 + 6, y + t + 29, c3 - 12, Ui.GOLD);
        } else {
            ui.textScaled("Pets ?", x3 + 6, y + t, 1.25f, Ui.DIM, true);
            hintLine(ui, GameProfile.hint(GameProfile.PETS), x3 + 6, y + t + 17, c3 - 12);
        }
        y += h + gap;

        // catacombs instead of the api kills/deaths, the game shows those nowhere
        t = ui.card(x, y, col, h, "Catacombs", "skeleton_skull", null, DUNGEON);
        if (d.catacombs != null) PagesMore.heroLevel(ui, x, y + t, col, d.catacombs, "Level ", DUNGEON);
        else {
            ui.textScaled("Level ?", x + 6, y + t, 1.25f, Ui.DIM, true);
            hintLine(ui, GameProfile.hint(GameProfile.CATACOMBS), x + 6, y + t + 17, col - 12);
        }
        if (d.catacombs != null && !d.selectedClass.isEmpty()) ui.row("Class", d.selectedClass, x + 6, y + t + 17, col - 12, Ui.TEXT);

        t = ui.card(x2, y, col, h, "Slayers", "rotten_flesh", null, 0xFFFF5252);
        ui.textScaled(slayerLevels(d), x2 + 6, y + t, 1.25f, d.slayers.isEmpty() ? Ui.DIM : Ui.TEXT, true);
        if (d.slayers.isEmpty()) hintLine(ui, GameProfile.hint(GameProfile.SLAYERS), x2 + 6, y + t + 17, col - 12);
        else {
            ui.text(ui.ellipsize("Rev/Tara/Sven/Void/Inferno/Rift", col - 12), x2 + 6, y + t + 17, Ui.DIM);
            ui.tip(x2, y + t + 16, col, 10, "Revenant/Tara/Sven/Void/Inferno/Rift");
        }

        t = ui.card(x3, y, c3, h, "Networth", "gold_ingot", null, NETWORTH);
        if (d.knows(GameProfile.NETWORTH)) {
            ui.textScaled(coins(d.networth()), x3 + 6, y + t, 1.5f, Ui.GOLD, true);
            ui.text("Items, pets, sacks, essence, coins", x3 + 6, y + t + 17, Ui.DIM);
        } else {
            ui.textScaled(d.networth() > 0 ? coins(d.networth()) + " so far" : "?", x3 + 6, y + t, 1.25f, Ui.DIM, true);
            hintLine(ui, GameProfile.hint(GameProfile.NETWORTH), x3 + 6, y + t + 17, c3 - 12);
        }
        double coins = Math.max(0, d.purse) + Math.max(0, d.bank);
        if (d.networth() > 0 && d.knows(GameProfile.NETWORTH)) ui.row("In coins", coins(coins) + " · " + MenuUi.pct(coins / d.networth()), x3 + 6, y + t + 29, c3 - 12, Ui.GOLD);
        y += h + gap;

        if (!d.skills.isEmpty()) y += PagesMore.levelGrid(ui, x, y, w, d.skills, true);
        y += stillToOpen(ui, x, y, w, d, GameProfile.CAN_READ);
        return y - y0;
    }

    private static int networth(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y;
        y += stillToOpen(ui, x, y, w, d, GameProfile.PETS, GameProfile.ACCESSORIES, GameProfile.SACKS, GameProfile.STORAGE, GameProfile.EQUIPMENT, GameProfile.ESSENCE);
        y += PagesMore.networth(ui, x, y, w, d);
        return y - y0;
    }

    private static int skills(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.skills.isEmpty()) return hintCard(ui, x, y, w, "Skills", "experience_bottle", 0xFF9CE37D, GameProfile.SKILLS);
        int y0 = y;
        y += PagesMore.skills(ui, x, y, w, d);
        if (d.skills.size() < Profile.SKILLS.length) y += hintCard(ui, x, y, w, d.skills.size() + " of " + Profile.SKILLS.length + " skills", "spyglass", Ui.ORANGE, GameProfile.SKILLS);
        return y - y0;
    }

    private static int slayers(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.slayers.isEmpty()) return hintCard(ui, x, y, w, "Slayers", "iron_sword", 0xFFFF5252, GameProfile.SLAYERS);
        int y0 = y;
        y += PagesMore.slayers(ui, x, y, w, d);
        if (d.slayers.size() < 6) y += hintCard(ui, x, y, w, d.slayers.size() + " of 6 slayers", "spyglass", Ui.ORANGE, GameProfile.SLAYERS);
        return y - y0;
    }

    private static int dungeons(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.catacombs == null && d.classes.isEmpty() && d.bossKills.isEmpty())
            return hintCard(ui, x, y, w, "Dungeons", "skeleton_skull", DUNGEON, GameProfile.CATACOMBS, GameProfile.CLASSES, GameProfile.FLOORS);
        int y0 = y, gap = MenuUi.GAP;
        if (d.catacombs != null) {
            Profile.Level c = d.catacombs;
            int h = 46;
            int top = ui.card(x, y, w, h, "The Catacombs", "skeleton_skull", d.selectedClass.isEmpty() ? null : d.selectedClass, DUNGEON);
            ui.textScaled("Level " + c.level(), x + 6, y + top, 2f, Ui.TEXT, true);
            if (c.progress() >= 0) {
                ui.textRight(MenuUi.pct(c.progress()), x + w - 6, y + top + 1, Ui.CYAN);
                ui.meter(x + 6, y + top + 21, w - 12, 4, c.progress(), DUNGEON);
            }
            y += h + gap;
        }
        if (!d.classes.isEmpty()) {
            int rows = (d.classes.size() + 1) / 2, ch = MenuUi.TOP + Math.max(1, rows) * 35 + 1;
            int ctop = ui.card(x, y, w, ch, "Classes", "bow", d.classes.size() + " classes", 0xFF7FB3FF);
            int col = (w - 8 - 3) / 2, i = 0;
            for (Map.Entry<String, Profile.Level> e : d.classes.entrySet()) {
                PagesMore.levelTile(ui, x + 4 + (i % 2) * (col + 3), y + ctop + (i / 2) * 35, col, e.getKey(), e.getValue(), 50, PagesMore.classIcon(e.getKey()), PagesMore.classColor(e.getKey()));
                i++;
            }
            y += ch + gap;
        }
        if (!d.bossKills.isEmpty()) {
            int total = d.bossKills.values().stream().mapToInt(Integer::intValue).sum();
            int perRow = Math.max(4, (w - 8) / 72), tileW = (w - 8 - (perRow - 1) * 3) / perRow, n = d.bossKills.size();
            int rows = (n + perRow - 1) / perRow, fh = MenuUi.TOP + rows * 29 + 2;
            int ftop = ui.card(x, y, w, fh, "Boss kills", "chest", MenuUi.num(total) + " kills", DUNGEON);
            int i = 0;
            for (Map.Entry<String, Integer> e : d.bossKills.entrySet()) {
                int fx = x + 4 + (i % perRow) * (tileW + 3), fy = y + ftop + (i / perRow) * 29;
                double share = total <= 0 ? 0 : (double) e.getValue() / total;
                ui.tile(fx, fy, tileW, 26, e.getKey().replace("F0", "Entrance"), MenuUi.num(e.getValue()) + " · " + MenuUi.pct(share), null, MenuUi.tone(0xFF5CD8FF), share, 0xFF5CD8FF);
                i++;
            }
            y += fh + gap;
        }
        y += stillToOpen(ui, x, y, w, d, GameProfile.CATACOMBS, GameProfile.CLASSES, GameProfile.FLOORS);
        return y - y0;
    }

    private static int pets(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.pets.isEmpty()) return hintCard(ui, x, y, w, "Pets", "bone", 0xFFFFB454, GameProfile.PETS);
        return PagesMore.pets(ui, x, y, w, d);
    }

    private static int items(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int y0 = y;
        y += stillToOpen(ui, x, y, w, d, GameProfile.STORAGE, GameProfile.EQUIPMENT, GameProfile.ACCESSORIES);
        if (d.inventories.values().stream().allMatch(list -> list.stream().allMatch(it -> it == null))) return y - y0;
        y += PagesMore.items(ui, x, y, w, d);
        return y - y0;
    }

    private static int accessories(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (!d.inventories.containsKey("Accessories")) return hintCard(ui, x, y, w, "Accessories", "enchanted_book", 0xFFB388FF, GameProfile.ACCESSORIES, GameProfile.MAGICAL_POWER);
        int y0 = y;
        y += stillToOpen(ui, x, y, w, d, GameProfile.MAGICAL_POWER);
        y += PagesMore.accessories(ui, x, y, w, d);
        return y - y0;
    }

    private static int collections(MenuUi ui, int x, int y, int w, Profile.Data d) {
        if (d.collections.isEmpty()) return hintCard(ui, x, y, w, "Collections", "chest", 0xFFD8B26E, GameProfile.COLLECTIONS);
        return PagesMore.collections(ui, x, y, w, d);
    }

    // PagesMore.moreStats cards with only what the game shows. not read = "?", the list under says where
    private static int moreStats(MenuUi ui, int x, int y, int w, Profile.Data d) {
        int gap = MenuUi.GAP, col = (w - gap) / 2;
        List<String[]> general = new ArrayList<>();
        general.add(PagesMore.stat("Skill average", d.skillAverage < 0 ? "?" : String.format(Locale.ROOT, "%.2f", d.skillAverage), Ui.TEXT));
        general.add(PagesMore.stat("Magical Power", ofKnown(d, GameProfile.MAGICAL_POWER, MenuUi.num(d.magicalPower)), MenuUi.tone(MenuUi.PURPLE)));
        general.add(PagesMore.stat("Bestiary milestone", d.bestiaryMilestone < 0 ? "?" : String.valueOf(d.bestiaryMilestone), Ui.TEXT));
        general.add(PagesMore.stat("Fairy souls, on islands seen", ofKnown(d, GameProfile.FAIRY_SOULS, String.valueOf(d.fairySouls)), Ui.PINK));

        List<String[]> mining = new ArrayList<>();
        mining.add(PagesMore.stat("HOTM", d.hotm == null ? "?" : "Tier " + d.hotm.level(), Ui.CYAN));
        mining.add(PagesMore.stat("HOTF", d.hotf == null ? "?" : "Tier " + d.hotf.level(), Ui.GREEN));
        mining.add(PagesMore.stat("Mithril powder", coinsOr(d.knows(GameProfile.POWDER) ? d.mithrilPowder : -1), MenuUi.tone(0xFF5FEA8C)));
        mining.add(PagesMore.stat("Gemstone powder", coinsOr(d.knows(GameProfile.POWDER) ? d.gemstonePowder : -1), MenuUi.tone(0xFFEC7DD8)));
        mining.add(PagesMore.stat("Glacite powder", coinsOr(d.knows(GameProfile.POWDER) ? d.glacitePowder : -1), MenuUi.tone(0xFF9BE7FF)));

        List<String[]> world = new ArrayList<>();
        world.add(PagesMore.stat("Faction", ofKnown(d, GameProfile.FACTION, d.faction), Ui.ORANGE));
        world.add(PagesMore.stat("Mage reputation", d.knows(GameProfile.FACTION) && (d.magesRep > 0 || d.faction.equals("Mage")) ? MenuUi.num(d.magesRep) : "?", MenuUi.tone(0xFF7FB3FF)));
        world.add(PagesMore.stat("Barbarian reputation", d.knows(GameProfile.FACTION) && (d.barbRep > 0 || d.faction.equals("Barbarian")) ? MenuUi.num(d.barbRep) : "?", MenuUi.tone(0xFFFF6B4A)));
        world.add(PagesMore.stat("Rift motes", ofKnown(d, GameProfile.MOTES, coins(d.motes)), Ui.PINK));
        world.add(PagesMore.stat("Enigma souls", ofKnown(d, GameProfile.ENIGMA, String.valueOf(d.enigmaSouls)), MenuUi.tone(MenuUi.PURPLE)));
        int fish = d.trophyFish.values().stream().mapToInt(Integer::intValue).sum();
        world.add(PagesMore.stat("Trophy fish caught", d.knows(GameProfile.TROPHY_FISH) ? coins(fish) + " (" + d.trophyFish.size() + " kinds)" : "?", MenuUi.tone(FISH)));

        List<String[]> economy = new ArrayList<>();
        economy.add(PagesMore.stat("Copper", ofKnown(d, GameProfile.COPPER, coins(d.copper)), MenuUi.tone(0xFFE0904A)));
        economy.add(PagesMore.stat("Jacob medals", d.medals.isEmpty() ? "?" : d.medals.entrySet().stream().map(e -> e.getValue() + " " + e.getKey()).reduce((a, b) -> a + ", " + b).orElse(""), Ui.GOLD));

        List<String[]> essence = new ArrayList<>();
        for (Map.Entry<String, Long> e : d.essence.entrySet()) essence.add(PagesMore.stat(e.getKey() + " essence", coins(e.getValue()), MenuUi.tone(0xFFB388FF)));

        int y1 = y, y2 = y;
        y1 += PagesMore.statCard(ui, x, y1, col, "Profile", "player_head", PROFILE, general) + gap;
        y2 += PagesMore.statCard(ui, x + col + gap, y2, w - col - gap, "Crimson Isle, Rift and fishing", "blaze_powder", 0xFFFF6B4A, world) + gap;
        y1 += PagesMore.statCard(ui, x, y1, col, "Mining and forest", "iron_pickaxe", 0xFF4FD8C4, mining) + gap;
        y2 += PagesMore.statCard(ui, x + col + gap, y2, w - col - gap, "Economy", "gold_ingot", NETWORTH, economy) + gap;
        if (!essence.isEmpty()) y1 += PagesMore.statCard(ui, x, y1, col, "Essence", "nether_star", 0xFFB388FF, essence) + gap;
        int y3 = Math.max(y1, y2);
        y3 += stillToOpen(ui, x, y3, w, d, GameProfile.CAN_READ);
        return y3 - y;
    }
}
