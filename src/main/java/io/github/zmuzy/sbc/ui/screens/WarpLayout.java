// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Modern Warp Menu; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.screens;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// picture warp menu layout in modern warp menu format + data (MIT): islands on a 64x36 screen grid, each with
// its picture + width as a share of the screen, warps on the islands own grid
public final class WarpLayout {
    public static final int GRID_W = 64, GRID_H = 36, ISLAND_GRID = 40;

    public record Tex(Identifier id, int width, int height) {}

    public record Warp(int gridX, int gridY, String name, String command, List<String> tags, int slotIndex) {
        // from modern warp menu Warp.getWarpCommand, no slash
        public String warpCommand() {
            return command.equals("/garry") ? "garry" : "warp " + command;
        }
    }

    public record Island(String name, Tex texture, Tex hover, int gridX, int gridY, int z, float widthPct, List<Warp> warps) {}

    // small corner buttons
    public record Corner(int gridX, int gridY, float widthPct, Tex texture) {}

    // a slot that must hold a certain item for the chest to really be the warp menu
    public record MenuCheck(int slot, String itemId, String itemName) {}

    public final String type;
    public final Identifier background;
    public final Tex warpIcon, warpIconHover;
    public final float warpIconPct;
    public final Corner configButton, regularButton;
    public final List<Island> islands = new ArrayList<>();

    private WarpLayout(String type, Identifier background, Tex warpIcon, Tex warpIconHover, float warpIconPct, Corner configButton, Corner regularButton) {
        this.type = type;
        this.background = background;
        this.warpIcon = warpIcon;
        this.warpIconHover = warpIconHover;
        this.warpIconPct = warpIconPct;
        this.configButton = configButton;
        this.regularButton = regularButton;
    }

    private static Tex tex(JsonObject o) {
        if (o == null) return null;
        return new Tex(Identifier.parse(o.get("location").getAsString()), o.get("width").getAsInt(), o.get("height").getAsInt());
    }

    private static Corner corner(JsonObject o) {
        if (o == null) return null;
        return new Corner(o.get("grid_x").getAsInt(), o.get("grid_y").getAsInt(), o.get("width_percentage").getAsFloat(), tex(o.getAsJsonObject("texture")));
    }

    static WarpLayout parse(JsonObject root) {
        JsonObject wi = root.getAsJsonObject("warp_icon");
        Tex icon = wi == null ? new Tex(Identifier.fromNamespaceAndPath("sbcompanion", "textures/gui/warp/portal.png"), 207, 256) : tex(wi.getAsJsonObject("texture"));
        Tex iconHover = wi == null || !wi.has("hover_effect_texture") ? icon : tex(wi.getAsJsonObject("hover_effect_texture"));
        float pct = wi == null ? 0.02f : wi.get("width_percentage").getAsFloat();
        Identifier bg = root.has("background") ? Identifier.parse(root.get("background").getAsString()) : null;
        WarpLayout l = new WarpLayout(root.has("type") ? root.get("type").getAsString() : "overworld", bg, icon, iconHover, pct,
                corner(root.getAsJsonObject("config_button")), corner(root.getAsJsonObject("regular_warp_menu_button")));
        for (JsonElement ie : root.getAsJsonArray("island_list")) {
            JsonObject io = ie.getAsJsonObject();
            List<Warp> warps = new ArrayList<>();
            JsonArray wl = io.getAsJsonArray("warp_list");
            if (wl != null) for (JsonElement we : wl) {
                JsonObject wo = we.getAsJsonObject();
                List<String> tags = new ArrayList<>();
                if (wo.has("tags")) for (JsonElement t : wo.getAsJsonArray("tags")) tags.add(t.getAsString());
                warps.add(new Warp(wo.get("grid_x").getAsInt(), wo.get("grid_y").getAsInt(), wo.get("display_name").getAsString(),
                        wo.has("command_name") ? wo.get("command_name").getAsString() : "", tags, wo.has("slot_index") ? wo.get("slot_index").getAsInt() : -1));
            }
            Tex t = tex(io.getAsJsonObject("texture"));
            l.islands.add(new Island(io.get("name").getAsString(), t, io.has("hover_effect_texture") ? tex(io.getAsJsonObject("hover_effect_texture")) : t,
                    io.get("grid_x").getAsInt(), io.get("grid_y").getAsInt(), io.has("z_level") ? io.get("z_level").getAsInt() : 0,
                    io.get("width_percentage").getAsFloat(), warps));
        }
        return l;
    }

