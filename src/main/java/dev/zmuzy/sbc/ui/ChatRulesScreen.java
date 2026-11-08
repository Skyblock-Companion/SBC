// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.chat.ChatFilters;
import dev.zmuzy.sbc.chat.ChatRules;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// chat rules screen, from skyblocker ChatRulesConfigScreen + the widgets around it. list with
// switch/edit/remove, and one rule's inputs and outputs
public final class ChatRulesScreen extends Screen {
    // islands a rule can be limited to (GameState keys)
    private static final String[] ISLANDS = {"HUB", "PRIVATE_ISLAND", "GARDEN", "THE_FARMING_ISLANDS", "THE_PARK", "GALATEA", "GOLD_MINES", "DEEP_CAVERNS", "DWARVEN_MINES", "CRYSTAL_HOLLOWS", "MINESHAFT", "SPIDER_DEN",
            "THE_END", "CRIMSON_ISLE", "KUUDRA", "DUNGEON_HUB", "CATACOMBS", "WINTER", "THE_RIFT", "BACKWATER_BAYOU", "DARK_AUCTION"};
    private static final int ROW = 24, W = 300;

    private final Screen parent;
    private int page;
    // null = on the list
    private ChatFilters.Rule editing;
    private boolean locations;
    private int asked = -1;
    private final List<Label> labels = new ArrayList<>();

    private record Label(String text, int x, int y) {}

    public ChatRulesScreen(Screen parent) {
        super(Component.literal("Chat Rules"));
        this.parent = parent;
    }

    private void reopen() {
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        labels.clear();
        if (editing != null && locations) locationsPage();
        else if (editing != null) rulePage();
        else listPage();
    }

