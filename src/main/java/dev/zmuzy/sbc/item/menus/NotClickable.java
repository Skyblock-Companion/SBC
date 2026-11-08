// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.menus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.market.BazaarApi;
import dev.zmuzy.sbc.mc.Interactions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;

// from skyhanni HideNotClickableItems: in a menu that only takes some items, the rest gets dimmed in your
// inventory with a reason
// skipped the composter and motes grubber, they need data from features we dont have
public final class NotClickable {
    // why an item is dimmed. green = the menu does take it
    record Verdict(String reason, boolean green, boolean bypass) {}

    private record Filter(List<String> equals, List<String> startsWith, List<String> endsWith, List<String> contains, List<String> word) {
        boolean match(String s) {
            for (String e : equals) if (e.equals(s)) return true;
            for (String e : startsWith) if (s.startsWith(e)) return true;
            for (String e : endsWith) if (s.endsWith(e)) return true;
            for (String e : contains) if (s.contains(e)) return true;
            for (String e : word) if (Arrays.asList(s.split(" ")).contains(e)) return true;
            return false;
        }
    }

    private static final Filter EMPTY = new Filter(List.of(), List.of(), List.of(), List.of(), List.of());
    private static Filter npcSell = EMPTY, storage = EMPTY, trade = EMPTY, auction = EMPTY;
    private static final List<String> SALVAGE = new ArrayList<>();
    private static boolean loaded;
    private static final Set<String> BIRD_FOOD = Set.of("BAG_OF_SEEDS", "WRIGGLEWORM", "YOGI_BERRY");
    private static final Set<String> ARMOR_AND_EQUIPMENT = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS", "NECKLACE", "BELT", "CLOAK", "GLOVES", "BRACELET", "CARNIVAL_MASK");
    private static final Verdict[] LOWER = new Verdict[0];
    private static Verdict[] verdicts = LOWER;
    private static boolean wasBypassed;

    private NotClickable() {}

    private static List<String> list(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o != null && o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }

    private static Filter filter(JsonObject root, String key) {
        JsonObject o = root.getAsJsonObject(key);
        return o == null ? EMPTY : new Filter(list(o, "equals"), list(o, "startsWith"), list(o, "endsWith"), list(o, "contains"), list(o, "containsWord"));
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        JsonObject root = Repo.constant("HideNotClickableItems.json");
        if (root == null) return;
        npcSell = filter(root, "hide_npc_sell");
        storage = filter(root, "hide_in_storage");
        trade = filter(root, "hide_player_trade");
        auction = filter(root, "not_auctionable");
        JsonObject salvage = root.getAsJsonObject("salvage");
        SALVAGE.addAll(list(salvage, "items"));
        for (String armor : list(salvage, "armor")) {
            SALVAGE.add(armor + " Helmet");
            SALVAGE.add(armor + " Chestplate");
            SALVAGE.add(armor + " Leggings");
            SALVAGE.add(armor + " Boots");
        }
    }

    private static boolean menuItem(MenuView.Item it) {
        return it.id.equals("SKYBLOCK_MENU");
    }

    private static boolean soulbound(MenuView.Item it) {
        return it.cleanLore.contains("* Soulbound *");
    }

    private static boolean anySoulbound(MenuView.Item it) {
        return soulbound(it) || it.cleanLore.contains("* Co-op Soulbound *");
    }

    private static boolean sack(MenuView.Item it) {
        return it.id.endsWith("_SACK") && it.clean.endsWith(" Sack");
    }

    private static boolean recombobulated(MenuView.Item it) {
        return it.tag.getIntOr("rarity_upgrades", 0) > 0;
    }

    private static boolean donated(MenuView.Item it) {
        return it.tag.getIntOr("donated_museum", 0) > 0;
    }

    // skyhanni ShiftClickNpcSell.inInventory: last line of the item 4 before the end of the menu
    static boolean npcMenu(MenuView v) {
        if (v.top < 5) return false;
        return Patterns.get("SH.inventory.npc.sell.lore").matcher(v.item(v.top - 5).lastLore()).matches();
    }

    // is this name what the ah menus call themselves
    private static boolean auctionHouse(String t) {
        return t.equals("Co-op Auction House") || t.equals("Auction House") || t.equals("Create BIN Auction") || t.equals("Create Auction");
    }

    private static final Verdict TAKES = new Verdict("", true, true), NOTHING = new Verdict("", false, true);

    private static Verdict no(String reason, boolean green, boolean bypass) {
        return new Verdict(reason, green, bypass);
    }

