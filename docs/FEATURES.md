# SkyBlock Companion - every feature

Every feature, as the settings menu lists them. Press **K** in game and open Settings to switch any of them.

[HUD](#hud) (23) · [Menus](#menus) (51) · [Items](#items) (54) · [Chat](#chat) (80) · [Navigation](#navigation) (9) · [World](#world) (9) · [Commands](#commands) (12) · [Garden](#garden) (78) · [Farming Islands](#farming-islands) (9) · [Mining](#mining) (52) · [Foraging](#foraging) (20) · [Fishing](#fishing) (33) · [Hunting](#hunting) (15) · [Combat](#combat) (34) · [Slayer](#slayer) (42) · [Dungeons](#dungeons) (64) · [Crimson Isle](#crimson-isle) (18) · [Rift](#rift) (32) · [Events](#events) (79) · [Pets](#pets) (8) · [Minions](#minions) (8) · [Skills](#skills) (8) · [Tracking](#tracking) (4) · [Bar parts](#bar-parts) (13) · [Misc](#misc) (26)


## HUD

Cards on your screen. You can edit everything in the HUD editor: press **H** in game.

+ **Player card**: Your head, name, SkyBlock level and active pet at the top left.
+ **Level, networth, purse and bank**: Four numbers under your name on the card.
+ **Routine list**: Today's open tasks, the next event and what you earn.
+ **Minimap**: Round map of your island under the top-right corner, on islands with a prepared map image.
+ **SkyBlock date and time**: The in-game date and time as a small line.
+ **Mayor and perks**: The current mayor with the active perks.
+ **Custom text on the HUD**: Your own lines on the HUD. Separate lines with |, use &amp; for colours.
+ **Status bars**: Health, mana and defense as slim bars with their numbers above the hotbar, instead of the vanilla hearts and hunger. Pick which bars and whether the numbers show.
+ **Month names in colour**: The date on the scoreboard in the colour of its season.
+ **Movement speed**: How fast you move, in blocks per second.
+ **Clock**: Your computer's time, handy in full screen.
+ **Server ticks per second**: How fast the server is running, up to 20.
+ **Beacon power**: How long your beacon runs and what it boosts. Opening the Beacon menu reads it.
+ **Legion and Bobbin' Time**: How many players and bobbers are near, and what your armor makes of them.
+ **SkyBlock level on the XP bar**: The game's XP bar shows your SkyBlock level, except in the Catacombs and the Rift. Switch off hiding the XP bar under Skill progress to see it.
+ **Move the game's hotbar and text**: The hotbar, the XP bar, the action bar text and the held item's name can be moved and sized in the HUD editor.
+ **/tab widgets on the HUD**: Keeps the widgets you tick on the screen, each its own card to place. Hypixel only sends what you switched on in its /tab menu.
+ **Custom scoreboard**: Your own sidebar: pick the lines and their order in the settings (title, date, island, purse, bank, bits, powders, objective, slayer, party, mayor, events...). Lines the patterns do not know stay at the end.
+ **Skill progress**: The skill you are levelling as a bar above the status bars: level, percent, XP into the level out of the XP needed, last gain and XP per hour. Replaces Hypixel's XP bar.
+ **Stats beside the hotbar**: Vitality and Speed left of the hotbar, Attack Speed and Ferocity right of it. Hypixel only sends the stats you pick for its Stats tab widget; the others show what the SkyBlock Menu said the last time you opened it.
+ **Equipped pet**: Your pet right of the hotbar: head, name in its rarity's colour, level and progress to the next level. Reads Hypixel's Pet tab widget.
+ **Custom tab list**: Hold TAB for the player list as boxes per widget. Choose the widgets, the columns, adverts and player sorting here; which widgets Hypixel sends is its own /tab setting.
+ **Appearance and layout**: Theme, size and position of the whole HUD. The HUD editor (H) moves and styles each element.

## Menus

Panels beside Hypixel's own menus. Hypixel's menus stay exactly as they are.

+ **Prices inside the Auction House and Bazaar**: Under each auction: the asking price against the market as a percentage (green = below lowest BIN). Under Bazaar items: the instant buy price.
+ **Quick navigation**: Buttons above and below every inventory and chest: SkyBlock Menu, pets, loadouts, equipment, storage, Accessory Bag, Sack of Sacks, bazaar, auction house, Warp Menu, crafting, anvil, enchanting, the Hex. Each one can be edited below: icon, command, tooltip, on or off, double click.
+ **Accessory helper**: Beside your Accessory Bag, on the other side from the Item List: what to get next for the most Magical Power per coin, and how far your purse gets you. The bag itself stays as it is.
+ **Sack helper**: Beside your Sack of Sacks, on the other side from the Item List: which sacks you are missing, the bigger size of the ones you have, and what finishing them costs. The menu itself stays as it is. - Beta 0.2 ([43a8da3](https://github.com/Skyblock-Companion/SBC/commit/43a8da3))
+ **Inventory search (Ctrl+F)**: Ctrl+F in any chest or inventory opens a search; items that do not match are dimmed, matches get a green edge.
+ **Wardrobe number keys**: In the Wardrobe and Loadouts menus, keys 1-9 press that slot's button.
+ **Market search**: In the Auction House and Bazaar the Search button (or Y) opens a search that lists matches as you type - write hyp and Hyperion shows up with its prices. Y also works anywhere in SkyBlock.
+ **Storage overlay**: Hypixel's Storage, Ender Chest and Backpack menus become one screen: every page with search and your inventory below it. The open page works as usual, clicking another one opens it. The Storage key (B) is the same as /storage.
+ **Radial menus**: Press the Radial Menu key (U) in any Hypixel menu to see its entries as a wheel.
+ **Hide former co-op members**: Takes those who left your co-op out of the collection tooltips. Open Historic Members once, or use /sbc coopmembers.
+ **Teleport pads**: On your Private Island: pad numbers in the Set Destination menu, and shorter names over the pads.
+ **Names in the Brewing Stand**: The ingredient's, the progress' and the potion's names, written by their slots.
+ **Bazaar helpers**: Highlights the item you searched for in the results, marks your orders and where they stand, turns max into the biggest order in the amount sign, and beside the menu shows the daily limit, what your purse buys and what a recipe needs.
+ **Auction House helpers**: In Manage Auctions marks sold (green), expired (red) and underbid (gold) auctions; a title when you are outbid; copy the underbid price of the hovered auction with /sbc copyunderbid (or of your item in Create BIN Auction).
+ **Super craft helpers**: Puts your amounts as buttons in the amount sign and reminds you that Shift-Click maximizes. If you switch it on, stops a super craft that wastes coins against buying the result and selling the materials (hold Ctrl to go through).
+ **Craftable item list**: Beside the Craft Item menu: what you could craft from your inventory, how many times and what it costs. A click opens its recipe.
+ **Trade value**: Beside a trade with another player: what your side and theirs are worth, with what makes up each.
+ **NPC trade helper**: In an NPC shop each cost line says what you have of it and what it is worth, and the head what the whole trade costs. What you can afford can be marked green.
+ **Bits helper**: In the Community Shop: coins per bit on the tooltip, the best of what sells well in green and the best overall in yellow.
+ **Sack display**: Next to an open sack: every item with stored/total and its Bazaar sell value, gemstones by quality, runes by level, Magmafish for trophy sacks, and the total; full sacks are marked red. The amounts are remembered per profile and kept current from the [Sacks] chat lines.
+ **Equipment in the inventory**: Necklace, cloak, belt and gloves beside the player in your inventory, as your menus last showed them: open /equipment or a loadout and they catch up. Click a piece to open the menu. The offhand slot and the recipe book button move right to make room.
    + A loadout switch shows its equipment at once. - Beta 0.2 ([4a38056](https://github.com/Skyblock-Companion/SBC/commit/4a38056))
+ **Museum helper**: Beside the Museum menu: everything you have not donated yet with its price, by category, sorted by lowest BIN, craft cost or coins per SkyBlock XP, with a search. It learns from the Museum's pages you open and the donations you make.
+ **Complete Museum categories**: In the Museum's menu, a category with every item donated turns green.
+ **Loadout highlighting**: In the Loadouts menu, the loadout you are wearing stands out, and your favourites if you want them to.
+ **Estimated value of a set**: On the button of a set in the Armor Sets and Equipment Sets menus: what each piece is worth, and the total.
+ **Crafting table for Craft Item**: The Craft Item menu looks like a plain crafting table with your inventory under it, the three quick crafts at the side. It replaces Hypixel's chest, so it starts off.
+ **Custom Wardrobe**: All your wardrobe sets on one screen, each a player wearing it, in place of Hypixel's Armor Sets menu. Click one to wear it, or to go to its page. Favourites and hiding of empty or locked slots. It replaces the menu, so it starts off.
+ **Anvil helper**: In an Anvil: what can combine with the item you put in is green, and the middle turns red if two books have enchantments of different levels.
+ **Reforge helper**: Beside the Reforge menus: every reforge this item can take with what it gives. Choose one and the button stops when it is reached.
+ **SkyBlock Guide highlights**: In the SkyBlock Guide and the menus around it, what you have not done yet is red. Choose which menus below.
+ **RNG Meter menu**: In the RNG Meter menus: the floor on each meter, floors with no drop chosen, and the chosen drop.
+ **Stats Tuning menu**: Maxwell's tuning: points on each stat, what a template loads, the chosen template, and your tuning on the Thaumaturgy button.
+ **Small menu highlights**: The active stat boost in the Beacon menu, and which widgets are on or off in the /tab settings.
+ **Hub selector capacity**: How full each lobby is, as a colour on the hub selector.
+ **Attribute shards**: The tier of each shard in the Attribute Menu, disabled attributes in red, what the next tier of each shard costs, and what your Hunting Box is worth.
+ **Essence Shop helper**: Beside an Essence Shop: what is left to buy, how much essence that takes, and what it costs on the Bazaar.
+ **Dim what a menu will not take**: In a menu that only takes some items, like the NPC shop or the Accessory Bag, what it will not take is dimmed in your inventory and says why.
+ **New Year Cake tracker**: Which New Year Cakes you have and which you lack, beside your cake bags and the Auction House, with what they cost.
+ **Calendar extras**: The Minister and their perk in the main calendar, and which pet Oringo brings to a Traveling Zoo.
+ **Tia's relays**: For Tia's Abiphone Network: which relay is next as a waypoint, and the order of the four sounds in the puzzle.
+ **Melody's Harp**: Melody's Harp, with your own keys instead of clicks.
+ **Fann's training cost**: In a Fann training slot, what each XP costs in coins and what each Bit gives.
+ **Pages with the wheel**: The mouse wheel turns the page in Hypixel's menus; over an item it needs the key.
+ **Clicks count as Shift clicks**: In the menus you choose, a plain click moves the item as a Shift click would.
+ **Quick craft guard**: Keeps you from quick crafting something by accident: dim, and Ctrl and click does it.
+ **Favourite power stones**: In Thaumaturgy, Shift and click marks a power as a favourite, in light blue.
+ **Old SkyBlock Menu shortcuts**: The empty corners of the SkyBlock Menu get the old shortcuts: Trades, Accessory Bag, Potion Bag, Quiver, Fishing Bag, Sack of Sacks.
+ **Snake with keys**: Your movement keys steer the Abiphone's Snake.
+ **No tooltips on empty items**: The empty panes in Hypixel's menus show no tooltip and do not take clicks.
+ **Middle click in menus**: Middle click works on Hypixel's menus as it does in creative mode.
+ **Improved SkyBlock menus**: Changes how SkyBlock's chests look: the filler panes go, and slots and buttons get a darker or lighter background. Every slot stays where it is.

## Items

Tooltips, prices and everything about the items you hold.

+ **Prices on item tooltips**: Bazaar, lowest BIN, estimated value, NPC price and craft cost under the item's text.
    + What the item really sold for: the median of the last 3 days and how many sell a day. - Beta 0.2 ([0265b9d](https://github.com/Skyblock-Companion/SBC/commit/0265b9d))
+ **Item details on tooltips**: Obtained date, dungeon quality, kill counters and the cost of the next star.
+ **Value breakdown**: Hold Shift on a modified item to see what its estimated value is made of.
+ **Accessory status**: Tells you on an accessory whether it is new to your bag, an upgrade, or already outdone.
+ **Museum status**: Tells you on an item whether the museum takes it, in which category, and whether you already donated one.
+ **Slot overlays**: Rarity glow behind items, pet and book levels, minion tiers and stars on the slot.
+ **Slot locking and item protection**: L over a slot locks it, V protects the item in it: no dragging, shift-clicking, swapping or dropping by mistake.
+ **Chest value**: What the things in an open chest or container would sell for.
+ **Item list and recipes**: Panel beside every inventory with all SkyBlock items: search, filters, stars, recipe and used-in cards (R / U over an item).
    + Finds NPCs, bosses, mobs, places and warps too, each with its own page: where it is, Navigate, its warp and the wiki. - Beta 0.2 ([473f158](https://github.com/Skyblock-Companion/SBC/commit/473f158))
    + Every Hunting shard, with prices and its own page. - Beta 0.2 ([473f158](https://github.com/Skyblock-Companion/SBC/commit/473f158))
+ **Item pickup log**: What you gained and lost in the last seconds with the coin value, in the bottom right corner: inventory changes, sack changes and coins.
+ **Numbers on items**: A small number on an item's icon at the bottom right: pests in a vacuum, Kuudra Key and Crimson armor tiers, Dungeon Potion levels, skill and collection levels in their menus, and more. Each can be switched off.
+ **Armor dye**: Colours the armor you wear (only you see it). Set it with /sbc dye &lt;head|chest|legs|feet|all&gt; &lt;RRGGBB|off&gt;.
+ **Custom item looks**: Names, armor trims, dyes, models, glint and head textures for your own items, by item. Only you see them. /sbc custom opens the screen for what you hold.
+ **Backpack contents on the tooltip**: What a backpack holds, with the total value, without opening it.
+ **Compactor and deletor contents**: What a personal compactor compacts and a personal deletor deletes, on their tooltips.
+ **Coloured enchantments**: Enchantment lists on tooltips: ultimates in pink, max level in gold, one below max in aqua.
+ **Item cards**: SkyBlock items get a tidy card: the item, its name and rarity at the top, stats lined up, abilities marked, gemstone slots as tiles, progress and fuel as bars. Every line Hypixel wrote stays.
+ **Scrollable tooltips**: When a tooltip is taller than the screen, the mouse wheel scrolls it (Shift + wheel: sideways).
+ **The wiki from Hypixel's menus**: The commands of the SkyBlock Wiki menu open the page in the in-game browser.
+ **Get what a craft is missing**: While the key in Controls is held: a green frame on items that lack only more of themselves for their next craft, and a left click takes what is missing from your sacks. Your own items cannot be picked up while it is held. It starts unbound.
+ **Get from sack key**: Press the Get From Sack key over an item in an inventory to run /gfs for it (Shift = a full stack, plain = enough to top up).
+ **Bits on a cookie**: On a Booster Cookie's tooltip: the bits it gives with your fame rank and museum, how long the buff lasts when you buy several, and what you would have available.
+ **Your XP beside a level cost**: On a tooltip that costs experience levels, like the anvil's combine button: the levels you have, green when it is enough.
+ **Who earned a Piece of Wizard Portal**: Puts the Earned by line back on a bought Piece of Wizard Portal.
+ **Pocket Sack-in-a-Sack as a number**: A stitched sack's tooltip says 2/3 instead of two.
+ **Chicken Head timer**: While you wear a Chicken Head: when you can lay the next egg.
+ **Fire Freeze**: A Fire Freeze Staff's timer: how long until a circle goes off, and how long each mob in it stays frozen.
+ **Fire Veil look**: What the Fire Veil Wand's ring looks like: Hypixel's flames, one thin line, or nothing.
+ **Stat icons on tooltips**: Puts each stat's icon after its number.
+ **Numbers for Roman numerals**: On tooltips: Level XII is Level 12.
+ **Ability cooldowns**: Time left on the abilities you use: on the item itself and on a card above the bars.
+ **Enchantment lists**: Re-colours and re-orders the enchantments on item tooltips (ultimates bold pink, max level gold, above the good level gold, good blue, below grey), optionally compressed to three per line or one per line, hides descriptions, and shows stacking enchant progress.
+ **New Year cakes in the Auction House**: In an Auction House search: the cakes your bag already has in red, the ones it lacks in green. Open your bag once so it knows.
+ **Consumable protection**: Right clicking does nothing while you hold a Bottle of Jyrre, Dark Cacao Truffle, Discrite, Moby Duck or Rosewater Flask, so a slip cannot eat what took weeks to mature.
+ **Sign calculator**: In the signs that ask for a number, write 2k+5, 3s (64 each), 1.5m/3 or purse/2: the answer shows above the sign and is what it sends. Also works as /calc.
+ **Magical Power on accessories**: How much Magical Power an accessory gives, on its icon, in the Accessory Bag and the Auction House.
+ **More tooltip lines**: Exotic type on armor, dates in the calendar, the age of evolving items, motes, essence cost in coins, the real colour of a dye, whole separator lines and shard prices in the Hunting Box.
+ **Sack value on the HUD**: What your sacks hold is worth, from selling instantly to buying instantly, and what makes most of it, wherever you are.
+ **Slot text**: Short texts in the corners of slots: dungeon levels, enchantments on books, chips, perks, upgrades and more. Each can be switched off.
+ **Star on ultimate enchant books**: A small pink star on an enchanted book that holds an ultimate enchantment.
+ **Cooldown on items**: A rising fill over items whose right click has a cooldown, like the Grappling Hook or a Treecapitator, as the game does for its own items.
+ **True quiver count**: The arrows you really have in the quiver slot of the hotbar, not 64 at most.
+ **Attributes on items**: Two letters and a level for each attribute of an item that still has them. Yellow is a good roll, green one wanted attribute.
+ **Personal Compactor status**: Whether a Personal Compactor or Deletor is on, as a mark on the item and a line on its tooltip.
+ **Mask cooldowns**: A Bonzo or Spirit Mask that just saved you goes from red to green behind its slot.
+ **Hex colours in their colour**: Hex codes on dyes and armor are written in the colour they stand for.
+ **Stars as a number**: On a tooltip the stars of an item as one number, with Crimson tiers counted.
+ **Swing on abilities**: Your hand swings when you use a right-click ability, on your screen only.
+ **No item bob on updates**: An item whose data changes, like a drill's fuel, no longer plays the equip animation.
+ **Viewmodel**: Held item animation: the item in your hand smaller and floating, moved and turned how you like, with a slow or fast swing that plays smoothly however fast you click. Your bare hand is not changed. Only how it looks to you: hits and abilities stay as fast as they are.
+ **Focus Mode**: Item tooltips show only the name; the Focus Mode key switches it in a menu.
+ **Hide potion effects**: The potion effect list beside your inventory and the effect icons at the top of the screen are hidden on SkyBlock (the tab list and the SkyBlock Menu show them).
+ **Wiki lookup key**: Press the Wiki key (F1) over an item to open its wiki page in the in-game browser.
+ **Item price lookup key**: Press the Price key (F6) over an item to search it in the Bazaar (/bz) or the Auction House (/auctionsearch).

## Chat

Cleaner chat, every filter switchable.

+ **NPC dialogue box**: NPC talk as an on-screen box with numbered answers instead of chat lines.
+ **Join and welcome spam**: Hides the welcome lines, profile ids and update notes when you join SkyBlock.
+ **Warp and transfer lines**: Hides Warping..., Sending to server and similar transfer messages.
+ **Guild and event EXP lines**: Hides lines like You earned 2 GEXP from playing SkyBlock!
+ **Kill combo lines**: Hides +5 Kill Combo and its buffs.
+ **Cooldown warnings**: Hides 'Ability on cooldown' and 'no more charges'. The HUD already shows them.
+ **Friend and guild join/leave**: Hides Friend &gt; X joined. and Guild &gt; X left.
+ **Pet summon lines**: Hides You summoned your X and You despawned your X.
+ **Bazaar, auction and bank progress lines**: Hides the submitting, executing and claiming steps of orders and auctions, and depositing or withdrawing coins.
+ **Dungeon combat spam**: Hides damage, healing, orb and buff lines inside a dungeon run.
+ **Repeating server reminders**: Hides reminders that repeat constantly: blocks in the way, and trees you cannot damage yet.
+ **Stash reminders**: Hides every stash line. Off, the reminders are shortened to one clickable line instead.
+ **Other players' sacrifices**: Hides SACRIFICE! and BONUS LOOT! about other players at the Dragon's Lair.
+ **Other players' rare rewards**: Hides RARE REWARD! about other players' chests.
+ **Parkour lines**: Hides started, finished, checkpoint and cancelled parkour lines.
+ **Unclaimed reward reminders**: Hides You haven't claimed your X Rewards yet! Talk to ...
+ **Hoppity's Hunt has begun**: Hides the line when the hunt starts.
+ **Event level up**: Hides You are now Event Level, the Event Silver you earned and the LEVEL UP line.
+ **Griffin burrow lines**: Hides burrows dug, Griffin Feathers and coins dug out; the Diana cards count them.
+ **Hoppity egg appeared**: Hides HOPPITY'S HUNT A Chocolate X Egg has appeared!
+ **Hunting shard chatter**: Hides 'X ate too much and passed out!' and other redundant shard lines.
+ **Anita fortune reminder**: Hides [NPC] Jacob: Your Anita's Talisman is giving you ...
+ **Arachne call lines**: Hides Arachne's Calling and Arachne Crystal placed from other players, also inside the Sanctuary. The Arachne fight filter is the finer one.
+ **Watchdog announcements**: Hides the [WATCHDOG ANNOUNCEMENT] blocks.
+ **Fire sale announcements**: Hides FIRE SALE lines.
+ **Sky Mall buff lines**: Hides New day! Your Sky Mall buff changed! Away from the mining islands only; there it stays.
+ **Lottery buff lines**: Hides the daily line that your Lottery buff changed.
+ **Trade and visit advertisements**: Hides lines in public chat selling, buying or asking for visits, and keeps everything else.
+ **Implosion and wave lines**: Hides Your Implosion hit X enemies ... and Your Molten Wave ...
+ **Teleport ability lines**: Hides There are blocks in the way! and similar from the Aspect of the End.
+ **Dungeon class stat lines**: Hides [Berserk] Melee Damage 48% -&gt; 88% and the other class buff lines at the start of a run.
+ **Boss dialogue**: Hides [BOSS] Name: ... speech lines. The Dungeon copilot still reads them.
+ **Hypixel lobby announcements**: In the lobbies outside SkyBlock, hides player joins, mystery boxes, prototype and tournament lines.
+ **Teleport pad lines**: Hides that you warped from one pad to the other, and pads without a destination.
+ **Chocolate Factory upgrades**: Hides Hypixel's lines about employees promoted, barn capacity and chocolate per click.
+ **Legacy items warning**: Hides the reminder that you have legacy items to exchange in /legacytrades.
+ **Feast Chef Ted**: Hides Thanks for the donation! Kernels going into your purse while you farm.
+ **Heal lines**: Hides You healed yourself, and that someone healed you.
+ **Show-off lines**: Hides other players using /show: is holding, is wearing, has.
+ **All player deaths**: Hides every death line, near or far. To hide only those far away, use Hide far death messages.
+ **Chicken Head eggs**: Hides You laid an egg!
+ **Compact Jacob reward claims**: The multi-line FARMING CONTEST REWARDS CLAIMED block becomes one line.
+ **Compact treasure chest rewards**: The mining treasure chest reward block becomes one line.
+ **Compact stash reminders**: The stash reminder becomes one clickable line, repeated only when the amount changes.
+ **Other annoying lines**: Hides what repeats and rarely matters, by kind below. It hides lines that some players want, so it starts off.
+ **Filtered lines**: Where a line goes that a chat filter here takes away: gone, or on the action bar, or a toast.
+ **Empty lines**: Hides every empty line in chat.
+ **Hide sack changes**: Hides the [Sacks] line; everything that reads it still does. Use this instead of Hypixel's own setting.
+ **Hide mana use**: Takes -20 Mana (Ability) off the action bar. Goes well with the bars on the HUD.
+ **Item in Dark Auction results**: The line that says who won adds which item it was.
+ **Enchanted book names**: A RARE DROP! Enchanted Book line says which book it was, once it is in your inventory.
+ **Accept prompts with a click**: A yes or no prompt in chat: with chat open, a click anywhere within 60 seconds says yes. Only your click sends it.
+ **Copy chat lines**: Right click a line in the open chat to copy it. Ctrl copies only that row, Alt keeps the colour codes.
+ **Marked players**: Players you marked with /markplayer stand out in chat.
+ **Players joining your lobby**: A line when players on your list join or leave the lobby you are in. Not every lobby lists everyone.
+ **Current chat**: While chat is open: which channel you are typing in.
+ **Hide repeated lines**: The same server line twice within 4 seconds is shown once.
+ **Your own chat rules**: Hide, replace, announce, toast or play a sound on lines that match your text. /sbc rules opens the list; /sbc rule adds one by command.
+ **Peek chat**: Hold the Peek Chat key (see Controls) to read the latest chat lines without opening chat.
+ **Hide ranks in chat**: [MVP+] and other rank tags are removed from player messages.
+ **Party tab-complete**: /p and /party suggest your party members and everyone online after invite, kick, promote, transfer and the like.
+ **Short party commands**: /pk kicks, /pt transfers (alone it is your play time), /pp promotes, /pd disbands, /pko kicks offline members, /pw warps, /rp invites everyone again after disbanding. With tab-complete. Off, these go to the server as typed.
+ **Give the party back**: /rpt transfers the party back to whoever gave it to you.
+ **Accept the last party invite**: /p accept with no name, or the Accept party invite key in Controls, joins whoever invited you last.
+ **Reparty offer**: When a leader disbands and invites you again, a line to click, or the Accept party invite key, joins. It never joins by itself.
+ **Party on boop**: A line to click to invite someone who sent you /boop.
+ **Transfer cooldown message**: A chat line when the 3 seconds after changing servers are over.
+ **Compact Bingo level ups**: On a Bingo profile, skill, SkyBlock and collection level ups and new areas take fewer lines of chat.
+ **Hide gift rewards**: Hides what gifts give you and the lines about gifts you cannot place. The Gift profit card still counts them.
+ **Compact potion messages**: The long BUFF! splash lines become one short "Potion Effect! &lt;effect&gt; by &lt;player&gt;" line (click for /effects).
+ **Compact bestiary**: The multi-line BESTIARY level-up block becomes one line with the details on hover (milestones get their own line).
+ **Compact experiment rewards**: After an Experimentation Table menu, the claim line carries the rewards on hover instead of one line each.
+ **SkyBlock XP in chat**: Writes the action bar's "+5 SkyBlock XP (...)" into the chat when it appears.
+ **Player messages**: Hides all-chat trade spam (lowballing, buying/selling, ...) and can strip ranks from player names.
+ **Shorten coin amounts**: Gold coin amounts in chat become 2.65M instead of 2,650,000.
+ **Pet rarity in drop messages**: PET DROP!, catch, claim and Oringo lines get the pet's rarity written before its name.
+ **Hide far death messages**: Hides the death of players who were not within 25 blocks of you in the last 30 seconds (not in dungeons or Kuudra).
+ **Player line format**: Builds player lines again from their parts: the order, brackets, name colours. It changes how chat looks, so it starts off. Ranks are hidden under Player messages.
+ **Powder mining chat**: Hides the lines of treasure chests you do not care about: small powder and essence, common items, low gemstones and eggs. It hides lines, so it starts off; compact chest rewards stay as they are for the lines that are left.
+ **Crystal Nucleus chat**: Shortens what a Crystal Nucleus run says: crystals found and placed, the end of a run, Professor Robot, King Yolkar, the Keepers and the goblin guards. It changes what you read, so it starts off.

## Navigation

+ **Path line**: A line and a beam that lead you to wherever you are going.
+ **Waypoints**: Your saved waypoints and the ones other features show, drawn in the world. /sbc wp opens your groups.
+ **Arrival title**: A short title in the middle of the screen when you reach where /sbc nav was taking you.
+ **Spider relics**: Marks the relics in the Spider's Den.
+ **Event baskets**: Marks event baskets near you.
+ **Warp menu**: The Fast Travel menu as a map of island pictures with a portal on every warp: click a portal, or an island for its first warp. Tab shows Hypixel's own menu.
+ **Island areas**: The areas of this island, nearest first: beside your inventory, where a click finds the path, and if you like as a card, on entering, and in the world.
+ **Relic route**: Leads you through the relics of Spider's Den you have not found, one after another. /sbc relics reset forgets the ones found.
+ **Fairy souls pathfinder**: Routes you through the island's unfound fairy souls one after another (navigator path + waypoint); found souls are remembered per profile, the Fairy Souls Guide menu sets Hypixel's totals.

## World

+ **Party member outlines**: Party members get an outline in the world, so you find them. Not in dungeons.
+ **Night Vision strength**: How bright a Night Vision effect makes the world, in SkyBlock. /sbc nightvision &lt;0-100&gt;, off or full.
+ **Builder's Wand preview**: On your Private Island and Garden: the blocks a Builder's Wand or Builder's Ruler would place, or remove while you sneak with the ruler.
+ **Teleport overlay**: The block a teleport item would put you on, while you hold it.
+ **Gyrokinetic Wand reach**: The ten blocks the wand pulls from, around where you aim it, while you hold it.
+ **Smooth teleports**: The camera slides to where a teleport item put you instead of jumping.
+ **Hide things in the world**: Tick what you would rather not see.
+ **Outline on dropped items**: Items on the ground get an outline in their rarity's colour.
+ **Marked players in the world**: Players you marked get an outline when you can see them.

## Commands

Shorter commands and your own. `/sbc` lists every command of the mod.

+ **Tab-complete**: Hypixel's commands suggest what they take: warps, contacts, sack items, recipes, players. What you type still goes to Hypixel as typed.
+ **/viewrecipe in lower case**: /viewrecipe aspect of the end or aspect_of_the_end finds the item, as if you had typed its id.
+ **Warp without /warp**: /wizard warps like /warp wizard, for every warp. /jerry on your island and /barn in the Garden keep their own meaning.
+ **/warp is**: /warp is goes to your island like /is.
+ **Wait out the transfer cooldown**: A warp you type in the 3 seconds after changing servers goes out when they are over, instead of being refused.
+ **Last storage**: /ec - and /bp - open the page you last asked for with /ec or /bp. /sbc lastopened opens the last of either.
+ **Speed presets**: /setmaxspeed crops sends the speed you saved under that name. /sbc speed lists them, runs one and changes them.
+ **Shortcuts**: Your own short commands and keys, anywhere, even outside Hypixel: /ga for /warp garden, /m for /msg, a key for /pets. /sbc shortcuts sets them up.
+ **Todos**: Your own timers. A line in chat, the action bar, the tab list or the sidebar starts one, per profile; the card says when it is ready. /sbc todos edits them.
+ **Reminders**: /sbc remind 1h30m Check the forge says so when the time comes, here or in any later game, until you dismiss it.
+ **Reminders card**: What reminders are coming and in how long.
+ **Run early commands later**: A command Hypixel refuses for being too soon after joining a server runs again by itself when it may.

## Garden

+ **Pest toast**: A toast and a sound when pests spawn on your Garden, with the plot name.
+ **Farming profit**: Crops and drops while you farm, at NPC and Bazaar prices, per hour; Crop Fevers and rare crops.
+ **Pest profit**: What pests drop at NPC and market prices, how many you killed of each and what one is worth.
+ **Next growth stage**: A line on the scoreboard, on every island, with the time to your Greenhouse's next growth stage. Open the Crop Diagnostics menu once to set it. Move it with the scoreboard's entry list.
+ **Farming card**: While you hold a farming tool: Cultivating, coins an hour at the Bazaar, Farming level and XP an hour, the tool's level, fortune, speed, Sowdust, yaw and pitch. Crops a minute and blocks a second are on the crop milestones card.
+ **Pests**: Pests alive out of the most there can be, a map of the infested plots (also as waypoints), how many are on your plot, spawn alerts, the spawn cooldown, spray, repellent, bonus fortune and your pest traps.
+ **Visitors**: Who is waiting in their rarity's colour, what each asks for, and when the next visitor comes, also how much sooner while you farm and an estimate of the sixth.
+ **Composter card**: Time left and stored compost, then organic matter and fuel; a warning when it runs low.
+ **Jacob's contest**: The contest's crops with your last measured crops per second, the crop you farm and the boosted crop picked out, and the time left or until it starts.
+ **Optimal farming speed**: The speed the held tool's crop wants (93 / 155 / 233 / 328 / 464) against your current speed on a gauge, with your farming fortune.
+ **Pest boxes**: A bright red box on every pest, with nothing written on it, so you spot them at once.
+ **Pest finder**: Infested plots get lines on their walls in the world with the pest count while you hold a vacuum. Where the vacuum's ability points is the Pest guess.
+ **Visitor offer prices**: In a visitor's menu, the Accept Offer button says what the items cost, what you pay per copper, how many crops they are and how long you farm them.
+ **Visitor offer block**: In a visitor's menu, Refuse Offer takes no click when the visitor has a reward on your list or you never accepted an offer from it. Hold the bypass key to click anyway. The copper and coin limits are off until you switch them on.
+ **Visitor engine**: Reads what each visitor wants from their menu, for the shopping list, the buy list, the block and the stats.
+ **Visitor chat**: A title and a chat line when a visitor arrives, in place of Hypixel's line. The visitors' talk is hidden, except for the few with long dialogues. An accepted offer becomes one line, with the whole block on hover.
+ **Visitor shopping list**: All the items your visitors want, with their price and how many your sacks hold, on a card and beside a visitor's menu. Click a line beside the menu to find it on the Bazaar with the amount copied; click a visitor there to leave it out of the list.
+ **Visitor statistics**: What accepted offers brought in on this profile: visitors by rarity, copper, Farming and Garden XP, coins spent and rare rewards. /sbc resetvisitordrops starts over.
+ **Charmed visitors**: The visitors you charmed with the Gift Vinyl Set. Read from each visitor's menu; click a name beside the menu to take it off.
+ **Visitor status in the world**: Over each visitor on the Barn plot: New, Items Ready, Accepted or Refused, and the rare reward it has. A new visitor can also get a box around it.
+ **Visitors outside the Garden**: On the other islands, the people you can still meet as visitors get a red tint, with their name and distance. Can also stop you from talking to them, to keep Dedication cycling going.
+ **Composter menus**: In the Composter: how much organic matter, fuel and compost there is, on the buttons. In Composter Upgrades: each upgrade's level on its icon, what it costs in coins in its tooltip, and what you can buy now in gold.
+ **Composter warnings**: A warning when your composter is about to run out or is empty, on every island, and when its organic matter or fuel is low.
+ **Composter outside the Garden**: A card on the other islands with the time until your composter is empty.
+ **Composter helper**: Beside the Composter: what fills your organic matter and fuel for the least coins, and with what you picked what a compost costs and earns. Beside Composter Upgrades: what the upgrade under your mouse would change.
+ **Contest helper**: Beside Your Contests: how long each crop takes to the medal you pick, at your fortune and speed, and the fortune every medal of the contest under your mouse took. Open more pages for more contests.
+ **Contest summary**: At the end of a Jacob's Contest, in chat: the blocks you broke, your average blocks per second and how long you took part.
+ **Personal best fortune**: After beating your contest record, in chat: how much more farming fortune the Personal Bests perk now gives you.
+ **Zorro's Cape protection**: Blocks claiming a contest's rewards while you don't wear Zorro's Cape, which gives more of them. Opens no menu for you: the Stats and Equipment menu has to have been opened to know what you wear.
+ **Next Jacob's contest**: The crops and start of the next contest, read from the calendar: open it and turn its pages once a year. A warning shortly before the contest starts, with its crops.
+ **Configure Plots highlights**: In Configure Plots, plots get a colour by their state: pests, a spray with its minutes left, locked, the one you are on, or being pasted.
+ **Plot price**: In Configure Plots, the price in coins of what the next plot costs.
+ **Plot borders**: The borders of your plot in the world, as F3 + G shows chunks, with the build limit: toggle them with the Plot Borders key, or see them while holding a Sprayonator.
+ **SkyMart copper prices**: Beside SkyMart: what each item earns per copper when sold, the best first. Hover a line for the numbers.
+ **Anita medal prices**: Beside Anita's shop: what each item earns per bronze medal, the best first; a gold medal is 8 and a silver medal 2 of them.
+ **Anita's Extra Farming Fortune**: On Anita's Extra Farming Fortune: your tier, and the medals, tickets and coins it costs to max it out.
+ **Carrolyn fetch helper**: Her fetch items say so in their tooltip. Using one shows the way to Carrolyn, or offers the warp to the Crimson Isle in chat first.
+ **Crop milestones**: For the crop you farm: milestone tier, progress x/y and %, time to the next tier, crops per second/minute/hour and blocks per second. The counter comes from the tool (Cultivating / hoe counter), the Crop Milestones menu and the tab widget.
+ **Best crop time**: Which crop reaches its next milestone soonest at your measured speeds.
+ **Money per hour**: Coins per hour for the Bazaar forms of your crops at your measured speed (sell offer price).
+ **Pest spawn title and sound**: A title when pests spawn, how the spawn line reads in chat and what the spawn sounds like.
+ **Pest timer**: Since when the last pest came, the spawn cooldown and the average time a spawn takes, with a warning before the cooldown ends if you want one.
+ **Pest guess**: When you use a vacuum, a waypoint at where its particles are heading, worked out from their path.
+ **Plot spray**: Which spray is on the plot you stand on and how long it lasts, and a chat line when a spray runs out on any plot.
+ **Stereo Harmony**: The vinyl your vacuum plays and the pest and crop it boosts. In its menu, each disc shows its crop, and the running contest's crop is picked out. Open the menu or hold the vacuum once to set it.
+ **Pest trap warnings**: Tells you again and again that a trap is full or out of bait, until you dealt with it. Needs the Pest Traps widget in your tab list.
+ **Mantid bonus**: With the Mantid reforge: how far your kill bonus is out of 20 and when the next pests fall out of it. Counted while you wear it, and it starts over when you change world.
+ **Pesthunter profit**: Beside Pesthunter's Wares: what each trade is worth per pest you give for it, best first.
+ **Pest highlight**: A box around each pest on the Garden: green for the running contest's crop, blue for the vinyl you play.
+ **DNA Analyzer solver**: In the DNA Analyzer, the two strands to swap next are green, for the fewest swaps. You do the clicking.
+ **Crop Diagnostics colours**: In the Crop Diagnostics menu: the Growth Status is green when the crop can be harvested, and the Water Status green when it has enough water.
+ **Analyzed mutations**: A greenhouse mutation's tooltip says whether you have analyzed it. It learns from the message when an analysis completes, and from the All Mutations menu. - Beta 0.2 ([e6a6b3e](https://github.com/Skyblock-Companion/SBC/commit/e6a6b3e))
+ **Phantomleaf solver**: While you look for a Phantomleaf, its hiding spot in green, worked out from the sounds it makes. Stand still for best results.
+ **Paste Greenhouse**: Copy a layout link, then /sbc greenhouse paste: the layout shows in your Greenhouse plot as a guide. /sbc greenhouse end removes it; rotate left, rotate right and mirror turn it, save &lt;name&gt; keeps the copied link, paste &lt;name&gt; brings it back.
+ **Crop Milestones menu**: In the Crop Milestones menu: the tier on each crop, the average of them, and in the tooltip the road to the last tier.
+ **Mooshroom Cow perk**: With a Mooshroom Cow while you farm something else: your Mushroom milestone, how far it is and how long it still takes. What the mushrooms and rare crops make is added to Money per hour.
+ **Crop start location**: A waypoint at the start of the farm of the tool in your hand, and where you last farmed with it. Set it with /sbc cropstartlocation, or by breaking a first crop.
+ **True fortune**: Your whole fortune for the crop of the tool in your hand, what pests take off it, and the Pesthunter's bonus. Needs the Stats widget in the tab list.
+ **Garden level**: Your Garden level and how far into it you are, also past the last level. Read from the tab's Garden Level widget, the Desk and the Garden Experience you get.
+ **Hoe level**: The level of the tool in your hand and the experience to the next one, also past level 50.
+ **Prevent breaking crops**: Stops you from breaking crops while you hold a fishing rod or a Sprayonator.
+ **Farming Toolkit crops**: In the Farming Toolkit, each tool's crop in its corner.
+ **Burrowing Spores title**: A title when Burrowing Spores drop while you farm mushrooms.
+ **Rare crop lines**: What the RARE CROP! lines do: stay in chat, go away, or turn into a toast.
+ **Atmospheric Filter**: The buff the Atmospheric Filter gives in the current season.
+ **Yaw and pitch**: Your yaw and pitch to more decimals than the Farming card, while you hold a farming tool. It fades when the mouse rests.
+ **Farming signs**: A button per crop in the signs of Squeaky Mousemat and Rancher's Boots, that writes its angles or speed. You close the sign yourself.
+ **Wrong speed warning**: A title and a chat line while you farm at another speed than the crop wants. With Rancher's Boots you can click the line to set it.
+ **Garden warp commands**: On your Garden: /home goes to your Garden spawn, /barn to the barn and /tp &lt;plot&gt; to a plot. Also the keys for them in the game's Controls.
+ **Mouse lock**: Lock your mouse with /sbc mouselock or lower its sensitivity with /sbc sensreduce, anywhere, until you change island; or let it happen by itself on your Garden while something below is true. Only your own mouse does less.
+ **Farming keys**: While you hold a farming tool on your Garden, the game's movement and click keys answer to the Farming keys you set in the game's Controls. A Farming key left unbound changes nothing.
+ **Farming lanes**: Learns where your lane turns with /sbc lanedetection. Then the distance and time to its end, a warning shortly before, and its corners in the world.
+ **See-through window**: A key in the game's Controls makes the window see-through while you farm. It goes back when you change world.
+ **Garden uptime**: Counts how long you farm each day. /sbc gardenuptime lists the days.
+ **Logbook stats**: Beside the Visitor's Logbook: how many visitors came, were accepted and were denied, over the pages you opened.
+ **Harvest Feast crops**: The crops in season at the Harvest Feast and when they change. Talk to Ted and open All Crops to set it, each feast.
+ **Garden Plots panel**: Beside your inventory in the Garden: all 25 plots with their icons. Click one to teleport to it, right click to pick a custom icon. Plots with pests flash red. Buttons for the Desk, the Garden spawn and setting the spawn. Drag it by its title; Shift + right click on the title puts it back.
+ **Jacob's contest menu**: In Your Contests: rewards still to claim, the medal of each contest, and when it really took place.

## Farming Islands

+ **Trevor's animal died**: When the animal dies: a title with a sound, and a line to click that calls Trevor.
+ **Trevor tracker**: Quests, pelts and pelts per hour, animals you killed and that died on their own, by kind.
+ **Trapper key**: On the Farming Islands, the Trapper key in Controls warps to Trevor's Den, or says yes to his question. It starts unbound.
+ **Trevor helper**: On the Farming Islands: a waypoint to the area of Trevor's animal, and to the animal once you can see it; the height and the ring from a Talbot's Theodolite; his cooldown over his head; a title when he is ready again.
+ **Hungry Hiker**: Says which food the Hungry Hiker on the Farming Islands means.
+ **Treasure Hunter**: Turns the Treasure Hunter's hint into where to mine, with a waypoint to click.
+ **Trevor card**: Where the animal is, the height hint and your pelts.
+ **Glowing mushrooms**: Outlines the glowing mushrooms in the Mushroom Cave.
+ **Trevor's animals**: The animals of Trevor's tasks get an outline in the colour of their rarity.

## Mining

+ **Mining emissaries**: The name of every emissary that gives Commissions, and how far away it is.
+ **Mining profit**: What mining brings in at NPC and market prices, per hour, on every mining island.
+ **Excavator profit**: What the Fossil Excavator finds at the Fossil Research Center, its Glacite Powder, and what the Suspicious Scrap cost.
+ **Corpse profit**: What corpses in the Glacite Mineshafts give at market prices, what the keys cost, keys saved by Resourceful.
+ **Crystal Nucleus profit**: What each Crystal Nucleus run gives at market prices, less the Jungle Key and the Professor's parts.
+ **Dark Monolith tracker**: Coins and Mithril Powder from Dark Monoliths, and how many you looted.
+ **Mining event tracker**: The running Dwarven Mines / Crystal Hollows event with the time left (chat, tab widget or boss bar).
+ **Powder this session**: Mithril, Gemstone and Glacite powder gained this session (from the Powders tab widget and the sidebar), with a per-hour rate.
+ **Mineshaft corpses**: Which corpses in a Glacite Mineshaft are still to loot (from the tab list).
+ **Treasure distance and fossil progress**: The metal detector's distance from the action bar and the fossil excavation percentage.
+ **Freezing warning**: A warning and a bell when Cold reaches 80 in the Glacite tunnels (Cold itself comes from the sidebar).
+ **Commissions**: Your commissions from the tab list with their progress; DONE ones marked (the chat's 'Commission Complete!' counts at once).
+ **Powder tracker (Crystal Hollows)**: Chests lockpicked, hard stone compacted and every chest reward from the chat with per-hour rates; Double Powder state.
+ **Glacite Mineshaft**: Which mineshaft you entered and how many since the last of that type, the 60 s cave-in timer with an estimate from your Cold, waypoints for the entrance and ladder, corpses found and looted, the known corpse spots, and corpse coordinates shared in chat.
+ **Mineshaft pity**: The pity counter from the tab list, the last mineshaft and the session count (Glacite Tunnels).
+ **Mining notifications**: A title and a sound for a Glacite Mineshaft portal, a Suspicious Scrap, Golden and Diamond Goblins, and when Cold reaches the threshold.
+ **Block break animation**: The cracks on the block you mine follow your Mining Speed and the block's strength, so they keep up on a slow connection. Needs Mining Speed in your /tab.
+ **Actual Gemstone Powder**: A treasure chest's Gemstone Powder line also says what you really got after every multiplier, with each one on hover.
+ **NPC riddles**: Says what Fetchur and the Fossil Muncher want instead of their riddle, and marks the block the Puzzler's arrows lead to.
+ **Next step in chat**: A line to click after a commission (call Queen Mismyla), on a bad signal (redial), when Don Expresso is full (teleport to him) and when you have no Crystal Hollows Pass (warp, and where to buy it). Only your click does anything.
+ **Cold frost**: Frost around the screen as your Cold rises in the Glacite Tunnels and Mineshafts. It changes how the game looks, so it starts off.
+ **Unbreakable carpets**: Marks the carpets in ore veins of the Dwarven Mines that cannot be mined. Only what you can already see.
+ **Crystal Hollows places**: Where the places of this lobby are, with their name and distance: from walking into them, coordinates in chat, what the NPCs say and two uses of a Wishing Compass. /sbc crystalwaypoints adds, shares and removes them.
+ **Names from the nucleus**: From the Crystal Nucleus you see in which direction the four quarters and the nucleus lie.
+ **Nucleus crystal boxes**: Coloured boxes in the Crystal Nucleus where each crystal goes.
+ **Area walls**: Coloured walls between the quarters of the Crystal Hollows and over the Magma Fields. They change how the place looks, so they start off.
+ **Crystal Hollows map**: A small map of the Crystal Hollows with you on it and the places you know. It is a prepared picture, it does not read the world.
+ **Fossil Excavator solver**: Lights the tile most likely to hold the fossil and says beside the chest which fossils can still be on the board, and hides the tooltips that only get in the way there.
+ **Scrap from sacks**: A button beside the Fossil Excavator that fetches Suspicious Scrap from your sacks when you press it, or opens the Bazaar when you have none.
+ **Metal Detector**: In the Mines of Divan: finds the treasure chest from the Metal Detector's distance, with a line, a box and how far, and warns when you carry all four Keeper tools.
+ **Treasure chests**: Each chest you uncover in the Crystal Hollows: how long it lasts, in a colour that runs out, on a card and on the chest, lines from one to the next, the lock's spot and the locks picked.
+ **Flowstate**: While you mine with Flowstate: your streak, the speed it gives, how long it still holds and your best.
+ **Heart of the Mountain**: In /hotm: perks green when on, red when off and gray when locked, your tokens on the heart, and in a perk's tooltip what it has cost, what ten more levels cost and what you have.
+ **Sky Mall**: Today's Sky Mall buff on a card, from chat or from your /hotm.
+ **Pickobulus**: While you hold a tool with Pickobulus: an outline of what it would break from where you aim, and a card with how many blocks and what they give.
+ **Commission places**: Where your commissions are done, with name and distance, and the Emissaries when one is done.
+ **Finished commissions in the menu**: Commissions that are done stand out green in the Commissions menu.
+ **Commission mobs**: A box around the mobs your commissions ask for, where you can see them. It starts off.
+ **Mineshaft odds**: In the Glacite Tunnels: your chance of a Mineshaft now, the blocks you mined for it, the average and how many of each block are still needed; on the spawn message when you hover it.
+ **Your Golden Goblin**: A box around the Golden or Diamond Goblin you just spawned, where you can see it.
+ **Superprotectron**: Colours the Superprotectron during a Goblin Raid, where you can see it.
+ **Quieter mines**: Takes away the panting at high Heat in the Crystal Hollows, and the Organ Donor's note once every corpse in a Mineshaft is found.
+ **Deep Caverns guide**: The way to the bottom of the Deep Caverns and to Rhys, step by step, until you know your way: it starts by itself, or from a button beside the Lift.
+ **Tunnel Maps**: A path to any place in the Glacite Tunnels: choose it beside your inventory, or right click a collector commission. The nearest spot goes first; a key, or a left click with the Royal Pigeon, picks the next.
+ **Precision Mining spot**: A box on the particle that shows where Precision Mining wants you to look, green while you look at it.
+ **Fallen Star Cult**: When the next Cult Meeting is, or how long this one still runs. It shows where you tick; with nothing ticked, always.
+ **King Talisman**: Which Dwarven Kings you still have to talk to and when each stands in the Royal Palace.
+ **Gemstone money**: What the gemstones you mine would sell for per hour, from your pristine procs and what goes to your sacks.
+ **Dark Monolith marks**: The Dark Monolith's egg, once you can see it: a title, a colour and a beam. What it gives is on the Dark Monolith card.
+ **Ores by commission**: Ores your commissions ask for get one colour, the others gray, only on your screen, in the Crystal Hollows and Glacite Tunnels. It changes how the world looks, so it starts off.
+ **Ordered waypoints**: A numbered route you load with /sbc ordered load, from your clipboard or a saved route: where you were, where to go now and what comes next, moving on as you reach each block.
+ **Drill fuel bar**: The bar on a drill shows its fuel, on a Pickonimbus the uses it has left.

## Foraging

+ **Foraging tracker**: Tree gifts: trees you helped cut, logs and drops at NPC and market prices, Foraging and HOTF experience, Forest Whispers.
+ **Starlyn coupon prices**: Beside the shops of Agatha and Miria: what each item earns per coupon, the best first.
+ **Compact Starlyn contests**: A sister's contest results in one line, and your log personal bests during a contest in one line.
+ **Tree progress**: How far the tree you are cutting is.
+ **Compact sweep details**: The Sweep Details block becomes one line with the penalties in brackets.
+ **Honeyhive and phantom alerts**: A reminder two hours after you looted a honeyhive, the honeyhives you have not looted yet marked on the Torrhus Canyon, and an alert when a phantom falls from a tree.
+ **Clean tree view**: The blocks flying from a tree you cut are not drawn.
+ **Mute foraging sounds**: Silences the sounds below.
+ **Foraging chat**: Lines of foraging that only clutter your chat.
+ **Tree felled title**: A title when you fell a whole tree.
+ **Sweep details**: Your sweep, the tree's toughness, the logs and what the last hit lost, for five seconds after it.
+ **Sweep overlay**: Holding an axe, marks the logs your hit would cut with your Sweep. Needs Sweep in your tab list.
+ **Foraging highlights**: Lushlilac and Sea Lumies on Galatea, full Honeyhives and Ruby Veilshrooms in the Torrhus Canyon.
+ **Temple puzzles**: The clicks each block of the Forest Temple needs; in the Desert Temple the order of the buttons and where the statues go.
+ **Foraging beacon**: Solves tuning the Moonglade Beacon: the clicks each setting needs, and what it should be. Says when the beacon is ready.
+ **Heart of the Forest**: Perks in green when on and red when off, Tokens on the Heart, and what each perk took and will take in Forest Whispers.
+ **Rotating perks**: Today's Lottery and Beekeeper buffs, once Hypixel has said them.
+ **Tree gift lines**: The long Tree Gift message becomes one line with what you helped cut, the rewards on hover and the bonus drops under it.
+ **Foraging tutorial quest**: Guides you through the quests that open the Park: where to go next.
+ **Frog Mask**: Which region of the Park your Frog Mask boosts today, and a warning when you forage or stand somewhere else.

## Fishing

+ **Hotspot radar**: Where your Hotspot Radar points: the distance to the hotspot and the way there, after you right-click it.
+ **Fishing profit**: What fishing brings in at NPC and market prices, per hour.
+ **Sea creatures**: How many of each sea creature you caught, with its share.
+ **Bobber timer and bite mark**: Shows how long your line has been out and marks the bobber with a sound when a fish bites.
+ **Sea creature alerts**: Counts the sea creatures you catch and gives a toast and a chime for the rare ones.
+ **Fishing session**: Sea creatures by rarity and per hour, coin catches, double hooks, trophy fish by tier, when the Golden Fish surfaced; an alert on legendary and mythic catches.
+ **Rods, bait and sea creatures**: What the fishing helpers read: your rod, the bobber, the bait and which sea creatures are alive.
+ **Bait**: The bait your rod uses and how much is left, while you hold a rod.
+ **Reel timer**: Hypixel's countdown over your bobber, on your screen, and how long the line has been in the water.
+ **Sea creature cap**: How many sea creatures are yours and around you against the caps, and the time until the first one despawns. Warns before you lose them.
+ **Golden Fish**: When the Golden Fish can come, how likely it is by now, and its hits and despawn once it is up.
+ **Totem of Corruption**: Time left and owner of the Totem near you, and how far it reaches.
+ **Shark counter**: Sharks caught this Fishing Festival by kind, with a line in chat when it ends.
+ **Sea creature health**: The health of the sea creatures around you, yours in green.
+ **Sea creature kill time**: How long a rare sea creature took to die, in chat.
+ **Trophy fish**: Which trophy fish you caught at which tier, from Odger's menu and your catches, and what a fish fillets for.
+ **Trophy fish in chat**: Each catch says how many of that fish and tier you have, with all your counts on hover.
+ **Caught item name**: The name and count of an item floating near you while you hold a rod.
+ **Wormhole finder**: Wearing Froggles, the arrows point at a wormhole: its place from the island map is marked, and you are told when it closes.
+ **Thunder Spark highlight**: Marks Thunder Sparks, seen through lava when you are close.
+ **Geyser**: A box on the lava where the Blazing Volcano's geyser is, and its particles out of the way of your bobber.
+ **Odger waypoint**: Where Odger stands, while you carry trophy fish and do not hold a lava rod.
+ **Lootshare range**: A sphere of 30 blocks around sea creatures: green where you share the loot.
+ **Hide Flay and Soul Whip particles**: Their particles close to you stay out of your view.
+ **Mute Banshees**: The Banshee's wail in the Backwater Bayou is silent.
+ **Hide Chum Buckets**: The name and count over Chum and Chumcap Buckets are not drawn.
+ **Sea creature lines**: A Double Hook goes on the line of what you caught, instead of a line of its own.
+ **Cocoon warning**: A title and a sound when a rare sea creature of yours gets cocooned.
+ **Hide other players' rods**: Bobbers of other players are not drawn.
+ **Coral fish helper**: Beside the Fish Family menu, the fish you have not shown yet, cheapest first.
+ **Lava replacement**: Draws lava with the water texture, so you see your hook while lava fishing.
+ **Common sea creature hider**: While you fish, sea creatures that are not rare turn see-through, so the rare ones stand out.
+ **Charged bottle while fishing**: A chat line when a Thunder, Storm or Hurricane in a Bottle in your inventory is full while you are fishing.

## Hunting

+ **Hunting profit**: The shards you catch at market prices, mobs caught and shards collected.
+ **Shard tracker**: Shards you picked with the Track shard key: how many are in your Hunting Box against what you need, and what you gained of them.
+ **Pure Reptile alert**: A sound and a note when a fusion gives a Pure Reptile.
+ **Hunting highlights**: A box with name and distance on the mobs you hunt on Galatea and in the Torrhus Canyon, when you are near them.
+ **Floor drops**: Marks a shard lying on the floor while it sparkles.
+ **Lasso**: How far your lasso's bar has gone, and when to reel.
+ **Fusion**: Beside the Fusion Box, how many of the shard you last fused are in your Hunting Box, and your Pure Reptiles since. Keys to repeat and confirm a fusion can be bound in the controls.
+ **Hunting keys**: The keys to repeat and confirm a fusion and to show the next Shulker. Bind them in the controls.
+ **Shulker finder**: The Show next Shulker key walks you along the spots where Hideonleaf and Hideonsun can be.
+ **Hunting Box ready**: A shard you hold enough of to level its attribute goes green, yellow when you still need to unlock it.
+ **Hunting chat**: Lines of hunting that only clutter your chat.
+ **Safari helpers**: Critter Safari: boxes on what you hunt, the capsule's path, biome names and fewer capsules in your view.
+ **Safari shard checklist**: Which shards of each biome you caught this run.
+ **Hideyho finder**: When Hideyho hides in the Critter Safari, you are led through its places, nearest next, until you see it.
+ **Empty trap guard**: Stops a click on an empty trap in the Hunting Toolkit.

## Combat

+ **Dragon profit**: What the dragons you fight in the End leave you at market prices, less the Summoning Eyes.
+ **Ghost profit**: Ghost drops, coins and Combat XP in The Mist, kills from the Bestiary tab widget, ghosts since your last Sorrow, longest combo.
+ **Ender Node profit**: What Ender Nodes give you in The End, and how many you mined.
+ **Sacrifice profit**: Dragon Essence and bonus loot from the Draconic Altar, per item sacrificed.
+ **Mob health bars**: A bar of health over the name of each mob, from the numbers in its name. Other helpers read those numbers too, so taking them out of the name starts off.
+ **Combat session**: Cocoons, ghost drops and bag-of-cash coins, and your armor stacks when you switch them on.
    + Armor stacks, as an option. - Beta 0.2 ([1671484](https://github.com/Skyblock-Companion/SBC/commit/1671484))
+ **Dragon fight**: Which dragon spawned and when, its HP and your damage from the sidebar; ender node drops.
+ **Mob names**: Works out which SkyBlock mob each one is from its name tag; the slayer and combat helpers ask it.
+ **Combat highlights**: Keeps track of which mobs the slayer and combat helpers highlight or hide.
+ **Compact Damage**: Damage numbers in short form, a crit in a gradient.
+ **Boss health reading**: Keeps track of the bosses around you, for the Damage Indicator and the other helpers.
+ **Damage Indicator**: The health of a boss over its head, its phase, what it is doing and how long the kill took.
+ **Mob highlights**: A glow on Corleone, Arachne and hers, where you can see them.
+ **Zealot highlights**: A glow on special Zealots, and if you want on Zealots with a chest and on all Zealots and Bruisers.
+ **Area bosses**: A glow on the Golden Ghoul, Old Wolf, Soul of the Alpha, Voidling Extremist and Millennia-Aged Blaze, and the seconds until the next.
+ **Enderman teleport hider**: Endermen do not jump about on your screen; they stay where the server says.
+ **Arachne**: How long a kill took, the seconds until she spawns over the altar, and no name tags on her small ones.
+ **Hide Arachne chat**: Her calls and Venom Shots away from her Sanctuary.
+ **Broodmother**: Its stage and the time until it spawns, from the tab list, with alerts.
+ **Cocoon overlay**: Over a mob's cocoon: what is in it and when it hatches.
+ **Deployable reading**: Keeps track of the deployables in range, for the card and the reminder.
+ **Deployables**: The power orb, lantern, Black Hole and Umberella in your range, with the time they have left.
+ **Deployable reminder**: When you start a fight, a Mineshaft or trophy fishing without what belongs to it.
+ **Quiver reading**: Keeps track of your arrows, for the card and the warning.
+ **Quiver display**: The arrow you are using and how many are left.
+ **Low quiver warning**: When your arrows run low, and after a dungeon or Kuudra run for the arrows you used.
+ **Flares**: The flare nearest in power, its time and buff, and a warning before it ends.
+ **Summoning mobs**: Your summoned mobs and their health, what a soul on the ground was, and a line when one dies.
+ **The End reading**: Follows the dragon, the Protector and your Zealot kills, for the helpers.
+ **Dragon weight**: Your weight when a dragon or the Protector goes down, while it fights on a card, and a title for a Superior.
+ **Zealot kills**: Your Zealot kills, those since the last Summoning Eye and how many an eye takes. The Bestiary's Zealot page corrects them.
+ **End Stone Protector**: Its stage and spot, and a waypoint when it is about to rise.
+ **Ender Node boxes**: A box around the Ender Nodes near you, found by their particles.
+ **Bestiary helper**: Beside the Bestiary: every family with its progress, sorted as you choose, and which are maxed.

## Slayer

+ **Slayer profit**: Drops and coins of your slayer quests at NPC and market prices, what the quests cost, and what a boss is worth.
+ **Slayer helper**: Times the kill against your best, counts bosses to the next level and marks Voidgloom beacons. It can also mark your boss.
+ **Carry tracker**: The slayer carries you do on a card: who, what, how many done and what is paid. /sbc carry &lt;player&gt; &lt;type like rev5&gt; &lt;amount&gt; adds one, /sbc carry &lt;type&gt; &lt;price&gt; sets its price.
+ **Slayer progress**: Kills towards the boss and the quest's progress from the sidebar and auto-slayer coins spent, on the Slayer card.
+ **Boss spawn warning**: A warning when the quest progress passes the percentage.
+ **Blaze attunement alert**: Which attunement the dagger needs, as a coloured alert.
+ **Slayer quest**: Knows your slayer quest, its state and which slayer this area is for; the slayer helpers ask it.
+ **Slayer minibosses**: An alert when a miniboss of your slayer spawns, and a highlight while it lives.
+ **Slayer items on the ground**: The name and worth of what is lying near you during a quest.
+ **Slayer quest warning**: When you hit slayer mobs with no quest running, or in the wrong slayer's area.
+ **Slayer times in chat**: How long the kill took, your best, and how long the whole quest took.
+ **Slayer cocoon warning**: A title when you cocoon your slayer boss.
+ **Call Maddox**: A line to click when a quest fails, to call Maddox for a new one. Only your click calls him.
+ **Polar Bear warning**: When you fight with a slayer weapon or Habanero Tactics and have no Re-heated Gummy Polar Bear going.
+ **Unclaimed slayer rewards**: Green in the Slayer LVL Rewards menu on what you can claim.
+ **Block slayers that cannot spawn here**: In the Slayer menu, a click on a quest that says it does not exist here does nothing.
+ **Hide mob names**: Hides the name tags of the mobs you farm for a slayer quest while they are at full health or dead.
+ **Hide slayer spawn particles**: Where a mob just died during your quest, in the right area.
+ **Mute slayer mob sounds**: Quiets the mobs of a slayer area.
+ **See-through during your boss**: While your slayer boss is alive, other mobs are see-through. Your boss and what you hit last stay solid.
+ **See-through mobs of no use**: Mobs that give nothing for your quest are see-through: the Burning Desert's Magma Cubes.
+ **Slayer background**: Keeps the slayer helpers running.
+ **Slayer RNG Meter**: The drop you chose and how many bosses it is away, with the meter. Open the slayer's RNG Meter menu once to set it.
+ **Remaining slayer kills**: How many of each mob around you until the boss spawns, with your Combat Wisdom, weapon, gear and the mayors counted.
+ **Slayer boss bar**: Your slayer boss's health as a bar.
+ **Hellion shield colours**: An Inferno Demonlord and its demons glow in the colour of their shield, and it is written over them.
+ **Blaze daggers**: While you hold a blaze dagger: which attunements it and its twin are on, and which one the nearest shield wants.
+ **Fire Pits warning**: A title and a sound when an Inferno Demonlord III or IV falls under a third of its health.
+ **Fire Pillar**: The seconds of a Fire Pillar near you, and a title for the last five when it is your slayer.
+ **Clear view near the Demonlord**: Hides all particles and fireballs within ten blocks of an Inferno Demonlord.
+ **Inferno Demonlord background**: Keeps the Inferno Demonlord helpers running.
+ **Voidgloom background**: Keeps the Voidgloom helpers running.
+ **Yang Glyphs**: Marks a Voidgloom's beacon in its hands, in the air with its path, and where it landed with the seconds left.
+ **Nukekubi skulls**: A glow and a name on the skulls a Voidgloom throws, where you can see them.
+ **Hide Enderman particles**: Smoke, flames and witch sparks around Endermen in The End.
+ **Tarantula background**: Knows when a Tarantula cannot be hurt, for the Damage Indicator and the glow.
+ **Mark an invincible Tarantula**: A glow on a Tarantula while its hatchlings keep it from being hurt.
+ **Egg sacs**: Marks a Tarantula's egg sacs during your fight.
+ **Line to your boss**: From your crosshair to your Tarantula or Voidgloom when it is within thirty blocks and in sight.
+ **Riftstalker background**: Keeps the Riftstalker helpers running.
+ **Riftstalker Bloodfiend**: Highlights your boss, titles for Twinclaws, Steak and Mania.
+ **Blood Ichor and Killer Spring**: Marks them near you, a beam on an Ichor from further away, and fixes the Spring's sound spam.

## Dungeons

+ **Dungeon chest tracker**: What the reward chests you open hold at market prices, what they cost, and what one is worth. Kuudra's chests count too.
+ **Secret Tracker spot**: Marks the secret that the Secret Tracker item is pointing to, from its own sparks and chat line.
+ **Who joined your party**: When someone joins your dungeon party: their Cata and class levels, Magical Power, armor, whether they own the key items, and their runs, in chat. It reads other players, so it stays quiet without the Advanced setting.
+ **Party Finder**: Hypixel's Party Finder in the mod's design: every party as a card with faces, classes and levels, search, filters, your class marked, paid carries marked, and level ranges typed right there.
+ **Room detection and secrets**: Recognises the room you are in and marks its secrets in the world (chests, items, bats, levers, wither essence); a secret disappears when you reach it.
+ **Secret routes**: Leads you secret after secret: the path, a jump arc for each etherwarp, the spots to mine with Dungeon Breaker, blow up with Superboom or pull a lever at, and the secret itself; opening, picking up or killing the secret moves to the next step. Set with /sbc route set normal|fow|3ppopka|pearl|fastest, skip with /sbc route next.
    + Says what to do at each spot: Pull lever, Mine (Dungeon Breaker), Superboom, Etherwarp or Pearl, with a lever before its locked chest. - Beta 0.2 ([b4869c9](https://github.com/Skyblock-Companion/SBC/commit/b4869c9))
+ **Room map**: A small map of the floor with every room you have been in, coloured by kind, and where you stand.
    + Hypixel's own map from your last hotbar slot, with your arrow where you stand. - Beta 0.2 ([16fb99f](https://github.com/Skyblock-Companion/SBC/commit/16fb99f))
+ **Floor 7 terminals and devices**: Marks the terminals and devices of the Goldor phase when you are close to them.
+ **Livid finder**: In the floor 5 boss fight, outlines the real Livid among the nine (the wool block's colour names it) with a line to it.
+ **Blaze room**: Outlines the blaze to hit next (green) and the one after (white) with a line between them.
+ **Floor 7 terminal windows**: Marks what to click in the terminal windows: the next three order panes, items starting with the letter, items of the colour, panes to correct, and the clicks to reach the most common colour.
+ **Three Weirdos**: Marks the chest that holds the reward once the right character has spoken; the mark goes when you open it.
+ **Quiz room**: Marks the buttons of the right answer in the quiz room and greys out the wrong answers in chat.
+ **Creeper Beams**: Pairs up the lanterns and prismarine blocks to hit, one colour per pair; finished pairs turn green.
+ **Tic Tac Toe**: Marks the best square to play in the tic tac toe room.
+ **Ice Fill**: Draws the route that fills every ice square of the three boards.
+ **Ice Path**: Draws the shortest chain of slides that gets the silverfish to the exit.
+ **Water board**: Shows which lever to pull next and a countdown on every lever, where the water will run, and what a lever you look at would change.
+ **Boulder room**: Draws a route through the boulders and marks the button at its end.
+ **Teleport maze**: Marks the pads that lead to dead ends in red and the way out in green once it is known.
+ **Simon Says**: Marks the next two buttons of the Simon Says device.
+ **Lights On**: Marks the levers of the Lights On device that are still off.
+ **Arrow Align**: Shows how many clicks every frame of the Arrow Align device still needs.
+ **Target Practice**: Marks the targets you have already hit.
+ **Dungeon score**: Works out your score during the run (rooms, secrets, puzzles, deaths, time, bonuses) and chimes at 270 and 300.
+ **Dungeon copilot**: One line that says what comes next in the run: clear, blood, Watcher, boss, terminals.
+ **Splits, deaths and milestones**: Time at blood open, blood done, each boss phase and completion; deaths and class milestones.
+ **Secret chime**: A chime when you click a chest, lever or wither essence, and when the secret count in the tab list goes up for anything else.
+ **Low health warning**: A warning and a low tone when you drop under 30% health in a dungeon.
+ **Spirit Leap class colours**: In the Spirit Leap menu every head gets its class colour and letter, dead players stand out, and four keys can leap for you when you press them.
+ **Party Finder marks**: In the Party Finder: $ on carries, dimmed parties you cannot join, and the letters of the classes a party is missing.
+ **Dungeon run clock**: Reads the run from chat and the tab list for the copilot, splits, chime and warnings.
+ **Dungeon run reading**: Reads the floor, the boss phase, your class and level and the team, for the other dungeon helpers.
+ **Goldor tasks**: A card for the Goldor phase of Floor 7: every terminal, device, lever and the gate of this section, who is at it, and what is done.
+ **Next door**: Marks the wither or blood door to open next, read from Hypixel's map: red until you hold a key, green after.
+ **Keys on the ground**: Marks wither and blood keys lying around, with their name and how far they are.
+ **Dungeon clocks**: One card with the Fire Freeze moment in the Floor 3 boss, when to kill the blood mobs, and when you can start another run.
+ **Dungeon warnings background**: Watches the run for the warnings below.
+ **Possible Trinity**: A title when a dungeon has five puzzles, which is when the Trinity room can appear.
+ **Rare room alert**: A title and a line in your chat when you find a rare room.
+ **Shadow Assassin jump**: A title and a beep when a Shadow Assassin is about to jump on you.
+ **Spring Boots charged**: A title and a beep in the Floor 7 boss when Spring Boots are charged high enough.
+ **Architect's Draft on a failed puzzle**: When a puzzle fails and you have a draft in your sacks, a title and a line you can click to take one.
+ **Teammate low on health**: A title and a sound when a teammate's health on the sidebar turns red.
+ **Dungeon objects background**: Watches what is lying and moving around for the features below.
+ **Hide things in dungeons**: Leaves things out of the world in a dungeon. Each one has its own switch; nothing is hidden until you turn it on.
+ **Marks on dungeon mobs**: Marks moving skeleton skulls, Deathmites and Fel skulls where you can see them.
+ **Starred mobs**: Starred mobs, minibosses and bats glow.
+ **Class colour on teammates**: Teammates glow in the colour of their class.
+ **Clicked blocks**: Levers, chests and wither essence you click light up for three seconds and say what they were. Hides the lever line in chat.
+ **Clean end**: When the block that ends the run appears in chat: hides all mobs and particles, except the chests and other players. Hypixel's music is not stopped, and until that block everything stays as it is.
+ **Blood camp helper**: Shows where each blood mob will stop once it starts moving, so you can wait there.
+ **Dungeon chat filter**: Hides kinds of lines in dungeons, each with its own switch. Nothing is hidden until you turn it on.
+ **Dungeon Hub races**: Shows the way through the races of the Dungeon Hub while you run one.
+ **Reward chest profit**: Beside a dungeon or Kuudra reward chest: what it holds, what it costs and the profit. At a run in Croesus: every chest, with the best one lit up.
+ **Croesus**: Greys out runs you opened, marks runs with no chest left and runs where a kismet was used, shows your kismets on the Reroll button, and counts what waits at Croesus.
+ **Class levels in the tab list**: Class levels in the tab list get a colour by how high they are. Ranks and emblems stay out of those rows.
+ **Guardian health**: The health of the guardians in the Floor 3 boss, on the guardians themselves.
+ **Salvage helper**: In the Salvage menu, marks dungeon items worth under 100k, which are safe to salvage.
+ **Sellable dungeon items**: With Ophelia, marks the dungeon items in your inventory you can sell to her.
+ **Blessings**: A card with the blessings your team found.
+ **Dungeon background**: Keeps the dungeon helpers without a switch running.
+ **Secrets per player**: At the end of a run, says in your chat how many secrets each player found. Read from their profiles.
+ **Quiver running out in a dungeon**: A title when your quiver runs low in a dungeon, and again when the run is over.

## Crimson Isle

+ **Kuudra helper**: Marks supply and fuel piles, ballista build spots, safe spots and pearl throw spots in Kuudra.
+ **Reputation Helper**: Today's five faction quests with their state and progress, the five daily minibosses and the five Kuudra tiers; white markers at quest spots, minibosses and the Town Board.
+ **Quest tracking**: Reads the Faction Quests tab widget, the Town Board menus, the completion lines, fetch items in your inventory, trophy fish and miniboss kills.
+ **Miniboss respawn timer**: Per area: when the miniboss respawns (2 min after DOWN!), a possible window from the beacon, SPAWNED!, Soon!.
+ **Vanquisher waypoint share**: Announces your own Vanquisher, and your key or a click shares it to the party; shared Vanquishers become 60 s waypoints with a beam; 'Vanquisher dead!' removes them.
+ **Rescue Mission waypoints**: The hostage parkour for your faction and tier (read from the Town Board's Rescue menu) once the Undercover Agent briefs you; navigation to the agent and the board.
+ **NPC sack helpers**: A click-to-grab-from-sacks line when Pablo asks for a flower, Sirih oinks (Barbarian: Sulphur) or Avorius hints (Mage: Cup of Blood).
+ **Volcano explosivity**: The volcano status from the tab list.
+ **Sulphur Skitter box**: A box around the nearest sponge where Sulphur Skitters can be fished.
+ **Heavy pearls**: In the Belly of the Beast, highlights the three heavy pearls and draws a line through them (lowest first).
+ **Kuudra tier detection**: Knows the Kuudra tier from the sidebar and hears KUUDRA DOWN! (feeds the daily Kuudra list).
+ **Dojo tests**: Help in each of the seven tests of the Dojo: which zombies to hit and how long they stay, where the gaps in the walls come, which block to shoot and where to aim.
+ **Dojo rank**: Beside the Challenges menu: your rank and score in each test, your total, your belt and the points to the next one.
+ **Ashfang**: In the Ashfang fight: how long you are frozen and when he pulls his blazes back, the blazes by colour, Blazing Souls and Gravity Orbs marked.
+ **Atom hit boxes**: The hit boxes of Exes, Wais and Zees.
+ **Magma Boss phase**: Which of its six phases the Magma Boss is in, above it.
+ **Clearer fog**: On the Crimson Isle, keeps the fog as far away as it is in the overworld instead of close like in the Nether.
+ **No player menu in Kuudra**: On the Kuudra island a right click on another player does nothing, so their profile menu cannot open during the fight.

## Rift

+ **Enigma souls nearby**: Marks Rift enigma souls near you.
+ **Timite tracker**: Timite, Youngite and Obsolite you collected at the Mountaintop, their worth in Motes and the time they gave.
+ **Rift panel**: Motes orbs, effigies, vermin, wooden buttons, enigma souls, punchcards, Bacte and Gunther's race, in one panel.
+ **Blood Effigies**: In Stillgore Château, marks the six effigies: red = break it, yellow = respawning soon (with the time), grey = unknown.
+ **Enigma Soul waypoints**: Click a soul in Rift Guide &gt; Enigma Souls to track it; the button in the middle of the page tracks every missing soul on it; finding a soul clears it.
+ **Rift Timer**: Your Rift time from the action bar with the maximum, the percentage and the recent changes.
+ **Crux Talisman progress**: The Crux Talisman's kill progress and bonuses, read from the item in your inventory.
+ **Kill-zone warning**: In the Colosseum, a title counting down the seconds you have to get back into the arena.
+ **Vermin tracker**: Vermin vacuumed this session and in total (Vermin Bin + vacuum), in the West Village while you carry the Turbomax Vacuum.
+ **Motes Orbs**: Marks Motes Orbs (found from their particles) until you pick them up.
+ **Punchcard Artifact**: Highlights players you have not punched yet (20 per visit) and counts your punches.
+ **Ubik's Cube reminder**: A reminder when Split or Steal can be played again (2 h after opening it, or the exact cooldown from chat).
+ **Wooden Buttons helper**: While the Buttons enigma soul is tracked, shows the nearest house with buttons left and the buttons themselves.
+ **Parkour helpers**: Gunther's race in the West Village and the Mirrorverse parkours (lava maze, upside-down, Tubulator): the next platforms, lines and shortcuts.
+ **Bacte fight**: In the Colosseum, marks the Blobbercysts and the tentacles on the floor with how many hits each one has left.
+ **Dreadfarm**: Counts how long an Agaricus Cap has grown while you look at it with the Wand of Farming, and shows a Volt's strike range and the time until it strikes.
+ **Wilted Berberis**: With the Wand of Farming in the Dreadfarm, shows where the Wilted Berberis is, and once it has seen a field come back, the order to break it in.
+ **Living Cave**: Defense blocks and the mob they guard, where Living Metal moves to next, and the Living Metal snakes coloured by their state while you hold a pickaxe or the pungi.
+ **Living Metal Suit progress**: How far each piece of the suit has evolved, while you wear Living Metal armor.
+ **Dance Room**: In the Mirrorverse dance room, the step to do now, the next ones and the time left on the step.
+ **Crafting Room**: Shows the mob on the far side of the Mirrorverse crafting room at its mirrored spot on your side, with its name and health.
+ **Mountaintop**: Where to drop for the flower pot soul, how long Timite takes to evolve and when it expires, and a click anywhere to leave Ubik's game once it has ended.
+ **Sun Gecko**: In the Time Chamber, the boss's health, your combo and its time left and the modifiers on; marks the fake bosses.
+ **Splatter hearts**: In the Stillgore Château, marks the hearts a Splatter Crux took from you.
+ **Vermin marks**: Marks the vermin in the West Village while you hold the Turbomax Vacuum.
+ **Wyld Woods**: Marks Larvas while you hold the Larva Hook and Odonatas while you hold an empty bottle, and warns you to look away when a Shy Crux is about to steal your time.
+ **Rift Guide marks**: In the Rift Guide, what you have not done yet stands out in yellow.
+ **Hide Horsezooka horses**: Hides horses while you hold the Horsezooka.
+ **Temporal Pillar dodge**: Navigation in the Rift goes around the big enderman that throws you back to the end credits.
+ **Healing Melon warning**: A title when your health in the Stillgore Château falls to the number of hearts below.
+ **Kloon hacking**: In the West Village: the right letters in the hacking menu, which colour fits the terminal you stand at, and a marker on each terminal left while you wear the visor.
+ **Motes**: Beside Rift Storage, what its items sell for; when you leave, the motes you gained on this visit; and your burger stacks kept right for the motes price on items.

## Events

+ **Century Party Invitation**: With the invitation in hand, players with a level colour you still need get one outline and the others another.
+ **Jerry's Workshop profit**: What Jerry's Workshop brings in at market prices, with Frozen Treasures, Compact finds and the ice they make.
+ **Stray rabbits**: Strays you caught in the Chocolate Factory by rarity, the production time they are worth, and the Golden ones by kind.
+ **Shiny Orb profit**: What Shiny Orbs give in the Year of the Pig, less what the orbs cost.
+ **Gift profit**: What gifts give at market prices, by tier, with North Stars and XP. Say what you used with /sbc addusedgifts white 10.
+ **Diana profit**: What Griffin burrows give at market prices, and what a burrow is worth.
+ **Mythological mobs**: Which mythological mobs you dug up, with their share.
+ **Beach Ball tracker**: The Beach Balls you used and the Fishy Treats they earned.
+ **Catch a Fish**: Outlines the yellow fish at the Carnival's Catch a Fish.
+ **Fishy Treat profit**: Beside Lukas the Aquarist, what each item is worth per Fishy Treat.
+ **Crown of Avarice counter**: While you wear the crown in a Mythological Ritual: its coins, how fast they come and when it is full. /sbc resetavarice starts the session over.
+ **Fruit Bowl**: With the Fruit Bowl in hand: the profile names it still misses and the ones you found twice.
+ **Diana card**: Burrows and chain progress, coins and feathers dug, mythological mobs and treasures.
+ **Carnival fruit digging**: The last hint (fruit, bombs or nothing nearby) and how many spots you dug.
+ **Great Spook**: The quick-maths answer and the Primal Fears you met.
+ **Quick maths solver**: Solves the QUICK MATHS question and shows the answer.
+ **Spook alerts**: Trick-or-Treat and Party chests, the word the Public Speaking Demon wants, and Primal Fear spawns.
+ **Winter gifting**: Gifts opened, coins and North Stars, items and XP from gifts, unique gifts given, Jyrre time left.
+ **Beach Ball bounces**: Best bounces and Fishy Treats from the Bouncy Beach Ball.
+ **Bingo Card**: On a Bingo profile, your goals on the HUD, with the community percentages and when the next hint comes. In the card's menu, each goal's difficulty and guide. Open the menu once to fill it.
+ **Minion crafts**: On a Bingo profile, how much more you need for the next tier of the minions in your inventory, and a title when you can craft one.
+ **Carnival goals**: At the Carnival, the goals you have left this year, by game. Open the Carnival Goals menu once to read the ones you already have.
+ **Carnival Ticket reminder**: While the Carnival is on, a click in chat to go there when your daily tickets are ready.
+ **Start Carnival games with a second click**: Click a game's NPC again after it has finished talking to start the game. This answers the NPC for you when you click.
+ **Zombie Shootout**: A coloured box on each zombie by its worth, with how long it stays; the lit lamp boxed with a line to it, and how long it stays lit.
+ **Event shop helper**: Beside the Carnival's event shops, what you have left to buy and how many tokens that takes.
+ **Frozen Treasure highlight**: In the Glacial Cave, the block of ice each Frozen Treasure is in.
+ **Workshop closing time**: On Jerry's Workshop, how long until it closes.
+ **New Year Cake reminder**: While the New Year Event is on, a click in chat to get your free cake, until you have it.
+ **Reindrake warp**: When someone summons a Reindrake on Jerry's Workshop, a click in chat to warp to the island's spawn.
+ **Your Jerries**: While Jerry's Jerrypocalypse is on, a glow on your Jerries where you can see them, in their colour. A line to them can be added.
+ **Jerry cooldown**: While Jerry's Jerrypocalypse is on, how long until a hidden Jerry can come again, with a message and a sound when it can.
+ **Unique gift counter**: While you hold a gift in December, how many different players you have given one to. Open Generow's menu to set it.
+ **Players you have not gifted**: In December, a glow on players you have not given a gift to, where you can see them.
+ **Clean gifts**: Hides the From and To lines over gifts in the world, leaving only the line to open them.
+ **Great Spook timers**: While the Great Spook is on, the time to the next Primal Fear, your Fear, and how long the event has left. For Fear, have the Stats widget with Fear on in your tab list.
+ **Century cake teams**: At the Century Celebration, with a slice of cake in hand, a glow on the players of that cake's team where you can see them, and a grey one on the rest.
+ **Cake Counter**: With a Cake Counter on your Private Island: a message when someone finds a Cake Soul while you are there, and if you want what happened while you were away.
+ **Bingo: party on boop**: On a Bingo profile, when someone boops you, a click in chat to invite them to your party.
+ **Fruit Bowl: who to click**: While you hold a Fruit Bowl, players you have not clicked yet get a green outline and the others a grey one.
+ **Griffin burrow helper**: Guesses the next burrow from the Ancestral Spade's particles and the chain arrow, shows every burrow near you with its type (start, mob, treasure) from Hypixel's particles, removes them when dug, and draws a line to the nearest one.
+ **Nearest burrow warp**: Suggests a Hub warp (Castle, Crypt, DA, Museum, Wizard, Stonks...) when it is closer to the next burrow than you are; the warp key sends you there.
+ **Rare Diana mob sharing**: The share key sends the Minos Inquisitor, Sphinx, King Minos or Manticore you dug out to party chat; party members' coordinates become waypoints for 75 seconds; rare mobs get an outline.
+ **Griffin pet warning**: A reminder when you hold the spade without a Griffin pet.
+ **Mute bugged spade music**: Hypixel's spade sometimes plays music sounds at you; they are muted while the real music stays.
+ **Hoppity's Hunt card**: Unclaimed meal eggs with their respawn timers and the collected-locations count; eggs and rabbits found this session; a warning (chat, title, sound) when all eggs are ready; the next egg time in chat.
+ **Egg waypoints**: The Egglocator's particle curve is followed to the nearest known egg spot; eggs other players share in chat; optionally every known spot of the island; duplicate spots marked; Mythic Rabbit pet reminder; Rabbit the Fish highlight.
+ **Hoppity call warning**: A repeating sound, a title and the screen flashing while Hoppity rings your Abiphone, until you pick up.
+ **Chocolate Factory**: Reads the factory menu: chocolate (live), per second, multiplier, barn, leaderboard, time tower, prestige and the best upgrade (highlighted; stack sizes = level). Barn-full and Time Tower warnings; Hoppity's Collection highlights and summary.
+ **Hoppity's shop**: In Hoppity's shop, what you have not bought yet is green; during the hunt, a reminder to visit it once a year.
+ **Chocolate upgrade reminder**: A chat line with a link to /cf when you can pay for the best upgrade of the Chocolate Factory.
+ **Chocolate factory goal**: Click something in the factory you cannot pay for yet: a card counts down until you can, and chat says when. Click it again to clear it.
+ **Chocolate factory keys**: In the Chocolate Factory, the number row upgrades the seven rabbits, one click to a press.
+ **Block /cf without buffs**: Keeps the Chocolate Factory from opening while what you want active is not, and says why.
+ **Move Chocolate Factory tooltips**: In the Chocolate Factory, the tooltip of what you hover stays at the side of the screen and does not cover the menu.
+ **Egg spots in Fast Travel**: During the hunt, on Hypixel's Fast Travel menu: how many egg spots of each island you collected.
+ **Meal egg sounds**: The note block sounds when you open a Chocolate meal egg: gone, or the old eating sound.
+ **Chocolate Factory in the SkyBlock Menu**: A slot of the SkyBlock Menu opens the Chocolate Factory when you click it.
+ **Stray rabbit warning**: A sound and a colour on the slot for a stray in the Chocolate Factory, and the screen flashing for the kinds you choose.
+ **Stray timer**: After a meal egg: 30 seconds in the Chocolate Factory during which strays may come.
+ **Compact Hoppity chat**: The three lines of a rabbit find become one, with the original lines on hover and the egg's share link on click. Big Hitman claims become one summary.
+ **Hoppity's Hunt summary**: When the hunt is over: meal eggs found of those that spawned, rabbits by rarity, chocolate, time in the factory and your leaderboard change. /sbc hoppitystats shows it for any year.
+ **Hoppity's Hunt stats**: This hunt's numbers on a card as they come in: meal eggs of those that spawned, rabbits by rarity as unique, duplicate and stray, chocolate, time in the factory.
+ **Missing rabbits**: Beside Hoppity's Collection: per island how many resident and hotspot rabbits you have, and what the location rabbits still need. Turn the pages once to read them all.
+ **Chocolate Shop prices**: Beside the Chocolate Shop: coins per million chocolate for everything in it, best first, with when you can afford each and the chocolate you spent.
+ **Hitman slots**: Beside Rabbit Hitman: what your slots cost and what the rest would, how many are open, when they are full.
+ **Season-locked places**: When Hypixel says something is only open in another season, how long until that season.
+ **Stonks Auction**: While the Stock Exchange perk runs: a card with the time left in the round and your bid, a line when a new round starts and a warning in its last minute. Open the Stonks Auction once for the exact time.
+ **Shiny Orb lines**: In the Year of the Pig: a line from you to the Shiny Pig that carries your orb, and from it to the orb.
+ **Century cake team colours**: In the Century Celebration, holding a Slice of Cake: players of that team glow in its colour, the others grey.
+ **Raffle tasks**: In the Century Celebration's Raffle Tasks menu, what is not done yet is red.
+ **Great Spook answers in chat**: A chat line with the answer to Quick Maths or for the Public Speaking Demon. Clicking it sends it to all chat; nothing goes out by itself.
+ **Perkpocalypse mayor reminder**: While Jerry is mayor: a line to open the calendar when the temporary mayor is not known or has run out.
+ **Calendar reminders**: A toast and a sound shortly before a SkyBlock event starts. Which events and how early are below.
+ **City Project**: In a City Project's menu: what you can contribute now is yellow, what all the components still need stands beside it with its price, and a reminder comes when you may contribute again.
+ **Year of the Witch**: Beside Agnes's shop and Alcina's stews: what each thing asks for against what you have in your inventory and sacks, which mob type drops every ingredient and where to find those mobs. Eaten stews are green, ones you can make now yellow.
+ **Witch ingredients**: During the Year of the Witch, a card with the ingredients in your sacks and what your kills added this session, so you don't have to open the Witch's Sack.
+ **Beach Ball helper**: A line where your Bouncy Beach Ball has been and will go, its bounces, and a box at your height that goes green when you stand under it. Not for the giant ball.
+ **Stock of Stonks price**: In the Stonks Auction, what you pay per Stock at each rank; the best one is green.

## Pets

+ **Pet helper**: Beside Hypixel's Pets menu, on the other side from the Item List: your Pet Score, the cheapest way to the next Magic Find, which pets to level, and what you are missing. The menu itself stays as it is.
+ **Highlight current pet**: Marks the pet you have out in the Pets menu.
+ **Pet Candy used**: How many Pet Candies went into a pet, on its slot.
+ **Pet item icons**: A small icon on pets holding an Exp Share or a Tier Boost.
+ **Pet progress to the top level**: On a pet's tooltip while holding Shift: how far it is to level 100 or 200, whatever its rarity.
+ **Pet name tags**: Takes the level off the name above pets.
+ **Pet details**: What the Equipped pet plate has no room for: total XP, XP past the top level, the held item and your Exp Share pets. Numbers as last seen in the Pets and Exp Sharing menus.
+ **Taming 60 helper**: Beside George's Offer Pets menu: the pets his egg asks for, the cheapest of each and what all of them cost. Click a pet to search for it.

## Minions

+ **Inferno Minion profit**: What you collect from Inferno Minions at market prices, less the fuel and eyedrops.
+ **Minion names**: Over your minions when you stand close: name and tier, and how long ago the hopper was emptied.
+ **Last clicked minion**: Marks the minion you last opened for a little while.
+ **Minion panel**: Beside a minion's menu: coins a day from its hopper, and where the next upgrade's items come from, with a click to the Bazaar or your sacks.
+ **XP from a minion**: On the items in a minion and on Collect All: the skill XP you get for taking them.
+ **Hide mob names near minions**: Mob name tags within 5 blocks of your minions.
+ **Inferno fuel blocker**: Stops you taking out the fuel of an Inferno Minion, or picking it up, while it has fuel. Ctrl lets it through.
+ **Minion chat helpers**: Coins collected, upgrade notes, eyedrops warning and missing upgrade items.

## Skills

+ **Experiments profit**: What the Experimentation Table gives you at market prices, its Enchanting XP, what the bottles and charges cost, and how many rewards you claimed. Shows near the table.
+ **Maxed skills in gold**: In Your Skills, a maxed skill's slot is gold. - Beta 0.2 ([13f4d56](https://github.com/Skyblock-Companion/SBC/commit/13f4d56))
+ **Skill ETA**: The skill you are levelling: how long to the next level or your goal, XP per hour, and how long this session has run. All three pause when XP stops coming in. The skill progress bar shows the same, so this is off unless you want the card.
+ **All skills**: Every skill with its level and its XP out of what the next level needs. Hide a skill in the HUD editor.
+ **Skill goals**: A level you are working towards, set with /sbc skills goal: the bar and the cards count to it instead of to the next level.
+ **Levels past the cap**: A maxed skill keeps counting from what its XP would be worth, as the levels Hypixel stops showing.
+ **Experiment solvers**: Chronomatron and Ultrasequencer: the next click stands out and a wrong click is refused. Superpairs: every card you have seen stays visible, pairs stand out.
+ **Experiments dry streak**: Attempts and Enchanting XP since your last ultra-rare, beside the table's menus. /sbc resetdrystreak starts it over.

## Tracking

+ **Items gained**: Counts the items you pick up, with their coin value, per session, day and week.
+ **Tracker engine**: Reads what you gain from your inventory and sacks for every tracker.
+ **Profit**: What you earn doing anything without a tracker of its own: coins from kills and items, at NPC and Bazaar or Auction House prices, in total and per hour. Shows while you earn at least the amount below in 10 minutes, and folds away when you are idle.
+ **Tracker settings**: What every profit tracker shows and how it counts.

## Bar parts

The pieces of the health, mana and skill bars.

+ **Health numbers**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Mana numbers**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Health bar**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Mana bar**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Defense number**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Effective HP**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Less damage**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Defense bar**: A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Skill name and level**: A piece of the skill bar: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Skill last gain and XP an hour**: A piece of the skill bar: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Skill percent**: A piece of the skill bar: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Skill bar**: A piece of the skill bar: switch it off, or drag it anywhere on its own in the HUD editor.
+ **Skill XP numbers**: A piece of the skill bar: switch it off, or drag it anywhere on its own in the HUD editor.

## Misc

+ **NPC sell limit**: What you sold to NPCs today against the 500M limit, which starts over at midnight GMT. Shows once you sold a part of it.
+ **Area helpers**: Keeps what the fishing, foraging and hunting helpers know about the things around you up to date.
+ **Bits messages**: Tells you when a cookie's bits have run out, with a line to click for the Bazaar, and when you gain bits.
+ **Community Shop upgrade done**: A line to click when your profile or account upgrade has finished, every half a minute until you claim it, but not while you are busy.
+ **Stranded: placeable NPCs**: In Island NPCs: those you can place but have not.
+ **Stranded: in water**: Says whether you are in water.
+ **Back on a server**: A chat line when you land on a server you were on a moment ago.
+ **Kicked from SkyBlock**: In the Hypixel lobby: how long ago you were kicked from SkyBlock, and a title with a sound when it is time to try again.
+ **Server restart title**: A title with the time left when your server is about to restart: every 30 seconds, then counting down in the last two minutes.
+ **Copy your playtime**: In Detailed /playtime: click the summary to copy it.
+ **Abiphone hotkey**: A key that picks up a ringing Abiphone. It has no key until you give it one in Controls, and it only answers when you press it.
+ **Fix double clicks**: Hypixel counts a right click on a block twice while you hold a fishing rod or a blaze dagger; this leaves out the extra half.
+ **Fix coloured particles**: Hypixel sets some particles' colours the old way, which shows wrong: slayer spawn particles and motes among them. It can break places where the colour is already right.
+ **Computer clock warning**: A chat line when your computer's clock is off by more than five seconds, which throws off timers.
+ **Time in Limbo**: How long you have been in Limbo, your longest stay, and your Limbo time in Detailed /playtime.
+ **Enchanted Clock reminder**: A chat line and a sound when a boost of your Enchanted Time Clock is ready again. Open the clock once so it knows the times.
+ **Other potion effects**: The effects that are not part of the God Potion, each with its time, and a warning before one runs out. Open /effects once to get them all.
+ **Session panel**: Server restart countdown, NPC coins sold today, minion coins, sacrifices, fairy souls and relics found.
+ **Piggy bank alert**: A loud alert when your piggy bank cracks or breaks.
+ **Coordinates become waypoints**: A chat message with coordinates gets a green [+ waypoint] you can click.
+ **NPC sell limit warning**: A warning when you sold 90% of the daily 500M NPC limit, which starts over at midnight GMT.
+ **Potion effects**: God Potion time and how many effects are active, from the tab list.
+ **Game data**: Keeps the mod's lists of game facts current without a new version of the mod. What it fetches is used from the next start.
+ **Alerts**: Pop-up alerts for resets, events and timers.
+ **In-game browser**: Wiki and chat links open inside the game instead of your desktop browser.
+ **Planner**: How the planner weighs money against levels and how long a session is.
