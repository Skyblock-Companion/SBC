// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// more stuff the profile page takes from the answer Profile already has, no extra call. fields from
// skyblocker profileviewer2/model: SkillTree, Treasures, Dungeons.DailyRuns, SlayerBoss...
final class ProfileDetails {
    // from skyblocker Dungeons.DailyRuns.MAX_DAILIES
    static final int MAX_DAILIES = 5;

    private ProfileDetails() {}

    static void read(Profile.Data d, JsonObject profile, JsonObject member) {
        trees(d, member);
        croesus(d, Profile.obj(Profile.obj(member, "dungeons"), "treasures"));
        d.dailyRuns = dailyRuns(Profile.obj(Profile.obj(member, "dungeons"), "daily_runs"), LocalDate.now(ZoneOffset.UTC));
        slayerKills(d, member);
        tuning(d, Profile.obj(Profile.obj(member, "accessory_bag_storage"), "tuning"));
        members(d, Profile.obj(profile, "members"));
    }

    // from skyblocker SkillTree: perks of the selected slot. old profiles keep the mountains under mining_core
    static void trees(Profile.Data d, JsonObject member) {
        JsonObject tree = Profile.obj(member, "skill_tree"), nodes = Profile.obj(tree, "nodes"), slots = Profile.obj(tree, "selected_skill_tree_slot");
        d.hotmSlot = (int) Math.max(1, Profile.num(slots, "mining"));
        d.hotfSlot = (int) Math.max(1, Profile.num(slots, "foraging"));
        JsonObject mountain = Profile.obj(nodes, slotKey("mining", d.hotmSlot));
        if (mountain.isEmpty() && d.hotmSlot == 1) mountain = Profile.obj(Profile.obj(member, "mining_core"), "nodes");
        perks(mountain, d.hotmNodes, d.hotmOff);
        perks(Profile.obj(nodes, slotKey("foraging", d.hotfSlot)), d.hotfNodes, d.hotfOff);
        JsonObject spent = Profile.obj(tree, "tokens_spent"), ability = Profile.obj(tree, "selected_ability");
        d.hotmTokensSpent = (int) Profile.num(spent, slotKey("mountain", d.hotmSlot));
        d.hotfTokensSpent = (int) Profile.num(spent, slotKey("forest", d.hotfSlot));
        d.hotmAbility = Profile.str(ability, slotKey("mining", d.hotmSlot));
        d.hotfAbility = Profile.str(ability, slotKey("foraging", d.hotfSlot));
    }

    private static String slotKey(String name, int slot) {
        return slot <= 1 ? name : name + "_" + slot;
    }

    // levels by perk id + "toggle_" + id false for one turned off (from skyblocker HotmTreeBuilder.buildNode)
    private static void perks(JsonObject from, Map<String, Integer> levels, Set<String> off) {
        for (Map.Entry<String, JsonElement> e : from.entrySet()) {
            if (!e.getValue().isJsonPrimitive()) continue;
            if (e.getKey().startsWith("toggle_")) {
                if (e.getValue().getAsJsonPrimitive().isBoolean() && !e.getValue().getAsBoolean()) off.add(e.getKey().substring("toggle_".length()));
            } else if (e.getValue().getAsJsonPrimitive().isNumber()) levels.put(e.getKey(), e.getValue().getAsInt());
        }
    }

