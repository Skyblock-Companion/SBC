// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.hud.ElementStyle;
import io.muzy.sbc.ui.hud.ScoreboardElement;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// /sbc import: reads settings you already made in other mods configs (same config folder) into ours
public final class ImportConfigs {
    private ImportConfigs() {}

    private static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    private static JsonObject path(JsonObject o, String... keys) {
        JsonObject cur = o;
        for (String k : keys) {
            if (cur == null || !cur.has(k) || !cur.get(k).isJsonObject()) return null;
            cur = cur.getAsJsonObject(k);
        }
        return cur;
    }

    private static void set(String feature, String key, Object v) {
        Feature f = Features.get(feature);
        if (f == null) return;
        var o = f.option(key);
        if (o != null) o.set(v);
    }

    // custom scoreboard from a skyhanni config: entries + order, title, spacing, bg, scale, filtering
    public static String skyhanniScoreboard() {
        Path p = configDir().resolve("skyhanni").resolve("config.json");
        if (!Files.exists(p)) return "No scoreboard settings from another mod were found.";
        try {
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            JsonObject cs = path(root, "gui", "customScoreboard");
            if (cs == null) return "That mod's settings have no custom scoreboard.";
            int n = 0;
            if (cs.has("scoreboardEntries") && cs.get("scoreboardEntries").isJsonArray()) {
                List<String> entries = new ArrayList<>();
                JsonArray arr = cs.getAsJsonArray("scoreboardEntries");
                for (JsonElement e : arr) {
                    String s = e.getAsString();
                    if (s.startsWith("EMPTY_LINE")) s = "EMPTY_LINE";
                    if (ScoreboardElement.ALL.contains(s)) entries.add(s);
                }
                if (!entries.isEmpty()) {
                    Config.I.boardEntries.clear();
                    Config.I.boardEntries.addAll(entries);
                    n = entries.size();
                }
            }
            JsonObject d = path(cs, "display");
            if (d != null) {
                JsonObject tf = path(d, "titleAndFooter");
                if (tf != null && tf.has("customTitle") && (!tf.has("useCustomTitle") || tf.get("useCustomTitle").getAsBoolean()))
                    set("board", "title", tf.get("customTitle").getAsString().replace("&&", "&"));
                if (d.has("lineSpacing")) ElementStyle.of("board").rowH(Math.max(8, Math.min(16, d.get("lineSpacing").getAsInt())));
                if (d.has("numberFormat")) set("board", "numbers", d.get("numberFormat").getAsString().equalsIgnoreCase("SHORT") ? "Short" : "Long");
                if (d.has("textAlignment")) {
                    String a = d.get("textAlignment").getAsString();
                    set("board", "align", a.equalsIgnoreCase("CENTER") ? "Center" : a.equalsIgnoreCase("RIGHT") ? "Right" : "Left");
                }
                JsonObject mayor = path(d, "mayor");
                if (mayor != null && mayor.has("showMayorPerks")) set("board", "mayorPerks", mayor.get("showMayorPerks").getAsBoolean());
                JsonObject party = path(d, "party");
                if (party != null && party.has("maxPartyList")) set("board", "partyMax", party.get("maxPartyList").getAsInt());
            }
            JsonObject filt = path(cs, "informationFiltering");
            if (filt != null && filt.has("hideEmptyLines")) set("board", "hideEmpty", filt.get("hideEmptyLines").getAsBoolean());
            JsonObject bg = path(cs, "background");
            if (bg != null) {
                ElementStyle st = ElementStyle.of("board");
                boolean enabled = !bg.has("enabled") || bg.get("enabled").getAsBoolean();
                if (!enabled) st.alpha(0f);
                else if (bg.has("color")) {
                    String[] parts = bg.get("color").getAsString().split(":");
                    if (parts.length >= 5) {
                        try {
                            int a = Integer.parseInt(parts[1]), r = Integer.parseInt(parts[2]), g = Integer.parseInt(parts[3]), b = Integer.parseInt(parts[4]);
                            st.bg(0xFF000000 | (r << 16) | (g << 8) | b);
                            st.alpha(a / 255f);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            JsonObject pos = path(cs, "position");
            if (pos != null && pos.has("scale")) HudLayout.setScale("board", pos.get("scale").getAsFloat());
            Config.save();
            return "Imported the scoreboard from another mod: " + n + " entries, title, spacing, background and scale.";
        } catch (Exception e) {
            io.muzy.sbc.SbcMod.LOG.warn("Could not read {}", p, e);
            return "Could not read that mod's settings.";
        }
    }
}
