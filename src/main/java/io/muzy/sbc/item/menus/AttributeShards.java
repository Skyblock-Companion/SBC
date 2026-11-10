// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemIcons;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.market.BazaarApi;
import io.muzy.sbc.item.market.Market;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;

// from skyhanni AttributeShardsData, AttributeShardOverlay, AttributesShardsInventory, HuntingBoxValue
// whats syphoned is read from the attribute menu, whats in the box from the hunting box, both per profile
// next to those menus a panel shows what the next tier and top tier of each shard cost on the bz. tiers from
// neu attribute_shards.json
public final class AttributeShards {
    private static final int SYPHONED = 0, IN_BOX = 1, ENABLED = 2;

    record Shard(String bazaarName, String displayName, String rarity, String internalName, String abilityName) {}

    // from skyhanni findTierAndAmountUntilNext
    record Progress(int tier, int toNext, int toMax) {}

    private static final Map<String, Shard> BY_BAZAAR = new LinkedHashMap<>(), BY_INTERNAL = new HashMap<>(), BY_ABILITY = new HashMap<>();
    private static final Map<String, int[]> LEVELLING = new HashMap<>();
    private static final Set<String> UNCONSUMABLE = new HashSet<>();
    private static boolean loaded;
    private static final Set<String> SEEN_IN_BOX = new HashSet<>();
    private static final Set<Integer> SEEN_PAGES = new HashSet<>();

    private AttributeShards() {}

