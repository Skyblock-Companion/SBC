// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.hoppity;

import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.item.enchants.EnchantParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// from skyhanni HoppityEggsCompactChat: the 3 lines of a find become one with the originals on hover, a
// hitman Claim All over the threshold becomes one summary
// also the 2 changes CFBarnManager makes to hypixel's dupe line when chat isnt compacted: the dupe number and
// time tower colour on its chocolate
public final class HoppityCompactChat {
    public static final String ID = "hoppity_compact_chat";

    private static Integer lockedHitmanClaimCount;
    private static final List<HoppityApi.DataSet> hitmanStash = new ArrayList<>();

    private HoppityCompactChat() {}

    public static boolean on() {
        Feature f = Features.get(ID);
        return f != null && f.enabled();
    }

    private static boolean opt(String key) {
        return Hoppity.b(ID, key, false);
    }

    // compactChat: line goes when this is on, the third one sends the short form
    static boolean compactChat(HoppityApi.DataSet live) {
        if (!on()) return false;
        if (live.hoppityMessages.size() == 3) sendCompact(live.copy());
        return true;
    }

    private static void sendCompact(HoppityApi.DataSet d) {
        if (d.lastMeal == HoppityEggType.HITMAN) compactMultipleFinds(d);
        else sendSingularFind(d);
    }

    private static void sendSingularFind(HoppityApi.DataSet d) {
        String hover = String.join("\n", d.hoppityMessages);
        if (d.lastMeal != null && d.lastMeal.isResetting() && Hoppity.b(Hoppity.EGGS, "shared", true)) {
            HoppityEnv.world.richChat(createCompactMessage(d, true), hover + "\n§eClick here to share the location of this chocolate egg with the server!", "/sbc shareegg");
        } else HoppityEnv.world.richChat(createCompactMessage(d, true), hover, null);
        lockedHitmanClaimCount = null;
    }

    private static int expectedHitmanFinds() {
        return lockedHitmanClaimCount != null ? lockedHitmanClaimCount : HoppityEnv.profile().availableHitmanEggs;
    }

    private static void compactMultipleFinds(HoppityApi.DataSet d) {
        int claiming = expectedHitmanFinds();
        if (claiming <= Hoppity.n(ID, "hitman_threshold", 29)) {
            sendSingularFind(d);
            return;
        }
        lockedHitmanClaimCount = claiming;
        hitmanStash.add(d);
        int now = hitmanStash.size();
        if (now >= claiming) sendHitmanSummary();
        // if what we expected is wrong the finds dont sit here forever
        else Hoppity.later(2000, () -> {
            if (hitmanStash.size() == now) sendHitmanSummary();
        });
    }

    private static void sendHitmanSummary() {
        if (hitmanStash.isEmpty()) return;
        List<String> lines = new ArrayList<>();
        lines.add("§c§lHitman Summary");
        lines.add("");
        List<HoppityApi.DataSet> fresh = hitmanStash.stream().filter(s -> !s.duplicate).toList();
        List<HoppityApi.DataSet> dupes = hitmanStash.stream().filter(s -> s.duplicate).toList();
        if (!fresh.isEmpty()) {
            rabbits(lines, hitmanStash, "Total Hitman");
            lines.add("");
            rabbits(lines, fresh, "New");
            lines.add("");
        }
        if (!dupes.isEmpty()) {
            rabbits(lines, dupes, "Duplicate");
            long chocolate = 0;
            for (HoppityApi.DataSet s : dupes) chocolate += s.lastDuplicateAmount == null ? 0 : s.lastDuplicateAmount;
            lines.add(" §6+" + HoppityEnv.sep(chocolate) + " §6Chocolate§7" + extraTime(chocolate));
        }
        List<HoppityApi.DataSet> sorted = new ArrayList<>(hitmanStash);
        sorted.sort(Comparator.comparingInt(s -> s.duplicate ? 1 : 0));
        List<String> hover = new ArrayList<>();
        for (HoppityApi.DataSet s : sorted) hover.add(createCompactMessage(s, false));
        HoppityEnv.world.richChat(String.join("\n", lines), String.join("\n", hover), null);
        hitmanStash.clear();
        lockedHitmanClaimCount = null;
    }

