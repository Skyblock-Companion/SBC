// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.world;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.Party;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.dungeon.DungeonMarks;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

// stuff in the world you can choose not to see. one feature with a line each, all off at start. from skyhanni
// misc/ParticleHider, MiscFeatures (explosions, fire overlay), ExpOrbsOnGroundHider...
public final class WorldHiders {
    public static final String ID = "world_hide";
    // from skyhanni MiscFeatures.chickenRacePattern, on the colorless action bar
    private static final Pattern CHICKEN_RACE = Pattern.compile("CHICKEN RACING.*");
    // from skyhanni MobUtils.defaultArmorStandName
    private static final String DEFAULT_STAND = "Armor Stand";

    // read every tick, what the hooks ask
    private static boolean expOrbs, explosions, fire, temporary, lesser, far, blaze, redstone, enderman, farParticles, fireBlock, fireball, smoke, blocks, blocksGarden, helmetOnly;
    private static boolean anyEntity, anyPacket, flames;
    private static String armor = "Off";
    private static Set<Integer> farHidden = Set.of(), lesserStands = Set.of();
    private static List<ArmorStand> lesserAt = List.of();
    private static int ticks, flameHeight = 100, flameOpacity = 100;

    private WorldHiders() {}

    private static boolean b(Feature f, String key) {
        return f.option(key).bool();
    }

    private static void read() {
        Feature f = Features.get(ID);
        boolean on = f != null && f.active() && (GameState.inSkyBlock || SbcMod.DEV);
        expOrbs = on && b(f, "exp_orbs");
        explosions = on && b(f, "explosions");
        fire = on && b(f, "fire_overlay");
        temporary = on && b(f, "temporary_stands");
        lesser = on && b(f, "lesser_orb");
        far = on && b(f, "far_entities");
        blaze = on && b(f, "blaze");
        redstone = on && b(f, "redstone");
        enderman = on && b(f, "enderman");
        farParticles = on && b(f, "far_particles");
        fireBlock = on && b(f, "fire_block");
        fireball = on && b(f, "fireball");
        smoke = on && b(f, "smoke");
        blocks = on && b(f, "block_break");
        blocksGarden = on && b(f, "block_break_garden");
        armor = on ? f.option("armor").choice() : "Off";
        helmetOnly = on && b(f, "armor_helmet");
        flameHeight = on ? f.option("flame_height").integer() : 100;
        flameOpacity = on ? f.option("flame_opacity").integer() : 100;
        flames = flameHeight != 100 || flameOpacity != 100;
        anyEntity = expOrbs || temporary || lesser || far;
        anyPacket = explosions || lesser || redstone || farParticles || fireball || smoke;
    }

    private static void tick(Minecraft mc) {
        read();
        if (mc.level == null || mc.player == null) return;
        if (far) farHidden = farScan(mc);
        else if (!farHidden.isEmpty()) farHidden = Set.of();
        if (lesser) {
            if (++ticks % 10 == 0) lesserScan(mc);
        } else if (!lesserStands.isEmpty()) {
            lesserStands = Set.of();
            lesserAt = List.of();
        }
    }

