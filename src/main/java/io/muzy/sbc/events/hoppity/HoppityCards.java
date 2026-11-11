// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.events.hoppity;

import io.muzy.sbc.core.Store;
import io.muzy.sbc.events.EventSheet;
import io.muzy.sbc.events.hoppity.HoppityCollectionStats.Highlight;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// hoppity card layouts. each takes plain numbers so live menu data and the editor sample run the same code
// a number nobody knows yet is hidden or a dash, never made up
final class HoppityCards {
    private static final int COCOA = 0xFFC48A5A, TOWER = 0xFFFF7CE5, GREY = 0xFFA9B4C2;
    private static final int MORNING = 0xFFFFB454, NOON = 0xFF7FB3FF, EVENING = Modern.GOOD;

    private HoppityCards() {}

    private static String sep(long n) {
        return HoppityEnv.sep(n);
    }

    private static String share(double part, double whole) {
        return whole <= 0 ? "" : RowText.percent(part / whole);
    }

    // game's dark blue is unreadable on the glass
    private static int mealColor(HoppityEggType t) {
        return switch (t) {
            case BREAKFAST, BRUNCH -> MORNING;
            case LUNCH, DEJEUNER -> NOON;
            case DINNER, SUPPER -> EVENING;
            case HITMAN -> Modern.BAD;
            case VISITOR -> EventSheet.MYTHIC;
            case SIDE_DISH, CHOCOLATE_SHOP_MILESTONE, CHOCOLATE_FACTORY_MILESTONE -> Modern.GOLD;
            default -> Modern.GOOD;
        };
    }

    // --- unclaimed eggs ---

    // one meal: name, state, timer text, how far recharged (-1 = no meter)
    record Meal(String name, int color, String state, String time, double recharge) {}

    private static final String READY = "READY", CLAIMED = "CLAIMED", SOON = "SOON", LAST = "LAST";

    static List<Meal> liveMeals() {
        List<Meal> out = new ArrayList<>();
        long period = 2 * HoppityTime.dayMillis();
        for (HoppityEggType t : HoppityEggsManager.entries()) {
            String state = !t.hasRemainingSpawns() ? LAST : t.hasNotFirstSpawnedYet() ? SOON : t.isClaimed() ? CLAIMED : READY;
            double recharge = state.equals(LAST) ? -1 : state.equals(READY) ? 1 : Math.max(0, 1 - t.timeUntil() / (double) period);
            out.add(new Meal(t.mealName, mealColor(t), state, HoppityEggsManager.eggTime(t), recharge));
        }
        return out;
    }

    static void unclaimed(EventSheet s, List<Meal> meals, String locations) {
        int up = 0;
        for (Meal m : meals) if (m.state.equals(READY) || m.state.equals(LAST)) up++;
        if (!meals.isEmpty()) s.right = "§7ready " + EventSheet.ratio(String.valueOf(up), String.valueOf(meals.size()), up / (double) meals.size(), 'a', 'f');
        for (Meal m : meals) {
            boolean ready = m.state.equals(READY), last = m.state.equals(LAST);
            int chip = ready ? Modern.GOOD : last ? Modern.BAD : m.state.equals(SOON) ? Modern.AQUA : Modern.DIM;
            int time = last ? Modern.BAD : ready ? Modern.LABEL : Modern.lighten(m.color, 0.2f);
            EventSheet.Line l = s.line(m.name, m.time, time).labelColor(ready || last ? m.color : Modern.DIM).pip(m.color, ready || last).chip(m.state, chip).chipAt(54);
            if (m.recharge >= 0) l.meter(m.recharge, m.color);
        }
        if (locations != null) {
            s.rule();
            s.shaped(locations);
        }
    }

    static void unclaimedLive(EventSheet s) {
        List<String> lines = HoppityEggsManager.displayLines();
        if (lines.isEmpty()) return;
        String last = lines.get(lines.size() - 1);
        unclaimed(s, liveMeals(), last.startsWith("§7Locations: ") ? last : null);
    }

