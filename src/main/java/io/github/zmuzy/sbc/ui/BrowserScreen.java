// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.web.Html;
import io.github.zmuzy.sbc.web.WebPage;
import io.github.zmuzy.sbc.web.WikiLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// the skyblock wiki inside mc, drawn by the mod. other sites dont open on purpose
// works like a normal browser: tabs (ctrl+t / ctrl+w / ctrl+tab), back/forward/reload/home, address bar
public final class BrowserScreen extends Screen {
    private static final int STRIP = 0xFF202124, STRIP_TAB = 0xFF35363A, BAR = 0xFF35363A, PILL = 0xFF202124, PAGE = 0xFF1B1F27;
    private static final int TAB_H = 18, BAR_H = 22, BOOK_H = 14;

    private final Ui ui = new Ui();
    private final Screen parent;
    private final List<Tab> tabs = new ArrayList<>();
    private int cur;
    private String address = "";
    private boolean editing;
    private int suggestionSel = -1;
    private List<String[]> suggestions = List.of();
    private boolean finding;
    private String find = "";
    private int findIndex, findCount;
    private List<int[]> findHits = List.of();
    private float zoom;
    // where the page goes, in screen units
    private int pageX, pageY, pageW, pageH, contentX;

    // one tab = its own history, page, scroll and layout
    private final class Tab {
        final List<String> history = new ArrayList<>();
        final List<Float> scrolls = new ArrayList<>();
        final Set<Html.Node> toggled = new HashSet<>();
        int index = -1;
        WebPage page;
        boolean loading;
        float scroll, scrollTarget, restoreScroll = -1;
        WikiLayout layout;
        int layoutW;
        String wantAnchor = "";

        String url() {
            return index >= 0 && index < history.size() ? history.get(index) : "";
        }

        String title() {
            if (loading) return "Loading…";
            if (page == null) return "New tab";
            return page.title.isBlank() ? shortUrl(page.url) : page.title;
        }

        void open(String url, boolean push) {
            if (push) {
                if (index >= 0 && index < scrolls.size()) scrolls.set(index, scroll);
                while (history.size() > index + 1) {
                    history.remove(history.size() - 1);
                    scrolls.remove(scrolls.size() - 1);
                }
                history.add(url);
                scrolls.add(0f);
                index = history.size() - 1;
            }
            if (this == current()) {
                address = url;
                editing = false;
            }
            loading = true;
            scroll = scrollTarget = 0;
            WebPage.load(url).thenAccept(p -> Minecraft.getInstance().execute(() -> {
                if (history.isEmpty() || !history.get(index).equals(url)) return; // already went somewhere else
                page = p;
                loading = false;
                layout = null;
                toggled.clear();
                wantAnchor = p.fragment;
                history.set(index, p.url);
                if (this == current() && !editing) address = p.url;
                if (p.error.isEmpty()) remember(p.url, p.title);
                if (finding) countMatches();
            }));
        }

        void step(int by) {
            int to = index + by;
            if (to < 0 || to >= history.size()) return;
            scrolls.set(index, scroll);
            index = to;
            restoreScroll = scrolls.get(to);
            open(history.get(to), false);
        }

        void lay(int width) {
            if (layout != null && layoutW == width) return;
            layoutW = width;
            layout = WikiLayout.of(page.root == null ? Html.parse("") : page.root, page.title, width, toggled, ui::width);
            if (restoreScroll >= 0) {
                scroll = scrollTarget = restoreScroll;
                restoreScroll = -1;
            }
            if (!wantAnchor.isEmpty()) {
                jump(wantAnchor);
                wantAnchor = "";
            }
            if (finding) countMatches();
        }

        void jump(String anchor) {
            String id = anchor.replace(' ', '_');
            Integer y = layout == null ? null : layout.anchors.get(id);
            if (y != null) scrollTarget = y - 4;
        }
    }

    public BrowserScreen(Screen parent, String url) {
        super(Component.literal("Browser"));
        this.parent = parent;
        zoom = Math.max(0.5f, Math.min(2.5f, Config.I.browserZoom <= 0 ? 1f : Config.I.browserZoom));
        Tab t = new Tab();
        tabs.add(t);
        if (url != null && !url.isBlank()) t.open(url, true);
        else editing = true;
    }

