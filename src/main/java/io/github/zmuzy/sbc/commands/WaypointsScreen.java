// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.nav.Waypoints;
import io.github.zmuzy.sbc.ui.layout.FlowLayout;
import io.github.zmuzy.sbc.ui.layout.Insets;
import io.github.zmuzy.sbc.ui.layout.Sizing;
import io.github.zmuzy.sbc.ui.layout.UIContainers;
import io.github.zmuzy.sbc.ui.screens.SbcScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

// from skyblocker WaypointsScreen, AbstractWaypointsScreen, WaypointsListWidget, WaypointsShareScreen, over
// our own groups (nav/Waypoints, same ones /sbc wp fills)
// show/hide, rename, edit points, add where you stand, remove, copy a group as text
// paste ours, skytils or skyblocker format. a group can be ordered like skyblockers, mining routes stay with
// Ordered Waypoints
public final class WaypointsScreen extends SbcScreen {
    private static final String SKYBLOCKER_PREFIX = "[Skyblocker-Waypoint-Data-V1]", SKYTILS_PREFIX = "<Skytils-Waypoint-Data>(V";
    private Store.WpGroup open;
    private FlowLayout root;
    private String message = "";

    public WaypointsScreen(Screen parent) {
        super("Waypoints", parent);
    }

    private void rebuild() {
        root.clearChildren();
        build(root);
    }

