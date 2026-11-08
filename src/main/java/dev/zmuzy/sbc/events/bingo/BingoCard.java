// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.bingo;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.combat.Combat;
import dev.zmuzy.sbc.core.CookieBuff;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.data.BingoData;
import dev.zmuzy.sbc.events.EventData;
import dev.zmuzy.sbc.events.EventPack;
import dev.zmuzy.sbc.events.EventSheet;
import dev.zmuzy.sbc.events.Events;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// bingo card on the hud + helpers in its menu. from skyhanni BingoCardReader, BingoCardDisplay, BingoCardTips
// with BingoApi goals, GoalType, HiddenGoalData
// skyhanni lets you pick highlighted goals on its overlay, here thats a panel next to the chest. goal lines
// stay as long as the game writes them
public final class BingoCard {
    public static final String ID = "bingo_card";
    private static final int MAX_PERSONAL_GOALS = 20;
    private static final String HINT_UNLOCKED = "Open the card to read it";
    private static final Panel PANEL = new Panel();

    // displayMode: 0 card, 1 steps, 2 nothing
    private static int displayMode;
    private static boolean lastSneak;

    private BingoCard() {}

    // SkyBlockUtils.isBingoProfile: hypixel's profile type or the sidebar's Bingo line
    public static boolean onBingo() {
        return SbcMod.DEV || GameState.profileType != null && GameState.profileType.toLowerCase(Locale.ROOT).contains("bingo") || Events.bingo();
    }

    static EventData.BingoSession session() {
        return EventData.get().bingo.computeIfAbsent(BingoData.startOfMonth(LocalDate.now(ZoneOffset.UTC)) / 1000, k -> new EventData.BingoSession());
    }

    private static boolean opt(String key) {
        return Combat.opt(ID, key);
    }

    // BingoApi.getCommunityPercentageColor as a colour
    static int percentColor(double percentage) {
        if (percentage < 0.01) return Modern.GOOD;
        if (percentage < 0.05) return 0xFFFFFF55;
        if (percentage < 0.25) return 0xFFFFAA00;
        return Modern.BAD;
    }

    static String percent(double fraction) {
        double p = fraction * 100;
        return (p == Math.rint(p) ? String.valueOf((long) p) : String.format(Locale.ROOT, "%.2f", p)) + "%";
    }

    // BingoCardReader.readCommunityGoalPercentage
    static double communityPercentage(List<String> lore) {
        for (String line : lore) {
            Matcher m = EventPack.plain("SH.bingo.card.percentage").matcher(GameState.strip(line));
            if (m.matches()) return Double.parseDouble(m.group("percentage")) / 100;
        }
        return -1;
    }

    // goal text: lore line 3 up to the first empty one
    static String description(List<String> lore) {
        StringBuilder out = new StringBuilder();
        for (int i = 2; i < lore.size(); i++) {
            String s = lore.get(i);
            if (s.isEmpty()) break;
            out.append(s).append(' ');
        }
        String d = out.toString();
        if (d.endsWith(" ")) d = d.substring(0, d.length() - 1);
        if (d.startsWith("§7§7")) d = d.substring(2);
        return d;
    }

    // BingoCardReader.getHiddenGoalData: whats still unknown, when the next hint comes, and what the repo tip says instead
    static void hiddenData(EventData.Goal goal, String original) {
        boolean unknown = false;
        long next = -1;
        if (goal.community) {
            unknown = GameState.strip(original).equals("This goal will be revealed when it hits Tier IV.");
        } else {
            Matcher m = EventPack.plain("SH.bingo.card.hiddengoal").matcher(GameState.strip(original));
            if (m.matches()) {
                unknown = true;
                next = CookieBuff.parse(m.group("time"));
            }
        }
        BingoData.Tip tip = BingoData.tip(goal.displayName);
        if (tip != null) {
            goal.description = "§7" + String.join(" ", tip.note());
            unknown = false;
        } else {
            goal.description = original;
        }
        goal.unknownTip = unknown;
        goal.nextHintIn = next;
    }

