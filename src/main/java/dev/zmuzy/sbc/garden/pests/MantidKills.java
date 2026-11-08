// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.pests;

import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

// from skyhanni MantidKillDisplay: mantid reforge bonus, 1 per pest killed in the last 10 min up to 20, each
// counted on its own
public final class MantidKills {
    public static final String ID = "garden_mantid";
    private static final int MAX_BONUS = 20;
    private static final long EXPIRE = 10 * 60_000L;
    private static final ArrayDeque<Long> QUEUE = new ArrayDeque<>();

    private MantidKills() {}

    // checkMantid: any armor piece with the reforge
    private static boolean wearing() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = mc.player.getItemBySlot(slot);
            var data = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null && "mantid".equals(data.copyTag().getStringOr("modifier", ""))) return true;
        }
        return false;
    }

    private static void onKill(String pest) {
        if (!Features.on(ID) || !wearing()) return;
        QUEUE.addLast(System.currentTimeMillis() + EXPIRE);
        while (QUEUE.size() > MAX_BONUS) QUEUE.pollFirst();
    }

    // updateNextExpireGroup: how many expire with the first, expiries are in order
    static int group(Iterable<Long> expiries, long first, long window) {
        int count = 0;
        for (long e : expiries) if (e - first < window) count++;
        return count;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        while (!QUEUE.isEmpty() && QUEUE.peekFirst() <= now) QUEUE.pollFirst();
    }

    static int kills() {
        return QUEUE.size();
    }

    static void killForTest() {
        QUEUE.addLast(System.currentTimeMillis() + EXPIRE);
    }

    private static boolean shown() {
        return switch (GardenPack.choice(ID, "when", "Mantid reforge")) {
            case "Always" -> true;
            case "Farming tool" -> GardenMilestones.hasToolInHand();
            case "Vacuum" -> GardenPack.vacuumInHand();
            default -> wearing();
        };
    }

    private static List<RichCard.Line> lines(int bonus, int group, String next) {
        List<RichCard.Line> out = new ArrayList<>();
        double p = (double) bonus / MAX_BONUS;
        out.add(RichCard.Line.row("Bonus", (bonus < MAX_BONUS ? "§c" : "§a") + bonus + "§7/§a" + MAX_BONUS + "  §7" + RowText.percent(p)).meter(p, Modern.GOOD));
        if (bonus > 0) out.add(RichCard.Line.row(group == 1 ? "1 pest expires" : group + " pests expire", "§b" + next).key("Expires"));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Mantid bonus", "Garden", Zone.TOP_LEFT, 31, ID) {
        @Override
        public boolean visible() {
            return GardenPack.garden() && shown();
        }

        @Override
        protected Content live() {
            long now = System.currentTimeMillis();
            Long first = QUEUE.peekFirst();
            int bonus = Math.min(QUEUE.size(), MAX_BONUS);
            return new Content("", lines(bonus, first == null ? 0 : group(QUEUE, first, GardenPack.number(ID, "group", 30) * 1000L), first == null ? "" : SbTime.dur(first - now)));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(14, 3, "4m 12s"));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Bonus", QUEUE.size() + "/" + MAX_BONUS, 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Bonus", "14/20", 0), new Row("3 pests expire", "4m 12s", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Mantid bonus", "With the Mantid reforge: how far your kill bonus is out of 20 and when the next pests fall out of it. Counted while you wear it, and it starts over when you change world.", "SkyHanni: Mantid Kill Display")
                .when(GardenPack::garden).tick(MantidKills::tick).left(QUEUE::clear) // onWorldChange: the bonus starts over
                .option(Option.choice("garden_mantid.when", "Show the card", "", "Mantid reforge", "Mantid reforge", "Farming tool", "Vacuum", "Always"))
                .option(Option.integer("garden_mantid.group", "Group expiries within (s)", "Pests that expire this close together count as one line.", 30, 1, 120)));
        PestAlerts.onKill(MantidKills::onKill);
        HudLayout.add(CARD);
    }
}
