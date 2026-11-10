// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

// from skyhanni rift/westvillage/kloon KloonHacking.kt + KloonTerminal.kt: buttons to press in the hacking
// menu, which color goes with the terminal youre at, a marker on each terminal
public final class KloonHacking {
    public static final String ID = "rift_kloon";
    static final Patterns.P COLOR = Patterns.p("SH.rift.area.westvillage.kloon.color");
    private static final String VISOR = "RETRO_ENCABULATING_VISOR";
    private static final int GREEN = 0x8055FF55, RED = 0x80FF5555, YELLOW = 0x80FFFF55;

    // from skyhanni KloonTerminal
    enum Terminal {
        RED(-69, 65, -63), ORANGE(-44, 71, -62), YELLOW(-39, 71, -95), GREEN(-62, 71, -83), AQUA(-33, 70, -134.5), BLUE(-66.5, 72, -119), PURPLE(-89, 73, -115), PINK(-110, 73, -107);
        final Vec3 location;

        Terminal(double x, double y, double z) {
            location = new Vec3(x, y, z);
        }
    }

    private static boolean wearingHelmet, inTerminal, inColor;
    private static final List<String> CORRECT = new ArrayList<>();
    private static Terminal nearest;

    private KloonHacking() {}

    // from skyhanni getNearestTerminal: within 8 blocks
    static Terminal nearestTerminal(Vec3 me) {
        Terminal best = null;
        double bestD = 8;
        for (Terminal t : Terminal.values()) {
            double d = t.location.distanceTo(me);
            if (d < bestD) {
                best = t;
                bestD = d;
            }
        }
        return best;
    }

    // color of a hacking slot, 0 = none. row i is one letter of the answer in slots 2 to 6: the slot where the
    // row has to stand is green or red, any other slot with the right letter shows too
    static int hackingColor(int slot, String name, List<String> correct) {
        if (slot < 9) return 0;
        int i = slot / 9 - 1;
        if (i >= correct.size()) return 0;
        if (slot == 11 + 10 * i) return name.equals(correct.get(i)) ? GREEN : RED;
        return name.equals(correct.get(i)) ? YELLOW : 0;
    }

    static void onMenu(String title, ChestMenu menu, int n) {
        inTerminal = inColor = false;
        nearest = null;
        if (!Features.on(ID)) return;
        if (title.equals("Hacking") || title.equals("Hacking (As seen on CSI)")) {
            if (!RiftScan.on(ID, "rift_kloon.solver", true)) return;
            inTerminal = true;
            CORRECT.clear();
            for (int s = 2; s <= 6 && s < n; s++) CORRECT.add(LegacyText.cleanName(menu.slots.get(s).getItem()));
        } else if (title.equals("Hacked Terminal Color Picker") && RiftScan.on(ID, "rift_kloon.color", true)) {
            inColor = true;
        }
    }

    static void onMenuClosed() {
        inTerminal = inColor = false;
    }

    static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (!inTerminal && !inColor) return;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || slot.container == mc.player.getInventory()) return;
        if (inTerminal) {
            int c = hackingColor(slot.getContainerSlot(), LegacyText.cleanName(st), CORRECT);
            if (c != 0) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, c);
            return;
        }
        if (nearest == null) nearest = nearestTerminal(mc.player.position());
        if (nearest == null) return;
        for (String l : LegacyText.lore(st)) {
            if (!l.contains(nearest.name())) continue;
            g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREEN);
            return;
        }
    }

    // from skyhanni onChat with the clean line. true = finished the terminal youre at
    static boolean onChat(String clean) {
        if (!wearingHelmet) return false;
        Matcher m = COLOR.matcher(clean.trim());
        if (!m.matches()) return false;
        Terminal done = null;
        for (Terminal t : Terminal.values()) if (t.name().equals(m.group("color"))) done = t;
        if (done == null || done != nearest) return false;
        List<String> list = RiftEnv.profile().completedKloonTerminals;
        if (!list.contains(done.name())) list.add(done.name());
        return true;
    }

    private static void draw(WorldDraw w) {
        if (!wearingHelmet || !RiftScan.on(ID, "rift_kloon.waypoints", true)) return;
        List<String> done = RiftEnv.profile().completedKloonTerminals;
        for (Terminal t : Terminal.values()) if (!done.contains(t.name())) w.waypoint(t.location, 0xFFAA0000, "§4" + t.name().charAt(0) + t.name().substring(1).toLowerCase(java.util.Locale.ROOT) + " terminal", true, true);
    }

    public static void register() {
        Features.add(new Feature(ID, "Rift", "Kloon hacking", "In the West Village: the right letters in the hacking menu, which colour fits the terminal you stand at, and a marker on each terminal left while you wear the visor.", "SkyHanni: Kloon Hacking")
                .when(Rift::rift)
                .tick(mc -> {
                    if (mc.player != null && mc.player.tickCount % 20 == 0) wearingHelmet = VISOR.equals(Gain.sbId(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
                })
                .world(KloonHacking::draw)
                .left(() -> {
                    wearingHelmet = false;
                    inTerminal = inColor = false;
                    nearest = null;
                    CORRECT.clear();
                })
                .option(Option.bool("rift_kloon.solver", "Hacking solver", "Colours the right button in each row of the hacking menu.", true))
                .option(Option.bool("rift_kloon.color", "Colour guide", "Marks the colour to pick for the terminal you stand at.", true))
                .option(Option.bool("rift_kloon.waypoints", "Terminal markers", "A marker on each terminal left, while you wear the visor.", true)));
    }
}
