// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

// the tab list like hypixel builds it: widgets, each starting with a line matching a known first line pattern
// (assets/sbcompanion/data/tabwidgets.json) and running to the next widgets first line
public final class TabWidgets {
    public record Def(String id, String name, Pattern pattern) {}

    // one widget rn: its def, first line index, its line indexes
    public record Widget(Def def, int first, List<Integer> lines) {
        public String title(List<String> plain) {
            return plain.get(first).trim();
        }
    }

    private static List<Def> defs;

    private TabWidgets() {}

    public static synchronized List<Def> defs() {
        if (defs != null) return defs;
        List<Def> out = new ArrayList<>();
        try (InputStream in = TabWidgets.class.getResourceAsStream("/assets/sbcompanion/data/tabwidgets.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("widgets")) {
                JsonObject w = e.getAsJsonObject();
                try {
                    out.add(new Def(w.get("id").getAsString(), w.get("name").getAsString(), Pattern.compile("\\s*(?:" + w.get("pattern").getAsString() + ")")));
                } catch (Exception ex) {
                    SbcMod.LOG.warn("Bad tab widget pattern {}", w.get("id"), ex);
                }
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("No tab widget definitions", e);
        }
        defs = Collections.unmodifiableList(out);
        return defs;
    }

    public static Def byId(String id) {
        for (Def d : defs()) if (d.id.equals(id)) return d;
        return null;
    }

    // the widget a line starts, or null
    public static Def startOf(String plain) {
        if (plain == null) return null;
        for (Def d : defs()) if (d.pattern.matcher(plain).matches()) return d;
        return null;
    }

    // hypixels list as widgets. like skyhanni: a 2nd "Players" or "Info" header at a column top gets dropped,
    // list cut at 80 lines, blank lines belong to nothing, lines before the first widget ignored
    private static List<String> parsedFor;
    private static List<Widget> parsed = List.of();

    public static List<Widget> parse(List<String> plain) {
        if (plain == parsedFor) return parsed;
        List<Widget> out = parseNow(plain);
        parsedFor = plain;
        parsed = out;
        return out;
    }

    private static List<Widget> parseNow(List<String> plain) {
        List<Integer> keep = new ArrayList<>();
        boolean players = false, info = false;
        int n = Math.min(plain.size(), 80);
        Def pl = byId("PLAYER_LIST"), inf = byId("INFO");
        for (int i = 0; i < n; i++) {
            if (i % 20 == 0) {
                Def d = startOf(plain.get(i));
                if (d != null && d == pl) {
                    if (players) continue;
                    players = true;
                } else if (d != null && d == inf) {
                    if (info) continue;
                    info = true;
                }
            }
            keep.add(i);
        }
        List<Widget> out = new ArrayList<>();
        Widget cur = null;
        for (int i : keep) {
            String l = plain.get(i);
            Def d = startOf(l);
            if (d != null) {
                cur = new Widget(d, i, new ArrayList<>());
                out.add(cur);
                continue;
            }
            if (cur == null || l.isBlank()) continue;
            cur.lines.add(i);
        }
        return out;
    }
}
