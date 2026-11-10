// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.value.ItemNbt;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// from skyhanni FlowstateHelper: flowstate pickaxe streak, the speed it gives, how long it holds and your best
// all of skyhannis lines stay on the card so their "Appearance" list has nothing left to pick
public final class Flowstate {
    static final String ID = "mining_flowstate";
    private static final long STREAK_FOR = 10_000;
    private static final int MAX_STREAK = 200;
    private static long streakEnd = MiningEnv.FAR_PAST;
    private static int streak;
    private static Integer level;
    private static String lastHeld = "";
    private static long nextHeld;

    private Flowstate() {}

    static int speedBonus(int streak, int level) {
        return Math.min(streak, MAX_STREAK) * level;
    }

    private static int best() {
        return MiningEnv.profile().flowstateBest;
    }

    private static void onMined(OreBlock original, Map<OreBlock, Integer> extra) {
        if (level == null || !Features.on(ID)) return;
        streakEnd = MiningEnv.now() + STREAK_FOR;
        for (int n : extra.values()) streak += n;
    }

    // skyhanni attemptClearDisplay: streak is over, maybe it was your best
    private static void endStreak() {
        int best = best();
        if (streak > best) {
            // no point saying new best if they never hit max speed before
            if (best > 200 && MiningPack.on(ID, "mining_flowstate.best_message")) MiningPack.chat("§d§lNEW FLOWSTATE BEST! §fStreak: " + streak + ". You beat your old best by " + (streak - best) + " blocks!");
            MiningEnv.profile().flowstateBest = streak;
        }
        streak = 0;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now >= nextHeld && mc.player != null) {
            nextHeld = now + 500;
            String held = MiningPack.heldId();
            if (!held.equals(lastHeld) || level == null) {
                lastHeld = held;
                var data = mc.player.getMainHandItem().get(DataComponents.CUSTOM_DATA);
                Map<String, Integer> enchants = data == null ? null : ItemNbt.getHypixelEnchantments(data.copyTag());
                level = enchants == null ? null : enchants.get("ultimate_flowstate");
            }
        }
        if (streak > 0 && MiningEnv.passedSince(streakEnd) >= 0) endStreak();
    }

    // streak doesnt survive an island change
    private static void leave() {
        streakEnd = MiningEnv.FAR_PAST;
        endStreak();
        level = null;
        lastHeld = "";
    }

    private static String timer(long left) {
        String code = !MiningPack.on(ID, "mining_flowstate.colorful") ? "§b" : left <= 2000 ? "§c" : left <= 4000 ? "§6" : left <= 6000 ? "§e" : left <= 8000 ? "§a" : "§2";
        return code + String.format(Locale.ROOT, "%.2fs", Math.max(0, left) / 1000.0);
    }

    private static List<RichCard.Line> lines(int streak, int level, long left, int best) {
        List<RichCard.Line> out = new ArrayList<>();
        String color = streak < MAX_STREAK ? "§e" : "§a";
        out.add(RichCard.Line.row("Time left", timer(left)).brief("Left"));
        out.add(RichCard.Line.row("Streak", color + streak + (streak < MAX_STREAK ? "§8/" + MAX_STREAK : "")));
        out.add(RichCard.Line.row("Speed", "§6+" + speedBonus(streak, level) + "⸕"));
        out.add(streak <= best ? RichCard.Line.row("Best", "§d" + best) : RichCard.Line.row("New best", color + streak));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Flowstate", "Mining", Zone.TOP_LEFT, 41, ID) {
        private long left() {
            return streakEnd - MiningEnv.now();
        }

        @Override
        public boolean visible() {
            if (!OreMined.customMining()) return false;
            if (level == null && left() <= 0) return false;
            int hide = MiningPack.number(ID, "mining_flowstate.auto_hide", 10);
            return hide < 0 || left() > STREAK_FOR - hide * 1000L;
        }

        @Override
        protected Content live() {
            return new Content("", lines(streak, level == null ? 0 : level, left(), best()));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(123, 3, 9710, 750));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Streak", streak + "/" + MAX_STREAK, 0), new Row("Speed", "+" + speedBonus(streak, level == null ? 0 : level), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Streak", "123/200", 0), new Row("Speed", "+369", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Flowstate", "While you mine with Flowstate: your streak, the speed it gives, how long it still holds and your best.", "SkyHanni: Flowstate Helper")
                .when(MiningPack::skyblock).tick(Flowstate::tick).left(Flowstate::leave)
                .option(Option.integer("mining_flowstate.auto_hide", "Hide after (s)", "Seconds after the last block; -1 never hides.", 10, -1, 10))
                .option(Option.bool("mining_flowstate.colorful", "Timer changes colour", "", false))
                .option(Option.bool("mining_flowstate.best_message", "Say a new best in chat", "", true)));
        OreMined.listen(Flowstate::onMined);
        HudLayout.add(CARD);
    }
}
