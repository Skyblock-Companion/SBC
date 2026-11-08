// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.events.diana.BezierFitter;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.mc.ParticleHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.regex.Matcher;

// from skyhanni DungeonSecretTrackerLocator: the secret tracker item draws a line of green sparks to a secret
// and says in chat how far. sparks = direction, the line = distance
public final class SecretCompass {
    private static final String FEATURE = "dungeon_secret_compass";
    private static final BezierFitter FITTER = new BezierFitter(1);
    private static Vec3 location;
    private static Integer distance;
    private static long lastParticle;

    private SecretCompass() {}

    private static boolean on() {
        return "CATACOMBS".equals(GameState.island) && Features.on(FEATURE);
    }

    private static void predict() {
        var curve = FITTER.fit();
        if (curve != null && distance != null) location = curve.at(distance * 2.0);
    }

    private static void reset() {
        location = null;
        FITTER.reset();
    }

    private static boolean particle(ParticleHooks.Particle p) {
        if (!on() || !p.is("minecraft:happy_villager") || p.count() != 1 || p.speed() != 0f) return false;
        lastParticle = System.currentTimeMillis();
        if (FITTER.tryAdd(new Vec3(p.x(), p.y(), p.z()), 1.0)) predict();
        return false;
    }

    static void chat(String clean) {
        if (!on()) return;
        Matcher m = Patterns.get("SH.dungeon.secrettracker.message.colorless").matcher(clean);
        if (m.find()) {
            int a = Integer.parseInt(m.group("distance"));
            int b = m.group("distance2") == null ? 0 : Integer.parseInt(m.group("distance2"));
            distance = (int) Math.sqrt(a * a + b * b);
            predict();
        }
        if (Patterns.get("SH.dungeon.secrettracker.no.missing.colorless").matcher(clean).matches()) reset();
    }

    // from skyhanni onItemClick: using it again starts a new line unless the last one is still drawing
    private static boolean itemClick(boolean right) {
        if (!right || !on()) return false;
        var p = Minecraft.getInstance().player;
        if (p == null || !"SECRET_TRACKER".equals(Gain.sbId(p.getMainHandItem()))) return false;
        if (System.currentTimeMillis() - lastParticle < 200) return true;
        FITTER.reset();
        return false;
    }

    private static void draw(WorldDraw w) {
        if (location == null) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        if (p.position().distanceTo(location) <= 3) {
            reset();
            return;
        }
        w.waypoint(location, 0xFFFF55FF, "SECRET", false, true);
    }

    public static void register() {
        Features.add(new Feature(FEATURE, "Dungeons", "Secret Tracker spot", "Marks the secret that the Secret Tracker item is pointing to, from its own sparks and chat line.", "SkyHanni: Secret Tracker Locator").defaultOff().world(SecretCompass::draw));
        ParticleHooks.listen(SecretCompass::particle);
        Interactions.onItemClick(SecretCompass::itemClick);
        dev.zmuzy.sbc.chat.ChatBus.register(dev.zmuzy.sbc.chat.ChatBus.OBSERVE + 22, "secret_compass", null, line -> {
            chat(GameState.strip(line.plain()));
            return dev.zmuzy.sbc.chat.ChatBus.PASS;
        });
    }
}
