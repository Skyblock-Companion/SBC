// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.muzy.sbc.chat.Party;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Profile;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// rest of the dungeon helpers. from skyblocker secrets/SecretsTracker (what each player found in a run,
// through our own profile server), dungeon/Reparty (your own command, same as there) and QuiverWarning
public final class DungeonPack {
    // from skyblocker DungeonManager.TEAM_SCORE_PATTERN: end of a run
    private static final Pattern TEAM_SCORE = Pattern.compile(" +Team Score: [0-9]+ \\([A-z+]+\\)");
    private static final String TRACK_START = "[NPC] Mort: You should find it useful if you get lost.";

    private static final Map<String, Integer> startSecrets = new HashMap<>();
    private static final Deque<String> queue = new ArrayDeque<>();
    private static long nextSend;
    private static String warning;
    private static boolean wasIn;
    private DungeonPack() {}

    // --- secrets per player ---

    private static void trackStart() {
        startSecrets.clear();
        for (DungeonRun.Member m : DungeonRun.team()) {
            String name = m.name();
            Profile.fetch(name, d -> startSecrets.put(name, d.secrets), e -> {});
        }
    }

    private static void trackEnd() {
        for (var e : new HashMap<>(startSecrets).entrySet()) {
            String name = e.getKey();
            int before = e.getValue();
            DungeonRun.Member m = DungeonRun.member(name);
            String cls = m == null || m.dungeonClass().isEmpty() ? "" : " (" + m.dungeonClass() + ")";
            Profile.fetch(name, d -> say("§f" + name + cls + " §7found §f" + Math.max(0, d.secrets - before) + " §7secrets"), err -> say("§7Could not read the secrets of " + name));
        }
    }

    private static void say(String text) {
        Minecraft.getInstance().execute(() -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §r" + text));
        });
    }

    // --- reparty ---

    private static void sendQueue(Minecraft mc) {
        if (queue.isEmpty() || mc.player == null || mc.player.connection == null) return;
        long now = System.currentTimeMillis();
        if (now < nextSend) return;
        nextSend = now + 500;
        mc.player.connection.sendCommand(queue.poll());
    }

    // --- quiver ---

    // from skyblocker QuiverWarning.onChatMessage for what you hear in a dungeon, and update for after
    static String quiverTitle(String line) {
        if (!line.endsWith("left in your Quiver!")) return null;
        if (line.startsWith("You only have 50")) return "§cYou only have 50 arrows left in your Quiver!";
        if (line.startsWith("You only have 10")) return "§cYou only have 10 arrows left in your Quiver!";
        if (line.startsWith("You don't have any more")) return "§cYou don't have any more arrows left in your Quiver!";
        return null;
    }

    private static void onLine(io.muzy.sbc.chat.ChatBus.Line line) {
        if (TRACK_START.equals(line.plain().trim()) && Features.on("dungeon_secrets_per_player") && DungeonRun.inDungeon()) trackStart();
        if (TEAM_SCORE.matcher(line.plain()).matches() && Features.on("dungeon_secrets_per_player") && DungeonRun.inDungeon()) trackEnd();
        String t = quiverTitle(line.plain().trim());
        if (t != null && DungeonRun.inDungeon() && Features.on("dungeon_quiver")) {
            if (DungeonMarks.opt("dungeon_quiver", "inside")) Minecraft.getInstance().gui.hud.setTitle(Component.literal(t));
            warning = t;
        }
    }

    // per run stuff has to go when the run ends. the features that clear it in their own tick switch off outside
    // dungeons so that tick never comes, a 2nd run would start with the old rooms otherwise
    private static void leftDungeon() {
        boolean in = DungeonRun.inDungeon();
        if (wasIn && !in) {
            DungeonExtras.reset();
            Score.leave();
            Dungeon.leave();
        }
        wasIn = in;
    }

    private static void tick(Minecraft mc) {
        leftDungeon();
        sendQueue(mc);
        // from skyblocker QuiverWarning.update: what you heard in the run, once more when its over
        if (warning != null && !DungeonRun.inDungeon() && GameState.inSkyBlock) {
            if (Features.on("dungeon_quiver") && DungeonMarks.opt("dungeon_quiver", "after")) mc.gui.hud.setTitle(Component.literal(warning));
            warning = null;
        }
    }

    public static void register() {
        DungeonRun.register();
        TerminalClicks.register();
        Goldor.register();
        DungeonDoors.register();
        DungeonTimers.register();
        DungeonAlerts.register();
        DungeonObjects.register();
        BloodCamp.register();
        DungeonChat.register();
        DungeonRaces.register();
        DungeonChests.register();
        FinderMarks.register();
        DungeonMisc.register();
        Feature pack = Features.add(new Feature("dungeon_pack_tick", "Dungeons", "Dungeon background", "Keeps the dungeon helpers without a switch running.", "").alwaysOn().tick(DungeonPack::tick));
        Feature secrets = Features.add(new Feature("dungeon_secrets_per_player", "Dungeons", "Secrets per player", "At the end of a run, says in your chat how many secrets each player found. Read from their profiles.", "Skyblocker: Player Secrets Tracker")
                .when(() -> DungeonRun.inDungeon() || io.muzy.sbc.SbcMod.DEV));
        Features.add(new Feature("dungeon_quiver", "Dungeons", "Quiver running out in a dungeon", "A title when your quiver runs low in a dungeon, and again when the run is over.", "Skyblocker: Quiver Warning").when(() -> GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV)
                .option(Option.bool("dungeon_quiver.inside", "During the run", "Warns when you hear it in the dungeon.", true))
                .option(Option.bool("dungeon_quiver.after", "After the run", "Warns again once you are out.", true)));
        io.muzy.sbc.chat.ChatBus.register(io.muzy.sbc.chat.ChatBus.OBSERVE + 17, "dungeonpack", null, line -> {
            onLine(line);
            return io.muzy.sbc.chat.ChatBus.PASS;
        });
        Safe.commands("DungeonPack.commands", (dispatcher, registry) -> {
            dispatcher.register(literal("sbc")
                    .then(literal("resetterminals").executes(c -> {
                        Goldor.resetMarks();
                        return 1;
                    }))
                    .then(literal("dungeonsound").then(argument("which", StringArgumentType.word()).suggests((c, b) -> {
                        b.suggest("chime");
                        b.suggest("lowhp");
                        return b.buildFuture();
                    }).executes(c -> {
                        if ("lowhp".equals(StringArgumentType.getString(c, "which"))) DungeonAlerts.lowHpSound();
                        else DungeonAlerts.chime();
                        return 1;
                    }))));
        });
    }
}
