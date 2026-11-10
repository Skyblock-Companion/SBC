// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.muzy.sbc.SbcMod;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// every /sbc sub command is also one word: /sbc nav = /sbcnav, /sbc config = /sbcconfig etc. built from the
// real tree so nothing gets listed twice. /sbc help prints them all
public final class Commands {
    private static final Map<String, String> HELP = new TreeMap<>();
    private Commands() {}

    // help line for a sub command, /sbc help shows it
    public static void describe(String sub, String text) {
        HELP.put(sub, text);
    }

    static {
        describe("nav", "Walk a line to any NPC, warp or place: /sbc nav <name>");
        describe("config", "Open the settings");
        describe("settings", "Open the settings");
        describe("hudedit", "Open the HUD editor (also key H)");
        describe("storage", "Storage overview: every Ender Chest page and backpack (also key B)");
        describe("wp", "Waypoints: add, at <x> <y> <z> <name>, remove, go, list, share");
        describe("route", "Dungeon secret routes");
        describe("rule", "Chat rules: add, remove, list");
        describe("rules", "Open the list of your chat rules");
        describe("colors", "List the colour and formatting codes");
        describe("profile", "Look up a player's profile");
        describe("dump", "Write the raw scoreboard, tab, action bar, chat and menu to a file");
        describe("backup", "Back up the config");
        describe("perf", "Toggle the performance overlay");
        describe("wiki", "Open the wiki page of the item in your hand");
        describe("credits", "Who this mod learned from and what it reuses");
        describe("setup", "The first-run setup page: what you play and how it looks");
        describe("tutorial", "The keys worth knowing, one at a time");
        describe("whatsnew", "What came with this version, and its new helpers to switch on");
        describe("discord", "The link to the mod's Discord, to copy");
        describe("reset", "Every feature and the HUD back to how they start");
        describe("pv", "Open the profile page of the menu");
        describe("pets", "Open the pets screen");
        describe("stop", "Stop navigating");
        describe("found", "Forget the secrets and collectibles you found: /sbc found reset");
        describe("copy", "Copy the last 10 chat lines");
        describe("learn", "Teach the routine how a task's chat line reads: /sbc learn <task>");
        describe("dye", "Colour the armor you wear, only for you: /sbc dye <piece> <colour>");
        describe("skills", "Skill numbers and goals: levelwithxp, xpforlevel, goal");
        describe("slayers", "Your best kill time of a slayer: /sbc slayers <slayer>");
        describe("museum", "Read your Museum again: /sbc museum resync");
        describe("coopmembers", "Former co-op members to hide: add, remove");
        describe("dungeonsound", "Hear a dungeon sound: /sbc dungeonsound chime, lowhp");
        describe("fixminions", "Take minions that cannot exist off your list");
        describe("bingotoggle", "Show or hide the Bingo Card");
        describe("refreshprices", "Fetch the item prices again");
        describe("sharemineshaft", "Tell your party which mineshaft type you are in");
        describe("resetcaketracker", "Forget which New Year Cakes you have");
        describe("resethuntingbox", "Forget the saved Hunting Box");
        describe("resetprofit", "Start the profit trackers over for this session");
        describe("resetskillsession", "Start the skill timer over");
        describe("resetterminals", "Clear the terminal marks of the Goldor phase");
        describe("resetploticons", "Every plot icon back to what the menu shows");
        describe("resetcontestdata", "Forget the contests read from the calendar");
        describe("waterreset", "Start the water board over");
        describe("import", "Copy the custom scoreboard setup from another mod's settings");
        describe("help", "This list");
    }

    // sub commands by what theyre for, anything missing falls to the rules in group()
    private static final Map<String, String> GROUP = new java.util.HashMap<>();

    private static void group(String name, String subs) {
        for (String s : subs.split(" ")) GROUP.put(s, name);
    }

    static {
        group("Open", "config settings hudedit storage lastopened wiki wikithis pv profile pets museum waypoints rules shortcuts todos colors credits setup tutorial whatsnew discord help");
        group("Set up", "rule downloadtodo remind speed dye custom nightvision mouselock sensreduce protectitem preventsell supercraftpreset importshards import coopmembers skills cropstartlocation sethoelevel bingotoggle disableall");
        group("Find your way", "nav stop route wp navall navclipboard ordered crystalwaypoints relics relicsfoundall relicsreset soulsfoundall soulsreset found rescueboard tpinfested enigma greenhouse lanedetection");
        group("Work out", "calc calccrop calccroptime cropsin croptime blockstrength ping tps gardenuptime hoppitystats slayers addusedgifts carry copyunderbid copy refreshprices bingoupdate bingoreload fixminions dungeonsound learn");
        group("Share", "sendcoords sharecorpse shareegg sharemineshaft sharerare sharevanquisher");
        group("Start over", "reset waterreset cleartalbotcircles");
        group("For a bug report", "dump perf backup");
    }

    // heading a sub command goes under
    public static String group(String sub) {
        String g = GROUP.get(sub);
        if (g != null) return g;
        return sub.startsWith("reset") ? "Start over" : "More";
    }

    // /sbc help output: each sub command once, grouped, so like 100 of them fit on one chat screen
    public static List<String> helpLines(java.util.Collection<String> subs) {
        Map<String, List<String>> by = new java.util.LinkedHashMap<>();
        for (String g : List.of("Open", "Set up", "Find your way", "Work out", "Share", "Start over", "For a bug report", "More")) by.put(g, new ArrayList<>());
        for (String s : subs.stream().sorted().toList()) by.get(group(s)).add(s);
        List<String> out = new ArrayList<>();
        out.add("§6SkyBlock Companion §7- each also works as one word, like /sbcnav. §e/sbc help <word> §7says what they do.");
        by.forEach((g, names) -> {
            if (!names.isEmpty()) out.add("§6" + g + ": §e" + String.join(" §8·§e ", names));
        });
        return out;
    }

    // every help line, /sbc help + its search read this
    public static Map<String, String> help() {
        return HELP;
    }

    // /sbc<x> for every /sbc <x> + /sbc help. call after the main tree is registered
    public static void registerAliases(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        CommandNode<FabricClientCommandSource> root = dispatcher.getRoot().getChild("sbc");
        if (root == null) return;
        int n = 0;
        for (CommandNode<FabricClientCommandSource> child : new ArrayList<>(root.getChildren())) {
            if (!(child instanceof LiteralCommandNode<FabricClientCommandSource> lit)) continue;
            String alias = "sbc" + lit.getLiteral();
            if (dispatcher.getRoot().getChild(alias) != null) continue;
            var node = ClientCommands.literal(alias).redirect(lit);
            if (lit.getCommand() != null) node.executes(lit.getCommand());
            dispatcher.register(node);
            n++;
        }
        if (root.getChild("help") == null) {
            dispatcher.register(ClientCommands.literal("sbc").then(ClientCommands.literal("help").executes(c -> {
                List<String> subs = new ArrayList<>();
                for (CommandNode<FabricClientCommandSource> ch : dispatcher.getRoot().getChild("sbc").getChildren()) subs.add(ch.getName());
                for (String line : helpLines(subs)) c.getSource().sendFeedback(Component.literal(line));
                return 1;
            })));
            dispatcher.register(ClientCommands.literal("sbchelp").redirect(dispatcher.getRoot().getChild("sbc").getChild("help"))
                    .executes(dispatcher.getRoot().getChild("sbc").getChild("help").getCommand()));
        }
        SbcMod.LOG.info("Registered {} /sbc<x> command aliases", n);
    }
}
