// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.combat;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.dungeon.DungeonMarks;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.panels.EquipmentCache;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// cocoons from the primordial belt or blood shot belt. from skyhanni CocoonAPI + CocoonOverlay
// larva skull that shows up where a mob just stood -> mob name + time till it hatches
// also gives a cocooned miniboss the SlayerMiniBossFeatures glow. sea creature names skipped
public final class Cocoons {
    public static final String ID = "cocoon_overlay";
    private static final long LIFETIME = 6400, KEEP = 8000;
    private static final int SIGHT = 32;

    // from skyhanni CocoonMob
    private record Cocoon(String name, Vec3 at, long spawned, ArmorStand stand) {}

    private static final List<Cocoon> ALL = new ArrayList<>();
    private static boolean canCocoon;

    private Cocoons() {}

    private static boolean belt(ItemStack st) {
        if ("THE_PRIMORDIAL".equals(Gain.sbId(st))) return true;
        return "blood_shot".equals(MenuView.Item.of(st).tag.getStringOr("modifier", ""));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.tickCount % 2 != 0) return;
        long now = System.currentTimeMillis();
        ALL.removeIf(c -> now - c.spawned() > KEEP || c.stand().isRemoved());
        if (mc.player.tickCount % 40 == 0) {
            canCocoon = false;
            for (ItemStack st : EquipmentCache.current()) canCocoon |= belt(st);
        }
        if ("THE_RIFT".equals(GameState.island)) return;
        String larva = DungeonMarks.skull("RIFT_LARVA");
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(SIGHT))) {
            if (a.tickCount > 20 || !DungeonMarks.headTexture(a.getItemBySlot(EquipmentSlot.HEAD)).equals(larva)) continue;
            Vec3 at = a.position();
            boolean known = false;
            for (Cocoon c : ALL) if (c.stand() == a || (c.at().x - at.x) * (c.at().x - at.x) + (c.at().z - at.z) * (c.at().z - at.z) < 0.5) known = true;
            if (known) continue;
            Mobs.Mob near = null;
            double best = 4;
            for (Mobs.Mob m : Mobs.all()) {
                double d = m.base.position().distanceToSqr(at);
                if (d < best && !(m.name.equals("Lord Jawbus") && Combat.health(m.base) < 10_000_000)) {
                    best = d;
                    near = m;
                }
            }
            if (near != null) ALL.add(new Cocoon(near.name, at, now, a));
        }
    }

    private static void draw(WorldDraw w) {
        if (!canCocoon && Combat.opt(ID, "require_belt")) return;
        long now = System.currentTimeMillis();
        for (Cocoon c : ALL) {
            if (c.at().distanceTo(w.eye) >= SIGHT) continue;
            if (Combat.opt(ID, "name")) w.dynamicText(c.at(), "§f" + c.name(), 2.0, 0);
            if (Combat.opt(ID, "timer")) {
                long left = LIFETIME - (now - c.spawned());
                w.dynamicText(c.at(), Combat.percentColor(1 - Math.clamp(left / (double) LIFETIME, 0, 1)) + Combat.seconds(left), 2.0, 1);
            }
        }
    }

    private static void left() {
        ALL.clear();
    }

    static void register() {
        Features.add(new Feature(ID, "Combat", "Cocoon overlay", "Over a mob's cocoon: what is in it and when it hatches.", "SkyHanni: Cocoon Overlay").when(Combat::sb).tick(Cocoons::tick).world(Cocoons::draw).left(Cocoons::left)
                .option(Option.bool("cocoon_overlay.name", "Mob's name", "", true))
                .option(Option.bool("cocoon_overlay.timer", "Time until it hatches", "", true))
                .option(Option.bool("cocoon_overlay.require_belt", "Only with a cocoon belt", "The Primordial, or a belt with Blood Shot.", false)));
    }
}
