// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// one map marker as the game draws it, so the dungeon map can leave out yours and draw its own arrow
@Mixin(MapRenderer.class)
public interface MapRendererAccess {
    @Invoker("extractDecorationRenderState")
    MapRenderState.MapDecorationRenderState sbc$decoration(MapDecoration decoration);
}
