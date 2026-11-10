// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.ui.KeyNames;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.layout.Color;
import io.github.zmuzy.sbc.ui.layout.FlowLayout;
import io.github.zmuzy.sbc.ui.layout.HorizontalAlignment;
import io.github.zmuzy.sbc.ui.layout.Insets;
import io.github.zmuzy.sbc.ui.layout.Sizing;
import io.github.zmuzy.sbc.ui.layout.UIContainers;
import io.github.zmuzy.sbc.ui.layout.VerticalAlignment;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

// keys worth knowing, one at a time right after setup. each page names a key + what it does. press it and it
// really happens: the menu, mission card or market search opens
public final class TutorialScreen extends SbcScreen {
    // what pressing shows here: real screen always, real screen only in a world, or just a confirm
    enum Demo { ALWAYS, IN_WORLD, NONE }

    // one key: binding, alt or not, default key, what it does, what pressing it shows here
    record Step(String binding, boolean alt, String fallback, String title, String what, Demo demo, String done) {}

    static final List<Step> STEPS = List.of(
            new Step("key.sbcompanion.open", false, "K", "The main menu",
                    "Your profile, the planner, trackers and every setting, with a search.", Demo.ALWAYS, "That was the main menu."),
            new Step("key.sbcompanion.open", true, "K", "Your next mission",
                    "A small card with what to do next from your daily routine.", Demo.ALWAYS, "That was the mission card."),
            new Step("key.sbcompanion.market", false, "Y", "Market search",
                    "Search the Bazaar and the Auction House as you type, with prices.", Demo.ALWAYS, "That was the market search."),
            new Step("key.sbcompanion.hudedit", false, "H", "The HUD editor",
                    "Edit everything on your screen, grouped by activity.", Demo.IN_WORLD, "That was the HUD editor."),
            new Step("key.sbcompanion.map", false, "N", "The island map",
                    "A map of the island you are on, with warps and points of interest.", Demo.IN_WORLD, "That was the island map."),
            new Step("key.sbcompanion.zoom_in", false, "Numpad +", "Minimap: closer",
                    "Zooms the round minimap in.", Demo.NONE, "Right: that zooms the minimap in."),
            new Step("key.sbcompanion.zoom_out", false, "Numpad -", "Minimap: farther",
                    "Zooms the round minimap out.", Demo.NONE, "Right: that zooms the minimap out."),
            new Step("key.sbcompanion.warp", false, "J", "Warp to the target",
                    "When the path line leads you somewhere, this warps you to the closest warp on the way.", Demo.NONE, "Right: that warps you toward the target."),
            new Step("key.sbcompanion.storage", false, "B", "Your storage",
                    "Your backpacks and ender chest pages on one screen.", Demo.NONE, "Right: that opens your storage in game."),
            new Step("key.sbcompanion.lock", false, "L", "Slot lock",
                    "With the mouse over a slot of your inventory: locks it, so nothing there is dropped or moved by mistake.", Demo.NONE,
                    "Right: over a slot, that locks it."),
            new Step("key.sbcompanion.wiki_lookup", false, "F1", "Wiki lookup",
                    "With the mouse over an item: its wiki page opens inside the game.", Demo.NONE, "Right: over an item, that opens its wiki page."));

    // how long a key screen stays before the tutorial takes over again
    static final long SHOW_MS = 3000;
    // the tutorial that opened a screen for a key, waiting to come back when the show ends
    private static TutorialScreen waiting;
    private static long showUntil;

    private int step;
    private String note = "";
    private boolean noteGood;
    private FlowLayout panel;

