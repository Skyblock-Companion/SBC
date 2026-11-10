// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.screens;

import com.mojang.blaze3d.platform.NativeImage;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.data.DataImages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// warp menu island pictures: the skyblock wiki renders (CC BY-NC-SA 3.0). theyre big so they live in the data
// repo not the jar. each loads under the same texture name the layout uses
public final class WarpImages {
    private static final String TEXTURE_FOLDER = "textures/gui/warp/islands/";
    private static final Set<String> uploaded = ConcurrentHashMap.newKeySet(), loading = ConcurrentHashMap.newKeySet();

    private WarpImages() {}

    // picture file name if this is a downloaded texture, else null
    static String fileOf(Identifier id) {
        if (!"sbcompanion".equals(id.getNamespace()) || !id.getPath().startsWith(TEXTURE_FOLDER)) return null;
        return id.getPath().substring(TEXTURE_FOLDER.length());
    }

    // can it be drawn. first ask starts fetching + reading it off the game thread
    static boolean ready(Identifier id) {
        String name = fileOf(id);
        if (name == null || uploaded.contains(name)) return true;
        if (loading.add(name)) DataImages.fetch("islands/" + name).whenComplete((file, problem) -> {
            if (problem != null) {
                SbcMod.LOG.info("Could not get the island picture {}: {}", name, problem.toString());
                return;
            }
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                Minecraft.getInstance().execute(() -> upload(id, name, image));
            } catch (Exception e) {
                SbcMod.LOG.info("Could not read the island picture {}: {}", name, e.toString());
            }
        });
        return false;
    }

    private static void upload(Identifier id, String name, NativeImage image) {
        DynamicTexture texture = new DynamicTexture(() -> "sbcompanion warp " + name, image);
        // picture is on the gpu now, theyre big so drop the copy in memory
        texture.setPixels(new NativeImage(1, 1, false));
        Minecraft.getInstance().getTextureManager().register(id, texture);
        uploaded.add(name);
    }
}
