// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.crimson;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.chat.Party;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.nav.Navigator;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.render.WorldDraw;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// glue for the skyhanni crimson isle port: mc world side, features, chat/sound hooks, menus, entity scans
// (minibosses, beacons, vanquishers, heavy pearls, sponges), hud and commands
public final class Crimson {
    public static final String PANEL = "crimson_panel", MINIBOSS = "crimson_miniboss", VANQUISHER = "crimson_vanquisher", REPUTATION = "crimson_reputation",
            RESCUE = "crimson_rescue", HELPERS = "crimson_npc_helpers", VOLCANO = "crimson_volcano", SULPHUR = "crimson_sulphur_box", MATRIARCH = "crimson_matriarch", KUUDRA = "crimson_kuudra_api";

    private static HudElement PANEL_HUD, MINIBOSS_HUD, VOLCANO_HUD;
    private static long lastSecond, lastFetch, lastWidget;
    private static int lastMenuId = -1;
    private static String lastMenuTitle = "";
    private static int lastMenuHash;
    private static String lastIsland = "", lastProfile = "";
    private static final Map<Integer, Boolean> vanquisherAlive = new HashMap<>();
    private static final Set<Integer> skullStands = new HashSet<>();

    private Crimson() {}

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

    // reputation helper panel shows what quest tracking reads, so read it if either is on
    private static boolean quests() {
        return Features.on(REPUTATION) || Features.on(PANEL);
    }

    static boolean crimson() {
        return "CRIMSON_ISLE".equals(GameState.island) || SbcMod.DEV;
    }

    // --- world ---

    static final class McWorld implements CrimsonEnv.World {
        private static Minecraft mc() {
            return Minecraft.getInstance();
        }

        @Override public boolean inSkyBlock() { return GameState.inSkyBlock; }
        @Override public String island() { return GameState.island; }
        @Override public String area() { return GameState.area; }
        @Override public List<String> scoreboardFormatted() { return GameState.scoreboardFormatted; }
        @Override public List<String> tab() { return GameState.tab; }

        @Override public Vec3 playerPos() {
            var p = mc().player;
            return p == null ? Vec3.ZERO : p.position();
        }

        @Override public String playerName() { return mc().getUser().getName(); }
        @Override public boolean inParty() { return !Party.members().isEmpty(); }

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

        @Override public void title(String title, String subtitle) {
            var mc = mc();
            mc.gui.hud.setTimes(5, 40, 10);
            mc.gui.hud.setTitle(Component.literal(title));
            mc.gui.hud.setSubtitle(Component.literal(subtitle == null ? "" : subtitle));
        }

        @Override public void sendCommand(String command) {
            var c = mc().getConnection();
            if (c != null) c.sendCommand(command);
        }

        @Override public List<CrimsonEnv.ItemCount> lowerInventory() {
            List<CrimsonEnv.ItemCount> out = new ArrayList<>();
            var p = mc().player;
            if (p == null) return out;
            var inv = p.getInventory();
            for (int s = 0; s < inv.getContainerSize(); s++) {
                ItemStack st = inv.getItem(s);
                if (st.isEmpty()) continue;
                out.add(new CrimsonEnv.ItemCount(GameState.strip(LegacyText.name(st)), Gain.sbId(st), st.getCount()));
            }
            return out;
        }

        @Override public Store.CrimsonProfile profile() {
            String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
            return Store.I.crimson.computeIfAbsent(key, k -> new Store.CrimsonProfile());
        }
    }

    // --- chat ---

    private static ChatBus.Result onChat(ChatBus.Line line) {
        // the DOWN! lines want the white their spaces have
        String formatted = io.muzy.sbc.core.LegacyText.whiteStart(io.muzy.sbc.core.LegacyText.of(line.component()));
        String clean = line.plain();
        if (GameState.inSkyBlock) KuudraApi.onChat(formatted);
        if (Features.on(HELPERS)) {
            CrimsonHelpers.pabloHelper = b(HELPERS, "pablo", true);
            CrimsonHelpers.sirihHelper = b(HELPERS, "sirih", true);
            CrimsonHelpers.avoriusHelper = b(HELPERS, "avorius", true);
            CrimsonHelpers.onChat(clean);
        }
        if (!crimson()) return ChatBus.PASS;
        if (Features.on(MINIBOSS)) CrimsonMinibossRespawnTimer.onChat(formatted);
        if (Features.on(VANQUISHER)) {
            VanquisherApi.onChat(clean);
            VanquisherWaypointShare.readGlobalChat = b(VANQUISHER, "readGlobalChat", false);
            if (VanquisherWaypointShare.onChat(clean)) return ChatBus.CANCEL;
        }
        if (quests()) DailyQuestHelper.onChat(formatted);
        if (Features.on(RESCUE)) {
            RescueMissionWaypoints.hostagePath = b(RESCUE, "hostagePath", true);
            RescueMissionWaypoints.agentPath = b(RESCUE, "agentPath", true);
            RescueMissionWaypoints.variant = i(RESCUE, "variant", 1);
            RescueMissionWaypoints.onChat(clean, formatted);
        }
        return ChatBus.PASS;
    }

