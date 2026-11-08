// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// port of skyhanni rift/area/dreadfarm/WoodenButtonsHelper.kt: while the buttons enigma soul is tracked, the
// nearest house spot (graph nodes tagged rift_buttons_quest) with buttons left
public final class WoodenButtonsHelper {
    static final Patterns.P BUTTON_HIT = Patterns.p("SH.rift.area.dreadfarm.buttons.hit");
    public static final String BLOWGUN = "BERBERIS_BLOWGUN";

    static final Set<Vec3> hitButtons = new HashSet<>();
    static Vec3 lastHitButton;
    static Graph.Node currentSpot;
    static long lastBlowgunFire = RiftEnv.FAR_PAST;

    public static boolean showButtonsHelper = true;
    public static int color = 0xFFAA00AA;

    private WoodenButtonsHelper() {}

    public static void onWorldChange() {
        hitButtons.clear();
        RiftApi.allButtonsHit = false;
        currentSpot = null;
    }

    // from skyhanni buttonLocations["${node.name}:${node.position}"]: a graph nodes buttons
    static List<Vec3> buttonsOf(Graph.Node node) {
        if (node.name() == null) return null;
        for (var e : RiftData.buttonLocations.entrySet()) {
            int i = e.getKey().indexOf(':');
            if (i < 0 || !e.getKey().substring(0, i).equals(node.name())) continue;
            Vec3 p = RiftEnv.vec(e.getKey().substring(i + 1));
            if (p.distanceToSqr(node.pos()) < 0.01) return e.getValue();
        }
        return null;
    }

    // from skyhanni findClosestSpot on the island graph
    public static void tick() {
        if (!showButtons()) return;
        Graph g = Repo.graph("THE_RIFT");
        if (g == null) return;
        Vec3 player = RiftEnv.world.playerPos();
        Graph.Node closest = null;
        double best = Double.MAX_VALUE;
        for (Graph.Node node : g.nodes) {
            if (!node.tags().contains("rift_buttons_quest")) continue;
            List<Vec3> buttons = buttonsOf(node);
            if (buttons == null || buttons.stream().allMatch(hitButtons::contains)) continue;
            double d = node.pos().distanceToSqr(player);
            if (d < best) {
                best = d;
                closest = node;
            }
        }
        if (closest != currentSpot) currentSpot = closest;
    }

    // from skyhanni onBlockClick on an oak button
    public static void onButtonClick(Vec3 location) {
        if (!checkButtons()) return;
        if (!hitButtons.contains(location)) lastHitButton = location;
    }

    // from skyhanni onItemClick: right click with the blowgun
    public static void onBlowgunFire() {
        if (!checkButtons()) return;
        lastBlowgunFire = RiftEnv.now();
    }

    // from skyhanni checkBlowgunActivatedButtons: a powered visible button within 2.5 sec of a shot counts as hit
    public static void onPoweredVisibleButton(Vec3 buttonLocation) {
        if (RiftEnv.now() - lastBlowgunFire > 2500) return;
        if (buttonLocation.equals(lastHitButton) || hitButtons.contains(buttonLocation)) return;
        lastHitButton = buttonLocation;
        addLastHitButton();
    }

    public static boolean shouldCheckPoweredButtons() {
        return checkButtons() && RiftEnv.now() - lastBlowgunFire <= 2500;
    }

    public static List<Vec3> allButtons() {
        List<Vec3> out = new ArrayList<>();
        for (List<Vec3> l : RiftData.buttonLocations.values()) out.addAll(l);
        return out;
    }

    private static void addLastHitButton() {
        if (lastHitButton != null) hitButtons.add(lastHitButton);
    }

    // from skyhanni onChat with the formatted line
    public static boolean onChat(String formatted) {
        if (!checkButtons()) return false;
        if (BUTTON_HIT.matcher(formatted).matches()) {
            addLastHitButton();
            return true;
        }
        if (!formatted.equals("§eYou've hit all §r§b56 §r§ewooden buttons!")) return false;
        RiftApi.allButtonsHit = true;
        hitButtons.addAll(allButtons());
        return true;
    }

    public static void render(WorldDraw w) {
        if (!showButtons()) return;
        Graph.Node spot = currentSpot;
        if (spot == null) return;
        double distance = spot.pos().distanceTo(RiftEnv.world.playerPos());
        if (distance > 2.5) w.label("Hit Buttons Here!", spot.pos().add(0, 1, 0), true);
        if (distance > 15.0) return;
        List<Vec3> buttons = buttonsOf(spot);
        if (buttons == null) return;
        for (Vec3 b : buttons) if (!hitButtons.contains(b)) w.box(new AABB(b, b.add(1, 1, 1)), color, true, true);
    }

    public static int hitCount() {
        return hitButtons.size();
    }

    static boolean checkButtons() {
        return RiftApi.inRift() && !RiftApi.allButtonsHit;
    }

    public static boolean showButtons() {
        return checkButtons() && RiftApi.trackingButtons && showButtonsHelper;
    }
}
