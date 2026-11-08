// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.data.DataFiles;
import dev.zmuzy.sbc.core.data.RegexCheck;
import dev.zmuzy.sbc.nav.Graph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// skyhannis data repo (MIT): chat + menu regex patterns and every islands nav graph. all goes through
// DataFiles: our own fresh copy, then the jar copy
public final class Repo {
    private static final Path ROOT = Config.DIR.resolveSibling("skyhanni/repo/constants");
    private static final String BUNDLED = "/assets/sbcompanion/data/sh/";
    private static final Map<String, java.util.Optional<Pattern>> regex = new ConcurrentHashMap<>();
    private static final Map<String, Graph> graphs = new ConcurrentHashMap<>();
    public static boolean available;

    private Repo() {}

    public static void load() {
        boolean own = DataFiles.hasFresh(DataFiles.Source.SKYHANNI);
        available = own || Files.isDirectory(ROOT) || Repo.class.getResource(BUNDLED + "island_graphs/HUB.json") != null;
        SbcMod.LOG.info("Island graphs and constants: {}", own ? "our own download" : Files.isDirectory(ROOT) ? "SkyHanni's folder" : "the copy in the mod");
    }

    // another mods copy of a file, only while we dont have our own
    private static Path foreign(String file) {
        if (DataFiles.hasFresh(DataFiles.Source.SKYHANNI)) return null;
        Path f = ROOT.resolve(file);
        return Files.exists(f) ? f : null;
    }

    private static String read(String file) throws java.io.IOException {
        Path f = foreign(file);
        return f != null ? Files.readString(f) : DataFiles.text(DataFiles.Source.SKYHANNI, file);
    }

    // pattern by repo key for plain text, color codes dropped. null = no such key
    public static Pattern pattern(String key) {
        return regex.computeIfAbsent(key, k -> {
            String full = RegexCheck.PREFIX + k;
            return java.util.Optional.ofNullable(Patterns.has(full) ? RegexCheck.plain(Patterns.text(full, "")) : null);
        }).orElse(null);
    }

    // nav graph for an island key (DWARVEN_MINES), or null
    public static Graph graph(String island) {
        if (!available || island == null || island.isEmpty()) return null;
        return graphs.computeIfAbsent(island, k -> {
            try {
                String text = read("island_graphs/" + k + ".json");
                if (text == null) return Graph.EMPTY;
                return Graph.parse(k, JsonParser.parseString(text).getAsJsonObject());
            } catch (Exception e) {
                SbcMod.LOG.warn("Bad island graph {}", k, e);
                return Graph.EMPTY;
            }
        });
    }

    // one of the repos constants files (SeaCreatures.json) as json, null if missing
    public static JsonObject constant(String file) {
        if (!available) return null;
        try {
            String text = read(file);
            return text == null ? null : JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    // repo file as text, fresh or from the jar, or null
    public static String bundled(String file) {
        return DataFiles.text(DataFiles.Source.SKYHANNI, file);
    }

    public static List<String> graphIslands() {
        if (!available) return List.of();
        List<String> own = DataFiles.listFresh(DataFiles.Source.SKYHANNI, "island_graphs");
        if (!own.isEmpty()) return own.stream().filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5)).toList();
        if (!Files.isDirectory(ROOT.resolve("island_graphs"))) {
            String index = bundled("island_graphs/index.txt");
            return index == null ? List.of() : index.lines().filter(l -> !l.isBlank()).toList();
        }
        try (Stream<Path> s = Files.list(ROOT.resolve("island_graphs"))) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