    static void load() {
        if (loaded) return;
        loaded = true;
        try (InputStream in = io.muzy.sbc.core.data.DataFiles.neu("attribute_shards.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("attribute_levelling").entrySet()) {
                var a = e.getValue().getAsJsonArray();
                int[] tiers = new int[a.size()];
                for (int i = 0; i < tiers.length; i++) tiers[i] = a.get(i).getAsInt();
                LEVELLING.put(e.getKey(), tiers);
            }
            for (JsonElement e : o.getAsJsonArray("unconsumable_attributes")) UNCONSUMABLE.add(e.getAsString());
            for (JsonElement e : o.getAsJsonArray("attributes")) {
                JsonObject s = e.getAsJsonObject();
                Shard shard = new Shard(s.get("bazaarName").getAsString(), s.get("displayName").getAsString(), s.get("rarity").getAsString(), s.get("internalName").getAsString(), s.has("abilityName") ? s.get("abilityName").getAsString() : "");
                BY_BAZAAR.put(shard.bazaarName(), shard);
                BY_INTERNAL.put(shard.internalName(), shard);
                BY_ABILITY.put(shard.abilityName(), shard);
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the attribute shards", e);
        }
    }

    // skyhanni resolveAttributeShardName: "ATTRIBUTE_SHARD_NATURE_ELEMENTAL;1", "" if not a shard
    static String internal(MenuView.Item it) {
        if (!it.id.equals("ATTRIBUTE_SHARD")) return "";
        CompoundTag attributes = it.tag.getCompoundOrEmpty("attributes");
        if (attributes.size() != 1) return "";
        String name = attributes.keySet().iterator().next();
        return "ATTRIBUTE_SHARD_" + name.toUpperCase(Locale.ROOT) + ";" + attributes.getIntOr(name, 1);
    }

    static boolean isShard(String internal) {
        return internal.startsWith("ATTRIBUTE_SHARD_") && internal.endsWith(";1");
    }

    // slots a shard can be in, the middle of the chest
    static boolean validSlot(int slot) {
        return slot >= 9 && slot <= 44 && slot % 9 != 0 && slot % 9 != 8;
    }

    static int total(Shard s, int currentTier, int toNextTier) {
        int[] tiers = LEVELLING.get(s.rarity());
        if (tiers == null) return 0;
        int cumulative = 0;
        for (int i = 0; i < Math.min(currentTier + 1, tiers.length); i++) cumulative += tiers[i];
        return cumulative - toNextTier;
    }

    static Progress progress(String rarity, int total) {
        int[] tiers = LEVELLING.get(rarity);
        if (tiers == null) return new Progress(0, 0, 0);
        int tier = 0, cumulative = 0, toNext = 0;
        for (int amount : tiers) {
            cumulative += amount;
            if (cumulative > total && toNext == 0) toNext = cumulative - total;
            if (cumulative > total) continue;
            tier++;
        }
        return new Progress(tier, toNext, Math.max(0, cumulative - total));
    }

    private static int[] data(String bazaar) {
        return Store.I.attributeShards.computeIfAbsent(bazaar, k -> new int[3]);
    }

    private static int maxShards() {
        load();
        return BY_BAZAAR.size() - UNCONSUMABLE.size();
    }

    // reading the menus

    // skyhanni processAttributeMenuItems, tier as the stack count (AttributesShardsInventory)
    static void attributeMenu(MenuView v) {
        load();
        boolean tierNumbers = MenuMarks.on("attribute_shards", "attribute_shards.tier_numbers");
        var name = Patterns.get("SH.inventory.attributeshards.name.colorless");
        var syphon = Patterns.get("SH.inventory.attributeshards.syphon.amount.colorless");
        var state = Patterns.get("SH.inventory.attributeshards.state.colorless");
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            String internal = internal(it);
            if (!isShard(internal)) continue;
            Matcher nm = name.matcher(it.clean);
            int tier = nm.matches() && nm.group("tier") != null ? GameState.roman(nm.group("tier")) : 0;
            Shard shard = BY_INTERNAL.get(internal);
            if (shard != null && !UNCONSUMABLE.contains(shard.bazaarName())) {
                Matcher sm = it.firstClean(syphon);
                int[] d = data(shard.bazaarName());
                int total = total(shard, tier, sm == null ? 0 : Integer.parseInt(sm.group("amount")));
                Matcher st = it.firstClean(state);
                int enabled = st == null ? d[ENABLED] : st.group("state").equals("Yes") ? 1 : 0;
                if (d[SYPHONED] != total || d[ENABLED] != enabled) {
                    d[SYPHONED] = total;
                    d[ENABLED] = enabled;
                    Store.markDirty();
                }
            }
            if (tierNumbers) {
                String color = tier == 0 ? "§c" : tier <= 5 ? "§e" : tier <= 9 ? "§a" : tier == 10 ? "§6" : "§f";
                v.number(slot, color + tier);
            }
        }
        // skyhanni addAllMissingShards: before advanced mode the menu doesnt list them all
        var advanced = Patterns.get("SH.inventory.attributeshards.advanced.mode.colorless");
        if (v.item(52).firstClean(advanced) != null && Store.I.attributeShards.size() <= 30) {
            for (Shard s : BY_BAZAAR.values()) if (!UNCONSUMABLE.contains(s.bazaarName())) Store.I.attributeShards.computeIfAbsent(s.bazaarName(), k -> new int[3]);
        }
    }

    // one line of the hunting box table
    record BoxLine(Shard shard, int slot, int owned, double instantSell, double instantBuy) {}

    private static List<BoxLine> box = List.of();
    private static long boxShards;
    private static double boxSell, boxBuy;
    private static boolean boxError;

