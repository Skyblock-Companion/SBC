// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.diana;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// from skyhanni BurrowWarpHelper: when a hub warp is enough closer to the next burrow than you, suggest it
// ("Warp to Crypt") and warp on the key
// warp spots + extra walking blocks from the repo's Warps.json (warpLocation)
public final class BurrowWarpHelper {
    public enum WarpPoint {
        HUB, CASTLE, CRYPT, DA, MUSEUM, WIZARD, STONKS, TAYLOR;

        public boolean unlocked = true;

        private DianaData.Warp data() {
            DianaData.Warp w = DianaData.warps().get(name());
            if (w == null) throw new IllegalStateException("repo invalid for diana warp " + name());
            return w;
        }

        public String displayName() {
            return data().displayName();
        }

        public Vec3 location() {
            return data().pos();
        }

        private int extraBlocks() {
            return data().extraDianaWarpBlocks();
        }

        public double distance(Vec3 other) {
            return other.distanceTo(location()) + extraBlocks();
        }

        public boolean ignored() {
            for (String s : Diana.text(Diana.WARP, "ignoredWarps", "TAYLOR").split("[,\\s]+")) if (s.trim().equalsIgnoreCase(name())) return true;
            return false;
        }
    }

    public static WarpPoint currentWarp;
    private static long lastWarpTime = DianaEnv.FAR_PAST;
    private static WarpPoint lastWarp;
    private static long warpJailUntil = DianaEnv.FAR_PAST;
    private static boolean warpQueued;

    private BurrowWarpHelper() {}

    // hold the warp while the spade particles are still coming (PreciseGuessBurrow)
    public static void blockWarp(long durationMs) {
        long until = DianaEnv.now() + durationMs;
        if (until > warpJailUntil) warpJailUntil = until;
    }

    // overlay text (BurrowWarpHelper.onGuiRenderOverlay), null if nothing to show
    public static String overlayText(String keyName) {
        if (!Diana.warpEnabled()) return null;
        if (!DianaApi.isDoingDiana()) return null;
        WarpPoint warp = currentWarp;
        if (warp == null) return null;
        if (GriffinBurrowHelper.mobAlive) return null;
        String text = "§bWarp to " + warp.displayName();
        return keyName == null || keyName.isEmpty() ? text : text + " §7(§ePress " + keyName + "§7)";
    }

    // warp key pressed, bound in the parent's key handler
    public static void onKeyPress() {
        if (warpQueued) return;
        if (warpJailUntil > DianaEnv.now()) {
            GriffinBurrowHelper.addDebug("delaying warp for " + (warpJailUntil - DianaEnv.now()) + "ms");
            warpQueued = true;
            Delayed.run(warpJailUntil - DianaEnv.now(), BurrowWarpHelper::warp);
        } else warp();
    }

    private static void warp() {
        warpQueued = false;
        if (!DianaApi.isDoingDiana()) return;
        if (!Diana.warpEnabled()) return;
        if (DianaEnv.world.screenOpen()) return;
        WarpPoint warp = currentWarp;
        if (warp == null) return;
        if (DianaEnv.passedSince(lastWarpTime) < 1000) return;
        GriffinBurrowHelper.addDebug("warping to " + warp + " count of bezierFitter " + PreciseGuessBurrow.getBezierFitterCount());
        lastWarpTime = DianaEnv.now();
        DianaEnv.world.sendCommand("warp " + warp.name().toLowerCase(Locale.ROOT));
        lastWarp = currentWarp;
    }

    // BurrowWarpHelper.onChat: hypixel refused the warp right after we sent it -> that warp is locked
    public static void onChat(String message) {
        if (!message.equals("§cYou haven't unlocked this fast travel destination!")) return;
        if (DianaEnv.passedSince(lastWarpTime) > 1000) return;
        WarpPoint it = lastWarp;
        if (it == null) return;
        it.unlocked = false;
        DianaEnv.world.chat("Detected not having access to warp point §b" + it.displayName() + "§e!");
        DianaEnv.world.chat("Use §c/sbc resetburrowwarps §eonce you have activated this travel scroll, or add it to the ignored warps setting.");
        lastWarp = null;
        currentWarp = null;
    }

    public static void onWorldChange() {
        lastWarp = null;
        currentWarp = null;
    }

    // BurrowWarpHelper.shouldUseWarps: sets currentWarp when a warp is closer to the target by the set margin
    public static void shouldUseWarps(Vec3 target) {
        Vec3 playerLocation = DianaEnv.world.playerPos();
        WarpPoint warpPoint = getNearestWarpPoint(target);
        if (warpPoint == null) return;
        double playerDistance = playerLocation.distanceTo(target);
        double warpDistance = warpPoint.distance(target);
        double difference = playerDistance - warpDistance;
        boolean setWarpPoint = difference > Diana.n(Diana.WARP, "warpDistance", 10);
        currentWarp = setWarpPoint ? warpPoint : null;
    }

    public static WarpPoint getNearestWarpPoint(Vec3 location) {
        WarpPoint best = null;
        double bestD = Double.MAX_VALUE;
        for (WarpPoint p : WarpPoint.values()) {
            if (!p.unlocked || p.ignored()) continue;
            double d = p.distance(location);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static void resetUnlocked() {
        for (WarpPoint p : WarpPoint.values()) p.unlocked = true;
    }

    // drop the suggestion and any pending warp (tests, profile switch)
    public static void reset() {
        currentWarp = null;
        lastWarp = null;
        lastWarpTime = DianaEnv.FAR_PAST;
        warpJailUntil = DianaEnv.FAR_PAST;
        warpQueued = false;
    }

    public static List<String> debug(Vec3 target) {
        List<String> out = new ArrayList<>();
        out.add("target: " + target);
        out.add("playerLocation: " + DianaEnv.world.playerPos());
        WarpPoint w = getNearestWarpPoint(target);
        out.add("warpPoint: " + (w == null ? "none" : w.displayName()));
        if (w != null) {
            out.add("playerDistance: " + DianaEnv.world.playerPos().distanceTo(target));
            out.add("warpDistance: " + w.distance(target));
        }
        return out;
    }
}
