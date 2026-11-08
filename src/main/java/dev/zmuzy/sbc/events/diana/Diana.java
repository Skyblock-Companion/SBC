// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.diana;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.events.EventSheet;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.mc.ParticleHooks;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// wires skyhanni's diana modules into the game: features + options, chat/particle/sound/click hooks, per tick
// stuff (rare mob scan, player movement, delayed runs), world render, the "Warp to" hud text, reset commands
// the actual logic is in the other classes here
public final class Diana {
    public static final String BURROWS = "diana_burrows", WARP = "diana_warp", SHARE = "diana_inquisitor", PET_WARNING = "diana_pet_warning", MUTE_SPADE = "diana_mute_spade";

    private static Object lastLevel;
    private static String lastIsland = "";
    private static String lastHeld = "";
    private static int lastSlot = -1;
    private static long lastItemChange;
    private static final Set<Integer> seenRareMobs = new HashSet<>();
    private Diana() {}

    // --- options ---

    static boolean b(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    static boolean b(String key, boolean def) {
        return b(BURROWS, key, def);
    }

    static double n(String feature, String key, double def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.number();
    }

    static double n(String key, double def) {
        return n(BURROWS, key, def);
    }

    static String text(String feature, String key, String def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.text();
    }

    static int color(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.color();
    }

    // config.guess. skyhanni ships it off, here its the whole point of the feature so on
    static boolean guess() {
        return b("guess", true);
    }

    // config.burrowNearestWarp, the feature switch (on when headless in tests)
    static boolean warpEnabled() {
        Feature f = Features.get(WARP);
        return f == null || f.active();
    }

    static boolean shareEnabled() {
        Feature f = Features.get(SHARE);
        return f == null || f.enabled();
    }

    static boolean shareFocus() {
        return b(SHARE, "focus", false);
    }

    static long averagePingMs() {
        return 100;
    }

    static void playShareSound() {
        try {
            var mc = Minecraft.getInstance();
            if (mc.player == null) return;
            net.minecraft.resources.Identifier named = net.minecraft.resources.Identifier.tryParse(text(SHARE, "soundName", "entity.experience_orb.pickup"));
            net.minecraft.sounds.SoundEvent sound = named == null ? null : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(named);
            mc.player.playSound(sound != null ? sound : SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, (float) n(SHARE, "soundPitch", 1.0));
        } catch (Throwable ignored) {
        }
    }

    // --- the game as DianaEnv.World ---

    private static final class McWorld implements DianaEnv.World {
        // GriffinBurrowHelper.allowedBlocksAboveGround (BlockCompat
        // addLeaves/addLeaves2/addTallGrass/addDoublePlant/addRedFlower)
        private final Set<Block> allowedAbove = Set.of(Blocks.AIR, Blocks.DANDELION, Blocks.SPRUCE_FENCE,
                Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
                Blocks.SHORT_GRASS, Blocks.FERN, Blocks.SUNFLOWER, Blocks.LILAC, Blocks.TALL_GRASS, Blocks.LARGE_FERN, Blocks.ROSE_BUSH, Blocks.PEONY,
                Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY);

        private Minecraft mc() {
            return Minecraft.getInstance();
        }

        @Override public Vec3 playerPos() {
            var p = mc().player;
            return p == null ? Vec3.ZERO : p.position();
        }

        @Override public Vec3 eyePos() {
            var p = mc().player;
            return p == null ? Vec3.ZERO : p.getEyePosition();
        }

        @Override public Vec3 look() {
            var p = mc().player;
            return p == null ? new Vec3(0, 0, 1) : p.getLookAngle();
        }

        private BlockPos bp(Vec3 v) {
            return BlockPos.containing(v.x, v.y, v.z);
        }

        @Override public boolean inLoadedChunk(Vec3 b) {
            var l = mc().level;
            return l != null && l.isLoaded(bp(b));
        }

        @Override public boolean isGrass(Vec3 b) {
            var l = mc().level;
            return l != null && l.getBlockState(bp(b)).is(Blocks.GRASS_BLOCK);
        }

        @Override public boolean isAllowedAbove(Vec3 b) {
            var l = mc().level;
            return l != null && allowedAbove.contains(l.getBlockState(bp(b)).getBlock());
        }

        @Override public boolean spadeInHand() {
            var p = mc().player;
            return p != null && DianaApi.isDianaSpade(Gain.sbId(p.getMainHandItem()));
        }

        @Override public boolean spadeInHotbar() {
            var p = mc().player;
            if (p == null) return false;
            for (int i = 0; i < 9; i++) if (DianaApi.isDianaSpade(Gain.sbId(p.getInventory().getItem(i)))) return true;
            return false;
        }

        @Override public boolean onHub() {
            return "HUB".equals(GameState.island) || SbcMod.DEV;
        }

        @Override public boolean ritualActiveByMayor() {
            if (Mayor.dianaActive()) return true;
            for (Mayor.Perk p : Mayor.perks) if (p.name().toLowerCase(Locale.ROOT).contains("perkpocalypse")) return true;
            return SbcMod.DEV;
        }

        @Override public boolean hasGriffinPet() {
            return Vitals.petName.equalsIgnoreCase("Griffin");
        }

        @Override public long lastItemChange() {
            return lastItemChange;
        }

        @Override public String playerName() {
            return mc().getUser().getName();
        }

        @Override public boolean screenOpen() {
            return mc().gui.screen() != null;
        }

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
            var style = net.minecraft.network.chat.Style.EMPTY.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(command)).withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(hover)));
            p.sendSystemMessage(Component.literal("§b[SBC] §r" + message).withStyle(style));
        }

        @Override public void title(String text) {
            var mc = mc();
            mc.gui.hud.setTimes(5, 40, 10);
            mc.gui.hud.setTitle(Component.literal(text));
            mc.gui.hud.setSubtitle(Component.empty());
            currentTitle = text;
        }

        @Override public void stopTitle(String text) {
            if (text.equals(currentTitle)) {
                mc().gui.hud.clearTitles();
                currentTitle = "";
            }
        }
    }

    private static String currentTitle = "";

    // a rare mob entity as the share logic sees it
    private record EntityMob(LivingEntity e) implements RareMobWaypointShare.Mob {
        @Override public int id() { return e.getId(); }
        @Override public String name() { return e.getName().getString(); }
        @Override public Vec3 pos() { return e.position(); }
        @Override public boolean dead() { return e.isRemoved() || e.isDeadOrDying() || e.getHealth() <= 0; }
        @Override public boolean exists() { var l = Minecraft.getInstance().level; return l != null && l.getEntity(e.getId()) == e; }
    }

    // --- hooks ---

    private static void tick(Minecraft mc) {
        try {
            if (mc.level == null || mc.player == null) return;
            if (mc.level != lastLevel) {
                lastLevel = mc.level;
                onWorldChange();
            }
            if (!GameState.island.equals(lastIsland)) {
                lastIsland = GameState.island;
                PreciseGuessBurrow.onIslandChange();
                DianaExtras.onIslandChange();
            }
            // InventoryUtils.lastItemChangeTime
            String held = Gain.sbId(mc.player.getMainHandItem());
            int slot = mc.player.getInventory().getSelectedSlot();
            if (!held.equals(lastHeld) || slot != lastSlot) {
                lastHeld = held;
                lastSlot = slot;
                lastItemChange = DianaEnv.now();
            }
            Delayed.tick();
            if (!DianaEnv.world.onHub()) return;
            // EntityEnterWorldEvent<RemotePlayer> for the rare mob names (DianaApi.onJoinWorld)
            for (var p : mc.level.players()) {
                if (!(p instanceof RemotePlayer rp) || seenRareMobs.contains(rp.getId())) continue;
                seenRareMobs.add(rp.getId());
                DianaApi.onRareMobEntity(new EntityMob(rp));
            }
            if (seenRareMobs.size() > 500) seenRareMobs.clear();
            GriffinBurrowParticleFinder.onTick();
            GriffinBurrowHelper.onPlayerMove();
            RareMobWaypointShare.onTick();
            DianaExtras.onTick();
            if (DianaApi.isDoingDiana()) {
                long now = DianaEnv.now();
                if (now - lastSecond >= 1000) {
                    lastSecond = now;
                    GriffinBurrowHelper.update();
                }
            }
        } catch (Throwable t) {
            SbcMod.LOG.warn("Diana tick failed", t);
        }
    }

    private static long lastSecond;

    private static void onWorldChange() {
        seenRareMobs.clear();
        GriffinBurrowParticleFinder.reset();
        GriffinBurrowHelper.onWorldChange();
        BurrowWarpHelper.onWorldChange();
        RareMobWaypointShare.onWorldChange();
        DianaExtras.onWorldChange();
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        try {
            // with colour codes, skyhanni's formatted chat patterns have them
            String message = dev.zmuzy.sbc.core.LegacyText.of(line.component());
            String plain = line.plain();
            DianaApi.onChat(plain);
            if (!DianaEnv.world.onHub()) return ChatBus.PASS;
            GriffinBurrowParticleFinder.onChat(message);
            GriffinBurrowHelper.onChat(message);
            BurrowWarpHelper.onChat(message);
            if (RareMobWaypointShare.onChat(message)) return ChatBus.CANCEL;
        } catch (Throwable t) {
            SbcMod.LOG.warn("Diana chat failed", t);
        }
        return ChatBus.PASS;
    }

    private static void onParticle(ParticleHooks.Particle p) {
        if (!DianaEnv.world.onHub()) return;
        GriffinBurrowParticleFinder.onParticle(p.type(), p.x(), p.y(), p.z(), p.count(), p.speed(), p.xd(), p.yd(), p.zd());
        PreciseGuessBurrow.onParticle(p.type(), p.x(), p.y(), p.z(), p.count(), p.speed());
        ArrowGuessBurrow.onParticle(p.type(), p.x(), p.y(), p.z(), p.count(), p.speed(), p.xd(), p.yd(), p.zd());
    }

    public static HudElement WARP_TEXT;

    private static HudElement warpText() {
        return new EventSheet.Card("diana_warp_text", "Diana warp", "Events", Zone.TOP_LEFT, 44, WARP) {
        @Override
        public boolean visible() {
            return BurrowWarpHelper.overlayText(keyName()) != null;
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        protected void live(EventSheet s) {
            BurrowWarpHelper.WarpPoint warp = BurrowWarpHelper.currentWarp;
            if (warp == null || BurrowWarpHelper.overlayText(keyName()) == null) return;
            Vec3 target = GriffinBurrowHelper.targetLocation;
            warpSheet(s, warp.displayName(), keyName(), target == null ? -1 : DianaEnv.world.playerPos().distanceTo(target), target == null ? -1 : warp.distance(target));
        }

        @Override
        protected void sample(EventSheet s) {
            warpSheet(s, "Crypt", "J", 182, 58);
        }

        @Override
        protected List<Row> plainRows() {
            String t = BurrowWarpHelper.overlayText(keyName());
            return t == null ? List.of() : List.of(new Row(t));
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("§bWarp to Crypt §7(§ePress J§7)"));
        }
        };
    }

    // where to warp + key, distance to the burrow from here and from the warp if theres one (-1 otherwise)
    private static void warpSheet(EventSheet s, String warp, String key, double fromHere, double fromWarp) {
        EventSheet.Line to = s.line("Warp to §f" + warp, "");
        to.labelColor(Modern.AQUA);
        if (key != null && !key.isEmpty()) to.chip(key, Modern.GOLD).chipRight();
        if (fromHere >= 0 && fromWarp >= 0) s.line("Burrow", String.format(Locale.ROOT, "§f%,d §7→ §a%,d §7blocks", Math.round(fromHere), Math.round(fromWarp)));
    }

    // key the parent bound to BurrowWarpHelper.onKeyPress, shown in the overlay. empty until it exists
    public static String boundKeyName = "";

    public static String keyName() {
        return boundKeyName;
    }

    public static void register() {
        DianaEnv.world = new McWorld();
        Features.add(new Feature(BURROWS, "Events", "Griffin burrow helper", "Guesses the next burrow from the Ancestral Spade's particles and the chain arrow, shows every burrow near you with its type (start, mob, treasure) from Hypixel's particles, removes them when dug, and draws a line to the nearest one.", "")
                .when(() -> DianaEnv.world.onHub())
                .option(Option.bool("guess", "Guess next burrow", "From the spade's particle curve and the chain arrow.", true))
                .option(Option.bool("guessFromArrow", "Guess from the arrow", "Guess the next burrow in the chain instantly from the particle arrow.", true))
                .option(Option.bool("burrowsNearbyDetection", "Nearby detection", "Show burrows near you with their type.", true))
                .option(Option.bool("lineToNext", "Line to next", "A line to the closest burrow or guess.", true))
                .option(Option.bool("multiGuesses", "Multi guesses", "Keep earlier guesses when a new one arrives.", true))
                .option(Option.bool("renderSubGuesses", "Show every possible block", "When the arrow allows several blocks, draw them all as a grey chain.", false))
                .option(Option.bool("warnOnFail", "Warn on failure", "\"Use Spade\" title when the arrow guess fails.", true))
                .option(Option.bool("warnOnChainComp", "Warn on chain complete", "\"Use Spade\" title when a chain ends with no burrow within 90 blocks.", true))
                .option(Option.bool("clearOnWorldChange", "Clear on world change", "", false))
                .option(Option.bool("fixChat", "Particle quality hint", "When the spade gives no guess, say so and suggest /pq extreme.", true))
                .option(Option.number("beaconDistance", "Beacon distance", "Min distance to draw a beacon; -1 = no beacons.", 10, -1, 400, 1))
                .option(Option.number("textScale", "Text scale", "", 1.0, 0.1, 2.5, 0.05))
                .option(Option.color("startColor", "Start burrow colour", "", 0xFF55FF55))
                .option(Option.color("mobColor", "Mob burrow colour", "", 0xFFFF5555))
                .option(Option.color("treasureColor", "Treasure burrow colour", "", 0xFFFFAA00))
                .option(Option.bool("filled", "Filled waypoints", "Off: outline only.", true))
                .world(w -> {
                    GriffinBurrowHelper.render(w);
                    DianaExtras.render(w);
                }));
        Features.add(new Feature(WARP, "Events", "Nearest burrow warp", "Suggests a Hub warp (Castle, Crypt, DA, Museum, Wizard, Stonks...) when it is closer to the next burrow than you are; the warp key sends you there.", "")
                .when(() -> DianaEnv.world.onHub()).defaultOff()
                .option(Option.integer("warpDistance", "Warp distance", "How much closer a warp needs to be than you to suggest it.", 10, 0, 200))
                .option(Option.text("ignoredWarps", "Ignored warps", "Comma separated: HUB, CASTLE, CRYPT, DA, MUSEUM, WIZARD, STONKS, TAYLOR.", "TAYLOR")));
        Features.add(new Feature(SHARE, "Events", "Rare Diana mob sharing", "The share key sends the Minos Inquisitor, Sphinx, King Minos or Manticore you dug out to party chat; party members' coordinates become waypoints for 75 seconds; rare mobs get an outline.", "SkyHanni: Rare Diana Mob Waypoint Sharing")
                .when(() -> DianaEnv.world.onHub())
                .option(Option.bool("focus", "Focus", "Hide other waypoints while the party has a rare mob.", false))
                .option(Option.bool("diana_inquisitor.instantShare", "Offer to tell its death", "When your rare mob dies: a line to click, or the share key, tells the party. Nothing goes out without that press.", false))
                .option(Option.bool("showDespawnTime", "Show despawn time", "", true))
                .option(Option.bool("globalChat", "Read global chat", "Also read coordinates from the global chat, not only the party.", false))
                .option(Option.bool("highlight", "Highlight rare mobs", "", true))
                .option(Option.bool("highlightWalls", "Also through walls", "", false))
                .option(Option.color("highlightColor", "Highlight colour", "", 0x7F55FFFF))
                .option(Option.text("soundName", "Sound", "The game's name of a sound, played when a party member's mob comes in.", "entity.experience_orb.pickup"))
                .option(Option.number("soundPitch", "Sound pitch", "", 1.0, 0.5, 2.0, 0.1))
                .option(Option.bool("share.sphinx", "Share: Sphinx", "", true))
                .option(Option.bool("share.minosInquisitor", "Share: Minos Inquisitor", "", true))
                .option(Option.bool("share.manticore", "Share: Manticore", "", true))
                .option(Option.bool("share.kingMinos", "Share: King Minos", "", true))
                .option(Option.bool("receive.sphinx", "Receive: Sphinx", "", true))
                .option(Option.bool("receive.minosInquisitor", "Receive: Minos Inquisitor", "", true))
                .option(Option.bool("receive.manticore", "Receive: Manticore", "", true))
                .option(Option.bool("receive.kingMinos", "Receive: King Minos", "", true)));
        Features.add(new Feature(PET_WARNING, "Events", "Griffin pet warning", "A reminder when you hold the spade without a Griffin pet.", "")
                .when(() -> DianaEnv.world.onHub()).option(Option.bool("diana_pet_warning.on", "Warn", "", true)));
        Features.add(new Feature(MUTE_SPADE, "Events", "Mute bugged spade music", "Hypixel's spade sometimes plays music sounds at you; they are muted while the real music stays.", "").defaultOff()
                .when(() -> DianaEnv.world.onHub()).option(Option.bool("diana_mute_spade.on", "Mute", "", true)));
        WARP_TEXT = warpText();
        HudLayout.add(WARP_TEXT);

        ChatBus.register(ChatBus.OBSERVE + 18, "diana", null, Diana::onChat);
        ParticleHooks.listen(Diana::onParticle);
        SoundHooks.listen(DianaExtras::onSound);
        Interactions.onBlockClick((pos, right) -> {
            if (DianaEnv.world.onHub()) GriffinBurrowHelper.onBlockClick(pos);
        });
        Interactions.onItemClick(right -> {
            if (!DianaEnv.world.onHub()) return false;
            DianaApi.onItemClick(right);
            DianaExtras.onItemClick(right);
            return PreciseGuessBurrow.onItemClick(right);
        });
        Safe.endTick("Diana.tick", Diana::tick);
        Safe.commands("Diana.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("resetburrows").executes(c -> {
                    GriffinBurrowHelper.resetAllData();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fManually reset all burrow data."));
                    return 1;
                }))
                .then(literal("resetburrowwarps").executes(c -> {
                    BurrowWarpHelper.resetUnlocked();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fReset disabled burrow warps."));
                    return 1;
                }))
                .then(literal("sharerare").executes(c -> {
                    RareMobWaypointShare.share();
                    return 1;
                }))
                .then(literal("resetdianamobs").executes(c -> {
                    RareMobWaypointShare.reset();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fManually reset all rare mob data."));
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("sharerare", "Tell your party about your rare Diana mob, or that it died");
        dev.zmuzy.sbc.core.Commands.describe("resetburrows", "Forget every Diana burrow guess");
        dev.zmuzy.sbc.core.Commands.describe("resetburrowwarps", "Allow every Hub warp for the burrow warp helper again");
        dev.zmuzy.sbc.core.Commands.describe("resetdianamobs", "Forget the shared rare Diana mob waypoints");
    }
}
