// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

//? if >=26.3 {
import com.mojang.blaze3d.platform.InputConstants;
//?}
import io.muzy.sbc.core.AuctionPrices;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.ItemInfo;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.mc.mixin.ContainerAccess;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
//? if <26.3 {
/*import org.lwjgl.glfw.GLFW;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// every sb item in a panel next to any inventory. search (spaces = all words, typos ok), filter bazaar /
// craftable / starred
// click = recipe, right click = what its used in, F = star
public final class ItemList {
    private static final Ui ui = new Ui();
    // World = npcs, mobs, places and warps (WorldIndex). All has them too while you search
    private static final String[] FILTERS = {"All", "Bazaar", "Craft", "World", "Stars"};
    private static final int[] FILTER_COLORS = {Modern.AQUA, Modern.WARN, Modern.GOOD, 0xFF55FF55, Modern.GOLD};
    private static final int COL = 20;

    private static String query = "";
    private static boolean focused;
    private static int filter, scroll;
    private static List<String> shown = List.of();
    private static String shownKey = "", shownHead = "";
    // how many items pass the search for each filter
    private static final int[] counts = new int[FILTERS.length];
    private static String hoverId;

    // the item under the mouse, for keys pressed over the list. a world entry isnt an item
    public static String hoveredId() {
        return WorldIndex.is(hoverId) ? null : hoverId;
    }
    private static int px, py, pw, ph, rows, cols;
    private static boolean placed;
    private ItemList() {}

    private static boolean active(Screen s) {
        return Features.on("itemlist") && s instanceof AbstractContainerScreen<?> && !(s instanceof io.muzy.sbc.ui.storage.StorageOverlayScreen) && !(s instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen);
    }

    private static boolean match(String id, String name, String[] words) {
        String hay = (name + " " + id).toLowerCase(Locale.ROOT);
        for (String w : words) if (!hay.contains(w)) return false;
        return true;
    }

    // loose match: query letters show up in order in the name
    private static boolean subsequence(String name, String q) {
        int i = 0;
        String n = name.toLowerCase(Locale.ROOT);
        for (int k = 0; k < n.length() && i < q.length(); k++) if (n.charAt(k) == q.charAt(i)) i++;
        return i == q.length();
    }

    private static void refresh() {
        String head = query + "|" + filter + "|" + Store.I.favourites.size();
        String key = head + "|" + ItemRepo.ids().size() + "|" + Prices.all().size() + "|" + WorldIndex.all().size();
        if (key.equals(shownKey)) return;
        shownKey = key;
        String q = query.toLowerCase(Locale.ROOT).trim();
        String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
        List<String> out = new ArrayList<>();
        List<String> loose = new ArrayList<>();
        java.util.Arrays.fill(counts, 0);
        for (String id : ItemRepo.ids()) {
            String name = io.muzy.sbc.core.GameState.strip(ItemRepo.name(id));
            boolean strict = match(id, name, words);
            if (!strict && !(q.length() >= 3 && subsequence(name, q.replace(" ", "")))) continue;
            boolean bazaar = ItemInfo.onBazaar(id), craft = ItemInfo.craftable(id), star = Store.I.favourites.contains(id);
            counts[0]++;
            if (bazaar) counts[1]++;
            if (craft) counts[2]++;
            if (star) counts[4]++;
            if (filter == 1 && !bazaar || filter == 2 && !craft || filter == 3 || filter == 4 && !star) continue;
            if (strict) out.add(id);
            else loose.add(id);
        }
        out.sort((a, b) -> {
            boolean fa = Store.I.favourites.contains(a), fb = Store.I.favourites.contains(b);
            if (fa != fb) return fa ? -1 : 1;
            return io.muzy.sbc.core.GameState.strip(ItemRepo.name(a)).compareToIgnoreCase(io.muzy.sbc.core.GameState.strip(ItemRepo.name(b)));
        });
        // the world: by name, kind, island or area ("baker", "npc hub"), all of it on the World tab
        List<String> world = new ArrayList<>();
        for (WorldIndex.Entry e : WorldIndex.all()) {
            if (!q.isEmpty() && !match(e.id(), e.name() + " " + e.kind() + " " + io.muzy.sbc.core.GameState.pretty(e.island()) + " " + e.area(), words)) continue;
            counts[3]++;
            if (!q.isEmpty()) counts[0]++;
            if (filter == 3 || filter == 0 && !q.isEmpty()) world.add(e.id());
        }
        world.sort(java.util.Comparator.comparingInt((String id) -> WorldIndex.order(WorldIndex.get(id).kind())).thenComparing(id -> WorldIndex.get(id).name(), String.CASE_INSENSITIVE_ORDER));
        out.addAll(world);
        out.addAll(loose);
        shown = out;
        // prices or the repo showing up late only update numbers, scroll stays
        if (!head.equals(shownHead)) scroll = 0;
        shownHead = head;
    }

    private static final String CLOSED_LABEL = "ITEM LIST";
    // HEAD = band + search + filter tiles above the grid, FOOT = 2 lines under it
    private static final int TAB_H = 16, ACCENT = Modern.AQUA, TILE_H = 22, HEAD = 18 + 18 + TILE_H + 4, FOOT = 26;

    // right of the chest, else left. -1 = no room either side
    private static int openX(int left, int cw, int sw) {
        int right = left + cw + 8, pw = 9 * COL + 16;
        if (right + pw <= sw - 2) return right;
        int x = left - 8 - pw;
        return x < 2 ? -1 : x;
    }

    // open panel takes the left side, the chest value card wants it too
    static boolean onLeftSide(int left, int cw, int sw) {
        int x = io.muzy.sbc.core.Config.I.itemListOpen ? openX(left, cw, sw) : -1;
        return x >= 0 && x < left;
    }

    // 'R'/'L' open, 'r'/'l' closed tab, 0 nowhere. for the other panels sharing the room
    public static char standing(int left, int cw, int sw) {
        if (!Features.on("itemlist")) return 0;
        if (io.muzy.sbc.core.Config.I.itemListOpen) {
            int x = openX(left, cw, sw);
            return x < 0 ? 0 : x < left ? 'L' : 'R';
        }
        return left + cw + 4 + Ui.MEASURE.width("› " + CLOSED_LABEL) + 10 <= sw - 2 ? 'r' : 'l';
    }

    private static void open() {
        io.muzy.sbc.core.Config.I.itemListOpen = true;
        io.muzy.sbc.core.Config.save();
    }

    private static void close() {
        io.muzy.sbc.core.Config.I.itemListOpen = false;
        io.muzy.sbc.core.Config.save();
        focused = false;
    }

    private static String number(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // 0.293 -> "29%", small ones keep a decimal "3.4%", a sliver is "<0.1%" never 0
    public static String percent(double share) {
        double p = share * 100;
        if (p > 0 && p < 0.1) return "<0.1%";
        return p == 0 || p >= 10 ? String.format(Locale.ROOT, "%.0f%%", p) : String.format(Locale.ROOT, "%.1f%%", p);
    }

    // white when it has no color
    public static int colorOf(String coloured) {
        if (coloured != null) {
            for (int i = coloured.indexOf('§'); i >= 0 && i + 1 < coloured.length(); i = coloured.indexOf('§', i + 2)) {
                net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(coloured.charAt(i + 1));
                var c = f == null ? null : net.minecraft.network.chat.TextColor.fromLegacyFormat(f);
                if (c != null) return readable(0xFF000000 | c.getValue());
            }
        }
        return 0xFFFFFFFF;
    }

    // hypixels blue and purple were made for a black tooltip, on glass they need a bit more light
    public static int readable(int rgb) {
        return switch (rgb) {
            case 0xFF5555FF -> 0xFF8088FF;
            case 0xFFAA00AA -> 0xFFD060E0;
            case 0xFF555555 -> Modern.DIM;
            default -> rgb;
        };
    }

    // first color in the name, the way hypixel sends it
    public static int colorOf(net.minecraft.network.chat.Component name) {
        for (net.minecraft.network.chat.Component part : name.toFlatList(net.minecraft.network.chat.Style.EMPTY)) {
            var c = part.getStyle().getColor();
            if (c != null) return readable(0xFF000000 | c.getValue());
        }
        return colorOf(name.getString());
    }

    // too long for the field = show the end
    public static void typing(Ui ui, String text, int x, int y, int maxW, boolean caret, int color) {
        String s = text;
        while (!s.isEmpty() && ui.width(s) > maxW - 3) s = s.substring(1);
        if (!s.isEmpty()) ui.textShadow(s, x, y, Modern.INK);
        if (caret && (ui.now / 500) % 2 == 0) ui.rect(x + ui.width(s) + 1, y - 1, 1, 9, color | 0xFF000000);
    }

    public static void searchBox(Ui ui, int x, int y, int w, int h, String text, boolean focused, String hint, int accent) {
        ui.rect(x, y, w, h, focused ? 0xE0182230 : 0xC00A0F17);
        ui.rect(x, y + h - 1, w, 1, focused ? accent | 0xFF000000 : 0x30FFFFFF);
        ui.icon(LegacyItems.stack("minecraft:spyglass"), x + 2, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
        int tx = x + Modern.ICON + 4, ty = y + (h - 8) / 2;
        if (text.isEmpty() && !focused) ui.textShadow(ui.ellipsize(hint, w - Modern.ICON - 8), tx, ty, Modern.DIM);
        else typing(ui, text, tx, ty, w - Modern.ICON - 8, focused, accent);
    }

    private static void closedTab(ContainerAccess a, Screen screen) {
        paintClosed(ui, a.sbc$left(), a.sbc$top(), a.sbc$width(), screen.width, screen.height, ContainerValue.hudBoxes(screen));
        placed = true;
    }

    static void paintClosed(Ui ui, int left, int top, int cw, int sw) {
        paintClosed(ui, left, top, cw, sw, Integer.MAX_VALUE, List.of());
    }

    // top of the window, or under whatever hud card would sit on it
    public static int tabY(int x, int w, int top, int sh, List<int[]> hud) {
        int y = Math.max(4, top);
        for (boolean moved = true; moved; ) {
            moved = false;
            for (int[] b : hud) {
                if (b[0] < x + w && x < b[0] + b[2] && b[1] < y + TAB_H && y < b[1] + b[3]) {
                    y = b[1] + b[3] + 2;
                    moved = true;
                }
            }
        }
        return y + TAB_H <= sh - 2 ? y : Math.max(4, top);
    }

    // left side when theres no room on the right
    static void paintClosed(Ui ui, int left, int top, int cw, int sw, int sh, List<int[]> hud) {
        Theme t = Theme.current();
        boolean book = false;
        String count = book || ItemRepo.ids().isEmpty() ? "" : number(ItemRepo.ids().size());
        int w = ui.width("› " + CLOSED_LABEL) + 10, right = left + cw + 4;
        px = right + w <= sw - 2 ? right : Math.max(2, left - 4 - w);
        py = tabY(px, w, top, sh, hud);
        pw = w;
        ph = TAB_H;
        boolean hv = ui.hover(px, py, pw, ph);
        t.panel(ui, px, py, pw, ph);
        if (hv) ui.rect(px + 1, py + 1, pw - 2, ph - 2, book ? 0x24FFFFFF : Ui.alpha(ACCENT, 0.2f));
        if (book) ui.rect(px + 1, py + ph - 2, pw - 2, 1, hv ? ACCENT : Ui.alpha(ACCENT, 0.45f));
        else {
            Modern.edge(ui, px, py, ph, ACCENT);
            ui.gradH(px + 2, py + ph - 2, pw - 3, 1, Ui.alpha(ACCENT, hv ? 1f : 0.7f), Ui.alpha(ACCENT, 0.08f));
        }
        ui.textShadow("›", px + 5, py + 4, ACCENT);
        ui.textShadow(CLOSED_LABEL, px + 5 + ui.width("› "), py + 4, hv ? 0xFFFFFFFF : Modern.LABEL);
        if (hv && !book) {
            if (count.isEmpty()) ui.tooltip("§bItem List", "§7Every SkyBlock item, with recipes and prices", "", "§eClick to open");
            else ui.tooltip("§bItem List", "§7Every SkyBlock item, with recipes and prices", "§f" + count + " §7items known", "", "§eClick to open");
        }
        ui.click(px, py, pw, ph, ItemList::open);
    }

    public static void draw(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        placed = false;
        hookRelease(screen);
        if (!active(screen)) return;
        ContainerAccess a = (ContainerAccess) screen;
        int sw = screen.width, sh = screen.height;
        ui.begin(g, mx, my);
        if (!io.muzy.sbc.core.Config.I.itemListOpen) {
            closedTab(a, screen);
            ui.flushTooltip();
            return;
        }
        pw = 9 * COL + 16;
        int x = openX(a.sbc$left(), a.sbc$width(), sw);
        if (x < 0) return;
        px = x;
        // level with the quick button rows, just tall enough for whole rows of icons
        py = Math.max(4, a.sbc$top() - 24);
        int room = Math.min(sh - py - 6, 270);
        if (room < 120) return;
        ph = HEAD + Math.max(1, (room - HEAD - 2 - FOOT) / COL) * COL + 2 + FOOT;
        refresh();
        paintOpen(ui, px, py, pw, ph);
        placed = true;
        ui.flushTooltip();
    }

    static void paintOpen(Ui ui, int x, int y, int w, int h) {
        px = x;
        py = y;
        pw = w;
        ph = h;
        paintModern(ui);
    }

    // for pictures
    static void preview(String q, boolean typing, int chosenFilter, int firstRow) {
        query = q;
        focused = typing;
        filter = chosenFilter;
        shownKey = "";
        refresh();
        scroll = firstRow;
    }

    private static void paintModern(Ui ui) {
        Theme.current().panel(ui, px, py, pw, ph);
        Modern.band(ui, "Item List", LegacyItems.stack("minecraft:knowledge_book"), "", ACCENT, px, py, pw);
        // arrow closes it again
        boolean closeHover = ui.hover(px + pw - 16, py + 1, 15, 12);
        if (closeHover) ui.rect(px + pw - 16, py + 1, 15, 12, Ui.alpha(ACCENT, 0.35f));
        ui.click(px + pw - 16, py + 1, 15, 12, ItemList::close);
        ui.textShadow("‹", px + pw - 10, py + 3, closeHover ? 0xFFFFFFFF : Modern.LABEL);
        int known = ItemRepo.ids().size(), n = shown.size();
        String count = filter == 3 || !query.isBlank() ? "§f" + number(n) + " §7results"
                : n == known ? "§f" + number(n) + " §7items" : Modern.fraction(number(n), number(known), 'f', '7') + " §b" + percent(known == 0 ? 0 : (double) n / known);
        ui.textShadow(count, px + pw - 18 - ui.width(count), py + 3, Modern.LABEL);

        int sx = px + 8, sy = py + 18, sw = pw - 16;
        searchBox(ui, sx, sy, sw, 14, query, focused, "Click to search...", ACCENT);
        ui.click(sx, sy, sw, 14, () -> focused = true);

        // filters are tiles that say how many items each would show
        int fy = sy + 18, tile = (sw - 2 * (FILTERS.length - 1)) / FILTERS.length;
        for (int i = 0; i < FILTERS.length; i++) {
            int idx = i, c = FILTER_COLORS[i], fx = sx + i * (tile + 2), fw = i == FILTERS.length - 1 ? sx + sw - fx : tile;
            boolean on = filter == i, over = ui.hover(fx, fy, fw, TILE_H);
            ui.rect(fx, fy, fw, TILE_H, on ? Ui.alpha(c, 0.26f) : over ? Ui.alpha(c, 0.14f) : 0x48000000);
            ui.rect(fx, fy + TILE_H - 1, fw, 1, on ? c : Ui.alpha(c, over ? 0.8f : 0.32f));
            String label = FILTERS[i], num = number(counts[i]);
            ui.textShadow(label, fx + (fw - ui.width(label)) / 2, fy + 2, on ? 0xFFFFFFFF : Modern.LABEL);
            ui.textShadow(num, fx + (fw - ui.width(num)) / 2, fy + 11, on || over ? Modern.lighten(c, 0.25f) : Modern.DIM);
            ui.click(fx, fy, fw, TILE_H, () -> {
                filter = idx;
                scroll = 0;
            });
        }

        cols = 9;
        int gy = py + HEAD, footY = py + ph - FOOT;
        rows = Math.max(1, (footY - 2 - gy) / COL);
        int total = (shown.size() + cols - 1) / cols;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - rows)));
        hoverId = null;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scroll + r) * cols + c, x = sx + c * COL, y = gy + r * COL;
                if (i >= shown.size()) {
                    ui.rect(x, y, COL - 1, COL - 1, 0x20000000);
                    continue;
                }
                String id = shown.get(i);
                boolean hv = ui.hover(x, y, COL - 1, COL - 1);
                ui.rect(x, y, COL - 1, COL - 1, hv ? Ui.alpha(ACCENT, 0.3f) : 0x50000000);
                WorldIndex.Entry place = WorldIndex.is(id) ? WorldIndex.get(id) : null;
                if (place != null) {
                    ui.rect(x, y + COL - 2, COL - 1, 1, hv ? ACCENT : Ui.alpha(WorldIndex.color(place.kind()), 0.75f));
                    ui.icon(WorldIndex.icon(place.kind()), x + 1, y + 1, 1f);
                    if (hv) {
                        hoverId = id;
                        ui.tooltip(WorldIndex.tooltip(place).toArray(new String[0]));
                    }
                    continue;
                }
                // bottom edge = rarity color, so a page of icons kinda sorts itself by eye
                int rarity = colorOf(ItemRepo.coloredName(id));
                ui.rect(x, y + COL - 2, COL - 1, 1, hv ? ACCENT : Ui.alpha(rarity, 0.75f));
                ui.icon(ItemInfo.stack(id), x + 1, y + 1, 1f);
                if (Store.I.favourites.contains(id)) ui.textShadow("★", x + COL - 8, y, Modern.GOLD);
                if (hv) {
                    hoverId = id;
                    // the item's real tooltip with the mod's lines, like over an inventory. outside SkyBlock the
                    // price lines are not added there, so the list adds its own
                    List<String> extra = new ArrayList<>();
                    if (!io.muzy.sbc.core.GameState.inSkyBlock) {
                        extra.add("");
                        extra.addAll(ItemInfo.priceLines(id));
                    }
                    extra.add("");
                    extra.add("§eClick: recipe  §7Right-click: used in  §7F: star");
                    ui.itemTooltip(ItemInfo.stack(id), extra);
                }
            }
        }
        if (shown.isEmpty()) {
            String none = query.isBlank() ? "Nothing here yet" : "Nothing matches";
            ui.textShadow(none, sx + (sw - ui.width(none)) / 2, gy + (rows * COL) / 2 - 8, Modern.LABEL);
            if (filter != 0 && counts[0] > 0) {
                String other = "§7" + number(counts[0]) + " in §bAll";
                ui.textShadow(other, sx + (sw - ui.width(other)) / 2, gy + (rows * COL) / 2 + 3, Modern.DIM);
            }
        }
        if (total > rows) {
            int barH = rows * COL - 1, th = Math.max(10, barH * rows / total);
            int ty = gy + (barH - th) * scroll / Math.max(1, total - rows);
            ui.rect(px + pw - 6, gy, 2, barH, 0x50000000);
            ui.rect(px + pw - 6, ty, 2, th, ACCENT);
        }

        Modern.rule(ui, px + Modern.PAD, footY, pw - 2 * Modern.PAD);
        WorldIndex.Entry hoverPlace = WorldIndex.is(hoverId) ? WorldIndex.get(hoverId) : null;
        if (hoverPlace != null) {
            ui.textShadow(ui.ellipsize(hoverPlace.name(), sw), sx, footY + 3, WorldIndex.color(hoverPlace.kind()));
            ui.textShadow(ui.ellipsize(WorldIndex.where(hoverPlace), sw), sx, footY + 13, Modern.DIM);
        } else if (hoverId != null) {
            String marks = (ItemInfo.craftable(hoverId) ? "§aRecipe" : "") + (Store.I.favourites.contains(hoverId) ? " §6★" : "");
            int mw = ui.width(marks);
            ui.textShadow(ui.ellipsize(io.muzy.sbc.core.GameState.strip(ItemRepo.name(hoverId)), sw - (mw > 0 ? mw + 4 : 0)), sx, footY + 3, colorOf(ItemRepo.coloredName(hoverId)));
            if (mw > 0) ui.textShadow(marks, sx + sw - mw, footY + 3, Modern.LABEL);
            ui.textShadow(ui.ellipsize(prices(hoverId), sw), sx, footY + 13, Modern.DIM);
        } else if (filter == 3) {
            ui.textShadow("§eClick§7 details  §eRight§7 guide me there", sx, footY + 3, Modern.LABEL);
            ui.textShadow("§7NPCs, bosses, mobs, places, warps", sx, footY + 13, Modern.LABEL);
        } else {
            ui.textShadow("§eClick§7 recipe  §eRight§7 used in  §eF§7 star", sx, footY + 3, Modern.LABEL);
            ui.textShadow("§7Over any item: §eR§7 recipe  §eU§7 used in", sx, footY + 13, Modern.LABEL);
        }
    }

    // everything we know about the price, one line
    private static String prices(String id) {
        StringBuilder out = new StringBuilder();
        Prices.Product p = Prices.get(id);
        if (p != null) out.append("§7Buy §6").append(Prices.coins(p.buy())).append("  §7Sell §e").append(Prices.coins(p.sell()));
        double bin = AuctionPrices.lowestBin(id), npc = ItemRepo.npcPrice(id);
        if (bin > 0) out.append(out.isEmpty() ? "" : "  ").append("§7BIN §6").append(Prices.coins(bin));
        if (npc > 0) out.append(out.isEmpty() ? "" : "  ").append("§7NPC §e").append(Prices.coins(npc));
        return out.isEmpty() ? "§7No price known" : out.toString();
    }

    private static boolean inside(double x, double y) {
        return placed && x >= px && y >= py && x < px + pw && y < py + ph;
    }

    // we took a press here. the game would throw the held item on release (counts as a click outside), so remember it
    private static boolean tookPress;
    private static boolean releaseHooked;

    // call this from anything else that takes a press on its own panel
    public static void pressTaken() {
        tookPress = true;
    }

    private static boolean release() {
        boolean allow = !tookPress;
        tookPress = false;
        return allow;
    }

    // once we took a press its release is ours too, a drag that started on a slot keeps its own
    private static void hookRelease(Screen first) {
        if (releaseHooked) return;
        releaseHooked = true;
        Safe.allowMouseRelease("ItemList.allowMouseRelease", first, (s, e) -> release());
        Safe.afterInit("ItemList.afterInit", (client, s, w, h) -> {
            if (s instanceof AbstractContainerScreen<?>) Safe.allowMouseRelease("ItemList.allowMouseRelease#2", s, (sc, e) -> release());
        });
    }

    // false = swallow the click
    public static boolean click(Screen screen, double x, double y, int button) {
        boolean allow = press(screen, x, y, button);
        tookPress = !allow;
        return allow;
    }

    private static boolean press(Screen screen, double x, double y, int button) {
        // panels next to the window arent "outside", clicking them must not throw your held item
        if (ContainerValue.over(x, y) || io.muzy.sbc.item.InventorySearch.over(x, y)) {
            focused = false;
            return false;
        }
        if (!active(screen) || !placed) return true;
        if (!inside(x, y)) {
            focused = false;
            return true;
        }
        if (hoverId != null && ui.hover((int) x, (int) y, 1, 1) && button == 1) {
            if (WorldIndex.is(hoverId)) WorldIndex.go(WorldIndex.get(hoverId));
            else ItemScreen.open(hoverId, true);
            return false;
        }
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (x < hit.x() || y < hit.y() || x >= hit.x() + hit.w() || y >= hit.y() + hit.h() || hit.click() == null) continue;
            if (hoverId == null || button == 0) hit.click().run();
            return false;
        }
        if (hoverId != null && button == 0) {
            if (WorldIndex.is(hoverId)) WorldScreen.open(WorldIndex.get(hoverId));
            else ItemScreen.open(hoverId);
        }
        return false;
    }

    public static boolean scroll(Screen screen, double x, double y, double dy) {
        if (!active(screen) || !inside(x, y)) return true;
        scroll -= (int) Math.signum(dy) * (1);
        return false;
    }

    public static boolean typed(Screen screen, int codepoint) {
        if (!active(screen) || !focused) return true;
        if (codepoint >= 32 && codepoint != 127 && query.length() < 40) {
            query += new String(Character.toChars(codepoint));
        }
        return false;
    }

    // false = swallow the key
    public static boolean key(Screen screen, int key, boolean shift) {
        if (!active(screen)) return true;
        if (focused) {
            //? if >=26.3 {
            if (key == InputConstants.KEY_BACKSPACE) {
            //?} else {
            /*if (key == GLFW.GLFW_KEY_BACKSPACE) {
            *///?}
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
            //? if >=26.3 {
            } else if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN) focused = false;
            //?} else {
            /*} else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) focused = false;
            *///?}
            return false;
        }
        String id = hoverId;
        if (id == null) {
            var hovered = ((ContainerAccess) screen).sbc$hovered();
            //? if >=26.3 {
            if (hovered != null && !hovered.getItem().isEmpty() && (key == InputConstants.KEY_R || key == InputConstants.KEY_U)) {
            //?} else {
            /*if (hovered != null && !hovered.getItem().isEmpty() && (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U)) {
            *///?}
                ItemStack st = hovered.getItem();
                String sb = Gain.sbId(st);
                if (!sb.isEmpty() && ItemRepo.get(sb) != null) {
                    //? if >=26.3 {
                    ItemScreen.open(sb, key == InputConstants.KEY_U);
                    //?} else {
                    /*ItemScreen.open(sb, key == GLFW.GLFW_KEY_U);
                    *///?}
                    return false;
                }
            }
            return true;
        }
        // stars and recipes are for items
        if (WorldIndex.is(id)) return true;
        //? if >=26.3 {
        if (key == InputConstants.KEY_F) {
        //?} else {
        /*if (key == GLFW.GLFW_KEY_F) {
        *///?}
            if (!Store.I.favourites.remove(id)) Store.I.favourites.add(id);
            Store.markDirty();
            return false;
        }
        //? if >=26.3 {
        if (key == InputConstants.KEY_R || key == InputConstants.KEY_U) {
            ItemScreen.open(id, key == InputConstants.KEY_U);
        //?} else {
        /*if (key == GLFW.GLFW_KEY_R || key == GLFW.GLFW_KEY_U) {
            ItemScreen.open(id, key == GLFW.GLFW_KEY_U);
        *///?}
            return false;
        }
        return true;
    }
}
