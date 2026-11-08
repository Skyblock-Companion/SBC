// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.combat;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.dungeon.DungeonRun;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;

// health over a bosses head + what its doing. from skyhanni combat/damageindicator (DamageIndicatorManager,
// MobFinder, DamageIndicatorEntityData, DamageCounter, OldDamage)
// every boss + every setting, on top of our Mobs detection. name tag table is the same
// differences: a dead boss waits for its entity to go instead of skyhannis endless timer, a dungeon boss seen
// before its boss line gets checked again
// and the 26 categories are one setting per kind of boss
public final class DamageIndicator {
    public static final String ID = "damage_indicator";
    private static final Patterns.P DAMAGE = Patterns.p("SH.combat.damageindicator.damage"), HITS = Patterns.p("SH.combat.damageindicator.enderslayer.hits"),
            BACTE_LINE = Patterns.p("SH.rift.colosseum.bacte.chat.name");
    private static final long LASER_MS = 8200;

    // skyhanni DamageIndicatorEntityData with DamageCounter on it
    public static final class Boss {
        public final Mobs.Mob mob;
        public final BossType type;
        public final boolean finalDungeonBoss;
        boolean ignoreBlocks;
        long delayedStart;
        long foundTime;
        long lastHealth, maxHealth;
        String healthText = "", namePrefix = "", nameSuffix = "", nameAbove = "";
        long timeLastTick = System.currentTimeMillis();
        public boolean dead;
        boolean firstDeath;
        Vec3 deathLocation;
        long timeToKill = -1;
        boolean visible;
        // DamageCounter
        long currentDamage, currentHealing, firstTick;
        final LinkedList<long[]> oldDamages = new LinkedList<>();

        Boss(Mobs.Mob mob, BossType type, boolean ignoreBlocks, long delayedStart, boolean finalBoss) {
            this.mob = mob;
            this.type = type;
            this.ignoreBlocks = ignoreBlocks;
            this.delayedStart = delayedStart;
            this.finalDungeonBoss = finalBoss;
            this.foundTime = System.currentTimeMillis();
        }

        public LivingEntity entity() {
            return mob.base;
        }

        public long health() {
            return lastHealth;
        }

        public long maxHealth() {
            return maxHealth;
        }

        // skyhanni timeToKill. locked in the first time its asked, which is when the boss dies
        public long timeToKill() {
            if (timeToKill < 0) timeToKill = System.currentTimeMillis() - foundTime;
            return timeToKill;
        }
    }

    public interface HealthChange {
        void on(Boss boss, long last, long health, long max);
    }

    private record Found(BossType type, boolean ignoreBlocks, long delayedStart, boolean finalBoss) {
        Found(BossType type) {
            this(type, false, 0, false);
        }
    }

    private static final Map<UUID, Boss> DATA = new HashMap<>();
    private static final Map<UUID, Long> MAX = new HashMap<>();
    private static final List<Consumer<Boss>> DEATH = new ArrayList<>();
    private static final List<HealthChange> CHANGE = new ArrayList<>();
    private static final List<Consumer<Boss>> FOUND = new ArrayList<>();
    private static long sussyTarantulaAt;
    private static int bactePhase;

    // MobFinder
    private static boolean bonzo1, bonzo2, summons1, secondPhase, guardianShield, professor, professorPrepare, professorGuardian, giants, sadan;
    private static long bonzo1At, bonzo2At, summons1At, secondPhaseAt, guardianShieldAt, professorAt, professorPrepareAt, giantsAt, sadanAt;
    private static final Set<Integer> summonsDiedOnce = new HashSet<>();
    private static final List<Guardian> guardians = new ArrayList<>();
    private static Guardian professorGuardianEntity;
    private static final Map<UUID, Object[]> giantDelays = new HashMap<>();

    private DamageIndicator() {}

    public static void onDeath(Consumer<Boss> l) {
        DEATH.add(l);
    }

    public static void onHealthChange(HealthChange l) {
        CHANGE.add(l);
    }

    public static void onFound(Consumer<Boss> l) {
        FOUND.add(l);
    }

    public static Collection<Boss> bosses() {
        return DATA.values();
    }

    public static Boss of(Entity e) {
        return e == null ? null : DATA.get(e.getUUID());
    }

    public static boolean spawned(BossType... types) {
        for (Boss b : DATA.values()) for (BossType t : types) if (b.type == t) return true;
        return false;
    }

    // from skyhanni getDistanceTo
    public static double distanceTo(BossType... types) {
        var p = Minecraft.getInstance().player;
        if (p == null) return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        for (Boss b : DATA.values()) for (BossType t : types) if (b.type == t) best = Math.min(best, b.entity().position().distanceTo(p.position()));
        return best;
    }

    // last boss of the floor once its at 0.5 health, DungeonCleanEnd waits on this. null before
    public static LivingEntity finalBossAtDeath() {
        if (!DungeonRun.inDungeon()) return null;
        for (Boss b : DATA.values()) if (b.finalDungeonBoss && Combat.health(b.entity()) <= 0.5f) return b.entity();
        return null;
    }

    // hp left on the floors last boss, -1 while unknown
    public static long finalBossHealth() {
        if (!DungeonRun.inDungeon()) return -1;
        for (Boss b : DATA.values()) if (b.finalDungeonBoss && !b.dead) return (long) Combat.health(b.entity());
        return -1;
    }

    // from skyhanni isDamageSplash
    public static boolean damageSplash(ArmorStand e) {
        if (e.tickCount > 300 || !e.hasCustomName() || !e.isAlive()) return false;
        return splashName(GameState.strip(e.getName().getString()).replace(",", ""));
    }

    // tests can hit it without an entity
    static boolean splashName(String cleanName) {
        return DAMAGE.matcher(cleanName).matches();
    }

    private static boolean o(String key) {
        return Combat.opt(ID, key);
    }

    private static boolean showNameAndHealth(Boss b) {
        return Features.on(ID) && o(b.type.group.option);
    }

    // MobFinder

    private static double hpMultiplier() {
        double m = 1;
        for (var p : Mayor.perks) {
            if (p.name().startsWith("DOUBLE MOBS HP")) m *= 2;
            if (p.name().equals("Work Harder")) m *= 1.1;
        }
        return m;
    }

