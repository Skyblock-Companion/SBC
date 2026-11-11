// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.contest;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenItems;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.GardenCard;
import io.muzy.sbc.garden.milestones.GardenCard.Block;
import io.muzy.sbc.garden.milestones.GardenCard.Chip;
import io.muzy.sbc.garden.milestones.GardenCard.Line;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni GardenNextJacobContest minus the website: calendar pages say which crops each contest has and
// the card + warning go by that
// open the calendar and flip its pages to read the year. skyblocker's JacobEventToast is the warning
public final class NextContest {
    public static final String FEATURE = "garden_next_contest";
    private static final int MAX_CONTESTS_PER_YEAR = 124;
    private static final long CONTEST_MS = 20 * 60_000L;

    // EliteFarmingContest: crops by CropType name
    public static final class Known {
        public long startTime;
        public List<String> crops = new ArrayList<>();
        public String boosted;

        long endTime() {
            return startTime + CONTEST_MS;
        }

        List<CropType> cropTypes() {
            List<CropType> out = new ArrayList<>();
            for (String c : crops) {
                try {
                    out.add(CropType.valueOf(c));
                } catch (IllegalArgumentException ignored) {
                    // a crop thats gone
                }
            }
            return out;
        }
    }

    private static long lastWarning;

    private NextContest() {}

    private static boolean option(String key, boolean otherwise) {
        Feature f = Features.get(FEATURE);
        Option o = f == null ? null : f.option(key);
        return o == null ? otherwise : o.bool();
    }

    private static List<Known> known() {
        return Store.I.jacobContests;
    }

    static Known next(List<Known> contests, long now) {
        Known best = null;
        for (Known c : contests) if (c.endTime() > now && (best == null || c.endTime() < best.endTime())) best = c;
        return best;
    }

    // isNextCrop: next contest's crop, underlined in the farming cards if asked
    public static boolean isNextCrop(CropType crop) {
        if (!option("garden_next_contest.other_guis", true) || !Features.on(FEATURE)) return false;
        Known next = next(known(), System.currentTimeMillis());
        return next != null && next.crops.contains(crop.name());
    }

    // isCloseToNewYear
    private static boolean closeToNewYear(long now) {
        long intoYear = Math.floorMod(now - SbTime.EPOCH, SbTime.YEAR);
        return intoYear < 30 * 60_000L || SbTime.YEAR - intoYear < 30 * 60_000L;
    }

    // crop of "○ Cactus" or the boosted one with hypixel's icon, null for other lines
    private static Matcher crop(String line) {
        Matcher m = Patterns.get("SH.garden.nextcontest.crop-no-color").matcher(line);
        return m.matches() ? m : null;
    }

    // readCalendar
    static void readCalendar(MenuView v) {
        if (!Features.on(FEATURE)) return;
        Matcher title = Patterns.get("SH.garden.nextcontest.month").matcher(v.title);
        if (!title.matches()) return;
        int month = -1;
        for (int i = 0; i < SbTime.MONTHS.length; i++) if (SbTime.MONTHS[i].equals(title.group("month"))) month = i;
        if (month < 0) return;
        int year = Integer.parseInt(title.group("year"));
        List<Known> contests = known();
        long now = System.currentTimeMillis();
        contests.removeIf(c -> c.endTime() < now - SbTime.YEAR);
        boolean changed = false;
        for (int slot = 0; slot < v.top; slot++) {
            Known read = readDay(v.items[slot], year, month);
            if (read == null) continue;
            boolean seen = false;
            for (Known c : contests) seen |= c.startTime == read.startTime;
            if (seen) continue;
            contests.add(read);
            changed = true;
        }
        if (changed) {
            contests.sort((a, b) -> Long.compare(a.startTime, b.startTime));
            Store.markDirty();
        }
    }

