// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.market.NpcTrade;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.item.sacks.SackApi;
import dev.zmuzy.sbc.mc.ParticleHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// mining helpers few players need, all start off. from skyhanni PrecisionMiningHighlight,
// FallenStarCultTimer, KingTalismanHelper
// GemstoneMoneyPerHour, DarkMonolithFeatures (its tracker is the dark monolith card we already have)
public final class MiningExtrasOff {
    static final String PRECISION = "mining_precision", CULT = "mining_fallen_star", KING = "mining_king_talisman", GEM = "mining_gemstone_money", MONOLITH = "mining_dark_monolith";

    private MiningExtrasOff() {}

    // precision mining

    private static AABB particle;
    private static boolean lookingAtParticle;
    private static long particleUntil;

    private static void onParticle(ParticleHooks.Particle p) {
        if (!Features.on(PRECISION) || !(p.is("minecraft:crit") || p.is("minecraft:happy_villager"))) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyAttack.isDown() || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        AABB box = new AABB(p.x() - 0.12, p.y() - 0.12, p.z() - 0.12, p.x() + 0.12, p.y() + 0.12, p.z() + 0.12);
        if (!new AABB(hit.getBlockPos()).intersects(box)) return;
        lookingAtParticle = p.is("minecraft:happy_villager");
        particle = box;
        particleUntil = System.currentTimeMillis() + 250;
    }

    private static void drawPrecision(WorldDraw w) {
        if (particle == null) return;
        if (System.currentTimeMillis() > particleUntil) {
            particle = null;
            return;
        }
        w.filledBox(particle, lookingAtParticle ? 0x00FF00 : 0x00FFFF, 0.8f, false);
    }

    // fallen star cult

    private static final Vec3 CULT_ROOM = new Vec3(-45.5, 193.5, 45.6);
    private static final int[] MEETING_DAYS = {7, 14, 21, 28};
    private static final int MEETING_END_HOUR = 6;

    // skyhanni currentMeetingEnd: when the running meeting ends, -1 if none
    static long meetingEnd(long now) {
        SbTime.SbDate d = SbTime.sbDate(now);
        boolean day = false;
        for (int m : MEETING_DAYS) day |= m == d.day();
        if (!day || d.hour() >= MEETING_END_HOUR) return -1;
        return SbTime.sbToReal(d.year(), d.month(), d.day()) + SbTime.DAY * MEETING_END_HOUR / 24;
    }

    // from skyhanni nextMeetingStart
    static long nextMeeting(long now) {
        SbTime.SbDate d = SbTime.sbDate(now);
        for (int m : MEETING_DAYS) if (m > d.day()) return SbTime.sbToReal(d.year(), d.month(), m);
        return d.month() == 11 ? SbTime.sbToReal(d.year() + 1, 0, MEETING_DAYS[0]) : SbTime.sbToReal(d.year(), d.month() + 1, MEETING_DAYS[0]);
    }

    private static boolean cultShows() {
        Minecraft mc = Minecraft.getInstance();
        boolean inMines = "DWARVEN_MINES".equals(GameState.island), inRoom = inMines && mc.player != null && mc.player.position().distanceTo(CULT_ROOM) < 10;
        boolean helmet = mc.player != null && "FALLEN_STAR_HAT".equals(Gain.sbId(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
        return (MiningPack.on(CULT, "mining_fallen_star.in_mines") && inMines) || (MiningPack.on(CULT, "mining_fallen_star.in_room") && inRoom) || (MiningPack.on(CULT, "mining_fallen_star.helmet") && helmet)
                || !(MiningPack.on(CULT, "mining_fallen_star.in_mines") || MiningPack.on(CULT, "mining_fallen_star.in_room") || MiningPack.on(CULT, "mining_fallen_star.helmet"));
    }

    private static RichCard.Content cult(long now) {
        long end = meetingEnd(now);
        if (end >= 0) return new RichCard.Content("", List.of(RichCard.Line.row("§aMeeting active", "§b" + MiningEnv.format(end - now) + " left")));
        return new RichCard.Content("", List.of(RichCard.Line.row("§eCult Meeting", "§bin " + MiningEnv.format(nextMeeting(now) - now))));
    }

    private static final RichCard CULT_CARD = new RichCard(CULT, "Fallen Star Cult", "Mining", Zone.TOP_LEFT, 45, CULT) {
        @Override
        public boolean visible() {
            return MiningPack.skyblock() && cultShows();
        }

        @Override
        protected Content live() {
            return cult(System.currentTimeMillis());
        }

        @Override
        protected Content sample() {
            return new Content("", List.of(RichCard.Line.row("§eCult Meeting", "§bin 1h 12m")));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Cult Meeting", MiningEnv.format(nextMeeting(System.currentTimeMillis()) - System.currentTimeMillis()), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Cult Meeting", "1h 12m", 0));
        }
    };

