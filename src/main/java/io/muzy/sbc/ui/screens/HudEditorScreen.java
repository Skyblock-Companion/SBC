// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.screens;

//? if >=26.3 {
import com.mojang.blaze3d.platform.InputConstants;
//?}
import io.muzy.sbc.core.Config;
import io.muzy.sbc.ui.HudGroups;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.TabList;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.hud.ElementStyle;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.hud.HudPresets;
import io.muzy.sbc.ui.theme.Theme;
import io.muzy.sbc.ui.layout.UIComponents;
import io.muzy.sbc.ui.layout.FlowLayout;
import io.muzy.sbc.ui.layout.UIContainers;
import io.muzy.sbc.ui.layout.Color;
import io.muzy.sbc.ui.layout.Insets;
import io.muzy.sbc.ui.layout.UIGraphics;
import io.muzy.sbc.ui.layout.Positioning;
import io.muzy.sbc.ui.layout.Sizing;
import io.muzy.sbc.ui.layout.Surface;
import io.muzy.sbc.ui.layout.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
//? if <26.3 {
/*import org.lwjgl.glfw.GLFW;
*///?}

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

// hud editor. whole hud drawn live on the left (demo stuff where theres nothing), drag it (snaps to center
// lines, edges, other elements), resize it, style it on the side panel
public final class HudEditorScreen extends SbcScreen {
    private static final int SNAP = 5, PANEL_W = 170;

    private String selected = "", dragging = "";
    private double grabX, grabY;
    private int guideX = -1, guideY = -1;
    private FlowLayout side;
    private io.muzy.sbc.ui.layout.ScrollContainer<FlowLayout> sideScroll;
    private boolean panelHidden = true;
    private Canvas canvas;
    private FlowLayout toolbar;
    private boolean tabMode;
    private String tabSelected = "", tabDragging = "";
    // where the other cards sat when the drag started, so the ones a move pushes get marked
    private java.util.Map<String, int[]> before = java.util.Map.of();
    private boolean dragWasPinned;
    // right click menu: on which card + where it opened
    private String menuFor = "";
    private int menuX, menuY;
    private final List<MenuHit> menuHits = new java.util.ArrayList<>();
    // the setup note closed for this time (OK), "don't show again" is the config one
    private boolean noteClosed;
    private FlowLayout noteBar;

    private record MenuHit(int x, int y, int w, int h, Runnable run) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    static final String SETUP_NOTE = "The mod placed the cards you picked in Setup. Moving a card pins it where you put it, and it won't arrange itself any more. Reset layout brings this back.";

    // after Setup placed the cards, until "don't show again"
    static boolean showsSetupNote() {
        return Config.I.setupDone && !Config.I.hudSetupNoteOff;
    }

    public HudEditorScreen(Screen parent) {
        super("Edit HUD", parent);
    }

    private static boolean shift() {
        long w = Minecraft.getInstance().getWindow().handle();
        //? if >=26.3 {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        //?} else {
        /*return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        *///?}
    }

    private static boolean ctrl() {
        long w = Minecraft.getInstance().getWindow().handle();
        //? if >=26.3 {
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
        //?} else {
        /*return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        *///?}
    }

    static UIComponent ui(Object vanillaBased) {
        return (UIComponent) vanillaBased;
    }

    // --- canvas ---

    private final class Canvas extends UiComponent {
        Canvas() {
            super((ui, g, x, y, w, h, mx, my) -> paint(ui, g, mx, my), 100, 100);
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent e, boolean doubleClick) {
            if (tabMode) {
                TabList.Card card = cardAt(e.x(), e.y());
                tabSelected = card == null ? "" : card.key();
                tabDragging = tabSelected;
                rebuildSide();
                return true;
            }
            if (!menuFor.isEmpty()) {
                for (MenuHit h : List.copyOf(menuHits)) {
                    if (!h.contains(e.x(), e.y())) continue;
                    h.run().run();
                    Config.save();
                    rebuildSide();
                    return true;
                }
                // a click anywhere else only closes the menu
                closeMenu();
                return true;
            }
            List<HudLayout.Placed> all = placed();
            for (int i = all.size() - 1; i >= 0; i--) {
                HudLayout.Placed p = all.get(i);
                if (!p.contains(e.x(), e.y())) continue;
                //? if >=26.3 {
                if (e.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                //?} else {
                /*if (e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                *///?}
                    menuFor = p.id;
                    menuX = (int) e.x();
                    menuY = (int) e.y();
                    return true;
                }
                select(p.id);
                HudPresets.mark();
                dragging = p.id;
                grabX = e.x() - p.x;
                grabY = e.y() - p.y;
                dragWasPinned = p.pinned;
                java.util.Map<String, int[]> at = new java.util.HashMap<>();
                for (HudLayout.Placed o : all) if (!o.id.equals(p.id)) at.put(o.id, new int[]{o.x, o.y});
                before = at;
                return true;
            }
            select("");
            return true;
        }

