// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.muzy.sbc.SbcMod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// everything we remember between sessions, config/sbcompanion/data.json (local only)
public final class Store {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path FILE = Config.DIR.resolve("data.json");

    public static Store I = new Store();
    private static boolean dirty;

    // stonks auction round as its item said last + the next city project contribution
    public long stonksRoundEnd, stonksLastBidRoundEnd, stonksBid, cityProjectNext;
    public Map<String, Map<String, Integer>> progress = new HashMap<>();
    // taskId -> learned regex (/sbc learn)
    public Map<String, String> learned = new HashMap<>();
    // witch ingredient guide lore by name
    public Map<String, List<String>> witchGuide = new HashMap<>();
    public List<CustomTask> customTasks = new ArrayList<>();
    // activityId -> measured totals
    public Map<String, Rate> rates = new HashMap<>();
    public List<SessionRecord> sessions = new ArrayList<>();
    // [epochMillis, purse, bank]
    public List<long[]> wealth = new ArrayList<>();
    // your minions per profile name: "ISLAND@x, y, z" (or "ISLAND@TypeTier" from a menu) -> record
    public Map<String, Map<String, MinionRecord>> minionsByProfile = new HashMap<>();
    // minions from before the split, or seen while no profile name was known yet. they go to the next profile read
    public Map<String, MinionRecord> minions = new LinkedHashMap<>();
    // "ISLAND|Name" -> [x,y,z], npc spots learned from their nametags
    public Map<String, double[]> npcs = new HashMap<>();
    // island|npc name -> head texture seen on it (map icons)
    public Map<String, String> npcTextures = new HashMap<>();
    // forge recipe (item name) -> hotm tier it needs, read from the forge menu
    public Map<String, Integer> forgeTiers = new HashMap<>();
    // id -> epochMillis when its ready (forge, kat, cookie...)
    public Map<String, Long> timers = new HashMap<>();
    // cookie end time per profile name, 0 = known off
    public Map<String, Long> cookieUntil = new HashMap<>();
    // next greenhouse growth stage per profile name
    public Map<String, Long> growthStageAt = new HashMap<>();
    // this years jacob contests from the calendar
    public List<io.muzy.sbc.garden.contest.NextContest.Known> jacobContests = new ArrayList<>();
    // items you starred for dungeon + kuudra chests, by sb id
    public java.util.Set<String> chestFavorites = new java.util.HashSet<>();
    // islands youve been to, unlocks routine tasks
    public List<String> visited = new ArrayList<>();
    // island -> active ms
    public Map<String, Long> islandTime = new HashMap<>();
    // what the acc bag pages showed, for playing without an api key
    public java.util.Set<String> accessoriesSeen = new java.util.HashSet<>();
    // every sack seen in the Sack of Sacks, like accessoriesSeen
    public java.util.Set<String> sacksSeen = new java.util.HashSet<>();
    // maxwell tuning as last seen, one entry per stat with color + icon
    public List<String> statTunings = new ArrayList<>();
    // the stat of each of those, when the menu said it
    public List<String> statTuningNames = new ArrayList<>();
    // a menu said what the tunings are, so none = none, not unknown
    public boolean statTuningsRead;
    // attribute shards by bazaar name: syphoned, in the hunting box, enabled (1) or not (0)
    public Map<String, int[]> attributeShards = new HashMap<>();
    // shard tracker: bazaar name -> how many you need, -1 = till maxed
    public Map<String, Integer> trackedShards = new java.util.LinkedHashMap<>();
    // from skyhanni BitsApi fameRank, per account: repo rank id, empty = unknown
    public String fameRank = "";
    // from skyhanni UpgradeReminder account upgrade, per account. null = none
    public io.muzy.sbc.core.game.Seen.ShopUpgrade accountUpgrade;
    // maxwell menus per profile name, see core/data/MaxwellData (skyhanni MaxwellPowerStorage)
    public Map<String, MaxwellProfile> maxwell = new HashMap<>();
    // thaumaturgy powers marked with shift click
    public java.util.Set<String> favoritePowers = new java.util.HashSet<>();
    // new year cake years you have
    public java.util.Set<Integer> cakesOwned = new java.util.HashSet<>();
    // what hypixels pets menu showed, for playing without an api key
    public Map<String, SeenPet> petsSeen = new HashMap<>();
    // fastest time per dungeon run stretch per floor, in sec
    public Map<String, Map<String, Long>> dungeonBests = new HashMap<>();
    // slayer rng meters, zealots, arrows per profile name, see combat/CombatData
    public Map<String, io.muzy.sbc.combat.CombatData> combat = new HashMap<>();
    // bingo, carnival, gifts, cakes: see events/EventData
    public io.muzy.sbc.events.EventData events = new io.muzy.sbc.events.EventData();
    // whats waiting at croesus per profile
    public Map<String, List<CroesusRun>> croesusRuns = new HashMap<>();

