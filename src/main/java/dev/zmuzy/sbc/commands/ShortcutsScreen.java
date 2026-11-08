// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.commands;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.screens.SbcScreen;
import dev.zmuzy.sbc.ui.screens.SbcWidgets;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// from skyblocker ShortcutsConfigScreen, ShortcutsConfigListWidget, KeybindWidget, ShortcutKeyBinding
// 3 lists: target + what it replaces, and keys that run a command. click a keys button, press keys, letting
// go ends it. saved on close
public final class ShortcutsScreen extends SbcScreen {
    private static final class Pair {
        String from, to;

        Pair(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    private final List<Pair> commands = new ArrayList<>(), args = new ArrayList<>(), keys = new ArrayList<>();
    // key row being set rn + its keys so far
    private Pair capturing;
    private final List<String> captured = new ArrayList<>();
    private FlowLayout root;

    public ShortcutsScreen(Screen parent) {
        super("Shortcuts", parent);
        Saved.Data d = Saved.get();
        d.shortcutCommands.forEach((k, v) -> commands.add(new Pair(k, v)));
        d.shortcutArgs.forEach((k, v) -> args.add(new Pair(k, v)));
        d.shortcutKeys.forEach((k, v) -> keys.add(new Pair(k, v)));
    }

    private static String slashed(String s) {
        String t = s.trim();
        return t.isEmpty() || t.startsWith("/") ? t : "/" + t;
    }

    private void store() {
        Saved.Data d = Saved.get();
        d.shortcutCommands = map(commands, true);
        d.shortcutArgs = map(args, true);
        Map<String, String> k = new LinkedHashMap<>();
        for (Pair p : keys) if (!p.from.isBlank() && !p.to.isBlank()) k.put(p.from, p.to.trim());
        d.shortcutKeys = k;
        Saved.save();
    }

    private static Map<String, String> map(List<Pair> list, boolean slash) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Pair p : list) if (!p.from.isBlank() && !p.to.isBlank()) out.put(slash ? slashed(p.from) : p.from, slash ? slashed(p.to) : p.to);
        return out;
    }

    private void rebuild() {
        root.clearChildren();
        build(root);
    }

    private void section(FlowLayout list, String title, String help, List<Pair> rows, String fromHint, String toHint) {
        list.child(new SbcWidgets.Heading(title, 0xFF5CD8FF));
        list.child(SbcScreen.label(help, SbcScreen.dim()).maxWidth(420));
        for (Pair p : rows) {
            FlowLayout r = EditorUi.row();
            r.child(EditorUi.field(38, p.from, fromHint, v -> p.from = v));
            r.child(SbcScreen.label("→", SbcScreen.dim()));
            r.child(EditorUi.field(48, p.to, toHint, v -> p.to = v));
            r.child(EditorUi.button("§cX", () -> {
                rows.remove(p);
                rebuild();
            }));
            list.child(r);
        }
        list.child(EditorUi.button("Add", () -> {
            rows.add(new Pair("", ""));
            rebuild();
        }));
    }

    private static String keysText(String combo) {
        if (combo.isBlank()) return "§7No key";
        StringBuilder out = new StringBuilder();
        for (String name : combo.split(" \\+ ")) {
            if (out.length() > 0) out.append(" + ");
            try {
                out.append(InputConstants.getKey(name).getDisplayName().getString());
            } catch (Exception e) {
                out.append(name);
            }
        }
        return out.toString();
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        root = rootComponent;
        FlowLayout foot = EditorUi.row();
        foot.child(EditorUi.button("Add the usual ones", () -> {
            List<Map<String, String>> usual = Shortcuts.usual();
            usual.get(0).forEach((k, v) -> {
                if (commands.stream().noneMatch(p -> p.from.equals(k))) commands.add(new Pair(k, v));
            });
            usual.get(1).forEach((k, v) -> {
                if (args.stream().noneMatch(p -> p.from.equals(k))) args.add(new Pair(k, v));
            });
            rebuild();
        }));
        foot.child(EditorUi.button("Controls", () -> {
            store();
            minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options));
        }));
        foot.child(EditorUi.button("Done", this::onClose));
        FlowLayout list = EditorUi.panel(root, width, "Shortcuts", "New command names show in chat suggestions from your next join.", foot);
        section(list, "Commands", "A whole command for another. Type it alone.", commands, "/ga", "/warp garden");
        section(list, "Words", "A word for another; what follows stays.", args, "/m", "/msg");
        list.child(new SbcWidgets.Heading("Keys", 0xFF5CD8FF));
        list.child(SbcScreen.label("A key or a mouse button, alone or together, runs a command or says a line. Only with no menu open.", SbcScreen.dim()).maxWidth(420));
        for (Pair p : keys) {
            FlowLayout r = EditorUi.row();
            SbcWidgets.Button key = EditorUi.button(p == capturing ? "§ePress keys..." : keysText(p.from), () -> {
                capturing = p;
                captured.clear();
                rebuild();
            });
            key.horizontalSizing(Sizing.fill(38));
            r.child(key);
            r.child(SbcScreen.label("→", SbcScreen.dim()));
            r.child(EditorUi.field(48, p.to, "/pets", v -> p.to = v));
            r.child(EditorUi.button("§cX", () -> {
                keys.remove(p);
                if (capturing == p) capturing = null;
                rebuild();
            }));
            list.child(r);
        }
        list.child(EditorUi.button("Add", () -> {
            keys.add(new Pair("", ""));
            rebuild();
        }));
    }

    private void finishCapture() {
        capturing.from = String.join(" + ", captured);
        capturing = null;
        captured.clear();
        rebuild();
    }

    // from skyblocker ShortcutsConfigScreen.keyPressed, escape clears the key
    @Override
    public boolean keyPressed(KeyEvent e) {
        if (capturing == null) return super.keyPressed(e);
        if (e.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            captured.clear();
            finishCapture();
            return true;
        }
        String name = InputConstants.getKey(e).getName();
        if (!captured.contains(name)) captured.add(name);
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent e) {
        if (capturing != null && !captured.isEmpty()) {
            finishCapture();
            return true;
        }
        return super.keyReleased(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (capturing != null) {
            String name = InputConstants.Type.MOUSE.getOrCreate(e.button()).getName();
            if (!captured.contains(name)) captured.add(name);
            finishCapture();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public void onClose() {
        store();
        super.onClose();
    }
}