        @Override
        public boolean onMouseDrag(MouseButtonEvent e, double dx, double dy) {
            if (tabMode) return true;
            if (dragging.isEmpty()) return false;
            HudLayout.Placed p = find(dragging);
            if (p == null) return true;
            int nx = (int) Math.round(e.x() - grabX), ny = (int) Math.round(e.y() - grabY);
            guideX = guideY = -1;
            int cx = nx + p.w / 2, cy = ny + p.h / 2;
            int sw = HudEditorScreen.this.width, sh = HudEditorScreen.this.height;
            // every snap draws its line: the center, the screen edge or the other cards edge
            if (Math.abs(cx - sw / 2) <= SNAP) {
                nx = sw / 2 - p.w / 2;
                guideX = sw / 2;
            } else if (Math.abs(nx) <= SNAP) {
                nx = 0;
                guideX = 0;
            } else if (Math.abs(nx + p.w - sw) <= SNAP) {
                nx = sw - p.w;
                guideX = sw - 1;
            }
            if (Math.abs(cy - sh / 2) <= SNAP) {
                ny = sh / 2 - p.h / 2;
                guideY = sh / 2;
            } else if (Math.abs(ny) <= SNAP) {
                ny = 0;
                guideY = 0;
            } else if (Math.abs(ny + p.h - sh) <= SNAP) {
                ny = sh - p.h;
                guideY = sh - 1;
            }
            // snap to other elements edges
            for (HudLayout.Placed o : placed()) {
                if (o.id.equals(p.id)) continue;
                if (Math.abs(nx - (o.x + o.w + 4)) <= SNAP) {
                    nx = o.x + o.w + 4;
                    guideX = nx;
                }
                if (Math.abs(nx + p.w - (o.x - 4)) <= SNAP) {
                    nx = o.x - 4 - p.w;
                    guideX = o.x - 4;
                }
                if (Math.abs(nx - o.x) <= SNAP) {
                    nx = o.x;
                    guideX = o.x;
                }
                if (Math.abs(ny - (o.y + o.h + 4)) <= SNAP) {
                    ny = o.y + o.h + 4;
                    guideY = ny;
                }
                if (Math.abs(ny + p.h - (o.y - 4)) <= SNAP) {
                    ny = o.y - 4 - p.h;
                    guideY = o.y - 4;
                }
                if (Math.abs(ny - o.y) <= SNAP) {
                    ny = o.y;
                    guideY = o.y;
                }
            }
            HudLayout.pin(p.id, nx, ny);
            return true;
        }

        @Override
        public boolean onMouseUp(MouseButtonEvent e) {
            if (tabMode) {
                TabList.Card over = cardAt(e.x(), e.y());
                if (!tabDragging.isEmpty() && over != null && !over.key().equals(tabDragging)) {
                    TabList.moveBefore(tabDragging, over.key());
                    Config.save();
                }
                tabDragging = "";
                return true;
            }
            if (!dragging.isEmpty()) {
                dragging = "";
                before = java.util.Map.of();
                guideX = guideY = -1;
                Config.save();
                rebuildSide();
            }
            return true;
        }

        @Override
        public boolean onMouseScroll(double x, double y, double amount) {
            float d = amount > 0 ? 0.05f : -0.05f;
            if (tabMode) {
                Config.I.tabScale = Math.max(0.5f, Math.min(1.5f, Math.round((Config.I.tabScale + d) * 20) / 20f));
                Config.save();
                rebuildSide();
                return true;
            }
            if (shift()) {
                HudPresets.mark();
                HudLayout.setAll(Config.I.hudAll + d);
                Config.save();
                rebuildSide();
                return true;
            }
            String target = selected;
            for (HudLayout.Placed p : placed()) if (p.contains(x, y)) target = p.id;
            if (target.isEmpty()) return true;
            HudPresets.mark();
            select(target);
            HudLayout.setScale(target, HudLayout.scaleOf(target) + d);
            Config.save();
            rebuildSide();
            return true;
        }
    }

    private static TabList.Card cardAt(double x, double y) {
        for (TabList.Card c : TabList.cards()) if (x >= c.x() && y >= c.y() && x < c.x() + c.w() && y < c.y() + c.h()) return c;
        return null;
    }

    // color of whats selected (classic aqua)
    private static int mark() {
        return io.muzy.sbc.ui.theme.Modern.AQUA;
    }

    // key hints along the bottom on their own strip so they read over anything
    private static void hints(Ui ui, String text, int sw, int sh) {
        ui.rect(0, sh - 13, sw, 13, 0xE80A0F17);
        ui.rect(0, sh - 13, sw, 1, 0x24FFFFFF);
        ui.text(text, 4, sh - 11, io.muzy.sbc.ui.theme.Modern.LABEL);
    }

    private static void outline(Ui ui, int x, int y, int w, int h, int color) {
        ui.rect(x - 1, y - 1, w + 2, 1, color);
        ui.rect(x - 1, y + h, w + 2, 1, color);
        ui.rect(x - 1, y, 1, h, color);
        ui.rect(x + w, y, 1, h, color);
    }

    // tab list like when TAB is held, over the dimmed hud
    private void paintTabMode(Ui ui, UIGraphics g, int sw, int sh, int mouseX, int mouseY) {
        g.nextStratum();
        TabList.draw(g);
        ui.begin(g, mouseX, mouseY);
        TabList.Card over = cardAt(mouseX, mouseY);
        for (TabList.Card c : TabList.cards()) {
            boolean sel = c.key().equals(tabSelected), hv = over == c;
            if (sel || hv) outline(ui, c.x(), c.y(), c.w(), c.h(), sel ? mark() : 0xFFFFFFFF);
            if (!tabDragging.isEmpty() && hv && !c.key().equals(tabDragging)) ui.rect(c.x(), c.y() - 2, c.w(), 2, 0xFF55FF55);
        }
        if (!tabDragging.isEmpty()) ui.textShadow(tabDragging.toLowerCase(Locale.ROOT).replace('_', ' '), mouseX + 8, mouseY + 2, mark());
        if (TabList.cards().isEmpty()) ui.textCenter("Nothing in the tab list here", sw / 2, sh / 2, 0xFFFFFFFF);
        hints(ui, "Drag a card onto another to put it before that one  -  wheel: size  -  the panel hides and shows cards  -  Esc: done", sw, sh);
    }

    private List<HudLayout.Placed> placed() {
        return HudLayout.layout(width, height, true);
    }

    private HudLayout.Placed find(String id) {
        for (HudLayout.Placed p : placed()) if (p.id.equals(id)) return p;
        return null;
    }

