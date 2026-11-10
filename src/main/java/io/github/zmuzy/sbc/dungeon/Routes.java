// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.SoundHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;

// secret routes: per room a list of steps, one per secret, with the path to walk, spots to etherwarp to,
// mine, blow up or interact with, and the secret itself. only the current step gets drawn
public final class Routes {
    public record Step(List<Vec3> path, List<Vec3> etherwarps, List<Vec3> mines, List<Vec3> interacts, List<Vec3> tnts, List<Vec3> pearls,
                       String type, Vec3 secret) {}

    // what to do on the way to a steps secret, in path order. levers are clicked, the rest gets passed
    enum Kind {
        ETHERWARP("Etherwarp", 0xFFA855F7), PEARL("Pearl", 0xFF22D3EE), MINE("Mine (Dungeon Breaker)", 0xFFFFFF00),
        TNT("Superboom", 0xFFEF4444), LEVER("Pull lever", 0xFF3B82F6);

        final String label;
        final int color;

        Kind(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    record Action(Kind kind, Vec3 at) {}

    public static final String FASTEST = "fastest", DEFAULT = "normal";
    private static final List<String> FILES = List.of("fow", "3ppopka", "pearl", "normal");
    public static final List<String> SETS = List.of(FASTEST, "fow", "3ppopka", "pearl", "normal");
    // what a spot costs vs walking, in blocks: aiming at it + the moment it takes
    private static final double TELEPORT_COST = 3, PEARL_COST = 4, MINE_COST = 1.5, TNT_COST = 4;
    // a chest click waits this many ticks for "That chest is locked!" before it counts. the line can take a moment
    // on a normal ping, and if it comes later still the step goes back (LATE_LOCK)
    static final int CLICK_WAIT = 30, LATE_LOCK = 80;
    private static final String LOCKED_LINE = "That chest is locked!", LEVER_LINE = "You hear the sound of something opening...";
    // a room whose step moved on from a chest click: the step it was on, and when
    private static final java.util.Map<Dungeon.Placed, int[]> CLICKED_ON = new java.util.WeakHashMap<>();

    private static final Map<String, Map<String, List<Step>>> LOADED = new HashMap<>();
    private static final Map<String, List<Step>> BEST = new HashMap<>();

    // whats done of the step a room is on. reset as soon as the step changes (also by /sbc route next)
    static final class Progress {
        int step = -1;
        final Set<Integer> pulled = new HashSet<>(), passed = new HashSet<>(), bats = new HashSet<>();
        int clickedAt = -1, emptyTicks;
        boolean locked, itemSeen, batDied;

        void reset(int s) {
            step = s;
            pulled.clear();
            passed.clear();
            bats.clear();
            clickedAt = -1;
            emptyTicks = 0;
            locked = itemSeen = batDied = false;
        }
    }

    private static final Map<Dungeon.Placed, Progress> PROGRESS = new WeakHashMap<>();
    // by identity, the steps are loaded once
    private static final Map<Step, List<Action>> ACTIONS = new java.util.IdentityHashMap<>();

    private Routes() {}

    private static Vec3 pt(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static List<Vec3> pts(JsonObject o, String key) {
        List<Vec3> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(pt(e));
        return out;
    }

    private static Map<String, List<Step>> set(String name) {
        return LOADED.computeIfAbsent(name, n -> {
            Map<String, List<Step>> out = new HashMap<>();
            try (var in = Routes.class.getResourceAsStream("/sbc/routes_" + n + ".json")) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    List<Step> steps = new ArrayList<>();
                    for (JsonElement se : e.getValue().getAsJsonArray()) {
                        JsonObject o = se.getAsJsonObject();
                        JsonObject sec = o.has("secret") ? o.getAsJsonObject("secret") : null;
                        boolean placed = sec != null && sec.has("location");
                        steps.add(new Step(pts(o, "locations"), pts(o, "etherwarps"), pts(o, "mines"), pts(o, "interacts"), pts(o, "tnts"), pts(o, "enderpearls"),
                                sec == null || !sec.has("type") ? "" : sec.get("type").getAsString(), placed ? pt(sec.get("location")) : null));
                    }
                    out.put(e.getKey().toLowerCase(Locale.ROOT), steps);
                }
            } catch (Exception ex) {
                SbcMod.LOG.warn("Could not read secret routes {}", n, ex);
            }
            return out;
        });
    }

