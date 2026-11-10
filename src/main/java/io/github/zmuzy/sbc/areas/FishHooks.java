// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.areas;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.core.GameState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

// what the fishing, foraging, hunting helpers share. skyhannis CheckRenderEntityEvent,
// RenderEntityOutlineEvent + cancelled ParticleEvent, boiled down to sets of entity ids worked out a few
// times a sec
public final class FishHooks {
    // one user of the shared walk: begin, every entity, end
    public interface Scan {
        boolean wanted();

        default void begin() {}

        void entity(Entity e);

        default void end() {}
    }

    // one scan with its own fail record: a throw stops it for that walk, 3 in a minute and it rests, the others keep going
    static final class Slot {
        static final int MAX_FAILURES = 3;
        static final long WINDOW_MS = 60_000, REST_MS = 60_000;
        final Scan scan;
        boolean running;
        int failures;
        long windowStart, restUntil;

        Slot(Scan scan) {
            this.scan = scan;
        }

        void fail(Throwable t, long now) {
            running = false;
            if (now - windowStart > WINDOW_MS) {
                windowStart = now;
                failures = 0;
            }
            if (failures++ == 0) io.github.zmuzy.sbc.SbcMod.LOG.warn("Area scan {} failed", scan.getClass().getName(), t);
            if (failures >= MAX_FAILURES) {
                restUntil = now + REST_MS;
                failures = 0;
            }
        }
    }

    private static final List<Slot> SCANS = new ArrayList<>();
    private static final List<Predicate<ClientboundLevelParticlesPacket>> PARTICLES = new CopyOnWriteArrayList<>();
    // only written on the client thread, same as the render paths that read them
    private static Set<Integer> hidden = new HashSet<>();
    private static Map<Integer, Integer> glowing = new HashMap<>();
    private static Set<Integer> nextHidden = new HashSet<>();
    private static Map<Integer, Integer> nextGlowing = new HashMap<>();
    private static int ticks;

    private FishHooks() {}

    public static void scan(Scan s) {
        SCANS.add(new Slot(s));
    }

    public static void hideParticles(Predicate<ClientboundLevelParticlesPacket> p) {
        PARTICLES.add(p);
    }

    // during a scan: dont draw this entity till the next one
    public static void hide(Entity e) {
        nextHidden.add(e.getId());
    }

    // during a scan: this entity gets an outline till the next one
    public static void glow(Entity e, int rgb) {
        nextGlowing.put(e.getId(), rgb & 0xFFFFFF);
    }

    // EntityGlowMixin calls this
    public static int glowOf(Entity e) {
        if (glowing.isEmpty()) return -1;
        Integer c = glowing.get(e.getId());
        return c == null ? -1 : c;
    }

    // EntityHideMixin calls this
    public static boolean hiddenNow(Entity e) {
        return !hidden.isEmpty() && hidden.contains(e.getId());
    }

    // ParticleMixin calls this
    public static boolean hidesParticle(ClientboundLevelParticlesPacket p) {
        if (PARTICLES.isEmpty() || !GameState.inSkyBlock) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread()) return false;
        for (Predicate<ClientboundLevelParticlesPacket> t : PARTICLES) {
            try {
                if (t.test(p)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static void tick(Minecraft mc) {
        if (++ticks % 4 != 0) return;
        if (mc.level == null || mc.player == null || !GameState.inSkyBlock) {
            hidden = new HashSet<>();
            glowing = new HashMap<>();
            return;
        }
        nextHidden = new HashSet<>();
        nextGlowing = new HashMap<>();
        walk(SCANS, mc.level.entitiesForRendering(), System.currentTimeMillis());
        hidden = nextHidden;
        glowing = nextGlowing;
    }

    // the shared walk. each scan runs alone so one that throws costs the others nothing
    static void walk(List<Slot> slots, Iterable<Entity> entities, long now) {
        List<Slot> on = new ArrayList<>();
        for (Slot s : slots) {
            if (now < s.restUntil) continue;
            try {
                s.running = s.scan.wanted();
                if (s.running) on.add(s);
            } catch (Throwable t) {
                s.fail(t, now);
            }
        }
        if (on.isEmpty()) return;
        for (Slot s : on) {
            try {
                s.scan.begin();
            } catch (Throwable t) {
                s.fail(t, now);
            }
        }
        for (Entity e : entities) {
            for (Slot s : on) {
                if (!s.running) continue;
                try {
                    s.scan.entity(e);
                } catch (Throwable t) {
                    s.fail(t, now);
                }
            }
        }
        for (Slot s : on) {
            if (!s.running) continue;
            try {
                s.scan.end();
            } catch (Throwable t) {
                s.fail(t, now);
            }
        }
    }

    // like FarmingKeys.held: a key that can be on a mouse button, down also while a screen is open
    public static boolean held(net.minecraft.client.KeyMapping key) {
        if (key == null) return false;
        Minecraft mc = Minecraft.getInstance();
        InputConstants.Key k = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(key);
        if (k.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        if (k.getType() == InputConstants.Type.MOUSE) return io.github.zmuzy.sbc.mc.KeyHeld.mouseDown(k.getValue());
        return InputConstants.isKeyDown(k.getValue());
    }

    // from skyhanni TitleManager.sendTitle
    public static void title(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.hud.setTimes(5, 40, 10);
        mc.gui.hud.setTitle(Component.literal(text));
        mc.gui.hud.setSubtitle(Component.empty());
    }

    public static void pling() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.4f);
    }

    public static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }
}
