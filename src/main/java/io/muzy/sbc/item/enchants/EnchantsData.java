// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.enchants;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// from skyhanni Enchants.json (EnchantsJson + Enchant): every enchant in 3 groups, NORMAL / ULTIMATE /
// STACKING, keyed by lowercase lore name
// with nbt name, good level, max level. stacking ones also have the progress nbt key, its label and the level
// thresholds
public final class EnchantsData {
    public enum Kind { NORMAL, ULTIMATE, STACKING, DUMMY }

    // skyhanni Enchant / Normal / Ultimate / Stacking / Dummy
    public static final class Enchant implements Comparable<Enchant> {
        public final Kind kind;
        public final String nbtName, loreName;
        public final int goodLevel, maxLevel;
        // stacking only
        public final String nbtNum, statLabel;
        public final List<Integer> stackLevel;

        Enchant(Kind kind, String nbtName, String loreName, int goodLevel, int maxLevel, String nbtNum, String statLabel, List<Integer> stackLevel) {
            this.kind = kind;
            this.nbtName = nbtName;
            this.loreName = loreName;
            this.goodLevel = goodLevel;
            this.maxLevel = maxLevel;
            this.nbtNum = nbtNum;
            this.statLabel = statLabel;
            this.stackLevel = stackLevel;
        }

        public static Enchant dummy(String name) {
            return new Enchant(Kind.DUMMY, name, name, 0, 0, "", "", List.of());
        }

        public boolean isUltimate() {
            return kind == Kind.ULTIMATE;
        }

        public boolean isStacking() {
            return kind == Kind.STACKING;
        }

        // skyhanni Enchant.compareTo: ultimates first, then stacking, then by lore name
        @Override
        public int compareTo(Enchant other) {
            if (isUltimate() == other.isUltimate()) {
                if (isStacking() == other.isStacking()) return loreName.compareTo(other.loreName);
                return isStacking() ? -1 : 1;
            }
            return isUltimate() ? -1 : 1;
        }

        @Override
        public String toString() {
            return nbtName + " " + goodLevel + " " + maxLevel;
        }
    }

    private static final Map<String, Enchant> normal = new HashMap<>(), ultimate = new HashMap<>(), stacking = new HashMap<>();
    private static final Map<String, Enchant> byNbt = new HashMap<>();
    private static boolean loaded;

    private EnchantsData() {}

    private static void read(JsonObject group, Kind kind, Map<String, Enchant> into) {
        if (group == null) return;
        for (Map.Entry<String, JsonElement> e : group.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<Integer> levels = new ArrayList<>();
            if (o.has("stackLevel")) for (JsonElement l : o.getAsJsonArray("stackLevel")) levels.add(l.getAsInt());
            Enchant en = new Enchant(kind, str(o, "nbtName"), str(o, "loreName"), o.has("goodLevel") ? o.get("goodLevel").getAsInt() : 0,
                    o.has("maxLevel") ? o.get("maxLevel").getAsInt() : 0, str(o, "nbtNum"), str(o, "statLabel"), levels);
            into.put(e.getKey().toLowerCase(Locale.ROOT), en);
            byNbt.put(en.nbtName, en);
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (var in = io.muzy.sbc.core.data.DataFiles.sh("Enchants.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            read(root.getAsJsonObject("NORMAL"), Kind.NORMAL, normal);
            read(root.getAsJsonObject("ULTIMATE"), Kind.ULTIMATE, ultimate);
            read(root.getAsJsonObject("STACKING"), Kind.STACKING, stacking);
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad Enchants.json", e);
        }
    }

    // from skyhanni EnchantsJson.hasEnchantData
    public static boolean hasEnchantData() {
        load();
        return !normal.isEmpty() && !ultimate.isEmpty() && !stacking.isEmpty();
    }

    // skyhanni EnchantsJson.getFromLore: by lore name (ignores case), a Dummy if unknown
    public static Enchant getFromLore(String passedLoreName) {
        load();
        String loreName = passedLoreName.toLowerCase(Locale.ROOT);
        Enchant e = normal.get(loreName);
        if (e == null) e = ultimate.get(loreName);
        if (e == null) e = stacking.get(loreName);
        return e == null ? Enchant.dummy(passedLoreName) : e;
    }

    // by lore name, null if unknown (no dummy)
    public static Enchant byLoreName(String loreName) {
        load();
        String k = loreName.toLowerCase(Locale.ROOT);
        Enchant e = normal.get(k);
        if (e == null) e = ultimate.get(k);
        if (e == null) e = stacking.get(k);
        return e;
    }

    public static Enchant byNbtName(String nbtName) {
        load();
        return byNbt.get(nbtName);
    }

    public static boolean isKnown(String loreNameLower) {
        load();
        return normal.containsKey(loreNameLower) || ultimate.containsKey(loreNameLower) || stacking.containsKey(loreNameLower);
    }

    public static Map<String, Enchant> stacking() {
        load();
        return stacking;
    }

    public static int size() {
        load();
        return normal.size() + ultimate.size() + stacking.size();
    }

    static JsonArray emptyArray() {
        return new JsonArray();
    }
}
