// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni ItemDisplayOverlayFeatures + ItemStars: small number on an items icon where the stack count goes
// pet levels and minion tiers arent here bc SlotInfo already writes them in the slot corner
// unlike skyhanni, a stack of more than one keeps showing its count
public final class ItemNumbers {
    // what the rules look at. menu = title of the open screen, no colors
    record Item(String id, String name, String colouredName, List<String> lore, CompoundTag tag, String menu, int speedCap) {}

    // skyhannis two readings of the line in one, colorless: "Vacuum Bag: 444 Pests" and the newer one with a
    // glyph "Vacuum Bag: 2,161 ? Pests"
    private static final Pattern BAG = Pattern.compile("^Vacuum Bag: (?<amount>[\\d.,]+) (?:\\S )?Pests?$");
    private static final Pattern ROMAN = Pattern.compile("^[IVXLCDM]+$");
    private static final String[][] OPTIONS = {
            {"master_star", "Master Star tier"},
            {"master_skull", "Master Skull tier"},
            {"dungeon_head", "Dungeon head floor"},
            {"dungeon_quality", "Dungeon item quality"},
            {"new_year_cake", "New Year Cake year"},
            {"crimson_armor", "Crimson armor tier and stars"},
            {"sack_name", "Sack name"},
            {"kuudra_key", "Kuudra Key tier"},
            {"skill_level", "Skill levels in Your Skills"},
            {"collection_level", "Collection levels"},
            {"ranchers_speed", "Rancher's Boots speed"},
            {"larva_hook", "Larva Hook harvests left"},
            {"dungeon_potion", "Dungeon Potion level"},
            {"vacuum", "Pests in a Garden vacuum"},
            {"vacuum_cap", "Vacuum stops at 40+"},
            {"evolving", "Hours held (Jyrre, Truffle...)"},
            {"edition", "Edition number"},
            {"bingo_rank", "Bingo goal rank"},
            {"skyblock_level", "SkyBlock level in the SkyBlock Menu"},
            {"bestiary", "Bestiary tiers"},
            {"pocket_sack", "Pocket Sack-in-a-Sack on a sack"},
    };
    private static final Map<ItemStack, String> CACHE = new WeakHashMap<>();
    private static long clearedAt;

    private ItemNumbers() {}

