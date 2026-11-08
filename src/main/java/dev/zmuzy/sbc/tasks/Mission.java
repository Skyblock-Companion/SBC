// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.tasks;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.nav.Navigator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// what the current task needs + how to do it: steps, items to bring (checked against your inventory),
// shortcuts (bazaar search, take from sacks, closest warp)
public final class Mission {
    // item a task needs: name like in the inventory, sb id, amount, does the bazaar sell it
    public record Need(String name, String id, int amount, boolean bazaar, String source) {}

    public record Shortcut(String label, String command) {}

    public static final Map<String, List<Need>> NEEDS = Map.of(
            "pomtair", List.of(new Need("Enchanted Bone Meal", "ENCHANTED_BONE_MEAL", 1, true, "")),
            "sirih", List.of(new Need("Sulphur", "SULPHUR_ORE", 1, true, "")),
            "suus", List.of(new Need("Turd's Toy", "", 1, false, "10% drop from Magma Cube Riders (Crimson Isle, Magma Chamber)")));

    private static final Map<String, List<String>> STEPS = Map.ofEntries(
            Map.entry("commissions", List.of("Go to the Dwarven Mines (/warp forge).", "Your 4 commissions are in the tab list under Commissions.",
                    "Follow the line to each commission's area and complete it.", "Claim finished ones at an Emissary - the line takes you there.")),
            Map.entry("fetchur", List.of("Fetchur stands in the Dwarven Mines near the Forge.", "He wants a different item every day - talk to him to see it.",
                    "Bring it back for Mithril Powder.")),
            Map.entry("puzzler", List.of("Talk to the Puzzler in the Dwarven Mines.", "Follow the arrows on the floor and stand on the right block.")),
            Map.entry("nucleus", List.of("Collect the 5 crystals in the Crystal Hollows (Jade, Amber, Topaz, Sapphire, Amethyst).",
                    "Place them at the Crystal Nucleus in the middle for the loot bundle.")),
            Map.entry("forge_collect", List.of("Open the Forge (/warp forge).", "Claim everything finished, then start new items so no slot is idle.")),
            Map.entry("crimson_quests", List.of("Your 5 faction quests are in the tab list under Faction Quests.",
                    "The line goes to the closest open quest: minibosses, Dojo, Kuudra, lava fishing spots.",
                    "Fetch quests: get the item, then hand it in at your Town Board (Dragontail / Scarleton).",
                    "The Rescue Mission is always saved for last.")),
            Map.entry("dojo", List.of("Go to the Dojo on the Crimson Isle.", "Do any test you have not maxed for belt points and reputation.")),
            Map.entry("pomtair", List.of("Have 1 Enchanted Bone Meal in your inventory (not sacks).", "Walk to Pomtair on the Crimson Isle and right-click him.",
                    "+50 Barbarian reputation, once a day. Detected from his thank-you message.")),
            Map.entry("sirih", List.of("Have 1 Sulphur in your inventory.", "Sirih is a pig on the Crimson Isle (back in pig form every 24h).",
                    "Right-click the pig to give the Sulphur: +10 Barbarian reputation.")),
            Map.entry("suus", List.of("Get Turd's Toy (the cube) - 10% drop from Magma Cube Riders.", "Bring it to Suus, standing next to Turd.",
                    "+40 Barbarian reputation, every 2 days.")),
            Map.entry("matriarch", List.of("Go to the Matriarch's lair on the Crimson Isle.", "Collect today's Heavy Pearls.")),
            Map.entry("kuudra", List.of("Talk to Elle at the Kuudra dock (/warp kuudra).", "Queue your tier with a party and open the chest after.")),
            Map.entry("visitors", List.of("Go to your Garden (/warp garden).", "Visitors wait at the Barn - accept the offers worth it.")),
            Map.entry("jacob", List.of("Check the 3 contest crops in the calendar.", "Farm one of them during the contest for medals.")),
            Map.entry("experiments", List.of("Use an Experimentation Table (on your island).", "Do Superpairs, Chronomatron and Ultrasequencer.")),
            Map.entry("bits", List.of("Elizabeth in the Hub runs the Community Shop.", "Spend your Bits there (Kat Flowers, God Potions...). Ticks when Bits go down.")),
            Map.entry("daily_reward", List.of("Find the Delivery Man in any Hypixel lobby and claim the daily reward.")),
            Map.entry("slayer", List.of("Maddox the Slayer is in the Hub tavern (or use your Batphone).", "Start your best slayer quest and kill the boss.")),
            Map.entry("dungeons", List.of("Go to the Dungeon Hub (/warp dungeon_hub).", "Talk to Mort and run your best floor.")),
            Map.entry("bazaar_orders", List.of("Open the Bazaar (/bz) and claim filled orders.", "Claim sold auctions at the Auction House.")));

