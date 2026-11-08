// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.nav;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// port of skyhanni misc/FastFairySoulsPathfinder.kt: the islands fairy souls (graph nodes tagged fairy_soul)
// minus found ones (per profile), routed from where you stand
public final class FairySouls {
    public static final String ID = "fairy_souls";
    static final Patterns.P DUPLICATE = Patterns.p("SH.misc.fairy-souls.chat.duplicate.colorless");
    static final Patterns.P NEW = Patterns.p("SH.misc.fairy-souls.chat.new.colorless");
    static final Patterns.P LORE_SOUL = Patterns.p("SH.misc.fairy-souls.new.colorless");

    // from skyhanni FastFairySoulsPathfinder.Data
    static final class Data {
        int found;
        final int total;
        final List<Vec3> route;
        final Set<Vec3> allSouls;
        Vec3 foundButNotClickedSoul;
        boolean disabled;
        String debugState;

        Data(int found, int total, List<Vec3> route, Set<Vec3> allSouls) {
            this.found = found;
            this.total = total;
            this.route = route;
            this.allSouls = allSouls;
            disabled = total > 0 && found == total;
        }
    }

    static Data data;
    static String island = "";
    public static Supplier<Vec3> playerPos = () -> Vec3.ZERO;
    public static Supplier<Boolean> inAir = () -> false;
    public static Supplier<Store.FairyProfile> storage = () -> new Store.FairyProfile();
    public static Supplier<Graph> graph = () -> Repo.graph(island);
    // skyhanni IslandGraphs.pathFind(location, label), swappable in tests
    public static java.util.function.BiConsumer<Vec3, String> pathFind = (v, l) -> {};
    public static Runnable stopNavigation = () -> {};
    public static java.util.function.Consumer<String> chat = m -> {};

    private FairySouls() {}

    static String key(Vec3 v) {
        return (int) Math.round(v.x) + ":" + (int) Math.round(v.y) + ":" + (int) Math.round(v.z);
    }

    static Set<String> foundOnIsland(String island) {
        return storage.get().found.computeIfAbsent(island, k -> new HashSet<>());
    }

