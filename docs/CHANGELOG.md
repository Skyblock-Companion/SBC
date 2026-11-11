# SkyBlock Companion - changelog

## [Beta 0.2](https://github.com/Skyblock-Companion/SBC/releases/tag/beta-0.2)

### New Features

#### Items

+ Added places to the Item List: NPCs, bosses, mobs, places and warps, in a new World tab and in the search of All. - [473f158](https://github.com/Skyblock-Companion/SBC/commit/473f158)
    + Each one opens its own page, like an item: where it is and how far, the wiki's first lines about it, Navigate, its warp and the wiki.
    + Right-click in the list guides you straight there with the path line.
+ Added every Hunting shard to the Item List, with prices, search and their own item page. - [473f158](https://github.com/Skyblock-Companion/SBC/commit/473f158)
+ Added what an item really sold for to item tooltips: the median price of the last 3 days and how many sell a day, under the lowest BIN. - [0265b9d](https://github.com/Skyblock-Companion/SBC/commit/0265b9d)
    + Items with fewer than 3 sales show nothing, and the lines wait until there is a full day of sales.

#### Garden

+ Added Analyzed mutations: a greenhouse mutation's tooltip says whether you have analyzed it. - [e6a6b3e](https://github.com/Skyblock-Companion/SBC/commit/e6a6b3e), [a931149](https://github.com/Skyblock-Companion/SBC/commit/a931149), [0a3cc2f](https://github.com/Skyblock-Companion/SBC/commit/0a3cc2f)

#### Menus

+ Added the Sack Helper beside the Sack of Sacks. - [43a8da3](https://github.com/Skyblock-Companion/SBC/commit/43a8da3)
    + Every kind of sack is one family of sizes.
    + Missing lists what is left, cheapest first. Owned shows what you have and what it costs to finish.
    + Your sacks come from your profile and from every sack you open in the menu.

#### General

+ Added a What's new page after an update: everything since the version you played before, newest first. - [fa86fd3](https://github.com/Skyblock-Companion/SBC/commit/fa86fd3)
    + It shows the whole changelog of every version you skipped. - [cfd7f6c](https://github.com/Skyblock-Companion/SBC/commit/cfd7f6c)
    + New helpers stay off until you switch them on there. Nothing is turned on for you.
    + `/sbc whatsnew` opens it again.
+ Added a fast and a detailed setup. The fast one asks a few questions; the detailed one then shows every helper of what you play. - [db863c2](https://github.com/Skyblock-Companion/SBC/commit/db863c2)
    + Nothing is answered for you: every question starts open.
    + Each helper's options and an example of its card are on the detailed pages. - [fa86fd3](https://github.com/Skyblock-Companion/SBC/commit/fa86fd3)
+ Added support for Minecraft 26.3. - [6aabe43](https://github.com/Skyblock-Companion/SBC/commit/6aabe43), [8b18d46](https://github.com/Skyblock-Companion/SBC/commit/8b18d46)
+ Added CurseForge to the update screen and Mod Menu, next to Modrinth and GitHub. - [5147423](https://github.com/Skyblock-Companion/SBC/commit/5147423)

### Improvements

#### Dungeons

+ The dungeon map is now Hypixel's own map from your last hotbar slot, pixel for pixel, in the card's frame. Your teammates show as Hypixel draws them. - [16fb99f](https://github.com/Skyblock-Companion/SBC/commit/16fb99f)
    + You are an arrow at where you really stand, turning with your view, so it moves smoothly instead of waiting for the next map update.
    + Every player Hypixel draws on the map shows on the card. - [db71045](https://github.com/Skyblock-Companion/SBC/commit/db71045)
+ The pet card follows summons, Autopet and despawns from chat, so it also knows your pet in dungeons, where the tab list has none. - [ed1f057](https://github.com/Skyblock-Companion/SBC/commit/ed1f057)
+ The Three Weirdos solver finds the right chest by the speaker's name, without waiting for the room to be matched. - [7cd5a54](https://github.com/Skyblock-Companion/SBC/commit/7cd5a54)
+ Secret routes say what to do at each spot: Pull lever, Mine (Dungeon Breaker), Superboom, Etherwarp or Pearl. - [b4869c9](https://github.com/Skyblock-Companion/SBC/commit/b4869c9), [fdf10a0](https://github.com/Skyblock-Companion/SBC/commit/fdf10a0), [cda7c28](https://github.com/Skyblock-Companion/SBC/commit/cda7c28)
    + A lever comes before its locked chest, an etherwarp is drawn as a jump, and a step moves on only when its secret is taken.
    + A chest that opens with a lever: the route goes to that lever first, from the room's data, and a locked chest keeps the step until it is pulled.
    + The normal route is the default.

#### HUD

+ The player card takes your SkyBlock level from the tab list first, so a level up shows right away. - [7933f67](https://github.com/Skyblock-Companion/SBC/commit/7933f67)
+ The player card keeps the top left corner at its own size. The other cards fit around it instead of moving it. - [dee8c3a](https://github.com/Skyblock-Companion/SBC/commit/dee8c3a)
+ Networth keeps up on the player card and the profile page: your profile loads again every 5 minutes, and the card uses your purse and bank as they are now. - [cd7116c](https://github.com/Skyblock-Companion/SBC/commit/cd7116c)
+ Your profile page shows each skill as the game shows it when Hypixel's API is still behind. - [c8df3e4](https://github.com/Skyblock-Companion/SBC/commit/c8df3e4)
+ A maxed skill gets a gold card on the profile page. Levels past the cap are off unless you switch them on. - [bfac13a](https://github.com/Skyblock-Companion/SBC/commit/bfac13a)
+ A maxed skill is gold in Your Skills too. - [13f4d56](https://github.com/Skyblock-Companion/SBC/commit/13f4d56)
+ The vanilla XP bar and your level stay on screen. Hiding them is an option of the skill card. - [d0d3ee4](https://github.com/Skyblock-Companion/SBC/commit/d0d3ee4)

#### Menus

+ The Buy Helper starts folded, like the Item List, and opens by itself when its list gets something to buy. - [9e8bcfa](https://github.com/Skyblock-Companion/SBC/commit/9e8bcfa)
+ Party Finder, the pets screen and the browser open at the K menu's size instead of covering the whole screen. - [63a617f](https://github.com/Skyblock-Companion/SBC/commit/63a617f)
+ Switching loadout shows its equipment in the inventory at once. - [4a38056](https://github.com/Skyblock-Companion/SBC/commit/4a38056)

#### Items

+ Protected items move freely in your own inventory and storage again: shift-click and number keys work. - [5d5de76](https://github.com/Skyblock-Companion/SBC/commit/5d5de76)
    + They still can never be dropped, salvaged, traded, auctioned, sold to an NPC or swapped in a dungeon chest.
    + Locked slots stay fully locked.

#### Combat

+ Armor stacks on the Combat session card are an option, off unless you switch it on. - [1671484](https://github.com/Skyblock-Companion/SBC/commit/1671484)

#### Events

+ The Year of the Witch helper opens beside the Witch's Sack on any island. Hover an ingredient to see which mob drops it. - [219abef](https://github.com/Skyblock-Companion/SBC/commit/219abef)
+ The Witch ingredients card counts this session only: the + counts start over each time you log in. - [1e1739f](https://github.com/Skyblock-Companion/SBC/commit/1e1739f)

#### General

+ A `/sbc perf record` report starts with where it was made: the island, the area, the players in the tab list and where you stood. - [b5d2532](https://github.com/Skyblock-Companion/SBC/commit/b5d2532)
+ Fabric API is no longer packed inside the mod: install it next to it, like with most Fabric mods. The download is about 2 MB smaller, and Fabric API updates on its own. - [ec36ead](https://github.com/Skyblock-Companion/SBC/commit/ec36ead)
+ Nothing of a mob shows through walls: every glow is only on mobs and players you can see, and boxes and lines on mobs stay behind blocks. - [3ae16b6](https://github.com/Skyblock-Companion/SBC/commit/3ae16b6)
    + Places keep their marks: Voidgloom beacons, dungeon doors, the Phantomleaf, wormholes, the minion you opened.
+ The same words everywhere: the Warp menu, and the HUD editor in the setup and the key tutorial. - [6176b9d](https://github.com/Skyblock-Companion/SBC/commit/6176b9d)

### Fixes

+ Fixed item icons for items newer than the mod's data: they use the textures Hypixel itself gives each item, in the Buy Helper, the Bazaar Helper and everywhere else. - [4244b93](https://github.com/Skyblock-Companion/SBC/commit/4244b93)
+ Fixed the Kuudra tier and the KUUDRA DOWN! counter with Hypixel's new location icon. The Rift time line too. - [4f3f00b](https://github.com/Skyblock-Companion/SBC/commit/4f3f00b)
+ Fixed right-clicking the Search button in the Bazaar and the Auction House: it clears your search again, like Hypixel intends. - [5b5de9e](https://github.com/Skyblock-Companion/SBC/commit/5b5de9e)
+ Fixed `/sbc perf record` showing a number from before the recording: it says when HUD as a picture is off. - [86419f0](https://github.com/Skyblock-Companion/SBC/commit/86419f0)
+ Fixed dungeon levers, chests and skulls that could not be used while "Fix double clicks" was on and you held a fishing rod or a blaze dagger. - [fdb881f](https://github.com/Skyblock-Companion/SBC/commit/fdb881f)
+ Fixed trackers counting both the items you picked up and the enchanted item a compactor made from them, which nearly doubled the profit, also when they went through your sacks. - [2777d4c](https://github.com/Skyblock-Companion/SBC/commit/2777d4c), [ec107e2](https://github.com/Skyblock-Companion/SBC/commit/ec107e2)
+ Fixed Garden visitors falling off the list when the tab list showed only some of them, which made the Buy Helper flicker and lose items. - [46ba2fd](https://github.com/Skyblock-Companion/SBC/commit/46ba2fd)
+ Fixed the Buy Helper keeping items you already bought, and dropping a visitor after you sold your sacks. - [4740535](https://github.com/Skyblock-Companion/SBC/commit/4740535)
+ Fixed the slayer profit tracker counting coins and drops while you did something else, like mining. - [3684370](https://github.com/Skyblock-Companion/SBC/commit/3684370), [17aaa0c](https://github.com/Skyblock-Companion/SBC/commit/17aaa0c)
+ Fixed the contest line on the scoreboard showing a medal you already hold as the next one. - [ac232a7](https://github.com/Skyblock-Companion/SBC/commit/ac232a7)
+ Fixed the Museum helper listing donated items with another id, or the lower tiers of what you donated, as not donated. - [f5d7195](https://github.com/Skyblock-Companion/SBC/commit/f5d7195)
+ Fixed scrolling a long tooltip stopping before its end. - [60bac76](https://github.com/Skyblock-Companion/SBC/commit/60bac76)
+ Fixed the HUD losing its card order and sizes until the window size changed. - [230d816](https://github.com/Skyblock-Companion/SBC/commit/230d816)
+ Fixed the Minions page missing minions: ones you opened, newer types and minions with a skin are listed, per profile. - [4ac2a5e](https://github.com/Skyblock-Companion/SBC/commit/4ac2a5e)
+ Fixed the end of the player card's values in the K menu being cut off. - [cae1ffb](https://github.com/Skyblock-Companion/SBC/commit/cae1ffb)
+ Fixed tooltips with no price for items a menu shows only by name, like the Experimentation Table's rewards. - [290684d](https://github.com/Skyblock-Companion/SBC/commit/290684d)
+ Fixed the Item List showing a plain text tooltip: an item there has its own tooltip with the mod's lines, like in an inventory. - [5d56caa](https://github.com/Skyblock-Companion/SBC/commit/5d56caa)
+ Fixed event times that could show another day's events right after the calendar looked at a different day. - [e3b9388](https://github.com/Skyblock-Companion/SBC/commit/e3b9388)

### Removed Features

+ Removed the through-walls options for mobs and the route line, and the setup question about the route line. - [3ae16b6](https://github.com/Skyblock-Companion/SBC/commit/3ae16b6)

## [Beta 0.1](https://github.com/Skyblock-Companion/SBC/releases/tag/beta-0.1)

The first public beta: tooltips and prices, a warp menu, a minimap, 30+ profit trackers, Garden, Mining, Crimson, Rift, Dungeon and Slayer helpers, a profile viewer and more, in one look and one settings menu.
