// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.muzy.sbc.core.CookieBuff;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.garden.milestones.CropMilestonesApi;
import io.muzy.sbc.garden.milestones.CropMoneyDisplay;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.GardenCropMilestoneDisplay;
import io.muzy.sbc.garden.milestones.GardenCropSpeed;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni FarmingMilestoneCommand, GardenCropTimeCommand, GardenCropsInCommand, shresetcropspeed:
// questions about your crops using your measured speed, answered in chat
public final class CropCommands {
    private CropCommands() {}

    private static String sep(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String output(long crops, boolean time, CropType crop) {
        if (!time) return sep(crops) + " §a" + crop.cropName;
        Integer speed = GardenCropSpeed.getSpeed(crop);
        if (speed == null || speed <= 0) return "§cno speed known yet";
        return SbTime.dur(crops / speed * 1000) + "§a";
    }

    // FarmingMilestoneCommand.onCommand
    static String milestone(CropType crop, Integer current, Integer target, boolean time) {
        if (current == null) {
            Long next = CropMilestonesApi.milestoneNextTierAmount(crop), have = CropMilestonesApi.milestoneProgressToNextTier(crop);
            if (next == null || have == null) return "§cNo crop milestone data! Open /cropmilestones and try again, or say your tiers.";
            return "§7" + output(next - have, time, crop) + " needed to reach the next milestone";
        }
        if (target == null) return "§7" + output(CropMilestonesApi.milestoneTotalCropsForTier(crop, current), time, crop) + " needed for milestone §7" + current;
        if (current >= target) return "§cThe first milestone is the same as or past the second.";
        long crops = CropMilestonesApi.milestoneTotalCropsForTier(crop, target) - CropMilestonesApi.milestoneTotalCropsForTier(crop, current);
        return "§7" + output(crops, time, crop) + " needed for milestone §7" + current + " §a-> §7" + target;
    }

    // GardenCropTimeCommand: time to farm this many of the items whose name starts like that
    static List<String> cropTime(long amount, String search) {
        List<String> out = new ArrayList<>();
        for (var e : CropMoneyDisplay.forms().entrySet()) {
            String name = CropMoneyDisplay.itemName.apply(e.getKey());
            if (!name.toLowerCase(Locale.ROOT).startsWith(search.toLowerCase(Locale.ROOT))) continue;
            CropType crop = (CropType) e.getValue()[0];
            long base = amount * (Integer) e.getValue()[1];
            Integer speed = GardenCropSpeed.getSpeed(crop);
            out.add("§e" + sep(amount) + "x " + name + ((Integer) e.getValue()[1] == 1 ? "" : " §7(§e" + sep(base) + "x " + crop.cropName + "§7)") + (speed == null || speed <= 0 ? " §cNo speed data!" : " §b" + SbTime.dur(base / speed * 1000)));
        }
        return out;
    }

    // GardenCropsInCommand: how many of the matching items you make in a time
    static List<String> cropsIn(long seconds, String search) {
        List<String> out = new ArrayList<>();
        for (var e : CropMoneyDisplay.forms().entrySet()) {
            String name = CropMoneyDisplay.itemName.apply(e.getKey());
            if (!name.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) continue;
            Integer speed = GardenCropSpeed.getSpeed((CropType) e.getValue()[0]);
            out.add(speed == null ? name + " §cNo speed data!" : name + " §b" + sep(seconds * speed / (Integer) e.getValue()[1]) + "x");
        }
        return out;
    }

    private static CropType crop(String name) {
        return CropType.getByNameOrNull(name.replace('_', ' '));
    }

    private static int usage(String what) {
        GardenPack.chat("§cHow to: §e/sbc " + what);
        return 1;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> calc(LiteralArgumentBuilder<FabricClientCommandSource> word, boolean time) {
        String name = time ? "calccroptime" : "calccrop";
        return word.executes(c -> usage(name + " <crop> [from tier] [to tier]  §7for example §e/sbc " + name + " wheat 20 30")).then(argument("crop", StringArgumentType.string())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(CropType.values()).map(t -> t.simpleName), b))
                .executes(c -> say(StringArgumentType.getString(c, "crop"), null, null, time))
                .then(argument("current", IntegerArgumentType.integer(0))
                        .executes(c -> say(StringArgumentType.getString(c, "crop"), IntegerArgumentType.getInteger(c, "current"), null, time))
                        .then(argument("target", IntegerArgumentType.integer(0))
                                .executes(c -> say(StringArgumentType.getString(c, "crop"), IntegerArgumentType.getInteger(c, "current"), IntegerArgumentType.getInteger(c, "target"), time)))));
    }

    private static int say(String name, Integer current, Integer target, boolean time) {
        CropType crop = crop(name);
        GardenPack.chat(crop == null ? "§cNo crop called " + name : milestone(crop, current, target, time));
        return 1;
    }

    private static int list(List<String> lines, String none) {
        if (lines.isEmpty()) GardenPack.chat("§c" + none);
        else for (String l : lines) GardenPack.chat(l);
        return 1;
    }

    public static void register() {
        Safe.commands("CropCommands.commands", (d, r) -> d.register(literal("sbc")
                .then(calc(literal("calccrop"), false))
                .then(calc(literal("calccroptime"), true))
                .then(literal("croptime").executes(c -> usage("croptime <amount> <item>  §7for example §e/sbc croptime 1000 enchanted hay")).then(argument("amount", LongArgumentType.longArg(1)).then(argument("item", StringArgumentType.greedyString())
                        .executes(c -> list(cropTime(LongArgumentType.getLong(c, "amount"), StringArgumentType.getString(c, "item")), "No crop item found.")))))
                .then(literal("cropsin").executes(c -> usage("cropsin <time> <item>  §7for example §e/sbc cropsin 2h wheat")).then(argument("time", StringArgumentType.word()).then(argument("item", StringArgumentType.greedyString()).executes(c -> {
                    long ms = CookieBuff.parse(StringArgumentType.getString(c, "time"));
                    if (ms < 0) return list(List.of(), "Not a valid time.");
                    return list(cropsIn(ms / 1000, StringArgumentType.getString(c, "item")), "No crops found.");
                }))))
                .then(literal("resetcropspeed").executes(c -> {
                    GardenMilestones.profile().cropsPerSecond.clear();
                    GardenCropMilestoneDisplay.timeTillNextCrop.clear();
                    GardenPack.chat("§7Reset the crop speed data.");
                    return 1;
                }))));
        io.muzy.sbc.core.Commands.describe("calccrop", "How many crops between two milestones: <crop> [from] [to]");
        io.muzy.sbc.core.Commands.describe("calccroptime", "How long to farm between two milestones: <crop> [from] [to]");
        io.muzy.sbc.core.Commands.describe("croptime", "How long to farm an amount of an item: <amount> <item>");
        io.muzy.sbc.core.Commands.describe("cropsin", "How many of an item you farm in a time: <time> <item>");
        io.muzy.sbc.core.Commands.describe("resetcropspeed", "Forget the measured crop speeds and best crop times");
    }
}
