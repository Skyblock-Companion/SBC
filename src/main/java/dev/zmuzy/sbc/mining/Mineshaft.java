// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni glacitemineshaft: MineshaftDetection (type from the sidebar type code, per profile "since"
// counters), MineshaftCaveInTimer (60 s from entering, cold rate guess)
// MineshaftPityDisplay (pity from the tab list), MineshaftWaypointManager + MineshaftWaypoint (entrance +
// ladder on entry, corpses via CorpseFinder, looted via CorpseLoot chat)
// CorpseSharing (coords in party / all chat), and FrozenCorpses.json for the known corpse spots per type
public final class Mineshaft {
    public enum Type {
        TOPA_1("§e", "Topaz 1"), TOPA_2("§e", "Topaz 2"), SAPP_1("§9", "Sapphire 1"), SAPP_2("§9", "Sapphire 2"),
        AMET_1("§5", "Amethyst 1"), AMET_2("§5", "Amethyst 2"), AMBE_1("§6", "Amber 1"), AMBE_2("§6", "Amber 2"),
        JADE_1("§a", "Jade 1"), JADE_2("§a", "Jade 2"), TITA_1("§7", "Titanium"), UMBE_1("§6", "Umber"), TUNG_1("§8", "Tungsten"),
        FAIR_1("§f", "Vanguard"), RUBY_1("§c", "Ruby 1"), RUBY_2("§c", "Ruby 2"), RUBY_C("§c", "Ruby Crystal"),
        ONYX_1("§0", "Onyx 1"), ONYX_2("§0", "Onyx 2"), ONYX_C("§0", "Onyx Crystal"), AQUA_1("§1", "Aquamarine 1"), AQUA_2("§1", "Aquamarine 2"), AQUA_C("§1", "Aquamarine Crystal"),
        CITR_1("§e", "Citrine 1"), CITR_2("§e", "Citrine 2"), CITR_C("§e", "Citrine Crystal"), PERI_1("§2", "Peridot 1"), PERI_2("§2", "Peridot 2"), PERI_C("§2", "Peridot Crystal"),
        JASP_1("§d", "Jasper"), JASP_C("§d", "Jasper Crystal"), OPAL_1("§f", "Opal"), OPAL_C("§f", "Opal Crystal"), LITT_L("§b", "Littlefoot's Den");

        public final String color, rawName;

        Type(String color, String rawName) {
            this.color = color;
            this.rawName = rawName;
        }

        public String displayName() {
            return color + rawName;
        }
    }

    // skyhanni CorpseType: helmet id -> corpse + the key it needs
    public enum Corpse {
        LAPIS("Lapis", "§9", "LAPIS_ARMOR_HELMET", null, 0xFF5555FF),
        TUNGSTEN("Tungsten", "§7", "MINERAL_HELMET", "TUNGSTEN_KEY", 0xFFAAAAAA),
        UMBER("Umber", "§6", "ARMOR_OF_YOG_HELMET", "UMBER_KEY", 0xFFFFAA00),
        VANGUARD("Vanguard", "§f", "VANGUARD_HELMET", "SKELETON_KEY", 0xFFFFFFFF);

        public final String type, color, helmet, key;
        public final int argb;

        Corpse(String type, String color, String helmet, String key, int argb) {
            this.type = type;
            this.color = color;
            this.helmet = helmet;
            this.key = key;
            this.argb = argb;
        }

        public String displayName() {
            return color + type;
        }

        public static Corpse byHelmet(String internalName) {
            for (Corpse c : values()) if (c.helmet.equals(internalName)) return c;
            return null;
        }
    }

    // from skyhanni MineshaftWaypoint
    public static final class Waypoint {
        public enum Kind { ENTRANCE, LADDER, FOUND_CORPSE, LOOTED_CORPSE, EXPECTED_CORPSE }

        public Kind kind;
        public final Vec3 location;
        public final Corpse corpse;
        public boolean isShared, offered;

