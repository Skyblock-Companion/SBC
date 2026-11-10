// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.chat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.ServerCommand;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// party commands you type yourself. from skyhanni PartyCommands (/pk /pt /pp /pd /pko /pw /rpt, kick reason,
// clickable reverse transfer), AcceptLastPartyInvite, PartyOnBoop, TransferCooldown
// from skyblocker Reparty (/rp + noticing a leader reforming the party)
// nothing sends by itself, every command is from something you typed, clicked or pressed. where skyblocker
// auto accepts a reparty this asks for the key or a click
public final class PartyCommands {
    public static final String SHORT = "chat_party_short", REVERSE = "chat_party_reverse", ACCEPT = "chat_accept_last_invite", REPARTY = "chat_accept_reparty", BOOP = "chat_boop_party", COOLDOWN = "chat_transfer_cooldown";

    private static final Patterns.P INVITED = Patterns.p("SH.party.invite.received"), BOOP_LINE = Patterns.p("SH.bingo.boop.colorless"),
            PRIVATE = Patterns.p("SH.data.chat.player.privatemessage"), TRANSFER = Patterns.p("SH.data.party.others.transfer.voluntary");
    // skyblocker Reparty, on stripped text
    private static final Pattern REPARTY_LINE = Pattern.compile("^(?:([\\[A-z+\\]]* )?(?<disband>.*) has disbanded .*|.*\\n([\\[A-z+\\]]* )?(?<invite>.*) has invited you to join their party!\\nYou have 60 seconds to accept\\. Click here to join!\\n.*)$");
    private static final int STEP = 10;

    private record Later(String command, int atTick) {}

    private static final ArrayDeque<Later> QUEUE = new ArrayDeque<>();
    private static int ticks;
    private static String lastInviter = "", disbander = "";
    private static int disbandAt;
    private static KeyMapping acceptKey;
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static long cooldownEnds;

    private PartyCommands() {}

    private static boolean on(String id) {
        return Features.on(id);
    }

    private static boolean opt(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(feature + "." + key);
        return o == null ? def : o.bool();
    }

