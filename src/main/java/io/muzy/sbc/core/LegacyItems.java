// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

// hypixels api still stores items in 1.8 format (numeric ids), map them to modern items for icons
public final class LegacyItems {
    private LegacyItems() {}

    private static final Map<Integer, String> IDS = new HashMap<>();

    static {
        String[] t = {
                "1:stone", "2:grass_block", "3:dirt", "4:cobblestone", "5:oak_planks", "6:oak_sapling", "12:sand", "13:gravel", "14:gold_ore",
                "15:iron_ore", "16:coal_ore", "17:oak_log", "18:oak_leaves", "20:glass", "22:lapis_block", "35:white_wool", "41:gold_block",
                "42:iron_block", "46:tnt", "47:bookshelf", "49:obsidian", "54:chest", "56:diamond_ore", "57:diamond_block", "58:crafting_table",
                "79:ice", "80:snow_block", "81:cactus", "82:clay", "86:pumpkin", "87:netherrack", "88:soul_sand", "89:glowstone", "91:jack_o_lantern",
                "95:white_stained_glass", "98:stone_bricks", "103:melon", "121:end_stone", "129:emerald_ore", "133:emerald_block", "138:beacon",
                "152:redstone_block", "154:hopper", "155:quartz_block", "160:white_stained_glass_pane", "165:slime_block", "168:prismarine",
                "170:hay_block", "174:packed_ice", "256:iron_shovel", "257:iron_pickaxe", "258:iron_axe", "259:flint_and_steel", "260:apple",
                "261:bow", "262:arrow", "263:coal", "264:diamond", "265:iron_ingot", "266:gold_ingot", "267:iron_sword", "268:wooden_sword",
                "269:wooden_shovel", "270:wooden_pickaxe", "271:wooden_axe", "272:stone_sword", "273:stone_shovel", "274:stone_pickaxe",
                "275:stone_axe", "276:diamond_sword", "277:diamond_shovel", "278:diamond_pickaxe", "279:diamond_axe", "280:stick", "281:bowl",
                "283:golden_sword", "284:golden_shovel", "285:golden_pickaxe", "286:golden_axe", "287:string", "288:feather", "289:gunpowder",
                "290:wooden_hoe", "291:stone_hoe", "292:iron_hoe", "293:diamond_hoe", "294:golden_hoe", "295:wheat_seeds", "296:wheat",
                "297:bread", "298:leather_helmet", "299:leather_chestplate", "300:leather_leggings", "301:leather_boots", "302:chainmail_helmet",
                "303:chainmail_chestplate", "304:chainmail_leggings", "305:chainmail_boots", "306:iron_helmet", "307:iron_chestplate",
                "308:iron_leggings", "309:iron_boots", "310:diamond_helmet", "311:diamond_chestplate", "312:diamond_leggings", "313:diamond_boots",
                "314:golden_helmet", "315:golden_chestplate", "316:golden_leggings", "317:golden_boots", "318:flint", "319:porkchop", "322:golden_apple",
                "331:redstone", "332:snowball", "334:leather", "336:brick", "337:clay_ball", "338:sugar_cane", "339:paper", "340:book",
                "341:slime_ball", "344:egg", "345:compass", "346:fishing_rod", "347:clock", "348:glowstone_dust", "349:cod", "351:ink_sac", "352:bone",
                "353:sugar", "357:cookie", "359:shears", "360:melon_slice", "361:pumpkin_seeds", "363:beef", "365:chicken", "367:rotten_flesh",
                "368:ender_pearl", "369:blaze_rod", "370:ghast_tear", "371:gold_nugget", "372:nether_wart", "373:potion", "374:glass_bottle",
                "375:spider_eye", "376:fermented_spider_eye", "377:blaze_powder", "378:magma_cream", "381:ender_eye", "382:glistering_melon_slice",
                "384:experience_bottle", "385:fire_charge", "386:writable_book", "388:emerald", "389:item_frame", "391:carrot", "392:potato",
                "394:poisonous_potato", "396:golden_carrot", "397:player_head", "399:nether_star", "401:firework_rocket", "403:enchanted_book",
                "405:nether_brick", "406:quartz", "409:prismarine_shard", "410:prismarine_crystals", "411:rabbit", "414:rabbit_foot", "415:rabbit_hide",
                "417:iron_horse_armor", "418:golden_horse_armor", "419:diamond_horse_armor", "420:lead", "421:name_tag", "423:mutton",
                "432:chorus_fruit", "433:popped_chorus_fruit", "437:dragon_breath", "438:splash_potion", "444:elytra", "449:totem_of_undying",
                "450:shulker_shell", "2256:music_disc_13"};
        for (String s : t) {
            int c = s.indexOf(':');
            IDS.put(Integer.parseInt(s.substring(0, c)), s.substring(c + 1));
        }
    }

    public static String toModern(int id, int damage, String sbId) {
        String m = IDS.get(id);
        if (m != null) return m;
        String s = sbId.toUpperCase();
        if (s.contains("SWORD") || s.contains("BLADE")) return "iron_sword";
        if (s.contains("BOW")) return "bow";
        if (s.contains("HELMET")) return "iron_helmet";
        if (s.contains("CHESTPLATE")) return "iron_chestplate";
        if (s.contains("LEGGINGS")) return "iron_leggings";
        if (s.contains("BOOTS")) return "iron_boots";
        return "paper";
    }

    private static final Map<String, ItemStack> STACKS = new HashMap<>();

    // cached icon stack, empty till item components are bound (before joining a world)
    public static ItemStack stack(String modernId) {
        ItemStack cached = STACKS.get(modernId);
        if (cached != null) return cached;
        try {
            Item item = BuiltInRegistries.ITEM.getValue(modernId.contains(":") ? Identifier.parse(modernId) : Identifier.withDefaultNamespace(modernId));
            ItemStack st = new ItemStack(item == null || item == Items.AIR ? Items.PAPER : item);
            STACKS.put(modernId, st);
            return st;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
