// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// the bacte fight, from skyhanni rift/area/colosseum: BacteApi.kt (phase = length of the bosses name, from
// chat + its name stand), BlobbercystsHighlight.kt, TentacleWaypoint.kt
public final class RiftColosseum {
    public static final String ID = "rift_colosseum";
    static final Patterns.P NAME = Patterns.p("SH.rift.colosseum.bacte.name"), CHAT = Patterns.p("SH.rift.colosseum.bacte.chat.name");
    private static final String CYST = "Blobbercyst ";
    private static final int TENTACLE_FLOOR_Y = 68, MIN_SIZE = 4, MAX_SIZE = 8;

    private static int phase;
    private static boolean bacteKnown, glow;
    private static final Map<Slime, Integer> HITS = new HashMap<>();
    private static final List<Player> CYSTS = new ArrayList<>();

    private RiftColosseum() {}

    // 0 = bacte not fighting, else 1 to 5
    public static int phase() {
        return phase;
    }

    // from skyhanni Phase.fromNumber(name.length)
    static int phaseOf(String name) {
        int n = name.length();
        return n >= 1 && n <= 5 ? n : 0;
    }

    // from skyhanni onChatMessage with the formatted line
    static boolean onChat(String formatted) {
        Matcher m = CHAT.matcher(formatted);
        if (!m.matches()) return false;
        phase = phaseOf(m.group("name"));
        return true;
    }

    // from skyhanni onSecondPassed: what its name stand says
    static void onStandName(String formatted) {
        Matcher m = NAME.matcher(formatted);
        phase = m.matches() ? phaseOf(m.group("name")) : 0;
    }

    // from skyhanni getText
    static String text(int hits, int phase) {
        if (phase == 5) return "§a" + hits + (hits == 1 ? " Hit" : " Hits");
        int max = phase == 4 ? 3 : 4;
        return (hits > 0 ? "§c" : "§a") + (max - hits) + "§a/" + max + "§c❤";
    }

    // from skyhanni BacteApi: read whether this feature is on or not bc the rift panel shows the phase too. runs
    // every game tick
    static void readPhase(Minecraft mc) {
        if (!"THE_RIFT".equals(GameState.island) || !RiftApi.inColosseum()) {
            phase = 0;
            bacteKnown = false;
            return;
        }
        if (mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0) return;
        boolean found = false;
        var box = mc.player.getBoundingBox().inflate(80);
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, box, Entity::hasCustomName)) {
            if (!GameState.strip(st.getName().getString()).startsWith("﴾ [Lv10] B")) continue;
            found = true;
            onStandName(LegacyText.of(st.getName(), true, false));
            break;
        }
        if (bacteKnown && !found) phase = 0;
        bacteKnown = found;
    }

    private static void reset() {
        HITS.clear();
        CYSTS.clear();
        glow = false;
    }

    private static void tick(Minecraft mc) {
        glow = RiftScan.on(ID, "rift_colosseum.blobbercyst_glow", false);
        if (!RiftApi.inColosseum()) {
            HITS.clear();
            CYSTS.clear();
            return;
        }
        if (mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0) return;
        var box = mc.player.getBoundingBox().inflate(80);
        for (Slime s : mc.level.getEntitiesOfClass(Slime.class, box)) {
            if (!(mc.level.getEntity(s.getId() + 1) instanceof Slime)) continue;
            if ((int) Math.ceil(s.getY()) != TENTACLE_FLOOR_Y || s.getSize() < MIN_SIZE || s.getSize() > MAX_SIZE) continue;
            HITS.putIfAbsent(s, 0);
        }
        CYSTS.clear();
        for (Player p : mc.level.players()) if (CYST.equals(p.getName().getString())) CYSTS.add(p);
    }

    // from skyhanni onEntityDamage: wall damage has another source and doesnt count
    public static void onDamage(int entityId, String source) {
        if (HITS.isEmpty() || !"generic".equals(source)) return;
        for (var e : HITS.entrySet()) if (e.getKey().getId() == entityId) e.setValue(e.getValue() + 1);
    }

    // from skyhanni RiftGlowAdder
    public static int glow(Entity e) {
        return glow && e instanceof Player p && CYST.equals(p.getName().getString()) ? 0x55FF55 : -1;
    }

    private static void draw(WorldDraw w) {
        if (RiftScan.on(ID, "rift_colosseum.blobbercysts", true))
            for (Player p : CYSTS) if (!p.isRemoved()) w.box(p.getBoundingBox(), 0xFFFF5555, true, false);
        if (!RiftScan.on(ID, "rift_colosseum.tentacles", true)) return;
        HITS.keySet().removeIf(s -> s.isRemoved() || s.isDeadOrDying());
        for (var e : HITS.entrySet()) {
            Vec3 at = e.getKey().position();
            w.waypointBox(at.add(-0.5, 0, -0.5), 0xFFFF5555, true, false, 0);
            w.dynamicText(at.add(-0.5, 1, -0.5), text(e.getValue(), phase), 1.2, 0);
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Rift", "Bacte fight", "In the Colosseum, marks the Blobbercysts and the tentacles on the floor with how many hits each one has left.", "SkyHanni: Colosseum; Skyblocker: Blobbercyst Glow")
                .when(Rift::rift).tick(RiftColosseum::tick).world(RiftColosseum::draw).left(RiftColosseum::reset)
                .option(Option.bool("rift_colosseum.blobbercysts", "Mark the Blobbercysts", "A red box around each one.", true))
                .option(Option.bool("rift_colosseum.blobbercyst_glow", "Blobbercysts glow", "A green outline.", false))
                .option(Option.bool("rift_colosseum.tentacles", "Mark the tentacles", "A marker with a beam and the hits left.", true)));
    }
}