    private void paint(Ui ui, UIGraphics g, int mouseX, int mouseY) {
        int sw = width, sh = height;
        ui.rect(0, 0, sw, sh, 0x50000000);
        ui.rect(sw / 2, 0, 1, sh, 0x30FFFFFF);
        ui.rect(0, sh / 2, sw, 1, 0x30FFFFFF);
        if (guideX >= 0) ui.rect(guideX, 0, 1, sh, Ui.alpha(mark(), 0.8f));
        if (guideY >= 0) ui.rect(0, guideY, sw, 1, Ui.alpha(mark(), 0.8f));
        HudLayout.render(ui, g, sw, sh, true);
        ui.begin(g, mouseX, mouseY);
        if (tabMode) {
            paintTabMode(ui, g, sw, sh, mouseX, mouseY);
            return;
        }
        List<HudLayout.Placed> all = placed();
        String hover = "";
        for (int i = all.size() - 1; i >= 0; i--)
            if (all.get(i).contains(mouseX, mouseY)) {
                hover = all.get(i).id;
                break;
            }
        for (HudLayout.Placed p : all) {
            boolean sel = p.id.equals(selected), hv = p.id.equals(hover);
            int col = sel ? mark() : hv ? 0xFFFFFFFF : p.visible ? 0x66FFFFFF : Ui.alpha(mark(), 0.4f);
            // collisions in red
            for (HudLayout.Placed o : all) {
                if (o == p || o.id.compareTo(p.id) <= 0) continue;
                if (p.x < o.x + o.w && o.x < p.x + p.w && p.y < o.y + o.h && o.y < p.y + p.h) {
                    int ix = Math.max(p.x, o.x), iy = Math.max(p.y, o.y);
                    ui.rect(ix, iy, Math.min(p.x + p.w, o.x + o.w) - ix, Math.min(p.y + p.h, o.y + o.h) - iy, 0x40FF4040);
                }
            }
            ui.rect(p.x - 1, p.y - 1, p.w + 2, 1, col);
            ui.rect(p.x - 1, p.y + p.h, p.w + 2, 1, col);
            ui.rect(p.x - 1, p.y, 1, p.h, col);
            ui.rect(p.x + p.w, p.y, 1, p.h, col);
            if ((sel || hv) && dragging.isEmpty()) {
                // name + size, under it what the card does on its own (arranges itself, pinned, hidden)
                String label = p.title + String.format(Locale.ROOT, "  %.0f%%", HudLayout.scaleOf(p.id) * Config.I.hudAll * 100);
                String state = state(p, sw, sh);
                int bw = Math.max(ui.width(label), ui.width(state)) + 8;
                int ly = p.y >= 22 ? p.y - 21 : p.y + p.h + 2;
                ui.rect(p.x, ly - 1, bw, 21, 0xE80A0F17);
                ui.text(label, p.x + 4, ly + 1, sel ? mark() : 0xFFFFFFFF);
                ui.text(state, p.x + 4, ly + 11, io.muzy.sbc.ui.theme.Modern.LABEL);
            }
        }
        if (!dragging.isEmpty()) paintDrag(ui, all, sw, sh, mouseX, mouseY);
        hints(ui, "Click: style · drag: move (pins it) · wheel: size (Shift: all) · right click: menu · arrows: nudge · Delete: reset card · P: panel · Esc: done", sw, sh);
        if (!menuFor.isEmpty()) paintMenu(ui, sw, sh, mouseX, mouseY);
    }

    // what a card does by itself, in the words the hover shows
    static String state(HudLayout.Placed p, int sw, int sh) {
        HudElement e = p.element;
        if (e != null && "Never".equals(e.showMode())) return "Hidden: not shown in game, the feature keeps working";
        if (p.pinned) return "Pinned where you put it";
        if (e != null && e.partOf() != null) return "Part of " + e.partOf().title + ", moves with it";
        if (e != null && e.homeAt(sw, sh) != null) return "Sits in its own spot";
        return "Automatic: moves with what's shown";
    }

    // while dragging: what letting go does, at the cursor, + every card the move pushes, marked before you let go
    private void paintDrag(Ui ui, List<HudLayout.Placed> all, int sw, int sh, int mouseX, int mouseY) {
        int warn = 0xFFFFAA00;
        for (HudLayout.Placed o : all) {
            int[] was = before.get(o.id);
            if (was == null) continue;
            int dx = o.x - was[0], dy = o.y - was[1];
            if (Math.abs(dx) + Math.abs(dy) <= 2) continue;
            outline(ui, was[0], was[1], o.w, o.h, Ui.alpha(warn, 0.45f));
            outline(ui, o.x, o.y, o.w, o.h, warn);
            String way = Math.abs(dy) >= Math.abs(dx) ? (dy > 0 ? "down" : "up") : (dx > 0 ? "right" : "left");
            String text = o.title + " will move " + way;
            int tw = ui.width(text) + 8;
            int tx = Math.max(0, Math.min(sw - tw, o.x)), ty = Math.max(0, Math.min(sh - 24, o.y + 2));
            ui.rect(tx, ty, tw, 11, 0xE80A0F17);
            ui.text(text, tx + 4, ty + 2, warn);
        }
        String hint = dragWasPinned ? "This card will stay here" : "This card will stay here, it won't arrange itself any more";
        int tw = ui.width(hint) + 8;
        int tx = mouseX + 12 + tw > sw ? mouseX - 12 - tw : mouseX + 12, ty = Math.max(0, Math.min(sh - 26, mouseY + 10));
        ui.rect(tx, ty, tw, 11, 0xE80A0F17);
        ui.text(hint, tx + 4, ty + 2, mark());
    }

    private void closeMenu() {
        menuFor = "";
        menuHits.clear();
    }

    // one entry of the right click menu: name, a line of what it does, and the click (null = a - / + row)
    private record Entry(String name, String line, Runnable run, String value, Runnable less, Runnable more) {
        static Entry action(String name, String line, Runnable run) {
            return new Entry(name, line, run, null, null, null);
        }

