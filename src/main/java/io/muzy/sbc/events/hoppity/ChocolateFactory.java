// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni chocolatefactory: CFApi (menu detect, prestige, per second, leaderboard), CFDataLoader
// (numbers from info, prestige, time tower, production, leaderboard, barn, hitman + every upgrade's cost and
// effective cost), ChocolateAmount (live chocolate since last read, time to a goal), CFStats (stat lines),
// CFBarnManager (barn full warning), CFTimeTowerManager (charges, full/expired warnings)
// menu items come as slot -> name, lore with legacy colours
public final class ChocolateFactory {
    static final Patterns.P INVENTORY_NAME = Patterns.p("SH.misc.chocolatefactory.inventory.name");
    static final Patterns.P CF_INVENTORY = Patterns.p("SH.misc.chocolatefactory.inventory.chocolatefactory");
    static final Patterns.P UPGRADE_LORE = Patterns.p("SH.misc.chocolatefactory.item.lore.upgrade");
    static final Patterns.P EMPLOYEE_NAME = Patterns.p("SH.misc.chocolatefactory.item.name.employee");
    static final Patterns.P PER_SECOND = Patterns.p("SH.misc.chocolatefactory.chocolate.persecond");
    static final Patterns.P ALL_TIME = Patterns.p("SH.misc.chocolatefactory.chocolate.alltime");
    static final Patterns.P PRESTIGE_LEVEL = Patterns.p("SH.misc.chocolatefactory.prestige.level");
    static final Patterns.P THIS_PRESTIGE = Patterns.p("SH.misc.chocolatefactory.chocolate.thisprestige");
    static final Patterns.P MAX_CHOCOLATE = Patterns.p("SH.misc.chocolatefactory.chocolate.max");
    static final Patterns.P FOR_PRESTIGE = Patterns.p("SH.misc.chocolatefactory.chocolate.forprestige");
    static final Patterns.P MULTIPLIER = Patterns.p("SH.misc.chocolatefactory.chocolate.multiplier");
    static final Patterns.P LEADERBOARD_PLACE = Patterns.p("SH.misc.chocolatefactory.leaderboard.place");
    static final Patterns.P LEADERBOARD_PERCENTILE = Patterns.p("SH.misc.chocolatefactory.leaderboard.percentile");
    static final Patterns.P BARN_AMOUNT = Patterns.p("SH.misc.chocolatefactory.barn.amount");
    static final Patterns.P TIME_TOWER_AMOUNT = Patterns.p("SH.misc.chocolatefactory.timetower.amount");
    static final Patterns.P TIME_TOWER_EMPTY = Patterns.p("SH.misc.chocolatefactory.timetower.amount.empty");
    static final Patterns.P TIME_TOWER_STATUS = Patterns.p("SH.misc.chocolatefactory.timetower.status");
    static final Patterns.P TIME_TOWER_RECHARGE = Patterns.p("SH.misc.chocolatefactory.timetower.recharge");
    static final Patterns.P RABBIT_AMOUNT = Patterns.p("SH.misc.chocolatefactory.rabbit.amount");
    static final Patterns.P UPGRADE_TIER = Patterns.p("SH.misc.chocolatefactory.upgradetier");
    static final Patterns.P UNEMPLOYED = Patterns.p("SH.misc.chocolatefactory.rabbit.unemployed");
    static final Patterns.P OTHER_UPGRADE = Patterns.p("SH.misc.chocolatefactory.other.upgrade");
    static final Patterns.P HITMAN_EGGS = Patterns.p("SH.misc.chocolatefactory.hitman.availableeggs");
    static final Patterns.P HITMAN_SLOTS = Patterns.p("SH.misc.chocolatefactory.hitman.purchasedslots");
    static final Patterns.P HITMAN_SLOT_CD = Patterns.p("SH.misc.chocolatefactory.hitman.slotcooldown");
    static final Patterns.P HITMAN_ALL_CD = Patterns.p("SH.misc.chocolatefactory.hitman.allslotscooldown");
    static final Patterns.P CLICK_ME = Patterns.p("SH.misc.chocolatefactory.rabbit.clickme");
    static final Patterns.P CLICK_ME_GOLDEN = Patterns.p("SH.misc.chocolatefactory.rabbit.clickme.golden");
    // LoreCostUtils: the chocolate price line of an upgrade, like "§7Cost §6150,000 Chocolate" / "§6150,000 Chocolate"
    private static final java.util.regex.Pattern CHOCOLATE_COST = java.util.regex.Pattern.compile("(?:§7Cost )?§6(?<amount>[\\d,]+) Chocolate");

    // menu item: legacy name + lore
    public record Item(String name, List<String> lore) {}

    // CFUpgrade
    public static final class Upgrade {
        public final int slotIndex, level;
        public final Long price;
        public final Double extraPerSecond, effectiveCost;
        public final boolean isRabbit, isPrestige;
        public final boolean isMaxed;
        public long canAffordAt;

