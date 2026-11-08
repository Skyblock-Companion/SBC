// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Toasts;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.Compact;
import dev.zmuzy.sbc.ui.hud.ElementStyle;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// dungeon helpers reading the run from chat + tab: copilot line saying whats next, splits per phase, death +
// milestone counters, secret chime, low health warning
public final class DungeonExtras {
    // chat (colors stripped)
    static final Pattern MORT = Pattern.compile("^\\[NPC\\] Mort: Here, I found this map when I first entered the dungeon\\.$");
    static final Pattern WITHER_DOOR = Pattern.compile("^(\\S+) opened a WITHER door!$");
    static final Pattern BLOOD_DOOR = Pattern.compile("^The BLOOD DOOR has been opened!$");
    static final Pattern WATCHER_DONE = Pattern.compile("^\\[BOSS\\] The Watcher: (That will be enough for now\\.|You have proven yourself\\. You may pass\\.)$");
    static final Pattern BOSS_START = Pattern.compile("^\\[BOSS\\] ([A-Za-z' ]+): (.+)$");
    static final Pattern TERMINAL = Pattern.compile("^(\\S+) (activated a (?:lever|terminal)|completed a device)! \\((\\d+)/(\\d+)\\)$");
    static final Pattern DEATH = Pattern.compile("^\\u2620 (.+?) (?:was killed by|were killed by|disconnected|was crushed|were crushed|suffocated|fell|died|burnt|drowned|was|were).*became a ghost\\.$");
    static final Pattern MILESTONE = Pattern.compile("^(Mage|Archer|Berserk|Tank|Healer) Milestone (.): You have .*! (\\d+)s$");
    static final Pattern COMPLETE = Pattern.compile("^\\s*(Master Mode )?The Catacombs - Floor ([IVX]+|Entrance)\\s*$");
    static final Pattern SECRETS_TAB = Pattern.compile("^\\s*Secrets Found: (\\d+)\\s*$");
    // party finder lore
    static final Pattern MEMBER = Pattern.compile("^\\s*(\\S+): (Mage|Archer|Berserk|Tank|Healer) \\((\\d+)\\)$");
    static final Pattern CARRY = Pattern.compile("(?i)^Note: .*\\b(carry|carries|carried)\\b.*$");
    static final Pattern INELIGIBLE = Pattern.compile("^Requires a Class at Level (\\d+)!$");

    private static final String[] CLASSES = {"Mage", "Archer", "Berserk", "Tank", "Healer"};
    private static final int[] CLASS_COLORS = {0xFF55AAFF, 0xFF55FF55, 0xFFFF5555, 0xFF999999, 0xFFFF55FF};

    // from skyhanni DungeonCopilot countdownPattern + keyPatternsList without colors
    static final Pattern COUNTDOWN = Pattern.compile("^.* has started the dungeon countdown\\. The dungeon will begin in 1 minute\\.$");
    static final Pattern KEY_PICKED = Pattern.compile("^A (?<key>Wither|Blood) Key was picked up!$");
    static final Pattern KEY_OBTAINED = Pattern.compile("^.* has obtained (?<key>Wither|Blood) Key!$");
    private static boolean searchForKey;

    private static final List<String[]> splits = new ArrayList<>();
    private static long runStart;
    private static String phase = "", boss = "";
    private static int witherDoors, deaths, secretStash = -1, terminalsDone, terminalsTotal;
    private static final Map<String, String> milestones = new LinkedHashMap<>();
    private static long lastLowHp, lastChime;
    private DungeonExtras() {}

    private static boolean inDungeon() {
        return "CATACOMBS".equals(GameState.island) || dev.zmuzy.sbc.SbcMod.DEV;
    }

