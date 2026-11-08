// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// heart of the mountain. from skyhanni HotmData + HotxFeatures (perks colored by on/off, tokens on the heart,
// todays sky mall from chat or the tree, on a card)
// and PowderPerHotmPerk + CurrencyPerHotxPerk (what a perk cost so far and still costs, in its tooltip)
// perk levels on the items use the older slot text "slot_text.hotm"
public final class HotmPerks {
    static final String ID = "mining_hotm", MALL = "mining_sky_mall";
    static final String HEART = "Heart of the Mountain";

    // skyhanni HotmData: a perk that costs powder per level
    record Perk(String name, int max, double add, double exponent, Powder.Type powder) {
        double cost(int level) {
            return Math.pow(level + add, exponent);
        }

        // skyhanni calculateTotalCost: levels 2 up to the wanted one, first is free
        long total(int level) {
            long sum = 0;
            for (int l = 2; l <= level; l++) sum += (int) cost(l);
            return sum;
        }
    }

    private static final Map<String, Perk> COSTS = new LinkedHashMap<>();
    // the on/off or token perks, rest of HotmData. all named in the tree too
    private static final List<String> OTHERS = List.of("Sky Mall", "Precision Mining", "Front Loaded", "Daily Grind", "Daily Powder", "Pickobulus", "Mining Speed Boost", "Maniac Miner", "Sheer Force", "Tunnel Vision", "Core of the Mountain", "Mineshaft Mayhem", "Gemstone Infusion", "Miner's Blessing");

    private static void perk(Powder.Type t, String name, int max, double exponent) {
        COSTS.put(name, new Perk(name, max, 1, exponent, t));
    }

    static {
        Powder.Type m = Powder.Type.MITHRIL, g = Powder.Type.GEMSTONE, c = Powder.Type.GLACITE;
        perk(m, "Mining Speed", 50, 3.0);
        perk(m, "Mining Fortune", 50, 3.05);
        perk(m, "Titanium Insanium", 50, 3.1);
        perk(m, "Luck of the Cave", 45, 3.07);
        perk(m, "Efficient Miner", 100, 2.6);
        perk(m, "Quick Forge", 20, 3.2);
        perk(g, "Old-School", 20, 4.0);
        perk(g, "Professional", 140, 2.3);
        perk(g, "Mole", 200, 2.17883);
        perk(g, "Gem Lover", 20, 4.0);
        perk(g, "Seasoned Mineman", 100, 2.3);
        perk(g, "Fortunate Mineman", 50, 3.2);
        perk(g, "Blockhead", 20, 4.0);
        perk(g, "Keep It Cool", 50, 3.07);
        perk(g, "Lonesome Miner", 45, 3.07);
        perk(g, "Great Explorer", 20, 4.0);
        perk(g, "Powder Buff", 50, 3.2);
        perk(g, "Speedy Mineman", 50, 3.2);
        perk(g, "Subterranean Fisher", 40, 3.07);
        perk(c, "No Stone Unturned", 50, 3.05);
        perk(c, "Strong Arm", 100, 2.3);
        perk(c, "Steady Hand", 100, 2.6);
        perk(c, "Warm Heart", 50, 3.1);
        perk(c, "Surveyor", 20, 4.0);
        perk(c, "Metal Head", 20, 4.0);
        perk(c, "Rags to Riches", 50, 3.05);
        perk(c, "Eager Adventurer", 100, 2.3);
        perk(c, "Crystalline", 50, 3.3);
        perk(c, "Gifts from the Departed", 100, 2.45);
        COSTS.put("Mining Master", new Perk("Mining Master", 10, 7, 5.0, c));
        perk(c, "Dead Man's Chest", 50, 3.2);
        perk(c, "Vanguard Seeker", 50, 3.1);
    }

