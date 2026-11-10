// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

// next door to open + keys on the ground. from skyblocker DungeonManager.getBloodRushDoorPos +
// DungeonMapUtils: door comes from hypixels map in the last hotbar slot, red till you hold a key
public final class DungeonDoors {
    // MapColor.PLANT at HIGH brightness = the entrance on the map
    private static final byte ENTRANCE = 30;
    // 119 = wither door black on the map, 18 = blood door red
    private static final byte WITHER_DOOR = 119, BLOOD_DOOR = 18;
    private static final Pattern KEY_FOUND = Pattern.compile("^RIGHT CLICK on (?:the BLOOD DOOR|a WITHER door) to open it. This key can only be used to open 1 door!$");
    private static final Pattern WITHER_OPENED = Pattern.compile("^\\w+ opened a WITHER door!$");
    private static final String BLOOD_OPENED = "The BLOOD DOOR has been opened!";

    private static int[] physicalEntrance, mapEntrance;
    private static int mapRoomSize;
    private static AABB door;
    private static boolean bloodOpened, hasKey;
    private static MapId mapId;
    private static String witherKey, bloodKey;

    private DungeonDoors() {}

    static boolean hasKey() {
        return hasKey;
    }

    static boolean bloodOpened() {
        return bloodOpened;
    }

    // from skyblocker DungeonMapUtils.getPhysicalRoomPos
    static int[] physicalRoom(double x, double z) {
        int px = (int) (x + 8.5), pz = (int) (z + 8.5);
        return new int[]{px - Math.floorMod(px, 32) - 8, pz - Math.floorMod(pz, 32) - 8};
    }

    private static byte color(byte[] colors, int x, int z) {
        return x < 0 || z < 0 || x >= 128 || z >= 128 ? -1 : colors[x + (z << 7)];
    }

    // from skyblocker getMapEntrancePosAndRoomSizeAt + getMapRoomSize: x, z, size. size 0 = no room
    static int[] entranceAt(byte[] colors, int x, int z) {
        while (color(colors, x - 1, z) == ENTRANCE) x--;
        while (color(colors, x, z - 1) == ENTRANCE) z--;
        int i = -1;
        do i++; while (color(colors, x + i, z) == ENTRANCE);
        return new int[]{x, z, i > 5 ? i : 0};
    }

    // from skyblocker getMapEntrancePosAndRoomSize: from your map mark in steps of 10 to the entrance green
    static int[] findEntrance(byte[] colors, int startX, int startZ) {
        Queue<int[]> check = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        check.add(new int[]{startX, startZ});
        seen.add((long) startX << 32 | (startZ & 0xffffffffL));
        int[][] steps = {{-10, 0}, {0, -10}, {10, 0}, {0, 10}};
        for (int[] p; (p = check.poll()) != null; ) {
            if (color(colors, p[0], p[1]) == ENTRANCE) {
                int[] e = entranceAt(colors, p[0], p[1]);
                if (e[2] > 0) return e;
            }
            for (int[] s : steps) {
                int x = p[0] + s[0], z = p[1] + s[1];
                if (x < -128 || z < -128 || x > 256 || z > 256) continue;
                if (seen.add((long) x << 32 | (z & 0xffffffffL))) check.add(new int[]{x, z});
            }
        }
        return null;
    }

    // from skyblocker getPhysicalPosFromMap
    private static int[] physicalFromMap(int[] mapEntrance, int size, int[] physicalEntrance, int x, int z) {
        return new int[]{(x - mapEntrance[0]) / (size + 4) * 32 + physicalEntrance[0], (z - mapEntrance[1]) / (size + 4) * 32 + physicalEntrance[1]};
    }

    // from skyblocker getBloodRushDoorPos: box of the first door that matters, or null
    static int[] doorBox(byte[] colors, int[] mapEntrance, int size, int[] physicalEntrance) {
        int nwX = Math.floorMod(mapEntrance[0], size + 4), nwZ = Math.floorMod(mapEntrance[1], size + 4);
        for (int x = nwX + size / 2; x < 128; x += size + 4) {
            for (int z = nwZ + size; z < 128; z += size + 4) {
                byte c = color(colors, x, z);
                if (c == WITHER_DOOR || c == BLOOD_DOOR) {
                    int[] d = physicalFromMap(mapEntrance, size, physicalEntrance, x - size / 2, z - size);
                    return new int[]{d[0] + 14, 69, d[1] + 30, d[0] + 17, 73, d[1] + 33};
                }
            }
        }
        for (int x = nwX + size; x < 128; x += size + 4) {
            for (int z = nwZ + size / 2; z < 128; z += size + 4) {
                byte c = color(colors, x, z);
                if (c == WITHER_DOOR || c == BLOOD_DOOR) {
                    int[] d = physicalFromMap(mapEntrance, size, physicalEntrance, x - size, z - size / 2);
                    return new int[]{d[0] + 30, 69, d[1] + 14, d[0] + 33, 73, d[1] + 17};
                }
            }
        }
        return null;
    }

    private static void reset() {
        physicalEntrance = mapEntrance = null;
        mapRoomSize = 0;
        door = null;
        bloodOpened = hasKey = false;
        mapId = null;
    }

    // from skyblocker DungeonMap.getMapIdComponent: map in the last hotbar slot, or the last one seen while
    // something else is there
    static net.minecraft.world.level.saveddata.maps.MapId mapId() {
        return mapId;
    }

