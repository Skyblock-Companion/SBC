// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.areas;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Toasts;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small helpers that fit nowhere else: piggy bank, server restart, trevor the trapper, spiders den relics,
// fairy souls, npc daily sell limit, draconic sacrifices, time warps, potion effects
public final class MiscExtras {
    static final Pattern PIGGY_CRACK = Pattern.compile("^You died and your piggy bank cracked!$");
    static final Pattern PIGGY_BROKE = Pattern.compile("^You died, lost ([\\d,]+) coins and your piggy bank broke!$");
    static final Pattern SERVER_CLOSING = Pattern.compile("^Server closing: (\\d+):(\\d+).*$");
    static final Pattern TREVOR_AREA = Pattern.compile("^\\[NPC\\] Trevor: You can find your .+? animal near the (.+?)\\.$");
    static final Pattern TREVOR_HINT = Pattern.compile("^The target is around (\\d+) blocks (above|below), at a (\\d+) degrees angle!$");
    static final Pattern TREVOR_PELTS = Pattern.compile("^(?:Your mob died randomly, you are rewarded|Killing the animal rewarded you) (\\d+) pelts?\\.$");
    static final Pattern RELIC = Pattern.compile("^\\+([\\d,]+) Coins! \\((\\d+)/(\\d+) Relics\\)$");
    static final Pattern RELIC_DUP = Pattern.compile("^You've already found this relic!$");
    static final Pattern FAIRY = Pattern.compile("^SOUL! You found a Fairy Soul!$");
    static final Pattern FAIRY_DUP = Pattern.compile("^You have already found that Fairy Soul!$");
    static final Pattern SACRIFICE = Pattern.compile("^SACRIFICE! You turned (.+?) into (\\d+) Dragon Essence!$");
    static final Pattern SACRIFICE_BONUS = Pattern.compile("^BONUS LOOT! You also received (\\d+)x (.+?) from your sacrifice!$");
    static final Pattern TIME_WARP = Pattern.compile("^TIME WARP! You have successfully warped time for your (.+?)!$");
    static final Pattern GOD_POT = Pattern.compile("^You have a God Potion active! (.+)$");
    static final Pattern EFFECTS = Pattern.compile("^You have (\\d+) (?:active effects?|non-god effects?)\\..*$");
    static final Pattern KICK = Pattern.compile("^(?:You were kicked while joining that server!|(?:A kick|An exception) occurred in your connection, so you were put in the SkyBlock [Ll]obby!|There was a problem joining SkyBlock, try again in a moment!)$");
    static final Pattern CAKE_SOUL = Pattern.compile("^You found a Cake Soul!$");
    static final Pattern COORDS = Pattern.compile("^(.+?): x: (-?\\d+), y: (-?\\d+), z: (-?\\d+)(.*)$");
    // minions
    static final Pattern MINION_COINS = Pattern.compile("^You received ([\\d,]+) coins!$");
    static final Pattern MINION_UPGRADE = Pattern.compile("^You have upgraded your Minion to Tier (\\w+)$");
    static final Pattern MINION_PLACED = Pattern.compile("^You (placed|picked up) a minion!$");
    static final Pattern EYEDROPS = Pattern.compile("^You applied the eyedrops on the minion and ran out!$");
    static final Pattern NEED_ITEMS = Pattern.compile("^You need (\\d+) more (.+?)\\.$");

    // from skyhanni NpcDayLimitTracker.DAILY_LIMIT. the day turns at midnight gmt, which today() counts in
    public static final long NPC_DAILY_LIMIT = 500_000_000L;

    private static String trevorArea = "", trevorHint = "";
    private static long trevorAt, trevorNextAt;
    private static int pelts, relics, relicsTotal, fairySouls, sacrifices, essence;
    private static long minionStonks;
    private static final Map<String, Integer> bonus = new LinkedHashMap<>();
    private static int minionsPlaced;
    private MiscExtras() {}

    private static boolean sb() {
        return GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV;
    }

    public static void reset() {
        trevorArea = trevorHint = "";
        pelts = relics = relicsTotal = fairySouls = sacrifices = essence = 0;
        minionStonks = 0;
        bonus.clear();
        minionsPlaced = 0;
    }

    static long npcSold() { return (long) io.github.zmuzy.sbc.track.ProfitCards.npcSold(); }

    // the session panel only says it while the npc limit card doesnt, so the number is on screen once
    private static long npcShown() {
        return Features.on("npc_day_limit") ? 0 : npcSold();
    }

