// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.menus;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.mixin.ContainerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

// the one way to say "in the menu with this title, this slot gets this color or number"
// rules run when the menu changes (a slots stack is a different one) and max 10x a sec, never per slot per
// frame. drawing only reads the result
// item.SlotInfo draws the colors behind items, item.ItemNumbers the numbers, overlay() everything over the items
public final class MenuMarks {
    public interface Rule {
        void mark(MenuView view);
    }

    private static final class Entry {
        final String feature;
        final Predicate<String> title;
        final Rule rule;
        String lastTitle;
        boolean lastIn, failed;

        Entry(String feature, Predicate<String> title, Rule rule) {
            this.feature = feature;
            this.title = title;
            this.rule = rule;
        }

        boolean in(String t) {
            if (!t.equals(lastTitle)) {
                lastTitle = t;
                lastIn = title.test(t);
                failed = false;
            }
            return lastIn;
        }
    }

    private static final List<Entry> RULES = new ArrayList<>();
    private static final Map<ItemStack, MenuView.Item> READ = new IdentityHashMap<>();
    private static final Map<ItemStack, MenuView.Mark> BY_STACK = new IdentityHashMap<>();

    private static AbstractContainerScreen<?> screen;
    private static ItemStack[] seen = new ItemStack[0];
    private static MenuView view;
    private static boolean dirty;
    private static long checkedAt, builtAt;

    private MenuMarks() {}

    public static void add(String feature, Predicate<String> title, Rule rule) {
        RULES.add(new Entry(feature, title, rule));
    }

    // rule for one title, whole title
    public static void add(String feature, String title, Rule rule) {
        add(feature, title::equals, rule);
    }

    public static void add(String feature, Pattern title, Rule rule) {
        add(feature, t -> title.matcher(t).matches(), rule);
    }

    // something a rule looks at changed outside the menu, like a choice in a side panel
    public static void invalidate() {
        dirty = true;
    }

    // the open menus screen, for stuff that has to notice it got opened again
    static Object screenNow() {
        return screen;
    }

    // a features bool setting, on if the setting doesnt exist
    static boolean on(String feature, String key) {
        Feature f = Features.get(feature);
        if (f == null) return true;
        Option o = f.option(key);
        return o == null || o.bool();
    }

    public static boolean setting(String feature, String key) {
        return on(feature, key);
    }

    // runs the matching rules over a view, for the game and tests
    static void run(MenuView v) {
        for (Entry e : RULES) {
            if (e.failed || !e.in(v.title) || !Features.on(e.feature)) continue;
            try {
                e.rule.mark(v);
            } catch (Throwable t) {
                e.failed = true;
                SbcMod.LOG.warn("menu rule of {} failed in {}", e.feature, v.title, t);
            }
        }
    }

    private static void clear() {
        screen = null;
        view = null;
        seen = new ItemStack[0];
        READ.clear();
        BY_STACK.clear();
    }