    private static boolean secretStep(Step s) {
        return s.secret() != null && !s.type().startsWith("exit") && !s.type().equals("dummy");
    }

    // route length: blocks walked + a price per etherwarp, pearl, block to mine and superboom
    static double cost(List<Step> steps) {
        double blocks = 0;
        for (Step s : steps) {
            for (int i = 1; i < s.path().size(); i++) blocks += s.path().get(i - 1).distanceTo(s.path().get(i));
            blocks += s.etherwarps().size() * TELEPORT_COST + s.pearls().size() * PEARL_COST + s.mines().size() * MINE_COST + s.tnts().size() * TNT_COST;
        }
        return blocks;
    }

    // out of a rooms routes, the shortest of the ones getting the most secrets. a short route that skips some isnt faster
    static List<Step> fastest(List<List<Step>> routes) {
        List<Step> best = List.of();
        long mostSecrets = -1;
        double lowest = Double.MAX_VALUE;
        for (List<Step> r : routes) {
            long secrets = r.stream().filter(Routes::secretStep).count();
            double c = cost(r);
            if (secrets > mostSecrets || (secrets == mostSecrets && c < lowest)) {
                best = r;
                mostSecrets = secrets;
                lowest = c;
            }
        }
        return best;
    }

    public static List<Step> steps(String roomId) {
        String room = roomId.toLowerCase(Locale.ROOT), s = Config.I.routeSet;
        if (!SETS.contains(s)) s = DEFAULT;
        if (FASTEST.equals(s)) {
            List<Step> known = BEST.get(room);
            if (known != null) return known;
            List<List<Step>> all = new ArrayList<>();
            for (String file : FILES) {
                List<Step> r = set(file).get(room);
                if (r != null) all.add(r);
            }
            List<Step> best = fastest(all);
            BEST.put(room, best);
            return best;
        }
        List<Step> r = set(s).get(room);
        if (r == null && !s.equals("fow")) r = set("fow").get(room);
        return r == null ? List.of() : r;
    }

    // ---- step logic, on room coords so it can be tested ----

    // the steps spots in the order you get to them: by the path point theyre closest to, a warp before what
    // you do where you land. levers always before the secret, which comes last and isnt in here
    static List<Action> actions(Step s) {
        List<Action> out = new ArrayList<>();
        for (Vec3 p : s.etherwarps()) out.add(new Action(Kind.ETHERWARP, p));
        for (Vec3 p : s.pearls()) out.add(new Action(Kind.PEARL, p));
        for (Vec3 p : s.mines()) out.add(new Action(Kind.MINE, p));
        for (Vec3 p : s.tnts()) out.add(new Action(Kind.TNT, p));
        for (Vec3 p : s.interacts()) out.add(new Action(Kind.LEVER, p));
        out.sort(Comparator.<Action>comparingInt(a -> closest(s.path(), a.kind() == Kind.ETHERWARP ? a.at().add(0, 1, 0) : a.at()))
                .thenComparingInt(a -> a.kind().ordinal()));
        return out;
    }

    private static int closest(List<Vec3> path, Vec3 p) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double d = path.get(i).distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    // true at the path points you land on with an etherwarp: the walk into them is a teleport, not a walk
    static boolean[] landings(Step s) {
        boolean[] out = new boolean[s.path().size()];
        for (int i = 0; i < out.length; i++) {
            Vec3 p = s.path().get(i);
            for (Vec3 e : s.etherwarps()) {
                double dy = p.y - e.y;
                if (dy >= 0 && dy <= 2 && Math.hypot(p.x - e.x, p.z - e.z) <= 1.6) {
                    out[i] = true;
                    break;
                }
            }
        }
        return out;
    }

