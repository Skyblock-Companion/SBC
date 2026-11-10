// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// shortcuts, speed presets, todos, reminders. config/sbcompanion/commands.json, local only
public final class Saved {
    public static final class Data {
        // skyblocker Shortcuts: "/from" -> "/to". keys: "key.keyboard.x + key.keyboard.y" -> a command or chat line
        public Map<String, String> shortcutCommands = new LinkedHashMap<>(), shortcutArgs = new LinkedHashMap<>(), shortcutKeys = new LinkedHashMap<>();
        // skyblocker SpeedPresets. null until first asked, then the defaults get written out
        public Map<String, Integer> speedPresets;
        public List<Todo> todos = new ArrayList<>();
        public List<Reminders.Reminder> reminders = new ArrayList<>();
        // OpenLastStorage, per profile
        public Map<String, LastStorage.Last> lastStorage = new HashMap<>();
        // FriendApi + GuildApi per account: name -> best friend, and guild member names
        public Map<String, Map<String, Boolean>> friends = new HashMap<>();
        public Map<String, List<String>> guilds = new HashMap<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data;

    private Saved() {}

    private static Path file() {
        return Config.DIR.resolve("commands.json");
    }

    public static synchronized Data get() {
        if (data == null) {
            try {
                if (Files.exists(file())) data = GSON.fromJson(Files.readString(file()), Data.class);
            } catch (Exception e) {
                SbcMod.LOG.warn("Could not read commands.json", e);
            }
            if (data == null) data = new Data();
        }
        return data;
    }

    public static synchronized void save() {
        if (data == null) return;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(data));
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not write commands.json", e);
        }
    }
}
