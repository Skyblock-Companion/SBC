// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.carnival;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.core.data.DataFiles;
import io.github.zmuzy.sbc.events.EventData;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni CarnivalShopHelper: whats left to buy in the carnival shops and how many tokens
// skyhanni swaps an item into the chest, here its a panel next to it. costs from neu carnivalshops.json
public final class CarnivalShop {
    public static final String ID = "carnival_shop";
    private static final int FIRST_SLOT = 11, LAST_SLOT = 15, FOOTER_SLOT = 32;

    record Upgrade(String name, int[] costs) {}

    record Status(String name, int level, int max, long remaining) {}

    private static final Map<String, List<Upgrade>> SHOPS = new LinkedHashMap<>();
    private static boolean loaded;
    private static final Panel PANEL = new Panel();

    private CarnivalShop() {}

    private static void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = DataFiles.neu("carnivalshops.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("carnivalTokenShops");
            for (Map.Entry<String, JsonElement> shop : root.entrySet()) {
                List<Upgrade> ups = new ArrayList<>();
                for (Map.Entry<String, JsonElement> u : shop.getValue().getAsJsonObject().entrySet()) {
                    JsonObject up = u.getValue().getAsJsonObject();
                    var a = up.getAsJsonArray("costs");
                    int[] costs = new int[a.size()];
                    for (int i = 0; i < costs.length; i++) costs[i] = a.get(i).getAsInt();
                    ups.add(new Upgrade(up.get("name").getAsString(), costs));
                }
                SHOPS.put(shop.getKey().replace("_", " "), ups);
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the Carnival shops", e);
        }
    }

    static Map<String, List<Upgrade>> shops() {
        load();
        return SHOPS;
    }

    static String shopOf(String title) {
        for (String name : shops().keySet()) if (name.equalsIgnoreCase(title)) return name;
        return null;
    }

    // EssenceUtils.extractPurchasedUpgrades over slots 11-15
    static Map<String, Integer> purchased(MenuView v) {
        Map<String, Integer> out = new HashMap<>();
        var upgrade = Patterns.get("SH.inventory.essence-shop-helper.essence.upgrade");
        var maxed = Patterns.get("SH.inventory.essence-shop-helper.essence.maxedupgrade");
        for (int slot = FIRST_SLOT; slot <= LAST_SLOT; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            Matcher m = upgrade.matcher(it.name);
            if (!m.matches() || m.group("upgrade") == null || m.group("tier") == null || m.group("tier").isEmpty()) continue;
            int next = GameState.roman(m.group("tier"));
            out.put(m.group("upgrade"), it.first(maxed) != null ? next : next - 1);
        }
        return out;
    }

    // EventShopProgress.remainingUpgrades
    static List<Status> remaining(String shop, Map<String, Integer> bought) {
        List<Status> out = new ArrayList<>();
        for (Upgrade u : shops().getOrDefault(shop, List.of())) {
            int have = bought.getOrDefault(u.name(), 0);
            long left = 0;
            for (int i = have; i < u.costs().length; i++) left += u.costs()[i];
            out.add(new Status(u.name(), have, u.costs().length, left));
        }
        return out;
    }

    // processTokenShopFooter, -1 if the menu doesnt say
    static int tokens(MenuView v) {
        MenuView.Item it = v.item(FOOTER_SLOT);
        if (!it.clean.equals("Carnival Tokens")) return -1;
        Matcher m = it.firstClean(io.github.zmuzy.sbc.events.EventPack.plain("SH.inventory.carnival-shop-helper.carnival.tokens.current"));
        if (m == null) return -1;
        String t = m.group("tokens").replace(",", "");
        return t.isEmpty() ? 0 : Integer.parseInt(t);
    }

    private static final class Panel implements SideHelper.Content {
        private String shop;
        private List<Status> left = List.of();
        // on the overview: each shop + what it still takes
        private final Map<String, Long> overview = new LinkedHashMap<>();
        private long sum;
        private int tokens = -1;

        @Override
        public String title() {
            return shop == null ? "Event shops" : shop;
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("name_tag");
        }

