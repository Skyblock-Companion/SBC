// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.GardenCropSpeed;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what you want on screen while farming. from skyblocker FarmingHud (tool's cultivating count, crops/min,
// coins/h, blocks/s, farming level + xp/h, yaw and pitch) + skyhanni blocks broken
// plus the tool's own level from its lore, fortune and speed from the tab, and the sowdust you made
public final class FarmingCard {
    private static final long WINDOW = 5000;
    private static final Pattern TOOL_LEVEL = Pattern.compile("^\\s*Level (\\d+) -> (\\d+)\\s*\\(([\\d.,]+[kKmM]?)/([\\d.,]+[kKmM]?)\\)");
    private static final Pattern STAT = Pattern.compile("^\\s*([A-Za-z ]+): \\D*([\\d,.]+)\\s*$");
    private static final Pattern SOWDUST = Pattern.compile("^\\s*Sowdust: ([\\d,]+)");

    private static final Deque<long[]> COUNTER = new ArrayDeque<>();
    private static long sowdustStart = -1, sowdustNow = -1;
    private static String toolLevel = "", toolXp = "";
    private static double toolPct = -1;
    private static long readAt;

    private FarmingCard() {}

    private static boolean tool() {
        return GardenMilestones.getCurrentlyFarmedCrop() != null;
    }

    static double amount(String s) {
        String t = s.replace(",", "");
        char c = Character.toLowerCase(t.charAt(t.length() - 1));
        double mul = c == 'k' ? 1e3 : c == 'm' ? 1e6 : 1;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        return Double.parseDouble(t) * mul;
    }

    // "Level 16 -> 17   (98.4k/160k)" from a tool's lore: level, xp, of. or null
    static String[] toolLevel(String loreLine) {
        Matcher m = TOOL_LEVEL.matcher(loreLine);
        return m.find() ? new String[]{m.group(1), m.group(3), m.group(4)} : null;
    }