    private Tab current() {
        return tabs.get(Math.max(0, Math.min(cur, tabs.size() - 1)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String shortUrl(String url) {
        String s = url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        return s.length() > 40 ? s.substring(0, 39) + "…" : s;
    }

    private void remember(String url, String title) {
        if (url == null || url.isBlank()) return;
        List<String> h = Config.I.browserHistory;
        h.removeIf(e -> e.startsWith(url + "\t") || e.equals(url));
        h.add(0, url + "\t" + (title == null ? "" : title));
        while (h.size() > 200) h.remove(h.size() - 1);
    }

    private boolean bookmarked(String url) {
        for (String b : Config.I.browserBookmarks) if (b.startsWith(url + "\t") || b.equals(url)) return true;
        return false;
    }

    private void toggleBookmark() {
        Tab t = current();
        if (t.url().isEmpty()) return;
        String url = t.url();
        if (bookmarked(url)) Config.I.browserBookmarks.removeIf(b -> b.startsWith(url + "\t") || b.equals(url));
        else Config.I.browserBookmarks.add(url + "\t" + t.title());
    }

    private void newTab(String url) {
        Tab t = new Tab();
        tabs.add(t);
        cur = tabs.size() - 1;
        if (url != null && !url.isBlank()) t.open(url, true);
        else {
            editing = true;
            address = "";
        }
    }

    private void closeTab(int i) {
        if (tabs.size() <= 1) {
            onClose();
            return;
        }
        tabs.remove(i);
        if (cur >= tabs.size()) cur = tabs.size() - 1;
        else if (i < cur) cur--;
        editing = false;
        address = current().url();
    }

    private void go(String text) {
        String q = text.trim();
        if (q.isEmpty()) return;
        if (!WebPage.isWiki(q)) q = WebPage.wikiSearchUrl(q); // anything thats not a wiki url = search the wiki for it
        current().open(q, true);
        editing = false;
        suggestionSel = -1;
    }

    private void follow(Tab t, String href) {
        if (href.startsWith("#")) {
            t.jump(href.substring(1));
            return;
        }
        if (ctrlDown()) {
            newTab(href);
            return;
        }
        int hash = href.indexOf('#');
        if (hash > 0 && href.substring(0, hash).equals(t.url().split("#")[0]) && t.layout != null) {
            t.jump(href.substring(hash + 1));
            return;
        }
        t.open(href, true);
    }

    // --- suggestions ---

    private void updateSuggestions() {
        List<String[]> out = new ArrayList<>();
        String q = address.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            for (String b : Config.I.browserBookmarks) {
                String[] p = split(b);
                if (!WebPage.isWiki(p[0])) continue;
                out.add(new String[]{p[0], p[1], "★"});
                if (out.size() >= 3) break;
            }
            for (String h : Config.I.browserHistory) {
                String[] p = split(h);
                if (!WebPage.isWiki(p[0])) continue;
                out.add(new String[]{p[0], p[1], "⏱"});
                if (out.size() >= 7) break;
            }
        } else {
            if (!WebPage.isWiki(address.trim())) out.add(new String[]{WebPage.wikiSearchUrl(address.trim()), "Search the wiki for \"" + address.trim() + "\"", "🔍"});
            for (String b : Config.I.browserBookmarks) {
                String[] p = split(b);
                if (WebPage.isWiki(p[0]) && (p[0].toLowerCase(Locale.ROOT).contains(q) || p[1].toLowerCase(Locale.ROOT).contains(q))) out.add(new String[]{p[0], p[1], "★"});
                if (out.size() >= 7) break;
            }
            for (String h : Config.I.browserHistory) {
                String[] p = split(h);
                if (WebPage.isWiki(p[0]) && (p[0].toLowerCase(Locale.ROOT).contains(q) || p[1].toLowerCase(Locale.ROOT).contains(q))) {
                    boolean dup = false;
                    for (String[] o : out) if (o[0].equals(p[0])) dup = true;
                    if (!dup) out.add(new String[]{p[0], p[1], "⏱"});
                }
                if (out.size() >= 7) break;
            }
        }
        suggestions = out;
        if (suggestionSel >= out.size()) suggestionSel = out.size() - 1;
    }

    private static String[] split(String entry) {
        int i = entry.indexOf('\t');
        return i < 0 ? new String[]{entry, shortUrl(entry)} : new String[]{entry.substring(0, i), entry.substring(i + 1).isBlank() ? shortUrl(entry.substring(0, i)) : entry.substring(i + 1)};
    }

    // --- find ---

    private void countMatches() {
        Tab t = current();
        findHits = t.layout == null ? List.of() : t.layout.find(find);
        findCount = findHits.size();
        if (findIndex >= findCount) findIndex = 0;
    }

    private void jumpToMatch() {
        Tab t = current();
        if (findHits.isEmpty() || t.layout == null) return;
        WikiLayout.Op op = t.layout.ops.get(findHits.get(Math.min(findIndex, findHits.size() - 1))[0]);
        t.scrollTarget = op.y - pageH / zoom / 3;
    }

    // --- drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ui.begin(g, mouseX, mouseY);
        int W = width, H = height;
        ui.rect(0, 0, W, H, 0xC0060810);
        int[] box = io.github.zmuzy.sbc.ui.theme.Modern.window(W, H, Integer.MAX_VALUE);
        int x = box[0], y = box[1], w = box[2], h = box[3];
        ui.round(x, y, w, h, 8, PAGE);
        // tab strip, toolbar, bookmarks bar
        ui.round(x, y, w, TAB_H + 6, 8, STRIP);
        ui.rect(x, y + TAB_H, w, 6, STRIP);
        tabStrip(x, y, w);
        ui.rect(x, y + TAB_H, w, BAR_H + 4, BAR);
        toolbar(x, y + TAB_H + 3, w);
        boolean books = !Config.I.browserBookmarks.isEmpty();
        int topH = TAB_H + BAR_H + 4 + (books ? BOOK_H : 0);
        if (books) bookmarksBar(x, y + TAB_H + BAR_H + 4, w);

        Tab t = current();
        pageX = x;
        pageY = y + topH;
        pageW = w;
        pageH = y + h - pageY;
        page(g, t, mouseX, mouseY);
        if (finding) findBar(x + w - 230, pageY + 4);
        if (editing) suggestionsBox(x, y + TAB_H + 3, w);
        ui.flushTooltip();
    }

    private void page(GuiGraphicsExtractor g, Tab t, int mouseX, int mouseY) {
        if (t.loading) {
            ui.text("Loading" + ".".repeat((int) (System.currentTimeMillis() / 300 % 4)), pageX + 16, pageY + 12, Ui.MUTED);
            return;
        }
        if (t.page == null) {
            newTabPage(pageX + 16, pageY + 12, pageW - 32);
            return;
        }
        if (!t.page.error.isEmpty()) {
            ui.textScaled(t.page.title, pageX + 16, pageY + 12, 1.5f, Ui.TEXT, true);
            ui.text(ui.ellipsize(t.page.error, pageW - 32), pageX + 16, pageY + 32, Ui.ORANGE);
            return;
        }
        int margin = 12, room = pageW - margin * 2 - 8;
        int layoutW = Math.round(Math.min(room, 760 * zoom) / zoom);
        t.lay(layoutW);
        contentX = pageX + margin + Math.max(0, (room - Math.round(layoutW * zoom)) / 2);
        float viewH = pageH / zoom;
        t.scrollTarget = Math.max(0, Math.min(t.scrollTarget, Math.max(0, t.layout.height - viewH)));
        t.scroll += (t.scrollTarget - t.scroll) * 0.4f;
        if (Math.abs(t.scrollTarget - t.scroll) < 0.5f) t.scroll = t.scrollTarget;

        int top = Math.round(t.scroll), bottom = top + (int) viewH + 2;
        int hx = (int) Math.floor((mouseX - contentX) / zoom), hy = (int) Math.floor((mouseY - pageY) / zoom) + top;
        boolean inside = mouseX >= pageX && mouseX < pageX + pageW && mouseY >= pageY && mouseY < pageY + pageH && !editing;
        String query = finding && !find.isEmpty() ? find.toLowerCase(Locale.ROOT) : null;

        g.enableScissor(pageX, pageY, pageX + pageW, pageY + pageH);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(contentX, pageY);
        pose.scale(zoom, zoom);
        pose.translate(0, -top);
        for (WikiLayout.Op op : t.layout.ops) {
            if (op.y + op.h < top || op.y > bottom) continue;
            switch (op.kind) {
                case RECT -> ui.rect(op.x, op.y, op.w, op.h, op.color);
                case IMAGE -> {
                    Identifier id = WikiImages.get(op.text);
                    if (id != null) g.blit(RenderPipelines.GUI_TEXTURED, id, op.x, op.y, 0f, 0f, op.w, op.h, op.w, op.h, op.w, op.h, 0xFFFFFFFF);
                }
                case TEXT -> {
                    if (query != null && op.text.toLowerCase(Locale.ROOT).contains(query)) ui.rect(op.x, op.y - 1, op.w, op.h + 1, 0x80FFD866);
                    if (op.scale == 1f) ui.text(op.text, op.x, op.y, op.color);
                    else ui.textScaled(op.text, op.x, op.y, op.scale, op.color, false);
                }
            }
        }
        WikiLayout.Hit over = null;
        if (inside) {
            for (WikiLayout.Hit h : t.layout.hits) {
                if (h.y + h.h < top || h.y > bottom) continue;
                if (hx >= h.x && hx < h.x + h.w && hy >= h.y && hy < h.y + h.h) over = h;
            }
        }
        if (over != null && (over.href != null || over.toggle != null)) ui.rect(over.x, over.y + over.h - 1, over.w, 1, WikiLayout.LINK);
        pose.popMatrix();
        g.disableScissor();
        if (over != null && over.tip != null) ui.tooltip(over.tip);
        else if (over != null && over.href != null && !over.href.startsWith("#")) ui.tooltip("§7" + shortUrl(over.href), "§8Ctrl+click: open in a new tab");

        if (t.layout.height > viewH) {
            int barH = Math.max(20, (int) (pageH * viewH / t.layout.height));
            int barY = pageY + (int) ((pageH - barH) * (t.scroll / Math.max(1, t.layout.height - viewH)));
            ui.rect(pageX + pageW - 6, pageY, 3, pageH, 0x14FFFFFF);
            ui.rect(pageX + pageW - 6, barY, 3, barH, 0x88FFFFFF);
        }
    }

    private void tabStrip(int x, int y, int w) {
        int tx = x + 6;
        int maxW = Math.min(160, Math.max(60, (w - 60) / Math.max(1, tabs.size())));
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            boolean on = i == cur;
            int tw = maxW;
            if (on) {
                ui.round(tx, y + 3, tw, TAB_H + 3, 5, STRIP_TAB);
                ui.rect(tx, y + TAB_H - 1, tw, 7, STRIP_TAB);
            } else if (ui.hover(tx, y + 3, tw, TAB_H)) ui.round(tx, y + 3, tw, TAB_H - 2, 5, 0x30FFFFFF);
            String title = t.title();
            ui.text(ui.ellipsize(title, tw - 26), tx + 8, y + 8, on ? Ui.TEXT : Ui.MUTED);
            final int idx = i;
            ui.click(tx, y + 3, tw - 18, TAB_H, () -> {
                cur = idx;
                editing = false;
                address = current().url();
                if (finding) countMatches();
            });
            boolean hvX = ui.hover(tx + tw - 16, y + 5, 12, 12);
            if (hvX) ui.round(tx + tw - 16, y + 5, 12, 12, 6, 0x40FFFFFF);
            ui.text("×", tx + tw - 13, y + 7, hvX ? Ui.TEXT : Ui.DIM);
            ui.click(tx + tw - 16, y + 5, 12, 12, () -> closeTab(idx));
            tx += tw + 2;
            if (i > 0 && !on && i - 1 != cur) ui.rect(tx - tw - 3, y + 7, 1, 10, 0x30FFFFFF);
        }
        boolean hvPlus = ui.hover(tx + 2, y + 5, 14, 14);
        if (hvPlus) ui.round(tx + 2, y + 5, 14, 14, 7, 0x40FFFFFF);
        ui.text("+", tx + 6, y + 7, hvPlus ? Ui.TEXT : Ui.MUTED);
        ui.click(tx + 2, y + 5, 14, 14, () -> newTab(null));
        ui.text(ui.ellipsize("Ctrl+T new tab · Ctrl+W close · Ctrl+F find · Ctrl+L address", w - (tx - x) - 40), tx + 24, y + 8, 0xFF6B6F78);
    }

