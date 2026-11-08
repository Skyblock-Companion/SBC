// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.track;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemIcons;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.TwoForms;
import dev.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import dev.zmuzy.sbc.nav.Slayers;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.Compact;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// money trackers like skyhannis: each watches what you gain doing one thing (ItemAddManager inventory + sack
// gains, PurseApi coins from kills, and that things own chat lines)
public final class Profit {
    public static final String COINS = "COINS";
    // what a tracker paid to do its thing (keys, fuel): comes off the total, own line
    public static final String COST = "COST";
    private static final List<Session> SESSIONS = new ArrayList<>();
    // you stopped after this long with no gain, the clock waits till the next one
    private static final long RECENT_MS = 10_000;
    private static final String SETTINGS = "tracker_settings";

    private static dev.zmuzy.sbc.feature.Option setting(String key) {
        Feature f = Features.get(SETTINGS);
        return f == null ? null : f.option(key);
    }

    private static boolean setting(String key, boolean def) {
        var o = setting(key);
        return o == null ? def : o.bool();
    }

    private static int setting(String key, int def) {
        var o = setting(key);
        return o == null ? def : o.integer();
    }

    private static long idleMs() {
        return setting("tracker.afkTimeout", 60) * 1000L;
    }

    private static int maxItems() {
        return setting("tracker.itemsShown", 6);
    }

