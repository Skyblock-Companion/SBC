// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.winter;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.areas.FishHooks;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Mayor;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.events.EventData;
import io.github.zmuzy.sbc.events.EventSheet;
import io.github.zmuzy.sbc.events.carnival.Carnival;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// jerry's workshop and mayor jerry. from skyhanni FrozenTreasureHighlighter, TimeFeatures (island closing
// time), NewYearCakeReminder, ReindrakeWarpHelper, HighlightJerries + WinterApi, and skyblocker JerryTimer
public final class Winter {
    public static final String TREASURES = "frozen_treasure_highlight", CLOSING = "winter_close_time", CAKE = "new_year_cake_reminder", REINDRAKE = "reindrake_warp", JERRIES = "jerry_highlight", JERRY_TIMER = "jerry_timer";
    // JerryTimer: hidden jerry cooldown
    private static final long JERRY_COOLDOWN = 6 * 60_000;
    private static final int JERRY_RANGE = 80;

    private static final Set<BlockPos> treasures = new HashSet<>();
    private static final Map<Entity, Integer> jerries = new HashMap<>();
    private static long jerryReadyAt, lastCakeReminder;

    private Winter() {}

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    private static boolean workshop() {
        return "WINTER".equals(GameState.island) || SbcMod.DEV;
    }

    // WinterApi.inGlacialCave, by the sidebar area
    public static boolean inGlacialCave() {
        return SbcMod.DEV || "WINTER".equals(GameState.island) && GameState.area.contains("Glacial Cave");
    }

    public static boolean december() {
        return LocalDate.now().getMonth() == Month.DECEMBER;
    }

    private static boolean perk(String name) {
        if (SbcMod.DEV) return true;
        for (Mayor.Perk p : Mayor.perks) if (p.name().startsWith(name)) return true;
        return false;
    }

    // --- FrozenTreasureHighlighter ---

    // onTick: a stand with only a helmet on (not a mob or player head) holds a treasure 2 blocks up
    static boolean holdsTreasure(int itemsOn, boolean helmet, boolean skull, String helmetName) {
        return itemsOn == 1 && helmet && !(skull && helmetName.endsWith("Head"));
    }