    // from skyhanni LesserOrbHider: the head in the stands hand says what it is
    private static void lesserScan(Minecraft mc) {
        String texture = DungeonMarks.skull("LESSER_ORB");
        Set<Integer> ids = new HashSet<>();
        List<ArmorStand> at = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand st && DungeonMarks.headTexture(st.getItemBySlot(EquipmentSlot.MAINHAND)).equals(texture)) {
                ids.add(e.getId());
                at.add(st);
            }
        }
        lesserStands = ids;
        lesserAt = at;
    }

    // from skyhanni HideFarEntities onTick + updateNeverHide
    private static Set<Integer> farScan(Minecraft mc) {
        Feature f = Features.get(ID);
        int max = Math.max(1, f.option("far_max").integer());
        int min = Math.max(3, f.option("far_min").integer());
        Set<String> party = Party.members();
        String island = GameState.island;
        boolean dungeon = "CATACOMBS".equals(island), kuudra = "KUUDRA".equals(island), winter = "WINTER".equals(island), mines = "DWARVEN_MINES".equals(island);
        List<Entity> all = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || e.distanceTo(mc.player) <= min) continue;
            if (!neverHide(e, party, dungeon, kuudra, winter, mines)) all.add(e);
        }
        if (all.size() <= max) return Set.of();
        all.sort((a, b) -> Float.compare(a.distanceTo(mc.player), b.distanceTo(mc.player)));
        Set<Integer> out = new HashSet<>();
        for (int i = max; i < all.size(); i++) out.add(all.get(i).getId());
        return out;
    }

    private static boolean neverHide(Entity e, Set<String> party, boolean dungeon, boolean kuudra, boolean winter, boolean mines) {
        if ((dungeon || kuudra) && e instanceof RemotePlayer && !npc(e)) return true;
        if (dungeon && (e instanceof WitherBoss || e instanceof EnderDragon)) return true;
        if (winter && e instanceof net.minecraft.world.entity.monster.cubemob.MagmaCube) return true;
        if (mines && (e instanceof net.minecraft.world.entity.monster.Ghast || e instanceof net.minecraft.world.entity.animal.golem.IronGolem)) return true;
        if (e instanceof WitherBoss && e.getId() < 0) return true;
        if (e instanceof RemotePlayer && party.contains(e.getName().getString())) return true;
        return io.github.zmuzy.sbc.combat.DamageIndicator.of(e) != null;
    }

    // skyhannis isNpc: hypixels fake players have a different uuid version than real ones
    private static boolean npc(Entity e) {
        return e.getUUID().version() != 4;
    }

    // EntityRenderDispatcher.shouldRender
    public static boolean hidden(Entity e) {
        if (!anyEntity) return false;
        if (expOrbs && e instanceof ExperienceOrb) return true;
        if (far && farHidden.contains(e.getId())) return true;
        if (e instanceof ArmorStand st) {
            if (lesser && lesserStands.contains(e.getId())) return true;
            if (temporary && st.tickCount < 10 && emptyStand(st)) return true;
        }
        return false;
    }

    // from skyhanni FixGhostEntities: a stand with the default name and nothing on it
    private static boolean emptyStand(ArmorStand st) {
        if (!DEFAULT_STAND.equals(st.getName().getString())) return false;
        for (EquipmentSlot slot : EquipmentSlot.values()) if (!st.getItemBySlot(slot).isEmpty()) return false;
        return true;
    }

    // ClientPacketListener.handleParticleEvent, both passes, only the game thread counts
    public static boolean hidesPacket(ClientboundLevelParticlesPacket p) {
        if (!anyPacket) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || mc.player == null || mc.level == null) return false;
        var type = p.particle().getType();
        double dx = p.x() - mc.player.getX(), dy = p.y() - mc.player.getY(), dz = p.z() - mc.player.getZ();
        double d2 = dx * dx + dy * dy + dz * dz;
        if (farParticles && d2 > 40 * 40 && !m7Boss()) return true;
        if (redstone && type == ParticleTypes.DUST && d2 < 2 * 2) return true;
        boolean smokes = type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE;
        if (smoke && smokes) return true;
        if (fireball && smokes && !mc.level.getEntitiesOfClass(SmallFireball.class, new AABB(p.x() - 5, p.y() - 5, p.z() - 5, p.x() + 5, p.y() + 5, p.z() + 5)).isEmpty()) return true;
        if (explosions && (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.POOF) && !chickenRace()) return true;
        if (lesser && type == ParticleTypes.DUST) {
            for (ArmorStand st : lesserAt) if (st.distanceToSqr(p.x(), p.y(), p.z()) < 4 * 4) return true;
        }
        return false;
    }

    private static boolean chickenRace() {
        return "WINTER".equals(GameState.island) && CHICKEN_RACE.matcher(GameState.strip(Vitals.actionBarRaw).trim()).matches();
    }

    // from skyhanni ParticleHider.inM7Boss: the far particles there are the fight
    private static boolean m7Boss() {
        return "CATACOMBS".equals(GameState.island) && io.github.zmuzy.sbc.dungeon.DungeonRun.inBoss() && io.github.zmuzy.sbc.dungeon.DungeonRun.isFloor("M7");
    }

    public static boolean hideBlaze() {
        return blaze;
    }

    public static boolean hideEnderman() {
        return enderman;
    }

    public static boolean hideFireBlock() {
        return fireBlock;
    }

    public static boolean hideFireball() {
        return fireball;
    }

    // from skyhanni shouldHideBlockParticles
    public static boolean hideBlockBreak() {
        return blocks && (!blocksGarden || "GARDEN".equals(GameState.island));
    }

    public static boolean hideFireOverlay() {
        return fire;
    }

    // from skyhanni ScreenEffectRendererMixin.configureFlameHeightInternal
    public static float flameY(float y) {
        return flames ? y - (0.5f - flameHeight / 200f) : y;
    }

    // from skyhanni ScreenEffectRendererMixin.configureFlameOpacity
    public static int flameColour(int colour) {
        if (!flames) return colour;
        float a = net.minecraft.util.ARGB.alphaFloat(colour) - (0.8f - flameOpacity / 125f);
        return net.minecraft.util.ARGB.color(Math.max(0f, a), colour);
    }

    // from skyhanni HideArmor.shouldHideArmor
    private static boolean hidesArmor(LivingEntity entity) {
        if (armor.equals("Off") || !(entity instanceof Player)) return false;
        if (entity.hasEffect(MobEffects.INVISIBILITY) || npc(entity)) return false;
        return switch (armor) {
            case "Everyone" -> true;
            case "Only mine" -> entity instanceof LocalPlayer;
            case "Only others" -> !(entity instanceof LocalPlayer);
            default -> false;
        };
    }

    // HumanoidMobRenderer.extractHumanoidRenderState: worn stuff goes from what gets drawn, never from the player
    public static void armor(LivingEntity entity, HumanoidRenderState state) {
        if (armor.equals("Off") || !hidesArmor(entity)) return;
        state.headEquipment = ItemStack.EMPTY;
        state.headItem.clear();
        state.wornHeadType = null;
        state.wornHeadProfile = null;
        if (helmetOnly) return;
        state.chestEquipment = ItemStack.EMPTY;
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;
    }

    public static void register() {
        Features.add(new Feature(ID, "World", "Hide things in the world", "Tick what you would rather not see.", "SkyHanni: particle hider, hide armor, hide far entities and others")
                .alwaysOn().tick(WorldHiders::tick)
                .option(Option.bool("world_hide.exp_orbs", "Experience orbs", "The ones lying on the ground.", false))
                .option(Option.bool("world_hide.lesser_orb", "Lesser Orb of Healing", "The orb and its particles.", false))
                .option(Option.bool("world_hide.temporary_stands", "Temporary armor stands", "The empty ones that show for a fraction of a second.", false))
                .option(Option.bool("world_hide.explosions", "Explosions", "Not during the chicken race.", false))
                .option(Option.bool("world_hide.fire_overlay", "Fire on your screen", "", false))
                .option(Option.integer("world_hide.flame_height", "Height of the fire on your screen (%)", "100 is the game's own.", 100, 0, 100))
                .option(Option.integer("world_hide.flame_opacity", "Opacity of the fire on your screen (%)", "100 is the game's own.", 100, 0, 100))
                .option(Option.choice("world_hide.armor", "Hide the armor of", "", "Off", "Off", "Everyone", "Only mine", "Only others"))
                .option(Option.bool("world_hide.armor_helmet", "Only the helmet", "", false))
                .option(Option.bool("world_hide.far_entities", "Far mobs and players", "Only the nearest are drawn. Bosses, party members and dungeon teammates always are.", false))
                .option(Option.integer("world_hide.far_max", "Mobs and players drawn at most", "", 30, 1, 150))
                .option(Option.integer("world_hide.far_min", "Always draw what is this close", "", 10, 3, 30))
                .option(Option.bool("world_hide.block_break", "Block break particles", "", false))
                .option(Option.bool("world_hide.block_break_garden", "Block break particles only on the Garden", "", false))
                .option(Option.bool("world_hide.blaze", "Blaze particles", "", false))
                .option(Option.bool("world_hide.enderman", "Enderman particles", "", false))
                .option(Option.bool("world_hide.fire_block", "Fire block particles", "", false))
                .option(Option.bool("world_hide.fireball", "Fireball particles", "", false))
                .option(Option.bool("world_hide.smoke", "Smoke particles", "", false))
                .option(Option.bool("world_hide.redstone", "Redstone particles around you", "They come from some potion effects.", false))
                .option(Option.bool("world_hide.far_particles", "Particles more than 40 blocks away", "Not in the Master Mode 7 boss.", false)));
    }
}
