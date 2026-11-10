// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events;

import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.areas.FishHooks;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.core.data.DataFiles;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.regex.Matcher;

// the great spook. from skyhanni TheGreatSpook (time to next primal fear minus 3s per fear point, fear stat,
// time left) and skyblocker MathTeacherHelper (answer in the chat line with a click that sends it)
// also SpookyChestAlert's title
public final class GreatSpook {
    public static final String TIMERS = "spook_timers";
    private static final long FEAR_COOLDOWN = 6 * 60_000;

    private static long start = -1, end = -1;
    private static long nextMob;
    private static boolean notified;

    private GreatSpook() {}

    // repo's Events.json
    private static void loadRange() {
        if (start != -1) return;
        start = end = 0;
        JsonObject o = DataFiles.object(DataFiles.Source.SKYHANNI, "Events.json");
        if (o == null || !o.has("great_spook")) return;
        JsonObject g = o.getAsJsonObject("great_spook");
        if (g.has("start_time")) start = g.get("start_time").getAsLong();
        if (g.has("end_time")) end = g.get("end_time").getAsLong();
    }

    // isGreatSpookActive
    static boolean active() {
        if (SbcMod.DEV) return true;
        loadRange();
        long now = System.currentTimeMillis();
        return now >= start && now <= end;
    }

    // cooldown minus 3s per fear point, negative once a fear can come
    static long untilFear(long nextMobAt, double fear, long now) {
        return nextMobAt - (long) (3_000 * fear) - now;
    }

    private static double fear() {
        String v = Vitals.stats.get("Fear");
        if (v == null) return 0;
        try {
            return Double.parseDouble(v.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void lines(EventSheet s, boolean known, long untilFear, String fearStat, long left) {
        if (known && Combat.opt(TIMERS, "primal_timer")) {
            if (untilFear > 0) s.line("Next fear in", SbTime.dur(untilFear), Modern.AQUA).icon("wither_rose").brief("Next fear");
            else s.line("Primal Fear", "Ready", EventSheet.EPIC).icon("wither_rose");
        }
        if (fearStat != null && Combat.opt(TIMERS, "fear_stat")) s.line("Fear", fearStat, EventSheet.EPIC);
        if (Combat.opt(TIMERS, "time_left")) {
            if (left > 0) s.line("Time left", SbTime.dur(left), Modern.AQUA);
            else s.line("The Great Spook", "Ended", Modern.DIM).brief("Spook");
        }
    }

    private static final HudElement CARD = new EventSheet.Card(TIMERS, "Great Spook timers", "Events", Zone.TOP_LEFT, 48, TIMERS) {
        @Override
        public boolean visible() {
            return (GameState.inSkyBlock || SbcMod.DEV) && active();
        }

        @Override
        protected void live(EventSheet s) {
            long now = System.currentTimeMillis();
            lines(s, nextMob > 0, untilFear(nextMob, fear(), now), Vitals.stats.get("Fear"), end - now);
        }

        @Override
        protected void sample(EventSheet s) {
            lines(s, true, 150_000, "120", 3 * 86_400_000L + 4 * 3_600_000L);
        }

        @Override
        protected List<Row> plainRows() {
            long now = System.currentTimeMillis();
            List<Row> out = new java.util.ArrayList<>();
            if (nextMob > 0 && Combat.opt(TIMERS, "primal_timer")) {
                long left = untilFear(nextMob, fear(), now);
                out.add(new Row("Next fear in", left > 0 ? SbTime.dur(left) : "Ready", 0));
            }
            if (Vitals.stats.get("Fear") != null && Combat.opt(TIMERS, "fear_stat")) out.add(new Row("Fear", Vitals.stats.get("Fear"), 0));
            if (Combat.opt(TIMERS, "time_left")) out.add(new Row("Time left", end > now ? SbTime.dur(end - now) : "Ended", 0));
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Next fear in", "2m 30s", 0));
        }
    };

    private static void tick(Minecraft mc) {
        if (nextMob == 0 || notified || !Combat.opt(TIMERS, "primal_notify")) return;
        if (untilFear(nextMob, fear(), System.currentTimeMillis()) <= 0) {
            FishHooks.pling();
            notified = true;
        }
    }

    // SpookyChestAlert: chest name as a title in the event colour
    static String chestTitle(String chest, boolean party, boolean compact) {
        String color = party ? "§l§c" : "§l§6";
        return color + (compact ? (party ? "PARTY" : "SPOOKY") + " CHEST" : chest.toUpperCase(java.util.Locale.ROOT) + "!");
    }

    static void chest(String chest, boolean party) {
        Combat.title(chestTitle(chest, party, Combat.opt("spook_alerts", "compact_title")), 40);
        if (Combat.opt("spook_alerts", "sound")) FishHooks.pling();
    }

    // MathTeacherHelper.onMessage: the line with its answer + a click that says it in chat
    private static ChatBus.Result maths(ChatBus.Line line) {
        if (ChatBus.replaying || !Combat.opt("spook_maths", "click")) return ChatBus.PASS;
        Matcher m = Patterns.get("SH.event.greatspook.chat.math.colorless").matcher(GameState.strip(line.plain()));
        if (!m.matches()) return ChatBus.PASS;
        String answer = Events.solve(m.group("math"));
        if (answer.isEmpty()) return ChatBus.PASS;
        Component out = line.component().copy().append(" = ").append(Component.literal("§b" + answer)).append(" ").append(Component.literal("§a[Click to send]").withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent.RunCommand("/ac " + answer)).withHoverEvent(new HoverEvent.ShowText(Component.literal("Says the answer in all chat")))));
        return new ChatBus.Replace(out);
    }

    public static void register() {
        Features.add(new Feature(TIMERS, "Events", "Great Spook timers", "While the Great Spook is on, the time to the next Primal Fear, your Fear, and how long the event has left. For Fear, have the Stats widget with Fear on in your tab list.", "SkyHanni: The Great Spook")
                .when(() -> (GameState.inSkyBlock || SbcMod.DEV) && active())
                .tick(GreatSpook::tick)
                .option(Option.bool("spook_timers.primal_timer", "Next Primal Fear", "", true))
                .option(Option.bool("spook_timers.primal_notify", "Sound when one can come", "", true))
                .option(Option.bool("spook_timers.fear_stat", "Your Fear", "", true))
                .option(Option.bool("spook_timers.time_left", "Time left", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 29, "great spook", null, line -> {
            if (GameState.inSkyBlock && active() && Patterns.get("SH.event.greatspook.mob.spawn.colorless").matcher(GameState.strip(line.plain())).matches()) {
                nextMob = System.currentTimeMillis() + FEAR_COOLDOWN;
                notified = false;
            }
            return ChatBus.PASS;
        });
        ChatBus.register(ChatBus.COMPACT + 18, "quick maths answer", Features.get("spook_maths"), GreatSpook::maths);
        HudLayout.add(CARD);
    }
}