    // from skyhanni SkyHanniItemTracker expensive drop warning: chat line + title, each with its own price
    static void warn(String id, double amount) {
        double price = dev.zmuzy.sbc.core.Gain.ofId(id) * amount;
        boolean chat = setting("tracker.chat", true) && price >= setting("tracker.minimumChat", 5_000_000);
        boolean title = setting("tracker.title", true) && price >= setting("tracker.minimumTitle", 5_000_000);
        if (!chat && !title) return;
        String name = ItemRepo.coloredName(id);
        if (name == null || name.isEmpty()) name = pretty(id);
        String text = (amount == 1 ? "" : "§8" + num(amount) + "x ") + name + " §7(§6" + Prices.coins(price) + "§7)";
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (chat && mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a+Tracker Drop§7: §r" + text));
        if (title) {
            mc.gui.hud.setTimes(5, 40, 10);
            mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§a+ " + name));
            mc.gui.hud.setSubtitle(net.minecraft.network.chat.Component.empty());
        }
    }

    // settings every tracker shares
    public static void registerSettings() {
        Features.add(new Feature(SETTINGS, "Tracking", "Tracker settings", "What every profit tracker shows and how it counts.", "SkyHanni: tracker settings").alwaysOn()
                .option(Option.integer("tracker.itemsShown", "Items shown", "The rest add up in one line.", 6, 1, 30))
                .option(Option.bool("tracker.profitPerHour", "Profit per hour", "", true))
                .option(Option.bool("tracker.showUptime", "Time spent", "How long the tracker has been active.", true))
                .option(Option.integer("tracker.afkTimeout", "Pause after (seconds)", "The clock stops when nothing was gained for this long.", 60, 5, 600))
                .option(Option.bool("tracker.recentDrops", "Recent drops in green", "The amount of what you just gained stands out.", true))
                .option(Option.bool("tracker.hideOutsideInventory", "Only with a menu open", "Hide trackers while no inventory is open.", false))
                .option(Option.bool("tracker.chat", "Expensive drop in chat", "A line in chat when you gain something expensive.", true))
                .option(Option.integer("tracker.minimumChat", "In chat from (coins)", "", 5_000_000, 0, 2_000_000_000))
                .option(Option.bool("tracker.title", "Expensive drop as a title", "A title when you gain something expensive.", true))
                .option(Option.integer("tracker.minimumTitle", "As a title from (coins)", "", 5_000_000, 0, 2_000_000_000))
                .option(Option.choice("tracker.priceSource", "Price source", "Best known is what the rest of the mod shows.", "BEST_KNOWN", "BEST_KNOWN", dev.zmuzy.sbc.item.market.PriceSource.INSTANT_BUY, dev.zmuzy.sbc.item.market.PriceSource.INSTANT_SELL, dev.zmuzy.sbc.item.market.PriceSource.NPC)));
    }

    // what one tracker has so far
    public static final class Session {
        final String id;
        final Trackers.Named data, numbers;
        private final Map<String, Long> gainedAt = new java.util.HashMap<>();
        private long activeMs, lastAt;

        Session(String id, String title) {
            this(id, Trackers.get("profit_" + id, title));
        }

        Session(String id, Trackers.Named data) {
            this.id = id;
            this.data = data;
            numbers = Trackers.numbers("profit_" + id + "_numbers", data.title);
            SESSIONS.add(this);
        }

        private void touch() {
            long now = System.currentTimeMillis();
            if (lastAt != 0 && now - lastAt < idleMs()) {
                activeMs += now - lastAt;
                // kept across game starts too so a per hour rate can come from all your time with it
                dev.zmuzy.sbc.core.Store.I.trackerMs.merge(id, now - lastAt, Long::sum);
                dev.zmuzy.sbc.core.Store.markDirty();
            }
            lastAt = now;
        }

        // last time something worth coins came in (not just counted) + what came in lately, for cards that only show
        // while earning
        private long earnedAt;
        private final java.util.ArrayDeque<double[]> earned = new java.util.ArrayDeque<>();

        boolean earnedWithin(long ms) {
            return earnedAt != 0 && System.currentTimeMillis() - earnedAt < ms;
        }

        // coins earned in the last ms
        double earnedSince(long ms) {
            long from = System.currentTimeMillis() - ms;
            while (!earned.isEmpty() && earned.peekFirst()[0] < from) earned.pollFirst();
            double sum = 0;
            for (double[] e : earned) sum += e[1];
            return sum;
        }

        private void earn(double coins) {
            if (coins <= 0) return;
            long now = System.currentTimeMillis();
            earnedAt = now;
            earned.addLast(new double[]{now, coins});
            while (earned.size() > 4096) earned.pollFirst();
        }

        void item(String id, long amount) {
            earn(data.add(id, amount));
            noted(id);
        }

        // something else counted this item, only the clock + the green amount are left
        void noted(String id) {
            gainedAt.put(id, System.currentTimeMillis());
            touch();
        }

        // item at its own price, for stuff the market doesnt say
        void item(String id, long amount, double coins) {
            data.addEntry(id, amount, coins);
            earn(coins);
            noted(id);
        }

        void cost(double coins) {
            if (coins > 0) data.addEntry(COST, 1, -coins);
        }

        // from skyhanni SkyHanniItemTracker newDrop
        boolean recent(String id) {
            Long at = gainedAt.get(id);
            return at != null && System.currentTimeMillis() - at < RECENT_MS && setting("tracker.recentDrops", true);
        }

        void coins(double amount) {
            data.addEntry(COINS, amount, amount);
            earn(amount);
            touch();
        }

        void count(String what, double amount) {
            numbers.addEntry(what, amount, 0);
            touch();
        }

        // keeps the highest value, for stuff that doesnt add up
        void peak(String what, double value) {
            numbers.peak(what, value);
            touch();
        }

        double count(String what) {
            double[] v = numbers.items(Trackers.Scope.SESSION).get(what);
            return v == null ? 0 : v[0];
        }

        Map<String, Double> counts(Trackers.Scope s) {
            Map<String, Double> out = new LinkedHashMap<>();
            for (var e : numbers.items(s).entrySet()) out.put(e.getKey(), e.getValue()[0]);
            return out;
        }

        boolean any() {
            return any(Trackers.Scope.SESSION);
        }

        boolean any(Trackers.Scope s) {
            return !data.items(s).isEmpty() || !numbers.items(s).isEmpty();
        }

        double total(Trackers.Scope s) {
            return data.total(s)[1];
        }

        long activeMs() {
            return activeMs;
        }

        double perHour() {
            return activeMs < 30_000 ? 0 : total(Trackers.Scope.SESSION) * 3_600_000d / activeMs;
        }

        void reset() {
            reset(Trackers.Scope.SESSION);
            activeMs = lastAt = earnedAt = 0;
            earned.clear();
        }

        void reset(Trackers.Scope s) {
            data.reset(s);
            numbers.reset(s);
            if (s == Trackers.Scope.ALL) dev.zmuzy.sbc.core.Store.I.trackerMs.remove(id);
        }
    }

    static final Session MINING = new Session("mining", "Mining profit"), EXCAVATOR = new Session("excavator", "Excavator profit"),
            EXPERIMENTS = new Session("experiments", "Experiments profit"), WINTER = new Session("winter", "Jerry's Workshop profit");
    static final Session TOTAL = new Session("total", "Profit"), FARMING = new Session("farming", "Farming profit"), PESTS = new Session("pests", "Pest profit"),
            SLAYER = new Session("slayer", "Slayer profit"), FISHING = new Session("fishing", "Fishing profit"), FORAGING = new Session("foraging", "Foraging");

    // what a tracker measured over all your play: coins/h + the hours behind it
    public record Measured(double perHour, double hours) {}

    // all time coins over all time active time once it has 10 min. null before that or no such tracker
    public static Measured measured(String trackerId) {
        long ms = dev.zmuzy.sbc.core.Store.I.trackerMs.getOrDefault(trackerId, 0L);
        if (ms < 10 * 60_000L) return null;
        for (Session s : SESSIONS) {
            if (!s.id.equals(trackerId)) continue;
            double coins = s.total(Trackers.Scope.ALL);
            return new Measured(coins * 3_600_000d / ms, ms / 3_600_000d);
        }
        return null;
    }

    static String num(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    static String time(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "s";
        if (s < 3600) return s / 60 + "m " + s % 60 + "s";
        return s / 3600 + "h " + s / 60 % 60 + "m";
    }

    // where it sells: "BZ" bazaar, "AH" auction house, "" neither
    static String market(String id) {
        if (Prices.get(id) != null) return "BZ";
        return AuctionPrices.lowestBin(id) > 0 ? "AH" : "";
    }

    // price in its own color when it beats the other way of selling, plain grey when not, dash when none
    private static String priceCell(double price, double other, String best, boolean compact) {
        return price <= 0 ? "§8-" : (price >= other ? best : "§7") + (compact ? brief(price) : Prices.coins(price));
    }

    // number in the compact short form, no comma left the color code in front could join
    static String brief(double v) {
        return Compact.value(num(v)).replace(",", "");
    }

    // npc price for one, 0 = no npc buys it
    private static double npc(String id) {
        return Math.max(0, ItemRepo.npcPrice(id));
    }

    // --- the card ---

    // tracker card: total, per hour + for how long, its own numbers, then items most valuable first
    abstract static class Card extends RichCard {
        private final Session session;
        private final Map<String, ItemStack> icons = new HashMap<>();

        Card(String id, String title, String section, int priority, Session session) {
            super(id, title, section, Zone.TOP_LEFT, priority, id);
            this.session = session;
        }

        // are you doing this thing rn
        abstract boolean relevant();

        // its own numbers
        List<Line> own(Map<String, Double> counts) {
            return List.of();
        }

        abstract Map<String, double[]> sampleItems();

        Map<String, Double> sampleCounts() {
            return Map.of();
        }

        // top right text, a tracker not counting coins says its own
        String heading(double total, Map<String, Double> counts) {
            return "§6" + Prices.coins(total);
        }

        Trackers.Scope scope() {
            Feature f = feature();
            Option o = f == null ? null : f.option("scope");
            String c = o == null ? "This session" : o.choice();
            return switch (c) {
                case "Today" -> Trackers.Scope.DAY;
                case "This week" -> Trackers.Scope.WEEK;
                case "All time" -> Trackers.Scope.ALL;
                default -> Trackers.Scope.SESSION;
            };
        }

        @Override
        public boolean visible() {
            if (setting("tracker.hideOutsideInventory", false) && net.minecraft.client.Minecraft.getInstance().gui.screen() == null) return false;
            return (GameState.inSkyBlock || SbcMod.DEV) && relevant() && session.any(scope());
        }

        @Override
        protected int maxWidth() {
            return 270;
        }

        private ItemStack icon(String id) {
            return icons.computeIfAbsent(id, k -> {
                try {
                    return ItemIcons.of(k);
                } catch (Exception e) {
                    return ItemStack.EMPTY;
                }
            });
        }

        private Content build(Map<String, double[]> items, Map<String, Double> counts, double perHour, long activeMs) {
            return TwoForms.of(this::compact, form -> form(items, counts, perHour, activeMs, form));
        }

        // compact: short numbers flow without rules so they pair, the items heading is also their columns heading,
        // item icons give their room to the names
        private Content form(Map<String, double[]> items, Map<String, Double> counts, double perHour, long activeMs, boolean compact) {
            double total = 0, coins = 0, npcTotal = 0, costs = 0;
            List<Map.Entry<String, double[]>> list = new ArrayList<>();
            for (Map.Entry<String, double[]> e : items.entrySet()) {
                total += e.getValue()[1];
                if (e.getKey().equals(COINS)) {
                    coins = e.getValue()[1];
                    npcTotal += coins;
                } else if (e.getKey().equals(COST)) {
                    costs = e.getValue()[1];
                    npcTotal += costs;
                } else {
                    list.add(e);
                    npcTotal += npc(e.getKey()) * e.getValue()[0];
                }
            }
            list.sort((a, b) -> {
                int byValue = Double.compare(b.getValue()[1], a.getValue()[1]);
                return byValue != 0 ? byValue : Double.compare(b.getValue()[0], a.getValue()[0]);
            });
            List<Line> out = new ArrayList<>();
            if (perHour > 0 && setting("tracker.profitPerHour", true)) out.add(Line.row("Per hour", "§6" + Prices.coins(perHour)));
            if (activeMs > 0 && setting("tracker.showUptime", true)) out.add(Line.row("Active", time(activeMs)));
            if (coins != 0) out.add(Line.row("Coins", "§6" + (compact ? brief(coins) : num(coins))));
            if (costs != 0) out.add(Line.row("Costs", "§c-" + (compact ? brief(-costs) : Prices.coins(-costs))));
            List<Line> own = own(counts);
            if (!own.isEmpty()) {
                if (!compact) out.add(Line.rule());
                out.addAll(own);
            }
            if (!list.isEmpty()) {
                if (compact) out.add(Line.table("Items  §7" + list.size() + " kinds", "", "§7BZ/AH", "§7NPC").key("Items").labelColor(accent()));
                else {
                    out.add(Line.section("Items", list.size() + " kinds"));
                    // one column for the market + one for npc, whichever pays more stands out so you see where to sell
                    out.add(Line.table("", "", "§7BZ / AH", "§7NPC").key("Items"));
                }
                double rest = 0, restNpc = 0;
                for (int i = 0; i < list.size(); i++) {
                    var e = list.get(i);
                    double count = e.getValue()[0], value = e.getValue()[1], atNpc = npc(e.getKey()) * count;
                    if (i >= maxItems()) {
                        rest += value;
                        restNpc += atNpc;
                        continue;
                    }
                    String name = ItemRepo.coloredName(e.getKey());
                    if (name == null || name.isEmpty()) name = pretty(e.getKey());
                    String market = demo ? (value >= 1_000_000 ? "AH" : "BZ") : market(e.getKey());
                    String tag = market.equals("AH") ? "§dAH" : market.equals("BZ") ? "§6BZ" : "";
                    String amountColor = session.recent(e.getKey()) ? "§a§l" : "§7";
                    if (compact) out.add(Line.table(amountColor + brief(count) + " " + name.replace("Enchanted ", "Ench "), tag, priceCell(value, atNpc, "§6", true), priceCell(atNpc, value, "§e", true)).key("Items"));
                    else out.add(Line.table(amountColor + num(count) + " " + name, tag, priceCell(value, atNpc, "§6", false), priceCell(atNpc, value, "§e", false)).icon(icon(e.getKey())).key("Items"));
                }
                if (list.size() > maxItems()) out.add(Line.table("§7+" + (list.size() - maxItems()) + " more kinds", "", priceCell(rest, restNpc, "§6", compact), priceCell(restNpc, rest, "§e", compact)).key("Items"));
                if (!compact) out.add(Line.rule());
                out.add(Line.table("§fTotal", "", priceCell(total, npcTotal, "§6", compact), priceCell(npcTotal, total, "§e", compact)).key("Total"));
            }
            return new Content(heading(total, counts), out);
        }

        @Override
        protected Content live() {
            Trackers.Scope s = scope();
            return build(session.data.items(s), session.counts(s), s == Trackers.Scope.SESSION ? session.perHour() : 0, s == Trackers.Scope.SESSION ? session.activeMs() : 0);
        }

        @Override
        protected Content sample() {
            Map<String, double[]> items = sampleItems();
            double total = 0;
            for (double[] v : items.values()) total += v[1];
            return build(items, sampleCounts(), total * 1.9, 1_893_000);
        }

        private List<Row> rowsOf(Map<String, double[]> items, double perHour) {
            double total = 0;
            for (double[] v : items.values()) total += v[1];
            List<Row> out = new ArrayList<>();
            out.add(new Row("Total", Prices.coins(total), Modern.GOLD));
            if (perHour > 0) out.add(new Row("Per hour", Prices.coins(perHour), Modern.GOLD));
            items.entrySet().stream().filter(e -> !e.getKey().equals(COINS) && !e.getKey().equals(COST)).sorted((a, b) -> Double.compare(b.getValue()[1], a.getValue()[1])).limit(4)
                    .forEach(e -> out.add(new Row(pretty(e.getKey()), num(e.getValue()[0]), 0)));
            return out;
        }

        @Override
        protected List<Row> classicRows() {
            return rowsOf(session.data.items(scope()), session.perHour());
        }

        @Override
        protected List<Row> classicDemo() {
            return rowsOf(sampleItems(), 4_200_000);
        }
    }

    public static String pretty(String id) {
        if (id.startsWith("vanilla:")) return id.substring(8);
        StringBuilder b = new StringBuilder();
        for (String w : id.replace(";", " ").split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(w.charAt(0)).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    static RichCard.Line count(String label, String value) {
        return RichCard.Line.row(label, value);
    }

    static Map<String, double[]> samples(Object... v) {
        Map<String, double[]> m = new LinkedHashMap<>();
        for (int i = 0; i + 2 < v.length; i += 3) m.put((String) v[i], new double[]{((Number) v[i + 1]).doubleValue(), ((Number) v[i + 2]).doubleValue()});
        return m;
    }

    private static boolean garden() {
        return "GARDEN".equals(GameState.island) || SbcMod.DEV;
    }

    private static boolean farming() {
        return System.currentTimeMillis() - GardenCropSpeed.lastBrokenTime < 5000;
    }

    private static boolean foraging() {
        return "GALATEA".equals(GameState.island) || "THE_PARK".equals(GameState.island) || "TORRHUS_CANYON".equals(GameState.island) || SbcMod.DEV;
    }

    private static boolean fishing() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.fishing != null || dev.zmuzy.sbc.areas.FishForageHunt.fishingRodHeld();
    }

    private static final Set<String> MINING_ISLANDS = Set.of("GOLD_MINES", "DEEP_CAVERNS", "DWARVEN_MINES", "CRYSTAL_HOLLOWS", "MINESHAFT");

    private static boolean mining() {
        return MINING_ISLANDS.contains(GameState.island) || SbcMod.DEV;
    }

    // from skyhanni ExcavatorProfitTracker.isEnabled
    private static boolean excavating() {
        return "DWARVEN_MINES".equals(GameState.island) && "Fossil Research Center".equals(GameState.area) || SbcMod.DEV;
    }

    private static boolean winter() {
        return "WINTER".equals(GameState.island) || SbcMod.DEV;
    }

    private static boolean atTable() {
        return System.currentTimeMillis() - lastClaimAt < 120_000 || dev.zmuzy.sbc.item.menus.Experiments.nearTable(5) || SbcMod.DEV;
    }

    private static long lastClaimAt;

    private static final Card MINING_CARD = new Card("mining_profit", "Mining profit", "Mining", 38, MINING) {
        @Override
        boolean relevant() {
            return mining() && !excavating();
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples("ENCHANTED_MITHRIL", 180, 900_000, "ENCHANTED_TITANIUM", 12, 480_000, "FLAWLESS_JADE_GEM", 3, 4_200_000, "TREASURITE", 8, 96_000);
        }
    };

    private static final Card EXCAVATOR_CARD = new Card("excavator_profit", "Excavator profit", "Mining", 38, EXCAVATOR) {
        @Override
        boolean relevant() {
            return excavating();
        }

        @Override
        List<Line> own(Map<String, Double> counts) {
            List<Line> out = new ArrayList<>();
            double done = counts.getOrDefault("Excavations", 0d);
            if (done > 0) out.add(count("Excavations", "§f" + num(done)).brief("Digs"));
            double glacite = counts.getOrDefault("Glacite Powder", 0d);
            if (glacite > 0) out.add(count("Glacite Powder", "§b" + num(glacite)).brief("Glacite"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples(COINS, -1_800_000, -1_800_000, "BANDAGED_MITHRIL_NECKLACE", 1, 4_100_000, "SUSPICIOUS_VIAL", 2, 240_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Excavations", 24d, "Glacite Powder", 31_200d);
        }
    };

    private static final Card EXPERIMENTS_CARD = new Card("experiments_profit", "Experiments profit", "Enchanting", 38, EXPERIMENTS) {
        @Override
        boolean relevant() {
            return atTable();
        }

        @Override
        List<Line> own(Map<String, Double> counts) {
            List<Line> out = new ArrayList<>();
            double claims = counts.getOrDefault("Claims", 0d), xp = counts.getOrDefault("Enchanting XP", 0d);
            double done = counts.getOrDefault("Experiments", 0d), bits = counts.getOrDefault("Bits", 0d), ms = counts.getOrDefault("Time ms", 0d);
            if (done > 0) out.add(count("Experiments done", "§a" + num(done)).brief("Done"));
            if (claims > 0) out.add(count("Rewards claimed", "§f" + num(claims)).brief("Claimed"));
            if (xp > 0) out.add(count("Enchanting XP", "§3" + Prices.coins(xp)).brief("Ench XP"));
            if (bits > 0) out.add(count("Bits spent", "§b" + num(bits)).brief("Bits"));
            if (ms > 0) out.add(count("Time spent", "§b" + time((long) ms)).brief("Time"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples(COST, 1, -320_000, "ENCHANTED_BOOK", 3, 5_400_000, "GRAND_EXP_BOTTLE", 6, 60_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Claims", 3d, "Enchanting XP", 2_450_000d, "Experiments", 3d, "Bits", 450d, "Time ms", 840_000d);
        }
    };

    // --- skyhanni ExperimentsProfitTracker + ExperimentsDryStreakDisplay ---

    private static final Trackers.Named SUKAtracker = Trackers.numbers("experiments_dry_streak", "Experiments dry streak");
    private static final String DRY_ATTEMPTS = "Attempts", DRY_XP = "XP";

    // attempts + enchanting xp since your last ultra rare
    public static double[] dryStreak() {
        Map<String, double[]> all = SUKAtracker.items(Trackers.Scope.ALL);
        return new double[]{all.containsKey(DRY_ATTEMPTS) ? all.get(DRY_ATTEMPTS)[0] : 0, all.containsKey(DRY_XP) ? all.get(DRY_XP)[0] : 0};
    }

    public static void dryAdd(long attempts, long xp) {
        if (attempts != 0) SUKAtracker.addEntry(DRY_ATTEMPTS, attempts, 0);
        if (xp != 0) SUKAtracker.addEntry(DRY_XP, xp, 0);
    }

    public static void dryReset() {
        SUKAtracker.reset(Trackers.Scope.ALL);
    }

    public static void experimentDone(boolean superpairs) {
        if (superpairs) EXPERIMENTS.count("Experiments", 1);
    }

    // from skyhanni calculateBottlePrice: a used or bought bottle counts at the better of market and npc
    public static void experimentCost(String id, int amount) {
        EXPERIMENTS.cost(Math.max(dev.zmuzy.sbc.core.Gain.ofId(id), npc(id)) * amount);
        lastClaimAt = System.currentTimeMillis();
    }

    public static void experimentTime(long ms) {
        EXPERIMENTS.count("Time ms", ms);
    }

    public static boolean experimentOption(String key, boolean def) {
        return experimentLike("experiments_profit", key, def);
    }

    private static boolean experimentLike(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static final Card WINTER_CARD = new Card("winter_profit", "Jerry's Workshop profit", "Events", 38, WINTER) {
        @Override
        boolean relevant() {
            return winter() && (!experimentLike("winter_profit", "only_cave", false) || dev.zmuzy.sbc.events.winter.Winter.inGlacialCave());
        }

        @Override
        List<Line> own(Map<String, Double> counts) {
            List<Line> out = new ArrayList<>();
            double found = counts.getOrDefault("Frozen Treasures", 0d), compact = counts.getOrDefault("Compact", 0d);
            if (found > 0) out.add(count("Frozen Treasures", "§b" + num(found)).brief("Treasures"));
            if (compact > 0) out.add(count("Compact", "§f" + num(compact)));
            long ice = dev.zmuzy.sbc.events.winter.FrozenTreasure.ice(counts);
            if (ice > 0) {
                out.add(count("Total ice", "§3" + num(ice)));
                out.add(count("Ice/hr", "§3" + num(demo ? 6_400 : dev.zmuzy.sbc.events.winter.FrozenTreasure.icePerHour(ice))));
            }
            boolean drops = dev.zmuzy.sbc.events.winter.FrozenTreasure.showAsDrops();
            for (dev.zmuzy.sbc.events.winter.FrozenTreasure.Type t : dev.zmuzy.sbc.events.winter.FrozenTreasure.Type.values()) {
                double n = counts.getOrDefault(dev.zmuzy.sbc.events.winter.FrozenTreasure.COUNT + t.name(), 0d);
                if (n > 0) out.add(count(t.shown, "§b" + num(drops ? n * t.defaultAmount : n)).key("Treasures found"));
            }
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples("ENCHANTED_ICE", 42, 210_000, "WHITE_GIFT", 12, 96_000, "GLACIAL_FRAGMENT", 20, 800_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Frozen Treasures", 75d, "Compact", 18d, "Treasure: PACKED_ICE", 41d, "Treasure: ENCHANTED_ICE", 22d, "Treasure: WHITE_GIFT", 12d);
        }
    };

    private static final Card FARM_CARD = new Card("garden_profit", "Farming profit", "Garden", 33, FARMING) {
        @Override
        boolean relevant() {
            return garden();
        }

        @Override
        List<Line> own(Map<String, Double> counts) {
            List<Line> out = new ArrayList<>();
            double fevers = counts.getOrDefault("Crop Fevers", 0d);
            if (fevers > 0) out.add(count("Crop Fevers", "§f" + num(fevers) + (feverUntil > System.currentTimeMillis() ? "  §aACTIVE" : "")).brief("Fevers"));
            double drops = counts.getOrDefault("Fever drops", 0d);
            if (drops > 0) out.add(count("Fever drops", "§f" + num(drops)).brief("Drops"));
            int rareLeft = 0;
            var rareOption = feature() == null ? null : feature().option("rare.max_lines");
            rareLeft = rareOption == null ? 5 : rareOption.integer();
            int rareShown = 0;
            for (var e : counts.entrySet()) {
                if (!e.getKey().startsWith("Rare: ") || rareLeft > 0 && rareShown >= rareLeft) continue;
                out.add(count(e.getKey().substring(6), "§d" + num(e.getValue())).key("Rare crops"));
                rareShown++;
            }
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples("ENCHANTED_HAY_BALE", 512, 660_000, "WHEAT", 24_816, 148_000, "SEEDS", 8_204, 12_000, "CROPIE", 3, 96_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Crop Fevers", 2d, "Fever drops", 14d, "Rare: Cropie", 3d);
        }
    };

    // from skyhanni PestProfitTracker.shouldShowDisplay: not while farming, and a while after a kill or with a tool in hand
    private static boolean pestShowing() {
        if (SbcMod.DEV) return true;
        if (farming() && gardenOption("pest.hide_farming", true)) return false;
        var f = Features.get("pest_profit");
        var seconds = f == null ? null : f.option("pest.seconds");
        if (System.currentTimeMillis() - lastPestAt < (seconds == null ? 30 : seconds.integer()) * 1000L) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String held = dev.zmuzy.sbc.core.Gain.sbId(mc.player.getMainHandItem());
        return held.contains("VACUUM") || held.contains("SPRAYONATOR") || held.contains("LASSO");
    }

    private static long lastPestAt;

    // from skyhanni onPestKill: a kill gives bits while theres bits to claim, at your multiplier. what a bit is
    // worth you set yourself
    private static void pestBits() {
        Feature f = Features.get("pest_profit");
        if (f == null || !gardenOption("pest.include_bits", false) || dev.zmuzy.sbc.core.Bits.available() <= 0) return;
        long bits = (long) (dev.zmuzy.sbc.core.Bits.PEST_KILL_BITS * dev.zmuzy.sbc.core.Bits.multiplier());
        PESTS.item("SKYBLOCK_BIT", bits, bits * (double) f.option("pest.coins_per_bit").integer());
    }

    private static boolean gardenOption(String key, boolean def) {
        for (String feature : List.of("pest_profit", "garden_profit")) {
            Feature f = Features.get(feature);
            Option o = f == null ? null : f.option(key);
            if (o != null) return o.bool();
        }
        return def;
    }

    // skyhanni PestProfitTracker hide messages + RareCropTracker hide chat
    static boolean hideGardenLine(String formatted, String clean) {
        if (!"GARDEN".equals(GameState.island)) return false;
        if (gardenOption("pest.hide_chat", false) && Patterns.get("SH.garden.pests-api.chat.pest-death").matcher(formatted).matches()) return true;
        return gardenOption("rare.hide_chat", false) && RARE_CROP.matcher(clean).matches();
    }

    private static final Card PEST_CARD = new Card("pest_profit", "Pest profit", "Garden", 34, PESTS) {
        @Override
        boolean relevant() {
            return garden() && pestShowing();
        }

        @Override
        List<Line> own(Map<String, Double> counts) {
            List<Line> out = new ArrayList<>();
            double kills = 0;
            for (var e : counts.entrySet()) if (e.getKey().startsWith("Pest: ")) kills += e.getValue();
            if (kills <= 0) return out;
            out.add(count("Pests killed", "§f" + num(kills)));
            double total = demo ? 1_810_000 : PESTS.total(Trackers.Scope.SESSION);
            if (total > 0) out.add(count("Per pest", "§6" + Prices.coins(total / kills)).brief("/pest"));
            counts.entrySet().stream().filter(e -> e.getKey().startsWith("Pest: ")).sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .forEach(e -> out.add(count(e.getKey().substring(6), "§f" + num(e.getValue())).key("Pests")));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples("ENCHANTED_POTATO", 128, 22_000, "ENCHANTED_SUGAR", 256, 88_000, "COMPOST", 12, 300_000, "PESTERMINATOR", 1, 1_400_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Pest: Locust", 8d, "Pest: Mosquito", 5d);
        }
    };

    private static final Card SLAYER_CARD = new Card("slayer_profit", "Slayer profit", "Slayer", 41, SLAYER) {
        @Override
        boolean relevant() {
            return Slayers.active() || System.currentTimeMillis() - lastSlayerAt < 120_000;
        }

        @Override
        List<Line> own(Map<String, Double> counts) {
            List<Line> out = new ArrayList<>();
            double bosses = counts.getOrDefault("Bosses", 0d), cost = counts.getOrDefault("Quest cost", 0d);
            if (bosses > 0) {
                out.add(count("Bosses slain", "§f" + num(bosses)).brief("Bosses"));
                double total = demo ? 1_388_000 : SLAYER.total(Trackers.Scope.SESSION);
                out.add(count("Per boss", "§6" + Prices.coins(total / bosses)).brief("/boss"));
            }
            if (cost > 0) out.add(count("Quest costs", "§c-" + Prices.coins(cost)).brief("Cost"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples(COINS, 84_210, 84_210, "REVENANT_FLESH", 412, 24_000, "FOUL_FLESH", 38, 180_000, "UNDEAD_CATALYST", 2, 1_100_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Bosses", 14d, "Quest cost", 700_000d);
        }
    };

    private static long lastFishAt;

    private static final Card FISHING_CARD = new Card("fishing_profit", "Fishing profit", "Fishing", 39, FISHING) {
        @Override
        boolean relevant() {
            // from skyhanni FishingProfitTracker show when pickup: a few sec after a catch whatever you hold
            return fishing() || System.currentTimeMillis() - lastFishAt < 10_000 && experimentLike("fishing_profit", "fishing.after_pickup", true);
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples(COINS, 240_000, 240_000, "LILY_PAD", 88, 44_000, "ENCHANTED_RAW_FISH", 32, 96_000, "GREAT_WHITE_SHARK_TOOTH", 3, 120_000);
        }
    };

    private static long axeHeldAt;

    // from skyhanni ForagingTracker only holding axe + disappearing delay. ItemCategory reads the last lore line
    private static boolean axeInHand() {
        if (!experimentLike("foraging_tracker", "foraging.only_axe", true) || SbcMod.DEV) return true;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player != null) {
            List<String> lore = Watcher.lore(mc.player.getMainHandItem());
            if (!lore.isEmpty() && GameState.strip(lore.get(lore.size() - 1)).endsWith(" AXE")) axeHeldAt = now;
        }
        var f = Features.get("foraging_tracker");
        var delay = f == null ? null : f.option("foraging.delay");
        return now - axeHeldAt < (delay == null ? 15 : delay.integer()) * 1000L;
    }

    private static final Card FORAGING_CARD = new Card("foraging_tracker", "Foraging tracker", "Foraging", 37, FORAGING) {
        @Override
        boolean relevant() {
            return foraging() && axeInHand();
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double trees = c.getOrDefault("Trees", 0d);
            if (trees > 0) {
                out.add(count("Trees helped", "§f" + num(trees)).brief("Trees"));
                out.add(count("Whole trees", "§f" + String.format(Locale.ROOT, "%,.1f", c.getOrDefault("Whole trees", 0d))).brief("Whole"));
                double total = demo ? 849_000 : FORAGING.total(Trackers.Scope.SESSION);
                out.add(count("Per gift", "§6" + Prices.coins(total / trees)).brief("/gift"));
            }
            if (c.containsKey("Foraging Experience")) out.add(count("Foraging XP", "§3" + num(c.get("Foraging Experience"))).brief("XP"));
            if (c.containsKey("HOTF Experience")) out.add(count("HOTF XP", "§a" + num(c.get("HOTF Experience"))));
            if (c.containsKey("Forest Whispers")) out.add(count("Forest Whispers", "§b" + num(c.get("Forest Whispers"))).brief("Whispers"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples("FIG_LOG", 6_204, 186_000, "ENCHANTED_FIG_LOG", 41, 492_000, "FOREST_ESSENCE", 182, 91_000, "STRETCHING_STICKS", 4, 80_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Trees", 31d, "Whole trees", 12.4d, "Foraging Experience", 412_000d, "HOTF Experience", 3_100d, "Forest Whispers", 1_240d);
        }
    };

    private static final List<Card> SPECIFIC = List.of(FARM_CARD, PEST_CARD, SLAYER_CARD, FISHING_CARD, FORAGING_CARD, MINING_CARD, EXCAVATOR_CARD, EXPERIMENTS_CARD, WINTER_CARD);

    // shows while you earned something in the last 10 min, folds away when idle
    static final long EARNING_MS = 10 * 60_000;

    // everything else you earn, steps aside when a card for what youre doing is on screen
    private static final Card TOTAL_CARD = new Card("profit_session", "Profit", "Trackers", 32, TOTAL) {
        @Override
        boolean relevant() {
            // a few coins from a kill isnt worth a card, wait till you hit the settings amount
            var f = feature();
            var min = f == null ? null : f.option("min");
            double need = min == null ? 100_000 : min.integer();
            if (!TOTAL.earnedWithin(EARNING_MS) || TOTAL.earnedSince(EARNING_MS) < need) return false;
            for (Card c : SPECIFIC) if (c.visible() && c.active()) return false;
            return !ProfitCards.anyShowing();
        }

        @Override
        Map<String, double[]> sampleItems() {
            return samples(COINS, 412_000, 412_000, "ENCHANTED_DIAMOND", 42, 420_000, "RECOMBOBULATOR_3000", 1, 6_100_000, "ENCHANTED_COAL", 640, 96_000);
        }
    };

    // --- what feeds them ---

    private static long feverUntil, questStart, lastSlayerAt;
    private static final Map<String, Set<String>> SLAYER_ITEMS = new HashMap<>();
    private static Set<String> fishingItems;

    private static Set<String> slayerItems(String boss) {
        return SLAYER_ITEMS.computeIfAbsent(boss, b -> {
            Set<String> out = new HashSet<>();
            try {
                JsonObject all = JsonParser.parseString(Repo.bundled("SlayerProfitTrackerItems.json")).getAsJsonObject().getAsJsonObject("slayers");
                if (all.has(b)) for (JsonElement e : all.getAsJsonArray(b)) out.add(base(e.getAsString()));
            } catch (Exception e) {
                SbcMod.LOG.warn("Could not read the slayer items", e);
            }
            return out;
        });
    }

    private static Set<String> fishingItems() {
        if (fishingItems != null) return fishingItems;
        Set<String> out = new HashSet<>();
        try {
            JsonObject all = JsonParser.parseString(Repo.bundled("FishingProfitItems.json")).getAsJsonObject().getAsJsonObject("categories");
            for (var c : all.entrySet()) for (JsonElement e : c.getValue().getAsJsonArray()) out.add(base(e.getAsString()));
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the fishing items", e);
        }
        return fishingItems = out;
    }

    // "SMITE;6" = a smite book: the item says ENCHANTED_BOOK and the level is in its data
    private static String base(String id) {
        int i = id.indexOf(';');
        return i < 0 ? id : id.substring(0, i);
    }

    // stuff with many kinds where the id alone doesnt say which
    private static final Set<String> ANY_KIND = Set.of("ENCHANTED_BOOK", "RUNE", "PET");

    static void onGain(String id, int amount) {
        if (!GameState.inSkyBlock && !SbcMod.DEV) return;
        warn(id, amount);
        ProfitCards.onGain(id, amount);
        TOTAL.item(id, amount);
        if (garden() && farming()) FARMING.item(id, amount);
        if (Slayers.active()) {
            String boss = Slayers.quest().boss();
            if (ANY_KIND.contains(id) || slayerItems(boss).contains(id)) {
                SLAYER.item(id, amount);
                lastSlayerAt = System.currentTimeMillis();
            }
        }
        if (fishing() && (ANY_KIND.contains(id) || fishingItems().contains(id))) {
            FISHING.item(id, amount);
            lastFishAt = System.currentTimeMillis();
        }
        if (mining() && !excavating()) MINING.item(id, amount);
        if (winter()) WINTER.item(id, amount);
        if (foraging() && (id.equals("STRETCHING_STICKS") || Patterns.get("SH.foraging.treegift.log-internal-name").matcher(id).matches())) FORAGING.item(id, amount);
    }

    static void onCoins(double diff, Trackers.Cause cause) {
        ProfitCards.onCoins(diff, cause);
        if (cause == Trackers.Cause.MOB_KILL) {
            TOTAL.coins(diff);
            if (Slayers.active() && diff < 100_000) {
                SLAYER.coins(diff);
                lastSlayerAt = System.currentTimeMillis();
            }
        } else if (diff < 0 && System.currentTimeMillis() - questStart < 1500) {
            SLAYER.coins(diff);
            SLAYER.count("Quest cost", -diff);
        }
    }

    private static boolean gift, bonusGift;
    private static final Map<String, Long> GIFT_LOOT = new LinkedHashMap<>();

    // skyhanni item name -> id, for the gift lines
    public static String idForName(String name) {
        return idFor(GameState.strip(name));
    }

    static String idFor(String name) {
        String plain = GameState.strip(name).trim(), id = ItemRepo.idForName(plain);
        if (id != null) return id;
        id = Prices.idFromName(plain);
        return id == null || id.isEmpty() ? null : id;
    }

    static long amount(String s) {
        return Long.parseLong(s.replace(",", ""));
    }

    // from skyhanni ForagingTracker tree gift message: a long rule opens + closes it, items come in the hover of
    // the rewards line
    private static void treeGift(String message, List<String> hovers) {
        if (Patterns.get("SH.foraging.treegift.open-close-reward").matcher(message).matches()) {
            gift = !gift;
            if (gift) bonusGift = false;
            else {
                GIFT_LOOT.forEach(FORAGING::item);
                GIFT_LOOT.clear();
            }
            return;
        }
        if (!gift) return;
        if (Patterns.get("SH.foraging.treegift.bonus-gift.separator").matcher(message).matches()) {
            bonusGift = true;
            return;
        }
        Matcher m = Patterns.get("SH.foraging.treegift.contribution-percentage").matcher(message);
        if (m.matches()) {
            FORAGING.count("Trees", 1);
            FORAGING.count("Whole trees", Double.parseDouble(m.group("percentage")) / 100);
        }
        if (Patterns.get("SH.foraging.treegift.rewards-gained").matcher(message).matches()) {
            for (String hover : hovers) for (String line : hover.split("\n")) {
                Matcher h = Patterns.get("SH.foraging.treegift.hover-reward").matcher(line);
                if (!h.matches()) continue;
                String item = h.group("item");
                long amount;
                try {
                    amount = h.group("percentage") != null ? 1 : amount(h.group("amount"));
                } catch (Exception e) {
                    continue;
                }
                if (item.equals("HOTF Experience") || item.equals("Foraging Experience") || item.equals("Forest Whispers")) FORAGING.count(item, amount);
                else {
                    String id = idFor(item);
                    if (id != null) GIFT_LOOT.merge(id, amount, Long::sum);
                }
            }
        }
        if (!bonusGift) return;
        Matcher r = Patterns.get("SH.foraging.treegift.bonus-gift.reward").matcher(message);
        if (!r.matches()) return;
        String id = idFor(r.group("item"));
        if (id != null) GIFT_LOOT.merge(id, 1L, Long::sum);
    }

    private static boolean inLoot, claimed;
    private static final java.util.regex.Pattern COUNT_AFTER = java.util.regex.Pattern.compile("^(.+?) x?([\\d,]+)$"), COUNT_BEFORE = java.util.regex.Pattern.compile("^x?([\\d,]+)x? (.+)$");

    // from skyhanni ItemUtils.readItemAmount on a colorless name: "Name x3" or "3x Name"
    static void addReward(Session to, String reward) {
        String t = GameState.strip(reward).replaceFirst("^\\+", "").trim();
        long n = 1;
        Matcher after = COUNT_AFTER.matcher(t), before = COUNT_BEFORE.matcher(t);
        if (after.matches()) {
            t = after.group(1).trim();
            n = amount(after.group(2));
        } else if (before.matches()) {
            n = amount(before.group(1));
            t = before.group(2).trim();
        }
        String id = idFor(t);
        if (id != null) to.item(id, n);
    }

    // from skyhanni FossilExcavatorApi: what a finished dig found, between its start line and closing rule
    private static void excavation(String message) {
        if (Patterns.get("SH.mining.fossil.excavator.chat.empty").matcher(message).matches()) {
            dug();
            return;
        }
        if (Patterns.get("SH.mining.fossil.excavator.chat.start").matcher(message).matches()) {
            inLoot = true;
            return;
        }
        if (!inLoot) return;
        if (Patterns.get("SH.mining.fossil.excavator.chat.end").matcher(message).matches()) {
            inLoot = false;
            dug();
            return;
        }
        Matcher item = Patterns.get("SH.mining.fossil.excavator.chat.item").matcher(message);
        if (!item.matches()) return;
        String clean = GameState.strip(item.group("item")).trim();
        if (clean.endsWith("Glacite Powder")) {
            Matcher powder = java.util.regex.Pattern.compile("^\\+?([\\d,]+) ").matcher(clean);
            if (powder.find()) EXCAVATOR.count("Glacite Powder", amount(powder.group(1)));
        } else addReward(EXCAVATOR, item.group("item"));
    }

    // one dig + the suspicious scrap it took
    private static void dug() {
        EXCAVATOR.count("Excavations", 1);
        EXCAVATOR.coins(-Prices.sell("SUSPICIOUS_SCRAP"));
    }

    // what the table gave you, on the lines after "You claimed the Superpairs rewards!"
    private static void experiments(String message, String clean) {
        Matcher renew = Patterns.get("SH.enchanting.experiments.renew").matcher(clean);
        if (renew.matches()) {
            // from skyhanni ExperimentsProfitTracker.onChat: first bonus charge of the day = 150 bits, 2nd 300, 3rd 500
            EXPERIMENTS.count("Bits", switch (renew.group("current")) {
                case "1" -> 150;
                case "2" -> 300;
                default -> 500;
            });
            return;
        }
        if (Patterns.get("SH.enchanting.experiments.claim").matcher(clean).matches()) {
            claimed = true;
            lastClaimAt = System.currentTimeMillis();
            EXPERIMENTS.count("Claims", 1);
            return;
        }
        if (!claimed) return;
        Matcher drop = Patterns.get("SH.enchanting.experiments.drop").matcher(message);
        if (!drop.matches()) {
            claimed = false;
            return;
        }
        Matcher xp = Patterns.get("SH.enchanting.experiments.exp").matcher(drop.group("reward"));
        Matcher thousands = Patterns.get("SH.enchanting.experiments.enchantingxp").matcher(message.substring(message.indexOf('+') + 1).replace("§r", ""));
        if (xp.matches()) EXPERIMENTS.count("Enchanting XP", Trackers.parseShort(xp.group("amount")));
        else if (thousands.matches()) EXPERIMENTS.count("Enchanting XP", Trackers.parseShort(thousands.group("xp") + "k"));
        else addReward(EXPERIMENTS, drop.group("reward"));
    }

    static void onMessage(String message, String clean, List<String> hovers) {
        if ("DWARVEN_MINES".equals(GameState.island) || SbcMod.DEV) excavation(message);
        experiments(message, clean);
        if (winter()) {
            if (clean.startsWith("FROZEN TREASURE! You found ")) {
                WINTER.count("Frozen Treasures", 1);
                dev.zmuzy.sbc.events.winter.FrozenTreasure.Type treasure = dev.zmuzy.sbc.events.winter.FrozenTreasure.found(clean);
                if (treasure != null) WINTER.count(dev.zmuzy.sbc.events.winter.FrozenTreasure.COUNT + treasure.name(), 1);
            } else if (Patterns.get("SH.event.jerry.frozentreasure.compact").matcher(clean).matches()) WINTER.count("Compact", 1);
        }
        if (garden()) {
            Matcher m = Patterns.get("SH.garden.pests-api.chat.pest-death").matcher(message);
            if (m.matches()) {
                String id = idFor(m.group("item")), pest = m.group("pest");
                if (id != null) PESTS.item(id, amount(m.group("amount")));
                // field mice + lunar moths write one line per crop for a single kill, the overclocker is extra
                boolean kill = switch (pest) {
                    case "Field Mouse" -> "DUNG".equals(id);
                    case "Lunar Moth" -> "ENCHANTED_SUNFLOWER".equals(id);
                    default -> !"OVERCLOCKER_3000".equals(id);
                };
                if (kill) {
                    PESTS.count("Pest: " + pest, 1);
                    lastPestAt = System.currentTimeMillis();
                    pestBits();
                }
            }
            m = Patterns.get("SH.garden.pests.tracker.raredrop").matcher(message);
            if (m.matches()) {
                String id = idFor(m.group("item"));
                if (id != null) PESTS.item(id, m.group("amount") == null ? 1 : amount(m.group("amount")));
            }
            if (Patterns.get("SH.garden.cropfever.start").matcher(clean).find()) {
                FARMING.count("Crop Fevers", 1);
                feverUntil = System.currentTimeMillis() + 60_000;
            }
            if (Patterns.get("SH.garden.cropfever.end").matcher(clean).find()) feverUntil = 0;
            if (feverUntil > System.currentTimeMillis() && Patterns.get("SH.garden.cropfever.drop").matcher(clean).find()) FARMING.count("Fever drops", 1);
            m = RARE_CROP.matcher(clean);
            if (m.matches()) FARMING.count("Rare: " + m.group(1).trim(), 1);
        }
        String t = clean.trim();
        if (t.equals("SLAYER QUEST STARTED!")) questStart = System.currentTimeMillis();
        else if (t.equals("SLAYER QUEST COMPLETE!")) {
            SLAYER.count("Bosses", 1);
            lastSlayerAt = System.currentTimeMillis();
        }
        Matcher coins = Patterns.get("SH.fishing.tracker.chat.coins.colorless").matcher(clean);
        if (coins.matches()) {
            FISHING.coins(amount(coins.group("coins")));
            lastFishAt = System.currentTimeMillis();
        }
        if (foraging()) treeGift(message, hovers);
        ProfitCards.onMessage(message, clean);
    }

    // name out of skyhannis "RARE CROP! Aggourdian" patterns, they only differ in the name
    private static final java.util.regex.Pattern RARE_CROP = java.util.regex.Pattern.compile("^\\s*(?:VERY )?RARE CROP! ([A-Za-z' ]+?)(?: [(+].*)?$");

    private static void hovers(Component c, List<String> out) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText st) out.add(LegacyText.of(st.value()));
        for (Component sib : c.getSiblings()) hovers(sib, out);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        List<String> hovers = new ArrayList<>();
        hovers(line.component(), hovers);
        onMessage(LegacyText.of(line.component()), GameState.strip(line.plain()), hovers);
        return ChatBus.PASS;
    }

    // restarts every tracker for this session, day/week/all time keep theirs
    public static void reset() {
        for (Session s : SESSIONS) s.reset();
    }

    // one tracker from zero, every total: its card id or a word from its title. false = none
    public static boolean resetAll(String which) {
        String w = which.toLowerCase(Locale.ROOT);
        boolean found = false;
        for (Session s : SESSIONS) {
            if (!s.data.id.equals("profit_" + w) && !s.data.id.equals(w) && !s.data.title.toLowerCase(Locale.ROOT).contains(w)) continue;
            s.reset();
            s.reset(Trackers.Scope.DAY);
            s.reset(Trackers.Scope.WEEK);
            s.reset(Trackers.Scope.ALL);
            found = true;
        }
        return found;
    }

    static Feature feature(String id, String module, String title, String description, String credit) {
        return Features.add(new Feature(id, module, title, description, credit)
                .option(Option.choice(id + ".scope", "Totals of", "", "This session", "This session", "Today", "This week", "All time")));
    }

    public static void register() {
        // off by default, each activity has its own tracker. this one is for everything else
        feature("profit_session", "Tracking", "Profit", "What you earn doing anything without a tracker of its own: coins from kills and items, at NPC and Bazaar or Auction House prices, in total and per hour. Shows while you earn at least the amount below in 10 minutes, and folds away when you are idle.", "SkyHanni: item trackers")
                .defaultOff()
                .option(Option.integer("profit_session.min", "Show from (coins in 10 min)", "Less than this and the card stays away.", 100_000, 0, 100_000_000));
        feature("garden_profit", "Garden", "Farming profit", "Crops and drops while you farm, at NPC and Bazaar prices, per hour; Crop Fevers and rare crops.", "SkyHanni: Crop Fever and Rare Crop trackers")
                .option(Option.integer("rare.max_lines", "Rare crops shown", "0 is all of them.", 5, 0, 50))
                .option(Option.bool("rare.hide_chat", "Hide the rare crop lines", "", false));
        feature("pest_profit", "Garden", "Pest profit", "What pests drop at NPC and market prices, how many you killed of each and what one is worth.", "SkyHanni: Pest Profit Tracker")
                .option(Option.bool("pest.hide_farming", "Hide while farming", "", true))
                .option(Option.integer("pest.seconds", "Show after a kill (seconds)", "Longer with a vacuum, a Sprayonator or a lasso in hand.", 30, 1, 600))
                .option(Option.bool("pest.hide_chat", "Hide the pest drop lines", "", false))
                .option(Option.bool("pest.include_bits", "Count the bits pests give", "While you have bits to claim, with your fame rank and museum.", false))
                .option(Option.integer("pest.coins_per_bit", "Coins for a bit", "", 700, 0, 2000));
        feature("slayer_profit", "Slayer", "Slayer profit", "Drops and coins of your slayer quests at NPC and market prices, what the quests cost, and what a boss is worth.", "SkyHanni: Slayer Profit Tracker");
        feature("fishing_profit", "Fishing", "Fishing profit", "What fishing brings in at NPC and market prices, per hour.", "SkyHanni: Fishing Profit Tracker")
                .option(Option.bool("fishing.after_pickup", "Show after a catch", "For a few seconds, whatever you hold.", true));
        feature("foraging_tracker", "Foraging", "Foraging tracker", "Tree gifts: trees you helped cut, logs and drops at NPC and market prices, Foraging and HOTF experience, Forest Whispers.", "SkyHanni: Foraging Tracker")
                .option(Option.bool("foraging.only_axe", "Only holding an axe", "", true))
                .option(Option.integer("foraging.delay", "Stay after the axe (seconds)", "", 15, 0, 120));
        feature("mining_profit", "Mining", "Mining profit", "What mining brings in at NPC and market prices, per hour, on every mining island.", "SkyHanni: Powder Tracker's profit");
        feature("excavator_profit", "Mining", "Excavator profit", "What the Fossil Excavator finds at the Fossil Research Center, its Glacite Powder, and what the Suspicious Scrap cost.", "SkyHanni: Excavator Profit Tracker");
        feature("experiments_profit", "Skills", "Experiments profit", "What the Experimentation Table gives you at market prices, its Enchanting XP, what the bottles and charges cost, and how many rewards you claimed. Shows near the table.", "SkyHanni: Experiments Profit Tracker")
                .option(Option.bool("experiments.used_bottles", "Count used bottles", "XP bottles thrown near the table come off the total.", true))
                .option(Option.bool("experiments.bottle_warnings", "Say so once a session", "A line the first time a thrown bottle is counted.", true))
                .option(Option.bool("experiments.time_spent", "Time spent", "How long you spend in experiments.", true));
        feature("winter_profit", "Events", "Jerry's Workshop profit", "What Jerry's Workshop brings in at market prices, with Frozen Treasures, Compact finds and the ice they make.", "SkyHanni: Frozen Treasure Tracker")
                .left(dev.zmuzy.sbc.events.winter.FrozenTreasure::worldChanged)
                .option(Option.bool("winter_profit.only_cave", "Only in the Glacial Cave", "", false))
                .option(Option.bool("winter_profit.as_drops", "Treasures as drops", "Each treasure times its base drop: 3 Ice Bait shows 48.", false))
                .option(Option.bool("winter_profit.hide_messages", "Hide the chat messages", "", false));
        for (Card c : SPECIFIC) HudLayout.add(c);
        HudLayout.add(TOTAL_CARD);
        ProfitCards.register();
        Trackers.onFresh(Profit::onGain);
        Trackers.onCoins(Profit::onCoins);
        ChatBus.register(ChatBus.OBSERVE + 21, "profit", null, Profit::onChat);
    }
}
