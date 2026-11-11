// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.world;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni inventory/SkyBlockXPBar + SkyBlockXPApi: the vanilla xp bar shows your sb level, except in
// the catacombs and the rift. your own numbers get swapped in just for the draw
public final class SkyblockXpBar {
    public static final String ID = "skyblock_xp_bar";
    // tab lists "SB Level: [292] 45/100 XP"
    private static final Pattern LEVEL = Pattern.compile("^SB Level: \\[(\\d+)\\] (\\d+).*$");

    private static float oldProgress;
    private static int oldTotal, oldLevel;

    private SkyblockXpBar() {}

    // level + xp out of 100 from the tab, null if it doesnt say
    static int[] read(Iterable<String> tab) {
        for (String t : tab) {
            Matcher m = LEVEL.matcher(t.trim());
            if (m.matches()) return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return null;
    }

    // true = numbers got swapped and end() has to run
    public static boolean begin() {
        if (!GameState.inSkyBlock || !Features.on(ID) || "CATACOMBS".equals(GameState.island) || "THE_RIFT".equals(GameState.island)) return false;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        int[] now = read(GameState.tab);
        if (now == null) return false;
        oldProgress = p.experienceProgress;
        oldTotal = p.totalExperience;
        oldLevel = p.experienceLevel;
        p.experienceProgress = now[1] / 100f;
        p.totalExperience = 100;
        p.experienceLevel = now[0];
        return true;
    }

    public static void end() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.experienceProgress = oldProgress;
        p.totalExperience = oldTotal;
        p.experienceLevel = oldLevel;
    }

    public static void register() {
        Features.add(new Feature(ID, "HUD", "SkyBlock level on the XP bar", "The game's XP bar shows your SkyBlock level, except in the Catacombs and the Rift. Switch off hiding the XP bar under Skill progress to see it.", "SkyHanni: SkyBlock XP Bar")
                .defaultOff());
    }
}
