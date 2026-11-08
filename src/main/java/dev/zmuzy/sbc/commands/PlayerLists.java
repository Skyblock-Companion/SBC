// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.commands;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.chat.ChatMatch;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.events.diana.RareMobWaypointShare;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// who your friends and guild members are, read from what hypixel says when you ask. from skyhanni FriendApi
// (/f list) and GuildApi (/g list)
// kept per account, for tab complete
public final class PlayerLists {
    // from skyhanni FriendApi.rawNamePattern, on the hover text without colors
    private static final Pattern PROFILE_NAME = Pattern.compile("Click here to view (?<name>.*)'s profile");
    private static boolean inGuildMessage;
    private static final List<String> guildList = new ArrayList<>();

    private PlayerLists() {}

    private static String account() {
        var user = Minecraft.getInstance().getUser();
        return user == null || user.getProfileId() == null ? "" : user.getProfileId().toString();
    }

    private static Map<String, Boolean> homies() {
        return Saved.get().friends.computeIfAbsent(account(), k -> new LinkedHashMap<>());
    }

    public static List<String> friends(boolean onlyBest) {
        List<String> out = new ArrayList<>();
        homies().forEach((name, best) -> {
            if (!onlyBest || best) out.add(name);
        });
        return out;
    }

    public static List<String> guild() {
        return Saved.get().guilds.getOrDefault(account(), List.of());
    }

    private static String clean(String raw) {
        return RareMobWaypointShare.cleanPlayerName(raw);
    }

    static ChatBus.Result onChat(ChatBus.Line line) {
        String plain = line.plain();
        readFriendsList(line.component(), plain);
        feedFriendLines(LegacyText.of(line.component()), plain);
        List<String> guild = guildLine(plain);
        if (guild != null) {
            Saved.get().guilds.put(account(), guild);
            Saved.save();
        }
        return ChatBus.PASS;
    }

    // from skyhanni FriendApi.readFriendsList: every name in /f list runs /viewprofile and has the name on hover.
    // best friends are bold
    private static void readFriendsList(Component line, String plain) {
        if (!plain.contains("Friends")) return;
        boolean any = false;
        for (Component sibling : line.getSiblings()) {
            if (!(sibling.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run) || !run.command().startsWith("/viewprofile")) continue;
            if (!(sibling.getStyle().getHoverEvent() instanceof HoverEvent.ShowText hover)) continue;
            Matcher m = PROFILE_NAME.matcher(GameState.strip(hover.value().getString()));
            if (!m.find()) continue;
            String firstWord = LegacyText.of(sibling).split(" ")[0];
            homies().put(clean(m.group("name")), sibling.getStyle().isBold() || firstWord.contains("§l"));
            any = true;
        }
        if (any) Saved.save();
    }

    private static void feedFriendLines(String formatted, String plain) {
        Matcher m;
        if ((m = ChatMatch.full("SH.data.friends.add", formatted, plain)) != null) {
            homies().put(clean(m.group("name")), false);
            Saved.save();
        } else if ((m = ChatMatch.full("SH.data.friends.remove", formatted, plain)) != null) {
            homies().remove(clean(m.group("name")));
            Saved.save();
        } else if ((m = ChatMatch.full("SH.data.friends.removebest", formatted, plain)) != null) {
            homies().replace(clean(m.group("name")), false);
            Saved.save();
        } else if ((m = ChatMatch.full("SH.data.friends.addbest", formatted, plain)) != null) {
            homies().replace(clean(m.group("name")), true);
            Saved.save();
        }
    }

    // from skyhanni GuildApi.onChat on a colorless line. whole list once it just ended, else null
    static List<String> guildLine(String plain) {
        if (plain.startsWith("Guild Name: ")) {
            inGuildMessage = true;
            guildList.clear();
            return null;
        }
        if (plain.startsWith("Offline Members: ")) {
            inGuildMessage = false;
            guildList.clear();
            return null;
        }
        if (inGuildMessage && plain.startsWith("-----------------------------------------------------")) {
            inGuildMessage = false;
            List<String> out = new ArrayList<>(guildList);
            guildList.clear();
            return out;
        }
        if (inGuildMessage && plain.contains("●")) {
            for (String word : plain.split("●")) if (!word.isBlank()) guildList.add(clean(word));
        }
        return null;
    }
}
