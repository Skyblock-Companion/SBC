// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.storage;

import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.RowText;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

// classic storage look: glass frame + search row, every page a card in its own color, inventory, overview,
// shortcuts under it. just drawing + layout math
final class StorageLook {
    static final int ENDER = 0xFFB98CFF, BACKPACK = 0xFFFFB454, FRAME = Modern.AQUA, INVENTORY = 0xFF6FE3B2, OVERVIEW = Modern.GOLD, SHORTCUTS = 0xFF8FB4E8;
    // search row + the gap under it = what it takes off the top of the grid
    static final int SEARCH_ROW = 16, SEARCH_BLOCK = 18, SEARCH_TEXT_X = 16, SEARCH_TEXT_Y = 4;
    // slots start this far below the top of a page card / inventory card
    static final int HEAD = 18;
    static final int GAP = 3, BOTTOM_H = 100, INV_W = 175, MIN_ALIGNED = 96, MIN_SIDE = 64, MAX_SIDE = 236, MAX_UNALIGNED = 184, NARROW = 150;

    static final ItemStack FRAME_ICON = LegacyItems.stack("minecraft:chest"), ENDER_ICON = LegacyItems.stack("minecraft:ender_chest"), BACKPACK_ICON = LegacyItems.stack("minecraft:bundle"),
            INVENTORY_ICON = LegacyItems.stack("minecraft:player_head"), OVERVIEW_ICON = LegacyItems.stack("minecraft:map"), SHORTCUTS_ICON = LegacyItems.stack("minecraft:comparator"),
            SEARCH_ICON = LegacyItems.stack("minecraft:spyglass"), FARMING_ICON = LegacyItems.stack("minecraft:golden_hoe"), HUNTING_ICON = LegacyItems.stack("minecraft:lead"),
            HOME_ICON = LegacyItems.stack("minecraft:oak_door"), HIDE_ICON = LegacyItems.stack("minecraft:ender_eye");

    // shortcut buttons top to bottom, the screen gives each its action
    static final List<Shortcut> SHORTCUT_LIST = List.of(
            new Shortcut("Farming Toolkit", "Farming", "/farmingtoolkit", FARMING_ICON, Modern.GOOD),
            new Shortcut("Hunting Toolkit", "Hunting", "/huntingtoolkit", HUNTING_ICON, Modern.WARN),
            new Shortcut("Home", "Home", "Hypixel's menu", HOME_ICON, Modern.AQUA),
            new Shortcut("Hide", "Hide", "plain menu", HIDE_ICON, Modern.LABEL));

    private static final float FRAME_GLASS = 0.8f, CARD_GLASS = 0.6f;
    private static final int WELL = 0x8C060A11, CARD_LIGHT = 0x0CFFFFFF, SLOT = 0x80050810, SLOT_TOP = 0x50000000, SLOT_LIT = 0x1CFFFFFF, MISSING = 0x38000000, DIMMED = 0xC40A0E14, DARK_INK = 0xFF0C1016;
    // where band + button text starts, with an icon and without
    private static final int BAND_ICON_TEXT = 2 + Modern.ICON + 2, BUTTON_ICON_TEXT = 5 + Modern.ICON + 3, BUTTON_TEXT = 5;

    private StorageLook() {}

    // where everything goes at this size. contentH = height the old layout gave the frame
    record Layout(int frameX, int frameY, int frameW, int frameH, int invX, int invY, int sideW) {
        boolean side() {
            return sideW >= MIN_SIDE;
        }

        int overviewX() {
            return invX - GAP - sideW;
        }

        int shortcutsX() {
            return invX + INV_W + GAP;
        }

        // inventory card is 1px narrower than its slot box, so a card lined up with the frame takes that px
        int shortcutsW() {
            return overviewX() == frameX ? frameX + frameW - shortcutsX() : sideW;
        }

        boolean inFrame(double x, double y) {
            return x >= frameX && x < frameX + frameW && y >= frameY && y < frameY + frameH;
        }

