// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.minion;

import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Store;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// minion smarts. measures each minions real output by comparing its storage between visits (no guessed speed
// tables), values it at live bazaar prices, suggests the upgrade that pays best
public final class Minions {
    private Minions() {}

    public static final List<String> FUELS = List.of("Hyper Catalyst", "Catalyst", "Tasty Cheese", "Foul Flesh", "Hamster Wheel",
            "Plasma Bucket", "Magma Bucket", "Enchanted Lava Bucket", "Everburning Flame", "Inferno Minion Fuel", "Solar Panel",
            "Enchanted Charcoal", "Enchanted Coal", "Block of Coal", "Enchanted Bread", "Coal");
    public static final List<String> UPGRADES = List.of("Super Compactor 3000", "Compactor", "Diamond Spreading", "Minion Expander",
            "Flycatcher", "Auto Smelter", "Budget Hopper", "Enchanted Hopper", "Corrupt Soil", "Sleepy Hollow", "Lesser Soulflow Engine",
            "Soulflow Engine", "Krampus Helmet", "Enchanted Egg", "Potato Spreading", "Berberis Fuel Injector", "Mithril Infusion");

    public record Advice(String text, double extraPerDay, double cost, boolean certain) {
        public double daysToPayBack() { return extraPerDay <= 0 ? Double.MAX_VALUE : cost / extraPerDay; }
    }

    public static boolean isFuel(String name) {
        for (String f : FUELS) if (name.contains(f)) return true;
        return false;
    }

    public static boolean isUpgrade(String name) {
        for (String u : UPGRADES) if (name.contains(u)) return true;
        return false;
    }

    // updates a minions record from a fresh look at its storage, items/hour since the last look
    public static void observe(String key, String type, int tier, String island, List<String> upgrades, String fuel,
                               Map<String, Integer> storage, int storageSlots) {
        long now = System.currentTimeMillis();
        Store.MinionRecord r = Store.I.minions.computeIfAbsent(key, k -> new Store.MinionRecord());
        r.key = key;
        r.type = type;
        r.tier = tier;
        r.island = island;
        r.upgrades = upgrades;
        r.fuel = fuel;
        r.storageSlots = storageSlots;
        if (r.lastSeen > 0) {
            double hours = (now - r.lastSeen) / 3_600_000.0;
            if (hours > 0.15) {
                for (Map.Entry<String, Integer> e : storage.entrySet()) {
                    int before = r.storage.getOrDefault(e.getKey(), 0);
                    int gained = e.getValue() - before;
                    if (gained > 0) {
                        double rate = gained / hours;
                        r.itemsPerHour.merge(e.getKey(), rate, (old, nw) -> old * 0.4 + nw * 0.6);
                    }
                }
            }
        }
        r.storage = storage;
        r.lastSeen = now;
        Store.markDirty();
    }

    // opening a minion while not looking at it makes a 2nd record keyed by type+tier. fold those into the real
    // minions record (same type + tier, found by its head) and drop the menu one
    public static void cleanup() {
        var all = Store.I.minions;
        java.util.List<String> drop = new java.util.ArrayList<>();
        for (var e : all.entrySet()) {
            Store.MinionRecord r = e.getValue();
            r.storage.keySet().removeIf(n -> n.contains("Required Items") || n.contains("Collect") || n.contains("Pickup"));
            if (hasCoords(e.getKey())) continue;
            for (var o : all.entrySet())
                if (o.getValue() != r && hasCoords(o.getKey()) && o.getValue().tier == r.tier && o.getValue().type != null
                        && o.getValue().type.equalsIgnoreCase(r.type)) {
                    drop.add(e.getKey());
                    break;
                }
        }
        drop.forEach(all::remove);
        if (!drop.isEmpty()) Store.markDirty();
    }

    public static boolean hasCoords(String key) {
        return key != null && key.contains("@") && key.substring(key.indexOf('@')).contains(",");
    }

    // recorded minion of this type + tier closest to a spot (the one whose menu you just opened)
    public static String nearestKey(String island, String type, int tier, net.minecraft.world.phys.Vec3 at) {
        String best = null;
        double bd = 8 * 8;
        for (var e : Store.I.minions.entrySet()) {
            Store.MinionRecord r = e.getValue();
            if (!hasCoords(e.getKey()) || !island.equals(r.island) || r.tier != tier || r.type == null || !r.type.equalsIgnoreCase(type)) continue;
            String[] c = e.getKey().substring(e.getKey().indexOf('@') + 1).split(",\\s*");
            try {
                double d = at.distanceToSqr(Double.parseDouble(c[0]) + 0.5, Double.parseDouble(c[1]), Double.parseDouble(c[2]) + 0.5);
                if (d < bd) {
                    bd = d;
                    best = e.getKey();
                }
            } catch (Exception ignored) {
            }
        }
        return best;
    }

    public static boolean measured(Store.MinionRecord r) {
        return !r.itemsPerHour.isEmpty();
    }

    public static double coinsPerDay(Store.MinionRecord r) {
        if (!measured(r)) {
            String t = r.catalogType != null ? r.catalogType : r.type == null ? "" : r.type.toUpperCase().replace(' ', '_');
            return MinionCatalog.coinsPerDay(t, r.tier, r.fuel, r.upgrades);
        }
        double v = 0;
        for (Map.Entry<String, Double> e : r.itemsPerHour.entrySet()) v += e.getValue() * 24 * MinionCatalog.unitPrice(Prices.idFromName(e.getKey()));
        return v;
    }

    // hours till storage is full at the measured rate (rough, 64 per stack)
    public static double hoursUntilFull(Store.MinionRecord r) {
        double rate = r.itemsPerHour.values().stream().mapToDouble(Double::doubleValue).sum();
        if (rate <= 0 || r.storageSlots <= 0) return -1;
        int held = r.storage.values().stream().mapToInt(Integer::intValue).sum();
        boolean compacted = r.upgrades.stream().anyMatch(u -> u.contains("Compactor"));
        double capacity = r.storageSlots * 64.0 * (compacted ? 160 : 1);
        return Math.max(0, (capacity - held) / rate);
    }

    // whats worth changing here, best payback first, through MinionMath.advise
    public static List<Advice> advise(Store.MinionRecord r) {
        String t = r.catalogType != null ? r.catalogType : r.type == null ? "" : r.type.toUpperCase(Locale.ROOT).replace(' ', '_');
        List<String> ids = new ArrayList<>();
        for (String u : r.upgrades) ids.add(MinionMath.idOf(u));
        List<Advice> out = new ArrayList<>();
        for (MinionMath.Advice a : MinionMath.advise(t, r.tier, MinionMath.idOf(r.fuel), ids, false))
            out.add(new Advice(a.text(), a.extraPerDay(), a.cost(), a.certain()));
        if (out.isEmpty() && coinsPerDay(r) <= 0) out.add(new Advice("No price for what this minion makes, so nothing to compare.", 0, 0, false));
        return out;
    }

    public static String pretty(String type) {
        return type == null ? "" : type.substring(0, 1).toUpperCase(Locale.ROOT) + type.substring(1);
    }
}
