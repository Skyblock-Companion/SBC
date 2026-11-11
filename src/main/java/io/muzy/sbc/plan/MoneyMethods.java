// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.plan;

import io.muzy.sbc.core.GameState;

import io.muzy.sbc.core.Mayor;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Profile;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.minion.Minions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

// ways to make coins + what each tier needs from your profile
// coins/h is only ever measured (tracker first, else the island, 10 active min). no fake numbers
public final class MoneyMethods {
    private MoneyMethods() {}

    public static final class Loadout {
        public final Set<String> ids = new HashSet<>();
        public final Set<String> names = new HashSet<>();
        public final Set<String> pets = new HashSet<>();
        public Profile.Data d;
        public boolean known;

        public boolean has(String... anyOf) {
            for (String a : anyOf) {
                for (String id : ids) if (id.equals(a) || id.startsWith(a + "_") || id.endsWith("_" + a)) return true;
                String word = a.replace('_', ' ').toLowerCase(Locale.ROOT);
                for (String n : names) if (n.toLowerCase(Locale.ROOT).contains(word)) return true;
            }
            return false;
        }

        public boolean pet(String type, String minTier) {
            List<String> order = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
            for (String p : pets) {
                int c = p.indexOf(':');
                if (p.substring(0, c).equals(type) && order.indexOf(p.substring(c + 1)) >= order.indexOf(minTier)) return true;
            }
            return false;
        }

        public int skill(String s) {
            if (d == null || !d.skills.containsKey(s)) return Planner.currentLevel(s);
            return d.skills.get(s).level();
        }

        public int slayer(String s) {
            return d == null || !d.slayers.containsKey(s) ? -1 : d.slayers.get(s).level();
        }

        public int cata() {
            return d == null || d.catacombs == null ? -1 : d.catacombs.level();
        }

        public int hotm() {
            return d == null || d.hotm == null ? -1 : d.hotm.level();
        }
    }

    public record Tier(String label, Predicate<Loadout> req, String needs) {}

    public record Method(String id, String name, String category, String icon, String island, String node, String how, boolean mayorOnly,
                         List<Tier> tiers) {}

    // coinsPerHour is 0 until measured. missing = what you need to even start it
    public record Rated(Method m, Tier tier, Tier next, double coinsPerHour, boolean measured, String basis, boolean available, String missing) {}

    // which profit trackers count for a method, best one first
    private static final Map<String, List<String>> TRACKERS = Map.ofEntries(
            Map.entry("dungeons", List.of("dungeon")), Map.entry("slayer", List.of("slayer")), Map.entry("farming", List.of("farming")),
            Map.entry("gemstones", List.of("mining")), Map.entry("glacite", List.of("corpse", "mining")), Map.entry("commissions_loop", List.of("mining")),
            Map.entry("fishing", List.of("fishing")), Map.entry("trophy", List.of("fishing")), Map.entry("foraging", List.of("foraging")),
            Map.entry("hunting", List.of("hunting")), Map.entry("diana", List.of("mythological", "diana")), Map.entry("visitors_loop", List.of("pests")));

    public static final List<Method> METHODS = new ArrayList<>();

    private static void add(String id, String name, String cat, String icon, String island, String node, String how, Tier... tiers) {
        METHODS.add(new Method(id, name, cat, icon, island, node, how, id.equals("diana"), List.of(tiers)));
    }

    private static Tier t(String label, Predicate<Loadout> req, String needs) {
        return new Tier(label, req, needs);
    }

    private static final Predicate<Loadout> ANY = l -> true;

