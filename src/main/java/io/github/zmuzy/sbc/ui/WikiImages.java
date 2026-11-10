// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.zmuzy.sbc.core.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// wiki pictures as textures. only asked for when on screen, a few at a time bc a long page has hundreds. mc
// reads png, the few gifs get their first frame turned into one
final class WikiImages {
    private static final int AT_ONCE = 8;
    private static final Map<String, Identifier> READY = new HashMap<>();
    private static final Map<String, Boolean> ASKED = new HashMap<>();
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static final AtomicInteger IDS = new AtomicInteger();
    private static int running;

    private WikiImages() {}

    // null while its still loading
    static Identifier get(String url) {
        Identifier id = READY.get(url);
        if (id != null) return id;
        if (ASKED.putIfAbsent(url, true) == null) {
            QUEUE.addFirst(url); // what you just scrolled to goes first
            next();
        }
        return null;
    }

    private static void next() {
        while (running < AT_ONCE && !QUEUE.isEmpty()) {
            String url = QUEUE.pollFirst();
            running++;
            Net.getBytes(url).whenCompleteAsync((bytes, e) -> {
                NativeImage image = null;
                if (e == null) {
                    try {
                        image = NativeImage.read(new ByteArrayInputStream(png(url, bytes)));
                    } catch (Exception ignored) {
                        // unreadable picture = blank space
                    }
                }
                NativeImage done = image;
                Minecraft.getInstance().execute(() -> {
                    running--;
                    if (done != null) {
                        if (ASKED.containsKey(url)) {
                            Identifier id = Identifier.fromNamespaceAndPath("sbcompanion", "wiki/" + IDS.incrementAndGet());
                            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "sbcompanion wiki picture", done));
                            READY.put(url, id);
                        } else {
                            done.close();
                        }
                    }
                    next();
                });
            }, Util.backgroundExecutor());
        }
    }

    private static byte[] png(String url, byte[] bytes) throws Exception {
        if (bytes.length > 3 && bytes[0] == (byte) 0x89 && bytes[1] == 'P') return bytes;
        var picture = javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes));
        if (picture == null) throw new IllegalArgumentException("Not a picture: " + url);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(picture, "png", out);
        return out.toByteArray();
    }

    // screen closed, free the memory
    static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Identifier id : READY.values()) mc.getTextureManager().release(id);
        READY.clear();
        ASKED.clear();
        QUEUE.clear();
    }
}
