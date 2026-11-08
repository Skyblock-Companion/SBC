// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.hud;

import dev.zmuzy.sbc.core.LegacyItems;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

// every card has its own color + icon so you know it before reading
public final class Looks {
    private Looks() {}

    public record Look(int accent, String icon) {
        public ItemStack stack() {
            return icon == null ? ItemStack.EMPTY : LegacyItems.stack(icon);
        }
    }

    private static final Look NONE = new Look(0xFF5CD8FF, null);
    private static final Map<String, Look> LOOKS = new HashMap<>();

    private static void put(String id, int accent, String icon) {
        LOOKS.put(id, new Look(accent, "minecraft:" + icon));
    }

    static {
        put("routine", 0xFFFFD166, "writable_book");
        put("board", 0xFFFFB627, "gold_ingot");
        put("sbdate", 0xFF7FD4FF, "clock");
        put("chat_current", 0xFF7FD4FF, "paper");
        put("mayor", 0xFF5CD8FF, "emerald");
        put("customtext", 0xFFB0BEC5, "name_tag");
        put("pickups", 0xFF5FEA8C, "hopper");
        put("ability_cooldowns", 0xFF80D8FF, "blaze_rod");
        put("sack_display", 0xFFD8B26E, "bundle");
        put("outside_sack_value", 0xFFD8B26E, "bundle");
        // garden
        put("garden_pests", 0xFFFF7A59, "silverfish_spawn_egg");
        put("garden_visitors", 0xFFFFC857, "villager_spawn_egg");
        put("garden_composter", 0xFFB5D65F, "composter");
        put("garden_contest", 0xFFFFB347, "gold_nugget");
        put("garden_speed", 0xFF6BC7FF, "sugar");
        put("garden_crop_milestones", 0xFF9CE37D, "wheat");
        put("garden_best_crop_time", 0xFF6FDB9E, "clock");
        put("garden_money_per_hour", 0xFFFFD166, "gold_ingot");
        // crimson
        put("crimson_panel", 0xFFFF6B4A, "blaze_powder");
        put("crimson_miniboss_timer", 0xFFFF4D6A, "wither_skeleton_skull");
        put("profit_session", 0xFFFFD166, "gold_ingot");
        put("garden_profit", 0xFFFFD166, "wheat");
        put("garden_farming", 0xFF9CE37D, "diamond_hoe");
        put("garden_pest_timer", 0xFFFF7A59, "clock");
        put("garden_spray", 0xFF9CE37D, "splash_potion");
        put("garden_stereo", 0xFFFF6B8A, "music_disc_13");
        put("garden_mantid", 0xFF5FBF5F, "iron_chestplate");
        put("garden_true_fortune", 0xFFFFD166, "golden_hoe");
        put("garden_level", 0xFF9CE37D, "experience_bottle");
        put("garden_mushroom_perk", 0xFFE8705F, "red_mushroom");
        put("garden_hoe_level", 0xFF6BC7FF, "diamond_hoe");
        put("garden_yaw_pitch", 0xFF6BC7FF, "compass");
        put("garden_atmospheric", 0xFF7FB3FF, "sunflower");
        put("garden_lanes", 0xFF9CE37D, "rail");
        put("garden_feast", 0xFFE8A33D, "cake");
        put("garden_mouse_lock", 0xFFFFC857, "iron_bars");
        put("pest_profit", 0xFF7BD66F, "slime_ball");
        put("slayer_profit", 0xFFFF7043, "iron_sword");
        put("fishing_profit", 0xFF5CD8FF, "fishing_rod");
        put("foraging_tracker", 0xFF7BC96F, "oak_log");
        put("crimson_volcano", 0xFFFF9F45, "magma_block");
        // mining
        put("mining_commissions", 0xFF4FD8C4, "paper");
        put("mining_event", 0xFFC77DFF, "firework_rocket");
        put("mining_powder", 0xFF58C7FF, "prismarine_crystals");
        put("mining_powder_tracker", 0xFFEC7DD8, "chest");
        put("mining_corpses", 0xFF9AD1FF, "skeleton_skull");
        put("mining_dig", 0xFFD8B26E, "brush");
        put("mining_cavein", 0xFFFF8F6B, "tnt");
        put("mining_pity", 0xFF7FB3FF, "ender_eye");
        put("mining_cold_value", 0xFF9BE7FF, "packed_ice");
        // dungeons
        put("dungeon_copilot", 0xFFFF5D73, "compass");
        put("dungeon_splits", 0xFFFFD166, "clock");
        put("dungeonmap", 0xFF7FB3FF, "filled_map");
        put("dungeon_goldor", 0xFFFFA94D, "command_block");
        put("dungeon_timers", 0xFF5CD8FF, "clock");
        put("dungeon_croesus", 0xFFFFD166, "chest");
        // slayer + combat
        put("slayers", 0xFFFF5252, "netherite_sword");
        put("combat_session", 0xFFFF8A65, "shield");
        put("end_dragon", 0xFFC77DFF, "dragon_head");
        // fishing + foraging
        put("fishing_session", 0xFF4FC3F7, "fishing_rod");
        put("foraging_tree", 0xFF81C784, "oak_sapling");
        // events
        put("diana_panel", 0xFFFFC107, "golden_shovel");
        put("diana_warp_text", 0xFFFFC107, "ender_pearl");
        put("hoppity_panel", 0xFFFF9EC7, "egg");
        put("hoppity_unclaimed", 0xFFFF9EC7, "egg");
        put("hoppity_cf_stats", 0xFFC48A5A, "cocoa_beans");
        put("hoppity_collection", 0xFFC48A5A, "rabbit_foot");
        put("winter_panel", 0xFF9BE7FF, "snowball");
        put("spook_panel", 0xFFFF9800, "carved_pumpkin");
        put("carnival_panel", 0xFFFF6FD8, "melon_slice");
        put("carnival_goals", 0xFFFF6FD8, "writable_book");
        put("carnival_zombies", 0xFFFF5555, "redstone_lamp");
        put("bingo_card", 0xFFFFC14D, "paper");
        put("bingo_minion_craft", 0xFF7FB3FF, "crafting_table");
        put("spook_timers", 0xFFFF9800, "wither_rose");
        put("gift_unique_counter", 0xFF9BE7FF, "chest");
        put("winter_close_time", 0xFF9BE7FF, "snowball");
        put("jerry_timer", 0xFF55FF55, "villager_spawn_egg");
        put("beachball_panel", 0xFF40C4FF, "slime_ball");
        // rift
        put("rift_panel", 0xFFD070FF, "ender_eye");
        put("rift_timer", 0xFFD070FF, "clock");
        put("rift_crux", 0xFFB388FF, "nether_star");
        put("rift_vermin", 0xFF9CCC65, "silverfish_spawn_egg");
        put("rift_punchcard", 0xFFFF80AB, "paper");
        put("rift_ubik", 0xFF82B1FF, "chain_command_block");
        // misc
        put("misc_panel", 0xFF90A4AE, "book");
        put("trevor_panel", 0xFFA5D6A7, "leather");
        put("effects_panel", 0xFFF48FB1, "potion");
    }

    public static Look of(String id) {
        return LOOKS.getOrDefault(id, NONE);
    }

    // stable nice color for stuff with no look of its own
    public static int colourFor(String key) {
        int[] palette = {0xFF5CD8FF, 0xFF9CE37D, 0xFFFFC857, 0xFFFF9EC7, 0xFFC77DFF, 0xFF4FD8C4, 0xFFFF8F6B, 0xFF7FB3FF};
        return palette[Math.floorMod(key.hashCode(), palette.length)];
    }
}