    private static void rabbits(List<String> lines, List<HoppityApi.DataSet> sets, String name) {
        Map<String, Integer> byRarity = new LinkedHashMap<>();
        for (HoppityApi.DataSet s : sets) if (s.lastRarity != null) byRarity.merge(s.lastRarity, 1, Integer::sum);
        lines.addAll(HoppitySummary.rabbitsFormat(byRarity, name, 0, 0, 0));
    }

    private static String extraTime(Long amount) {
        if (amount == null) return "?";
        if (!Hoppity.b(Hoppity.CF, "showDuplicateTime", true)) return "";
        long extra = ChocolateFactory.timeUntilNeed(amount);
        return extra == Long.MAX_VALUE ? "" : ", §a+§b" + HoppityEnv.format(extra, 2) + "§7";
    }

    private static String rarityFormat(HoppityApi.DataSet d) {
        String wanted = Hoppity.text(ID, "rarity", "New");
        boolean show = d.duplicate ? wanted.equals("Both") || wanted.equals("Duplicates") : wanted.equals("Both") || wanted.equals("New");
        if (!show) return "";
        return (d.lastRarity == null ? "§C§L???" : rarityCode(d.lastRarity) + "§l" + d.lastRarity) + " ";
    }

    static String rarityCode(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "DIVINE" -> "§b";
            default -> "§f";
        };
    }

    // createCompactMessage
    static String createCompactMessage(HoppityApi.DataSet d, boolean withMeal) {
        String meal = "";
        if (withMeal && d.lastMeal != null) meal = d.lastMeal.coloredName() + (d.lastMeal.isResetting() ? " Egg! " : " Rabbit! ");
        String name = d.lastName.isEmpty() ? "§C§L???" : d.lastName;
        String rarity = rarityFormat(d);
        if (d.duplicate) {
            String amount = d.lastDuplicateAmount == null ? "?" : EnchantParser.shortFormat(d.lastDuplicateAmount);
            String number = "";
            if (opt("dupe_number")) {
                int count = HoppityCollectionStats.getRabbitCount(d.lastName);
                if (count > 0) number = " §7(§b#" + count + "§7)";
            }
            String color = opt("recolor_tower") && ChocolateFactory.timeTowerActive() ? "§d" : "§6";
            return meal + "§7Duplicate " + rarity + name + number + " §7(§6+" + color + amount + " §6Chocolate§7" + extraTime(d.lastDuplicateAmount) + ")";
        }
        return meal + "§d§lNEW " + rarity + name + " §7(" + d.lastProfit + "§7)";
    }

    // CFBarnManager's SkyHanniChatEvent.Modify: hypixel's dupe line when not compacted, null = stays as it came
    static String changedDuplicateLine(String message, String lastRabbit, String rawAmount) {
        String changed = message;
        if (opt("dupe_number")) {
            int count = HoppityCollectionStats.getRabbitCount(lastRabbit);
            if (count > 0) changed = changed.replace("§7§lDUPLICATE RABBIT!", "§7§lDUPLICATE RABBIT! §7(Duplicate §b#" + count + "§7)§r");
        }
        if (opt("recolor_tower") && ChocolateFactory.timeTowerActive()) changed = changed.replace("§6+" + rawAmount + " Chocolate", "§6+§d" + rawAmount + " §6Chocolate");
        return changed.equals(message) ? null : changed;
    }

    public static void reset() {
        lockedHitmanClaimCount = null;
        hitmanStash.clear();
    }

    static Feature feature() {
        return new Feature(ID, "Events", "Compact Hoppity chat", "The three lines of a rabbit find become one, with the original lines on hover and the egg's share link on click. Big Hitman claims become one summary.", "SkyHanni: Compact Chat")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .option(dev.zmuzy.sbc.feature.Option.choice("hoppity_compact_chat.rarity", "Show the rarity", "On which finds the short line names it.", "New", "New", "Duplicates", "Both", "None"))
                .option(dev.zmuzy.sbc.feature.Option.integer("hoppity_compact_chat.hitman_threshold", "Hitman summary above", "A Claim All of more eggs than this becomes one summary; 29 is never.", 29, 0, 29))
                .option(dev.zmuzy.sbc.feature.Option.bool("hoppity_compact_chat.dupe_number", "Number of the duplicate", "How many of this rabbit you found. Also on Hypixel's line when this feature is off.", false))
                .option(dev.zmuzy.sbc.feature.Option.bool("hoppity_compact_chat.recolor_tower", "Time Tower chocolate in pink", "Duplicate chocolate that came while the Time Tower ran. Also on Hypixel's line when this feature is off.", false));
    }
}
