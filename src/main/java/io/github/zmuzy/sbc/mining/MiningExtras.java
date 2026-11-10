// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.Toasts;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

// the older mining hud stuff, fed by the skyhanni bits: running event (MiningEvents), powder this session
// (POWDER widget + sidebar deltas)
// corpses left to loot (FROZEN_CORPSES tab lines), metal detector distance, fossil progress, freezing warning
// (MiningApi cold)
public final class MiningExtras {
    static final Patterns.P CORPSE_TAB = Patterns.p("SH.mining.glacitemineshaft.tabwidgetcorpse");
    static final Patterns.P FOSSIL = Patterns.p("SH.mining.fossilexcavator.fossilprogress");
    static final Patterns.P TREASURE = Patterns.p("SH.mining.crystalnucleus.metaldetector.treasure");

    private static final Set<String> MINING_ISLANDS = Set.of("DWARVEN_MINES", "CRYSTAL_HOLLOWS", "MINESHAFT", "GOLD_MINES", "DEEP_CAVERNS");

    private static long sessionStart;
    private static String fossil = "";
    private static long fossilAt, lastCold;

    private MiningExtras() {}

    static boolean mining() {
        return MINING_ISLANDS.contains(GameState.island) || io.github.zmuzy.sbc.SbcMod.DEV;
    }

    public static void reset() {
        sessionStart = 0;
        fossil = "";
        Powder.reset();
        MiningEvents.reset();
    }

    // pure: one colorless chat line, true if it got used
    public static boolean feed(String plain) {
        Matcher m = FOSSIL.matcher(plain.trim());
        if (m.matches()) {
            fossil = m.group("progress");
            fossilAt = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    public static String event() {
        String l = MiningEvents.displayLine();
        return l == null ? "" : GameState.strip(l).replaceAll(" \\S+$", "").trim();
    }

    // powder this session per type (mithril, gemstone, glacite)
    public static long[] powder() {
        return new long[]{Powder.sessionGained.getOrDefault(Powder.Type.MITHRIL, 0L), Powder.sessionGained.getOrDefault(Powder.Type.GEMSTONE, 0L), Powder.sessionGained.getOrDefault(Powder.Type.GLACITE, 0L)};
    }

    // treasure distance from the action bar (skyhanni MetalDetectorSolver pattern), or -1
    public static double treasure() {
        return treasure(Vitals.actionBarRaw, System.currentTimeMillis() - Vitals.actionBarAt < 4000);
    }

    static double treasure(String actionBar, boolean fresh) {
        Matcher m = TREASURE.matcher(actionBar);
        if (!fresh || !m.find()) return -1;
        try {
            return Double.parseDouble(m.group("distance"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (mining()) feed(line.plain());
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        if (!mining()) return;
        if (sessionStart == 0 && (powder()[0] + powder()[1] + powder()[2]) > 0) sessionStart = System.currentTimeMillis();
        if (Features.on("mining_cold") && mc.player != null && MiningApi.inColdArea()) {
            int cold = MiningApi.cold;
            long now = System.currentTimeMillis();
            if (cold >= 80 && now - lastCold > 4000) {
                lastCold = now;
                Toasts.push("Too cold!", "Cold " + cold + " - warm up before 100", 0xFF8FD3FF);
                mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 0.7f);
            }
        }
    }

    // per hour since the sessions first powder, -1 till that means anything
    static double hourly(long n) {
        if (sessionStart == 0) return -1;
        double h = (System.currentTimeMillis() - sessionStart) / 3_600_000.0;
        return h < 0.02 ? -1 : n / h;
    }

    static String perHour(long n) {
        double r = hourly(n);
        return r < 0 ? "" : String.format(Locale.ROOT, "%,.0f/h", r);
    }

    // fossil progress while its recent, else ""
    static String freshFossil() {
        return System.currentTimeMillis() - fossilAt < 60_000 ? fossil : "";
    }

    static long fossilAgo() {
        return System.currentTimeMillis() - fossilAt;
    }

    public static final HudElement EVENT = MiningCards.event(), POWDER = MiningCards.powder(), CORPSES = MiningCards.corpses(), DIG = MiningCards.dig();

    public static void register() {
        Features.add(new Feature("mining_event", "Mining", "Mining event tracker", "The running Dwarven Mines / Crystal Hollows event with the time left (chat, tab widget or boss bar).", "").when(MiningExtras::mining));
        Features.add(new Feature("mining_powder", "Mining", "Powder this session", "Mithril, Gemstone and Glacite powder gained this session (from the Powders tab widget and the sidebar), with a per-hour rate.", "").when(MiningExtras::mining));
        Features.add(new Feature("mining_corpses", "Mining", "Mineshaft corpses", "Which corpses in a Glacite Mineshaft are still to loot (from the tab list).", "").when(() -> "MINESHAFT".equals(GameState.island)));
        Features.add(new Feature("mining_dig", "Mining", "Treasure distance and fossil progress", "The metal detector's distance from the action bar and the fossil excavation percentage.", "").when(MiningExtras::mining));
        Features.add(new Feature("mining_cold", "Mining", "Freezing warning", "A warning and a bell when Cold reaches 80 in the Glacite tunnels (Cold itself comes from the sidebar).", "").when(MiningExtras::mining).tick(MiningExtras::tick));
        ChatBus.register(ChatBus.OBSERVE + 13, "miningextras", null, MiningExtras::onChat);
        HudLayout.add(EVENT);
        HudLayout.add(POWDER);
        HudLayout.add(CORPSES);
        HudLayout.add(DIG);
    }
}
