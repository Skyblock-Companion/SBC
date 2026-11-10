// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import io.github.zmuzy.sbc.ui.layout.FlowLayout;
import io.github.zmuzy.sbc.ui.layout.Insets;
import io.github.zmuzy.sbc.ui.layout.Sizing;
import io.github.zmuzy.sbc.ui.layout.UIContainers;
import io.github.zmuzy.sbc.ui.screens.SbcScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Map;

// from skyhanni CustomTodos, CustomTodoEditor, CommunityTodoViewer. list of todos, one open at a time with
// all its fields
// paste a template, copy one, or grab one from the community list
public final class TodosScreen extends SbcScreen {
    private Todo open;
    private boolean browsing;
    private FlowLayout root;
    private String message = "";

    public TodosScreen(Screen parent) {
        super("Todos", parent);
    }

    private static List<Todo> todos() {
        return Saved.get().todos;
    }

    private void rebuild() {
        root.clearChildren();
        build(root);
    }

    private static int number(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private FlowLayout todoCard() {
        FlowLayout c = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        c.surface(SbcScreen.card());
        c.padding(Insets.of(4));
        c.gap(3);
        return c;
    }

    private void listPage(FlowLayout list) {
        for (Todo t : todos()) {
            FlowLayout r = EditorUi.row();
            r.child(SbcScreen.label((t.isEnabled ? "" : "§8") + t.label.replace("&&", "§") + (t.downloaded ? " §7(community)" : ""), EditorUi.ink()).horizontalSizing(Sizing.expand()));
            r.child(EditorUi.button("Edit", () -> {
                open = t;
                rebuild();
            }));
            r.child(EditorUi.button("Copy", () -> {
                minecraft.keyboardHandler.setClipboard(t.toTemplate());
                message = "§aCopied " + t.label + ".";
                rebuild();
            }));
            r.child(EditorUi.button("§cX", () -> {
                todos().remove(t);
                Saved.save();
                rebuild();
            }));
            list.child(r);
        }
        if (todos().isEmpty()) list.child(SbcScreen.label("No todos yet.", SbcScreen.dim()));
    }

    private void editPage(FlowLayout list, Todo t) {
        FlowLayout c = todoCard();
        c.child(EditorUi.labelled("Name", t.label, v -> t.label = v));
        c.child(EditorUi.toggle("Switched on", t.isEnabled, v -> t.isEnabled = v, this::rebuild));
        c.child(EditorUi.labelled("Text that starts it", t.trigger, v -> t.trigger = v));
        c.child(EditorUi.labelled("Text that undoes it", t.antiTrigger, v -> t.antiTrigger = v));
        FlowLayout how = EditorUi.row();
        how.child(SbcScreen.label("How it is compared", SbcScreen.dim()).horizontalSizing(Sizing.fill(34)));
        for (Todo.TriggerMatcher m : Todo.TriggerMatcher.values()) {
            how.child(EditorUi.button((t.triggerMatcher == m ? "§b" : "") + switch (m) {
                case REGEX -> "Regex";
                case STARTS_WITH -> "Starts";
                case CONTAINS -> "Contains";
                case EQUALS -> "Equals";
            }, () -> {
                t.triggerMatcher = m;
                rebuild();
            }));
        }
        c.child(how);
        FlowLayout where = EditorUi.row();
        where.child(SbcScreen.label("Where to look", SbcScreen.dim()).horizontalSizing(Sizing.fill(34)));
        for (Todo.TriggerTarget target : Todo.TriggerTarget.values()) {
            where.child(EditorUi.button((t.triggerTarget == target ? "§b" : "") + switch (target) {
                case CHAT -> "Chat";
                case ACTION_BAR -> "Action bar";
                case TAB_LIST -> "Tab";
                case SIDEBAR -> "Sidebar";
            }, () -> {
                t.triggerTarget = target;
                rebuild();
            }));
        }
        c.child(where);
        c.child(EditorUi.toggle("Ignore colour codes", t.ignoreColorCodes, v -> t.ignoreColorCodes = v, this::rebuild));
        c.child(EditorUi.labelled(t.isResetOffset ? "Seconds after midnight UTC" : "Seconds until ready", Integer.toString(t.timer), v -> t.timer = number(v, t.timer)));
        c.child(EditorUi.toggle("At a time of day", t.isResetOffset, v -> t.isResetOffset = v, this::rebuild));
        c.child(EditorUi.labelled("Times needed", Integer.toString(t.totalTriggers), v -> t.totalTriggers = Math.max(1, number(v, 1))));
        c.child(EditorUi.labelled("Show from (seconds left)", Integer.toString(t.showWhen), v -> t.showWhen = Math.max(0, number(v, 0))));
        c.child(EditorUi.toggle("Only when ready", t.showOnlyWhenReady, v -> t.showOnlyWhenReady = v, this::rebuild));
        c.child(EditorUi.labelled("Item icon", t.icon, v -> t.icon = v));
        c.child(EditorUi.toggle("On a schedule", t.cronEnabled, v -> t.cronEnabled = v, this::rebuild));
        if (t.cronEnabled) c.child(EditorUi.labelled("Cron line", t.cronExpression, v -> t.cronExpression = v));
        list.child(c);
    }

    private void communityPage(FlowLayout list) {
        for (Map.Entry<String, String> e : Todos.community().entrySet()) {
            FlowLayout r = EditorUi.row();
            r.child(SbcScreen.label(e.getKey(), EditorUi.ink()).horizontalSizing(Sizing.expand()));
            boolean have = todos().stream().anyMatch(t -> e.getKey().equals(t.downloadedId));
            r.child(EditorUi.button(have ? "§7Added" : "Add", () -> {
                if (!have) Todos.download(e.getKey());
                rebuild();
            }));
            list.child(r);
        }
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        root = rootComponent;
        FlowLayout foot = EditorUi.row();
        if (open != null || browsing) {
            foot.child(EditorUi.button("Back", () -> {
                open = null;
                browsing = false;
                Saved.save();
                rebuild();
            }));
        } else {
            foot.child(EditorUi.button("New", () -> {
                Todo t = new Todo();
                t.label = "Custom Todo # " + (todos().size() + 1);
                todos().add(t);
                open = t;
                Saved.save();
                rebuild();
            }));
            foot.child(EditorUi.button("Paste", () -> {
                Todo t = Todo.fromTemplate(minecraft.keyboardHandler.getClipboard());
                if (t == null) message = "§cThat is not a todo.";
                else {
                    todos().add(t);
                    Saved.save();
                    message = "§aAdded " + t.label + ".";
                }
                rebuild();
            }));
            foot.child(EditorUi.button("Community", () -> {
                browsing = true;
                rebuild();
            }));
        }
        foot.child(EditorUi.button("Done", this::onClose));
        String hint = open != null ? "A timer starts when the text comes by, on this profile. The card shows it from the first time on." : message.isEmpty() ? "Your own timers." : message;
        FlowLayout list = EditorUi.panel(root, width, open != null ? open.label : browsing ? "Community todos" : "Todos", hint, foot);
        if (open != null) editPage(list, open);
        else if (browsing) communityPage(list);
        else listPage(list);
    }

    @Override
    public void onClose() {
        Saved.save();
        super.onClose();
    }
}
