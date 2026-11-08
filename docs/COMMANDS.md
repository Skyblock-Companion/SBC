# SkyBlock Companion - commands

Every `/sbc <word>` also works as one word: `/sbc nav` is `/sbcnav`, `/sbc wp add` is `/sbcwp add`. `/sbc help` lists them in game, and `/sbc help <word>` searches them.

`<name>` is something you fill in, `[name]` can be left out.

## Screens

- `/sbc` - Opens the menu, same as **K**.
- `/sbc settings`, `/sbc config` - Open the settings.
- `/sbc hudedit` - Opens the HUD editor, same as **H**.
- `/sbc storage` - Opens the storage overview.
- `/sbc pets` - Opens the pets screen.
- `/sbc waypoints`, `/sbc wp` - Open the waypoints screen.
- `/sbc rules` - Opens your chat rules.
- `/sbc shortcuts` - Opens the shortcuts editor. `/sbc shortcuts list` lists them in chat.
- `/sbc todos` - Opens the todos editor.
- `/sbc custom` - Opens the custom item looks screen.
- `/sbc credits` - Opens the credits.
- `/sbc setup` - Opens the first start setup.
- `/sbc tutorial` - Opens the key tutorial.
- `/sbc discord` - Prints a clickable link to the Discord server.
- `/sbc help [word]` - Lists every `/sbc` command, or the ones matching the word.

## Profiles

- `/sbc pv [player]`, `/pv [player]` - Open the profile page, for you or for that player.
- `/nw [player]`, `/networth [player]` - Print a networth breakdown in your chat.
- `/cata [player]` - Prints Catacombs level, secrets, class levels and runs per floor in your chat.
- `/sbc profile` - Loads your own profile again from the server.
- `/sbc museum resync` - Reads your Museum donations again, at most once every 10 minutes.

## Finding your way

- `/sbc nav <place>`, `/sbnav <place>` - A walking line to an NPC, warp, place or waypoint. Also `nearest <kind>`, `<name> @ <island>`, and `stop`.
- `/sbc stop` - Stops the walking line.
- `/sbc navall <kind>` - Leads you to every spot of that kind on the island, nearest first (fairy_soul, hoppity, spider_relic, rift_effigy and more). `skip`, `undo` and `stop` control it.
- `/sbc navclipboard [optimized|ordered]` - Leads you through the `x:y:z` lines in your clipboard. `ordered` keeps your order, the others go nearest first. `skip`, `undo` and `stop` control it.
- `/sbc wp add <name>` - Saves a waypoint where you stand.
- `/sbc wp at <x> <y> <z> <name>` - Saves a waypoint at those coordinates.
- `/sbc wp go <name>` - Walking line to that waypoint.
- `/sbc wp list` - Lists your waypoints.
- `/sbc wp remove <name>` - Removes every waypoint with that name.
- `/sbc wp clear` - Deletes all of your own waypoints. No confirm.
- `/sbc wp share` - Copies your waypoints to the clipboard as text.
- `/sbc wp import <text>` - Adds waypoints from that text.
- `/sbc wp next`, `/sbc wp previous`, `/sbc wp first` - Step through an ordered waypoint group.
- `/sbc ordered load [name]`, `save <name>`, `erase <name>`, `export`, `unload` - Load, save, delete, copy or clear an ordered route.
- `/sbc ordered add <number>`, `delete <number>` - Add the block under you at that place in the route, or remove a waypoint.
- `/sbc ordered skip [amount]`, `unskip [amount]`, `skipto <number>` - Move along the route.
- `/sbc ordered setupmode` - Turns setup mode on or off.
- `/sbc soulsfoundall`, `/sbc soulsreset` - Mark every Fairy Soul on this island as found, or as not found.
- `/sbc relics foundall`, `/sbc relics reset` - Mark every Spider's Den relic as found, or as not found.
- `/sbc found reset` - Forgets every secret and collectible you found. No confirm.

## Trackers and timers

- `/sbc resetprofit` - Starts every profit tracker over for this session. Day, week and all-time totals stay.
- `/sbc resetprofit <tracker>` - Clears every total of that tracker, all-time too. No confirm.
- `/sbc addusedgifts <white|green|red|party> [amount]` - Adds used gifts and their cost to the gift tracker.
- `/sbc carry <player> <type> <amount>` - Adds a slayer carry for that player (rev5, sven4, eman3, blaze2...).
- `/sbc carry <type> <price>` - Sets the price of a carry type.
- `/sbc carry party <player>` - Sends that player's carry status to party chat.
- `/sbc carry remove <player>` - Removes that player from the carry tracker.
- `/sbc remind <time> <text>` - A reminder after that time (1h30m, 10m, 2d, or h:m:s).
- `/sbc remind`, `/sbc remind list` - Lists your reminders with remove, edit and move buttons.
- `/sbc remind edit <id> <text>`, `move <id> <time>`, `remove <id>` - Change, move or delete a reminder.
- `/sbc downloadtodo [name]` - Lists the community todos, or adds one to yours.
- `/sbc hoppitystats [year|all]` - Prints a Hoppity's Hunt summary in your chat.
- `/sbc slayers <revenant|tarantula|sven|voidgloom|demonlord|vampire>` - Your best kill time of that slayer.
- `/sbc skills goal [skill] [level]` - Lists your skill goals, sets one, or clears it with no level.
- `/sbc skills levelwithxp <xp>` - The level that much XP gives.
- `/sbc skills xpforlevel <level>` - The XP that level needs.