    static Known readDay(MenuView.Item item, int year, int month) {
        if (item.empty() || !item.cleanHas("Jacob's Farming Contest")) return null;
        Matcher day = Patterns.get("SH.garden.nextcontest.day").matcher(item.name);
        if (!day.matches()) return null;
        Known out = new Known();
        try {
            out.startTime = SbTime.sbToReal(year, month, Integer.parseInt(day.group("day")));
        } catch (NumberFormatException e) {
            return null;
        }
        for (String line : item.cleanLore) {
            Matcher m = crop(line);
            if (m == null) continue;
            CropType crop = CropType.getByNameOrNull(m.group("crop"));
            if (crop == null) continue;
            out.crops.add(crop.name());
            if (m.group("boosted") != null) out.boosted = crop.name();
        }
        return out.crops.isEmpty() ? null : out;
    }

    // tryUpdateBoostedCrop: tab says which crop the next contest boosts
    private static void readTabBoost(Known next) {
        if (next == null) return;
        List<String> tab = GameState.tab;
        for (int i = 0; i < tab.size(); i++) {
            String t = tab.get(i).trim();
            if (!t.startsWith("Jacob's Contest")) continue;
            if (Patterns.get("SH.garden.nextcontest.time-left-no-color").matcher(t).matches()) return;
            for (int j = i + 1; j < tab.size() && j < i + 6; j++) {
                Matcher m = crop(tab.get(j));
                if (m == null || m.group("boosted") == null) continue;
                CropType crop = CropType.getByNameOrNull(m.group("crop"));
                if (crop != null && !crop.name().equals(next.boosted)) {
                    next.boosted = crop.name();
                    Store.markDirty();
                }
            }
            return;
        }
    }