    private void toolbar(int x, int ty, int w) {
        Tab t = current();
        int bx = x + 6;
        bx += iconButton("b_back", bx, ty, "‹", t.index > 0 ? () -> t.step(-1) : null);
        bx += iconButton("b_fwd", bx, ty, "›", t.index < t.history.size() - 1 ? () -> t.step(1) : null);
        bx += iconButton("b_reload", bx, ty, "⟳", t.url().isEmpty() ? null : () -> t.open(t.url(), false));
        bx += iconButton("b_home", bx, ty, "⌂", () -> t.open(WebPage.WIKI + "/w/Main_Page", true));
        int rightW = 22 * 4 + 6;
        int ax = bx + 2, aw = w - (ax - x) - rightW - 10;
        ui.round(ax, ty, aw, 20, 10, editing ? 0xFF3C4043 : PILL);
        if (editing) ui.round(ax, ty, aw, 20, 10, 0x30FAC775);
        String shown = editing ? address : t.loading ? address : t.page != null ? (t.page.title.isBlank() ? "" : t.page.title + "  ·  ") + shortUrl(t.page.url) : address;
        if (editing && address.isEmpty()) ui.text("Search the SkyBlock wiki", ax + 10, ty + 6, Ui.DIM);
        int room = aw - 10 - 28;
        while (ui.width(shown) > room && shown.length() > 1) shown = editing ? shown.substring(1) : shown.substring(0, shown.length() - 1);
        ui.text(shown, ax + 10, ty + 6, editing ? Ui.TEXT : Ui.MUTED);
        if (editing && (System.currentTimeMillis() / 500) % 2 == 0) ui.rect(ax + 11 + ui.width(shown), ty + 5, 1, 10, Ui.CYAN);
        ui.click(ax, ty, aw - 22, 20, () -> {
            editing = true;
            address = t.url();
            suggestionSel = -1;
            updateSuggestions();
        });
        boolean marked = bookmarked(t.url());
        boolean hvStar = ui.hover(ax + aw - 20, ty + 3, 16, 14);
        ui.text(marked ? "★" : "☆", ax + aw - 17, ty + 6, marked ? Ui.GOLD : hvStar ? Ui.TEXT : Ui.DIM);
        ui.click(ax + aw - 20, ty + 3, 16, 14, this::toggleBookmark);
        if (hvStar) ui.tooltip(marked ? "Remove bookmark" : "Bookmark this page");
        int rx = ax + aw + 6;
        rx += iconButton("b_zoomout", rx, ty, "−", () -> setZoom(zoom - 0.1f));
        rx += iconButton("b_zoomin", rx, ty, "+", () -> setZoom(zoom + 0.1f));
        if (ui.hover(rx - 44, ty, 44, 20)) ui.tooltip("Zoom " + Math.round(zoom * 100) + "%  (Ctrl+wheel, Ctrl+0 resets)");
        rx += iconButton("b_find", rx, ty, "⌕", () -> {
            finding = !finding;
            if (finding) countMatches();
        });
        iconButton("b_close", rx, ty, "✕", this::onClose);
    }

