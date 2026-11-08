// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;

// hypixels own party finder + floor menus for when our screen is off. from skyhanni DungeonFinderFeatures:
// party colors, floor as stack size, gate tip
public final class FinderMarks {
    private static final String F = "dungeon_finder";
    private static final Patterns.P ANY = Patterns.p("SH.dungeon.finder.new.floor.any"), ENTRANCE = Patterns.p("SH.dungeon.finder.new.entrance.colorless"), FLOOR = Patterns.p("SH.dungeon.finder.new.floor"),
            NUMBER = Patterns.p("SH.dungeon.finder.new.floor.number"), TYPE = Patterns.p("SH.dungeon.finder.new.floor.type"), PARTY = Patterns.p("SH.dungeon.finder.new.check.if.party"),
            DUNGEON = Patterns.p("SH.dungeon.finder.new.floor.dungeon"), INELIGIBLE = Patterns.p("SH.dungeon.finder.new.ineligible"), MEMBER = Patterns.p("SH.dungeon.finder.new.member.colorless"),
            TEAM = Patterns.p("SH.dungeon.tablist.playerteam.colorless");
    private static final int PURPLE = 0xC0FF55FF, DARK_RED = 0xC0AA0000;

    private FinderMarks() {}

    private static String myClass() {
        Profile.Data me = Profile.current;
        return me == null || me.selectedClass == null ? "" : me.selectedClass;
    }

    // from skyhanni highlightingHandler: a partys color, 0 = none
    static int partyColor(List<String> lore, String title, String mine, boolean ineligible, boolean paid, boolean nonPug, int low, boolean missing) {
        for (String l : lore) if (ineligible && INELIGIBLE.matcher(l).matches()) return DARK_RED;
        var p = PartyListing.parse(0, title, lore, false);
        if (paid && p.paidCarry()) return MenuView.RED;
        if (nonPug && p.nonPug()) return PURPLE;
        if (low != 0) for (var m : p.members()) if (m.level() <= low) return MenuView.YELLOW;
        if (missing && !mine.isEmpty() && p.missing().contains(mine)) return MenuView.GREEN;
        return 0;
    }

