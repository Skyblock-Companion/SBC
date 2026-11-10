// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.tasks;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.nav.Graph;
import io.github.zmuzy.sbc.nav.Navigator;
import io.github.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// guided routine: walks you through todays open tasks by importance with a path line, moves on by itself when
// one is done. commissions + crimson quests get step by step help
public final class Guide {
    public static boolean active;
    // task the routine is on rn
    public static TaskDef current;
    // open tasks put aside bc you dont carry their item yet
    public static final List<TaskDef> waiting = new ArrayList<>();
    public static String step = "";
    private static String lastTarget = "";
    private static int ticks;

    private Guide() {}

    // timed grind block youre on after the tasks (null while on tasks)
    public static io.github.zmuzy.sbc.plan.MaxPlan.Block block;
    public static long blockEndsAt;
    private static int blockIdx;

    public static void start() {
        active = true;
        lastTarget = "";
        block = null;
        blockIdx = 0;
        blockEndsAt = 0;
        ticks = 19; // pick the first task next tick, not a sec later
        Toasts.push(RoutinePreset.current().name(), "Follow the line - I move on by myself when a task is done (Alt+K = mission)", 0xFF39D0FF);
    }

    public static void stop() {
        active = false;
        current = null;
        block = null;
        step = "";
        Navigator.stop();
    }

    // ends this grind block early, goes to the next
    public static void nextBlock() {
        if (block == null) return;
        blockIdx++;
        block = null;
        blockEndsAt = 0;
        ticks = 19;
    }

    public static void tick(Minecraft mc) {
        if (!active || mc.player == null || ++ticks % 20 != 0) return;
        // candidates: the chosen routines open tasks. prefer ones with a spot, among the most important prefer your
        // island (no warping back and forth), tasks missing their item go last
        boolean smart = RoutinePreset.current().tasks() == null;
        List<Routine.Entry> open = new ArrayList<>();
        for (Routine.Entry e : RoutinePreset.tasks()) {
            if (e.complete() || Mission.skipped.contains(e.def().id())) continue;
            if (smart && e.def().island().isEmpty() && e.def().guideNode().isEmpty()) continue;
            open.add(e);
        }
        waiting.clear();
        Routine.Entry next = null;
        int bestPrio = -1;
        for (Routine.Entry e : open) {
            if (Mission.missing(e.def()) != null) {
                waiting.add(e.def());
                continue;
            }
            if (!e.def().island().isEmpty()) bestPrio = Math.max(bestPrio, smart ? Routine.priority(e.def()) : 0);
        }
        for (int pass = 0; pass < 3 && next == null; pass++)
            for (Routine.Entry e : open) {
                if (Mission.missing(e.def()) != null) continue;
                boolean placed = !e.def().island().isEmpty();
                boolean prio = !smart || Routine.priority(e.def()) == bestPrio;
                boolean here = e.def().island().equals(GameState.island);
                if ((pass == 0 && placed && prio && here) || (pass == 1 && placed && prio) || (pass == 2)) {
                    next = e;
                    break;
                }
            }
        if (next == null && !open.isEmpty()) next = open.get(0); // only item blocked tasks left: say what to get
        if (next == null) {
            runBlocks();
            return;
        }
        block = null;
        TaskDef t = next.def();
        current = t;
        // dont send you to an npc before you carry what it wants
        Mission.Need need = Mission.missing(t);
        if (need != null) {
            step = t.name() + ": get " + need.amount() + "x " + need.name() + " first (Alt+K)";
            if (!("need|" + t.id()).equals(lastTarget)) {
                lastTarget = "need|" + t.id();
                Navigator.stop();
                Toasts.push("You need " + need.name(), need.bazaar() ? "Alt+K → Take from sacks / Bazaar" : need.source(), 0xFFFF9F43);
            }
            return;
        }
        Navigator.Target target = helperTarget(t);
        if (target == null) target = locationOf(t);
        if (target == null) {
            step = t.name() + " - do it anywhere" + (t.target() > 1 ? " (" + next.done() + "/" + t.target() + ")" : "");
            if (!("free|" + t.id()).equals(lastTarget)) {
                lastTarget = "free|" + t.id();
                Navigator.stop();
                Toasts.push("Next: " + t.name(), t.hint(), 0xFF39D0FF);
            }
            return;
        }
        step = t.name() + " → " + target.name();
        if (!target.name().equals(lastTarget)) {
            lastTarget = target.name();
            Navigator.navigate(target);
        }
    }

