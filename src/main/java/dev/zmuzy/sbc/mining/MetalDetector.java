// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni MetalDetectorSolver + MetalDetectorChests.json, MetalDetectorAllToolsAlert, MetalDetectorMute
// from the distance the detector shows in the action bar, find its treasure chest in mines of divan
// skyblocker MetalDetector does the same with its own list so this covers both. chests sit at fixed spots
// from a barrier near the jade crystal
public final class MetalDetector {
    static final String ID = "mining_metal_detector";
    private static final String DETECTOR = "DWARVEN_METAL_DETECTOR";
    private static final String[] TOOLS = {"DWARVEN_LAPIS_SWORD", "DWARVEN_EMERALD_HAMMER", "DWARVEN_GOLD_HAMMER", "DWARVEN_DIAMOND_AXE"};
    private static final Patterns.P FOUND = Patterns.p("SH.mining.crystalnucleus.metaldetector.treasurefound");
    private static final Patterns.P DISTANCE = Patterns.p("SH.mining.crystalnucleus.metaldetector.treasure");

    private static List<Vec3> chests;
    private static final List<Vec3> TREASURE_GUESSES = new ArrayList<>();
    private static Vec3 base, ignore, lastLoc;
    private static long lastSearch = MiningEnv.FAR_PAST, lastFound = MiningEnv.FAR_PAST, lastRead;
    private static boolean playedPling, playedAllTools;
    private static String lastMessage = "", heldBefore = "", heldNow = "";
    private static long lastAllToolsTitle;

    private MetalDetector() {}

    private static synchronized List<Vec3> chests() {
        if (chests != null) return chests;
        List<Vec3> out = new ArrayList<>();
        try (InputStream in = dev.zmuzy.sbc.core.data.DataFiles.sh("MetalDetectorChests.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (JsonElement v : root.getAsJsonArray("locations")) {
                    String[] p = v.getAsString().split(":");
                    out.add(new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
                }
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad MetalDetectorChests.json", e);
        }
        return chests = out;
    }

    // skyhanni chestLocations.forEach: every chest whose block above is exactly this far, to the 1 decimal the
    // action bar has
    static List<Vec3> candidates(List<Vec3> offsets, Vec3 base, Vec3 player, double distance) {
        List<Vec3> out = new ArrayList<>();
        for (Vec3 off : offsets) {
            Vec3 loc = base.subtract(off);
            double d = loc.add(0, 1, 0).distanceTo(player);
            if (Math.round(d * 10) / 10.0 == distance) out.add(loc);
        }
        return out;
    }

    private static boolean hasAllTools() {
        for (String t : TOOLS) if (!MiningPack.inInventory(t)) return false;
        return true;
    }

    private static void say(String text) {
        if (text.equals(lastMessage)) return;
        lastMessage = text;
        MiningPack.chat(text);
    }

    private static void leave() {
        base = ignore = lastLoc = null;
        lastSearch = lastFound = MiningEnv.FAR_PAST;
        TREASURE_GUESSES.clear();
        playedPling = playedAllTools = false;
        lastMessage = "";
        heldBefore = heldNow = "";
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        String held = MiningPack.heldId();
        if (!held.equals(heldNow)) {
            heldBefore = heldNow;
            heldNow = held;
        }
        if (!MiningApi.inCrystalHollows()) return;
        allTools(mc, held);
        if (!MiningApi.inMinesOfDivan()) return;
        Vec3 player = mc.player.position();
        if (TREASURE_GUESSES.size() == 1) {
            if (TREASURE_GUESSES.get(0).distanceToSqr(player) <= 25) {
                ignore = TREASURE_GUESSES.get(0);
                TREASURE_GUESSES.clear();
            }
        } else if (TREASURE_GUESSES.isEmpty() && ignore != null && ignore.distanceToSqr(player) > 100) ignore = null;
        String bar = Vitals.actionBarRaw;
        if (bar == null || Vitals.actionBarAt == lastRead) return;
        lastRead = Vitals.actionBarAt;
        onActionBar(mc, player, bar);
    }

    // from skyhanni MetalDetectorAllToolsAlert
    private static void allTools(Minecraft mc, String held) {
        if (!MiningPack.on(ID, "mining_metal_detector.all_tools") || !DETECTOR.equals(held)) return;
        if (hasAllTools()) {
            if (System.currentTimeMillis() - lastAllToolsTitle >= 1000) {
                lastAllToolsTitle = System.currentTimeMillis();
                MiningEnv.world.title("§cALL TOOLS");
            }
            if (!playedAllTools) {
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f);
                playedAllTools = true;
            }
        } else playedAllTools = false;
    }

