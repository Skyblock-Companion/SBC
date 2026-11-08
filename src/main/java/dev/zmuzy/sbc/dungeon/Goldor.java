// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker, SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// f7 goldor phase: which terminals, devices, levers of this section are left. from skyblocker
// GoldorWaypointsManager (state from chat, section number) + TerminalHud
public final class Goldor {
    // from skyblocker GoldorWaypoint: what, which section, done or not
    static final class Task {
        final String kind, name;
        final int phase;
        final Vec3 pos;
        boolean done;

        Task(String kind, int phase, String name, Vec3 pos) {
            this.kind = kind;
            this.phase = phase;
            this.name = name;
            this.pos = pos;
        }
    }

    private static final String TERMINALS_START = "[BOSS] Storm: I should have known that I stood no chance.", GATE_DESTROYED = "The gate has been destroyed!", CORE_ENTRANCE = "The Core entrance is opening!";
    private static final Pattern STEP = Pattern.compile("^(?<name>\\w+) (?:activated a (?<a>terminal|lever)|completed a (?<c>device))! \\((?<n>\\d)/(?<t>\\d)\\)$");

    private static final List<Task> ALL = new ArrayList<>();
    private static boolean active, gateDestroyed;
    private static int phase;

    private Goldor() {}

    private static List<Task> all() {
        if (ALL.isEmpty()) {
            try (var in = Goldor.class.getResourceAsStream("/sbc/goldor.json")) {
                for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                    JsonObject o = e.getAsJsonObject();
                    JsonArray p = o.getAsJsonArray("pos");
                    ALL.add(new Task(o.get("kind").getAsString(), o.get("phase").getAsInt(), o.get("name").getAsString(), new Vec3(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt())));
                }
            } catch (Exception ex) {
                SbcMod.LOG.warn("Could not read terminal markers", ex);
            }
        }
        return ALL;
    }

    public static boolean active() {
        return active;
    }

    static int section() {
        return phase;
    }

    static boolean gateDestroyed() {
        return gateDestroyed;
    }

    static void reset() {
        active = gateDestroyed = false;
        phase = 0;
        resetMarks();
    }

    // skyhannis /shresetterminal: everything shows again
    public static void resetMarks() {
        for (Task t : all()) t.done = false;
    }

    static List<Task> phaseTasks() {
        List<Task> out = new ArrayList<>();
        for (Task t : all()) if (t.phase == phase) out.add(t);
        return out;
    }

    // from skyblocker GoldorWaypointsManager.onChatMessage. positionOf says where a player stands, or null
    static void feed(String plain, java.util.function.Function<String, Vec3> positionOf) {
        String m = plain.trim();
        if (!active) {
            if (m.equals(TERMINALS_START)) {
                reset();
                active = true;
            }
            return;
        }
        Matcher s = STEP.matcher(m);
        if (s.matches()) {
            if (s.group("n").equals(s.group("t"))) {
                phase++;
                gateDestroyed = false;
                return;
            }
            String kind = s.group("c") != null ? "device" : s.group("a");
            Vec3 at = positionOf.apply(s.group("name"));
            if (at == null) return;
            phaseTasks().stream().filter(t -> !t.done && t.kind.equals(kind)).min(Comparator.comparingDouble(t -> t.pos.add(0.5, 0.5, 0.5).distanceToSqr(at))).ifPresent(t -> t.done = true);
        } else if (m.equals(CORE_ENTRANCE)) {
            active = false;
        } else if (m.equals(GATE_DESTROYED)) {
            gateDestroyed = true;
        }
    }

    private static Vec3 playerAt(String name) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        for (var p : level.players()) if (p.getGameProfile().name().equals(name)) return p.position();
        return null;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (DungeonRun.inDungeon() && DungeonRun.isFloor("F7", "M7")) feed(line.plain(), Goldor::playerAt);
        else if (active) reset();
        return ChatBus.PASS;
    }

    private static int color(Task t) {
        return switch (t.kind) {
            case "terminal" -> 0xFF00FF00;
            case "device" -> 0xFF0000FF;
            default -> 0xFFFFFF00;
        };
    }

    // from skyblocker extractRendering: whats left of this section + name + distance
    public static void draw(WorldDraw w) {
        if (!active) return;
        Vec3 me = w.mc.player.position();
        String look = DungeonMarks.choice("dungeon_terminals", "look", "Filled and outline");
        for (Task t : phaseTasks()) {
            if (t.done) continue;
            Vec3 c = t.pos;
            DungeonMarks.draw(w, new AABB(c.x, c.y, c.z, c.x + 1, c.y + 1, c.z + 1), color(t), look, true);
            w.label(t.name + "  §7" + (int) c.distanceTo(me) + "m", c.add(0.5, 1.5, 0.5), true);
        }
    }

    // --- the card (TerminalHud) ---

    // from skyblocker TerminalHud.getStatusText: done, who stands within 5 blocks, or still to do
    private static String status(Task t) {
        if (t.done) return "Done";
        var level = Minecraft.getInstance().level;
        if (level != null && DungeonMarks.opt("dungeon_goldor", "player")) {
            for (var p : level.players()) {
                if (p.distanceToSqr(t.pos.add(0.5, 0.5, 0.5)) < 25) {
                    String n = p.getGameProfile().name();
                    return "§e" + n.substring(0, Math.min(12, n.length()));
                }
            }
        }
        return "To do";
    }

    private static boolean listed(Task t) {
        if (t.done && !DungeonMarks.opt("dungeon_goldor", "show_done")) return false;
        return switch (t.kind) {
            case "terminal" -> DungeonMarks.opt("dungeon_goldor", "terminals");
            case "device" -> DungeonMarks.opt("dungeon_goldor", "device");
            default -> DungeonMarks.opt("dungeon_goldor", "levers");
        };
    }

    private static List<RichCard.Line> rows(List<String[]> tasks, boolean gate, boolean gateGone, int done, int total) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.bar("Done", total == 0 ? 0 : (double) done / total, Modern.GOOD));
        for (String[] t : tasks) out.add(RichCard.Line.row(t[0], (t[1].equals("Done") ? "§a" : t[1].startsWith("§") ? "" : "§c") + t[1]).noMeter());
        if (gate) out.add(RichCard.Line.row("Gate", gateGone ? "§aDestroyed" : "§cTo do").noMeter());
        return out;
    }

    private static RichCard.Content liveContent() {
        List<Task> tasks = phaseTasks();
        List<String[]> shown = new ArrayList<>();
        int done = 0, lever = 0;
        for (Task t : tasks) {
            if (t.done) done++;
            String name = t.kind.equals("lever") ? "Lever #" + ++lever : t.name;
            if (listed(t)) shown.add(new String[]{name, status(t)});
        }
        boolean gate = DungeonMarks.opt("dungeon_goldor", "gate") && phase < 3 && (!gateDestroyed || DungeonMarks.opt("dungeon_goldor", "show_done"));
        return new RichCard.Content("§7Section " + (phase + 1) + "  §f" + done + "/" + tasks.size(), rows(shown, gate, gateDestroyed, done, tasks.size()));
    }

    private static RichCard.Content sampleOf(int section, int done, boolean gateGone) {
        List<String[]> t = new ArrayList<>();
        String[] names = {"Terminal #1", "Terminal #2", "Terminal #3", "Terminal #4", "Device", "Lever #1", "Lever #2"};
        for (int i = 0; i < names.length; i++) t.add(new String[]{names[i], i < done ? "Done" : i == done ? "§ePlayer" : "To do"});
        return new RichCard.Content("§7Section " + section + "  §f" + done + "/7", rows(t, true, gateGone, done, 7));
    }

    private static List<HudElement.Row> classic(List<String[]> rows) {
        List<HudElement.Row> out = new ArrayList<>();
        for (String[] r : rows) out.add(new HudElement.Row(r[0], r[1], 0));
        return out;
    }

    public static final HudElement CARD = new RichCard("dungeon_goldor", "Goldor tasks", "Dungeon", Zone.TOP_LEFT, 37, "dungeon_goldor") {
        @Override public boolean visible() {
            return active;
        }

        @Override protected Content live() {
            return liveContent();
        }

        @Override protected Content sample() {
            return sampleOf(2, 3, false);
        }

        @Override public List<Content> states() {
            return List.of(sampleOf(4, 7, true));
        }

        @Override protected List<Row> classicRows() {
            List<String[]> rows = new ArrayList<>();
            for (Task t : phaseTasks()) if (listed(t)) rows.add(new String[]{t.name, status(t)});
            return classic(rows);
        }

        @Override protected List<Row> classicDemo() {
            return classic(List.of(new String[]{"Terminal #1", "Done"}, new String[]{"Terminal #2", "§ePlayer"}, new String[]{"Device", "To do"}, new String[]{"Lever", "To do"}));
        }
    };

    public static void register() {
        Features.add(new Feature("dungeon_goldor", "Dungeons", "Goldor tasks", "A card for the Goldor phase of Floor 7: every terminal, device, lever and the gate of this section, who is at it, and what is done.", "Skyblocker: Terminal HUD")
                .when(() -> DungeonRun.inDungeon() || SbcMod.DEV)
                .option(Option.bool("dungeon_goldor.terminals", "Show terminals", "Terminals on the card.", true))
                .option(Option.bool("dungeon_goldor.device", "Show the device", "The device on the card.", true))
                .option(Option.bool("dungeon_goldor.levers", "Show levers", "The levers on the card.", true))
                .option(Option.bool("dungeon_goldor.gate", "Show the gate", "The gate on the card.", true))
                .option(Option.bool("dungeon_goldor.player", "Who is at it", "The name of a player standing at a task.", true))
                .option(Option.bool("dungeon_goldor.show_done", "Keep what is done", "What is done stays on the card, marked. Off, only what is left is listed.", true)));
        ChatBus.register(ChatBus.OBSERVE + 14, "goldor", null, Goldor::onChat);
        HudLayout.add(CARD);
    }
}