    private static String elapsed(long ms) {
        long s = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    public static void reset() {
        splits.clear();
        runStart = 0;
        phase = "";
        boss = "";
        witherDoors = deaths = terminalsDone = terminalsTotal = 0;
        secretStash = -1;
        milestones.clear();
    }

    // for tests
    public static int splitCount() {
        return splits.size();
    }

    public static int deaths() {
        return deaths;
    }

    private static void split(String name) {
        if (runStart == 0) runStart = System.currentTimeMillis();
        long now = System.currentTimeMillis() - runStart;
        best(name, splits.isEmpty() ? now / 1000 : now / 1000 - seconds(splits.get(splits.size() - 1)[1]));
        splits.add(new String[]{name, elapsed(now)});
        if (name.equals("Complete")) bestTotal(now / 1000);
    }

    // from skyblocker DungeonSplitsWidget.FLOOR_SPLITS, the moments the boss rows dont have
    private static final Map<String, String> EXTRA_SPLITS = Map.of(
            "[BOSS] Bonzo: Oh I'm dead!", "Bonzo again",
            "[BOSS] Scarf: Did you forget? I was taught by the best! Let's dance.", "Scarf's minions",
            "[BOSS] The Professor: Oh? You found my Guardians' one weakness?", "Guardians",
            "[BOSS] The Professor: I see. You have forced me to use my ultimate technique.", "Professor",
            "[BOSS] Sadan: ENOUGH!", "Terracottas",
            "[BOSS] Sadan: You did it. I understand now, you have earned my respect.", "Giants",
            "The Core entrance is opening!", "Terminals");

    private static boolean extraSplit(String t) {
        String name = EXTRA_SPLITS.get(t);
        if (name == null || runStart == 0) return false;
        split(name);
        return false;
    }

    // from skyblocker updateBest: fastest time per stretch per floor, in sec
    private static void best(String name, long seconds) {
        if (Score.floor.isEmpty()) return;
        Map<String, Long> floor = dev.zmuzy.sbc.core.Store.I.dungeonBests.computeIfAbsent(Score.floor, k -> new java.util.HashMap<>());
        Long was = floor.get(name);
        if (was == null || seconds < was) {
            floor.put(name, seconds);
            dev.zmuzy.sbc.core.Store.markDirty();
        }
    }

    private static void bestTotal(long seconds) {
        best("Whole run", seconds);
    }

    private static Long bestOf(String floor, String name) {
        Map<String, Long> m = dev.zmuzy.sbc.core.Store.I.dungeonBests.get(floor);
        return m == null ? null : m.get(name);
    }

    // pure: one colorless chat line, true = changed the state (tests)
    public static boolean feed(String plain) {
        String t = plain.trim();
        Matcher m;
        if (MORT.matcher(t).matches()) {
            reset();
            runStart = System.currentTimeMillis();
            phase = "Clear rooms and find the blood door";
            searchForKey = true;
            return true;
        }
        if (COUNTDOWN.matcher(t).matches()) {
            phase = "Ready up";
            return true;
        }
        if ((m = KEY_PICKED.matcher(t)).matches() || (m = KEY_OBTAINED.matcher(t)).matches()) {
            phase = "Open " + m.group("key") + " Door";
            return true;
        }
        if (extraSplit(t)) return true;
        if ((m = WITHER_DOOR.matcher(t)).matches()) {
            witherDoors++;
            phase = "Wither doors: " + witherDoors + "  -  keep clearing";
            searchForKey = true;
            return true;
        }
        if (BLOOD_DOOR.matcher(t).matches()) {
            split("Blood open");
            phase = "Blood: wait for the Watcher, clear the camp";
            return true;
        }
        if (WATCHER_DONE.matcher(t).matches()) {
            split("Blood done");
            phase = "Kill the blood mobs, then enter the boss";
            return true;
        }
        if ((m = TERMINAL.matcher(t)).matches()) {
            terminalsDone = Integer.parseInt(m.group(3));
            terminalsTotal = Integer.parseInt(m.group(4));
            phase = "Goldor: " + terminalsDone + "/" + terminalsTotal + " terminals";
            return true;
        }
        if ((m = BOSS_START.matcher(t)).matches() && !m.group(1).equals("The Watcher")) {
            if (!m.group(1).equals(boss)) {
                boss = m.group(1);
                split("Boss " + boss);
                phase = "Boss: " + boss;
            }
            return true;
        }
        if ((m = DEATH.matcher(t)).matches()) {
            deaths++;
            return true;
        }
        if ((m = MILESTONE.matcher(t)).matches()) {
            milestones.put(m.group(1), m.group(2));
            return true;
        }
        if (COMPLETE.matcher(t).matches()) {
            split("Complete");
            phase = "Run complete";
            return true;
        }
        return false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        // from skyhanni DungeonCopilot: once youre ready all thats left is waiting
        var me = Minecraft.getInstance().player;
        if (inDungeon() && me != null && line.plain().trim().endsWith(" is now ready!") && line.plain().contains(me.getGameProfile().name())) phase = "Wait for the dungeon to start!";
        if (inDungeon()) feed(line.plain());
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        if (!inDungeon()) {
            if (!phase.isEmpty()) reset();
            return;
        }
        long now = System.currentTimeMillis();
        copilotTick(mc);
        if (mc.player != null && mc.player.tickCount % 5 == 0 && Features.on("dungeon_secret_chime")) {
            int secrets = -1;
            for (String l : GameState.tab) {
                Matcher m = SECRETS_TAB.matcher(l);
                if (m.matches()) secrets = Integer.parseInt(m.group(1));
            }
            if (secrets >= 0 && secretStash >= 0 && secrets > secretStash && now - lastChime > 300 && mc.player != null && !DungeonRun.inBoss() && !DungeonAlerts.chimedLately()) {
                lastChime = now;
                DungeonAlerts.chime();
            }
            if (secrets >= 0) secretStash = secrets;
        }
        if (Features.on("dungeon_lowhp") && mc.player != null) {
            double max = Math.max(1, Vitals.hpMax()), hp = Vitals.hpNow();
            if (hp / max < 0.3 && now - lastLowHp > 3000) {
                lastLowHp = now;
                Toasts.push("LOW HEALTH", Vitals.compact(hp) + " / " + Vitals.compact(max), 0xFFFF5555);
                mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.6f);
            }
        }
    }

