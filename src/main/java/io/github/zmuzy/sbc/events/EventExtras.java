// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Mayor;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.core.data.SbCalendar;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Toasts;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// small event stuff, each part ported from the class named above it
// season places opening, stonks auction, year of the pig, century, great spook answers, early commands,
// jerrys perkpocalypse, calendar reminders
public final class EventExtras {
    public static final String SEASON = "season_locked", STONKS = "stonks_auction", PIG = "pig_lines", STEW = "witch_stew", TEAM = "anniversary_team_finder", RAFFLE = "anniversary_raffle", SPOOK = "spook_chat_answers", EARLY = "prevent_early_commands", PERKPOCALYPSE = "perkpocalypse_warning", NOTIFY = "event_notifications";

    private static long lastSecond;

    private EventExtras() {}

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    private static boolean on(String id) {
        Feature f = Features.get(id);
        return f != null && f.enabled();
    }

    private static boolean opt(String id, String key, boolean def) {
        Feature f = Features.get(id);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static void chat(Component c) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(c);
    }

    private static void chat(String message) {
        chat(Component.literal("§b[SBC] §r" + message));
    }

    // the player clicks to send, nothing goes out by itself
    private static void clickable(String message, String command, String hover) {
        chat(Component.literal("§b[SBC] §r" + message).withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))));
    }

    private static void title(String text) {
        var mc = Minecraft.getInstance();
        mc.gui.hud.setTimes(5, 40, 10);
        mc.gui.hud.setTitle(Component.literal(text));
        mc.gui.hud.setSubtitle(Component.empty());
    }

    // --- from skyhanni SeasonLockedChat ---

    // current month's start already passed so that ones next year. month 0 = early spring
    static long nextStartOfMonth(int month, SbTime.SbDate now) {
        return SbTime.sbToReal(month > now.month() ? now.year() : now.year() + 1, month, 1);
    }

    static String seasonLine(String plain, long now) {
        Matcher m = Patterns.get("SH.event.timing.season.locked").matcher(plain);
        if (!m.matches()) return null;
        int month = java.util.Arrays.asList(SbTime.MONTHS).indexOf(m.group("season"));
        if (month < 0) return null;
        long start = nextStartOfMonth(month, SbTime.sbDate(now));
        String date = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ROOT).format(Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()));
        return "§e" + m.group("name") + " §7opens in §b" + SbTime.dur(start - now) + " §7(" + date + ")";
    }

    // --- from skyhanni StonksAuctionTimer + StockOfStonkFeature.updateAuctionData ---

    private static final long ROUND_TOLERANCE = 5 * 60_000;
    private static long lastSeenRoundEnd = Long.MIN_VALUE / 4, lastWarnedRoundEnd = Long.MIN_VALUE / 4;

    static boolean sameRound(long a, long b) {
        return Math.abs(a - b) <= ROUND_TOLERANCE;
    }

    // tooltip value until it expires, then the calendar's guess. rounds end at the end of a skyblock month
    static long roundEnd(long exact, long now) {
        if (exact > now) return exact;
        SbTime.SbDate d = SbTime.sbDate(now);
        return SbTime.sbToReal(d.year(), d.month(), 1) + SbTime.MONTH;
    }

    private static boolean stockExchange() {
        for (Mayor.Perk p : Mayor.perks) if (p.name().startsWith("Stock Exchange")) return true;
        return false;
    }

    // updateAuctionData: the stonks auction tooltip says when the round ends and what you bid
    private static void stonksTooltip(ItemStack stack, List<Component> lines) {
        var mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.screen() == null) return;
        Patterns.P inventory = Patterns.p("SH.inventory.stockofstonks.inventory");
        if (!inventory.matcher(GameState.strip(mc.gui.screen().getTitle().getString()).trim()).matches() || !inventory.matcher(GameState.strip(stack.getHoverName().getString()).trim()).matches()) return;
        Long end = null, bid = null;
        for (Component c : lines) {
            String line = c.getString();
            Matcher m = Patterns.get("SH.inventory.stockofstonks.endsin").matcher(line);
            if (m.matches()) end = System.currentTimeMillis() + io.github.zmuzy.sbc.events.hoppity.HoppityEnv.duration(m.group("duration"));
            m = Patterns.get("SH.inventory.stockofstonks.currentbid").matcher(line);
            if (m.matches() && m.group("amount") != null) bid = Long.parseLong(m.group("amount").replace(",", ""));
        }
        if (end == null) return;
        Store.I.stonksRoundEnd = end;
        if (bid != null) {
            Store.I.stonksLastBidRoundEnd = end;
            Store.I.stonksBid = bid;
        }
        Store.markDirty();
    }

    private static void stonksSecond(long now) {
        if (!on(STONKS) || !stockExchange()) return;
        long end = roundEnd(Store.I.stonksRoundEnd, now);
        if (end <= now) return;
        if (!sameRound(end, lastSeenRoundEnd)) {
            boolean first = lastSeenRoundEnd <= Long.MIN_VALUE / 4;
            lastSeenRoundEnd = end;
            lastWarnedRoundEnd = Long.MIN_VALUE / 4;
            if (opt(STONKS, "new_round", true) && !first) chat("§6A new Stonks Auction round has started! §7Ends in §e" + SbTime.dur(end - now));
        }
        if (!opt(STONKS, "one_minute", true) || end - now > 60_000 || sameRound(end, lastWarnedRoundEnd)) return;
        lastWarnedRoundEnd = end;
        String bid = sameRound(Store.I.stonksLastBidRoundEnd, end) ? "" : " §c§lYou haven't bid yet!";
        title("§6Stonks Auction ending soon!" + bid);
        chat("§6The Stonks Auction round ends in less than a minute!" + bid);
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f);
    }

    private static void stonksSheet(EventSheet s, long left, boolean bid, long amount) {
        s.line("Ends in", SbTime.dur(left), left < 60_000 ? Modern.BAD : left < 600_000 ? Modern.WARN : Modern.GOLD);
        if (bid) s.line("Your bid", String.format(Locale.ROOT, "%,d", amount), Modern.GOOD).chip("BID", Modern.GOOD).chipRight();
        else s.line("Not bid", "", Modern.BAD).labelColor(Modern.BAD);
    }

    private static final HudElement STONKS_EL = new EventSheet.Card(STONKS, "Stonks Auction", "Events", Zone.TOP_LEFT, 51, STONKS) {
        @Override public boolean visible() {
            return sb() && opt(STONKS, "countdown", true) && stockExchange() && roundEnd(Store.I.stonksRoundEnd, System.currentTimeMillis()) > System.currentTimeMillis();
        }

        @Override protected void live(EventSheet s) {
            long now = System.currentTimeMillis(), end = roundEnd(Store.I.stonksRoundEnd, now);
            stonksSheet(s, end - now, sameRound(Store.I.stonksLastBidRoundEnd, end), Store.I.stonksBid);
        }

        @Override protected void sample(EventSheet s) {
            stonksSheet(s, 42 * 60_000, true, 2_400_002);
        }

        @Override protected List<Row> plainRows() {
            long now = System.currentTimeMillis(), end = roundEnd(Store.I.stonksRoundEnd, now);
            return List.of(new Row("§6Stonks Auction §7ends in §e" + SbTime.dur(end - now)), new Row(sameRound(Store.I.stonksLastBidRoundEnd, end) ? "§a✔ Bid: §e" + String.format(Locale.ROOT, "%,d", Store.I.stonksBid) + " Coins" : "§c✘ Not bid!"));
        }

        @Override protected List<Row> plainDemo() {
            return List.of(new Row("§6Stonks Auction §7ends in §e42m"), new Row("§a✔ Bid: §e2,400,002 Coins"));
        }
    };

    // --- from skyhanni PigFeatures, PigFeaturesApi ---

    private record Orb(int pig, Vec3 location, long spawnTime) {}

    private static final List<Orb> ORBS = new ArrayList<>();

    static boolean yearOfThePig(int year) {
        return year % 12 == 11;
    }

    private static boolean isYearOfThePig() {
        return yearOfThePig(SbTime.sbDate(System.currentTimeMillis()).year());
    }

    private static boolean shinyPig(Entity e) {
        if (!(e instanceof Pig)) return false;
        for (ArmorStand st : e.level().getEntitiesOfClass(ArmorStand.class, e.getBoundingBox().inflate(0.5, 3, 0.5))) if (st.getName().getString().contains("SHINY PIG")) return true;
        return false;
    }

    private static InteractionResult pigClick(Entity entity) {
        if (!on(PIG) || !"HUB".equals(GameState.island) || !isYearOfThePig()) return InteractionResult.PASS;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !"SHINY_ORB".equals(Gain.sbId(mc.player.getMainHandItem())) || !shinyPig(entity)) return InteractionResult.PASS;
        int id = entity.getId();
        Vec3 from = entity.position();
        later(1000, () -> {
            for (Orb o : ORBS) if (o.pig == id) return;
            String skull = io.github.zmuzy.sbc.dungeon.DungeonMarks.skull("SHINY_PIG_ORB");
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            ArmorStand best = null;
            for (ArmorStand st : level.getEntitiesOfClass(ArmorStand.class, new AABB(from, from).inflate(5))) {
                if (!skull.isEmpty() && skull.equals(io.github.zmuzy.sbc.dungeon.DungeonMarks.headTexture(st.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD))) && (best == null || st.distanceToSqr(from) < best.distanceToSqr(from))) best = st;
            }
            if (best != null) ORBS.add(new Orb(id, best.position().add(0, 2, 0), System.currentTimeMillis()));
        });
        return InteractionResult.PASS;
    }

    private static void pigChat(String plain) {
        if (ORBS.isEmpty()) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (Patterns.get("SH.event.year-of-the-pig.chat.orb.looted").matcher(plain).matches()) {
            later(1000, () -> {
                var now = Minecraft.getInstance().level;
                ORBS.removeIf(o -> (now == null || now.getEntity(o.pig) == null) && System.currentTimeMillis() - o.spawnTime > 2000);
            });
        }
        if (Patterns.get("SH.event.year-of-the-pig.chat.orb.expired.colorless").matcher(plain).matches()) {
            Orb oldest = ORBS.get(0);
            if (level.getEntity(oldest.pig) == null) ORBS.removeIf(o -> o.pig == oldest.pig);
        }
    }

    private static void pigLines(WorldDraw w) {
        if (ORBS.isEmpty() || w.mc.level == null || w.mc.player == null) return;
        Vec3 start = w.eye.add(w.mc.player.getLookAngle().scale(2));
        for (Orb o : ORBS) {
            Entity pig = w.mc.level.getEntity(o.pig);
            if (pig == null) continue;
            Vec3 at = pig.position().add(0, 0.54, 0);
            if (opt(PIG, "to_pig", true)) w.line(start, at, 0xFFFFAFAF, 3, false);
            if (opt(PIG, "to_orb", true)) w.line(at, o.location.add(0, -0.5, 0), 0xFFFFFF00, 3, false);
        }
    }

    // --- from skyhanni Year400Features ---

    // CakeColor
    private static final Map<String, Character> CAKES = Map.of("SLICE_OF_STRAWBERRY_SHORTCAKE", 'd', "SLICE_OF_BLUEBERRY_CAKE", '9', "SLICE_OF_CHEESECAKE", 'e', "SLICE_OF_GREEN_VELVET_CAKE", 'a', "SLICE_OF_RED_VELVET_CAKE", 'c');
    private static final Map<Character, Integer> CAKE_COLORS = Map.of('d', 0xFFFF55FF, '9', 0xFF5555FF, 'e', 0xFFFFFF55, 'a', 0xFF55FF55, 'c', 0xFFFF5555);
    private static final int WRONG_TEAM = 0xFF4C4C4C;
    private static final Map<Integer, Character> playerCakes = new HashMap<>();
    private static int lastClickedPlayer = -1;
    private static long lastClickedAt;

    // playerColorNametagPattern: team colour or null
    static Character teamOf(String displayName) {
        Matcher m = Patterns.get("SH.event.anniversary-celebration.400.team-finder.player-color-nametag").matcher(displayName);
        if (!m.matches()) return null;
        char c = m.group("color").charAt(0);
        // extreme banker emblem is the same symbol in gold
        return c == '6' || !CAKE_COLORS.containsKey(c) ? null : c;
    }

    private static void teamGlow(java.util.function.BiConsumer<Entity, Integer> mark) {
        if (!on(TEAM) || !sb()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Character inHand = CAKES.get(Gain.sbId(mc.player.getMainHandItem()));
        if (inHand == null) return;
        for (var p : mc.level.players()) {
            if (!(p instanceof RemotePlayer) || p.getUUID().version() == 2) continue;
            playerCakes.computeIfAbsent(p.getId(), id -> {
                Character team = teamOf(LegacyText.of(p.getDisplayName()));
                return team == null ? '?' : team;
            });
            mark.accept(p, inHand.equals(playerCakes.get(p.getId())) ? CAKE_COLORS.get(inHand) : WRONG_TEAM);
        }
    }

    private static InteractionResult teamClick(Entity entity) {
        if (on(TEAM) && entity instanceof RemotePlayer) {
            lastClickedPlayer = entity.getId();
            lastClickedAt = System.currentTimeMillis();
        }
        return InteractionResult.PASS;
    }

    private static void teamChat(String plain) {
        if (lastClickedPlayer < 0 || !on(TEAM) || System.currentTimeMillis() - lastClickedAt >= 500) return;
        if (!Patterns.get("SH.event.anniversary-celebration.400.team-finder.player-full-message").matcher(plain).matches()) return;
        playerCakes.put(lastClickedPlayer, '?');
        lastClickedPlayer = -1;
    }

    // SkyblockGuideHighlightFeature "Raffle Tasks"
    private static void raffleMarks(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            if (v.item(slot).loreHas("§c§lINCOMPLETE")) v.back(slot, MenuView.RED);
            else if (v.item(slot).lastClean().equals("COMPLETE") && raffleOption("anniversary_raffle.done")) v.back(slot, MenuView.GREEN);
        }
    }

    private static boolean raffleOption(String key) {
        var f = Features.get(RAFFLE);
        var o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    // from skyblocker RaffleRewardHighlight: prize to claim = red, claimed = green
    private static void raffleRewards(MenuView v) {
        if (!raffleOption("anniversary_raffle.rewards")) return;
        for (int slot = 0; slot < v.top; slot++) {
            switch (v.item(slot).lastClean()) {
                case "You claimed your prize!", "You've claimed this raffle!" -> v.back(slot, MenuView.GREEN);
                case "Click to reveal your prize!" -> v.back(slot, MenuView.RED);
                default -> {
                }
            }
        }
    }

    // --- from skyhanni TheGreatSpook: mathSolver, publicSpeakingSolver ---

    private static void spookChat(String plain) {
        if (!on(SPOOK)) return;
        Matcher m = Patterns.get("SH.event.greatspook.chat.math.colorless").matcher(plain);
        if (m.matches() && opt(SPOOK, "math", true)) {
            String answer = Events.solve(m.group("math"));
            if (answer.isEmpty()) later(0, () -> chat("§cFailed to solve " + m.group("math") + "!"));
            else later(0, () -> clickable("The answer is: §b" + answer + "§e. §7Click to send it.", "/ac " + answer, "§eClick to send the answer!"));
        }
        if (Patterns.get("SH.event.greatspook.chat.speaking.colorless").matcher(plain).matches() && opt(SPOOK, "speaking", true)) {
            later(0, () -> clickable("Public Speaking: §7click to say something.", "/ac Today is a good day to kill Spooky monsters.", "§eClick to send a line to all chat!"));
        }
    }

    // --- from skyhanni PreventEarlyCommands ---

    private static String lastCommand;
    private static long worldChanged;
    private static Object lastLevel;

    static long earlyDelay(int cooldownSeconds, long sinceWorldChange) {
        return Math.max(0, cooldownSeconds * 1000L - sinceWorldChange);
    }

    private static boolean onCommand(String command) {
        if (on(EARLY) && GameState.onHypixel) lastCommand = command;
        return true;
    }

    // true = hide hypixel's refusal
    private static boolean earlyChat(String message) {
        if (!on(EARLY) || lastCommand == null) return false;
        Matcher m = Patterns.get("SH.commands.cooldown").matcher(message);
        if (!m.matches()) return false;
        String command = lastCommand;
        long wait = earlyDelay(Integer.parseInt(m.group("cooldown")), System.currentTimeMillis() - worldChanged);
        later(wait, () -> {
            var c = Minecraft.getInstance().getConnection();
            if (c != null) c.sendCommand(command);
        });
        chat("§cCannot execute §e/" + command + " §cyet. §aRunning it in " + (long) Math.ceil(wait / 1000.0) + " seconds.");
        return true;
    }

    // --- from skyhanni ElectionApi: jerry's perkpocalypse mayor ---

    // perk that says which mayor jerry brought and until when, empty if unknown
    public static String perkpocalypsePerk = "";
    private static long perkpocalypseUntil, lastPerkReminder = Long.MIN_VALUE / 4;

    private static boolean jerry() {
        return Mayor.name.equals("Jerry");
    }

    // end of a 6h turn. mayor changes every 6h from the last election, max until the next one
    static long perkpocalypseEnd(long nextElection, long now) {
        long last = nextElection - SbTime.YEAR;
        for (int i = 1; i <= 21; i++) {
            long at = last + i * 6 * 3_600_000L;
            if (at > now) return Math.min(at, nextElection);
        }
        return 0;
    }

    private static void calendarMarks(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            Matcher head = Patterns.get("SH.mayorapi.mayor.head").matcher(it.name);
            if (!head.matches() || !"Jerry".equals(head.group("name"))) continue;
            for (int i = 0; i + 2 < it.lore.size(); i++) {
                if (!Patterns.get("SH.mayorapi.perkpocalypse").matcher(it.lore.get(i)).matches()) continue;
                perkpocalypsePerk = GameState.strip(it.lore.get(i + 2)).trim();
                perkpocalypseUntil = perkpocalypseEnd(Mayor.nextElection(System.currentTimeMillis()), System.currentTimeMillis());
                return;
            }
        }
    }

    private static void perkpocalypseSecond(long now) {
        if (!jerry()) return;
        if (!perkpocalypsePerk.isEmpty() && perkpocalypseUntil <= now) {
            perkpocalypsePerk = "";
            lastPerkReminder = now;
            if (on(PERKPOCALYPSE)) clickable("The Perkpocalypse Mayor has expired! Click here to update the new temporary Mayor.", "/calendar", "§eClick to open the calendar!");
            return;
        }
        if (!perkpocalypsePerk.isEmpty() || !on(PERKPOCALYPSE) || now - lastPerkReminder < 5 * 60_000 || "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island)) return;
        lastPerkReminder = now;
        clickable("The Perkpocalypse Mayor is not known! Click here to update the temporary Mayor.", "/calendar", "§eClick to open the calendar!");
    }

    // --- from skyblocker EventNotifications ---

    private static long lastNotify;

    // event key in settings
    private static String notifyKey(SbCalendar.Event e) {
        return "event_notifications." + e.name().toLowerCase(Locale.ROOT);
    }

    // which reminder falls between the last look and now: its minutes or -1. reminders = minutes before start
    static int dueReminder(long start, long previous, long now, List<Integer> minutes) {
        for (int m : minutes) {
            long at = start - m * 60_000L;
            if (now >= at && previous < at) return m;
        }
        return -1;
    }

    static List<Integer> reminderMinutes(String text) {
        List<Integer> out = new ArrayList<>();
        for (String p : text.split(",")) {
            try {
                out.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static boolean criterionMet() {
        Feature f = Features.get(NOTIFY);
        Option o = f == null ? null : f.option("criterion");
        String c = o == null ? "In SkyBlock" : o.text();
        return switch (c) {
            case "On Hypixel" -> GameState.onHypixel;
            case "Everywhere" -> true;
            default -> GameState.inSkyBlock;
        };
    }

    private static void notifySecond(long now) {
        long previous = lastNotify == 0 ? now : lastNotify;
        lastNotify = now;
        if (!on(NOTIFY) || !criterionMet()) return;
        Feature f = Features.get(NOTIFY);
        Option times = f.option("minutes");
        List<Integer> minutes = reminderMinutes(times == null ? "5,1" : times.text());
        for (SbCalendar.Event e : SbCalendar.Event.values()) {
            if (!e.computable() || !opt(NOTIFY, notifyKey(e), true)) continue;
            SbCalendar.Occurrence next = SbCalendar.next(e, now);
            if (next == null) continue;
            int due = dueReminder(next.start(), previous, now, minutes);
            if (due < 0) continue;
            Toasts.push(e.title, due == 0 ? "Starts now" : "Starts in " + due + (due == 1 ? " minute" : " minutes"), 0xFFFFD166);
            notifySound();
        }
    }

    private static void notifySound() {
        var p = Minecraft.getInstance().player;
        Feature f = Features.get(NOTIFY);
        Option o = f == null ? null : f.option("sound");
        if (p == null || o == null) return;
        switch (o.text()) {
            case "Bell" -> p.playSound(SoundEvents.BELL_BLOCK, 1f, 1f);
            case "Ding" -> p.playSound(SoundEvents.ARROW_HIT_PLAYER, 1f, 1f);
            case "Pling" -> p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
            default -> {
            }
        }
    }

    // --- together ---

    private record Later(long at, Runnable run) {}

    private static final List<Later> LATER = new ArrayList<>();

    private static void later(long ms, Runnable r) {
        synchronized (LATER) {
            LATER.add(new Later(System.currentTimeMillis() + ms, r));
        }
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        List<Runnable> due = new ArrayList<>();
        synchronized (LATER) {
            LATER.removeIf(l -> {
                if (l.at > now) return false;
                due.add(l.run);
                return true;
            });
        }
        for (Runnable r : due) r.run();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldChanged = now;
            lastCommand = null;
            ORBS.clear();
            playerCakes.clear();
            BeachBallHelper.onWorldChange();
        }
        if (mc.level == null || mc.player == null) return;
        BeachBallHelper.tick(mc);
        if (now - lastSecond < 1000) return;
        lastSecond = now;
        notifySecond(now);
        if (!sb()) return;
        stonksSecond(now);
        perkpocalypseSecond(now);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String message = LegacyText.of(line.component()), plain = line.plain();
        if (earlyChat(message)) return ChatBus.CANCEL;
        if (!sb()) return ChatBus.PASS;
        if (on(SEASON)) {
            String opens = seasonLine(plain, System.currentTimeMillis());
            if (opens != null) later(0, () -> chat(opens));
        }
        spookChat(plain);
        pigChat(plain);
        teamChat(plain);
        return ChatBus.PASS;
    }

    public static void register() {
        Features.add(new Feature(SEASON, "Events", "Season-locked places", "When Hypixel says something is only open in another season, how long until that season.", "SkyHanni: Season Locked Messages")
                .when(EventExtras::sb));
        Features.add(new Feature(STONKS, "Events", "Stonks Auction", "While the Stock Exchange perk runs: a card with the time left in the round and your bid, a line when a new round starts and a warning in its last minute. Open the Stonks Auction once for the exact time.", "SkyHanni: Stonks Auction")
                .when(EventExtras::sb).tooltip(EventExtras::stonksTooltip)
                .option(Option.bool("stonks_auction.countdown", "Countdown card", "", true))
                .option(Option.bool("stonks_auction.new_round", "New round line", "", true))
                .option(Option.bool("stonks_auction.one_minute", "Last minute warning", "", true)));
        Features.add(new Feature(PIG, "Events", "Shiny Orb lines", "In the Year of the Pig: a line from you to the Shiny Pig that carries your orb, and from it to the orb.", "SkyHanni: Year of the Pig")
                .when(() -> "HUB".equals(GameState.island)).world(EventExtras::pigLines)
                .option(Option.bool("pig_lines.to_pig", "You to the pig", "", true))
                .option(Option.bool("pig_lines.to_orb", "Pig to the orb", "", true)));
        Features.add(new Feature(TEAM, "Events", "Century cake team colours", "In the Century Celebration, holding a Slice of Cake: players of that team glow in its colour, the others grey.", "SkyHanni: Team Finder")
                .when(EventExtras::sb).defaultOff());
        Features.add(new Feature(RAFFLE, "Events", "Raffle tasks", "In the Century Celebration's Raffle Tasks menu, what is not done yet is red.", "SkyHanni: Raffle Task Highlighter")
                .when(EventExtras::sb)
                .option(Option.bool("anniversary_raffle.done", "Done tasks in green", "", true))
                .option(Option.bool("anniversary_raffle.rewards", "Prizes in Previous Raffles", "Red to claim, green claimed.", true)));
        Features.add(new Feature(SPOOK, "Events", "Great Spook answers in chat", "A chat line with the answer to Quick Maths or for the Public Speaking Demon. Clicking it sends it to all chat; nothing goes out by itself.", "SkyHanni: Primal Fear Solver")
                .when(EventExtras::sb).defaultOff()
                .option(Option.bool("spook_chat_answers.math", "Quick Maths", "", true))
                .option(Option.bool("spook_chat_answers.speaking", "Public Speaking", "", true)));
        Features.add(new Feature(EARLY, "Commands", "Run early commands later", "A command Hypixel refuses for being too soon after joining a server runs again by itself when it may.", "SkyHanni: Prevent Early Command Execution")
                .defaultOff());
        Features.add(new Feature(PERKPOCALYPSE, "Events", "Perkpocalypse mayor reminder", "While Jerry is mayor: a line to open the calendar when the temporary mayor is not known or has run out.", "SkyHanni: Unknown Perkpocalypse Mayor Warning")
                .when(EventExtras::sb));
        Features.add(new Feature(NOTIFY, "Events", "Calendar reminders", "A toast and a sound shortly before a SkyBlock event starts. Which events and how early are below.", "Skyblocker: Event Notifications")
                .option(Option.choice("event_notifications.criterion", "Where", "", "In SkyBlock", "In SkyBlock", "On Hypixel", "Everywhere"))
                .option(Option.choice("event_notifications.sound", "Sound", "", "Pling", "None", "Bell", "Ding", "Pling"))
                .option(Option.text("event_notifications.minutes", "Minutes before", "Separated by commas.", "5,1")));
        Feature notify = Features.get(NOTIFY);
        for (SbCalendar.Event e : SbCalendar.Event.values()) if (e.computable()) notify.option(Option.bool(notifyKey(e), e.title, "", true));
        CityProject.register();
        YearOfTheWitch.register();
        WitchIngredients.register();
        BeachBallHelper.register();
        HudLayout.add(STONKS_EL);
        MenuMarks.add(RAFFLE, "Raffle Tasks", EventExtras::raffleMarks);
        MenuMarks.add(RAFFLE, "Previous Raffles", EventExtras::raffleRewards);
        MenuMarks.add(PERKPOCALYPSE, Patterns.get("SH.calendarapi.gui"), EventExtras::calendarMarks);
        io.github.zmuzy.sbc.combat.CombatMarks.glowing(EventExtras::teamGlow);
        Safe.useEntity("EventExtras.useEntity", (player, level, hand, entity, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            teamClick(entity);
            return pigClick(entity);
        });
        Safe.attackEntity("EventExtras.attackEntity", (player, level, hand, entity, hit) -> {
            if (level.isClientSide()) teamClick(entity);
            return InteractionResult.PASS;
        });
        Safe.allowCommand("EventExtras.onCommand", EventExtras::onCommand);
        ChatBus.register(ChatBus.OBSERVE + 21, "event_extras", null, EventExtras::onChat);
        Safe.endTick("EventExtras.tick", EventExtras::tick);
    }
}