    private static final Patterns.P MENU = Patterns.p("SH.mining.hotm.inventory");
    private static final Patterns.P LOCKED = Patterns.p("SH.mining.hotm.perk.notunlocked");
    private static final Patterns.P ENABLED = Patterns.p("SH.mining.hotm.perk.enable");
    private static final Patterns.P LEVEL = Patterns.p("SH.mining.hotm.perk.level");
    private static final Patterns.P COST = Patterns.p("SH.mining.hotm.perk.cost.new");
    private static final Patterns.P HEART_ITEM = Patterns.p("SH.mining.hotm.inventory.heart");
    private static final Patterns.P HEART_TOKENS = Patterns.p("SH.mining.hotm.inventory.heart.token");
    private static final Patterns.P ROTATING = Patterns.p("SH.misc.hotx.perk.generic");
    private static final String[][] SKY_MALL = {
            {"mining.speed", "§6+100⸕ Mining Speed"}, {"mining.fortune", "§6+50☘ Mining Fortune"}, {"extra.powder", "§a+15% §7more Powder"},
            {"ability.cooldown", "§a-20% §7Pickaxe Ability cooldowns"}, {"goblin.chance", "§a10x §6Gold §7& §bDiamond §7Goblin chance"}, {"titanium", "§a5x §9Titanium §7drops"}};

    private HotmPerks() {}

    // skyhanni HotmApi.skymall == EXTRA_POWDER from what chat or the tree last said. perk must not be turned off
    static boolean skyMallGivesPowder() {
        String mall = MiningEnv.profile().skyMall;
        for (String[] s : SKY_MALL) if (s[0].equals("extra.powder")) return s[1].equals(mall);
        return false;
    }

    private static boolean perkName(String name) {
        return COSTS.containsKey(name) || OTHERS.contains(name);
    }

    // level the tree last said, 0 if it didnt
    public static int level(String perk) {
        return MiningEnv.profile().hotmLevels.getOrDefault(perk, 0);
    }

    // skyhanni MineshaftPityDisplay.calculateChance: surveyor gives 0.75% a level, core level 9 gives 10%
    static double mineshaftChanceBonus() {
        return level("Surveyor") * 0.75 + (level("Core of the Mountain") >= 9 ? 10 : 0);
    }

    // one perk read from the tree, skyhanni HotxHandler.readFromSlot
    static void read(MenuView.Item it, Map<String, Integer> levels) {
        boolean core = it.clean.equals("Core of the Mountain");
        if (it.cleanLore.isEmpty()) return;
        if (!core && LOCKED.matcher(it.cleanLore.get(it.cleanLore.size() - 1)).matches()) {
            levels.put(it.clean, 0);
            return;
        }
        Matcher m = LEVEL.matcher(it.lore.get(0));
        Perk p = COSTS.get(it.clean);
        levels.put(it.clean, m.matches() ? Integer.parseInt(m.group("level")) : p != null ? p.max() : 1);
    }

    private static MenuView lastTree;