    public static final class CroesusRun {
        public String floor, state;
        public long at;
    }

    public static final class SeenPet {
        public String type = "", name = "", tier = "", held = "";
        public int level;
        public boolean active;
    }

    // hypixels own /playtimedetailed numbers, when read, and islandTime at that moment
    public Map<String, Long> playtime = new HashMap<>();
    public long playtimeAt;
    public Map<String, Long> playtimeBase = new HashMap<>();
    // scanned sb leveling tasks
    public List<LevelTask> levelTasks = new ArrayList<>();
    public long levelTasksScannedAt;
    // skill info seen in tab/menus: skill -> [level, percentToNext]
    public Map<String, double[]> observedSkills = new HashMap<>();
    // active ms per profit tracker over all your play, what its all time coins were made in
    public Map<String, Long> trackerMs = new HashMap<>();
    public long lastLoginBriefing;
    // day the auto routine last started (once a day)
    public String autoGuidedDay = "";
    public long totalActiveMs;
    // locked inventory slots (0-8 hotbar, 9-35 main) so nothing can move or drop them
    public java.util.Set<Integer> lockedSlots = new java.util.HashSet<>();
    // items (uuid, or id if none) that can never be dropped or sold
    public java.util.Set<String> protectedItems = new java.util.HashSet<>();
    // starred in the item list
    public java.util.Set<String> favourites = new java.util.HashSet<>();
    // collectibles already picked up (island|x:y:z)
    public java.util.Set<String> found = new java.util.HashSet<>();
    // named trackers (see Trackers): id -> day/week totals that survive restarts
    public Map<String, TrackerData> trackers = new HashMap<>();
    // waypoint groups (see Waypoints): name -> group
    public Map<String, WpGroup> waypoints = new LinkedHashMap<>();
    // hoppity / chocolate factory per profile name (skyhanni CFStorage)
    public Map<String, HoppityProfile> hoppity = new HashMap<>();

    // mining per profile name (skyhanni MiningConfig)
    public Map<String, MiningProfile> mining = new HashMap<>();

    // rift per profile name (skyhanni RiftStorage)
    public Map<String, RiftProfile> rift = new HashMap<>();

    // sacks per profile name (skyhanni SackData.sackContents): item id -> amount, lastChange, status
    public Map<String, SackProfile> sacks = new HashMap<>();

    // from skyhanni BazaarApi tax rate (per player there)
    public double bazaarTaxRate = 1.25;
    // from skyhanni BazaarLimitTracker: coins traded on the bazaar today + the utc day
    public double bazaarCoinsTowardsLimit;
    public String bazaarLimitDay = "";
    // from skyhanni MagicalPowerDisplay: abiphone contacts, an abicase adds half of them
    public int abiphoneContacts;


    public static final class SackProfile {
        public Map<String, int[]> contents = new HashMap<>();
    }

    // crimson per profile name (skyhanni CrimsonIsleStorage)
    public Map<String, CrimsonProfile> crimson = new HashMap<>();

    // garden per profile name (skyhanni GardenStorage)
    public Map<String, GardenProfile> garden = new HashMap<>();

    // what the game showed of your profile, per profile name (core/game/GameProfile)
    public Map<String, io.muzy.sbc.core.game.Seen> gameProfiles = new HashMap<>();