    // warnAbout
    private static void warn(Known next, long now) {
        if (now >= next.startTime || !option("garden_next_contest.warn", true)) return;
        Option time = Features.get(FEATURE).option("garden_next_contest.warn_time");
        long warnMs = (time == null ? 120 : (long) time.number()) * 1000;
        if (next.startTime - now >= warnMs || now - lastWarning < warnMs) return;
        boolean wanted = false;
        for (CropType crop : next.cropTypes()) wanted |= option(warnKey(crop), true);
        if (!wanted) return;
        lastWarning = now;
        StringBuilder crops = new StringBuilder();
        for (CropType crop : next.cropTypes()) {
            if (!crops.isEmpty()) crops.append("§7, ");
            crops.append(crop.name().equals(next.boosted) ? "§6" : "§a").append(crop.cropName);
        }
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[SBC] §eNext farming contest: " + crops));
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1f);
        }
        if (mc.gui != null && mc.gui.hud != null) mc.gui.hud.setTitle(Component.literal("§eFarming Contest!"));
    }

    private static String warnKey(CropType crop) {
        return "garden_next_contest.warn_" + crop.name().toLowerCase(Locale.ROOT);
    }

    public static void second() {
        if (!Features.on(FEATURE)) return;
        long now = System.currentTimeMillis();
        Known next = next(known(), now);
        if (next == null) return;
        readTabBoost(next);
        warn(next, now);
    }

    private static final class Card extends GardenCard {
        Card() {
            super(FEATURE, "Next contest", Zone.TOP_LEFT, 34, FEATURE);
        }

        private Known demoContest() {
            Known k = new Known();
            k.startTime = System.currentTimeMillis() + 34 * 60_000L;
            k.crops = List.of("WHEAT", "COCOA_BEANS", "CACTUS");
            k.boosted = "COCOA_BEANS";
            return k;
        }

        private Known shownNext() {
            return demo ? demoContest() : next(known(), System.currentTimeMillis());
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("minecraft:clock");
        }

        @Override
        public boolean visible() {
            return GameState.inSkyBlock && ("GARDEN".equals(GameState.island) || option("garden_next_contest.outside", false));
        }

        private List<Row> rowsOf(Known k) {
            List<Row> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            if (k == null) {
                out.add(new Row("Open the calendar"));
                return out;
            }
            out.add(new Row(k.startTime <= now ? "Ends in" : "Starts in", GardenItems.time(k.startTime <= now ? k.endTime() - now : k.startTime - now), Theme.current().accent()));
            for (CropType c : k.cropTypes()) out.add(new Row(c.cropName));
            return out;
        }

        @Override
        public List<Row> rows() {
            return rowsOf(shownNext());
        }

        @Override
        public List<Row> demo() {
            return rowsOf(demoContest());
        }

        @Override
        protected Chip chip() {
            Known k = shownNext();
            if (k == null) return null;
            return k.startTime <= System.currentTimeMillis() ? new Chip("ACTIVE", Modern.GOOD) : new Chip("NEXT", Modern.AQUA);
        }

        @Override
        protected List<Block> blocks() {
            List<Block> out = new ArrayList<>();
            Known k = shownNext();
            long now = System.currentTimeMillis();
            if (k == null) {
                out.add(new Line(closeToNewYear(now) ? "Close to the new year" : "Open the calendar"));
                return out;
            }
            for (CropType crop : k.cropTypes()) {
                boolean boosted = crop.name().equals(k.boosted);
                Line row = new Line(crop.cropName).labelColor(boosted ? Modern.GOLD : Modern.INK).pairs();
                row.icon(crop.icon());
                if (boosted) row.chip(compact() ? "BOOST" : "BOOSTED", Modern.GOLD);
                out.add(row);
            }
            boolean active = k.startTime <= now;
            out.add(new Line(active ? "Ends in" : "Starts in").icon("minecraft:clock").value(GardenItems.time(active ? k.endTime() - now : k.startTime - now), Modern.AQUA));
            int have = 0;
            int year = (int) ((now - SbTime.EPOCH) / SbTime.YEAR);
            for (Known c : known()) if ((int) ((c.startTime - SbTime.EPOCH) / SbTime.YEAR) == year) have++;
            if (demo) have = 62;
            if (have < MAX_CONTESTS_PER_YEAR) {
                double share = (double) have / MAX_CONTESTS_PER_YEAR;
                out.add(new Line("Year read").value("§f" + have + "§7/" + MAX_CONTESTS_PER_YEAR).meter(share, accent()));
            }
            return out;
        }
    }

    public static final HudElement CARD = new Card();

    static void register() {
        Feature f = new Feature(FEATURE, "Garden", "Next Jacob's contest", "The crops and start of the next contest, read from the calendar: open it and turn its pages once a year. A warning shortly before the contest starts, with its crops.", "SkyHanni: Next Jacob's Contest · Skyblocker: Jacob's event toast")
                .when(() -> GameState.inSkyBlock)
                .option(Option.bool("garden_next_contest.outside", "Outside the Garden", "The card everywhere in SkyBlock.", false))
                .option(Option.bool("garden_next_contest.other_guis", "Mark the crops in other cards", "The next contest's crops underlined in Best crop time and Money per hour.", true))
                .option(Option.bool("garden_next_contest.warn", "Warning", "A title, a chat line and a sound shortly before a contest starts.", true))
                .option(Option.integer("garden_next_contest.warn_time", "Warning time (s)", "How long before it starts.", 120, 10, 600));
        for (CropType crop : CropType.values())
            f.option(Option.bool(warnKey(crop), "Warn for: " + crop.cropName, "", true));
        Features.add(f);
        MenuMarks.add(FEATURE, Patterns.get("SH.garden.nextcontest.month"), NextContest::readCalendar);
        HudLayout.add(CARD);
        Safe.commands("NextContest.commands", (dispatcher, registry) -> dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("sbc")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("resetcontestdata").executes(c -> {
                    known().clear();
                    Store.markDirty();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fThe contests read from the calendar are forgotten. Open the calendar to read them again."));
                    return 1;
                }))));
        io.muzy.sbc.core.Commands.describe("resetcontestdata", "Forget the Jacob's contests read from the calendar");
    }
}
