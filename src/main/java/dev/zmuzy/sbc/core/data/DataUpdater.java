// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.data.DataFiles.Source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

// keeps game data fresh with no release: at game start, off the game thread, asks for the newest commit that
// touched skyhanni + neu data in our data repo and fetches what changed
public final class DataUpdater {
    // one GET: an answer, or IOException with no connection
    public interface Web {
        Reply get(String url, Map<String, String> headers) throws IOException, InterruptedException;
    }

    public record Reply(int status, byte[] body, String etag) {
        boolean ok() {
            return status / 100 == 2;
        }

        boolean rateLimited() {
            return status == 403 || status == 429;
        }

        String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    // how one sources check ended. only RATE_LIMITED stops the other one, the limit is per address
    public enum Outcome { UP_TO_DATE, UPDATED, RATE_LIMITED, FAILED }

    static final String API = "https://api.github.com/repos/", RAW = "https://raw.githubusercontent.com/";
    static final String REMOTE_URL = RAW + "Skyblock-Companion/sbc-data/main/" + RemoteSwitch.FILE;
    // way above what either folder has, a bigger list isnt the folder we know
    private static final int MAX_FILES = 2000;
    private static final long MAX_BYTES = 128L * 1024 * 1024;
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_-]+(?:[./][A-Za-z0-9_-]+)*\\.json");
    private static final Gson GSON = new Gson();
    private static boolean started;

    private DataUpdater() {}

    // once at game start when all features exist, checks for data unless the setting is off
    public static synchronized void start() {
        if (RemoteSwitch.holdBound()) Safe.endTick("DataUpdater.endTick", mc -> RemoteSwitch.keepOff());
        if (started || !GameData.autoUpdate()) return;
        started = true;
        Thread t = new Thread(() -> {
            try {
                runAll(DataWeb.INSTANCE);
            } catch (Exception e) {
                SbcMod.LOG.info("Could not check for newer game data, keeping what there is: {}", e.toString());
            }
        }, "SBC data update");
        t.setDaemon(true);
        t.start();
    }

    // both repos + the remote switch file
    static void runAll(Web web) throws InterruptedException {
        DataFiles.ensure();
        for (Source s : Source.values()) {
            Outcome o = run(s, web);
            if (o == Outcome.RATE_LIMITED) break;
        }
        fetchRemote(web);
    }

    // --- one repo ---

