// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyCubed; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

// real sb island maps from skycubeds data in meowdding-repo: a drawn image per island with world bounds +
// points of interest. that data is CC BY-NC-SA 4.0 not GPL, so it lives in the data repo not the jar
public final class SkyMaps {
    public record Poi(String name, Vec3 pos, String type, String texture) {}

    // head texture (base64 profile property) from a skin url
    public static String headTexture(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url.replace("https://", "http://") + "\"}}}";
        return java.util.Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static final class IslandMap {
        public String island, url;
        public int topX, topY, bottomX, bottomY;
        public final List<Poi> pois = new ArrayList<>();
        public int width, height;
        public Identifier texture;
        // map pixels (kept for the round minimap), null = unloaded
        public NativeImage image;
        volatile boolean loading;
        volatile String error = "";

        public double u(double worldX) {
            return (worldX - topX) / (double) (bottomX - topX) * width;
        }

        public double v(double worldZ) {
            return (worldZ - topY) / (double) (bottomY - topY) * height;
        }
    }

    private static final Path CACHE = Config.DIR.resolve("maps");
    private static final Map<String, IslandMap> maps = new LinkedHashMap<>();
    private static final Map<String, String> ALIAS = Map.of("SPIDERS_DEN", "SPIDER_DEN", "THE_BARN", "THE_FARMING_ISLANDS",
            "JERRYS_WORKSHOP", "WINTER");
    private static final List<String> loadedOrder = new ArrayList<>();

    private SkyMaps() {}

    private static final Path DATA = Config.DIR.resolve("maps_data");
    private static final String RAW = "https://raw.githubusercontent.com/Skyblock-Companion/sbc-data/data/data/maps/";
    private static final long REFRESH_MS = 24L * 3600 * 1000;
    private static final String IMAGE_INDEX = "/assets/sbcompanion/data/maps/image_index.json";
    private static volatile Map<String, String> bundledImages;
    // the island map files
    private static final List<String> FILES = List.of("backwater.json", "crystal_hollows.json", "dungeon_hub.json", "dwarves.json", "jerrys_workshop.json", "lotus_atoll.json", "main.json", "rift.json", "safari.json");

    public static void load() {
        try {
            for (String name : FILES) {
                Path f = DATA.resolve(name);
                if (Files.exists(f)) parseAll(Files.readString(f));
            }
            SbcMod.LOG.info("Loaded {} SkyBlock island maps", maps.size());
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the island maps", e);
        }
        for (String name : FILES) fetch(name);
    }

    private static void parseAll(String text) {
        JsonElement root = JsonParser.parseString(text);
        List<JsonObject> entries = new ArrayList<>();
        if (root.isJsonArray()) root.getAsJsonArray().forEach(e -> entries.add(e.getAsJsonObject()));
        else if (root.isJsonObject()) entries.add(root.getAsJsonObject());
        for (JsonObject o : entries) parse(o);
    }

    // fetch a data file now? always if theres none (the jar has no copy), when a day old with updates on. pure, for tests
    static boolean stale(boolean ours, long ageMs, boolean updatesOn) {
        return !ours || (updatesOn && ageMs > REFRESH_MS);
    }

    private static void fetch(String name) {
        Path f = DATA.resolve(name);
        try {
            boolean ours = Files.exists(f);
            long age = ours ? System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() : 0;
            if (!stale(ours, age, io.muzy.sbc.core.data.GameData.updatesOn())) return;
        } catch (Exception e) {
            return;
        }
        Net.getBytes(RAW + name).whenComplete((bytes, e) -> {
            if (e != null) {
                SbcMod.LOG.info("Could not download the island map {}, keeping what there is: {}", name, e.toString());
                return;
            }
            try {
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                JsonParser.parseString(text); // never swap good data for a broken download
                Files.createDirectories(DATA);
                Path tmp = DATA.resolve(name + ".tmp");
                Files.writeString(tmp, text);
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Minecraft.getInstance().execute(() -> parseAll(text));
            } catch (Exception ex) {
                SbcMod.LOG.info("Could not save the island map {}: {}", name, ex.toString());
            }
        });
    }

    private static void parse(JsonObject o) {
        if (!o.has("island") || !o.has("default")) return;
        IslandMap m = new IslandMap();
        String isl = o.get("island").getAsString();
        m.island = ALIAS.getOrDefault(isl, isl);
        m.url = o.get("default").getAsString();
        m.topX = o.get("topX").getAsInt();
        m.topY = o.get("topY").getAsInt();
        m.bottomX = o.get("bottomX").getAsInt();
        m.bottomY = o.get("bottomY").getAsInt();
        if (o.has("pois")) for (JsonElement pe : o.getAsJsonArray("pois")) {
            try {
                JsonObject p = pe.getAsJsonObject();
                JsonObject pos = p.getAsJsonObject("position");
                if (pos == null || !p.has("name")) continue;
                m.pois.add(new Poi(p.get("name").getAsString().replaceAll("<[^>]*>", "").trim(), new Vec3(pos.get("x").getAsDouble(), pos.get("y").getAsDouble(), pos.get("z").getAsDouble()),
                        p.has("type") ? p.get("type").getAsString() : "poi", p.has("texture") ? headTexture(p.get("texture").getAsString()) : null));
            } catch (Exception ignored) {
            }
        }
        maps.put(m.island, m);
    }

    public static IslandMap get(String island) {
        return maps.get(island);
    }

    public static List<IslandMap> all() {
        return new ArrayList<>(maps.values());
    }

    // an islands map texture, loading (and downloading once) in the background. null till ready
    public static IslandMap ready(String island) {
        IslandMap m = maps.get(island);
        if (m == null) return null;
        if (m.texture != null) return m;
        if (!m.loading && m.error.isEmpty()) startLoad(m);
        return null;
    }

    public static String status(String island) {
        IslandMap m = maps.get(island);
        if (m == null) return "No drawn map for this island";
        if (!m.error.isEmpty()) return m.error;
        return m.texture != null ? "" : "Loading map…";
    }

    // the data repo file for an image url, null = none
    static String bundledImage(String url) {
        return bundled().get(url);
    }

    private static Map<String, String> bundled() {
        if (bundledImages == null) {
            Map<String, String> out = new HashMap<>();
            try (InputStream in = SkyMaps.class.getResourceAsStream(IMAGE_INDEX)) {
                if (in != null) JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet()
                        .forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            } catch (Exception e) {
                SbcMod.LOG.warn("Could not read the list of map images", e);
            }
            bundledImages = out;
        }
        return bundledImages;
    }

    private static void startLoad(IslandMap m) {
        m.loading = true;
        String ours = bundledImage(m.url);
        if (ours != null) {
            // big pictures: read off the game thread, only the upload happens on it
            io.muzy.sbc.core.data.DataImages.fetch("maps/" + ours).whenComplete((file, problem) -> {
                if (problem != null) {
                    m.loading = false;
                    m.error = "Could not download the map image";
                    return;
                }
                try (InputStream in = Files.newInputStream(file)) {
                    NativeImage img = NativeImage.read(in);
                    Minecraft.getInstance().execute(() -> upload(m, img));
                } catch (Exception e) {
                    m.loading = false;
                    m.error = "Map image could not be read";
                }
            });
            return;
        }
        Path file = CACHE.resolve(sha1(m.url) + ".png");
        if (Files.exists(file)) {
            Minecraft.getInstance().execute(() -> upload(m, file));
            return;
        }
        Net.getBytes(m.url).whenComplete((bytes, e) -> {
            if (e != null) {
                m.loading = false;
                m.error = "Could not download the map image";
                return;
            }
            try {
                Files.createDirectories(CACHE);
                Files.write(file, bytes);
                Minecraft.getInstance().execute(() -> upload(m, file));
            } catch (Exception ex) {
                m.loading = false;
                m.error = "Could not save the map image";
            }
        });
    }

    private static void upload(IslandMap m, Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            upload(m, NativeImage.read(in));
        } catch (Exception e) {
            m.loading = false;
            m.error = "Map image could not be read";
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
            }
        }
    }

    private static void upload(IslandMap m, NativeImage img) {
        try {
            m.width = img.getWidth();
            m.height = img.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath("sbcompanion", "skymap/" + m.island.toLowerCase(Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "sbcompanion map " + m.island, img));
            m.texture = id;
            m.image = img;
            loadedOrder.remove(m.island);
            loadedOrder.add(m.island);
            // keep max 3 big map textures in memory
            while (loadedOrder.size() > 3) {
                IslandMap old = maps.get(loadedOrder.remove(0));
                if (old != null && old.texture != null) {
                    Minecraft.getInstance().getTextureManager().release(old.texture);
                    old.texture = null;
                    old.image = null;
                }
            }
        } catch (Exception e) {
            m.error = "Map image could not be read";
        } finally {
            m.loading = false;
        }
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes()));
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    // lowercase display name for an island key
    public static String key(String island) {
        return island == null ? "" : island;
    }

    static Map<String, IslandMap> raw() {
        return new HashMap<>(maps);
    }
}
