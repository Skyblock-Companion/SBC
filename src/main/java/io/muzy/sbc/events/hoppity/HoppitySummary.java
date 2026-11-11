// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.events.EventSheet;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// from skyhanni HoppityEventSummary + HoppityLiveDisplay: one record per hoppity hunt, per profile. when the
// hunt ends its summary goes to chat, while it runs a card shows the same numbers
// skyhanni lets you drag stat order, here every stat with a number shows in its order. older hunts: /sbc
// hoppitystats [year|all]
public final class HoppitySummary {
    public static final String ID = "hoppity_summary", LIVE = "hoppity_live";
    private static final String SEPARATOR = "§d§l" + "▬".repeat(64);
    private static final String LINE_HEADER = "    ";

    private static long lastAddedCf = HoppityEnv.FAR_PAST, lastCfReminder = HoppityEnv.FAR_PAST;
    private static long lastCheckedEnd = HoppityEnv.FAR_PAST;
    private static String lastSnapshotServer;
    private HoppitySummary() {}

    private static boolean on(String id) {
        Feature f = Features.get(id);
        return f == null || f.enabled();
    }

    static Store.HoppityEventStats year(int year) {
        return HoppityEnv.profile().eventStats.computeIfAbsent(year, k -> new Store.HoppityEventStats());
    }

    private static Store.HoppityEventStats current() {
        return year(HoppityTime.now().year());
    }

    // onRabbitFound: only finds during the hunt count, after it they move the "since" count of the year's rabbits
    static void onRabbitFound(HoppityApi.DataSet d) {
        Store.HoppityEventStats stats = current();
        if (!HoppityTime.isHoppityEvent()) {
            stats.typeCountsSince = HoppityCollectionStats.typeCountSnapshot();
            return;
        }
        if (d.lastMeal != null) stats.mealsFound.merge(d.lastMeal.name(), 1, Integer::sum);
        String rarity = HoppityApi.rarityByRabbit(d.lastName);
        if (rarity == null) return;
        int[] counts = stats.rabbitsFound.computeIfAbsent(rarity, k -> new int[3]);
        if (d.duplicate) counts[1]++;
        else counts[0]++;
        if (d.lastDuplicateAmount != null && d.lastDuplicateAmount > 0) stats.dupeChocolateGained += d.lastDuplicateAmount;
        stats.typeCountSnapshot = HoppityCollectionStats.typeCountSnapshot();
    }

    // addStrayCaught, from the stray tracker
    public static void addStrayCaught(String rarity, long chocolate) {
        if (!HoppityTime.isHoppityEvent()) return;
        Store.HoppityEventStats stats = current();
        stats.rabbitsFound.computeIfAbsent(rarity.toUpperCase(Locale.ROOT), k -> new int[3])[2]++;
        stats.strayChocolateGained += chocolate;
    }

    // rabbitTheFishPattern on the formatted line
    static void onChat(String message) {
        if (HoppityTime.isHoppityEvent() && HoppityApi.RABBIT_THE_FISH.matcher(message).matches()) current().rabbitTheFishFinds++;
    }

    // updateCfPosition: once per server, from the factory leaderboard item
    static void updatePosition(Integer position, Double percentile) {
        if (!HoppityTime.isHoppityEvent() || position == null || percentile == null) return;
        String server = server();
        boolean same = server != null && server.equals(lastSnapshotServer);
        lastSnapshotServer = server;
        if (same || server == null) return;
        Store.HoppityEventStats stats = current();
        if (stats.initialPosition == -1) {
            stats.initialPosition = position;
            stats.initialPercentile = percentile;
        }
        stats.finalPosition = position;
        stats.finalPercentile = percentile;
        stats.lastLbUpdate = HoppityEnv.now();
    }

    // HypixelData.serverId as the sidebar's lobby code says it
    private static String server() {
        for (String line : GameState.scoreboard) {
            var m = io.muzy.sbc.core.Patterns.get("SH.sb.main.lobbyCode").matcher(line);
            if (m.matches()) return m.group("code");
        }
        return null;
    }