        // the whole row under the frame, gaps between cards too. a click there must not drop whats on the cursor
        boolean inSide(double x, double y) {
            return side() && y >= invY && y < invY + BOTTOM_H && x >= overviewX() && x < shortcutsX() + shortcutsW();
        }
    }

    static Layout layout(int screenW, int screenH, int frameW, int contentH) {
        int top = screenH / 10, frameY = top - Modern.BAND - 2, invX = (screenW - 176) / 2, frameX = (screenW - frameW) / 2;
        // first inventory slot is always contentH + 5 below the top, see StorageOverlayMenu
        int invY = top + contentH + 5 - HEAD;
        // side cards line up with the frame. frame too narrow = they get whatever room the screen has on the side
        int aligned = invX - GAP - frameX;
        int sideW = aligned >= MIN_ALIGNED ? Math.min(MAX_SIDE, aligned) : Math.min(MAX_UNALIGNED, invX - GAP - 4);
        return new Layout(frameX, frameY, frameW, invY - GAP - frameY, invX, invY, sideW);
    }

    static int gridHeight(int contentH) {
        return contentH - SEARCH_BLOCK;
    }

    // --- numbers ---

    // whole percent that never says empty or full too early
    static int percent(int part, int whole) {
        if (whole <= 0) return 0;
        if (part >= whole) return 100;
        if (part <= 0) return 0;
        return Math.max(1, Math.min(99, Math.round(part * 100f / whole)));
    }

    static String percentCode(int percent) {
        return percent >= 100 ? "§c" : percent >= 90 ? "§6" : percent >= 60 ? "§e" : "§a";
    }

    // thousands separators. almost everything here is small and this runs every frame
    static String number(long n) {
        return n >= 0 && n < 1000 ? Long.toString(n) : String.format(Locale.ROOT, "%,d", n);
    }

    // "12 §7stacks" in whatever color is set before it
    private static String many(long n, String what) {
        return number(n) + " §7" + what + (n == 1 ? "" : "s");
    }

    private static String fraction(long part, long whole) {
        return Modern.fraction(number(part), number(whole), 'f', '7');
    }

    private static String share(int part, int whole) {
        int p = percent(part, whole);
        return percentCode(p) + p + "%";
    }

    // all pages together. never opened ones have unknown contents so they only count as pages
    static final class Totals {
        int enderPages, enderSeen, enderFilled, enderSlots, backpackPages, backpackSeen, backpackFilled, backpackSlots, unseen, foundStacks, foundPages;
        long items, foundItems;

        void add(boolean ender, boolean seen, int filled, int slots, long stackedItems, int found, long foundStackedItems) {
            if (ender) enderPages++;
            else backpackPages++;
            if (!seen) {
                unseen++;
                return;
            }
            if (ender) {
                enderSeen++;
                enderFilled += filled;
                enderSlots += slots;
            } else {
                backpackSeen++;
                backpackFilled += filled;
                backpackSlots += slots;
            }
            items += stackedItems;
            foundStacks += found;
            foundItems += foundStackedItems;
            if (found > 0) foundPages++;
        }

        int pages() {
            return enderPages + backpackPages;
        }

        int filled() {
            return enderFilled + backpackFilled;
        }

        int slots() {
            return enderSlots + backpackSlots;
        }

        // what the search found, for the end of the search row
        String results(boolean searching) {
            if (!searching) return "";
            if (foundStacks == 0) return "§cNo matches";
            return "§a" + many(foundStacks, "stack") + "  §f" + many(foundItems, "item") + "  §f" + many(foundPages, "page");
        }
    }

    // card on the frame, a bit lighter than whats under it so it floats
    private static void card(Ui ui, int x, int y, int w, int h) {
        Modern.card(ui, x, y, w, h, CARD_GLASS);
        ui.rect(x + 1, y + 1, w - 2, h - 2, CARD_LIGHT);
    }

