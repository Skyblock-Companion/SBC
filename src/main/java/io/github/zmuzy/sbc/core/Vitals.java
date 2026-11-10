// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core;

import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// live numbers behind the main hud: health, defense, mana from the action bar, skill xp gains, stats from tab
// (sb menu as fallback), active pet. read only
public final class Vitals {
    public static long hp = -1, hpMax = -1, defense = -1, mana = -1, manaMax = -1, overflow = 0;
    // vitality is a pool like mana now: "116/116" with its own glyph on the action bar
    public static long vitality = -1, vitalityMax = -1;
    // healing going on ("+120▄" after health), 0 = none
    public static long healing = 0;
    // anything else the action bar says (ability names, "NOT ENOUGH MANA", secrets...) + when
    public static String message = "";
    public static long messageAt, vitalsAt;
    // whole action bar as last sent with color codes (skyhanni ActionBarUpdateEvent text) + when
    public static volatile String actionBarRaw = "";
    public static volatile long actionBarAt;
    // last skill gain: skill, "+12.5", percent into the level (-1 ?), when
    public static String skill = "", skillGain = "";
    public static double skillPct = -1;
    // hypixels "(1,234/5,678)" when the skill display is set to numbers, else ""
    public static String skillNumbers = "";
    // one skills rate, from skyhanni SkillXPInfo
    private static final class XpInfo {
        final java.util.ArrayDeque<Double> gains = new java.util.ArrayDeque<>();
        double last = -1, perHour;
        int pause;
        // from skyhanni SkillXPInfo isActive, sessionTimerActive, lastUpdate, timeActive
        boolean active, sessionOn;
        long lastUpdate, sessionSeconds;
    }

    private static final Map<String, XpInfo> XP_INFO = new java.util.HashMap<>();

    // secs without xp that still count as doing it (skyhanni defaults)
    private static int pauseFor(String skill) {
        return io.github.zmuzy.sbc.skills.SkillCards.pauseSeconds(skill);
    }

    // from skyhanni SkillProgress.updateSkillInfo, once a sec: how much the skill xp really grew. the action bar
    // "+6.2" cant say that, its one blocks worth sent once or twice a sec while you farm
    public static void xpReading(String skill, double current) {
        if (current < 0) return;
        XpInfo i = XP_INFO.computeIfAbsent(skill, k -> new XpInfo());
        if (i.last > 0) {
            double delta = current - i.last;
            if (delta > 0) {
                i.pause = pauseFor(skill);
                addGain(i, delta);
            } else if (i.pause > 0) {
                i.pause--;
                addGain(i, 0);
            } else {
                i.active = false;
            }
        }
        i.last = current;
    }

    // from skyhanni SkillApi.onSecondPassed: session runs while xp comes in, stops after the skills pause goes by
    // without any
    public static void sessionTick(long now) {
        XpInfo i = XP_INFO.get(skill);
        if (i == null || !i.sessionOn) return;
        if (now - i.lastUpdate > pauseFor(skill) * 1000L) i.sessionOn = false;
        if (i.sessionOn) i.sessionSeconds++;
    }

    // from skyhanni SkillApi.onActionBarUpdate: xp for this skill just showed
    private static void sessionGain(String name, long now) {
        XpInfo i = XP_INFO.computeIfAbsent(name, k -> new XpInfo());
        i.lastUpdate = now;
        i.sessionOn = true;
    }

    // rate from when xp stopped coming, skyhanni keeps showing it with PAUSED next to it
    public static double lastXpPerHour() {
        XpInfo i = XP_INFO.get(skill);
        return i == null ? 0 : i.perHour;
    }

    public static boolean xpActive() {
        XpInfo i = XP_INFO.get(skill);
        return i != null && i.active;
    }

    public static boolean sessionRunning() {
        XpInfo i = XP_INFO.get(skill);
        return i != null && i.sessionOn;
    }

    public static long sessionSeconds() {
        XpInfo i = XP_INFO.get(skill);
        return i == null ? 0 : i.sessionSeconds;
    }

