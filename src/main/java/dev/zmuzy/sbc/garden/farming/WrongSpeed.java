// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.CropType;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.EquipmentSlot;

// from skyhanni GardenOptimalSpeed.warn: farming at a different speed than the crop wants = title + chat
// line. with rancher's boots on the line is clickable to set the speed
// their speed read is walking speed: 4.317 * speed / 100 blocks/s, capped at 500
public final class WrongSpeed {
    public static final String ID = "garden_wrong_speed";
    private static long lastWarn, lastToolSwitch;
    private static CropType lastTool;

    private WrongSpeed() {}

    private static int want(CropType crop) {
        String key = switch (crop) {
            case NETHER_WART -> "speed_wart";
            case SUGAR_CANE -> "speed_cane";
            case COCOA_BEANS -> "speed_cocoa";
            case WILD_ROSE -> "speed_rose";
            case MOONFLOWER, SUNFLOWER -> "speed_sunflower";
            default -> "speed_" + crop.simpleName;
        };
        return GardenPack.number("garden_speed", key, 0);
    }

    // PlayerUtils.getWalkSpeed
    private static int walkSpeed(Minecraft mc) {
        return (int) (mc.player.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 1000);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        CropType crop = GardenMilestones.cropInHand();
        long now = System.currentTimeMillis();
        if (crop != lastTool) {
            lastTool = crop;
            lastToolSwitch = now;
        }
        if (crop == null || now - lastToolSwitch < 1500 || !mc.player.onGround() || !GardenMilestones.isCurrentlyFarming() || now - lastWarn < 20_000) return;
        int want = want(crop), have = walkSpeed(mc);
        if (want <= 0 || want == have) return;
        boolean ranchers = "RANCHERS_BOOTS".equals(Gain.sbId(mc.player.getItemBySlot(EquipmentSlot.FEET)));
        if (!ranchers && GardenPack.on(ID, "only_ranchers")) return;
        lastWarn = now;
        GardenPack.title("§cWrong speed!", 2);
        Style st = ranchers ? Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/setmaxspeed " + want)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to change the speed"))) : Style.EMPTY;
        mc.player.sendSystemMessage(Component.literal("§b[SBC] §cWrong speed while farming " + crop.cropName + "! §eCurrent: §f" + have + "§e, optimal: §f" + want).withStyle(st));
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Wrong speed warning", "A title and a chat line while you farm at another speed than the crop wants. With Rancher's Boots you can click the line to set it.", "SkyHanni: Wrong Speed Warning")
                .when(GardenPack::garden).defaultOff().tick(WrongSpeed::tick)
                .option(Option.bool("garden_wrong_speed.only_ranchers", "Only with Rancher's Boots", "", false)));
    }
}
