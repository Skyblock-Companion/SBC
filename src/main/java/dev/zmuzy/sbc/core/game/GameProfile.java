// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core.game;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Pets;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.Worth;
import dev.zmuzy.sbc.item.StorageCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// your own profile with no api key: Profile.current filled from what the game showed (see GameRead), so
// everything reading it works where it can and says what to open where it cant
public final class GameProfile {
    // what Profile.Data.knows gets asked. never known from the game = stuff the game doesnt show anywhere
    public static final String SB_XP = "sbXp", PURSE = "purse", BANK = "bank", SKILLS = "skills", SKILL_AVERAGE = "skillAverage", SLAYERS = "slayers",
            CATACOMBS = "catacombs", CLASSES = "classes", FLOORS = "floors", PETS = "pets", ACCESSORIES = "accessories", MAGICAL_POWER = "magicalPower",
            COLLECTIONS = "collections", HOTM = "hotm", HOTF = "hotf", POWDER = "powder", MUSEUM = "museum", ESSENCE = "essence", TROPHY_FISH = "trophyFish",
            COPPER = "copper", MEDALS = "medals", MOTES = "motes", ENIGMA = "enigmaSouls", FAIRY_SOULS = "fairySouls", FACTION = "faction", SACKS = "sacks",
            STORAGE = "storage", EQUIPMENT = "equipment", NETWORTH = "networth", BESTIARY = "bestiary", INVENTORY = "inventory",
            // hypixel shows these nowhere in game, they stay api only
            KILLS = "kills", SECRETS = "secrets", BESTS = "bests", MASTER_RUNS = "masterRuns", DOJO = "dojo", KUUDRA = "kuudra", AUCTIONS = "auctions", CRYSTALS = "crystals",
            MINIONS_CRAFTED = "minionsCrafted", FIRST_JOIN = "firstJoin";

    // everything a player can do something about, in the order the hints are worth following
    public static final String[] CAN_READ = {SB_XP, SKILLS, PETS, ACCESSORIES, MAGICAL_POWER, CATACOMBS, CLASSES, SLAYERS, COLLECTIONS, HOTM, HOTF, POWDER, MUSEUM,
            SACKS, STORAGE, EQUIPMENT, ESSENCE, FLOORS, BESTIARY, TROPHY_FISH, MEDALS, COPPER, MOTES, FACTION, ENIGMA, FAIRY_SOULS};

    private static final Map<String, String> HINTS = new LinkedHashMap<>();

    static {
        HINTS.put(SB_XP, "Open the SkyBlock Menu once.");
        HINTS.put(PURSE, "Your purse shows on the scoreboard.");
        HINTS.put(BANK, "Show the Bank widget in /tab.");
        HINTS.put(SKILLS, "Open Your Skills in the SkyBlock Menu once.");
        HINTS.put(SKILL_AVERAGE, "Open Your Skills in the SkyBlock Menu once.");
        HINTS.put(SLAYERS, "Finish a Slayer boss: the chat says its level.");
        HINTS.put(CATACOMBS, "Open Dungeoneering in Your Skills once.");
        HINTS.put(CLASSES, "Open Dungeon Classes once.");
        HINTS.put(FLOORS, "Open Boss Collections in Collections once.");
        HINTS.put(PETS, "Open your Pets menu once, every page.");
        HINTS.put(ACCESSORIES, "Open your Accessory Bag once, every page.");
        HINTS.put(MAGICAL_POWER, "Open Your Bags in the SkyBlock Menu once.");
        HINTS.put(COLLECTIONS, "Open Collections and each of its categories once.");
        HINTS.put(HOTM, "Open Heart of the Mountain, at its locked tiers.");
        HINTS.put(HOTF, "Open Heart of the Forest, at its locked tiers.");
        HINTS.put(POWDER, "Open Heart of the Mountain, at its locked tiers.");
        HINTS.put(MUSEUM, "Open your Museum's pages once.");
        HINTS.put(SACKS, "Open your Sacks once.");
        HINTS.put(STORAGE, "Open your Ender Chest and backpacks once.");
        HINTS.put(EQUIPMENT, "Open Stats & Equipment once.");
        HINTS.put(ESSENCE, "Open an Essence Shop once, or show Essence in /tab.");
        HINTS.put(BESTIARY, "Open the Bestiary once.");
        HINTS.put(TROPHY_FISH, "Talk to Odger and open Trophy Fish once.");
        HINTS.put(MEDALS, "Open Anita's shop once.");
        HINTS.put(COPPER, "Visit your Garden once.");
        HINTS.put(MOTES, "Enter the Rift once.");
        HINTS.put(FACTION, "Visit the Crimson Isle once.");
        HINTS.put(ENIGMA, "Open Enigma Souls in the Rift Guide once.");
        HINTS.put(FAIRY_SOULS, "Show Fairy Souls in /tab on your islands.");
        HINTS.put(NETWORTH, "Open your Pets, Accessory Bag, Sacks and storage once.");
    }