    private static void party(MenuView v) {
        boolean stack = DungeonMarks.opt(F, "floor_stack");
        String mine = myClass();
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || !PARTY.matcher(it.clean).matches()) continue;
            int c = partyColor(it.cleanLore, it.clean, mine, DungeonMarks.opt(F, "ineligible"), DungeonMarks.opt(F, "paid"), DungeonMarks.opt(F, "non_pug"), DungeonMarks.integer(F, "low_level", 0), DungeonMarks.opt(F, "missing_class"));
            if (c != 0) v.back(i, c);
            if (stack) {
                String floor = "", dungeon = "";
                for (String l : it.cleanLore) {
                    if (FLOOR.matcher(l).matches()) floor = l;
                    if (DUNGEON.matcher(l).matches()) dungeon = l;
                }
                if (!floor.isEmpty() && !dungeon.isEmpty()) v.number(i, PartyListing.shortFloor(floor, dungeon));
            }
        }
    }

    // from skyhanni selectFloorStackTip
    private static void selectFloor(MenuView v) {
        if (!DungeonMarks.opt(F, "floor_stack")) return;
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || it.clean.isEmpty()) continue;
            if (ANY.matcher(it.clean).matches()) v.number(i, "A");
            else if (ENTRANCE.matcher(it.clean).matches()) v.number(i, "E");
            else if (FLOOR.matcher(it.clean).matches()) {
                Matcher n = NUMBER.matcher(it.clean);
                if (n.matches()) v.number(i, String.valueOf(GameState.roman(n.group("floorNum"))));
            }
        }
    }

    // from skyhanni catacombsGateStackTip
    private static void gate(MenuView v) {
        if (!DungeonMarks.opt(F, "floor_stack")) return;
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || !TYPE.matcher(it.clean).matches() || !NUMBER.matcher(it.clean).matches()) continue;
            v.number(i, PartyListing.shortFloor(it.clean, it.clean));
        }
    }

    // from skyhanni DungeonApi.getColor
    static String levelColor(int level) {
        if (level >= 50) return "§c§l";
        if (level >= 45) return "§c";
        if (level >= 40) return "§6";
        if (level >= 35) return "§d";
        if (level >= 30) return "§9";
        if (level >= 25) return "§b";
        if (level >= 20) return "§2";
        if (level >= 15) return "§a";
        if (level >= 10) return "§e";
        if (level >= 5) return "§f";
        return "§7";
    }

    // from skyhanni DungeonFinderFeatures.toolTipHandler, on the lines the tooltip is about to show
    private static void tooltip(ItemStack stack, List<Component> lines) {
        var s = Minecraft.getInstance().gui.screen();
        if (s == null || !GameState.strip(s.getTitle().getString()).trim().equals("Party Finder")) return;
        if (!PARTY.matcher(GameState.strip(stack.getHoverName().getString()).trim()).matches()) return;
        boolean levels = DungeonMarks.opt(F, "class_levels"), showMissing = DungeonMarks.opt(F, "show_missing");
        java.util.List<String> missing = new java.util.ArrayList<>(DungeonRun.CLASSES);
        boolean any = false;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = MEMBER.matcher(GameState.strip(lines.get(i).getString()));
            if (!m.matches()) continue;
            any = true;
            missing.remove(m.group("className"));
            int level = Integer.parseInt(m.group("level"));
            if (levels) lines.set(i, Component.literal(" §b" + m.group("playerName") + "§f: §e" + m.group("className") + " " + levelColor(level) + level));
        }
        if (!showMissing || !any || missing.isEmpty()) return;
        String mine = myClass();
        StringBuilder sb = new StringBuilder("§cMissing: ");
        for (int i = 0; i < missing.size(); i++) sb.append(i > 0 ? "§7, " : "").append(missing.get(i).equals(mine) ? "§a" : "§7").append(missing.get(i));
        lines.add(Component.empty());
        lines.add(Component.literal(sb.toString()));
    }

    // from skyhanni DungeonRankTabListColor.onTabListText: "[319] Name (Mage XXXIV)" with the level colored,
    // ranks + emblems stay out
    public static String tabName(String coded) {
        if (!DungeonRun.inDungeon() || !Features.on("dungeon_tab_levels")) return coded;
        String plain = GameState.strip(coded).trim();
        Matcher m = TEAM.matcher("[0] " + plain);
        if (!m.matches() || m.group("className") == null || m.group("classLevel") == null) return coded;
        return "§f" + m.group("playerName") + " §f(§d" + m.group("className") + " " + levelColor(GameState.roman(m.group("classLevel"))) + m.group("classLevel") + "§f)";
    }

    public static void register() {
        Feature f = Features.get(F);
        f.option(Option.bool("dungeon_finder.ineligible", "Mark what you cannot join", "Dark red behind parties whose requirements you do not meet.", true));
        f.option(Option.bool("dungeon_finder.paid", "Mark paid carries", "Red behind parties selling a carry.", true));
        f.option(Option.bool("dungeon_finder.non_pug", "Mark perm and VC parties", "Purple behind perm parties and parties asking for a voice chat.", true));
        f.option(Option.integer("dungeon_finder.low_level", "Mark low levels", "Yellow behind parties with a player at or below this class level. 0 is off.", 0, 0, 50));
        f.option(Option.bool("dungeon_finder.missing_class", "Mark where your class is free", "Green behind parties without your class.", true));
        f.option(Option.bool("dungeon_finder.floor_stack", "Floor as stack size", "The floor where the item's count goes.", true));
        f.option(Option.bool("dungeon_finder.class_levels", "Class levels in colour", "In the tooltip of a party.", true));
        f.option(Option.bool("dungeon_finder.show_missing", "Missing classes", "A line in the tooltip of a party saying which classes it misses.", true));
        f.tooltip(FinderMarks::tooltip);
        MenuMarks.add(F, "Party Finder", FinderMarks::party);
        MenuMarks.add(F, "Select Floor", FinderMarks::selectFloor);
        MenuMarks.add(F, "Catacombs Gate", FinderMarks::gate);
        Features.add(new Feature("dungeon_tab_levels", "Dungeons", "Class levels in the tab list", "Class levels in the tab list get a colour by how high they are. Ranks and emblems stay out of those rows.", "SkyHanni: Colored Class Level")
                .when(DungeonRun::inDungeon));
    }
}
