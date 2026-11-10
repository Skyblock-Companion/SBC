// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.network.chat.Component;

import java.util.function.Predicate;
import java.util.regex.Matcher;

// from skyhanni PowderMiningChatFilter + CrystalNucleusChatFilter: cuts chest hunting and nucleus chat down
// to what you want
// both start off bc they hide lines. a rule returns null = leave it, "" = hide it, or the line to show instead
public final class MiningChat {
    static final String POWDER = "chat_powder_mining", NUCLEUS = "chat_crystal_nucleus";
    private static final String[] EGGS = {"Show all", "Hide all", "Green and up", "Yellow and up", "Red and up", "Blue only"};
    private static final String[] GEMS = {"Show all", "Hide all", "Flawed and up", "Fine and up", "Flawless only"};
    private static final String[][] SIMPLE = {
            {"ascensionrope", "ascension_rope"}, {"wishingcompass", "wishing_compass"}, {"oilbarrel", "oil_barrel"}, {"prehistoricegg", "prehistoric_egg"}, {"pickonimbus", "pickonimbus"},
            {"jungleheart", "jungle_heart"}, {"sludgejuice", "sludge_juice"}, {"yoggie", "yoggie"}, {"robotparts", "robot_parts"}, {"treasurite", "treasurite"}};

    private static boolean unclosedRewards, unclosedRun, unclosedCrystal, inComponentList;
    private static long componentListAt;
    private static int crystalCount;
    private static String lastKeeper = "";
    private MiningChat() {}

    private static boolean m(String key, String message) {
        return Patterns.get(key).matcher(message).matches();
    }

    private static int index(String[] list, String choice) {
        for (int i = 0; i < list.length; i++) if (list[i].equals(choice)) return i;
        return 0;
    }

    // powder mining

    // skyhanni PowderMiningChatFilter.block, settings passed in
    static String powder(String message, int powderThreshold, int essenceThreshold, boolean toolMessages, String eggs, java.util.function.Function<String, String> gem, Predicate<String> simple) {
        if (m("SH.filter.powdermining.warning.chestuncover", message) || m("SH.filter.powdermining.warning.successpick", message) || m("SH.filter.powdermining.warning.alreadylooted", message)) return "";
        if (m("SH.filter.powdermining.warning.breakingpower", message) && toolMessages) return "";
        if (m("SH.filter.powdermining.powder.chestwrapper", message)) {
            unclosedRewards = !unclosedRewards;
            return "";
        }
        if (!unclosedRewards) return null;
        if (message.isEmpty() || m("SH.filter.powdermining.powder.picked", message) || m("SH.filter.powdermining.lootchest.collected", message) || m("SH.filter.powdermining.reward.header", message)) return "";
        if (!message.startsWith("    ")) return null;
        String s = message.substring(4);
        Matcher p = Patterns.get("SH.filter.powdermining.reward.powder").matcher(s);
        if (p.matches()) return below(p.group("amount"), powderThreshold, 60000);
        p = Patterns.get("SH.filter.powdermining.reward.essence").matcher(s);
        if (p.matches()) return below(p.group("amount"), essenceThreshold, 20);
        for (String[] r : SIMPLE) if (m("SH.filter.powdermining.reward." + r[0], s)) return simple.test(r[1]) ? "" : null;
        p = Patterns.get("SH.filter.powdermining.reward.goblineggs").matcher(s);
        if (p.matches()) return eggs(p.group("color"), eggs);
        p = Patterns.get("SH.filter.powdermining.reward.gemstone").matcher(s);
        if (p.matches()) return gems(p.group("tier").toLowerCase(java.util.Locale.ROOT), gem.apply(p.group("gem").toLowerCase(java.util.Locale.ROOT)));
        return null;
    }

    private static String below(String amount, int threshold, int hideAll) {
        if (threshold >= hideAll) return "";
        if (threshold <= 0) return null;
        long n = amount == null || amount.isEmpty() ? 1 : MiningEnv.parseLong(amount);
        return n < threshold ? "" : null;
    }

    static String eggs(String color, String setting) {
        int s = index(EGGS, setting);
        if (s == 0) return null;
        if (s == 1 || color == null) return "";
        return switch (color.toLowerCase(java.util.Locale.ROOT)) {
            case "green" -> s > 2 ? "" : null;
            case "yellow" -> s > 3 ? "" : null;
            case "red" -> s > 4 ? "" : null;
            default -> null;
        };
    }

    static String gems(String tier, String setting) {
        int s = index(GEMS, setting);
        if (s == 0) return null;
        if (s == 1) return "";
        return switch (tier) {
            case "rough" -> "";
            case "flawed" -> s > 2 ? "" : null;
            case "fine" -> s > 3 ? "" : null;
            default -> null;
        };
    }

