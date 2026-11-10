// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.dungeon.DungeonMarks;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// riftstalker bloodfiend in stillgore chateau. from skyhanni VampireSlayerFeatures + skyblocker
// ManiaIndicator, StakeIndicator, TwinClawsIndicator, HealingMelonIndicator
// your boss, a coop members and one you hit share one highlight color here
// the titles live here (not in nav/Slayers) bc this has its own switch
// skyhannis "Spawned by" regexes live in its code
public final class VampireSlayer {
    public static final String ID = "vampire_slayer", ICHOR = "vampire_ichor";
    private static final Pattern SPAWNED_BY = Pattern.compile(".*§(?:\\d|\\w)+Spawned by: §(?:\\d|\\w)(\\w*).*");
    private static final Pattern TWINCLAWS = Pattern.compile(".*(?:§(?:\\d|\\w))+TWINCLAWS (?:§(?:\\w|\\d))+[0-9.,]+s.*");

    private static final Map<Integer, Integer> marked = new HashMap<>();
    private static final Set<Integer> tagged = new HashSet<>();
    private static final List<Player> highlighted = new ArrayList<>();
    private static final List<ArmorStand> ichors = new ArrayList<>(), springs = new ArrayList<>();
    private static final Map<ArmorStand, Player> standBoss = new HashMap<>();
    private static long nextClaw, clawAt, lastSpawnSound, lastMelon;

    private VampireSlayer() {}

    private static boolean inChateau() {
        return "THE_RIFT".equals(GameState.island) && ("Stillgore Château".equals(GameState.area) || "Oubliette".equals(GameState.area));
    }

    private static List<String> coop() {
        Feature f = Features.get(ID);
        var o = f == null ? null : f.option("coop_members");
        String text = o == null ? "" : o.text();
        return text.isBlank() ? List.of() : Arrays.asList(text.split(","));
    }

