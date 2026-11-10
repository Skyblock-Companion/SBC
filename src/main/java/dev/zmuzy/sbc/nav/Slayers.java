// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.nav;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.Bling;
import dev.zmuzy.sbc.ui.DripKit;
import dev.zmuzy.sbc.ui.Toasts;
import dev.zmuzy.sbc.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// slayers, display only: quest from the sidebar, the boss found by the armor stand with your name, a box
// around it, kill time vs your best (per boss + tier)
public final class Slayers {
    private static final dev.zmuzy.sbc.core.Patterns.P BOSS = dev.zmuzy.sbc.core.Patterns.p("Slayers.BOSS");
    private static final dev.zmuzy.sbc.core.Patterns.P XP_LEFT = dev.zmuzy.sbc.core.Patterns.p("Slayers.XP_LEFT");
    private static final Map<String, EntityType<?>> MOB = Map.of(
            "Revenant Horror", EntityTypes.ZOMBIE, "Atoned Horror", EntityTypes.ZOMBIE, "Tarantula Broodfather", EntityTypes.SPIDER, "Conjoined Brood", EntityTypes.SPIDER,
            "Sven Packmaster", EntityTypes.WOLF, "Voidgloom Seraph", EntityTypes.ENDERMAN, "Inferno Demonlord", EntityTypes.BLAZE,
            "Riftstalker Bloodfiend", EntityTypes.PLAYER, "Bloodfiend", EntityTypes.PLAYER);
    private static final String[] TIERS = {"", "I", "II", "III", "IV", "V"};
    private static final int[] XP_PER_TIER = {5, 25, 100, 500, 1500};
    private static final long BEACON_MS = 5000;

    private static String bossName = "", tier = "";
    private static boolean questOn, spawned;
    private static long spawnAt, deathAt, lastTitle;
    private static ArmorStand stand;
    private static LivingEntity boss;
    private static final Map<BlockPos, Long> beacons = new HashMap<>();
    private static final Map<String, Long> best = new HashMap<>();
    private static final Map<String, Integer> xpLeft = new HashMap<>(), levels = new HashMap<>();
    // the best the last kill was up against, to show what a new best beat. -1 = none
    private static long bestBefore = -1;
    private static boolean loaded;
    private Slayers() {}

    private static Path file() {
        return Config.DIR.resolve("slayer_best.json");
    }

