// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.screens;

import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.feature.Changelog;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.feature.Releases;
import io.github.zmuzy.sbc.ui.layout.Color;
import io.github.zmuzy.sbc.ui.layout.FlowLayout;
import io.github.zmuzy.sbc.ui.layout.HorizontalAlignment;
import io.github.zmuzy.sbc.ui.layout.Insets;
import io.github.zmuzy.sbc.ui.layout.Sizing;
import io.github.zmuzy.sbc.ui.layout.UIContainers;
import io.github.zmuzy.sbc.ui.layout.VerticalAlignment;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// after an update: what came since the version this player last played, all versions they skipped together,
// newest first. new helpers are off until they switch them on here; nothing is turned on for them. a new player
// never sees it, the setup already showed them everything
public final class WhatsNewScreen extends SbcScreen {
    public static final String CHANGELOG = "https://github.com/Skyblock-Companion/SBC/blob/main/docs/CHANGELOG.md";
    private static boolean checkedThisRun;

    private final List<Releases.Release> releases;
    // older versions start folded so a long list stays short
    private final Set<String> open = new HashSet<>();
    // sections folded or opened by hand, per version
    private final Set<String> closed = new HashSet<>(), opened = new HashSet<>();
    private FlowLayout body;

    WhatsNewScreen(Screen parent, List<Releases.Release> releases) {
        super("What's new", parent);
        this.releases = releases;
        if (!releases.isEmpty()) open.add(releases.get(0).version());
    }

    // the version this player saw last: empty after the setup was done means the first beta
    static String lastSeen() {
        return Config.I.lastVersion == null || Config.I.lastVersion.isEmpty() ? Releases.FIRST : Config.I.lastVersion;
    }

    // what this player has not seen yet. nothing before the setup is done: a new player gets the setup instead
    public static List<Releases.Release> unseen() {
        if (!Config.I.setupDone) return List.of();
        return Releases.newerThan(lastSeen());
    }

    // once per start, from the main menu: new helpers go off until chosen, then the page opens
    public static boolean maybeShow(Minecraft mc, Screen parent) {
        if (checkedThisRun) return false;
        checkedThisRun = true;
        List<Releases.Release> list = unseen();
        if (list.isEmpty()) return false;
        holdNew(list);
        mc.gui.setScreen(new WhatsNewScreen(parent, list));
        return true;
    }

    // a helper new since the last version waits off, unless this player already chose it
    public static void holdNew(List<Releases.Release> list) {
        for (Releases.Release r : list)
            for (String id : r.features()) {
                Feature f = Features.get(id);
                if (f != null && !f.alwaysOn && !f.ownField() && !Config.I.features.containsKey(id) && f.enabled()) f.setEnabled(false);
            }
        Config.save();
    }

    // /sbc whatsnew: this version's page, whatever was seen before
    public static void openLatest(Screen parent) {
        List<Releases.Release> list = Releases.newerThan(Releases.FIRST);
        Minecraft.getInstance().gui.setScreen(new WhatsNewScreen(parent, list.isEmpty() ? List.of() : List.of(list.get(0))));
    }

