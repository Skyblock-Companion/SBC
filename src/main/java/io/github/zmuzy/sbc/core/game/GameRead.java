// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.TabWidgets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// reads your own profile from what the game shows like skyhanni does, no network here. every method is pure
// over a Seen so tests can feed it skyhannis real lines + fake menus
public final class GameRead {
    // one open menu slot: name + lore as legacy text like skyhannis getLore
    public record MenuItem(int slot, String name, List<String> lore, String id, int count, double value, String vanilla, String petInfo) {
        public MenuItem(int slot, String name, List<String> lore, String id, int count, double value, String vanilla) {
            this(slot, name, lore, id, count, value, vanilla, "");
        }

        public static MenuItem of(int slot, String name, String... lore) {
            return new MenuItem(slot, name, List.of(lore), "", 1, 0, "");
        }

        public String cleanName() {
            return GameState.strip(name).trim();
        }

        public List<String> cleanLore() {
            List<String> out = new ArrayList<>();
            for (String l : lore) out.add(GameState.strip(l));
            return out;
        }

        MenuItem withId(String id) {
            return new MenuItem(slot, name, lore, id, count, value, vanilla, petInfo);
        }
    }

    // ---- from skyhanni SkillApi
    private static final Patterns.P SKILL_PERCENT = Patterns.p("SH.api.skilldisplay.skill.percent");
    private static final Patterns.P SKILL_MULTIPLIER = Patterns.p("SH.api.skilldisplay.skill.multiplier");
    private static final Patterns.P SKILL_TAB = Patterns.p("SH.api.skilldisplay.skill.tab.colorless");
    private static final Patterns.P SKILL_TAB_MAX = Patterns.p("SH.api.skilldisplay.skill.tab.max.colorless");
    private static final Patterns.P SKILL_TAB_NOPERCENT = Patterns.p("SH.api.skilldisplay.skill.tab.nopercent.colorless");
    private static final Patterns.P SKILL_MENU = Patterns.p("SH.api.skilldisplay.skill.menu.name");
    private static final Patterns.P SKILL_MENU_MAX = Patterns.p("SH.api.skilldisplay.skill.menu.maxreached");
    // ---- from skyhanni SkyBlockXPApi + the widgets
    private static final Patterns.P SB_MENU = Patterns.p("SH.utils.inventory.skyblockmenu");
    private static final Patterns.P SB_ITEM = Patterns.p("SH.skyblockxpapi.inventory.itemname");
    private static final Patterns.P SB_LEVEL_LORE = Patterns.p("SH.skyblockxpapi.inventory.level");
    private static final Patterns.P SB_XP_LORE = Patterns.p("SH.skyblockxpapi.inventory.xp");
    private static final Patterns.P SB_LEVEL_TAB = Patterns.p("SH.tab.widgetcomponent.enum.sb.level");
    private static final Patterns.P SKILLS_TAB = Patterns.p("SH.tab.widgetcomponent.enum.skills");
    private static final Patterns.P COPPER_TAB = Patterns.p("SH.tab.widgetcomponent.enum.copper");
    // ---- from skyhanni CurrencyApi
    private static final Patterns.P ESSENCE_LINE = Patterns.p("SH.data.currency.essence.amount");
    private static final Patterns.P ESSENCE_WIDGET = Patterns.p("SH.data.currency.essence.widget");
    private static final Patterns.P MEDAL_LINE = Patterns.p("SH.data.currency.medal.amount");
    // ---- from skyhanni MaxwellApi
    private static final Patterns.P YOUR_BAGS = Patterns.p("SH.data.maxwell.gui.yourbags");
    private static final Patterns.P BAG_STACK = Patterns.p("SH.data.maxwell.stack.accessorybag");
    private static final Patterns.P BAG_MP = Patterns.p("SH.data.maxwell.inventory.magicalpower");
    private static final Patterns.P REDSTONE_REQUIRED = Patterns.p("SH.data.maxwell.collection.redstone.requirement");
    private static final Patterns.P THAUMATURGY = Patterns.p("SH.data.maxwell.gui.thaumaturgy");
    private static final Patterns.P THAUMATURGY_MP = Patterns.p("SH.data.maxwell.gui.thaumaturgy.magicalpower");
    private static final Patterns.P CHAT_POWER = Patterns.p("SH.data.maxwell.chat.power");
    private static final Patterns.P CHAT_POWER_UNLOCKED = Patterns.p("SH.data.maxwell.chat.power.unlocked");
    private static final Patterns.P POWER_SELECTED = Patterns.p("SH.data.maxwell.gui.selectedpower");
    // ---- from skyhanni SkillApi.onChat
    private static final Patterns.P SKILL_JERRY_BOX = Patterns.p("SH.api.skilldisplay.chat.jerry-box.skillxp");
    private static final Patterns.P SKILL_GIFT = Patterns.p("SH.api.skilldisplay.chat.gift.skillxp");
    private static final Patterns.P LILY_START = Patterns.p("SH.api.skilldisplay.chat.lily-splosion.start");
    private static final Patterns.P LILY_XP = Patterns.p("SH.api.skilldisplay.chat.lily-splosion.skillxp");
    // from skyhanni SkillType maxLevel
    private static final Map<String, Integer> SKILL_CAP = Map.of("Combat", 60, "Farming", 60, "Fishing", 50, "Mining", 60, "Foraging", 57, "Enchanting", 60, "Alchemy", 50, "Carpentry", 50, "Taming", 60, "Hunting", 50);
    private static final Map<String, Double> GIFT_TALISMANS = Map.of("WHITE_GIFT_TALISMAN", 0.05, "GREEN_GIFT_TALISMAN", 0.10, "BLUE_GIFT_TALISMAN", 0.15, "PURPLE_GIFT_TALISMAN", 0.20, "GOLD_GIFT_TALISMAN", 0.25);
    private static long lastLilySplosion;
    // ---- from skyhanni AccessoryBagApi
    private static final Patterns.P BAG_PAGE = Patterns.p("SH.api.accessory.bag.inventory.name");
    private static final Pattern BAG_PAGE_NUMBER = Pattern.compile("\\((\\d+)/(\\d+)\\)");
    // ---- from skyhanni CollectionApi
    private static final Patterns.P COLLECTION_MENU = Patterns.p("SH.data.collection.api.collections");
    private static final Patterns.P COLLECTION_COUNTER = Patterns.p("SH.data.collection.api.counter.new");
    private static final Patterns.P COLLECTION_SINGLE = Patterns.p("SH.data.collection.api.singlecounter.new");
    private static final Patterns.P COLLECTION_PLAYER = Patterns.p("SH.data.collection.api.playercounter.new");
    private static final Patterns.P COLLECTION_NOT_MAXED = Patterns.p("SH.data.collection.api.collections.notmaxed.new");
    // ---- from skyhanni DungeonApi, DungeonFinderFeatures
    private static final Patterns.P BOSS_MENU = Patterns.p("SH.dungeon.boss.collections.inventory");
    private static final Patterns.P BOSS_LINE = Patterns.p("SH.dungeon.boss");
    private static final Patterns.P BOSS_KILLS = Patterns.p("SH.dungeon.level");
    private static final Patterns.P BOSS_TOTAL_KILLS = Patterns.p("SH.dungeon.total-kills");
    private static final Patterns.P DUNGEON_TEAM = Patterns.p("SH.dungeon.tablist.playerteam.colorless");
    private static final Patterns.P CLASS_DETECT = Patterns.p("SH.dungeon.finder.new.detect.dungeon.class");
    private static final Patterns.P CLASS_GET = Patterns.p("SH.dungeon.finder.new.get.dungeon.class");
    private static final Map<String, String> BOSS_FLOOR = Map.of("The Watcher", "F0", "Bonzo", "F1", "Scarf", "F2", "The Professor", "F3",
            "Thorn", "F4", "Livid", "F5", "Sadan", "F6", "Necron", "F7");
    // ---- from skyhanni PetStorageApi
    private static final Patterns.P PET_MENU = Patterns.p("SH.misc.pet.storage.menu.gui.name");
    private static final Patterns.P PET_STACK = Patterns.p("SH.misc.pet.storage.menu.petstack.name");
    private static final Patterns.P PET_TITLE_ITEM = Patterns.p("SH.misc.pet.storage.menu.gui.titleitem.name");
    private static final Patterns.P PET_XP = Patterns.p("SH.misc.pet.storage.menu.selected.xp.colorless");
    private static final Patterns.P PET_TAB_NAME = Patterns.p("SH.misc.pet.storage.tab.name");
    private static final Patterns.P AUTOPET = Patterns.p("SH.misc.pet.storage.autopet.message.formatted");
    private static final Patterns.P PET_HELD = Patterns.p("SH.misc.pet.storage.chat.helditem");
    private static final Patterns.P PET_HELD_REMOVED = Patterns.p("SH.misc.pet.storage.chat.removedhelditem");
    // from skyhanni LorenzRarity by color code
    private static final Map<String, String> PET_RARITY = Map.of("f", "COMMON", "a", "UNCOMMON", "9", "RARE", "5", "EPIC", "6", "LEGENDARY", "d", "MYTHIC");
    // ---- from skyhanni HotxData
    private static final Patterns.P HOTM_MENU = Patterns.p("SH.mining.hotm.inventory");
    private static final Patterns.P HOTF_MENU = Patterns.p("SH.foraging.hotf.inventory");
    private static final Patterns.P HOTM_LOCKED = Patterns.p("SH.mining.hotm.perk.notunlocked");
    private static final Patterns.P HOTF_LOCKED = Patterns.p("SH.foraging.hotf.perk.notunlocked");
    private static final Patterns.P HOTM_HEART = Patterns.p("SH.mining.hotm.inventory.heart");
    private static final Patterns.P HOTM_RESET = Patterns.p("SH.mining.hotm.inventory.reset");
    // the number in skyhannis notUnlockedPattern sample, "Requires Tier 10"
    private static final Pattern REQUIRES_TIER = Pattern.compile("Requires Tier (\\d+)");
    // ---- from skyhanni TrophyFishManager
    private static final Patterns.P TROPHY_MENU = Patterns.p("SH.fishing.trophyfish.odger.inventory");
    private static final Patterns.P TROPHY_RANK = Patterns.p("SH.fishing.trophyfish.odger.rank.colorless");
    private static final Patterns.P TROPHY_EMPTY = Patterns.p("SH.fishing.trophyfish.odger.rank.empty.colorless");
    private static final Patterns.P TROPHY_CHAT = Patterns.p("SH.fishing.trophy.trophyfish");
    private static final Pattern TROPHY_NAME = Pattern.compile("[- ]");
    // ---- from skyhanni EnigmaSoulWaypoints
    private static final Patterns.P ENIGMA_MENU = Patterns.p("SH.rift.everywhere.enigma-souls.inventory");
    private static final Patterns.P ENIGMA_TITLE = Patterns.p("SH.rift.everywhere.enigma-souls.title");
    private static final Patterns.P ENIGMA_NOT_DONE = Patterns.p("SH.rift.everywhere.enigma-souls.not-completed");
    // ---- from skyhanni BestiaryAchievement
    private static final Patterns.P BESTIARY_MILESTONE = Patterns.p("SH.achievements.bestiary");
    // ---- from skyhanni CurrentEquipmentApi
    private static final Patterns.P EQUIPMENT_MENU = Patterns.p("SH.data.equipment.equipment-and-stats-menu");
    private static final int[] EQUIPMENT_SLOTS = {10, 19, 28, 37};
    // ---- from skyblocker SlayerManager. these 2 arent in the catalog, it only has skyhannis
    private static final Pattern SLAYER_XP_NEEDED = Pattern.compile("\\s*(Wolf|Zombie|Spider|Enderman|Blaze|Vampire) Slayer LVL ([0-9]) - (?:Next LVL in ([\\d,]+) XP!|LVL MAXED OUT!)\\s*");
    private static final Pattern SLAYER_LVL_UP = Pattern.compile("\\s*LVL UP! ➜ (Wolf|Zombie|Spider|Enderman|Blaze|Vampire) Slayer LVL ([1-9])\\s*");
    // ---- from skyblocker CatacombsLevelAdder
    private static final Pattern DUNGEONEERING_LEVEL = Pattern.compile(".*?(?:(?: (?<arabic>\\d+)| (?<roman>[IVXLC]+))(?: ✯)?)?");
    private static final Pattern BRACKET_LEVEL = Pattern.compile("^\\[Lvl (\\d+)] (.+)$");