    private int iconButton(String key, int x, int y, String glyph, Runnable r) {
        boolean hv = r != null && ui.hover(x, y, 20, 20);
        if (hv) ui.round(x, y, 20, 20, 10, 0x30FFFFFF);
        ui.text(glyph, x + 10 - ui.width(glyph) / 2, y + 6, r == null ? 0xFF5F6368 : hv ? Ui.TEXT : Ui.MUTED);
        if (r != null) ui.click(x, y, 20, 20, r);
        return 22;
    }

    private void setZoom(float z) {
        zoom = Math.max(0.5f, Math.min(2.5f, Math.round(z * 10) / 10f));
        Config.I.browserZoom = zoom;
    }

    private void bookmarksBar(int x, int y, int w) {
        ui.rect(x, y, w, BOOK_H, 0xFF2B2D31);
        int bx = x + 8;
        for (String b : new ArrayList<>(Config.I.browserBookmarks)) {
            String[] p = split(b);
            if (!WebPage.isWiki(p[0])) continue;
            String label = "★ " + ui.ellipsize(p[1], 90);
            int bw = ui.width(label) + 10;
            if (bx + bw > x + w - 8) break;
            boolean hv = ui.hover(bx, y + 1, bw, BOOK_H - 2);
            if (hv) ui.round(bx, y + 1, bw, BOOK_H - 2, 4, 0x30FFFFFF);
            ui.text(label, bx + 5, y + 3, hv ? Ui.TEXT : Ui.MUTED);
            if (hv) ui.tooltip(p[1], "§7" + p[0], "§8right-click to remove");
            final String url = p[0];
            ui.clickRight(bx, y + 1, bw, BOOK_H - 2, () -> current().open(url, true), () -> Config.I.browserBookmarks.removeIf(e -> e.startsWith(url + "\t") || e.equals(url)));
            bx += bw + 4;
        }
    }

