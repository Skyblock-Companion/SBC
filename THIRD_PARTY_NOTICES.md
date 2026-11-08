# Third-party notices

SkyBlock Companion is licensed as a whole under the **GNU General Public License, version 3 only** (see `LICENSE`).
It contains or follows work by the projects below, and each keeps its own notice here. Full licence texts are in
`src/main/resources/licenses/` and inside the mod's jar at `licenses/`.

This list was built from each project's own licence files. Where a source does not state something clearly, this file says so
instead of guessing. It is a good faith reading of the licences, not legal advice.

## How the licences combine

- **SkyHanni** is under LGPL-2.1. Its code in this mod is conveyed under GPL-3.0 through section 3 of the LGPL-2.1.
- **Skyblocker** and the **NotEnoughUpdates** mod are under LGPL-3.0. Their code in this mod is conveyed under GPL-3.0 with the
  additional permissions of the LGPL removed, as LGPL-3.0 section 2 and GPL-3.0 section 7 allow.
- **Dungeon Rooms Mod** and **Secret Routes** are under GPL-3.0.
- MIT, BSD and Apache-2.0 material keeps its own notice and is compatible with GPL-3.0.
- **What the mod downloads is not under the GPL.** The pictures and map data are separate works under CC BY-NC-SA, in the project's data repository. They are not in the jar, and they may not be used commercially.

Java files carry an SPDX line and, where they follow another project, a line that names it and the year they were changed.

## Code