    // seen: the next start does not open it again
    static void markSeen() {
        Config.I.lastVersion = Releases.current();
        Config.save();
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Showcase.backdrop());
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(panelWidth()), Sizing.content());
        panel.surface(Showcase.panel(0xFF42A5F5));
        panel.padding(Insets.of(12));
        panel.gap(6);
        body = panel;
        fill();
        root.child(panel);
    }

    private int panelWidth() {
        return Math.min(460, width - 30);
    }

    private int textWidth() {
        return panelWidth() - 24;
    }

    private void fill() {
        body.clearChildren();
        body.child(new Showcase.BigText("WHAT'S NEW", 1.5f, 0xFFFFFF, 0xB9CCFF).margins(Insets.bottom(2)));
        body.child(label(releases.size() > 1 ? "Everything since the version you played before, newest first." : "What came with this version.", dim()).maxWidth(textWidth()));
        FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(3);
        if (releases.isEmpty()) list.child(label("Nothing new to show.", text()));
        for (Releases.Release r : releases) release(list, r);
        body.child(SbcWidgets.scroll(Sizing.fill(100), Sizing.fixed(Math.max(140, Math.min(300, height - 120))), list));
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.margins(Insets.top(4));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("The full changelog"), b -> UpdateScreen.openLink(CHANGELOG))).horizontalSizing(Sizing.fixed(130)));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Done"), b -> onClose())).horizontalSizing(Sizing.expand()));
        body.child(row);
    }

    private void release(FlowLayout list, Releases.Release r) {
        boolean shown = open.contains(r.version());
        FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.verticalAlignment(VerticalAlignment.CENTER);
        head.margins(Insets.top(4));
        head.child(label(r.name(), Color.ofArgb(Modern.AQUA)).horizontalSizing(Sizing.expand()));
        if (releases.size() > 1)
            head.child(SettingsScreen.ui(SbcWidgets.button(Component.literal(shown ? "Hide" : "Show"), b -> {
                if (!open.remove(r.version())) open.add(r.version());
                fill();
            })).horizontalSizing(Sizing.fixed(50)));
        list.child(head);
        if (!shown) return;
        boolean any = false;
        for (String id : r.features()) {
            Feature f = Features.get(id);
            if (f == null || f.alwaysOn) continue;
            if (!any) {
                list.child(label("New helpers. They stay off until you switch them on:", dim()).margins(Insets.top(3)));
                any = true;
            }
            helper(list, f);
        }
        // everything that changed, as the changelog says it: new features and improvements open, the rest folded
        for (Changelog.Section s : Changelog.of(r.version())) {
            String key = r.version() + "/" + s.name();
            boolean folded = s.name().startsWith("New") || s.name().startsWith("Improvements") ? closed.contains(key) : !opened.contains(key);
            FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.margins(Insets.top(4));
            row.child(label(s.name() + " (" + s.entries().size() + ")", text()).horizontalSizing(Sizing.expand()));
            row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal(folded ? "Show" : "Hide"), b -> {
                if (s.name().startsWith("New") || s.name().startsWith("Improvements")) {
                    if (!closed.remove(key)) closed.add(key);
                } else if (!opened.remove(key)) opened.add(key);
                fill();
            })).horizontalSizing(Sizing.fixed(50)));
            list.child(row);
            if (folded) continue;
            String area = null;
            for (Changelog.Entry e : s.entries()) {
                if (!e.area().isEmpty() && !e.area().equals(area)) {
                    area = e.area();
                    list.child(label(area, dim()).margins(Insets.of(2, 0, 0, 4)));
                }
                list.child(label("- " + e.text(), text()).maxWidth(textWidth() - 14).margins(Insets.left(6)));
                for (String m : e.more()) list.child(label(m, dim()).maxWidth(textWidth() - 24).margins(Insets.left(16)));
            }
        }
    }

    // a new helper: its switch, what it does, its options and how its card looks
    private void helper(FlowLayout list, Feature f) {
        list.child(SettingsScreen.ui(SbcWidgets.toggle(Component.literal(f.title)).checked(f.enabled()).onChanged(on -> {
            f.setEnabled(on);
            Config.save();
            fill();
        })).horizontalSizing(Sizing.fill(100)));
        if (!f.description.isEmpty()) list.child(label(f.description, dim()).maxWidth(textWidth() - 14).margins(Insets.left(4)));
        if (!f.enabled()) return;
        for (Option o : f.options) list.child(SettingsPanel.optionRow(o, this::fill).margins(Insets.left(8)));
        CardPreview card = CardPreview.of(f, textWidth() - 20);
        if (card != null) list.child(card.margins(Insets.of(2, 0, 2, 8)));
    }

    @Override
    public void onClose() {
        markSeen();
        super.onClose();
    }
}