        @Override
        public int accent() {
            return 0xFF55FFFF;
        }

        @Override
        public String[] tabs() {
            return new String[]{shop == null ? "Shops " + overview.size() : "Left to buy " + left.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            MenuView v = MenuMarks.current();
            if (v == null) return;
            shop = shopOf(v.title);
            EventData.ProfileEvents stored = EventData.profile();
            if (shop != null) {
                Map<String, Integer> bought = purchased(v);
                stored.carnivalShops.put(shop, bought);
                Store.markDirty();
                List<Status> open = new ArrayList<>();
                sum = 0;
                for (Status s : remaining(shop, bought)) {
                    if (s.remaining() <= 0) continue;
                    open.add(s);
                    sum += s.remaining();
                }
                left = open;
                tokens = tokens(v);
                return;
            }
            overview.clear();
            sum = 0;
            for (String name : shops().keySet()) {
                Map<String, Integer> bought = stored.carnivalShops.get(name);
                if (bought == null) {
                    overview.put(name, -1L);
                    continue;
                }
                long t = 0;
                for (Status s : remaining(name, bought)) t += s.remaining();
                overview.put(name, t);
                sum += t;
            }
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            SideHelper.tile(ui, x, y, half, "Tokens needed", String.format(Locale.ROOT, "%,d", sum), "", 0xFF55FFFF);
            SideHelper.tile(ui, x + half + 3, y, w - half - 3, "Tokens you have", tokens < 0 ? "-" : String.format(Locale.ROOT, "%,d", tokens), "", Modern.INK);
            int ry = y + 26;
            // what the list under it is, in words
            ui.textShadow(ui.ellipsize("§7What each upgrade here still costs", w), x, ry, Modern.DIM);
            ry += 10;
            if (tokens >= 0 && sum > 0) {
                Modern.meter(ui, x, ry, w, 2, Math.min(1, tokens / (double) sum), 0xFF55FFFF);
                ry += 4;
                if (sum > tokens) {
                    Modern.row(ui, "Still needed", "§f" + String.format(Locale.ROOT, "%,d", sum - tokens), x, ry, w, Modern.LABEL, Modern.INK);
                    ry += 10;
                } else {
                    ui.textShadow("§aYou have enough tokens.", x, ry, Modern.GOOD);
                    ry += 10;
                }
            }
            return ry - y;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            if (shop == null) {
                for (Map.Entry<String, Long> e : overview.entrySet()) {
                    String right = e.getValue() < 0 ? "§cOpen it" : e.getValue() == 0 ? "§aAll bought" : "§7" + String.format(Locale.ROOT, "%,d", e.getValue());
                    int rw = ui.width(right);
                    ui.textShadow(ui.ellipsize("§f" + e.getKey(), w - rw - 6), x + 2, y + 1, Modern.LABEL);
                    ui.textShadow(right, x + w - rw - 2, y + 1, Modern.INK);
                    y += 11;
                }
                return y - y0;
            }
            for (Status s : left) {
                String right = "§7" + String.format(Locale.ROOT, "%,d", s.remaining());
                int rw = ui.width(right);
                ui.textShadow(ui.ellipsize("§a" + s.name() + " §b" + s.level() + "§7/§b" + s.max(), w - rw - 6), x + 2, y + 1, Modern.LABEL);
                ui.textShadow(right, x + w - rw - 2, y + 1, Modern.INK);
                y += 11;
            }
            if (left.isEmpty()) y += SideHelper.emptyText(ui, "All upgrades bought.", x, y, w);
            return y - y0;
        }
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID) || s == null) return null;
        String title = GameState.strip(s.getTitle().getString()).trim();
        if (shopOf(title) != null || Patterns.get("SH.inventory.carnival-shop-helper.carnival.overviewinventories").matcher(title).matches()) return PANEL;
        return null;
    }

    static void register() {
        Features.add(new Feature(ID, "Events", "Event shop helper", "Beside the Carnival's event shops, what you have left to buy and how many tokens that takes.", "SkyHanni: Token Shop Helper")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        SideHelper.add(CarnivalShop::panel);
    }
}
