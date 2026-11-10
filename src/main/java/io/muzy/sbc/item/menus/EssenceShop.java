// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.item.market.Market;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
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
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;

// from skyhanni EssenceShopHelper: whats left to buy in an essence shop + what that essence costs
// skyhanni swaps an item into the chest for this, here its a panel next to the chest and the bz lookup is a
// button on it. costs from neu essenceshops.json
public final class EssenceShop {
    // one upgrade: name + cost per level
    private record Upgrade(String name, int[] costs) {}

    record Status(String name, int level, int max, long remaining) {}

    private static final Map<String, List<Upgrade>> SHOPS = new LinkedHashMap<>();
    private static boolean loaded;
    private static final Panel PANEL = new Panel();

    private EssenceShop() {}

    private static void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = io.muzy.sbc.core.data.DataFiles.neu("essenceshops.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> shop : o.entrySet()) {
                List<Upgrade> ups = new ArrayList<>();
                for (Map.Entry<String, JsonElement> u : shop.getValue().getAsJsonObject().entrySet()) {
                    JsonObject up = u.getValue().getAsJsonObject();
                    var a = up.getAsJsonArray("costs");
                    int[] costs = new int[a.size()];
                    for (int i = 0; i < costs.length; i++) costs[i] = a.get(i).getAsInt();
                    ups.add(new Upgrade(up.get("name").getAsString(), costs));
                }
                SHOPS.put(shop.getKey(), ups);
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the essence shops", e);
        }
    }

    // skyhanni EssenceUtils.extractPurchasedUpgrades over slots 10 to 33
    static Map<String, Integer> purchased(MenuView v) {
        Map<String, Integer> out = new HashMap<>();
        var upgrade = Patterns.get("SH.inventory.essence-shop-helper.essence.upgrade");
        var maxed = Patterns.get("SH.inventory.essence-shop-helper.essence.maxedupgrade");
        for (int slot = 10; slot <= 33; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            Matcher m = upgrade.matcher(it.name);
            if (!m.matches() || m.group("upgrade") == null || m.group("tier") == null || m.group("tier").isEmpty()) continue;
            int next = GameState.roman(m.group("tier"));
            out.put(m.group("upgrade"), it.first(maxed) != null ? next : next - 1);
        }
        return out;
    }

    static int owned(MenuView v) {
        Matcher m = v.item(4).first(Patterns.get("SH.inventory.essence-shop-helper.essence.current"));
        if (m == null || m.group("count").isEmpty()) return 0;
        return Integer.parseInt(m.group("count").replace(",", ""));
    }

    static String type(String title) {
        Matcher m = Patterns.get("SH.inventory.essence-shop-helper.essence.shop").matcher(title);
        return m.matches() ? m.group("essence") : null;
    }

    // whats left of each upgrade in this shop, null if the shop isnt known
    static List<Status> remaining(String type, Map<String, Integer> bought) {
        load();
        List<Upgrade> shop = SHOPS.get("ESSENCE_" + type.toUpperCase(Locale.ROOT));
        if (shop == null) return null;
        List<Status> out = new ArrayList<>();
        for (Upgrade u : shop) {
            int level = bought.getOrDefault(u.name(), 0);
            long left = 0;
            for (int i = level; i < u.costs().length; i++) left += u.costs()[i];
            out.add(new Status(u.name(), level, u.costs().length, left));
        }
        return out;
    }

    // skyhanni nonRepoUpgrades: names the shop shows that the repo doesnt know
    static List<String> notInRepo(String type, Map<String, Integer> bought) {
        load();
        List<Upgrade> shop = SHOPS.get("ESSENCE_" + type.toUpperCase(Locale.ROOT));
        List<String> out = new ArrayList<>();
        if (shop == null) return out;
        for (String name : bought.keySet()) {
            boolean known = false;
            for (Upgrade u : shop) if (u.name().equalsIgnoreCase(name)) known = true;
            if (!known) out.add(name);
        }
        return out;
    }

    private static final class Panel implements SideHelper.Content {
        private String type = "";
        private List<Status> left = List.of();
        private List<String> unknown = List.of();
        private long sum, needed;
        private int have;

        @Override
        public String title() {
            return type.isEmpty() ? "Essence Shop" : type + " Essence";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("gold_nugget");
        }

        @Override
        public int accent() {
            return 0xFFE6B8FF;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Upgrades left " + left.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            MenuView v = MenuMarks.current();
            if (v == null) return;
            String t = type(v.title);
            if (t == null) return;
            type = t;
            Map<String, Integer> bought = purchased(v);
            List<Status> all = remaining(t, bought);
            if (all == null) all = List.of();
            List<Status> open = new ArrayList<>();
            sum = 0;
            for (Status s : all) {
                if (s.remaining() <= 0) continue;
                open.add(s);
                sum += s.remaining();
            }
            left = open;
            unknown = notInRepo(t, bought);
            have = owned(v);
            needed = sum - have;
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            String item = "ESSENCE_" + type.toUpperCase(Locale.ROOT);
            Prices.Product p = Prices.get(item);
            String essence = (type.isEmpty() ? "" : type + " ") + "Essence";
            SideHelper.tile(ui, x, y, half, essence + " needed", String.format(Locale.ROOT, "%,d", sum), "", 0xFFE6B8FF);
            SideHelper.tile(ui, x + half + 3, y, w - half - 3, essence + " you have", String.format(Locale.ROOT, "%,d", have), "", Modern.INK);
            int ry = y + 26;
            Modern.meter(ui, x, ry, w, 2, sum == 0 ? 1 : Math.min(1, have / (double) sum), 0xFFE6B8FF);
            ry += 4;
            if (needed > 0) {
                Modern.row(ui, "Still needed", "§f" + String.format(Locale.ROOT, "%,d", needed), x, ry, w, Modern.LABEL, Modern.INK);
                ry += 10;
                if (p != null) {
                    Modern.row(ui, "Buy now", "§6" + Prices.coins(p.buy() * needed), x, ry, w, Modern.LABEL, Modern.INK);
                    ry += 10;
                    Modern.row(ui, "Buy order", "§6" + Prices.coins(p.sell() * needed), x, ry, w, Modern.LABEL, Modern.INK);
                    ry += 10;
                }
                boolean hv = ui.hover(x, ry, w, 12);
                ui.rect(x, ry, w, 12, hv ? 0x44FFFFFF : 0x22FFFFFF);
                ui.textShadow("§eOpen on the Bazaar", x + 3, ry + 2, Modern.INK);
                ui.click(x, ry, w, 12, () -> {
                    MenuHighlights.bazaar(type + " Essence", (int) needed);
                });
                ry += 14;
            } else if (sum > 0) {
                ui.textShadow("§aYou have enough essence.", x, ry, Modern.GOOD);
                ry += 10;
            }
            return ry - y;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (Status s : left) {
                String right = "§7" + Prices.coins(s.remaining());
                int rw = ui.width(right);
                ui.textShadow(ui.ellipsize("§a" + s.name() + " §b" + s.level() + "§7/§b" + s.max(), w - rw - 6), x + 2, y + 1, Modern.LABEL);
                ui.textShadow(right, x + w - rw - 2, y + 1, Modern.INK);
                y += 11;
            }
            if (left.isEmpty()) y += SideHelper.emptyText(ui, "All upgrades bought.", x, y, w);
            if (!unknown.isEmpty()) {
                ui.textShadow("§cNot in the list yet:", x + 2, y + 2, Modern.DIM);
                y += 12;
                for (String name : unknown) {
                    ui.textShadow(ui.ellipsize("§4" + name, w - 6), x + 4, y, Modern.DIM);
                    y += 10;
                }
            }
            return y - y0;
        }
    }

    static SideHelper.Content panelForTest() {
        return PANEL;
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on("essence_shop")) return null;
        String t = type(GameState.strip(s.getTitle().getString()).trim());
        if (t == null) return null;
        load();
        return SHOPS.containsKey("ESSENCE_" + t.toUpperCase(Locale.ROOT)) ? PANEL : null;
    }

    static void register(BooleanSupplier skyblock) {
        Features.add(new Feature("essence_shop", "Menus", "Essence Shop helper", "Beside an Essence Shop: what is left to buy, how much essence that takes, and what it costs on the Bazaar.", "SkyHanni: Essence Shop Helper").when(skyblock));
        SideHelper.add(EssenceShop::panel);
    }
}
