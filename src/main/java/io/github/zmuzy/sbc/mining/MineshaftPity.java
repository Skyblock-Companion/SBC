// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// the bits of skyhanni MineshaftPityDisplay the older pity card doesnt have
// blocks broken since the last shaft, chance rn, how many of each block still needed, avg per shaft, and
// those on the spawn message hover
public final class MineshaftPity {
    static final String ID = "mining_pity_odds";
    private static final int MAX = 2000;

    // from skyhanni PityBlock
    enum Block {
        MITHRIL("Mithril", 2), GEMSTONE("Low Tier Gemstone", 8), HIGH_TIER_GEMSTONE("High Tier Gemstone", 10), GLACITE("Glacite", 4), TUNGSTEN("Tungsten", 4), UMBER("Umber", 4), TITANIUM("Titanium", 8);

        final String label;
        final int multiplier;

        Block(String label, int multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        static Block of(OreBlock ore) {
            return switch (ore.oreType()) {
                case "MITHRIL" -> MITHRIL;
                case "RUBY", "AMBER", "AMETHYST", "JADE", "SAPPHIRE" -> GEMSTONE;
                case "AQUAMARINE", "CITRINE", "ONYX", "PERIDOT" -> HIGH_TIER_GEMSTONE;
                case "GLACITE" -> GLACITE;
                case "TUNGSTEN" -> TUNGSTEN;
                case "UMBER" -> UMBER;
                case "TITANIUM" -> TITANIUM;
                default -> null;
            };
        }
    }

    private static Component lastHover;
    private static long hoverAt;
    private static String lastIsland = "";

    private MineshaftPity() {}

    private static Store.MiningProfile store() {
        return MiningEnv.profile();
    }

    private static int[] counts(Block b) {
        return store().pityBlocks.computeIfAbsent(b.name(), k -> new int[2]);
    }

    // skyhanni onOreMined: the one you hit counts whole, what efficient miner took along counts as spread. always
    // read, whatever card is on
    private static void onOre(OreBlock original, Map<OreBlock, Integer> extra) {
        if (!MiningApi.inGlacialTunnels()) return;
        Block first = original == null ? null : Block.of(original);
        if (first != null) counts(first)[0]++;
        for (var e : extra.entrySet()) {
            Block b = Block.of(e.getKey());
            if (b == null) continue;
            int n = e.getValue() - (e.getKey() == original ? 1 : 0);
            counts(b)[1] += n;
        }
    }

    // from skyhanni PityBlock.getPity
    static double pity(int broken, int spread, int multiplier) {
        return (broken + spread / 2.0) * multiplier;
    }

    // skyhanni calculateChance: 1 in this many. counter = whats left till pity
    static double chance(int left, double bonusPercent) {
        return left / (1 + bonusPercent / 100);
    }

    private static int left() {
        return MAX - Mineshaft.tablistPity;
    }

    private static int totalBlocks() {
        int n = 0;
        for (int[] c : store().pityBlocks.values()) n += c[0] + c[1];
        return n;
    }

    private static String average() {
        Store.MiningProfile p = store();
        return p.mineshaftTotalCount == 0 ? "—" : MiningEnv.sep(p.mineshaftTotalBlocks / p.mineshaftTotalCount);
    }

    private static String percent(double chance) {
        return String.format(Locale.ROOT, "%.3f%%", chance <= 0 ? 100 : 100 / chance);
    }

    // skyhanni onChat: hover text for the spawn message, then a new count. called before the message shows
    public static void onSpawn() {
        Store.MiningProfile p = store();
        int left = left(), total = totalBlocks();
        double chance = chance(left, HotmPerks.mineshaftChanceBonus());
        p.mineshaftTotalBlocks += total;
        StringBuilder hover = new StringBuilder("§7Blocks mined: §e" + total + "\n§7Pity Counter: §e" + left + "\n§7Chance: §e1§6/§e" + Math.round(chance) + " §7(§b" + percent(chance) + "§7)");
        for (Block b : Block.values()) {
            int[] c = p.pityBlocks.get(b.name());
            if (c != null && c[0] + c[1] > 0) hover.append("\n    §7").append(b.label).append(" mined: §e").append(c[0]).append(" [+").append(c[1]).append(" spread]");
        }
        hover.append("\n\n§7Average Blocks/Mineshaft: §e").append(MiningEnv.sep(p.mineshaftTotalBlocks / (p.mineshaftTotalCount + 1)));
        if (!MiningEnv.isFarPast(Mineshaft.lastMineshaftSpawn)) hover.append("\n\n§7Time since Last Mineshaft: §b").append(MiningEnv.format(MiningEnv.passedSince(Mineshaft.lastMineshaftSpawn)));
        lastHover = Component.literal(hover.toString());
        hoverAt = System.currentTimeMillis();
        p.pityBlocks.clear();
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (lastHover == null || System.currentTimeMillis() - hoverAt > 1000 || !MiningPack.on(ID, "mining_pity_odds.message")) return ChatBus.PASS;
        if (!MiningNotifications.MINESHAFT_SPAWN.matcher(LegacyText.of(line.component())).matches()) return ChatBus.PASS;
        Component hover = lastHover;
        lastHover = null;
        return new ChatBus.Replace(line.component().copy().append(Component.literal(" §e(" + Mineshaft.tablistPity + ")")).withStyle(st -> st.withHoverEvent(new HoverEvent.ShowText(hover))));
    }

