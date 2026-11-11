// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// profile viewer data. goes through our server (ProfileServer) which holds the hypixel key and loads the full
// profile of you or anyone you search (skills, slayers, dungeons, pets, inventories...)
public final class Profile {
    public static final long[] SKILL_XP = {0, 50, 175, 375, 675, 1175, 1925, 2925, 4425, 6425, 9925, 14925, 22425, 32425, 47425, 67425,
            97425, 147425, 222425, 322425, 522425, 822425, 1222425, 1722425, 2322425, 3022425, 3822425, 4722425, 5722425, 6822425,
            8022425, 9322425, 10722425, 12222425, 13822425, 15522425, 17322425, 19222425, 21222425, 23322425, 25522425, 27822425,
            30222425, 32722425, 35322425, 38072425, 40972425, 44072425, 47472425, 51172425, 55172425, 59472425, 64072425, 68972425,
            74172425, 79672425, 85472425, 91572425, 97972425, 104672425, 111672425};
    public static final long[] DUNGEON_XP = {0, 50, 125, 235, 395, 625, 955, 1425, 2095, 3045, 4385, 6275, 8940, 12700, 17960, 25340,
            35640, 50040, 70040, 97640, 135640, 188140, 259640, 356640, 488640, 668640, 911640, 1239640, 1684640, 2284640, 3084640,
            4149640, 5559640, 7459640, 9959640, 13259640, 17559640, 23159640, 30359640, 39559640, 51559640, 66559640, 85559640,
            109559640, 139559640, 177559640, 225559640, 285559640, 360559640, 453559640, 569809640};
    // neus leveling.json added up: runecrafting_xp + social have their own tables
    static final long[] RUNECRAFTING_XP = {0, 50, 150, 275, 435, 635, 885, 1200, 1600, 2100, 2725, 3510, 4510, 5760, 7360, 9360, 11825, 14950, 18950, 23950, 30150, 37950, 47750, 59950, 75250, 94300};
    static final long[] SOCIAL_XP = {0, 50, 150, 300, 550, 1050, 1800, 2800, 4050, 5550, 7550, 10050, 13050, 16800, 21300, 27300, 35300, 45300, 57800, 72800, 92800, 117800, 147800, 182800, 222800, 272800};
    public static final long[] HOTM_XP = {0, 3000, 12000, 37000, 97000, 197000, 347000, 557000, 847000, 1247000};
    public static final String[] SKILLS = {"FARMING", "MINING", "COMBAT", "FORAGING", "FISHING", "ENCHANTING", "ALCHEMY", "TAMING",
            "CARPENTRY", "HUNTING", "RUNECRAFTING", "SOCIAL"};
    public static final Map<String, int[]> SLAYER_XP = Map.of(
            "zombie", new int[]{5, 15, 200, 1000, 5000, 20000, 100000, 400000, 1000000},
            "spider", new int[]{5, 25, 200, 1000, 5000, 20000, 100000, 400000, 1000000},
            "wolf", new int[]{10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
            "enderman", new int[]{10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
            "blaze", new int[]{10, 30, 250, 1500, 5000, 20000, 100000, 400000, 1000000},
            "vampire", new int[]{20, 75, 240, 840, 2400});

    public record Level(int level, double progress, double xp, double toNext) {}

    public record Item(String name, List<String> lore, String sbId, int count, String vanilla, String texture, int color, boolean glint,
                       double value) {
        // the item like the game has it rn, null = empty slot. real icon + tooltip
        public static Item of(net.minecraft.world.item.ItemStack st, double value) {
            if (st == null || st.isEmpty()) return null;
            Item it = new Item(LegacyText.name(st), LegacyText.lore(st), Gain.sbId(st), st.getCount(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), null, -1, false, value);
            // put() would keep an equal older key and the entry would go with the older Data
            TIPS.remove(it);
            TIPS.put(it, st.copy());
            return it;
        }

        // icon built like the real item so packs show their sb texture
        public net.minecraft.world.item.ItemStack stack() {
            var st = TIPS.get(this);
            if (st != null) return st;
            st = ItemIcons.of(sbId, vanilla, texture, color, glint).copy();
            try {
                st.setCount(Math.max(1, count));
                if (name != null && !name.isEmpty()) st.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
                List<net.minecraft.network.chat.Component> ls = new ArrayList<>();
                for (String l : lore) ls.add(net.minecraft.network.chat.Component.literal(l));
                st.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(ls));
            } catch (Exception ignored) {
            }
            TIPS.put(this, st);
            return st;
        }
    }

    // icons with the real name + lore, built once per item so hover shows what the game would
    private static final Map<Item, net.minecraft.world.item.ItemStack> TIPS = new java.util.WeakHashMap<>();

    public record Pet(String type, String name, String tier, int level, boolean active, String heldItem, double exp, double value) {
        // pet tooltip: colored name, level, tier, xp, held item
        public List<String> tooltip() {
            String col = switch (tier == null ? "" : tier.toUpperCase(java.util.Locale.ROOT)) {
                case "COMMON" -> "§f"; case "UNCOMMON" -> "§a"; case "RARE" -> "§9"; case "EPIC" -> "§5"; case "LEGENDARY" -> "§6";
                case "MYTHIC" -> "§d"; default -> "§7";
            };
            List<String> l = new ArrayList<>();
            l.add(col + name);
            l.add("§8Level " + level + " · " + (tier == null ? "" : tier.charAt(0) + tier.substring(1).toLowerCase(java.util.Locale.ROOT)));
            l.add("§7Total XP: §e" + Prices.coins((long) exp));
            if (heldItem != null && !heldItem.isEmpty()) l.add("§7Held item: §f" + ItemRepo.coloredName(heldItem));
            if (active) l.add("§aActive pet");
            return l;
        }
    }

    // floor pbs in ms, 0 = never done
    public record Best(long s, long sPlus, long any, int score) {}

    // one profile of a player
    public record Ref(String id, String name, String mode, boolean shown) {}

    public static final class Data {
        // read from what the game showed (core/game/GameProfile) not the api, only known() stuff is known
        public boolean fromGame;
        // when the server's answer was read. networth is worked out then, so an old one is loaded again
        public long loadedAt = System.currentTimeMillis();
        public final java.util.Set<String> known = java.util.concurrent.ConcurrentHashMap.newKeySet();
        // bestiary milestone from the menu, -1 = unknown
        public int bestiaryMilestone = -1;

        // api = everything known. from game = one of GameProfiles names, only once the game showed it
        public boolean knows(String what) {
            return !fromGame || known.contains(what);
        }

        // sb level, -1 = unknown
        public int sbLevel() {
            return sbXp < 0 ? -1 : (int) (sbXp / 100);
        }

        public String player = "", profileName = "", gameMode = "", uuid = "", profileId = "";
        // every profile this player has, to switch between
        public final List<Ref> profiles = new ArrayList<>();
        JsonObject raw;
        // done once the slow part (museum) is in
        public final java.util.concurrent.CompletableFuture<Void> extras = new java.util.concurrent.CompletableFuture<>();
        public int secrets;
        // class they play, "Mage". empty = unknown
        public String selectedClass = "";
        // by floor: "F7", "M7"
        public final Map<String, Best> bests = new LinkedHashMap<>();
        public double sbXp, purse, bank, sacksValue;
        // networth by source (purse, bank, sacks, inventory, pets...) we work out ourselves
        public final Map<String, Double> worth = java.util.Collections.synchronizedMap(new LinkedHashMap<>());
        public double essenceValue;
        // cookie from the api: 1 on, 0 off, -1 unknown
        public int cookieActive = -1;
        public long firstJoin;
        public final Map<String, Long> essence = new LinkedHashMap<>();
        public final Map<String, Integer> trophyFish = new LinkedHashMap<>();
        public int copper;
        public final Map<String, Integer> medals = new LinkedHashMap<>();
        public long motes;
        public int enigmaSouls;
        public long highestCrit;
        public int dojoPoints, kuudraKills;
        public long auctionsEarned, auctionsSpent;
        public int crystalsFound;
        public int fairySouls;
        public final Map<String, Level> skills = new LinkedHashMap<>();
        public final Map<String, Level> slayers = new LinkedHashMap<>();
        public Level catacombs;
        public final Map<String, Level> classes = new LinkedHashMap<>();
        public final Map<String, Integer> floorCompletions = new LinkedHashMap<>();
        // from the game: "F7" -> boss collection kills, floor + its master mode together. not runs
        public final Map<String, Integer> bossKills = new LinkedHashMap<>();
        public final List<Pet> pets = new ArrayList<>();
        public final Map<String, List<Item>> inventories = new LinkedHashMap<>();
        public final Map<String, Long> collections = new LinkedHashMap<>();
        public Level hotm, hotf;
        public double hotfXp;
        public int magicalPower, uniqueMinions;
        public String selectedPower = "";
        // museum item ids (filled a bit after the profile loads)
        public final java.util.Set<String> museum = java.util.concurrent.ConcurrentHashMap.newKeySet();
        public long kills, deaths;
        public long mithrilPowder, gemstonePowder, glacitePowder;
        public String faction = "";
        public int magesRep, barbRep;
        public double skillAverage;
        // perk id -> level for the selected tree slot. from the game = HeartTrees.key of its name, see core/game/HeartTrees
        public final Map<String, Integer> hotmNodes = new LinkedHashMap<>(), hotfNodes = new LinkedHashMap<>();
        // leveled perks that are turned off
        public final java.util.Set<String> hotmOff = new java.util.HashSet<>(), hotfOff = new java.util.HashSet<>();
        public int hotmSlot = 1, hotfSlot = 1;
        // -1 = unknown
        public int hotmTokensSpent = -1, hotfTokensSpent = -1, hotmTokensLeft = -1, hotfTokensLeft = -1;
        public String hotmAbility = "", hotfAbility = "";
        // croesus runs + unclaimed ones, and todays dungeon runs that count as daily. -1 = unknown
        public int croesusRuns = -1, croesusUnclaimed = -1, dailyRuns = -1;
        // your own share of the bank on a coop, -1 = none. bank holds both
        public double personalBank = -1;
        // "Revenant" -> kills per tier, tier 1 first
        public final Map<String, long[]> slayerKills = new LinkedHashMap<>();
        // what stats tuning gives now: "Health" -> 50
        public final Map<String, Double> tuning = new LinkedHashMap<>();
        // whole profiles total, collections is this players share
        public final Map<String, Long> collectionsTotal = new LinkedHashMap<>();
        public final Map<String, Integer> collectionTiers = new LinkedHashMap<>();
        // "WHEAT" -> tiers crafted by anyone on the profile
        public final Map<String, java.util.Set<Integer>> craftedMinions = new LinkedHashMap<>();

        public double computedNetworth() {
            synchronized (worth) {
                return worth.values().stream().mapToDouble(Double::doubleValue).sum();
            }
        }

        public double networth() {
            return computedNetworth();
        }
    }

    public static volatile Data current;
    // who the profile page shows: you or whoever you looked up. everything else reads current, which is always you
    public static volatile Data viewed;
    public static volatile String status = "";
    private static volatile boolean loading;
    private static volatile long lastRetry;

    private Profile() {}

    public static boolean serverUp() {
        return ProfileServer.up();
    }

    // the game fills the profile while the server is down. this asks the server again now and then and its full
    // answer takes over
    public static void retrySelf() {
        long now = System.currentTimeMillis();
        if (loading || now - lastRetry < 5 * 60_000L) return;
        lastRetry = now;
        loadSelf();
    }

    public static void loadSelf() {
        var p = Minecraft.getInstance().player;
        if (p != null) load(p.getUUID().toString().replace("-", ""), p.getGameProfile().name());
    }

    public static void lookup(String name) {
        if (!serverUp()) {
            status = "Looking up other players is not available.";
            return;
        }
        status = "Looking up " + name + "...";
        String n = name.trim();
        Net.getJson("https://api.mojang.com/users/profiles/minecraft/" + n, null)
                .exceptionallyCompose(err -> Net.getJson("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + n, null))
                .whenComplete((j, e) -> {
                    if (e != null || j == null || !j.isJsonObject() || !j.getAsJsonObject().has("id")) {
                        status = "Player not found: " + n;
                        return;
                    }
                    load(j.getAsJsonObject().get("id").getAsString(), j.getAsJsonObject().get("name").getAsString());
                });
    }

    // someones profile for a chat command, doesnt touch what the profile page shows or who you are
    public static void fetch(String name, java.util.function.Consumer<Data> ok, java.util.function.Consumer<String> fail) {
        if (!serverUp()) {
            fail.accept("Looking up other players is not available.");
            return;
        }
        String n = name.trim();
        Net.getJson("https://api.mojang.com/users/profiles/minecraft/" + n, null)
                .exceptionallyCompose(err -> Net.getJson("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + n, null))
                .whenComplete((j, e) -> {
                    if (e != null || j == null || !j.isJsonObject() || !j.getAsJsonObject().has("id")) {
                        fail.accept("Player not found: " + n);
                        return;
                    }
                    String uuid = j.getAsJsonObject().get("id").getAsString(), real = j.getAsJsonObject().get("name").getAsString();
                    ProfileServer.get(ProfileServer.profiles(uuid)).whenComplete((json, e2) -> {
                        if (e2 != null) {
                            String msg = String.valueOf(e2.getMessage());
                            fail.accept(msg.contains("403") ? "Looking up other players is not available." : msg.contains("429") ? "Hypixel rate limit, wait a minute." : "Could not load " + real + " right now.");
                            return;
                        }
                        try {
                            Data d = parse(json.getAsJsonObject(), uuid, real);
                            // museum counts for networth but a slow museum doesnt hold the answer back long
                            d.extras.orTimeout(6, java.util.concurrent.TimeUnit.SECONDS).whenComplete((v, late) -> ok.accept(d));
                        } catch (NoProfile ex) {
                            fail.accept(real + " has no SkyBlock profile.");
                        } catch (Exception ex) {
                            io.muzy.sbc.SbcMod.LOG.warn("Could not read profile of {}", real, ex);
                            fail.accept("Could not read " + real + "'s profile.");
                        }
                    });
                });
    }

    // shows another profile of whoever is on the page, from whats already loaded
    public static void switchTo(Data from, String profileId) {
        if (from.raw == null || loading) return;
        loading = true;
        status = "Loading " + from.player + "...";
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                viewed = parse(from.raw, from.uuid, from.player, profileId);
                status = "";
            } catch (Exception ex) {
                io.muzy.sbc.SbcMod.LOG.warn("Could not read that profile of {}", from.player, ex);
                status = "Could not read that profile.";
            } finally {
                loading = false;
            }
        });
    }

    // your own profile again when it is older than this, so networth and the stats from it keep up
    static final long REFRESH_MS = 5 * 60_000L;
    private static long refreshTried;

    private static int liveTicks;

    public static void refreshTick() {
        // once a second your own loaded profile takes the skills the game shows further along
        if (++liveTicks % 20 == 0) {
            Data mine = current;
            if (mine != null && !mine.fromGame && mine.profileName.equalsIgnoreCase(GameState.profileName == null ? "" : GameState.profileName)) {
                io.muzy.sbc.core.game.GameProfile.liveSkills(mine);
                Data v = viewed;
                if (v != null && v != mine && v.uuid.equalsIgnoreCase(mine.uuid) && v.profileId.equals(mine.profileId)) io.muzy.sbc.core.game.GameProfile.liveSkills(v);
            }
        }
        long now = System.currentTimeMillis();
        Data d = current;
        if (d == null || d.fromGame || loading || !serverUp() || now - d.loadedAt < REFRESH_MS || now - refreshTried < 60_000) return;
        refreshTried = now;
        var p = Minecraft.getInstance().player;
        if (p != null) load(p.getUUID().toString().replace("-", ""), p.getGameProfile().name(), true);
    }

    // the profile page asks: yours again when what is loaded is over a minute old
    public static void loadSelfIfOld() {
        if (current == null || !current.fromGame && System.currentTimeMillis() - current.loadedAt > 60_000) loadSelf();
    }

    public static void load(String uuid, String playerName) {
        load(uuid, playerName, false);
    }

    // background: a refresh of your own profile, which must not replace another player open on the profile page
    private static void load(String uuid, String playerName, boolean background) {
        if (!serverUp()) {
            if (background) return;
            status = "";
            viewed = null;
            io.muzy.sbc.core.game.GameProfile.requestRebuild();
            return;
        }
        if (loading) return;
        loading = true;
        if (!background) status = "Loading " + playerName + "...";
        ProfileServer.get(ProfileServer.profiles(uuid)).whenComplete((json, e) -> {
            loading = false;
            if (e != null) {
                if (background) return;
                ProfileServer.failed();
                status = "The server did not answer, so your profile comes from the game for now.";
                io.muzy.sbc.core.game.GameProfile.requestRebuild();
                return;
            }
            try {
                Data read = parse(json.getAsJsonObject(), uuid, playerName);
                if (!background || viewed == null || viewed.uuid.equalsIgnoreCase(uuid)) viewed = read;
                var me = Minecraft.getInstance().player;
                if (me == null || me.getUUID().toString().replace("-", "").equalsIgnoreCase(uuid)) current = read;
                status = "";
            } catch (NoProfile ex) {
                status = playerName + " has no SkyBlock profile on Hypixel (in a test world you are a random dev player - look up your name instead).";
            } catch (Exception ex) {
                io.muzy.sbc.SbcMod.LOG.warn("Could not read profile of {}", playerName, ex);
                status = "Could not read " + playerName + "'s profile: " + ex.getMessage();
            }
        });
    }

    private static final class NoProfile extends RuntimeException {}

    private static Data parse(JsonObject root, String uuid, String playerName) {
        return parse(root, uuid, playerName, null);
    }

    private static Data parse(JsonObject root, String uuid, String playerName, String wanted) {
        if (!root.has("profiles") || root.get("profiles").isJsonNull() || root.getAsJsonArray("profiles").isEmpty()) throw new NoProfile();
        JsonObject chosen = null;
        for (JsonElement el : root.getAsJsonArray("profiles")) {
            JsonObject p = el.getAsJsonObject();
            if (chosen == null || (p.has("selected") && p.get("selected").getAsBoolean())) chosen = p;
        }
        for (JsonElement el : root.getAsJsonArray("profiles"))
            if (str(el.getAsJsonObject(), "profile_id").equals(wanted)) chosen = el.getAsJsonObject();
        Data d = new Data();
        d.raw = root;
        for (JsonElement el : root.getAsJsonArray("profiles")) {
            JsonObject p = el.getAsJsonObject();
            if (obj(p, "members").has(uuid)) d.profiles.add(new Ref(str(p, "profile_id"), str(p, "cute_name"), str(p, "game_mode"), p == chosen));
        }
        d.player = playerName;
        d.uuid = uuid;
        d.profileId = str(chosen, "profile_id");
        d.profileName = str(chosen, "cute_name");
        d.gameMode = str(chosen, "game_mode");
        JsonObject m = chosen.getAsJsonObject("members").getAsJsonObject(uuid);
        if (m == null) throw new IllegalStateException("member data missing");
        d.sbXp = num(obj(m, "leveling"), "experience");
        d.purse = num(obj(m, "currencies"), "coin_purse");
        d.bank = num(obj(chosen, "banking"), "balance");
        // coop banks are often hidden from the api, your tab list still shows it
        var self = Minecraft.getInstance().player;
        // from skyblocker ProfileMemberProfile.personalBankAccount: only on a coop, SkillsInfoBoxWidget shows both added up
        if (obj(m, "profile").has("bank_account")) {
            d.personalBank = num(obj(m, "profile"), "bank_account");
            d.bank += d.personalBank;
        }
        if (d.bank <= 0 && GameState.bank > 0 && self != null && self.getUUID().toString().replace("-", "").equalsIgnoreCase(uuid)) d.bank = GameState.bank;
        d.fairySouls = (int) num(obj(m, "fairy_soul"), "total_collected");

        JsonObject exp = obj(obj(m, "player_data"), "experience");
        double sum = 0;
        int counted = 0;
        for (String s : SKILLS) {
            double xp = s.equals("SOCIAL") ? ProfileDetails.socialXp(obj(chosen, "members")) : num(exp, "SKILL_" + s);
            Level l = level(s.equals("RUNECRAFTING") ? RUNECRAFTING_XP : s.equals("SOCIAL") ? SOCIAL_XP : SKILL_XP, xp, ProfileDetails.skillCap(s, m));
            d.skills.put(cap(s), l);
            if (!s.equals("RUNECRAFTING") && !s.equals("SOCIAL")) {
                sum += l.level() + l.progress();
                counted++;
            }
        }
        d.skillAverage = counted == 0 ? 0 : sum / counted;

        JsonObject bosses = obj(obj(m, "slayer"), "slayer_bosses");
        for (Map.Entry<String, int[]> e : SLAYER_XP.entrySet()) {
            double xp = num(obj(bosses, e.getKey()), "xp");
            int[] t = e.getValue();
            int lvl = 0;
            while (lvl < t.length && xp >= t[lvl]) lvl++;
            double prev = lvl == 0 ? 0 : t[lvl - 1];
            double next = lvl >= t.length ? prev : t[lvl];
            d.slayers.put(slayerName(e.getKey()), new Level(lvl, next == prev ? 1 : (xp - prev) / (next - prev), xp, Math.max(0, next - xp)));
        }

        JsonObject dungeons = obj(m, "dungeons");
        JsonObject cata = obj(obj(dungeons, "dungeon_types"), "catacombs");
        d.catacombs = level(DUNGEON_XP, num(cata, "experience"), 50);
        JsonObject classes = obj(dungeons, "player_classes");
        for (String c : new String[]{"healer", "mage", "berserk", "archer", "tank"})
            d.classes.put(cap(c), level(DUNGEON_XP, num(obj(classes, c), "experience"), 50));
        JsonObject tiers = obj(cata, "tier_completions");
        for (Map.Entry<String, JsonElement> e : tiers.entrySet())
            if (!e.getKey().equals("total")) d.floorCompletions.put("F" + e.getKey(), e.getValue().getAsInt());
        JsonObject mm = obj(obj(obj(dungeons, "dungeon_types"), "master_catacombs"), "tier_completions");
        for (Map.Entry<String, JsonElement> e : mm.entrySet())
            if (!e.getKey().equals("total")) d.floorCompletions.put("M" + e.getKey(), e.getValue().getAsInt());

        d.secrets = (int) num(dungeons, "secrets");
        d.selectedClass = cap(str(dungeons, "selected_dungeon_class"));
        readBests(d, "F", cata);
        readBests(d, "M", obj(obj(dungeons, "dungeon_types"), "master_catacombs"));

        JsonObject petsData = obj(m, "pets_data");
        if (petsData.has("pets")) for (JsonElement el : petsData.getAsJsonArray("pets")) {
            JsonObject p = el.getAsJsonObject();
            String type = str(p, "type"), tier = str(p, "tier");
            double petExp = num(p, "exp");
            d.pets.add(new Pet(type, petName(type), tier, Pets.level(petExp, tier, type), p.has("active") && p.get("active").getAsBoolean(),
                    str(p, "heldItem"), petExp, AuctionPrices.pet(type, tier, petExp, str(p, "heldItem"), str(p, "skin"))));
        }
        d.pets.sort((a, b) -> a.active() != b.active() ? Boolean.compare(b.active(), a.active()) : Double.compare(b.value(), a.value()));

        JsonObject core = obj(m, "mining_core");
        // newer profiles keep hotm/hotf xp under skill_tree, older ones under mining_core
        JsonObject treeXp = obj(obj(m, "skill_tree"), "experience");
        double hotmXp = Math.max(num(treeXp, "mining"), num(core, "experience"));
        d.hotm = tier(level(HOTM_XP, hotmXp, 9));
        d.hotfXp = num(treeXp, "foraging");
        // neu leveling.json: the forest has 8 tiers on the mountains table, the mountain 10
        d.hotf = tier(level(HOTM_XP, d.hotfXp, 7));
        d.magicalPower = (int) num(obj(m, "accessory_bag_storage"), "highest_magical_power");
        d.selectedPower = obj(m, "accessory_bag_storage").has("selected_power") ? cap(obj(m, "accessory_bag_storage").get("selected_power").getAsString().replace('_', ' ')) : "";
        JsonObject pd = obj(m, "player_data");
        if (pd.has("crafted_generators") && pd.get("crafted_generators").isJsonArray()) d.uniqueMinions = pd.getAsJsonArray("crafted_generators").size();
        JsonObject stats = obj(obj(m, "player_stats"), "kills");
        d.kills = (long) num(stats, "total");
        d.deaths = (long) num(obj(obj(m, "player_stats"), "deaths"), "total");
        d.mithrilPowder = (long) (num(core, "powder_mithril") + num(core, "powder_spent_mithril"));
        d.gemstonePowder = (long) (num(core, "powder_gemstone") + num(core, "powder_spent_gemstone"));
        d.glacitePowder = (long) (num(core, "powder_glacite") + num(core, "powder_spent_glacite"));

        JsonObject nether = obj(m, "nether_island_player_data");
        d.faction = cap(str(nether, "selected_faction"));
        d.magesRep = (int) num(nether, "mages_reputation");
        d.barbRep = (int) num(nether, "barbarians_reputation");

        d.firstJoin = (long) num(obj(m, "profile"), "first_join");
        JsonObject ess = obj(obj(m, "currencies"), "essence");
        for (Map.Entry<String, JsonElement> e : ess.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            long n = (long) num(e.getValue().getAsJsonObject(), "current");
            d.essence.put(cap(e.getKey()), n);
            d.essenceValue += n * Prices.sell("ESSENCE_" + e.getKey());
        }
        d.motes = (long) num(obj(m, "currencies"), "motes_purse");
        JsonObject trophy = obj(m, "trophy_fish");
        for (Map.Entry<String, JsonElement> e : trophy.entrySet())
            if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber() && !e.getKey().contains("_"))
                d.trophyFish.put(cap(e.getKey()), e.getValue().getAsInt());
        JsonObject garden = obj(m, "garden_player_data");
        d.copper = (int) num(garden, "copper");
        JsonObject medals = obj(obj(m, "jacobs_contest"), "medals_inv");
        for (Map.Entry<String, JsonElement> e : medals.entrySet()) d.medals.put(cap(e.getKey()), e.getValue().getAsInt());
        JsonObject enigma = obj(obj(m, "rift"), "enigma");
        if (enigma.has("found_souls") && enigma.get("found_souls").isJsonArray()) d.enigmaSouls = enigma.getAsJsonArray("found_souls").size();
        JsonObject ps = obj(m, "player_stats");
        d.highestCrit = (long) num(ps, "highest_critical_damage");
        JsonObject auc = obj(ps, "auctions");
        d.auctionsEarned = (long) num(auc, "gold_earned");
        d.auctionsSpent = (long) num(auc, "gold_spent");
        for (Map.Entry<String, JsonElement> e : obj(nether, "dojo").entrySet())
            if (e.getKey().startsWith("dojo_points_") && e.getValue().isJsonPrimitive()) d.dojoPoints += e.getValue().getAsInt();
        for (Map.Entry<String, JsonElement> e : obj(nether, "kuudra_completed_tiers").entrySet())
            if (!e.getKey().startsWith("highest") && e.getValue().isJsonPrimitive()) d.kuudraKills += e.getValue().getAsInt();
        for (Map.Entry<String, JsonElement> e : obj(core, "crystals").entrySet())
            if (e.getValue().isJsonObject() && num(e.getValue().getAsJsonObject(), "total_found") > 0) d.crystalsFound++;

        JsonObject coll = obj(m, "collection");
        for (Map.Entry<String, JsonElement> e : coll.entrySet()) d.collections.put(e.getKey(), e.getValue().getAsLong());

        JsonObject inv = obj(m, "inventory");
        decode(d, "Armor", obj(inv, "inv_armor"));
        decode(d, "Equipment", obj(inv, "equipment_contents"));
        decode(d, "Inventory", obj(inv, "inv_contents"));
        decode(d, "Ender Chest", obj(inv, "ender_chest_contents"));
        decode(d, "Wardrobe", obj(inv, "wardrobe_contents"));
        JsonObject bags = obj(inv, "bag_contents");
        decode(d, "Accessories", obj(bags, "talisman_bag"));
        decode(d, "Sacks", obj(bags, "sacks_bag"));
        decode(d, "Fishing Bag", obj(bags, "fishing_bag"));
        decode(d, "Quiver", obj(bags, "quiver"));
        decode(d, "Personal Vault", obj(inv, "personal_vault_contents"));
        // wardrobe lives under "loadout" now: armor + equipment sets, one entry per piece
        JsonObject loadout = obj(m, "loadout");
        for (String part : new String[]{"armor", "equipment"})
            for (Map.Entry<String, JsonElement> set : obj(loadout, part).entrySet())
                if (set.getValue().isJsonObject())
                    for (Map.Entry<String, JsonElement> piece : set.getValue().getAsJsonObject().entrySet())
                        if (piece.getValue().isJsonObject()) decode(d, part.equals("armor") ? "Wardrobe" : "Equipment sets", piece.getValue().getAsJsonObject());
        // hunting tools (lasso, scythe, net, traps...)
        for (Map.Entry<String, JsonElement> tool : obj(obj(m, "foraging"), "hunting_toolkit").entrySet())
            if (tool.getValue().isJsonArray())
                for (JsonElement t : tool.getValue().getAsJsonArray()) if (t.isJsonObject()) decode(d, "Hunting Toolkit", t.getAsJsonObject());
        JsonObject backpacks = obj(inv, "backpack_contents");
        for (int i = 0; i < 18; i++) decode(d, "Backpack " + (i + 1), obj(backpacks, String.valueOf(i)));
        JsonObject sacks = obj(inv, "sacks_counts");
        for (Map.Entry<String, JsonElement> e : sacks.entrySet()) d.sacksValue += e.getValue().getAsLong() * Prices.sell(e.getKey());

        // networth by source, every item valued with its upgrades (see Worth)
        d.worth.put("Purse", d.purse);
        d.worth.put("Bank", d.bank);
        d.worth.put("Sacks", d.sacksValue);
        d.worth.put("Essence", d.essenceValue);
        for (Map.Entry<String, List<Item>> e : d.inventories.entrySet()) {
            String group = e.getKey().startsWith("Backpack") ? "Backpacks" : e.getKey();
            double v = 0;
            for (Item it : e.getValue()) if (it != null) v += it.value();
            d.worth.merge(group, v, Double::sum);
        }
        d.worth.put("Pets", d.pets.stream().mapToDouble(Pet::value).sum());
        d.worth.entrySet().removeIf(e -> e.getValue() <= 0);
        ProfileDetails.read(d, chosen, m);
        d.cookieActive = obj(m, "profile").has("cookie_buff_active") ? (obj(m, "profile").get("cookie_buff_active").getAsBoolean() ? 1 : 0) : -1;
        loadMuseum(d);
        return d;
    }

    // one dungeon types pbs, the fields skyblockers profile viewer reads
    static void readBests(Data d, String prefix, JsonObject type) {
        JsonObject s = obj(type, "fastest_time_s"), plus = obj(type, "fastest_time_s_plus"), any = obj(type, "fastest_time"), score = obj(type, "best_score");
        java.util.Set<String> floors = new java.util.TreeSet<>(any.keySet());
        floors.addAll(s.keySet());
        floors.addAll(plus.keySet());
        for (String f : floors)
            if (!f.isEmpty() && Character.isDigit(f.charAt(0))) d.bests.put(prefix + f, new Best((long) num(s, f), (long) num(plus, f), (long) num(any, f), (int) num(score, f)));
    }

    // museum items (not in any inventory). borrowed ones get skipped bc theyre in your inventory
    private static void loadMuseum(Data d) {
        if (d.profileId.isEmpty()) {
            d.extras.complete(null);
            return;
        }
        ProfileServer.get(ProfileServer.museum(d.profileId)).whenComplete((json, e) -> {
            if (e != null) {
                d.extras.complete(null);
                return;
            }
            try {
                JsonObject me = obj(obj(json.getAsJsonObject(), "members"), d.uuid);
                double v = 0;
                for (Map.Entry<String, JsonElement> it : obj(me, "items").entrySet()) {
                    d.museum.add(it.getKey());
                    JsonObject o = it.getValue().getAsJsonObject();
                    if (o.has("borrowing") && o.get("borrowing").getAsBoolean()) continue;
                    v += valueOfData(obj(o, "items"));
                }
                if (me.has("special") && me.get("special").isJsonArray())
                    for (JsonElement sp : me.getAsJsonArray("special")) v += valueOfData(obj(sp.getAsJsonObject(), "items"));
                if (v > 0) d.worth.put("Museum", v);
            } catch (Exception ignored) {
            }
            d.extras.complete(null);
        });
    }

    private static double valueOfData(JsonObject container) {
        if (!container.has("data")) return 0;
        try {
            byte[] raw = Base64.getDecoder().decode(container.get("data").getAsString());
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtAccounter.unlimitedHeap());
            ListTag list = root.getListOrEmpty("i");
            double v = 0;
            for (int i = 0; i < list.size(); i++) {
                CompoundTag it = list.getCompoundOrEmpty(i);
                if (it.isEmpty()) continue;
                v += Worth.item(it.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes"), it.getByteOr("Count", (byte) 1));
            }
            return v;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void decode(Data d, String label, JsonObject container) {
        if (!container.has("data")) return;
        try {
            byte[] raw = Base64.getDecoder().decode(container.get("data").getAsString());
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtAccounter.unlimitedHeap());
            ListTag list = root.getListOrEmpty("i");
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag it = list.getCompoundOrEmpty(i);
                if (it.isEmpty()) {
                    items.add(null);
                    continue;
                }
                CompoundTag tag = it.getCompoundOrEmpty("tag");
                CompoundTag display = tag.getCompoundOrEmpty("display");
                List<String> lore = new ArrayList<>();
                for (Tag l : display.getListOrEmpty("Lore")) lore.add(l.asString().orElse(""));
                CompoundTag ea = tag.getCompoundOrEmpty("ExtraAttributes");
                String sbId = ea.getStringOr("id", "");
                int count = it.getByteOr("Count", (byte) 1);
                String texture = null;
                for (Tag pt : tag.getCompoundOrEmpty("SkullOwner").getCompoundOrEmpty("Properties").getListOrEmpty("textures")) {
                    String v = pt.asCompound().map(c -> c.getStringOr("Value", "")).orElse("");
                    if (!v.isEmpty()) texture = v;
                }
                boolean glint = !tag.getListOrEmpty("ench").isEmpty() || !ea.getCompoundOrEmpty("enchantments").isEmpty();
                int color = display.contains("color") ? display.getIntOr("color", -1) : -1;
                double value = 0;
                try {
                    value = Worth.item(ea, count);
                } catch (Exception ignored) {
                }
                items.add(new Item(display.getStringOr("Name", sbId), lore, sbId, count,
                        LegacyItems.toModern(it.getShortOr("id", (short) 0), it.getShortOr("Damage", (short) 0), sbId), texture, color, glint, value));
            }
            d.inventories.computeIfAbsent(label, k -> new ArrayList<>()).addAll(items);
        } catch (Exception ignored) {
        }
    }

    // hotm/hotf tiers start at tier 1 with 0 xp, one above the xp table index
    private static Level tier(Level l) {
        return new Level(l.level() + 1, l.progress(), l.xp(), l.toNext());
    }

    public static Level level(long[] table, double xp, int cap) {
        int lvl = 0;
        while (lvl + 1 < table.length && lvl < cap && xp >= table[lvl + 1]) lvl++;
        if (lvl >= cap || lvl + 1 >= table.length) return new Level(lvl, 1, xp, 0);
        double into = xp - table[lvl], span = table[lvl + 1] - table[lvl];
        return new Level(lvl, into / span, xp, span - into);
    }

    private static String petName(String type) {
        StringBuilder b = new StringBuilder();
        for (String w : type.split("_")) b.append(b.isEmpty() ? "" : " ").append(cap(w));
        return b.toString();
    }


    private static String slayerName(String k) {
        return switch (k) { case "zombie" -> "Revenant"; case "spider" -> "Tarantula"; case "wolf" -> "Sven"; case "enderman" -> "Voidgloom";
            case "blaze" -> "Inferno"; case "vampire" -> "Riftstalker"; default -> cap(k); };
    }

    public static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        String l = s.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(l.charAt(0)) + l.substring(1);
    }

    static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    static double num(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : 0;
    }

    static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }
}