    private static ChatBus.Result onPowder(ChatBus.Line line) {
        if (!MiningApi.inCrystalHollows()) return ChatBus.PASS;
        String res = powder(LegacyText.of(line.component()), MiningPack.number(POWDER, "chat_powder_mining.powder", 1000), MiningPack.number(POWDER, "chat_powder_mining.essence", 5), MiningPack.on(POWDER, "chat_powder_mining.tool_messages"),
                MiningPack.choice(POWDER, "chat_powder_mining.eggs", "Yellow and up"), g -> MiningPack.choice(POWDER, "chat_powder_mining." + g, "Fine and up"), k -> MiningPack.on(POWDER, "chat_powder_mining.hide_" + k));
        return res == null ? ChatBus.PASS : ChatBus.CANCEL;
    }

    // crystal nucleus

    // from skyhanni CrystalNucleusChatFilter.block
    static String nucleus(String message, boolean inNucleus, Predicate<String> types) {
        if (types.test("crystal_collected")) {
            if (m("SH.filter.crystalnucleus.crystal.collected.wrapper", message)) {
                unclosedCrystal = !unclosedCrystal;
                return "";
            }
            if (unclosedCrystal) {
                Matcher c = Patterns.get("SH.filter.crystalnucleus.crystal.collected.count").matcher(message);
                if (c.matches()) crystalCount = Integer.parseInt(c.group("count"));
                c = Patterns.get("SH.filter.crystalnucleus.crystal.collected.id").matcher(message);
                if (c.matches()) return "§5§l✦ " + c.group("crystal") + " §5found§d! §7(§a" + crystalCount + "§7/§a5§7)";
                return "";
            }
        }
        if (inNucleus && types.test("crystal_placed")) {
            if (message.equals("  §r§dKeep exploring the §r§5Crystal Hollows §r§dto find the rest!")) return "";
            Matcher c = Patterns.get("SH.filter.crystalnucleus.crystal.placed").matcher(message);
            if (c.matches()) return "§5§l✦ " + c.group("crystal") + " §5placed§d!";
        }
        if (inNucleus && types.test("run_completed")) {
            if (m("SH.filter.crystalnucleus.run.completed", message)) {
                unclosedRun = !unclosedRun;
                return "";
            }
            if (message.equals("§7Pick it up near the §r§5Nucleus Vault§r§7!")) return "§5Crystal Nucleus Run complete!";
            if (unclosedRun) return "";
        }
        if (types.test("non_tool_scavenge")) {
            Matcher c = Patterns.get("SH.filter.crystalnucleus.divan.scavenge").matcher(message);
            if (c.matches() && !c.group("loot").startsWith("§cScavenged")) return "";
            if (m("SH.filter.crystalnucleus.divan.scavenge.secondary", message)) return "";
        }
        if (types.test("goblin_guards") && (m("SH.filter.crystalnucleus.goblin.guard.exit", message) || message.startsWith("§c[GUARD]"))) return "";
        if (!message.startsWith("§e[NPC]")) return null;
        if (types.test("prof_robot")) {
            String r = robot(message);
            if (r != null) return r;
        }
        if (types.test("king_yolkar") && message.startsWith("§e[NPC] §6King Yolkar")) {
            if (message.contains("*rumble* *rumble*")) return "§e[NPC] §6King Yolkar§f: ...";
            if (message.contains("Bring me back a §9Goblin Egg")) return "§e[NPC] §6King Yolkar§f: §rBring me a §9Goblin Egg §rof any type.";
            if (message.contains("This egg will help me stomach my pain.")) return "§e[NPC] §6King Yolkar§f: §2King's Scent§r applied.";
            return "";
        }
        if (types.test("divan_keepers") && message.startsWith("§e[NPC] §6Keeper of ")) {
            Matcher k = Patterns.get("SH.filter.crystalnucleus.npc.keeper").matcher(message);
            if (k.matches()) lastKeeper = k.group("keepertype");
            if (message.contains("You found all of the items!")) return "§e[NPC] §6Keeper of §6" + lastKeeper + "§f: §rAll tools submitted.";
            return "";
        }
        return null;
    }

    private static String robot(String message) {
        boolean inList = inComponentList && System.currentTimeMillis() - componentListAt < 2000;
        if (inList && m("SH.filter.crystalnucleus.component.list", message)) return "";
        if (!message.startsWith("§e[NPC] Professor Robot")) return null;
        if (Patterns.get("SH.filter.crystalnucleus.component.list.preamble").matcher(message).find()) {
            inComponentList = true;
            componentListAt = System.currentTimeMillis();
            return "";
        }
        Matcher c = Patterns.get("SH.filter.crystalnucleus.precursor.submitted").matcher(message);
        if (c.find()) {
            if (message.contains("brought me all") || message.contains("This will work just fine.")) return "§e[NPC] Professor Robot§f: §rAll components submitted.";
            return "§e[NPC] Professor Robot§f: " + c.group("component") + " submitted. " + c.group("remaining") + " components left.";
        }
        return "";
    }

