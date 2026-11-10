// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker, NotEnoughUpdates, SkyBlockAPI, Modern Warp Menu, owo-lib, SkyCubed, Secret Routes, Dungeon Rooms Mod, SkyHelper; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.screens;

import io.github.zmuzy.sbc.ui.hud.Looks;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import io.github.zmuzy.sbc.ui.layout.UIComponents;
import io.github.zmuzy.sbc.ui.layout.FlowLayout;
import io.github.zmuzy.sbc.ui.layout.UIContainers;
import io.github.zmuzy.sbc.ui.layout.Color;
import io.github.zmuzy.sbc.ui.layout.HorizontalAlignment;
import io.github.zmuzy.sbc.ui.layout.Insets;
import io.github.zmuzy.sbc.ui.layout.Sizing;
import io.github.zmuzy.sbc.ui.layout.VerticalAlignment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// the one place naming the projects we learned from + reuse: who, license, what we took
// opens from the K menu under Settings, from Settings or /sbc credits. no server address here
public final class CreditsScreen extends SbcScreen {
    private static final String[][] CREDITS = {
            {"SkyBlock Companion", "Copyright (C) 2026 zMuzy - GNU GPL version 3 only", "Free software: you may share and change it under the GPL, version 3 only. It comes with ABSOLUTELY NO WARRANTY, to the extent the law allows. The licence and the full notice of every project below are inside the jar: LICENSE-SkyBlockCompanion.txt, THIRD_PARTY_NOTICES.md and licenses/. Source: github.com/Skyblock-Companion/SBC"},
            {"SkyHanni", "hannibal2 and the SkyHanni contributors - LGPL-2.1, conveyed under GPL-3.0", "Logic and regexes across chat, Diana, Hoppity, Rift, mining, garden, Crimson Isle, items and enchants; tab widget and scoreboard patterns; the chest menu pictures."},
            {"Skyblocker", "LifeIsAParadox/wohlhabend, kevinthegreat1, AzureAaron, viciscat, Emirlol, olim88, Alex33856 and contributors - LGPL-3.0, conveyed under GPL-3.0", "Logic for the museum, equipment slots, craft menu, fairy souls, dungeon rooms and map features; some regexes; equipment and quick-craft sprites; Goldor, Kuudra and water board data; the Crystal Hollows map picture."},
            {"NotEnoughUpdates", "Copyright (C) 2022 NotEnoughUpdates contributors - LGPL-3.0-or-later, conveyed under GPL-3.0", "Attribute shard names and the Improved SkyBlock menus code and pictures, through SkyHanni."},
            {"Dungeon Rooms Mod", "Copyright 2021 Quantizr(_risk) - GPL-3.0", "Many of the dungeon rooms and secret waypoints, through Skyblocker."},
            {"Secret Routes", "Copyright 2025 yourboykyle, wyannnnn (R-aMcC), with Itplays, zzyyrraa, 3ppopka and FlameOfWar - GPL-3.0", "The dungeon secret route sets and their format."},
            {"SkyBlockAPI", "SkyblockAPI, Copyright (c) 2025, no holder named in its licence - MIT", "Museum page reading."},
            {"Modern Warp Menu", "Copyright (c) 2024 Yukkuritaku - MIT", "The warp menu and its layouts."},
            {"Fancy Warp Menu", "Copyright (c) 2023 TirelessTraveler - MIT", "The original of that menu: its logo, notification dot and menu button pictures."},
            {"SkyblockAddons", "Copyright (c) 2023 Biscuit Development - MIT", "The portal picture, and the origin of the enchant parser."},
            {"owo-lib", "glisco and wisp-forest, Copyright (c) 2021, no holder named in its licence - MIT", "Our screen layout code is ported from it and trimmed, and the menu's click sound is its own. Not a dependency."},
            {"SkyCubed", "ThatGravyBoat and j10a1n15 (meowdding), Copyright (c) 2024, no holder named in its licence - MIT", "The approach of the island maps."},
            {"SkyHelper-Networth", "Copyright (c) 2022 Altpapier - MIT", "Pet prices scaled by XP."},
            {"SkyHanni-REPO", "Copyright (c) 2022 hannibal2 - MIT", "Game data and most of the regexes, in the jar and kept up to date."},
            {"NotEnoughUpdates-REPO", "Copyright (c) 2020 Moulberry - MIT", "Item data, recipes and names behind the item list, prices and tooltips, kept up to date."},
            {"SkyblockAPI Repo", "ThatGravyBoat, from NEU-REPO's data - MIT", "The item, pet, recipe and mob files in the jar and their updates."},
            {"Island pictures", "Hypixel SkyBlock Wiki contributors - CC BY-NC-SA 3.0", "The warp menu's renders, downloaded from the project's data repository (Skyblock-Companion/sbc-data), not in the jar. Each picture and its page is in licenses/wiki-island-pictures.txt. Designs by Hypixel Inc., assets partly by Mojang. Non-commercial."},
            {"Island map images", "Hypixel SkyBlock Wiki contributors - CC BY-NC-SA 3.0; SkyGuide by DeDiamondPro - CC BY-NC-SA 4.0", "The drawn maps, downloaded from the data repository, not in the jar. Each image and its source is in licenses/map-images.txt. Non-commercial."},
            {"Island map data", "meowdding and ThatGravyBoat (meowdding-repo) - CC BY-NC-SA 4.0", "Bounds and points of interest, downloaded from the data repository, not in the jar. Non-commercial."},
            {"SkyHelper Prices", "Altpapier and the SkyHelper team - no licence stated", "Item prices, downloaded by your game from the same public list their own library reads. Used with credit."},
            {"Hypixel SkyBlock Wiki", "hypixelskyblock.minecraft.wiki contributors - CC BY-NC-SA 3.0", "The pages in the in-game browser, read through its API; George's pet prices through NEU-REPO."},
            {"Hypixel API", "Hypixel Inc. - its own terms", "Player, profile and game data. Lookups go through this project's server, which holds the key; no key ships with the mod."},
            {"Fabric Loader, Fabric API", "FabricMC - Apache-2.0", "The loader, needed and not included, and the API, packed inside this mod unchanged."},
            {"Hypixel Mod API", "Copyright (c) 2024 Hypixel Inc. - MIT", "Exact location when you have it installed."},
            {"Ideas", "No code or pictures taken", "SkyBlock Item List by Alex33856 and j10a1n15: an item list beside menus. Tooltip Scroll by Provismet: scrolling long tooltips. SkyBlock Enhancements by Kd_Gaming1: its slot locks are read so both mods work together."},
            {"Minecraft and Hypixel", "Mojang Studios, Hypixel Inc.", "An unofficial fan project, not affiliated with or endorsed by Hypixel Inc. Hypixel, SkyBlock and their game text and designs belong to Hypixel Inc. NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."},
    };

