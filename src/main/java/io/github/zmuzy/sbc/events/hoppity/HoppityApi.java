// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.hoppity;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni HoppityApi: every rabbit find is 3 chat lines (egg/hitman/bought/visitor line, "You found X
// (RARITY)!", "NEW RABBIT!"/"DUPLICATE RABBIT!"), once all 3 are in a RabbitFoundEvent fires
// here it feeds the hoppity panel counts (eggs per meal, rabbits per rarity, new, dupes, chocolate) and the
// barn (CFBarnManager: currentRabbits++ on new, dupe chocolate into totals)
// MythicRabbitPetWarning lives here too
public final class HoppityApi {
    static final Patterns.P MILESTONE_NAME = Patterns.p("SH.misc.chocolatefactory.rabbit.milestone");
    static final Patterns.P SIDE_DISH_NAME = Patterns.p("SH.misc.chocolatefactory.rabbit.sidedish");
    static final Patterns.P ALL_TIME_LORE = Patterns.p("SH.misc.chocolatefactory.milestone.alltime");
    static final Patterns.P SHOP_LORE = Patterns.p("SH.misc.chocolatefactory.milestone.shop");
    static final Patterns.P CLAIMABLE = Patterns.p("SH.misc.chocolatefactory.milestone.claimable");
    static final Patterns.P RABBIT_CRUSHED = Patterns.p("SH.misc.chocolatefactory.rabbit.crushed");
    static final Patterns.P RABBIT_THE_FISH = Patterns.p("SH.misc.chocolatefactory.rabbit.thefish");

    public static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE");

    // HoppityStateDataSet
    public static final class DataSet {
        public final List<String> hoppityMessages = new ArrayList<>();
        public boolean duplicate;
        public String lastRarity;
        public String lastName = "";
        public String lastProfit = "";
        public HoppityEggType lastMeal;
        public Long lastDuplicateAmount;

        DataSet copy() {
            DataSet d = new DataSet();
            d.hoppityMessages.addAll(hoppityMessages);
            d.duplicate = duplicate;
            d.lastRarity = lastRarity;
            d.lastName = lastName;
            d.lastProfit = lastProfit;
            d.lastMeal = lastMeal;
            d.lastDuplicateAmount = lastDuplicateAmount;
            return d;
        }

        void reset() {
            hoppityMessages.clear();
            duplicate = false;
            lastRarity = null;
            lastName = "";
            lastProfit = "";
            lastMeal = null;
            lastDuplicateAmount = null;
        }
    }

    private static final DataSet dataSet = new DataSet();
    private static boolean checkNextInvOpen;
    private static Long lastHoppityCallAccept;

    // --- session counts for the panel (skyhanni HoppityEventSummary keeps these per event year) ---
    public static final Map<HoppityEggType, Integer> foundByType = new LinkedHashMap<>();
    public static final Map<String, Integer> rabbitsByRarity = new LinkedHashMap<>();
    public static int newRabbits, duplicateRabbits;
    public static long duplicateChocolate, strayChocolate;
    public static long lastEggSpawnAt;
    public static String lastEggSpawnMeal = "";

    // --- MythicRabbitPetWarning ---
    private static long lastPetCheck = HoppityEnv.FAR_PAST;

    private HoppityApi() {}

    public static boolean onHoppityIsland() {
        return HoppityEnv.world.inSkyBlock() && HoppityData.eggIslands().contains(HoppityEnv.world.island());
    }

    // LorenzRarity by the rabbit name's colour code (first 2 chars)
    public static String rarityByRabbit(String rabbit) {
        if (rabbit == null || rabbit.length() < 2) return null;
        return switch (rabbit.substring(0, 2)) {
            case "§f" -> "COMMON";
            case "§a" -> "UNCOMMON";
            case "§9" -> "RARE";
            case "§5" -> "EPIC";
            case "§6" -> "LEGENDARY";
            case "§d" -> "MYTHIC";
            case "§b" -> "DIVINE";
            default -> null;
        };
    }