    // types CurrencyApi.essenceInternalNameOrNull accepts, like skyblockers EssenceWidget lists them
    private static final Set<String> ESSENCES = Set.of("Wither", "Spider", "Undead", "Dragon", "Gold", "Diamond", "Ice", "Crimson", "Forest", "Fossil");

    private GameRead() {}

    // from skyhanni NumberUtil.formatLong: "22,922", "75k", "4M". -1 = not a number
    public static long number(String text) {
        if (text == null) return -1;
        String t = text.replace(",", "").trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return -1;
        double mul = 1;
        if (t.endsWith("k")) mul = 1e3;
        else if (t.endsWith("m")) mul = 1e6;
        else if (t.endsWith("b")) mul = 1e9;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return Math.round(Double.parseDouble(t) * mul);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int level(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.chars().allMatch(Character::isDigit) ? Integer.parseInt(text) : GameState.roman(text);
    }

    // --- skills ---

    // from skyhanni levelingExp: xp of the next level, same table as the api path
    private static long levelCost(int level) {
        if (level + 1 >= Profile.SKILL_XP.length) return 7_600_000; // handleSkillPatternPercents number past the table
        return Profile.SKILL_XP[level + 1] - Profile.SKILL_XP[level];
    }

    // from skyhanni calculateLevelXP(level - 1): all the xp a level takes
    private static long levelBase(int level) {
        return Profile.SKILL_XP[Math.max(0, Math.min(level, Profile.SKILL_XP.length - 1))];
    }

    // from skyhanni getLevelExact: level this cost leads to, 0 = none costs that
    private static int levelForCost(long cost) {
        for (int i = 0; i + 1 < Profile.SKILL_XP.length; i++) if (Profile.SKILL_XP[i + 1] - Profile.SKILL_XP[i] == cost) return i + 1;
        return 0;
    }

    private static boolean realSkill(String name) {
        return GameState.REAL_SKILLS.contains(name);
    }

    // runecrafting + social have their own tables so xp cant come from the shared one
    public static boolean ownTable(String skill) {
        return skill.equals("Runecrafting") || skill.equals("Social");
    }

    // from skyhanni SkillApi.onInventoryFullyOpened: "Your Skills"
    static void skillsMenu(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            List<String> lore = item.cleanLore();
            if (lore.stream().noneMatch(l -> l.contains("Click to view!") || l.contains("Not unlocked!"))) continue;
            String[] split = item.cleanName().split(" ");
            String name = split[0];
            if (!realSkill(name)) continue;
            int level = split.length > 1 ? level(split[split.length - 1]) : 0;
            Seen.Skill info = s.skills.computeIfAbsent(name, k -> new Seen.Skill());
            info.level = level;
            for (int i = 1; i < lore.size(); i++) {
                String line = lore.get(i);
                if (!line.startsWith("                    ")) continue;
                String progress = line.substring(line.lastIndexOf(' ') + 1);
                if (SKILL_MENU_MAX.matcher(lore.get(i - 1)).matches()) {
                    long total = number(progress);
                    if (total >= 0) info.totalXp = total;
                    info.currentXp = 0;
                    info.currentXpMax = 0;
                    info.percent = -1;
                    info.max = true;
                } else {
                    String[] parts = progress.split("/");
                    long current = number(parts[0]), needed = number(parts[parts.length - 1]);
                    if (current < 0 || needed < 0) continue;
                    info.currentXp = current;
                    info.currentXpMax = needed;
                    info.totalXp = levelBase(level) + current;
                    info.percent = -1;
                    info.max = false;
                }
            }
        }
    }