    // from skyhanni DungeonCopilot: talk to mort at the start, pick up a key when one is around, defeat the boss
    // in the boss room
    private static void copilotTick(Minecraft mc) {
        if (!Features.on("dungeon_copilot") || mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0 || !"CATACOMBS".equals(GameState.island)) return;
        if (phase.isEmpty() && runStart == 0 && !DungeonRun.floor().isEmpty() && !DungeonRun.started()) phase = "Talk to Mort";
        if (DungeonRun.inBoss() && phase.startsWith("Kill the blood")) phase = "Defeat the boss! Good luck :)";
        if (!searchForKey) return;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof net.minecraft.world.entity.decoration.ArmorStand st) || !st.hasCustomName()) continue;
            String n = GameState.strip(st.getName().getString());
            if (n.equals("Wither Key") || n.equals("Blood Key")) {
                phase = "Pick up " + n;
                searchForKey = false;
                return;
            }
        }
    }

    // --- what the cards read ---

    // one run like the cards read it: live, or the editor sample
    private record Run(String phase, List<String[]> splits, long elapsedMs, int deaths, int doors, int secrets, int terminalsDone, int terminalsTotal, Map<String, String> milestones, String floor) {
        boolean complete() {
            return !splits.isEmpty() && splits.get(splits.size() - 1)[0].equals("Complete");
        }
    }

    private static final Run SAMPLE = new Run("Goldor: 5/7 terminals",
            List.of(new String[]{"Blood open", "4:12"}, new String[]{"Blood done", "5:40"}, new String[]{"Boss Maxor", "6:02"}, new String[]{"Boss Storm", "7:18"}, new String[]{"Boss Goldor", "8:44"}),
            571_000, 1, 4, 38, 5, 7, Map.of("Mage", "❷"), "F7");

    private static Run live() {
        return new Run(phase, splits, runStart == 0 ? 0 : System.currentTimeMillis() - runStart, deaths, witherDoors, secretStash, terminalsDone, terminalsTotal, milestones, Score.floor);
    }

    private static final int CLEAR_COLOR = 0xFF4FD8C4, BLOOD_COLOR = 0xFFFF5D73;

    private static int bossColor(String boss) {
        return switch (boss) {
            case "Maxor" -> 0xFFC77DFF;
            case "Storm" -> 0xFF5CD8FF;
            case "Goldor" -> 0xFFFFA94D;
            case "Necron" -> 0xFFFF6B6B;
            case "Wither King" -> 0xFF9D8CFF;
            default -> dev.zmuzy.sbc.ui.hud.Looks.colourFor(boss);
        };
    }

    private static int splitColor(String name) {
        if (name.equals("Blood open")) return CLEAR_COLOR;
        if (name.equals("Blood done")) return BLOOD_COLOR;
        if (name.equals("Complete")) return Modern.GOOD;
        return name.startsWith("Boss ") ? bossColor(name.substring(5)) : Modern.AQUA;
    }

    // a split marks when its phase starts so the stretch up to it belongs to the one before: clear, blood, walk
    // to the first boss, each boss. null next = the stretch still running
    private static int stretchColor(String prev, String next) {
        if (prev == null || "Blood open".equals(next)) return CLEAR_COLOR;
        if (prev.equals("Blood open")) return BLOOD_COLOR;
        return prev.startsWith("Boss ") ? bossColor(prev.substring(5)) : Modern.DIM;
    }

    private static int classColor(String cls) {
        int i = classIndex(cls);
        return i < 0 ? Modern.LABEL : CLASS_COLORS[i];
    }

    // hypixel numbers milestones with circled digits, plain ones read better this small
    private static String milestoneNumber(String symbol) {
        char c = symbol.isEmpty() ? 0 : symbol.charAt(0);
        return c >= '❶' && c <= '❾' ? String.valueOf(c - '❶' + 1) : symbol;
    }

    // "12:05" back to sec, splits keep times as text
    private static int seconds(String time) {
        int c = time.indexOf(':');
        return Integer.parseInt(time.substring(0, c)) * 60 + Integer.parseInt(time.substring(c + 1));
    }

    // copilot line: phase tag, what to do now, a number when theres one
    private record Line(String chip, int color, String text, String right, double progress) {}

    private static Line lineOf(Run r) {
        String p = r.phase();
        if (p.isEmpty()) return new Line("RUN", Modern.LABEL, "No run yet", "", -1);
        if (p.startsWith("Clear rooms")) return new Line("CLEAR", CLEAR_COLOR, "Find the blood door", "", -1);
        if (p.startsWith("Wither doors")) return new Line("CLEAR", CLEAR_COLOR, "Keep clearing", "§b" + r.doors() + " §7wither door" + (r.doors() == 1 ? "" : "s"), -1);
        if (p.startsWith("Blood:")) return new Line("BLOOD", BLOOD_COLOR, "Wait for Watcher, clear the camp", "", -1);
        if (p.startsWith("Kill the blood")) return new Line("BLOOD", BLOOD_COLOR, "Kill the mobs, then enter the boss", "", -1);
        if (p.startsWith("Goldor:")) {
            double done = r.terminalsTotal() > 0 ? (double) r.terminalsDone() / r.terminalsTotal() : 0;
            return new Line("GOLDOR", bossColor("Goldor"), "Terminals", Modern.fraction(String.valueOf(r.terminalsDone()), String.valueOf(r.terminalsTotal()), 'a', 'f') + "  §7" + RowText.percent(done), done);
        }
        if (p.startsWith("Boss:")) {
            String boss = p.substring(5).trim();
            return new Line("BOSS", bossColor(boss), boss, "", -1);
        }
        if (r.complete()) return new Line("DONE", Modern.GOOD, p, "§a" + r.splits().get(r.splits().size() - 1)[1], -1);
        return new Line("RUN", BLOOD_COLOR, p, "", -1);
    }

    // --- hud elements ---

    // copilot short form: same thing, fewer words
    private static Line briefLine(Line l) {
        return new Line(l.chip(), l.color(), l.text().replace("Wait for", "Await").replace(", then ", ", ").replace(" the ", " "), l.right().replace(" §7wither door", " §7door"), l.progress());
    }

    public static final HudElement COPILOT = new HudElement("dungeon_copilot", "Dungeon copilot", "Dungeon", Zone.TOP_LEFT, 27, "dungeon_copilot") {
        private Line line() {
            Line l = lineOf(demo ? SAMPLE : live());
            return compact() ? briefLine(l) : l;
        }

        @Override
        public boolean visible() {
            return inDungeon() && !phase.isEmpty();
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        public List<Row> rows() {
            return List.of(new Row(phase, "", Theme.current().accent()));
        }

        @Override
        public List<Row> demo() {
            return List.of(new Row(SAMPLE.phase(), "", Theme.current().accent()));
        }

        @Override
        public int width() {
            Line l = line();
            Ui ui = Ui.MEASURE;
            int w = 2 * pad(Theme.current(), style()) + 12 + ui.width(l.chip()) + 6 + 4 + ui.width(l.text()) + (l.right().isEmpty() ? 0 : ui.width(l.right()) + 8);
            return Math.min(236, Math.max(100, w));
        }

        @Override
        public int height() {
            return 5 + Math.min(rowH(Theme.current(), style()), Compact.ROW_H) + (line().progress() >= 0 ? Compact.METER_BLOCK : 0);
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            Theme t = Theme.current();
            ElementStyle st = style();
            Line l = line();
            int w = drawWidth(), h = height(), pad = pad(t, st), rh = Math.min(rowH(t, st), Compact.ROW_H), color = st.titleColor() != null ? st.titleColor() : l.color();
            t.panel(ui, x, y, w, h, st);
            Modern.edge(ui, x, y, h, color);
            if (visibleRows(st).isEmpty()) return;
            if (!icon().isEmpty()) ui.icon(icon(), x + 4, y + (rh + 5 - Modern.ICON) / 2, Modern.ICON_SCALE);
            int cy = y + 3, right = x + w - pad, cx = x + pad + 12;
            cx += Modern.chip(ui, cx, cy, l.chip(), color) + 4;
            int rw = l.right().isEmpty() ? 0 : ui.width(l.right());
            if (rw > 0) ui.textShadow(l.right(), right - rw, cy, ElementStyle.or(st.valueColor(), Modern.INK));
            ui.textShadow(ui.ellipsize(l.text(), right - cx - (rw > 0 ? rw + 6 : 0)), cx, cy, ElementStyle.or(st.textColor(), Modern.INK));
            if (l.progress() >= 0) Modern.meter(ui, x + pad, cy + rh, w - 2 * pad, Modern.METER, l.progress(), l.progress() >= 1 ? Modern.GOOD : color);
        }
    };

    public static final HudElement SPLITS = new HudElement("dungeon_splits", "Dungeon splits", "Dungeon", Zone.TOP_LEFT, 36, "dungeon_splits") {

        private record Tile(String value, String label, int color) {
            String shortLabel() {
                return label.equals("Wither doors") ? "Doors" : label;
            }
        }

        private Run run() {
            return demo ? SAMPLE : live();
        }

        @Override
        public boolean visible() {
            return inDungeon() && runStart != 0;
        }

        @Override
        public String headingValue() {
            Run r = run();
            Long pb = demo ? Long.valueOf(502) : bestOf(r.floor(), "Whole run");
            return pb == null ? r.floor() : r.floor() + "  §7PB §f" + elapsed(pb * 1000L);
        }

        @Override
        public List<Row> rows() {
            List<Row> out = new ArrayList<>();
            for (String[] s : splits) out.add(new Row(s[0], s[1], Theme.current().text()));
            out.add(new Row("Now", elapsed(System.currentTimeMillis() - runStart), Theme.current().accent()));
            if (deaths > 0) out.add(new Row("Deaths", String.valueOf(deaths), Theme.current().bad()));
            if (witherDoors > 0) out.add(new Row("Wither doors", String.valueOf(witherDoors), 0));
            if (secretStash >= 0) out.add(new Row("Secrets", String.valueOf(secretStash), 0));
            if (terminalsTotal > 0) out.add(new Row("Terminals", terminalsDone + "/" + terminalsTotal, 0));
            for (Map.Entry<String, String> e : milestones.entrySet()) out.add(new Row(e.getKey() + " milestone", e.getValue(), Theme.current().dim()));
            return out;
        }

        @Override
        public List<Row> demo() {
            List<Row> out = new ArrayList<>();
            for (String[] s : SAMPLE.splits()) out.add(new Row(s[0], s[1], 0));
            out.add(new Row("Now", elapsed(SAMPLE.elapsedMs()), Theme.current().accent()));
            out.add(new Row("Deaths", String.valueOf(SAMPLE.deaths()), Theme.current().bad()));
            out.add(new Row("Wither doors", String.valueOf(SAMPLE.doors()), 0));
            out.add(new Row("Secrets", String.valueOf(SAMPLE.secrets()), 0));
            out.add(new Row("Terminals", SAMPLE.terminalsDone() + "/" + SAMPLE.terminalsTotal(), 0));
            for (Map.Entry<String, String> e : SAMPLE.milestones().entrySet()) out.add(new Row(e.getKey() + " milestone", e.getValue(), Theme.current().dim()));
            return out;
        }

        // counters along the bottom, each only when known. you can hide any
        private List<Tile> tiles(Run r, ElementStyle st) {
            List<Tile> out = new ArrayList<>();
            if (!st.isHidden("Deaths")) out.add(new Tile(String.valueOf(r.deaths()), "Deaths", r.deaths() > 0 ? Modern.BAD : Modern.DIM));
            if (!st.isHidden("Wither doors")) out.add(new Tile(String.valueOf(r.doors()), "Wither doors", r.doors() > 0 ? Modern.INK : Modern.DIM));
            if (r.secrets() >= 0 && !st.isHidden("Secrets")) out.add(new Tile(String.valueOf(r.secrets()), "Secrets", Modern.AQUA));
            if (r.terminalsTotal() > 0 && !st.isHidden("Terminals")) {
                double done = (double) r.terminalsDone() / r.terminalsTotal();
                out.add(new Tile(Modern.fraction(String.valueOf(r.terminalsDone()), String.valueOf(r.terminalsTotal()), done >= 1 ? 'a' : 'f', 'f') + " §7" + RowText.percent(done), "Terminals", Modern.INK));
            }
            return out;
        }

        // milestone chips in lines that fit next to their label
        private List<List<String[]>> chipLines(Run r, ElementStyle st, int room) {
            List<List<String[]>> lines = new ArrayList<>();
            List<String[]> line = new ArrayList<>();
            int used = 0;
            for (Map.Entry<String, String> e : r.milestones().entrySet()) {
                if (st.isHidden(e.getKey() + " milestone")) continue;
                String text = e.getKey() + " " + milestoneNumber(e.getValue());
                int cw = Ui.MEASURE.width(text) + 6 + 3;
                if (used + cw > room && !line.isEmpty()) {
                    lines.add(line);
                    line = new ArrayList<>();
                    used = 0;
                }
                line.add(new String[]{text, e.getKey()});
                used += cw;
            }
            if (!line.isEmpty()) lines.add(line);
            return lines;
        }

        private int visibleSplits(Run r, ElementStyle st) {
            int n = 0;
            for (String[] s : r.splits()) if (!st.isHidden(s[0])) n++;
            return n;
        }

        private int prevAt(Run r, int i) {
            return i == 0 ? 0 : seconds(r.splits().get(i - 1)[1]);
        }

        // short form = counter label + value on one row
        private int tileWidth(Ui ui, Tile tile) {
            return ui.width(tile.shortLabel()) + 4 + ui.width(tile.value());
        }

        // short form counters in rows that fit, nothing reordered
        private List<List<Tile>> tileLines(Ui ui, List<Tile> tiles, int room) {
            List<List<Tile>> lines = new ArrayList<>();
            List<Tile> line = new ArrayList<>();
            int used = 0;
            for (Tile tile : tiles) {
                int tw = tileWidth(ui, tile);
                if (!line.isEmpty() && used + Compact.GAP + tw > room) {
                    lines.add(line);
                    line = new ArrayList<>();
                    used = 0;
                }
                used += (line.isEmpty() ? 0 : Compact.GAP) + tw;
                line.add(tile);
            }
            if (!line.isEmpty()) lines.add(line);
            return lines;
        }

        private int briefMilestoneRoom(Theme t, ElementStyle st) {
            return Compact.WIDE - 2 * pad(t, st) - Ui.MEASURE.width("Milestones") - 6;
        }

        private int briefWidth() {
            Theme t = Theme.current();
            Run r = run();
            ElementStyle st = style();
            Ui ui = Ui.MEASURE;
            int labelW = ui.width("Now"), timeW = ui.width(elapsed(r.elapsedMs())), pads = 2 * pad(t, st);
            for (String[] s : r.splits()) {
                labelW = Math.max(labelW, ui.width(s[0]));
                timeW = Math.max(timeW, ui.width(s[1]));
            }
            int w = pads + 5 + labelW + 8 + 30 + 8 + timeW;
            if (titleShown(st)) w = Math.max(w, 16 + ui.width(heading()) + 6 + (headingValue().isEmpty() ? 0 : ui.width(headingValue()) + 8));
            for (List<Tile> line : tileLines(ui, tiles(r, st), Compact.WIDE - pads)) {
                int used = -Compact.GAP;
                for (Tile tile : line) used += Compact.GAP + tileWidth(ui, tile);
                w = Math.max(w, pads + used);
            }
            for (List<String[]> line : chipLines(r, st, briefMilestoneRoom(t, st))) {
                int used = ui.width("Milestones") + 6 - 3;
                for (String[] chip : line) used += ui.width(chip[0]) + 6 + 3;
                w = Math.max(w, pads + used);
            }
            return Math.min(236, Math.max(80, w));
        }

        private int briefHeight() {
            Theme t = Theme.current();
            ElementStyle st = style();
            Run r = run();
            int rh = Math.min(rowH(t, st), Compact.ROW_H);
            int h = (titleShown(st) ? Modern.BAND + 3 : 5) + Compact.THICK_BLOCK + visibleSplits(r, st) * rh;
            if (!r.complete() && !st.isHidden("Now")) h += rh;
            List<List<Tile>> lines = tileLines(Ui.MEASURE, tiles(r, st), Compact.WIDE - 2 * pad(t, st));
            int chips = chipLines(r, st, briefMilestoneRoom(t, st)).size();
            if (!lines.isEmpty() || chips > 0) h += Compact.RULE_BLOCK;
            return h + (lines.size() + chips) * rh;
        }

        // under the splits in the short form: every counter with what it needs + a share of the leftover
        private void drawBriefBottom(Ui ui, Theme t, ElementStyle st, Run r, int cx, int cy, int cw, int rh, int labelColor) {
            List<List<Tile>> lines = tileLines(ui, tiles(r, st), Compact.WIDE - 2 * pad(t, st));
            List<List<String[]>> chips = chipLines(r, st, briefMilestoneRoom(t, st));
            if (!lines.isEmpty() || !chips.isEmpty()) cy += Compact.RULE_BLOCK;
            for (List<Tile> line : lines) {
                int used = -Compact.GAP;
                for (Tile tile : line) used += Compact.GAP + tileWidth(ui, tile);
                int spare = Math.max(0, cw - used) / line.size(), tx = cx;
                for (int i = 0; i < line.size(); i++) {
                    Tile tile = line.get(i);
                    int tw = i == line.size() - 1 ? cx + cw - tx : tileWidth(ui, tile) + spare;
                    ui.textShadow(tile.shortLabel(), tx, cy, Modern.DIM);
                    ui.textShadow(tile.value(), tx + tw - ui.width(tile.value()), cy, tile.color());
                    if (i > 0) ui.rect(tx - Compact.GAP / 2 - 1, cy, 1, 8, 0x30FFFFFF);
                    tx += tw + Compact.GAP;
                }
                cy += rh;
            }
            if (chips.isEmpty()) return;
            ui.textShadow(r.milestones().size() == 1 ? "Milestone" : "Milestones", cx, cy, labelColor);
            for (List<String[]> line : chips) {
                int used = -3;
                for (String[] chip : line) used += ui.width(chip[0]) + 6 + 3;
                int mx = cx + cw - used;
                for (String[] chip : line) mx += Modern.chip(ui, mx, cy, chip[0], classColor(chip[1])) + 3;
                cy += rh;
            }
        }

        @Override
        public int width() {
            return briefWidth();
        }

        @Override
        public int height() {
            return briefHeight();
        }

        private void splitRow(Ui ui, String label, String delta, String time, int swatch, int labelColor, int deltaColor, int timeColor, int x, int y, int w, int timeW) {
            ui.rect(x, y + 1, 2, 6, swatch | 0xFF000000);
            ui.textShadow(time, x + w - ui.width(time), y, timeColor);
            int deltaRight = x + w - timeW - 8, deltaW = ui.width(delta);
            ui.textShadow(delta, deltaRight - deltaW, y, deltaColor);
            ui.textShadow(ui.ellipsize(label, deltaRight - deltaW - 4 - (x + 5)), x + 5, y, labelColor);
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            Theme t = Theme.current();
            ElementStyle st = style();
            Run r = run();
            int w = drawWidth(), h = height(), pad = pad(t, st), rh = Math.min(rowH(t, st), Compact.ROW_H), accent = st.titleColor() != null ? st.titleColor() : accent();
            int labelColor = ElementStyle.or(st.textColor(), Modern.LABEL), valueColor = ElementStyle.or(st.valueColor(), Modern.INK);
            t.panel(ui, x, y, w, h, st);
            int cx = x + pad, cw = w - 2 * pad, cy;
            if (titleShown(st)) cy = y + Modern.band(ui, heading(), icon(), headingValue(), accent, x, y, w);
            else {
                Modern.edge(ui, x, y, h, accent);
                cy = y + 3;
            }
            boolean complete = r.complete();
            long nowSeconds = r.elapsedMs() / 1000;
            int last = r.splits().isEmpty() ? 0 : seconds(r.splits().get(r.splits().size() - 1)[1]);
            long total = Math.max(1, complete ? last : Math.max(last, nowSeconds));

            // where the time went, each stretch in its row color
            int sx = cx;
            String before = null;
            for (String[] s : r.splits()) {
                int ex = cx + (int) (cw * Math.min(total, seconds(s[1])) / total);
                if (ex - sx > 1) Modern.meter(ui, sx, cy, ex - sx - (ex < cx + cw ? 1 : 0), Compact.THICK, 1, stretchColor(before, s[0]));
                sx = Math.max(sx, ex);
                before = s[0];
            }
            int running = stretchColor(before, null);
            if (!complete && sx < cx + cw) Modern.meter(ui, sx, cy, cx + cw - sx, Compact.THICK, 1, running);
            cy += Compact.THICK_BLOCK;

            int timeW = ui.width(elapsed(r.elapsedMs()));
            for (String[] s : r.splits()) timeW = Math.max(timeW, ui.width(s[1]));
            int prev = 0;
            for (int i = 0; i < r.splits().size(); i++) {
                String[] s = r.splits().get(i);
                int at = seconds(s[1]);
                // boss row = how long that boss took, others = how long to get here, the end just its time
                long until = i + 1 < r.splits().size() ? seconds(r.splits().get(i + 1)[1]) : nowSeconds;
                String delta = s[0].startsWith("Boss ") ? elapsed(Math.max(0, until - at) * 1000L) : s[0].equals("Complete") ? "" : "+" + elapsed((at - prev) * 1000L);
                prev = at;
                if (st.isHidden(s[0])) continue;
                int c = splitColor(s[0]);
                if (complete && s[0].equals("Complete")) {
                    ui.gradH(x + 1, cy - 1, w - 2, rh, Ui.alpha(Modern.GOOD, 0.32f), Ui.alpha(Modern.GOOD, 0.05f));
                    splitRow(ui, s[0], delta, s[1], c, Modern.lighten(Modern.GOOD, 0.3f), Modern.lighten(c, 0.25f), Modern.GOOD, cx, cy, cw, timeW);
                } else {
                    // from skyblocker DungeonSplitsWidget.buildRows: green when this stretch tied your best on this floor
                    Long pb = demo ? null : bestOf(r.floor(), s[0]);
                    int deltaColor = pb != null && !s[0].startsWith("Boss ") && at - prevAt(r, i) <= pb ? Modern.GOOD : Modern.lighten(c, 0.25f);
                    splitRow(ui, s[0], delta, s[1], c, labelColor, deltaColor, valueColor, cx, cy, cw, timeW);
                }
                cy += rh;
            }
            if (!complete && !st.isHidden("Now")) {
                ui.gradH(x + 1, cy - 1, w - 2, rh, Ui.alpha(accent | 0xFF000000, 0.34f), Ui.alpha(accent | 0xFF000000, 0.05f));
                String delta = "+" + elapsed(Math.max(0, r.elapsedMs() - last * 1000L));
                splitRow(ui, "Now", delta, elapsed(r.elapsedMs()), running, Modern.lighten(accent | 0xFF000000, 0.45f), Modern.lighten(accent | 0xFF000000, 0.25f), accent | 0xFF000000, cx, cy, cw, timeW);
                cy += rh;
            }
            drawBriefBottom(ui, t, st, r, cx, cy, cw, rh, labelColor);
        }
    };

    // --- slot overlays ---

    private static String screenTitle() {
        var s = Minecraft.getInstance().gui.screen();
        return s == null ? "" : GameState.strip(s.getTitle().getString()).trim();
    }

    private static List<String> lore(ItemStack st) {
        List<String> out = new ArrayList<>();
        var l = st.get(DataComponents.LORE);
        if (l != null) for (Component c : l.lines()) out.add(GameState.strip(c.getString()));
        return out;
    }

    private static int classIndex(String cls) {
        for (int i = 0; i < CLASSES.length; i++) if (CLASSES[i].equalsIgnoreCase(cls)) return i;
        return -1;
    }

    // spirit leap: class color behind each head + class letter. party finder: carry, ineligible + missing class marks
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        String title = screenTitle();
        var font = Minecraft.getInstance().font;
        if (LeapMenu.draw(g, slot, title)) return;
        if (Features.on("dungeon_finder") && title.startsWith("Party Finder")) {
            List<String> lore = lore(st);
            boolean carry = false, ineligible = false, party = false;
            boolean[] has = new boolean[CLASSES.length];
            for (String l : lore) {
                String t = l.trim();
                if (CARRY.matcher(t).matches()) carry = true;
                if (INELIGIBLE.matcher(t).matches()) ineligible = true;
                Matcher m = MEMBER.matcher(l);
                if (m.matches()) {
                    party = true;
                    int ci = classIndex(m.group(2));
                    if (ci >= 0) has[ci] = true;
                }
            }
            if (!party) return;
            if (ineligible) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x90000000);
            if (carry) g.text(font, "$", slot.x + 1, slot.y, 0xFFFF5555, true);
            StringBuilder missing = new StringBuilder();
            for (int i = 0; i < CLASSES.length; i++) if (!has[i]) missing.append(CLASSES[i].charAt(0));
            if (missing.length() > 0 && missing.length() < 5) {
                var p = g.pose();
                p.pushMatrix();
                p.translate(slot.x + 16.5f - font.width(missing.toString()) * 0.6f, slot.y + 10.5f);
                p.scale(0.6f, 0.6f);
                g.text(font, missing.toString(), 0, 0, 0xFFFFFF55, true);
                p.popMatrix();
            }
        }
    }

    public static void register() {
        java.util.function.BooleanSupplier dg = DungeonExtras::inDungeon;
        Feature copilot = Features.add(new Feature("dungeon_copilot", "Dungeons", "Dungeon copilot", "One line that says what comes next in the run: clear, blood, Watcher, boss, terminals.", "").when(dg));
        Features.add(new Feature("dungeon_splits", "Dungeons", "Splits, deaths and milestones", "Time at blood open, blood done, each boss phase and completion; deaths and class milestones.", "").when(dg));
        Features.add(new Feature("dungeon_secret_chime", "Dungeons", "Secret chime", "A chime when you click a chest, lever or wither essence, and when the secret count in the tab list goes up for anything else.", "").when(dg)
                .option(dev.zmuzy.sbc.feature.Option.text("dungeon_secret_chime.sound", "Sound", "The sound's id. Hear it with /sbc dungeonsound chime.", "block.note_block.chime"))
                .option(dev.zmuzy.sbc.feature.Option.number("dungeon_secret_chime.pitch", "Pitch", "The pitch of the sound.", 1.6, 0.5, 2, 0.05))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_secret_chime.mute_chest", "Mute the chest sound", "No chest opening sound in dungeons.", false))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_secret_chime.mute_lever", "Mute the lever sound", "No lever sound in dungeons.", false)));
        Features.add(new Feature("dungeon_lowhp", "Dungeons", "Low health warning", "A warning and a low tone when you drop under 30% health in a dungeon.", "").when(dg));
        Features.add(new Feature("dungeon_leap", "Dungeons", "Spirit Leap class colours", "In the Spirit Leap menu every head gets its class colour and letter, dead players stand out, and four keys can leap for you when you press them.", "").when(dg)
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_leap.show_level", "Class level", "The class level next to the letter.", false))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_leap.keys", "Leap keys", "Keys in Controls leap to the first to fourth player, ordered by class. Acts only when you press one.", false))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_leap.key_hints", "Show the keys", "The key's name on each head.", false))
                .option(dev.zmuzy.sbc.feature.Option.color("dungeon_leap.archer", "Archer colour", "The colour behind an Archer.", 0xFF55FF55))
                .option(dev.zmuzy.sbc.feature.Option.color("dungeon_leap.berserk", "Berserk colour", "The colour behind a Berserk.", 0xFFFF5555))
                .option(dev.zmuzy.sbc.feature.Option.color("dungeon_leap.healer", "Healer colour", "The colour behind a Healer.", 0xFFFF55FF))
                .option(dev.zmuzy.sbc.feature.Option.color("dungeon_leap.mage", "Mage colour", "The colour behind a Mage.", 0xFF55AAFF))
                .option(dev.zmuzy.sbc.feature.Option.color("dungeon_leap.tank", "Tank colour", "The colour behind a Tank.", 0xFF999999))
                .option(dev.zmuzy.sbc.feature.Option.color("dungeon_leap.dead", "Dead colour", "The colour behind a dead teammate.", 0xFF780000)));
        Features.add(new Feature("dungeon_finder", "Dungeons", "Party Finder marks", "In the Party Finder: $ on carries, dimmed parties you cannot join, and the letters of the classes a party is missing.", "")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV));
        Features.add(new Feature("dungeon_extras_tick", "Dungeons", "Dungeon run clock", "Reads the run from chat and the tab list for the copilot, splits, chime and warnings.", "").when(dg).tick(DungeonExtras::tick).alwaysOn());
        ChatBus.register(ChatBus.OBSERVE + 12, "dungeonextras", copilot, DungeonExtras::onChat);
        HudLayout.add(COPILOT);
        HudLayout.add(SPLITS);
    }

    static Ui ui() {
        return Ui.MEASURE;
    }
}
