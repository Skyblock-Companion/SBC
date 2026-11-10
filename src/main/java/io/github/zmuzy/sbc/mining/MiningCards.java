// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.mining.MiningCard.Body;
import io.github.zmuzy.sbc.mining.MiningCard.Line;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.hud.RowText;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what each mining card shows. each one is a pure function from what we know to lines, used for the live card
// and the sample
// unknown numbers are a dash or no line at all. logic stays in Commissions, MiningEvents, Powder, Mineshaft,
// MiningExtras
final class MiningCards {
    private static final int MITHRIL = 0xFF4FD08A, GEMSTONE = 0xFFFF7AC6, GLACITE = 0xFF8FD3FF, LIME = 0xFFB6F27A;
    private static final int PURPLE = 0xFFC77DFF, BLUE = 0xFF7FA6FF, PINK = 0xFFFF7AE0, TEAL = 0xFF4FD8C4, SAND = 0xFFD8B26E, ICE = 0xFF7FB3FF;
    private static final String DASH = "—";
    private static final Pattern PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final Pattern AMOUNT = Pattern.compile("^(.*\\S)\\s+x(\\d[\\d,]*)$");
    private MiningCards() {}

    // numbers and colors

    private static String percent(double fraction) {
        return RowText.percent(Math.max(0, Math.min(1, fraction)));
    }