    private static HoppityEggType getBoughtType() {
        return lastHoppityCallAccept != null ? HoppityEggType.BOUGHT_ABIPHONE : HoppityEggType.BOUGHT;
    }

    // MessageSendToServerEvent: "/selectnpcoption hoppity r_2_1" picks up hoppity's call -> next shop is the abiphone one
    public static void onCommandSend(String command) {
        if (Patterns.get("SH.misc.chocolatefactory.hoppity.call.pickup.outgoing").matcher(command).matches()) checkNextInvOpen = true;
    }

    public static void onInventoryOpened(String title) {
        if (!checkNextInvOpen) return;
        checkNextInvOpen = false;
        if (Patterns.get("SH.misc.chocolatefactory.hoppity.inventory").matcher(GameState.strip(title)).matches()) lastHoppityCallAccept = HoppityEnv.now();
    }

    public static void onInventoryClose() {
        processedStraySlots.clear();
        if (lastHoppityCallAccept == null) return;
        Hoppity.later(1000, () -> lastHoppityCallAccept = null);
    }

    private static boolean postEggFound(HoppityEggType type, String message, String note) {
        return onEggFound(type, message, note);
    }

    // HoppityApi.onSlotClick: rabbits you get by clicking in the factory menus
    public static void onSlotClick(String title, String name, List<String> lore) {
        if (!Patterns.get("SH.misc.chocolatefactory.inventory.misc").matcher(title).matches() || name.isEmpty() || lore.isEmpty()) return;
        if (SIDE_DISH_NAME.matcher(name).matches()) onEggFound(HoppityEggType.SIDE_DISH, null, null);
        if (!MILESTONE_NAME.matcher(name).matches() || !anyMatches(CLAIMABLE, lore)) return;
        if (anyMatches(ALL_TIME_LORE, lore)) onEggFound(HoppityEggType.CHOCOLATE_FACTORY_MILESTONE, null, null);
        if (anyMatches(SHOP_LORE, lore)) onEggFound(HoppityEggType.CHOCOLATE_SHOP_MILESTONE, null, null);
    }

    private static boolean anyMatches(Patterns.P p, List<String> lines) {
        for (String l : lines) if (p.matcher(l).matches()) return true;
        return false;
    }

    private static final Map<Integer, String> processedStraySlots = new LinkedHashMap<>();

    // HoppityApi.onInventoryUpdated: fish the rabbit and el dorado are real rabbits among the strays
    public static void onInventoryUpdated(Map<Integer, ChocolateFactory.Item> items) {
        processedStraySlots.entrySet().removeIf(e -> !items.containsKey(e.getKey()) || !items.get(e.getKey()).name().equals(e.getValue()));
        if (!ChocolateFactory.inChocolateFactory) return;
        for (Map.Entry<Integer, ChocolateFactory.Item> e : items.entrySet()) {
            int slot = e.getKey();
            ChocolateFactory.Item it = e.getValue();
            if (slot == 13 || slot > 26 || it.name().isEmpty() || it.lore().isEmpty() || processedStraySlots.containsKey(slot)) continue;
            String single = String.join(" ", it.lore().stream().filter(l -> !l.isEmpty()).toList());
            Matcher caught = Patterns.get("SH.misc.chocolatefactory.stray.caught").matcher(it.name());
            if (caught.find()) {
                processedStraySlots.put(slot, it.name());
                if ("Fish the Rabbit".equals(GameState.strip(caught.group("name")))) strayRabbit("§9Fish the Rabbit", "RARE", anyMatches(Patterns.p("SH.misc.chocolatefactory.stray.pseudoduplicate"), it.lore()));
            }
            if (Patterns.get("SH.misc.chocolatefactory.stray.dorado").matcher(single).matches() && !anyMatches(Patterns.p("SH.misc.chocolatefactory.stray.doradoescape"), it.lore())) {
                strayRabbit("§6El Dorado", "LEGENDARY", anyMatches(Patterns.p("SH.misc.chocolatefactory.stray.doradoduplicate"), it.lore()));
            }
        }
    }

