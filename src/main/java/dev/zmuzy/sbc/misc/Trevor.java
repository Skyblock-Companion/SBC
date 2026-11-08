// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.misc;

import dev.zmuzy.sbc.areas.FishHooks;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.dungeon.DungeonMarks;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni misc/trevor TrevorFeatures, TrevorSolver, TalbotCircles, TrapperMobArea, TrevorMob: where the
// animal is, the talbot theodolite circle, trevors cooldown over his head
public final class Trevor {
    public static final String ID = "trevor_solver";
    private static final Patterns.P TRAPPER = Patterns.p("SH.misc.trevor.trapper"), ABOVE = Patterns.p("SH.misc.trevor.above"), BELOW = Patterns.p("SH.misc.trevor.below"), AT = Patterns.p("SH.misc.trevor.at"),
            ZONE = Patterns.p("SH.misc.trevor.zone"), DIED = Patterns.p("SH.misc.trevor.mob.died.colorless"), OUT_OF_TIME = Patterns.p("SH.misc.trevor.outoftime");

    // from skyhanni TrapperMobArea
    record Area(String location, double x, double y, double z) {}

    static final List<Area> AREAS = List.of(new Area("Oasis", 126, 77, -456), new Area("Mushroom Gorge", 300, 80, -509), new Area("Overgrown Mushroom Cave", 242, 60, -389),
            new Area("Desert Settlement", 184, 86, -384), new Area("Glowing Mushroom Cave", 199, 50, -512), new Area("Desert Mountain", 255, 148, -518));

    // from skyhanni TrevorMob: name + how far you can see it
    private record Mob(String name, double see) {}

    private static final List<Mob> MOBS = List.of(new Mob("Cow", 68), new Mob("Horse", 90), new Mob("Sheep", 68), new Mob("Pig", 68), new Mob("Rabbit", 43), new Mob("Chicken", 33));
    // from skyhanni TrevorSolver.animalHealths
    static final Set<Integer> HEALTHS = Set.of(100, 200, 500, 1000, 2000, 5000, 10000, 30000);
    private static final double MAX_RADIUS = 500;

    private enum Status {
        READY("§2Ready", 0x4B00AA00), WAITING("§3Waiting", 0x4B00AAAA), ACTIVE("§cActive Quest", 0x4BAA0000);
        final String label;
        final int color;

