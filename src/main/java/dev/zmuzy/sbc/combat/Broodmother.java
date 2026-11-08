// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.combat;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Toasts;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// broodmother from the tab list. from skyhanni BroodmotherFeatures + SpidersDenApi, the stage says how long
// till it spawns
public final class Broodmother {
    public static final String ID = "spider_broodmother";
    private static final Patterns.P WIDGET = Patterns.p("SH.tab.widgetcomponent.enum.broodmother"), TOP_OF_NEST = Patterns.p("SH.combat.spidersden.broodmother");

    // from skyhanni StageEntry
    enum Stage {
        SLAIN("§eSlain", 150_000), DORMANT("§eDormant", 120_000), SOON("§6Soon", 90_000), AWAKENING("§6Awakening", 60_000), IMMINENT("§4Imminent", 30_000), ALIVE("§4Alive!", 0);

        final String shown;
        final long ms;

        Stage(String shown, long ms) {
            this.shown = shown;
            this.ms = ms;
        }

        static Stage of(String tab) {
            try {
                return valueOf(tab.replace("!", "").trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static Stage current, last;
    private static long spawnAt;

    private Broodmother() {}

    private static boolean o(String k) {
        return Combat.opt(ID, k);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.player.tickCount % 10 != 0) return;
        for (String l : GameState.tab) {
            Matcher m = WIDGET.matcher(l);
            if (!m.matches()) continue;
            Stage s = Stage.of(m.group("stage"));
            if (s != null && s != current) {
                last = current;
                current = s;
                onStage();
                if (last == null) last = current;
            }
            return;
        }
    }

    private static void onStage() {
        if (last == null && o("stage_on_join") && !(current == Stage.ALIVE && o("spawn_alert"))) {
            Combat.say("§eThe Broodmother's current stage in this server is " + current.shown.replace("!", "") + "§e." + (current.ms != 0 ? " It will spawn §bwithin " + Combat.clock(current.ms) + "§e." : ""));
            // skyhanni stops here with no countdown till the next stage. this setting is on by default here so the card
            // starts right away
            if (current != Stage.ALIVE) spawnAt = System.currentTimeMillis() + current.ms;
            return;
        }
        // hypixel flips the stage back to imminent for a moment after it dies, ignore that
        if (current == Stage.IMMINENT && last == Stage.ALIVE) return;
        if (current == Stage.ALIVE) {
            spawnAt = 0;
            if (o("spawn_alert")) {
                Combat.title("§4Broodmother has spawned!", 40);
                for (int i = 0; i < 3; i++) Combat.pling(1f);
                Combat.sayClickable("§eThe Broodmother has spawned! §7Click to warp to the Top of the Nest.", "/warp nest");
            }
            return;
        }
        spawnAt = System.currentTimeMillis() + current.ms;
        if ((current == Stage.SOON || current == Stage.AWAKENING) && o("soon_alert")) {
            Toasts.push("Broodmother", GameState.strip(current.shown), 0xFFAA0000);
            Combat.pling(0.9f);
        }
        if (current == Stage.IMMINENT && o("imminent")) {
            Combat.pling(0.5f);
            Combat.say("§eThe Broodmother is §4Imminent§e! It will spawn in §b30 seconds§e!");
        } else if (current == Stage.SLAIN && o("slain") && !(o("hide_own_kill") && atTopOfNest())) {
            Combat.say("§eThe Broodmother was killed!");
        }
    }

    // from skyhanni SpidersDenApi.isAtTopOfNest
    private static boolean atTopOfNest() {
        for (String l : GameState.scoreboardFormatted) if (TOP_OF_NEST.matcher(l).matches()) return true;
        return false;
    }

    private static void left() {
        current = last = null;
        spawnAt = 0;
    }

    private static RichCard.Content card(String stage, long left) {
        return new RichCard.Content("", List.of(left > 0 ? RichCard.Line.row("Broodmother", stage + "  §b" + Combat.clock(left)).raw() : RichCard.Line.row("Broodmother", stage).raw()));
    }

    private static final HudElement CARD = new RichCard("spider_broodmother_card", "Broodmother", "Combat", Zone.TOP_LEFT, 44, ID) {
        @Override public boolean visible() {
            return current != null && o("countdown");
        }

        @Override public String heading() {
            return "";
        }

        @Override protected Content live() {
            if (current == null) return new Content("", List.of());
            long left = spawnAt - System.currentTimeMillis();
            return card(spawnAt != 0 && left <= 0 ? "§4spawning now!" : current.shown, spawnAt == 0 ? 0 : left);
        }

        @Override protected Content sample() {
            return card("§6Soon", 74_000);
        }

        @Override protected List<Row> classicRows() {
            return current == null ? List.of() : List.of(new Row("Broodmother", current.shown, Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Broodmother", "§6Soon", Modern.WARN));
        }
    };

    static void register() {
        Features.add(new Feature(ID, "Combat", "Broodmother", "Its stage and the time until it spawns, from the tab list, with alerts.", "SkyHanni: Broodmother").when(() -> Combat.island("SPIDER_DEN")).tick(Broodmother::tick).left(Broodmother::left)
                .option(Option.bool("spider_broodmother.countdown", "Countdown card", "", true))
                .option(Option.bool("spider_broodmother.spawn_alert", "Alert when it spawns", "A title and a sound, and a line you can click to warp.", true))
                .option(Option.bool("spider_broodmother.soon_alert", "Alert when soon or awakening", "", true))
                .option(Option.bool("spider_broodmother.imminent", "Warn when imminent", "", true))
                .option(Option.bool("spider_broodmother.slain", "Say when it was killed", "", true))
                .option(Option.bool("spider_broodmother.hide_own_kill", "Not when you were there", "", false))
                .option(Option.bool("spider_broodmother.stage_on_join", "Stage when you join a server", "", true)));
        HudLayout.add(CARD);
    }
}
