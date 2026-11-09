// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.plan.Planner;
import dev.zmuzy.sbc.track.Dps;
import dev.zmuzy.sbc.track.Tracker;
import dev.zmuzy.sbc.ui.screens.SbcScreen;
import dev.zmuzy.sbc.ui.screens.SbcWidgets;
import dev.zmuzy.sbc.ui.screens.SettingsPanel;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import dev.zmuzy.sbc.ui.layout.BaseUIComponent;
import dev.zmuzy.sbc.ui.layout.LabelComponent;
import dev.zmuzy.sbc.ui.layout.UIComponents;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.UIContainers;
import dev.zmuzy.sbc.ui.layout.Color;
import dev.zmuzy.sbc.ui.layout.CursorStyle;
import dev.zmuzy.sbc.ui.layout.HorizontalAlignment;
import dev.zmuzy.sbc.ui.layout.Insets;
import dev.zmuzy.sbc.ui.layout.UIAdapter;
import dev.zmuzy.sbc.ui.layout.UIGraphics;
import dev.zmuzy.sbc.ui.layout.ParentUIComponent;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.Surface;
import dev.zmuzy.sbc.ui.layout.UIComponent;
import dev.zmuzy.sbc.ui.layout.VerticalAlignment;
import dev.zmuzy.sbc.ui.layout.UIErrorToast;
import dev.zmuzy.sbc.ui.layout.UISounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

// the K menu. whole thing is laid out on a fixed 900x420 canvas and drawn scaled to 80% of the screen, centered
// every mouse event gets mapped back onto the canvas so the tree always sees the same size
public final class CompanionScreen extends SbcScreen {
    public record Tab(String name, String icon, String subtitle) {}

    public static final Tab[] TABS = {
            new Tab("Dashboard", "nether_star", "Everything at a glance"),
            new Tab("Routine", "writable_book", "Daily, weekly and event tasks - never miss one"),
            new Tab("Calendar", "clock", "Your history and every upcoming SkyBlock event"),
            new Tab("Planner", "compass", "Auto Routine: the smartest use of your time today"),
            new Tab("Money", "gold_ingot", "Bazaar flips, best activities and live rates"),
            new Tab("Level", "experience_bottle", "Fastest SkyBlock XP from the Task Guide"),
            new Tab("Minions", "hopper", "Measured output, profit and upgrade advice"),
            new Tab("Profile", "player_head", "Full profile viewer - you or anyone"),
            new Tab("Navigate", "recovery_compass", "Path line to any NPC or place"),
            new Tab("Map", "filled_map", "Island map with every NPC and warp"),
            new Tab("Stats", "spyglass", "Sessions, earnings and time played"),
            new Tab("Settings", "comparator", "HUD, alerts and path line"),
            new Tab("Forge", "anvil", "What to forge for the most profit at your HOTM")};

    // every tab has its own color (sidebar bar + line under the header)
    private static final int[] TAB_COLOR = {0xFF5CD8FF, 0xFFFFD166, 0xFF7FD4FF, 0xFF4FD8C4, 0xFFFFB627, 0xFF9CE37D, 0xFFFFB454, 0xFFC77DFF, 0xFF58C7FF, 0xFF81C784, 0xFF7FB3FF, 0xFF90A4AE, 0xFFFF8F6B};

    private record TabGroup(String name, int... tabs) {}

    // sidebar groups, empty name = just a line
    private static final TabGroup[] TAB_GROUPS = {new TabGroup("Today", 0, 1, 2, 3), new TabGroup("Earn", 4, 12, 6), new TabGroup("Grow", 5, 7, 10), new TabGroup("World", 8, 9), new TabGroup("", 11)};
    private static final int[] TAB_ORDER = {0, 1, 2, 3, 4, 12, 6, 5, 7, 10, 8, 9, 11};

    private static final int SETTINGS_TAB = 11, MAP_TAB = 9, SETUP_VIEW = -1, NO_VIEW = Integer.MIN_VALUE;
    private static final int VW = 900, VH = 420, SIDE_W = 132, PROFILE_W = 150;
    // room for the scrollbar
    private static final int GUTTER = 6;

