// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what the mines npcs say. skyblocker Fetchur, Puzzler, FossilMuncher answer their riddles
// skyblocker CallMismyla + RedialOnBadSignal, skyhanni DonExpressoFeedingReminder + JoinCrystalHollows offer
// the next step as a click line
// nothing here calls or warps by itself. skyblocker sets a block in the world for the puzzler, here its a mark
public final class NpcHelpers {
    static final String SOLVERS = "mining_npc_solvers", PROMPTS = "mining_call_prompts";
    private static final Pattern FETCHUR = Pattern.compile("^\\[NPC] Fetchur: (?:its|theyre) ([a-zA-Z, \\-]*)$");
    private static final Pattern PUZZLER = Pattern.compile("^\\[NPC] Puzzler: ((?:▲|▶|◀|▼){10})$");
    private static final Pattern MUNCHER = Pattern.compile("^\\[NPC] Fossil Muncher: the fossil i want ([a-zA-Z, \\-]*)$");
    private static final Pattern MISMYLA = Pattern.compile("^([\\w' ]+) Commission Complete! Visit the King to claim your rewards!$");
    private static final Pattern FRED = Pattern.compile("^\\[NPC] (Fred): ✆ .*$");
    private static final Vec3 PASS_SPOT = new Vec3(88, 198, -99);
    private static final Map<String, String> FETCHUR_ANSWERS = Map.ofEntries(
            Map.entry("yellow and see through", "Yellow Stained Glass"),
            Map.entry("circular and sometimes moves", "Compass"),
            Map.entry("expensive minerals", "Mithril"),
            Map.entry("useful during celebrations", "Firework Rocket"),
            Map.entry("hot and gives energy", "Cheap / Decent / Black Coffee"),
            Map.entry("tall and can be opened", "Any Wooden Door / Iron Door"),
            Map.entry("brown and fluffy", "Rabbit's Foot"),
            Map.entry("explosive but more than usual", "Superboom TNT"),
            Map.entry("wearable and grows", "Pumpkin"),
            Map.entry("shiny and makes sparks", "Flint and Steel"),
            Map.entry("green and some dudes trade stuff for it", "Emerald"),
            Map.entry("red and soft", "Red Wool"));
    private static final Map<String, String> MUNCHER_ANSWERS = Map.of(
            "lived underground and dug tunnels", "Claw Fossil",
            "had a really fancy tail", "Clubbed Fossil",
            "was the king of his kind", "Footprint Fossil",
            "lived underwater", "Helix Fossil",
            "was kinda spiny u know", "Spine Fossil",
            "lived in herds and was quite woolly", "Tusk Fossil",
            "is pretty rough to look at", "Ugly Fossil",
            "had a really pointy beak", "Webbed Fossil");

    private static Vec3 puzzlerBlock;
    private static long lastWrongPass;
    private static String lastIsland = "";

    private NpcHelpers() {}

    static String fetchurAnswer(String riddle) {
        return FETCHUR_ANSWERS.getOrDefault(riddle, riddle);
    }

    static String muncherAnswer(String riddle) {
        return MUNCHER_ANSWERS.getOrDefault(riddle, riddle);
    }

    // skyblocker Puzzler.onMatch: block the arrows lead to, from the one skyblocker starts at
    static Vec3 puzzlerTarget(String arrows) {
        int x = 181, z = 135;
        for (char c : arrows.toCharArray()) {
            if (c == '▲') z++;
            else if (c == '▼') z--;
            else if (c == '◀') x++;
            else if (c == '▶') x--;
        }
        return new Vec3(x, 195, z);
    }

    private static ChatBus.Result solvers(ChatBus.Line line) {
        String plain = line.plain();
        Matcher m;
        if (MiningPack.on(SOLVERS, "mining_npc_solvers.fetchur") && (m = FETCHUR.matcher(plain)).matches())
            return new ChatBus.Replace(Component.literal("§e[NPC] Fetchur§f: " + fetchurAnswer(m.group(1))));
        if (MiningPack.on(SOLVERS, "mining_npc_solvers.muncher") && (m = MUNCHER.matcher(plain)).matches())
            return new ChatBus.Replace(Component.literal("§e[NPC] §6Fossil Muncher§f: " + muncherAnswer(m.group(1))));
        if (MiningPack.on(SOLVERS, "mining_npc_solvers.puzzler") && (m = PUZZLER.matcher(plain)).matches()) puzzlerBlock = puzzlerTarget(m.group(1));
        return ChatBus.PASS;
    }

