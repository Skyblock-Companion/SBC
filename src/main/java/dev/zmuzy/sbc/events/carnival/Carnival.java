// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.carnival;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.combat.Combat;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.events.EventData;
import dev.zmuzy.sbc.events.EventPack;
import dev.zmuzy.sbc.events.Events;
import dev.zmuzy.sbc.events.EventSheet;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// carnival stuff around its games. from skyhanni CarnivalAPI (skyblocker ChivalrousCarnival) for when its on,
// CarnivalGoal for whats left, CarnivalReminder for daily tickets, CarnivalQuickStart for starting a game
// with a 2nd click
public final class Carnival {
    public static final String GOALS = "carnival_goals", TICKETS = "carnival_tickets", QUICK = "carnival_quick_start";
    private static long nextCheck;
    private static boolean claimedToday;
    private static long lastChat, lastClicked;

    private Carnival() {}

    // Perk.CHIVALROUS_CARNIVAL.isActive
    public static boolean active() {
        if (SbcMod.DEV) return true;
        for (Mayor.Perk p : Mayor.perks) if (p.name().startsWith("Chivalrous Carnival")) return true;
        return false;
    }

    // CarnivalAPI.inCarnivalArea, by the sidebar area like skyblocker reads it
    public static boolean inArea() {
        return SbcMod.DEV || "HUB".equals(GameState.island) && active() && GameState.area.equals("Carnival");
    }

    // --- CarnivalGoal ---

    enum Game {
        FRUIT_DIGGING("Fruit Digging", "sand", 0xFFFFAA00), CATCH_A_FISH("Catch a Fish", "cod", 0xFF00AAAA), ZOMBIE_SHOOTOUT("Zombie Shootout", "arrow", 0xFFFF5555);

        final String shown, icon;
        final int color;

        Game(String shown, String icon, int color) {
            this.shown = shown;
            this.icon = icon;
            this.color = color;
        }
    }

    enum Goal {
        FRUIT_DIGGING_PLAY(Game.FRUIT_DIGGING, "fruit.digging.play", "Play 3 games"), FRUIT_DIGGING_SCORE(Game.FRUIT_DIGGING, "fruit.digging.score", "Reach 3,000 score"), DIG_APPLE(Game.FRUIT_DIGGING, "dig.apple", "Dig up 3 Apples"),
        UNIQUE_FRUIT(Game.FRUIT_DIGGING, "unique.fruit", "Dig up 5 unique Fruits"), DRAGONFRUIT(Game.FRUIT_DIGGING, "dragonfruit", "Dig up a Dragonfruit"),
        CATCH_A_FISH_PLAY(Game.CATCH_A_FISH, "catch.a.fish.play", "Play 3 games"), CATCH_A_FISH_SCORE(Game.CATCH_A_FISH, "catch.a.fish.score", "Reach 3,000 score"), CATCH_FISH(Game.CATCH_A_FISH, "catch.fish", "Catch 30 Fish"),
        CATCH_YELLOW_FISH(Game.CATCH_A_FISH, "catch.yellow.fish", "Catch 5 Yellow Fish"), CATCH_STREAK(Game.CATCH_A_FISH, "catch.streak", "Reach a 5 Catch Streak"),
        ZOMBIE_SHOOTOUT_PLAY(Game.ZOMBIE_SHOOTOUT, "zombie.shootout.play", "Play 3 games"), ZOMBIE_SHOOTOUT_SCORE(Game.ZOMBIE_SHOOTOUT, "zombie.shootout.score", "Reach 3,000 score"), SHOOT_ZOMBIE(Game.ZOMBIE_SHOOTOUT, "shoot.zombie", "Shoot 60 Zombies"),
        SHOOT_DIAMOND_ZOMBIE(Game.ZOMBIE_SHOOTOUT, "shoot.diamond.zombie", "Shoot 5 Diamond Zombies"), SHOOT_LAMPS(Game.ZOMBIE_SHOOTOUT, "shoot.lamps", "Shoot 5 Redstone Lamps");

