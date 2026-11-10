// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.hud;

import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

// a piece of a bigger element that can leave it (mana numbers without the bar, bar without numbers). at home
// the big one draws it and this is just what the editor grabs. dragged out it draws itself
public final class PartElement extends HudElement {
    public interface Paint {
        void draw(Ui ui, int x, int y, boolean demo);
    }

    private final HudElement owner;
    private final int width, height;
    private final Supplier<int[]> at;
    private final BooleanSupplier shown;
    private final Paint paint;

    public PartElement(String id, String title, String section, HudElement owner, int priority, int width, int height, Supplier<int[]> at, BooleanSupplier shown, Paint paint) {
        super(id, title, section, owner.zone, priority, id);
        this.owner = owner;
        this.width = width;
        this.height = height;
        this.at = at;
        this.shown = shown;
        this.paint = paint;
    }

    @Override
    public HudElement partOf() {
        return owner;
    }

    @Override
    public int[] partAt() {
        return at.get();
    }

    @Override
    public boolean visible() {
        return shown.getAsBoolean();
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
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        if (HudLayout.pinned(id)) paint.draw(ui, x, y, demo);
    }

    // big element draws this piece: on + not moved away
    public static boolean home(String id) {
        var f = io.github.zmuzy.sbc.feature.Features.get(id);
        return (f == null || f.enabled()) && !HudLayout.pinned(id);
    }
}
