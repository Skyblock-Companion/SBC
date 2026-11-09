// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.authlib.GameProfile;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.WardrobeKeys;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// custom wardrobe: all 27 slots at once, each a player wearing that set, instead of hypixels armor sets menu.
// starts off bc it replaces the menu. port of skyhanni features/inventory/wardrobe
public final class CustomWardrobe {
    private static final String ID = "custom_wardrobe";
    private static final Pattern TITLE = Patterns.get("SH.inventory.wardrobe.armor.name");
    private static final Pattern EQUIPPED = Patterns.get("SH.inventory.wardrobe.equippedslot.colorless");
    private static final int FIRST_SLOT = 36, PER_PAGE = 9, PAGES = 3, SLOTS = PER_PAGE * PAGES, BACK = 48, CLOSE = 49, PREVIOUS = 45, NEXT = 53;
    // menu goes top to bottom = head to feet
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // one wardrobe slot, numbered from 1 over all pages
    public static final class Set {
        public final int id, page, column;
        public final ItemStack[] armor = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        public final ItemStack[] shown = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        public boolean locked = true;

        Set(int id) {
            this.id = id;
            page = (id - 1) / PER_PAGE + 1;
            column = (id - 1) % PER_PAGE;
        }

        public boolean empty() {
            for (ItemStack s : armor) if (!s.isEmpty()) return false;
            return true;
        }
    }

    private static final Set[] SETS = new Set[SLOTS];

    static {
        for (int i = 0; i < SLOTS; i++) SETS[i] = new Set(i + 1);
    }

    private static int currentPage, currentSlot = -1;
    private static boolean waiting, switching;
    private static String loaded = "";
    private static KeyMapping tooltipKey;
    private static RemotePlayer fake;
    private static boolean broken;

    private CustomWardrobe() {}

