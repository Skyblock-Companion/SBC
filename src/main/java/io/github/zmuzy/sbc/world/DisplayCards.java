// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.world;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small cards that start off. from skyhanni gui/GuiConfig real time, misc/TpsCounter, gui/BeaconPower,
// gui/LegionBobbinOverlay
public final class DisplayCards {
    public static final String REAL_TIME = "real_time", TPS = "tps_display", BEACON = "beacon_power", LEGION = "legion_bobbin";

    private DisplayCards() {}

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    private static List<RichCard.Line> one(String label, String value, int color) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row(label, value, color).raw());
        return out;
    }

    // --- pc clock ---

    private static String clock(LocalTime t) {
        Feature f = Features.get(REAL_TIME);
        boolean twelve = f != null && f.option("twelve_hour").bool(), seconds = f == null || f.option("seconds").bool();
        String pattern = (twelve ? "h:mm" : "HH:mm") + (seconds ? ":ss" : "") + (twelve ? " a" : "");
        return t.format(DateTimeFormatter.ofPattern(pattern, Locale.ROOT));
    }

    private static final RichCard REAL_TIME_CARD = new RichCard(REAL_TIME, "Clock", "GUI", Zone.TOP_RIGHT, 32, REAL_TIME) {
        @Override
        public boolean visible() {
            return true;
        }

        @Override
        protected Content live() {
            return new Content("", one("Time", clock(LocalTime.now()), Modern.INK));
        }

        @Override
        protected Content sample() {
            return new Content("", one("Time", clock(LocalTime.of(14, 5, 12)), Modern.INK));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Time", clock(LocalTime.now()), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Time", clock(LocalTime.of(14, 5, 12)), 0));
        }
    };

    // --- tps ---

    // from skyhanni TpsCounter.getColor
    static int tpsColor(double tps) {
        if (tps > 19.8) return 0xFF00AA00;
        if (tps > 19) return 0xFF55FF55;
        if (tps > 17.5) return 0xFFFFAA00;
        if (tps > 12) return 0xFFFF5555;
        return 0xFFAA0000;
    }

    private static RichCard.Content tpsContent(double tps) {
        if (tps < 0) return new RichCard.Content("", one("TPS", "§7measuring", Modern.INK));
        return new RichCard.Content("", one("TPS", String.format(Locale.ROOT, "%.1f", tps), tpsColor(tps)));
    }

    private static final RichCard TPS_CARD = new RichCard(TPS, "Server ticks", "GUI", Zone.TOP_RIGHT, 33, TPS) {
        @Override
        public boolean visible() {
            return true;
        }

        @Override
        protected Content live() {
            return tpsContent(io.github.zmuzy.sbc.commands.ServerStats.currentTps());
        }

        @Override
        protected Content sample() {
            return tpsContent(19.6);
        }

        @Override
        public List<Content> states() {
            return List.of(tpsContent(-1));
        }

        @Override
        protected List<Row> classicRows() {
            double t = io.github.zmuzy.sbc.commands.ServerStats.currentTps();
            return List.of(new Row("TPS", t < 0 ? "measuring" : String.format(Locale.ROOT, "%.1f", t), t < 0 ? 0 : tpsColor(t)));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("TPS", "19.6", tpsColor(19.6)));
        }
    };

    // --- beacon ---

    // skyhanni BeaconPower patterns, on colorless lines
    static final Pattern BEACON_OFF = Pattern.compile("Beacon Deactivated - No Power Remaining"), BEACON_TIME = Pattern.compile("Power Remaining: (?<time>.+)"),
            BEACON_STAT = Pattern.compile("Current Stat: (?<stat>.+)");
    private static final Pattern TIME_PART = Pattern.compile("(\\d+)([dhms])");
    private static final int BEACON_POWER_SLOT = 22, STATS_SLOT = 23;

    // from skyhanni TimeUtils.getDuration for what hypixel writes here: "0d 5h 53m 12s"
    static long duration(String text) {
        Matcher m = TIME_PART.matcher(text);
        long ms = 0;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return ms;
    }

    private static Store.BeaconPower beacon() {
        return Store.I.beaconPower.computeIfAbsent(GameState.profileName, k -> new Store.BeaconPower());
    }

    private static void beaconMenu(MenuView v) {
        Store.BeaconPower b = beacon();
        MenuView.Item power = v.item(BEACON_POWER_SLOT);
        if (power.firstClean(BEACON_OFF) != null) {
            b.expiry = 0;
            Store.markDirty();
        } else {
            Matcher m = power.firstClean(BEACON_TIME);
            if (m != null) {
                b.expiry = System.currentTimeMillis() + duration(m.group("time"));
                Store.markDirty();
            }
        }
        Matcher s = v.item(STATS_SLOT).firstClean(BEACON_STAT);
        if (s != null && !s.group("stat").equals(b.stat)) {
            b.stat = s.group("stat");
            Store.markDirty();
        }
    }

    private static RichCard.Content beaconContent(long left, String stat) {
        List<RichCard.Line> out = new ArrayList<>();
        if (left <= 0) {
            out.add(RichCard.Line.row("Power", "Not active", Modern.BAD).raw());
            return new RichCard.Content("", out);
        }
        out.add(RichCard.Line.row("Power", SbTime.dur(left), Modern.AQUA).raw());
        Feature f = Features.get(BEACON);
        if ((f == null || f.option("stat").bool()) && !stat.isEmpty()) {
            boolean compress = f != null && f.option("compress").bool();
            String shown = compress && stat.contains(" ") ? stat.substring(0, stat.indexOf(' ')) : stat;
            out.add(RichCard.Line.row("Stat", shown, Modern.INK).raw());
        }
        return new RichCard.Content("", out);
    }

    private static final RichCard BEACON_CARD = new RichCard(BEACON, "Beacon", "GUI", Zone.TOP_RIGHT, 34, BEACON) {
        @Override
        public boolean visible() {
            return sb() && !"BINGO".equalsIgnoreCase(GameState.profileType);
        }

        @Override
        protected Content live() {
            Store.BeaconPower b = beacon();
            return beaconContent(b.expiry - System.currentTimeMillis(), b.stat);
        }

        @Override
        protected Content sample() {
            return beaconContent(21_192_000L, "+5✯ Magic Find");
        }

        @Override
        public List<Content> states() {
            return List.of(beaconContent(0, ""));
        }

        @Override
        protected List<Row> classicRows() {
            Store.BeaconPower b = beacon();
            long left = b.expiry - System.currentTimeMillis();
            return left <= 0 ? List.of(new Row("Power", "Not active", Modern.BAD)) : List.of(new Row("Power", SbTime.dur(left), Modern.AQUA), new Row("Stat", b.stat, 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Power", "5h 53m", Modern.AQUA), new Row("Stat", "+5✯ Magic Find", 0));
        }
    };

    // --- legion + bobbin time ---

    // skyhanni repo Items.json distance_enchant_data, same as its defaults
    private static final double LEGION_DISTANCE = 30, LEGION_MULT = 0.07, BOBBIN_DISTANCE = 30, BOBBIN_MULT = 0.2;
    private static final int LEGION_LIMIT = 20, BOBBIN_LIMIT = 5;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static int nearbyPlayers, nearbyBobbers;
    private static double legionBuff, bobbinBuff;

    private static void legionTick(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || me.tickCount % 20 != 0) return;
        AABB legion = me.getBoundingBox().inflate(LEGION_DISTANCE), bobbin = me.getBoundingBox().inflate(BOBBIN_DISTANCE);
        int players = 0;
        for (Player p : mc.level.players()) if (p != me && p.getUUID().version() == 4 && legion.contains(p.position()) && p.distanceTo(me) <= LEGION_DISTANCE) players++;
        nearbyPlayers = Math.min(players, LEGION_LIMIT);
        nearbyBobbers = Math.min(mc.level.getEntitiesOfClass(FishingHook.class, bobbin, h -> h.distanceTo(me) <= BOBBIN_DISTANCE).size(), BOBBIN_LIMIT);
        double l = 0, b = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack piece = me.getItemBySlot(slot);
            var data = piece.get(DataComponents.CUSTOM_DATA);
            if (data == null) continue;
            var enchants = data.copyTag().getCompoundOrEmpty("enchantments");
            l += enchants.getIntOr("ultimate_legion", 0) * LEGION_MULT;
            b += enchants.getIntOr("ultimate_bobbin_time", 0) * BOBBIN_MULT;
        }
        legionBuff = l;
        bobbinBuff = b;
    }

    private static boolean hideWithout() {
        Feature f = Features.get(LEGION);
        return f == null || f.option("hide_without").bool();
    }

    private static String buff(double perStack, int stacks) {
        return stacks + " §7(" + String.format(Locale.ROOT, "%.2f", perStack * stacks) + "%)";
    }

    private static RichCard.Content legionContent(int players, double legion, int bobbers, double bobbin, boolean hide) {
        List<RichCard.Line> out = new ArrayList<>();
        if (!hide || legion != 0) out.add(RichCard.Line.row("Legion", buff(legion, players), Modern.AQUA).raw());
        if (!hide || bobbin != 0) out.add(RichCard.Line.row("Bobbin' Time", buff(bobbin, bobbers), Modern.AQUA).raw().brief("Bobbin'"));
        return new RichCard.Content("", out);
    }

    private static final RichCard LEGION_CARD = new RichCard(LEGION, "Nearby buffs", "GUI", Zone.TOP_RIGHT, 36, LEGION) {
        @Override
        public boolean visible() {
            return sb() && (!hideWithout() || legionBuff != 0 || bobbinBuff != 0);
        }

        @Override
        protected Content live() {
            return legionContent(nearbyPlayers, legionBuff, nearbyBobbers, bobbinBuff, hideWithout());
        }

        @Override
        protected Content sample() {
            return legionContent(6, 2.8, 3, 1.0, false);
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Legion", buff(legionBuff, nearbyPlayers), Modern.AQUA), new Row("Bobbin' Time", buff(bobbinBuff, nearbyBobbers), Modern.AQUA));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Legion", buff(2.8, 6), Modern.AQUA), new Row("Bobbin' Time", buff(1.0, 3), Modern.AQUA));
        }
    };

    public static void register() {
        Features.add(new Feature(REAL_TIME, "HUD", "Clock", "Your computer's time, handy in full screen.", "SkyHanni: Real Time")
                .defaultOff()
                .option(Option.bool("real_time.twelve_hour", "12 hour clock", "", false))
                .option(Option.bool("real_time.seconds", "Seconds", "", true)));
        Features.add(new Feature(TPS, "HUD", "Server ticks per second", "How fast the server is running, up to 20.", "SkyHanni: TPS Display")
                .defaultOff().when(DisplayCards::sb));
        Features.add(new Feature(BEACON, "HUD", "Beacon power", "How long your beacon runs and what it boosts. Opening the Beacon menu reads it.", "SkyHanni: Beacon Power")
                .defaultOff().when(DisplayCards::sb)
                .option(Option.bool("beacon_power.stat", "The stat it boosts", "", true))
                .option(Option.bool("beacon_power.compress", "Only the stat's number", "", false)));
        Features.add(new Feature(LEGION, "HUD", "Legion and Bobbin' Time", "How many players and bobbers are near, and what your armor makes of them.", "SkyHanni: Legion/Bobbin Overlay")
                .defaultOff().when(DisplayCards::sb).tick(DisplayCards::legionTick)
                .option(Option.bool("legion_bobbin.hide_without", "Only with the enchantment on your armor", "", true)));
        HudLayout.add(REAL_TIME_CARD);
        HudLayout.add(TPS_CARD);
        HudLayout.add(BEACON_CARD);
        HudLayout.add(LEGION_CARD);
        MenuMarks.add(BEACON, "Beacon", DisplayCards::beaconMenu);
    }
}
