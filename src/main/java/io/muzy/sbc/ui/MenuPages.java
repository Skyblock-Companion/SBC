// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

// which page code draws which tab
final class MenuPages {
    static final int SETUP = -1, TAB_COUNT = 13, SETTINGS = 11;

    private MenuPages() {}

    // returns height
    static int paint(int tab, MenuUi ui, int x, int y, int w, int viewH) {
        return switch (tab) {
            case 0 -> PagesMain.dashboard(ui, x, y, w);
            case 1 -> PagesMain.routine(ui, x, y, w);
            case 2 -> PagesMain.calendar(ui, x, y, w);
            case 3 -> PagesPlan.planner(ui, x, y, w);
            case 4 -> PagesPlan.money(ui, x, y, w);
            case 5 -> PagesPlan.level(ui, x, y, w);
            case 6 -> PagesPlan.minions(ui, x, y, w);
            case 7 -> PagesMore.profile(ui, x, y, w);
            case 8 -> PagesMore.navigate(ui, x, y, w);
            case 9 -> PagesMap.map(ui, x, y, w);
            case 10 -> PagesMore.stats(ui, x, y, w);
            case 12 -> PagesPlan.forge(ui, x, y, w);
            default -> {
                PagesMap.setup(ui, x, y, w, viewH);
                yield viewH;
            }
        };
    }
}
