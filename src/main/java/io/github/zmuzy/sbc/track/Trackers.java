// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.track;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.item.PickupLog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

// named trackers with session, day + week totals (day + week survive restarts and roll at the real reset).
// every entry = item or label with a count + its coin worth
public final class Trackers {
    public enum Scope { SESSION, DAY, WEEK, ALL }

    // the skyhanni PurseApi causes that matter here
    public enum Cause { MOB_KILL, OTHER }

    public static final class Named {
        public final String id, title;
        private final Map<String, double[]> session = new LinkedHashMap<>();

        Named(String id, String title) {
            this.id = id;
            this.title = title;
        }

        private Store.TrackerData data() {
            Store.TrackerData d = Store.I.trackers.computeIfAbsent(id, k -> new Store.TrackerData());
            long now = System.currentTimeMillis();
            String dk = SbTime.periodKey(SbTime.Period.DAILY, now), wk = SbTime.periodKey(SbTime.Period.WEEKLY, now);
            if (!dk.equals(d.dayKey)) {
                d.dayKey = dk;
                d.day.clear();
            }
            if (!wk.equals(d.weekKey)) {
                d.weekKey = wk;
                d.week.clear();
            }
            return d;
        }

        private static void put(Map<String, double[]> m, String key, double count, double coins) {
            double[] v = m.computeIfAbsent(key, k -> new double[2]);
            v[0] += count;
            v[1] += coins;
        }

        // counts an item by sb id, coins come from price data. returns what it was worth (0 = no price)
        public double add(String item, double count) {
            var f = Features.get("tracker_settings");
            var source = f == null ? null : f.option("tracker.priceSource");
            double each = source == null || source.choice().equals("BEST_KNOWN") ? Gain.ofId(item) : io.github.zmuzy.sbc.item.market.PriceSource.of(source.choice(), item);
            double coins = each > 0 ? each * count : 0;
            addEntry(item, count, coins);
            return coins;
        }

        public void addEntry(String label, double count, double coins) {
            Store.TrackerData d = data();
            put(session, label, count, coins);
            put(d.day, label, count, coins);
            put(d.week, label, count, coins);
            put(d.all, label, count, coins);
            Store.markDirty();
        }

        // highest value so far for a number that doesnt add up (like the longest combo), the count of its entry
        public void peak(String label, double value) {
            Store.TrackerData d = data();
            for (Map<String, double[]> m : List.of(session, d.day, d.week, d.all)) {
                double[] v = m.computeIfAbsent(label, k -> new double[2]);
                if (value > v[0]) v[0] = value;
            }
            Store.markDirty();
        }

        public Map<String, double[]> items(Scope s) {
            return switch (s) {
                case SESSION -> session;
                case DAY -> data().day;
                case WEEK -> data().week;
                case ALL -> data().all;
            };
        }

        // count + coins summed over everything
        public double[] total(Scope s) {
            double[] t = new double[2];
            for (double[] v : items(s).values()) {
                t[0] += v[0];
                t[1] += v[1];
            }
            return t;
        }

        public void reset(Scope s) {
            items(s).clear();
            Store.markDirty();
        }
    }

    private static final Map<String, Named> ALL = new LinkedHashMap<>();
    private static final List<BiConsumer<String, Integer>> LISTENERS = new ArrayList<>(), FRESH = new ArrayList<>();
    private static final List<BiConsumer<Double, Cause>> COIN_LISTENERS = new ArrayList<>();
    private static long lastMenuAt, lastSackMenuAt, coinPurse = Long.MIN_VALUE;
    private static Map<String, Long> itemList = new HashMap<>();
    private static Object lastLevel;
    private static long worldChangedAt;
    private static long lastPurse = Long.MIN_VALUE;

    private Trackers() {}

    public static Named get(String id, String title) {
        return ALL.computeIfAbsent(id, k -> new Named(id, title));
    }

    // a trackers own numbers: saved like the rest but not an item list any page should show
    static Named numbers(String id, String title) {
        return new Named(id, title);
    }

    public static List<Named> all() {
        return new ArrayList<>(ALL.values());
    }

    // called with (item id, count) for each item you gain with no menu open
    public static void onGain(BiConsumer<String, Integer> l) {
        LISTENERS.add(l);
    }

