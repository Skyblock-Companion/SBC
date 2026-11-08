// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.item.StorageCache;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

// what the panels remember per profile, one small file each in config/sbcompanion/panels. follows the profile
// like skyblockers GardenPlots + MuseumItemCache do
final class PanelStore {
    static final class Data {
        // configure plots menu as last seen, see GardenPlots
        GardenPlots.Plot[] plots = new GardenPlots.Plot[25];
        // favourite loadout numbers (from 0, over all pages)
        Set<Integer> loadoutFavourites = new HashSet<>();
        // favourite wardrobe slots (from 1, over all pages) + whether only those show
        Set<Integer> wardrobeFavourites = new HashSet<>();
        boolean wardrobeOnlyFavourites;
        // last museum api read, 0 = never
        long museumResync;
        // api answered for this profile at least once
        boolean museumApi;
        // item + set ids in the museum, like MuseumItemCache collectedItemIds
        Set<String> museumCollected = new HashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static Data data = new Data();
    private static String key = "";

    private PanelStore() {}

    static Data data() {
        return data;
    }

    // account + profile, empty till the game says which profile
    static String key() {
        return key;
    }

    // "uuid_profile". profile id comes on every join, name from the tab till then
    private static String currentKey(Minecraft mc) {
        String profile = StorageCache.profileId();
        if (profile.isEmpty()) profile = GameState.profileName == null ? "" : GameState.profileName;
        if (profile.isEmpty()) return "";
        return mc.getUser().getProfileId().toString().replace("-", "") + "_" + profile.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static Path file(String k) {
        return Config.DIR.resolve("panels").resolve(k + ".json");
    }

    static void tick(Minecraft mc) {
        String now = currentKey(mc);
        if (now.isEmpty() || now.equals(key)) return;
        save();
        key = now;
        data = read(file(now));
        GardenPlots.changed();
        MuseumData.profileChanged();
    }

    private static Data read(Path f) {
        try {
            if (Files.exists(f)) {
                Data d = GSON.fromJson(Files.readString(f), Data.class);
                if (d != null) {
                    if (d.plots == null || d.plots.length != 25) d.plots = java.util.Arrays.copyOf(d.plots == null ? new GardenPlots.Plot[25] : d.plots, 25);
                    if (d.loadoutFavourites == null) d.loadoutFavourites = new HashSet<>();
                    if (d.museumCollected == null) d.museumCollected = new HashSet<>();
                    if (d.wardrobeFavourites == null) d.wardrobeFavourites = new HashSet<>();
                    return d;
                }
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read {}", f.getFileName(), e);
        }
        return new Data();
    }

    static void save() {
        if (key.isEmpty()) return;
        try {
            Path f = file(key);
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(data));
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not save the panels' data", e);
        }
    }

    // for pictures + tests
    static void useForTests(Data d) {
        data = d;
    }
}
