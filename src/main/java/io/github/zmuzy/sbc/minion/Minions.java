// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.minion;

import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Store;

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
        Store.MinionRecord r = Store.minions().computeIfAbsent(key, k -> new Store.MinionRecord());
        r.key = key;
        r.type = type;
        r.catalogType = MinionCatalog.typeId(type);
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
        var all = Store.minions();
        java.util.List<String> drop = new java.util.ArrayList<>();
        for (var e : all.entrySet()) {
            Store.MinionRecord r = e.getValue();
            r.storage.keySet().removeIf(n -> n.contains("Required Items") || n.contains("Collect") || n.contains("Pickup"));
            if (hasCoords(e.getKey())) continue;
            for (var o : all.entrySet())
                if (o.getValue() != r && hasCoords(o.getKey()) && o.getValue().tier == r.tier && sameType(o.getValue(), r.type)) {
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

    // block x, y, z of a "ISLAND@x, y, z" key, null = no coords
    public static double[] coords(String key) {
        if (!hasCoords(key)) return null;
        String[] c = key.substring(key.indexOf('@') + 1).split(",\\s*");
        try {
            return new double[]{Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2])};
        } catch (Exception e) {
            return null;
        }
    }

    // a record of this type, by repo id when it has one ("Nether Wart" and NETHER_WARTS are one type)
    static boolean sameType(Store.MinionRecord r, String type) {
        if (r.type == null && r.catalogType == null || type == null) return false;
        String id = MinionCatalog.typeId(type);
        return id.equals(r.catalogType) || r.type != null && (r.type.equalsIgnoreCase(type) || MinionCatalog.typeId(r.type).equals(id));
    }

    // storage slots a tier has, when the repo doesnt say
    public static int slotsForTier(int tier) {
        return tier <= 2 ? 1 : tier <= 4 ? 3 : tier <= 6 ? 6 : tier <= 8 ? 9 : tier <= 10 ? 12 : 15;
    }

    // key of the block a minion stands on, like the head scan makes it
    static String keyAt(String island, double x, double y, double z) {
        return island + "@" + (int) Math.floor(x) + ", " + (int) Math.floor(y) + ", " + (int) Math.floor(z);
    }

    // the private island spots MinionFeatures remembers (placed, opened) fill what the head scan cant see: an
    // unknown head, or a part of the island you havent walked past. a spot thats gone takes its record along
    public static boolean fillFromSpots(Map<String, io.github.zmuzy.sbc.core.game.Seen.MinionSpot> spots, Map<String, Store.MinionRecord> all) {
        boolean changed = false;
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (var e : spots.entrySet()) {
            MinionCatalog.Named n = MinionCatalog.named(e.getValue().displayName);
            String[] c = e.getKey().split(",");
            if (n == null || c.length != 3) continue;
            String key;
            try {
                key = keyAt("PRIVATE_ISLAND", Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]));
            } catch (NumberFormatException ex) {
                continue;
            }
            keys.add(key);
            Store.MinionRecord r = all.get(key);
            if (r == null) {
                r = new Store.MinionRecord();
                r.fromSpot = true;
                r.key = key;
                r.island = "PRIVATE_ISLAND";
                all.put(key, r);
                changed = true;
            }
            // only records this made follow the spots name, once the head scan saw it the scan keeps it right
            if (!r.fromSpot || r.lastDetected > 0) continue;
            if (r.tier != n.tier() || !n.type().equals(r.catalogType)) {
                r.catalogType = n.type();
                r.type = MinionCatalog.displayType(n.type());
                r.tier = n.tier();
                if (r.storageSlots <= 0) r.storageSlots = slotsForTier(n.tier());
                changed = true;
            }
        }
        changed |= all.entrySet().removeIf(e -> e.getValue().fromSpot && !keys.contains(e.getKey()));
        return changed;
    }

    // "You picked up a minion!" for the one you had open: its record goes, however it was made
    public static void pickedUp(String island, net.minecraft.world.phys.Vec3 at) {
        if (Store.minions().remove(keyAt(island, at.x, at.y, at.z)) != null) Store.markDirty();
    }

    // recorded minion of this type + tier closest to a spot (the one whose menu you just opened)
    public static String nearestKey(String island, String type, int tier, net.minecraft.world.phys.Vec3 at) {
        String best = null;
        double bd = 8 * 8;
        for (var e : Store.minions().entrySet()) {
            Store.MinionRecord r = e.getValue();
            double[] c = coords(e.getKey());
            if (c == null || !island.equals(r.island) || r.tier != tier || !sameType(r, type)) continue;
            double d = at.distanceToSqr(c[0] + 0.5, c[1], c[2] + 0.5);
            if (d < bd) {
                bd = d;
                best = e.getKey();
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
