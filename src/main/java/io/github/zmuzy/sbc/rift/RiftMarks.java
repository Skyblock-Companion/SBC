// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.ParticleHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// the small rift marks, each from its skyhanni file: SplatterHearts, VerminHighlighter, RiftLarva,
// RiftOdonata, ShyCruxWarnings and more
public final class RiftMarks {
    public static final String SPLATTER = "rift_splatter", VERMIN = "rift_vermin_marks", WOODS = "rift_wyld_woods", GUIDE = "rift_guide", HORSEZOOKA = "rift_horsezooka",
            PILLAR = "rift_temporal_pillar", MELON = "rift_healing_melon";
    private static final String VACUUM = "TURBOMAX_VACUUM", LARVA_HOOK = "LARVA_HOOK", ODONATA_BOTTLE = "EMPTY_ODONATA_BOTTLE", HORSEZOOKA_ID = "HORSEZOOKA";
    private static final Set<String> SHY_NAMES = Set.of("I'm ugly! :(", "Eek!", "Don't look at me!", "Look away!");

    private RiftMarks() {}

    // --- splatter hearts ---

    private static long lastHearts = Long.MIN_VALUE / 4;
    private static Set<Vec3> shownHearts = Set.of();
    private static final Set<Vec3> CURRENT_HEARTS = new HashSet<>();

    private static void onParticle(ParticleHooks.Particle p) {
        if (!Rift.rift() || !Features.on(SPLATTER) || !RiftApi.inStillgoreChateau()) return;
        if (!p.is("minecraft:heart") || p.count() != 3 || p.speed() != 0f) return;
        long now = System.currentTimeMillis();
        if (now - lastHearts > 50) {
            shownHearts = new HashSet<>(CURRENT_HEARTS);
            CURRENT_HEARTS.clear();
        }
        lastHearts = now;
        CURRENT_HEARTS.add(new Vec3(p.x(), p.y(), p.z()));
    }

    private static void drawHearts(WorldDraw w) {
        if (!RiftApi.inStillgoreChateau() || System.currentTimeMillis() - lastHearts > 300) return;
        for (Vec3 h : shownHearts) {
            Vec3 pos = h.add(-0.5, 0.3, -0.5);
            w.filledBox(new AABB(pos, pos.add(1, 1, 1)), 0xFF5555, 100 / 255f, false);
        }
    }

    // --- vermin, larvas, odonatas ---

    private static final List<Entity> VERMINS = new ArrayList<>(), LARVAS = new ArrayList<>(), ODONATAS = new ArrayList<>();

    private static boolean vermin(LivingEntity e) {
        if (e instanceof ArmorStand st) {
            String tex = RiftScan.texture(st.getItemBySlot(EquipmentSlot.HEAD));
            return tex != null && (tex.equals(RiftScan.skull("VERMIN_FLY")) || tex.equals(RiftScan.skull("VERMIN_SPIDER")));
        }
        return e instanceof Silverfish && e.getAttributeBaseValue(Attributes.MAX_HEALTH) == 8;
    }

