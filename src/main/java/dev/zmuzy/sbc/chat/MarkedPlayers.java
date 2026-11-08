// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.TabWidgets;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni MarkedPlayerManager, the chat half: marked players stand out in chat, and a line when listed
// players join/leave your lobby
// tab list says whos here. marking them in the world is up to the world highlight settings
public final class MarkedPlayers {
    public static final String ID = "chat_marked_players", JOIN = "chat_join_leave";
    private static final Pattern TAB_NAME = dev.zmuzy.sbc.core.Patterns.get("SH.misc.markedplayer.tabplayername-no-color");
    private static final Set<String> notified = new HashSet<>(), here = new HashSet<>();
    private static long nextCheck;

    private MarkedPlayers() {}

    private static Option opt(String feature, String key) {
        Feature f = Features.get(feature);
        return f == null ? null : f.option(feature + "." + key);
    }

    // lowercase, like MarkedPlayerManager keeps them
    public static Set<String> marked() {
        Option o = opt(ID, "names");
        Set<String> out = new LinkedHashSet<>();
        if (o != null) for (String s : o.text().split(",")) if (!s.isBlank()) out.add(s.trim().toLowerCase(Locale.ROOT));
        // MarkedPlayerConfig.markOwnName
        Option own = opt(ID, "markOwn");
        Minecraft mc = Minecraft.getInstance();
        if (own != null && own.bool() && mc != null && mc.player != null) out.add(mc.player.getName().getString().toLowerCase(Locale.ROOT));
        return out;
    }

    // /markplayer toggles
    private static int toggle(String name) {
        Option o = opt(ID, "names");
        if (o == null) return 0;
        Set<String> names = marked();
        String lower = name.toLowerCase(Locale.ROOT);
        boolean added = names.add(lower);
        if (!added) names.remove(lower);
        o.set(String.join(",", names));
        dev.zmuzy.sbc.core.Config.save();
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal(added ? "§b[SBC] §aMarked §eplayer §b" + name + "§e!" : "§b[SBC] §cUnmarked §eplayer §b" + name + "§e!"));
        return 1;
    }

    // line with every marked name in this colour, hover/click kept. null when no name is in it. pure
    public static Component recolor(Component line, Set<String> names, String colorCode) {
        if (names.isEmpty()) return null;
        StringBuilder alt = new StringBuilder("\\b(?:");
        boolean first = true;
        for (String n : names) {
            alt.append(first ? "" : "|").append(Pattern.quote(n));
            first = false;
        }
        Pattern any = Pattern.compile(alt.append(")\\b").toString(), Pattern.CASE_INSENSITIVE);
        if (!any.matcher(line.getString()).find()) return null;
        var color = net.minecraft.ChatFormatting.getByCode(colorCode.charAt(colorCode.length() - 1));
        MutableComponent out = Component.empty();
        line.<Object>visit((style, text) -> {
            Matcher m = any.matcher(text);
            int at = 0;
            while (m.find()) {
                if (m.start() > at) out.append(Component.literal(text.substring(at, m.start())).setStyle(style));
                out.append(Component.literal(m.group()).setStyle(color == null ? style : style.withColor(color)));
                at = m.end();
            }
            if (at < text.length()) out.append(Component.literal(text.substring(at)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    // onWidgetUpdate(PLAYER_LIST): who came and went of the ones you asked about. {joined, left} comma separated or empty
    static String[] changes(Set<String> inLobby, List<String> ofInterest, Set<String> told) {
        List<String> joined = new ArrayList<>(), left = new ArrayList<>();
        for (String n : ofInterest) {
            if (inLobby.contains(n) && told.add(n)) joined.add(n);
            else if (!inLobby.contains(n) && told.remove(n)) left.add(n);
        }
        return new String[]{String.join(", ", joined), String.join(", ", left)};
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextCheck || mc.player == null) return;
        nextCheck = now + 2000;
        Option list = opt(JOIN, "players");
        if (list == null || list.text().isBlank()) return;
        List<String> ofInterest = new ArrayList<>();
        for (String s : list.text().split(",")) if (!s.isBlank()) ofInterest.add(s.trim());
        here.clear();
        List<String> tab = GameState.tab;
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!"PLAYER_LIST".equals(w.def().id())) continue;
            for (int i : w.lines()) {
                Matcher m = TAB_NAME.matcher(tab.get(i).trim());
                if (m.matches() && !m.group("name").equals(mc.getUser().getName())) here.add(m.group("name"));
            }
        }
        String[] c = changes(here, ofInterest, notified);
        if (!c[0].isEmpty()) say(mc, "joinMessage", "&&b%s &&ajoined your lobby.", c[0]);
        if (!c[1].isEmpty()) say(mc, "leftMessage", "&&b%s &&cleft your lobby.", c[1]);
    }

    private static void say(Minecraft mc, String key, String def, String names) {
        Option text = opt(JOIN, key), prefix = opt(JOIN, "prefix");
        String format = (text == null ? def : text.text()).replace("&&", "§");
        String line = format.contains("%s") ? format.replace("%s", names) : format + " " + names;
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal((prefix == null || prefix.bool() ? "§b[SBC] " : "") + line));
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Chat", "Marked players", "Players you marked with /markplayer stand out in chat.", "SkyHanni: Marked Players")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.text("chat_marked_players.names", "Names", "With commas between them. /markplayer adds and removes.", ""))
                .option(Option.bool("chat_marked_players.chat", "Highlight in chat", "", true))
                .option(Option.bool("chat_marked_players.markOwn", "Your own name too", "", false))
                .option(Option.choice("chat_marked_players.color", "Chat colour", "", "Yellow", "Yellow", "Aqua", "Green", "Red", "Pink", "Gold", "White")));
        ChatBus.register(ChatBus.COMPACT + 6, ID, f, line -> {
            Option chat = opt(ID, "chat");
            if (chat != null && !chat.bool()) return ChatBus.PASS;
            Component done = recolor(line.component(), marked(), code());
            return done == null ? ChatBus.PASS : new ChatBus.Replace(done);
        });
        Features.add(new Feature(JOIN, "Chat", "Players joining your lobby", "A line when players on your list join or leave the lobby you are in. Not every lobby lists everyone.", "SkyHanni: Join/Leave Message")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff()
                .tick(MarkedPlayers::tick)
                .left(notified::clear)
                .option(Option.text("chat_join_leave.players", "Players", "Their exact names, with commas between them.", ""))
                .option(Option.text("chat_join_leave.joinMessage", "Join line", "&& starts a colour code, %s is the name.", "&&b%s &&ajoined your lobby."))
                .option(Option.text("chat_join_leave.leftMessage", "Leave line", "&& starts a colour code, %s is the name.", "&&b%s &&cleft your lobby."))
                .option(Option.bool("chat_join_leave.prefix", "Say it comes from here", "The small tag in front of the line.", true)));
        Safe.commands("MarkedPlayers.commands", (d, r) -> d.register(literal("markplayer").then(argument("name", StringArgumentType.word()).suggests((c, b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) for (var i : conn.getOnlinePlayers()) if (i.getProfile().name().toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(i.getProfile().name());
            return b.buildFuture();
        }).executes(c -> toggle(StringArgumentType.getString(c, "name"))))));
    }

    public static String code() {
        Option o = opt(ID, "color");
        return switch (o == null ? "Yellow" : o.choice()) {
            case "Aqua" -> "§b";
            case "Green" -> "§a";
            case "Red" -> "§c";
            case "Pink" -> "§d";
            case "Gold" -> "§6";
            case "White" -> "§f";
            default -> "§e";
        };
    }
}
