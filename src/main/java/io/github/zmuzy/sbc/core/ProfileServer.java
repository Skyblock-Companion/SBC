// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

// our server, it holds the hypixel key so players never need one. only gets the player/profile id being
// looked up and answers what hypixels profile + museum calls answer
public final class ProfileServer {
    // this release name, sent with every call, the server compares it with VERSION_CHECK. same as jar_name in
    // gradle.properties
    public static final String VERSION = "beta-0.2";
    private static final String BASE = "https://sbc.muzy.io/";
    private static final long WAIT_AFTER_FAILURE_MS = 5 * 60_000L;
    private static volatile long downUntil;
    private static volatile Boolean forced;
    // servers update message, null = none (or not asked yet)
    private static volatile Update update;

    public record Update(String latest, String message) {}

    private ProfileServer() {}

    public static String profiles(String uuid) {
        return BASE + "profiles?uuid=" + uuid;
    }

    public static String museum(String profileId) {
        return BASE + "museum?profile=" + profileId;
    }

    // server call with the version header it wants
    public static CompletableFuture<JsonElement> get(String url) {
        return Net.getJson(url, "X-SBC-Version", VERSION);
    }

    // asks once if this is the current version, answer waits in update()
    public static void checkVersion() {
        get(BASE + "version").whenComplete((json, e) -> {
            if (e == null) update = readUpdate(json);
        });
    }

    public static Update update() {
        return update;
    }

    // servers answer as an update, null if this version is current or its not one
    static Update readUpdate(JsonElement json) {
        if (json == null || !json.isJsonObject()) return null;
        JsonObject o = json.getAsJsonObject();
        if (!o.has("update") || !o.get("update").isJsonPrimitive() || !o.get("update").getAsBoolean()) return null;
        String latest = text(o, "latest", 32);
        String message = text(o, "message", 300);
        if (message.isEmpty()) message = latest.isEmpty() ? "A new version is out." : latest + " is out.";
        return new Update(latest, message);
    }

    // plain text from the answer: no color codes or control chars, max long
    private static String text(JsonObject o, String key, int max) {
        if (!o.has(key) || !o.get(key).isJsonPrimitive()) return "";
        String s = o.get(key).getAsString().replaceAll("§.", "").replaceAll("\\p{Cntrl}", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    public static boolean up() {
        return forced != null ? forced : System.currentTimeMillis() >= downUntil;
    }

    // no answer, leave it alone for a while
    public static void failed() {
        downUntil = System.currentTimeMillis() + WAIT_AFTER_FAILURE_MS;
    }

    // tests: true/false holds that answer, null goes back to the real one
    public static void forTests(Boolean up) {
        forced = up;
        downUntil = 0;
    }

    // tests + previews: sets the waiting update
    public static void updateForTests(Update u) {
        update = u;
    }
}