    // --- the frame ---

    // openName = the open pages menu, null while on the storage menu itself
    static void frame(Ui ui, Layout l, String openName, boolean anyPages) {
        Modern.card(ui, l.frameX(), l.frameY(), l.frameW(), l.frameH(), FRAME_GLASS);
        int room = l.frameW() - BAND_ICON_TEXT - ui.width("Storage") - 8 - Modern.PAD;
        String status = openName != null ? "§7Open: §f" + openName : anyPages ? "§7Click a page to open it" : "";
        if (ui.width(status) > room) status = openName != null ? "§f" + ui.ellipsize(openName, room) : "";
        Modern.band(ui, "Storage", FRAME_ICON, status, FRAME, l.frameX(), l.frameY(), l.frameW());
    }

    static void searchRow(Ui ui, int x, int y, int w, boolean focused, boolean hovered, String results) {
        ui.rect(x, y, w, SEARCH_ROW, WELL);
        ui.rect(x, y, w, 1, SLOT_TOP);
        ui.rect(x, y + SEARCH_ROW - 1, w, 1, focused ? FRAME : Ui.alpha(FRAME, hovered ? 0.6f : 0.25f));
        ui.icon(SEARCH_ICON, x + 3, y + 3, Modern.ICON_SCALE);
        if (!results.isEmpty()) {
            String shown = ui.ellipsize(results, resultsRoom(w) - 4);
            ui.textShadow(shown, x + w - 4 - ui.width(shown), y + SEARCH_TEXT_Y, Modern.INK);
        }
    }

    // end of the search row kept free of typed text for the results
    static int resultsRoom(int w) {
        return Math.min(190, w / 3);
    }

    static void scrollbar(Ui ui, int x, int y, int h, int thumbY, int thumbH, boolean hovered) {
        ui.rect(x + 2, y, 2, h, WELL);
        ui.rect(x + 1, thumbY, 4, thumbH, hovered ? Modern.lighten(FRAME | 0xFF000000, 0.25f) : Ui.alpha(FRAME | 0xFF000000, 0.7f));
        ui.rect(x + 1, thumbY, 4, 1, Modern.lighten(FRAME | 0xFF000000, 0.5f));
    }

    // in the empty list when a search found nothing and no page is open
    static void noMatches(Ui ui, int x, int y, int w, int h, Totals t) {
        int cx = x + w / 2, ty = y + h / 2 - (t.unseen > 0 ? 15 : 10);
        centred(ui, "§cNo matches", cx, ty, w);
        centred(ui, "§7Searched §f" + many(t.pages() - t.unseen, "page"), cx, ty + Modern.ROW, w);
        if (t.unseen > 0) centred(ui, "§e" + many(t.unseen, "page") + " never opened, not known", cx, ty + 2 * Modern.ROW, w);
    }

    private static void centred(Ui ui, String s, int cx, int y, int w) {
        s = ui.ellipsize(s, w - 8);
        ui.textShadow(s, cx - ui.width(s) / 2, y, Modern.INK);
    }

    // ends the list. text the scissor hid still counts as drawn, and stuff after the list isnt cut by that
    // scissor, so a hint scrolled out under the grid would erase a shortcut name
    static void endGrid(Ui ui) {
        ui.resetText();
    }

    // --- a page ---

    // what a page card says. found = stacks the search found here, 0 = none or no search
    record Page(String title, String shortTitle, ItemStack icon, int accent, int filled, int total, boolean seen, boolean open, int found) {
        double fill() {
            return total <= 0 ? 0 : (double) filled / total;
        }

        // ender chest page never opened: the cache only has a stand in so the size is unknown. backpacks get it from lore
        boolean placeholder() {
            return !seen && accent == ENDER;
        }
    }