    private static void onActionBar(Minecraft mc, Vec3 player, String bar) {
        if (TREASURE_GUESSES.size() == 1) return;
        if (MiningPack.on(ID, "mining_metal_detector.stop_all_tools") && hasAllTools()) return;
        if (!player.equals(lastLoc)) {
            lastLoc = player;
            playedPling = false;
        }
        Matcher m = DISTANCE.matcher(bar);
        if (!m.find()) return;
        double distance;
        try {
            distance = Double.parseDouble(m.group("distance"));
        } catch (NumberFormatException e) {
            return;
        }
        if (base == null) findBase(mc);
        if (base == null) return;
        TREASURE_GUESSES.clear();
        for (Vec3 loc : candidates(chests(), base, player, distance)) {
            if (loc.equals(ignore)) {
                ignore = null;
                TREASURE_GUESSES.clear();
                return;
            }
            TREASURE_GUESSES.add(loc);
        }
        if (!TREASURE_GUESSES.isEmpty() && !playedPling) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
            playedPling = true;
        }
        if (TREASURE_GUESSES.size() == 1) {
            say("Found a treasure chest location");
            return;
        }
        if (MiningEnv.passedSince(lastFound) < 500) return;
        if (TREASURE_GUESSES.isEmpty()) say("No chests found. Try standing still with the metal detector in a different spot.");
        else say(TREASURE_GUESSES.size() + " possible locations found. Please try standing still with the metal detector in a different spot.");
    }

    private static boolean is(Minecraft mc, int x, int y, int z, Block b) {
        return mc.level.getBlockState(new BlockPos(x, y, z)).is(b);
    }

    // skyhanni findBaseCoordinates: quartz stairs with a barrier 13 above, then the barrier with the highest x, y, z
    private static void findBase(Minecraft mc) {
        if (MiningEnv.passedSince(lastSearch) < 15_000) return;
        lastSearch = MiningEnv.now();
        int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
        for (int i = -50; i < 50; i++) {
            for (int j = 30; j >= -30; j--) {
                for (int k = -50; k < 50; k++) {
                    int x = px + i, y = py + j, z = pz + k;
                    if (!is(mc, x, y, z, Blocks.QUARTZ_STAIRS) || !is(mc, x, y + 13, z, Blocks.BARRIER)) continue;
                    int[] at = {x, y + 13, z};
                    boolean changed = true;
                    while (changed) {
                        changed = false;
                        if (is(mc, at[0] + 1, at[1], at[2], Blocks.BARRIER)) {
                            at[0]++;
                            changed = true;
                        }
                        if (is(mc, at[0], at[1] + 1, at[2], Blocks.BARRIER)) {
                            at[1]++;
                            changed = true;
                        }
                        if (is(mc, at[0], at[1], at[2] + 1, Blocks.BARRIER)) {
                            at[2]++;
                            changed = true;
                        }
                    }
                    base = new Vec3(at[0], at[1], at[2]);
                    return;
                }
            }
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!MiningApi.inMinesOfDivan() || !FOUND.matcher(LegacyText.of(line.component())).matches()) return ChatBus.PASS;
        playedPling = false;
        lastMessage = "";
        TREASURE_GUESSES.clear();
        if (MiningPack.on(ID, "mining_metal_detector.time") && !MiningEnv.isFarPast(lastFound))
            MiningPack.chat("§aYou found the treasure in §e" + MiningEnv.passedSince(lastFound) / 1000 + " §aseconds.");
        lastFound = MiningEnv.now();
        return ChatBus.PASS;
    }

    private static void draw(WorldDraw w) {
        if (!MiningApi.inMinesOfDivan() || !MiningPack.on(ID, "mining_metal_detector.solver")) return;
        for (Vec3 p : TREASURE_GUESSES) {
            Vec3 center = p.add(0.5, 0.5, 0.5);
            w.line(w.eye.add(0, -0.3, 0).add(w.mc.player.getLookAngle()), center, 0xFFFFFFFF, 3f, true);
            w.waypointBox(p, 0xFFFF5555, true, true, 0);
            w.dynamicText(center.add(0, 0.5, 0), String.format(Locale.ROOT, "§fTreasure: §e%.1fm", center.distanceTo(w.eye)), 1.5, 0);
        }
    }

    // skyhanni MetalDetectorMute: the note the detector plays while its in your hand or just was
    private static boolean onSound(SoundHooks.Sound s) {
        if (!MiningPack.on(ID, "mining_metal_detector.mute") || !MiningApi.inMinesOfDivan()) return false;
        if (!DETECTOR.equals(heldNow) && !DETECTOR.equals(heldBefore)) return false;
        return s.name().equals("block.note_block.harp");
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Mining", "Metal Detector", "In the Mines of Divan: finds the treasure chest from the Metal Detector's distance, with a line, a box and how far, and warns when you carry all four Keeper tools.", "SkyHanni: Metal Detector Solver, All Tools Alert, Mute Metal Detector; Skyblocker: Metal Detector Helper")
                .when(MiningPack::hollows).tick(MetalDetector::tick).world(MetalDetector::draw).left(MetalDetector::leave)
                .option(Option.bool("mining_metal_detector.solver", "Solver", "", true))
                .option(Option.bool("mining_metal_detector.all_tools", "Alert with all tools", "A title while you hold the detector and carry all four tools.", true))
                .option(Option.bool("mining_metal_detector.stop_all_tools", "Stop after all tools", "", false))
                .option(Option.bool("mining_metal_detector.time", "Say how long it took", "", false))
                .option(Option.bool("mining_metal_detector.mute", "Mute the detector", "", false)));
        ChatBus.register(ChatBus.OBSERVE + 23, ID, f, MetalDetector::onChat);
        SoundHooks.listen(MetalDetector::onSound);
    }
}
