// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.item.PickupLog;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.CustomElement;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

// the hud elements we ship, each tied to its feature. layout does the placing, theme does the look
public final class Widgets {
    private static final String[][] BRIEF_ROUTINE = {{"Jacob's Farming Contest", "Jacob's Contest"}, {"New Year Celebration", "New Year"}, {"Commissions (daily bonus)", "Commissions (bonus)"},
            {"Faction daily quests", "Faction quests"}, {"Enchanted Bone Meal", "Ench. Bone Meal"}};
    private Widgets() {}

    public static void register() {
        HudLayout.add(new HudElement("playercard", "Player card", "HUD", Zone.TOP_LEFT, 10, "playercard") {
            // stands + draws like a CustomElement, that class just cant say the card has a short form
            private final CustomElement card = new CustomElement("playercard", "Player card", "HUD", Zone.TOP_LEFT, 10, "playercard",
                    () -> true || PlayerCard.active(), PlayerCard::width, PlayerCard::height, PlayerCard::draw, PlayerCard.width(), 92);

            @Override
            public boolean visible() {
                return card.visible();
            }

            @Override
            public boolean supportsAlways() {
                return card.supportsAlways();
            }

            @Override
            public boolean stretchable() {
                return card.stretchable();
            }

            @Override
            public int width() {
                card.demo = demo;
                return card.width();
            }

            @Override
            public int height() {
                card.demo = demo;
                return card.height();
            }

            @Override
            public List<Row> demo() {
                return card.demo();
            }

            @Override
            public void draw(Ui ui, net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y) {
                card.demo = demo;
                card.draw(ui, g, x, y);
            }

        });
        HudLayout.add(new HudElement("routine", "Routine", "HUD", Zone.TOP_LEFT, 20, "routine") {
            @Override
            public boolean visible() {
                return true;
            }

            @Override
            public boolean supportsAlways() {
                return false;
            }

            private boolean classic() {
                return true;
            }

            @Override
            public boolean stretchable() {
                return classic();
            }

            @Override
            public String heading() {
                return classic() ? io.github.zmuzy.sbc.tasks.RoutinePreset.current().name() : "";
            }

            @Override
            public String headingValue() {
                return classic() ? Hud.routineProgress() : "";
            }

            private List<Row> shownRows = List.of();
            private long readAt;

            @Override
            public List<Row> rows() {
                long now = System.currentTimeMillis();
                if (now - readAt > 250) {
                    readAt = now;
                    shownRows = Hud.routineRows();
                }
                if (!compact()) return shownRows;
                // brief() only changes with the rows (every 250ms max) or the style but rows() gets asked a few times a frame
                int stamp = style().stamp();
                if (shownRows != briefFor || stamp != briefStyle) {
                    briefMemo = brief(shownRows);
                    briefFor = shownRows;
                    briefStyle = stamp;
                }
                return briefMemo;
            }

            private List<Row> briefFor, briefMemo = List.of();
            private int briefStyle;

            // long names shorter for the compact card. what you hid under the full name stays hidden
            private List<Row> brief(List<Row> rows) {
                List<Row> out = new ArrayList<>(rows.size());
                for (Row r : rows) {
                    if (style().isHidden(r.label())) continue;
                    String label = r.label();
                    for (String[] b : BRIEF_ROUTINE) label = label.replace(b[0], b[1]);
                    out.add(new Row(r.icon(), label, r.value(), r.color(), r.progress(), r.barColor()));
                }
                return out;
            }

            @Override
            public List<Row> demo() {
                return rows();
            }

            @Override
            public int width() {
                return classic() ? Math.max(120, super.width()) : PlayerCard.W;
            }

            @Override
            public int height() {
                return classic() ? super.height() : Hud.routineHeight();
            }

            @Override
            public void draw(Ui ui, net.minecraft.client.gui.GuiGraphicsExtractor g, int x, int y) {
                if (classic()) {
                    super.draw(ui, g, x, y);
                    return;
                }
                int h = Hud.routineHeight();
                Theme.current().panel(ui, x, y, PlayerCard.W, h);
                Hud.drawPanel(x, y, PlayerCard.W, h);
            }
        });
        HudLayout.add(new CustomElement("minimap", "Minimap", "HUD", Zone.TOP_RIGHT, 10, "minimap",
                () -> !("CATACOMBS".equals(GameState.island) && Config.I.minimapHideInDungeon),
                () -> Config.I.minimapSize, () -> Config.I.minimapSize + (Config.I.minimapRound ? 34 : 20),
                (ui, g, x, y) -> io.github.zmuzy.sbc.map.MapRender.drawMinimap(ui, x, y, Config.I.minimapSize), 110, 130));
        HudLayout.add(new HudElement("pickups", "Item pickup log", "Items", Zone.BOTTOM_RIGHT, 10, "pickups") {
            @Override
            public boolean visible() {
                return !PickupLog.recent().isEmpty();
            }

            @Override
            public String heading() {
                return "Items";
            }

            @Override
            protected boolean meters() {
                return false;
            }

            @Override
            public String headingValue() {
                if (demo ) return "\u00a76+6.13M";
                double sum = 0;
                for (var e : PickupLog.recent()) sum += PickupLog.value(e);
                sum = PickupLog.shownTotal(sum);
                if (sum == 0) return "";
                String text = (sum > 0 ? "+" : "-") + io.github.zmuzy.sbc.core.Prices.coins((long) Math.abs(sum));
                return (sum > 0 ? "\u00a76" : "\u00a7c") + text;
            }

            @Override
            public List<Row> rows() {
                List<Row> out = new ArrayList<>();
                Theme t = Theme.current();
                for (var e : PickupLog.recent()) {
                    String w = PickupLog.worth(e);
                    net.minecraft.world.item.ItemStack icon = e.id() == null || e.id().isEmpty() || e.id().equals("COINS") ? io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:gold_nugget") : io.github.zmuzy.sbc.core.ItemInfo.stack(e.id());
                    if (!io.github.zmuzy.sbc.item.market.Market.b("pickups", "pickups.icons", true)) icon = net.minecraft.world.item.ItemStack.EMPTY;
                    out.add(new Row(icon, PickupLog.lineText(e), w, e.removed() ? t.bad() : gain(t)));
                }
                return out;
            }

            @Override
            public List<Row> demo() {
                Theme t = Theme.current();
                return List.of(new Row(io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:carrot"), "\u00a7a+64 \u00a7fEnchanted Carrot", "4.2k", gain(t)), new Row(io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:nether_star"), "\u00a7a+1 \u00a7fRecombobulator 3000", "6.13M", gain(t)),
                        new Row(io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:gold_nugget"), "\u00a7a+12,400 \u00a7fCoins", "", 0), new Row(io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:paper"), "\u00a7c-1 \u00a7fWither Catalyst", "1.1M", t.bad()));
            }

            // gains are coins and coins are gold
            private int gain(Theme t) {
                return io.github.zmuzy.sbc.ui.theme.Modern.GOLD;
            }
        });
        HudLayout.add(new CustomElement("dungeonmap", "Dungeon map", "Dungeon", Zone.TOP_RIGHT, 15, "dungeon_map",
                DungeonMap::active, DungeonMap::width, DungeonMap::height, DungeonMap::draw, 130, 150));
        HudLayout.add(new CustomElement("peek", "Chat peek", "Chat", Zone.BOTTOM_LEFT, 5, "chat_peek",
                io.github.zmuzy.sbc.chat.Peek::active, io.github.zmuzy.sbc.chat.Peek::width, io.github.zmuzy.sbc.chat.Peek::height,
                io.github.zmuzy.sbc.chat.Peek::draw, 300, 80));
        HudLayout.add(io.github.zmuzy.sbc.ui.hud.BarsElement.I);
        io.github.zmuzy.sbc.ui.hud.BarsElement.registerParts();
        HudLayout.add(io.github.zmuzy.sbc.ui.hud.TextElement.I);
        io.github.zmuzy.sbc.ui.hud.InfoElements.register();
        io.github.zmuzy.sbc.ui.hud.ScoreboardElement.register();
        io.github.zmuzy.sbc.ui.hud.SkillElement.register();
        io.github.zmuzy.sbc.ui.hud.HotbarElements.register();
        TabList.register();
    }
}