    // fairy souls per profile name (skyhanni FairySoulsStorage)
    public Map<String, FairyProfile> fairySouls = new HashMap<>();

    // from skyhanni GardenStorage: crop milestone counters + speeds the milestone display measured
    public static final class GardenProfile {
        // crop -> crops counted for milestones
        public Map<String, Long> cropMilestoneCounter = new HashMap<>();
        // crop -> crops per sec (with fortune) / latest blocks per sec
        public Map<String, Integer> cropsPerSecond = new HashMap<>();
        public Map<String, Double> latestBlocksPerSecond = new HashMap<>();
        // visitors, composter, contests, plots
        public io.muzy.sbc.garden.GardenData data = new io.muzy.sbc.garden.GardenData();
        // stereo harmony vinyl playing, null = none
        public String activeVinyl;
        // plot -> when its spray runs out, what it was, did chat get told
        public Map<String, Long> sprayExpiry = new HashMap<>();
        public Map<String, String> sprayType = new HashMap<>();
        public java.util.Set<String> sprayNotified = new java.util.HashSet<>();
        // garden xp from the desk, -1 before you opened it
        public long experience = -1;
        // tool uuid -> overflow levels above 50
        public Map<String, Integer> overflowHoeLevels = new HashMap<>();
        // crop -> x, y, z
        public Map<String, double[]> cropStartLocations = new HashMap<>();
        public Map<String, double[]> cropLastFarmedLocations = new HashMap<>();
        // crop -> 0 north-south or 1 east-west, min, max
        public Map<String, double[]> farmingLanes = new HashMap<>();
        // "yyyy-MM-dd" -> secs farmed that day
        public Map<String, Long> uptime = new HashMap<>();
        // crop -> latest true farming fortune
        public Map<String, Double> latestTrueFortune = new HashMap<>();
        // harvest feast as teds menu said last: crops in season, when the others come, when the month ends
        public List<String> feastCrops = new ArrayList<>();
        public Map<String, Long> feastNext = new HashMap<>();
        public long feastEnd;
    }

    // from skyhanni FairySoulsStorage: found soul spots ("x:y:z") per island + hypixels found total per island
    public static final class FairyProfile {
        public Map<String, java.util.Set<String>> found = new HashMap<>();
        public Map<String, Integer> totalFound = new HashMap<>();
    }

    // from skyhanni RiftStorage
    public static final class RiftProfile {
        // when ubiks cube is ready again (ms), far future = ready
        public long ubikRemindTime = Long.MAX_VALUE / 4;
        // from skyhanni KloonHacking: terminals done by color name
        public java.util.List<String> completedKloonTerminals = new java.util.ArrayList<>();
        // enigma souls found by name, for the marks on every soul
        public java.util.List<String> foundEnigmaSouls = new java.util.ArrayList<>();
    }

    // from skyhanni CrimsonIsleStorage (+ crimsonIsleFaction)
    public static final class CrimsonProfile {
        // from skyhanni KuudraKeys: what each key costs you, read from the faction shop
        public Map<String, Integer> kuudraKeyPrices = new HashMap<>();
        // from skyhanni KuudraKeys: best kuudra pet seen in the pets menu
        public String kuudraPetTier = "";
        public int kuudraPetLevel;
        public double kuudraPetExp;
        // "BARBARIAN" / "MAGE" / null
        public String faction;
        public Map<String, Integer> reputation = new HashMap<>();
        // from skyhanni DailyQuestHelper.saveConfig: "name:STATE:need:have"
        public java.util.List<String> quests = new java.util.ArrayList<>();
        public java.util.List<String> miniBossesDoneToday = new java.util.ArrayList<>();
        public java.util.List<String> kuudraTiersDone = new java.util.ArrayList<>();
        // sb day the daily data was reset for last (quests reset with the daily reset)
        public long lastReset;
    }

    // selected power, magical power, each powers stats + the mp they were read at
    public static final class MaxwellProfile {
        public String power = "";
        public int magicalPower;
        public Map<String, List<String>> stats = new HashMap<>();
        public Map<String, Integer> statsAt = new HashMap<>();
    }