    static {
        add("dungeons", "Dungeons", "Dungeons", "skeleton_skull", "DUNGEON_HUB", "Mort", "Run the highest floor you clear fast; sell chest loot and essence.",
                t("Floor 5", l -> l.cata() >= 15, "Catacombs 15"),
                t("Floor 7", l -> l.cata() >= 28 && l.has("HYPERION", "VALKYRIE", "ASTRAEA", "SCYLLA", "TERMINATOR"), "Cata 28 + Hyperion/Terminator"),
                t("Master 5-6", l -> l.cata() >= 34 && l.has("HYPERION", "TERMINATOR") && l.has("POWER_WITHER", "WISE_WITHER", "TANK_WITHER", "SPEED_WITHER"), "Cata 34 + Wither armor + Hyperion/Terminator"),
                t("Master 7", l -> l.cata() >= 42 && l.has("TERMINATOR") && l.has("HYPERION", "VALKYRIE"), "Cata 42 + Terminator + Hyperion"));
        add("kuudra", "Kuudra", "Crimson Isle", "magma_cream", "CRIMSON_ISLE", "Elle", "Run the highest tier your team can do; open paid chests with keys.",
                t("Basic", l -> l.has("CRIMSON", "AURORA", "TERROR", "FERVOR", "HOLLOW"), "Any Crimson Isle armor"),
                t("Hot / Burning", l -> l.has("CRIMSON", "AURORA", "TERROR") && l.has("HYPERION", "TERMINATOR", "VALKYRIE", "AURORA_STAFF", "HOLLOW_WAND", "MIDAS_STAFF"), "Crimson/Aurora/Terror + a strong weapon"),
                t("Fiery", l -> l.has("TERROR", "AURORA", "CRIMSON") && l.has("TERMINATOR", "HYPERION"), "Terror/Aurora/Crimson + Terminator or Hyperion"),
                t("Infernal", l -> l.has("TERROR") && l.has("TERMINATOR") && (l.d == null || !l.d.knows(io.muzy.sbc.core.game.GameProfile.MAGICAL_POWER) || l.d.magicalPower >= 1200), "Terror + Terminator + 1200 Magical Power"));
        add("slayer", "Slayers", "Slayer", "rotten_flesh", "HUB", "Maddox the Slayer", "Your best-paying boss tier; drops, Slayer XP and Combat XP.",
                t("Revenant T4", l -> l.slayer("Revenant") >= 3 || l.skill("Combat") >= 20, "Revenant 3"),
                t("Tarantula T4", l -> l.slayer("Tarantula") >= 3, "Tarantula 3"),
                t("Revenant T5", l -> l.slayer("Revenant") >= 6 && l.has("REAPER", "AXE_OF_THE_SHREDDED", "HYPERION"), "Revenant 6 + Reaper/AOTS"),
                t("Voidgloom T4", l -> l.slayer("Voidgloom") >= 4 && l.has("HYPERION", "JUJU_SHORTBOW", "TERMINATOR", "ATOMSPLIT_KATANA", "VOIDEDGE_KATANA"), "Voidgloom 4 + Juju/Terminator/Katana"),
                t("Inferno T4", l -> l.slayer("Inferno") >= 4 && l.has("TERMINATOR", "HYPERION", "DEATHRIPPER_DAGGER", "FIREDUST_DAGGER", "HEARTFIRE_DAGGER", "BURSTFIRE_DAGGER"), "Inferno 4 + daggers or Terminator"));
        add("farming", "Farming", "Farming", "golden_hoe", "GARDEN", "", "Farm your highest-fortune crop; sell enchanted crops to the Bazaar.",
                t("Basic hoe", ANY, ""),
                t("Theoretical / Math hoe", l -> l.has("THEORETICAL_HOE", "COCO_CHOPPER", "CACTUS_KNIFE", "FUNGI_CUTTER"), "A Math hoe or crop tool"),
                t("Dicer + farming armor", l -> l.has("MELON_DICER", "PUMPKIN_DICER", "THEORETICAL_HOE") && l.has("MELON_", "CROPIE", "SQUASH", "FERMENTO", "RANCHERS"), "Dicer/Math hoe + Melon/Cropie/Squash/Fermento"),
                t("Max fortune (Fermento)", l -> l.has("FERMENTO") && (l.pet("MOOSHROOM_COW", "LEGENDARY") || l.pet("ELEPHANT", "LEGENDARY")), "Fermento armor + Mooshroom/Elephant"));
        add("gemstones", "Gemstone mining", "Mining", "amethyst_shard", "CRYSTAL_HOLLOWS", "", "Mine the most valuable gemstone your drill handles.",
                t("Gemstone drill", l -> l.hotm() >= 3 || l.has("GEMSTONE_DRILL", "TITANIUM_DRILL", "MITHRIL_DRILL"), "HOTM 3 + a drill"),
                t("Topaz / Jasper drill", l -> l.has("TOPAZ_DRILL", "JASPER_DRILL", "GEMSTONE_DRILL_4", "TITANIUM_DRILL_4"), "Topaz or Jasper drill"),
                t("Divan's drill", l -> l.has("DIVAN_DRILL") && l.hotm() >= 7, "Divan's Drill + HOTM 7"));
        add("glacite", "Glacite Mineshafts", "Mining", "packed_ice", "DWARVEN_MINES", "", "Mine Glacite Tunnels and loot corpses in Mineshafts.",
                t("Tunnels", l -> l.hotm() >= 5 || l.has("DRILL"), "HOTM 5"),
                t("Mineshaft corpses", l -> l.hotm() >= 7 && l.has("GLACITE", "MINERAL", "LAPIS_ARMOR"), "HOTM 7 + Glacite/Mineral armor"));
        add("commissions_loop", "Commissions", "Mining", "iron_pickaxe", "DWARVEN_MINES", "King", "Chain commissions for powder, Mining XP and Mithril.",
                t("Commissions", ANY, ""),
                t("Commissions + Mithril", l -> l.hotm() >= 5, "HOTM 5"));
        add("fishing", "Fishing", "Fishing", "fishing_rod", "CRIMSON_ISLE", "", "Lava fishing for Thunder / Lord Jawbus, or water sea creatures.",
                t("Water sea creatures", ANY, ""),
                t("Bayou / Water Hydra", l -> l.skill("Fishing") >= 24 && l.has("ROD_OF_THE_SEA", "GIANT_FISHING_ROD", "SHREDDER"), "Fishing 24 + Rod of the Sea"),
                t("Lava (Thunder/Jawbus)", l -> l.skill("Fishing") >= 35 && l.has("HELLFIRE_ROD", "INFERNO_ROD", "MAGMA_ROD") && l.has("THUNDER", "MAGMA_LORD", "TAURUS"), "Fishing 35 + Hellfire rod + Thunder/Magma Lord"));
        add("trophy", "Trophy fishing", "Fishing", "tropical_fish", "CRIMSON_ISLE", "Odger", "Catch missing trophy fish tiers and sell the extras to Odger.",
                t("Trophy fishing", l -> l.skill("Fishing") >= 15, "Fishing 15"));
        add("foraging", "Foraging", "Foraging", "diamond_axe", "GALATEA", "", "Chop the best tree your axe handles on Galatea and sell logs/enchanted wood.",
                t("Basic axe", ANY, ""),
                t("Treecapitator", l -> l.has("TREECAPITATOR_AXE", "JUNGLE_AXE"), "Treecapitator"),
                t("Galatea axe + Monkey", l -> l.skill("Foraging") >= 25 && l.pet("MONKEY", "EPIC"), "Foraging 25 + Epic Monkey"));
        add("hunting", "Hunting shards", "Hunting", "lead", "GALATEA", "", "Hunt the shards with the best Bazaar price (see the shard list below).",
                t("Starting out", ANY, ""),
                t("Hunting 10+", l -> l.skill("Hunting") >= 10, "Hunting 10"),
                t("Hunting 25+", l -> l.skill("Hunting") >= 25, "Hunting 25"));
        add("diana", "Mythological Ritual", "Diana", "golden_shovel", "HUB", "", "Only when Diana is mayor: burrows, Inquisitors, Chimera books.",
                t("Common Griffin", ANY, ""),
                t("Legendary Griffin", l -> l.pet("GRIFFIN", "LEGENDARY") && l.has("HYPERION", "TERMINATOR", "DAEDALUS_AXE", "ASTRAEA", "VALKYRIE"), "Legendary Griffin + strong weapon"));
        add("visitors_loop", "Garden visitors + pests", "Farming", "emerald", "GARDEN", "", "Serve visitors and kill pests between them for copper and rare drops.",
                t("Visitors", ANY, ""));
        add("rift", "Rift", "Rift", "ender_eye", "THE_RIFT", "", "Motes into Rift items; Enigma Souls and Timecharms for SkyBlock XP.",
                t("Rift", ANY, ""));
    }