    public static int page;

    private final long opened = System.currentTimeMillis();
    private final Ui screenUi = new Ui();
    private final List<Runnable> live = new ArrayList<>();
    private long lastLive;
    private float scale = 1f;
    private int offX, offY, realMouseX, realMouseY;

    private FlowLayout body, modelBox;
    private SbcWidgets.Icon headerIcon;
    private LabelComponent headerTitle, headerSubtitle;
    private final LabelComponent[] tabLabels = new LabelComponent[TABS.length];
    private final PageHost[] pages = new PageHost[TABS.length];
    private PageHost setupPage, shownPage;
    private FlowLayout settingsView;
    private Supplier<String[]> pendingTip;
    private Supplier<net.minecraft.world.item.ItemStack> pendingItem;
    private int shownView = NO_VIEW;

    public CompanionScreen() {
        super("SkyBlock Companion", null);
    }

    // --- window ---

    @Override
    protected UIAdapter<FlowLayout> createAdapter() {
        return UIAdapter.createWithoutScreen(0, 0, VW, VH, UIContainers::horizontalFlow);
    }

    @Override
    protected void init() {
        if (this.invalid) return;
        scale = Math.min(0.8f * width / VW, 0.9f * height / VH);
        offX = Math.round((width - VW * scale) / 2f);
        offY = Math.round((height - VH * scale) / 2f);
        if (this.uiAdapter != null) return; // canvas never changes with the window, only how its drawn
        try {
            this.uiAdapter = createAdapter();
            build(this.uiAdapter.rootComponent);
            this.uiAdapter.inflateAndMount();
        } catch (Exception error) {
            SbcMod.LOG.warn("Could not build the K menu", error);
            UIErrorToast.report(error);
            this.invalid = true;
        }
    }

    @Override
    protected void build(FlowLayout root) {
        Ui.applyTheme();
        live.clear();
        root.surface(SbcScreen.panel());
        root.padding(Insets.of(1));
        root.child(sidebar());
        root.child(profileColumn());
        root.child(content());
        shownView = NO_VIEW;
        shownPage = null;
    }

    private static Color color(int argb) {
        return Color.ofArgb(argb);
    }

    private static void setText(LabelComponent label, String text) {
        if (!label.text().getString().equals(text)) label.text(Component.literal(text));
    }

    // label that updates itself a few times a sec
    private LabelComponent liveLabel(Supplier<String> text, int argb) {
        LabelComponent l = label(text.get(), color(argb));
        live.add(() -> setText(l, text.get()));
        return l;
    }

    private static String fit(String text, int width) {
        return Ui.MEASURE.ellipsize(text, width);
    }

    // --- sidebar ---

    private FlowLayout sidebar() {
        FlowLayout side = UIContainers.verticalFlow(Sizing.fixed(SIDE_W), Sizing.fill(100));
        int edge = 0x24FFFFFF;
        Surface face = (g, c) -> dev.zmuzy.sbc.ui.screens.SbcGradient.rect(g, c.x(), c.y(), c.width(), c.height(), 0x70000000, 0x70000000, 0x38000000, 0x38000000);
        side.surface(face.and((g, c) -> g.fill(c.x() + c.width() - 1, c.y(), c.x() + c.width(), c.y() + c.height(), edge)));
        side.padding(Insets.of(6, 6, 4, 5));
        FlowLayout brand = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(14));
        brand.verticalAlignment(VerticalAlignment.CENTER);
        brand.gap(4);
        brand.padding(Insets.left(2));
        brand.margins(Insets.bottom(5));
        brand.child(new SbcWidgets.Icon(LegacyItems.stack("nether_star"), 12));
        brand.child(label("SkyBlock Companion", SbcScreen.accent()));
        side.child(brand);
        for (TabGroup g : TAB_GROUPS) {
            if (!g.name().isEmpty()) side.child(label(g.name(), SbcScreen.dim()).margins(Insets.of(5, 2, 4, 0)));
            else side.child(UIComponents.box(Sizing.fill(100), Sizing.fixed(1)).fill(true).color(Color.ofArgb(0x14FFFFFF)).margins(Insets.of(5, 4, 0, 0)));
            for (int tab : g.tabs()) {
                side.child(tabRow(tab));
                if (tab == SETTINGS_TAB) side.child(creditsRow());
            }
        }