        Waypoint(Kind kind, Vec3 location, Corpse corpse) {
            this.kind = kind;
            this.location = location;
            this.corpse = corpse;
        }

        public boolean isCorpse() {
            return corpse != null;
        }

        public String label() {
            return switch (kind) {
                case ENTRANCE -> "Entrance";
                case LADDER -> "Ladder";
                case EXPECTED_CORPSE -> "Corpse?";
                default -> (corpse == null ? "Unknown" : corpse.type) + " Corpse";
            };
        }

        public String text() {
            return (kind == Kind.LOOTED_CORPSE ? "§a" : kind == Kind.EXPECTED_CORPSE ? "§7" : "§e") + label();
        }

        public int fillColor() {
            return switch (kind) {
                case ENTRANCE, LADDER -> 0xFFFFFF55;
                case EXPECTED_CORPSE -> 0x80AAAAAA;
                default -> corpse == null ? 0xFF555555 : corpse.argb;
            };
        }
    }

    static final Patterns.P TAB_PITY = Patterns.p("SH.mineshaft.pity.tablist");
    static final Patterns.P LOOT_START = Patterns.p("SH.mining.mineshaft.chat.start");
    static final Patterns.P LOOT_END = Patterns.p("SH.mining.mineshaft.chat.end");
    static final Patterns.P LOOT_ITEM = Patterns.p("SH.mining.mineshaft.chat.item");
    static final Patterns.P RESOURCEFUL = Patterns.p("SH.mining.mineshaft.chat.resourceful");
    static final Patterns.P CORPSE_COORDS = Patterns.p("SH.mineshaft.corpse.coords");
    static final Patterns.P TAB_CORPSE = Patterns.p("SH.mining.glacitemineshaft.tabwidgetcorpse");
    static final Patterns.P PARTY_CHAT = Patterns.p("SH.data.chat.player.party");
    static final Patterns.P GLOBAL_CHAT = Patterns.p("SH.data.chat.player.global");
    static final long CAVE_IN_DURATION = 60_000;
    static final int PITY_TARGET = 2000;
    private static final int MARK_AS_FOUND_TICKS_THRESHOLD = 10;
    private static final int LADDER_BLOCKS_FORWARD = 7, LADDER_BLOCKS_DOWN = -15;

    // MineshaftDetection
    private static boolean found;
    public static Type current;
    // cave in timer
    private static long caveInTimerStart = MiningEnv.FAR_PAST, firstColdTime = MiningEnv.FAR_PAST;
    private static Integer lastColdValue;
    private static int totalColdGained;
    private static Long estimatedMaxTime;
    // pity
    public static int tablistPity = 2000;
    public static boolean everFoundPityWidget;
    public static long lastMineshaftSpawn = MiningEnv.FAR_PAST;
    public static int sessionMineshafts;
    // waypoints
    public static final List<Waypoint> waypoints = new ArrayList<>();
    private static final Map<Integer, int[]> corpseSeenTicks = new HashMap<>();
    private static int totalCorpseCount;
    private static final List<Vec3> sharedWaypoints = new ArrayList<>();
    private static long timeLastShared = MiningEnv.FAR_PAST;
    // CorpseLoot
    private static boolean inLoot;
    private static Corpse yoinkCorpse;
    private static final List<String> loot = new ArrayList<>();
    private static boolean pendingKeyConsumed = true;
    public static final List<String> lastLoot = new ArrayList<>();
    private static Map<String, List<Vec3>> corpsicles;
    private Mineshaft() {}

    // FrozenCorpses.json