    private static Loadout cached;
    private static Profile.Data cachedFor;

    public static Loadout loadout() {
        Profile.Data d = Profile.current;
        if (cached != null && d == cachedFor) return cached;
        Loadout l = new Loadout();
        l.d = d;
        if (d != null) {
            l.known = d.knows(io.muzy.sbc.core.game.GameProfile.INVENTORY);
            for (List<Profile.Item> inv : d.inventories.values())
                for (Profile.Item it : inv) if (it != null && !it.sbId().isEmpty()) l.ids.add(it.sbId());
            for (Profile.Pet p : d.pets) l.pets.add(p.type() + ":" + p.tier());
        }
        cached = l;
        cachedFor = d;
        return l;
    }

    public static Rated rate(Method m) {
        Loadout l = loadout();
        Tier best = m.tiers().get(0), next = null;
        for (int i = 0; i < m.tiers().size(); i++) {
            Tier t = m.tiers().get(i);
            if (t.req().test(l)) {
                best = t;
                next = i + 1 < m.tiers().size() ? m.tiers().get(i + 1) : null;
            }
        }
        boolean available = m.tiers().get(0).req().test(l);
        String missing = available ? "" : m.tiers().get(0).needs();
        for (String tracker : TRACKERS.getOrDefault(m.id(), List.of())) {
            io.muzy.sbc.track.Profit.Measured got = io.muzy.sbc.track.Profit.measured(tracker);
            if (got != null && got.perHour() > 0)
                return new Rated(m, best, next, got.perHour(), true, "measured: " + hours(got.hours()) + " with its tracker", available, missing);
        }
        Store.Rate r = m.island().isEmpty() ? null : Store.I.rates.get(m.island());
        if (r != null && r.activeMs > 10 * 60_000 && r.coinsPerHour() > 0)
            return new Rated(m, best, next, r.coinsPerHour(), true, "measured: " + hours(r.activeMs / 3_600_000d) + " of all you earned on " + GameState.pretty(m.island()), available, missing);
        return new Rated(m, best, next, 0, false, "not measured yet: play it 10 minutes with its tracker on", available, missing);
    }