    // saved ordered waypoint routes: name -> blocks as x, y, z
    public Map<String, java.util.List<double[]>> orderedRoutes = new HashMap<>();

    public static final class MiningProfile {
        // powder type -> available, total
        public Map<String, long[]> powder = new HashMap<>();
        // mineshaft type -> entered since the last of that type / when last entered (ms)
        public Map<String, Integer> mineshaftsEnteredSince = new HashMap<>();
        public Map<String, Long> lastMineshaftTime = new HashMap<>();
        public long mineshaftTotalBlocks;
        public int mineshaftTotalCount;
        public int tokens;
        // hotm perk -> level the tree said last
        public Map<String, Integer> hotmLevels = new HashMap<>();
        // todays sky mall buff as a line
        public String skyMall = "";
        public int flowstateBest;
        // pity block -> blocks broken, spread blocks broken since the last mineshaft
        public Map<String, int[]> pityBlocks = new HashMap<>();
        // dwarven kings you talked to, for the king talisman
        public java.util.List<String> kingsTalkedTo = new java.util.ArrayList<>();
    }

    // from skyhanni CFStorage: what the chocolate factory menu said last + hoppity egg memory
    public static final class HoppityProfile {
        public long currentChocolate, chocolateThisPrestige, chocolateAllTime, maxChocolate;
        public double chocolateMultiplier = 1, rawChocolateMultiplier = 1;
        public int rawChocPerSecond;
        public long lastDataSave;
        public int timeTowerLevel, currentTimeTowerUses, maxTimeTowerUses = 3;
        public long currentTimeTowerEnds = Long.MIN_VALUE / 4, nextTimeTower = Long.MIN_VALUE / 4;
        public int currentRabbits, maxRabbits = -1;
        public long bestUpgradeAvailableAt = Long.MIN_VALUE / 4, bestUpgradeCost;
        public int hoppityShopYearOpened = -1;
        // island -> "x:y:z" egg spots already collected there
        public Map<String, java.util.Set<String>> collectedEggLocations = new HashMap<>();
        // meal -> when its egg was found last / when it spawns next (ms)
        public Map<String, Long> mealLastFound = new HashMap<>(), mealNextSpawn = new HashMap<>();
        // rabbit -> times found (hoppitys collection)
        public Map<String, Integer> rabbitCounts = new HashMap<>();
        // rabbit hitman
        public int availableHitmanEggs, purchasedHitmanSlots;
        public long hitmanSingleSlotCooldown = Long.MIN_VALUE / 4, hitmanAllSlotsCooldown = Long.MIN_VALUE / 4;
        // leaderboard change
        public int lastLeaderboardPosition = -1;
        public String lastLeaderboard;
        public long lastLeaderboardTime;
        // sb year -> that hoppity hunt (HoppityEventStats)
        public Map<Integer, HoppityEventStats> eventStats = new HashMap<>();
        // chocolate factory reminder goal
        public long reminderGoal;
        public String reminderName = "";
    }

    // from skyhanni HoppityEventStats: one hunt. rabbits per rarity: unique, dupe, stray
    public static final class HoppityEventStats {
        public Map<String, Integer> mealsFound = new HashMap<>();
        public Map<String, int[]> rabbitsFound = new HashMap<>();
        public long dupeChocolateGained, strayChocolateGained, millisInCf, lastLbUpdate = Long.MIN_VALUE / 4;
        public int rabbitTheFishFinds, initialPosition = -1, finalPosition = -1;
        public double initialPercentile = -1, finalPercentile = -1;
        public int[] typeCountSnapshot, typeCountsSince;
        public boolean summarized;
    }

    public static final class CustomTask {
        public String id, name, period = "DAILY", regex = "", island = "";
        public int target = 1, minutes = 5;
        // 3 = critical, 2 = important, 1 = optional
        public int priority = 2;
    }

    public static final class Rate {
        public long activeMs;
        public double coins;
        public double sbXp;
        public Map<String, Double> skillXp = new HashMap<>();

