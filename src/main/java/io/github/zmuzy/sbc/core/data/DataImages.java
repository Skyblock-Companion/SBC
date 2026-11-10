// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.Net;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// pictures we dont ship to keep the jar small: they live in our data repo and each player keeps a copy in
// config/sbcompanion/data_images. the repos manifest.json lists every file + its hash
public final class DataImages {
    // the data repo, raw
    static final String BASE = "https://raw.githubusercontent.com/Skyblock-Companion/sbc-data/main/";
    private static final String MANIFEST = "manifest.json";
    private static final Map<String, CompletableFuture<Path>> FETCHING = new ConcurrentHashMap<>();
    private static CompletableFuture<Map<String, String>> manifest;

    private DataImages() {}

    private static Path dir() {
        return Config.DIR.resolve("data_images");
    }

    // "islands/hub.png" -> its sha256. pure, for tests
    static Map<String, String> parseManifest(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonObject files = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("files");
        files.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
        return out;
    }

    static boolean matches(byte[] bytes, String sha256) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equalsIgnoreCase(sha256);
        } catch (Exception e) {
            return false;
        }
    }

    // only paths the manifest could really have, so nothing weird in a downloaded list can reach outside the
    // folder. pure, for tests
    static boolean safePath(String path) {
        return path.matches("[a-z_]+/[a-z0-9_]+\\.png");
    }

    // the repos list, or last times saved one if it cant be reached
    private static synchronized CompletableFuture<Map<String, String>> manifest() {
        if (manifest == null) {
            manifest = Net.getBytes(BASE + MANIFEST).handle((bytes, e) -> {
                Path saved = dir().resolve(MANIFEST);
                try {
                    if (e != null) throw e;
                    String text = new String(bytes, StandardCharsets.UTF_8);
                    Map<String, String> files = parseManifest(text);
                    Files.createDirectories(dir());
                    Files.writeString(saved, text);
                    return files;
                } catch (Throwable problem) {
                    SbcMod.LOG.info("Could not read the list of pictures from the data repository: {}", problem.toString());
                    try {
                        return Files.exists(saved) ? parseManifest(Files.readString(saved)) : Map.<String, String>of();
                    } catch (Exception bad) {
                        return Map.<String, String>of();
                    }
                }
            });
        }
        return manifest;
    }

    // picture on disk, fetched first when missing or changed. fails = next start tries again
    public static CompletableFuture<Path> fetch(String path) {
        return FETCHING.computeIfAbsent(path, p -> manifest().thenCompose(files -> {
            String want = files.get(p);
            if (want == null || !safePath(p)) return CompletableFuture.failedFuture(new IllegalStateException("not in the data repository: " + p));
            Path file = dir().resolve(p);
            try {
                if (Files.exists(file) && matches(Files.readAllBytes(file), want)) return CompletableFuture.completedFuture(file);
            } catch (Exception ignored) {
                // read again below
            }
            return download(p, file, want).exceptionallyCompose(first -> download(p, file, want));
        }));
    }

    private static CompletableFuture<Path> download(String path, Path file, String want) {
        return Net.getBytes(BASE + path).thenApply(bytes -> {
            if (!matches(bytes, want)) throw new IllegalStateException("not the expected file: " + path);
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(tmp, bytes);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return file;
        });
    }

    // fetches every picture one by one at game start off the game thread, so none waits till its first looked at
    public static void prefetchAll() {
        Thread t = new Thread(() -> {
            int failed = 0;
            List<String> paths = List.copyOf(manifest().join().keySet());
            for (String p : paths) {
                try {
                    fetch(p).join();
                } catch (Exception e) {
                    failed++;
                }
            }
            if (failed > 0) SbcMod.LOG.info("Could not fetch {} of {} pictures", failed, paths.size());
        }, "SBC pictures");
        t.setDaemon(true);
        t.start();
    }
}
