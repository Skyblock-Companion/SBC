// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.track.Watcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// stuff drawn on top of item slots in any inventory: rarity glow, small level/tier numbers, lock marks
public final class SlotInfo {
    private record Info(int rarity, String text, int color) {}

    private static final dev.zmuzy.sbc.core.Patterns.P PET = dev.zmuzy.sbc.core.Patterns.p("SlotInfo.PET");
    private static final dev.zmuzy.sbc.core.Patterns.P ROMAN_END = dev.zmuzy.sbc.core.Patterns.p("SlotInfo.ROMAN_END");
    private static final dev.zmuzy.sbc.core.Patterns.P ROMAN_LORE = dev.zmuzy.sbc.core.Patterns.p("SlotInfo.ROMAN_LORE");
    private static final Map<ItemStack, Info> CACHE = new WeakHashMap<>();
    private static int sweep;

    private SlotInfo() {}

    private static Info info(ItemStack st) {
        Info c = CACHE.get(st);
        if (c != null) return c;
        String name = GameState.strip(st.getHoverName().getString());
        int rarity = dev.zmuzy.sbc.ui.DripKit.rarity(st);
        String text = "";
        int color = 0xFFFFFFFF;
        Matcher m;
        String id = dev.zmuzy.sbc.core.Gain.sbId(st);
        if ((m = PET.matcher(name)).find()) {
            text = m.group(1);
            // both mods skip the number on a pet at max level
            boolean top = "200".equals(text) || "100".equals(text) && (!name.contains("Dragon") || name.contains("Ender Dragon"));
            if (top && !dev.zmuzy.sbc.item.menus.MenuMarks.setting("slotinfo", "slotinfo.max_pet_level")) text = "";
            color = 0xFFFFD36B;
        } else if (id.equals("ENCHANTED_BOOK")) {
            List<String> lore = Watcher.lore(st);
            if (!lore.isEmpty() && (m = ROMAN_LORE.matcher(lore.get(0))).find()) {
                text = String.valueOf(GameState.roman(m.group(2)));
                color = 0xFFB7E57A;
            }
        } else if (id.contains("_GENERATOR_") && (m = ROMAN_END.matcher(name)).find()) {
            text = String.valueOf(GameState.roman(m.group(1)));
            color = 0xFF8FD3FF;
        } else if (name.contains("✪")) {
            text = String.valueOf(name.chars().filter(ch -> ch == '✪').count()) + "✪";
            color = 0xFFFFC857;
        }
        Info i = new Info(rarity, text, color);
        if (++sweep > 4000) {
            sweep = 0;
            CACHE.clear();
        }
        CACHE.put(st, i);
        return i;
    }

    // before the item: rarity glow behind it
    public static void back(GuiGraphicsExtractor g, Slot slot) {
        int mark = dev.zmuzy.sbc.ui.panels.Panels.slotColor(slot);
        if (mark != 0) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, mark);
        ItemStack st = slot.getItem();
        if (Features.on("slotinfo") && !st.isEmpty()) dev.zmuzy.sbc.item.menus.SlotBackgrounds.draw(g, slot.x, slot.y, st, dev.zmuzy.sbc.item.menus.SlotBackgrounds.rarity(info(st).rarity()));
        dev.zmuzy.sbc.item.menus.MenuMarks.back(g, slot);
        dev.zmuzy.sbc.item.menus.MenuExtras.maskBack(g, slot);
    }

    // skyblocker SlotLockStyle: classic dims the slot under the padlock, fancy leaves the item bright
    private static boolean fancyLock() {
        var f = Features.get("slotlocks");
        var o = f == null ? null : f.option("slotlocks.lockStyle");
        return o != null && o.choice().equals("FANCY");
    }

    // after the item: small number + lock or protection marks
    public static void front(GuiGraphicsExtractor g, Slot slot) {
        ItemStack st = slot.getItem();
        var font = Minecraft.getInstance().font;
        var mark = dev.zmuzy.sbc.dungeon.Puzzles.mark(slot);
        if (mark != null) {
            g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, mark.color());
            if (!mark.text().isEmpty()) g.text(font, mark.text(), slot.x + 8 - font.width(mark.text()) / 2, slot.y + 4, 0xFFFFFFFF, true);
        }
        if (Features.on("slotinfo") && !st.isEmpty()) {
            Info i = info(st);
            if (!i.text().isEmpty()) {
                var p = g.pose();
                p.pushMatrix();
                p.translate(slot.x + 0.5f, slot.y + 0.5f);
                p.scale(0.75f, 0.75f);
                g.text(font, i.text(), 0, 0, i.color(), true);
                p.popMatrix();
            }
        }
        if (Features.on("slotlocks")) {
            boolean locked = SlotLocks.isLocked(slot);
            if (locked && !fancyLock()) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x66000000);
            if (locked || SlotLocks.isProtected(st)) {
                int gx = slot.x + 10, gy = slot.y + 9;
                if (locked) {
                    // tiny padlock, shackle on top, body below
                    g.fill(gx + 1, gy, gx + 5, gy + 1, 0xFF000000);
                    g.fill(gx + 1, gy + 1, gx + 2, gy + 3, 0xFF000000);
                    g.fill(gx + 4, gy + 1, gx + 5, gy + 3, 0xFF000000);
                    g.fill(gx, gy + 3, gx + 6, gy + 7, 0xFF000000);
                    g.fill(gx + 1, gy + 4, gx + 5, gy + 6, 0xFFFFC857);
                } else {
                    g.fill(gx, gy, gx + 6, gy + 6, 0xFF000000);
                    g.fill(gx + 1, gy + 1, gx + 5, gy + 5, 0xFF4CE08A);
                }
            }
        }
    }
}
