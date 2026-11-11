// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.diana;

import net.minecraft.world.phys.Vec3;

// from skyhanni BurrowApi: last burrow related chat line + last burrow block clicked
public final class BurrowApi {
    public static long lastBurrowRelatedChatMessage = DianaEnv.FAR_PAST;
    public static Vec3 lastBurrowInteracted;

    private BurrowApi() {}

    public static void setBurrowInteracted(Vec3 interacted) {
        if (interacted != null) GriffinBurrowHelper.addDebug("set last interacted burrow to [" + interacted.x + ", " + interacted.y + ", " + interacted.z + "]");
        else GriffinBurrowHelper.addDebug("set last interacted burrow to null");
        lastBurrowInteracted = interacted;
    }

    public static void reset() {
        lastBurrowRelatedChatMessage = DianaEnv.FAR_PAST;
        lastBurrowInteracted = null;
    }
}