    private static void load() {
        loaded = true;
        try {
            if (!Files.exists(file())) return;
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            for (var e : o.entrySet()) {
                if (e.getKey().startsWith("xp:")) xpLeft.put(e.getKey().substring(3), e.getValue().getAsInt());
                else if (e.getKey().startsWith("lvl:")) levels.put(e.getKey().substring(4), e.getValue().getAsInt());
                else best.put(e.getKey(), e.getValue().getAsLong());
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read slayer times", e);
        }
    }

    private static void save() {
        try {
            JsonObject o = new JsonObject();
            best.forEach(o::addProperty);
            xpLeft.forEach((k, v) -> o.addProperty("xp:" + k, v));
            levels.forEach((k, v) -> o.addProperty("lvl:" + k, v));
            Files.createDirectories(Config.DIR);
            Files.writeString(file(), o.toString());
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not write slayer times", e);
        }
    }

    private static String key() {
        return bossName + " " + tier;
    }

    public static String fmt(long ms) {
        return String.format(Locale.ROOT, "%.2fs", ms / 1000.0);
    }

    private static boolean valid(ArmorStand a) {
        return a.isAlive() && !a.getName().getString().endsWith(" 0❤");
    }

    private static void title(Minecraft mc, String text, boolean sound) {
        long now = System.currentTimeMillis();
        if (now - lastTitle < 1500) return;
        lastTitle = now;
        mc.gui.hud.setTimes(2, 20, 8);
        mc.gui.hud.setTitle(Component.literal(text));
        if (sound && mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.8f);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String t = line.plain().trim(); // hypixel indents these lines
        Minecraft mc = Minecraft.getInstance();
        if (t.equals("Your Slayer Quest has been cancelled!") || t.equals("SLAYER QUEST FAILED!")) {
            reset();
        } else if (t.equals("NICE! SLAYER BOSS SLAIN!") || t.equals("SLAYER QUEST COMPLETE!")) {
            if (spawned && spawnAt != 0 && deathAt == 0) {
                deathAt = System.currentTimeMillis();
                long took = deathAt - spawnAt;
                if (!loaded) load();
                Long old = best.get(key());
                bestBefore = old == null ? -1 : old;
                if (old == null || took < old) {
                    best.put(key(), took);
                    save();
                    Toasts.push("New best: " + fmt(took), bossName + " " + tier + (old == null ? "" : "  (was " + fmt(old) + ")"), dev.zmuzy.sbc.ui.theme.Theme.current().accent());
                } else {
                    Toasts.push("Slain in " + fmt(took), bossName + " " + tier + "  (best " + fmt(old) + ")", dev.zmuzy.sbc.ui.theme.Theme.current().text());
                }
            }
            beacons.clear();
        } else {
            Matcher m = XP_LEFT.matcher(t);
            if (m.matches()) {
                if (!loaded) load();
                String type = m.group(1);
                xpLeft.put(type, m.group(3) == null ? -1 : Integer.parseInt(m.group(3).replace(",", "")));
                levels.put(type, Integer.parseInt(m.group(2)));
                save();
            }
        }
        return ChatBus.PASS;
    }

    private static void reset() {
        questOn = spawned = false;
        spawnAt = deathAt = 0;
        bossName = tier = "";
        stand = null;
        boss = null;
        beacons.clear();
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.tickCount % 5 != 0) return;
        if (!loaded) load();
        boolean on = false, up = false;
        for (String l : GameState.scoreboard) {
            Matcher m = BOSS.matcher(l);
            if (m.find()) {
                on = true;
                String t = m.group(2) == null ? "" : m.group(2);
                if (!m.group(1).equals(bossName) || !t.equals(tier)) {
                    reset();
                    bossName = m.group(1);
                    tier = t;
                }
            } else if (l.equals("Slay the boss!")) {
                up = true;
            }
        }
        if (!on) {
            if (questOn) reset();
            return;
        }
        questOn = true;
        // auto slayer starts the next quest by itself, the kill stays on the card till its kills or the next boss show
        if (spawned && deathAt != 0 && System.currentTimeMillis() - deathAt > 3000 && (up || nextQuestRunning())) {
            spawned = false;
            deathAt = 0;
        }
        if (up && !spawned) {
            spawned = true;
            spawnAt = System.currentTimeMillis();
            deathAt = 0;
            if (dev.zmuzy.sbc.combat.Combat.opt("slayers", "boss_alert")) title(mc, "§cBoss spawned", true);
        } else if (!up && spawned && deathAt == 0 && (stand == null || !stand.isAlive())) {
            spawned = false;
        }
        findBoss(mc);
        if (bossName.startsWith("Voidgloom")) scanBeacons(mc);
        else beacons.clear();
    }

    private static boolean nextQuestRunning() {
        var progress = dev.zmuzy.sbc.core.Patterns.get("SH.slayer.bosswarning.progress");
        for (String l : GameState.scoreboard) {
            String t = l.trim();
            if (progress.matcher(t).matches() && (t.endsWith(" Kills") || t.endsWith("Combat XP") || t.endsWith(" Slayer"))) return true;
        }
        return false;
    }

