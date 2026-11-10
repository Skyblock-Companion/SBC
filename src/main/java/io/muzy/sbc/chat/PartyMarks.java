// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.chat;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// outlines on players. from skyhanni PartyMemberOutlines (party members) and CenturyPartyInvitation (with the
// invite in hand, who has a level colour you still miss)
// outlines only on the players you can see, both start off
public final class PartyMarks {
    public static final String PARTY = "party_member_outlines", CENTURY = "century_party_invitation";
    private static final Patterns.P SEPARATOR = Patterns.p("SH.misc.century-party-invitation.item-lore.missing-color-separator"), MISSING = Patterns.p("SH.misc.century-party-invitation.item-lore.missing-color-line"),
            NAME_COLOR = Patterns.p("SH.misc.century-party-invitation.nametag.player-color"), ADDED = Patterns.p("SH.misc.century-party-invitation.chat-message.party-add");
    private static final Set<Character> needed = new HashSet<>();
    private static final Map<Integer, Character> playerColor = new HashMap<>();
    private static boolean inHand;
    private static int ticks, rereadAt = -1;

    private PartyMarks() {}

    private static int color(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(feature + "." + key);
        return o == null ? def : o.color();
    }

    // outline colour for the render state, or -1
    public static int glow(Entity e) {
        if (!(e instanceof Player p) || p instanceof net.minecraft.client.player.LocalPlayer) return -1;
        if (inHand && Features.on(CENTURY)) {
            Character c = playerColor.get(e.getId());
            if (c != null) return color(CENTURY, needed.contains(c) ? "can" : "cannot", needed.contains(c) ? 0xFF55FF55 : 0xFF4C4C4C) & 0xFFFFFF;
        }
        if (e instanceof RemotePlayer && Features.on(PARTY) && Party.members().contains(GameState.strip(p.getName().getString()))) return color(PARTY, "color", 0xFF55FF55) & 0xFFFFFF;
        return -1;
    }

    // updateColorsNeeded: colour codes under "Level colors missing:". pure
    static Set<Character> missing(List<String> lore) {
        Set<Character> out = new HashSet<>();
        boolean past = false;
        for (String line : lore) {
            if (!past) {
                past = SEPARATOR.matcher(line).matches();
                continue;
            }
            Matcher m = MISSING.matcher(line);
            if (m.matches()) out.add(m.group("color").charAt(0));
        }
        return out;
    }

    private static void century(Minecraft mc) {
        ticks++;
        if (mc.player == null || mc.level == null) return;
        ItemStack hand = mc.player.getMainHandItem();
        boolean holding = "CENTURY_PARTY_INVITATION".equals(Gain.sbId(hand));
        // item in hand changed, or it says something new after a chat line
        if (holding != inHand || ticks == rereadAt) {
            needed.clear();
            if (holding) needed.addAll(missing(LegacyText.lore(hand)));
            inHand = holding && !needed.isEmpty();
        }
        if (!inHand || ticks % 20 != 0) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            Matcher m = NAME_COLOR.matcher(LegacyText.of(p.getDisplayName(), true, false));
            if (m.matches()) playerColor.put(p.getId(), m.group("color").charAt(0));
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String message = LegacyText.of(line.component());
        if (ChatMatch.full("SH.misc.century-party-invitation.chat-message.found-all", message, line.plain()) != null && inHand) rereadAt = ticks + 10;
        Matcher m = ADDED.matcher(message);
        if (m.matches()) needed.remove(m.group("color").charAt(0));
        return ChatBus.PASS;
    }

    public static void register() {
        Features.add(new Feature(PARTY, "World", "Party member outlines", "Party members get an outline in the world, so you find them. Not in dungeons.", "SkyHanni: Highlight Party Members")
                .when(() -> (GameState.inSkyBlock || SbcMod.DEV) && !"CATACOMBS".equals(GameState.island)).defaultOff()
                .option(Option.color("party_member_outlines.color", "Outline colour", "", 0xFF55FF55)));
        Feature century = Features.add(new Feature(CENTURY, "Events", "Century Party Invitation", "With the invitation in hand, players with a level colour you still need get one outline and the others another.", "SkyHanni: Century Party Invitation")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff()
                .tick(PartyMarks::century)
                .left(() -> {
                    inHand = false;
                    needed.clear();
                    playerColor.clear();
                })
                .option(Option.color("century_party_invitation.can", "Can invite", "", 0xFF55FF55))
                .option(Option.color("century_party_invitation.cannot", "No need to invite", "", 0xFF4C4C4C)));
        ChatBus.register(ChatBus.OBSERVE + 18, CENTURY, century, PartyMarks::onChat);
    }
}
