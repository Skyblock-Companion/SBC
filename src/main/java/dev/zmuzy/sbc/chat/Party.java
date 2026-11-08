// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// party names for tab complete. party is tracked from the game's messages, /p and /party suggest members
// first then the tab list
// what you type goes to the server exactly as typed, nothing added
public final class Party {
    private static final dev.zmuzy.sbc.core.Patterns.P NAME = dev.zmuzy.sbc.core.Patterns.p("Party.NAME");
    private static final dev.zmuzy.sbc.core.Patterns.P JOINED = dev.zmuzy.sbc.core.Patterns.p("Party.JOINED");
    private static final dev.zmuzy.sbc.core.Patterns.P LEFT = dev.zmuzy.sbc.core.Patterns.p("Party.LEFT");
    private static final dev.zmuzy.sbc.core.Patterns.P YOURS = dev.zmuzy.sbc.core.Patterns.p("Party.YOURS");
    private static final dev.zmuzy.sbc.core.Patterns.P WITH = dev.zmuzy.sbc.core.Patterns.p("Party.WITH");
    private static final Set<String> SUB_WITH_NAME = Set.of("invite", "kick", "promote", "demote", "transfer", "mute", "unmute", "kickoffline");
    private static final Set<String> SUBS = Set.of("invite", "kick", "promote", "demote", "transfer", "disband", "leave", "list", "mute", "unmute", "warp", "settings", "kickoffline", "accept", "yoink", "private", "all");

    // from skyhanni PartyApi, patterns by key through ChatMatch
    private static final Set<String> GONE = Set.of("You left the party.", "The party was disbanded because all invites expired and the party was empty.",
            "You are not currently in a party.", "You are not in a party.", "The party was disbanded because the party leader disconnected.");
    private static final dev.zmuzy.sbc.core.Patterns.P PARTY_CHAT = dev.zmuzy.sbc.core.Patterns.p("SH.data.chat.player.party");

    private static final Set<String> members = new LinkedHashSet<>();
    private static String leader, previousLeader;

    private Party() {}

    public static Set<String> members() {
        return members;
    }

    // PartyApi.partyLeader: null until chat says who leads
    public static String leader() {
        return leader;
    }

    // PartyApi.prevPartyLeader
    public static String previousLeader() {
        return previousLeader;
    }

    private static String ownName() {
        var user = Minecraft.getInstance().getUser();
        return user == null ? "" : user.getName();
    }

    private static String clean(String raw) {
        return dev.zmuzy.sbc.events.diana.RareMobWaypointShare.cleanPlayerName(raw);
    }

    private static void add(String name, String own) {
        if (!name.equals(own)) members.add(name);
    }

    private static void removeWithLeader(String name) {
        members.remove(name);
        if (name.equals(previousLeader)) previousLeader = null;
    }

    private static void left() {
        members.clear();
        leader = null;
        previousLeader = null;
    }

    // tests: forget the party
    static void reset() {
        left();
    }

    private static Matcher sh(String key, String formatted, String plain) {
        return ChatMatch.full("SH.data.party." + key, formatted, plain);
    }

    // PartyApi.onChat, with colours, without, and as it came
    static void feed(String formatted, String plain, String span, String own) {
        String message = formatted.trim().replace("§r", "");
        String t = plain.trim();
        Matcher m;
        if ((m = sh("you.joined", message, t)) != null) {
            leader = clean(m.group("name"));
            add(leader, own);
        }
        if ((m = sh("others.joined", message, t)) != null) {
            if (members.isEmpty()) leader = own;
            add(clean(m.group("name")), own);
        }
        if ((m = sh("others.inparty", message, t)) != null) for (String n : m.group("names").split(", ")) add(clean(n), own);
        if ((m = sh("kuudrafinder.join", message, t)) != null) add(clean(m.group("name")), own);
        if ((m = sh("dungeonfinder.join", message, t)) != null) add(clean(m.group("name")), own);
        if ((m = sh("others.left", message, t)) != null) removeWithLeader(clean(m.group("name")));
        if ((m = sh("others.kicked", message, t)) != null) removeWithLeader(clean(m.group("name")));
        if ((m = sh("others.offline", message, t)) != null) removeWithLeader(clean(m.group("name")));
        if ((m = sh("others.disconnect", message, t)) != null) members.remove(clean(m.group("name")));
        if ((m = sh("others.transfer.leave", t, t)) != null) {
            leader = clean(m.group("newowner"));
            members.remove(clean(m.group("name")));
        }
        if ((m = sh("others.transfer.voluntary", t, t)) != null) {
            leader = clean(m.group("newowner"));
            previousLeader = clean(m.group("name"));
        }
        if (sh("others.disband", message, t) != null || sh("you.kicked", message, t) != null || t.startsWith("You have been kicked from the party by ") || GONE.contains(t)) left();
        if (sh("members.start", message, t) != null) members.clear();
        if ((m = sh("members.list.withkind", t, t)) != null) {
            boolean isLeader = m.group("kind").equals("Leader");
            for (String n : m.group("names").split(" ● ")) {
                String name = clean(n.replace(" ●", ""));
                add(name, own);
                if (isLeader) leader = name;
            }
        }
        if ((m = PARTY_CHAT.matcher(span)).matches()) add(clean(m.group("author")), own);
    }