    // from skyhanni EntityUtils.hasMaxHealth
    private static boolean hasMaxHealth(LivingEntity e, int health, boolean boss) {
        return maxHealthMatches(Combat.maxHealth(e), health, boss, hpMultiplier(), DungeonRun.inDungeon());
    }

    static boolean maxHealthMatches(int max, int health, boolean boss, double multiplier, boolean inDungeon) {
        if (max == (int) (health * multiplier)) return true;
        if (!boss && !inDungeon) {
            if (max == (int) (health * 3 * multiplier)) return true;
            if (max == (int) (health * 4 * multiplier)) return true;
            return max == (int) (health * 12 * multiplier);
        }
        return false;
    }

    private static Found find(Mobs.Mob mob) {
        if (DungeonRun.inDungeon()) return findDungeon(mob);
        if ("THE_RIFT".equals(GameState.island)) return findRift(mob);
        if ("GARDEN".equals(GameState.island)) return findGarden(mob);
        if ("PRIVATE_ISLAND".equals(GameState.island) && mob.name.equals("Dummy")) return new Found(BossType.DUMMY);
        return findByType(mob);
    }

    private static Found findGarden(Mobs.Mob mob) {
        if (!(mob.base instanceof Silverfish) && !(mob.base instanceof Bat)) return null;
        for (BossType t : BossType.values()) if (t.group == BossType.Group.PESTS && GameState.strip(t.fullName).equals(mob.name)) return new Found(t);
        return null;
    }

    private static Found findDungeon(Mobs.Mob mob) {
        String f = DungeonRun.floor();
        return switch (f.isEmpty() ? "" : f.substring(f.length() - 1)) {
            case "1" -> findF1(mob);
            case "2" -> findF2(mob);
            case "3" -> findF3(mob);
            case "4" -> mob.base instanceof Ghast ? new Found(BossType.DUNGEON_F4_THORN, true, 0, true) : null;
            case "5" -> mob.name.equals("Real Livid") ? new Found(BossType.DUNGEON_F5, true, 0, true) : null;
            case "6" -> findF6(mob);
            default -> null;
        };
    }

    private static Found findF1(Mobs.Mob mob) {
        if (!mob.name.equals("Bonzo")) return null;
        if (bonzo1) return new Found(BossType.DUNGEON_F1_BONZO_FIRST, false, bonzo1At, false);
        if (bonzo2) return new Found(BossType.DUNGEON_F1_BONZO_SECOND, false, bonzo2At, true);
        return null;
    }

    private static Found findF2(Mobs.Mob mob) {
        LivingEntity e = mob.base;
        if (!(e instanceof Player)) return null;
        String name = e.getName().getString();
        if (name.equals("Summon ")) {
            if (summons1 && !summonsDiedOnce.contains(e.getId())) {
                if ((int) Combat.health(e) != 0) return new Found(BossType.DUNGEON_F2_SUMMON, false, summons1At, false);
                summonsDiedOnce.add(e.getId());
            }
            if (secondPhase) return new Found(BossType.DUNGEON_F2_SUMMON, false, secondPhaseAt, false);
        }
        if (secondPhase && name.equals("Scarf ")) return new Found(BossType.DUNGEON_F2_SCARF, false, secondPhaseAt, true);
        return null;
    }

    private static Found findF3(Mobs.Mob mob) {
        LivingEntity e = mob.base;
        if (e instanceof Guardian g && guardianShield) {
            if (guardians.size() == 4) guardiansTotal();
            else findGuardians();
            if (guardians.contains(g)) return new Found(BossType.DUNGEON_F3_GUARDIAN, true, guardianShieldAt, false);
        }
        long now = System.currentTimeMillis();
        if (professor && mob.name.equals("The Professor")) return new Found(BossType.DUNGEON_F3_PROFESSOR_1, now - professorAt > 1000, professorAt, false);
        if (professorPrepare && mob.name.equals("The Professor")) return new Found(BossType.DUNGEON_F3_PROFESSOR_2, true, professorPrepareAt, false);
        if (e instanceof Guardian && professorGuardian && e == professorGuardianEntity) return new Found(BossType.DUNGEON_F3_PROFESSOR_2, false, 0, true);
        return null;
    }

