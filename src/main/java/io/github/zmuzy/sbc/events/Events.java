// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.events.diana.DianaData;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Toasts;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.hud.RowText;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// seasonal events from chat + sidebar: diana (chain, coins, feathers, mythological mobs), carnival games,
// great spook (quick maths, fears), winter gifts, bingo, the year-of-X minigames
// burrow helper and rare mob share are in events/diana, hoppity in events/hoppity
public final class Events {
    // diana: skyhanni's patterns without colours (DianaProfitTracker
    // chatDugOutPattern/chatDugOutCoinsPattern/griffinFeatherDropPattern, GriffinBurrowHelper
    // burrowDugPattern/genericMythologicalSpawnPattern/treasureDugPattern)
    // burrow waypoints themselves are in events/diana (skyhanni's own § patterns on the formatted line)
    static final Pattern BURROW = Pattern.compile("^You (?:dug out a Griffin Burrow|finished the Griffin burrow chain)!(?: \\((\\d+)/(\\d+)\\))?$");
    static final Pattern DUG_COINS = Pattern.compile("^Wow! You dug out ([\\d,]+) coins!$");
    static final Pattern FEATHER = Pattern.compile("^RARE DROP! You dug out a Griffin Feather!$");
    static final Pattern MYTHO = Pattern.compile("^(?:Uh oh|Oi|Woah|Danger|Yikes|Oh no)! You dug out (?:an? )?(.+)!$");
    static final Pattern TREASURE_DROP = Pattern.compile("^RARE DROP! You dug out (?:an? )?(.+)!$");
    static final Pattern RITUAL_OFF = Pattern.compile("^The mythological ritual isn't active!$");
    // carnival
    static final Pattern FRUIT_START = Pattern.compile("^\\[NPC\\] Carnival Pirateman: Good luck, matey!$");
    static final Pattern FRUIT_TREASURE = Pattern.compile("^TREASURE! There is an? (\\w+) nearby\\.$");
    static final Pattern FRUIT_NONE = Pattern.compile("^(?:TREASURE|ANCHOR)! There are no fruits nearby!$");
    static final Pattern FRUIT_MINES = Pattern.compile("^MINES! There (?:is|are) (\\d+) bombs? hidden nearby\\.$");
    static final Pattern ZOMBIE_START = Pattern.compile("^\\[NPC\\] Carnival Cowboy: Good luck, pal!$");
    public static final Pattern TICKETS = Pattern.compile("^You claimed Carnival Ticket x(\\d+)!$");
    public static final Pattern TICKETS_DONE = Pattern.compile("^\\[NPC\\] Carnival Leader: You've already claimed your Carnival Tickets for today.*$");
    // spook
    static final Pattern SPOOK_CHEST = Pattern.compile("^(?:SPOOKY|PARTY)! A (.+?) has appeared!$");
    static final Pattern MATHS = Pattern.compile("^QUICK MATHS! Solve: (.+)$");
    static final Pattern SPEAK = Pattern.compile("^\\[FEAR\\] Public Speaking Demon: Speak (\\S+)!$");
    static final Pattern PRIMAL = Pattern.compile("^FEAR\\. A Primal Fear has been summoned!$");
    // winter
    static final Pattern GIFT_COINS = Pattern.compile("^([A-Z ]+)! \\+([\\d,]+) Coins gift with (.+)!$");
    static final Pattern GIFT_XP = Pattern.compile("^([A-Z ]+)! \\+([\\d,]+) (\\w+) XP gift with (.+)!$");
    static final Pattern GIFT_STARS = Pattern.compile("^EXTRA! \\+(\\d+) North Stars?$");
    static final Pattern GIFT_ITEM = Pattern.compile("^([A-Z ]+)! (.+?) gift with (.+)!$");
    static final Pattern GIFT_UNIQUE = Pattern.compile("^\\+1 Unique Gift given! To (.+?)!$");
    static final Pattern JYRRE = Pattern.compile("^You consumed a Refined Bottle of Jyrre and gained \\+(\\d+)\\S* Intelligence for (\\d+)m!$");
    // bingo / year-of
    static final Pattern BINGO_SB = Pattern.compile("^.*\\u24B7 Bingo.*$");
    static final Pattern BOOP = Pattern.compile("^Boop!$");
    static final Pattern PIG_ORB = Pattern.compile("^SHINY! You received a Shiny Token and (.+?) from the Shiny Pig's orb!$");
    static final Pattern BEACH = Pattern.compile("^([A-Z ]+)! You kept the Bouncy Beach Ball in the air for (\\d+) bounces and earned (\\d+) Fishy Treats?!$");