    // skyhanni onItemNotClickable checks, in order
    static Verdict judge(MenuView v, MenuView.Item it, boolean npc, boolean inRift, boolean privateIsland) {
        String t = v.title, name = it.clean;
        if (!inRift && npc && !v.item(29).clean.equals("Accept Offer")) {
            String bare = name.endsWith(" x" + it.count) ? name.substring(0, name.length() - (" x" + it.count).length()) : name;
            boolean sellable = it.loreHas("§eClick to sell!") || (!it.id.equals("PET") && ItemRepo.npcPrice(it.id) > 0);
            if (!sellable) return no("This item cannot be sold at the NPC!", true, true);
            if (donated(it)) return no("This item cannot be sold at the NPC! (Donated to Museum)", true, true);
            if (recombobulated(it)) return no("This item should not be sold at the NPC! (Recombobulated)", true, true);
            if (MenuMarks.on("not_clickable", "not_clickable.protect_rare") && !npcSell.match(bare)) return no("This item should not be sold at the NPC!", true, true);
            return TAKES;
        }
        if (t.contains("Ender Chest") || t.contains("Backpack") || t.equals("Storage")) {
            if (menuItem(it)) return no("The SkyBlock Menu cannot be put into the storage!", false, false);
            return storage.match(name) ? no("Bags cannot be put into the storage!", false, true) : NOTHING;
        }
        if (t.equals("Salvage Item") || t.equals("Salvage Items")) {
            if (recombobulated(it)) return no("This item should not be salvaged! (Recombobulated)", true, true);
            for (String line : it.lore) if (line.contains("LEGENDARY DUNGEON")) return no("This item should not be salvaged! (Legendary)", true, true);
            if (donated(it)) return no("This item cannot be salvaged! (Donated to Museum)", true, true);
            if (menuItem(it)) return no("The SkyBlock Menu cannot be salvaged!", true, false);
            for (String item : SALVAGE) if (name.endsWith(item)) return TAKES;
            return no("This item cannot be salvaged!", true, true);
        }
        if (t.startsWith("You    ")) {
            if (anySoulbound(it)) return no("Soulbound items cannot be traded!", false, true);
            if (menuItem(it)) return no("The SkyBlock Menu cannot be traded!", false, false);
            if (sack(it)) return no("Sacks cannot be traded!", false, true);
            return trade.match(name) ? no("This item cannot be traded!", false, true) : NOTHING;
        }
        boolean bazaar = BazaarApi.inBazaarInventory, ah = auctionHouse(t);
        if (bazaar || ah) {
            if (menuItem(it)) return no(bazaar ? "The SkyBlock Menu is not a Bazaar Product!" : "The SkyBlock Menu cannot be auctioned!", true, false);
            if (bazaar != (Prices.get(it.id) != null)) return no(bazaar ? "This item is not a Bazaar Product!" : "Bazaar Products cannot be auctioned!", true, true);
            if (anySoulbound(it)) return no("Soulbound items cannot be auctioned!", true, true);
            if (sack(it)) return no("Sacks cannot be auctioned!", true, true);
            return auction.match(name) ? no("This item cannot be auctioned!", true, true) : TAKES;
        }
        if (t.startsWith("Accessory Bag")) {
            if (menuItem(it)) return NOTHING;
            return it.loreHas("ACCESSORY") || it.loreHas("HATCESSORY") ? TAKES : no("This item is not an accessory!", true, true);
        }
        if (t.startsWith("Basket of Seeds")) {
            if (menuItem(it)) return no("The SkyBlock Menu cannot be put into the basket of seeds!", false, false);
            return Patterns.get("SH.inventory.hidenotclickable.inventory.hidenotclickable.seeds").matcher(it.id).matches() ? NOTHING : no("This item is not a seed!", false, true);
        }
        if (t.startsWith("Nether Wart Pouch")) {
            if (menuItem(it)) return no("The SkyBlock Menu cannot be put into the nether wart pouch!", false, false);
            return it.id.equals("NETHER_STALK") ? NOTHING : no("This item is not a nether wart!", false, true);
        }
        if (t.startsWith("Trick or Treat Bag")) {
            if (menuItem(it)) return no("The SkyBlock Menu cannot be put into the trick or treat bag!", false, false);
            return name.equals("Green Candy") || name.equals("Purple Candy") || name.equals("Dark Candy") ? NOTHING : no("This item is not a spooky candy!", false, true);
        }
        if (t.startsWith("Sack of Sacks")) {
            if (menuItem(it)) return NOTHING;
            return sack(it) ? TAKES : no("This item is not a sack!", true, true);
        }
        if (t.startsWith("Potion Bag")) {
            if (menuItem(it)) return no("The SkyBlock Menu cannot be put into the potion bag!", false, false);
            return name.endsWith(" Potion") || name.equals("Water Bottle") ? TAKES : no("This item is not a potion!", true, true);
        }
        if ((t.equals("Chest") || t.equals("Large Chest")) && privateIsland && soulbound(it)) return no("This item cannot be stored into a chest!", false, true);
        if (t.startsWith("Attribute Fusion")) return !it.tag.getCompoundOrEmpty("attributes").isEmpty() ? TAKES : no("This item has no attributes!", true, true);
        if (Patterns.get("SH.data.equipment.equipment-and-stats-menu").matcher(t).matches()) {
            String category = ReforgeHelper.categoryAndRarity(it)[0];
            if (ARMOR_AND_EQUIPMENT.contains(category) && it.loreHas("§l")) return TAKES;
            if (menuItem(it)) return no("The SkyBlock Menu cannot be put into your equipment!", false, false);
            return no("This item cannot be put into your equipment!", false, true);
        }
        if (t.equals("Rift Transfer Chest")) return it.first(Patterns.get("SH.utils.item.rift.transferable")) != null || it.first(Patterns.get("SH.utils.item.rift.exportable")) != null ? TAKES : no("Not Rift-Transferable or Rift-Exportable!", true, true);
        if (t.equals("Fossil Excavator")) {
            if (it.id.isEmpty()) return no("Not a chisel or scrap!", true, true);
            return it.id.equals("SUSPICIOUS_SCRAP") || ReforgeHelper.categoryAndRarity(it)[0].equals("CHISEL") ? TAKES : no("Not a chisel or scrap!", true, true);
        }
        if (t.equals("Research Center")) return it.id.isEmpty() || it.id.equals("HELIX") || it.id.endsWith("_FOSSIL") ? TAKES : no("Not a fossil!", true, true);
        if (t.equals("Birdfeeder")) return it.id.isEmpty() || BIRD_FOOD.contains(it.id) ? TAKES : no("Not bird food!", true, true);
        return NOTHING;
    }

