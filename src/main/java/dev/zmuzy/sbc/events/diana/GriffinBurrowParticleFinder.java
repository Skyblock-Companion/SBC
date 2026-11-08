// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.diana;

import dev.zmuzy.sbc.core.Patterns;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

// from skyhanni GriffinBurrowParticleFinder: hypixel marks every burrow near you with particles, ENCHANT
// (footstep/enchant pair) + one of ENCHANTED_HIT (start), CRIT (mob) or DRIPPING_LAVA (treasure), each with a
// fixed count, speed and offset
// a block that showed the enchant particle and a type particle = detected burrow of that type
public final class GriffinBurrowParticleFinder {
    // matches: §eYou finished the Griffin burrow chain! §r§7(4/4)
    static final Patterns.P FINISHED_CHAIN = Patterns.p("SH.event.diana.mythological.burrows.chain-finished");

    public static final class Burrow {
        public final Vec3 location;
        public boolean hasFootstep = true, hasEnchant, found;
        public int type = -1;
        public long lastSeen = DianaEnv.now();

        Burrow(Vec3 location) {
            this.location = location;
        }

        public BurrowType getType() {
            return switch (type) {
                case 0 -> BurrowType.START;
                case 1 -> BurrowType.MOB;
                case 2 -> BurrowType.TREASURE;
                default -> BurrowType.UNKNOWN;
            };
        }
    }

    private static final Map<Vec3, Burrow> burrows = new HashMap<>();

    private GriffinBurrowParticleFinder() {}

    public static boolean containsBurrow(Vec3 location) {
        return burrows.containsKey(location);
    }

    public static Map<Vec3, Burrow> burrows() {
        return burrows;
    }

    // skyhanni ParticleType or -1: EMPTY=0, MOB=1, TREASURE=2, ENCHANT=3
    static int classify(String type, int count, float speed, float xd, float yd, float zd) {
        double ox = round2(xd), oy = round2(yd), oz = round2(zd);
        if (type.equals("minecraft:enchanted_hit") && count == 4 && speed == 0.01f && ox == 0.5 && oy == 0.1 && oz == 0.5) return 0;
        if (type.equals("minecraft:crit") && count == 3 && speed == 0.01f && ox == 0.5 && oy == 0.1 && oz == 0.5) return 1;
        if (type.equals("minecraft:dripping_lava") && count == 2 && speed == 0.01f && ox == 0.35 && oy == 0.1 && oz == 0.35) return 2;
        if (type.equals("minecraft:enchant") && count == 5 && speed == 0.05f && ox == 0.5 && oy == 0.4 && oz == 0.5) return 3;
        return -1;
    }

    private static double round2(float f) {
        return Math.round(f * 100.0) / 100.0;
    }

    // LorenzVec.roundToBlock().down(): ground block under the particle
    static Vec3 groundBlock(double x, double y, double z) {
        return new Vec3(Math.floor(x), Math.floor(y) - 1, Math.floor(z));
    }

    // GriffinBurrowParticleFinder.onParticle (hub only, guess on)
    public static void onParticle(String type, double x, double y, double z, int count, float speed, float xd, float yd, float zd) {
        if (!isEnabled()) return;
        if (!Diana.guess()) return;
        int kind = classify(type, count, speed, xd, yd, zd);
        if (kind < 0) return;

        // the rounding is skyhanni's workaround for hypixel's particle positions
        Vec3 location = groundBlock(x, y, z);
        Burrow burrow = burrows.computeIfAbsent(location, Burrow::new);
        int oldBurrowType = burrow.type;
        switch (kind) {
            case 3 -> burrow.hasEnchant = true;
            case 0 -> burrow.type = 0;
            case 1 -> burrow.type = 1;
            case 2 -> burrow.type = 2;
            default -> { }
        }
        burrow.lastSeen = DianaEnv.now();
        if (burrow.hasEnchant && burrow.hasFootstep && burrow.type != -1) {
            if (!burrow.found || burrow.type != oldBurrowType) {
                GriffinBurrowHelper.onBurrowDetect(burrow.location, burrow.getType());
                burrow.found = true;
            }
        }
    }

    // GriffinBurrowParticleFinder.onTick: forget burrows whose particles stopped while they should be visible
    public static void onTick() {
        if (!DianaEnv.world.onHub()) return;
        if (GriffinBurrowHelper.shouldBurrowParticlesBeVisible(2000)) {
            burrows.values().removeIf(b -> DianaEnv.passedSince(b.lastSeen) > 500);
        }
    }

    // GriffinBurrowParticleFinder.onChat: when hypixel last talked about a burrow (formatted)
    public static void onChat(String message) {
        if (!isEnabled()) return;
        if (!Diana.guess()) return;
        if (message.startsWith("§eYou dug out a Griffin Burrow!") || FINISHED_CHAIN.matcher(message).matches()) {
            BurrowApi.lastBurrowRelatedChatMessage = DianaEnv.now();
        }
        if (message.equals("§cDefeat all the burrow defenders in order to dig it!")) {
            BurrowApi.lastBurrowRelatedChatMessage = DianaEnv.now();
        }
    }

    public static void onBurrowDug(Vec3 location) {
        burrows.remove(location);
    }

    public static void reset() {
        burrows.clear();
    }

    private static boolean isEnabled() {
        return DianaApi.isDoingDiana();
    }
}
