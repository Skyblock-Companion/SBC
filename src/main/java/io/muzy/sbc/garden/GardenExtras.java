// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemIcons;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.events.hoppity.HoppityEnv;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.garden.milestones.GardenCard;
import io.muzy.sbc.garden.milestones.GardenCard.Beside;
import io.muzy.sbc.garden.milestones.GardenCard.Block;
import io.muzy.sbc.garden.milestones.GardenCard.Chip;
import io.muzy.sbc.garden.milestones.GardenCard.Line;
import io.muzy.sbc.garden.milestones.GardenCard.Look;
import io.muzy.sbc.garden.milestones.GardenCard.Pair;
import io.muzy.sbc.garden.milestones.GardenCard.Rule;
import io.muzy.sbc.garden.milestones.GardenCropSpeed;
import io.muzy.sbc.garden.milestones.GardenMilestones;
import io.muzy.sbc.nav.Waypoints;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.Toasts;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.hud.Compact;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// garden helpers from tab, scoreboard and chat: pests (count, infested plots as waypoints, spawn alerts,
// cooldown), visitors + next visitor timer, composter levels + empty warning, jacob contest lines, and the
// speed your tool wants vs what you have
public final class GardenExtras {
    static final Pattern PEST_ONE = Pattern.compile("^GROSS! A .*?Pest has appeared in (Plot - (.+?)|The Barn)!$");
    static final Pattern PEST_MANY = Pattern.compile("^YUCK! (\\d+) .*?Pests? have spawned in (Plot - (.+?)|The Barn)!$");
    static final Pattern PEST_PLOTS_TAB = Pattern.compile("^\\s*Plots: (.+)$");
    static final Pattern PEST_COOLDOWN = Pattern.compile("^\\s*Cooldown: (.+)$");
    static final Pattern PESTS_SB = Pattern.compile("^.*?The Garden.*?x(\\d+)$");
    static final Pattern CONTEST_SB = Pattern.compile("^\\u25CB (.+?) (\\d+m\\d+s|\\d+m|\\d+s)$");
    static final Pattern CONTEST_LEFT = Pattern.compile("^Jacob's Contest: (\\d+m(?: \\d+s)?|\\d+s) left$");
    static final Pattern NEXT_VISITOR = Pattern.compile("^\\s*Next Visitor: (.+)$");
    static final Pattern COMPOSTER_LINE = Pattern.compile("^\\s*(Organic Matter|Fuel|Time Left|Stored Compost): (.+)$");
    static final Pattern PCT = Pattern.compile("([\\d.]+)%");
    private static final Pattern FORTUNE_LINE = Pattern.compile("^\\s*(.+ Fortune): (.+)$");

    // plot number -> grid col/row (0..4), barn is the center
    private static final int[][] PLOT_GRID = {
            {21, 13, 9, 14, 22},
            {15, 5, 1, 6, 16},
            {10, 2, 0, 3, 11},
            {17, 7, 4, 8, 18},
            {23, 19, 12, 20, 24}};

    private static final Map<String, Integer> SPEED = Map.ofEntries(
            Map.entry("wheat", 93), Map.entry("carrot", 93), Map.entry("potato", 93), Map.entry("nether wart", 93), Map.entry("warts", 93),
            Map.entry("pumpkin", 155), Map.entry("melon", 155), Map.entry("cocoa", 155), Map.entry("sugar cane", 328), Map.entry("cactus", 464), Map.entry("mushroom", 233), Map.entry("fungi", 233),
            Map.entry("sunflower", 328), Map.entry("moonflower", 328), Map.entry("wild rose", 328));

    // GardenOptimalSpeed's custom speed for this crop
    static int wantedSpeed(String crop) {
        String key = switch (crop) {
            case "nether wart", "warts" -> "speed_wart";
            case "sugar cane" -> "speed_cane";
            case "fungi" -> "speed_mushroom";
            case "wild rose" -> "speed_rose";
            case "moonflower" -> "speed_sunflower";
            default -> "speed_" + crop;
        };
        return io.muzy.sbc.garden.GardenPack.number("garden_speed", key, SPEED.getOrDefault(crop, 0));
    }

    // skyhanni PestSpawnTimer: " Alive: 8" is the max
    private static final int MAX_PESTS = 8;
    // skyhanni FarmingContestApi: a contest runs 20 min
    private static final long CONTEST_MS = 20 * 60_000L;
    // how wide a compact card line can be to share a row
    private static final int HALF_ROW = (Compact.WIDE - 2 * Modern.PAD - Compact.GAP) / 2;

    private static long lastPestAlert, lastComposterWarn;

    private GardenExtras() {}

    private static boolean garden() {
        return "GARDEN".equals(GameState.island) || io.muzy.sbc.SbcMod.DEV;
    }

