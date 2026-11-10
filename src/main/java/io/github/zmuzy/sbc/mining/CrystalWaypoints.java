// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.ParticleHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyblocker CrystalsLocationsManager, WishingCompassSolver + the crystal hollows half of
// MiningLocationLabel: where this lobbys places are
// you learn them by walking in, from coords in chat, from what npcs say, and from 2 wishing compass uses
// skyblocker also shares them over its own server, we skip that
public final class CrystalWaypoints {
    static final String ID = "mining_crystals";

    // skyblocker CrystalHollowsLocationsCategory + the first line that says youre standing at it
    public enum Place {
        UNKNOWN("Unknown", 0xFFFFFF, null),
        JUNGLE_TEMPLE("Jungle Temple", 0x8932B8, "[NPC] Kalhuiki Door Guardian:"),
        MINES_OF_DIVAN("Mines of Divan", 0x00FF00, "                                Jade Crystal"),
        GOBLIN_QUEENS_DEN("Goblin Queen's Den", 0xF9801D, "                                Amber Crystal"),
        LOST_PRECURSOR_CITY("Lost Precursor City", 0x00FFFF, "                                Sapphire Crystal"),
        KHAZAD_DUM("Khazad-dûm", 0xFFFF00, "                                Topaz Crystal"),
        FAIRY_GROTTO("Fairy Grotto", 0xFFAFAF, null),
        DRAGONS_LAIR("Dragon's Lair", 0xFFAA00, "[NPC] Golden Dragon:"),
        CORLEONE("Corleone", 0xFFFFFF, null),
        KING_YOLKAR("King Yolkar", 0xFF0000, "[NPC] King Yolkar:"),
        ODAWA("Odawa", 0xFF00FF, "[NPC] Odawa:"),
        KEY_GUARDIAN("Key Guardian", 0xC0C0C0, null),
        XALX("Xalx", 0x00FF00, "[NPC] Xalx:");

        public final String label;
        public final int rgb;
        final String linked;

        Place(String label, int rgb, String linked) {
            this.label = label;
            this.rgb = rgb;
            this.linked = linked;
        }

        static Place byName(String name) {
            for (Place p : values()) if (p.label.equals(name)) return p;
            return null;
        }

        // small places on the map, skyblocker CrystalsHudWidget.SMALL_LOCATIONS
        boolean small() {
            return switch (this) {
                case FAIRY_GROTTO, KING_YOLKAR, CORLEONE, ODAWA, KEY_GUARDIAN, XALX, UNKNOWN -> true;
                default -> false;
            };
        }
    }

    static final Pattern COORDS = Pattern.compile("\\Dx?(\\d{3})(?=[, ]),? ?y?(\\d{2,3})(?=[, ]),? ?z?(\\d{3})\\D?(?!\\d)");
    private static final int REMOVE_UNKNOWN_DISTANCE = 50;
    // whats known in this lobby
    static final Map<Place, Vec3> ACTIVE = new LinkedHashMap<>();
    private static final List<Place> VERIFIED = new ArrayList<>();
    private static long nextArea;
    private static Object level;

    private CrystalWaypoints() {}

    static boolean inHollows(double x, double y, double z) {
        return x >= 202 && x <= 823 && z >= 202 && z <= 823 && y >= 31 && y <= 188;
    }

    // coords in a players message or null, skyblocker extractLocationFromMessage
    static Vec3 coordinates(String userMessage) {
        Matcher m = COORDS.matcher(userMessage);
        if (!m.find()) return null;
        double x = Integer.parseInt(m.group(1)), y = Integer.parseInt(m.group(2)), z = Integer.parseInt(m.group(3));
        return inHollows(x, y, z) ? new Vec3(x, y, z) : null;
    }

    // the place a message names by any word of its name, or null
    static Place named(String userMessage) {
        String low = userMessage.toLowerCase(Locale.ENGLISH);
        for (Place p : Place.values()) {
            for (String word : p.label.toLowerCase(Locale.ENGLISH).split(" ")) if (low.contains(word)) return p;
        }
        return null;
    }

    static void add(Place place, Vec3 pos) {
        Vec3 unknown = ACTIVE.get(Place.UNKNOWN);
        if (unknown != null && unknown.distanceTo(pos) < REMOVE_UNKNOWN_DISTANCE) ACTIVE.remove(Place.UNKNOWN);
        ACTIVE.put(place, pos);
    }

