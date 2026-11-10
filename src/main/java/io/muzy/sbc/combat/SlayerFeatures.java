// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import com.google.gson.JsonObject;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// small slayer helpers. from skyhanni SlayerBossSpawnSoon, SlayerQuestWarning, SlayerTimeMessages,
// SlayerCocoonWarning, SlayerItemsOnGround, SlayerMiniBossFeatures
// GummyWarning, BlockNotSpawnable, HideMobNames, HideSlayerSpawnParticles, ActiveBossTransparency,
// HideIrrelevantMobsInSlayerArea
// and skyblocker SlayerTimer, CallMaddox, RewardsHighlighter, SlayerMinibossSpawnFilter, SlayerGlowAdder
// minibosses, Mute*Sounds, the sven pup tag from EntityRenderDispatcherMixin
public final class SlayerFeatures {
    public static final String MINIBOSS = "slayer_miniboss", GROUND = "slayer_ground_items", WARNING = "slayer_quest_warning", TIMES = "slayer_time_messages", COCOON = "slayer_cocoon_warning", MADDOX = "slayer_maddox",
            GUMMY = "slayer_gummy", NOT_SPAWNABLE = "slayer_block_not_spawnable", HIDE_NAMES = "slayer_hide_names", HIDE_PARTICLES = "slayer_hide_spawn_particles", MUTE = "slayer_mute_sounds", REWARDS = "slayer_rewards",
            FADE = "slayer_active_boss_fade", IRRELEVANT = "slayer_irrelevant_mobs";
    private static final Patterns.P SOON = Patterns.p("SH.slayer.bosswarning.progress"), NOT_SPAWN = Patterns.p("SH.slayer.notspawnable"), SMOLDERING_AREA = Patterns.p("SH.slayer.gummy.smoldering-area");
    private static final Pattern MINI_LINE = Pattern.compile("^SLAYER MINI-BOSS .+ has spawned!$");
    private static final Set<String> TELEPORT_ITEMS = Set.of("ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID");
    private static final String GUMMY_GAINED = "You ate a Re-heated Gummy Polar Bear!", GUMMY_TIMER = "gummy_polar_bear";
    private static final long GUMMY_MS = 60 * 60 * 1000L;

    // skyhanni SpawnParticles: the 3 smoke kinds a slayer mob death leaves
    private static final Set<String> MUTE_BLAZE = Set.of("entity.blaze.burn", "entity.blaze.ambient", "entity.blaze.hurt", "entity.ghast.shoot", "block.fire.ambient", "block.lava.pop", "entity.lightning_bolt.thunder", "entity.lightning_bolt.impact"),
            MUTE_SPIDER = Set.of("entity.spider.ambient", "entity.spider.death", "entity.spider.hurt", "entity.spider.step", "entity.bat.hurt", "entity.silverfish.hurt", "entity.silverfish.death", "entity.silverfish.ambient",
                    "entity.skeleton.ambient", "entity.skeleton.death", "entity.skeleton.hurt", "entity.skeleton.step"),
            MUTE_ENDERMAN = Set.of("entity.enderman.ambient", "entity.enderman.death", "entity.enderman.hurt", "entity.enderman.scream", "entity.enderman.stare");

    private static boolean warned;
    private static double lastCompletion;
    private static boolean noQuestBruh;
    private static String questReason = "";
    private static long lastWarning, lastWeaponUse, slainAt;
    private static final Map<Integer, Float> lastHealth = new HashMap<>();
    private static final List<ItemEntity> groundItems = new ArrayList<>();
    private static final Map<ItemEntity, String> groundNames = new HashMap<>();
    private static final Map<Vec3, Long> recentDeaths = new HashMap<>();
    private static final Map<Integer, Mobs.Mob> minis = new HashMap<>();
    private static long lastGummyWarn;
    private static boolean hasHabanero, holdingWeapon;
    private static Mobs.Mob lastClicked;
    private static long bossAt;

    private SlayerFeatures() {}

    private static boolean o(String f, String k) {
        return Combat.opt(f, k);
    }

    // boss soon

