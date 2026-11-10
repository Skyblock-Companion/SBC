// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.garden.GardenPack;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLVideo;

// from skyhanni SeeThroughWindow: a key makes the game window see-through so you can watch something behind
// it while farming
public final class SeeThrough {
    public static final String ID = "garden_see_through";
    private static KeyMapping key;
    private static boolean active, unsupported;
    private static float current = 1f;
    private static Object level;

    private SeeThrough() {}

    private static void apply(Minecraft mc) {
        if (unsupported) return;
        float target = active ? Math.max(0.05f, Math.min(1f, GardenPack.number(ID, "percent", 50) / 100f)) : 1f;
        if (current == target) return;
        long handle = mc.getWindow().handle();
        if (SDLVideo.SDL_SetWindowOpacity(handle, target)) current = target;
        else {
            unsupported = true;
            GardenPack.chat("§cYour window system does not support a see-through window.");
        }
    }

    private static void tick(Minecraft mc) {
        if (key == null) return;
        while (key.consumeClick()) if (mc.gui.screen() == null && Features.on(ID)) {
            active = !active;
            apply(mc);
        }
        // onWorldChange
        if (mc.level != level) {
            level = mc.level;
            active = false;
            apply(mc);
        }
    }

    public static void keys(KeyMapping.Category category) {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.see_through", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    public static void register() {
        Feature f = new Feature(ID, "Garden", "See-through window", "A key in the game's Controls makes the window see-through while you farm. It goes back when you change world.", "SkyHanni: See Through Farming")
                .defaultOff()
                .left(() -> {
                    active = false;
                    apply(Minecraft.getInstance());
                })
                .option(Option.integer("garden_see_through.percent", "Opacity (%)", "How much of the game you still see.", 50, 5, 100));
        Features.add(f);
        Safe.endTick("SeeThrough.tick", SeeThrough::tick);
    }
}