    // the trapper card only says the pelts while the tracker card doesnt, so the number is on screen once
    private static int peltsShown() {
        return io.github.zmuzy.sbc.track.ProfitCards.trevorTrackerUp() ? 0 : pelts;
    }

    // from skyhanni NpcDayLimitTracker warning, on the one count
    public static void npcWarning(double sold) {
        if (sold > NPC_DAILY_LIMIT * 0.9 && Features.on("misc_npc_limit")) alert("NPC sell limit", String.format(Locale.ROOT, "%,d of 500M sold today", (long) sold), 0xFFFFAA00, 0.9f);
    }
    static int pelts() { return pelts; }
    static long minionStonks() { return minionStonks; }

    private static void alert(String title, String body, int color, float pitch) {
        Toasts.push(title, body, color);
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.9f, pitch);
    }

    // pure: one colorless chat line. true = used
    public static boolean feed(String plain) {
        String t = plain.trim();
        Matcher m;
        if (PIGGY_CRACK.matcher(t).matches() || (m = PIGGY_BROKE.matcher(t)).matches()) {
            if (Features.on("misc_piggy")) alert("Piggy bank", t.contains("broke") ? "It broke - buy a new one before you lose more coins" : "It cracked - get a new one from the Bazaar", 0xFFFF5555, 0.7f);
            return true;
        }
        if ((m = TREVOR_AREA.matcher(t)).matches()) {
            trevorNextAt = System.currentTimeMillis() + 20_000L;
            trevorArea = m.group(1);
            trevorHint = "";
            trevorAt = System.currentTimeMillis();
            return true;
        }
        if ((m = TREVOR_HINT.matcher(t)).matches()) {
            trevorHint = m.group(1) + " blocks " + m.group(2) + ", " + m.group(3) + "°";
            trevorAt = System.currentTimeMillis();
            return true;
        }
        if ((m = TREVOR_PELTS.matcher(t)).matches()) {
            pelts += Integer.parseInt(m.group(1));
            trevorArea = trevorHint = "";
            return true;
        }
        if ((m = RELIC.matcher(t)).matches()) {
            relics = Integer.parseInt(m.group(2));
            relicsTotal = Integer.parseInt(m.group(3));
            return true;
        }
        if (RELIC_DUP.matcher(t).matches()) return true;
        if (FAIRY.matcher(t).matches()) {
            fairySouls++;
            return true;
        }
        if (FAIRY_DUP.matcher(t).matches()) return true;
        if ((m = SACRIFICE.matcher(t)).matches()) {
            sacrifices++;
            essence += Integer.parseInt(m.group(2));
            return true;
        }
        if ((m = SACRIFICE_BONUS.matcher(t)).matches()) {
            bonus.merge(m.group(2), Integer.parseInt(m.group(1)), Integer::sum);
            return true;
        }
        if ((m = TIME_WARP.matcher(t)).matches()) {
            Toasts.push("Time warp", "Warped time for your " + m.group(1), 0xFFFFD866);
            return true;
        }
        if (KICK.matcher(t).matches()) {
            Toasts.push("Kicked", "Hypixel put you in the lobby - rejoin in a moment", 0xFFFF5555);
            return true;
        }
        if (CAKE_SOUL.matcher(t).matches()) {
            Toasts.push("Cake Soul", "found", 0xFFFF55FF);
            return true;
        }
        if ((m = MINION_COINS.matcher(t)).matches()) {
            minionStonks += Long.parseLong(m.group(1).replace(",", ""));
            return true;
        }
        if ((m = MINION_UPGRADE.matcher(t)).matches()) {
            Toasts.push("Minion upgraded", "Tier " + m.group(1), 0xFF55FF55);
            return true;
        }
        if ((m = MINION_PLACED.matcher(t)).matches()) {
            minionsPlaced += m.group(1).equals("placed") ? 1 : -1;
            return true;
        }
        if (EYEDROPS.matcher(t).matches()) {
            if (Features.on("minion_chat")) alert("Eyedrops", "The minion used the last of your eyedrops", 0xFFFFAA00, 1.0f);
            return true;
        }
        if ((m = NEED_ITEMS.matcher(t)).matches()) {
            Toasts.push("Minion upgrade", "Needs " + m.group(1) + " more " + m.group(2), 0xFFFFAA00);
            return true;
        }
        return false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!sb()) return ChatBus.PASS;
        feed(line.plain());
        if (Features.on("misc_coords")) {
            Matcher m = COORDS.matcher(line.plain().trim());
            boolean ours = m.matches();
            int[] other = ours ? null : io.github.zmuzy.sbc.chat.ChatExtras.coords(line.plain());
            if ((ours || other != null) && !line.plain().contains("[+ waypoint]")) {
                String who = !ours ? "" : m.group(1).replaceAll("^.*> ", "").replaceAll("\\[.*?\\] ", "").replaceAll("[^A-Za-z0-9_ ]", "").trim();
                String cmd = "/sbc wp at " + (ours ? m.group(2) + " " + m.group(3) + " " + m.group(4) : other[0] + " " + other[1] + " " + other[2]) + " " + (who.isEmpty() ? "Shared" : who);
                net.minecraft.network.chat.MutableComponent c = line.component().copy();
                c.append(net.minecraft.network.chat.Component.literal(" [+ waypoint]").withStyle(st -> st.withColor(0x55FF55).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(cmd))));
                return new ChatBus.Replace(c);
            }
        }
        return ChatBus.PASS;
    }

    private static String sbLine(Pattern p) {
        for (String l : GameState.scoreboard) {
            Matcher m = p.matcher(l.trim());
            if (m.matches()) return m.group(1) + ":" + m.group(2);
        }
        return "";
    }

    private static String tabLine(Pattern p) {
        for (String l : GameState.tab) {
            Matcher m = p.matcher(l.trim());
            if (m.matches()) return m.group(1);
        }
        return "";
    }

    // --- session ---

    // everything the session card can say, so the editor sample goes through the same layout as live numbers
    private record Session(String closing, long npc, long minion, int placed, int sacrifices, int essence, Map<String, Integer> bonus, int souls, int relics, int relicsOf) {}

    private static String num(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static RichCard.Content session(Session s) {
        List<RichCard.Line> out = new ArrayList<>();
        if (!s.closing().isEmpty()) out.add(RichCard.Line.row("Server restart", s.closing(), Modern.BAD).icon("minecraft:clock").raw().brief("Restart"));
        out.add(RichCard.Line.section("Coins"));
        if (s.npc() > 0) {
            boolean near = s.npc() > NPC_DAILY_LIMIT * 0.9;
            out.add(RichCard.Line.row("NPC sold today", Modern.fraction(num(s.npc()), "500M", near ? 'c' : '6', 'f')).icon("minecraft:gold_ingot").meter((double) s.npc() / NPC_DAILY_LIMIT, near ? Modern.BAD : Modern.GOLD).brief("NPC sold"));
            out.add(RichCard.Line.row("Left to sell today", num(Math.max(0, NPC_DAILY_LIMIT - s.npc())), near ? Modern.BAD : Modern.LABEL).key("NPC sold today").raw().brief("Left"));
        }
        if (s.minion() > 0) out.add(RichCard.Line.row("Minion coins", num(s.minion()), Modern.GOLD).icon("minecraft:hopper").raw().brief("Minions"));
        if (s.placed() != 0) out.add(RichCard.Line.row("Minions placed", (s.placed() > 0 ? "+" : "") + s.placed(), Modern.INK).icon("minecraft:cobblestone").raw().brief("Placed"));
        out.add(RichCard.Line.section("Dragon sacrifices"));
        if (s.sacrifices() > 0) {
            out.add(RichCard.Line.row("Sacrifices", String.valueOf(s.sacrifices()), Modern.INK).icon("minecraft:dragon_head").raw());
            out.add(RichCard.Line.row("Dragon Essence", "§d" + num(s.essence()) + "  §7" + String.format(Locale.ROOT, "%.1f each", (double) s.essence() / s.sacrifices())).key("Sacrifices").raw().brief("Essence"));
            for (Map.Entry<String, Integer> e : s.bonus().entrySet()) out.add(RichCard.Line.row(e.getKey(), "§ex" + e.getValue()).raw());
        }
        out.add(RichCard.Line.section("Found this session"));
        if (s.souls() > 0) out.add(RichCard.Line.row("Fairy souls", String.valueOf(s.souls()), 0xFFFF7FE0).icon("minecraft:pink_dye").raw().brief("Souls"));
        if (s.relicsOf() > 0) out.add(RichCard.Line.row("Relics", Modern.fraction(String.valueOf(s.relics()), String.valueOf(s.relicsOf()), 'a', 'f')).icon("minecraft:spider_eye"));
        return new RichCard.Content("", out);
    }

    public static final HudElement MISC = new RichCard("misc_panel", "Session", "Misc", Zone.TOP_LEFT, 52, "misc_panel") {
        @Override
        public boolean visible() {
            return sb() && (npcShown() > 0 || sacrifices > 0 || fairySouls > 0 || relicsTotal > 0 || minionStonks > 0 || !sbLine(SERVER_CLOSING).isEmpty());
        }

        @Override
        protected Content live() {
            return session(new Session(sbLine(SERVER_CLOSING), npcShown(), minionStonks, minionsPlaced, sacrifices, essence, bonus, fairySouls, relics, relicsTotal));
        }

        @Override
        protected Content sample() {
            Map<String, Integer> loot = new LinkedHashMap<>();
            loot.put("Ritual Residue", 12);
            loot.put("Crude Gabagool Distillate", 2);
            return session(new Session("3:12", 41_000_000L, 1_240_500L, 3, 8, 412, loot, 3, 12, 28));
        }

        @Override
        public List<Content> states() {
            return List.of(session(new Session("", 185_400_000L, 0, -1, 0, 0, Map.of(), 0, 28, 28)), session(new Session("", 0, 0, 0, 0, 0, Map.of(), 1, 0, 0)));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            String closing = sbLine(SERVER_CLOSING);
            if (!closing.isEmpty()) out.add(new Row("Server restart", closing, Theme.current().bad()));
            long npc = npcShown();
            if (npc > 0) out.add(new Row("NPC sold today", String.format(Locale.ROOT, "%,d / 500M", npc), npc > NPC_DAILY_LIMIT * 0.9 ? Theme.current().bad() : Theme.current().accent()));
            if (minionStonks > 0) out.add(new Row("Minion coins", String.format(Locale.ROOT, "%,d", minionStonks), Theme.current().accent()));
            if (sacrifices > 0) out.add(new Row("Sacrifices", sacrifices + "  " + essence + " essence", 0xFFAA00AA));
            for (Map.Entry<String, Integer> e : bonus.entrySet()) out.add(new Row(e.getKey(), String.valueOf(e.getValue()), Theme.current().dim()));
            if (fairySouls > 0) out.add(new Row("Fairy souls", String.valueOf(fairySouls), 0xFFFF55FF));
            if (relicsTotal > 0) out.add(new Row("Relics", relics + "/" + relicsTotal, Theme.current().text()));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("NPC sold today", "41,000,000 / 500M", Theme.current().accent()), new Row("Fairy souls", "3", 0xFFFF55FF));
        }
    };

    // --- trevor ---

    private static final Pattern HINT_PARTS = Pattern.compile("^(\\d+) blocks (above|below), (\\d+)°$");

    private static RichCard.Content trevor(String area, String hint, long hintAgoMs, int peltCount) {
        List<RichCard.Line> out = new ArrayList<>();
        boolean hunting = !area.isEmpty();
        if (hunting) out.add(RichCard.Line.row("Animal near", area, Modern.AQUA).icon("minecraft:compass").raw().brief("Near"));
        if (!hint.isEmpty()) {
            Matcher m = HINT_PARTS.matcher(hint);
            if (m.matches()) {
                boolean above = m.group(2).equals("above");
                out.add(RichCard.Line.row("Hint", "§f" + m.group(1) + " blocks  §b" + m.group(3) + "°").icon("minecraft:spyglass").chip(above ? "ABOVE" : "BELOW", above ? Modern.GOOD : Modern.WARN).raw());
            } else out.add(RichCard.Line.row("Hint", hint).raw());
        }
        if (hunting && hintAgoMs >= 0) out.add(RichCard.Line.row("Last hint", "§7" + (hintAgoMs / 1000) + "s ago").raw().brief("Last"));
        long nextIn = trevorNextAt - System.currentTimeMillis();
        if (nextIn > 0) out.add(RichCard.Line.row("Next quest", (nextIn + 999) / 1000 + "s", Modern.WARN).raw().brief("Next"));
        if (peltCount > 0) out.add(RichCard.Line.row("Pelts", num(peltCount), Modern.GOOD).icon("minecraft:leather").raw());
        return new RichCard.Content(hunting ? "§aHunting" : "§7Idle", out);
    }

    public static final HudElement TREVOR = new RichCard("trevor_panel", "Trevor", "Misc", Zone.TOP_LEFT, 53, "trevor_panel") {
        @Override
        public boolean visible() {
            return sb() && (peltsShown() > 0 || (!trevorArea.isEmpty() && System.currentTimeMillis() - trevorAt < 10 * 60_000L));
        }

        @Override
        protected Content live() {
            return trevor(trevorArea, trevorHint, System.currentTimeMillis() - trevorAt, peltsShown());
        }

        @Override
        protected Content sample() {
            return trevor("Desert Mountain", "40 blocks above, 45°", 18_000, 12);
        }

        @Override
        public List<Content> states() {
            return List.of(trevor("", "", -1, 7), trevor("Mushroom Gorge", "12 blocks below, 80°", 2_000, 0));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            if (!trevorArea.isEmpty()) out.add(new Row("Animal near", trevorArea, Theme.current().accent()));
            if (!trevorHint.isEmpty()) out.add(new Row("Hint", trevorHint, Theme.current().text()));
            if (peltsShown() > 0) out.add(new Row("Pelts", String.valueOf(peltsShown()), Theme.current().good()));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Animal near", "Desert Mountain", Theme.current().accent()), new Row("Hint", "40 blocks above, 45°", 0), new Row("Pelts", "12", Theme.current().good()));
        }
    };

    // --- effects ---

    // hypixel says "non-god effects" while a god potion runs and "active effects" otherwise
    private static boolean nonGodEffects() {
        for (String l : GameState.tab) {
            String t = l.trim();
            if (EFFECTS.matcher(t).matches()) return t.contains("non-god");
        }
        return false;
    }

    private static RichCard.Content effects(String god, String count, boolean nonGod) {
        List<RichCard.Line> out = new ArrayList<>();
        if (!god.isEmpty()) out.add(RichCard.Line.row("God Potion", god, 0xFFFF7FE0).icon("minecraft:potion").chip("ACTIVE", 0xFFFF7FE0).raw().brief("God Pot"));
        if (!count.isEmpty()) out.add(RichCard.Line.row(nonGod ? "Non-god effects" : "Active effects", count, Modern.INK).key("Active effects").icon("minecraft:glass_bottle").raw().brief(nonGod ? "Non-god" : "Effects"));
        return new RichCard.Content("", out);
    }

    public static final HudElement EFFECTS_EL = new RichCard("effects_panel", "Effects", "Misc", Zone.TOP_RIGHT, 29, "effects_panel") {
        @Override
        public boolean visible() {
            return sb() && (!tabLine(GOD_POT).isEmpty() || !tabLine(EFFECTS).isEmpty());
        }

        @Override
        protected Content live() {
            return effects(tabLine(GOD_POT), tabLine(EFFECTS), nonGodEffects());
        }

        @Override
        protected Content sample() {
            return effects("12 Hours", "3", true);
        }

        @Override
        public List<Content> states() {
            return List.of(effects("", "5", false));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            String god = tabLine(GOD_POT);
            if (!god.isEmpty()) out.add(new Row("God Potion", god, 0xFFFF55FF));
            String n = tabLine(EFFECTS);
            if (!n.isEmpty()) out.add(new Row("Active effects", n, Theme.current().text()));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("God Potion", "12 Hours", 0xFFFF55FF), new Row("Active effects", "3", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature("misc_panel", "Misc", "Session panel", "Server restart countdown, NPC coins sold today, minion coins, sacrifices, fairy souls and relics found.", "").when(MiscExtras::sb));
        Features.add(new Feature("misc_piggy", "Misc", "Piggy bank alert", "A loud alert when your piggy bank cracks or breaks.", "").when(MiscExtras::sb));
        Features.add(new Feature("misc_coords", "Misc", "Coordinates become waypoints", "A chat message with coordinates gets a green [+ waypoint] you can click.", "").when(MiscExtras::sb));
        Features.add(new Feature("misc_npc_limit", "Misc", "NPC sell limit warning", "A warning when you sold 90% of the daily 500M NPC limit, which starts over at midnight GMT.", "").when(MiscExtras::sb));
        Features.add(new Feature("trevor_panel", "Farming Islands", "Trevor card", "Where the animal is, the height hint and your pelts.", "").when(() -> "THE_FARMING_ISLANDS".equals(GameState.island) || io.github.zmuzy.sbc.SbcMod.DEV));
        Features.add(new Feature("effects_panel", "Misc", "Potion effects", "God Potion time and how many effects are active, from the tab list.", "").when(MiscExtras::sb));
        Features.add(new Feature("minion_chat", "Minions", "Minion chat helpers", "Coins collected, upgrade notes, eyedrops warning and missing upgrade items.", "").when(MiscExtras::sb));
        ChatBus.register(ChatBus.OBSERVE + 19, "misc", f, MiscExtras::onChat);
        HudLayout.add(MISC);
        HudLayout.add(TREVOR);
        HudLayout.add(EFFECTS_EL);
    }
}
