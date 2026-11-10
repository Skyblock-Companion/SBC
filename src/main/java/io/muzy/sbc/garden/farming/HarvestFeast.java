// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.farming;

import io.muzy.sbc.core.CookieBuff;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni HarvestFeastManager + EliteFeastData: which 4 crops are in season at the harvest feast, read
// from ted's All Crops menu
// skyhanni also fetches and shares this through another mod's site, thats out, so open the menu once per feast
public final class HarvestFeast {
    public static final String ID = "garden_feast";
    private static final int[] CURRENT_SLOTS = {11, 12, 14, 15};
    private static final int MONTH_MIDDLE_DAY = 18;

    private HarvestFeast() {}

    // getTimeStamp: "in season in 20h" means from the middle or start of a skyblock month, thats when crops change
    static long snap(long at) {
        SbTime.SbDate d = SbTime.sbDate(at);
        return SbTime.sbToReal(d.year(), d.day() < MONTH_MIDDLE_DAY ? d.month() : d.month() + 1, 1);
    }

    // readAllCrops: store what the menu says
    static void read(MenuView v) {
        List<String> current = new ArrayList<>();
        for (int slot : CURRENT_SLOTS) {
            CropType c = CropType.getByNameOrNull(v.item(slot).clean);
            if (c != null) current.add(c.name());
        }
        if (current.size() != 4) return;
        long now = System.currentTimeMillis();
        Map<String, Long> next = new HashMap<>();
        for (int slot = 27; slot <= 44; slot++) {
            MenuView.Item it = v.item(slot);
            CropType crop = CropType.getByNameOrNull(it.clean);
            Matcher m = it.firstClean(Patterns.get("SH.event.harvestfeast.crop.willbe"));
            if (crop == null || m == null) continue;
            long left = CookieBuff.parse(m.group("time"));
            if (left >= 0) next.put(crop.name(), snap(now + left));
        }
        Store.GardenProfile s = GardenMilestones.profile();
        s.feastCrops = current;
        s.feastNext = next;
        SbTime.SbDate d = SbTime.sbDate(now);
        s.feastEnd = SbTime.sbToReal(d.year(), d.month() + 1, 1);
        Store.markDirty();
    }

    // getCurrentCrops: first group of 4 that started, or what the menu said was current
    static List<String> current(List<String> crops, Map<String, Long> next, long now) {
        Map<Long, List<String>> groups = new HashMap<>();
        for (var e : next.entrySet()) groups.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        long best = Long.MIN_VALUE;
        List<String> out = crops;
        for (var g : groups.entrySet()) if (g.getKey() <= now && g.getKey() > best && g.getValue().size() == 4) {
            best = g.getKey();
            out = g.getValue();
        }
        return out;
    }

    // getActiveDuration: next change or end of month
    static long left(Map<String, Long> next, long monthEnd, long now) {
        long min = Long.MAX_VALUE;
        for (long t : next.values()) if (t > now) min = Math.min(min, t);
        return (min == Long.MAX_VALUE ? monthEnd : min) - now;
    }

    private static boolean known() {
        Store.GardenProfile s = GardenMilestones.profile();
        return s.feastCrops != null && !s.feastCrops.isEmpty() && s.feastEnd > System.currentTimeMillis();
    }

    private static List<RichCard.Line> lines(List<String> crops, long left) {
        List<RichCard.Line> out = new ArrayList<>();
        for (String c : crops) {
            CropType t = CropType.valueOf(c);
            out.add(RichCard.Line.row(t.cropName, "").icon(LegacyItems.stack(t.icon())).key("Crops"));
        }
        out.add(RichCard.Line.row("Changes in", "§b" + SbTime.dur(left)));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Harvest Feast", "Garden", Zone.TOP_LEFT, 39, ID) {
        @Override
        public boolean visible() {
            return (GardenPack.garden() || GardenPack.skyblock() && GardenPack.on(ID, "outside")) && known();
        }

        @Override
        protected Content live() {
            Store.GardenProfile s = GardenMilestones.profile();
            long now = System.currentTimeMillis();
            return new Content("", lines(current(s.feastCrops, s.feastNext, now), left(s.feastNext, s.feastEnd, now)));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(List.of("WHEAT", "CARROT", "MELON", "CACTUS"), 14 * 60_000L));
        }

        @Override
        protected List<Row> classicRows() {
            Store.GardenProfile s = GardenMilestones.profile();
            List<Row> out = new ArrayList<>();
            for (String c : s.feastCrops) out.add(new Row(CropType.valueOf(c).cropName));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Wheat"), new Row("Carrot"), new Row("Melon Slice"), new Row("Cactus"));
        }
    };

    // onBackgroundDrawn: out of date, the All Crops button is where to go
    private static void mainMenu(MenuView v) {
        if (known()) return;
        for (int slot = 0; slot < v.top; slot++) if (v.item(slot).clean.toLowerCase(java.util.Locale.ROOT).contains("all crops")) v.back(slot, 0x64FF6464);
    }

    private static void allCrops(MenuView v) {
        read(v);
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Harvest Feast crops", "The crops in season at the Harvest Feast and when they change. Talk to Ted and open All Crops to set it, each feast.", "SkyHanni: Harvest Feast")
                .when(GardenPack::skyblock)
                .option(Option.bool("garden_feast.outside", "Outside the Garden too", "", false)));
        MenuMarks.add(ID, Patterns.get("SH.event.harvestfeast.main.menu"), HarvestFeast::mainMenu);
        MenuMarks.add(ID, Patterns.get("SH.event.harvestfeast.main.allcrops"), HarvestFeast::allCrops);
        HudLayout.add(CARD);
    }
}