    // tab Skills widget, read like SkillApis 3 tab patterns: "Farming 35: 12.4%", ": MAX", ": 22,922/75k"
    private static void skillTabLine(Seen s, String line) {
        Matcher m;
        if ((m = SKILL_TAB_MAX.matcher(line)).matches()) {
            String name = m.group("type");
            if (!realSkill(name)) return;
            Seen.Skill info = s.skills.computeIfAbsent(name, k -> new Seen.Skill());
            info.level = Integer.parseInt(m.group("level"));
            info.max = true;
            info.percent = -1;
        } else if ((m = SKILL_TAB_NOPERCENT.matcher(line)).matches()) {
            String name = m.group("type");
            if (!realSkill(name) || m.group("level") == null) return;
            long current = number(m.group("current")), needed = number(m.group("needed"));
            if (current < 0 || needed < 0) return;
            Seen.Skill info = s.skills.computeIfAbsent(name, k -> new Seen.Skill());
            info.level = Integer.parseInt(m.group("level"));
            info.currentXp = current;
            info.currentXpMax = needed;
            info.totalXp = levelBase(info.level) + current;
            info.percent = -1;
            info.max = false;
        } else if ((m = SKILL_TAB.matcher(line)).matches()) {
            String name = m.group("type");
            if (!realSkill(name) || m.group("level") == null) return;
            Seen.Skill info = s.skills.computeIfAbsent(name, k -> new Seen.Skill());
            int tabLevel = Integer.parseInt(m.group("level"));
            double percent = Double.parseDouble(m.group("progress"));
            long total = levelBase(tabLevel) + Math.round(levelCost(tabLevel) * percent / 100);
            // the menus exact numbers stay till the percent says more, a rounded percent can lag a bit behind
            if (tabLevel != info.level || total >= info.totalXp || info.currentXpMax <= 0) {
                info.level = tabLevel;
                info.percent = percent;
                info.totalXp = total;
                info.currentXp = 0;
                info.currentXpMax = 0;
                info.max = false;
            }
        }
    }

    // from skyhanni SkillApi.onActionBarUpdate: "+1.1 Mining (48.39%)" takes the level from tab, "+6.3 Foraging
    // (24/750)" from the cost
    public static void actionBar(Seen s, String colorless, List<String> tab) {
        if (colorless == null || !colorless.contains("(")) return;
        for (String part : colorless.split(" {2,}")) {
            String component = part.trim();
            Matcher percent = SKILL_PERCENT.matcher(component), multiplier = SKILL_MULTIPLIER.matcher(component);
            if (percent.matches()) {
                String name = percent.group("skillName");
                if (!realSkill(name)) return;
                for (String l : tab) {
                    String line = " " + l.trim();
                    Matcher m = SKILL_TAB.matcher(line);
                    if (m.matches() && name.equals(m.group("type")) && m.group("level") != null) {
                        Seen.Skill info = s.skills.computeIfAbsent(name, k -> new Seen.Skill());
                        info.level = Integer.parseInt(m.group("level"));
                        double got = Double.parseDouble(percent.group("progress").replace(",", ""));
                        info.percent = got;
                        info.totalXp = levelBase(info.level) + Math.round(levelCost(info.level) * got / 100);
                        info.currentXp = 0;
                        info.currentXpMax = 0;
                        info.max = false;
                        return;
                    }
                    m = SKILL_TAB_NOPERCENT.matcher(line);
                    if (m.matches() && name.equals(m.group("type")) && m.group("level") != null) {
                        skillTabLine(s, line);
                        return;
                    }
                }
                return;
            }
            if (multiplier.matches()) {
                String name = multiplier.group("skillName");
                if (!realSkill(name) || ownTable(name)) return;
                long current = number(multiplier.group("current")), needed = number(multiplier.group("needed"));
                if (current < 0 || needed < 0) return;
                Seen.Skill info = s.skills.computeIfAbsent(name, k -> new Seen.Skill());
                if (needed == 0) {
                    // from skyhanni handleSkillPatternMultiplier: past the cap the bar counts on from the caps level
                    if (info.level > 0) {
                        info.max = true;
                        info.currentXp = current;
                        info.totalXp = levelBase(info.level) + current;
                    }
                    return;
                }
                int level = levelForCost(needed) - 1;
                if (level < 0) return;
                info.level = level;
                info.currentXp = current;
                info.currentXpMax = needed;
                info.totalXp = levelBase(level) + current;
                info.percent = -1;
                info.max = false;
                return;
            }
        }
    }

    // --- tab list ---