    private void findBar(int x, int y) {
        ui.round(x, y, 224, 22, 6, 0xFF35363A);
        String shown = find;
        while (ui.width(shown) > 120 && shown.length() > 1) shown = shown.substring(1);
        if (find.isEmpty()) ui.text("Find in page", x + 8, y + 7, Ui.DIM);
        else ui.text(shown, x + 8, y + 7, Ui.TEXT);
        if ((System.currentTimeMillis() / 500) % 2 == 0) ui.rect(x + 9 + ui.width(shown), y + 6, 1, 10, Ui.CYAN);
        String count = find.isEmpty() ? "" : findCount == 0 ? "0/0" : (Math.min(findIndex, findCount - 1) + 1) + "/" + findCount;
        ui.text(count, x + 136, y + 7, findCount == 0 && !find.isEmpty() ? Ui.RED : Ui.MUTED);
        iconButton("f_prev", x + 162, y + 1, "↑", findCount == 0 ? null : () -> {
            findIndex = (findIndex - 1 + findCount) % findCount;
            jumpToMatch();
        });
        iconButton("f_next", x + 182, y + 1, "↓", findCount == 0 ? null : () -> {
            findIndex = (findIndex + 1) % findCount;
            jumpToMatch();
        });
        iconButton("f_close", x + 202, y + 1, "✕", () -> finding = false);
    }