    // card with band + meter under the slots. the widgets own box stays clear around it as the gap to its neighbours
    static void page(Ui ui, int x, int y, int w, int h, Page p) {
        int cx = x + 1, cw = w - 3, ch = h - 2, c = p.accent() | 0xFF000000;
        card(ui, cx, y, cw, ch);
        if (p.open()) {
            ui.rect(cx + 1, y + 1, cw - 2, ch - 2, Ui.alpha(c, 0.08f));
            ui.gradH(cx + 1, y + 1, cw - 2, Modern.BAND - 2, Ui.alpha(c, 0.34f), Ui.alpha(c, 0.04f));
        }
        band(ui, p, c, cx, y, cw);
        Modern.meter(ui, cx + 1, y + Modern.BAND, cw - 2, Modern.METER, p.seen() ? p.fill() : 0, p.seen() && p.filled() >= p.total() ? Modern.BAD : c);
        if (p.open()) {
            outline(ui, cx - 1, y - 1, cw + 2, ch + 2, Ui.alpha(c, 0.3f));
            outline(ui, cx, y, cw, ch, c);
        }
    }

    // over a never opened page, on top of its slots
    static void pageHint(Ui ui, int x, int y, int w, int h) {
        int cx = x + 1, cw = w - 3, ch = h - 2;
        if (ch <= HEAD + 12 || cw < 44) return;
        String hint = ui.width("Click to load this page") <= cw - 8 ? "Click to load this page" : "Click to load";
        ui.textCenter(ui.ellipsize(hint, cw - 8), cx + cw / 2, y + HEAD + (ch - HEAD - 8) / 2 - 3, Modern.LABEL);
    }

    // one way to fill a band. fraction + share go right, any text can be empty
    record BandFit(String title, boolean icon, boolean openChip, String found, String fraction, String share) {
        // how far it reaches from the bands left edge
        int width(Ui ui) {
            int chips = (openChip ? tagWidth(ui, "OPEN") + 2 : 0) + (found.isEmpty() ? 0 : tagWidth(ui, found) + 2);
            int after = (icon ? BAND_ICON_TEXT : Modern.PAD) + ui.width(title) + 2 + chips;
            int count = (fraction.isEmpty() ? 0 : ui.width(fraction) + 3) + ui.width(share);
            return count > 0 ? after + 1 + count : after - 2;
        }
    }

    // title: 0 long, 1 short, 2 just the number. found: 0 "3 found", 1 "3", 2 nothing. count: 0 "a/b p%", 1 "p%", 2 nothing
    private record BandStep(int title, boolean icon, int found, int count, boolean openChip) {}

    // everything down to the least, first that fits gets drawn. numbers outlast icons + words, the outline still
    // says open without its chip
    private static final BandStep[] BAND_STEPS = {
            new BandStep(0, true, 0, 0, true),
            new BandStep(1, true, 0, 0, true),
            new BandStep(1, false, 0, 0, true),
            new BandStep(1, true, 1, 0, true),
            new BandStep(1, false, 1, 0, true),
            new BandStep(1, true, 1, 1, true),
            new BandStep(1, false, 1, 1, true),
            new BandStep(1, true, 2, 1, true),
            new BandStep(1, false, 2, 1, true),
            new BandStep(1, true, 1, 1, false),
            new BandStep(1, false, 1, 1, false),
            new BandStep(1, true, 2, 1, false),
            new BandStep(1, false, 2, 1, false),
            new BandStep(2, true, 2, 1, false),
            new BandStep(2, false, 2, 1, false),
            new BandStep(2, true, 2, 2, false),
            new BandStep(2, false, 2, 2, false),
    };

