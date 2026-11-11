// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.combat.Combat;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.events.bingo.BingoCard;
import io.muzy.sbc.events.bingo.CompactBingoChat;
import io.muzy.sbc.events.bingo.MinionCrafts;
import io.muzy.sbc.events.carnival.Carnival;
import io.muzy.sbc.events.winter.Gifting;
import io.muzy.sbc.events.winter.Winter;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;

import java.util.regex.Matcher;

// bingo, carnival, jerry's workshop, gifts, great spook, century celebration. registered after Events bc some
// add to its cards
public final class EventPack {
    public static final String BOOP = "bingo_boop_party";

    private static final java.util.Map<String, java.util.regex.Pattern> PLAIN = new java.util.concurrent.ConcurrentHashMap<>();

    private EventPack() {}

    // a skyhanni pattern with colour codes stripped, for colourless text
    // how hypixel's lines look coloured depends on how they got cut into parts and the resets between, the words dont
    public static java.util.regex.Pattern plain(String key) {
        java.util.regex.Pattern from = io.muzy.sbc.core.Patterns.get(key);
        return PLAIN.computeIfAbsent(key + "\0" + from.pattern(), k -> java.util.regex.Pattern.compile(from.pattern().replaceAll("§\\[[^\\]]*\\]|§\\(\\?<\\w+>[^)]*\\)|§.", "")));
    }

    // from skyhanni PartyOnBoop: who just booped you from a dm, null for anything else or outgoing
    static String booper(String clean) {
        Matcher m = io.muzy.sbc.core.Patterns.get("SH.data.chat.player.privatemessage").matcher(clean);
        if (!m.matches() || !m.group("direction").equals("From")) return null;
        if (!io.muzy.sbc.core.Patterns.get("SH.bingo.boop.colorless").matcher(m.group("message")).matches()) return null;
        return m.group("author").trim();
    }

    // if the general switch is on too it already said it. one boop one line
    static boolean bingoOffers(boolean onBingo, boolean generalOn) {
        return onBingo && !generalOn;
    }

    public static void register() {
        BingoCard.register();
        MinionCrafts.register();
        CompactBingoChat.register();
        Carnival.register();
        Winter.register();
        io.muzy.sbc.events.winter.FrozenTreasure.register();
        Gifting.register();
        GreatSpook.register();
        Century.register();
        CakeCounter.register();
        Feature boop = Features.add(new Feature(BOOP, "Events", "Bingo: party on boop", "On a Bingo profile, when someone boops you, a click in chat to invite them to your party.", "SkyHanni: Boop Party, Only on Bingo")
                .when(() -> GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV)
                .defaultOff());
        ChatBus.register(ChatBus.OBSERVE + 32, BOOP, boop, line -> {
            if (bingoOffers(BingoCard.onBingo(), Features.on(io.muzy.sbc.chat.PartyCommands.BOOP))) io.muzy.sbc.chat.PartyCommands.offerBoop(GameState.strip(line.plain()));
            return ChatBus.PASS;
        });
    }
}