## Garden

- `/sbc calccrop <crop> [from] [to]` - Crops needed for your next milestone, or between two tiers.
- `/sbc calccroptime <crop> [from] [to]` - The same, as farming time at your measured speed.
- `/sbc croptime <amount> <item>` - Farming time for that many crops.
- `/sbc cropsin <time> <item>` - How many crops you farm in that time.
- `/sbc gardenuptime [days]` - Your farming time per day, for 7 days or up to 30.
- `/sbc cropstartlocation` - Saves where you stand as the start of the farm for the tool you hold.
- `/sbc sethoelevel <level>` - Sets the overflow level of the farming tool you hold.
- `/sbc tpinfested` - Teleports to the nearest plot with pests.
- `/sbc lanedetection` - Turns farming lane detection on or off.
- `/sbc mouselock`, `/sbc sensreduce` - Lock the mouse, or lower its sensitivity, until you change island.
- `/sbc greenhouse paste [preset]` - Shows the Greenhouse layout from your clipboard, or a saved one, as a guide.
- `/sbc greenhouse save <name>`, `rotate left`, `rotate right`, `mirror`, `end` - Save, turn, mirror or remove the layout.

## Mining

- `/sbc blockstrength [speed]` - How many ticks each ore takes at your Mining Speed.
- `/sbc crystalwaypoints add [x y z place]` - Adds a Crystal Hollows waypoint, by default where you stand.
- `/sbc crystalwaypoints remove [place]` - Removes one, or lists them to click.
- `/sbc crystalwaypoints share [place]` - Sends a place's coordinates to all chat.
- `/sbc sharemineshaft` - Sends the mineshaft type to party chat.
- `/sbc sharecorpse` - Sends the nearest corpse's coordinates to party chat, or all chat outside a party.

## Dungeons

- `/sbc route next`, `prev`, `reset` - Step through the secret route of the room you are in.
- `/sbc route set <fastest|fow|3ppopka|pearl|normal>` - Chooses the secret route set.
- `/sbc waterreset` - Solves the water board again from where it is now.
- `/sbc dungeonsound <chime|lowhp>` - Plays that dungeon sound so you can hear it.

## Events and islands

- `/sbc sharerare` - Sends your rare Diana mob's coordinates to party chat.
- `/sbc sharevanquisher` - Sends your Vanquisher's coordinates to party chat.
- `/sbc shareegg` - Sends the Chocolate Egg you found to all chat.
- `/sbc rescueboard` - Walking line to your faction's Quest Board on the Crimson Isle.
- `/sbc enigma markallfound`, `markallmissing` - Mark every Rift Enigma Soul as found, or as missing.
- `/sbc enigma markclosestfound`, `markclosestmissing` - The same for the soul within 4 blocks.
- `/sbc bingotoggle` - Switches the Bingo Card between card, next steps and hidden.
- `/sbc bingoupdate` - Loads this month's Bingo times again.
- `/sbc bingoreload` - Forgets the Bingo goals, open the card to read them again.
- `/sbc cleartalbotcircles` - Removes the Talbot's Theodolite rings.
- `/sbc fixminions` - On your island, forgets minion spots within 30 blocks that have no minion.

## Items

- `/sbc protectitem` - Protects the item you hold, or stops protecting it.
- `/sbc preventsell` - Stops the item you hold from being sold, or allows it again.
- `/sbc wiki [search]` - Opens the wiki page of the item you hold, or a wiki search.
- `/sbc wikithis` - Opens the wiki page of the item you hold.
- `/sbc refreshprices` - Loads Bazaar and Auction House prices again now.
- `/sbc importshards` - Follows the shard recipe in your clipboard, in place of the current list.
- `/sbc supercraftpreset [number]` - Shows the super craft amount buttons, or adds or removes one.
- `/sbc coopmembers add <name>`, `remove <name>` - Hide a former co-op member from collection tooltips, or show them again.
- `/sbc dye <head|chest|legs|feet|all> <RRGGBB|off>` - Colours the armor you wear, only for you.
- `/sbc custom dye <hex|dye name|off>` - Dyes the item you hold, only for you.
- `/sbc custom animatedDye [hex1 hex2 duration cycleBack delay]` - A two colour moving dye, or none.
- `/sbc custom armorTrim [material pattern]` - An armor trim, or none.
- `/sbc custom glint <on|off|default>` - Glint on, off or as normal.
- `/sbc custom renameItem [text]` - A custom name (& for colours), or opens the screen.
- `/sbc custom helmet <texture|item|off>`, `model <what|off>`, `armormodel <what|off>` - Change the head texture or the model.
- `/sbc custom clear` - Removes every custom look from the item you hold.
- `/sbc copyunderbid` - Copies the hovered auction's price minus 1.

