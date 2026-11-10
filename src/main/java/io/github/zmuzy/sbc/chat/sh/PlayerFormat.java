// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.chat.sh;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

// from skyhanni PlayerNameFormatter: a player's line rebuilt from its parts in your order, with/without
// brackets, ranks, colours
// works on text where skyhanni keeps styled pieces, so the line keeps the first hover/click the original had
// crimson faction, ironman and bingo parts come from skyhanni's tab reader, not here
public final class PlayerFormat {
    public static final String ID = "chat_player_format";
    private static final Patterns.P EMBLEM = Patterns.p("SH.data.chat.player.name.emblem"), ISLAND_RANK = Patterns.p("SH.data.chat.player.privateislandrank"), ISLAND_GUEST = Patterns.p("SH.data.chat.player.privateislandguest");

    // settings as a record so the text part tests without a game
    public record Settings(List<String> parts, boolean brackets, boolean levelColorName, boolean rankHider, boolean ignoreYouTube, boolean sameColor) {}

    private PlayerFormat() {}

    private static boolean b(String key, boolean def) {
        return ChatCompact.b(ID, ID + "." + key, def);
    }

    private static Settings settings() {
        Feature f = Features.get(ID);
        Option order = f == null ? null : f.option(ID + ".parts");
        List<String> parts = new ArrayList<>();
        for (String s : (order == null ? "level, island rank, island guest, name, guild rank, emblem" : order.text()).split(",")) parts.add(s.trim().toLowerCase(Locale.ROOT));
        return new Settings(parts, !b("hide_brackets", false), b("level_color", false), ChatCompact.b(ChatCompact.PLAYER, "playerRankHider", false), b("ignore_youtube", false), b("same_color", true));
    }

    // formatAuthor: rank kept or not, name in its colour
    static String author(String author, String levelColor, Settings s) {
        String clean = author.startsWith("§f") ? author.substring(2) : author;
        if (clean.contains("ADMIN") || (s.ignoreYouTube() && clean.contains("YOUTUBE"))) return clean;
        int space = clean.indexOf(' ');
        String rank = space < 0 ? null : clean.substring(0, space + 1), name = space < 0 ? clean : clean.substring(space + 1);
        String rankColor = rank != null && rank.startsWith("§") && rank.length() > 1 ? rank.substring(0, 2) : "";
        String bare = GameState.strip(name);
        String colored = levelColor != null && s.levelColorName() ? levelColor + bare : s.rankHider() ? "§b" + bare : rankColor.isEmpty() ? name : rankColor + bare;
        return s.rankHider() || rank == null ? colored : rank + colored;
    }

    // nameFormat: parts in the chosen order
    static String name(String rawAuthor, String levelColor, String level, String guildRank, String islandRank, String islandGuest, Settings s) {
        String author = rawAuthor, emblem = null;
        Matcher m = EMBLEM.matcher(author);
        if (m.matches()) {
            emblem = m.group("emblem");
            author = m.group("author");
        }
        Map<String, String> parts = new LinkedHashMap<>();
        if (level != null && levelColor != null) parts.put("level", s.brackets() ? "§8[" + levelColor + level + "§8]" : levelColor + level);
        if (islandRank != null) parts.put("island rank", islandRank);
        if (islandGuest != null) parts.put("island guest", islandGuest.trim());
        parts.put("name", author(author, levelColor, s));
        if (guildRank != null) parts.put("guild rank", guildRank);
        if (emblem != null) parts.put("emblem", emblem);
        List<String> out = new ArrayList<>();
        for (String p : s.parts()) if (parts.containsKey(p)) out.add(parts.get(p));
        if (!out.contains(parts.get("name"))) out.add(parts.get("name")); // a list without the name isnt what anyone meant lol
        return String.join(" ", out) + "§r";
    }

