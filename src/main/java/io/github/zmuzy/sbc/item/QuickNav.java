// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.mc.mixin.ContainerAccess;
import io.github.zmuzy.sbc.ui.ItemList;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// quick nav: small buttons around every inventory and chest that open the menus you use most (sb menu, pets,
// loadouts, storage, equipment, acc bag, sacks, bz, ah, warps, crafting, anvil, enchanting, hex)
// a row above and a row below the container, never wider than it. each button sends one command on click
// every button has its own color, the one for the menu youre in stays lit
// each of the 14 is editable like skyblocker quicknav (QuickNav, QuickNavButton, QuickNavConfirmationButton):
// icon, count, components, command, tooltip, title it lights up in, shown or not, confirm click within a sec
// these are the defaults, changes go to config under quicknav.buttonN, editor is ui/panels/QuickNavEditor
public final class QuickNav {
    private QuickNav() {}

    // here = a word in the title of the menu this button opens, so it can light up there
    private record Tab(String icon, String label, String command, int color, String here) {}

    private static final List<Tab> TABS = List.of(
            new Tab("minecraft:nether_star", "SkyBlock Menu", "sbmenu", 0xFFFFF0A0, "skyblock menu"),
            new Tab("minecraft:bone", "Pets", "pets", 0xFFFF9E80, "pets"),
            new Tab("minecraft:barrel", "Loadouts", "loadouts", 0xFFD8B26E, "loadout"),
            new Tab("minecraft:ender_pearl", "Equipment", "equipment", 0xFF4FD8C4, "equipment"),
            new Tab("minecraft:chest", "Storage", "storage", 0xFFC48A5A, "storage"),
            new Tab("minecraft:ender_eye", "Accessory Bag", "accessories", 0xFFFF80AB, "accessory bag"),
            new Tab("minecraft:bundle", "Sack of Sacks", "sacks", 0xFFD7B98E, "sack of sacks"),
            new Tab("minecraft:gold_ingot", "Bazaar", "bz", 0xFFFFB454, "bazaar"),
            new Tab("minecraft:gold_block", "Auction House", "ah", 0xFFFFD166, "auction"),
            new Tab("minecraft:compass", "Warp Menu", "warp", 0xFF9CE37D, "fast travel"),
            new Tab("minecraft:crafting_table", "Crafting", "craft", 0xFFE0A070, "craft item"),
            new Tab("minecraft:anvil", "Anvil", "anvil", 0xFF9FB3C8, "anvil"),
            new Tab("minecraft:enchanting_table", "Enchanting", "et", 0xFFC77DFF, "enchant"),
            new Tab("minecraft:amethyst_shard", "The Hex", "hex", 0xFFB388FF, "the hex"));

    private static final Ui ui = new Ui();
    private static final int SIZE = 20, GAP = 2;
    // skyblocker QuickNavButton.TOGGLE_DURATION + QuickNavConfirmationButton.DOUBLE_CLICK_TIME
    private static final long LIT_AFTER_CLICK = 1000, DOUBLE_CLICK_TIME = 1000;

    // one button as it is now, defaults with changes on top
    public static final class Button {
        public final int index;
        final Tab tab;
        final ItemStack icon;
        final String command;
        final boolean render, doubleClick;
        final String tooltip;
        final java.util.regex.Pattern uiTitle;
        long sentAt, lastClicked;

        Button(int index, Tab tab) {
            this.index = index;
            this.tab = tab;
            icon = icon(get(index, "item", tab.icon()), get(index, "components", ""), (int) Math.round(number(index, "count", 1)));
            command = stripSlash(get(index, "command", tab.command()));
            render = bool(index, "render", true);
            doubleClick = bool(index, "doubleClick", false);
            tooltip = get(index, "tooltip", null);
            uiTitle = compile(index, get(index, "uiTitle", defaultTitle(tab)));
        }
    }

    private static Button[] buttons;
    private static List<Button> shown = List.of();

    public static int count() {
        return TABS.size();
    }

    // button name as we ship it
    public static String label(int i) {
        return TABS.get(i).label();
    }

    public static String defaultItem(int i) {
        return TABS.get(i).icon();
    }

    public static String defaultCommand(int i) {
        return TABS.get(i).command();
    }

    // title a button lights up in, as a pattern of the whole title
    public static String defaultTitle(int i) {
        return defaultTitle(TABS.get(i));
    }

    private static String defaultTitle(Tab t) {
        return "(?i).*" + java.util.regex.Pattern.quote(t.here()) + ".*";
    }

    private static String key(int i, String field) {
        return "quicknav.button" + (i + 1) + "." + field;
    }

    public static String get(int i, String field, String def) {
        Object v = io.github.zmuzy.sbc.core.Config.I.options.get(key(i, field));
        return v == null ? def : String.valueOf(v);
    }

    public static boolean bool(int i, String field, boolean def) {
        Object v = io.github.zmuzy.sbc.core.Config.I.options.get(key(i, field));
        return v instanceof Boolean b ? b : def;
    }

