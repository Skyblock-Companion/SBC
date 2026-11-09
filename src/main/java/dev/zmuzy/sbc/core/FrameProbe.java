// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.core;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

// /sbc perf record: times every frame part by part, plus what was on screen and in memory
// hooks only add up nanoTime while recording. the report itself is FrameReport
public final class FrameProbe {
    // the parts of a frame, in the order the game runs them. the indented ones sit inside the one above
    public static final int POLL = 0, PACKETS = 1, TASKS = 2, TICK = 3, ENTITY_TICK = 4, PARTICLE_TICK = 5, EXTRACT = 6, WORLD_EXTRACT = 7,
            ENTITY_EXTRACT = 8, PARTICLE_EXTRACT = 9, GUI_EXTRACT = 10, HUD_PICTURE = 11, RENDER = 12, WORLD_RENDER = 13, CHUNK_COMPILE = 14,
            FEATURE_PREPARE = 15, FEATURE_DRAW = 16, GUI_DRAW = 17, PRESENT = 18, LIMITER = 19, MOD = 20, PARTS = 21;
    public static final String[] NAMES = {"poll events", "packets", "tasks", "game tick", "  entity tick", "  particle tick", "extract",
            "  world extract", "    entity extract", "    particle extract", "  gui extract", "    hud picture redraw", "render", "  world render",
            "    chunk compile", "    feature prepare", "    feature draw", "  gui draw", "present (gpu)", "frame limiter wait", "mod (timed parts)"};
    // top level parts: what is left of a frame after them is "other"
    static final int[] TOP = {POLL, PACKETS, TASKS, TICK, EXTRACT, RENDER, PRESENT, LIMITER};

    // what a frame counted
    public static final int C_ENTITIES = 0, C_PLAYERS = 1, C_STANDS = 2, C_TICKED = 3, C_TICKS = 4, C_GUI = 5, C_TEXT = 6, C_ITEMS = 7, COUNTS = 8;
    public static final String[] COUNT_NAMES = {"entities drawn", "players drawn", "armor stands drawn", "entities ticked", "game ticks", "gui pieces", "texts", "gui items"};

    public static volatile boolean on;
    private static Thread owner;
    private static final long[] start = new long[PARTS], acc = new long[PARTS];
    private static final int[] count = new int[COUNTS];
    private static long frameStart, allocStart, endAt;
    private static boolean hudRedrawn;

    // the recording, one row per frame
    static int frames;
    static float[] total, alloc;
    static float[][] parts;
    static int[][] counts;
    static long[] at;
    static byte[] flags;
    static final int F_HUD = 1, F_SCREEN = 2, F_GC = 4;
    static long startedAt, gcBefore;
    static final List<FrameReport.Second> SECONDS = new ArrayList<>();
    private static long nextSecond;
    private static long[] gcCountAtFrame = new long[0];

    private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private FrameProbe() {}

    public static boolean recording() {
        return on;
    }

    public static void start(int seconds) {
        int max = Math.max(1, seconds) * 1200;
        total = new float[max];
        alloc = new float[max];
        parts = new float[PARTS][max];
        counts = new int[COUNTS][max];
        at = new long[max];
        flags = new byte[max];
        frames = 0;
        SECONDS.clear();
        owner = null;
        frameStart = 0;
        startedAt = System.nanoTime();
        endAt = startedAt + seconds * 1_000_000_000L;
        nextSecond = startedAt;
        gcBefore = gcCount();
        dev.zmuzy.sbc.ui.HudTexture.resetShare();
        on = true;
    }

    public static void stop() {
        on = false;
    }

    static long gcCount() {
        long n = 0;
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) n += Math.max(0, b.getCollectionCount());
        return n;
    }

    private static boolean mine() {
        Thread t = Thread.currentThread();
        if (owner == null) owner = t;
        return t == owner;
    }

    public static void enter(int part) {
        if (!on || !mine()) return;
        start[part] = System.nanoTime();
    }

    public static void exit(int part) {
        if (!on || !mine() || start[part] == 0) return;
        acc[part] += System.nanoTime() - start[part];
        start[part] = 0;
    }

    public static void count(int what, int n) {
        if (on && mine()) count[what] += n;
    }

    public static void hudRedrawn() {
        if (on) hudRedrawn = true;
    }

    public static void mod(long ns) {
        if (on && mine()) acc[MOD] += ns;
    }

    // start of every frame (Minecraft.runTick), writes down the one before
    public static void frame() {
        if (!on || !mine()) return;
        long now = System.nanoTime();
        long allocNow = THREADS.getCurrentThreadAllocatedBytes();
        if (frameStart != 0 && frames < total.length) {
            int i = frames++;
            total[i] = (now - frameStart) / 1e6f;
            alloc[i] = (allocNow - allocStart) / 1024f;
            for (int p = 0; p < PARTS; p++) parts[p][i] = acc[p] / 1e6f;
            for (int c = 0; c < COUNTS; c++) counts[c][i] = count[c];
            at[i] = frameStart - startedAt;
            byte f = 0;
            if (hudRedrawn) f |= F_HUD;
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() != null) f |= F_SCREEN;
            long gc = gcCount();
            if (gc != gcBefore) f |= F_GC;
            gcBefore = gc;
            flags[i] = f;
        }
        java.util.Arrays.fill(acc, 0);
        java.util.Arrays.fill(count, 0);
        hudRedrawn = false;
        frameStart = now;
        allocStart = allocNow;
        if (now >= nextSecond) {
            nextSecond = now + 1_000_000_000L;
            try {
                SECONDS.add(FrameReport.Second.now((now - startedAt) / 1_000_000_000L));
            } catch (Throwable t) {
                Safe.caught("FrameProbe.second", t);
            }
        }
        if (now >= endAt || frames >= total.length) {
            on = false;
            FrameReport.write();
        }
    }
}