    // one look at the tab: lines with codes stripped like GameState keeps them
    public static void tab(Seen s, List<String> lines, String playerName) {
        for (TabWidgets.Widget w : TabWidgets.parse(lines)) {
            String id = w.def().id();
            String head = lines.get(w.first());
            Matcher m;
            switch (id) {
                case "MINION" -> {
                    m = MINION_TAB.matcher(head.trim());
                    if (m.matches()) s.minionSlots = Integer.parseInt(m.group("slots"));
                }
                case "SB_LEVEL" -> {
                    m = SB_LEVEL_TAB.matcher(head);
                    if (m.matches()) s.totalSkyBlockXp = Integer.parseInt(m.group("level")) * 100 + Integer.parseInt(m.group("xp"));
                }
                case "SKILLS" -> {
                    m = SKILLS_TAB.matcher(head);
                    if (m.matches() && !m.group("avg").isEmpty()) s.tabSkillAverage = Double.parseDouble(m.group("avg"));
                    for (int i : w.lines()) skillTabLine(s, " " + lines.get(i).trim());
                }
                case "ESSENCE" -> {
                    for (int i : w.lines()) {
                        m = ESSENCE_WIDGET.matcher(lines.get(i));
                        if (!m.matches() || !ESSENCES.contains(m.group("type"))) continue;
                        s.essence.put(m.group("type"), number(m.group("amount")));
                    }
                }
                case "COPPER" -> {
                    m = COPPER_TAB.matcher(head);
                    // from skyhanni CurrencyApi.exactAmountOrNull: a rounded amount is worse than nothing
                    if (m.matches() && m.group("copper").trim().chars().allMatch(c -> Character.isDigit(c) || c == ',')) {
                        long v = number(m.group("copper"));
                        if (v >= 0) s.copper = v;
                    }
                }
                case "PET" -> {
                    for (int i : w.lines()) {
                        m = PET_TAB_NAME.matcher(" " + lines.get(i).trim());
                        if (m.matches()) activePet(s, m.group("pet").trim(), (int) number(m.group("level")));
                    }
                }
                default -> {
                }
            }
        }
        // from skyhanni DungeonApi.onTablistChange: in a dungeon your own line says class + level
        if (playerName != null && !playerName.isEmpty()) for (String l : lines) {
            if (!l.contains(playerName)) continue;
            Matcher m = DUNGEON_TEAM.matcher(l.trim());
            if (m.matches() && playerName.equals(m.group("playerName")) && m.group("className") != null) {
                s.selectedClass = m.group("className");
                Seen.Skill c = s.classes.computeIfAbsent(s.selectedClass, k -> new Seen.Skill());
                int read = GameState.roman(m.group("classLevel"));
                if (read != c.level) {
                    c.level = read;
                    c.percent = -1;
                    c.totalXp = 0;
                    c.currentXp = 0;
                    c.currentXpMax = 0;
                }
            }
        }
    }

    // from skyhanni PetStorageApi widget: the tab pet is the active one. rarity is only in the menu so a pet not
    // seen there waits
    private static void activePet(Seen s, String name, int level) {
        Seen.Pet active = null;
        List<Seen.Pet> all = s.allPets();
        for (Seen.Pet p : all) {
            if (!p.name.equals(name)) continue;
            if (p.level == level) {
                active = p;
                break;
            }
            if (active == null || p.active) active = p;
        }
        if (active == null) return;
        if (level > 0) active.level = level;
        for (Seen.Pet p : all) p.active = p == active;
    }

    // --- chat ---

    // what the game said + what youre wearing on your head
    public static boolean chat(Seen s, String plain, String formatted, String helmetId, long now) {
        return skillChat(s, plain, helmetId, now) | petChat(s, plain, formatted) | powerChat(s, formatted) | chat(s, plain, formatted);
    }

    // from skyhanni SkillApi.onChat: xp the action bar never shows
    static boolean skillChat(Seen s, String plain, String helmetId, long now) {
        String message = plain.trim();
        if (LILY_START.matcher(message).matches()) {
            lastLilySplosion = now;
            return false;
        }
        Matcher m = SKILL_JERRY_BOX.matcher(message);
        if (m.matches()) return addSkillXp(s, m.group("skillName"), number(m.group("gained")));
        m = SKILL_GIFT.matcher(message);
        if (m.matches()) return addSkillXp(s, m.group("skillName"), number(m.group("gained")) * giftMultiplier(s, helmetId));
        m = LILY_XP.matcher(message);
        if (m.matches() && now - lastLilySplosion <= 5000) return addSkillXp(s, m.group("skillName"), number(m.group("gained")));
        return false;
    }

    // from skyhanni giftXpMultiplier. the carnival professional gifter isnt read here so it adds nothing
    private static double giftMultiplier(Seen s, String helmetId) {
        double talisman = 0;
        for (List<Seen.Item> page : s.accessoryPages.values())
            for (Seen.Item it : page) talisman = Math.max(talisman, GIFT_TALISMANS.getOrDefault(it.id, 0.0));
        return 1 + talisman + ("SNOWMAN_MASK".equals(helmetId) ? 0.10 : 0);
    }

    // from skyhanni addChatSkillXp: only on top of a known total, never from a guessed 0
    private static boolean addSkillXp(Seen s, String name, double gained) {
        Seen.Skill info = s.skills.get(name);
        if (gained <= 0 || !realSkill(name) || ownTable(name) || info == null || info.totalXp <= 0) return false;
        long total = Math.round(info.totalXp + gained);
        info.totalXp = total;
        int level = 0;
        for (int i = Profile.SKILL_XP.length - 1; i >= 0; i--) {
            if (Profile.SKILL_XP[i] <= total) {
                level = i;
                break;
            }
        }
        int cap = SKILL_CAP.getOrDefault(name, 60);
        info.level = Math.min(level, cap);
        info.max = info.level >= cap;
        info.currentXp = total - levelBase(info.level);
        info.currentXpMax = info.max ? 0 : levelCost(info.level);
        info.percent = -1;
        return true;
    }

    // from skyhanni PetStorageApi.onChat
    static boolean petChat(Seen s, String plain, String formatted) {
        Matcher m = PET_HELD.matcher(plain);
        if (m.matches()) return setActivePetHeld(s, m.group("item"));
        if (PET_HELD_REMOVED.matcher(plain).matches()) return setActivePetHeld(s, "");
        if (formatted == null) return false;
        m = AUTOPET.matcher(formatted.replace("§r", ""));
        if (!m.matches()) return false;
        String name = m.group("pet").trim(), tier = PET_RARITY.get(m.group("rarity"));
        int level = Integer.parseInt(m.group("level"));
        Seen.Pet found = null;
        List<Seen.Pet> all = s.allPets();
        for (Seen.Pet p : all) {
            if (!p.name.equals(name) || !p.tier.equals(tier)) continue;
            if (found == null || p.level == level) found = p;
        }
        if (found == null) return false; // next pets menu look adds it
        found.level = Math.max(found.level, level);
        for (Seen.Pet p : all) p.active = p == found;
        return true;
    }

