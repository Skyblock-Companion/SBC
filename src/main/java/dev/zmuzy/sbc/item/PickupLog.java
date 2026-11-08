// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item;

import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Prices;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// item pickup log, kept like skyhanni ItemPickupLog: one entry per item for added stuff and one for removed
// each entry grows while it keeps changing and expires a fixed time after its last change
// fed by track.Trackers (inventory diff, sack changes, coins)
public final class PickupLog {
    public record Entry(String id, String name, long count, long at, boolean removed) {}

    // secs an entry stays after its last change (skyhanni expireAfter = 10)
    public static volatile int expireSeconds = 10;
    private static final Map<String, Entry> ADDED = new LinkedHashMap<>(), REMOVED = new LinkedHashMap<>();

    private PickupLog() {}

    private static final dev.zmuzy.sbc.core.Patterns.P SENT_TO_BOX = dev.zmuzy.sbc.core.Patterns.p("SH.inventory.attributeshards.sent.to.hunting.box.colorless");

    // skyhanni ItemPickupLog.onShardGain, for stuff going to the hunting box. id comes from the shard name bc we
    // dont have the repos shard list
    public static void onChat(String clean) {
        if (!opt("pickups.shards", false) || !dev.zmuzy.sbc.feature.Features.on("pickups")) return;
        var m = SENT_TO_BOX.matcher(clean);
        if (m.matches()) add("SHARD_" + m.group("shardName").toUpperCase(java.util.Locale.ROOT).replace(' ', '_'), m.group("amount") == null ? 1 : Integer.parseInt(m.group("amount")));
    }

    // negative count = removed
    public static synchronized void add(String id, long count) {
        if (count == 0) return;
        boolean removed = count < 0;
        Map<String, Entry> target = removed ? REMOVED : ADDED, other = removed ? ADDED : REMOVED;
        long now = System.currentTimeMillis();
        Entry o = other.get(id);
        if (o != null) other.put(id, new Entry(o.id(), o.name(), o.count(), now, o.removed())); // skyhanni: refresh the opposite entry
        Entry e = target.get(id);
        long amount = Math.abs(count);
        if (e != null) target.put(id, new Entry(id, e.name(), e.count() + amount, now, removed));
        else target.put(id, new Entry(id, nameOf(id), amount, now, removed));
    }

    private static String nameOf(String id) {
        if (id.equals("COINS")) return "§6Coins";
        if (id.startsWith("SHARD_")) return dev.zmuzy.sbc.core.GameState.pretty(id.substring(6)) + " Shard";
        if (id.startsWith("vanilla:")) return id.substring(8);
        String n = ItemRepo.coloredName(id);
        return n == null || n.isEmpty() ? id : n;
    }

    private static boolean opt(String key, boolean def) {
        return dev.zmuzy.sbc.item.market.Market.b("pickups", key, def);
    }

    public static synchronized List<Entry> recent() {
        long now = System.currentTimeMillis(), life = expireSeconds * 1000L;
        ADDED.values().removeIf(e -> now - e.at() > life);
        REMOVED.values().removeIf(e -> now - e.at() > life);
        return opt("pickups.compactLines", true) ? compact(ADDED, REMOVED) : normal(ADDED, REMOVED);
    }

    // skyhanni handleNormalLines: an added line with its removed line under it, whats left after them
    private static List<Entry> normal(Map<String, Entry> added, Map<String, Entry> removed) {
        List<Entry> out = new ArrayList<>();
        Map<String, Entry> rest = new LinkedHashMap<>(removed);
        for (Entry e : added.values()) {
            out.add(e);
            Entry r = rest.remove(e.id());
            if (r != null) out.add(r);
        }
        out.addAll(rest.values());
        return out;
    }

    // skyhanni handleCompactLines: what came and went of one item is one amount. if they cancel out neither is left
    static List<Entry> compact(Map<String, Entry> added, Map<String, Entry> removed) {
        List<Entry> out = new ArrayList<>();
        Map<String, Entry> rest = new LinkedHashMap<>(removed);
        List<String> gone = new ArrayList<>();
        for (Entry e : added.values()) {
            Entry r = rest.remove(e.id());
            if (r == null) {
                out.add(e);
                continue;
            }
            long total = e.count() - r.count();
            if (total > 0) out.add(new Entry(e.id(), e.name(), total, e.at(), false));
            else if (total < 0) out.add(new Entry(e.id(), e.name(), -total, e.at(), true));
            else gone.add(e.id());
        }
        for (String id : gone) {
            added.remove(id);
            removed.remove(id);
        }
        out.addAll(rest.values());
        return out;
    }

    public static synchronized void clear() {
        ADDED.clear();
        REMOVED.clear();
    }

    // coin value of an entry, negative for what you lost, 0 if unknown
    public static double value(Entry e) {
        if (e.id().startsWith("vanilla:")) return 0;
        // skyhanni computeTotalCoinValue: purse coins count as themselves
        double v = (e.id().equals("COINS") ? 1 : each(e.id())) * e.count();
        return e.removed() ? -v : v;
    }

    // value of one by the chosen source, stuff the source has no price for keeps the general value
    private static double each(String id) {
        var f = dev.zmuzy.sbc.feature.Features.get("pickups");
        var o = f == null ? null : f.option("pickups.priceSource");
        double p = o == null ? 0 : dev.zmuzy.sbc.item.market.PriceSource.of(o.choice(), id);
        return p > 0 ? p : dev.zmuzy.sbc.core.Gain.ofId(id);
    }

    // skyhanni computeTotalCoinValue switch + threshold, the number for the cards band
    public static double shownTotal(double sum) {
        if (!opt("pickups.coinValue", true)) return 0;
        var f = dev.zmuzy.sbc.feature.Features.get("pickups");
        var o = f == null ? null : f.option("pickups.coinThreshold");
        int threshold = o == null ? 0 : o.integer();
        return sum >= threshold || threshold == 0 ? sum : 0;
    }

    // one lines text: amount + name in the chosen order
    public static String lineText(Entry e) {
        String sign = e.removed() ? "§c-" : "§a+";
        String amount = opt("pickups.shorten", false) ? dev.zmuzy.sbc.item.enchants.EnchantParser.shortFormat(e.count()) : String.format(java.util.Locale.ROOT, "%,d", e.count());
        String name = dev.zmuzy.sbc.core.GameState.strip(e.name());
        var f = dev.zmuzy.sbc.feature.Features.get("pickups");
        var o = f == null ? null : f.option("pickups.layout");
        return o != null && o.choice().equals("NAME_AMOUNT") ? "§f" + name + " " + sign + amount : sign + amount + " §f" + name;
    }

    public static String worth(Entry e) {
        if (e.id().equals("COINS") || e.id().startsWith("vanilla:")) return "";
        double v = each(e.id()) * e.count();
        return v > 0 ? Prices.coins(v) : "";
    }
}
