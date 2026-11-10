// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// from skyhanni GardenYawAndPitch: yaw + pitch to as many decimals as you want, for setting up a farm
// the farming card always has them at 1 decimal, this is for when you need more, fades when you stop moving the mouse
public final class YawPitch {
    public static final String ID = "garden_yaw_pitch";
    private static float lastYaw, lastPitch;
    private static long lastChange;

    private YawPitch() {}

    static String rounded(float v, int precision) {
        return new BigDecimal(v).setScale(precision, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        float yaw = Mth.wrapDegrees(mc.player.getYRot()), pitch = mc.player.getXRot();
        if (yaw != lastYaw || pitch != lastPitch) lastChange = System.currentTimeMillis();
        lastYaw = yaw;
        lastPitch = pitch;
    }

    private static List<RichCard.Line> lines(float yaw, float pitch) {
        return List.of(RichCard.Line.row("Yaw", "§f" + rounded(yaw, GardenPack.number(ID, "yaw_precision", 4))), RichCard.Line.row("Pitch", "§f" + rounded(pitch, GardenPack.number(ID, "pitch_precision", 4))));
    }

    private static final RichCard CARD = new RichCard(ID, "Yaw and pitch", "Garden", Zone.TOP_LEFT, 39, ID) {
        @Override
        public boolean visible() {
            if (!GardenPack.garden() && !(GardenPack.skyblock() && GardenPack.on(ID, "outside"))) return false;
            if (!GardenMilestones.hasToolInHand() && !GardenPack.on(ID, "no_tool")) return false;
            return GardenPack.on(ID, "always") || System.currentTimeMillis() - lastChange <= GardenPack.number(ID, "timeout", 5) * 1000L;
        }

        @Override
        protected Content live() {
            return new Content("", lines(lastYaw, lastPitch));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(-90f, 2.8f));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Yaw", rounded(lastYaw, 2), 0), new Row("Pitch", rounded(lastPitch, 2), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Yaw", "-90", 0), new Row("Pitch", "2.8", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Yaw and pitch", "Your yaw and pitch to more decimals than the Farming card, while you hold a farming tool. It fades when the mouse rests.", "SkyHanni: Yaw and Pitch")
                .when(GardenPack::skyblock).defaultOff().tick(YawPitch::tick)
                .option(Option.integer("garden_yaw_pitch.yaw_precision", "Yaw decimals", "", 4, 0, 10))
                .option(Option.integer("garden_yaw_pitch.pitch_precision", "Pitch decimals", "", 4, 0, 10))
                .option(Option.integer("garden_yaw_pitch.timeout", "Fade after (s)", "Without moving the mouse.", 5, 1, 60))
                .option(Option.bool("garden_yaw_pitch.always", "Never fade", "", false))
                .option(Option.bool("garden_yaw_pitch.outside", "Outside the Garden too", "", false))
                .option(Option.bool("garden_yaw_pitch.no_tool", "Without a tool too", "", false)));
        HudLayout.add(CARD);
    }
}