    private static void strayRabbit(String name, String rarity, boolean duplicate) {
        dataSet.lastName = name;
        dataSet.lastRarity = rarity;
        dataSet.duplicate = duplicate;
        onEggFound(HoppityEggType.STRAY, null, null);
    }

    // EggFoundEvent (HIGHEST priority handler + listeners)
    public static boolean onEggFound(HoppityEggType type, String chatMessage, String note) {
        dataSet.lastMeal = type;
        String synthetic = switch (type) {
            case SIDE_DISH -> "§d§lHOPPITY'S HUNT §r§dYou found a §r§6§lSide Dish §r§6Egg §r§din the Chocolate Factory§r§d!";
            case CHOCOLATE_FACTORY_MILESTONE -> "§d§lHOPPITY'S HUNT §r§dYou claimed a §r§6§lChocolate Milestone Rabbit §r§din the Chocolate Factory§r§d!";
            case CHOCOLATE_SHOP_MILESTONE -> "§d§lHOPPITY'S HUNT §r§dYou claimed a §r§6§lShop Milestone Rabbit §r§din the Chocolate Factory§r§d!";
            case STRAY -> "§d§lHOPPITY'S HUNT §r§dYou found a §r§aStray Rabbit§r§d!";
            default -> null;
        };
        if (synthetic != null) dataSet.hoppityMessages.add(synthetic);
        foundByType.merge(type, 1, Integer::sum);
        HoppityEggsManager.onEggFound(type, note);
        HoppityEggLocator.onEggFound(type);
        CFStray.onEggFound(type);
        return attemptFireRabbitFound(chatMessage, null);
    }

    // HoppityApi.onChat (skyblock, HIGH priority), formatted line. true = compact chat takes it
    public static boolean onChat(String message, String plain) {
        Matcher m = HoppityEggsManager.EGG_FOUND.matcher(message);
        if (m.matches()) {
            dataSet.reset();
            HoppityEggType type = HoppityEggType.byMeal(m.group("meal"));
            return type != null && postEggFound(type, message, GameState.strip(m.group("note")));
        }
        if ("GARDEN".equals(HoppityEnv.world.island()) && HoppityEggsManager.VISITOR_ACCEPTED.matcher(plain).matches()) {
            dataSet.reset();
            return postEggFound(HoppityEggType.VISITOR, message, null);
        }
        if (HoppityEggsManager.HITMAN_EGG_FOUND.matcher(message).matches()) {
            dataSet.reset();
            return postEggFound(HoppityEggType.HITMAN, message, null);
        }
        m = HoppityEggsManager.EGG_BOUGHT.matcher(message);
        if (m.matches()) {
            if (!m.group("rabbitname").equals(dataSet.lastName)) return false;
            return postEggFound(getBoughtType(), message, null);
        }
        m = HoppityEggsManager.RABBIT_FOUND.matcher(message);
        if (m.matches()) {
            dataSet.lastName = m.group("name");
            ChocolateFactory.lastRabbit = dataSet.lastName;
            dataSet.lastRarity = m.group("rarity");
            return attemptFireRabbitFound(message, null);
        }
        m = HoppityEggsManager.NEW_RABBIT.matcher(message);
        if (m.matches()) {
            // CFBarnManager.onChat: one more rabbit in the barn, share prompt
            HoppityEnv.profile().currentRabbits += 1;
            ChocolateFactory.trySendBarnFullMessage(false);
            HoppityEggsManager.shareWaypointPrompt();
            newRabbits++;
            String other = m.group("other");
            if (other != null) {
                dataSet.lastProfit = other;
                return attemptFireRabbitFound(message, null);
            }
            String chocolate = m.group("chocolate");
            String perSecond = m.group("perSecond");
            dataSet.lastProfit = chocolate != null ? "§6+" + chocolate + " §7and §6+" + perSecond + "x c/s!" : "§6+" + perSecond + "x c/s!";
            return attemptFireRabbitFound(message, null);
        }
        m = HoppityEggsManager.DUPLICATE_RABBIT.matcher(message);
        if (m.matches()) {
            HoppityEggsManager.shareWaypointPrompt();
            long amount = HoppityEnv.parseLong(m.group("amount"));
            duplicateRabbits++;
            duplicateChocolate += amount;
            ChocolateFactory.addToAll(amount);
            // CFBarnManager.duplicateFoundMessage: what the chocolate is worth in production, if the short line doesnt say it
            if (Hoppity.b(Hoppity.CF, "showDuplicateTime", true) && !HoppityCompactChat.on()) {
                long time = ChocolateFactory.timeUntilNeed(amount);
                if (time != Long.MAX_VALUE) Hoppity.later(0, () -> HoppityEnv.world.chat("§7(§a+§b" + HoppityEnv.format(time, 2) + " §aof production§7)"));
            }
            return attemptFireRabbitFound(message, amount);
        }
        m = RABBIT_CRUSHED.matcher(message);
        if (m.matches()) {
            HoppityEggsManager.shareWaypointPrompt();
            ChocolateFactory.addToAll(HoppityEnv.parseLong(m.group("amount")));
        }
        return false;
    }

