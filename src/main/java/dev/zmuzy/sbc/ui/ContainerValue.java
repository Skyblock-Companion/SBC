// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.mc.mixin.ContainerAccess;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// card next to any chest with priced stuff in it: what its worth, how much could be priced, biggest first
// no room or nothing priced = small one line plate instead
public final class ContainerValue {
    // all stacks with the same id + name
    record Line(String name, int color, ItemStack icon, long count, int stacks, double value) {}

    // glass panes dont count. priced/filled skip them, rawFilled counts everything
    record Summary(double total, int priced, int filled, int slots, long units, List<Line> lines, List<Line> unpriced, int rawFilled) {}

    private static final int GAP = 8, TWO_COLUMN = 160, NARROW = 132, LINE = 22, TIP_LINES = 24;
    private static final int HEAD = Modern.BAND + 2 + 2 * (Modern.ROW + Modern.METER_BLOCK) + 2 * Modern.ROW + 2 + Modern.ROW;
    private static final Summary NOTHING = new Summary(0, 0, 0, 0, 0, List.of(), List.of(), 0);
    private static final Ui ui = new Ui();
    private static net.minecraft.world.inventory.AbstractContainerMenu lastMenu;
    private static int lastHash;
    private static Summary now = NOTHING;
    private static boolean plateAbove;
    private static int cardX, cardY, cardW, cardH;
    private static List<int[]> hud = List.of();
    private static long hudAt;

    private static final java.util.regex.Pattern OTHER_STORAGE = java.util.regex.Pattern.compile("Personal Vault|Chest Storage|Wood Chest\\+");

    // hovered kind on the card, its stacks light up in the chest
    private static Line hoveredLine;
    private static final Map<Line, List<Slot>> LINE_SLOTS = new java.util.IdentityHashMap<>();
    private static int overHidden = Integer.MAX_VALUE;
    private ContainerValue() {}

    private static dev.zmuzy.sbc.feature.Option option(String key) {
        var f = Features.get("containervalue");
        return f == null ? null : f.option(key);
    }

    private static boolean on(String key, boolean def) {
        var o = option(key);
        return o == null ? def : o.bool();
    }

    private static int number(String key, int def) {
        var o = option(key);
        return o == null ? def : o.integer();
    }

    private static int colorOf(String key, int def) {
        var o = option(key);
        return o == null ? def : o.color();
    }

    private static int wide() {
        return number("containervalue.width", 208);
    }

    private static int maxLines() {
        return number("containervalue.itemToShow", 6);
    }

    // from skyhanni formatPrice
    private static String fmt(double v) {
        var o = option("containervalue.format");
        return o != null && o.choice().equals("LONG") ? String.format(Locale.ROOT, "%,d", Math.round(v)) : dev.zmuzy.sbc.core.Prices.coins(v);
    }

    private static int accent() {
        return colorOf("containervalue.color", Modern.GOLD);
    }

    // from skyhanni ChestValue.isAnySoulbound
    static boolean soulbound(List<String> cleanLore) {
        for (String l : cleanLore) if (l.equals("* Co-op Soulbound *") || l.equals("* Soulbound *")) return true;
        return false;
    }

    // from skyhanni ChestValue.isValidStorage. recipes, bazaar and other menus dont count
    static boolean validStorage(String title) {
        String name = GameState.strip(title).trim();
        if (name.contains("Backpack") && name.contains("Slot #") || name.startsWith("Ender Chest (")) return true;
        return name.equals("Chest") || name.equals("Large Chest") || OTHER_STORAGE.matcher(name).matches();
    }

    private static final class Tally {
        final String name;
        final int color;
        final ItemStack icon;
        final List<Slot> slots = new ArrayList<>();
        long count;
        int stacks;
        double value;

        Tally(String name, int color, ItemStack icon) {
            this.name = name;
            this.color = color;
            this.icon = icon;
        }

        Line line() {
            return new Line(name, color, icon, count, stacks, value);
        }
    }