    // as much as fits a band this wide, the tooltip always has all of it
    static BandFit bandFit(Ui ui, Page p, int w) {
        int room = w - Modern.PAD;
        String number = p.shortTitle().substring(p.shortTitle().lastIndexOf(' ') + 1);
        String fraction = p.seen() ? fraction(p.filled(), p.total()) : "", share = p.seen() ? share(p.filled(), p.total()) : "§7not opened", least = p.seen() ? share : "§7-";
        String longTag = p.found() > 0 ? p.found() + " found" : "", shortTag = p.found() > 0 ? Integer.toString(p.found()) : "";
        BandFit fit = null;
        for (BandStep s : BAND_STEPS) {
            String title = s.title() == 0 ? p.title() : s.title() == 1 ? p.shortTitle() : number;
            String tag = s.found() == 0 ? longTag : s.found() == 1 ? shortTag : "";
            fit = new BandFit(title, s.icon(), s.openChip() && p.open(), tag, s.count() == 0 ? fraction : "", s.count() == 0 ? share : s.count() == 1 ? least : "");
            if (fit.width(ui) <= room) return fit;
        }
        return fit;
    }

    private static void band(Ui ui, Page p, int c, int x, int y, int w) {
        BandFit f = bandFit(ui, p, w);
        Modern.band(ui, f.title(), f.icon() ? p.icon() : ItemStack.EMPTY, "", c, x, y, w);
        int after = x + (f.icon() ? BAND_ICON_TEXT : Modern.PAD) + ui.width(f.title()) + 2, ty = y + 3;
        if (f.openChip()) {
            openTag(ui, after, ty, c);
            after += tagWidth(ui, "OPEN") + 2;
        }
        if (!f.found().isEmpty()) tag(ui, after, ty, f.found(), Modern.GOOD);
        count(ui, x + w - Modern.PAD, ty, f.fraction(), f.share());
    }

    // "12/45 27%" ending at right
    private static void count(Ui ui, int right, int y, String fraction, String share) {
        int sw = ui.width(share);
        if (sw > 0) ui.textShadow(share, right - sw, y, Modern.INK);
        if (!fraction.isEmpty()) ui.textShadow(fraction, right - sw - 3 - ui.width(fraction), y, Modern.INK);
    }

    private static int tagWidth(Ui ui, String text) {
        return ui.width(text) + 3;
    }

    private static void tag(Ui ui, int x, int y, String text, int color) {
        int c = color | 0xFF000000;
        ui.rect(x, y - 1, tagWidth(ui, text), 10, Ui.alpha(c, 0.24f));
        ui.rect(x, y + 8, tagWidth(ui, text), 1, Ui.alpha(c, 0.85f));
        ui.textShadow(text, x + 2, y, Modern.lighten(c, 0.35f));
    }

    // the one state that matters most here: lit in the page color, dark letters on it
    private static void openTag(Ui ui, int x, int y, int c) {
        ui.rect(x, y - 1, tagWidth(ui, "OPEN"), 10, c);
        ui.rect(x, y - 1, tagWidth(ui, "OPEN"), 1, Modern.lighten(c, 0.45f));
        ui.text("OPEN", x + 2, y, DARK_INK);
    }

    private static void outline(Ui ui, int x, int y, int w, int h, int c) {
        ui.rect(x, y, w, 1, c);
        ui.rect(x, y + h - 1, w, 1, c);
        ui.rect(x, y + 1, 1, h - 2, c);
        ui.rect(x + w - 1, y + 1, 1, h - 2, c);
    }

    // hover over a clickable page: more light + brighter band. the double outline stays for the open page only
    static void pageHover(Ui ui, int x, int y, int w, int h, int accent) {
        int cx = x + 1, cw = w - 3, ch = h - 2, c = accent | 0xFF000000;
        ui.rect(cx + 1, y + 1, cw - 2, ch - 2, 0x0DFFFFFF);
        ui.gradH(cx + 1, y + 1, cw - 2, Modern.BAND - 2, Ui.alpha(c, 0.25f), Ui.alpha(c, 0.02f));
        outline(ui, cx, y, cw, ch, 0x40FFFFFF);
    }

