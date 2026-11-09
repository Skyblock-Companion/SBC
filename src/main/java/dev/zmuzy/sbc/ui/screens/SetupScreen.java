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

// page a new player sees once, first time the game hits the title screen with the mod: keep the recommended
// setup in one click, or go through the short pages. nothing changes till Finish
public final class SetupScreen extends SbcScreen {
    public static final String DISCORD = "https://discord.gg/QrqX3pgVW2";
    private static final String[] PAGES = {"Welcome", "What you play", "Daily routine", "Look", "Extras", "Done"};
    private static final int LAST = PAGES.length - 1;

    private int page;
    // true after "Set it up my way": finish applies the pages, back on the last page goes back to them
    private boolean visited;
    // activities you turned off on page 2, their features go off on finish
    private final Set<String> skipped = new LinkedHashSet<>();
    private float size = Config.I.hudAll;
    private boolean smooth = Config.I.hudAnimations;
    private boolean hearts = hideHearts();
    private boolean briefing = Config.I.loginBriefing;
    private boolean routine = routineOn();
    private String preset = Config.I.routinePreset;
    private boolean autoGuide = Config.I.autoGuide;
    private boolean viewmodel = enabled(dev.zmuzy.sbc.render.HeldItem.ID);
    private boolean throughWalls = Config.I.pathThroughWalls;
    private boolean rotate = Config.I.minimapRotate;
    private boolean visitorBox = visitorBoxOn();
    private boolean witchCard = enabled(dev.zmuzy.sbc.events.WitchIngredients.ID);
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
            body.child(new Showcase.BigText(PAGES[page].toUpperCase(java.util.Locale.ROOT), 1.5f, 0xFFFFFF, 0xB9CCFF).margins(Insets.bottom(2)));
        }
        switch (page) {
            case 0 -> welcome();
            case 1 -> activities();
            case 2 -> routinePage();
            case 3 -> look();
            case 4 -> extras();
            default -> done();
        }
    }

    // pages of the path youre on. the quick one still asks about the routine, its the one thing worth choosing
    static int[] path(boolean myWay) {
        return myWay ? new int[]{1, 2, 3, 4, LAST} : new int[]{2, LAST};
    }

    // where you are: dot per step, done ones lit, "Step 2 of 4" next to them
    private FlowLayout steps() {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.horizontalAlignment(HorizontalAlignment.CENTER);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);
        int[] path = path(visited);
        int at = 0;
        for (int i = 0; i < path.length; i++) if (path[i] == page) at = i + 1;
        for (int i = 1; i <= path.length; i++) {
            int on = i <= at ? 0xFF42A5F5 : 0x40FFFFFF;
            FlowLayout dot = UIContainers.horizontalFlow(Sizing.fixed(i == at ? 14 : 6), Sizing.fixed(4));
            dot.surface(dev.zmuzy.sbc.ui.layout.Surface.flat(on));
            row.child(dot);
        }
        row.child(label("  Step " + at + " of " + path.length, dim()));
        return row;
    }

    private void welcome() {
        int w = textWidth();
        body.child(label("Everything is set up for you already: bars, HUD cards, prices on items and helpers for every island.", text())
                .maxWidth(w).horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100)));
        body.child(label("Keep it as it is, or take a minute to make it yours. Everything can be changed later with K.", dim())
                .maxWidth(w).horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100)));
        FlowLayout choices = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        choices.gap(6);
        choices.margins(Insets.top(4));
        choices.child(choice("Use the recommended setup", "Pick your daily routine, then play", 0xFF4CAF50, () -> {
            visited = false;
            page = 2;
            fill();
        }));
        choices.child(choice("Set it up my way", "Five short steps", 0xFF42A5F5, () -> {
            visited = true;
            page = 1;
            fill();
        }));
        body.child(choices);
        body.child(discordRow());
    }

    private FlowLayout choice(String title, String line, int color, Runnable go) {
        FlowLayout card = UIContainers.verticalFlow(Sizing.fixed(half()), Sizing.content());
        card.surface(glass(color, true));
        card.padding(Insets.of(3, 5, 5, 5));
        card.gap(4);
        card.child(label(title, Color.ofArgb(Modern.lighten(color, 0.45f))).margins(Insets.bottom(4)));
        card.child(label(line, dim()));
        card.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Choose"), b -> go.run())).horizontalSizing(Sizing.fill(100)));
        return card;
    }

    private void activities() {
        int w = textWidth();
        body.child(label("Untick what you never do and its helpers switch off. Everything else stays on.", dim()).maxWidth(w));
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
            cell.child(SettingsScreen.ui(SbcWidgets.toggle(Component.literal(name)).checked(!skipped.contains(name)).onChanged(on -> {
                if (on) skipped.remove(name);
                else skipped.add(name);
            })).horizontalSizing(Sizing.fixed(half() - 30)));
            cell.child(label(count(g) + "", dim()));
            row.child(cell);
        }
        body.child(grid);
        body.child(label("The number is how many helpers each one has. General helpers (chat, items, menus, map) always stay.", dim()).maxWidth(w));
        body.child(label(placesLine(key(hudKey(), "H")), text()).maxWidth(w).margins(Insets.top(2)));
        body.child(nav(0, 2));
    }

    // what happens to what you pick, so the hud placing itself is no surprise
    static String placesLine(String key) {
        return "The mod will place what you pick on the screen. Afterwards you can move, resize or turn off any card with " + key + ".";
    }

    // daily routine: what it is, want it or not, what kind of day, does the guide start by itself
    private void routinePage() {
        int w = textWidth();
        body.child(label("The routine is your daily to-do list: the dailies worth doing (visitors, experiments, Fetchur, quests and more), "
                + "on a card on your screen. Each one ticks itself off as you do it, and the guide draws a line to the next.", text()).maxWidth(w));
        body.child(toggle("Use the daily routine", routine, v -> {
            routine = v;
            fill();
        }));
        if (routine) {
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
        } else {
            body.child(label("No routine card and no guide. You can turn it on any time in the settings (K).", dim()).maxWidth(w));
        }
        body.child(visited ? nav(1, 3) : nav(0, LAST));
    }

    private dev.zmuzy.sbc.tasks.RoutinePreset.Preset chosenPreset() {
        for (dev.zmuzy.sbc.tasks.RoutinePreset.Preset p : dev.zmuzy.sbc.tasks.RoutinePreset.ALL) if (p.id().equals(preset)) return p;
        return dev.zmuzy.sbc.tasks.RoutinePreset.ALL.get(0);
    }

    // width of one of 3 buttons side by side
    private int third() {
        return (textWidth() - 8) / 3;
    }

    private static boolean routineOn() {
        Feature f = Features.get("routine");
        return f == null || f.enabled();
    }

    private void look() {
        int w = textWidth();
        FlowLayout sizeRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        sizeRow.verticalAlignment(VerticalAlignment.CENTER);
        sizeRow.gap(6);
        sizeRow.child(label("HUD size", text()).horizontalSizing(Sizing.fixed(70)));
        SbcWidgets.Slider slider = SbcWidgets.slider(Sizing.expand(), 0.5, 2.0);
        slider.decimalPlaces(2);
        slider.setFromDiscreteValue(size);
        slider.onChanged(v -> size = (float) (Math.round(v * 20) / 20.0));
        sizeRow.child(SettingsScreen.ui(slider));
        body.child(sizeRow);
        body.child(toggle("Smooth HUD: panels ease into place", smooth, v -> smooth = v));
        body.child(toggle("Health, mana and defense bars instead of the vanilla hearts", hearts, v -> hearts = v));
        body.child(toggle("Short summary of what is ready each time you join SkyBlock", briefing, v -> briefing = v));
        body.child(label("The HUD editor (" + key(hudKey(), "H") + ") moves and resizes each card.", dim()).maxWidth(w));
        body.child(nav(2, 4));
    }

    // newer stuff that changes what you see in the world
    private void extras() {
        int w = textWidth();
        body.child(label("A few more, each one can be changed later with " + key(openKey(), "K") + ".", dim()).maxWidth(w));
        body.child(toggle("Small floating item in your hand with a slow swing (Viewmodel)", viewmodel, v -> viewmodel = v));
        body.child(toggle("Route line shows through walls", throughWalls, v -> throughWalls = v));
        body.child(toggle("Minimap turns with you instead of north up", rotate, v -> rotate = v));
        body.child(toggle("Box around a new Garden visitor", visitorBox, v -> visitorBox = v));
        body.child(toggle("Witch ingredients card during the Year of the Witch", witchCard, v -> witchCard = v));
        body.child(label("Viewmodel changes only the item: a bare hand stays as the game draws it, and hits stay as fast as they are.", dim()).maxWidth(w));
        body.child(nav(3, LAST));
    }

    private UIComponent toggle(String text, boolean value, java.util.function.Consumer<Boolean> set) {
        return SettingsScreen.ui(SbcWidgets.toggle(Component.literal(text)).checked(value).onChanged(set::accept)).horizontalSizing(Sizing.fill(100));
    }

    private void done() {
        int w = textWidth();
        body.child(label("You're set.", text()));
        List<String> lines = new ArrayList<>();
        lines.add(key(openKey(), "K") + " opens the settings: every helper, its options and a search.");
        lines.add(key(hudKey(), "H") + " opens the HUD editor, where you can edit everything.");
        lines.add("/sbc lists the commands; /sbc setup and /sbc tutorial bring these pages back.");
        if (!skipped.isEmpty()) lines.add("Switching off: " + String.join(", ", skipped) + ".");
        for (String s : lines) body.child(label("- " + s, dim()).maxWidth(w));
        body.child(discordRow());
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.margins(Insets.top(4));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Back"), b -> {
            page = visited ? LAST - 1 : 2;
            fill();
        })).horizontalSizing(Sizing.fixed(80)));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Next: learn the keys"), b -> finish())).horizontalSizing(Sizing.expand()));
        body.child(row);
    }

    private FlowLayout nav(int back, int next) {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.margins(Insets.top(4));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Back"), b -> {
            page = back;
            fill();
        })).horizontalSizing(Sizing.fixed(80)));
        row.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Next"), b -> {
            page = next;
            fill();
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

    // applies the choices. the quick path only changes the routine, the rest of the recommended setup stays as is
    private void finish() {
        if (visited) {
            apply(skipped, size, smooth, hearts, briefing);
            applyExtras(viewmodel, throughWalls, rotate, visitorBox, witchCard);
        }
        applyRoutine(routine, preset, autoGuide);
        Config.I.setupDone = true;
        Config.save();
        // keys come next, over the same title screen
        minecraft.gui.setScreen(new TutorialScreen(parent));
    }

    // routine page choices. routine off = its card + the auto guide go too
    static void applyRoutine(boolean on, String preset, boolean autoGuide) {
        Feature f = Features.get("routine");
        if (f != null && f.enabled() != on) f.setEnabled(on);
        for (dev.zmuzy.sbc.tasks.RoutinePreset.Preset p : dev.zmuzy.sbc.tasks.RoutinePreset.ALL) if (p.id().equals(preset)) Config.I.routinePreset = preset;
        Config.I.autoGuide = on && autoGuide;
    }

    // closing early keeps the defaults and doesnt ask again
    @Override
    public void onClose() {
        Config.I.setupDone = true;
        Config.save();
        super.onClose();
    }

    // what finish does, split out so a test can run it without a screen
    static void apply(Set<String> skipped, float size, boolean smooth, boolean hearts, boolean briefing) {
        for (HudGroups.Group g : HudGroups.ALL) {
            if (g == HudGroups.GENERAL || !skipped.contains(g.name())) continue;
            for (Feature f : Features.all()) if (!f.alwaysOn && g.sections().contains(f.module) && f.enabled()) f.setEnabled(false);
        }
        Config.I.hudAll = Math.max(0.5f, Math.min(2f, size));
        Config.I.hudAnimations = smooth;
        Config.I.loginBriefing = briefing;
        Option o = heartsOption();
        if (o != null) o.set(hearts);
    }

    // the extras page, split out like apply so a test runs it
    static void applyExtras(boolean viewmodel, boolean throughWalls, boolean rotate, boolean visitorBox, boolean witchCard) {
        setEnabled(dev.zmuzy.sbc.render.HeldItem.ID, viewmodel);
        setEnabled(dev.zmuzy.sbc.events.WitchIngredients.ID, witchCard);
        Config.I.pathThroughWalls = throughWalls;
        Config.I.minimapRotate = rotate;
        Option o = visitorOption();
        if (o != null) {
            boolean names = o.text().equals("Both") || o.text().equals("Name");
            o.set(visitorBox ? (names ? "Both" : "Color") : (names ? "Name" : "Off"));
        }
    }

    private static boolean enabled(String id) {
        Feature f = Features.get(id);
        return f != null && f.enabled();
    }

    private static void setEnabled(String id, boolean on) {
        Feature f = Features.get(id);
        if (f != null && f.enabled() != on) f.setEnabled(on);
    }

    private static Option visitorOption() {
        Feature f = Features.get(dev.zmuzy.sbc.garden.visitor.VisitorWorld.STATUS);
        return f == null ? null : f.option("garden_visitor_status.highlight");
    }

    private static boolean visitorBoxOn() {
        Option o = visitorOption();
        return o != null && (o.text().equals("Both") || o.text().equals("Color"));
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

    private static boolean hideHearts() {
        Option o = heartsOption();
        return o == null || o.bool();
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
