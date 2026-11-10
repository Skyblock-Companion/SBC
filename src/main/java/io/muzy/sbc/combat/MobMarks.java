// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.core.Mayor;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// mobs worth seeing. from skyhanni MobHighlight, AreaMiniBossFeatures + the enderman teleports in MiscFeatures
// skyhanni tints them so only stuff in sight shows. a glow goes through walls so we only glow what you can
// see from where you stand
public final class MobMarks {
    public static final String HIGHLIGHT = "mob_highlight", ZEALOTS = "zealot_highlight", AREA = "area_boss", TELEPORT = "enderman_teleport_hider";

    // skyhanni AreaMiniBossType: name, color, spawn spots
    private record AreaBoss(String name, int rgb, String chat, List<Vec3> spawns) {}

    private static final List<AreaBoss> AREA_BOSSES = List.of(
            new AreaBoss("Golden Ghoul", 0xFFFF55, "§e", List.of(new Vec3(-99, 39, -86), new Vec3(-128, 42, -138))),
            new AreaBoss("Old Wolf", 0xFFAA00, "§6", List.of(new Vec3(-248, 123, 54), new Vec3(-256, 105, 75), new Vec3(-268, 90, 97), new Vec3(-258, 94, 75), new Vec3(-225, 92, 127))),
            new AreaBoss("Soul of the Alpha", 0xFFAA00, "§6", List.of(new Vec3(-381, 56, -94), new Vec3(-394, 63, -52), new Vec3(-386, 50, -2), new Vec3(-396, 58, 29))),
            new AreaBoss("Voidling Extremist", 0xFF55FF, "§d", List.of(new Vec3(-591, 10, -304), new Vec3(-544, 21, -301), new Vec3(-593, 26, -328), new Vec3(-565, 41, -307), new Vec3(-573, 51, -353))),
            new AreaBoss("Millennia-Aged Blaze", 0xAA0000, "§4", List.of(new Vec3(-292, 97, -999), new Vec3(-232, 77, -951), new Vec3(-304, 73, -952), new Vec3(-281, 82, -1010), new Vec3(-342, 86, -1035))));

    private static AreaBoss lastBoss;
    private static long lastSpawn, respawn = 11_000;
    private static final Set<Mobs.Mob> areaMobs = new HashSet<>();

    private MobMarks() {}

    public static boolean hideTeleport() {
        return Features.on(TELEPORT);
    }

    // from skyhanni isZealotOrBruiserMaxHealth
    static boolean zealotHealth(int max) {
        return max == 13_000 || max == 65_000 || max == 13_000 * 4 || max == 65_000 * 4;
    }

    private static void onSpawn(Mobs.Mob m) {
        for (AreaBoss b : AREA_BOSSES) {
            if (!b.name().equals(m.name)) continue;
            long now = System.currentTimeMillis(), diff = now - lastSpawn;
            if (diff >= 5000 && diff <= 20_000) respawn = diff;
            lastSpawn = now;
            lastBoss = b;
            areaMobs.add(m);
        }
    }