    // king talisman

    private static final Vec3 KING_SPOT = new Vec3(129.6, 196.5, 194.1);
    private static final String[] KINGS = {"Brammor", "Emkam", "Redros", "Erren", "Thormyr", "Emmor", "Grandan"};

    // skyhanni getKingTimes: time till each king stands there, the current one is the farthest away
    static long[] kingTimes(long now) {
        SbTime.SbDate d = SbTime.sbDate(now);
        int kingIndex = (d.day() + 5) % KINGS.length;
        long start = SbTime.sbToReal(d.year(), d.month(), d.day()) - kingIndex * SbTime.DAY;
        long circle = SbTime.DAY * KINGS.length;
        long[] out = new long[KINGS.length];
        for (int i = 0; i < KINGS.length; i++) {
            long next = start + SbTime.DAY * i - now;
            while (next < 0) next += circle;
            out[i] = next;
        }
        return out;
    }

    static int currentKing(long[] times) {
        int best = 0;
        for (int i = 1; i < times.length; i++) if (times[i] > times[best]) best = i;
        return best;
    }

    private static boolean nearKing() {
        return "DWARVEN_MINES".equals(GameState.island) && "Royal Palace".equals(GameState.area) && MiningEnv.world.playerPos().distanceTo(KING_SPOT) < 10;
    }

    private static void readKing(MenuView v) {
        if (!nearKing()) return;
        List<String> talked = MiningEnv.profile().kingsTalkedTo;
        String king = KINGS[currentKing(kingTimes(System.currentTimeMillis()))];
        if (!talked.contains(king)) talked.add(king);
    }

    private static ChatBus.Result onKingChat(ChatBus.Line line) {
        if ("DWARVEN_MINES".equals(GameState.island) && Patterns.get("SH.mining.kingtalisman.talisman").matcher(LegacyText.of(line.component())).matches()) {
            MiningEnv.profile().kingsTalkedTo.clear();
            MiningEnv.profile().kingsTalkedTo.addAll(List.of(KINGS));
        }
        return ChatBus.PASS;
    }

    private static List<RichCard.Line> kingLines(long now, List<String> talked, boolean near) {
        List<RichCard.Line> out = new ArrayList<>();
        if (talked.size() >= KINGS.length) {
            out.add(RichCard.Line.row("§eAll Kings found.", ""));
            return out;
        }
        long[] times = kingTimes(now);
        int current = currentKing(times);
        int next = -1;
        for (int i = 0; i < KINGS.length; i++) {
            boolean missing = !talked.contains(KINGS[i]);
            long left = i == current ? times[i] - SbTime.DAY * (KINGS.length - 1) : times[i];
            String when = i == current ? MiningEnv.format(left) + " left" : "in " + MiningEnv.format(left);
            if (near) out.add(RichCard.Line.row((i == current ? "§6King " : "§7") + KINGS[i], missing ? "§c" + when : "§aDone"));
            if (missing && (next < 0 || i == current || (next != current && times[i] < times[next]))) next = i;
        }
        if (!near && next >= 0) out.add(RichCard.Line.row("§cNext king: §7" + KINGS[next], next == current ? "§eNow" : "§bin " + MiningEnv.format(times[next])));
        return out;
    }