    // what to open once so the game shows this
    public static String hint(String key) {
        return HINTS.getOrDefault(key, "");
    }

    // whats not known yet but can be, each hint once
    public static List<String> missing(Profile.Data d) {
        List<String> out = new ArrayList<>();
        if (d == null) return out;
        for (String key : CAN_READ) {
            if (d.knows(key)) continue;
            String h = hint(key);
            if (!h.isEmpty() && !out.contains(h)) out.add(h);
        }
        return out;
    }

    // --- store -> Profile.Data ---

    // what this needs from the rest of the mod, passed in so a test can build a profile with no game
    public record Live(String player, String uuid, String profileName, String gameMode, String profileId, List<Profile.Item> armor, List<Profile.Item> inventory,
                       Map<String, List<Profile.Item>> storage, Map<String, Long> sacks, Map<String, long[]> minedPowder, Store.CrimsonProfile crimson,
                       int fairySouls, Set<String> museum) {}

    private static final String[] AVERAGED = {"Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting", "Alchemy", "Taming", "Carpentry", "Hunting"};
    // order of the api paths legend: "Rev/Tara/Sven/Void/Inferno/Rift"
    private static final String[][] SLAYER_ORDER = {{"Revenant", "zombie"}, {"Tarantula", "spider"}, {"Sven", "wolf"}, {"Voidgloom", "enderman"}, {"Inferno", "blaze"}, {"Riftstalker", "vampire"}};

    // from skyhanni SkillType.maxLevel, the 2 it doesnt list are the api paths
    public static int skillCap(String s) {
        return switch (s) {
            case "Farming", "Mining", "Combat", "Enchanting", "Taming" -> 60;
            case "Foraging" -> 57;
            case "Runecrafting", "Social" -> 25;
            default -> 50;
        };
    }

    // one level from what was read: "Max" only if the game said so or its at the cap with nothing else read. the
    // bar wins over the cap bc the bar is what was read. progress -1 = only the level was read
    static Profile.Level level(Seen.Skill sk, long[] table, int cap, boolean ownTable) {
        int lvl = sk.level;
        long base = table[Math.max(0, Math.min(lvl, table.length - 1))];
        if (sk.max) return new Profile.Level(lvl, 1, Math.max(sk.totalXp, ownTable ? 0 : base), 0);
        if (sk.currentXpMax > 0) {
            double p = Math.min(1, (double) sk.currentXp / sk.currentXpMax);
            return new Profile.Level(lvl, p, ownTable ? sk.currentXp : base + sk.currentXp, Math.max(0, sk.currentXpMax - sk.currentXp));
        }
        if (sk.percent >= 0 && !ownTable && lvl + 1 < table.length) {
            double span = table[lvl + 1] - table[lvl], p = sk.percent / 100;
            return new Profile.Level(lvl, p, base + span * p, span * (1 - p));
        }
        if (sk.percent >= 0) return new Profile.Level(lvl, sk.percent / 100, 0, 1);
        if (lvl >= cap) return new Profile.Level(lvl, 1, Math.max(sk.totalXp, ownTable ? 0 : base), 0);
        return new Profile.Level(lvl, -1, ownTable ? 0 : base, !ownTable && lvl + 1 < table.length ? table[lvl + 1] - table[lvl] : 1);
    }

    private static Profile.Level slayer(long[] read, int[] thresholds) {
        int lvl = (int) read[0];
        double prev = lvl <= 0 ? 0 : thresholds[Math.min(lvl, thresholds.length) - 1];
        if (lvl >= thresholds.length) return new Profile.Level(lvl, 1, prev, 0);
        if (read[1] < 0) return new Profile.Level(lvl, -1, prev, thresholds[lvl] - prev);
        double next = thresholds[lvl], xp = next - read[1];
        return new Profile.Level(lvl, Math.max(0, (xp - prev) / (next - prev)), xp, read[1]);
    }