    public static double number(int i, String field, double def) {
        Object v = io.github.zmuzy.sbc.core.Config.I.options.get(key(i, field));
        return v instanceof Number n ? n.doubleValue() : def;
    }

    // sets one field of a button, null goes back to default. saved right away
    public static void set(int i, String field, Object value) {
        if (value == null) io.github.zmuzy.sbc.core.Config.I.options.remove(key(i, field));
        else io.github.zmuzy.sbc.core.Config.I.options.put(key(i, field), value);
        io.github.zmuzy.sbc.core.Config.save();
        buttons = null;
    }

    // whole button back to default
    public static void reset(int i) {
        for (String f : List.of("item", "components", "count", "command", "render", "doubleClick", "tooltip", "uiTitle")) io.github.zmuzy.sbc.core.Config.I.options.remove(key(i, f));
        io.github.zmuzy.sbc.core.Config.save();
        buttons = null;
    }

    private static String stripSlash(String c) {
        c = c.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }

    // icon: a skyblock id, or a vanilla item with components like /give (skyblocker
    // ItemStackComponentizationFixer). barrier if neither
    public static ItemStack icon(String item, String components, int count) {
        String id = item == null ? "" : item.trim();
        int n = Math.clamp(count, 1, 99);
        try {
            if (!id.contains(":") && io.github.zmuzy.sbc.core.ItemRepo.get(id.toUpperCase(Locale.ROOT)) != null) {
                ItemStack st = io.github.zmuzy.sbc.core.ItemInfo.stack(id.toUpperCase(Locale.ROOT)).copy();
                st.setCount(n);
                return st;
            }
            var level = Minecraft.getInstance().level;
            if (components != null && !components.isBlank() && level != null) {
                var parsed = new net.minecraft.commands.arguments.item.ItemParser(level.registryAccess())
                        .parse(new com.mojang.brigadier.StringReader((id.contains(":") ? id : "minecraft:" + id) + components.trim()));
                ItemStack st = new ItemStack(parsed.item(), n);
                st.applyComponents(parsed.components());
                return st;
            }
            net.minecraft.world.item.Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id.contains(":") ? net.minecraft.resources.Identifier.parse(id) : net.minecraft.resources.Identifier.withDefaultNamespace(id.toLowerCase(Locale.ROOT)));
            if (it == null || it == net.minecraft.world.item.Items.AIR) return new ItemStack(net.minecraft.world.item.Items.BARRIER);
            if (n == 1) return io.github.zmuzy.sbc.core.LegacyItems.stack(id);
            return new ItemStack(it, n);
        } catch (Exception e) {
            return new ItemStack(net.minecraft.world.item.Items.BARRIER);
        }
    }

    private static java.util.regex.Pattern compile(int i, String regex) {
        try {
            return java.util.regex.Pattern.compile(regex);
        } catch (java.util.regex.PatternSyntaxException e) {
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7b[SBC] \u00a7cInvalid pattern in Quick Navigation button " + (i + 1) + "!"));
            return null;
        }
    }

    // buttons as they are now, rebuilt after a change
    public static Button[] buttons() {
        if (buttons == null) {
            Button[] all = new Button[TABS.size()];
            List<Button> on = new ArrayList<>();
            for (int i = 0; i < all.length; i++) {
                all[i] = new Button(i, TABS.get(i));
                if (all[i].render) on.add(all[i]);
            }
            buttons = all;
            shown = on;
        }
        return buttons;
    }

    private static List<Button> shown() {
        buttons();
        return shown;
    }

    // button tooltip: what was typed (text json or plain, nothing for none), else name + command
    private static List<net.minecraft.network.chat.Component> tip(Button b) {
        List<net.minecraft.network.chat.Component> out = new ArrayList<>();
        if (b.tooltip == null) {
            out.add(net.minecraft.network.chat.Component.literal("\u00a7f" + b.tab.label()));
            out.add(net.minecraft.network.chat.Component.literal("\u00a77/" + b.command));
        } else if (!b.tooltip.isEmpty()) {
            try {
                out.add(net.minecraft.network.chat.ComponentSerialization.CODEC.decode(com.mojang.serialization.JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(b.tooltip)).getOrThrow().getFirst());
            } catch (Exception e) {
                out.add(net.minecraft.network.chat.Component.literal(b.tooltip));
            }
        }
        return out;
    }

    // what a button sends without the slash, null if no such button
    static String commandOf(String label) {
        for (Tab t : TABS) if (t.label().equals(label)) return t.command();
        return null;
    }

    // the band above the chest its buttons take, so labels drawn there move up
    public static int[] band(Screen s, int top) {
        if (!applies(s)) return null;
        int y = rowTop(top);
        return new int[]{y, y + SIZE};
    }

    private static boolean applies(Screen s) {
        return Features.on("quicknav") && (GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV) && s instanceof AbstractContainerScreen<?>
                && !(s instanceof CreativeModeInventoryScreen) && !(s instanceof io.github.zmuzy.sbc.ui.storage.StorageOverlayScreen);
    }

    // top edge of the row above the container, -1 if the buttons arent there
    public static int rowTop(Screen s) {
        return applies(s) ? rowTop(((ContainerAccess) s).sbc$top()) : -1;
    }

    private static int rowTop(int top) {
        return Math.max(2, top - SIZE - 4);
    }

    // top left of every button in tab order: one row above the container, the rest below
    private static List<int[]> layout(int left, int top, int cw, int ch, int sh) {
        int perRow = Math.max(1, (cw + GAP) / (SIZE + GAP));
        int n = shown().size();
        int topCount = n <= perRow ? n : Math.min(perRow, (n + 1) / 2);
        List<int[]> out = new ArrayList<>(n);
        int i = 0;
        for (int row = 0; i < n; row++) {
            int count = row == 0 ? topCount : Math.min(perRow, n - i);
            int rowW = count * SIZE + (count - 1) * GAP;
            int x = left + (cw - rowW) / 2;
            int y = row == 0 ? rowTop(top) : Math.min(sh - SIZE - 2, top + ch + 4 + (row - 1) * (SIZE + GAP));
            for (int k = 0; k < count; k++, i++) out.add(new int[]{x + k * (SIZE + GAP), y});
        }
        return out;
    }

    private static List<int[]> layout(Screen s) {
        ContainerAccess a = (ContainerAccess) s;
        return layout(a.sbc$left(), a.sbc$top(), a.sbc$width(), a.sbc$height(), s.height);
    }

    // all buttons around a container here, title = the open menus
    public static void paint(Ui ui, int left, int top, int cw, int ch, int sh, String title) {
        Theme t = Theme.current();
        List<int[]> pos = layout(left, top, cw, ch, sh);
        List<Button> on = shown();
        String whole = GameState.strip(title == null ? "" : title);
        for (int i = 0; i < on.size(); i++) {
            Button button = on.get(i);
            Tab tab = button.tab;
            int x = pos.get(i)[0], y = pos.get(i)[1];
            boolean hv = ui.hover(x, y, SIZE, SIZE);
            t.panel(ui, x, y, SIZE, SIZE);
            boolean lit = button.uiTitle != null && button.uiTitle.matcher(whole).matches() || ui.now - button.sentAt < LIT_AFTER_CLICK;
            if (hv || lit) ui.rect(x + 1, y + 1, SIZE - 2, SIZE - 2, Ui.alpha(tab.color(), hv ? 0.34f : 0.22f));
            ui.rect(x + 1, y + SIZE - 2, SIZE - 2, 1, hv || lit ? tab.color() : Ui.alpha(tab.color(), 0.7f));
            ui.icon(button.icon, x + 2, y + 2, 1f);
            if (button.icon.getCount() > 1) {
                String n = String.valueOf(button.icon.getCount());
                ui.textShadow(n, x + SIZE - 2 - ui.width(n), y + SIZE - 10, Modern.INK);
            }
            if (hv) {
                if (button.doubleClick && ui.now - button.lastClicked < DOUBLE_CLICK_TIME) ui.tooltip("§eClick again to confirm.");
                else {
                    List<net.minecraft.network.chat.Component> tip = tip(button);
                    if (!tip.isEmpty()) ui.tooltip.add(tip);
                }
            }
        }
    }

    public static void draw(Screen s, GuiGraphicsExtractor g, int mx, int my) {
        if (!applies(s)) return;
        ui.begin(g, mx, my);
        ContainerAccess a = (ContainerAccess) s;
        paint(ui, a.sbc$left(), a.sbc$top(), a.sbc$width(), a.sbc$height(), s.height, s.getTitle().getString());
        ui.flushTooltip();
    }

    // skyblocker QuickNavButton.onClick + QuickNavConfirmationButton.onClick: a button just pressed waits a sec,
    // one that wants 2 clicks takes the 2nd within a sec
    private static void press(Button b, long now) {
        if (now - b.sentAt < LIT_AFTER_CLICK) return;
        Minecraft mc = Minecraft.getInstance();
        if (b.doubleClick && now - b.lastClicked >= DOUBLE_CLICK_TIME) {
            b.lastClicked = now;
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME, 1.0f));
            return;
        }
        b.sentAt = now;
        var conn = mc.getConnection();
        if (b.command.isEmpty()) {
            if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("\u00a7b[SBC] \u00a7cQuick Navigation button " + (b.index + 1) + " has no command!"));
        } else if (conn != null) conn.sendCommand(b.command);
    }

    // mouse click on a container screen, false when a button took it. only left click sends, and no button throws
    // what youre holding
    public static boolean click(Screen s, double mx, double my, int button) {
        if (!applies(s)) return true;
        List<int[]> pos = layout(s);
        for (int i = 0; i < pos.size(); i++) {
            int x = pos.get(i)[0], y = pos.get(i)[1];
            if (mx < x || my < y || mx >= x + SIZE || my >= y + SIZE) continue;
            if (button == 0) press(shown().get(i), System.currentTimeMillis());
            ItemList.pressTaken();
            return false;
        }
        return true;
    }
}
