// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// whats hidden, marked and glowing in a dungeon. from skyhanni DungeonHideItems, DungeonMobManager,
// HighlightDungeonDeathmite, HighlightClickedBlocks, CleanEnd, TerracottaPhase, BossHideDamageSplash
public final class DungeonObjects {
    private static final String HIDE = "dungeon_hide", STARRED = "dungeon_starred", CLICKED = "dungeon_clicked_blocks", CLEAN = "dungeon_clean_end", MARKS = "dungeon_mob_marks";
    private static final Patterns.P DAMAGE = Patterns.p("SH.combat.damageindicator.damage");
    // from skyblocker DungeonSplitsWidget.DUNGEON_END: boss is dead
    static final Pattern DEFEATED = Pattern.compile("^\\s*☠ Defeated (.+) in 0?([\\dhms ]+?)\\s*(\\(NEW RECORD!\\))?$");
    private static final Patterns.P COMPLETE = Patterns.p("SH.dungeon.completecolorless");
    // from skyblocker DungeonGlowAdder colors
    private static final int LOST_ADVENTURER = 0xFEE15C, SHADOW_ASSASSIN = 0x5B2CB2, ANGRY_ARCHAEOLOGIST = 0x57C2F7, FEL_GLOW = 0xCC00FA;

    private record Clicked(String text, int color, long at) {}

    private static final Map<BlockPos, Clicked> CLICKS = new LinkedHashMap<>();
    private static BlockPos lastChest;
    private static final String[] RANDOM = {"§c", "§6", "§e", "§a", "§2", "§b", "§3", "§9", "§d", "§5"};
    private static final int[] RANDOM_RGB = {0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF00AA00, 0xFF55FFFF, 0xFF00AAAA, 0xFF5555FF, 0xFFFF55FF, 0xFFAA00AA};
    private static int randomIndex;

    private static final Map<Integer, Vec3> STAND_AT = new HashMap<>();
    private static final Map<Integer, Long> STAND_MOVED = new HashMap<>();
    private static final Set<Integer> starred = new HashSet<>(), deathmites = new HashSet<>();
    private static final Map<Integer, Boolean> hideCache = new HashMap<>();
    private static final List<Object[]> hiddenAt = new ArrayList<>();
    private static String fel;
    private static boolean bossDone, chestsSpawned;

    private DungeonObjects() {}

    private static boolean o(String key) {
        return DungeonMarks.opt(HIDE, key);
    }

    private static String clean(ItemStack st) {
        return GameState.strip(st.getHoverName().getString()).trim();
    }

    // from skyhanni DamageIndicatorManager.isDamageSplash
    static boolean isDamageSplashName(String name) {
        return DAMAGE.matcher(name.replace(",", "")).matches();
    }

    private static boolean damageSplash(ArmorStand st) {
        return st.tickCount <= 300 && st.hasCustomName() && st.isAlive() && isDamageSplashName(GameState.strip(st.getName().getString()));
    }

    // from skyhanni DungeonHideItems.onCheckRender with the stands plain name. head + texture = what it wears
    static boolean hiddenStand(String name, String head, String texture, boolean movedLately, java.util.function.Predicate<String> on) {
        if (on.test("superboom") && (name.startsWith("Superboom TNT") || head.equals("Superboom TNT"))) return true;
        if (on.test("blessing") && (name.startsWith("Blessing of ") || same(texture, "DUNGEONS_BLESSING"))) return true;
        if (on.test("revive_stone") && (name.equals("Revive Stone") || same(texture, "DUNGEONS_REVIVE_STONE"))) return true;
        if (on.test("premium_flesh") && (name.equals("Premium Flesh") || same(texture, "DUNGEONS_PREMIUM_FLESH"))) return true;
        if (on.test("skeleton_skull") && head.equals("Skeleton Skull") && !movedLately) return true;
        if (on.test("healer_orbs") && (name.startsWith("DAMAGE ") || name.startsWith("ABILITY DAMAGE ") || name.startsWith("DEFENSE ")
                || same(texture, "DUNGEONS_ABILITY_ORB") || same(texture, "DUNGEONS_SUPPORT_ORB") || same(texture, "DUNGEONS_DAMAGE_ORB"))) return true;
        return on.test("soulweaver") && same(texture, "DUNGEONS_SOUL_WEAVER");
    }