    // tasks you skipped this session (the routine moves past them)
    public static final Set<String> skipped = new HashSet<>();

    private Mission() {}

    public static List<String> steps(TaskDef t) {
        List<String> s = STEPS.get(t.id());
        return s != null ? s : List.of(t.hint());
    }

    public static List<Need> needs(TaskDef t) {
        return NEEDS.getOrDefault(t.id(), List.of());
    }

    // how many of an item (by name) are in your inventory
    public static int inInventory(String name) {
        var p = Minecraft.getInstance().player;
        if (p == null) return 0;
        var inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && GameState.strip(st.getHoverName().getString()).trim().equalsIgnoreCase(name)) n += st.getCount();
        }
        return n;
    }

    // first needed item you dont carry, null = got everything
    public static Need missing(TaskDef t) {
        for (Need n : needs(t)) if (inInventory(n.name()) < n.amount()) return n;
        return null;
    }

    // task the mission menu shows: the guided step, else the first open daily
    public static Routine.Entry current() {
        for (Routine.Entry e : RoutinePreset.tasks()) {
            if (e.complete() || skipped.contains(e.def().id())) continue;
            return e;
        }
        return null;
    }

    // warp closest to the task ("/warp crimson"), or empty
    public static String warp(TaskDef t) {
        Navigator.Target target = Guide.locationOf(t);
        if (target == null) return "";
        Graph g = Repo.graph(target.island());
        if (g == null || g.isEmpty()) return "";
        Graph.Node w = g.warpNear(target.pos());
        return w == null || !w.name().startsWith("/warp") ? "" : w.name();
    }

    public static List<Shortcut> shortcuts(TaskDef t) {
        List<Shortcut> out = new ArrayList<>();
        String w = warp(t);
        if (!w.isEmpty() && !t.island().equals(GameState.island)) out.add(new Shortcut("Warp (" + w.substring(1) + ")", w.substring(1)));
        for (Need n : needs(t)) {
            if (inInventory(n.name()) >= n.amount()) continue;
            if (!n.id().isEmpty()) out.add(new Shortcut("Take from sacks", "gfs " + n.id() + " " + n.amount()));
            if (n.bazaar()) out.add(GameState.hasCookie() ? new Shortcut("Buy on Bazaar", "bz " + n.name())
                    : new Shortcut("Go to Bazaar (no cookie)", "nav:HUB:Bazaar"));
        }
        switch (t.id()) {
            case "forge_collect", "commissions", "fetchur", "puzzler" -> out.add(new Shortcut("Warp Forge", "warp forge"));
            case "bazaar_orders" -> out.add(new Shortcut("Open Bazaar", "bz"));
            case "visitors", "composter", "pests", "jacob" -> out.add(new Shortcut("Warp Garden", "warp garden"));
            case "experiments" -> out.add(new Shortcut("Warp island", "warp island"));
            case "dungeons" -> out.add(new Shortcut("Warp Dungeon Hub", "warp dungeon_hub"));
            case "kuudra" -> out.add(new Shortcut("Warp Kuudra", "warp kuudra"));
            default -> {
            }
        }
        // drop dupes (same command twice)
        List<Shortcut> dedup = new ArrayList<>();
        for (Shortcut s : out) if (dedup.stream().noneMatch(d -> d.command().equals(s.command()))) dedup.add(s);
        return dedup;
    }

    // runs a hypixel command for a shortcut you clicked
    public static void run(String command) {
        var mc = Minecraft.getInstance();
        if (command.startsWith("nav:")) {
            String[] p = command.split(":", 3);
            Navigator.navigateTo(p[1], p[2]);
            if (!p[1].equals(GameState.island) && mc.getConnection() != null) mc.getConnection().sendCommand("warp " + p[1].toLowerCase());
            return;
        }
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
    }
}
