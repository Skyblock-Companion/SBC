// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.misc;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni misc/FruitBowlFeatures.kt: with the fruit bowl in hand, which profile names it still misses +
// which you found twice
public final class FruitBowl {
    public static final String ID = "fruit_bowl";
    private static final Patterns.P CLICKED = Patterns.p("SH.misc.fruit-bowl.chat-message.clicked-player"), DUPLICATE = Patterns.p("SH.misc.fruit-bowl.chat-message.duplicate"),
            FOUND = Patterns.p("SH.misc.fruit-bowl.chat-message.found-missing"), ALL = Patterns.p("SH.misc.fruit-bowl.chat-message.found-all"),
            SEPARATOR = Patterns.p("SH.misc.fruit-bowl.item-lore.missing-names-separator"), NAME = Patterns.p("SH.misc.fruit-bowl.item-lore.missing-name-line");

    private static final Set<String> CLICKED_PLAYERS = new HashSet<>();
    private static final Map<String, Integer> DUPLICATES = new LinkedHashMap<>();
    private static Set<String> missing = Set.of();
    private static boolean inHand;
    private static String lastPlayer, lastProfile;
    private static int tick;
    private static long rereadAt;

    private FruitBowl() {}

    // from skyhanni updateNamesMissing: up to 20 lines after "Names missing:", each with one or more names
    static Set<String> missing(List<String> lore) {
        Set<String> out = new LinkedHashSet<>();
        boolean after = false;
        int seen = 0;
        for (String l : lore) {
            if (!after) {
                after = SEPARATOR.matcher(l).matches();
                continue;
            }
            if (++seen > 20) break;
            Matcher m = NAME.matcher(l);
            while (m.find()) if (!m.group("name").isEmpty()) out.add(m.group("name"));
        }
        return out;
    }

    // from skyhanni cleanPlayerName: "[MVP+] Name's" -> Name
    static String player(String text) {
        String t = text.replaceAll("\\[[^]]*] ", "").trim();
        return t.endsWith("'s") ? t.substring(0, t.length() - 2) : t;
    }

    private static void read(Minecraft mc) {
        var hand = mc.player == null ? null : mc.player.getMainHandItem();
        missing = hand == null || !"FRUIT_BOWL".equals(Gain.sbId(hand)) ? Set.of() : missing(LegacyText.lore(hand));
        inHand = !missing.isEmpty();
    }

    private static void tick(Minecraft mc) {
        if (++tick % 10 == 0) read(mc);
        if (rereadAt != 0 && System.currentTimeMillis() >= rereadAt) {
            rereadAt = 0;
            read(mc);
            CLICKED_PLAYERS.clear();
        }
    }

    private static ChatBus.Result chat(ChatBus.Line line) {
        if (!inHand) return ChatBus.PASS;
        String clean = line.plain().trim();
        if (ALL.matcher(LegacyText.of(line.component())).matches()) rereadAt = System.currentTimeMillis() + 500;
        Matcher m = CLICKED.matcher(clean);
        if (m.matches()) {
            lastPlayer = player(m.group("player"));
            lastProfile = m.group("profile");
        }
        if (DUPLICATE.matcher(clean).matches() && lastPlayer != null) {
            if (CLICKED_PLAYERS.add(lastPlayer)) DUPLICATES.merge(lastProfile, 1, Integer::sum);
            lastPlayer = null;
        }
        m = FOUND.matcher(clean);
        if (m.matches() && lastPlayer != null) {
            CLICKED_PLAYERS.add(lastPlayer);
            Set<String> left = new LinkedHashSet<>(missing);
            left.remove(lastProfile);
            missing = left;
        }
        return ChatBus.PASS;
    }

    private static RichCard.Content card(Set<String> names, Map<String, Integer> twice) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Missing names", "§a" + names.size()).icon("minecraft:melon_slice").raw().brief("Missing"));
        for (String n : names) out.add(RichCard.Line.row(n, "§7missing").raw());
        int total = 0;
        for (int v : twice.values()) total += v;
        out.add(RichCard.Line.row("Duplicate names", "§a" + total).raw().brief("Twice"));
        for (Map.Entry<String, Integer> e : twice.entrySet()) out.add(RichCard.Line.row(e.getKey(), "§a" + e.getValue() + "x").raw());
        return new RichCard.Content("", out);
    }

    private static final HudElement CARD = new RichCard(ID, "Fruit Bowl", "Misc", Zone.TOP_LEFT, 54, ID) {
        @Override public boolean visible() {
            return (GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV) && inHand && Features.get(ID).option("fruit_bowl.display").bool();
        }

        @Override protected Content live() {
            return card(missing, DUPLICATES);
        }

        @Override protected Content sample() {
            return card(new LinkedHashSet<>(List.of("Papaya", "Mango")), new LinkedHashMap<>(Map.of("Grapes", 2)));
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("Missing names", String.valueOf(missing.size()), Theme.current().good()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Missing names", "2", Theme.current().good()));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Events", "Fruit Bowl", "With the Fruit Bowl in hand: the profile names it still misses and the ones you found twice.", "SkyHanni: Fruit Bowl")
                .when(() -> GameState.inSkyBlock).tick(FruitBowl::tick).left(() -> inHand = false)
                .option(Option.bool("fruit_bowl.display", "Show the card", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 21, ID, f, FruitBowl::chat);
        HudLayout.add(CARD);
    }
}
