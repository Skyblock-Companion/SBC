// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// spirit leap menu stays hypixels. on its heads goes what skyhanni DungeonSpiritLeapOverlay + skyblocker
// LeapOverlay put in their own screens: class color, class level, dead or offline
public final class LeapMenu {
    private static final Patterns.P MENU = Patterns.p("SH.dungeon.spirit-leap.menu");

    // one head in the menu
    public record Target(int slot, String name, String dungeonClass, int level, boolean dead, boolean offline) {}

    private static List<Target> cached = List.of();
    private static Object cachedFor;
    private static long cachedAt;

    private LeapMenu() {}

    public static boolean isTitle(String title) {
        return MENU.matcher(title).matches();
    }

    // from skyhanni collectPlayerStacks + sortByClass, with skyblocker LeapOverlays dead + offline from the lore
    static List<Target> targets(AbstractContainerScreen<?> cs) {
        List<Target> out = new ArrayList<>();
        var slots = cs.getMenu().slots;
        int size = Math.max(0, slots.size() - 36);
        for (int i = 0; i < size; i++) {
            ItemStack st = slots.get(i).getItem();
            if (!st.is(Items.PLAYER_HEAD)) continue;
            List<String> lore = LegacyText.lore(st);
            if (lore.isEmpty()) continue;
            String name = GameState.strip(st.getHoverName().getString()).trim();
            String all = GameState.strip(String.join(" ", lore)).toLowerCase(Locale.ENGLISH);
            DungeonRun.Member m = DungeonRun.member(name);
            String cls = m != null && !m.dungeonClass().isEmpty() ? m.dungeonClass() : Puzzles.classOf(name);
            out.add(new Target(i, name, cls, m == null ? 0 : m.level(), all.contains("dead") || (m != null && m.dead()), all.contains("offline")));
        }
        out.sort(Comparator.comparingInt((Target t) -> {
            int o = DungeonRun.CLASSES.indexOf(t.dungeonClass());
            return o < 0 ? DungeonRun.CLASSES.size() : o;
        }));
        return out;
    }

    static int slotOfTarget(AbstractContainerScreen<?> cs, int index) {
        List<Target> t = targets(cs);
        return index < t.size() ? t.get(index).slot() : -1;
    }

    // class color from the settings
    static int classColor(String cls) {
        return switch (cls) {
            case "Archer" -> DungeonMarks.color("dungeon_leap", "archer", 0xFF55FF55);
            case "Berserk" -> DungeonMarks.color("dungeon_leap", "berserk", 0xFFFF5555);
            case "Healer" -> DungeonMarks.color("dungeon_leap", "healer", 0xFFFF55FF);
            case "Mage" -> DungeonMarks.color("dungeon_leap", "mage", 0xFF55AAFF);
            case "Tank" -> DungeonMarks.color("dungeon_leap", "tank", 0xFF999999);
            default -> 0;
        };
    }

    // one menu slot: color, class letter, level, key name. true = this is the menu
    static boolean draw(GuiGraphicsExtractor g, Slot slot, String title) {
        if (!Features.on("dungeon_leap") || !isTitle(title)) return false;
        var s = Minecraft.getInstance().gui.screen();
        if (!(s instanceof AbstractContainerScreen<?> cs)) return true;
        long now = System.currentTimeMillis();
        if (cachedFor != cs || now - cachedAt > 200) {
            cachedFor = cs;
            cachedAt = now;
            cached = targets(cs);
        }
        for (int i = 0; i < cached.size(); i++) {
            Target t = cached.get(i);
            if (t.slot() != slot.index) continue;
            Font font = Minecraft.getInstance().font;
            int color = t.dead() ? DungeonMarks.color("dungeon_leap", "dead", 0xFF780000) : classColor(t.dungeonClass());
            if (color != 0) g.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, (0x90 << 24) | (color & 0xFFFFFF));
            String letter = t.dungeonClass().isEmpty() ? "" : t.dungeonClass().substring(0, 1);
            if (DungeonMarks.opt("dungeon_leap", "show_level") && t.level() > 0) letter += t.level();
            if (t.offline()) letter = "off";
            if (!letter.isEmpty()) g.text(font, letter, slot.x + 16 - font.width(letter), slot.y + 9, 0xFFFFFFFF, true);
            if (DungeonMarks.opt("dungeon_leap", "keys") && DungeonMarks.opt("dungeon_leap", "key_hints")) {
                String key = DungeonKeys.leapName(i);
                if (!key.isEmpty()) g.text(font, key, slot.x, slot.y, 0xFFFFFF55, true);
            }
            return true;
        }
        return true;
    }
}
