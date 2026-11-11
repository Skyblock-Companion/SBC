# SkyBlock Companion - FAQ

## Using the mod

### How do I open the menu?

Press **K**. `/sbc settings` opens the settings directly, and `/sbc` lists every command of the mod.

### How do I move, resize or hide something on my HUD?

Press **H** (or `/sbc hudedit`). Drag a card to move it, use the mouse wheel to resize it (hold Shift to resize all of them) and right-click it for its menu. **P** opens the panel where you hide and show cards, and Delete puts a card back the way it started.

### How do I turn a feature off?

Press **K**, open Settings and search for it.

### How do I reset a tracker?

`/sbc resetprofit` starts every tracker over for this session. Day, week and all-time totals keep what they have. `/sbc resetprofit <tracker>` clears every total of one tracker.

### Can I bring my scoreboard over from another mod?

Yes. If that mod's settings are in the same game folder, `/sbc import` copies its custom scoreboard: the lines and their order, the title, spacing, background and size.

### How do I reset all my settings?

`/sbc backup` saves a copy of them first, in `config/sbcompanion/backups`. Then `/sbc reset` puts every feature and the HUD back to how they start.

## When something looks wrong

### A tracker does not count items that go into my sacks

Trackers read Hypixel's `[Sacks]` chat message. Turn it on in Hypixel's settings: `Settings -> Personal -> Chat Feedback -> Sack Notifications`.

### A helper does not know my accessories, pets or sacks

The helpers learn from your profile and from Hypixel's own menus. Open the Accessory Bag, the Pets menu or the Sack of Sacks once, and they pick it up.

### I see two scoreboards or two tab lists

Another mod draws its own. Turn off that mod's scoreboard or tab list, or ours in Settings.

### Prices or game data look old

Prices update while you play, and game data every time the game starts. If a download fails, the mod keeps working with the last copy it has. Restart the game, and if it stays wrong, report it.

### The game crashes when it starts

Make sure Fabric Loader and Fabric API are up to date and, in launchers like Prism or MultiMC, that the instance uses Java 25. Then send the crash report from the `crash-reports` folder in the [Discord server](https://discord.gg/QrqX3pgVW2).

### Something is wrong, how do I report it?

1. Type `/sbc dump` in game right after it happens. It writes a file to `config/sbcompanion/dumps` with what the mod saw.
2. Open an [issue](https://github.com/Skyblock-Companion/SBC/issues) or post in the [Discord server](https://discord.gg/QrqX3pgVW2), with the file and what you expected.

## About the mod

### How is the FPS with it?

Very good. The HUD is drawn into one image 20 times a second and reused in between, instead of being drawn every frame, so it barely affects your FPS. `/sbc perf record` shows what the mod costs in your own game.

### What does it send, and where?

A few requests, so its features work:

- **Profile viewer and museum**: the player or profile you look up goes to the project's own server, which reads it from the Hypixel API and keeps a short copy.
- **Update check**: when the game starts, only the mod's version goes to the project's own server, which answers whether a newer one is out.
- **Prices**: Bazaar and auction data from the public [Hypixel API](https://api.hypixel.net), a lowest BIN list from the project's [data repository](https://github.com/Skyblock-Companion/sbc-data), and item prices from [SkyHelper Prices](https://github.com/SkyHelperBot/Prices). Nothing about you is sent.
- **Game data and pictures**: island pictures, maps and game data from the project's [data repository](https://github.com/Skyblock-Companion/sbc-data), which holds copies of SkyHanni-REPO, NotEnoughUpdates-REPO and others with their credits and licences. Nothing about you is sent.
- **Wiki**: pages of the [Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki) when you open them in the in-game wiki.
- **Links**: the Discord, CurseForge, Modrinth and GitHub buttons open your browser only when you click them.

No private data is ever sent to anyone else.

### Where are my settings?

In `config/sbcompanion` inside your Minecraft folder. Copy that folder to keep them.

---

Not answered here? Ask in the [Discord server](https://discord.gg/QrqX3pgVW2).
