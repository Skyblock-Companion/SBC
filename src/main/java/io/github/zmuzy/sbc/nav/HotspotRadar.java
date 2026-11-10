// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.nav;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.events.diana.BezierFitter;
import io.github.zmuzy.sbc.events.diana.DianaData;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.ParticleHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

// from skyhanni FishingHotspotRadar: right clicking the radar sends flame particles along a curve, where it
// lands is the hotspot (if its on this island at all)
public final class HotspotRadar {
    private static final String ID = "col_hotspot", RADAR = "HOTSPOT_RADAR", HOTSPOT_TAG = "fishing_hotspot";
    // radius + 5 sec from skyhanni FishingHotspotRadar.pathFind
    private static final double NODE_RADIUS = 15;
    private static final long PATH_AFTER_ARRIVAL_MS = 5000;
    private static final BezierFitter FITTER = new BezierFitter(3);
    private static long lastParticle, lastAbilityUse, arrivedAt;
    private static Vec3 hotspot;
    private static Object world;
    // the guide this feature started, so it only ever stops its own
    private static Navigator.Target guide;

    private HotspotRadar() {}

    private static boolean option(String key, boolean otherwise) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(ID + "." + key);
        return o == null ? otherwise : o.bool();
    }

    private static void onParticle(ParticleHooks.Particle p) {
        if (!Features.on(ID) || !p.type().equals("minecraft:flame") || p.count() != 1 || p.speed() != 0f) return;
        long now = System.currentTimeMillis();
        lastParticle = now;
        if (now - lastAbilityUse > 1000) return;
        if (!FITTER.tryAdd(new Vec3(p.x(), p.y(), p.z()), 3.0)) return;
        Vec3 guess = FITTER.solve();
        if (guess == null) return;
        hotspot = onIsland(GameState.island, guess);
        if (hotspot != null) guideTo(hotspot);
    }

    // from skyhanni IslandType.isInBounds: a curve pointing off the island isnt a hotspot. an island without a
    // box takes any guess
    static Vec3 onIsland(String island, Vec3 guess) {
        return DianaData.inBounds(island, guess) ? guess : null;
    }

    // from skyhanni pathFind findClosestNode: closest node tagged hotspot, if its close enough to be this one
    static Graph.Node closestHotspot(Graph g, Vec3 at) {
        if (g == null) return null;
        Graph.Node best = null;
        double bestDistance = NODE_RADIUS;
        for (Graph.Node n : g.nodes) {
            if (!n.tags().contains(HOTSPOT_TAG)) continue;
            double d = n.pos().distanceTo(at);
            if (d < bestDistance) {
                bestDistance = d;
                best = n;
            }
        }
        return best;
    }

    private static void guideTo(Vec3 at) {
        arrivedAt = 0;
        if (!option("path", true)) return;
        Graph.Node node = closestHotspot(Repo.graph(GameState.island), at);
        Navigator.Target next = node != null ? new Navigator.Target(GameState.island, "Fishing Hotspot", node.pos()) : new Navigator.Target(GameState.island, "Unknown Fishing Hotspot", at);
        if (guide != null && Navigator.target() == guide) {
            if (guide.equals(next)) return;
            Navigator.retarget(next);
        } else {
            Navigator.navigate(next);
        }
        guide = next;
    }

    private static void stopGuide() {
        if (guide != null && Navigator.target() == guide) Navigator.stop();
        guide = null;
        arrivedAt = 0;
    }

    private static boolean onItemClick(boolean right) {
        if (!right || !Features.on(ID)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !RADAR.equals(Gain.sbId(mc.player.getMainHandItem()))) return false;
        long now = System.currentTimeMillis();
        if (now - lastParticle < 200) return false;
        FITTER.reset();
        lastAbilityUse = now;
        return false;
    }

    private static void reset() {
        hotspot = null;
        lastAbilityUse = 0;
        FITTER.reset();
        stopGuide();
    }

    private static void draw(WorldDraw w) {
        if (w.mc.level != world) {
            world = w.mc.level;
            reset();
        }
        if (arrivedAt != 0 && System.currentTimeMillis() - arrivedAt >= PATH_AFTER_ARRIVAL_MS) stopGuide();
        if (guide != null && !option("path", true)) stopGuide();
        Vec3 at = hotspot;
        if (at == null || w.mc.player == null) return;
        double distance = at.distanceTo(w.mc.player.getEyePosition());
        if (option("line", true)) w.line(w.mc.player.getEyePosition().add(w.mc.player.getLookAngle().scale(0.8)), at, 0xFFFF55FF, 3f, option("through_walls", false));
        if (distance <= 10) {
            // guide goes 5 sec later like skyhannis foundTime
            hotspot = null;
            FITTER.reset();
            arrivedAt = System.currentTimeMillis();
            return;
        }
        w.dynamicText(at.add(-0.5, 1.7, -0.5), "§d§lHOTSPOT", 1.7, 0);
        w.dynamicText(at.add(-0.5, 1.6 - distance / (12 * 1.7), -0.5), "§e" + String.format(java.util.Locale.ROOT, "%,d", (int) distance) + "m", 1.0, 0);
    }

    public static void register() {
        Features.add(new Feature(ID, "Fishing", "Hotspot radar", "Where your Hotspot Radar points: the distance to the hotspot and the way there, after you right-click it.", "SkyHanni: FishingHotspotRadar")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).world(HotspotRadar::draw).left(HotspotRadar::reset)
                .option(Option.bool("col_hotspot.path", "Guide to the hotspot", "Shows the way along the island's paths, to the closest hotspot it knows.", true))
                .option(Option.bool("col_hotspot.line", "Line to the hotspot", "A straight line from you to the hotspot.", true))
                .option(Option.bool("col_hotspot.through_walls", "Line through walls", "Also draws the line behind blocks.", false)));
        ParticleHooks.listen(HotspotRadar::onParticle);
        Interactions.onItemClick(HotspotRadar::onItemClick);
    }
}