        Upgrade(int slotIndex, int level, Long price, Double extraPerSecond, Double effectiveCost, boolean isRabbit, boolean isPrestige) {
            this.slotIndex = slotIndex;
            this.level = level;
            this.price = price;
            this.extraPerSecond = extraPerSecond;
            this.effectiveCost = effectiveCost;
            this.isRabbit = isRabbit;
            this.isPrestige = isPrestige;
            this.isMaxed = price == null;
            long in = (isPrestige ? Amount.PRESTIGE : Amount.CURRENT).timeUntilGoal(price == null ? 0 : price);
            canAffordAt = in == Long.MAX_VALUE ? HoppityEnv.FAR_FUTURE : HoppityEnv.now() + in;
        }

        // CFUpgrade.formattedTimeUntilGoal
        public String formattedTimeUntilGoal() {
            return (isPrestige ? Amount.PRESTIGE : Amount.CURRENT).formattedTimeUntilGoal(price == null ? 0 : price);
        }

        public boolean canAfford() {
            if (price == null) return false;
            return (isPrestige ? Amount.PRESTIGE : Amount.CURRENT).chocolate() >= price;
        }

        // CFUpgrade.stackTip: level in the rabbit's rank colour
        public String stackTip() {
            if (level == 0) return "";
            if (isMaxed) return "§a✔";
            if (isRabbit) {
                String c = level < 10 ? "" : level < 75 ? "§a" : level < 125 ? "§9" : level < 175 ? "§5" : level < 200 ? "§6" : level < 220 ? "§d" : level < 226 ? "§b" : "§c";
                return c + level;
            }
            return String.valueOf(level);
        }
    }

    // ChocolateAmount
    public enum Amount {
        CURRENT, PRESTIGE, ALL_TIME;

        public long chocolate() {
            Store.HoppityProfile p = HoppityEnv.profile();
            return switch (this) {
                case CURRENT -> p.currentChocolate;
                case PRESTIGE -> p.chocolateThisPrestige;
                case ALL_TIME -> p.chocolateAllTime;
            };
        }

        public String formatted() {
            return HoppityEnv.sep(chocolate() + chocolateSinceUpdate());
        }

        // Long.MAX_VALUE = never
        public long timeUntilGoal(long goal) {
            long updatedAgo = HoppityEnv.passedSince(HoppityEnv.profile().lastDataSave);
            long t = timeUntilNeed(goal - chocolate());
            return t == Long.MAX_VALUE ? t : t - updatedAgo;
        }

        public String formattedTimeUntilGoal(long goal) {
            long t = timeUntilGoal(goal);
            if (t == Long.MAX_VALUE) return "§cNever";
            if (t < 0) return "§aNow";
            return "§b" + HoppityEnv.format(t);
        }
    }

    public static boolean inChocolateFactory, chocolateFactoryPaused;
    public static int currentPrestige = 1;
    public static double chocolatePerSecond;
    public static Integer leaderboardPosition;
    public static Double leaderboardPercentile;
    public static long chocolateForPrestige = 150_000_000L;
    public static List<Upgrade> factoryUpgrades = List.of();
    public static int bestAffordableSlot = -1, bestPossibleSlot = -1;
    static String lastRabbit = "";
    private static boolean sentBarnFullWarning;
    private static long lastTimeTowerWarning = HoppityEnv.FAR_PAST;
    private static boolean warnAboutNewCharge, wasTimeTowerRecentlyActive;

    private ChocolateFactory() {}

    // --- ChocolateAmount ---

    public static long chocolateSinceUpdate() {
        if (isMax()) return 0L;
        long lastUpdate = HoppityEnv.profile().lastDataSave;
        if (lastUpdate <= 0) return 0;
        long seconds = (HoppityEnv.now() - lastUpdate) / 1000;
        return (long) (chocolatePerSecond * seconds);
    }

    public static double averageChocPerSecond(double baseMultiplierIncrease, int rawPerSecondIncrease, boolean includeTower) {
        Store.HoppityProfile p = HoppityEnv.profile();
        double baseMultiplier = p.rawChocolateMultiplier + baseMultiplierIncrease;
        int rawPerSecond = p.rawChocPerSecond + rawPerSecondIncrease;
        double basePerSecond = rawPerSecond * baseMultiplier;
        if (!includeTower) return basePerSecond;
        // timeTowerCooldown: 8h (7 with einstein), in hours
        double timeTowerCooldown = timeTowerChargeDuration() / 3_600_000.0;
        return basePerSecond + (rawPerSecond * .1) / timeTowerCooldown;
    }

