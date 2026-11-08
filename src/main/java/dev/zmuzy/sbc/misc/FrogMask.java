// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.misc;

import dev.zmuzy.sbc.areas.FishHooks;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;

// from skyhanni misc/FrogMaskFeatures.kt: which park region your frog mask boosts today + a warning when
// youre somewhere else
public final class FrogMask {
    public static final String ID = "frog_mask";
    private static final Patterns.P REGION = Patterns.p("SH.misc.frogmask.description.active");
    private static String region = "";
    private static long lastWarning, lastLogClick, lastCheck;

    private FrogMask() {}

    // from skyhanni findFrogMask: helmet first, then anywhere in your inventory
    private static ItemStack find(Minecraft mc) {
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        if ("FROG_MASK".equals(Gain.sbId(head))) return head;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            if ("FROG_MASK".equals(Gain.sbId(st))) return st;
        }
        return ItemStack.EMPTY;
    }

    static String region(List<String> lore) {
        for (String l : lore) {
            Matcher m = REGION.matcher(l);
            if (m.matches()) return m.group("region");
        }
        return "";
    }

    private static boolean wearing() {
        var p = Minecraft.getInstance().player;
        return p != null && "FROG_MASK".equals(Gain.sbId(p.getItemBySlot(EquipmentSlot.HEAD)));
    }

    private static boolean inPark() {
        return "THE_PARK".equals(GameState.island);
    }

    // from skyhanni shouldShowDisplay
    private static boolean show() {
        return switch (Features.get(ID).option("frog_mask.display").choice()) {
            case "Always" -> true;
            case "In the Park" -> inPark();
            case "While worn" -> wearing();
            case "While worn in the Park" -> wearing() && inPark();
            default -> false;
        };
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return;
        lastCheck = now;
        Feature f = Features.get(ID);
        ItemStack mask = find(mc);
        region = mask.isEmpty() ? "" : region(LegacyText.lore(mask));
        if (region.isEmpty() || !inPark()) return;
        String type = f.option("frog_mask.warningType").choice();
        if (type.equals("Never") || GameState.strip(region).equals(GameState.area.trim())) return;
        if (now - lastWarning <= f.option("frog_mask.cooldown").integer() * 1000L) return;
        if (type.equals("While foraging") && now - lastLogClick >= 5000) return;
        lastWarning = now;
        FishHooks.title("§cWrong Region!");
        SmallMisc.say("§eYour Frog Mask currently boosts " + region + "§e!");
    }

    private static RichCard.Content card(String where, long left) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Boosts", where).icon("minecraft:lily_pad").raw(), RichCard.Line.row("For", "§b" + SmallMisc.time(left)).raw()));
    }

    // from skyhanni nextDay: next sb day starts at a whole number of days since the calendar start
    static long untilNextDay(long now) {
        return SbTime.DAY - Math.floorMod(now - SbTime.EPOCH, SbTime.DAY);
    }

    private static final HudElement CARD = new RichCard(ID, "Frog Mask", "Misc", Zone.TOP_LEFT, 54, ID) {
        @Override public boolean visible() {
            return GameState.inSkyBlock && !region.isEmpty() && show();
        }

        @Override protected Content live() {
            return card(region, untilNextDay(System.currentTimeMillis()));
        }

        @Override protected Content sample() {
            return card("§aDark Thicket", 734_000);
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("Boosts", GameState.strip(region), Theme.current().good()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Boosts", "Dark Thicket", Theme.current().good()));
        }
    };

    private static void blockClick(net.minecraft.world.phys.Vec3 pos, boolean right) {
        if (right || !inPark() || !Features.on(ID)) return;
        var level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(BlockPos.containing(pos)).is(BlockTags.LOGS)) lastLogClick = System.currentTimeMillis();
    }

    public static void register() {
        Features.add(new Feature(ID, "Foraging", "Frog Mask", "Which region of the Park your Frog Mask boosts today, and a warning when you forage or stand somewhere else.", "SkyHanni: Frog Mask Features")
                .when(() -> GameState.inSkyBlock).tick(FrogMask::tick)
                .option(Option.choice("frog_mask.display", "Show the card", "Needs a Frog Mask in your inventory.", "While worn in the Park", "Off", "Always", "In the Park", "While worn", "While worn in the Park"))
                .option(Option.choice("frog_mask.warningType", "Warn", "", "While foraging", "Never", "While in the Park", "While foraging"))
                .option(Option.integer("frog_mask.cooldown", "Between warnings (seconds)", "", 30, 5, 300)));
        HudLayout.add(CARD);
        Interactions.onBlockClick(FrogMask::blockClick);
    }
}
