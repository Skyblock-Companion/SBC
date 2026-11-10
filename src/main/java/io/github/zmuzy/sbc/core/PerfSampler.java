// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// samples the main thread ~500 times a sec while the perf overlay is on
// the overlay only times what we wrapped, this also catches the game drawing our cards and gpu waits
// written to perf-sample.txt next to perf-report.txt
public final class PerfSampler {
    private static final long EVERY_MS = 2, WRITE_EVERY_MS = 15_000;
    private static volatile Thread target;
    private static Thread worker;
    private static final Object LOCK = new Object();
    private static final Map<String, int[]> SELF = new HashMap<>(), INCLUSIVE = new HashMap<>(), OURS_ENTRY = new HashMap<>(), OURS_LEAF = new HashMap<>();
    private static int samples, ours, waiting;
    private static long startedAt, writtenAt;

    private PerfSampler() {}

    // called every frame on the game thread, starts and stops with the overlay
    public static void frame(boolean on) {
        if (on && worker == null) start(Thread.currentThread());
        else if (!on && worker != null) stop();
        else if (on && System.currentTimeMillis() - writtenAt > WRITE_EVERY_MS) write();
    }

    private static void start(Thread game) {
        synchronized (LOCK) {
            SELF.clear();
            INCLUSIVE.clear();
            OURS_ENTRY.clear();
            OURS_LEAF.clear();
            samples = ours = waiting = 0;
            startedAt = writtenAt = System.currentTimeMillis();
        }
        target = game;
        worker = new Thread(PerfSampler::run, "SBC perf sampler");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    public static void stop() {
        target = null;
        if (worker != null) worker.interrupt();
        worker = null;
        write();
    }

    private static void run() {
        while (target != null) {
            Thread t = target;
            if (t == null) break;
            StackTraceElement[] st = t.getStackTrace();
            if (st.length > 0) add(st);
            try {
                Thread.sleep(EVERY_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    static boolean oursFrame(StackTraceElement f) {
        return f.getClassName().startsWith("io.github.zmuzy.sbc.") || f.getMethodName().contains("$sbc$");
    }

    private static boolean waitFrame(StackTraceElement f) {
        String c = f.getClassName(), m = f.getMethodName();
        return m.equals("nSDL_GL_SwapWindow") || m.equals("SDL_GL_SwapWindow") || c.equals("java.lang.Thread") && m.startsWith("sleep")
                || c.endsWith("LockSupport") && m.startsWith("park") || c.equals("java.lang.Object") && m.equals("wait") || m.equals("nglClientWaitSync") || m.equals("glClientWaitSync")
                || m.equals("glFinish") || m.equals("nglFinish") || m.contains("WaitSync") || m.equals("SDL_WaitEventTimeout") || m.equals("nSDL_WaitEventTimeout");
    }

    private static String key(StackTraceElement f) {
        String c = f.getClassName();
        int dot = c.lastIndexOf('.');
        return (dot < 0 ? c : c.substring(dot + 1)) + "." + f.getMethodName();
    }

    static void add(StackTraceElement[] st) {
        synchronized (LOCK) {
            samples++;
            SELF.computeIfAbsent(key(st[0]), k -> new int[1])[0]++;
            for (int i = 0; i < Math.min(st.length, 4); i++) {
                if (waitFrame(st[i])) {
                    waiting++;
                    break;
                }
            }
            Set<String> seen = new HashSet<>();
            int outer = -1, inner = -1;
            for (int i = 0; i < st.length; i++) {
                String k = key(st[i]);
                if (seen.add(k)) INCLUSIVE.computeIfAbsent(k, x -> new int[1])[0]++;
                if (oursFrame(st[i])) {
                    if (inner < 0) inner = i;
                    outer = i;
                }
            }
            if (outer >= 0) {
                ours++;
                OURS_ENTRY.computeIfAbsent(key(st[outer]), k -> new int[1])[0]++;
                OURS_LEAF.computeIfAbsent(key(st[inner]) + (inner > 0 ? "  -> " + key(st[0]) : ""), k -> new int[1])[0]++;
            }
        }
    }

    private static List<String> top(Map<String, int[]> m, int n, int of) {
        List<Map.Entry<String, int[]>> all = new ArrayList<>(m.entrySet());
        all.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++)
            out.add(String.format(Locale.ROOT, "%5.1f%%  %s", 100.0 * all.get(i).getValue()[0] / Math.max(1, of), all.get(i).getKey()));
        return out;
    }

    static List<String> report() {
        synchronized (LOCK) {
            List<String> out = new ArrayList<>();
            out.add(String.format(Locale.ROOT, "%d samples over %.0f s of the game thread", samples, (System.currentTimeMillis() - startedAt) / 1000.0));
            out.add(String.format(Locale.ROOT, "through this mod: %.1f%%   waiting (graphics card, frame limit): %.1f%%", 100.0 * ours / Math.max(1, samples), 100.0 * waiting / Math.max(1, samples)));
            out.add("");
            out.add("== where this mod's share starts (outermost of our frames)");
            out.addAll(top(OURS_ENTRY, 40, samples));
            out.add("");
            out.add("== where this mod's share ends (innermost of ours -> what runs at that moment)");
            out.addAll(top(OURS_LEAF, 50, samples));
            out.add("");
            out.add("== the whole thread, by method on the stack (inclusive)");
            out.addAll(top(INCLUSIVE, 80, samples));
            out.add("");
            out.add("== the whole thread, by the method running at that moment (self)");
            out.addAll(top(SELF, 50, samples));
            return out;
        }
    }

    private static void write() {
        writtenAt = System.currentTimeMillis();
        try {
            Path p = Config.DIR.resolve("perf-sample.txt");
            Files.write(p, report());
        } catch (Exception e) {
            io.github.zmuzy.sbc.SbcMod.LOG.warn("Could not save the performance sample", e);
        }
    }
}