    public static void addToAll(long amount) {
        Store.HoppityProfile p = HoppityEnv.profile();
        p.currentChocolate += amount;
        p.chocolateThisPrestige += amount;
        p.chocolateAllTime += amount;
        updateBestUpgrade();
    }

    public static void addToCurrent(long amount) {
        HoppityEnv.profile().currentChocolate += amount;
        updateBestUpgrade();
    }

    private static void updateBestUpgrade() {
        Store.HoppityProfile p = HoppityEnv.profile();
        if (HoppityEnv.isFarPast(p.bestUpgradeAvailableAt) || p.bestUpgradeCost == 0L) return;
        long in = Amount.CURRENT.timeUntilGoal(p.bestUpgradeCost);
        p.bestUpgradeAvailableAt = in == Long.MAX_VALUE ? HoppityEnv.FAR_FUTURE : HoppityEnv.now() + in;
    }

    // --- CFApi ---

    public static boolean isMax() {
        Store.HoppityProfile p = HoppityEnv.profile();
        return p.maxChocolate != 0 && p.maxChocolate == p.currentChocolate;
    }

    public static boolean isMaxPrestige() {
        return currentPrestige >= HoppityData.maxPrestige;
    }

    public static long timeTowerChargeDuration() {
        return (HoppityCollectionStats.hasFoundRabbit("Einstein") ? 7 : 8) * 3_600_000L;
    }

    public static double timeTowerMultiplier() {
        double m = HoppityEnv.profile().timeTowerLevel * 0.1;
        if (HoppityCollectionStats.hasFoundRabbit("Mu")) m += 0.7;
        return m;
    }

    // CFApi.timeUntilNeed in ms, Long.MAX_VALUE when production is 0
    public static long timeUntilNeed(long goal) {
        long needed = goal;
        Store.HoppityProfile p = HoppityEnv.profile();
        double baseMultiplier = p.rawChocolateMultiplier;
        int rawChocolatePerSecond = p.rawChocPerSecond;
        if (rawChocolatePerSecond == 0) return Long.MAX_VALUE;
        long secondsUntilTowerExpires = timeTowerActiveDuration() / 1000;
        double timeTowerChocPerSecond = rawChocolatePerSecond * (baseMultiplier + timeTowerMultiplier());
        double secondsAtRate = needed / timeTowerChocPerSecond;
        if (secondsAtRate < secondsUntilTowerExpires) return (long) (secondsAtRate * 1000);
        needed -= (long) (secondsUntilTowerExpires * timeTowerChocPerSecond);
        double basePerSecond = rawChocolatePerSecond * baseMultiplier;
        return (long) ((needed / basePerSecond + secondsUntilTowerExpires) * 1000);
    }

    // CFApi.getChocolateBuyCost (LoreCostUtils.readLoreCosts for chocolate)
    public static Long getChocolateBuyCost(List<String> lore) {
        for (String l : lore) {
            Matcher m = CHOCOLATE_COST.matcher(l);
            if (m.matches()) return HoppityEnv.parseLong(m.group("amount"));
        }
        return null;
    }

    // CFApi.getNextLevelName
    public static String getNextLevelName(Item stack) {
        for (String line : stack.lore()) {
            Matcher m = UPGRADE_LORE.matcher(line);
            if (!m.matches()) continue;
            boolean isEmployee = stack.lore().contains("§8Employee");
            String upgradeName = null;
            if (!isEmployee) upgradeName = m.group("upgradename");
            else {
                Matcher e = EMPLOYEE_NAME.matcher(stack.name());
                if (e.matches()) upgradeName = e.group("employee");
            }
            String nextLevel = m.group("nextlevel") != null ? m.group("nextlevel") : m.group("nextlevelalt");
            if (upgradeName == null || nextLevel == null) return null;
            return upgradeName + " " + nextLevel;
        }
        return null;
    }

    // --- CFDataLoader ---

    // menu got read, the title says if its the factory, a hoppity menu or something else
    public static void onInventoryOpened(String title, Map<Integer, Item> items) {
        String plain = GameState.strip(title).trim();
        if (INVENTORY_NAME.matcher(plain).matches()) {
            chocolateFactoryPaused = true;
            return;
        }
        inChocolateFactory = CF_INVENTORY.matcher(plain).matches();
        if (!inChocolateFactory) return;
        factoryUpgrades = List.of();
        updateInventoryItems(items);
    }

    public static void onInventoryClose() {
        inChocolateFactory = false;
        clearData();
        sentBarnFullWarning = false;
    }

    public static void onWorldChange() {
        inChocolateFactory = false;
        clearData();
    }

    private static void clearData() {
        chocolateFactoryPaused = false;
        factoryUpgrades = List.of();
        bestAffordableSlot = -1;
        bestPossibleSlot = -1;
    }