        public double coinsPerHour() { return activeMs < 60_000 ? 0 : coins * 3_600_000.0 / activeMs; }
        public double sbXpPerHour() { return activeMs < 60_000 ? 0 : sbXp * 3_600_000.0 / activeMs; }
        public double skillPerHour(String skill) {
            return activeMs < 60_000 ? 0 : skillXp.getOrDefault(skill, 0.0) * 3_600_000.0 / activeMs;
        }
    }

    public static final class SessionRecord {
        public long start, end, activeMs;
        public double coins, sbXp;
        public Map<String, Long> islandMs = new HashMap<>();
    }

    // minions of the profile youre on
    public static Map<String, MinionRecord> minions() {
        return I.minionsOf(GameState.profileName);
    }

    // a profiles minions. the old shared map moves into the first profile asked for, once
    public Map<String, MinionRecord> minionsOf(String profile) {
        if (profile == null || profile.isEmpty()) return minions;
        Map<String, MinionRecord> m = minionsByProfile.computeIfAbsent(profile, k -> new LinkedHashMap<>());
        if (!minions.isEmpty()) {
            // records from before fromScan was saved: seen by the head scan, never opened = made by the scan
            for (MinionRecord r : minions.values()) if (r.lastSeen == 0 && r.catalogType != null && !r.fromSpot) r.fromScan = true;
            minions.forEach(m::putIfAbsent);
            minions.clear();
            markDirty();
        }
        return m;
    }

    public static final class MinionRecord {
        public String key, type;
        // made by the head scan (so the scan may forget it), not by opening the menu or a remembered spot
        public boolean fromScan;
        // made from a spot MinionFeatures remembers, goes when that spot does
        public boolean fromSpot;
        // repo type id (COBBLESTONE) when we know it from its head
        public String catalogType;
        public long lastDetected;
        public int tier;
        public String island = "";
        public List<String> upgrades = new ArrayList<>();
        public String fuel = "";
        // item name -> count seen at lastSeen
        public Map<String, Integer> storage = new HashMap<>();
        public long lastSeen;
        // item name -> measured items per hour
        public Map<String, Double> itemsPerHour = new HashMap<>();
        public int storageSlots;
    }

    public static final class TrackerData {
        public String dayKey = "", weekKey = "";
        // item or label -> count, coins
        public Map<String, double[]> day = new LinkedHashMap<>(), week = new LinkedHashMap<>(), all = new LinkedHashMap<>();
    }

    // from skyhanni BeaconPower: what the beacon menu said, per profile name
    public Map<String, BeaconPower> beaconPower = new HashMap<>();

    public static final class BeaconPower {
        public long expiry;
        public String stat = "";
    }

    public static final class WpGroup {
        public String name = "";
        public int color = 0xFF39D0FF;
        public boolean visible = true;
        public double radius = 3;
        // skyblockers ordered groups: one after another, the way they were marked
        public boolean ordered;
        public transient int current;
        public List<WpPoint> points = new ArrayList<>();
    }

    public static final class WpPoint {
        public String name = "", island = "";
        public double x, y, z;
        // 0 = the groups color
        public int color;
    }

    public static final class LevelTask {
        public String category, name;
        public int xp;
        public boolean done;
        public String progress = "";
    }

    public static void markDirty() {
        dirty = true;
    }

    // hash of what this part saves as, to tell if it changed since last look
    public static int savedHash(Object part) {
        return GSON.toJson(part).hashCode();
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Store s = GSON.fromJson(Files.readString(FILE), Store.class);
                if (s != null) I = s;
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read data.json, starting fresh (old file kept as data.json.bad)", e);
            try {
                Files.copy(FILE, FILE.resolveSibling("data.json.bad"), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }
    }

    public static void saveIfDirty() {
        if (dirty) save();
    }

    public static void save() {
        dirty = false;
        try {
            Files.createDirectories(Config.DIR);
            Path tmp = FILE.resolveSibling("data.json.tmp");
            Files.writeString(tmp, GSON.toJson(I));
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not save data.json", e);
        }
    }
}
