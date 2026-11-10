// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.combat;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;

// which skyblock mob an entity is, from the name tag hypixel spawns right after it. from skyhanni data/mob
// (MobFilter, MobFactories, Mob, the IslandExceptions bits we need)
// entity ids count up from the mob to its tag. tag has name, level or tier, and owner for summons
// 2 ids on from a slayers tag sits who spawned it
// skipped jerries, rats, farm animals and fishing mobs of the new islands, nothing here asks for them
public final class Mobs {
    public enum Category {SLAYER, BOSS, DUNGEON, BASIC, SUMMON, SPECIAL}

    private static final Patterns.P SLAYER = Patterns.p("SH.mob.detection.filter.slayer"), BOSS = Patterns.p("SH.mob.detection.filter.boss"), BASIC = Patterns.p("SH.mob.detection.filter.basic"),
            DUNGEON = Patterns.p("SH.mob.detection.filter.dungeon"), SUMMON = Patterns.p("SH.mob.detection.filter.summon"), OWNER = Patterns.p("SH.mob.detection.pattern.summon.owner");
    // how many looks an entity gets for its tag, 2 a sec
    private static final int TRIES = 40;
    // longest entity stack under one tag worth following
    private static final int STACK = 6;

    // skyhanni Mob.kt: entity, its name tag and what the tag says
    public static final class Mob {
        public final LivingEntity base;
        public final ArmorStand stand;
        public final String name;
        public final int levelOrTier;
        public final Category category;
        public final boolean runic;
        private String owner;

        Mob(LivingEntity base, ArmorStand stand, String name, int levelOrTier, Category category, String owner) {
            this.base = base;
            this.stand = stand;
            this.name = name;
            this.levelOrTier = levelOrTier;
            this.category = category;
            this.owner = owner;
            this.runic = category == Category.BASIC && stand != null && !"THE_RIFT".equals(GameState.island) && io.github.zmuzy.sbc.core.LegacyText.of(stand.getName(), true, false).startsWith("§5");
        }

        // slayer owner, read lazily bc the tag can show up later than the mob
        public String owner() {
            if (owner == null && category == Category.SLAYER && stand != null) owner = ownerOf(stand);
            return owner;
        }

        public boolean mine() {
            var mc = Minecraft.getInstance();
            return mc.getUser() != null && mc.getUser().getName().equals(owner());
        }
    }

    private static final Map<Integer, Mob> BY_ID = new HashMap<>();
    private static final Map<Integer, Integer> tries = new HashMap<>();
    private static final List<Consumer<Mob>> SPAWN = new ArrayList<>(), GONE = new ArrayList<>();

    private Mobs() {}

    public static void onSpawn(Consumer<Mob> l) {
        SPAWN.add(l);
    }

    public static void onGone(Consumer<Mob> l) {
        GONE.add(l);
    }

    public static Mob of(Entity e) {
        return e == null ? null : BY_ID.get(e.getId());
    }

    public static Collection<Mob> all() {
        return BY_ID.values();
    }

    private static String ownerOf(ArmorStand stand) {
        var level = stand.level();
        if (level.getEntity(stand.getId() + 2) instanceof ArmorStand tag) {
            Matcher m = OWNER.matcher(GameState.strip(tag.getName().getString()));
            if (m.matches()) return m.group("name").trim();
        }
        return null;
    }

    // skyhanni MobFactories in the order createSkyblockMob asks them. package private + no game, for tests
    static Parsed parse(String cleanName, boolean inDungeon) {
        Matcher m = SUMMON.matcher(cleanName);
        if (m.matches()) return new Parsed(m.group("name"), -1, Category.SUMMON, m.group("owner"));
        m = SLAYER.matcher(cleanName);
        if (m.matches()) {
            String tier = m.group("tier");
            return new Parsed(m.group("name"), tier == null ? 5 : GameState.roman(tier), Category.SLAYER, null);
        }
        m = BOSS.matcher(cleanName);
        if (m.matches()) return new Parsed(m.group("name"), level(m.group("level")), Category.BOSS, null);
        if (inDungeon) {
            m = DUNGEON.matcher(cleanName);
            return m.matches() ? new Parsed(m.group("name"), -1, Category.DUNGEON, null) : null;
        }
        m = BASIC.matcher(cleanName);
        if (m.find()) {
            String name = m.group("name");
            if (m.group("corrupted") != null && !m.group("corrupted").isEmpty() && !name.isEmpty()) name = name.substring(0, name.length() - 1);
            return new Parsed(name, level(m.group("level")), Category.BASIC, null);
        }
        return null;
    }

    record Parsed(String name, int levelOrTier, Category category, String owner) {}

    private static int level(String s) {
        return s == null || s.isEmpty() ? -1 : Integer.parseInt(s);
    }

    private static boolean isDefault(ArmorStand a) {
        return !a.hasCustomName() || a.getName().getString().equals("Armor Stand");
    }

    // skyhanni MobFilter.isSkyBlockMob minus the display npcs, a tag wouldnt match them anyway
    private static boolean candidate(Entity e) {
        if (!(e instanceof LivingEntity) || e instanceof ArmorStand) return false;
        if (e instanceof Player p) return p.getUUID().version() != 4 && !p.getName().getString().startsWith("§");
        return true;
    }