    static boolean attemptFireRabbitFound(String message, Long lastDuplicateAmount) {
        if (lastDuplicateAmount != null) {
            dataSet.lastDuplicateAmount = lastDuplicateAmount;
            dataSet.duplicate = true;
        }
        if (message != null) dataSet.hoppityMessages.add(message);
        boolean hide = HoppityCompactChat.compactChat(dataSet) && message != null;
        // shouldnt happen but failsafe
        if (dataSet.lastMeal == null) return hide;
        if (dataSet.hoppityMessages.size() != 3) return hide;
        onRabbitFound(dataSet);
        dataSet.reset();
        return hide;
    }

    // dupe line with what the settings add, or null if the short line replaces it
    public static String changedDuplicate(String message) {
        if (HoppityCompactChat.on()) return null;
        Matcher m = HoppityEggsManager.DUPLICATE_RABBIT.matcher(message);
        return m.matches() ? HoppityCompactChat.changedDuplicateLine(message, ChocolateFactory.lastRabbit, m.group("amount")) : null;
    }

    // RabbitFoundEvent: the whole find is known
    private static void onRabbitFound(DataSet d) {
        if (d.lastRarity != null) rabbitsByRarity.merge(d.lastRarity, 1, Integer::sum);
        if (!d.lastName.isEmpty()) {
            String clean = GameState.strip(d.lastName);
            HoppityEnv.profile().rabbitCounts.merge(clean, 1, Integer::sum);
        }
        HoppitySummary.onRabbitFound(d);
    }

    // in-flight data set, for tests
    static DataSet dataSet() {
        return dataSet;
    }

    // --- MythicRabbitPetWarning ---
    public static void mythicRabbitCheck() {
        if (!Hoppity.b(Hoppity.EGGS, "petWarning", true)) return;
        if (HoppityEnv.passedSince(lastPetCheck) < 30_000) return;
        if (!HoppityEnv.world.hasMythicRabbitPet()) {
            lastPetCheck = HoppityEnv.now();
            HoppityEnv.world.chat("Use a §dMythic Rabbit Pet §rfor more chocolate!");
            HoppityEnv.world.title("§cNo Rabbit Pet!");
        }
    }

    public static void reset() {
        dataSet.reset();
        processedStraySlots.clear();
        checkNextInvOpen = false;
        lastHoppityCallAccept = null;
        foundByType.clear();
        rabbitsByRarity.clear();
        newRabbits = duplicateRabbits = 0;
        duplicateChocolate = strayChocolate = 0;
        lastEggSpawnAt = 0;
        lastEggSpawnMeal = "";
        lastPetCheck = HoppityEnv.FAR_PAST;
    }
}