    // index of the first spot still to do, -1 = only the secret is left
    static int next(List<Action> acts, Progress p) {
        for (int i = 0; i < acts.size(); i++) {
            if (acts.get(i).kind() == Kind.LEVER ? !p.pulled.contains(i) : !p.passed.contains(i)) return i;
        }
        return -1;
    }

    // a spot got done, and with it every spot before it (a lever only counts once its clicked)
    static void pass(List<Action> acts, Progress p, int k) {
        for (int i = 0; i <= k; i++) if (acts.get(i).kind() != Kind.LEVER) p.passed.add(i);
    }

    // a right click on a block (world coords). a lever of the step gets pulled; the secret block starts the wait
    // for a locked chest. at = room coords to the world block
    static void click(Step s, Progress p, Vec3 block, UnaryOperator<Vec3> at, int now) {
        List<Action> acts = actionsOf(s);
        for (int i = 0; i < acts.size(); i++) {
            if (acts.get(i).kind() == Kind.LEVER && at.apply(acts.get(i).at()).distanceTo(block) < 0.5) {
                p.pulled.add(i);
                p.locked = false;
                return;
            }
        }
        if (s.secret() != null && s.type().equals("interact") && at.apply(s.secret()).distanceTo(block) < 1.1) {
            p.clickedAt = now;
            p.locked = false;
        }
    }

    // "That chest is locked!" right after the click: the step stays, and asks for the lever
    static void locked(Progress p) {
        if (p.clickedAt < 0) return;
        p.clickedAt = -1;
        p.locked = true;
    }

    // the click on the secret counted, no locked line came
    static boolean clickDone(Progress p, int now) {
        return p.clickedAt >= 0 && now - p.clickedAt >= CLICK_WAIT;
    }

    // an item secret is done once the item seen at the spot is gone. stood on it with no item there = someone
    // else took it
    static boolean itemDone(Progress p, boolean itemThere, boolean standingOn) {
        if (itemThere) {
            p.itemSeen = true;
            p.emptyTicks = 0;
            return false;
        }
        if (p.itemSeen) return true;
        return standingOn && ++p.emptyTicks >= 10;
    }

    // a bat secret is done once a bat seen near the spot dies (or its death sound came from there). at the spot
    // with no bat around for 2 s = already killed
    static boolean batDone(Progress p, Collection<Integer> batsNear, IntPredicate gone, boolean standingOn) {
        if (p.batDied) return true;
        for (int id : p.bats) if (gone.test(id)) return true;
        p.bats.addAll(batsNear);
        if (!p.bats.isEmpty() || !batsNear.isEmpty()) {
            p.emptyTicks = 0;
            return false;
        }
        return standingOn && ++p.emptyTicks >= 40;
    }

    // exits (and steps without a secret kind we can see happen) by walking up to them
    static boolean reached(Step s, Vec3 me, UnaryOperator<Vec3> at) {
        Vec3 target = s.secret() != null ? s.secret() : s.path().isEmpty() ? null : s.path().get(s.path().size() - 1);
        if (target == null) return true;
        double reach = s.type().startsWith("exit") ? 2.0 : 3.2;
        return at.apply(target).add(0.5, 0.5, 0.5).distanceTo(me) < reach;
    }

    // what the secret itself is, block = what the world has there (DungeonAlerts.clickedType) or null
    static String secretName(String type, String block) {
        return switch (type) {
            case "interact" -> block == null || block.equals("Trapped Chest") ? "Chest" : block;
            case "item" -> "Item";
            case "bat" -> "Bat";
            case "exit", "exitroute" -> "Exit";
            case "prince" -> "Prince";
            case "" -> "Secret";
            default -> Character.toUpperCase(type.charAt(0)) + type.substring(1);
        };
    }

