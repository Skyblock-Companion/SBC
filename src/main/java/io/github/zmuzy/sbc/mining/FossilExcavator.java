// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.item.sacks.Sacks;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

// fossil excavator menu. from skyhanni FossilSolverDisplay (best tile lit green + its chance, rest next to the chest)
// ExcavatorTooltipHider, GlacitePowderFeatures, FossilExcavatorApi, ScrapGFS (button next to the chest that
// grabs suspicious scrap when pressed)
public final class FossilExcavator {
    static final String SOLVER = "mining_fossil_solver", SCRAP = "mining_scrap_gfs";
    private static final String TITLE = "Fossil Excavator", SCRAP_ID = "SUSPICIOUS_SCRAP";
    private static final Patterns.P INVENTORY = Patterns.p("SH.mining.fossil.excavator.inventory");
    private static final Patterns.P CHARGES = Patterns.p("SH.mining.fossilexcavator.chargesremaining");
    private static final Patterns.P PROGRESS = Patterns.p("SH.mining.fossilexcavator.fossilprogress");
    private static final Patterns.P POWDER = Patterns.p("SH.inventory.item.overlay.glacitepowder");

    private static boolean inMenu, wasOpen;
    private static int maxCharges, charges;
    private static String percentage;
    private static FossilSolver.Move move;
    private static int lastBoard;
    private static final Panel PANEL = new Panel();

    private FossilExcavator() {}

    private static void clear() {
        inMenu = false;
        maxCharges = charges = 0;
        percentage = null;
        move = null;
        lastBoard = 0;
    }

