// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.plot;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.events.hoppity.HoppityEnv;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenData;
import io.muzy.sbc.garden.GardenExtras;
import io.muzy.sbc.garden.GardenItems;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.render.WorldDraw;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.regex.Matcher;

// what we know per plot, skyhanni GardenPlot + GardenPlotApi: read from Configure Plots, chat and tab
// plus GardenPlotMenuHighlighting, GardenNextPlotPrice, GardenPlotBorders. picked GardenPlotIcon icons live
// on the garden plots panel
public final class GardenPlotInfo {
    public static final String HIGHLIGHT = "garden_plot_menu", PRICE = "garden_plot_price", BORDER = "garden_plot_border";
    private static final String CONFIGURE = "Configure Plots";
    // GardenPlotApi.plotMap
    private static final int[][] PLOT_MAP = {
            {21, 13, 9, 14, 22},
            {15, 5, 1, 6, 16},
            {10, 2, 0, 3, 11},
            {17, 7, 4, 8, 18},
            {23, 19, 12, 20, 24}};
    // SprayonatorType: the held one decides how long the spray lasts, until the tab says
    private static final long BASIC_SPRAY = 30 * 60_000L, JUICY_SPRAY = 45 * 60_000L, SALTY_SPRAY = 60 * 60_000L;

    public static KeyMapping borderKey;
    private static boolean showBorders;
    private static long sprayMs = BASIC_SPRAY;

    private GardenPlotInfo() {}

    // PlotStatusType, order = hierarchy
    enum Status {
        PESTS("pests", MenuView.RED), SPRAYS("sprays", MenuView.GOLD), LOCKED("locked", 0xC0555555), CURRENT("current", MenuView.GREEN), PASTING("pasting", MenuView.YELLOW);

        final String key;
        final int color;

        Status(String key, int color) {
            this.key = key;
            this.color = color;
        }
    }

    private static GardenData.Plot data(int id) {
        return GardenData.get().plots.computeIfAbsent(id, k -> new GardenData.Plot());
    }

    // chest slot of a plot
    static int slot(int row, int column) {
        return row * 9 + 2 + column;
    }

    // getPlot: by position in the garden, -1 outside
    public static int plotAt(double x, double z) {
        if (x < -240 || x > 240 || z < -240 || z > 240) return -1;
        return PLOT_MAP[index(z)][index(x)];
    }

    private static int index(double v) {
        return Math.max(0, Math.min(4, (int) Math.floor((v + 240) / 96)));
    }

    // GardenApi.onUnfarmablePlot: barn plot, or one Configure Plots showed as a greenhouse
    public static boolean onUnfarmablePlot(double x, double z) {
        int id = plotAt(x, z);
        if (id < 0) return false;
        if (id == 0) return true;
        GardenData.Plot plot = GardenData.get().plots.get(id);
        return plot != null && plot.greenhouse;
    }