    static MapItemSavedData map(Minecraft mc) {
        ItemStack st = mc.player.getInventory().getNonEquipmentItems().get(8);
        if (st.is(Items.FILLED_MAP) && st.has(DataComponents.MAP_ID)) mapId = st.get(DataComponents.MAP_ID);
        return mapId == null ? null : MapItem.getSavedData(mapId, mc.level);
    }

    private static void tick(Minecraft mc) {
        // from skyblocker onChatMessage: the score line + the bosses first line reset it, no run = no next door
        if (DungeonRun.inBoss() || DungeonRun.completed()) {
            if (physicalEntrance != null || door != null) reset();
            return;
        }
        if (!DungeonRun.inDungeon() || mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0) return;
        if (physicalEntrance == null) {
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof ArmorStand st && st.hasCustomName() && st.getCustomName() != null && st.getCustomName().getString().contains("Mort")) {
                    physicalEntrance = physicalRoom(st.getX(), st.getZ());
                    break;
                }
            }
            if (physicalEntrance == null) return;
        }
        MapItemSavedData map = map(mc);
        if (map == null) return;
        if (mapEntrance == null || mapRoomSize == 0) {
            for (MapDecoration d : map.getDecorations()) {
                if (!d.type().value().equals(MapDecorationTypes.FRAME.value())) continue;
                int[] e = findEntrance(map.colors, (d.x() >> 1) + 64, (d.y() >> 1) + 64);
                if (e != null) {
                    mapEntrance = new int[]{e[0], e[1]};
                    mapRoomSize = e[2];
                }
                break;
            }
            if (mapEntrance == null) return;
        }
        int[] b = doorBox(map.colors, mapEntrance, mapRoomSize, physicalEntrance);
        if (b != null) door = new AABB(b[0], b[1], b[2], b[3], b[4], b[5]);
    }

    // from skyblocker DungeonManager.onChatMessage, the keys part
    static void feed(String plain) {
        String t = plain.trim();
        if (bloodOpened) return;
        if (t.equals(BLOOD_OPENED)) bloodOpened = true;
        if (KEY_FOUND.matcher(t).matches()) hasKey = true;
        if (WITHER_OPENED.matcher(t).matches()) hasKey = false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (DungeonRun.inDungeon()) feed(line.plain());
        return ChatBus.PASS;
    }

    private static void loadHeads() {
        if (witherKey != null) return;
        witherKey = bloodKey = "";
        try (var in = DungeonDoors.class.getResourceAsStream("/sbc/dungeon_heads.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            witherKey = o.get("wither_key").getAsString();
            bloodKey = o.get("blood_key").getAsString();
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read dungeon heads", e);
        }
    }

    private static void drawDoor(WorldDraw w) {
        if (door == null || bloodOpened) return;
        int c = hasKey ? 0xFF00FF00 : 0xFFFF0000;
        DungeonMarks.draw(w, door, c, DungeonMarks.choice("dungeon_doors", "look", "Outline"), DungeonMarks.opt("dungeon_doors", "through_walls"));
        Vec3 at = door.getCenter();
        if (w.visible(at)) w.label((hasKey ? "§aDoor: you have the key" : "§cDoor: find the key") + "  §7" + (int) at.distanceTo(w.mc.player.position()) + "m", at.add(0, 3, 0), DungeonMarks.opt("dungeon_doors", "through_walls"));
    }

    private static void drawKeys(WorldDraw w) {
        loadHeads();
        boolean walls = false;
        Vec3 me = w.mc.player.position();
        for (var e : w.mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand st) || !st.hasItemInSlot(EquipmentSlot.HEAD)) continue;
            String t = DungeonMarks.headTexture(st.getItemBySlot(EquipmentSlot.HEAD));
            boolean wither = t.equals(witherKey);
            if (!wither && !t.equals(bloodKey)) continue;
            Vec3 at = st.position().add(0, 1.6, 0);
            w.box(new AABB(at.x - 0.4, at.y, at.z - 0.4, at.x + 0.4, at.y + 0.8, at.z + 0.4), 0xFF00AAAA, true, walls);
            w.label((wither ? "§8Wither Key" : "§cBlood Key") + "  §7" + (int) st.position().distanceTo(me) + "m", at.add(0, 1.2, 0), walls);
        }
    }

    public static void register() {
        Feature doors = Features.add(new Feature("dungeon_doors", "Dungeons", "Next door", "Marks the wither or blood door to open next, read from Hypixel's map: red until you hold a key, green after.", "Skyblocker: Door Highlight")
                .when(() -> DungeonRun.inDungeon() || SbcMod.DEV).tick(DungeonDoors::tick).world(DungeonDoors::drawDoor).left(DungeonDoors::reset)
                .option(Option.choice("dungeon_doors.look", "Look", "How the door is drawn.", "Outline", DungeonMarks.LOOKS))
                .option(Option.bool("dungeon_doors.through_walls", "Through walls", "Also draws the door behind walls.", false)));
        Features.add(new Feature("dungeon_door_keys", "Dungeons", "Keys on the ground", "Marks wither and blood keys lying around, with their name and how far they are.", "Skyblocker: Highlight Door Keys")
                .when(() -> DungeonRun.inDungeon() || SbcMod.DEV).world(DungeonDoors::drawKeys));
        ChatBus.register(ChatBus.OBSERVE + 15, "dungeondoors", doors, DungeonDoors::onChat);
    }
}
