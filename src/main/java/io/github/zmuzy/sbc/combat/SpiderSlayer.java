// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.combat;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// tarantula + the line to your boss. from skyhanni SlayerSpiderFeatures, SpiderEggSacHighlighter,
// LineToSpiderSlayer, LineToVoidgloomSeraph
public final class SpiderSlayer {
    public static final String INVINCIBLE = "spider_invincible", SACS = "spider_egg_sacs", LINE = "slayer_boss_line";
    private static final Patterns.P SAC_TIMER = Patterns.p("SH.slayer.spider.eggsac.timer");
    private static final String STUCK_LINE = "You need to kill the Broodfather's hatchlings before it can be damaged again!";

    private static Mobs.Mob lastClicked;
    // skyhanni stuckMobs + where each one stood. it moves again once it can be hurt again
    private static final Map<Mobs.Mob, Vec3> stuck = new HashMap<>();
    private static final Set<ArmorStand> sacs = new HashSet<>();

    private SpiderSlayer() {}

    public static boolean stuck(Mobs.Mob m) {
        return stuck.containsKey(m);
    }

    private static boolean rightTier(Mobs.Mob m) {
        return m.category == Mobs.Category.SLAYER && m.levelOrTier >= 3 && m.levelOrTier <= 5 && m.name.equals("Tarantula Broodfather");
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        stuck.entrySet().removeIf(e -> e.getKey().base.isRemoved() || e.getKey().base.position().distanceToSqr(e.getValue()) > 0.01);
        if (mc.player.tickCount % 5 == 0) scanSacs(mc);
    }

    // from skyhanni SpiderEggSacHighlighter.onTick
    private static void scanSacs(Minecraft mc) {
        sacs.clear();
        if (!Features.on(SACS) || SlayerApi.active() != SlayerApi.Type.TARANTULA || !SlayerApi.inBossFight()) return;
        List<ArmorStand> stands = mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(20));
        List<ArmorStand> shoot = new ArrayList<>(), timers = new ArrayList<>();
        for (ArmorStand a : stands) {
            String n = GameState.strip(a.getName().getString());
            if (n.equalsIgnoreCase("SHOOT ME!")) shoot.add(a);
            else if (SAC_TIMER.matcher(n).matches()) timers.add(a);
        }
        for (ArmorStand t : timers) {
            boolean near = false;
            for (ArmorStand s : shoot) if (s.position().distanceTo(t.position()) < 2.5) near = true;
            if (!near) continue;
            sacs.add(t);
            for (ArmorStand a : stands) if (a.getName().getString().equals("Armor Stand") && a.position().distanceTo(t.position()) < 1.2) sacs.add(a);
        }
    }

    private static void draw(WorldDraw w) {
        if (Features.on(SACS)) {
            int color = Combat.color(SACS, "color", 0x78FFFF00);
            for (ArmorStand a : sacs) if (a.isAlive() && !a.getName().getString().equals("Armor Stand")) w.filledBox(a.getBoundingBox().inflate(0.4), color & 0xFFFFFF, Math.max(0.2f, (color >>> 24) / 255f), false);
        }
        if (Features.on(LINE) && SlayerApi.inAnyArea()) {
            float width = Combat.integer(LINE, "width", 3);
            for (Mobs.Mob m : Mobs.all()) {
                if (!m.mine() || !Combat.seen(m.base, 30, 0.5)) continue;
                SlayerApi.Type t = SlayerApi.Type.byName(m.name);
                if ((t == SlayerApi.Type.TARANTULA && Combat.opt(LINE, "tarantula")) || (t == SlayerApi.Type.VOID && Combat.opt(LINE, "voidgloom"))) w.line(Combat.fromCrosshair(w), m.base.position().add(0, 1, 0), 0xFF55FFFF, width, true);
            }
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!Combat.sb() || lastClicked == null || !line.plain().equals(STUCK_LINE)) return ChatBus.PASS;
        stuck.put(lastClicked, lastClicked.base.position());
        return ChatBus.PASS;
    }

    private static void left() {
        lastClicked = null;
        stuck.clear();
        sacs.clear();
    }

    static void register() {
        Features.add(new Feature("spider_tick", "Slayer", "Tarantula background", "Knows when a Tarantula cannot be hurt, for the Damage Indicator and the glow.", "").alwaysOn().when(Combat::sb).tick(SpiderSlayer::tick).world(SpiderSlayer::draw).left(SpiderSlayer::left));
        ChatBus.register(ChatBus.OBSERVE + 20, "spiderslayer", null, SpiderSlayer::onChat);
        SlayerApi.onAttack(e -> {
            Mobs.Mob m = Mobs.of(e);
            if (m != null && rightTier(m)) lastClicked = m;
        });
        Features.add(new Feature(INVINCIBLE, "Slayer", "Mark an invincible Tarantula", "A glow on a Tarantula while its hatchlings keep it from being hurt.", "SkyHanni: Mark When Invincible").when(Combat::sb)
                .option(Option.color("spider_invincible.color", "Colour", "", 0xFFFFFF00)));
        CombatMarks.glowing(mark -> {
            if (!Features.on(INVINCIBLE)) return;
            int color = Combat.color(INVINCIBLE, "color", 0xFFFFFF00);
            for (Mobs.Mob m : stuck.keySet()) CombatMarks.seenGlow(mark, m.base, color);
        });
        Features.add(new Feature(SACS, "Slayer", "Egg sacs", "Marks a Tarantula's egg sacs during your fight.", "SkyHanni: Highlight Egg Sacs").when(Combat::sb)
                .option(Option.color("spider_egg_sacs.color", "Colour", "", 0x78FFFF00)));
        CombatMarks.glowing(mark -> {
            if (Features.on(SACS)) for (ArmorStand a : sacs) CombatMarks.seenGlow(mark, a, 0xFFFF00);
        });
        Features.add(new Feature(LINE, "Slayer", "Line to your boss", "From your crosshair to your Tarantula or Voidgloom when it is within thirty blocks and in sight.", "SkyHanni: Line to Tarantula Boss, Line to Voidgloom Boss").when(Combat::sb).defaultOff()
                .option(Option.bool("slayer_boss_line.tarantula", "Tarantula", "", true))
                .option(Option.bool("slayer_boss_line.voidgloom", "Voidgloom", "", true))
                .option(Option.integer("slayer_boss_line.width", "Line width", "", 3, 1, 10)));
    }
}