    // --- tick ---

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!GameState.island.equals(lastIsland)) {
            lastIsland = GameState.island;
            onWorldChange();
        }
        if (!GameState.profileName.equals(lastProfile)) {
            lastProfile = GameState.profileName;
            RescueMissionWaypoints.onProfileJoin();
            DailyQuestHelper.load();
        }
        long now = System.currentTimeMillis();
        if (GameState.inSkyBlock) KuudraApi.onScoreboard(GameState.scoreboardFormatted);
        if (!crimson()) return;
        readMenu(mc);
        scanEntities(mc);
        if (now - lastWidget >= 1000) {
            lastWidget = now;
            ReputationApi.onTab(GameState.tab);
            if (quests()) DailyQuestHelper.loadFromTabList(GameState.tab);
            if (Features.on(VOLCANO)) CrimsonHelpers.onTab(GameState.tab);
        }
        if (now - lastSecond >= 1000) {
            lastSecond = now;
            VanquisherApi.onSecondPassed();
            VanquisherWaypointShare.onSecondPassed();
            if (Features.on(MINIBOSS)) CrimsonMinibossRespawnTimer.onSecondPassed(minibossScan(mc));
            if (quests() && now - lastFetch >= 3000) {
                lastFetch = now;
                DailyQuestHelper.checkInventoryForFetchItem(CrimsonEnv.world.lowerInventory());
            }
            if (Features.on(SULPHUR)) scanSponges(mc);
        }
        if (Features.on(MATRIARCH)) tickMatriarch(mc);
    }

    private static void onWorldChange() {
        KuudraApi.onWorldChange();
        CrimsonMinibossRespawnTimer.onWorldChange();
        VanquisherApi.onWorldChange();
        VanquisherWaypointShare.onWorldChange();
        RescueMissionWaypoints.onIslandChange();
        vanquisherAlive.clear();
        skullStands.clear();
        spongeLocations = List.of();
        closestSponge = null;
        pearls.clear();
        lastMenuId = -1;
    }

    private static CrimsonMinibossRespawnTimer.Scan minibossScan(Minecraft mc) {
        return new CrimsonMinibossRespawnTimer.Scan() {
            @Override public int bossDistance(CrimsonMinibossRespawnTimer.MiniBoss boss) {
                return Crimson.bossDistance(mc, boss);
            }

            @Override public boolean beacon(CrimsonMinibossRespawnTimer.MiniBoss boss) {
                return beaconInArea(mc, boss.area);
            }

            @Override public boolean areaLoaded(CrimsonMinibossRespawnTimer.MiniBoss boss) {
                return Crimson.areaLoaded(mc, boss.area);
            }
        };
    }

    // distance to a mob named like the boss, -1 if the client has none in its area
    // name tag floats above the mob so extra height. "[Lv200] Bladesoul 20M/20M❤" has a level or hearts, a sign
    // or npc line doesnt
    private static int bossDistance(Minecraft mc, CrimsonMinibossRespawnTimer.MiniBoss boss) {
        String wanted = boss.displayName.toLowerCase(java.util.Locale.ROOT);
        for (Entity e : mc.level.getEntities((Entity) null, boss.area.inflate(0, 6, 0))) {
            if (!e.hasCustomName()) continue;
            String plain = GameState.strip(e.getName().getString());
            if (plain.toLowerCase(java.util.Locale.ROOT).contains(wanted) && (plain.contains("[Lv") || plain.contains("❤")))
                return (int) Math.round(Math.sqrt(e.distanceToSqr(mc.player)));
        }
        return -1;
    }

    private static boolean areaLoaded(Minecraft mc, AABB area) {
        int minCx = ((int) Math.floor(area.minX)) >> 4, maxCx = ((int) Math.floor(area.maxX)) >> 4;
        int minCz = ((int) Math.floor(area.minZ)) >> 4, maxCz = ((int) Math.floor(area.maxZ)) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++)
                if (mc.level.getChunkSource().getChunkNow(cx, cz) == null) return false;
        return true;
    }

    // for the dump: every named mob and beacon near you, to check what the scans can see
    public static java.util.List<String> debugScan(Minecraft mc) {
        java.util.List<String> out = new java.util.ArrayList<>(CrimsonMinibossRespawnTimer.debug());
        if (mc.level == null || mc.player == null) return out;
        int named = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!e.hasCustomName() || named >= 60) continue;
            String plain = GameState.strip(e.getName().getString());
            if (!plain.contains("[Lv") && !plain.contains("❤")) continue;
            named++;
            out.add("mob " + plain + " | " + e.getClass().getSimpleName() + " | " + e.blockPosition().toShortString() + " | " + Math.round(Math.sqrt(e.distanceToSqr(mc.player))) + "m");
        }
        int r = mc.options.renderDistance().get(), cx = mc.player.blockPosition().getX() >> 4, cz = mc.player.blockPosition().getZ() >> 4, loaded = 0;
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++) {
                var chunk = mc.level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                loaded++;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet())
                    if (e.getValue() instanceof BeaconBlockEntity) out.add("beacon " + e.getKey().toShortString());
            }
        out.add("loaded chunks around you: " + loaded + ", render distance " + r);
        return out;
    }

    // EntityUtils.getAllTileEntities() filtered to beacons in the area
    private static boolean beaconInArea(Minecraft mc, AABB area) {
        int minCx = ((int) Math.floor(area.minX)) >> 4, maxCx = ((int) Math.floor(area.maxX)) >> 4;
        int minCz = ((int) Math.floor(area.minZ)) >> 4, maxCz = ((int) Math.floor(area.maxZ)) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++) {
                var chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet())
                    if (e.getValue() instanceof BeaconBlockEntity && area.contains(Vec3.atLowerCornerOf(e.getKey()))) return true;
            }
        return false;
    }

    // VanquisherApi's entity side: skull stands (spawn marker) and mobs named Vanquisher (spawn / death / despawn)
    private static void scanEntities(Minecraft mc) {
        if (!Features.on(VANQUISHER) || mc.player.tickCount % 5 != 0) return;
        AABB box = mc.player.getBoundingBox().inflate(40);
        Set<Integer> seen = new HashSet<>();
        for (Entity e : mc.level.getEntities((Entity) null, box)) {
            if (!(e instanceof ArmorStand st)) continue;
            if (st.getItemBySlot(EquipmentSlot.HEAD).is(Items.WITHER_SKELETON_SKULL) && skullStands.add(st.getId())) VanquisherApi.onSkullStand(st.position());
            if (!st.hasCustomName() || !GameState.strip(st.getName().getString()).contains("Vanquisher")) continue;
            // the mob under its name tag
            LivingEntity base = null;
            for (Entity c : mc.level.getEntities(st, st.getBoundingBox().inflate(0.5, 2.5, 0.5)))
                if (c instanceof LivingEntity le && !(c instanceof ArmorStand) && !(c instanceof net.minecraft.world.entity.player.Player)) base = le;
            int key = base != null ? base.getId() : st.getId();
            seen.add(key);
            boolean alive = base == null || !base.isDeadOrDying();
            vanquisherAlive.put(key, alive);
            var data = VanquisherApi.onMobSpawn(key, base != null ? base.position() : st.position());
            data.position = base != null ? base.position() : st.position();
        }
        for (Integer key : new ArrayList<>(vanquisherAlive.keySet())) {
            if (seen.contains(key)) continue;
            VanquisherApi.onMobDeSpawn(key, vanquisherAlive.remove(key));
        }
    }

    // --- sulphur skitter box, from skyhanni SulphurSkitterBox ---

    private static List<Vec3> spongeLocations = List.of();
    private static Vec3 closestSponge;
    private static AABB spongeBox;
    private static final int RADIUS = 4;

    private static void scanSponges(Minecraft mc) {
        List<Vec3> out = new ArrayList<>();
        BlockPos c = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-15, -15, -15), c.offset(15, 15, 15)))
            if (mc.level.getBlockState(p).is(Blocks.SPONGE)) out.add(Vec3.atLowerCornerOf(p));
        spongeLocations = out;
        Vec3 player = mc.player.position();
        Vec3 best = null;
        for (Vec3 v : spongeLocations) if (best == null || v.distanceTo(player) < best.distanceTo(player)) best = v;
        if (best == null || !best.equals(closestSponge)) {
            closestSponge = best;
            spongeBox = best == null ? null : new AABB(best.add(-RADIUS, -RADIUS, -RADIUS), best.add(RADIUS + 1, RADIUS + 1, RADIUS + 1)).inflate(0.001);
        }
    }

    private static void drawSponge(WorldDraw w) {
        if (closestSponge == null || spongeBox == null) return;
        if (closestSponge.distanceTo(w.mc.player.position()) >= 50) return;
        if (b(SULPHUR, "onlyWithRods", true)) {
            String id = Gain.sbId(w.mc.player.getMainHandItem());
            if (!(id.contains("ROD") && (id.contains("LAVA") || id.contains("MAGMA") || id.contains("INFERNO") || id.contains("HELLFIRE") || id.contains("SULPHUR") || id.contains("POLISHED_TOPAZ") || id.contains("STARTER_LAVA")))) return;
        }
        int color = io.muzy.sbc.rift.RiftScan.color(SULPHUR, "crimson_sulphur_box.color", 0xFFFF4500);
        Feature f = Features.get(SULPHUR);
        var type = f == null ? null : f.option("crimson_sulphur_box.type");
        String look = type == null ? "Both" : type.choice();
        if (!look.equals("Wireframe")) w.box(spongeBox, (color & 0xFFFFFF) | 0x60000000, true, false);
        if (!look.equals("Filled")) w.box(spongeBox, color | 0xFF000000, false, false);
    }

    // --- matriarch heavy pearls, from skyhanni MatriarchHelper (simple line) ---

    private record Pearl(ArmorStand stand, Slime slime) {
        Vec3 location() {
            return slime.position().add(0, 1.2, 0);
        }
    }

    private static final List<Pearl> pearls = new ArrayList<>();
    private static String chonkyPearlSkin;

    private static String chonkyPearlSkin() {
        if (chonkyPearlSkin != null) return chonkyPearlSkin;
        var o = Repo.constant("Skulls.json");
        chonkyPearlSkin = o != null && o.has("HEAVY_PEARL") ? o.get("HEAVY_PEARL").getAsString() : "";
        return chonkyPearlSkin;
    }

    private static boolean wearsSkull(ArmorStand st, String texture) {
        if (texture.isEmpty()) return false;
        ItemStack head = st.getItemBySlot(EquipmentSlot.HEAD);
        var profile = head.get(DataComponents.PROFILE);
        if (profile == null) return false;
        try {
            for (var pr : profile.partialProfile().properties().get("textures")) if (texture.equals(pr.value())) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void tickMatriarch(Minecraft mc) {
        if (!GameState.area.equals("Belly of the Beast")) {
            pearls.clear();
            return;
        }
        if (mc.player.tickCount % 10 != 0) return;
        pearls.removeIf(p -> p.slime().isRemoved() || p.stand().isRemoved());
        String texture = chonkyPearlSkin();
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(60))) {
            if (!wearsSkull(st, texture)) continue;
            boolean known = false;
            for (Pearl p : pearls) if (p.stand() == st) known = true;
            if (known) continue;
            var slimes = mc.level.getEntitiesOfClass(Slime.class, st.getBoundingBox());
            if (slimes.isEmpty()) continue; // decorative midair pearls have no slime
            Slime slime = slimes.get(0);
            boolean shared = false;
            for (Pearl p : pearls) if (p.slime() == slime) shared = true; // stacked stands share one slime
            if (shared) continue;
            pearls.add(new Pearl(st, slime));
        }
        if (pearls.size() > 3) pearls.clear();
    }

    private static void drawMatriarch(WorldDraw w) {
        if (pearls.isEmpty() || !GameState.area.equals("Belly of the Beast")) return;
        int highlight = io.muzy.sbc.rift.RiftScan.color(MATRIARCH, "crimson_matriarch.highlight_color", 0xFF55FFFF), lineColor = io.muzy.sbc.rift.RiftScan.color(MATRIARCH, "crimson_matriarch.line_color", 0xFF55FF55);
        if (b(MATRIARCH, "crimson_matriarch.highlight", true)) for (Pearl p : pearls) w.box(p.slime().getBoundingBox().inflate(0.1), highlight, true, true);
        if (!b(MATRIARCH, "crimson_matriarch.line", true)) return;
        List<Pearl> sorted = new ArrayList<>(pearls);
        sorted.sort(java.util.Comparator.comparingDouble(p -> p.location().y));
        Vec3 prev = w.eye;
        for (Pearl p : sorted) {
            w.line(prev, p.location(), lineColor, 10f, false);
            prev = p.location();
        }
        if (b(MATRIARCH, "crimson_matriarch.simple_line", false)) return;
        // then on to the way out: graph node named Heavy Pearls
        var g = Repo.graph("CRIMSON_ISLE");
        var exit = g == null ? null : g.byName("Heavy Pearls");
        if (exit == null) return;
        for (Vec3 step : g.path(g.nearest(prev), exit)) {
            Vec3 at = step.add(0.5, 0.5, 0.5);
            w.line(prev, at, lineColor, 10f, false);
            prev = at;
        }
    }

    // --- menus ---

    private static void readMenu(Minecraft mc) {
        var screen = mc.gui.screen();
        if (screen == null || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            lastMenuId = -1;
            lastMenuTitle = "";
            lastMenuHash = 0;
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
        lastMenuId = menu.containerId;
        lastMenuTitle = title;
        lastMenuHash = hash;
        if (hash == 0 || n <= 22) return;
        ItemStack s22 = menu.slots.get(22).getItem();
        if (Features.on(RESCUE)) RescueMissionWaypoints.onInventory(title, s22.isEmpty() ? null : LegacyText.name(s22));
        if (quests()) {
            // QuestLoader.checkInventory: only at the two town boards (graph areas Community Center / Dragontail)
            String area = GameState.area;
            if (area.equals("Community Center") || area.equals("Dragontail")) DailyQuestHelper.onTownBoardMenu(title, s22.isEmpty() ? null : LegacyText.lore(s22));
        }
    }

    // dojo "Challenges" menu: highlight the quest's challenge. called from SlotOverlayMixin
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (!crimson() || !Features.on(REPUTATION)) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof ChestMenu) || slot.container == mc.player.getInventory()) return;
        if (!lastMenuTitle.equals("Challenges") || !GameState.area.equals("Dojo")) return;
        var dojo = DailyQuestHelper.getQuest(DailyQuestHelper.QuestCategory.DOJO);
        if (dojo == null || dojo.state != DailyQuestHelper.QuestState.ACCEPTED) return;
        ItemStack st = slot.getItem();
        if (!st.isEmpty() && LegacyText.name(st).contains(dojo.target)) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8055FFFF);
    }

    // --- render ---

    static Vec3 questBoard() {
        var f = ReputationApi.factionType();
        if (f == null) return null;
        var g = Repo.graph("CRIMSON_ISLE");
        if (g == null) return null;
        var n = g.byName(f.questBoardNode());
        return n == null ? null : n.pos();
    }

    private static net.minecraft.client.KeyMapping holdKey;

    public static void keys(net.minecraft.client.KeyMapping.Category cat) {
        holdKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(new net.minecraft.client.KeyMapping("key.sbcompanion.reputation_hold", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), cat));
    }

    // isHotkeyHeld: over the game or inventory only. key can be a mouse button, read like FarmingKeys.held does
    private static boolean holdKeyDown() {
        var mc = Minecraft.getInstance();
        var screen = mc.gui.screen();
        if (holdKey == null || holdKey.isUnbound() || (screen != null && !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen))) return false;
        var k = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(holdKey);
        if (k.getType() == InputConstants.Type.MOUSE) return io.muzy.sbc.mc.KeyHeld.mouseDown(k.getValue());
        return InputConstants.isKeyDown(k.getValue());
    }

    // with "only while holding the key" on, panel + markers wait for the key
    static boolean reputationShown() {
        return !b(PANEL, "crimson_panel.use_hotkey", false) || holdKeyDown();
    }

    private static void drawReputation(WorldDraw w) {
        if (!reputationShown()) return;
        DailyQuestHelper.showLocations = b(REPUTATION, "showLocation", true);
        Vec3 board = questBoard();
        DailyQuestHelper.render(w, board, loc -> {
            for (ArmorStand st : w.mc.level.getEntitiesOfClass(ArmorStand.class, new AABB(loc, loc).inflate(40)))
                if (st.hasCustomName() && GameState.strip(st.getName().getString()).contains("❤")) return true;
            return false;
        });
    }

    // --- register ---

    static void registerHud() {
        PANEL_HUD = CrimsonCards.reputation();
        MINIBOSS_HUD = CrimsonCards.miniboss();
        VOLCANO_HUD = CrimsonCards.volcano();
        HudLayout.add(PANEL_HUD);
        HudLayout.add(MINIBOSS_HUD);
        HudLayout.add(VOLCANO_HUD);
        HudLayout.add(Ashfang.card());
    }

    public static void register() {
        CrimsonEnv.world = new McWorld();
        ReputationData.load();
        DailyQuestHelper.loadData();
        VanquisherWaypointShare.init();
        KuudraApi.onKuudraComplete(DailyQuestHelper::onKuudraComplete);
        RescueMissionWaypoints.navigate = (node, pos) -> {
            if (node != null) Navigator.navigateTo("CRIMSON_ISLE", node);
            else if (pos != null) Navigator.navigate(new Navigator.Target("CRIMSON_ISLE", "start of parkour", pos));
        };

        Features.add(new Feature(PANEL, "Crimson Isle", "Reputation Helper", "Today's five faction quests with their state and progress, the five daily minibosses and the five Kuudra tiers; white markers at quest spots, minibosses and the Town Board.", "SkyHanni: Crimson Isle Reputation Helper")
                .when(Crimson::crimson).world(Crimson::drawReputation)
                .option(Option.bool("hideComplete", "Hide completed", "Hide collected quests and done bosses.", false))
                .option(Option.bool("showLocation", "Show locations", "Mark quest, miniboss, Kuudra and Town Board spots in the world.", true))
                .option(Option.bool("crimson_panel.use_hotkey", "Only while holding the key", "Shows the panel and its markers only while you hold the key from Controls.", false)));
        Features.add(new Feature(REPUTATION, "Crimson Isle", "Quest tracking", "Reads the Faction Quests tab widget, the Town Board menus, the completion lines, fetch items in your inventory, trophy fish and miniboss kills.", "SkyHanni: Daily Quest Helper")
                .when(Crimson::crimson));
        Features.add(new Feature(MINIBOSS, "Crimson Isle", "Miniboss respawn timer", "Per area: when the miniboss respawns (2 min after DOWN!), a possible window from the beacon, SPAWNED!, Soon!.", "SkyHanni: Crimson Miniboss Respawn Timer")
                .when(Crimson::crimson));
        Features.add(new Feature(VANQUISHER, "Crimson Isle", "Vanquisher waypoint share", "Announces your own Vanquisher, and your key or a click shares it to the party; shared Vanquishers become 60 s waypoints with a beam; 'Vanquisher dead!' removes them.", "SkyHanni: Vanquisher Waypoint Share")
                .when(Crimson::crimson).world(VanquisherWaypointShare::render)
                .option(Option.bool("readGlobalChat", "Read all chat", "Also read and send shares in all chat, not only the party.", false)));
        Features.add(new Feature(RESCUE, "Crimson Isle", "Rescue Mission waypoints", "The hostage parkour for your faction and tier (read from the Town Board's Rescue menu) once the Undercover Agent briefs you; navigation to the agent and the board.", "SkyHanni: Rescue Mission Waypoints")
                .when(Crimson::crimson).world(w -> {
                    RescueMissionWaypoints.style(b(RESCUE, "crimson_rescue.rainbow", true), io.muzy.sbc.rift.RiftScan.color(RESCUE, "crimson_rescue.color", 0x3C0000FF), i(RESCUE, "crimson_rescue.look_ahead", 2));
                    RescueMissionWaypoints.render(w);
                })
                .option(Option.bool("hostagePath", "Hostage path", "Show the parkour to the hostage.", true))
                .option(Option.bool("agentPath", "Agent path", "Navigate to the Undercover Agent.", true))
                .option(Option.integer("variant", "S-rank variant", "Which of the two S-rank parkours (Barbarian).", 1, 1, 2))
                .option(Option.integer("crimson_rescue.look_ahead", "Look ahead", "How many waypoints to show in front of you.", 2, 1, 10))
                .option(Option.bool("crimson_rescue.rainbow", "Rainbow", "", true))
                .option(Option.color("crimson_rescue.color", "Single colour", "Used when Rainbow is off.", 0x3C0000FF)));
        Features.add(new Feature(HELPERS, "Crimson Isle", "NPC sack helpers", "A click-to-grab-from-sacks line when Pablo asks for a flower, Sirih oinks (Barbarian: Sulphur) or Avorius hints (Mage: Cup of Blood).", "SkyHanni: Pablo / Sirih / Avorius helper")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("pablo", "Pablo", "", true))
                .option(Option.bool("sirih", "Sirih", "", true))
                .option(Option.bool("avorius", "Avorius", "", true)));
        Features.add(new Feature(VOLCANO, "Crimson Isle", "Volcano explosivity", "The volcano status from the tab list.", "SkyHanni: Volcano Explosivity Display")
                .when(Crimson::crimson));
        Features.add(new Feature(SULPHUR, "Crimson Isle", "Sulphur Skitter box", "A box around the nearest sponge where Sulphur Skitters can be fished.", "SkyHanni: Sulphur Skitter Box")
                .when(Crimson::crimson).world(Crimson::drawSponge)
                .option(Option.bool("onlyWithRods", "Only with a lava rod", "", true))
                .option(Option.color("crimson_sulphur_box.color", "Colour", "", 0xFFFF4500))
                .option(Option.choice("crimson_sulphur_box.type", "Look", "", "Both", "Both", "Wireframe", "Filled")));
        Features.add(new Feature(MATRIARCH, "Crimson Isle", "Heavy pearls", "In the Belly of the Beast, highlights the three heavy pearls and draws a line through them (lowest first).", "SkyHanni: Matriarch Helper (simple line)")
                .when(Crimson::crimson).world(Crimson::drawMatriarch)
                .option(Option.bool("crimson_matriarch.highlight", "Highlight", "Highlights the pearls.", true))
                .option(Option.color("crimson_matriarch.highlight_color", "Highlight colour", "", 0xFF55FFFF))
                .option(Option.bool("crimson_matriarch.line", "Line", "A line to the lowest pearl, on through the others and to the way out.", true))
                .option(Option.color("crimson_matriarch.line_color", "Line colour", "", 0xFF55FF55))
                .option(Option.bool("crimson_matriarch.simple_line", "Only between the pearls", "No line to the way out.", false)));
        Features.add(new Feature(KUUDRA, "Crimson Isle", "Kuudra tier detection", "Knows the Kuudra tier from the sidebar and hears KUUDRA DOWN! (feeds the daily Kuudra list).", "SkyHanni: Kuudra API")
                .alwaysOn().when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Dojo.register();
        DojoRank.register();
        Ashfang.register();
        CrimsonMarks.register();
        KuudraKeys.register();
        KuudraPlayerClick.register();

        ChatBus.register(ChatBus.OBSERVE + 22, "crimson", null, Crimson::onChat);
        Safe.endTick("Crimson.tick", Crimson::tick);
        SoundHooks.listen(s -> {
            if (crimson() && Features.on(VANQUISHER)) VanquisherApi.onSound(s.name(), new Vec3(s.x(), s.y(), s.z()), s.volume(), s.pitch());
            return false;
        });

        registerHud();

        Safe.commands("Crimson.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("sharevanquisher").executes(c -> {
                    VanquisherWaypointShare.sendSpawn();
                    return 1;
                }))
                .then(literal("resetreputation").executes(c -> {
                    DailyQuestHelper.reset();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fReset Reputation Helper."));
                    return 1;
                }))
                .then(literal("rescueboard").executes(c -> {
                    RescueMissionWaypoints.navigateToQuestBoard("try again");
                    return 1;
                }))));
        io.muzy.sbc.core.Commands.describe("sharevanquisher", "Share your Vanquisher's position to the party");
        io.muzy.sbc.core.Commands.describe("resetreputation", "Reset the Crimson Isle Reputation Helper's daily data");
        io.muzy.sbc.core.Commands.describe("rescueboard", "Navigate back to your faction's Quest Board");
    }
}
