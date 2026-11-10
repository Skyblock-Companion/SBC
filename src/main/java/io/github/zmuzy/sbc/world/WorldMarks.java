// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.world;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.combat.CombatMarks;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// who + what stands out in the world. from skyblocker MushroomDesertGlowAdder (trevors animals) + skyhanni
// misc/GlowingDroppedItems, MarkedPlayerManager, TeleportPad stuff...
public final class WorldMarks {
    public static final String TREVOR = "trevor_glow", DROPPED = "glowing_dropped_items", MARKED = "marked_players_world", FRUIT = "fruit_bowl_players", PADS = "teleport_pads", BREWING = "brewing_stand_names";

    private WorldMarks() {}

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    // --- trevors animals ---

    private static final String[] PELT_RARITIES = {"Untrackable", "Trackable", "Undetected", "Endangered", "Elusive"};
    private static final int[] PELT_COLORS = {0x55FF55, 0xFFFFFF, 0x5555FF, 0xAA00AA, 0xFFAA00};

    // from skyblocker MushroomDesertGlowAdder.getGlowColor: color of "Trackable Cow" etc, -1 for any other name
    static int peltColor(String name, String animal) {
        for (int i = 0; i < PELT_RARITIES.length; i++) if (name.contains(PELT_RARITIES[i] + " " + animal)) return PELT_COLORS[i];
        return -1;
    }

    private static String animal(Entity e) {
        if (e instanceof Cow) return "Cow";
        if (e instanceof Pig) return "Pig";
        if (e instanceof Sheep) return "Sheep";
        if (e instanceof Rabbit) return "Rabbit";
        if (e instanceof Chicken) return "Chicken";
        if (e instanceof Horse) return "Horse";
        return null;
    }

    private static void trevorGlow(BiConsumer<Entity, Integer> mark) {
        if (!Features.on(TREVOR) || !"THE_FARMING_ISLANDS".equals(GameState.island)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            String animal = animal(e);
            if (animal == null) continue;
            int color = -1;
            for (ArmorStand st : e.level().getEntitiesOfClass(ArmorStand.class, e.getBoundingBox().inflate(0, 2, 0))) {
                color = peltColor(st.getName().getString(), animal);
                if (color >= 0) break;
            }
            if (color < 0) continue;
            CombatMarks.seenGlow(mark, e, color);
        }
    }

    // --- items on the ground ---

    // skyhanni LorenzRarity colors, on the rarity line an items lore ends with
    private static final Map<String, Integer> RARITY = Map.ofEntries(Map.entry("COMMON", 0xFFFFFF), Map.entry("UNCOMMON", 0x55FF55), Map.entry("RARE", 0x5555FF), Map.entry("EPIC", 0xAA00AA),
            Map.entry("LEGENDARY", 0xFFAA00), Map.entry("MYTHIC", 0xFF55FF), Map.entry("DIVINE", 0x55FFFF), Map.entry("SPECIAL", 0xFF5555), Map.entry("VERY SPECIAL", 0xFF5555),
            Map.entry("ULTIMATE", 0xAA0000), Map.entry("ADMIN", 0xAA0000));

    static int rarityColor(List<String> lore) {
        for (int i = lore.size() - 1; i >= 0; i--) {
            String line = GameState.strip(lore.get(i)).trim();
            if (line.isEmpty()) continue;
            String[] words = line.split(" ");
            String word = words[0].equals("a") && words.length > 1 ? words[1] : words[0];
            Integer c = word.equals("VERY") ? RARITY.get("VERY SPECIAL") : RARITY.get(word);
            if (c != null) return c;
            break;
        }
        return RARITY.get("COMMON");
    }

