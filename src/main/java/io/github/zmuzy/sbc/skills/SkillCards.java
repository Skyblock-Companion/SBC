// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.skills;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.core.game.GameProfile;
import io.github.zmuzy.sbc.core.game.Seen;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni skillprogress/SkillProgress.kt, SkillTooltip.kt + the api/SkillApi.kt command, on what
// core/game/GameRead already reads of your skills: the eta card with its pause, tooltips, level up lines
public final class SkillCards {
    public static final String ETA = "skill_eta", ALL = "skill_all", GOALS = "skill_goals", OVERFLOW = "skill_overflow";
    // skyhannis AllSkillDisplayConfig list + hunting, which is new
    private static final List<String> ORDER = List.of("Combat", "Farming", "Fishing", "Mining", "Foraging", "Enchanting", "Alchemy", "Carpentry", "Taming", "Hunting");
    private static final Map<String, String> ICONS = Map.of("Combat", "minecraft:golden_sword", "Farming", "minecraft:golden_hoe", "Fishing", "minecraft:fishing_rod", "Mining", "minecraft:golden_pickaxe",
            "Foraging", "minecraft:golden_axe", "Enchanting", "minecraft:enchanting_table", "Alchemy", "minecraft:brewing_stand", "Carpentry", "minecraft:crafting_table", "Taming", "minecraft:polar_bear_spawn_egg", "Hunting", "minecraft:lead");
    // stays on the card a bit after the last xp, a paused skill is still worth reading
    private static final long ETA_STAYS = 300_000;
    private static final long ALL_STAYS = 60_000;

    private static final Map<String, Integer> lastLevel = new HashMap<>();
    private SkillCards() {}

    private static boolean option(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(feature + "." + key);
        return o == null ? def : o.bool();
    }

    // from skyhanni SkillETADisplayConfig: how long without xp still counts as doing it. the settings value, read
    // whatever the cards switch says
    public static int pauseSeconds(String skill) {
        String key = switch (skill) {
            case "Farming" -> "pause_farming";
            case "Mining" -> "pause_mining";
            case "Combat" -> "pause_combat";
            case "Foraging" -> "pause_foraging";
            case "Fishing" -> "pause_fishing";
            default -> "";
        };
        int def = switch (skill) {
            case "Combat" -> 30;
            case "Fishing" -> 15;
            default -> 3;
        };
        if (key.isEmpty()) return def;
        Feature f = Features.get(ETA);
        Option o = f == null ? null : f.option(ETA + "." + key);
        return o == null ? def : o.integer();
    }

    private static int cap(String skill) {
        return GameProfile.skillCap(skill);
    }

    private static int goalOf(Seen s, String skill) {
        Integer g = s == null ? null : s.skillGoals.get(skill);
        return g == null ? 0 : g;
    }

    private static int activeGoal(Seen s, String skill, Seen.Skill info) {
        int goal = goalOf(s, skill);
        return goal > 0 && info != null && goal > SkillLevels.overflowLevel(info, cap(skill)) ? goal : 0;
    }

    // SkillElement numbers with your goal or levels past the cap in them: skill, level, percent, xp, xp needed
    public static Object[] forBar(Object[] base) {
        String skill = (String) base[0];
        Seen s = GameProfile.current();
        Seen.Skill info = s == null ? null : s.skills.get(skill);
        if (info == null) return base;
        int goal = activeGoal(s, skill, info);
        if (goal > 0 && Features.on(GOALS) && option(GOALS, "bar", true)) {
            long have = SkillLevels.total(info), need = SkillLevels.xpRequiredForLevel(goal);
            return new Object[]{skill, base[1], SkillLevels.goalPercent(have, goal), (double) have, (double) need};
        }
        if (info.max && Features.on(OVERFLOW) && option(OVERFLOW, "cards", true)) {
            SkillLevels.Level l = SkillLevels.calculate(SkillLevels.total(info), cap(skill));
            if (l.forNext() > 0 && l.level() >= cap(skill)) return new Object[]{skill, l.level(), 100.0 * l.current() / l.forNext(), (double) l.current(), (double) l.forNext()};
        }
        return base;
    }

