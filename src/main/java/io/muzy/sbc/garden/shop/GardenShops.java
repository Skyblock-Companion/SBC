// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.shop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenData;
import io.muzy.sbc.garden.GardenItems;
import io.muzy.sbc.garden.visitor.Visitors;
import io.muzy.sbc.item.market.LoreCosts;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.nav.Destinations;
import io.muzy.sbc.ui.SideHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// garden shops + helpers around them, from skyhanni SkyMartCopperPrice, AnitaMedalProfit,
// AnitaExtraFarmingFortune, CarrolynHelper, and for galatea StarlynSisterCouponProfit + CompactStarlynSisters
public final class GardenShops {
    public static final String SKYMART = "garden_skymart_prices", ANITA = "garden_anita_prices", ANITA_FORTUNE = "garden_anita_fortune", CARROLYN = "garden_carrolyn", STARLYN = "starlyn_coupons", STARLYN_CHAT = "starlyn_compact";

    private static final ProfitPanel SKYMART_PANEL = new ProfitPanel("SkyMart Prices", "Coins per copper", LegacyItems.stack("minecraft:copper_ingot"), 0xFFEC9A62, "No prices yet.");
    private static final ProfitPanel ANITA_PANEL = new ProfitPanel("Anita Prices", "Profit per bronze medal", LegacyItems.stack("minecraft:gold_ingot"), 0xFFFFD166, "No prices yet.");
    private static final ProfitPanel STARLYN_PANEL = new ProfitPanel("Coupon Prices", "Profit per coupon", LegacyItems.stack("minecraft:paper"), 0xFF9BD96B, "No prices yet.");

    private static Map<Integer, int[]> levelPrice;
    private static boolean pendingCarrolyn;

    private GardenShops() {}

    private static boolean island(String... keys) {
        for (String k : keys) if (k.equals(GameState.island)) return true;
        return false;
    }