    private static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        Screen now = mc == null || mc.gui == null ? null : mc.gui.screen();
        if (!(now instanceof AbstractContainerScreen<?> s)) {
            if (screen != null) clear();
            return;
        }
        long time = System.currentTimeMillis();
        if (s == screen && !dirty && time - checkedAt < 100) return;
        checkedAt = time;
        List<Slot> slots = s.getMenu().slots;
        boolean build = s != screen || dirty || time - builtAt > 1000 || seen.length != slots.size();
        if (!build) {
            for (int i = 0; i < seen.length; i++) {
                if (slots.get(i).getItem() != seen[i]) {
                    build = true;
                    break;
                }
            }
        }
        if (!build) return;
        if (s != screen) READ.clear();
        screen = s;
        dirty = false;
        builtAt = time;
        if (READ.size() > 400) READ.clear();
        int size = slots.size(), top = 0;
        seen = new ItemStack[size];
        MenuView.Item[] items = new MenuView.Item[size];
        for (int i = 0; i < size; i++) {
            Slot slot = slots.get(i);
            ItemStack st = slot.getItem();
            seen[i] = st;
            items[i] = READ.computeIfAbsent(st, MenuView.Item::of);
            if (!(slot.container instanceof Inventory)) top = i + 1;
        }
        MenuView v = new MenuView(GameState.strip(s.getTitle().getString()).trim(), items, top);
        run(v);
        view = v;
        BY_STACK.clear();
        for (int i = 0; i < size; i++) if (v.marks[i] != null && !seen[i].isEmpty()) BY_STACK.put(seen[i], v.marks[i]);
    }

    // pictures and tests with no game open: the menu panels read
    public static void showForTest(MenuView v) {
        view = v;
    }

    // open menu, read max 10x a sec, null if none
    public static MenuView current() {
        refresh();
        return view;
    }

    private static MenuView.Mark mark(Slot slot) {
        refresh();
        MenuView v = view;
        if (v == null) return null;
        int i = slot.index;
        return i >= 0 && i < v.marks.length ? v.marks[i] : null;
    }

    // called before a slots item is drawn
    public static void back(GuiGraphicsExtractor g, Slot slot) {
        MenuView.Mark m = mark(slot);
        if (m != null && m.back != 0) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, m.back);
    }

    // what goes where the stack count is, or null. one map lookup
    public static String number(ItemStack stack) {
        refresh();
        if (view == null) return null;
        MenuView.Mark m = BY_STACK.get(stack);
        return m == null ? null : m.number;
    }

    // stuff over the items, after theyre all drawn
    public static void overlay(Screen s, GuiGraphicsExtractor g, int mx, int my) {
        if (!(s instanceof AbstractContainerScreen<?> cs)) return;
        refresh();
        MenuView v = view;
        if (v == null || screen != cs) return;
        boolean any = false;
        for (MenuView.Mark m : v.marks) {
            if (m != null && (m.front != 0 || m.border != 0 || m.icon != null || m.badge != null || m.tip != null || m.labels != null || m.corners[0] != null || m.corners[1] != null || m.corners[2] != null || m.corners[3] != null)) {
                any = true;
                break;
            }
        }
        if (!any) return;
        ContainerAccess a = (ContainerAccess) cs;
        int left = a.sbc$left(), top = a.sbc$top();
        List<Slot> slots = cs.getMenu().slots;
        g.nextStratum();
        Font font = Minecraft.getInstance().font;
        boolean text = SlotTexts.showing();
        navBand = io.github.zmuzy.sbc.item.QuickNav.band(cs, top);
        for (int i = 0; i < v.marks.length && i < slots.size(); i++) {
            MenuView.Mark m = v.marks[i];
            if (m == null) continue;
            Slot slot = slots.get(i);
            if (!slot.isActive()) continue;
            draw(g, font, m, left + slot.x, top + slot.y, text);
        }
        int hover = hoveredSlot();
        if (hover >= 0 && hover < v.marks.length && v.marks[hover] != null && v.marks[hover].tip != null) {
            List<net.minecraft.network.chat.Component> lines = new ArrayList<>();
            for (String l : v.marks[hover].tip) lines.add(net.minecraft.network.chat.Component.literal(l));
            g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), mx, my, null);
        }
    }

    // one slots marks at a spot on screen, the pictures use this too
    private static int[] navBand;

    public static void draw(GuiGraphicsExtractor g, Font font, MenuView.Mark m, int x, int y, boolean cornerText) {
        if (m.icon != null) g.item(m.icon, x, y);
        if (m.badge != null) {
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + 8, y + 8);
            pose.scale(0.5f, 0.5f);
            g.item(m.badge, 0, 0);
            pose.popMatrix();
        }
        if (m.front != 0) g.fill(x, y, x + 16, y + 16, m.front);
        if (m.border != 0) {
            g.fill(x, y, x + 16, y + 1, m.border);
            g.fill(x, y + 15, x + 16, y + 16, m.border);
            g.fill(x, y + 1, x + 1, y + 15, m.border);
            g.fill(x + 15, y + 1, x + 16, y + 15, m.border);
        }
        if (cornerText) for (int c = 0; c < 4; c++) if (m.corners[c] != null) corner(g, font, m.corners[c], m.cornerColors[c], c, x, y);
        if (m.labels != null) {
            for (MenuView.Label l : m.labels) {
                int ly = y + l.dy();
                // a label above the chest would sit on the quick nav buttons, move it above them
                if (navBand != null && ly < navBand[1] && ly + 10 > navBand[0]) ly = navBand[0] - 11;
                var p = g.pose();
                p.pushMatrix();
                p.translate(x + l.dx(), ly);
                p.scale(l.scale(), l.scale());
                g.text(font, l.text(), 0, 0, 0xFFFFFFFF, true);
                p.popMatrix();
            }
        }
    }

    // skyblocker SlotTextManager.extractSlotText: corner text, shrunk when it doesnt fit the slot
    private static void corner(GuiGraphicsExtractor g, Font font, String text, int color, int corner, int x, int y) {
        int w = font.width(text);
        float scale = w > 16 ? 16f / w : 1f;
        float drawn = w * scale;
        float tx = corner == MenuView.TOP_RIGHT || corner == MenuView.BOTTOM_RIGHT ? x + 16 - drawn : x;
        float ty = corner == MenuView.BOTTOM_LEFT || corner == MenuView.BOTTOM_RIGHT ? y + 16 - (font.lineHeight - 2) * scale : y;
        var p = g.pose();
        p.pushMatrix();
        p.translate(tx, ty);
        if (scale != 1f) p.scale(scale, scale);
        g.text(font, text, 0, 0, color, true);
        p.popMatrix();
    }

    // slot under the mouse in the open menu, -1 if none
    public static int hoveredSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s)) return -1;
        Slot hovered = ((ContainerAccess) s).sbc$hovered();
        return hovered == null ? -1 : hovered.index;
    }
}