    // reset for the clickable "Session" line
    public static void resetSession() {
        XpInfo i = XP_INFO.get(skill);
        if (i == null) return;
        i.sessionOn = false;
        i.sessionSeconds = 0;
    }

    private static void addGain(XpInfo i, double gain) {
        i.gains.addFirst(gain);
        while (i.gains.size() > 30) i.gains.removeLast();
        double total = 0;
        for (double g : i.gains) total += g;
        i.perHour = total * 3600 / i.gains.size();
        i.active = true;
    }

    // skill xp/h over the last 30 sec of readings (0 once you stop)
    public static double xpPerHour() {
        if (System.currentTimeMillis() - skillAt > 60_000) return 0;
        XpInfo i = XP_INFO.get(skill);
        return i == null ? 0 : i.perHour;
    }

    public static long skillAt;
    // stat -> value text ("Speed" -> "400"), in the games order
    public static final Map<String, String> stats = new LinkedHashMap<>();
    public static String petName = "";
    public static int petLevel;
    // pet progress to next level (-1 ?, 100 at max) + rarity, from the tab pet widget
    public static double petXpPct = -1;
    // xp into level / needed like hypixel writes it ("550,162/1.4M"), "" = unknown
    public static String petXp = "";
    public static int petColor;
    public static String petTier = "";
    // a swap the tab doesnt show (dungeons have no pet line). from skyhanni CurrentPetApi (summon) and
    // PetStoragePatterns (autopet), read on the plain line with the rarity from the names colour
    private static final Pattern PET_SUMMON = Pattern.compile("^You summoned your (.+?)(?: ✦)?!$");
    private static final Pattern PET_DESPAWN = Pattern.compile("^You despawned your (.+?)(?: ✦)?!$");
    private static final Pattern PET_AUTO = Pattern.compile("^Autopet equipped your \\[Lvl (\\d+)] (?:\\[\\d+✦] )?(.+?)(?: ✦)?! VIEW RULE(?: \\(\\d+\\))?$");
    private static final java.util.regex.Pattern PET_XP = java.util.regex.Pattern.compile("\\(([\\d.]+)%\\)\\s*$");
    private static final java.util.regex.Pattern PET_NUMBERS = java.util.regex.Pattern.compile("([\\d,.]+[kKmMbB]?)\\s*/\\s*([\\d,.]+[kKmMbB]?)\\s*XP");
    // "<icon> Ferocity 55" in the sb menu profile item + stats menu (skyhanni menuPattern)
    private static final java.util.regex.Pattern LORE_STAT = java.util.regex.Pattern.compile("^\\s*\\S{1,2} ([A-Z][A-Za-z' ]+?) (-?[\\d,.]+%?)(?: .*)?$");

    private static final io.github.zmuzy.sbc.core.Patterns.P HP = io.github.zmuzy.sbc.core.Patterns.p("Vitals.HP");
    private static final io.github.zmuzy.sbc.core.Patterns.P DEF = io.github.zmuzy.sbc.core.Patterns.p("Vitals.DEF");
    private static final io.github.zmuzy.sbc.core.Patterns.P MANA = io.github.zmuzy.sbc.core.Patterns.p("Vitals.MANA");
    private static final io.github.zmuzy.sbc.core.Patterns.P OVER = io.github.zmuzy.sbc.core.Patterns.p("Vitals.OVER");
    private static final io.github.zmuzy.sbc.core.Patterns.P GAIN = io.github.zmuzy.sbc.core.Patterns.p("Vitals.GAIN");
    private static final io.github.zmuzy.sbc.core.Patterns.P GAIN2 = io.github.zmuzy.sbc.core.Patterns.p("Vitals.GAIN2");
    private static final io.github.zmuzy.sbc.core.Patterns.P STAT = io.github.zmuzy.sbc.core.Patterns.p("Vitals.STAT");
    private static final io.github.zmuzy.sbc.core.Patterns.P PET = io.github.zmuzy.sbc.core.Patterns.p("Vitals.PET");