    private static double number(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String shortNumber(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }

    private void listPage(FlowLayout list) {
        for (Store.WpGroup g : new ArrayList<>(Store.I.waypoints.values())) {
            FlowLayout r = EditorUi.row();
            r.child(SbcScreen.label((g.visible ? "" : "§8") + g.name + " §7(" + g.points.size() + ")", EditorUi.ink()).horizontalSizing(Sizing.expand()));
            r.child(EditorUi.button(g.visible ? "§aShown" : "§7Hidden", () -> {
                g.visible = !g.visible;
                Store.markDirty();
                rebuild();
            }));
            r.child(EditorUi.button(g.ordered ? "§aIn order" : "§7All", () -> {
                g.ordered = !g.ordered;
                g.current = 0;
                Store.markDirty();
                rebuild();
            }));
            r.child(EditorUi.button("Edit", () -> {
                open = g;
                rebuild();
            }));
            r.child(EditorUi.button("Copy", () -> {
                minecraft.keyboardHandler.setClipboard(Waypoints.export(g.name));
                message = "§aCopied " + g.name + ".";
                rebuild();
            }));
            r.child(EditorUi.button("For others", () -> {
                String shared = Waypoints.exportShared(g.name);
                if (!shared.isEmpty()) minecraft.keyboardHandler.setClipboard(shared);
                message = shared.isEmpty() ? "§cNothing to copy." : "§aCopied " + g.name + " in the form other tools read.";
                rebuild();
            }));
            r.child(EditorUi.button("§cX", () -> {
                Waypoints.clear(g.name);
                rebuild();
            }));
            list.child(r);
        }
        if (Store.I.waypoints.isEmpty()) list.child(SbcScreen.label("No waypoints yet.", SbcScreen.dim()));
    }

    private void groupPage(FlowLayout list, Store.WpGroup g) {
        FlowLayout head = EditorUi.row();
        head.child(SbcScreen.label("Name", SbcScreen.dim()));
        head.child(EditorUi.field(50, g.name, null, v -> {
            if (v.isBlank() || Store.I.waypoints.containsKey(v)) return;
            Store.I.waypoints.remove(g.name);
            g.name = v;
            Store.I.waypoints.put(v, g);
            Store.markDirty();
        }));
        head.child(SbcScreen.label("Hide within", SbcScreen.dim()));
        head.child(EditorUi.field(14, shortNumber(g.radius), null, v -> {
            g.radius = Math.max(0, number(v, g.radius));
            Store.markDirty();
        }));
        list.child(head);
        for (Store.WpPoint p : new ArrayList<>(g.points)) {
            FlowLayout c = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            c.surface(SbcScreen.card());
            c.padding(Insets.of(3));
            c.gap(2);
            FlowLayout top = EditorUi.row();
            top.child(EditorUi.field(50, p.name, "Name", v -> {
                p.name = v;
                Store.markDirty();
            }));
            top.child(SbcScreen.label(p.island.isEmpty() ? "§7Any island" : "§7" + GameState.pretty(p.island), SbcScreen.dim()).horizontalSizing(Sizing.expand()));
            top.child(EditorUi.button("Go", () -> {
                io.github.zmuzy.sbc.nav.Navigator.navigate(new io.github.zmuzy.sbc.nav.Navigator.Target(p.island, p.name, new Vec3(p.x, p.y, p.z)));
                onClose();
            }));
            top.child(EditorUi.button("§cX", () -> {
                g.points.remove(p);
                Store.markDirty();
                rebuild();
            }));
            c.child(top);
            FlowLayout pos = EditorUi.row();
            pos.child(EditorUi.field(30, shortNumber(p.x), "x", v -> {
                p.x = number(v, p.x);
                Store.markDirty();
            }));
            pos.child(EditorUi.field(30, shortNumber(p.y), "y", v -> {
                p.y = number(v, p.y);
                Store.markDirty();
            }));
            pos.child(EditorUi.field(30, shortNumber(p.z), "z", v -> {
                p.z = number(v, p.z);
                Store.markDirty();
            }));
            c.child(pos);
            list.child(c);
        }
        list.child(EditorUi.button("Add where you stand", () -> {
            if (minecraft.player != null) Waypoints.add(g.name, "Waypoint " + (g.points.size() + 1), GameState.island, minecraft.player.position());
            rebuild();
        }));
    }

    // Waypoints.fromSkytils + fromSkyblocker into groups here. returns how many came in, -1 if the text is neither
    static int paste(String text, String defaultIsland) {
        String t = text.trim();
        int ours = Waypoints.importText(t);
        if (ours >= 0) return ours;
        try {
            if (t.startsWith(SKYBLOCKER_PREFIX)) return addSkyblocker(JsonParser.parseString(gunzip(t.substring(SKYBLOCKER_PREFIX.length()))).getAsJsonArray(), defaultIsland);
            String json;
            if (t.startsWith(SKYTILS_PREFIX)) json = gunzip(t.substring(t.indexOf(':') + 1));
            else json = new String(Base64.getDecoder().decode(t), StandardCharsets.UTF_8);
            return addSkytils(json, defaultIsland);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String gunzip(String base64) throws java.io.IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(base64)))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String island(JsonObject group, String fallback) {
        String id = group.has("island") ? group.get("island").getAsString() : "";
        String key = GameState.islandOfMode(id);
        return key.isEmpty() ? fallback : key;
    }

    // skyblocker WaypointGroup.SKYTILS_CODEC, or a bare waypoint list like fromSkytilsJson takes
    private static int addSkytils(String json, String defaultIsland) {
        JsonElement e = JsonParser.parseString(json);
        JsonArray categories;
        if (e.isJsonObject() && e.getAsJsonObject().has("categories")) categories = e.getAsJsonObject().getAsJsonArray("categories");
        else {
            JsonObject one = new JsonObject();
            one.addProperty("name", "New Group");
            one.add("waypoints", e.getAsJsonArray());
            categories = new JsonArray();
            categories.add(one);
        }
        int n = 0;
        for (JsonElement c : categories) {
            JsonObject g = c.getAsJsonObject();
            String isl = island(g, defaultIsland), name = g.has("name") ? g.get("name").getAsString() : "New Group";
            for (JsonElement p : g.getAsJsonArray("waypoints")) {
                JsonObject o = p.getAsJsonObject();
                Waypoints.add(name, o.get("name").getAsString(), isl, new Vec3(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
                n++;
            }
        }
        return n;
    }

    // skyblocker WaypointGroup.CODEC + NamedWaypoint.CODEC: pos as a list, name as text
    private static int addSkyblocker(JsonArray groups, String defaultIsland) {
        int n = 0;
        for (JsonElement c : groups) {
            JsonObject g = c.getAsJsonObject();
            String isl = island(g, defaultIsland), name = g.get("name").getAsString();
            if (g.has("ordered") && g.get("ordered").getAsBoolean()) Waypoints.group(name).ordered = true;
            for (JsonElement p : g.getAsJsonArray("waypoints")) {
                JsonObject o = p.getAsJsonObject();
                JsonArray pos = o.getAsJsonArray("pos");
                JsonElement label = o.get("name");
                String text = label.isJsonPrimitive() ? label.getAsString() : label.isJsonObject() && label.getAsJsonObject().has("text") ? label.getAsJsonObject().get("text").getAsString() : "Waypoint";
                Waypoints.add(name, text, isl, new Vec3(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()));
                n++;
            }
        }
        return n;
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        root = rootComponent;
        FlowLayout foot = EditorUi.row();
        if (open != null) {
            foot.child(EditorUi.button("Back", () -> {
                open = null;
                rebuild();
            }));
        } else {
            foot.child(EditorUi.button("New group", () -> {
                int i = 1;
                while (Store.I.waypoints.containsKey("Group " + i)) i++;
                open = Waypoints.group("Group " + i);
                Store.markDirty();
                rebuild();
            }));
            foot.child(EditorUi.button("Paste", () -> {
                int n = paste(minecraft.keyboardHandler.getClipboard(), GameState.island);
                message = n < 0 ? "§cThat is not a waypoint text." : "§aAdded " + n + " waypoints.";
                rebuild();
            }));
        }
        foot.child(EditorUi.button("Done", this::onClose));
        String hint = open != null ? "Places are saved as you type." : message.isEmpty() ? "Yours and what you imported, by group. /sbc wp adds from chat." : message;
        FlowLayout list = EditorUi.panel(root, width, open != null ? open.name : "Waypoints", hint, foot);
        if (open != null) groupPage(list, open);
        else listPage(list);
    }
}
