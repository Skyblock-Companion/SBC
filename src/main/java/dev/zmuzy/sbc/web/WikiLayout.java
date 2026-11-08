// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// lays out a wiki page like the wikis own stylesheet does for its own markup: infobox on the right, tables
// sized to content, crafting grids, item tooltips...
public final class WikiLayout {
    // wiki px -> screen units, its 16px text is about the games 9px font
    static final float SCALE = 0.6f;
    private static final int LINE = 11, SLOT = 22;

    public static final int TEXT_COLOR = 0xFFE6E8EC, LINK = 0xFF41C3FF;
    private static final int BORDER = 0xFF4A5060, HEAD_BG = 0xFF2C313C, CELL_BG = 0xFF21252E, BOX_BG = 0xFF262B35, TITLE_BG = 0xFF388BB2, RULE = 0xFF4A4F5C;
    private static final int MCUI_BG = 0xFFC6C6C6, SLOT_BG = 0xFF8B8B8B, SLOT_DARK = 0xFF373737, SLOT_LIGHT = 0xFFFFFFFF;
    private static final Map<String, Integer> COLORS = Map.ofEntries(Map.entry("black", 0xFF000000), Map.entry("dark_blue", 0xFF0000AA), Map.entry("dark_green", 0xFF00AA00),
            Map.entry("dark_aqua", 0xFF00AAAA), Map.entry("dark_red", 0xFFAA0000), Map.entry("dark_purple", 0xFFAA00AA), Map.entry("gold", 0xFFFFAA00), Map.entry("gray", 0xFFAAAAAA),
            Map.entry("dark_gray", 0xFF555555), Map.entry("blue", 0xFF5555FF), Map.entry("green", 0xFF55FF55), Map.entry("aqua", 0xFF55FFFF), Map.entry("red", 0xFFFF5555),
            Map.entry("light_purple", 0xFFFF55FF), Map.entry("yellow", 0xFFFFFF55), Map.entry("white", 0xFFFFFFFF), Map.entry("orange", 0xFFFFAA00), Map.entry("pink", 0xFFFF55FF), Map.entry("purple", 0xFFAA00AA));
    private static final Set<String> SKIP_TAGS = Set.of("script", "style", "input", "label", "link", "meta", "noscript", "iframe", "video", "audio", "colgroup", "col");
    private static final Set<String> SKIP_CLASSES = Set.of("machine-metadata", "mw-editsection", "toctogglespan", "navbox-vde", "infobox-section-labels", "mw-collapsible-toggle", "mw-empty-elt", "navigation-only");
    private static final Set<String> INLINE = Set.of("a", "span", "b", "strong", "i", "em", "u", "small", "big", "sup", "sub", "abbr", "code", "tt", "kbd", "s", "del", "ins", "font", "bdi", "cite", "q", "mark", "time", "var", "nowiki");
    private static final Pattern STYLE_COLOR = Pattern.compile("(?:^|;)\\s*color\\s*:\\s*#([0-9a-fA-F]{3,6})");
    private static final Pattern STYLE_COLOR_NAME = Pattern.compile("(?:^|;)\\s*color\\s*:\\s*([a-z_]+)");
    private static final Pattern STYLE_BG = Pattern.compile("background(?:-color)?\\s*:\\s*#([0-9a-fA-F]{3,6})");
    private static final Pattern STYLE_ALIGN = Pattern.compile("text-align\\s*:\\s*(left|center|right)");
    private static final Pattern SRCSET_2X = Pattern.compile("(\\S+)\\s+2x");

    // width of text with its § codes: game font in game, anything in a test
    public interface Fonts {
        int width(String text);
    }

    public enum Kind { RECT, TEXT, IMAGE }

    // one thing to draw
    public static final class Op {
        public final Kind kind;
        public int x, y;
        public final int w, h, color;
        public final String text;
        public final float scale;