    // places are for this feature and for the map
    private static boolean learning() {
        return Features.on(ID) || Features.on(CrystalCore.MAP);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!"CRYSTAL_HOLLOWS".equals(GameState.island) || !learning()) return ChatBus.PASS;
        if (MiningPack.on(ID, "mining_crystals.chat")) chatCoordinates(line.plain());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ChatBus.PASS;
        for (Place p : Place.values()) {
            if (p.linked != null && line.plain().startsWith(p.linked) && !VERIFIED.contains(p)) {
                add(p, new Vec3(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()));
                VERIFIED.add(p);
            }
        }
        WishingCompass.onChat(line.plain());
        return ChatBus.PASS;
    }

    private static void chatCoordinates(String text) {
        if (!text.contains(":") || text.startsWith("[SBC]")) return;
        String user = text.split(":", 2)[1];
        Vec3 pos = coordinates(user);
        if (pos == null) return;
        Place p = named(user);
        if (p != null) {
            if (!ACTIVE.containsKey(p)) add(p, pos);
            return;
        }
        sendMenu((int) pos.x + " " + (int) pos.y + " " + (int) pos.z);
    }

    private static Component prefix() {
        return Component.literal("§b[SBC] §r");
    }

    // skyblocker getLocationMenu: every place not known yet, to click
    private static void sendMenu(String location) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (ACTIVE.size() == Place.values().length) {
            MiningPack.chat("§cYou have no more waypoints left to create.");
            return;
        }
        MutableComponent text = Component.empty().append(prefix()).append("Mark (" + location + ") as: ");
        for (Place p : Place.values()) {
            if (ACTIVE.containsKey(p)) continue;
            text.append(Component.literal("[" + p.label + "]").withColor(p.rgb).withStyle(st -> st
                    .withClickEvent(new ClickEvent.RunCommand("/sbc crystalwaypoints add " + location + " " + p.label))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Add Location")))));
        }
        mc.player.sendSystemMessage(text);
    }

    // skyblocker getPlacesMenu: what you know, to share or remove
    private static void sendPlaces(String action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (ACTIVE.isEmpty()) {
            MiningPack.chat("§cYou have no active waypoints to share or remove.");
            return;
        }
        String hover = action.equals("remove") ? "Remove Location" : "Share Location";
        MutableComponent text = Component.empty().append(prefix()).append(hover + ": ");
        for (Place p : ACTIVE.keySet()) {
            text.append(Component.literal("[" + p.label + "]").withColor(p.rgb).withStyle(st -> st
                    .withClickEvent(new ClickEvent.RunCommand("/sbc crystalwaypoints " + action + " " + p.label))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))));
        }
        mc.player.sendSystemMessage(text);
    }

    private static void addFromCommand(String args) {
        String[] p = args.split(" ", 4);
        if (p.length < 4) return;
        Place place = Place.byName(p[3]);
        if (place == null) return;
        try {
            add(place, new Vec3(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
        } catch (NumberFormatException e) {
            return;
        }
        MiningPack.chat("Added waypoint for '§" + "e" + place.label + "§r' at " + p[0] + " " + p[1] + " " + p[2] + ".");
    }

    private static void share(String name) {
        Place p = Place.byName(name);
        Vec3 v = p == null ? null : ACTIVE.get(p);
        if (v == null) {
            MiningPack.chat("§cCan only share waypoints you have found.");
            return;
        }
        // only runs bc the player ran the command or clicked the line
        MiningEnv.world.sendCommand("ac " + p.label + ": " + (int) v.x + ", " + (int) v.y + ", " + (int) v.z);
    }

    private static void remove(String name) {
        Place p = Place.byName(name);
        if (p == null || !ACTIVE.containsKey(p)) {
            MiningPack.chat("§cCan only remove waypoints you have found.");
            return;
        }
        ACTIVE.remove(p);
        VERIFIED.remove(p);
        MiningPack.chat("Removed waypoint for " + p.label);
    }

    // runs everywhere bc what it clears belongs to two features and the world they were in
    private static void tick(Minecraft mc) {
        if (mc.level != level) {
            level = mc.level;
            ACTIVE.clear();
            VERIFIED.clear();
            WishingCompass.reset();
        }
        long now = System.currentTimeMillis();
        if (mc.player == null || now < nextArea || !"CRYSTAL_HOLLOWS".equals(GameState.island) || !learning()) return;
        nextArea = now + 2000;
        // skyblocker update(): walking into a place you dont know yet
        Place p = Place.byName(GameState.area);
        if (p != null && p != Place.UNKNOWN && !ACTIVE.containsKey(p)) add(p, new Vec3(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()));
    }

    private static void draw(WorldDraw w) {
        double scale = MiningPack.number(ID, "mining_crystals.text_scale", 100) / 100.0;
        for (var e : ACTIVE.entrySet()) {
            Vec3 at = e.getValue().add(0.5, 0.5, 0.5);
            w.dynamicText(at, "§" + code(e.getKey()) + e.getKey().label, 1.5 * scale, 0);
            w.dynamicText(at, "§e" + Math.round(at.distanceTo(w.eye)) + " blocks", 1.2 * scale, -1);
        }
    }

    private static char code(Place p) {
        return switch (p) {
            case JUNGLE_TEMPLE -> '5';
            case MINES_OF_DIVAN, XALX -> 'a';
            case GOBLIN_QUEENS_DEN, DRAGONS_LAIR -> '6';
            case LOST_PRECURSOR_CITY -> 'b';
            case KHAZAD_DUM -> 'e';
            case FAIRY_GROTTO, ODAWA -> 'd';
            case KING_YOLKAR -> 'c';
            case KEY_GUARDIAN -> '7';
            default -> 'f';
        };
    }

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Crystal Hollows places", "Where the places of this lobby are, with their name and distance: from walking into them, coordinates in chat, what the NPCs say and two uses of a Wishing Compass. /sbc crystalwaypoints adds, shares and removes them.", "Skyblocker: Crystal Hollows waypoints, Wishing Compass Solver")
                .when(MiningPack::hollows).world(CrystalWaypoints::draw)
                .option(Option.bool("mining_crystals.chat", "Find places in chat", "Coordinates someone writes become a waypoint, or a line asking what is there.", true))
                .option(Option.bool("mining_crystals.compass", "Wishing Compass solver", "Use one, walk a bit, use a second: the place gets a waypoint.", true))
                .option(Option.integer("mining_crystals.text_scale", "Text size (%)", "", 100, 50, 300)));
        ChatBus.register(ChatBus.OBSERVE + 22, ID, null, CrystalWaypoints::onChat);
        Safe.endTick("CrystalWaypoints.tick", CrystalWaypoints::tick);
        WishingCompass.hook();
        Safe.commands("CrystalWaypoints.commands", (d, r) -> d.register(literal("sbc").then(literal("crystalwaypoints")
                .then(literal("add").executes(c -> {
                    var p = Minecraft.getInstance().player;
                    if (p != null) sendMenu(p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ());
                    return 1;
                }).then(argument("spot", StringArgumentType.greedyString()).executes(c -> {
                    addFromCommand(StringArgumentType.getString(c, "spot"));
                    return 1;
                })))
                .then(literal("share").executes(c -> {
                    sendPlaces("share");
                    return 1;
                }).then(argument("place", StringArgumentType.greedyString()).executes(c -> {
                    share(StringArgumentType.getString(c, "place"));
                    return 1;
                })))
                .then(literal("remove").executes(c -> {
                    sendPlaces("remove");
                    return 1;
                }).then(argument("place", StringArgumentType.greedyString()).executes(c -> {
                    remove(StringArgumentType.getString(c, "place"));
                    return 1;
                }))))));
        io.github.zmuzy.sbc.core.Commands.describe("crystalwaypoints", "Add, share or remove a Crystal Hollows place");
    }

    // from skyblocker WishingCompassSolver
    static final class WishingCompass {
        private enum State { NOT_STARTED, FIRST, WAITING, SECOND }

        private enum Zone { NUCLEUS, JUNGLE, MITHRIL, GOBLIN, PRECURSOR, MAGMA, NONE }

        private static final Map<Zone, AABB> ZONES = Map.of(
                Zone.NUCLEUS, new AABB(462, 63, 461, 564, 181, 565),
                Zone.JUNGLE, new AABB(201, 63, 201, 513, 189, 513),
                Zone.MITHRIL, new AABB(512, 63, 201, 824, 189, 513),
                Zone.GOBLIN, new AABB(201, 63, 512, 513, 189, 824),
                Zone.PRECURSOR, new AABB(512, 63, 512, 824, 189, 824),
                Zone.MAGMA, new AABB(201, 30, 201, 824, 64, 824));
        private static final Vec3 JUNGLE_TEMPLE_DOOR = new Vec3(-57, 36, -21);
        private static final long PER_LINE = 25, MAX_DELAY = 500, BETWEEN_USES = 8;
        private static final double MAX_DISTANCE = 0.9, TOLERANCE = 5.0;
        private static State state = State.NOT_STARTED;
        private static Vec3 startOne = Vec3.ZERO, startTwo = Vec3.ZERO, dirOne = Vec3.ZERO, dirTwo = Vec3.ZERO, lastPos = Vec3.ZERO;
        private static long countOne, countTwo, lastUpdate;

        private WishingCompass() {}

        static void reset() {
            state = State.NOT_STARTED;
            startOne = startTwo = dirOne = dirTwo = lastPos = Vec3.ZERO;
            countOne = countTwo = 0;
            lastUpdate = System.currentTimeMillis();
        }

        static void onChat(String plain) {
            if (plain.equals("The Wishing Compass can't seem to locate anything!")) state = State.NOT_STARTED;
        }

        private static Zone zone(Vec3 v) {
            // nucleus sits inside the others so check it first
            if (ZONES.get(Zone.NUCLEUS).contains(v)) return Zone.NUCLEUS;
            for (Zone z : new Zone[]{Zone.JUNGLE, Zone.MITHRIL, Zone.GOBLIN, Zone.PRECURSOR, Zone.MAGMA}) if (ZONES.get(z).contains(v)) return z;
            return Zone.NONE;
        }

        private static boolean tabHas(java.util.function.Predicate<String> test) {
            for (String l : GameState.tab) if (test.test(GameState.strip(l).trim())) return true;
            return false;
        }

        private static boolean zoneComplete(Zone z) {
            if (!tabHas("Crystals:"::equals)) {
                MiningPack.chat("§cEnable crystals in the /tab to allow the compass to identify discovered items");
                return false;
            }
            return switch (z) {
                case JUNGLE -> !tabHas("Amethyst: ✖ Not Found"::equals);
                case MITHRIL -> !tabHas("Jade: ✖ Not Found"::equals);
                case GOBLIN -> !tabHas("Amber: ✖ Not Found"::equals);
                case PRECURSOR -> !tabHas("Sapphire: ✖ Not Found"::equals);
                case MAGMA -> !tabHas("Topaz: ✖ Not Found"::equals);
                default -> false;
            };
        }

        private static boolean kingsScent() {
            if (!tabHas(s -> s.startsWith("Active Effects:"))) {
                MiningPack.chat("§cEnable effects in the /tab to allow the compass to identify queen coordinates");
                return false;
            }
            return tabHas(s -> s.startsWith("King's Scent"));
        }

        private static Place target(Zone z) {
            if (zoneComplete(z)) return Place.UNKNOWN;
            return switch (z) {
                case JUNGLE -> MiningPack.inInventory("JUNGLE_KEY") ? Place.JUNGLE_TEMPLE : Place.ODAWA;
                case MITHRIL -> Place.MINES_OF_DIVAN;
                case GOBLIN -> kingsScent() ? Place.GOBLIN_QUEENS_DEN : Place.KING_YOLKAR;
                case PRECURSOR -> Place.LOST_PRECURSOR_CITY;
                case MAGMA -> Place.KHAZAD_DUM;
                default -> Place.UNKNOWN;
            };
        }

        private static void onParticle(ParticleHooks.Particle p) {
            if (!p.is("minecraft:happy_villager") || !"CRYSTAL_HOLLOWS".equals(GameState.island) || state == State.NOT_STARTED || state == State.WAITING) return;
            Vec3 pos = new Vec3(p.x(), p.y(), p.z());
            lastUpdate = System.currentTimeMillis();
            if (pos.distanceTo(lastPos) > MAX_DISTANCE) return;
            lastPos = pos;
            if (state == State.FIRST) {
                dirOne = dirOne.add(pos.subtract(startOne).normalize().scale(1.0 / PER_LINE));
                if (++countOne >= PER_LINE) {
                    state = State.WAITING;
                    MiningPack.chat("§aWishing compass used. Move to another location and use a second compass to triangulate the target");
                }
            } else {
                dirTwo = dirTwo.add(pos.subtract(startTwo).normalize().scale(1.0 / PER_LINE));
                if (++countTwo >= PER_LINE) solveNow();
            }
        }

        private static void solveNow() {
            Vec3 at = solve(startOne, startTwo, dirOne, dirTwo);
            if (at == null) {
                MiningPack.chat("§cThe lines did not intersect (Something went wrong) please try again.");
            } else {
                Zone z = zone(startOne);
                Place place = target(z);
                AABB box = ZONES.get(z);
                if (box == null || !box.inflate(100, 0, 100).contains(at)) {
                    place = Place.UNKNOWN;
                    MiningPack.chat("§cThe target location is too far away to identify the structure, possibly due to a missing structure in the lobby.");
                }
                if (place == Place.JUNGLE_TEMPLE) at = at.add(JUNGLE_TEMPLE_DOOR);
                MiningPack.chat("§aWishing compass solver found " + place.label + ": " + (int) at.x + " " + (int) at.y + " " + (int) at.z);
                add(place, new Vec3(Math.floor(at.x), Math.floor(at.y), Math.floor(at.z)));
            }
            reset();
        }

        // middle of the shortest line between the 2 rays, null if they miss by more than 5 blocks
        static Vec3 solve(Vec3 a, Vec3 b, Vec3 da, Vec3 db) {
            if (da.equals(db)) return null;
            Vec3 r = a.subtract(b);
            double aa = da.dot(da), ab = da.dot(db), bb = db.dot(db), ar = da.dot(r), br = db.dot(r);
            double det = aa * bb - ab * ab;
            if (Math.abs(det) < 1e-9) return null;
            double s = (ab * br - bb * ar) / det, t = (aa * br - ab * ar) / det;
            Vec3 p1 = a.add(da.scale(s)), p2 = b.add(db.scale(t));
            return p1.distanceTo(p2) < TOLERANCE ? p1.add(p2).scale(0.5) : null;
        }

        private static void start(Minecraft mc, State next) {
            Vec3 at = mc.player.position().add(0, 1.62, 0);
            state = next;
            if (next == State.FIRST) startOne = at;
            else startTwo = at;
            lastUpdate = System.currentTimeMillis();
            lastPos = at;
        }

        // skyblocker useCompass: true blocks a use that would just waste a compass
        private static boolean use() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return false;
            Vec3 eye = mc.player.getEyePosition();
            Zone now = zone(eye);
            switch (state) {
                case NOT_STARTED -> {
                    if (now == Zone.NUCLEUS) {
                        MiningPack.chat("§cUse compass outside of nucleus for better results");
                        return true;
                    }
                    start(mc, State.FIRST);
                }
                case WAITING -> {
                    if (startOne.closerThan(eye, BETWEEN_USES)) {
                        MiningPack.chat("§eMove further away from the first use before using again");
                        return true;
                    }
                    if (now != zone(startOne)) {
                        MiningPack.chat("§eChanged zone. Restarting...");
                        reset();
                        start(mc, State.FIRST);
                    } else start(mc, State.SECOND);
                }
                default -> {
                    if (System.currentTimeMillis() - lastUpdate < MAX_DELAY) {
                        MiningPack.chat("§eWait a moment before using another wishing compass");
                        return true;
                    }
                    MiningPack.chat("§eUnable to detect the last use. Restarting...");
                    reset();
                    start(mc, State.FIRST);
                }
            }
            return false;
        }

        static void hook() {
            ParticleHooks.listen(WishingCompass::onParticle);
            Interactions.onItemClick(right -> right && "CRYSTAL_HOLLOWS".equals(GameState.island) && MiningPack.on(ID, "mining_crystals.compass") && "WISHING_COMPASS".equals(MiningPack.heldId()) && use());
        }
    }
}
