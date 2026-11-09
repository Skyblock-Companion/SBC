// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.screens;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.ui.HudGroups;
import dev.zmuzy.sbc.ui.hud.Looks;
import dev.zmuzy.sbc.ui.layout.Color;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.HorizontalAlignment;
import dev.zmuzy.sbc.ui.layout.Insets;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.UIComponent;
import dev.zmuzy.sbc.ui.layout.UIContainers;
import dev.zmuzy.sbc.ui.layout.VerticalAlignment;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// page a new player sees once, first time the game hits the title screen with the mod. fast: a few general
// questions about how you play and what you like. detailed: the same, then every helper of what you play, one
// page per activity. nothing is answered for you and nothing changes till Finish; K changes anything later
public final class SetupScreen extends SbcScreen {
    public static final String DISCORD = "https://discord.gg/QrqX3pgVW2";
    private static final String[] PAGES = {"Welcome", "What you play", "Your screen", "Chat", "Daily routine", "Preferences", "Done"};
    static final int LAST = PAGES.length - 1;

    // chat filters that hide or shorten lines and are on from the start: "cleaner" keeps them, "as hypixel sends it"
    // turns them off
    static final List<String> CHAT_CLEANER = List.of("chat_lobby", "chat_implosion", "chat_warp", "chat_aote", "chat_repeat", "chat_guildexp",
            "chat_dungeon_start", "chat_combo", "chat_dungeon_boss", "chat_mana", "chat_lobbies", "chat_pet", "chat_bazaar", "chat_dungeon", "chat_noise",
            "chat_feast_chef", "chat_heal", "chat_sacrifice", "chat_raredrops_others", "chat_chicken_head", "chat_compact_jacob", "chat_compact_chest",
            "chat_compact_potions", "chat_hoppity_begin", "chat_stash_compact", "chat_compact_bestiary", "chat_event_level", "chat_compact_experiments",
            "chat_hoppity_appear", "chat_shards", "chat_anita", "chat_watchdog", "chat_firesale", "chat_skymall", "bingo_compact_chat");
    // the ones that are off from the start: "as clean as possible" adds them
    static final List<String> CHAT_CLEANEST = List.of("chat_ads", "chat_join_leave", "chat_social", "chat_teleport_pads", "chat_stash", "chat_parkour",
            "chat_rewardbundle", "chat_others", "chat_arachne", "chat_lottery", "chat_empty", "chat_hide_far_deaths", "chat_showoff", "gift_chat_filter",
            "chat_factory_upgrade", "chat_legacy_items");
    static final String CHAT_HYPIXEL = "hypixel", CHAT_CLEAN = "cleaner", CHAT_CLEANEST_ID = "cleanest";

    private int page;
    // the answers. null = not answered yet: Next waits for every question on its page
    private final Set<String> playing = new LinkedHashSet<>();
    private Float size;
    private Boolean bars, smooth, routine, rotate, throughWalls, viewmodel, briefing;
    private String chat;
    private String preset = Config.I.routinePreset;
    private boolean autoGuide = Config.I.autoGuide;
    // Next pressed with a question left open
    private boolean missing;
    // the detailed setup: after the questions, a page per activity you play and one for the general helpers
    private boolean detailed;
    private static final int GROUP_PAGE = 100;
    private int groupAt;
    // helpers switched by hand on those pages. only these are applied, so they never undo an answer
    private final java.util.Map<String, Boolean> changed = new java.util.LinkedHashMap<>();
    // helpers whose options and card are unfolded on a detailed page
    private final Set<String> unfolded = new java.util.HashSet<>();
    private FlowLayout body;

    public SetupScreen(Screen parent) {
        super("SkyBlock Companion setup", parent);
    }

    private static boolean openedThisRun;
    // main menu as this game has it (vanilla or another mods), once its settled
    private static Class<?> menuClass;
    private static Screen seen;
    private static int settled;

    // every tick: once the main menu shows and loading is done, setup opens (first start only), then the keys,
    // then the update card if theres one. main menu = vanilla title screen or whatever another mod put there
    public static void titleTick(Minecraft mc) {
        Screen s = mc.gui.screen();
        boolean onMenu = isMenu(mc, s);
        if (TutorialScreen.resumeTick(mc, onMenu)) return;
        if (!onMenu) return;
        if (!Config.I.setupDone) {
            if (!openedThisRun) {
                openedThisRun = true;
                mc.gui.setScreen(new SetupScreen(s));
            }
            return;
        }
        if (WhatsNewScreen.maybeShow(mc, s)) return;
        UpdateScreen.maybeShow(mc, s);
    }

