// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// every sea creature + the exact chat line hypixel announces it with, from skyhannis repo
// (constants/SeaCreatures.json). a catch is matched by that line, never by guessing sentence shapes
public final class SeaCreatures {
    public record Creature(String name, String rarity, int fishingXp, String area) {}

    private static Map<String, Creature> byMessage;

    private SeaCreatures() {}

    private static synchronized Map<String, Creature> load() {
        if (byMessage != null) return byMessage;
        Map<String, Creature> out = new HashMap<>();
        try (InputStream in = io.muzy.sbc.core.data.DataFiles.sh("SeaCreatures.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> area : root.entrySet()) {
                JsonObject a = area.getValue().getAsJsonObject();
                if (!a.has("sea_creatures")) continue;
                for (Map.Entry<String, JsonElement> c : a.getAsJsonObject("sea_creatures").entrySet()) {
                    JsonObject o = c.getValue().getAsJsonObject();
                    String msg = o.has("chat_message") ? o.get("chat_message").getAsString() : null;
                    if (msg == null) continue;
                    out.put(msg.trim(), new Creature(c.getKey(), o.has("rarity") ? o.get("rarity").getAsString() : "COMMON",
                            o.has("fishing_experience") ? o.get("fishing_experience").getAsInt() : 0, area.getKey()));
                }
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("No sea creature data", e);
        }
        byMessage = Collections.unmodifiableMap(out);
        return byMessage;
    }

    // creature a plain chat line announces, or null
    public static Creature fromChat(String plain) {
        return load().get(plain.trim());
    }

    public static int count() {
        return load().size();
    }
}
