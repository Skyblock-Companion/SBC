// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.dungeon.DungeonMarks;
import io.muzy.sbc.item.enchants.EnchantParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// shared stuff for the slayer/combat helpers + where they get registered
// from skyhanni EntityUtils (name tags, canBeSeen, baseMaxHealth), EntityCompat.findHealthReal,
// NumberUtil.percentageColor, TitleManager
public final class Combat {
    private Combat() {}

    public static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    public static boolean island(String id) {
        return id.equals(GameState.island) || SbcMod.DEV;
    }

    public static boolean opt(String feature, String key) {
        return DungeonMarks.opt(feature, key);
    }

    public static int integer(String feature, String key, int fallback) {
        return DungeonMarks.integer(feature, key, fallback);
    }

    public static int color(String feature, String key, int fallback) {
        return DungeonMarks.color(feature, key, fallback);
    }

    public static String choice(String feature, String key, String fallback) {
        return DungeonMarks.choice(feature, key, fallback);
    }

    // skyhanni TitleManager.sendTitle, big line for a moment
    public static void title(String text, int stayTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.hud.setTimes(2, stayTicks, 4);
        mc.gui.hud.setTitle(Component.literal(text));
    }

    public static void pling(float pitch) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, pitch);
    }

    // sound by id like a setting holds it, does nothing if the id isnt real
    public static void play(String id, float volume, float pitch) {
        var p = Minecraft.getInstance().player;
        Identifier rl = Identifier.tryParse(id);
        if (p != null && rl != null) p.playSound(SoundEvent.createVariableRangeEvent(rl), volume, pitch);
    }

    public static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    // chat line that runs a command on click. only the players click sends anything
    public static void sayClickable(String text, String command) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §f" + text).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command))));
    }

    // from skyhanni EntityUtils.baseMaxHealth
    public static int maxHealth(LivingEntity e) {
        return (int) e.getAttributeBaseValue(Attributes.MAX_HEALTH);
    }

    // skyhanni EntityCompat.findHealthReal: the health hypixel writes on the entity, 1024 = full
    public static float health(LivingEntity e) {
        float h = e.getHealth();
        return h == 1024f ? maxHealth(e) : h;
    }

    // skyhanni LocationUtils.canBeSeen: straight line from the eyes to this point
    public static boolean seen(Vec3 point, double viewDistance) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        if (eye.distanceTo(point) >= viewDistance) return false;
        return mc.level.clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    // skyhanni EntityUtils.canBeSeen minus the frustum check, nothing outside it gets drawn anyway
    public static boolean seen(Entity e, double viewDistance, double yOffset) {
        return e.isAlive() && seen(e.position().add(0, yOffset, 0), viewDistance);
    }

    // skyhanni getAllNameTagsWith: name tags around a point this far above the entity with this in the colored name
    public static List<ArmorStand> tagsWith(LivingEntity e, int y, String contains, double radius) {
        Minecraft mc = Minecraft.getInstance();
        List<ArmorStand> out = new ArrayList<>();
        if (mc.level == null) return out;
        Vec3 c = e.position().add(0, y, 0);
        AABB box = new AABB(c.x - radius, c.y - radius - 3, c.z - radius, c.x + radius, c.y + radius + 3, c.z + radius);
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName))
            if (LegacyText.of(a.getName(), true, false).contains(contains)) out.add(a);
        return out;
    }

    public static ArmorStand tagWith(LivingEntity e, int y, String contains) {
        List<ArmorStand> tags = tagsWith(e, y, contains, 1.6);
        return tags.isEmpty() ? null : tags.getFirst();
    }

    // skyhanni getAllNameTagsInRadiusWith: radius 3 around a point 3 above the entity, plain name
    public static List<ArmorStand> tagsInRadius(LivingEntity e, String contains) {
        Minecraft mc = Minecraft.getInstance();
        List<ArmorStand> out = new ArrayList<>();
        if (mc.level == null) return out;
        Vec3 c = e.position().add(0, 3, 0);
        AABB box = new AABB(c.x - 3, c.y - 6, c.z - 3, c.x + 3, c.y + 6, c.z + 3);
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, box, ArmorStand::hasCustomName))
            if (a.getName().getString().contains(contains)) out.add(a);
        return out;
    }

    // near end of skyhannis drawLineToCrosshair
    public static Vec3 fromCrosshair(io.muzy.sbc.render.WorldDraw w) {
        return w.eye.add(w.mc.player.getLookAngle().scale(2));
    }

    // from skyhanni NumberUtil.percentageColor
    public static String percentColor(double fraction) {
        if (fraction > 0.9) return "§2";
        if (fraction > 0.75) return "§a";
        if (fraction > 0.5) return "§e";
        if (fraction > 0.25) return "§6";
        return "§c";
    }

    public static String percentColor(long have, long max) {
        return percentColor(max <= 0 ? 0 : have / (double) max);
    }

    // from skyhanni NumberUtil.shortFormat
    public static String shortNumber(double v) {
        return EnchantParser.shortFormat(v);
    }

    // secs with 1 decimal, like skyhanni format(showMilliSeconds = true)
    public static String seconds(long ms) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0, ms) / 1000.0);
    }

    // min:sec like skyhanni format()
    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 60 ? s / 60 + "m " + s % 60 + "s" : s + "s";
    }

    public static void register() {
        Mobs.register();
        CombatMarks.register();
        CompactDamage.register();
        DamageIndicator.register();
        SlayerApi.register();
        SlayerFeatures.register();
        SlayerCards.register();
        BlazeSlayer.register();
        EndermanSlayer.register();
        SpiderSlayer.register();
        VampireSlayer.register();
        MobMarks.register();
        Arachne.register();
        Broodmother.register();
        Cocoons.register();
        Deployables.register();
        Quiver.register();
        Flares.register();
        Summonings.register();
        EndIsland.register();
        BestiaryPanel.register();
    }
}
