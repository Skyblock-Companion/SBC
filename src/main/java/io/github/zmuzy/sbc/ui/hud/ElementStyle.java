// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.hud;

import io.github.zmuzy.sbc.core.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// per element look on top of the theme: bg color + opacity, border, title/text colors, padding, hidden rows.
// all optional, unset = theme decides. saved per element
public final class ElementStyle {
    private final Map<String, Object> m;

    private ElementStyle(Map<String, Object> m) {
        this.m = m;
    }

    // changes when any setting of this style does, so a card can cache till then
    public int stamp() {
        return m.hashCode();
    }

    private static String key(String elementId) {
        return io.github.zmuzy.sbc.ui.theme.Theme.current().id() + ":" + elementId;
    }

    // live view, changes get saved on Config.save
    public static ElementStyle of(String elementId) {
        return new ElementStyle(Config.I.hudStyles.computeIfAbsent(key(elementId), k -> new HashMap<>()));
    }

    public static boolean has(String elementId) {
        Map<String, Object> s = Config.I.hudStyles.get(key(elementId));
        return s != null && !s.isEmpty();
    }

    public static void reset(String elementId) {
        Config.I.hudStyles.remove(key(elementId));
    }

    public boolean isEmpty() {
        return m.isEmpty();
    }

    private Integer intOr(String k) {
        Object v = m.get(k);
        return v instanceof Number n ? (int) n.longValue() : null;
    }

    private Float floatOr(String k) {
        Object v = m.get(k);
        return v instanceof Number n ? n.floatValue() : null;
    }

    private Boolean boolOr(String k) {
        Object v = m.get(k);
        return v instanceof Boolean b ? b : null;
    }

    private void put(String k, Object v) {
        if (v == null) m.remove(k);
        else m.put(k, v);
    }

    // bg
    public Integer bg() { return intOr("bg"); }
    public void bg(Integer argb) { put("bg", argb); }
    public Float alpha() { return floatOr("alpha"); }
    public void alpha(Float a) { put("alpha", a); }
    public Boolean border() { return boolOr("border"); }
    public void border(Boolean b) { put("border", b); }
    public Integer borderColor() { return intOr("borderColor"); }
    public void borderColor(Integer c) { put("borderColor", c); }
    public Integer titleColor() { return intOr("titleColor"); }
    public void titleColor(Integer c) { put("titleColor", c); }
    public Integer textColor() { return intOr("textColor"); }
    public void textColor(Integer c) { put("textColor", c); }
    public Integer valueColor() { return intOr("valueColor"); }
    public void valueColor(Integer c) { put("valueColor", c); }
    public Boolean showTitle() { return boolOr("showTitle"); }
    public void showTitle(Boolean b) { put("showTitle", b); }
    public Boolean shadow() { return boolOr("shadow"); }
    public void shadow(Boolean b) { put("shadow", b); }
    public Integer padding() { return intOr("padding"); }
    public void padding(Integer p) { put("padding", p); }
    public Integer rowH() { return intOr("rowH"); }
    public void rowH(Integer h) { put("rowH", h); }

    // rows you hid
    @SuppressWarnings("unchecked")
    public List<String> hiddenRows() {
        Object v = m.get("hidden");
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return new ArrayList<>();
    }

    public boolean isHidden(String rowLabel) {
        return hiddenRows().contains(rowLabel);
    }

    public void setHidden(String rowLabel, boolean hidden) {
        List<String> l = hiddenRows();
        if (hidden && !l.contains(rowLabel)) l.add(rowLabel);
        if (!hidden) l.remove(rowLabel);
        put("hidden", l.isEmpty() ? null : l);
    }

    // override else the themes
    public static int or(Integer v, int fallback) {
        return v == null ? fallback : v;
    }
}