    // the short line for the route card: the next thing to do
    static String nextAction(Step s, Progress p, String block) {
        List<Action> acts = actionsOf(s);
        int k = next(acts, p);
        if (k >= 0) return acts.get(k).kind().label;
        if (p.locked) return "Locked, pull the lever first";
        return switch (secretName(s.type(), block)) {
            case "Chest" -> "Open chest";
            case "Lever" -> "Pull lever";
            case "Wither Essence" -> "Take essence";
            case "Item" -> "Pick up item";
            case "Bat" -> "Kill bat";
            case "Exit" -> "Go to exit";
            case "Secret" -> "";
            default -> secretName(s.type(), block);
        };
    }

    static List<Action> actionsOf(Step s) {
        return ACTIONS.computeIfAbsent(s, Routes::actions);
    }

    // ---- in the world ----

    private static Vec3 block(Dungeon.Placed r, Vec3 rel) {
        Vec3 c = r.toActual(rel.x, rel.y, rel.z);
        return new Vec3(Math.floor(c.x), Math.floor(c.y), Math.floor(c.z));
    }

    private static Step current(Dungeon.Placed r) {
        List<Step> steps = steps(r.def.id());
        return r.step < steps.size() ? steps.get(r.step) : null;
    }

    private static Progress progress(Dungeon.Placed r) {
        Progress p = PROGRESS.computeIfAbsent(r, k -> new Progress());
        if (p.step != r.step) p.reset(r.step);
        return p;
    }

    private static String blockKind(Dungeon.Placed r, Step s) {
        return s.secret() == null || !s.type().equals("interact") ? null : DungeonAlerts.clickedType(block(r, s.secret()));
    }

