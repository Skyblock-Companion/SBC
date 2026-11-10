// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

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
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// puzzle helpers, display only: blaze room (skyblocker puzzle/DungeonBlaze), f5 livid finder (skyhanni
// DungeonLividFinder + the repos dungeons/LividSolver.json names) and the f7 terminal solvers
public final class Puzzles {
    // a mark on a terminal slot: fill color + small label
    public record Mark(int color, String text) {}

    private static final dev.zmuzy.sbc.core.Patterns.P ORDER = dev.zmuzy.sbc.core.Patterns.p("Puzzles.ORDER");
    private static final dev.zmuzy.sbc.core.Patterns.P STARTS = dev.zmuzy.sbc.core.Patterns.p("Puzzles.STARTS");
    private static final dev.zmuzy.sbc.core.Patterns.P COLOR = dev.zmuzy.sbc.core.Patterns.p("Puzzles.COLOR");
    private static final dev.zmuzy.sbc.core.Patterns.P PANES = dev.zmuzy.sbc.core.Patterns.p("Puzzles.PANES");
    private static final dev.zmuzy.sbc.core.Patterns.P SAME = dev.zmuzy.sbc.core.Patterns.p("Puzzles.SAME");
    // from skyblocker SameColorTerminal.ORDER: red, orange, yellow, green, blue panes cycle each click
    private static final String[] CYCLE = {"red", "orange", "yellow", "green", "blue"};
    // from skyblocker OrderTerminal.PANES_NUM
    private static final int PANES_NUM = 14;
    // from skyblocker ColorTerminal: dye color names (DyeColor.getName uppercased + SILVER and LIGHT BLUE) and
    // the items of each
    private static final String[] DYES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

    private static Map<Integer, Mark> marks = Map.of();
    private static long computedAt;
    private static Object computedFor;

    private Puzzles() {}

    // one of the terminal solver switches
    static boolean solves(String key) {
        return DungeonMarks.opt("dungeon_gui_terminals", key);
    }

    // ---- spirit leap ----

    private static final dev.zmuzy.sbc.core.Patterns.P CLASS_AFTER = dev.zmuzy.sbc.core.Patterns.p("Puzzles.CLASS_AFTER");
    private static final dev.zmuzy.sbc.core.Patterns.P CLASS_BEFORE = dev.zmuzy.sbc.core.Patterns.p("Puzzles.CLASS_BEFORE");

    // party members dungeon class from the tab, empty = not found
    public static String classOf(String name) {
        for (String l : GameState.tab) {
            Matcher a = CLASS_AFTER.matcher(l);
            while (a.find()) if (a.group(1).equalsIgnoreCase(name)) return a.group(2);
            Matcher b = CLASS_BEFORE.matcher(l);
            while (b.find()) if (b.group(2).equalsIgnoreCase(name)) return b.group(1);
        }
        return "";
    }

    private static int classColor(String cls) {
        return switch (cls) {
            case "Archer" -> 0xB0E0C040;
            case "Berserk" -> 0xB0D94A4A;
            case "Mage" -> 0xB04A9AE0;
            case "Healer" -> 0xB033D17A;
            default -> 0xB0A0A0A0;
        };
    }

    // ---- terminals ----

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // from skyblocker ColorTerminal.itemColor: "<color>_dye|wool|stained_glass|terracotta" + bone meal, lapis,
    // cocoa beans, ink sac
    static String itemColor(String itemPath) {
        switch (itemPath) {
            case "bone_meal": return "WHITE";
            case "lapis_lazuli": return "BLUE";
            case "cocoa_beans": return "BROWN";
            case "ink_sac": return "BLACK";
            default: break;
        }
        for (String d : DYES)
            for (String kind : new String[]{"_dye", "_wool", "_stained_glass", "_terracotta"})
                if (itemPath.equals(d + kind)) return d.toUpperCase(Locale.ROOT);
        return null;
    }

