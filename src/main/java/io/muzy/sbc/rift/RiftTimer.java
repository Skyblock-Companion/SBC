// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.events.hoppity.HoppityEnv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// port of skyhanni rift/everywhere/RiftTimer.kt: rift time from the action bar (RIFT_TIME: "§a7m 15sф"), the
// max seen, the percent + recent changes
public final class RiftTimer {
    // from skyhanni ActionBarStatsData.RIFT_TIME
    static final Patterns.P RIFT_TIME = Patterns.p("SH.actionbar.rift.time");
    static final Patterns.P NAMETAG = Patterns.p("SH.rift.everywhere.nametag.timer");

    static long maxTime, currentTime, latestTime;
    static final Map<Long, String> changes = new LinkedHashMap<>();
    static List<String> display = List.of();

    public static boolean showPercentage = true, showMaxTime = true;

    private RiftTimer() {}

    // from skyhanni onEntityDisplayName: name tag to show instead of "3150 ф", or null
    public static net.minecraft.network.chat.Component nameTag(net.minecraft.network.chat.Component name) {
        var f = io.muzy.sbc.feature.Features.get(Rift.TIMER);
        var o = f == null ? null : f.option("rift_timer.nametag");
        if (o == null || !o.bool() || !f.active()) return null;
        String text = nameTagText(name.getString());
        return text == null ? null : net.minecraft.network.chat.Component.literal(text);
    }

    static String nameTagText(String tag) {
        Matcher m = NAMETAG.matcher(tag);
        if (!m.matches()) return null;
        String icon = tag.substring(tag.lastIndexOf(' ') + 1);
        return HoppityEnv.format(Long.parseLong(m.group("time")) * 1000) + " §a" + icon;
    }

    public static void onWorldChange() {
        display = List.of();
        maxTime = latestTime = currentTime = 0;
        changes.clear();
    }

    // from skyhanni ActionBarStatsData: the bar gets split on runs of spaces and each part tried
    public static String riftTimeFrom(String actionBarFormatted) {
        for (String part : actionBarFormatted.split(" {3,}|\\s{2,}")) {
            Matcher m = RIFT_TIME.matcher(part.trim());
            // skyhannis SkyblockStat.RIFT_TIME icon is U+E020 (the "ф" glyph in hypixels font)
            if (m.matches() && part.indexOf('') >= 0) return m.group("riftTime").trim();
        }
        return null;
    }

    public static void onActionBar(String actionBarFormatted) {
        if (!isEnabled() || RiftApi.inRiftRace) return;
        String value = riftTimeFrom(actionBarFormatted);
        if (value == null) return;
        Long t = HoppityEnv.duration(value.replace("m", "m "));
        if (t == null) return;
        if (t > maxTime) maxTime = t;
        currentTime = t;
        update();
    }

    // from skyhanni onTick every 5 ticks during the race: xp level = seconds
    public static void onRaceTick(int experienceLevel) {
        if (!isEnabled() || !RiftApi.inRiftRace) return;
        currentTime = experienceLevel * 1000L;
        update();
    }

    private static void update() {
        if (currentTime != latestTime) {
            long diff = (currentTime - latestTime) + 1000;
            latestTime = currentTime;
            if (latestTime != maxTime) addDiff(diff);
        }
        String currentFormat = HoppityEnv.format(currentTime);
        String percentage = maxTime <= 0 ? "0%" : String.format(Locale.ROOT, "%.1f%%", currentTime * 100.0 / maxTime);
        String percentageFormat = showPercentage ? " §7(" + percentage + ")" : "";
        String maxTimeFormat = showMaxTime ? "§7/§b" + HoppityEnv.format(maxTime) : "";
        String color = currentTime <= 60_000 ? "§c" : currentTime <= 5 * 60_000 ? "§e" : "§b";
        List<String> out = new ArrayList<>();
        out.add("§eRift Timer: " + color + currentFormat + maxTimeFormat + percentageFormat);
        long now = RiftEnv.now();
        changes.keySet().removeIf(k -> now > k + 4_000);
        out.addAll(changes.values());
        display = out;
    }

    private static void addDiff(long diff) {
        String f;
        if (diff > 0) f = "§a+" + HoppityEnv.format(diff);
        else if (diff < 0) f = "§c-" + HoppityEnv.format(-diff);
        else return;
        changes.put(RiftEnv.now(), f);
    }

    public static List<String> display() {
        if (!isEnabled() || RiftApi.inMirrorVerse()) return List.of();
        return display;
    }

    public static boolean isEnabled() {
        return RiftApi.inRift();
    }
}
