// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.crimson;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;

// from skyhanni KuudraProfileViewerBlocker: on the kuudra island right clicking a player does nothing, so
// hypixel's player menu cant pop up mid fight. npcs still work. off by default like there
final class KuudraPlayerClick {
    static final String ID = "kuudra_no_player_menu";

    private KuudraPlayerClick() {}

    // EntityUtils.isNpc: an npc's player entity has a version 2 uuid
    static boolean realPlayer(Entity entity) {
        return entity instanceof RemotePlayer && entity.getUUID().version() != 2;
    }

    static void register() {
        Features.add(new Feature(ID, "Crimson Isle", "No player menu in Kuudra", "On the Kuudra island a right click on another player does nothing, so their profile menu cannot open during the fight.", "SkyHanni: Disable Profile Viewer in Kuudra")
                .defaultOff().when(() -> "KUUDRA".equals(GameState.island)));
        Safe.useEntity("KuudraPlayerClick.useEntity", (player, level, hand, entity, hit) -> {
            if (!level.isClientSide() || !Features.on(ID) || !"KUUDRA".equals(GameState.island) || !realPlayer(entity)) return InteractionResult.PASS;
            return InteractionResult.FAIL;
        });
    }
}
