// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.crimson;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Pets;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what a kuudra chest costs and gives, from skyblocker Kuudra, KuudraProfileData, ChestValue
// computeKuudraKeyPrice + computeCrimsonEssenceMultiplier
// a key = coins (differ per player so read from your faction's shop) + an ingredient of your faction + two
// stars. the kuudra pet gives bonus crimson essence
// menus read no matter whats on, the dungeon pack's chest values need them
public final class KuudraKeys {
    // KuudraProfileData.EMPTY
    private static final Map<String, Integer> DEFAULT_PRICES = Map.of("KUUDRA_TIER_KEY", 200_000, "KUUDRA_HOT_TIER_KEY", 400_000, "KUUDRA_BURNING_TIER_KEY", 750_000,
            "KUUDRA_FIERY_TIER_KEY", 1_500_000, "KUUDRA_INFERNAL_TIER_KEY", 3_000_000);
    private static final Map<String, Integer> INGREDIENTS = Map.of("KUUDRA_TIER_KEY", 2, "KUUDRA_HOT_TIER_KEY", 4, "KUUDRA_BURNING_TIER_KEY", 16, "KUUDRA_FIERY_TIER_KEY", 40, "KUUDRA_INFERNAL_TIER_KEY", 80);
    private static final Pattern FACTION_SHOP = Pattern.compile("^(?<faction>Mage|Barbarian) Shop$");
    // ChestValue.DUNGEON_CHEST_COIN_COST_PATTERN
    private static final Pattern COIN_COST = Pattern.compile("^([0-9,]+) Coins$");

    private KuudraKeys() {}

    private static Store.CrimsonProfile profile() {
        return CrimsonEnv.profile();
    }

    // computeKuudraKeyPrice, -1 when a price isnt known
    public static double cost(String keyId, ToDoubleFunction<String> price) {
        Integer base = profile().kuudraKeyPrices.getOrDefault(keyId, DEFAULT_PRICES.get(keyId));
        Integer amount = INGREDIENTS.get(keyId);
        if (base == null || amount == null) return -1;
        return cost(base, amount, ReputationApi.factionType() == ReputationApi.FactionType.BARBARIAN, price);
    }

    static double cost(int base, int amount, boolean barbarian, ToDoubleFunction<String> price) {
        double ingredient = price.applyAsDouble(barbarian ? "ENCHANTED_RED_SAND" : "ENCHANTED_MYCELIUM"), star = price.applyAsDouble("CORRUPTED_NETHER_STAR");
        if (ingredient < 0 || star < 0) return -1;
        return base + ingredient * amount + star * 2;
    }

    // computeCrimsonEssenceMultiplier for the stored pet
    public static float essenceMultiplier() {
        return essenceMultiplier(profile().kuudraPetTier, profile().kuudraPetLevel);
    }

    static float essenceMultiplier(String tier, int level) {
        float percent = switch (tier) {
            case "UNCOMMON", "RARE" -> 15f;
            case "EPIC", "LEGENDARY" -> 20f;
            default -> 10f;
        } * (level / 100f);
        return percent / 100f + 1f;
    }

    private static void readShop(ContainerScreen s) {
        var prices = profile().kuudraKeyPrices;
        for (Slot slot : s.getMenu().slots) {
            ItemStack st = slot.getItem();
            if (st.isEmpty() || !DEFAULT_PRICES.containsKey(Gain.sbId(st))) continue;
            for (String l : LegacyText.lore(st)) {
                Matcher m = COIN_COST.matcher(GameState.strip(l).trim());
                if (m.matches()) prices.put(Gain.sbId(st), Integer.parseInt(m.group(1).replace(",", "")));
            }
        }
    }

    // checkForKuudraPet: rarest pet with the most xp
    private static void readPets(ContainerScreen s) {
        var p = profile();
        for (Slot slot : s.getMenu().slots) {
            var cd = slot.getItem().get(DataComponents.CUSTOM_DATA);
            if (cd == null) continue;
            String info = cd.copyTag().getStringOr("petInfo", "");
            if (!info.contains("KUUDRA")) continue;
            try {
                var o = com.google.gson.JsonParser.parseString(info).getAsJsonObject();
                if (!"KUUDRA".equals(o.get("type").getAsString())) continue;
                String tier = o.get("tier").getAsString();
                double exp = o.get("exp").getAsDouble();
                if (p.kuudraPetTier.isEmpty() || (rank(tier) >= rank(p.kuudraPetTier) && exp >= p.kuudraPetExp)) {
                    p.kuudraPetTier = tier;
                    p.kuudraPetExp = exp;
                    p.kuudraPetLevel = Pets.level(exp, tier, "KUUDRA");
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static int rank(String tier) {
        return java.util.List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY").indexOf(tier);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.player.tickCount % 10 != 0 || !GameState.inSkyBlock || !(mc.gui.screen() instanceof ContainerScreen s)) return;
        String title = s.getTitle().getString();
        if (FACTION_SHOP.matcher(title).matches()) readShop(s);
        else if (title.endsWith("Pets")) readPets(s);
    }

    static void register() {
        Safe.endTick("KuudraKeys.tick", KuudraKeys::tick);
    }
}