    // from skyblocker colorFromName: the terminals word -> DyeColor name (SILVER = light gray)
    static String colorFromName(String word) {
        String w = word.trim().toUpperCase(Locale.ROOT);
        if (w.equals("SILVER")) return "LIGHT_GRAY";
        if (w.equals("LIGHT BLUE")) return "LIGHT_BLUE";
        for (String d : DYES) if (d.toUpperCase(Locale.ROOT).equals(w) || d.toUpperCase(Locale.ROOT).equals(w.replace(' ', '_'))) return d.toUpperCase(Locale.ROOT);
        return null;
    }

    // from skyblocker ContainerSolver.trimEdges: border slots of a chest with this many rows are never part of the puzzle
    static boolean isEdge(int slot, int rows) {
        int row = slot / 9, col = slot % 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    // from skyblocker OrderTerminal: slot -> count-1 (black panes ignored), null = a slot is still empty
    static int[] orderSlots(List<ItemStack> items) {
        int[] ordered = new int[PANES_NUM];
        Arrays.fill(ordered, -1);
        for (int i = 0; i < items.size(); i++) {
            if (isEdge(i, 4)) continue;
            ItemStack st = items.get(i);
            if (!st.isEmpty() && path(st).equals("black_stained_glass_pane")) continue;
            if (st.isEmpty()) return null;
            int idx = st.getCount() - 1;
            if (idx >= 0 && idx < PANES_NUM) ordered[idx] = i;
        }
        return ordered;
    }

    // from skyblocker SameColorTerminal.computeClicks: target = most common color, clicks forward (+) or back (-)
    static Map<Integer, Integer> sameColorClicks(Map<Integer, Integer> slotColors) {
        Map<Integer, Integer> out = new HashMap<>();
        if (slotColors.isEmpty()) return out;
        int[] counts = new int[CYCLE.length];
        for (int c : slotColors.values()) counts[c]++;
        int target = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[target]) target = i;
        for (var e : slotColors.entrySet()) {
            int color = e.getValue();
            int fwd = Math.floorMod(target - color, CYCLE.length), back = Math.floorMod(color - target, CYCLE.length);
            out.put(e.getKey(), fwd <= back ? fwd : -back);
        }
        return out;
    }