        static Entry stepper(String name, String line, String value, Runnable less, Runnable more) {
            return new Entry(name, line, null, value, less, more);
        }
    }

    private List<Entry> menuEntries(HudLayout.Placed p) {
        List<Entry> out = new java.util.ArrayList<>();
        String id = p.id;
        out.add(Entry.stepper("Size", "Changes right here, you see it as you go.", String.format(Locale.ROOT, "%.0f%%", HudLayout.scaleOf(id) * Config.I.hudAll * 100), () -> {
            HudPresets.mark();
            HudLayout.setScale(id, HudLayout.scaleOf(id) - 0.05f);
        }, () -> {
            HudPresets.mark();
            HudLayout.setScale(id, HudLayout.scaleOf(id) + 0.05f);
        }));
        if (p.element != null) {
            ElementStyle st = ElementStyle.of(id);
            float a = st.alpha() == null ? allOpacity() : st.alpha();
            out.add(Entry.stepper("Background opacity", "Changes right here, you see it as you go.", String.format(Locale.ROOT, "%.0f%%", a * 100), () -> {
                HudPresets.mark();
                st.alpha(Math.max(0f, Math.round((a - 0.1f) * 10) / 10f));
            }, () -> {
                HudPresets.mark();
                st.alpha(Math.min(1f, Math.round((a + 0.1f) * 10) / 10f));
            }));
            out.add(Entry.action("Style...", "Colours, border, padding and rows in the side panel, with a live preview.", () -> {
                closeMenu();
                select(id);
                panelHidden = false;
                placePanel();
            }));
        }
        if (p.pinned) out.add(Entry.action("Back to automatic", "Unpins it: it arranges itself again with what's shown. Its size stays.", () -> {
            HudPresets.mark();
            HudLayout.unpin(id);
            closeMenu();
        }));
        var f = p.element == null ? null : p.element.feature();
        var show = f == null ? null : f.option("show");
        if (show != null) {
            if ("Never".equals(p.element.showMode())) out.add(Entry.action("Show card again", "Shows again whenever it has something to show.", () -> {
                show.reset();
                closeMenu();
            }));
            else out.add(Entry.action("Hide card", "Won't be shown; the feature keeps working. Bring it back in settings (Show).", () -> {
                show.set("Never");
                closeMenu();
            }));
        }
        if (f != null && !f.alwaysOn) out.add(Entry.action("Turn feature off", "Turns the feature itself off, not just the card. Back on in settings (K).", () -> {
            f.setEnabled(false);
            closeMenu();
            select("");
        }));
        if (f != null) out.add(Entry.action("Open its settings", "Every option of this feature.", () -> {
            closeMenu();
            SettingsScreen.open(this, f.id);
        }));
        out.add(Entry.action("Reset card", "Back to automatic with its default size and style.", () -> {
            HudPresets.mark();
            HudLayout.reset(id);
            ElementStyle.reset(id);
            closeMenu();
        }));
        return out;
    }