    private static void tick(Minecraft mc) {
        if (lastIsland.equals(GameState.island)) return;
        boolean shaft = "MINESHAFT".equals(lastIsland) || "MINESHAFT".equals(GameState.island);
        lastIsland = GameState.island;
        // skyhanni onIslandChange + onIslandLeave: entering or leaving a shaft starts over
        if (shaft) store().pityBlocks.clear();
    }

    private static void reset() {
        Store.MiningProfile p = store();
        p.pityBlocks.clear();
        p.mineshaftTotalBlocks = 0;
        p.mineshaftTotalCount = 0;
        Mineshaft.sessionMineshafts = 0;
        Mineshaft.lastMineshaftSpawn = MiningEnv.FAR_PAST;
    }

    private static List<RichCard.Line> lines(int left, double chance, String average, int total) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Chance", "§e1§6/§e" + MiningEnv.sep(Math.round(chance)) + " §7(§b" + percent(chance) + "§7)"));
        out.add(RichCard.Line.row("Blocks mined", "§e" + MiningEnv.sep(total)));
        out.add(RichCard.Line.row("Average per shaft", "§e" + average).brief("Average"));
        out.add(RichCard.Line.section("Needed to pity"));
        out.add(RichCard.Line.row("Mithril", "§b" + left / 2));
        out.add(RichCard.Line.row("Glacite, Umber, Tungsten", "§b" + left / 4).brief("Glacite+"));
        out.add(RichCard.Line.row("Gemstone, Titanium", "§b" + left / 8).brief("Gem, Titanium"));
        out.add(RichCard.Line.row("Tunnel gemstone", "§b" + left / 10));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Mineshaft odds", "Mining", Zone.TOP_LEFT, 39, ID) {
        @Override
        public boolean visible() {
            return (MiningApi.inGlacialTunnels() || MiningApi.inDwarvenBaseCamp()) && Mineshaft.everFoundPityWidget;
        }

        @Override
        protected Content live() {
            return new Content("", lines(left(), chance(left(), HotmPerks.mineshaftChanceBonus()), average(), totalBlocks()));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(1439, 1250.0, "361", 280));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Chance", "1/" + Math.round(chance(left(), HotmPerks.mineshaftChanceBonus())), 0), new Row("Blocks mined", String.valueOf(totalBlocks()), 0), new Row("Average", average(), 0), new Row("Mithril to pity", String.valueOf(left() / 2), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Chance", "1/1250", 0), new Row("Blocks mined", "280", 0), new Row("Average", "361", 0), new Row("Mithril to pity", "719", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Mining", "Mineshaft odds", "In the Glacite Tunnels: your chance of a Mineshaft now, the blocks you mined for it, the average and how many of each block are still needed; on the spawn message when you hover it.", "SkyHanni: Mineshaft Pity Display")
                .when(MiningPack::dwarven)
                .option(Option.bool("mining_pity_odds.message", "More on the spawn message", "", true)));
        ChatBus.register(ChatBus.FILTER + 41, ID, f, MineshaftPity::onChat);
        OreMined.listen(MineshaftPity::onOre);
        Safe.endTick("MineshaftPity.tick", MineshaftPity::tick);
        HudLayout.add(CARD);
        Safe.commands("MineshaftPity.commands", (d, r) -> d.register(literal("sbc").then(literal("resetmineshaftpity").executes(c -> {
            reset();
            MiningPack.chat("Reset the mineshaft pity stats!");
            return 1;
        }))));
        io.github.zmuzy.sbc.core.Commands.describe("resetmineshaftpity", "Reset the Mineshaft pity stats");
    }
}