    private static void measure(AbstractContainerScreen<?> cs, Minecraft mc, boolean own) {
        var menu = cs.getMenu();
        int h = 1;
        for (Slot s : menu.slots) if ((s.container == mc.player.getInventory()) == own) h = h * 31 + (s.getItem().isEmpty() ? 0 : System.identityHashCode(s.getItem()) + s.getItem().getCount());
        if (menu == lastMenu && h == lastHash) return;
        lastMenu = menu;
        lastHash = h;
        double total = 0;
        int priced = 0, filled = 0, slots = 0, raw = 0;
        long units = 0;
        Map<String, Tally> kinds = new LinkedHashMap<>(), unknown = new LinkedHashMap<>();
        boolean noSoulbound = on("containervalue.ignoreSoulbound", false);
        for (Slot s : menu.slots) {
            if ((s.container == mc.player.getInventory()) != own) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) {
                slots++;
                continue;
            }
            raw++;
            // like skyhanni ChestValue.createItems: only sb items, so page arrows and buttons dont count
            if (RadialScreen.filler(st) || Gain.sbId(st).isEmpty()) continue;
            if (noSoulbound && soulbound(dev.zmuzy.sbc.core.LegacyText.lore(st).stream().map(GameState::strip).toList())) continue;
            slots++;
            filled++;
            units += st.getCount();
            double v = Gain.ofStack(st);
            if (v > 0) {
                total += v;
                priced++;
            }
            String name = GameState.strip(st.getHoverName().getString()).trim();
            Tally t = (v > 0 ? kinds : unknown).computeIfAbsent(Gain.sbId(st) + "|" + name, k -> new Tally(name, ItemList.colorOf(st.getHoverName()), st));
            t.count += st.getCount();
            t.stacks++;
            t.slots.add(s);
            if (v > 0) t.value += v;
        }
        List<Line> lines = new ArrayList<>(kinds.size()), nameless = new ArrayList<>(unknown.size());
        LINE_SLOTS.clear();
        for (Tally t : kinds.values()) {
            Line l = t.line();
            lines.add(l);
            LINE_SLOTS.put(l, t.slots);
        }
        for (Tally t : unknown.values()) nameless.add(t.line());
        var sorting = option("containervalue.sorting");
        boolean ascending = sorting != null && sorting.choice().equals("ASCENDING");
        // hidden stuff still counts in "others", so that line goes last either way
        int hideBelow = number("containervalue.hideBelow", 0);
        lines.sort((a, b) -> {
            boolean ah = a.value() < hideBelow, bh = b.value() < hideBelow;
            if (ah != bh) return ah ? 1 : -1;
            return ascending ? Double.compare(a.value(), b.value()) : Double.compare(b.value(), a.value());
        });
        overHidden = (int) lines.stream().filter(l -> l.value() >= hideBelow).count();
        nameless.sort((a, b) -> Long.compare(b.count(), a.count()));
        now = new Summary(total, priced, filled, slots, units, lines, nameless, raw);
    }

    public static void draw(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
        plateAbove = false;
        cardW = 0;
        hoveredLine = null;
        if (!Features.on("containervalue") || !(screen instanceof AbstractContainerScreen<?> cs) || screen instanceof dev.zmuzy.sbc.ui.storage.StorageOverlayScreen) return;
        boolean own = cs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen && on("containervalue.inOwnInventory", false);
        if (!own && (!(cs instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen) || !validStorage(cs.getTitle().getString()))) return;
        if ("CATACOMBS".equals(GameState.island) && !on("containervalue.inDungeons", false)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        measure(cs, mc, own);
        if (now.rawFilled() == 0) return;
        ContainerAccess a = (ContainerAccess) cs;
        ui.begin(g, mx, my);
        int left = a.sbc$left(), top = a.sbc$top();
        // only glass, nothing to say
        if (now.filled() == 0) return;
        boolean listThere = Features.on("itemlist") && ItemList.onLeftSide(left, a.sbc$width(), screen.width);
        paintClassic(ui, left, top, a.sbc$width(), screen.height, listThere, Features.on("quicknav") ? 42 : 18, now, hudBoxes(screen));
        // skyhanni highlightsOnHoverSlots
        if (hoveredLine != null && on("containervalue.highlight", true)) {
            List<Slot> slots = LINE_SLOTS.get(hoveredLine);
            if (slots != null) for (Slot s : slots) ui.rect(left + s.x, top + s.y, 16, 16, 0x80FFD166);
        }
        ui.flushTooltip();
    }

    // card if it fits and something has a price, else the one liner
    static void paintClassic(Ui ui, int left, int top, int cw, int sh, boolean listThere, int lift, Summary s, List<int[]> boxes) {
        plateAbove = false;
        cardW = 0;
        int[] spot = s.priced() > 0 && !listThere ? place(left, top, sh, s, boxes) : null;
        if (spot != null) {
            cardX = spot[0];
            cardY = top;
            cardW = spot[1];
            cardH = paintCard(ui, cardX, cardY, cardW, sh - top - 4, s);
        } else paintPlate(ui, left, top, cw, lift, s);
    }

    // left of the chest, away from any hud card next to it. null = use the plate
    static int[] place(int left, int top, int sh, Summary s, List<int[]> boxes) {
        int right = left - GAP, from = 2, h = height(s, sh - top - 4);
        for (int[] b : boxes) if (b[0] < right && b[1] < top + h && top < b[1] + b[3]) from = Math.max(from, b[0] + b[2] + 2);
        int w = Math.min(wide(), right - from);
        return w >= NARROW ? new int[]{right - w, w} : null;
    }

    // x, y, w, h of hud stuff that stays on top of this screen. checked a few times a sec
    public static List<int[]> hudBoxes(Screen screen) {
        long t = System.currentTimeMillis();
        if (t - hudAt < 250) return hud;
        hudAt = t;
        List<int[]> out = new ArrayList<>();
        try {
            if ((GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV) && !Minecraft.getInstance().gui.hud.isHidden())
                for (HudLayout.Placed p : HudLayout.layout(screen.width, screen.height, false))
                    if (Config.I.hudInInventory || HudLayout.MENU_ELEMENTS.contains(p.id)) out.add(new int[]{p.x, p.y, p.w, p.h});
        } catch (Throwable t2) {
            out.clear();
        }
        hud = out;
        return out;
    }

    public static boolean over(double x, double y) {
        return cardW > 0 && x >= cardX && y >= cardY && x < cardX + cardW && y < cardY + cardH;
    }

    // total is above the chest this frame (where a search box would go)
    public static boolean plateAbove() {
        return plateAbove;
    }

    // one line version. if the button row is in the way it goes on the title line, shorter
    static void paintPlate(Ui ui, int left, int top, int cw, int lift, Summary s) {
        boolean priced = s.priced() > 0, onTitle = top - lift < 2;
        if (onTitle && !priced) return;
        String label = priced ? "Contents " : "No prices for these", coins = priced ? fmt(s.total()) : "";
        String rest = priced ? " " + Modern.fraction(count(s.priced()), count(s.filled()), 'f', '7') + (onTitle ? "" : " §7priced") + " §b" + ItemList.percent((double) s.priced() / s.filled()) : "";
        if (onTitle) label = "";
        int lw = ui.width(label), vw = ui.width(coins), tw = lw + vw + ui.width(rest);
        int w = tw + 12, h = 14, x = onTitle ? left + cw - 5 - w : left + cw / 2 - w / 2, y = onTitle ? top + 2 : top - lift;
        plateAbove = !onTitle;
        Theme.current().panel(ui, x, y, w, h);
        Modern.edge(ui, x, y, h, priced ? accent() : Modern.DIM);
        if (!label.isEmpty()) ui.textShadow(label, x + 6, y + 3, Modern.LABEL);
        if (priced) {
            ui.textShadow(coins, x + 6 + lw, y + 3, accent());
            ui.textShadow(rest, x + 6 + lw + vw, y + 3, Modern.LABEL);
        }
        cardX = x;
        cardY = y;
        cardW = w;
        cardH = h;
        if (ui.hover(x, y, w, h)) ui.tooltip.add(plateTip(s));
    }

    private static Component text(String s) {
        return Component.literal(s);
    }

    // plate replaces the card so its tooltip has all the card numbers
    private static List<Component> plateTip(Summary s) {
        List<Component> tip = new ArrayList<>();
        if (s.priced() > 0) tip.add(text("§6" + count(Math.round(s.total())) + " §7coins"));
        else tip.add(text("§7No prices for these"));
        tip.add(text(ratio("Priced stacks", s.priced(), s.filled())));
        tip.add(text(ratio("Slots filled", s.filled(), s.slots())));
        tip.add(text("§7Items inside: §f" + count(s.units())));
        if (s.priced() == 0) return tip;
        tip.add(text("§7Average stack: §6" + fmt(s.total() / s.priced())));
        tip.add(text(""));
        kinds(tip, s.lines(), 0, TIP_LINES - 12, s.total());
        if (!s.unpriced().isEmpty()) {
            tip.add(text(""));
            tip.add(text("§7No price:"));
            nameless(tip, s.unpriced(), 6);
        }
        return tip;
    }

    private static String ratio(String label, int part, int of) {
        return "§7" + label + ": " + Modern.fraction(count(part), count(of), 'f', '7') + " §b" + ItemList.percent(of <= 0 ? 0 : (double) part / of);
    }

    private static Component named(Line l, String more) {
        return Component.empty().append(Component.literal(l.name()).withColor(l.color() & 0xFFFFFF)).append(text(more));
    }

    private static void kinds(List<Component> tip, List<Line> lines, int from, int max, double total) {
        int end = Math.min(lines.size(), from + max);
        for (int i = from; i < end; i++) {
            Line l = lines.get(i);
            tip.add(named(l, " §7x" + count(l.count()) + "  §6" + fmt(l.value()) + " §b" + ItemList.percent(l.value() / total)));
        }
        if (end < lines.size()) tip.add(text("§7...and " + (lines.size() - end) + " more"));
    }

    private static void nameless(List<Component> tip, List<Line> lines, int max) {
        int end = Math.min(lines.size(), max);
        for (int i = 0; i < end; i++) tip.add(named(lines.get(i), " §7x" + count(lines.get(i).count())));
        if (end < lines.size()) tip.add(text("§7...and " + (lines.size() - end) + " more"));
    }

    private static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // label, both numbers, percent and a meter under it
    private static int stat(Ui ui, String label, int part, int of, int color, int x, int y, int w) {
        double share = of <= 0 ? 0 : (double) part / of;
        Modern.row(ui, label, Modern.fraction(count(part), count(of), 'f', '7') + " §b" + ItemList.percent(share), x, y, w, Modern.LABEL, Modern.INK);
        Modern.meter(ui, x, y + Modern.ROW - 1, w, Modern.METER, share, color);
        return Modern.ROW + Modern.METER_BLOCK;
    }

    static int linesFit(Summary s, int maxH) {
        return Math.max(1, Math.min(Math.min(maxLines(), overHidden), (maxH - HEAD - Modern.ROW - 4) / LINE));
    }

    static int height(Summary s, int maxH) {
        int shown = Math.min(linesFit(s, maxH), s.lines().size()), rest = s.lines().size() - shown;
        return HEAD + shown * LINE + (rest > 0 ? Modern.ROW + 2 : 0) + 2;
    }

    // maxH = room going down. returns height
    static int paintCard(Ui ui, int x, int y, int w, int maxH, Summary s) {
        int shown = Math.min(linesFit(s, maxH), s.lines().size()), rest = s.lines().size() - shown, h = height(s, maxH);
        Theme.current().panel(ui, x, y, w, h);
        String total = fmt(s.total());
        int totalW = ui.width(total);
        Modern.band(ui, w >= TWO_COLUMN ? "Contents Value" : "Contents", LegacyItems.stack("minecraft:chest"), "", accent(), x, y, w - totalW - 4);
        ui.textShadow(total, x + w - Modern.PAD - totalW, y + 3, s.priced() < s.filled() ? colorOf("containervalue.incompleteColor", accent()) : accent());
        if (ui.hover(x, y, w, Modern.BAND)) ui.tooltip("§6" + count(Math.round(s.total())) + " §7coins", "§7What the priced stacks would sell for");

        int cx = x + Modern.PAD, cw = w - 2 * Modern.PAD, cy = y + Modern.BAND + 2;
        if (ui.hover(x, cy, w, Modern.ROW + Modern.METER_BLOCK)) ui.tooltip.add(pricedTip(s));
        cy += stat(ui, "Priced stacks", s.priced(), s.filled(), accent(), cx, cy, cw);
        if (ui.hover(x, cy, w, Modern.ROW + Modern.METER_BLOCK)) slotsTip(ui, s);
        cy += stat(ui, "Slots filled", s.filled(), s.slots(), Modern.AQUA, cx, cy, cw);
        Modern.row(ui, "Items inside", count(s.units()), cx, cy, cw, Modern.LABEL, Modern.INK);
        cy += Modern.ROW;
        Modern.row(ui, "Average stack", fmt(s.total() / s.priced()), cx, cy, cw, Modern.LABEL, accent());
        cy += Modern.ROW + 2;

        String kinds = "§f" + s.lines().size() + (s.lines().size() == 1 ? " §7kind" : " §7kinds");
        int kw = ui.width(kinds);
        Modern.section(ui, "Top items", cx, cy, cw - kw - 4, accent());
        ui.textShadow(kinds, cx + cw - kw, cy, Modern.LABEL);
        cy += Modern.ROW;
        for (int i = 0; i < shown; i++) {
            line(ui, s.lines().get(i), s.total(), cx, cy, cw, w < TWO_COLUMN);
            cy += LINE;
        }
        if (rest > 0) {
            double others = 0;
            for (int i = shown; i < s.lines().size(); i++) others += s.lines().get(i).value();
            Modern.rule(ui, cx, cy, cw);
            cy += 2;
            ui.textShadow("+" + rest + (rest == 1 ? " other" : " others"), cx, cy, Modern.LABEL);
            String pct = ItemList.percent(others / s.total()), value = fmt(others);
            int pw = ui.width(pct);
            ui.textShadow(pct, cx + cw - pw, cy, Modern.AQUA);
            ui.textShadow(value, cx + cw - pw - 4 - ui.width(value), cy, accent());
            if (ui.hover(x, cy - 2, w, Modern.ROW + 2)) {
                List<Component> tip = new ArrayList<>();
                kinds(tip, s.lines(), shown, TIP_LINES, s.total());
                ui.tooltip.add(tip);
            }
        }
        return h;
    }

    // which stacks have no price
    private static List<Component> pricedTip(Summary s) {
        List<Component> tip = new ArrayList<>();
        int without = s.filled() - s.priced();
        if (without == 0) {
            tip.add(text("§aEvery stack has a price"));
            return tip;
        }
        tip.add(text("§f" + count(without) + (without == 1 ? " §7stack has no price:" : " §7stacks have no price:")));
        nameless(tip, s.unpriced(), TIP_LINES);
        return tip;
    }

    private static void slotsTip(Ui ui, Summary s) {
        String empty = "§f" + count(s.slots() - s.filled()) + " §7empty";
        if (s.rawFilled() > s.filled()) ui.tooltip(empty, "§f" + count(s.rawFilled() - s.filled()) + " §7menu glass, not counted");
        else ui.tooltip(empty);
    }

    // icon, name, value, count, share + a meter. narrow = name gets the whole line
    private static void line(Ui ui, Line l, double total, int x, int y, int w, boolean narrow) {
        double share = total <= 0 ? 0 : l.value() / total;
        boolean hv = ui.hover(x - 2, y, w + 4, LINE);
        if (hv) {
            ui.rect(x - 2, y, w + 4, LINE - 1, Ui.alpha(accent(), 0.14f));
            hoveredLine = l;
        }
        boolean icons = on("containervalue.showStacks", true);
        if (icons) {
            ui.rect(x, y + 1, 18, 18, 0x50000000);
            ui.rect(x, y + 18, 18, 1, Ui.alpha(l.color(), 0.75f));
            ui.icon(l.icon(), x + 1, y + 1, 1f);
        }
        int tx = icons ? x + 21 : x, tw = icons ? w - 21 : w;
        String value = fmt(l.value()), pct = ItemList.percent(share);
        int vw = ui.width(value), pw = ui.width(pct);
        if (narrow) {
            ui.textShadow(ui.ellipsize(l.name(), tw), tx, y + 1, l.color());
            ui.textShadow(value, tx, y + 10, accent());
        } else {
            ui.textShadow(ui.ellipsize(l.name(), tw - vw - 4), tx, y + 1, l.color());
            ui.textShadow(value, x + w - vw, y + 1, accent());
            String amount = "x" + count(l.count()) + (l.stacks() > 1 ? " §7in " + l.stacks() + " stacks" : "");
            ui.textShadow(ui.ellipsize(amount, tw - pw - 4), tx, y + 10, Modern.LABEL);
        }
        ui.textShadow(pct, x + w - pw, y + 10, Modern.AQUA);
        Modern.meter(ui, tx, y + 19, tw, Modern.METER, share, accent());
        if (hv) {
            ui.tooltip.add(List.of(named(l, ""),
                    text("§7Count: §f" + count(l.count()) + (l.stacks() > 1 ? " §7in §f" + l.stacks() + " §7stacks" : "")),
                    text("§7Each: §6" + fmt(l.value() / Math.max(1, l.count()))),
                    text("§7Value: §6" + count(Math.round(l.value())) + " §7coins"),
                    text("§7Share of this total: §b" + ItemList.percent(share))));
        }
    }
}
