// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.TabList;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.ElementStyle;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.hud.Looks;
import dev.zmuzy.sbc.ui.hud.ScoreboardElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import dev.zmuzy.sbc.ui.layout.LabelComponent;
import dev.zmuzy.sbc.ui.layout.UIComponents;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.UIContainers;
import dev.zmuzy.sbc.ui.layout.Color;
import dev.zmuzy.sbc.ui.layout.CursorStyle;
import dev.zmuzy.sbc.ui.layout.HorizontalAlignment;
import dev.zmuzy.sbc.ui.layout.Insets;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.Surface;
import dev.zmuzy.sbc.ui.layout.UIComponent;
import dev.zmuzy.sbc.ui.layout.VerticalAlignment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// the settings: every feature of every area with its switch, options and health, built from the feature
// registry. the search filters by any word in a title, description or option
public final class SettingsPanel {

    private final Screen host;
    private final boolean embedded;
    // what was typed in the search, kept when the K menu closes and opens again
    private static String typed = "";
    private String query = typed.trim().toLowerCase(Locale.ROOT);
    private String section = null;
    private final java.util.Map<String, LabelComponent> headingCounts = new java.util.HashMap<>();
    private FlowLayout list, sections, top;
    private int listWidth = 300;

    public SettingsPanel(Screen host, boolean embedded) {
        this.host = host;
        this.embedded = embedded;
    }

    // opens on one feature: its section selected + search set to its title
    public void focus(String featureId) {
        Feature f = Features.get(featureId);
        if (f != null) {
            section = moduleShown(f.module) ? f.module : null;
            typed = f.title;
            query = f.title.toLowerCase(Locale.ROOT);
        }
    }

    static UIComponent ui(Object vanillaBased) {
        return (UIComponent) vanillaBased;
    }

    private static LabelComponent label(String s, Color c) {
        return SbcScreen.label(s, c);
    }

    private static Color accent() {
        return SbcScreen.accent();
    }

    private static Color text() {
        return SbcScreen.text();
    }

    private static Color dim() {
        return SbcScreen.dim();
    }

    // sections in the order you read them: screen stuff first, then what you do, then the rest
    public static final List<String> SECTIONS = List.of("HUD", "Bar parts", "Chat", "Items", "Menus", "Navigation", "Tracking", "Skills", "Pets",
            "Dungeons", "Slayer", "Combat", "Crimson Isle", "Rift", "Mining", "Garden", "Farming Islands", "Foraging", "Fishing", "Hunting", "Events", "Minions",
            "World", "Commands", "Misc");

    private static List<String> modules() {
        List<String> out = new ArrayList<>();
        List<String> known = Features.modules();
        for (String m : SECTIONS) if (known.contains(m)) out.add(m);
        for (String m : known) if (!out.contains(m)) out.add(m);
        return out;
    }

    // a helper with no switch and no setting has nothing here so it stays out, unless it paused itself after
    // errors and needs Resume. the 2 that only say which key to bind stay
    public static boolean listed(Feature f, boolean hasCard) {
        return f.paused || !f.alwaysOn || !f.options.isEmpty() || hasCard || f.id.equals("hunting_keys") || f.id.equals("abiphone_key");
    }

    private static boolean listed(Feature f) {
        return listed(f, !elementsOf(f).isEmpty());
    }

    private static List<Feature> inModule(String module) {
        return Features.all().stream().filter(f -> f.module.equals(module) && listed(f)).toList();
    }