    // "2.4k", "1M", "1,500" as a number
    static double parseShort(String s) {
        String t = s.replace(",", "").trim().toLowerCase(java.util.Locale.ROOT);
        double mul = t.endsWith("k") ? 1e3 : t.endsWith("m") ? 1e6 : t.endsWith("b") ? 1e9 : 1;
        if (mul > 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void onFresh(BiConsumer<String, Integer> l) {
        FRESH.add(l);
    }

    public static void onCoins(BiConsumer<Double, Cause> l) {
        COIN_LISTENERS.add(l);
    }

    // from skyhanni PurseApi.getCause: coins with no menu open, 2 sec after the last one closed = from what youre killing
    static Cause cause(double diff, boolean screenOpen, long sinceMenuMs) {
        if (diff <= 0 || diff == 1 || diff == 15_000_000 || diff == 100_000_000) return Cause.OTHER;
        return !screenOpen && sinceMenuMs > 2000 ? Cause.MOB_KILL : Cause.OTHER;
    }

    private static boolean opt(String key, boolean def) {
        var f = Features.get("pickups");
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    // key an item gets counted under: sb id, else vanilla item
    static String keyOf(ItemStack st) {
        if (st == null || st.isEmpty()) return "";
        String id = Gain.sbId(st);
        if (!id.isEmpty()) return id;
        return "vanilla:" + GameState.strip(st.getHoverName().getString());
    }

    // placed orbs and flares come back to you, never a drop
    private static final java.util.regex.Pattern DEPLOYABLE = java.util.regex.Pattern.compile("[A-Z_]*(?:POWER_ORB|_FLARE)|UMBERELLA|TOTEM_OF_CORRUPTION");
    // "Your previous Overflux Power Orb was removed!", "Your Overflux Power Orb despawned."
    private static final java.util.regex.Pattern BACK_CHAT = java.util.regex.Pattern.compile("Your (?:previous )?(?<name>.+?) (?:was removed!|despawned\\.)");
    static final long BACK_MS = 10 * 60_000L;
    // unstackable things that just left the inventory: id -> when
    static final Map<String, Long> LEFT = new HashMap<>();
    private static final java.util.Set<String> UNSTACKABLE = new java.util.HashSet<>();
    private static String backName = "";
    private static long backUntil;

    static boolean deployable(String id) {
        return DEPLOYABLE.matcher(id).matches();
    }

    // a gain that is only something of yours coming back
    static boolean comesBack(String id, long now) {
        if (deployable(id)) return true;
        if (!backName.isEmpty() && now < backUntil && GameState.strip(io.github.zmuzy.sbc.core.ItemRepo.coloredName(id) == null ? "" : io.github.zmuzy.sbc.core.ItemRepo.coloredName(id)).contains(backName)) return true;
        Long left = LEFT.get(id);
        if (left != null && now - left < BACK_MS) {
            LEFT.remove(id);
            return true;
        }
        return false;
    }

    static void backChat(String plain, long now) {
        java.util.regex.Matcher m = BACK_CHAT.matcher(plain);
        if (!m.matches()) return;
        backName = m.group("name");
        backUntil = now + 5000;
    }

    private static Map<String, Long> snapshot(Minecraft mc, Map<String, Long> old) {
        Map<String, Long> m = new HashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (i == 8) continue; // the sb menu
            String k = keyOf(inv.getItem(i));
            if (k.isEmpty()) continue;
            m.merge(k, (long) inv.getItem(i).getCount(), Long::sum);
            if (inv.getItem(i).getMaxStackSize() == 1) UNSTACKABLE.add(k);
        }
        ItemStack cursor = mc.player.containerMenu == null ? ItemStack.EMPTY : mc.player.containerMenu.getCarried();
        String ck = keyOf(cursor);
        if (!ck.isEmpty() && old.containsKey(ck)) m.merge(ck, (long) cursor.getCount(), Long::sum); // only if it was ours already
        return m;
    }

    // every client tick
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            itemList = new HashMap<>();
            lastLevel = null;
            return;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldChangedAt = System.currentTimeMillis();
            itemList = new HashMap<>();
            PickupLog.clear();
            lastPurse = Long.MIN_VALUE;
            coinPurse = Long.MIN_VALUE;
        }
        long now = System.currentTimeMillis();
        if (mc.gui.screen() != null) {
            lastMenuAt = now;
            if (mc.gui.screen().getTitle().getString().contains("Sack")) lastSackMenuAt = now;
        }
        if (GameState.purse >= 0) {
            if (coinPurse != Long.MIN_VALUE && GameState.purse != coinPurse) {
                double diff = GameState.purse - coinPurse;
                Cause c = cause(diff, mc.gui.screen() != null, now - lastMenuAt);
                for (var l : COIN_LISTENERS) l.accept(diff, c);
            }
            coinPurse = GameState.purse;
        }
        Map<String, Long> old = itemList;
        // menus move items around so the list isnt read while one is open (skyhanni does the same)
        if (mc.gui.screen() == null) itemList = snapshot(mc, old);
        boolean cooled = System.currentTimeMillis() - worldChangedAt > 2000;
        if (!cooled) return;
        if (!old.isEmpty()) {
            Named loot = get("loot", "Items gained");
            boolean quiet = mc.gui.screen() == null && now - lastMenuAt > 2000;
            RECENT_LOSS.values().removeIf(l -> now - l[1] > COMPACT_MS);
            if (quiet) for (var e : old.entrySet()) {
                long loss = e.getValue() - itemList.getOrDefault(e.getKey(), 0L);
                if (loss > 0 && !e.getKey().startsWith("vanilla:")) {
                    long[] l = RECENT_LOSS.computeIfAbsent(e.getKey(), k -> new long[2]);
                    l[0] += loss;
                    l[1] = now;
                }
            }
            for (var e : itemList.entrySet()) {
                long gain = e.getValue() - old.getOrDefault(e.getKey(), 0L);
                if (gain <= 0) continue;
                if (comesBack(e.getKey(), now)) continue;
                if (!e.getKey().startsWith("vanilla:")) {
                    netCompaction(e.getKey(), gain, now);
                    loot.add(e.getKey(), gain);
                    for (var l : LISTENERS) l.accept(e.getKey(), (int) Math.min(Integer.MAX_VALUE, gain));
                    if (now - lastMenuAt > 500) for (var l : FRESH) l.accept(e.getKey(), (int) Math.min(Integer.MAX_VALUE, gain));
                } else if (Features.on("pickups")) PickupLog.add(e.getKey(), gain);
            }
            for (var e : old.entrySet()) {
                long loss = e.getValue() - itemList.getOrDefault(e.getKey(), 0L);
                if (loss <= 0) continue;
                if (UNSTACKABLE.contains(e.getKey())) LEFT.put(e.getKey(), now);
                if (Features.on("pickups") && !deployable(e.getKey())) PickupLog.add(e.getKey(), -loss);
            }
            LEFT.values().removeIf(t -> now - t > BACK_MS);
        }
        if (opt("coins", true) && Features.on("pickups") && GameState.purse >= 0) {
            if (lastPurse != Long.MIN_VALUE && GameState.purse != lastPurse) PickupLog.add("COINS", GameState.purse - lastPurse);
            lastPurse = GameState.purse;
        }
    }

