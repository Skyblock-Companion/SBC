// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Commands;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.SlotLocks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;


import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// the small commands. from skyhanni SendCoordinatedCommand + WikiManager, skyblocker ChatPositionShare,
// NightVisionCommand, GameRendererMixin, DisableAll
// also ItemProtections command and the waypoints screen command
public final class MoreCommands {
    public static final String NIGHT_VISION = "night_vision_strength", WIKI_MENUS = "wiki_menus";

    private MoreCommands() {}

    private static void say(String text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    // from skyhanni LorenzVec.toChatFormat, "x: 1, y: 2, z: 3"
    static String chatFormat(Vec3 pos) {
        return "x: " + (int) Math.floor(pos.x) + ", y: " + (int) Math.floor(pos.y) + ", z: " + (int) Math.floor(pos.z);
    }

    private static int sendCoords(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return 0;
        mc.getConnection().sendChat(chatFormat(mc.player.position()) + (message.isEmpty() ? "" : " " + message));
        return 1;
    }

    static String searchUrl(String search) {
        return io.github.zmuzy.sbc.web.WebPage.wikiSearchUrl(search);
    }

    private static int wiki(String search) {
        Minecraft mc = Minecraft.getInstance();
        String url = search.isBlank() ? io.github.zmuzy.sbc.web.WebPage.WIKI : searchUrl(search);
        mc.execute(() -> com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url)));
        return 1;
    }

    // from skyhanni WikiManager.wikiThisItem
    private static int wikiThis() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        var stack = mc.player.getMainHandItem();
        String id = Gain.sbId(stack);
        String name = id.isEmpty() ? GameState.strip(stack.getHoverName().getString()) : ItemRepo.name(id);
        if (stack.isEmpty() || name.isBlank()) {
            say("§cYou must be holding an item to use this command!");
            return 0;
        }
        return wiki(GameState.strip(name));
    }

    // from skyblocker GameRendererMixin.onGetNightVisionStrength, full night vision at this strength
    public static float nightVisionScale(float original) {
        if (original != 1f || !GameState.inSkyBlock) return original;
        Feature f = Features.get(NIGHT_VISION);
        if (f == null || !f.active()) return original;
        return Math.clamp(f.option(NIGHT_VISION + ".strength").integer() / 100f, 0f, 1f);
    }

    private static int nightVision(int strength) {
        Feature f = Features.get(NIGHT_VISION);
        f.option(NIGHT_VISION + ".strength").set(strength);
        Config.save();
        say("Night Vision strength is now " + strength + "%.");
        return 1;
    }

    // switches off every feature that has a switch. /sbc reset brings them back
    private static int disableAll() {
        for (Feature f : Features.all()) {
            if (f.alwaysOn) continue;
            if (f.bound()) f.field(false);
            else Config.I.features.put(f.id, false);
        }
        Config.save();
        say("Every feature is off. §e/sbc reset §fturns them back on.");
        return 1;
    }

    // the one reset for relic markers + the relic route list. relicsreset and relicsfoundall land here too
    public static int relics(boolean found) {
        int n = io.github.zmuzy.sbc.nav.Collectibles.mark("col_relic", found);
        io.github.zmuzy.sbc.misc.RelicRoute.setAll(found);
        say(n == 0 ? (found ? "Marked the Relic route as found. Go to Spider's Den to mark the markers too." : "Reset the Relic route. Go to Spider's Den to reset the markers too.") : found ? "Marked " + n + " relics as found." : "Reset " + n + " relics.");
        return 1;
    }

    // from skyhanni BetterWikiFromMenus.onSlotClick: the skyblock wiki menus 2 commands, and with the setting on,
    // items in the skyblock guide
    private static boolean onSlotClick(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button) {
        if (slot == null || !Features.on(WIKI_MENUS)) return false;
        var stack = slot.getItem();
        if (stack.isEmpty()) return false;
        String name = stack.getHoverName().getString();
        if (GameState.strip(screen.getTitle().getString()).equals("SkyBlock Wiki")) {
            if (slotId == 11 && name.contains("Wiki Command")) wiki("");
            else if (slotId == 15 && name.contains("Wikithis Command")) wikiThis();
            return false;
        }
        if (!Features.get(WIKI_MENUS).option("wiki_menus.guide").bool()) return false;
        if (!io.github.zmuzy.sbc.core.LegacyText.lore(stack).contains("§7§eClick to view on the SkyBlock Wiki!")) return false;
        wiki(GameState.strip(name).replace("✔ ", "").replace("✖ ", ""));
        return true;
    }

    private static int protectHeld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getMainHandItem().isEmpty()) {
            say("§cHold the item first.");
            return 0;
        }
        SlotLocks.protectItem(mc.player.getMainHandItem());
        return 1;
    }

    // help search, anything that names or describes it
    private static int helpSearch(String search) {
        String s = search.toLowerCase(java.util.Locale.ROOT);
        int[] n = {0};
        Commands.help().forEach((name, text) -> {
            if (name.contains(s) || text.toLowerCase(java.util.Locale.ROOT).contains(s)) {
                say("§e/sbc " + name + " §7- " + text);
                n[0]++;
            }
        });
        if (n[0] == 0) say("§7No commands found.");
        return 1;
    }

    public static void register() {
        Features.add(new Feature(NIGHT_VISION, "World", "Night Vision strength", "How bright a Night Vision effect makes the world, in SkyBlock. /sbc nightvision <0-100>, off or full.", "Skyblocker: Night Vision Strength")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.integer(NIGHT_VISION + ".strength", "Strength", "In percent. 100 is how the game draws it.", 100, 0, 100)));
        Features.add(new Feature(WIKI_MENUS, "Items", "The wiki from Hypixel's menus", "The commands of the SkyBlock Wiki menu open the page in the in-game browser.", "SkyHanni: Better /wiki from menus")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("wiki_menus.guide", "SkyBlock Guide too", "A click on an item of the Guide opens its wiki page instead of Hypixel's link.", false)));
        io.github.zmuzy.sbc.mc.Interactions.onSlotClick(MoreCommands::onSlotClick);
        Commands.describe("resetpets", "Forget the pets seen in your Pets menu");
        Commands.describe("relics", "Spider's Den relics: /sbc relics foundall, reset");
        Commands.describe("sendcoords", "Say where you stand in chat: /sbc sendcoords [text]");
        Commands.describe("wikithis", "Look up the item in your hand on the wiki");
        Commands.describe("waypoints", "See and change your waypoints");
        Commands.describe("nightvision", "Night Vision strength: /sbc nightvision <0-100>, off, full");
        Commands.describe("disableall", "Turn every feature off: /sbc disableall confirm");
        Commands.describe("protectitem", "Protect the item in your hand, or stop protecting it");
        Safe.commands("MoreCommands.commands", (d, r) -> d.register(literal("sbc")
                .then(literal("sendcoords").executes(c -> sendCoords(""))
                        .then(argument("message", StringArgumentType.greedyString()).executes(c -> sendCoords(StringArgumentType.getString(c, "message")))))
                .then(literal("wiki").then(argument("search", StringArgumentType.greedyString()).executes(c -> wiki(StringArgumentType.getString(c, "search")))))
                .then(literal("wikithis").executes(c -> wikiThis()))
                .then(literal("resetpets").executes(c -> {
                    // from skyhanni PetStorageCommands: forget what the pets menu showed, opening it again fills it back
                    io.github.zmuzy.sbc.core.Store.I.petsSeen.clear();
                    io.github.zmuzy.sbc.core.Store.markDirty();
                    say("Cleared the pets seen in your menu. Open /pets to read them again.");
                    return 1;
                }))
                .then(literal("relics")
                        .then(literal("foundall").executes(c -> relics(true)))
                        .then(literal("reset").executes(c -> relics(false))))
                .then(literal("help").then(argument("search", StringArgumentType.greedyString()).executes(c -> helpSearch(StringArgumentType.getString(c, "search")))))
                .then(literal("protectitem").executes(c -> protectHeld()))
                .then(literal("waypoints").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new WaypointsScreen(mc.gui.screen())));
                    return 1;
                }))
                .then(literal("nightvision")
                        .then(literal("off").executes(c -> nightVision(0)))
                        .then(literal("full").executes(c -> nightVision(100)))
                        .then(argument("strength", IntegerArgumentType.integer(0, 100)).executes(c -> nightVision(IntegerArgumentType.getInteger(c, "strength")))))
                .then(literal("disableall").executes(c -> {
                    say("§eThis turns every feature off. §f/sbc disableall confirm");
                    return 1;
                }).then(literal("confirm").executes(c -> disableAll())))));
    }
}