    private static void say(String text) {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] " + text));
    }

    private static void command(String command) {
        ServerCommand.send(command);
    }

    private static String ownName() {
        var user = Minecraft.getInstance().getUser();
        return user == null ? "" : user.getName();
    }

    // what you typed after /p or /party, true = we handled it
    static boolean handled(String rest) {
        String[] w = rest.trim().split(" +");
        if (w.length == 1 && w[0].equalsIgnoreCase("accept") && on(ACCEPT)) {
            acceptLast();
            return true;
        }
        if (w.length >= 3 && w[0].equalsIgnoreCase("kick") && opt(SHORT, "kick_reason", false) && on(SHORT)) {
            kick(w[1], rest.trim().split(" +", 3)[2]);
            return true;
        }
        return false;
    }

    // AcceptLastPartyInvite.onMessageSendToServer
    private static void acceptLast() {
        if (lastInviter.isEmpty()) {
            say("§7There is no party invite to accept!");
            return;
        }
        command("party accept " + lastInviter);
        lastInviter = "";
    }

    private static void kick(String who, String reason) {
        if (reason != null && !reason.isBlank() && opt(SHORT, "kick_reason", false)) command("pc Kicking " + who + ": " + reason);
        command("party kick " + who);
    }

    // /reparty: disband and invite everyone again, spaced like skyblocker's
    private static int reparty(CommandContext<FabricClientCommandSource> c) throws CommandSyntaxException {
        if (!on(SHORT)) return ServerCommand.toServer();
        List<String> names = Party.members().stream().filter(n -> !n.equals(ownName())).toList();
        if (names.isEmpty() || !ownName().equals(Party.leader())) {
            say("§7You are not leading a party with others. /p list reads the party again.");
            return 1;
        }
        int at = ticks + STEP;
        QUEUE.add(new Later("party disband", at));
        for (String n : names) QUEUE.add(new Later("party invite " + n, at += STEP));
        return 1;
    }

    // only sends while the short commands are on, else the server gets what was typed
    private static int run(Runnable action) throws CommandSyntaxException {
        if (!on(SHORT)) return ServerCommand.toServer();
        action.run();
        return 1;
    }

    private static com.mojang.brigadier.suggestion.SuggestionsBuilder names(com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        String typed = b.getRemainingLowerCase();
        for (String n : Party.members()) if (n.toLowerCase(Locale.ROOT).startsWith(typed)) b.suggest(n);
        return b;
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> simple(String name, String command) {
        return literal(name).executes(c -> run(() -> command(command)));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> named(String name, String command) {
        return literal(name).then(argument("name", StringArgumentType.word()).suggests((c, b) -> names(b).buildFuture())
                .executes(c -> run(() -> command(command + " " + StringArgumentType.getString(c, "name")))));
    }

    // PartyCommands.autoPartyTransfer
    private static void reverse() {
        String previous = Party.previousLeader();
        if (previous == null) {
            say("§7No earlier leader to give the party back to.");
            return;
        }
        command("party transfer " + previous);
        Feature f = Features.get(REVERSE);
        Option message = f == null ? null : f.option(REVERSE + ".message");
        if (message != null && !message.text().isBlank()) command("pc " + message.text());
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String formatted = LegacyText.of(line.component()), plain = line.plain();
        // AcceptLastPartyInvite.onChat, plain text too in case the colours differ
        Matcher m = INVITED.matcher(formatted);
        if (m.find()) lastInviter = m.group("player");
        else if ((m = PLAIN_INVITE.matcher(plain)).find()) lastInviter = m.group("player");
        m = ChatMatch.full("SH.party.invite.expired", formatted, plain);
        if (m != null && lastInviter.equals(m.group("player"))) lastInviter = "";

        if (on(REPARTY)) {
            m = REPARTY_LINE.matcher(plain);
            if (m.matches()) {
                String gone = m.group("disband"), invite = m.group("invite");
                if (gone != null && !gone.equals(ownName())) {
                    disbander = gone;
                    disbandAt = ticks;
                } else if (invite != null && invite.equals(disbander) && ticks - disbandAt <= 61) offer(invite);
            }
        }
        if (on(BOOP)) offerBoop(plain);
        if (on(REVERSE) && opt(REVERSE, "clickable", false) && TRANSFER.matcher(plain.trim()).matches() && ownName().equals(Party.leader()) && Party.previousLeader() != null) {
            return new ChatBus.Replace(Component.literal(formatted).withStyle(style("/rpt", "§eClick to give the party back to §b" + Party.previousLeader())));
        }
        return ChatBus.PASS;
    }

    private static final Pattern PLAIN_INVITE = Pattern.compile("(?:^|\\n)(?:\\[[^\\]]+\\] )?(?<player>\\w+) has invited you to join their party!");

    private static Style style(String command, String hover) {
        return Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
    }

    private static void clickable(String text, String command) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] " + text).withStyle(style(command, "§e" + command)));
    }

    // BoopParty: who just booped you (not you), or null
    static String booper(String plain, String own) {
        Matcher m = PRIVATE.matcher(plain.trim());
        if (!m.matches() || !m.group("direction").equals("From") || !BOOP_LINE.matcher(m.group("message").trim()).matches()) return null;
        String who = io.github.zmuzy.sbc.events.diana.RareMobWaypointShare.cleanPlayerName(m.group("author"));
        return who.equals(own) ? null : who;
    }

    // the line to click, for this switch and the bingo-only one
    public static void offerBoop(String plain) {
        String who = booper(plain, ownName());
        if (who != null) clickable("§eClick to invite §b" + who + " §eto the party!", "/party invite " + who);
    }

    // where skyblocker would send /party accept by itself
    private static void offer(String leader) {
        lastInviter = leader;
        String key = acceptKey == null || acceptKey.isUnbound() ? "" : " or press " + acceptKey.getTranslatedKeyMessage().getString();
        clickable("§b" + leader + " §eis reforming the party. Click" + key + " to join.", "/party accept " + leader);
    }

    private static void tick(Minecraft mc) {
        ticks++;
        while (!QUEUE.isEmpty() && QUEUE.peek().atTick() <= ticks) command(QUEUE.poll().command());
        if (acceptKey != null) while (acceptKey.consumeClick()) if (mc.player != null && mc.gui.screen() == null && (on(ACCEPT) || on(REPARTY))) acceptLast();
        // TransferCooldown: a world change starts it
        if (on(COOLDOWN)) {
            if (mc.level != lastLevel) {
                lastLevel = mc.level;
                if (mc.level != null) cooldownEnds = System.currentTimeMillis() + 3000;
            }
            if (cooldownEnds > 0 && System.currentTimeMillis() >= cooldownEnds) {
                cooldownEnds = 0;
                if (GameState.inSkyBlock) say("§aPlayer Transfer Cooldown has ended.");
            }
        }
    }

    public static void keys(KeyMapping.Category category) {
        acceptKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.party_accept",
                InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    public static void register() {
        Features.add(new Feature(SHORT, "Chat", "Short party commands", "/pk kicks, /pt transfers (alone it is your play time), /pp promotes, /pd disbands, /pko kicks offline members, /pw warps, /rp invites everyone again after disbanding. With tab-complete. Off, these go to the server as typed.", "SkyHanni: Party Commands")
                .when(() -> GameState.onHypixel || SbcMod.DEV)
                .option(Option.bool("chat_party_short.kick_reason", "Say why you kick", "/pk Name reason or /p kick Name reason first writes the reason in party chat.", false)));
        Features.add(new Feature(REVERSE, "Chat", "Give the party back", "/rpt transfers the party back to whoever gave it to you.", "SkyHanni: Reverse Party Transfer")
                .when(() -> GameState.onHypixel || SbcMod.DEV)
                .option(Option.bool("chat_party_reverse.clickable", "Clickable transfer line", "Click the line that the party was transferred to you to give it back.", false))
                .option(Option.text("chat_party_reverse.message", "Party chat line", "Written in party chat when /rpt gives the party back. Empty writes nothing.", "")));
        Features.add(new Feature(ACCEPT, "Chat", "Accept the last party invite", "/p accept with no name, or the Accept party invite key in Controls, joins whoever invited you last.", "SkyHanni: Accept Last Party Invite")
                .when(() -> GameState.onHypixel || SbcMod.DEV).defaultOff());
        Features.add(new Feature(REPARTY, "Chat", "Reparty offer", "When a leader disbands and invites you again, a line to click, or the Accept party invite key, joins. It never joins by itself.", "Skyblocker: Auto accept Reparty")
                .when(() -> GameState.onHypixel || SbcMod.DEV).defaultOff());
        Features.add(new Feature(BOOP, "Chat", "Party on boop", "A line to click to invite someone who sent you /boop.", "SkyHanni: Boop Party")
                .when(() -> GameState.onHypixel || SbcMod.DEV).defaultOff());
        Features.add(new Feature(COOLDOWN, "Chat", "Transfer cooldown message", "A chat line when the 3 seconds after changing servers are over.", "SkyHanni: Transfer Cooldown Message")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff());
        // reads invites if either is on, the key and /p accept both need them
        ChatBus.register(ChatBus.OBSERVE + 14, "party_commands", null, PartyCommands::onChat);
        Safe.endTick("PartyCommands.tick", PartyCommands::tick);
        Safe.commands("PartyCommands.commands", (d, r) -> {
            d.register(simple("pko", "party kickoffline"));
            d.register(simple("pw", "party warp"));
            d.register(simple("pd", "party disband"));
            d.register(named("pp", "party promote"));
            d.register(literal("pk").then(argument("name", StringArgumentType.word()).suggests((c, b) -> names(b).buildFuture())
                    .executes(c -> run(() -> kick(StringArgumentType.getString(c, "name"), null)))
                    .then(argument("reason", StringArgumentType.greedyString()).executes(c -> run(() -> kick(StringArgumentType.getString(c, "name"), StringArgumentType.getString(c, "reason")))))));
            d.register(literal("pt").executes(c -> run(() -> command("playtime"))).then(argument("name", StringArgumentType.word()).suggests((c, b) -> names(b).buildFuture())
                    .executes(c -> run(() -> command("party transfer " + StringArgumentType.getString(c, "name"))))));
            d.register(literal("rpt").executes(c -> {
                if (!on(REVERSE)) return ServerCommand.toServer();
                reverse();
                return 1;
            }));
            d.register(literal("rp").executes(PartyCommands::reparty));
            d.register(literal("reparty").executes(PartyCommands::reparty));
        });
    }
}
