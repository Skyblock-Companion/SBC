// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.feature;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.PickupLog;
import dev.zmuzy.sbc.item.Tips;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.nav.Waypoints;
import dev.zmuzy.sbc.track.Trackers;
import dev.zmuzy.sbc.ui.Widgets;

import java.util.function.BooleanSupplier;

// the features we ship. new ones go here, one line each, and the settings page lists them with no extra code
public final class Builtin {
    private static final BooleanSupplier SKYBLOCK = () -> GameState.inSkyBlock || SbcMod.DEV;
    private Builtin() {}

    public static void register() {
        // ---- hud ----
        Features.add(new Feature("playercard", "HUD", "Player card", "Your head, name, SkyBlock level and active pet at the top left.", "Skyblocker: HUD · SkyHanni: pet display")
                .bind(() -> Config.I.playerCard, v -> Config.I.playerCard = v).when(SKYBLOCK)
                .option(Option.bool("playercard.fps", "FPS beside the name", "", true)));
        Features.add(new Feature("cardstats", "HUD", "Level, networth, purse and bank", "Four numbers under your name on the card.", "SkyHanni: custom scoreboard")
                .bind(() -> Config.I.infoPanel, v -> Config.I.infoPanel = v).when(SKYBLOCK));
        Features.add(new Feature("routine", "HUD", "Routine list", "Today's open tasks, the next event and what you earn.", "")
                .bind(() -> Config.I.hudEnabled, v -> Config.I.hudEnabled = v).when(SKYBLOCK));
        Features.add(new Feature("minimap", "HUD", "Minimap", "Round map of your island under the top-right corner, on islands with a prepared map image.", "Skyblocker: minimap")
                .bind(() -> Config.I.minimap, v -> Config.I.minimap = v).when(SKYBLOCK));


        // ---- navigation ----
        Features.add(new Feature("pathline", "Navigation", "Path line", "A line and a beam that lead you to wherever you are going.", "SkyHanni: navigation")
                .bind(() -> Config.I.pathLine, v -> Config.I.pathLine = v)
                .world(w -> Navigator.emitGizmos(w.mc)));
        Features.add(new Feature("waypoints", "Navigation", "Waypoints", "Your saved waypoints and the ones other features show, drawn in the world. /sbc wp opens your groups.", "Skyblocker: waypoints · SkyHanni: waypoints")
                .when(SKYBLOCK).world(Waypoints::draw).tick(Waypoints::tick).left(Waypoints::resetOrder)
                .option(Option.choice("waypoints.look", "Look", "", "Box and beam", "Box and beam", "Box", "Outline"))
                .option(Option.bool("waypoints.line", "Line to the next one", "In a group that goes in order.", true))
                .option(Option.color("waypoints.line_color", "Colour of that line", "", 0xFF00FF00))
                .option(Option.number("waypoints.line_width", "Width of that line", "", 5, 1, 10, 0.5))
                .option(Option.number("waypoints.radius", "How close counts as reached", "In a group that goes in order.", 2, 1, 10, 0.5))
                .option(Option.bool("waypoints.skip", "May skip waypoints", "In a group that goes in order.", true))
                .option(Option.bool("waypoints.backwards", "May go backwards", "In a group that goes in order.", true)));

        Features.add(new Feature("navtitle", "Navigation", "Arrival title", "A short title in the middle of the screen when you reach where /sbc nav was taking you.", "SkyHanni: navigation")
                .when(SKYBLOCK));

        // ---- menus ----

        // ---- items ----
        Features.add(new Feature("tip_prices", "Items", "Prices on item tooltips", "Bazaar, lowest BIN, estimated value, NPC price and craft cost under the item's text.", "Skyblocker: tooltips · SkyHanni: price display")
                .when(SKYBLOCK).tooltip(Tips::prices)
                .option(Option.bool("tip_prices.bazaar", "Bazaar price", "", true))
                .option(Option.bool("tip_prices.lowestBin", "Lowest BIN", "", true))
                .option(Option.bool("tip_prices.estimated", "Estimated value", "", true))
                .option(Option.bool("tip_prices.npc", "NPC price", "", true))
                .option(Option.bool("tip_prices.george", "George's price for pets", "What George pays for a pet with no experience.", true))
                .option(Option.choice("tip_prices.craftCost", "Craft cost", "Off, or priced by what you pay for materials instantly or by buy order.", "SELL_OFFER", "OFF", "SELL_OFFER", "BUY_ORDER")));
        Features.add(new Feature("tip_details", "Items", "Item details on tooltips", "Obtained date, dungeon quality, kill counters and the cost of the next star.", "Skyblocker: tooltips")
                .when(SKYBLOCK).tooltip(Tips::details)
                .option(Option.bool("tip_details.obtained", "Obtained date", "", true))
                .option(Option.bool("tip_details.quality", "Dungeon quality", "Quality and the floor it dropped on.", true))
                .option(Option.bool("tip_details.nextStar", "Next star's cost", "", true)));
        Features.add(new Feature("tip_breakdown", "Items", "Value breakdown", "Hold Shift on a modified item to see what its estimated value is made of.", "SkyHanni: estimated item value")
                .when(SKYBLOCK).tooltip(dev.zmuzy.sbc.item.Extras::breakdown));
        Features.add(new Feature("tip_accessory", "Items", "Accessory status", "Tells you on an accessory whether it is new to your bag, an upgrade, or already outdone.", "Skyblocker: accessory info")
                .when(SKYBLOCK).tooltip(dev.zmuzy.sbc.item.Extras::accessory));
        Features.add(new Feature("tip_museum", "Items", "Museum status", "Tells you on an item whether the museum takes it, in which category, and whether you already donated one.", "Skyblocker: museum item info")
                .when(SKYBLOCK).tooltip(dev.zmuzy.sbc.item.Extras::museum));
        Features.add(new Feature("slotinfo", "Items", "Slot overlays", "Rarity glow behind items, pet and book levels, minion tiers and stars on the slot.", "Skyblocker: item rarity backgrounds · SkyHanni: item numbers")
                .when(SKYBLOCK));
        Features.add(new Feature("slotlocks", "Items", "Slot locking and item protection", "L over a slot locks it, V protects the item in it: no dragging, shift-clicking, swapping or dropping by mistake.", "Skyblocker: slot locking · SkyHanni: item protection")
                .when(SKYBLOCK)
                .option(Option.bool("slotlocks.notify", "Say when something is locked", "A note each time you lock, unlock, protect or unprotect.", true))
                .option(Option.choice("slotlocks.lockStyle", "Lock look", "Classic dims the slot under the padlock, Fancy keeps the item bright.", "CLASSIC", "CLASSIC", "FANCY")));
        Features.add(new Feature("containervalue", "Items", "Chest value", "What the things in an open chest or container would sell for.", "SkyHanni: chest value")
                .when(SKYBLOCK)
                .option(Option.bool("containervalue.inOwnInventory", "In your own inventory", "Also what your inventory is worth.", false))
                .option(Option.bool("containervalue.inDungeons", "In dungeons", "", false))
                .option(Option.bool("containervalue.highlight", "Light up its slots", "Hovering a kind on the card lights up where it is in the chest.", true))
                .option(Option.bool("containervalue.showStacks", "Item icons", "", true))
                .option(Option.bool("containervalue.ignoreSoulbound", "Leave soulbound out", "Soulbound items do not count towards the total.", false))
                .option(Option.choice("containervalue.sorting", "Sorted", "", "DESCENDING", "DESCENDING", "ASCENDING"))
                .option(Option.choice("containervalue.format", "Value format", "Short is 1.2M, long is every digit.", "SHORT", "SHORT", "LONG"))
                .option(Option.integer("containervalue.itemToShow", "Kinds shown", "All of them still count for the total.", 6, 1, 15))
                .option(Option.integer("containervalue.hideBelow", "Hide kinds below (coins)", "They still count for the total.", 0, 0, 100_000_000))
                .option(Option.integer("containervalue.width", "Card width", "Narrower gives item names less room.", 208, 132, 280))
                .option(Option.color("containervalue.color", "Total colour", "", 0xFFFFD166))
                .option(Option.color("containervalue.incompleteColor", "Total colour when something has no price", "", 0xFF5CD8FF)));
        Features.add(new Feature("itemlist", "Items", "Item list and recipes", "Panel beside every inventory with all SkyBlock items: search, filters, stars, recipe and used-in cards (R / U over an item).", "SkyBlock Item List")
                .when(SKYBLOCK));
        Features.add(new Feature("pickups", "Items", "Item pickup log", "What you gained and lost in the last seconds with the coin value, in the bottom right corner: inventory changes, sack changes and coins.", "")
                .when(SKYBLOCK)
                .option(Option.bool("pickups.sack", "Sack changes", "From the [Sacks] message's details.", true))
                .option(Option.bool("pickups.coins", "Coins", "Purse changes as a Coins line.", true))
                .option(Option.bool("pickups.shards", "Shards", "Shards sent to your Hunting Box.", false))
                .option(Option.integer("pickups.expire", "Seconds a line stays", "", 10, 3, 60))
                .option(Option.bool("pickups.compactLines", "One line per item", "What came and went of the same item shows as one amount.", true))
                .option(Option.bool("pickups.shorten", "Compact numbers", "1.2k instead of 1,200.", false))
                .option(Option.bool("pickups.icons", "Item icons", "", true))
                .option(Option.choice("pickups.layout", "Line order", "", "AMOUNT_NAME", "AMOUNT_NAME", "NAME_AMOUNT"))
                .option(Option.bool("pickups.coinValue", "Total coin value", "The value of everything listed, in the card's band.", true))
                .option(Option.integer("pickups.coinThreshold", "Total only above (coins)", "", 0, 0, 100_000_000))
                .option(Option.choice("pickups.priceSource", "Price source", "", dev.zmuzy.sbc.item.market.PriceSource.INSTANT_SELL, dev.zmuzy.sbc.item.market.PriceSource.CHOICES)));

        // ---- chat ----
        Features.add(new Feature("npcdialogue", "Chat", "NPC dialogue box", "NPC talk as an on-screen box with numbered answers instead of chat lines.", "")
                .bind(() -> Config.I.npcDialogue, v -> Config.I.npcDialogue = v));

        // ---- tracking ----
        // off by default, each activitys own tracker counts the same stuff better
        Features.add(new Feature("loot", "Tracking", "Items gained", "Counts the items you pick up, with their coin value, per session, day and week.", "SkyHanni: item trackers")
                .when(SKYBLOCK).defaultOff());
        // feeds every tracker + the pickup log by diffing inventory and sacks. always on whatever cards are off
        Features.add(new Feature("tracker_engine", "Tracking", "Tracker engine", "Reads what you gain from your inventory and sacks for every tracker.", "").alwaysOn()
                .when(SKYBLOCK).tick(mc -> {
                    var ex = Features.get("pickups") == null ? null : Features.get("pickups").option("expire");
                    if (ex != null) PickupLog.expireSeconds = ex.integer();
                    Trackers.tick(mc);
                }));

        // the chat chain: observers first (always on), then npc dialogue. filters, compactors + rules plug in here
        ChatBus.register(ChatBus.OBSERVE, "observe", null, line -> {
            SbcMod.onChatLine(line.component());
            return ChatBus.PASS;
        });
        ChatBus.register(ChatBus.DIALOGUE, "dialogue", Features.get("npcdialogue"),
                line -> SbcMod.consumeChat(line.component()) ? ChatBus.CANCEL : ChatBus.PASS);

        Trackers.onGain((id, n) -> {
            if (Features.on("pickups")) PickupLog.add(id, n);
        });
        ChatBus.register(ChatBus.OBSERVE + 2, "sacks", Features.get("tracker_engine"), Trackers::onChat);
        dev.zmuzy.sbc.chat.ChatFilters.register();
        dev.zmuzy.sbc.chat.Alerts.register();
        dev.zmuzy.sbc.nav.Collectibles.register();
        dev.zmuzy.sbc.track.Profit.register();
        dev.zmuzy.sbc.chat.ProfileChat.register();
        dev.zmuzy.sbc.ui.PartyFinderScreen.register();
        dev.zmuzy.sbc.item.ItemNumbers.register();
        dev.zmuzy.sbc.garden.GrowthStage.register();
        dev.zmuzy.sbc.garden.FarmingCard.register();
        Safe.endTick("Playtime.tick", dev.zmuzy.sbc.track.Playtime::tick);
        Safe.endTick("PetMenu.tick", dev.zmuzy.sbc.ui.PetMenu::tick);
        Safe.endTick("SkillElement.rateTick", dev.zmuzy.sbc.ui.hud.SkillElement::rateTick);
        dev.zmuzy.sbc.dungeon.Dungeon.register();
        dev.zmuzy.sbc.dungeon.Puzzles.register();
        dev.zmuzy.sbc.dungeon.RoomPuzzles.register();
        dev.zmuzy.sbc.dungeon.IcePuzzles.register();
        dev.zmuzy.sbc.dungeon.Waterboard.register();
        dev.zmuzy.sbc.dungeon.Boulder.register();
        dev.zmuzy.sbc.dungeon.TeleportMaze.register();
        dev.zmuzy.sbc.dungeon.Devices.register();
        dev.zmuzy.sbc.item.ArmorDye.register();
        dev.zmuzy.sbc.item.custom.CustomLooks.register();
        dev.zmuzy.sbc.nav.Fishing.register();
        dev.zmuzy.sbc.nav.Kuudra.register();
        dev.zmuzy.sbc.dungeon.Score.register();
        dev.zmuzy.sbc.nav.Slayers.register();
        dev.zmuzy.sbc.chat.Peek.register();
        Features.add(new Feature("tip_backpack", "Items", "Backpack contents on the tooltip", "What a backpack holds, with the total value, without opening it.", "")
                .when(SKYBLOCK).tooltip(dev.zmuzy.sbc.item.Previews::backpack));
        Features.add(new Feature("tip_compactor", "Items", "Compactor and deletor contents", "What a personal compactor compacts and a personal deletor deletes, on their tooltips.", "")
                .when(SKYBLOCK).tooltip(dev.zmuzy.sbc.item.Previews::compactor));
        Features.add(new Feature("market_overlay", "Menus", "Prices inside the Auction House and Bazaar", "Under each auction: the asking price against the market as a percentage (green = below lowest BIN). Under Bazaar items: the instant buy price.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("sbdate", "HUD", "SkyBlock date and time", "The in-game date and time as a small line.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("mayor", "HUD", "Mayor and perks", "The current mayor with the active perks.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("chat_hiderank", "Chat", "Hide ranks in chat", "[MVP+] and other rank tags are removed from player messages.", "")
                .when(SKYBLOCK).defaultOff());
        Features.add(new Feature("tip_enchants", "Items", "Coloured enchantments", "Enchantment lists on tooltips: ultimates in pink, max level in gold, one below max in aqua.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("quicknav", "Menus", "Quick navigation", "Buttons above and below every inventory and chest: SkyBlock Menu, pets, loadouts, equipment, storage, Accessory Bag, Sack of Sacks, bazaar, auction house, Warp Menu, crafting, anvil, enchanting, the Hex. Each one can be edited below: icon, command, tooltip, on or off, double click.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("accessory_helper", "Menus", "Accessory helper", "Beside your Accessory Bag, on the other side from the Item List: what to get next for the most Magical Power per coin, and how far your purse gets you. The bag itself stays as it is.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("sack_helper", "Menus", "Sack helper", "Beside your Sack of Sacks, on the other side from the Item List: which sacks you are missing, the bigger size of the ones you have, and what finishing them costs. The menu itself stays as it is.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("pet_helper", "Pets", "Pet helper", "Beside Hypixel's Pets menu, on the other side from the Item List: your Pet Score, the cheapest way to the next Magic Find, which pets to level, and what you are missing. The menu itself stays as it is.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("inv_search", "Menus", "Inventory search (Ctrl+F)", "Ctrl+F in any chest or inventory opens a search; items that do not match are dimmed, matches get a green edge.", "")
                .when(() -> true)
                .option(Option.bool("inv_search.reset", "Empty the search when the menu closes", "", true)));
        Features.add(new Feature("wardrobe_keys", "Menus", "Wardrobe number keys", "In the Wardrobe and Loadouts menus, keys 1-9 press that slot's button.", "")
                .when(SKYBLOCK));
        Features.add(new Feature("customtext", "HUD", "Custom text on the HUD", "Your own lines on the HUD. Separate lines with |, use & for colours.", "")
                .when(SKYBLOCK)
                .option(Option.text("customtext.text", "Text", "Lines separated by |; &6 etc. for colours.", ""))
                .option(Option.bool("customtext.only_menus", "Only while a menu is open", "", false)));
        Features.add(new Feature("warp_menu", "Navigation", "Warp menu", "The Fast Travel menu as a map of island pictures with a portal on every warp: click a portal, or an island for its first warp. Tab shows Hypixel's own menu.", "")
                .when(SKYBLOCK)
                .option(Option.bool("showIslandLabels", "Island names", "", true))
                .option(Option.bool("hideWarpLabelsUntilIslandHovered", "Warp names only on the hovered island", "", false))
                .option(Option.bool("hideWarpLabelForIslandsWithOneWarp", "No warp name on islands with one warp", "The island's own name says it.", true))
                .option(Option.bool("showJerryIsland", "Jerry's Workshop", "In Late Winter and in December.", true))
                .option(Option.bool("hideUnobtainableWarps", "Hide warps you cannot get", "The ones marked as Bingo rewards.", true))
                .option(Option.bool("showRegularWarpMenuButton", "Button for Hypixel's menu", "", false)));
        Features.add(new Feature("fancy_tooltip", "Items", "Item cards", "SkyBlock items get a tidy card: the item, its name and rarity at the top, stats lined up, abilities marked, gemstone slots as tiles, progress and fuel as bars. Every line Hypixel wrote stays.", "")
                .when(SKYBLOCK)
                .option(Option.bool("fancy_tooltip.rarityFrame", "Edge in the item's rarity colour", "Off: the mod's plain dark panel.", true))
                .option(Option.bool("fancy_tooltip.hideVanilla", "Hide what the base game adds under SkyBlock items", "Enchants, attributes, trims and the like.", true))
                .option(Option.bool("fancy_tooltip.icon", "Item icon in the head", "", true))
                .option(Option.bool("fancy_tooltip.alignedStats", "Stats lined up", "", true))
                .option(Option.bool("fancy_tooltip.bars", "Progress and fuel as bars", "", true))
                .option(Option.bool("fancy_tooltip.gemstones", "Gemstone slots as tiles", "", true)));
        Features.add(new Feature("tooltip_scroll", "Items", "Scrollable tooltips", "When a tooltip is taller than the screen, the mouse wheel scrolls it (Shift + wheel: sideways).", "")
                .when(() -> true));
        Features.add(new Feature("bars", "HUD", "Status bars", "Health, mana and defense as slim bars with their numbers above the hotbar, instead of the vanilla hearts and hunger. Pick which bars and whether the numbers show.", "")
                .when(SKYBLOCK)
                .option(Option.bool("bars.health", "Health bar", "", true))
                .option(Option.bool("bars.mana", "Mana bar", "", true))
                .option(Option.bool("bars.defense", "Defense bar", "", true))
                .option(Option.bool("bars.numbers", "Numbers above the bars", "", true))
                .option(Option.bool("bars.percent", "Percent next to the numbers", "", false))
                .option(Option.choice("bars.style", "Bar style", "", "Glass", "Glass", "Flat", "Neon", "Blocks", "Thin"))
                .option(Option.bool("bars.hideActionBar", "Remove health, defense and mana from Hypixel's action bar", "The bars show them; the rest of the action bar (abilities, healing, mana use) stays.", true))
                .option(Option.bool("bars.rift", "Bars in the Rift", "Off: Minecraft's hearts in the Rift.", true))
                .option(Option.bool("bars.rift_hp", "Rift health as points", "20, 19, 18 instead of hearts: 10, 9.5, 9.", false))
                .option(Option.bool("bars.hideVanilla", "Hide the vanilla hearts, hunger, armor and air", "Off: Minecraft's hearts stay as they are.", true)));
        Features.add(new Feature("market_search", "Menus", "Market search", "In the Auction House and Bazaar the Search button (or Y) opens a search that lists matches as you type - write hyp and Hyperion shows up with its prices. Y also works anywhere in SkyBlock.", "Skyblocker: search overlay")
                .when(SKYBLOCK));
        dev.zmuzy.sbc.item.StorageCache.init();
        dev.zmuzy.sbc.ui.storage.StorageOverlayScreen.setup();
        Features.add(new Feature("storage_overlay", "Menus", "Storage overlay", "Hypixel's Storage, Ender Chest and Backpack menus become one screen: every page with search and your inventory below it. The open page works as usual, clicking another one opens it. The Storage key (B) is the same as /storage.", "Skyblocker: storage overlay")
                .when(SKYBLOCK).tick(mc -> {
                    dev.zmuzy.sbc.item.StorageCache.tick(mc);
                    dev.zmuzy.sbc.ui.storage.StorageOverlayScreen.tick(mc);
                })
                .option(Option.integer("storagesPerRow", "Pages per row", "At most this many pages side by side. 0: as many as fit.", 3, 0, 9))
                .option(Option.integer("backpackWidth", "Slots per row", "How many slots each page shows per row, fewer if they would not fit.", 9, 1, 9))
                .option(Option.bool("rememberSearch", "Remember search", "Keep the search and scroll position after closing.", false))
                .option(Option.bool("rememberOpened", "Remember open page", "Reopen the last page when running /storage.", false))
                .option(Option.bool("doNotResetCursor", "Keep the cursor between pages", "Going to another page closes and reopens the menu; the cursor stays where it was.", true)));
        Features.add(new Feature("radial_menu", "Menus", "Radial menus", "Press the Radial Menu key (U) in any Hypixel menu to see its entries as a wheel.", "Skyblocker: radial menus")
                .when(SKYBLOCK).defaultOff());
        dev.zmuzy.sbc.chat.Party.register();
        dev.zmuzy.sbc.commands.CommandPack.register();
        Features.add(new Feature("gfs_key", "Items", "Get from sack key", "Press the Get From Sack key over an item in an inventory to run /gfs for it (Shift = a full stack, plain = enough to top up).", "SkyHanni: get from sack keybind")
                .when(SKYBLOCK));
        dev.zmuzy.sbc.dungeon.DungeonExtras.register();
        dev.zmuzy.sbc.dungeon.DungeonPack.register();
        Features.get("slotlocks").option(Option.bool("slotlocks.dungeon_abilities", "Allow the drop key in a dungeon", "The drop key still works on a locked slot during a run, for class abilities.", false));
        dev.zmuzy.sbc.mining.MiningExtras.register();
        dev.zmuzy.sbc.garden.GardenExtras.register();
        dev.zmuzy.sbc.garden.GardenHelpers.register();
        dev.zmuzy.sbc.areas.FishForageHunt.register();
        dev.zmuzy.sbc.areas.AreaPack.register();
        dev.zmuzy.sbc.minion.MinionFeatures.register();
        dev.zmuzy.sbc.skills.SkillCards.register();
        dev.zmuzy.sbc.pets.PetExtras.register();
        dev.zmuzy.sbc.core.Bits.register();
        dev.zmuzy.sbc.areas.CommunityUpgrades.register();
        dev.zmuzy.sbc.areas.StrandedExtras.register();
        dev.zmuzy.sbc.misc.MiscPack.register();
        dev.zmuzy.sbc.item.menus.HiddenCoopMembers.register();
        dev.zmuzy.sbc.areas.SlayerCombatCrimson.register();
        dev.zmuzy.sbc.combat.Combat.register();
        dev.zmuzy.sbc.events.Events.register();
        dev.zmuzy.sbc.events.EventPack.register();
        dev.zmuzy.sbc.areas.RiftExtras.register();
        dev.zmuzy.sbc.areas.MiscExtras.register();
        dev.zmuzy.sbc.world.WorldPack.register();
        dev.zmuzy.sbc.item.AbilityCooldowns.register();
        dev.zmuzy.sbc.events.diana.Diana.register();
        dev.zmuzy.sbc.events.hoppity.Hoppity.register();
        dev.zmuzy.sbc.events.EventExtras.register();
        dev.zmuzy.sbc.mining.Mining.register();
        dev.zmuzy.sbc.rift.Rift.register();
        dev.zmuzy.sbc.crimson.Crimson.register();
        dev.zmuzy.sbc.item.enchants.EnchantTooltip.register();
        dev.zmuzy.sbc.item.market.Market.register();
        dev.zmuzy.sbc.item.guard.ConsumableProtection.register();
        dev.zmuzy.sbc.item.calc.SignCalculator.register();
        dev.zmuzy.sbc.item.market.SuperCraft.register();
        dev.zmuzy.sbc.item.market.MoreMarket.register();
        dev.zmuzy.sbc.item.market.NpcTrade.register();
        dev.zmuzy.sbc.item.MagicalPowerDisplay.register();
        dev.zmuzy.sbc.item.Extras.registerOptions();
        dev.zmuzy.sbc.ui.MarketSearchScreen.registerCommands();
        dev.zmuzy.sbc.track.Profit.registerSettings();
        dev.zmuzy.sbc.item.tooltip.TipExtras.register();
        dev.zmuzy.sbc.item.sacks.Sacks.register();
        dev.zmuzy.sbc.item.sacks.SackMessagePrice.register();
        dev.zmuzy.sbc.item.sacks.GfsExtras.register();
        dev.zmuzy.sbc.chat.sh.ChatCompact.register();
        dev.zmuzy.sbc.garden.milestones.GardenMilestones.register();
        dev.zmuzy.sbc.garden.GardenPack.register();
        dev.zmuzy.sbc.mining.MiningPack.register();
        dev.zmuzy.sbc.nav.FairySouls.register();
        dev.zmuzy.sbc.core.game.GameProfile.register();
        dev.zmuzy.sbc.ui.panels.Panels.register();
        dev.zmuzy.sbc.item.menus.Menus.register();
        Widgets.register();
        dev.zmuzy.sbc.core.Backups.make();
        dev.zmuzy.sbc.core.data.GameData.register();
        Options.register();
    }
}