    // once a sec in skyblock
    static void onSecondPassed() {
        checkCfReminder();
        checkEnded();
        if (!HoppityTime.isHoppityEvent()) return;
        if (!ChocolateFactory.inChocolateFactory) {
            lastAddedCf = HoppityEnv.FAR_PAST;
            return;
        }
        if (!HoppityEnv.isFarPast(lastAddedCf)) current().millisInCf += HoppityEnv.passedSince(lastAddedCf);
        lastAddedCf = HoppityEnv.now();
    }

    static void onProfileJoin() {
        lastSnapshotServer = null;
        lastCheckedEnd = HoppityEnv.FAR_PAST;
    }

    // checkLbUpdateWarning
    private static void checkCfReminder() {
        if (!HoppityTime.isHoppityEvent() || !Hoppity.b(ID, "cf_reminder", false)) return;
        double lastHours = Hoppity.n(ID, "cf_reminder_hours", 2);
        if (lastHours <= 0) return;
        Long end = HoppityTime.eventEndMark();
        if (lastHours < 30 && end != null && end - HoppityEnv.now() >= lastHours * 3_600_000) return;
        long interval = (long) (Hoppity.n(ID, "cf_reminder_minutes", 30) * 60_000);
        if (HoppityEnv.passedSince(lastCfReminder) < interval) return;
        if (HoppityEnv.passedSince(current().lastLbUpdate) < interval) return;
        lastCfReminder = HoppityEnv.now();
        HoppityEnv.world.chat("§6§lReminder! §r§eSwitch to a new server and run §6/cf §eto update your leaderboard position in Hoppity Event stats.");
    }

    // checkEnded: a hunt thats over and not summed up yet
    private static void checkEnded() {
        if (!on(ID)) return;
        if (HoppityEnv.passedSince(lastCheckedEnd) < 5000) return;
        lastCheckedEnd = HoppityEnv.now();
        int now = HoppityTime.now().year();
        boolean over = !HoppityTime.isHoppityEvent();
        for (Map.Entry<Integer, Store.HoppityEventStats> e : new ArrayList<>(HoppityEnv.profile().eventStats.entrySet())) {
            Store.HoppityEventStats stats = e.getValue();
            if (stats.summarized || !(e.getKey() < now || (e.getKey() == now && over))) continue;
            if (hasParticipated(stats)) send(stats, e.getKey());
            stats.summarized = true;
        }
    }

    private static boolean hasParticipated(Store.HoppityEventStats s) {
        return !s.mealsFound.isEmpty() || !s.rabbitsFound.isEmpty() || s.millisInCf != 0;
    }

    // getSpawnedEggCounts for one year
    static Map<HoppityEggType, Integer> spawned(int year) {
        Map<HoppityEggType, Integer> out = new LinkedHashMap<>();
        SbDateNow now = SbDateNow.of();
        if (year > now.year) return out;
        if (HoppityTime.eventNumber(year) < 41) {
            out.put(HoppityEggType.BREAKFAST, 93);
            out.put(HoppityEggType.LUNCH, 93);
            out.put(HoppityEggType.DINNER, 93);
            return out;
        }
        if (year < now.year) {
            // events end with 47 of the normal day's eggs and 46 of the alt day's
            for (HoppityEggType t : HoppityEggType.RESETTING) out.put(t, t.altDay ? 46 : 47);
            return out;
        }
        long difference = HoppityEnv.now() - HoppityTime.eventStart(year);
        boolean past = difference > 3 * io.muzy.sbc.core.SbTime.MONTH;
        int fullDays = (int) (difference / io.muzy.sbc.core.SbTime.DAY);
        int remainderDays = fullDays % 2;
        boolean altToday = HoppityTime.isAlternateDay();
        boolean altRemainder = remainderDays == 1 && !altToday;
        for (HoppityEggType t : HoppityEggType.RESETTING) {
            int count = fullDays / 2;
            if (remainderDays == 1 && altRemainder == t.altDay) count++;
            if (!past && altToday == t.altDay && now.hour >= t.resetsAt) count++;
            out.put(t, count);
        }
        return out;
    }