    private static void collect(java.util.function.BiConsumer<Entity, Integer> mark) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean h = Features.on(HIGHLIGHT);
        if (h) {
            for (Mobs.Mob m : Mobs.all()) {
                int rgb = switch (m.name) {
                    case "Boss Corleone" -> Combat.opt(HIGHLIGHT, "corleone") ? 0xAA00AA : -1;
                    case "Arachne's Keeper" -> Combat.opt(HIGHLIGHT, "arachne_keeper") ? 0x0000AA : -1;
                    case "Arachne's Brood" -> Combat.opt(HIGHLIGHT, "arachne") ? 0xFFAA00 : -1;
                    case "Arachne" -> Combat.opt(HIGHLIGHT, "arachne") ? 0xFF5555 : -1;
                    default -> m.runic && Combat.opt(HIGHLIGHT, "runic") ? 0xFF55FF : -1;
                };
                if (rgb >= 0) CombatMarks.seenGlow(mark, m.base, rgb);
            }
            if (Combat.opt(HIGHLIGHT, "corrupted")) {
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (!(e instanceof net.minecraft.world.entity.LivingEntity l) || e instanceof net.minecraft.world.entity.decoration.ArmorStand || e == mc.player) continue;
                    int max = Combat.maxHealth(l), hp = (int) Combat.health(l) / (hpDouble() ? 2 : 1) * 3;
                    if (max == hp && max > 0) CombatMarks.seenGlow(mark, l, 0xAA00AA);
                }
            }
        }
        if (Features.on(ZEALOTS)) {
            for (Enderman e : mc.level.getEntitiesOfClass(Enderman.class, mc.player.getBoundingBox().inflate(64))) {
                var held = e.getCarriedBlock();
                if (held != null && held.is(Blocks.END_PORTAL_FRAME)) {
                    if (Combat.opt(ZEALOTS, "special")) CombatMarks.seenGlow(mark, e, 0xAA0000);
                } else if (held != null && held.is(Blocks.ENDER_CHEST)) {
                    if (Combat.opt(ZEALOTS, "chest")) CombatMarks.seenGlow(mark, e, 0x55FF55);
                } else if (Combat.opt(ZEALOTS, "bruiser") && zealotHealth(Combat.maxHealth(e))) {
                    CombatMarks.seenGlow(mark, e, 0x00AAAA);
                }
            }
        }
        if (Features.on(AREA) && Combat.opt(AREA, "highlight")) {
            areaMobs.removeIf(m -> m.base.isRemoved());
            for (Mobs.Mob m : areaMobs) {
                for (AreaBoss b : AREA_BOSSES) if (b.name().equals(m.name)) CombatMarks.seenGlow(mark, m.base, b.rgb());
            }
        }
    }

    private static boolean hpDouble() {
        for (var p : Mayor.perks) if (p.name().startsWith("DOUBLE MOBS HP")) return true;
        return false;
    }

    // skyhanni AreaMiniBossFeatures.onRenderWorld: time till the next one over where it can spawn
    private static void draw(WorldDraw w) {
        if (lastBoss == null || !SlayerApi.inAnyArea() || !Combat.opt(AREA, "timer")) return;
        long since = System.currentTimeMillis() - lastSpawn;
        String text = respawn <= since ? "§c?" : lastBoss.chat() + Combat.seconds(respawn - since);
        for (Vec3 at : lastBoss.spawns()) if (at.distanceTo(w.eye) < 15 && Combat.seen(at.add(0.5, 1, 0.5), 15)) w.dynamicText(at, text, 1.2, 0);
    }

    private static void left() {
        lastBoss = null;
        areaMobs.clear();
    }

    static void register() {
        Features.add(new Feature(HIGHLIGHT, "Combat", "Mob highlights", "A glow on Corleone, Arachne and hers, where you can see them.", "SkyHanni: Mob Highlighters").when(Combat::sb)
                .option(Option.bool("mob_highlight.corleone", "Boss Corleone", "", true))
                .option(Option.bool("mob_highlight.arachne", "Arachne and her Brood", "", true))
                .option(Option.bool("mob_highlight.arachne_keeper", "Arachne's Keeper", "", true))
                .option(Option.bool("mob_highlight.runic", "Runic mobs", "", false))
                .option(Option.bool("mob_highlight.corrupted", "Corrupted mobs", "", false)));
        Features.add(new Feature(ZEALOTS, "Combat", "Zealot highlights", "A glow on special Zealots, and if you want on Zealots with a chest and on all Zealots and Bruisers.", "SkyHanni: Special Zealots").when(() -> Combat.island("THE_END"))
                .option(Option.bool("zealot_highlight.special", "Special Zealots", "The ones carrying an End Portal Frame.", true))
                .option(Option.bool("zealot_highlight.chest", "Zealots with a chest", "", false))
                .option(Option.bool("zealot_highlight.bruiser", "All Zealots and Bruisers", "", false)));
        Features.add(new Feature(AREA, "Combat", "Area bosses", "A glow on the Golden Ghoul, Old Wolf, Soul of the Alpha, Voidling Extremist and Millennia-Aged Blaze, and the seconds until the next.", "SkyHanni: Area Boss Highlighter, Area Boss Timer").when(Combat::sb).world(MobMarks::draw).left(MobMarks::left)
                .option(Option.bool("area_boss.highlight", "Highlight", "", true))
                .option(Option.bool("area_boss.timer", "Respawn timer", "Over its spawn spots near you.", true)));
        Features.add(new Feature(TELEPORT, "Combat", "Enderman teleport hider", "Endermen do not jump about on your screen; they stay where the server says.", "SkyHanni: Enderman TP Hider").defaultOff().when(Combat::sb));
        CombatMarks.glowing(MobMarks::collect);
        Mobs.onSpawn(MobMarks::onSpawn);
    }
}