    public CreditsScreen(Screen parent) {
        super("Credits", parent);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(backdrop());
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(Math.min(440, width - 20)), Sizing.fill(86));
        panel.surface(panel());
        panel.padding(Insets.of(8));
        panel.gap(6);
        panel.child(label("Credits", accent()));
        panel.child(label("SkyBlock Companion stands on the work of these projects. Thank you.", dim()).maxWidth(Math.min(420, width - 40)));
        FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(4);
        list.padding(Insets.right(4));
        int w = Math.min(410, width - 50);
        for (String[] c : CREDITS) {
            int color = Looks.colourFor(c[0]);
            FlowLayout card = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
            card.surface(glass(color, true));
            card.padding(Insets.of(3, 5, 5, 5));
            card.gap(2);
            card.child(label(c[0], Color.ofArgb(Modern.lighten(color | 0xFF000000, 0.45f))).margins(Insets.bottom(5)));
            card.child(label(c[1], accent()).maxWidth(w));
            card.child(label(c[2], Color.ofArgb(Modern.LABEL)).maxWidth(w));
            list.child(card);
        }
        panel.child(SbcWidgets.scroll(Sizing.fill(100), Sizing.expand(), list));
        FlowLayout buttons = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttons.gap(6);
        buttons.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Join the Discord"), b -> SetupScreen.openDiscord())).horizontalSizing(Sizing.fixed(120)));
        buttons.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Close"), b -> onClose())).horizontalSizing(Sizing.expand()));
        panel.child(buttons);
        root.child(panel);
    }

    public static void open(Screen parent) {
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(new CreditsScreen(parent));
    }
}