    private static void tickVermin(Minecraft mc) {
        VERMINS.clear();
        if (mc.player == null || mc.level == null || !RiftApi.inWestVillage() || !RiftScan.heldId(mc).equals(VACUUM)) return;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(48))) if (vermin(e)) VERMINS.add(e);
    }

    // a stands mob is its skull, up where the head is
    private static AABB box(Entity e) {
        if (!(e instanceof ArmorStand)) return e.getBoundingBox();
        double x = e.getX(), y = e.getY() + 1.4, z = e.getZ();
        return new AABB(x - 0.4, y, z - 0.4, x + 0.4, y + 0.8, z + 0.4);
    }

    private static void drawEntities(WorldDraw w, List<Entity> list, int color) {
        for (Entity e : list) if (!e.isRemoved()) w.box(box(e), color, true, false);
    }

    private static void tickWoods(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        String held = RiftScan.heldId(mc);
        boolean larva = RiftScan.on(WOODS, "rift_wyld_woods.larvas", true) && held.equals(LARVA_HOOK), odonata = RiftScan.on(WOODS, "rift_wyld_woods.odonatas", true) && held.equals(ODONATA_BOTTLE);
        if (mc.player.tickCount % 5 == 0) {
            LARVAS.clear();
            ODONATAS.clear();
        }
        if (mc.player.tickCount % 5 == 0 && (larva || odonata)) {
            for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
                if (larva) {
                    String tex = RiftScan.texture(st.getItemBySlot(EquipmentSlot.HEAD));
                    if (tex != null && tex.equals(RiftScan.skull("RIFT_LARVA"))) LARVAS.add(st);
                }
                if (odonata) {
                    String tex = RiftScan.texture(st.getItemBySlot(EquipmentSlot.MAINHAND));
                    if (tex != null && tex.equals(RiftScan.skull("MOB_ODONATA"))) ODONATAS.add(st);
                }
            }
        }
        // from skyhanni checkForShy: every other tick is plenty for a warning that lasts longer than that
        if (mc.player.tickCount % 2 == 0 && RiftScan.on(WOODS, "rift_wyld_woods.shy", true)) {
            for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(8))) {
                if (!SHY_NAMES.contains(e.getName().getString())) continue;
                mc.gui.hud.setTimes(0, 3, 1);
                mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§eLook away!"));
                break;
            }
        }
    }

    // --- rift guide ---

    private static final Set<Integer> GUIDE_SLOTS = new HashSet<>();
    private static boolean inGuide;

    // from skyhanni onInventoryFullyOpened: slot 40 points back at the guide, lore ending in the mark = still to do
    static boolean guideMenu(List<String> slot40Lore) {
        return slot40Lore.size() == 1 && slot40Lore.get(0).startsWith("§7To Rift Guide");
    }

    static boolean todo(List<String> lore) {
        return !lore.isEmpty() && lore.get(lore.size() - 1).equals("§8✖ Not completed yet!");
    }

    static void onMenu(ChestMenu menu, int n) {
        GUIDE_SLOTS.clear();
        inGuide = false;
        if (!Features.on(GUIDE) || n <= 40 || !guideMenu(LegacyText.lore(menu.slots.get(40).getItem()))) return;
        inGuide = true;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (!st.isEmpty() && todo(LegacyText.lore(st))) GUIDE_SLOTS.add(i);
        }
    }

    static void onMenuClosed() {
        inGuide = false;
        GUIDE_SLOTS.clear();
    }

    static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (inGuide && GUIDE_SLOTS.contains(slot.getContainerSlot())) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80FFFF55);
    }

    // --- horsezooka, temporal pillar, healing melon ---

    // from skyhanni RiftHorsezookaHider
    public static boolean hidden(Entity e) {
        return e instanceof Horse && Features.on(HORSEZOOKA) && HORSEZOOKA_ID.equals(RiftScan.heldId(Minecraft.getInstance()));
    }

    private static final List<Vec3> PILLARS = new ArrayList<>();

    // from skyhanni RiftApi.onSecondPassed: nodes within 7 blocks of a temporal pillar arent a way to go
    private static void tickPillars(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.tickCount % 20 != 0) return;
        PILLARS.clear();
        if (io.github.zmuzy.sbc.nav.Navigator.target() != null) {
            for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(128), Entity::hasCustomName))
                if (GameState.strip(st.getName().getString()).contains("Temporal Pillar")) PILLARS.add(st.position());
        }
        io.github.zmuzy.sbc.nav.Navigator.avoid = PILLARS.isEmpty() ? null : node -> {
            for (Vec3 p : PILLARS) if (node.pos().distanceTo(p) < 7) return true;
            return false;
        };
    }

    // from skyhanni HealingMelonIndicator.updateHealth: a title while youre low in the stillgore chateau, health in hearts
    private static void tickMelon(Minecraft mc) {
        if (mc.player == null || mc.player.tickCount % 20 != 0 || !RiftApi.inStillgoreChateau()) return;
        Feature f = Features.get(MELON);
        Option o = f == null ? null : f.option("rift_healing_melon.hearts");
        double hearts = o == null ? 4 : o.number();
        if (mc.player.getHealth() > hearts * 2) return;
        mc.gui.hud.setTimes(0, 25, 5);
        mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§4Heal now!"));
    }

    public static void register() {
        Features.add(new Feature(SPLATTER, "Rift", "Splatter hearts", "In the Stillgore Château, marks the hearts a Splatter Crux took from you.", "SkyHanni: Splatter Hearts")
                .when(Rift::rift).world(RiftMarks::drawHearts).left(() -> {
                    CURRENT_HEARTS.clear();
                    shownHearts = Set.of();
                }));
        Features.add(new Feature(VERMIN, "Rift", "Vermin marks", "Marks the vermin in the West Village while you hold the Turbomax Vacuum.", "SkyHanni: Vermin Highlighter")
                .when(Rift::rift).tick(mc -> {
                    if (mc.player != null && mc.player.tickCount % 5 == 0) tickVermin(mc);
                }).world(w -> drawEntities(w, VERMINS, RiftScan.color(VERMIN, "rift_vermin_marks.color", 0x3C0000FF))).left(VERMINS::clear)
                .option(Option.color("rift_vermin_marks.color", "Colour", "", 0x3C0000FF)));
        Features.add(new Feature(WOODS, "Rift", "Wyld Woods", "Marks Larvas while you hold the Larva Hook and Odonatas while you hold an empty bottle, and warns you to look away when a Shy Crux is about to steal your time.", "SkyHanni: Wyld Woods")
                .when(Rift::rift).tick(RiftMarks::tickWoods).world(w -> {
                    drawEntities(w, LARVAS, RiftScan.color(WOODS, "rift_wyld_woods.larva_color", 0x780D31FF));
                    drawEntities(w, ODONATAS, RiftScan.color(WOODS, "rift_wyld_woods.odonata_color", 0x780D31FF));
                })
                .left(() -> {
                    LARVAS.clear();
                    ODONATAS.clear();
                })
                .option(Option.bool("rift_wyld_woods.larvas", "Larvas", "", true))
                .option(Option.color("rift_wyld_woods.larva_color", "Larva colour", "", 0x780D31FF))
                .option(Option.bool("rift_wyld_woods.odonatas", "Odonatas", "", true))
                .option(Option.color("rift_wyld_woods.odonata_color", "Odonata colour", "", 0x780D31FF))
                .option(Option.bool("rift_wyld_woods.shy", "Shy Crux warning", "A title when a Shy Crux is close, for playing without sound.", true)));
        Features.add(new Feature(GUIDE, "Rift", "Rift Guide marks", "In the Rift Guide, what you have not done yet stands out in yellow.", "SkyHanni: Highlight Guide")
                .when(Rift::rift));
        Features.add(new Feature(HORSEZOOKA, "Rift", "Hide Horsezooka horses", "Hides horses while you hold the Horsezooka.", "SkyHanni: Horsezooka Hider")
                .when(Rift::rift).defaultOff());
        Features.add(new Feature(PILLAR, "Rift", "Temporal Pillar dodge", "Navigation in the Rift goes around the big enderman that throws you back to the end credits.", "SkyHanni: Temporal Pillar Dodge")
                .when(Rift::rift).tick(RiftMarks::tickPillars).left(() -> {
                    PILLARS.clear();
                    io.github.zmuzy.sbc.nav.Navigator.avoid = null;
                }));
        Features.add(new Feature(MELON, "Rift", "Healing Melon warning", "A title when your health in the Stillgore Château falls to the number of hearts below.", "Skyblocker: Healing Melon Indicator")
                .when(Rift::rift).tick(RiftMarks::tickMelon)
                .option(Option.number("rift_healing_melon.hearts", "Hearts", "", 4, 1, 20, 1)));
        ParticleHooks.listen(RiftMarks::onParticle);
    }
}