    private static boolean bypassed() {
        return MenuMarks.on("not_clickable", "not_clickable.bypass") && ReforgeHelper.modifierDown();
    }

    // skyhanni onForegroundDrawn in a slots marks. held bypass gets checked again next time the rule runs
    static void mark(MenuView v) {
        load();
        Feature f = Features.get("not_clickable");
        int dim = (f.option("not_clickable.transparency").integer() << 24) | 0x555555;
        boolean green = f.option("not_clickable.green_line").bool(), inRift = "THE_RIFT".equals(GameState.island), island = "PRIVATE_ISLAND".equals(GameState.island), npc = npcMenu(v);
        verdicts = new Verdict[v.itemsLength()];
        for (int slot = v.top; slot < v.itemsLength(); slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            Verdict d = judge(v, it, npc, inRift, island);
            verdicts[slot] = d;
            if (!d.reason().isEmpty()) {
                v.mark(slot).front = dim;
                List<String> tip = new ArrayList<>();
                tip.add("§7" + it.clean);
                tip.add("");
                tip.add("§c" + d.reason());
                if (d.bypass() && f.option("not_clickable.bypass").bool()) tip.add("  §7(Hold Ctrl to bypass)");
                v.mark(slot).tip = tip;
            } else if (d.green() && green) v.mark(slot).border = 0xC855FF55;
        }
    }

    static void register(BooleanSupplier skyblock) {
        Features.add(new Feature("not_clickable", "Menus", "Dim what a menu will not take", "In a menu that only takes some items, like the NPC shop or the Accessory Bag, what it will not take is dimmed in your inventory and says why.", "SkyHanni: Not Clickable Items")
                .when(skyblock).defaultOff()
                .tick(mc -> {
                    boolean now = bypassed();
                    if (now != wasBypassed) MenuMarks.invalidate();
                    wasBypassed = now;
                })
                .option(Option.bool("not_clickable.block_clicks", "Block clicks on them", "", true))
                .option(Option.bool("not_clickable.bypass", "Ctrl bypasses it", "", true))
                .option(Option.bool("not_clickable.green_line", "Green line on what it takes", "", true))
                .option(Option.bool("not_clickable.protect_rare", "Protect rarely sold items", "At an NPC, also what is rarely sold on purpose.", false))
                .option(Option.integer("not_clickable.transparency", "How dark", "", 180, 0, 255)));
        MenuMarks.add("not_clickable", t -> true, v -> {
            if (!bypassed()) mark(v);
            else verdicts = LOWER;
        });
        Interactions.onSlotClick((screen, slot, slotId, button) -> {
            if (!Features.on("not_clickable") || !MenuMarks.on("not_clickable", "not_clickable.block_clicks") || bypassed()) return false;
            if (slot == null || !(slot.container instanceof Inventory)) return false;
            return slotId >= 0 && slotId < verdicts.length && verdicts[slotId] != null && !verdicts[slotId].reason().isEmpty();
        });
    }
}
