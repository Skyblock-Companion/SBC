// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.areas.FishHooks;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.combat.Combat;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni Year400Features, century celebration team finder: with a cake slice in hand, who is on that
// cake's team. skyhanni tints them, here they glow where you can see them
public final class Century {
    public static final String ID = "century_team_finder";

    enum CakeColor {
        PINK("SLICE_OF_STRAWBERRY_SHORTCAKE", 'd', "pink", 0xFFF150F1), BLUE("SLICE_OF_BLUEBERRY_CAKE", '9', "blue", 0xFF5555FF), YELLOW("SLICE_OF_CHEESECAKE", 'e', "yellow", 0xFFFFFF55),
        GREEN("SLICE_OF_GREEN_VELVET_CAKE", 'a', "green", 0xFF55FF55), RED("SLICE_OF_RED_VELVET_CAKE", 'c', "red", 0xFFFF5555);

        final String id, key;
        final char code;
        final int color;

        CakeColor(String id, char code, String key, int color) {
            this.id = id;
            this.code = code;
            this.key = key;
            this.color = color;
        }

        int chosen() {
            return Combat.color(ID, key, color);
        }

        static CakeColor ofItem(String id) {
            for (CakeColor c : values()) if (c.id.equals(id)) return c;
            return null;
        }
    }

    private static final Map<Integer, CakeColor> PLAYERS = new HashMap<>();
    private static CakeColor inHand;
    private static int lastClicked = -1;
    private static long lastClickedAt;

    private Century() {}

    // addPlayer: team colour after the cake mark in a name. gold is the extreme banker emblem (same symbol)
    // null if no mark or not a team's
    static CakeColor teamOf(String formattedName) {
        Matcher m = Patterns.get("SH.event.anniversary-celebration.400.team-finder.player-color-nametag").matcher(formattedName);
        if (!m.matches()) return null;
        char code = m.group("color").charAt(0);
        for (CakeColor c : CakeColor.values()) if (c.code == code) return c;
        return null;
    }

    private static final FishHooks.Scan SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            if (!Features.on(ID)) return false;
            var p = Minecraft.getInstance().player;
            inHand = p == null ? null : CakeColor.ofItem(Gain.sbId(p.getMainHandItem()));
            return inHand != null;
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof RemotePlayer p) || p.getUUID().version() == 2) return;
            if (!PLAYERS.containsKey(p.getId())) {
                CakeColor team = teamOf(LegacyText.of(p.getDisplayName(), true, false));
                if (team != null) PLAYERS.put(p.getId(), team);
            }
            CakeColor team = PLAYERS.get(p.getId());
            if (Combat.seen(p, 64, p.getBbHeight() * 0.8)) FishHooks.glow(p, (team == inHand ? inHand.chosen() : Combat.color(ID, "wrong", 0xFF4C4C4C)) & 0xFFFFFF);
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Events", "Century cake teams", "At the Century Celebration, with a slice of cake in hand, a glow on the players of that cake's team where you can see them, and a grey one on the rest.", "SkyHanni: Team Finder")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .defaultOff()
                .left(() -> {
                    PLAYERS.clear();
                    inHand = null;
                })
                .option(Option.color("century_team_finder.pink", "Pink team", "", 0xFFF150F1))
                .option(Option.color("century_team_finder.blue", "Blue team", "", 0xFF5555FF))
                .option(Option.color("century_team_finder.yellow", "Yellow team", "", 0xFFFFFF55))
                .option(Option.color("century_team_finder.green", "Green team", "", 0xFF55FF55))
                .option(Option.color("century_team_finder.red", "Red team", "", 0xFFFF5555))
                .option(Option.color("century_team_finder.wrong", "Everyone else", "", 0xFF4C4C4C)));
        FishHooks.scan(SCAN);
        Safe.attackEntity("Century.attackEntity", (player, level, hand, entity, hit) -> {
            if (level.isClientSide() && Features.on(ID) && inHand != null && entity instanceof RemotePlayer && entity.getUUID().version() != 2) {
                lastClicked = entity.getId();
                lastClickedAt = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });
        ChatBus.register(ChatBus.OBSERVE + 30, ID, f, line -> {
            if (System.currentTimeMillis() - lastClickedAt < 500 && lastClicked != -1 && Patterns.get("SH.event.anniversary-celebration.400.team-finder.player-full-message").matcher(GameState.strip(line.plain())).matches()) {
                PLAYERS.put(lastClicked, null);
                lastClicked = -1;
            }
            return ChatBus.PASS;
        });
    }
}