    // sidebar sowdust, or -1
    static long sowdust(List<String> board) {
        for (String l : board) {
            Matcher m = SOWDUST.matcher(l);
            if (m.find()) return (long) amount(m.group(1));
        }
        return -1;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        while (!COUNTER.isEmpty() && COUNTER.peekFirst()[1] + WINDOW < now) COUNTER.pollFirst();
        ItemStack st = mc.player.getMainHandItem();
        var data = st.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = data.copyTag();
            if (tag.contains("farmed_cultivating")) {
                long count = tag.getLongOr("farmed_cultivating", 0);
                if (COUNTER.isEmpty() || COUNTER.peekLast()[0] != count) COUNTER.addLast(new long[]{count, now});
            }
        }
        if (now - readAt < 1000) return;
        readAt = now;
        long dust = sowdust(GameState.scoreboard);
        if (dust >= 0) {
            sowdustNow = dust;
            if (sowdustStart < 0) sowdustStart = dust;
        }
        toolLevel = "";
        toolPct = -1;
        var lore = st.get(DataComponents.LORE);
        if (lore != null) for (Component line : lore.lines()) {
            String[] level = toolLevel(GameState.strip(line.getString()));
            if (level == null) continue;
            toolLevel = level[0];
            toolXp = level[1] + "/" + level[2];
            double of = amount(level[2]);
            if (of > 0) toolPct = amount(level[1]) / of;
        }
    }

    private static double cropsPerMinute() {
        if (COUNTER.size() < 2) return 0;
        long[] first = COUNTER.peekFirst(), last = COUNTER.peekLast();
        return last[1] == first[1] ? 0 : (last[0] - first[0]) * 60_000d / (last[1] - first[1]);
    }

    // a number from the tab's Stats widget, or ""
    private static final java.util.regex.Pattern GARDEN_LEVEL = java.util.regex.Pattern.compile("^\\s*Garden Level: (\\d+)(?: \\((\\d+(?:\\.\\d+)?)%\\))?.*$");

    // {level, fraction into it} from the tab's " Garden Level: 10 (71%)", or null
    public static double[] gardenLevel() {
        for (String l : GameState.tab) {
            Matcher m = GARDEN_LEVEL.matcher(GameState.strip(l));
            if (m.matches()) return new double[]{Integer.parseInt(m.group(1)), m.group(2) == null ? 1 : Double.parseDouble(m.group(2)) / 100};
        }
        return null;
    }

    static String stat(String name) {
        for (String l : GameState.tab) {
            Matcher m = STAT.matcher(GameState.strip(l));
            if (m.matches() && m.group(1).trim().equals(name)) return m.group(2);
        }
        return "";
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    private record Reading(String cultivating, double cpm, double bazaarHour, double npcHour, double bps, double bpsAverage, long blocks, int level, double levelPct, double xpHour,
                           String toolLevel, String toolXp, double toolPct, String fortune, String cropFortuneName, String cropFortune, String speed, String pestChance, long sowdust, float yaw, float pitch) {}

    // crop fortune in a compact card: "Wheat FF". a crop too long to share a row becomes "Crop FF", the card says which
    static String briefFortune(String name) {
        String crop = name.replace(" Fortune", "");
        return crop.length() > 7 && CropType.getByNameOrNull(crop) != null ? "Crop FF" : crop + " FF";
    }

    // what the sections under this card already say, so each value shows once
    private record Carried(boolean speed, boolean hoe, boolean angles, boolean level) {
    }

    // a rate that drops to 0 for a sec (turning at the lane end) keeps its last value a bit
    // so rows dont flicker and the card doesnt resize every pause
    private static final java.util.Map<String, double[]> HELD = new java.util.HashMap<>();

    static double hold(String key, double value, long now, long forMs) {
        double[] h = HELD.computeIfAbsent(key, k -> new double[]{0, 0});
        if (value > 0) {
            h[0] = value;
            h[1] = now;
            return value;
        }
        return now - h[1] < forMs ? h[0] : 0;
    }

    private static List<RichCard.Line> lines(Reading r, boolean compact, Carried c) {
        List<RichCard.Line> out = new ArrayList<>();
        if (!r.cultivating.isEmpty()) out.add(RichCard.Line.row("Cultivating", "§f" + r.cultivating));
        // cpm, bps and blocks broken belong to the crop milestones card
        if (r.bazaarHour > 0) out.add(RichCard.Line.row("Bazaar an hour", "§6" + Prices.coins(r.bazaarHour)));
        // the npc price only matters when it beats the bazaar
        if (r.npcHour > r.bazaarHour) out.add(RichCard.Line.row("NPC an hour", "§e" + Prices.coins(r.npcHour)));
        // with the other rates, between two meters it'd take its own row
        if (compact && r.xpHour > 0) out.add(RichCard.Line.row("Farming XP an hour", "§a" + Prices.coins(r.xpHour)));
        out.add(RichCard.Line.rule());
        if (r.level >= 0) out.add(RichCard.Line.row("Farming " + r.level, RowText.percent(r.levelPct)).meter(r.levelPct, Modern.GOLD).key("Farming"));
        if (!compact && r.xpHour > 0) out.add(RichCard.Line.row("Farming XP an hour", "§a" + Prices.coins(r.xpHour)));
        // the garden level card has the exact xp, only show it here when that card isnt up
        double[] garden = c.level() ? null : gardenLevel();
        if (garden != null) out.add(RichCard.Line.row("Garden " + (int) garden[0], RowText.percent(garden[1])).meter(garden[1], 0xFF9CE37D).key("Garden level"));
        if (!r.toolLevel.isEmpty() && !c.hoe()) out.add(RichCard.Line.row("Tool level " + r.toolLevel, "§f" + r.toolXp + "  §7" + RowText.percent(r.toolPct)).meter(r.toolPct, Modern.AQUA).key("Tool level").brief("Tool " + r.toolLevel));
        out.add(RichCard.Line.rule());
        // the speed section has the tab fortunes + your speed vs the best
        if (!r.fortune.isEmpty() && !c.speed()) out.add(RichCard.Line.row("Farming Fortune", "§6" + r.fortune));
        if (!r.cropFortune.isEmpty() && !c.speed()) out.add(RichCard.Line.row(r.cropFortuneName, "§6" + r.cropFortune).key("Crop Fortune").brief(briefFortune(r.cropFortuneName)));
        if (!r.speed.isEmpty() && !c.speed()) out.add(RichCard.Line.row("Speed", "§f" + r.speed));
        if (!r.pestChance.isEmpty()) out.add(RichCard.Line.row("Bonus Pest Chance", "§2" + r.pestChance));
        if (r.sowdust > 0) out.add(RichCard.Line.row("Sowdust earned", "§2+" + num(r.sowdust)));
        if (!c.angles()) out.add(RichCard.Line.row("Yaw and pitch", "§f" + String.format(Locale.ROOT, "%.1f", r.yaw) + "§7, §f" + String.format(Locale.ROOT, "%.1f", r.pitch)).key("Yaw and pitch").brief("Y/P"));
        return out;
    }

    private static final RichCard CARD = new RichCard("garden_farming", "Farming", "Garden", Zone.TOP_LEFT, 28, "garden_farming") {
        @Override
        public boolean visible() {
            return ("GARDEN".equals(GameState.island) || SbcMod.DEV) && (tool() || System.currentTimeMillis() - GardenCropSpeed.lastBrokenTime < 30_000);
        }

        @Override
        public String heading() {
            CropType c = demo ? CropType.WHEAT : GardenMilestones.getCurrentlyFarmedCrop();
            return c == null ? title : "Farming " + c.cropName;
        }

        @Override
        protected Content live() {
            Minecraft mc = Minecraft.getInstance();
            CropType crop = GardenMilestones.getCurrentlyFarmedCrop();
            String id = crop == null ? null : ItemRepo.idForName(crop.cropName);
            long now = System.currentTimeMillis();
            double cpm = hold("cpm", cropsPerMinute(), now, 30_000);
            double bazaar = id == null ? 0 : Math.max(0, Prices.sell(id)), npc = id == null ? 0 : Math.max(0, ItemRepo.npcPrice(id));
            double[] farming = GameState.tabSkills.get("Farming");
            double xpHour = hold("xp", "Farming".equals(Vitals.skill) ? Vitals.xpPerHour() : 0, now, 60_000);
            String cropStat = crop == null ? "" : crop.cropName.replace(" Slice", "") + " Fortune";
            Reading r = new Reading(COUNTER.isEmpty() ? "" : num(COUNTER.peekLast()[0]), cpm, cpm * 60 * bazaar, cpm * 60 * npc, hold("bps", GardenCropSpeed.getRecentBPS(), now, 30_000), hold("bpsavg", GardenCropSpeed.averageBlocksPerSecond, now, 30_000), GardenCropSpeed.sessionBlocks,
                    farming == null ? -1 : (int) farming[0], farming == null ? 0 : farming[1] / 100, xpHour, toolLevel, toolXp, toolPct,
                    stat("Farming Fortune"), cropStat, cropStat.isEmpty() ? "" : stat(cropStat), stat("Speed"), stat("Bonus Pest Chance"),
                    sowdustStart < 0 ? 0 : sowdustNow - sowdustStart, mc.player == null ? 0 : Mth.wrapDegrees(mc.player.getYRot()), mc.player == null ? 0 : mc.player.getXRot());
            Carried c = carried();
            return TwoForms.of(this::compact, form -> new Content(cpm * 60 * bazaar > 0 ? "§6" + Prices.coins(cpm * 60 * bazaar) + "§7/h" : "", lines(r, form, c)));
        }

        @Override
        protected Content sample() {
            Reading r = new Reading("230,499", 12_840, 4_620_000, 4_210_000, 19.8, 19.2, 18_402, 31, 0.975, 412_000, "16", "98.4k/160k", 0.6151,
                    "379", "Wheat Fortune", "61", "381", "162", 41_208, -90f, 2.8f);
            Carried c = carried();
            return TwoForms.of(this::compact, form -> new Content("§6" + Prices.coins(4_620_000) + "§7/h", lines(r, form, c)));
        }

        private Carried carried() {
            HudElement level = HudLayout.element(io.muzy.sbc.garden.farming.GardenLevel.ID);
            return new Carried(carries("garden_speed"), carries(io.muzy.sbc.garden.farming.HoeLevel.ID), carries(io.muzy.sbc.garden.farming.YawPitch.ID), level != null && (demo || level.shows()));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            CropType crop = GardenMilestones.getCurrentlyFarmedCrop();
            String id = crop == null ? null : ItemRepo.idForName(crop.cropName);
            double bazaar = id == null ? 0 : Math.max(0, Prices.sell(id));
            out.add(new Row("Bazaar an hour", Prices.coins(cropsPerMinute() * 60 * bazaar), 0));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Bazaar an hour", "4.6M", 0));
        }
    };

    public static void register() {
        Features.add(new Feature("garden_farming", "Garden", "Farming card", "While you hold a farming tool: Cultivating, coins an hour at the Bazaar, Farming level and XP an hour, the tool's level, fortune, speed, Sowdust, yaw and pitch. Crops a minute and blocks a second are on the crop milestones card.", "Skyblocker: Farming HUD · SkyHanni: Crop Break Tracker")
                .when(() -> "GARDEN".equals(GameState.island) || SbcMod.DEV).tick(FarmingCard::tick));
        HudLayout.add(CARD);
    }
}
