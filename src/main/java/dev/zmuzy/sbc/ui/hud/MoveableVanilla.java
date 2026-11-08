// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.hud;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

// from skyhanni gui/moveablehud: vanilla hotbar, xp bar, action bar text and held item name as stuff the hud
// editor can move + size. the game still draws them, only the spot changes. not moved = stays out of the
// layout
public final class MoveableVanilla extends HudElement {
    public static final String ID = "move_vanilla_hud";
    // from skyhanni MoveableHotBar/XPBar/ActionBar/HeldItemTooltip: size + offset from the bottom middle
    public static final MoveableVanilla HOTBAR = new MoveableVanilla("vanilla_hotbar", "Hotbar", 182, 22, 91, 22),
            XP = new MoveableVanilla("vanilla_xp", "XP bar", 182, 5, 91, 29),
            ACTION_BAR = new MoveableVanilla("vanilla_action_bar", "Action bar text", 182, 10, 91, 72),
            HELD_ITEM = new MoveableVanilla("vanilla_held_item", "Held item name", 182, 10, 91, 59);

    private final int w, h, offsetX, offsetY;

    private MoveableVanilla(String id, String title, int w, int h, int offsetX, int offsetY) {
        super(id, title, "HUD", Zone.BOTTOM_CENTER, 90, ID);
        this.w = w;
        this.h = h;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    private static boolean allowed() {
        if (!Features.on(ID)) return false;
        if (GameState.inSkyBlock || SbcMod.DEV) return true;
        Feature f = Features.get(ID);
        return f != null && f.option("outside").bool();
    }

    // does the games layer need to go somewhere else
    public boolean moved() {
        return allowed() && HudLayout.pinned(id);
    }

    // moves the pose to the new spot, then the game draws like normal
    public void apply(GuiGraphicsExtractor g) {
        int homeX = g.guiWidth() / 2 - offsetX, homeY = g.guiHeight() - offsetY;
        float scale = Config.I.hudAll * HudLayout.scaleOf(id);
        var pose = g.pose();
        pose.translate(HudLayout.pinX(id), HudLayout.pinY(id));
        pose.scale(scale, scale);
        pose.translate(-homeX, -homeY);
    }

    @Override
    public int[] homeAt(int sw, int sh) {
        return new int[]{sw / 2 - offsetX, sh - offsetY};
    }

    @Override
    public boolean visible() {
        return allowed();
    }

    @Override
    public boolean supportsAlways() {
        return false;
    }

    @Override
    public boolean stretchable() {
        return false;
    }

    @Override
    public String heading() {
        return "";
    }

    @Override
    public List<Row> demo() {
        return List.of();
    }

    @Override
    public int width() {
        return w;
    }

    @Override
    public int height() {
        return h;
    }

    // the game draws it, editor too. nothing to paint here
    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
    }

    public static void register() {
        Features.add(new Feature(ID, "HUD", "Move the game's hotbar and text", "The hotbar, the XP bar, the action bar text and the held item's name can be moved and sized in the HUD editor.", "SkyHanni: Moveable HUD")
                .defaultOff()
                .option(Option.bool("move_vanilla_hud.outside", "Also outside SkyBlock", "", false)));
        HudLayout.add(HOTBAR);
        HudLayout.add(XP);
        HudLayout.add(ACTION_BAR);
        HudLayout.add(HELD_ITEM);
    }
}