    static int byName(String name) {
        for (var e : GardenData.get().plots.entrySet()) if (name.equals(e.getValue().name)) return e.getKey();
        try {
            return Integer.parseInt(name.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // GardenPlotApi.onInventoryFullyOpened, on every plot item
    static void readMenu(MenuView v) {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                MenuView.Item it = v.item(slot(r, c));
                if (it.empty()) continue;
                GardenData.Plot plot = data(PLOT_MAP[r][c]);
                Matcher name = Patterns.get("SH.garden.plot.name").matcher(it.name);
                if (name.matches()) plot.name = name.group("name");
                Matcher barn = Patterns.get("SH.garden.plot.barnname").matcher(it.name);
                if (barn.matches()) plot.name = barn.group("name");
                plot.locked = plot.pasting = plot.greenhouse = plot.uncleared = false;
                for (String line : it.lore) {
                    if (line.contains("§7Cost:")) plot.locked = true;
                    if (line.contains("§7Pasting in progress:")) plot.pasting = true;
                    if (Patterns.get("SH.garden.plot.uncleaned").matcher(line).matches()) plot.uncleared = true;
                    if (Patterns.get("SH.garden.plot.greenhouse").matcher(line).matches()) plot.greenhouse = true;
                }
                PlotPests.fromMenu(PLOT_MAP[r][c], it.lore, plot.locked || plot.uncleared);
            }
        }
        Store.markDirty();
    }

    static Status status(int id, GardenData.Plot plot, List<Integer> infested, int here, long now, boolean[] enabled) {
        if (enabled[0] && infested.contains(id)) return Status.PESTS;
        if (enabled[1] && plot != null && plot.sprayExpiry > now) return Status.SPRAYS;
        if (enabled[2] && plot != null && plot.locked) return Status.LOCKED;
        if (enabled[3] && id == here) return Status.CURRENT;
        if (enabled[4] && plot != null && plot.pasting) return Status.PASTING;
        return null;
    }

    // GardenPlotMenuHighlighting
    static void highlight(MenuView v) {
        readMenu(v);
        boolean[] enabled = new boolean[Status.values().length];
        for (Status s : Status.values()) enabled[s.ordinal()] = MenuMarks.setting(HIGHLIGHT, "garden_plot_menu." + s.key);
        var mc = Minecraft.getInstance();
        int here = mc.player == null ? -1 : plotAt(mc.player.getX(), mc.player.getZ());
        List<Integer> infested = GardenExtras.infestedPlots();
        long now = System.currentTimeMillis();
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                int id = PLOT_MAP[r][c], slot = slot(r, c);
                GardenData.Plot plot = GardenData.get().plots.get(id);
                Status status = status(id, plot, infested, here, now, enabled);
                if (status == null) continue;
                v.back(slot, status.color);
                if (status == Status.SPRAYS) v.number(slot, String.valueOf(Math.max(1, (plot.sprayExpiry - now) / 60_000L)));
            }
        }
    }

    // GardenPlotApi.onChat
    static ChatBus.Result onChat(ChatBus.Line line) {
        if (!"GARDEN".equals(GameState.island)) return ChatBus.PASS;
        String clean = line.plain();
        String message = LegacyText.of(line.component());
        Matcher m = Patterns.get("SH.garden.plot.spray.target.colorless").matcher(clean);
        if (m.matches()) {
            int id = byName(m.group("plot"));
            if (id >= 0) {
                GardenData.Plot plot = data(id);
                plot.sprayType = m.group("spray");
                plot.sprayExpiry = System.currentTimeMillis() + sprayMs;
                Store.markDirty();
            }
        }
        m = Patterns.get("SH.garden.plot.chat.clean").matcher(message);
        if (m.matches()) {
            int id = byName(m.group("plot"));
            if (id >= 0) data(id).uncleared = false;
        }
        m = Patterns.get("SH.garden.plot.chat.unlock").matcher(message);
        if (m.matches()) {
            int id = byName(m.group("plot"));
            if (id >= 0) data(id).locked = false;
        }
        if (Patterns.get("SH.garden.plot.spray.cleared.portablewasher-nocolor").matcher(clean).matches()) {
            for (GardenData.Plot p : GardenData.get().plots.values()) p.sprayExpiry = 0;
            Store.markDirty();
        }
        return ChatBus.PASS;
    }

    // GardenPlotApi.onWidgetUpdate: Pests widget says the current plot's spray
    public static void second(Minecraft mc) {
        if (mc.player == null || !"GARDEN".equals(GameState.island)) return;
        PlotPests.second();
        // SprayonatorType.getRecentlyHeldOrNull
        String held = Gain.sbId(mc.player.getMainHandItem());
        if (held.equals("SPRAYONATOR")) sprayMs = BASIC_SPRAY;
        else if (held.equals("JUICY_SPRAYONATOR")) sprayMs = JUICY_SPRAY;
        else if (held.equals("SALTY_SPRAYONATOR")) sprayMs = SALTY_SPRAY;
        int id = plotAt(mc.player.getX(), mc.player.getZ());
        if (id <= 0) return;
        for (String line : GameState.tab) {
            Matcher m = Patterns.get("SH.garden.plot.tablist.spraytime-nocolor").matcher(line.trim());
            if (!m.matches()) continue;
            GardenData.Plot plot = data(id);
            String time = m.group("time");
            if (time == null) {
                if (plot.sprayExpiry != 0) {
                    plot.sprayExpiry = 0;
                    Store.markDirty();
                }
                return;
            }
            Long left = HoppityEnv.duration(time);
            if (left != null) {
                plot.sprayType = m.group("spray").trim();
                plot.sprayExpiry = System.currentTimeMillis() + left;
            }
            return;
        }
    }

    // GardenNextPlotPrice.onToolTip
    static void priceTooltip(ItemStack stack, List<Component> lines) {
        MenuView v = MenuMarks.current();
        if (v == null || !v.title.equals(CONFIGURE) || !GameState.strip(stack.getHoverName().getString()).startsWith("Plot")) return;
        for (int i = 0; i < lines.size() - 1; i++) {
            if (!lines.get(i).getString().contains("Cost")) continue;
            GardenItems.Amount read = GardenItems.readItemAmount(LegacyText.of(lines.get(i + 1)));
            String id = read == null ? null : GardenItems.idOf(read.name());
            if (id != null) lines.set(i + 1, lines.get(i + 1).copy().append(" §7(§6" + Prices.coins(GardenItems.price(id) * read.amount()) + "§7)"));
            return;
        }
    }

    // renderPlot: every 4 blocks, like F3 + G shows chunks
    private static void renderPlot(WorldDraw w, int id, int lineColor, int cornerColor, boolean buildLimit) {
        Vec3 middle = GardenExtras.plotCenter(id);
        if (middle == null) return;
        int size = 96, minX = (int) Math.floor(middle.x) - 48, minZ = (int) Math.floor(middle.z) - 48, minY = 66, maxY = 66 + 36, limit = minY + 11;
        for (int i = 0; i <= size; i += size)
            for (int j = 0; j <= size; j += size) w.line(new Vec3(minX + i, minY, minZ + j), new Vec3(minX + i, maxY, minZ + j), cornerColor, 3, true);
        for (int x = 4; x < size; x += 4) {
            w.line(new Vec3(minX + x, minY, minZ), new Vec3(minX + x, maxY, minZ), lineColor, 2, true);
            w.line(new Vec3(minX + x, minY, minZ + size), new Vec3(minX + x, maxY, minZ + size), lineColor, 2, true);
        }
        for (int z = 4; z < size; z += 4) {
            w.line(new Vec3(minX, minY, minZ + z), new Vec3(minX, maxY, minZ + z), lineColor, 2, true);
            w.line(new Vec3(minX + size, minY, minZ + z), new Vec3(minX + size, maxY, minZ + z), lineColor, 2, true);
        }
        for (int y = minY; y <= maxY; y += 4) {
            int color = y == limit ? 0xFFFF0000 : lineColor;
            horizontal(w, minX, y, minZ, size, color);
        }
        if (buildLimit) horizontal(w, minX, limit, minZ, size, 0xFFFF0000);
    }

    private static void horizontal(WorldDraw w, int x, int y, int z, int size, int color) {
        w.line(new Vec3(x, y, z), new Vec3(x, y, z + size), color, 2, true);
        w.line(new Vec3(x, y, z + size), new Vec3(x + size, y, z + size), color, 2, true);
        w.line(new Vec3(x + size, y, z + size), new Vec3(x + size, y, z), color, 2, true);
        w.line(new Vec3(x + size, y, z), new Vec3(x, y, z), color, 2, true);
    }

    // closest plot's number
    private static int closest(double x, double z) {
        return plotAt(Math.max(-240, Math.min(240, x)), Math.max(-240, Math.min(240, z)));
    }

    private static boolean sprayonatorHeld(Minecraft mc) {
        String held = Gain.sbId(mc.player.getMainHandItem());
        return held.endsWith("SPRAYONATOR");
    }

    // GardenPlotBorders.onKeyDown, SprayFeatures
    static void draw(WorldDraw w) {
        if (w.mc.player == null || !"GARDEN".equals(GameState.island)) return;
        Feature f = Features.get(BORDER);
        Option spray = f == null ? null : f.option("garden_plot_border.sprayonator");
        boolean byKey = showBorders;
        if (!byKey && (spray == null || !spray.bool() || !sprayonatorHeld(w.mc))) return;
        int id = byKey ? closest(w.mc.player.getX(), w.mc.player.getZ()) : plotAt(w.mc.player.getX(), w.mc.player.getZ());
        if (id < 0) return;
        renderPlot(w, id, 0xFFFFFF55, 0xFF0000AA, byKey);
    }

    public static void keyTick(Minecraft mc) {
        if (borderKey == null) return;
        while (borderKey.consumeClick()) if (mc.gui.screen() == null && "GARDEN".equals(GameState.island)) showBorders = !showBorders;
    }

    public static void keys(KeyMapping.Category category) {
        borderKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.plot_border", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    public static void register() {
        Feature highlight = new Feature(HIGHLIGHT, "Garden", "Configure Plots highlights", "In Configure Plots, plots get a colour by their state: pests, a spray with its minutes left, locked, the one you are on, or being pasted.", "SkyHanni: Plot Menu Highlighting")
                .when(() -> "GARDEN".equals(GameState.island))
                .option(Option.bool("garden_plot_menu.pests", "Pests", "", true))
                .option(Option.bool("garden_plot_menu.sprays", "Sprays", "", true))
                .option(Option.bool("garden_plot_menu.locked", "Locked", "", true))
                .option(Option.bool("garden_plot_menu.current", "Current plot", "", true))
                .option(Option.bool("garden_plot_menu.pasting", "Pasting", "", true));
        Features.add(highlight);
        MenuMarks.add(HIGHLIGHT, CONFIGURE, GardenPlotInfo::highlight);
        MenuMarks.add("garden_pest_finder", CONFIGURE, GardenPlotInfo::readMenu);
        Features.add(new Feature(PRICE, "Garden", "Plot price", "In Configure Plots, the price in coins of what the next plot costs.", "SkyHanni: Plot Price")
                .when(() -> "GARDEN".equals(GameState.island))
                .tooltip(GardenPlotInfo::priceTooltip));
        Features.add(new Feature(BORDER, "Garden", "Plot borders", "The borders of your plot in the world, as F3 + G shows chunks, with the build limit: toggle them with the Plot Borders key, or see them while holding a Sprayonator.", "SkyHanni: Plot Border")
                .when(() -> "GARDEN".equals(GameState.island))
                .world(GardenPlotInfo::draw)
                .option(Option.bool("garden_plot_border.sprayonator", "While holding a Sprayonator", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 18, "garden_plot_info", null, GardenPlotInfo::onChat);
        Safe.commands("GardenPlotInfo.commands", (dispatcher, registry) -> dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("sbc")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("resetploticons").executes(c -> {
                    io.muzy.sbc.ui.panels.GardenPlots.clearCustomIcons();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fEvery plot icon is back to what the menu shows."));
                    return 1;
                }))));
        io.muzy.sbc.core.Commands.describe("resetploticons", "Put the icons of the Garden Plots panel back to what the menu shows");
    }
}