    // every area has its own color + icon
    private static int moduleColor(String module) {
        return switch (module == null ? "" : module) {
            case "" -> 0xFF5CD8FF;
            case "Dungeons" -> 0xFFFF5D73;
            case "Items" -> 0xFF7FD4FF;
            case "Mining" -> 0xFF4FD8C4;
            case "Chat" -> 0xFFB0BEC5;
            case "Commands" -> 0xFF8FD3A8;
            case "Misc" -> 0xFF90A4AE;
            case "HUD" -> 0xFF5CD8FF;
            case "Events" -> 0xFFFF9EC7;
            case "Garden" -> 0xFF9CE37D;
            case "Menus" -> 0xFF7FB3FF;
            case "Bar parts" -> 0xFF5CD8FF;
            case "Farming Islands" -> 0xFFA5D6A7;
            case "Skills" -> 0xFF4FD8C4;
            case "Pets" -> 0xFFFFB454;
            case "World" -> 0xFF81C784;
            case "Navigation" -> 0xFF58C7FF;
            case "Slayer" -> 0xFFFF5252;
            case "Foraging" -> 0xFF81C784;
            case "Fishing" -> 0xFF4FC3F7;
            case "Combat" -> 0xFFFF8A65;
            case "Tracking" -> 0xFFFFD166;
            case "Rift" -> 0xFFD070FF;
            case "Minions" -> 0xFFFFB454;
            case "Hunting" -> 0xFFA5D6A7;
            case "Crimson Isle" -> 0xFFFF6B4A;
            default -> Looks.colourFor(module);
        };
    }

    private static String moduleIcon(String module) {
        return switch (module == null ? "" : module) {
            case "" -> "nether_star";
            case "Dungeons" -> "skeleton_skull";
            case "Items" -> "chest";
            case "Mining" -> "iron_pickaxe";
            case "Chat" -> "paper";
            case "Commands" -> "command_block";
            case "Misc" -> "book";
            case "HUD" -> "compass";
            case "Events" -> "firework_rocket";
            case "Garden" -> "wheat";
            case "Menus" -> "painting";
            case "Bar parts" -> "golden_apple";
            case "Farming Islands" -> "hay_block";
            case "Skills" -> "enchanting_table";
            case "Pets" -> "bone";
            case "World" -> "grass_block";
            case "Navigation" -> "recovery_compass";
            case "Slayer" -> "rotten_flesh";
            case "Foraging" -> "oak_sapling";
            case "Fishing" -> "fishing_rod";
            case "Combat" -> "iron_sword";
            case "Tracking" -> "clock";
            case "Rift" -> "ender_eye";
            case "Minions" -> "hopper";
            case "Hunting" -> "lead";
            case "Crimson Isle" -> "blaze_powder";
            default -> "book";
        };
    }

    // builds into an already sized root, width = roots width in gui px
    public void build(FlowLayout root, int width) {
        int sectionsW = 134;
        FlowLayout panel;
        if (embedded) {
            panel = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
            panel.gap(6);
            root.child(panel);
            listWidth = Math.max(160, width - sectionsW - 30);
        } else {
            root.surface(SbcScreen.backdrop());
            root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            panel = UIContainers.horizontalFlow(Sizing.fill(92), Sizing.fill(90));
            panel.gap(6);
            panel.surface(SbcScreen.panel());
            panel.padding(Insets.of(6));
            root.child(panel);
            listWidth = Math.max(200, (int) (width * 0.92) - sectionsW - 40);
        }
        sections = UIContainers.verticalFlow(Sizing.fixed(sectionsW - 6), Sizing.content());
        sections.gap(1);
        panel.child(SbcWidgets.scroll(Sizing.fixed(sectionsW), Sizing.fill(100), sections));

        FlowLayout right = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        right.gap(4);
        top = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        top.gap(6);
        top.verticalAlignment(VerticalAlignment.CENTER);
        right.child(top);
        SbcWidgets.Input search = SbcWidgets.input(Sizing.fill(100), typed);
        search.setHint(Component.literal("Search settings... (e.g. scoreboard, slayer, price, chat)"));
        search.onChanged(s -> {
            typed = s;
            query = s.trim().toLowerCase(Locale.ROOT);
            rebuild();
        });
        right.child(ui(search));
        list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(4);
        list.padding(Insets.right(5));
        right.child(SbcWidgets.scroll(Sizing.fill(100), Sizing.expand(), list));
        panel.child(right);
        buildTop();
        buildSections();
        rebuild();
    }

    private void buildTop() {
        top.<FlowLayout>configure(t -> fillTop());
    }

    private void fillTop() {
        top.clearChildren();
        top.child(label("Every helper of the mod, by section. Changes save at once.", dim()).horizontalSizing(Sizing.expand()));
        int all = 0, on = 0;
        for (Feature f : Features.all()) {
            if (!moduleShown(f.module) || !listed(f)) continue;
            all++;
            if (f.enabled()) on++;
        }
        top.child(label(on + "/" + all + " on", Color.ofArgb(Modern.GOOD)));
    }