    private static String num(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    private static String brief(double v) {
        return Combat.shortNumber((long) v);
    }

    // eta card numbers, split from where they come from (skyhanni drawETADisplay)
    record Eta(String skill, int level, int target, long remaining, boolean goal, double perHour, boolean active, long session, boolean sessionRunning, boolean locked) {
        // "In": whats left at this pace, or why we cant say
        String in() {
            if (locked) return "§cIncrease level cap!";
            if (perHour < 1000) return "§cN/A";
            return "§b" + SbTime.dur((long) (remaining * 3_600_000.0 / perHour)) + (active ? "" : " §c(PAUSED)");
        }

        String xpPerHour() {
            if (perHour <= 0) return "§cN/A";
            return "§e" + num((long) perHour) + (active ? "" : " §c(PAUSED)");
        }

        String sessionText() {
            return "§e" + SbTime.dur(session * 1000) + (sessionRunning ? "" : " §c(PAUSED)");
        }
    }

    private static Eta eta() {
        String skill = Vitals.skill;
        if (!GameState.REAL_SKILLS.contains(skill)) return null;
        Seen s = GameProfile.current();
        Seen.Skill info = s == null ? null : s.skills.get(skill);
        if (info == null) return null;
        int cap = cap(skill), overflow = SkillLevels.overflowLevel(info, cap);
        boolean past = info.max && Features.on(OVERFLOW) && option(OVERFLOW, "cards", true);
        int goal = option(GOALS, "eta", true) && Features.on(GOALS) ? activeGoal(s, skill, info) : 0;
        int level = goal > 0 || past ? overflow : info.level;
        int target = goal > 0 ? goal : level + 1;
        long total = SkillLevels.total(info);
        long remaining = Math.max(0, SkillLevels.xpRequiredForLevel(target) - total);
        // "have > need": at the cap the bar keeps counting and theres nothing to level till the cap goes up
        boolean locked = info.max && !past && goal == 0;
        return new Eta(skill, level, target, remaining, goal > 0, Vitals.lastXpPerHour(), Vitals.xpActive(), Vitals.sessionSeconds(), Vitals.sessionRunning(), locked);
    }

    private static List<RichCard.Line> etaLines(Eta e) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Skill", "§a" + e.skill() + " §7" + e.level() + "➜§b" + e.target()).raw().key("Skill"));
        if (e.goal()) out.add(RichCard.Line.row("Needed XP", "§e" + num(e.remaining())).raw().key("Needed XP").brief("Needed"));
        out.add(RichCard.Line.row("In", e.in()).raw().key("In"));
        out.add(RichCard.Line.row("XP/h", e.xpPerHour()).raw().key("XP/h"));
        out.add(RichCard.Line.row("Session", e.sessionText()).raw().key("Session"));
        return out;
    }