    private record SbDateNow(int year, int hour) {
        static SbDateNow of() {
            var d = HoppityTime.now();
            return new SbDateNow(d.year(), d.hour());
        }
    }

    private static int mealTotal(Store.HoppityEventStats s) {
        int total = 0;
        for (HoppityEggType t : HoppityEggType.RESETTING) total += s.mealsFound.getOrDefault(t.name(), 0);
        return total;
    }

    private static int sum(Map<HoppityEggType, Integer> m) {
        int total = 0;
        for (int n : m.values()) total += n;
        return total;
    }

    private static int meals(Store.HoppityEventStats s, HoppityEggType... types) {
        int total = 0;
        for (HoppityEggType t : types) total += s.mealsFound.getOrDefault(t.name(), 0);
        return total;
    }

    private static String plural(long n, String word) {
        return n == 1 ? word : word + "s";
    }

    // getRabbitsFormat: name + count, one number per rarity
    static List<String> rabbitsFormat(Map<String, Integer> byRarity, String name, int previous, int current, int since) {
        int total = 0;
        for (int n : byRarity.values()) total += n;
        if (total == 0) return List.of();
        String count = "";
        if (Hoppity.b(ID, "show_count_diff", false) && previous != 0 && current != 0) count = " §7(" + previous + (since > 0 ? " §8+" + since + "§7" : "") + " -> " + current + ")";
        StringBuilder rarities = new StringBuilder();
        for (String r : HoppityApi.RARITIES) {
            if (rarities.length() > 0) rarities.append(" §7-");
            rarities.append(" ").append(HoppityCompactChat.rarityCode(r)).append(HoppityEnv.sep(byRarity.getOrDefault(r, 0)));
        }
        return List.of("§7" + name + " Rabbits: §f" + HoppityEnv.sep(total) + count, rarities.toString());
    }

    private static Map<String, Integer> kind(Store.HoppityEventStats s, int index) {
        Map<String, Integer> out = new LinkedHashMap<>();
        s.rabbitsFound.forEach((r, c) -> out.put(r, c[index]));
        return out;
    }

    // getPairTriple: this hunt's rabbits of a kind vs last year's record
    private static List<String> rabbits(Store.HoppityEventStats s, int year, int index, String name) {
        Store.HoppityEventStats last = HoppityEnv.profile().eventStats.get(year - 1);
        int previous = 0, current = 0, since = 0;
        if (last != null && s.typeCountSnapshot != null && last.typeCountSnapshot != null) {
            int was = last.typeCountSnapshot[index], now = s.typeCountSnapshot[index];
            int after = (last.typeCountsSince == null ? was : last.typeCountsSince[index]) - was;
            if (was > 0 && was != now && after > 0) {
                previous = was;
                current = now;
                since = after;
            }
        }
        return rabbitsFormat(kind(s, index), name, previous, current, since);
    }

