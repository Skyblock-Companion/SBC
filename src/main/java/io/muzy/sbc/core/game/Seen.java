// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// what the game showed about one profile, kept between sessions in Store.gameProfiles. follows skyhannis
// ProfileSpecificStorage (skills, sb xp, currencies, essences, dungeons...)
public final class Seen {
    // from skyhanni SkillApi.SkillInfo + the tab percent when thats all there is
    public static final class Skill {
        public int level;
        public long currentXp, currentXpMax, totalXp;
        // into the next level 0 to 100, -1 = the numbers above are what was read
        public double percent = -1;
        // "Max Skill level reached!" or the tabs MAX
        public boolean max;
    }

    // one item like the menu showed it, minus stuff that can be read again
    public static final class Item {
        public String id = "", name = "", vanilla = "";
        public List<String> lore = new ArrayList<>();
        public int count = 1;
        public double value;
    }

    public static final class Pet {
        public String type = "", name = "", tier = "", held = "";
        public int level;
        public boolean active;
        // all its xp when the menu said it, -1 = only the level is known
        public double exp = -1;
        // petInfo ownedUuid, tells 2 copies apart. empty = no petInfo
        public String uuid = "";
        // name the lore gave when it named an item the repo didnt know yet
        public String heldName = "";
    }

    // from skyhanni SkyBlockXPApi: level * 100 + xp into the level, -1 = unknown
    public int totalSkyBlockXp = -1;
    public long purse = -1, bank = -1, copper = -1, motes = -1;
    // skills widget own average, -1 = unknown
    public double tabSkillAverage = -1;
    public Map<String, Skill> skills = new LinkedHashMap<>();
    // slayer -> level, xp left to next (-1 if the line didnt say)
    public Map<String, long[]> slayers = new LinkedHashMap<>();
    public Skill catacombs;
    public Map<String, Skill> classes = new LinkedHashMap<>();
    public String selectedClass = "";
    // from skyhanni DungeonApi bosses: "F7" -> boss collection kills
    public Map<String, Integer> bossKills = new LinkedHashMap<>();
    // pets menu page -> what was on it so a reread page replaces itself. page 0 = what old versions remembered
    public Map<Integer, List<Pet>> petPages = new HashMap<>();
    // acc bag page -> what was on it so a reread page replaces itself
    public Map<Integer, List<Item>> accessoryPages = new HashMap<>();
    public int magicalPower = -1;
    // from skyhanni MaxwellApi.currentPower, empty = unknown
    public String selectedPower = "";
    // item id like the collections menu gives it -> count. ids are the apis keys
    public Map<String, Long> collections = new LinkedHashMap<>();
    public int hotmTier = -1, hotfTier = -1;
    // from skyhanni HotxHandler.parse: perk (HeartTrees.key of its name) -> level, as far as the trees were seen
    public Map<String, Integer> hotmPerks = new LinkedHashMap<>(), hotfPerks = new LinkedHashMap<>();
    // leveled perks that werent enabled
    public Set<String> hotmOff = new HashSet<>(), hotfOff = new HashSet<>();
    // the ability that says SELECTED, empty = unknown
    public String hotmAbility = "", hotfAbility = "";
    // tokens to spend + spent from the heart and reset items, -1 = unknown
    public int[] hotmTokens = {-1, -1}, hotfTokens = {-1, -1};
    // powder type -> available, spent from the heart and reset items, -1 = unknown
    public Map<String, long[]> powder = new HashMap<>();
    // "Undead" -> amount
    public Map<String, Long> essence = new LinkedHashMap<>();
    // "Gold" -> amount
    public Map<String, Integer> medals = new LinkedHashMap<>();
    // fish -> rarity -> count (skyhanni crimsonIsle.trophyFishes)
    public Map<String, Map<String, Integer>> trophyFish = new LinkedHashMap<>();
    // enigma souls ticked in the rift guide + that the guide was opened at all
    public Set<String> enigmaSouls = new HashSet<>();
    public boolean enigmaRead;
    public int bestiaryMilestone = -1;
    public List<Item> equipment = new ArrayList<>();
    // the "Profile ID:" line that came with this profiles "You are playing on profile:" line, empty till one did
    public String profileId = "";

    // skill -> the level youre going for (skyhanni customGoalLevel), only what you set with /sbc skills
    public Map<String, Integer> skillGoals = new LinkedHashMap<>();
    // from skyhanni BitsApi bitsAvailable + museumMilestone, -1 = unknown
    public long bitsAvailable = -1;
    // minion slots you have, from the crafted minions item or the island tab. -1 = not shown yet
    public int minionSlots = -1;
    public int museumMilestone = -1;
    // the 3 exp share slots: pet uuid, empty = no pet (skyhanni PetStorageExpShare)
    public List<String> expSharePets = new ArrayList<>();
    // your minions on the private island: "x,y,z" -> what MinionFeatures remembers
    public Map<String, MinionSpot> minionSpots = new LinkedHashMap<>();
    // from skyhanni ProfileSpecificStorage.hiddenCoopMembers
    public Set<String> hiddenCoopMembers = new HashSet<>();
    // from skyhanni UpgradeReminder: this profiles community shop upgrade, null = none
    public ShopUpgrade profileUpgrade;

    // from skyhanni ProfileSpecificStorage.MinionConfig
    public static final class MinionSpot {
        public String displayName = "";
        // when the hopper got emptied last, 0 = unknown
        public long lastClicked;
    }

    // from skyhanni UpgradeReminder.CommunityShopUpgrade
    public static final class ShopUpgrade {
        public String name = "";
        public long completionTime = Long.MAX_VALUE;
    }

    // every pet read, page by page
    public List<Pet> allPets() {
        List<Pet> out = new ArrayList<>();
        petPages.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> out.addAll(e.getValue()));
        return out;
    }
}