    private static boolean spawnedByCoop(List<String> coop, String formatted) {
        Matcher m = SPAWNED_BY.matcher(formatted);
        return m.matches() && coop.contains(m.group(1));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (clawAt != 0 && now >= clawAt) {
            clawAt = 0;
            if (now > nextClaw) {
                Combat.title("§6§lTWINCLAWS", 34);
                Combat.pling(1f);
                nextClaw = now + 5000;
            }
        }
        if (mc.player.tickCount % 5 != 0) return;
        marked.clear();
        highlighted.clear();
        ichors.clear();
        springs.clear();
        standBoss.keySet().removeIf(a -> !a.isAlive());
        if (Features.on(ID)) {
            for (Player p : mc.level.players()) {
                if (p == mc.player || p.getUUID().version() == 4 || !p.getName().getString().equals("Bloodfiend ")) continue;
                if (p.position().distanceTo(mc.player.position()) <= 15) process(mc, p);
            }
            mania(mc);
        }
        if (Features.on(ICHOR)) {
            String ichor = DungeonMarks.skull("BLOOD_ICHOR"), spring = DungeonMarks.skull("KILLER_SPRING");
            for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
                String t = DungeonMarks.headTexture(a.getItemBySlot(EquipmentSlot.HEAD));
                if (t.isEmpty()) continue;
                if (t.equals(ichor)) ichors.add(a);
                else if (t.equals(spring)) springs.add(a);
            }
        }
    }

    // skyblocker ManiaIndicator: your floor says if this is the safe spot, only your own bosses tag counts
    private static void mania(Minecraft mc) {
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.getName().getString().equals("Bloodfiend ")) continue;
            boolean mine = false;
            for (ArmorStand tag : Combat.tagsInRadius(p, "Spawned by")) if (LegacyText.of(tag.getName(), true, false).contains(mc.getUser().getName())) mine = true;
            if (!mine) continue;
            for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, p.getBoundingBox().inflate(0.1, 2.5, 0.1), ArmorStand::hasCustomName)) {
                String n = a.getName().getString();
                if (n.contains("MANIA") && Combat.opt(ID, "mania_title")) {
                    boolean green = mc.level.getBlockState(mc.player.blockPosition().below()).is(Blocks.DYED_TERRACOTTA.green());
                    Combat.title(green ? "§aMANIA - stand here" : "§cMANIA - find green", 10);
                } else if (n.contains("҉") && Combat.opt(ID, "stake_title")) {
                    Combat.title("§cSteak Stake now", 10);
                }
            }
        }
    }

    // from skyhanni RemotePlayer.process
    private static void process(Minecraft mc, Player p) {
        String me = mc.getUser().getName();
        List<String> coop = coop();
        boolean isTagged = tagged.contains(p.getId());
        for (ArmorStand tag : Combat.tagsInRadius(p, "Spawned by")) {
            String name = LegacyText.of(tag.getName(), true, false);
            boolean mine = name.contains(me), ofCoop = spawnedByCoop(coop, name);
            if (mine) {
                tagged.remove(p.getId());
                isTagged = false;
            }
            boolean canSteak = Combat.health(p) <= Combat.maxHealth(p) * 0.2f;
            boolean own = Combat.opt(ID, "own") && mine, other = Combat.opt(ID, "others") && isTagged, co = Combat.opt(ID, "coop") && ofCoop;
            if (own || other || co) {
                highlighted.add(p);
                if (Combat.opt(ID, "highlight")) marked.put(p.getId(), canSteak && Combat.opt(ID, "low_health") ? Combat.color(ID, "steak_color", 0xFFFF0058) : Combat.color(ID, "color", 0xFF00FF58));
                if (canSteak && Combat.opt(ID, "steak_alert")) Combat.title("§c§lSTEAK!", 6);
            }
        }
        if (!Combat.opt(ID, "twinclaws")) return;
        for (ArmorStand tag : Combat.tagsInRadius(p, "TWINCLAWS")) {
            if (!TWINCLAWS.matcher(LegacyText.of(tag.getName(), true, false)).matches()) continue;
            boolean mine = false, ofCoop = false;
            for (ArmorStand by : Combat.tagsInRadius(p, "Spawned by")) {
                String n = LegacyText.of(by.getName(), true, false);
                mine |= n.contains(me);
                ofCoop |= spawnedByCoop(coop, n);
            }
            if ((mine && Combat.opt(ID, "own")) || (ofCoop && Combat.opt(ID, "coop")) || (isTagged && Combat.opt(ID, "others")))
                if (clawAt == 0) clawAt = System.currentTimeMillis() + Combat.integer(ID, "twinclaws_delay", 0);
        }
    }

    // skyhanni onEntityClick: a boss you hit thats not yours or a coop members
    private static void onAttack(net.minecraft.world.entity.Entity e) {
        if (!Features.on(ID) || !(e instanceof Player p) || p.getUUID().version() == 4) return;
        var mc = Minecraft.getInstance();
        List<String> coop = coop();
        for (ArmorStand a : Combat.tagsInRadius(p, "Spawned by")) {
            String n = LegacyText.of(a.getName(), true, false);
            if (n.contains(mc.getUser().getName()) || spawnedByCoop(coop, n)) return;
            tagged.add(p.getId());
        }
    }

    private static void draw(WorldDraw w) {
        if (Features.on(ID) && Combat.opt(ID, "line")) {
            int color = Combat.color(ID, "line_color", 0xFFFF0058);
            float width = Combat.integer(ID, "line_width", 1);
            for (Player p : highlighted) if (Combat.seen(p, 15, 0.5)) w.line(Combat.fromCrosshair(w), p.position().add(0, 1.54, 0), color, width, true);
        }
        if (!Features.on(ICHOR)) return;
        int alpha = Combat.integer(ICHOR, "alpha", 80);
        if (Combat.opt(ICHOR, "ichor")) for (ArmorStand a : ichors) drawStand(w, a, "§4Ichor", Combat.color(ICHOR, "ichor_color", 0xFF640058), alpha, Combat.opt(ICHOR, "ichor_lines"), Combat.color(ICHOR, "ichor_line_color", 0xFFFF0D00));
        if (Combat.opt(ICHOR, "spring")) for (ArmorStand a : springs) drawStand(w, a, "§4Spring", Combat.color(ICHOR, "spring_color", 0xFF640058), alpha, Combat.opt(ICHOR, "spring_lines"), Combat.color(ICHOR, "spring_line_color", 0xFFFF0D00));
        if (Combat.opt(ICHOR, "beam")) for (ArmorStand a : ichors) if (a.isAlive()) w.beacon(a.position().add(0, -2, 0), Combat.color(ICHOR, "ichor_color", 0xFF640058), false);
    }

    private static void drawStand(WorldDraw w, ArmorStand a, String text, int color, int alpha, boolean lines, int lineColor) {
        if (!a.isAlive() || a.position().distanceTo(w.eye) > 15) return;
        BlockPos p = a.blockPosition();
        w.filledBox(new AABB(p).move(0, 1, 0), color & 0xFFFFFF, alpha / 100f, false);
        w.filledBox(new AABB(p.above(2)), 0xAA0000, 1f, false);
        if (Combat.seen(Vec3.atCenterOf(p.above(2)), 64)) w.label(text, Vec3.atLowerCornerOf(p).add(0.5, 2.5, 0.5), false);
        if (!lines) return;
        Player boss = standBoss.get(a);
        if (boss != null && Combat.seen(a, 64, 1.5)) w.line(boss.getEyePosition(), a.getEyePosition(), lineColor, 3, true);
    }

    // skyhanni onParticle: enchant sparks from a boss you follow to a stand tie that stand to the boss
    private static void onParticle(io.muzy.sbc.mc.ParticleHooks.Particle p) {
        if (!Features.on(ICHOR) || !inChateau() || !p.is("minecraft:enchant")) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        AABB box = new AABB(p.x() - 3, p.y() - 3, p.z() - 3, p.x() + 3, p.y() + 3, p.z() + 3);
        for (Player boss : level.getEntitiesOfClass(Player.class, box)) {
            if (!highlighted.contains(boss)) continue;
            for (ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, box)) if (ichors.contains(a) || springs.contains(a)) standBoss.put(a, boss);
        }
    }

    // skyhanni onPlaySound: killer spring plays this many times in 1 tick
    private static boolean onSound(SoundHooks.Sound s) {
        if (!inChateau() || !Features.on(ICHOR) || !Combat.opt(ICHOR, "fix_sound") || !s.name().equals("entity.wither.spawn")) return false;
        long now = System.currentTimeMillis();
        if (now - lastSpawnSound < 50) return true;
        lastSpawnSound = now;
        return false;
    }

    private static void left() {
        marked.clear();
        tagged.clear();
        highlighted.clear();
        ichors.clear();
        springs.clear();
        standBoss.clear();
        clawAt = 0;
    }

    static void register() {
        Features.add(new Feature("vampire_tick", "Slayer", "Riftstalker background", "Keeps the Riftstalker helpers running.", "").alwaysOn().when(VampireSlayer::inChateau).tick(VampireSlayer::tick).world(VampireSlayer::draw).left(VampireSlayer::left));
        Features.add(new Feature(ID, "Slayer", "Riftstalker Bloodfiend", "Highlights your boss, titles for Twinclaws, Steak and Mania.", "SkyHanni: Vampire Slayer · Skyblocker: Mania, Holy Ice and Steak Stake Indicators").when(VampireSlayer::inChateau)
                .option(Option.bool("vampire_slayer.own", "Your boss", "", true))
                .option(Option.bool("vampire_slayer.others", "A boss you hit", "", true))
                .option(Option.bool("vampire_slayer.coop", "Your co-op's bosses", "", true))
                .option(Option.text("vampire_slayer.coop_members", "Co-op members", "Their names with commas between.", ""))
                .option(Option.bool("vampire_slayer.highlight", "Highlight", "A glow on those bosses where you can see them.", true))
                .option(Option.color("vampire_slayer.color", "Highlight colour", "", 0xFF00FF58))
                .option(Option.bool("vampire_slayer.low_health", "Other colour at Steak health", "", true))
                .option(Option.color("vampire_slayer.steak_color", "Steak colour", "", 0xFFFF0058))
                .option(Option.bool("vampire_slayer.steak_alert", "Steak title", "When it is at a fifth of its health.", true))
                .option(Option.bool("vampire_slayer.twinclaws", "Twinclaws title", "", true))
                .option(Option.integer("vampire_slayer.twinclaws_delay", "Twinclaws title delay (ms)", "", 0, 0, 500))
                .option(Option.bool("vampire_slayer.mania_title", "Mania title", "Says whether you stand on green.", true))
                .option(Option.bool("vampire_slayer.stake_title", "Steak Stake title", "When your boss's tag has the mark.", true))
                .option(Option.bool("vampire_slayer.line", "Line to it", "", false))
                .option(Option.color("vampire_slayer.line_color", "Line colour", "", 0xFFFF0058))
                .option(Option.integer("vampire_slayer.line_width", "Line width", "", 1, 1, 10)));
        Features.add(new Feature(ICHOR, "Slayer", "Blood Ichor and Killer Spring", "Marks them near you, a beam on an Ichor from further away, and fixes the Spring's sound spam.", "SkyHanni: Blood Ichor, Killer Spring").when(VampireSlayer::inChateau)
                .option(Option.bool("vampire_ichor.ichor", "Blood Ichor", "", true))
                .option(Option.color("vampire_ichor.ichor_color", "Blood Ichor colour", "", 0xFF640058))
                .option(Option.bool("vampire_ichor.beam", "Blood Ichor beam", "", true))
                .option(Option.bool("vampire_ichor.ichor_lines", "Lines from the boss to an Ichor", "", false))
                .option(Option.color("vampire_ichor.ichor_line_color", "Ichor line colour", "", 0xFFFF0D00))
                .option(Option.bool("vampire_ichor.spring", "Killer Spring", "", true))
                .option(Option.color("vampire_ichor.spring_color", "Killer Spring colour", "", 0xFF640058))
                .option(Option.bool("vampire_ichor.spring_lines", "Lines from the boss to a Spring", "", false))
                .option(Option.color("vampire_ichor.spring_line_color", "Spring line colour", "", 0xFFFF0D00))
                .option(Option.integer("vampire_ichor.alpha", "How solid (%)", "", 80, 0, 100))
                .option(Option.bool("vampire_ichor.fix_sound", "Fix the Spring's sound spam", "", true)));
        CombatMarks.glowing(mark -> {
            var level = Minecraft.getInstance().level;
            if (level == null || marked.isEmpty()) return;
            for (var e : marked.entrySet()) {
                var en = level.getEntity(e.getKey());
                if (en != null) CombatMarks.seenGlow(mark, en, e.getValue());
            }
        });
        SlayerApi.onAttack(VampireSlayer::onAttack);
        io.muzy.sbc.mc.ParticleHooks.listen(VampireSlayer::onParticle);
        SoundHooks.listen(VampireSlayer::onSound);
    }
}