    private static Entity next(Entity e, int offset) {
        return e.level().getEntity(e.getId() + offset);
    }

    private static ArmorStand standAt(Entity e, int offset) {
        return next(e, offset) instanceof ArmorStand a ? a : null;
    }

    private static Mob build(LivingEntity base, ArmorStand stand) {
        Parsed p = parse(GameState.strip(stand.getName().getString()), "CATACOMBS".equals(GameState.island));
        return p == null ? null : new Mob(base, stand, p.name().trim(), p.levelOrTier(), p.category(), p.owner());
    }

    // skyhanni IslandExceptions, just what the bosses need: mobs whose tag isnt next to them or that have none
    private static Mob exception(LivingEntity base, Entity nextEntity) {
        String plain = base.getName().getString();
        switch (GameState.island) {
            case "CATACOMBS" -> {
                if (base instanceof Player && plain.equals("The Professor")) {
                    ArmorStand tag = standAt(base, 9);
                    if (tag == null) return null;
                    Matcher m = BOSS.matcher(GameState.strip(tag.getName().getString()));
                    return m.matches() ? new Mob(base, tag, m.group("name").trim(), level(m.group("level")), Category.BOSS, null) : null;
                }
                if (base instanceof Player && plain.contains("Livid") && (nextEntity instanceof Giant || nextEntity == null)) {
                    ArmorStand tag = standAt(base, 10);
                    return tag == null ? null : new Mob(base, tag, "Real Livid", -1, Category.BOSS, null);
                }
            }
            case "PRIVATE_ISLAND" -> {
                if (Combat.maxHealth(base) >= 2_000_000_000) return new Mob(base, nextEntity instanceof ArmorStand a ? a : null, "Dummy", -1, Category.SPECIAL, null);
            }
            case "THE_RIFT" -> {
                if (base instanceof AbstractCubeMob && nextEntity instanceof ArmorStand a && GameState.strip(a.getName().getString()).startsWith("﴾ [Lv10] B"))
                    return new Mob(base, a, "Bacte", 10, Category.BOSS, null);
            }
            case "CRIMSON_ISLE" -> {
                if (base instanceof Player && plain.equals("Mage Outlaw")) return new Mob(base, nextEntity instanceof ArmorStand a ? a : null, "Mage Outlaw", -1, Category.BOSS, null);
            }
            case "HUB" -> {
                if (base instanceof Player && (plain.equals("Minos Champion") || plain.equals("Minos Inquisitor") || plain.equals("Minotaur ")) && nextEntity instanceof ArmorStand) {
                    ArmorStand tag = standAt(base, 2);
                    return tag == null ? null : build(base, tag);
                }
            }
            default -> {
            }
        }
        if (base instanceof EnderDragon && !"CATACOMBS".equals(GameState.island)) return new Mob(base, null, GameState.strip(plain), -1, Category.BASIC, null);
        return null;
    }

    // skyhanni MobFilter.createSkyblockEntity: walk up the ids from the mob till a named stand, null = not found yet
    private static Mob create(LivingEntity base) {
        Entity nextEntity = next(base, 1);
        Mob special = exception(base, nextEntity);
        if (special != null) return special;
        Entity e = nextEntity;
        for (int i = 1; i <= STACK && e instanceof LivingEntity; i++) {
            if (e instanceof ArmorStand a && !isDefault(a)) return build(base, a);
            if (BY_ID.containsKey(e.getId())) return null;
            e = next(base, i + 1);
        }
        return null;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.player.tickCount % 10 != 0) return;
        BY_ID.values().removeIf(m -> {
            if (!m.base.isRemoved()) return false;
            gone(m);
            return true;
        });
        tries.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        List<LivingEntity> fresh = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering())
            if (candidate(e) && !BY_ID.containsKey(e.getId()) && tries.getOrDefault(e.getId(), 0) < TRIES) fresh.add((LivingEntity) e);
        // lowest id first, its the bottom of a stack and the rest belong to it
        fresh.sort(Comparator.comparingInt(Entity::getId));
        for (LivingEntity e : fresh) {
            if (e == mc.player) continue;
            Mob m = create(e);
            if (m == null) {
                tries.merge(e.getId(), 1, Integer::sum);
                continue;
            }
            tries.remove(e.getId());
            BY_ID.put(e.getId(), m);
            // skyhannis stack logic: whatever sits between a mob and its tag is that mob, not another one
            if (m.stand != null && m.stand.getId() - e.getId() <= STACK) for (int id = e.getId() + 1; id < m.stand.getId(); id++) tries.put(id, TRIES);
            for (Consumer<Mob> l : SPAWN) l.accept(m);
        }
    }

    // new world reuses the same entity ids for other stuff
    private static void reset() {
        for (Mob m : new ArrayList<>(BY_ID.values())) gone(m);
        BY_ID.clear();
        tries.clear();
    }

    private static void gone(Mob m) {
        for (Consumer<Mob> l : GONE) l.accept(m);
    }

    static void register() {
        Features.add(new Feature("combat_mobs", "Combat", "Mob names", "Works out which SkyBlock mob each one is from its name tag; the slayer and combat helpers ask it.", "SkyHanni: Mob Detection")
                .alwaysOn().when(() -> GameState.inSkyBlock || SbcMod.DEV).tick(Mobs::tick).left(Mobs::reset));
    }
}