    static void unclaimedSample(EventSheet s) {
        unclaimed(s, List.of(
                new Meal("Breakfast", MORNING, READY, "12m 40s", 1),
                new Meal("Lunch", NOON, READY, "19m 2s", 1),
                new Meal("Déjeuner", NOON, CLAIMED, "6m 18s", 0.84),
                new Meal("Dinner", EVENING, CLAIMED, "25m 44s", 0.36),
                new Meal("Brunch", MORNING, CLAIMED, "32m 40s", 0.18),
                new Meal("Supper", EVENING, LAST, "1h 3m", -1)), "§7Locations: §e9§7/§a15");
    }

    // --- the hunt ---

    // each regular meal next to its other-day twin, then the other ways to get a rabbit
    private static final List<HoppityEggType> EGG_ORDER = List.of(HoppityEggType.BREAKFAST, HoppityEggType.BRUNCH, HoppityEggType.LUNCH, HoppityEggType.DEJEUNER, HoppityEggType.DINNER, HoppityEggType.SUPPER,
            HoppityEggType.SIDE_DISH, HoppityEggType.HITMAN, HoppityEggType.BOUGHT, HoppityEggType.BOUGHT_ABIPHONE, HoppityEggType.CHOCOLATE_SHOP_MILESTONE, HoppityEggType.CHOCOLATE_FACTORY_MILESTONE, HoppityEggType.STRAY, HoppityEggType.VISITOR);

    static int rarityColor(String r) {
        return switch (r) {
            case "UNCOMMON" -> EventSheet.UNCOMMON;
            case "RARE" -> EventSheet.RARE;
            case "EPIC" -> EventSheet.EPIC;
            case "LEGENDARY" -> EventSheet.LEGENDARY;
            case "MYTHIC" -> EventSheet.MYTHIC;
            case "DIVINE" -> EventSheet.DIVINE;
            default -> EventSheet.COMMON;
        };
    }

    private static String shortMeal(HoppityEggType t) {
        return t == HoppityEggType.CHOCOLATE_FACTORY_MILESTONE ? "Choc. Milestone" : t.mealName;
    }

