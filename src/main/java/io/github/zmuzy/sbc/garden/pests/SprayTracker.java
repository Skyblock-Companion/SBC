// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.pests;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.CookieBuff;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.garden.milestones.GardenMilestones;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni SprayDisplay + the spray half of GardenPlotApi: which spray is on your plot and for how long,
// from the sprayonator's chat line (length by tool, SprayonatorType) and the tab's "Spray:" line, + a chat
// line when one runs out
public final class SprayTracker {
    public static final String ID = "garden_spray";
    private static final Set<String> SPRAYS = Set.of("Compost", "Plant Matter", "Dung", "Honey Jar", "Tasty Cheese", "Jelly", "Moondew");
    private static String lastHeldSprayonator = "";
    private static long nextRead;
    private static String lastIsland = "";

    private SprayTracker() {}

    private static Store.GardenProfile store() {
        return GardenMilestones.profile();
    }

    // plot you stand on as the sidebar says it, null = none
    static String currentPlot(List<String> scoreboardFormatted) {
        for (String l : scoreboardFormatted) {
            Matcher m = Patterns.get("SH.garden.pests-api.scoreboard.plot.pests").matcher(l);
            if (m.matches()) return GameState.strip(m.group("plot")).trim();
            m = Patterns.get("SH.garden.pests-api.scoreboard.plot.no-pests").matcher(l);
            if (m.matches()) return GameState.strip(m.group("plot")).trim();
        }
        return null;
    }

    private static void set(String plot, String spray, long until) {
        Store.GardenProfile s = store();
        s.sprayExpiry.put(plot, until);
        s.sprayType.put(plot, spray);
        s.sprayNotified.remove(plot);
        Store.markDirty();
    }

    private static void remove(String plot) {
        Store.GardenProfile s = store();
        s.sprayExpiry.remove(plot);
        s.sprayType.remove(plot);
        s.sprayNotified.remove(plot);
        Store.markDirty();
    }

