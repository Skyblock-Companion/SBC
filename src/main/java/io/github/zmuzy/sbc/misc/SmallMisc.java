// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.misc;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.areas.FishHooks;
import io.github.zmuzy.sbc.areas.FishState;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// the small ones, from skyhanni features/misc: ChargeBottleNotification, LastServers, SkyBlockKickDuration,
// MovementSpeedDisplay, XPInInventories, RestorePieceOfWizardPortalLore, PocketSackInASackDisplay...
public final class SmallMisc {
    public static final String BOTTLE = "charge_bottle", LAST_SERVERS = "last_servers", KICK = "kick_duration", SPEED = "movement_speed", XP = "xp_in_inventory",
            PORTAL = "wizard_portal_lore", POCKET = "pocket_sack_lore", RESTART = "server_restart_title", COPY = "copy_playtime", ABIPHONE = "abiphone_key",
            DOUBLE = "fix_double_clicks", PARTICLES = "fix_colored_particles", CLOCK = "pc_clock_warning", MONTHS = "month_colors";
    // from skyhanni FixDoubleClicks.blazeDaggers
    static final Set<String> BLAZE_DAGGERS = Set.of("BURSTFIRE_DAGGER", "BURSTMAW_DAGGER", "FIREDUST_DAGGER", "HEARTFIRE_DAGGER", "HEARTMAW_DAGGER", "MAWDUST_DAGGER");
    private static final Set<String> EMPTY_BOTTLES = Set.of("THUNDER_IN_A_BOTTLE_EMPTY", "STORM_IN_A_BOTTLE_EMPTY", "HURRICANE_IN_A_BOTTLE_EMPTY");
    private static final Map<String, String> BOTTLES = Map.of("THUNDER_IN_A_BOTTLE", "Thunder in a Bottle", "STORM_IN_A_BOTTLE", "Storm in a Bottle", "HURRICANE_IN_A_BOTTLE", "Hurricane in a Bottle");
    private static final Patterns.P KICKED = Patterns.p("SH.misc.kickduration.kicked"), PROBLEM = Patterns.p("SH.misc.kickduration.problemjoining"),
            RESTARTING = Patterns.p("SH.features.misc.serverrestart.time"), RING = Patterns.p("SH.misc.abiphone.call.ring"), EARNED = Patterns.p("SH.misc.restore.wizard.portal.earned"),
            XP_LEVELS = Patterns.p("SH.misc.xp-in-inventory.exp-levels.new.0"), XP_START = Patterns.p("SH.misc.xp-in-inventory.exp-levels.new.1");
    public static final String PLAYTIME_MENU = "Detailed /playtime";

    private static KeyMapping abiphoneKey;
    private static String acceptUuid;
    private static long acceptAt;
    private static long lastBottle;
    private static String lastServerId;
    private static final Map<String, Long> lastServers = new HashMap<>();
    private static boolean kickMessage, showKick, kickWarned;
    private static long kickAt;
    private static long lastRestartTitle;
    private static double speed;
    private static long clockWarnedAt;
    private SmallMisc() {}

    private static int intOpt(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.integer();
    }