        Op(Kind kind, int x, int y, int w, int h, int color, String text, float scale) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.color = color;
            this.text = text;
            this.scale = scale;
        }
    }

    // something the mouse can do. href = link, tip = tooltip, toggle = the box it opens/closes
    public static final class Hit {
        public int x, y;
        public final int w, h;
        public final String href;
        public final String[] tip;
        public final Html.Node toggle;

        Hit(int x, int y, int w, int h, String href, String[] tip, Html.Node toggle) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.href = href;
            this.tip = tip;
            this.toggle = toggle;
        }
    }

    // what text looks like at this point of the page
    private record S(int color, boolean bold, boolean italic, String href, String[] tip, float scale) {
        S color(int c) {
            return new S(c, bold, italic, href, tip, scale);
        }

        S asBold() {
            return new S(color, true, italic, href, tip, scale);
        }

        S asItalic() {
            return new S(color, bold, true, href, tip, scale);
        }

        S link(String h) {
            return new S(LINK, bold, italic, h, tip, scale);
        }

        S tip(String[] t) {
            return new S(color, bold, italic, href, t, scale);
        }

        S scale(float f) {
            return new S(color, bold, italic, href, tip, f);
        }

        String prefix() {
            return (bold ? "§l" : "") + (italic ? "§o" : "");
        }
    }

    private static final class Item {
        String text, url;
        S style;
        int w, h;
        boolean space, slot;
        String count;
    }

    // a column of flowing content
    private final class Box {
        int x, w, y, align;
        int floatBottom, floatW;
        final List<Item> line = new ArrayList<>();
        int lineW;
        boolean pendingSpace, pre;

        Box(int x, int w, int y) {
            this.x = x;
            this.w = Math.max(10, w);
            this.y = y;
        }

        int avail() {
            return y < floatBottom ? Math.max(40, w - floatW - 8) : w;
        }

        void add(Item it) {
            int gap = it.space && !line.isEmpty() ? fonts.width(" ") : 0;
            if (!line.isEmpty() && lineW + gap + it.w > avail()) {
                flush();
                gap = 0;
            }
            if (line.isEmpty()) it.space = false;
            line.add(it);
            lineW += gap + it.w;
        }

        void flush() {
            if (line.isEmpty()) return;
            int height = 0;
            for (Item it : line) height = Math.max(height, it.url != null || it.slot ? it.h + 1 : Math.round(LINE * it.style.scale));
            int free = avail() - lineW;
            int cx = x + (align == 1 ? Math.max(0, free / 2) : align == 2 ? Math.max(0, free) : 0);
            StringBuilder run = new StringBuilder();
            S runStyle = null;
            int runX = cx;
            for (Item it : line) {
                int gap = it.space ? fonts.width(" ") : 0;
                if (it.url != null || it.slot) {
                    endRun(run, runStyle, runX, y, height);
                    runStyle = null;
                    cx += gap;
                    int iy = y + (height - it.h) / 2;
                    if (it.slot) slotAt(cx, iy, it.w, it.url, it.style, it.count);
                    else {
                        ops.add(new Op(Kind.IMAGE, cx, iy, it.w, it.h, 0, it.url, 1));
                        hit(cx, iy, it.w, it.h, it.style);
                    }
                    cx += it.w;
                    runX = cx;
                    continue;
                }
                if (runStyle != it.style) {
                    endRun(run, runStyle, runX, y, height);
                    runStyle = it.style;
                    runX = cx + gap;
                    cx += gap;
                    run.append(it.text);
                } else {
                    if (it.space) run.append(' ');
                    run.append(it.text);
                    cx += gap;
                }
                cx += it.w;
            }
            endRun(run, runStyle, runX, y, height);
            y += height;
            line.clear();
            lineW = 0;
        }

        void text(String raw, S style) {
            if (pre) {
                String[] lines = raw.split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        if (line.isEmpty()) y += LINE;
                        flush();
                    }
                    if (!lines[i].isEmpty()) word(lines[i], style, false);
                }
                return;
            }
            int i = 0, n = raw.length();
            while (i < n) {
                int start = i;
                while (i < n && isSpace(raw.charAt(i))) i++;
                if (i > start) pendingSpace = true;
                start = i;
                while (i < n && !isSpace(raw.charAt(i))) i++;
                if (i > start) {
                    word(raw.substring(start, i).replace(' ', ' '), style, pendingSpace);
                    pendingSpace = false;
                }
            }
        }

        private void word(String text, S style, boolean space) {
            Item it = new Item();
            it.text = text;
            it.style = style;
            it.w = Math.round(fonts.width(style.prefix() + text) * style.scale);
            it.space = space;
            add(it);
        }
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r';
    }

    public final List<Op> ops = new ArrayList<>();
    public final List<Hit> hits = new ArrayList<>();
    public final Map<String, Integer> anchors = new HashMap<>();
    public int height;

    private final Fonts fonts;
    private final Set<Html.Node> toggled;
    private final Map<Html.Node, int[]> measured = new IdentityHashMap<>();
    private final S base = new S(TEXT_COLOR, false, false, null, null, 1);

    private WikiLayout(Fonts fonts, Set<Html.Node> toggled) {
        this.fonts = fonts;
        this.toggled = toggled;
    }

    // page laid out this wide. toggled = collapsible boxes the reader changed
    public static WikiLayout of(Html.Node root, String title, int width, Set<Html.Node> toggled, Fonts fonts) {
        WikiLayout l = new WikiLayout(fonts, toggled);
        Box b = l.new Box(0, width, 0);
        if (title != null && !title.isEmpty()) {
            b.text(title, l.base.scale(2f).color(0xFFFFFFFF));
            b.flush();
            b.y += 3;
            l.rect(0, b.y, width, 1, RULE);
            b.y += 8;
        }
        l.children(root, b, l.base);
        b.flush();
        l.height = Math.max(b.y, b.floatBottom) + 12;
        return l;
    }

    private void rect(int x, int y, int w, int h, int color) {
        ops.add(new Op(Kind.RECT, x, y, w, h, color, null, 1));
    }

    private void hit(int x, int y, int w, int h, S style) {
        if (style.href != null || style.tip != null) hits.add(new Hit(x, y, w, h, style.href, style.tip, null));
    }

    private void endRun(StringBuilder run, S style, int x, int lineY, int lineH) {
        if (style == null || run.length() == 0) {
            run.setLength(0);
            return;
        }
        String text = run.toString();
        run.setLength(0);
        int w = Math.round(fonts.width(style.prefix() + text) * style.scale), h = Math.round(9 * style.scale);
        int y = lineY + (lineH - h) / 2;
        ops.add(new Op(Kind.TEXT, x, y, w, h, style.color, style.prefix() + text, style.scale));
        hit(x, y - 1, w, h + 2, style);
    }

    // --- walking the page ---

    private void children(Html.Node n, Box b, S style) {
        for (Html.Node c : n.children) node(c, b, style);
    }

    private static boolean hidden(Html.Node n) {
        if (SKIP_TAGS.contains(n.tag)) return true;
        String cls = n.attr("class");
        if (cls != null) for (String c : cls.split("\\s+")) if (SKIP_CLASSES.contains(c)) return true;
        String st = n.attr("style");
        return st != null && st.replace(" ", "").contains("display:none");
    }

    private static int hex(String s) {
        if (s.length() == 3) s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        return s.length() == 6 ? 0xFF000000 | Integer.parseInt(s, 16) : 0;
    }

    // color-gold, light-color, style="color:..."
    private static S styled(Html.Node n, S s) {
        String cls = n.attr("class");
        if (cls != null) {
            for (String c : cls.split("\\s+")) {
                if (c.startsWith("color-")) {
                    Integer color = COLORS.get(c.substring(6));
                    if (color != null) s = s.color(color);
                } else if (c.equals("bold") || c.equals("format-l")) {
                    s = s.asBold();
                } else if (c.equals("format-o")) {
                    s = s.asItalic();
                }
            }
        }
        String st = n.attr("style");
        if (st != null) {
            Matcher m = STYLE_COLOR.matcher(st);
            if (m.find()) s = s.color(hex(m.group(1)));
            m = STYLE_COLOR_NAME.matcher(st);
            if (m.find() && COLORS.containsKey(m.group(1))) s = s.color(COLORS.get(m.group(1)));
            if (st.replace(" ", "").contains("font-weight:bold")) s = s.asBold();
        }
        return s;
    }

    // "/w/Hyperion", "#Crafting", or a link off the wiki which goes nowhere here
    private static String link(String href) {
        if (href == null || href.isEmpty()) return null;
        if (href.startsWith("#")) return href;
        if (href.startsWith("/w/") || href.startsWith("/index.php")) return WebPage.WIKI + href;
        return WebPage.isWiki(href) ? href : null;
    }

    private static String[] minetip(Html.Node n) {
        String title = n.attr("data-minetip-title");
        if (title == null && n.attr("data-minetip-text") == null) return null;
        List<String> lines = new ArrayList<>();
        if (title != null) lines.add(title.replace('&', '§'));
        String text = n.attr("data-minetip-text");
        if (text != null) for (String l : text.split("(?<!\\\\)/", -1)) lines.add(l.replace("\\/", "/").replace('&', '§').stripTrailing());
        return lines.toArray(String[]::new);
    }

    private void node(Html.Node n, Box b, S style) {
        if (n.isText()) {
            b.text(n.text, style);
            return;
        }
        if (hidden(n)) return;
        String id = n.attr("id");
        if (id != null) anchors.putIfAbsent(id, b.y);
        S s = styled(n, style);
        String[] tip = minetip(n);
        if (tip != null) s = s.tip(tip);
        switch (n.tag) {
            case "br" -> {
                if (b.line.isEmpty()) b.y += LINE / 2;
                b.flush();
                return;
            }
            case "img" -> {
                image(n, b, s);
                return;
            }
            case "hr" -> {
                b.flush();
                b.y += 4;
                rect(b.x, b.y, b.avail(), 1, RULE);
                b.y += 5;
                return;
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                heading(n, b, s);
                return;
            }
            case "table" -> {
                table(n, b, s);
                return;
            }
            case "ul", "ol" -> {
                list(n, b, s);
                return;
            }
            case "dl" -> {
                definitions(n, b, s);
                return;
            }
            case "a" -> {
                String h = link(n.attr("href"));
                S linked = h != null ? s.link(h) : n.attr("href") != null && n.attr("href").startsWith("http") ? s.color(0xFF9AA3AE) : s;
                children(n, b, styled(n, linked));
                return;
            }
            case "b", "strong", "th" -> s = s.asBold();
            case "i", "em", "cite" -> s = s.asItalic();
            default -> {
            }
        }
        if (n.hasClass("invslot")) {
            slot(n, b, s);
            return;
        }
        if (n.hasClass("mcui")) {
            mcui(n, b, s);
            return;
        }
        if (n.hasClass("infobox")) {
            infobox(n, b, s);
            return;
        }
        if (n.hasClass("toc")) {
            toc(n, b, s);
            return;
        }
        if (n.hasClass("gallery")) {
            gallery(n, b, s);
            return;
        }
        if (n.hasClass("widget-title") || n.hasClass("widget-subtitle")) {
            b.flush();
            int start = ops.size(), top = b.y + 4;
            b.y = top + 4;
            int alignWas = b.align;
            b.align = 1;
            b.text(n.text(), s.asBold().color(0xFFFFFFFF).scale(n.hasClass("widget-title") ? 1.2f : 1f));
            b.flush();
            b.align = alignWas;
            b.y += 4;
            ops.add(start, new Op(Kind.RECT, b.x, top, b.avail(), b.y - top, TITLE_BG, null, 1));
            b.y += 4;
            return;
        }
        if (n.hasClass("mw-collapsible") && collapsibleBox(n, b, s)) return;
        if (INLINE.contains(n.tag)) {
            children(n, b, s);
            return;
        }
        block(n, b, s);
    }

    private void block(Html.Node n, Box b, S s) {
        b.flush();
        boolean para = n.tag.equals("p") || n.tag.equals("blockquote") || n.tag.equals("pre") || n.tag.equals("figure");
        if (n.tag.equals("blockquote")) {
            b.x += 12;
            b.w -= 12;
        }
        int alignWas = b.align;
        b.align = alignOf(n, b.align);
        boolean preWas = b.pre;
        b.pre = n.tag.equals("pre");
        if (n.hasClass("hatnote") || n.hasClass("dablink")) s = s.asItalic();
        if (n.tag.equals("figcaption") || n.hasClass("thumbcaption") || n.hasClass("gallerytext")) s = s.color(0xFFB8BEC8);
        int startOp = ops.size(), top = b.y;
        children(n, b, s);
        b.flush();
        String bg = background(n);
        if (bg != null && b.y > top) ops.add(startOp, new Op(Kind.RECT, b.x, top, b.avail(), b.y - top, hex(bg), null, 1));
        b.pre = preWas;
        b.align = alignWas;
        if (n.tag.equals("blockquote")) {
            b.x -= 12;
            b.w += 12;
        }
        if (para) b.y += 6;
    }

    private static String background(Html.Node n) {
        String st = n.attr("style");
        if (st == null) return null;
        Matcher m = STYLE_BG.matcher(st);
        return m.find() ? m.group(1) : null;
    }

    private static int alignOf(Html.Node n, int inherited) {
        if (n.hasClass("centertext") || n.tag.equals("center")) return 1;
        String al = n.attr("align");
        if (al != null) inherited = al.equalsIgnoreCase("center") ? 1 : al.equalsIgnoreCase("right") ? 2 : 0;
        String st = n.attr("style");
        if (st != null) {
            Matcher m = STYLE_ALIGN.matcher(st);
            if (m.find()) return m.group(1).equals("center") ? 1 : m.group(1).equals("right") ? 2 : 0;
        }
        return inherited;
    }

    private void heading(Html.Node n, Box b, S s) {
        b.flush();
        int level = n.tag.charAt(1) - '0';
        b.y += level <= 2 ? 12 : 8;
        String id = n.attr("id");
        if (id == null) {
            Html.Node headline = n.find(null, "mw-headline");
            if (headline != null) id = headline.attr("id");
        }
        if (id != null) anchors.put(id, b.y);
        S h = s.color(0xFFFFFFFF).scale(level <= 2 ? 1.5f : level == 3 ? 1.2f : 1f);
        children(n, b, level >= 3 ? h.asBold() : h);
        b.flush();
        if (level <= 2) {
            b.y += 1;
            rect(b.x, b.y, b.avail(), 1, RULE);
            b.y += 3;
        }
        b.y += 3;
    }

    // --- images ---

    static String imageUrl(Html.Node img) {
        String src = img.attr("src");
        String set = img.attr("srcset");
        if (set != null) {
            Matcher m = SRCSET_2X.matcher(set);
            if (m.find()) src = m.group(1);
        }
        if (src == null) return null;
        if (src.startsWith("//")) return "https:" + src;
        return src.startsWith("/") ? WebPage.WIKI + src : src;
    }

    private static int attrInt(Html.Node n, String key) {
        try {
            return n.attr(key) == null ? 0 : Integer.parseInt(n.attr(key).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int[] imageSize(Html.Node img) {
        int w = Math.max(1, Math.round(Math.max(attrInt(img, "width"), 16) * SCALE));
        int h = Math.max(1, Math.round(Math.max(attrInt(img, "height"), 16) * SCALE));
        // the front page stylesheet draws its tiles small whatever the file size
        for (Html.Node n = img; n != null; n = n.parent()) {
            if (n.hasClass("frontpage-gallery") && w > 48) {
                h = h * 48 / w;
                w = 48;
                break;
            }
        }
        return new int[]{w, h};
    }

    private void image(Html.Node img, Box b, S s) {
        String url = imageUrl(img);
        if (url == null) return;
        int[] size = imageSize(img);
        Item it = new Item();
        it.url = url;
        it.style = s;
        int room = b.avail();
        it.w = Math.min(size[0], room);
        it.h = size[0] > room ? size[1] * room / size[0] : size[1];
        it.space = b.pendingSpace;
        b.pendingSpace = false;
        b.add(it);
    }

    // a game inventory slot in the text, like the one in an infobox
    private void slot(Html.Node n, Box b, S s) {
        Html.Node item = n.find(null, "invslot-item");
        S st = s;
        if (item != null) {
            String[] tip = minetip(item);
            if (tip != null) st = st.tip(tip);
        }
        Html.Node a = n.find("a", null);
        if (a != null && link(a.attr("href")) != null) st = st.link(link(a.attr("href")));
        Html.Node img = n.find("img", null);
        Item it = new Item();
        it.slot = true;
        it.url = img == null ? null : imageUrl(img);
        it.style = st;
        it.w = it.h = SLOT;
        Html.Node count = n.find(null, "invslot-stacksize");
        it.count = count == null ? null : count.text();
        it.space = b.pendingSpace;
        b.pendingSpace = false;
        b.add(it);
    }

    private void slotAt(int x, int y, int size, String url, S style, String count) {
        rect(x, y, size, size, SLOT_BG);
        rect(x, y, size, 1, SLOT_DARK);
        rect(x, y, 1, size, SLOT_DARK);
        rect(x, y + size - 1, size, 1, SLOT_LIGHT);
        rect(x + size - 1, y, 1, size, SLOT_LIGHT);
        if (url != null) ops.add(new Op(Kind.IMAGE, x + 2, y + 2, size - 4, size - 4, 0, url, 1));
        if (count != null && !count.isEmpty()) {
            int w = fonts.width(count);
            ops.add(new Op(Kind.TEXT, x + size - w - 1, y + size - 10, w, 9, 0xFFFFFFFF, count, 1));
        }
        hit(x, y, size, size, style);
    }

    // crafting grid: rows going in, an arrow, what comes out, on the games grey panel
    private void mcui(Html.Node n, Box b, S s) {
        b.flush();
        Html.Node input = n.find(null, "mcui-input"), output = n.find(null, "mcui-output");
        List<Html.Node> rows = new ArrayList<>();
        if (input != null) input.findAll(null, "mcui-row", rows);
        int cols = 0;
        for (Html.Node row : rows) {
            List<Html.Node> slots = new ArrayList<>();
            row.findAll(null, "invslot", slots);
            cols = Math.max(cols, slots.size());
        }
        int pad = 5, arrow = output == null ? 0 : 24;
        int width = cols * SLOT + arrow + (output == null ? 0 : SLOT) + pad * 2, height = Math.max(rows.size(), 1) * SLOT + pad * 2;
        int free = b.avail() - width;
        int x = b.x + (b.align == 1 ? Math.max(0, free / 2) : b.align == 2 ? Math.max(0, free) : 0), y = b.y;
        rect(x, y, width, height, MCUI_BG);
        rect(x, y, width, 1, SLOT_LIGHT);
        rect(x, y, 1, height, SLOT_LIGHT);
        rect(x, y + height - 1, width, 1, 0xFF555555);
        rect(x + width - 1, y, 1, height, 0xFF555555);
        int ry = y + pad;
        for (Html.Node row : rows) {
            List<Html.Node> slots = new ArrayList<>();
            row.findAll(null, "invslot", slots);
            int sx = x + pad;
            for (Html.Node sl : slots) {
                slotInto(sl, sx, ry, s);
                sx += SLOT;
            }
            ry += SLOT;
        }
        if (output != null) {
            int ax = x + pad + cols * SLOT + 4, ay = y + height / 2;
            rect(ax, ay - 1, 11, 3, SLOT_BG);
            for (int i = 0; i < 6; i++) rect(ax + 11 + i, ay - 5 + i, 1, 11 - 2 * i, SLOT_BG);
            List<Html.Node> out = new ArrayList<>();
            output.findAll(null, "invslot", out);
            if (!out.isEmpty()) slotInto(out.get(0), x + width - pad - SLOT, y + (height - SLOT) / 2, s);
        }
        b.y = y + height;
        b.y += 2;
    }

    private void slotInto(Html.Node sl, int x, int y, S s) {
        Html.Node item = sl.find(null, "invslot-item");
        S st = s;
        if (item != null) {
            String[] tip = minetip(item);
            if (tip != null) st = st.tip(tip);
        }
        Html.Node a = sl.find("a", null);
        if (a != null && link(a.attr("href")) != null) st = st.link(link(a.attr("href")));
        Html.Node img = sl.find("img", null);
        Html.Node count = sl.find(null, "invslot-stacksize");
        slotAt(x, y, SLOT, img == null ? null : imageUrl(img), st, count == null ? null : count.text());
    }

    // --- boxes ---

    // box on the right the text runs next to. on a narrow page it takes the whole width
    private void infobox(Html.Node n, Box b, S s) {
        b.flush();
        boolean beside = b.w >= 360;
        int w = beside ? Math.min(190, b.w * 2 / 5) : b.w;
        int x = b.x + b.w - w, y = b.y;
        int start = ops.size();
        Box in = new Box(x + 1, w - 2, y + 1);
        for (Html.Node c : n.children) infoboxPart(c, in, s);
        in.flush();
        int bottom = in.y + 1;
        ops.add(start, new Op(Kind.RECT, x, y, w, bottom - y, BORDER, null, 1));
        ops.add(start + 1, new Op(Kind.RECT, x + 1, y + 1, w - 2, bottom - y - 2, BOX_BG, null, 1));
        if (beside) {
            b.floatBottom = bottom + 6;
            b.floatW = w;
        } else {
            b.y = bottom + 6;
        }
    }

    private void infoboxPart(Html.Node n, Box in, S s) {
        if (n.isText()) return;
        if (hidden(n)) return;
        if (n.hasClass("infobox-title") || n.hasClass("infobox-header")) {
            int start = ops.size(), top = in.y;
            in.y += 3;
            in.align = 1;
            children(n, in, s.asBold().color(0xFFFFFFFF).scale(n.hasClass("infobox-title") ? 1.2f : 1f));
            in.flush();
            in.align = 0;
            in.y += 3;
            ops.add(start, new Op(Kind.RECT, in.x, top, in.w, in.y - top, TITLE_BG, null, 1));
            return;
        }
        if (n.hasClass("infobox-row-container")) {
            Html.Node label = null, value = null;
            for (Html.Node c : n.children) {
                if (c.hasClass("infobox-row-label")) label = c;
                if (c.hasClass("infobox-row-value")) value = c;
            }
            int top = in.y + 3;
            int bottom = top;
            if (label != null && value != null) {
                int lw = in.w * 2 / 5;
                Box l = new Box(in.x + 4, lw - 6, top), v = new Box(in.x + lw + 2, in.w - lw - 6, top);
                children(label, l, s.asBold());
                l.flush();
                children(value, v, s);
                v.flush();
                bottom = Math.max(l.y, v.y);
            } else if (value != null) {
                Box v = new Box(in.x + 4, in.w - 8, top);
                v.align = 1;
                children(value, v, s);
                v.flush();
                bottom = v.y;
            }
            in.y = bottom + 3;
            rect(in.x, in.y, in.w, 1, BORDER);
            in.y += 1;
            return;
        }
        if (n.tag.equals("div") || n.tag.equals("section") || n.tag.equals("ul")) {
            if (n.hasClass("infobox-panel") || n.hasClass("section") || n.find(null, "infobox-row-container") != null) {
                for (Html.Node c : n.children) infoboxPart(c, in, s);
                return;
            }
        }
        node(n, in, s);
        in.flush();
    }

    // contents, in its own box as wide as it needs
    private void toc(Html.Node n, Box b, S s) {
        b.flush();
        int w = Math.min(b.avail(), measure(n)[1] + 16);
        int start = ops.size(), top = b.y + 2;
        Box in = new Box(b.x + 6, w - 12, top + 5);
        Html.Node title = n.find(null, "toctitle");
        if (title != null) {
            in.align = 1;
            in.text(title.text(), s.asBold());
            in.flush();
            in.align = 0;
            rect(in.x, in.y + 1, in.w, 1, LINK);
            in.y += 4;
        }
        for (Html.Node c : n.children) if (c.tag != null && (c.tag.equals("ul") || c.tag.equals("ol"))) tocList(c, in, s, 0);
        in.flush();
        int bottom = in.y + 5;
        ops.add(start, new Op(Kind.RECT, b.x, top, w, bottom - top, BOX_BG, null, 1));
        b.y = bottom + 6;
    }

    private void tocList(Html.Node ul, Box in, S s, int depth) {
        for (Html.Node li : ul.children) {
            if (!"li".equals(li.tag)) continue;
            in.x += depth * 10;
            in.w -= depth * 10;
            for (Html.Node c : li.children) {
                if ("ul".equals(c.tag)) continue;
                node(c, in, s);
            }
            in.flush();
            in.y += 2;
            in.x -= depth * 10;
            in.w += depth * 10;
            for (Html.Node c : li.children) if ("ul".equals(c.tag)) tocList(c, in, s, depth + 1);
        }
    }

    private void gallery(Html.Node n, Box b, S s) {
        b.flush();
        List<Html.Node> boxes = new ArrayList<>();
        n.findAll(null, "gallerybox", boxes);
        if (boxes.isEmpty()) for (Html.Node c : n.children) if ("li".equals(c.tag)) boxes.add(c);
        if (boxes.isEmpty()) {
            children(n, b, s);
            b.flush();
            return;
        }
        int widest = 60;
        for (Html.Node g : boxes) widest = Math.max(widest, measure(g)[0]);
        int cell = Math.min(b.avail(), widest), cols = Math.max(1, (b.avail() + 6) / (cell + 6));
        int top = b.y, x = b.x, col = 0, rowBottom = top;
        for (Html.Node g : boxes) {
            Box in = new Box(x, cell, top);
            in.align = 1;
            children(g, in, s);
            in.flush();
            rowBottom = Math.max(rowBottom, in.y);
            x += cell + 6;
            if (++col == cols) {
                col = 0;
                x = b.x;
                top = rowBottom + 6;
                rowBottom = top;
            }
        }
        b.y = rowBottom + 6;
    }

    // box that shows just its head till opened. false = no separate content, its a normal block
    private boolean collapsibleBox(Html.Node n, Box b, S s) {
        if (n.tag.equals("table")) return false;
        Html.Node content = n.find(null, "mw-collapsible-content");
        if (content == null) return false;
        boolean closed = n.hasClass("mw-collapsed") != toggled.contains(n);
        b.flush();
        for (Html.Node c : n.children) {
            if (c == content) continue;
            node(c, b, s);
        }
        b.flush();
        String word = closed ? "[Show]" : "[Hide]";
        int w = fonts.width(word);
        ops.add(new Op(Kind.TEXT, b.x, b.y + 1, w, 9, LINK, word, 1));
        hits.add(new Hit(b.x, b.y, w, 11, null, null, n));
        b.y += LINE;
        if (!closed) {
            children(content, b, s);
            b.flush();
        }
        return true;
    }

    // --- lists ---

    private static boolean plain(Html.Node ul) {
        for (Html.Node n = ul; n != null; n = parentOf(n)) if (n.hasClass("plainlist") || n.hasClass("hlist")) return true;
        return false;
    }

    private static Html.Node parentOf(Html.Node n) {
        return n.parent();
    }

    private void list(Html.Node ul, Box b, S s) {
        b.flush();
        if (grid(ul)) {
            gallery(ul, b, s);
            return;
        }
        if (hlist(ul)) {
            hlistRow(ul, b, s);
            return;
        }
        boolean plain = plain(ul), numbered = ul.tag.equals("ol");
        int indent = plain && !inList(ul) ? 0 : 12, number = 0;
        b.x += indent;
        b.w -= indent;
        for (Html.Node li : ul.children) {
            if (!"li".equals(li.tag) || hidden(li)) continue;
            number++;
            if (!plain || inList(ul)) {
                String mark = numbered ? number + "." : "•";
                int w = fonts.width(mark);
                ops.add(new Op(Kind.TEXT, b.x - w - 3, b.y + 1, w, 9, 0xFFB8BEC8, mark, 1));
            }
            children(li, b, styled(li, s));
            b.flush();
            b.y += 2;
        }
        b.x -= indent;
        b.w += indent;
        b.y += 2;
    }

    // pictures in a grid: a gallery or the front page tiles
    private static boolean grid(Html.Node ul) {
        if (ul.hasClass("gallery")) return true;
        for (Html.Node n = ul; n != null; n = n.parent()) if (n.hasClass("frontpage-gallery")) return true;
        return false;
    }

    private static boolean hlist(Html.Node ul) {
        Html.Node p = ul.parent();
        return ul.hasClass("hlist") || p != null && p.hasClass("hlist");
    }

    // items in a row with dots between, like a nav box
    private void hlistRow(Html.Node ul, Box b, S s) {
        boolean first = true;
        for (Html.Node li : ul.children) {
            if (!"li".equals(li.tag)) continue;
            if (!first) b.text(" · ", s.color(0xFF9AA3AE));
            first = false;
            for (Html.Node c : li.children) {
                if ("ul".equals(c.tag) || "ol".equals(c.tag)) continue;
                node(c, b, s);
            }
        }
        b.flush();
    }

    private static boolean inList(Html.Node ul) {
        for (Html.Node n = ul.parent(); n != null; n = n.parent()) if ("li".equals(n.tag)) return true;
        return false;
    }

    private void definitions(Html.Node dl, Box b, S s) {
        b.flush();
        for (Html.Node c : dl.children) {
            if (c.isText()) continue;
            if (c.tag.equals("dt")) {
                children(c, b, s.asBold());
                b.flush();
            } else if (c.tag.equals("dd")) {
                b.x += 12;
                b.w -= 12;
                children(c, b, s);
                b.flush();
                b.x -= 12;
                b.w += 12;
            }
        }
        b.y += 4;
    }

    // --- tables ---

    private static final class Cell {
        Html.Node node;
        int col, row, colspan, rowspan;
    }

    private static List<Html.Node> rows(Html.Node table) {
        List<Html.Node> out = new ArrayList<>();
        for (Html.Node c : table.children) {
            if ("tr".equals(c.tag)) out.add(c);
            else if ("tbody".equals(c.tag) || "thead".equals(c.tag) || "tfoot".equals(c.tag)) out.addAll(rows(c));
        }
        return out;
    }

    private static int span(Html.Node cell, String key) {
        int v = attrInt(cell, key);
        return Math.max(1, Math.min(v, 200));
    }

    private boolean closed(Html.Node table) {
        return table.hasClass("mw-collapsible") && table.hasClass("mw-collapsed") != toggled.contains(table);
    }

    private List<Cell> grid(List<Html.Node> rows, int[] columns) {
        List<Cell> cells = new ArrayList<>();
        Map<Long, Boolean> taken = new HashMap<>();
        int cols = 0;
        for (int r = 0; r < rows.size(); r++) {
            int col = 0;
            for (Html.Node c : rows.get(r).children) {
                if (!"td".equals(c.tag) && !"th".equals(c.tag)) continue;
                if (hidden(c)) continue;
                while (taken.containsKey(((long) r << 20) | col)) col++;
                Cell cell = new Cell();
                cell.node = c;
                cell.row = r;
                cell.col = col;
                cell.colspan = span(c, "colspan");
                cell.rowspan = Math.min(span(c, "rowspan"), rows.size() - r);
                cells.add(cell);
                for (int rr = r; rr < r + cell.rowspan; rr++) for (int cc = col; cc < col + cell.colspan; cc++) taken.put(((long) rr << 20) | cc, true);
                col += cell.colspan;
                cols = Math.max(cols, col);
            }
        }
        columns[0] = cols;
        return cells;
    }

    private int[][] columnWidths(List<Cell> cells, int cols) {
        int[] min = new int[cols], max = new int[cols];
        int pad = 8;
        for (Cell c : cells) {
            if (c.colspan != 1) continue;
            int[] m = measure(c.node);
            min[c.col] = Math.max(min[c.col], m[0] + pad);
            max[c.col] = Math.max(max[c.col], m[1] + pad);
        }
        for (Cell c : cells) {
            if (c.colspan == 1) continue;
            int[] m = measure(c.node);
            int haveMin = 0, haveMax = 0;
            for (int i = c.col; i < c.col + c.colspan; i++) {
                haveMin += min[i];
                haveMax += max[i];
            }
            if (m[0] + pad > haveMin) for (int i = c.col; i < c.col + c.colspan; i++) min[i] += (m[0] + pad - haveMin) / c.colspan + 1;
            if (m[1] + pad > haveMax) for (int i = c.col; i < c.col + c.colspan; i++) max[i] += (m[1] + pad - haveMax) / c.colspan + 1;
        }
        for (int i = 0; i < cols; i++) max[i] = Math.max(max[i], min[i]);
        return new int[][]{min, max};
    }

    private void table(Html.Node table, Box b, S s) {
        b.flush();
        List<Html.Node> rows = rows(table);
        if (closed(table) && !rows.isEmpty()) rows = rows.subList(0, 1);
        int[] count = new int[1];
        List<Cell> cells = grid(rows, count);
        int cols = count[0];
        if (cols == 0) return;
        int[][] sizes = columnWidths(cells, cols);
        int sumMin = 0, sumMax = 0;
        for (int i = 0; i < cols; i++) {
            sumMin += sizes[0][i];
            sumMax += sizes[1][i];
        }
        boolean full = table.hasClass("navbox") || table.hasClass("mw-collapsible") || (table.attr("style") != null && table.attr("style").replace(" ", "").contains("width:100%"));
        // a table wider than the room next to the infobox goes under it, like a browser does
        if (b.y < b.floatBottom && (sumMin > b.avail() || full)) {
            b.y = b.floatBottom;
        }
        int room = b.avail();
        int[] widths = new int[cols];
        int total = 0;
        if (sumMax <= room) {
            int extra = full ? room - sumMax : 0;
            for (int i = 0; i < cols; i++) {
                widths[i] = sizes[1][i] + (sumMax == 0 ? 0 : extra * sizes[1][i] / sumMax);
                total += widths[i];
            }
        } else if (sumMin >= room) {
            for (int i = 0; i < cols; i++) {
                widths[i] = Math.max(12, sizes[0][i] * room / Math.max(1, sumMin));
                total += widths[i];
            }
        } else {
            int spread = room - sumMin, want = sumMax - sumMin;
            for (int i = 0; i < cols; i++) {
                widths[i] = sizes[0][i] + (want == 0 ? 0 : spread * (sizes[1][i] - sizes[0][i]) / want);
                total += widths[i];
            }
        }
        int tableX = b.x + (b.align == 1 ? Math.max(0, (room - total) / 2) : 0), top = b.y;
        int[] colX = new int[cols + 1];
        colX[0] = tableX;
        for (int i = 0; i < cols; i++) colX[i + 1] = colX[i] + widths[i];
        int[] rowY = new int[rows.size() + 1];
        rowY[0] = top;
        // each row as tall as its tallest cell, a cell over many rows counts on the last one
        int[] rowH = new int[rows.size()];
        List<int[]> ranges = new ArrayList<>();
        int startOps = ops.size();
        Box[] boxes = new Box[cells.size()];
        int[] firstOp = new int[cells.size()], lastOp = new int[cells.size()], firstHit = new int[cells.size()], lastHit = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            int w = colX[c.col + c.colspan] - colX[c.col];
            Box in = new Box(colX[c.col] + 4, w - 8, 0);
            boolean head = c.node.tag.equals("th");
            in.align = alignOf(c.node, head ? 1 : 0);
            firstOp[i] = ops.size();
            firstHit[i] = hits.size();
            S cs = styled(c.node, head ? s.asBold() : s);
            in.y = 3;
            children(c.node, in, cs);
            in.flush();
            lastOp[i] = ops.size();
            lastHit[i] = hits.size();
            boxes[i] = in;
            int h = in.y + 3;
            if (c.rowspan == 1) rowH[c.row] = Math.max(rowH[c.row], h);
        }
        for (Cell c : cells) {
            if (c.rowspan == 1) continue;
            int i = cells.indexOf(c), have = 0;
            for (int r = c.row; r < c.row + c.rowspan; r++) have += rowH[r];
            int need = boxes[i].y + 3;
            if (need > have) rowH[c.row + c.rowspan - 1] += need - have;
        }
        for (int r = 0; r < rows.size(); r++) rowY[r + 1] = rowY[r] + Math.max(rowH[r], 13);
        // row heights known now: move each cells content in place (centered like the wiki cells) and put the cell behind it
        List<Op> backs = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            Cell c = cells.get(i);
            int x = colX[c.col], y = rowY[c.row], w = colX[c.col + c.colspan] - x, h = rowY[c.row + c.rowspan] - y;
            int dy = y + (h - (boxes[i].y + 3)) / 2;
            for (int k = firstOp[i]; k < lastOp[i]; k++) ops.get(k).y += dy;
            for (int k = firstHit[i]; k < lastHit[i]; k++) hits.get(k).y += dy;
            String bg = background(c.node);
            if (bg == null && rows.get(c.row).attr("style") != null) bg = background(rows.get(c.row));
            int color = bg != null ? hex(bg) : c.node.tag.equals("th") ? HEAD_BG : CELL_BG;
            backs.add(new Op(Kind.RECT, x, y, w, h, BORDER, null, 1));
            backs.add(new Op(Kind.RECT, x + 1, y + 1, w - 1, h - 1, color, null, 1));
        }
        int bottom = rowY[rows.size()];
        backs.add(new Op(Kind.RECT, tableX, bottom, total + 1, 1, BORDER, null, 1));
        backs.add(new Op(Kind.RECT, tableX + total, top, 1, bottom - top, BORDER, null, 1));
        ops.addAll(startOps, backs);
        if (table.hasClass("mw-collapsible")) {
            boolean isClosed = closed(table);
            String word = isClosed ? "[Show]" : "[Hide]";
            int w = fonts.width(word), wx = tableX + total - w - 4, wy = top + (rowY[1] - top - 9) / 2;
            ops.add(new Op(Kind.TEXT, wx, wy, w, 9, LINK, word, 1));
            hits.add(new Hit(wx, wy - 1, w, 11, null, null, table));
        }
        b.y = bottom + 8;
    }

    // --- how wide something wants to be ---

    // narrowest it can go, widest it wants
    private int[] measure(Html.Node n) {
        int[] done = measured.get(n);
        if (done != null) return done;
        int[] r = {0, 0, 0}; // min, max, the line so far
        walk(n, r, base, true);
        r[1] = Math.max(r[1], r[2]);
        int[] out = {r[0], Math.max(r[0], r[1])};
        measured.put(n, out);
        return out;
    }

    private void endLine(int[] r) {
        r[1] = Math.max(r[1], r[2]);
        r[2] = 0;
    }

    private void walk(Html.Node n, int[] r, S style, boolean top) {
        if (n.isText()) {
            String prefix = style.prefix();
            boolean any = false;
            for (String word : n.text.trim().split("[ \\n\\t\\r]+")) {
                if (word.isEmpty()) continue;
                int w = fonts.width(prefix + word.replace(' ', ' '));
                r[0] = Math.max(r[0], w);
                r[2] += w + (any || r[2] > 0 ? fonts.width(" ") : 0);
                any = true;
            }
            return;
        }
        if (hidden(n)) return;
        switch (n.tag) {
            case "br" -> {
                endLine(r);
                return;
            }
            case "img" -> {
                int[] size = imageSize(n);
                r[0] = Math.max(r[0], size[0]);
                r[2] += size[0];
                return;
            }
            case "table" -> {
                if (top) break;
                endLine(r);
                List<Html.Node> rows = rows(n);
                if (closed(n) && !rows.isEmpty()) rows = rows.subList(0, 1);
                int[] count = new int[1];
                List<Cell> cells = grid(rows, count);
                int[][] sizes = columnWidths(cells, count[0]);
                int min = 0, max = 0;
                for (int i = 0; i < count[0]; i++) {
                    min += sizes[0][i];
                    max += sizes[1][i];
                }
                r[0] = Math.max(r[0], min);
                r[1] = Math.max(r[1], max);
                return;
            }
            default -> {
            }
        }
        if (n.hasClass("invslot")) {
            r[0] = Math.max(r[0], SLOT);
            r[2] += SLOT;
            return;
        }
        if (n.hasClass("mcui")) {
            endLine(r);
            Html.Node input = n.find(null, "mcui-input");
            int cols = 0;
            if (input != null) {
                List<Html.Node> rows = new ArrayList<>();
                input.findAll(null, "mcui-row", rows);
                for (Html.Node row : rows) {
                    List<Html.Node> slots = new ArrayList<>();
                    row.findAll(null, "invslot", slots);
                    cols = Math.max(cols, slots.size());
                }
            }
            int w = cols * SLOT + (n.find(null, "mcui-output") == null ? 0 : 24 + SLOT) + 10;
            r[0] = Math.max(r[0], w);
            r[1] = Math.max(r[1], w);
            return;
        }
        S s = styled(n, n.tag.equals("b") || n.tag.equals("strong") || n.tag.equals("th") ? style.asBold() : n.tag.equals("i") || n.tag.equals("em") ? style.asItalic() : style);
        boolean inline = INLINE.contains(n.tag) || n.tag.equals("#root");
        if (!inline) endLine(r);
        int indent = n.tag.equals("li") ? 12 : 0;
        r[2] += indent;
        for (Html.Node c : n.children) walk(c, r, s, false);
        if (!inline) endLine(r);
    }

    // every word on the page lowercase with where it is, for find in page
    public List<int[]> find(String text) {
        List<int[]> out = new ArrayList<>();
        if (text.isEmpty()) return out;
        String q = text.toLowerCase(Locale.ROOT);
        for (Op op : ops) {
            if (op.kind != Kind.TEXT || op.text == null) continue;
            String plain = op.text.replace("§l", "").replace("§o", "").toLowerCase(Locale.ROOT);
            int at = 0;
            while ((at = plain.indexOf(q, at)) >= 0) {
                out.add(new int[]{ops.indexOf(op), at});
                at += q.length();
            }
        }
        return out;
    }
}
