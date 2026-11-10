// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.ServerCommand;
import io.github.zmuzy.sbc.core.data.CommandLists;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// rewrites a command you typed before it goes out. from skyhanni ViewRecipeCommand, ShortenWarpCommand,
// WarpIsCommand, TransferCooldown
// only ever on what you typed. TransferCooldown holds it back and sends it once hypixel takes it
public final class CommandRewrites {
    public static final String VIEWRECIPE = "cmd_viewrecipe", SHORT_WARP = "cmd_short_warp", WARP_IS = "cmd_warp_is", TRANSFER = "cmd_transfer_wait";
    // lowercase, no slash here
    private static final Pattern VIEW_RECIPE = Pattern.compile("viewrecipe (?<item>.*)");
    private static final long COOLDOWN_MS = 3000;

    private static boolean sending;
    private static ClientLevel lastLevel;
    private static long cooldownEnds;
    private static String held;

    private CommandRewrites() {}

    // "viewrecipe ID page" the way hypixel wants it, null if its not /viewrecipe
    static String viewRecipe(String command, Predicate<String> isItem) {
        Matcher m = VIEW_RECIPE.matcher(command.toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        String input = m.group("item").toUpperCase(Locale.ROOT).trim();
        if (input.isEmpty()) return null;
        String[] args = input.split(" ");
        String last = args[args.length - 1];
        if (args.length > 1 && last.matches("\\d+") && !isItem.test(input.replace(' ', '_')))
            return "viewrecipe " + String.join("_", List.of(args).subList(0, args.length - 1)) + " " + last;
        return "viewrecipe " + input.replace(' ', '_') + " 1";
    }

    // the warp for "wizard" etc, null = leave it alone
    static String shortWarp(String command, List<String> warps, String island, boolean gardenWarps) {
        String c = command.toLowerCase(Locale.ROOT).trim();
        if (c.equals("jerry") && "PRIVATE_ISLAND".equals(island)) return null;
        if (c.equals("barn") && "GARDEN".equals(island) && gardenWarps) return null;
        return warps.contains(c) ? "warp " + c : null;
    }

    // what to send once the cooldown is over, null if it doesnt need to wait
    static String afterCooldown(String command) {
        String[] w = command.split(" ", 2);
        return switch (w[0]) {
            case "is" -> "is";
            case "warp" -> w.length > 1 ? "warp " + w[1] : null;
            case "warpforge" -> "warp forge";
            case "hub" -> "warp hub";
            default -> null;
        };
    }

    private static void send(String command) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        sending = true;
        try {
            conn.sendCommand(command);
        } finally {
            sending = false;
        }
    }

    private static boolean onCommand(String command) {
        if (sending) return true;
        if (Features.on(VIEWRECIPE)) {
            String out = viewRecipe(command, id -> ItemRepo.get(id) != null);
            if (out != null) {
                send(out);
                return false;
            }
        }
        if (Features.on(WARP_IS) && command.equalsIgnoreCase("warp is")) {
            send("is");
            return false;
        }
        if (Features.on(TRANSFER) && System.currentTimeMillis() < cooldownEnds) {
            String later = afterCooldown(command);
            if (later != null) {
                held = later;
                return false;
            }
        }
        if (Features.on(SHORT_WARP)) {
            String out = shortWarp(command, CommandLists.warps(), GameState.island, Features.on("garden_warps"));
            if (out != null) {
                send(out);
                return false;
            }
        }
        return true;
    }

    private static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level != null && System.currentTimeMillis() >= cooldownEnds) cooldownEnds = System.currentTimeMillis() + COOLDOWN_MS;
        }
        if (held != null && System.currentTimeMillis() >= cooldownEnds) {
            String c = held;
            held = null;
            if (Features.on(TRANSFER)) send(c);
        }
    }

    public static void register() {
        Features.add(new Feature(VIEWRECIPE, "Commands", "/viewrecipe in lower case", "/viewrecipe aspect of the end or aspect_of_the_end finds the item, as if you had typed its id.", "SkyHanni: Lower Case /viewrecipe")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Features.add(new Feature(SHORT_WARP, "Commands", "Warp without /warp", "/wizard warps like /warp wizard, for every warp. /jerry on your island and /barn in the Garden keep their own meaning.", "SkyHanni: Shorten /warp")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff());
        Features.add(new Feature(WARP_IS, "Commands", "/warp is", "/warp is goes to your island like /is.", "SkyHanni: Replace /warp is")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff());
        Features.add(new Feature(TRANSFER, "Commands", "Wait out the transfer cooldown", "A warp you type in the 3 seconds after changing servers goes out when they are over, instead of being refused.", "SkyHanni: Fix Transfer Cooldown")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff());
        Safe.allowCommand("CommandRewrites.onCommand", CommandRewrites::onCommand);
        Safe.endTick("CommandRewrites.tick", CommandRewrites::tick);
        // warps as commands for tab complete. they only let the command through so it still goes to the server, extra words too
        Safe.commands("CommandRewrites.commands", (d, r) -> {
            Feature f = Features.get(SHORT_WARP);
            if (f == null || !f.enabled()) return;
            for (String w : CommandLists.warps()) if (d.getRoot().getChild(w) == null) d.register(literal(w).executes(c -> ServerCommand.toServer())
                    .then(argument("args", StringArgumentType.greedyString()).executes(c -> ServerCommand.toServer())));
        });
    }
}