    private static final java.util.regex.Pattern SACK_WINDOW = java.util.regex.Pattern.compile("\\(Last (\\d+)s\\.\\)");

    // a [Sacks] line covers the last few seconds ("(Last 30s.)"). removals count only when no menu was open in that
    // time: selling sacks, a visitor or a craft in a menu takes items out on purpose. pure, for tests
    static boolean countsRemovals(String plain, long sinceMenuMs) {
        java.util.regex.Matcher m = SACK_WINDOW.matcher(plain);
        long window = m.find() ? Long.parseLong(m.group(1)) * 1000 : 30_000;
        return sinceMenuMs > window + 1000;
    }

    // what left the inventory lately with no menu open, per id: amount and when
    private static final Map<String, long[]> RECENT_LOSS = new java.util.HashMap<>();
    // the [Sacks] message can come up to half a minute after the items went
    private static final long COMPACT_MS = 60_000;

    // a compactor turns the items you picked up into their enchanted form. both would count as gained, so when the
    // made item comes in, the items it was made from that just left you come off again
    static void netCompaction(String made, long amount, long now) {
        for (io.github.zmuzy.sbc.core.ItemInfo.Recipe r : io.github.zmuzy.sbc.core.ItemInfo.recipes(made)) {
            if (r.parts().isEmpty() || r.resultCount() <= 0) continue;
            String base = r.parts().get(0).id();
            if (base.equals(made) || !r.parts().stream().allMatch(p -> p.id().equals(base))) continue;
            long per = r.parts().stream().mapToLong(io.github.zmuzy.sbc.core.ItemInfo.Part::count).sum();
            long[] lost = RECENT_LOSS.get(base);
            if (lost == null || lost[0] <= 0 || now - lost[1] > COMPACT_MS) continue;
            long used = Math.min(lost[0], per * amount / r.resultCount());
            if (used <= 0) continue;
            lost[0] -= used;
            get("loot", "Items gained").add(base, -used);
            for (var l : LISTENERS) l.accept(base, (int) -Math.min(Integer.MAX_VALUE, used));
            for (var l : FRESH) l.accept(base, (int) -Math.min(Integer.MAX_VALUE, used));
            return;
        }
    }