    // main menu = same screen for 1 sec, no world and nothing loading. works with custom menus too
    static boolean isMenu(Minecraft mc, Screen s) {
        if (s == null || mc.level != null || mc.gui.overlay() != null || transitional(s)) {
            seen = null;
            settled = 0;
            return false;
        }
        if (s instanceof net.minecraft.client.gui.screens.TitleScreen) return true;
        if (menuClass != null) return s.getClass() == menuClass;
        if (s != seen) {
            seen = s;
            settled = 0;
            return false;
        }
        if (++settled < 20) return false;
        menuClass = s.getClass();
        return true;
    }

    // screens on the way somewhere (connecting, loading, a message) + our own pages, never the main menu
    private static boolean transitional(Screen s) {
        return s instanceof SbcScreen || s instanceof LegacyHost || s instanceof net.minecraft.client.gui.screens.ConnectScreen
                || s instanceof net.minecraft.client.gui.screens.ProgressScreen || s instanceof net.minecraft.client.gui.screens.GenericMessageScreen
                || s instanceof net.minecraft.client.gui.screens.GenericWaitingScreen || s instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
                || s instanceof net.minecraft.client.gui.screens.DisconnectedScreen
                || s instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
                || s instanceof dev.zmuzy.sbc.ui.CompanionScreen || s instanceof dev.zmuzy.sbc.ui.MissionScreen
                || s instanceof dev.zmuzy.sbc.ui.MarketSearchScreen;
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new SetupScreen(parent));
    }

    // opens the discord invite in the browser, they pressed the button for it
    public static void openDiscord() {
        UpdateScreen.openLink(DISCORD);
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
        return Math.min(440, width - 30);
    }

    private int textWidth() {
        return panelWidth() - 24;
    }

    // width of one of 2 side by side cards
    private int half() {
        return (textWidth() - 6) / 2;
    }

    private void fill() {
        body.clearChildren();
        if (page == 0) {
            // floating island + name, then the welcome
            body.child(new Showcase.Logo(Math.max(48, Math.min(88, height / 4))));
            body.child(new Showcase.BigText("SKYBLOCK COMPANION", 2.5f, 0xFFFFFF, 0xB9CCFF));
            body.child(label("Every island, one clean HUD", dim()).horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100)).margins(Insets.bottom(4)));
        } else {
            body.child(steps());
            String title = page == GROUP_PAGE ? detailGroups().get(groupAt).name() : PAGES[page];
            body.child(new Showcase.BigText(title.toUpperCase(java.util.Locale.ROOT), 1.5f, 0xFFFFFF, 0xB9CCFF).margins(Insets.bottom(2)));
        }
        switch (page) {
            case 0 -> welcome();
            case 1 -> activities();
            case 2 -> screenPage();
            case 3 -> chatPage();
            case 4 -> routinePage();
            case 5 -> preferences();
            case GROUP_PAGE -> groupPage();
            default -> done();
        }
    }

    // where you are: dot per step, done ones lit, "Step 2 of 6" next to them
    private FlowLayout steps() {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.horizontalAlignment(HorizontalAlignment.CENTER);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);
        List<Object> flow = flow();
        int at = flow.indexOf(here()) + 1;
        for (int i = 1; i <= flow.size(); i++) {
            int on = i <= at ? 0xFF42A5F5 : 0x40FFFFFF;
            FlowLayout dot = UIContainers.horizontalFlow(Sizing.fixed(i == at ? 14 : 6), Sizing.fixed(4));
            dot.surface(dev.zmuzy.sbc.ui.layout.Surface.flat(on));
            row.child(dot);
        }
        row.child(label("  Step " + at + " of " + flow.size(), dim()));
        return row;
    }

    // the pages in order: the questions, then (detailed) a page per activity, then done
    List<Object> flow() {
        List<Object> out = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        if (detailed) out.addAll(detailGroups());
        out.add(LAST);
        return out;
    }

    private Object here() {
        return page == GROUP_PAGE ? detailGroups().get(groupAt) : page;
    }

    // the activities you picked (all of them when you picked none), then the general helpers
    List<HudGroups.Group> detailGroups() {
        List<HudGroups.Group> out = new ArrayList<>();
        for (HudGroups.Group g : HudGroups.ALL) if (g != HudGroups.GENERAL && (playing.isEmpty() || playing.contains(g.name()))) out.add(g);
        out.add(HudGroups.GENERAL);
        return out;
    }

    private void goTo(Object stop) {
        if (stop instanceof HudGroups.Group g) {
            page = GROUP_PAGE;
            groupAt = detailGroups().indexOf(g);
        } else page = (Integer) stop;
        missing = false;
        fill();
    }

    private void welcome() {
        int w = textWidth();
        body.child(label("A few quick questions about how you play and what you like. Nothing is picked for you, and nothing changes until you finish.", text())
                .maxWidth(w).horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100)));
        body.child(label("What isn't asked keeps its default, and everything can be changed later with " + key(openKey(), "K") + ".", dim())
                .maxWidth(w).horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100)));
        FlowLayout choices = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        choices.gap(6);
        choices.margins(Insets.top(4));
        choices.child(choice("Fast setup", "A few questions, about a minute", 0xFF4CAF50, () -> {
            detailed = false;
            go(1);
        }));
        choices.child(choice("Detailed setup", "The same questions, then every helper of what you play", 0xFF42A5F5, () -> {
            detailed = true;
            go(1);
        }));
        body.child(choices);
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(6);
        row.child(label("Or keep every default as it is.", dim()).horizontalSizing(Sizing.expand()));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Skip"), b -> skip())).horizontalSizing(Sizing.fixed(80)));
        body.child(row);
        body.child(discordRow());
    }

    private FlowLayout choice(String title, String line, int color, Runnable go) {
        FlowLayout card = UIContainers.verticalFlow(Sizing.fixed(half()), Sizing.content());
        card.surface(glass(color, true));
        card.padding(Insets.of(3, 5, 5, 5));
        card.gap(4);
        card.child(label(title, Color.ofArgb(Modern.lighten(color, 0.45f))).margins(Insets.bottom(4)));
        card.child(label(line, dim()).maxWidth(half() - 10));
        card.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Choose"), b -> go.run())).horizontalSizing(Sizing.fill(100)));
        return card;
    }

    // every helper of one activity, each with what it does and a switch that shows how it will be
    private void groupPage() {
        HudGroups.Group g = detailGroups().get(groupAt);
        body.child(label(g == HudGroups.GENERAL ? "The helpers every island has: chat, items, menus, the map and more. Switch off what you don't want."
                : "Every helper for " + g.name() + ". Switch off what you don't want.", text()).maxWidth(textWidth()));
        body.child(label("Options change as soon as you set them. The switches apply when you finish.", dim()).maxWidth(textWidth()));
        FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(3);
        String module = null;
        for (Feature f : helpers(g)) {
            if (!f.module.equals(module)) {
                module = f.module;
                list.child(label(module, Color.ofArgb(Modern.AQUA)).margins(Insets.top(3)));
            }
            String id = f.id;
            CardPreview card = CardPreview.of(f, textWidth() - 24);
            boolean extra = !f.options.isEmpty() || card != null;
            FlowLayout line = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            line.gap(4);
            line.verticalAlignment(VerticalAlignment.CENTER);
            line.child(SettingsScreen.ui(SbcWidgets.toggle(Component.literal(f.title)).checked(willBeOn(f)).onChanged(on -> changed.put(id, on)))
                    .horizontalSizing(Sizing.expand()));
            boolean open = unfolded.contains(id);
            if (extra) line.child(SettingsScreen.ui(SbcWidgets.button(Component.literal(open ? "Hide" : "Options"), b -> {
                if (!unfolded.remove(id)) unfolded.add(id);
                fill();
            })).horizontalSizing(Sizing.fixed(54)));
            list.child(line);
            if (!f.description.isEmpty()) list.child(label(f.description, dim()).maxWidth(textWidth() - 12).margins(Insets.left(4)));
            if (!open) continue;
            for (dev.zmuzy.sbc.feature.Option o : f.options) list.child(SettingsPanel.optionRow(o, this::fill).margins(Insets.left(8)));
            if (card != null) list.child(card.margins(Insets.of(2, 0, 2, 8)));
        }
        body.child(SbcWidgets.scroll(Sizing.fill(100), Sizing.fixed(Math.max(120, Math.min(260, height - 170))), list));
        body.child(nav());
    }

    // the switchable helpers of a group, by section
    static List<Feature> helpers(HudGroups.Group g) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : Features.all()) if (!f.alwaysOn && g.sections().contains(f.module)) out.add(f);
        out.sort(java.util.Comparator.comparing((Feature f) -> f.module).thenComparing(f -> f.title));
        return out;
    }

    // how a helper will be after Finish: a hand switch, else what the answers do to it, else as it is now
    boolean willBeOn(Feature f) {
        if (changed.containsKey(f.id)) return changed.get(f.id);
        if (chat != null && CHAT_CLEANER.contains(f.id)) return !chat.equals(CHAT_HYPIXEL);
        if (chat != null && CHAT_CLEANEST.contains(f.id)) return chat.equals(CHAT_CLEANEST_ID);
        if (viewmodel != null && f.id.equals(dev.zmuzy.sbc.render.HeldItem.ID)) return viewmodel;
        return f.enabled();
    }

    private void activities() {
        int w = textWidth();
        body.child(label("Pick what you play. Their helpers stay on and the others switch off. Pick nothing and every island keeps its helpers.", text()).maxWidth(w));
        FlowLayout grid = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        grid.gap(3);
        FlowLayout row = null;
        int n = 0;
        for (HudGroups.Group g : HudGroups.ALL) {
            if (g == HudGroups.GENERAL) continue;
            if (n++ % 2 == 0) {
                row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                row.gap(6);
                grid.child(row);
            }
            FlowLayout cell = UIContainers.horizontalFlow(Sizing.fixed(half()), Sizing.content());
            cell.surface(glass(Looks.colourFor(g.name()), false));
            cell.padding(Insets.of(4));
            cell.verticalAlignment(VerticalAlignment.CENTER);
            String name = g.name();
            cell.child(SettingsScreen.ui(SbcWidgets.toggle(Component.literal(name)).checked(playing.contains(name)).onChanged(on -> {
                if (on) playing.add(name);
                else playing.remove(name);
            })).horizontalSizing(Sizing.fixed(half() - 30)));
            cell.child(label(count(g) + "", dim()));
            row.child(cell);
        }
        body.child(grid);
        body.child(label("The number is how many helpers each one has. General helpers (chat, items, menus, map) always stay.", dim()).maxWidth(w));
        body.child(label(placesLine(key(hudKey(), "H")), dim()).maxWidth(w));
        body.child(nav());
    }

    // what happens to what you pick, so the hud placing itself is no surprise
    static String placesLine(String key) {
        return "The mod will place what you pick on the screen. Afterwards you can move, resize or turn off any card with " + key + ".";
    }

    private void screenPage() {
        ask("How big should the HUD be?", List.of("Small", "Normal", "Large"), List.of(0.8f, 1f, 1.25f), size, v -> size = v);
        ask("Health, mana and defense", List.of("Bars from the mod", "Vanilla hearts"), List.of(true, false), bars, v -> bars = v);
        ask("When cards change size or place", List.of("Ease into place", "Jump right away"), List.of(true, false), smooth, v -> smooth = v);
        body.child(label("You can edit everything in the HUD editor later: press " + key(hudKey(), "H") + " in game.", dim()).maxWidth(textWidth()));
        body.child(nav());
    }

    private void chatPage() {
        body.child(label("SkyBlock's chat is busy. How much of it do you want to see?", text()).maxWidth(textWidth()));
        ask(null, List.of("As Hypixel sends it", "Cleaner", "As clean as possible"), List.of(CHAT_HYPIXEL, CHAT_CLEAN, CHAT_CLEANEST_ID), chat, v -> chat = v);
        String about = chat == null ? "" : switch (chat) {
            case CHAT_HYPIXEL -> "Every line stays as it is. No line is hidden or shortened.";
            case CHAT_CLEAN -> "Spam, repeated lines and server reminders are hidden, and rewards are shortened. Player messages always stay.";
            default -> "Like Cleaner, and also ads, lobby joins, friend and guild joins, empty lines and more. Player messages always stay.";
        };
        if (!about.isEmpty()) body.child(label(about, dim()).maxWidth(textWidth()));
        body.child(label("Each filter can be switched on its own later in the settings.", dim()).maxWidth(textWidth()));
        body.child(nav());
    }

    // daily routine: what it is, want it or not, what kind of day, does the guide start by itself
    private void routinePage() {
        int w = textWidth();
        body.child(label("The routine is your daily to-do list: the dailies worth doing (visitors, experiments, Fetchur, quests and more), "
                + "on a card on your screen. Each one ticks itself off as you do it, and the guide draws a line to the next.", text()).maxWidth(w));
        ask(null, List.of("Use the routine", "No routine"), List.of(true, false), routine, v -> routine = v);
        if (Boolean.TRUE.equals(routine)) {
            body.child(label("What kind of day?", text()).margins(Insets.top(2)));
            FlowLayout grid = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            grid.gap(3);
            FlowLayout row = null;
            int n = 0;
            for (dev.zmuzy.sbc.tasks.RoutinePreset.Preset p : dev.zmuzy.sbc.tasks.RoutinePreset.ALL) {
                if (n++ % 3 == 0) {
                    row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                    row.gap(4);
                    grid.child(row);
                }
                boolean on = p == chosenPreset();
                String id = p.id();
                row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal((on ? "> " : "") + p.name()), b -> {
                    preset = id;
                    fill();
                })).horizontalSizing(Sizing.fixed(third())));
            }
            body.child(grid);
            body.child(label(chosenPreset().about(), dim()).maxWidth(w));
            body.child(toggle("Start the guide by itself the first time you join each day", autoGuide, v -> autoGuide = v));
        } else if (Boolean.FALSE.equals(routine)) {
            body.child(label("No routine card and no guide. You can turn it on any time in the settings.", dim()).maxWidth(w));
        }
        body.child(nav());
    }

    private dev.zmuzy.sbc.tasks.RoutinePreset.Preset chosenPreset() {
        for (dev.zmuzy.sbc.tasks.RoutinePreset.Preset p : dev.zmuzy.sbc.tasks.RoutinePreset.ALL) if (p.id().equals(preset)) return p;
        return dev.zmuzy.sbc.tasks.RoutinePreset.ALL.get(0);
    }

    // width of one of 3 buttons side by side
    private int third() {
        return (textWidth() - 8) / 3;
    }

    private void preferences() {
        ask("The minimap", List.of("Turns with you", "North stays up"), List.of(true, false), rotate, v -> rotate = v);
        ask("The route line to where you're going", List.of("Shows through walls", "Only where you can see"), List.of(true, false), throughWalls, v -> throughWalls = v);
        ask("The item in your hand", List.of("Small and floating", "As the game draws it"), List.of(true, false), viewmodel, v -> viewmodel = v);
        ask("When you join SkyBlock", List.of("Show what is ready today", "Show nothing"), List.of(true, false), briefing, v -> briefing = v);
        body.child(nav());
    }

    // one question: its line, then a button per answer. the picked one says so
    private <T> void ask(String question, List<String> names, List<T> values, T current, java.util.function.Consumer<T> set) {
        if (question != null) body.child(label(question, text()).margins(Insets.top(2)));
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        int each = (textWidth() - 4 * (names.size() - 1)) / names.size();
        for (int i = 0; i < names.size(); i++) {
            T v = values.get(i);
            boolean on = v.equals(current);
            row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal((on ? "> " : "") + names.get(i)), b -> {
                set.accept(v);
                missing = false;
                fill();
            })).horizontalSizing(Sizing.fixed(each)));
        }
        body.child(row);
    }

    // every question on the page answered. what you play is a choice too: nothing picked means keep them all
    boolean answered(int p) {
        return switch (p) {
            case 2 -> size != null && bars != null && smooth != null;
            case 3 -> chat != null;
            case 4 -> routine != null;
            case 5 -> rotate != null && throughWalls != null && viewmodel != null && briefing != null;
            default -> true;
        };
    }

    private UIComponent toggle(String text, boolean value, java.util.function.Consumer<Boolean> set) {
        return SettingsScreen.ui(SbcWidgets.toggle(Component.literal(text)).checked(value).onChanged(set::accept)).horizontalSizing(Sizing.fill(100));
    }

    // what finish is about to do, line by line, so nothing comes as a surprise
    List<String> summary() {
        List<String> out = new ArrayList<>();
        if (playing.isEmpty()) out.add("Every island keeps its helpers.");
        else {
            List<String> off = new ArrayList<>();
            for (HudGroups.Group g : HudGroups.ALL) if (g != HudGroups.GENERAL && !playing.contains(g.name())) off.add(g.name());
            out.add("Helpers for " + String.join(", ", playing) + ".");
            if (!off.isEmpty()) out.add("Switching off: " + String.join(", ", off) + ".");
        }
        if (size != null) out.add("HUD size " + (size < 1f ? "small" : size > 1f ? "large" : "normal") + ", " + (Boolean.TRUE.equals(bars) ? "bars from the mod" : "vanilla hearts")
                + (Boolean.TRUE.equals(smooth) ? ", cards ease into place." : ", cards jump right away."));
        if (chat != null) out.add("Chat " + switch (chat) {
            case CHAT_HYPIXEL -> "as Hypixel sends it.";
            case CHAT_CLEAN -> "cleaner.";
            default -> "as clean as possible.";
        });
        if (routine != null) out.add(routine ? "Daily routine: " + chosenPreset().name() + (autoGuide ? ", the guide starts by itself." : ".") : "No daily routine.");
        if (rotate != null) out.add("Minimap " + (rotate ? "turns with you" : "keeps north up") + ", route line " + (Boolean.TRUE.equals(throughWalls) ? "through walls" : "only where you can see") + ".");
        if (viewmodel != null) out.add((viewmodel ? "Small floating item in your hand" : "Item in your hand as the game draws it") + (Boolean.TRUE.equals(briefing) ? ", and a summary when you join." : "."));
        long on = changed.values().stream().filter(v -> v).count(), off = changed.size() - on;
        if (!changed.isEmpty()) out.add("Switched by hand: " + (on > 0 ? on + " on" : "") + (on > 0 && off > 0 ? ", " : "") + (off > 0 ? off + " off" : "") + ".");
        return out;
    }

    private void done() {
        int w = textWidth();
        body.child(label("This is what Finish will set:", text()));
        for (String s : summary()) body.child(label("- " + s, text()).maxWidth(w));
        body.child(label(key(openKey(), "K") + " opens the menu and the settings, " + key(hudKey(), "H") + " the HUD editor. /sbc setup brings these questions back.", dim())
                .maxWidth(w).margins(Insets.top(2)));
        body.child(discordRow());
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.margins(Insets.top(4));
        List<Object> flow = flow();
        Object back = flow.get(flow.size() - 2);
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Back"), b -> goTo(back))).horizontalSizing(Sizing.fixed(80)));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Finish and learn the keys"), b -> finish())).horizontalSizing(Sizing.expand()));
        body.child(row);
    }

    private void go(int to) {
        page = to;
        missing = false;
        fill();
    }

    private FlowLayout nav() {
        List<Object> flow = flow();
        int at = flow.indexOf(here());
        Object back = at <= 0 ? (Object) 0 : flow.get(at - 1), next = flow.get(Math.min(flow.size() - 1, at + 1));
        if (missing) body.child(label("Answer each question to go on.", Color.ofArgb(Modern.WARN)));
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.margins(Insets.top(4));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Back"), b -> goTo(back))).horizontalSizing(Sizing.fixed(80)));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Next"), b -> {
            if (answered(page)) goTo(next);
            else {
                missing = true;
                fill();
            }
        })).horizontalSizing(Sizing.expand()));
        return row;
    }

    private FlowLayout discordRow() {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(6);
        row.margins(Insets.top(4));
        row.child(label("Questions, ideas, bugs: discord.gg/QrqX3pgVW2", dim()).horizontalSizing(Sizing.expand()));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Join the Discord"), b -> openDiscord())).horizontalSizing(Sizing.fixed(110)));
        return row;
    }

    // applies every answer, then the keys come next over the same title screen
    private void finish() {
        applyActivities(playing);
        applyScreen(size, bars, smooth);
        applyChat(chat);
        applyRoutine(Boolean.TRUE.equals(routine), preset, autoGuide);
        applyPreferences(rotate, throughWalls, viewmodel, briefing);
        applyHandSwitches(changed);
        Config.I.setupDone = true;
        Config.I.lastVersion = dev.zmuzy.sbc.feature.Releases.current();
        Config.save();
        minecraft.gui.setScreen(new TutorialScreen(parent));
    }

    // skip keeps every default, and the keys still come next
    private void skip() {
        Config.I.setupDone = true;
        Config.I.lastVersion = dev.zmuzy.sbc.feature.Releases.current();
        Config.save();
        minecraft.gui.setScreen(new TutorialScreen(parent));
    }

    // closing early keeps the defaults and doesnt ask again
    @Override
    public void onClose() {
        Config.I.setupDone = true;
        Config.I.lastVersion = dev.zmuzy.sbc.feature.Releases.current();
        Config.save();
        super.onClose();
    }

    // --- what finish does, split out so tests can run it without a screen. null = not answered, left alone ---

    // nothing picked keeps every island. otherwise the islands not picked switch their helpers off
    static void applyActivities(Set<String> playing) {
        if (playing.isEmpty()) return;
        for (HudGroups.Group g : HudGroups.ALL) {
            if (g == HudGroups.GENERAL || playing.contains(g.name())) continue;
            for (Feature f : Features.all()) if (!f.alwaysOn && g.sections().contains(f.module) && f.enabled()) f.setEnabled(false);
        }
    }

    static void applyScreen(Float size, Boolean bars, Boolean smooth) {
        if (size != null) Config.I.hudAll = Math.max(0.5f, Math.min(2f, size));
        if (smooth != null) Config.I.hudAnimations = smooth;
        Option o = heartsOption();
        if (bars != null && o != null) o.set(bars);
    }

    static void applyChat(String level) {
        if (level == null) return;
        for (String id : CHAT_CLEANER) setEnabled(id, !level.equals(CHAT_HYPIXEL));
        for (String id : CHAT_CLEANEST) setEnabled(id, level.equals(CHAT_CLEANEST_ID));
    }

    // routine page choices. routine off = its card + the auto guide go too
    static void applyRoutine(boolean on, String preset, boolean autoGuide) {
        Feature f = Features.get("routine");
        if (f != null && f.enabled() != on) f.setEnabled(on);
        for (dev.zmuzy.sbc.tasks.RoutinePreset.Preset p : dev.zmuzy.sbc.tasks.RoutinePreset.ALL) if (p.id().equals(preset)) Config.I.routinePreset = preset;
        Config.I.autoGuide = on && autoGuide;
    }

    // the detailed pages: only what was switched by hand, after every answer
    static void applyHandSwitches(java.util.Map<String, Boolean> changed) {
        for (var e : changed.entrySet()) setEnabled(e.getKey(), e.getValue());
    }

    static void applyPreferences(Boolean rotate, Boolean throughWalls, Boolean viewmodel, Boolean briefing) {
        if (rotate != null) Config.I.minimapRotate = rotate;
        if (throughWalls != null) Config.I.pathThroughWalls = throughWalls;
        if (viewmodel != null) setEnabled(dev.zmuzy.sbc.render.HeldItem.ID, viewmodel);
        if (briefing != null) Config.I.loginBriefing = briefing;
    }

    private static boolean enabled(String id) {
        Feature f = Features.get(id);
        return f != null && f.enabled();
    }

    private static void setEnabled(String id, boolean on) {
        Feature f = Features.get(id);
        if (f != null && f.enabled() != on) f.setEnabled(on);
    }

    // how many switchable helpers a category has
    static int count(HudGroups.Group g) {
        int n = 0;
        for (Feature f : Features.all()) if (!f.alwaysOn && g.sections().contains(f.module)) n++;
        return n;
    }

    private static Option heartsOption() {
        Feature bars = Features.get("bars");
        return bars == null ? null : bars.option("hideVanilla");
    }

    private static net.minecraft.client.KeyMapping openKey() {
        return dev.zmuzy.sbc.SbcMod.openKey();
    }

    private static net.minecraft.client.KeyMapping hudKey() {
        return dev.zmuzy.sbc.SbcMod.hudEditKey();
    }

    // key in english as bound. the games own names follow its language and come out scrambled in an english line
    private static String key(net.minecraft.client.KeyMapping k, String fallback) {
        return dev.zmuzy.sbc.ui.KeyNames.of(k, fallback);
    }
}
