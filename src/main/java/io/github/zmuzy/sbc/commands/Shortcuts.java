// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.core.Commands;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.KeyHeld;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyblocker Shortcuts: your own commands. "/ga" -> "/warp garden", "/m" -> "/msg" + the rest, or a key
// that runs a command
// nothing ships on, the usual ones are a button away. works anywhere like theirs
// keys go through KeyHeld each tick so mouse buttons and combos work, only with no screen open (same as theirs)
public final class Shortcuts {
    public static final String ID = "shortcuts";
    private static final long KEY_COOLDOWN = 200;
    private static final Set<String> down = new HashSet<>();
    private static long lastKeyCommand;

    private Shortcuts() {}

    // skyblocker getDefaultShortcuts: {commands, command args}
    static List<Map<String, String>> usual() {
        Map<String, String> commands = new LinkedHashMap<>(), args = new LinkedHashMap<>();
        commands.put("/s", "/skyblock");
        commands.put("/i", "/is");
        commands.put("/h", "/hub");
        commands.put("/ga", "/warp garden");
        commands.put("/d", "/warp dungeon_hub");
        commands.put("/dn", "/warp dungeon_hub");
        commands.put("/ca", "/chat all");
        commands.put("/cp", "/chat party");
        commands.put("/cg", "/chat guild");
        commands.put("/co", "/chat officer");
        args.put("/m", "/msg");
        args.put("/pa", "/p accept");
        commands.put("/pd", "/p disband");
        args.put("/v", "/visit");
        commands.put("/vp", "/visit portalhub");
        return List.of(commands, args);
    }

    // skyblocker modifyCommand, command without the slash
    static String modify(String command, Map<String, String> commands, Map<String, String> args, boolean useCommands, boolean useArgs) {
        String c = '/' + command;
        if (useCommands) c = commands.getOrDefault(c, c);
        if (useArgs) {
            String[] words = c.split(" ");
            for (int i = 0; i < words.length; i++) words[i] = args.getOrDefault(words[i], words[i]);
            c = String.join(" ", words);
        }
        return c.substring(1);
    }

    private static boolean opt(String key) {
        Feature f = Features.get(ID);
        if (f == null || !f.active()) return false;
        Option o = f.option(ID + "." + key);
        return o != null && o.bool();
    }

    private static List<InputConstants.Key> keys(String combo) {
        List<InputConstants.Key> out = new ArrayList<>();
        for (String name : combo.split(" \\+ ")) {
            try {
                out.add(InputConstants.getKey(name));
            } catch (Exception e) {
                return List.of();
            }
        }
        return out;
    }

    // runs a command or chat line like the player sent it
    private static void run(Minecraft mc, String what) {
        var conn = mc.getConnection();
        if (conn == null || what.isBlank()) return;
        if (what.startsWith("/")) conn.sendCommand(what.substring(1));
        else conn.sendChat(what);
    }

    // skyblocker onKeyPressed. longest combo held wins so ctrl+g doesnt also fire g
    private static void tick(Minecraft mc) {
        Map<String, String> bound = Saved.get().shortcutKeys;
        if (bound.isEmpty()) return;
        boolean allowed = opt("keys") && mc.gui.screen() == null;
        List<String> combos = new ArrayList<>(bound.keySet());
        combos.sort(Comparator.comparingInt(c -> -c.length()));
        String fire = null;
        for (String combo : combos) {
            List<InputConstants.Key> keys = keys(combo);
            boolean held = allowed && !keys.isEmpty();
            for (InputConstants.Key k : keys) held &= KeyHeld.held(k);
            if (!held) {
                down.remove(combo);
            } else if (down.add(combo) && fire == null) {
                fire = combo;
            }
        }
        if (fire == null || System.currentTimeMillis() - lastKeyCommand < KEY_COOLDOWN) return;
        lastKeyCommand = System.currentTimeMillis();
        run(mc, bound.get(fire));
    }

    private static int help(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> c) {
        Saved.Data d = Saved.get();
        d.shortcutCommands.forEach((k, v) -> c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("§7" + k + " → " + v)));
        d.shortcutArgs.forEach((k, v) -> c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("§7" + k + " … → " + v + " …")));
        d.shortcutKeys.forEach((k, v) -> c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("§7" + k + " → " + v)));
        return 1;
    }

    public static void register() {
        Features.add(new Feature(ID, "Commands", "Shortcuts", "Your own short commands and keys, anywhere, even outside Hypixel: /ga for /warp garden, /m for /msg, a key for /pets. /sbc shortcuts sets them up.", "Skyblocker: Shortcuts")
                .option(Option.bool("shortcuts.commands", "Command shortcuts", "A whole command for another. Must be typed alone.", true))
                .option(Option.bool("shortcuts.args", "Word shortcuts", "A word for another, the rest of the command stays.", true))
                .option(Option.bool("shortcuts.keys", "Key shortcuts", "A key runs a command.", true)));
        Commands.describe("shortcuts", "Set up your own commands and keys");
        Safe.modifyCommand("Shortcuts.modifyCommand", command -> {
            Feature f = Features.get(ID);
            if (f == null || !f.active()) return command;
            Saved.Data d = Saved.get();
            return modify(command, d.shortcutCommands, d.shortcutArgs, opt("commands"), opt("args"));
        });
        Safe.endTick("Shortcuts.tick", Shortcuts::tick);
        Safe.commands("Shortcuts.commands", (d, r) -> {
            d.register(literal("sbc").then(literal("shortcuts")
                    .executes(c -> {
                        Minecraft mc = Minecraft.getInstance();
                        mc.execute(() -> mc.gui.setScreen(new ShortcutsScreen(mc.gui.screen())));
                        return 1;
                    })
                    .then(literal("list").executes(Shortcuts::help))));
            // these only let the command through. the game needs the name for suggestions, what you typed goes out
            // through modify above
            Saved.Data saved = Saved.get();
            for (String key : saved.shortcutCommands.keySet()) if (key.startsWith("/") && !key.contains(" ") && key.length() > 1 && d.getRoot().getChild(key.substring(1)) == null) d.register(literal(key.substring(1)).executes(c -> io.github.zmuzy.sbc.core.ServerCommand.toServer()).then(argument("args", StringArgumentType.greedyString()).executes(c -> io.github.zmuzy.sbc.core.ServerCommand.toServer())));
            for (String key : saved.shortcutArgs.keySet()) if (key.startsWith("/") && !key.contains(" ") && key.length() > 1 && d.getRoot().getChild(key.substring(1)) == null) d.register(literal(key.substring(1)).executes(c -> io.github.zmuzy.sbc.core.ServerCommand.toServer()).then(argument("args", StringArgumentType.greedyString()).executes(c -> io.github.zmuzy.sbc.core.ServerCommand.toServer())));
        });
    }
}
