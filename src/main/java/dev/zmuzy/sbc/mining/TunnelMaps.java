// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni TunnelsMaps over island_graphs/GLACITE_TUNNELS: a path to any place in the glacite tunnels
// pick it next to your inventory or by right clicking a collector commission
// nearest spot not just visited gets picked, once you get there it waits a min before it counts again
public final class TunnelMaps {
    static final String ID = "mining_tunnel_maps";
    private static final Set<String> AREAS = Set.of("Glacite Tunnels", "Dwarven Base Camp", "Great Glacite Lake", "Fossil Research Center");
    private static final Patterns.P GLACITE_REWARD = Patterns.p("SH.mining.commisson.reward.glacite.colorless");
    private static final Patterns.P INVALID = Patterns.p("SH.mining.commisson.collector.invalid");
    private static final String OLD_GEMS = "Ruby|Amethyst|Jade|Sapphire|Amber|Topaz", NEW_GEMS = "Aquamarine|Onyx|Citrine|Peridot";
    private static final long NEXT_DELAY = 500;

    private static Graph graph;
    private static final Map<String, List<Graph.Node>> PLACES = new LinkedHashMap<>();
    private static final Map<String, Graph.Node> FAIRY = new LinkedHashMap<>();
    private static final Map<String, List<Graph.Node>> GEMS = new LinkedHashMap<>(), OTHERS = new LinkedHashMap<>();
    private static Graph.Node campfire;
    private static final Map<Graph.Node, Long> COOLDOWNS = new HashMap<>();
    private static String active = "";
    private static Graph.Node goal, prevGoal, closest;
    private static boolean goalReached, isCommission;
    private static List<Vec3> path = List.of();
    private static final Map<Vec3, String> LANDMARKS = new HashMap<>();
    private static double pathLength;
    private static Map<Integer, String> clickTranslate = Map.of();
    private static long lastCampKey, nextSpotAt;
    private static KeyMapping campfireKey, nextKey;
    private static final Panel PANEL = new Panel();

    private TunnelMaps() {}

    // skyhanni isEnabled: dwarven mines, in the tunnels or whats leading there
    private static boolean here() {
        return "DWARVEN_MINES".equals(GameState.island) && AREAS.contains(GameState.area);
    }

    // skyhanni onRepoReload: colored names are places, gemstones + fairy souls + campfire get their own lists
    private static synchronized void load() {
        if (graph != null) return;
        Graph g = Repo.graph("GLACITE_TUNNELS");
        if (g == null || g.isEmpty()) return;
        graph = g;
        for (Graph.Node n : g.nodes) if (n.name() != null && !n.name().isEmpty()) PLACES.computeIfAbsent(n.name(), k -> new ArrayList<>()).add(n);
        Map<String, List<Graph.Node>> newGems = new LinkedHashMap<>(), oldGems = new LinkedHashMap<>();
        for (var e : PLACES.entrySet()) {
            String key = e.getKey();
            if (key.contains("Campfire")) campfire = e.getValue().get(0);
            else if (key.contains("Fairy")) FAIRY.put(key, e.getValue().get(0));
            else if (key.matches(".*(?:" + NEW_GEMS + ").*")) newGems.put(key, e.getValue());
            else if (key.matches(".*(?:" + OLD_GEMS + ").*")) oldGems.put(key, e.getValue());
            else if (!GameState.strip(key).equals(key)) OTHERS.put(key, e.getValue());
        }
        GEMS.putAll(newGems);
        GEMS.putAll(oldGems);
    }

