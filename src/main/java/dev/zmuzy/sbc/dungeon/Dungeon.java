// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker, Secret Routes; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// dungeon rooms. the floor is a 6x6 grid of 32 block cells, every room is a fixed layout that can be turned 4
// ways. each known room has a sample of its blocks, standing in a cell we test which layout + turn fits
public final class Dungeon {
    private Dungeon() {}

    public record Secret(String name, String category, int x, int y, int z) {}

    public record Def(String id, String name, String kind, int[][] cells, int[] samples, List<Secret> secrets) {}

    // a room on the floor: layout, turn, corner, which cells it covers
    public static final class Placed {
        public final Def def;
        public final int dir, cx, cz;
        public final Set<Long> cells = new HashSet<>();
        public final Set<Integer> done = new HashSet<>();
        // secret route step youre on
        public int step;

        Placed(Def def, int dir, int cx, int cz) {
            this.def = def;
            this.dir = dir;
            this.cx = cx;
            this.cz = cz;
        }

        public Vec3 toActual(double rx, double y, double rz) {
            return actual(dir, cx, cz, rx, y, rz);
        }

        // rooms own x, z for a world block (opposite of toActual)
        public int[] toRelative(int x, int z) {
            return switch (dir) {
                case 0 -> new int[]{x - cx, z - cz};
                case 1 -> new int[]{z - cz, cx - x};
                case 2 -> new int[]{cz - z, x - cx};
                default -> new int[]{cx - x, cz - z};
            };
        }
    }

    // first cell origin is -200, 6 cells of 32 blocks (31 room + 1 door) end at -8
    public static final int ORIGIN = -200, CELLS = 6;

    private static final Map<String, Integer> BLOCK_ID = new HashMap<>();
    private static List<Def> defs;
    private static final Map<Long, Placed> placed = new HashMap<>();
    private static final List<Placed> list = new ArrayList<>();
    private static long lastTry;
    private static int tries;
    public static String status = "";

    static {
        String[] names = {"stone", "diorite", "polished_diorite", "andesite", "polished_andesite", "grass_block", "dirt", "coarse_dirt", "cobblestone", "bedrock",
                "oak_leaves", "gray_wool", "double_stone_slab", "mossy_cobblestone", "clay", "stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks",
                "gray_terracotta", "cyan_terracotta", "black_terracotta"};
        for (int i = 0; i < names.length; i++) BLOCK_ID.put("minecraft:" + names[i], i + 1);
    }

    // ---- geometry ----

    static Vec3 actual(int dir, int cx, int cz, double rx, double y, double rz) {
        return switch (dir) {
            case 0 -> new Vec3(rx + cx, y, rz + cz);
            case 1 -> new Vec3(cx - rz, y, cz + rx);
            case 2 -> new Vec3(cx + rz, y, cz - rx);
            default -> new Vec3(cx - rx, y, cz - rz);
        };
    }

    public static int cellOrigin(double v) {
        return (int) Math.floor((v + 8.5) / 32.0) * 32 - 8;
    }

    private static long key(int ox, int oz) {
        return ((long) ox << 32) ^ (oz & 0xffffffffL);
    }

    public static int index(int origin) {
        return (origin - ORIGIN) / 32;
    }

    // ---- data ----

