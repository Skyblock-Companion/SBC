// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

// what we decide about an entity (glow, hidden, faded, name tag) holds for one tick
// game asks this every frame for every entity on screen and off lol. at 240 fps thats 12x a tick with string
// + regex work on hundreds of armor stands
// so each answer is cached per entity till the next tick
public final class EntityTickCache {
    public static final int GLOW = 0, AREA_GLOW = 1, HIDE = 2, AREA_HIDE = 3, WORLD_HIDE = 4, VISITOR_HIDE = 5,
            NAME_AREA = 6, NAME_WORLD = 7, NAME_DAMAGE = 8, FADE = 9, EMPTY_STAND = 10;
    private static final int LIMIT = 16384;

    private static final class Slot {
        long tick = -1;
        int value;
        Component name;
    }

    private static final Long2ObjectOpenHashMap<Slot> SLOTS = new Long2ObjectOpenHashMap<>();
    private static long tick;
    private static Object level;

    private EntityTickCache() {}

    // once a tick: clear the answers. new world drops everything bc ids get reused
    public static void tick(Minecraft mc) {
        tick++;
        if (mc.level != level || SLOTS.size() > LIMIT) {
            SLOTS.clear();
            level = mc.level;
        }
    }

    private static final int KINDS = 11;

    private static Slot slot(int kind, Entity e) {
        // the entitys own field (EntitySlotsMixin), no shared table. new world = new entities so nothing sticks around
        // the table is only for tests where the mixin isnt there
        if ((Object) e instanceof EntitySlots holder) {
            Object[] own = holder.sbc$slots();
            if (own == null) {
                own = new Object[KINDS];
                holder.sbc$slots(own);
            }
            Slot s = (Slot) own[kind];
            if (s == null) {
                s = new Slot();
                own[kind] = s;
            }
            return s;
        }
        long key = ((long) kind << 32) | (e.getId() & 0xFFFFFFFFL);
        Slot s = SLOTS.get(key);
        if (s == null) {
            s = new Slot();
            SLOTS.put(key, s);
        }
        return s;
    }

    public static int color(int kind, Entity e, ToIntFunction<Entity> f) {
        Slot s = slot(kind, e);
        if (s.tick != tick) {
            s.value = f.applyAsInt(e);
            s.tick = tick;
        }
        return s.value;
    }

    public static boolean test(int kind, Entity e, Predicate<Entity> p) {
        Slot s = slot(kind, e);
        if (s.tick != tick) {
            s.value = p.test(e) ? 1 : 0;
            s.tick = tick;
        }
        return s.value == 1;
    }

    // like test, but the answer is kept for every ticks
    public static boolean testEvery(int kind, Entity e, Predicate<Entity> p, int every) {
        Slot s = slot(kind, e);
        if (s.tick < 0 || tick - s.tick >= every) {
            s.value = p.test(e) ? 1 : 0;
            s.tick = tick;
        }
        return s.value == 1;
    }

    // name tag after f. f can return null, that gets cached too
    public static Component name(int kind, Entity e, Component in, UnaryOperator<Component> f) {
        Slot s = slot(kind, e);
        if (s.tick != tick) {
            s.name = f.apply(in);
            s.tick = tick;
        }
        return s.name;
    }

    // tests: forget everything + start a new tick
    static void resetForTests() {
        SLOTS.clear();
        tick++;
    }
}