    static boolean moduleShown(String module) {
        return true;
    }

    private void buildSections() {
        sections.<FlowLayout>configure(t -> fillSections());
    }

    private void fillSections() {
        sections.clearChildren();
        sections.child(sectionButton("All", null));
        for (String m : modules()) if (moduleShown(m)) sections.child(sectionButton(m, m));
        UIComponent credits = ui(SbcWidgets.button(Component.literal("Credits"), b -> CreditsScreen.open(host)));
        credits.horizontalSizing(Sizing.fill(100));
        credits.margins(Insets.top(6));
        sections.child(credits);
        UIComponent setup = ui(SbcWidgets.button(Component.literal("First-run setup"), b -> SetupScreen.open(host)));
        setup.horizontalSizing(Sizing.fill(100));
        sections.child(setup);
        UIComponent discord = ui(SbcWidgets.button(Component.literal("Discord"), b -> SetupScreen.openDiscord()));
        discord.horizontalSizing(Sizing.fill(100));
        sections.child(discord);
        sections.child(label(embedded ? "Changes save at once." : "Esc closes. Changes save at once.", dim()).maxWidth(108).margins(Insets.top(6)));
    }

    private UIComponent sectionButton(String name, String module) {
        boolean on = module == null ? section == null : module.equals(section);
        int color = moduleColor(module);
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.padding(Insets.of(0, 0, 4, 2));
        row.gap(3);
        row.cursorStyle(CursorStyle.HAND);
        row.surface((g, c) -> {
            int x = c.x(), y = c.y(), w = c.width(), h = c.height();
            if (on) {
                dev.zmuzy.sbc.ui.screens.SbcGradient.rect(g, x, y, w, h, Ui.alpha(color, 0.42f), Ui.alpha(color, 0.04f), Ui.alpha(color, 0.04f), Ui.alpha(color, 0.42f));
                g.fill(x, y, x + 2, y + h, color);
            }
        });
        row.mouseDown().subscribe((click, doubled) -> {
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
            SbcWidgets.clickSound();
            section = module;
            buildSections();
            rebuild();
            return true;
        });
        SbcWidgets.Icon icon = new SbcWidgets.Icon(LegacyItems.stack(moduleIcon(module)), 10);
        icon.cursorStyle(CursorStyle.HAND);
        row.child(icon);
        List<Feature> fs = module == null ? Features.all().stream().filter(f -> moduleShown(f.module) && listed(f)).toList() : inModule(module);
        long enabled = fs.stream().filter(Feature::enabled).count();
        String count = enabled + "/" + fs.size();
        LabelComponent name2 = label(Ui.MEASURE.ellipsize(name, 102 - Ui.MEASURE.width(count)), on ? text() : Color.ofArgb(Modern.LABEL));
        name2.cursorStyle(CursorStyle.HAND);
        name2.horizontalSizing(Sizing.expand());
        row.child(name2);
        LabelComponent n = label(count, Color.ofArgb(enabled == fs.size() ? Modern.DIM : Modern.WARN));
        n.cursorStyle(CursorStyle.HAND);
        row.child(n);
        return row;
    }

    private String[] words() {
        return query.isEmpty() ? new String[0] : query.split("\\s+");
    }

    private boolean matches(Feature f, String[] words) {
        if (words.length == 0) return true;
        String hay = (f.title + " " + f.description + " " + f.module + " " + f.id).toLowerCase(Locale.ROOT);
        boolean all = true;
        for (String w : words) if (!hay.contains(w)) all = false;
        if (all) return true;
        for (Option o : f.options) if (o.matches(words)) return true;
        return false;
    }

    private void rebuild() {
        list.<FlowLayout>configure(l -> fillList());
    }

    // a switch changed: only rebuild its card, the whole list is slow enough to feel on every click
    private void refreshCard(Feature f, String[] words, FlowLayout holder) {
        holder.<FlowLayout>configure(h -> {
            h.clearChildren();
            h.child(card(f, words, holder));
        });
        LabelComponent count = headingCounts.get(f.module);
        if (count != null) {
            List<Feature> fs = inModule(f.module);
            count.text(Component.literal(fs.stream().filter(Feature::enabled).count() + "/" + fs.size() + " on"));
        }
    }