    private static GardenItems.Source source(String feature, String key, String otherwise) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return GardenItems.Source.of(o == null ? otherwise : o.text());
    }

    private static void frame(MenuView v) {
        int slot = ProfitPanel.framed();
        if (slot >= 0 && slot < v.top) v.mark(slot).border = 0xFFFFFFFF;
    }

    // a book by what it holds
    private static String bookId(MenuView.Item item, String otherwise) {
        String key = io.muzy.sbc.core.AuctionPrices.key(item.tag);
        return key == null || key.isEmpty() ? otherwise : key;
    }

    // item.getInternalName, or what its name says when a shop leaves the id out
    private static String shopItemId(MenuView.Item item) {
        if (item.model.equals("enchanted_book")) return bookId(item, item.id);
        return item.id.isEmpty() ? GardenItems.idOf(item.name) : item.id;
    }

    // --- skymart ---

    // one shop item, null if not sold for copper or no price
    static ProfitPanel.Entry skyMartEntry(int slot, MenuView.Item item, double itemPrice, java.util.function.ToDoubleFunction<String> priceOf) {
        List<LoreCosts.Entry> costs = LoreCosts.read(item.lore);
        Long copper = null;
        double other = 0;
        for (LoreCosts.Entry c : costs) {
            if (c.id().equals(LoreCosts.COPPER)) copper = c.amount();
            else other += priceOf.applyAsDouble(c.id()) * c.amount();
        }
        if (copper == null || copper <= 0 || itemPrice <= 0) return null;
        double profit = itemPrice - other, factor = profit / copper;
        List<String> tip = new ArrayList<>(List.of(item.name, "", "§7Item price: §6" + Prices.coins(itemPrice)));
        if (other != 0) tip.add("§7Additional cost: §6" + Prices.coins(other));
        tip.add("§7Profit per purchase: §6" + Prices.coins(profit));
        tip.add("");
        tip.add("§7Copper amount: §c" + String.format(Locale.ROOT, "%,d", copper));
        tip.add("§7Profit per copper: §6" + Prices.coins(factor));
        String icon = shopItemId(item);
        return new ProfitPanel.Entry(item.name, icon == null || icon.isEmpty() ? null : icon, "§6§l" + Prices.coins(factor), factor, slot, tip);
    }

    static void skyMart(MenuView v) {
        List<ProfitPanel.Entry> table = new ArrayList<>();
        GardenItems.Source source = source(SKYMART, "garden_skymart_prices.price_source", "Instant sell");
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            String id = shopItemId(it);
            if (id == null) continue;
            ProfitPanel.Entry e = skyMartEntry(slot, it, GardenItems.price(id, source), GardenItems::price);
            if (e != null) table.add(e);
        }
        SKYMART_PANEL.set(table);
        frame(v);
    }

    // --- anita ---

    // MedalType: a medal in bronze
    static long bronzeFactor(String id) {
        if (id.equals(LoreCosts.GOLD_MEDAL)) return 8;
        if (id.equals(LoreCosts.SILVER_MEDAL)) return 2;
        if (id.equals(LoreCosts.BRONZE_MEDAL)) return 1;
        return 0;
    }

    // AnitaMedalProfit.readItem
    static ProfitPanel.Entry anitaEntry(int slot, MenuView.Item item, java.util.function.ToDoubleFunction<String> priceOf, java.util.function.Function<String, String> idOf) {
        if (!LoreCosts.hasTradeLine(item.lore)) return null;
        List<LoreCosts.Entry> required = LoreCosts.read(item.lore);
        Long bronze = null;
        double additional = 0;
        List<String> materials = new ArrayList<>();
        for (LoreCosts.Entry c : required) {
            long factor = bronzeFactor(c.id());
            if (factor > 0) {
                if (bronze == null) bronze = factor * c.amount();
                continue;
            }
            double price = priceOf.applyAsDouble(c.id()) * c.amount();
            additional += price;
            materials.add(" §7" + GameState.strip(ItemRepo.name(c.id())) + " §8x" + c.amount() + " §6" + Prices.coins(price));
        }
        // ignore items without medal cost, like the infinidirt wand
        if (bronze == null) return null;
        GardenItems.Amount named = GardenItems.readItemAmount(item.name);
        if (named == null) return null;
        String id = idOf.apply(named.name());
        if (id == null) id = item.id;
        // ItemCategory.ENCHANTED_BOOK: the book's own price, not any book's
        if (item.model.equals("enchanted_book")) id = bookId(item, id);
        double itemPrice = priceOf.applyAsDouble(id) * named.amount();
        if (itemPrice < 0) return null;
        double profit = itemPrice - additional, perBronze = profit / bronze;
        List<String> tip = new ArrayList<>(List.of(item.name, "", "§7Sell price: §6" + Prices.coins(itemPrice), "§7Additional cost: §6" + Prices.coins(additional)));
        tip.addAll(materials);
        tip.add("§7Profit per sell: §6" + Prices.coins(profit));
        tip.add("");
        tip.add("§7Bronze medals required: §c" + bronze);
        tip.add("§7Profit per bronze medal: §6" + Prices.coins(perBronze));
        return new ProfitPanel.Entry(item.name, id.isEmpty() ? null : id, (perBronze > 0 ? "§6" : "§c") + Prices.coins(perBronze), perBronze, slot, tip);
    }

    static void anita(MenuView v) {
        if (Visitors.open() != null) return;
        List<ProfitPanel.Entry> table = new ArrayList<>();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            ProfitPanel.Entry e = anitaEntry(slot, it, GardenItems::price, GardenItems::idOf);
            if (e != null) table.add(e);
        }
        ANITA_PANEL.set(table);
        frame(v);
    }

    // --- starlyn sisters ---

    // StarlynSisterType's coupon by shop, or null
    static String couponOf(String title) {
        return switch (title) {
            case "Agatha's Shop" -> "AGATHA_COUPON";
            case "Miria's Shop" -> "MIRIA_COUPON";
            default -> null;
        };
    }

    // isValidSlotNumber
    static boolean shopSlot(int slot) {
        return slot >= 9 && slot <= 44 && slot % 9 != 0 && slot % 9 != 8;
    }

    static void starlyn(MenuView v) {
        String coupon = couponOf(v.title);
        if (coupon == null) return;
        List<ProfitPanel.Entry> table = new ArrayList<>();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty() || !shopSlot(slot)) continue;
            String id = it.model.equals("enchanted_book") ? bookId(it, it.id) : GardenItems.idOf(it.name);
            if (id == null || id.isEmpty()) continue;
            double price = GardenItems.price(id), cost = 0;
            long coupons = 0;
            List<String> materials = new ArrayList<>();
            for (LoreCosts.Entry c : LoreCosts.read(it.lore)) {
                double p = GardenItems.price(c.id());
                cost += p * c.amount();
                if (c.id().equals(coupon)) coupons = c.amount();
                materials.add(" §7" + GameState.strip(ItemRepo.name(c.id())) + " §8x" + c.amount() + " §6" + Prices.coins(p * c.amount()));
            }
            double profit = price - cost, perCoupon = coupons > 0 ? profit / coupons : 0;
            List<String> tip = new ArrayList<>(List.of(it.name, "", "§7Sell price: §6" + Prices.coins(price), "§7Total cost: §6" + Prices.coins(cost)));
            tip.addAll(materials);
            tip.add("");
            tip.add("§7Profit per sell: §6" + Prices.coins(profit));
            if (coupons > 0) tip.add("§7Profit per coupon: §6" + Prices.coins(perCoupon));
            table.add(new ProfitPanel.Entry(it.name, id, "§6" + Prices.coins(perCoupon), perCoupon, slot, tip));
        }
        STARLYN_PANEL.set(table);
        frame(v);
    }

    // for pictures: a shop's panel after reading this menu
    public static SideHelper.Content panelForTest(String which, MenuView v) {
        switch (which) {
            case "skymart" -> {
                skyMart(v);
                return SKYMART_PANEL;
            }
            case "anita" -> {
                anita(v);
                return ANITA_PANEL;
            }
            default -> {
                starlyn(v);
                return STARLYN_PANEL;
            }
        }
    }

    private static SideHelper.Content panel(Screen s) {
        String t = GameState.strip(s.getTitle().getString()).trim();
        if (Features.on(SKYMART) && t.startsWith("SkyMart ") && "GARDEN".equals(GameState.island)) return SKYMART_PANEL;
        if (Features.on(ANITA) && t.equals("Anita") && Visitors.open() == null) return ANITA_PANEL;
        if (Features.on(STARLYN) && couponOf(t) != null) return STARLYN_PANEL;
        return null;
    }

    // --- anita's extra farming fortune ---

    private static Map<Integer, int[]> levelPrice() {
        if (levelPrice != null) return levelPrice;
        Map<Integer, int[]> out = new HashMap<>();
        try (var in = io.muzy.sbc.core.data.DataFiles.sh("AnitaUpgradeCosts.json")) {
            if (in != null) {
                JsonObject levels = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("level_price");
                for (Map.Entry<String, JsonElement> e : levels.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    out.put(Integer.parseInt(e.getKey()), new int[]{o.get("gold_medals").getAsInt(), o.get("jacob_tickets").getAsInt()});
                }
            }
        } catch (Exception e) {
            io.muzy.sbc.SbcMod.LOG.warn("No AnitaUpgradeCosts.json", e);
        }
        levelPrice = out;
        return out;
    }

    // whats left to pay from a tier on, {gold medals, tickets}. contributionFactor = what the next tier's line
    // says vs the table
    static int[] costToMax(int tier, Map<Integer, int[]> prices, double factor) {
        int gold = 0, tickets = 0;
        for (var e : prices.entrySet()) {
            if (e.getKey() > tier) {
                gold += e.getValue()[0];
                tickets += e.getValue()[1];
            }
        }
        return new int[]{gold, (int) (factor * tickets)};
    }

    // AnitaExtraFarmingFortune.onToolTip
    static void anitaTooltip(ItemStack stack, List<Component> lines) {
        MenuView v = MenuMarks.current();
        if (v == null || !Patterns.get("SH.garden.inventory.anita.extrafortune.inventory").matcher(v.title).matches()) return;
        if (!Patterns.get("SH.garden.inventory.anita.extrafortune.extrafarmingfortune").matcher(GameState.strip(stack.getHoverName().getString())).matches()) return;
        int farmingFortune = 0;
        double realTickets = 0;
        int click = -1, fortuneLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String plain = GameState.strip(lines.get(i).getString());
            Matcher ff = Patterns.get("SH.garden.inventory.anita.extrafortune.farmingfortune").matcher(plain);
            if (ff.matches()) {
                farmingFortune = (int) GameState.parseShort(ff.group("farmingFortune"));
                fortuneLine = i;
            }
            Matcher real = Patterns.get("SH.garden.inventory.anita.extrafortune.realamount.new").matcher(plain);
            if (real.matches()) realTickets = GameState.parseShort(real.group("realAmount"));
            if (click < 0 && Patterns.get("SH.garden.inventory.anita.extrafortune.clicktotrade").matcher(plain).matches()) click = i;
        }
        Map<Integer, int[]> prices = levelPrice();
        int[] next = prices.get(farmingFortune / 4 + 1);
        if (next == null) return;
        int upgrade = farmingFortune / 4;
        if (upgrade > 0 && GardenData.get().anitaUpgrade != upgrade) {
            GardenData.get().anitaUpgrade = upgrade;
            Store.markDirty();
        }
        double factor = next[1] > 0 ? realTickets / next[1] : 0;
        int[] cost = costToMax(upgrade, prices, factor);
        int index = click - 1;
        if (index < 0) return;
        double price = cost[1] * GardenItems.price("JACOBS_TICKET");
        lines.add(index, Component.literal("  §7Price: §6" + Prices.coins(price) + " coins"));
        lines.add(index, Component.literal("§aJacob Tickets §8x" + String.format(Locale.ROOT, "%,d", cost[1])));
        lines.add(index, Component.literal("§6Gold medals: §8x" + cost[0]));
        lines.add(index, Component.literal("§7Cost to max out"));
        lines.add(index, Component.empty());
        if (fortuneLine >= 0) lines.add(fortuneLine + 1, Component.literal("§7Current Tier: §e" + upgrade + "/" + prices.size()));
    }

    // --- carrolyn ---

    private static boolean carrolynItem(List<String> lore) {
        var p = Patterns.get("SH.garden.carrolyn.lore");
        for (String l : lore) if (p.matcher(l).matches()) return true;
        return false;
    }

    // CarrolynHelper.onTooltip
    static void carrolynTooltip(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty() || !carrolynItem(LegacyText.lore(stack))) return;
        lines.add(Component.empty());
        lines.add(Component.literal("§eClick to navigate to Carrolyn!"));
    }

    private static void navigate() {
        var p = Minecraft.getInstance().player;
        String said = Destinations.go("Carrolyn @ CRIMSON_ISLE");
        if (p != null && said != null) p.sendSystemMessage(Component.literal("§b[SBC] §e" + said));
    }

    // CarrolynHelper.onItemClick. the warp is your own click on the line
    private static boolean carrolynClick(boolean right) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !Features.on(CARROLYN) || !carrolynItem(LegacyText.lore(mc.player.getMainHandItem()))) return false;
        if ("CRIMSON_ISLE".equals(GameState.island)) {
            navigate();
            return false;
        }
        pendingCarrolyn = true;
        mc.player.sendSystemMessage(Component.literal("§b[SBC] §eCarrolyn is on the Crimson Isle. Click here to warp there!").withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand("/warp crimson"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to warp to the Crimson Isle")))));
        return false;
    }

    // EntityMovementData.onNextTeleport: the path to her starts once you're there
    public static void second() {
        if (pendingCarrolyn && "CRIMSON_ISLE".equals(GameState.island)) {
            pendingCarrolyn = false;
            navigate();
        }
    }

    // --- starlyn chat ---

    private static String sister = "", bracketPrefix = "", bracket = "", score = "", previous = "", pbType = "", pbTypeDisplay = "", pbCollected = "", pbMore = "";
    private static boolean inResults, inBest, hadBest;

    private static java.util.regex.Pattern starlyn(String key) {
        return Patterns.get("SH.foraging.starlyn-contest." + key);
    }

    private static boolean starlynOption(String key) {
        Feature f = Features.get(STARLYN_CHAT);
        Option o = f == null ? null : f.option(key);
        return o == null || o.bool();
    }

    private static void sayPlain(String text, String hover, String command) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        Component c = Component.literal(text);
        if (hover != null) c = c.copy().withStyle(s -> {
            var st = s.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
            return command == null ? st : st.withClickEvent(new ClickEvent.RunCommand(command));
        });
        boolean was = ChatBus.replaying;
        ChatBus.replaying = true;
        try {
            p.sendSystemMessage(c);
        } finally {
            ChatBus.replaying = was;
        }
    }

    // what CompactStarlynSisters makes of a line: null = through, "" = hide, other text replaces it
    static String compactStarlyn(String message, boolean results, boolean best) {
        if (best) {
            Matcher m = starlyn("coll-pb-during-contest").matcher(message);
            if (m.matches()) {
                return "§b" + m.group("foragingSister") + "'s §eContest: You broke a §dpersonal best §eof §b" + m.group("previousRecord") + " §6" + m.group("woodType") + " logs §ecollected during a contest! Keep it up!";
            }
            if (starlyn("keep-it-up-during-contest").matcher(message).matches()) return "";
            if (!inBest) {
                m = starlyn("collection-personal-best").matcher(message);
                if (m.matches()) {
                    inBest = true;
                    pbTypeDisplay = m.group("woodTypeDisplay");
                    pbType = m.group("woodType");
                    pbCollected = m.group("duringContestDisplay");
                    pbMore = m.group("aLotMore");
                    return "";
                }
            } else {
                m = starlyn("sweep-from-collection-pb").matcher(message);
                if (m.matches()) {
                    inBest = false;
                    return "§6" + pbType + " PB§e: Your §2Sweep §eincreased by " + m.group("sweepIncreaseDisplay") + " §efrom collecting " + pbCollected + " " + pbTypeDisplay + " §bLogs §e(" + pbMore + " §emore than your previous record)!";
                }
            }
        }
        if (!results) return null;
        if (!inResults) {
            Matcher m = starlyn("start-results").matcher(message);
            if (!m.matches()) return null;
            inResults = true;
            sister = m.group("foragingSister");
            bracketPrefix = m.group("formattingCode");
            bracket = m.group("bracket");
            hadBest = false;
            previous = "";
            return "";
        }
        Matcher m = starlyn("points-earned").matcher(message);
        if (m.matches()) {
            score = m.group("pointsString");
            String pb = m.group("personalBest");
            hadBest = pb != null && !pb.isEmpty();
            return "";
        }
        if (hadBest) {
            m = starlyn("previous-best").matcher(message);
            if (m.matches()) {
                previous = m.group("previousBest");
                return "";
            }
        }
        m = starlyn("claim-rewards").matcher(message);
        if (m.matches()) {
            inResults = false;
            String out = sister + "'s §eContest: You earned §r" + score + " §epoints, placing you in the " + bracketPrefix + bracket + " §ebracket!";
            if (hadBest && !previous.isEmpty()) out += " Your previous §dpersonal best §ewas " + previous + " §epoints!";
            return out;
        }
        return null;
    }

    // for a test
    static void resetStarlyn() {
        inResults = inBest = hadBest = false;
    }

    private static ChatBus.Result onStarlynChat(ChatBus.Line line) {
        if (!island("GALATEA", "THE_PARK")) return ChatBus.PASS;
        String out = compactStarlyn(LegacyText.of(line.component()), starlynOption("starlyn_compact.results"), starlynOption("starlyn_compact.personal_best"));
        if (out == null) return ChatBus.PASS;
        if (!out.isEmpty()) sayPlain(out, "§eClick to see the sisters", "/starlynsisters");
        return ChatBus.CANCEL;
    }

    public static void register() {
        Features.add(new Feature(SKYMART, "Garden", "SkyMart copper prices", "Beside SkyMart: what each item earns per copper when sold, the best first. Hover a line for the numbers.", "SkyHanni: SkyMart Copper Price")
                .when(() -> "GARDEN".equals(GameState.island))
                .option(Option.choice("garden_skymart_prices.price_source", "Price source", "", "Instant sell", "Instant buy", "Instant sell", "NPC")));
        MenuMarks.add(SKYMART, t -> t.startsWith("SkyMart "), GardenShops::skyMart);
        Features.add(new Feature(ANITA, "Garden", "Anita medal prices", "Beside Anita's shop: what each item earns per bronze medal, the best first; a gold medal is 8 and a silver medal 2 of them.", "SkyHanni: Medal Prices")
                .when(() -> GameState.inSkyBlock));
        MenuMarks.add(ANITA, "Anita", GardenShops::anita);
        Features.add(new Feature(ANITA_FORTUNE, "Garden", "Anita's Extra Farming Fortune", "On Anita's Extra Farming Fortune: your tier, and the medals, tickets and coins it costs to max it out.", "SkyHanni: Extra Farming Fortune")
                .when(() -> GameState.inSkyBlock)
                .tooltip(GardenShops::anitaTooltip));
        Features.add(new Feature(CARROLYN, "Garden", "Carrolyn fetch helper", "Her fetch items say so in their tooltip. Using one shows the way to Carrolyn, or offers the warp to the Crimson Isle in chat first.", "SkyHanni: Carrolyn Fetch Helper")
                .when(() -> GameState.inSkyBlock)
                .tooltip(GardenShops::carrolynTooltip));
        Interactions.onItemClick(GardenShops::carrolynClick);
        Features.add(new Feature(STARLYN, "Foraging", "Starlyn coupon prices", "Beside the shops of Agatha and Miria: what each item earns per coupon, the best first.", "SkyHanni: Coupon Prices")
                .when(() -> GameState.inSkyBlock));
        MenuMarks.add(STARLYN, t -> couponOf(t) != null, GardenShops::starlyn);
        Feature compact = new Feature(STARLYN_CHAT, "Foraging", "Compact Starlyn contests", "A sister's contest results in one line, and your log personal bests during a contest in one line.", "SkyHanni: Starlyn Contests")
                .when(() -> GameState.inSkyBlock)
                .option(Option.bool("starlyn_compact.results", "Compact results", "Your placement in a contest.", true))
                .option(Option.bool("starlyn_compact.personal_best", "Compact personal bests", "Log collection personal bests during a contest.", true));
        Features.add(compact);
        ChatBus.register(ChatBus.COMPACT + 5, STARLYN_CHAT, compact, GardenShops::onStarlynChat);
        SideHelper.add(GardenShops::panel);
    }
}
