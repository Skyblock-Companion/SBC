// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// finds a spot for every corner card, no overlaps, nothing off screen
// a card goes on its own side till that side hits ~half the screen and is taller than the other, then it spills over
final class HudPacker {
    static final int GAP = 3, EDGE = 2;
    // half width of the middle strip only a first column can reach into
    static final int CENTRE = 70;
    private static final int MAX_DEPTH = 4;
    private static final double BALANCE = 0.5;

    static final class Box {
        final String id;
        final boolean right, stretch;
        int w;
        final int h;
        int x, y;
        boolean fits, stretched;
        private Column column;

        Box(String id, boolean right, int w, int h) {
            this(id, right, w, h, false);
        }

        Box(String id, boolean right, int w, int h, boolean stretch) {
            this.id = id;
            this.right = right;
            this.w = w;
            this.h = h;
            this.stretch = stretch;
        }
    }

    record Rect(int x, int y, int w, int h) {
        boolean hits(int ox, int oy, int ow, int oh) {
            return ox < x + w && x < ox + ow && oy < y + h && y < oy + oh;
        }
    }

    private static final class Column {
        final boolean right;
        final int depth, edge;
        int nextY, width;

        Column(boolean right, int depth, int edge, int top) {
            this.right = right;
            this.depth = depth;
            this.edge = edge;
            this.nextY = top;
        }

        int xFor(int w) {
            return right ? edge - w : edge;
        }

        int code() {
            return right ? -depth - 1 : depth;
        }
    }

    private final int sw, sh, leftTop, rightTop;
    private final List<Rect> taken;
    private final Map<Integer, Column> columns = new HashMap<>();

    HudPacker(int sw, int sh, int leftEdge, int leftTop, int rightEdge, int rightTop, List<Rect> taken) {
        this.sw = sw;
        this.sh = sh;
        this.leftTop = leftTop;
        this.rightTop = rightTop;
        this.taken = new ArrayList<>(taken);
        columns.put(0, new Column(false, 0, leftEdge, leftTop));
        columns.put(-1, new Column(true, 0, rightEdge, rightTop));
    }

    private Column column(boolean right, int depth) {
        int code = right ? -depth - 1 : depth;
        Column c = columns.get(code);
        if (c != null) return c;
        Column before = column(right, depth - 1);
        int edge = right ? before.edge - before.width - GAP : before.edge + before.width + GAP;
        c = new Column(right, depth, edge, right ? rightTop : leftTop);
        columns.put(code, c);
        return c;
    }

    // -1 = doesnt fit here
    private int fit(Column c, Box b) {
        int x = c.xFor(b.w);
        if (x < 0 || x + b.w > sw) return -1;
        if (c.depth > 0 && x < sw / 2 + CENTRE && x + b.w > sw / 2 - CENTRE) return -1;
        int y = c.nextY;
        while (true) {
            if (y + b.h > sh - EDGE) return -1;
            // same gap to everything around it, not just what is above in its own column
            Rect hit = null;
            for (Rect r : taken) if (r.hits(x - GAP, y - GAP, b.w + 2 * GAP, b.h + 2 * GAP)) {
                hit = r;
                break;
            }
            if (hit == null) return y;
            y = hit.y() + hit.h() + GAP;
        }
    }

    private void put(Column c, Box b, int y) {
        b.x = c.xFor(b.w);
        b.y = y;
        b.fits = true;
        b.column = c;
        c.nextY = y + b.h + GAP;
        c.width = Math.max(c.width, b.w);
        taken.add(new Rect(b.x, b.y, b.w, b.h));
    }

    // sticky = the column each card was in last time (read + updated, can be null). false = something didnt find
    // a free spot, its still kept on screen just overlapping
    boolean pack(List<Box> boxes, Map<String, Integer> sticky) {
        boolean all = true;
        // remembered cards first, shallow columns before deep ones, so a deeper column starts right of the final
        // width of the one before it
        if (sticky != null) for (int depth = 0; depth < MAX_DEPTH; depth++)
            for (Box b : boxes) if (b.column == null && depthOf(sticky.get(b.id)) == depth) putRemembered(b, sticky);
        // overflow from the right waits for the left sides own cards, top left corner stays theirs
        List<Box> overflow = new ArrayList<>();
        for (Box b : boxes) {
            if (b.column != null) continue;
            if (b.right) {
                Column own = column(true, 0);
                int at = fit(own, b);
                if (at < 0) {
                    overflow.add(b);
                    continue;
                }
                put(own, b, at);
                if (sticky != null) sticky.put(b.id, own.code());
                continue;
            }
            all &= place(b, sticky);
            for (Box o : overflow) all &= place(o, sticky);
            overflow.clear();
        }
        for (Box o : overflow) all &= place(o, sticky);
        for (Box b : boxes) stretch(b);
        return all;
    }

    // -1 = not remembered
    private static int depthOf(Integer code) {
        if (code == null) return -1;
        return Math.min(code < 0 ? -code - 1 : code, MAX_DEPTH - 1);
    }

    // stay in the last column if it still fits
    private boolean putRemembered(Box b, Map<String, Integer> sticky) {
        Integer last = sticky == null ? null : sticky.get(b.id);
        if (last == null) return false;
        Column c = column(last < 0, depthOf(last));
        int at = fit(c, b);
        if (at < 0) return false;
        put(c, b, at);
        sticky.put(b.id, c.code());
        return true;
    }

    // false = no column fits, only kept on screen
    private boolean place(Box b, Map<String, Integer> sticky) {
        for (int depth = 0; depth < MAX_DEPTH; depth++) {
            // left stuff crosses over once the left is taller and past the middle, so one side doesnt carry everything.
            // the right sides own cards (map, scoreboard) stay put
            boolean across = depth == 0 && !b.right && column(false, 0).nextY + b.h > sh * BALANCE && column(true, 0).nextY < column(false, 0).nextY;
            for (boolean right : across ? new boolean[]{!b.right, b.right} : new boolean[]{b.right, !b.right}) {
                Column c = column(right, depth);
                int at = fit(c, b);
                if (at < 0) continue;
                put(c, b, at);
                if (sticky != null) sticky.put(b.id, c.code());
                return true;
            }
        }
        Column own = column(b.right, 0);
        b.x = Math.max(0, Math.min(sw - b.w, own.xFor(b.w)));
        b.y = Math.max(0, Math.min(sh - EDGE - b.h, own.nextY));
        b.fits = false;
        taken.add(new Rect(b.x, b.y, b.w, b.h));
        return false;
    }

    // full column width unless something is in the way
    private void stretch(Box b) {
        if (!b.stretch || b.column == null || b.w >= b.column.width) return;
        int width = b.column.width, x = b.column.xFor(width);
        Rect own = new Rect(b.x, b.y, b.w, b.h);
        for (Rect r : taken) if (!r.equals(own) && r.hits(x, b.y, width, b.h)) return;
        taken.remove(own);
        b.x = x;
        b.w = width;
        b.stretched = true;
        taken.add(new Rect(b.x, b.y, b.w, b.h));
    }

    // y under the first column of a side, its top when empty
    int bottom(boolean right) {
        return column(right, 0).nextY - GAP;
    }
}