    private static String hours(double h) {
        return h < 1 ? Math.round(h * 60) + " min" : String.format(java.util.Locale.ROOT, "%.1f h", h);
    }

    // measured first, then the rest. diana only when shes mayor
    public static List<Rated> ranked() {
        List<Rated> out = new ArrayList<>();
        for (Method m : METHODS) {
            if (m.mayorOnly() && !Mayor.dianaActive()) continue;
            Rated r = rate(m);
            if (r.available()) out.add(r);
        }
        out.sort(Comparator.comparing((Rated r) -> !r.measured()).thenComparing(Comparator.comparingDouble(Rated::coinsPerHour).reversed()));
        return out;
    }

    public static List<Rated> locked() {
        List<Rated> out = new ArrayList<>();
        for (Method m : METHODS) {
            if (m.mayorOnly() && !Mayor.dianaActive()) continue;
            Rated r = rate(m);
            if (!r.available()) out.add(r);
        }
        return out;
    }

    public static double rateFor(String activityId, double fallback) {
        for (Method m : METHODS) {
            if (!m.id().equals(activityId)) continue;
            Rated r = rate(m);
            return r.measured() ? r.coinsPerHour() : fallback;
        }
        return fallback;
    }

    public static double minionsPerHour() {
        return Store.minions().values().stream().mapToDouble(Minions::coinsPerDay).sum() / 24;
    }

    // shard price * how much actually sells per hour, so dead shards dont top the list
    public static List<Object[]> bestShards(int limit) {
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, Prices.Product> e : Prices.all().entrySet()) {
            if (!e.getKey().startsWith("SHARD_")) continue;
            Prices.Product p = e.getValue();
            double perHourSold = p.sellWeek() / (7 * 24);
            if (p.sell() <= 0 || perHourSold < 2) continue;
            out.add(new Object[]{e.getKey(), p.sell(), perHourSold, p.sell() * Math.min(perHourSold, 30)});
        }
        out.sort((a, b) -> Double.compare((double) b[3], (double) a[3]));
        return out.size() > limit ? out.subList(0, limit) : out;
    }
}
