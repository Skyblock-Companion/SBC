// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.garden.visitor;

import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.garden.GardenData;
import io.github.zmuzy.sbc.item.menus.MenuView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// hypixels Visitor's Logbook ("(2/6) Visitor's Logbook"), one head per visitor with all time visits + accepted
// the stats card trusts it over our own count
public final class VisitorLogbook {
    private static final Pattern TITLE = Pattern.compile("\\((\\d+)/(\\d+)\\) Visitor's Logbook");
    private static final Pattern VISITED = Pattern.compile("Times Visited: ([\\d,]+)"), ACCEPTED = Pattern.compile("Offers Accepted: ([\\d,]+)");

    private VisitorLogbook() {}

    // the page a title names, 0 when it is not the logbook
    static int page(String title) {
        Matcher m = TITLE.matcher(title.trim());
        return m.matches() ? Integer.parseInt(m.group(1)) : 0;
    }

    static int pages(String title) {
        Matcher m = TITLE.matcher(title.trim());
        return m.matches() ? Integer.parseInt(m.group(2)) : 0;
    }

    static void read(MenuView v) {
        int page = page(v.title);
        if (page == 0) return;
        GardenData d = GardenData.get();
        boolean changed = false;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty() || !it.model.contains("head") && !it.model.contains("spawn_egg")) continue;
            Integer visited = null, accepted = null;
            for (String l : it.cleanLore) {
                Matcher a = VISITED.matcher(l.trim());
                if (a.matches()) visited = Integer.parseInt(a.group(1).replace(",", ""));
                Matcher b = ACCEPTED.matcher(l.trim());
                if (b.matches()) accepted = Integer.parseInt(b.group(1).replace(",", ""));
            }
            if (visited == null || accepted == null) continue;
            int[] now = {visited, accepted};
            int[] old = d.logbook.get(it.clean);
            if (old == null || old[0] != now[0] || old[1] != now[1]) {
                d.logbook.put(it.clean, now);
                changed = true;
            }
        }
        if (d.logbookPages.add(page)) changed = true;
        int count = pages(v.title);
        if (count != d.logbookPageCount) {
            d.logbookPageCount = count;
            changed = true;
        }
        if (changed) Store.markDirty();
    }
}
