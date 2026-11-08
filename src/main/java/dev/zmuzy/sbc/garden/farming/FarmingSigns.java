// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.CropType;
import dev.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// from skyhanni GardenOptimalAngles + GardenOptimalSpeed's sign part: in a squeaky mousemat or rancher's
// boots sign, a button per crop writes its numbers into the sign
// closing the sign (which sends them) is still on you. an angle pair is one setting per crop "yaw/pitch"
// instead of two numbers
public final class FarmingSigns {
    public static final String ID = "garden_farming_signs";
    // CustomAnglesConfig defaults
    private static final Object[][] ANGLES = {
            {"wheat", "Wheat", "90/0", CropType.WHEAT}, {"carrot", "Carrot", "-90/2.8", CropType.CARROT}, {"potato", "Potato", "-90/2.8", CropType.POTATO},
            {"wart", "Nether Wart", "90/0", CropType.NETHER_WART}, {"pumpkin", "Pumpkin", "90/-58.5", CropType.PUMPKIN}, {"melon", "Melon", "90/-58.5", CropType.MELON},
            {"cocoa", "Cocoa Beans", "180/-45", CropType.COCOA_BEANS}, {"cane", "Sugar Cane", "-135/0", CropType.SUGAR_CANE}, {"cactus", "Cactus", "-90/0", CropType.CACTUS},
            {"mushroom", "Mushroom", "116.5/0", CropType.MUSHROOM}, {"sunflower", "Sunflower and Moonflower", "45/0", CropType.SUNFLOWER}, {"rose", "Wild Rose", "45/0", CropType.WILD_ROSE}};

    private FarmingSigns() {}

    // SignUtils.isMousematSign
    static boolean mousemat(String[] lines) {
        return lines.length == 4 && lines[1].equals("Set Yaw Above!") && lines[2].equals("Set Pitch Below!");
    }

    // SignUtils.isRancherSign
    static boolean rancher(String[] lines) {
        return lines.length == 4 && lines[1].equals("^^^^^^") && lines[2].startsWith("Set your") && lines[3].endsWith("speed cap!");
    }

    // {yaw, pitch} from "yaw/pitch", null if it doesnt read like that
    static String[] angles(String text) {
        String[] p = text.split("/");
        if (p.length != 2) return null;
        try {
            Float.parseFloat(p[0].trim());
            Float.parseFloat(p[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        return new String[]{p[0].trim(), p[1].trim()};
    }

    private static boolean last(CropType crop) {
        return crop == GardenCropSpeed.lastBrokenCrop || crop == CropType.SUNFLOWER && GardenCropSpeed.lastBrokenCrop == CropType.MOONFLOWER;
    }

    private static Button button(int index, String text, boolean gold, Runnable action) {
        int perColumn = 7, w = 118, h = 16;
        int x = 4 + (index / perColumn) * (w + 4), y = 4 + (index % perColumn) * (h + 2);
        return Button.builder(Component.literal((gold ? "§6" : "§f") + text), b -> action.run()).bounds(x, y, w, h).build();
    }

    // this sign's buttons, none for any other sign
    public static List<Button> buttons(String[] messages) {
        List<Button> out = new ArrayList<>();
        if (!GardenPack.garden() || !Features.on(ID)) return out;
        if (mousemat(messages) && GardenPack.on(ID, "mousemat")) {
            for (Object[] a : ANGLES) {
                String[] yp = angles(GardenPack.choice(ID, "angle_" + a[0], (String) a[2]));
                if (yp == null) continue;
                out.add(button(out.size(), a[1] + " " + yp[0] + "/" + yp[1], last((CropType) a[3]), () -> {
                    messages[0] = yp[0];
                    messages[3] = yp[1];
                }));
            }
        } else if (rancher(messages) && GardenPack.on(ID, "rancher")) {
            for (Object[] a : ANGLES) {
                CropType crop = (CropType) a[3];
                int speed = speed((String) a[0]);
                if (speed <= 0) continue;
                out.add(button(out.size(), a[1] + " " + speed, last(crop), () -> messages[0] = String.valueOf(speed)));
            }
        }
        return out;
    }

    private static int speed(String key) {
        return GardenPack.number("garden_speed", "speed_" + key, 0);
    }

    public static void register() {
        Feature f = new Feature(ID, "Garden", "Farming signs", "A button per crop in the signs of Squeaky Mousemat and Rancher's Boots, that writes its angles or speed. You close the sign yourself.", "SkyHanni: Squeaky Mousemat and Rancher Boots")
                .when(GardenPack::garden)
                .option(Option.bool("garden_farming_signs.mousemat", "In the Squeaky Mousemat", "", true))
                .option(Option.bool("garden_farming_signs.rancher", "In Rancher's Boots", "The speeds are the ones of Optimal farming speed.", true));
        String how = "Yaw and pitch, as yaw/pitch.";
        f.option(Option.text("garden_farming_signs.angle_wheat", "Wheat angles", how, "90/0"));
        f.option(Option.text("garden_farming_signs.angle_carrot", "Carrot angles", how, "-90/2.8"));
        f.option(Option.text("garden_farming_signs.angle_potato", "Potato angles", how, "-90/2.8"));
        f.option(Option.text("garden_farming_signs.angle_wart", "Nether Wart angles", how, "90/0"));
        f.option(Option.text("garden_farming_signs.angle_pumpkin", "Pumpkin angles", how, "90/-58.5"));
        f.option(Option.text("garden_farming_signs.angle_melon", "Melon angles", how, "90/-58.5"));
        f.option(Option.text("garden_farming_signs.angle_cocoa", "Cocoa Beans angles", how, "180/-45"));
        f.option(Option.text("garden_farming_signs.angle_cane", "Sugar Cane angles", how, "-135/0"));
        f.option(Option.text("garden_farming_signs.angle_cactus", "Cactus angles", how, "-90/0"));
        f.option(Option.text("garden_farming_signs.angle_mushroom", "Mushroom angles", how, "116.5/0"));
        f.option(Option.text("garden_farming_signs.angle_sunflower", "Sunflower and Moonflower angles", how, "45/0"));
        f.option(Option.text("garden_farming_signs.angle_rose", "Wild Rose angles", how, "45/0"));
        Features.add(f);
    }
}