    // CFDataLoader.updateInventoryItems: every number the menu shows
    public static void updateInventoryItems(Map<Integer, Item> inventory) {
        if (!inChocolateFactory) return;
        Store.HoppityProfile p = HoppityEnv.profile();
        Item chocolateItem = inventory.get(HoppityData.infoIndex);
        Item prestigeItem = inventory.get(HoppityData.prestigeIndex);
        Item timeTowerItem = inventory.get(HoppityData.timeTowerIndex);
        Item productionInfoItem = inventory.get(HoppityData.productionInfoIndex);
        Item leaderboardItem = inventory.get(HoppityData.leaderboardIndex);
        Item barnItem = inventory.get(HoppityData.barnIndex);
        Item hitmanItem = inventory.get(HoppityData.rabbitHitmanIndex);
        if (chocolateItem == null || prestigeItem == null || timeTowerItem == null || productionInfoItem == null || leaderboardItem == null || barnItem == null || hitmanItem == null) return;

        factoryUpgrades = List.of();
        processChocolateItem(chocolateItem);
        List<Upgrade> list = new ArrayList<>();
        processPrestigeItem(list, prestigeItem);
        processTimeTowerItem(timeTowerItem);
        processProductionItem(productionInfoItem);
        processLeaderboardItem(leaderboardItem);
        updatePosition();
        processBarnItem(barnItem);
        processHitmanItem(hitmanItem);

        p.rawChocPerSecond = (int) (chocolatePerSecond / p.chocolateMultiplier + .01);
        p.lastDataSave = HoppityEnv.now();

        processInventory(list, inventory);
        findBestUpgrades(list);
        factoryUpgrades = list;
    }

    // SkyblockCurrency.CHOCOLATE.readAmountOrNull: "7,645,879,859 Chocolate" in the info item name
    private static final java.util.regex.Pattern CHOCOLATE_NAME = java.util.regex.Pattern.compile("(?<amount>[\\d,]+) Chocolate");

    private static void processChocolateItem(Item item) {
        Store.HoppityProfile p = HoppityEnv.profile();
        Matcher n = CHOCOLATE_NAME.matcher(GameState.strip(item.name()));
        if (n.matches()) p.currentChocolate = HoppityEnv.parseLong(n.group("amount"));
        for (String line : item.lore()) {
            Matcher m = PER_SECOND.matcher(line);
            if (m.matches()) chocolatePerSecond = HoppityEnv.parseDouble(m.group("amount"));
            m = ALL_TIME.matcher(line);
            if (m.matches()) p.chocolateAllTime = HoppityEnv.parseLong(m.group("amount"));
        }
    }

    private static void processPrestigeItem(List<Upgrade> list, Item item) {
        Store.HoppityProfile p = HoppityEnv.profile();
        Matcher m = PRESTIGE_LEVEL.matcher(item.name());
        if (m.matches()) currentPrestige = GameState.roman(m.group("prestige"));
        Long prestigeCost = null;
        for (String line : item.lore()) {
            m = THIS_PRESTIGE.matcher(line);
            if (m.matches()) p.chocolateThisPrestige = HoppityEnv.parseLong(m.group("amount"));
            m = MAX_CHOCOLATE.matcher(line);
            if (m.matches()) p.maxChocolate = HoppityEnv.parseLong(m.group("max"));
            m = FOR_PRESTIGE.matcher(line);
            if (m.matches()) {
                chocolateForPrestige = HoppityEnv.parseLong(m.group("amount"));
                prestigeCost = chocolateForPrestige;
            }
        }
        list.add(new Upgrade(HoppityData.prestigeIndex, currentPrestige, prestigeCost, null, null, false, true));
    }

    private static void processProductionItem(Item item) {
        Store.HoppityProfile p = HoppityEnv.profile();
        for (String line : item.lore()) {
            Matcher m = MULTIPLIER.matcher(line);
            if (!m.matches()) continue;
            double currentMultiplier = HoppityEnv.parseDouble(m.group("amount"));
            p.chocolateMultiplier = currentMultiplier;
            p.rawChocolateMultiplier = timeTowerActive() ? currentMultiplier - p.timeTowerLevel * 0.1 : currentMultiplier;
            break;
        }
    }

    private static void processLeaderboardItem(Item item) {
        leaderboardPosition = null;
        leaderboardPercentile = null;
        for (String line : item.lore()) {
            Matcher m = LEADERBOARD_PLACE.matcher(line);
            if (m.matches()) leaderboardPosition = (int) HoppityEnv.parseLong(m.group("position"));
            m = LEADERBOARD_PERCENTILE.matcher(line);
            if (m.matches()) leaderboardPercentile = HoppityEnv.parseDouble(m.group("percent"));
        }
    }

