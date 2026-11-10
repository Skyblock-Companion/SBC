// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.nav;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// kuudra, display only: fight phase (from elles lines), supply + fuel piles while you can grab them, ballista
// build spots, safe spots + pearl spots
public final class Kuudra {
    private enum Phase { OTHER, RETRIEVE_SUPPLIES, DPS, KUUDRA_LAIR }

    private static final String ID = "kuudra_helper";
    private static final int KUUDRA_SIZE = 30;

    private static Phase phase = Phase.OTHER;
    private static List<BlockPos> supplies = List.of(), fuel = List.of(), ballista = List.of();
    private static List<BlockPos> safeSpots, pearls;

    private Kuudra() {}

    private static boolean opt(String key, boolean def) {
        Feature f = Features.get(ID);
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static void reset() {
        phase = Phase.OTHER;
        supplies = fuel = ballista = List.of();
        lastSlot = -1;
    }

    // from skyblocker KuudraGlowAdder: kuudra = the size 30 magma cube
    public static int glow(Entity e) {
        return e instanceof net.minecraft.world.entity.monster.cubemob.MagmaCube m && m.getSize() == KUUDRA_SIZE && Features.on(ID) && opt("kuudra_helper.glow", false) ? 0xF7510F : -1;
    }

    private static boolean inKuudra() {
        return "KUUDRA".equals(GameState.island);
    }

    private static List<BlockPos> load(String file) {
        List<BlockPos> out = new ArrayList<>();
        try (var in = Kuudra.class.getResourceAsStream(file)) {
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("waypoints")) {
                var o = e.getAsJsonObject();
                out.add(new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()));
            }
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read {}", file, ex);
        }
        return out;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String m = line.plain().trim();
        if (m.equals("[NPC] Elle: ARGH! All of the supplies fell into the lava! You need to retrieve them quickly!")) phase = Phase.RETRIEVE_SUPPLIES;
        else if (m.equals("[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!")) phase = Phase.DPS;
        else if (m.equals("[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!")) phase = Phase.OTHER;
        else if (m.equals("[NPC] Elle: What just happened!? Is this Kuudra's real lair?")) phase = Phase.KUUDRA_LAIR;
        return ChatBus.PASS;
    }

    private static final java.util.Map<String, String> DANGER = java.util.Map.of("green_terracotta", "§2", "lime_terracotta", "§a", "yellow_terracotta", "§e",
            "orange_terracotta", "§6", "pink_terracotta", "§d", "red_terracotta", "§c");
    private static long lastWarn;
    private static int lastSlot = -1;

    private static void warn(Minecraft mc, String text, float pitch) {
        long now = System.currentTimeMillis();
        if (now - lastWarn < 1200) return;
        lastWarn = now;
        mc.gui.hud.setTimes(2, 16, 6);
        mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal(text));
        mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, pitch);
    }

    // the lava looking floor tiles that hurt in kuudras lair are colored terracotta, say so while youre on one
    private static void danger(Minecraft mc) {
        for (int i = 1; i <= 5; i++) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(mc.player.blockPosition().below(i)).getBlock()).getPath();
            String col = DANGER.get(path);
            if (col != null) {
                warn(mc, col + "DANGER", 0.5f);
                return;
            }
        }
    }

    // switching to a bow in the dps phase without toxic arrow poison in your inventory
    private static void warnPoison(Minecraft mc) {
        int slot = mc.player.getInventory().getSelectedSlot();
        if (slot == lastSlot) return;
        lastSlot = slot;
        if (phase != Phase.DPS || slot == 8) return;
        var held = mc.player.getInventory().getItem(slot);
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().equals("bow")) return;
        int amount = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            var st = mc.player.getInventory().getItem(i);
            if (io.github.zmuzy.sbc.core.Gain.sbId(st).equals("TOXIC_ARROW_POISON")) amount += st.getCount();
        }
        if (amount == 0) warn(mc, "§aNo Arrow Poison", 0.8f);
        else if (amount < threshold()) warn(mc, "§aLow Arrow Poison (" + amount + ")", 0.8f);
    }

    private static int threshold() {
        Feature f = Features.get(ID);
        var o = f == null ? null : f.option("kuudra_helper.poison_threshold");
        return o == null ? 32 : o.integer();
    }

    // from skyblocker suppliesAndFuelWaypointType: a beam on it or just the box
    private static void pile(WorldDraw w, BlockPos p, int color, String name) {
        w.box(new AABB(p), color, true, true);
        Feature f = Features.get(ID);
        var o = f == null ? null : f.option("kuudra_helper.pile_type");
        if (o == null || o.choice().equals("Waypoint")) w.beacon(Vec3.atBottomCenterOf(p).add(0, 1, 0), color, true);
        w.label(name, Vec3.atCenterOf(p).add(0, 1.2, 0), true);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount % 5 == 0 && opt("kuudra_helper.danger", true)) danger(mc);
        if (opt("kuudra_helper.no_poison", true)) warnPoison(mc);
        if (mc.player.tickCount % 20 != 0) return;
        AABB box = mc.player.getBoundingBox().inflate(500);
        List<BlockPos> sup = new ArrayList<>(), fue = new ArrayList<>(), bal = new ArrayList<>();
        for (Giant g : mc.level.getEntitiesOfClass(Giant.class, box, x -> x.getY() < 67)) {
            double yaw = (g.getYRot() + 115.0f) * (Math.PI / 180.0);
            BlockPos p = BlockPos.containing(g.getX() + 4.5 * Math.cos(yaw), 75, g.getZ() + 4.5 * Math.sin(yaw));
            (phase == Phase.DPS ? fue : sup).add(p);
        }
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, box, Entity::hasCustomName))
            if (a.getName().getString().contains("SNEAK + PUNCH")) bal.add(a.blockPosition());
        supplies = sup;
        fuel = fue;
        ballista = bal;
    }

    private static void draw(WorldDraw w) {
        if (!inKuudra()) return;
        if (safeSpots == null) safeSpots = load("/sbc/kuudra_safe.json");
        if (pearls == null) pearls = load("/sbc/kuudra_pearls.json");
        if (opt("kuudra_helper.supplies", true)) for (BlockPos p : supplies) pile(w, p, 0xFFFF3B3B, "Supplies");
        if (opt("kuudra_helper.fuel", true)) for (BlockPos p : fuel) pile(w, p, 0xFFFF9F43, "Fuel");
        if (opt("kuudra_helper.ballista", true)) {
            for (BlockPos p : ballista) {
                w.box(new AABB(p), 0xFF39D0FF, true, true);
                w.label("Build here", Vec3.atCenterOf(p).add(0, 1.2, 0), true);
            }
        }
        if (opt("kuudra_helper.safe", true)) for (BlockPos p : safeSpots) w.box(new AABB(p), 0xFFFF55FF, true, false);
        if (opt("kuudra_helper.pearls", true)) for (BlockPos p : pearls) w.box(new AABB(p), 0xFF39757D, true, false);
    }

    public static void register() {
        Feature f = Features.add(new Feature("kuudra_helper", "Crimson Isle", "Kuudra helper", "Marks supply and fuel piles, ballista build spots, safe spots and pearl throw spots in Kuudra.", "Skyblocker: Kuudra waypoints")
                .when(Kuudra::inKuudra).tick(Kuudra::tick).world(Kuudra::draw).left(Kuudra::reset)
                .option(Option.bool("kuudra_helper.supplies", "Supply piles", "", true))
                .option(Option.bool("kuudra_helper.fuel", "Fuel piles", "", true))
                .option(Option.choice("kuudra_helper.pile_type", "Supply and fuel markers", "Waypoint has a beam, Highlight only the box.", "Waypoint", "Waypoint", "Highlight"))
                .option(Option.bool("kuudra_helper.ballista", "Ballista build spots", "", true))
                .option(Option.bool("kuudra_helper.safe", "Safe spots", "", true))
                .option(Option.bool("kuudra_helper.pearls", "Pearl spots", "", true))
                .option(Option.bool("kuudra_helper.danger", "Danger warning", "A warning in the colour of the danger when a tentacle slam or TNT is about to hit you.", true))
                .option(Option.bool("kuudra_helper.no_poison", "Arrow poison warning", "When you pull out a bow in the damage phase with no or little Toxic Arrow Poison.", true))
                .option(Option.integer("kuudra_helper.poison_threshold", "Little poison is under", "", 32, 1, 256))
                .option(Option.bool("kuudra_helper.glow", "Kuudra glows", "A deep orange outline.", false)));
        ChatBus.register(ChatBus.OBSERVE + 9, "kuudra", f, Kuudra::onChat);
    }
}