    private void fillList() {
        list.clearChildren();
        String[] words = words();
        int shown = 0;
        for (String module : modules()) {
            if (!moduleShown(module)) continue;
            if (section != null && !section.equals(module)) continue;
            List<Feature> fs = inModule(module).stream().filter(f -> matches(f, words)).toList();
            if (fs.isEmpty()) continue;
            list.child(moduleHeading(module, fs));
            for (Feature f : fs) {
                FlowLayout holder = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
                holder.child(card(f, words, holder));
                list.child(holder);
                shown++;
            }
        }
        if (shown == 0) list.child(label(query.isEmpty() ? "Nothing here yet." : "No setting matches \"" + query + "\".", dim()));
        if ((section == null || "Misc".equals(section)) && (words.length == 0 || "data reset".contains(words[0]))) list.child(dataCard());
    }

    // area name in its color + how many of its features are on
    private FlowLayout moduleHeading(String module, List<Feature> fs) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);
        row.margins(Insets.top(2));
        int color = moduleColor(module);
        row.child(new SbcWidgets.Icon(LegacyItems.stack(moduleIcon(module)), 10));
        row.child(new SbcWidgets.Heading(module, color).horizontalSizing(Sizing.expand()));
        long on = fs.stream().filter(Feature::enabled).count();
        LabelComponent count = label(on + "/" + fs.size() + " on", Color.ofArgb(Modern.DIM));
        headingCounts.put(module, count);
        row.child(count);
        return row;
    }

    private static List<HudElement> elementsOf(Feature f) {
        List<HudElement> out = new ArrayList<>();
        for (HudElement e : HudLayout.elements()) if (e.feature() == f) out.add(e);
        return out;
    }

    // small colored state tag for the title line
    private static FlowLayout tag(String text, int color) {
        FlowLayout tag = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        tag.padding(Insets.of(1, 1, 3, 3));
        int c = color | 0xFF000000;
        tag.surface((g, p) -> {
            g.fill(p.x(), p.y(), p.x() + p.width(), p.y() + p.height(), Ui.alpha(c, 0.22f));
            g.fill(p.x(), p.y() + p.height() - 1, p.x() + p.width(), p.y() + p.height(), Ui.alpha(c, 0.8f));
        });
        tag.child(label(text, Color.ofArgb(Modern.lighten(c, 0.35f))));
        return tag;
    }

    private FlowLayout card(Feature f, String[] words, FlowLayout holder) {
        FlowLayout card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.gap(3);
        int color = f.enabled() ? moduleColor(f.module) : Modern.DIM;
        card.surface(SbcScreen.glass(color, true));
        card.padding(Insets.of(1, 5, 5, 4));

        FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(12));
        head.verticalAlignment(VerticalAlignment.CENTER);
        head.gap(4);
        head.child(label(f.title, f.enabled() ? (Color.ofArgb(Modern.lighten(color | 0xFF000000, 0.45f))) : dim()).horizontalSizing(Sizing.expand()));
        if (f.paused) head.child(tag("PAUSED", Modern.BAD));
        if (!f.alwaysOn) {
            SbcWidgets.Toggle cb = SbcWidgets.toggle(Component.empty()).checked(f.enabled());
            cb.onChanged(v -> {
                f.setEnabled(v);
                refreshCard(f, words, holder);
                buildTop();
                buildSections();
            });
            head.child(ui(cb));
        } else {
            head.child(label("always on", Color.ofArgb(Modern.DIM)));
        }
        card.child(head);
        card.child(label(f.description, Color.ofArgb(Modern.LABEL)).maxWidth(listWidth - 20).margins(Insets.top(3)));

        boolean showAll = words.length == 0 || f.options.stream().noneMatch(o -> o.matches(words));
        for (Option o : f.options) if (showAll || o.matches(words)) card.child(optionRow(o));

        for (HudElement e : elementsOf(f)) {
            if (!e.pickable().isEmpty()) {
                FlowLayout box = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
                box.gap(1);
                box.child(new SbcWidgets.Heading(e.title, color));
                List<UIComponent> toggles = new ArrayList<>();
                for (String name : e.pickable()) {
                    SbcWidgets.Toggle cb = SbcWidgets.toggle(Component.literal(name)).checked(e.picked(name));
                    cb.onChanged(v -> {
                        e.pick(name, v);
                        Config.save();
                    });
                    toggles.add(ui(cb));
                }
                inColumns(box, toggles, 2);
                card.child(box);
            }
            List<HudElement.Row> rows = e.demo();
            if (rows.isEmpty() || e == ScoreboardElement.I) continue;
            FlowLayout rowsBox = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            rowsBox.margins(Insets.left(0));
            rowsBox.gap(1);
            rowsBox.child(new SbcWidgets.Heading("Rows of " + e.title, color));
            ElementStyle st = ElementStyle.of(e.id);
            List<UIComponent> toggles = new ArrayList<>();
            for (HudElement.Row r : rows) {
                String lab = r.label();
                if (lab.isEmpty()) continue;
                SbcWidgets.Toggle cb = SbcWidgets.toggle(Component.literal(lab.length() > 32 ? lab.substring(0, 32) + "…" : lab)).checked(!st.isHidden(lab));
                cb.onChanged(v -> {
                    st.setHidden(lab, !v);
                    Config.save();
                });
                toggles.add(ui(cb));
            }
            inColumns(rowsBox, toggles, 2);
            card.child(rowsBox);
        }
        if (f.id.equals("board")) card.child(boardEditor());
        if (f.id.equals("tablist")) card.child(tabEditor());
        if (f.id.equals("quicknav")) card.child(dev.zmuzy.sbc.ui.panels.QuickNavEditor.editor(listWidth));
        var commandEditor = dev.zmuzy.sbc.commands.CommandPack.settingsButton(f.id);
        if (commandEditor != null) card.child(commandEditor);

        if (f.paused) {
            FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(4);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(label("Paused: " + f.lastError, Color.ofArgb(0xFFEF9F27)).maxWidth(listWidth - 90).horizontalSizing(Sizing.expand()));
            row.child(ui(SbcWidgets.button(Component.literal("Resume"), b -> {
                f.resume();
                refreshCard(f, words, holder);
            })));
            card.child(row);
        } else if (f.calls > 0 && Config.I.perfOverlay) {
            card.child(label(String.format(Locale.ROOT, "%.2f ms per call, %d calls", f.avgMs(), f.calls), dim()));
        }
        return card;
    }

    // equal columns so a long list of switches stays compact
    private void inColumns(FlowLayout box, List<UIComponent> items, int cols) {
        int w = Math.max(80, (listWidth - 16 - (cols - 1) * 3) / cols);
        FlowLayout cur = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                cur = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                cur.gap(3);
                box.child(cur);
            }
            UIComponent c = items.get(i);
            c.horizontalSizing(Sizing.fixed(w));
            cur.child(c);
        }
    }

    private UIComponent smallButton(String text, java.util.function.Consumer<SbcWidgets.Button> onPress, int w) {
        UIComponent c = ui(SbcWidgets.button(Component.literal(text), onPress));
        c.horizontalSizing(Sizing.fixed(w));
        return c;
    }

    private FlowLayout dataCard() {
        FlowLayout card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.gap(3);
        card.surface(SbcScreen.glass(Modern.GOLD, true));
        card.padding(Insets.of(1, 5, 5, 4));
        FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(12));
        head.verticalAlignment(VerticalAlignment.CENTER);
        head.gap(4);
        head.child(label("Data", Color.ofArgb(Modern.lighten(Modern.GOLD, 0.45f))).horizontalSizing(Sizing.expand()));
        card.child(head);
        FlowLayout tools = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        tools.gap(4);
        tools.child(ui(SbcWidgets.button(Component.literal("Reset today's checkmarks"), b -> dev.zmuzy.sbc.core.Store.I.progress.remove(dev.zmuzy.sbc.core.SbTime.periodKey(dev.zmuzy.sbc.core.SbTime.Period.DAILY, System.currentTimeMillis())))));
        tools.child(ui(SbcWidgets.button(Component.literal("Forget taught patterns"), b -> dev.zmuzy.sbc.core.Store.I.learned.clear())));
        tools.child(ui(SbcWidgets.button(Component.literal("Reset measured rates"), b -> dev.zmuzy.sbc.core.Store.I.rates.clear())));
        if (!Config.I.hiddenTasks.isEmpty()) tools.child(ui(SbcWidgets.button(Component.literal("Show " + Config.I.hiddenTasks.size() + " hidden task(s)"), b -> {
            Config.I.hiddenTasks.clear();
            Config.save();
            rebuild();
        })));
        card.child(tools);
        return card;
    }

    // custom scoreboard: entries in order, up/down/remove, add lines, import from skyhanni
    private FlowLayout boardEditor() {
        FlowLayout box = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        box.margins(Insets.left(0));
        box.gap(2);
        box.child(label("Lines, in order (the lines Hypixel sends that no entry claims stay under Events)", dim()).maxWidth(listWidth - 30));
        List<String> entries = ScoreboardElement.entries();
        for (int i = 0; i < entries.size(); i++) {
            final int idx = i;
            String e = entries.get(i);
            FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(3);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(smallButton("▲", b -> {
                if (idx > 0) {
                    entries.add(idx - 1, entries.remove(idx));
                    Config.save();
                    rebuild();
                }
            }, 16));
            row.child(smallButton("▼", b -> {
                if (idx < entries.size() - 1) {
                    entries.add(idx + 1, entries.remove(idx));
                    Config.save();
                    rebuild();
                }
            }, 16));
            row.child(label(ScoreboardElement.NAMES.getOrDefault(e, e), text()).horizontalSizing(Sizing.expand()));
            row.child(smallButton("✕", b -> {
                entries.remove(idx);
                Config.save();
                rebuild();
            }, 16));
            box.child(row);
        }
        box.child(label("Add a line", dim()).margins(Insets.top(3)));
        FlowLayout cur = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        cur.gap(3);
        int n = 0;
        for (String e : ScoreboardElement.ALL) {
            if (!e.equals("EMPTY_LINE") && entries.contains(e)) continue;
            String name = ScoreboardElement.NAMES.getOrDefault(e, e);
            UIComponent b = ui(SbcWidgets.button(Component.literal("+ " + name), bt -> {
                entries.add(e);
                Config.save();
                rebuild();
            }));
            cur.child(b);
            if (++n % 3 == 0) {
                box.child(cur);
                cur = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                cur.gap(3);
            }
        }
        if (n % 3 != 0) box.child(cur);
        FlowLayout tools = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        tools.gap(3);
        tools.margins(Insets.top(3));
        tools.child(ui(SbcWidgets.button(Component.literal("Import from another mod"), b -> {
            String msg = dev.zmuzy.sbc.core.ImportConfigs.skyhanniScoreboard();
            b.setMessage(Component.literal(msg.startsWith("Imported") ? "Imported" : "Not found"));
            rebuild();
        })));
        tools.child(ui(SbcWidgets.button(Component.literal("Default order"), b -> {
            entries.clear();
            entries.addAll(ScoreboardElement.DEFAULT);
            Config.save();
            rebuild();
        })));
        tools.child(ui(SbcWidgets.button(Component.literal("Edit look (HUD editor)"), b -> HudEditorScreen.open())));
        box.child(tools);
        return box;
    }

    // tab list: every widget hypixel sent so far with on/off, never seen ones dimmed
    private FlowLayout tabEditor() {
        FlowLayout box = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        box.margins(Insets.left(0));
        box.gap(2);
        box.child(label("Widgets. Hypixel decides which it sends (its /tab widget settings, per island, with a limit on how many fit); the ones it never sent here are dimmed.", dim()).maxWidth(listWidth - 30));
        List<String> ids = new ArrayList<>();
        for (dev.zmuzy.sbc.core.TabWidgets.Def d : dev.zmuzy.sbc.core.TabWidgets.defs()) ids.add(d.id());
        for (String k : Config.I.tabSeen) if (!ids.contains(k)) ids.add(k);
        FlowLayout cur = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        cur.gap(3);
        int n = 0;
        for (String k : ids) {
            boolean seen = Config.I.tabSeen.contains(k);
            dev.zmuzy.sbc.core.TabWidgets.Def def = dev.zmuzy.sbc.core.TabWidgets.byId(k);
            String shownName = def == null ? k : def.name();
            SbcWidgets.Toggle cb = SbcWidgets.toggle(Component.literal(seen ? shownName : shownName + " (not sent yet)")).checked(!TabList.hidden(k));
            cb.onChanged(v -> {
                TabList.setHidden(k, !v);
                Config.save();
            });
            UIComponent c = ui(cb);
            c.horizontalSizing(Sizing.fixed(Math.max(120, (listWidth - 40) / 3)));
            cur.child(c);
            if (++n % 3 == 0) {
                box.child(cur);
                cur = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                cur.gap(3);
            }
        }
        if (n % 3 != 0) box.child(cur);
        return box;
    }

    private FlowLayout optionRow(Option o) {
        return optionRow(o, this::rebuild);
    }

    // one option with its control, as the settings show it. changes are saved right away; rebuild runs after a reset
    static FlowLayout optionRow(Option o, Runnable rebuild) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.margins(Insets.left(0));
        Surface hair = (g, c) -> g.fill(c.x(), c.y() - 2, c.x() + c.width(), c.y() - 1, 0x1CFFFFFF);
        row.surface(hair);
        row.allowOverflow(true);
        var name = label(o.label, Color.ofArgb(Modern.LABEL));
        name.horizontalSizing(Sizing.expand());
        if (!o.description.isEmpty()) name.tooltip(Component.literal(o.description));
        row.child(name);
        switch (o.kind) {
            case BOOL -> {
                SbcWidgets.Toggle cb = SbcWidgets.toggle(Component.empty()).checked(o.bool());
                cb.onChanged(v -> {
                    o.set(v);
                    Config.save();
                });
                row.child(ui(cb));
            }
            case INT, FLOAT -> {
                SbcWidgets.Slider s = SbcWidgets.slider(Sizing.fixed(120), o.min, o.max);
                s.decimalPlaces(o.step >= 1 ? 0 : o.step >= 0.1 ? 1 : 2);
                s.setFromDiscreteValue(o.number());
                s.onChanged(v -> {
                    double snapped = o.step > 0 ? Math.round(v / o.step) * o.step : v;
                    o.set(o.kind == Option.Kind.INT ? (Object) (int) Math.round(snapped) : (Object) snapped);
                    Config.save();
                });
                row.child(ui(s));
            }
            case ENUM -> {
                SbcWidgets.Button b = SbcWidgets.button(Component.literal(o.choice()), bt -> {
                    int i = (o.choiceIndex() + 1) % o.choices.length;
                    o.set(o.choices[i]);
                    Config.save();
                    bt.setMessage(Component.literal(o.choice()));
                });
                UIComponent c = ui(b);
                c.horizontalSizing(Sizing.fixed(120));
                row.child(c);
            }
            case COLOR, TEXT -> {
                SbcWidgets.Input tb = SbcWidgets.input(Sizing.fixed(o.kind == Option.Kind.COLOR ? 80 : 120), o.valueText());
                tb.onChanged(s -> {
                    if (o.kind == Option.Kind.COLOR) {
                        try {
                            String h = s.replace("#", "").trim();
                            long l = Long.parseLong(h, 16);
                            o.set((int) (h.length() <= 6 ? (l | 0xFF000000L) : l));
                        } catch (Exception ignored) {
                            return;
                        }
                    } else o.set(s);
                    Config.save();
                });
                if (o.kind == Option.Kind.COLOR) row.child(label("■", Color.ofArgb(o.color())));
                row.child(ui(tb));
            }
        }
        if (!o.isDefault()) {
            UIComponent reset = ui(SbcWidgets.button(Component.literal("↺"), b -> {
                o.reset();
                Config.save();
                rebuild.run();
            }));
            reset.tooltip(Component.literal("Back to the default: " + o.def));
            row.child(reset);
        }
        return row;
    }
}
