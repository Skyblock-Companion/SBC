// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni BlockStrengthGuide + OreBlock.miningTicks + Mining.json: ticks per block at your mining
// speed, as a table in chat
// and skyblocker ItemStackMixin + ItemUtils.getDurability: drill fuel and pickonimbus uses as the item bar
public final class BlockStrength {
    static final String FUEL = "mining_drill_fuel";
    private static final String[][] GROUPS = {
            {"Vanilla ores", "COAL_ORE"}, {"Pure ores", "PURE_COAL"}, {"Titanium", "TITANIUM"}, {"Gray Mithril", "LOW_TIER_MITHRIL"}, {"Green Mithril", "MID_TIER_MITHRIL"}, {"Blue Mithril", "HIGH_TIER_MITHRIL"},
            {"Tungsten, Umber", "HIGH_TIER_TUNGSTEN"}, {"Glacite", "GLACITE"}, {"Obsidian", "OBSIDIAN"}, {"Ruby", "RUBY"}, {"Nucleus gemstones", "SAPPHIRE"}, {"Topaz, Opal", "TOPAZ"}, {"Jasper", "JASPER"}, {"Tunnel gemstones", "ONYX"}};
    private static final Map<String, Integer> STRENGTHS = new java.util.HashMap<>();
    private static final Pattern NOT_FUEL = Pattern.compile("[^0-9 /]");
    private static final Map<ItemStack, Float> FILLS = new WeakHashMap<>();
    private static long filledAt;
    private static boolean loaded;

    private BlockStrength() {}

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = io.muzy.sbc.core.data.DataFiles.sh("Mining.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("block_strengths");
            for (String k : o.keySet()) STRENGTHS.put(k, o.get(k).getAsInt());
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad Mining.json", e);
        }
    }

    // strength of a block where you stand, null if none of the ores here match
    static Integer strengthOf(net.minecraft.world.level.block.state.BlockState state) {
        load();
        for (OreBlock b : OreBlock.inCurrentArea()) if (b.check.test(state)) return STRENGTHS.get(b.name());
        return null;
    }

    // from skyhanni OreBlock.miningTicks
    static int miningTicks(int strength, double speed, OreBlock.Category category) {
        int instant = strength * (category != OreBlock.Category.BLOCK ? 60 : 30);
        int softCap = (int) Math.ceil(20.0 / 3.0 * strength);
        if (speed >= instant) return 1;
        if (speed >= softCap) return 4;
        return (int) Math.round(strength * 30.0 / speed);
    }

    private static void table(double speed) {
        load();
        if (speed <= 0) {
            MiningPack.chat("§cNo Mining Speed known. Enable it in your /tab, or say it: /sbc blockstrength <speed>");
            return;
        }
        MiningPack.chat("§6Ticks to mine at §e" + (int) speed + "⸕§6:");
        for (String[] g : GROUPS) {
            OreBlock b = OreBlock.valueOf(g[1]);
            Integer s = STRENGTHS.get(g[1]);
            if (s == null) continue;
            int ticks = miningTicks(s, speed, b.category);
            String next = ticks <= 1 ? "" : " §7(§e+" + (int) Math.ceil(s * 30 / (ticks - 0.5) - speed) + "⸕ §7for " + (ticks - 1) + ")";
            MiningPack.chat("§7" + g[0] + ": §f" + ticks + next);
        }
    }

    // skyblocker ItemUtils.getDurability: how full the bar is, -1 = no bar
    static float fill(String id, boolean pickonimbusHasData, int pickonimbus, String fuelLine) {
        if (id.equals("PICKONIMBUS")) return (pickonimbusHasData ? pickonimbus : 2000) / 2000f;
        if (fuelLine == null) return -1;
        String[] p = NOT_FUEL.matcher(fuelLine).replaceAll("").trim().split("/");
        if (p.length != 2) return -1;
        try {
            return Integer.parseInt(p[0].trim()) / (Integer.parseInt(p[1].trim()) * 1000f);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // asked for every item every frame. checked once in a while, right away for non skyblock stuff
    public static float fill(ItemStack st) {
        if (!(GameState.inSkyBlock || SbcMod.DEV) || st.isEmpty()) return -1;
        long now = System.currentTimeMillis();
        if (now - filledAt > 1000) {
            FILLS.clear();
            filledAt = now;
        }
        Float known = FILLS.get(st);
        if (known != null) return known;
        float out = -1;
        if (Features.on(FUEL)) {
            var data = st.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null) {
                var tag = Gain.tag(data);
                String id = tag.getStringOr("id", "");
                String fuel = null;
                var lore = st.get(net.minecraft.core.component.DataComponents.LORE);
                if (lore != null) for (var l : lore.lines()) {
                    String clean = l.getString();
                    if (clean.contains("Fuel: ")) {
                        fuel = GameState.strip(clean);
                        break;
                    }
                }
                out = fill(id, tag.contains("pickonimbus_durability"), tag.getIntOr("pickonimbus_durability", 0), fuel);
            }
        }
        FILLS.put(st, out);
        return out;
    }

    // skyhannis bar goes red to green through the same steps as vanillas
    public static int fillColor(float fill) {
        float f = Math.clamp(fill, 0f, 1f);
        return net.minecraft.util.Mth.hsvToRgb(f / 3f, 1f, 1f);
    }

    public static void register() {
        Features.add(new Feature(FUEL, "Mining", "Drill fuel bar", "The bar on a drill shows its fuel, on a Pickonimbus the uses it has left.", "Skyblocker: Enable Drill Fuel").when(MiningPack::skyblock));
        Safe.commands("BlockStrength.commands", (d, r) -> d.register(literal("sbc").then(literal("blockstrength").executes(c -> {
            table(Vitals.stat("Mining Speed"));
            return 1;
        }).then(argument("speed", IntegerArgumentType.integer(1)).executes(c -> {
            table(IntegerArgumentType.getInteger(c, "speed"));
            return 1;
        })))));
        io.muzy.sbc.core.Commands.describe("blockstrength", "How many ticks each block takes at your Mining Speed");
    }
}