## Chat and party

- `/sbc rule add <hide|replace|color|toast|sound> <pattern> [=> text]` - Adds a chat rule. `replace` and `color` need `=> text`.
- `/sbc rule list`, `/sbc rule remove <number>` - List or remove chat rules.
- `/sbc colors` - Prints the colour and format codes.
- `/sbc copy` - Copies the last 10 chat lines.
- `/sbc sendcoords [message]` - Sends your coordinates in the chat you are in.
- `/markplayer <name>` - Marks a player in chat, or unmarks them.
- `/pk <name> [reason]` - Kicks a player from the party.
- `/pw` - Party warp.
- `/pd` - Disbands the party.
- `/pko` - Kicks offline party members.
- `/pp <name>` - Promotes a party member.
- `/pt <name>` - Transfers the party. With no name, `/playtime`.
- `/rp`, `/reparty` - Disbands the party and invites everyone back.
- `/rpt` - Gives the party back to the last leader.

## Other

- `/sbc calc <equation>`, `/calc <equation>` - Works out a sum in your chat (2k, 3s, purse/2...).
- `/sbc speed [preset]` - Lists your speed presets, or sets your speed to one.
- `/sbc speed set <name> <speed>`, `remove <name>` - Save or delete a speed preset.
- `/sbc nightvision <0-100|off|full>` - Night Vision strength.
- `/sbc lastopened` - Opens the Ender Chest page or backpack you opened last.
- `/sbc ping` - Your ping to the server.
- `/sbc tps` - The server's TPS.
- `/sbc learn <task>` - The next game message becomes that routine task's trigger, for 5 minutes.
- `/sbc import` - Copies the custom scoreboard setup from another mod's settings in the same game folder.
- `/sbc disableall confirm` - Turns every feature off.
- `/sbc reset` - Puts every feature and the HUD back to how they start. No confirm.

## For a bug report

- `/sbc dump` - Writes what the mod sees to a file in `config/sbcompanion/dumps`.
- `/sbc perf` - Shows or hides the performance overlay.
- `/sbc perf record [seconds]`, `/sbc perf stop` - Times your game for 60 seconds, or 5 to 600, part by part.
- `/sbc backup` - Saves a copy of your settings.
- `/sbc backup list`, `/sbc backup restore <name>` - List your backups, or bring one back after a restart.

## Starting things over

None of these ask to confirm.

- `/sbc resetavarice` - Crown of Avarice session.
- `/sbc resetburrows`, `/sbc resetburrowwarps`, `/sbc resetdianamobs` - Diana burrow guesses, burrow warps and shared rare mobs.
- `/sbc resetcaketracker` - The New Year Cakes you own.
- `/sbc resetcontestdata` - Jacob's contests read from the calendar.
- `/sbc resetcropmilestones`, `/sbc resetcropspeed` - Crop milestones, and measured crop speeds.
- `/sbc resetdrystreak` - Experimentation Table dry streak.
- `/sbc resetgemstone` - Gemstone money card.
- `/sbc resethoppitystats confirm` - Every Hoppity's Hunt on record.
- `/sbc resethuntingbox` - The saved Hunting Box.
- `/sbc resetkinghelper` - King Talisman list.
- `/sbc resetmineshaftpity` - Mineshaft pity.
- `/sbc resetpets` - The pets read from your Pets menu.
- `/sbc resetploticons` - Garden plot icons.
- `/sbc resetpowdertracker` - Powder tracker.
- `/sbc resetpunchcard` - Rift Punchcard Artifact list.
- `/sbc resetreputation` - Crimson Isle reputation helper.
- `/sbc resetsavedrabbits` - The rabbits on record.
- `/sbc resetskillsession` - Skill session timer.
- `/sbc resetterminals` - Goldor terminal marks.
- `/sbc resetvermintracker` - Vermin tracker.
- `/sbc resetvisitordrops` - Visitor statistics.

## Changes to Hypixel commands

Each one is a feature you can turn off in Settings.

- `/viewrecipe <name>` - Takes a normal item name, like `/viewrecipe aspect of the end`.
- `/ec -`, `/bp -` - Open the Ender Chest page or backpack you opened last.
- `/setmaxspeed <preset>` - Takes one of your speed preset names.
- `/gfs <item> <amount>` - Takes item names with underscores.
- `/warp is` - Goes to your island. Off by default.
- `/<warp>` - Every warp works without `/warp`, like `/wizard`. Off by default.
- `/home`, `/barn`, `/tp <plot>` - Garden warps while on your Garden. Off by default.
- `/storage` - Opens the page you had open last. Off by default.
- `/playtime` - In Limbo, shows your Limbo time.
- `/p accept` - With no name, accepts the last party invite. Off by default.
- `/ahs`, `/ahsearch`, `/bzs` - Open the mod's market search. Off by default.
- Warps right after changing servers wait out Hypixel's 3 second cooldown. Off by default.
- A command Hypixel refuses for being too early runs again by itself when it may. Off by default.
- `/cf` is blocked while your chosen buffs are missing. Off by default.
- `/joininstance` is blocked while the dungeon cooldown runs. Off by default.
