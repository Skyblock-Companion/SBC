// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.mc.ParticleHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// glue for the skyhanni rift port (features/rift): the mc world side, feature defs, chat/particle/click
// hooks, menu reading, hud elements, commands. the logic lives in the small classes
public final class Rift {
    public static final String EFFIGIES = "rift_effigies", ENIGMA = "rift_enigma", TIMER = "rift_timer", CRUX = "rift_crux", KILLZONE = "rift_killzone",
            VERMIN = "rift_vermin", MOTES = "rift_motes_orb", PUNCHCARD = "rift_punchcard", UBIK = "rift_ubik", BUTTONS = "rift_buttons", PARKOURS = "rift_parkours";

    private static HudElement TIMER_HUD, CRUX_HUD, VERMIN_HUD, PUNCH_HUD, UBIK_HUD;
    private static long lastSecond, lastCrux;
    private static int lastMenuId = -1;
    private static String lastMenuTitle = "";
    private static int lastMenuHash;
    private static String lastActionBar = "";
    private static String lastIsland = "";

    private Rift() {}

    static boolean b(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    static int i(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : (int) o.number();
    }

    static int color(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.color();
    }

    static boolean rift() {
        return "THE_RIFT".equals(GameState.island) || SbcMod.DEV;
    }

    // --- world ---

    static final class McWorld implements RiftEnv.World {
        private static Minecraft mc() {
            return Minecraft.getInstance();
        }

        @Override public boolean inSkyBlock() { return GameState.inSkyBlock; }
        @Override public String island() { return GameState.island; }
        @Override public String area() { return GameState.area; }
        @Override public List<String> scoreboardRaw() { return GameState.scoreboardRaw; }

        @Override public Vec3 playerPos() {
            var p = mc().player;
            return p == null ? Vec3.ZERO : p.position();
        }

        @Override public boolean onGround() {
            var p = mc().player;
            return p != null && p.onGround();
        }

        @Override public int experienceLevel() {
            var p = mc().player;
            return p == null ? 0 : p.experienceLevel;
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

        @Override public void title(String title, String subtitle, int ticks) {
            var mc = mc();
            mc.gui.hud.setTimes(0, ticks, 0);
            mc.gui.hud.setTitle(Component.literal(title));
            mc.gui.hud.setSubtitle(Component.literal(subtitle == null ? "" : subtitle));
        }

        @Override public void sendCommand(String command) {
            var c = mc().getConnection();
            if (c != null) c.sendCommand(command);
        }

        @Override public void pathFind(String name, Vec3 pos) {
            if (b(ENIGMA, "rift_enigma.path", true)) dev.zmuzy.sbc.nav.Navigator.navigate(new dev.zmuzy.sbc.nav.Navigator.Target("THE_RIFT", name, pos));
        }

        @Override public void stopPath() {
            var t = dev.zmuzy.sbc.nav.Navigator.target();
            if (t != null && t.name().endsWith("Enigma Soul")) dev.zmuzy.sbc.nav.Navigator.stop();
        }

        @Override public Store.RiftProfile profile() {
            String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
            return Store.I.rift.computeIfAbsent(key, k -> new Store.RiftProfile());
        }
    }

    // --- chat ---

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String formatted = dev.zmuzy.sbc.core.LegacyText.of(line.component());
        String clean = line.plain();
        if (GameState.inSkyBlock && Features.on(UBIK) && UbikReminder.onChat(clean.trim())) return ChatBus.PASS;
        if (!rift()) return ChatBus.PASS;
        if (Features.on(ENIGMA)) EnigmaSoulWaypoints.onChat(clean);
        if (Features.on(KILLZONE) && KillZoneWarning.onChat(formatted)) {
            var p = Minecraft.getInstance().player;
            if (p != null) p.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
        }
        // the rift panel counts these too, so each gets read when either of the 2 wants it
        boolean panel = Features.on("rift_panel");
        if ((Features.on(VERMIN) || panel) && VerminTracker.onChat(formatted) && Features.on(VERMIN) && b(VERMIN, "hideChat", false)) return ChatBus.CANCEL;
        if (Features.on(MOTES) || panel) RiftMotesOrb.onChat(formatted);
        if (Features.on(PUNCHCARD) || panel) PunchcardHighlight.onChat(formatted);
        if (Features.on(BUTTONS)) WoodenButtonsHelper.onChat(formatted);
        // race on or not: the timer + panel need it whatever the parkour lines are set to
        RiftParkours.onChat(formatted);
        return ChatBus.PASS;
    }

    // --- tick ---

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!GameState.island.equals(lastIsland)) {
            lastIsland = GameState.island;
            onWorldChange();
            if (rift()) PunchcardHighlight.onServerChange(GameState.area + "|" + System.currentTimeMillis() / 600_000);
        }
        long now = System.currentTimeMillis();
        if (GameState.inSkyBlock && now - lastSecond >= 1000) {
            lastSecond = now;
            if (Features.on(UBIK)) UbikReminder.onSecondPassed();
        }
        if (!rift()) return;
        readMenu(mc);
        if (Features.on(TIMER)) {
            String bar = Vitals.actionBarRaw;
            if (!bar.equals(lastActionBar)) {
                lastActionBar = bar;
                RiftTimer.showPercentage = b(TIMER, "percentage", true);
                RiftTimer.showMaxTime = b(TIMER, "maxTime", true);
                RiftTimer.onActionBar(bar);
            }
            if (RiftApi.inRiftRace && mc.player.tickCount % 5 == 0) RiftTimer.onRaceTick(mc.player.experienceLevel);
        }
        if (Features.on(KILLZONE)) {
            String[] t = KillZoneWarning.title();
            if (t != null) RiftEnv.world.title(t[0], t[1], 2);
        }
        if (Features.on(PUNCHCARD)) PunchcardHighlight.tick();
        if (Features.on(BUTTONS)) {
            WoodenButtonsHelper.tick();
            if (WoodenButtonsHelper.shouldCheckPoweredButtons()) {
                Vec3 eye = mc.player.getEyePosition();
                for (Vec3 b : WoodenButtonsHelper.allButtons()) {
                    BlockPos pos = BlockPos.containing(b);
                    var state = mc.level.getBlockState(pos);
                    if (!(state.getBlock() instanceof ButtonBlock) || !state.getValue(BlockStateProperties.POWERED)) continue;
                    double d = b.add(0.5, 0.5, 0.5).distanceTo(eye);
                    if (d < 1 || d > 3) continue;
                    WoodenButtonsHelper.onPoweredVisibleButton(b);
                }
            }
        }
        if (lastSecond == now) onSecondPassed(mc, now);
    }

    private static void onSecondPassed(Minecraft mc, long now) {
        if ((Features.on(EFFIGIES) || Features.on("rift_panel")) && RiftBloodEffigies.isEnabled()) {
            RiftBloodEffigies.onScoreboard(GameState.scoreboardRaw);
            for (ArmorStand e : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(15)))
                RiftBloodEffigies.onArmorStand(LegacyText.of(e.getName()), e.position());
        }
        if (Features.on(VERMIN) || Features.on(PUNCHCARD) || Features.on(CRUX)) {
            boolean vacuum = false, punchcard = false;
            List<List<String>> lores = new ArrayList<>();
            boolean crux = false;
            var inv = mc.player.getInventory();
            for (int s = 0; s < inv.getContainerSize(); s++) {
                ItemStack st = inv.getItem(s);
                if (st.isEmpty()) continue;
                String id = Gain.sbId(st);
                if (id.equals(VerminTracker.TURBOMAX_VACUUM)) vacuum = true;
                if (id.equals(PunchcardHighlight.PUNCHCARD_ARTIFACT)) punchcard = true;
                if (id.startsWith("CRUX_TALISMAN")) {
                    crux = true;
                    lores.add(LegacyText.lore(st));
                }
            }
            VerminTracker.setHasVacuum(vacuum);
            hasPunchcard = punchcard;
            if (Features.on(CRUX) && crux && now - lastCrux >= 2000) {
                lastCrux = now;
                CruxTalismanDisplay.compactWhenMaxed = b(CRUX, "compactWhenMaxed", true);
                CruxTalismanDisplay.showBonuses = b(CRUX, "showBonuses", true);
                CruxTalismanDisplay.read(lores);
            }
        }
    }

    private static boolean hasPunchcard;

    private static void onWorldChange() {
        RiftApi.onWorldChange();
        RiftBloodEffigies.onWorldChange();
        RiftTimer.onWorldChange();
        WoodenButtonsHelper.onWorldChange();
        RiftParkours.onIslandChange();
        RiftMotesOrb.reset();
        RiftPack.onMenuClosed();
        lastMenuId = -1;
    }

    // skyhanni InventoryFullyOpenedEvent / InventoryCloseEvent from the open chest (same way as the hoppity port)
    private static void readMenu(Minecraft mc) {
        var screen = mc.gui.screen();
        if (screen == null || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            if (lastMenuId != -1) {
                lastMenuId = -1;
                lastMenuTitle = "";
                lastMenuHash = 0;
                EnigmaSoulWaypoints.onInventoryClose();
                RiftPack.onMenuClosed();
            }
            return;
        }
        String title = screen.getTitle().getString();
        int n = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int hash = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (!st.isEmpty()) hash = hash * 31 + st.getHoverName().getString().hashCode() + st.getCount();
        }
        if (menu.containerId == lastMenuId && title.equals(lastMenuTitle) && hash == lastMenuHash) return;
        boolean opened = menu.containerId != lastMenuId || !title.equals(lastMenuTitle);
        lastMenuId = menu.containerId;
        lastMenuTitle = title;
        lastMenuHash = hash;
        if (hash == 0) return;
        RiftPack.onMenu(title, menu, n);
        Map<Integer, EnigmaSoulWaypoints.Item> items = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            List<String> lore = new ArrayList<>();
            for (String l : LegacyText.lore(st)) lore.add(GameState.strip(l));
            items.put(i, new EnigmaSoulWaypoints.Item(GameState.strip(LegacyText.name(st)), lore));
        }
        if (Features.on(ENIGMA)) EnigmaSoulWaypoints.onInventoryFullyOpened(title, items);
        if (opened && Features.on(UBIK)) UbikReminder.onInventoryOpen(title);
        if (Features.on(VERMIN) && title.equals("Vermin Bin") && n > 13) {
            ItemStack bin = menu.slots.get(13).getItem();
            Map<String, Integer> bag = null;
            var inv = mc.player.getInventory();
            for (int s = 0; s < inv.getContainerSize(); s++) {
                ItemStack st = inv.getItem(s);
                if (!Gain.sbId(st).equals(VerminTracker.TURBOMAX_VACUUM)) continue;
                var cd = st.get(DataComponents.CUSTOM_DATA);
                if (cd == null) break;
                var tag = cd.copyTag();
                bag = new HashMap<>();
                for (VerminTracker.VerminType t : VerminTracker.VerminType.values()) bag.put(t.attribute, tag.getIntOr(t.attribute, 0));
                break;
            }
            VerminTracker.onVerminBin(LegacyText.lore(bin), bag);
        }
    }

    // slot highlight for the enigma soul menu (SlotOverlayMixin calls this)
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (!rift()) return;
        RiftPack.slot(g, slot);
        if (!Features.on(ENIGMA)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof ChestMenu)) return;
        if (slot.container == mc.player.getInventory()) return;
        ItemStack st = slot.getItem();
        String name = st.isEmpty() ? null : GameState.strip(LegacyText.name(st));
        if (EnigmaSoulWaypoints.highlight(slot.getContainerSlot(), name)) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80AA00AA);
    }

    // --- render ---

    private static void drawPunchcard(WorldDraw w) {
        if (!hasPunchcard) return;
        var mc = w.mc;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.getUUID().version() == 2) continue;
            if (PunchcardHighlight.highlight(p.getName().getString())) w.box(p.getBoundingBox(), (color(PUNCHCARD, "color", 0xFFFF55FF) & 0x00FFFFFF) | 0x60000000, true, false);
        }
    }

    private static void drawParkours(WorldDraw w) {
        RiftParkours.rainbowColor = b(PARKOURS, "rainbowColor", true);
        RiftParkours.lookAhead = i(PARKOURS, "lookAhead", 3);
        RiftParkours.monochromeColor = color(PARKOURS, "rift_parkours.color", 0x3C0000FF);
        RiftParkours.outline = b(PARKOURS, "rift_parkours.outline", true);
        RiftParkours.updateConfig();
        RiftParkours.renderRace(w);
        RiftParkours.renderMirrorverse(w);
    }

    // --- register ---

    public static void register() {
        RiftEnv.world = new McWorld();
        RiftData.load();
        RiftParkours.load();

        Features.add(new Feature(EFFIGIES, "Rift", "Blood Effigies", "In Stillgore Château, marks the six effigies: red = break it, yellow = respawning soon (with the time), grey = unknown.", "SkyHanni: Rift Blood Effigies")
                .when(Rift::rift).world(w -> {
                    RiftBloodEffigies.unknownTime = b(EFFIGIES, "unknownTime", true);
                    RiftBloodEffigies.respawningSoon = b(EFFIGIES, "respawningSoon", true);
                    RiftBloodEffigies.respawningSoonTime = i(EFFIGIES, "respawningSoonTime", 3);
                    RiftBloodEffigies.render(w);
                })
                .option(Option.bool("unknownTime", "Unknown time", "Also mark effigies whose state or respawn time is unknown.", true))
                .option(Option.bool("respawningSoon", "Respawning soon", "Mark broken effigies that respawn within the time below.", true))
                .option(Option.integer("respawningSoonTime", "Soon = minutes", "How many minutes before respawn an effigy counts as soon.", 3, 1, 20)));
        Features.add(new Feature(ENIGMA, "Rift", "Enigma Soul waypoints", "Click a soul in Rift Guide > Enigma Souls to track it; the button in the middle of the page tracks every missing soul on it; finding a soul clears it.", "SkyHanni: Enigma Soul Waypoints")
                .when(Rift::rift).world(w -> {
                    EnigmaSoulWaypoints.color = color(ENIGMA, "color", 0xFFAA00AA);
                    EnigmaSoulWaypoints.markAll = b(ENIGMA, "rift_enigma.all", false);
                    EnigmaSoulWaypoints.markFound = b(ENIGMA, "rift_enigma.found", true);
                    EnigmaSoulWaypoints.render(w);
                })
                .option(Option.color("rift_enigma.color", "Waypoint colour", "The colour of the soul waypoints.", 0xFFAA00AA))
                .option(Option.bool("rift_enigma.path", "Path to the soul", "Navigates to a soul when you start tracking it.", true))
                .option(Option.bool("rift_enigma.all", "Mark every soul", "A mark on all of them, through walls, without tracking each one.", false))
                .option(Option.bool("rift_enigma.found", "Mark found souls too", "With every soul marked: red for the ones you have found.", true)));
        Features.add(new Feature(TIMER, "Rift", "Rift Timer", "Your Rift time from the action bar with the maximum, the percentage and the recent changes.", "SkyHanni: Rift Timer")
                .when(Rift::rift)
                .option(Option.bool("percentage", "Percentage", "Show the time left as a percentage of the maximum.", true))
                .option(Option.bool("maxTime", "Max time", "Show the maximum time seen.", true))
                .option(Option.bool("rift_timer.nametag", "Other players' time as minutes", "Writes the Rift time in other players' name tags as 52m 30s instead of seconds.", false)));
        Features.add(new Feature(CRUX, "Rift", "Crux Talisman progress", "The Crux Talisman's kill progress and bonuses, read from the item in your inventory.", "SkyHanni: Crux Talisman Display")
                .when(Rift::rift)
                .option(Option.bool("compactWhenMaxed", "Compact when maxed", "Only show MAXED once every crux is done.", true))
                .option(Option.bool("showBonuses", "Show bonuses", "Show the Total Bonuses block.", true)));
        Features.add(new Feature(KILLZONE, "Rift", "Kill-zone warning", "In the Colosseum, a title counting down the seconds you have to get back into the arena.", "SkyHanni: Kill Zone Warning")
                .when(Rift::rift));
        Features.add(new Feature(VERMIN, "Rift", "Vermin tracker", "Vermin vacuumed this session and in total (Vermin Bin + vacuum), in the West Village while you carry the Turbomax Vacuum.", "SkyHanni: Vermin Tracker")
                .when(Rift::rift)
                .option(Option.bool("hideChat", "Hide chat", "Hide the 'You vacuumed a ...' lines.", false))
                .option(Option.bool("showOutsideWestVillage", "Show outside the West Village", "", false))
                .option(Option.bool("showWithoutVacuum", "Show without the vacuum", "", false)));
        Features.add(new Feature(MOTES, "Rift", "Motes Orbs", "Marks Motes Orbs (found from their particles) until you pick them up.", "SkyHanni: Rift Motes Orb")
                .when(Rift::rift).world(w -> {
                    RiftMotesOrb.hideParticles = b(MOTES, "hideParticles", false);
                    RiftMotesOrb.size = i(MOTES, "size", 5);
                    RiftMotesOrb.throughWalls = b(MOTES, "through_walls", false);
                    RiftMotesOrb.render(w);
                })
                .option(Option.bool("hideParticles", "Hide particles", "Hide the orb's particles once it is recognised.", false))
                .option(Option.integer("size", "Size", "Marker size (1-10).", 5, 1, 10))
                .option(Option.bool("through_walls", "Through walls", "Also show the orbs behind blocks.", false)));
        Features.add(new Feature(PUNCHCARD, "Rift", "Punchcard Artifact", "Highlights players you have not punched yet (20 per visit) and counts your punches.", "SkyHanni: Punchcard Highlight")
                .when(Rift::rift).world(Rift::drawPunchcard)
                .option(Option.color("rift_punchcard.color", "Highlight colour", "", 0xFFFF55FF))
                .option(Option.bool("reverse", "Reverse", "Highlight punched players instead.", false))
                .option(Option.bool("reverseGUI", "Count down", "Show the punches left instead of made.", false)));
        Features.add(new Feature(UBIK, "Rift", "Ubik's Cube reminder", "A reminder when Split or Steal can be played again (2 h after opening it, or the exact cooldown from chat).", "SkyHanni: Ubik Reminder")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("onlyWhenReady", "Only when ready", "Hide the countdown; show only Ready.", false)));
        Features.add(new Feature(BUTTONS, "Rift", "Wooden Buttons helper", "While the Buttons enigma soul is tracked, shows the nearest house with buttons left and the buttons themselves.", "SkyHanni: Wooden Buttons Helper")
                .when(Rift::rift).world(w -> {
                    WoodenButtonsHelper.color = color(ENIGMA, "color", 0xFFAA00AA);
                    WoodenButtonsHelper.render(w);
                }));
        Features.add(new Feature(PARKOURS, "Rift", "Parkour helpers", "Gunther's race in the West Village and the Mirrorverse parkours (lava maze, upside-down, Tubulator): the next platforms, lines and shortcuts.", "SkyHanni: Rift parkours")
                .when(Rift::rift).world(Rift::drawParkours)
                .option(Option.bool("rainbowColor", "Rainbow", "Colour the platforms by index.", true))
                .option(Option.integer("lookAhead", "Look ahead", "How many platforms ahead to draw.", 3, 1, 10))
                .option(Option.color("rift_parkours.color", "Single colour", "Used when Rainbow is off.", 0x3C0000FF))
                .option(Option.bool("rift_parkours.outline", "Outline", "Outline the top edge of the upside-down and Tubulator platforms.", true))
                .option(Option.bool("rift_parkours.hide_players", "Hide other players", "While you are on a Mirrorverse parkour or in Gunther's race.", false)));

        ChatBus.register(ChatBus.OBSERVE + 21, "rift", null, Rift::onChat);
        Safe.endTick("Rift.tick", Rift::tick);
        ParticleHooks.listen(p -> {
            if (!rift() || !Features.on(MOTES) || !p.type().equals("minecraft:entity_effect")) return;
            RiftMotesOrb.onParticle(new Vec3(p.x(), p.y(), p.z()));
        });
        Interactions.onBlockClick((pos, right) -> {
            if (!rift() || !Features.on(BUTTONS)) return;
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            BlockPos bp = BlockPos.containing(pos);
            if (BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(bp).getBlock()).getPath().equals("oak_button"))
                WoodenButtonsHelper.onButtonClick(new Vec3(bp.getX(), bp.getY(), bp.getZ()));
        });
        Interactions.onItemClick(right -> {
            if (!right || !rift() || !Features.on(BUTTONS)) return false;
            var mc = Minecraft.getInstance();
            if (mc.player != null && Gain.sbId(mc.player.getMainHandItem()).equals(WoodenButtonsHelper.BLOWGUN)) WoodenButtonsHelper.onBlowgunFire();
            return false;
        });
        Interactions.onSlotClick((screen, slot, slotId, button) -> {
            if (!rift() || !Features.on(ENIGMA)) return false;
            if (slot != null && slot.container == Minecraft.getInstance().player.getInventory()) return false;
            String name = slot == null || slot.getItem().isEmpty() ? null : GameState.strip(LegacyText.name(slot.getItem()));
            return EnigmaSoulWaypoints.onSlotClick(slot == null ? slotId : slot.getContainerSlot(), name);
        });
        Safe.attackEntity("Rift.attackEntity", (player, level, hand, entity, hit) -> {
            if (level.isClientSide() && rift() && Features.on(PUNCHCARD) && entity instanceof Player p && p.getUUID().version() != 2) {
                PunchcardHighlight.reverse = b(PUNCHCARD, "reverse", false);
                PunchcardHighlight.onPunch(p.getName().getString());
            }
            return InteractionResult.PASS;
        });

        TIMER_HUD = RiftCards.timerCard();
        CRUX_HUD = RiftCards.cruxCard();
        VERMIN_HUD = RiftCards.verminCard(() -> {
            VerminTracker.showOutsideWestVillage = b(VERMIN, "showOutsideWestVillage", false);
            VerminTracker.showWithoutVacuum = b(VERMIN, "showWithoutVacuum", false);
            return VerminTracker.shouldShowDisplay() ? VerminTracker.display(false) : List.of();
        });
        PUNCH_HUD = RiftCards.punchcardCard(() -> {
            PunchcardHighlight.reverseGUI = b(PUNCHCARD, "reverseGUI", false);
            return hasPunchcard ? List.of(PunchcardHighlight.display()) : List.of();
        });
        UBIK_HUD = RiftCards.ubikCard();
        HudLayout.add(TIMER_HUD);
        HudLayout.add(CRUX_HUD);
        HudLayout.add(VERMIN_HUD);
        HudLayout.add(PUNCH_HUD);
        HudLayout.add(UBIK_HUD);
        RiftPack.register();

        Safe.commands("Rift.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("resetpunchcard").executes(c -> {
                    PunchcardHighlight.reset();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fReset the Punchcard Artifact player list."));
                    return 1;
                }))
                .then(literal("enigma")
                        .then(literal("markallfound").executes(c -> {
                            EnigmaSoulWaypoints.markEvery(true);
                            c.getSource().sendFeedback(Component.literal("§b[SBC] §fMarked every Enigma Soul as found."));
                            return 1;
                        }))
                        .then(literal("markallmissing").executes(c -> {
                            EnigmaSoulWaypoints.markEvery(false);
                            c.getSource().sendFeedback(Component.literal("§b[SBC] §fMarked every Enigma Soul as missing."));
                            return 1;
                        }))
                        .then(literal("markclosestfound").executes(c -> {
                            c.getSource().sendFeedback(Component.literal(EnigmaSoulWaypoints.markClosest(true) ? "§b[SBC] §fMarked the closest Enigma Soul as found." : "§b[SBC] §fNo Enigma Soul within 4 blocks."));
                            return 1;
                        }))
                        .then(literal("markclosestmissing").executes(c -> {
                            c.getSource().sendFeedback(Component.literal(EnigmaSoulWaypoints.markClosest(false) ? "§b[SBC] §fMarked the closest Enigma Soul as missing." : "§b[SBC] §fNo Enigma Soul within 4 blocks."));
                            return 1;
                        })))
                .then(literal("resetvermintracker").executes(c -> {
                    VerminTracker.reset();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fReset the Vermin Tracker session."));
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("resetpunchcard", "Reset the Rift Punchcard Artifact player list");
        dev.zmuzy.sbc.core.Commands.describe("resetvermintracker", "Reset the Vermin Tracker session");
        dev.zmuzy.sbc.core.Commands.describe("enigma", "Mark Enigma Souls as found or missing, all of them or the closest");
    }

    static AABB block(Vec3 v) {
        return new AABB(v, v.add(1, 1, 1));
    }
}
