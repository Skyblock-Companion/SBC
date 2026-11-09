// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.ui.ItemList;
import dev.zmuzy.sbc.ui.ItemScreen;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

// next to the museum menu: what you didnt donate yet, prices, category filter, 3 sorts, search. port of
// skyblocker MuseumManager, DonationButton, ItemFilter
public final class MuseumPanel implements SideHelper.Content {
    public static final MuseumPanel I = new MuseumPanel();
    private static final int ACCENT = 0xFFE8C468, ROW = 22;
    private static final String[] SORTS = {"BIN", "Craft", "Coins/XP"};
    private static final Pattern ALL_DONATED = Patterns.get("SH.museum.all-items-donated");
    private static final Pattern MAIN_MENU = Patterns.get("SH.data.bits.gui.museumguiname");
    private static final Pattern PAGE = Pattern.compile("Museum ➜ .+$");

    private static String query = "";
    private static boolean focused;
    private static int filter;
    private static ItemStack hovered;
    private static long hoveredAt;
    private final List<MuseumData.Donation> undonated = new ArrayList<>(), shown = new ArrayList<>();
    private int total, lastSort = -1, lastFilter = -1;
    private String lastQuery = "";
    private long pricedAt;
    private final Map<ItemStack, Boolean> complete = new WeakHashMap<>();

    private MuseumPanel() {}

