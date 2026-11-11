// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// our own profiler: ms per part per frame averaged over the last sec, so an fps drop points at the element or
// hook doing it instead of guessing. costs nothing when off
public final class Perf {
    public static final class Entry {
        public final String key;
        long frameNs; // this frames total
        long[] ring = new long[60];
        int ringPos;
        public long calls;
        public double avgMs, maxMs;

        Entry(String key) {
            this.key = key;
        }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final List<Entry> SORTED = new ArrayList<>();
    private static long frameStart;
    private static double frameMs, fps;
    private static int frames;
    private static long fpsWindow;
    public static volatile boolean enabled;

    private Perf() {}

    // start of a timed section, pair with end() and pass it the return value
    public static long begin() {
        return enabled ? System.nanoTime() : 0;
    }

    public static void end(String key, long t0) {
        if (!enabled || t0 == 0) return;
        long dt = System.nanoTime() - t0;
        Entry e = ENTRIES.get(key);
        if (e == null) {
            e = new Entry(key);
            ENTRIES.put(key, e);
        }
        e.frameNs += dt;
        e.calls++;
    }

    public static void time(String key, Runnable r) {
        long t0 = begin();
        try {
            r.run();
        } finally {
            end(key, t0);
        }
    }

    // once a frame from the hud: closes the frame + refreshes averages
    public static void frame() {
        if (!enabled) return;
        long now = System.nanoTime();
        // what the timed parts of the mod took since the last frame, for /sbc perf record
        if (FrameProbe.on) {
            long sum = 0;
            for (Entry e : ENTRIES.values()) sum += e.frameNs;
            FrameProbe.mod(sum);
        }
        if (frameStart != 0) frameMs = (now - frameStart) / 1e6;
        frameStart = now;
        frames++;
        if (now - fpsWindow > 1_000_000_000L) {
            fps = frames / ((now - fpsWindow) / 1e9);
            frames = 0;
            fpsWindow = now;
        }
        boolean settle = now - settledAt > 250_000_000L;
        for (Entry e : ENTRIES.values()) {
            e.ring[e.ringPos] = e.frameNs;
            e.ringPos = (e.ringPos + 1) % e.ring.length;
            e.frameNs = 0;
            if (!settle) continue;
            long sum = 0, max = 0;
            for (long v : e.ring) {
                sum += v;
                if (v > max) max = v;
            }
            e.avgMs = sum / (double) e.ring.length / 1e6;
            e.maxMs = max / 1e6;
        }
        if (!settle) return;
        settledAt = now;
        SORTED.clear();
        SORTED.addAll(ENTRIES.values());
        SORTED.sort(Comparator.comparingDouble((Entry e) -> e.avgMs).reversed());
    }

    private static long settledAt;

    // heaviest first, valid after frame()
    public static List<Entry> top(int n) {
        return SORTED.size() > n ? new ArrayList<>(SORTED.subList(0, n)) : new ArrayList<>(SORTED);
    }

    public static double fps() {
        return fps;
    }

    public static double frameMs() {
        return frameMs;
    }

    // all timed sections this window, ms per frame
    public static double totalMs() {
        double t = 0;
        for (Entry e : SORTED) t += e.avgMs;
        return t;
    }

    public static void reset() {
        ENTRIES.clear();
        SORTED.clear();
    }

    // lines for the overlay or a report
    public static List<String> report(int n) {
        List<String> out = new ArrayList<>();
        out.add(String.format(java.util.Locale.ROOT, "%.0f fps  frame %.1f ms  mod %.2f ms", fps, frameMs, totalMs()));
        for (Entry e : top(n)) out.add(String.format(java.util.Locale.ROOT, "%-22s %6.2f ms  max %6.2f", e.key, e.avgMs, e.maxMs));
        return out;
    }
}