    static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    // "1h 2m 3s" like skyhannis Duration.format
    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60;
        s %= 60;
        StringBuilder out = new StringBuilder();
        if (d > 0) out.append(d).append("d ");
        if (h > 0) out.append(h).append("h ");
        if (m > 0) out.append(m).append("m ");
        if (s > 0 || out.isEmpty()) out.append(s).append("s");
        return out.toString().trim();
    }

    // --- skyhanni ChargeBottleNotification ---

    // names of the full bottles unless an empty one is still charging, empty = nothing to say
    static List<String> fullBottles(List<String> ids) {
        Set<String> full = new LinkedHashSet<>();
        for (String id : ids) {
            if (EMPTY_BOTTLES.contains(id)) return List.of();
            String name = BOTTLES.get(id);
            if (name != null) full.add(name);
        }
        return new ArrayList<>(full);
    }

    private static void bottles(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastBottle < 10_000 || mc.player == null || !FishState.fishing()) return;
        lastBottle = now;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            String id = Gain.sbId(mc.player.getInventory().getItem(i));
            if (!id.isEmpty()) ids.add(id);
        }
        List<String> full = fullBottles(ids);
        if (!full.isEmpty()) say("You are fishing, but " + String.join(", ", full) + (full.size() == 1 ? " is full" : " are full"));
    }

    // --- skyhanni LastServers ---

    private static long lastServerCheck;

    private static void servers() {
        long now = System.currentTimeMillis();
        if (now - lastServerCheck < 1000) return;
        lastServerCheck = now;
        String id = GameState.serverId();
        if (id.isEmpty()) return;
        if (id.equals(lastServerId)) {
            lastServers.put(id, now);
            return;
        }
        long keep = intOpt(LAST_SERVERS, "warnTime", 60) * 1000L;
        lastServers.values().removeIf(t -> now - t > keep);
        Long seen = lastServers.get(id);
        if (seen != null) say("§7You were already on this server §b" + time(now - seen) + " §7ago.");
        lastServerId = id;
        lastServers.put(id, now);
    }

    // --- skyhanni SkyBlockKickDuration ---

    private static void kicked() {
        kickMessage = false;
        showKick = true;
        kickAt = System.currentTimeMillis();
    }

    private static void notKicked() {
        showKick = false;
        kickWarned = false;
    }

    private static ChatBus.Result kickChat(ChatBus.Line line) {
        if (showKick) return ChatBus.PASS;
        String text = LegacyText.of(line.component());
        if (KICKED.matcher(text).matches()) {
            if (GameState.onHypixel && !GameState.inSkyBlock) kicked();
            else kickMessage = true;
        }
        if (PROBLEM.matcher(text).matches()) kicked();
        return ChatBus.PASS;
    }

    // outside skyblock theres no hud so it goes where the game has room: the action bar
    private static void kickTick(Minecraft mc) {
        if (kickMessage && GameState.onHypixel && !GameState.inSkyBlock) kicked();
        if (!showKick) return;
        long since = System.currentTimeMillis() - kickAt;
        if (GameState.inSkyBlock || since > 5 * 60_000L) {
            notKicked();
            return;
        }
        if (since > intOpt(KICK, "warnTime", 60) * 1000L && !kickWarned) {
            kickWarned = true;
            FishHooks.title("§eTry rejoining SkyBlock now!");
            FishHooks.pling();
        }
        if (mc.gui != null) mc.gui.hud.setOverlayMessage(Component.literal("§cKicked from SkyBlock §b" + time(since) + " ago"), false);
    }

    // --- skyhanni MovementSpeedDisplay ---

    private static RichCard.Content speedCard(double bps) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Movement speed", String.format(Locale.ROOT, "%.2f", bps)).raw().brief("Speed")));
    }

    private static final HudElement SPEED_CARD = new RichCard(SPEED, "Movement speed", "HUD", Zone.TOP_LEFT, 62, SPEED) {
        @Override public boolean visible() {
            return GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV;
        }

        @Override protected Content live() {
            return speedCard(speed);
        }

        @Override protected Content sample() {
            return speedCard(7.43);
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("Movement speed", String.format(Locale.ROOT, "%.2f", speed), Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Movement speed", "7.43", Theme.current().text()));
        }
    };

    private static int speedTick;

    private static void speedTick(Minecraft mc) {
        if (++speedTick % 5 != 0 || mc.player == null) return;
        // distance from the last tick times ticks per sec
        Vec3 now = mc.player.position();
        speed = new Vec3(mc.player.xOld, mc.player.yOld, mc.player.zOld).distanceTo(now) * 20;
    }

    // --- tooltips ---

    // from skyhanni XPInInventories: index of the line with a level cost + that cost, or null
    static int[] xpCost(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = XP_LEVELS.matcher(lines.get(i));
            if (!m.matches()) m = XP_START.matcher(lines.get(i));
            if (m.matches()) return new int[]{i, Integer.parseInt(m.group("xp"))};
        }
        return null;
    }

    private static List<String> plain(List<Component> lines) {
        List<String> out = new ArrayList<>(lines.size());
        for (Component c : lines) out.add(c.getString());
        return out;
    }

    private static void xpTooltip(ItemStack stack, List<Component> lines) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        int[] cost = xpCost(plain(lines));
        if (cost == null) return;
        lines.add(cost[0] + 1, Component.literal("§7Your XP: " + (p.experienceLevel >= cost[1] ? "§a" : "§c") + p.experienceLevel));
    }

    private static void portalTooltip(ItemStack stack, List<Component> lines) {
        if (!"WIZARD_PORTAL_MEMENTO".equals(Gain.sbId(stack))) return;
        for (Component c : lines) if (EARNED.matcher(c.getString()).matches()) return;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        String recipient = data == null ? "" : data.copyTag().getStringOr("recipient_name", "");
        if (!recipient.isEmpty()) lines.add(Math.min(5, lines.size()), Component.literal("§7Earned by: " + recipient));
    }

    // from skyhanni PocketSackInASackDisplay replaced lore, the 2 lines of hypixels sentence
    static boolean pocketLore(List<Component> lines, int applied) {
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (!lines.get(i).getString().contains("This sack is")) continue;
            String color = applied == 3 ? "§a" : "§b";
            lines.set(i, Component.literal("§7This sack is stitched " + color + applied + "§7/" + color + "3"));
            lines.set(i + 1, Component.literal("§7times with a §cPocket Sack-in-a-Sack§7."));
            return true;
        }
        return false;
    }

    private static void pocketTooltip(ItemStack stack, List<Component> lines) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !Gain.sbId(stack).endsWith("_SACK")) return;
        int applied = data.copyTag().getIntOr("sack_pss", 0);
        if (applied > 0) pocketLore(lines, applied);
    }

    private static String menu() {
        var s = Minecraft.getInstance().gui.screen();
        return s == null ? "" : GameState.strip(s.getTitle().getString());
    }

    private static void copyTooltip(ItemStack stack, List<Component> lines) {
        if (!PLAYTIME_MENU.equals(menu()) || io.github.zmuzy.sbc.item.menus.MenuMarks.hoveredSlot() != 4) return;
        lines.add(Component.empty());
        lines.add(Component.literal("§eClick to Copy!"));
    }

    private static boolean copyClick(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button) {
        if (!Features.on(COPY) || slotId != 4 || button != 0 || slot == null || !PLAYTIME_MENU.equals(GameState.strip(screen.getTitle().getString()))) return false;
        Minecraft mc = Minecraft.getInstance();
        List<String> text = new ArrayList<>();
        text.add((mc.player == null ? "" : mc.player.getName().getString()) + "'s - " + GameState.profileName + " Playtime Stats");
        for (String l : LegacyText.lore(slot.getItem())) text.add(GameState.strip(l));
        mc.keyboardHandler.setClipboard(String.join("\n", text));
        say("Copied playtime stats into clipboard.");
        return true;
    }

    // --- skyhanni ServerRestartTitle ---

    // time till the restart a sidebar line says, or -1
    static long restartMs(List<String> sidebar) {
        for (String l : sidebar) {
            Matcher m = RESTARTING.matcher(l);
            if (m.matches()) return (Integer.parseInt(m.group("minutes")) * 60L + Integer.parseInt(m.group("seconds"))) * 1000;
        }
        return -1;
    }

    // skyhanni keeps one title up the whole wait. here it comes every 30 sec and only counts down in the last 2
    // min so the screen stays free till then
    private static void restartTick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastRestartTitle < 1000) return;
        long left = restartMs(GameState.scoreboardFormatted);
        if (left < 0) return;
        long seconds = left / 1000;
        if (seconds > 120 && seconds % 30 != 0) return;
        lastRestartTitle = now;
        if (mc.gui == null) return;
        mc.gui.hud.setTimes(0, 30, 5);
        mc.gui.hud.setTitle(Component.literal("§cServer Restart in §b" + time(left)));
        mc.gui.hud.setSubtitle(Component.empty());
    }

    // --- skyhanni AbiphoneFeatures ---

    // from skyhanni readPickupUuid: the id in the /cb command of the [PICK UP] part
    static String pickupUuid(Component c) {
        ClickEvent click = c.getStyle().getClickEvent();
        if (click instanceof ClickEvent.RunCommand run && run.command().toLowerCase(Locale.ROOT).startsWith("/cb ")) return run.command().substring(4).trim().toLowerCase(Locale.ROOT);
        for (Component s : c.getSiblings()) {
            String id = pickupUuid(s);
            if (id != null) return id;
        }
        return null;
    }

    private static ChatBus.Result ringChat(ChatBus.Line line) {
        if (acceptUuid == null && RING.matcher(LegacyText.of(line.component())).matches()) {
            acceptUuid = pickupUuid(line.component());
            acceptAt = System.currentTimeMillis();
        }
        return ChatBus.PASS;
    }

    private static void abiphoneTick(Minecraft mc) {
        if (acceptUuid != null && System.currentTimeMillis() - acceptAt > 20_000) acceptUuid = null;
        if (abiphoneKey == null) return;
        while (abiphoneKey.consumeClick()) {
            if (mc.gui.screen() != null || acceptUuid == null || mc.player == null) continue;
            mc.player.connection.sendCommand("cb " + acceptUuid);
            acceptUuid = null;
        }
    }

    public static void keys(KeyMapping.Category category) {
        abiphoneKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.abiphone", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    // --- skyhanni FixDoubleClicks, ColorParticleFix ---

    // the packet mixin calls this: the use on block part of a click hypixel would count twice. never on a block that
    // is meant to be used (a lever, a chest, a button, a secret's skull), or dungeon secrets could not be opened
    public static boolean dropUseOnBlock(net.minecraft.core.BlockPos at) {
        if (!GameState.inSkyBlock || !Features.on(DOUBLE)) return false;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || !(FishState.holdingRod || BLAZE_DAGGERS.contains(Gain.sbId(p.getMainHandItem())))) return false;
        return mc.level == null || at == null || !usable(mc.level.getBlockState(at));
    }

    static boolean usable(net.minecraft.world.level.block.state.BlockState b) {
        var block = b.getBlock();
        return block instanceof net.minecraft.world.level.block.LeverBlock || block instanceof net.minecraft.world.level.block.ChestBlock
                || block instanceof net.minecraft.world.level.block.ButtonBlock || block instanceof net.minecraft.world.level.block.SkullBlock
                || block instanceof net.minecraft.world.level.block.WallSkullBlock || block instanceof net.minecraft.world.level.block.EnderChestBlock;
    }

    public static boolean fixParticles() {
        return GameState.inSkyBlock && Features.on(PARTICLES);
    }

    // --- skyhanni ScoreboardData season colors ---

    // date line in its seasons color when asked
    public static String monthColored(String line) {
        if (line == null || !Features.on(MONTHS)) return line;
        return seasonColor(line);
    }

    static String seasonColor(String line) {
        String plain = GameState.strip(line).trim();
        String season = plain.replaceFirst("^(Early|Late) ", "");
        String code = season.startsWith("Spring") ? "§d" : season.startsWith("Summer") ? "§6" : season.startsWith("Autumn") ? "§e" : season.startsWith("Winter") ? "§9" : null;
        return code == null ? line : code + plain;
    }

    // --- skyhanni ComputerTimeOffset ---

    // skyhanni asks an ntp server which needs a library. the Date header from hypixels api (we read it anyway) is
    // good to a second and the warning starts at 5, asked once a session
    private static boolean clockAsked;

    static long offsetMs(String dateHeader, long localMs) {
        long server = java.time.ZonedDateTime.parse(dateHeader, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();
        return localMs - server;
    }

    private static void clockTick(Minecraft mc) {
        if (clockAsked || !GameState.inSkyBlock) return;
        clockAsked = true;
        Thread t = new Thread(() -> {
            try {
                var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://api.hypixel.net/v2/resources/skyblock/election")).timeout(java.time.Duration.ofSeconds(10)).method("HEAD", java.net.http.HttpRequest.BodyPublishers.noBody()).build();
                long before = System.currentTimeMillis();
                var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
                long local = (before + System.currentTimeMillis()) / 2;
                String date = response.headers().firstValue("date").orElse("");
                if (date.isEmpty()) return;
                long off = Math.abs(offsetMs(date, local));
                if (off < 5000) return;
                mc.execute(() -> {
                    if (System.currentTimeMillis() - clockWarnedAt < 3_600_000L) return;
                    clockWarnedAt = System.currentTimeMillis();
                    say("§cYour computer's clock is off by " + time(off) + ". §ePlease update your time settings. Timers here and in the game's calendar may be wrong until you do.");
                });
            } catch (Throwable ignored) {
            }
        }, "sbc-clock-check");
        t.setDaemon(true);
        t.start();
    }

    public static void register() {
        Features.add(new Feature(MONTHS, "HUD", "Month names in colour", "The date on the scoreboard in the colour of its season.", "SkyHanni: Color Month Names").when(() -> GameState.inSkyBlock).defaultOff());
        Safe.commands("SmallMisc.commands", (d, b) -> d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("sbc").then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("backup")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("list").executes(c -> {
                    List<String> all = io.github.zmuzy.sbc.core.Backups.list();
                    say(all.isEmpty() ? "No backups yet." : "Backups: §e" + String.join("§f, §e", all));
                    return 1;
                }))
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("restore").then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                    boolean ok = io.github.zmuzy.sbc.core.Backups.restore(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "name"));
                    say(ok ? "Backup restored. It takes effect after you restart the game." : "§cFailed to restore that backup.");
                    return ok ? 1 : 0;
                }))))));
        Features.add(new Feature(BOTTLE, "Fishing", "Charged bottle while fishing", "A chat line when a Thunder, Storm or Hurricane in a Bottle in your inventory is full while you are fishing.", "SkyHanni: Charge Bottle Notification")
                .when(() -> GameState.inSkyBlock).tick(SmallMisc::bottles));
        Features.add(new Feature(LAST_SERVERS, "Misc", "Back on a server", "A chat line when you land on a server you were on a moment ago.", "SkyHanni: Last Servers")
                .when(() -> GameState.inSkyBlock).tick(mc -> servers())
                .option(Option.integer("last_servers.warnTime", "Seconds that count as a moment ago", "", 60, 1, 600)));
        Feature kick = Features.add(new Feature(KICK, "Misc", "Kicked from SkyBlock", "In the Hypixel lobby: how long ago you were kicked from SkyBlock, and a title with a sound when it is time to try again.", "SkyHanni: Kick Duration")
                .when(() -> GameState.onHypixel).tick(SmallMisc::kickTick)
                .option(Option.integer("kick_duration.warnTime", "Say to rejoin after (seconds)", "", 60, 5, 300)));
        ChatBus.register(ChatBus.OBSERVE + 21, KICK, kick, SmallMisc::kickChat);
        Features.add(new Feature(SPEED, "HUD", "Movement speed", "How fast you move, in blocks per second.", "SkyHanni: Movement Speed")
                .when(() -> GameState.inSkyBlock).tick(SmallMisc::speedTick).defaultOff());
        HudLayout.add(SPEED_CARD);
        Features.add(new Feature(XP, "Items", "Your XP beside a level cost", "On a tooltip that costs experience levels, like the anvil's combine button: the levels you have, green when it is enough.", "SkyHanni: XP in Inventory")
                .when(() -> GameState.inSkyBlock).tooltip(SmallMisc::xpTooltip));
        Features.add(new Feature(PORTAL, "Items", "Who earned a Piece of Wizard Portal", "Puts the Earned by line back on a bought Piece of Wizard Portal.", "SkyHanni: Piece Of Wizard Portal")
                .when(() -> GameState.inSkyBlock).tooltip(SmallMisc::portalTooltip));
        Features.add(new Feature(POCKET, "Items", "Pocket Sack-in-a-Sack as a number", "A stitched sack's tooltip says 2/3 instead of two.", "SkyHanni: Pocket Sack-In-A-Sack")
                .when(() -> GameState.inSkyBlock).tooltip(SmallMisc::pocketTooltip));
        Features.add(new Feature(RESTART, "Misc", "Server restart title", "A title with the time left when your server is about to restart: every 30 seconds, then counting down in the last two minutes.", "SkyHanni: Server Restart Title")
                .when(() -> GameState.inSkyBlock).tick(SmallMisc::restartTick));
        Features.add(new Feature(COPY, "Misc", "Copy your playtime", "In Detailed /playtime: click the summary to copy it.", "SkyHanni: Copy Playtime")
                .when(() -> GameState.inSkyBlock).tooltip(SmallMisc::copyTooltip));
        Interactions.onSlotClick(SmallMisc::copyClick);
        Feature abiphone = Features.add(new Feature(ABIPHONE, "Misc", "Abiphone hotkey", "A key that picks up a ringing Abiphone. It has no key until you give it one in Controls, and it only answers when you press it.", "SkyHanni: Abiphone Hotkey")
                .alwaysOn().tick(SmallMisc::abiphoneTick).left(() -> acceptUuid = null));
        ChatBus.register(ChatBus.OBSERVE + 21, ABIPHONE, abiphone, SmallMisc::ringChat);
        Features.add(new Feature(DOUBLE, "Misc", "Fix double clicks", "Hypixel counts a right click on a block twice while you hold a fishing rod or a blaze dagger; this leaves out the extra half.", "SkyHanni: Fix item double clicks").when(() -> GameState.inSkyBlock).defaultOff());
        Features.add(new Feature(PARTICLES, "Misc", "Fix coloured particles", "Hypixel sets some particles' colours the old way, which shows wrong: slayer spawn particles and motes among them. It can break places where the colour is already right.", "SkyHanni: Fix Colored Particles")
                .when(() -> GameState.inSkyBlock).defaultOff());
        Features.add(new Feature(CLOCK, "Misc", "Computer clock warning", "A chat line when your computer's clock is off by more than five seconds, which throws off timers.", "SkyHanni: Computer Time Offset Warning")
                .when(() -> GameState.inSkyBlock).tick(SmallMisc::clockTick));
    }
}