    private static void droppedGlow(BiConsumer<Entity, Integer> mark) {
        if (!Features.on(DROPPED) || !sb()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity item) || e.isCurrentlyGlowing()) continue;
            ItemStack stack = item.getItem();
            mark.accept(e, rarityColor(LegacyText.lore(stack)));
        }
    }

    // --- marked players ---

    private static void markedGlow(BiConsumer<Entity, Integer> mark) {
        if (!Features.on(MARKED) || !sb()) return;
        Set<String> names = io.github.zmuzy.sbc.chat.MarkedPlayers.marked();
        if (names.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int color = Features.get(MARKED).option("color").color() & 0xFFFFFF;
        for (var p : mc.level.players()) {
            if (p instanceof RemotePlayer && names.contains(p.getName().getString().toLowerCase(Locale.ROOT))) CombatMarks.seenGlow(mark, p, color);
        }
    }

    // --- fruit bowl ---

    // skyhanni FruitBowlFeatures patterns: chat without colors, lore with
    static final Pattern FRUIT_CLICKED = Pattern.compile("FRUIT BOWL! (?<player>.*) profile name is (?<profile>[a-zA-Z]*)!"), FRUIT_DUPLICATE = Pattern.compile("You already found this profile name in your bowl!"),
            FRUIT_FOUND = Pattern.compile("(?<profile>[a-zA-Z]*) has been added to the fruit bowl!"), FRUIT_ALL = Pattern.compile("FRUITALICIOUS! You completed your fruit bowl!");
    private static final Set<String> clicked = new LinkedHashSet<>();
    private static String lastClick;

    // from skyhanni cleanPlayerName: "[MVP+] Name's" -> Name
    static String cleanName(String raw) {
        String s = raw.replaceAll("^\\[[^\\]]+\\] ", "").trim();
        return s.endsWith("'s") ? s.substring(0, s.length() - 2) : s;
    }

    static void fruitChat(String plain) {
        String t = plain.trim();
        if (FRUIT_ALL.matcher(t).matches()) {
            clicked.clear();
            lastClick = null;
            return;
        }
        Matcher m = FRUIT_CLICKED.matcher(t);
        if (m.matches()) {
            lastClick = cleanName(m.group("player"));
            return;
        }
        if (lastClick != null && (FRUIT_DUPLICATE.matcher(t).matches() || FRUIT_FOUND.matcher(t).matches())) {
            clicked.add(lastClick);
            lastClick = null;
        }
    }

    private static void fruitGlow(BiConsumer<Entity, Integer> mark) {
        if (!Features.on(FRUIT) || !sb()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || !Gain.sbId(me.getMainHandItem()).equals("FRUIT_BOWL")) return;
        Feature f = Features.get(FRUIT);
        int can = f.option("can").color() & 0xFFFFFF, cannot = f.option("cannot").color() & 0xFFFFFF;
        for (var p : mc.level.players()) {
            if (p == me || !(p instanceof RemotePlayer) || p.getUUID().version() != 4) continue;
            CombatMarks.seenGlow(mark, p, clicked.contains(p.getName().getString()) ? cannot : can);
        }
    }

    // --- teleport pads ---

    static final Pattern PAD_NAME = Pattern.compile("§.✦ §aWarp To (?<name>.*)"), PAD_NO_NAME = Pattern.compile("§.✦ §cNo Destination");
    private static final Pattern PAD_NUMBER = Pattern.compile("(?<number>.*) teleport pad");
    private static final Map<String, Integer> NUMBERS = new HashMap<>();

    static {
        String[] base = {"one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
        String[] tens = {"twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
        for (int i = 0; i < base.length; i++) NUMBERS.put(base[i], i + 1);
        for (int t = 0; t < tens.length; t++) {
            NUMBERS.put(tens[t], (t + 2) * 10);
            for (int i = 0; i < 9; i++) NUMBERS.put(tens[t] + base[i], (t + 2) * 10 + i + 1);
        }
    }

    // from skyhanni TeleportPadInventoryNumber: pad number from its name, -1 = none
    static int padNumber(String cleanName) {
        Matcher m = PAD_NUMBER.matcher(cleanName.toLowerCase(Locale.ROOT).trim());
        if (!m.matches()) return -1;
        Integer n = NUMBERS.get(m.group("number").trim());
        return n == null ? -1 : n;
    }

    private static void padMenu(MenuView v) {
        if (!Features.get(PADS).option("numbers").bool()) return;
        for (int slot = 0; slot < v.top; slot++) {
            int n = padNumber(v.item(slot).clean);
            if (n > 0) v.number(slot, Integer.toString(n));
        }
    }

    // from skyhanni TeleportPadCompactName for the drawn name: just the destination, nothing over a pad without one
    public static Component nameTag(Entity entity, Component name) {
        if (name == null || !(entity instanceof ArmorStand) || !"PRIVATE_ISLAND".equals(GameState.island) || !Features.on(PADS) || !Features.get(PADS).option("compact_name").bool()) return name;
        String coded = LegacyText.of(name);
        if (PAD_NO_NAME.matcher(coded).matches()) return null;
        Matcher m = PAD_NAME.matcher(coded);
        return m.matches() ? Component.literal(m.group("name")) : name;
    }

    // --- brewing stand ---

    // from skyhanni BrewingStandOverlay: ingredient (13), progress (21), output (42), written by their slots
    private static void brewingMenu(MenuView v) {
        for (int slot : new int[]{13, 21, 42}) {
            String name = v.item(slot).name;
            if (name.isEmpty() || name.contains(" or ")) continue;
            v.label(slot, name, slot == 21 ? 58 : 3, -5, 0.8f);
        }
    }

    public static void register() {
        Features.add(new Feature(TREVOR, "Farming Islands", "Trevor's animals", "The animals of Trevor's tasks get an outline in the colour of their rarity.", "Skyblocker: trapper animal highlighter")
                .when(() -> "THE_FARMING_ISLANDS".equals(GameState.island) || SbcMod.DEV));
        Features.add(new Feature(DROPPED, "World", "Outline on dropped items", "Items on the ground get an outline in their rarity's colour.", "SkyHanni: Glowing Dropped Items")
                .when(WorldMarks::sb).defaultOff());
        Features.add(new Feature(MARKED, "World", "Marked players in the world", "Players you marked get an outline when you can see them.", "SkyHanni: Marked Players")
                .when(WorldMarks::sb)
                .option(Option.color("marked_players_world.color", "Colour", "", 0xFFFFFF55)));
        Features.add(new Feature(FRUIT, "Events", "Fruit Bowl: who to click", "While you hold a Fruit Bowl, players you have not clicked yet get a green outline and the others a grey one.", "SkyHanni: Fruit Bowl Player Highlighter")
                .when(WorldMarks::sb).chat(WorldMarks::fruitChat).left(() -> {
                    clicked.clear();
                    lastClick = null;
                })
                .option(Option.color("fruit_bowl_players.can", "Not clicked yet", "", 0xFF55FF55))
                .option(Option.color("fruit_bowl_players.cannot", "Already clicked", "", 0xFF4C4C4C)));
        Features.add(new Feature(PADS, "Menus", "Teleport pads", "On your Private Island: pad numbers in the Set Destination menu, and shorter names over the pads.", "SkyHanni: Teleport Pads")
                .when(() -> "PRIVATE_ISLAND".equals(GameState.island) || SbcMod.DEV)
                .option(Option.bool("teleport_pads.numbers", "Numbers in the menu", "", true))
                .option(Option.bool("teleport_pads.compact_name", "Only the destination over a pad", "No Warp To, and nothing over a pad without a destination.", false)));
        Features.add(new Feature(BREWING, "Menus", "Names in the Brewing Stand", "The ingredient's, the progress' and the potion's names, written by their slots.", "SkyHanni: Brewing Stand Overlay")
                .when(WorldMarks::sb));
        CombatMarks.glowing(WorldMarks::trevorGlow);
        CombatMarks.glowing(WorldMarks::droppedGlow);
        CombatMarks.glowing(WorldMarks::markedGlow);
        CombatMarks.glowing(WorldMarks::fruitGlow);
        MenuMarks.add(PADS, "Set Destination", WorldMarks::padMenu);
        MenuMarks.add(BREWING, "Brewing Stand", WorldMarks::brewingMenu);
    }
}
