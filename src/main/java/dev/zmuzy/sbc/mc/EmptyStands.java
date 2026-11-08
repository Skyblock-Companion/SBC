// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc;

import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

// skyblock is full of empty invisible armor stands (hypixels hologram + effect anchors)
// the game still extracts and submits every one each frame lol, we skip the ones showing nothing
public final class EmptyStands {
    public static final String ID = "skip_empty_stands";

    private EmptyStands() {}

    private static final EquipmentSlot[] SLOTS = EquipmentSlot.values();

    public static boolean pointless(Entity e) {
        if (!(e instanceof ArmorStand a) || !a.isInvisible() || a.isCustomNameVisible() || a.isCurrentlyGlowing() || a.isVehicle()) return false;
        for (EquipmentSlot s : SLOTS) if (!a.getItemBySlot(s).isEmpty()) return false;
        return true;
    }

    private static long epochSeen = -1;
    private static boolean on;

    // asked for every entity every frame, so the switch once a tick and a stand once a sec
    public static boolean hidden(Entity e) {
        if (!(e instanceof ArmorStand)) return false;
        long ep = dev.zmuzy.sbc.feature.Features.epoch;
        if (ep != epochSeen) {
            on = Features.on(ID);
            epochSeen = ep;
        }
        return on && EntityTickCache.testEvery(EntityTickCache.EMPTY_STAND, e, EmptyStands::pointless, 20);
    }

    public static void register() {
        Features.add(new Feature(ID, "Misc", "Skip empty armor stands", "Invisible armor stands that hold nothing and show no name are not drawn. Hypixel leaves hundreds of them in every area; skipping them costs no picture and frees the frame.", "")
                .when(() -> dev.zmuzy.sbc.core.GameState.inSkyBlock));
    }
}
