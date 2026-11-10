// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.areas;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.TwoForms;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.Toasts;
import io.muzy.sbc.ui.hud.Compact;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// fishing, foraging, hunting helpers from chat + tab: fishing session (catches, coins, double hooks, trophy
// fish by tier, golden fish), tree progress, compact sweep lines
public final class FishForageHunt {
    private FishForageHunt() {}

    // fishing
    static final Pattern COINS = Pattern.compile("^.*?(?:GOOD|GREAT|OUTSTANDING) CATCH! You caught ([\\d,]+) Coins!$");
    static final Pattern DOUBLE_HOOK = Pattern.compile("^It's a Double Hook!.*$");
    static final Pattern TROPHY = Pattern.compile("^.*?TROPHY FISH! You caught (?:an? )?([A-Za-z' -]+?) (BRONZE|SILVER|GOLD|DIAMOND)!$");
    static final Pattern GOLDEN_SPAWN = Pattern.compile("^You spot a Golden Fish surface from beneath the lava!$");
    static final Pattern GOLDEN_DESPAWN = Pattern.compile("^The Golden Fish swims back beneath the lava\\.\\.\\.$");
    // foraging
    static final Pattern SWEEP = Pattern.compile("^Sweep Details: ([\\d,.]+)\\u222E Sweep$");
    static final Pattern PENALTY = Pattern.compile("^(Axe throw|Wrong Style|[A-Za-z ]+): (-\\d+% Sweep) ([\\d.]+ Logs)(?: (.*))?$");
    static final Pattern TOUGHNESS = Pattern.compile("^(.+?) Tree Toughness: ([\\d.,]+) ([\\d.,]+) Logs$");
    static final Pattern HIVE = Pattern.compile("^You stick your hand into the honeyhive and feel around\\.\\.\\.$");
    static final Pattern QUEEN = Pattern.compile("^QUEEN BEE! The Honeyhive instantly refilled with loot!$");
    static final Pattern PHANTOM = Pattern.compile("^A (Phanpyre|Phanflare|Dreadwing) fell from the Tree!$");
    // hunting
    static final Pattern PURE = Pattern.compile("^PURE REPTILE$");

    private static long sessionStart, coins, lastGolden, lastHive;
    private static int catches, doubles, trophies, creatures;
    private static final Map<String, Integer> tiers = new LinkedHashMap<>(), creatureRarity = new LinkedHashMap<>(), creatureNames = new LinkedHashMap<>();
    private static final Map<String, String> creatureNameRarity = new HashMap<>();
    private static long lastDoubleHook;
    private static String lastCreature = "";
    private static boolean goldenUp;
    private static String sweep = "";
    private static final List<String> penalties = new ArrayList<>();
    private static long sweepAt, lastSweepAt;
    private static String sweepTree = "", sweepToughness = "", sweepLogs = "";

    // last sweep details (sweep, tree, toughness, logs, penalties) for the card. null after 5 sec like skyblockers listener
    static String[] lastSweep() {
        if (sweep.isEmpty() || System.currentTimeMillis() - lastSweepAt > 5000) return null;
        return new String[]{sweep, sweepTree, sweepToughness, sweepLogs, String.join(", ", penalties)};
    }