    // hotm/hotf: just the tier + everything it takes to get there
    private static Profile.Level tier(int tier) {
        return new Profile.Level(tier, -1, Profile.HOTM_XP[Math.max(0, Math.min(tier - 1, Profile.HOTM_XP.length - 1))], 0);
    }

    private static Profile.Item item(Seen.Item it) {
        return new Profile.Item(it.name, it.lore == null ? List.of() : it.lore, it.id, it.count, it.vanilla, null, -1, false, it.value);
    }

    // pure: everything known about a profile as the Data every reader already reads
    public static Profile.Data build(Seen s, Live l) {
        Profile.Data d = new Profile.Data();
        d.fromGame = true;
        d.extras.complete(null);
        d.player = l.player();
        d.uuid = l.uuid();
        d.profileName = l.profileName();
        d.gameMode = l.gameMode();
        d.profileId = l.profileId();
        Set<String> k = d.known;

        d.sbXp = s.totalSkyBlockXp;
        if (s.totalSkyBlockXp >= 0) k.add(SB_XP);
        d.purse = s.purse;
        if (s.purse >= 0) k.add(PURSE);
        d.bank = s.bank;
        if (s.bank >= 0) k.add(BANK);

        for (Map.Entry<String, Seen.Skill> e : s.skills.entrySet()) {
            d.skills.put(e.getKey(), level(e.getValue(), Profile.SKILL_XP, skillCap(e.getKey()), GameRead.ownTable(e.getKey())));
        }
        if (!d.skills.isEmpty()) k.add(SKILLS);
        // same average as the api path: all 10 that count. some unread = the skills widgets own number if it has one
        double sum = 0;
        int counted = 0;
        for (String name : AVERAGED) {
            Profile.Level lvl = d.skills.get(name);
            if (lvl != null) {
                sum += lvl.level() + Math.max(0, lvl.progress());
                counted++;
            }
        }
        d.skillAverage = counted == AVERAGED.length ? sum / counted : s.tabSkillAverage;
        if (d.skillAverage >= 0) k.add(SKILL_AVERAGE);

        for (String[] names : SLAYER_ORDER) {
            long[] read = s.slayers.get(names[0]);
            if (read != null) d.slayers.put(names[0], slayer(read, Profile.SLAYER_XP.get(names[1])));
        }
        if (!d.slayers.isEmpty()) k.add(SLAYERS);

        if (s.catacombs != null) {
            d.catacombs = level(s.catacombs, Profile.DUNGEON_XP, 50, false);
            k.add(CATACOMBS);
        }
        for (Map.Entry<String, Seen.Skill> e : s.classes.entrySet()) d.classes.put(e.getKey(), level(e.getValue(), Profile.DUNGEON_XP, 50, false));
        if (!d.classes.isEmpty()) k.add(CLASSES);
        d.selectedClass = s.selectedClass;
        // boss collections counts kills, floor + master together. no line for runs so floorCompletions stays empty
        d.bossKills.putAll(s.bossKills);
        if (!s.bossKills.isEmpty()) k.add(FLOORS);

        List<Seen.Pet> pets = s.allPets();
        for (Seen.Pet p : pets) {
            double exp = p.exp >= 0 ? p.exp : Pets.xpAtLevel(p.tier, p.level);
            String held = heldId(p);
            d.pets.add(new Profile.Pet(p.type, p.name, p.tier, p.level, p.active, held, exp, AuctionPrices.pet(p.type, p.tier, exp, held)));
        }
        d.pets.sort((a, b) -> a.active() != b.active() ? Boolean.compare(b.active(), a.active()) : Double.compare(b.value(), a.value()));
        if (!pets.isEmpty()) k.add(PETS);

        if (!l.armor().isEmpty()) d.inventories.put("Armor", new ArrayList<>(l.armor()));
        if (!s.equipment.isEmpty()) {
            List<Profile.Item> equipment = new ArrayList<>();
            for (Seen.Item it : s.equipment) equipment.add(item(it));
            d.inventories.put("Equipment", equipment);
            k.add(EQUIPMENT);
        }
        if (!l.inventory().isEmpty()) {
            d.inventories.put("Inventory", new ArrayList<>(l.inventory()));
            k.add(INVENTORY);
        }
        if (!l.storage().isEmpty()) {
            d.inventories.putAll(l.storage());
            k.add(STORAGE);
        }
        if (!s.accessoryPages.isEmpty()) {
            List<Profile.Item> bag = new ArrayList<>();
            s.accessoryPages.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> e.getValue().forEach(it -> bag.add(item(it))));
            d.inventories.put("Accessories", bag);
            k.add(ACCESSORIES);
        }
        if (s.magicalPower >= 0) {
            d.selectedPower = s.selectedPower;
            d.magicalPower = s.magicalPower;
            k.add(MAGICAL_POWER);
        }
        d.collections.putAll(s.collections);
        if (!s.collections.isEmpty()) k.add(COLLECTIONS);
        if (s.hotmTier > 0) {
            d.hotm = tier(s.hotmTier);
            k.add(HOTM);
        }
        if (s.hotfTier > 0) {
            d.hotf = tier(s.hotfTier);
            d.hotfXp = d.hotf.xp();
            k.add(HOTF);
        }
        d.hotmNodes.putAll(s.hotmPerks);
        d.hotfNodes.putAll(s.hotfPerks);
        d.hotmOff.addAll(s.hotmOff);
        d.hotfOff.addAll(s.hotfOff);
        d.hotmAbility = s.hotmAbility;
        d.hotfAbility = s.hotfAbility;
        d.hotmTokensLeft = s.hotmTokens[0];
        d.hotmTokensSpent = s.hotmTokens[1];
        d.hotfTokensLeft = s.hotfTokens[0];
        d.hotfTokensSpent = s.hotfTokens[1];
        d.mithrilPowder = powder(s, l, "mithril");
        d.gemstonePowder = powder(s, l, "gemstone");
        d.glacitePowder = powder(s, l, "glacite");
        if (d.mithrilPowder >= 0 || d.gemstonePowder >= 0 || d.glacitePowder >= 0) k.add(POWDER);