        final Game game;
        final String key, display;

        Goal(Game game, String key, String display) {
            this.game = game;
            this.key = key;
            this.display = display;
        }

        Pattern lore() {
            return EventPack.plain("SH.event.carnival.goals.lore." + key);
        }

        Pattern chat() {
            return EventPack.plain("SH.event.carnival.goals.chat." + key);
        }
    }

    // getEntry: the game's item picks the group, score goals read the same in all 3
    static Goal goalOf(String model, String firstLore) {
        for (Goal g : Goal.values()) if (g.game.icon.equals(model) && g.lore().matcher(firstLore).matches()) return g;
        return null;
    }

    // isReached: new skyblock year = new goals
    private static EventData.ProfileEvents goalYear() {
        EventData.ProfileEvents p = EventData.profile();
        int year = SbTime.sbDate(System.currentTimeMillis()).year();
        if (p.carnivalYear != year) {
            p.carnivalGoals.clear();
            p.carnivalYear = year;
        }
        return p;
    }

    // onInventoryFullyOpened
    static void readGoals(MenuView v, java.util.Set<String> reached) {
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.cleanLore.isEmpty()) continue;
            Goal goal = goalOf(it.model, it.cleanLore.get(0));
            if (goal == null) continue;
            if (EventPack.plain("SH.event.carnival.goals.complete").matcher(it.lastClean()).matches()) reached.add(goal.name());
            else reached.remove(goal.name());
        }
    }

    // stripped chat line that says a goal is reached
    static Goal goalFromChat(String clean) {
        for (Goal g : Goal.values()) if (g.chat().matcher(clean).matches()) return g;
        return null;
    }

    static void goalLines(EventSheet s, java.util.Set<String> reached) {
        for (Game game : Game.values()) {
            List<Goal> left = new ArrayList<>();
            for (Goal g : Goal.values()) if (g.game == game && !reached.contains(g.name())) left.add(g);
            if (left.isEmpty()) continue;
            s.section(game.shown);
            for (Goal g : left) s.line(g.display, "", Modern.INK).labelColor(Modern.LABEL);
        }
    }

    private static final HudElement GOALS_CARD = new EventSheet.Card(GOALS, "Carnival goals", "Events", Zone.TOP_LEFT, 47, GOALS) {
        @Override
        public boolean visible() {
            return inArea() && goalYear().carnivalGoals.size() < Goal.values().length;
        }

        @Override
        protected void live(EventSheet s) {
            goalLines(s, goalYear().carnivalGoals);
        }

        @Override
        protected void sample(EventSheet s) {
            goalLines(s, java.util.Set.of("FRUIT_DIGGING_PLAY", "DIG_APPLE", "CATCH_A_FISH_PLAY", "CATCH_A_FISH_SCORE", "CATCH_FISH", "CATCH_YELLOW_FISH", "CATCH_STREAK", "ZOMBIE_SHOOTOUT_PLAY", "SHOOT_ZOMBIE", "SHOOT_LAMPS"));
        }

        @Override
        protected List<Row> plainRows() {
            List<Row> out = new ArrayList<>();
            java.util.Set<String> reached = goalYear().carnivalGoals;
            for (Goal g : Goal.values()) if (!reached.contains(g.name())) out.add(new Row(g.game.shown, g.display, 0));
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Reach 3,000 score"));
        }
    };

    // --- CarnivalReminder ---

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    // checkDate
    static boolean claimedOn(String claimedDay, String day) {
        return !claimedDay.isEmpty() && day.compareTo(claimedDay) <= 0;
    }

    // ReminderUtils.isBusy, as far as we can tell
    public static boolean busy() {
        return "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island) || "THE_RIFT".equals(GameState.island);
    }

    private static void ticketsTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (!active() || now < nextCheck) return;
        claimedToday = claimedOn(EventData.profile().ticketsClaimedDay, today());
        if (claimedToday || busy()) return;
        Combat.sayClickable("Carnival Tickets are ready to be claimed! §eClick to warp to the Carnival.", "/warp carnival");
        nextCheck = now + 5 * 60_000;
    }

    // --- CarnivalQuickStart ---

    // HypixelCommands.npcOption first arg for this npc, or null
    static String quickStartNpc(String name) {
        if (Patterns.get("SH.carnival.npcs.cowboy").matcher(name).matches()) return "carnival_cowboy";
        if (Patterns.get("SH.carnival.npcs.fisher").matcher(name).matches()) return "carnival_fisherman";
        if (Patterns.get("SH.carnival.npcs.pirate").matcher(name).matches()) return "carnival_pirateman";
        return null;
    }

    private static String npcNameNear(Entity e) {
        if (e.level() == null) return "";
        for (ArmorStand a : e.level().getEntitiesOfClass(ArmorStand.class, e.getBoundingBox().inflate(0.5, 3, 0.5), ArmorStand::hasCustomName)) {
            String name = GameState.strip(a.getCustomName().getString()).trim();
            if (quickStartNpc(name) != null) return name;
        }
        return GameState.strip(e.getName().getString()).trim();
    }

    private static boolean quickStart(Entity entity) {
        long now = System.currentTimeMillis();
        if (!Features.on(QUICK) || !inArea() || now - lastChat > 5_000) return false;
        String npc = quickStartNpc(npcNameNear(entity));
        if (npc == null) return false;
        if (now - lastClicked < 1_000) return true;
        lastClicked = now;
        var c = Minecraft.getInstance().getConnection();
        if (c != null) c.sendCommand("selectnpcoption " + npc + " r_2_1");
        return true;
    }

    public static void register() {
        Features.add(new Feature(GOALS, "Events", "Carnival goals", "At the Carnival, the goals you have left this year, by game. Open the Carnival Goals menu once to read the ones you already have.", "SkyHanni: Show Goals").when(Carnival::inArea));
        Features.add(new Feature(TICKETS, "Events", "Carnival Ticket reminder", "While the Carnival is on, a click in chat to go there when your daily tickets are ready.", "SkyHanni: Reminder Daily Tickets")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .tick(Carnival::ticketsTick));
        Features.add(new Feature(QUICK, "Events", "Start Carnival games with a second click", "Click a game's NPC again after it has finished talking to start the game. This answers the NPC for you when you click.", "SkyHanni: Double Click to Start").when(Carnival::inArea).defaultOff());
        MenuMarks.add(GOALS, Patterns.get("SH.event.carnival.goals.inventory"), v -> {
            readGoals(v, goalYear().carnivalGoals);
            Store.markDirty();
        });
        ChatBus.register(ChatBus.OBSERVE + 26, "carnival", null, line -> {
            if (!GameState.inSkyBlock) return ChatBus.PASS;
            String clean = GameState.strip(line.plain());
            if (Features.on(GOALS) && inArea()) {
                Goal g = goalFromChat(clean);
                if (g != null && goalYear().carnivalGoals.add(g.name())) Store.markDirty();
            }
            if (Events.TICKETS.matcher(clean).matches() || Events.TICKETS_DONE.matcher(clean).matches()) {
                EventData.profile().ticketsClaimedDay = today();
                claimedToday = true;
                Store.markDirty();
            }
            if (Patterns.get("SH.carnival.select.option.chat-nocolor").matcher(clean).matches()) lastChat = System.currentTimeMillis();
            return ChatBus.PASS;
        });
        Safe.useEntity("Carnival.useEntity", (player, level, hand, entity, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            try {
                return quickStart(entity) ? InteractionResult.FAIL : InteractionResult.PASS;
            } catch (Throwable t) {
                return InteractionResult.PASS;
            }
        });
        HudLayout.add(GOALS_CARD);
        FruitDigging.options(Features.get(FruitDigging.ID));
        FruitDigging.register();
        ZombieShootout.register();
        CarnivalShop.register();
    }
}