    public static boolean fishingRodHeld() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return io.muzy.sbc.SbcMod.DEV;
        return GameState.strip(mc.player.getMainHandItem().getHoverName().getString()).toLowerCase(Locale.ROOT).contains("rod");
    }

    private static boolean foraging() {
        return "THE_PARK".equals(GameState.island) || "GALATEA".equals(GameState.island) || "TORRHUS_CANYON".equals(GameState.island) || io.muzy.sbc.SbcMod.DEV;
    }

    public static void reset() {
        sessionStart = coins = 0;
        catches = doubles = trophies = creatures = 0;
        tiers.clear();
        creatureRarity.clear();
        creatureNames.clear();
        creatureNameRarity.clear();
        lastCreature = "";
        goldenUp = false;
        sweep = "";
        penalties.clear();
    }

    // pure: one colorless chat line. "" = hide (compacted), a replacement text, or null to pass
    public static String feed(String plain) {
        String t = plain.trim();
        Matcher m;
        if ((m = COINS.matcher(t)).matches()) {
            if (sessionStart == 0) sessionStart = System.currentTimeMillis();
            coins += Long.parseLong(m.group(1).replace(",", ""));
            catches++;
            return null;
        }
        if (DOUBLE_HOOK.matcher(t).matches()) {
            doubles++;
            lastDoubleHook = System.currentTimeMillis();
            return null;
        }
        // sea creatures by their exact announcement (skyhanni SeaCreatures.json), a double hook right before counts twice
        io.muzy.sbc.core.SeaCreatures.Creature sc = io.muzy.sbc.core.SeaCreatures.fromChat(t);
        if (sc != null) {
            int n = System.currentTimeMillis() - lastDoubleHook < 1000 ? 2 : 1;
            lastDoubleHook = 0;
            creatures += n;
            creatureRarity.merge(sc.rarity(), n, Integer::sum);
            creatureNames.merge(sc.name(), n, Integer::sum);
            creatureNameRarity.put(sc.name(), sc.rarity());
            lastCreature = sc.name();
            if (sessionStart == 0) sessionStart = System.currentTimeMillis();
            if ((sc.rarity().equals("LEGENDARY") || sc.rarity().equals("MYTHIC")) && Features.on("fishing_session")) alert(sc.name() + "!", sc.rarity().charAt(0) + sc.rarity().substring(1).toLowerCase(Locale.ROOT) + " sea creature", 0xFFFFAA00);
            return null;
        }
        if ((m = TROPHY.matcher(t)).matches()) {
            trophies++;
            tiers.merge(m.group(2), 1, Integer::sum);
            if (sessionStart == 0) sessionStart = System.currentTimeMillis();
            return null;
        }
        if (GOLDEN_SPAWN.matcher(t).matches()) {
            goldenUp = true;
            lastGolden = System.currentTimeMillis();
            return null;
        }
        if (GOLDEN_DESPAWN.matcher(t).matches()) {
            goldenUp = false;
            return null;
        }
        if ((m = SWEEP.matcher(t)).matches()) {
            sweep = m.group(1);
            penalties.clear();
            sweepTree = sweepToughness = sweepLogs = "";
            sweepAt = lastSweepAt = System.currentTimeMillis();
            return Features.on("foraging_sweep") ? "" : null;
        }
        if ((m = TOUGHNESS.matcher(t)).matches() && System.currentTimeMillis() - sweepAt < 1000) {
            sweepTree = m.group(1);
            sweepToughness = m.group(2);
            sweepLogs = m.group(3);
            return Features.on("foraging_sweep") ? "" : null;
        }
        if ((m = PENALTY.matcher(t)).matches() && System.currentTimeMillis() - sweepAt < 1000) {
            penalties.add(m.group(1) + " " + m.group(2));
            sweepLogs = m.group(3).replace(" Logs", "");
            return Features.on("foraging_sweep") ? "" : null;
        }
        if (HIVE.matcher(t).matches()) {
            lastHive = System.currentTimeMillis();
            return null;
        }
        if (QUEEN.matcher(t).matches()) {
            if (Features.on("foraging_hive") && Features.get("foraging_hive").option("foraging_hive.queen").bool()) io.muzy.sbc.areas.FishHooks.title("§6Honeyhive Instantly Refilled!");
            return null;
        }
        if ((m = PHANTOM.matcher(t)).matches()) {
            if (Features.on("foraging_hive")) alert("Phantom!", m.group(1) + " fell from the tree", 0xFFFF55FF);
            return null;
        }
        if (PURE.matcher(t).matches()) {
            if (Features.on("hunting_fusion")) alert("PURE REPTILE", "Fusion gave a pure reptile", 0xFF55FFFF);
            return null;
        }
        return null;
    }

    private static void alert(String title, String body, int color) {
        Toasts.push(title, body, color);
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.4f);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String r = feed(line.plain());
        if (r == null) return ChatBus.PASS;
        if (r.isEmpty()) {
            // compact sweep line shows once the penalties arrived
            if (!sweep.isEmpty() && SWEEP.matcher(line.plain().trim()).matches()) return ChatBus.CANCEL;
            return ChatBus.CANCEL;
        }
        return new ChatBus.Replace(net.minecraft.network.chat.Component.literal(r));
    }

    private static void tick(Minecraft mc) {
        // compact sweep: once the penalty lines stop (300ms) print one line
        if (!sweep.isEmpty() && sweepAt != 0 && System.currentTimeMillis() - sweepAt > 300 && Features.on("foraging_sweep")) {
            String line = "§6Sweep §2" + sweep + "∮" + (sweepLogs.isEmpty() ? "" : " §7-> §a" + sweepLogs + " " + sweepTree + " logs") + (penalties.isEmpty() ? "" : " §7(" + String.join(", ", penalties) + ")");
            sweepAt = 0;
            if (mc.gui != null) {
                ChatBus.replaying = true;
                try {
                    mc.gui.hud.getChat().addServerSystemMessage(net.minecraft.network.chat.Component.literal(line));
                } finally {
                    ChatBus.replaying = false;
                }
            }
        }
    }

    private static void hiveTick(Minecraft mc) {
        if (lastHive != 0 && System.currentTimeMillis() - lastHive > 2 * 3_600_000L) {
            lastHive = 0;
            Toasts.push("Honeyhive", "Two hours passed - the honeyhive should be full again", 0xFFFFD866);
        }
    }

    private static String perHour(long n) {
        if (sessionStart == 0) return "";
        double h = (System.currentTimeMillis() - sessionStart) / 3_600_000.0;
        return h < 0.02 ? "" : String.format(Locale.ROOT, "%,.0f/h", n / h);
    }

    // --- fishing ---

    private static final String[] RARITIES = {"MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"}, TIERS = {"DIAMOND", "GOLD", "SILVER", "BRONZE"};

    private static int rarityColor(String rarity) {
        return switch (rarity) {
            case "MYTHIC" -> 0xFFFF6FD8;
            case "LEGENDARY" -> 0xFFFFB627;
            case "EPIC" -> 0xFFC77DFF;
            case "RARE" -> 0xFF6F9BFF;
            case "UNCOMMON" -> 0xFF5FEA8C;
            default -> 0xFFE8EEF4;
        };
    }

    private static int tierColor(String tier) {
        return switch (tier) {
            case "DIAMOND" -> 0xFF7FE8FF;
            case "GOLD" -> 0xFFFFD166;
            case "SILVER" -> 0xFFD3DDE6;
            default -> 0xFFD79A62;
        };
    }

    private static String pretty(String upper) {
        return upper.charAt(0) + upper.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String num(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // per hour once the session is long enough to mean something (72 sec), "" till then
    private static String rate(long n, long ms) {
        double h = ms / 3_600_000.0;
        return h < 0.02 ? "" : "§b" + io.muzy.sbc.core.Vitals.compact(n / h) + "/h";
    }

    // everything the fishing card can say, so the editor sample goes through the same layout as live numbers
    private record Session(long ms, long coins, int catches, int doubles, int trophies, int creatures, Map<String, Integer> tiers, Map<String, Integer> rarities,
                           Map<String, Integer> names, Map<String, String> nameRarity, String last, boolean golden, long goldenAgoS) {}

    // number in the compact short form, no comma left that the color code in front could join
    private static String brief(long n) {
        return Compact.value(num(n)).replace(",", "");
    }

    // compact: trophy tiers share rows, the coins total goes in their heading like creatures + trophies, unlisted
    // kinds get counted in the heading
    private static RichCard.Content fishing(Session s, boolean compact) {
        List<RichCard.Line> out = new ArrayList<>();
        if (s.golden()) out.add(RichCard.Line.row("Golden Fish", (compact ? "§6" : "§6surfaced ") + s.goldenAgoS() + "s ago").icon("minecraft:gold_ingot").chip("UP", Modern.GOLD).raw());
        if (s.creatures() > 0) {
            String rate = rate(s.creatures(), s.ms());
            out.add(RichCard.Line.section("Sea creatures", "§f" + num(s.creatures()) + (rate.isEmpty() ? "" : "  " + rate)));
            List<RichCard.Seg> shares = new ArrayList<>();
            for (String r : RARITIES) shares.add(new RichCard.Seg(s.rarities().getOrDefault(r, 0), rarityColor(r)));
            out.add(RichCard.Line.segments("Rarities", 1, shares));
            for (String r : RARITIES) {
                int n = s.rarities().getOrDefault(r, 0);
                if (n > 0) out.add(RichCard.Line.table(pretty(r), "§f" + num(n), "§7" + RowText.percent((double) n / s.creatures()), rate(n, s.ms())).key("Rarities").labelColor(rarityColor(r)));
            }
            if (!compact) out.add(RichCard.Line.rule());
            if (s.doubles() > 0) {
                int bites = s.creatures() - s.doubles();
                out.add(RichCard.Line.table("Double hooks", "§a" + num(s.doubles()), bites > 0 && s.doubles() <= bites ? "§7" + RowText.percent((double) s.doubles() / bites) : "", rate(s.doubles(), s.ms())));
            }
            if (!s.last().isEmpty()) out.add(RichCard.Line.row("Last", s.last(), rarityColor(s.nameRarity().getOrDefault(s.last(), "COMMON"))).raw());
            List<Map.Entry<String, Integer>> byCount = new ArrayList<>(s.names().entrySet());
            byCount.sort((a, b) -> b.getValue() - a.getValue());
            out.add(compact && byCount.size() > 6 ? RichCard.Line.section("Caught", "§7+" + (byCount.size() - 6) + " more kinds") : RichCard.Line.section("Caught"));
            int shown = 0;
            for (Map.Entry<String, Integer> e : byCount) {
                if (shown++ == 6) {
                    if (!compact) out.add(RichCard.Line.row("§7and " + (byCount.size() - 6) + " more kinds", "").key("Caught").raw());
                    break;
                }
                out.add(RichCard.Line.table(e.getKey(), "§f" + num(e.getValue()), "§7" + RowText.percent((double) e.getValue() / s.creatures())).key("Caught").labelColor(rarityColor(s.nameRarity().getOrDefault(e.getKey(), "COMMON"))));
            }
        }
        if (s.trophies() > 0) {
            String rate = rate(s.trophies(), s.ms());
            out.add(RichCard.Line.section("Trophy fish", "§f" + num(s.trophies()) + (rate.isEmpty() ? "" : "  " + rate)));
            List<RichCard.Seg> shares = new ArrayList<>();
            for (String t : TIERS) shares.add(new RichCard.Seg(s.tiers().getOrDefault(t, 0), tierColor(t)));
            out.add(RichCard.Line.segments("Trophy fish", 1, shares));
            for (String t : TIERS) {
                int n = s.tiers().getOrDefault(t, 0);
                if (n > 0 && compact) out.add(RichCard.Line.row(pretty(t), "§f" + num(n) + " §7" + Math.round(100.0 * n / s.trophies()) + "%").key("Trophy fish").labelColor(tierColor(t)).raw());
                else if (n > 0) out.add(RichCard.Line.table(pretty(t), "§f" + num(n), "§7" + RowText.percent((double) n / s.trophies())).key("Trophy fish").labelColor(tierColor(t)));
            }
        }
        if (s.catches() > 0 && compact) {
            String rate = rate(s.coins(), s.ms()), catchRate = rate(s.catches(), s.ms());
            out.add(RichCard.Line.section("Coins", "§6" + brief(s.coins()) + (rate.isEmpty() ? "" : "  " + rate)).key("Coins"));
            out.add(RichCard.Line.row("Catch", "§f" + num(s.catches()) + (catchRate.isEmpty() ? "" : " " + catchRate)).key("Coin catches").raw());
            out.add(RichCard.Line.row("/catch", "§6" + brief(s.coins() / s.catches())).key("Coins").raw());
        } else if (s.catches() > 0) {
            out.add(RichCard.Line.section("Coins"));
            out.add(RichCard.Line.table("Coin catches", "§f" + num(s.catches()), rate(s.catches(), s.ms())));
            out.add(RichCard.Line.table("Coins", "§6" + num(s.coins()), rate(s.coins(), s.ms())));
            out.add(RichCard.Line.table("Per catch", "§6" + num(s.coins() / s.catches()), "").key("Coins"));
        }
        return new RichCard.Content(s.ms() > 0 ? "§7" + io.muzy.sbc.events.hoppity.HoppityEnv.format(s.ms(), 2) : "", out);
    }

    public static final HudElement FISHING = new RichCard("fishing_session", "Fishing", "Fishing", Zone.TOP_LEFT, 38, "fishing_session") {
        @Override
        public boolean visible() {
            return fishingRodHeld() && (catches > 0 || trophies > 0 || goldenUp || creatures > 0);
        }

        private Content both(Session s) {
            return TwoForms.of(this::compact, form -> fishing(s, form));
        }

        @Override
        protected Content live() {
            return both(new Session(sessionStart == 0 ? 0 : System.currentTimeMillis() - sessionStart, coins, catches, doubles, trophies, creatures, tiers, creatureRarity, creatureNames, creatureNameRarity, lastCreature, goldenUp, (System.currentTimeMillis() - lastGolden) / 1000));
        }

        @Override
        protected Content sample() {
            Map<String, Integer> names = new LinkedHashMap<>(), kinds = new LinkedHashMap<>(), trophy = new LinkedHashMap<>(), rarity = new LinkedHashMap<>();
            Map<String, String> nameRarity = new LinkedHashMap<>();
            Object[][] sample = {{"Night Squid", "COMMON", 41}, {"Frozen Steve", "COMMON", 33}, {"Sea Archer", "UNCOMMON", 21}, {"Rider of the Deep", "RARE", 14}, {"Catfish", "RARE", 9}, {"Sea Leech", "EPIC", 4}, {"Water Hydra", "LEGENDARY", 2}, {"Lord Jawbus", "MYTHIC", 1}};
            for (Object[] c : sample) {
                names.put((String) c[0], (Integer) c[2]);
                nameRarity.put((String) c[0], (String) c[1]);
                rarity.merge((String) c[1], (Integer) c[2], Integer::sum);
            }
            trophy.put("BRONZE", 14);
            trophy.put("SILVER", 7);
            trophy.put("GOLD", 2);
            trophy.put("DIAMOND", 1);
            return both(new Session(23 * 60_000L + 12_000L, 1_412_000L, 14, 11, 24, 125, trophy, rarity, names, nameRarity, "Water Hydra", true, 12));
        }

        @Override
        public List<Content> states() {
            Map<String, Integer> justStarted = Map.of("Sea Walker", 2), justStartedRarity = Map.of("COMMON", 2), trophy = Map.of("BRONZE", 3);
            return List.of(both(new Session(40_000L, 0, 0, 0, 0, 2, Map.of(), justStartedRarity, justStarted, Map.of("Sea Walker", "COMMON"), "Sea Walker", false, 0)),
                    both(new Session(600_000L, 88_000L, 3, 0, 3, 0, trophy, Map.of(), Map.of(), Map.of(), "", false, 0)),
                    both(new Session(0, 0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), Map.of(), "", true, 5)));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            if (catches > 0) {
                out.add(new Row("Coin catches", catches + "  " + perHour(catches), Theme.current().text()));
                out.add(new Row("Coins", String.format(Locale.ROOT, "%,d  %s", coins, perHour(coins)).trim(), Theme.current().accent()));
            }
            if (doubles > 0) out.add(new Row("Double hooks", String.valueOf(doubles), Theme.current().good()));
            if (creatures > 0) {
                StringBuilder r = new StringBuilder();
                for (Map.Entry<String, Integer> e : creatureRarity.entrySet()) r.append(e.getKey().charAt(0)).append(e.getValue()).append(' ');
                out.add(new Row("Sea creatures", creatures + "  " + r.toString().trim() + "  " + perHour(creatures), 0xFF55FFFF));
                if (!lastCreature.isEmpty()) out.add(new Row("Last", lastCreature, Theme.current().dim()));
            }
            if (trophies > 0) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, Integer> e : tiers.entrySet()) sb.append(e.getKey().charAt(0)).append(e.getValue()).append(' ');
                out.add(new Row("Trophy fish", trophies + "  " + sb.toString().trim(), 0xFFFFAA00));
            }
            if (goldenUp) out.add(new Row("Golden Fish", "surfaced " + ((System.currentTimeMillis() - lastGolden) / 1000) + "s ago", 0xFFFFD866));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Coin catches", "14  52/h", 0), new Row("Coins", "412,000  1.5M/h", Theme.current().accent()), new Row("Trophy fish", "6  B4 S2", 0xFFFFAA00));
        }
    };

    // --- tree ---

    // from skyhanni TreeProgressDisplay.isHoldingAxe: the items category, last word of its last line
    private static boolean holdingAxe() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return io.muzy.sbc.SbcMod.DEV;
        return io.muzy.sbc.item.menus.MenuView.Item.of(mc.player.getMainHandItem()).lastClean().endsWith(" AXE");
    }

    private static String[] treeRead;
    private static long treeReadAt;

    // from skyhanni TreeProgressDisplay: "FIG TREE 88%" is an armor stand name at the tree, the closest one is
    // your tree. goes when the stand does
    private static String[] readTree() {
        long now = System.currentTimeMillis();
        if (now - treeReadAt < 250) return treeRead;
        treeReadAt = now;
        treeRead = null;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        double best = Double.MAX_VALUE;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand) || e.getCustomName() == null) continue;
            Matcher m = io.muzy.sbc.core.Patterns.get("SH.foraging.tree.progress.colorless").matcher(GameState.strip(e.getCustomName().getString()).trim());
            if (!m.matches()) continue;
            double d = e.distanceToSqr(mc.player);
            if (d >= best) continue;
            best = d;
            treeRead = new String[]{m.group("treeType") + " TREE", m.group("percent")};
        }
        return treeRead;
    }

    private static RichCard.Content tree(String upperName, int percent) {
        StringBuilder name = new StringBuilder();
        for (String w : upperName.split(" ")) name.append(name.length() > 0 ? " " : "").append(pretty(w));
        String value = "§a" + percent + "%" + (percent < 100 ? "  §7" + (100 - percent) + "% left" : "");
        return new RichCard.Content("", List.of(RichCard.Line.row(name.toString(), value).key("Tree").raw(), RichCard.Line.bar("Tree meter", percent / 100.0, percent >= 100 ? Modern.GOOD : 0xFF81C784)));
    }

    public static final HudElement TREE_EL = new RichCard("foraging_tree", "Tree progress", "Foraging", Zone.TOP_LEFT, 38, "foraging_tree") {
        @Override
        public boolean visible() {
            return foraging() && readTree() != null && (!Features.get("foraging_tree").option("foraging_tree.only_axe").bool() || holdingAxe());
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        protected Content live() {
            String[] r = readTree();
            return r == null ? new Content("", List.of()) : tree(r[0], Integer.parseInt(r[1]));
        }

        @Override
        protected Content sample() {
            return tree("FIG TREE", 88);
        }

        @Override
        public List<Content> states() {
            return List.of(tree("MANGROVE TREE", 5), tree("FIG TREE", 100));
        }

        @Override
        protected List<Row> classicRows() {
            String[] r = readTree();
            if (r == null) return List.of();
            String name = r[0].charAt(0) + r[0].substring(1).toLowerCase(Locale.ROOT);
            return List.of(new Row(name, r[1] + "%", Theme.current().good()));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Fig tree", "88%", Theme.current().good()));
        }
    };

    public static void register() {
        Feature fish = Features.add(new Feature("fishing_session", "Fishing", "Fishing session", "Sea creatures by rarity and per hour, coin catches, double hooks, trophy fish by tier, when the Golden Fish surfaced; an alert on legendary and mythic catches.", "").when(() -> GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV));
        Features.add(new Feature("foraging_tree", "Foraging", "Tree progress", "How far the tree you are cutting is.", "").when(FishForageHunt::foraging)
                .option(Option.bool("foraging_tree.only_axe", "Only holding an axe", "", true)));
        Features.add(new Feature("foraging_sweep", "Foraging", "Compact sweep details", "The Sweep Details block becomes one line with the penalties in brackets.", "").when(FishForageHunt::foraging).tick(FishForageHunt::tick));
        Features.add(new Feature("foraging_hive", "Foraging", "Honeyhive and phantom alerts", "A reminder two hours after you looted a honeyhive, the honeyhives you have not looted yet marked on the Torrhus Canyon, and an alert when a phantom falls from a tree.", "SkyHanni: Honeyhive Reminder").when(FishForageHunt::foraging).tick(FishForageHunt::hiveTick)
                .option(Option.bool("foraging_hive.queen", "Title when a Queen Bee refills it", "", true))
                .option(Option.bool("foraging_hive.marks", "Mark the other honeyhives after a loot", "", true)));
        Features.add(new Feature("hunting_fusion", "Hunting", "Pure Reptile alert", "A sound and a note when a fusion gives a Pure Reptile.", "").when(() -> GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV));
        ChatBus.register(ChatBus.OBSERVE + 15, "fishforagehunt", null, FishForageHunt::onChat);
        HudLayout.add(FISHING);
        HudLayout.add(TREE_EL);
    }
}