    private static final java.util.Set<String> KNOWN = java.util.Set.of("Ability Damage", "Alchemy Wisdom", "Attack Speed", "Block Fortune", "Bonus Attack Speed", "Bonus Pest Chance", "Breaking Power", "Cactus Fortune", "Carpentry Wisdom", "Carrot Fortune", "Charm Chance", "Cocoa Beans Fortune", "Cold Resistance", "Combat Wisdom", "Crit Chance", "Crit Damage", "Defense", "Double Hook Chance", "Dwarven Metal Fortune", "Enchanting Wisdom", "Farming Fortune", "Farming Wisdom", "Fear", "Ferocity", "Fig Fortune", "Fishing Speed", "Fishing Wisdom", "Foraging Fortune", "Foraging Wisdom", "Gemstone Fortune", "Gemstone Spread", "Health", "Health Regen", "Hearts", "Heat Resistance", "Helix Fortune", "Hunter Fortune", "Hunting Fortune", "Hunting Wisdom", "Intelligence", "Magic Find", "Mana Regen", "Mangrove Fortune", "Melon Slice Fortune", "Mending", "Mining Fortune", "Mining Speed", "Mining Spread", "Mining Wisdom", "Moonflower Fortune", "Mushroom Fortune", "Nether Wart Fortune", "Ore Fortune", "Overbloom", "Pet Luck", "Potato Fortune", "Pressure Resistance", "Pristine", "Pull", "Pumpkin Fortune", "Respiration", "Rift Damage", "Rift Time", "Runecrafting Wisdom", "Sea Creature Chance", "Social Wisdom", "Speed", "Strength", "Sugar Cane Fortune", "Sunflower Fortune", "Sweep", "Swing Range", "Taming Wisdom", "Timber", "Tracking", "Treasure Chance", "Trophy Fish Chance", "True Defense", "Vitality", "Wheat Fortune", "Wild Rose Fortune");

    private Vitals() {}

    // dev world only: fake but believable numbers to check the hud without hypixel
    public static void devFill() {
        long now = System.currentTimeMillis();
        hp = 1840 + (now / 700 % 400);
        hpMax = 2240;
        defense = 612;
        mana = 410;
        manaMax = 560;
        overflow = 37;
        vitalsAt = now;
        if (now - messageAt > 5000) {
            message = "Rapid-fire -50 Mana";
            messageAt = now;
        }
        skill = "Mining";
        skillGain = "+12.5";
        skillPct = 56.4;
        skillAt = now;
        if (stats.isEmpty()) {
            stats.put("Speed", "400");
            stats.put("Strength", "612");
            stats.put("Crit Chance", "78");
            stats.put("Crit Damage", "421");
            stats.put("Attack Speed", "100");
            stats.put("Ferocity", "55");
            stats.put("Magic Find", "118");
        }
        petName = "Tiger";
        petLevel = 100;
        GameState.scoreboard = java.util.List.of("10/31/26 m12A", "", "Late Autumn 14th", "10:40pm ☽", "⏣ Village", "", "Purse: 4,210,533", "Motes: 1,204",
                "", "Bits: 18,400", "", "Mithril Powder: 84,210", "Gemstone Powder: 12,800", "", "Objective", "Talk to Jerry about the winter event",
                "", "Slayer Quest", "Revenant Horror III", "(120/500) Combat XP", "", "Party (2)", "Player_Name (Leader)", "", "www.hypixel.net");
    }