    private static void findGuardians() {
        guardians.clear();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Guardian g)) continue;
            if (hasMaxHealth(g, 1_000_000, true) || hasMaxHealth(g, 1_200_000, true) || hasMaxHealth(g, 120_000_000, true) || hasMaxHealth(g, 240_000_000, true)
                    || hasMaxHealth(g, 140_000_000, true) || hasMaxHealth(g, 280_000_000, true)) guardians.add(g);
        }
    }

    private static void guardiansTotal() {
        int total = 0;
        for (Guardian g : guardians) total += (int) Combat.health(g);
        if (total == 0) {
            guardianShield = false;
            guardians.clear();
        }
    }

    private static Found findF6(Mobs.Mob mob) {
        LivingEntity e = mob.base;
        if (!(e instanceof Giant) || e.isInvisible()) return null;
        long now = System.currentTimeMillis();
        if (giants && e.getY() > 68) {
            Object[] d = giantDelay(e);
            long extra = (Long) d[0];
            return new Found((BossType) d[1], now - giantsAt > extra, giantsAt + extra + 5000, false);
        }
        if (sadan) return new Found(BossType.DUNGEON_F6_SADAN, true, sadanAt, true);
        return null;
    }

    // from skyhanni checkExtraF6GiantsDelay
    private static Object[] giantDelay(LivingEntity e) {
        return giantDelays.computeIfAbsent(e.getUUID(), u -> {
            int pos;
            BossType type;
            double mx = -8, mz = 56;
            if (e.getX() > mx && e.getZ() > mz) {
                pos = 2;
                type = BossType.DUNGEON_F6_GIANT_3;
            } else if (e.getX() > mx && e.getZ() < mz) {
                pos = 3;
                type = BossType.DUNGEON_F6_GIANT_4;
            } else if (e.getX() < mx && e.getZ() < mz) {
                pos = 0;
                type = BossType.DUNGEON_F6_GIANT_1;
            } else if (e.getX() < mx && e.getZ() > mz) {
                pos = 1;
                type = BossType.DUNGEON_F6_GIANT_2;
            } else {
                pos = 0;
                type = BossType.DUNGEON_F6_GIANT_1;
            }
            return new Object[]{900L * pos, type};
        });
    }

    private static Found findRift(Mobs.Mob mob) {
        return switch (mob.name) {
            case "Leech Supreme" -> new Found(BossType.LEECH_SUPREME);
            case "Bloodfiend", "Riftstalker Bloodfiend" -> {
                // no derpy in the rift. hasMaxHealth with ignoreDerpy undoes the multiplier
                int hp = (int) (Combat.maxHealth(mob.base) * hpMultiplier());
                double m = hpMultiplier();
                if (maxHealthMatches(hp, 625, true, m, false)) yield new Found(BossType.SLAYER_BLOODFIEND_1);
                if (maxHealthMatches(hp, 1_100, true, m, false)) yield new Found(BossType.SLAYER_BLOODFIEND_2);
                if (maxHealthMatches(hp, 1_800, true, m, false)) yield new Found(BossType.SLAYER_BLOODFIEND_3);
                if (maxHealthMatches(hp, 2_400, true, m, false)) yield new Found(BossType.SLAYER_BLOODFIEND_4);
                if (maxHealthMatches(hp, 3_000, true, m, false)) yield new Found(BossType.SLAYER_BLOODFIEND_5);
                yield null;
            }
            case "Bacte" -> new Found(BossType.BACTE);
            case "Sun Gecko" -> new Found(BossType.SUN_GECKO);
            default -> null;
        };
    }

    // skyhanni tryAddByEntityType. ZombifiedPiglin has to come before Zombie bc it extends it
    private static Found findByType(Mobs.Mob mob) {
        LivingEntity e = mob.base;
        String n = mob.name;
        int tier = mob.levelOrTier;
        if (e instanceof Player) {
            return switch (n) {
                case "Mage Outlaw" -> new Found(BossType.NETHER_MAGE_OUTLAW);
                case "DukeBarb", "Barbarian Duke X" -> new Found(BossType.NETHER_BARBARIAN_DUKE);
                case "Minos Inquisitor" -> new Found(BossType.MINOS_INQUISITOR);
                case "Minos Champion" -> new Found(BossType.MINOS_CHAMPION);
                case "Minotaur" -> new Found(BossType.MINOTAUR);
                case "Ragnarok" -> new Found(BossType.RAGNAROK);
                default -> null;
            };
        }
        if (e instanceof IronGolem) {
            if (n.equals("Endstone Protector")) return new Found(BossType.END_ENDSTONE_PROTECTOR);
            if (hasMaxHealth(e, 1_500_000, false)) return new Found(BossType.GAIA_CONSTRUCT);
            if ("CRIMSON_ISLE".equals(GameState.island) && hasMaxHealth(e, 100_000_000, false)) return new Found(BossType.LORD_JAWBUS);
            return null;
        }
        if (e instanceof ZombifiedPiglin) {
            if (!n.contains("ⓉⓎⓅⒽⓄⒺⓊⓈ")) return null;
            if (hasMaxHealth(e, 10_000_000, true)) return new Found(BossType.SLAYER_BLAZE_TYPHOEUS_4);
            if (hasMaxHealth(e, 5_000_000, true)) return new Found(BossType.SLAYER_BLAZE_TYPHOEUS_3);
            if (hasMaxHealth(e, 1_750_000, true)) return new Found(BossType.SLAYER_BLAZE_TYPHOEUS_2);
            if (hasMaxHealth(e, 500_000, true)) return new Found(BossType.SLAYER_BLAZE_TYPHOEUS_1);
            return null;
        }
        if (e instanceof MagmaCube) return n.equals("Magma Boss") ? new Found(BossType.NETHER_MAGMA_BOSS, true, 0, false) : null;
        if (e instanceof EnderMan) {
            if (!n.equals("Voidgloom Seraph")) return null;
            return switch (tier) {
                case 1 -> new Found(BossType.SLAYER_ENDERMAN_1);
                case 2 -> new Found(BossType.SLAYER_ENDERMAN_2);
                case 3 -> new Found(BossType.SLAYER_ENDERMAN_3);
                case 4 -> new Found(BossType.SLAYER_ENDERMAN_4);
                default -> null;
            };
        }
        if (e instanceof AbstractSkeleton) {
            if (n.contains("ⓆⓊⒶⓏⒾⒾ")) {
                if (hasMaxHealth(e, 10_000_000, true)) return new Found(BossType.SLAYER_BLAZE_QUAZII_4);
                if (hasMaxHealth(e, 5_000_000, true)) return new Found(BossType.SLAYER_BLAZE_QUAZII_3);
                if (hasMaxHealth(e, 1_750_000, true)) return new Found(BossType.SLAYER_BLAZE_QUAZII_2);
                if (hasMaxHealth(e, 500_000, true)) return new Found(BossType.SLAYER_BLAZE_QUAZII_1);
                return null;
            }
            return n.equals("Bladesoul") ? new Found(BossType.NETHER_BLADESOUL) : null;
        }
        if (e instanceof Guardian) return hasMaxHealth(e, 35_000_000, false) ? new Found(BossType.THUNDER) : null;
        if (e instanceof Zombie) {
            if (n.equals("Atoned Horror")) return new Found(BossType.SLAYER_ZOMBIE_5);
            if (!n.equals("Revenant Horror")) return null;
            return switch (tier) {
                case 1 -> new Found(BossType.SLAYER_ZOMBIE_1);
                case 2 -> new Found(BossType.SLAYER_ZOMBIE_2);
                case 3 -> new Found(BossType.SLAYER_ZOMBIE_3);
                case 4 -> new Found(BossType.SLAYER_ZOMBIE_4);
                case 5 -> new Found(BossType.SLAYER_ZOMBIE_5);
                default -> null;
            };
        }
        if (e instanceof WitherBoss) return n.equals("Vanquisher") ? new Found(BossType.NETHER_VANQUISHER) : null;
        if (e instanceof EnderDragon) {
            if ("THE_END".equals(GameState.island)) return new Found(BossType.END_ENDER_DRAGON);
            if ("WINTER".equals(GameState.island)) return new Found(BossType.WINTER_REINDRAKE);
            return null;
        }
        if (e instanceof Spider) {
            if (n.equals("Tarantula Broodfather")) {
                return switch (tier) {
                    case 1 -> new Found(BossType.SLAYER_SPIDER_1);
                    case 2 -> new Found(BossType.SLAYER_SPIDER_2);
                    case 3 -> new Found(BossType.SLAYER_SPIDER_3);
                    case 4 -> new Found(BossType.SLAYER_SPIDER_4);
                    case 5 -> new Found(BossType.SLAYER_SPIDER_5_1);
                    default -> null;
                };
            }
            if (n.equals("Conjoined Brood")) return new Found(BossType.SLAYER_SPIDER_5_2);
            if ("SPIDER_DEN".equals(GameState.island)) {
                if (n.equals("Broodmother")) return new Found(BossType.BROODMOTHER);
                if (n.equals("Arachne")) return tier == 300 ? new Found(BossType.ARACHNE_SMALL) : tier == 500 ? new Found(BossType.ARACHNE_BIG) : null;
            }
            return null;
        }
        if (e instanceof Horse) return n.equals("Headless Horseman") ? new Found(BossType.HUB_HEADLESS_HORSEMAN) : null;
        if (e instanceof Blaze) {
            if (n.equals("Ashfang")) return new Found(BossType.NETHER_ASHFANG);
            if (!n.equals("Inferno Demonlord")) return null;
            return switch (tier) {
                case 1 -> new Found(BossType.SLAYER_BLAZE_1);
                case 2 -> new Found(BossType.SLAYER_BLAZE_2);
                case 3 -> new Found(BossType.SLAYER_BLAZE_3);
                case 4 -> new Found(BossType.SLAYER_BLAZE_4);
                default -> null;
            };
        }
        if (e instanceof Wolf) {
            if (!n.equals("Sven Packmaster")) return null;
            return switch (tier) {
                case 1 -> new Found(BossType.SLAYER_WOLF_1);
                case 2 -> new Found(BossType.SLAYER_WOLF_2);
                case 3 -> new Found(BossType.SLAYER_WOLF_3);
                case 4 -> new Found(BossType.SLAYER_WOLF_4);
                default -> null;
            };
        }
        return null;
    }

    // from skyhanni MobFinder.handleChat
    static void handleChat(String message) {
        if (!DungeonRun.inDungeon()) return;
        long now = System.currentTimeMillis();
        switch (message) {
            case "[BOSS] Bonzo: Gratz for making it this far, but I'm basically unbeatable." -> {
                bonzo1 = true;
                bonzo1At = now + 11_250;
            }
            case "[BOSS] Bonzo: Oh noes, you got me.. what ever will I do?!" -> bonzo1 = false;
            case "[BOSS] Bonzo: Oh I'm dead!" -> {
                bonzo2 = true;
                bonzo2At = now + 4_200;
            }
            case "[BOSS] Bonzo: Alright, maybe I'm just weak after all.." -> bonzo2 = false;
            case "[BOSS] Scarf: ARISE, MY CREATIONS!" -> {
                summons1 = true;
                summons1At = now + 3_500;
            }
            case "[BOSS] Scarf: Those toys are not strong enough I see." -> summons1 = false;
            case "[BOSS] Scarf: Don't get too excited though." -> {
                secondPhase = true;
                secondPhaseAt = now + 6_300;
            }
            case "[BOSS] Scarf: Whatever..." -> secondPhase = false;
            case "[BOSS] The Professor: I was burdened with terrible news recently..." -> {
                guardianShield = true;
                guardianShieldAt = now + 15_400;
            }
            case "[BOSS] The Professor: Oh? You found my Guardians' one weakness?" -> {
                guardianShield = false;
                DATA.values().removeIf(b -> b.type == BossType.DUNGEON_F3_GUARDIAN);
                professor = true;
                professorAt = now + 10_300;
            }
            case "[BOSS] The Professor: I see. You have forced me to use my ultimate technique." -> {
                professor = false;
                professorPrepare = true;
                professorPrepareAt = now + 10_500;
            }
            case "[BOSS] The Professor: The process is irreversible, but I'll be stronger than a Wither now!" -> professorGuardian = true;
            case "[BOSS] The Professor: What?! My Guardian power is unbeatable!" -> professorGuardian = false;
            case "[BOSS] Sadan: ENOUGH!" -> {
                giants = true;
                giantsAt = now + 2_800;
            }
            case "[BOSS] Sadan: You did it. I understand now, you have earned my respect." -> {
                giants = false;
                sadan = true;
                sadanAt = now + 11_500;
            }
            case "[BOSS] Sadan: NOOOOOOOOO!!! THIS IS IMPOSSIBLE!!" -> sadan = false;
            default -> {
            }
        }
    }

    private static void resetFinder() {
        bonzo1 = bonzo2 = summons1 = secondPhase = guardianShield = professor = professorPrepare = professorGuardian = giants = sadan = false;
        summonsDiedOnce.clear();
        guardians.clear();
        professorGuardianEntity = null;
        giantDelays.clear();
    }

    // the manager

    private static void tryAdd(Mobs.Mob mob) {
        if (DATA.containsKey(mob.base.getUUID())) return;
        Found r = find(mob);
        if (r == null) return;
        Boss b = new Boss(mob, r.type(), r.ignoreBlocks(), r.delayedStart(), r.finalBoss());
        DATA.put(mob.base.getUUID(), b);
        for (Consumer<Boss> l : FOUND) l.accept(b);
        update(b);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        // handleNewEntity: the guardian the professor turns into
        if (professorGuardian && professorGuardianEntity == null && DungeonRun.inDungeon()) {
            for (Entity e : mc.level.entitiesForRendering())
                if (e instanceof Guardian g && e.tickCount < 40) {
                    professorGuardianEntity = g;
                    professorPrepare = false;
                    break;
                }
        }
        // stuff seen before its boss line said its the boss. skyhanni has this as a TODO in tryAddDungeonF1
        if (DungeonRun.inDungeon() && mc.player.tickCount % 20 == 0) for (Mobs.Mob m : new ArrayList<>(Mobs.all())) tryAdd(m);
        boolean see = mc.player.tickCount % 2 == 0;
        for (Boss b : new ArrayList<>(DATA.values())) {
            update(b);
            if (see) b.visible = b.ignoreBlocks || Combat.seen(b.entity(), 70, visionOffset(b));
        }
        long now = System.currentTimeMillis();
        Iterator<Boss> it = DATA.values().iterator();
        while (it.hasNext()) {
            Boss b = it.next();
            boolean none = noDeathDisplay(b.type);
            long wait = b.dead && !none ? 4000 : 100;
            if (now > b.timeLastTick + wait || (b.dead && none)) it.remove();
        }
    }

    private static void reset() {
        DATA.clear();
        MAX.clear();
        ICONS.clear();
        dummySplashes.clear();
        resetFinder();
        sussyTarantulaAt = 0;
        bactePhase = 0;
    }

    private static double visionOffset(Boss b) {
        return switch (b.type) {
            case END_ENDSTONE_PROTECTOR -> 3.0;
            case SLAYER_SPIDER_5_1 -> 2.0;
            default -> 1.0;
        };
    }

    private static boolean noDeathDisplay(BossType t) {
        return switch (t) {
            case SLAYER_BLAZE_TYPHOEUS_1, SLAYER_BLAZE_TYPHOEUS_2, SLAYER_BLAZE_TYPHOEUS_3, SLAYER_BLAZE_TYPHOEUS_4, SLAYER_BLAZE_QUAZII_1, SLAYER_BLAZE_QUAZII_2, SLAYER_BLAZE_QUAZII_3, SLAYER_BLAZE_QUAZII_4, SLAYER_SPIDER_5_1 -> true;
            default -> false;
        };
    }

    private static void update(Boss b) {
        LivingEntity e = b.entity();
        long health = (long) Combat.health(e);
        long max = MAX.getOrDefault(e.getUUID(), 0L);
        if (max == 0) {
            max = Math.max(Combat.maxHealth(e), health);
            MAX.put(e.getUUID(), max);
        }
        b.maxHealth = max;
        b.namePrefix = b.nameSuffix = b.nameAbove = "";
        String custom;
        if (health == 0) {
            b.dead = true;
            if (b.type.slayer && o("time_to_kill")) b.nameAbove = "§e" + Combat.seconds(b.timeToKill());
            custom = "§cDead";
        } else {
            custom = customHealth(b, health, e, max);
            if (custom == null) return;
        }
        checkDamage(b, health, b.lastHealth);
        tickDamage(b);
        if (b.lastHealth != health) for (HealthChange l : CHANGE) l.on(b, b.lastHealth, health, max);
        healthUpdate(b, health);
        b.lastHealth = health;
        b.healthText = !custom.isEmpty() ? custom : Combat.percentColor(health, max) + Combat.shortNumber(health);
        if (!e.isRemoved()) b.timeLastTick = System.currentTimeMillis();
    }

    // skyhanni onEntityHealthUpdate: 2nd tarantula takes the first ones start time, death only told once
    private static void healthUpdate(Boss b, long health) {
        if (b.mob.mine()) {
            if (b.type == BossType.SLAYER_SPIDER_5_1) {
                if (sussyTarantulaAt < b.foundTime) sussyTarantulaAt = b.foundTime;
            } else if (b.type == BossType.SLAYER_SPIDER_5_2) {
                if (sussyTarantulaAt != 0 && b.foundTime > sussyTarantulaAt) b.foundTime = sussyTarantulaAt;
            } else if (b.type.slayer) {
                sussyTarantulaAt = 0;
            }
        }
        if (health <= 1 && !b.firstDeath) {
            b.firstDeath = true;
            if (b.mob.mine() && b.type != BossType.SLAYER_SPIDER_5_1) sussyTarantulaAt = 0;
            for (Consumer<Boss> l : DEATH) l.accept(b);
        }
    }

    private static void tickDamage(Boss b) {
        long now = System.currentTimeMillis();
        if (b.currentDamage != 0 || b.currentHealing != 0) {
            if (b.firstTick == 0) b.firstTick = now;
            if (now - b.firstTick > 1000) {
                b.oldDamages.addFirst(new long[]{now, b.currentDamage, b.currentHealing});
                b.firstTick = 0;
                b.currentDamage = b.currentHealing = 0;
            }
        }
        b.oldDamages.removeIf(d -> now - d[0] > 5000);
    }

    private static void checkDamage(Boss b, long health, long last) {
        long damage = last - health, healing = health - last;
        if (damage > 0 && b.type != BossType.DUMMY) b.currentDamage += damage;
        if (healing > 0) {
            // the atoned horrors own heal every 10 ticks, not real healing
            if ((healing == 15_000 || healing == 15_001) && b.type == BossType.SLAYER_ZOMBIE_5) return;
            b.currentHealing += healing;
        }
    }

    private static String customHealth(Boss b, long health, LivingEntity e, long max) {
        switch (b.type) {
            case DUNGEON_F4_THORN -> {
                return thorn(DungeonRun.floor(), health, max);
            }
            case SLAYER_SPIDER_3, SLAYER_SPIDER_4, SLAYER_SPIDER_5_1 -> {
                b.nameAbove = SpiderSlayer.stuck(b.mob) && o("spider.invincible") ? "§eKill hatchlings!" : "";
                if (o("phases")) b.nameSuffix = " §e1/2";
                return "";
            }
            case SLAYER_SPIDER_5_2 -> {
                if (o("phases")) b.nameSuffix = " §e2/2";
                return "";
            }
            case SLAYER_ENDERMAN_1, SLAYER_ENDERMAN_2, SLAYER_ENDERMAN_3, SLAYER_ENDERMAN_4 -> {
                return enderSlayer(e, b, (int) health, (int) max);
            }
            case SLAYER_BLOODFIEND_1, SLAYER_BLOODFIEND_2, SLAYER_BLOODFIEND_3, SLAYER_BLOODFIEND_4, SLAYER_BLOODFIEND_5 -> {
                return vampire(e, b, (int) health, (int) max);
            }
            case SLAYER_BLAZE_1, SLAYER_BLAZE_2, SLAYER_BLAZE_3, SLAYER_BLAZE_4, SLAYER_BLAZE_QUAZII_2, SLAYER_BLAZE_QUAZII_3, SLAYER_BLAZE_QUAZII_4, SLAYER_BLAZE_TYPHOEUS_2, SLAYER_BLAZE_TYPHOEUS_3, SLAYER_BLAZE_TYPHOEUS_4 -> {
                return blaze(e, b, (int) health, (int) max);
            }
            case NETHER_MAGMA_BOSS -> {
                return magma((MagmaCube) e, b, (int) health, (int) max);
            }
            case SLAYER_ZOMBIE_5 -> {
                if (Combat.tagWith(e, 3, "Boom!") != null && o("phases")) b.nameSuffix = " §f§lBOOM!";
                return "";
            }
            case SLAYER_WOLF_3, SLAYER_WOLF_4 -> {
                return Combat.tagWith(e, 2, "Calling the pups!") != null ? "Pups!" : "";
            }
            case NETHER_BARBARIAN_DUKE -> {
                var p = Minecraft.getInstance().player;
                b.ignoreBlocks = e.getY() == 117.0 && p != null && e.position().distanceTo(p.position()) < 15;
                return "";
            }
            case BACTE -> {
                if (o("bacte_phase") && bactePhase > 0) b.namePrefix = "§c" + bactePhase + "/5 ";
                return "";
            }
            case END_ENDER_DRAGON -> {
                return dragon(b);
            }
            default -> {
                return "";
            }
        }
    }

    private static String dragon(Boss b) {
        String type = EndIsland.dragonType();
        if (!type.isEmpty()) b.namePrefix = "§c§l" + type + " ";
        long hp = EndIsland.dragonHp();
        return hp >= 0 ? "§c" + Combat.shortNumber(hp) : "";
    }

    // from skyhanni checkBlazeSlayer
    private static String blaze(LivingEntity e, Boss b, int health, int max) {
        boolean found = false;
        for (BlazeSlayer.Shield s : BlazeSlayer.Shield.values()) {
            ArmorStand tag = Combat.tagWith(e, 3, s.name());
            if (tag == null) continue;
            String[] parts = LegacyText.of(tag.getName(), true, false).split(" ♨");
            String number = parts.length > 1 && !parts[1].isEmpty() ? parts[1].substring(0, 1) : "";
            BlazeSlayer.setShield(b.mob, s);
            if (Features.on(BlazeSlayer.COLORED)) b.nameAbove = s.formatted + " " + number;
            found = true;
            break;
        }
        if (!found) BlazeSlayer.setShield(b.mob, null);
        if (!o("phases")) return "";
        int[] step = phases(b.type == BossType.SLAYER_BLAZE_1 || b.type == BossType.SLAYER_BLAZE_2 ? 2 : b.type == BossType.SLAYER_BLAZE_3 || b.type == BossType.SLAYER_BLAZE_4 ? 3 : 0, health, max);
        if (step == null) return "";
        b.namePrefix = phaseName(step[0], step[1]);
        return Combat.percentColor(step[2], step[3]) + Combat.shortNumber(step[2]);
    }

    // which of n equal hp parts this is + whats left of it: { phase, n, left, part }. null = no phases
    static int[] phases(int n, int health, int max) {
        if (n == 0) return null;
        int part = max / n;
        for (int i = 1; i < n; i++) if (health > part * (n - i)) return new int[]{i, n, health - part * (n - i), part};
        return new int[]{n, n, health, part};
    }

    private static String phaseName(int phase, int of) {
        return (phase == 1 ? "§c" : phase == of ? "§a" : "§e") + phase + "/" + of + " ";
    }

    // from skyhanni checkMagmaCube
    private static String magma(MagmaCube e, Boss b, int health, int max) {
        String prefix = switch (e.getSize()) {
            case 24 -> "§c1/6";
            case 22 -> "§e2/6";
            case 20 -> "§e3/6";
            case 18 -> "§e4/6";
            case 16 -> "§e5/6";
            default -> null;
        };
        boolean show = o("phases");
        if (prefix == null) {
            if (show) b.namePrefix = "§a6/6";
            return Combat.percentColor(health, 10_000_000) + Combat.shortNumber(health);
        }
        if (show) b.namePrefix = prefix + " §f";
        int calc = -1;
        for (String line : GameState.scoreboardRaw) {
            if (!line.contains("▎")) continue;
            String color;
            if (line.startsWith("§7")) color = "§7";
            else if (line.startsWith("§e")) color = "§e";
            else if (line.startsWith("§6") || line.startsWith("§a") || line.startsWith("§c")) {
                calc = 0;
                break;
            } else break;
            String text = line.replace("🎁" + color, "");
            String[] parts = text.split("§e|§7");
            int length = parts.length > 1 ? parts[1].length() : 0;
            double missing = (health / 25.0) * length;
            calc = (int) (health - missing);
        }
        if (calc == -1) return null;
        return Combat.percentColor(calc, max) + Combat.shortNumber(calc);
    }

    // from skyhanni checkEnderSlayer
    private static String enderSlayer(LivingEntity e, Boss b, int health, int max) {
        int[] step = phases(b.type == BossType.SLAYER_ENDERMAN_4 ? 6 : 3, health, max);
        String result = "";
        if (o("phases")) {
            b.namePrefix = phaseName(step[0], step[1]);
            result = Combat.percentColor(step[2], step[3]) + Combat.shortNumber(step[2]);
        }
        String hitText = null;
        ArmorStand tag = Combat.tagWith(e, 3, " Hit");
        if (tag != null) {
            int maxHits = switch (b.type) {
                case SLAYER_ENDERMAN_1 -> 15;
                case SLAYER_ENDERMAN_2 -> 30;
                case SLAYER_ENDERMAN_3 -> 60;
                default -> 100;
            };
            Matcher m = HITS.matcher(LegacyText.of(tag.getName(), true, false));
            if (m.matches()) {
                int hits = Integer.parseInt(m.group("hits"));
                hitText = Combat.percentColor(hits, maxHits) + hits + " Hits";
            }
        }
        Entity riding = e.getVehicle();
        if (o("enderman.laser") && riding != null) {
            long left = LASER_MS - riding.tickCount * 50L;
            String delay = formatDelay(left);
            if (o("enderman.laser_health") || hitText != null) b.nameSuffix = " §f" + delay;
            else return delay;
        }
        return hitText != null ? hitText : result;
    }

    // from skyhanni formatDelay
    private static String formatDelay(long ms) {
        return (ms < 1000 ? "§5" : ms < 3000 ? "§d" : "§f") + Combat.seconds(ms);
    }

    // from skyhanni checkVampireSlayer
    private static String vampire(LivingEntity e, Boss b, int health, int max) {
        if (o("vampire.percent")) b.nameSuffix = " §e" + Math.round(100.0 * health / Math.max(1, max)) + "%";
        if (o("vampire.mania")) {
            Entity v = e.getVehicle();
            if (v != null && v.tickCount > 40) {
                int end = 20 * 26 - v.tickCount;
                b.nameAbove = "Mania Circles: §b" + String.format(java.util.Locale.ROOT, "%.1f", end / 20.0) + "s";
                return "";
            }
        }
        if (o("vampire.steak")) {
            double show = health - max * 0.2;
            if (show < 300) b.nameAbove = show > 0 ? "§cHP till Steak: " + String.format(java.util.Locale.ROOT, "%,d", (long) show) : "§cSteak!";
        }
        return "";
    }

    // from skyhanni checkThorn
    static String thorn(String floor, long realHealth, long realMax) {
        int max;
        int health;
        boolean derpy;
        if (floor.equals("F4")) {
            max = 4;
            derpy = realMax != 300_000L;
            long[] steps = derpy ? new long[]{132_000, 288_000, 444_000, 600_000} : new long[]{66_000, 144_000, 222_000, 300_000};
            health = step(realHealth, steps);
        } else if (floor.equals("M4")) {
            max = 6;
            derpy = realMax != 900_000L;
            long[] steps = derpy ? new long[]{270_000, 576_000, 882_000, 1_188_000, 1_494_000, 1_800_000} : new long[]{135_000, 288_000, 441_000, 594_000, 747_000, 900_000};
            health = step(realHealth, steps);
        } else return null;
        if (health < 0) return null;
        return Combat.percentColor(health, max) + health + "/" + max;
    }

    private static int step(long real, long[] steps) {
        if (real == 1) return 0;
        for (int i = 0; i < steps.length; i++) if (real <= steps[i]) return i + 1;
        return -1;
    }

    // drawing

    private static void draw(WorldDraw w) {
        boolean third = !w.mc.options.getCameraType().isFirstPerson();
        double sizeHealth = third ? 2.8 : 1.9, sizeAbove = third ? 2.2 : 1.8, sizeName = third ? 2.4 : 2.1, sizeFinal = third ? 1.8 : 1.4;
        String style = Combat.choice(ID, "boss_name", "Full name");
        long now = System.currentTimeMillis();
        for (Boss b : DATA.values()) {
            if (!b.visible) continue;
            boolean show = showNameAndHealth(b);
            String healthText = b.healthText;
            if (b.delayedStart != 0 && b.delayedStart > now) healthText = formatDelay(b.delayedStart - now);
            Vec3 at;
            if (b.dead && b.deathLocation != null) at = b.deathLocation;
            else {
                at = b.entity().position();
                if (b.dead) b.deathLocation = at;
            }
            // from skyhanni DamageIndicatorManager.onRenderWorld. they shift text by -yOffset so the name (-9) is a line
            // above the health
            // nameAbove (-18) above that, icons and damage lines (+9) under it. our dynamicText counts lines up so the signs flip
            if (show) w.dynamicText(at, healthText, sizeHealth, 0);
            if (!b.nameAbove.isEmpty()) w.dynamicText(at, b.nameAbove, sizeAbove, 2);
            String name = !show ? "" : switch (style) {
                case "Hidden" -> "";
                case "Short name" -> b.type.shortName;
                default -> b.type.fullName;
            };
            name = b.namePrefix + name + b.nameSuffix;
            if (!name.isEmpty()) w.dynamicText(at, name, sizeName, 1);
            double line = -1;
            String icons = icons(b);
            if (!icons.isEmpty()) {
                w.dynamicText(at, icons, sizeName, line);
                line -= 22 / 9.0;
            } else line -= 4 / 9.0;
            if (show && o("damage_over_time")) {
                if (b.currentDamage != 0 || b.currentHealing != 0) {
                    w.dynamicText(at, damageLine(b.currentDamage, b.currentHealing, ""), sizeFinal, line);
                    line--;
                }
                for (long[] d : b.oldDamages) {
                    w.dynamicText(at, damageLine(d[1], d[2], "/s"), sizeFinal, line);
                    line--;
                }
            }
        }
    }

    private static String damageLine(long damage, long healing, String per) {
        String dmg = "§c" + Combat.shortNumber(damage) + per, heal = "§a+" + Combat.shortNumber(healing) + per;
        return healing == 0 ? dmg : damage == 0 ? heal : dmg + " §7/ " + heal;
    }

    private static final Map<Integer, Object[]> ICONS = new HashMap<>();

    // extremely real shuriken + twilight arrow poison marks. checked once a sec like skyhannis iconCache
    private static String icons(Boss b) {
        long now = System.currentTimeMillis();
        Object[] cached = ICONS.get(b.entity().getId());
        if (cached != null && now - (Long) cached[0] < 1000) return (String) cached[1];
        boolean compact = o("compact_effects");
        List<String> out = new ArrayList<>();
        if (o("shuriken") && Combat.tagWith(b.entity(), 3, "§b✯") != null) out.add(compact ? "§b✯" : "§bShuriken");
        if (o("twilight") && Combat.tagWith(b.entity(), 3, "§5ᛤ") != null) out.add(compact ? "§5ᛤ" : "§5Twilight");
        String s = String.join(compact ? "" : " ", out);
        if (ICONS.size() > 64) ICONS.clear();
        ICONS.put(b.entity().getId(), new Object[]{now, s});
        return s;
    }

    // hiding

    // skyhanni onCheckRender: damage splashes and name tags near a boss. the dummys splashes are its damage
    static boolean hidden(ArmorStand stand, boolean splash) {
        if (DATA.isEmpty()) return false;
        Boss near = null;
        Vec3 pos = stand.position();
        for (Boss b : DATA.values()) {
            if (b.entity().position().distanceTo(pos) < 4.5) {
                near = b;
                break;
            }
        }
        if (near == null) return false;
        return hides(splash, showNameAndHealth(near), near.type.slayer, stand.getName().getString(), stand.hasCustomName(), o("hide_splash"), o("hide_splash_slayer"), o("hide_nametag"));
    }

    // the onCheckRender decision alone so tests can reach it without an entity
    static boolean hides(boolean splash, boolean show, boolean slayer, String name, boolean customName, boolean hideSplash, boolean hideSplashSlayer, boolean hideNametag) {
        if (splash) return (show && hideSplash) || (slayer && hideSplashSlayer);
        if (!show || !hideNametag) return false;
        if (name.contains("Plasmaflux") || name.contains("Overflux") || name.contains("Mana Flux") || name.contains("Radiant")) return false;
        return customName;
    }

    static boolean hidesAnything() {
        return !DATA.isEmpty() && Features.on(ID) && (o("hide_splash") || o("hide_splash_slayer") || o("hide_nametag"));
    }

    private static final Set<UUID> dummySplashes = new HashSet<>();

    // dummy has no health to read, its damage is whatever its splashes say
    private static void countDummy(Minecraft mc) {
        Boss dummy = null;
        for (Boss b : DATA.values()) if (b.type == BossType.DUMMY) dummy = b;
        if (dummy == null || mc.level == null) return;
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, dummy.entity().getBoundingBox().inflate(4.5), DamageIndicator::damageSplash)) {
            if (!dummySplashes.add(a.getUUID())) continue;
            String digits = GameState.strip(a.getName().getString()).replaceAll("\\D", "");
            if (!digits.isEmpty() && digits.length() < 18) dummy.currentDamage += Long.parseLong(digits);
        }
        if (dummySplashes.size() > 512) dummySplashes.clear();
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        handleChat(line.plain().trim());
        Matcher m = BACTE_LINE.matcher(LegacyText.of(line.component()));
        if (m.matches()) bactePhase = m.group("name").length();
        return ChatBus.PASS;
    }

    static void register() {
        // reading keeps going with the display off like skyhanni, blaze daggers, fire pits, clean end etc all ask it
        Feature f = Features.add(new Feature("damage_indicator_reading", "Combat", "Boss health reading", "Keeps track of the bosses around you, for the Damage Indicator and the other helpers.", "")
                .alwaysOn().when(Combat::sb).tick(mc -> {
                    tick(mc);
                    countDummy(mc);
                }).left(DamageIndicator::reset));
        Features.add(new Feature(ID, "Combat", "Damage Indicator", "The health of a boss over its head, its phase, what it is doing and how long the kill took.", "SkyHanni: Damage Indicator")
                .when(Combat::sb).world(DamageIndicator::draw)
                .option(Option.choice("damage_indicator.boss_name", "Boss name", "How the boss's name is written.", "Full name", "Hidden", "Full name", "Short name"))
                .option(Option.bool("damage_indicator.bosses.slayers", "Slayer bosses", "Revenant, Tarantula, Sven, Voidgloom, Inferno and Riftstalker.", true))
                .option(Option.bool("damage_indicator.bosses.dungeon", "Dungeon bosses", "Floors 1 to 6, Master Mode too.", true))
                .option(Option.bool("damage_indicator.bosses.crimson", "Crimson Isle bosses", "The minibosses and the Vanquisher.", true))
                .option(Option.bool("damage_indicator.bosses.end", "End bosses", "The Endstone Protector and the dragons.", true))
                .option(Option.bool("damage_indicator.bosses.diana", "Mythological mobs", "Minos Inquisitor, Champion, Minotaur, Gaia Construct and the Horseman.", true))
                .option(Option.bool("damage_indicator.bosses.sea", "Sea creatures", "Thunder, Lord Jawbus, Ragnarok and the Reindrake.", true))
                .option(Option.bool("damage_indicator.bosses.spiders", "Arachne and Broodmother", "", true))
                .option(Option.bool("damage_indicator.bosses.rift", "Rift bosses", "Leech Supreme, Bacte and Sun Gecko.", true))
                .option(Option.bool("damage_indicator.bosses.pests", "Garden pests", "Off: the Pest boxes mark them, without text.", false))
                .option(Option.bool("damage_indicator.bosses.dummy", "Training dummy", "On your private island, for testing your damage.", false))
                .option(Option.bool("damage_indicator.hide_splash", "Hide damage splashes", "Near the bosses above.", false))
                .option(Option.bool("damage_indicator.hide_splash_slayer", "Hide splashes near a slayer boss", "Only near slayer bosses, also when their health is not shown.", false))
                .option(Option.bool("damage_indicator.hide_nametag", "Hide the boss's own name tag", "", false))
                .option(Option.bool("damage_indicator.damage_over_time", "Damage over time", "Damage and healing of the last seconds under the health.", true))
                .option(Option.bool("damage_indicator.shuriken", "Shuriken mark", "When an Extremely Real Shuriken hit it.", true))
                .option(Option.bool("damage_indicator.twilight", "Twilight mark", "When Twilight Arrow Poison hit it.", true))
                .option(Option.bool("damage_indicator.compact_effects", "Marks as icons", "The icons of the two marks instead of their names.", false))
                .option(Option.bool("damage_indicator.time_to_kill", "Time to kill", "Over a slayer boss when it dies.", true))
                .option(Option.bool("damage_indicator.bacte_phase", "Bacte's phase", "", true))
                .option(Option.bool("damage_indicator.phases", "Phases", "Which phase a Voidgloom, Inferno, Tarantula or Magma Boss is in with the health of that phase, and the Atoned Horror's Boom.", true))
                .option(Option.bool("damage_indicator.spider.invincible", "Text while invincible", "'Kill hatchlings!' over a Tarantula that cannot be hurt.", true))
                .option(Option.bool("damage_indicator.enderman.laser", "Laser timer", "How long the Voidgloom's laser lasts.", true))
                .option(Option.bool("damage_indicator.enderman.laser_health", "Health during the laser", "Keeps the health with the timer beside it.", false))
                .option(Option.bool("damage_indicator.vampire.percent", "Riftstalker's health in percent", "", true))
                .option(Option.bool("damage_indicator.vampire.mania", "Mania circles", "How long the Riftstalker's Mania lasts.", true))
                .option(Option.bool("damage_indicator.vampire.steak", "Health until Steak", "When the Steak Stake kills it.", true)));
        ChatBus.register(ChatBus.OBSERVE + 18, "damageindicator", f, DamageIndicator::onChat);
        Mobs.onSpawn(DamageIndicator::tryAdd);
        CombatMarks.hiding(DamageIndicator::hidesAnything, e -> e instanceof ArmorStand a && hidden(a, damageSplash(a)));
    }
}