    private static final HudElement ETA_CARD = new RichCard(ETA, "Skill ETA", "HUD", Zone.TOP_LEFT, 57, ETA) {
        @Override public boolean visible() {
            return !Vitals.skill.isEmpty() && System.currentTimeMillis() - Vitals.skillAt < ETA_STAYS && eta() != null;
        }

        @Override protected Content live() {
            Eta e = eta();
            return new Content(e == null || !e.active() ? "§cPaused" : "", e == null ? List.of() : etaLines(e));
        }

        private Eta example() {
            return new Eta("Farming", 55, 60, 18_420_000, true, 3_100_000, true, 5_040, true, false);
        }

        @Override protected Content sample() {
            return new Content("", etaLines(example()));
        }

        @Override protected List<Row> classicRows() {
            Eta e = eta();
            return e == null ? List.of() : List.of(new Row(e.skill(), e.level() + "➜" + e.target(), Theme.current().text()), new Row("In", GameState.strip(e.in()), Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Farming", "55➜60", Theme.current().text()), new Row("In", "5h 56m", Theme.current().text()));
        }
    };

    // one skill row: level, xp out of needed, how far that is
    private static RichCard.Line skillRow(Seen s, String skill, Seen.Skill info, boolean active) {
        String name = (active ? "§e" : "§a") + skill;
        String icon = ICONS.get(skill);
        if (info == null) return RichCard.Line.row(name, "§7-").raw().icon(icon).key(skill);
        int cap = cap(skill);
        int goal = option(GOALS, "all", true) && Features.on(GOALS) ? activeGoal(s, skill, info) : 0;
        long have = SkillLevels.total(info);
        if (goal > 0) {
            long need = SkillLevels.xpRequiredForLevel(goal);
            return RichCard.Line.row(name + " §f" + info.level + "§7➜§b" + goal, "§b" + brief(have) + "§6/§b" + brief(need)).raw().icon(icon).key(skill).meter(SkillLevels.goalPercent(have, goal) / 100.0);
        }
        if (info.max) {
            if (Features.on(OVERFLOW) && option(OVERFLOW, "cards", true)) {
                SkillLevels.Level l = SkillLevels.calculate(have, cap);
                if (l.forNext() > 0 && l.level() >= cap) return RichCard.Line.row(name + " §f" + l.level(), "§b" + brief(l.current()) + "§6/§b" + brief(l.forNext())).raw().icon(icon).key(skill).meter((double) l.current() / l.forNext());
            }
            return RichCard.Line.row(name + " §f" + info.level, "§6MAX").raw().icon(icon).key(skill).meter(1.0);
        }
        long next = info.currentXpMax > 0 ? info.currentXpMax : SkillLevels.xpRequiredForLevel(info.level + 1) - SkillLevels.xpRequiredForLevel(info.level);
        long current = info.percent >= 0 ? Math.round(next * info.percent / 100) : info.currentXp;
        return RichCard.Line.row(name + " §f" + info.level, "§b" + brief(current) + "§6/§b" + brief(next)).raw().icon(icon).key(skill).meter(next <= 0 ? 0 : (double) current / next);
    }

    private static final HudElement ALL_CARD = new RichCard(ALL, "All skills", "HUD", Zone.TOP_LEFT, 58, ALL) {
        @Override public boolean visible() {
            Feature f = Features.get(ALL);
            if (f != null && f.option("skill_all.when").choice().equals("Always")) return true;
            return !Vitals.skill.isEmpty() && System.currentTimeMillis() - Vitals.skillAt < ALL_STAYS;
        }

        @Override protected Content live() {
            Seen s = GameProfile.current();
            List<Line> out = new ArrayList<>();
            boolean known = false;
            for (String skill : ORDER) {
                Seen.Skill info = s == null ? null : s.skills.get(skill);
                known |= info != null;
                out.add(skillRow(s, skill, info, skill.equals(Vitals.skill)));
            }
            if (!known) return new Content("", List.of(Line.row("§cOpen your skills menu!", "/skills").raw().key("Hint")));
            return new Content("", out);
        }

        @Override protected Content sample() {
            Seen s = new Seen();
            List<Line> out = new ArrayList<>();
            Object[][] rows = {{"Combat", 50, 3_612_500L, 4_300_000L}, {"Farming", 55, 1_250_000L, 5_200_000L}, {"Mining", 42, 512_000L, 1_100_000L}};
            for (Object[] r : rows) {
                Seen.Skill i = new Seen.Skill();
                i.level = (Integer) r[1];
                i.currentXp = (Long) r[2];
                i.currentXpMax = (Long) r[3];
                out.add(skillRow(s, (String) r[0], i, r[0].equals("Farming")));
            }
            return new Content("", out);
        }

        @Override protected List<Row> classicRows() {
            Seen s = GameProfile.current();
            List<Row> out = new ArrayList<>();
            if (s != null) for (String skill : ORDER) {
                Seen.Skill i = s.skills.get(skill);
                if (i != null) out.add(new Row(skill, String.valueOf(i.level), Theme.current().text()));
            }
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Combat", "50", Theme.current().text()), new Row("Farming", "55", Theme.current().text()));
        }
    };

    // --- skyhanni SkillTooltip ---

    // from skyhanni StringUtils.progressBar, like the games skill menu draws one
    private static String progressBar(double done) {
        StringBuilder b = new StringBuilder("§2");
        boolean missing = false;
        int steps = 24;
        for (int i = 0; i <= steps; i++) {
            if (!missing && (double) i / steps > done) {
                b.append("§f");
                missing = true;
            }
            b.append("§l§m ");
        }
        return b.append("§r").toString();
    }

    // what goes under the skills own bar in Your Skills. pure for tests
    static void skillTooltip(List<Component> tip, String skill, long have, int goal, boolean goalLines, SkillLevels.Level overflow) {
        String bar = "                    ";
        boolean next = false;
        for (int i = 0; i < tip.size(); i++) {
            String line = tip.get(i).getString();
            if (overflow != null && line.contains("Max Skill level reached!")) {
                double progress = overflow.forNext() <= 0 ? 0 : (double) overflow.current() / overflow.forNext();
                tip.set(i, Component.literal("§7Progress to Level " + (overflow.level() + 1) + ": §e" + String.format(Locale.ROOT, "%.1f", progress * 100) + "%"));
                next = true;
                continue;
            }
            if (!line.contains(bar)) continue;
            if (next) {
                double progress = overflow.forNext() <= 0 ? 0 : (double) overflow.current() / overflow.forNext();
                tip.set(i, Component.literal(progressBar(progress) + " §e" + num(overflow.current()) + "§6/§e" + num(overflow.forNext())));
                tip.add(i + 1, Component.empty());
                tip.add(i + 2, Component.literal("§b§lOVERFLOW XP:"));
                tip.add(i + 3, Component.literal("§7▸ " + num(overflow.overflow())));
                i += 3;
                next = false;
            }
            if (goalLines) {
                long need = SkillLevels.xpRequiredForLevel(goal);
                double progress = need <= 0 ? 1 : Math.min(1, (double) have / need);
                tip.add(i + 1, Component.empty());
                tip.add(i + 2, Component.literal("§7Progress to Level " + goal + ": §e" + String.format(Locale.ROOT, "%.1f", progress * 100) + "%"));
                tip.add(i + 3, Component.literal(progressBar(progress) + " §e" + num(have) + "§6/§e" + num(need)));
                return;
            }
            if (overflow == null) return;
        }
    }

    private static void tooltip(ItemStack stack, List<Component> tip) {
        Minecraft mc = Minecraft.getInstance();
        var screen = mc.gui.screen();
        if (screen == null || !GameState.strip(screen.getTitle().getString()).trim().equals("Your Skills")) return;
        Seen s = GameProfile.current();
        if (s == null) return;
        String skill = GameState.strip(stack.getHoverName().getString()).trim().split(" ")[0];
        Seen.Skill info = s.skills.get(skill);
        if (info == null) return;
        boolean any = false;
        for (Component c : tip) any |= c.getString().contains("Click to view!");
        if (!any) return;
        long have = SkillLevels.total(info);
        int goal = goalOf(s, skill);
        boolean goalLines = goal != 0 && Features.on(GOALS) && option(GOALS, "tooltip", true);
        SkillLevels.Level overflow = Features.on(OVERFLOW) && option(OVERFLOW, "tooltip", true) ? SkillLevels.calculate(have, cap(skill)) : null;
        skillTooltip(tip, skill, have, goal, goalLines, overflow);
    }

    // from skyhanni SkillOverflowConfig "Skill Menu Stack Size": level past the cap instead of the capped one,
    // null = leave it
    public static String stackLevel(String skill) {
        if (!Features.on(OVERFLOW) || !option(OVERFLOW, "stack", true)) return null;
        Seen s = GameProfile.current();
        Seen.Skill info = s == null ? null : s.skills.get(skill);
        return info == null || !info.max ? null : String.valueOf(SkillLevels.overflowLevel(info, cap(skill)));
    }

    // --- level up lines ---

    // from skyhanni SkillProgress.onLevelUp minus their made up rewards: past the cap hypixel says nothing, and a
    // reached goal should be said
    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.player.tickCount % 20 != 0) return;
        Seen s = GameProfile.current();
        if (s == null) return;
        for (var e : s.skills.entrySet()) {
            String skill = e.getKey();
            int cap = cap(skill), level = SkillLevels.overflowLevel(e.getValue(), cap);
            Integer before = lastLevel.put(GameState.profileName + "|" + skill, level);
            if (before == null || level <= before) continue;
            int goal = goalOf(s, skill);
            boolean reached = goal > before && goal <= level && Features.on(GOALS) && option(GOALS, "chat", true);
            if (reached) Combat.say("§lYou have reached your goal level of §b§l" + goal + " §f§lin the §b§l" + skill + " §f§lskill!");
            boolean past = before >= cap && Features.on(OVERFLOW) && option(OVERFLOW, "chat", true);
            if (past) Combat.say("§b§lSKILL LEVEL UP §3" + skill + " §8" + before + "➜§3" + level);
            if (reached || past) Combat.play("minecraft:entity.player.levelup", 1f, 1f);
        }
    }