    // words that fit in w, line by line
    static List<String> wrap(Ui ui, String text, int w) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && ui.width(next) > w) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else line = new StringBuilder(next);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    // small menu where you right clicked: each entry with what it does under it
    private void paintMenu(Ui ui, int sw, int sh, int mouseX, int mouseY) {
        menuHits.clear();
        HudLayout.Placed p = find(menuFor);
        if (p == null) {
            menuFor = "";
            return;
        }
        int w = 200, pad = 5;
        List<Entry> entries = menuEntries(p);
        List<List<String>> lines = new java.util.ArrayList<>();
        int h = 15;
        for (Entry en : entries) {
            List<String> l = wrap(ui, en.line(), w - pad * 2);
            lines.add(l);
            h += 12 + l.size() * 10 + 3;
        }
        h += 2;
        int x = Math.max(2, Math.min(sw - w - 2, menuX)), y = Math.max(2, Math.min(sh - h - 2, menuY));
        ui.rect(x, y, w, h, 0xF40A0F17);
        outline(ui, x, y, w, h, 0x50FFFFFF);
        ui.text(ui.ellipsize(p.title, w - pad * 2), x + pad, y + 4, mark());
        int ry = y + 15;
        for (int i = 0; i < entries.size(); i++) {
            Entry en = entries.get(i);
            int rh = 12 + lines.get(i).size() * 10 + 3;
            if (en.run() != null) {
                if (mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rh) ui.rect(x + 1, ry, w - 2, rh, 0x26FFFFFF);
                menuHits.add(new MenuHit(x, ry, w, rh, en.run()));
                ui.text(en.name(), x + pad, ry + 2, 0xFFFFFFFF);
            } else {
                // - value + on the right, each button its own click, the menu stays open
                ui.text(en.name(), x + pad, ry + 2, 0xFFFFFFFF);
                int bx = x + w - pad - 58;
                stepButton(ui, "-", bx, ry, mouseX, mouseY, en.less());
                ui.textCenter(en.value(), bx + 29, ry + 2, mark());
                stepButton(ui, "+", bx + 46, ry, mouseX, mouseY, en.more());
            }
            int ly = ry + 12;
            for (String l : lines.get(i)) {
                ui.text(l, x + pad, ly, io.muzy.sbc.ui.theme.Modern.LABEL);
                ly += 10;
            }
            ry += rh;
        }
        // last one: a click between the menu buttons does nothing instead of closing it
        menuHits.add(new MenuHit(x, y, w, h, () -> {
        }));
    }

    private void stepButton(Ui ui, String text, int x, int y, int mouseX, int mouseY, Runnable run) {
        boolean hv = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + 11;
        ui.rect(x, y, 12, 11, hv ? 0x50FFFFFF : 0x26FFFFFF);
        ui.textCenter(text, x + 6, y + 2, 0xFFFFFFFF);
        menuHits.add(new MenuHit(x, y, 12, 11, run));
    }

    // --- side panel ---

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.BLANK);
        var stack = UIContainers.stack(Sizing.fill(100), Sizing.fill(100));
        canvas = new Canvas();
        canvas.sizing(Sizing.fill(100), Sizing.fill(100));
        stack.child(canvas);
        side = UIContainers.verticalFlow(Sizing.fixed(PANEL_W), Sizing.content());
        side.gap(3);
        side.surface(panel());
        side.padding(Insets.of(6));
        sideScroll = SbcWidgets.scroll(Sizing.fixed(PANEL_W + 8), Sizing.fill(100), side);
        stack.child(sideScroll);
        toolbar = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        toolbar.gap(6);
        groupsBar = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        groupsBar.gap(3);
        FlowLayout top = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
        top.gap(4);
        top.surface(panel());
        top.padding(Insets.of(4));
        top.positioning(Positioning.relative(50, 0));
        top.child(toolbar);
        top.child(groupsBar);
        noteBar = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        noteBar.gap(6);
        noteBar.verticalAlignment(io.muzy.sbc.ui.layout.VerticalAlignment.CENTER);
        top.child(noteBar);
        stack.child(top);
        root.child(stack);
        placePanel();
        rebuildSide();
    }

    // one bg value for every element, so ones with their own opacity follow it
    private static float allOpacity() {
        return Theme.current().backgroundOpacity();
    }

    private void setAllOpacity(float value) {
        Config.I.classicAlpha = value;
        for (HudElement e : HudLayout.elements()) if (ElementStyle.has(e.id)) ElementStyle.of(e.id).alpha(null);
    }

    private FlowLayout toolbarSlider(String name, double value, double min, double max, Consumer<Double> set) {
        FlowLayout f = UIContainers.verticalFlow(Sizing.fixed(Math.max(104, Ui.MEASURE.width(name) + 2)), Sizing.content());
        f.child(label(name, dim()));
        SbcWidgets.Slider s = SbcWidgets.slider(Sizing.fill(100), min, max);
        s.decimalPlaces(2);
        s.setFromDiscreteValue(value);
        s.onChanged(v -> {
            set.accept(v);
            Config.save();
        });
        f.child(ui(s));
        return f;
    }

    private FlowLayout groupsBar;
    private boolean groupsOpen;

    // what the editor shows now, in its menus words
    private static String showing() {
        if (HudLayout.editorGroup != null) return HudLayout.editorGroup.name();
        return HudLayout.editorAllIslands ? "Everything" : "This island";
    }

    private void pickGroup(HudGroups.Group g, boolean all) {
        HudLayout.editorGroup = g;
        HudLayout.editorAllIslands = all;
        groupsOpen = false;
        select("");
        fillToolbar();
        rebuildSide();
    }

    // category menu: this island, everything, then each category + how many cards
    private void fillGroups() {
        if (groupsBar == null) return;
        groupsBar.clearChildren();
        if (!groupsOpen || tabMode) return;
        FlowLayout row = null;
        int n = 0;
        List<Object[]> entries = new java.util.ArrayList<>();
        entries.add(new Object[]{"This island", null, false});
        entries.add(new Object[]{"Everything", null, true});
        for (HudGroups.Group g : HudGroups.ALL) entries.add(new Object[]{g.name() + " (" + HudGroups.count(g) + ")", g, false});
        for (Object[] en : entries) {
            if (n++ % 4 == 0) {
                row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
                row.gap(3);
                groupsBar.child(row);
            }
            HudGroups.Group g = (HudGroups.Group) en[1];
            boolean all = (Boolean) en[2];
            boolean on = g != null ? HudLayout.editorGroup == g : HudLayout.editorGroup == null && HudLayout.editorAllIslands == all;
            String text = (on ? "● " : "") + en[0];
            UIComponent b = ui(SbcWidgets.button(Component.literal(text), x -> pickGroup(g, all)));
            b.horizontalSizing(Sizing.fixed(Ui.MEASURE.width(text) + 14));
            row.child(b);
        }
    }

    private void fillToolbar() {
        if (toolbar == null) return;
        toolbar.clearChildren();
        UIComponent mode = ui(SbcWidgets.button(Component.literal(tabMode ? "Editing: TAB" : "Editing: HUD"), b -> {
            tabMode = !tabMode;
            tabSelected = tabDragging = "";
            select("");
            panelHidden = !tabMode;
            placePanel();
            rebuildSide();
        }));
        mode.horizontalSizing(Sizing.fixed(70));
        toolbar.child(mode);
        toolbar.child(toolbarSlider("Background opacity (all)", allOpacity(), 0, 1, v -> setAllOpacity(v.floatValue())));
        if (tabMode) {
            toolbar.child(toolbarSlider("Tab size", Config.I.tabScale, 0.5, 1.5, v -> Config.I.tabScale = v.floatValue()));
        } else {
            toolbar.child(toolbarSlider("Size (all)", Config.I.hudAll, 0.5, 2.0, v -> HudLayout.setAll(v.floatValue())));
            UIComponent show = ui(SbcWidgets.button(Component.literal("Show: " + showing() + (groupsOpen ? "  ▲" : "  ▼")), b -> {
                groupsOpen = !groupsOpen;
                fillToolbar();
            }));
            show.horizontalSizing(Sizing.fixed(Math.max(110, Ui.MEASURE.width("Show: " + showing()) + 24)));
            toolbar.child(show);
        }
        UIComponent panelButton = ui(SbcWidgets.button(Component.literal("Panel (P)"), b -> togglePanel()));
        panelButton.horizontalSizing(Sizing.fixed(56));
        toolbar.child(panelButton);
        fillGroups();
        fillNote();
    }

    // slim line under the toolbar after setup placed the cards, says what moving one does. blocks nothing
    private void fillNote() {
        if (noteBar == null) return;
        noteBar.clearChildren();
        if (tabMode || noteClosed || !showsSetupNote()) return;
        noteBar.child(label(SETUP_NOTE, text()).maxWidth(Math.min(380, width - 190)));
        SbcWidgets.Toggle off = SbcWidgets.toggle(Component.literal("Don't show again")).checked(false);
        off.onChanged(v -> {
            Config.I.hudSetupNoteOff = v;
            Config.save();
        });
        noteBar.child(ui(off));
        UIComponent ok = ui(SbcWidgets.button(Component.literal("OK"), b -> {
            noteClosed = true;
            fillNote();
        }));
        ok.horizontalSizing(Sizing.fixed(28));
        noteBar.child(ok);
    }

    // panel goes on the side away from the selected element, or off screen when hidden
    private void placePanel() {
        if (sideScroll == null) return;
        if (panelHidden) {
            sideScroll.positioning(Positioning.absolute(width + 40, 0));
            return;
        }
        HudLayout.Placed p = selected.isEmpty() ? null : find(selected);
        boolean left = p != null && p.x + p.w / 2 > width / 2;
        sideScroll.positioning(left ? Positioning.absolute(0, 0) : Positioning.relative(100, 0));
    }

    private void select(String id) {
        boolean changed = !id.equals(selected);
        selected = id;
        // picking an element opens the panel next to it, clicking empty space hides it
        panelHidden = id.isEmpty();
        placePanel();
        if (changed) rebuildSide();
    }

    private FlowLayout optionCheck(String name, String key) {
        var o = TabList.option(key);
        return check(name, o != null && o.bool(), v -> {
            if (o != null) o.set(v);
        });
    }

    // panel while editing the tab list
    private void fillTabSide() {
        side.child(label("Tab list", accent()));
        var columns = TabList.option("columns");
        if (columns != null) side.child(slider("Columns (0: automatic)", columns.integer(), 0, 6, 0, v -> columns.set(v.intValue())));
        side.child(optionCheck("Player heads", "heads"));
        side.child(optionCheck("Header", "header"));
        side.child(optionCheck("Footer", "footer"));
        side.child(optionCheck("Hide adverts", "hideAdverts"));
        var sort = TabList.option("sortPlayers");
        if (sort != null) side.child(button("Sort players: " + sort.choice(), b -> {
            sort.set(sort.choices[(sort.choiceIndex() + 1) % sort.choices.length]);
            Config.save();
            rebuildSide();
        }));
        side.child(button("Automatic order", b -> {
            Config.I.tabOrder = new java.util.ArrayList<>();
            Config.save();
        }));
        side.child(label("Cards", accent()).margins(Insets.top(6)));
        for (String id : Config.I.tabSeen) {
            var def = io.muzy.sbc.core.TabWidgets.byId(id);
            String name = id.equals(TabList.INFO_KEY) ? "Info lines" : def == null ? id : def.name();
            FlowLayout row = check(name.length() > 24 ? name.substring(0, 24) + "…" : name, !TabList.hidden(id), v -> TabList.setHidden(id, !v));
            if (id.equals(tabSelected)) row.surface(Surface.outline(mark()));
            side.child(row);
        }
        side.child(button("Done", b -> onClose()).margins(Insets.top(6)));
    }

    // a dim line under a button: what pressing it does
    private UIComponent note(String text) {
        return label(text, dim()).maxWidth(PANEL_W - 12).margins(Insets.bottom(2));
    }

    private UIComponent button(String text, Consumer<SbcWidgets.Button> onPress) {
        UIComponent c = ui(SbcWidgets.button(Component.literal(text), onPress));
        c.horizontalSizing(Sizing.fill(100));
        return c;
    }

    private FlowLayout twoButtons(String a, Consumer<SbcWidgets.Button> fa, String b, Consumer<SbcWidgets.Button> fb) {
        FlowLayout f = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        f.gap(3);
        UIComponent ca = ui(SbcWidgets.button(Component.literal(a), fa)), cb = ui(SbcWidgets.button(Component.literal(b), fb));
        // 2 side by side, each gets half. an expanding child would eat all the room and the 2nd falls off
        int half = (PANEL_W - 12 - 3) / 2;
        ca.horizontalSizing(Sizing.fixed(half));
        cb.horizontalSizing(Sizing.fixed(half));
        f.child(ca);
        f.child(cb);
        return f;
    }

    private FlowLayout slider(String name, double value, double min, double max, int decimals, Consumer<Double> set) {
        FlowLayout f = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        f.child(label(name, dim()));
        SbcWidgets.Slider s = SbcWidgets.slider(Sizing.fill(100), min, max);
        s.decimalPlaces(decimals);
        s.setFromDiscreteValue(value);
        s.onChanged(v -> {
            set.accept(v);
            Config.save();
        });
        f.child(ui(s));
        return f;
    }

    private FlowLayout check(String name, boolean value, Consumer<Boolean> set) {
        FlowLayout f = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        f.gap(3);
        SbcWidgets.Toggle cb = SbcWidgets.toggle(Component.literal(name)).checked(value);
        cb.onChanged(v -> {
            set.accept(v);
            Config.save();
        });
        f.child(ui(cb));
        return f;
    }

    private FlowLayout color(String name, Integer value, int fallback, Consumer<Integer> set) {
        FlowLayout f = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        f.gap(3);
        int shown = value == null ? fallback : value;
        f.child(label(name, dim()).horizontalSizing(Sizing.expand()));
        f.child(label("■", Color.ofArgb(shown)));
        SbcWidgets.Input tb = SbcWidgets.input(Sizing.fixed(62), String.format(Locale.ROOT, "%08X", shown));
        tb.onChanged(s -> {
            try {
                String h = s.replace("#", "").trim();
                if (h.length() != 6 && h.length() != 8) return;
                long l = Long.parseLong(h, 16);
                set.accept((int) (h.length() == 6 ? (l | 0xFF000000L) : l));
                Config.save();
            } catch (Exception ignored) {
            }
        });
        f.child(ui(tb));
        return f;
    }

    private void rebuildSide() {
        if (side == null) return;
        fillToolbar();
        side.clearChildren();
        if (tabMode) {
            fillTabSide();
            return;
        }
        side.child(twoButtons("EDIT HUD", b -> {
        }, "Hide (P)", b -> togglePanel()));
        HudLayout.Placed p = selected.isEmpty() ? null : find(selected);
        if (p == null) {
            side.child(note("Click a card to style it. Drag to move it (it stays where you put it), wheel to resize, right click for its menu."));
        } else {
            ElementStyle st = ElementStyle.of(p.id);
            Theme t = Theme.current();
            side.child(label(p.title, text()).maxWidth(PANEL_W - 12));
            side.child(label(String.format(Locale.ROOT, "x %d  y %d  %dx%d", p.x, p.y, p.w, p.h), dim()));
            side.child(twoButtons("Center X", b -> {
                HudPresets.mark();
                HudLayout.pin(p.id, (width - PANEL_W - 8 - p.w) / 2, p.y);
                Config.save();
                rebuildSide();
            }, "Center Y", b -> {
                HudPresets.mark();
                HudLayout.pin(p.id, p.x, (height - p.h) / 2);
                Config.save();
                rebuildSide();
            }));
            side.child(slider("Size", HudLayout.scaleOf(p.id), HudLayout.MIN_SCALE, HudLayout.MAX_SCALE, 2, v -> HudLayout.setScale(p.id, v.floatValue())));
            if (p.element != null && p.element.feature() != null) {
                final String fid = p.element.feature().id;
                side.child(button("Open in settings", b -> SettingsScreen.open(this, fid)));
            }
            if (p.element != null && p.element.feature() != null && p.element.feature().option("show") != null) {
                var show = p.element.feature().option("show");
                side.child(button("Show: " + show.choice(), b -> {
                    int i = (show.choiceIndex() + 1) % show.choices.length;
                    show.set(show.choices[i]);
                    Config.save();
                    rebuildSide();
                }));
            }
            if (p.element != null) {
                side.child(label("Look", accent()).margins(Insets.top(4)));
                side.child(color("Background", st.bg(), t == io.muzy.sbc.ui.theme.ClassicTheme.I ? 0xFF000000 : 0xFF2B2B29, st::bg));
                side.child(slider("Opacity", st.alpha() == null ? allOpacity() : st.alpha(), 0, 1, 2, v -> st.alpha(v.floatValue())));
                side.child(check("Border", Boolean.TRUE.equals(st.border()), st::border));
                if (Boolean.TRUE.equals(st.border())) side.child(color("Border colour", st.borderColor(), t.accent(), st::borderColor));
                side.child(color("Title colour", st.titleColor(), t.accent(), st::titleColor));
                side.child(color("Text colour", st.textColor(), t.text(), st::textColor));
                side.child(color("Value colour", st.valueColor(), t.text(), st::valueColor));
                side.child(check("Show title", st.showTitle() == null || st.showTitle(), st::showTitle));
                side.child(check("Text shadow", st.shadow() == null || st.shadow(), st::shadow));
                side.child(slider("Padding", st.padding() == null ? t.pad() : st.padding(), 0, 12, 0, v -> st.padding(v.intValue())));
                side.child(slider("Row height", st.rowH() == null ? t.rowH() : st.rowH(), 8, 16, 0, v -> st.rowH(v.intValue())));
                if (!p.element.pickable().isEmpty()) {
                    side.child(label("Stats", accent()).margins(Insets.top(4)));
                    for (String name : p.element.pickable()) side.child(check(name.length() > 22 ? name.substring(0, 22) + "…" : name, p.element.picked(name), v -> p.element.pick(name, v)));
                }
                List<HudElement.Row> rows = p.element.demo ? p.element.demo() : p.element.rows();
                if (!rows.isEmpty()) {
                    side.child(label("Rows", accent()).margins(Insets.top(4)));
                    for (HudElement.Row r : rows) {
                        String lab = r.label();
                        if (lab.isEmpty()) continue; // spacer line, nothing to switch
                        side.child(check(lab.length() > 22 ? lab.substring(0, 22) + "…" : lab, !st.isHidden(lab), v -> st.setHidden(lab, !v)));
                    }
                }
                // cards drawn inside this one: each its own rows, its own look stays its own
                for (HudElement m : p.element.members()) {
                    if (!m.active()) continue;
                    ElementStyle ms = ElementStyle.of(m.id);
                    side.child(label("Inside: " + m.title, accent()).maxWidth(PANEL_W - 12).margins(Insets.top(4)));
                    if (m.feature() != null && m.feature().option("show") != null) side.child(note("Shows here when it has something. Hide it from its menu or settings (Show: Never)."));
                    boolean was = m.demo;
                    m.demo = true;
                    try {
                        for (HudElement.Row r : m.demo()) {
                            String lab = r.label();
                            if (lab.isEmpty()) continue;
                            side.child(check(lab.length() > 22 ? lab.substring(0, 22) + "…" : lab, !ms.isHidden(lab), v -> ms.setHidden(lab, !v)));
                        }
                    } finally {
                        m.demo = was;
                    }
                }
            }
            if (p.pinned) {
                side.child(button("Back to automatic", b -> {
                    HudPresets.mark();
                    HudLayout.unpin(p.id);
                    Config.save();
                    rebuildSide();
                }));
                side.child(note("Unpins it: it arranges itself again with what's shown. Its size stays."));
            }
            side.child(button("Reset card", b -> {
                HudPresets.mark();
                HudLayout.reset(p.id);
                ElementStyle.reset(p.id);
                Config.save();
                rebuildSide();
            }));
            side.child(note("Back to automatic with its default size and style."));
        }
        side.child(label("Everything", accent()).margins(Insets.top(6)));
        side.child(twoButtons("Undo", b -> {
            HudPresets.undo();
            rebuildSide();
        }, "Redo", b -> {
            HudPresets.redo();
            rebuildSide();
        }));
        side.child(note("Undo takes back your last change, Redo puts it back (Ctrl+Z, Ctrl+Shift+Z)."));
        side.child(button("Reset layout", b -> {
            HudPresets.mark();
            HudLayout.resetAll();
            Config.I.hudStyles.clear();
            Config.I.hudSetupNoteOff = false;
            noteClosed = false;
            Config.save();
            rebuildSide();
        }));
        side.child(note("Every card back to automatic with default sizes and styles. The Setup note comes back."));
        side.child(label("Presets", accent()).margins(Insets.top(6)));
        for (String name : HudPresets.names()) {
            if (name.equals("Default")) {
                side.child(button("Default", b -> {
                    HudPresets.load(name);
                    rebuildSide();
                }));
                continue;
            }
            side.child(twoButtons(name, b -> {
                HudPresets.load(name);
                rebuildSide();
            }, "Save as", b -> {
                HudPresets.save(name);
                rebuildSide();
            }));
        }
        SbcWidgets.Input newName = SbcWidgets.input(Sizing.fill(100), "");
        newName.setHint(Component.literal("New preset name..."));
        side.child(ui(newName));
        side.child(twoButtons("Save new", b -> {
            String n = newName.getValue().trim();
            if (!n.isEmpty()) HudPresets.save(n);
            rebuildSide();
        }, "Copy JSON", b -> Minecraft.getInstance().keyboardHandler.setClipboard(HudPresets.exportJson())));
        side.child(button("Paste JSON", b -> {
            if (HudPresets.importJson(Minecraft.getInstance().keyboardHandler.getClipboard())) rebuildSide();
        }));
        side.child(button("Done", b -> onClose()).margins(Insets.top(6)));
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        //? if >=26.3 {
        if (k == InputConstants.KEY_ESCAPE) {
        //?} else {
        /*if (k == GLFW.GLFW_KEY_ESCAPE) {
        *///?}
            if (!menuFor.isEmpty()) {
                closeMenu();
                return true;
            }
            onClose();
            return true;
        }
        //? if >=26.3 {
        if (k == InputConstants.KEY_P && !typing()) {
        //?} else {
        /*if (k == GLFW.GLFW_KEY_P && !typing()) {
        *///?}
            togglePanel();
            return true;
        }
        //? if >=26.3 {
        if (ctrl() && k == InputConstants.KEY_Z) {
        //?} else {
        /*if (ctrl() && k == GLFW.GLFW_KEY_Z) {
        *///?}
            if (shift()) HudPresets.redo();
            else HudPresets.undo();
            rebuildSide();
            return true;
        }
        HudLayout.Placed p = selected.isEmpty() ? null : find(selected);
        if (p != null && !typing()) {
            int step = shift() ? 5 : 1;
            boolean handled = true;
            switch (k) {
                //? if >=26.3 {
                case InputConstants.KEY_LEFT -> HudLayout.pin(p.id, p.x - step, p.y);
                case InputConstants.KEY_RIGHT -> HudLayout.pin(p.id, p.x + step, p.y);
                case InputConstants.KEY_UP -> HudLayout.pin(p.id, p.x, p.y - step);
                case InputConstants.KEY_DOWN -> HudLayout.pin(p.id, p.x, p.y + step);
                case InputConstants.KEY_DELETE, InputConstants.KEY_R -> {
                //?} else {
                /*case GLFW.GLFW_KEY_LEFT -> HudLayout.pin(p.id, p.x - step, p.y);
                case GLFW.GLFW_KEY_RIGHT -> HudLayout.pin(p.id, p.x + step, p.y);
                case GLFW.GLFW_KEY_UP -> HudLayout.pin(p.id, p.x, p.y - step);
                case GLFW.GLFW_KEY_DOWN -> HudLayout.pin(p.id, p.x, p.y + step);
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_R -> {
                *///?}
                    HudLayout.reset(p.id);
                    ElementStyle.reset(p.id);
                }
                //? if >=26.3 {
                case InputConstants.KEY_EQUALS, InputConstants.KEY_ADD -> HudLayout.setScale(p.id, HudLayout.scaleOf(p.id) + 0.05f);
                case InputConstants.KEY_MINUS, org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_MINUS -> HudLayout.setScale(p.id, HudLayout.scaleOf(p.id) - 0.05f);
                //?} else {
                /*case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> HudLayout.setScale(p.id, HudLayout.scaleOf(p.id) + 0.05f);
                case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> HudLayout.setScale(p.id, HudLayout.scaleOf(p.id) - 0.05f);
                *///?}
                default -> handled = false;
            }
            if (handled) {
                Config.save();
                rebuildSide();
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        Config.save();
        io.muzy.sbc.ui.HudReplay.forget();
        io.muzy.sbc.ui.HudLayout.editorClosed();
        super.onClose();
    }

    // a text field has the keyboard (getFocused() is always the adapter here)
    private boolean typing() {
        var focus = this.uiAdapter.rootComponent.focusHandler();
        return focus != null && focus.focused() instanceof SbcWidgets.Input;
    }

    private void togglePanel() {
        panelHidden = !panelHidden;
        placePanel();
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        // opens on the category of your island so the cards you wanna move are right there
        HudLayout.editorGroup = HudGroups.here();
        HudLayout.editorAllIslands = false;
        mc.gui.setScreen(new HudEditorScreen(mc.gui.screen()));
    }
}
