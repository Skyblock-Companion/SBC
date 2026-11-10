// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.layout.BaseUIComponent;
import io.muzy.sbc.ui.layout.UIGraphics;
import io.muzy.sbc.ui.layout.Sizing;
import net.minecraft.client.input.MouseButtonEvent;

// component painted by our own Ui renderer. thats how the old pages (routine, planner, maps, profile...) live
// inside the component screens: tree does layout, Ui paints
public class UiComponent extends BaseUIComponent {
    public interface Painter {
        // draws into (x, y, w, h) with mouse at (mx, my), registers clicks on ui
        void paint(Ui ui, UIGraphics g, int x, int y, int w, int h, int mx, int my);
    }

    protected final Ui ui = new Ui();
    protected final Painter painter;
    private final int contentW, contentH;
    private Ui.Drag dragging;

    public UiComponent(Painter painter, int contentW, int contentH) {
        this.painter = painter;
        this.contentW = contentW;
        this.contentH = contentH;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return contentW;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return contentH;
    }

    @Override
    public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
        ui.begin(g, mouseX, mouseY);
        ui.textLimit = x() + width();
        painter.paint(ui, g, x(), y(), width(), height(), mouseX, mouseY);
        ui.flushTooltip();
        ui.textLimit = Integer.MAX_VALUE;
    }

    // only the focused component gets drags + releases, without this nothing painted could be dragged
    @Override
    public boolean canFocus(FocusSource source) {
        return source == FocusSource.MOUSE_CLICK;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent e, boolean doubleClick) {
        double mx = x() + e.x(), my = y() + e.y();
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit h = ui.hits.get(i);
            if (mx < h.x() || my < h.y() || mx >= h.x() + h.w() || my >= h.y() + h.h()) continue;
            if (h.drag() != null) {
                dragging = h.drag();
                dragging.at(mx, my);
                return true;
            }
            Runnable r = e.button() == InputConstants.MOUSE_BUTTON_RIGHT && h.right() != null ? h.right() : h.click();
            if (r != null) {
                r.run();
                Config.save();
                return true;
            }
        }
        return super.onMouseDown(e, doubleClick);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent e, double dx, double dy) {
        if (dragging != null) {
            dragging.at(x() + e.x(), y() + e.y());
            return true;
        }
        return super.onMouseDrag(e, dx, dy);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent e) {
        if (dragging != null) {
            dragging = null;
            Config.save();
            return true;
        }
        return super.onMouseUp(e);
    }
}
