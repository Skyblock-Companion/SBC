// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.hud;

import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.ui.HudLayout.Zone;

import java.util.ArrayList;
import java.util.List;

// your own hud text: lines split by
// in the option, & color codes work
public final class TextElement extends HudElement {
    public static final TextElement I = new TextElement();

    private TextElement() {
        super("customtext", "Custom text", "GUI", Zone.TOP_LEFT, 90, "customtext");
    }

    private String text() {
        Feature f = Features.get("customtext");
        Option o = f == null ? null : f.option("text");
        return o == null ? "" : o.text();
    }

    @Override
    public boolean visible() {
        Feature f = Features.get("customtext");
        Option only = f == null ? null : f.option("only_menus");
        if (only != null && only.bool() && !(net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return false;
        return !text().isBlank();
    }

    @Override
    public String heading() {
        return "";
    }

    @Override
    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (String l : text().split("\\|")) out.add(new Row(l.replace('&', '§').trim()));
        return out;
    }

    @Override
    public List<Row> demo() {
        return List.of(new Row("§eYour text here"), new Row("§7Split lines with |, colour with &"), new Row(""), new Row("§fGoal: §a3/10"));
    }
}
