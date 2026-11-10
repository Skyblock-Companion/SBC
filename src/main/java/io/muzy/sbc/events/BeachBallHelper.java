// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.dungeon.DungeonMarks;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// from skyhanni BeachBallCatchHelper: a line where your bouncy beach ball went and will go, bounce count +
// how good that is, and a box at your height over the ball that goes green when you're under it
// path comes from its 3 small fits
public final class BeachBallHelper {
    public static final String ID = "beach_ball_line";
    private static final Map<Integer, Predictor> crystalBalls = new HashMap<>();
    private static int ticks;

    private BeachBallHelper() {}

    private static boolean on() {
        Feature f = Features.get(ID);
        return f != null && f.enabled();
    }

    static void onWorldChange() {
        crystalBalls.clear();
    }

    // new balls looked for every 2 ticks, thats how long skyhanni gives a stand to get its head
    static void tick(Minecraft mc) {
        if (!on()) {
            crystalBalls.clear();
            return;
        }
        if (mc.level == null || mc.player == null) return;
        Vec3 me = mc.player.position();
        crystalBalls.entrySet().removeIf(e -> {
            Entity entity = mc.level.getEntity(e.getKey());
            if (entity == null) return true;
            e.getValue().newData(entity.position(), me);
            return false;
        });
        if (++ticks % 2 != 0) return;
        String skull = DungeonMarks.skull("NORMAL_BEACH_BALL");
        if (skull.isEmpty()) return;
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
            if (crystalBalls.containsKey(st.getId())) continue;
            if (skull.equals(DungeonMarks.headTexture(st.getItemBySlot(EquipmentSlot.HEAD)))) crystalBalls.put(st.getId(), new Predictor(st.position(), me));
        }
    }

    private static void path(WorldDraw w, List<Vec3> path, int color) {
        for (int i = 0; i + 1 < path.size(); i++) w.line(path.get(i), path.get(i + 1), color, 4, false);
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 7 / 10, g = (argb >> 8 & 0xFF) * 7 / 10, b = (argb & 0xFF) * 7 / 10;
        return argb & 0xFF000000 | r << 16 | g << 8 | b;
    }

    // {colour code, word}: how good a bounce count is
    static String quality(int bounces) {
        if (bounces <= 1) return "§c§l" + bounces;
        String code, word;
        if (bounces <= 5) { code = "§f"; word = "DECENT"; }
        else if (bounces <= 15) { code = "§a"; word = "GOOD"; }
        else if (bounces <= 25) { code = "§5"; word = "AMAZING"; }
        else if (bounces <= 39) { code = "§6"; word = "IMPRESSIVE"; }
        else { code = "§d"; word = "INSANE"; }
        return code + "§l" + bounces + " §8- " + code + "§l" + word + "!";
    }

    private static void render(WorldDraw w) {
        if (crystalBalls.isEmpty() || w.mc.level == null || w.mc.player == null) return;
        Feature f = Features.get(ID);
        Option colorOption = f.option("color"), spot = f.option("landing");
        int color = colorOption == null ? 0xFF00C4F5 : colorOption.color();
        Vec3 player = w.mc.player.position().add(0, 1, 0);
        for (Map.Entry<Integer, Predictor> e : crystalBalls.entrySet()) {
            Predictor p = e.getValue();
            path(w, p.prePath, darker(color));
            path(w, p.predicted, color);
            if (spot != null && !spot.bool()) continue;
            Entity entity = w.mc.level.getEntity(e.getKey());
            if (entity == null) continue;
            Vec3 at = new Vec3(entity.getX(), player.y, entity.getZ());
            double distance = at.distanceTo(player);
            int box = distance < 0.3 ? 0x8055FF55 : distance < 0.9 ? 0x80FFAA00 : 0x80FF5555;
            w.box(new AABB(at.add(-0.3, -0.3, -0.3), at.add(0.3, 0.3, 0.3)), box, true, false);
            w.label(quality(p.bounces), at.add(0, 0.7, 0), false);
        }
    }

    // what the ball did since your last bounce + 3 fits of what it'll do
    static final class Predictor {
        final List<Vec3> data = new ArrayList<>();
        List<Vec3> prePath = List.of(), predicted = List.of();
        int bounces;
        private int startIndex, updated;
        private double minY;
        private Vec3 lastPosition;
        private boolean positive = true;
        private long lastChange = System.currentTimeMillis();

        Predictor(Vec3 start, Vec3 player) {
            lastPosition = start;
            newData(start, player);
        }

        void newData(Vec3 now, Vec3 player) {
            updateDirection(now);
            data.add(now);
            if (now.distanceTo(player) < 2.1) {
                startIndex = data.size() - 1;
                minY = now.y;
            }
            prePath = new ArrayList<>(data.subList(startIndex, data.size() - 1));
            if (!predicted.isEmpty()) predicted = new ArrayList<>(predicted.subList(1, predicted.size()));
            updated++;
            // only every 4 ticks or the path flickers
            if (updated < 4) return;
            predicted = predict();
            updated = 0;
        }

        private void updateDirection(Vec3 now) {
            if (lastPosition.distanceTo(now) < 0.3 || System.currentTimeMillis() - lastChange < 800) return;
            boolean up = now.y - lastPosition.y > 0;
            if (up && !positive) {
                bounces++;
                lastChange = System.currentTimeMillis();
            }
            positive = up;
            lastPosition = now;
        }

        private List<Vec3> predict() {
            int present = data.size() - 1 - startIndex, current = data.size() - 1;
            List<List<Vec3>> paths = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            if (present >= 3) add(paths, weights, small(current), 1);
            if (present >= 7) add(paths, weights, average(current), 2);
            if (present >= 5) add(paths, weights, spread(current), 1);
            if (paths.isEmpty()) return List.of(data.get(current));
            double xSum = 0, zSum = 0, weight = 0;
            for (int i = 0; i < paths.size(); i++) {
                Vec3 last = paths.get(i).get(paths.get(i).size() - 1);
                xSum += last.x * weights.get(i);
                zSum += last.z * weights.get(i);
                weight += weights.get(i);
            }
            double x = xSum / weight, z = zSum / weight;
            List<Vec3> best = paths.get(0);
            double bestD = Double.MAX_VALUE;
            for (List<Vec3> p : paths) {
                Vec3 last = p.get(p.size() - 1);
                double d = (last.x - x) * (last.x - x) + (last.z - z) * (last.z - z);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            return best;
        }

        private void add(List<List<Vec3>> paths, List<Integer> weights, List<Vec3> path, int weight) {
            double y = path.get(path.size() - 1).y;
            if (minY - 1 < y && y < minY + 1) {
                paths.add(path);
                weights.add(weight);
            }
        }

        private double smoothed(int t) {
            return (data.get(t - 1).y + data.get(t).y + data.get(t + 1).y) / 3;
        }

        // SmallPoly
        private List<Vec3> small(int current) {
            int t1 = current, t2 = current - 1, t3 = current - 2;
            return poly(t1, t2, t3, data.get(t1).y, data.get(t2).y, data.get(t3).y, current, data.get(current).x - data.get(current - 1).x, data.get(current).z - data.get(current - 1).z);
        }

        // AveragePoly
        private List<Vec3> average(int current) {
            int t1 = current - 1, t2 = current - 3, t3 = current - 5;
            double dx = (data.get(current).x - data.get(current - 3).x) / 3, dz = (data.get(current).z - data.get(current - 3).z) / 3;
            return poly(t1, t2, t3, smoothed(t1), smoothed(t2), smoothed(t3), current, dx, dz);
        }

        // SpreadPoly
        private List<Vec3> spread(int current) {
            int t1 = current - 1, t2 = (current - startIndex) / 2 + startIndex, t3 = startIndex + 1;
            return poly(t1, t2, t3, smoothed(t1), smoothed(t2), smoothed(t3), current, data.get(current).x - data.get(current - 1).x, data.get(current).z - data.get(current - 1).z);
        }

        // PolyModel.predict: parabola through 3 points, runs until it hits the height of your last bounce
        private List<Vec3> poly(long t1, long t2, long t3, double y1, double y2, double y3, int current, double dx, double dz) {
            double a = ((y3 - y1) * (t2 - t1) + (y2 - y1) * (t1 - t3)) / ((t3 * t3 - t1 * t1) * (double) (t2 - t1) + (t2 * t2 - t1 * t1) * (double) (t1 - t3));
            double b = ((y2 - y1) - a * (t2 * t2 - t1 * t1)) / (t2 - t1);
            double c = y1 - b * t1 - a * t1 * t1;
            List<Vec3> out = new ArrayList<>();
            Vec3 at = data.get((int) t1);
            out.add(at);
            for (int t = current + 1; t <= current + 300; t++) {
                double y = a * t * t + b * t + c;
                at = new Vec3(at.x + dx, y, at.z + dz);
                out.add(at);
                if (y <= minY) break;
            }
            return out;
        }
    }

    static void register() {
        Features.add(new Feature(ID, "Events", "Beach Ball helper", "A line where your Bouncy Beach Ball has been and will go, its bounces, and a box at your height that goes green when you stand under it. Not for the giant ball.", "SkyHanni: Beach Ball Line")
                .when(() -> GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV).world(BeachBallHelper::render)
                .option(Option.color("beach_ball_line.color", "Line colour", "", 0xFF00C4F5))
                .option(Option.bool("beach_ball_line.landing", "Landing spot", "", true)));
    }
}