    private static Map<Integer, Mark> compute(AbstractContainerScreen<?> screen) {
        String title = GameState.strip(screen.getTitle().getString()).trim();
        var menu = screen.getMenu();
        int size = Math.max(0, menu.slots.size() - 36);
        List<ItemStack> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) items.add(menu.slots.get(i).getItem());
        Map<Integer, Mark> out = new HashMap<>();
        Matcher m;
        if (title.equals("Spirit Leap")) {
            if (Features.on("dungeon_leap")) return out;
            for (int i = 0; i < size; i++) {
                ItemStack st = items.get(i);
                if (st.isEmpty() || !path(st).equals("player_head")) continue;
                String who = GameState.strip(st.getHoverName().getString()).trim();
                String cls = classOf(who);
                if (cls.isEmpty()) continue;
                out.put(i, new Mark(classColor(cls), cls.substring(0, 1)));
            }
        } else if (ORDER.matcher(title).matches()) {
            if (!solves("solve_order")) return out;
            int[] ordered = orderSlots(items);
            if (ordered == null) return out;
            int currentNum = 0;
            while (currentNum < PANES_NUM && ordered[currentNum] >= 0 && ordered[currentNum] < size && path(items.get(ordered[currentNum])).equals("lime_stained_glass_pane")) currentNum++;
            int last = Math.min(3, PANES_NUM - currentNum);
            for (int i = 0; i < last; i++) {
                int slotNum = ordered[currentNum + i];
                if (slotNum == -1) continue;
                int alpha = 224 - 64 * i;
                out.put(slotNum, new Mark((alpha << 24) | 0x4060FF, String.valueOf(currentNum + i + 1)));
            }
        } else if ((m = STARTS.matcher(title)).matches()) {
            if (!solves("solve_starts")) return out;
            String letter = m.group(1);
            for (int i = 0; i < size; i++) {
                if (isEdge(i, 6)) continue;
                ItemStack st = items.get(i);
                if (st.isEmpty() || st.hasFoil()) continue;
                if (st.getHoverName().getString().startsWith(letter)) out.put(i, new Mark(0xA033D17A, ""));
            }
        } else if ((m = COLOR.matcher(title)).matches()) {
            if (!solves("solve_color")) return out;
            String target = colorFromName(m.group(1));
            if (target == null) return out;
            for (int i = 0; i < size; i++) {
                if (isEdge(i, 6)) continue;
                ItemStack st = items.get(i);
                if (st.isEmpty() || st.hasFoil()) continue;
                if (target.equals(itemColor(path(st)))) out.put(i, new Mark(0xA033D17A, ""));
            }
        } else if (PANES.matcher(title).matches()) {
            if (!solves("solve_panes")) return out;
            for (int i = 0; i < size; i++) {
                ItemStack st = items.get(i);
                if (!st.isEmpty() && path(st).equals("red_stained_glass_pane")) out.put(i, new Mark(0xA033D17A, ""));
            }
        } else if (SAME.matcher(title).matches()) {
            if (!solves("solve_same")) return out;
            Map<Integer, Integer> at = new HashMap<>();
            for (int i = 0; i < size; i++) {
                ItemStack st = items.get(i);
                if (st.isEmpty()) continue;
                String p = path(st);
                for (int c = 0; c < CYCLE.length; c++) if (p.equals(CYCLE[c] + "_stained_glass_pane")) at.put(i, c);
            }
            for (var e : sameColorClicks(at).entrySet()) {
                int clicks = e.getValue();
                if (clicks == 0) continue;
                out.put(e.getKey(), clicks > 0 ? new Mark(0xA033D17A, "+" + clicks) : new Mark(0xA0E0C040, String.valueOf(clicks)));
            }
        }
        return out;
    }

    // mark for a slot of the open terminal, or null. cheap, the answer gets redone a few times a sec
    public static Mark mark(Slot slot) {
        if (!Features.on("dungeon_gui_terminals")) return null;
        var screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> cs)) return null;
        long now = System.currentTimeMillis();
        if (computedFor != cs || now - computedAt > 120) {
            computedFor = cs;
            computedAt = now;
            try {
                marks = compute(cs);
            } catch (Exception e) {
                marks = Map.of();
            }
        }
        return marks.get(slot.index);
    }

    // ---- blaze room (from skyblocker DungeonBlaze) ----

    private record Blaze(ArmorStand stand, int health) {}

    // from skyblocker getBlazesInWorld: the number after the slash in "Blaze x/y❤" (y = max health), sorted low to high
    static Integer blazeHealth(String name) {
        if (!name.contains("Blaze") || !name.contains("/")) return null;
        try {
            return Integer.parseInt(name.substring(name.indexOf("/") + 1, name.length() - 1).replaceAll(",", ""));
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static List<Blaze> blazes(Minecraft mc) {
        List<Blaze> out = new ArrayList<>();
        AABB box = mc.player.getBoundingBox().inflate(500);
        for (Entity e : mc.level.getEntities((Entity) null, box)) {
            if (!(e instanceof ArmorStand st) || !st.isAlive() || st.isVehicle()) continue;
            Integer h = blazeHealth(st.getName().getString());
            if (h != null) out.add(new Blaze(st, h));
        }
        out.sort((a, b) -> Integer.compare(a.health(), b.health()));
        return out;
    }

    private static AABB blazeBox(ArmorStand st) {
        return st.getBoundingBox().inflate(0.3, 0.9, 0.3).move(0, -1.1, 0);
    }

    private static void outline(WorldDraw w, ArmorStand blaze, ArmorStand next) {
        AABB a = blazeBox(blaze);
        w.box(a, 0xFF00FF00, false, true);
        if (next != null && next.isAlive() && next != blaze) {
            AABB b = blazeBox(next);
            w.box(b, 0xFFFFFFFF, false, true);
            w.line(a.getCenter(), b.getCenter(), 0xFFFFFFFF, 5f, true);
        }
    }

    public static void drawBlaze(WorldDraw w) {
        var room = Dungeon.current();
        if (room == null || !room.def.id().startsWith("blaze-room")) return;
        List<Blaze> list = blazes(w.mc);
        if (list.isEmpty()) return;
        Blaze lowest = list.get(0), highest = list.get(list.size() - 1);
        if (!highest.stand().isAlive() || !lowest.stand().isAlive()) return;
        // from skyblocker DungeonBlaze: the platform above y=69 kills low to high, below it high to low
        if (highest.stand().getY() < 69) outline(w, highest.stand(), list.size() > 1 ? list.get(list.size() - 2).stand() : null);
        if (lowest.stand().getY() > 69) outline(w, lowest.stand(), list.size() > 1 ? list.get(1).stand() : null);
    }

    // ---- livid (f5 boss, from skyhanni DungeonLividFinder) ----

    // from skyhanni blockLocation: the wool block whose color names the real livid
    private static final net.minecraft.core.BlockPos LIVID_WOOL = new net.minecraft.core.BlockPos(6, 109, 43);
    // dungeon.f5.livid.name: "Doctor Livid"
    private static final dev.zmuzy.sbc.core.Patterns.P LIVID_NAME = dev.zmuzy.sbc.core.Patterns.p("SH.dungeon.f5.livid.name");
    // from skyhanni lividTextureToColor: skin texture -> chat color code, from the repos LividSolver.json
    private static Map<String, Character> lividTextureToColor;
    // from skyhanni lividNameToColor: livid name -> chat color code (LividSolver.json, skyhannis built in table
    // as fallback)
    private static Map<String, Character> lividNameToColor;

    private static synchronized void loadLivids() {
        if (lividNameToColor != null) return;
        Map<String, Character> textures = new HashMap<>(), names = new HashMap<>();
        try (var in = dev.zmuzy.sbc.core.data.DataFiles.sh("LividSolver.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("livids").entrySet()) {
                    String key = e.getKey();
                    if (key.length() < 2) continue;
                    char code = key.charAt(1);
                    JsonObject info = e.getValue().getAsJsonObject();
                    textures.put(info.get("skin").getAsString(), code);
                    names.put(info.get("name").getAsString(), code);
                }
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad LividSolver.json", e);
        }
        if (names.isEmpty()) {
            names.put("Vendetta", 'f');
            names.put("Doctor", '7');
            names.put("Crossed", 'd');
            names.put("Purple", '5');
            names.put("Scream", '9');
            names.put("Hockey", 'c');
            names.put("Arcade", 'e');
            names.put("Smile", 'a');
            names.put("Frog", '2');
        }
        lividTextureToColor = textures;
        lividNameToColor = names;
    }

    // from skyhanni LorenzColor.toLorenzColor for the wool at blockLocation: dye -> chat color code, null = not wool
    static Character woolColorCode(String blockPath) {
        if (!blockPath.endsWith("_wool")) return null;
        return switch (blockPath.substring(0, blockPath.length() - 5)) {
            case "white" -> 'f';
            case "magenta", "pink" -> 'd';
            case "red" -> 'c';
            case "light_gray", "gray" -> '7';
            case "green" -> '2';
            case "lime" -> 'a';
            case "blue", "light_blue" -> '9';
            case "purple" -> '5';
            case "yellow" -> 'e';
            case "orange", "brown" -> '6';
            case "cyan" -> '3';
            case "black" -> '0';
            default -> 'f';
        };
    }

    // livid color code by name ("Doctor Livid" -> '7'), or null
    static Character lividColorByName(String plainName) {
        loadLivids();
        Matcher m = LIVID_NAME.matcher(plainName);
        if (!m.matches()) return null;
        return lividNameToColor.get(m.group("name"));
    }

    private static String skinTexture(net.minecraft.world.entity.player.Player p) {
        try {
            for (var pr : p.getGameProfile().properties().get("textures")) return pr.value();
        } catch (Exception ignored) {
        }
        return null;
    }

    // from skyhanni getLividColor, then the name fallback
    private static Character lividColor(net.minecraft.world.entity.player.Player p) {
        loadLivids();
        String texture = skinTexture(p);
        if (texture != null) {
            Character c = lividTextureToColor.get(texture);
            if (c != null) return c;
        }
        return lividColorByName(GameState.strip(p.getName().getString()));
    }

    // the 16 chat colors (from skyhanni LorenzColor.toColor)
    static int chatArgb(char code) {
        return switch (code) {
            case '0' -> 0xFF000000;
            case '1' -> 0xFF0000AA;
            case '2' -> 0xFF00AA00;
            case '3' -> 0xFF00AAAA;
            case '4' -> 0xFFAA0000;
            case '5' -> 0xFFAA00AA;
            case '6' -> 0xFFFFAA00;
            case '7' -> 0xFFAAAAAA;
            case '8' -> 0xFF555555;
            case '9' -> 0xFF5555FF;
            case 'a' -> 0xFF55FF55;
            case 'c' -> 0xFFFF5555;
            case 'd' -> 0xFFFF55FF;
            case 'e' -> 0xFFFFFF55;
            case 'f' -> 0xFFFFFFFF;
            default -> 0xFF55FFFF;
        };
    }

    // real livids color code while known + its entity
    private static Character realLividColor;
    private static int foundId = -1;
    private static boolean titleShown;

    // new run knows nothing about the last runs livid
    static void resetLivid() {
        realLividColor = null;
        foundId = -1;
        titleShown = false;
    }

    // from skyhanni LividFinderConfig.hideWrong: an npc livid in another color than the real one
    static boolean isWrongLivid(net.minecraft.world.entity.player.Player p) {
        if (realLividColor == null || p.getUUID().version() != 2 || p == Minecraft.getInstance().player) return false;
        Character c = lividColor(p);
        return c != null && c != realLividColor;
    }

    // from skyhanni LividColor.shouldGlow
    static boolean lividGlows(net.minecraft.world.entity.player.Player p) {
        return p.getId() == foundId && Features.on("dungeon_livid") && DungeonMarks.opt("dungeon_livid", "glow");
    }

    // from skyhanni LividColor.getGlowColor: the livids own color or the one from settings
    static int lividRgb() {
        return lividArgb() & 0xFFFFFF;
    }

    private static int lividArgb() {
        if (DungeonMarks.opt("dungeon_livid", "use_color")) return DungeonMarks.color("dungeon_livid", "color", 0xFFFF5555);
        return realLividColor == null ? 0xFFFFFFFF : chatArgb(realLividColor);
    }

    // from skyhanni WOOL_TO_FORMATTING names, for the title
    private static String colorWord(char code) {
        return switch (code) {
            case 'c' -> "RED";
            case 'e' -> "YELLOW";
            case 'a' -> "LIME";
            case '2' -> "GREEN";
            case '9' -> "BLUE";
            case 'd' -> "MAGENTA";
            case '5' -> "PURPLE";
            case '7' -> "GRAY";
            case 'f' -> "WHITE";
            default -> "UNKNOWN";
        };
    }

    public static void drawLivid(WorldDraw w) {
        var mc = w.mc;
        if (mc.level == null || mc.player == null) return;
        if (!"CATACOMBS".equals(GameState.island) || !DungeonRun.floor().endsWith("5")) {
            realLividColor = null;
            foundId = -1;
            titleShown = false;
        }
        // from skyhanni isCurrentlyBlind: nothing while livids blindness is on
        var blind = mc.player.getEffect(MobEffects.BLINDNESS);
        if (blind != null && blind.getDuration() > 10) return;
        var state = mc.level.getBlockState(LIVID_WOOL);
        Character color = woolColorCode(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        if (color == null) return;
        for (var p : mc.level.players()) {
            // from skyhanni lividEntities: npc players (uuid version 2) named "<x> Livid" or with a known livid skin
            if (p == mc.player || p.getUUID().version() != 2) continue;
            Character c = lividColor(p);
            if (c == null || c != color) continue;
            realLividColor = color;
            foundId = p.getId();
            int argb = lividArgb();
            if (DungeonMarks.opt("dungeon_livid", "title") && !titleShown) {
                titleShown = true;
                mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§" + color + DungeonMarks.text("dungeon_livid", "title_text", "The livid is [color]").replace("[color]", colorWord(color))));
            }
            w.label("§" + color + "Livid", p.position().add(0, p.getBbHeight() + 0.6, 0), true);
            if (DungeonMarks.opt("dungeon_livid", "box")) w.box(p.getBoundingBox(), argb, true, true);
            w.line(w.eye.add(mc.player.getLookAngle().scale(2)), p.position().add(0.5, 0, 0.5), argb, 3f, true);
            return;
        }
    }

    public static void register() {
        Features.add(new Feature("dungeon_livid", "Dungeons", "Livid finder", "In the floor 5 boss fight, outlines the real Livid among the nine (the wool block's colour names it) with a line to it.", "SkyHanni: Livid finder")
                .when(() -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV).world(Puzzles::drawLivid)
                .option(Option.bool("dungeon_livid.box", "Box", "A box around the real Livid.", true))
                .option(Option.bool("dungeon_livid.glow", "Glow", "The real Livid glows.", false))
                .option(Option.bool("dungeon_livid.title", "Title", "A title on screen naming the colour.", false))
                .option(Option.text("dungeon_livid.title_text", "Title text", "[color] becomes the colour.", "The livid is [color]"))
                .option(Option.bool("dungeon_livid.hide_wrong", "Hide wrong Livids", "The other Livids disappear.", false))
                .option(Option.bool("dungeon_livid.use_color", "Own colour", "Uses the colour below instead of the Livid's.", false))
                .option(Option.color("dungeon_livid.color", "Colour", "The colour of the box and the glow when set above.", 0xFFFF5555)));
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Features.add(new Feature("dungeon_blaze", "Dungeons", "Blaze room", "Outlines the blaze to hit next (green) and the one after (white) with a line between them.", "Skyblocker: blaze solver")
                .when(inDungeon).world(Puzzles::drawBlaze));
        Features.add(new Feature("dungeon_gui_terminals", "Dungeons", "Floor 7 terminal windows", "Marks what to click in the terminal windows: the next three order panes, items starting with the letter, items of the colour, panes to correct, and the clicks to reach the most common colour.", "Skyblocker: terminal solvers")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("dungeon_gui_terminals.block_wrong", "Stop wrong clicks", "A click the solver calls wrong does not go out. Hold the key in Controls to click anyway.", true))
                .option(Option.bool("dungeon_gui_terminals.solve_order", "Click in order", "Solve the order terminal.", true))
                .option(Option.bool("dungeon_gui_terminals.solve_color", "Select coloured", "Solve the colour terminal.", true))
                .option(Option.bool("dungeon_gui_terminals.solve_same", "All to same colour", "Solve the same colour terminal.", true))
                .option(Option.bool("dungeon_gui_terminals.solve_starts", "Starts with", "Solve the starts with terminal.", true))
                .option(Option.bool("dungeon_gui_terminals.solve_panes", "Correct all panes", "Solve the panes terminal.", true)));
    }
}
