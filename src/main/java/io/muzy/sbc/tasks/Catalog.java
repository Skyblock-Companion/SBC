// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.tasks;

import io.muzy.sbc.core.SbTime.Period;

import java.util.ArrayList;
import java.util.List;

import static io.muzy.sbc.tasks.TaskDef.daily;

// built in recurring tasks. coin + sb xp values are rough mid game guesses only for ordering the routine, the
// planner uses measured rates. island gated tasks only show once youve been there
public final class Catalog {
    public static final List<TaskDef> TASKS = new ArrayList<>();

    private Catalog() {}

    static {
        // --- dwarven mines / crystal hollows ---
        TASKS.add(daily("commissions", "Commissions (daily bonus)", "Mining", "iron_pickaxe", 4, 20, 400_000, 0, "DWARVEN_MINES", "King",
                "First commissions of the day give bonus HOTM XP and powder. Claim at an Emissary.",
                List.of("mining.commissions.complete"), List.of(".*Commission Complete! Visit the King.*")));
        TASKS.add(daily("fetchur", "Fetchur's request", "Mining", "chest", 1, 3, 50_000, 0, "DWARVEN_MINES", "Fetchur",
                "Bring Fetchur the item he asks for today (Mithril Powder reward).", List.of(), List.of("^\\[NPC\\] Fetchur: thanks thats probably what i needed.*")));
        TASKS.add(daily("puzzler", "Puzzler's puzzle", "Mining", "compass", 1, 2, 30_000, 0, "DWARVEN_MINES", "Puzzler",
                "Solve the Puzzler's arrow puzzle in the Dwarven Mines.", List.of(), List.of("(?i)^Puzzler gave you .* for solving the puzzle", "(?i)^\\[NPC\\] Puzzler: .*Nice!")));
        TASKS.add(daily("nucleus", "Crystal Nucleus run", "Mining", "amethyst_shard", 1, 25, 1_500_000, 0, "CRYSTAL_HOLLOWS", "Geonathan Greatforge",
                "Place all 5 crystals in the Crystal Nucleus.", List.of(), List.of(".*You've earned a Crystal Loot Bundle!.*", ".*CRYSTAL NUCLEUS LOOT BUNDLE.*")));
        TASKS.add(daily("forge_collect", "Collect & refill the Forge", "Mining", "anvil", 1, 2, 0, 0, "DWARVEN_MINES", "Forge Foreman",
                "Claim finished forge items and start new ones so slots never sit idle.", List.of(), List.of()));

        // --- crimson isle ---
        TASKS.add(daily("crimson_quests", "Faction daily quests", "Crimson Isle", "blaze_rod", 5, 30, 300_000, 0, "CRIMSON_ISLE", "",
                "Complete your 5 daily faction quests for reputation.", List.of(), List.of("(?i).*DAILY QUEST COMPLETE.*", "(?i)^You completed your .* quest! Visit the Town Board")));
        TASKS.add(daily("dojo", "Dojo test", "Crimson Isle", "golden_sword", 1, 5, 50_000, 0, "CRIMSON_ISLE", "Dojo",
                "Do a Dojo test for belt progress and reputation.", List.of(), List.of("(?i).*Test of .* OBTAINED.*", "(?i)^\\s*YOUR RANK: .*", "(?i)^You completed your Dojo quest")));
        TASKS.add(daily("pomtair", "Pomtair: Enchanted Bone Meal", "Crimson Isle", "bone_meal", 1, 2, 0, 0, "CRIMSON_ISLE", "Pomtair",
                "Give Pomtair 1 Enchanted Bone Meal for +50 Barbarian reputation (daily).", List.of(),
                List.of("(?i)\\[NPC\\] Pomtair: (?:Oh thanks, I really appreciate you|Oh sulfur! It worked|I hope I won't lose this Enchanted Bone Meal|I'm so untalented, I'll probably burn the potatoes)")));
        TASKS.add(daily("sirih", "Sirih: give Sulphur", "Crimson Isle", "gunpowder", 1, 2, 0, 0, "CRIMSON_ISLE", "Sirih",
                "Give Sirih (in Pig form) 1 Sulphur for +10 Barbarian reputation (daily).", List.of(),
                List.of("(?i)\\[NPC\\] Sirih: (?:Thanks for the Sulphur|Again\\.\\.\\.|What, when\\? Oh|I get so absorbed by my work)")));
        TASKS.add(daily("suus", "Suus: Turd's Toy (the cube)", "Crimson Isle", "brown_dye", 1, 3, 0, 0, "CRIMSON_ISLE", "Suus",
                "Bring Suus Turd's Toy (10% drop from Magma Cube Riders) for +40 Barbarian reputation. Every 2 days.", List.of(),
                List.of("(?i)\\[NPC\\] Suus: (?:Thanks for finding his toy|Such an expensive toy|You found his toy|Thank you so much)")));
        TASKS.add(daily("matriarch", "Matriarch heavy pearls", "Crimson Isle", "ender_pearl", 1, 3, 250_000, 0, "CRIMSON_ISLE", "The Matriarch",
                "Collect today's Heavy Pearls from the Matriarch in the Crimson Isle.", List.of(), List.of("(?i).*Heavy Pearl.*")));
        TASKS.add(daily("kuudra", "Kuudra run", "Crimson Isle", "magma_cream", 1, 15, 2_000_000, 0, "CRIMSON_ISLE", "Elle",
                "Run Kuudra at your tier; the chest is the main money.", List.of(), List.of("(?i).*KUUDRA DOWN!.*", "(?i).*KUUDRA ESCAPED!.*")));

        // --- garden / farming ---
        TASKS.add(daily("visitors", "Garden visitors", "Farming", "emerald", 5, 10, 500_000, 0, "GARDEN", "",
                "Accept visitor offers before the queue fills up (copper, Garden XP, rare drops).",
                List.of("garden.visitor.droptracker.accept"), List.of("(?i)OFFER ACCEPTED (?:with|w/)")));
        TASKS.add(daily("jacob", "Jacob's Contest", "Farming", "wheat", 1, 20, 800_000, 0, "GARDEN", "",
                "Farm one of the three contest crops; medals buy upgrades from Anita.", List.of(), List.of("(?i).*You earned a? ?.* medal.*", "(?i).*JACOB'S CONTEST.*FINISHED.*", "(?i)^\\s*JACOB'S CONTEST REWARDS CLAIMED", "(?i)^You (?:placed|finished) in the .* bracket")));
        TASKS.add(daily("composter", "Composter", "Farming", "composter", 1, 2, 100_000, 0, "GARDEN", "",
                "Refill organic matter and fuel so the Composter keeps running.", List.of(), List.of("(?i)^Added [\\d,]+ (?:organic matter|fuel) to the composter")));
        TASKS.add(daily("pests", "Clear pests", "Farming", "slime_ball", 1, 5, 200_000, 0, "GARDEN", "",
                "Kill pests before they slow your crops.", List.of(), List.of("(?i).*YUCK! You found a .* on your plot.*")));

        // --- experiments / misc dailies ---
        TASKS.add(daily("experiments", "Experiments", "Enchanting", "enchanting_table", 3, 10, 300_000, 0, "", "",
                "Superpairs, Chronomatron and Ultrasequencer at the Experimentation Table.",
                List.of("chat.experiments.compact.message"), List.of(".*You claimed the .* rewards!.*")));
        TASKS.add(daily("bits", "Spend Bits", "Economy", "cookie", 1, 2, 0, 0, "HUB", "Elizabeth",
                "Turn Bits into coins at the Community Shop (Elizabeth). Ticks when your Bits actually go down.", List.of(), List.of()));
        TASKS.add(daily("daily_reward", "Hypixel daily reward", "Economy", "gold_nugget", 1, 1, 0, 0, "", "",
                "Claim the network daily reward from the Delivery Man in any lobby.", List.of(), List.of(".*You claimed your daily reward.*")));
        TASKS.add(daily("chocolate", "Chocolate Factory", "Economy", "cocoa_beans", 1, 3, 0, 5, "", "",
                "Collect chocolate and buy the best rabbit upgrade.", List.of(), List.of()));
        TASKS.add(daily("rift", "Rift time", "Rift", "ender_eye", 1, 20, 300_000, 5, "THE_RIFT", "",
                "Use your Rift time: Motes, Enigma Souls, Timecharms.", List.of(), List.of()));
        TASKS.add(daily("slayer", "Slayer quests", "Combat", "rotten_flesh", 3, 25, 1_200_000, 0, "HUB", "Maddox the Slayer",
                "Do your best-paying slayer a few times.", List.of(), List.of("(?i).*SLAYER QUEST COMPLETE!.*")));
        TASKS.add(daily("dungeons", "Dungeon runs", "Dungeons", "skeleton_skull", 3, 30, 2_000_000, 0, "DUNGEON_HUB", "Mort",
                "Run your best floor for chests and Catacombs XP.", List.of(), List.of("(?i).*(?:The Catacombs|Master Mode).* - Floor .*")));
        TASKS.add(daily("bazaar_orders", "Claim Bazaar & AH", "Economy", "gold_ingot", 1, 2, 0, 0, "HUB", "Bazaar",
                "Claim filled Bazaar orders and sold auctions.", List.of(),
                List.of("(?i)^\\[Bazaar\\] Claimed .*", "(?i)^You collected .* coins from selling .*", "(?i)^You claimed .* coins from .*auction.*", "(?i)collected an expired auction", "(?i)^\\[Bazaar\\] Your .* (?:Buy Order|Sell Offer) .* filled")));

        // --- galatea ---
        TASKS.add(daily("hunting_shards", "Hunt shards", "Hunting", "lead", 20, 30, 1_000_000, 0, "", "",
                "Catch 20 shards (Invisibugs, Glacite Walkers...). Each catch counts.", List.of(),
                List.of("(?i)^You caught (?:an? |x[\\d,]+ ).*Shards?!", "(?i)^You caught yourself an? .*shard was sent")));
        TASKS.add(daily("starlyn", "Starlyn's contest", "Foraging", "oak_sapling", 1, 20, 300_000, 0, "GALATEA", "Starlyn",
                "Join Starlyn's foraging contest on Galatea and claim the rewards.", List.of(),
                List.of("(?i)STARLYN CONTEST REWARDS CLAIMED")));

        // --- weekly / monthly ---
        TASKS.add(new TaskDef("museum", "Museum donations", "Collection", "item_frame", Period.WEEKLY, 1, 5, 0, 10, false, "PRIVATE_ISLAND", "",
                "Donate new items to the Museum for SkyBlock XP and milestones.", List.of(), List.of(), ""));
        TASKS.add(new TaskDef("bestiary", "Bestiary milestones", "Combat", "zombie_head", Period.WEEKLY, 1, 30, 0, 10, false, "", "",
                "Push the next Bestiary milestone; each gives SkyBlock XP.", List.of(), List.of(), ""));
        TASKS.add(new TaskDef("collections", "Collection milestones", "Collection", "chest", Period.WEEKLY, 1, 30, 0, 10, false, "", "",
                "Unlock the next collection tiers you are close to.", List.of(), List.of(), ""));
        TASKS.add(new TaskDef("bingo", "Bingo goals", "Events", "paper", Period.MONTHLY, 1, 60, 0, 0, false, "HUB", "Bingo",
                "Monthly Bingo event goals and rewards.", List.of(), List.of(), ""));

        // --- events (only while active) ---
        TASKS.add(new TaskDef("hoppity_eggs", "Hoppity eggs", "Events", "egg", Period.SB_DAY, 3, 5, 0, 2, true, "", "",
                "Find the Breakfast, Lunch and Dinner chocolate eggs this SkyBlock day.", List.of(), List.of(".*HOPPITY'S HUNT You found a .*"), "hoppity"));
        TASKS.add(new TaskDef("spooky", "Spooky Festival", "Events", "jack_o_lantern", Period.EVENT, 1, 20, 1_000_000, 0, true, "HUB", "",
                "Kill Trick or Treat mobs for Green and Purple Candy.", List.of(), List.of(), "spooky"));
        TASKS.add(new TaskDef("jerry", "Season of Jerry", "Events", "snowball", Period.EVENT, 1, 20, 1_000_000, 0, true, "WINTER", "St. Jerry",
                "Defend Jerry's Workshop for gifts.", List.of(), List.of(), "jerry"));
        TASKS.add(new TaskDef("new_year", "New Year Cake", "Events", "cake", Period.EVENT, 1, 2, 0, 0, true, "HUB", "",
                "Grab your free New Year Cake.", List.of(), List.of("(?i).*You claimed a New Year Cake.*"), "new_year"));
        TASKS.add(new TaskDef("zoo", "Traveling Zoo", "Events", "lead", Period.EVENT, 1, 3, 0, 0, false, "HUB", "Oringo",
                "Check Oringo's pets.", List.of(), List.of(), "zoo_summer"));
        TASKS.add(new TaskDef("vote", "Vote for Mayor", "Events", "paper", Period.SB_YEAR, 1, 2, 0, 0, true, "HUB", "",
                "Vote at the Election Booth.", List.of(), List.of("(?i).*You (?:cast|changed) your vote.*"), "election"));
        TASKS.add(new TaskDef("dark_auction", "Dark Auction", "Events", "ender_chest", Period.SB_DAY, 1, 5, 0, 0, false, "HUB", "Sirius",
                "Check today's Dark Auction items.", List.of(), List.of(), "dark_auction"));
    }

    public static TaskDef byId(String id) {
        for (TaskDef t : TASKS) if (t.id().equals(id)) return t;
        return null;
    }
}