    private static synchronized Map<String, List<Vec3>> corpsicles() {
        if (corpsicles != null) return corpsicles;
        Map<String, List<Vec3>> out = new LinkedHashMap<>();
        try (InputStream in = dev.zmuzy.sbc.core.data.DataFiles.sh("FrozenCorpses.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("locations").entrySet()) {
                    List<Vec3> l = new ArrayList<>();
                    for (JsonElement v : e.getValue().getAsJsonArray()) {
                        String[] p = v.getAsString().split(":");
                        l.add(new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
                    }
                    out.put(e.getKey(), Collections.unmodifiableList(l));
                }
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad FrozenCorpses.json", e);
        }
        corpsicles = Collections.unmodifiableMap(out);
        return corpsicles;
    }

    public static List<Vec3> expectedCorpses(Type t) {
        return corpsicles().getOrDefault(t.name(), List.of());
    }

    // MineshaftDetection

    // formatted sidebar lines in, returns the type when it was just detected
    public static Type onScoreboard(List<String> formatted) {
        if (found || !MiningApi.inMineshaft()) return null;
        String matching = null;
        for (String line : formatted) {
            for (Type t : Type.values()) if (line.contains(t.name())) {
                matching = GameState.strip(line);
                break;
            }
            if (matching != null) break;
        }
        if (matching == null) return null;
        String[] parts = matching.trim().split(" ");
        String areaName = parts[parts.length - 1];
        Type type = null;
        for (Type t : Type.values()) if (areaName.contains(t.name())) {
            type = t;
            break;
        }
        if (type == null) return null;
        found = true;
        onDetect(type);
        return type;
    }

    // from skyhanni GlaciteMineshaftDetectEvent
    private static void onDetect(Type type) {
        current = type;
        Store.MiningProfile p = MiningEnv.profile();
        int sinceThis = p.mineshaftsEnteredSince.getOrDefault(type.name(), 0);
        Long timeSince = p.lastMineshaftTime.get(type.name());
        String formattedTime = timeSince != null ? MiningEnv.format(MiningEnv.passedSince(timeSince)) : "Unknown (no data yet)";
        if (Mining.b("mining_mineshaft", "detection", true)) MiningEnv.world.chat("You entered a " + type.displayName() + "§r mineshaft!");
        if (tracked(type)) {
            MiningEnv.world.title(type.displayName());
            MiningEnv.world.chat("§aIt took §e" + formattedTime + " §aand §e" + sinceThis + " §amineshaft" + (sinceThis == 1 ? "" : "s") + " entered to get a " + type.displayName() + "§a mineshaft.");
        }
        OrderedWaypoints.onMineshaft(type.name());
        // from skyhanni handleShaftData
        p.mineshaftsEnteredSince.put(type.name(), 0);
        p.lastMineshaftTime.put(type.name(), MiningEnv.now());
        for (Type other : Type.values()) if (other != type) p.mineshaftsEnteredSince.merge(other.name(), 1, Integer::sum);
        offerType(Mining.b("mining_mineshaft", "sendTypeToPartyChat", false), Mining.text("mining_mineshaft", "partyChatFormat", "{type} mineshaft, {amountSinceThis} since the last one ({timeSinceThis})")
                .replace("{type}", type.displayName()).replace("{amountSinceThis}", String.valueOf(sinceThis)).replace("{timeSinceThis}", formattedTime));
        // from skyhanni MineshaftCaveInTimer.onMineshaftDetect
        caveInTimerStart = MiningEnv.now();
        firstColdTime = MiningEnv.FAR_PAST;
        lastColdValue = null;
        totalColdGained = 0;
        estimatedMaxTime = null;
        // repo corpse spots for this type until the real ones are found
        waypoints.removeIf(w -> w.kind == Waypoint.Kind.EXPECTED_CORPSE);
        if (Mining.b("mining_mineshaft", "expectedCorpses", true)) for (Vec3 v : expectedCorpses(type)) waypoints.add(new Waypoint(Waypoint.Kind.EXPECTED_CORPSE, v, null));
    }

    // what the key or the click line would tell the party about this shaft, empty = nothing to tell
    private static String typeToSend = "";
    // set by the glue to the keys name, for the click line
    public static String typeKeyName = "the share key";

    // skyhanni sends it on entering, here we only offer it. sending takes a press
    static void offerType(boolean option, String message) {
        typeToSend = "";
        if (!option || !MiningEnv.world.inParty()) return;
        typeToSend = GameState.strip(message);
        MiningEnv.world.clickableChat("Click here or press " + typeKeyName + " to tell your party which mineshaft this is!", "/sbc sharemineshaft", "Click to tell them");
    }

    // the key or the click line
    public static boolean sendType() {
        if (typeToSend.isEmpty() || !MiningEnv.world.inParty()) return false;
        MiningEnv.world.sendCommand("pc " + typeToSend);
        typeToSend = "";
        return true;
    }

    private static boolean tracked(Type type) {
        String list = Mining.text("mining_mineshaft", "mineshaftsToTrack", "");
        for (String s : list.split("[,\\s]+")) if (s.trim().equalsIgnoreCase(type.name()) || s.trim().equalsIgnoreCase(type.rawName)) return true;
        return false;
    }

    // MineshaftCaveInTimer

    public static void onColdUpdate(int cold) {
        if (!MiningApi.inMineshaft()) return;
        Integer prev = lastColdValue;
        lastColdValue = cold;
        if (prev == null) {
            firstColdTime = MiningEnv.now();
            return;
        }
        int delta = cold - prev;
        if (delta > 0) totalColdGained += delta;
        estimatedMaxTime = computeEstimatedMaxTime();
    }

    private static Long computeEstimatedMaxTime() {
        Integer current = lastColdValue;
        if (current == null || MiningEnv.isFarPast(firstColdTime)) return null;
        double elapsed = MiningEnv.passedSince(firstColdTime) / 1000.0;
        if (totalColdGained <= 0 || elapsed <= 0) return null;
        double ratePerSecond = totalColdGained / elapsed;
        return MiningEnv.now() + (long) ((100 - current) / ratePerSecond * 1000);
    }

    // cave in timer lines (skyhanni onSecondPassed), empty outside a shaft
    public static List<String> caveInLines() {
        List<String> out = new ArrayList<>();
        if (!MiningApi.inMineshaft() || MiningEnv.isFarPast(caveInTimerStart)) return out;
        long timeLeft = CAVE_IN_DURATION - MiningEnv.passedSince(caveInTimerStart);
        String color = timeLeft > 30_000 ? "§a" : timeLeft > 10_000 ? "§e" : "§c";
        out.add("§fEntrance caves in: " + color + (timeLeft < 0 ? "Caved in!" : MiningEnv.format(timeLeft)));
        if (Mining.b("mining_mineshaft", "showTimeInMineshaft", true)) out.add("§fTime in mineshaft: §e" + MiningEnv.format(MiningEnv.passedSince(caveInTimerStart)));
        if (Mining.b("mining_mineshaft", "showEstimatedTimeLeft", true)) {
            Long est = estimatedMaxTime;
            out.add("§fEstimated time left: §e" + (est == null ? "§7Calculating..." : MiningEnv.format(Math.max(0, est - MiningEnv.now()))));
        }
        return out;
    }

    // time till the entrance caves in, negative once it has
    static long caveInLeft() {
        return CAVE_IN_DURATION - MiningEnv.passedSince(caveInTimerStart);
    }

    static long timeInMineshaft() {
        return MiningEnv.passedSince(caveInTimerStart);
    }

    // cold rate guess of the time left, null while still working it out
    static Long estimatedLeft() {
        Long est = estimatedMaxTime;
        return est == null ? null : est - MiningEnv.now();
    }

    // MineshaftPityDisplay (tab part)

    // plain tab lines, the pity line is in the glacite tunnels widget
    public static void onTab(List<String> tab) {
        for (String l : tab) {
            Matcher m = TAB_PITY.matcher(l);
            if (!m.matches()) continue;
            everFoundPityWidget = true;
            tablistPity = (int) MiningEnv.parseLong(m.group("pity"));
            return;
        }
    }

    // a mineshaft spawned (MiningNotifications.mineshaftSpawn seen in chat)
    public static void onMineshaftSpawn() {
        MineshaftPity.onSpawn();
        lastMineshaftSpawn = MiningEnv.now();
        sessionMineshafts++;
        MiningEnv.profile().mineshaftTotalCount++;
    }

    public static List<String> pityLines() {
        List<String> out = new ArrayList<>();
        if (!MiningApi.inGlacialTunnels()) return out;
        if (!everFoundPityWidget) {
            out.add("§cPity Tab Widget Missing");
            out.add("§cDo /tab and enable the pity widget");
            return out;
        }
        out.add("§3Pity Counter: §e" + tablistPity + "§6/§e2000");
        if (!MiningEnv.isFarPast(lastMineshaftSpawn)) out.add("§3Last Mineshaft: §e" + MiningEnv.format(MiningEnv.passedSince(lastMineshaftSpawn)));
        out.add("§3Mineshafts total: §e" + MiningEnv.profile().mineshaftTotalCount);
        out.add("§3Mineshafts this session: §e" + sessionMineshafts);
        return out;
    }

    // MineshaftWaypointManager

    // skyhanni IslandJoinEvent(MINESHAFT): block below the player + the way they face
    public static void onIslandJoin(Vec3 blockBelow, Vec3 facing) {
        addEntranceWaypoints(blockBelow, facing);
    }

    private static void addEntranceWaypoints(Vec3 entrance, Vec3 d) {
        waypoints.removeIf(w -> w.kind == Waypoint.Kind.ENTRANCE || w.kind == Waypoint.Kind.LADDER);
        Vec3 ladder = entrance
                // 7 blocks in front of the player = in the ladder shaft
                .add(d.x * LADDER_BLOCKS_FORWARD, 0, d.z * LADDER_BLOCKS_FORWARD)
                // 2 blocks right = middle of the ladder shaft
                .add(d.z * -2, 0, d.x * 2)
                // 15 down = bottom of the ladder shaft
                .add(0, LADDER_BLOCKS_DOWN, 0);
        waypoints.add(new Waypoint(Waypoint.Kind.ENTRANCE, entrance, null));
        waypoints.add(new Waypoint(Waypoint.Kind.LADDER, ladder, null));
    }

    // skyhanni WidgetUpdateEvent(FROZEN_CORPSES): how many corpses the shaft has
    public static void onCorpseWidget(List<String> lines) {
        int n = 0;
        for (String l : lines) if (TAB_CORPSE.matcher(l).matches()) n++;
        totalCorpseCount = n;
    }

    // skyhanni CorpseFinder: one corpse stand per tick + whether its visible. 10 visible ticks in a row (or a
    // click) = found
    public static void onCorpseSeen(int entityId, Corpse type, Vec3 location, boolean visible, boolean clicked) {
        int[] ticks = corpseSeenTicks.computeIfAbsent(entityId, k -> new int[]{0});
        if (ticks[0] >= MARK_AS_FOUND_TICKS_THRESHOLD) return;
        if (clicked) ticks[0] = MARK_AS_FOUND_TICKS_THRESHOLD;
        else if (!visible) {
            ticks[0] = 0;
            return;
        } else ticks[0]++;
        if (ticks[0] >= MARK_AS_FOUND_TICKS_THRESHOLD) onCorpseFound(type, location.add(0, 1, 0));
    }

    static boolean allCorpsesFound() {
        return areAllCorpsesFound() && Mining.b("mining_mineshaft", "foundCorpse", true);
    }

    private static boolean areAllCorpsesFound() {
        if (totalCorpseCount <= 0 || totalCorpseCount != corpseSeenTicks.size()) return false;
        for (int[] t : corpseSeenTicks.values()) if (t[0] < MARK_AS_FOUND_TICKS_THRESHOLD) return false;
        return true;
    }

    // from skyhanni CorpseFoundEvent
    static void onCorpseFound(Corpse type, Vec3 location) {
        waypoints.add(new Waypoint(Waypoint.Kind.FOUND_CORPSE, location, type));
        waypoints.removeIf(w -> w.kind == Waypoint.Kind.EXPECTED_CORPSE && w.location.distanceTo(location) <= 5);
        if (Mining.b("mining_mineshaft", "foundCorpse", true)) {
            String article = type == Corpse.UMBER ? "an" : "a";
            MiningEnv.world.chat("Found " + article + " " + type.displayName() + " Corpse§e at " + (int) location.x + " " + (int) location.y + " " + (int) location.z + ".");
        }
        if (areAllCorpsesFound()) waypoints.removeIf(w -> w.kind == Waypoint.Kind.EXPECTED_CORPSE);
    }

    // whoever else wants to know a corpse got looted, the corpse profit tracker
    public interface LootListener {
        void looted(Corpse type, List<String> items, boolean keyConsumed);
    }

    public static LootListener lootListener;

    // skyhanni CorpseLootedEvent: the corpse within 5 blocks got looted
    private static void onCorpseLooted(Corpse type, List<String> items, boolean keyConsumed) {
        if (lootListener != null) lootListener.looted(type, items, keyConsumed);
        lastLoot.clear();
        lastLoot.addAll(items);
        Vec3 player = MiningEnv.world.playerPos();
        Waypoint closest = null;
        double best = Double.MAX_VALUE;
        for (Waypoint w : waypoints) {
            if (!w.isCorpse()) continue;
            double d = w.location.distanceTo(player);
            if (d <= 5 && d < best) {
                best = d;
                closest = w;
            }
        }
        if (closest != null) closest.kind = Waypoint.Kind.LOOTED_CORPSE;
    }

    // share key or click line: nearest waypoint within 5 blocks goes to party (or all) chat
    public static boolean shareNearest() {
        if (MiningEnv.passedSince(timeLastShared) < 500) return false;
        Waypoint closest = nearest(false);
        if (closest == null) return false;
        timeLastShared = MiningEnv.now();
        Vec3 l = closest.location;
        String message = "x: " + (int) l.x + ", y: " + (int) l.y + ", z: " + (int) l.z + " | (" + closest.label() + ")";
        MiningEnv.world.sendCommand((MiningEnv.world.inParty() ? "pc " : "ac ") + message);
        closest.isShared = true;
        return true;
    }

    // set by the glue to the keys name, for the click line
    public static String corpseKeyName = "the share key";

    private static Waypoint nearest(boolean onlyUnsharedCorpses) {
        Vec3 player = MiningEnv.world.playerPos();
        Waypoint closest = null;
        double best = Double.MAX_VALUE;
        for (Waypoint w : waypoints) {
            if (w.kind == Waypoint.Kind.EXPECTED_CORPSE) continue;
            if (onlyUnsharedCorpses) {
                if (!w.isCorpse() || w.isShared) continue;
                boolean alreadyShared = false;
                for (Vec3 s : sharedWaypoints) if (w.location.distanceTo(s) <= 5) alreadyShared = true;
                if (alreadyShared) continue;
            }
            double d = w.location.distanceTo(player);
            if (d <= 5 && d < best) {
                best = d;
                closest = w;
            }
        }
        return closest;
    }

    // skyhanni CorpseSharing.onSecondPassed sends by itself. here the option only offers it, once per corpse,
    // sending takes a press
    public static void onSecondPassed() {
        if (!MiningApi.inMineshaft()) return;
        if (!Mining.b("mining_mineshaft", "autoShareCorpses", false)) return;
        if (waypoints.isEmpty() || !MiningEnv.world.inParty()) return;
        Waypoint w = nearest(true);
        if (w == null || w.offered) return;
        w.offered = true;
        MiningEnv.world.clickableChat("Click here or press " + corpseKeyName + " to share this " + w.label() + " with your party!", "/sbc sharecorpse", "Click to share");
    }

    // formatted chat in the shaft: corpse loot blocks + shared coords
    public static void onChat(String message, String plain) {
        if (!MiningApi.inMineshaft()) return;
        Matcher m = LOOT_START.matcher(message);
        if (m.matches()) {
            inLoot = true;
            try {
                yoinkCorpse = Corpse.valueOf(m.group("name"));
            } catch (IllegalArgumentException e) {
                yoinkCorpse = null;
            }
            return;
        }
        if (RESOURCEFUL.matcher(plain).matches()) {
            pendingKeyConsumed = false;
            return;
        }
        if (inLoot) {
            if (LOOT_END.matcher(message).matches()) {
                if (yoinkCorpse != null) {
                    onCorpseLooted(yoinkCorpse, new ArrayList<>(loot), pendingKeyConsumed);
                    pendingKeyConsumed = true;
                }
                yoinkCorpse = null;
                loot.clear();
                inLoot = false;
                return;
            }
            Matcher i = LOOT_ITEM.matcher(message);
            if (i.matches()) loot.add(GameState.strip(i.group("item")).trim());
            return;
        }
        // skyhanni CorpseSharing: party and all chat from other players
        Matcher p = PARTY_CHAT.matcher(message);
        if (!p.matches()) p = GLOBAL_CHAT.matcher(message);
        if (!p.matches()) return;
        String author = GameState.strip(p.group("author"));
        if (author.contains(MiningEnv.world.playerName())) return;
        Matcher c = CORPSE_COORDS.matcher(GameState.strip(p.group("message")));
        if (!c.matches()) return;
        Vec3 location = new Vec3(Integer.parseInt(c.group("x")), Integer.parseInt(c.group("y")), Integer.parseInt(c.group("z")));
        for (Vec3 s : sharedWaypoints) if (s.distanceTo(location) <= 5) return;
        sharedWaypoints.add(location);
    }

    public static List<Vec3> sharedWaypoints() {
        return sharedWaypoints;
    }

    // from skyhanni MineshaftWaypointManager.onRenderWorld
    public static void render(WorldDraw w) {
        if (!MiningApi.inMineshaft()) return;
        for (Waypoint p : new ArrayList<>(waypoints)) {
            boolean show = switch (p.kind) {
                case ENTRANCE -> Mining.b("mining_mineshaft", "entrance", true);
                case LADDER -> Mining.b("mining_mineshaft", "ladder", true);
                case FOUND_CORPSE -> Mining.b("mining_mineshaft", "foundCorpse", true);
                case LOOTED_CORPSE -> Mining.b("mining_mineshaft", "lootedCorpse", true);
                case EXPECTED_CORPSE -> Mining.b("mining_mineshaft", "expectedCorpses", true);
            };
            if (!show) continue;
            w.waypointBox(p.location, p.fillColor(), true, true, 0);
            w.label(p.text(), new Vec3(Math.floor(p.location.x) + 0.5, Math.floor(p.location.y) + 1.5, Math.floor(p.location.z) + 0.5), true);
            w.labelBelow("§e" + Math.round(p.location.distanceTo(w.eye)) + " blocks", new Vec3(Math.floor(p.location.x) + 0.5, Math.floor(p.location.y) + 1.5, Math.floor(p.location.z) + 0.5));
        }
        for (Vec3 s : sharedWaypoints) {
            w.waypoint(s, 0xFF55FFFF, "§bShared corpse", true, true);
        }
    }

    public static void onWorldChange() {
        found = false;
        current = null;
        caveInTimerStart = firstColdTime = MiningEnv.FAR_PAST;
        lastColdValue = null;
        totalColdGained = 0;
        estimatedMaxTime = null;
        waypoints.clear();
        corpseSeenTicks.clear();
        totalCorpseCount = 0;
        sharedWaypoints.clear();
        inLoot = false;
        yoinkCorpse = null;
        loot.clear();
    }

    public static void onIslandChange() {
        pendingKeyConsumed = true;
    }

    public static void reset() {
        onWorldChange();
        tablistPity = 2000;
        everFoundPityWidget = false;
        lastMineshaftSpawn = MiningEnv.FAR_PAST;
        sessionMineshafts = 0;
        lastLoot.clear();
        timeLastShared = MiningEnv.FAR_PAST;
        pendingKeyConsumed = true;
    }
}
