// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

// everything the slayer/combat helpers do to entities, in one spot the render hooks can ask without slowing a frame
// glow + color (skyhanni RenderLivingEntityHelper, skyblocker MobGlowAdder), not drawn (CheckRenderEntityEvent)
// see through (EntityTransparencyManager), dropped particles (ParticleEvent). recomputed a few times a sec
public final class CombatMarks {
    public interface GlowSource {
        void collect(BiConsumer<Entity, Integer> mark);
    }

    public interface ParticleRule {
        boolean hides(String type, double x, double y, double z);
    }

    private record HideRule(BooleanSupplier active, Predicate<Entity> hides) {}

    private record FadeRule(BooleanSupplier active, Predicate<LivingEntity> fades, java.util.function.IntSupplier percent) {}

    private record ParticleEntry(BooleanSupplier active, ParticleRule rule) {}

    private static final List<GlowSource> GLOWS = new ArrayList<>();
    private static final List<HideRule> HIDES = new ArrayList<>();
    private static final List<FadeRule> FADES = new ArrayList<>();
    private static final List<ParticleEntry> PARTICLES = new ArrayList<>();
    private static Map<Integer, Integer> glow = new HashMap<>();
    private static final Map<Integer, Boolean> hideCache = new HashMap<>();
    private static final Set<Integer> faded = new HashSet<>();
    private static boolean anyHide, anyParticle;
    private static int fadePercent = -1;

    private CombatMarks() {}

    public static void glowing(GlowSource s) {
        GLOWS.add(s);
    }

    public static void hiding(BooleanSupplier active, Predicate<Entity> rule) {
        HIDES.add(new HideRule(active, rule));
    }

    public static void fading(BooleanSupplier active, Predicate<LivingEntity> rule, java.util.function.IntSupplier percent) {
        FADES.add(new FadeRule(active, rule, percent));
    }

    public static void particles(BooleanSupplier active, ParticleRule rule) {
        PARTICLES.add(new ParticleEntry(active, rule));
    }

    // -1 = no glow
    public static int glow(Entity e) {
        return glow.isEmpty() ? -1 : glow.getOrDefault(e.getId(), -1);
    }

    // a glow outline is drawn over the world, walls too. so every glow of the mod is only given to an entity you can
    // see from here: its feet, middle or head in sight, so it does not flicker at a corner (like skyhanni's no-xray glow)
    public static boolean inSight(Entity e) {
        double h = e.getBbHeight();
        return Combat.seen(e, 96, h * 0.5) || Combat.seen(e, 96, h * 0.9) || Combat.seen(e, 96, 0.1);
    }

    // glow only on what you can see from here. a highlight that doesnt go through walls is what you see anyway
    public static void seenGlow(BiConsumer<Entity, Integer> mark, Entity e, int rgb) {
        if (Combat.seen(e, 64, e.getBbHeight() * 0.8)) mark.accept(e, rgb);
    }

    public static boolean hidden(Entity e) {
        if (!anyHide) return false;
        return hideCache.computeIfAbsent(e.getId(), id -> {
            for (HideRule r : HIDES) if (r.active().getAsBoolean() && r.hides().test(e)) return true;
            return false;
        });
    }

    public static boolean faded(Entity e) {
        return !faded.isEmpty() && faded.contains(e.getId());
    }

    // alpha from whatever setting holds rn, swapped in for vanillas invisible mob constant
    public static int fadeTint(int vanilla) {
        return fadePercent < 0 ? vanilla : (fadePercent * 255 / 100) << 24 | 0xFFFFFF;
    }

    public static boolean hidesParticle(ClientboundLevelParticlesPacket p) {
        if (!anyParticle) return false;
        try {
            var key = BuiltInRegistries.PARTICLE_TYPE.getKey(p.particle().getType());
            if (key == null) return false;
            String type = key.getPath();
            for (ParticleEntry e : PARTICLES) if (e.active().getAsBoolean() && e.rule().hides(type, p.x(), p.y(), p.z())) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount % 5 == 0) {
            Map<Integer, Integer> next = new HashMap<>();
            for (GlowSource s : GLOWS) s.collect((e, rgb) -> next.put(e.getId(), rgb & 0xFFFFFF));
            glow = next;
            faded.clear();
            fadePercent = -1;
            for (FadeRule r : FADES) {
                if (!r.active().getAsBoolean()) continue;
                for (Entity e : mc.level.entitiesForRendering())
                    if (e instanceof LivingEntity l && e != mc.player && r.fades().test(l)) faded.add(e.getId());
                if (fadePercent < 0) fadePercent = r.percent().getAsInt();
            }
        }
        if (mc.player.tickCount % 10 == 0) {
            hideCache.clear();
            boolean h = false, p = false;
            for (HideRule r : HIDES) h |= r.active().getAsBoolean();
            for (ParticleEntry e : PARTICLES) p |= e.active().getAsBoolean();
            anyHide = h;
            anyParticle = p;
        }
    }

    private static void left() {
        glow = new HashMap<>();
        hideCache.clear();
        faded.clear();
        fadePercent = -1;
        anyHide = anyParticle = false;
    }

    static void register() {
        Features.add(new Feature("combat_marks", "Combat", "Combat highlights", "Keeps track of which mobs the slayer and combat helpers highlight or hide.", "").alwaysOn().when(Combat::sb).tick(CombatMarks::tick).left(CombatMarks::left));
    }
}
