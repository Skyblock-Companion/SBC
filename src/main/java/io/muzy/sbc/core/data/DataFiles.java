// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

// the one reader of data files from other projects repos: the fresh copy DataUpdater downloaded first, else
// the jars. downloads wait in their own folder and get swapped in on the next start
public final class DataFiles {
    // where a file comes from: a copy of SkyHanni-REPO + NEU-REPO constants in our data repo, kept fresh by a
    // daily job there. both MIT
    public enum Source {
        SKYHANNI("skyhanni", "/assets/sbcompanion/data/sh/", "data/skyhanni/constants", "regexesModern.json"),
        NEU("neu", "/assets/sbcompanion/data/neu/", "data/neu/constants", "reforges.json"),
        HYPIXEL("hypixel", "/assets/sbcompanion/data/hypixel/", "data/hypixel", "collections.json");

        final String folder, jar, path, repo = "Skyblock-Companion/sbc-data", branch = "data", required;

        Source(String folder, String jar, String path, String required) {
            this.folder = folder;
            this.jar = jar;
            this.path = path;
            this.required = required;
        }
    }

    static final String MANIFEST = ".manifest.json";
    private static Path root = Config.DIR.resolve("data");
    private static boolean ready;
    private static final Map<Source, Map<String, Path>> BY_PATH = new HashMap<>();
    private static final Map<Source, Map<String, Path>> BY_NAME = new HashMap<>();
    private static final Set<Path> CHECKED = new HashSet<>();
    private static final Set<Source> OLDER = new HashSet<>();
    private static Bundled bundled;

    private DataFiles() {}

    static Path root() {
        return root;
    }

    static Path dir(Source s, String kind) {
        return root.resolve(s.folder).resolve(kind);
    }

    // tests read + write in their own folder, null puts the real one back. forgets what it read
    static synchronized void useRoot(Path r) {
        root = r == null ? Config.DIR.resolve("data") : r;
        forgetAll();
        bundled = null;
    }

    // tests: stands in for data/bundled.json till the next useRoot. forgets what it read
    static synchronized void useBundled(Bundled b) {
        forgetAll();
        bundled = b;
    }

    private static void forgetAll() {
        ready = false;
        BY_PATH.clear();
        BY_NAME.clear();
        CHECKED.clear();
        OLDER.clear();
    }

    private static synchronized Bundled bundled() {
        if (bundled == null) bundled = Bundled.read();
        return bundled;
    }

    // regex keys a release fixed by hand
    static Set<String> pinnedRegexes() {
        return bundled().regexes();
    }

    // downloaded copy of this source is older than the jars
    static synchronized boolean olderThanJar(Source s) {
        ensure();
        return OLDER.contains(s);
    }

    // swaps in waiting downloads + reads what theres. once per game start before the first read
    static synchronized void ensure() {
        if (ready) return;
        ready = true;
        for (Source s : Source.values()) {
            settle(s);
            index(s);
        }
        settleRemote();
    }

    // clears what a crash or half run left, a complete pending copy becomes current
    private static void settle(Source s) {
        Path current = dir(s, "current"), pending = dir(s, "pending"), old = dir(s, "old");
        try {
            delete(dir(s, "download"));
            if (Files.isDirectory(old)) {
                if (Files.isDirectory(current)) delete(old);
                else Files.move(old, current);
            }
            if (!Files.isDirectory(pending)) return;
            if (!Files.exists(pending.resolve(MANIFEST))) {
                delete(pending);
                return;
            }
            if (Files.isDirectory(current)) Files.move(current, old);
            try {
                Files.move(pending, current);
            } catch (IOException e) {
                if (Files.isDirectory(old)) Files.move(old, current);
                throw e;
            }
            delete(old);
            SbcMod.LOG.info("Game data from {} updated", s.repo);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not put the new game data of {} in place, keeping the last copy: {}", s.repo, e.toString());
        }
    }