    // for stuff counting down: a percent that drops its decimals now and then would make the numbers next to it jump
    private static String wholePercent(double fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 100) + "%";
    }

    private static String fraction(String now, String of) {
        return Modern.fraction(now, of, 'b', 'f');
    }

    // "8,560", "34.5k", "498k", "1.9M". a right aligned column of rates has to read at a glance. dash till there is a rate
    private static String hourly(double perHour) {
        if (perHour <= 0) return DASH;
        long n = Math.round(perHour);
        if (n >= 999_500) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        if (n >= 100_000) return String.format(Locale.ROOT, "%.0fk", n / 1e3);
        if (n >= 10_000) return String.format(Locale.ROOT, "%.1fk", n / 1e3);
        return MiningEnv.sep(n);
    }

    // "1,144/h", or a dash if not known yet
    private static String hourlyCell(double perHour) {
        return perHour > 0 ? hourly(perHour) + "/h" : DASH;
    }

    private static String big(long n) {
        if (n >= 10_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        return n >= 1_000_000 ? String.format(Locale.ROOT, "%.2fM", n / 1e6) : MiningEnv.sep(n);
    }

    // avg of something per chest
    private static String perChest(long n, long chests) {
        if (chests <= 0) return DASH;
        double v = n / (double) chests;
        if (n > 0 && v < 0.01) return "<0.01";
        return String.format(Locale.ROOT, v >= 100 ? "%,.0f" : v >= 10 ? "%.1f" : "%.2f", v);
    }

    // same colors as the game codes, the ones you cant read on the glass get brightened
    private static int codeColor(String coloured) {
        char c = coloured.length() > 1 && coloured.charAt(0) == '§' ? coloured.charAt(1) : 'f';
        return switch (c) {
            case '1', '9' -> BLUE;
            case '2', 'a' -> Modern.GOOD;
            case '3' -> TEAL;
            case 'b' -> Modern.AQUA;
            case '4', 'c' -> Modern.BAD;
            case '5' -> PURPLE;
            case 'd' -> PINK;
            case '6' -> Modern.GOLD;
            case 'e' -> 0xFFFFF176;
            case '0', '7', '8' -> Modern.LABEL;
            default -> Modern.INK;
        };
    }

    private static String bright(String coloured) {
        return coloured.replace("§0", "§7").replace("§1", "§b").replace("§9", "§b").replace("§2", "§a").replace("§5", "§d").replace("§8", "§7");
    }

    // commissions

    // long name with numbers and a percent next to it. rare, worth the width
    static final int WIDE = 260;

    private static String commissionIcon(String name) {
        String n = Mining.lower(name);
        if (n.contains("slayer") || n.contains("puncher") || n.contains("walker")) return "iron_sword";
        if (n.contains("mithril")) return "prismarine_crystals";
        if (n.contains("titanium")) return "polished_diorite";
        if (n.contains("powder")) return "glowstone_dust";
        if (n.contains("raffle")) return "name_tag";
        if (n.contains("goblin")) return "gold_nugget";
        if (n.contains("glacite")) return "packed_ice";
        if (n.contains("tungsten")) return "flint";
        if (n.contains("umber")) return "brick";
        if (n.contains("corpse")) return "bone";
        if (n.contains("gemstone") || n.contains("crystal")) return "amethyst_shard";
        if (n.contains("chest")) return "chest";
        if (n.contains("miner") || n.contains("mine")) return "iron_pickaxe";
        return "paper";
    }

    private static Line commission(Commissions.Commission c) {
        Line l = Line.item(commissionIcon(c.name()), c.name()).brief(c.name().replace("Team Treasurite Member", "Treasurite").replace("Treasure Hoarder", "Hoarder"));
        if (c.done()) return l.chip("DONE", Modern.GOOD).meter(1, Modern.GOOD);
        Matcher f = Commissions.FRACTION.matcher(c.progress());
        boolean known = f.find(), fromMenu = false;
        if (!known && c.detail() != null) {
            f = Commissions.FRACTION.matcher(c.detail());
            known = f.find();
            fromMenu = known;
        }
        String numbers = "";
        double p = -1;
        long need = 0;
        if (known) {
            need = MiningEnv.parseLong(f.group("need"));
            numbers = fraction(f.group("have"), f.group("need"));
            if (need > 0) p = MiningEnv.parseLong(f.group("have")) / (double) need;
        }
        Matcher pc = PERCENT.matcher(c.progress());
        if (pc.find()) {
            double live = Double.parseDouble(pc.group(1)) / 100;
            // menu numbers are from the last time it was opened, tab percent is now. once they disagree only the target still holds
            if (fromMenu && need > 0 && Math.abs(p - live) > 0.01 + 1.0 / need) numbers = "§7/§f" + f.group("need");
            p = live;
        }
        if (p < 0) return l.cells("§e" + c.progress());
        int color = p < 1 / 3d ? Modern.WARN : p < 2 / 3d ? Modern.GOLD : LIME;
        return l.cells(numbers, percent(p)).colors(0, color).meter(p, color);
    }

    static Body commissions(List<Commissions.Commission> all) {
        Body b = new Body();
        int done = 0;
        for (Commissions.Commission c : all) {
            if (c.done()) done++;
            b.add(commission(c));
        }
        if (!all.isEmpty()) b.right("§a" + done + "§7/§f" + all.size() + "  §7" + percent(done / (double) all.size()));
        return b;
    }

    static HudElement commissions() {
        return new MiningCard("mining_commissions", "Commissions", 31, "mining_commissions") {
            @Override public boolean visible() { return !Commissions.current().isEmpty(); }
            @Override int maxWidth() { return WIDE; }
            @Override Body live() { return commissions(Commissions.current()); }

            @Override Body sample() {
                return commissions(List.of(new Commissions.Commission("Mithril Miner", "45%", false),
                        new Commissions.Commission("Goblin Slayer", "DONE", true),
                        new Commissions.Commission("Royal Mines Titanium", "12%", false, "6/50"),
                        new Commissions.Commission("Team Treasurite Member Slayer", "72%", false)));
            }

        };
    }

    // the running event

    private static String eventIcon(MiningEvents.Type t) {
        return switch (t) {
            case GOBLIN_RAID -> "iron_sword";
            case RAFFLE -> "name_tag";
            case MITHRIL_GOURMAND -> "prismarine_crystals";
            case BETTER_TOGETHER -> "totem_of_undying";
            case GONE_WITH_THE_WIND -> "feather";
            case DOUBLE_POWDER -> "glowstone_dust";
        };
    }

    static Body event(MiningEvents.Type type, long left) {
        Body b = new Body();
        boolean running = left > 0;
        int color = codeColor(type.color);
        b.add(Line.item(eventIcon(type), type.displayName).labelColor(color).chip(running ? "ACTIVE" : "ENDED?", running ? Modern.GOOD : Modern.DIM));
        if (!running) return b.add(Line.row("Time left", "§7" + DASH));
        String of = left <= type.duration ? "§7/§f" + MiningEnv.format(type.duration) : "";
        double p = left / (double) type.duration;
        return b.add(Line.row("Time left", "§e" + MiningEnv.format(left) + of, wholePercent(p)).colors(0, Modern.LABEL).meter(p, color).thick());
    }

    static HudElement event() {
        return new MiningCard("mining_event", "Mining event", 32, "mining_event") {
            @Override public boolean visible() { return MiningExtras.mining() && MiningEvents.displayLine() != null; }

            @Override Body live() {
                MiningEvents.Active e = MiningEvents.getActiveEvent(null);
                return e == null ? new Body() : event(e.type, e.timeLeft());
            }

            @Override Body sample() { return event(MiningEvents.Type.DOUBLE_POWDER, 760_000); }

        };
    }

    // powder this session

    record Gain(String name, String icon, int color, long gained, double perHour, long have, long spent) {}

    private static Gain gain(Powder.Type t, String icon, int color) {
        long gained = Powder.sessionGained.getOrDefault(t, 0L);
        return new Gain(t.displayName, icon, color, gained, MiningExtras.hourly(gained), t.current(), Powder.sessionSpent.getOrDefault(t, 0L));
    }

    static Body powder(List<Gain> powders, long chests, long doubleLeft) {
        Body b = new Body();
        if (doubleLeft > 0) b.right("§b2x Powder §e" + MiningEnv.format(doubleLeft)).briefRight("§b2x §e" + MiningEnv.format(doubleLeft));
        b.add(Line.head("", "Gained", "/hour", "Have").briefCells("Gain", "/h", "Have"));
        long sum = 0;
        double sumRate = 0;
        int gaining = 0;
        for (Gain g : powders) {
            if (g.gained() <= 0) continue;
            gaining++;
            sum += g.gained();
            sumRate += Math.max(0, g.perHour());
            b.add(Line.item(g.icon(), g.name(), "+" + MiningEnv.sep(g.gained()), hourly(g.perHour()), g.have() > 0 ? big(g.have()) : DASH)
                    .labelColor(g.color()).colors(g.color(), Modern.LABEL, Modern.LABEL));
        }
        if (gaining > 1) b.add(Line.rule(), Line.row("Total", "+" + MiningEnv.sep(sum), hourly(sumRate), "").colors(Modern.INK, Modern.LABEL, 0));
        List<Line> below = new ArrayList<>();
        for (Gain g : powders) if (g.spent() > 0) below.add(Line.row("Spent " + g.name(), "-" + MiningEnv.sep(g.spent())).colors(Modern.BAD).brief("Spent " + briefPowder(g.name())));
        if (chests > 0) below.add(Line.row("Chests", MiningEnv.sep(chests)));
        if (!below.isEmpty()) {
            b.add(Line.rule());
            below.forEach(b::add);
        }
        return b;
    }

    private static String briefPowder(String name) {
        return name.equals("Mithril") ? "Mith" : name.equals("Gemstone") ? "Gem" : name;
    }

    static HudElement powder() {
        return new MiningCard("mining_powder", "Powder this session", 33, "mining_powder") {
            @Override public String heading() { return compact() ? "Session powder" : super.heading(); }

            @Override public boolean visible() {
                long[] p = MiningExtras.powder();
                return MiningExtras.mining() && (p[0] + p[1] + p[2] > 0);
            }

            @Override Body live() {
                MiningEvents.Active doubled = MiningEvents.getActiveEvent(MiningEvents.Type.DOUBLE_POWDER);
                return powder(List.of(gain(Powder.Type.MITHRIL, "prismarine_crystals", MITHRIL), gain(Powder.Type.GEMSTONE, "amethyst_shard", GEMSTONE), gain(Powder.Type.GLACITE, "packed_ice", GLACITE)),
                        Powder.totalChestPicked, doubled == null ? 0 : doubled.timeLeft());
            }

            @Override Body sample() {
                return powder(List.of(new Gain("Mithril", "prismarine_crystals", MITHRIL, 124_400, 498_000, 14_842_107, 0),
                        new Gain("Gemstone", "amethyst_shard", GEMSTONE, 186_420, 745_680, 3_120_884, 12_000),
                        new Gain("Glacite", "packed_ice", GLACITE, 8_620, 34_480, 912_040, 0)), 148, 760_000);
            }

        };
    }

    // powder tracker

    record Tracked(String icon, String label, long amount, double perHour, int color) {}

    record Tracker(long chests, double chestsPerHour, long hardStone, double hardStonePerHour, long lastChestAgo, boolean grinding, long doubleLeft, List<Tracked> rewards, Map<String, long[]> gems, Map<Powder.Reward, Long> drops) {}

    private static final String[][] GEMS = {{"Ruby", "§c", "redstone"}, {"Sapphire", "§b", "lapis_lazuli"}, {"Amber", "§6", "resin_clump"}, {"Amethyst", "§5", "amethyst_shard"}, {"Jade", "§a", "emerald"}, {"Topaz", "§e", "honeycomb"}};
    private static final int[] GEM_TIERS = {PURPLE, BLUE, Modern.GOOD, Modern.INK};

    private static String dropIcon(Powder.Reward r) {
        return switch (r) {
            case FTX_3070, ELECTRON_TRANSIMTTER, ROBOTRON_REFLECTOR, SUPERLITE_MOTOR, CONTROL_SWITCH, SYNTHETIC_HEART -> "comparator";
            case GOBLIN_EGG, GREEN_GOBLIN_EGG, RED_GOBLIN_EGG, YELLOW_GOBLIN_EGG, BLUE_GOBLIN_EGG -> "egg";
            case WISHING_COMPASS -> "compass";
            case SLUDGE_JUICE -> "slime_ball";
            case ASCENSION_ROPE -> "lead";
            case TREASURITE -> "raw_gold";
            case JUNGLE_HEART -> "heart_of_the_sea";
            case PICKONIMBUS_2000 -> "diamond_pickaxe";
            case YOGGIE -> "beef";
            case PREHISTORIC_EGG -> "turtle_egg";
            case OIL_BARREL -> "coal";
            default -> "paper";
        };
    }

    // drops that matter go on top. inside a rarity they keep a fixed order so no row hops around while numbers grow
    private static int rarity(Powder.Reward r) {
        char code = r.displayName.charAt(1);
        int i = "659a".indexOf(code);
        return i >= 0 ? i : code == 'f' ? 4 : 3;
    }

    static Body tracker(Tracker t) {
        Body b = new Body();
        if (t.grinding()) b.right("§aGrinding");
        b.add(Line.row("Total Chests Picked", MiningEnv.sep(t.chests()), hourlyCell(t.chestsPerHour())).labelColor(0xFFFF9BEF).colors(Modern.INK, Modern.LABEL).brief("Chests"));
        b.add(Line.row("Hard Stone Compacted", MiningEnv.sep(t.hardStone()), hourlyCell(t.hardStonePerHour())).colors(Modern.INK, Modern.LABEL).brief("Hard Stone"));
        if (t.lastChestAgo() >= 0) b.add(Line.row("Last chest", (t.grinding() ? "§a" : "§7") + MiningEnv.format(t.lastChestAgo()) + " ago").brief("Last").beside("Total Chests Picked"));
        Line doubled = Line.row("Double Powder");
        b.add(t.doubleLeft() > 0 ? doubled.cells("§e" + MiningEnv.format(t.doubleLeft())).chip("ACTIVE", Modern.GOOD) : doubled.chip("INACTIVE", Modern.DIM));
        b.add(Line.head("Rewards", "Total", "/hour", "/chest").briefCells("Total", "/h", "/chest"));
        for (Tracked r : t.rewards())
            b.add(Line.item(r.icon(), r.label(), big(r.amount()), hourly(r.perHour()), perChest(r.amount(), t.chests())).labelColor(r.color()).colors(Modern.INK, Modern.LABEL, Modern.LABEL).brief(r.label().replace(" Powder", "").replace(" Essence", "")));
        if (!t.gems().isEmpty()) {
            b.add(Line.head("Gemstones", "Flawless", "Fine", "Flawed", "Rough").colors(GEM_TIERS).brief("Gems").briefCells("Flawl", "Fine", "Flawd", "Rough"));
            for (String[] g : GEMS) {
                long[] c = t.gems().get(g[0]);
                if (c == null) continue;
                b.add(Line.item(g[2], g[0], MiningEnv.sep(c[0]), MiningEnv.sep(c[1]), MiningEnv.sep(c[2]), MiningEnv.sep(c[3])).labelColor(codeColor(g[1]))
                        .colors(c[0] > 0 ? GEM_TIERS[0] : Modern.DIM, c[1] > 0 ? GEM_TIERS[1] : Modern.DIM, c[2] > 0 ? GEM_TIERS[2] : Modern.DIM, c[3] > 0 ? GEM_TIERS[3] : Modern.DIM).brief(g[0].replace("Sapphire", "Sapph").replace("Amethyst", "Ameth")));
            }
        }
        if (!t.drops().isEmpty()) {
            b.add(Line.head("Drops", "Total", "/chest"));
            List<Map.Entry<Powder.Reward, Long>> drops = new ArrayList<>(t.drops().entrySet());
            drops.sort(Comparator.comparingInt(e -> rarity(e.getKey())));
            for (Map.Entry<Powder.Reward, Long> e : drops) {
                String name = e.getKey().displayName;
                b.add(Line.item(dropIcon(e.getKey()), RowText.plain(name), MiningEnv.sep(e.getValue()), perChest(e.getValue(), t.chests())).labelColor(codeColor(name)).colors(Modern.INK, Modern.LABEL)
                        .brief(RowText.plain(name).replace("Transmitter", "Trans.").replace("Reflector", "Refl.")));
            }
        }
        return b;
    }

    private static List<Tracked> trackedRewards(long gem, double gemRate, long diamond, double diamondRate, long gold, double goldRate) {
        return List.of(new Tracked("amethyst_shard", "Gemstone Powder", gem, gemRate, GEMSTONE),
                new Tracked("diamond", "Diamond Essence", diamond, diamondRate, Modern.AQUA),
                new Tracked("gold_ingot", "Gold Essence", gold, goldRate, Modern.GOLD));
    }

    static HudElement tracker() {
        return new MiningCard("mining_powder_tracker", "Powder Tracker", 35, "mining_powder_tracker") {
            @Override public boolean visible() {
                return Powder.trackerEnabled() && (!Mining.b("mining_powder_tracker", "onlyWhenPowderGrinding", false) || Powder.isGrinding());
            }

            @Override Body live() {
                MiningEvents.Active doubled = MiningEvents.getActiveEvent(MiningEvents.Type.DOUBLE_POWDER);
                Map<String, long[]> gems = new LinkedHashMap<>();
                for (String[] g : GEMS) {
                    long[] c = Powder.gemstone(g[0]);
                    if (c != null) gems.put(g[0], c);
                }
                Map<Powder.Reward, Long> drops = new LinkedHashMap<>();
                for (Powder.Reward r : Powder.Reward.values()) {
                    long n = Powder.rewards.getOrDefault(r, 0L);
                    if (n > 0 && r != Powder.Reward.GEMSTONE_POWDER && r != Powder.Reward.DIAMOND_ESSENCE && r != Powder.Reward.GOLD_ESSENCE && !r.name().endsWith("_GEMSTONE")) drops.put(r, n);
                }
                return tracker(new Tracker(Powder.totalChestPicked, Powder.chestInfo.perHour, Powder.totalHardStoneCompacted, Powder.hardStoneInfo.perHour, Powder.sinceLastChest(), Powder.isGrinding(), doubled == null ? 0 : doubled.timeLeft(),
                        trackedRewards(Powder.rewards.getOrDefault(Powder.Reward.GEMSTONE_POWDER, 0L), Powder.gemstoneInfo.perHour, Powder.rewards.getOrDefault(Powder.Reward.DIAMOND_ESSENCE, 0L), Powder.diamondEssenceInfo.perHour,
                                Powder.rewards.getOrDefault(Powder.Reward.GOLD_ESSENCE, 0L), Powder.goldEssenceInfo.perHour), gems, drops));
            }

            @Override Body sample() {
                Map<String, long[]> gems = new LinkedHashMap<>();
                gems.put("Ruby", new long[]{0, 1, 42, 12});
                gems.put("Amber", new long[]{1, 3, 0, 77});
                gems.put("Topaz", new long[]{0, 0, 61, 40});
                Map<Powder.Reward, Long> drops = new LinkedHashMap<>();
                drops.put(Powder.Reward.ELECTRON_TRANSIMTTER, 3L);
                drops.put(Powder.Reward.GOBLIN_EGG, 6L);
                drops.put(Powder.Reward.WISHING_COMPASS, 11L);
                drops.put(Powder.Reward.TREASURITE, 31L);
                return tracker(new Tracker(148, 592, 2_140, 8_560, 9_000, true, 760_000, trackedRewards(186_420, 745_680, 412, 1_648, 286, 1_144), gems, drops));
            }

        };
    }

    // corpses

    record Seen(String name, boolean looted) {}

    private static String corpseIcon(Mineshaft.Corpse c) {
        if (c == null) return "bone";
        return switch (c) {
            case LAPIS -> "lapis_lazuli";
            case TUNGSTEN -> "flint";
            case UMBER -> "brick";
            case VANGUARD -> "iron_helmet";
        };
    }

    private static Mineshaft.Corpse corpse(String name) {
        for (Mineshaft.Corpse c : Mineshaft.Corpse.values()) if (c.type.equalsIgnoreCase(name)) return c;
        return null;
    }

    // "TUNGSTEN_KEY" the way a player reads it
    private static String keyName(String key) {
        StringBuilder out = new StringBuilder();
        for (String w : key.split("_")) out.append(out.isEmpty() ? "" : " ").append(w.charAt(0)).append(Mining.lower(w.substring(1)));
        return out.toString();
    }

    static Body corpses(List<Seen> all, int found, Mineshaft.Corpse nearest, double nearestAway, List<String> loot) {
        Body b = new Body();
        int looted = 0;
        for (Seen s : all) {
            Mineshaft.Corpse c = corpse(s.name());
            Line l = Line.item(corpseIcon(c), s.name()).spaced();
            if (s.looted()) {
                looted++;
                b.add(l.labelColor(Modern.DIM).chip("LOOTED", Modern.GOOD));
            } else {
                if (c != null) l.labelColor(codeColor(c.color));
                if (c != null && c.key != null) l.cells(keyName(c.key)).colors(Modern.LABEL).briefCells(keyName(c.key).replace("Tungsten", "Tung").replace("Skeleton", "Skel"));
                b.add(l.chip("NOT LOOTED", Modern.WARN).briefChip("TO LOOT"));
            }
        }
        if (!all.isEmpty()) b.right("§a" + looted + "§7/§f" + all.size() + "  §7" + percent(looted / (double) all.size()));
        boolean count = found > 0 && found <= all.size();
        if (count || nearest != null) {
            b.add(Line.rule());
            if (count) b.add(Line.row("Found", fraction(String.valueOf(found), String.valueOf(all.size())), percent(found / (double) all.size())).colors(0, Modern.LABEL));
            if (nearest != null) b.add(Line.row("Nearest to loot", bright(nearest.color) + nearest.type, Math.round(nearestAway) + "m").brief("Next"));
        }
        if (!loot.isEmpty()) {
            b.add(Line.head("Last loot"));
            for (String item : loot) b.add(lootLine(item));
        }
        return b;
    }

    private static Line lootLine(String item) {
        Matcher m = AMOUNT.matcher(item);
        boolean counted = m.matches();
        String name = counted ? m.group(1) : item, n = Mining.lower(name);
        Line l;
        if (n.contains("gemstone powder")) l = Line.item("amethyst_shard", name).labelColor(GEMSTONE);
        else if (n.contains("gemstone")) l = Line.item("amethyst_shard", name).labelColor(PURPLE);
        else if (n.contains("glacite")) l = Line.item("packed_ice", name).labelColor(GLACITE);
        else if (n.contains("mithril")) l = Line.item("prismarine_crystals", name).labelColor(MITHRIL);
        else if (n.contains("tungsten")) l = Line.item("flint", name);
        else if (n.contains("umber")) l = Line.item("brick", name).labelColor(codeColor(Mineshaft.Corpse.UMBER.color));
        else l = Line.item("paper", name);
        return counted ? l.cells("x" + m.group(2)) : l;
    }

    static HudElement corpses() {
        return new MiningCard("mining_corpses", "Corpses", 34, "mining_corpses") {
            private List<String[]> read() {
                List<String[]> out = new ArrayList<>();
                for (String l : GameState.tab) {
                    Matcher m = MiningExtras.CORPSE_TAB.matcher(l);
                    if (m.matches()) out.add(new String[]{m.group("corpse"), l.contains("NOT LOOTED") ? "NOT LOOTED" : "LOOTED"});
                }
                return out;
            }

            @Override public boolean visible() { return "MINESHAFT".equals(GameState.island) && !read().isEmpty(); }

            @Override Body live() {
                List<Seen> seen = new ArrayList<>();
                boolean anyLooted = false;
                for (String[] c : read()) {
                    boolean looted = c[1].equals("LOOTED");
                    anyLooted |= looted;
                    seen.add(new Seen(c[0], looted));
                }
                int found = 0;
                Mineshaft.Corpse nearest = null;
                double away = Double.MAX_VALUE;
                Vec3 me = MiningEnv.world.playerPos();
                for (Mineshaft.Waypoint w : new ArrayList<>(Mineshaft.waypoints)) {
                    if (!w.isCorpse()) continue;
                    found++;
                    if (w.kind != Mineshaft.Waypoint.Kind.FOUND_CORPSE) continue;
                    double d = w.location.distanceTo(me);
                    if (d < away) {
                        away = d;
                        nearest = w.corpse;
                    }
                }
                // last loot outlives its shaft. with nothing looted here yet it would belong to another one
                return corpses(seen, found, nearest, away, anyLooted ? Mineshaft.lastLoot : List.of());
            }

            @Override Body sample() {
                return corpses(List.of(new Seen("Lapis", true), new Seen("Tungsten", false), new Seen("Umber", false), new Seen("Vanguard", false)), 3, Mineshaft.Corpse.UMBER, 34.2,
                        List.of("☠ Fine Onyx Gemstone x2", "Glacite Powder x1,200", "Enchanted Tungsten x3", "Shattered Locket"));
            }

        };
    }

    // treasure and fossils

    static Body dig(double treasure, String fossil, long fossilAgo) {
        Body b = new Body();
        if (treasure >= 0) {
            Line l = Line.item("gold_nugget", "Treasure", (treasure < 5 ? "§a" : "§b") + treasure + "m §7away");
            b.add(treasure < 5 ? l.chip("CLOSE", Modern.GOOD) : l);
        }
        if (!fossil.isEmpty()) {
            Matcher m = PERCENT.matcher(fossil);
            Line l = Line.item("bone", "Fossil", MiningEnv.format(fossilAgo) + " ago", fossil).colors(Modern.LABEL, Modern.GOLD);
            if (!m.find()) return b.add(l);
            double p = Double.parseDouble(m.group(1)) / 100;
            b.add(l.colors(Modern.LABEL, p >= 1 ? Modern.GOOD : Modern.GOLD).meter(p, p >= 1 ? Modern.GOOD : SAND).thick());
        }
        return b;
    }

    static HudElement dig() {
        return new MiningCard("mining_dig", "Treasure and fossils", 37, "mining_dig") {
            @Override public boolean visible() { return MiningExtras.mining() && (MiningExtras.treasure() >= 0 || !MiningExtras.freshFossil().isEmpty()); }
            @Override Body live() { return dig(MiningExtras.treasure(), MiningExtras.freshFossil(), MiningExtras.fossilAgo()); }
            @Override Body sample() { return dig(12.4, "8.3%", 12_000); }

        };
    }

    // mineshaft timer

    static Body caveIn(String shaft, long left, long inside, boolean showInside, boolean showEstimate, Long estimate) {
        Body b = new Body();
        if (shaft != null) b.right(bright(shaft));
        if (left < 0) b.add(Line.row("Entrance").chip("CAVED IN!", Modern.BAD));
        else {
            double p = left / (double) Mineshaft.CAVE_IN_DURATION;
            String code = left > 30_000 ? "§a" : left > 10_000 ? "§e" : "§c";
            int color = left > 30_000 ? Modern.GOOD : left > 10_000 ? Modern.GOLD : Modern.BAD;
            b.add(Line.row("Entrance caves in", code + left / 1000 + "s§7/§f" + Mineshaft.CAVE_IN_DURATION / 1000 + "s", wholePercent(p)).colors(0, Modern.LABEL).meter(p, color).thick());
        }
        if (showInside) b.add(Line.row("Time in mineshaft", MiningEnv.format(inside)).brief("Inside"));
        if (showEstimate) b.add(Line.row("Estimated time left", estimate == null ? "§7Calculating..." : "§e" + MiningEnv.format(Math.max(0, estimate))).brief("Est. left"));
        return b;
    }

    static HudElement caveIn() {
        return new MiningCard("mining_cavein", "Mineshaft timer", 36, "mining_mineshaft") {
            @Override public boolean visible() { return Mining.b("mining_mineshaft", "caveInTimer", true) && !Mineshaft.caveInLines().isEmpty(); }
            @Override public String heading() { return compact() ? "Mineshaft" : title; }

            @Override Body live() {
                if (Mineshaft.caveInLines().isEmpty()) return new Body();
                return caveIn(Mineshaft.current == null ? null : Mineshaft.current.displayName(), Mineshaft.caveInLeft(), Mineshaft.timeInMineshaft(),
                        Mining.b("mining_mineshaft", "showTimeInMineshaft", true), Mining.b("mining_mineshaft", "showEstimatedTimeLeft", true), Mineshaft.estimatedLeft());
            }

            @Override Body sample() { return caveIn(Mineshaft.Type.JASP_C.displayName(), 42_000, 18_000, true, true, 192_000L); }

        };
    }

    // pity

    static Body pity(boolean widget, int pity, long lastAgo, int total, int session) {
        Body b = new Body();
        if (!widget) return b.add(Line.row("Pity Tab Widget").chip("MISSING", Modern.BAD), Line.row("Do /tab and enable the pity widget").labelColor(Modern.WARN).brief("Do /tab, enable pity widget"));
        double p = pity / (double) Mineshaft.PITY_TARGET;
        b.add(Line.row("Pity Counter", fraction(MiningEnv.sep(pity), MiningEnv.sep(Mineshaft.PITY_TARGET)), percent(p)).colors(0, Modern.LABEL).meter(p, p >= 1 ? Modern.GOOD : ICE).thick());
        b.add(Line.row("To go", MiningEnv.sep(Math.max(0, Mineshaft.PITY_TARGET - pity))));
        if (lastAgo >= 0) b.add(Line.row("Last Mineshaft", "§e" + MiningEnv.format(lastAgo) + " §7ago").brief("Last"));
        return b.add(Line.row("Mineshafts", MiningEnv.sep(total) + " §7total  §f" + MiningEnv.sep(session) + " §7session"));
    }

    static HudElement pity() {
        return new MiningCard("mining_pity", "Mineshaft Pity", 38, "mining_pity") {
            @Override public boolean visible() { return !Mineshaft.pityLines().isEmpty(); }
            @Override public String heading() { return title; }

            @Override Body live() {
                if (Mineshaft.pityLines().isEmpty()) return new Body();
                return pity(Mineshaft.everFoundPityWidget, Mineshaft.tablistPity, MiningEnv.isFarPast(Mineshaft.lastMineshaftSpawn) ? -1 : MiningEnv.passedSince(Mineshaft.lastMineshaftSpawn), MiningEnv.profile().mineshaftTotalCount, Mineshaft.sessionMineshafts);
            }

            @Override Body sample() { return pity(true, 124, 723_000, 38, 2); }

        };
    }

    // cold

    static Body cold(int c) {
        int color = c >= 80 ? Modern.BAD : c >= 50 ? Modern.GOLD : Modern.AQUA;
        Line l = Line.row("Cold ❄", c + "§7/§f100", percent(c / 100.0)).colors(color, Modern.LABEL).labelColor(Modern.lighten(Modern.AQUA, 0.3f)).meter(c / 100.0, color).thick().ticks(0.5, 0.8);
        return new Body().add(c >= 80 ? l.chip("FREEZING", Modern.BAD) : l);
    }

    static HudElement cold() {
        return new MiningCard("mining_cold_value", "Cold", 39, "mining_cold") {
            @Override public boolean visible() { return MiningApi.inColdArea() && MiningApi.cold > 0; }
            @Override public String heading() { return ""; }
            @Override Body live() { return cold(MiningApi.cold); }
            @Override Body sample() { return cold(54); }

        };
    }
}
