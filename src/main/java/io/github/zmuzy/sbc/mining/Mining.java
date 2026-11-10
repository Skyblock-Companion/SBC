// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.chat.Party;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.mixin.BossOverlayAccessor;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// wires the skyhanni mining stuff into the game: features + options, chat / scoreboard / tab / boss bar
// feeds, corpse scan per tick in a shaft
// world rendering, hud elements, commands. the logic lives in MiningApi, MiningEvents, Commissions, Powder,
// Mineshaft, MiningNotifications
public final class Mining {
    private static Object lastLevel;
    private static String lastIsland = "", lastArea = "";
    private static long lastSecond;
    private static int lastScoreboardHash, lastTabHash;
    private static boolean wasDead;
    private Mining() {}

    static boolean b(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    static double n(String feature, String key, double def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.number();
    }

    static String text(String feature, String key, String def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.text();
    }

    private static final class McWorld implements MiningEnv.World {
        private Minecraft mc() {
            return Minecraft.getInstance();
        }

        @Override public boolean inSkyBlock() { return GameState.inSkyBlock || SbcMod.DEV; }
        @Override public String island() { return GameState.island; }
        @Override public String area() { return GameState.area; }
        @Override public List<String> scoreboardFormatted() { return GameState.scoreboardFormatted; }
        @Override public List<String> tab() { return GameState.tab; }

        @Override public String bossbar() {
            try {
                var overlay = mc().gui.hud.getBossOverlay();
                for (LerpingBossEvent e : ((BossOverlayAccessor) overlay).sbc$events().values()) return LegacyText.of(e.getName());
            } catch (Throwable ignored) {
            }
            return "";
        }

        @Override public Vec3 playerPos() {
            var p = mc().player;
            return p == null ? Vec3.ZERO : p.position();
        }

        @Override public Vec3 facing() {
            var p = mc().player;
            if (p == null) return new Vec3(0, 0, 1);
            var d = p.getDirection().getUnitVec3i();
            return new Vec3(d.getX(), d.getY(), d.getZ());
        }

        @Override public String playerName() { return mc().getUser().getName(); }
        @Override public boolean inParty() { return !Party.members().isEmpty(); }

        @Override public void sendCommand(String command) {
            var c = mc().getConnection();
            if (c != null) c.sendCommand(command);
        }

        @Override public void chat(String message) {
            var p = mc().player;
            if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §r" + message));
        }

        @Override public void clickableChat(String message, String command, String hover) {
            var p = mc().player;
            if (p == null) return;
            Style st = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)));
            p.sendSystemMessage(Component.literal("§b[SBC] §r" + message).withStyle(st));
        }

        @Override public void title(String t) {
            var mc = mc();
            mc.gui.hud.setTimes(5, 30, 10);
            mc.gui.hud.setTitle(Component.literal(t));
            mc.gui.hud.setSubtitle(Component.empty());
        }

        @Override public void playSound(String id, float pitch) {
            var p = mc().player;
            if (p != null) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, pitch);
        }

        @Override public Store.MiningProfile profile() {
            String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
            return Store.I.mining.computeIfAbsent(key, k -> new Store.MiningProfile());
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        try {
            if (!MiningEnv.world.inSkyBlock()) return ChatBus.PASS;
            String message = LegacyText.of(line.component());
            String plain = line.plain();
            MiningApi.onChat(message);
            MiningEvents.onChat(message);
            Commissions.onChat(message);
            Powder.onChat(plain);
            MiningNotifications.onChat(message);
            if (MiningNotifications.MINESHAFT_SPAWN.matcher(message).matches() && MiningApi.inGlacialTunnels()) Mineshaft.onMineshaftSpawn();
            Mineshaft.onChat(message, plain);
        } catch (Throwable t) {
            SbcMod.LOG.warn("Mining chat failed", t);
        }
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        try {
            if (mc.level == null || mc.player == null) return;
            if (mc.level != lastLevel) {
                lastLevel = mc.level;
                onWorldChange();
            }
            if (!GameState.island.equals(lastIsland)) {
                lastIsland = GameState.island;
                Mineshaft.onIslandChange();
                if (GameState.island.equals("MINESHAFT")) {
                    Vec3 below = new Vec3(Math.floor(mc.player.getX()), Math.floor(mc.player.getY()) - 1, Math.floor(mc.player.getZ()));
                    Mineshaft.onIslandJoin(below, MiningEnv.world.facing());
                }
            }
            if (!GameState.area.equals(lastArea)) {
                lastArea = GameState.area;
                MiningApi.onAreaChange();
            }
            boolean dead = mc.player.isDeadOrDying();
            if (dead && !wasDead) MiningApi.onPlayerDeath();
            wasDead = dead;
            if (!MiningEnv.world.inSkyBlock()) return;
            int sh = GameState.scoreboardFormatted.hashCode();
            if (sh != lastScoreboardHash) {
                lastScoreboardHash = sh;
                MiningApi.onScoreboard(GameState.scoreboardFormatted);
                Powder.onScoreboard(GameState.scoreboardFormatted);
                Mineshaft.onScoreboard(GameState.scoreboardFormatted);
            }
            int th = GameState.tab.hashCode();
            if (th != lastTabHash) {
                lastTabHash = th;
                Commissions.onTab(GameState.tab);
                Mineshaft.onTab(GameState.tab);
                List<String> powder = MiningEvents.widgetLines(GameState.tab, "POWDER");
                if (powder != null) Powder.onWidget(powder);
                List<String> corpses = MiningEvents.widgetLines(GameState.tab, "FROZEN_CORPSES");
                if (corpses != null) Mineshaft.onCorpseWidget(corpses);
            }
            if (MiningApi.inMineshaft() && Features.on("mining_mineshaft")) scanCorpses(mc);
            readCommissionsMenu(mc);
            long now = MiningEnv.now();
            if (now - lastSecond >= 1000) {
                lastSecond = now;
                MiningEvents.onSecondPassed(GameState.tab, MiningEnv.world.bossbar());
                Powder.onSecondPassed();
                Mineshaft.onSecondPassed();
            }
        } catch (Throwable t) {
            SbcMod.LOG.warn("Mining tick failed", t);
        }
    }

    private static int lastCommissionMenu = -1;

    // commissions menu opened -> Commissions.onMenu (skyblocker CommissionHighlight menu)
    private static void readCommissionsMenu(Minecraft mc) {
        var screen = mc.gui.screen();
        if (screen == null || !(mc.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu menu)) {
            lastCommissionMenu = -1;
            return;
        }
        if (menu.containerId == lastCommissionMenu) return;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        if (!title.equals("Commissions")) return;
        int n = Math.min(menu.getRowCount() * 9, menu.slots.size());
        java.util.Map<String, List<String>> items = new java.util.LinkedHashMap<>();
        boolean any = false;
        for (int i = 0; i < n; i++) {
            var st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            any = true;
            items.put(LegacyText.name(st), LegacyText.lore(st));
        }
        if (!any) return;
        lastCommissionMenu = menu.containerId;
        Commissions.onMenu(title, items);
    }

    // skyhanni CorpseFinder: armor stands with a corpse helmet, found after 10 ticks seen (canBeSeen(-1..3))
    private static void scanCorpses(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        AABB range = mc.player.getBoundingBox().inflate(64);
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, range, x -> true)) {
            Mineshaft.Corpse type = Mineshaft.Corpse.byHelmet(Gain.sbId(a.getItemBySlot(EquipmentSlot.HEAD)));
            if (type == null) continue;
            Vec3 pos = a.position();
            boolean visible = false;
            for (int off = -1; off <= 3 && !visible; off++) {
                Vec3 target = pos.add(0, off, 0);
                if (target.distanceTo(eye) >= 150) continue;
                HitResult hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                visible = hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceTo(target) < 1.0;
            }
            Mineshaft.onCorpseSeen(a.getId(), type, pos, visible, false);
        }
    }

    private static void onWorldChange() {
        MiningApi.onWorldChange();
        MiningEvents.reset();
        MiningNotifications.onWorldChange();
        Mineshaft.onWorldChange();
        lastScoreboardHash = lastTabHash = 0;
    }

    public static HudElement COMMISSIONS, POWDER_TRACKER, CAVE_IN, PITY, COLD;

    private static void elements() {
        COMMISSIONS = MiningCards.commissions();
        POWDER_TRACKER = MiningCards.tracker();
        CAVE_IN = MiningCards.caveIn();
        PITY = MiningCards.pity();
        COLD = MiningCards.cold();
    }

    public static void register() {
        MiningEnv.world = new McWorld();
        Features.add(new Feature("mining_commissions", "Mining", "Commissions", "Your commissions from the tab list with their progress; DONE ones marked (the chat's 'Commission Complete!' counts at once).", "")
                .when(MiningEnv::isMiningIsland));
        Features.add(new Feature("mining_powder_tracker", "Mining", "Powder tracker (Crystal Hollows)", "Chests lockpicked, hard stone compacted and every chest reward from the chat with per-hour rates; Double Powder state.", "")
                .when(MiningApi::inCrystalHollows)
                .option(Option.bool("onlyWhenPowderGrinding", "Only while grinding", "Hide a minute after the last chest.", false)));
        Features.add(new Feature("mining_mineshaft", "Mining", "Glacite Mineshaft", "Which mineshaft you entered and how many since the last of that type, the 60 s cave-in timer with an estimate from your Cold, waypoints for the entrance and ladder, corpses found and looted, the known corpse spots, and corpse coordinates shared in chat.", "")
                .when(() -> MiningApi.inMineshaft() || MiningApi.inGlacialTunnels())
                .option(Option.bool("detection", "Say which mineshaft you entered", "", true))
                .option(Option.text("mineshaftsToTrack", "Mineshafts to announce with a title", "Comma separated type codes or names, e.g. JASP_C, Opal Crystal", ""))
                .option(Option.bool("sendTypeToPartyChat", "Offer to tell the party the type", "On entering, a line to click and a key to press (Mineshaft: tell the party the type), unbound at first. Nothing goes out without that press.", false))
                .option(Option.text("partyChatFormat", "Party message", "{type}, {amountSinceThis}, {timeSinceThis}", "{type} mineshaft, {amountSinceThis} since the last one ({timeSinceThis})"))
                .option(Option.bool("caveInTimer", "Cave-in timer", "", true))
                .option(Option.bool("showTimeInMineshaft", "Time in mineshaft", "", true))
                .option(Option.bool("showEstimatedTimeLeft", "Estimated time left (cold rate)", "", true))
                .option(Option.bool("entrance", "Entrance waypoint", "", true))
                .option(Option.bool("ladder", "Ladder waypoint", "", true))
                .option(Option.bool("foundCorpse", "Found corpse waypoints", "", true))
                .option(Option.bool("lootedCorpse", "Looted corpse waypoints", "", true))
                .option(Option.bool("expectedCorpses", "Known corpse spots of this mineshaft type", "Until the real corpse is seen.", true))
                .option(Option.bool("autoShareCorpses", "Offer to share corpses in the party", "Near a corpse nobody shared: a line to click and a key to press (Mineshaft: share the nearest corpse), unbound at first. Nothing goes out without that press.", false))
                .world(Mineshaft::render));
        Features.add(new Feature("mining_pity", "Mining", "Mineshaft pity", "The pity counter from the tab list, the last mineshaft and the session count (Glacite Tunnels).", "")
                .when(MiningApi::inGlacialTunnels));
        Features.add(new Feature("mining_notifications", "Mining", "Mining notifications", "A title and a sound for a Glacite Mineshaft portal, a Suspicious Scrap, Golden and Diamond Goblins, and when Cold reaches the threshold.", "")
                .when(MiningEnv::isMiningIsland)
                .option(Option.bool("mining_notifications.on", "Titles and sounds", "", true))
                .option(Option.bool("sound", "Sound", "", true))
                .option(Option.bool("mineshaft", "Mineshaft portal", "", true))
                .option(Option.bool("scrap", "Suspicious Scrap", "", true))
                .option(Option.bool("goldenGoblin", "Golden Goblin", "", true))
                .option(Option.bool("diamondGoblin", "Diamond Goblin", "", true))
                .option(Option.bool("cold", "Cold", "", true))
                .option(Option.integer("coldThreshold", "Cold threshold", "", 100, 1, 100))
                .option(Option.bool("getAscensionRope", "Offer an Ascension Rope", "A line to click in a Mineshaft when Cold gets high.", true))
                .option(Option.integer("coldAmount", "Offer the rope at Cold", "", 90, 1, 100)));
        elements();
        HudLayout.add(COMMISSIONS);
        HudLayout.add(POWDER_TRACKER);
        HudLayout.add(CAVE_IN);
        HudLayout.add(PITY);
        HudLayout.add(COLD);
        MiningApi.onColdUpdate(MiningNotifications::onColdUpdate);
        MiningApi.onColdUpdate(Mineshaft::onColdUpdate);
        ChatBus.register(ChatBus.OBSERVE + 20, "mining", null, Mining::onChat);
        BlockPrediction.register();
        ActualPowder.register();
        Safe.endTick("Mining.tick", Mining::tick);
        Safe.commands("Mining.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("sharemineshaft").executes(c -> {
                    Mineshaft.sendType();
                    return 1;
                }))
                .then(literal("sharecorpse").executes(c -> {
                    if (!Mineshaft.shareNearest()) c.getSource().sendFeedback(Component.literal("§b[SBC] §cNo mineshaft waypoint within 5 blocks."));
                    return 1;
                }))
                .then(literal("resetpowdertracker").executes(c -> {
                    Powder.reset();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fPowder tracker reset."));
                    return 1;
                }))));
        io.github.zmuzy.sbc.core.Commands.describe("sharecorpse", "Share the nearest mineshaft waypoint in party / all chat");
        io.github.zmuzy.sbc.core.Commands.describe("resetpowdertracker", "Reset the Crystal Hollows powder tracker");
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
