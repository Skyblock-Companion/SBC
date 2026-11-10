// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemIcons;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.TabWidgets;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.mixin.HudAccessor;
import io.github.zmuzy.sbc.mc.mixin.PlayerTabOverlayAccessor;
import io.github.zmuzy.sbc.ui.hud.Looks;
import io.github.zmuzy.sbc.ui.hud.RowText;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the tab list in our style. hypixel fills it with widgets, each starts with a known first line ("Players
// (8)", "Info", "Skills:"...) and runs to the next one
public final class TabList {
    private static final int LINE = 10, PAD = 4, GAP = 3;
    private static final Pattern LEVEL = Pattern.compile("^\\[(\\d+)\\]\\s+(\\S+)");

    // player = the entry whose name is on the line. face = where the head comes from, also for hypixels fake entries
    private record Line(Component text, String coded, String plain, PlayerInfo player, PlayerInfo face, int level, boolean gapBefore) {}

    public record Widget(String key, String title, List<Line> lines, Line head) {}

    private static final Ui ui = new Ui();
    private static final List<Widget> widgets = new ArrayList<>();
    // where cards were drawn last (screen px), for the hud editor
    public record Card(String key, String title, int x, int y, int w, int h) {}

    private static final List<Card> cards = new ArrayList<>();
    private static Component header, footer;
    private static long refreshed;
    // set by the no-game pictures. heads come from skins and theres no players to ask
    private static boolean standIns;
    private TabList() {}

    public static Option option(String key) {
        return opt(key);
    }

    private static Option opt(String key) {
        Feature f = Features.get("tablist");
        return f == null ? null : f.option(key);
    }

    private static boolean optBool(String key, boolean def) {
        Option o = opt(key);
        return o == null ? def : o.bool();
    }

    private static int optInt(String key, int def) {
        Option o = opt(key);
        return o == null ? def : o.integer();
    }

    private static String optChoice(String key, String def) {
        Option o = opt(key);
        return o == null ? def : o.choice();
    }

    // tab held on skyblock and our list replaces vanilla
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        if (!Features.on("tablist") || mc.player == null || mc.getConnection() == null) return false;
        if (!(GameState.inSkyBlock || SbcMod.DEV) || mc.isLocalServer()) return false;
        boolean down = mc.options.keyPlayerList.isDown();
        if (!optToggle()) return down;
        // skyhanni toggle tab: one press opens, next one closes
        if (down && !toggleWasDown) toggled = !toggled;
        toggleWasDown = down;
        return toggled;
    }

    private static boolean toggled, toggleWasDown;

    private static boolean optToggle() {
        var o = Features.get("tablist").option("tablist.toggle");
        return o != null && o.bool();
    }

    // with toggle tab the vanilla list cant pop up while the key is held
    public static boolean toggleHidesVanilla() {
        return Features.on("tablist") && GameState.inSkyBlock && optToggle();
    }

    public static boolean hidden(String key) {
        return Config.I.tabHidden.contains(key);
    }

    public static void setHidden(String key, boolean hide) {
        if (hide && !Config.I.tabHidden.contains(key)) Config.I.tabHidden.add(key);
        if (!hide) Config.I.tabHidden.remove(key);
    }

    private static boolean advert(String plain) {
        String l = plain.toLowerCase(Locale.ROOT);
        return l.contains("store.hypixel.net") || l.contains("hypixel store");
    }

    private static void refresh(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - refreshed < 200) return;
        refreshed = now;
        var conn = mc.getConnection();
        if (conn == null) return;
        List<PlayerInfo> infos = new ArrayList<>(conn.getListedOnlinePlayers());
        infos.sort(Comparator.comparing((PlayerInfo p) -> p.getTeam() != null ? p.getTeam().getName() : "")
                .thenComparing(p -> p.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        List<Component> texts = new ArrayList<>(infos.size());
        List<String> plain = new ArrayList<>(infos.size());
        for (PlayerInfo p : infos) {
            Component dn = p.getTabListDisplayName();
            Component text = dn != null ? dn : Component.literal(p.getProfile().name());
            texts.add(text);
            plain.add(GameState.strip(text.getString()));
        }
        buildWidgets(texts, plain, infos);
        String sort = optChoice("sortPlayers", "Hypixel");
        for (Widget w : widgets) {
            if (!w.key.equals("PLAYER_LIST")) continue;
            if (sort.equals("Level")) w.lines.sort(Comparator.comparingInt((Line l) -> -l.level));
            else if (sort.equals("Name")) w.lines.sort(Comparator.comparing(l -> l.plain.trim().replaceAll("^\\[\\d+\\]\\s*", "").toLowerCase(Locale.ROOT)));
            if (Features.get("tablist").option("tablist.reverseSort") != null && Features.get("tablist").option("tablist.reverseSort").bool()) java.util.Collections.reverse(w.lines);
        }
        var overlay = (PlayerTabOverlayAccessor) ((HudAccessor) mc.gui.hud).sbc$tabList();
        header = overlay.sbc$header();
        footer = overlay.sbc$footer();
    }

    // card for the one-liners ("Area: Crimson Isle", "Gems: 90")
    public static final String INFO_KEY = "INFO_LINES";

    private static Line line(int i, List<Component> texts, List<String> plain, List<PlayerInfo> infos) {
        String pl = plain.get(i);
        PlayerInfo info = infos == null ? null : infos.get(i);
        boolean real = info != null && !info.getProfile().name().startsWith("!") && pl.contains(info.getProfile().name());
        int level = -1;
        Matcher m = LEVEL.matcher(pl.trim());
        boolean levelled = m.find();
        if (levelled) level = Integer.parseInt(m.group(1));
        PlayerInfo face = real ? info : null;
        // hypixel lines are fake-named entries. skin is on the players own entry, else the lines
        if (face == null && info != null && levelled) {
            var conn = Minecraft.getInstance().getConnection();
            PlayerInfo own = conn == null ? null : conn.getPlayerInfo(m.group(2));
            face = own != null ? own : info;
        }
        boolean gap = i > 0 && plain.get(i - 1).isBlank();
        return new Line(texts.get(i), coded(texts.get(i)), pl, real ? info : null, face, level, gap);
    }

    private static void buildWidgets(List<Component> texts, List<String> plain, List<PlayerInfo> infos) {
        boolean hideAds = optBool("hideAdverts", true);
        widgets.clear();
        plan = null;
        List<Line> info = null;
        for (TabWidgets.Widget w : TabWidgets.parse(plain)) {
            if (!Config.I.tabSeen.contains(w.def().id())) Config.I.tabSeen.add(w.def().id());
            if (hidden(w.def().id())) continue;
            if (hideAds && w.def().id().equals("FIRE_SALE")) continue;
            String title = w.title(plain);
            // a header with its own value is just an info line, not a card title
            if (title.contains(": ") && !title.endsWith(":") && w.lines().size() <= 1) {
                if (hidden(INFO_KEY)) continue;
                if (info == null) {
                    info = new ArrayList<>();
                    widgets.add(new Widget(INFO_KEY, "Info", info, null));
                    if (!Config.I.tabSeen.contains(INFO_KEY)) Config.I.tabSeen.add(INFO_KEY);
                }
                info.add(line(w.first(), texts, plain, infos));
                for (int i : w.lines()) if (!plain.get(i).isBlank()) info.add(line(i, texts, plain, infos));
                continue;
            }
            List<Line> lines = new ArrayList<>();
            for (int i : w.lines()) {
                if (hideAds && advert(plain.get(i))) continue;
                lines.add(line(i, texts, plain, infos));
            }
            if (lines.isEmpty() && !w.def().id().equals("PLAYER_LIST")) continue;
            widgets.add(new Widget(w.def().id(), title.endsWith(":") ? title.substring(0, title.length() - 1) : title, lines, line(w.first(), texts, plain, infos)));
        }
    }

    // cards from plain lines, for no-game pictures
    static void fillForTest(List<String> lines) {
        List<Component> texts = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (String l : lines) {
            texts.add(Component.literal(l));
            plain.add(GameState.strip(l));
        }
        buildWidgets(texts, plain, null);
        refreshed = System.currentTimeMillis();
        standIns = true;
        header = Component.literal("§bYou are playing on §e§lMC.HYPIXEL.NET\n§s");
        footer = Component.literal("\n§a§lUpgrades\n§6Coins Allowance II §e3 Days\n§6Garden Farming Fortune III §e49 Minutes\n§s\n§a§lRanks, Boosters & MORE! §c§lSTORE.HYPIXEL.NET");
    }

    // --- colors ---

    private static final int[] LEGACY_RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    // same colors on dark glass, dark ones keep their hue but stay readable
    private static final int[] SHOWN_RGB = {0xFF777777, 0xFF6A6AF0, 0xFF34CC34, 0xFF2BCFCF, 0xFFE85252, 0xFFD060D0, 0xFFFFAA00, 0xFFAAAAAA, 0xFFAAAAAA, 0xFF7C7CFF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};
    private static final String CODES = "0123456789abcdef", DARK_CODES = "01234589";

    // closest legacy code. hypixel only uses the 16 so its basically always exact
    private static char codeOf(TextColor color) {
        int rgb = color.getValue() & 0xFFFFFF, best = 15, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int c = LEGACY_RGB[i];
            int dr = (c >> 16) - (rgb >> 16), dg = ((c >> 8) & 255) - ((rgb >> 8) & 255), db = (c & 255) - (rgb & 255);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return CODES.charAt(best);
    }

    private static String codesOf(Style s) {
        StringBuilder b = new StringBuilder();
        TextColor color = s.getColor();
        if (color != null) b.append('§').append(codeOf(color));
        if (s.isBold()) b.append(ChatFormatting.BOLD);
        if (s.isItalic()) b.append(ChatFormatting.ITALIC);
        if (s.isUnderlined()) b.append(ChatFormatting.UNDERLINE);
        if (s.isStrikethrough()) b.append(ChatFormatting.STRIKETHROUGH);
        if (s.isObfuscated()) b.append(ChatFormatting.OBFUSCATED);
        return color == null && !b.isEmpty() ? "§r" + b : b.toString();
    }

    // component -> text with color codes. already coded text passes through
    static String coded(Component c) {
        if (c == null) return "";
        StringBuilder out = new StringBuilder();
        String[] was = {""};
        c.visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            String now = codesOf(style);
            if (!now.equals(was[0])) {
                out.append(now.isEmpty() ? "§r" : now);
                was[0] = now;
            }
            out.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        return out.toString();
    }

    // header/footer lines, each starting with the color the last one ended on
    static List<String> codedLines(Component c) {
        List<String> out = new ArrayList<>();
        if (c == null) return out;
        String carry = "";
        for (String s : coded(c).split("\n")) {
            String line = carry + s;
            carry = TabColumns.activeCodes(line);
            if (!GameState.strip(line).isBlank()) out.add(line);
        }
        return out;
    }

    // too long lines wrap, so header/footer never shrink the cards and nothing gets lost
    private static List<String> wrapped(List<String> lines, int room) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            String cur = "";
            for (String word : line.split(" ")) {
                String next = cur.isEmpty() ? word : cur + " " + word;
                if (ui.width(next) <= room || cur.isEmpty()) {
                    cur = next;
                    continue;
                }
                out.add(cur);
                cur = TabColumns.activeCodes(cur) + word;
            }
            if (!GameState.strip(cur).isBlank()) out.add(cur);
        }
        return out;
    }

    private static int rgbOf(char code) {
        int i = CODES.indexOf(Character.toLowerCase(code));
        return i < 0 ? 0 : SHOWN_RGB[i];
    }

    // first actually colored color, for the meter. 0 = none
    private static int firstVivid(String s) {
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            int c = rgbOf(s.charAt(++i));
            if (c == 0) continue;
            int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 40) return c;
        }
        return 0;
    }

    // first color code, 0 = none
    private static char firstCode(String s) {
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            char c = Character.toLowerCase(s.charAt(++i));
            if (CODES.indexOf(c) >= 0) return c;
        }
        return 0;
    }

    // white/grey label says nothing with its color, give it the cards label color
    private static String quietLabel(String label) {
        for (int i = 0; i + 1 < label.length(); i++) {
            if (label.charAt(i) != '§') continue;
            if ("01234569abcde".indexOf(Character.toLowerCase(label.charAt(++i))) >= 0) return label;
        }
        return RowText.plain(label);
    }

    private static final Pattern ENDS_IN_LEVEL = Pattern.compile("^(.*\\D) (\\d+)$");

    // "Mining 47": level is what you came for, the name is just a label
    private static String brightLevel(String label) {
        if (label.indexOf('§') >= 0) return label;
        Matcher m = ENDS_IN_LEVEL.matcher(label);
        return m.matches() ? m.group(1) + " §f" + m.group(2) : label;
    }

    // cut colored text to a width, never inside a code
    private static String fit(String s, int maxW) {
        if (ui.width(s) <= maxW) return s;
        String cut = s;
        while (!cut.isEmpty() && ui.width(cut + "…") > maxW) cut = TabColumns.withoutLast(cut);
        return cut + "…";
    }

    // draws coded text. the dark colors go piece by piece in their readable shade bc the font only draws a code
    // in its own color and those were made for black tooltips
    private static void print(String s, int x, int y, int color) {
        boolean dark = false;
        for (int i = 0; i + 1 < s.length() && !dark; i++) if (s.charAt(i) == '§') dark = DARK_CODES.indexOf(s.charAt(++i)) >= 0;
        if (!dark) {
            ui.textShadow(s, x, y, color);
            return;
        }
        int cx = x, c = color;
        String format = "";
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '§' || i + 1 >= s.length()) {
                run.append(ch);
                continue;
            }
            char code = Character.toLowerCase(s.charAt(++i));
            if (run.length() > 0) {
                String piece = format + run;
                ui.textShadow(piece, cx, y, c);
                cx += ui.width(piece);
                run.setLength(0);
            }
            int n = CODES.indexOf(code);
            if (n >= 0) {
                c = SHOWN_RGB[n];
                format = "";
            } else if (code == 'r') {
                c = color;
                format = "";
            } else if ("klmno".indexOf(code) >= 0) format += "§" + code;
        }
        if (run.length() > 0) ui.textShadow(format + run, cx, y, c);
    }

    // label left, value right. what doesnt fit gets cut, value stays whole as long as possible
    private static void row(String label, String value, int x, int y, int w, int labelColor, int valueColor) {
        if (value.isEmpty()) {
            print(fit(label, w), x, y, labelColor);
            return;
        }
        String v = fit(value, w - 8);
        int vw = ui.width(v);
        print(fit(label, Math.max(8, w - vw - 6)), x, y, labelColor);
        print(v, x + w - vw, y, valueColor);
    }

    // --- how each card looks ---

    private static final Map<String, Looks.Look> LOOKS = new HashMap<>();
    private static final Map<String, String> ROW_ICONS = new HashMap<>();
    private static final Map<String, Integer> ROW_COLORS = new HashMap<>();

    private static void look(int accent, String icon, String... ids) {
        for (String id : ids) LOOKS.put(id, new Looks.Look(accent, "minecraft:" + icon));
    }

    private static void rowIcon(String icon, String... labels) {
        for (String l : labels) ROW_ICONS.put(l, "minecraft:" + icon);
    }

    // own meter color per skill/crop, a card full of bars reads better that way
    private static void rowColor(int color, String... labels) {
        for (String l : labels) ROW_COLORS.put(l, color);
    }

    static {
        look(0xFF5FEA8C, "player_head", "PLAYER_LIST");
        look(0xFF9CE37D, "player_head", "COOP");
        look(0xFF7FD4FF, "oak_door", "GUESTS", "ISLAND");
        look(0xFFFF9EC7, "cake", "PARTY", "DUNGEON_PARTY");
        look(0xFF5CD8FF, "compass", INFO_KEY, "INFO", "AREA");
        look(0xFF90A4AE, "repeater", "SERVER");
        look(0xFF5FEA8C, "emerald", "GEMS");
        look(0xFFFF9EC7, "ghast_tear", "FAIRY_SOULS");
        look(0xFFFFD166, "name_tag", "PROFILE");
        look(0xFF5CD8FF, "experience_bottle", "SB_LEVEL");
        look(0xFFFFD166, "gold_ingot", "BANK");
        look(0xFFFFB627, "gold_nugget", "INTEREST");
        look(0xFF7FD4FF, "soul_lantern", "SOULFLOW");
        look(0xFFFFB454, "bone", "PET");
        look(0xFFFFB454, "lead", "PET_TRAINING", "PET_SITTER", "OLD_PET_SITTER");
        look(0xFFFF8F6B, "blaze_powder", "FIRE_SALE");
        look(0xFF5CD8FF, "paper", "ELECTION");
        look(0xFFC77DFF, "firework_rocket", "EVENT", "EVENT_TRACKERS");
        look(0xFFFFD166, "golden_pickaxe", "SKILLS", "DUNGEON_SKILLS_AND_STATS");
        look(0xFFFF6B6B, "iron_chestplate", "STATS", "DUNGEON_STATS_LINE");
        look(0xFFD8B26E, "bundle", "COLLECTION");
        look(0xFFD8B26E, "chest", "UNCLAIMED_CHESTS");
        look(0xFFFFB347, "gold_nugget", "JACOB_CONTEST", "AGATHA_CONTEST", "MIRIA_CONTEST");
        look(0xFFFF5252, "netherite_sword", "SLAYER");
        look(0xFF4FD8C4, "writable_book", "DAILY_QUESTS");
        look(0xFFF48FB1, "potion", "ACTIVE_EFFECTS");
        look(0xFF9CCC65, "book", "BESTIARY");
        look(0xFFC77DFF, "purple_dye", "ESSENCE");
        look(0xFFFF8F6B, "furnace", "FORGE");
        look(0xFF7FD4FF, "clock", "TIMERS", "JERRY_ISLAND_CLOSING");
        look(0xFFFF5D73, "wither_skeleton_skull", "DUNGEON_STATS", "DUNGEON_HUB_PROGRESS", "DUNGEON_PLAYER_STATS");
        look(0xFF7FB3FF, "ender_eye", "DUNGEON_PUZZLE", "PITY");
        look(0xFF5CD8FF, "filled_map", "DUNGEON_ACCOUNT_INFO_LINE");
        look(0xFFA5D6A7, "leather", "TRAPPER");
        look(0xFF4FD8C4, "iron_pickaxe", "COMMISSIONS");
        look(0xFF80D8FF, "iron_pickaxe", "PICKAXE_COOLDOWN");
        look(0xFF58C7FF, "prismarine_crystals", "POWDER");
        look(0xFFEC7DD8, "amethyst_shard", "CRYSTAL", "PRIVATE_ISLAND_CRYSTALS");
        look(0xFF4FC3F7, "water_bucket", "RAIN");
        look(0xFFFF8A65, "spider_eye", "BROODMOTHER");
        look(0xFFC77DFF, "ender_eye", "EYES_PLACED");
        look(0xFFC77DFF, "dragon_egg", "PROTECTOR");
        look(0xFFC77DFF, "dragon_head", "DRAGON");
        look(0xFFFF9F45, "fire_charge", "VOLCANO");
        look(0xFFFF6B4A, "blaze_powder", "REPUTATION");
        look(0xFFFF6B4A, "writable_book", "FACTION_QUESTS");
        look(0xFF4FC3F7, "tropical_fish", "TROPHY_FISH");
        look(0xFFD070FF, "ender_eye", "RIFT_INFO", "RIFT_SHEN", "RIFT_BARRY");
        look(0xFFB5D65F, "composter", "COMPOSTER");
        look(0xFF9CE37D, "wheat_seeds", "GARDEN_LEVEL");
        look(0xFFFF8F6B, "copper_ingot", "COPPER");
        look(0xFFD8B26E, "bone_meal", "SOWDUST");
        look(0xFFFF7A59, "silverfish_spawn_egg", "PESTS");
        look(0xFFFF7A59, "iron_bars", "PEST_TRAPS", "FULL_TRAPS", "NO_BAIT", "SHARD_TRAPS");
        look(0xFFFFC857, "villager_spawn_egg", "VISITORS");
        look(0xFF9CE37D, "wheat", "CROP_MILESTONE");
        look(0xFF9AD1FF, "skeleton_skull", "FROZEN_CORPSES");
        look(0xFFD8B26E, "iron_nugget", "SCRAP");
        look(0xFF4FD8C4, "beacon", "MOONGLADE_BEACON");
        look(0xFFB0BEC5, "sugar", "SALTS");
        look(0xFF81C784, "oak_sapling", "FOREST_WHISPERS");
        look(0xFFFFD166, "nether_star", "STARBORN_TEMPLE", "NORTH_STARS");
        look(0xFFD8B26E, "cobblestone", "MINION");

        rowIcon("filled_map", "area", "dungeon");
        rowIcon("repeater", "server");
        rowIcon("emerald", "gems", "social");
        rowIcon("ghast_tear", "fairy souls");
        rowIcon("name_tag", "profile");
        rowIcon("experience_bottle", "sb level");
        rowIcon("gold_ingot", "bank", "gold");
        rowIcon("gold_nugget", "interest");
        rowIcon("soul_lantern", "soulflow");
        rowIcon("copper_ingot", "copper");
        rowIcon("bone_meal", "sowdust");
        rowIcon("wheat_seeds", "garden level");
        rowIcon("fire_charge", "volcano");
        rowIcon("gunpowder", "ashfall");
        rowIcon("nether_star", "north stars");
        rowIcon("cobblestone", "minions");
        rowIcon("amethyst_shard", "crystals");
        rowIcon("firework_rocket", "event");
        rowIcon("paper", "election");
        rowIcon("lead", "kat", "hunting");
        rowIcon("cake", "party");
        rowIcon("water_bucket", "rain");
        rowIcon("lightning_rod", "thunder");
        rowIcon("spider_eye", "broodmother", "spider");
        rowIcon("ender_eye", "eyes placed");
        rowIcon("dragon_egg", "protector");
        rowIcon("dragon_breath", "dragon");
        rowIcon("iron_bars", "pest traps", "full traps", "no bait");
        rowIcon("villager_spawn_egg", "visitors");
        rowIcon("golden_hoe", "farming");
        rowIcon("iron_pickaxe", "mining");
        rowIcon("stone_sword", "combat");
        rowIcon("oak_sapling", "foraging", "forest", "organic matter");
        rowIcon("fishing_rod", "fishing");
        rowIcon("enchanted_book", "enchanting");
        rowIcon("brewing_stand", "alchemy");
        rowIcon("bone", "taming");
        rowIcon("crafting_table", "carpentry");
        rowIcon("magma_cream", "runecrafting");
        rowIcon("wither_skeleton_skull", "catacombs", "dungeoneering");
        rowIcon("blaze_rod", "mage");
        rowIcon("iron_chestplate", "tank");
        rowIcon("iron_sword", "berserk");
        rowIcon("bow", "archer");
        rowIcon("potion", "healer");
        rowIcon("wheat", "wheat");
        rowIcon("carrot", "carrot");
        rowIcon("potato", "potato");
        rowIcon("pumpkin", "pumpkin");
        rowIcon("melon_slice", "melon", "melon slice");
        rowIcon("red_mushroom", "mushroom");
        rowIcon("cactus", "cactus");
        rowIcon("sugar_cane", "sugar cane");
        rowIcon("nether_wart", "nether wart");
        rowIcon("cocoa_beans", "cocoa beans");
        rowIcon("sunflower", "sunflower");
        rowIcon("blue_orchid", "moonflower");
        rowIcon("rose_bush", "wild rose");
        rowIcon("wither_rose", "wither");
        rowIcon("rotten_flesh", "undead");
        rowIcon("diamond", "diamond");
        rowIcon("ice", "ice");
        rowIcon("crimson_fungus", "crimson");
        rowIcon("nautilus_shell", "fossil");
        rowIcon("prismarine_crystals", "mithril");
        rowIcon("pink_dye", "gemstone");
        rowIcon("packed_ice", "glacite");
        rowIcon("furnace", "fuel");
        rowIcon("clock", "time left", "starts in");
        rowIcon("composter", "stored compost");

        rowColor(0xFFE8C547, "farming", "wheat", "sunflower");
        rowColor(0xFF5CD8FF, "mining", "moonflower");
        rowColor(0xFFFF6B6B, "combat", "mushroom", "nether wart");
        rowColor(0xFF5FEA8C, "foraging", "cactus");
        rowColor(0xFF4FC3F7, "fishing");
        rowColor(0xFFC77DFF, "enchanting");
        rowColor(0xFFFF9EC7, "alchemy", "wild rose");
        rowColor(0xFFFFB454, "taming", "carrot", "pumpkin");
        rowColor(0xFFD8B26E, "carpentry", "potato");
        rowColor(0xFFEC7DD8, "runecrafting");
        rowColor(0xFF9CE37D, "social", "melon", "sugar cane");
        rowColor(0xFFA5D6A7, "hunting");
        rowColor(0xFFFF5D73, "catacombs", "dungeoneering");
        rowColor(0xFFC48A5A, "cocoa beans");
    }

    private static final Pattern LABEL_NOISE = Pattern.compile("^[^\\p{L}]+|[\\s\\d]+$|\\s+[ivx]+$");

    // "Mining 47", "○ Potato", "Wheat VII" -> plain name
    private static String rowKey(String label) {
        return LABEL_NOISE.matcher(RowText.plain(label).trim().toLowerCase(Locale.ROOT)).replaceAll("").trim();
    }

    private static ItemStack rowIconFor(String label) {
        String id = ROW_ICONS.get(rowKey(label));
        return id == null ? null : LegacyItems.stack(id);
    }

    private static Looks.Look lookOf(Widget w) {
        if (w.key.equals("REPUTATION")) {
            boolean mage = w.title.startsWith("Mage");
            return new Looks.Look(mage ? 0xFF7FB3FF : 0xFFFF6B4A, mage ? "minecraft:enchanted_book" : "minecraft:iron_axe");
        }
        Looks.Look l = LOOKS.get(w.key);
        return l != null ? l : new Looks.Look(Looks.colourFor(w.key), "minecraft:paper");
    }

    // --- a card as shown ---

    private static final int M_GAP = 4, CARD_MAX = 236, CARD_MIN = 118, SUB_GAP = 9, GUTTER = 12, MOST_SPREAD = 2, PET_H = 24, PET_BARE_H = 18, HEAD = 10, SLIM_MIN = 120, PET_TEXT = 20;
    // cards of short readings, side by side when theres lots of lines and room
    private static final Set<String> GRID = Set.of("STATS", "ESSENCE", "TROPHY_FISH", "VISITORS", "DUNGEON_STATS_LINE", "DUNGEON_PLAYER_STATS", "MINION");
    private static final int GRID_FROM = 6, MOST_SUB_COLUMNS = 4, FEWEST_IN_SUB_COLUMN = 6;
    private static final int RULE_COLOR = 0x1CFFFFFF;

    private enum Kind {TEXT, RULE, PLAYER, PET}

    private record Row(Kind kind, ItemStack icon, String label, String value, double progress, int bar, Line line, TabColumns.Player player, TabColumns.Pet pet) {
        static final Row RULE = new Row(Kind.RULE, null, "", "", -1, 0, null, null, null);

        static Row text(ItemStack icon, String label, String value, double progress, int bar, Line line) {
            return new Row(Kind.TEXT, icon, label, value, progress, bar, line, null, null);
        }

        int height() {
            return switch (kind) {
                case RULE -> Modern.RULE_BLOCK;
                case PET -> pet.xp().isEmpty() ? PET_BARE_H : pet.toNext().isEmpty() ? PET_H : PET_H + Modern.ROW;
                default -> Modern.ROW + (progress >= 0 ? Modern.METER_BLOCK : 0);
            };
        }
    }

    private static final class View {
        final Widget w;
        final int accent;
        final ItemStack icon;
        final String title;
        String right;
        double headProgress;
        final List<Row> rows = new ArrayList<>();
        boolean gutter;
        int badge, players;
        String statLabel = "", statValue = "";
        // player level colors low to high, color + count. the lists makeup at a glance
        final List<int[]> levelMix = new ArrayList<>();
        int mixTotal;
        int cols = 1, colW, width, height;

        View(Widget w) {
            this.w = w;
            Looks.Look look = lookOf(w);
            accent = look.accent();
            icon = look.stack();
            TabColumns.Title t = titleOf(w);
            title = t.name().isEmpty() ? w.title : t.name();
            right = t.right();
            headProgress = t.progress();
            build();
            measure();
        }

        private void build() {
            List<Line> lines = w.lines;
            int from = 0;
            if (w.key.equals("DUNGEON_STATS") && w.head != null) {
                lines = new ArrayList<>(lines);
                lines.addFirst(w.head);
            }
            if (w.key.equals("PET") && !lines.isEmpty()) {
                TabColumns.Pet pet = petOf(lines);
                if (pet != null) {
                    rows.add(new Row(Kind.PET, petHead(pet), "", "", pet.progress(), 0, lines.get(0), null, pet));
                    if (right.isEmpty()) right = rarityWord(pet.name());
                    from = !pet.xp().isEmpty() ? 2 : 1;
                }
            }
            boolean levels = false;
            for (int i = from; i < lines.size(); i++) {
                Line l = lines.get(i);
                if (l.gapBefore && !rows.isEmpty() && rows.getLast().kind != Kind.RULE) rows.add(Row.RULE);
                TabColumns.Player p = l.level >= 0 ? playerOf(l) : null;
                if (p != null) {
                    levels = true;
                    players++;
                    rows.add(new Row(Kind.PLAYER, null, "", "", -1, 0, l, p, null));
                    continue;
                }
                TabColumns.Shown s = shownOf(w.key, l);
                if (RowText.plain(s.label()).isBlank() && s.value().isEmpty()) {
                    if (!rows.isEmpty() && rows.getLast().kind != Kind.RULE) rows.add(Row.RULE);
                    continue;
                }
                // a bar under a number belongs to it: "5,410" + "||||| (77.3%)" = one reading
                if (RowText.plain(s.label()).isBlank() && s.progress() >= 0 && !rows.isEmpty()) {
                    Row before = rows.getLast();
                    if (before.kind == Kind.TEXT && before.value.isEmpty() && before.progress < 0) {
                        rows.set(rows.size() - 1, Row.text(before.icon, before.label, s.value(), s.progress(), 0, before.line));
                        continue;
                    }
                }
                String label = s.value().isEmpty() ? s.label() : quietLabel(s.label());
                if (s.progress() >= 0) label = brightLevel(label);
                rows.add(Row.text(rowIconFor(s.label()), label, s.value(), s.progress(), ROW_COLORS.getOrDefault(rowKey(s.label()), 0), l));
            }
            while (!rows.isEmpty() && rows.getLast().kind == Kind.RULE) rows.removeLast();
            int texts = 0, withIcon = 0;
            for (Row r : rows) {
                if (r.kind != Kind.TEXT) continue;
                texts++;
                if (r.icon != null) withIcon++;
            }
            gutter = withIcon > 0 && withIcon * 2 >= texts;
            if (levels && w.key.equals("PLAYER_LIST")) levelStats(); // a partys few levels average to nothing
            countDone();
        }

        // level stats, only for a list long enough to mean something
        private void levelStats() {
            List<TabColumns.Player> list = new ArrayList<>();
            for (Row r : rows) if (r.player != null) list.add(r.player);
            if (list.size() < 3) return;
            list.sort(Comparator.comparingInt(TabColumns.Player::level));
            int sum = 0;
            for (TabColumns.Player p : list) {
                sum += p.level();
                int c = rgbOf(p.code());
                if (!levelMix.isEmpty() && levelMix.getLast()[0] == c) levelMix.getLast()[1]++;
                else levelMix.add(new int[]{c, 1});
            }
            mixTotal = list.size();
            TabColumns.Player top = list.getLast();
            statLabel = "Average level";
            statValue = "§f" + Math.round(sum / (double) list.size()) + "  §7top §" + top.code() + top.level();
        }

        // "✔ Ashfang", "Goblin Slayer: DONE" = how far along, so the band says it too
        private void countDone() {
            if (!right.isEmpty()) return;
            int done = 0, total = 0;
            for (Row r : rows) {
                if (r.kind != Kind.TEXT) continue;
                String p = RowText.plain(r.label).trim();
                if (w.key.equals("COMMISSIONS")) {
                    total++;
                    if (r.progress >= 1) done++;
                } else if (p.startsWith("✔")) {
                    total++;
                    done++;
                } else if (p.startsWith("✖")) total++;
            }
            if (total == 0) return;
            headProgress = done / (double) total;
            right = Modern.fraction(Integer.toString(done), Integer.toString(total), done >= total ? 'a' : 'e', 'f') + "  §7" + RowText.percent(headProgress);
        }

        private int rowWidth(Row r) {
            return switch (r.kind) {
                case RULE -> 0;
                case PLAYER -> (heads() ? HEAD : 0) + badge + 3 + ui.width(r.player.name());
                case PET -> {
                    TabColumns.Pet p = r.pet;
                    int top = ui.width(p.name()) + 8 + ui.width(levelText(p));
                    int bottom = ui.width(p.xp()) + (p.percent().isEmpty() ? 0 : 8 + ui.width(p.percent()));
                    int next = p.toNext().isEmpty() ? 0 : ui.width(toNextLabel(p)) + 8 + ui.width(toNextValue(p));
                    yield PET_TEXT + Math.max(next, Math.max(top, bottom));
                }
                case TEXT -> (gutter ? GUTTER : 0) + ui.width(r.label) + (r.value.isEmpty() ? 0 : 8 + ui.width(r.value) + (TabColumns.isState(r.value) ? 6 : 0));
            };
        }

        private void measure() {
            badge = 0;
            if (optBool("showLevel", true)) for (Row r : rows) if (r.player != null) badge = Math.max(badge, ui.width(Integer.toString(r.player.level())) + 4);
            colW = 0;
            for (Row r : rows) colW = Math.max(colW, rowWidth(r));
            if (GRID.contains(w.key) && rows.size() >= GRID_FROM && 2 * colW + SUB_GAP + 2 * PAD <= CARD_MAX) cols = 2;
            size();
        }

        // how many sub-columns the players can use
        int mostColumns() {
            return Math.max(1, Math.min(MOST_SUB_COLUMNS, rows.size() / FEWEST_IN_SUB_COLUMN));
        }

        void columns(int n) {
            cols = n;
            size();
        }

        private void size() {
            width = cols * colW + (cols - 1) * SUB_GAP + 2 * PAD;
            if (cols == 1) width = Math.min(CARD_MAX, width);
            int stat = statValue.isEmpty() ? 0 : ui.width(statLabel) + 8 + ui.width(statValue) + 2 * PAD;
            int band = 2 + Modern.ICON + 2 + ui.width(title) + (right.isEmpty() ? 0 : 8 + ui.width(right)) + PAD;
            width = Math.max(width, Math.max(stat, Math.max(band, CARD_MIN)));
            width += width & 1;
            int tall = 0;
            for (List<Row> c : subColumns()) {
                int h = 0;
                for (Row r : c) h += r.height();
                tall = Math.max(tall, h);
            }
            height = Modern.BAND + 4 + tall + (headProgress >= 0 ? Modern.METER_BLOCK : 0) + (statValue.isEmpty() ? 0 : Modern.RULE_BLOCK + Modern.ROW);
            height += height & 1;
        }

        // rows per sub-column, top to bottom, left to right
        List<List<Row>> subColumns() {
            List<List<Row>> out = new ArrayList<>();
            int per = (rows.size() + cols - 1) / Math.max(1, cols);
            for (int i = 0; i < cols; i++) out.add(rows.subList(Math.min(rows.size(), i * per), Math.min(rows.size(), (i + 1) * per)));
            return out;
        }

        // how many rows share the extra room this card got
        int spreadRows() {
            return (rows.size() + cols - 1) / Math.max(1, cols);
        }
    }

    private static boolean heads() {
        return optBool("heads", true);
    }

    // this comes from the server. one weird line breaks that line, never the whole list

    private static TabColumns.Title titleOf(Widget w) {
        try {
            // this widgets first line is its first reading not its name
            if (w.key.equals("DUNGEON_STATS")) return new TabColumns.Title("Dungeon", "", -1);
            if (w.head != null) return TabColumns.title(w.head.coded);
        } catch (RuntimeException ignored) {
        }
        return new TabColumns.Title(w.title, "", -1);
    }

    private static TabColumns.Pet petOf(List<Line> lines) {
        try {
            TabColumns.Pet pet = TabColumns.pet(lines.get(0).coded, lines.size() > 1 ? lines.get(1).coded : null);
            return pet != null || lines.size() < 2 ? pet : TabColumns.pet(lines.get(0).coded, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static TabPlayerNames.Wanted wantedNames() {
        return new TabPlayerNames.Wanted(optBool("hideEmblem", false), optBool("hideFaction", false), optBool("plainNames", false), optBool("bingoNumber", false));
    }

    private static TabPlayerNames.People people() {
        var user = net.minecraft.client.Minecraft.getInstance().getUser();
        return new TabPlayerNames.People(user == null ? "" : user.getName(), io.github.zmuzy.sbc.chat.MarkedPlayers.marked(), io.github.zmuzy.sbc.chat.Party.members(),
                io.github.zmuzy.sbc.commands.PlayerLists.friends(false), io.github.zmuzy.sbc.commands.PlayerLists.guild(), io.github.zmuzy.sbc.chat.MarkedPlayers.code());
    }

    private static TabColumns.Player playerOf(Line l) {
        try {
            TabColumns.Player p = TabColumns.player(l.coded);
            if (p == null || "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island)) return p;
            String name = TabPlayerNames.rewrite(p.name(), wantedNames(), "CRIMSON_ISLE".equals(GameState.island));
            if (optBool("markPeople", false)) name = TabPlayerNames.marked(name, people());
            return new TabColumns.Player(p.level(), p.code(), name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static TabColumns.Shown shownOf(String id, Line l) {
        try {
            return TabColumns.shown(id, l.coded);
        } catch (RuntimeException e) {
            return new TabColumns.Shown(RowText.trimStart(l.coded), "", -1);
        }
    }

    private static ItemStack petHead(TabColumns.Pet pet) {
        String name = RowText.plain(pet.name()).replaceAll("[^\\p{L} ]", "").trim();
        try {
            return ItemIcons.pet(name.toUpperCase(Locale.ROOT).replace(' ', '_'), java.util.Objects.requireNonNullElse(rarityId(pet.name()), "COMMON"));
        } catch (Exception e) {
            return LegacyItems.stack("minecraft:bone");
        }
    }

    // pet rarity = color of its name
    private static String rarityId(String coded) {
        return switch (firstCode(coded)) {
            case 'a' -> "UNCOMMON";
            case '9' -> "RARE";
            case '5' -> "EPIC";
            case '6' -> "LEGENDARY";
            case 'd' -> "MYTHIC";
            case 'f' -> "COMMON";
            default -> null;
        };
    }

    // rarity word in the name color, nothing if the line had no color
    private static String rarityWord(String coded) {
        char code = firstCode(coded);
        if (code == 0) return "";
        String id = rarityId(coded);
        if (id == null) return "";
        return "§" + code + id.charAt(0) + id.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String levelText(TabColumns.Pet p) {
        return "§7Lvl §f" + p.level();
    }

    private static String toNextLabel(TabColumns.Pet p) {
        return "To Lvl " + (p.level() + 1);
    }

    private static String toNextValue(TabColumns.Pet p) {
        return "§f" + p.toNext() + " §7XP";
    }

    // --- where everything goes ---

    // what this refresh looks like at this screen size. worked out once, drawn every frame
    private record Plan(int sw, int sh, float size, List<String> head, List<String> foot, List<View> views, List<List<Integer>> layout) {
        int headH() {
            return slimHeight(head);
        }

        int footH() {
            return slimHeight(foot);
        }

        int total() {
            int total = -M_GAP;
            for (List<Integer> c : layout) total += columnWidth(c) + M_GAP;
            return total;
        }

        int tallest() {
            int tallest = 0;
            for (List<Integer> c : layout) tallest = Math.max(tallest, columnHeight(c));
            return tallest;
        }

        int columnWidth(List<Integer> c) {
            int w = 0;
            for (int i : c) w = Math.max(w, views.get(i).width);
            return w;
        }

        int columnHeight(List<Integer> c) {
            int h = -M_GAP;
            for (int i : c) h += views.get(i).height + M_GAP;
            return h;
        }

        int fullW() {
            return Math.max(total(), Math.max(head.isEmpty() ? 0 : slimWidth(head), foot.isEmpty() ? 0 : slimWidth(foot)));
        }

        int fullH() {
            return headH() + (headH() > 0 ? M_GAP : 0) + tallest() + (footH() > 0 ? M_GAP : 0) + footH();
        }

        float scale() {
            return Math.min(Config.I.tabScale, Math.min((sw - 16) / (float) fullW(), (sh - 20) / (float) fullH()));
        }
    }

    private static Plan plan;

    private static List<List<Integer>> layoutOf(List<View> views, int maxW, int maxH) {
        int[] hs = new int[views.size()], ws = new int[views.size()];
        for (int i = 0; i < hs.length; i++) {
            hs[i] = views.get(i).height;
            ws[i] = views.get(i).width;
        }
        return TabColumns.largest(hs, ws, M_GAP, maxW, maxH, optInt("columns", 0), !Config.I.tabOrder.isEmpty());
    }

    // tries the player card at each number of sub-columns: as big as possible, then not crowded, then least room
    private static Plan planFor(int sw, int sh) {
        float size = Math.max(0.25f, Config.I.tabScale);
        if (plan != null && plan.sw == sw && plan.sh == sh && plan.size == size) return plan;
        List<String> head = optBool("header", true) ? codedLines(header) : List.of(), foot = optBool("footer", true) ? codedLines(footer) : List.of();
        if (optBool("hideAdverts", true)) foot = foot.stream().filter(s -> !advert(GameState.strip(s))).toList();
        List<View> views = new ArrayList<>();
        View players = null;
        for (Widget w : ordered()) {
            View v = new View(w);
            views.add(v);
            if (w.key.equals("PLAYER_LIST")) players = v;
        }
        int maxW = (int) ((sw - 16) / size);
        head = wrapped(head, maxW - 16);
        foot = wrapped(foot, maxW - 16);
        int maxH = (int) ((sh - 20) / size) - slimHeight(head) - slimHeight(foot) - (head.isEmpty() ? 0 : M_GAP) - (foot.isEmpty() ? 0 : M_GAP);
        Plan best = null;
        int bestColumns = 1, bestArea = 0;
        boolean bestEasy = false;
        int most = players == null ? 1 : players.mostColumns();
        for (int n = 1; n <= most; n++) {
            if (players != null) players.columns(n);
            Plan p = new Plan(sw, sh, size, head, foot, views, layoutOf(views, maxW, maxH));
            float scale = p.scale(), bestScale = best == null ? -1 : best.scale();
            boolean easy = TabColumns.comfortable(p.tallest(), maxH);
            int area = p.fullW() * p.fullH();
            if (scale > bestScale + 0.001f || (scale > bestScale - 0.001f && (easy && !bestEasy || easy == bestEasy && area < bestArea))) {
                best = p;
                bestColumns = n;
                bestEasy = easy;
                bestArea = area;
            }
        }
        if (players != null) players.columns(bestColumns);
        plan = best;
        return plan;
    }

    // --- drawing the cards ---

    private static int meterColor(Row r, int accent) {
        if (r.progress >= 1) return Modern.GOOD;
        if (r.bar != 0) return r.bar;
        int c = firstVivid(r.value);
        if (c == 0) c = firstVivid(r.label);
        return c != 0 ? c : accent;
    }

    // a state as a small tag, right end at x, returns left end. fits a 10px row, 2 of them too
    private static int tag(String value, int right, int y) {
        String text = RowText.plain(value).trim();
        char code = firstCode(value);
        int c = code == 0 ? Modern.DIM : rgbOf(code);
        int w = ui.width(text) + 6, x = right - w;
        ui.rect(x, y - 1, w, 9, Ui.alpha(c | 0xFF000000, 0.26f));
        ui.textShadow(text, x + 3, y, Modern.lighten(c | 0xFF000000, 0.25f));
        return x;
    }

    private static void drawHead(GuiGraphicsExtractor g, Row r, int x, int y) {
        if (r.line.face != null) {
            try {
                var id = r.line.face.getSkin().body().texturePath();
                g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 8f, 8f, 8, 8, 8, 8, 64, 64);
                g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 40f, 8f, 8, 8, 8, 8, 64, 64);
            } catch (Exception ignored) {
            }
        } else if (standIns) {
            String name = RowText.plain(r.player.name());
            int hair = 0xFF000000 | Looks.colourFor(name) & 0xFFFFFF, eye = (name.hashCode() & 1) == 0 ? 0xFF2B4B8C : 0xFF3A2A1C;
            ui.rect(x, y, 8, 8, 0xFFC99A74);
            ui.rect(x, y, 8, 3, hair);
            ui.rect(x, y + 3, 1, 2, hair);
            ui.rect(x + 7, y + 3, 1, 2, hair);
            ui.rect(x + 2, y + 4, 1, 1, eye);
            ui.rect(x + 5, y + 4, 1, 1, eye);
            ui.rect(x + 3, y + 6, 2, 1, 0xFF8A5A44);
        }
    }

    private static String ownName() {
        var p = Minecraft.getInstance().player;
        return p != null ? p.getGameProfile().name() : standIns ? "Steve" : "";
    }

    private static void drawPlayer(GuiGraphicsExtractor g, View v, Row r, int x, int y, int w, String self) {
        TabColumns.Player p = r.player;
        String plainName = RowText.plain(p.name());
        if (!self.isEmpty() && (plainName.equals(self) || plainName.startsWith(self + " "))) {
            int a = v.accent | 0xFF000000;
            ui.gradH(x - 2, y - 1, w + 4, 10, Ui.alpha(a, 0.26f), Ui.alpha(a, 0.02f));
        }
        int tx = x;
        if (heads()) {
            drawHead(g, r, tx, y);
            tx += HEAD;
        }
        int c = rgbOf(p.code());
        if (c == 0) c = Modern.INK;
        String level = Integer.toString(p.level());
        if (v.badge > 0) {
            ui.rect(tx, y - 1, v.badge, 9, Ui.alpha(c, 0.18f));
            ui.textShadow(level, tx + (v.badge - ui.width(level)) / 2, y, c);
            tx += v.badge + 3;
        }
        print(fit(p.name(), x + w - tx), tx, y, Modern.INK);
    }

    private static void drawPet(View v, Row r, int x, int y, int w) {
        TabColumns.Pet p = r.pet;
        ui.icon(r.icon, x, y + 1, 1f);
        int tx = x + PET_TEXT, tw = w - PET_TEXT;
        row(p.name(), levelText(p), tx, y, tw, Modern.INK, Modern.INK);
        if (p.xp().isEmpty()) return;
        row(p.xp(), p.percent(), tx, y + Modern.ROW, tw, Modern.LABEL, Modern.INK);
        int bar = firstVivid(p.name());
        Modern.meter(ui, tx, y + 2 * Modern.ROW - 1, tw, Modern.METER, Math.max(0, p.progress()), p.progress() >= 1 ? Modern.GOOD : bar != 0 ? bar : v.accent);
        if (!p.toNext().isEmpty()) row(toNextLabel(p), toNextValue(p), tx, y + PET_H, tw, Modern.LABEL, Modern.INK);
    }

    private static void drawText(View v, Row r, int x, int y, int w) {
        int lx = x, lw = w;
        if (v.gutter) {
            if (r.icon != null) ui.icon(r.icon, lx - 1, y - 1, 0.6f);
            lx += GUTTER;
            lw -= GUTTER;
        }
        if (TabColumns.isState(r.value)) {
            int left = tag(r.value, lx + lw, y);
            print(fit(r.label, left - lx - 4), lx, y, Modern.LABEL);
        } else row(r.label, r.value, lx, y, lw, Modern.LABEL, Modern.INK);
        if (r.progress >= 0) Modern.meter(ui, x, y + Modern.ROW - 1, w, Modern.METER, r.progress, meterColor(r, v.accent));
    }

    private static void drawCard(GuiGraphicsExtractor g, View v, int x, int y, int w, int h, int spread, String self) {
        if (!optBool("noBackground", false)) Theme.current().panel(ui, x, y, w, h);
        int rw = v.right.isEmpty() ? 0 : ui.width(v.right);
        int top = y + Modern.band(ui, fit(v.title, w - 2 - Modern.ICON - 2 - PAD - (rw == 0 ? 0 : rw + 6)), v.icon, "", v.accent, x, y, w);
        if (rw > 0) print(v.right, x + w - PAD - rw, y + 3, Modern.INK);
        int cx = x + PAD, cw = w - 2 * PAD;
        if (v.headProgress >= 0) {
            Modern.meter(ui, cx, top - 1, cw, Modern.METER, v.headProgress, v.headProgress >= 1 ? Modern.GOOD : v.accent);
            top += Modern.METER_BLOCK;
        }
        int statH = v.statValue.isEmpty() ? 0 : Modern.RULE_BLOCK + Modern.ROW;
        int colW = (cw - (v.cols - 1) * SUB_GAP) / v.cols, sx = cx;
        List<List<Row>> subs = v.subColumns();
        for (List<Row> rows : subs) {
            int ry = top;
            for (Row r : rows) {
                switch (r.kind) {
                    case RULE -> Modern.rule(ui, sx, ry + 2, colW);
                    case PLAYER -> drawPlayer(g, v, r, sx, ry, colW, self);
                    case PET -> drawPet(v, r, sx, ry, colW);
                    case TEXT -> drawText(v, r, sx, ry, colW);
                }
                ry += r.height() + spread;
            }
            sx += colW + SUB_GAP;
        }
        for (int c = 1; c < subs.size(); c++) ui.rect(cx + c * (colW + SUB_GAP) - (SUB_GAP + 1) / 2, top - 1, 1, y + h - top - 2 - statH, RULE_COLOR);
        if (statH > 0) {
            int sy = y + h - 2 - Modern.ROW, done = 0;
            for (int[] part : v.levelMix) {
                int from = cx + done * cw / v.mixTotal;
                done += part[1];
                ui.rect(from, sy - 4, cx + done * cw / v.mixTotal - from, Modern.METER, part[0]);
            }
            row(v.statLabel, v.statValue, cx, sy, cw, Modern.LABEL, Modern.INK);
        }
    }

    private static int slimHeight(List<String> lines) {
        return lines.isEmpty() ? 0 : lines.size() * Modern.ROW + 4;
    }

    private static int slimWidth(List<String> lines) {
        int w = SLIM_MIN;
        for (String s : lines) w = Math.max(w, ui.width(s) + 16);
        return w + (w & 1);
    }

    // fps box next to the header. green 100+, yellow 50+, red below
    private static void drawFps(int x, int y) {
        if (!optBool("fps", true)) return;
        int fps = Minecraft.getInstance().getFps();
        String s = "§7FPS §" + (fps >= 100 ? "a" : fps >= 50 ? "e" : "c") + fps;
        int w = ui.width(s) + 12, h = slimHeight(List.of(s));
        Theme.current().panel(ui, x, y, w, h);
        print(s, x + 6, y + 3, Modern.INK);
    }

    // header/footer only as wide as its text, color line on the edge facing the cards
    private static void drawSlim(List<String> lines, int y, int accent, boolean top) {
        int w = slimWidth(lines), h = slimHeight(lines), x = -w / 2, a = accent | 0xFF000000, half = w / 2 - 1, ey = top ? y + h - 1 : y;
        Theme.current().panel(ui, x, y, w, h);
        ui.gradH(x + 1, ey, half, 1, Ui.alpha(a, 0.02f), Ui.alpha(a, 0.95f));
        ui.gradH(0, ey, half, 1, Ui.alpha(a, 0.95f), Ui.alpha(a, 0.02f));
        for (int i = 0; i < lines.size(); i++) {
            String s = fit(lines.get(i), w - 8);
            print(s, -ui.width(s) / 2, y + 3 + i * Modern.ROW, Modern.INK);
        }
    }

    private static void drawModern(GuiGraphicsExtractor g, int sw, int sh) {
        Plan p = planFor(sw, sh);
        float scale = p.scale();
        int total = p.total(), tallest = p.tallest();
        cards.clear();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(sw / 2f, 10f);
        pose.scale(scale, scale);
        int y = 0;
        if (p.headH() > 0) {
            drawSlim(p.head, y, Modern.GOLD, true);
            drawFps(slimWidth(p.head) / 2 + M_GAP, y);
            y += p.headH() + M_GAP;
        } else drawFps(total / 2 + M_GAP, y);
        String self = ownName();
        int x = -total / 2;
        for (List<Integer> col : p.layout) {
            int w = p.columnWidth(col), wy = y;
            // leftover room in this column goes to its rows evenly so all columns end on the same line
            int spare = tallest - p.columnHeight(col), rows = 0;
            for (int i : col) rows += p.views.get(i).spreadRows();
            int spread = rows == 0 ? 0 : Math.min(MOST_SPREAD, spare / rows), left = spare - spread * rows, n = col.size(), index = 0;
            for (int i : col) {
                index++;
                View v = p.views.get(i);
                int h = v.height + spread * v.spreadRows() + left / n + (index == n ? left % n : 0);
                drawCard(g, v, x, wy, w, h, spread, self);
                cards.add(new Card(v.w.key, v.w.title, Math.round(sw / 2f + x * scale), Math.round(10f + wy * scale), Math.round(w * scale), Math.round(h * scale)));
                wy += h + M_GAP;
            }
            x += w + M_GAP;
        }
        y += tallest + M_GAP;
        if (p.footH() > 0) drawSlim(p.foot, y, Modern.AQUA, false);
        pose.popMatrix();
    }

    // --- old list path ---

    private static int widthOf(Widget w) {
        int width = 80;
        boolean heads = optBool("heads", true);
        for (Line l : w.lines) {
            int tw = ui.font.width(l.text) + (l.player != null && heads ? 11 : 0) + 2;
            width = Math.max(width, Math.min(180, tw + 2 * PAD));
        }
        width = Math.max(width, Math.min(180, ui.width(w.title) + 2 * PAD + 4));
        return width;
    }

    private static int heightOf(Widget w) {
        return titleH() + w.lines.size() * LINE + 2 * PAD;
    }

    private static int titleH() {
        return Theme.current().titleHeight(LINE);
    }

    private static List<String> lines(Component c) {
        List<String> out = new ArrayList<>();
        if (c == null) return out;
        for (String s : c.getString().split("\n")) if (!GameState.strip(s).isBlank()) out.add(s);
        return out;
    }

    // cards in your order, new ones after. hypixels order until you move any
    private static List<Widget> ordered() {
        List<String> order = Config.I.tabOrder;
        if (order.isEmpty()) return widgets;
        List<Widget> out = new ArrayList<>(widgets);
        out.sort(Comparator.comparingInt(w -> {
            int i = order.indexOf(w.key);
            return i < 0 ? Integer.MAX_VALUE : i;
        }));
        return out;
    }

    // spread cards over columns as even as possible
    private static List<List<Widget>> columns(int maxW, int maxH) {
        List<Widget> list = ordered();
        int[] heights = new int[list.size()], widths = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            heights[i] = heightOf(list.get(i));
            widths[i] = widthOf(list.get(i));
        }
        List<List<Widget>> cols = new ArrayList<>();
        for (List<Integer> c : TabColumns.arrange(heights, widths, GAP, maxW, maxH, optInt("columns", 0), !Config.I.tabOrder.isEmpty())) {
            List<Widget> col = new ArrayList<>();
            for (int i : c) col.add(list.get(i));
            cols.add(col);
        }
        return cols;
    }

    // cards as drawn last, for the editor
    public static List<Card> cards() {
        return cards;
    }

    // editor: put a card before another. whats on screen now becomes your order
    public static void moveBefore(String key, String before) {
        List<String> order = new ArrayList<>();
        for (Card c : cards) if (!c.key.equals(key)) order.add(c.key);
        int at = order.indexOf(before);
        order.add(at < 0 ? order.size() : at, key);
        Config.I.tabOrder = order;
        plan = null;
    }

    public static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        ui.begin(g, -1, -1);
        refresh(mc);
        if (widgets.isEmpty()) return;
        ui.textLimit = Integer.MAX_VALUE;
        int sw = g.guiWidth(), sh = g.guiHeight();
        drawModern(g, sw, sh);
        return;
    }

    public static void register() {
        Features.add(new Feature("tablist", "HUD", "Custom tab list", "Hold TAB for the player list as boxes per widget. Choose the widgets, the columns, adverts and player sorting here; which widgets Hypixel sends is its own /tab setting.", "")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.integer("tablist.columns", "Columns (0 = as many as fit)", "", 0, 0, 6))
                .option(Option.bool("tablist.hideAdverts", "Hide adverts and fire sales", "", true))
                .option(Option.bool("tablist.heads", "Player heads", "", true))
                .option(Option.bool("tablist.header", "Show the header", "", true))
                .option(Option.bool("tablist.footer", "Show the footer", "", true))
                .option(Option.bool("tablist.fps", "FPS beside the header", "Your frame rate in a small box: green from 100, yellow from 50, red below.", true))
                .option(Option.choice("tablist.sortPlayers", "Sort players by", "", "Hypixel", "Hypixel", "Level", "Name"))
                .option(Option.bool("tablist.showLevel", "SkyBlock level beside a player", "", true))
                .option(Option.bool("tablist.plainNames", "Names without their rank's colour", "", false))
                .option(Option.bool("tablist.hideEmblem", "No emblems behind names", "The ironman and bingo marks stay.", false))
                .option(Option.bool("tablist.hideFaction", "No Crimson Isle faction behind names", "", false))
                .option(Option.bool("tablist.bingoNumber", "Number beside a bingo rank", "With emblems off, for who does not know the icons.", false))
                .option(Option.bool("tablist.markPeople", "Mark people you know", "Behind the name: MARKED, P for your party, F for a friend, G for your guild. Friends and guild come from /f list and /g list.", false))
                .option(Option.bool("tablist.noBackground", "No background behind the cards", "", false)));
    }
}
