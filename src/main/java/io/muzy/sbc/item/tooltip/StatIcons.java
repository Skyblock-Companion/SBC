// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.item.tooltip;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// symbol + color hypixel uses for each stat, so a stat line can be drawn as "icon  Name ····· value"
// the symbols are what skyblock prints in the action bar and lore, plain unicode thats in the game font
public final class StatIcons {
    private StatIcons() {}

    public record Stat(String icon, int color) {}

    private static final Map<String, Stat> BY_NAME = new HashMap<>();

    private static void put(String icon, int color, String... names) {
        for (String n : names) BY_NAME.put(n.toLowerCase(Locale.ROOT), new Stat(icon, color));
    }

    private static final int RED = 0xFFFF5555, GREEN = 0xFF55FF55, AQUA = 0xFF55FFFF, BLUE = 0xFF5555FF, YELLOW = 0xFFFFFF55, WHITE = 0xFFFFFFFF,
            DARK_RED = 0xFFAA0000, DARK_GREEN = 0xFF00AA00, GOLD = 0xFFFFAA00, DARK_PURPLE = 0xFFAA00AA, DARK_AQUA = 0xFF00AAAA, LIGHT_PURPLE = 0xFFFF55FF;

    static {
        put("❤", RED, "Health");
        put("❁", RED, "Damage");
        put("❈", GREEN, "Defense");
        put("❁", RED, "Strength");
        put("✎", AQUA, "Intelligence");
        put("☠", BLUE, "Crit Damage");
        put("☣", BLUE, "Crit Chance");
        put("⚔", YELLOW, "Attack Speed", "Bonus Attack Speed");
        put("๑", RED, "Ability Damage");
        put("❂", WHITE, "True Defense");
        put("⫽", RED, "Ferocity");
        put("❤", RED, "Health Regen");
        put("♨", DARK_RED, "Vitality");
        put("☄", GREEN, "Mending");
        put("ⓩ", YELLOW, "Swing Range");
        put("⸕", DARK_GREEN, "Breaking Power");
        put("⸕", GOLD, "Mining Speed");
        put("☘", GOLD, "Mining Fortune", "Ore Fortune", "Block Fortune", "Dwarven Metal Fortune", "Gemstone Fortune");
        put("▚", YELLOW, "Mining Spread", "Gemstone Spread");
        put("✧", DARK_PURPLE, "Pristine");
        put("☘", GOLD, "Farming Fortune", "Wheat Fortune", "Carrot Fortune", "Potato Fortune", "Pumpkin Fortune", "Melon Fortune", "Melon Slice Fortune",
                "Cactus Fortune", "Sugar Cane Fortune", "Nether Wart Fortune", "Cocoa Beans Fortune", "Mushroom Fortune", "Sunflower Fortune", "Moonflower Fortune", "Wild Rose Fortune");
        put("ᴸ", DARK_GREEN, "Bonus Pest Chance");
        put("☘", GOLD, "Foraging Fortune", "Fig Fortune", "Mangrove Fortune", "Helix Fortune");
        put("∮", DARK_GREEN, "Sweep");
        put("☂", AQUA, "Fishing Speed");
        put("α", DARK_AQUA, "Sea Creature Chance");
        put("⫽", BLUE, "Double Hook Chance");
        put("⸕", GOLD, "Trophy Fish Chance", "Trophy Chance");
        put("⸕", GOLD, "Treasure Chance");
        put("❣", AQUA, "Charm Chance");
        put("☯", DARK_AQUA, "Combat Wisdom", "Mining Wisdom", "Farming Wisdom", "Foraging Wisdom", "Fishing Wisdom", "Enchanting Wisdom",
                "Alchemy Wisdom", "Carpentry Wisdom", "Runecrafting Wisdom", "Social Wisdom", "Taming Wisdom", "Hunting Wisdom");
        put("❦", LIGHT_PURPLE, "Hunting Fortune");
        put("✦", WHITE, "Speed");
        put("✯", AQUA, "Magic Find");
        put("♣", LIGHT_PURPLE, "Pet Luck");
        put("♨", RED, "Heat Resistance");
        put("❄", AQUA, "Cold Resistance");
        put("☉", DARK_AQUA, "Respiration");
        put("⚓", BLUE, "Pressure Resistance");
        put("☠", DARK_PURPLE, "Fear");
        put("☉", GREEN, "Rift Time");
        put("❤", RED, "Hearts", "Rift Hearts");
        put("✎", AQUA, "Mana Regen");
        put("❂", DARK_PURPLE, "Rift Damage");
        put("⬣", GOLD, "Gear Score");
    }

    // stat for a lore label ("Crit Chance"), null if not a stat line
    public static Stat of(String name) {
        return name == null ? null : BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
    }
}
