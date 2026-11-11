// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker, SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.muzy.sbc.core.Profile;
import io.muzy.sbc.core.data.DataFiles;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// hotm + hotf trees like the profile page shows them, for anyone the server answers about and for you from
// what /hotm and /hotf showed. from skyblocker profileviewer2
public final class HeartTrees {
    private HeartTrees() {}

    public enum State {
        // from game: tree didnt show this perk yet
        UNREAD,
        LOCKED,
        ON,
        OFF,
        MAX
    }

    // one perk. x, y = spot on the tree, tooltip worked out ahead so nothing runs while drawing
    public record Node(String key, String name, int x, int y, int level, int max, State state, String icon, List<String> tooltip) {}

    public static final String MOUNTAIN_CORE = "Core of the Mountain", FOREST_CORE = "Center of the Forest";
    // columns per tree, hotmlayout.json x
    public static final int COLUMNS = 7;

    // from skyhanni HotfData: name, max level, cost exponent. 0 = no cost, -1 = daily wishes straight line.
    // ForageHelpers reads it too
    public static final Object[][] HOTF_PERKS = {{"Sweep", 50, 3.0}, {"Foraging Fortune", 50, 3.105}, {"Strength Boost", 50, 3.1}, {"Damage Boost", 2, 0d}, {"Speed Boost", 50, 3.1}, {"Axe Toss", 2, 0d}, {"Luck of the Forest", 40, 3.07}, {"Daily Wishes", 100, -1d}, {"250 Gifts", 40, 3.07}, {"Lottery", 2, 0d}, {"Foraging Madness", 2, 0d}, {"Deep Waters", 50, 2.9}, {"Efficient Forager", 100, 2.6}, {"Collector", 50, 2.9}, {"Early Bird", 2, 0d}, {"Precision Cutting", 2, 0d}, {"Monster Hunter", 2, 0d}, {"Tree Whisperer", 2, 0d}, {"Homing Axe", 2, 0d}, {"Forest Strength", 50, 3.4}, {"Beekeeper", 2, 0d}, {"Hunter's Luck", 50, 3.2}, {"Galatea's Might", 50, 3.2}, {"Essence Fortune", 50, 3.2}, {"Forest Speed", 50, 3.4}, {"Maniac Slicer", 2, 0d}, {"Half Empty", 25, 4.1}, {"Ricochet", 10, 5.5}, {"Half Full", 25, 4.1}, {FOREST_CORE, 5, 0d}};

    // from skyblocker SkillTreeBuilder.LORE_TEMPLATE_PATTERN
    private static final Pattern TEMPLATE = Pattern.compile("\\{(?<name>[\\w\\-]+)\\}");
    // unicode private area = neus own font glyphs, mc cant draw them. the stat name goes next to each
    private static final Pattern FONT_GLYPH = Pattern.compile("[\\uE000-\\uF8FF]");
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static boolean loaded;
    private static JsonObject perks, powders;
    private static TreeLisp lisp;
    private static final Map<String, Integer> MOUNTAIN_MAX = new HashMap<>();
    private static final Map<String, Integer> FOREST_MAX = new HashMap<>();

    // last trees worked out, a profiles data doesnt change once built
    private static Profile.Data cachedFor;
    private static List<Node> cachedMountain = List.of(), cachedForest = List.of();

    static {
        for (Object[] p : HOTF_PERKS) FOREST_MAX.put((String) p[0], (Integer) p[1]);
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        JsonObject file = DataFiles.object(DataFiles.Source.NEU, "hotmlayout.json");
        if (file == null || !file.has("hotm")) return;
        List<String> prelude = new ArrayList<>();
        for (JsonElement e : file.getAsJsonArray("prelude")) prelude.add(e.getAsString());
        lisp = new TreeLisp(prelude);
        perks = file.getAsJsonObject("hotm").getAsJsonObject("perks");
        powders = file.getAsJsonObject("hotm").getAsJsonObject("powders");
        for (Map.Entry<String, JsonElement> e : perks.entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            MOUNTAIN_MAX.put(p.get("name").getAsString(), p.get("maxLevel").getAsInt());
        }
    }