    private static ChatBus.Result onNucleus(ChatBus.Line line) {
        if (!MiningApi.inCrystalHollows()) return ChatBus.PASS;
        String res = nucleus(LegacyText.of(line.component()), "Crystal Nucleus".equals(GameState.area), k -> MiningPack.on(NUCLEUS, "chat_crystal_nucleus." + k));
        if (res == null) return ChatBus.PASS;
        return res.isEmpty() ? ChatBus.CANCEL : new ChatBus.Replace(Component.literal(res));
    }

    private static void leavePowder() {
        unclosedRewards = false;
    }

    private static void leaveNucleus() {
        unclosedRun = unclosedCrystal = inComponentList = false;
        crystalCount = 0;
        lastKeeper = "";
    }

    public static void register() {
        Feature p = new Feature(POWDER, "Chat", "Powder mining chat", "Hides the lines of treasure chests you do not care about: small powder and essence, common items, low gemstones and eggs. It hides lines, so it starts off; compact chest rewards stay as they are for the lines that are left.", "SkyHanni: Powder Mining filter")
                .when(MiningPack::hollows).defaultOff().left(MiningChat::leavePowder)
                .option(Option.integer("chat_powder_mining.powder", "Show powder from", "Less is hidden; 60000 hides all, 0 shows all.", 1000, 0, 60000))
                .option(Option.integer("chat_powder_mining.essence", "Show essence from", "Less is hidden; 20 hides all, 0 shows all.", 5, 0, 20))
                .option(Option.bool("chat_powder_mining.tool_messages", "Hide 'stronger tool' lines", "", true))
                .option(Option.choice("chat_powder_mining.eggs", "Goblin Eggs", "", "Yellow and up", EGGS))
                .option(Option.bool("chat_powder_mining.hide_ascension_rope", "Hide Ascension Rope", "", true))
                .option(Option.bool("chat_powder_mining.hide_wishing_compass", "Hide Wishing Compass", "", true))
                .option(Option.bool("chat_powder_mining.hide_oil_barrel", "Hide Oil Barrel", "", true))
                .option(Option.bool("chat_powder_mining.hide_prehistoric_egg", "Hide Prehistoric Egg", "", false))
                .option(Option.bool("chat_powder_mining.hide_pickonimbus", "Hide Pickonimbus 2000", "", false))
                .option(Option.bool("chat_powder_mining.hide_jungle_heart", "Hide Jungle Heart", "", true))
                .option(Option.bool("chat_powder_mining.hide_sludge_juice", "Hide Sludge Juice", "", true))
                .option(Option.bool("chat_powder_mining.hide_yoggie", "Hide Yoggie", "", true))
                .option(Option.bool("chat_powder_mining.hide_robot_parts", "Hide Robot Parts", "", false))
                .option(Option.bool("chat_powder_mining.hide_treasurite", "Hide Treasurite", "", true));
        p.option(Option.choice("chat_powder_mining.ruby", "Ruby", "", "Fine and up", GEMS));
        p.option(Option.choice("chat_powder_mining.sapphire", "Sapphire", "", "Fine and up", GEMS));
        p.option(Option.choice("chat_powder_mining.amber", "Amber", "", "Fine and up", GEMS));
        p.option(Option.choice("chat_powder_mining.amethyst", "Amethyst", "", "Fine and up", GEMS));
        p.option(Option.choice("chat_powder_mining.jade", "Jade", "", "Fine and up", GEMS));
        p.option(Option.choice("chat_powder_mining.topaz", "Topaz", "", "Fine and up", GEMS));
        p.option(Option.choice("chat_powder_mining.jasper", "Jasper", "", "Fine and up", GEMS));
        Features.add(p);
        ChatBus.register(ChatBus.FILTER + 42, POWDER, p, MiningChat::onPowder);
        Feature n = Features.add(new Feature(NUCLEUS, "Chat", "Crystal Nucleus chat", "Shortens what a Crystal Nucleus run says: crystals found and placed, the end of a run, Professor Robot, King Yolkar, the Keepers and the goblin guards. It changes what you read, so it starts off.", "SkyHanni: Crystal Nucleus filter")
                .when(MiningPack::hollows).defaultOff().left(MiningChat::leaveNucleus)
                .option(Option.bool("chat_crystal_nucleus.crystal_collected", "Crystal found", "", true))
                .option(Option.bool("chat_crystal_nucleus.crystal_placed", "Crystal placed", "", true))
                .option(Option.bool("chat_crystal_nucleus.run_completed", "Run completed", "", true))
                .option(Option.bool("chat_crystal_nucleus.divan_keepers", "Keepers of Divan", "", true))
                .option(Option.bool("chat_crystal_nucleus.prof_robot", "Professor Robot", "", true))
                .option(Option.bool("chat_crystal_nucleus.king_yolkar", "King Yolkar", "", true))
                .option(Option.bool("chat_crystal_nucleus.goblin_guards", "Goblin guards", "", true))
                .option(Option.bool("chat_crystal_nucleus.non_tool_scavenge", "Other Metal Detector finds", "", true)));
        ChatBus.register(ChatBus.FILTER + 43, NUCLEUS, n, MiningChat::onNucleus);
    }
}