        Status(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private record Circle(Vec3 center, double radius) {}

    private static final List<Circle> CIRCLES = new ArrayList<>();
    private static long nextReady;
    private static boolean trevorBackFromBreak = true, questActive;
    private static Status status = Status.READY;
    private static Area area;
    private static boolean found;
    private static Vec3 foundAt;
    private static String foundName = "";
    private static int foundId = -1;
    private static double minHeight, maxHeight, averageHeight;
    private static RemotePlayer trevor;
    private static long lastSecond;

    private Trevor() {}

    private static boolean here() {
        return "THE_FARMING_ISLANDS".equals(GameState.island);
    }

    private static boolean opt(String key) {
        return Features.get(ID).option(key).bool();
    }

    static Area area(String name) {
        for (Area a : AREAS) if (a.location().equals(name)) return a;
        return null;
    }

    static void resetLocation() {
        maxHeight = minHeight = averageHeight = 0;
        foundId = -1;
        foundAt = null;
        found = false;
    }

    private static void reset() {
        resetLocation();
        CIRCLES.clear();
        status = Status.READY;
        questActive = false;
        trevor = null;
        area = null;
    }

    // from skyhanni findMobHeight: each reading narrows the 5 blocks the mob can be in
    static void height(double playerY, int height, boolean above) {
        double mob = above ? playerY + height : playerY - height;
        if (maxHeight == 0) {
            maxHeight = mob + 2.5;
            minHeight = mob - 2.5;
        } else if (mob + 2.5 >= minHeight && mob + 2.5 <= maxHeight) maxHeight = mob + 2.5;
        else if (mob - 2.5 >= minHeight && mob - 2.5 <= maxHeight) minHeight = mob - 2.5;
        else {
            maxHeight = mob + 2.5;
            minHeight = mob - 2.5;
        }
        averageHeight = (minHeight + maxHeight) / 2;
    }

    public static boolean questActive() {
        return questActive;
    }

    static double averageHeight() {
        return averageHeight;
    }

    // from skyhanni TalbotCircles.addResult: how far the angle puts it at that height
    static double radius(int dy, int angle) {
        return Math.tan(Math.toRadians(90.0 - angle)) * Math.abs(dy);
    }

    private static void addCircle(Vec3 p, int dy, int angle) {
        double r = radius(dy, angle);
        if (r >= 0 && r <= MAX_RADIUS) CIRCLES.add(new Circle(new Vec3(p.x, Math.round(p.y * 100) / 100d + dy, p.z), r));
    }

    private static ChatBus.Result chat(ChatBus.Line line) {
        String clean = line.plain().trim();
        Minecraft mc = Minecraft.getInstance();
        Matcher m;
        if (DIED.matcher(clean).matches()) {
            resetLocation();
            CIRCLES.clear();
            trevorBackFromBreak = true;
            area = null;
            status = System.currentTimeMillis() >= nextReady ? Status.READY : Status.WAITING;
        } else if (TRAPPER.matcher(clean).matches()) {
            nextReady = System.currentTimeMillis() + 20_000;
            status = Status.ACTIVE;
            trevorBackFromBreak = false;
        } else if ((m = ABOVE.matcher(clean)).matches() || (m = BELOW.matcher(clean)).matches()) {
            boolean above = clean.contains("blocks above");
            try {
                int height = Integer.parseInt(m.group("height")), angle = Integer.parseInt(m.group("angle"));
                if (mc.player != null) {
                    height(mc.player.getY(), height, above);
                    addCircle(mc.player.position(), above ? height : -height, angle);
                }
            } catch (NumberFormatException ignored) {
            }
        } else if (AT.matcher(clean).matches()) {
            if (mc.player != null) averageHeight = mc.player.getY();
        } else if (OUT_OF_TIME.matcher(clean).matches()) reset();
        return ChatBus.PASS;
    }

    // from skyhanni onTabListUpdate
    static void readTab(List<String> tab) {
        boolean seen = false, active = false;
        Area previous = area;
        for (String raw : tab) {
            String l = GameState.strip(raw).trim();
            if (l.startsWith("Time Left: ")) {
                trevorBackFromBreak = false;
                status = Status.ACTIVE;
                active = true;
            }
            Area a = area(l);
            if (a != null) {
                area = a;
                seen = true;
            }
            Matcher m = ZONE.matcher(l);
            if (m.matches()) {
                area = area(m.group("zone"));
                seen = true;
            }
        }
        if (!seen) area = null;
        if (!active) trevorBackFromBreak = true;
        if (foundAt != null && active) area = previous;
        questActive = active;
    }

    private static void findMob(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        boolean blind = mc.player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player || !(entity instanceof LivingEntity living)) continue;
            String name = entity.getName().getString();
            Mob mob = null;
            for (Mob m : MOBS) if (m.name().contains(name) || name.equals(m.name())) mob = m;
            if (mob == null || !living.getAttributes().hasAttribute(Attributes.MAX_HEALTH)) continue;
            int health = (int) living.getAttributeBaseValue(Attributes.MAX_HEALTH);
            if (!HEALTHS.contains(health) && !HEALTHS.contains(health / 2)) continue;
            if (foundId == entity.getId()) {
                boolean oasisMob = mob.name().equals("Rabbit") || mob.name().equals("Sheep");
                if (oasisMob && area != null && area.location().equals("Oasis")) return;
                boolean canSee = mc.player.distanceTo(entity) <= mob.see() && mc.player.hasLineOfSight(entity) && !entity.isInvisible() && !blind;
                if (canSee) {
                    if (!found) FishHooks.title("§2Saw " + mob.name() + "!");
                    found = true;
                    foundName = mob.name();
                    foundAt = Vec3.atLowerCornerOf(entity.blockPosition());
                }
            } else foundId = entity.getId();
            return;
        }
        if (foundId != -1) {
            foundAt = null;
            foundId = -1;
        }
    }