    // minutes for "SPRAYONATOR! You sprayed Plot - 6 with Compost!": what you hold or held last, 30 if unknown (BASIC)
    static int minutes(String held, String lastHeld) {
        int now = GardenPack.sprayonatorMinutes(held);
        if (now > 0) return now;
        int last = GardenPack.sprayonatorMinutes(lastHeld);
        return last > 0 ? last : 30;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!GardenPack.garden()) return ChatBus.PASS;
        String clean = GameState.strip(line.plain());
        Matcher m = Patterns.get("SH.garden.plot.spray.target.colorless").matcher(clean);
        if (m.matches() && SPRAYS.contains(m.group("spray"))) {
            set(m.group("plot"), m.group("spray"), System.currentTimeMillis() + minutes(GardenPack.heldId(), lastHeldSprayonator) * 60_000L);
        } else if (Patterns.get("SH.garden.plot.spray.cleared.portablewasher-nocolor").matcher(clean).matches()) {
            for (String plot : new ArrayList<>(store().sprayExpiry.keySet())) remove(plot);
        }
        return ChatBus.PASS;
    }

    // tab line for your plot, it overrides what we thought
    private static void readTab(long now) {
        String plot = currentPlot(GameState.scoreboardFormatted);
        if (plot == null) return;
        for (String l : GameState.tab) {
            Matcher m = Patterns.get("SH.garden.plot.tablist.spraytime-nocolor").matcher(l.trim());
            if (!m.matches()) continue;
            String name = m.group("spray").trim(), time = m.group("time");
            Long had = store().sprayExpiry.get(plot);
            if (!SPRAYS.contains(name)) {
                if (had != null) remove(plot);
                return;
            }
            long left = time == null ? -1 : CookieBuff.parse(time);
            if (left < 0) return;
            // sprayMessageEligible: tab value is new if its another spray or way longer than what we knew
            String hadType = store().sprayType.get(plot);
            boolean fresh = had == null || !name.equals(hadType) || had <= now + left - 10 * 60_000L;
            if (GardenPack.on(ID, "new_spray") && fresh && left >= 60_000L) {
                GardenPack.chat("§aPlot §7- §b" + plot + " §7was sprayed with §a" + name + "§7!");
                GardenPack.chat("§7This will expire in §a" + SbTime.dur(left) + "§7!");
            }
            set(plot, name, now + left);
            return;
        }
    }

    // sendExpiredPlotsToChat
    static String expiredLine(List<String> plots, boolean wasAway) {
        return (wasAway ? "§7While you were away, your " : "§7Your ") + (plots.size() == 1 ? "spray" : "sprays") + " on §aPlot §7- §b" + String.join("§7, §b", plots) + " §7expired.";
    }

    private static void notifyExpired(long now, boolean wasAway) {
        Store.GardenProfile s = store();
        List<String> done = new ArrayList<>();
        for (var e : s.sprayExpiry.entrySet()) if (e.getValue() <= now && s.sprayNotified.add(e.getKey())) done.add(e.getKey());
        if (done.isEmpty()) return;
        Store.markDirty();
        if (GardenPack.on(ID, "expiry")) GardenPack.chat(expiredLine(done, wasAway));
    }

    static boolean comingBack() {
        return lastIsland.isEmpty();
    }

    static void beenHereForTest() {
        lastIsland = "GARDEN";
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        String held = GardenPack.heldId();
        if (GardenPack.sprayonatorMinutes(held) > 0) lastHeldSprayonator = held;
        boolean arrived = !GameState.island.equals(lastIsland) && "GARDEN".equals(GameState.island);
        lastIsland = GameState.island;
        long now = System.currentTimeMillis();
        if (now < nextRead && !arrived) return;
        nextRead = now + 1000;
        readTab(now);
        notifyExpired(now, arrived);
    }

    // SprayFeatures: the plot you stand on so you know what you're spraying
    private static void draw(io.github.zmuzy.sbc.render.WorldDraw w) {
        if (!GardenPack.on(ID, "border") || GardenPack.sprayonatorMinutes(GardenPack.heldId()) <= 0 || w.mc.player == null) return;
        double x = Math.floor((w.mc.player.getX() + 240) / 96) * 96 - 240, z = Math.floor((w.mc.player.getZ() + 240) / 96) * 96 - 240;
        w.box(new net.minecraft.world.phys.AABB(x, 64, z, x + 96, 78, z + 96), 0xFFFFFF55, false, false);
    }

    private static List<RichCard.Line> lines(String spray, String left) {
        List<RichCard.Line> out = new ArrayList<>();
        if (spray == null) out.add(RichCard.Line.row("Spray", "§cNot sprayed"));
        else {
            out.add(RichCard.Line.row("Spray", "§a" + spray));
            out.add(RichCard.Line.row("Left", "§b" + left));
        }
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Plot spray", "Garden", Zone.TOP_LEFT, 31, ID) {
        private String plot() {
            return currentPlot(GameState.scoreboardFormatted);
        }

        @Override
        public boolean visible() {
            String p = plot();
            if (!GardenPack.garden() || p == null) return false;
            Long until = store().sprayExpiry.get(p);
            return until != null && until > System.currentTimeMillis() || GardenPack.on(ID, "not_sprayed");
        }

        @Override
        protected Content live() {
            String p = plot();
            Long until = p == null ? null : store().sprayExpiry.get(p);
            long now = System.currentTimeMillis();
            if (until == null || until <= now) return new Content(p == null ? "" : "§7Plot " + p, lines(null, ""));
            return new Content("§7Plot " + p, lines(store().sprayType.get(p), SbTime.dur(until - now)));
        }

        @Override
        protected Content sample() {
            return new Content("§7Plot 6", lines("Honey Jar", "24m 18s"));
        }

        @Override
        protected List<Row> classicRows() {
            String p = plot();
            Long until = p == null ? null : store().sprayExpiry.get(p);
            return until == null ? List.of(new Row("Not sprayed")) : List.of(new Row(store().sprayType.get(p), SbTime.dur(until - System.currentTimeMillis()), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Honey Jar", "24m 18s", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Plot spray", "Which spray is on the plot you stand on and how long it lasts, and a chat line when a spray runs out on any plot.", "SkyHanni: Spray Display")
                .when(GardenPack::garden).tick(SprayTracker::tick).world(SprayTracker::draw).left(() -> {
                    // next garden visit counts as coming back
                    if (!GardenPack.garden()) lastIsland = "";
                })
                .option(Option.bool("garden_spray.border", "Show the plot with a Sprayonator", "Its border in the world while one is in your hand.", true))
                .option(Option.bool("garden_spray.expiry", "Say when a spray runs out", "In chat, for any plot.", true))
                .option(Option.bool("garden_spray.new_spray", "Say when a new spray is found", "In chat, when you enter a plot with one you did not know of.", false))
                .option(Option.bool("garden_spray.not_sprayed", "Show on plots without one", "The card also says so when this plot is not sprayed.", false)));
        ChatBus.register(ChatBus.OBSERVE + 16, ID, f, SprayTracker::onChat);
        HudLayout.add(CARD);
    }
}