    private static boolean setActivePetHeld(Seen s, String itemName) {
        for (Seen.Pet p : s.allPets()) {
            if (!p.active) continue;
            String id = itemName.isEmpty() ? null : ItemRepo.idForName(itemName);
            p.held = id == null ? "" : id;
            p.heldName = id == null ? itemName : "";
            return true;
        }
        return false;
    }

    // from skyhanni PetStorageApi.onSlotClick: right click takes the pet out, left click summons/despawns
    public static boolean petClick(Seen s, String title, MenuItem item, boolean right, boolean shift) {
        if (!PET_MENU.matcher(title).matches() || !petSlot(item.slot())) return false;
        Matcher m = PET_STACK.matcher(item.cleanName());
        if (!m.matches()) return false;
        Seen.Pet clicked = pet(item, m);
        if (clicked == null) return false;
        if (right) {
            if (clicked.uuid.isEmpty()) return false;
            boolean removed = false;
            for (List<Seen.Pet> page : s.petPages.values()) removed |= page.removeIf(p -> p.uuid.equals(clicked.uuid));
            return removed;
        }
        if (shift) return false;
        List<Seen.Pet> all = s.allPets();
        boolean wasOut = clicked.active || all.stream().anyMatch(p -> p.active && samePet(p, clicked));
        for (Seen.Pet p : all) p.active = !wasOut && samePet(p, clicked);
        return true;
    }

    private static boolean samePet(Seen.Pet a, Seen.Pet b) {
        if (!a.uuid.isEmpty() || !b.uuid.isEmpty()) return a.uuid.equals(b.uuid);
        return a.type.equals(b.type) && a.tier.equals(b.tier) && a.level == b.level;
    }

    // from skyhanni MaxwellApi.onChat
    static boolean powerChat(Seen s, String formatted) {
        if (formatted == null) return false;
        String message = formatted.trim().replace("§r", "");
        Matcher m = CHAT_POWER.matcher(message);
        if (!m.matches()) {
            m = CHAT_POWER_UNLOCKED.matcher(message);
            if (!m.matches()) return false;
        }
        s.selectedPower = GameState.strip(m.group("power")).trim();
        return true;
    }

    // from skyhanni CollectionApi.onItemAdd: only an already read collection. compressed items never are one so
    // getPrimitiveMultipliers check holds
    public static boolean collected(Seen s, String id, int amount) {
        Long have = s.collections.get(id);
        if (have == null || amount <= 0) return false;
        s.collections.put(id, have + amount);
        return true;
    }

    // from skyblocker SlayerManager + TrophyFishManager. plain = no colors, formatted = with. true = learned something
    public static boolean chat(Seen s, String plain, String formatted) {
        Matcher m = SLAYER_XP_NEEDED.matcher(plain);
        if (m.matches()) {
            boolean maxed = m.group(3) == null;
            int level = Integer.parseInt(m.group(2));
            s.slayers.put(slayerName(m.group(1)), new long[]{level, maxed ? 0 : number(m.group(3))});
            return true;
        }
        m = SLAYER_LVL_UP.matcher(plain);
        if (m.matches()) {
            s.slayers.put(slayerName(m.group(1)), new long[]{Integer.parseInt(m.group(2)), -1});
            return true;
        }
        if (formatted == null) return false;
        m = TROPHY_CHAT.matcher(formatted);
        if (m.matches()) {
            String fish = fishKey(GameState.strip(m.group("displayName")));
            String rarity = Profile.cap(GameState.strip(m.group("displayRarity")));
            // unknown stays unknown, counting up from a guessed 0 shows a wrong total
            Map<String, Integer> have = s.trophyFish.get(fish);
            if (have == null) return false;
            have.merge(rarity, 1, Integer::sum);
            return true;
        }
        return false;
    }

    private static String slayerName(String chatName) {
        return switch (chatName) {
            case "Zombie" -> "Revenant";
            case "Spider" -> "Tarantula";
            case "Wolf" -> "Sven";
            case "Enderman" -> "Voidgloom";
            case "Blaze" -> "Inferno";
            default -> "Riftstalker";
        };
    }

    // from skyhanni TrophyFishApi.getInternalName
    static String fishKey(String displayName) {
        return TROPHY_NAME.matcher(displayName.replace("Obfuscated", "Obfuscated Fish")).replaceAll("").toLowerCase(Locale.ROOT);
    }

    // --- menus ---

    // one look at an open menu. inDungeon like MaxwellApi asks it bc mp is boosted there
    public static void menu(Seen s, String title, List<MenuItem> items, boolean inDungeon) {
        if (SKILL_MENU.matcher(title).matches()) skillsMenu(s, items);
        if (SB_MENU.matcher(title).matches()) skyBlockMenu(s, items);
        if (YOUR_BAGS.matcher(title).matches()) yourBags(s, items, inDungeon);
        if (THAUMATURGY.matcher(title).matches()) thaumaturgy(s, items);
        if (BAG_PAGE.matcher(title).matches()) accessoryPage(s, title, items);
        if (COLLECTION_MENU.matcher(title).matches()) collections(s, title, items);
        if (BOSS_MENU.matcher(title).matches()) bossCollections(s, items);
        // "Forge pets menu is also called Pets, but doesn't have this item" (skyhanni): slot 4 tells them apart
        if (PET_MENU.matcher(title).matches() && items.stream().anyMatch(i -> i.slot() == 4 && PET_TITLE_ITEM.matcher(i.cleanName()).matches())) pets(s, title, items);
        if (HOTM_MENU.matcher(title).matches()) heart(s, items, true);
        if (HOTF_MENU.matcher(title).matches()) heart(s, items, false);
        if (TROPHY_MENU.matcher(title).matches()) trophyFish(s, items);
        if (ENIGMA_MENU.matcher(title).matches()) enigma(s, items);
        if (title.equals("Bestiary")) bestiary(s, items);
        if (EQUIPMENT_MENU.matcher(title).matches()) equipment(s, items);
        if (title.startsWith("Dungeoneering")) dungeoneering(s, items);
        if (title.startsWith("Dungeon Classes")) dungeonClasses(s, items);
        io.github.zmuzy.sbc.core.Bits.menu(s, title, items);
        io.github.zmuzy.sbc.pets.PetExtras.menu(s, title, items);
        io.github.zmuzy.sbc.minion.MinionFeatures.menu(s, title, items);
        anyMenu(s, items);
    }

