// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.garden.GardenPack;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni GardenWarpCommands: on your garden /home, /barn and /tp plot do what you mean, plus 3 keys
// (unbound at first) for spawn, set spawn and barn
// each sends one command bc you typed or pressed it. off by default since it changes what your commands do
public final class GardenWarps {
    public static final String ID = "garden_warps";
    private static KeyMapping homeKey, setHomeKey, barnKey;

    private GardenWarps() {}

    // command for what you typed, no slash. null = send as is
    static String rewrite(String typed) {
        String message = typed.toLowerCase(Locale.ROOT);
        if (message.equals("home")) return "warp garden";
        if (message.equals("barn")) return "plottp barn";
        Matcher m = Patterns.get("SH.garden.warpcommand.tpplot").matcher("/" + typed);
        return m.matches() ? "plottp " + m.group("plot") : null;
    }

    private static boolean onCommand(String command) {
        if (!Features.on(ID) || !GardenPack.garden()) return true;
        String to = rewrite(command.trim());
        if (to == null) return true;
        GardenPack.command(to);
        return false;
    }

    private static void tick(Minecraft mc) {
        if (homeKey == null) return;
        boolean ok = mc.gui.screen() == null && GardenPack.garden() && Features.on(ID);
        while (homeKey.consumeClick()) if (ok) GardenPack.command("warp garden");
        while (setHomeKey.consumeClick()) if (ok) GardenPack.command("setspawn");
        while (barnKey.consumeClick()) if (ok) GardenPack.command("plottp barn");
    }

    public static void keys(KeyMapping.Category category) {
        int none = InputConstants.UNKNOWN.getValue();
        homeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.garden_home", InputConstants.Type.KEYBOARD, none, category));
        setHomeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.garden_sethome", InputConstants.Type.KEYBOARD, none, category));
        barnKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.garden_barn", InputConstants.Type.KEYBOARD, none, category));
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Garden warp commands", "On your Garden: /home goes to your Garden spawn, /barn to the barn and /tp <plot> to a plot. Also the keys for them in the game's Controls.", "SkyHanni: Garden Commands")
                .when(GardenPack::garden).defaultOff());
        Safe.allowCommand("GardenWarps.onCommand", GardenWarps::onCommand);
        Safe.endTick("GardenWarps.tick", GardenWarps::tick);
    }
}