    // skyhanni HotxHandler.onInventory: what the tree says. other features need it so its read no matter which
    // switches are on
    private static void readTree(MenuView v) {
        if (v == lastTree) return;
        lastTree = v;
        Map<String, Integer> levels = MiningEnv.profile().hotmLevels;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty() || !perkName(it.clean)) continue;
            read(it, levels);
            if (it.clean.equals("Sky Mall")) {
                // a coal perk isnt bought yet whatever its last line says, no buff to read so the card goes
                if (it.model.endsWith("coal")) levels.put(it.clean, 0);
                else readSkyMall(it);
            }
        }
    }

    private static void watchTree(Minecraft mc) {
        if (mc.gui.screen() == null) return;
        MenuView v = MenuMarks.current();
        if (v != null && v.title.equals(HEART)) readTree(v);
    }

    // skyhanni HotxFeatures.onBackgroundDrawn + handleTokenStackSize
    static void mark(MenuView v) {
        boolean color = MiningPack.on(ID, "mining_hotm.highlight"), tokens = MiningPack.on(ID, "mining_hotm.token_stack");
        readTree(v);
        Map<String, Integer> levels = MiningEnv.profile().hotmLevels;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            if (tokens && HEART_ITEM.matcher(it.clean).matches()) {
                Matcher t = it.firstClean(HEART_TOKENS.pattern());
                if (t != null && !t.group("token").equals("0")) v.number(slot, "§b" + t.group("token"));
                continue;
            }
            if (!color || !perkName(it.clean)) continue;
            boolean locked = !it.clean.equals("Core of the Mountain") && !it.cleanLore.isEmpty() && LOCKED.matcher(it.cleanLore.get(it.cleanLore.size() - 1)).matches();
            boolean on = !locked && (it.clean.equals("Core of the Mountain") ? levels.getOrDefault(it.clean, 0) != 0 : anyEnabled(it));
            v.back(slot, locked ? 0xC0555555 : on ? MenuView.GREEN : MenuView.RED);
        }
    }

    private static boolean anyEnabled(MenuView.Item it) {
        for (String l : it.cleanLore) if (ENABLED.matcher(l.trim()).matches()) return true;
        return false;
    }

    // skyhanni RotatingPerkSlot.trySetFromItem: the line after "Your Current Effect"
    private static void readSkyMall(MenuView.Item it) {
        boolean after = false;
        for (String l : it.cleanLore) {
            if (l.trim().equals("Your Current Effect")) {
                after = true;
                continue;
            }
            if (!after) continue;
            Matcher m = ROTATING.matcher(l);
            if (m.matches() && setSkyMall(m.group("perk"), "item")) return;
        }
    }

    private static boolean setSkyMall(String perkText, String source) {
        for (String[] s : SKY_MALL) {
            if (Patterns.get("SH.mining.hotm.skymall." + source + "." + s[0]).matcher(perkText).matches()) {
                MiningEnv.profile().skyMall = s[1];
                return true;
            }
        }
        return false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        Matcher m = ROTATING.matcher(line.plain());
        if (m.matches()) setSkyMall(m.group("perk"), "chat");
        return ChatBus.PASS;
    }

    // the lines CurrencyPerHotxPerk adds
    static String spentLine(Perk p, int level, String design) {
        long spent = p.total(level), all = p.total(p.max());
        String pct = String.format(Locale.ROOT, "%.2f", spent * 100.0 / all);
        String label = "§7Powder spent:";
        if (level >= p.max()) return design.equals("Percentage") ? label + " §e" + pct + "% §7(§aMax level§7)" : label + " §e" + MiningEnv.sep(all) + " §7(§aMax level§7)";
        return switch (design) {
            case "Number" -> label + " §e" + MiningEnv.sep(spent) + "§7 / §e" + MiningEnv.sep(all);
            case "Percentage" -> label + " §e" + pct + "%§7 of max";
            default -> label + " §e" + MiningEnv.sep(spent) + "§7/§e" + MiningEnv.sep(all) + "§7 (§e" + pct + "%§7)";
        };
    }

    // line for the profile page tree, null for perks with no powder cost
    public static String spentLine(String perk, int level) {
        Perk p = COSTS.get(perk);
        return p == null ? null : spentLine(p, level, "Number and Percentage");
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        var screen = mc.gui.screen();
        if (screen == null || !MENU.matcher(GameState.strip(screen.getTitle().getString()).trim()).matches() || lines.size() < 2) return;
        Perk p = COSTS.get(GameState.strip(stack.getHoverName().getString()).trim());
        if (p == null) return;
        int level = level(p.name());
        int costAt = -1;
        for (int i = 0; i < lines.size(); i++) if (COST.matcher(GameState.strip(lines.get(i).getString()).trim()).matches()) costAt = i;
        boolean unlocked = level > 0;
        if (MiningPack.on(ID, "mining_hotm.powder_spent")) {
            lines.add(Math.min(2, lines.size()), Component.literal(spentLine(p, level, MiningPack.choice(ID, "mining_hotm.spent_design", "Number and Percentage"))));
            if (costAt >= 0) costAt++;
        }
        if (costAt < 0 || level >= p.max()) return;
        if (MiningPack.on(ID, "mining_hotm.for_ten") && InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)) {
            int to = Math.min(level + 10, p.max());
            lines.add(Math.min(costAt + 2, lines.size()), Component.literal("§7Powder for " + (to - level) + (to - level == 1 ? " level" : " levels") + " §e" + MiningEnv.sep(p.total(to) - p.total(level))));
        }
        if (MiningPack.on(ID, "mining_hotm.current_powder") && unlocked) {
            int at = Math.min(costAt + 2, lines.size());
            lines.add(at, Component.literal(" "));
            lines.add(at + 1, Component.literal("§7You have"));
            lines.add(at + 2, Component.literal(p.powder().color + MiningEnv.sep(p.powder().current()) + " " + p.powder().displayName + " Powder"));
        }
    }

    // false once /hotm showed sky mall locked. not read yet = maybe, the card asks for /hotm
    static boolean skyMallUnlocked() {
        Integer level = MiningEnv.profile().hotmLevels.get("Sky Mall");
        return level == null || level > 0;
    }

    private static RichCard.Content mall(String text) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Today", text)));
    }

    private static final RichCard CARD = new RichCard(MALL, "Sky Mall", "Mining", Zone.TOP_LEFT, 43, MALL) {
        private String text() {
            String s = MiningEnv.profile().skyMall;
            return s == null || s.isEmpty() ? "§cOpen /hotm to read it" : s;
        }

        @Override
        public boolean visible() {
            if (!skyMallUnlocked()) return false;
            if ("Everywhere".equals(MiningPack.choice(MALL, "mining_sky_mall.where", "Mining islands"))) return MiningPack.skyblock();
            return MiningPack.mining();
        }

        @Override
        protected Content live() {
            return mall(text());
        }

        @Override
        protected Content sample() {
            return mall("§6+50☘ Mining Fortune");
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Today", text(), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Today", "§6+50☘ Mining Fortune", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Heart of the Mountain", "In /hotm: perks green when on, red when off and gray when locked, your tokens on the heart, and in a perk's tooltip what it has cost, what ten more levels cost and what you have.", "SkyHanni: HotM settings")
                .when(MiningPack::skyblock).tooltip(HotmPerks::tooltip)
                .option(Option.bool("mining_hotm.highlight", "Colour the perks", "", true))
                .option(Option.bool("mining_hotm.token_stack", "Tokens on the heart", "", true))
                .option(Option.bool("mining_hotm.powder_spent", "Powder spent", "", true))
                .option(Option.choice("mining_hotm.spent_design", "How to show it", "", "Number and Percentage", "Number", "Percentage", "Number and Percentage"))
                .option(Option.bool("mining_hotm.for_ten", "Powder for 10 levels", "While you hold Shift.", true))
                .option(Option.bool("mining_hotm.current_powder", "Powder you have", "", true)));
        Safe.endTick("HotmPerks.watchTree", HotmPerks::watchTree);
        ChatBus.register(ChatBus.OBSERVE + 26, "mining_sky_mall_read", null, HotmPerks::onChat);
        Features.add(new Feature(MALL, "Mining", "Sky Mall", "Today's Sky Mall buff on a card, from chat or from your /hotm.", "SkyHanni: Sky Mall Display")
                .when(MiningPack::skyblock)
                .option(Option.choice("mining_sky_mall.where", "Where", "", "Mining islands", "Mining islands", "Everywhere")));
        MenuMarks.add(ID, HEART, HotmPerks::mark);
        HudLayout.add(CARD);
    }

    static List<String> perksForTest() {
        return new ArrayList<>(COSTS.keySet());
    }
}