    private static void settleRemote() {
        Path own = root.resolve("own"), waiting = own.resolve(RemoteSwitch.FILE + ".pending");
        try {
            if (Files.exists(waiting)) Files.move(waiting, own.resolve(RemoteSwitch.FILE), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not put the new remote switch file in place: {}", e.toString());
        }
    }

    private static void index(Source s) {
        Map<String, Path> paths = new HashMap<>(), names = new HashMap<>();
        BY_PATH.put(s, paths);
        BY_NAME.put(s, names);
        Path current = dir(s, "current");
        if (!Files.exists(current.resolve(MANIFEST))) return;
        boolean older = bundled().older(s, commitDate(current.resolve(MANIFEST)));
        if (older) {
            OLDER.add(s);
            SbcMod.LOG.info("The downloaded game data of {} is older than the mod's, the mod's copy is used until the next update", s.repo);
        }
        try (Stream<Path> files = Files.walk(current)) {
            for (Path f : files.filter(Files::isRegularFile).sorted().toList()) {
                String rel = current.relativize(f).toString().replace('\\', '/');
                if (rel.equals(MANIFEST) || bundled().pinned(s, rel)) continue;
                if (older && inJar(s, rel)) continue;
                paths.put(rel, f);
                names.putIfAbsent(f.getFileName().toString(), f);
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the downloaded game data of {}", s.repo, e);
            paths.clear();
            names.clear();
        }
    }

    private static String commitDate(Path manifest) {
        try {
            DataUpdater.Manifest m = new Gson().fromJson(Files.readString(manifest), DataUpdater.Manifest.class);
            return m == null ? null : m.date;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean inJar(Source s, String file) {
        try (InputStream in = jarStream(s, file)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }

    private static synchronized Path find(Source s, String file) {
        ensure();
        Path p = BY_PATH.get(s).get(file);
        if (p == null) p = BY_NAME.get(s).get(baseName(file));
        if (p == null || !CHECKED.add(p)) return p;
        String why;
        try {
            why = DataUpdater.misfit(s, dir(s, "current").relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
        } catch (IOException e) {
            why = e.toString();
        }
        if (why == null) return p;
        SbcMod.LOG.warn("Downloaded {} no longer fits, using the copy in the mod: {}", p.getFileName(), why);
        drop(s, p);
        return null;
    }

    // dont read this downloaded file again till next start
    private static void drop(Source s, Path p) {
        BY_PATH.get(s).values().remove(p);
        BY_NAME.get(s).values().remove(p);
    }

    private static String baseName(String file) {
        return file.substring(file.lastIndexOf('/') + 1);
    }

    // theres a downloaded copy of this source at all
    public static synchronized boolean hasFresh(Source s) {
        ensure();
        return !BY_PATH.get(s).isEmpty();
    }

    // this file comes from the download not the jar
    public static boolean isFresh(Source s, String file) {
        return find(s, file) != null;
    }

    // file as a stream, null if neither copy has it. caller closes it
    public static InputStream stream(Source s, String file) {
        Path p = find(s, file);
        if (p != null) {
            try {
                return Files.newInputStream(p);
            } catch (IOException e) {
                SbcMod.LOG.warn("Could not read {} from the downloaded game data, using the copy in the mod", file, e);
            }
        }
        return jarStream(s, file);
    }

    private static InputStream jarStream(Source s, String file) {
        InputStream in = DataFiles.class.getResourceAsStream(s.jar + file);
        return in != null ? in : DataFiles.class.getResourceAsStream(s.jar + baseName(file));
    }

    // skyhanni repo file, fresh or jar
    public static InputStream sh(String file) {
        return stream(Source.SKYHANNI, file);
    }

    // neu repo file, fresh or jar
    public static InputStream neu(String file) {
        return stream(Source.NEU, file);
    }

    // file as text, null if neither copy has it
    public static String text(Source s, String file) {
        try (InputStream in = stream(s, file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    // parsed file. an unreadable download falls back to the jar, null if neither
    public static JsonObject object(Source s, String file) {
        String text = text(s, file);
        try {
            JsonElement e = text == null ? null : JsonParser.parseString(text);
            if (e != null && e.isJsonObject()) return e.getAsJsonObject();
        } catch (Exception ignored) {
        }
        try (InputStream in = jarStream(s, file)) {
            if (in == null) return null;
            JsonElement e = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // gives a file to a reader that walks it by hand. if it fails on the download the jar copy gets read instead,
    // so a change the layout check missed still leaves good data. true = a reader finished
    public static boolean read(Source s, String file, Consumer<JsonObject> reader) {
        Path p = find(s, file);
        if (p != null) {
            try {
                reader.accept(JsonParser.parseString(Files.readString(p)).getAsJsonObject());
                return true;
            } catch (Exception e) {
                SbcMod.LOG.warn("Could not read the downloaded {}, using the copy in the mod: {}", file, e.toString());
                synchronized (DataFiles.class) {
                    drop(s, p);
                }
            }
        }
        try (InputStream in = jarStream(s, file)) {
            if (in == null) return false;
            reader.accept(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            return true;
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read {}", file, e);
            return false;
        }
    }

    // downloaded files in one folder of a source by name, empty without a download
    public static synchronized List<String> listFresh(Source s, String folder) {
        ensure();
        List<String> out = new ArrayList<>();
        String prefix = folder + "/";
        for (String p : BY_PATH.get(s).keySet())
            if (p.startsWith(prefix) && p.indexOf('/', prefix.length()) < 0) out.add(p.substring(prefix.length()));
        out.sort(Comparator.naturalOrder());
        return out;
    }

    static void delete(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path p : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
