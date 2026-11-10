// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import io.muzy.sbc.core.Patterns;

// from skyhanni HoppityCallWarning: when hoppity rings the abiphone ("✆ Hoppity ✆") the pling repeats every
// 100ms for 7s until you pick up ("[NPC] Hoppity: ✆ What's up, ...?")
public final class HoppityCallWarning {
    static final Patterns.P INIT_CALL = Patterns.p("SH.misc.chocolatefactory.hoppity.call.init.colorless");
    static final Patterns.P PICKUP_CALL = Patterns.p("SH.misc.chocolatefactory.hoppity.call.pickup.colorless");
    private static final long CALL_LENGTH = 7000;

    private static boolean ringRing;
    private static long nextWarningTime, finalWarningTime;

    private HoppityCallWarning() {}

    public static boolean active() {
        return ringRing;
    }

    // stripped line
    public static void onChat(String plain) {
        if (!isEnabled()) return;
        if (INIT_CALL.matcher(plain).matches()) startWarningUser();
        if (PICKUP_CALL.matcher(plain).matches()) stopWarningUser();
    }

    // every tick (skyhanni repeats from onSecondPassed with a 100ms repeat)
    public static void onTick() {
        if (!isEnabled() || !ringRing) return;
        long now = HoppityEnv.now();
        if (now >= nextWarningTime) {
            HoppityEnv.world.playSound(sound(), 1f);
            nextWarningTime = now + 100;
        }
        if (now >= finalWarningTime) stopWarningUser();
    }

    public static void onWorldChange() {
        stopWarningUser();
    }

    private static String sound() {
        return Hoppity.text(Hoppity.CALL, "sound", "block.note_block.pling");
    }

    // onGuiRenderOverlay: screen colour rn, 0 = none
    public static int flashColor(long now) {
        if (!ringRing || !isEnabled()) return 0;
        int alpha = (int) Math.max(0, Math.min(255, (2 + Math.sin(now / 1000.0)) * 255 / 4));
        return alpha << 24 | (Hoppity.color(Hoppity.CALL, "flash_color", 0x7F00EEFF) & 0xFFFFFF);
    }

    private static void startWarningUser() {
        if (ringRing) return;
        ringRing = true;
        long now = HoppityEnv.now();
        HoppityEnv.world.playSound(sound(), 1f);
        nextWarningTime = now + 100;
        finalWarningTime = now + CALL_LENGTH;
        HoppityEnv.world.title("§d✆ Hoppity is calling!");
    }

    private static void stopWarningUser() {
        ringRing = false;
        finalWarningTime = 0;
        nextWarningTime = 0;
    }

    private static boolean isEnabled() {
        return HoppityEnv.world.inSkyBlock() && Hoppity.callEnabled();
    }
}
