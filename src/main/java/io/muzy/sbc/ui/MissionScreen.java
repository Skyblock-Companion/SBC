// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.nav.Navigator;
import io.muzy.sbc.tasks.Guide;
import io.muzy.sbc.tasks.Mission;
import io.muzy.sbc.tasks.Routine;
import io.muzy.sbc.tasks.TaskDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

// alt+K: small card for the mission youre on. steps, needed items (checked in your inv), shortcuts: guided
// routine, nav, warp, sacks, bazaar...
public final class MissionScreen extends Screen {
    private final Ui ui = new Ui();
    private float scale = 1f;
    private final long opened = System.currentTimeMillis();

    public MissionScreen() {
        super(Component.literal("Current mission"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int w = 340;
        scale = Math.min(1f, Math.min(width / (w + 20f), height / 300f));
        int W = Math.round(width / scale), H = Math.round(height / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        ui.begin(g, Math.round(mouseX / scale), Math.round(mouseY / scale), mouseX, mouseY);
        float t = Math.min(1, (System.currentTimeMillis() - opened) / 180f);
        ui.rect(0, 0, W, H, Ui.alpha(0x99060810, t));

        Routine.Entry e = Guide.active && Guide.current != null ? entryOf(Guide.current) : Mission.current();
        int h = measure(e);
        int x = W / 2 - w / 2, y = Math.max(8, H / 2 - h / 2) + (int) ((1 - t) * 10);
        ui.panel(x, y, w, h, 10, Ui.alpha(Ui.PANEL, 0.97f));
        ui.textLimit = x + w - 8;
        ui.roundGradH(x, y, w, 3, 2, Ui.VIOLET, Ui.CYAN);
        draw(e, x, y, w);
        g.pose().popMatrix();
        ui.flushTooltip();
    }

    private static Routine.Entry entryOf(TaskDef t) {
        for (Routine.Entry e : Routine.today()) if (e.def().id().equals(t.id())) return e;
        return null;
    }

    private int measure(Routine.Entry e) {
        if (e == null) return 110;
        TaskDef t = e.def();
        int h = 64 + 28 + 16 + Mission.steps(t).size() * 12 + 8;
        if (!Mission.needs(t).isEmpty()) h += 16 + Mission.needs(t).size() * 13 + 6;
        if (!Mission.shortcuts(t).isEmpty()) h += 16 + 24;
        return h + 16 + 3 * 12 + 20;
    }

    private void draw(Routine.Entry e, int x, int y, int w) {
        int ly = y + 12;
        ui.text(Guide.active ? "▶ GUIDED ROUTINE" : "NEXT MISSION", x + 12, ly, Guide.active ? Ui.GREEN : Ui.MUTED);
        ui.textRight("Alt+K", x + w - 12, ly, Ui.DIM);
        ly += 14;
        if (Guide.active && Guide.block != null) {
            var b = Guide.block;
            ui.icon(b.icon(), x + 12, ly, 1.2f);
            ui.textScaled(ui.ellipsize(b.name(), (int) ((w - 60) / 1.3f)), x + 36, ly + 2, 1.3f, Ui.TEXT, true);
            long left = Math.max(0, Guide.blockEndsAt - System.currentTimeMillis());
            ui.text("Grind block · " + io.muzy.sbc.core.SbTime.dur(left) + " left", x + 12, ly + 22, Ui.GOLD);
            ui.text(ui.ellipsize(b.why(), w - 24), x + 12, ly + 36, Ui.MUTED);
            ui.button("m_nb", x + 12, ly + 52, 120, 18, "Next block ›", Ui.SOFT, Guide::nextBlock);
            ui.button("m_stop2", x + 138, ly + 52, 80, 18, "■ Stop", Ui.DANGER, Guide::stop);
            return;
        }
        if (e == null) {
            ui.textScaled("Everything is done for today!", x + 12, ly, 1.3f, Ui.GREEN, true);
            ui.text("Open the full menu with K to plan the rest of your time.", x + 12, ly + 20, Ui.MUTED);
            return;
        }
        TaskDef t = e.def();
        ui.icon(t.icon(), x + 12, ly, 1.2f);
        ui.textScaled(ui.ellipsize(t.name(), (int) ((w - 110) / 1.3f)), x + 36, ly + 2, 1.3f, Ui.TEXT, true);
        ui.chip(x + w - 12 - ui.width(t.periodLabel()) - 10, ly + 1, t.periodLabel(), Ui.CYAN);
        ui.button("m_hide", x + w - 12 - 54, ly + 17, 54, 14, "Hide task", Ui.GHOST, () -> {
            Config.I.hiddenTasks.add(t.id());
            Config.save();
            if (Guide.current != null && Guide.current.id().equals(t.id())) Navigator.stop();
            io.muzy.sbc.ui.Toasts.push("Hidden", t.name() + " won't show again (Settings to undo)", Ui.MUTED);
        });
        ly += 18;
        Navigator.Target loc = Guide.locationOf(t);
        String where = loc == null ? "Anywhere" : GameState.pretty(loc.island()) + (t.guideNode().isEmpty() ? "" : " · " + t.guideNode());
        var p = minecraft.player;
        if (loc != null && p != null && loc.island().equals(GameState.island))
            where += "  ·  " + (int) Math.sqrt(loc.pos().distanceToSqr(p.position())) + "m away";
        ui.text(ui.ellipsize(where, w - 24), x + 12, ly + 2, Ui.MUTED);
        if (t.target() > 1) ui.textRight(e.done() + "/" + t.target(), x + w - 12, ly + 2, Ui.CYAN);
        ly += 18;

        // main buttons
        int bx = x + 12, bw = (w - 24 - 18) / 4;
        ui.button("m_guide", bx, ly, bw, 18, Guide.active ? "■ Stop" : "▶ Start", Guide.active ? Ui.DANGER : Ui.PRIMARY, () -> {
            if (Guide.active) Guide.stop();
            else Guide.start();
        });
        ui.button("m_nav", bx + bw + 6, ly, bw, 18, "➜ Navigate", Ui.SOFT, () -> {
            if (loc != null) Navigator.navigate(loc);
        });
        ui.button("m_done", bx + 2 * (bw + 6), ly, bw, 18, "✔ Done", Ui.SOFT, () -> {
            Routine.add(t, Math.max(1, t.target() - e.done()));
            Store.markDirty();
        });
        ui.button("m_skip", bx + 3 * (bw + 6), ly, bw, 18, "Skip ›", Ui.GHOST, () -> {
            Mission.skipped.add(t.id());
            if (Navigator.target() != null && loc != null && Navigator.target().name().equals(loc.name())) Navigator.stop();
        });
        ly += 28;

        ui.text("What to do", x + 12, ly, Ui.CYAN);
        ly += 14;
        int n = 1;
        for (String s : Mission.steps(t)) {
            ui.text(n++ + ".", x + 14, ly, Ui.DIM);
            ui.text(ui.ellipsize(s, w - 44), x + 28, ly, Ui.TEXT);
            ly += 12;
        }
        ly += 8;

        List<Mission.Need> needs = Mission.needs(t);
        if (!needs.isEmpty()) {
            ui.text("You need", x + 12, ly, Ui.CYAN);
            ly += 14;
            for (Mission.Need nd : needs) {
                int have = Mission.inInventory(nd.name());
                boolean ok = have >= nd.amount();
                ui.text((ok ? "✔ " : "✖ ") + nd.amount() + "x " + nd.name(), x + 14, ly, ok ? Ui.GREEN : Ui.RED);
                String right = ok ? "in inventory (" + have + ")" : nd.source().isEmpty() ? "not in inventory" : nd.source();
                ui.textRight(ui.ellipsize(right, w / 2), x + w - 12, ly, ok ? Ui.MUTED : Ui.ORANGE);
                ly += 13;
            }
            ly += 6;
        }

        List<Mission.Shortcut> cuts = Mission.shortcuts(t);
        if (!cuts.isEmpty()) {
            ui.text("Shortcuts", x + 12, ly, Ui.CYAN);
            ly += 14;
            int cx = x + 12;
            for (Mission.Shortcut c : cuts) {
                int cw = ui.width(c.label()) + 16;
                if (cx + cw > x + w - 12) break;
                ui.button("cut" + c.command(), cx, ly, cw, 18, c.label(), Ui.SOFT, () -> {
                    Mission.run(c.command());
                    onClose();
                });
                if (ui.hover(cx, ly, cw, 18)) ui.tooltip(c.label(), "§7Runs /" + c.command());
                cx += cw + 6;
            }
            ly += 24;
        }

        ui.rect(x + 12, ly, w - 24, 1, 0x18FFFFFF);
        ly += 6;
        ui.text("Up next", x + 12, ly, Ui.MUTED);
        ly += 12;
        int shown = 0;
        boolean past = false;
        for (Routine.Entry o : Routine.today()) {
            if (o.def().id().equals(t.id())) {
                past = true;
                continue;
            }
            if (!past || o.complete() || Mission.skipped.contains(o.def().id())) continue;
            ui.icon(o.def().icon(), x + 12, ly - 3, 0.6f);
            ui.text(ui.ellipsize(o.def().displayName(), w - 40), x + 24, ly, Ui.DIM);
            ly += 12;
            if (++shown >= 3) break;
        }
        if (shown == 0) ui.text("Nothing after this one", x + 12, ly, Ui.DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / scale, my = e.y() / scale;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit h = ui.hits.get(i);
            if (mx < h.x() || my < h.y() || mx >= h.x() + h.w() || my >= h.y() + h.h() || h.click() == null) continue;
            h.click().run();
            Config.save();
            Store.markDirty();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
        if (e.key() == InputConstants.KEY_K) {
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }
}