    // onInventoryUpdated
    static void read(MenuView v, EventData.BingoSession session, boolean report) {
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            boolean personal = false, community = false;
            for (String l : it.lore) {
                if (l.endsWith("Personal Goal")) personal = true;
                else if (l.endsWith("Community Goal")) community = true;
            }
            if (!personal && !community) continue;
            EventData.Goal goal = session.goals.computeIfAbsent(slot, k -> new EventData.Goal());
            goal.community = !personal;
            goal.displayName = it.clean;
            BingoData.Tip tip = BingoData.tip(it.clean);
            goal.guide = new ArrayList<>();
            if (tip != null) for (String g : tip.guide()) goal.guide.add("§7" + g);
            else goal.guide.add("§cNo guide yet!");
            goal.done = it.loreHas("GOAL REACHED");
            hiddenData(goal, description(it.lore));
            double pct = communityPercentage(it.lore);
            if (pct >= 0) {
                if (report) difference(goal, pct);
                goal.communityPercentage = pct;
            }
        }
        session.lastCardOpen = System.currentTimeMillis();
    }

    // bingoGoalDifference: from a percent you never saw, 100%
    private static void difference(EventData.Goal goal, double now) {
        double old = goal.communityPercentage < 0 ? 1.0 : goal.communityPercentage;
        if (now == old) return;
        String color = now > old ? "§c" : "§a";
        Combat.say(color + goal.displayName + ": §f" + percent(old) + " §b-> §f" + percent(now));
    }

    private static void mark(MenuView v) {
        read(v, session(), opt("community_progress"));
        Store.markDirty();
        BingoSteps.dirty();
        tips(v, session());
    }

    // difficulty colour behind the goal
    private static int difficultyColor(String difficulty) {
        return switch (difficulty.toUpperCase(Locale.ROOT)) {
            case "EASY" -> 0x7855FF55;
            case "MEDIUM" -> 0x78FFFF55;
            case "HARD" -> 0x78FF5555;
            default -> 0x78AAAAAA;
        };
    }

    private static String difficultyName(String difficulty) {
        return switch (difficulty.toUpperCase(Locale.ROOT)) {
            case "EASY" -> "§aEasy";
            case "MEDIUM" -> "§eMedium";
            case "HARD" -> "§cHard";
            default -> "§7" + difficulty;
        };
    }

    // BingoCardTips.onToolTip: goal lines with difficulty after the name and the guide above the reward. null = leave it
    static List<String> tooltip(MenuView.Item it, EventData.Goal goal, BingoData.Tip tip) {
        if (tip == null) return null;
        if (Patterns.get("SH.bingo.card.tips.new.row.name").matcher(it.clean).matches()) return null;
        List<String> out = new ArrayList<>();
        out.add(it.name + " §7(" + difficultyName(tip.difficulty()) + "§7)");
        out.addAll(it.lore);
        var pattern = Patterns.get(goal.community ? "SH.bingo.card.tips.new.reward.contribution" : "SH.bingo.card.tips.new.reward");
        int at = -1;
        for (int i = 0; i < it.cleanLore.size(); i++) {
            if (pattern.matcher(it.cleanLore.get(i)).matches()) {
                at = i + 1 - 2;
                break;
            }
        }
        if (at < 0) return null;
        out.add(++at, "");
        out.add(++at, "§eGuide:");
        for (String line : tip.guide()) out.add(++at, " " + line);
        if (tip.found() != null) out.add(++at, "§7Found by: §e" + tip.found());
        return out;
    }

    // BingoCardTips.onBackgroundDrawn
    private static void tips(MenuView v, EventData.BingoSession session) {
        if (!opt("guide")) return;
        for (Map.Entry<Integer, EventData.Goal> e : session.goals.entrySet()) {
            int slot = e.getKey();
            if (slot >= v.top) continue;
            EventData.Goal goal = e.getValue();
            BingoData.Tip tip = goal.community ? BingoData.tip(goal.displayName) : BingoData.tipExact(goal.displayName);
            if (!(opt("hide_done_difficulty") && goal.done)) v.back(slot, tip == null ? 0x78AAAAAA : difficultyColor(tip.difficulty()));
            List<String> lines = tooltip(v.item(slot), goal, tip);
            if (lines != null) v.mark(slot).tip = lines;
        }
    }

    // BingoCardReader.onChat
    static boolean goalComplete(String message, EventData.BingoSession session) {
        Matcher m = EventPack.plain("SH.bingo.card.goalcomplete").matcher(GameState.strip(message));
        if (!m.matches()) return false;
        String name = m.group("name");
        for (EventData.Goal g : session.goals.values()) {
            if (g.community || !g.displayName.equals(name)) continue;
            g.done = true;
            return true;
        }
        return false;
    }

    private static List<EventData.Goal> goals(EventData.BingoSession s, boolean community) {
        List<EventData.Goal> out = new ArrayList<>();
        for (EventData.Goal g : s.goals.values()) if (g.community == community) out.add(g);
        return out;
    }

    // addGoals: only highlighted ones while any are, + how many that leaves out
    private static void goalLines(EventSheet s, List<EventData.Goal> goals) {
        boolean filter = false;
        for (EventData.Goal g : goals) if (g.highlight) filter = true;
        int left = 0;
        for (EventData.Goal g : goals) {
            if (filter && !g.highlight) {
                left++;
                continue;
            }
            EventSheet.Line l = s.line(GameState.strip(g.description), g.communityPercentage >= 0 ? percent(g.communityPercentage) : "", g.communityPercentage >= 0 ? percentColor(g.communityPercentage) : Modern.INK);
            l.labelColor(Modern.LABEL);
        }
        if (left > 0) s.line("Not highlighted", String.valueOf(left), Modern.DIM);
    }

    // drawDisplay
    static void fill(EventSheet s, List<EventData.Goal> community, List<EventData.Goal> personal, boolean showCommunity, boolean nextTip, long nextHintAt, long now) {
        if (community.isEmpty() && personal.isEmpty()) {
            s.line("Open the card with /bingo", "", Modern.WARN);
            return;
        }
        if (showCommunity) {
            List<EventData.Goal> known = new ArrayList<>();
            int hidden = 0;
            for (EventData.Goal g : community) {
                if (g.unknownTip) hidden++;
                else known.add(g);
            }
            s.section("Community goals");
            goalLines(s, known);
            if (hidden > 0) s.line("More unknown", String.valueOf(hidden), Modern.BAD);
        }
        List<EventData.Goal> todo = new ArrayList<>();
        int hidden = 0;
        for (EventData.Goal g : personal) {
            if (g.done) continue;
            if (g.unknownTip) hidden++;
            else todo.add(g);
        }
        int done = MAX_PERSONAL_GOALS - todo.size() - hidden;
        s.section("Personal goals", done + "/" + MAX_PERSONAL_GOALS);
        goalLines(s, todo);
        if (hidden > 0) s.line("More unknown", String.valueOf(hidden), Modern.BAD);
        if (nextTip && nextHintAt > 0) {
            if (nextHintAt <= now) s.line("Next hint", HINT_UNLOCKED, Modern.WARN);
            else s.line("Next hint", SbTime.dur(nextHintAt - now), Modern.AQUA);
        }
    }

    private static long nextHintAt(EventData.BingoSession s) {
        long next = -1;
        for (EventData.Goal g : s.goals.values()) {
            if (g.community || g.done || !g.unknownTip || g.nextHintIn < 0) continue;
            if (next < 0 || g.nextHintIn < next) next = g.nextHintIn;
        }
        return next < 0 ? 0 : s.lastCardOpen + next;
    }

    private static EventData.Goal goal(boolean community, String description, double percentage, boolean unknown, boolean done) {
        EventData.Goal g = new EventData.Goal();
        g.community = community;
        g.description = description;
        g.communityPercentage = percentage;
        g.unknownTip = unknown;
        g.done = done;
        return g;
    }

    private static final HudElement CARD = new EventSheet.Card(ID, "Bingo Card", "Events", Zone.TOP_LEFT, 66, ID) {
        @Override
        public boolean visible() {
            if (!onBingo() || displayMode == 2) return false;
            Minecraft mc = Minecraft.getInstance();
            return displayMode == 1 || !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
        }

        @Override
        protected void live(EventSheet s) {
            if (displayMode == 1 && opt("step_helper")) {
                BingoSteps.fill(s);
                return;
            }
            EventData.BingoSession b = session();
            fill(s, goals(b, true), goals(b, false), !opt("hide_community"), opt("next_tip"), nextHintAt(b), System.currentTimeMillis());
        }

        @Override
        protected void sample(EventSheet s) {
            fill(s, List.of(goal(true, "Mine Ore Blocks", 0.04, false, false), goal(true, "Summon Sea Creatures", 0.12, false, false), goal(true, "", -1, true, false)),
                    List.of(goal(false, "Reach 1,000 Ink Sac Collection.", -1, false, false), goal(false, "Obtain level 10 in the Foraging Skill.", -1, false, false), goal(false, "", -1, true, false)), true, true, 3_600_000, 0);
        }

        @Override
        protected List<Row> plainRows() {
            EventData.BingoSession b = session();
            List<Row> out = new ArrayList<>();
            int left = 0;
            for (EventData.Goal g : goals(b, false)) if (!g.done) left++;
            out.add(new Row("Personal goals", (MAX_PERSONAL_GOALS - left) + "/" + MAX_PERSONAL_GOALS, Theme.current().text()));
            if (!opt("hide_community")) for (EventData.Goal g : goals(b, true)) if (!g.unknownTip) out.add(new Row(GameState.strip(g.description), g.communityPercentage >= 0 ? percent(g.communityPercentage) : "", percentColor(g.communityPercentage)));
            for (EventData.Goal g : goals(b, false)) if (!g.done && !g.unknownTip) out.add(new Row(GameState.strip(g.description)));
            return out;
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Personal goals", "17/20", Theme.current().text()));
        }
    };

    // toggleCommand
    private static int toggleCommand() {
        if (!onBingo()) Combat.say("§cThis command only works on a Bingo profile.");
        else if (!Features.on(ID)) Combat.say("§cThe Bingo Card is switched off in the settings.");
        else {
            toggleMode();
            Combat.say("Bingo Card visibility toggled.");
        }
        return 1;
    }

    private static void toggleMode() {
        displayMode = (displayMode + 1) % 3;
    }

    // renderDisplay's quick toggle: sneak with the skyblock menu in hand
    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (!opt("step_helper") && displayMode == 1) displayMode = 2;
        if (!opt("quick_toggle") || !"SKYBLOCK_MENU".equals(Gain.sbId(mc.player.getMainHandItem()))) return;
        boolean sneaking = mc.player.isShiftKeyDown();
        if (sneaking != lastSneak) {
            lastSneak = sneaking;
            if (sneaking) toggleMode();
        }
    }

    // what skyhanni's overlay does while the menu is open: every goal with its guide, click to highlight
    private static final class Panel implements SideHelper.Content {
        private List<EventData.Goal> shown = List.of();

        @Override
        public String title() {
            return "Bingo goals";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("paper");
        }

        @Override
        public int accent() {
            return 0xFFFFC14D;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Goals " + shown.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            List<EventData.Goal> all = new ArrayList<>();
            for (EventData.Goal g : session().goals.values()) if (!g.done && !g.unknownTip) all.add(g);
            shown = all;
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            ui.textShadow("§7Click a goal to highlight it.", x, y, Modern.DIM);
            return 11;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (EventData.Goal g : shown) {
                boolean hover = ui.hover(x, y, w, 21);
                if (hover) ui.rect(x, y, w, 21, 0x22FFFFFF);
                ui.textShadow(ui.ellipsize((g.highlight ? "§e" : "§f") + g.displayName, w - 4), x + 2, y + 1, Modern.INK);
                ui.textShadow(ui.ellipsize("§7" + GameState.strip(g.description), w - 4), x + 2, y + 11, Modern.LABEL);
                ui.click(x, y, w, 21, () -> {
                    g.highlight = !g.highlight;
                    Store.markDirty();
                });
                y += 22;
            }
            if (shown.isEmpty()) y += SideHelper.emptyText(ui, "No goals left to show.", x, y, w);
            return y - y0;
        }
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID) || s == null || !GameState.strip(s.getTitle().getString()).trim().equals("Bingo Card")) return null;
        return PANEL;
    }

    public static void register() {
        Features.add(new Feature(ID, "Events", "Bingo Card", "On a Bingo profile, your goals on the HUD, with the community percentages and when the next hint comes. In the card's menu, each goal's difficulty and guide. Open the menu once to fill it.", "SkyHanni: Bingo Card")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .tick(BingoCard::tick)
                .option(Option.bool("bingo_card.hide_community", "Hide community goals", "", false))
                .option(Option.bool("bingo_card.next_tip", "Time until the next hint", "", true))
                .option(Option.bool("bingo_card.quick_toggle", "Quick toggle", "Sneak with the SkyBlock Menu in hand to show or hide the card.", true))
                .option(Option.bool("bingo_card.step_helper", "Next steps", "The quick toggle also shows what to do next for your goals. Not every goal is known.", false))
                .option(Option.bool("bingo_card.guide", "Difficulty and guide in the menu", "", true))
                .option(Option.bool("bingo_card.hide_done_difficulty", "No difficulty colour when done", "", true))
                .option(Option.bool("bingo_card.community_progress", "Community percentages in chat", "What changed since you last opened the card.", true)));
        MenuMarks.add(ID, "Bingo Card", BingoCard::mark);
        SideHelper.add(BingoCard::panel);
        ChatBus.register(ChatBus.OBSERVE + 23, "bingo card", null, line -> {
            if (!Features.on(ID) || !GameState.inSkyBlock || !onBingo()) return ChatBus.PASS;
            String message = LegacyText.of(line.component());
            if (goalComplete(message, session())) {
                Store.markDirty();
                BingoSteps.dirty();
            }
            BingoSteps.chat(GameState.strip(line.plain()));
            return ChatBus.PASS;
        });
        HudLayout.add(CARD);
        Safe.commands("BingoCard.commands", (d, r) -> d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("sbc")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("bingotoggle").executes(c -> toggleCommand()))
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("bingoupdate").executes(c -> {
                    BingoData.refresh();
                    Combat.say("Asking for this month's Bingo times.");
                    return 1;
                }))
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("bingoreload").executes(c -> {
                    session().goals.clear();
                    Store.markDirty();
                    Combat.say("Bingo goals cleared. Open the card to read them again.");
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("bingotoggle", "Show the Bingo Card, the next steps, or nothing");
        dev.zmuzy.sbc.core.Commands.describe("bingoupdate", "Ask Hypixel for this month's Bingo times again");
        dev.zmuzy.sbc.core.Commands.describe("bingoreload", "Forget the Bingo goals read so far");
    }
}