    // diana state
    private static int burrowsDug, chainDone, feathers, inquisitors;
    private static String chain = "";
    private static long dianaCoins;
    private static final Map<String, Integer> mythos = new LinkedHashMap<>(), treasures = new LinkedHashMap<>();
    // carnival
    private static String fruitHint = "";
    private static long fruitAt;
    private static int fruitsDug, bombs;
    // spook
    private static String mathsAnswer = "", mathsQuestion = "";
    private static long mathsAt;
    private static int fears;
    // winter
    private static int gifts, uniqueGifts, northStars;
    private static long giftCoins;
    private static final Map<String, Integer> giftItems = new LinkedHashMap<>();
    private static long jyrreVibeUntil;
    // beach ball
    private static int bestBounces, treats;

    private Events() {}

    private static boolean sb() {
        return GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV;
    }

    private static boolean hub() {
        return "HUB".equals(GameState.island) || io.github.zmuzy.sbc.SbcMod.DEV;
    }

    public static void reset() {
        burrowsDug = chainDone = feathers = inquisitors = 0;
        chain = "";
        dianaCoins = 0;
        mythos.clear();
        treasures.clear();
        fruitHint = "";
        fruitsDug = bombs = 0;
        mathsAnswer = mathsQuestion = "";
        fears = 0;
        gifts = uniqueGifts = northStars = 0;
        giftCoins = 0;
        giftItems.clear();
        bestBounces = treats = 0;
    }

    static int burrowsDug() { return burrowsDug; }
    static String chain() { return chain; }
    static String mathsAnswer() { return mathsAnswer; }
    static long giftCoins() { return giftCoins; }
    static int gifts() { return gifts; }