    private static ChatBus.Result prompts(ChatBus.Line line) {
        String plain = line.plain();
        String island = GameState.island;
        if (MiningPack.on(PROMPTS, "mining_call_prompts.mismyla") && !"MINESHAFT".equals(island) && MISMYLA.matcher(plain).matches())
            MiningPack.click("§bClick here to call Queen Mismyla!", "/call mismyla", "§eRuns /call mismyla");
        Matcher fred = FRED.matcher(plain);
        if (MiningPack.on(PROMPTS, "mining_call_prompts.redial") && fred.matches() && (LegacyText.of(line.component()).contains("§k") || line.component().getString().contains("§k")))
            MiningPack.click("§bClick here to redial " + fred.group(1) + "!", "/call " + fred.group(1), "§eRuns /call " + fred.group(1));
        if (MiningPack.on(PROMPTS, "mining_call_prompts.don_expresso") && "DWARVEN_MINES".equals(island)
                && Patterns.get("SH.mining.donexpresso.full").matcher(plain).matches() && MiningPack.inInventory("MITHRIL_GOURMAND"))
            MiningPack.click("§6Don Expresso §eis full! Click to teleport to him.", "/tptodonexpresso", "§eRuns /tptodonexpresso");
        if (MiningPack.on(PROMPTS, "mining_call_prompts.hollows_pass")) hollowsPass(plain, island);
        return ChatBus.PASS;
    }

    // from skyhanni JoinCrystalHollows.onChat
    private static void hollowsPass(String plain, String island) {
        if (plain.equals("You do not have an active Crystal Hollows pass!")) {
            lastWrongPass = System.currentTimeMillis();
            if (!"DWARVEN_MINES".equals(island)) MiningPack.click("§eClick here to warp to the Dwarven Mines!", "/warp mines", "§eRuns /warp mines");
            else MiningPack.chat("§eBuy a §2Crystal Hollows Pass §efrom §5Gwendolyn");
        }
        if (plain.equals("[NPC] Gwendolyn: Great! Now hop on into the Minecart and I'll get you on your way!") && passWanted())
            MiningPack.click("§eClick here to warp to the Crystal Hollows!", "/warp ch", "§eRuns /warp ch");
    }

    private static boolean passWanted() {
        return System.currentTimeMillis() - lastWrongPass < 120_000;
    }

    private static void draw(WorldDraw w) {
        if (puzzlerBlock != null && "DWARVEN_MINES".equals(GameState.island) && MiningPack.on(SOLVERS, "mining_npc_solvers.puzzler"))
            w.waypoint(puzzlerBlock, 0xFFC00000, "§cPuzzler block", false, false);
    }

    private static void drawPass(WorldDraw w) {
        if ("DWARVEN_MINES".equals(GameState.island) && passWanted() && MiningPack.on(PROMPTS, "mining_call_prompts.hollows_pass"))
            w.waypoint(PASS_SPOT, 0xFFFFFF55, "§eBuy Crystal Hollows Pass", true, true);
    }

    private static void tick(Minecraft mc) {
        if (lastIsland.equals(GameState.island)) return;
        lastIsland = GameState.island;
        if ("DWARVEN_MINES".equals(lastIsland) && passWanted() && MiningPack.on(PROMPTS, "mining_call_prompts.hollows_pass")) MiningPack.chat("§eBuy a §2Crystal Hollows Pass §efrom §5Gwendolyn§e!");
        if ("CRYSTAL_HOLLOWS".equals(lastIsland)) lastWrongPass = 0;
    }

    public static void register() {
        Feature f = Features.add(new Feature(SOLVERS, "Mining", "NPC riddles", "Says what Fetchur and the Fossil Muncher want instead of their riddle, and marks the block the Puzzler's arrows lead to.", "Skyblocker: Solve Fetchur, Solve Puzzler Puzzle, Solve Fossil Muncher")
                .when(MiningPack::mining).world(NpcHelpers::draw).left(() -> puzzlerBlock = null)
                .option(Option.bool("mining_npc_solvers.fetchur", "Fetchur", "", true))
                .option(Option.bool("mining_npc_solvers.puzzler", "Puzzler", "", true))
                .option(Option.bool("mining_npc_solvers.muncher", "Fossil Muncher", "", true)));
        ChatBus.register(ChatBus.FILTER + 40, "mining_npc_solvers", f, NpcHelpers::solvers);
        Feature p = Features.add(new Feature(PROMPTS, "Mining", "Next step in chat", "A line to click after a commission (call Queen Mismyla), on a bad signal (redial), when Don Expresso is full (teleport to him) and when you have no Crystal Hollows Pass (warp, and where to buy it). Only your click does anything.", "Skyblocker: Call Mismyla, Redial on Bad Signal; SkyHanni: Don Expresso Feeding Reminder, CH Join")
                .when(MiningPack::skyblock).world(NpcHelpers::drawPass).tick(NpcHelpers::tick)
                .option(Option.bool("mining_call_prompts.mismyla", "Call Queen Mismyla", "Needs her as an Abiphone contact.", true))
                .option(Option.bool("mining_call_prompts.redial", "Redial on a bad signal", "", true))
                .option(Option.bool("mining_call_prompts.don_expresso", "Don Expresso is full", "While you carry Mithril Gourmand.", true))
                .option(Option.bool("mining_call_prompts.hollows_pass", "Crystal Hollows Pass", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 21, "mining_call_prompts", p, NpcHelpers::prompts);
    }
}
