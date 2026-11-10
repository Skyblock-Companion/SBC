// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.Worth;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small skyblocker helpers: dungeon/GuardianHealth (f3 guardian numbers), SalvageHelper +
// SellableItemsHighlighter (what to toss, on menus), DungeonBuffWidget
public final class DungeonMisc {
    private static final AABB BOSS_ROOM = new AABB(34, 65, -32, -32, 100, 36);
    private static final Pattern GUARDIAN = Pattern.compile("^(.*?) Guardian (.*?)([A-Za-z])❤$");
    private static final Pattern PROFESSOR = Pattern.compile("^﴾ (The Professor) (.*?)([A-za-z])❤ ﴿$");
    private static final String BOSS_LINE = "[BOSS] The Professor: I was burdened with terrible news recently...";
    // from skyblocker SalvageHelper.DUNGEON_SALVAGABLE: salvageable dungeon items, without "DUNGEON ITEM"
    private static final Pattern SALVAGABLE = Pattern.compile("DUNGEON(?! ITEM)");
    private static final Set<String> SELLABLE = Set.of("DEFUSE_KIT", "TRAINING_WEIGHTS", "DUNGEON_LORE_PAPER", "REVIVE_STONE");
    private static final Patterns.P BLESSING = Patterns.p("SH.dungeon.blessings.colorless");
    private static boolean inBoss;

    private DungeonMisc() {}

    // --- guardian health ---

    // from skyblocker GuardianHealth: "50k" from a guardians name, or null
    static String guardianHealth(String name) {
        Matcher m = name.contains("The Professor") ? PROFESSOR.matcher(name) : GUARDIAN.matcher(name);
        return m.matches() ? m.group(2) + m.group(3) : null;
    }

    private static void drawGuardians(WorldDraw w) {
        if (!inBoss) return;
        var level = w.mc.level;
        for (Guardian g : level.getEntitiesOfClass(Guardian.class, BOSS_ROOM, e -> true)) {
            for (ArmorStand st : level.getEntitiesOfClass(ArmorStand.class, g.getBoundingBox().inflate(0, 1, 0), s -> s.hasCustomName())) {
                String health = guardianHealth(GameState.strip(st.getName().getString()));
                if (health == null) continue;
                w.label("§a" + health, g.position(), DungeonMarks.opt("dungeon_guardians", "through_walls"));
            }
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!inBoss && line.plain().trim().equals(BOSS_LINE)) inBoss = true;
        return ChatBus.PASS;
    }

    // --- what to toss ---

    // from skyblocker isPriceWithinRange: worth something but not over 100k
    static boolean salvagePrice(double value) {
        return value > 0 && value < 100_000;
    }

    private static boolean salvageLore(List<String> lore) {
        for (String l : lore) if (SALVAGABLE.matcher(l).find()) return true;
        return false;
    }

    private static void markSalvage(MenuView v) {
        for (int i = 0; i < v.items.length; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || it.id.isEmpty() || !salvageLore(it.cleanLore)) continue;
            if (!salvagePrice(Worth.item(it.tag, it.count))) continue;
            if (DungeonMarks.opt("dungeon_salvage", "donated_only")) {
                Profile.Data me = Profile.current;
                if (me == null || !me.museum.contains(it.id)) continue;
            }
            v.back(i, MenuView.YELLOW);
        }
    }

    // from skyblocker SellableItemsHighlighter.isValidItem, over your own slots in the menu
    static boolean sellable(String id, String name) {
        return id.equals("POTION") ? name.contains("Healing VIII Splash Potion") : SELLABLE.contains(id);
    }

    private static void markSellable(MenuView v) {
        for (int i = v.top; i < v.items.length; i++) {
            MenuView.Item it = v.items[i];
            if (it != null && !it.id.isEmpty() && sellable(it.id, it.clean)) v.back(i, MenuView.YELLOW);
        }
    }

    // --- blessings ---

    // from skyblocker DungeonBuffWidget.getBlessingColor
    private static String blessingColor(String blessing) {
        return switch (blessing) {
            case "Life" -> "§d";
            case "Power" -> "§c";
            case "Stone" -> "§a";
            case "Time" -> "§7";
            case "Wisdom" -> "§b";
            default -> "§f";
        };
    }

    // from skyhanni DungeonApi.onTabListFooterUpdate on the tab footer: name, level
    static List<String[]> blessings(String footer) {
        List<String[]> out = new ArrayList<>();
        Matcher m = BLESSING.matcher(footer);
        while (m.find()) out.add(new String[]{m.group("type"), m.group("amount")});
        return out;
    }

    private static RichCard.Content cardOf(List<String[]> list) {
        List<RichCard.Line> lines = new ArrayList<>();
        for (String[] b : list) lines.add(RichCard.Line.row(b[0], blessingColor(b[0]) + b[1]).noMeter());
        return new RichCard.Content("", lines);
    }

    public static final HudElement CARD = new RichCard("dungeon_buffs", "Blessings", "Dungeon", Zone.TOP_LEFT, 40, "dungeon_buffs") {
        @Override public boolean visible() {
            return DungeonRun.inDungeon() && !blessings(GameState.tabFooter).isEmpty();
        }

        @Override protected Content live() {
            return cardOf(blessings(GameState.tabFooter));
        }

        @Override protected Content sample() {
            return cardOf(List.of(new String[]{"Power", "XIX"}, new String[]{"Life", "IX"}, new String[]{"Wisdom", "V"}, new String[]{"Stone", "VII"}, new String[]{"Time", "II"}));
        }

        @Override protected List<Row> classicRows() {
            List<Row> rows = new ArrayList<>();
            for (String[] b : blessings(GameState.tabFooter)) rows.add(new Row(b[0], b[1], 0));
            return rows;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Power", "XIX", 0), new Row("Life", "IX", 0));
        }
    };

    public static void register() {
        Feature g = Features.add(new Feature("dungeon_guardians", "Dungeons", "Guardian health", "The health of the guardians in the Floor 3 boss, on the guardians themselves.", "Skyblocker: Guardian Health Display")
                .when(() -> DungeonRun.inDungeon() || SbcMod.DEV).world(DungeonMisc::drawGuardians).left(() -> inBoss = false)
                .option(Option.bool("dungeon_guardians.through_walls", "Through walls", "Also shows the health of guardians behind blocks.", false)));
        ChatBus.register(ChatBus.OBSERVE + 18, "dungeonguardians", g, DungeonMisc::onChat);
        Features.add(new Feature("dungeon_salvage", "Dungeons", "Salvage helper", "In the Salvage menu, marks dungeon items worth under 100k, which are safe to salvage.", "Skyblocker: Salvage Helper")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("dungeon_salvage.donated_only", "Only what is in your museum", "Leaves items you have not donated yet alone.", false)));
        Features.add(new Feature("dungeon_sellable", "Dungeons", "Sellable dungeon items", "With Ophelia, marks the dungeon items in your inventory you can sell to her.", "Skyblocker: Sellable Items Highlighter")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Features.add(new Feature("dungeon_buffs", "Dungeons", "Blessings", "A card with the blessings your team found.", "Skyblocker: Dungeon Buffs")
                .when(() -> DungeonRun.inDungeon() || SbcMod.DEV));
        MenuMarks.add("dungeon_salvage", Pattern.compile("^Salvage Items.*"), DungeonMisc::markSalvage);
        MenuMarks.add("dungeon_sellable", Pattern.compile("^(Ophelia|Booster Cookie)$"), DungeonMisc::markSellable);
        HudLayout.add(CARD);
        if (Gain.ofId("") < 0) return; // nothing to set up for prices
    }
}