    static void hunt(EventSheet s, Map<HoppityEggType, Integer> eggs, Map<String, Integer> rabbits, int fresh, int dupes, long dupeChocolate) {
        int eggTotal = 0, rabbitTotal = 0;
        for (int n : eggs.values()) eggTotal += n;
        for (int n : rabbits.values()) rabbitTotal += n;
        if (eggTotal > 0) s.right = "§f" + sep(eggTotal) + " §7eggs";
        int found = fresh + dupes;
        s.tiles(EventSheet.tile("New rabbits", sep(fresh) + (found > 0 ? " §7" + share(fresh, found) : ""), Modern.GOOD, "New"),
                EventSheet.tile("Duplicates", sep(dupes) + (found > 0 ? " §7" + share(dupes, found) : ""), GREY, "Dupes"));
        if (found > 0) s.stack(List.of(new EventSheet.Share(fresh, Modern.GOOD), new EventSheet.Share(dupes, GREY)));
        if (dupeChocolate > 0) {
            s.line("Duplicate chocolate", "+" + sep(dupeChocolate), Modern.GOLD).icon("cocoa_beans").brief("Choc");
            if (dupes > 0) s.line("Per duplicate", "+" + sep(dupeChocolate / dupes), Modern.LABEL).indent().brief("/dupe");
        }
        if (eggTotal > 0) {
            s.section("Eggs");
            EventSheet.Grid g = s.grid();
            for (HoppityEggType t : EGG_ORDER) {
                Integer n = eggs.get(t);
                if (n != null) g.tinted(shortMeal(t), sep(n), Modern.INK, mealColor(t)).brief(t == HoppityEggType.CHOCOLATE_FACTORY_MILESTONE ? "Milestone" : t == HoppityEggType.CHOCOLATE_SHOP_MILESTONE ? "Shop Mile." : t.mealName);
            }
        }
        if (rabbitTotal > 0) {
            s.section("Rabbits", sep(rabbitTotal));
            List<EventSheet.Share> shares = new ArrayList<>();
            for (String r : HoppityApi.RARITIES) if (rabbits.containsKey(r)) shares.add(new EventSheet.Share(rabbits.get(r), rarityColor(r)));
            s.stack(shares);
            EventSheet.Grid g = s.grid();
            for (String r : HoppityApi.RARITIES) {
                Integer n = rabbits.get(r);
                if (n != null) g.tinted(r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT), sep(n), Modern.INK, rarityColor(r), share(n, rabbitTotal)).brief(briefRarity(r));
            }
        }
    }

    private static String briefRarity(String r) {
        return switch (r) {
            case "COMMON" -> "Com";
            case "UNCOMMON" -> "Unc";
            case "LEGENDARY" -> "Leg";
            case "MYTHIC" -> "Myth";
            case "DIVINE" -> "Div";
            default -> r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT);
        };
    }

    static void huntSample(EventSheet s) {
        Map<HoppityEggType, Integer> eggs = new java.util.LinkedHashMap<>();
        eggs.put(HoppityEggType.BREAKFAST, 14);
        eggs.put(HoppityEggType.BRUNCH, 13);
        eggs.put(HoppityEggType.LUNCH, 12);
        eggs.put(HoppityEggType.DEJEUNER, 13);
        eggs.put(HoppityEggType.DINNER, 11);
        eggs.put(HoppityEggType.SUPPER, 12);
        eggs.put(HoppityEggType.HITMAN, 28);
        eggs.put(HoppityEggType.STRAY, 6);
        eggs.put(HoppityEggType.SIDE_DISH, 4);
        eggs.put(HoppityEggType.CHOCOLATE_FACTORY_MILESTONE, 1);
        Map<String, Integer> rabbits = new java.util.LinkedHashMap<>();
        rabbits.put("COMMON", 48);
        rabbits.put("UNCOMMON", 33);
        rabbits.put("RARE", 19);
        rabbits.put("EPIC", 9);
        rabbits.put("LEGENDARY", 3);
        rabbits.put("MYTHIC", 1);
        hunt(s, eggs, rabbits, 22, 91, 1_284_500_000L);
    }

    // --- the factory ---

    // every number the stats card shows, read or faked (sample) in one place
    static final class Factory {
        int prestige, raw, rabbits, maxRabbits, towerNow, towerMax, towerLevel, hitmanEggs, hitmanSlots;
        boolean maxPrestige, atCap, barnFull, towerActive, towerFull;
        long chocolate, cap, thisPrestige, prestigeCost, toPrestige, allTime, nextMilestone, toMilestone, bestCost;
        long towerLeft, nextCharge, fullIn, hitmanNext, hitmanAll;
        double perSecond, multiplier, towerBoost, bestGain;
        Integer position;
        Double percentile;
        String timeToPrestige = "", timeToMilestone = "", bestIn = "", lastRabbit = "";

        static Factory live() {
            Store.HoppityProfile p = HoppityEnv.profile();
            Factory f = new Factory();
            long since = ChocolateFactory.chocolateSinceUpdate(), now = HoppityEnv.now();
            f.prestige = ChocolateFactory.currentPrestige;
            f.maxPrestige = ChocolateFactory.isMaxPrestige();
            f.chocolate = ChocolateFactory.Amount.CURRENT.chocolate() + since;
            f.cap = p.maxChocolate;
            f.atCap = ChocolateFactory.isMax();
            f.thisPrestige = ChocolateFactory.Amount.PRESTIGE.chocolate() + since;
            f.prestigeCost = ChocolateFactory.chocolateForPrestige;
            f.toPrestige = f.prestigeCost - ChocolateFactory.Amount.PRESTIGE.chocolate();
            f.timeToPrestige = ChocolateFactory.Amount.PRESTIGE.formattedTimeUntilGoal(f.prestigeCost);
            long allTime = ChocolateFactory.Amount.ALL_TIME.chocolate();
            f.allTime = allTime + since;
            f.nextMilestone = HoppityData.nextMilestone(allTime);
            f.toMilestone = f.nextMilestone - allTime;
            if (f.nextMilestone > 0) f.timeToMilestone = ChocolateFactory.Amount.ALL_TIME.formattedTimeUntilGoal(f.nextMilestone);
            f.perSecond = ChocolateFactory.chocolatePerSecond;
            f.raw = p.rawChocPerSecond;
            f.multiplier = p.chocolateMultiplier;
            f.rabbits = p.currentRabbits;
            f.maxRabbits = p.maxRabbits;
            f.barnFull = p.maxRabbits > 0 && ChocolateFactory.isBarnFull();
            f.position = ChocolateFactory.leaderboardPosition;
            f.percentile = ChocolateFactory.leaderboardPercentile;
            f.towerActive = ChocolateFactory.timeTowerActive();
            f.towerFull = ChocolateFactory.timeTowerFull();
            f.towerNow = ChocolateFactory.currentCharges();
            f.towerMax = p.maxTimeTowerUses;
            f.towerLevel = p.timeTowerLevel;
            f.towerBoost = ChocolateFactory.timeTowerMultiplier();
            if (f.towerActive) f.towerLeft = p.currentTimeTowerEnds - now;
            if (!HoppityEnv.isFarPast(p.nextTimeTower)) {
                f.nextCharge = p.nextTimeTower - now;
                if (!f.towerFull) f.fullIn = ChocolateFactory.timeTowerFullTimeMark() - now;
            }
            f.bestCost = p.bestUpgradeCost;
            f.bestIn = ChocolateFactory.Amount.CURRENT.formattedTimeUntilGoal(p.bestUpgradeCost);
            for (ChocolateFactory.Upgrade u : ChocolateFactory.factoryUpgrades) if (u.slotIndex == ChocolateFactory.bestPossibleSlot && u.extraPerSecond != null) f.bestGain = u.extraPerSecond;
            f.hitmanEggs = p.availableHitmanEggs;
            f.hitmanSlots = p.purchasedHitmanSlots;
            f.hitmanNext = p.hitmanSingleSlotCooldown - now;
            f.hitmanAll = p.hitmanAllSlotsCooldown - now;
            f.lastRabbit = ChocolateFactory.lastRabbit;
            return f;
        }

        static Factory sample() {
            Factory f = new Factory();
            f.prestige = 4;
            f.chocolate = 12_845_567_021L;
            f.cap = 60_000_000_000L;
            f.thisPrestige = 3_330_382_389L;
            f.prestigeCost = 4_000_000_000L;
            f.toPrestige = 669_617_611L;
            f.timeToPrestige = "§b1h 0m 29s";
            f.allTime = 27_645_879_859L;
            f.nextMilestone = 30_000_000_000L;
            f.toMilestone = 2_354_120_141L;
            f.timeToMilestone = "§b3h 32m";
            f.perSecond = 184_512.35;
            f.raw = 98_354;
            f.multiplier = 1.876;
            f.rabbits = 412;
            f.maxRabbits = 450;
            f.position = 5_139;
            f.percentile = 5.06;
            f.towerNow = 2;
            f.towerMax = 3;
            f.towerLevel = 8;
            f.towerBoost = 0.8;
            f.nextCharge = 5 * 3_600_000L + 12 * 60_000L;
            f.fullIn = 13 * 3_600_000L + 12 * 60_000L;
            f.bestCost = 148_500_000L;
            f.bestIn = "§aNow";
            f.bestGain = 1_420.5;
            f.hitmanEggs = 3;
            f.hitmanSlots = 28;
            f.hitmanNext = 42 * 60_000L;
            f.hitmanAll = 6 * 3_600_000L;
            f.lastRabbit = "§6Olivette";
            return f;
        }
    }

    private static String decimal(double v) {
        String s = String.format(Locale.ROOT, "%,.3f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    static void factory(EventSheet s, Factory f) {
        s.right = "§6Prestige " + ChocolateFactory.roman(f.prestige) + (f.maxPrestige ? " §a§lMAX" : "");
        EventSheet.Hero hero = s.hero("Chocolate", sep(f.chocolate), Modern.GOLD).brief("Choc");
        if (f.atCap) hero.chip("MAX", Modern.BAD);
        if (f.cap > 0) s.line("Max Chocolate", "§e" + HoppityEnv.shortFormat(f.cap) + "  §7" + share(Math.min(f.chocolate, f.cap), f.cap)).alarmMeter(f.chocolate / (double) f.cap, f.atCap ? Modern.BAD : COCOA).brief("Max");
        s.line("Per second", decimal(f.perSecond), Modern.GOLD).brief("/s");
        s.line("Per hour", sep(Math.round(f.perSecond * 3600)), Modern.INK);
        s.grid().cell("Raw per sec", sep(f.raw), Modern.INK).brief("Raw/s").cell("Multiplier", "x" + decimal(f.multiplier), EventSheet.EPIC).brief("Multi");
        if (f.nextMilestone <= 0) s.line("All-time", sep(f.allTime), Modern.GOLD);

        if (!f.maxPrestige) {
            s.section("Prestige", "§7next §6" + ChocolateFactory.roman(f.prestige + 1));
            if (f.prestigeCost > 0) s.line("This Prestige", EventSheet.ratio(sep(f.thisPrestige), HoppityEnv.shortFormat(f.prestigeCost), f.thisPrestige / (double) f.prestigeCost, '6', 'e')).meter(f.thisPrestige / (double) f.prestigeCost, COCOA);
            if (f.toPrestige <= 0) s.line("Chocolate To Prestige", "").chip("AVAILABLE", Modern.GOOD).chipRight().brief("To go");
            else s.line("Chocolate To Prestige", sep(f.toPrestige), Modern.GOLD).brief("To go");
            s.line("Time To Prestige", f.timeToPrestige).brief("Time");
        } else s.line("This Prestige", sep(f.thisPrestige), Modern.GOLD);
        if (f.nextMilestone > 0) {
            s.section("Milestone", "§7next §e" + HoppityEnv.shortFormat(f.nextMilestone));
            s.line("All-time", EventSheet.ratio(sep(f.allTime), HoppityEnv.shortFormat(f.nextMilestone), f.allTime / (double) f.nextMilestone, '6', 'e')).meter(f.allTime / (double) f.nextMilestone, Modern.GOLD);
            s.line("Chocolate To Milestone", sep(f.toMilestone), Modern.GOLD).brief("To go");
            s.line("Time To Milestone", f.timeToMilestone).brief("Time");
        }

        s.section("Time Tower", f.towerActive ? "§d§lACTIVE" : f.towerLevel > 0 ? "§7Level " + ChocolateFactory.roman(f.towerLevel) : "");
        if (f.towerMax > 0) s.line("Charges", EventSheet.ratio(String.valueOf(f.towerNow), String.valueOf(f.towerMax), f.towerNow / (double) f.towerMax, 'e', 'a')).pips(f.towerNow, f.towerMax, TOWER);
        if (f.towerActive) s.line("Active for", HoppityEnv.format(Math.max(0, f.towerLeft)), TOWER).brief("Active");
        if (f.towerBoost > 0) s.line("Boost when active", "+" + decimal(f.towerBoost) + "x", EventSheet.EPIC).brief("Boost");
        if (!f.towerFull && f.nextCharge > 0) s.line("Next charge", HoppityEnv.format(f.nextCharge), Modern.AQUA).brief("Next");
        if (f.towerFull) s.line("Full Tower Charges", "§a§lNow").brief("Full");
        else s.line("Full Tower Charges", f.fullIn > 0 ? HoppityEnv.format(f.fullIn) : "—", Modern.AQUA).brief("Full");

        s.section("Barn", f.maxRabbits > 0 ? (f.barnFull ? "§c§lFULL  " : "") + "§f" + sep(Math.max(0, f.maxRabbits - f.rabbits)) + " §7free" : "");
        if (f.maxRabbits > 0) s.line("Rabbits", EventSheet.ratio(sep(f.rabbits), sep(f.maxRabbits), f.rabbits / (double) f.maxRabbits, f.barnFull ? 'c' : 'e', '6')).alarmMeter(f.rabbits / (double) f.maxRabbits, f.barnFull ? Modern.BAD : COCOA);
        else s.line("Rabbits", "§f" + sep(f.rabbits) + "§7/—");
        if (f.lastRabbit != null && !f.lastRabbit.isEmpty()) s.line("Last rabbit", f.lastRabbit).brief("Last");

        s.section("Leaderboard").inLine();
        EventSheet.Line place = s.line("Position", f.position == null ? "—" : "#" + sep(f.position), Modern.AQUA).brief("Leaderboard");
        if (f.percentile != null) place.chip("Top " + decimal(f.percentile) + "%", Modern.GOOD);

        s.section("Best Upgrade");
        if (f.bestCost > 0) {
            s.line("Cost", sep(f.bestCost), Modern.GOLD);
            if (f.chocolate < f.bestCost) s.line("Saved", EventSheet.ratio(HoppityEnv.shortFormat(f.chocolate), HoppityEnv.shortFormat(f.bestCost), f.chocolate / (double) f.bestCost, '6', 'e')).meter(f.chocolate / (double) f.bestCost, Modern.GOLD);
            if (f.bestGain > 0) {
                s.line("Gain", "+" + decimal(f.bestGain) + " §7per second", Modern.GOOD).briefValue("+" + decimal(f.bestGain) + "§7/s");
                s.line("Pays back in", HoppityEnv.format((long) (f.bestCost / f.bestGain * 1000)), Modern.AQUA).brief("Payback");
            }
            s.line("Affordable in", f.bestIn).brief("Affordable");
        } else s.line("Affordable in", "—").brief("Affordable");

        if (f.hitmanEggs > 0 || f.hitmanSlots > 0) {
            s.section("Rabbit Hitman");
            s.grid().cell("Eggs", sep(f.hitmanEggs), Modern.BAD).cell("Slots", sep(f.hitmanSlots), Modern.INK);
            if (f.hitmanNext > 0) s.line("Next slot", HoppityEnv.format(f.hitmanNext), Modern.AQUA);
            if (f.hitmanAll > 0) s.line("All slots", HoppityEnv.format(f.hitmanAll), Modern.AQUA);
        }
    }

    // --- the collection ---

    // rabbits found out of total, with a label
    record Tally(String label, int found, int total) {}

    record LocationTally(String rabbit, String location, int found, int required) {}

    // what the collection pages told us so far
    static final class Album {
        int found, total, seen, owned, duplicates, topCount;
        String topName = "";
        final List<Tally> island = new ArrayList<>();
        final List<LocationTally> locations = new ArrayList<>();
        final List<Highlight> highlights = new ArrayList<>();

        static Album live() {
            Album a = new Album();
            a.found = HoppityCollectionStats.foundOnPage;
            a.total = HoppityCollectionStats.totalOnPage;
            Map<String, Integer> counts = HoppityEnv.profile().rabbitCounts;
            a.seen = counts.size();
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                int n = e.getValue();
                if (n > 0) a.owned++;
                if (n > 1) a.duplicates += n - 1;
                if (n > a.topCount) {
                    a.topCount = n;
                    a.topName = e.getKey();
                }
            }
            for (int[] t : HoppityCollectionStats.residentsHere()) a.island.add(new Tally("Residents here", t[0], t[1]));
            for (int[] t : HoppityCollectionStats.hotspotsHere()) a.island.add(new Tally("Hotspots here", t[0], t[1]));
            for (Map.Entry<String, HoppityCollectionStats.LocationRabbit> e : HoppityCollectionStats.locationRabbits().entrySet()) {
                HoppityCollectionStats.LocationRabbit r = e.getValue();
                a.locations.add(new LocationTally(e.getKey(), r.locationName(), r.loreFoundCount(), r.requiredCount()));
            }
            for (Highlight h : Highlight.values()) if (HoppityCollectionStats.wants(h)) a.highlights.add(h);
            return a;
        }

        static Album sample() {
            Album a = new Album();
            a.found = 395;
            a.total = 457;
            a.seen = 412;
            a.owned = 395;
            a.duplicates = 12_408;
            a.topName = "Rabbit Rosemary";
            a.topCount = 214;
            a.island.add(new Tally("Residents here", 3, 5));
            a.island.add(new Tally("Hotspots here", 1, 4));
            a.locations.add(new LocationTally("Dante", "Crimson Isle", 9, 15));
            a.locations.add(new LocationTally("Tom", "Dwarven Mines", 15, 15));
            a.highlights.addAll(List.of(Highlight.MET, Highlight.RESIDENTS, Highlight.HOTSPOTS));
            return a;
        }
    }

    private static String highlightName(Highlight h) {
        return switch (h) {
            case ABI -> "Abi";
            case FACTORY -> "Factory milestone";
            case MET -> "Requirement met";
            case NOT_MET -> "Not met";
            case SHOP -> "Shop milestone";
            case STRAYS -> "Stray";
            case RESIDENTS -> "Resident here";
            case HOTSPOTS -> "Hotspot here";
        };
    }

    static void collection(EventSheet s, Album a) {
        if (a.total > 0) {
            s.hero("Rabbits found", "§a" + sep(a.found) + "§7/§f" + sep(a.total), Modern.GOOD).brief("Found");
            s.line("Left to find", (a.total > a.found ? "§6" : "§a") + sep(a.total - a.found) + "  §7found §f" + share(a.found, a.total)).meter(a.found / (double) a.total, COCOA)
                    .brief("Left").briefValue((a.total > a.found ? "§6" : "§a") + sep(a.total - a.found) + "  §f" + share(a.found, a.total));
        }
        s.section("Seen in the pages");
        s.tiles(EventSheet.tile("Rabbits", sep(a.seen), Modern.INK, "Seen"), EventSheet.tile("Found", sep(a.owned), Modern.GOOD), EventSheet.tile("Duplicates", sep(a.duplicates), GREY, "Dupes"));
        if (a.topCount > 1) s.line("Most found", "§f" + a.topName + " §7x" + sep(a.topCount)).brief("Most");
        if (!a.island.isEmpty()) {
            s.section("This island");
            for (Tally t : a.island) {
                s.line(t.label, EventSheet.ratio(sep(t.found), sep(t.total), t.found / (double) Math.max(1, t.total), 'a', 'f')).meter(t.found / (double) Math.max(1, t.total), Modern.AQUA);
            }
        }
        if (!a.locations.isEmpty()) {
            int done = 0;
            for (LocationTally t : a.locations) if (t.found >= t.required) done++;
            s.section("Location rabbits", EventSheet.ratio(String.valueOf(done), String.valueOf(a.locations.size()), done / (double) a.locations.size(), 'a', 'f'));
            for (LocationTally t : a.locations) {
                double p = t.found / (double) Math.max(1, t.required);
                s.line("§f" + t.rabbit + " §7" + t.location, EventSheet.ratio(sep(t.found), sep(t.required), p, 'e', 'a')).meter(p, Modern.WARN);
            }
        }
        if (!a.highlights.isEmpty()) {
            s.section("Slot colours");
            EventSheet.Grid g = s.grid();
            for (Highlight h : a.highlights) g.key(highlightName(h), h.color).brief(highlightName(h).replace("Requirement", "Req.").replace(" milestone", ""));
        }
    }
}