    // whole line again as text, or null if its no player line. pure
    public static String format(String span, String levelColor, boolean onIsland, String ownName, Settings s) {
        Matcher m = PlayerChat.COOP.matcher(span);
        if (m.matches()) return "§bCo-op > " + name(m.group("author"), null, null, null, null, null, s) + "§f: " + m.group("message");
        m = PlayerChat.PARTY.matcher(span);
        if (m.matches()) return "§9Party §8> " + name(m.group("author"), null, null, null, null, null, s) + "§f: " + m.group("message");
        m = PlayerChat.GUILD.matcher(span);
        if (m.matches()) return "§2Guild > " + name(m.group("author"), null, null, m.group("guildRank"), null, null, s) + "§f: " + m.group("message");
        m = PlayerChat.PRIVATE.matcher(span);
        if (m.matches()) {
            String message = m.group("message");
            return "§d" + m.group("direction") + " " + name(m.group("author"), null, null, null, null, null, s) + "§f: " + (s.sameColor() ? "§f" + GameState.strip(message) : message);
        }
        m = PlayerChat.ITEM_SHOW.matcher(span);
        if (m.matches()) {
            String level = m.group("level");
            String color = level == null || level.length() < 2 ? null : level.substring(0, 2);
            return name(m.group("author"), color, level == null ? null : GameState.strip(level), null, null, null, s) + " §7" + GameState.strip(m.group("action")) + " " + m.group("itemName");
        }
        m = PlayerChat.GLOBAL.matcher(span);
        if (!m.matches()) return null;
        String author = m.group("author"), chatColor = m.group("chatColor"), level = m.group("level");
        if (chatColor.isEmpty() && !GameState.strip(author).endsWith(ownName)) return null;
        if (GameState.strip(author).contains("[NPC]")) return null;
        String message = m.group("message");
        if (message.startsWith("§f")) message = message.substring(2);
        String guest = null, islandRank = null;
        if (onIsland) {
            Matcher g = ISLAND_GUEST.matcher(author);
            if (g.matches()) {
                guest = g.group("guest");
                author = g.group("prefix") + g.group("suffix");
            }
            Matcher r = ISLAND_RANK.matcher(author);
            if (r.matches()) {
                islandRank = r.group("privateIslandRank");
                author = r.group("prefix") + r.group("suffix");
            }
        }
        return name(author, level == null ? null : levelColor == null ? "§7" : levelColor, level, null, islandRank, guest, s) + "§f: " + (s.sameColor() ? "§f" + GameState.strip(message) : chatColor + message);
    }

    // level number colour, from the piece that holds it
    private static String levelColor(Component line, String span) {
        Matcher m = PlayerChat.GLOBAL.matcher(span);
        String level = m.matches() ? m.group("level") : null;
        if (level == null) return null;
        return line.<String>visit((style, text) -> text.contains(level) && style.getColor() != null ? Optional.of(LegacyText.chatStyle(style)) : Optional.empty(), Style.EMPTY).orElse(null);
    }

    // first hover and click in the line
    static Style events(Component line) {
        Style[] out = {Style.EMPTY};
        line.visit((style, text) -> {
            ClickEvent click = style.getClickEvent();
            HoverEvent hover = style.getHoverEvent();
            if (out[0].getClickEvent() == null && click != null) out[0] = out[0].withClickEvent(click);
            if (out[0].getHoverEvent() == null && hover != null) out[0] = out[0].withHoverEvent(hover);
            return Optional.empty();
        }, Style.EMPTY);
        return out[0];
    }

    static Component apply(Component line, String span, String ownName) {
        String text = format(span, levelColor(line, span), "PRIVATE_ISLAND".equals(GameState.island), ownName, settings());
        if (text == null || text.equals(span)) return null;
        return Component.literal(text).withStyle(events(line));
    }

    static void register() {
        Features.add(new Feature(ID, "Chat", "Player line format", "Builds player lines again from their parts: the order, brackets, name colours. It changes how chat looks, so it starts off. Ranks are hidden under Player messages.", "SkyHanni: Enable Chat Formatting")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .option(Option.text("chat_player_format.parts", "Part order", "With commas between them: level, island rank, island guest, name, guild rank, emblem.", "level, island rank, island guest, name, guild rank, emblem"))
                .option(Option.bool("chat_player_format.hide_brackets", "Hide level brackets", "", false))
                .option(Option.bool("chat_player_format.level_color", "Level colour on the name", "", false))
                .option(Option.bool("chat_player_format.ignore_youtube", "Keep the YouTube rank", "The rank hider leaves it alone.", false))
                .option(Option.bool("chat_player_format.same_color", "White messages", "All messages white, whatever the rank.", true)));
    }
}
