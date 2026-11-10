// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Modern Warp Menu; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.ui.screens;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.muzy.sbc.mc.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

// from modern warp menu BlitExtension + BlitFloatRenderState (MIT): whole texture at fractional coords with
// linear filtering. thats why the islands grow smooth and stay clean at any size
final class WarpBlit {
    private WarpBlit() {}

    static void blit(GuiGraphicsExtractor gui, Identifier texture, float x, float y, float w, float h, int color) {
        GpuTextureView view = Minecraft.getInstance().getTextureManager().getTexture(texture).getTextureView();
        ((GuiGraphicsAccessor) gui).sbc$guiRenderState().addGuiElement(new State(RenderPipelines.GUI_TEXTURED,
                TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                new Matrix3x2f(gui.pose()), x, y, x + w, y + h, 0f, 1f, 0f, 1f, color, null));
    }

    private record State(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float x0, float y0, float x1, float y1, float u0, float u1, float v0, float v1, int color,
                         @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        State(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float x0, float y0, float x1, float y1, float u0, float u1, float v0, float v1, int color, @Nullable ScreenRectangle scissorArea) {
            this(pipeline, textureSetup, pose, x0, y0, x1, y1, u0, u1, v0, v1, color, scissorArea, bounds((int) x0, (int) y0, (int) x1, (int) y1, pose, scissorArea));
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
            consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
            consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
            consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
        }

        @Nullable
        private static ScreenRectangle bounds(int x0, int y0, int x1, int y1, Matrix3x2f pose, @Nullable ScreenRectangle scissorArea) {
            ScreenRectangle r = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
            return scissorArea != null ? scissorArea.intersection(r) : r;
        }
    }
}