    static Outcome run(Source s, Web web) throws InterruptedException {
        Path download = DataFiles.dir(s, "download");
        try {
            Manifest known = newest(s);
            Map<String, String> headers = new HashMap<>(DataWeb.API_HEADERS);
            if (known != null && known.etag != null && !known.etag.isEmpty()) headers.put("If-None-Match", known.etag);
            Reply commit = web.get(API + s.repo + "/commits?sha=" + s.branch + "&path=" + s.path + "&per_page=1", headers);
            if (commit.status == 304) return Outcome.UP_TO_DATE;
            if (commit.rateLimited()) return limited(s);
            if (!commit.ok()) return failed(s, "commit check answered " + commit.status);
            String sha = newestCommit(commit.text());
            if (sha == null) return failed(s, "the commit list has a shape we do not know");
            if (known != null && sha.equals(known.commit)) return Outcome.UP_TO_DATE;
            String date = commitDate(commit.text());
            if (date == null) return failed(s, "the commit list has a shape we do not know");

            Reply tree = web.get(API + s.repo + "/git/trees/" + sha + ":" + s.path + "?recursive=1", DataWeb.API_HEADERS);
            if (tree.rateLimited()) return limited(s);
            if (!tree.ok()) return failed(s, "file list answered " + tree.status);
            Map<String, String> files = listed(tree.text(), known == null ? 0 : known.files.size(), s.required);
            if (files == null) return failed(s, "the data folder has a layout we do not know");

            DataFiles.delete(download);
            Files.createDirectories(download);
            Manifest made = new Manifest();
            made.commit = sha;
            made.date = date;
            made.etag = commit.etag == null ? "" : commit.etag;
            int fetched = 0, refused = 0;
            for (Map.Entry<String, String> f : files.entrySet()) {
                String path = f.getKey(), blob = f.getValue();
                Path to = download.resolve(path);
                Files.createDirectories(to.getParent());
                Path same = known != null && blob.equals(known.files.get(path)) ? newestDir(s).resolve(path) : null;
                byte[] body;
                if (same != null && Files.isRegularFile(same)) {
                    body = Files.readAllBytes(same);
                } else {
                    Reply r = web.get(RAW + s.repo + "/" + sha + "/" + s.path + "/" + path, DataWeb.PLAIN_HEADERS);
                    if (r.rateLimited()) return limited(s);
                    if (!r.ok()) return failed(s, path + " answered " + r.status);
                    body = r.body;
                    fetched++;
                }
                if (!blob.equals(gitHash(body))) return failed(s, path + " came in broken");
                if (!usable(s, path, body)) {
                    refused++; // this file alone stays the jars
                    continue;
                }
                Files.write(to, body);
                made.files.put(path, blob);
            }
            if (!made.files.containsKey(s.required)) return failed(s, s.required + " can not be used");
            Files.writeString(download.resolve(DataFiles.MANIFEST), GSON.toJson(made));
            Path pending = DataFiles.dir(s, "pending");
            DataFiles.delete(pending);
            move(download, pending);
            SbcMod.LOG.info("Fetched {} of {} game data files from {}, used from the next start{}", fetched, files.size(), s.repo,
                    refused > 0 ? "; " + refused + " kept as they are in the mod, their shape changed" : "");
            return Outcome.UPDATED;
        } catch (IOException | RuntimeException e) {
            return failed(s, e.toString());
        } finally {
            try {
                DataFiles.delete(download);
            } catch (IOException ignored) {
            }
        }
    }

    private static Outcome limited(Source s) {
        SbcMod.LOG.info("GitHub's limit was reached while checking {}, keeping what there is", s.repo);
        return Outcome.RATE_LIMITED;
    }

    private static Outcome failed(Source s, String why) {
        SbcMod.LOG.info("Could not update the game data from {}, keeping what there is: {}", s.repo, why);
        return Outcome.FAILED;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }

    // whats already here, newest first: the copy waiting for next start, or the one in use
    private static Path newestDir(Source s) {
        Path pending = DataFiles.dir(s, "pending");
        return Files.exists(pending.resolve(DataFiles.MANIFEST)) ? pending : DataFiles.dir(s, "current");
    }

    private static Manifest newest(Source s) {
        try {
            Path f = newestDir(s).resolve(DataFiles.MANIFEST);
            if (!Files.exists(f)) return null;
            Manifest m = GSON.fromJson(Files.readString(f), Manifest.class);
            return m == null || m.commit == null || m.files == null ? null : m;
        } catch (Exception e) {
            return null;
        }
    }

    // --- pure parts, for tests ---

    // sha of the first commit in githubs list, or null
    static String newestCommit(String text) {
        try {
            JsonArray a = JsonParser.parseString(text).getAsJsonArray();
            if (a.isEmpty()) return null;
            String sha = a.get(0).getAsJsonObject().get("sha").getAsString();
            return sha.matches("[0-9a-f]{40}") ? sha : null;
        } catch (Exception e) {
            return null;
        }
    }

    // first commits date, or null. a copy gets compared by it with what the jar has, see Bundled
    static String commitDate(String text) {
        try {
            String date = JsonParser.parseString(text).getAsJsonArray().get(0).getAsJsonObject()
                    .getAsJsonObject("commit").getAsJsonObject("committer").get("date").getAsString();
            java.time.Instant.parse(date);
            return date;
        } catch (Exception e) {
            return null;
        }
    }