    // CFApi.updatePosition + HoppityEventSummary.updateCfPosition: leaderboard item vs last time
    private static void updatePosition() {
        HoppitySummary.updatePosition(leaderboardPosition, leaderboardPercentile);
        if (leaderboardPosition == null) return;
        Store.HoppityProfile p = HoppityEnv.profile();
        String leaderboard = "#" + HoppityEnv.sep(leaderboardPosition) + (leaderboardPercentile == null ? "" : " §7Top §a" + leaderboardPercentile + "%");
        if (leaderboard.equals(p.lastLeaderboard)) return;
        if (p.lastLeaderboard != null && p.lastLeaderboardPosition != -1 && p.lastLeaderboardPosition != leaderboardPosition && Hoppity.b(Hoppity.CF, "leaderboardChange", false)) {
            int change = p.lastLeaderboardPosition - leaderboardPosition;
            HoppityEnv.world.richChat("§6CF Leaderboard Change§7: §b" + p.lastLeaderboard + " §c-> §b" + leaderboard, null, null);
            HoppityEnv.world.richChat(" §7Changed by " + (change > 0 ? "§a+" : "§c") + HoppityEnv.sep(change) + (Math.abs(change) == 1 ? " spot" : " spots") + (p.lastLeaderboardTime > 0 ? " §7in §b" + HoppityEnv.format(HoppityEnv.passedSince(p.lastLeaderboardTime), 2) : ""), null, null);
        }
        p.lastLeaderboard = leaderboard;
        p.lastLeaderboardPosition = leaderboardPosition;
        p.lastLeaderboardTime = HoppityEnv.now();
    }

    private static void processBarnItem(Item item) {
        Store.HoppityProfile p = HoppityEnv.profile();
        for (String line : item.lore()) {
            Matcher m = BARN_AMOUNT.matcher(line);
            if (!m.matches()) continue;
            p.currentRabbits = (int) HoppityEnv.parseLong(m.group("rabbits"));
            p.maxRabbits = (int) HoppityEnv.parseLong(m.group("max"));
            trySendBarnFullMessage(true);
            break;
        }
    }

    private static void processTimeTowerItem(Item item) {
        Store.HoppityProfile p = HoppityEnv.profile();
        for (String line : item.lore()) {
            Matcher m = TIME_TOWER_AMOUNT.matcher(line);
            if (m.matches()) {
                p.currentTimeTowerUses = (int) HoppityEnv.parseLong(m.group("uses"));
                p.maxTimeTowerUses = (int) HoppityEnv.parseLong(m.group("max"));
                checkTimeTowerWarning(true);
            }
            if (TIME_TOWER_EMPTY.matcher(line).matches()) {
                p.currentTimeTowerUses = 0;
                p.maxTimeTowerUses = 0;
            }
            m = TIME_TOWER_STATUS.matcher(line);
            if (m.matches()) {
                String activeTime = m.group("acitveTime");
                if (activeTime != null && !activeTime.isEmpty()) {
                    Long d = HoppityEnv.duration(activeTime);
                    p.currentTimeTowerEnds = d == null ? HoppityEnv.FAR_PAST : HoppityEnv.now() + d;
                } else p.currentTimeTowerEnds = HoppityEnv.FAR_PAST;
            }
            m = TIME_TOWER_RECHARGE.matcher(line);
            if (m.matches()) {
                Long d = HoppityEnv.duration(m.group("duration"));
                if (d != null) p.nextTimeTower = HoppityEnv.now() + d;
            }
        }
    }

    private static void processHitmanItem(Item item) {
        Store.HoppityProfile p = HoppityEnv.profile();
        int eggs = 0, slots = 0;
        long single = HoppityEnv.FAR_PAST, all = HoppityEnv.FAR_PAST;
        for (String line : item.lore()) {
            Matcher m = HITMAN_EGGS.matcher(line);
            if (m.matches()) eggs = (int) HoppityEnv.parseLong(m.group("amount"));
            m = HITMAN_SLOT_CD.matcher(line);
            if (m.matches()) {
                Long d = HoppityEnv.duration(m.group("duration"));
                if (d != null) single = HoppityEnv.now() + d;
            }
            m = HITMAN_ALL_CD.matcher(line);
            if (m.matches()) {
                Long d = HoppityEnv.duration(m.group("duration"));
                if (d != null) all = HoppityEnv.now() + d;
            }
            m = HITMAN_SLOTS.matcher(line);
            if (m.matches()) slots = (int) HoppityEnv.parseLong(m.group("amount"));
        }
        p.availableHitmanEggs = eggs;
        p.purchasedHitmanSlots = slots;
        p.hitmanSingleSlotCooldown = single;
        p.hitmanAllSlotsCooldown = all;
    }

    private static void processInventory(List<Upgrade> list, Map<Integer, Item> inventory) {
        for (Map.Entry<Integer, Item> e : inventory.entrySet()) processItem(list, e.getValue(), e.getKey());
    }