    private void suggestionsBox(int x, int ty, int w) {
        if (suggestions.isEmpty()) return;
        int ax = x + 6 + 22 * 4 + 2, aw = w - (ax - x) - (22 * 4 + 6) - 10;
        int sy = ty + 22, sh = suggestions.size() * 16 + 8;
        ui.round(ax, sy, aw, sh, 8, 0xFF2B2D31);
        for (int i = 0; i < suggestions.size(); i++) {
            String[] s = suggestions.get(i);
            int ry = sy + 4 + i * 16;
            boolean on = i == suggestionSel || ui.hover(ax + 2, ry, aw - 4, 16);
            if (on) ui.round(ax + 2, ry, aw - 4, 16, 4, 0x30FFFFFF);
            ui.text(s[2], ax + 8, ry + 4, Ui.DIM);
            String title = ui.ellipsize(s[1], aw / 2 - 30);
            ui.text(title, ax + 24, ry + 4, Ui.TEXT);
            ui.text(ui.ellipsize(shortUrl(s[0]), aw / 2 - 20), ax + 24 + ui.width(title) + 10, ry + 4, Ui.DIM);
            final String url = s[0];
            ui.click(ax + 2, ry, aw - 4, 16, () -> go(url));
        }
    }

    // empty tab = bookmarks + recent pages as tiles
    private void newTabPage(int x, int y, int w) {
        ui.textScaled("SkyBlock Wiki", x, y, 1.6f, Ui.TEXT, true);
        ui.text("Type above to search the wiki, or pick a page:", x, y + 22, Ui.MUTED);
        int ty = y + 40;
        if (!Config.I.browserBookmarks.isEmpty()) {
            ui.text("Bookmarks", x, ty, Ui.GOLD);
            ty += 12;
            ty = tiles(Config.I.browserBookmarks, x, ty, w, 12);
            ty += 8;
        }
        ui.text("Recent", x, ty, Ui.GOLD);
        ty += 12;
        if (Config.I.browserHistory.isEmpty()) ui.text("Nothing yet.", x, ty, Ui.DIM);
        else tiles(Config.I.browserHistory, x, ty, w, 16);
    }