    private static boolean same(String texture, String skull) {
        return !texture.isEmpty() && texture.equals(DungeonMarks.skull(skull));
    }

    // should the game leave this entity out. max once a tick per entity, called from the entity dispatcher
    public static boolean hidden(Entity e) {
        if (!DungeonRun.inDungeon()) return false;
        Boolean c = hideCache.get(e.getId());
        if (c != null) return c;
        boolean h = false;
        try {
            h = compute(e);
        } catch (Throwable t) {
            SbcMod.LOG.warn("dungeon hide failed", t);
        }
        hideCache.put(e.getId(), h);
        return h;
    }

    private static boolean compute(Entity e) {
        var mc = Minecraft.getInstance();
        if (Features.on(CLEAN) && bossDone && e != mc.player) {
            boolean show = DungeonMarks.opt(CLEAN, "guardians") && DungeonRun.isFloor("F3", "M3") && e instanceof Guardian && mc.player != null && mc.player.isShiftKeyDown();
            if (chestsSpawned && ((e instanceof ArmorStand a && !a.hasCustomName()) || e instanceof RemotePlayer)) show = true;
            if (!show) return true;
        }
        if (Features.on("dungeon_livid") && DungeonMarks.opt("dungeon_livid", "hide_wrong") && e instanceof Player p && Puzzles.isWrongLivid(p)) return true;
        if (!Features.on(HIDE)) return false;
        if (e instanceof ItemEntity it) {
            String n = clean(it.getItem());
            return (o("revive_stone") && n.equals("Revive Stone")) || (o("journal") && n.equals("Journal Entry"));
        }
        if (!(e instanceof ArmorStand st)) return false;
        if (damageSplash(st)) {
            if (o("boss_splash") && DungeonRun.inBoss()) return true;
            if (o("terracotta_splash") && DungeonRun.inBoss() && DungeonRun.phase() == DungeonRun.Phase.F6_TERRACOTTA) return true;
        }
        ItemStack head = st.getItemBySlot(EquipmentSlot.HEAD);
        String texture = DungeonMarks.headTexture(head), name = GameState.strip(st.getName().getString()).trim();
        Long moved = STAND_MOVED.get(st.getId());
        boolean movedLately = moved != null && System.currentTimeMillis() - moved < 100;
        boolean hide = hiddenStand(name, head.isEmpty() ? "" : clean(head), texture, movedLately, DungeonObjects::o);
        if (hide && (head.isEmpty() ? "" : clean(head)).equals("Superboom TNT") || hide && !texture.isEmpty()) hiddenAt.add(new Object[]{st.position(), System.currentTimeMillis()});
        if (!hide && o("healer_fairy")) {
            ItemStack hand = st.getItemBySlot(EquipmentSlot.MAINHAND);
            hide = same(DungeonMarks.headTexture(hand), "DUNGEONS_HEALER_FAIRY");
        }
        return hide;
    }

    // --- glow ---

    // from skyblocker DungeonGlowAdder.computeColour: this entitys glow color, -1 = none
    private static final Map<Integer, Integer> GLOW = new HashMap<>();
    private static long glowAt;

    // glow of an entity worked out once every 100ms, render asks every frame for every entity
    public static int glow(Entity e) {
        if (!DungeonRun.inDungeon()) return -1;
        long now = System.currentTimeMillis() / 100;
        if (now != glowAt) {
            glowAt = now;
            GLOW.clear();
        }
        Integer c = GLOW.get(e.getId());
        if (c != null) return c;
        int v = glowNow(e);
        GLOW.put(e.getId(), v);
        return v;
    }

