// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.areas;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.events.hoppity.HoppityEnv;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.rift.CruxTalismanDisplay;
import io.github.zmuzy.sbc.rift.EnigmaSoulWaypoints;
import io.github.zmuzy.sbc.rift.PunchcardHighlight;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.rift.RiftApi;
import io.github.zmuzy.sbc.rift.RiftBloodEffigies;
import io.github.zmuzy.sbc.rift.RiftMotesOrb;
import io.github.zmuzy.sbc.rift.UbikReminder;
import io.github.zmuzy.sbc.rift.VerminTracker;
import io.github.zmuzy.sbc.rift.WoodenButtonsHelper;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// rift summary panel, fed by the skyhanni rift port (rift/): motes orbs picked up, effigies broken
// (RiftBloodEffigies), vermin vacuumed (VerminTracker)...
public final class RiftExtras {
    static final Patterns.P MOTES_ORB = Patterns.p("SH.rift.everywhere.motesorb");
    static final Patterns.P BUTTONS = Patterns.p("SH.rift.area.dreadfarm.buttons.hit");
    static final Patterns.P ENIGMA = Patterns.p("SH.rift.everywhere.enigma-souls.found");
    static final Patterns.P BACTE = Patterns.p("SH.rift.colosseum.bacte.chat.name");
    static final Patterns.P RACE_FINISH = Patterns.p("SH.rift.area.westvillage.riftrace.finish");
    private static final java.util.regex.Pattern BUTTONS_COUNT = java.util.regex.Pattern.compile("(\\d+)/56");
    private static final java.util.regex.Pattern RACE_TIME = java.util.regex.Pattern.compile("(\\d+:\\d+\\.\\d+)");

    private static int enigma, buttonsHit;
    private static String lastRace = "", bacte = "";
    private static boolean pb;
    private static long raceStart;

    private RiftExtras() {}

    private static boolean rift() {
        return "THE_RIFT".equals(GameState.island) || io.github.zmuzy.sbc.SbcMod.DEV;
    }

    public static void reset() {
        enigma = buttonsHit = 0;
        lastRace = bacte = "";
        pb = false;
        raceStart = 0;
    }

    static int enigma() {
        return enigma;
    }

    static int buttonsHit() {
        return buttonsHit;
    }

    // one formatted chat line (skyhanni patterns are formatted). true = used
    public static boolean feed(String formatted) {
        Matcher m;
        if (ENIGMA.matcher(GameState.strip(formatted).trim()).matches()) {
            if (!formatted.contains("already")) enigma++;
            return true;
        }
        if (BUTTONS.matcher(formatted).matches()) {
            m = BUTTONS_COUNT.matcher(GameState.strip(formatted));
            if (m.find()) buttonsHit = Integer.parseInt(m.group(1));
            return true;
        }
        if ((m = BACTE.matcher(formatted)).matches()) {
            bacte = GameState.strip(m.group("name"));
            return true;
        }
        if ((m = RACE_FINISH.matcher(formatted)).matches()) {
            Matcher t = RACE_TIME.matcher(GameState.strip(formatted));
            lastRace = t.find() ? t.group(1) : "";
            pb = formatted.contains("PERSONAL BEST");
            raceStart = 0;
            return true;
        }
        return false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (rift()) {
            feed(io.github.zmuzy.sbc.core.LegacyText.of(line.component()));
            if (RiftApi.inRiftRace && raceStart == 0) raceStart = System.currentTimeMillis();
            if (!RiftApi.inRiftRace && lastRace.isEmpty()) raceStart = 0;
        }
        return ChatBus.PASS;
    }

    // everything the panel can say, so the editor sample goes through the same layout as live numbers
    private record State(String area, int motes, int enigma, Map<String, Integer> vermin, boolean stillgore, List<RiftBloodEffigies.View> effigies, int broken,
                         int buttons, int punches, int tracked, String bacte, long raceSeconds, String lastRace, boolean pb) {}

    // same meaning as the world markers: red = break it, yellow = respawning soon, grey = unknown
    private static int effigyColor(RiftBloodEffigies.View v) {
        return switch (v.state()) {
            case NOT_BROKEN -> Modern.BAD;
            case UNKNOWN -> Modern.DIM;
            case BROKEN -> v.soon() ? Modern.WARN : Modern.GOOD;
        };
    }

