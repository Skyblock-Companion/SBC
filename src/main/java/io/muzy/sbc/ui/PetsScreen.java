// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.ItemIcons;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Profile;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// missing pets, ones you can still improve, finished ones, in rarity colors. numbers from your last profile load
public final class PetsScreen extends Screen {
    private static final String[] RARITY = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final int[] COLOR = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF};
    private static final String[] SORTS = {"Level", "Rarity", "Name", "Levels left"};

    private final Ui ui = new Ui();
    private final Screen parent;
    private float scale = 1f, scroll, scrollTarget;
    private int contentH, viewH, tab, sort;

    public PetsScreen(Screen parent) {
        super(Component.literal("Pets"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    private final PetMenu.View view = new PetMenu.View();
    private PetMenu.Data classicData;
    private long classicDataAt;

    // same size on any window it fits, smaller ones scale down
    private void classic(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        scale = Math.min(1f, Math.min(width / 460f, height / 300f));
        int W = Math.round(width / scale), H = Math.round(height / scale);
        long now = System.currentTimeMillis();
        if (classicData == null || now - classicDataAt > 1000) {
            classicData = PetMenu.read();
            classicDataAt = now;
        }
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        ui.begin(g, Math.round(mouseX / scale), Math.round(mouseY / scale), mouseX, mouseY);
        PetMenu.paint(ui, g, classicData, view, W, H, this::onClose);
        g.pose().popMatrix();
        ui.flushTooltip();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        classic(g, mouseX, mouseY);
        return;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / scale, my = e.y() / scale;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (mx < hit.x() || my < hit.y() || mx >= hit.x() + hit.w() || my >= hit.y() + hit.h() || hit.click() == null) continue;
            hit.click().run();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scrollTarget -= (float) (Math.signum(sy) * 60);
        view.scrollTarget -= (float) (Math.signum(sy) * 30);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
