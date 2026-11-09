// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.zmuzy.sbc.SbcMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

// settings, config/sbcompanion/config.json. everything the settings page changes lives here
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIR = dir();

    private static Path dir() {
        try {
            return FabricLoader.getInstance().getConfigDir().resolve("sbcompanion");
        } catch (Throwable t) {
            return java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sbcompanion-test"); // no loader = tests
        }
    }
    private static final Path FILE = DIR.resolve("config.json");

    public static Config I = new Config();


    public int configVersion = 0;
    // protection took V from the radial menu, saved keybinds get moved once after the game read them
    public static boolean keysToMove;
    public boolean hudEnabled = true;
    public int hudX = 6;
    public int hudY = 60;
    public float hudScale = 1.0f;
    // whole hud size, multiplies each elements own size
    public float hudAll = 1.0f;
    // item list next to inventories starts closed as a small tab
    public boolean itemListOpen = false;
    // acc + pet helpers next to their chests: open or folded to a tab
    public boolean helperOpen = true;
    public float classicAlpha = DEFAULT_ALPHA;
    public static final float DEFAULT_ALPHA = 0.78f;
    // per element (see HudLayout): pinned 0/1, x, y, size multiplier. missing = default spot + size
    public java.util.Map<String, float[]> hudPrefs = new java.util.HashMap<>();
    // another mods locks got moved to ours, see SlotLocks.settleWithOtherMod
    public boolean otherLocksImported = false;
    // only read once, from a config from back when there were 2 layouts, see mergeLayouts
    public Boolean compactLayout;
    public java.util.Map<String, float[]> hudPrefsCompact;
    // per element + theme ("classic:minimap"): bg, colors, padding, hidden rows (see ElementStyle)
    public java.util.Map<String, java.util.Map<String, Object>> hudStyles = new java.util.HashMap<>();
    public int hudMaxTasks = 7;
    // profiler overlay: ms per hud element / feature hook per frame
    public boolean perfOverlay = false;
    public boolean hudShowEvents = true;
    public boolean hudInInventory = true;

    // player card top left (portrait, level, pet, purse)
    public boolean playerCard = true;
    public boolean playerCardModel = true;
    // top right plate: sb level, networth, purse, bank
    public boolean infoPanel = true;
    // our scoreboard instead of the vanilla sidebar
    public boolean customBoard = true;
    // custom scoreboard entries in order, empty = default
    public List<String> boardEntries = new ArrayList<>();
    // tab list size + your card order if you moved them
    public float tabScale = 1f;
    public List<String> tabOrder = new ArrayList<>();
    // last stats read from a menu per profile ("Coconut/Ferocity"), for what tab doesnt send
    public java.util.Map<String, String> statMemory = new java.util.HashMap<>();
    // panels ease in + fade instead of jumping
    public boolean hudAnimations = true;
    public List<String> tabHidden = new ArrayList<>();
    public List<String> tabSeen = new ArrayList<>();

    // on/off for features with no own field (id -> switch), missing = on
    public java.util.Map<String, Boolean> features = new java.util.HashMap<>();
    // feature options by "featureId.key" (see Option), only non default values get saved
    public java.util.Map<String, Object> options = new java.util.HashMap<>();
    // moves a whole hud zone (zone -> dx, dy), widgets inside still lay out on their own
    public java.util.Map<String, int[]> zoneNudge = new java.util.HashMap<>();
    public java.util.Map<String, int[]> zoneNudgeCompact;

    // your own chat rules (see ChatFilters)
    public java.util.List<dev.zmuzy.sbc.chat.ChatFilters.Rule> chatRules = new java.util.ArrayList<>();

    // dungeon secret route set: fow, 3ppopka, pearl or normal
    public String routeSet = "fastest";

    // armor dye per piece (head, chest, legs, feet -> rgb), only on your own body
    public java.util.Map<String, Integer> armorDye = new java.util.HashMap<>();

    public boolean alerts = true;
    public int alertMinutesBeforeReset = 60;
    public boolean loginBriefing = true;
    // setup page was shown + closed, doesnt open by itself again
    public boolean setupDone = false;
    // the version this player last saw what's new for. empty with setupDone = they came from the first beta
    public String lastVersion = "";
    // hud editor setup note, "don't show again" ticked. reset layout clears it
    public boolean hudSetupNoteOff = false;
    // start the guided routine by itself the first time you join skyblock each day
    public boolean autoGuide = true;
    // wiki links (chat, mods, F1) open in game instead of your desktop browser
    public boolean inGameBrowser = true;
    // in game browser: bookmarks as "url\ttitle" + last 200 pages
    public List<String> browserBookmarks = new ArrayList<>();
    public List<String> browserHistory = new ArrayList<>();
    public float browserZoom = 1.0f;
    // npc talk as a dialogue box with numbered answers (1-9) instead of chat lines
    public boolean npcDialogue = true;
    public boolean hideNpcChat = true;
    // which routine the guide runs (see RoutinePreset)
    public String routinePreset = "smart";
    public boolean sounds = true;

    public boolean minimap = true;
    public int minimapSize = 128;
    // blocks from center to edge, smaller = more zoomed in
    public int minimapRadius = 64;
    public boolean minimapLabels = true;
    // round minimap that turns with you (false = square, north up)
    public boolean minimapRound = true;
    public boolean minimapRotate = false;
    // dungeons have their own map so the minimap steps aside there
    public boolean minimapHideInDungeon = true;
    // old configs had 2 layouts with their own spots. keep the one in use, every card has just one form now
    static void mergeLayouts(Config c) {
        if (Boolean.TRUE.equals(c.compactLayout)) {
            if (c.hudPrefsCompact != null) c.hudPrefs = c.hudPrefsCompact;
            if (c.zoneNudgeCompact != null) c.zoneNudge = c.zoneNudgeCompact;
        }
        c.compactLayout = null;
        c.hudPrefsCompact = null;
        c.zoneNudgeCompact = null;
        for (java.util.Map<String, Object> style : c.hudStyles.values()) style.remove("compact");
    }


    public boolean pathLine = true;
    public boolean pathThroughWalls = false;
    public int pathColor = 0xFF39D0FF;

    // 0 = pure sb level, 100 = pure money
    public int moneyWeight = 50;
    public double planHours = 2.0;
    public double flipCapital = 0; // 0 = use purse + bank
    public List<String> excludedActivities = new ArrayList<>();
    public List<String> hiddenTasks = new ArrayList<>();

    // iso weekday of the weekly reset, midnight new york time
    public int weeklyResetDay = 7;

    public static void load() {
        try {
            Files.createDirectories(DIR);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not make the config folder", e);
        }
        Config read = read(FILE);
        if (read != null) I = read;
        else if (Files.exists(FILE)) keepFile = !keepBad(FILE);
        fastMaps();
        try {
            migrate();
        } catch (Exception e) {
            // a file that reads but has nulls where lists go is as bad as one that doesnt read
            SbcMod.LOG.warn("Config could not be brought up to date, using defaults (old file kept as config.json.bad)", e);
            keepFile = !keepBad(FILE);
            I = new Config();
            migrate();
        }
        save();
    }

    // gson hands us a tree map and every feature checks its switch a bunch of times a frame
    // string compares down a tree each time is slow, hash map is way faster
    private static void fastMaps() {
        if (!(I.features instanceof java.util.HashMap)) I.features = new java.util.HashMap<>(I.features == null ? java.util.Map.of() : I.features);
        if (!(I.hudPrefs instanceof java.util.HashMap)) I.hudPrefs = new java.util.HashMap<>(I.hudPrefs == null ? java.util.Map.of() : I.hudPrefs);
        if (!(I.hudStyles instanceof java.util.HashMap)) I.hudStyles = new java.util.HashMap<>(I.hudStyles == null ? java.util.Map.of() : I.hudStyles);
        // card styles get read + hashed every frame too
        I.hudStyles.replaceAll((k, v) -> v == null || v instanceof java.util.HashMap ? v : new java.util.HashMap<>(v));
    }

    // file has settings we couldnt read or copy aside, so nothing writes over it
    private static boolean keepFile;

    // whats in the file, null if theres none or it cant be read
    static Config read(Path file) {
        try {
            if (!Files.exists(file)) return null;
            return GSON.fromJson(Files.readString(file), Config.class);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read config, using defaults (old file kept as {}.bad)", file.getFileName(), e);
            return null;
        }
    }

    // puts a copy of an unreadable file next to it. false = that failed so dont write over it
    static boolean keepBad(Path file) {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not keep a copy of {}: it is left as it is and settings are not saved", file.getFileName(), e);
            return false;
        }
    }

    // all of the text or none, the old file stays till the new one is complete
    static void writeAtomic(Path file, String text) throws java.io.IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void migrate() {
        // config v3: everything on by default. a config with every switch off (and nothing on) is old leftovers not a
        // choice, so turn it all back on once
        boolean anyOn = I.features.containsValue(Boolean.TRUE);
        if (I.configVersion < 3 && !anyOn) {
            I.features.clear();
            I.hudEnabled = I.customBoard = I.playerCard = I.infoPanel = true;
            SbcMod.LOG.info("Config migrated to 3.0: all features on");
        }
        if (I.configVersion < 4) I.minimap = true;
        // cards got redrawn with their own sizes, the old sizes + styles would just fight them
        if (I.configVersion < 5) {
            I.hudAll = 1f;
            I.hudScale = 1f;
            I.classicAlpha = DEFAULT_ALPHA;
            I.hudPrefs.clear();
            I.hudStyles.keySet().removeIf(k -> k.startsWith("classic:"));
            I.zoneNudge.clear();
        }
        if (I.configVersion < 6) {
            I.routeSet = "fastest";
            keysToMove = true;
            if (I.minimapSize == 110) I.minimapSize = 128;
            I.playerCard = true;
        }
        if (I.configVersion < 7 && !I.boardEntries.isEmpty() && !I.boardEntries.contains("GROWTH_STAGE")) {
            int cookie = I.boardEntries.indexOf("COOKIE");
            I.boardEntries.add(cookie < 0 ? I.boardEntries.size() : cookie + 1, "GROWTH_STAGE");
        }
        if (I.configVersion < 8) mergeLayouts(I);
        // unclaimedBits got saved off by a bug, back to default (on)
        if (I.configVersion < 9) I.options.remove("board.unclaimedBits");
        // both minimaps north up by default
        if (I.configVersion < 10) I.minimapRotate = false;
        // route line through walls is confusing, behind them by default
        if (I.configVersion < 11) I.pathThroughWalls = false;
        // the visitor box is for new visitors only and starts off
        if (I.configVersion < 12) {
            Object h = I.options.get("garden_visitor_status.highlight");
            if ("Both".equals(h)) I.options.put("garden_visitor_status.highlight", "Name");
            else if ("Color".equals(h)) I.options.put("garden_visitor_status.highlight", "Off");
        }
        I.configVersion = 12;
    }

    // /sbc reset: every feature + hud switch back on, options kept
    public static void resetFeatures() {
        I.features.clear();
        I.hudEnabled = I.customBoard = I.playerCard = I.infoPanel = true;
        save();
    }

    public static synchronized void save() {
        if (keepFile) return;
        try {
            Files.createDirectories(DIR);
            writeAtomic(FILE, dev.zmuzy.sbc.core.data.RemoteSwitch.savedText(GSON, I));
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not save config", e);
        }
    }
}