    // center of a plot by number, or null
    public static Vec3 plotCenter(int plot) {
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) if (PLOT_GRID[r][c] == plot) return new Vec3((c - 2) * 96 + 0.5, 73, (r - 2) * 96 + 0.5);
        return null;
    }

    // plot you stand on, -1 = none
    private static int plotHere() {
        var mc = Minecraft.getInstance();
        var p = mc == null ? null : mc.player;
        if (p == null) return -1;
        for (int r = 0; r < 5; r++)
            for (int c = 0; c < 5; c++)
                if (Math.abs(p.getX() - ((c - 2) * 96 + 0.5)) <= 48 && Math.abs(p.getZ() - ((r - 2) * 96 + 0.5)) <= 48) return PLOT_GRID[r][c];
        return -1;
    }

    public static List<Integer> infestedPlots() {
        List<Integer> out = new ArrayList<>();
        for (String l : GameState.tab) {
            Matcher m = io.muzy.sbc.core.Patterns.get("SH.garden.pests-api.tablist.infected-plots-no-color").matcher(l);
            if (!m.matches()) continue;
            for (String p : m.group("plots").split(",")) {
                try {
                    out.add(Integer.parseInt(p.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }

    private static String tabValue(Pattern p) {
        for (String l : GameState.tab) {
            Matcher m = p.matcher(l);
            if (m.matches()) return m.group(1).trim();
        }
        return "";
    }

    // hypixel's own icons mean nothing without its font
    private static String noGlyphs(String s) {
        return s.replaceAll("[\\uE000-\\uF8FF]", "").trim();
    }

    // what a "Key: value" line says, or ""
    private static String field(List<String> lines, String key) {
        String prefix = key + ":";
        for (String l : lines) {
            String t = l.trim();
            if (t.startsWith(prefix)) return noGlyphs(t.substring(prefix.length()));
        }
        return "";
    }

    // pure: one stripped chat line, plot name when a pest spawned, else null
    public static String pestSpawn(String plain) {
        Matcher m = PEST_ONE.matcher(plain.trim());
        if (m.matches()) return m.group(1);
        m = PEST_MANY.matcher(plain.trim());
        if (m.matches()) return m.group(2);
        return null;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!garden() || !Features.on("garden_pests")) return ChatBus.PASS;
        String where = pestSpawn(line.plain());
        if (where != null) {
            long now = System.currentTimeMillis();
            if (now - lastPestAlert > 2000) {
                lastPestAlert = now;
                Toasts.push("Pest!", where, 0xFF97C459);
                var mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.2f);
            }
        }
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        if (!garden() || !Features.on("garden_composter")) return;
        long now = System.currentTimeMillis();
        for (String l : GameState.tab) {
            Matcher m = COMPOSTER_LINE.matcher(l);
            if (!m.matches()) continue;
            Matcher p = PCT.matcher(m.group(2));
            if ((m.group(1).equals("Organic Matter") || m.group(1).equals("Fuel")) && p.find() && Double.parseDouble(p.group(1)) < 5 && now - lastComposterWarn > 300_000) {
                lastComposterWarn = now;
                Toasts.push("Composter " + m.group(1).toLowerCase(Locale.ROOT) + " almost empty", m.group(2), 0xFFEF9F27);
            }
        }
    }

    private static List<String> tabSection(String header) {
        List<String> out = new ArrayList<>();
        boolean in = false;
        for (String l : GameState.tab) {
            String t = l.trim();
            if (t.startsWith(header)) {
                in = true;
                String rest = t.substring(header.length()).trim();
                if (!rest.isEmpty()) out.add(rest);
                continue;
            }
            if (in) {
                if (t.isEmpty() || (!l.startsWith(" ") && t.endsWith(":"))) break;
                if (!l.startsWith(" ") && !t.startsWith("○")) break;
                out.add(t);
            }
        }
        return out;
    }

    // crop a held farming tool is for, or empty
    static String heldCrop(String toolName) {
        String n = toolName.toLowerCase(Locale.ROOT);
        for (String k : new String[]{"nether wart", "warts", "sugar cane", "cocoa", "pumpkin", "melon", "cactus", "mushroom", "fungi", "wheat", "carrot", "potato", "sunflower", "moonflower", "wild rose"})
            if (n.contains(k)) return k;
        return "";
    }

    private static boolean none(String v) {
        return v.isEmpty() || v.equalsIgnoreCase("None") || v.equalsIgnoreCase("INACTIVE");
    }

    // --- pests ---

    // the tab's Pests + Pest Traps widgets. alive = -1 when neither they nor the sidebar say it, thisPlot is "4
    // x2" when the sidebar says it
    private record PestView(int alive, List<Integer> plots, String cooldown, String spray, String repellent, String bonus, String chance, String traps, String full, String noBait, int here, String thisPlot) {
        static final PestView DEMO = new PestView(4, List.of(4, 12, 13), "1m 58s", "None", "Regular (58m)", "+200 29m", "162", "1/3", "None", "#1", 4, "4 x2");

        static PestView read() {
            int count = -1;
            String thisPlot = "";
            for (String l : GameState.scoreboardFormatted) {
                Matcher m = io.muzy.sbc.core.Patterns.get("SH.garden.pests-api.scoreboard.pests").matcher(l);
                if (m.matches()) try {
                    count = Integer.parseInt(GameState.strip(m.group("pests")).trim());
                } catch (NumberFormatException ignored) {
                }
                m = io.muzy.sbc.core.Patterns.get("SH.garden.pests-api.scoreboard.plot.pests").matcher(l);
                if (m.matches()) thisPlot = GameState.strip(m.group("plot")).trim() + " x" + m.group("pests");
            }
            List<String> pests = tabSection("Pests:"), traps = tabSection("Pest Traps:");
            String alive = field(pests, "Alive");
            if (count < 0 && alive.matches("\\d+")) count = Integer.parseInt(alive);
            return new PestView(count, infestedPlots(), tabValue(PEST_COOLDOWN), field(pests, "Spray"), field(pests, "Repellent"), field(pests, "Bonus"), io.muzy.sbc.garden.farming.TrueFortune.chanceText(field(GameState.tab, "Bonus Pest Chance")),
                    traps.isEmpty() ? "" : noGlyphs(traps.get(0)), field(traps, "Full Traps"), field(traps, "No Bait"), plotHere(), thisPlot);
        }

        String plotList() {
            return plots.toString().replaceAll("[\\[\\]]", "");
        }
    }

    // the garden from above: infested plots lit, barn in the middle, frame where you stand. tight in a compact card
    private record PlotMap(List<Integer> infested, int here, boolean tight) implements Block {
        private static final int CELL_W = 12;

        private int cellH() {
            return tight ? 9 : 10;
        }

        private int gap() {
            return tight ? 1 : 2;
        }

        @Override
        public int height(int rowH) {
            return 5 * cellH() + 4 * gap();
        }

        @Override
        public int width(Ui ui) {
            return 5 * CELL_W + 4 * gap();
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
            int cellH = cellH(), gap = gap();
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 5; c++) {
                    int plot = PLOT_GRID[r][c], cx = x + c * (CELL_W + gap), cy = y + r * (cellH + gap);
                    boolean bad = infested.contains(plot);
                    ui.rect(cx, cy, CELL_W, cellH, bad ? Ui.alpha(Modern.BAD, 0.85f) : plot == 0 ? Ui.alpha(0xFFD8B26E, 0.42f) : 0x1EFFFFFF);
                    if (bad) ui.rect(cx, cy, CELL_W, 1, Ui.alpha(Modern.lighten(Modern.BAD, 0.5f), 0.9f));
                    if (plot == here) {
                        int a = Modern.AQUA;
                        ui.rect(cx - 1, cy - 1, CELL_W + 2, 1, a);
                        ui.rect(cx - 1, cy + cellH, CELL_W + 2, 1, a);
                        ui.rect(cx - 1, cy, 1, cellH, a);
                        ui.rect(cx + CELL_W, cy, 1, cellH, a);
                    }
                    String n = plot == 0 ? "B" : String.valueOf(plot);
                    int tx = cx + (CELL_W - ui.width(n)) / 2 + 1, ty = cy + 1;
                    if (bad) ui.textShadow(n, tx, ty, Modern.INK);
                    else ui.text(n, tx, ty, plot == here ? Modern.AQUA : plot == 0 ? 0xFFD8B26E : Modern.DIM);
                }
            }
        }
    }

    public static final HudElement PESTS = new GardenCard("garden_pests", "Pests", Zone.TOP_LEFT, 31, "garden_pests") {
        @Override
        public boolean visible() {
            if (!garden()) return false;
            for (String l : GameState.scoreboardFormatted) if (io.muzy.sbc.core.Patterns.get("SH.garden.pests-api.scoreboard.pests").matcher(l).matches()) return true;
            return !infestedPlots().isEmpty() || !tabValue(PEST_COOLDOWN).isEmpty();
        }

        private List<Row> rowsOf(PestView v) {
            List<Row> out = new ArrayList<>();
            out.add(new Row("Alive", v.alive() < 0 ? "-" : String.valueOf(v.alive()), v.alive() > 0 ? Theme.current().bad() : Theme.current().good()));
            if (!v.plots().isEmpty()) out.add(new Row("Plots", v.plotList(), Theme.current().accent()));
            if (!v.thisPlot().isEmpty()) out.add(new Row("This plot", v.thisPlot(), Theme.current().bad()));
            if (!v.cooldown().isEmpty()) out.add(new Row("Cooldown", v.cooldown(), v.cooldown().equals("READY") ? Theme.current().good() : Theme.current().dim()));
            if (!v.spray().isEmpty()) out.add(new Row("Spray", v.spray(), Theme.current().dim()));
            if (!v.repellent().isEmpty()) out.add(new Row("Repellent", v.repellent(), Theme.current().dim()));
            if (!v.bonus().isEmpty()) out.add(new Row("Bonus", v.bonus(), Theme.current().dim()));
            if (!v.chance().isEmpty()) out.add(new Row("Bonus Pest Chance", v.chance(), Theme.current().dim()));
            if (!v.traps().isEmpty()) out.add(new Row("Pest Traps", v.traps(), Theme.current().accent()));
            if (!v.full().isEmpty()) out.add(new Row("Full Traps", v.full(), Theme.current().dim()));
            if (!v.noBait().isEmpty()) out.add(new Row("No Bait", v.noBait(), Theme.current().dim()));
            return out;
        }

        @Override
        public List<Row> rows() {
            return rowsOf(PestView.read());
        }

        @Override
        public List<Row> demo() {
            return rowsOf(PestView.DEMO);
        }

        private PestView view() {
            return demo ? PestView.DEMO : PestView.read();
        }

        @Override
        protected Chip chip() {
            PestView v = view();
            if (v.alive() >= MAX_PESTS || v.cooldown().equals("MAX PESTS")) return new Chip("MAX PESTS", Modern.BAD);
            if (v.alive() > 0) return new Chip("INFESTED", Modern.WARN);
            return v.alive() == 0 ? new Chip("CLEAR", Modern.GOOD) : null;
        }

        private void info(List<Block> out, String label, String value, int color) {
            if (!value.isEmpty() && shown(label)) out.add(new Line(label).value(value, none(value) ? Modern.DIM : color));
        }

        // short form: meters + what fits next to the map, rest under it where short neighbours share a row
        private List<Block> brief(PestView v) {
            List<Block> lines = new ArrayList<>();
            if (shown("Alive") && v.alive() < 0) lines.add(new Line("Alive").value("-", Modern.DIM));
            else if (shown("Alive")) {
                double p = Math.min(1, (double) v.alive() / MAX_PESTS);
                lines.add(new Line("Alive").value(Modern.fraction(String.valueOf(v.alive()), String.valueOf(MAX_PESTS), v.alive() > 0 ? 'c' : 'a', 'f') + "  §7" + RowText.percent(p)).meter(p, Modern.BAD));
            }
            if (!v.traps().isEmpty() && shown("Pest Traps")) {
                RowText.Shaped s = RowText.shape("Pest Traps", v.traps().replaceFirst("^(\\d+)/(\\d+)$", "§e$1§7/§f$2"));
                Line traps = new Line(s.label()).brief("Traps").value(s.value());
                if (s.progress() >= 0) traps.meter(s.progress(), accent());
                lines.add(traps);
            }
            info(lines, "Plots", v.plots().isEmpty() ? "" : v.plotList(), Modern.lighten(Modern.BAD, 0.2f));
            info(lines, "This plot", v.thisPlot(), Modern.BAD);
            int cd = v.cooldown().equals("READY") ? Modern.GOOD : v.cooldown().equals("MAX PESTS") ? Modern.BAD : Modern.AQUA;
            if (!v.cooldown().isEmpty() && shown("Cooldown")) lines.add(new Line("Cooldown").brief("CD").value(v.cooldown(), none(v.cooldown()) ? Modern.DIM : cd));
            info(lines, "Spray", v.spray(), Modern.GOOD);
            info(lines, "Bonus Pest Chance", v.chance(), Modern.INK);
            info(lines, "Repellent", v.repellent(), Modern.GOOD);
            if (!v.bonus().isEmpty() && shown("Bonus")) lines.add(new Line("Fortune Bonus").value(none(v.bonus()) ? "Off" : v.bonus(), none(v.bonus()) ? Modern.DIM : Modern.GOLD));
            List<Block> out = new ArrayList<>();
            if (!v.plots().isEmpty() && shown("Plots")) {
                PlotMap map = new PlotMap(v.plots(), v.here(), true);
                int room = Compact.WIDE - 2 * Modern.PAD - map.width(Ui.MEASURE) - Beside.GAP;
                List<Block> side = new ArrayList<>();
                // 5 lines = the map's height, also with both meters
                for (Block b : lines) {
                    if (side.size() < 5 && ((Line) b).compactWidth(Ui.MEASURE) <= room) side.add(b);
                    else out.add(b);
                }
                out.add(0, new Beside(map, side));
            } else out.addAll(lines);
            if (!v.full().isEmpty() && shown("Full Traps")) out.add(briefTrap("Full Traps", v.full()));
            if (!v.noBait().isEmpty() && shown("No Bait")) out.add(briefTrap("No Bait", v.noBait()));
            return out;
        }

        // half a row is just too short for "Full Traps" and "None"
        private Line briefTrap(String label, String value) {
            return new Line(label).value(value.equals("None") ? "-" : value, none(value) ? Modern.DIM : Modern.WARN);
        }

        @Override
        protected List<Block> blocks() {
            PestView v = view();
            if (compact()) return brief(v);
            List<Block> out = new ArrayList<>();
            if (shown("Alive") && v.alive() < 0) out.add(new Line("Alive").value("-", Modern.DIM));
            else if (shown("Alive")) {
                double p = Math.min(1, (double) v.alive() / MAX_PESTS);
                out.add(new Line("Alive").value(Modern.fraction(String.valueOf(v.alive()), String.valueOf(MAX_PESTS), v.alive() > 0 ? 'c' : 'a', 'f') + "  §7" + RowText.percent(p)).meter(p, Modern.BAD));
            }
            List<Block> side = new ArrayList<>();
            info(side, "Plots", v.plots().isEmpty() ? "" : v.plotList(), Modern.lighten(Modern.BAD, 0.2f));
            info(side, "This plot", v.thisPlot(), Modern.BAD);
            int cd = v.cooldown().equals("READY") ? Modern.GOOD : v.cooldown().equals("MAX PESTS") ? Modern.BAD : Modern.AQUA;
            info(side, "Cooldown", v.cooldown(), cd);
            info(side, "Spray", v.spray(), Modern.GOOD);
            info(side, "Repellent", v.repellent(), Modern.GOOD);
            if (!v.bonus().isEmpty() && shown("Bonus")) side.add(new Line("Fortune Bonus").value(v.bonus(), none(v.bonus()) ? Modern.DIM : Modern.GOLD));
            info(side, "Bonus Pest Chance", v.chance(), Modern.INK);
            if (!v.plots().isEmpty() && shown("Plots")) out.add(new Beside(new PlotMap(v.plots(), v.here(), false), side));
            else out.addAll(side);
            if (!v.traps().isEmpty() && shown("Pest Traps")) {
                RowText.Shaped s = RowText.shape("Pest Traps", v.traps().replaceFirst("^(\\d+)/(\\d+)$", "§e$1§7/§f$2"));
                out.add(new Rule());
                Line traps = new Line(s.label()).value(s.value());
                if (s.progress() >= 0) traps.meter(s.progress(), accent());
                out.add(traps);
            }
            boolean full = !v.full().isEmpty() && shown("Full Traps"), bait = !v.noBait().isEmpty() && shown("No Bait");
            if (full && bait) out.add(new Pair(trapLine("Full Traps", v.full()), trapLine("No Bait", v.noBait())));
            else if (full) out.add(trapLine("Full Traps", v.full()));
            else if (bait) out.add(trapLine("No Bait", v.noBait()));
            return out;
        }

        private Line trapLine(String label, String value) {
            return new Line(label).value(value, none(value) ? Modern.DIM : Modern.WARN);
        }
    };

    // --- visitors ---

    private static Map<String, JsonObject> visitorRepo;

    // skyhanni's Garden.json: each visitor's rarity + what it asks for
    private static Map<String, JsonObject> visitorRepo() {
        if (visitorRepo != null) return visitorRepo;
        Map<String, JsonObject> out = new HashMap<>();
        try (var in = io.muzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in != null) {
                JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("visitors");
                for (Map.Entry<String, JsonElement> e : all.entrySet()) out.put(e.getKey(), e.getValue().getAsJsonObject());
            }
        } catch (Exception e) {
            io.muzy.sbc.SbcMod.LOG.warn("No visitor data in Garden.json", e);
        }
        visitorRepo = out;
        return out;
    }

    private static int rarityColor(String rarity) {
        return switch (rarity) {
            case "uncommon" -> 0xFF55FF55;
            case "rare" -> 0xFF6FA8FF;
            case "legendary" -> 0xFFFFAA00;
            case "mythic" -> 0xFFFF55FF;
            case "special" -> 0xFFFF5555;
            default -> Modern.INK;
        };
    }

    // icon for something a visitor wants, the real skyblock item if it is one. null if nothing shows it
    private static ItemStack askedIcon(String item) {
        CropType crop = CropType.getByNameOrNull(item);
        if (crop != null) return LegacyItems.stack(crop.icon());
        // same icon as the composter card, also works without hypixel's resource pack
        if (item.equals("Compost")) return LegacyItems.stack("minecraft:bone_meal");
        String id = ItemRepo.idForName(item);
        if (id != null) return ItemIcons.of(id);
        String plain = switch (item) {
            case "Red Mushroom Block" -> "red_mushroom_block";
            case "Golden Carrot", "Enchanted Golden Carrot" -> "golden_carrot";
            case "Enchanted Baked Potato" -> "baked_potato";
            case "Enchanted Sugar" -> "sugar";
            case "Bread" -> "bread";
            case "Cake" -> "cake";
            case "Raw Mutton" -> "mutton";
            case "Raw Porkchop" -> "porkchop";
            case "Raw Rabbit" -> "rabbit";
            case "Jack o' Lantern" -> "jack_o_lantern";
            case "Music Disc - 13" -> "music_disc_13";
            default -> null;
        };
        return plain == null ? null : LegacyItems.stack("minecraft:" + plain);
    }

    // what a visitor wants, at the end of its line: every item's icon + how many more have none. the ones that
    // take anything say so
    private static void asked(Line row, JsonObject info) {
        JsonArray needs = info.has("need_items") ? info.getAsJsonArray("need_items") : new JsonArray();
        if (needs.isEmpty()) {
            row.value(info.has("unknown_rewards") && info.get("unknown_rewards").getAsBoolean() ? "§7Unknown" : "§7Any");
            return;
        }
        List<ItemStack> icons = new ArrayList<>();
        for (JsonElement e : needs) {
            ItemStack icon = askedIcon(e.getAsString());
            if (icon != null) icons.add(icon);
        }
        row.tail(icons);
        if (icons.size() < needs.size()) row.value("§7+" + (needs.size() - icons.size()));
    }

    private record VisitorView(List<String> waiting, int count, String next) {
        static final VisitorView DEMO = new VisitorView(List.of("Duncan NEW!", "Anita", "Lumina", "Madame Eleanor Q. Goldsworth III", "Lumber Jack"), 5, "Queue Full!");

        static VisitorView read() {
            List<String> waiting = new ArrayList<>();
            int count = -1;
            for (String v : tabSection("Visitors:")) {
                if (v.startsWith("(")) {
                    String n = v.replaceAll("\\D", "");
                    if (!n.isEmpty()) count = Integer.parseInt(n);
                } else if (!NEXT_VISITOR.matcher(v).matches()) waiting.add(v);
            }
            return new VisitorView(waiting, count < 0 ? waiting.size() : count, tabValue(NEXT_VISITOR));
        }
    }

    public static final HudElement VISITORS = new GardenCard("garden_visitors", "Visitors", Zone.TOP_LEFT, 32, "garden_visitors") {
        @Override
        public boolean visible() {
            return garden() && (!tabSection("Visitors:").isEmpty() || !tabValue(NEXT_VISITOR).isEmpty());
        }

        private List<Row> rowsOf(VisitorView v) {
            List<Row> out = new ArrayList<>();
            for (String name : v.waiting()) out.add(new Row(name));
            if (!v.next().isEmpty()) out.add(new Row("Next", v.next(), v.next().contains("Full") ? Theme.current().bad() : Theme.current().accent()));
            return out;
        }

        @Override
        public List<Row> rows() {
            return rowsOf(VisitorView.read());
        }

        @Override
        public List<Row> demo() {
            return rowsOf(VisitorView.DEMO);
        }

        private VisitorView view() {
            return demo ? VisitorView.DEMO : VisitorView.read();
        }

        @Override
        protected String bandValue() {
            int n = view().count();
            return n > 0 ? "§f" + n + " §7waiting" : "";
        }

        @Override
        protected Chip chip() {
            return view().next().contains("Full") ? new Chip("FULL", Modern.BAD) : null;
        }

        // name too long for a compact row: middle words as initials, still says who it is
        private static String shortName(String name) {
            String[] words = name.split(" ");
            if (words.length < 4) return name;
            for (int i = 1; i < words.length - 2; i++) if (!words[i].endsWith(".")) words[i] = words[i].charAt(0) + ".";
            return String.join(" ", words);
        }

        @Override
        protected List<Block> blocks() {
            VisitorView v = view();
            List<Block> out = new ArrayList<>(), wide = new ArrayList<>();
            for (String entry : v.waiting()) {
                if (!shown(entry)) continue;
                boolean fresh = entry.endsWith(" NEW!");
                String name = fresh ? entry.substring(0, entry.length() - 5).trim() : entry;
                JsonObject info = visitorRepo().get(name);
                Line row = new Line(name).brief(shortName(name)).pairs();
                if (info == null) row.labelColor(Modern.INK).value("§7?");
                else {
                    String rarity = info.has("rarity") ? info.get("rarity").getAsString() : "";
                    row.labelColor(rarityColor(rarity));
                    asked(row, info);
                }
                if (fresh) row.chip("NEW", Modern.GOOD);
                // in a compact card the ones that need a whole row go first, so the others end up neighbours and share theirs
                if (compact() && row.compactWidth(Ui.MEASURE) > HALF_ROW) wide.add(row);
                else out.add(row);
            }
            out.addAll(0, wide);
            if (!v.next().isEmpty() && shown("Next")) {
                if (!out.isEmpty() && !compact()) out.add(new Rule());
                out.add(new Line("Next visitor").brief("Next").icon("minecraft:clock").value(demo ? v.next() : io.muzy.sbc.garden.visitor.VisitorTimer.shown(v.next()), v.next().contains("Full") ? Modern.BAD : Modern.AQUA));
            }
            return out;
        }
    };

    // --- composter ---

    public static final HudElement COMPOSTER = new GardenCard("garden_composter", "Composter", Zone.TOP_LEFT, 33, "garden_composter") {
        private final List<String[]> demoLines = List.of(new String[]{"Organic Matter", "38.4k"}, new String[]{"Fuel", "45.8k"}, new String[]{"Time Left", "1h 52m"}, new String[]{"Stored Compost", "26"});

        private List<String[]> read() {
            List<String[]> out = new ArrayList<>();
            for (String l : GameState.tab) {
                Matcher m = COMPOSTER_LINE.matcher(l);
                if (m.matches()) out.add(new String[]{m.group(1), m.group(2).trim()});
            }
            return out;
        }

        private List<String[]> lines() {
            return demo ? demoLines : read();
        }

        private boolean low(String value) {
            Matcher p = PCT.matcher(value);
            return p.find() && Double.parseDouble(p.group(1)) < 10;
        }

        private List<Row> rowsOf(List<String[]> lines) {
            List<Row> out = new ArrayList<>();
            for (String[] r : lines) out.add(new Row(r[0], r[1], low(r[1]) ? Theme.current().bad() : Theme.current().text()));
            return out;
        }

        @Override
        public boolean visible() {
            return garden() && !read().isEmpty();
        }

        @Override
        public List<Row> rows() {
            return rowsOf(read());
        }

        @Override
        public List<Row> demo() {
            return rowsOf(demoLines);
        }

        @Override
        protected Chip chip() {
            for (String[] r : lines()) if (r[0].equals("Time Left")) return none(r[1]) ? new Chip("INACTIVE", Modern.WARN) : new Chip("ACTIVE", Modern.GOOD);
            return null;
        }

        @Override
        protected List<Block> blocks() {
            // what you come here for on top, what keeps it running under the line
            List<Block> out = new ArrayList<>(), fed = new ArrayList<>();
            for (String[] r : lines()) {
                if (!shown(r[0])) continue;
                boolean low = low(r[1]);
                String icon;
                int color, bar;
                switch (r[0]) {
                    case "Organic Matter" -> {
                        icon = "minecraft:wheat";
                        color = Modern.INK;
                        bar = 0xFFB5D65F;
                    }
                    case "Fuel" -> {
                        icon = "minecraft:coal";
                        color = Modern.INK;
                        bar = Modern.WARN;
                    }
                    case "Time Left" -> {
                        icon = "minecraft:clock";
                        color = bar = none(r[1]) ? Modern.DIM : Modern.AQUA;
                    }
                    default -> {
                        icon = "minecraft:bone_meal";
                        color = bar = r[1].matches("0+") ? Modern.DIM : Modern.GOOD;
                    }
                }
                // whatever form hypixel writes it in, "x/y" and "n%" become meters with both numbers
                RowText.Shaped s = RowText.shape(r[0], r[1]);
                Line row = new Line(r[0].equals("Time Left") ? "Time left" : s.label()).value(s.value(), low ? Modern.BAD : color);
                row.brief(r[0].equals("Organic Matter") ? "Matter" : r[0].equals("Stored Compost") ? "Compost" : null);
                // labels say what the icons say, and 4 values fit in 2 rows without them
                if (!compact()) row.icon(icon);
                if (s.progress() >= 0) row.meter(s.progress(), low ? Modern.BAD : bar);
                if (low) row.chip("LOW", Modern.BAD);
                (r[0].equals("Time Left") || r[0].equals("Stored Compost") ? out : fed).add(row);
            }
            if (!out.isEmpty() && !fed.isEmpty() && !compact()) out.add(new Rule());
            out.addAll(fed);
            // ComposterDisplay: when it runs out with your upgrades
            String empty = demo ? "§b5h 20m" : io.muzy.sbc.garden.composter.Composter.emptyText();
            if (!empty.isEmpty() && shown("Empty in")) out.add(new Line("Empty in").value(empty));
            return out;
        }
    };

    // --- jacob's contest ---

    public static final HudElement CONTEST = new GardenCard("garden_contest", "Jacob's contest", Zone.TOP_LEFT, 34, "garden_contest") {
        private final List<String[]> demoLines = List.of(new String[]{"Ends in", "15m 14s"}, new String[]{"Cocoa Beans", "", ""}, new String[]{"Nether Wart", "", ""}, new String[]{"Sunflower", "", "marked"});
        private final Map<CropType, Integer> demoRates = Map.of(CropType.COCOA_BEANS, 41, CropType.NETHER_WART, 62);

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("minecraft:golden_hoe");
        }

        private List<String[]> read() {
            List<String[]> out = new ArrayList<>();
            for (String l : GameState.scoreboard) {
                String t = l.trim();
                Matcher m = CONTEST_SB.matcher(t);
                if (m.matches()) out.add(new String[]{m.group(1), m.group(2)});
                m = CONTEST_LEFT.matcher(t);
                if (m.matches()) out.add(new String[]{"Ends in", m.group(1)});
            }
            // tab widget: "Jacob's Contest: 1m 4s left", then ACTIVE and the 3 crops under it
            List<String> tab = GameState.tab;
            for (int i = 0; i < tab.size(); i++) {
                String t = tab.get(i).trim();
                if (!t.startsWith("Jacob's Contest")) continue;
                Matcher m = CONTEST_LEFT.matcher(t);
                if (m.matches()) out.add(new String[]{"Ends in", m.group(1)});
                else if (t.length() > 16) out.add(new String[]{"Contest", t.substring(16).replaceFirst("^:\\s*", "").trim()});
                for (int j = i + 1; j < tab.size() && j < i + 6; j++) {
                    String c = tab.get(j).replaceAll("[\\uE000-\\uF8FF]", "").replace("○", "").trim();
                    if (c.isEmpty()) break;
                    if (c.equalsIgnoreCase("ACTIVE") || c.equalsIgnoreCase("INACTIVE")) continue;
                    // hypixel marks one crop with its fortune icon instead of a circle
                    out.add(new String[]{c, "", tab.get(j).matches(".*[\\uE000-\\uF8FF].*") ? "marked" : ""});
                }
                break;
            }
            return out;
        }

        private List<String[]> lines() {
            return demo ? demoLines : read();
        }

        private List<Row> rowsOf(List<String[]> lines) {
            List<Row> out = new ArrayList<>();
            for (String[] r : lines) out.add(new Row(r[0], r[1], Theme.current().accent()));
            return out;
        }

        @Override
        public boolean visible() {
            return garden() && !read().isEmpty();
        }

        @Override
        public List<Row> rows() {
            return rowsOf(read());
        }

        @Override
        public List<Row> demo() {
            return rowsOf(demoLines);
        }

        private String timeOf(List<String[]> lines, String key) {
            for (String[] r : lines) {
                RowText.Shaped s = RowText.shape(r[0], r[1]);
                if (s.label().equalsIgnoreCase(key)) return s.value();
            }
            return "";
        }

        // time left when only the sidebar crop lines say it
        private String sidebarTime(List<String[]> lines) {
            for (String[] r : lines) if (!r[1].isEmpty() && CropType.getByNameOrNull(r[0]) != null) return r[1];
            return "";
        }

        @Override
        protected Chip chip() {
            List<String[]> lines = lines();
            if (!timeOf(lines, "Ends in").isEmpty() || !sidebarTime(lines).isEmpty()) return new Chip("ACTIVE", Modern.GOOD);
            return timeOf(lines, "Starts In").isEmpty() ? null : new Chip("UPCOMING", Modern.AQUA);
        }

        @Override
        protected List<Block> blocks() {
            List<String[]> lines = lines();
            CropType farmed = demo ? CropType.COCOA_BEANS : GardenMilestones.getCurrentlyFarmedCrop();
            List<Block> out = new ArrayList<>();
            // sidebar and tab both list the crops, show each once
            Map<String, Boolean> crops = new LinkedHashMap<>();
            List<Block> rest = new ArrayList<>();
            for (String[] r : lines) {
                RowText.Shaped s = RowText.shape(r[0], r[1]);
                if (s.label().equalsIgnoreCase("Ends in") || s.label().equalsIgnoreCase("Starts In")) continue;
                if (CropType.getByNameOrNull(r[0]) != null) crops.merge(r[0], r.length > 2 && !r[2].isEmpty(), Boolean::logicalOr);
                else if (shown(r[0])) rest.add(new Line(s.label()).value(s.value(), accent()));
            }
            String ends = timeOf(lines, "Ends in"), sidebar = ends.isEmpty() ? sidebarTime(lines) : "";
            for (Map.Entry<String, Boolean> e : crops.entrySet()) {
                if (!shown(e.getKey())) continue;
                CropType crop = CropType.getByNameOrNull(e.getKey());
                boolean boosted = e.getValue();
                Line row = new Line(e.getKey()).icon(crop.icon()).labelColor(boosted ? Modern.GOLD : Modern.INK).pairs();
                if (crop == farmed) row.chip(compact() ? "FARM" : "FARMING", Modern.GOOD);
                if (boosted) row.chip(compact() ? "BOOST" : "BOOSTED", Modern.GOLD);
                // last crops/s you hit on it, thats what decides a contest
                Integer rate = demo ? demoRates.get(crop) : GardenCropSpeed.getSpeed(crop);
                if (rate != null && rate > 0) row.value("§f" + rate + (compact() ? " §7C/s" : " §7crops/s"));
                out.add(row);
            }
            out.addAll(rest);
            String left = ends.isEmpty() ? sidebar : ends, starts = timeOf(lines, "Starts In");
            if (!left.isEmpty() && shown("Ends in")) {
                if (!out.isEmpty() && !compact()) out.add(new Rule());
                Long ms = HoppityEnv.duration(left);
                Line row = new Line("Ends in").icon("minecraft:clock");
                if (ms != null) {
                    double p = Math.min(1, (double) ms / CONTEST_MS);
                    row.value("§b" + left + "  §7" + RowText.percent(p)).meter(p, accent());
                } else row.value(left, Modern.AQUA);
                out.add(row);
            } else if (!starts.isEmpty() && shown("Starts In: " + starts)) {
                if (!out.isEmpty() && !compact()) out.add(new Rule());
                out.add(new Line("Starts in").brief("Starts").icon("minecraft:clock").value(starts, Modern.AQUA));
            }
            return out;
        }
    };

    // --- farming speed ---

    // where your speed sits: middle tick = what the crop wants, left too slow, right too fast. thin in a compact card
    private record SpeedGauge(int want, int have, int color, boolean thin) implements Block {
        @Override
        public int height(int rowH) {
            return thin ? 5 : 8;
        }

        @Override
        public int width(Ui ui) {
            return 0;
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
            int gy = y + 1;
            Modern.meter(ui, x, gy, w, thin ? 2 : 4, want <= 0 ? 0 : Math.min(1, have / (2.0 * want)), color);
            int mid = x + w / 2;
            ui.rect(mid, gy - 1, 1, thin ? 4 : 6, Modern.INK);
        }
    }

    public static final HudElement SPEED_EL = new GardenCard("garden_speed", "Farming speed", Zone.TOP_LEFT, 35, "garden_speed") {
        private String crop() {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return "";
            return heldCrop(GameState.strip(mc.player.getMainHandItem().getHoverName().getString()));
        }

        @Override
        public boolean visible() {
            return garden() && !crop().isEmpty();
        }

        private static String title(String crop) {
            return Character.toUpperCase(crop.charAt(0)) + crop.substring(1);
        }

        private List<Row> show(String name, int want, int have, List<String[]> fortunes) {
            boolean ok = Math.abs(have - want) <= 2;
            int color = color(want, have);
            List<Row> out = new ArrayList<>();
            out.add(new Row(name, String.valueOf(want), Theme.current().accent()));
            out.add(Row.meter("Now", have + (ok ? "  ✔" : have > want ? "  too fast" : "  too slow"), color, near(want, have), color));
            for (String[] f : fortunes) out.add(new Row(f[0], f[1], Theme.current().accent()));
            return out;
        }

        private List<String[]> fortunes() {
            if (demo) return List.of(new String[]{"Farming Fortune", "755"}, new String[]{"Wheat Fortune", "120"});
            List<String[]> out = new ArrayList<>();
            for (String l : GameState.tab) {
                Matcher m = FORTUNE_LINE.matcher(l);
                if (m.matches()) out.add(new String[]{m.group(1).trim(), noGlyphs(m.group(2))});
            }
            return out;
        }

        private double near(int want, int have) {
            return want <= 0 || have <= 0 ? 0 : (double) Math.min(want, have) / Math.max(want, have);
        }

        private int color(int want, int have) {
            return Math.abs(have - want) <= 2 ? Theme.current().good() : near(want, have) > 0.8 ? 0xFFFFB454 : Theme.current().bad();
        }

        @Override
        public List<Row> rows() {
            String crop = crop();
            if (crop.isEmpty()) return List.of();
            return show(title(crop) + " best speed", wantedSpeed(crop), (int) Vitals.stat("Speed"), fortunes());
        }

        @Override
        public List<Row> demo() {
            return show("Wheat best speed", 93, 112, fortunes());
        }

        private String shownCrop() {
            return demo ? "wheat" : crop();
        }

        private int have() {
            return demo ? 112 : (int) Vitals.stat("Speed");
        }

        @Override
        protected Chip chip() {
            String crop = shownCrop();
            int have = have(), want = wantedSpeed(crop);
            if (crop.isEmpty() || have <= 0 || want <= 0) return null;
            String state = Math.abs(have - want) <= 2 ? "PERFECT" : have > want ? "TOO FAST" : "TOO SLOW";
            return new Chip(state, Math.abs(have - want) <= 2 ? Modern.GOOD : near(want, have) > 0.8 ? Modern.WARN : Modern.BAD);
        }

        // less room for the title, so the icon and gauge say whose speed it is
        @Override
        public String heading() {
            return compact() ? "Speed" : super.heading();
        }

        // in a compact card the band says how far off you are so the speed itself shares a row with the wanted one
        @Override
        protected String bandValue() {
            String crop = shownCrop();
            int have = have(), want = wantedSpeed(crop);
            return compact() && !crop.isEmpty() && shown("Now") && have > 0 && want > 0 ? "§7" + RowText.percent((double) have / want) : "";
        }

        private static String cropIcon(String crop) {
            CropType type = CropType.getByNameOrNull(crop);
            if (type != null) return type.icon();
            return switch (crop) {
                case "nether wart", "warts" -> CropType.NETHER_WART.icon();
                case "cocoa" -> CropType.COCOA_BEANS.icon();
                case "sugar cane" -> CropType.SUGAR_CANE.icon();
                case "melon" -> CropType.MELON.icon();
                default -> CropType.MUSHROOM.icon();
            };
        }

        @Override
        protected List<Block> blocks() {
            String crop = shownCrop();
            List<Block> out = new ArrayList<>();
            if (crop.isEmpty()) return out;
            int want = wantedSpeed(crop), have = have();
            String best = title(crop) + " best speed";
            // labels say what the icons say, values fit in 2 rows without them
            boolean icons = !compact();
            if (shown(best)) out.add(new Line(title(crop)).icon(icons ? LegacyItems.stack(cropIcon(crop)) : null).labelColor(Modern.INK).value("§7best §b" + (want > 0 ? String.valueOf(want) : "-")));
            if (shown("Now")) {
                if (have > 0 && want > 0) {
                    int diff = have - want;
                    int color = Math.abs(diff) <= 2 ? Modern.GOOD : near(want, have) > 0.8 ? Modern.WARN : Modern.BAD;
                    String off = (diff >= 0 ? "+" : "") + diff;
                    out.add(new Line("Now").icon(icons ? LegacyItems.stack("minecraft:sugar") : null).value(have + "  §7" + off + (icons ? "  " + RowText.percent((double) have / want) : ""), color));
                    out.add(new SpeedGauge(want, have, color, !icons));
                } else out.add(new Line("Now").icon(icons ? LegacyItems.stack("minecraft:sugar") : null).value("-", Modern.DIM));
            }
            for (String[] f : fortunes()) if (shown(f[0])) out.add(new Line(f[0]).icon(icons ? LegacyItems.stack(cropIcon(crop)) : null).value(f[1], Modern.GOLD).brief(f[0].equals("Farming Fortune") ? "FF" : FarmingCard.briefFortune(f[0])));
            return out;
        }
    };

    public static void register() {
        java.util.function.BooleanSupplier g = GardenExtras::garden;
        Feature pests = Features.add(new Feature("garden_pests", "Garden", "Pests", "Pests alive out of the most there can be, a map of the infested plots (also as waypoints), how many are on your plot, spawn alerts, the spawn cooldown, spray, repellent, bonus fortune and your pest traps.", "").when(g));
        Features.add(new Feature("garden_visitors", "Garden", "Visitors", "Who is waiting in their rarity's colour, what each asks for, and when the next visitor comes, also how much sooner while you farm and an estimate of the sixth.", "SkyHanni: Visitor Timer").when(g)
                .option(io.muzy.sbc.feature.Option.bool("garden_visitors.sixth", "Sixth visitor estimate", "Estimate when the sixth visitor in the queue arrives, which Hypixel does not say.", true))
                .option(io.muzy.sbc.feature.Option.bool("garden_visitors.sixth_warning", "Sixth visitor warning", "A title and a sound when the sixth visitor should have come.", true))
                .option(io.muzy.sbc.feature.Option.bool("garden_visitors.new_ping", "New visitor ping", "A sound when a new visitor is less than 10 seconds away.", false)));
        Features.add(new Feature("garden_composter", "Garden", "Composter card", "Time left and stored compost, then organic matter and fuel; a warning when it runs low.", "").when(g).tick(GardenExtras::tick));
        Features.add(new Feature("garden_contest", "Garden", "Jacob's contest", "The contest's crops with your last measured crops per second, the crop you farm and the boosted crop picked out, and the time left or until it starts.", "").when(g));
        Features.add(new Feature("garden_speed", "Garden", "Optimal farming speed", "The speed the held tool's crop wants (93 / 155 / 233 / 328 / 464) against your current speed on a gauge, with your farming fortune.", "").when(g)
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_wheat", "Wheat speed", "", 93, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_carrot", "Carrot speed", "", 93, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_potato", "Potato speed", "", 93, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_wart", "Nether Wart speed", "", 93, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_pumpkin", "Pumpkin speed", "", 155, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_melon", "Melon speed", "", 155, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_cocoa", "Cocoa Beans speed", "", 155, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_cane", "Sugar Cane speed", "", 328, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_cactus", "Cactus speed", "", 464, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_mushroom", "Mushroom speed", "", 233, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_sunflower", "Sunflower and Moonflower speed", "", 328, 1, 500))
                .option(io.muzy.sbc.feature.Option.integer("garden_speed.speed_rose", "Wild Rose speed", "", 328, 1, 500)));
        ChatBus.register(ChatBus.OBSERVE + 14, "gardenpests", pests, GardenExtras::onChat);
        Waypoints.provider("garden_pests", () -> {
            List<Waypoints.Point> out = new ArrayList<>();
            if (!garden() || !Features.on("garden_pests")) return out;
            for (int p : infestedPlots()) {
                Vec3 c = plotCenter(p);
                if (c != null) out.add(new Waypoints.Point("Pest plot " + p, "GARDEN", c, 0xFF97C459, "pests", 6));
            }
            return out;
        });
        PestFinder.register();
        HudLayout.add(PESTS);
        HudLayout.add(VISITORS);
        HudLayout.add(COMPOSTER);
        HudLayout.add(CONTEST);
        HudLayout.add(SPEED_EL);
    }
}