    // everything we know about a page, for its band tooltip
    static String[] pageTooltip(Page p, String realName, String nameCode, long items, long foundItems) {
        String name = nameCode + (realName == null || realName.isEmpty() ? p.title() : realName);
        String unknown = "§7Not opened yet, its items are not known.";
        if (p.placeholder()) return new String[]{name, unknown, "", "§eClick to load it"};
        if (!p.seen()) return new String[]{name, unknown, "§7Slots: §f" + p.total(), "", "§eClick to load it"};
        int free = p.total() - p.filled();
        String used = "§7Used: " + fraction(p.filled(), p.total()) + " §7(" + percentCode(percent(p.filled(), p.total())) + RowText.percent(p.fill()) + "§7)";
        String last = p.open() ? "§bThis page is open" : "§eClick to open";
        if (p.found() > 0) return new String[]{name, used, "§7Free: §f" + free, "§7Items: §f" + number(items), "§7Found here: §a" + many(p.found(), "stack") + "§7, §a" + many(foundItems, "item"), "", last};
        return new String[]{name, used, "§7Free: §f" + free, "§7Items: §f" + number(items), "", last};
    }

    // --- slots ---

    // soft inset under an item, itemX/itemY = where the item goes
    static void slot(Ui ui, int itemX, int itemY) {
        ui.rect(itemX - 1, itemY - 1, 17, 17, SLOT);
        ui.rect(itemX - 1, itemY - 1, 17, 1, SLOT_TOP);
        ui.rect(itemX - 1, itemY + 15, 17, 1, SLOT_LIT);
    }

    // a grid spot the page doesnt really have
    static void missingSlot(Ui ui, int itemX, int itemY) {
        ui.rect(itemX - 1, itemY - 1, 17, 17, MISSING);
    }

    // not found by the search = sinks back
    static void dim(Ui ui, int itemX, int itemY) {
        ui.rect(itemX, itemY, 16, 16, DIMMED);
    }

    // found by the search = stands out
    static void found(Ui ui, int itemX, int itemY) {
        outline(ui, itemX - 1, itemY - 1, 17, 17, Ui.alpha(Modern.GOOD | 0xFF000000, 0.85f));
    }

    static void slotHover(Ui ui, int itemX, int itemY) {
        ui.rect(itemX, itemY, 16, 16, 0x38FFFFFF);
    }

    // --- under the frame ---

    // inventory band + meter, hairline splits off the hotbar
    static void inventory(Ui ui, Layout l, String title, int filled, int total) {
        int x = l.invX(), y = l.invY(), c = INVENTORY | 0xFF000000;
        String fraction = fraction(filled, total), share = share(filled, total);
        int counted = ui.width(fraction) + 3 + ui.width(share);
        card(ui, x, y, INV_W, BOTTOM_H);
        Modern.band(ui, ui.ellipsize(title, INV_W - BAND_ICON_TEXT - Modern.PAD - counted - 3), INVENTORY_ICON, "", c, x, y, INV_W);
        count(ui, x + INV_W - Modern.PAD, y + 3, fraction, share);
        Modern.meter(ui, x + 1, y + Modern.BAND, INV_W - 2, Modern.METER, total <= 0 ? 0 : (double) filled / total, filled >= total ? Modern.BAD : c);
        Modern.rule(ui, x + 6, y + HEAD + 55, INV_W - 12);
    }