    // from skyblocker Treasures totalRuns + runsWithUnclaimedChests: chests grouped by run, a run is claimed once
    // one was paid for or opened
    static void croesus(Profile.Data d, JsonObject treasures) {
        if (!treasures.has("chests") || !treasures.get("chests").isJsonArray()) {
            d.croesusRuns = 0;
            d.croesusUnclaimed = 0;
            return;
        }
        Map<String, Boolean> claimed = new HashMap<>();
        for (JsonElement el : treasures.getAsJsonArray("chests")) {
            if (!el.isJsonObject()) continue;
            JsonObject chest = el.getAsJsonObject();
            String run = Profile.str(chest, "run_id");
            boolean taken = bool(chest, "paid") || bool(chest, "is_opened");
            claimed.merge(run, taken, Boolean::logicalOr);
        }
        d.croesusRuns = claimed.size();
        d.croesusUnclaimed = (int) claimed.values().stream().filter(c -> !c).count();
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    // from skyblocker getDailyRunsCompleted: the stamp is days since epoch and the count only holds that day in GMT
    static int dailyRuns(JsonObject daily, LocalDate today) {
        long stamp = (long) Profile.num(daily, "current_day_stamp");
        return stamp != 0 && stamp == today.toEpochDay() ? Math.min((int) Profile.num(daily, "completed_runs_count"), MAX_DAILIES) : 0;
    }

    // from skyblocker SlayerBoss boss_kills_tier_0 and up, the first is tier 1
    private static void slayerKills(Profile.Data d, JsonObject member) {
        JsonObject bosses = Profile.obj(Profile.obj(member, "slayer"), "slayer_bosses");
        for (String[] s : new String[][]{{"zombie", "Revenant"}, {"spider", "Tarantula"}, {"wolf", "Sven"}, {"enderman", "Voidgloom"}, {"blaze", "Inferno"}, {"vampire", "Riftstalker"}}) {
            JsonObject boss = Profile.obj(bosses, s[0]);
            long[] kills = new long[5];
            for (int t = 0; t < kills.length; t++) kills[t] = (long) Profile.num(boss, "boss_kills_tier_" + t);
            d.slayerKills.put(s[1], kills);
        }
    }

    // from skyblocker TuningSlotWidget.buildLines: the api gives points put into a stat, not what they give. slot
    // 0 = active
    static void tuning(Profile.Data d, JsonObject tuning) {
        JsonObject slot = Profile.obj(tuning, "slot_0");
        put(d, "Health", Profile.num(slot, "health") * 5);
        put(d, "Defense", Profile.num(slot, "defense"));
        put(d, "Strength", Profile.num(slot, "strength"));
        put(d, "Crit Chance", Profile.num(slot, "critical_chance") * 0.2);
        put(d, "Crit Damage", Profile.num(slot, "critical_damage"));
        put(d, "Attack Speed", Profile.num(slot, "attack_speed") * 0.3);
        put(d, "Intelligence", Profile.num(slot, "intelligence") * 2);
        put(d, "Speed", Profile.num(slot, "walk_speed") * 1.5);
    }

    private static void put(Profile.Data d, String stat, double amount) {
        if (amount > 0) d.tuning.put(stat, amount);
    }

    // from skyblocker getCollectionReport + addMinionItems: collections, tiers and crafted minions are the whole
    // profiles. tier comes from unlockedCollectionTiers ("WHEAT_5")
    static void members(Profile.Data d, JsonObject members) {
        for (Map.Entry<String, JsonElement> e : members.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject member = e.getValue().getAsJsonObject();
            for (Map.Entry<String, JsonElement> c : Profile.obj(member, "collection").entrySet())
                if (c.getValue().isJsonPrimitive()) d.collectionsTotal.merge(c.getKey(), c.getValue().getAsLong(), Long::sum);
            JsonObject data = Profile.obj(member, "player_data");
            if (data.has("unlocked_coll_tiers") && data.get("unlocked_coll_tiers").isJsonArray())
                for (JsonElement t : data.getAsJsonArray("unlocked_coll_tiers")) {
                    int at = t.getAsString().lastIndexOf('_');
                    int tier = at < 0 ? 0 : number(t.getAsString().substring(at + 1));
                    if (tier > 0) d.collectionTiers.merge(t.getAsString().substring(0, at), tier, Math::max);
                }
            if (data.has("crafted_generators") && data.get("crafted_generators").isJsonArray())
                for (JsonElement m : data.getAsJsonArray("crafted_generators")) {
                    int at = m.getAsString().lastIndexOf('_');
                    int tier = at < 0 ? 0 : number(m.getAsString().substring(at + 1));
                    if (tier > 0) d.craftedMinions.computeIfAbsent(m.getAsString().substring(0, at), k -> new TreeSet<>()).add(tier);
                }
        }
    }

    // collection tiers can hold junk like -1, says skyblocker PlayerData
    private static int number(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // from skyblocker PlayerData.getSkillLevel + the level cap from LevelCalculator.getSkillCapIncrease
    static int skillCap(String skill, JsonObject member) {
        return switch (skill) {
            case "FARMING" -> Math.min(60, 50 + (int) Profile.num(Profile.obj(Profile.obj(member, "jacobs_contest"), "perks"), "farming_level_cap"));
            case "TAMING" -> {
                JsonObject care = Profile.obj(Profile.obj(member, "pets_data"), "pet_care");
                yield Math.min(60, 50 + (care.has("pet_types_sacrificed") && care.get("pet_types_sacrificed").isJsonArray() ? care.getAsJsonArray("pet_types_sacrificed").size() : 0));
            }
            // LevelCalculator adds to 50 by collection tiers. the tier table isnt here so use the max, foragings absolute cap
            case "FORAGING" -> 57;
            case "MINING", "COMBAT", "ENCHANTING" -> 60;
            case "RUNECRAFTING", "SOCIAL" -> 25;
            default -> 50;
        };
    }

    // skyblockers first viewer counts the whole coops social like the game does (getCoopSocialXP)
    static double socialXp(JsonObject members) {
        double sum = 0;
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, JsonElement> e : members.entrySet()) {
            if (e.getValue().isJsonObject() && seen.add(e.getKey())) sum += Profile.num(Profile.obj(Profile.obj(e.getValue().getAsJsonObject(), "player_data"), "experience"), "SKILL_SOCIAL");
        }
        return sum;
    }
}
