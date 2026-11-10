// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.areas;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;

import java.util.List;

// for stranded profiles. from skyhanni stranded/HighlightPlaceableNpcs.kt + misc/InWaterDisplay.kt
public final class StrandedExtras {
    public static final String NPCS = "stranded_npcs", WATER = "stranded_water";
    private static final Patterns.P LOCATION = Patterns.p("SH.stranded.highlightplacement.location"), SET = Patterns.p("SH.stranded.highlightplacement.clicktoset"), SPAWN = Patterns.p("SH.stranded.highlightplacement.clicktospawn");

    private StrandedExtras() {}

    // from skyhanni isPlaceableNpc: last line offers to place it, no line says where it stands
    static boolean placeable(List<String> lore) {
        if (lore.isEmpty()) return false;
        String last = lore.get(lore.size() - 1);
        if (!SET.matcher(last).matches() && !SPAWN.matcher(last).matches()) return false;
        for (String l : lore) if (LOCATION.matcher(l).matches()) return false;
        return true;
    }

    private static void mark(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) if (placeable(v.item(slot).lore)) v.back(slot, MenuView.GREEN);
    }

    private static final HudElement CARD = new RichCard(WATER, "In water", "HUD", Zone.TOP_LEFT, 49, WATER) {
        private boolean inWater() {
            var p = Minecraft.getInstance().player;
            return p != null && p.isInWater();
        }

        @Override public boolean visible() {
            return FishState.stranded();
        }

        @Override protected Content live() {
            return new Content("", List.of(Line.row("In Water", inWater() ? "§aTrue" : "§cFalse").raw()));
        }

        @Override protected Content sample() {
            return new Content("", List.of(Line.row("In Water", "§aTrue").raw()));
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("In Water", inWater() ? "True" : "False", Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("In Water", "True", Theme.current().text()));
        }
    };

    public static void register() {
        Features.add(new Feature(NPCS, "Misc", "Stranded: placeable NPCs", "In Island NPCs: those you can place but have not.", "SkyHanni: Highlight Placeable NPCs")
                .when(() -> GameState.inSkyBlock && FishState.stranded()).defaultOff());
        MenuMarks.add(NPCS, "Island NPCs", StrandedExtras::mark);
        Features.add(new Feature(WATER, "Misc", "Stranded: in water", "Says whether you are in water.", "SkyHanni: In Water Display")
                .when(() -> GameState.inSkyBlock && FishState.stranded()).defaultOff());
        HudLayout.add(CARD);
    }
}