    private Button button(String text, int x, int y, int w, Runnable run) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> run.run()).pos(x, y).size(w, 20).build());
    }

    private void listPage() {
        int x = (width - W) / 2, y = 34;
        List<ChatFilters.Rule> rules = Config.I.chatRules;
        int perPage = Math.max(1, (height - 34 - 34) / ROW);
        int pages = Math.max(1, (rules.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        for (int i = page * perPage; i < rules.size() && i < (page + 1) * perPage; i++, y += ROW) {
            ChatFilters.Rule r = rules.get(i);
            int index = i;
            String shown = "rule".equals(r.action) ? r.name : r.action + ": " + r.regex;
            labels.add(new Label(font.plainSubstrByWidth(shown, W - 130), x, y + 6));
            button(r.on ? "§aEnabled" : "§7Enabled", x + W - 126, y, 50, () -> {
                r.on = !r.on;
                Config.save();
                reopen();
            });
            if ("rule".equals(r.action)) button("Edit", x + W - 74, y, 34, () -> {
                editing = r;
                reopen();
            });
            button(asked == index ? "§cSure?" : "§cX", x + W - 38, y, 38, () -> {
                if (asked == index) {
                    rules.remove(index);
                    Config.save();
                    asked = -1;
                } else asked = index;
                reopen();
            });
        }
        if (rules.isEmpty()) labels.add(new Label("§7No rules yet.", x, y + 6));
        int foot = height - 28;
        button("New Chat Rule", x, foot, 110, () -> {
            ChatFilters.Rule r = ChatRules.fresh();
            rules.add(r);
            Config.save();
            editing = r;
            reopen();
        });
        if (pages > 1) {
            button("<", x + 116, foot, 20, () -> {
                page = Math.max(0, page - 1);
                reopen();
            });
            button(">", x + 138, foot, 20, () -> {
                page = Math.min(pages - 1, page + 1);
                reopen();
            });
        }
        button("Done", x + W - 80, foot, 80, this::onClose);
    }

    private void rulePage() {
        ChatFilters.Rule r = editing;
        int column = Math.min(W, (width - 24) / 2), left = width / 2 - column - 4, right = width / 2 + 4;
        int step = Math.max(21, Math.min(ROW, (height - 60) / 10));
        int y = 26;
        labels.add(new Label("§7Inputs", left, y));
        labels.add(new Label("§7Outputs. Codes with &.", right, y));
        y += 11;
        int ly = y, ry = y;
        field("Rule Name", "Name", left, ly, column, () -> r.name, v -> r.name = v);
        ly += step;
        field("Filter", "What to look for", left, ly, column, () -> r.regex, v -> r.regex = v);
        ly += step;
        toggle("Partial Match", left, ly, column / 2 - 2, () -> r.partial, v -> r.partial = v);
        toggle("Use regex", left + column / 2 + 2, ly, column / 2 - 2, () -> r.useRegex, v -> r.useRegex = v);
        ly += step;
        toggle("Ignore Case", left, ly, column / 2 - 2, () -> r.ignoreCase, v -> r.ignoreCase = v);
        toggle("Include Formatting", left + column / 2 + 2, ly, column / 2 - 2, () -> r.formatting, v -> r.formatting = v);
        ly += step;
        button("Edit Locations (" + (r.locations.isEmpty() ? "all" : r.locations.size()) + ")", left, ly, column, () -> {
            locations = true;
            reopen();
        });
        ly += step;
        toggle("Hide Message", left, ly, column, () -> r.hide, v -> r.hide = v);
        ly += step;
        if (!ChatRules.valid(r)) labels.add(new Label("§cThat regex cannot be read, so the rule does nothing.", left, ly + 6));

        field("Replace Message With", "Replace message with (empty: the original)", right, ry, column, () -> r.chat, v -> r.chat = v);
        ry += step;
        field("Action Bar", "Action bar (empty: nothing)", right, ry, column, () -> r.actionBar, v -> r.actionBar = v);
        ry += step;
        field("Announcement", "Announcement (empty: nothing)", right, ry, column - 44, () -> r.announcement, v -> r.announcement = v);
        number("Ticks", right + column - 40, ry, 40, () -> r.announcementTicks, v -> r.announcementTicks = v);
        ry += step;
        field("Toast", "Toast (empty: nothing)", right, ry, column - 44, () -> r.toast, v -> r.toast = v);
        number("Seconds", right + column - 40, ry, 40, () -> r.toastSeconds, v -> r.toastSeconds = v);
        ry += step;
        field("Toast Icon", "Toast icon, like minecraft:paper", right, ry, column, () -> r.toastIcon, v -> r.toastIcon = v);
        ry += step;
        List<String> sounds = new ArrayList<>(List.of("None"));
        sounds.addAll(ChatRules.SOUND_NAMES);
        String shown = r.sound.isBlank() ? "None" : r.sound;
        button("Sound: " + shown, right, ry, column / 2 - 2, () -> {
            int at = sounds.indexOf(r.sound.isBlank() ? "None" : r.sound);
            r.sound = sounds.get((at + 1) % sounds.size());
            if (r.sound.equals("None")) r.sound = "";
            reopen();
        });
        field("Custom sound", "Or a sound id", right + column / 2 + 2, ry, column / 2 - 2, () -> sounds.contains(r.sound) ? "" : r.sound, v -> {
            if (!v.isBlank()) r.sound = v;
        });
        button("Finish", width / 2 - 40, height - 26, 80, () -> {
            Config.save();
            editing = null;
            reopen();
        });
    }

    private void locationsPage() {
        ChatFilters.Rule r = editing;
        labels.add(new Label("§7When all locations are off, the rule works in every location.", (width - font.width("When all locations are off, the rule works in every location.")) / 2, 18));
        int cols = 3, w = 120, gap = 4, x0 = (width - cols * w - (cols - 1) * gap) / 2, y0 = 34, step = Math.max(21, Math.min(22, (height - 70) / (ISLANDS.length / cols + 1)));
        for (int i = 0; i < ISLANDS.length; i++) {
            String island = ISLANDS[i];
            button((r.locations.contains(island) ? "§a" : "§7") + GameState.pretty(island), x0 + (i % cols) * (w + gap), y0 + (i / cols) * step, w, () -> {
                if (!r.locations.remove(island)) r.locations.add(island);
                reopen();
            });
        }
        button("Finish", width / 2 - 40, height - 26, 80, () -> {
            locations = false;
            reopen();
        });
    }

    private void field(String narration, String hint, int x, int y, int w, Supplier<String> get, Consumer<String> set) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(narration));
        box.setMaxLength(512);
        box.setValue(get.get());
        box.setHint(Component.literal("§8" + hint));
        box.setResponder(set);
        addRenderableWidget(box);
    }

    private void number(String narration, int x, int y, int w, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(narration));
        box.setMaxLength(4);
        box.setValue(get.getAsInt() == 0 ? "" : String.valueOf(get.getAsInt()));
        box.setHint(Component.literal("§8" + narration));
        box.setResponder(v -> {
            try {
                set.accept(v.isBlank() ? 0 : Math.max(0, Integer.parseInt(v.trim())));
            } catch (NumberFormatException e) {
                set.accept(0);
            }
        });
        addRenderableWidget(box);
    }

    private void toggle(String text, int x, int y, int w, Supplier<Boolean> get, Consumer<Boolean> set) {
        button((get.get() ? "§a" : "§7") + text, x, y, w, () -> {
            set.accept(!get.get());
            reopen();
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.centeredText(font, editing == null ? "Chat Rules" : locations ? "Chat Rule Locations" : "Chat Rule", width / 2, 8, 0xFFFFFFFF);
        for (Label l : labels) g.text(font, l.text(), l.x(), l.y(), 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        minecraft.gui.setScreen(parent);
    }
}