    // --- /sbc skills ---

    private static int help() {
        Combat.say("§6/sbc skills levelwithxp <xp> §7- the level that much XP is");
        Combat.say("§6/sbc skills xpforlevel <level> §7- the XP a level takes");
        Combat.say("§6/sbc skills goal §7- your goals");
        Combat.say("§6/sbc skills goal <skill> <level> §7- set a goal; without a level it goes");
        return 1;
    }

    // name like hypixel spells it whatever case was typed, null = not a skill
    static String skillNamed(String typed) {
        for (String skill : ORDER) if (skill.equalsIgnoreCase(typed)) return skill;
        return null;
    }

    // what /sbc skills goal answers, sets the goal when it can. pure on a store, for tests
    static String setGoal(Seen s, String typed, Integer level) {
        String skill = skillNamed(typed);
        if (skill == null) return "§cUnknown skill: " + typed;
        if (level == null) {
            s.skillGoals.remove(skill);
            return "Custom goal level for §b" + skill + " §freset";
        }
        Seen.Skill info = s.skills.get(skill);
        int now = info == null ? 0 : SkillLevels.overflowLevel(info, GameProfile.skillCap(skill));
        if (level <= now) return "§cCustom goal level (" + level + ") must be greater than your current level (" + now + ").";
        s.skillGoals.put(skill, level);
        return "Custom goal level for §b" + skill + " §fset to §b" + level;
    }