    private static String bare(String s) {
        return s.replaceAll("^\\[[^\\]]+\\] ", "").trim();
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        feed(dev.zmuzy.sbc.core.LegacyText.of(line.component()), line.plain(), line.component().getString(), ownName());
        String t = line.plain().trim();
        Matcher m;
        if (t.startsWith("Party Members") || t.startsWith("Party Leader") || t.startsWith("Party Moderators")) {
            Matcher n = NAME.matcher(t);
            if (t.startsWith("Party Members (")) members.clear();
            while (n.find()) members.add(n.group(1));
        } else if ((m = JOINED.matcher(t)).matches()) {
            members.add(m.group(1));
        } else if ((m = LEFT.matcher(t)).matches()) {
            members.remove(m.group(1));
        } else if ((m = YOURS.matcher(t)).matches()) {
            members.clear();
            members.add(m.group(1));
        } else if ((m = WITH.matcher(t)).matches()) {
            for (String s : m.group(1).replace(".", "").split(", ")) if (!s.isBlank()) members.add(bare(s));
        } else if (t.equals("You left the party.") || t.contains("has disbanded the party") || t.equals("The party was disbanded because all invites expired and the party was empty.")
                || t.contains("disbanded the party")) {
            members.clear();
        }
        return ChatBus.PASS;
    }

    private static void suggestNames(com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        String typed = b.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> seen = new LinkedHashSet<>(members);
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null)
            for (var info : mc.getConnection().getOnlinePlayers()) {
                String n = info.getProfile().name();
                if (n.matches("\\w{3,16}") && !n.equals(mc.getUser().getName())) seen.add(n);
            }
        for (String n : seen) if (n.toLowerCase(Locale.ROOT).startsWith(typed)) b.suggest(n);
    }

    // ours if PartyCommands takes it, anything else goes to the server as typed. never forward from here, see ServerCommand
    private static int send(String rest) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return PartyCommands.handled(rest) ? 1 : dev.zmuzy.sbc.core.ServerCommand.toServer();
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String root) {
        return literal(root)
                .executes(c -> send(""))
                .then(argument("args", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            String all = b.getInput().substring(b.getStart() > 0 ? 0 : 0);
                            String[] words = all.replaceFirst("^/", "").split(" ", -1);
                            // words[0] = command, words[1] = subcommand or a name
                            if (words.length <= 2) {
                                String typed = words.length == 2 ? words[1].toLowerCase(Locale.ROOT) : "";
                                var sub = new com.mojang.brigadier.suggestion.SuggestionsBuilder(b.getInput(), b.getInput().length() - typed.length());
                                for (String s : SUBS) if (s.startsWith(typed)) sub.suggest(s);
                                for (String n : members) if (n.toLowerCase(Locale.ROOT).startsWith(typed)) sub.suggest(n);
                                return sub.buildFuture();
                            }
                            if (SUB_WITH_NAME.contains(words[1].toLowerCase(Locale.ROOT))) {
                                String typed = words[words.length - 1];
                                var sub = new com.mojang.brigadier.suggestion.SuggestionsBuilder(b.getInput(), b.getInput().length() - typed.length());
                                suggestNames(sub);
                                return sub.buildFuture();
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> send(StringArgumentType.getString(c, "args"))));
    }

    public static void register() {
        Feature f = Features.add(new Feature("chat_party", "Chat", "Party tab-complete", "/p and /party suggest your party members and everyone online after invite, kick, promote, transfer and the like.", "SkyHanni: party tab-complete")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        // other stuff follows the party too (board, short commands, current chat) so this keeps running even with tab
        // complete off
        ChatBus.register(ChatBus.OBSERVE + 13, "party", null, Party::onChat);
        PartyCommands.register();
        Safe.commands("Party.commands", (d, r) -> {
            d.register(command("p"));
            d.register(command("party"));
        });
    }
}
