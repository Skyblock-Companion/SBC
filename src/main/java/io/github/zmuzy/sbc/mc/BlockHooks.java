// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// block changes from the server. like skyhanni ServerBlockChangeEvent / skyblocker WorldEvents.BLOCK_STATE_UPDATE
// pos can be mutable, so if a listener keeps it, call immutable() first
public final class BlockHooks {
    public interface Listener {
        void on(BlockPos pos, BlockState oldState, BlockState newState);
    }

    private record Named(String name, Listener listener) {}

    private static final List<Named> LISTENERS = new CopyOnWriteArrayList<>();

    private BlockHooks() {}

    public static void listen(Listener l) {
        LISTENERS.add(new Named("block listener " + l.getClass().getName(), l));
    }

    public static void on(BlockPos pos, BlockState oldState, BlockState newState) {
        if (oldState == newState) return;
        for (Named l : LISTENERS) {
            if (Safe.resting(l.name)) continue;
            try {
                l.listener.on(pos, oldState, newState);
            } catch (Throwable t) {
                Safe.caught(l.name, t);
            }
        }
    }
}