    // skyhanni onInventoryUpdated for the box + HuntingBoxValue.processInventory
    static void huntingBox(MenuView v) {
        load();
        var owned = Patterns.get("SH.inventory.attributeshards.owned.colorless");
        var loreName = Patterns.get("SH.inventory.attributeshards.name.lore.colorless");
        var syphon = Patterns.get("SH.inventory.attributeshards.syphon.amount.colorless");
        var query = Patterns.get("SH.inventory.attributeshards.hunting-box.search-query.colorless");
        var page = Patterns.get("SH.inventory.attributeshards.hunting-box.page");
        List<BoxLine> lines = new ArrayList<>();
        Set<String> inBox = new HashSet<>();
        boolean search = false;
        boxShards = 0;
        boxSell = boxBuy = 0;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (!validSlot(slot)) {
                if (it.firstClean(query) != null) search = true;
                continue;
            }
            String internal = internal(it);
            if (!isShard(internal)) continue;
            Shard shard = BY_INTERNAL.get(internal);
            if (shard == null) continue;
            inBox.add(shard.bazaarName());
            int tier = 0, toNext = 0, amount = -1;
            for (String line : it.cleanLore) {
                Matcher m = loreName.matcher(line);
                if (m.matches() && m.group("tier") != null) tier = GameState.roman(m.group("tier"));
                m = syphon.matcher(line);
                if (m.matches()) toNext = Integer.parseInt(m.group("amount"));
                m = owned.matcher(line);
                if (m.matches()) amount = Integer.parseInt(m.group("amount").replace(",", ""));
            }
            int[] d = data(shard.bazaarName());
            int total = total(shard, tier, toNext);
            if (!UNCONSUMABLE.contains(shard.bazaarName()) && d[SYPHONED] != total) {
                d[SYPHONED] = total;
                Store.markDirty();
            }
            if (amount < 0) continue;
            if (d[IN_BOX] != amount) {
                d[IN_BOX] = amount;
                Store.markDirty();
            }
            Prices.Product p = Prices.get(shard.bazaarName());
            double sell = p == null ? 0 : p.sell(), buy = p == null ? 0 : p.buy();
            boxShards += amount;
            boxSell += sell * amount;
            boxBuy += buy * amount;
            lines.add(new BoxLine(shard, slot, amount, sell, buy));
        }
        lines.sort(Comparator.comparingDouble((BoxLine l) -> -l.instantSell() * l.owned()));
        box = lines;
        boxError = lines.isEmpty() && !v.item(10).empty();
        // skyhanni syncHuntingBoxAmounts: once every page was seen, whatever was on none of them is gone
        Matcher pm = page.matcher(v.title);
        if (search || !pm.matches()) return;
        int current = pm.group("page") == null ? 1 : Integer.parseInt(pm.group("page")), pages = pm.group("pages") == null ? 1 : Integer.parseInt(pm.group("pages"));
        if (current == 1) {
            SEEN_IN_BOX.clear();
            SEEN_PAGES.clear();
        }
        SEEN_IN_BOX.addAll(inBox);
        SEEN_PAGES.add(current);
        if (SEEN_PAGES.size() < pages) return;
        for (Map.Entry<String, int[]> e : Store.I.attributeShards.entrySet()) if (!SEEN_IN_BOX.contains(e.getKey())) e.getValue()[IN_BOX] = 0;
    }

    // skyhanni FusionData: what the machine in confirm fusion takes and gives
    private record Fusion(String first, int firstAmount, String second, int secondAmount, String output) {}

    private static Fusion fusion;

    static void confirmFusion(MenuView v) {
        load();
        var required = Patterns.get("SH.inventory.attributeshards.fuse.required.colorless");
        Matcher a = v.item(12).firstClean(required), b = v.item(14).firstClean(required);
        String output = internal(v.item(31));
        if (a == null || b == null || output.isEmpty()) return;
        fusion = new Fusion(internal(v.item(12)), Integer.parseInt(a.group("amount")), internal(v.item(14)), Integer.parseInt(b.group("amount")), output);
    }

    private static void moveInternal(String internal, int amount) {
        Shard s = BY_INTERNAL.get(internal);
        if (s == null) return;
        int[] d = data(s.bazaarName());
        d[IN_BOX] = Math.max(0, d[IN_BOX] + amount);
        Store.markDirty();
    }

    private static void resetBox() {
        for (int[] d : Store.I.attributeShards.values()) d[IN_BOX] = 0;
        Store.markDirty();
    }

    private static Shard byName(String shardName) {
        load();
        for (Shard s : BY_BAZAAR.values()) if (s.displayName().equalsIgnoreCase(shardName)) return s;
        return null;
    }

    private static void addToBox(String shardName, int amount) {
        Shard s = byName(shardName.replaceAll(" Shards?$", ""));
        if (s == null) return;
        int[] d = data(s.bazaarName());
        d[IN_BOX] = Math.max(0, d[IN_BOX] + amount);
        Store.markDirty();
    }

    // skyhanni onChat lines that move shards in and out of the box, syphon and enable
    private static ChatBus.Result chat(ChatBus.Line line) {
        String plain = GameState.strip(line.plain());
        if (plain.isEmpty()) return ChatBus.PASS;
        load();
        Matcher m = Patterns.get("SH.inventory.attributeshards.chat.syphoned.colorless").matcher(plain);
        if (m.matches()) {
            Shard s = BY_ABILITY.get(m.group("attributeName"));
            if (s == null) return ChatBus.PASS;
            int[] d = data(s.bazaarName());
            d[SYPHONED] = total(s, Integer.parseInt(m.group("level")), Integer.parseInt(m.group("untilNext")));
            d[IN_BOX] = Math.max(0, d[IN_BOX] - Integer.parseInt(m.group("amount")));
            Store.markDirty();
            return ChatBus.PASS;
        }
        m = Patterns.get("SH.inventory.attributeshards.chat.syphoned.maxed.colorless").matcher(plain);
        if (m.matches()) {
            Shard s = BY_ABILITY.get(m.group("attributeName"));
            if (s == null) return ChatBus.PASS;
            int[] d = data(s.bazaarName());
            d[SYPHONED] = total(s, 10, 0);
            d[IN_BOX] = Math.max(0, d[IN_BOX] - Integer.parseInt(m.group("amount")));
            Store.markDirty();
            return ChatBus.PASS;
        }
        for (String key : new String[]{"chat.enabled.colorless", "chat.disabled.colorless"}) {
            m = Patterns.get("SH.inventory.attributeshards." + key).matcher(plain);
            if (!m.matches()) continue;
            Shard s = BY_ABILITY.get(m.group("attributeName"));
            if (s != null) data(s.bazaarName())[ENABLED] = key.startsWith("chat.enabled") ? 1 : 0;
            return ChatBus.PASS;
        }
        m = Patterns.get("SH.inventory.attributeshards.fusion.shard.colorless").matcher(plain);
        if (m.matches()) {
            if (fusion != null) {
                String amount = group(m, "amount");
                moveInternal(fusion.output(), amount == null ? 1 : Integer.parseInt(amount));
                moveInternal(fusion.first(), -fusion.firstAmount());
                moveInternal(fusion.second(), -fusion.secondAmount());
            }
            return ChatBus.PASS;
        }
        for (String key : new String[]{"caught.shards.colorless", "caught-multiple.shards", "loot.share.shard.colorless", "charmed.shard.colorless", "sent.to.hunting.box.colorless", "captured.shard", "floor-drop.shard", "given.shards", "boss.shard"}) {
            m = Patterns.get("SH.inventory.attributeshards." + key).matcher(plain);
            if (!m.matches()) continue;
            String amount = group(m, "amount");
            addToBox(m.group("shardName"), amount == null ? 1 : Integer.parseInt(amount));
            return ChatBus.PASS;
        }
        return ChatBus.PASS;
    }

    // shards you follow, from skyhanni ShardTrackerDisplay

    public static net.minecraft.client.KeyMapping trackKey;

    // one followed shard: how many in your box, how many you need. goal 0 = nothing more to get
    public record TrackedRow(String name, int have, int goal) {}

    // bz name of a shard from its chat name, with or without the word Shard, or null
    public static String bazaarNameOf(String shardName) {
        Shard s = byName(shardName.replaceAll(" Shards?$", ""));
        return s == null ? null : s.bazaarName();
    }

    // skyhanni getActiveLevel: tier of an attribute you have on, 0 if off or unknown
    public static int activeLevel(String bazaar) {
        load();
        Shard s = BY_BAZAAR.get(bazaar);
        if (s == null) return 0;
        int[] d = data(bazaar);
        return d[ENABLED] == 0 ? 0 : progress(s.rarity(), d[SYPHONED]).tier();
    }

    // from skyhanni getAmountInHuntingBox
    public static int inBox(String shardName) {
        Shard s = byName(shardName.replaceAll(" Shards?$", ""));
        return s == null ? 0 : data(s.bazaarName())[IN_BOX];
    }

    public static boolean tracked(String bazaar) {
        return Store.I.trackedShards.containsKey(bazaar);
    }

    public static List<TrackedRow> trackedRows() {
        if (Store.I.trackedShards.isEmpty()) return List.of();
        load();
        List<TrackedRow> out = new ArrayList<>();
        for (var e : Store.I.trackedShards.entrySet()) {
            Shard s = BY_BAZAAR.get(e.getKey());
            if (s == null) continue;
            int[] d = data(s.bazaarName());
            int goal = e.getValue() >= 0 ? e.getValue() : progress(s.rarity(), d[SYPHONED]).toMax();
            out.add(new TrackedRow(s.displayName(), d[IN_BOX], goal));
        }
        return out;
    }

    private static void toggleTracked(String bazaar) {
        if (Store.I.trackedShards.remove(bazaar) == null) Store.I.trackedShards.put(bazaar, -1);
        Store.markDirty();
    }

    // from skyhanni ShardTrackerDisplay.isInsideShardsMenu
    private static boolean inShardMenu(String title) {
        return Patterns.get("SH.inventory.attributeshards.attribute-menu").matcher(title).matches() || Patterns.get("SH.inventory.attributeshards.hunting-box").matcher(title).matches()
                || Patterns.get("SH.inventory.attributeshards.confirm-fusion").matcher(title).matches() || Patterns.get("SH.inventory.attributeshards.fusion-box").matcher(title).matches() || Patterns.get("SH.inventory.attributeshards.shard-fusion").matcher(title).matches();
    }

    // key pressed in a menu, true if it did something
    public static boolean trackHovered(net.minecraft.client.input.KeyEvent event) {
        if (trackKey == null || !trackKey.matches(event) || !Features.on("shard_tracker")) return false;
        MenuView v = MenuMarks.current();
        int slot = MenuMarks.hoveredSlot();
        if (v == null || slot < 0 || !inShardMenu(v.title)) return false;
        load();
        String internal = internal(v.item(slot));
        for (Shard s : BY_BAZAAR.values()) {
            if (!s.internalName().equals(internal)) continue;
            toggleTracked(s.bazaarName());
            return true;
        }
        return false;
    }

    // skyhanni importFromSkyShards: recipe copied from skyshards (gzipped json in base64 after a colon) replaces
    // what you follow
    // only reads the clipboard when you ask
    public static String importFromClipboard(String clipboard) {
        String[] split = clipboard == null ? new String[0] : clipboard.split(":");
        if (split.length < 2 || !split[0].contains("Recipe>(V")) return "§cMake sure you have a valid recipe copied.";
        try (var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(split[1].trim())))) {
            var data = com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            load();
            Store.I.trackedShards.clear();
            int n = 0;
            for (var e : data) {
                var o = e.getAsJsonObject();
                if (o.has("source") && !o.get("source").isJsonNull() && !o.get("source").getAsString().equals("Direct")) continue;
                Shard s = byName(o.get("name").getAsString());
                if (s == null) s = BY_ABILITY.get(o.get("name").getAsString());
                if (s == null) continue;
                Store.I.trackedShards.put(s.bazaarName(), o.get("needed").getAsInt());
                n++;
            }
            Store.markDirty();
            return "§aFollowing " + n + " shards from the recipe.";
        } catch (Exception e) {
            return "§cMake sure you have a valid recipe copied.";
        }
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // the panels

    private static final int ROW = 16;

    private static double price(Shard s) {
        Prices.Product p = Prices.get(s.bazaarName());
        if (p == null) return 0;
        return Features.get("attribute_shards").option("attribute_shards.price_source").choice().equals("Buy order") ? p.sell() : p.buy();
    }

    private static int onOrder(Shard s) {
        Integer byId = BazaarApi.buyOrders.get(s.bazaarName());
        if (byId == null) byId = BazaarApi.buyOrders.get(s.internalName());
        return byId == null ? 0 : byId;
    }

    // skyhanni createShardRenderable + its numbers
    private record Line(Shard shard, Progress progress, int inBox, int ordered, int amountNext, int amountMax, double each) {
        double priceNext() {
            return each * amountNext;
        }

        double priceMax() {
            return each * amountMax;
        }
    }

    private static final class OverlayPanel implements SideHelper.Content {
        private List<Line> lines = List.of();
        private int unlocked, maxed, levels, known;
        private double toMax;
        private Set<String> current = Set.of();

        @Override
        public String title() {
            return "Attribute Shards";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("prismarine_shard");
        }

        @Override
        public int accent() {
            return 0xFF7DD3FC;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Cheapest next", "Cheapest top"};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            MenuView v = MenuMarks.current();
            if (v == null) return;
            load();
            Feature f = Features.get("attribute_shards");
            boolean box = f.option("attribute_shards.include_box").bool(), orders = f.option("attribute_shards.include_orders").bool();
            boolean onlyHere = f.option("attribute_shards.only_current").bool(), hideMaxed = f.option("attribute_shards.hide_maxed").bool(), notUnlocked = f.option("attribute_shards.only_not_unlocked").bool();
            Set<String> here = new HashSet<>();
            for (int i = 0; i < v.top; i++) {
                String id = internal(v.items[i]);
                if (!id.isEmpty()) here.add(id);
            }
            current = here;
            List<Line> out = new ArrayList<>();
            unlocked = maxed = levels = 0;
            toMax = 0;
            known = 0;
            for (Map.Entry<String, int[]> e : Store.I.attributeShards.entrySet()) {
                Shard s = BY_BAZAAR.get(e.getKey());
                if (s == null || UNCONSUMABLE.contains(s.bazaarName())) continue;
                known++;
                if (onlyHere && !here.contains(s.internalName())) continue;
                Progress p = progress(s.rarity(), e.getValue()[SYPHONED]);
                if (p.tier() == 10) maxed++;
                if (p.tier() > 0) {
                    unlocked++;
                    levels += p.tier();
                }
                int inBox = box ? e.getValue()[IN_BOX] : 0, ordered = orders ? onOrder(s) : 0, covered = inBox + ordered;
                Line line = new Line(s, p, inBox, ordered, Math.max(0, p.toNext() - covered), Math.max(0, p.toMax() - covered), price(s));
                toMax += line.priceMax();
                if (hideMaxed && p.tier() == 10) continue;
                if (notUnlocked && p.tier() > 0) continue;
                out.add(line);
            }
            lines = out;
        }

        private int total() {
            return Features.get("attribute_shards").option("attribute_shards.only_current").bool() ? current.size() : maxShards();
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2, top = total();
            SideHelper.tile(ui, x, y, half, "Found", unlocked + "/" + top, "", Modern.AQUA);
            SideHelper.tile(ui, x + half + 3, y, w - half - 3, "Maxed", maxed + "/" + top, "", Modern.GOLD);
            int ry = y + 26;
            Modern.row(ui, "Levels", "§f" + levels + "§7/" + top * 10, x, ry, w, Modern.LABEL, Modern.INK);
            Modern.meter(ui, x, ry + 9, w, 2, top == 0 ? 0 : levels / (double) (top * 10), Modern.AQUA);
            ry += 12;
            if (toMax > 0) {
                Modern.row(ui, "To max", "§6" + Prices.coins(toMax), x, ry, w, Modern.LABEL, Modern.INK);
                ry += 10;
            }
            if (known < maxShards()) {
                int missing = maxShards() - known;
                ui.textShadow(ui.ellipsize("§c" + missing + " not read yet: open /am", w), x, ry, Modern.DIM);
                ry += 10;
            }
            return ry - y + 2;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            List<Line> sorted = new ArrayList<>(lines);
            sorted.sort(Comparator.comparingDouble(tab == 0 ? Line::priceNext : Line::priceMax));
            for (Line l : sorted) y += row(ui, l, tab, x, y, w);
            if (y == y0) y += SideHelper.emptyText(ui, "No shards to show.", x, y, w);
            return y - y0;
        }

        private int row(Ui ui, Line l, int tab, int x, int y, int w) {
            if (y + ROW < ui.clipY0 || y > ui.clipY1) return ROW;
            boolean hv = ui.hover(x, y, w, ROW);
            if (hv) ui.rect(x, y, w, ROW, 0x22FFFFFF);
            ui.icon(ItemIcons.of(l.shard().internalName()), x + 1, y + 1, 14 / 16f);
            int tier = l.progress().tier(), amount = tab == 0 ? l.amountNext() : l.amountMax();
            double cost = tab == 0 ? l.priceNext() : l.priceMax();
            String right = tier == 10 ? "§aMaxed" : amount == 0 ? (l.ordered() != 0 ? "§eOn order" : l.inBox() != 0 ? "§aIn your box" : "§aNothing needed") : (cost <= 0 ? "§7no price" : (l.ordered() != 0 ? "§b" : "§6") + Prices.coins(cost));
            int rw = ui.width(right);
            ui.textShadow(ui.ellipsize(l.shard().displayName(), w - rw - 42), x + 17, y + 4, Modern.LABEL);
            ui.textShadow("§7T§e" + tier, x + w - rw - 20, y + 4, Modern.INK);
            ui.textShadow(right, x + w - rw - 2, y + 4, Modern.INK);
            if (hv) ui.tooltip(tip(l, amount));
            if (amount > 0) ui.click(x, y, w, ROW, () -> buy(l.shard(), amount));
            return ROW;
        }

        private static String[] tip(Line l, int amount) {
            List<String> t = new ArrayList<>();
            t.add("§f" + l.shard().displayName());
            int tier = l.progress().tier();
            t.add("§7Tier: §e" + tier);
            t.add("§7Each: §6" + Prices.coins(l.each()));
            t.add("§7In your box: §a" + String.format(Locale.ROOT, "%,d", l.inBox()));
            if (tier < 10) {
                if (tier != 9 && l.amountNext() != 0) t.add("§7Next tier: §a" + String.format(Locale.ROOT, "%,d", l.amountNext()) + " §7for §6" + Prices.coins(l.priceNext()));
                if (l.amountMax() != 0) t.add("§7Top tier: §a" + String.format(Locale.ROOT, "%,d", l.amountMax()) + " §7for §6" + Prices.coins(l.priceMax()));
            }
            if (l.ordered() != 0) t.add("§e" + String.format(Locale.ROOT, "%,d", l.ordered()) + "x §7on order");
            t.add("");
            t.add(amount > 0 ? "§eClick to look up " + String.format(Locale.ROOT, "%,d", amount) + "x on the Bazaar" : "§7Nothing to buy right now");
            return t.toArray(new String[0]);
        }
    }

    private static void buy(Shard s, int amount) {
        MenuHighlights.bazaar(s.displayName() + " Shard", amount);
    }

    private static final class BoxPanel implements SideHelper.Content {
        @Override
        public String title() {
            return "Hunting Box Value";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("chest");
        }

        @Override
        public int accent() {
            return 0xFFFFD166;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Shards " + box.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            MenuMarks.current();
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            SideHelper.tile(ui, x, y, half, "Sell now", Prices.coins(boxSell), "", Modern.GOLD);
            SideHelper.tile(ui, x + half + 3, y, w - half - 3, "Buy now", Prices.coins(boxBuy), "", Modern.GOLD);
            Modern.row(ui, "Shards", "§a" + String.format(Locale.ROOT, "%,d", boxShards), x, y + 26, w, Modern.LABEL, Modern.INK);
            return 36;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (BoxLine l : box) {
                boolean hv = ui.hover(x, y, w, ROW);
                if (hv) ui.rect(x, y, w, ROW, 0x22FFFFFF);
                ui.icon(ItemIcons.of(l.shard().internalName()), x + 1, y + 1, 14 / 16f);
                String right = "§6" + Prices.coins(l.instantSell() * l.owned());
                int rw = ui.width(right);
                ui.textShadow(ui.ellipsize(l.shard().displayName() + " §8x" + l.owned(), w - rw - 22), x + 17, y + 4, Modern.LABEL);
                ui.textShadow(right, x + w - rw - 2, y + 4, Modern.INK);
                if (hv) ui.tooltip("§f" + l.shard().displayName(), "§7Sell now: §6" + Prices.coins(l.instantSell()) + " §7each", "§7Buy now: §6" + Prices.coins(l.instantBuy()) + " §7each", "", "§7Owned: §a" + l.owned(), "§7Sell all: §6" + Prices.coins(l.instantSell() * l.owned()), "§7Buy all: §6" + Prices.coins(l.instantBuy() * l.owned()));
                y += ROW;
            }
            if (y == y0) y += SideHelper.emptyText(ui, boxError ? "These shards could not be read." : "The box is empty.", x, y, w);
            return y - y0;
        }
    }

    private static final OverlayPanel OVERLAY = new OverlayPanel();
    private static final BoxPanel BOX = new BoxPanel();

    static SideHelper.Content overlayForTest() {
        return OVERLAY;
    }

    static SideHelper.Content boxForTest() {
        return BOX;
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on("attribute_shards")) return null;
        String title = GameState.strip(s.getTitle().getString()).trim();
        Feature f = Features.get("attribute_shards");
        if (f.option("attribute_shards.hunting_box_value").bool() && Patterns.get("SH.inventory.attributeshards.hunting-box").matcher(title).matches()) return BOX;
        if (f.option("attribute_shards.overlay").bool() && (Patterns.get("SH.inventory.attributeshards.attribute-menu").matcher(title).matches() || Patterns.get("SH.inventory.attributeshards.bazaar.shards.inventory").matcher(title).matches())) return OVERLAY;
        return null;
    }

    // the skyhanni AttributeOverlay / skyblocker marks for items that still have two attributes live in ItemMarks
    static void register(BooleanSupplier skyblock) {
        Feature f = new Feature("attribute_shards", "Menus", "Attribute shards", "The tier of each shard in the Attribute Menu, disabled attributes in red, what the next tier of each shard costs, and what your Hunting Box is worth.", "SkyHanni: Attribute Shards · Skyblocker: attribute level slot text")
                .when(skyblock)
                .option(Option.bool("attribute_shards.overlay", "Price panel", "Beside the Attribute Menu and the Bazaar's shard page.", true))
                .option(Option.bool("attribute_shards.tier_numbers", "Tier on each shard", "Where the stack count goes.", true))
                .option(Option.bool("attribute_shards.highlight_disabled", "Highlight disabled attributes", "", true))
                .option(Option.bool("attribute_shards.hunting_box_value", "Hunting Box value", "Beside the Hunting Box.", true))
                .option(Option.bool("attribute_shards.hide_maxed", "Hide maxed shards", "", true))
                .option(Option.bool("attribute_shards.only_not_unlocked", "Only shards not unlocked", "", false))
                .option(Option.bool("attribute_shards.only_current", "Only shards on this page", "", false))
                .option(Option.bool("attribute_shards.include_box", "Count the Hunting Box", "What the box holds is not bought again.", true))
                .option(Option.bool("attribute_shards.include_orders", "Count Bazaar orders", "What you have on order is not bought again.", true))
                .option(Option.choice("attribute_shards.price_source", "Price", "", "Instant buy", "Instant buy", "Buy order"));
        Features.add(f);
        MenuMarks.add("attribute_shards", Patterns.get("SH.inventory.attributeshards.attribute-menu"), AttributeShards::attributeMenu);
        MenuMarks.add("attribute_shards", Patterns.get("SH.inventory.attributeshards.hunting-box"), AttributeShards::huntingBox);
        MenuMarks.add("attribute_shards", Patterns.get("SH.inventory.attributeshards.confirm-fusion"), AttributeShards::confirmFusion);
        ChatBus.register(ChatBus.OBSERVE, "attribute_shards", f, AttributeShards::chat);
        SideHelper.add(AttributeShards::panel);
        io.muzy.sbc.core.Commands.describe("importshards", "Follow the shards of a recipe copied from SkyShards");
        Safe.commands("AttributeShards.commands", (d, r) -> d.register(ClientCommands.literal("sbc").then(ClientCommands.literal("importshards").executes(c -> {
            c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(importFromClipboard(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard())));
            return 1;
        }))));
        Safe.commands("AttributeShards.commands#2", (d, r) -> d.register(ClientCommands.literal("sbc").then(ClientCommands.literal("resethuntingbox").executes(c -> {
            resetBox();
            c.getSource().sendFeedback(Component.literal("§b[SBC] §fThe saved Hunting Box is cleared. Open the box to read it again."));
            return 1;
        }))));
    }
}
