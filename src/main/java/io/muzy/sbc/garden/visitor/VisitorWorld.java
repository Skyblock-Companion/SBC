// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.visitor;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// visitors in the world, from skyhanni GardenVisitorHighlight (status over their heads + as a colour),
// VisitorListener.onCheckRender (hypixel's CLICK under the name makes room for it),
// HighlightVisitorsOutsideOfGarden (where unlockable visitors stand)
public final class VisitorWorld {
    public static final String STATUS = "garden_visitor_status", OUTSIDE = "garden_visitor_outside";

    private static final Map<Integer, String> OUTSIDE_NOW = new HashMap<>();
    private static long scannedAt;

    private VisitorWorld() {}

    private static String highlight() {
        Feature f = Features.get(STATUS);
        Option o = f == null ? null : f.option("garden_visitor_status.highlight");
        return f == null || !f.enabled() || o == null ? "Off" : o.text();
    }

    private static boolean names() {
        String h = highlight();
        return h.equals("Both") || h.equals("Name");
    }

    // VisitorListener.onCheckRender, from the mixin. runs for every entity every frame so cheap checks go first
    public static boolean hidden(Entity entity) {
        if (!(entity instanceof ArmorStand) || !"GARDEN".equals(GameState.island)) return false;
        if (!entity.hasCustomName() || !"CLICK".equals(entity.getName().getString())) return false;
        return names() && Visitors.onBarnPlot();
    }

    static void draw(WorldDraw w) {
        if (!Visitors.onBarnPlot()) return;
        String h = highlight();
        boolean names = h.equals("Both") || h.equals("Name"), colors = h.equals("Both") || h.equals("Color");
        Feature block = Features.get(VisitorBlock.FEATURE);
        boolean overName = block != null && block.enabled() && block.option("garden_visitor_block.over_name") != null && block.option("garden_visitor_block.over_name").bool();
        for (Visitors.Visitor v : Visitors.all()) {
            // the box only marks one you have not met yet
            if (colors && v.status == Visitors.Status.NEW) {
                Entity e = Visitors.entityOf(w.mc, v);
                if (e != null) w.filledBox(e.getBoundingBox(), v.status.color & 0xFFFFFF, (v.status.color >>> 24) / 255f, false);
            }
            if (!names) continue;
            Entity tag = Visitors.nameTagOf(w.mc, v);
            if (tag == null || w.mc.player == null || tag.distanceTo(w.mc.player) > 15) continue;
            w.dynamicText(tag.position().add(0, 2.23, 0), v.status.display, 1, 0);
            if (!overName) continue;
            int line = 0;
            for (VisitorReward reward : v.warned()) {
                w.dynamicText(tag.position().add(0, 2.73 + line * 0.25, 0), "§c§l! " + reward.displayName + " §c§l!", 1, 0);
                line++;
            }
        }
    }

    // --- outside the garden ---

    // getSkinOrTypeFor: Garden.json has the old class names ("EntityVillager") and the skin for the player ones
    private static String skinOrType(Entity entity) {
        if (entity instanceof Player p) {
            for (var property : p.getGameProfile().properties().get("textures")) return property.value();
            return "no skin";
        }
        return "Entity" + entity.getClass().getSimpleName();
    }

    // isVisitor: visitor name or null
    static String visitorOf(Entity entity, String mode) {
        String skin = skinOrType(entity);
        for (VisitorRepo.Info info : VisitorRepo.all().values()) {
            if (!mode.equals(info.mode()) || !skin.equals(info.skinOrType())) continue;
            double[] at = info.position();
            if (at == null) return info.name();
            var pos = entity.blockPosition();
            double dx = pos.getX() - at[0], dy = pos.getY() - at[1], dz = pos.getZ() - at[2];
            if (dx * dx + dy * dy + dz * dz < 1) return info.name();
        }
        return null;
    }

