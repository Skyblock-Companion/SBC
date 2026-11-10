// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

// the rest of mining: everything dwarven mines, hollows, glacite tunnels and fossil excavator that isnt in
// the older mining files
// each class ports one skyhanni or skyblocker class and registers itself from register(). this file just
// holds what they all need
public final class MiningPack {
    private MiningPack() {}

    public static boolean skyblock() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    static boolean dwarven() {
        return "DWARVEN_MINES".equals(GameState.island) || SbcMod.DEV;
    }

    static boolean hollows() {
        return "CRYSTAL_HOLLOWS".equals(GameState.island) || SbcMod.DEV;
    }

    // from skyhanni IslandTypeTag.MINING
    static boolean mining() {
        return MiningEnv.isMiningIsland() || SbcMod.DEV;
    }

    // a features switch. off if the feature or switch doesnt exist, so a test without features stays quiet
    static boolean on(String feature, String key) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o != null && o.bool() && f.enabled();
    }

    static int number(String feature, String key, int fallback) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? fallback : (int) o.number();
    }

    static int color(String feature, String key, int fallback) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? fallback : o.color();
    }

    static String choice(String feature, String key, String fallback) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? fallback : o.choice();
    }

    static void chat(String text) {
        MiningEnv.world.chat(text);
    }

    // chat line that runs a command on click, never by itself
    static void click(String text, String command, String hover) {
        MiningEnv.world.clickableChat(text, command, hover);
    }

    static String heldId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        ItemStack st = mc.player.getMainHandItem();
        return st.isEmpty() ? "" : Gain.sbId(st);
    }

    static boolean inInventory(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) if (!st.isEmpty() && id.equals(Gain.sbId(st))) return true;
        return false;
    }

    public static void register() {
        NpcHelpers.register();
        ColdOverlay.register();
        Carpets.register();
        CrystalWaypoints.register();
        CrystalCore.register();
        FossilExcavator.register();
        MetalDetector.register();
        TreasureChests.register();
        OreMined.register();
        Flowstate.register();
        HotmPerks.register();
        Pickobulus.register();
        CommissionMarks.register();
        MineshaftPity.register();
        GoblinMarks.register();
        DeepCaverns.register();
        TunnelMaps.register();
        MiningChat.register();
        MiningExtrasOff.register();
        CommissionBlocks.register();
        OrderedWaypoints.register();
        BlockStrength.register();
    }
}