    private static void processItem(List<Upgrade> list, Item item, int slotIndex) {
        if (slotIndex == HoppityData.prestigeIndex) return;
        boolean rabbit = HoppityData.rabbitSlots().containsKey(slotIndex), other = HoppityData.otherUpgradeSlots().contains(slotIndex);
        if (!rabbit && !other) return;
        String itemName = GameState.strip(item.name());
        Long upgradeCost = getChocolateBuyCost(item.lore());
        double averageChocolate = Math.round(averageChocPerSecond(0, 0, false) * 100) / 100.0;
        boolean isMaxed = upgradeCost == null;
        if (rabbit) handleRabbitSlot(list, itemName, slotIndex, isMaxed, upgradeCost, averageChocolate);
        else handleOtherUpgradeSlot(list, itemName, slotIndex, isMaxed, upgradeCost, averageChocolate);
    }

    private static void handleRabbitSlot(List<Upgrade> list, String itemName, int slotIndex, boolean isMaxed, Long upgradeCost, double averageChocolate) {
        Integer level = null;
        Matcher m = RABBIT_AMOUNT.matcher(itemName);
        if (m.matches()) level = (int) HoppityEnv.parseLong(m.group("amount"));
        else if (UNEMPLOYED.matcher(itemName).matches()) level = 0;
        if (level == null) return;
        if (isMaxed) {
            list.add(new Upgrade(slotIndex, level, null, null, null, true, false));
            return;
        }
        int chocolateIncrease = HoppityData.rabbitSlots().getOrDefault(slotIndex, 0);
        double newAverage = averageChocPerSecond(0, chocolateIncrease, false);
        addUpgradeToList(list, slotIndex, level, upgradeCost, averageChocolate, newAverage, true);
    }

    private static void handleOtherUpgradeSlot(List<Upgrade> list, String itemName, int slotIndex, boolean isMaxed, Long upgradeCost, double averageChocolate) {
        Integer level = null;
        Matcher m = UPGRADE_TIER.matcher(itemName);
        if (m.matches()) level = GameState.roman(m.group("tier"));
        else if (OTHER_UPGRADE.matcher(itemName).matches()) level = 0;
        if (level == null) return;
        if (slotIndex == HoppityData.timeTowerIndex) HoppityEnv.profile().timeTowerLevel = level;
        if (isMaxed) {
            list.add(new Upgrade(slotIndex, level, null, null, null, false, false));
            return;
        }
        double newAverage;
        if (slotIndex == HoppityData.timeTowerIndex) newAverage = averageChocPerSecond(0, 0, true);
        else if (slotIndex == HoppityData.coachRabbitIndex) newAverage = averageChocPerSecond(0.01, 0, false);
        else {
            list.add(new Upgrade(slotIndex, level, upgradeCost, null, null, false, false));
            return;
        }
        addUpgradeToList(list, slotIndex, level, upgradeCost, averageChocolate, newAverage, false);
    }

    private static void addUpgradeToList(List<Upgrade> list, int slotIndex, int level, long upgradeCost, double averageChocolate, double newAverageChocolate, boolean isRabbit) {
        double extra = Math.round((newAverageChocolate - averageChocolate) * 100) / 100.0;
        double effectiveCost = Math.round(upgradeCost / extra * 100) / 100.0;
        list.add(new Upgrade(slotIndex, level, upgradeCost, extra, effectiveCost, isRabbit, false));
    }

    private static void findBestUpgrades(List<Upgrade> list) {
        Store.HoppityProfile p = HoppityEnv.profile();
        boolean ttAllowed = Hoppity.b(Hoppity.CF, "upgradeWarningTimeTower", false);
        Upgrade best = null, affordable = null;
        for (Upgrade u : list) {
            if (!ttAllowed && u.slotIndex == HoppityData.timeTowerIndex) continue;
            if (u.isMaxed || u.effectiveCost == null) continue;
            if (best == null || u.effectiveCost < best.effectiveCost) best = u;
            if (u.canAfford() && (affordable == null || u.effectiveCost < affordable.effectiveCost)) affordable = u;
        }
        p.bestUpgradeAvailableAt = best == null ? HoppityEnv.FAR_PAST : best.canAffordAt;
        p.bestUpgradeCost = best == null || best.price == null ? 0 : best.price;
        bestPossibleSlot = best == null ? -1 : validUpgradeIndex(best.slotIndex);
        bestAffordableSlot = affordable == null ? -1 : validUpgradeIndex(affordable.slotIndex);
        if (best != null) CFHelpers.checkUpgradeChange(best.slotIndex, best.level);
    }

    // CFUpgrade.getValidUpgradeIndex: prestige, hand cookie, shrine and barn are never "the best upgrade"
    private static int validUpgradeIndex(int slot) {
        if (slot == HoppityData.prestigeIndex || slot == HoppityData.handCookieIndex || slot == HoppityData.shrineIndex || slot == HoppityData.barnIndex) return -1;
        return slot;
    }