    // onSecondPassed, only nearby entities, not the whole island
    private static void scan(Minecraft mc) {
        OUTSIDE_NOW.clear();
        if (mc.level == null || mc.player == null || GameState.apiMode.isEmpty()) return;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, new AABB(mc.player.blockPosition()).inflate(48))) {
            if (e instanceof ArmorStand || e == mc.player) continue;
            String name = visitorOf(e, GameState.apiMode);
            if (name != null) OUTSIDE_NOW.put(e.getId(), name);
        }
    }

    static void drawOutside(WorldDraw w) {
        if (Visitors.garden()) return;
        long now = System.currentTimeMillis();
        if (now - scannedAt > 1000) {
            scannedAt = now;
            scan(w.mc);
        }
        for (var e : OUTSIDE_NOW.entrySet()) {
            Entity entity = w.mc.level == null ? null : w.mc.level.getEntity(e.getKey());
            if (entity == null || w.mc.player == null) continue;
            w.filledBox(entity.getBoundingBox(), 0xAA0000, 50 / 255f, false);
            w.dynamicText(entity.position().add(0, entity.getBbHeight() + 0.6, 0), "§c" + e.getValue() + " §7" + Math.round(entity.distanceTo(w.mc.player)) + "m", 1, 0);
        }
    }

    private static boolean shouldBlock() {
        Feature f = Features.get(OUTSIDE);
        Option o = f == null ? null : f.option("garden_visitor_outside.block");
        if (o == null) return false;
        return switch (o.text()) {
            case "Always" -> true;
            case "Only on Bingo" -> VisitorBlock.bingo();
            default -> false;
        };
    }

    private static boolean visitorNearby(Entity stand, String mode) {
        var mc = Minecraft.getInstance();
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, stand.getBoundingBox().inflate(2))) {
            if (!(e instanceof ArmorStand) && e != mc.player && visitorOf(e, mode) != null) return true;
        }
        return false;
    }

    // onClickEntity: sneaking lets it through
    private static boolean blockClick(Entity entity) {
        if (!GameState.inSkyBlock || !shouldBlock()) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isShiftKeyDown()) return false;
        String mode = GameState.apiMode;
        boolean visitor = entity instanceof ArmorStand ? visitorNearby(entity, mode) : visitorOf(entity, mode) != null;
        if (!visitor) return false;
        mc.player.sendSystemMessage(Component.literal("§b[SBC] §eBlocked you from interacting with a visitor. Sneak to bypass or change it in the settings."));
        return true;
    }

    static void registerClicks() {
        Safe.useEntity("VisitorWorld.useEntity", (player, level, hand, entity, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            try {
                return blockClick(entity) ? InteractionResult.FAIL : InteractionResult.PASS;
            } catch (Throwable t) {
                return InteractionResult.PASS;
            }
        });
        Safe.attackEntity("VisitorWorld.attackEntity", (player, level, hand, entity, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            try {
                return blockClick(entity) ? InteractionResult.FAIL : InteractionResult.PASS;
            } catch (Throwable t) {
                return InteractionResult.PASS;
            }
        });
    }

    static List<Feature> features() {
        List<Feature> out = new ArrayList<>();
        out.add(new Feature(STATUS, "Garden", "Visitor status in the world", "Over each visitor on the Barn plot: New, Items Ready, Accepted or Refused, and the rare reward it has. A new visitor can also get a box around it.", "SkyHanni: Highlight Status")
                .when(Visitors::garden)
                .world(VisitorWorld::draw)
                .option(Option.choice("garden_visitor_status.highlight", "Highlight status", "Name: the text above them. Color: a box around a new visitor. Both, or Off.", "Name", "Both", "Name", "Color", "Off")));
        out.add(new Feature(OUTSIDE, "Garden", "Visitors outside the Garden", "On the other islands, the people you can still meet as visitors get a red tint, with their name and distance. Can also stop you from talking to them, to keep Dedication cycling going.", "SkyHanni: Highlight Visitors in SkyBlock")
                .defaultOff()
                .when(() -> GameState.inSkyBlock)
                .world(VisitorWorld::drawOutside)
                .option(Option.choice("garden_visitor_outside.block", "Block talking to them", "Sneak to talk to them anyway.", "Never", "Never", "Always", "Only on Bingo")));
        return out;
    }
}
