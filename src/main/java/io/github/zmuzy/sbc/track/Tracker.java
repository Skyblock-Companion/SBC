// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.track;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Store;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// measures what you really earn: purse gains, skill xp (action bar) + sb xp per activity (your island), only
// active time (afk ignored)
public final class Tracker {
    private static final io.github.zmuzy.sbc.core.Patterns.P SKILL_GAIN = io.github.zmuzy.sbc.core.Patterns.p("Tracker.SKILL_GAIN");
    private static final io.github.zmuzy.sbc.core.Patterns.P SB_XP = io.github.zmuzy.sbc.core.Patterns.p("Tracker.SB_XP");
    private static final io.github.zmuzy.sbc.core.Patterns.P WITHDRAW = io.github.zmuzy.sbc.core.Patterns.p("Tracker.WITHDRAW");

    public static Store.SessionRecord session;
    public static double sessionCoins, sessionSbXp;
    public static final Map<String, Double> sessionSkill = new HashMap<>();
    private static long lastPurse = -1;
    private static long lastTick, lastActive;
    private static Vec3 lastPos;
    private static boolean ignoreNextPurseJump;
    private static long lastWealthSample;

    private Tracker() {}

    public static boolean active() {
        return System.currentTimeMillis() - lastActive < 90_000;
    }

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (!GameState.inSkyBlock || mc.player == null) {
            lastTick = now;
            return;
        }
        if (session == null) {
            session = new Store.SessionRecord();
            session.start = now;
            Store.I.sessions.add(session);
            while (Store.I.sessions.size() > 200) Store.I.sessions.remove(0);
        }
        Vec3 pos = mc.player.position();
        if (lastPos == null || lastPos.distanceToSqr(pos) > 0.5) lastActive = now;
        lastPos = pos;

        long dt = lastTick == 0 ? 0 : Math.min(5_000, now - lastTick);
        lastTick = now;
        String activity = activity();
        if (active() && dt > 0) {
            Store.Rate r = rate(activity);
            r.activeMs += dt;
            session.activeMs += dt;
            session.islandMs.merge(GameState.island, dt, Long::sum);
            Store.I.islandTime.merge(GameState.island, dt, Long::sum);
            Store.I.totalActiveMs += dt;
            Store.markDirty();
        }
        session.end = now;

        long purse = GameState.purse;
        if (purse >= 0) {
            if (lastPurse >= 0 && purse > lastPurse) {
                long gain = purse - lastPurse;
                if (!ignoreNextPurseJump && gain < 50_000_000) {
                    rate(activity).coins += gain;
                    session.coins += gain;
                    sessionCoins += gain;
                    lastActive = now;
                }
                ignoreNextPurseJump = false;
            }
            lastPurse = purse;
        }
        if (now - lastWealthSample > 10 * 60_000 && purse >= 0) {
            lastWealthSample = now;
            Store.I.wealth.add(new long[]{now, purse, Math.max(0, GameState.bank)});
            while (Store.I.wealth.size() > 2000) Store.I.wealth.remove(0);
        }
    }

    // activity key for rates: the island, a few islands split by area
    private static String lastIsland = "";

    public static String activity() {
        String i = GameState.island;
        if (!i.isEmpty()) lastIsland = i;
        return lastIsland.isEmpty() ? "UNKNOWN" : lastIsland;
    }

    public static Store.Rate rate(String activity) {
        return Store.I.rates.computeIfAbsent(activity, k -> new Store.Rate());
    }

    public static void onChat(String plain) {
        if (WITHDRAW.matcher(plain).find()) ignoreNextPurseJump = true;
        Matcher m = SB_XP.matcher(plain);
        // mod summaries ("[SkyHanni] +9 SkyBlock XP (Attribute Levels)") repeat xp you already got, only count the
        // games own lines
        if (!plain.startsWith("[") && m.find()) {
            double xp = Double.parseDouble(m.group(1));
            rate(activity()).sbXp += xp;
            if (session != null) session.sbXp += xp;
            sessionSbXp += xp;
        }
    }

    private static String lastBar = "";
    private static long lastBarAt;

    public static void onActionBar(String plain) {
        Matcher m = SKILL_GAIN.matcher(plain);
        if (m.find()) {
            // hypixel resends the same gain every sec while its shown (health/mana around it change). the gain with its
            // "(1,234/5,000)" progress only repeats for the same gain
            long now = System.currentTimeMillis();
            int close = plain.indexOf(')', m.end());
            String key = close > 0 ? plain.substring(m.start(), close + 1) : m.group(0);
            if (key.equals(lastBar) && now - lastBarAt < 4000) return;
            lastBar = key;
            lastBarAt = now;
            double xp = Double.parseDouble(m.group(1).replace(",", ""));
            rate(activity()).skillXp.merge(m.group(2), xp, Double::sum);
            sessionSkill.merge(m.group(2), xp, Double::sum);
            lastActive = System.currentTimeMillis();
        }
    }
}
