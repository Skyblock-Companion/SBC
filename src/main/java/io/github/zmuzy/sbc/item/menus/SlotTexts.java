// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.menus;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// skyblocker slot text: short text in a slot corner
// only the adders item/ItemNumbers and item/SlotInfo dont already cover (those do bestiary, collection, skill
// + sb levels, ranchers speed, cake year, pet levels, stats tuning points)
// patterns + numbers are skyblockers own slottext/adders
public final class SlotTexts {
    static final int CREAM = 0xFFDDC1, GOLD = 0xE5B80B, LIGHT_ORANGE = 0xFAB387, LIGHT_YELLOW = 0xF9E2AF, LIGHT_PURPLE = 0xCBA6F7, LIGHT_RED = 0xFF7276, LIGHT_GREEN = 0x90EE90, BLUE = 0x5555FF, PINK = 0xFF55FF;

    public static KeyMapping key;
    private static boolean toggled = true;

    private SlotTexts() {}

    private static final String[][] ADDERS = {
            {"catacombs", "Dungeon levels", "Dungeoneering, Dungeon Classes and Ready Up."},
            {"chips", "Chip levels", "In Manage Chips."},
            {"choose_pet", "Choose Pet levels", "In the menus that ask for a pet."},
            {"community_shop", "Community Shop upgrades", "Level, in progress, ready to claim, maxed."},
            {"crop_milestones", "Crop Milestones", ""},
            {"enchant_abbreviation", "Enchantment on a book", "Two or three letters, top right."},
            {"enchant_level", "Enchantment level on a book", ""},
            {"essence_shop", "Essence Shop levels", "And the essence you own."},
            {"evolving", "Evolving item bonus", "The bonus of Jyrre, Truffle and others."},
            {"garden_upgrades", "Garden upgrade tiers", ""},
            {"hotm", "Heart of the Mountain perks", ""},
            {"hotf", "Heart of the Forest perks", ""},
            {"hunting_toolkit", "Part of a Hunting Toolkit", ""},
            {"minion", "Minion tier", "Also on minions in menus and recipes."},
            {"potion", "Potion level", ""},
            {"power_stones", "Learned power stones", ""},
            {"prehistoric_egg", "Prehistoric Egg distance", ""},
            {"guide", "SkyBlock Guide tasks", "A tick or a cross."},
            {"your_essence", "Your Essence amounts", ""},
    };

    static boolean adder(String name) {
        return MenuMarks.on("slot_text", name);
    }

    // from skyblocker SlotTextManager.isEnabled
    static boolean showing() {
        Feature f = Features.get("slot_text");
        if (f == null || !f.active()) return false;
        return switch (f.option("slot_text.mode").choice()) {
            case "Hold to show" -> Keys.down(key);
            case "Hold to hide" -> !Keys.down(key);
            case "Press to toggle" -> toggled;
            default -> true;
        };
    }

    // key pressed in a menu
    static void pressed() {
        toggled = !toggled;
    }

    private static int roman(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(c -> "IVXLCDM".indexOf(c) >= 0) ? GameState.roman(s) : 0;
    }

    private static boolean romanValid(String s) {
        return roman(s) > 0;
    }

    private static boolean digits(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(Character::isDigit);
    }

    // skyblocker SHORT_FLOAT_NUMBERS: 1.2K
    static String shortNumber(long v) {
        if (v < 1000) return String.valueOf(v);
        String[] units = {"K", "M", "B", "T"};
        double d = v;
        int u = -1;
        while (d >= 1000 && u < units.length - 1) {
            d /= 1000;
            u++;
        }
        String s = String.format(Locale.ROOT, "%.1f", d);
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s + units[u];
    }

    // by title

    private static final Pattern LEVEL_NAME = Pattern.compile(".*?(?:(?: (?<arabic>\\d+)| (?<roman>[IVXLC]+))(?: ✯)?)?");