    private int tiles(List<String> entries, int x, int y, int w, int max) {
        int tw = Math.max(120, (w - 12) / 3), tx = x, ty = y, n = 0;
        for (String e : new ArrayList<>(entries)) {
            if (n >= max) break;
            String[] p = split(e);
            if (!WebPage.isWiki(p[0])) continue;
            n++;
            boolean hv = ui.hover(tx, ty, tw - 6, 26);
            ui.round(tx, ty, tw - 6, 26, 6, hv ? 0xFF3C4043 : 0xFF2B2D31);
            ui.text(ui.ellipsize(p[1], tw - 20), tx + 7, ty + 5, Ui.TEXT);
            ui.text(ui.ellipsize(shortUrl(p[0]), tw - 20), tx + 7, ty + 15, Ui.DIM);
            final String url = p[0];
            ui.click(tx, ty, tw - 6, 26, () -> current().open(url, true));
            tx += tw;
            if (tx + tw - 6 > x + w) {
                tx = x;
                ty += 30;
            }
        }
        return tx == x ? ty : ty + 30;
    }

    // --- input ---

    private boolean ctrlDown() {
        long window = minecraft.getWindow().handle();
        return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit h = ui.hits.get(i);
            if (e.x() < h.x() || e.y() < h.y() || e.x() >= h.x() + h.w() || e.y() >= h.y() + h.h() || h.click() == null) continue;
            boolean wasEditing = editing;
            editing = false; // clicking the address bar turns it back on
            h.click().run();
            if (wasEditing && !editing) suggestionSel = -1;
            return true;
        }
        editing = false;
        Tab t = current();
        if (t.layout != null && !t.loading && e.x() >= pageX && e.x() < pageX + pageW && e.y() >= pageY && e.y() < pageY + pageH) {
            int hx = (int) Math.floor((e.x() - contentX) / zoom), hy = (int) Math.floor((e.y() - pageY) / zoom) + Math.round(t.scroll);
            WikiLayout.Hit over = null;
            for (WikiLayout.Hit h : t.layout.hits) {
                if ((h.href != null || h.toggle != null) && hx >= h.x && hx < h.x + h.w && hy >= h.y && hy < h.y + h.h) over = h;
            }
            if (over != null) {
                if (over.toggle != null) {
                    if (!t.toggled.remove(over.toggle)) t.toggled.add(over.toggle);
                    t.layout = null;
                } else {
                    follow(t, over.href);
                }
            }
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (ctrlDown()) {
            setZoom(zoom + (float) (sy > 0 ? 0.1 : -0.1));
            return true;
        }
        current().scrollTarget -= (float) (sy * 36);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        boolean ctrl = (e.modifiers() & InputConstants.MOD_CONTROL) != 0, shift = (e.modifiers() & InputConstants.MOD_SHIFT) != 0, alt = (e.modifiers() & InputConstants.MOD_ALT) != 0;
        Tab t = current();
        if (ctrl && k == InputConstants.KEY_T) {
            newTab(null);
            return true;
        }
        if (ctrl && k == InputConstants.KEY_W) {
            closeTab(cur);
            return true;
        }
        if (ctrl && k == InputConstants.KEY_TAB) {
            cur = (cur + (shift ? tabs.size() - 1 : 1)) % tabs.size();
            editing = false;
            address = current().url();
            return true;
        }
        if (ctrl && k == InputConstants.KEY_L) {
            editing = true;
            finding = false;
            address = t.url();
            suggestionSel = -1;
            updateSuggestions();
            return true;
        }
        if (ctrl && k == InputConstants.KEY_F) {
            finding = true;
            editing = false;
            countMatches();
            return true;
        }
        if (ctrl && k == InputConstants.KEY_D) {
            toggleBookmark();
            return true;
        }
        if (ctrl && (k == InputConstants.KEY_EQUALS || k == InputConstants.KEY_ADD)) {
            setZoom(zoom + 0.1f);
            return true;
        }
        if (ctrl && (k == InputConstants.KEY_MINUS || k == org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_MINUS)) {
            setZoom(zoom - 0.1f);
            return true;
        }
        if (ctrl && (k == InputConstants.KEY_0 || k == InputConstants.KEY_NUMPAD0)) {
            setZoom(1f);
            return true;
        }
        if (editing) {
            if (k == InputConstants.KEY_BACKSPACE && !address.isEmpty()) {
                address = ctrl ? address.replaceFirst("\\S*\\s*$", "") : address.substring(0, address.length() - 1);
                updateSuggestions();
            } else if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
                if (suggestionSel >= 0 && suggestionSel < suggestions.size()) go(suggestions.get(suggestionSel)[0]);
                else if (!address.isBlank()) go(address);
            } else if (k == InputConstants.KEY_ESCAPE) {
                editing = false;
                address = t.url();
            } else if (k == InputConstants.KEY_DOWN) {
                suggestionSel = suggestions.isEmpty() ? -1 : (suggestionSel + 1) % suggestions.size();
            } else if (k == InputConstants.KEY_UP) {
                suggestionSel = suggestions.isEmpty() ? -1 : (suggestionSel - 1 + suggestions.size()) % suggestions.size();
            } else if (k == InputConstants.KEY_V && ctrl) {
                address += minecraft.keyboardHandler.getClipboard().replaceAll("[\\r\\n]", "");
                updateSuggestions();
            }
            return true;
        }
        if (finding) {
            if (k == InputConstants.KEY_BACKSPACE && !find.isEmpty()) {
                find = find.substring(0, find.length() - 1);
                countMatches();
            } else if (k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER) {
                if (findCount > 0) {
                    findIndex = (findIndex + (shift ? findCount - 1 : 1)) % findCount;
                    jumpToMatch();
                }
            } else if (k == InputConstants.KEY_ESCAPE) {
                finding = false;
            } else if (k == InputConstants.KEY_V && ctrl) {
                find += minecraft.keyboardHandler.getClipboard().replaceAll("[\\r\\n]", "");
                countMatches();
            }
            return true;
        }
        if (k == InputConstants.KEY_BACKSPACE || (alt && k == InputConstants.KEY_LEFT)) {
            t.step(-1);
            return true;
        }
        if (alt && k == InputConstants.KEY_RIGHT) {
            t.step(1);
            return true;
        }
        if (k == InputConstants.KEY_F5 || (ctrl && k == InputConstants.KEY_R)) {
            if (!t.url().isEmpty()) t.open(t.url(), false);
            return true;
        }
        if (k == InputConstants.KEY_PAGEDOWN || k == InputConstants.KEY_SPACE) {
            t.scrollTarget += pageH * 0.8f / zoom;
            return true;
        }
        if (k == InputConstants.KEY_PAGEUP) {
            t.scrollTarget -= pageH * 0.8f / zoom;
            return true;
        }
        if (k == InputConstants.KEY_HOME) {
            t.scrollTarget = 0;
            return true;
        }
        if (k == InputConstants.KEY_END) {
            t.scrollTarget = t.layout == null ? 0 : t.layout.height;
            return true;
        }
        if (k == InputConstants.KEY_DOWN) {
            t.scrollTarget += 40;
            return true;
        }
        if (k == InputConstants.KEY_UP) {
            t.scrollTarget -= 40;
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        if (e.codepoint() < 32) return super.charTyped(e);
        String ch = new String(Character.toChars(e.codepoint()));
        if (editing && address.length() < 300) {
            address += ch;
            updateSuggestions();
            return true;
        }
        if (finding && find.length() < 100) {
            find += ch;
            findIndex = 0;
            countMatches();
            jumpToMatch();
            return true;
        }
        return super.charTyped(e);
    }

    @Override
    public void removed() {
        WikiImages.clear();
        Config.save();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
