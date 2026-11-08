// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;

// the rest of the garden: farming tools, greenhouse, pests and their keys that dont have a card file already
// each class here is a port of one skyhanni or skyblocker class and registers from register(). this file only
// has what they all need
public final class GardenPack {
    private GardenPack() {}

    public static boolean garden() {
        return "GARDEN".equals(GameState.island) || SbcMod.DEV;
    }

    public static boolean skyblock() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    // a feature's switch. off if the feature or switch doesnt exist so a test without features stays quiet
    public static boolean on(String feature, String key) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    public static int number(String feature, String key, int fallback) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? fallback : (int) o.number();
    }

    public static String choice(String feature, String key, String fallback) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? fallback : String.valueOf(o.raw());
    }

    // skyhanni's TitleManager.sendTitle, the one way vanilla has
    public static void title(String text, int seconds) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(5, seconds * 20, 10);
        mc.gui.hud.setTitle(Component.literal(text));
        mc.gui.hud.setSubtitle(Component.empty());
    }

    public static void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[SBC] §r" + text));
    }

    public static void sound(SoundEvent sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(sound, 1f, pitch);
    }

    // a command you asked for with a key or click, nothing here sends one by itself
    public static void command(String command) {
        var c = Minecraft.getInstance().getConnection();
        if (c != null) c.sendCommand(command);
    }

    public static String heldId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "";
        ItemStack st = mc.player.getMainHandItem();
        return st.isEmpty() ? "" : Gain.sbId(st);
    }

    // ItemCategory.VACUUM
    public static boolean vacuumInHand() {
        return heldId().contains("VACUUM");
    }

    // ItemCategory.LASSO
    public static boolean lassoInHand() {
        return heldId().contains("LASSO");
    }

    // GardenApi.hasMousematInHand
    public static boolean mousematInHand() {
        return heldId().equals("SQUEAKY_MOUSEMAT");
    }

    // SprayonatorType: 30, 45 or 60 min by tool, -1 = no sprayonator
    public static int sprayonatorMinutes(String id) {
        return switch (id) {
            case "SPRAYONATOR" -> 30;
            case "JUICY_SPRAYONATOR" -> 45;
            case "SALTY_SPRAYONATOR" -> 60;
            default -> -1;
        };
    }

    // called from Builtin
    public static void register() {
        dev.zmuzy.sbc.garden.pests.PestAlerts.register();
        dev.zmuzy.sbc.garden.pests.PestTimer.register();
        dev.zmuzy.sbc.garden.pests.PestGuess.register();
        dev.zmuzy.sbc.garden.pests.SprayTracker.register();
        dev.zmuzy.sbc.garden.pests.StereoHarmony.register();
        dev.zmuzy.sbc.garden.pests.PestTraps.register();
        dev.zmuzy.sbc.garden.pests.MantidKills.register();
        dev.zmuzy.sbc.garden.pests.PesthunterProfit.register();
        dev.zmuzy.sbc.garden.pests.PestHighlight.register();
        dev.zmuzy.sbc.garden.greenhouse.DnaSolver.register();
        dev.zmuzy.sbc.garden.greenhouse.CropDiagnostics.register();
        dev.zmuzy.sbc.garden.greenhouse.PhantomleafSolver.register();
        dev.zmuzy.sbc.garden.greenhouse.GreenhousePaste.register();
        dev.zmuzy.sbc.garden.farming.MilestoneGoals.register();
        dev.zmuzy.sbc.garden.farming.MilestoneMenu.register();
        dev.zmuzy.sbc.garden.farming.MushroomPerk.register();
        dev.zmuzy.sbc.garden.farming.CropCommands.register();
        dev.zmuzy.sbc.garden.farming.CropStartLocation.register();
        dev.zmuzy.sbc.garden.farming.TrueFortune.register();
        dev.zmuzy.sbc.garden.farming.GardenLevel.register();
        dev.zmuzy.sbc.garden.farming.HoeLevel.register();
        dev.zmuzy.sbc.garden.farming.PreventBreaking.register();
        dev.zmuzy.sbc.garden.farming.ToolkitIcons.register();
        dev.zmuzy.sbc.garden.farming.BurrowingSpores.register();
        dev.zmuzy.sbc.garden.farming.RareCrops.register();
        dev.zmuzy.sbc.garden.farming.AtmosphericFilter.register();
        dev.zmuzy.sbc.garden.farming.YawPitch.register();
        dev.zmuzy.sbc.garden.farming.FarmingSigns.register();
        dev.zmuzy.sbc.garden.farming.WrongSpeed.register();
        dev.zmuzy.sbc.garden.farming.GardenWarps.register();
        dev.zmuzy.sbc.garden.farming.MouseLock.register();
        dev.zmuzy.sbc.garden.farming.FarmingKeys.register();
        dev.zmuzy.sbc.garden.farming.FarmingLanes.register();
        dev.zmuzy.sbc.garden.farming.SeeThrough.register();
        dev.zmuzy.sbc.garden.farming.GardenUptime.register();
        dev.zmuzy.sbc.garden.farming.LogbookStats.register();
        dev.zmuzy.sbc.garden.farming.HarvestFeast.register();
    }

    // called from SbcMod with the other keys
    public static void keys(KeyMapping.Category category) {
        dev.zmuzy.sbc.garden.pests.PestAlerts.keys(category);
        dev.zmuzy.sbc.garden.pests.PestTraps.keys(category);
        dev.zmuzy.sbc.garden.farming.GardenWarps.keys(category);
        dev.zmuzy.sbc.garden.farming.MouseLock.keys(category);
        dev.zmuzy.sbc.garden.farming.FarmingKeys.keys(category);
        dev.zmuzy.sbc.garden.farming.SeeThrough.keys(category);
    }
}