    private static Graph.Node next() {
        Graph.Node fairy = FAIRY.get(active);
        if (fairy != null) {
            goalReached = false;
            return fairy;
        }
        List<Graph.Node> list = PLACES.get(active);
        if (closest == null || list == null) return null;
        long now = MiningEnv.now();
        Graph.Node best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Graph.Node n : list) {
            Long c = COOLDOWNS.get(n);
            if (c != null && c > now) continue;
            double d = length(graph.path(closest, n));
            if (d < bestDistance) {
                bestDistance = d;
                best = n;
            }
        }
        if (best == null) {
            long oldest = Long.MAX_VALUE;
            for (Graph.Node n : list) {
                long c = COOLDOWNS.getOrDefault(n, MiningEnv.FAR_PAST);
                if (c < oldest) {
                    oldest = c;
                    best = n;
                }
            }
        }
        if (best != null && COOLDOWNS.getOrDefault(best, MiningEnv.FAR_PAST) <= now) COOLDOWNS.put(best, now + 5000);
        goalReached = false;
        return best;
    }

    private static double length(List<Vec3> p) {
        double d = 0;
        for (int i = 1; i < p.size(); i++) d += p.get(i - 1).distanceTo(p.get(i));
        return d;
    }

    private static void setGoal(Graph.Node n) {
        prevGoal = goal;
        goal = n;
    }

    private static void activate(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (closest == null && graph != null && mc.player != null) closest = graph.nearest(mc.player.position());
        active = name;
        setGoal(next());
        path = List.of();
    }

    private static void clear() {
        path = List.of();
        prevGoal = goal;
        goal = null;
    }

    private static void campfireOverride() {
        goalReached = false;
        setGoal(campfire);
    }

    private static void nextSpot() {
        if (MiningEnv.now() < nextSpotAt) return;
        nextSpotAt = MiningEnv.now() + NEXT_DELAY;
        setGoal(next());
    }

    // skyhanni onInventoryFullyOpened("Commissions"): which slots are collector commissions we can walk to
    static void readCommissions(MenuView v) {
        Map<Integer, String> out = new HashMap<>();
        load();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty() || it.firstClean(GLACITE_REWARD.pattern()) == null || it.cleanHas("COMPLETED")) continue;
            Matcher c = it.firstClean(Commissions.COLLECTOR.pattern());
            if (c == null) continue;
            String type = c.group("what");
            if (INVALID.matcher(type).matches()) continue;
            for (String key : PLACES.keySet()) {
                if (GameState.strip(key).toUpperCase(java.util.Locale.ROOT).contains(type.toUpperCase(java.util.Locale.ROOT))) {
                    out.put(slot, key);
                    break;
                }
            }
        }
        clickTranslate = out;
        if (!MiningPack.on(ID, "mining_tunnel_maps.auto_commission") || !here()) return;
        if (!out.isEmpty()) {
            isCommission = true;
            activate(out.values().iterator().next());
        } else if (isCommission) {
            active = "";
            clear();
            isCommission = false;
        }
    }

    private static boolean onSlot(AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button) {
        if (button == 1 && slot != null && Features.on(ID) && here()) {
            String place = clickTranslate.get(slotId);
            if (place != null) {
                isCommission = true;
                activate(place);
            }
        }
        return false;
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        if (clickTranslate.isEmpty()) return;
        MenuView v = MenuMarks.current();
        if (v == null || !v.title.equals("Commissions")) return;
        for (int slot : clickTranslate.keySet()) {
            MenuView.Item it = v.item(slot);
            if (it.clean.equals(GameState.strip(stack.getHoverName().getString()).trim())) {
                lines.add(Component.literal("§e§lRight Click §r§efor Tunnel Maps."));
                return;
            }
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        load();
        if (graph == null || !here() || checkReached(mc)) return;
        Graph.Node prevClosest = closest;
        closest = graph.nearest(mc.player.position());
        if (closest == null || goal == null) return;
        if (closest == prevClosest && goal == prevGoal && !path.isEmpty()) return;
        prevGoal = goal;
        List<Vec3> nodes = graph.path(closest, goal);
        pathLength = length(nodes) + (nodes.isEmpty() ? 0 : mc.player.position().distanceTo(nodes.get(0)));
        path = nodes;
        LANDMARKS.clear();
        for (Vec3 p : nodes) {
            Graph.Node n = graph.nearest(p);
            if (n != null && n != goal && n.name() != null && !n.name().isEmpty() && n.pos().distanceToSqr(p) < 1) LANDMARKS.put(p, n.name());
        }
    }

    private static boolean checkReached(Minecraft mc) {
        if (goalReached) return true;
        if (goal == null) return false;
        double d = goal.pos().distanceToSqr(mc.player.position());
        goalReached = d < (goal == campfire ? 15.0 * 15.0 : 6.0 * 6.0);
        if (!goalReached) return false;
        if (goal == campfire && !active.equals(campfire.name())) setGoal(next());
        else {
            COOLDOWNS.put(goal, MiningEnv.now() + 60_000);
            clear();
        }
        return true;
    }

    private static int pathColor() {
        if (MiningPack.on(ID, "mining_tunnel_maps.dynamic_color") && goal != null) {
            String s = goal.name();
            if (s != null && s.length() >= 2 && s.charAt(0) == '§') {
                net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(s.charAt(1));
                var c = f == null ? null : net.minecraft.network.chat.TextColor.fromLegacyFormat(f);
                if (c != null && f != net.minecraft.ChatFormatting.WHITE) return 0xFF000000 | c.getValue();
            }
        }
        return MiningPack.color(ID, "mining_tunnel_maps.path_color", 0xFF00FF00);
    }

    private static void draw(WorldDraw w) {
        if (!here() || path.isEmpty() || goal == null) return;
        int color = pathColor();
        float width = MiningPack.number(ID, "mining_tunnel_maps.path_width", 4);
        double text = MiningPack.number(ID, "mining_tunnel_maps.text_size", 100) / 100.0;
        List<Vec3> route = new ArrayList<>();
        route.add(w.eye.add(w.mc.player.getLookAngle().scale(0.8)).add(0, -0.35, 0));
        for (Vec3 p : path) route.add(p.add(0.5, 0.5, 0.5));
        List<Vec3> smooth = Navigator.rounded(route);
        for (int i = 1; i < smooth.size(); i++) w.line(smooth.get(i - 1), smooth.get(i), color, width, true);
        if (MiningPack.on(ID, "mining_tunnel_maps.landmarks")) for (var e : LANDMARKS.entrySet()) w.dynamicText(e.getKey().add(0.5, 1.5, 0.5), e.getValue(), 1.0 * text, 0);
        w.dynamicText(goal.pos().add(0.5, 1.5, 0.5), goal.name(), 1.5 * text, 0);
        Vec3 distanceAt = MiningPack.on(ID, "mining_tunnel_maps.distance_first") ? path.get(0) : path.get(path.size() - 1);
        w.dynamicText(distanceAt.add(0.5, 1.5, 0.5), "§e" + Math.round(pathLength) + "m", 1.0 * text, -1);
    }

    // runs everywhere: an unread key press would fire when you come back
    private static void keys(Minecraft mc) {
        if (campfireKey == null) return;
        while (campfireKey.consumeClick()) {
            if (mc.gui.screen() != null || !Features.on(ID) || !here() || MiningEnv.passedSince(lastCampKey) < 2000) continue;
            lastCampKey = MiningEnv.now();
            if (MiningPack.on(ID, "mining_tunnel_maps.travel_scroll")) MiningEnv.world.sendCommand("warp basecamp");
            else campfireOverride();
        }
        while (nextKey.consumeClick()) if (mc.gui.screen() == null && Features.on(ID) && here()) nextSpot();
    }

    private static void leave() {
        closest = null;
        clear();
        COOLDOWNS.clear();
        goalReached = false;
        clickTranslate = Map.of();
    }

    private static final class Panel implements SideHelper.Content {
        @Override
        public String title() {
            return "Tunnel Maps";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("map");
        }

        @Override
        public int accent() {
            return 0xFF8FD3FF;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Places"};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            load();
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            return 0;
        }

        private int row(Ui ui, String text, int x, int y, int w, Runnable click, String... tips) {
            boolean hv = ui.hover(x, y, w, 11);
            if (hv) ui.rect(x, y, w, 11, 0x33FFFFFF);
            ui.textShadow(ui.ellipsize(text, w - 4), x + 2, y + 1, Modern.LABEL);
            if (hv && tips.length > 0) ui.tooltip(tips);
            ui.click(x, y, w, 11, click);
            return 11;
        }

        private int places(Ui ui, Map<String, List<Graph.Node>> list, int x, int y, int w) {
            int y0 = y;
            for (String name : list.keySet()) {
                y += row(ui, name, x, y, w, () -> {
                    isCommission = false;
                    activate(name);
                });
            }
            return y - y0;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            if (!active.isEmpty()) {
                if (goal == campfire && campfire != null && !active.equals(campfire.name())) {
                    ui.textShadow(ui.ellipsize("§6Override for " + campfire.name(), w - 4), x + 2, y + 1, Modern.LABEL);
                    y += 11;
                    y += row(ui, "§eMake §f" + active + " §eactive", x, y, w, TunnelMaps::nextSpot);
                } else {
                    y += row(ui, "§6Active: §f" + active, x, y, w, TunnelMaps::clear, "§eClick to stop");
                    List<Graph.Node> l = PLACES.get(active);
                    if (l != null && l.size() > 1) y += row(ui, "§eNext Spot", x, y, w, TunnelMaps::nextSpot);
                }
                y += 3;
            }
            if (campfire != null) {
                String name = campfire.name();
                y += row(ui, name, x, y, w, () -> activate(name));
                y += row(ui, "§7" + GameState.strip(name) + " for now", x, y, w, TunnelMaps::campfireOverride, "§7Goes back to your place afterwards");
            }
            if (!MiningPack.on(ID, "mining_tunnel_maps.exclude_fairy")) {
                for (String name : FAIRY.keySet()) y += row(ui, name, x, y, w, () -> activate(name));
            }
            y += places(ui, GEMS, x, y, w);
            y += places(ui, OTHERS, x, y, w);
            if (y == y0) y += SideHelper.emptyText(ui, "The map of the tunnels is not there.", x, y, w);
            return y - y0;
        }
    }

    private static SideHelper.Content panel(Screen s) {
        return s instanceof InventoryScreen && Features.on(ID) && here() ? PANEL : null;
    }

    private static boolean onPigeon(boolean right) {
        if (!right && Features.on(ID) && here() && MiningPack.on(ID, "mining_tunnel_maps.left_click_pigeon") && "ROYAL_PIGEON".equals(MiningPack.heldId())) nextSpot();
        return false;
    }

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Tunnel Maps", "A path to any place in the Glacite Tunnels: choose it beside your inventory, or right click a collector commission. The nearest spot goes first; a key, or a left click with the Royal Pigeon, picks the next.", "SkyHanni: Tunnel Maps")
                .when(MiningPack::dwarven).tick(TunnelMaps::tick).world(TunnelMaps::draw).tooltip(TunnelMaps::tooltip).left(TunnelMaps::leave)
                .option(Option.bool("mining_tunnel_maps.landmarks", "Landmarks on the way", "", true))
                .option(Option.bool("mining_tunnel_maps.auto_commission", "Take the first commission", "When you open the Commissions menu.", false))
                .option(Option.bool("mining_tunnel_maps.travel_scroll", "I have the travel scroll", "The campfire key warps to the Base Camp you asked for, instead of a path.", false))
                .option(Option.bool("mining_tunnel_maps.left_click_pigeon", "Left click the Royal Pigeon", "For the next spot.", true))
                .option(Option.bool("mining_tunnel_maps.dynamic_color", "Line in the place's colour", "", true))
                .option(Option.color("mining_tunnel_maps.path_color", "Line colour", "", 0xFF00FF00))
                .option(Option.integer("mining_tunnel_maps.text_size", "Text size (%)", "", 100, 50, 300))
                .option(Option.integer("mining_tunnel_maps.path_width", "Line width", "", 4, 1, 10))
                .option(Option.bool("mining_tunnel_maps.distance_first", "Distance at the first bend", "Instead of at the end.", false))
                .option(Option.bool("mining_tunnel_maps.exclude_fairy", "Leave out fairy souls", "", false)));
        MenuMarks.add(ID, "Commissions", TunnelMaps::readCommissions);
        SideHelper.add(TunnelMaps::panel);
        Interactions.onSlotClick(TunnelMaps::onSlot);
        Interactions.onItemClick(TunnelMaps::onPigeon);
        Safe.endTick("TunnelMaps.keys", TunnelMaps::keys);
        try {
            KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sbcompanion", "mining"));
            campfireKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.tunnel_campfire", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), cat));
            nextKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.tunnel_next", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), cat));
        } catch (Throwable t) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("Tunnel Maps keys", t);
        }
    }
}
