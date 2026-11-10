// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker, SkyHelper; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.tasks.Routine;
import dev.zmuzy.sbc.tasks.TaskDef;
import dev.zmuzy.sbc.track.Tracker;
import dev.zmuzy.sbc.track.Watcher;
import dev.zmuzy.sbc.ui.CompanionScreen;
import dev.zmuzy.sbc.ui.Hud;
import dev.zmuzy.sbc.ui.Toasts;
import dev.zmuzy.sbc.ui.Ui;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// skyblock companion: routines, planner, minions, profile viewer, navigation + stats for hypixel skyblock.
// info only, it reads the game + public hypixel data and never does actions for you
public class SbcMod implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("SkyBlockCompanion");
    public static final boolean DEV = Boolean.getBoolean("sbc.dev");
    // hud check: no menu opens so you can look at the main hud
    public static final boolean HUD_CHECK = Boolean.getBoolean("sbc.hud");
    public static final boolean CYCLE = Boolean.getBoolean("sbc.cycle");

    private static KeyMapping openKey, zoomIn, zoomOut, warpKey, mapKey, lockKey, sackKey, storageKey, hudEditKey;

    // settings key, null before the mod starts
    public static KeyMapping openKey() {
        return openKey;
    }

    // hud editor key, null before the mod starts
    public static KeyMapping hudEditKey() {
        return hudEditKey;
    }
    public static KeyMapping peekKey, radialKey, marketKey, protectKey;
    private static KeyMapping hoppityLiveKey, dianaWarpKey, corpseShareKey, rareMobShareKey, vanquisherShareKey, underbidKey, mineshaftTypeKey;
    public static boolean openNextTick;
    private static boolean overlaysDrawn;

    // value panel, item list, search + buttons around a container
    public static void containerOverlays(Screen s, net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my) {
        if (!(s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return;
        overlaysDrawn = true;
        try {
            dev.zmuzy.sbc.item.menus.MenuMarks.overlay(s, g, mx, my);
            dev.zmuzy.sbc.ui.ContainerValue.draw(s, g, mx, my);
            dev.zmuzy.sbc.ui.ItemList.draw(s, g, mx, my);
            dev.zmuzy.sbc.ui.SideHelper.draw(s, g, mx, my);
            dev.zmuzy.sbc.ui.panels.Panels.draw(s, g, mx, my);
            dev.zmuzy.sbc.item.InventorySearch.draw(s, g, mx, my);
            dev.zmuzy.sbc.item.QuickNav.draw(s, g, mx, my);
            dev.zmuzy.sbc.item.WardrobeKeys.draw(s, g);
        } catch (Throwable t) {
            Safe.caught("SbcMod.containerOverlays", t);
        }
    }
    private static int ticks;
    private static boolean wasInSkyBlock;

    @Override
    public void onInitializeClient() {
        Config.load();
        Store.load();
        dev.zmuzy.sbc.minion.Minions.cleanup();
        Store.I.observedSkills.keySet().retainAll(GameState.REAL_SKILLS);
        Repo.load();
        dev.zmuzy.sbc.feature.Builtin.register();
        dev.zmuzy.sbc.mc.EmptyStands.register();
        dev.zmuzy.sbc.ui.UiFeatures.register();
        dev.zmuzy.sbc.map.SkyMaps.load();
        dev.zmuzy.sbc.core.data.DataImages.prefetchAll();
        dev.zmuzy.sbc.minion.MinionCatalog.load();
        dev.zmuzy.sbc.minion.MinionMath.load();
        dev.zmuzy.sbc.core.ItemRepo.load();
        dev.zmuzy.sbc.core.RepoData.update();
        dev.zmuzy.sbc.core.data.DataUpdater.start();
        dev.zmuzy.sbc.tasks.FactionQuests.load();
        dev.zmuzy.sbc.plan.Forge.load();
        dev.zmuzy.sbc.core.ItemInfo.load();
        if (FabricLoader.getInstance().isModLoaded("hypixel-mod-api")) dev.zmuzy.sbc.core.HypixelLocation.init();
        Safe.disconnect("SbcMod.disconnect", (handler, client) -> dev.zmuzy.sbc.core.GameState.disconnected());

        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sbcompanion", "main"));
        dev.zmuzy.sbc.chat.PartyCommands.keys(cat);
        dev.zmuzy.sbc.commands.CommandPack.keys(cat);
        openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_K, cat));
        zoomIn = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.zoom_in", InputConstants.Type.KEYBOARD, InputConstants.KEY_ADD, cat));
        zoomOut = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.zoom_out", InputConstants.Type.KEYBOARD, org.lwjgl.sdl.SDLScancode.SDL_SCANCODE_KP_MINUS, cat));
        warpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.warp", InputConstants.Type.KEYBOARD, InputConstants.KEY_J, cat));
        mapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.map", InputConstants.Type.KEYBOARD, InputConstants.KEY_N, cat));
        lockKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.lock", InputConstants.Type.KEYBOARD, InputConstants.KEY_L, cat));
        protectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.protect", InputConstants.Type.KEYBOARD, InputConstants.KEY_V, cat));
        sackKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.sack", InputConstants.Type.KEYBOARD, InputConstants.KEY_G, cat));
        storageKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.storage", InputConstants.Type.KEYBOARD, InputConstants.KEY_B, cat));
        hudEditKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.hudedit", InputConstants.Type.KEYBOARD, InputConstants.KEY_H, cat));
        marketKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.market", InputConstants.Type.KEYBOARD, InputConstants.KEY_Y, cat));
        dev.zmuzy.sbc.item.market.BuyKey.keys(cat);
        peekKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.peek", InputConstants.Type.KEYBOARD, InputConstants.KEY_GRAVE, cat));
        radialKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.radial", InputConstants.Type.KEYBOARD, InputConstants.KEY_U, cat));
        int unbound = InputConstants.UNKNOWN.getValue();
        dianaWarpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.diana_warp", InputConstants.Type.KEYBOARD, unbound, cat));
        hoppityLiveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.hoppity_live", InputConstants.Type.KEYBOARD, unbound, cat));
        corpseShareKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.share_corpse", InputConstants.Type.KEYBOARD, unbound, cat));
        rareMobShareKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.share_rare_mob", InputConstants.Type.KEYBOARD, unbound, cat));
        vanquisherShareKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.share_vanquisher", InputConstants.Type.KEYBOARD, unbound, cat));
        mineshaftTypeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.share_mineshaft", InputConstants.Type.KEYBOARD, unbound, cat));
        underbidKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.copy_underbid", InputConstants.Type.KEYBOARD, unbound, cat));
        dev.zmuzy.sbc.dungeon.DungeonChests.favoriteKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.chest_favorite", InputConstants.Type.KEYBOARD, unbound, cat));
        dev.zmuzy.sbc.item.menus.Menus.keys(cat);
        dev.zmuzy.sbc.garden.GardenHelpers.keys(cat);
        dev.zmuzy.sbc.garden.GardenPack.keys(cat);
        dev.zmuzy.sbc.crimson.Crimson.keys(cat);
        dev.zmuzy.sbc.misc.MiscPack.keys(cat);
        dev.zmuzy.sbc.ui.UiFeatures.wikiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.wiki_lookup", InputConstants.Type.KEYBOARD, InputConstants.KEY_F1, cat));
        dev.zmuzy.sbc.ui.UiFeatures.priceKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.price_lookup", InputConstants.Type.KEYBOARD, InputConstants.KEY_F6, cat));
        dev.zmuzy.sbc.item.tooltip.FancyTooltip.plainKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.plain_tooltip", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), cat));


        Safe.endTick("SbcMod.tick", this::tick);
        Safe.startTick("KeyClashes.settle", mc -> dev.zmuzy.sbc.mc.KeyClashes.settle(mc, lockKey));
        dev.zmuzy.sbc.mc.TextInput.register();
        // extra tooltip lines from any feature show in vanilla inventories and our own pages the same
        Safe.tooltip("SbcMod.tooltip", (stack, ctx, flag, lines) -> dev.zmuzy.sbc.feature.Features.tooltip(stack, lines));
        // our pages open inside a host screen (host owns the window, the page is one component)
        Safe.afterInit("SbcMod.afterInit", (client, screen, w, h) -> {
            if (screen instanceof dev.zmuzy.sbc.ui.MarketSearchScreen || screen instanceof dev.zmuzy.sbc.ui.RadialScreen
                    || screen instanceof dev.zmuzy.sbc.ui.ItemScreen || screen instanceof dev.zmuzy.sbc.ui.AccessoryScreen
                    || screen instanceof dev.zmuzy.sbc.ui.PetsScreen || screen instanceof dev.zmuzy.sbc.ui.BrowserScreen
                    || screen instanceof dev.zmuzy.sbc.ui.DialogueScreen || screen instanceof dev.zmuzy.sbc.ui.MissionScreen) {
                client.execute(() -> {
                    if (client.gui.screen() == screen) client.gui.setScreen(dev.zmuzy.sbc.ui.screens.LegacyHost.wrap(screen));
                });
            }
        });
        // is a newer version out, asked once. the answer shows as a card on the title screen
        dev.zmuzy.sbc.core.ProfileServer.checkVersion();
        // first start with the mod: setup page, then the update card, over the title screen. a tick not the screen
        // init bc at start the title screen gets built under the loading overlay
        Safe.endTick("SbcMod.titleScreens", dev.zmuzy.sbc.ui.screens.SetupScreen::titleTick);
        dev.zmuzy.sbc.ui.screens.TutorialScreen.register();
        Safe.startTick("EntityTickCache.tick", dev.zmuzy.sbc.mc.EntityTickCache::tick);
        // the picture warp menu replaces the fast travel chest the moment it opens
        Safe.afterInit("SbcMod.afterInit#2", (client, screen, w, h) -> {
            if (GameState.inSkyBlock && screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen cs
                    && dev.zmuzy.sbc.ui.screens.WarpScreen.kind(cs.getTitle().getString()) != 0) {
                client.execute(() -> {
                    if (client.gui.screen() == cs) dev.zmuzy.sbc.ui.screens.WarpScreen.openFor(cs);
                });
            }
        });
        // inventories + chests: chest value on top, lock key over a slot
        Safe.afterInit("SbcMod.afterInit#4", (client, screen, w, h) -> {
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs)) return;
            // only if ScreenOverlayMixin didnt draw them first, thats the way that keeps the tooltip on top
            Safe.afterExtract("SbcMod.afterExtract", screen, (s, g, mx, my, delta) -> {
                if (!overlaysDrawn) containerOverlays(s, g, mx, my);
                overlaysDrawn = false;
            });
            Safe.allowMouseClick("SbcMod.allowMouseClick", screen, (s, event) ->
                    dev.zmuzy.sbc.ui.ItemList.click(s, event.x(), event.y(), dev.zmuzy.sbc.mc.KeyHeld.clickButton(event.button())) && dev.zmuzy.sbc.ui.panels.Panels.click(s, event) && dev.zmuzy.sbc.ui.SideHelper.click(s, event.x(), event.y(), dev.zmuzy.sbc.mc.KeyHeld.clickButton(event.button())) && dev.zmuzy.sbc.item.QuickNav.click(s, event.x(), event.y(), dev.zmuzy.sbc.mc.KeyHeld.clickButton(event.button())));
            Safe.allowMouseScroll("SbcMod.allowMouseScroll", screen, (s, smx, smy, sh, sv) ->
                    dev.zmuzy.sbc.ui.SideHelper.scroll(s, smx, smy, sv) && dev.zmuzy.sbc.ui.ItemList.scroll(s, smx, smy, sv) && !dev.zmuzy.sbc.item.menus.MenuExtras.scroll(s, sv));
            Safe.allowCharType("SbcMod.allowCharType", screen, (s, event) ->
                    dev.zmuzy.sbc.ui.panels.Panels.typed(s, event.codepoint()) && dev.zmuzy.sbc.item.InventorySearch.typed(s, event.codepoint()) && dev.zmuzy.sbc.ui.ItemList.typed(s, event.codepoint()));
            Safe.allowKeyPress("SbcMod.allowKeyPress", screen, (s, event) -> {
                if (s instanceof dev.zmuzy.sbc.ui.storage.StorageOverlayScreen o && o.typing()) return true;
                if (!dev.zmuzy.sbc.item.InventorySearch.key(s, event.key(), event.modifiers())) return false;
                if (!dev.zmuzy.sbc.ui.panels.Panels.key(cs, event)) return false;
                if (dev.zmuzy.sbc.item.menus.MenuExtras.key(event.key(), event)) return false;
                if (!dev.zmuzy.sbc.item.WardrobeKeys.key(s, event)) return false;
                if (!dev.zmuzy.sbc.events.hoppity.CFHelpers.key(s, event.key())) return false;
                if (!dev.zmuzy.sbc.dungeon.DungeonKeys.key(s, event)) return false;
                if (!dev.zmuzy.sbc.dungeon.DungeonChests.favoriteKey(s, event)) return false;
                if (!dev.zmuzy.sbc.garden.pests.PestTraps.key(s, event)) return false;
                if (dev.zmuzy.sbc.ui.UiFeatures.key(cs, event)) return false;
                if (!dev.zmuzy.sbc.item.market.BuyKey.key(s, event)) return false;
                if (!dev.zmuzy.sbc.ui.ItemList.key(s, event.key(), (event.modifiers() & InputConstants.MOD_SHIFT) != 0)) return false;
                if (dev.zmuzy.sbc.feature.Features.on("market_search") && marketKey.matches(event) && dev.zmuzy.sbc.ui.MarketSearchScreen.isMarketTitle(cs.getTitle().getString())) {
                    Minecraft.getInstance().gui.setScreen(new dev.zmuzy.sbc.ui.MarketSearchScreen(cs, dev.zmuzy.sbc.ui.MarketSearchScreen.modeFor(cs.getTitle().getString())));
                    return false;
                }
                if (dev.zmuzy.sbc.feature.Features.on("radial_menu") && radialKey.matches(event) && cs instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen chest) {
                    Minecraft.getInstance().gui.setScreen(new dev.zmuzy.sbc.ui.RadialScreen(chest, chest.getMenu(), chest.getTitle()));
                    return false;
                }
                if (dev.zmuzy.sbc.feature.Features.on("gfs_key") && sackKey.matches(event)) {
                    var hovered = ((dev.zmuzy.sbc.mc.mixin.ContainerAccess) cs).sbc$hovered();
                    if (hovered != null) {
                        dev.zmuzy.sbc.item.GetFromSack.press(hovered, (event.modifiers() & InputConstants.MOD_SHIFT) != 0);
                        return false;
                    }
                }
                if (underbidKey.matches(event)) {
                    dev.zmuzy.sbc.item.market.Market.copyUnderbidHovered();
                    return false;
                }
                if (dev.zmuzy.sbc.feature.Features.on("slotlocks") && lockKey.matches(event)) {
                    var hovered = ((dev.zmuzy.sbc.mc.mixin.ContainerAccess) cs).sbc$hovered();
                    if (hovered != null) {
                        dev.zmuzy.sbc.item.SlotLocks.toggle(hovered, false);
                        return false;
                    }
                }
                if (dev.zmuzy.sbc.feature.Features.on("slotlocks") && protectKey.matches(event)) {
                    var hovered = ((dev.zmuzy.sbc.mc.mixin.ContainerAccess) cs).sbc$hovered();
                    if (hovered != null) {
                        dev.zmuzy.sbc.item.SlotLocks.toggle(hovered, true);
                        return false;
                    }
                }
                return true;
            });
        });
        Safe.game("SbcMod.game", (message, overlay) -> onMessage(message, overlay));
        dev.zmuzy.sbc.mc.ChatHooks.register();
        Safe.afterInit("SbcMod.afterInit#5", (client, screen, w, h) -> {
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return;
            dev.zmuzy.sbc.item.tooltip.TooltipScroll.reset();
            Safe.allowMouseScroll("SbcMod.allowMouseScroll#2", screen, (s, mx, my, hAmount, vAmount) -> {
                long win = client.getWindow().handle();
                boolean shift = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                        || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
                return dev.zmuzy.sbc.item.tooltip.TooltipScroll.onScroll(s, vAmount, shift);
            });
        });
        dev.zmuzy.sbc.mc.HudHooks.register();
        Safe.stopping("SbcMod.stopping", c -> {
            Config.save();
            Store.save();
        });

        Safe.commands("SbcMod.commands", (dispatcher, registry) -> dispatcher.register(dev.zmuzy.sbc.nav.Destinations.command("sbnav")));
        Safe.commands("SbcMod.commands#2", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("reset").executes(c -> {
                    dev.zmuzy.sbc.core.Config.resetFeatures();
                    c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7fEvery feature and the HUD are back to how they start."));
                    return 1;
                }))
                .then(literal("import").executes(c -> {
                    c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7f" + dev.zmuzy.sbc.core.ImportConfigs.skyhanniScoreboard()));
                    return 1;
                }))
                .then(literal("credits").executes(c -> {
                    Minecraft.getInstance().execute(() -> dev.zmuzy.sbc.ui.screens.CreditsScreen.open(null));
                    return 1;
                }))
                .then(literal("setup").executes(c -> {
                    Minecraft.getInstance().execute(() -> dev.zmuzy.sbc.ui.screens.SetupScreen.open(null));
                    return 1;
                }))
                .then(literal("whatsnew").executes(c -> {
                    Minecraft.getInstance().execute(() -> dev.zmuzy.sbc.ui.screens.WhatsNewScreen.openLatest(null));
                    return 1;
                }))
                .then(literal("tutorial").executes(c -> {
                    Minecraft.getInstance().execute(() -> dev.zmuzy.sbc.ui.screens.TutorialScreen.open(null));
                    return 1;
                }))
                .then(literal("discord").executes(c -> {
                    c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("§7Join the SkyBlock Companion Discord: ")
                            .append(net.minecraft.network.chat.Component.literal("§9§n" + dev.zmuzy.sbc.ui.screens.SetupScreen.DISCORD)
                                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(dev.zmuzy.sbc.ui.screens.SetupScreen.DISCORD)))
                                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("Click to open")))))
                            .append(net.minecraft.network.chat.Component.literal(" §8(click to open)")));
                    return 1;
                }))
                .then(literal("resetprofit").executes(c -> {
                    dev.zmuzy.sbc.track.Profit.reset();
                    c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal("§7Profit trackers start over for this session. Day, week and all-time totals keep what they have."));
                    return 1;
                }).then(argument("tracker", StringArgumentType.greedyString()).executes(c -> {
                    String tracker = StringArgumentType.getString(c, "tracker");
                    c.getSource().sendFeedback(net.minecraft.network.chat.Component.literal(dev.zmuzy.sbc.track.Profit.resetAll(tracker) ? "§7Every total of that tracker starts over." : "§cNo tracker called " + tracker));
                    return 1;
                })))
                .then(literal("pv").executes(c -> {
                    dev.zmuzy.sbc.core.Profile.loadSelfIfOld();
                    CompanionScreen.page = 7;
                    openNextTick = true;
                    return 1;
                }).then(argument("player", StringArgumentType.word()).executes(c -> {
                    dev.zmuzy.sbc.core.Profile.lookup(StringArgumentType.getString(c, "player"));
                    CompanionScreen.page = 7;
                    openNextTick = true;
                    return 1;
                })))
                .then(literal("pets").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new dev.zmuzy.sbc.ui.PetsScreen(mc.gui.screen())));
                    return 1;
                }))
                .then(literal("help").executes(c -> {
                    java.util.List<String> subs = new java.util.ArrayList<>();
                    for (var ch : dispatcher.getRoot().getChild("sbc").getChildren()) subs.add(ch.getName());
                    for (String l : dev.zmuzy.sbc.core.Commands.helpLines(subs)) c.getSource().sendFeedback(Component.literal(l));
                    return 1;
                }))
                .executes(c -> {
                    openNextTick = true;
                    return 1;
                })
                .then(literal("learn").then(argument("task", StringArgumentType.word()).executes(c -> {
                    String id = StringArgumentType.getString(c, "task");
                    TaskDef t = Routine.find(id);
                    if (t == null) {
                        c.getSource().sendFeedback(Component.literal("§c[SBC] Unknown task id: " + id));
                        return 0;
                    }
                    Routine.learn(id);
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fDo \"" + t.name() + "\" now - the next game message becomes its trigger."));
                    return 1;
                })))
                .then(literal("config").executes(c -> {
                    Minecraft.getInstance().execute(() -> dev.zmuzy.sbc.ui.screens.SettingsScreen.open(null));
                    return 1;
                }))
                .then(literal("settings").executes(c -> {
                    Minecraft.getInstance().execute(() -> dev.zmuzy.sbc.ui.screens.SettingsScreen.open(null));
                    return 1;
                }))
                .then(literal("wiki").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null) return 0;
                    var st = mc.player.getMainHandItem();
                    String id = dev.zmuzy.sbc.core.Gain.sbId(st);
                    String name = id.isEmpty() ? GameState.strip(st.getHoverName().getString()) : dev.zmuzy.sbc.core.ItemRepo.name(id);
                    if (name.isBlank()) {
                        Toasts.push("Wiki", "Hold an item first", Ui.ORANGE);
                        return 0;
                    }
                    String url = dev.zmuzy.sbc.web.WebPage.WIKI + "/w/" + name.trim().replace(' ', '_');
                    // the in game browser takes over here when its on (OpenUriMixin)
                    mc.execute(() -> com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url)));
                    return 1;
                }))
                .then(literal("perf").then(literal("record").executes(c -> record(60))
                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, Integer>argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 600))
                                .executes(c -> record(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds")))))
                        .then(literal("stop").executes(c -> {
                            if (dev.zmuzy.sbc.core.FrameProbe.recording()) Minecraft.getInstance().execute(() -> {
                                dev.zmuzy.sbc.core.FrameProbe.stop();
                                dev.zmuzy.sbc.core.FrameReport.writeNow();
                            });
                            return 1;
                        })).executes(c -> {
                    // turning it off keeps what it measured in perf-report.txt, to send with a low fps report
                    if (Config.I.perfOverlay) {
                        try {
                            java.util.List<String> lines = new java.util.ArrayList<>(dev.zmuzy.sbc.core.Perf.report(60));
                            lines.add(1, dev.zmuzy.sbc.ui.Ui.elements + " gui elements in the last frame from this mod, HUD as a picture " + dev.zmuzy.sbc.ui.HudTexture.share() + ", replayed " + dev.zmuzy.sbc.ui.HudReplay.share() + " of frames");
                            java.nio.file.Files.write(Config.DIR.resolve("perf-report.txt"), lines);
                            dev.zmuzy.sbc.core.PerfSampler.stop();
                        } catch (java.io.IOException e) {
                            LOG.warn("Could not save the performance report", e);
                        }
                    }
                    Config.I.perfOverlay = !Config.I.perfOverlay;
                    Config.save();
                    Toasts.push("Performance overlay", Config.I.perfOverlay ? "On" : "Off - saved to perf-report.txt and perf-sample.txt", Ui.GREEN);
                    return 1;
                }))
                .then(literal("dump").executes(c -> {
                    Minecraft.getInstance().execute(() -> {
                        try {
                            var f = dev.zmuzy.sbc.core.Dump.write(Minecraft.getInstance());
                            Toasts.push("Dump written", f.getFileName().toString() + " in config/sbcompanion/dumps", Ui.GREEN);
                        } catch (Exception e) {
                            LOG.warn("dump failed", e);
                            Toasts.push("Dump failed", String.valueOf(e.getMessage()), Ui.RED);
                        }
                    });
                    return 1;
                }))
                .then(literal("hudedit").executes(c -> {
                    Minecraft.getInstance().execute(dev.zmuzy.sbc.ui.screens.HudEditorScreen::open);
                    return 1;
                }))
                .then(literal("storage").executes(c -> {
                    Minecraft.getInstance().execute(dev.zmuzy.sbc.ui.storage.StorageOverlayScreen::open);
                    return 1;
                }))
                .then(dev.zmuzy.sbc.nav.Destinations.command("nav"))
                .then(literal("route")
                        .then(literal("set").then(argument("set", StringArgumentType.word()).suggests((c, b) -> {
                            for (String s : dev.zmuzy.sbc.dungeon.Routes.SETS) b.suggest(s);
                            return b.buildFuture();
                        }).executes(c -> {
                            String s = StringArgumentType.getString(c, "set");
                            boolean ok = dev.zmuzy.sbc.dungeon.Routes.SETS.contains(s);
                            if (ok) {
                                Config.I.routeSet = s;
                                Config.save();
                            }
                            c.getSource().sendFeedback(Component.literal(ok ? "\u00a7b[SBC] \u00a7fRoute set: \u00a7e" + s : "\u00a7c[SBC] Sets: fow, 3ppopka, pearl, normal"));
                            return ok ? 1 : 0;
                        })))
                        .then(literal("next").executes(c -> {
                            var r = dev.zmuzy.sbc.dungeon.Dungeon.current();
                            if (r != null) r.step++;
                            return 1;
                        }))
                        .then(literal("prev").executes(c -> {
                            var r = dev.zmuzy.sbc.dungeon.Dungeon.current();
                            if (r != null && r.step > 0) r.step--;
                            return 1;
                        }))
                        .then(literal("reset").executes(c -> {
                            var r = dev.zmuzy.sbc.dungeon.Dungeon.current();
                            if (r != null) r.step = 0;
                            return 1;
                        })))
                .then(literal("dye").then(argument("piece", StringArgumentType.word()).suggests((c, b) -> {
                    for (String s : dev.zmuzy.sbc.item.ArmorDye.PIECES) b.suggest(s);
                    b.suggest("all");
                    return b.buildFuture();
                }).then(argument("colour", StringArgumentType.word()).executes(c -> {
                    c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7f" + dev.zmuzy.sbc.item.ArmorDye.set(StringArgumentType.getString(c, "piece"), StringArgumentType.getString(c, "colour"))));
                    return 1;
                }))))
                .then(literal("found").then(literal("reset").executes(c -> {
                    c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7fForgot " + dev.zmuzy.sbc.nav.Collectibles.reset() + " collected markers"));
                    return 1;
                })))
                .then(literal("copy").executes(c -> {
                    int n = dev.zmuzy.sbc.chat.ChatFilters.copyRecent(10);
                    c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7fCopied the last " + n + " chat lines"));
                    return 1;
                }))
                .then(literal("backup").executes(c -> {
                    String n = dev.zmuzy.sbc.core.Backups.make();
                    c.getSource().sendFeedback(Component.literal(n != null ? "\u00a7b[SBC] \u00a7fBackup saved: \u00a7e" + n : "\u00a7c[SBC] Backup failed"));
                    return n != null ? 1 : 0;
                }))
                .then(literal("rules").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new dev.zmuzy.sbc.ui.ChatRulesScreen(null)));
                    return 1;
                }))
                .then(literal("colors").executes(c -> {
                    dev.zmuzy.sbc.chat.ChatExtras.colors();
                    return 1;
                }))
                .then(literal("rule")
                        .then(literal("add").then(argument("action", StringArgumentType.word()).then(argument("pattern", StringArgumentType.greedyString()).executes(c -> {
                            String msg = dev.zmuzy.sbc.chat.ChatFilters.add(StringArgumentType.getString(c, "action"), StringArgumentType.getString(c, "pattern"));
                            c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7f" + msg));
                            return 1;
                        }))))
                        .then(literal("remove").then(argument("number", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(c -> {
                            c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7f" + dev.zmuzy.sbc.chat.ChatFilters.remove(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "number"))));
                            return 1;
                        })))
                        .then(literal("list").executes(c -> {
                            var l = dev.zmuzy.sbc.chat.ChatFilters.list();
                            if (l.isEmpty()) c.getSource().sendFeedback(Component.literal("\u00a77[SBC] No rules - /sbc rule add hide <pattern>"));
                            for (String s : l) c.getSource().sendFeedback(Component.literal("\u00a77" + s));
                            return 1;
                        })))
                .then(literal("wp").executes(c -> {
                            var mc = Minecraft.getInstance();
                            mc.execute(() -> mc.gui.setScreen(new dev.zmuzy.sbc.commands.WaypointsScreen(mc.gui.screen())));
                            return 1;
                        })
                        .then(literal("next").executes(c -> dev.zmuzy.sbc.nav.Waypoints.step(1)))
                        .then(literal("previous").executes(c -> dev.zmuzy.sbc.nav.Waypoints.step(-1)))
                        .then(literal("first").executes(c -> dev.zmuzy.sbc.nav.Waypoints.first()))
                        .then(literal("at").then(argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).then(argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).then(argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            int wx = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x"), wy = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "y"), wz = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z");
                            dev.zmuzy.sbc.nav.Waypoints.add("My waypoints", name, GameState.island, new net.minecraft.world.phys.Vec3(wx + 0.5, wy, wz + 0.5));
                            c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7fWaypoint \u00a7e" + name + "\u00a7f added at " + wx + " " + wy + " " + wz));
                            return 1;
                        }))))))
                        .then(literal("add").then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            var mc = Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            String name = StringArgumentType.getString(c, "name");
                            dev.zmuzy.sbc.nav.Waypoints.add("My waypoints", name, GameState.island, mc.player.position());
                            c.getSource().sendFeedback(Component.literal("\u00a7b[SBC] \u00a7fWaypoint \u00a7e" + name + "\u00a7f saved here"));
                            return 1;
                        })))
                        .then(literal("remove").then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            boolean ok = dev.zmuzy.sbc.nav.Waypoints.remove(StringArgumentType.getString(c, "name"));
                            c.getSource().sendFeedback(Component.literal(ok ? "\u00a7b[SBC] \u00a7fWaypoint removed" : "\u00a7c[SBC] No such waypoint"));
                            return ok ? 1 : 0;
                        })))
                        .then(literal("go").then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            boolean ok = dev.zmuzy.sbc.nav.Waypoints.go(StringArgumentType.getString(c, "name"));
                            c.getSource().sendFeedback(Component.literal(ok ? "\u00a7b[SBC] \u00a7fNavigating" : "\u00a7c[SBC] No such waypoint"));
                            return ok ? 1 : 0;
                        })))
                        .then(literal("list").executes(c -> {
                            var all = dev.zmuzy.sbc.nav.Waypoints.all();
                            if (all.isEmpty()) c.getSource().sendFeedback(Component.literal("\u00a77[SBC] No waypoints yet - /sbc wp add <name>"));
                            for (var p : all) c.getSource().sendFeedback(Component.literal("\u00a7b" + p.group() + " \u00a78\u00b7 \u00a7f" + p.name() + " \u00a77" + GameState.pretty(p.island())));
                            return 1;
                        }))
                        .then(literal("clear").executes(c -> {
                            dev.zmuzy.sbc.nav.Waypoints.clear("My waypoints");
                            return 1;
                        }))
                        .then(literal("share").executes(c -> {
                            String t = dev.zmuzy.sbc.nav.Waypoints.export("My waypoints");
                            if (!t.isEmpty()) Minecraft.getInstance().keyboardHandler.setClipboard(t);
                            c.getSource().sendFeedback(Component.literal(t.isEmpty() ? "\u00a7c[SBC] Nothing to share" : "\u00a7b[SBC] \u00a7fCopied - paste it into /sbc wp import on another PC"));
                            return 1;
                        }))
                        .then(literal("import").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            int n = dev.zmuzy.sbc.nav.Waypoints.importText(StringArgumentType.getString(c, "text"));
                            c.getSource().sendFeedback(Component.literal(n < 0 ? "\u00a7c[SBC] That is not a waypoint text" : "\u00a7b[SBC] \u00a7fImported " + n + " waypoint(s)"));
                            return n < 0 ? 0 : 1;
                        }))))
                .then(literal("stop").executes(c -> {
                    Navigator.stop();
                    return 1;
                }))
                .then(literal("profile").executes(c -> {
                    Profile.loadSelf();
                    return 1;
                }))));
        LOG.info("SkyBlock Companion loaded");
        Safe.commands("SbcMod.commands#3", (dispatcher, registry) -> dev.zmuzy.sbc.core.Commands.registerAliases(dispatcher));
    }

    private Object lastLevel;

    private void tick(Minecraft mc) {
        ticks++;
        while (openKey.consumeClick()) {
            if (mc.gui.screen() != null) continue;
            // alt+K opens the small mission card, K alone the full menu
            boolean alt = InputConstants.isKeyDown(InputConstants.KEY_LALT)
                    || InputConstants.isKeyDown(InputConstants.KEY_RALT);
            mc.gui.setScreen(alt ? new dev.zmuzy.sbc.ui.MissionScreen() : new CompanionScreen());
        }
        if (openNextTick && mc.gui.screen() == null) {
            openNextTick = false;
            mc.gui.setScreen(new CompanionScreen());
        }
        if (DEV) devTick(mc);

        // one part failing must not take the rest of the tick with it or put all of it to rest
        if (ticks % 5 == 0) {
            Safe.run("GameState.update", () -> GameState.update(mc));
            Safe.run("Navigator.tick", () -> Navigator.tick(mc));
        }
        if (ticks % 10 == 0) Safe.run("Tracker.tick", () -> Tracker.tick(mc));
        if (ticks % 20 == 0) {
            Safe.run("CookieBuff.tick", () -> dev.zmuzy.sbc.core.CookieBuff.tick(mc));
            Safe.run("GrowthStage.tick", () -> dev.zmuzy.sbc.garden.GrowthStage.tick(mc));
        }
        Safe.run("Watcher.tick", () -> Watcher.tick(mc));
        Safe.run("Features.tick", () -> dev.zmuzy.sbc.feature.Features.tick(mc));
        Safe.run("ChatBlocks.tick", dev.zmuzy.sbc.chat.ChatBlocks::tick);
        Safe.run("Dps.tick", () -> dev.zmuzy.sbc.track.Dps.tick(mc));
        Safe.run("Guide.tick", () -> dev.zmuzy.sbc.tasks.Guide.tick(mc));
        Safe.run("MinionCatalog.tick", () -> dev.zmuzy.sbc.minion.MinionCatalog.tick(mc));
        // hypixels server pack shows up a bit after joining so look for its item models again now and then
        if (mc.level != lastLevel) {
            onWorldChange();
            Watcher.onWorldChange();
        }
        if (mc.level != lastLevel || ticks % 600 == 0) {
            lastLevel = mc.level;
            dev.zmuzy.sbc.core.ItemIcons.reset();
        }
        if (ticks % 20 == 0) Safe.run("ItemIcons.noteSeen", () -> dev.zmuzy.sbc.core.ItemIcons.noteSeen(mc));
        while (hudEditKey.consumeClick()) if (mc.gui.screen() == null) dev.zmuzy.sbc.ui.screens.HudEditorScreen.open();
        dev.zmuzy.sbc.item.SlotLocks.settleWithOtherMod(mc, lockKey);
        if (Config.keysToMove) {
            Config.keysToMove = false;
            // protection has V now, the radial menu moves off it if thats where it was saved
            if (radialKey.saveString().equals("key.keyboard.v")) {
                radialKey.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_U));
                KeyMapping.resetMapping();
                mc.options.save();
            }
        }
        while (protectKey.consumeClick()) {
            // only over a slot in a screen, the key event above handles that
        }
        // L in the world: lock the held hotbar slot (from skyblocker HotbarSlotLock.handleInputEvents)
        while (lockKey.consumeClick()) if (mc.gui.screen() == null && dev.zmuzy.sbc.feature.Features.on("slotlocks")) dev.zmuzy.sbc.item.SlotLocks.toggleHotbar(mc.player);
        while (hoppityLiveKey.consumeClick()) dev.zmuzy.sbc.events.hoppity.HoppitySummary.toggle();
        while (dianaWarpKey.consumeClick()) if (mc.gui.screen() == null) dev.zmuzy.sbc.events.diana.BurrowWarpHelper.onKeyPress();
        while (corpseShareKey.consumeClick()) if (mc.gui.screen() == null) dev.zmuzy.sbc.mining.Mineshaft.shareNearest();
        while (rareMobShareKey.consumeClick()) if (mc.gui.screen() == null) dev.zmuzy.sbc.events.diana.RareMobWaypointShare.onKeyPress();
        while (vanquisherShareKey.consumeClick()) if (mc.gui.screen() == null) dev.zmuzy.sbc.crimson.VanquisherWaypointShare.sendSpawn();
        while (mineshaftTypeKey.consumeClick()) if (mc.gui.screen() == null) dev.zmuzy.sbc.mining.Mineshaft.sendType();
        if (ticks % 40 == 0) {
            dev.zmuzy.sbc.events.diana.Diana.boundKeyName = dianaWarpKey.isUnbound() ? "" : dianaWarpKey.getTranslatedKeyMessage().getString();
            dev.zmuzy.sbc.crimson.VanquisherWaypointShare.keyName = vanquisherShareKey.isUnbound() ? "the share key" : vanquisherShareKey.getTranslatedKeyMessage().getString();
            dev.zmuzy.sbc.mining.Mineshaft.typeKeyName = mineshaftTypeKey.isUnbound() ? "the share key" : mineshaftTypeKey.getTranslatedKeyMessage().getString();
            dev.zmuzy.sbc.mining.Mineshaft.corpseKeyName = corpseShareKey.isUnbound() ? "the share key" : corpseShareKey.getTranslatedKeyMessage().getString();
        }
        while (marketKey.consumeClick()) if (mc.gui.screen() == null && dev.zmuzy.sbc.feature.Features.on("market_search")) mc.gui.setScreen(new dev.zmuzy.sbc.ui.MarketSearchScreen(null, 0));
        while (storageKey.consumeClick()) if (mc.gui.screen() == null && dev.zmuzy.sbc.feature.Features.on("storage_overlay")) dev.zmuzy.sbc.ui.storage.StorageOverlayScreen.open();
        while (mapKey.consumeClick()) if (mc.gui.screen() == null) {
            // straight to the map of your island
            CompanionScreen.devViewIsland(GameState.island);
            CompanionScreen.page = 9;
            mc.gui.setScreen(new CompanionScreen());
        }
        while (warpKey.consumeClick()) if (mc.gui.screen() == null && !Navigator.warpNow() && Navigator.target() != null) Toasts.push("Already here", "The target is on this island", Ui.MUTED);
        while (zoomIn.consumeClick()) setRadius(Config.I.minimapRadius * 4 / 5);
        while (zoomOut.consumeClick()) setRadius(Config.I.minimapRadius * 5 / 4);
        if (ticks % 20 == 0) {
            if (GameState.onHypixel || DEV) {
                Safe.run("Prices.tick", Prices::tick);
                Safe.run("AuctionPrices.tick", dev.zmuzy.sbc.core.AuctionPrices::tick);
                Safe.run("SoldPrices.tick", dev.zmuzy.sbc.core.SoldPrices::tick);
                Safe.run("Profile.refresh", dev.zmuzy.sbc.core.Profile::refreshTick);
                Safe.run("SkyHelperPrices.tick", dev.zmuzy.sbc.core.SkyHelperPrices::tick);
                Safe.run("Mayor.tick", Mayor::tick);
            }
            Safe.run("Routine.tickAlerts", Routine::tickAlerts);
            if (GameState.inSkyBlock && !wasInSkyBlock) onJoinSkyBlock();
            wasInSkyBlock = GameState.inSkyBlock;
        }
        if (ticks % 1200 == 0) Store.saveIfDirty();
    }

    private static void setRadius(int r) {
        Config.I.minimapRadius = Math.max(16, Math.min(256, r));
        Config.save();
        Toasts.push("Minimap zoom", Config.I.minimapRadius + " blocks to the edge", Ui.CYAN);
    }

    private static int worldTicks;

    // dev only check in a local world: open the menu once loaded and flip through every page for screenshots
    private static void devTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        worldTicks++;
        dev.zmuzy.sbc.core.Vitals.devFill();
        if (HUD_CHECK && worldTicks == 220) {
            try {
                net.minecraft.client.Screenshot.grab(mc, false);
            } catch (Throwable t) {
                LOG.warn("screenshot failed", t);
            }
        }
        if (worldTicks == 100 && !HUD_CHECK) mc.gui.setScreen(new CompanionScreen());
        if (CYCLE && CompanionScreen.page == 9 && worldTicks % 100 == 50) CompanionScreen.devViewIsland("HUB");
        if (CYCLE && worldTicks > 100 && worldTicks % 100 == 0 && dev.zmuzy.sbc.ui.screens.LegacyHost.unwrap(mc.gui.screen()) instanceof CompanionScreen && CompanionScreen.page < CompanionScreen.TABS.length - 1)
            CompanionScreen.page++;
    }

    private static void onJoinSkyBlock() {
        long now = System.currentTimeMillis();
        // the game went straight into a server and never showed the title screen
        Minecraft game = Minecraft.getInstance();
        if (game.gui.screen() == null) dev.zmuzy.sbc.ui.screens.UpdateScreen.maybeShow(game, null);
        if (Config.I.loginBriefing && now - Store.I.lastLoginBriefing > 30 * 60_000) {
            Store.I.lastLoginBriefing = now;
            Hud.showBriefing();
        }
        if (Profile.serverUp() && Profile.current == null) Profile.loadSelf();
        // auto routine: start guiding once a day when you join skyblock with dailies still open
        String day = dev.zmuzy.sbc.core.SbTime.periodKey(dev.zmuzy.sbc.core.SbTime.Period.DAILY, now);
        int[] c = dev.zmuzy.sbc.tasks.Routine.counts();
        if (Config.I.autoGuide && !dev.zmuzy.sbc.tasks.Guide.active && !day.equals(Store.I.autoGuidedDay) && c[0] < c[1]) {
            Store.I.autoGuidedDay = day;
            dev.zmuzy.sbc.tasks.Guide.start();
            Toasts.push("Auto routine started", "Follow the line · Alt+K for the mission card", Ui.GREEN);
        } else Toasts.push("SkyBlock Companion", "K = menu · Alt+K = current mission", Ui.VIOLET);
    }

    // /sbc perf record, the report goes to config/sbcompanion/perf
    private static int record(int seconds) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (dev.zmuzy.sbc.core.FrameProbe.recording()) return;
            dev.zmuzy.sbc.core.FrameProbe.start(seconds);
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[SBC] §7Recording §f" + seconds + " §7seconds of frames. Play as usual; §f/sbc perf stop §7ends it early."));
        });
        return 1;
    }

    private static void onMessage(Component message, boolean overlay) {
        String plain = GameState.strip(message.getString());
        if (overlay) return; // the action bar gets read in ChatHooks before any filtering
        lastServerMessage = System.currentTimeMillis();
        handle(plain);
    }

    // last chat message from the server + last world join
    private static long lastServerMessage, joinedAt;
    private static int burst;
    private static long burstStart, mutedUntil;

    // every line added to the chat box (see ChatComponentMixin), this also sees lines other mods rewrite like
    // skyhannis compact visitor message. chat history mods paste thousands of old lines, those get muted
    public static void onChatLine(Component message) {
        long now = System.currentTimeMillis();
        if (now - burstStart > 250) {
            burstStart = now;
            burst = 0;
        }
        if (++burst > 25) mutedUntil = now + 3000; // a restore pastes hundreds of lines at once
        if (now < mutedUntil || now - joinedAt < 15_000 || now - lastServerMessage > 1500) return;
        handle(GameState.strip(message.getString()));
    }

    // same gate as onChatLine. true = npc talk now shown as a dialogue that should stay out of chat
    public static boolean consumeChat(Component message) {
        long now = System.currentTimeMillis();
        if (now < mutedUntil || now - joinedAt < 15_000) return false;
        return dev.zmuzy.sbc.ui.Dialogue.onMessage(message);
    }

    // world changed (joining a server, switching islands)
    public static void onWorldChange() {
        joinedAt = System.currentTimeMillis();
    }

    private static final java.util.Map<String, Long> recent = new java.util.LinkedHashMap<>();

    // the network event + the chat box hook both deliver the same line, handle each once
    private static void handle(String plain) {
        long now = System.currentTimeMillis();
        recent.values().removeIf(t -> now - t > 800);
        for (String line : plain.split("\n")) {
            if (line.isBlank() || recent.containsKey(line)) continue;
            recent.put(line, now);
            // inside the chat packet: a throw would disconnect, and one reader failing must not cost the others the line
            Safe.run("GameState.onChat", () -> GameState.onChat(line));
            Safe.run("Routine.onChat", () -> Routine.onChat(line));
            Safe.run("Tracker.onChat", () -> Tracker.onChat(line));
            Safe.run("Features.chat", () -> dev.zmuzy.sbc.feature.Features.chat(line));
        }
    }

}
