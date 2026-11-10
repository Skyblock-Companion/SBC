// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.FrameProbe;
import io.github.zmuzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.thread.BlockableEventLoop;

// /sbc perf record: the tasks queued for the game thread
@Mixin(BlockableEventLoop.class)
public abstract class ProbeTasksMixin {
    @Inject(method = "runAllTasks", at = @At("HEAD"), require = 0)
    private void sbc$runAllTasksIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.TASKS);
        } catch (Throwable t) {
            Safe.caught("ProbeTasksMixin.runAllTasksIn", t);
        }
    }

    @Inject(method = "runAllTasks", at = @At("RETURN"), require = 0)
    private void sbc$runAllTasksOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.TASKS);
        } catch (Throwable t) {
            Safe.caught("ProbeTasksMixin.runAllTasksOut", t);
        }
    }
}
