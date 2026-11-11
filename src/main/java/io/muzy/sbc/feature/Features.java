// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.feature;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// registry of every feature + the guarded way to run their hooks. a hook that throws 5 times in 30 sec, or is
// slower than SLOW_MS 5 times in 30 sec, pauses its feature
public final class Features {
    private static final Map<String, Feature> ALL = new LinkedHashMap<>();
    private static final long WINDOW = 30_000;
    private static final int SLOW_MS = 25;
    private static final int STRIKES = 5;

    private Features() {}

    public static Feature add(Feature f) {
        ALL.put(f.id, f);
        return f;
    }

    public static Feature get(String id) {
        return ALL.get(id);
    }

    // on and in its place
    public static boolean on(String id) {
        Feature f = ALL.get(id);
        return f != null && f.active();
    }

    public static List<Feature> all() {
        return new ArrayList<>(ALL.values());
    }

    public static List<String> modules() {
        List<String> out = new ArrayList<>();
        for (Feature f : ALL.values()) if (!out.contains(f.module)) out.add(f.module);
        return out;
    }

    public static List<Feature> inModule(String m) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : ALL.values()) if (f.module.equals(m)) out.add(f);
        return out;
    }

    // runs one hook of a feature, never throws
    public static void guard(Feature f, Runnable r) {
        long t0 = System.nanoTime();
        try {
            r.run();
        } catch (Throwable t) {
            fail(f, t);
        }
        long dt = System.nanoTime() - t0;
        if (io.muzy.sbc.core.Perf.enabled) io.muzy.sbc.core.Perf.end("feat:" + f.id, t0);
        f.calls++;
        f.totalNs += dt;
        if (dt > f.maxNs) f.maxNs = dt;
        if (dt > SLOW_MS * 1_000_000L) strike(f, true, "took " + dt / 1_000_000 + " ms");
    }

    private static void fail(Feature f, Throwable t) {
        f.lastError = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        SbcMod.LOG.warn("Feature {} failed", f.id, t);
        strike(f, false, f.lastError);
    }

    private static void strike(Feature f, boolean slow, String why) {
        long now = System.currentTimeMillis();
        if (now - f.windowStart > WINDOW) {
            f.windowStart = now;
            f.errors = 0;
            f.slow = 0;
        }
        if (slow) f.slow++;
        else f.errors++;
        if (f.errors >= STRIKES || f.slow >= STRIKES) {
            f.paused = true;
            f.lastError = (slow ? "too slow: " : "failed: ") + why;
            SbcMod.LOG.warn("Feature {} paused ({})", f.id, f.lastError);
        }
    }

    // goes up every tick and on every switch, Feature.active keeps its answer till it does
    public static volatile long epoch;

    public static void changed() {
        if (epoch != 0) epoch++;
    }

    public static void tick(Minecraft mc) {
        epoch++;
        leaving(mc.level);
        for (Feature f : ALL.values()) if (f.onTick != null && f.active()) guard(f, () -> f.onTick.accept(mc));
    }

    private static Object lastLevel;

    // tells every feature that was active and isnt anymore, or whose world just changed
    static void leaving(Object level) {
        boolean changed = level != lastLevel;
        lastLevel = level;
        for (Feature f : ALL.values()) {
            if (f.onLeave == null) continue;
            boolean now = f.active();
            if (f.wasActive && (!now || changed)) guard(f, f.onLeave);
            f.wasActive = now;
        }
    }

    public static void chat(String line) {
        for (Feature f : ALL.values()) if (f.onChat != null && f.active()) guard(f, () -> f.onChat.accept(line));
    }

    private static ItemStack tipStack;
    private static int tipIn, tipKeys;
    private static long tipAt;
    private static List<Component> tipOut;

    // the game asks for a tooltip every frame while the mouse sits on an item. same item + same game lines + same
    // keys held = reuse the lines from a moment ago
    public static void tooltip(ItemStack stack, List<Component> lines) {
        long now = System.currentTimeMillis();
        int in = lines.hashCode(), keys = heldKeys();
        if (stack == tipStack && tipOut != null && in == tipIn && keys == tipKeys && now - tipAt < 250) {
            lines.clear();
            lines.addAll(tipOut);
            return;
        }
        for (Feature f : ALL.values()) if (f.onTooltip != null && f.active()) guard(f, () -> f.onTooltip.accept(stack, lines));
        tipStack = stack;
        tipIn = in;
        tipKeys = keys;
        tipAt = now;
        tipOut = new ArrayList<>(lines);
    }

    // shift, ctrl, alt bc tooltips change with them
    private static int heldKeys() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return (mc.hasShiftDown() ? 1 : 0) | (mc.hasControlDown() ? 2 : 0) | (mc.hasAltDown() ? 4 : 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    // features that draw in the world, kept so a frame doesnt walk every feature. rebuilt when one joins (ALL's size)
    private static Feature[] drawing = new Feature[0];
    private static int drawingFor = -1;

    public static void world(WorldDraw w) {
        if (drawingFor != ALL.size()) {
            java.util.List<Feature> out = new java.util.ArrayList<>();
            for (Feature f : ALL.values()) if (f.onWorld != null) out.add(f);
            drawing = out.toArray(new Feature[0]);
            drawingFor = ALL.size();
        }
        for (Feature f : drawing) if (f.onWorld != null && f.active()) guard(f, () -> f.onWorld.accept(w));
    }
}
