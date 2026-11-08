// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.commands;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Commands;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni OpenLastStorage: /ec - and /bp - open the page you last asked for, /sbc lastopened opens the
// last of either. per profile
public final class LastStorage {
    private LastStorage() {}

    public static final String ID = "last_storage";

    public enum Type {
        ENDER_CHEST(1, 9, "enderchest", "ec"), BACKPACK(0, 18, "backpack", "bp");

        final int min, max;
        final List<String> commands;

        Type(int min, int max, String... commands) {
            this.min = min;
            this.max = max;
            this.commands = List.of(commands);
        }

        static Type fromCommand(String command) {
            for (Type t : values()) if (t.commands.contains(command)) return t;
            return null;
        }

        boolean validPage(int page) {
            return page >= min && page <= max;
        }

        String command(int page) {
            return commands.get(1) + " " + page;
        }

        String label() {
            return this == ENDER_CHEST ? "ender chest" : "backpack";
        }
    }

    public static final class Last {
        public Type type;
        public Integer page;
    }

    private static Last storage() {
        return Saved.get().lastStorage.computeIfAbsent(GameState.profileName, k -> new Last());
    }

    private static void say(String text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    private static void open(Type type) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        Integer page = storage().page;
        if (page != null) {
            conn.sendCommand(type.command(page));
            say("Opened last " + type.label() + " " + page + ".");
            return;
        }
        String fallback = Features.get(ID).option("last_storage.fallbackCommand").text().trim();
        if (fallback.startsWith("/")) fallback = fallback.substring(1);
        if (fallback.isBlank() || asksForLast(fallback)) {
            say("No last " + type.label() + " found yet. Open one with /" + type.command(1) + ".");
            return;
        }
        conn.sendCommand(fallback);
        say("No last " + type.label() + " found. Running /" + fallback + ".");
    }

    // true if this command would land right back here, sending it from open would loop forever
    static boolean asksForLast(String command) {
        String[] args = command.toLowerCase(java.util.Locale.ROOT).split(" ");
        return Type.fromCommand(args[0]) != null && args.length > 1 && args[1].equals("-");
    }

    // true when it asks for the last page, otherwise the page gets remembered in last
    static boolean handle(String[] args, Type type, Last last) {
        if (args.length > 1 && args[1].equals("-")) return true;
        Integer page;
        if (args.length <= 1) page = 1; // no arg = first page
        else {
            try {
                int n = Integer.parseInt(args[1]);
                page = type.validPage(n) ? n : null;
            } catch (NumberFormatException e) {
                return false; // not a number, hypixel answers it and we remember nothing
            }
        }
        last.page = page;
        last.type = type;
        return false;
    }

    private static boolean onCommand(String command) {
        if (!Features.on(ID) || !GameState.inSkyBlock) return true;
        String[] args = command.toLowerCase(java.util.Locale.ROOT).split(" ");
        Type type = Type.fromCommand(args[0]);
        if (type == null) return true;
        Last last = storage();
        if (handle(args, type, last)) {
            open(type);
            return false;
        }
        Saved.save();
        return true;
    }

    public static void register() {
        Features.add(new Feature(ID, "Commands", "Last storage", "/ec - and /bp - open the page you last asked for with /ec or /bp. /sbc lastopened opens the last of either.", "SkyHanni: Last Storage")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.text("last_storage.fallbackCommand", "When there is none", "The command to run when no page is remembered yet.", "ec 1")));
        Commands.describe("lastopened", "Open the Ender Chest page or backpack you last asked for");
        Safe.allowCommand("LastStorage.onCommand", LastStorage::onCommand);
        Safe.commands("LastStorage.commands", (d, r) -> d.register(literal("sbc").then(literal("lastopened").executes(c -> {
            if (!Features.on(ID)) {
                say("This is switched off: Last storage in the settings.");
                return 0;
            }
            Last last = storage();
            open(last.type == null ? Type.ENDER_CHEST : last.type);
            return 1;
        }))));
    }
}
