// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Commands;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Feature;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyblocker SpeedPresets: names for hypixels /setmaxspeed. /setmaxspeed crops sends the number, /sbc
// speed lists, runs or edits them
public final class SpeedPresets {
    public static final String ID = "speed_presets";
    private static final Pattern COMMAND = Pattern.compile("^setmaxspeed\\s([a-zA-Z]\\w*)$");
    private SpeedPresets() {}

    // skyblocker createDefaultMap
    static Map<String, Integer> defaults() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("default", 100);
        map.put("crops", 93);
        map.put("cocoa", 155);
        map.put("mushroom", 233);
        map.put("cane", 327);
        map.put("squash", 327);
        map.put("cactus", 464);
        return map;
    }

    public static Map<String, Integer> presets() {
        Saved.Data d = Saved.get();
        if (d.speedPresets == null) d.speedPresets = defaults();
        return d.speedPresets;
    }

    static String modify(String command, Map<String, Integer> presets) {
        Matcher m = COMMAND.matcher(command);
        if (!m.matches()) return command;
        Integer speed = presets.get(m.group(1));
        return speed == null ? command : "setmaxspeed " + speed;
    }

    private static void say(CommandContext<FabricClientCommandSource> c, String text) {
        c.getSource().sendFeedback(Component.literal("§b[SBC] §f" + text));
    }

    public static void register() {
        Features.add(new Feature(ID, "Commands", "Speed presets", "/setmaxspeed crops sends the speed you saved under that name. /sbc speed lists them, runs one and changes them.", "Skyblocker: Speed Presets")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Commands.describe("speed", "Speed presets: /sbc speed [name or number], set <name> <speed>, remove <name>");
        Safe.modifyCommand("SpeedPresets.modifyCommand", command -> Features.on(ID) ? modify(command, presets()) : command);
        Safe.commands("SpeedPresets.commands", (d, r) -> {
            d.register(literal("setmaxspeed").executes(c -> dev.zmuzy.sbc.core.ServerCommand.toServer()).then(argument("preset", StringArgumentType.greedyString()).suggests((c, b) -> {
                if (Features.on(ID)) for (String n : presets().keySet()) if (n.toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(n);
                return b.buildFuture();
            }).executes(c -> dev.zmuzy.sbc.core.ServerCommand.toServer())));
            d.register(literal("sbc").then(literal("speed").executes(c -> {
                presets().forEach((name, speed) -> say(c, "§e" + name + " §7" + speed));
                return 1;
            }).then(literal("set").then(argument("name", StringArgumentType.word()).then(argument("speed", IntegerArgumentType.integer(0)).executes(c -> {
                presets().put(StringArgumentType.getString(c, "name"), IntegerArgumentType.getInteger(c, "speed"));
                Saved.save();
                say(c, "Saved.");
                return 1;
            })))).then(literal("remove").then(argument("name", StringArgumentType.word()).suggests((c, b) -> {
                for (String n : presets().keySet()) b.suggest(n);
                return b.buildFuture();
            }).executes(c -> {
                boolean had = presets().remove(StringArgumentType.getString(c, "name")) != null;
                Saved.save();
                say(c, had ? "Removed." : "§cNo such preset.");
                return 1;
            }))).then(argument("preset", dev.zmuzy.sbc.core.WordArgument.except("set", "remove")).suggests((c, b) -> {
                for (String n : presets().keySet()) if (n.toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(n);
                return b.buildFuture();
            }).executes(c -> {
                var conn = Minecraft.getInstance().getConnection();
                if (conn != null) conn.sendCommand("setmaxspeed " + StringArgumentType.getString(c, "preset"));
                return 1;
            }))));
        });
    }
}