    static void dungeoneering(MenuView v) {
        for (int slot : new int[]{12, 29, 30, 31, 32, 33}) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            Matcher m = LEVEL_NAME.matcher(it.clean);
            if (!m.matches()) continue;
            String arabic = m.group("arabic"), roman = m.group("roman");
            String level = arabic != null ? arabic : roman != null && romanValid(roman) ? String.valueOf(roman(roman)) : roman == null ? "0" : null;
            v.corner(slot, MenuView.BOTTOM_LEFT, level, CREAM);
        }
    }

    private static String bracketedLevel(String name) {
        if (!name.startsWith("[Lvl ")) return null;
        int index = name.indexOf(']');
        return index == -1 ? null : name.substring(5, index);
    }

    static void dungeonClasses(MenuView v, int from, int to) {
        for (int slot = from; slot <= to; slot++) {
            String level = bracketedLevel(v.item(slot).clean);
            if (digits(level)) v.corner(slot, MenuView.BOTTOM_LEFT, level, CREAM);
        }
    }

    private static final Pattern LEVEL = Pattern.compile("Level (?<level>\\d+)/?(?<max>\\d+)?");

    static void chips(MenuView v) {
        for (int slot = 18; slot <= 35; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty() || it.model.equals("gray_dye") || it.cleanLore.isEmpty()) continue;
            Matcher m = LEVEL.matcher(it.cleanLore.get(0));
            if (!m.matches()) continue;
            String level = m.group("level"), max = m.group("max");
            int color = CREAM;
            if (level.equals(max)) {
                color = switch (max) {
                    case "10" -> 0x1D8DE8;
                    case "15" -> 0xA40BE2;
                    case "20" -> GOLD;
                    default -> CREAM;
                };
            }
            v.corner(slot, MenuView.BOTTOM_RIGHT, level, color);
        }
    }

    private static final Pattern AUTOPET = Pattern.compile("Equip: ⭐? ?\\[Lvl (\\d+)].*"), PET_LEVEL = Pattern.compile("⭐? ?\\[Lvl (\\d+)].*");

    static void choosePet(MenuView v) {
        for (int slot = 9; slot <= 44; slot++) {
            MenuView.Item it = v.item(slot);
            if (!it.model.equals("player_head") || it.cleanLore.contains("Except if:")) continue;
            Matcher m = it.firstClean(AUTOPET);
            if (m == null) {
                m = PET_LEVEL.matcher(it.clean);
                if (!m.matches()) continue;
            }
            String level = m.group(1);
            if (!digits(level) || "100".equals(level) && (!it.clean.contains("Dragon") || it.clean.contains("Ender Dragon")) || "200".equals(level)) continue;
            v.corner(slot, MenuView.BOTTOM_RIGHT, level, CREAM);
        }
    }

    static void communityShop(MenuView v) {
        int screen = -1;
        for (int slot = 10; slot <= 14; slot++) if (v.item(slot).model.equals("lime_stained_glass_pane")) screen = slot - 10;
        if (screen != 1) return; // upgrades, the only page with this
        for (int slot = 18; slot <= 44; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty() || it.cleanLore.isEmpty()) continue;
            String roman = it.clean.substring(it.clean.lastIndexOf(' ') + 1);
            if (!romanValid(roman)) continue;
            switch (it.lastClean()) {
                case "Maxed out!" -> v.corner(slot, MenuView.BOTTOM_LEFT, "Max", LIGHT_ORANGE);
                case "Currently upgrading!", "Click to instantly upgrade!" -> v.corner(slot, MenuView.BOTTOM_LEFT, "§l⏰", LIGHT_YELLOW);
                case "Click to claim!" -> v.corner(slot, MenuView.BOTTOM_LEFT, "§l✅", 0xA6E3A1);
                default -> v.corner(slot, MenuView.BOTTOM_LEFT, String.valueOf(roman(roman)), LIGHT_PURPLE);
            }
        }
    }

    static void cropMilestones(MenuView v) {
        for (int slot = 0; slot < Math.min(54, v.top); slot++) {
            MenuView.Item it = v.items[slot];
            int space = it.clean.lastIndexOf(' ');
            if (space == -1) continue;
            String number = it.clean.substring(space + 1);
            if (!digits(number)) continue;
            v.corner(slot, MenuView.BOTTOM_RIGHT, number, it.cleanHas("Max tier reached!") ? GOLD : CREAM);
        }
    }

    private static final Pattern ESSENCE_LEVEL = Pattern.compile("^[\\w ]+ (?<level>[IVXLCDM]+)$"), ESSENCE_AMOUNT = Pattern.compile("Your \\w+ Essence: (?<essence>[\\d,]+)");

    private static Matcher findLore(MenuView.Item it, Pattern p) {
        for (String l : it.cleanLore) {
            Matcher m = p.matcher(l);
            if (m.find()) return m;
        }
        return null;
    }

    static void essenceShop(MenuView v) {
        for (int slot = 0; slot < Math.min(54, v.top); slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            Matcher level = ESSENCE_LEVEL.matcher(it.clean);
            if (level.matches()) {
                int n = roman(level.group("level"));
                if (!it.cleanHas("UNLOCKED")) n -= 1;
                v.corner(slot, MenuView.BOTTOM_RIGHT, String.valueOf(n), CREAM);
                continue;
            }
            Matcher amount = findLore(it, ESSENCE_AMOUNT);
            if (amount != null) v.corner(slot, MenuView.BOTTOM_RIGHT, shortNumber(Long.parseLong(amount.group("essence").replace(",", ""))), CREAM);
        }
    }

    private static final Pattern TIER = Pattern.compile("Current Tier: (?<tier>\\d+)/(?<max>\\d+)");

    static void gardenUpgrades(MenuView v) {
        for (int slot = 0; slot < Math.min(54, v.top); slot++) {
            Matcher m = findLore(v.items[slot], TIER);
            if (m == null) continue;
            v.corner(slot, MenuView.BOTTOM_RIGHT, m.group("tier"), m.group("tier").equals(m.group("max")) ? GOLD : CREAM);
        }
    }

    // skyblocker HeartOfTheXAdder. no "max" group = the perk is maxed
    static void heart(MenuView v, Pattern nothing, String notLevelled, boolean byItemModel) {
        for (int slot = 0; slot <= 44 && slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty() || it.cleanLore.isEmpty() || (byItemModel ? it.itemModel : it.model).equals(notLevelled)) continue;
            Matcher m = LEVEL.matcher(it.cleanLore.get(0));
            if (!m.matches()) continue;
            v.corner(slot, MenuView.BOTTOM_RIGHT, m.group("level"), m.group("max") == null ? GOLD : CREAM);
        }
    }

    private static final Pattern LEARNED = Pattern.compile("Learned: (Yes|Not Yet) (?<symbol>[✖✔])");

    static void powerStones(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            Matcher m = findLore(v.items[slot], LEARNED);
            if (m == null) continue;
            if (m.group("symbol").equals("✖")) v.corner(slot, MenuView.BOTTOM_RIGHT, "✘", LIGHT_RED);
            else v.corner(slot, MenuView.BOTTOM_RIGHT, "✔", LIGHT_GREEN);
        }
    }

    private static final Pattern GUIDE = Pattern.compile("^(?<symbol>[✖✔])\\s*(?<text>.+)");

    static void guide(MenuView v) {
        for (int slot = 18; slot <= 44 && slot < v.top; slot++) {
            Matcher m = GUIDE.matcher(v.items[slot].clean);
            if (!m.matches()) continue;
            if (m.group("symbol").equals("✖")) v.corner(slot, MenuView.BOTTOM_RIGHT, "✘", LIGHT_RED);
            else v.corner(slot, MenuView.BOTTOM_RIGHT, "✔", LIGHT_GREEN);
        }
    }

    private static final Pattern YOUR_ESSENCE = Pattern.compile("You currently own (?<essence>[\\d,]+)");

    static void yourEssence(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (!it.clean.contains("Essence") || it.cleanLore.isEmpty()) continue;
            Matcher m = YOUR_ESSENCE.matcher(it.cleanLore.get(0));
            boolean found = m.find();
            if (!found && it.cleanLore.size() >= 3) {
                m = ESSENCE_AMOUNT.matcher(it.cleanLore.get(it.cleanLore.size() - 3));
                found = m.find();
            }
            if (found) v.corner(slot, MenuView.BOTTOM_RIGHT, shortNumber(Long.parseLong(m.group("essence").replace(",", ""))), CREAM);
        }
    }

    // any item, any menu

    private static final Pattern MINION = Pattern.compile(".* Minion ([IVXLCDM]+)"), BONUS = Pattern.compile("\\+?([\\d.]+)");
    private static final Map<String, String> EVOLVING = Map.of("NEW_BOTTLE_OF_JYRRE", "Current Bonus: ", "DARK_CACAO_TRUFFLE", "Current Bonus: ", "DISCRITE", "Current Bonus: ", "MOBY_DUCK", "Current Bonus: ",
            "ROSEWATER_FLASK", "Current Bonus: ", "NOT_VERY_MOLDY_BREAD", "Current Bonus: ", "TRAINING_WEIGHTS", "Strength Gain: ", "BOTTLE_OF_JYRRE", "Intelligence Bonus: ");

    static void anyItem(MenuView v) {
        boolean abbreviation = adder("slot_text.enchant_abbreviation"), level = adder("slot_text.enchant_level"), toolkit = adder("slot_text.hunting_toolkit"), minion = adder("slot_text.minion");
        boolean potion = adder("slot_text.potion"), egg = adder("slot_text.prehistoric_egg"), evolving = adder("slot_text.evolving");
        for (int slot = 0; slot < v.itemsLength(); slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            if (it.model.equals("enchanted_book")) {
                CompoundTag enchants = it.tag.getCompoundOrEmpty("enchantments");
                if (it.clean.equals("Enchanted Book")) {
                    if (enchants.size() == 1) {
                        String id = enchants.keySet().iterator().next();
                        if (abbreviation) {
                            String normal = EnchantAbbreviations.NORMAL.get(id), ultimate = EnchantAbbreviations.ULTIMATE.get(id);
                            if (normal != null) v.corner(slot, MenuView.TOP_RIGHT, normal, BLUE);
                            else if (ultimate != null) v.corner(slot, MenuView.TOP_RIGHT, ultimate, PINK);
                        }
                        if (level) v.corner(slot, MenuView.BOTTOM_LEFT, String.valueOf(enchants.getIntOr(id, 0)), CREAM);
                    }
                } else if (level) {
                    // on the bazaar the level is in the books name
                    int n = roman(it.clean.substring(it.clean.lastIndexOf(' ') + 1));
                    if (n != 0) v.corner(slot, MenuView.BOTTOM_LEFT, String.valueOf(n), CREAM);
                }
            }
            if (minion && it.model.equals("player_head") && !it.id.contains("_GENERATOR_")) {
                Matcher m = MINION.matcher(it.clean);
                if (m.matches() && romanValid(m.group(1))) v.corner(slot, MenuView.TOP_RIGHT, String.valueOf(roman(m.group(1))), CREAM);
            }
            if (potion && it.tag.contains("potion_level") && !it.clean.contains("Healer") && !it.clean.contains("Class Passives")) v.corner(slot, MenuView.BOTTOM_RIGHT, String.valueOf(it.tag.getIntOr("potion_level", 0)), CREAM);
            if (egg && it.id.equals("PREHISTORIC_EGG") && it.tag.contains("blocks_walked")) {
                int walked = it.tag.getIntOr("blocks_walked", 0);
                v.corner(slot, MenuView.BOTTOM_LEFT, walked < 1000 ? String.valueOf(walked) : walked < 10000 ? String.format(Locale.ROOT, "%.1fk", walked / 1000f) : walked / 1000 + "k", CREAM);
            }
            if (toolkit && it.cleanHas("Part of a Toolkit!")) v.corner(slot, MenuView.TOP_LEFT, "❒", 0xFF5555);
            if (evolving) {
                String prefix = EVOLVING.get(it.id);
                if (prefix != null) bonus(v, slot, it, prefix);
            }
        }
    }

    private static void bonus(MenuView v, int slot, MenuView.Item it, String prefix) {
        for (int i = 0; i < it.cleanLore.size(); i++) {
            String line = it.cleanLore.get(i);
            if (!line.startsWith(prefix)) continue;
            Matcher m = BONUS.matcher(line.substring(prefix.length()));
            if (!m.find()) continue;
            String coloured = it.lore.get(i);
            int at = coloured.indexOf(prefix);
            String code = at >= 0 && at + prefix.length() + 1 < coloured.length() && coloured.charAt(at + prefix.length()) == '§' ? coloured.substring(at + prefix.length(), at + prefix.length() + 2) : "";
            v.corner(slot, MenuView.BOTTOM_LEFT, code + m.group(1), CREAM);
            return;
        }
    }

    static void register(BooleanSupplier skyblock) {
        Feature f = new Feature("slot_text", "Items", "Slot text", "Short texts in the corners of slots: dungeon levels, enchantments on books, chips, perks, upgrades and more. Each can be switched off.", "Skyblocker: slot text")
                .when(skyblock)
                .option(Option.choice("slot_text.mode", "When to show", "The modes that need a key use the Slot text key: give it one in the game's Controls.", "Always", "Always", "Hold to show", "Press to toggle", "Hold to hide"));
        for (String[] a : ADDERS) f.option(Option.bool("slot_text." + a[0], a[1], a[2], true));
        Features.add(f);
        MenuMarks.add("slot_text", t -> true, v -> anyItem(v));
        rule("^Dungeoneering.*", "slot_text.catacombs", SlotTexts::dungeoneering);
        rule("^Dungeon Classes.*", "slot_text.catacombs", v -> dungeonClasses(v, 11, 15));
        rule("^Ready Up.*", "slot_text.catacombs", v -> dungeonClasses(v, 29, 33));
        rule("^Manage Chips$", "slot_text.chips", SlotTexts::chips);
        rule("^(\\(\\d+/\\d+\\) )?Choose Pet$", "slot_text.choose_pet", SlotTexts::choosePet);
        rule("^Community Shop.*", "slot_text.community_shop", SlotTexts::communityShop);
        rule("^Crop Milestones.*", "slot_text.crop_milestones", SlotTexts::cropMilestones);
        rule(".*Essence Shop", "slot_text.essence_shop", SlotTexts::essenceShop);
        rule("^(?:Crop|Greenhouse) Upgrades.*", "slot_text.garden_upgrades", SlotTexts::gardenUpgrades);
        rule("^Heart of the Mountain$", "slot_text.hotm", v -> heart(v, null, "coal", false));
        rule("^Heart of the Forest$", "slot_text.hotf", v -> heart(v, null, "pale_oak_button", true));
        rule("^Power Stones Guide.*", "slot_text.power_stones", SlotTexts::powerStones);
        rule("^(?:\\(\\d+/\\d+\\)\\s+)?Guide ➜ \\w+.*", "slot_text.guide", SlotTexts::guide);
        rule("^(?:Your Essence|Essence Guide).*", "slot_text.your_essence", SlotTexts::yourEssence);
    }

    private static void rule(String title, String option, MenuMarks.Rule rule) {
        Pattern p = Pattern.compile(title);
        MenuMarks.add("slot_text", t -> p.matcher(t).matches(), v -> {
            if (adder(option)) rule.mark(v);
        });
    }
}
