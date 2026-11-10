// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// an open hypixel menu the way the rules see it: title + what each slot holds, already read
// a rule only ever says "this slot gets this color or number", so the same rule runs on the real menu and on
// a list of lines in a test
public final class MenuView {
    public static final int TOP_LEFT = 0, TOP_RIGHT = 1, BOTTOM_LEFT = 2, BOTTOM_RIGHT = 3;

    // skyhanni LorenzColor as highlights. the item is drawn over them so they stay readable
    public static final int RED = 0xC0FF5555, GREEN = 0xC055FF55, YELLOW = 0xC0FFFF55, AQUA = 0xC055FFFF, GOLD = 0xC0FFAA00, BLUE = 0xC05555FF;

    // a slots content, read once
    public static final class Item {
        public static final Item EMPTY = new Item("", "", "", List.of(), new CompoundTag(), 0);

        public final String id, name, clean, model;
        // item_model component if it has one, like heart of the forest locked perks
        public String itemModel = "";
        public final List<String> lore, cleanLore;
        public final CompoundTag tag;
        public final int count;

        public Item(String id, String name, String model, List<String> lore, CompoundTag tag, int count) {
            this.id = id;
            this.name = name;
            this.clean = GameState.strip(name).trim();
            this.model = model;
            this.lore = lore;
            List<String> plain = new ArrayList<>(lore.size());
            for (String l : lore) plain.add(GameState.strip(l));
            this.cleanLore = plain;
            this.tag = tag;
            this.count = count;
        }

        public static Item of(ItemStack st) {
            if (st == null || st.isEmpty()) return EMPTY;
            var data = st.get(DataComponents.CUSTOM_DATA);
            Item it = new Item(Gain.sbId(st), LegacyText.name(st), BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), LegacyText.lore(st), data == null ? new CompoundTag() : data.copyTag(), st.getCount());
            var model = st.get(DataComponents.ITEM_MODEL);
            if (model != null) it.itemModel = model.getPath();
            return it;
        }

        // test item with a name + lore lines
        public static Item of(String id, String name, String model, String... lore) {
            return new Item(id, name, model, List.of(lore), new CompoundTag(), 1);
        }

        public boolean empty() {
            return this == EMPTY || model.isEmpty();
        }

        public boolean loreHas(String text) {
            for (String l : lore) if (l.contains(text)) return true;
            return false;
        }

        public boolean cleanHas(String text) {
            for (String l : cleanLore) if (l.contains(text)) return true;
            return false;
        }

        // skyhanni firstMatcher: first whole line that matches, or null
        public Matcher first(Pattern p) {
            return first(p, lore);
        }

        public Matcher firstClean(Pattern p) {
            return first(p, cleanLore);
        }

        private static Matcher first(Pattern p, List<String> lines) {
            for (String l : lines) {
                Matcher m = p.matcher(l);
                if (m.matches()) return m;
            }
            return null;
        }

        public String lastLore() {
            return lore.isEmpty() ? "" : lore.get(lore.size() - 1);
        }

        public String lastClean() {
            return cleanLore.isEmpty() ? "" : cleanLore.get(cleanLore.size() - 1);
        }
    }

    // free text near a slot (can go outside it), for the tips skyhanni places by hand
    public record Label(String text, int dx, int dy, float scale) {}

    // what a slot gets, anything not set isnt drawn
    public static final class Mark {
        // fill behind the item, over the item, and a 1 px frame
        public int back, front, border;
        // instead of the stack count
        public String number;
        public final String[] corners = new String[4];
        public final int[] cornerColors = {0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        // tooltip that replaces the slots own
        public List<String> tip;
        public List<Label> labels;
        // something to draw in the slot instead of it looking empty, + what its tooltip says
        public ItemStack icon;
        // small icon in the slots corner over the item, says what its for without hiding the item
        public ItemStack badge;
    }

    public final String title;
    public final Item[] items;
    // how many slots are the menus own, the rest is the player inventory
    public final int top;
    public final Mark[] marks;

    public MenuView(String title, Item[] items, int top) {
        this.title = title;
        this.items = items;
        this.top = top;
        this.marks = new Mark[items.length];
    }

    // test: menu items, then 36 empty inventory slots
    public static MenuView chest(String title, Item... top) {
        Item[] all = Arrays.copyOf(top, top.length + 36);
        Arrays.fill(all, top.length, all.length, Item.EMPTY);
        return new MenuView(title, all, top.length);
    }

    public Item item(int slot) {
        return slot >= 0 && slot < items.length ? items[slot] : Item.EMPTY;
    }

    public Mark mark(int slot) {
        Mark m = marks[slot];
        if (m == null) marks[slot] = m = new Mark();
        return m;
    }

    public MenuView back(int slot, int color) {
        if (slot >= 0 && slot < items.length) mark(slot).back = color;
        return this;
    }

    public MenuView number(int slot, String text) {
        if (slot >= 0 && slot < items.length && text != null) mark(slot).number = text;
        return this;
    }

    // only if nothing else took that corner, first rule to speak keeps it
    public MenuView corner(int slot, int corner, String text) {
        if (slot >= 0 && slot < items.length && text != null && !text.isEmpty() && mark(slot).corners[corner] == null) mark(slot).corners[corner] = text;
        return this;
    }

    public MenuView corner(int slot, int corner, String text, int rgb) {
        if (slot >= 0 && slot < items.length && text != null && !text.isEmpty() && mark(slot).corners[corner] == null) {
            mark(slot).corners[corner] = text;
            mark(slot).cornerColors[corner] = 0xFF000000 | rgb;
        }
        return this;
    }

    public MenuView label(int slot, String text, int dx, int dy, float scale) {
        if (slot < 0 || slot >= items.length) return this;
        Mark m = mark(slot);
        if (m.labels == null) m.labels = new ArrayList<>(1);
        m.labels.add(new Label(text, dx, dy, scale));
        return this;
    }

    // slot with this exact name, or -1
    public int find(String cleanName) {
        for (int i = 0; i < top; i++) if (items[i].clean.equals(cleanName)) return i;
        return -1;
    }

    public int itemsLength() {
        return items.length;
    }
}
