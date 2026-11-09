// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.pests;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenExtras;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.mc.SoundHooks;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni PestSpawn, PestSpawnSound, PestFinder's "no pests" title + teleport, PestApi vacuum mute +
// pest kill: what happens when a pest comes, and the key to the nearest infested plot
// that key sends /plottp once, only bc you pressed it
public final class PestAlerts {
    public static final String ID = "garden_pest_spawn";
    private static final List<Consumer<Integer>> SPAWN = new ArrayList<>();
    private static final List<Consumer<String>> KILL = new ArrayList<>();

    // listeners for the pest amount, null when hypixel doesnt say
    public static void onSpawn(Consumer<Integer> listener) {
        SPAWN.add(listener);
    }

    // listeners for a kill, gets the pest name
    public static void onKill(Consumer<String> listener) {
        KILL.add(listener);
    }

    private static KeyMapping teleportKey;
    private static long lastKey, lastSpawn, lastCustomSound;
    private static final java.util.Set<String> OWN_PESTS = java.util.Set.of("TORRHUS_CANYON");

    private PestAlerts() {}

    // a spawn line in chat read like PestSpawn reads it. {amount or null, plot} or null, a player's line doesnt match
    static Object[] spawn(String clean) {
        for (String key : new String[]{"SH.garden.pests.spawn.one.colorless.0", "SH.garden.pests.spawn.one.colorless.1"}) {
            Matcher m = Patterns.get(key).matcher(clean);
            if (m.find()) return new Object[]{1, m.group("plot")};
        }
        for (String key : new String[]{"SH.garden.pests.spawn.multiple.colorless.0", "SH.garden.pests.spawn.multiple.colorless.1"}) {
            Matcher m = Patterns.get(key).matcher(clean);
            if (m.find()) return new Object[]{Integer.parseInt(m.group("amount")), m.group("plot")};
        }
        Matcher m = Patterns.get("SH.garden.pests.spawn.offline.colorless").matcher(clean);
        if (m.find()) return new Object[]{null, m.group("plots")};
        return null;
    }

    // "1 Pest" / "2 Pests" like skyhanni writes them
    static String spawnMessage(int amount, String plot) {
        return "§e" + amount + " §a" + (amount == 1 ? "Pest" : "Pests") + " Spawned in §b" + plot + "§a!";
    }

    // PestProfitTracker.checkPestChats: mice and lunar moths send more than one drop line per kill, an
    // overclocker isnt a kill
    static boolean countsAsKill(String pest, String item) {
        return switch (pest) {
            case "Field Mouse" -> item.equals("Dung");
            case "Lunar Moth" -> item.equals("Enchanted Sunflower");
            default -> !item.equals("Overclocker 3000");
        };
    }

    // the reading half, for PestTimer and MantidKills
    // skyhanni's PestSpawn fires its event whatever its own settings say, so this doesnt depend on the alerts switch
    static void read(String clean, String coloured) {
        Matcher kill = Patterns.get("SH.garden.pests-api.chat.pest-death").matcher(coloured);
        if (kill.matches() && countsAsKill(kill.group("pest"), GameState.strip(kill.group("item")))) for (Consumer<String> l : KILL) l.accept(kill.group("pest"));
        if (kill.matches()) dev.zmuzy.sbc.garden.plot.PlotPests.killed();
        if (Patterns.get("SH.garden.pests-api.chat.no-pests").matcher(coloured).matches()) dev.zmuzy.sbc.garden.plot.PlotPests.reset();
        Object[] spawn = spawn(clean);
        if (spawn != null) {
            lastSpawn = System.currentTimeMillis();
            dev.zmuzy.sbc.garden.plot.PlotPests.spawned((Integer) spawn[0], (String) spawn[1]);
            for (Consumer<Integer> l : SPAWN) l.accept((Integer) spawn[0]);
        }
    }