    private static final RichCard KING_CARD = new RichCard(KING, "King Talisman", "Mining", Zone.TOP_LEFT, 46, KING) {
        @Override
        public boolean visible() {
            return MiningPack.skyblock() && ("DWARVEN_MINES".equals(GameState.island) || MiningPack.on(KING, "mining_king_talisman.outside"));
        }

        @Override
        protected Content live() {
            return new Content("", kingLines(System.currentTimeMillis(), MiningEnv.profile().kingsTalkedTo, nearKing()));
        }

        @Override
        protected Content sample() {
            return new Content("", List.of(RichCard.Line.row("§cNext king: §7Emkam", "§bin 16m")));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Kings", MiningEnv.profile().kingsTalkedTo.size() + "/" + KINGS.length, 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Kings", "3/7", 0));
        }
    };

    // gemstone money

    private static long gemStart = MiningEnv.FAR_PAST, gemLastMined = MiningEnv.FAR_PAST, gemNextSecond;
    private static double gemCoins;
    private static String lastGemstone = "";
    private static boolean useNextSack, gemPaused;
    private static long gemUptime;
    private static final String[] GEM_TYPES = {"Rough", "Flawed", "Fine", "Flawless"};

    private static String gemId(String gem) {
        return MiningPack.choice(GEM, "mining_gemstone_money.type", "Flawless").toUpperCase(Locale.ROOT) + "_" + gem.toUpperCase(Locale.ROOT) + "_GEM";
    }

    private static double gemPrice(String id) {
        double npc = Math.max(0, NpcTrade.priceOf(id));
        return MiningPack.on(GEM, "mining_gemstone_money.force_npc") ? npc : Math.max(npc, Prices.sell(id));
    }

    // skyhanni getFraction: how many of the chosen tier one gem of this tier makes
    private static double fraction(int tier) {
        int chosen = 1;
        String c = MiningPack.choice(GEM, "mining_gemstone_money.type", "Flawless");
        for (int i = 0; i < GEM_TYPES.length; i++) if (GEM_TYPES[i].equals(c)) chosen = i + 1;
        return Math.pow(80, tier - chosen);
    }

    private static ChatBus.Result onGemChat(ChatBus.Line line) {
        Matcher m = Patterns.get("SH.mining.pristine").matcher(LegacyText.of(line.component()));
        if (!m.matches()) return ChatBus.PASS;
        long now = MiningEnv.now();
        if (MiningEnv.isFarPast(gemStart)) gemStart = now;
        gemPaused = false;
        useNextSack = true;
        gemLastMined = now;
        lastGemstone = m.group("gemstone");
        gemCoins += Integer.parseInt(m.group("amount")) * fraction(2) * gemPrice(gemId(lastGemstone));
        return ChatBus.PASS;
    }

    private static void onSack(List<SackApi.SackChange> changes) {
        if (!Features.on(GEM) || !useNextSack) return;
        useNextSack = false;
        for (SackApi.SackChange c : changes) {
            if (c.delta() < 0 || c.internalName() == null) continue;
            String readable = c.internalName().replace('_', ' ').toLowerCase(Locale.ROOT);
            Matcher m = Patterns.get("SH.mining.roughgemstone").matcher(readable);
            if (m.find()) gemCoins += c.delta() * fraction(1) * gemPrice(gemId(m.group("gemstone")));
        }
    }

    private static void resetGem() {
        gemStart = gemLastMined = MiningEnv.FAR_PAST;
        gemUptime = 0;
        gemCoins = 0;
        gemPaused = false;
        lastGemstone = "";
    }

    private static void tickGem(Minecraft mc) {
        long now = MiningEnv.now();
        if (now < gemNextSecond || MiningEnv.isFarPast(gemLastMined)) return;
        gemNextSecond = now + 1000;
        if (MiningEnv.passedSince(gemLastMined) > MiningPack.number(GEM, "mining_gemstone_money.timeout", 15) * 1000L) {
            if (MiningPack.on(GEM, "mining_gemstone_money.pause")) gemPaused = true;
            else resetGem();
        } else gemUptime += 1000;
    }

    private static List<RichCard.Line> gemLines(String name, double price, double perHour, double coins, long uptime, boolean paused) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Selling " + name, "§6" + Prices.coins(price) + " §aeach").brief(name));
        out.add(RichCard.Line.row("Coins/hr", "§6" + Prices.coins(perHour)));
        out.add(RichCard.Line.row("Coins made", "§6" + Prices.coins(coins)));
        out.add(RichCard.Line.row("Uptime", "§b" + MiningEnv.format(uptime) + (paused ? " §c(PAUSED)" : "")));
        return out;
    }

    private static final RichCard GEM_CARD = new RichCard(GEM, "Gemstone money", "Mining", Zone.TOP_LEFT, 47, GEM) {
        @Override
        public boolean visible() {
            return !MiningEnv.isFarPast(gemStart) && !lastGemstone.isEmpty();
        }

        private double perHour() {
            return gemCoins / Math.max(gemUptime / 1000.0, 1) * 3600;
        }

        @Override
        protected Content live() {
            return new Content("", gemLines(MiningPack.choice(GEM, "mining_gemstone_money.type", "Flawless") + " " + lastGemstone, gemPrice(gemId(lastGemstone)), perHour(), gemCoins, gemUptime, gemPaused));
        }

        @Override
        protected Content sample() {
            return new Content("", gemLines("Flawless Ruby", 2_400_000, 8_300_000, 2_100_000, 912_000, false));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Coins/hr", Prices.coins(perHour()), 0), new Row("Coins made", Prices.coins(gemCoins), 0), new Row("Uptime", MiningEnv.format(gemUptime), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Coins/hr", "8.3M", 0), new Row("Coins made", "2.1M", 0), new Row("Uptime", "15m 12s", 0));
        }
    };

    // dark monolith

    private static BlockPos egg, lastEgg;
    private static long nextEggScan, nextEggView;
    private static final List<BlockPos> EGGS = new ArrayList<>();

    private static boolean canSee(Minecraft mc, BlockPos p) {
        Vec3 eye = mc.player.getEyePosition();
        double[][] faces = {{0.5, 1.01, 0.5}, {-0.01, 0.5, 0.5}, {1.01, 0.5, 0.5}, {0.5, 0.5, -0.01}, {0.5, 0.5, 1.01}};
        for (double[] f : faces) {
            Vec3 at = new Vec3(p.getX() + f[0], p.getY() + f[1], p.getZ() + f[2]);
            HitResult hit = mc.level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS || (hit instanceof BlockHitResult b && b.getBlockPos().equals(p))) return true;
        }
        return false;
    }

    private static void tickMonolith(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (mc.level == null || mc.player == null) return;
        if (now >= nextEggScan) {
            nextEggScan = now + 1000;
            EGGS.clear();
            for (BlockPos p : BlockPos.betweenClosed(mc.player.blockPosition().offset(-40, -40, -40), mc.player.blockPosition().offset(40, 40, 40))) {
                if (mc.level.getBlockState(p).is(Blocks.DRAGON_EGG)) EGGS.add(p.immutable());
            }
        }
        if (now < nextEggView) return;
        nextEggView = now + 500;
        egg = null;
        for (BlockPos p : EGGS) {
            if (canSee(mc, p)) {
                egg = p;
                break;
            }
        }
        if (egg != null && !egg.equals(lastEgg)) {
            lastEgg = egg;
            if (MiningPack.on(MONOLITH, "mining_dark_monolith.title")) {
                MiningEnv.world.title(dev.zmuzy.sbc.feature.Features.get(MONOLITH).option("mining_dark_monolith.title_text").text());
            }
        }
    }

    private static void drawMonolith(WorldDraw w) {
        if (egg == null) return;
        AABB box = new AABB(egg);
        if (MiningPack.on(MONOLITH, "mining_dark_monolith.highlight")) {
            int c = MiningPack.color(MONOLITH, "mining_dark_monolith.highlight_color", 0x4B9B1DC2);
            w.filledBox(box, c & 0xFFFFFF, ((c >>> 24) & 0xFF) / 255f, false);
        }
        if (MiningPack.on(MONOLITH, "mining_dark_monolith.beacon")) w.beacon(new Vec3(egg.getX() + 0.5, egg.getY() + 1, egg.getZ() + 0.5), MiningPack.color(MONOLITH, "mining_dark_monolith.beacon_color", 0xFF9B1DC2), false);
    }

    private static void leaveMonolith() {
        egg = lastEgg = null;
        EGGS.clear();
    }

    private static ChatBus.Result onMonolithChat(ChatBus.Line line) {
        if (Patterns.get("SH.mining.dwarves.darkmonolith.drop.chat.colorless").matcher(line.plain()).find()) leaveMonolith();
        return ChatBus.PASS;
    }

    public static void register() {
        Features.add(new Feature(PRECISION, "Mining", "Precision Mining spot", "A box on the particle that shows where Precision Mining wants you to look, green while you look at it.", "SkyHanni: Precision Mining Helper")
                .when(OreMined::customMining).defaultOff().world(MiningExtrasOff::drawPrecision).left(() -> particle = null));
        ParticleHooks.listen(MiningExtrasOff::onParticle);
        Features.add(new Feature(CULT, "Mining", "Fallen Star Cult", "When the next Cult Meeting is, or how long this one still runs. It shows where you tick; with nothing ticked, always.", "SkyHanni: Fallen Star Cult Timer")
                .when(MiningPack::skyblock).defaultOff()
                .option(Option.bool("mining_fallen_star.in_mines", "In the Dwarven Mines", "", false))
                .option(Option.bool("mining_fallen_star.in_room", "In the Cult Room", "", true))
                .option(Option.bool("mining_fallen_star.helmet", "Wearing the Fallen Star Helmet", "", true)));
        HudLayout.add(CULT_CARD);
        Feature k = Features.add(new Feature(KING, "Mining", "King Talisman", "Which Dwarven Kings you still have to talk to and when each stands in the Royal Palace.", "SkyHanni: King Talisman")
                .when(MiningPack::skyblock).defaultOff()
                .option(Option.bool("mining_king_talisman.outside", "Outside the Mines too", "", false)));
        ChatBus.register(ChatBus.OBSERVE + 29, KING, k, MiningExtrasOff::onKingChat);
        MenuMarks.add(KING, "Commissions", MiningExtrasOff::readKing);
        HudLayout.add(KING_CARD);
        Feature g = Features.add(new Feature(GEM, "Mining", "Gemstone money", "What the gemstones you mine would sell for per hour, from your pristine procs and what goes to your sacks.", "SkyHanni: Gemstone Money per Hour Display")
                .when(MiningPack::skyblock).defaultOff().tick(MiningExtrasOff::tickGem).left(MiningExtrasOff::resetGem)
                .option(Option.choice("mining_gemstone_money.type", "Sell as", "", "Flawless", GEM_TYPES))
                .option(Option.bool("mining_gemstone_money.force_npc", "NPC price only", "", false))
                .option(Option.bool("mining_gemstone_money.pause", "Pause when not mining", "Otherwise it resets.", true))
                .option(Option.integer("mining_gemstone_money.timeout", "Not mining after (s)", "", 15, 10, 30)));
        ChatBus.register(ChatBus.OBSERVE + 30, GEM, g, MiningExtrasOff::onGemChat);
        SackApi.onChange.add(MiningExtrasOff::onSack);
        HudLayout.add(GEM_CARD);
        Feature m = Features.add(new Feature(MONOLITH, "Mining", "Dark Monolith marks", "The Dark Monolith's egg, once you can see it: a title, a colour and a beam. What it gives is on the Dark Monolith card.", "SkyHanni: Dark Monolith")
                .when(() -> "DWARVEN_MINES".equals(GameState.island)).defaultOff().tick(MiningExtrasOff::tickMonolith).world(MiningExtrasOff::drawMonolith).left(MiningExtrasOff::leaveMonolith)
                .option(Option.bool("mining_dark_monolith.title", "Title when you see it", "", true))
                .option(Option.text("mining_dark_monolith.title_text", "Title text", "", "Dark Monolith"))
                .option(Option.bool("mining_dark_monolith.highlight", "Colour the egg", "", true))
                .option(Option.color("mining_dark_monolith.highlight_color", "Egg colour", "", 0x4B9B1DC2))
                .option(Option.bool("mining_dark_monolith.beacon", "Beam", "", true))
                .option(Option.color("mining_dark_monolith.beacon_color", "Beam colour", "", 0xFF9B1DC2)));
        ChatBus.register(ChatBus.OBSERVE + 31, MONOLITH, m, MiningExtrasOff::onMonolithChat);
        Safe.commands("MiningExtrasOff.commands", (d, r) -> d.register(literal("sbc")
                .then(literal("resetgemstone").executes(c -> {
                    resetGem();
                    MiningPack.chat("Reset gemstone money per hour display!");
                    return 1;
                }))
                .then(literal("resetkinghelper").executes(c -> {
                    MiningEnv.profile().kingsTalkedTo.clear();
                    MiningPack.chat("Reset the King Talisman card!");
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("resetgemstone", "Reset the Gemstone money card");
        dev.zmuzy.sbc.core.Commands.describe("resetkinghelper", "Reset the King Talisman card");
    }
}
