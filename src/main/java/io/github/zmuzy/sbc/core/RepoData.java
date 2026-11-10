// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of NotEnoughUpdates, SkyBlockAPI; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

// sb item data (items, pets, recipes, mobs) from the SkyblockAPI repo (built from NEU-REPO, MIT) through a
// copy in our data repo, so the jar alone works on any pc
public final class RepoData {
    private static final String BASE = "https://raw.githubusercontent.com/Skyblock-Companion/sbc-data/data/data/repo/";
    private static final String VERSION = "1_21_5";
    private static final String BUNDLED = "/assets/sbcompanion/data/repo/";
    private static final String INDEX = "shas.json";
    // versioned files we read, by their index name
    private static final List<String> FILES = List.of("items", "pets", "recipes", "mobs");

    private RepoData() {}

    private static Path dir() {
        return Config.DIR.resolve("repo_cache");
    }

    // text of a file like "items.min.json": game folder copy if there is one, else the jars. null = neither
    public static String read(String file) {
        try {
            Path p = dir().resolve(file);
            if (Files.exists(p)) return Files.readString(p);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read {} from the game folder, using the copy in the mod", file, e);
        }
        return bundled(file);
    }

    private static String bundled(String file) {
        try (InputStream in = RepoData.class.getResourceAsStream(BUNDLED + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static String sha(JsonObject index, String name) {
        if (index == null || !index.has(VERSION)) return "";
        JsonObject v = index.getAsJsonObject(VERSION);
        return v.has(name) ? v.get(name).getAsString() : "";
    }

    // which files the remote index wants fetched: missing or different ones. pure, for tests
    static List<String> outdated(JsonObject remote, JsonObject local, java.util.function.Predicate<String> onDisk) {
        return FILES.stream().filter(n -> {
            String want = sha(remote, n);
            return !want.isEmpty() && (!want.equals(sha(local, n)) || !onDisk.test(n + ".min.json"));
        }).toList();
    }

    // fetches what changed off the game thread, a fail just keeps the copies we have
    public static void update() {
        Thread t = new Thread(RepoData::fetch, "SBC repo update");
        t.setDaemon(true);
        t.start();
    }

    private static void fetch() {
        try {
            HttpClient client = HttpClient.newBuilder().sslContext(io.github.zmuzy.sbc.core.Tls.context()).connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
            String remoteText = get(client, INDEX);
            JsonObject remote = JsonParser.parseString(remoteText).getAsJsonObject();
            Path dir = dir();
            Files.createDirectories(dir);
            Path localIndex = dir.resolve(INDEX);
            String localText = Files.exists(localIndex) ? Files.readString(localIndex) : bundled(INDEX);
            JsonElement parsed = localText == null ? null : JsonParser.parseString(localText);
            JsonObject local = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            // the jar copies count as there as long as their index is the one being compared
            boolean fresh = !Files.exists(localIndex);
            List<String> todo = outdated(remote, local, f -> fresh || Files.exists(dir.resolve(f)));
            for (String name : todo) {
                String file = name + ".min.json", body = get(client, VERSION + "/" + file);
                JsonParser.parseString(body); // never swap good data for a broken download
                Path tmp = dir.resolve(file + ".tmp");
                Files.writeString(tmp, body);
                Files.move(tmp, dir.resolve(file), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (!todo.isEmpty()) {
                Files.writeString(localIndex, remoteText);
                SbcMod.LOG.info("Updated the SkyBlock item data: {}; used from the next start", todo);
            }
        } catch (Exception e) {
            SbcMod.LOG.info("Could not check for newer SkyBlock item data, keeping what there is: {}", e.toString());
        }
    }

    private static String get(HttpClient client, String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path)).timeout(Duration.ofSeconds(60)).header("User-Agent", "SkyBlockCompanion/1.0 (Fabric mod)").GET().build();
        HttpResponse<String> r = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode() + " for " + path);
        return r.body();
    }
}