    // from skyhanni SlayerBossSpawnSoon.onSlayerProgressChange
    private static void onProgress(String old, String now) {
        if (!Features.on("slayer_spawn_soon") || !SlayerApi.hasQuest() || !SlayerApi.inCorrectArea()) return;
        Matcher m = SOON.matcher(now);
        if (!m.matches()) return;
        double total = shortNumber(m.group("total"));
        if (total <= 0) return;
        double completion = shortNumber(m.group("progress")) / total;
        if (completion > Combat.integer("slayer_spawn_soon", "percent", 80) / 100.0) {
            if (!warned || (Combat.opt("slayer_spawn_soon", "repeat") && completion != lastCompletion)) {
                Combat.pling(1f);
                Combat.say("§eSlayer boss soon!");
                Combat.title("§eSlayer boss soon!", 40);
                warned = true;
            }
        } else {
            warned = false;
        }
        lastCompletion = completion;
    }

    // "4.8k" -> 4800
    static double shortNumber(String s) {
        s = s.replace(",", "").trim().toLowerCase(java.util.Locale.ROOT);
        double mult = s.endsWith("k") ? 1e3 : s.endsWith("m") ? 1e6 : s.endsWith("b") ? 1e9 : 1;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // quest warning

    private static void onState(SlayerApi.State s) {
        switch (s) {
            case GRINDING -> noQuestBruh = false;
            case BOSS_FIGHT -> {
                if (bossAt == 0) bossAt = System.currentTimeMillis();
            }
            case FAILED -> {
                questReason = "The old slayer quest has failed!";
                noQuestBruh = true;
                bossAt = 0;
            }
            case SLAIN -> slainAt = System.currentTimeMillis();
            case COCOONED -> {
                if (Features.on(COCOON)) {
                    Combat.title("§lSlayer Boss Cocooned!", 40);
                    if (o(COCOON, "ding")) {
                        Combat.pling(1f);
                        dingLeft = 9;
                    }
                }
            }
            default -> {
            }
        }
        if (s == SlayerApi.State.FAILED && Features.on(MADDOX) && o(MADDOX, "on_fail")) maddox();
        if (s == SlayerApi.State.SLAIN && Features.on(MADDOX) && o(MADDOX, "on_kill")) maddox();
    }

    private static int dingLeft;

    // skyblocker CallMaddox: nothing gets called till the player clicks the line. not in the rift, no phone there
    private static void maddox() {
        if ("THE_RIFT".equals(GameState.island)) return;
        Combat.sayClickable("§bClick here to call Maddox.", "/call slayer");
    }

    private static void warn(String title, String chat) {
        if (!Features.on(WARNING)) return;
        long now = System.currentTimeMillis();
        if (now - lastWarning < 10_000 || now - lastWeaponUse > 500) return;
        lastWarning = now;
        Combat.say(chat);
        if (o(WARNING, "title")) Combat.title("§e" + title, 40);
    }

    // skyhanni isSlayerMob: is this what the areas slayer wants and is the active quest the right one
    private static boolean isSlayerMob(LivingEntity e) {
        SlayerApi.Type area = SlayerApi.areaType();
        if (area == null) return false;
        String name = e.getName().getString();
        if (name.equals("Oubliette Guard")) return false;
        if ("Stronghold".equals(GameState.area) && name.equals("Skeleton")) return false;
        if (!area.mobClass.isInstance(e)) return false;
        SlayerApi.Type active = SlayerApi.active();
        if (active != null && active != area) warn("Wrong Slayer!", "Wrong slayer selected! You have " + active.displayName + " selected and you are in an " + area.displayName + " area!");
        return active == area;
    }

    private static void onHealthChange(LivingEntity e) {
        var p = Minecraft.getInstance().player;
        if (p != null && noQuestBruh && Features.on(WARNING) && e.position().distanceTo(p.position()) < 6 && isSlayerMob(e) && noQuestBruh) warn("New Slayer Quest!", "Start a new slayer quest! " + questReason);
    }

    // ticks

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (dingLeft > 0 && mc.player.tickCount % 2 == 0) {
            dingLeft--;
            Combat.pling(1f);
        }
        if (slainAt != 0 && now - slainAt > 5000) {
            slainAt = 0;
            if (SlayerApi.state() == SlayerApi.State.SLAIN) {
                questReason = "You have no Auto-Slayer active!";
                noQuestBruh = true;
            }
        }
        boolean particles = Features.on(HIDE_PARTICLES) && SlayerApi.hasQuest() && SlayerApi.inCorrectArea();
        if ((noQuestBruh && Features.on(WARNING)) || particles) {
            for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(particles ? 32 : 8), x -> !(x instanceof ArmorStand) && x != mc.player)) {
                float h = e.getHealth();
                Float was = lastHealth.put(e.getId(), h);
                if (was != null && was != h) {
                    onHealthChange(e);
                    if (h == 0 && particles) recentDeaths.put(e.position(), now);
                }
            }
            if (lastHealth.size() > 512) lastHealth.clear();
            recentDeaths.values().removeIf(t -> now - t > 3000);
        }
        if (mc.player.tickCount % 2 == 0) {
            groundNames.clear();
            if (Features.on(GROUND) && SlayerApi.inCorrectArea() && SlayerApi.hasQuest()) scanGround(mc);
        }
        if (mc.player.tickCount % 20 == 0) gummyGear(mc);
    }

    // skyhanni SlayerItemsOnGround.onTick + getItemNameAndPrice: better of market and npc, for the whole stack
    private static void scanGround(Minecraft mc) {
        int min = Combat.integer(GROUND, "min_price", 50_000);
        for (ItemEntity it : mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(15))) {
            ItemStack st = it.getItem();
            if (st.getItem() instanceof SpawnEggItem) continue;
            String id = Gain.sbId(st);
            if (id == null || id.isEmpty()) continue;
            double total = Math.max(Math.max(0, Gain.ofId(id)), ItemRepo.npcPrice(id)) * st.getCount();
            if (total < min) continue;
            String name = LegacyText.name(st);
            groundNames.put(it, name + (st.getCount() > 1 ? " §8x" + st.getCount() : "") + " §7(§6" + Prices.coins(total) + "§7)");
        }
    }

    private static void draw(WorldDraw w) {
        if (!groundNames.isEmpty()) for (var e : groundNames.entrySet()) if (e.getKey().isAlive()) w.label(e.getValue(), e.getKey().position().add(0, 0.8, 0), false);
        if (Features.on(MINIBOSS) && o(MINIBOSS, "line") && SlayerApi.inAnyArea()) {
            float width = Combat.integer(MINIBOSS, "line_width", 3);
            for (Mobs.Mob m : minis.values()) if (Combat.seen(m.base, 10, 0.5)) w.line(Combat.fromCrosshair(w), m.base.position().add(0, 1, 0), 0xFF55FFFF, width, false);
        }
    }

    // minibosses

    private static boolean isMini(String name) {
        for (SlayerApi.Type t : SlayerApi.Type.values()) if (t.minibosses.contains(name)) return true;
        return false;
    }

    // skyblocker checkSlayerBoss: someone elses miniboss comes with no chat line, so look around for it
    private static void miniSpawn(Mobs.Mob m) {
        if (!isMini(GameState.strip(m.name))) return;
        minis.put(m.base.getId(), m);
        if (!Features.on(MINIBOSS) || !o(MINIBOSS, "others") || !SlayerApi.hasQuest()) return;
        var p = Minecraft.getInstance().player;
        if (p == null || m.base.position().distanceTo(p.position()) > 15) return;
        if (SlayerApi.active() == null || !SlayerApi.active().minibosses.contains(m.name)) return;
        Combat.title(o(MINIBOSS, "name") && m.stand != null ? LegacyText.of(m.stand.getName(), true, false) : "§cMini-boss", 20);
        Combat.pling(0.8f);
    }

    // skyblocker SlayerManager.onChatMessage: with others minibosses on, the look around covers yours too
    private static boolean miniAlertFromChat() {
        return o(MINIBOSS, "alert") && !o(MINIBOSS, "others");
    }

    // gummy

    private static void gummyGear(Minecraft mc) {
        if (!Features.on(GUMMY) || mc.player == null) return;
        boolean habanero = false;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var tag = MenuView.Item.of(mc.player.getItemBySlot(s)).tag;
            if (tag.getCompoundOrEmpty("enchantments").contains("ultimate_habanero_tactics")) habanero = true;
        }
        hasHabanero = habanero;
        holdingWeapon = false;
        SlayerApi.Type t = SlayerApi.active();
        if (t == null) return;
        JsonObject data = Repo.constant("Slayer.json");
        if (data == null || !data.has("weapons") || !data.getAsJsonObject("weapons").has(t.dataKey)) return;
        String held = Gain.sbId(mc.player.getMainHandItem());
        holdingWeapon = held != null && data.getAsJsonObject("weapons").getAsJsonObject(t.dataKey).has(held);
    }

    static boolean smoldering() {
        Long until = Store.I.timers.get(GUMMY_TIMER);
        if (until != null && until > System.currentTimeMillis()) return true;
        for (String l : GameState.tab) if (l.contains("Smoldering Polarization")) return true;
        return false;
    }

    // skyhanni GummyWarning.onEntityClick: hit with a slayer weapon that wants the polar bear, without it
    private static void onAttack(Entity e) {
        lastWeaponUse = System.currentTimeMillis();
        Mobs.Mob m = Mobs.of(e);
        if (m != null) lastClicked = m;
        if (!Features.on(GUMMY) || SlayerApi.active() == null || !holdingWeapon) return;
        boolean smolderingArea = SMOLDERING_AREA.matcher(GameState.area).matches();
        if (!hasHabanero && !smolderingArea) return;
        if (smoldering()) return;
        long now = System.currentTimeMillis();
        if (now - lastGummyWarn < 10_000) return;
        lastGummyWarn = now;
        Combat.play("block.anvil.land", 1f, 0.5f);
        Combat.title("§4§lNo Polar Bear Active!", 60);
        Combat.sayClickable("You do not have an active Re-Heated Gummy Polar Bear! §7Click to open the Bazaar.", "/bz Re-Heated Gummy Polar Bear");
    }

    // chat

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String plain = line.plain().trim();
        if (plain.equals(GUMMY_GAINED)) {
            long now = System.currentTimeMillis();
            Long until = Store.I.timers.get(GUMMY_TIMER);
            Store.I.timers.put(GUMMY_TIMER, (until != null && until > now ? until : now) + GUMMY_MS);
            Store.markDirty();
        } else if (plain.equals("You cleared all of your active effects!")) {
            Store.I.timers.remove(GUMMY_TIMER);
        }
        if (plain.equals("NICE! SLAYER BOSS SLAIN!") || plain.equals("SLAYER QUEST COMPLETE!") || plain.equals("YOU COCOONED YOUR SLAYER BOSS")) killed();
        if (MINI_LINE.matcher(plain).matches() && Features.on(MINIBOSS)) {
            if (miniAlertFromChat()) {
                Combat.title("§cMini-boss", 20);
                Combat.pling(0.8f);
            }
            if (o(MINIBOSS, "hide_chat")) return ChatBus.CANCEL;
        }
        return ChatBus.PASS;
    }

    // skyblocker SlayerTimer.sendMessage + skyhanni buildTimeMessages: this kill vs the best for this boss and tier
    private static void killed() {
        if (bossAt == 0 || !SlayerApi.hasQuest()) return;
        long took = System.currentTimeMillis() - bossAt;
        bossAt = 0;
        var bests = CombatData.get().slayerBests;
        String boss = SlayerApi.category();
        Long pb = bests.get(boss);
        boolean newBest = pb == null || took < pb;
        if (newBest) {
            bests.put(boss, took);
            CombatData.changed();
        }
        if (!Features.on(TIMES)) return;
        for (String line : timeLines(boss, took, pb, newBest, o(TIMES, "kill"), o(TIMES, "best"), o(TIMES, "compact"))) Combat.say(line);
        if (newBest && pb != null && o(TIMES, "best")) Combat.title("§bPersonal best!", 60);
    }

    // from skyblocker formatTime
    private static String fmt(long ms) {
        return String.format(java.util.Locale.ROOT, "%.2fs", ms / 1000.0);
    }

    static List<String> timeLines(String boss, long took, Long pb, boolean newBest, boolean kill, boolean best, boolean compact) {
        List<String> out = new ArrayList<>();
        String t = fmt(took), old = pb == null ? null : fmt(pb);
        if (kill) out.add(compact ? "§e" + boss + " took §b" + t : "§eIt took §b" + t + "§e to kill " + boss);
        if (best) {
            if (newBest && old == null) out.add(compact ? "§e§lNEW PB! §e" + boss + " in §a" + t : "§e§lNEW PERSONAL BEST! §a" + t + " §efor " + boss);
            else if (newBest) out.add(compact ? "§e§lNEW PB! §e" + boss + " in §c" + old + " §e-> §a" + t : "§e§lNEW PERSONAL BEST! §a" + t + " §7(Previous " + old + ") §efor " + boss);
            else out.add(compact ? "§e" + boss + " PB: §6" + old : "§e" + boss + " personal best: §6" + old);
        }
        return out;
    }

    // skyblocker sendSlayerPersonalBest: highest tier of this slayer that has a time
    private static int sayBest(SlayerApi.Type type) {
        String[] tiers = {"V", "IV", "III", "II", "I"};
        var bests = CombatData.get().slayerBests;
        String[] names = type == SlayerApi.Type.VAMPIRE ? new String[]{"Riftstalker Bloodfiend", "Bloodfiend"} : new String[]{type.displayName};
        for (String tier : tiers) {
            for (String name : names) {
                Long t = bests.get(name + " " + tier);
                if (t != null) {
                    Combat.say("§5" + name + " " + tier + ": §b" + fmt(t));
                    return 1;
                }
            }
        }
        Combat.say("No personal best yet for " + type.displayName + ".");
        return 0;
    }

    private static void questDone() {
        long start = SlayerApi.questStart();
        if (!Features.on(TIMES) || !o(TIMES, "quest_complete") || start == 0) return;
        String d = Combat.clock(System.currentTimeMillis() - start);
        Combat.say(o(TIMES, "compact") ? "§eQuest took §b" + d + "§e in total." : "§eSlayer quest took §b" + d + "§e to complete.");
    }

    // hides

    // skyhanni HideMobNames.shouldNameBeHidden: full hp or dead names of stuff you kill by the dozen
    static boolean hideMobName(String cleanName) {
        for (int i = 0; i <= 18; i++) {
            Matcher m = Patterns.get("SH.slayer.hidemobname." + i).matcher(cleanName);
            if (!m.matches()) continue;
            String min = m.group("min");
            if (min.equals(m.group("max")) || min.equals("0")) return true;
        }
        return false;
    }

    private static boolean nameHidden(Entity e) {
        if (!(e instanceof ArmorStand a) || !a.hasCustomName()) return false;
        String clean = GameState.strip(a.getName().getString());
        if (o(HIDE_NAMES, "sven") && ("HUB".equals(GameState.island) || "THE_PARK".equals(GameState.island)) && clean.contains("Sven Pup")) return true;
        return o(HIDE_NAMES, "mobs") && hideMobName(clean);
    }

    private static boolean onSound(SoundHooks.Sound s) {
        if (!Features.on(MUTE)) return false;
        String n = s.name();
        String island = GameState.island;
        if (o(MUTE, "blaze") && "CRIMSON_ISLE".equals(island) && MUTE_BLAZE.contains(n)) return true;
        if (o(MUTE, "wolf") && ("THE_PARK".equals(island) || "HUB".equals(island)) && n.startsWith("entity.wolf.")) return true;
        if (o(MUTE, "spider") && ("SPIDER_DEN".equals(island) || "CRIMSON_ISLE".equals(island)) && SlayerApi.active() != SlayerApi.Type.INFERNO && MUTE_SPIDER.contains(n)) return true;
        return o(MUTE, "enderman") && "THE_END".equals(island) && MUTE_ENDERMAN.contains(n);
    }

    // skyhanni HideSlayerSpawnParticles.onParticle. they compare squared distance with 5, kept that
    private static boolean spawnParticle(String type, double x, double y, double z) {
        boolean wanted = (type.equals("enchant") && o(HIDE_PARTICLES, "white")) || (type.equals("witch") && o(HIDE_PARTICLES, "purple")) || (type.equals("entity_effect") && o(HIDE_PARTICLES, "specific"));
        if (!wanted) return false;
        for (Vec3 at : recentDeaths.keySet()) if (at.distanceToSqr(x, y, z) < 5) return true;
        return false;
    }

    // from skyhanni ActiveBossTransparency.onEntityTransparencyTick
    private static boolean fadesInBossFight(LivingEntity e) {
        if (e instanceof net.minecraft.world.entity.player.Player p && p.getUUID().version() != 4) return false;
        Mobs.Mob m = Mobs.of(e);
        if (m != null) {
            if (m == lastClicked) return false;
            if (m.category == Mobs.Category.SLAYER && m.mine()) return false;
        }
        if (e instanceof net.minecraft.world.entity.player.Player) return o(FADE, "players");
        return true;
    }

    // skyhanni HideIrrelevantMobsInSlayerArea, only the burning desert magma cubes
    private static boolean irrelevant(LivingEntity e) {
        Mobs.Mob m = Mobs.of(e);
        return m != null && (m.name.equals("Magma Cube") || m.name.equals("Magma Cube Rider"));
    }

    static void register() {
        Features.add(new Feature(MINIBOSS, "Slayer", "Slayer minibosses", "An alert when a miniboss of your slayer spawns, and a highlight while it lives.", "Skyblocker: MiniBoss Spawn Alert · SkyHanni: Miniboss Highlight").when(Combat::sb)
                .option(Option.bool("slayer_miniboss.alert", "Alert", "A title and a sound when your own miniboss spawns.", true))
                .option(Option.bool("slayer_miniboss.others", "Also others' minibosses", "Near you, of your slayer. These have no chat line.", false))
                .option(Option.bool("slayer_miniboss.name", "Name in the alert", "Which miniboss it is, instead of the word miniboss.", false))
                .option(Option.bool("slayer_miniboss.hide_chat", "Hide the chat line", "", false))
                .option(Option.bool("slayer_miniboss.highlight", "Highlight", "A glow on a miniboss you can see.", true))
                .option(Option.color("slayer_miniboss.color", "Highlight colour", "", 0xFF55FFFF))
                .option(Option.bool("slayer_miniboss.line", "Line to it", "From your crosshair.", false))
                .option(Option.integer("slayer_miniboss.line_width", "Line width", "", 3, 1, 10)));
        Features.add(new Feature(GROUND, "Slayer", "Slayer items on the ground", "The name and worth of what is lying near you during a quest.", "SkyHanni: Items on Ground").when(Combat::sb)
                .option(Option.integer("slayer_ground_items.min_price", "At least worth", "Coins, for the whole stack.", 50_000, 0, 10_000_000)));
        Features.add(new Feature(WARNING, "Slayer", "Slayer quest warning", "When you hit slayer mobs with no quest running, or in the wrong slayer's area.", "SkyHanni: Quest Warning").when(Combat::sb)
                .option(Option.bool("slayer_quest_warning.title", "Also a title", "", true)));
        Features.add(new Feature(TIMES, "Slayer", "Slayer times in chat", "How long the kill took, your best, and how long the whole quest took.", "SkyHanni: Slayer Time Messages · Skyblocker: Slayer Timer").when(Combat::sb)
                .option(Option.bool("slayer_time_messages.kill", "Kill time", "", true))
                .option(Option.bool("slayer_time_messages.best", "Personal best", "", true))
                .option(Option.bool("slayer_time_messages.quest_complete", "Quest time", "", true))
                .option(Option.bool("slayer_time_messages.compact", "Shorter lines", "", false)));
        Features.add(new Feature(COCOON, "Slayer", "Slayer cocoon warning", "A title when you cocoon your slayer boss.", "SkyHanni: Slayer Cocoon Title").when(Combat::sb)
                .option(Option.bool("slayer_cocoon_warning.ding", "Also a sound", "", true)));
        Features.add(new Feature(MADDOX, "Slayer", "Call Maddox", "A line to click when a quest fails, to call Maddox for a new one. Only your click calls him.", "Skyblocker: Call Maddox").when(Combat::sb)
                .option(Option.bool("slayer_maddox.on_fail", "When a quest fails", "", true))
                .option(Option.bool("slayer_maddox.on_kill", "When you killed the boss", "", false)));
        Features.add(new Feature(GUMMY, "Slayer", "Polar Bear warning", "When you fight with a slayer weapon or Habanero Tactics and have no Re-heated Gummy Polar Bear going.", "SkyHanni: No Gummy Warning").when(Combat::sb));
        Features.add(new Feature(REWARDS, "Slayer", "Unclaimed slayer rewards", "Green in the Slayer LVL Rewards menu on what you can claim.", "Skyblocker: Highlight Unclaimed Rewards").when(Combat::sb));
        MenuMarks.add(REWARDS, Pattern.compile(".*Slayer LVL Rewards$"), SlayerFeatures::rewards);
        Features.add(new Feature(NOT_SPAWNABLE, "Slayer", "Block slayers that cannot spawn here", "In the Slayer menu, a click on a quest that says it does not exist here does nothing.", "SkyHanni: Block Not Spawnable").when(Combat::sb).defaultOff());
        Interactions.onSlotClick((screen, slot, id, button) -> {
            if (!Features.on(NOT_SPAWNABLE) || slot == null || !GameState.strip(screen.getTitle().getString()).trim().equals("Slayer")) return false;
            return MenuView.Item.of(slot.getItem()).first(NOT_SPAWN.pattern()) != null;
        });
        Features.add(new Feature(HIDE_NAMES, "Slayer", "Hide mob names", "Hides the name tags of the mobs you farm for a slayer quest while they are at full health or dead.", "SkyHanni: Hide Mob Names · Skyblocker: Hide Sven Pup Nametag").when(Combat::sb).defaultOff()
                .option(Option.bool("slayer_hide_names.mobs", "Slayer area mobs", "", true))
                .option(Option.bool("slayer_hide_names.sven", "Sven pups", "", true)));
        CombatMarks.hiding(() -> Features.on(HIDE_NAMES), SlayerFeatures::nameHidden);
        Features.add(new Feature(HIDE_PARTICLES, "Slayer", "Hide slayer spawn particles", "Where a mob just died during your quest, in the right area.", "SkyHanni: Spawn Particles").when(Combat::sb).defaultOff()
                .option(Option.bool("slayer_hide_spawn_particles.white", "White", "", true))
                .option(Option.bool("slayer_hide_spawn_particles.purple", "Purple", "", true))
                .option(Option.bool("slayer_hide_spawn_particles.specific", "Slayer specific", "", true)));
        CombatMarks.particles(() -> Features.on(HIDE_PARTICLES) && !recentDeaths.isEmpty(), SlayerFeatures::spawnParticle);
        Features.add(new Feature(MUTE, "Slayer", "Mute slayer mob sounds", "Quiets the mobs of a slayer area.", "Skyblocker: Mute Sounds").when(Combat::sb).defaultOff()
                .option(Option.bool("slayer_mute_sounds.blaze", "Blazes", "On the Crimson Isle.", true))
                .option(Option.bool("slayer_mute_sounds.wolf", "Wolves", "In the Hub and the Park.", true))
                .option(Option.bool("slayer_mute_sounds.spider", "Spiders", "In the Spider's Den and on the Crimson Isle, not while fighting an Inferno Demonlord.", true))
                .option(Option.bool("slayer_mute_sounds.enderman", "Endermen", "In The End.", true)));
        SoundHooks.listen(SlayerFeatures::onSound);
        Features.add(new Feature(FADE, "Slayer", "See-through during your boss", "While your slayer boss is alive, other mobs are see-through. Your boss and what you hit last stay solid.", "SkyHanni: Active Boss Transparency").when(Combat::sb).defaultOff()
                .option(Option.integer("slayer_active_boss_fade.percent", "How solid (%)", "", 35, 15, 70))
                .option(Option.bool("slayer_active_boss_fade.players", "Also other players", "", false)));
        CombatMarks.fading(() -> Features.on(FADE) && SlayerApi.inBossFight(), SlayerFeatures::fadesInBossFight, () -> Combat.integer(FADE, "percent", 35));
        Features.add(new Feature(IRRELEVANT, "Slayer", "See-through mobs of no use", "Mobs that give nothing for your quest are see-through: the Burning Desert's Magma Cubes.", "SkyHanni: Hide Irrelevant Mobs").when(Combat::sb).defaultOff()
                .option(Option.integer("slayer_irrelevant_mobs.percent", "How solid (%)", "", 40, 0, 100)));
        CombatMarks.fading(() -> Features.on(IRRELEVANT) && SlayerApi.inCorrectArea() && "Burning Desert".equals(GameState.area), SlayerFeatures::irrelevant, () -> Combat.integer(IRRELEVANT, "percent", 40));
        Feature f = Features.add(new Feature("slayer_helpers_tick", "Slayer", "Slayer background", "Keeps the slayer helpers running.", "").alwaysOn().when(Combat::sb).tick(SlayerFeatures::tick).world(SlayerFeatures::draw).left(SlayerFeatures::left));
        ChatBus.register(ChatBus.OBSERVE + 14, "slayerfeatures", f, SlayerFeatures::onChat);
        SlayerApi.onProgress(SlayerFeatures::onProgress);
        SlayerApi.onState(SlayerFeatures::onState);
        SlayerApi.onQuestComplete(SlayerFeatures::questDone);
        SlayerApi.onAttack(SlayerFeatures::onAttack);
        Mobs.onSpawn(SlayerFeatures::miniSpawn);
        Mobs.onGone(m -> {
            minis.remove(m.base.getId());
            if (lastClicked == m) lastClicked = null;
        });
        Interactions.onItemClick(right -> {
            var mc = Minecraft.getInstance();
            if (right && mc.player != null) {
                String id = Gain.sbId(mc.player.getMainHandItem());
                if (id == null || id.isEmpty() || TELEPORT_ITEMS.contains(id)) return false;
            }
            lastWeaponUse = System.currentTimeMillis();
            return false;
        });
        Safe.commands("SlayerFeatures.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc").then(literal("slayers")
                .then(literal("revenant").executes(c -> sayBest(SlayerApi.Type.REVENANT)))
                .then(literal("tarantula").executes(c -> sayBest(SlayerApi.Type.TARANTULA)))
                .then(literal("sven").executes(c -> sayBest(SlayerApi.Type.SVEN)))
                .then(literal("voidgloom").executes(c -> sayBest(SlayerApi.Type.VOID)))
                .then(literal("demonlord").executes(c -> sayBest(SlayerApi.Type.INFERNO)))
                .then(literal("vampire").executes(c -> sayBest(SlayerApi.Type.VAMPIRE))))));
        CombatMarks.glowing(mark -> {
            if (!Features.on(MINIBOSS) || !o(MINIBOSS, "highlight")) return;
            int color = Combat.color(MINIBOSS, "color", 0xFF55FFFF);
            for (Mobs.Mob m : minis.values()) CombatMarks.seenGlow(mark, m.base, color);
        });
    }

    private static void left() {
        lastHealth.clear();
        recentDeaths.clear();
        minis.clear();
        groundNames.clear();
        lastClicked = null;
        bossAt = 0;
        slainAt = 0;
        warned = false;
    }

    // from skyblocker RewardsHighlighter
    private static void rewards(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) if ("Click to claim rewards!".equals(v.items[slot].lastClean())) v.back(slot, MenuView.GREEN);
    }
}