    private static int glowNow(Entity e) {
        boolean star = Features.on(STARRED) && DungeonMarks.opt(STARRED, "glow");
        String name = e.getName().getString();
        if (e instanceof Player && star && !DungeonRun.floor().endsWith("4")) {
            if (name.equals("Lost Adventurer")) return LOST_ADVENTURER;
            if (name.equals("Shadow Assassin")) return SHADOW_ASSASSIN;
            if (name.equals("Diamond Guy")) return ANGRY_ARCHAEOLOGIST;
        }
        if (e instanceof Player p && Puzzles.lividGlows(p)) return Puzzles.lividRgb();
        if (e instanceof Bat && star) return DungeonMarks.color(STARRED, "color", 0xFFF57738) & 0xFFFFFF;
        if (e instanceof ArmorStand a) {
            if (star && a.isMarker() && DungeonMarks.headTexture(a.getItemBySlot(EquipmentSlot.HEAD)).equals(felTexture())) return FEL_GLOW;
            return -1;
        }
        if (star && starred.contains(e.getId())) return DungeonMarks.color(STARRED, "color", 0xFFF57738) & 0xFFFFFF;
        if (e instanceof Player p && Features.on("dungeon_class_glow") && DungeonRun.started()) {
            DungeonRun.Member m = DungeonRun.member(p.getGameProfile().name());
            if (m == null || m.dead()) return -1;
            return classGlow(m.dungeonClass());
        }
        return -1;
    }

    // from skyblocker DungeonClass: each class glow color
    static int classGlow(String cls) {
        return switch (cls) {
            case "Healer" -> 0x820DD1;
            case "Mage" -> 0x36C6E3;
            case "Berserk" -> 0xFA5B16;
            case "Archer" -> 0xED240E;
            case "Tank" -> 0x138717;
            default -> -1;
        };
    }

