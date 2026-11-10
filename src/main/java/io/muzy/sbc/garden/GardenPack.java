// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
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
        io.muzy.sbc.garden.pests.PestAlerts.register();
        io.muzy.sbc.garden.pests.PestTimer.register();
        io.muzy.sbc.garden.pests.PestGuess.register();
        io.muzy.sbc.garden.pests.SprayTracker.register();
        io.muzy.sbc.garden.pests.StereoHarmony.register();
        io.muzy.sbc.garden.pests.PestTraps.register();
        io.muzy.sbc.garden.pests.MantidKills.register();
        io.muzy.sbc.garden.pests.PesthunterProfit.register();
        io.muzy.sbc.garden.pests.PestHighlight.register();
        io.muzy.sbc.garden.greenhouse.DnaSolver.register();
        io.muzy.sbc.garden.greenhouse.CropDiagnostics.register();
        io.muzy.sbc.garden.greenhouse.MutationAnalysis.register();
        io.muzy.sbc.garden.greenhouse.PhantomleafSolver.register();
        io.muzy.sbc.garden.greenhouse.GreenhousePaste.register();
        io.muzy.sbc.garden.farming.MilestoneGoals.register();
        io.muzy.sbc.garden.farming.MilestoneMenu.register();
        io.muzy.sbc.garden.farming.MushroomPerk.register();
        io.muzy.sbc.garden.farming.CropCommands.register();
        io.muzy.sbc.garden.farming.CropStartLocation.register();
        io.muzy.sbc.garden.farming.TrueFortune.register();
        io.muzy.sbc.garden.farming.GardenLevel.register();
        io.muzy.sbc.garden.farming.HoeLevel.register();
        io.muzy.sbc.garden.farming.PreventBreaking.register();
        io.muzy.sbc.garden.farming.ToolkitIcons.register();
        io.muzy.sbc.garden.farming.BurrowingSpores.register();
        io.muzy.sbc.garden.farming.RareCrops.register();
        io.muzy.sbc.garden.farming.AtmosphericFilter.register();
        io.muzy.sbc.garden.farming.YawPitch.register();
        io.muzy.sbc.garden.farming.FarmingSigns.register();
        io.muzy.sbc.garden.farming.WrongSpeed.register();
        io.muzy.sbc.garden.farming.GardenWarps.register();
        io.muzy.sbc.garden.farming.MouseLock.register();
        io.muzy.sbc.garden.farming.FarmingKeys.register();
        io.muzy.sbc.garden.farming.FarmingLanes.register();
        io.muzy.sbc.garden.farming.SeeThrough.register();
        io.muzy.sbc.garden.farming.GardenUptime.register();
        io.muzy.sbc.garden.farming.LogbookStats.register();
        io.muzy.sbc.garden.farming.HarvestFeast.register();
    }

    // called from SbcMod with the other keys
    public static void keys(KeyMapping.Category category) {
        io.muzy.sbc.garden.pests.PestAlerts.keys(category);
        io.muzy.sbc.garden.pests.PestTraps.keys(category);
        io.muzy.sbc.garden.farming.GardenWarps.keys(category);
        io.muzy.sbc.garden.farming.MouseLock.keys(category);
        io.muzy.sbc.garden.farming.FarmingKeys.keys(category);
        io.muzy.sbc.garden.farming.SeeThrough.keys(category);
    }
}