    static void register(KeyMapping.Category cat) {
        tooltipKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.wardrobe_tooltip", InputConstants.Type.KEYBOARD, InputConstants.KEY_LSHIFT, cat));
        Features.add(new Feature(ID, "Menus", "Custom Wardrobe", "All your wardrobe sets on one screen, each a player wearing it, in place of Hypixel's Armor Sets menu. Click one to wear it, or to go to its page. Favourites and hiding of empty or locked slots. It replaces the menu, so it starts off.", "SkyHanni: custom wardrobe")
                .defaultOff().when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("custom_wardrobe.hideEmptySlots", "Hide empty slots", "Hide wardrobe slots with no armor.", false))
                .option(Option.bool("custom_wardrobe.hideLockedSlots", "Hide locked slots", "", false))
                .option(Option.bool("custom_wardrobe.eyesFollowMouse", "Players follow the mouse", "", true))
                .option(Option.bool("custom_wardrobe.loadingText", "Loading text", "Say so while a page has not arrived yet.", true))
                .option(Option.bool("custom_wardrobe.showTooltipOnlyKeybind", "Armor tooltip only with its key", "Only show a piece's tooltip while the Wardrobe tooltip key is held.", false))
                .option(Option.color("custom_wardrobe.backgroundColor", "Background colour", "", 0x7F000000))
                .option(Option.color("custom_wardrobe.equippedColor", "Equipped colour", "", 0x7F55FF55))
                .option(Option.color("custom_wardrobe.favoriteColor", "Favourite colour", "", 0x7FFF5555))
                .option(Option.color("custom_wardrobe.samePageColor", "Same page colour", "", 0x7F5E6CFF))
                .option(Option.color("custom_wardrobe.otherPageColor", "Other page colour", "", 0x7F000000))
                .option(Option.color("custom_wardrobe.topBorderColor", "Top outline colour", "When hovered.", 0xFFFFC800))
                .option(Option.color("custom_wardrobe.bottomBorderColor", "Bottom outline colour", "When hovered.", 0xFFFF0000))
                .option(Option.integer("custom_wardrobe.globalScale", "Scale of everything", "", 100, 20, 200))
                .option(Option.integer("custom_wardrobe.slotWidth", "Slot width", "", 75, 30, 200))
                .option(Option.integer("custom_wardrobe.slotHeight", "Slot height", "", 140, 40, 300))
                .option(Option.integer("custom_wardrobe.playerScale", "Player scale", "", 75, 10, 200))
                .option(Option.integer("custom_wardrobe.maxPlayersPerRow", "Slots per row", "", 9, 1, 27))
                .option(Option.integer("custom_wardrobe.horizontalSpacing", "Space between slots, across", "", 3, 0, 20))
                .option(Option.integer("custom_wardrobe.verticalSpacing", "Space between slots, down", "", 3, 0, 20))
                .option(Option.integer("custom_wardrobe.backgroundPadding", "Background padding", "", 10, 0, 40))
                .option(Option.integer("custom_wardrobe.buttonSlotsVerticalSpacing", "Space above the buttons", "", 10, 0, 40))
                .option(Option.integer("custom_wardrobe.buttonWidth", "Button width", "", 50, 20, 100))
                .option(Option.integer("custom_wardrobe.buttonHeight", "Button height", "", 20, 10, 40))
                .option(Option.integer("custom_wardrobe.buttonHorizontalSpacing", "Space between buttons, across", "", 10, 0, 40))
                .option(Option.integer("custom_wardrobe.buttonVerticalSpacing", "Space between buttons, down", "", 10, 0, 40))
                .option(Option.integer("custom_wardrobe.outlineThickness", "Outline thickness", "", 5, 0, 15))
                .option(Option.number("custom_wardrobe.outlineBlur", "Outline blur", "", 0.5, 0, 1, 0.1)));
    }

    private static Option option(String key) {
        Feature f = Features.get(ID);
        return f == null ? null : f.option(key);
    }

    private static int number(String key, int def) {
        Option o = option(key);
        return o == null ? def : o.integer();
    }

    private static boolean flag(String key, boolean def) {
        Option o = option(key);
        return o == null ? def : o.bool();
    }

    private static int color(String key, int def) {
        Option o = option(key);
        return o == null ? def : o.color();
    }

    // --- reading the menu ---

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    private static ItemStack piece(ItemStack st) {
        return st.isEmpty() || path(st).endsWith("glass_pane") ? ItemStack.EMPTY : st;
    }

    private static ItemStack withoutShine(ItemStack st) {
        if (st.isEmpty()) return st;
        ItemStack out = st.copy();
        out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        return out;
    }

    // from skyhanni handleInventoryUpdated: what this page shows, other pages stay as last seen
    public static void read(String title, IntFunction<ItemStack> slotAt) {
        Matcher m = TITLE.matcher(title);
        if (!m.matches()) return;
        int page = Integer.parseInt(m.group("currentPage"));
        if (page < 1 || page > PAGES) return;
        boolean allGray = true, allEmpty = true;
        for (int c = 0; c < PER_PAGE; c++) {
            if (!path(slotAt.apply(FIRST_SLOT + c)).equals("gray_dye")) allGray = false;
            for (int r = 0; r < 4; r++) if (!piece(slotAt.apply(r * 9 + c)).isEmpty()) allEmpty = false;
        }
        if (allGray && !allEmpty) return;
        boolean found = false, changed = false, lockRest = false;
        for (int c = 0; c < PER_PAGE; c++) {
            Set s = SETS[(page - 1) * PER_PAGE + c];
            for (int r = 0; r < 4; r++) {
                ItemStack now = allGray ? ItemStack.EMPTY : piece(slotAt.apply(r * 9 + c));
                if (!ItemStack.matches(s.armor[r], now)) {
                    s.armor[r] = now.copy();
                    s.shown[r] = withoutShine(now);
                    changed = true;
                }
            }
            ItemStack icon = slotAt.apply(FIRST_SLOT + c);
            if (EQUIPPED.matcher(GameState.strip(icon.getHoverName().getString()).trim()).matches()) {
                currentSlot = s.id;
                found = true;
            }
            boolean locked = path(icon).equals("red_dye");
            if (s.locked != locked) changed = true;
            s.locked = locked;
            if (locked) lockRest = true;
        }
        if (lockRest) {
            int first = 0;
            for (int i = (page - 1) * PER_PAGE; i < SLOTS; i++) {
                if (first > 0) SETS[i].locked = true;
                else if (SETS[i].locked) first = SETS[i].id;
            }
        }
        if (!found && currentSlot > 0 && (currentSlot - 1) / PER_PAGE + 1 == page) currentSlot = -1;
        if (page != currentPage || waiting) {
            currentPage = page;
            waiting = false;
        }
        if (changed) save();
    }

    private static Path file() {
        return Config.DIR.resolve("wardrobe").resolve(PanelStore.key() + ".nbt");
    }

    private static void save() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || PanelStore.key().isEmpty()) return;
        List<ItemStack> all = new ArrayList<>(SLOTS * 4);
        for (Set s : SETS) all.addAll(Arrays.asList(s.armor));
        EquipmentCache.writeStacks(mc, file(), all);
    }

    private static void load(Minecraft mc) {
        if (PanelStore.key().equals(loaded) || mc.level == null) return;
        loaded = PanelStore.key();
        currentSlot = -1;
        List<ItemStack> all = PanelStore.key().isEmpty() ? List.of() : EquipmentCache.readStacks(mc, file());
        for (Set s : SETS) {
            s.locked = false;
            for (int r = 0; r < 4; r++) {
                int at = (s.id - 1) * 4 + r;
                s.armor[r] = at < all.size() ? all.get(at) : ItemStack.EMPTY;
                s.shown[r] = withoutShine(s.armor[r]);
            }
        }
    }

    // --- opening ---

    // from skyhanni MenuScreensHook.openCustomWardrobe: true when the menu opened here or changed under our open screen
    public static boolean open(Minecraft mc, ChestMenu menu, Component name) {
        if (!Features.on(ID) || !TITLE.matcher(GameState.strip(name.getString()).trim()).matches() || mc.player == null) return false;
        load(mc);
        mc.player.containerMenu = menu;
        Screen now = mc.gui.screen();
        if (now instanceof WardrobeScreen s) {
            s.menu = menu;
            s.shownTitle = name;
        }
        else if (now instanceof EditScreen) {
            switching = true;
            mc.gui.setScreen(new EditScreen(menu, mc.player.getInventory(), name));
            switching = false;
        } else mc.gui.setScreen(new WardrobeScreen(menu, name));
        return true;
    }

    private static List<Set> visible() {
        List<Set> out = new ArrayList<>(SLOTS);
        boolean hideLocked = flag("hideLockedSlots", false), hideEmpty = flag("hideEmptySlots", false), only = PanelStore.data().wardrobeOnlyFavourites;
        for (Set s : SETS) {
            if (hideLocked && s.locked) continue;
            if (hideEmpty && s.empty()) continue;
            if (only && !PanelStore.data().wardrobeFavourites.contains(s.id) && s.id != currentSlot) continue;
            out.add(s);
        }
        return out;
    }

    // from skyhanni createWarning: what to say instead of a grid
    private static String warning() {
        if (!visible().isEmpty()) return null;
        if (PanelStore.data().wardrobeOnlyFavourites) return "§cDidn't set any favorites";
        if (flag("hideEmptySlots", false)) return "§cAll slots are empty :(";
        return "§cAll your slots are locked? Somehow";
    }

    private static boolean inPage(Set s) {
        return (currentPage == 0 && s.page == 1) || s.page == currentPage;
    }

    // from skyhanni WardrobeSlot.clickSlot: wear whats on this page, go to the page of what isnt
    private static void clickSet(ChestMenu menu, Set s) {
        Minecraft mc = Minecraft.getInstance();
        if (currentPage == 0) return;
        if (inPage(s)) {
            if (s.empty() || s.locked || waiting) return;
            currentSlot = s.id == currentSlot ? -1 : s.id;
            click(mc, menu, FIRST_SLOT + s.column);
        } else {
            waiting = true;
            click(mc, menu, s.page < currentPage ? PREVIOUS : NEXT);
        }
    }

    private static void click(Minecraft mc, ChestMenu menu, int slot) {
        if (mc.gameMode != null && mc.player != null) mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
    }

    private static int darker(int c, float f) {
        int a = c >>> 24, r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // from skyhanni getSlotColor
    private static int slotColor(Set s, boolean favourite) {
        int c;
        if (s.id == currentSlot) c = color("equippedColor", 0x7F55FF55);
        else if (favourite && !PanelStore.data().wardrobeOnlyFavourites) c = color("favoriteColor", 0x7FFF5555);
        else {
            c = color(inPage(s) ? "samePageColor" : "otherPageColor", inPage(s) ? 0x7F5E6CFF : 0x7F000000);
            if (s.locked || s.empty()) c = darker(c, 0.2f);
            return (100 << 24) | (c & 0xFFFFFF);
        }
        return inPage(s) ? c : darker(c, 0.7f);
    }

    // sizes at this scale, the whole thing shrinks to 95% of the window like updateScreenSize
    record Layout(float scale, int cols, int rows, int sw, int sh, int hGap, int vGap, int pad, int bw, int bh, int btnHGap, int btnVGap, int gapAbove, int width, int height, int left, int top) {}

    static Layout layout(int screenW, int screenH) {
        float scale = number("globalScale", 100) / 100f;
        Layout at = sized(scale, screenW, screenH);
        float most = Math.min(0.95f * screenW / (at.width() / scale), 0.95f * screenH / (at.height() / scale));
        return most < scale ? sized(most, screenW, screenH) : at;
    }

    private static Layout sized(float scale, int screenW, int screenH) {
        int perRow = Math.max(1, number("maxPlayersPerRow", 9)), rows = (SLOTS - 1) / perRow + 1;
        int sw = Math.round(number("slotWidth", 75) * scale), sh = Math.round(number("slotHeight", 140) * scale);
        int hGap = Math.round(number("horizontalSpacing", 3) * scale), vGap = Math.round(number("verticalSpacing", 3) * scale);
        int pad = Math.round(number("backgroundPadding", 10) * scale), gap = Math.round(number("buttonSlotsVerticalSpacing", 10) * scale);
        int bw = Math.round(number("buttonWidth", 50) * scale), bh = Math.round(number("buttonHeight", 20) * scale);
        int bhg = Math.round(number("buttonHorizontalSpacing", 10) * scale), bvg = Math.round(number("buttonVerticalSpacing", 10) * scale);
        int buttonsW = 3 * bw + 2 * bhg, buttonsH = 2 * bh + bvg;
        int w = Math.max(perRow * sw + (perRow - 1) * hGap, buttonsW) + 2 * pad;
        int h = rows * sh + (rows - 1) * vGap + gap + buttonsH + 2 * pad;
        return new Layout(scale, perRow, rows, sw, sh, hGap, vGap, pad, bw, bh, bhg, bvg, gap, w, h, (screenW - w) / 2, (screenH - h) / 2);
    }

    private static void outline(Ui ui, int x, int y, int w, int h, int top, int bottom, int thick, float blur) {
        for (int k = 0; k < thick; k++) {
            float fade = 1f - blur * k / Math.max(1, thick);
            int t = Ui.alpha(top, fade), b = Ui.alpha(bottom, fade);
            ui.rect(x + k, y + k, w - 2 * k, 1, t);
            ui.rect(x + k, y + h - 1 - k, w - 2 * k, 1, b);
            ui.gradV(x + k, y + k + 1, 1, h - 2 * k - 2, t, b);
            ui.gradV(x + w - 1 - k, y + k + 1, 1, h - 2 * k - 2, t, b);
        }
    }

    // whole screen. models = draw the players (pictures cant). hover = the piece to tooltip
    static void paint(Ui ui, int screenW, int screenH, ChestMenu menu, boolean models, ItemStack[] hover) {
        Layout l = layout(screenW, screenH);
        ui.rect(l.left(), l.top(), l.width(), l.height(), color("backgroundColor", 0x7F000000));
        String warn = warning();
        int gridTop = l.top() + l.pad();
        List<Set> sets = visible();
        boolean onlyKey = flag("showTooltipOnlyKeybind", false);
        if (warn != null) {
            ui.textShadow(warn, l.left() + (l.width() - ui.width(warn)) / 2, gridTop + 20, Modern.INK);
        } else {
            for (int i = 0; i < sets.size(); i++) {
                Set s = sets.get(i);
                int x = l.left() + l.pad() + (i % l.cols()) * (l.sw() + l.hGap()), y = gridTop + (i / l.cols()) * (l.sh() + l.vGap());
                set(ui, s, x, y, l, menu, models, onlyKey, hover);
            }
        }
        if (waiting && flag("loadingText", true)) {
            String t = "§cLoading...";
            ui.textShadow(t, (screenW - ui.width(t)) / 2, Math.max(2, l.top() - 11), Modern.INK);
        }
        buttons(ui, l, menu);
    }

    private static void set(Ui ui, Set s, int x, int y, Layout l, ChestMenu menu, boolean models, boolean onlyKey, ItemStack[] hover) {
        int w = l.sw(), h = l.sh();
        boolean hv = ui.hover(x, y, w, h), favourite = PanelStore.data().wardrobeFavourites.contains(s.id);
        ui.rect(x, y, w, h, slotColor(s, favourite));
        if (hv && !s.empty() && !s.locked) {
            int top = color("topBorderColor", 0xFFFFC800), bottom = color("bottomBorderColor", 0xFFFF0000);
            if (!inPage(s)) {
                top = darker(top, 0.5f);
                bottom = darker(bottom, 0.5f);
            }
            Option blur = option("outlineBlur");
            outline(ui, x, y, w, h, top, bottom, Math.round(number("outlineThickness", 5) * l.scale()), blur == null ? 0.5f : (float) blur.number());
        }
        if (models) model(ui, s, x, y, w, h, l);
        else for (int r = 0; r < 4; r++) ui.icon(s.shown[r], x + (w - 16) / 2, y + 6 + r * (h - 12) / 4, 1f);
        ui.click(x, y, w, h, () -> clickSet(menu, s));
        if (hv && !s.empty() && (!onlyKey || keyHeld())) {
            int band = Math.max(0, Math.min(3, (ui.my - y) * 4 / Math.max(1, h)));
            if (!s.armor[band].isEmpty()) hover[0] = s.armor[band];
        }
        // from skyhanni addSlotHoverableButtons: the heart + what the set is worth
        boolean shown = !s.empty() && !s.locked;
        if (!shown && !favourite) return;
        int hx = x + w - 11, hy = y + 2;
        boolean overHeart = ui.hover(hx, hy, 10, 10);
        ui.textShadow(overHeart ? (favourite ? "§4❤" : "§8❤") : (favourite ? "§c❤" : "§7❤"), hx + 1, hy + 1, Modern.INK);
        ui.click(hx, hy, 10, 10, () -> {
            var set = PanelStore.data().wardrobeFavourites;
            if (!set.remove(s.id)) set.add(s.id);
            PanelStore.save();
        });
        if (shown && Features.on("wardrobe_value")) {
            int vy = hy + 10;
            boolean overValue = ui.hover(hx, vy, 10, 10);
            ui.textShadow("§2$", hx + 2, vy + 1, Modern.INK);
            if (overValue) {
                hover[0] = null;
                List<String> lore = Loadouts.priceLore("Armor", Arrays.asList(s.armor));
                if (!lore.isEmpty()) ui.tooltip(lore.toArray(new String[0]));
            }
        }
    }

    private static boolean keyHeld() {
        return dev.zmuzy.sbc.mc.KeyHeld.held(tooltipKey);
    }

    private static void model(Ui ui, Set s, int x, int y, int w, int h, Layout l) {
        Minecraft mc = Minecraft.getInstance();
        RemotePlayer p = fake(mc);
        if (p == null) return;
        try {
            for (int r = 0; r < 4; r++) p.setItemSlot(WORN[r], s.shown[r]);
            float scale = w * (number("playerScale", 75) / 100f);
            if (!inPage(s)) scale *= 0.9f;
            int cx = x + w / 2, cy = y + h / 2;
            boolean follow = flag("eyesFollowMouse", true);
            InventoryScreen.extractEntityInInventoryFollowsMouse(ui.g, x, y, x + w, y + h, Math.max(5, Math.round(scale / 2)), 0.0625f, follow ? ui.mx : cx, follow ? ui.my : cy, p);
        } catch (Throwable t) {
            broken = true;
            SbcMod.LOG.warn("Could not draw a wardrobe player", t);
        }
    }

    // from skyhanni FakePlayer.fromLocalPlayer: a copy of you the world knows nothing about
    private static RemotePlayer fake(Minecraft mc) {
        if (broken || mc.level == null || mc.player == null) return null;
        if (fake != null && fake.level() == mc.level) return fake;
        GameProfile profile = mc.player.getGameProfile();
        var me = mc.player;
        fake = new RemotePlayer(mc.level, profile) {
            @Override
            public PlayerSkin getSkin() {
                return me.getSkin();
            }

            @Override
            public boolean isModelPartShown(PlayerModelPart part) {
                return part != PlayerModelPart.CAPE;
            }
        };
        fake.setCustomNameVisible(false);
        fake.setId(-1);
        return fake;
    }

    // from skyhanni addButtons: back, close, favorite in a row, edit under
    private static void buttons(Ui ui, Layout l, ChestMenu menu) {
        int rowW = 3 * l.bw() + 2 * l.btnHGap(), x = l.left() + (l.width() - rowW) / 2;
        int y = l.top() + l.height() - l.pad() - 2 * l.bh() - l.btnVGap();
        Minecraft mc = Minecraft.getInstance();
        ui.button("cw_back", x, y, l.bw(), l.bh(), "§aBack", Ui.SOFT, () -> click(mc, menu, BACK));
        ui.button("cw_close", x + l.bw() + l.btnHGap(), y, l.bw(), l.bh(), "§cClose", Ui.SOFT, () -> click(mc, menu, CLOSE));
        boolean only = PanelStore.data().wardrobeOnlyFavourites;
        ui.button("cw_fav", x + 2 * (l.bw() + l.btnHGap()), y, l.bw(), l.bh(), (only ? "§a" : "§e") + "Favorite", Ui.SOFT, () -> {
            PanelStore.data().wardrobeOnlyFavourites = !PanelStore.data().wardrobeOnlyFavourites;
            PanelStore.save();
        });
        int ey = y + l.bh() + l.btnVGap();
        ui.button("cw_edit", l.left() + (l.width() - l.bw()) / 2, ey, l.bw(), l.bh(), "§bEdit", Ui.SOFT, () -> mc.execute(() -> {
            switching = true;
            mc.gui.setScreen(new EditScreen(menu, mc.player.getInventory(), Component.literal("Armor Sets")));
            switching = false;
        }));
    }

    // --- the screens ---

    // grid instead of the chest. chest stays open and gets read a few times a sec
    private static final class WardrobeScreen extends Screen {
        private final Ui ui = new Ui();
        ChestMenu menu;
        private long keyAt, readAt;
        Component shownTitle;

        WardrobeScreen(ChestMenu menu, Component title) {
            super(title);
            this.menu = menu;
            shownTitle = title;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractBackground(g, mouseX, mouseY, a);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractRenderState(g, mouseX, mouseY, a);
            long now = System.currentTimeMillis();
            if (now - readAt > 150) {
                readAt = now;
                read(GameState.strip(shownTitle.getString()).trim(), i -> i < menu.slots.size() ? menu.getSlot(i).getItem() : ItemStack.EMPTY);
            }
            ui.begin(g, mouseX, mouseY);
            ItemStack[] hover = {null};
            paint(ui, width, height, menu, true, hover);
            if (hover[0] != null) g.setTooltipForNextFrame(font, hover[0], mouseX, mouseY);
            else ui.flushTooltip();
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean doubled) {
            if (e.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return true;
            for (int i = ui.hits.size() - 1; i >= 0; i--) {
                Ui.Hit hit = ui.hits.get(i);
                if (e.x() < hit.x() || e.y() < hit.y() || e.x() >= hit.x() + hit.w() || e.y() >= hit.y() + hit.h() || hit.click() == null) continue;
                hit.click().run();
                return true;
            }
            return true;
        }

        // from skyhanni CustomWardrobeKeybinds.handlePress: nth set on this page
        @Override
        public boolean keyPressed(KeyEvent e) {
            if (minecraft.options.keyInventory.matches(e)) {
                onClose();
                return true;
            }
            int index = WardrobeKeys.keyIndex(e);
            if (index >= 0 && index < PER_PAGE) {
                long now = System.currentTimeMillis();
                if (now - keyAt >= 200) {
                    keyAt = now;
                    List<Set> onPage = new ArrayList<>();
                    for (Set s : visible()) if (inPage(s)) onPage.add(s);
                    if (index < onPage.size()) clickSet(menu, onPage.get(index));
                }
                return true;
            }
            return super.keyPressed(e);
        }

        @Override
        public void onClose() {
            if (minecraft.player != null) minecraft.player.closeContainer();
            super.onClose();
        }
    }

    // from skyhanni CustomWardrobeEditScreen: hypixels chest to move armor around, way back next to it
    private static final class EditScreen extends ContainerScreen {
        EditScreen(ChestMenu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        public void removed() {
            if (!switching) super.removed();
        }

        @Override
        protected void init() {
            super.init();
            addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("§bEdit"), b -> {
                switching = true;
                minecraft.gui.setScreen(new WardrobeScreen(menu, title));
                switching = false;
            }).pos(leftPos + imageWidth + imageWidth / 20, topPos + (imageHeight - 20) / 2).size(50, 20).build());
        }
    }

    // --- pictures + tests ---

    public static Set set(int id) {
        return SETS[id - 1];
    }

    public static void preview(int page, int worn) {
        currentPage = page;
        currentSlot = worn;
        waiting = false;
    }

    // whole screen without the players
    public static void previewPaint(Ui ui, int w, int h) {
        paint(ui, w, h, null, false, new ItemStack[1]);
    }

    public static int currentSlot() {
        return currentSlot;
    }

    public static int currentPage() {
        return currentPage;
    }
}
