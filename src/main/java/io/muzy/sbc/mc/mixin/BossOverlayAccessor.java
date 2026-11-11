// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

// boss bars the server shows rn (skyhanni BossbarData reads the same packets)
@Mixin(BossHealthOverlay.class)
public interface BossOverlayAccessor {
    @Accessor("events")
    Map<UUID, LerpingBossEvent> sbc$events();
}