    private static long num(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    // one "1,234/5,678<glyph>" or "1,234<glyph>" chunk + the words that can follow
    private static final io.github.zmuzy.sbc.core.Patterns.P TOKEN = io.github.zmuzy.sbc.core.Patterns.p("Vitals.TOKEN");
    private static final io.github.zmuzy.sbc.core.Patterns.P HEAL = io.github.zmuzy.sbc.core.Patterns.p("Vitals.HEAL");
    private static long lastDump;

    private static boolean isHeart(String g) {
        return g.indexOf('❤') >= 0 || g.indexOf('♥') >= 0 || g.indexOf('♡') >= 0;
    }

    private static boolean isMana(String g) {
        return g.indexOf('✎') >= 0 || g.indexOf('✏') >= 0 || g.indexOf('✐') >= 0;
    }

    private static boolean isDefense(String g) {
        return g.indexOf('❈') >= 0 || g.indexOf('❇') >= 0;
    }

    // from skyhanni ArmorStackDisplay: stacks of whatever armor set, in the action bars own glyph
    public static final java.util.regex.Pattern ARMOR_STACKS = java.util.regex.Pattern.compile("(?:^|\\s)(\\d+)[\u1750\u2051|\u0489\u046A\u26B6]");
    private static final long STACKS_HOLD = 3000;
    private static int armorStacks;
    private static long armorStacksAt;

    // armor stacks. a line without them only ends them after a moment, one can pass between 2 that have them
    public static int armorStacks() {
        return System.currentTimeMillis() - armorStacksAt < STACKS_HOLD ? armorStacks : 0;
    }

    // every action bar line, codes already stripped
    public static void onActionBar(String plain) {
        long now = System.currentTimeMillis();
        String rest = plain;
        Matcher stacks = ARMOR_STACKS.matcher(plain);
        if (stacks.find()) {
            armorStacks = Integer.parseInt(stacks.group(1));
            armorStacksAt = now;
            rest = rest.replace(stacks.group(), " ");
        }
        Matcher m;
        boolean gotHp = false, gotMana = false, gotDef = false, gotOver = false;
        healing = 0;
        Matcher heal = HEAL.matcher(plain);
        if (heal.find()) {
            healing = num(heal.group(1));
            rest = rest.replace(heal.group(), " ");
        }
        // stat tokens get matched by glyph (+ the words after when spelled out), so a changed word or another heart
        // glyph cant dump the whole line into the message strip
        List<long[]> loose = new java.util.ArrayList<>();
        List<String> looseText = new java.util.ArrayList<>();
        m = TOKEN.matcher(plain);
        while (m.find()) {
            String g = m.group(3), word = m.group(4) == null ? "" : m.group(4);
            boolean slash = m.group(2) != null;
            long a = num(m.group(1)), b = slash ? num(m.group(2)) : -1;
            if (slash && (isHeart(g) || word.equals("Health"))) {
                hp = a;
                hpMax = b;
                vitalsAt = now;
                gotHp = true;
            } else if (slash && (isMana(g) || word.equals("Mana"))) {
                mana = a;
                manaMax = b;
                gotMana = true;
            } else if (slash && (g.indexOf('') >= 0 || word.equals("Vitality"))) {
                vitality = a;
                vitalityMax = b;
            } else if (!slash && (isDefense(g) || word.equals("Defense"))) {
                defense = a;
                gotDef = true;
            } else if (!slash && (g.indexOf('ʬ') >= 0 || word.equals("Overflow Mana"))) {
                overflow = Math.max(0, a);
                gotOver = true;
            } else if (slash && g.length() > 0 && !Character.isLetter(g.charAt(0))) {
                loose.add(new long[]{a, b});
                looseText.add(m.group());
                continue;
            } else if (!slash && g.length() > 0 && !Character.isLetter(g.charAt(0))) {
                loose.add(new long[]{a, -1});
                looseText.add(m.group());
                continue;
            } else continue;
            rest = rest.replace(m.group(), " ");
        }
        // unknown glyphs: the line always goes health, defense, mana, so fall back on position
        if (!gotHp && !loose.isEmpty() && loose.get(0)[1] >= 0) {
            hp = loose.get(0)[0];
            hpMax = loose.get(0)[1];
            vitalsAt = now;
            gotHp = true;
            rest = rest.replace(looseText.get(0), " ");
            loose.remove(0);
            looseText.remove(0);
        }
        if (!gotDef && !loose.isEmpty() && loose.get(0)[1] < 0) {
            defense = loose.get(0)[0];
            gotDef = true;
            rest = rest.replace(looseText.get(0), " ");
            loose.remove(0);
            looseText.remove(0);
        }
        if (!gotMana && !loose.isEmpty() && loose.get(0)[1] >= 0) {
            mana = loose.get(0)[0];
            manaMax = loose.get(0)[1];
            gotMana = true;
            rest = rest.replace(looseText.get(0), " ");
            loose.remove(0);
            looseText.remove(0);
        }
        if (gotMana && !gotOver) overflow = 0;
        if (!gotHp && now - lastDump > 60_000 && plain.matches(".*\\d/\\d.*")) {
            lastDump = now;
            StringBuilder cps = new StringBuilder();
            plain.codePoints().forEach(c -> cps.append(c < 128 ? String.valueOf((char) c) : "<U+" + Integer.toHexString(c) + ">"));
            io.github.zmuzy.sbc.SbcMod.LOG.info("Action bar not understood: {}", cps);
        }
        // leftover number + symbol chunks are stat readouts, not messages
        for (String t : looseText) rest = rest.replace(t, " ");
        if ((m = GAIN.matcher(rest)).find()) {
            skill = m.group(2);
            skillGain = "+" + m.group(1);
            try {
                skillPct = Double.parseDouble(m.group(3));
            } catch (Exception e) {
                skillPct = -1;
            }
            skillNumbers = "";
            skillAt = now;
            sessionGain(skill, now);
            rest = rest.replace(m.group(), " ");
        } else if ((m = GAIN2.matcher(rest)).find()) {
            skill = m.group(2);
            skillGain = "+" + m.group(1);
            skillPct = -1;
            skillNumbers = m.group(3) + "/" + m.group(4);
            skillAt = now;
            sessionGain(skill, now);
            rest = rest.replace(m.group(), " ");
        }
        rest = rest.replaceAll("\\s{2,}", "  ").trim();
        if (!rest.isEmpty() && !rest.equals(message)) {
            message = rest;
            messageAt = now;
        } else if (!rest.isEmpty() && now - messageAt > 4000) {
            message = rest;
            messageAt = now;
        }
    }

    // action bar numbers are fresh (game resends them about once a sec)
    public static boolean live() {
        return System.currentTimeMillis() - vitalsAt < 6000 && hpMax > 0;
    }

    // health to show: real action bar number, else the players scaled health
    public static double hpNow() {
        if (live()) return hp;
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : p.getHealth();
    }

    public static double hpMax() {
        if (live()) return hpMax;
        var p = Minecraft.getInstance().player;
        return p == null ? 20 : p.getMaxHealth();
    }

    // GameState calls this with the stripped tab lines
    public static void readTab(List<String> lines) {
        Map<String, String> found = new LinkedHashMap<>();
        String pet = "";
        int level = 0;
        double xp = -1;
        String xpNumbers = "";
        boolean noPet = false;
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            Matcher m = STAT.matcher(l);
            if (m.find() && KNOWN.contains(m.group(1).trim())) found.putIfAbsent(m.group(1).trim(), m.group(2));
            else if ((m = PET.matcher(l)).find()) {
                level = Integer.parseInt(m.group(1));
                pet = m.group(2).replaceAll("✦", "").trim();
                String next = i + 1 < lines.size() ? lines.get(i + 1) : "";
                Matcher x = PET_XP.matcher(next);
                if (x.find()) xp = Double.parseDouble(x.group(1));
                else if (next.contains("MAX LEVEL")) xp = 100;
                Matcher n = PET_NUMBERS.matcher(next);
                if (n.find()) xpNumbers = n.group(1).replaceFirst("\\.\\d+$", "") + "/" + n.group(2);
            } else if (l.trim().equals("Pet:") && i + 1 < lines.size() && lines.get(i + 1).contains("No pet")) noPet = true;
        }
        if (noPet) {
            petName = "";
            petLevel = 0;
            petXpPct = -1;
            petXp = "";
        }
        if (!found.isEmpty()) {
            stats.putAll(found);
        }
        if (!pet.isEmpty()) {
            petName = pet;
            petLevel = level;
            petXpPct = xp;
            petXp = xpNumbers;
        }
    }