    // --- CFBarnManager ---

    public static boolean isBarnFull() {
        Store.HoppityProfile p = HoppityEnv.profile();
        // unlocked barn space already >= total rabbits
        boolean alreadyBigEnough = p.maxRabbits >= HoppityCollectionStats.knownRabbitCount();
        int remainingSpace = p.maxRabbits - p.currentRabbits;
        return remainingSpace <= Hoppity.n(Hoppity.CF, "barnCapacityThreshold", 6) && !alreadyBigEnough;
    }

    public static void trySendBarnFullMessage(boolean inventory) {
        if (!Hoppity.cfEnabled()) return;
        if (Hoppity.n(Hoppity.CF, "barnCapacityThreshold", 6) <= 0) return;
        Store.HoppityProfile p = HoppityEnv.profile();
        if (p.maxRabbits >= HoppityData.maxRabbits) return;
        if (!isBarnFull()) return;
        if (inventory && sentBarnFullWarning) return;
        sentBarnFullWarning = true;
        if (p.maxRabbits == -1) {
            HoppityEnv.world.clickableChat("Open your chocolate factory to see your barn's capacity status!", "/cf", "§eClick to run /cf!");
            return;
        }
        if (Hoppity.b(Hoppity.CF, "rabbitCrushOnlyDuringHoppity", false) && !HoppityTime.isHoppityEvent()) return;
        String fullLevel = p.currentRabbits == p.maxRabbits ? "full" : "almost full";
        HoppityEnv.world.clickableChat("§cYour barn is " + fullLevel + " §7(" + barnStatus() + "). §cUpgrade it so they don't get crushed!", "/cf", "§eClick to run /cf!");
        HoppityEnv.world.playSound("block.note_block.pling", 1f);
    }

    public static String barnStatus() {
        Store.HoppityProfile p = HoppityEnv.profile();
        return p.currentRabbits + "/" + p.maxRabbits + " Rabbits";
    }

    // --- CFTimeTowerManager ---

    // once a sec in skyblock
    public static void timeTowerSecond() {
        Store.HoppityProfile p = HoppityEnv.profile();
        if (HoppityEnv.isInPast(p.currentTimeTowerEnds)) p.currentTimeTowerEnds = HoppityEnv.FAR_PAST;
        checkTimeTowerExpired();
        if (inChocolateFactory) return;
        if (HoppityEnv.isInPast(timeTowerFullTimeMark())) p.currentTimeTowerUses = maxCharges();
        else {
            long nextCharge = p.nextTimeTower;
            while (HoppityEnv.isInPast(nextCharge) && !HoppityEnv.isFarPast(nextCharge)) {
                p.currentTimeTowerUses++;
                nextCharge += timeTowerChargeDuration();
                p.nextTimeTower = nextCharge;
                warnAboutNewCharge = true;
            }
        }
        if (currentCharges() > 0 && currentCharges() < maxCharges()) {
            if (!Hoppity.b(Hoppity.CF, "timeTowerWarning", true) || timeTowerActive()) return;
            if (!warnAboutNewCharge) return;
            HoppityEnv.world.clickableChat("Your Time Tower has an available charge §7(" + timeTowerCharges() + ")§e. Click here to open the Chocolate Factory menu.", "/cf", "§eClick to run /cf!");
            HoppityEnv.world.playSound("block.note_block.pling", 1f);
            lastTimeTowerWarning = HoppityEnv.now();
            warnAboutNewCharge = false;
        }
        checkTimeTowerWarning(false);
    }

    private static void checkTimeTowerExpired() {
        boolean isActive = timeTowerActive();
        if (!isActive && wasTimeTowerRecentlyActive && Hoppity.b(Hoppity.CF, "timeTowerReminder", true) && currentCharges() > 0) {
            int c = currentCharges();
            HoppityEnv.world.clickableChat("§cYour Time Tower just expired and has " + c + (c == 1 ? " charge" : " charges") + " remaining. Click here to open the Chocolate Factory Menu.", "/cf", "§eClick to run /cf!");
            HoppityEnv.world.playSound("block.note_block.pling", 1f);
        }
        wasTimeTowerRecentlyActive = isActive;
    }

    public static void checkTimeTowerWarning(boolean inInventory) {
        if (!Hoppity.cfEnabled()) return;
        if (!Hoppity.b(Hoppity.CF, "timeTowerWarning", true)) return;
        if (!timeTowerFull()) return;
        if (HoppityEnv.world.isBusy()) return;
        if (maxCharges() == 0) return;
        long separation = inInventory ? 30_000 : 5 * 60_000;
        if (HoppityEnv.passedSince(lastTimeTowerWarning) < separation) return;
        HoppityEnv.world.clickableChat("§cYour Time Tower is full §7(" + timeTowerCharges() + ")§c, Use one to avoid wasting time tower usages! Click here to open the Chocolate Factory menu.", "/cf", "§eClick to run /cf!");
        HoppityEnv.world.playSound("block.note_block.pling", 1f);
        lastTimeTowerWarning = HoppityEnv.now();
    }