    // all pages at a glance: how full, room left, how many still unknown. dash = nothing known yet
    static void overview(Ui ui, Layout l, Totals t) {
        int x = l.overviewX(), y = l.invY(), w = l.sideW(), c = OVERVIEW | 0xFF000000;
        boolean narrow = w < NARROW, known = t.slots() > 0;
        card(ui, x, y, w, BOTTOM_H);
        Modern.band(ui, narrow ? "Overview" : "Storage Overview", OVERVIEW_ICON, narrow || t.pages() == 0 ? "" : "§f" + many(t.pages(), "page"), c, x, y, w);
        int margin = narrow ? Modern.PAD : Modern.PAD + 1, rx = x + margin, rw = w - 2 * margin, ry = y + Modern.BAND + 4;
        ry = meterRow(ui, "Ender Chests", "EC", scope(t.enderSeen, t.enderPages), t.enderFilled, t.enderSlots, ENDER, rx, ry, rw);
        ry = meterRow(ui, "Backpacks", "BP", scope(t.backpackSeen, t.backpackPages), t.backpackFilled, t.backpackSlots, BACKPACK, rx, ry, rw);
        ry = meterRow(ui, "All Slots", "All", "", t.filled(), t.slots(), Modern.AQUA, rx, ry, rw);
        Modern.rule(ui, rx, ry, rw);
        ry += 3;
        // these 2 only count what was seen, say so while some pages are left out
        String note = t.unseen > 0 && !narrow ? " §7(seen)" : "";
        int free = t.slots() - t.filled();
        cells(ui, (narrow ? "Free" : "Free Slots") + note, known ? freeCode(free, t.slots()) + number(free) : "§7-", rx, ry, rw);
        ry += Modern.ROW;
        cells(ui, (narrow ? "Items" : "Items Stored") + note, known ? "§f" + number(t.items) : "§7-", rx, ry, rw);
        ry += Modern.ROW;
        String seenLabel = narrow ? "Seen" : "Pages Seen";
        cells(ui, seenLabel, seenValue(ui, t, rw - ui.width(seenLabel) - 4), rx, ry, rw);
    }

    private static String scope(int seen, int pages) {
        if (pages <= 0) return "";
        return " §7(" + (seen == pages ? Integer.toString(pages) : seen + "/" + pages) + ")";
    }

    private static String freeCode(int free, int slots) {
        return free <= 0 ? "§c" : free * 10 < slots ? "§e" : "§a";
    }

    private static String seenValue(Ui ui, Totals t, int room) {
        if (t.pages() <= 0) return "§7-";
        int seen = t.pages() - t.unseen;
        String share = (t.unseen > 0 ? "§e" : "§a") + percent(seen, t.pages()) + "%";
        String both = (t.unseen > 0 ? "§e" : "§f") + seen + "§7/§7" + t.pages() + " " + share;
        return ui.width(both) <= room ? both : share;
    }

    // "a/b p%", no separators when only that fits, else just the percent over its meter
    private static String meterValue(Ui ui, int part, int whole, int room) {
        if (whole <= 0) return "§7-";
        String share = share(part, whole), full = fraction(part, whole) + " " + share;
        if (ui.width(full) <= room) return full;
        String bare = Modern.fraction(Integer.toString(part), Integer.toString(whole), 'f', '7') + " " + share;
        return ui.width(bare) <= room ? bare : share;
    }

    // label left, value right. value always stays whole, label gets the rest
    private static void cells(Ui ui, String label, String value, int x, int y, int w) {
        int vw = ui.width(value);
        ui.textShadow(ui.ellipsize(label, w - vw - 4), x, y, Modern.LABEL);
        ui.textShadow(value, x + w - vw, y, Modern.INK);
    }

    private static int meterRow(Ui ui, String label, String shortLabel, String scope, int filled, int slots, int accent, int x, int y, int w) {
        String value = meterValue(ui, filled, slots, w - ui.width(shortLabel) - 4);
        int vw = ui.width(value);
        String left = label + scope;
        if (ui.width(left) + 6 + vw > w) left = shortLabel + scope;
        if (ui.width(left) + 4 + vw > w) left = shortLabel;
        cells(ui, left, value, x, y, w);
        Modern.meter(ui, x, y + 9, w, Modern.METER, slots <= 0 ? 0 : (double) filled / slots, filled >= slots && slots > 0 ? Modern.BAD : accent);
        return y + 9 + Modern.METER + 3;
    }

    static void shortcuts(Ui ui, Layout l) {
        int w = l.shortcutsW();
        card(ui, l.shortcutsX(), l.invY(), w, BOTTOM_H);
        boolean icon = BAND_ICON_TEXT + ui.width("Shortcuts") <= w - Modern.PAD;
        Modern.band(ui, "Shortcuts", icon ? SHORTCUTS_ICON : ItemStack.EMPTY, "", SHORTCUTS, l.shortcutsX(), l.invY(), w);
    }

