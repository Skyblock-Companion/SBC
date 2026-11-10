// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc;

import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.ui.Hud;
import io.github.zmuzy.sbc.ui.TabList;
import io.github.zmuzy.sbc.ui.hud.MoveableVanilla;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

import java.util.function.BooleanSupplier;

// where we meet the vanilla hud, through fabrics hud element registry instead of mixins
// vanilla layers get swapped for ours while the matching feature is on, our hud is one more layer at the end
// all of this is mc version sensitive, nothing outside mc/ should have to know about it
public final class HudHooks {
    private HudHooks() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("sbcompanion", path);
    }

    // draws the vanilla layer unless hide says ours is on screen instead
    private static void hideWhen(Identifier vanilla, BooleanSupplier hide) {
        String name = "HudHooks.hide " + vanilla;
        HudElementRegistry.replaceElement(vanilla, el -> (g, d) -> {
            if (!Safe.test(name, false, hide)) el.extractRenderState(g, d);
        });
    }

    private static void moves(Identifier vanilla, MoveableVanilla part) {
        String name = "HudHooks.moves " + vanilla;
        BooleanSupplier moved = part::moved;
        HudElementRegistry.replaceElement(vanilla, el -> (g, d) -> {
            if (!Safe.test(name, false, moved)) {
                el.extractRenderState(g, d);
                return;
            }
            g.pose().pushMatrix();
            try {
                part.apply(g);
            } catch (Throwable t) {
                Safe.caught(name, t);
            }
            el.extractRenderState(g, d);
            g.pose().popMatrix();
        });
    }

    public static void register() {
        for (Identifier v : new Identifier[]{VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR})
            hideWhen(v, io.github.zmuzy.sbc.ui.hud.BarsElement::hidesVanilla);
        // hypixel draws skill progress on the vanilla xp bar, our skill bar replaces it
        for (Identifier v : new Identifier[]{VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL})
            hideWhen(v, io.github.zmuzy.sbc.ui.hud.SkillElement::hidesXpBar);
        // item name + hypixels action bar text go above the hotbar stack instead of over it
        for (Identifier v : new Identifier[]{VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.OVERLAY_MESSAGE})
            HudElementRegistry.replaceElement(v, el -> (g, d) -> {
                int lift = 0;
                if (!Safe.resting("HudHooks.lift")) {
                    try {
                        lift = (v == VanillaHudElements.HELD_ITEM_TOOLTIP ? MoveableVanilla.HELD_ITEM : MoveableVanilla.ACTION_BAR).moved() ? 0 : io.github.zmuzy.sbc.ui.HudLayout.vanillaTextLift(g.guiHeight());
                    } catch (Throwable t) {
                        Safe.caught("HudHooks.lift", t);
                    }
                }
                if (lift == 0) {
                    el.extractRenderState(g, d);
                    return;
                }
                g.pose().pushMatrix();
                g.pose().translate(0, -lift);
                el.extractRenderState(g, d);
                g.pose().popMatrix();
            });
        // from skyhanni SkyBlockXPBar: your skyblock level on the bar, put back right after its drawn
        for (Identifier v : new Identifier[]{VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL})
            HudElementRegistry.replaceElement(v, el -> (g, d) -> {
                boolean swapped = Safe.test("HudHooks.xpBar", false, io.github.zmuzy.sbc.world.SkyblockXpBar::begin);
                try {
                    el.extractRenderState(g, d);
                } finally {
                    if (swapped) Safe.run("HudHooks.xpBar", io.github.zmuzy.sbc.world.SkyblockXpBar::end);
                }
            });
        // from skyhanni moveablehud: the games own pieces wherever the hud editor put them
        moves(VanillaHudElements.HOTBAR, MoveableVanilla.HOTBAR);
        moves(VanillaHudElements.INFO_BAR, MoveableVanilla.XP);
        moves(VanillaHudElements.EXPERIENCE_LEVEL, MoveableVanilla.XP);
        moves(VanillaHudElements.OVERLAY_MESSAGE, MoveableVanilla.ACTION_BAR);
        moves(VanillaHudElements.HELD_ITEM_TOOLTIP, MoveableVanilla.HELD_ITEM);
        // from skyblocker HideStatusEffectOverlay: effect icons at the top go along with the list next to the inventory
        hideWhen(VanillaHudElements.MOB_EFFECTS, io.github.zmuzy.sbc.ui.UiFeatures::hidePotionEffects);
        hideWhen(VanillaHudElements.SCOREBOARD, io.github.zmuzy.sbc.ui.hud.ScoreboardElement::on);
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, el -> (g, d) -> {
            if (!Safe.test("HudHooks.playerList", false, HudHooks::tabListHidesVanilla)) el.extractRenderState(g, d); // ours comes last, over the hud
        });
        // our own hud, after every vanilla layer
        HudElement hud = Safe.hud("Hud.render", Hud::render);
        HudElement tab = Safe.hud("TabList.draw", (g, d) -> {
            if (TabList.active()) {
                g.nextStratum();
                // drawn as a picture 20x a sec like the hud, the tab list is hundreds of pieces
                String key = g.guiWidth() + "x" + g.guiHeight() + " tab";
                if (!io.github.zmuzy.sbc.ui.HudTexture.show("tab", g, null, key, g.guiWidth(), g.guiHeight(), false, TabList::draw)) TabList.draw(g);
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, id("hud"), (g, d) -> {
            g.nextStratum();
            hud.extractRenderState(g, d);
            tab.extractRenderState(g, d);
        });
    }

    private static boolean tabListHidesVanilla() {
        return TabList.active() || TabList.toggleHidesVanilla();
    }
}