    private static int goalCommand(String skill, Integer level) {
        Seen s = GameProfile.current();
        if (s == null) {
            Combat.say("§cNo profile known yet.");
            return 1;
        }
        Combat.say(setGoal(s, skill, level));
        Store.markDirty();
        return 1;
    }

    private static int listGoals() {
        Seen s = GameProfile.current();
        Combat.say("§bSkill goals");
        if (s == null || s.skillGoals.isEmpty()) Combat.say("§cYou haven't set any goals yet!");
        else s.skillGoals.forEach((skill, level) -> Combat.say("§f" + skill + ": §b" + level));
        return 1;
    }

    private static int levelWithXp(long xp) {
        SkillLevels.Level l = SkillLevels.calculate(xp, 60);
        Combat.say("With §b" + num(xp) + " §fXP you would be level §b" + l.level() + " §fwith progress (§b" + num(l.current()) + "§f/§b" + num(l.forNext()) + "§f) XP");
        return 1;
    }

    private static int xpForLevel(int level) {
        Combat.say("You need §b" + num(SkillLevels.xpRequiredForLevel(level)) + " §fXP to reach level §b" + level);
        return 1;
    }

    public static final String MAXED_MENU = "skill_maxed_menu";

    // a skill's slot in Your Skills is gold once it is maxed, like its card on the profile page
    static void markMaxed(io.github.zmuzy.sbc.item.menus.MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            io.github.zmuzy.sbc.item.menus.MenuView.Item it = v.item(slot);
            if (!it.empty() && maxed(it.clean, it.cleanLore)) v.back(slot, io.github.zmuzy.sbc.item.menus.MenuView.GOLD);
        }
    }

    // "Enchanting LX" at its cap, or a skill whose lore says it is maxed. pure, for tests
    static boolean maxed(String name, List<String> lore) {
        String[] parts = name.split(" ");
        if (parts.length < 2) return false;
        String skill = parts[0];
        boolean isSkill = false;
        for (String l : lore) {
            if (l.contains("Click to view!")) isSkill = true;
            if (l.contains("MAX LEVEL") || l.contains("Max Level")) return isSkillName(skill);
        }
        if (!isSkill || !isSkillName(skill)) return false;
        String lvl = parts[parts.length - 1];
        int level = lvl.matches("\\d+") ? Integer.parseInt(lvl) : GameState.roman(lvl);
        int cap = cap(skill);
        return cap > 0 && level >= cap;
    }

    private static boolean isSkillName(String word) {
        return java.util.Arrays.asList(io.github.zmuzy.sbc.core.Profile.SKILLS).contains(word.toUpperCase(Locale.ROOT));
    }

    public static void register() {
        Features.add(new Feature(MAXED_MENU, "Skills", "Maxed skills in gold", "In Your Skills, a maxed skill's slot is gold.", "")
                .when(() -> GameState.inSkyBlock));
        io.github.zmuzy.sbc.item.menus.MenuMarks.add(MAXED_MENU, "Your Skills", SkillCards::markMaxed);
        Features.add(new Feature(ETA, "Skills", "Skill ETA", "The skill you are levelling: how long to the next level or your goal, XP per hour, and how long this session has run. All three pause when XP stops coming in. The skill progress bar shows the same, so this is off unless you want the card.", "SkyHanni: Skill ETA")
                .defaultOff()
                .when(() -> GameState.inSkyBlock)
                .option(Option.integer("skill_eta.pause_farming", "Pause Farming after (seconds)", "How long without XP still counts as doing it.", 3, 1, 120))
                .option(Option.integer("skill_eta.pause_mining", "Pause Mining after (seconds)", "", 3, 1, 120))
                .option(Option.integer("skill_eta.pause_combat", "Pause Combat after (seconds)", "", 30, 1, 120))
                .option(Option.integer("skill_eta.pause_foraging", "Pause Foraging after (seconds)", "", 3, 1, 120))
                .option(Option.integer("skill_eta.pause_fishing", "Pause Fishing after (seconds)", "", 15, 1, 120)));
        HudLayout.add(ETA_CARD);
        Features.add(new Feature(ALL, "Skills", "All skills", "Every skill with its level and its XP out of what the next level needs. Hide a skill in the HUD editor.", "SkyHanni: All Skill Display")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .option(Option.choice("skill_all.when", "When to show", "", "While gaining XP", "While gaining XP", "Always")));
        HudLayout.add(ALL_CARD);
        Features.add(new Feature(GOALS, "Skills", "Skill goals", "A level you are working towards, set with /sbc skills goal: the bar and the cards count to it instead of to the next level.", "SkyHanni: Custom Goal")
                .when(() -> GameState.inSkyBlock).tooltip(SkillCards::tooltip)
                .option(Option.bool("skill_goals.bar", "On the skill bar", "", true))
                .option(Option.bool("skill_goals.eta", "On the Skill ETA card", "", true))
                .option(Option.bool("skill_goals.all", "On the All skills card", "", true))
                .option(Option.bool("skill_goals.tooltip", "In Your Skills", "On each skill's tooltip.", true))
                .option(Option.bool("skill_goals.chat", "Line when you reach it", "", true)));
        Features.add(new Feature(OVERFLOW, "Skills", "Levels past the cap", "A maxed skill keeps counting from what its XP would be worth, as the levels Hypixel stops showing.", "SkyHanni: Skill Overflow").defaultOff()
                .when(() -> GameState.inSkyBlock).tooltip(SkillCards::overflowTooltip)
                .option(Option.bool("skill_overflow.cards", "On the bar and the cards", "", true))
                .option(Option.bool("skill_overflow.tooltip", "In Your Skills", "On each skill's tooltip.", true))
                .option(Option.bool("skill_overflow.stack", "As the number on a skill", "In Your Skills.", true))
                .option(Option.bool("skill_overflow.chat", "Level-up line", "", true)));
        // the goal line + level up line each have their own switch so neither depends on the others feature
        Safe.endTick("SkillCards.tick", SkillCards::tick);
        Safe.commands("SkillCards.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("skills").executes(c -> help())
                        .then(literal("levelwithxp").then(argument("xp", LongArgumentType.longArg(0)).executes(c -> levelWithXp(LongArgumentType.getLong(c, "xp")))))
                        .then(literal("xpforlevel").then(argument("level", IntegerArgumentType.integer(0, 400)).executes(c -> xpForLevel(IntegerArgumentType.getInteger(c, "level")))))
                        .then(literal("goal").executes(c -> listGoals())
                                .then(argument("skill", StringArgumentType.word()).executes(c -> goalCommand(StringArgumentType.getString(c, "skill"), null))
                                        .then(argument("level", IntegerArgumentType.integer(1, 400)).executes(c -> goalCommand(StringArgumentType.getString(c, "skill"), IntegerArgumentType.getInteger(c, "level")))))))
                .then(literal("resetskillsession").executes(c -> {
                    Vitals.resetSession();
                    Combat.say("Timer for §b" + Vitals.skill + " §fhas been reset!");
                    return 1;
                }))));
    }

    // a 2nd switch reaches the same lines: the goals feature can be off while this one is on
    private static void overflowTooltip(ItemStack stack, List<Component> tip) {
        if (!Features.on(GOALS)) tooltip(stack, tip);
    }
}
