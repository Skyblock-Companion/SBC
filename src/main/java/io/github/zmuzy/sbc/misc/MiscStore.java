// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// what this packages small helpers remember between sessions: config/sbcompanion/misc.json
public final class MiscStore {
    private static final Gson GSON = new GsonBuilder().create();
    private static MiscStore instance;

    // from skyhanni LimboTimeTracker storage: total secs in limbo + longest single stay
    public long limboSeconds, limboBest;
    // profile -> boost -> what the enchanted time clock said
    public Map<String, Map<String, EnchantedClock.Status>> clock = new HashMap<>();
    // from skyhanni SpiderDenRelicPathfinder: relics found per profile
    public Map<String, java.util.Set<String>> relics = new HashMap<>();

    // custom item looks by item uuid
    public io.github.zmuzy.sbc.item.custom.CustomLooks.Data customLooks = new io.github.zmuzy.sbc.item.custom.CustomLooks.Data();

    private MiscStore() {}

    private static Path file() {
        return Config.DIR.resolve("misc.json");
    }

    public static synchronized MiscStore get() {
        if (instance != null) return instance;
        instance = new MiscStore();
        try {
            Path f = file();
            if (Files.exists(f)) {
                MiscStore read = GSON.fromJson(Files.readString(f), MiscStore.class);
                if (read != null) instance = read;
            }
        } catch (Throwable t) {
            SbcMod.LOG.warn("could not read misc.json", t);
        }
        return instance;
    }

    public static synchronized void save() {
        if (instance == null) return;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(instance));
        } catch (Throwable t) {
            SbcMod.LOG.warn("could not write misc.json", t);
        }
    }
}
