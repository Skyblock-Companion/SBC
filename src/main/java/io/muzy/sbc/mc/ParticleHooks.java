// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// particle packets turned into plain values before any feature sees them. like skyhanni ParticleEvent
// (postParticleEvent): one event per packet with type, pos, count, speed, offset
// vanilla handler runs once on the network thread and again on the main thread, listeners only get the main
// thread pass so each packet is seen once
public final class ParticleHooks {
    // one packet. type = registry id ("minecraft:crit"), offset = xDist/yDist/zDist, speed = maxSpeed
    public record Particle(ParticleOptions options, String type, double x, double y, double z, int count, float speed,
                           float xd, float yd, float zd, boolean longDistance) {
        public boolean is(String id) {
            return type.equals(id);
        }
    }

    public interface Listener {
        void on(Particle p);
    }

    private record Named(String name, Listener listener) {}

    private static final List<Named> LISTENERS = new CopyOnWriteArrayList<>();

    private ParticleHooks() {}

    public static void listen(Listener l) {
        LISTENERS.add(new Named("particle listener " + l.getClass().getName(), l));
    }

    // the one hook on the vanilla handler: cancel this pass or not
    // every listener hears the particle before anything can hide it, bc the solvers read the exact particles the
    // options hide
    // only the game thread pass may cancel. cancelling the network one stops the packet from ever reaching the
    // game thread, and the listeners
    // throwing out of here disconnects the player, so on a throw the packet just goes through
    public static boolean hides(ClientboundLevelParticlesPacket p) {
        Minecraft mc = Minecraft.getInstance();
        return decide(mc != null && mc.isSameThread(), () -> on(p), () -> io.muzy.sbc.misc.FireFreeze.hidesParticle(p) || io.muzy.sbc.garden.pests.PestGuess.hides(p) || io.muzy.sbc.areas.FishHooks.hidesParticle(p)
                || io.muzy.sbc.combat.CombatMarks.hidesParticle(p) || io.muzy.sbc.areas.AreaHooks.blockParticle(p) || io.muzy.sbc.dungeon.DungeonObjects.blockParticle(p) || io.muzy.sbc.world.WorldHiders.hidesPacket(p));
    }

    // hides order on its own, for the tests
    static boolean decide(boolean gameThread, Runnable listeners, java.util.function.BooleanSupplier hidden) {
        if (!gameThread || Safe.resting("ParticleHooks.hides")) return false;
        try {
            listeners.run();
            return hidden.getAsBoolean();
        } catch (Throwable t) {
            Safe.caught("ParticleHooks.hides", t);
            return false;
        }
    }

    public static void on(ClientboundLevelParticlesPacket p) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !mc.isSameThread() || mc.player == null) return;
            var type = p.particle().getType();
            if (LISTENERS.isEmpty()) return;
            var key = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            Particle ev = new Particle(p.particle(), key == null ? "" : key.toString(), p.x(), p.y(), p.z(), p.count(), p.xMaxSpeed(),
                    p.xDist(), p.yDist(), p.zDist(), p.overrideLimiter());
            for (Named l : LISTENERS) {
                if (Safe.resting(l.name)) continue;
                try {
                    l.listener.on(ev);
                } catch (Throwable t) {
                    Safe.caught(l.name, t);
                }
            }
        } catch (Throwable t) {
            Safe.caught("ParticleHooks.on", t);
        }
    }
}