    private static void treasureTick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 5 != 0) return;
        treasures.clear();
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(50))) {
            int on = 0;
            for (EquipmentSlot slot : EquipmentSlot.values()) if (!a.getItemBySlot(slot).isEmpty()) on++;
            ItemStack helmet = a.getItemBySlot(EquipmentSlot.HEAD);
            if (!holdsTreasure(on, !helmet.isEmpty(), helmet.is(Items.PLAYER_HEAD), helmet.getHoverName().getString())) continue;
            BlockPos at = a.blockPosition().above(2);
            BlockState state = mc.level.getBlockState(at);
            if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)) treasures.add(at);
        }
    }

    private static void treasureDraw(WorldDraw w) {
        int argb = Combat.color(TREASURES, "color", 0xBF55FF55);
        for (BlockPos p : treasures) {
            float alpha = (argb >>> 24) / 255f;
            w.filledBox(new AABB(p).inflate(0.005), argb & 0xFFFFFF, alpha, false);
        }
    }

    // --- TimeFeatures: when the workshop closes ---

    // time to the next skyblock year. over 5 days = workshop already closing
    static long untilClose(long now) {
        return SbTime.sbToReal(SbTime.sbDate(now).year() + 1, 0, 1) - now;
    }

    private static void closeLine(EventSheet s, long left) {
        if (left > 5 * 86_400_000L) s.line("Jerry's Workshop", "Closing!", Modern.BAD).brief("Workshop");
        else s.line("Closes in", SbTime.dur(left), Modern.AQUA).icon("snowball");
    }

    private static final HudElement CLOSE_CARD = new EventSheet.Card(CLOSING, "Jerry's Workshop", "Events", Zone.TOP_LEFT, 49, CLOSING) {
        @Override
        public boolean visible() {
            return workshop() && !december();
        }

        @Override
        protected void live(EventSheet s) {
            closeLine(s, untilClose(System.currentTimeMillis()));
        }

        @Override
        protected void sample(EventSheet s) {
            closeLine(s, 3 * 3_600_000L + 12 * 60_000L);
        }

        @Override
        protected List<Row> plainRows() {
            return List.of(new Row("Closes in", SbTime.dur(untilClose(System.currentTimeMillis())), 0));
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Closes in", "3h 12m", 0));
        }
    };

    // --- NewYearCakeReminder ---

    private static void cakeClaimed() {
        EventData.get().cakeCollectedYear = SbTime.sbDate(System.currentTimeMillis()).year();
        Store.markDirty();
    }

    // isCakeTime: the sidebar's New Year line
    static boolean cakeTime(List<String> sidebar) {
        for (String l : sidebar) if (io.github.zmuzy.sbc.events.EventPack.plain("SH.event.winter.newyearcake.reminder.sidebar").matcher(GameState.strip(l)).matches()) return true;
        return false;
    }

    private static void cakeTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastCakeReminder < 30_000 || Carnival.busy() || !cakeTime(GameState.scoreboardFormatted)) return;
        if (EventData.get().cakeCollectedYear == SbTime.sbDate(now).year()) return;
        lastCakeReminder = now;
        Combat.sayClickable("Your free New Year Cake is waiting. §eClick to open the baker's menu.", "/openbaker");
    }

    // --- HighlightJerries ---

    // colour of a jerry thats yours, -1 for a stand that says something else. from skyhanni
    // MobFilter.jerryPattern + HighlightJerries colours
    static int jerryColor(String cleanName, String player) {
        Matcher m = Patterns.get("SH.mob.detection.jerry").matcher(cleanName);
        // the stand name can keep the "a" of a colour code before the owner, the source's own lines show it
        if (!m.matches() || !m.group("owner").equals(player) && !m.group("owner").equals("a" + player)) return -1;
        Matcher c = Patterns.get("SH.jerry.highlight").matcher(m.group("name"));
        if (!c.lookingAt()) return -1;
        return switch (c.group("color")) {
            case "Green" -> 0x55FF55;
            case "Blue" -> 0x5555FF;
            case "Purple" -> 0xAA00AA;
            case "Golden" -> 0xFFAA00;
            default -> -1;
        };
    }

    private static final FishHooks.Scan JERRY_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            return Features.on(JERRIES) && perk("Jerrypocalypse");
        }

        @Override
        public void begin() {
            jerries.clear();
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof ArmorStand a) || !a.hasCustomName()) return;
            String name = GameState.strip(a.getCustomName().getString());
            if (!name.contains("Jerry")) return;
            Minecraft mc = Minecraft.getInstance();
            int rgb = jerryColor(name, mc.getUser().getName());
            if (rgb < 0 || !(a.level().getEntity(a.getId() - 1) instanceof LivingEntity base)) return;
            jerries.put(base, rgb);
            if (Combat.opt(JERRIES, "highlight") && Combat.seen(base, 64, base.getBbHeight() * 0.8)) FishHooks.glow(base, rgb);
        }
    };

    private static void jerryDraw(WorldDraw w) {
        if (!Combat.opt(JERRIES, "line")) return;
        for (Map.Entry<Entity, Integer> e : jerries.entrySet()) {
            if (!e.getKey().isAlive() || e.getKey().distanceTo(w.mc.player) > JERRY_RANGE) continue;
            w.line(Combat.fromCrosshair(w), e.getKey().position().add(0, e.getKey().getBbHeight() / 2, 0), 0xFF000000 | e.getValue(), 2, false);
        }
    }

    // --- JerryTimer ---

    // lines that mean a jerry came: "§b ☺ §eThere is a §aGreen Jerry§e!" in different words, with something to hover
    // by words, colours in the text or not. not what a jerry box says
    static boolean jerrySpawned(String text, boolean hasHover) {
        String clean = GameState.strip(text);
        return hasHover && clean.startsWith(" ☺ ") && clean.contains("Jerry") && !clean.contains("Jerry Box");
    }

    private static boolean hasHover(net.minecraft.network.chat.Component c) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText) return true;
        for (var s : c.getSiblings()) if (hasHover(s)) return true;
        return false;
    }

    private static void jerryLine(EventSheet s, long left) {
        s.line("Next Jerry in", SbTime.dur(left), Modern.GOOD).icon("villager_spawn_egg").meter(1 - left / (double) JERRY_COOLDOWN).brief("Next Jerry");
    }

    private static final HudElement JERRY_CARD = new EventSheet.Card(JERRY_TIMER, "Jerry", "Events", Zone.TOP_LEFT, 49, JERRY_TIMER) {
        @Override
        public boolean visible() {
            return sb() && jerryReadyAt > System.currentTimeMillis();
        }

        @Override
        protected void live(EventSheet s) {
            jerryLine(s, jerryReadyAt - System.currentTimeMillis());
        }

        @Override
        protected void sample(EventSheet s) {
            jerryLine(s, 3 * 60_000 + 12_000);
        }

        @Override
        protected List<Row> plainRows() {
            return List.of(new Row("Next Jerry in", SbTime.dur(jerryReadyAt - System.currentTimeMillis()), 0));
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Next Jerry in", "3m 12s", 0));
        }
    };

    private static void jerryTick(Minecraft mc) {
        if (jerryReadyAt == 0 || System.currentTimeMillis() < jerryReadyAt) return;
        jerryReadyAt = 0;
        if (mc.player == null) return;
        Combat.say("§aJerry cooldown is over!");
        mc.player.playSound(SoundEvents.VILLAGER_TRADE, 1f, 1f);
    }

    public static void register() {
        Features.add(new Feature(TREASURES, "Events", "Frozen Treasure highlight", "In the Glacial Cave, the block of ice each Frozen Treasure is in.", "SkyHanni: Frozen Treasure Highlighter")
                .when(Winter::inGlacialCave)
                .tick(Winter::treasureTick)
                .world(Winter::treasureDraw)
                .left(treasures::clear)
                .option(Option.color("frozen_treasure_highlight.color", "Colour", "", 0xBF55FF55)));
        Features.add(new Feature(CLOSING, "Events", "Workshop closing time", "On Jerry's Workshop, how long until it closes.", "SkyHanni: Island Close Time").when(Winter::workshop));
        Features.add(new Feature(CAKE, "Events", "New Year Cake reminder", "While the New Year Event is on, a click in chat to get your free cake, until you have it.", "SkyHanni: New Year Cake Reminder")
                .when(Winter::sb)
                .tick(Winter::cakeTick));
        Features.add(new Feature(REINDRAKE, "Events", "Reindrake warp", "When someone summons a Reindrake on Jerry's Workshop, a click in chat to warp to the island's spawn.", "SkyHanni: Reindrake Warp Helper").when(Winter::workshop).defaultOff());
        Features.add(new Feature(JERRIES, "Events", "Your Jerries", "While Jerry's Jerrypocalypse is on, a glow on your Jerries where you can see them, in their colour. A line to them can be added.", "SkyHanni: Highlight Jerries")
                .when(Winter::sb)
                .world(Winter::jerryDraw)
                .left(jerries::clear)
                .option(Option.bool("jerry_highlight.highlight", "Glow", "", true))
                .option(Option.bool("jerry_highlight.line", "Line to your Jerries", "", false)));
        Features.add(new Feature(JERRY_TIMER, "Events", "Jerry cooldown", "While Jerry's Jerrypocalypse is on, how long until a hidden Jerry can come again, with a message and a sound when it can.", "Skyblocker: Jerry Timer")
                .when(Winter::sb)
                .tick(Winter::jerryTick));
        MenuMarks.add(CAKE, "Baker", v -> cakeClaimed());
        ChatBus.register(ChatBus.OBSERVE + 27, "winter", null, line -> {
            if (!GameState.inSkyBlock) return ChatBus.PASS;
            String clean = GameState.strip(line.plain());
            if (clean.equals("You claimed a New Year Cake!")) cakeClaimed();
            if (Features.on(REINDRAKE) && "WINTER".equals(GameState.island) && Patterns.get("SH.event.winter.reindrake.spawn.message").matcher(clean).matches())
                Combat.sayClickable("A Reindrake was summoned. §eClick to warp to the Winter Island spawn.", "/warp winter");
            if (Features.on(JERRY_TIMER) && perk("Jerrypocalypse") && jerrySpawned(line.component().getString(), hasHover(line.component())))
                jerryReadyAt = System.currentTimeMillis() + JERRY_COOLDOWN;
            return ChatBus.PASS;
        });
        FishHooks.scan(JERRY_SCAN);
        HudLayout.add(CLOSE_CARD);
        HudLayout.add(JERRY_CARD);
    }
}
