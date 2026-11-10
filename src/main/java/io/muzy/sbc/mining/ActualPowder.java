// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.game.GameProfile;
import io.muzy.sbc.core.game.Seen;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.item.menus.AttributeShards;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni ActualGemstonePowderDisplay: a treasure chests gemstone powder line also says what you really
// got after every multiplier, factors on hover
// stuff we cant know yet (a perk or shard whose menu you never opened) counts as nothing, so the number can
// be too low but never too high
public final class ActualPowder {
    public static final String ID = "mining_actual_powder";
    private static final Patterns.P REWARD = Patterns.p("SH.mining.powder.tracker.reward.gemstonepowder.colorless");
    private static final Patterns.P DRILL_BASE = Patterns.p("SH.mining.powder.multiplier.drill.powder.base"), DRILL_UPGRADE = Patterns.p("SH.mining.powder.multiplier.drill.powder.upgrade");

    private ActualPowder() {}

    // one multiplier + its hover lines
    record Factor(double multiplier, List<String> lines) {}

    private static String number(double v) {
        String s = String.format(Locale.US, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }

    private static String percent(double fraction) {
        return "+" + number(fraction * 100) + "%";
    }

    // attribute bonus: echo of atomized scales atomized crystals, echo of echoes scales that
    static Factor attributes(int atomized, int echo, int echoOfEchoes) {
        double a = atomized / 100.0, e = a * (echo * 2 / 100.0), ee = e * (echoOfEchoes * 5 / 100.0), bonus = a + e + ee;
        if (bonus <= 0) return null;
        List<String> lines = new ArrayList<>();
        lines.add("§7└ §7Attribute Bonus: §ax" + number(1 + bonus));
        if (a > 0) lines.add("§8  └ §8Atomized Crystals [" + atomized + "]: §2" + percent(a));
        if (e > 0) lines.add("§8  └ §8Echo of Atomized [" + echo + "]: §2" + percent(e));
        if (ee > 0) lines.add("§8  └ §8Echo of Echoes [" + echoOfEchoes + "]: §2" + percent(ee));
        return new Factor(1 + bonus, lines);
    }

    // hotm powder buffs: the perk + the drills base and its goblin egg
    static Factor hotm(int perkLevel, int drillBase, int drillUpgrade, String drillName) {
        double perk = perkLevel / 100.0, base = drillBase / 100.0, upgrade = drillUpgrade / 100.0, bonus = perk + base + upgrade;
        if (bonus <= 0) return null;
        List<String> lines = new ArrayList<>();
        lines.add("§7└ §7HOTM Powder Buffs: §ax" + number(1 + bonus));
        if (perk > 0) lines.add("§8  └ §8HOTM Powder Buff: §2" + percent(perk));
        if (base + upgrade > 0) lines.add("§8  └ §8" + drillName + ": §2" + percent(base + upgrade));
        if (base > 0) lines.add("§5    └ §5Drill Base: §b" + percent(base));
        if (upgrade > 0) lines.add("§5    └ §5Goblin Egg: §b" + percent(upgrade));
        return new Factor(1 + bonus, lines);
    }

    private static Factor plain(String name, double bonus) {
        return new Factor(1 + bonus, List.of("§7└ §7" + name + ": §ax" + number(1 + bonus)));
    }

    private static List<Factor> factors() {
        List<Factor> out = new ArrayList<>();
        Factor a = attributes(AttributeShards.activeLevel("SHARD_THYST"), AttributeShards.activeLevel("SHARD_IGUANA"), AttributeShards.activeLevel("SHARD_TIAMAT"));
        if (a != null) out.add(a);
        Minecraft mc = Minecraft.getInstance();
        int base = 0, upgrade = 0;
        String drillName = "";
        ItemStack held = mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
        if (Gain.sbId(held).contains("DRILL")) {
            for (String line : LegacyText.lore(held)) {
                Matcher m = DRILL_BASE.matcher(line);
                if (m.matches()) base += Integer.parseInt(m.group("bonus"));
                m = DRILL_UPGRADE.matcher(line);
                if (m.matches()) upgrade += Integer.parseInt(m.group("bonus"));
            }
            drillName = GameState.strip(held.getHoverName().getString());
        }
        Factor h = hotm(MiningEnv.profile().hotmLevels.getOrDefault("Powder Buff", 0), base, upgrade, drillName);
        if (h != null) out.add(h);
        if (MiningEvents.isActive(MiningEvents.Type.DOUBLE_POWDER)) out.add(plain("2x Powder Event", 1.0));
        if (HotmPerks.skyMallGivesPowder()) out.add(plain("Sky Mall", 0.15));
        Seen s = GameProfile.current();
        if (s != null) {
            for (Seen.Pet p : s.allPets()) {
                if (!p.active) continue;
                if (p.name.equals("Scatha") && p.tier.equalsIgnoreCase("LEGENDARY")) out.add(plain("[Lvl " + p.level + "] Scatha", 0.2 * p.level / 100.0));
                break;
            }
        }
        return out;
    }

    static ChatBus.Result onChat(ChatBus.Line line) {
        if (!MiningApi.inCrystalHollows()) return ChatBus.PASS;
        Matcher m = REWARD.matcher(line.plain());
        if (!m.matches() || m.group("amount") == null) return ChatBus.PASS;
        return line(Integer.parseInt(m.group("amount").replace(",", "")), factors());
    }

    // what it says, nothing if nothing multiplies it
    static ChatBus.Result line(int amount, List<Factor> factors) {
        double total = 1;
        for (Factor f : factors) total *= f.multiplier();
        if (total <= 1) return ChatBus.PASS;
        int actual = (int) (amount * total);
        StringBuilder hover = new StringBuilder("§7Base Powder: §e").append(String.format(Locale.US, "%,d", amount)).append("\n\n§7Total Multiplier: §6x").append(number(total));
        for (Factor f : factors) for (String l : f.lines()) hover.append('\n').append(l);
        hover.append("\n\n§7Actual Powder: §d").append(String.format(Locale.US, "%,d", actual));
        return new ChatBus.Replace(Component.literal("    §r§dGemstone Powder §r§8x" + String.format(Locale.US, "%,d", amount) + " §7(x" + String.format(Locale.US, "%,d", actual) + ")")
                .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover.toString())))));
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Mining", "Actual Gemstone Powder", "A treasure chest's Gemstone Powder line also says what you really got after every multiplier, with each one on hover.", "SkyHanni: Show Actual Powder Gained")
                .when(MiningApi::inCrystalHollows).defaultOff());
        ChatBus.register(ChatBus.FILTER + 30, ID, f, ActualPowder::onChat);
    }
}
