// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// sound packets as plain values like skyhanni PlaySoundEvent (soundName, pitch, volume, location). listener
// returns true = muted
// only the main thread pass gets forwarded, same deal as ParticleHooks
public final class SoundHooks {
    // name = sound id without "minecraft:", like "music.game" or "entity.player.levelup"
    public record Sound(String name, double x, double y, double z, float volume, float pitch) {
        public boolean locationIsZero() {
            return x == 0 && y == 0 && z == 0;
        }
    }

    public interface Listener {
        // true mutes it
        boolean on(Sound s);
    }

    private record Named(String name, Listener listener) {}

    private static final List<Named> LISTENERS = new CopyOnWriteArrayList<>();

    private SoundHooks() {}

    public static void listen(Listener l) {
        LISTENERS.add(new Named("sound listener " + l.getClass().getName(), l));
    }

    // true when a listener wants the packet dropped
    public static boolean on(ClientboundSoundPacket p) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || !mc.isSameThread() || mc.player == null || LISTENERS.isEmpty()) return false;
            var id = p.getSound().value().location();
            String name = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
            Sound s = new Sound(name, p.getX(), p.getY(), p.getZ(), p.getVolume(), p.getPitch());
            boolean cancel = false;
            for (Named l : LISTENERS) {
                if (Safe.resting(l.name)) continue;
                try {
                    cancel |= l.listener.on(s);
                } catch (Throwable t) {
                    Safe.caught(l.name, t);
                }
            }
            return cancel;
        } catch (Throwable t) {
            Safe.caught("SoundHooks.on", t);
            return false;
        }
    }
}