    public static Set<Vec3> foundSoulsOnCurrentIsland() {
        Set<Vec3> out = new HashSet<>();
        for (String s : foundOnIsland(island)) {
            String[] p = s.split(":");
            out.add(new Vec3(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        }
        return out;
    }

    public static int amountFoundOnCurrentIsland() {
        return storage.get().totalFound.getOrDefault(island, 0);
    }

    public static void onWorldChange(String newIsland) {
        island = newIsland == null ? "" : newIsland;
        data = null;
    }

    // from skyhanni getTargetNodes: nodes tagged fairy_soul
    static List<Vec3> allSouls(Graph g) {
        List<Vec3> out = new ArrayList<>();
        if (g == null) return out;
        for (Graph.Node n : g.nodes) if (n.tags().contains("fairy_soul")) out.add(n.pos());
        return out;
    }

    // skyhanni NavigationUtils.getRoute first stage (greedy tsp from the closest node). the 2-opt pass isnt ported
    static List<Vec3> greedyRoute(List<Vec3> targets, Vec3 start) {
        List<Vec3> left = new ArrayList<>(targets);
        List<Vec3> route = new ArrayList<>();
        Vec3 cur = start;
        while (!left.isEmpty()) {
            Vec3 best = null;
            for (Vec3 v : left) if (best == null || v.distanceToSqr(cur) < best.distanceToSqr(cur)) best = v;
            route.add(best);
            left.remove(best);
            cur = best;
        }
        return route;
    }

    // from skyhanni reload: builds the data for this island
    public static void reload() {
        Graph g = graph.get();
        if (g == null || g.isEmpty()) {
            data = new Data(0, 0, new ArrayList<>(), Set.of());
            data.disabled = true;
            data.debugState = "island graph is empty";
            return;
        }
        Set<Vec3> foundSouls = foundSoulsOnCurrentIsland();
        List<Vec3> all = allSouls(g);
        List<Vec3> missing = new ArrayList<>();
        for (Vec3 v : all) if (!foundSouls.contains(v)) missing.add(v);
        if (missing.isEmpty()) {
            if (foundSouls.isEmpty()) {
                data = new Data(0, 0, new ArrayList<>(), Set.of());
                data.disabled = true;
                data.debugState = "There are no fairy souls in the graph network of " + island;
            } else {
                data = new Data(foundSouls.size(), foundSouls.size(), new ArrayList<>(), foundSouls);
                data.debugState = "found all souls on " + island;
            }
            return;
        }
        Data d = new Data(0, all.size(), new ArrayList<>(), Set.of());
        data = d;
        if (checkHaveAll(d)) return;
        List<Vec3> route = greedyRoute(missing, playerPos.get());
        data = new Data(foundSouls.size(), all.size(), route, new HashSet<>(all));
        pathToNext();
    }

    static boolean checkHaveAll(Data d) {
        boolean haveAll = d.total > 0 && amountFoundOnCurrentIsland() == d.total;
        if (haveAll) allFound(d, "already found all souls on " + island + " according to hypixel data");
        return haveAll;
    }

    static void allFound(Data d, String state) {
        d.disabled = true;
        Set<String> found = foundOnIsland(island);
        for (Vec3 v : d.route) found.add(key(v));
        d.found = found.size();
        storage.get().totalFound.put(island, d.found);
        d.debugState = state;
        Store.markDirty();
    }

    static void pathToNext() {
        Data d = data;
        if (d == null || d.disabled) return;
        if (d.route.isEmpty()) {
            chat.accept("§eFound all §5" + d.found + " Fairy Souls §ein " + GameState.pretty(island) + "!");
            allFound(d, "found last soul of " + island);
            stopNavigation.run();
        } else pathTo(d.route.get(0));
    }

    private static void pathTo(Vec3 loc) {
        Data d = data;
        if (d == null) return;
        double percentage = d.total == 0 ? 0 : (d.found * 100.0 / d.total);
        pathFind.accept(loc, "§b" + d.found + "/" + d.total + " §5Fairy Souls §8(§b" + String.format(Locale.ROOT, "%.1f", percentage) + "%§8)");
    }

    // from skyhanni onSystemMessage: found / duplicate line
    public static boolean onChat(String clean) {
        String t = clean.trim();
        if (!DUPLICATE.matcher(t).matches() && !NEW.matcher(t).matches()) return false;
        Data d = data;
        if (d != null) foundNearby(d);
        return true;
    }

    static void foundNearby(Data d) {
        if (d.disabled) return;
        d.foundButNotClickedSoul = null;
        Vec3 nearest = getNearestSoul(d);
        if (nearest == null) return;
        if (d.route.remove(nearest)) d.found++;
        foundOnIsland(island).add(key(nearest));
        Store.markDirty();
        pathToNext();
    }

    private static Vec3 getNearestSoul(Data d) {
        Vec3 player = playerPos.get();
        Vec3 nearest = null;
        for (Vec3 v : d.allSouls) if (nearest == null || v.distanceToSqr(player) < nearest.distanceToSqr(player)) nearest = v;
        if (nearest == null) return null;
        if (nearest.distanceTo(player) < 10) return nearest;
        if (inAir.get()) {
            Vec3 above = player.add(0, 10, 0);
            Vec3 aboveNearest = null;
            for (Vec3 v : d.allSouls) if (aboveNearest == null || v.distanceToSqr(above) < aboveNearest.distanceToSqr(above)) aboveNearest = v;
            if (aboveNearest != null && aboveNearest.distanceTo(above) < 10) return aboveNearest;
        }
        return null;
    }

    // from skyhanni onSecondPassed
    public static void onSecondPassed() {
        Data d = data;
        if (d != null) {
            if (!d.disabled && d.foundButNotClickedSoul != null && d.foundButNotClickedSoul.distanceTo(playerPos.get()) > 5) {
                pathTo(d.foundButNotClickedSoul);
                d.foundButNotClickedSoul = null;
            }
            return;
        }
        reload();
    }

    // navigator reached the current target (skyhanni IslandGraphs onFound)
    public static void onArrived(Vec3 target) {
        Data d = data;
        if (d != null && !d.route.isEmpty() && d.route.get(0).distanceToSqr(target) < 1) d.foundButNotClickedSoul = target;
    }

    // from skyhanni onInventoryFullyOpened("Fairy Souls Guide"): island items with "Fairy Souls: x/y" as the
    // first lore line
    public static void onGuide(java.util.Map<String, String> itemNameToFirstLore) {
        for (var e : itemNameToFirstLore.entrySet()) {
            String islandKey = islandByName(e.getKey());
            if (islandKey == null || e.getValue() == null) continue;
            Matcher m = LORE_SOUL.matcher(GameState.strip(e.getValue()));
            if (!m.matches()) continue;
            int found;
            try {
                found = Integer.parseInt(m.group("found").trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            if (islandKey.equals(island) && data != null) checkHaveAll(data);
            storage.get().totalFound.put(islandKey, found);
            Store.markDirty();
        }
    }

    // from skyhanni IslandType.getByNameOrNull for the guides item names
    static String islandByName(String name) {
        String n = GameState.strip(name).trim();
        return switch (n) {
            case "Hub" -> "HUB";
            case "Private Island" -> "PRIVATE_ISLAND";
            case "The Park" -> "THE_PARK";
            case "The Farming Islands" -> "THE_FARMING_ISLANDS";
            case "Spider's Den" -> "SPIDER_DEN";
            case "The End" -> "THE_END";
            case "Crimson Isle" -> "CRIMSON_ISLE";
            case "Gold Mine" -> "GOLD_MINES";
            case "Deep Caverns" -> "DEEP_CAVERNS";
            case "Dwarven Mines" -> "DWARVEN_MINES";
            case "Crystal Hollows" -> "CRYSTAL_HOLLOWS";
            case "Jerry's Workshop", "Winter Island" -> "WINTER";
            case "Dungeon Hub" -> "DUNGEON_HUB";
            case "The Rift" -> "THE_RIFT";
            case "Backwater Bayou" -> "BACKWATER_BAYOU";
            case "Galatea" -> "GALATEA";
            default -> null;
        };
    }

    public static Data data() {
        return data;
    }

    public static void resetFoundOnCurrentIsland() {
        storage.get().totalFound.put(island, 0);
        foundOnIsland(island).clear();
        Store.markDirty();
    }

    // --- mc glue ---

    private static long lastSecond;
    private static String lastIsland = "";

    private static boolean enabled() {
        return (GameState.inSkyBlock || SbcMod.DEV) && Features.on(ID) && (dev.zmuzy.sbc.dungeon.DungeonMarks.opt(ID, "on_bingo") || !dev.zmuzy.sbc.events.bingo.BingoCard.onBingo());
    }

    private static void render(WorldDraw w) {
        Data d = data;
        if (d == null || d.disabled || d.route.isEmpty()) return;
        // next soul: the navigator draws the path, the soul gets a waypoint
        w.waypoint(d.route.get(0), 0xFFAA00AA, "§5Fairy Soul §8(" + d.found + "/" + d.total + ")", true, true);
        // from skyblocker waypoint/FairySouls: the others too, found ones in another color, optionally only the ones near you
        boolean showFound = dev.zmuzy.sbc.dungeon.DungeonMarks.opt(ID, "show_found"), nearby = dev.zmuzy.sbc.dungeon.DungeonMarks.opt(ID, "only_nearby");
        for (Vec3 soul : d.allSouls) {
            if (soul.equals(d.route.get(0))) continue;
            boolean unfound = d.route.contains(soul);
            if (!unfound && !showFound) continue;
            if (nearby && soul.distanceTo(w.eye) > 50 || !w.visible(soul)) continue;
            w.waypointBox(soul, unfound ? 0xFFAA00AA : 0xFF55FF55, false, false, 0);
        }
    }

    public static void register() {
        playerPos = () -> {
            var p = Minecraft.getInstance().player;
            return p == null ? Vec3.ZERO : p.position();
        };
        inAir = () -> {
            var p = Minecraft.getInstance().player;
            return p != null && !p.onGround();
        };
        storage = () -> {
            String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
            return Store.I.fairySouls.computeIfAbsent(key, k -> new Store.FairyProfile());
        };
        graph = () -> Repo.graph(island);
        pathFind = (loc, label) -> Navigator.navigate(new Navigator.Target(GameState.island, label, loc));
        stopNavigation = Navigator::stop;
        chat = m -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §r" + m));
        };
        Feature f = Features.add(new Feature(ID, "Navigation", "Fairy souls pathfinder", "Routes you through the island's unfound fairy souls one after another (navigator path + waypoint); found souls are remembered per profile, the Fairy Souls Guide menu sets Hypixel's totals.", "SkyHanni: Fast Fairy Souls Pathfinder")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff().world(FairySouls::render)
                .option(dev.zmuzy.sbc.feature.Option.bool("fairy_souls.on_bingo", "On Bingo profiles too", "", true))
                .option(dev.zmuzy.sbc.feature.Option.bool("fairy_souls.show_found", "Also mark the ones you found", "In green.", true))
                .option(dev.zmuzy.sbc.feature.Option.bool("fairy_souls.only_nearby", "Only mark souls within 50 blocks", "The next one is always marked.", false)));
        ChatBus.register(ChatBus.OBSERVE + 2, "fairy_souls", f, line -> {
            onChat(line.plain());
            return ChatBus.PASS;
        });
        Safe.endTick("FairySouls.endTick", mc -> {
            if (mc.player == null) return;
            if (!GameState.island.equals(lastIsland)) {
                lastIsland = GameState.island;
                onWorldChange(GameState.island);
            }
            if (!enabled()) {
                data = null;
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastSecond >= 1000) {
                lastSecond = now;
                Navigator.Target t = Navigator.target();
                Data d = data;
                if (t == null && d != null && !d.route.isEmpty() && d.foundButNotClickedSoul == null && lastTarget != null) onArrived(lastTarget.pos());
                lastTarget = t;
                onSecondPassed();
            }
        });
        Safe.commands("FairySouls.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("soulsreset").executes(c -> {
                    resetFoundOnCurrentIsland();
                    reload();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fReset found Fairy Souls on " + GameState.pretty(island) + "."));
                    return 1;
                }))
                .then(literal("soulsfoundall").executes(c -> {
                    Data d = data;
                    if (d != null) allFound(d, "manually set all souls as found via command");
                    reload();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fMarked all Fairy Souls as found on " + GameState.pretty(island) + "."));
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("soulsreset", "Reset the known Fairy Souls of this island");
        dev.zmuzy.sbc.core.Commands.describe("soulsfoundall", "Mark every Fairy Soul of this island as found");
    }

    private static Navigator.Target lastTarget;
}