    private static void findBoss(Minecraft mc) {
        if (stand != null && valid(stand) && boss != null && boss.isAlive()) return;
        stand = null;
        boss = null;
        String me = mc.getUser().getName();
        AABB area = mc.player.getBoundingBox().inflate(40);
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, area, x -> x.hasCustomName() && valid(x))) {
            if (!BOSS.matcher(a.getName().getString()).find()) continue;
            boolean mine = false;
            for (ArmorStand o : mc.level.getEntitiesOfClass(ArmorStand.class, a.getBoundingBox().inflate(0.1, 1.5, 0.1), x -> x != a && x.hasCustomName()))
                if (o.getName().getString().contains(me)) mine = true;
            if (!mine) continue;
            stand = a;
            Matcher m = BOSS.matcher(a.getName().getString());
            m.find();
            EntityType<?> type = MOB.getOrDefault(m.group(1), EntityTypes.ZOMBIE);
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, a.getBoundingBox().inflate(0, 1.5, 0), x -> x.getType() == type && x.isAlive() && x != mc.player)) {
                double d = e.distanceToSqr(a);
                if (d < bd) {
                    bd = d;
                    boss = e;
                }
            }
            return;
        }
    }

    private static void scanBeacons(Minecraft mc) {
        if (boss == null) return;
        BlockPos c = boss.blockPosition();
        long now = System.currentTimeMillis();
        beacons.values().removeIf(t -> now - t > BEACON_MS + 500);
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++)
                for (int y = -4; y <= 4; y++) {
                    BlockPos p = c.offset(x, y, z);
                    if (mc.level.getBlockState(p).is(Blocks.BEACON) && !beacons.containsKey(p)) {
                        beacons.put(p.immutable(), now);
                        if (!dev.zmuzy.sbc.combat.EndermanSlayer.ownsBeacons()) title(mc, "§cYang Glyph!", true);
                    }
                }
    }

    private static void draw(WorldDraw w) {
        if (!questOn) return;
        int color = dev.zmuzy.sbc.combat.Combat.color("slayers", "color", 0xFFFF3B3B);
        if (dev.zmuzy.sbc.combat.Combat.choice("slayers", "highlight", "Nothing").equals("Box")) {
            if (boss != null && boss.isAlive() && !bossName.contains("Bloodfiend")) w.box(boss.getBoundingBox().inflate(0.05), color, false, false);
            else if (stand != null && stand.isAlive()) w.box(new AABB(stand.blockPosition()).inflate(0.4, 1.2, 0.4).move(0, -1.2, 0), color, false, false);
        }
        if (dev.zmuzy.sbc.combat.EndermanSlayer.ownsBeacons()) return;
        long now = System.currentTimeMillis();
        for (var e : beacons.entrySet()) {
            long left = BEACON_MS - (now - e.getValue());
            if (left < 0) continue;
            w.box(new AABB(e.getKey()), 0xFFFF3B3B, true, true);
            w.label(String.format(Locale.ROOT, "§b%.1fs", left / 1000.0), Vec3.atCenterOf(e.getKey().above()), true);
        }
    }

    // ---- hud widget ----

    public static boolean active() {
        return questOn;
    }

    private static String type() {
        return switch (bossName) {
            case "Revenant Horror", "Atoned Horror" -> "Zombie";
            case "Tarantula Broodfather", "Conjoined Brood" -> "Spider";
            case "Sven Packmaster" -> "Wolf";
            case "Voidgloom Seraph" -> "Enderman";
            case "Inferno Demonlord" -> "Blaze";
            case "Riftstalker Bloodfiend", "Bloodfiend" -> "Vampire";
            default -> "";
        };
    }

    // xp one boss of this tier gives with mayor perks, -1 = unknown (the table has no riftstalker)
    private static double xpPerBoss() {
        int ti = java.util.Arrays.asList(TIERS).indexOf(tier) - 1;
        if (type().equals("Vampire") || ti < 0 || ti >= XP_PER_TIER.length) return -1;
        double buff = 1.0;
        for (var p : Mayor.perks) {
            if (p.name().equals("Work Smarter")) buff *= 1.5;
            if (p.name().equals("Slayer XP Buff")) buff *= 1.25;
        }
        return XP_PER_TIER[ti] * buff;
    }

    // what the api says about your own slayer level when one of your profiles is loaded
    private static dev.zmuzy.sbc.core.Profile.Level apiLevel(String type) {
        var d = dev.zmuzy.sbc.core.Profile.current;
        var me = Minecraft.getInstance().player;
        if (d == null || me == null || !me.getUUID().toString().replace("-", "").equalsIgnoreCase(d.uuid)) return null;
        return d.slayers.get(switch (type) {
            case "Zombie" -> "Revenant";
            case "Spider" -> "Tarantula";
            case "Wolf" -> "Sven";
            case "Enderman" -> "Voidgloom";
            case "Blaze" -> "Inferno";
            case "Vampire" -> "Riftstalker";
            default -> "";
        });
    }

    // whats left of the boss per its name tag ("1.2M❤", "33 Hits"), empty = no tag in reach
    private static String health() {
        if (stand == null || !stand.isAlive()) return "";
        String name = GameState.strip(stand.getName().getString());
        Matcher m = BOSS.matcher(name);
        return m.find() ? name.substring(m.end()).trim() : "";
    }

    public enum Phase {WAITING, FIGHTING, SLAIN}

    // quest like the slayer card shows it. times in ms, -1 = unknown. time = this fight, target = what its held
    // against (your best or the best it just beat)
    public record Quest(String boss, String tier, Phase phase, long time, long target, boolean newBest, long best, String health, int glyphs, long glyphLeft,
                        boolean maxed, int level, long xpLeft, long xpPrev, long xpNext, int bosses, long xpEach) {}

    public static final long GLYPH_MS = BEACON_MS;

    public static Quest quest() {
        long now = System.currentTimeMillis();
        Phase phase = !spawned ? Phase.WAITING : deathAt != 0 ? Phase.SLAIN : Phase.FIGHTING;
        long time = phase == Phase.WAITING ? -1 : (phase == Phase.SLAIN ? deathAt : now) - spawnAt;
        Long b = best.get(key()), told = dev.zmuzy.sbc.combat.CombatData.get().slayerBests.get(key());
        if (told != null && (b == null || told < b)) b = told;
        long bestMs = b == null ? -1 : b;
        boolean newBest = phase == Phase.SLAIN && bestMs == time;
        long target = phase == Phase.WAITING ? -1 : newBest ? bestBefore : bestMs;
        int glyphs = 0;
        long glyphLeft = -1;
        for (long at : beacons.values()) {
            long left = BEACON_MS - (now - at);
            if (left < 0) continue;
            glyphs++;
            glyphLeft = glyphLeft < 0 ? left : Math.min(glyphLeft, left);
        }
        String type = type();
        var api = apiLevel(type);
        Integer chatXp = xpLeft.get(type), chatLevel = levels.get(type);
        int[] table = dev.zmuzy.sbc.core.Profile.SLAYER_XP.get(type.toLowerCase(Locale.ROOT));
        int level = chatLevel != null ? chatLevel : api != null ? api.level() : -1;
        boolean maxed = chatXp != null ? chatXp < 0 : api != null && table != null && api.level() >= table.length;
        long left = maxed ? -1 : chatXp != null ? chatXp : api != null && api.progress() >= 0 ? Math.round(api.toNext()) : -1;
        long next = !maxed && table != null && level >= 0 && level < table.length ? table[level] : -1;
        long prev = next >= 0 && level > 0 ? table[level - 1] : 0;
        double each = xpPerBoss();
        int bosses = left > 0 && each > 0 ? (int) Math.ceil(left / each) : -1;
        return new Quest(bossName, tier, phase, time, target, newBest, bestMs, phase == Phase.FIGHTING ? health() : "", glyphs, glyphLeft, maxed, level, left, prev, next, bosses, each > 0 ? Math.round(each) : -1);
    }

    public static void register() {
        Feature f = Features.add(new Feature("slayers", "Slayer", "Slayer helper", "Times the kill against your best, counts bosses to the next level and marks Voidgloom beacons. It can also mark your boss.", "Skyblocker: slayers · SkyHanni: slayer displays")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tick(Slayers::tick).world(Slayers::draw)
                .option(dev.zmuzy.sbc.feature.Option.bool("slayers.boss_alert", "Boss spawn alert", "A title and a sound when your boss spawns.", true))
                .option(dev.zmuzy.sbc.feature.Option.choice("slayers.highlight", "Mark your boss with", "", "Nothing", "Nothing", "Box", "Glow"))
                .option(dev.zmuzy.sbc.feature.Option.color("slayers.color", "Mark colour", "", 0xFFFF3B3B))
                .option(dev.zmuzy.sbc.feature.Option.bool("slayers.hide_away", "Hide while you do something else", "The card goes after 30 seconds away from your slayer's area with no kills for the quest, and comes back when you are.", true)));
        dev.zmuzy.sbc.combat.CombatMarks.glowing(mark -> {
            if (Features.on("slayers") && questOn && boss != null && boss.isAlive() && dev.zmuzy.sbc.combat.Combat.choice("slayers", "highlight", "Nothing").equals("Glow")) mark.accept(boss, dev.zmuzy.sbc.combat.Combat.color("slayers", "color", 0xFFFF3B3B));
        });
        ChatBus.register(ChatBus.OBSERVE + 12, "slayers", f, Slayers::onChat);
    }
}
