// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden;

import io.muzy.sbc.core.AuctionPrices;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemInfo;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// item lines + prices for the garden helpers. from skyhanni ItemUtils.readItemAmount,
// NeuInternalName.fromItemName, ItemPriceUtils.getPrice, NeuItems.getPrimitiveMultiplier, on our item data
public final class GardenItems {
    // ItemPriceSource
    public enum Source {
        INSTANT_BUY, INSTANT_SELL, NPC;

        public static Source of(String choice) {
            return switch (choice) {
                case "Instant sell" -> INSTANT_SELL;
                case "NPC" -> NPC;
                default -> INSTANT_BUY;
            };
        }
    }

    public record Amount(String name, int amount) {}

    public record Primitive(String id, int amount) {}

    // "Turbo Cacti I Book" = the enchant name
    private static final Pattern BOOK = Pattern.compile("(?<name>.* [IVX]+) Book");
    private static final Pattern ENCHANT = Pattern.compile("(?<name>.*) (?<level>[IVX]+)");
    private static final Map<String, Primitive> PRIMITIVE = new HashMap<>();

    // item name -> id, and a price. swappable in tests
    public static Function<String, String> names = ItemRepo::idForName;
    public static ToDoubleBiFunction<String, Source> prices = GardenItems::livePrice;

    private GardenItems() {}

    // ItemUtils.readItemAmount: "§aFine Flour §8x3", "§8+§52 Pelts"
    public static Amount readItemAmount(String original) {
        Matcher book = BOOK.matcher(original);
        String input = (book.matches() ? book.group("name") : original).replace("§r", "");
        Matcher front = Patterns.get("SH.utils.item.amount.front").matcher(input);
        if (front.matches()) {
            String name = front.group("name");
            if (!name.contains("§8x")) return new Amount(name.trim(), amount(front.group("amount")));
        }
        String string = input.trim();
        if (string.length() < 2) return null;
        String color = string.substring(0, 2);
        Matcher behind = Patterns.get("SH.utils.item.amount.behind").matcher(string.substring(2));
        if (!behind.matches()) return null;
        return new Amount(color + behind.group("name").trim(), amount(behind.group("amount")));
    }

    private static int amount(String text) {
        if (text == null) return 1;
        long v = GameState.parseShort(text);
        return v < 0 ? 1 : (int) v;
    }

    // NeuInternalName.fromItemNameOrNull: books go by their bazaar id
    public static String idOf(String colouredName) {
        String plain = GameState.strip(colouredName).trim().replace("◆ ", "");
        if (plain.endsWith(" Pet")) plain = plain.substring(0, plain.length() - 4);
        String id = names.apply(plain);
        if (id != null) return id;
        Matcher m = ENCHANT.matcher(plain);
        if (m.matches()) {
            int level = GameState.roman(m.group("level"));
            if (level > 0) return "ENCHANTMENT_" + m.group("name").toUpperCase(Locale.ROOT).replace(' ', '_').replace("-", "_") + "_" + level;
        }
        return null;
    }

    public static double price(String id) {
        return price(id, Source.INSTANT_BUY);
    }

    public static double price(String id, Source source) {
        return prices.applyAsDouble(id, source);
    }

    private static Prices.Product product(String id) {
        Prices.Product p = Prices.get(id);
        return p != null ? p : Prices.get(id.replace('-', ':'));
    }

    // ItemPriceUtils.getPriceOrNull
    private static double livePrice(String id, Source source) {
        return livePrice(id, source, 0);
    }

    private static double livePrice(String id, Source source, int depth) {
        switch (id) {
            case "ENCHANTED_HAY_BLOCK" -> {
                return 7_776;
            }
            case "TIGHTLY_TIED_HAY_BALE" -> {
                return 1_119_744;
            }
            default -> {
            }
        }
        if (source != Source.NPC) {
            Prices.Product p = product(id);
            if (p != null) return source == Source.INSTANT_BUY ? p.buy() : p.sell();
            double bin = AuctionPrices.value(id);
            if (bin > 0) return bin;
            if (id.equals("JACK_O_LANTERN")) return livePrice("PUMPKIN", source, depth + 1) + 1;
        }
        if (id.equals("GOLDEN_CARROT")) return 7;
        double npc = ItemRepo.npcPrice(id);
        if (npc > 0 || source == Source.NPC || depth >= 6) return Math.max(0, npc);
        // getRawCraftCostOrNull
        double best = 0;
        for (ItemInfo.Recipe r : ItemInfo.recipes(id)) {
            if (!r.type().equals("crafting")) continue;
            double sum = 0;
            for (ItemInfo.Part part : r.parts()) sum += livePrice(part.id(), source, depth + 1) * part.count();
            sum /= Math.max(1, r.resultCount());
            if (sum > 0 && (best == 0 || sum < best)) best = sum;
        }
        return best;
    }

    // NeuItems.getPrimitiveMultiplier: how many of the plain item this compressed one is
    public static Primitive primitive(String id) {
        return primitive(id, 0);
    }

    private static Primitive primitive(String id, int tries) {
        Primitive known = PRIMITIVE.get(id);
        if (known != null) return known;
        Primitive plain = new Primitive(id, 1);
        if (tries == 10) return plain;
        for (ItemInfo.Recipe recipe : ItemInfo.recipes(id)) {
            if (!recipe.type().equals("crafting")) continue;
            Map<String, Integer> map = new HashMap<>();
            for (ItemInfo.Part part : recipe.parts()) {
                String ingredient = part.id();
                // ignore cactus green
                if (id.equals("ENCHANTED_CACTUS_GREEN") && ingredient.equals("INK_SACK:2")) ingredient = "CACTUS";
                // ignore rabbit hide in leather
                if (id.equals("LEATHER") && ingredient.equals("RABBIT_HIDE")) continue;
                map.merge(ingredient, part.count(), Integer::sum);
            }
            if (map.size() != 1) continue;
            var only = map.entrySet().iterator().next();
            if (only.getValue() <= 1) return plain;
            Primitive child = primitive(only.getKey(), tries + 1);
            Primitive result = new Primitive(child.id(), child.amount() * only.getValue());
            PRIMITIVE.put(id, result);
            return result;
        }
        PRIMITIVE.put(id, plain);
        return plain;
    }

    // InventoryUtils.getAmountInInventory
    public static int inInventory(String id) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return 0;
        int n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (id.equals(Gain.sbId(st))) n += st.getCount();
        }
        return n;
    }

    // like TimeUtils.format, max 2 units: "3h 5m"
    public static String time(long ms) {
        return time(ms, 2);
    }

    public static String time(long ms, int units) {
        long s = Math.max(0, ms) / 1000;
        long[] parts = {s / 86400, s % 86400 / 3600, s % 3600 / 60, s % 60};
        String[] names = {"d", "h", "m", "s"};
        StringBuilder b = new StringBuilder();
        int used = 0;
        for (int i = 0; i < 4 && used < units; i++) {
            if (parts[i] == 0) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(parts[i]).append(names[i]);
            used++;
        }
        return b.isEmpty() ? "0s" : b.toString();
    }
}