    // "IX" or "9", -1 if neither
    private static int level(String s) {
        if (s.isEmpty()) return -1;
        if (ROMAN.matcher(s).matches()) return GameState.roman(s);
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String lastWord(String s) {
        return s.substring(s.lastIndexOf(' ') + 1);
    }

    private static boolean anyLore(List<String> lore, String text) {
        for (String l : lore) if (l.contains(text)) return true;
        return false;
    }

    private static Matcher firstMatch(Pattern p, List<String> lore) {
        for (String l : lore) {
            Matcher m = p.matcher(l);
            if (m.matches()) return m;
        }
        return null;
    }

    private static boolean tierZero(List<String> lore) {
        return firstMatch(Patterns.get("SH.data.collection.api.tierzero"), lore) != null;
    }

    // skyhanni isOwnItem: someone elses vacuum in a trade or auction says nothing about yours
    private static boolean notOwn(List<String> lore) {
        return anyLore(lore, "Click to trade!") || anyLore(lore, "Starting bid:") || anyLore(lore, "Buy it now:") || anyLore(lore, "Click to inspect");
    }

    static String vacuum(List<String> lore) {
        if (notOwn(lore)) return null;
        for (String raw : lore) {
            Matcher m = BAG.matcher(GameState.strip(raw).trim());
            if (!m.matches()) continue;
            long pests = Long.parseLong(m.group("amount").replace(",", "").replace(".", ""));
            if (pests < 40) return String.valueOf(pests);
            if (pests < 1_000) return "§6" + pests;
            if (pests < 100_000) return "§c" + pests / 1000 + "k";
            return "§c" + pests / 100_000 / 10.0 + "m";
        }
        return null;
    }

    // skyhanni config.vacuumBagCap: anything 40+ reads the same, vacuum() colors those
    private static boolean pestsAboveCap(String pests) {
        return pests.startsWith("§");
    }

    private static String grabSackName(String name) {
        String first = name.split(" ")[0];
        for (String size : new String[]{"Large", "Medium", "Small", "Enchanted"}) if (first.equals(size)) return grabSackName(name.substring(first.length() + 1));
        return first;
    }

    // skyhanni ItemStars: kuudra armor counts 10 per tier above the first, on top of its stars
    private static String crimsonArmor(Item it) {
        Matcher m = Patterns.get("SH.data.kuudra.internalname.armor").matcher(it.id());
        if (!m.matches()) return null;
        int tier = switch (m.group("tier")) {
            case "HOT" -> 2;
            case "BURNING" -> 3;
            case "FIERY" -> 4;
            case "INFERNAL" -> 5;
            default -> 1;
        };
        return String.valueOf(it.tag().getIntOr("upgrade_level", 0) + (tier - 1) * 10);
    }

    private static String kuudraKey(String id) {
        return switch (id) {
            case "KUUDRA_TIER_KEY" -> "§a1";
            case "KUUDRA_HOT_TIER_KEY" -> "§22";
            case "KUUDRA_BURNING_TIER_KEY" -> "§e3";
            case "KUUDRA_FIERY_TIER_KEY" -> "§64";
            case "KUUDRA_INFERNAL_TIER_KEY" -> "§c5";
            default -> "§4?";
        };
    }

    private static String dungeonHead(String id) {
        if (!id.contains("GOLD_") && !id.contains("DIAMOND_")) return null;
        String[] bosses = {"BONZO", "SCARF", "PROFESSOR", "THORN", "LIVID", "SADAN", "NECRON"};
        for (int i = 0; i < bosses.length; i++) if (id.contains(bosses[i])) return String.valueOf(i + 1);
        return null;
    }

    private static int secondsHeld(Item it) {
        return switch (it.id()) {
            case "NEW_BOTTLE_OF_JYRRE" -> it.tag().getIntOr("bottle_of_jyrre_seconds", -1);
            case "DISCRITE" -> it.tag().getIntOr("rift_discrite_seconds", -1);
            default -> it.tag().getIntOr("seconds_held", -1);
        };
    }

    // the rules in skyhannis order, first one with something to say wins
    static String number(Item it, Predicate<String> on) {
        String id = it.id(), name = it.name(), menu = it.menu();
        List<String> lore = it.lore();
        CompoundTag tag = it.tag();

        if (on.test("master_star")) {
            switch (id) {
                case "FIRST_MASTER_STAR" -> { return "1"; }
                case "SECOND_MASTER_STAR" -> { return "2"; }
                case "THIRD_MASTER_STAR" -> { return "3"; }
                case "FOURTH_MASTER_STAR" -> { return "4"; }
                case "FIFTH_MASTER_STAR" -> { return "5"; }
                default -> { }
            }
        }
        if (on.test("master_skull")) {
            Matcher m = Patterns.get("SH.inventory.item.overlay.masterskull.id").matcher(id);
            if (m.matches()) return m.group("tier");
        }
        if (on.test("dungeon_head")) {
            String floor = dungeonHead(id);
            if (floor != null) return floor;
        }
        if (on.test("dungeon_quality") && tag.contains("baseStatBoostPercentage")) return String.valueOf(tag.getIntOr("baseStatBoostPercentage", 0));
        if (on.test("new_year_cake") && id.equals("NEW_YEAR_CAKE") && tag.contains("new_years_cake")) return "§b" + tag.getIntOr("new_years_cake", 0);
        if (on.test("crimson_armor")) {
            String armor = crimsonArmor(it);
            if (armor != null) return armor;
        }
        if (on.test("sack_name") && id.endsWith("_SACK") && name.endsWith(" Sack")) {
            String sack = grabSackName(name);
            return (name.contains("Enchanted") ? "§5" : "") + sack.substring(0, Math.min(2, sack.length()));
        }
        if (on.test("kuudra_key") && name.contains("Kuudra Key")) return kuudraKey(id);
        if (on.test("skill_level") && menu.equals("Your Skills") && anyLore(lore, "Click to view!")) {
            if (tierZero(lore)) return "0";
            if (!name.contains("Dungeon")) {
                if (!name.contains(" ")) return "0";
                int level = level(lastWord(name));
                if (level >= 0) {
                    String past = io.github.zmuzy.sbc.skills.SkillCards.stackLevel(name.split(" ")[0]);
                    return past != null ? past : String.valueOf(level);
                }
            }
        }
        if (on.test("collection_level") && menu.endsWith(" Collections") && anyLore(lore, "Click to view!")) {
            if (tierZero(lore)) return "0";
            if (it.colouredName().startsWith("§e")) {
                int level = level(lastWord(name));
                if (level >= 0) return String.valueOf(level);
            }
        }
        if (on.test("ranchers_speed") && id.equals("RANCHERS_BOOTS") && tag.contains("ranchers_speed")) {
            int speed = tag.getIntOr("ranchers_speed", 0);
            String text = speed > 999 ? "1k" : String.valueOf(speed);
            return (speed > it.speedCap() ? "§c" : "§a") + text;
        }
        if (on.test("larva_hook") && id.equals("LARVA_HOOK")) {
            Matcher m = firstMatch(Patterns.get("SH.inventory.item.overlay.harvest"), lore);
            if (m != null && Character.isDigit(m.group("amount").charAt(0))) {
                int amount = Integer.parseInt(m.group("amount"));
                return (amount > 4 ? "§a" : amount > 2 ? "§e" : "§c") + amount;
            }
        }
        if (on.test("dungeon_potion") && name.startsWith("Dungeon ") && name.contains(" Potion")) {
            Matcher m = Patterns.get("SH.inventory.item.overlay.dungeonpotion").matcher(name);
            if (m.matches()) {
                int level = level(m.group("level"));
                if (level > 0) return (level <= 2 ? "§f" : level <= 4 ? "§a" : level <= 6 ? "§9" : "§5") + level;
            }
        }
        if (on.test("vacuum") && id.contains("VACUUM")) {
            String pests = vacuum(lore);
            if (pests != null) return on.test("vacuum_cap") && pestsAboveCap(pests) ? "§640+" : pests;
        }
        // from skyhanni PocketSackInASackDisplay
        if (on.test("pocket_sack") && tag.getIntOr("sack_pss", 0) > 0) return "§a" + tag.getIntOr("sack_pss", 0);
        if (on.test("evolving")) {
            int seconds = secondsHeld(it);
            if (seconds >= 0) return "§a" + seconds / 3600;
        }
        if (on.test("edition") && tag.contains("edition")) {
            int edition = tag.getIntOr("edition", 0);
            if (edition < 1_000) return "§6" + edition;
        }
        if (on.test("bingo_rank") && menu.equals("Bingo Card") && !lore.isEmpty() && lore.get(lore.size() - 1).equals("§aGOAL REACHED")) {
            for (String l : lore) {
                Matcher m = Patterns.get("SH.inventory.item.overlay.bingogoalrank").matcher(l);
                if (!m.find() || !m.group("rank").chars().allMatch(Character::isDigit)) continue;
                long rank = Long.parseLong(m.group("rank"));
                if (rank < 1_000) return "§6" + rank;
                if (rank < 10_000) return "§6" + String.format(java.util.Locale.ROOT, "%.1fk", rank / 1000.0);
            }
        }
        if (on.test("skyblock_level") && menu.equals("SkyBlock Menu") && name.equals("SkyBlock Leveling")) {
            Matcher m = firstMatch(Patterns.get("SH.inventory.item.overlay.skyblocklevel"), lore);
            if (m != null) return m.group("level");
        }
        if (on.test("bestiary") && (menu.contains("Bestiary ➜") || menu.contains("Fishing ➜")) && anyLore(lore, "Deaths: ")) {
            Matcher m = firstMatch(Patterns.get("SH.inventory.item.overlay.bestiarystack"), lore);
            if (m != null) {
                int tier = level(m.group("tier"));
                if (tier > 0) return String.valueOf(tier - 1);
            } else {
                int tier = level(lastWord(name));
                if (tier >= 0) return String.valueOf(tier);
            }
        }
        return null;
    }

    // ranchers boots cap at 400, or 500 with black cat, racing helmet, or cactus knife in the garden
    private static int speedCap(Minecraft mc) {
        if (Vitals.petName.contains("Black Cat")) return 500;
        if (mc.player == null) return 400;
        if (Gain.sbId(mc.player.getItemBySlot(EquipmentSlot.HEAD)).equals("RACING_HELMET")) return 500;
        return "GARDEN".equals(GameState.island) && Gain.sbId(mc.player.getMainHandItem()).equals("CACTUS_KNIFE") ? 500 : 400;
    }

    private static Item read(ItemStack stack, Minecraft mc) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        String id = tag.getStringOr("id", "");
        var screen = mc.gui == null ? null : mc.gui.screen();
        return new Item(id, GameState.strip(stack.getHoverName().getString()).trim(), LegacyText.name(stack), LegacyText.lore(stack), tag,
                screen == null ? "" : GameState.strip(screen.getTitle().getString()).trim(), id.equals("RANCHERS_BOOTS") ? speedCap(mc) : 400);
    }