    private static String verminIcon(String plainName) {
        return switch (plainName) {
            case "Flies" -> "minecraft:bee_spawn_egg";
            case "Spiders" -> "minecraft:spider_spawn_egg";
            default -> "minecraft:silverfish_spawn_egg";
        };
    }

    private static RichCard.Content panel(State s) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.section("Progress"));
        if (s.stillgore()) {
            out.add(RichCard.Line.row("Effigies broken", Modern.fraction(String.valueOf(s.broken()), "6", s.broken() == 6 ? 'a' : 'c', 'f')).icon("minecraft:redstone").noMeter().brief("Effigies"));
            List<RichCard.Seg> pips = new ArrayList<>();
            long next = -1;
            boolean soon = false;
            for (RiftBloodEffigies.View v : s.effigies()) {
                pips.add(new RichCard.Seg(1, effigyColor(v)));
                if (v.respawnIn() >= 0 && (next < 0 || v.respawnIn() < next)) {
                    next = v.respawnIn();
                    soon = v.soon();
                }
            }
            out.add(RichCard.Line.segments("Effigies broken", 2, pips));
            if (next >= 0) out.add(RichCard.Line.row("Next respawn", (soon ? "§6" : "§b") + HoppityEnv.format(next)).raw().brief("Respawn"));
        }
        if (s.buttons() > 0) out.add(RichCard.Line.row("Wooden buttons", Modern.fraction(String.valueOf(s.buttons()), "56", 'a', 'f')).icon("minecraft:oak_button").brief("Buttons"));
        if (s.punches() > 0) out.add(RichCard.Line.row("Punches", Modern.fraction(String.valueOf(s.punches()), "20", 'd', 'f')).icon("minecraft:paper"));
        out.add(RichCard.Line.section("Found this session"));
        if (s.motes() > 0) out.add(RichCard.Line.row("Motes orbs", String.valueOf(s.motes()), 0xFFFF7FE0).icon("minecraft:amethyst_shard").raw().brief("Motes"));
        if (s.enigma() > 0) out.add(RichCard.Line.row("Enigma souls", String.valueOf(s.enigma()), 0xFFC77DFF).icon("minecraft:purple_dye").raw().brief("Enigma"));
        for (Map.Entry<String, Integer> e : s.vermin().entrySet()) out.add(RichCard.Line.row(e.getKey(), String.format(Locale.ROOT, "%,d", e.getValue()), Modern.GOLD).icon(verminIcon(e.getKey())).raw());
        out.add(RichCard.Line.section("Activity"));
        if (s.tracked() > 0) out.add(RichCard.Line.row("Souls with a waypoint", String.valueOf(s.tracked()), 0xFFC77DFF).icon("minecraft:ender_eye").raw().brief("Waypoints"));
        if (!s.bacte().isEmpty()) out.add(RichCard.Line.row("Bacte", s.bacte(), Modern.LABEL).icon("minecraft:slime_ball").raw());
        if (s.raceSeconds() >= 0) out.add(RichCard.Line.row("Race", s.raceSeconds() + "s", Modern.AQUA).key("Last race").icon("minecraft:leather_boots").chip("RACING", Modern.AQUA).raw());
        else if (!s.lastRace().isEmpty()) {
            RichCard.Line race = RichCard.Line.row("Last race", s.lastRace(), s.pb() ? Modern.GOOD : Modern.LABEL).icon("minecraft:leather_boots").raw();
            out.add(s.pb() ? race.chip("PB", Modern.GOOD) : race);
        }
        return new RichCard.Content(s.area().isEmpty() ? "" : "§d" + s.area(), out);
    }

    public static final HudElement RIFT = new RichCard("rift_panel", "The Rift", "Rift", Zone.TOP_LEFT, 51, "rift_panel") {
        @Override
        public boolean visible() {
            return rift() && (RiftMotesOrb.orbsPicked() > 0 || !VerminTracker.session().isEmpty() || buttonsHit > 0 || enigma > 0 || PunchcardHighlight.count() > 0 || RiftApi.inStillgoreChateau() || !lastRace.isEmpty());
        }

        @Override
        protected Content live() {
            Map<String, Integer> vermin = new LinkedHashMap<>();
            for (var e : VerminTracker.session().entrySet()) vermin.put(GameState.strip(e.getKey().vermin), e.getValue());
            long race = RiftApi.inRiftRace && raceStart > 0 ? (System.currentTimeMillis() - raceStart) / 1000 : -1;
            return panel(new State(GameState.area, RiftMotesOrb.orbsPicked(), enigma, vermin, RiftApi.inStillgoreChateau(), RiftBloodEffigies.views(), RiftBloodEffigies.brokenCount(), buttonsHit, PunchcardHighlight.count(), EnigmaSoulWaypoints.tracked().size(), bacte + (io.github.zmuzy.sbc.rift.RiftColosseum.phase() > 0 ? " " + io.github.zmuzy.sbc.rift.RiftColosseum.phase() + "/5" : ""), race, lastRace, pb));
        }

        @Override
        protected Content sample() {
            Map<String, Integer> vermin = new LinkedHashMap<>();
            vermin.put("Flies", 3);
            vermin.put("Spiders", 1);
            vermin.put("Silverfish", 27);
            List<RiftBloodEffigies.View> effigies = new ArrayList<>();
            for (int i = 0; i < 4; i++) effigies.add(new RiftBloodEffigies.View(RiftBloodEffigies.EffigyState.BROKEN, (9 + i) * 60_000L, false));
            effigies.add(new RiftBloodEffigies.View(RiftBloodEffigies.EffigyState.BROKEN, 132_000L, true));
            effigies.add(new RiftBloodEffigies.View(RiftBloodEffigies.EffigyState.NOT_BROKEN, -1, false));
            return panel(new State("Stillgore Château", 12, 3, vermin, true, effigies, 5, 38, 14, 4, "Bacterium", -1, "00:32.794", true));
        }

        @Override
        public List<Content> states() {
            List<RiftBloodEffigies.View> unknown = new ArrayList<>();
            for (int i = 0; i < 6; i++) unknown.add(new RiftBloodEffigies.View(RiftBloodEffigies.EffigyState.UNKNOWN, -1, false));
            return List.of(panel(new State("Stillgore Château", 0, 0, Map.of(), true, unknown, 0, 0, 0, 0, "", -1, "", false)),
                    panel(new State("West Village", 2, 0, Map.of(), false, List.of(), 0, 56, 20, 0, "", 23, "", false)));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            if (RiftMotesOrb.orbsPicked() > 0) out.add(new Row("Motes orbs", String.valueOf(RiftMotesOrb.orbsPicked()), 0xFFFF55FF));
            if (RiftApi.inStillgoreChateau()) {
                int broken = RiftBloodEffigies.brokenCount();
                out.add(new Row("Effigies broken", broken + "/6", broken == 6 ? Theme.current().good() : Theme.current().bad()));
            }
            for (var e : VerminTracker.session().entrySet()) out.add(new Row(GameState.strip(e.getKey().vermin), String.valueOf(e.getValue()), Theme.current().text()));
            if (buttonsHit > 0) out.add(new Row("Wooden buttons", buttonsHit + "/56", buttonsHit >= 56 ? Theme.current().good() : Theme.current().accent()));
            if (enigma > 0) out.add(new Row("Enigma souls", String.valueOf(enigma), 0xFFAA00AA));
            if (PunchcardHighlight.count() > 0) out.add(new Row("Punches", PunchcardHighlight.count() + "/20", PunchcardHighlight.count() >= 20 ? Theme.current().bad() : Theme.current().text()));
            if (!bacte.isEmpty()) out.add(new Row("Bacte", bacte, Theme.current().dim()));
            if (RiftApi.inRiftRace && raceStart > 0) out.add(new Row("Race", ((System.currentTimeMillis() - raceStart) / 1000) + "s", Theme.current().accent()));
            else if (!lastRace.isEmpty()) out.add(new Row("Last race", lastRace + (pb ? "  PB" : ""), pb ? Theme.current().good() : Theme.current().dim()));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Motes orbs", "12", 0xFFFF55FF), new Row("Effigies broken", "5/6", Theme.current().bad()), new Row("Silverfish", "27", 0), new Row("Punches", "14/20", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature("rift_panel", "Rift", "Rift panel", "Motes orbs, effigies, vermin, wooden buttons, enigma souls, punchcards, Bacte and Gunther's race, in one panel.", "").when(RiftExtras::rift).left(RiftExtras::reset));
        ChatBus.register(ChatBus.OBSERVE + 18, "rift_panel", f, RiftExtras::onChat);
        HudLayout.add(RIFT);
        WoodenButtonsHelper.hitCount(); // keep the dependency obvious: the helpers count is per spot, the panels is the chat total
    }
}
