// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small forgiving html parser: builds an element tree the page reader walks (unclosed tags get closed for it)
public final class Html {
    public static final class Node {
        public final String tag; // "#text" for text
        public final Map<String, String> attrs;
        public final List<Node> children = new ArrayList<>();
        public final String text;
        Node parent;

        Node(String tag, Map<String, String> attrs, String text) {
            this.tag = tag;
            this.attrs = attrs;
            this.text = text;
        }

        public Node parent() {
            return parent;
        }

        public boolean isText() {
            return text != null;
        }

        public String attr(String k) {
            return attrs == null ? null : attrs.get(k);
        }

        public boolean hasClass(String c) {
            String cl = attr("class");
            if (cl == null) return false;
            for (String k : cl.split("\\s+")) if (k.equals(c)) return true;
            return false;
        }

        public boolean classContains(String part) {
            String cl = attr("class");
            return cl != null && cl.contains(part);
        }

        // all text under this node, whitespace collapsed
        public String text() {
            StringBuilder b = new StringBuilder();
            collect(this, b);
            return b.toString().replaceAll("\\s+", " ").trim();
        }

        private static void collect(Node n, StringBuilder b) {
            if (n.isText()) {
                b.append(n.text);
                return;
            }
            if (n.tag.equals("br")) b.append(' ');
            for (Node c : n.children) collect(c, b);
            if (BLOCK.contains(n.tag)) b.append(' ');
        }

        // first descendant (depth first) matching a tag + optional class
        public Node find(String tag, String cls) {
            for (Node c : children) {
                if (c.isText()) continue;
                if ((tag == null || c.tag.equals(tag)) && (cls == null || c.hasClass(cls))) return c;
                Node f = c.find(tag, cls);
                if (f != null) return f;
            }
            return null;
        }

        public void findAll(String tag, String cls, List<Node> out) {
            for (Node c : children) {
                if (c.isText()) continue;
                if ((tag == null || c.tag.equals(tag)) && (cls == null || c.hasClass(cls))) out.add(c);
                c.findAll(tag, cls, out);
            }
        }
    }

    static final Set<String> VOID = Set.of("br", "img", "hr", "meta", "link", "input", "source", "wbr", "col", "area", "base", "embed", "param", "track");
    static final Set<String> BLOCK = Set.of("p", "div", "li", "tr", "td", "th", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "table", "dd", "dt",
            "blockquote", "section", "figure", "figcaption", "center");
    private static final Set<String> RAW = Set.of("script", "style");
    private static final dev.zmuzy.sbc.core.Patterns.P TOKEN = dev.zmuzy.sbc.core.Patterns.p("Html.TOKEN");
    private static final dev.zmuzy.sbc.core.Patterns.P ATTR = dev.zmuzy.sbc.core.Patterns.p("Html.ATTR");

    private Html() {}

    public static Node parse(String html) {
        Node root = new Node("#root", Map.of(), null);
        Node cur = root;
        Matcher m = TOKEN.matcher(html);
        while (m.find()) {
            if (m.group(4) != null) {
                cur.children.add(text(m.group(4), cur));
                continue;
            }
            if (m.group(2) == null) continue; // comment or stray "<"
            String tag = m.group(2).toLowerCase(Locale.ROOT);
            boolean close = !m.group(1).isEmpty();
            String rawAttrs = m.group(3) == null ? "" : m.group(3);
            if (close) {
                // close the nearest open element with this tag (+ anything still open inside it)
                for (Node n = cur; n != null && n != root; n = n.parent)
                    if (n.tag.equals(tag)) {
                        cur = n.parent;
                        break;
                    }
                continue;
            }
            // a new <p>/<li>/<td>/<tr> sibling closes the last one
            if (tag.equals("li") || tag.equals("td") || tag.equals("th") || tag.equals("tr") || tag.equals("p")) cur = autoClose(cur, tag, root);
            Node n = new Node(tag, attrs(rawAttrs), null);
            n.parent = cur;
            cur.children.add(n);
            if (RAW.contains(tag)) {
                int end = html.toLowerCase(Locale.ROOT).indexOf("</" + tag, m.end());
                if (end < 0) break;
                m.region(end, html.length());
                continue;
            }
            if (!VOID.contains(tag) && !rawAttrs.endsWith("/")) cur = n;
        }
        return root;
    }

    private static Node autoClose(Node cur, String tag, Node root) {
        Set<String> stops = switch (tag) {
            case "li" -> Set.of("ul", "ol");
            case "td", "th" -> Set.of("tr", "table");
            case "tr" -> Set.of("table", "tbody", "thead", "tfoot");
            default -> Set.of("div", "td", "th", "li", "body", "section");
        };
        for (Node n = cur; n != null && n != root; n = n.parent) {
            if (stops.contains(n.tag)) return cur;
            if (n.tag.equals(tag) || (tag.equals("td") && n.tag.equals("th")) || (tag.equals("th") && n.tag.equals("td"))) return n.parent;
        }
        return cur;
    }

    private static Node text(String raw, Node parent) {
        Node t = new Node("#text", null, entities(raw));
        t.parent = parent;
        return t;
    }

    private static Map<String, String> attrs(String raw) {
        Map<String, String> out = new HashMap<>();
        Matcher a = ATTR.matcher(raw);
        while (a.find()) {
            String v = a.group(3) != null ? a.group(3) : a.group(4) != null ? a.group(4) : a.group(5) != null ? a.group(5) : "";
            out.put(a.group(1).toLowerCase(Locale.ROOT), entities(v));
        }
        return out;
    }

    // html entities -> text, and the wikis private use icon glyphs (mc cant draw them) removed
    public static String entities(String s) {
        s = s.replaceAll("[\uE000-\uF8FF]", "");
        if (s.indexOf('&') < 0) return s;
        Matcher m = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String e = m.group(1), r;
            try {
                if (e.startsWith("#x") || e.startsWith("#X")) r = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                else if (e.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                else r = switch (e) {
                    case "amp" -> "&";
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "quot" -> "\"";
                    case "apos" -> "'";
                    case "nbsp" -> " ";
                    case "ndash" -> "–";
                    case "mdash" -> "—";
                    case "hellip" -> "…";
                    case "times" -> "×";
                    case "rarr" -> "→";
                    case "larr" -> "←";
                    default -> m.group();
                };
            } catch (Exception ex) {
                r = "";
            }
            if (r.length() == 1 && r.charAt(0) >= '\uE000' && r.charAt(0) <= '\uF8FF') r = "";
            m.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }
}