    private static boolean option(Feature f, String key) {
        Option o = f.option(key);
        return o == null || o.bool();
    }

    // what to draw instead of the stack count, null = leave it
    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String inMenu = io.github.zmuzy.sbc.item.menus.MenuMarks.number(stack);
        if (inMenu != null) return inMenu;
        String quiver = io.github.zmuzy.sbc.item.menus.ItemMarks.quiver(stack);
        if (quiver != null) return quiver;
        String power = MagicalPowerDisplay.of(stack);
        if (power != null) return power;
        if (stack.getCount() > 1 || !Features.on("item_numbers")) return null;
        // recomputed once in a while, not per slot per frame. short enough that a changed option or held item shows up soon
        long now = System.currentTimeMillis();
        if (now - clearedAt > 2000) {
            CACHE.clear();
            clearedAt = now;
        }
        if (CACHE.containsKey(stack)) return CACHE.get(stack);
        Feature f = Features.get("item_numbers");
        String text = number(read(stack, Minecraft.getInstance()), key -> option(f, key));
        CACHE.put(stack, text);
        return text;
    }

    public static void register() {
        Feature f = new Feature("item_numbers", "Items", "Numbers on items", "A small number on an item's icon at the bottom right: pests in a vacuum, Kuudra Key and Crimson armor tiers, Dungeon Potion levels, skill and collection levels in their menus, and more. Each can be switched off.", "SkyHanni: Item Number")
                .when(() -> GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV);
        for (String[] o : OPTIONS) f.option(Option.bool("item_numbers." + o[0], o[1], "", true));
        Features.add(f);
    }
}