    private static WarpLayout overworld, rift;

    public static WarpLayout get(boolean riftLayout) {
        if (riftLayout) {
            if (rift == null) rift = load("rift_layout");
            return rift;
        }
        if (overworld == null) overworld = load("layout");
        return overworld;
    }

    public static void reload() {
        overworld = rift = null;
    }

    private static WarpLayout load(String name) {
        try {
            var override = io.github.zmuzy.sbc.core.Config.DIR.resolve("warp").resolve(name + ".json");
            if (java.nio.file.Files.exists(override)) return parse(JsonParser.parseString(java.nio.file.Files.readString(override)).getAsJsonObject());
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad warp layout override {}", name, e);
        }
        try (InputStream in = WarpLayout.class.getResourceAsStream("/assets/sbcompanion/warp/" + name + ".json")) {
            if (in == null) throw new IllegalStateException("missing " + name);
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad warp layout {}", name, e);
            return new WarpLayout("overworld", null, new Tex(Identifier.fromNamespaceAndPath("sbcompanion", "textures/gui/warp/portal.png"), 207, 256), null, 0.02f, null, null);
        }
    }

    // --- rest of modern warp menus data + rules ---

    private static JsonObject constants;
    private static Map<String, String> failMessages;

    private static JsonObject constants() {
        if (constants == null) {
            try (InputStream in = WarpLayout.class.getResourceAsStream("/assets/sbcompanion/warp/skyblock_constants.json")) {
                constants = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                SbcMod.LOG.warn("Bad warp constants", e);
                constants = new JsonObject();
            }
        }
        return constants;
    }

    private static String errorText(String key) {
        return switch (key.substring(key.lastIndexOf('.') + 1)) {
            case "notUnlocked" -> "Not Unlocked";
            case "unknownDestination" -> "Unknown Destination";
            case "noDestination" -> "No Destination Found";
            case "notVisited" -> "Visit Location to Unlock Warp";
            case "notOpenYet" -> "Not Open Yet";
            default -> key;
        };
    }

    // what to show for a "warp failed" chat line, null if its not one
    public static String failMessage(String chatLine) {
        if (failMessages == null) {
            Map<String, String> map = new HashMap<>();
            JsonObject messages = constants().getAsJsonObject("warp_messages");
            if (messages != null) for (var e : messages.getAsJsonObject("warp_fail_messages").entrySet()) map.put(e.getKey(), errorText(e.getValue().getAsString()));
            // what hypixel says now (seen in a dump), the data only has the old wording
            map.put("Jerry's Workshop is only available during the season of Late Winter!", errorText("notOpenYet"));
            failMessages = map;
        }
        return failMessages.get(chatLine);
    }

    // menu_matching for the overworld or rift menu
    public static List<MenuCheck> menuChecks(boolean riftMenu) {
        List<MenuCheck> out = new ArrayList<>();
        JsonObject matching = constants().getAsJsonObject("menu_matching");
        if (matching == null) return out;
        JsonArray list = matching.getAsJsonArray(riftMenu ? "porhtal" : "fast_travel");
        if (list != null) for (JsonElement e : list) {
            JsonObject o = e.getAsJsonObject();
            out.add(new MenuCheck(o.get("inventory_slot").getAsInt(), o.get("item_id").getAsString(), o.get("item_name").getAsString()));
        }
        return out;
    }

    private static final Pattern SEASON = Pattern.compile("(?<seasonStage>Late|Early)? ?(?<season>[a-zA-Z]+) \\d{1,2}.*");

    // from modern warp menu checkSeason + the "jerry" tag: jerrys workshop shows in late winter or december
    public static boolean jerryOpen(List<String> plainScoreboard, int month) {
        if (month == 12) return true;
        for (String line : plainScoreboard) {
            Matcher m = SEASON.matcher(line.trim());
            if (m.matches()) return "Winter".equals(m.group("season")) && "Late".equals(m.group("seasonStage"));
        }
        return false;
    }
}
