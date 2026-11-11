// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import net.minecraft.client.Minecraft;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.muzy.sbc.core.FrameProbe.COUNTS;
import static io.muzy.sbc.core.FrameProbe.COUNT_NAMES;
import static io.muzy.sbc.core.FrameProbe.NAMES;
import static io.muzy.sbc.core.FrameProbe.PARTS;

// turns a FrameProbe recording into a text report + a csv of every frame
// slow frames vs the ones that made the cap, and what the slow ones spent their time on
public final class FrameReport {
    // what the game looked like each second
    public record Second(long t, String island, String area, int players, int entities, String particles, long heapMb, double cpuProcess,
                         double cpuMachine, String screen, int fps, long gcCount, long gcMs) {
        static Second now(long t) {
            Minecraft mc = Minecraft.getInstance();
            int players = mc.level == null ? 0 : mc.level.players().size(), entities = mc.level == null ? 0 : mc.level.getEntityCount();
            String particles = "";
            try {
                particles = mc.particleEngine.countParticles();
            } catch (Throwable ignored) {
            }
            Runtime r = Runtime.getRuntime();
            double proc = -1, machine = -1;
            if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                proc = os.getProcessCpuLoad();
                machine = os.getCpuLoad();
            }
            long gcN = 0, gcMs = 0;
            for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
                gcN += Math.max(0, b.getCollectionCount());
                gcMs += Math.max(0, b.getCollectionTime());
            }
            var screen = mc.gui.screen();
            return new Second(t, GameState.island, GameState.area, players, entities, particles, (r.totalMemory() - r.freeMemory()) >> 20, proc, machine,
                    screen == null ? "" : screen.getClass().getSimpleName(), mc.getFps(), gcN, gcMs);
        }
    }

    private FrameReport() {}

    // what the game is set to, read on the game thread before the report is worked out
    static List<String> settings(double budget) {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        var o = mc.options;
        // where it was recorded, so a report says it by itself
        int players = mc.getConnection() == null ? 0 : mc.getConnection().getOnlinePlayers().size();
        out.add("where: island " + GameState.island + ", area " + GameState.area + ", players in the tab list " + players
                + (mc.player == null ? "" : String.format(Locale.ROOT, ", position %.0f %.0f %.0f facing %.0f %.0f", mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot())));
        int cap = o.framerateLimit().get();
        out.add("frame cap: " + (cap >= 260 ? "unlimited (judged against 240)" : cap) + "   budget a frame: " + String.format(Locale.ROOT, "%.2f ms", budget));
        //? if >=26.3 {
        out.add("vsync: " + o.enableVsync().get() + "   refresh rate: " + refreshRate(mc) + " Hz   fullscreen: " + mc.getWindow().isExclusiveFullscreen()
        //?} else {
        /*out.add("vsync: " + o.enableVsync().get() + "   refresh rate: " + mc.getWindow().getRefreshRate() + " Hz   fullscreen: " + mc.getWindow().isFullscreen()
        *///?}
                + "   window: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + "   gui scale: " + o.guiScale().get());
        out.add("render distance: " + o.renderDistance().get() + "   simulation distance: " + o.simulationDistance().get() + "   entity distance: "
                + Math.round(o.entityDistanceScaling().get() * 100) + "%   graphics: " + o.graphicsPreset().get() + "   particles: " + o.particles().get());
        try {
            var info = com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo();
            out.add("gpu: " + info.name() + " (" + info.vendorName() + ", " + info.backendName() + ")");
        } catch (Throwable ignored) {
        }
        out.add("shaders: " + shaders());
        Runtime r = Runtime.getRuntime();
        List<String> jvm = new ArrayList<>();
        for (String a : ManagementFactory.getRuntimeMXBean().getInputArguments()) if (a.startsWith("-X")) jvm.add(a);
        List<String> gcs = new ArrayList<>();
        for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) gcs.add(b.getName());
        out.add("java " + System.getProperty("java.version") + "   cores: " + r.availableProcessors() + "   max memory: " + (r.maxMemory() >> 20) + " MB   collectors: " + gcs);
        out.add("java options: " + String.join(" ", jvm));
        out.add("os: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        List<String> mods = new ArrayList<>();
        for (var m : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            String id = m.getMetadata().getId();
            if (id.startsWith("fabric-") || id.equals("java") || id.equals("minecraft") || id.equals("fabricloader") || id.equals("mixinextras")) continue;
            if (m.getContainingMod().isPresent()) continue;
            mods.add(id + " " + m.getMetadata().getVersion().getFriendlyString());
        }
        out.add("mods: " + String.join(", ", mods));
        out.add("this mod: HUD as a picture " + io.muzy.sbc.ui.HudTexture.share() + (io.muzy.sbc.ui.HudTexture.on() ? " of frames" : "") + ", perf overlay " + (Config.I.perfOverlay ? "on (it costs a little)" : "off"));
        return out;
    }

    private static String shaders() {
        try {
            Object api = Class.forName("net.irisshaders.iris.api.v0.IrisApi").getMethod("getInstance").invoke(null);
            boolean in = (boolean) api.getClass().getMethod("isShaderPackInUse").invoke(api);
            return in ? "ON (a shader pack is in use)" : "off";
        } catch (Throwable t) {
            return "no Iris";
        }
    }

    static double budget() {
        int cap = Minecraft.getInstance().options.framerateLimit().get();
        return 1000.0 / (cap >= 260 ? 240 : cap);
    }

    public static void writeNow() {
        write();
    }

    // runs on the game thread when recording ends, the math happens on another thread
    static void write() {
        double budget = budget();
        List<String> settings = settings(budget);
        int n = FrameProbe.frames;
        float[] total = FrameProbe.total, alloc = FrameProbe.alloc;
        float[][] parts = FrameProbe.parts;
        int[][] counts = FrameProbe.counts;
        long[] at = FrameProbe.at;
        byte[] flags = FrameProbe.flags;
        List<Second> seconds = new ArrayList<>(FrameProbe.SECONDS);
        say("§7Recorded §f" + n + " §7frames, writing the report...");
        Thread t = new Thread(() -> {
            try {
                List<String> report = report(n, total, alloc, parts, counts, at, flags, seconds, budget, settings);
                Path dir = Config.DIR.resolve("perf");
                Files.createDirectories(dir);
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Path txt = dir.resolve("record-" + stamp + ".txt"), csv = dir.resolve("record-" + stamp + ".csv");
                Files.write(txt, report);
                Files.write(csv, csv(n, total, alloc, parts, counts, at, flags));
                say("§a" + report.get(1).trim());
                say("§7Saved to §fconfig/sbcompanion/perf/" + txt.getFileName());
            } catch (Throwable e) {
                Safe.caught("FrameReport.write", e);
                say("§cCould not write the report: " + e.getMessage());
            }
        }, "sbc frame report");
        t.setDaemon(true);
        t.start();
    }

    private static void say(String line) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[SBC] " + line));
        });
    }

    private static String ms(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static double pct(float[] sorted, double p) {
        if (sorted.length == 0) return 0;
        int i = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
    }

    static List<String> report(int n, float[] total, float[] alloc, float[][] parts, int[][] counts, long[] at, byte[] flags, List<Second> seconds, double budget, List<String> settings) {
        List<String> out = new ArrayList<>();
        out.add("SkyBlock Companion frame recording, " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (n == 0) {
            out.add("No frames were recorded.");
            return out;
        }
        float[] sorted = Arrays.copyOf(total, n);
        Arrays.sort(sorted);
        double sum = 0;
        for (int i = 0; i < n; i++) sum += total[i];
        double seconds_ = (at[n - 1] / 1e9) + total[n - 1] / 1000.0;
        int worst1 = Math.max(1, n / 100), worst01 = Math.max(1, n / 1000);
        double low1 = 0, low01 = 0;
        for (int i = 0; i < worst1; i++) low1 += sorted[n - 1 - i];
        for (int i = 0; i < worst01; i++) low01 += sorted[n - 1 - i];
        low1 /= worst1;
        low01 /= worst01;
        int over = 0;
        for (int i = 0; i < n; i++) if (total[i] > budget * 1.05) over++;
        out.add(String.format(Locale.ROOT, "%d frames in %.0f s, %.0f fps average, 1%% low %.0f fps, %d frames (%.1f%%) slower than %.2f ms", n, seconds_, n / seconds_,
                1000 / low1, over, 100.0 * over / n, budget));
        out.add("");
        out.add("== settings");
        out.addAll(settings);
        out.add("");
        out.add("== frame time (ms, the whole frame, waiting included)");
        out.add(String.format(Locale.ROOT, "average %s   median %s   90%% %s   99%% %s   99.9%% %s   worst %s", ms(sum / n), ms(pct(sorted, 50)), ms(pct(sorted, 90)),
                ms(pct(sorted, 99)), ms(pct(sorted, 99.9)), ms(sorted[n - 1])));
        out.add(String.format(Locale.ROOT, "1%% low %.0f fps (%s ms)   0.1%% low %.0f fps (%s ms)", 1000 / low1, ms(low1), 1000 / low01, ms(low01)));
        double[] limits = {budget, 1000 / 144.0, 1000 / 120.0, 1000 / 60.0, 1000 / 30.0};
        StringBuilder b = new StringBuilder("slower than: ");
        for (double l : limits) {
            int c = 0;
            for (int i = 0; i < n; i++) if (total[i] > l) c++;
            b.append(String.format(Locale.ROOT, "%s ms (%.0f fps) %d   ", ms(l), 1000 / l, c));
        }
        out.add(b.toString());
        double[] edges = {0, 2, 3, 4, 4.17, 5, 6, 8, 10, 15, 25, 50, 1e9};
        StringBuilder h = new StringBuilder("spread: ");
        for (int e = 0; e + 1 < edges.length; e++) {
            int c = 0;
            for (int i = 0; i < n; i++) if (total[i] >= edges[e] && total[i] < edges[e + 1]) c++;
            h.append(e + 2 == edges.length ? "over " + ms(edges[e]) : ms(edges[e]) + "-" + ms(edges[e + 1])).append(": ").append(c).append("   ");
        }
        out.add(h.toString());
        out.add("");

        // which part grows in a slow frame
        boolean[] slow = new boolean[n];
        int slowN = 0;
        for (int i = 0; i < n; i++) if (slow[i] = total[i] > budget * 1.05) slowN++;
        int fastN = n - slowN;
        out.add("== where a frame goes (ms, average) - all frames / frames that made it / slow frames / how much more in a slow one");
        double[] extra = new double[PARTS];
        for (int p = 0; p < PARTS; p++) {
            double all = 0, fast = 0, sl = 0;
            for (int i = 0; i < n; i++) {
                all += parts[p][i];
                if (slow[i]) sl += parts[p][i];
                else fast += parts[p][i];
            }
            all /= n;
            fast = fastN == 0 ? 0 : fast / fastN;
            sl = slowN == 0 ? 0 : sl / slowN;
            extra[p] = sl - fast;
            out.add(String.format(Locale.ROOT, "%-26s %7s %7s %7s %+8.2f", NAMES[p], ms(all), ms(fast), ms(sl), extra[p]));
        }
        double otherAll = 0, otherFast = 0, otherSlow = 0;
        for (int i = 0; i < n; i++) {
            double top = 0;
            for (int p : FrameProbe.TOP) top += parts[p][i];
            double o = Math.max(0, total[i] - top);
            otherAll += o;
            if (slow[i]) otherSlow += o;
            else otherFast += o;
        }
        out.add(String.format(Locale.ROOT, "%-26s %7s %7s %7s %+8.2f", "other (between the parts)", ms(otherAll / n), ms(fastN == 0 ? 0 : otherFast / fastN),
                ms(slowN == 0 ? 0 : otherSlow / slowN), (slowN == 0 ? 0 : otherSlow / slowN) - (fastN == 0 ? 0 : otherFast / fastN)));
        out.add("");
        out.add("== what a frame had (average) - all / made it / slow");
        for (int c = 0; c < COUNTS; c++) {
            double all = 0, fast = 0, sl = 0;
            for (int i = 0; i < n; i++) {
                all += counts[c][i];
                if (slow[i]) sl += counts[c][i];
                else fast += counts[c][i];
            }
            out.add(String.format(Locale.ROOT, "%-26s %9.1f %9.1f %9.1f", COUNT_NAMES[c], all / n, fastN == 0 ? 0 : fast / fastN, slowN == 0 ? 0 : sl / slowN));
        }
        double aAll = 0, aFast = 0, aSlow = 0;
        for (int i = 0; i < n; i++) {
            aAll += alloc[i];
            if (slow[i]) aSlow += alloc[i];
            else aFast += alloc[i];
        }
        out.add(String.format(Locale.ROOT, "%-26s %9.0f %9.0f %9.0f", "memory made (KB)", aAll / n, fastN == 0 ? 0 : aFast / fastN, slowN == 0 ? 0 : aSlow / slowN));
        int[][] flag = new int[3][2];
        int[] bits = {FrameProbe.F_HUD, FrameProbe.F_SCREEN, FrameProbe.F_GC};
        for (int i = 0; i < n; i++) for (int k = 0; k < 3; k++) if ((flags[i] & bits[k]) != 0) flag[k][slow[i] ? 1 : 0]++;
        String[] flagNames = {"hud picture redrawn", "a menu open", "a memory collection ended"};
        for (int k = 0; k < 3; k++)
            out.add(String.format(Locale.ROOT, "%-26s %8.1f%% of frames that made it, %5.1f%% of slow frames", flagNames[k], fastN == 0 ? 0 : 100.0 * flag[k][0] / fastN, slowN == 0 ? 0 : 100.0 * flag[k][1] / slowN));
        out.add("");

        // per slow frame, the part furthest over its own usual time
        if (slowN > 0) {
            double[] usual = new double[PARTS];
            for (int p = 0; p < PARTS; p++) {
                float[] f = new float[Math.max(1, fastN)];
                int k = 0;
                for (int i = 0; i < n; i++) if (!slow[i]) f[k++] = parts[p][i];
                Arrays.sort(f, 0, Math.max(1, k));
                usual[p] = k == 0 ? 0 : f[k / 2];
            }
            int[] blame = new int[PARTS + 2];
            for (int i = 0; i < n; i++) {
                if (!slow[i]) continue;
                int best = -1;
                double bestOver = 0;
                for (int p = 0; p < PARTS; p++) {
                    if (p == FrameProbe.LIMITER || p == FrameProbe.EXTRACT || p == FrameProbe.RENDER || p == FrameProbe.MOD) continue;
                    double o = parts[p][i] - usual[p];
                    if (o > bestOver) {
                        bestOver = o;
                        best = p;
                    }
                }
                if (counts[FrameProbe.C_TICKS][i] >= 2 && (best < 0 || bestOver < parts[FrameProbe.TICK][i])) blame[PARTS]++;
                else if (best < 0) blame[PARTS + 1]++;
                else blame[best]++;
            }
            out.add("== in each slow frame, the part furthest over its usual time");
            List<int[]> rows = new ArrayList<>();
            for (int p = 0; p < blame.length; p++) if (blame[p] > 0) rows.add(new int[]{p, blame[p]});
            rows.sort((x, y) -> y[1] - x[1]);
            for (int[] r : rows) {
                String name = r[0] == PARTS ? "two or more game ticks in one frame" : r[0] == PARTS + 1 ? "nothing measured (outside the parts)" : NAMES[r[0]].trim();
                out.add(String.format(Locale.ROOT, "%-40s %5d frames (%.0f%%)", name, r[1], 100.0 * r[1] / slowN));
            }
            out.add("");
        }

        // the worst frames one by one
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (x, y) -> Float.compare(total[y], total[x]));
        out.add("== the 40 slowest frames: when (s), ms, then each part over 0.3 ms, counts, flags");
        for (int k = 0; k < Math.min(40, n); k++) {
            int i = order[k];
            StringBuilder r = new StringBuilder(String.format(Locale.ROOT, "%7.2fs %7s |", at[i] / 1e9, ms(total[i])));
            for (int p = 0; p < PARTS; p++) if (parts[p][i] >= 0.3 && p != FrameProbe.EXTRACT && p != FrameProbe.RENDER) r.append(' ').append(NAMES[p].trim()).append(' ').append(ms(parts[p][i])).append(',');
            r.append(" | ticks ").append(counts[FrameProbe.C_TICKS][i]).append(", entities ").append(counts[FrameProbe.C_ENTITIES][i]).append(", players ")
                    .append(counts[FrameProbe.C_PLAYERS][i]).append(", alloc ").append(Math.round(alloc[i])).append(" KB");
            if ((flags[i] & FrameProbe.F_HUD) != 0) r.append(", hud redraw");
            if ((flags[i] & FrameProbe.F_SCREEN) != 0) r.append(", menu");
            if ((flags[i] & FrameProbe.F_GC) != 0) r.append(", collection");
            out.add(r.toString());
        }
        out.add("");

        // second by second
        out.add("== second by second: frames, slow frames, worst ms | where, players, entities, particles, memory MB, cpu game/all, collections, menu");
        int s = 0;
        for (Second sec : seconds) {
            int frames = 0, sl = 0;
            float worst = 0;
            for (int i = 0; i < n; i++) {
                if (at[i] / 1_000_000_000L != sec.t()) continue;
                frames++;
                if (slow[i]) sl++;
                worst = Math.max(worst, total[i]);
            }
            long gcSince = s + 1 < seconds.size() ? seconds.get(s + 1).gcCount() - sec.gcCount() : 0;
            out.add(String.format(Locale.ROOT, "%3ds %4d %4d %7s | %s %s, %d players, %d entities, particles %s, %d MB, cpu %.0f%%/%.0f%%, %d collections%s",
                    sec.t(), frames, sl, ms(worst), sec.island(), sec.area(), sec.players(), sec.entities(), sec.particles(), sec.heapMb(),
                    sec.cpuProcess() * 100, sec.cpuMachine() * 100, gcSince, sec.screen().isEmpty() ? "" : ", " + sec.screen()));
            s++;
        }
        return out;
    }

    static List<String> csv(int n, float[] total, float[] alloc, float[][] parts, int[][] counts, long[] at, byte[] flags) {
        List<String> out = new ArrayList<>(n + 1);
        StringBuilder head = new StringBuilder("time_s,frame_ms");
        for (String name : NAMES) head.append(',').append(name.trim().replace(' ', '_').replace("(", "").replace(")", ""));
        for (String name : COUNT_NAMES) head.append(',').append(name.replace(' ', '_'));
        head.append(",alloc_kb,hud_redraw,menu,collection");
        out.add(head.toString());
        for (int i = 0; i < n; i++) {
            StringBuilder r = new StringBuilder(String.format(Locale.ROOT, "%.4f,%.3f", at[i] / 1e9, total[i]));
            for (int p = 0; p < PARTS; p++) r.append(',').append(String.format(Locale.ROOT, "%.3f", parts[p][i]));
            for (int c = 0; c < COUNTS; c++) r.append(',').append(counts[c][i]);
            r.append(',').append(Math.round(alloc[i])).append(',').append((flags[i] & FrameProbe.F_HUD) != 0 ? 1 : 0).append(',')
                    .append((flags[i] & FrameProbe.F_SCREEN) != 0 ? 1 : 0).append(',').append((flags[i] & FrameProbe.F_GC) != 0 ? 1 : 0);
            out.add(r.toString());
        }
        return out;
    }
    //? if >=26.3 {

    private static String refreshRate(Minecraft mc) {
        var monitor = mc.getWindow().findBestMonitor();
        return monitor == null ? "?" : String.valueOf(monitor.currentMode().getRefreshRate());
    }
    //?}
}
