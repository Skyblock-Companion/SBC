// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.sacks;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// from skyhanni OutsideSackValue: what your sacks are worth without opening them (instant sell to instant
// buy) and what makes most of it
// skyhanni groups lines by sack from its repo sack list, we dont have that so its just the items. only bazaar
// items count, same as theirs
public final class OutsideSackValue {
    public static final String ID = "outside_sack_value";
    private static final int SHOWN = 5;
    private static long updatedAt;
    private static RichCard.Content card = new RichCard.Content("", List.of());

    private OutsideSackValue() {}

    // one priced sack item
    record Priced(String name, int amount, double min, double max) {}

    private static String range(double min, double max) {
        return "§6" + Prices.coins(min) + "§7-§6" + Prices.coins(max);
    }

    // skyhanni calculateData as card lines
    static RichCard.Content content(List<Priced> items) {
        double min = 0, max = 0;
        long amount = 0;
        for (Priced p : items) {
            min += p.min();
            max += p.max();
            amount += p.amount();
        }
        List<Priced> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Double.compare(b.min(), a.min()));
        List<RichCard.Line> lines = new ArrayList<>();
        lines.add(RichCard.Line.row("Items", "§f" + String.format(Locale.ROOT, "%,d", amount)));
        for (int i = 0; i < Math.min(SHOWN, sorted.size()); i++) {
            Priced p = sorted.get(i);
            lines.add(RichCard.Line.row(p.name().replace("Enchanted ", "Ench "), "§6" + Prices.coins(p.min())).key("Top items"));
        }
        if (sorted.size() > SHOWN) lines.add(RichCard.Line.row("§7+" + (sorted.size() - SHOWN) + " more kinds", "").key("Top items"));
        return new RichCard.Content(items.isEmpty() ? "§cEmpty" : range(min, max), lines);
    }

    private static void update() {
        List<Priced> items = new ArrayList<>();
        for (var e : SackApi.sackData.entrySet()) {
            int amount = e.getValue().amount();
            if (amount == 0) continue;
            Prices.Product p = Prices.get(e.getKey());
            if (p == null) continue;
            items.add(new Priced(GameState.strip(ItemRepo.name(e.getKey())), amount, p.sell() * amount, p.buy() * amount));
        }
        card = content(items);
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "Sack value on the HUD", "What your sacks hold is worth, from selling instantly to buying instantly, and what makes most of it, wherever you are.", "SkyHanni: outside sack value")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff());
        HudLayout.add(new RichCard(ID, "Sack value", "Items", Zone.TOP_LEFT, 61, ID) {
            @Override
            public boolean visible() {
                return (GameState.inSkyBlock || SbcMod.DEV) && !SackApi.sackData.isEmpty() && !isSackMenu();
            }

            private boolean isSackMenu() {
                var s = Minecraft.getInstance().gui.screen();
                return s != null && SackApi.isSackInventory(GameState.strip(s.getTitle().getString()));
            }

            @Override
            protected Content live() {
                long now = System.currentTimeMillis();
                if (now - updatedAt > 5000) {
                    updatedAt = now;
                    update();
                }
                return card;
            }

            @Override
            protected Content sample() {
                return OutsideSackValue.content(List.of(new Priced("Enchanted Coal Block", 12_400, 18_600_000, 21_200_000), new Priced("Enchanted Diamond", 8_200, 9_800_000, 10_900_000), new Priced("Mithril", 410_000, 6_500_000, 7_100_000)));
            }

            @Override
            protected List<Row> classicRows() {
                List<Row> out = new ArrayList<>();
                out.add(new Row("Sacks", card.right(), 0));
                return out;
            }

            @Override
            protected List<Row> classicDemo() {
                return List.of(new Row("Sacks", "§618.6M§7-§621.2M", 0));
            }
        });
    }
}