        FlowLayout wallet = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
        wallet.verticalAlignment(VerticalAlignment.BOTTOM);
        wallet.gap(2);
        wallet.padding(Insets.of(0, 0, 3, 3));
        row(wallet, "Purse", () -> GameState.purse >= 0 ? Prices.coins(GameState.purse) : "-", Ui.GOLD, null);
        row(wallet, "Bits", () -> GameState.bits >= 0 ? Prices.coins(GameState.bits) : "-", 0xFF5CC8FF, null);
        wallet.child(label("Esc closes · ↑↓ tabs", SbcScreen.dim()).margins(Insets.top(3)));
        side.child(wallet);
        return side;
    }

    // open tab gets a colored face and a bar on the left
    private FlowLayout tabRow(int index) {
        int active = 0x38FFFFFF, hoverFace = 0x14FFFFFF, accent = TAB_COLOR[index];
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(15));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.padding(Insets.of(0, 0, 6, 3));
        row.gap(5);
        row.margins(Insets.bottom(1));
        row.cursorStyle(CursorStyle.HAND);
        row.surface((g, c) -> {
            int x = c.x(), y = c.y(), w = c.width(), h = c.height();
            boolean open = page == index;
            if (open && false) g.fill(x, y, x + w, y + h, active);
            else if (open) dev.zmuzy.sbc.ui.screens.SbcGradient.rect(g, x, y, w, h, Ui.alpha(accent, 0.42f), Ui.alpha(accent, 0.04f), Ui.alpha(accent, 0.04f), Ui.alpha(accent, 0.42f));
            else if (c.isInBoundingBox(mouseOnCanvasX(), mouseOnCanvasY())) g.fill(x, y, x + w, y + h, hoverFace);
            if (open) g.fill(x, y, x + 2, y + h, accent);
        });
        row.mouseDown().subscribe((click, doubled) -> {
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
            UISounds.playInteractionSound();
            goTo(index);
            return true;
        });
        SbcWidgets.Icon icon = new SbcWidgets.Icon(LegacyItems.stack(TABS[index].icon()), 11);
        icon.cursorStyle(CursorStyle.HAND);
        row.child(icon);
        LabelComponent name = label(TABS[index].name(), SbcScreen.dim());
        name.cursorStyle(CursorStyle.HAND);
        name.horizontalSizing(Sizing.expand());
        tabLabels[index] = name;
        row.child(name);
        if (index == 1) {
            LabelComponent badge = liveLabel(() -> {
                int[] c = dev.zmuzy.sbc.tasks.Routine.counts();
                return c[0] + "/" + c[1];
            }, Modern.DIM);
            badge.cursorStyle(CursorStyle.HAND);
            row.child(badge);
        } else if (index == 6) {
            LabelComponent badge = liveLabel(() -> Store.I.minions.isEmpty() ? "" : String.valueOf(Store.I.minions.size()), Modern.DIM);
            badge.cursorStyle(CursorStyle.HAND);
            row.child(badge);
        }
        return row;
    }


    // below Settings: who the mod stands on + its licences, a screen of its own
    private FlowLayout creditsRow() {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(15));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.padding(Insets.of(0, 0, 6, 3));
        row.gap(5);
        row.margins(Insets.bottom(1));
        row.cursorStyle(CursorStyle.HAND);
        row.surface((g, c) -> {
            if (c.isInBoundingBox(mouseOnCanvasX(), mouseOnCanvasY())) g.fill(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), 0x14FFFFFF);
        });
        row.mouseDown().subscribe((click, doubled) -> {
            if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
            UISounds.playInteractionSound();
            dev.zmuzy.sbc.ui.screens.CreditsScreen.open(this);
            return true;
        });
        SbcWidgets.Icon icon = new SbcWidgets.Icon(LegacyItems.stack("written_book"), 11);
        icon.cursorStyle(CursorStyle.HAND);
        row.child(icon);
        LabelComponent name = label("Credits", SbcScreen.dim());
        name.cursorStyle(CursorStyle.HAND);
        name.horizontalSizing(Sizing.expand());
        row.child(name);
        return row;
    }

    private int mouseOnCanvasX() {
        return (int) Math.floor((realMouseX - offX) / scale);
    }

    private int mouseOnCanvasY() {
        return (int) Math.floor((realMouseY - offY) / scale);
    }

    // --- character column ---

    private static final class Bar extends BaseUIComponent {
        private final DoubleSupplier pct;
        private final int fill;

        Bar(DoubleSupplier pct, int fill) {
            this.pct = pct;
            this.fill = fill;
            this.horizontalSizing(Sizing.fill(100));
            this.verticalSizing(Sizing.fixed(4));
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            g.fill(x, y, x + width, y + height, 0xC0050911);
            int f = (int) Math.round(width * Math.max(0, Math.min(1, pct.getAsDouble())));
            if (f <= 0) return;
            g.fill(x, y, x + f, y + height, fill);
            g.fill(x, y, x + f, y + 1, 0x48FFFFFF);
        }
    }

    private void row(FlowLayout col, String name, Supplier<String> value, int valueColor, String tip) {
        FlowLayout r = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        r.child(label(name, color(Ui.MUTED)).horizontalSizing(Sizing.expand()));
        r.child(liveLabel(value, valueColor));
        if (tip != null) r.tooltip(Component.literal(tip));
        col.child(r);
    }

    private static int levelOf(Profile.Data d) {
        if (d != null && d.sbLevel() >= 0) return d.sbLevel();
        try {
            return Integer.parseInt(GameState.sbLevel.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private FlowLayout profileColumn() {
        FlowLayout col = UIContainers.verticalFlow(Sizing.fixed(PROFILE_W), Sizing.fill(100));
        col.surface(SbcScreen.glass(Modern.GOLD, true));
        col.padding(Insets.of(3, 5, 6, 6));
        col.margins(Insets.of(6, 6, 6, 0));
        col.gap(1);

        LabelComponent name = liveLabel(() -> minecraft.player != null ? minecraft.player.getGameProfile().name() : "Player", Modern.GOLD);
        name.horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100));
        name.margins(Insets.bottom(4));
        col.child(name);
        LabelComponent sub = liveLabel(() -> {
            Profile.Data d = Profile.current;
            String s = d != null ? d.profileName + (d.gameMode.isEmpty() ? "" : " · " + Profile.cap(d.gameMode))
                    : GameState.profileName.isEmpty() ? "SkyBlock" : GameState.profileName;
            return fit(s, PROFILE_W - 16);
        }, Modern.LABEL);
        sub.horizontalTextAlignment(HorizontalAlignment.CENTER).horizontalSizing(Sizing.fill(100));
        col.child(sub);

        // the player model gets drawn over this box after the tree (see drawModel)
        modelBox = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(160));
        modelBox.surface((g, c) -> {
            dev.zmuzy.sbc.ui.screens.SbcGradient.rect(g, c.x(), c.y(), c.width(), c.height(), 0x70000000, 0x70000000, 0x30000000, 0x30000000);
            g.fill(c.x(), c.y() + c.height() - 1, c.x() + c.width(), c.y() + c.height(), 0x20FFFFFF);
        });
        modelBox.margins(Insets.vertical(3));
        col.child(modelBox);

        row(col, "SkyBlock Level", () -> {
            int level = levelOf(Profile.current);
            return level >= 0 ? String.valueOf(level) : "?";
        }, Ui.GOLD, null);
        col.child(new Bar(() -> Profile.current != null && Profile.current.sbXp >= 0 ? (Profile.current.sbXp % 100) / 100.0 : 0, Modern.AQUA).margins(Insets.of(1, 1, 0, 0)));
        row(col, "XP to next", () -> {
            Profile.Data d = Profile.current;
            if (d == null || d.sbXp < 0) return "-";
            int into = (int) (d.sbXp % 100);
            return into + "/100  " + into + "%";
        }, Modern.AQUA, null);
        col.child(new SbcWidgets.Heading("Wealth", Modern.GOLD).margins(Insets.top(3)));

        row(col, "Net worth", () -> {
            Profile.Data d = Profile.current;
            double purse = purse(d), bank = bank(d);
            if (purse < 0 && d == null) return "?";
            if (d != null && !d.knows(dev.zmuzy.sbc.core.game.GameProfile.NETWORTH)) return "?";
            return Prices.coins(d != null ? d.networth() : Math.max(0, purse) + Math.max(0, bank));
        }, Ui.GOLD, "Items, pets, sacks, essence, purse and bank");
        row(col, "Purse", () -> purse(Profile.current) < 0 ? "?" : Prices.coins(purse(Profile.current)), Ui.GOLD, null);
        row(col, "Bank", () -> bank(Profile.current) < 0 ? "?" : Prices.coins(bank(Profile.current)), Ui.GOLD, null);
        col.child(new SbcWidgets.Heading("This session", Modern.GOOD).margins(Insets.top(3)));
        row(col, "Money now", () -> "+" + Prices.coins(Tracker.sessionCoins), Ui.GREEN, "Coins earned this session");
        row(col, "Per hour", () -> {
            double rate = Planner.currentRate();
            return rate > 0 ? Prices.coins(rate) : "-";
        }, Ui.GREEN, "Your measured coins/hour on this island");
        row(col, "DPS", () -> {
            double dps = Dps.dps();
            return dps > 0 ? Prices.coins(dps) : "-";
        }, Ui.RED, "From your damage numbers over the last 5s");
        col.child(new SbcWidgets.Heading("Progress", Modern.AQUA).margins(Insets.top(3)));
        row(col, "Skill avg", () -> Profile.current == null || Profile.current.skillAverage < 0 ? "-" : String.format(Locale.ROOT, "%.1f", Profile.current.skillAverage), Ui.TEXT, null);
        row(col, "Catacombs", () -> {
            Profile.Data d = Profile.current;
            if (d == null || d.catacombs == null) return "-";
            return d.catacombs.progress() < 0 ? String.valueOf(d.catacombs.level()) : d.catacombs.level() + "  " + MenuUi.pct(d.catacombs.progress());
        }, Ui.TEXT, null);
        row(col, "Magical Power", () -> {
            Profile.Data d = Profile.current;
            if (d == null || !d.knows(dev.zmuzy.sbc.core.game.GameProfile.MAGICAL_POWER)) return "-";
            return MenuUi.num(d.magicalPower);
        }, MenuUi.PURPLE, null);
        row(col, "Cookie", () -> {
            String c = GameState.cookieStatus();
            return c.isEmpty() ? "?" : fit(c, 86);
        }, 0xFFE0B070, "Booster Cookie");

        FlowLayout where = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
        where.verticalAlignment(VerticalAlignment.BOTTOM);
        where.gap(1);
        where.child(liveLabel(() -> fit(GameState.islandName.isEmpty() ? "Not in SkyBlock" : GameState.islandName, PROFILE_W - 16), Ui.TEXT));
        where.child(liveLabel(() -> fit(GameState.area, PROFILE_W - 16), Ui.DIM));
        col.child(where);
        return col;
    }

    private static double purse(Profile.Data d) {
        return GameState.purse >= 0 ? GameState.purse : d != null ? d.purse : -1;
    }

    private static double bank(Profile.Data d) {
        return GameState.bank >= 0 ? GameState.bank : d != null ? d.bank : -1;
    }

    // live player model, follows the mouse. real screen coords, not canvas
    private void drawModel(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (minecraft.player == null || modelBox == null) return;
        int x0 = offX + Math.round(modelBox.x() * scale), y0 = offY + Math.round(modelBox.y() * scale);
        int x1 = offX + Math.round((modelBox.x() + modelBox.width()) * scale), y1 = offY + Math.round((modelBox.y() + modelBox.height()) * scale);
        int size = Math.max(12, (int) ((y1 - y0) * 0.42f));
        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, size, 0.0625f, mouseX, mouseY, minecraft.player);
        } catch (Exception ignored) {
        }
    }

    // --- header and body ---

    private FlowLayout chip(Supplier<String> text, int argb) {
        FlowLayout chip = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
        chip.padding(Insets.of(3, 2, 5, 5));
        int c = argb | 0xFF000000;
        chip.surface((g, p) -> {
            g.fill(p.x(), p.y(), p.x() + p.width(), p.y() + p.height(), Ui.alpha(c, 0.2f));
            g.fill(p.x(), p.y() + p.height() - 1, p.x() + p.width(), p.y() + p.height(), Ui.alpha(c, 0.8f));
        });
        chip.child(liveLabel(text, Modern.lighten(c, 0.3f)));
        return chip;
    }

    private FlowLayout content() {
        FlowLayout c = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        c.padding(Insets.of(6, 6, 6, 6));

        FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(6);
        FlowLayout tile = UIContainers.verticalFlow(Sizing.fixed(22), Sizing.fixed(22));
        tile.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        tile.surface((g, p) -> {
            int a = TAB_COLOR[Math.floorMod(page, TABS.length)];
            dev.zmuzy.sbc.ui.screens.SbcGradient.rect(g, p.x(), p.y(), p.width(), p.height(), Ui.alpha(a, 0.34f), Ui.alpha(a, 0.34f), Ui.alpha(a, 0.1f), Ui.alpha(a, 0.1f));
            g.fill(p.x(), p.y() + p.height() - 1, p.x() + p.width(), p.y() + p.height(), Ui.alpha(a, 0.85f));
        });
        headerIcon = new SbcWidgets.Icon(LegacyItems.stack(TABS[page].icon()), 16);
        tile.child(headerIcon);
        header.child(tile);
        FlowLayout titles = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        titles.gap(2);
        headerTitle = label(TABS[page].name(), SbcScreen.text());
        headerSubtitle = label(TABS[page].subtitle(), color(Ui.MUTED));
        titles.child(headerTitle);
        titles.child(headerSubtitle);
        header.child(titles);
        header.child(chip(() -> {
            SbTime.SbDate d = SbTime.sbDate(System.currentTimeMillis());
            return d.monthName() + " " + d.day() + SbTime.suffix(d.day()) + " · " + d.clock();
        }, Ui.CYAN));
        header.child(chip(() -> "Reset " + SbTime.dur(SbTime.untilReset(SbTime.Period.DAILY, System.currentTimeMillis())), Ui.ORANGE));
        c.child(header);
        FlowLayout rule = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fixed(1));
        rule.surface((g, p) -> {
            int a = TAB_COLOR[Math.floorMod(page, TABS.length)];
            dev.zmuzy.sbc.ui.screens.SbcGradient.rect(g, p.x(), p.y(), p.width(), 1, Ui.alpha(a, 0.9f), Ui.alpha(a, 0.05f), Ui.alpha(a, 0.05f), Ui.alpha(a, 0.9f));
        });
        rule.margins(Insets.of(3, 4, 0, 0));
        c.child(rule);

        body = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
        c.child(body);
        return c;
    }

    private int wantedView() {
        return page;
    }

    private UIComponent viewOf(int view) {
        if (view == SETTINGS_TAB) {
            if (settingsView == null) {
                settingsView = UIContainers.verticalFlow(Sizing.fill(100), Sizing.fill(100));
                new SettingsPanel(this, true).build(settingsView, body.width());
            }
            return settingsView;
        }
        return hostOf(view).scroll;
    }

    private PageHost hostOf(int view) {
        if (view == SETUP_VIEW) {
            if (setupPage == null) setupPage = new PageHost(SETUP_VIEW);
            return setupPage;
        }
        if (pages[view] == null) pages[view] = new PageHost(view);
        return pages[view];
    }

    // tab changes apply here, never in the middle of a click
    private void syncTree() {
        FlowLayout root = this.uiAdapter.rootComponent;
        int want = wantedView();
        if (want == shownView) return;
        shownView = want;
        var focus = root.focusHandler();
        if (focus != null) focus.focus(null, UIComponent.FocusSource.MOUSE_CLICK);
        UIComponent view = viewOf(want);
        body.<FlowLayout>configure(b -> {
            b.clearChildren();
            b.child(view);
        });
        shownPage = want == SETTINGS_TAB ? null : hostOf(want);
        if (shownPage != null) shownPage.changed();

        Tab tab = TABS[Math.floorMod(page, TABS.length)];
        headerIcon.stack(LegacyItems.stack(tab.icon()));
        headerTitle.text(Component.literal(tab.name()).withStyle(ChatFormatting.BOLD));
        headerSubtitle.text(Component.literal(tab.subtitle()));
        Color idle = color(Modern.LABEL);
        for (int i = 0; i < tabLabels.length; i++) tabLabels[i].color(page == i ? SbcScreen.text() : idle);
    }

    // --- one page ---

    // one tab's content. the page describes itself a few times a sec through MenuUi, if it changed we rebuild in
    // one layout pass
    // scroll, hover and clicks stay where they were
    private final class PageHost implements MenuUi.Host {
        private static final long REFRESH_MS = 250;
        private final int tab;
        final SbcWidgets.Scroll<FlowLayout> scroll;
        private final Map<String, UIComponent> kept = new HashMap<>();
        private long lastBuild, shownSignature;
        private boolean dirty = true, built, failed;

        PageHost(int tab) {
            this.tab = tab;
            this.scroll = SbcWidgets.scroll(Sizing.fill(100), Sizing.fill(100), UIContainers.verticalFlow(Sizing.fixed(1), Sizing.fixed(1)));
        }

        @Override
        public void changed() {
            dirty = true;
        }

        @Override
        public void goTo(int view) {
            CompanionScreen.this.goTo(view);
        }

        @Override
        public void showTip(Supplier<String[]> lines) {
            pendingTip = lines;
            pendingItem = null;
        }

        @Override
        public void showItemTip(Supplier<net.minecraft.world.item.ItemStack> item, Supplier<String[]> extra) {
            pendingTip = extra;
            pendingItem = item;
        }

        @Override
        public boolean mouseInView(int mouseX, int mouseY) {
            return scroll.isInBoundingBox(mouseX, mouseY);
        }

        // runs at the start of a frame while this page is on screen
        void sync(long now) {
            if (!dirty && now - lastBuild < REFRESH_MS) return;
            dirty = false;
            lastBuild = now;
            int viewW = Math.max(1, body.width()), viewH = Math.max(1, body.height());
            MenuUi ui = new MenuUi(this, kept, viewW);
            int contentH;
            try {
                contentH = MenuPages.paint(tab, ui, 0, 4, viewW - GUTTER, viewH) + (tab == SETUP_VIEW ? 0 : 8);
            } catch (RuntimeException error) {
                if (!failed) SbcMod.LOG.warn("K menu page {} failed", tab, error);
                failed = true;
                ui = new MenuUi(this, kept, viewW);
                ui.text("This page hit an error (see the log). The other tabs still work.", 4, 8, Ui.ORANGE);
                contentH = 30;
            }
            long signature = ui.signature() * 31 + contentH;
            if (built && signature == shownSignature) return;
            built = true;
            shownSignature = signature;
            scroll.child(ui.materialize(viewW, contentH));
        }
    }

    // --- drawing ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        // a new layer of the frame every 64 pieces, the page is hundreds of them
        AutoStrata.start(64);
        try {
            paint(g, mouseX, mouseY, a);
        } finally {
            AutoStrata.stop();
        }
    }

    private void paint(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (this.invalid || this.uiAdapter == null) {
            onClose();
            return;
        }
        Ui.applyTheme();
        realMouseX = mouseX;
        realMouseY = mouseY;
        syncTree();
        long now = System.currentTimeMillis();
        if (shownPage != null) shownPage.sync(now);
        if (now - lastLive >= 250) {
            lastLive = now;
            for (Runnable r : live) r.run();
        }

        float t = Math.min(1, (now - opened) / 220f);
        float open = 1 - (1 - t) * (1 - t) * (1 - t);
        g.fill(0, 0, width, height, Ui.alpha(0xFF000000, 0.35f * open));

        int canvasX = mouseOnCanvasX(), canvasY = mouseOnCanvasY();
        float slide = (1 - open) * 10 * scale;
        pendingTip = null;
        pendingItem = null;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(offX, offY + slide);
        pose.scale(scale, scale);
        this.uiAdapter.extractRenderState(g, canvasX, canvasY, a);
        pose.popMatrix();

        drawModel(g, mouseX, mouseY);
        screenUi.begin(g, mouseX, mouseY);
        Toasts.render(screenUi, width);

        // tooltips last and at the games own scale, otherwise theyre tiny on the smaller window
        UIComponent tipped = tooltipAt(this.uiAdapter.rootComponent, canvasX, canvasY);
        net.minecraft.world.item.ItemStack tipStack = pendingItem == null ? null : pendingItem.get();
        if (tipStack != null && !tipStack.isEmpty() && minecraft.level != null) {
            dev.zmuzy.sbc.item.tooltip.ItemTips.draw(g, tipStack, mouseX, mouseY, pendingTip == null ? List.of() : List.of(pendingTip.get()));
        } else if (pendingTip != null) {
            screenUi.tooltip(pendingTip.get());
            screenUi.flushTooltip();
        } else if (tipped != null) {
            g.nextStratum();
            g.tooltip(minecraft.font, tipped.tooltip(), mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null, true);
        }
    }

    private static UIComponent tooltipAt(ParentUIComponent parent, int x, int y) {
        List<UIComponent> children = parent.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            UIComponent child = children.get(i);
            if (!child.isInBoundingBox(x, y)) continue;
            if (child instanceof ParentUIComponent p) {
                UIComponent inside = tooltipAt(p, x, y);
                if (inside != null) return inside;
            }
            List<ClientTooltipComponent> tip = child.tooltip();
            if (tip != null && !tip.isEmpty()) return child;
        }
        return null;
    }

    // --- input: everything gets mapped onto the canvas ---

    private MouseButtonEvent onCanvas(MouseButtonEvent e) {
        return new MouseButtonEvent((e.x() - offX) / scale, (e.y() - offY) / scale, e.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (this.uiAdapter == null) return false;
        this.uiAdapter.mouseClicked(onCanvas(e), doubleClick);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (this.uiAdapter == null) return false;
        return this.uiAdapter.mouseDragged(onCanvas(e), dx / scale, dy / scale);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (this.uiAdapter == null) return false;
        return this.uiAdapter.mouseReleased(onCanvas(e));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (this.uiAdapter == null) return false;
        this.uiAdapter.mouseScrolled((x - offX) / scale, (y - offY) / scale, sx, sy);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (this.uiAdapter == null) return false;
        var focus = this.uiAdapter.rootComponent.focusHandler();
        boolean typing = focus != null && focus.focused() instanceof SbcWidgets.Input;
        if (e.isEscape()) {
            if (typing) focus.focus(null, UIComponent.FocusSource.MOUSE_CLICK); // first esc leaves the field, second one closes
            else onClose();
            return true;
        }
        if (this.uiAdapter.keyPressed(e)) return true;
        if (!typing && (e.key() == InputConstants.KEY_DOWN || e.key() == InputConstants.KEY_UP)) {
            int step = e.key() == InputConstants.KEY_DOWN ? 1 : -1;
            // same order as the sidebar
            int at = 0;
            for (int i = 0; i < TAB_ORDER.length; i++) if (TAB_ORDER[i] == page) at = i;
            goTo(TAB_ORDER[Math.floorMod(at + step, TAB_ORDER.length)]);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent e) {
        return this.uiAdapter != null && this.uiAdapter.charTyped(e);
    }

    // dev only: open an island on the map page (for screenshots)
    public static void devViewIsland(String island) {
        PagesMap.viewing = island;
    }

    // tree changes next frame, not inside the click that asked for it
    public void goTo(int tab) {
        page = tab;
    }

    @Override
    public void removed() {
        super.removed();
        Config.save();
        Store.save();
    }
}