    public static String timeTowerCharges() {
        return currentCharges() + "/" + maxCharges() + " Charges";
    }

    public static int currentCharges() {
        return HoppityEnv.profile().currentTimeTowerUses;
    }

    private static int maxCharges() {
        return HoppityEnv.profile().maxTimeTowerUses;
    }

    public static boolean timeTowerFull() {
        return currentCharges() >= maxCharges();
    }

    public static boolean timeTowerActive() {
        Store.HoppityProfile p = HoppityEnv.profile();
        return p.currentTimeTowerEnds > p.lastDataSave;
    }

    public static long timeTowerFullTimeMark() {
        Store.HoppityProfile p = HoppityEnv.profile();
        if (timeTowerFull()) return HoppityEnv.FAR_PAST;
        int remainingAfter = p.maxTimeTowerUses - (p.currentTimeTowerUses + 1);
        return p.nextTimeTower + timeTowerChargeDuration() * remainingAfter;
    }

    public static long timeTowerActiveDuration() {
        if (!timeTowerActive()) return 0;
        Store.HoppityProfile p = HoppityEnv.profile();
        return p.currentTimeTowerEnds - p.lastDataSave;
    }

    // --- CFStats ---

    static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    // CFStats.updateDisplay: stat lines in skyhanni's default order
    public static List<String> statsLines() {
        Store.HoppityProfile p = HoppityEnv.profile();
        List<String> out = new ArrayList<>();
        out.add("§6§lChocolate Factory " + roman(currentPrestige));
        out.add("§eCurrent Chocolate: §6" + Amount.CURRENT.formatted() + (isMax() ? " §cMax!" : ""));
        out.add("§eThis Prestige: §6" + Amount.PRESTIGE.formatted());
        out.add("§eAll-time: §6" + Amount.ALL_TIME.formatted());
        out.add("§ePer Second: §6" + String.format(java.util.Locale.ROOT, "%,.2f", chocolatePerSecond));
        out.add("§eChocolate Multiplier: §6" + p.chocolateMultiplier);
        out.add("§eBarn: §6" + barnStatus());
        String positionText = leaderboardPosition == null ? "???" : HoppityEnv.sep(leaderboardPosition);
        String percentileText = leaderboardPercentile == null ? "" : "§7Top §a" + leaderboardPercentile + "%";
        out.add("§ePosition: §b#" + positionText + " " + percentileText);
        out.add("§eTime Tower: §6" + (timeTowerActive() ? "§d§lActive" : "§6" + timeTowerCharges()));
        if (timeTowerFull()) out.add("§eFull Tower Charges: §a§lNow");
        else out.add("§eFull Tower Charges: §b" + HoppityEnv.format(timeTowerFullTimeMark() - HoppityEnv.now()));
        out.add("§eRaw Per Second: §6" + HoppityEnv.sep(p.rawChocPerSecond));
        long allTime = Amount.ALL_TIME.chocolate();
        long nextMilestone = HoppityData.nextMilestone(allTime);
        long untilMilestone = nextMilestone - allTime;
        long untilPrestige = chocolateForPrestige - Amount.PRESTIGE.chocolate();
        if (!isMaxPrestige()) {
            out.add("§eTime To Prestige: " + Amount.PRESTIGE.formattedTimeUntilGoal(chocolateForPrestige));
            out.add("§eChocolate To Prestige: §6" + (untilPrestige <= 0 ? "§aPrestige Available" : HoppityEnv.sep(untilPrestige)));
        } else if (untilMilestone >= 0) {
            out.add("§eTime To Next Milestone: " + Amount.ALL_TIME.formattedTimeUntilGoal(nextMilestone));
            out.add("§eChocolate To Next Milestone: §6" + HoppityEnv.sep(untilMilestone));
        }
        out.add("§eBest Upgrade: " + Amount.CURRENT.formattedTimeUntilGoal(p.bestUpgradeCost));
        return out;
    }

    public static void reset() {
        inChocolateFactory = chocolateFactoryPaused = false;
        currentPrestige = 1;
        chocolatePerSecond = 0;
        leaderboardPosition = null;
        leaderboardPercentile = null;
        chocolateForPrestige = 150_000_000L;
        factoryUpgrades = List.of();
        bestAffordableSlot = bestPossibleSlot = -1;
        lastRabbit = "";
        sentBarnFullWarning = false;
        lastTimeTowerWarning = HoppityEnv.FAR_PAST;
        warnAboutNewCharge = wasTimeTowerRecentlyActive = false;
    }
}
