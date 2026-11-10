// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.misc;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker skyblock/barn HungryHiker + TreasureHunter: their farming islands riddles become the answer
public final class BarnSolvers {
    private BarnSolvers() {}

    public static final String HIKER = "barn_hungry_hiker", HUNTER = "barn_treasure_hunter";
    private static final Pattern HIKER_LINE = Pattern.compile("^\\[NPC] Hungry Hiker: (The food I want is|(I asked for) food that is) ([a-zA-Z, '\\-]*\\.)$");
    private static final Pattern HUNTER_LINE = Pattern.compile("^\\[NPC] Treasure Hunter: ([a-zA-Z, '\\-\\.]*)$");
    // description -> the foods name key
    static final Map<String, String> FOODS = new HashMap<>();
    // hint -> where to mine
    static final Map<String, String> SPOTS = new HashMap<>();

    static {
        FOODS.put("from a cow.", "item.minecraft.cooked_beef");
        FOODS.put("meat from a fowl.", "item.minecraft.cooked_chicken");
        FOODS.put("red on the inside, green on the outside.", "item.minecraft.melon_slice");
        FOODS.put("a cooked potato.", "item.minecraft.baked_potato");
        FOODS.put("a stew.", "item.minecraft.rabbit_stew");
        FOODS.put("a grilled meat.", "item.minecraft.cooked_porkchop");
        FOODS.put("red and crunchy.", "item.minecraft.apple");
        FOODS.put("made of wheat.", "item.minecraft.bread");
        SPOTS.put("There's a treasure chest somewhere in a small cave in the gorge.", "258 70 -492");
        SPOTS.put("I was in the desert earlier, and I saw something near a red sand rock.", "357 82 -319");
        SPOTS.put("There's this guy who collects animals to experiment on, I think I saw something near his house.", "259 184 -564");
        SPOTS.put("There's a small house in the gorge, I saw some treasure near there.", "297 87 -562");
        SPOTS.put("There's this guy who says he has the best sheep in the world. I think I saw something around his hut.", "392 85 -372");
        SPOTS.put("I spotted something by an odd looking mushroom on one of the ledges in the Mushroom Gorge, you should check it out.", "305 73 -557");
        SPOTS.put("There are some small ruins out in the desert, might want to check them out.", "320 102 -471");
        SPOTS.put("Some dirt was kicked up by the water pool in the overgrown Mushroom Cave. Have a look over there.", "234 56 -410");
        SPOTS.put("There are some old stone structures in the Mushroom Gorge, give them a look.", "223 54 -503");
        SPOTS.put("In the Mushroom Gorge where blue meets the ceiling and floor, you will find what you are looking for.", "205 42 -527");
        SPOTS.put("There was a haystack with a crop greener than usual around it, I think there is something near there.", "334 82 -389");
        SPOTS.put("There's a single piece of tall grass growing in the desert, I saw something there.", "283 76 -363");
        SPOTS.put("I saw some treasure by a cow skull near the village.", "141 77 -397");
        SPOTS.put("Near a melon patch inside a tunnel in the mountain I spotted something.", "257 100 -569");
        SPOTS.put("I saw something near a farmer's cart, you should check it out.", "155 90 -591");
        SPOTS.put("I remember there was a stone pillar made only of cobblestone in the oasis, could be something there.", "122 66 -409");
        SPOTS.put("I thought I saw something near the smallest stone pillar in the oasis.", "94 65 -455");
        SPOTS.put("I found something by a mossy stone pillar in the oasis, you should take a look.", "179 93 -537");
        SPOTS.put("Down in the glowing Mushroom Cave, there was a weird looking mushroom, check it out.", "182 44 -451");
        SPOTS.put("Something caught my eye by the red sand near the bridge over the gorge.", "306 105 -489");
        SPOTS.put("I seem to recall seeing something near the well in the village.", "170 77 -375");
        SPOTS.put("I was down near the lower oasis yesterday, I think I saw something under the bridge.", "142 69 -448");
        SPOTS.put("I was at the upper oasis today, I recall seeing something on the cobblestone stepping stones.", "188 77 -459");
    }

    // hints spot, or null
    static String spot(String plain) {
        Matcher m = HUNTER_LINE.matcher(plain.trim());
        return m.matches() ? SPOTS.get(m.group(1)) : null;
    }

    // "The food I want is" + name key, or null
    static String[] food(String plain) {
        Matcher m = HIKER_LINE.matcher(plain.trim());
        if (!m.matches()) return null;
        String key = FOODS.get(m.group(3));
        return key == null ? null : new String[]{m.group(2) != null ? m.group(2) : m.group(1), key};
    }

    private static ChatBus.Result hiker(ChatBus.Line line) {
        String[] f = food(line.plain());
        if (f == null) return ChatBus.PASS;
        return new ChatBus.Replace(Component.literal("§e[NPC] Hungry Hiker§f: " + f[0] + " ").append(Component.translatable(f[1])).append("."));
    }

    private static ChatBus.Result hunter(ChatBus.Line line) {
        String spot = spot(line.plain());
        if (spot == null) return ChatBus.PASS;
        MutableComponent c = Component.literal("§e[NPC] Treasure Hunter§f: Go mine around " + spot + " ");
        c.append(Component.literal("§a[+ waypoint]").withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/sbc wp at " + spot + " Treasure"))));
        return new ChatBus.Replace(c);
    }

    public static void register() {
        Feature a = Features.add(new Feature(HIKER, "Farming Islands", "Hungry Hiker", "Says which food the Hungry Hiker on the Farming Islands means.", "Skyblocker: Solve Hungry Hiker").when(() -> GameState.inSkyBlock));
        Feature b = Features.add(new Feature(HUNTER, "Farming Islands", "Treasure Hunter", "Turns the Treasure Hunter's hint into where to mine, with a waypoint to click.", "Skyblocker: Solve Treasure Hunter").when(() -> GameState.inSkyBlock));
        ChatBus.register(ChatBus.COMPACT, HIKER, a, BarnSolvers::hiker);
        ChatBus.register(ChatBus.COMPACT, HUNTER, b, BarnSolvers::hunter);
    }
}