    // after the tasks: the routines grind blocks one after another, each with a countdown
    private static void runBlocks() {
        current = null;
        List<io.github.zmuzy.sbc.plan.MaxPlan.Block> blocks = RoutinePreset.blocks();
        long now = System.currentTimeMillis();
        if (blockIdx >= blocks.size()) {
            step = "Routine complete!";
            var sk = io.github.zmuzy.sbc.track.Tracker.sessionSkill.entrySet().stream().max(java.util.Map.Entry.comparingByValue());
            Toasts.push("Routine complete · " + RoutinePreset.current().name(),
                    "+" + io.github.zmuzy.sbc.core.Prices.coins(io.github.zmuzy.sbc.track.Tracker.sessionCoins) + " coins"
                            + (sk.isPresent() ? " · +" + io.github.zmuzy.sbc.core.Prices.coins(sk.get().getValue()) + " " + sk.get().getKey() + " XP" : "")
                            + " this session", 0xFF4CE08A);
            stop();
            return;
        }
        io.github.zmuzy.sbc.plan.MaxPlan.Block b = blocks.get(blockIdx);
        if (block == null || blockEndsAt == 0) {
            block = b;
            blockEndsAt = now + b.minutes() * 60_000L;
            Toasts.push("Tasks done - grind time", b.name() + " for " + b.minutes() + " min", 0xFF4CE08A);
            boolean ok = !b.node().isEmpty() && Navigator.navigateTo(b.island(), b.node());
            if (!ok) {
                TaskDef place = new TaskDef("block", b.name(), "", b.icon(), io.github.zmuzy.sbc.core.SbTime.Period.DAILY, 1, b.minutes(), 0, 0, false,
                        b.island(), b.node(), b.why(), List.of(), List.of(), "");
                Navigator.Target t = locationOf(place);
                if (t != null) Navigator.navigate(t);
            }
        }
        long left = blockEndsAt - now;
        step = "⏱ " + b.name() + " · " + io.github.zmuzy.sbc.core.SbTime.dur(Math.max(0, left)) + " left";
        if (left <= 0) {
            Toasts.push("Time's up", b.name() + " done - next block", 0xFFFFC857);
            nextBlock();
        }
    }

    // where to go for a task: its npc, else the islands arrival warp
    public static Navigator.Target locationOf(TaskDef t) {
        if (!t.guideNode().isEmpty()) {
            Graph g = Repo.graph(t.island());
            if (g != null && !g.isEmpty()) {
                Graph.Node n = g.byName(t.guideNode());
                if (n != null) return new Navigator.Target(t.island(), n.name(), n.pos());
            }
        }
        if (t.island().isEmpty()) return null;
        Graph g = Repo.graph(t.island());
        if (g == null || g.isEmpty()) return null;
        for (Graph.Node n : g.named()) if (n.tags().contains("warp")) return new Navigator.Target(t.island(), GameState.pretty(t.island()), n.pos());
        return new Navigator.Target(t.island(), GameState.pretty(t.island()), g.nodes[0].pos());
    }

    // commission / faction quest helper: target the area of the first unfinished quest in the tab
    private static Navigator.Target helperTarget(TaskDef t) {
        if (t.id().equals("crimson_quests") && "CRIMSON_ISLE".equals(GameState.island)) {
            // exact quest spots from skyhannis data, never guessed from words (a "Mage Outlaw" quest isnt in mage land)
            Navigator.Target q = FactionQuests.next();
            if (q != null) return q;
            List<String> fetch = FactionQuests.missingFetch();
            if (!fetch.isEmpty()) return new Navigator.Target("CRIMSON_ISLE", "Town Board - first get " + String.join(", ", fetch), FactionQuests.townBoard());
        }
        String header = switch (t.id()) {
            case "commissions" -> "Commissions:";
            case "crimson_quests" -> "Faction Quests:";
            default -> null;
        };
        if (header == null || !t.island().equals(GameState.island)) return null;
        List<String> quests = section(header);
        Graph g = Repo.graph(GameState.island);
        if (g == null || g.isEmpty()) return null;
        var me = Minecraft.getInstance().player.position();
        for (String q : quests) {
            boolean done = q.toUpperCase(Locale.ROOT).contains("DONE") || q.contains("100%");
            if (done && t.id().equals("commissions")) {
                Graph.Node em = nearest(g, "emissary", me);
                if (em != null) return new Navigator.Target(GameState.island, em.name() + " (claim)", em.pos());
            }
        }
        for (String q : quests) {
            if (q.toUpperCase(Locale.ROOT).contains("DONE")) continue;
            Graph.Node n = bestMatch(g, q);
            if (n != null) return new Navigator.Target(GameState.island, q.replaceAll(":.*", "") + " @ " + n.name(), n.pos());
        }
        return null;
    }

    private static List<String> section(String header) {
        List<String> out = new ArrayList<>();
        List<String> tab = GameState.tab;
        for (int i = 0; i < tab.size(); i++) {
            if (!tab.get(i).trim().startsWith(header)) continue;
            for (int j = i + 1; j < tab.size() && j < i + 8; j++) {
                String l = tab.get(j).trim();
                if (l.isEmpty() || (l.endsWith(":") && !l.contains("%"))) break;
                out.add(l);
            }
        }
        return out;
    }

    // named spot whose name shares the most words with the quest ("Royal Mines Titanium" -> Royal Mines)
    private static Graph.Node bestMatch(Graph g, String quest) {
        String q = quest.toLowerCase(Locale.ROOT);
        Graph.Node best = null;
        int bestScore = 0;
        for (Graph.Node n : g.named()) {
            String name = n.name().toLowerCase(Locale.ROOT);
            if (name.equals("no_area") || name.startsWith("/warp")) continue;
            int score = q.contains(name) ? name.length() * 2 : 0;
            if (score == 0) for (String w : name.split(" ")) if (w.length() > 3 && q.contains(w)) score += w.length();
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return bestScore >= 5 ? best : null;
    }

    private static Graph.Node nearest(Graph g, String nameContains, Vec3 from) {
        Graph.Node best = null;
        double bd = Double.MAX_VALUE;
        for (Graph.Node n : g.named()) {
            if (!n.name().toLowerCase(Locale.ROOT).contains(nameContains)) continue;
            double d = n.pos().distanceToSqr(from);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }
}