    // every tick in a room: spots passed, and the next step once this ones secret is done
    public static void advance(Dungeon.Placed r, Vec3 me) {
        Minecraft mc = Minecraft.getInstance();
        Step s = current(r);
        if (s == null || mc.level == null || mc.player == null) return;
        Progress p = progress(r);
        UnaryOperator<Vec3> at = rel -> block(r, rel);
        List<Action> acts = actionsOf(s);
        for (int i = 0; i < acts.size(); i++) {
            Action a = acts.get(i);
            if (a.kind() == Kind.LEVER || p.passed.contains(i)) continue;
            Vec3 b = at.apply(a.at());
            BlockPos pos = BlockPos.containing(b);
            double d = b.add(0.5, 0.5, 0.5).distanceTo(me);
            boolean air = d < 16 && mc.level.hasChunkAt(pos) && mc.level.getBlockState(pos).isAir();
            boolean done = switch (a.kind()) {
                case ETHERWARP -> b.add(0.5, 1, 0.5).distanceTo(me) < 2.0;
                case PEARL -> d < 1.5;
                case MINE -> air;
                case TNT -> air && d < 5;
                default -> false;
            };
            if (done) pass(acts, p, i);
        }
        boolean done;
        if (s.secret() == null) done = reached(s, me, at);
        else {
            Vec3 sec = at.apply(s.secret());
            Vec3 mid = sec.add(0.5, 0.5, 0.5);
            boolean standingOn = mid.distanceTo(me) < 2.0;
            done = switch (s.type()) {
                case "interact" -> {
                    boolean d = clickDone(p, mc.player.tickCount);
                    if (d) CLICKED_ON.put(r, new int[]{r.step, mc.player.tickCount});
                    yield d;
                }
                case "item" -> itemDone(p, !mc.level.getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(sec)).inflate(1.5)).isEmpty(), standingOn);
                case "bat" -> {
                    List<Integer> near = new ArrayList<>();
                    for (Bat b : mc.level.getEntitiesOfClass(Bat.class, new AABB(BlockPos.containing(sec)).inflate(8), e -> !e.isDeadOrDying())) near.add(b.getId());
                    yield batDone(p, near, id -> {
                        var e = mc.level.getEntity(id);
                        return e == null || e.isRemoved() || (e instanceof LivingEntity l && l.isDeadOrDying());
                    }, mid.distanceTo(me) < 3.0);
                }
                default -> reached(s, me, at);
            };
        }
        if (done) r.step++;
    }

    private static void onClick(Vec3 pos, boolean right) {
        if (!right || !Features.on("dungeon_routes") || !"CATACOMBS".equals(GameState.island)) return;
        Dungeon.Placed r = Dungeon.current();
        var mc = Minecraft.getInstance();
        if (r == null || mc.player == null) return;
        Step s = current(r);
        if (s != null) click(s, progress(r), pos, rel -> block(r, rel), mc.player.tickCount);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String t = line.plain().trim();
        Dungeon.Placed r = Dungeon.current();
        var mc = Minecraft.getInstance();
        if (r == null || mc.player == null) return ChatBus.PASS;
        if (LOCKED_LINE.equals(t)) {
            int[] was = CLICKED_ON.remove(r);
            Progress p = progress(r);
            // the chest was locked after all, but the step already moved on: back to it, asking for the lever
            if (lateLock(p.clickedAt, was, mc.player.tickCount)) {
                r.step = was[0];
                progress(r).locked = true;
            } else locked(p);
        } else if (LEVER_LINE.equals(t)) {
            Step s = current(r);
            if (s != null) leverPulled(s, progress(r));
        }
        return ChatBus.PASS;
    }

    // a locked line with no click waiting, shortly after a click moved the step on. pure, for tests
    static boolean lateLock(int clickedAt, int[] movedOn, int now) {
        return clickedAt < 0 && movedOn != null && now - movedOn[1] <= LATE_LOCK;
    }

    // Hypixel says a lever opened something: every lever of the step counts as pulled
    static void leverPulled(Step s, Progress p) {
        List<Action> acts = actionsOf(s);
        for (int i = 0; i < acts.size(); i++) if (acts.get(i).kind() == Kind.LEVER) p.pulled.add(i);
        p.locked = false;
    }

    private static boolean onSound(SoundHooks.Sound snd) {
        if (!snd.name().equals("entity.bat.death") || !Features.on("dungeon_routes")) return false;
        Dungeon.Placed r = Dungeon.current();
        Step s = r == null ? null : current(r);
        if (s == null || s.secret() == null || !s.type().equals("bat")) return false;
        if (block(r, s.secret()).add(0.5, 0.5, 0.5).distanceTo(new Vec3(snd.x(), snd.y(), snd.z())) < 12) progress(r).batDied = true;
        return false;
    }

    static void register(Feature f) {
        Interactions.onBlockClick(Routes::onClick);
        ChatBus.register(ChatBus.OBSERVE + 16, "dungeonroutes", f, Routes::onChat);
        SoundHooks.listen(Routes::onSound);
    }

    // the next thing to do in the room youre in, "" = no route here
    public static String next(Dungeon.Placed r) {
        Step s = current(r);
        return s == null ? "" : nextAction(s, progress(r), blockKind(r, s));
    }

    public static String info(Dungeon.Placed r) {
        List<Step> steps = steps(r.def.id());
        if (steps.isEmpty()) return "";
        if (r.step >= steps.size()) return "Route done";
        String n = next(r);
        return "Route " + (r.step + 1) + "/" + steps.size() + (n.isEmpty() ? "" : " · " + n);
    }

    private static void box(WorldDraw w, Vec3 at, int col, boolean filled) {
        w.box(new AABB(at.x, at.y, at.z, at.x + 1, at.y + 1, at.z + 1), col, filled, true);
    }

    // a jump: dashed arc from where you warp to where you land
    private static void teleportArc(WorldDraw w, Vec3 a, Vec3 b) {
        double len = a.distanceTo(b), h = Math.min(2.5, 0.5 + len * 0.15);
        int n = Math.max(8, (int) (len * 2)) & ~1;
        Vec3 prev = a;
        for (int k = 1; k <= n; k++) {
            double t = (double) k / n;
            Vec3 p = a.add(b.subtract(a).scale(t)).add(0, h * 4 * t * (1 - t), 0);
            if (k % 2 == 1) w.line(prev, p, Kind.ETHERWARP.color, 3f, false);
            prev = p;
        }
    }

    private static void walk(WorldDraw w, List<Vec3> run) {
        if (run.size() < 2) return;
        List<Vec3> smooth = io.github.zmuzy.sbc.nav.Navigator.rounded(run);
        for (int i = 1; i < smooth.size(); i++) w.line(smooth.get(i - 1), smooth.get(i), Config.I.pathColor | 0xFF000000, 5f, false);
    }

    // the walk drawn like the nav line: your color + width, starting just in front of your eyes, rounded corners.
    // the way into an etherwarp landing is an arc, you dont walk it
    private static void walkLine(WorldDraw w, Dungeon.Placed r, Step s) {
        List<Vec3> points = new ArrayList<>();
        List<Boolean> jump = new ArrayList<>();
        boolean[] land = landings(s);
        for (int i = 0; i < s.path().size(); i++) {
            points.add(block(r, s.path().get(i)).add(0.5, 0.5, 0.5));
            jump.add(land[i]);
        }
        if (s.secret() != null) {
            points.add(block(r, s.secret()).add(0.5, 0.5, 0.5));
            jump.add(false);
        }
        if (points.isEmpty()) return;
        Vec3 me = w.mc.player.position(), eye = w.mc.player.getEyePosition();
        int start = io.github.zmuzy.sbc.nav.Navigator.closestIndexAhead(points, me);
        Vec3 dir = points.get(start).subtract(eye);
        List<Vec3> run = new ArrayList<>();
        run.add(dir.lengthSqr() < 1e-4 ? eye : eye.add(dir.normalize().scale(0.8)).add(0, -0.35, 0));
        for (int i = start; i < points.size(); i++) {
            if (jump.get(i)) {
                walk(w, run);
                teleportArc(w, run.get(run.size() - 1), points.get(i));
                run = new ArrayList<>();
            }
            run.add(points.get(i));
        }
        walk(w, run);
    }

    public static boolean draw(WorldDraw w, Dungeon.Placed r) {
        List<Step> steps = steps(r.def.id());
        if (steps.isEmpty()) return false;
        if (r.step >= steps.size()) return true;
        Step s = steps.get(r.step);
        Progress p = progress(r);
        walkLine(w, r, s);
        List<Action> acts = actionsOf(s);
        List<Action> labelled = new ArrayList<>();
        boolean leverLeft = false;
        for (int i = 0; i < acts.size(); i++) {
            Action a = acts.get(i);
            boolean lever = a.kind() == Kind.LEVER;
            if (lever ? p.pulled.contains(i) : p.passed.contains(i)) continue;
            Vec3 b = block(r, a.at());
            // the first lever left gets a line from your crosshair, so it is clear where to go before the chest
            if (lever && !leverLeft) w.line(io.github.zmuzy.sbc.combat.Combat.fromCrosshair(w), b.add(0.5, 0.5, 0.5), a.kind().color, 2f, false);
            leverLeft |= lever;
            box(w, b, a.kind().color, a.kind() == Kind.TNT);
            // one label per bunch of blocks to mine or blow up
            if (labelled.stream().anyMatch(l -> l.kind() == a.kind() && l.at().distanceTo(b) < 2.5)) continue;
            labelled.add(new Action(a.kind(), b));
            w.label(a.kind().label, b.add(0.5, a.kind() == Kind.ETHERWARP ? 1.9 : 1.4, 0.5), true);
        }
        if (s.secret() != null) {
            Vec3 at = block(r, s.secret());
            int col = switch (s.type()) {
                case "item" -> 0xFF4ADE80;
                case "interact" -> 0xFF60A5FA;
                case "bat" -> 0xFF2DD4BF;
                case "exit", "exitroute" -> 0xFFF2F2F2;
                default -> 0xFFEF9F27;
            };
            box(w, at, col, true);
            w.label((r.step + 1) + " - " + secretName(s.type(), blockKind(r, s)), at.add(0.5, 1.4, 0.5), true);
            if (leverLeft || p.locked) w.labelBelow("Pull the lever first", at.add(0.5, 1.4, 0.5));
        }
        return true;
    }
}