    static void register() {
        Features.add(new Feature("museum_helper", "Menus", "Museum helper", "Beside the Museum menu: everything you have not donated yet with its price, by category, sorted by lowest BIN, craft cost or coins per SkyBlock XP, with a search. It learns from the Museum's pages you open and the donations you make.", "Skyblocker: museum overlay")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV));
        Features.add(new Feature("museum_highlight", "Menus", "Complete Museum categories", "In the Museum's menu, a category with every item donated turns green.", "SkyHanni: museum category highlighter")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV));
    }

    // ContainerScreenMixin check: Museum in the title
    public static boolean isMuseum(String title) {
        return title.contains("Museum");
    }

    private static boolean onPage(String title) {
        return PAGE.matcher(title).find();
    }

    static int slotColor(Slot slot, String title) {
        if (!Features.on("museum_highlight") || !MAIN_MENU.matcher(title).matches()) return 0;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return 0;
        Boolean done = I.complete.get(st);
        if (done == null) {
            done = false;
            for (String line : dev.zmuzy.sbc.core.LegacyText.lore(st)) {
                if (ALL_DONATED.matcher(GameState.strip(line)).find()) {
                    done = true;
                    break;
                }
            }
            I.complete.put(st, done);
        }
        return done ? 0xC855FF55 : 0;
    }

    @Override
    public String title() {
        return "Museum Helper";
    }

    @Override
    public ItemStack icon() {
        return LegacyItems.stack("minecraft:gold_block");
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    public String[] tabs() {
        return SORTS;
    }

    @Override
    public void refresh(AbstractContainerScreen<?> screen) {
        MuseumData.refreshCatalog();
        Profile.Data d = Profile.current;
        MuseumData.fromProfile(d);
        MuseumData.askOnce();
        if (screen.getMenu() instanceof ChestMenu menu) {
            String title = GameState.strip(screen.getTitle().getString()).trim();
            if (onPage(title)) MuseumData.readPage(i -> menu.getSlot(i).getItem(), menu.getRowCount() * 9);
        }
        long now = System.currentTimeMillis();
        if (now - pricedAt < 15_000 && !undonated.isEmpty()) return;
        pricedAt = now;
        MuseumData.forgetCrafts();
        set(MuseumData.undonated(MuseumData::lBinOf, MuseumData::craftOf), MuseumData.all().size());
    }

    // the list filled in, what shows follows from it
    public void set(List<MuseumData.Donation> list, int all) {
        undonated.clear();
        undonated.addAll(list);
        total = all;
        lastSort = -1;
    }

    private String filterName() {
        List<String> cats = MuseumData.categories();
        return filter <= 0 || filter > cats.size() ? "All" : pretty(cats.get(filter - 1));
    }

    private static String pretty(String s) {
        String[] words = s.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String w : words) out.append(out.isEmpty() ? "" : " ").append(w.isEmpty() ? "" : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        return out.toString();
    }

    // filter, sort + search, only when one of them or the list changed
    private void apply(int sort) {
        if (sort == lastSort && filter == lastFilter && query.equals(lastQuery)) return;
        lastSort = sort;
        lastFilter = filter;
        lastQuery = query;
        shown.clear();
        List<String> cats = MuseumData.categories();
        String cat = filter <= 0 || filter > cats.size() ? null : cats.get(filter - 1);
        for (MuseumData.Donation d : undonated) if ((cat == null || d.category.equals(cat)) && MuseumData.matches(d, query)) shown.add(d);
        MuseumData.sort(shown, MuseumData.Sort.values()[sort]);
    }

    @Override
    public int summary(Ui ui, int x, int y, int w) {
        int half = (w - 3) / 2, donated = Math.max(0, total - undonated.size());
        double pct = total == 0 ? 0 : (double) donated / total;
        SideHelper.tile(ui, x, y, half, "Donated", donated + "/" + total, "", ACCENT);
        SideHelper.tile(ui, x + half + 3, y, w - half - 3, "Left to give", String.valueOf(undonated.size()), ItemList.percent(pct), Modern.INK);
        Modern.meter(ui, x, y + 24, w, 2, pct, ACCENT);
        int sy = y + 28;
        // filter says what its on, click = next one
        int fw = Math.max(58, ui.width("Filter: Dungeoneering") + 6 > w / 2 ? w / 2 : ui.width("Filter: Dungeoneering") + 6);
        int bw = w - fw - 2;
        ItemList.searchBox(ui, x, sy, bw, 14, query, focused, "Search...", ACCENT);
        ui.click(x, sy, bw, 14, () -> focused = true);
        ui.button("museum_filter", x + bw + 2, sy, fw, 14, ui.ellipsize(filterName(), fw - 6), Ui.SOFT, () -> {
            int n = MuseumData.categories().size() + 1;
            filter = (filter + 1) % Math.max(1, n);
        });
        if (ui.hover(x + bw + 2, sy, fw, 14)) ui.tooltip(filterTip());
        int ry = sy + 16;
        String source = MuseumData.knowsFromApi() ? "§7From the §aMuseum API" : "§7From the pages you open";
        ui.textShadow(ui.ellipsize(source, w), x, ry, Modern.LABEL);
        if (ui.hover(x, ry, w, 9)) ui.tooltip(MuseumData.knowsFromApi()
                ? new String[]{"§7Read from the Museum API, plus what you donated since.", "§7/sbc museum resync reads it again."}
                : new String[]{"§7Only the Museum pages you open and what you donate", "§7count as donated. Everything else is listed here."});
        return 28 + 16 + 11;
    }

    private String[] filterTip() {
        List<String> tip = new ArrayList<>();
        tip.add("§aItem Filter");
        tip.add("");
        tip.add((filter == 0 ? "§b➤ " : "§7  ") + "All");
        List<String> cats = MuseumData.categories();
        for (int i = 0; i < cats.size(); i++) tip.add((filter == i + 1 ? "§b➤ " : "§7  ") + pretty(cats.get(i)));
        tip.add("");
        tip.add("§eClick to switch filter!");
        return tip.toArray(new String[0]);
    }

    @Override
    public int list(Ui ui, int tab, int x, int y, int w) {
        if (total == 0) return SideHelper.emptyText(ui, "The museum list is still loading.", x, y, w);
        apply(tab);
        if (shown.isEmpty()) return SideHelper.emptyText(ui, query.isEmpty() ? "Nothing left to donate here." : "Nothing matches.", x, y, w);
        hovered = null;
        int y0 = y;
        y += sortLine(ui, tab, x, y, w);
        for (MuseumData.Donation d : shown) {
            if (y + ROW >= ui.clipY0 && y <= ui.clipY1) row(ui, d, tab, x, y, w);
            y += ROW;
        }
        return y - y0;
    }

    private int sortLine(Ui ui, int tab, int x, int y, int w) {
        String[] names = {"Lowest BIN, low first", "Craft cost, low first", "Coins per XP, low first"};
        ui.textShadow(ui.ellipsize(names[Math.max(0, Math.min(tab, 2))], w), x, y + 1, Modern.DIM);
        return 11;
    }

    private void row(Ui ui, MuseumData.Donation d, int sort, int x, int y, int w) {
        String item = MuseumData.displayItem(d.id);
        ItemStack icon = ItemInfo.stack(item);
        boolean hv = ui.hover(x, y, w, ROW - 2);
        int color = ItemList.colorOf(MuseumData.colored(d.id));
        ui.rect(x, y, w, ROW - 2, hv ? Ui.alpha(color, 0.18f) : 0x22000000);
        ui.rect(x, y, 2, ROW - 2, color);
        ui.icon(icon, x + 3, y + 2, 1f);
        int tx = x + 21, right = x + w - 3;
        ui.textShadow(ui.ellipsize(MuseumData.displayName(d.id), right - tx), tx, y + 1, color);
        String price = d.hasPrice() ? Prices.coins(d.effective()) : "no price";
        String sub = sort == 2 && d.ratio() > 0 ? "§b" + Prices.coins(d.ratio()) + " §7per XP" : "§b+" + d.totalXp() + " §7XP";
        ui.textShadow(sub, tx, y + 10, Modern.LABEL);
        ui.textShadow(price, right - ui.width(price), y + 10, d.hasPrice() ? Modern.GOLD : Modern.DIM);
        if (!hv) return;
        hovered = icon;
        hoveredAt = ui.now;
        ui.tooltip(tip(d));
        ui.click(x, y, w, ROW - 2, () -> ItemScreen.open(item));
    }

    // from skyblocker DonationButton.buildTooltip
    private static String[] tip(MuseumData.Donation d) {
        List<String> t = new ArrayList<>();
        t.add(MuseumData.colored(d.id).isEmpty() || d.isSet() ? "§f" + MuseumData.displayName(d.id) : MuseumData.colored(d.id));
        for (String piece : d.set) {
            double[] p = d.pieces.get(piece);
            t.add("  " + ItemRepo.coloredName(piece) + " §8(" + (p != null && p[2] > 0 ? "§6" + Prices.coins(p[2]) : "§cUnknown") + "§8)");
        }
        t.add("");
        t.add("§7Reward: §b" + d.totalXp() + " SkyBlock XP");
        t.add("§7Lowest BIN: " + (d.lBin() > 0 ? "§6" + Prices.coins(d.lBin()) + " Coins" : "§cUnknown"));
        t.add("§7Craft Cost: " + (d.craftable() ? "§6" + Prices.coins(d.hasDiscount() ? d.craftCost() - d.discount : d.craftCost()) + " Coins"
                + (d.hasDiscount() ? " §8(§6" + Prices.coins(d.craftCost()) + "§f - §6" + Prices.coins(d.discount) + "§8)" : "") : "§cUnknown"));
        t.add("§7Coins/XP Ratio: §b" + Prices.coins(d.ratio()) + " Coins per XP");
        if (d.countsFor.size() > 1) {
            t.add("");
            t.add("§7Will count for:");
            for (MuseumData.Donation c : d.countsFor) t.add(" §7● §f" + MuseumData.displayName(c.id) + " §b(" + c.xp + " XP)");
        }
        if (d.craftable() && d.hasDiscount()) {
            t.add("");
            t.add("§7Crafted with: §8(Donated Item)");
            t.add(" §7- §f" + MuseumData.displayName(d.discountId) + " §a✔ §8(§6" + Prices.coins(d.discount) + "§8)");
        }
        t.add("");
        t.add("§eClick: recipe and prices");
        return t.toArray(new String[0]);
    }

    // --- typing ---

    // any click: search lets go, takes it back if the click was on it
    static void clicked() {
        focused = false;
    }

    static boolean typed(int codepoint) {
        if (!focused) return true;
        if (codepoint >= 32 && codepoint != 127 && query.length() < 60) query += new String(Character.toChars(codepoint));
        return false;
    }

    // false = swallow the key
    static boolean key(AbstractContainerScreen<?> s, KeyEvent event) {
        if (focused) {
            if (event.key() == InputConstants.KEY_BACKSPACE) {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
            } else if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_RETURN) focused = false;
            return false;
        }
        return hovered == null || System.currentTimeMillis() - hoveredAt > 200 || !dev.zmuzy.sbc.ui.UiFeatures.keyOver(s, hovered, event);
    }

    // forget what the mouse was on, panel isnt drawn in this screen
    static void notShown() {
        hovered = null;
        focused = false;
    }

    // for pictures
    public static void preview(String q) {
        query = q;
        filter = 0;
    }
}