        if (l.museum() != null && !l.museum().isEmpty()) {
            d.museum.addAll(l.museum());
            k.add(MUSEUM);
        }
        for (Map.Entry<String, Long> e : s.essence.entrySet()) {
            d.essence.put(e.getKey(), e.getValue());
            d.essenceValue += e.getValue() * Prices.sell("ESSENCE_" + e.getKey().toUpperCase(Locale.ROOT));
        }
        if (!s.essence.isEmpty()) k.add(ESSENCE);
        for (Map.Entry<String, Map<String, Integer>> e : s.trophyFish.entrySet()) {
            int total = e.getValue().values().stream().mapToInt(Integer::intValue).sum();
            if (total > 0) d.trophyFish.put(Profile.cap(e.getKey()), total);
        }
        if (!s.trophyFish.isEmpty()) k.add(TROPHY_FISH);
        d.medals.putAll(s.medals);
        if (!s.medals.isEmpty()) k.add(MEDALS);
        if (s.copper >= 0) {
            d.copper = (int) s.copper;
            k.add(COPPER);
        }
        if (s.motes >= 0) {
            d.motes = s.motes;
            k.add(MOTES);
        }
        if (l.crimson() != null && l.crimson().faction != null) {
            d.faction = Profile.cap(l.crimson().faction);
            k.add(FACTION);
        }
        if (l.crimson() != null) {
            d.magesRep = l.crimson().reputation.getOrDefault("MAGE", 0);
            d.barbRep = l.crimson().reputation.getOrDefault("BARBARIAN", 0);
        }
        if (s.enigmaRead) {
            d.enigmaSouls = s.enigmaSouls.size();
            k.add(ENIGMA);
        }
        if (l.fairySouls() > 0) {
            d.fairySouls = l.fairySouls();
            k.add(FAIRY_SOULS);
        }
        d.bestiaryMilestone = s.bestiaryMilestone;
        if (s.bestiaryMilestone >= 0) k.add(BESTIARY);

        for (Map.Entry<String, Long> e : l.sacks().entrySet()) d.sacksValue += e.getValue() * Prices.sell(e.getKey());
        if (!l.sacks().isEmpty()) k.add(SACKS);