    public static void petChat(net.minecraft.network.chat.Component line, String plain) {
        Matcher m;
        if (PET_DESPAWN.matcher(plain).matches()) {
            petName = "";
            petLevel = 0;
            petXpPct = -1;
            petXp = "";
            return;
        }
        int level = -1;
        String name;
        if ((m = PET_AUTO.matcher(plain)).matches()) {
            level = Integer.parseInt(m.group(1));
            name = m.group(2).trim();
        } else if ((m = PET_SUMMON.matcher(plain)).matches()) name = m.group(1).trim();
        else return;
        int color = nameColor(line, name);
        String tier = color == 0 ? "" : tierOfColor(color);
        if (level < 0) level = knownLevel(name, tier);
        // the tab fills the bar again outside dungeons, a new pet starts without the old ones
        if (!name.equals(petName) || !tier.equals(petTier)) {
            petXpPct = -1;
            petXp = "";
        }
        petName = name;
        petLevel = Math.max(0, level);
        if (color != 0) {
            petColor = color;
            petTier = tier;
        }
    }

    private static int nameColor(net.minecraft.network.chat.Component line, String name) {
        int[] color = {0};
        line.visit((style, text) -> {
            if (color[0] == 0 && style.getColor() != null && !text.isBlank() && name.contains(text.replace("✦", "").trim())) color[0] = style.getColor().getValue();
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return color[0];
    }

    // level from the pets we know (menu or /pv), 0 when this one isnt there
    static int knownLevel(String name, String tier) {
        Profile.Data d = Profile.current;
        if (d == null) return 0;
        for (Profile.Pet p : d.pets) if (p.name().equalsIgnoreCase(name) && (tier.isEmpty() || tier.equals(p.tier()))) return p.level();
        return 0;
    }

    // pet tab line with colors, name color = rarity
    public static void readPetStyle(net.minecraft.network.chat.Component line) {
        int[] color = {0};
        line.visit((style, text) -> {
            if (style.getColor() != null && !text.contains("Lvl") && text.chars().anyMatch(Character::isLetter)) color[0] = style.getColor().getValue();
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        if (color[0] != 0) {
            petColor = color[0];
            petTier = tierOfColor(color[0]);
        }
    }

    static String tierOfColor(int rgb) {
        return switch (rgb & 0xFFFFFF) {
            case 0xFFFFFF -> "COMMON";
            case 0x55FF55 -> "UNCOMMON";
            case 0x5555FF -> "RARE";
            case 0xAA00AA -> "EPIC";
            case 0xFFAA00 -> "LEGENDARY";
            case 0xFF55FF -> "MYTHIC";
            default -> "";
        };
    }

    // stats a menu item lists, read like skyhanni does from the sb menu + stats & equipment
    public static Map<String, String> statsInLore(List<String> plainLines) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String l : plainLines) {
            Matcher m = LORE_STAT.matcher(l);
            if (!m.matches()) continue;
            String name = m.group(1).trim();
            if (name.equals("Bonus Attack Speed")) name = "Attack Speed";
            if (KNOWN.contains(name)) out.putIfAbsent(name, m.group(2));
        }
        return out;
    }

    // fallback: a stat from the sb menu profile item
    public static void setStat(String name, String value) {
        stats.putIfAbsent(name, value);
    }

    public static double stat(String name) {
        String v = stats.get(name);
        if (v == null) return 0;
        try {
            return Double.parseDouble(v.replace(",", "").replace("%", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    // ehp: health scaled by defense
    public static double ehp() {
        double d = defense >= 0 ? defense : stat("Defense");
        return hpMax() * (1 + d / 100.0);
    }

    public static String compact(double v) {
        if (v >= 1e9) return String.format(Locale.ROOT, "%.1fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.ROOT, "%.1fM", v / 1e6);
        if (v >= 10_000) return String.format(Locale.ROOT, "%.1fk", v / 1e3);
        return String.format(Locale.ROOT, "%,d", (long) v);
    }
}