    // from skyhanni SkyBlockXPApi.onInventoryFullyOpened
    private static void skyBlockMenu(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            if (!SB_ITEM.matcher(item.name()).matches()) continue;
            int level = -1, xp = -1;
            for (String line : item.lore()) {
                Matcher m = SB_LEVEL_LORE.matcher(line);
                if (level < 0 && m.matches()) {
                    level = Integer.parseInt(m.group("level"));
                    continue;
                }
                m = SB_XP_LORE.matcher(line);
                if (xp < 0 && m.matches()) xp = Integer.parseInt(m.group("xp"));
            }
            if (level >= 0 && xp >= 0) s.totalSkyBlockXp = level * 100 + xp;
            return;
        }
    }

    // from skyhanni MaxwellApi.processStack, the mp part
    private static void yourBags(Seen s, List<MenuItem> items, boolean inDungeon) {
        for (MenuItem item : items) {
            if (!BAG_STACK.matcher(item.name()).matches()) continue;
            boolean found = false;
            for (String line : item.lore()) {
                if (REDSTONE_REQUIRED.matcher(line).matches()) {
                    s.magicalPower = 0;
                    return;
                }
                Matcher m = BAG_MP.matcher(line);
                if (m.matches()) {
                    found = true;
                    if (!inDungeon) s.magicalPower = (int) number(m.group("mp"));
                }
            }
            if (!found) s.magicalPower = 0;
        }
    }

    // from skyhanni loadThaumaturgyMagicalPower: slot 48
    private static void thaumaturgy(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            if (!item.lore().isEmpty() && POWER_SELECTED.matcher(item.lore().get(item.lore().size() - 1)).matches()) s.selectedPower = item.cleanName();
        }
        for (MenuItem item : items) {
            if (item.slot() != 48) continue;
            for (String line : item.lore()) {
                Matcher m = THAUMATURGY_MP.matcher(line);
                if (m.matches()) {
                    s.magicalPower = (int) number(m.group("mp"));
                    return;
                }
            }
        }
    }

    private static Seen.Item seenItem(MenuItem it, boolean keepLore) {
        Seen.Item out = new Seen.Item();
        out.id = it.id();
        out.name = it.name();
        out.vanilla = it.vanilla();
        out.count = it.count();
        out.value = it.value();
        if (keepLore) out.lore = new ArrayList<>(it.lore());
        return out;
    }

    // from skyhanni AccessoryBagApi: "Accessory Bag (1/2)". each page replaces what it had so what you took out goes
    private static void accessoryPage(Seen s, String title, List<MenuItem> items) {
        Matcher number = BAG_PAGE_NUMBER.matcher(title);
        int page = number.find() ? Integer.parseInt(number.group(1)) : 1;
        List<Seen.Item> read = new ArrayList<>();
        for (MenuItem item : items) if (!item.id().isEmpty()) read.add(seenItem(item, false));
        s.accessoryPages.put(page, read);
    }

    // from skyhanni CollectionApi.onInventoryFullyOpened. ids stay like the menu gives them, those are the apis
    // collection keys
    // from skyhanni CollectionApi.getCorrectedName
    private static String collectionId(String id) {
        return "MUSHROOM_COLLECTION".equals(id) ? "RED_MUSHROOM" : id;
    }

    private static void collections(Seen s, String title, List<MenuItem> items) {
        if (title.endsWith("n")) {
            for (MenuItem item : items) {
                if (item.slot() == 4 && !item.id().isEmpty()) {
                    for (String line : item.cleanLore()) {
                        Matcher m = COLLECTION_SINGLE.matcher(line);
                        if (m.matches()) {
                            long v = number(m.group("amount"));
                            if (v >= 0) s.collections.put(collectionId(item.id()), v);
                            break;
                        }
                    }
                }
                // from skyhanni readOneMaxCollection: one bosses own page says its total
                if (item.slot() == 4 && title.endsWith(" Collection")) {
                    String boss = title.substring(0, title.length() - " Collection".length());
                    String floor = BOSS_FLOOR.get(boss);
                    if (floor == null) continue;
                    boolean fromBosses = items.stream().anyMatch(o -> o.slot() == 48 && o.cleanName().equals("Go Back")
                            && !o.cleanLore().isEmpty() && o.cleanLore().get(0).equals("To Boss Collections"));
                    if (!fromBosses) continue;
                    for (String line : item.cleanLore()) {
                        if (!line.contains("Total Kills:")) continue;
                        Matcher m = BOSS_TOTAL_KILLS.matcher(line);
                        if (m.matches() && number(m.group("kills")) >= 0) s.bossKills.put(floor, (int) number(m.group("kills")));
                    }
                }
            }
        }
        if (title.endsWith("s") && !BOSS_MENU.matcher(title).matches()) {
            for (MenuItem item : items) {
                if (item.cleanName().contains("Collections") || item.id().isEmpty()) continue;
                List<String> lore = item.cleanLore();
                if (lore.stream().noneMatch(l -> l.contains("Click to view!"))) continue;
                boolean coop = lore.stream().anyMatch(l -> COLLECTION_PLAYER.matcher(l).matches());
                boolean notMaxed = lore.stream().anyMatch(l -> COLLECTION_NOT_MAXED.matcher(l).matches());
                if (!coop || notMaxed) {
                    for (String line : lore) {
                        Matcher m = COLLECTION_COUNTER.matcher(line);
                        if (m.matches()) {
                            long v = number(m.group("amount"));
                            if (v >= 0) s.collections.put(collectionId(item.id()), v);
                            break;
                        }
                    }
                } else {
                    int from = lore.indexOf("Co-op Contributions:");
                    if (from < 0) continue;
                    long total = 0;
                    for (String line : lore.subList(from, lore.size())) {
                        if (line.isBlank()) continue;
                        Matcher m = COLLECTION_PLAYER.matcher(line);
                        if (m.matches()) {
                            long v = number(m.group("amount"));
                            if (v > 0) total += v;
                        }
                    }
                    s.collections.put(collectionId(item.id()), total);
                }
            }
        }
    }

    // from skyhanni readAllCollections: "View all your Bonzo Collection" + " 1234" on its own line
    private static void bossCollections(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            String name = "";
            int kills = 0;
            for (String line : item.cleanLore()) {
                Matcher m = BOSS_LINE.matcher(line);
                if (m.matches()) name = m.group("name");
                m = BOSS_KILLS.matcher(line);
                if (m.matches()) {
                    kills = Integer.parseInt(m.group("kills"));
                    break;
                }
            }
            String floor = BOSS_FLOOR.get(name);
            if (floor != null) s.bossKills.put(floor, kills);
        }
    }

    // from skyhanni LorenzRarity.getByColorCode on the pets own name, like petNameAndRarityOrNull takes it from
    // that spans color
    private static String tierOf(String coloredName, String pet) {
        int at = coloredName.indexOf(pet);
        for (int i = at - 2; i >= 0; i--) {
            if (coloredName.charAt(i) != '§') continue;
            switch (coloredName.charAt(i + 1)) {
                case 'f' -> {
                    return "COMMON";
                }
                case 'a' -> {
                    return "UNCOMMON";
                }
                case '9' -> {
                    return "RARE";
                }
                case '5' -> {
                    return "EPIC";
                }
                case '6' -> {
                    return "LEGENDARY";
                }
                case 'd' -> {
                    return "MYTHIC";
                }
                default -> {
                }
            }
        }
        return "";
    }

    // from skyhanni PetStorageApi.isPetStackLocation
    private static boolean petSlot(int slot) {
        return slot >= 10 && slot <= 43 && slot % 9 != 0 && (slot + 1) % 9 != 0;
    }

    // from skyhanni toVisiblePetDataOrNull: stack petInfo first (type, exact xp, held item, the uuid telling
    // copies apart), then "[Lvl 100] Elephant", its xp if the lore says, held item
    private static void pets(Seen s, String title, List<MenuItem> items) {
        Matcher number = BAG_PAGE_NUMBER.matcher(title);
        boolean paged = number.find();
        int page = paged ? Integer.parseInt(number.group(1)) : 1, pages = paged ? Integer.parseInt(number.group(2)) : 1;
        List<Seen.Pet> read = new ArrayList<>();
        Seen.Pet active = null;
        for (MenuItem item : items) {
            if (!petSlot(item.slot())) continue;
            Matcher m = PET_STACK.matcher(item.cleanName());
            if (!m.matches()) continue;
            Seen.Pet p = pet(item, m);
            if (p == null) continue;
            read.add(p);
            if (p.active) active = p;
        }
        // a pet with a uuid is in one place, sorting or selling moves it between pages
        for (Seen.Pet p : read) {
            if (p.uuid.isEmpty()) continue;
            for (List<Seen.Pet> other : s.petPages.values()) other.removeIf(o -> o.uuid.equals(p.uuid));
        }
        // what old versions remembered by type + tier goes once the same pet is read
        List<Seen.Pet> old = s.petPages.get(0);
        if (old != null) {
            old.removeIf(o -> read.stream().anyMatch(p -> p.type.equals(o.type) && p.tier.equals(o.tier)));
            if (old.isEmpty()) s.petPages.remove(0);
        }
        s.petPages.put(page, read);
        s.petPages.keySet().removeIf(n -> n > pages);
        // only one pet is out: the page with the active one says so, the others lose the mark
        if (active != null) for (Seen.Pet p : s.allPets()) p.active = p == active;
    }

    private static Seen.Pet pet(MenuItem item, Matcher m) {
        JsonObject info = petInfo(item.petInfo());
        Seen.Pet p = new Seen.Pet();
        p.name = m.group("pet").trim();
        p.type = info == null ? "" : text(info, "type");
        if (p.type.isEmpty()) p.type = p.name.toUpperCase(Locale.ROOT).replace(' ', '_');
        p.tier = tierOf(item.name(), p.name);
        if (p.tier.isEmpty() && info != null) p.tier = text(info, "tier");
        if (p.tier.isEmpty()) return null;
        p.level = (int) number(m.group("level"));
        for (String l : item.cleanLore()) {
            String line = l.trim();
            if (line.startsWith("Held Item: ")) {
                p.heldName = line.substring(11);
                String id = ItemRepo.idForName(p.heldName);
                p.held = id == null ? "" : id;
            }
            if (line.contains("Click to despawn")) p.active = true;
            Matcher xp = PET_XP.matcher(l);
            if (p.exp < 0 && xp.matches()) {
                long current = number(xp.group("current"));
                if (current >= 0) p.exp = xp.group("next") == null ? current : io.github.zmuzy.sbc.core.Pets.xpAtLevel(p.tier, p.level) + current;
            }
        }
        if (info != null) {
            // from skyhanni PetInfo.ownedUuid
            p.uuid = text(info, "uniqueId");
            if (p.uuid.isEmpty()) p.uuid = text(info, "uuid");
            String held = text(info, "heldItem");
            if (!held.isEmpty()) p.held = held;
            // from skyhanni petInfoExp: a zero only counts at level 1
            double exp = info.has("exp") && info.get("exp").isJsonPrimitive() ? info.get("exp").getAsDouble() : 0;
            if (exp > 0 || p.level <= 1 && info.has("exp")) p.exp = exp;
        }
        return p;
    }

    private static JsonObject petInfo(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    // from skyhanni HotxHandler.parse + readFromHeartOrReset. the trees own tier has no line in either mod, what
    // they read is a locked perks "Requires Tier 10" and the lowest number there
    private static void heart(Seen s, List<MenuItem> items, boolean mountain) {
        Patterns.P locked = mountain ? HOTM_LOCKED : HOTF_LOCKED;
        int lowest = Integer.MAX_VALUE;
        for (MenuItem item : items) {
            for (String line : item.cleanLore()) {
                if (!locked.matcher(line.trim()).matches()) continue;
                Matcher m = REQUIRES_TIER.matcher(line.trim());
                if (m.matches()) lowest = Math.min(lowest, Integer.parseInt(m.group(1)));
            }
        }
        if (lowest != Integer.MAX_VALUE) {
            if (mountain) s.hotmTier = lowest - 1;
            else s.hotfTier = lowest - 1;
        }
        perks(s, items, mountain);
        if (!mountain) return;
        for (MenuItem item : items) {
            boolean isHeart = HOTM_HEART.matcher(item.cleanName()).matches(), isReset = HOTM_RESET.matcher(item.cleanName()).matches();
            if (!isHeart && !isReset) continue;
            for (String type : new String[]{"mithril", "gemstone", "glacite"}) {
                Patterns.P p = Patterns.p("SH.inventory." + type + "." + (isHeart ? "heart" : "reset"));
                for (String line : item.cleanLore()) {
                    Matcher m = p.matcher(line);
                    if (!m.matches()) continue;
                    long[] have = s.powder.computeIfAbsent(type, k -> new long[]{-1, -1});
                    have[isHeart ? 0 : 1] = number(m.group("powder"));
                }
            }
        }
    }

    // from skyhanni HotxHandler.parse + handleCurrency: each perk the page shows + the tokens on the heart and
    // reset items. a reread page only replaces what it shows
    private static void perks(Seen s, List<MenuItem> items, boolean mountain) {
        String tree = mountain ? "SH.mining.hotm." : "SH.foraging.hotf.";
        Patterns.P locked = mountain ? HOTM_LOCKED : HOTF_LOCKED, level = Patterns.p(tree + "perk.level"), enabled = Patterns.p(tree + "perk.enable");
        Patterns.P heartToken = Patterns.p(tree + "inventory.heart.token"), resetToken = Patterns.p(tree + "inventory.reset.token");
        Map<String, Integer> levels = mountain ? s.hotmPerks : s.hotfPerks;
        java.util.Set<String> off = mountain ? s.hotmOff : s.hotfOff;
        int[] tokens = mountain ? s.hotmTokens : s.hotfTokens;
        String core = mountain ? HeartTrees.MOUNTAIN_CORE : HeartTrees.FOREST_CORE, heartName = mountain ? "Heart of the Mountain" : "Heart of the Forest", resetName = "Reset " + heartName;
        for (MenuItem item : items) {
            String name = item.cleanName();
            List<String> lore = item.cleanLore();
            if (name.equals(heartName) || name.equals(resetName)) {
                for (String line : lore) {
                    Matcher t = (name.equals(heartName) ? heartToken : resetToken).matcher(line);
                    if (t.matches()) tokens[name.equals(heartName) ? 0 : 1] = (int) number(t.group("token"));
                }
                continue;
            }
            int max = HeartTrees.maxLevel(mountain, name);
            if (max <= 0 || lore.isEmpty()) continue;
            String key = HeartTrees.key(name);
            if (!name.equals(core) && locked.matcher(lore.get(lore.size() - 1)).matches()) {
                levels.put(key, 0);
                off.remove(key);
                continue;
            }
            Matcher m = level.matcher(item.lore().get(0));
            // from skyhanni readingLevelTransform: a blue level is one the blue egg added
            int read = m.matches() ? Integer.parseInt(m.group("level")) - (mountain && "b".equals(m.group("color")) ? 1 : 0) : max;
            levels.put(key, read);
            if (name.equals(core)) continue;
            boolean on = false;
            for (String line : lore) {
                String trimmed = line.trim();
                if (enabled.matcher(trimmed).matches()) on = true;
                if (trimmed.equals("SELECTED")) {
                    if (mountain) s.hotmAbility = key;
                    else s.hotfAbility = key;
                }
            }
            if (on) off.remove(key);
            else off.add(key);
        }
    }

    // from skyhanni TrophyFishManager.onInventoryFullyOpened: odgers menu has each ranks count
    private static void trophyFish(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            for (String line : item.cleanLore()) {
                Matcher m = TROPHY_RANK.matcher(line);
                if (m.matches()) {
                    long v = number(m.group("amount"));
                    if (v >= 0) s.trophyFish.computeIfAbsent(fishKey(item.cleanName()), k -> new java.util.LinkedHashMap<>()).put(Profile.cap(m.group("rarity").trim()), (int) v);
                    continue;
                }
                m = TROPHY_EMPTY.matcher(line);
                if (m.matches()) s.trophyFish.computeIfAbsent(fishKey(item.cleanName()), k -> new java.util.LinkedHashMap<>()).put(Profile.cap(m.group("rarity").trim()), 0);
            }
        }
    }

    // from skyhanni EnigmaSoulWaypoints: a soul without "Not completed yet!" as its last line is found
    private static void enigma(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            Matcher m = ENIGMA_TITLE.matcher(item.cleanName());
            if (!m.matches()) continue;
            s.enigmaRead = true;
            List<String> lore = item.cleanLore();
            boolean open = !lore.isEmpty() && ENIGMA_NOT_DONE.matcher(lore.get(lore.size() - 1)).matches();
            if (open) s.enigmaSouls.remove(m.group("name"));
            else s.enigmaSouls.add(m.group("name"));
        }
    }

    // from skyhanni BestiaryAchievement: the item named "Bestiary Milestone XI"
    private static void bestiary(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            Matcher m = BESTIARY_MILESTONE.matcher(item.cleanName());
            if (m.matches()) s.bestiaryMilestone = level(m.group("milestone"));
        }
    }

    // from skyhanni CurrentEquipmentApi.handleInventoryItem: the 4 slots of "Stats & Equipment"
    private static void equipment(Seen s, List<MenuItem> items) {
        List<Seen.Item> read = new ArrayList<>();
        for (int slot : EQUIPMENT_SLOTS) for (MenuItem item : items) if (item.slot() == slot && !item.id().isEmpty()) read.add(seenItem(item, true));
        s.equipment = read;
    }

    // from skyblocker CatacombsLevelAdder.Dungeoneering: slot 12 = catacombs, 29 to 33 = classes, level at the
    // end of the name
    private static void dungeoneering(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            int slot = item.slot();
            if (slot != 12 && (slot < 29 || slot > 33)) continue;
            String clean = item.cleanName();
            Matcher m = DUNGEONEERING_LEVEL.matcher(clean);
            if (!m.matches()) continue;
            int lvl = m.group("arabic") != null ? Integer.parseInt(m.group("arabic")) : m.group("roman") != null ? GameState.roman(m.group("roman")) : 0;
            String word = clean.split(" ")[0];
            Seen.Skill info;
            if (slot == 12) {
                if (!word.equals("The") && !word.equals("Catacombs")) continue;
                if (s.catacombs == null) s.catacombs = new Seen.Skill();
                info = s.catacombs;
            } else info = s.classes.computeIfAbsent(word, k -> new Seen.Skill());
            info.level = lvl;
            readBar(info, item.cleanLore());
        }
    }

    // from skyhanni SkillApi progress line if the item has one: 20 spaces then "current/needed"
    private static void readBar(Seen.Skill info, List<String> lore) {
        for (String line : lore) {
            if (!line.startsWith("                    ")) continue;
            String progress = line.substring(line.lastIndexOf(' ') + 1);
            String[] parts = progress.split("/");
            if (parts.length != 2) continue;
            long current = number(parts[0]), needed = number(parts[1]);
            if (current < 0 || needed <= 0) continue;
            info.currentXp = current;
            info.currentXpMax = needed;
            return;
        }
        info.currentXp = 0;
        info.currentXpMax = 0;
    }

    // from skyblocker CatacombsLevelAdder.DungeonClasses: slots 11 to 15, "[Lvl 34] Mage"
    private static void dungeonClasses(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            if (item.slot() < 11 || item.slot() > 15) continue;
            Matcher m = BRACKET_LEVEL.matcher(item.cleanName());
            if (!m.matches()) continue;
            Seen.Skill info = s.classes.computeIfAbsent(m.group(2).trim(), k -> new Seen.Skill());
            info.level = Integer.parseInt(m.group(1));
            readBar(info, item.cleanLore());
        }
    }

    // from skyhanni CurrencyApi + DungeonFinderFeatures: lines that mean the same in any menu
    private static final Patterns.P MINION_LIMIT = Patterns.p("GameRead.MINION_LIMIT"), MINION_TAB = Patterns.p("GameRead.MINION_TAB");

    private static void anyMenu(Seen s, List<MenuItem> items) {
        for (MenuItem item : items) {
            List<String> lore = item.cleanLore();
            for (String line : lore) {
                // "Minion limit: 26/32" on the crafted minions item (collections + crafted minions menus)
                Matcher ml = MINION_LIMIT.matcher(line.trim());
                if (ml.matches()) {
                    s.minionSlots = Integer.parseInt(ml.group("slots"));
                    continue;
                }
                Matcher m = ESSENCE_LINE.matcher(line);
                if (m.matches() && ESSENCES.contains(m.group("type"))) {
                    long v = number(m.group("amount"));
                    if (v >= 0) s.essence.put(m.group("type"), v);
                    continue;
                }
                m = MEDAL_LINE.matcher(line);
                if (m.matches()) {
                    long v = number(m.group("amount"));
                    if (v >= 0) s.medals.put(Profile.cap(m.group("type")), (int) v);
                }
            }
            if (lore.size() > 3 && CLASS_DETECT.matcher(lore.get(0)).matches()) {
                Matcher m = CLASS_GET.matcher(lore.get(2));
                if (m.matches()) s.selectedClass = m.group("class");
            }
        }
    }
}