    // stats as chat lines, skyhanni's default order + the ones it hides by default
    static List<String> lines(Store.HoppityEventStats s, int year) {
        List<String> out = new ArrayList<>();
        int meals = mealTotal(s);
        Map<HoppityEggType, Integer> spawned = spawned(year);
        if (meals > 0) out.add("§7You found §b" + HoppityEnv.sep(meals) + "§7/§a" + HoppityEnv.sep(sum(spawned)) + " §6Chocolate Meal " + plural(meals, "Egg") + "§7.");
        int hitman = meals(s, HoppityEggType.HITMAN);
        if (hitman > 0 && HoppityTime.eventNumber(year) > 41) {
            int missed = sum(spawned) - meals;
            out.add("§7You recovered §b" + HoppityEnv.sep(hitman) + "§7/§a" + HoppityEnv.sep(missed) + " §7missed §6Meal " + plural(missed, "Egg") + " §7from §cRabbit Hitman§7.");
        }
        int bought = meals(s, HoppityEggType.BOUGHT, HoppityEggType.BOUGHT_ABIPHONE);
        if (bought > 0) out.add("§7You bought §b" + HoppityEnv.sep(bought) + " §f" + plural(bought, "Rabbit") + " §7from §aHoppity§7.");
        int visitors = meals(s, HoppityEggType.VISITOR);
        if (visitors > 0) out.add("§6Hoppity §7gave you §b" + visitors + " §d" + plural(visitors, "Rabbit") + " §r§7on your §aGarden§7.");
        int sideDish = meals(s, HoppityEggType.SIDE_DISH);
        if (sideDish > 0) out.add("§7You found §b" + sideDish + " §6§lSide Dish " + plural(sideDish, "Egg") + " §r§7in the §6Chocolate Factory§7.");
        int milestones = meals(s, HoppityEggType.CHOCOLATE_FACTORY_MILESTONE, HoppityEggType.CHOCOLATE_SHOP_MILESTONE);
        if (milestones > 0) out.add("§7You claimed §b" + milestones + " §6§lMilestone " + plural(milestones, "Rabbit") + "§7.");
        out.add("");
        out.addAll(rabbits(s, year, 0, "Unique"));
        out.add("");
        out.addAll(rabbits(s, year, 1, "Duplicate"));
        chocolate(out, s.dupeChocolateGained);
        out.add("");
        out.addAll(rabbits(s, year, 2, "Stray"));
        chocolate(out, s.strayChocolateGained);
        out.add("");
        if (s.millisInCf > 0) out.add("§7You spent §b" + HoppityEnv.format(s.millisInCf, 2) + " §7in the §6Chocolate Factory§7.");
        if (s.rabbitTheFishFinds > 0) out.add("§7You found §b" + s.rabbitTheFishFinds + " §cRabbit the Fish §7in Meal Eggs.");
        if (s.initialPosition != -1 && s.finalPosition != -1 && s.initialPosition != s.finalPosition) {
            out.add("§7Leaderboard: §b#" + HoppityEnv.sep(s.initialPosition) + " §c-> §b#" + HoppityEnv.sep(s.finalPosition));
            int change = s.initialPosition - s.finalPosition;
            String spots = " §7(" + (change > 0 ? "§a+" : "§c") + HoppityEnv.sep(change) + " " + plural(Math.abs(change), "spot") + "§7)";
            out.add(spots + (s.finalPercentile != s.initialPercentile ? " §7Top §a" + s.initialPercentile + "% §c-> §7Top §a" + s.finalPercentile + "%" : " §7Top §a" + s.initialPercentile + "%"));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        for (int i = out.size() - 1; i > 0; i--) if (out.get(i).isEmpty() && out.get(i - 1).isEmpty()) out.remove(i);
        return out;
    }

    private static void chocolate(List<String> out, long gained) {
        if (gained <= 0) return;
        String line = " §6+" + HoppityEnv.sep(gained) + " Chocolate";
        long time = ChocolateFactory.timeUntilNeed(gained);
        if (Hoppity.b(Hoppity.CF, "showDuplicateTime", true) && time != Long.MAX_VALUE) line += " §7(§a+§b" + HoppityEnv.format(time, 2) + "§7)";
        out.add(line);
    }

    // sendStatsMessage
    static void send(Store.HoppityEventStats s, int year) {
        HoppityEnv.world.richChat(SEPARATOR, null, null);
        HoppityEnv.world.richChat(" ".repeat(26) + "§d§lHoppity's Hunt #" + HoppityTime.eventNumber(year) + " Stats", null, null);
        HoppityEnv.world.richChat("", null, null);
        for (String l : lines(s, year)) HoppityEnv.world.richChat(l.isEmpty() ? "" : LINE_HEADER + l, null, null);
        HoppityEnv.world.richChat(SEPARATOR, null, null);
    }

    // getAllTimeStats
    static Store.HoppityEventStats allTime() {
        Store.HoppityEventStats all = new Store.HoppityEventStats();
        for (Map.Entry<Integer, Store.HoppityEventStats> e : new java.util.TreeMap<>(HoppityEnv.profile().eventStats).entrySet()) {
            Store.HoppityEventStats s = e.getValue();
            s.mealsFound.forEach((k, v) -> all.mealsFound.merge(k, v, Integer::sum));
            s.rabbitsFound.forEach((k, v) -> {
                int[] sum = all.rabbitsFound.computeIfAbsent(k, x -> new int[3]);
                for (int i = 0; i < 3; i++) sum[i] += v[i];
            });
            all.dupeChocolateGained += s.dupeChocolateGained;
            all.strayChocolateGained += s.strayChocolateGained;
            all.millisInCf += s.millisInCf;
            all.rabbitTheFishFinds += s.rabbitTheFishFinds;
            if (all.initialPosition == -1 && s.initialPosition != -1) {
                all.initialPosition = s.initialPosition;
                all.initialPercentile = s.initialPercentile;
            }
            if (s.finalPosition != -1) {
                all.finalPosition = s.finalPosition;
                all.finalPercentile = s.finalPercentile;
            }
        }
        return all;
    }

    // /sbc hoppitystats: this hunt, a year, or everything
    public static void command(String which) {
        Store.HoppityProfile p = HoppityEnv.profile();
        if (which != null && which.equalsIgnoreCase("all")) {
            HoppityEnv.world.richChat("§d§lAll Hoppity's Hunts", null, null);
            for (String l : allLines()) HoppityEnv.world.richChat(l, null, null);
            return;
        }
        int year = HoppityTime.now().year();
        if (which != null && !which.isEmpty()) {
            try {
                year = Integer.parseInt(which.trim());
            } catch (NumberFormatException e) {
                HoppityEnv.world.chat("§cSay a year, or all.");
                return;
            }
        }
        Store.HoppityEventStats s = p.eventStats.get(year);
        if (s == null || !hasParticipated(s)) {
            HoppityEnv.world.chat("§eNothing recorded for year " + year + ".");
            return;
        }
        send(s, year);
    }

    private static List<String> allLines() {
        Store.HoppityEventStats all = allTime();
        List<String> out = new ArrayList<>();
        int meals = mealTotal(all), spawned = 0;
        for (int year : HoppityEnv.profile().eventStats.keySet()) spawned += sum(spawned(year));
        if (meals > 0) out.add("§7You found §b" + HoppityEnv.sep(meals) + "§7/§a" + HoppityEnv.sep(spawned) + " §6Chocolate Meal " + plural(meals, "Egg") + "§7.");
        out.addAll(rabbitsFormat(kind(all, 0), "Unique", 0, 0, 0));
        out.addAll(rabbitsFormat(kind(all, 1), "Duplicate", 0, 0, 0));
        out.addAll(rabbitsFormat(kind(all, 2), "Stray", 0, 0, 0));
        chocolate(out, all.dupeChocolateGained + all.strayChocolateGained);
        if (all.millisInCf > 0) out.add("§7You spent §b" + HoppityEnv.format(all.millisInCf, 2) + " §7in the §6Chocolate Factory§7.");
        return out;
    }

    public static void reset() {
        lastAddedCf = lastCfReminder = lastCheckedEnd = HoppityEnv.FAR_PAST;
        lastSnapshotServer = null;
    }

    // --- the card ---

    // card numbers, live or sample
    record View(int number, String when, int meals, int spawnedMeals, int hitman, int missed, int bought, int visitors, int sideDish, int milestones,
                Map<String, int[]> rabbits, long dupeChocolate, long strayChocolate, long cfMillis, int fish, String allTime) {}

    private static View view() {
        int year = HoppityTime.now().year();
        Store.HoppityEventStats s = current();
        Map<HoppityEggType, Integer> spawned = spawned(year);
        String allTime = "";
        if (Hoppity.b(LIVE, "all_time", true)) allTime = HoppityEnv.sep(mealTotal(allTime()));
        return new View(HoppityTime.eventNumber(year), whenText(), mealTotal(s), sum(spawned), meals(s, HoppityEggType.HITMAN), Math.max(0, sum(spawned) - mealTotal(s)),
                meals(s, HoppityEggType.BOUGHT, HoppityEggType.BOUGHT_ABIPHONE), meals(s, HoppityEggType.VISITOR), meals(s, HoppityEggType.SIDE_DISH),
                meals(s, HoppityEggType.CHOCOLATE_FACTORY_MILESTONE, HoppityEggType.CHOCOLATE_SHOP_MILESTONE), s.rabbitsFound, s.dupeChocolateGained, s.strayChocolateGained, s.millisInCf, s.rabbitTheFishFinds, allTime);
    }

    // hunt time in the card's band: until it ends, or the end date
    private static String whenText() {
        Long end = HoppityTime.eventEndMark();
        long at = end != null ? end : HoppityTime.eventStart(HoppityTime.now().year() + 1);
        if (Hoppity.text(LIVE, "date_format", "Time left").equals("Date")) return (end != null ? "§7ends " : "§7next ") + "§f" + DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()));
        return (end != null ? "§7ends in " : "§7next in ") + "§f" + HoppityEnv.format(at - HoppityEnv.now(), 2);
    }

    private static int total(Map<String, int[]> rabbits, int index) {
        int n = 0;
        for (int[] c : rabbits.values()) n += c[index];
        return n;
    }

    private static void card(EventSheet s, View v) {
        s.right = "§d#" + v.number + " " + v.when;
        double p = v.spawnedMeals > 0 ? Math.min(1, v.meals / (double) v.spawnedMeals) : 0;
        s.line("Meal eggs", Modern.fraction(HoppityEnv.sep(v.meals), HoppityEnv.sep(v.spawnedMeals), 'f', '7') + (v.spawnedMeals > 0 ? " §7" + RowText.percent(p) : ""), Modern.INK).icon("egg").meter(p, Modern.GOOD).brief("Meals");
        if (v.hitman > 0) s.line("From Hitman", v.hitman + "§7/" + (v.missed + v.hitman), Modern.BAD).icon("iron_sword").brief("Hitman");
        int fresh = total(v.rabbits, 0), dupes = total(v.rabbits, 1), strays = total(v.rabbits, 2);
        s.tiles(EventSheet.tile("Unique", HoppityEnv.sep(fresh), fresh > 0 ? Modern.GOOD : Modern.DIM), EventSheet.tile("Duplicates", HoppityEnv.sep(dupes), dupes > 0 ? Modern.INK : Modern.DIM, "Dupes"), EventSheet.tile("Strays", HoppityEnv.sep(strays), strays > 0 ? Modern.AQUA : Modern.DIM));
        if (fresh + dupes + strays > 0) {
            EventSheet.Grid g = s.grid();
            for (String r : HoppityApi.RARITIES) {
                int[] c = v.rabbits.get(r);
                if (c != null) g.tinted(r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT), c[0] + "§7/" + c[1] + "/" + c[2], Modern.INK, HoppityCards.rarityColor(r));
            }
        }
        if (v.dupeChocolate > 0) s.line("Duplicate chocolate", "+" + HoppityEnv.sep(v.dupeChocolate), Modern.GOLD).icon("cocoa_beans").brief("Dupe");
        if (v.strayChocolate > 0) s.line("Stray chocolate", "+" + HoppityEnv.sep(v.strayChocolate), Modern.GOLD).icon("cocoa_beans").brief("Stray");
        if (v.bought > 0) s.line("Bought", String.valueOf(v.bought), Modern.INK);
        if (v.visitors > 0) s.line("From visitors", String.valueOf(v.visitors), Modern.INK).brief("Visitors");
        if (v.sideDish > 0) s.line("Side dishes", String.valueOf(v.sideDish), Modern.GOLD).brief("Side");
        if (v.milestones > 0) s.line("Milestones", String.valueOf(v.milestones), Modern.GOLD);
        if (v.cfMillis > 0) s.line("In the factory", HoppityEnv.format(v.cfMillis, 2), Modern.AQUA).brief("Factory");
        if (v.fish > 0) s.line("Rabbit the Fish", String.valueOf(v.fish), Modern.BAD).brief("Fish");
        if (!v.allTime.isEmpty()) s.line("All-time meal eggs", v.allTime, Modern.LABEL).brief("All-time");
    }

    private static boolean toggledOff;

    // liveDisplayEnabled
    private static boolean shows() {
        if (toggledOff) return false;
        if (Hoppity.b(LIVE, "only_event", true) && !HoppityTime.isHoppityEvent()) return false;
        if (Hoppity.b(LIVE, "only_islands", false) && !HoppityApi.onHoppityIsland()) return false;
        return !Hoppity.b(LIVE, "only_locator", false) || HoppityEnv.world.locatorInHand();
    }

    public static void toggle() {
        toggledOff = !toggledOff;
    }

    static HudElement element() {
        return new EventSheet.Card(LIVE, "Hoppity's Hunt stats", "Events", Zone.TOP_LEFT, 65, LIVE) {
            @Override public boolean visible() {
                return HoppityEnv.world.inSkyBlock() && shows();
            }

            @Override public String heading() {
                return compact() ? "Hunt stats" : "Hoppity's Hunt stats";
            }

            @Override protected void live(EventSheet s) {
                card(s, view());
            }

            @Override protected void sample(EventSheet s) {
                Map<String, int[]> rabbits = new LinkedHashMap<>();
                rabbits.put("COMMON", new int[]{3, 28, 80});
                rabbits.put("UNCOMMON", new int[]{2, 19, 44});
                rabbits.put("RARE", new int[]{1, 9, 19});
                rabbits.put("EPIC", new int[]{1, 3, 6});
                rabbits.put("LEGENDARY", new int[]{0, 1, 2});
                card(s, new View(71, "§7ends in §f12h 40m", 188, 240, 28, 52, 7, 3, 4, 2, rabbits, 2_450_000_000L, 915_000_000L, 16_560_000L, 2, "1,204"));
            }

            @Override protected List<Row> plainRows() {
                List<Row> out = new ArrayList<>();
                for (String l : lines(current(), HoppityTime.now().year())) if (!l.isEmpty()) out.add(new Row(l));
                return out;
            }

            @Override protected List<Row> plainDemo() {
                return List.of(new Row("§7You found §b188§7/§a240 §6Chocolate Meal Eggs§7."), new Row("§7Unique Rabbits: §f7"), new Row(" §f3 §7- §a2 §7- §91 §7- §51"));
            }
        };
    }

    static List<Feature> features() {
        return List.of(
                new Feature(ID, "Events", "Hoppity's Hunt summary", "When the hunt is over: meal eggs found of those that spawned, rabbits by rarity, chocolate, time in the factory and your leaderboard change. /sbc hoppitystats shows it for any year.", "SkyHanni: Hoppity Hunt Stats Summary")
                        .when(() -> HoppityEnv.world.inSkyBlock())
                        .option(Option.bool("hoppity_summary.show_count_diff", "Count before and after", "Next to unique and duplicate rabbits: 270 -> 275.", false))
                        .option(Option.bool("hoppity_summary.cf_reminder", "Leaderboard reminder", "Tells you now and then to change server and open /cf, so your position gets read.", false))
                        .option(Option.integer("hoppity_summary.cf_reminder_minutes", "Reminder every (minutes)", "", 30, 1, 60))
                        .option(Option.integer("hoppity_summary.cf_reminder_hours", "Only in the last (hours)", "Of the hunt. 0 is never, 30 the whole hunt.", 2, 0, 30)),
                new Feature(LIVE, "Events", "Hoppity's Hunt stats", "This hunt's numbers on a card as they come in: meal eggs of those that spawned, rabbits by rarity as unique, duplicate and stray, chocolate, time in the factory.", "SkyHanni: Live Display")
                        .when(() -> HoppityEnv.world.inSkyBlock())
                        .defaultOff()
                        .option(Option.bool("hoppity_live.only_event", "Only during the hunt", "", true))
                        .option(Option.bool("hoppity_live.only_islands", "Only on islands with eggs", "", false))
                        .option(Option.bool("hoppity_live.only_locator", "Only holding the Egglocator", "", false))
                        .option(Option.bool("hoppity_live.all_time", "All-time line", "The meal eggs of every hunt on record.", true))
                        .option(Option.choice("hoppity_live.date_format", "Time in the band", "", "Time left", "Time left", "Date")));
    }
}