    // where the ith shortcut goes in its card
    static int shortcutY(Layout l, int i) {
        return l.invY() + 19 + i * 20;
    }

    // one shortcut button
    record Shortcut(String label, String shortLabel, String hint, ItemStack icon, int accent) {}

    // a button column reads the same top to bottom: all icons or none, all hints or none
    record Column(boolean shortNames, boolean icons, boolean hints) {
        // plain name only
        static final Column PLAIN = new Column(false, false, false);

        String label(Shortcut s) {
            return shortNames ? s.shortLabel() : s.label();
        }

        String hint(Shortcut s) {
            return hints ? s.hint() : "";
        }

        ItemStack icon(Shortcut s) {
            return icons ? s.icon() : ItemStack.EMPTY;
        }
    }

    // how shortcuts show at this width, decided once for all of them
    static Column column(Ui ui, int w) {
        int label = 0, small = 0, hint = 0;
        for (Shortcut s : SHORTCUT_LIST) {
            label = Math.max(label, ui.width(s.label()));
            small = Math.max(small, ui.width(s.shortLabel()));
            hint = Math.max(hint, ui.width(s.hint()));
        }
        boolean shortNames = BUTTON_TEXT + label > w - 3;
        if (shortNames) label = small;
        boolean icons = BUTTON_ICON_TEXT + label <= w - 3;
        return new Column(shortNames, icons, (icons ? BUTTON_ICON_TEXT : BUTTON_TEXT) + label + 6 + hint <= w - 4);
    }

    private static void buttonFace(Ui ui, int x, int y, int w, int h, int c, boolean hovered) {
        ui.rect(x, y, w, h, hovered ? 0x8C10161F : 0x70060A11);
        ui.gradH(x, y, w, h, Ui.alpha(c, hovered ? 0.42f : 0.16f), Ui.alpha(c, hovered ? 0.1f : 0.02f));
        ui.rect(x, y, 2, h, hovered ? Modern.lighten(c, 0.3f) : Ui.alpha(c, 0.8f));
        ui.rect(x + 2, y + h - 1, w - 2, 1, hovered ? Ui.alpha(c, 0.9f) : 0x18FFFFFF);
    }

    // flat button: color runs in from a lit left edge, icon + name left, what it does right
    static void button(Ui ui, int x, int y, int w, int h, String label, String hint, ItemStack icon, int accent, boolean hovered) {
        int c = accent | 0xFF000000, ly = y + (h - 8) / 2, tx = x + BUTTON_TEXT;
        buttonFace(ui, x, y, w, h, c, hovered);
        if (icon != null && !icon.isEmpty()) {
            ui.icon(icon, x + 5, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
            tx = x + BUTTON_ICON_TEXT;
        }
        ui.textShadow(ui.ellipsize(label, x + w - 3 - tx), tx, ly, hovered ? Modern.lighten(c, 0.7f) : Modern.INK);
        if (hint != null && !hint.isEmpty()) ui.textRight(hint, x + w - 4, ly, Modern.DIM);
    }

    // the one thing to do when theres nothing else, text in the middle
    static void loneButton(Ui ui, int x, int y, int w, int h, String label, int accent, boolean hovered) {
        int c = accent | 0xFF000000;
        buttonFace(ui, x, y, w, h, c, hovered);
        ui.textCenter(ui.ellipsize(label, w - 8), x + w / 2, y + (h - 8) / 2, hovered ? Modern.lighten(c, 0.7f) : Modern.INK);
    }

    // reload button text, long form if it fits
    static String reloadLabel(Ui ui, int w) {
        String full = "No backpacks found :(. Click to reload storage.";
        return ui.width(full) <= w - 8 ? full : "Click to reload storage";
    }
}