        // networth by source, same sources + names as the api path. only real networth once the big ones are read
        worth(d);
        if (k.contains(PETS) && k.contains(ACCESSORIES) && k.contains(SACKS) && k.contains(STORAGE)) k.add(NETWORTH);
        return d;
    }

    // never null, Profile.Pet readers call heldItem().isEmpty(). an item the lore named before the repo loaded
    // gets looked up again, an unknown one keeps its name as an id so it still shows
    static String heldId(Seen.Pet p) {
        if (p.held != null && !p.held.isEmpty()) return p.held;
        if (p.heldName == null || p.heldName.isEmpty()) return "";
        String id = ItemRepo.idForName(p.heldName);
        return id != null ? id : p.heldName.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    // what the acc helpers count as yours: from game only this profiles bag, with the api also what was seen in any bag
    public static Set<String> ownedAccessories(Profile.Data d) {
        Set<String> have = new java.util.HashSet<>();
        if (d == null || !d.fromGame) have.addAll(Store.I.accessoriesSeen);
        List<Profile.Item> bag = d == null ? null : d.inventories.get("Accessories");
        if (bag != null) for (Profile.Item it : bag) if (it != null && it.sbId() != null && !it.sbId().isEmpty()) have.add(it.sbId());
        return have;
    }

    private static long powder(Seen s, Live l, String type) {
        long[] heart = s.powder.get(type);
        if (heart != null && heart[0] >= 0 && heart[1] >= 0) return heart[0] + heart[1];
        long[] mined = l.minedPowder() == null ? null : l.minedPowder().get(type.toUpperCase(Locale.ROOT));
        if (mined != null && mined[1] > 0) return mined[1];
        if (mined != null && mined[0] > 0) return mined[0];
        return heart != null && heart[0] >= 0 ? heart[0] : -1;
    }

    private static void worth(Profile.Data d) {
        if (d.purse > 0) d.worth.put("Purse", d.purse);
        if (d.bank > 0) d.worth.put("Bank", d.bank);
        d.worth.put("Sacks", d.sacksValue);
        d.worth.put("Essence", d.essenceValue);
        for (Map.Entry<String, List<Profile.Item>> e : d.inventories.entrySet()) {
            String group = e.getKey().startsWith("Backpack") ? "Backpacks" : e.getKey();
            double v = 0;
            for (Profile.Item it : e.getValue()) if (it != null) v += it.value();
            d.worth.merge(group, v, Double::sum);
        }
        d.worth.put("Pets", d.pets.stream().mapToDouble(Profile.Pet::value).sum());
        d.worth.entrySet().removeIf(e -> e.getValue() <= 0);
    }

    // --- game -> store ---

    // stuff that changes all the time isnt a reason to rebuild everything
    private static final Gson HASH = new GsonBuilder().setExclusionStrategies(new ExclusionStrategy() {
        @Override public boolean shouldSkipField(FieldAttributes f) {
            String n = f.getName();
            return f.getDeclaringClass() == Seen.class && (n.equals("purse") || n.equals("bank") || n.equals("copper") || n.equals("motes"));
        }

        @Override public boolean shouldSkipClass(Class<?> c) {
            return false;
        }
    }).create();

    static int ticks;
    private static int builtHash, savedHash, inventoryHash, menuHash, settleUntil;
    private static String builtFor = "", liveFor = "", storageFrom = "";
    // the 2 server join lines, whichever comes first
    private static String joinedName = "", joinedId = "";
    private static long joinedAt;
    private static final Pattern PROFILE_ID = Pattern.compile("^Profile ID: ([0-9a-fA-F-]+)$");
    private static long storageReadAt;
    private static Map<String, List<Profile.Item>> storageItems = Map.of();
    private static volatile boolean rebuild;
    private static List<String> lastTab = List.of();
    // 3 sec of looks after a profile switch
    private static final int SETTLE_TICKS = 60;

    private GameProfile() {}

    // store of the profile youre on, null till a profile name was read
    public static Seen current() {
        String name = GameState.profileName;
        if (name == null || name.isEmpty()) return null;
        Seen s = Store.I.gameProfiles.get(name);
        if (s != null) return s;
        boolean first = Store.I.gameProfiles.isEmpty();
        s = new Seen();
        if (first) adoptOldData(s);
        Store.I.gameProfiles.put(name, s);
        Store.markDirty();
        return s;
    }

    // old versions kept one shared store for all profiles, it goes to the first profile played
    private static void adoptOldData(Seen s) {
        for (Map.Entry<String, double[]> e : Store.I.observedSkills.entrySet()) {
            Seen.Skill sk = new Seen.Skill();
            sk.level = (int) e.getValue()[0];
            sk.percent = e.getValue()[1];
            s.skills.put(e.getKey(), sk);
        }
        List<Seen.Pet> pets = new ArrayList<>();
        for (Map.Entry<String, Store.SeenPet> e : Store.I.petsSeen.entrySet()) {
            Seen.Pet p = new Seen.Pet();
            p.type = e.getValue().type;
            p.name = e.getValue().name;
            p.tier = e.getValue().tier;
            p.held = e.getValue().held == null ? "" : e.getValue().held;
            p.level = e.getValue().level;
            p.active = e.getValue().active;
            pets.add(p);
        }
        if (!pets.isEmpty()) s.petPages.put(0, pets);
        if (!Store.I.accessoriesSeen.isEmpty()) {
            List<Seen.Item> bag = new ArrayList<>();
            for (String id : Store.I.accessoriesSeen) {
                Seen.Item it = new Seen.Item();
                it.id = id;
                it.name = ItemRepo.coloredName(id);
                bag.add(it);
            }
            s.accessoryPages.put(1, bag);
        }
    }

    // next look rebuilds the profile, for prices that just came in
    public static void requestRebuild() {
        rebuild = true;
    }

    public static void register() {
        Safe.endTick("GameProfile.tick", GameProfile::tick);
        MenuPackets.listen(GameProfile::menuChanged);
        dev.zmuzy.sbc.track.Trackers.onFresh((id, amount) -> {
            Seen s = current();
            if (s != null && GameRead.collected(s, id, amount)) Store.markDirty();
        });
        ChatBus.register(5, "game_profile", null, line -> {
            // from skyhanni HypixelData: no Profile widget in tab = the join line names the profile
            String lower = line.plain().toLowerCase(Locale.ROOT);
            if (lower.startsWith("you are playing on profile:")) {
                String joined = line.plain().substring("you are playing on profile:".length()).replace("(Co-op)", "").trim();
                if (GameState.profileName == null || GameState.profileName.isEmpty()) GameState.profileName = joined;
                joinLine(joined, "", System.currentTimeMillis());
            }
            Matcher id = PROFILE_ID.matcher(line.plain());
            if (id.matches()) joinLine("", id.group(1).replace("-", ""), System.currentTimeMillis());
            Seen s = current();
            if (s != null && GameRead.chat(s, line.plain(), LegacyText.of(line.component()), helmet(), System.currentTimeMillis())) Store.markDirty();
            return ChatBus.PASS;
        });
    }

    // skyblocker Utils reads the same 2 lines. they say which profile a "Profile ID" is (tab only says it later)
    // and StorageCache pages go by that id
    static void joinLine(String name, String id, long now) {
        if (now - joinedAt > 5000) joinedName = joinedId = "";
        joinedAt = now;
        if (!name.isEmpty()) joinedName = name;
        if (!id.isEmpty()) joinedId = id;
        if (joinedName.isEmpty() || joinedId.isEmpty()) return;
        Seen s = Store.I.gameProfiles.computeIfAbsent(joinedName, k -> new Seen());
        if (!joinedId.equals(s.profileId)) {
            s.profileId = joinedId;
            Store.markDirty();
        }
        joinedName = joinedId = "";
    }

    // from ChatHooks: action bar, colors stripped
    public static void onActionBar(String plain) {
        Seen s = current();
        if (s != null) GameRead.actionBar(s, plain, GameState.tab);
    }

    private static String helmet() {
        var player = Minecraft.getInstance().player;
        return player == null ? "" : Gain.sbId(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    private static void tick(Minecraft mc) {
        MenuPackets.tick(mc.gui.screen() instanceof AbstractContainerScreen<?>);
        if (mc.player == null || !GameState.inSkyBlock) return;
        if (++ticks % 10 != 0) return;
        Seen s = current();
        if (s == null) return;
        try {
            live(s, GameState.profileName);
            if (ticks % 40 == 0) {
                // widgets run regexes per line, so only when the list changed
                if (!GameState.tab.equals(lastTab)) {
                    lastTab = GameState.tab;
                    GameRead.tab(s, GameState.tab, mc.player.getGameProfile().name());
                }
                publish(s, mc);
            }
        } catch (Exception e) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("Could not read the profile from the game", e);
        }
    }

    // numbers hypixel only shows in some places: last read sticks and goes straight into whats shown. GameState
    // keeps copper, motes, bank till read again, which might be never on the next island
    static void live(Seen s, String name) {
        if (!name.equals(liveFor)) {
            if (!liveFor.isEmpty()) {
                settleUntil = ticks + SETTLE_TICKS;
                lastTab = List.of();
            }
            liveFor = name;
        }
        if (ticks < settleUntil) {
            GameState.copper = GameState.motes = GameState.bank = -1;
            return;
        }
        boolean changed = false;
        if (GameState.purse >= 0 && GameState.purse != s.purse) {
            s.purse = GameState.purse;
            changed = true;
        }
        if (GameState.bank >= 0 && GameState.bank != s.bank) {
            s.bank = GameState.bank;
            changed = true;
        }
        if (GameState.copper >= 0 && GameState.copper != s.copper) {
            s.copper = GameState.copper;
            changed = true;
        }
        if (GameState.motes >= 0 && GameState.motes != s.motes) {
            s.motes = GameState.motes;
            changed = true;
        }
        if (!changed) return;
        Store.markDirty();
        Profile.Data d = Profile.current;
        if (d == null || !d.fromGame) return;
        d.purse = s.purse;
        d.bank = s.bank;
        d.copper = (int) Math.max(0, s.copper);
        d.motes = Math.max(0, s.motes);
        if (s.purse > 0) d.worth.put("Purse", (double) s.purse);
        if (s.bank > 0) d.worth.put("Bank", (double) s.bank);
        if (s.purse >= 0) d.known.add(PURSE);
        if (s.bank >= 0) d.known.add(BANK);
        if (s.copper >= 0) d.known.add(COPPER);
        if (s.motes >= 0) d.known.add(MOTES);
    }

    // from skyhanni InventoryFullyOpenedEvent + InventoryUpdatedEvent, through MenuPackets
    private static void menuChanged() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !GameState.inSkyBlock) return;
        Seen s = current();
        if (s == null) return;
        try {
            menu(s, mc);
        } catch (Exception e) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("Could not read the menu", e);
        }
    }

    // slot click, from skyhanni PetStorageApi.onSlotClick
    public static void menuClick(AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button, boolean shift) {
        if (!GameState.inSkyBlock || slot == null || slot.getItem().isEmpty()) return;
        Seen s = current();
        if (s == null) return;
        ItemStack st = slot.getItem();
        String title = GameState.strip(screen.getTitle().getString()).trim();
        GameRead.MenuItem item = new GameRead.MenuItem(slotId, LegacyText.name(st), LegacyText.lore(st), Gain.sbId(st), st.getCount(), 0, BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), petInfo(st));
        if (GameRead.petClick(s, title, item, button == 1, shift)) Store.markDirty();
    }

    private static void menu(Seen s, Minecraft mc) {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !(cs.getMenu() instanceof ChestMenu chest)) {
            menuHash = 0;
            return;
        }
        String title = GameState.strip(cs.getTitle().getString()).trim();
        int n = Math.min(chest.getRowCount() * 9, chest.slots.size());
        int hash = title.hashCode();
        boolean any = false;
        for (int i = 0; i < n; i++) {
            ItemStack st = chest.slots.get(i).getItem();
            any |= !st.isEmpty();
            hash = 31 * hash + (st.isEmpty() ? 0 : st.getHoverName().getString().hashCode() + st.getCount() + (st.get(DataComponents.LORE) == null ? 0 : st.get(DataComponents.LORE).lines().size()));
        }
        if (!any || hash == menuHash) return;
        menuHash = hash;
        List<GameRead.MenuItem> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = chest.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            items.add(new GameRead.MenuItem(i, LegacyText.name(st), LegacyText.lore(st), Gain.sbId(st), st.getCount(), worth(st), BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), petInfo(st)));
        }
        GameRead.menu(s, title, items, "CATACOMBS".equals(GameState.island));
    }

    // from skyhanni getPetInfo: the json a pet stack carries, empty if none
    private static String petInfo(ItemStack st) {
        var data = st.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("petInfo", "");
    }

    private static double worth(ItemStack st) {
        var data = st.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        try {
            return Worth.item(data.copyTag(), st.getCount());
        } catch (Exception e) {
            return 0;
        }
    }

    // rebuilds Profile.current when something changed, only with no key and only over its own last result
    private static void publish(Seen s, Minecraft mc) {
        int hash = HASH.toJson(s).hashCode();
        var player = mc.player;
        var inv = player.getInventory();
        int invHash = 1;
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            invHash = 31 * invHash + (st.isEmpty() ? 0 : Gain.sbId(st).hashCode() + st.getCount());
        }
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = player.getItemBySlot(slot);
            invHash = 31 * invHash + (st.isEmpty() ? 0 : Gain.sbId(st).hashCode());
        }
        // saving goes by what was saved last. with a key none of this runs and it must not write the file every minute
        if (dirtied(hash)) Store.markDirty();
        boolean changed = hash != builtHash;
        if (Profile.serverUp() && Profile.current != null && Profile.current.fromGame) Profile.retrySelf();
        if (Profile.serverUp() || Profile.current != null && !Profile.current.fromGame) return;
        String name = GameState.profileName;
        long now = System.currentTimeMillis();
        // StorageCache holds pages of the profile its last "Profile ID" named, before that id is this profiles theyre
        // someone elses
        String storageIs = s.profileId.isEmpty() || !s.profileId.equals(StorageCache.profileId()) ? "" : s.profileId;
        boolean refreshStorage = now - storageReadAt > 60_000 || !storageIs.equals(storageFrom);
        if (!changed && invHash == inventoryHash && !rebuild && !refreshStorage && name.equals(builtFor) && Profile.current != null) return;
        builtHash = hash;
        inventoryHash = invHash;
        builtFor = name;
        rebuild = false;
        if (refreshStorage) {
            storageReadAt = now;
            storageFrom = storageIs;
            storageItems = storageIs.isEmpty() ? Map.of() : storage();
        }
        List<Profile.Item> armor = new ArrayList<>(), main = new ArrayList<>();
        boolean anyArmor = false;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack st = player.getItemBySlot(slot);
            anyArmor |= !st.isEmpty();
            armor.add(Profile.Item.of(st, worth(st)));
        }
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            main.add(Profile.Item.of(st, worth(st)));
        }
        Map<String, Long> sacks = new HashMap<>();
        Store.SackProfile sp = Store.I.sacks.get(name);
        if (sp != null) for (Map.Entry<String, int[]> e : sp.contents.entrySet()) if (e.getValue().length > 0) sacks.put(e.getKey(), (long) e.getValue()[0]);
        Store.MiningProfile mp = Store.I.mining.get(name);
        Store.FairyProfile fp = Store.I.fairySouls.get(name);
        int souls = fp == null ? 0 : fp.totalFound.values().stream().mapToInt(Integer::intValue).sum();
        Profile.current = build(s, new Live(player.getGameProfile().name(), player.getUUID().toString().replace("-", ""), name, GameState.profileType,
                s.profileId, anyArmor ? armor : List.of(), main, storageItems, sacks, mp == null ? Map.of() : mp.powder, Store.I.crimson.get(name), souls,
                dev.zmuzy.sbc.ui.panels.MuseumData.donated()));
    }

    // true once per new state of whatd be saved
    static boolean dirtied(int hash) {
        if (hash == savedHash) return false;
        savedHash = hash;
        return true;
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    // ender chest + backpacks like StorageCache remembers them
    private static Map<String, List<Profile.Item>> storage() {
        Map<String, List<Profile.Item>> out = new LinkedHashMap<>();
        List<Profile.Item> ender = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            StorageCache.Page page = StorageCache.page(true, i);
            if (page == null) continue;
            for (ItemStack st : page.items()) ender.add(Profile.Item.of(st, worth(st)));
        }
        if (!ender.isEmpty()) out.put("Ender Chest", ender);
        for (int i = 1; i <= 18; i++) {
            StorageCache.Page page = StorageCache.page(false, i);
            if (page == null) continue;
            List<Profile.Item> list = new ArrayList<>();
            for (ItemStack st : page.items()) list.add(Profile.Item.of(st, worth(st)));
            out.put("Backpack " + i, list);
        }
        return out;
    }
}