    // "Miner's Blessing" -> "miners_blessing": the apis perk id form + how menu perks get kept
    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", "_");
    }

    // perk max level, 0 = an item with that name isnt a perk of this tree
    static int maxLevel(boolean mountain, String name) {
        load();
        return (mountain ? MOUNTAIN_MAX : FOREST_MAX).getOrDefault(name, 0);
    }

    public static synchronized List<Node> mountain(Profile.Data d) {
        cache(d);
        return cachedMountain;
    }

    public static synchronized List<Node> forest(Profile.Data d) {
        cache(d);
        return cachedForest;
    }

    private static void cache(Profile.Data d) {
        if (d == cachedFor) return;
        cachedFor = d;
        cachedMountain = buildMountain(d);
        cachedForest = buildForest(d);
    }

    private static State state(boolean read, int level, int max, boolean off) {
        if (!read) return State.UNREAD;
        if (level <= 0) return State.LOCKED;
        if (off) return State.OFF;
        return level >= max ? State.MAX : State.ON;
    }

    private static List<Node> buildMountain(Profile.Data d) {
        load();
        List<Node> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        if (perks == null) return out;
        int tier = d.hotm == null ? 0 : d.hotm.level();
        int core = levelOf(d.hotmNodes, "core_of_the_mountain", MOUNTAIN_CORE);
        int rows = 0;
        for (Map.Entry<String, JsonElement> e : perks.entrySet()) {
            JsonObject p = e.getValue().getAsJsonObject();
            String id = e.getKey(), name = p.get("name").getAsString(), byName = key(name);
            // the api says "anomalous_desire" where the game says "Tunnel Vision"
            String found = d.hotmNodes.containsKey(id) ? id : byName;
            used.add(id);
            used.add(byName);
            boolean read = !d.fromGame || d.hotmNodes.containsKey(found);
            int level = d.hotmNodes.getOrDefault(found, 0), max = p.get("maxLevel").getAsInt(), y = p.get("y").getAsInt();
            boolean off = d.hotmOff.contains(found);
            State state = state(read, level, max, off);
            rows = Math.max(rows, y + 1);
            Map<String, Double> bindings = new HashMap<>();
            bindings.put("hotm", (double) tier);
            bindings.put("potm", (double) core);
            bindings.put("level", (double) Math.max(1, level));
            bindings.put("level0", (double) level);
            bindings.put("maxLevel", (double) max);
            String icon = state == State.OFF ? "barrier" : lisp.run(text(p, "item"), bindings) instanceof String s ? s.toLowerCase(Locale.ROOT) : "barrier";
            out.add(new Node(found, name, p.get("x").getAsInt(), y, level, max, state, icon, mountainTip(p, name, level, max, state, bindings)));
        }
        extras(out, d.hotmNodes, d.hotmOff, used, rows);
        return out;
    }

    private static int levelOf(Map<String, Integer> nodes, String id, String name) {
        Integer level = nodes.get(id);
        return level != null ? level : nodes.getOrDefault(key(name), 0);
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    // from skyblocker HotmTreeBuilder.getTooltip
    private static List<String> mountainTip(JsonObject p, String name, int level, int max, State state, Map<String, Double> bindings) {
        List<String> tip = new ArrayList<>();
        tip.add((state == State.MAX ? "§a" : state == State.ON ? "§e" : "§c") + name);
        if (max > 1) tip.add(level >= max ? "§7Level " + max : "§7Level " + level + "§8/" + max);
        if (state == State.UNREAD) {
            tip.add("§8Not read yet.");
            return tip;
        }
        tip.add("");
        List<String> lines = new ArrayList<>();
        for (JsonElement l : p.getAsJsonArray("lore")) {
            if (l.isJsonPrimitive()) lines.add(l.getAsString());
            else if (Boolean.TRUE.equals(lisp.run(text(l.getAsJsonObject(), "onlyIf"), bindings))) lines.add(text(l.getAsJsonObject(), "text"));
        }
        // skyblocker skips the cost for a perk not bought yet and one at max
        if (level > 0 && level < max) {
            String powder = lisp.run(text(p, "powder"), bindings) instanceof String s ? s : "";
            if (powders.has(powder)) {
                lines.add("");
                lines.add(text(powders.getAsJsonObject(powder), "costLine"));
            }
        }
        for (String line : lines) tip.add(FONT_GLYPH.matcher(fill(line, p, bindings)).replaceAll(""));
        if (level > 0) {
            tip.add("");
            tip.add(state == State.OFF ? "§c§lDISABLED" : name.equals(MOUNTAIN_CORE) ? "§a§lUNLOCKED" : "§a§lENABLED");
        }
        return tip;
    }

    // a lines {stat} + {cost}, each a little program in the perks own entry
    private static String fill(String line, JsonObject p, Map<String, Double> bindings) {
        Matcher m = TEMPLATE.matcher(line);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group("name");
            Object value = lisp.run(text(p, name), bindings);
            String shown = switch (value) {
                case Double v -> NUMBER.format(name.equals("cost") ? Math.floor(v) : v);
                case String s -> s;
                case null, default -> "?";
            };
            m.appendReplacement(out, Matcher.quoteReplacement(shown));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static List<Node> buildForest(Profile.Data d) {
        List<Node> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        int i = 0;
        for (Object[] perk : HOTF_PERKS) {
            String name = (String) perk[0], id = key(name);
            int max = (Integer) perk[1], level = d.hotfNodes.getOrDefault(id, 0);
            used.add(id);
            State state = state(!d.fromGame || d.hotfNodes.containsKey(id), level, max, d.hotfOff.contains(id));
            List<String> tip = new ArrayList<>();
            tip.add((state == State.MAX ? "§a" : state == State.ON ? "§e" : "§c") + name);
            tip.add(level >= max ? "§7Level " + max : "§7Level " + level + "§8/" + max);
            if (state == State.UNREAD) tip.add("§8Not read yet.");
            else if (level > 0) {
                tip.add("");
                tip.add(state == State.OFF ? "§c§lDISABLED" : name.equals(FOREST_CORE) ? "§a§lUNLOCKED" : "§a§lENABLED");
            }
            out.add(new Node(id, name, i % COLUMNS, i / COLUMNS, level, max, state, forestIcon(name, level, max, state), tip));
            i++;
        }
        extras(out, d.hotfNodes, d.hotfOff, used, (i + COLUMNS - 1) / COLUMNS);
        return out;
    }

    // the mountains own signs from hotmlayout.json: coal not bought, emerald bought, diamond maxed. barrier =
    // turned off (the files core is a redstone block, skyblockers sign for off)
    private static String forestIcon(String name, int level, int max, State state) {
        if (name.equals(FOREST_CORE)) return "redstone_block";
        return switch (state) {
            case OFF -> "barrier";
            case UNREAD, LOCKED -> "coal";
            case MAX -> "diamond";
            case ON -> "emerald";
        };
    }

    // perks the server names that neither list knows (like a new one) still show under the tree with their level
    private static void extras(List<Node> out, Map<String, Integer> nodes, Set<String> off, Set<String> used, int firstRow) {
        int i = 0;
        for (Map.Entry<String, Integer> e : new LinkedHashMap<>(nodes).entrySet()) {
            if (used.contains(e.getKey())) continue;
            String name = io.muzy.sbc.core.GameState.pretty(e.getKey());
            State state = e.getValue() <= 0 ? State.LOCKED : off.contains(e.getKey()) ? State.OFF : State.ON;
            out.add(new Node(e.getKey(), name, i % COLUMNS, firstRow + i / COLUMNS, e.getValue(), e.getValue(), state, state == State.OFF ? "barrier" : state == State.LOCKED ? "coal" : "emerald",
                    List.of("§e" + name, "§7Level " + e.getValue())));
            i++;
        }
    }

    // tests: reread the layout + forget whats worked out
    static synchronized void forget() {
        loaded = false;
        cachedFor = null;
        MOUNTAIN_MAX.clear();
    }
}