    // path -> git hash for each json file of a tree listing. null = not a folder we can trust
    static Map<String, String> listed(String text, int knownFiles, String required) {
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (o.has("truncated") && o.get("truncated").getAsBoolean()) return null;
            Map<String, String> out = new LinkedHashMap<>();
            long bytes = 0;
            for (JsonElement e : o.getAsJsonArray("tree")) {
                JsonObject f = e.getAsJsonObject();
                if (!"blob".equals(f.get("type").getAsString())) continue;
                String path = f.get("path").getAsString();
                if (!path.endsWith(".json")) continue;
                if (!safePath(path)) continue;
                out.put(path, f.get("sha").getAsString());
                bytes += f.has("size") ? f.get("size").getAsLong() : 0;
            }
            if (!out.containsKey(required) || out.size() > MAX_FILES || bytes > MAX_BYTES) return null;
            return out.size() * 2 < knownFiles ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    // a name that stays inside the folder its written to whatever a server says
    static boolean safePath(String path) {
        return SAFE_PATH.matcher(path).matches() && !path.contains("..");
    }

    // git hash of a files content, the tree listing has it for every file
    static String gitHash(byte[] body) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-1");
            d.update(("blob " + body.length + "\0").getBytes(StandardCharsets.US_ASCII));
            StringBuilder out = new StringBuilder();
            for (byte b : d.digest(body)) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // can a file replace the jars copy, see misfit
    static boolean usable(Source s, String path, byte[] body) {
        return misfit(s, path, body) == null;
    }

    // why this file cant replace the jars copy, null = it can. must parse + fit the jars layout
    static String misfit(Source s, String path, byte[] body) {
        JsonElement fresh;
        try {
            fresh = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return "it does not parse";
        }
        if (path.equals("regexesModern.json")) {
            boolean ok = fresh.isJsonObject() && fresh.getAsJsonObject().has("regexes") && fresh.getAsJsonObject().get("regexes").isJsonObject();
            return ok ? null : "it has no regexes";
        }
        JsonElement ours = bundled(s, path);
        return ours == null ? null : misfit(ours, fresh);
    }

    // see Shape. a list we have entries in must not come back empty
    static String misfit(JsonElement ours, JsonElement fresh) {
        if (ours.isJsonArray() && !ours.getAsJsonArray().isEmpty() && fresh.isJsonArray() && fresh.getAsJsonArray().isEmpty()) return "the list came back empty";
        return Shape.of(ours).misfit(fresh);
    }

    static boolean sameShape(JsonElement ours, JsonElement fresh) {
        return misfit(ours, fresh) == null;
    }

    private static JsonElement bundled(Source s, String path) {
        String base = path.substring(path.lastIndexOf('/') + 1);
        try (InputStream a = DataUpdater.class.getResourceAsStream(s.jar + path);
             InputStream b = a != null ? null : DataUpdater.class.getResourceAsStream(s.jar + base)) {
            InputStream in = a != null ? a : b;
            return in == null ? null : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    // --- the remote switch file ---

    // one plain GET. answered = replaces our copy next start. not there = nothing is off. anything else = last copy stays
    static void fetchRemote(Web web) throws InterruptedException {
        try {
            Reply r = web.get(REMOTE_URL, DataWeb.PLAIN_HEADERS);
            String text;
            if (r.status == 404) text = "{\"features\":{}}";
            else if (!r.ok()) return;
            else text = r.text();
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) return;
            Path own = DataFiles.root().resolve("own");
            Files.createDirectories(own);
            Path tmp = own.resolve(RemoteSwitch.FILE + ".tmp");
            Files.writeString(tmp, text);
            Files.move(tmp, own.resolve(RemoteSwitch.FILE + ".pending"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            SbcMod.LOG.info("Could not read the remote switch file, keeping what there is: {}", e.toString());
        }
    }

    // what a downloaded folder holds, written inside it last
    static final class Manifest {
        String commit, date, etag;
        Map<String, String> files = new LinkedHashMap<>();
    }
}