    public TutorialScreen(Screen parent) {
        super("SkyBlock Companion keys", parent);
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new TutorialScreen(parent));
    }

    // every tick while a key screen is on show: after 3 sec the tutorial takes the screen back on the next key.
    // the show cant be closed early (keys + clicks held, see register)
    public static boolean resumeTick(Minecraft mc, boolean onMenu) {
        if (waiting == null) return false;
        Screen s = mc.gui.screen();
        if (System.currentTimeMillis() >= showUntil || s == null || onMenu) {
            TutorialScreen t = waiting;
            waiting = null;
            mc.gui.setScreen(t);
        }
        return true;
    }

    // secs left of the show, for the countdown
    static int secondsLeft() {
        return (int) Math.max(0, (showUntil - System.currentTimeMillis() + 999) / 1000);
    }

    // holds every key + click of a shown screen, it closes by itself
    public static void register() {
        io.github.zmuzy.sbc.core.Safe.afterInit("TutorialScreen.show", (client, screen, w, h) -> {
            if (waiting == null || screen instanceof TutorialScreen) return;
            io.github.zmuzy.sbc.core.Safe.allowKeyPress("TutorialScreen.showKeys", screen, (sc, e) -> waiting == null);
            io.github.zmuzy.sbc.core.Safe.allowMouseClick("TutorialScreen.showClicks", screen, (sc, e) -> waiting == null);
        });
        io.github.zmuzy.sbc.core.Safe.afterInit("TutorialScreen.count", (client, screen, w, h) -> {
            if (waiting == null || screen instanceof TutorialScreen) return;
            io.github.zmuzy.sbc.core.Safe.afterExtract("TutorialScreen.countDraw", screen, (sc, g, mx, my, delta) -> {
                if (waiting == null) return;
                String line = "Back to the keys in " + secondsLeft() + "...";
                var f = client.font;
                int tw = f.width(line), x = (sc.width - tw) / 2, y = sc.height - 22;
                g.fill(x - 8, y - 5, x + tw + 8, y + 13, 0xC0101826);
                g.fill(x - 8, y - 5, x + tw + 8, y - 4, 0xFF42A5F5);
                g.text(f, line, x, y, 0xFFFFFFFF, true);
            });
        });
    }

    // key as bound in english, Alt in front when needed
    static String keyName(Step s) {
        return (s.alt() ? "Alt + " : "") + KeyNames.of(KeyNames.binding(s.binding()), s.fallback());
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Showcase.backdrop());
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        panel = UIContainers.verticalFlow(Sizing.fixed(width()), Sizing.content());
        panel.surface(Showcase.panel(0xFF42A5F5));
        panel.padding(Insets.of(12));
        panel.gap(6);
        fill();
        root.child(panel);
    }

    private int width() {
        return Math.min(300, width - 20);
    }

    private void fill() {
        int w = width(), inner = w - 20;
        panel.clearChildren();
        panel.child(new Showcase.BigText("LEARN THE KEYS", 1.5f, 0xFFFFFF, 0xB9CCFF));
        panel.child(label(step < STEPS.size() ? "Key " + (step + 1) + " of " + STEPS.size() : "All done", dim())
                .horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100)));
        panel.child(progress(inner));
        if (!note.isEmpty()) panel.child(label((noteGood ? "✔ " : "") + note, Color.ofArgb(noteGood ? 0xFF66BB6A : 0xFFFFB74D)).maxWidth(inner));
        if (step >= STEPS.size()) {
            panel.child(label("That's all you need to start.", text()).margins(Insets.top(4)));
            panel.child(label("Every key can be changed in Options > Controls > Key Binds, under SkyBlock Companion. /sbc tutorial shows this again.", dim()).maxWidth(inner));
            panel.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Start playing"), b -> onClose())).horizontalSizing(Sizing.fill(100)).margins(Insets.top(6)));
            return;
        }
        Step s = STEPS.get(step);
        int color = 0xFF42A5F5;
        FlowLayout card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(glass(color, true));
        card.padding(Insets.of(3, 8, 8, 8));
        card.gap(6);
        card.horizontalAlignment(HorizontalAlignment.CENTER);
        card.child(label(s.title(), Color.ofArgb(Modern.lighten(color, 0.45f))).margins(Insets.bottom(4)));
        card.child(keycap(keyName(s)));
        card.child(label(s.what(), dim()).maxWidth(inner - 16).horizontalTextAlignment(HorizontalAlignment.CENTER));
        if (s.demo() == Demo.IN_WORLD && !inWorld()) card.child(label("In game this opens it; here, press it to go on.", Color.ofArgb(0xFF8FA3B8)).maxWidth(inner - 16));
        panel.child(card);
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.margins(Insets.top(2));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Skip all"), b -> onClose())).horizontalSizing(Sizing.fixed(70)));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Skip this key"), b -> {
            note = "";
            step++;
            fill();
        })).horizontalSizing(Sizing.expand()));
        panel.child(row);
    }

    // key drawn as a keycap: raised box with the name in it
    private static FlowLayout keycap(String key) {
        FlowLayout cap = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        cap.padding(Insets.of(5, 6, 10, 10));
        cap.surface((g, c) -> {
            int x = c.x(), y = c.y(), w = c.width(), h = c.height();
            g.fill(x, y + 2, x + w, y + h, 0xFF0A0F16);
            g.fill(x, y, x + w, y + h - 2, 0xFF2A3A4E);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x40FFFFFF);
            g.drawRectOutline(x, y, w, h, 0xFF4F6A88);
        });
        cap.child(label(key, Color.ofArgb(0xFFFFFFFF)));
        return cap;
    }

    // thin bar of how far through the keys you are
    private FlowLayout progress(int w) {
        float done = Math.min(1f, step / (float) STEPS.size());
        FlowLayout bar = UIContainers.horizontalFlow(Sizing.fixed(w), Sizing.fixed(2));
        int a = Theme.current().accent() | 0xFF000000;
        bar.surface((g, c) -> {
            g.fill(c.x(), c.y(), c.x() + c.width(), c.y() + 2, 0x30FFFFFF);
            g.fill(c.x(), c.y(), c.x() + Math.round(c.width() * done), c.y() + 2, Ui.alpha(a, 0.9f));
        });
        return bar;
    }

    private static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.level != null && mc.player != null;
    }

    // is this event the steps key: its binding, alt held exactly when the step wants alt
    static boolean pressed(Step s, KeyMapping k, KeyEvent e) {
        return k != null && k.matches(e) && e.hasAltDown() == s.alt();
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (step >= STEPS.size()) return super.keyPressed(e);
        int k = e.key();
        if (k == InputConstants.KEY_LALT || k == InputConstants.KEY_RALT || k == InputConstants.KEY_LSHIFT || k == InputConstants.KEY_RSHIFT
                || k == InputConstants.KEY_LCONTROL || k == InputConstants.KEY_RCONTROL) return true;
        Step s = STEPS.get(step);
        if (!pressed(s, KeyNames.binding(s.binding()), e)) {
            note = "Not that one: press " + keyName(s) + ".";
            noteGood = false;
            fill();
            return true;
        }
        SbcWidgets.clickSound();
        note = s.done();
        noteGood = true;
        step++;
        // shown again as is when the show ends, the screen comes back without a rebuild
        fill();
        if (s.demo() == Demo.ALWAYS || (s.demo() == Demo.IN_WORLD && inWorld())) demo(s);
        return true;
    }

    // opens what the key opens, tutorial comes back on close. false = couldnt open
    private boolean demo(Step s) {
        try {
            Screen open = switch (s.binding()) {
                case "key.sbcompanion.open" -> {
                    if (s.alt()) yield new io.github.zmuzy.sbc.ui.MissionScreen();
                    io.github.zmuzy.sbc.ui.CompanionScreen.page = 0;
                    yield new io.github.zmuzy.sbc.ui.CompanionScreen();
                }
                case "key.sbcompanion.market" -> new io.github.zmuzy.sbc.ui.MarketSearchScreen(null, 0);
                case "key.sbcompanion.map" -> {
                    io.github.zmuzy.sbc.ui.CompanionScreen.devViewIsland(io.github.zmuzy.sbc.core.GameState.island);
                    io.github.zmuzy.sbc.ui.CompanionScreen.page = 9;
                    yield new io.github.zmuzy.sbc.ui.CompanionScreen();
                }
                default -> null;
            };
            waiting = this;
            showUntil = System.currentTimeMillis() + SHOW_MS;
            if (open != null) minecraft.gui.setScreen(open);
            else if (s.binding().equals("key.sbcompanion.hudedit")) HudEditorScreen.open();
            else {
                waiting = null;
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            waiting = null;
            io.github.zmuzy.sbc.core.Safe.caught("TutorialScreen.demo", ex);
            return false;
        }
    }

    @Override
    public void onClose() {
        waiting = null;
        super.onClose();
    }
}