    // the [Sacks] message (skyhanni data/SackApi.kt onChat): the "+5 items" / "-2 items" parts have hovers
    // starting with Added / Removed listing "+5 Wheat (Agronomy Sack)" lines
    public static ChatBus.Result onChat(ChatBus.Line line) {
        PickupLog.onChat(GameState.strip(line.plain()).trim());
        backChat(GameState.strip(line.plain()).trim(), System.currentTimeMillis());
        if (!GameState.strip(line.plain()).trim().startsWith("[Sacks]") || !opt("sack", true) || !Features.on("pickups")) return ChatBus.PASS;
        String text = sackChangeText(line.component());
        if (text.isEmpty()) return ChatBus.PASS;
        long now = System.currentTimeMillis();
        boolean removals = countsRemovals(GameState.strip(line.plain()).trim(), now - lastMenuAt);
        for (var e : parseSackChanges(text).entrySet()) {
            String id = e.getKey();
            long delta = e.getValue();
            PickupLog.add(id, delta);
            // what left the sacks with no menu open: a compactor turning 160 Mushroom into an Enchanted Mushroom, or
            // /gfs taking items out. it comes off again, so a compacted item and the items it was made from count once
            if (!id.startsWith("vanilla:") && delta < 0 && removals) {
                get("loot", "Items gained").add(id, delta);
                for (var l : LISTENERS) l.accept(id, (int) Math.max(Integer.MIN_VALUE, delta));
                if (now - lastSackMenuAt > 10_000) for (var l : FRESH) l.accept(id, (int) Math.max(Integer.MIN_VALUE, delta));
            }
            if (!id.startsWith("vanilla:") && delta > 0) {
                netCompaction(id, delta, System.currentTimeMillis());
                get("loot", "Items gained").add(id, delta);
                for (var l : LISTENERS) l.accept(id, (int) Math.min(Integer.MAX_VALUE, delta));
                if (System.currentTimeMillis() - lastSackMenuAt > 10_000) for (var l : FRESH) l.accept(id, (int) Math.min(Integer.MAX_VALUE, delta));
            }
        }
        return ChatBus.PASS;
    }

    // from skyhanni SackApi.onChat: sackAddText + sackRemoveText (first hover starting with Added, first with Removed)
    public static String sackChangeText(Component root) {
        List<String> hovers = new ArrayList<>();
        collectHovers(root, hovers);
        String added = "", removed = "";
        for (String h : hovers) {
            if (added.isEmpty() && h.startsWith("Added")) added = h;
            else if (removed.isEmpty() && h.startsWith("Removed")) removed = h;
        }
        return added + removed;
    }

    private static void collectHovers(Component c, List<String> out) {
        HoverEvent h = c.getStyle().getHoverEvent();
        if (h instanceof HoverEvent.ShowText st) out.add(GameState.strip(st.value().getString()));
        for (Component sib : c.getSiblings()) collectHovers(sib, out);
    }

    // skyhanni sackChangePattern over the hover: item id -> summed delta. ids: item repo name lookup, else the
    // bazaar id for the name, else "vanilla:<name>" (skyhanni NeuInternalName.fromItemName)
    public static Map<String, Long> parseSackChanges(String text) {
        Map<String, Long> out = new LinkedHashMap<>();
        Matcher m = Patterns.get("SH.data.sacks.change").matcher(text);
        while (m.find()) {
            long delta;
            try {
                delta = Long.parseLong(m.group("delta").replace(",", "").replace("+", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            String item = m.group("item").trim();
            out.merge(sackItemId(item), delta, Long::sum);
        }
        return out;
    }

    // display name -> sb id or null. swappable in tests bc the item repo cant start without fabric
    public static java.util.function.Function<String, String> resolveItemId = item -> {
        String id = ItemRepo.idForName(item);
        if (id != null) return id;
        String guess = Prices.idFromName(item);
        if (!guess.isEmpty() && (Prices.get(guess) != null || ItemRepo.get(guess) != null)) return guess;
        return null;
    };

    // from skyhanni NeuInternalName.fromItemName for a sack items name, "vanilla:<name>" when nothing knows it
    public static String sackItemId(String item) {
        String id = resolveItemId.apply(item);
        return id != null ? id : "vanilla:" + item;
    }
}