    private static ChatBus.Result onRead(ChatBus.Line line) {
        if (GardenPack.garden()) read(GameState.strip(line.plain()), LegacyText.of(line.component()));
        return ChatBus.PASS;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!GardenPack.garden()) return ChatBus.PASS;
        String clean = GameState.strip(line.plain());
        String coloured = LegacyText.of(line.component());
        if (Patterns.get("SH.garden.pests-api.chat.no-pests").matcher(coloured).matches()) {
            if (GardenPack.on(ID, "no_pests_title")) GardenPack.title("§eNo pests!", 2);
            if (GardenPack.on(ID, "hide_no_pests")) return ChatBus.CANCEL;
        }
        String format = GardenPack.choice(ID, "chat", "Hypixel");
        Object[] spawn = spawn(clean);
        if (spawn != null) {
            if (spawn[0] == null) return ChatBus.PASS;
            String plot = (String) spawn[1], message = spawnMessage((Integer) spawn[0], plot);
            if (GardenPack.on(ID, "title")) GardenPack.title(message, 7);
            if (format.equals("Compact")) {
                String target = plot.equals("The Barn") ? "barn" : plot;
                Style st = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/plottp " + target)).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /plottp " + target)));
                var mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[SBC] §r" + message).withStyle(st));
            }
            return format.equals("Hypixel") ? ChatBus.PASS : ChatBus.CANCEL;
        }
        if (!format.equals("Hypixel") && System.currentTimeMillis() - lastSpawn < 1000 && Patterns.get("SH.garden.pests.spawn.teleport.colorless").matcher(clean).matches()) return ChatBus.CANCEL;
        return ChatBus.PASS;
    }

    // PestSpawnSound.isPestSpawnSound
    static boolean isSpawnSound(String name, double distance, float volume, float pitch) {
        return name.equals("block.note_block.bass") && distance < 15 && volume == 1f && pitch == 1.4920635f;
    }

    // IslandTypeTag.HAS_OWN_PESTS
    private static boolean hasOwnPests() {
        return GardenPack.garden() || OWN_PESTS.contains(GameState.island);
    }

    private static boolean onSound(SoundHooks.Sound s) {
        if (!Features.on(ID)) return false;
        if (GardenPack.on(ID, "mute_vacuum") && s.name().equals("entity.wither.shoot") && hasOwnPests()) return true;
        if (!GardenPack.garden()) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String mode = GardenPack.choice(ID, "sound", "Hypixel");
        if (mode.equals("Hypixel") || !isSpawnSound(s.name(), Math.sqrt(mc.player.distanceToSqr(s.x(), s.y(), s.z())), s.volume(), s.pitch())) return false;
        if (mode.equals("Our sound")) {
            long now = System.currentTimeMillis();
            if (now - lastCustomSound >= 5000) {
                lastCustomSound = now;
                // skyhanni's default custom sound: its one note 3 times, 150ms apart
                for (int i = 0; i < 3; i++) delayed.add(new long[]{now + i * 150L});
            }
        }
        return true;
    }

    private static final List<long[]> delayed = new ArrayList<>();

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        delayed.removeIf(d -> {
            if (d[0] > now) return false;
            GardenPack.sound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.4920635f);
            return true;
        });
        while (teleportKey != null && teleportKey.consumeClick()) if (mc.gui.screen() == null && GardenPack.garden() && now - lastKey >= 2000) {
            lastKey = now;
            teleportNearest();
        }
    }

    // PestFinder.teleportNearestInfestedPlot. plot numbers and middles from the tab + the grid
    static void teleportNearest() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!GardenPack.garden()) {
            GardenPack.chat("§cThis only works on your Garden.");
            return;
        }
        Vec3 here = mc.player.position();
        int best = -1;
        double dist = Double.MAX_VALUE;
        boolean skipSingle = GardenPack.on("garden_pest_finder", "ignore_single");
        for (int plot : GardenExtras.infestedPlots()) {
            if (skipSingle && dev.zmuzy.sbc.garden.plot.PlotPests.single(plot)) continue;
            Vec3 c = GardenExtras.plotCenter(plot);
            if (c == null) continue;
            double d = c.distanceToSqr(here.x, c.y, here.z);
            if (d < dist) {
                dist = d;
                best = plot;
            }
        }
        if (best < 0) {
            if (GardenPack.on("garden_pest_finder", "back_to_garden")) GardenPack.command("warp garden");
            else GardenPack.chat("§cNo infested plots to warp to.");
            return;
        }
        Vec3 c = GardenExtras.plotCenter(best);
        if (Math.abs(here.x - c.x) <= 48 && Math.abs(here.z - c.z) <= 48 && !GardenPack.on("garden_pest_finder", "always_tp")) {
            GardenPack.chat("§cYou are already on an infested plot.");
            return;
        }
        String cmd = dev.zmuzy.sbc.ui.panels.GardenPlots.commandForNumber(best);
        GardenPack.command(cmd);
    }

    public static void keys(KeyMapping.Category category) {
        teleportKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.pest_teleport", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Pest spawn title and sound", "A title when pests spawn, how the spawn line reads in chat and what the spawn sounds like.", "SkyHanni: Pest Spawn")
                .when(GardenPack::garden)
                .option(Option.bool("garden_pest_spawn.title", "Title when a pest spawns", "How many and on which plot.", true))
                .option(Option.choice("garden_pest_spawn.chat", "Spawn line in chat", "Hypixel's own, a short one you can click to go there, or none.", "Hypixel", "Hypixel", "Compact", "Hidden"))
                .option(Option.choice("garden_pest_spawn.sound", "Spawn sound", "Hypixel's own, none, or a sound of ours three times.", "Hypixel", "Hypixel", "Muted", "Our sound"))
                .option(Option.bool("garden_pest_spawn.no_pests_title", "Title when there are no pests", "When Hypixel says your Garden has none.", false))
                .option(Option.bool("garden_pest_spawn.hide_no_pests", "Hide the no pests line", "Hypixel's line when your Garden has none.", false))
                .option(Option.bool("garden_pest_spawn.mute_vacuum", "Mute the vacuum", "The sound of its right click ability.", false)));
        ChatBus.register(ChatBus.FILTER + 4, "garden_pest_read", null, PestAlerts::onRead);
        ChatBus.register(ChatBus.FILTER + 5, ID, f, PestAlerts::onChat);
        SoundHooks.listen(PestAlerts::onSound);
        Safe.endTick("PestAlerts.tick", PestAlerts::tick);
        Safe.commands("PestAlerts.commands", (d, r) -> d.register(literal("sbc").then(literal("tpinfested").executes(c -> {
            teleportNearest();
            return 1;
        }))));
        dev.zmuzy.sbc.core.Commands.describe("tpinfested", "Teleport to the nearest infested plot");
    }
}
