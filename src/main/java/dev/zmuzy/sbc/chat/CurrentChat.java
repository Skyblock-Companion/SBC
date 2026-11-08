// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni CurrentChatDisplay: with chat open a card shows which channel you type into, party size, how
// long a dm has been going
// channel is remembered between sessions like there
public final class CurrentChat {
    public static final String ID = "chat_current";
    private static final Patterns.P PRIVATE_LINE = Patterns.p("SH.data.chat.player.privatemessage");
    private static final String KEY = "chat_current.channel";
    private static final long PRIVATE_TIME = 5 * 60_000L;

    private static String channel;
    private static String privateWith;
    private static long privateEnds;

    private CurrentChat() {}

    private static String channel() {
        if (channel == null) {
            Object saved = Config.I.options.get(KEY);
            channel = saved == null ? "" : String.valueOf(saved);
        }
        return channel;
    }

    private static void set(String value, String with) {
        channel = value;
        privateWith = with;
        Config.I.options.put(KEY, value);
        Config.save();
    }

    // onChat with colours: {channel, who with} when the channel changed, else null. pure
    static String[] change(String formatted) {
        String plain = GameState.strip(formatted);
        Matcher m = ChatMatch.full("SH.chat.current-chat.changed", formatted, plain);
        if (m != null) return new String[]{m.group("chat").toUpperCase(Locale.ROOT), null};
        if (ChatMatch.full("SH.chat.current-chat.all", formatted, plain) != null) return new String[]{"ALL", null};
        m = ChatMatch.full("SH.chat.current-chat.private.open", formatted, plain);
        if (m == null) return null;
        // without colours the rank is still in the name
        String name = GameState.strip(m.group("player")).trim();
        return new String[]{"PRIVATE", name.substring(name.lastIndexOf(' ') + 1)};
    }

    private static void feed(String formatted, String plain, long now) {
        String[] c = change(formatted);
        if (c != null) {
            if (c[1] != null) privateEnds = now + PRIVATE_TIME;
            set(c[0], c[1]);
            return;
        }
        // onPrivateMessageChat: talking keeps the dm open
        Matcher m;
        m = PRIVATE_LINE.matcher(plain.trim());
        if (m.matches() && "PRIVATE".equals(channel) && m.group("author").trim().equals(privateWith)) privateEnds = now + PRIVATE_TIME;
    }

    private static String name(String channel) {
        return switch (channel) {
            case "ALL" -> "§eAll";
            case "PARTY" -> "§9Party";
            case "GUILD" -> "§2Guild";
            case "OFFICER" -> "§3Officer";
            case "SKYBLOCK CO-OP" -> "§bCo-op";
            default -> channel;
        };
    }

    private static String left(long ms) {
        long s = Math.max(0, ms / 1000);
        return s >= 60 ? s / 60 + "m " + s % 60 + "s" : s + "s";
    }

    static RichCard.Content content(String channel, String with, long leftMs, int partySize) {
        List<RichCard.Line> out = new ArrayList<>();
        if (channel.equals("PRIVATE")) {
            out.add(RichCard.Line.row("Talking to", with == null ? "§cUnknown" : "§6" + with).raw().brief("To"));
            out.add(RichCard.Line.row("Ends in", leftMs <= 0 ? "§cExpired" : "§b" + left(leftMs)).raw().brief("Ends"));
            return new RichCard.Content("§dPrivate", out);
        }
        out.add(RichCard.Line.row("Channel", name(channel)).raw());
        if (channel.equals("PARTY")) out.add(RichCard.Line.row("Party", partySize == 0 ? "§cNot in one" : "§a" + (partySize + 1) + " members").raw());
        return new RichCard.Content("", out);
    }

    public static final HudElement CARD = new RichCard(ID, "Current chat", "Chat", Zone.BOTTOM_LEFT, 6, ID) {
        @Override
        public boolean visible() {
            return Minecraft.getInstance().gui.screen() instanceof ChatScreen && !channel().isEmpty();
        }

        @Override
        protected Content live() {
            return content(channel(), privateWith, privateEnds - System.currentTimeMillis(), Party.members().size());
        }

        @Override
        protected Content sample() {
            return content("PARTY", null, 0, 3);
        }

        @Override
        public List<Content> states() {
            return List.of(content("ALL", null, 0, 0), content("PARTY", null, 0, 0), content("PRIVATE", "Blaze_Hunter", 251_000, 0), content("PRIVATE", "Blaze_Hunter", 0, 0));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            String c = channel();
            if (c.equals("PRIVATE")) {
                long ms = privateEnds - System.currentTimeMillis();
                out.add(new Row("Talking to", privateWith == null ? "§cUnknown" : "§6" + privateWith, 0));
                out.add(new Row("Ends in", ms <= 0 ? "§cExpired" : "§b" + left(ms), 0));
                return out;
            }
            out.add(new Row("Chat", name(c), Modern.AQUA));
            int size = Party.members().size();
            if (c.equals("PARTY")) out.add(new Row("Party", size == 0 ? "§cNot in one" : "§a" + (size + 1) + " members", 0));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Chat", "§9Party", 0), new Row("Party", "§a4 members", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Chat", "Current chat", "While chat is open: which channel you are typing in.", "SkyHanni: Current Chat Display").when(() -> true));
        // always reads the channel so its right the moment the card gets turned on
        ChatBus.register(ChatBus.OBSERVE + 17, ID, null, line -> {
            feed(LegacyText.of(line.component()), line.plain(), System.currentTimeMillis());
            return ChatBus.PASS;
        });
        HudLayout.add(CARD);
    }
}
