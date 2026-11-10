// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.garden.milestones.CropMilestonesApi;
import io.muzy.sbc.garden.milestones.CropMoneyDisplay;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.GardenCropSpeed;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// from skyhanni GardenCropMilestoneDisplay.addMushroomCowData + CropMoneyDisplay.buildDisplayBody extra coins
// with a mooshroom cow you make mushrooms while farming something else, so how far your mushroom milestone is
// and what mushrooms + rare crops add to coins/h. pet comes from the tab
public final class MushroomPerk {
    public static final String ID = "garden_mushroom_perk";
    private static final Set<String> RARE_OR_UP = Set.of("RARE", "EPIC", "LEGENDARY", "MYTHIC");
    // repo ArmorDrops.json: {drop, armor piece name, chance by pieces}, each in percent
    private static final Object[][] RARE_CROPS = {
            {"CROPIE", "MELON", new double[]{0.0, 0.03, 0.04, 0.05}},
            {"SQUASH", "CROPIE", new double[]{0.0, 0.01, 0.02, 0.03}},
            {"FERMENTO", "SQUASH", new double[]{0.0, 0.005, 0.006, 0.007}},
            {"HELIANTHUS", "FERMENTO", new double[]{0.0, 0.002, 0.003, 0.004}}};
    private static long nextMoney;

    private MushroomPerk() {}

    // GardenApi.mushroomCowPet: rare or better
    static boolean hasPet() {
        return Vitals.petName.contains("Mooshroom Cow") && (Vitals.petTier.isEmpty() || RARE_OR_UP.contains(Vitals.petTier));
    }

    // RareCropTracker.getDropsPerHour: per block broken. armorPieces = pieces of the armor the drop needs
    static double dropsPerHour(String specialDrop, int armorPieces) {
        for (Object[] r : RARE_CROPS) {
            if (!r[0].equals(specialDrop)) continue;
            double[] rates = (double[]) r[2];
            double rate = armorPieces > 0 && rates.length >= armorPieces ? rates[armorPieces - 1] : 0;
            return rate * 60 * 60 / 100;
        }
        return 0;
    }

    private static int armorPieces(Minecraft mc, String specialDrop) {
        if (mc.player == null) return 0;
        String armor = null;
        for (Object[] r : RARE_CROPS) if (r[0].equals(specialDrop)) armor = (String) r[1];
        if (armor == null) return 0;
        int n = 0;
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            String id = io.muzy.sbc.core.Gain.sbId(mc.player.getItemBySlot(slot));
            if (id.contains(armor) || id.contains("FERMENTO") || id.contains("HELIANTHUS")) n++;
        }
        return n;
    }

    // CropMoneyDisplay.buildDisplayBody: coins on top of every Money per hour line
    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextMoney) return;
        nextMoney = now + 1000;
        CropType crop = GardenMilestones.getCurrentlyFarmedCrop();
        double extra = 0;
        if (crop != null) {
            double bps = GardenCropSpeed.getRecentBPS();
            if (GardenPack.on(GardenMilestones.MONEY, "mooshroom") && crop != CropType.MUSHROOM && hasPet()) {
                double red = Prices.sell("ENCHANTED_RED_MUSHROOM") / 160, brown = Prices.sell("ENCHANTED_BROWN_MUSHROOM") / 160;
                extra += bps * crop.multiplier() * (red + brown) / 2 * (Vitals.petLevel / 100.0) * 3600;
            }
            if (GardenPack.on(GardenMilestones.MONEY, "rare_crops")) extra += bps * dropsPerHour(crop.specialDropType, armorPieces(mc, crop.specialDropType)) * ItemRepo.npcPrice(crop.specialDropType);
        }
        CropMoneyDisplay.extraPerHour = extra;
    }

    private static List<RichCard.Line> lines(String tiers, long have, long need, double percent, String time) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Mushroom", "§8" + tiers));
        out.add(RichCard.Line.row("Progress", "§e" + String.format(java.util.Locale.ROOT, "%,d", have) + "§8/§e" + String.format(java.util.Locale.ROOT, "%,d", need) + "  §7" + RowText.percent(percent)).meter(percent, Modern.GOOD));
        if (!time.isEmpty()) out.add(RichCard.Line.row("In", "§b" + time));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Mooshroom Cow perk", "Garden", Zone.TOP_LEFT, 36, ID) {
        @Override
        public boolean visible() {
            CropType crop = GardenMilestones.getCurrentlyFarmedCrop();
            return GardenPack.garden() && crop != null && crop != CropType.MUSHROOM && hasPet() && !CropMilestonesApi.isMaxMilestone(CropType.MUSHROOM) && CropMilestonesApi.getCurrentMilestoneTier(CropType.MUSHROOM) != null;
        }

        @Override
        protected Content live() {
            CropType m = CropType.MUSHROOM;
            Integer tier = CropMilestonesApi.getCurrentMilestoneTier(m);
            Long have = CropMilestonesApi.milestoneProgressToNextTier(m), need = CropMilestonesApi.milestoneNextTierAmount(m);
            if (tier == null || have == null || need == null) return new Content("", List.of(RichCard.Line.row("Open /cropmilestones", "")));
            double speed = GardenCropSpeed.averageBlocksPerSecond;
            CropType farmed = GardenMilestones.getCurrentlyFarmedCrop();
            String time = speed == 0 ? "" : MilestoneGoals.format((long) ((need - have) / (speed * (farmed == null ? 1 : farmed.multiplier())) * 1000), GardenPack.choice(GardenMilestones.MILESTONES, "timeFormat", "Year"));
            return new Content("", lines(tier + "➜§3" + (tier + 1), have, need, (double) have / Math.max(1, need), time));
        }

        @Override
        protected Content sample() {
            return new Content("", lines("21➜§322", 226_400, 400_000, 0.566, "49m 2s"));
        }

        @Override
        protected List<Row> classicRows() {
            Integer tier = CropMilestonesApi.getCurrentMilestoneTier(CropType.MUSHROOM);
            Double p = CropMilestonesApi.percentToNextMilestone(CropType.MUSHROOM);
            return List.of(new Row("Mushroom " + tier, p == null ? "" : RowText.percent(p), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Mushroom 21", "56.6%", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Mooshroom Cow perk", "With a Mooshroom Cow while you farm something else: your Mushroom milestone, how far it is and how long it still takes. What the mushrooms and rare crops make is added to Money per hour.", "SkyHanni: Mushroom Pet Perk")
                .when(GardenPack::garden).tick(MushroomPerk::tick));
        HudLayout.add(CARD);
    }
}
