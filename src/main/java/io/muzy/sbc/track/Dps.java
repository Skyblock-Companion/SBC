// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.track;

import io.muzy.sbc.core.GameState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// dps from the damage numbers hypixel spawns when you hit stuff (floating stands like "✧12,345✧"). each
// number counted once, dps = damage of the last 5 sec of fighting
public final class Dps {
    private static final io.muzy.sbc.core.Patterns.P DAMAGE = io.muzy.sbc.core.Patterns.p("Dps.DAMAGE");
    private static final ArrayDeque<long[]> hits = new ArrayDeque<>();
    private static final Set<Integer> seen = new HashSet<>();
    public static double peak;
    private static int ticks;

    private Dps() {}

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !GameState.inSkyBlock || ++ticks % 2 != 0) return;
        long now = System.currentTimeMillis();
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(14))) {
            if (!a.hasCustomName() || a.tickCount > 20 || !seen.add(a.getId())) continue;
            String name = GameState.strip(a.getCustomName().getString()).replace(" ", "");
            Matcher m = DAMAGE.matcher(name);
            if (!m.matches()) continue;
            long dmg = GameState.num(m.group(1));
            if (dmg > 0) hits.add(new long[]{now, dmg});
        }
        while (!hits.isEmpty() && now - hits.peekFirst()[0] > 5000) hits.pollFirst();
        if (seen.size() > 5000) seen.clear();
        double d = dps();
        if (d > peak) peak = d;
    }

    public static double dps() {
        if (hits.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        long first = hits.peekFirst()[0];
        double secs = Math.max(1, Math.min(5, (now - first) / 1000.0));
        long sum = 0;
        for (long[] h : hits) sum += h[1];
        return sum / secs;
    }
}
