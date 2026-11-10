// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.areas;

import io.github.zmuzy.sbc.combat.CombatMarks;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;

// from skyhanni fishing/CommonSeaCreatureHider: while you fish the non rare sea creatures go see through so
// the rare ones stand out. uses the same see through hook as the slayer hider
public final class SeaCreatureHider {
    public static final String ID = "fishing_common_hider";
    private static final Set<Integer> COMMON = new HashSet<>();

    private SeaCreatureHider() {}

    private static boolean active() {
        if (!Features.on(ID) || !FishState.fishing()) return false;
        return !Features.get(ID).option("fishing_common_hider.only_rod").bool() || FishState.holdingRod;
    }

    // what skyhannis mob detection gives: the living thing under the name of a creature the repo doesnt call rare
    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 10 != 0) return;
        COMMON.clear();
        if (!active()) return;
        for (FishState.Live l : FishState.live()) {
            if (l.kind.rare() || !(mc.level.getEntity(l.stand) instanceof ArmorStand st)) continue;
            for (Entity b : mc.level.getEntities(st, st.getBoundingBox().inflate(0.5, 3, 0.5))) {
                if (b instanceof LivingEntity && !(b instanceof ArmorStand) && !(b instanceof Player)) {
                    COMMON.add(b.getId());
                    break;
                }
            }
        }
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Fishing", "Common sea creature hider", "While you fish, sea creatures that are not rare turn see-through, so the rare ones stand out.", "SkyHanni: Common Sea Creature Hider")
                .when(() -> GameState.inSkyBlock).defaultOff().tick(SeaCreatureHider::tick).left(COMMON::clear)
                .option(Option.bool("fishing_common_hider.only_rod", "Only while holding a rod", "", true))
                .option(Option.integer("fishing_common_hider.visibility", "Visibility (%)", "How visible they stay.", 50, 0, 100)));
        CombatMarks.fading(SeaCreatureHider::active, e -> COMMON.contains(e.getId()), () -> f.option("fishing_common_hider.visibility").integer());
    }
}
