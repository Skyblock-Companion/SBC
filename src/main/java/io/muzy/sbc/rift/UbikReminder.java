// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.events.hoppity.HoppityEnv;

import java.util.regex.Matcher;

// port of skyhanni rift/area/mountaintop/UbikReminder.kt: opening "Split or Steal" starts a 2h cooldown, the
// SPLIT! line sets it exact, chat says when ubiks cube is ready
public final class UbikReminder {
    static final Patterns.P INVENTORY = Patterns.p("SH.rift.ubik.inventory");
    static final Patterns.P COOLDOWN = Patterns.p("SH.rift.ubik.cooldown");

    // cooldown from opening split or steal, the chat line can say less
    static final long FULL_COOLDOWN = 2 * 3_600_000L;

    public static boolean onlyWhenReady;

    private UbikReminder() {}

    public static void onInventoryOpen(String inventoryName) {
        if (!INVENTORY.matcher(inventoryName).matches()) return;
        RiftEnv.profile().ubikRemindTime = RiftEnv.now() + FULL_COOLDOWN;
    }

    // from skyhanni onChat with the clean line
    public static boolean onChat(String clean) {
        if (!RiftApi.inRift()) return false;
        Matcher m = COOLDOWN.matcher(clean);
        if (!m.matches()) return false;
        Long d = HoppityEnv.duration(m.group("duration"));
        if (d != null) RiftEnv.profile().ubikRemindTime = RiftEnv.now() + d;
        return true;
    }

    // from skyhanni onSecondPassed (anywhere in skyblock)
    public static void onSecondPassed() {
        var storage = RiftEnv.profile();
        if (storage.ubikRemindTime > RiftEnv.now()) return;
        RiftEnv.world.chat("§aUbik's Cube is ready in the Rift!");
        storage.ubikRemindTime = RiftEnv.FAR_FUTURE;
    }

    // time left, -1 = ready
    static long millisLeft() {
        long remind = RiftEnv.profile().ubikRemindTime;
        return remind == RiftEnv.FAR_FUTURE ? -1 : Math.max(0, remind - RiftEnv.now());
    }

    // gui text, null = hidden
    public static String display() {
        long remind = RiftEnv.profile().ubikRemindTime;
        if (remind == RiftEnv.FAR_FUTURE) return "§aReady!";
        if (onlyWhenReady) return null;
        return "§e" + HoppityEnv.format(remind - RiftEnv.now());
    }
}