    private static List<Def> defs() {
        if (defs != null) return defs;
        List<Def> out = new ArrayList<>();
        try (var in = Dungeon.class.getResourceAsStream("/sbc/dungeon_rooms.json")) {
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                JsonArray cs = o.getAsJsonArray("cells");
                int[][] cells = new int[cs.size()][2];
                for (int i = 0; i < cs.size(); i++) {
                    cells[i][0] = cs.get(i).getAsJsonArray().get(0).getAsInt();
                    cells[i][1] = cs.get(i).getAsJsonArray().get(1).getAsInt();
                }
                JsonArray sa = o.getAsJsonArray("samples");
                int[] samples = new int[sa.size()];
                for (int i = 0; i < samples.length; i++) samples[i] = (int) sa.get(i).getAsLong();
                List<Secret> secrets = new ArrayList<>();
                for (JsonElement se : o.getAsJsonArray("secrets")) {
                    JsonArray s = se.getAsJsonArray();
                    secrets.add(new Secret(s.get(0).getAsString(), s.get(1).getAsString(), s.get(2).getAsInt(), s.get(3).getAsInt(), s.get(4).getAsInt()));
                }
                out.add(new Def(o.get("id").getAsString(), o.get("name").getAsString(), o.get("kind").getAsString(), cells, samples, secrets));
            }
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read dungeon rooms", ex);
        }
        defs = out;
        return out;
    }

    // ---- matching ----

    private static int blockId(Minecraft mc, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (!mc.level.hasChunkAt(p)) return -1;
        Block b = mc.level.getBlockState(p).getBlock();
        return BLOCK_ID.getOrDefault(BuiltInRegistries.BLOCK.getKey(b).toString(), 0);
    }

    // cells a layout covers turned dir with its corner at cx, cz. null = leaves the floor
    private static Set<Long> footprint(Def d, int dir, int cx, int cz) {
        Set<Long> out = new HashSet<>();
        for (int[] c : d.cells()) {
            Vec3 v = actual(dir, cx, cz, c[0] * 32 + 15, 0, c[1] * 32 + 15);
            int ox = cellOrigin(v.x), oz = cellOrigin(v.z);
            if (index(ox) < 0 || index(ox) >= CELLS || index(oz) < 0 || index(oz) >= CELLS) return null;
            out.add(key(ox, oz));
        }
        return out;
    }

    private static Placed match(Minecraft mc, int px, int pz) {
        long here = key(px, pz);
        Placed best = null;
        int bestBad = Integer.MAX_VALUE;
        boolean tie = false;
        for (Def d : defs()) {
            for (int dir = 0; dir < 4; dir++) {
                for (int ix = 0; ix < CELLS; ix++) {
                    for (int iz = 0; iz < CELLS; iz++) {
                        int ox = ORIGIN + ix * 32, oz = ORIGIN + iz * 32;
                        int cx = dir == 1 || dir == 3 ? ox + 30 : ox, cz = dir >= 2 ? oz + 30 : oz;
                        Set<Long> fp = footprint(d, dir, cx, cz);
                        if (fp == null || !fp.contains(here)) continue;
                        int bad = 0, seen = 0;
                        for (int s : d.samples()) {
                            Vec3 v = actual(dir, cx, cz, (s >>> 24) & 0xff, (s >>> 16) & 0xff, (s >>> 8) & 0xff);
                            int id = blockId(mc, (int) Math.floor(v.x), (int) v.y, (int) Math.floor(v.z));
                            if (id < 0) continue;
                            seen++;
                            if (id != (s & 0xff) && ++bad > 4) break;
                        }
                        if (seen < 24 || bad > 4) continue;
                        if (bad < bestBad) {
                            bestBad = bad;
                            tie = false;
                            best = new Placed(d, dir, cx, cz);
                            best.cells.addAll(fp);
                        } else if (bad == bestBad && best != null && !best.def.id().equals(d.id())) tie = true;
                    }
                }
            }
        }
        return tie && bestBad > 0 ? null : best;
    }

    // ---- per tick + per frame ----

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!"CATACOMBS".equals(GameState.island)) {
            if (!placed.isEmpty()) {
                placed.clear();
                list.clear();
                tries = 0;
            }
            return;
        }
        if ("CATACOMBS".equals(GameState.island) && Features.on("dungeon_routes")) {
            Placed cur = current();
            if (cur != null) Routes.advance(cur, mc.player.position());
        }
        if (mc.player.tickCount % 10 != 0) return;
        int ox = cellOrigin(mc.player.getX()), oz = cellOrigin(mc.player.getZ());
        if (index(ox) < 0 || index(ox) >= CELLS || index(oz) < 0 || index(oz) >= CELLS) return;
        if (placed.containsKey(key(ox, oz))) {
            status = "";
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTry < 1500) return;
        lastTry = now;
        Placed p = match(mc, ox, oz);
        if (p == null) {
            status = "Looking for this room...";
            return;
        }
        status = "";
        list.add(p);
        for (long c : p.cells) placed.put(c, p);
    }

    // for DungeonPack: this runs rooms go with it
    static void leave() {
        placed.clear();
        list.clear();
        tries = 0;
    }

    public static Placed current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return placed.get(key(cellOrigin(mc.player.getX()), cellOrigin(mc.player.getZ())));
    }

    public static List<Placed> rooms() {
        return list;
    }

    private static int color(String category) {
        return switch (category) {
            case "chest" -> 0xFFEF9F27;
            case "item" -> 0xFF39D0FF;
            case "bat" -> 0xFF97C459;
            case "wither" -> 0xFF222222;
            case "lever" -> 0xFFF2F2F2;
            case "superboom", "stonk" -> 0xFFFF6B6B;
            case "entrance" -> 0xFF8B8478;
            case "fairysoul" -> 0xFFFF7ACD;
            case "key" -> 0xFFFFD36B;
            default -> 0xFFB070FF;
        };
    }

    private static String pretty(String category) {
        return switch (category) {
            case "wither" -> "Wither essence";
            case "superboom" -> "Superboom";
            case "stonk" -> "Stonk";
            case "aotv" -> "Teleport";
            case "fairysoul" -> "Fairy soul";
            default -> category.isEmpty() ? "Secret" : Character.toUpperCase(category.charAt(0)) + category.substring(1);
        };
    }

    // from skyblocker SecretWaypoint.Category.isEnabled: a switch per secret kind
    private static boolean shows(String category) {
        String key = switch (category) {
            case "chest" -> "chest";
            case "item" -> "item";
            case "bat" -> "bat";
            case "wither" -> "wither";
            case "lever" -> "lever";
            case "superboom" -> "superboom";
            case "stonk" -> "stonk";
            case "aotv" -> "aotv";
            case "pearl" -> "pearl";
            case "prince" -> "prince";
            case "fairysoul" -> "fairysoul";
            case "key" -> "key";
            case "entrance" -> "entrance";
            default -> "other";
        };
        return DungeonMarks.opt("dungeon_rooms", key);
    }

    // from skyblocker SecretWaypoint.getRenderBox: essence + keys can take their blocks size
    private static AABB secretBox(WorldDraw w, Secret s, Vec3 at) {
        AABB full = new AABB(at.x, at.y, at.z, at.x + 1, at.y + 1, at.z + 1);
        if (!DungeonMarks.opt("dungeon_rooms", "adaptive") || (!s.category().equals("wither") && !s.category().equals("key"))) return full;
        BlockPos p = BlockPos.containing(at);
        var shape = w.mc.level.getBlockState(p).getShape(w.mc.level, p);
        return shape.isEmpty() ? full : shape.bounds().move(p);
    }

    public static void drawSecrets(WorldDraw w) {
        Placed r = current();
        if (r == null) return;
        if (Features.on("dungeon_routes") && Routes.draw(w, r)) return;
        Vec3 me = w.mc.player.position();
        int idx = 0;
        for (Secret s : r.def.secrets()) {
            idx++;
            if (r.done.contains(idx) || !shows(s.category())) continue;
            Vec3 c = r.toActual(s.x(), s.y(), s.z());
            Vec3 at = new Vec3(Math.floor(c.x), Math.floor(c.y), Math.floor(c.z));
            double d = at.add(0.5, 0.5, 0.5).distanceTo(me);
            if (d < 2.8) {
                r.done.add(idx);
                continue;
            }
            if (!w.visible(at)) continue;
            int col = color(s.category());
            DungeonMarks.draw(w, secretBox(w, s, at), col, DungeonMarks.choice("dungeon_rooms", "look", "Filled and outline"), true);
            if (DungeonMarks.opt("dungeon_rooms", "text")) w.label(s.name().isEmpty() ? pretty(s.category()) : s.name(), at.add(0.5, 1.4, 0.5), true);
        }
    }

    // ---- goldor (f7 boss): devices + terminals ----

    public static void drawTerminals(WorldDraw w) {
        if ("CATACOMBS".equals(GameState.island)) Goldor.draw(w);
    }

    public static void register() {
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Features.add(new Feature("dungeon_rooms", "Dungeons", "Room detection and secrets", "Recognises the room you are in and marks its secrets in the world (chests, items, bats, levers, wither essence); a secret disappears when you reach it.", "Skyblocker: dungeon secrets")
                .when(inDungeon).tick(Dungeon::tick).world(Dungeon::drawSecrets)
                .option(Option.choice("dungeon_rooms.look", "Look", "How a secret is drawn.", "Filled and outline", DungeonMarks.LOOKS))
                .option(Option.bool("dungeon_rooms.text", "Say what it is", "The name of the secret above its mark.", true))
                .option(Option.bool("dungeon_rooms.adaptive", "Fit small blocks", "Essence and keys are marked at the size of their block.", false))
                .option(Option.bool("dungeon_rooms.chest", "Chests", "Mark chests.", true))
                .option(Option.bool("dungeon_rooms.item", "Items", "Mark items to pick up.", true))
                .option(Option.bool("dungeon_rooms.bat", "Bats", "Mark where bats are.", true))
                .option(Option.bool("dungeon_rooms.wither", "Wither essence", "Mark wither essence.", true))
                .option(Option.bool("dungeon_rooms.lever", "Levers", "Mark levers.", true))
                .option(Option.bool("dungeon_rooms.superboom", "Superboom spots", "Mark what a Superboom opens.", true))
                .option(Option.bool("dungeon_rooms.stonk", "Stonk spots", "Mark what a pickaxe opens.", true))
                .option(Option.bool("dungeon_rooms.aotv", "Teleport spots", "Mark where to teleport to.", true))
                .option(Option.bool("dungeon_rooms.pearl", "Pearl spots", "Mark where to throw a pearl.", true))
                .option(Option.bool("dungeon_rooms.prince", "Princes", "Mark Princes, one bonus point with the Reborn attribute.", true))
                .option(Option.bool("dungeon_rooms.fairysoul", "Fairy souls", "Mark fairy souls.", true))
                .option(Option.bool("dungeon_rooms.key", "Redstone keys", "Mark redstone keys.", true))
                .option(Option.bool("dungeon_rooms.other", "Everything else", "Mark what has no kind of its own.", true))
                .option(Option.bool("dungeon_rooms.entrance", "Entrances", "Mark the way into a secret room too.", false)));
        Features.add(new Feature("dungeon_routes", "Dungeons", "Secret routes", "Leads you secret after secret: the path, the etherwarp, mine, TNT and interact spots, and the secret itself; reaching it moves to the next step. Set with /sbc route set fow|3ppopka|pearl|normal, skip with /sbc route next.", "Secret Routes Mod")
                .when(inDungeon));
        Features.add(new Feature("dungeon_map", "Dungeons", "Room map", "A small map of the floor with every room you have been in, coloured by kind, and where you stand.", "Skyblocker: dungeon map")
                .when(inDungeon)
                .option(Option.bool("dungeon_map.team", "Teammates", "Your teammates on the map, in the colour of their class.", true))
                .option(Option.bool("dungeon_map.secret_counts", "Secrets left", "How many secrets each room you left still has, or a tick.", true)));
        Features.add(new Feature("dungeon_terminals", "Dungeons", "Floor 7 terminals and devices", "Marks the terminals and devices of the Goldor phase when you are close to them.", "Skyblocker: Goldor waypoints")
                .when(inDungeon).world(Dungeon::drawTerminals)
                .option(Option.choice("dungeon_terminals.look", "Look", "How a terminal is drawn.", "Filled and outline", DungeonMarks.LOOKS)));
    }
}