| Project | Holder or authors | Licence | Used for |
|---|---|---|---|
| [SkyHanni](https://github.com/SkyHanniStudios/SkyHanni) | hannibal2 and the SkyHanni contributors. Its licence file names no holder. | LGPL-2.1 | Logic and regexes ported across chat, Diana, Hoppity, Rift, mining, garden, Crimson Isle, items and enchants; tab widget and scoreboard patterns; test samples cut from its REGEX-TEST comments, with players' names replaced; the chest menu pictures in `assets/sbcompanion/dynamic_54/`. |
| [Skyblocker](https://github.com/SkyblockerMod/Skyblocker) | LifeIsAParadox/wohlhabend, kevinthegreat1, AzureAaron, viciscat, Emirlol, olim88, Alex33856 and contributors. Its licence file names no holder. | LGPL-3.0 | Ported logic for the museum, equipment slots, craft menu, fairy souls, dungeon rooms and map features; some regexes; equipment and quick-craft sprites; Goldor, Kuudra and water board data; dungeon room data.; the Crystal Hollows map picture `textures/gui/crystals_map.png` |
| [NotEnoughUpdates](https://github.com/NotEnoughUpdates/NotEnoughUpdates) | NotEnoughUpdates contributors, Copyright (C) 2022 | LGPL-3.0-or-later | Attribute shard name logic, reached through SkyHanni; the Improved SkyBlock menus code and pictures, reached through SkyHanni. |
| [Dungeon Rooms Mod](https://github.com/Quantizr/DungeonRoomsMod) | Copyright 2021 Quantizr(_risk) | GPL-3.0 | Through Skyblocker: many of the dungeon room samples and secret waypoints in `sbc/dungeon_rooms.json`. |
| [Secret Routes](https://github.com/yourboykyle/SecretRoutes) | Copyright 2025 yourboykyle, wyannnnn (R-aMcC), with Itplays, zzyyrraa, 3ppopka and FlameOfWar. Each file's `#copyright` field is kept. | GPL-3.0 | Dungeon route sets in `sbc/routes_*.json` and their format. |
| [SkyBlockAPI](https://github.com/SkyblockAPI/SkyblockAPI) | Copyright (c) 2025. The holder is blank in its licence file. | MIT | Museum page reading followed in `MuseumData.java`. |
| [Modern Warp Menu](https://github.com/Yukkuritaku/ModernWarpMenu) | Copyright (c) 2024 Yukkuritaku | MIT | The warp menu: `WarpScreen`, `WarpLayout`, `WarpBlit`, layouts and constants. Island sizes and portal points were then changed for the pictures below. |
| [Fancy Warp Menu](https://github.com/ILikePlayingGames/FancyWarpMenu) | Copyright (c) 2023 TirelessTraveler | MIT | The original of that menu: its logo, notification dot and regular menu button pictures. |
| [SkyblockAddons](https://github.com/BiscuitDevelopment/SkyblockAddons) | Copyright (c) 2023 Biscuit Development | MIT | Portal picture, through Fancy Warp Menu; the enchant parser's origin, through SkyHanni. |
| [owo-lib](https://github.com/wisp-forest/owo-lib) | Copyright (c) 2021, no holder written; glisco is the author its mod file names | MIT | The screen layout code in `ui/layout` is ported from its 26.2 branch and trimmed; `sounds/ui_interaction.ogg` is its sound. No longer a dependency. |
| [SkyCubed](https://github.com/meowdding/SkyCubed) | Copyright (c) 2024. The holder is blank in its licence file; its mod file names ThatGravyBoat and j10a1n15. | MIT | The approach of the island maps in `map/SkyMaps.java`. |
| [SkyHelper-Networth](https://github.com/Altpapier/SkyHelper-Networth) | Copyright (c) 2022 Altpapier | MIT | Pet price scaling by XP. |

## Data in the jar

| Project | Holder | Licence | Used for |
|---|---|---|---|
| [SkyHanni-REPO](https://github.com/SkyHanniStudios/SkyHanni-REPO) | Copyright (c) 2022 hannibal2 | MIT | `data/sh/*`, `patterns/skyhanni_regexes.json`; updated at runtime from a copy in the data repository. |
| [NotEnoughUpdates-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) | Copyright (c) 2020 Moulberry | MIT | `data/neu/*`; updated at runtime from a copy in the data repository. |
| [SkyblockAPI Repo](https://github.com/SkyblockAPI/Repo) | A transformation of NEU-REPO; its README says the data is owned by NEU-REPO under MIT. It has no licence file of its own. | MIT, by reference | `data/repo/*`; updated at runtime from a copy in the data repository. |

## Downloaded by each player, not in the jar

The player's game downloads these. Most come from the project's [data repository](https://github.com/Skyblock-Companion/sbc-data), which copies them with their credit and licence; the rest come directly from the host named here. They are not in the mod's jar.

| What | From | Terms |
|---|---|---|
| Island map data | The project's data repository, copied daily from [meowdding-repo](https://github.com/meowdding/meowdding-repo/tree/master/repo/skycubed/maps), by meowdding and ThatGravyBoat | CC BY-NC-SA 4.0. Non-commercial only. |
| Warp menu island pictures | The project's data repository, from the [Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki)'s renders by its contributors, each listed in `licenses/wiki-island-pictures.txt`. Designs by Hypixel Inc., assets partly by Mojang. | CC BY-NC-SA 3.0. Non-commercial only. Trimmed and resized. |
| Island map images | The project's data repository, from the wiki's top view renders and [SkyGuide](https://github.com/DeDiamondPro/SkyGuide)'s images by DeDiamondPro, each listed in `licenses/map-images.txt`. Designs by Hypixel Inc., assets partly by Mojang. | CC BY-NC-SA 3.0 (wiki), 4.0 (SkyGuide). Non-commercial only. Cropped and resized to fit the map data. |
| Item prices | [SkyHelper Prices](https://github.com/SkyHelperBot/Prices), by Altpapier and the SkyHelper team | No licence is stated. Used with credit, through the same public file the official library reads. |
| Island map images not in the data repository yet, for an island added later | The URL in the updated map data, usually Team Resourceful's image host | Not stated for those images. |
| Pet prices from George | [NEU-REPO](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO) `george.json`, from the wiki | CC BY-NC-SA 3.0. |
| Wiki pages in the in-game browser | [Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki), read through its API and drawn by the mod | CC BY-NC-SA 3.0. |
| Player and game data | Hypixel's public API and Bingo data. Profiles and museums go through this project's server, which receives only the player or profile id being looked up and holds the key | Each service's own terms. No key ships with the mod, and players never enter one. |

Because of the NonCommercial material here and above, a paid or donation-gated version of the mod would need these sources removed or checked again.

## Libraries

| Library | Holder | Licence | How |
|---|---|---|---|
| [Fabric API](https://github.com/FabricMC/fabric) | FabricMC | Apache-2.0 | Packed inside the mod's jar, unmodified. |
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) | FabricMC | Apache-2.0 | Required at runtime, not included. |
| [Hypixel Mod API](https://github.com/HypixelDev/ModAPI) | Copyright (c) 2024 Hypixel Inc. | MIT | `libs/mod-api-1.0.2.jar`, build time only; used at runtime if the player has it installed. |

## Ideas, no code or assets taken

- [SkyBlock Item List](https://github.com/OperationPotato/ItemList), by Alex33856 and j10a1n15 (All Rights Reserved): the idea of an item list beside menus.
- [Tooltip Scroll](https://github.com/Provismet/Tooltip-Scroll-Fabric), by Provismet (MIT): the idea of scrolling long tooltips.
- [SkyBlock Enhancements](https://github.com/KdGaming0/SkyBlock-Enhancements), by Kd_Gaming1 (MIT): slot locks are imported from its config so both can be used together.

## Not affiliated

SkyBlock Companion is an unofficial fan project and is not affiliated with or endorsed by Hypixel Inc.
Hypixel, SkyBlock and their game text and designs belong to Hypixel Inc.; game text in the data above is theirs.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
