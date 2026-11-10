// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.visitor;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.events.hoppity.HoppityEnv;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenData;
import io.muzy.sbc.garden.GardenItems;
import io.muzy.sbc.garden.milestones.GardenCropSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;

// from skyhanni GardenVisitorTimer: when the next visitor comes, how much sooner while farming, and a guess
// for the 6th one hypixel says nothing about ("Queue Full!")
// goes into the Next visitor line of the visitors card
public final class VisitorTimer {
    private static final long FIVE_MINUTES = 5 * 60_000L;

    private static long lastMillis, sixthArrival, lastSixthWarning, lastTimerUpdate;
    private static String lastTimerValue = "";
    private static int lastVisitorCrew = -1;
    private static boolean visitorJustArrived;
    private static long lastSessionBlocks;
    private static String shown = "";
    private static Object lastLevel;

    private VisitorTimer() {}

    private static boolean option(String key, boolean otherwise) {
        Feature f = Features.get("garden_visitors");
        if (f == null) return otherwise;
        Option o = f.option(key);
        return o == null ? otherwise : o.bool();
    }

    // card line given what the tab says
    public static String shown(String fromTab) {
        return shown.isEmpty() ? fromTab : shown;
    }

    static void arrived() {
        visitorJustArrived = true;
    }

    // what an update comes to: line text + whether to warn
    public record Result(String text, long millis, boolean warnSixth, boolean ping) {}

    // processUpdates. millis = what the tab says, or the interval when the queue is full. farming = blocks/s
    // while farming, else 0
    static Result compute(int visitors, long millis, boolean queueFull, long sixthLeft, long sinceTimerUpdate, double farming, boolean sixthEnabled) {
        boolean warn = false;
        if (queueFull) {
            millis = sixthLeft;
            if (millis < 0) {
                visitors++;
                warn = true;
            }
        }
        long since = sinceTimerUpdate - 100;
        if (visitors < 5 && since >= 500 && since <= 60_000) millis -= since;
        long adjusted = farming > 0 ? millis / 3 : millis;
        String extra = farming > 0 ? "/" + GardenItems.time((long) (adjusted * (farming / 20))) : "";
        String next = queueFull && (!sixthEnabled || millis < 0) ? "Queue Full!" : GardenItems.time(millis) + extra;
        return new Result(next, millis, warn, adjusted < 10_000);
    }

    static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            // onWorldChange
            lastVisitorCrew = -1;
            long stored = GardenData.get().nextSixthVisitorArrival;
            if (stored > System.currentTimeMillis()) sixthArrival = stored;
            lastMillis = sixthArrival - System.currentTimeMillis();
        }
        if (!Visitors.garden()) return;
        // CropClickEvent: every crop brings the 6th visitor 0.1s closer
        long blocks = GardenCropSpeed.sessionBlocks;
        if (blocks > lastSessionBlocks) {
            long gained = (blocks - lastSessionBlocks) * 100;
            sixthArrival -= gained;
            if (lastMillis > FIVE_MINUTES) lastTimerUpdate -= gained;
        }
        lastSessionBlocks = blocks;
        update(mc);
    }

    // a pest kill the same way: 30s
    static ChatBus.Result onChat(ChatBus.Line line) {
        if (!Visitors.garden()) return ChatBus.PASS;
        if (Patterns.get("SH.garden.pests-api.chat.pest-death").matcher(LegacyText.of(line.component())).matches()) {
            sixthArrival -= 30_000;
            if (lastMillis > FIVE_MINUTES) lastTimerUpdate -= 30_000;
        }
        return ChatBus.PASS;
    }

    private static void update(Minecraft mc) {
        Matcher m = null;
        for (String l : GameState.tab) {
            Matcher t = Patterns.get("SH.garden.visitor.timer.time.new.colorless").matcher(l);
            if (t.matches()) {
                m = t;
                break;
            }
        }
        if (m == null) {
            shown = "";
            return;
        }
        String info = m.group("info");
        if (info.equals("Not Unlocked!")) {
            shown = "";
            return;
        }
        GardenData data = GardenData.get();
        long now = System.currentTimeMillis();
        int visitors = Visitors.visitorsInTabList(GameState.tab).size();
        boolean full = info.equals("Queue Full!");
        long millis = data.visitorInterval;
        if (!full) {
            if (!lastTimerValue.equals(info)) {
                lastTimerUpdate = now;
                lastTimerValue = info;
            }
            Long parsed = HoppityEnv.duration(info);
            if (parsed != null) millis = parsed;
        }
        if (lastVisitorCrew != -1 && visitors - lastVisitorCrew == 1) {
            if (!full) {
                if (data.visitorInterval != millis) Store.markDirty();
                data.visitorInterval = millis;
            } else sixthArrival = now + data.visitorInterval;
        }
        if (full) {
            if (visitorJustArrived && visitors - lastVisitorCrew == 1) {
                sixthArrival = now + data.visitorInterval;
                visitorJustArrived = false;
            }
            long left = sixthArrival - now;
            data.nextSixthVisitorArrival = now + left + data.visitorInterval * (5 - visitors);
        }
        double farming = GardenCropSpeed.getRecentBPS();
        Result r = compute(visitors, millis, full, sixthArrival - now, now - lastTimerUpdate, farming, option("garden_visitors.sixth", true));
        if (r.warnSixth() && option("garden_visitors.sixth_warning", true)) warnSixth(mc, now);
        if (r.millis() == lastMillis && visitors == lastVisitorCrew) return;
        lastMillis = r.millis();
        lastVisitorCrew = visitors;
        shown = r.text();
        if (r.ping() && option("garden_visitors.new_ping", false) && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1f);
    }

    private static void warnSixth(Minecraft mc, long now) {
        // dont warn right on world switch
        if (Visitors.sinceWorldSwitch() < 3000 || now - lastSixthWarning < 2 * 60_000L) return;
        lastSixthWarning = now;
        if (mc.gui != null && mc.gui.hud != null) mc.gui.hud.setTitle(Component.literal("§a6th Visitor Ready"));
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1f);
    }
}