    private static void alert(String title, String body, int color, float pitch) {
        Toasts.push(title, body, color);
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.9f, pitch);
    }

    // solves the great spook QUICK MATHS (+ - * / x and brackets), "" if it cant
    public static String solve(String expr) {
        try {
            String s = expr.replace("x", "*").replace("X", "*").replace(" ", "");
            double v = new Object() {
                int i = 0;
                double parse() {
                    double v = term();
                    while (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                        char op = s.charAt(i++);
                        double r = term();
                        v = op == '+' ? v + r : v - r;
                    }
                    return v;
                }
                double term() {
                    double v = factor();
                    while (i < s.length() && (s.charAt(i) == '*' || s.charAt(i) == '/')) {
                        char op = s.charAt(i++);
                        double r = factor();
                        v = op == '*' ? v * r : v / r;
                    }
                    return v;
                }
                double factor() {
                    if (i < s.length() && s.charAt(i) == '(') {
                        i++;
                        double v = parse();
                        i++;
                        return v;
                    }
                    if (i < s.length() && s.charAt(i) == '-') {
                        i++;
                        return -factor();
                    }
                    int st = i;
                    while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                    return Double.parseDouble(s.substring(st, i));
                }
            }.parse();
            return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v);
        } catch (Exception e) {
            return "";
        }
    }

    // pure: one stripped chat line, true if we used it
    public static boolean feed(String plain) {
        String t = plain.trim();
        Matcher m;
        // diana
        if ((m = BURROW.matcher(t)).matches()) {
            burrowsDug++;
            if (m.group(1) != null) {
                chain = m.group(1) + "/" + m.group(2);
                if (t.startsWith("You finished")) chainDone++;
            }
            return true;
        }
        if ((m = DUG_COINS.matcher(t)).matches()) {
            dianaCoins += Long.parseLong(m.group(1).replace(",", ""));
            return true;
        }
        if (FEATHER.matcher(t).matches()) {
            feathers++;
            return true;
        }
        if ((m = MYTHO.matcher(t)).matches()) {
            mythos.merge(m.group(1), 1, Integer::sum);
            if (m.group(1).contains("Inquisitor")) {
                inquisitors++;
                if (Features.on("diana_inquisitor")) alert("INQUISITOR", "You dug out a Minos Inquisitor", 0xFFFF55FF, 0.7f);
            }
            return true;
        }
        if ((m = TREASURE_DROP.matcher(t)).matches()) {
            treasures.merge(m.group(1), 1, Integer::sum);
            return true;
        }
        if (RITUAL_OFF.matcher(t).matches()) return true;
        // carnival
        if (FRUIT_START.matcher(t).matches()) {
            fruitHint = "";
            fruitsDug = bombs = 0;
            fruitAt = System.currentTimeMillis();
            return true;
        }
        if ((m = FRUIT_TREASURE.matcher(t)).matches()) {
            fruitHint = m.group(1) + " nearby";
            fruitAt = System.currentTimeMillis();
            fruitsDug++;
            return true;
        }
        if (FRUIT_NONE.matcher(t).matches()) {
            fruitHint = "no fruits nearby";
            fruitAt = System.currentTimeMillis();
            fruitsDug++;
            return true;
        }
        if ((m = FRUIT_MINES.matcher(t)).matches()) {
            bombs = Integer.parseInt(m.group(1));
            fruitHint = bombs + (bombs == 1 ? " bomb nearby" : " bombs nearby");
            fruitAt = System.currentTimeMillis();
            fruitsDug++;
            return true;
        }
        if (ZOMBIE_START.matcher(t).matches()) return true;
        if ((m = TICKETS.matcher(t)).matches() || TICKETS_DONE.matcher(t).matches()) return true;
        // spook
        if ((m = SPOOK_CHEST.matcher(t)).matches()) {
            if (Features.on("spook_alerts")) GreatSpook.chest(m.group(1), t.startsWith("PARTY"));
            return true;
        }
        if ((m = MATHS.matcher(t)).matches()) {
            mathsAnswer = solve(m.group(1));
            mathsQuestion = m.group(1);
            mathsAt = System.currentTimeMillis();
            if (!mathsAnswer.isEmpty() && Features.on("spook_maths")) alert("Quick maths", m.group(1) + " = " + mathsAnswer, 0xFF55FF55, 1.4f);
            return true;
        }
        if ((m = SPEAK.matcher(t)).matches()) {
            if (Features.on("spook_alerts")) alert("Say in chat", m.group(1), 0xFF55FF55, 1.4f);
            return true;
        }
        if (PRIMAL.matcher(t).matches()) {
            fears++;
            if (Features.on("spook_alerts")) alert("Primal Fear", "summoned near you", 0xFFAA00AA, 0.7f);
            return true;
        }
        // winter
        if ((m = GIFT_COINS.matcher(t)).matches()) {
            gifts++;
            giftCoins += Long.parseLong(m.group(2).replace(",", ""));
            return true;
        }
        if ((m = GIFT_XP.matcher(t)).matches()) {
            gifts++;
            giftItems.merge(m.group(3) + " XP", Integer.parseInt(m.group(2).replace(",", "")), Integer::sum);
            return true;
        }
        if ((m = GIFT_STARS.matcher(t)).matches()) {
            northStars += Integer.parseInt(m.group(1));
            return true;
        }
        if ((m = GIFT_ITEM.matcher(t)).matches()) {
            gifts++;
            giftItems.merge(m.group(2), 1, Integer::sum);
            return true;
        }
        if ((m = GIFT_UNIQUE.matcher(t)).matches()) {
            uniqueGifts++;
            return true;
        }
        if ((m = JYRRE.matcher(t)).matches()) {
            jyrreVibeUntil = System.currentTimeMillis() + Long.parseLong(m.group(2)) * 60_000L;
            return true;
        }
        // bingo / year-of
        if (BOOP.matcher(t).matches()) return true;
        if ((m = PIG_ORB.matcher(t)).matches()) {
            Toasts.push("Shiny orb", m.group(1), 0xFFFFD866);
            return true;
        }
        if ((m = BEACH.matcher(t)).matches()) {
            bestBounces = Math.max(bestBounces, Integer.parseInt(m.group(2)));
            treats += Integer.parseInt(m.group(3));
            return true;
        }
        return false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (sb()) feed(line.plain());
        return ChatBus.PASS;
    }

    public static boolean bingo() {
        for (String l : GameState.scoreboard) if (BINGO_SB.matcher(l).matches()) return true;
        return false;
    }

    private static String ago(long at) {
        long s = (System.currentTimeMillis() - at) / 1000;
        return s < 60 ? s + "s" : s / 60 + "m " + s % 60 + "s";
    }

    private static String sep(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static <K> long sum(Map<K, Integer> m) {
        long total = 0;
        for (int n : m.values()) total += n;
        return total;
    }

    // game colours for what you dig out, brighter for the glass: common mobs dark green, rare red (Diana.json),
    // treasures by item rarity
    // rows get a vanilla look-alike icon so you know the mob before reading
    private static final int MOB = 0xFF72D97C, RARE_MOB = 0xFFFF6B6B, FEATHER_BLUE = EventSheet.RARE;
    private static final Map<String, String> MOB_ICONS = Map.ofEntries(Map.entry("Minos Inquisitor", "nether_star"), Map.entry("Sphinx", "chiseled_sandstone"), Map.entry("King Minos", "golden_helmet"),
            Map.entry("Manticore", "blaze_powder"), Map.entry("Minos Champion", "golden_sword"), Map.entry("Minotaur", "cow_spawn_egg"), Map.entry("Gaia Construct", "iron_golem_spawn_egg"), Map.entry("Siamese Lynxes", "ocelot_spawn_egg"),
            Map.entry("Minos Hunter", "bow"), Map.entry("Cretan Bull", "mooshroom_spawn_egg"), Map.entry("Harpy", "phantom_membrane"), Map.entry("Stranded Nymph", "tropical_fish"));

    private record Drop(int color, String icon) {}

    private static final Map<String, Drop> DROPS = Map.ofEntries(Map.entry("Crown of Greed", new Drop(EventSheet.LEGENDARY, "golden_helmet")), Map.entry("Daedalus Stick", new Drop(EventSheet.LEGENDARY, "stick")),
            Map.entry("Washed-up Souvenir", new Drop(EventSheet.EPIC, "nautilus_shell")), Map.entry("Dwarf Turtle Shelmet", new Drop(EventSheet.EPIC, "turtle_helmet")), Map.entry("Crochet Tiger Plushie", new Drop(EventSheet.EPIC, "white_wool")),
            Map.entry("Antique Remedies", new Drop(EventSheet.EPIC, "azure_bluet")), Map.entry("Minos Relic", new Drop(EventSheet.EPIC, "ender_eye")), Map.entry("Braided Griffin Feather", new Drop(EventSheet.EPIC, "string")),
            Map.entry("Mythos Fragment", new Drop(EventSheet.RARE, "prismarine_shard")), Map.entry("Griffin Feather", new Drop(EventSheet.RARE, "feather")));

    private static boolean rareMob(String name) {
        var c = DianaData.creatures().get(name);
        return c != null ? c.rare() : name.contains("Inquisitor");
    }

    private static void dianaSheet(EventSheet s, int burrows, String chainNow, int chains, long coins, int feathers, Map<String, Integer> mobs, Map<String, Integer> drops) {
        long mobTotal = sum(mobs), dropTotal = sum(drops);
        s.right = "§f" + sep(burrows) + " §7burrows";
        if (!chainNow.isEmpty()) {
            String[] c = chainNow.split("/");
            int of = Math.max(1, Integer.parseInt(c[1]));
            double p = Integer.parseInt(c[0]) / (double) of;
            s.line("Chain", EventSheet.ratio(c[0], c[1], p, 'e', '6')).meter(p).steps(of);
        }
        s.tiles(EventSheet.tile("Chains", sep(chains), Modern.INK), EventSheet.tile("Mobs", sep(mobTotal), mobTotal > 0 ? MOB : Modern.DIM), EventSheet.tile("Feathers", sep(feathers), feathers > 0 ? FEATHER_BLUE : Modern.DIM));
        if (coins > 0) {
            s.line("Coins", sep(coins), Modern.GOLD).icon("gold_nugget");
            if (burrows > 0) s.line("Per burrow", sep(coins / burrows), Modern.LABEL).indent().brief("/burrow");
        }
        if (mobTotal > 0) {
            List<Map.Entry<String, Integer>> order = new ArrayList<>(mobs.entrySet());
            order.sort(java.util.Comparator.comparingInt(e -> rareMob(e.getKey()) ? 0 : 1));
            s.section("Mythological mobs", sep(mobTotal) + (burrows > 0 ? " §7" + RowText.percent(Math.min(1, mobTotal / (double) burrows)) + " of burrows" : "")).brief("Mobs");
            List<EventSheet.Share> shares = new ArrayList<>();
            for (Map.Entry<String, Integer> e : order) shares.add(new EventSheet.Share(e.getValue(), rareMob(e.getKey()) ? RARE_MOB : MOB));
            s.stack(shares);
            for (Map.Entry<String, Integer> e : order) {
                boolean rare = rareMob(e.getKey());
                int color = rare ? RARE_MOB : MOB;
                EventSheet.Line l = s.line(e.getKey(), sep(e.getValue()), Modern.INK).labelColor(color).tail(RowText.percent(e.getValue() / (double) mobTotal)).brief(e.getKey().replace("Minos ", ""));
                String icon = MOB_ICONS.get(e.getKey());
                if (icon != null) l.icon(icon);
                else l.pip(color, true);
                if (rare) l.chip("RARE", RARE_MOB);
            }
        }
        if (dropTotal > 0) {
            s.section("Treasures", sep(dropTotal));
            for (Map.Entry<String, Integer> e : drops.entrySet()) {
                Drop d = DROPS.get(e.getKey());
                int color = d == null ? EventSheet.RARE : d.color;
                EventSheet.Line l = s.line(e.getKey(), sep(e.getValue()), Modern.INK).labelColor(color);
                if (d != null) l.icon(d.icon);
                else l.pip(color, true);
            }
        }
    }

    public static final HudElement DIANA = new EventSheet.Card("diana_panel", "Diana", "Events", Zone.TOP_LEFT, 45, "diana_panel") {
        @Override
        public boolean visible() {
            return hub() && burrowsDug > 0;
        }

        @Override
        protected void live(EventSheet s) {
            dianaSheet(s, burrowsDug, chain, chainDone, dianaCoins, feathers, mythos, treasures);
        }

        @Override
        protected void sample(EventSheet s) {
            Map<String, Integer> mobs = new LinkedHashMap<>(), drops = new LinkedHashMap<>();
            mobs.put("Minos Hunter", 41);
            mobs.put("Siamese Lynxes", 27);
            mobs.put("Minotaur", 16);
            mobs.put("Gaia Construct", 12);
            mobs.put("Stranded Nymph", 9);
            mobs.put("Minos Champion", 7);
            mobs.put("Minos Inquisitor", 2);
            drops.put("Crown of Greed", 1);
            drops.put("Washed-up Souvenir", 2);
            drops.put("Dwarf Turtle Shelmet", 1);
            drops.put("Mythos Fragment", 6);
            dianaSheet(s, 342, "3/4", 85, 18_412_500, 11, mobs, drops);
        }

        @Override
        protected List<Row> plainRows() {
            List<Row> out = new ArrayList<>();
            out.add(new Row("Burrows", burrowsDug + (chain.isEmpty() ? "" : "  chain " + chain), Theme.current().text()));
            if (chainDone > 0) out.add(new Row("Chains done", String.valueOf(chainDone), Theme.current().dim()));
            if (dianaCoins > 0) out.add(new Row("Coins", String.format(Locale.ROOT, "%,d", dianaCoins), Theme.current().accent()));
            if (feathers > 0) out.add(new Row("Griffin Feathers", String.valueOf(feathers), 0xFFFFD866));
            if (inquisitors > 0) out.add(new Row("Inquisitors", String.valueOf(inquisitors), 0xFFFF55FF));
            for (Map.Entry<String, Integer> e : mythos.entrySet()) if (!e.getKey().contains("Inquisitor")) out.add(new Row(e.getKey(), String.valueOf(e.getValue()), Theme.current().dim()));
            for (Map.Entry<String, Integer> e : treasures.entrySet()) out.add(new Row(e.getKey(), String.valueOf(e.getValue()), 0xFF5555FF));
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Burrows", "42  chain 3/4", 0), new Row("Coins", "120,000", Theme.current().accent()), new Row("Inquisitors", "1", 0xFFFF55FF));
        }
    };

    private static void carnivalSheet(EventSheet s, int dug, String hint, int mines, String age) {
        s.right = "§7Fruit digging";
        if (!hint.isEmpty()) s.hero("Hint", hint, hint.contains("bomb") ? Modern.BAD : hint.startsWith("no ") ? Modern.LABEL : Modern.GOOD);
        List<EventSheet.Tile> tiles = new ArrayList<>();
        tiles.add(EventSheet.tile("Spots dug", sep(dug), Modern.INK, "Dug"));
        if (mines > 0) tiles.add(EventSheet.tile("Last mines", sep(mines), Modern.BAD, "Mines"));
        tiles.add(EventSheet.tile("Last hint", age, Modern.AQUA, "Hint"));
        s.tiles(tiles.toArray(new EventSheet.Tile[0]));
    }

    public static final HudElement CARNIVAL = new EventSheet.Card("carnival_panel", "Carnival", "Events", Zone.TOP_LEFT, 47, "carnival_panel") {
        @Override
        public boolean visible() {
            return hub() && (fruitAt > 0 && System.currentTimeMillis() - fruitAt < 90_000 || io.github.zmuzy.sbc.events.carnival.FruitDigging.playing());
        }

        @Override
        protected void live(EventSheet s) {
            carnivalSheet(s, fruitsDug, fruitHint, bombs, ago(fruitAt) + " ago");
            io.github.zmuzy.sbc.events.carnival.FruitDigging.fillLive(s);
        }

        @Override
        protected void sample(EventSheet s) {
            carnivalSheet(s, 14, "Durian nearby", 3, "12s ago");
            io.github.zmuzy.sbc.events.carnival.FruitDigging.fillSample(s);
        }

        @Override
        protected List<Row> plainRows() {
            List<Row> out = new ArrayList<>();
            out.add(new Row("Fruit digging", fruitsDug + " dug", Theme.current().text()));
            if (!fruitHint.isEmpty()) out.add(new Row("Hint", fruitHint, fruitHint.contains("bomb") ? Theme.current().bad() : Theme.current().good()));
            out.addAll(io.github.zmuzy.sbc.events.carnival.FruitDigging.plainRows());
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Fruit digging", "5 dug", 0), new Row("Hint", "Durian nearby", Theme.current().good()));
        }
    };

    private static final long MATHS_SHOWN_MS = 20_000;

    private static boolean mathsFresh() {
        return mathsAt > 0 && System.currentTimeMillis() - mathsAt < MATHS_SHOWN_MS;
    }

    // answer as soon as its known, question beside it, countdown for how long it stays
    private static void spookSheet(EventSheet s, boolean maths, String question, String answer, String age, double left, int primalFears) {
        if (maths) {
            if (!question.isEmpty()) s.line("Quick maths", question, Modern.LABEL).brief("Maths");
            s.hero("Answer", answer.isEmpty() ? "?" : answer, answer.isEmpty() ? Modern.WARN : Modern.GOOD);
            s.line("Asked", age + " ago", Modern.AQUA).meter(left, Modern.GOOD);
        }
        if (primalFears > 0) s.line("Primal Fears", sep(primalFears), EventSheet.EPIC).icon("wither_rose").brief("Fears");
    }

    public static final HudElement SPOOK = new EventSheet.Card("spook_panel", "Great Spook", "Events", Zone.TOP_LEFT, 48, "spook_panel") {
        @Override
        public boolean visible() {
            return sb() && (fears > 0 || mathsFresh());
        }

        @Override
        protected void live(EventSheet s) {
            spookSheet(s, mathsFresh(), mathsQuestion, mathsAnswer, ago(mathsAt), 1 - (System.currentTimeMillis() - mathsAt) / (double) MATHS_SHOWN_MS, fears);
        }

        @Override
        protected void sample(EventSheet s) {
            spookSheet(s, true, "(12 x 5) + 20", "80", "6s", 0.7, 3);
        }

        @Override
        protected List<Row> plainRows() {
            List<Row> out = new ArrayList<>();
            if (mathsFresh()) out.add(new Row("Answer", mathsAnswer.isEmpty() ? "?" : mathsAnswer, Theme.current().good()));
            if (fears > 0) out.add(new Row("Primal Fears", String.valueOf(fears), 0xFFAA00AA));
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Answer", "80", Theme.current().good()), new Row("Primal Fears", "3", 0xFFAA00AA));
        }
    };

    private static final Map<String, String> SKILL_ICONS = Map.ofEntries(Map.entry("Combat", "iron_sword"), Map.entry("Farming", "golden_hoe"), Map.entry("Mining", "stone_pickaxe"), Map.entry("Foraging", "jungle_sapling"), Map.entry("Fishing", "fishing_rod"),
            Map.entry("Enchanting", "enchanting_table"), Map.entry("Alchemy", "brewing_stand"), Map.entry("Taming", "bone"), Map.entry("Carpentry", "crafting_table"), Map.entry("Runecrafting", "magma_cream"), Map.entry("Social", "emerald"));

    private static final String JYRRE_INACTIVE = "Inactive";

    private static boolean jyrreKept() {
        var f = Features.get("winter_panel");
        var o = f == null ? null : f.option("winter_panel.jyrre_inactive");
        return jyrreVibeUntil > 0 && o != null && o.bool();
    }

    private static void winterSheet(EventSheet s, int opened, int given, int stars, long coins, Map<String, Integer> items, String jyrre) {
        s.tiles(EventSheet.tile("Gifts opened", sep(opened), Modern.INK, "Gifts"), EventSheet.tile("Unique given", sep(given), given > 0 ? Modern.GOOD : Modern.DIM, "Unique"), EventSheet.tile("North Stars", sep(stars), stars > 0 ? EventSheet.MYTHIC : Modern.DIM, "Stars"));
        if (coins > 0) {
            s.line("Coins", sep(coins), Modern.GOLD).icon("gold_nugget");
            if (opened > 0) s.line("Per gift", sep(coins / opened), Modern.LABEL).indent().brief("/gift");
        }
        if (JYRRE_INACTIVE.equals(jyrre)) s.line("Jyrre", jyrre, Modern.BAD).icon("potion");
        else if (jyrre != null) s.line("Jyrre", jyrre, Modern.AQUA).icon("potion").chip("ACTIVE", Modern.AQUA);
        Map<String, Integer> xp = new LinkedHashMap<>(), drops = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : items.entrySet()) (e.getKey().endsWith(" XP") ? xp : drops).put(e.getKey(), e.getValue());
        if (!xp.isEmpty()) {
            s.section("XP from gifts");
            for (Map.Entry<String, Integer> e : xp.entrySet()) {
                String icon = SKILL_ICONS.get(e.getKey().substring(0, e.getKey().length() - 3));
                s.line(e.getKey(), sep(e.getValue()), Modern.AQUA).icon(icon == null ? "experience_bottle" : icon).brief(e.getKey().substring(0, e.getKey().length() - 3));
            }
        }
        if (!drops.isEmpty()) {
            s.section("Items from gifts", sep(sum(drops)));
            for (Map.Entry<String, Integer> e : drops.entrySet()) s.line(e.getKey(), sep(e.getValue()), Modern.INK).pip(Modern.lighten(0xFF9BE7FF, 0.1f), true);
        }
    }

    public static final HudElement WINTER = new EventSheet.Card("winter_panel", "Winter", "Events", Zone.TOP_LEFT, 49, "winter_panel") {
        @Override
        public boolean visible() {
            return sb() && (gifts > 0 || uniqueGifts > 0 || jyrreVibeUntil > System.currentTimeMillis() || jyrreKept());
        }

        @Override
        protected void live(EventSheet s) {
            winterSheet(s, gifts, uniqueGifts, northStars, giftCoins, giftItems, jyrreVibeUntil > System.currentTimeMillis() ? ago(2 * System.currentTimeMillis() - jyrreVibeUntil) + " left" : jyrreKept() ? JYRRE_INACTIVE : null);
        }

        @Override
        protected void sample(EventSheet s) {
            Map<String, Integer> items = new LinkedHashMap<>();
            items.put("Combat XP", 18_400);
            items.put("Foraging XP", 6_250);
            items.put("Refined Bottle of Jyrre", 3);
            items.put("Enchanted Book", 1);
            winterSheet(s, 186, 24, 37, 2_456_000, items, "12m 3s left");
        }

        @Override
        protected List<Row> plainRows() {
            List<Row> out = new ArrayList<>();
            if (gifts > 0) out.add(new Row("Gifts opened", String.valueOf(gifts), Theme.current().text()));
            if (giftCoins > 0) out.add(new Row("Coins", String.format(Locale.ROOT, "%,d", giftCoins), Theme.current().accent()));
            if (northStars > 0) out.add(new Row("North Stars", String.valueOf(northStars), 0xFFFF55FF));
            for (Map.Entry<String, Integer> e : giftItems.entrySet()) out.add(new Row(e.getKey(), String.format(Locale.ROOT, "%,d", e.getValue()), Theme.current().dim()));
            if (uniqueGifts > 0) out.add(new Row("Unique gifts given", String.valueOf(uniqueGifts), Theme.current().good()));
            if (jyrreVibeUntil > System.currentTimeMillis()) out.add(new Row("Jyrre", ago(2 * System.currentTimeMillis() - jyrreVibeUntil) + " left", 0xFF55FFFF));
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Gifts opened", "12", 0), new Row("Coins", "25,000", Theme.current().accent()), new Row("North Stars", "9", 0xFFFF55FF));
        }
    };

    private static void beachSheet(EventSheet s, int best, int fishy) {
        s.tiles(EventSheet.tile("Best bounces", sep(best), Modern.GOOD, "Bounces"), EventSheet.tile("Fishy Treats", sep(fishy), Modern.GOLD, "Treats"));
    }

    public static final HudElement BEACH_EL = new EventSheet.Card("beachball_panel", "Beach ball", "Events", Zone.TOP_LEFT, 50, "beachball_panel") {
        @Override
        public boolean visible() {
            return sb() && treats > 0;
        }

        @Override
        protected void live(EventSheet s) {
            beachSheet(s, bestBounces, treats);
        }

        @Override
        protected void sample(EventSheet s) {
            beachSheet(s, 38, 126);
        }

        @Override
        protected List<Row> plainRows() {
            return List.of(new Row("Best bounces", String.valueOf(bestBounces), Theme.current().good()), new Row("Fishy Treats", String.valueOf(treats), Theme.current().accent()));
        }

        @Override
        protected List<Row> plainDemo() {
            return plainRows();
        }
    };

    public static void register() {
        Features.add(new Feature("diana_panel", "Events", "Diana card", "Burrows and chain progress, coins and feathers dug, mythological mobs and treasures.", "").when(Events::hub));
        Features.add(new Feature("carnival_panel", "Events", "Carnival fruit digging", "The last hint (fruit, bombs or nothing nearby) and how many spots you dug.", "").when(Events::hub));
        Features.add(new Feature("spook_panel", "Events", "Great Spook", "The quick-maths answer and the Primal Fears you met.", "").when(Events::sb));
        Features.add(new Feature("spook_maths", "Events", "Quick maths solver", "Solves the QUICK MATHS question and shows the answer.", "").when(Events::sb)
                .option(io.github.zmuzy.sbc.feature.Option.bool("spook_maths.click", "Answer in the chat line", "The answer after the question, with a click that says it in all chat.", false)));
        Features.add(new Feature("spook_alerts", "Events", "Spook alerts", "Trick-or-Treat and Party chests, the word the Public Speaking Demon wants, and Primal Fear spawns.", "").when(Events::sb)
                .option(io.github.zmuzy.sbc.feature.Option.bool("spook_alerts.compact_title", "Short chest title", "Only the kind of chest.", false))
                .option(io.github.zmuzy.sbc.feature.Option.bool("spook_alerts.sound", "Sound for chests", "", true)));
        Features.add(new Feature("winter_panel", "Events", "Winter gifting", "Gifts opened, coins and North Stars, items and XP from gifts, unique gifts given, Jyrre time left.", "").when(Events::sb)
                .option(io.github.zmuzy.sbc.feature.Option.bool("winter_panel.jyrre_inactive", "Show Jyrre when it runs out", "Keeps the line, saying Inactive, instead of removing it.", false)));
        Features.add(new Feature("beachball_panel", "Events", "Beach Ball bounces", "Best bounces and Fishy Treats from the Bouncy Beach Ball.", "").when(Events::sb));
        ChatBus.register(ChatBus.OBSERVE + 17, "events", null, Events::onChat);
        HudLayout.add(DIANA);
        HudLayout.add(CARNIVAL);
        HudLayout.add(SPOOK);
        HudLayout.add(WINTER);
        HudLayout.add(BEACH_EL);
    }
}