    private static void findTrevor(Minecraft mc) {
        if (mc.level == null || trevor != null && !trevor.isRemoved()) return;
        trevor = null;
        String texture = DungeonMarks.skull("TREVOR");
        if (texture.isEmpty()) return;
        for (Player p : mc.level.players()) {
            if (!(p instanceof RemotePlayer rp)) continue;
            for (var pr : rp.getGameProfile().properties().get("textures")) {
                if (texture.equals(pr.value())) {
                    trevor = rp;
                    return;
                }
            }
        }
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        // from skyhanni updateTrapper
        if (trevorBackFromBreak && now < nextReady) status = Status.WAITING;
        if (trevorBackFromBreak && now >= nextReady) {
            if (nextReady != 0) {
                nextReady = 0;
                if (opt("trevor_solver.readyTitle")) {
                    FishHooks.title("§2Trapper Ready");
                    FishHooks.pling();
                }
            }
            status = Status.READY;
        }
        if (now - lastSecond >= 1000) {
            lastSecond = now;
            readTab(GameState.tab);
            if (opt("trevor_solver.solver") && questActive) findMob(mc);
            if (opt("trevor_solver.cooldown")) findTrevor(mc);
        }
    }

    private static void circle(WorldDraw w, Circle c) {
        int steps = 64;
        Vec3 last = null;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2 * i / steps;
            Vec3 p = new Vec3(c.center().x + Math.cos(a) * c.radius(), c.center().y, c.center().z + Math.sin(a) * c.radius());
            if (last != null) w.line(last, p, 0xFFFFA500, 2f, true);
            last = p;
        }
    }

    private static void world(WorldDraw w) {
        Minecraft mc = Minecraft.getInstance();
        if (opt("trevor_solver.cooldown") && trevor != null && mc.player != null) {
            Vec3 pos = trevor.position();
            AABB box = trevor.getBoundingBox();
            w.box(box, status.color, true, false);
            if (mc.player.position().distanceTo(pos) < 15) {
                String label = status == Status.WAITING ? "§3" + Math.max(1, (nextReady - System.currentTimeMillis() + 999) / 1000) + " seconds left" : status.label;
                w.label(label, pos.add(0, 2.23, 0), false);
            }
        }
        boolean mobFound = false;
        if (opt("trevor_solver.solver")) {
            if (found && foundAt != null) {
                w.waypoint(foundAt.add(0, -2, 0), 0xFF55FF55, foundName.isEmpty() ? "Mob Location" : foundName, true, true);
                mobFound = true;
            } else if (area != null) {
                w.waypoint(new Vec3(area.x(), averageHeight != 0 ? averageHeight : area.y(), area.z()), 0xFFFFAA00, area.location(), true, true);
            }
        }
        if (opt("trevor_solver.talbotCircles") && !mobFound) for (Circle c : CIRCLES) circle(w, c);
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Farming Islands", "Trevor helper", "On the Farming Islands: a waypoint to the area of Trevor's animal, and to the animal once you can see it; the height and the ring from a Talbot's Theodolite; his cooldown over his head; a title when he is ready again.", "SkyHanni: Trapper Solver, Trapper Cooldown, Theodolite Visualizer")
                .when(() -> here() || dev.zmuzy.sbc.SbcMod.DEV).tick(Trevor::tick).world(Trevor::world).left(Trevor::reset)
                .option(Option.bool("trevor_solver.solver", "Waypoint to the animal", "Does not find rabbits or sheep in the Oasis.", true))
                .option(Option.bool("trevor_solver.talbotCircles", "Theodolite ring", "The larger the height difference, the more exact it is. /sbc cleartalbotcircles takes the rings away.", true))
                .option(Option.bool("trevor_solver.cooldown", "Cooldown over Trevor", "", true))
                .option(Option.bool("trevor_solver.readyTitle", "Title when he is ready", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 21, ID, f, Trevor::chat);
        Safe.commands("Trevor.commands", (d, b) -> d.register(ClientCommands.literal("sbc").then(ClientCommands.literal("cleartalbotcircles").executes(c -> {
            CIRCLES.clear();
            return 1;
        }))));
        dev.zmuzy.sbc.core.Commands.describe("cleartalbotcircles", "Take away the rings of a Talbot's Theodolite");
    }
}