    private static String felTexture() {
        if (fel != null) return fel;
        fel = "";
        try (var in = DungeonObjects.class.getResourceAsStream("/sbc/dungeon_heads.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            fel = o.get("fel").getAsString();
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read dungeon heads", ex);
        }
        return fel;
    }

    // --- the world ---

    // from skyhanni DungeonCleanEnd onBossDead + onEntityHealthUpdate: the last boss the damage indicator knows
    // at 0.5 health or less
    private static void watchLastBoss() {
        if (bossDone || !Features.on(CLEAN)) return;
        var last = dev.zmuzy.sbc.combat.DamageIndicator.finalBossAtDeath();
        if (last == null) return;
        bossDone = true;
        hideCache.clear();
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §eFloor " + DungeonRun.floor() + " done!"));
    }

    private static void tick(Minecraft mc) {
        watchLastBoss();
        hideCache.clear();
        if (!DungeonRun.inDungeon()) {
            if (!STAND_AT.isEmpty() || bossDone) reset();
            return;
        }
        if (mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        hiddenAt.removeIf(o -> now - (Long) o[1] > 200);
        CLICKS.values().removeIf(c -> now - c.at() > 3000);
        boolean wantSkulls = (Features.on(HIDE) && o("skeleton_skull")) || (Features.on(MARKS) && DungeonMarks.opt(MARKS, "moving_skulls"));
        if (wantSkulls) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ArmorStand st)) continue;
                ItemStack head = st.getItemBySlot(EquipmentSlot.HEAD);
                if (head.isEmpty() || !clean(head).equals("Skeleton Skull")) continue;
                Vec3 at = st.position();
                Vec3 was = STAND_AT.put(st.getId(), at);
                if (was != null && was.distanceToSqr(at) > 0) STAND_MOVED.put(st.getId(), now);
            }
        }
        if (mc.player.tickCount % 5 != 0) return;
        boolean wantStars = Features.on(STARRED);
        boolean wantMites = Features.on(MARKS) && DungeonMarks.opt(MARKS, "deathmites");
        starred.clear();
        deathmites.clear();
        if (!wantStars && !wantMites) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand st) || !st.hasCustomName()) continue;
            String name = st.getName().getString();
            boolean star = wantStars && name.contains("✯"), mite = wantMites && name.contains("Deathmite");
            if (!star && !mite) continue;
            // from skyblocker MobGlow.getArmorStands the other way around: what the name stands over
            for (LivingEntity m : mc.level.getEntitiesOfClass(LivingEntity.class, st.getBoundingBox().inflate(1, 3, 1), x -> !(x instanceof ArmorStand) && x != mc.player && x.getBoundingBox().inflate(0, 2, 0).intersects(st.getBoundingBox()))) {
                if (star) starred.add(m.getId());
                if (mite && m instanceof Silverfish) deathmites.add(m.getId());
            }
        }
    }

    private static void reset() {
        STAND_AT.clear();
        STAND_MOVED.clear();
        starred.clear();
        deathmites.clear();
        hiddenAt.clear();
        CLICKS.clear();
        bossDone = chestsSpawned = false;
        lastChest = null;
        Puzzles.resetLivid();
    }

    private static void drawMarks(WorldDraw w) {
        var mc = w.mc;
        long now = System.currentTimeMillis();
        if (DungeonMarks.opt(MARKS, "moving_skulls")) {
            for (var en : STAND_MOVED.entrySet()) {
                if (now - en.getValue() >= 200) continue;
                Entity e = mc.level.getEntity(en.getKey());
                if (e != null) w.box(new AABB(e.getX() - 0.4, e.getY() + 1.4, e.getZ() - 0.4, e.getX() + 0.4, e.getY() + 2.2, e.getZ() + 0.4), 0xFFFFAA00, true, false);
            }
        }
        for (int id : deathmites) {
            Entity e = mc.level.getEntity(id);
            if (e != null) w.box(e.getBoundingBox(), 0xFFAA0000, true, false);
        }
        if (DungeonMarks.opt(MARKS, "fels")) {
            int color = DungeonMarks.color(MARKS, "fel_color", 0xFFFF00FF);
            boolean line = DungeonMarks.opt(MARKS, "fel_line");
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof ArmorStand st) || !st.isMarker()) continue;
                if (!DungeonMarks.headTexture(st.getItemBySlot(EquipmentSlot.HEAD)).equals(felTexture())) continue;
                Vec3 at = st.position();
                w.waypointBox(at.add(-0.5, -0.23, -0.5), color, false, false, -0.2);
                if (line && at.distanceTo(mc.player.position()) <= 30) w.line(w.eye.add(mc.player.getLookAngle().scale(2)), at.add(0, 0.15, 0), color, 3f, true);
            }
        }
    }

    private static void drawStars(WorldDraw w) {
        if (!DungeonMarks.opt(STARRED, "boxes")) return;
        int color = DungeonMarks.color(STARRED, "color", 0xFFF57738);
        for (int id : starred) {
            Entity e = w.mc.level.getEntity(id);
            if (e != null) w.box(e.getBoundingBox(), color, false, true);
        }
    }

    private static void drawClicks(WorldDraw w) {
        if (DungeonRun.inBoss()) return;
        boolean text = DungeonMarks.opt(CLICKED, "text");
        for (var en : CLICKS.entrySet()) {
            BlockPos p = en.getKey();
            w.filledBox(new AABB(p), en.getValue().color(), 0.5f, true);
            if (text) w.label(en.getValue().text(), Vec3.atCenterOf(p), true);
        }
    }

    // from skyhanni DungeonHighlightClickedBlocks.onDungeonClickedBlock
    static void clicked(BlockPos pos, String type) {
        if (!Features.on(CLICKED) || DungeonRun.inBoss()) return;
        int color;
        String code = "§f";
        if (DungeonMarks.opt(CLICKED, "random")) {
            randomIndex = (randomIndex + 1) % RANDOM.length;
            color = RANDOM_RGB[randomIndex];
            code = RANDOM[randomIndex];
        } else {
            color = switch (type) {
                case "Lever" -> DungeonMarks.color(CLICKED, "lever", 0xFFFFFF55);
                case "Chest" -> DungeonMarks.color(CLICKED, "chest", 0xFF55FF55);
                case "Trapped Chest" -> DungeonMarks.color(CLICKED, "trapped", 0xFF55FF55);
                default -> DungeonMarks.color(CLICKED, "wither", 0xFFAA00AA);
            };
        }
        CLICKS.put(pos, new Clicked(code + "Clicked " + type, color, System.currentTimeMillis()));
        if (type.equals("Chest") || type.equals("Trapped Chest")) lastChest = pos;
    }

    private static final String LEVER_LINE = "You hear the sound of something opening...", LOCKED_LINE = "That chest is locked!";

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!DungeonRun.inDungeon()) return ChatBus.PASS;
        String t = line.plain().trim();
        if (Features.on(CLEAN)) {
            if (!bossDone && DEFEATED.matcher(line.plain()).matches()) {
                bossDone = true;
                hideCache.clear();
                var p = Minecraft.getInstance().player;
                if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §eFloor " + DungeonRun.floor() + " done!"));
            }
            if (COMPLETE.matcher(line.plain()).matches()) chestsSpawned = true;
        }
        if (Features.on(CLICKED) && !DungeonRun.inBoss()) {
            if (t.equals(LEVER_LINE)) return ChatBus.CANCEL;
            if (t.equals(LOCKED_LINE) && lastChest != null) {
                Clicked c = CLICKS.get(lastChest);
                if (c != null) CLICKS.put(lastChest, new Clicked(c.text(), DungeonMarks.color(CLICKED, "locked", 0xFFFF5555), c.at()));
            }
        }
        return ChatBus.PASS;
    }

    // --- particles + sounds ---

    // clean end + terracotta drop all particles, DungeonHideItems only the ones around what it hides
    public static boolean blockParticle(ClientboundLevelParticlesPacket p) {
        if (!Minecraft.getInstance().isSameThread() || !DungeonRun.inDungeon()) return false;
        if (Features.on(CLEAN) && bossDone) return true;
        if (Features.on(HIDE) && o("terracotta_particles") && DungeonRun.inBoss() && DungeonRun.phase() == DungeonRun.Phase.F6_TERRACOTTA) return true;
        if (!Features.on(HIDE) || (!o("superboom") && !o("revive_stone")) || hiddenAt.isEmpty()) return false;
        var type = p.particle().getType();
        if (type != ParticleTypes.FIREWORK && type != ParticleTypes.DUST) return false;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        for (Object[] h : hiddenAt) if (((Vec3) h[0]).distanceTo(at) < 2) return true;
        return false;
    }

    // from skyhanni DungeonCleanEnd.onPlaySound. never true while bossDone comes from the "Defeated" line, see
    // the class comment
    private static boolean onSound(SoundHooks.Sound s) {
        return DungeonRun.inDungeon() && Features.on(CLEAN) && bossDone && !chestsSpawned && s.name().startsWith("block.note_block.");
    }

    public static void register() {
        java.util.function.BooleanSupplier dg = () -> DungeonRun.inDungeon() || SbcMod.DEV;
        Features.add(new Feature("dungeon_objects_tick", "Dungeons", "Dungeon objects background", "Watches what is lying and moving around for the features below.", "").alwaysOn().tick(DungeonObjects::tick));
        Features.add(new Feature(HIDE, "Dungeons", "Hide things in dungeons", "Leaves things out of the world in a dungeon. Each one has its own switch; nothing is hidden until you turn it on.", "SkyHanni: Object Hider").when(dg)
                .option(Option.bool("dungeon_hide.blessing", "Blessings", "Hide blessings lying around.", false))
                .option(Option.bool("dungeon_hide.healer_fairy", "Healer fairy", "Hide the golden fairy that follows the Healer.", false))
                .option(Option.bool("dungeon_hide.healer_orbs", "Healer orbs", "Hide damage, ability damage and defense orbs.", false))
                .option(Option.bool("dungeon_hide.journal", "Journal entries", "Hide journal pages lying around.", false))
                .option(Option.bool("dungeon_hide.premium_flesh", "Premium Flesh", "Hide Premium Flesh lying around.", false))
                .option(Option.bool("dungeon_hide.revive_stone", "Revive Stones", "Hide Revive Stones lying around.", false))
                .option(Option.bool("dungeon_hide.skeleton_skull", "Skeleton skulls", "Hide skeleton skulls lying around; they show when they move.", false))
                .option(Option.bool("dungeon_hide.soulweaver", "Soulweaver skulls", "Hide the skulls that float around you with Soulweaver Gloves.", false))
                .option(Option.bool("dungeon_hide.superboom", "Superboom TNT", "Hide Superboom TNT lying around.", false))
                .option(Option.bool("dungeon_hide.boss_splash", "Damage numbers at bosses", "Hide damage numbers inside the boss room.", false))
                .option(Option.bool("dungeon_hide.terracotta_splash", "Damage numbers on terracottas", "Hide damage numbers during the terracotta phase of Floor 6.", false))
                .option(Option.bool("dungeon_hide.terracotta_particles", "Particles on terracottas", "Hide particles during the terracotta phase of Floor 6.", false)));
        Features.add(new Feature(MARKS, "Dungeons", "Marks on dungeon mobs", "Marks moving skeleton skulls, Deathmites and Fel skulls where you can see them.", "SkyHanni: Object Highlighter").when(dg).world(DungeonObjects::drawMarks)
                .option(Option.bool("dungeon_mob_marks.moving_skulls", "Moving skeleton skulls", "Skulls about to become a Skeletor turn orange.", true))
                .option(Option.bool("dungeon_mob_marks.deathmites", "Deathmites", "Deathmites turn red.", true))
                .option(Option.bool("dungeon_mob_marks.fels", "Fel skulls", "Marks the skulls of Fels that are not active yet.", true))
                .option(Option.color("dungeon_mob_marks.fel_color", "Fel colour", "The colour of the Fel mark and its line.", 0xFFFF00FF))
                .option(Option.bool("dungeon_mob_marks.fel_line", "Line to Fel skulls", "A line to Fel skulls within 30 blocks.", false)));
        Features.add(new Feature(STARRED, "Dungeons", "Starred mobs", "Starred mobs, minibosses and bats glow, which also shows them through walls.", "SkyHanni: Highlight Starred; Skyblocker: Starred Mob Glow").when(dg).world(DungeonObjects::drawStars).defaultOff()
                .option(Option.bool("dungeon_starred.glow", "Glow", "Starred mobs glow in the colour below.", true))
                .option(Option.bool("dungeon_starred.boxes", "Boxes", "Draws a box around starred mobs too.", false))
                .option(Option.color("dungeon_starred.color", "Colour", "The colour of the glow and the boxes.", 0xFFF57738)));
        Features.add(new Feature("dungeon_class_glow", "Dungeons", "Class colour on teammates", "Teammates glow in the colour of their class, which also shows them through walls.", "Skyblocker: Class-based Player Glow").when(dg).defaultOff());
        Feature clicks = Features.add(new Feature(CLICKED, "Dungeons", "Clicked blocks", "Levers, chests and wither essence you click light up for three seconds and say what they were. Hides the lever line in chat.", "SkyHanni: Clicked Blocks").when(dg).world(DungeonObjects::drawClicks).defaultOff()
                .option(Option.bool("dungeon_clicked_blocks.text", "Say what it was", "A short text on the block.", true))
                .option(Option.bool("dungeon_clicked_blocks.random", "Random colours", "A new colour each click instead of the ones below.", false))
                .option(Option.color("dungeon_clicked_blocks.chest", "Chest", "The colour of clicked chests.", 0xFF55FF55))
                .option(Option.color("dungeon_clicked_blocks.trapped", "Trapped chest", "The colour of clicked trapped chests.", 0xFF55FF55))
                .option(Option.color("dungeon_clicked_blocks.locked", "Locked chest", "The colour of a chest that turned out locked.", 0xFFFF5555))
                .option(Option.color("dungeon_clicked_blocks.lever", "Lever", "The colour of clicked levers.", 0xFFFFFF55))
                .option(Option.color("dungeon_clicked_blocks.wither", "Wither essence", "The colour of clicked wither essence.", 0xFFAA00AA)));
        Features.add(new Feature(CLEAN, "Dungeons", "Clean end", "When the block that ends the run appears in chat: hides all mobs and particles, except the chests and other players. Hypixel's music is not stopped, and until that block everything stays as it is.", "SkyHanni: Clean End").when(dg).defaultOff()
                .option(Option.bool("dungeon_clean_end.guardians", "Floor 3 guardians while sneaking", "Guardians of Floor 3 stay visible while you sneak.", false)));
        ChatBus.register(ChatBus.FILTER - 4, "dungeonobjects", null, DungeonObjects::onChat);
        SoundHooks.listen(DungeonObjects::onSound);
    }
}