    // menu rule: reads the board when it changed, lights the tile
    static void mark(MenuView v) {
        Set<Integer> fossil = new HashSet<>(), dirt = new HashSet<>();
        int hash = 1;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            hash = 31 * hash + it.clean.hashCode();
            if (MiningPack.on(SOLVER, "mining_fossil_solver.powder_stack")) {
                Matcher p = POWDER.matcher(it.clean);
                if (p.matches()) v.number(slot, "§b" + Vitals.compact(MiningEnv.parseLong(p.group("amount"))));
            }
            if (slot > 53) continue;
            boolean isDirt = it.clean.equals("Dirt"), isFossil = it.clean.equals("Fossil");
            if (isDirt) dirt.add(slot);
            else if (isFossil) fossil.add(slot);
        }
        for (int slot = 0; slot < v.top; slot++) {
            if (v.item(slot).clean.equals("Start Excavator")) {
                move = null;
                return;
            }
        }
        if (fossil.isEmpty() && dirt.isEmpty()) return;
        if (hash != lastBoard) {
            lastBoard = hash;
            read(v, fossil, dirt);
        }
        if (move == null || move.slot() < 0) return;
        v.back(move.slot(), 0x5A55FF55);
        if (MiningPack.on(SOLVER, "mining_fossil_solver.percent")) v.corner(move.slot(), MenuView.BOTTOM_RIGHT, Math.round(move.percent() * 1000) / 10.0 + "%", 0xFF00AA00);
    }

    private static void read(MenuView v, Set<Integer> fossil, Set<Integer> dirt) {
        boolean foundCharges = false;
        for (int slot = 0; slot < v.top && slot <= 53; slot++) {
            MenuView.Item it = v.item(slot);
            boolean isFossil = it.clean.equals("Fossil");
            if (!isFossil && !it.clean.equals("Dirt")) continue;
            if (!foundCharges) {
                Matcher c = it.firstClean(CHARGES.pattern());
                if (c != null) {
                    charges = Integer.parseInt(c.group("charges"));
                    if (maxCharges == 0) maxCharges = charges;
                    foundCharges = true;
                }
            }
            if (isFossil && percentage == null) {
                Matcher p = it.firstClean(PROGRESS.pattern());
                if (p != null) percentage = p.group("progress");
            }
        }
        move = FossilSolver.solve(fossil, dirt, percentage, maxCharges);
    }

    private static final class Panel implements SideHelper.Content {
        @Override
        public String title() {
            return "Fossil Excavator";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("bone");
        }

        @Override
        public int accent() {
            return 0xFFD8B26E;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Excavator"};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            return 0;
        }

        private int row(Ui ui, String text, int x, int y, int w) {
            ui.textShadow(ui.ellipsize(text, w - 4), x + 2, y + 1, Modern.LABEL);
            return 11;
        }

        private int button(Ui ui, String text, int x, int y, int w, Runnable click) {
            boolean hv = ui.hover(x, y, w, 14);
            ui.rect(x, y, w, 14, hv ? 0x66FFFFFF : 0x33000000);
            ui.textShadow(ui.ellipsize(text, w - 6), x + (w - Math.min(ui.width(text), w - 6)) / 2, y + 3, Modern.INK);
            ui.click(x, y, w, 14, click);
            return 16;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            if (Features.on(SOLVER) && !inMenu && move != null) {
                if (move.impossible()) y += row(ui, "§cNo possible fossils on board.", x, y, w);
                else if (move.completed()) y += row(ui, "§aFossil found, get all the loot you can.", x, y, w);
                else y += row(ui, "§ePossible fossils left: §a" + move.remaining(), x, y, w);
                y += row(ui, "§eCharges left: §a" + charges, x, y, w);
                if (!move.possible().isEmpty()) {
                    y += row(ui, "§ePossible types:", x, y, w);
                    for (FossilSolver.Type t : move.possible()) y += row(ui, "§7- §f" + t.displayName, x, y, w);
                }
            }
            if (Features.on(SCRAP) && inMenu && (!MiningPack.on(SCRAP, "mining_scrap_gfs.only_none") || !MiningPack.inInventory(SCRAP_ID))) y += scrap(ui, x, y, w);
            if (y == y0) y += SideHelper.emptyText(ui, "Start the excavator to see the best tile.", x, y, w);
            return y - y0;
        }

        private int scrap(Ui ui, int x, int y, int w) {
            int y0 = y;
            Option amount = Features.get(SCRAP).option("mining_scrap_gfs.amount");
            int n = amount.integer();
            y += row(ui, "§7Fetch amount: §f" + n, x, y, w);
            int third = (w - 8) / 3;
            button(ui, "-1", x, y, third, () -> amount.set(Math.max(1, n - 1)));
            button(ui, "-16", x + third + 4, y, third, () -> amount.set(Math.max(1, n - 16)));
            button(ui, "+16", x + 2 * (third + 4), y, third, () -> amount.set(Math.min(2048, n + 16)));
            y += 16;
            y += button(ui, "§eGet Scrap from Sacks", x, y, w, () -> MiningEnv.world.sendCommand("gfs " + SCRAP_ID + " " + n));
            Integer inSacks = Sacks.amountInSacks(SCRAP_ID);
            if (inSacks != null && inSacks > 0) y += row(ui, "§7Scrap in sacks: §f" + inSacks, x, y, w);
            else {
                y += row(ui, "§7No Scrap in sacks!", x, y, w);
                if (MiningPack.on(SCRAP, "mining_scrap_gfs.bazaar")) y += button(ui, "§eBuy from the Bazaar", x, y, w, () -> MiningEnv.world.sendCommand("bz Suspicious Scrap"));
            }
            return y - y0;
        }
    }

    // skyhanni onInventoryUpdated + onCloseInventory, for the solver and the scrap button both
    private static void watch(Minecraft mc) {
        Screen s = mc.gui.screen();
        boolean open = s != null && INVENTORY.matcher(GameState.strip(s.getTitle().getString()).trim()).matches();
        if (!open) {
            if (wasOpen) clear();
            wasOpen = false;
            return;
        }
        wasOpen = true;
        MenuView v = MenuMarks.current();
        if (v == null || !v.title.equals(TITLE)) return;
        boolean start = false;
        for (int slot = 0; slot < v.top; slot++) if (v.item(slot).clean.equals("Start Excavator")) start = true;
        inMenu = start;
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(SOLVER) && !Features.on(SCRAP)) return null;
        return TITLE.equals(GameState.strip(s.getTitle().getString()).trim()) ? PANEL : null;
    }

    private static boolean inExcavator() {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.gui.screen();
        return s != null && INVENTORY.matcher(GameState.strip(s.getTitle().getString()).trim()).matches() && !inMenu;
    }

    // from skyhanni ExcavatorTooltipHider
    private static void tooltip(ItemStack stack, List<net.minecraft.network.chat.Component> lines) {
        if (!inExcavator()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getInventory().contains(stack)) return;
        if (MiningPack.on(SOLVER, "mining_fossil_solver.hide_all") || (MiningPack.on(SOLVER, "mining_fossil_solver.hide_dirt") && "Dirt".equals(GameState.strip(stack.getHoverName().getString()).trim()))) lines.clear();
    }

    public static void register() {
        Features.add(new Feature(SOLVER, "Mining", "Fossil Excavator solver", "Lights the tile most likely to hold the fossil and says beside the chest which fossils can still be on the board, and hides the tooltips that only get in the way there.", "SkyHanni: Fossil Excavator Helper, Excavator Tooltip Hider, Glacite Powder Stack; Skyblocker: Fossil Solver")
                .when(MiningPack::skyblock).tooltip(FossilExcavator::tooltip)
                .option(Option.bool("mining_fossil_solver.percent", "Show the chance", "", true))
                .option(Option.bool("mining_fossil_solver.hide_dirt", "Hide the Dirt tooltip", "", true))
                .option(Option.bool("mining_fossil_solver.hide_all", "Hide every tooltip on the board", "", false))
                .option(Option.bool("mining_fossil_solver.powder_stack", "Glacite Powder as a number", "On the item, in the excavator's rewards.", true)));
        Features.add(new Feature(SCRAP, "Mining", "Scrap from sacks", "A button beside the Fossil Excavator that fetches Suspicious Scrap from your sacks when you press it, or opens the Bazaar when you have none.", "SkyHanni: Get Scrap from Sacks")
                .when(MiningPack::skyblock).defaultOff()
                .option(Option.integer("mining_scrap_gfs.amount", "Amount", "", 16, 1, 2048))
                .option(Option.bool("mining_scrap_gfs.only_none", "Only without scrap", "Only while you carry none.", true))
                .option(Option.bool("mining_scrap_gfs.bazaar", "Bazaar button", "When your sacks have none either.", true)));
        Safe.endTick("FossilExcavator.watch", FossilExcavator::watch);
        MenuMarks.add(SOLVER, TITLE, FossilExcavator::mark);
        SideHelper.add(FossilExcavator::panel);
    }
}
