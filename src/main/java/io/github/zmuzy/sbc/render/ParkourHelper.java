// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// port of skyhanni utils/ParkourHelper.kt: platforms you hop along. the next lookAhead ones get drawn as
// filled blocks joined by lines, shortcuts in red
public final class ParkourHelper {
    public record ShortCut(int from, int to) {}

    // from skyhanni ParkourJson
    public record Data(List<Vec3> locations, List<ShortCut> shortCuts) {}

    private final List<Vec3> locations;
    private final List<ShortCut> shortCuts;
    private final double platformSize, detectionRange;
    private final boolean depth;
    private final Runnable onEndReach;
    private final boolean goInOrder;

    private int current = -1;
    private boolean visible;
    public boolean rainbowColor;
    public int monochromeColor = 0xFFFFFFFF;
    public int lookAhead = 2;
    public boolean outline;
    public boolean showEverything;

    // player pos + on ground, passed in so the helper is testable
    public Supplier<Vec3> playerPos = () -> Vec3.ZERO;
    public BooleanSupplier onGround = () -> true;

    public ParkourHelper(List<Vec3> locations, List<ShortCut> shortCuts, double platformSize, double detectionRange, boolean depth, Runnable onEndReach, boolean goInOrder) {
        this.locations = locations;
        this.shortCuts = shortCuts == null ? List.of() : shortCuts;
        this.platformSize = platformSize;
        this.detectionRange = detectionRange;
        this.depth = depth;
        this.onEndReach = onEndReach == null ? () -> {} : onEndReach;
        this.goInOrder = goInOrder;
    }

    public ParkourHelper(Data data, double platformSize, double detectionRange) {
        this(data.locations(), data.shortCuts(), platformSize, detectionRange, true, null, false);
    }

    public boolean inParkour() {
        return current != -1;
    }

    public int current() {
        return current;
    }

    public Vec3 getStartLocation() {
        return locations.get(0);
    }

    public void reset() {
        current = -1;
        visible = false;
    }

    private Vec3 offsetCenter(Vec3 v) {
        return v.add(platformSize / 2, 1.0, platformSize / 2);
    }

    private double distanceToPlayer(Vec3 v) {
        return v.distanceTo(playerPos.get());
    }

    // the tracking half of render(): updates current/visible, true = something to draw
    public boolean update() {
        if (locations.isEmpty()) return false;
        if (!showEverything) {
            if (current == locations.size() - 1) visible = false;
            if (visible) {
                for (int index = 0; index < locations.size(); index++) {
                    boolean closeEnough = distanceToPlayer(offsetCenter(locations.get(index))) < detectionRange;
                    if (!(closeEnough && onGround.getAsBoolean())) continue;
                    if (goInOrder && (index < current - 1 || index > current + 1)) continue;
                    current = index;
                }
            }
            double distanceToPlayer = distanceToPlayer(offsetCenter(locations.get(0)));
            if (distanceToPlayer < detectionRange) visible = true;
            else if (distanceToPlayer > 15 && current < 1) visible = false;
            if (!visible) return false;
            if (current < 0) return false;
        } else {
            current = 0;
            lookAhead = locations.size();
        }
        if (locations.size() == current + 1) onEndReach.run();
        return true;
    }

    public void render(WorldDraw w) {
        if (!update()) return;
        try {
            Vec3[] inProgress = getInProgressPair();
            int end = Math.min(locations.size() - 1, current + lookAhead - 1);
            for (int i = current; i < end; i++)
                w.line(offsetCenter(locations.get(i)), offsetCenter(locations.get(i + 1)), colorForIndex(i), 5f, !depth);
            if (inProgress != null) w.line(offsetCenter(inProgress[0]), offsetCenter(inProgress[1]), colorForIndex(current + lookAhead - 1), 5f, !depth);
            renderShortCuts(w);
            renderSteps(w, inProgress);
        } catch (Throwable ignored) {
        }
    }

    private void renderShortCuts(WorldDraw w) {
        for (ShortCut s : shortCuts) {
            if (s.from() < current || s.from() >= current + lookAhead || s.to() < 0 || s.to() >= locations.size()) continue;
            Vec3 from = offsetCenter(locations.get(s.from())), to = offsetCenter(locations.get(s.to()));
            w.line(from, to, 0xFFFF0000, 3f, !depth);
            Vec3 textLocation = from.add(to.subtract(from).normalize());
            w.label("§cShortcut", textLocation.add(-0.5, 1.0, -0.5), true);
            AABB aabb = axisAlignedBB(locations.get(s.to()));
            w.box(aabb, 0xFFFF0000, true, !depth);
            if (outline) w.box(aabb, 0xFF000000, false, !depth);
        }
    }

    private void renderSteps(WorldDraw w, Vec3[] inProgress) {
        int end = Math.min(locations.size(), current + lookAhead);
        for (int i = current; i < end; i++) {
            AABB aabb = axisAlignedBB(locations.get(i));
            w.box(aabb, colorForIndex(i), true, !depth);
            if (outline) w.box(aabb, 0xFF000000, false, !depth);
        }
        if (inProgress != null && !showEverything) {
            // the moving platform: a lighter filled box
            AABB aabb = axisAlignedBB(inProgress[1]).inflate(0.1);
            w.box(aabb, (colorForIndex(current + lookAhead) & 0x00FFFFFF) | 0x99000000, true, !depth);
        }
    }

    // from skyhanni getInProgressPair: lookAheadStart + slopeLocation, or null
    private Vec3[] getInProgressPair() {
        if (current < 0 || current + lookAhead >= locations.size()) return null;
        Vec3 currentPosition = offsetCenter(locations.get(current));
        Vec3 nextPosition = offsetCenter(locations.get(current + 1));
        Vec3 lookAheadStart = locations.get(current + lookAhead - 1);
        Vec3 lookAheadEnd = locations.get(current + lookAhead);
        Vec3 player = playerPos.get();
        if (player.distanceTo(nextPosition) > currentPosition.distanceTo(nextPosition)) return null;
        double factor = player.distanceTo(currentPosition) / currentPosition.distanceTo(nextPosition);
        Vec3 slope = lookAheadStart.add(lookAheadEnd.subtract(lookAheadStart).scale(factor));
        return new Vec3[]{lookAheadStart, slope};
    }

    private AABB axisAlignedBB(Vec3 loc) {
        return new AABB(loc.x, loc.y, loc.z, loc.x + platformSize, loc.y + 1.0, loc.z + platformSize).inflate(0.001);
    }

    private int colorForIndex(int index) {
        if (!rainbowColor) return monochromeColor;
        float hueOffset = (((-index / 12f) % 1f) + 1f) % 1f;
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(hueOffset, 1f, 0.7f) & 0x00FFFFFF);
    }
}
