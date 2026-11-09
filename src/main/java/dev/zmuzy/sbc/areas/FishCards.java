// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.areas;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.core.game.GameProfile;
import dev.zmuzy.sbc.core.game.Seen;
import dev.zmuzy.sbc.dungeon.DungeonMarks;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.AttributeShards;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the fishing cards. from skyhanni FishingBaitDisplay + Warnings, FishingHookDisplay, FishingBobberTimer,
// BarnFishingTimer, with skyblocker FishingHudWidget + SeaCreatureTracker
public final class FishCards {
    private static final BooleanSupplier SKYBLOCK = () -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV;
    private FishCards() {}

    private static Option opt(String id, String key) {
        return Features.get(id).option(id + "." + key);
    }

    private static boolean on(String id, String key) {
        return Features.on(id) && opt(id, key).bool();
    }

    // --- bait ---

    private static final String BAIT = "fishing_bait";

    static void baitChanged(boolean wasBait, String before, String after) {
        if (!FishState.holdingRod || !wasBait || !on(BAIT, "warn_change")) return;
        FishHooks.pling();
        FishHooks.title("§eBait changed!");
        FishHooks.say("§b[SBC] §fFishing bait changed: " + before + " §e-> " + (after.isEmpty() ? "None" : after));
    }

    private static void noBait() {
        if (on(BAIT, "warn_none") && FishState.baitKnown && FishState.baitId.isEmpty()) {
            FishHooks.pling();
            FishHooks.title("§cNo bait is used!");
        }
    }

    private static RichCard.Content baitContent(String name, int amount, ItemStack icon) {
        RichCard.Line l = name.isEmpty() ? RichCard.Line.row("Bait", "§cNo Bait").raw() : RichCard.Line.row(name, "§b" + String.format(Locale.ROOT, "%,d", amount) + "x").key("Bait").raw();
        if (icon != null && !icon.isEmpty() && opt(BAIT, "icon").bool()) l = l.icon(icon);
        return new RichCard.Content("", List.of(l));
    }

    private static final RichCard BAIT_CARD = new RichCard(BAIT, "Bait", "Fishing", Zone.TOP_LEFT, 38, BAIT) {
        @Override
        public boolean visible() {
            return FishState.holdingRod || dev.zmuzy.sbc.SbcMod.DEV;
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        protected Content live() {
            return baitContent(FishState.baitName, FishState.baitAmount, FishState.baitStack);
        }

        @Override
        protected Content sample() {
            return baitContent("§9Whale Bait", 1250, null);
        }

        @Override
        public List<Content> states() {
            return List.of(baitContent("", 0, null));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Bait", "1,250x", 0));
        }
    };

    // --- reel timer ---

    private static final String REEL = "fishing_reel";
    // from skyblocker FishingHookDisplayHelper: "3.0" counts down, "!!!" = reel
    private static final Pattern TIME = Pattern.compile("\\d+\\.\\d+");
    private static int reelStand = -1;
    private static String reelText = "";
    private static double reelDistance;
    private static boolean reeled;

    private static final FishHooks.Scan REEL_SCAN = new FishHooks.Scan() {
        private int best;
        private String text;
        private double dist;

        @Override
        public boolean wanted() {
            return Features.on(REEL) && FishState.holdingRod;
        }

        @Override
        public void begin() {
            best = -1;
            text = "";
            dist = 9;
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof ArmorStand st) || !st.hasCustomName()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.player.fishing == null) return;
            double d = st.distanceToSqr(mc.player.fishing);
            if (d >= dist) return;
            String name = GameState.strip(st.getCustomName().getString()).trim();
            if (!name.equals("!!!") && !TIME.matcher(name).matches()) return;
            best = st.getId();
            text = name;
            dist = d;
        }

        @Override
        public void end() {
            reelStand = best;
            reelText = text;
            reelDistance = dist;
            Minecraft mc = Minecraft.getInstance();
            boolean bite = text.equals("!!!");
            if (bite && !reeled && opt(REEL, "title").bool()) FishHooks.title("§a§lReel now!");
            reeled = bite;
            if (best >= 0 && opt(REEL, "hide_stand").bool() && mc.level != null) {
                Entity st = mc.level.getEntity(best);
                if (st != null) FishHooks.hide(st);
            }
        }
    };

    private static String reelValue(String raw) {
        if (raw.isEmpty()) return "§7Line out";
        boolean alert = raw.equals("!!!");
        if (!alert) {
            try {
                alert = Float.parseFloat(raw) <= opt(REEL, "reel_in").number();
            } catch (NumberFormatException ignored) {
            }
        }
        if (alert) return opt(REEL, "alert_text").text().replace("&", "§");
        return "§e§l" + raw;
    }

    // from skyhanni FishingBobberTimer + skyblockers progress to 20 sec. at 20 it changes color (what changeTimerColor did)
    private static RichCard.Content reelContent(String raw, long ms) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Reel", reelValue(raw)).raw());
        if (ms >= 0) {
            double s = ms / 1000.0;
            out.add(RichCard.Line.row("Bobber", (s >= 20 ? "§a" : "§f") + String.format(Locale.ROOT, "%.1fs", s)).raw().meter(Math.min(1, s / 20), s >= 20 ? Modern.GOOD : 0xFF4FC3F7));
        }
        return new RichCard.Content("", out);
    }

    private static final RichCard REEL_CARD = new RichCard(REEL, "Reel timer", "Fishing", Zone.TOP_LEFT, 38, REEL) {
        @Override
        public boolean visible() {
            Minecraft mc = Minecraft.getInstance();
            return (mc.player != null && mc.player.fishing != null && FishState.holdingRod) || dev.zmuzy.sbc.SbcMod.DEV;
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        protected Content live() {
            long from = opt(REEL, "from_liquid").bool() ? FishState.liquidAt : FishState.castAt;
            return reelContent(reelText, from == 0 ? -1 : System.currentTimeMillis() - from);
        }

        @Override
        protected Content sample() {
            return reelContent("2.5", 12_400);
        }

        @Override
        public List<Content> states() {
            return List.of(reelContent("!!!", 21_000), reelContent("", -1));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Reel", "2.5", 0));
        }
    };

    // --- sea creature cap ---

    private static final String CAP = "fishing_cap";
    private static final int GLOBAL_CAP = 60, PERSONAL_CAP = 10;
    private static final Vec3 HUB_BARN = new Vec3(108, 89, -252);
    private static long lastCapWarning;

    // from skyhanni BarnFishingTimer.updateLocation + the move check. skyblockers onlyShowHudInBarn is the same spot
    private static boolean capHere() {
        if (opt(CAP, "anywhere").bool()) return true;
        Minecraft mc = Minecraft.getInstance();
        return switch (GameState.island) {
            case "CRYSTAL_HOLLOWS" -> opt(CAP, "crystal_hollows").bool();
            case "CRIMSON_ISLE" -> opt(CAP, "crimson").bool();
            case "WINTER" -> opt(CAP, "winter").bool();
            case "PRIVATE_ISLAND" -> opt(CAP, "stranded").bool() && FishState.stranded();
            case "HUB" -> mc.player != null && mc.player.position().distanceTo(HUB_BARN) < 50;
            default -> false;
        };
    }

    private static void capTick(Minecraft mc) {
        if (FishState.liveCount() == 0 || !capHere()) return;
        long now = System.currentTimeMillis();
        if (now - lastCapWarning < 5000) return;
        long age = FishState.oldestAge();
        String title = null, line = null;
        if (opt(CAP, "time_alert").bool() && age >= opt(CAP, "alert_seconds").number() * 1000) {
            title = "§cFishing Time Limit!";
            line = "Reached barn fishing time limit!";
        } else if (opt(CAP, "warn_personal").bool() && FishState.ownCount() >= PERSONAL_CAP) {
            title = "§cReached Personal Cap!";
            line = "Reached personal sea creature cap!";
        } else if (opt(CAP, "warn_global").bool() && FishState.liveCount() >= GLOBAL_CAP) {
            title = "§cReached Global Cap!";
            line = "Reached global sea creature cap!";
        }
        if (title == null) return;
        lastCapWarning = now;
        FishHooks.pling();
        FishHooks.title(title);
        FishHooks.say("§c" + line);
    }

    private static RichCard.Content capContent(int own, int all, long ageMs) {
        List<RichCard.Line> out = new ArrayList<>();
        double alert = opt(CAP, "alert_seconds").number() * 1000;
        out.add(RichCard.Line.row("Yours", (own >= PERSONAL_CAP ? "§c" : "§a") + own + "§7/" + PERSONAL_CAP).raw().meter(Math.min(1, own / (double) PERSONAL_CAP), own >= PERSONAL_CAP ? Modern.BAD : Modern.GOOD));
        out.add(RichCard.Line.row("Around", (all >= GLOBAL_CAP ? "§c" : "§f") + all + "§7/" + GLOBAL_CAP).raw().meter(Math.min(1, all / (double) GLOBAL_CAP), all >= GLOBAL_CAP ? Modern.BAD : 0xFF4FC3F7));
        long left = (long) Math.max(0, alert - ageMs);
        out.add(RichCard.Line.row("Time left", (left == 0 ? "§c" : "§b") + SbTime.dur(left)).raw().meter(left / alert, left == 0 ? Modern.BAD : 0xFF81C784));
        return new RichCard.Content("", out);
    }

    private static final RichCard CAP_CARD = new RichCard(CAP, "Sea creature cap", "Fishing", Zone.TOP_LEFT, 38, CAP) {
        @Override
        public boolean visible() {
            return (FishState.liveCount() > 0 && capHere()) || dev.zmuzy.sbc.SbcMod.DEV;
        }

        @Override
        protected Content live() {
            return capContent(FishState.ownCount(), FishState.liveCount(), Math.max(0, FishState.oldestAge()));
        }

        @Override
        protected Content sample() {
            return capContent(7, 22, 184_000);
        }

        @Override
        public List<Content> states() {
            return List.of(capContent(10, 60, 320_000));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Yours", "7/10", 0));
        }
    };

    // --- golden fish ---

    private static final String GOLDEN = "fishing_golden";
    private static final int MAX_INTERACTIONS = 3;
    private static final long DESPAWN = 60_000, MAX_ROD = 180_000, TIMEOUT = 10_000;
    private static final Pattern G_INTERACT = Pattern.compile("^The Golden Fish escapes your hook but looks weakened\\.$"), G_WEAK = Pattern.compile("^The Golden Fish is weak!$");
    private static long gChatAt, gLastFish, gLastThrow, gDespawnAt, gBase;
    private static int gInteractions;
    private static boolean gDownInit = true, gDownPost, gWarned, gHasLavaRod, gWeak;
    private static ArmorStand gEntity;

    private static boolean goldenIsland() {
        return "CRIMSON_ISLE".equals(GameState.island) || (FishState.stranded() && "PRIVATE_ISLAND".equals(GameState.island));
    }

    private static boolean goldenActive() {
        return Features.on(GOLDEN) && goldenIsland() && gHasLavaRod && (FishState.fishing() || System.currentTimeMillis() - gLastThrow < MAX_ROD);
    }

    // from skyhanni minimumSpawnTime + maximumSpawnTime: the goldfin shard attribute takes 30 sec off per level
    private static long minSpawn() {
        return 8 * 60_000L - 30_000L * AttributeShards.activeLevel("SHARD_GOLDFIN");
    }

    private static void goldenRemove() {
        gDespawnAt = 0;
        gEntity = null;
        gWeak = false;
    }

    private static void goldenChat(String t) {
        if (FishForageHunt.GOLDEN_SPAWN.matcher(t).matches()) gChatAt = System.currentTimeMillis();
        else if (G_INTERACT.matcher(t).matches()) {
            gDespawnAt = System.currentTimeMillis() + DESPAWN;
            gInteractions++;
        } else if (G_WEAK.matcher(t).matches()) {
            gDespawnAt = System.currentTimeMillis() + DESPAWN;
            gWeak = true;
        } else if (FishForageHunt.GOLDEN_DESPAWN.matcher(t).matches()) {
            gBase = System.currentTimeMillis();
            goldenRemove();
        } else {
            Matcher m = FishForageHunt.TROPHY.matcher(t);
            if (m.matches() && m.group(1).equals("Golden Fish")) {
                gBase = System.currentTimeMillis();
                goldenRemove();
            }
        }
    }

    private static final FishHooks.Scan GOLDEN_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            return Features.on(GOLDEN) && gEntity == null && gChatAt != 0 && System.currentTimeMillis() - gChatAt < TIMEOUT;
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof ArmorStand st) || gEntity != null) return;
            if (!DungeonMarks.headTexture(st.getItemBySlot(EquipmentSlot.HEAD)).equals(DungeonMarks.skull("GOLDEN_FISH"))) return;
            gEntity = st;
            gLastFish = System.currentTimeMillis();
            gChatAt = 0;
            gInteractions = 0;
            gWeak = false;
            gDespawnAt = gLastFish + DESPAWN;
        }
    };

    private static int gTicks;

    private static void goldenReset() {
        gChatAt = gLastFish = gLastThrow = gBase = 0;
        gInteractions = 0;
        gHasLavaRod = false;
        goldenRemove();
    }

    private static void goldenTick(Minecraft mc) {
        if (mc.player == null || !goldenIsland()) return;
        if (++gTicks % 20 == 0) {
            gHasLavaRod = false;
            var inv = mc.player.getInventory();
            for (int i = 0; i < 36 && !gHasLavaRod; i++) gHasLavaRod = FishState.isLavaRod(Gain.sbId(inv.getItem(i)));
            if (gLastThrow != 0 && System.currentTimeMillis() - gLastThrow > MAX_ROD) {
                gBase = 0;
                gLastThrow = 0;
            }
            if (gLastThrow != 0 && MAX_ROD - (System.currentTimeMillis() - gLastThrow) < opt(GOLDEN, "warn_seconds").number() * 1000 && opt(GOLDEN, "throw_warning").bool() && !gWarned) {
                gWarned = true;
                FishHooks.title("§cThrow your rod!");
                FishHooks.pling();
            }
        }
        // only counts as thrown in lava when the rod goes down, up, down again. skyhannis best fix so far
        var bobber = mc.player.fishing;
        if (bobber == null) {
            gDownInit = true;
            gDownPost = false;
            return;
        }
        if (!bobber.isInLava() || bobber.tickCount < 5) return;
        double vy = bobber.getDeltaMovement().y;
        if (vy > 0 && gDownInit) gDownInit = false;
        else if (vy < 0 && !gDownInit && !gDownPost) {
            gWarned = false;
            gDownPost = true;
            gLastThrow = System.currentTimeMillis();
            if (gBase == 0) gBase = gLastThrow;
        }
    }

    private static RichCard.Content goldenContent(long now, boolean up, int interactions, long despawnAt, long lastFish, long lastThrow, long base, long min) {
        List<RichCard.Line> out = new ArrayList<>();
        if (up) {
            out.add(RichCard.Line.row("Hits", "§b" + interactions + "§7/" + MAX_INTERACTIONS).raw().meter(Math.min(1, interactions / (double) MAX_INTERACTIONS)));
            out.add(RichCard.Line.row("Despawns", "§b" + SbTime.dur(Math.max(0, despawnAt - now))).raw());
            return new RichCard.Content("", out);
        }
        out.add(RichCard.Line.row("Last fish", lastFish == 0 ? "§cNone yet" : "§b" + SbTime.dur(now - lastFish)).raw());
        out.add(RichCard.Line.row("Last throw", lastThrow == 0 ? "§cNone yet" : "§b" + SbTime.dur(now - lastThrow) + " §3" + SbTime.dur(Math.max(0, lastThrow + MAX_ROD - now))).raw());
        if (base == 0) out.add(RichCard.Line.row("Spawn", "§cCast rod").raw());
        else if (base + min > now) out.add(RichCard.Line.row("Spawn in", "§b" + SbTime.dur(base + min - now)).raw());
        else {
            long since = now - base - min;
            double chance = Math.min(1, since / (double) (4 * 60_000L));
            out.add(RichCard.Line.row("Possible", "§a" + SbTime.dur(since)).raw());
            out.add(RichCard.Line.row("Chance", "§b" + Math.round(chance * 100) + "%").raw().meter(chance));
        }
        return new RichCard.Content("", out);
    }

    private static final RichCard GOLDEN_CARD = new RichCard(GOLDEN, "Golden Fish", "Fishing", Zone.TOP_LEFT, 37, GOLDEN) {
        @Override
        public boolean visible() {
            return goldenActive() || dev.zmuzy.sbc.SbcMod.DEV;
        }

        @Override
        protected Content live() {
            return goldenContent(System.currentTimeMillis(), gEntity != null, gInteractions, gDespawnAt, gLastFish, gLastThrow, gBase, minSpawn());
        }

        @Override
        protected Content sample() {
            long now = 1_000_000_000L;
            return goldenContent(now, false, 0, 0, now - 512_000, now - 42_000, now - 500_000, 480_000);
        }

        @Override
        public List<Content> states() {
            long now = 1_000_000_000L;
            return List.of(goldenContent(now, true, 2, now + 43_000, now - 17_000, now - 12_000, now - 500_000, 480_000), goldenContent(now, false, 0, 0, 0, 0, 0, 480_000), goldenContent(now, false, 0, 0, 0, now - 12_000, now - 60_000, 480_000));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Spawn in", "1m", 0));
        }
    };

    private static void goldenDraw(WorldDraw w) {
        if (gEntity == null || !goldenActive()) return;
        if (gEntity.isRemoved()) {
            gEntity = null;
            return;
        }
        Vec3 at = gEntity.position().add(0, 2.5, 0);
        if (opt(GOLDEN, "highlight").bool() && gWeak) w.box(gEntity.getBoundingBox().inflate(0.3), 0xFF55FF55, false, false);
        if (!opt(GOLDEN, "nametag").bool() || at.distanceTo(w.eye) > 20) return;
        w.label("§b" + SbTime.dur(Math.max(0, gDespawnAt - System.currentTimeMillis())), at.add(0, 0.5, 0), false);
        if (gInteractions >= MAX_INTERACTIONS) w.label("§cPULL", at.add(0, 0.25, 0), false);
        w.label("§6Golden Fish §a(" + gInteractions + "/" + MAX_INTERACTIONS + ")", at, false);
    }

    // --- totem of corruption ---

    private static final String TOTEM = "fishing_totem";

    private record Totem(java.util.UUID id, Vec3 at, long leftS, String owner, double distance) {
        boolean own() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && owner.equals(mc.player.getName().getString());
        }
    }

    private static List<Totem> allTotems = List.of(), totems = List.of();
    private static final Map<java.util.UUID, Long> warnedTotems = new HashMap<>();
    private static long totemsAt;

    private static final FishHooks.Scan TOTEM_SCAN = new FishHooks.Scan() {
        private final List<ArmorStand> names = new ArrayList<>();
        private final Map<ArmorStand, String> all = new LinkedHashMap<>();

        @Override
        public boolean wanted() {
            return Features.on(TOTEM) && System.currentTimeMillis() - totemsAt >= 2000;
        }

        @Override
        public void begin() {
            names.clear();
            all.clear();
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof ArmorStand st) || !st.hasCustomName()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || st.distanceToSqr(mc.player) > 10_000) return;
            String n = GameState.strip(st.getCustomName().getString());
            if (Patterns.get("SH.fishing.totemofcorruption.totemname-nocolor").matcher(n).matches()) names.add(st);
            else if (n.startsWith("Remaining:") || n.startsWith("Owner:")) all.put(st, n);
        }

        @Override
        public void end() {
            totemsAt = System.currentTimeMillis();
            Minecraft mc = Minecraft.getInstance();
            List<Totem> out = new ArrayList<>();
            for (ArmorStand t : names) {
                long left = -1;
                String owner = null;
                for (var e : all.entrySet()) {
                    if (e.getKey().distanceToSqr(t) > 4) continue;
                    Matcher m = Patterns.get("SH.fishing.totemofcorruption.timeremaining-nocolor").matcher(e.getValue());
                    if (m.matches()) left = (m.group("min") == null ? 0 : Integer.parseInt(m.group("min")) * 60L) + Integer.parseInt(m.group("sec"));
                    m = Patterns.get("SH.fishing.totemofcorruption.owner-nocolor").matcher(e.getValue());
                    if (m.matches()) owner = m.group("owner");
                }
                if (left >= 0 && owner != null) out.add(new Totem(t.getUUID(), t.position(), left, owner, mc.player == null ? 0 : t.position().distanceTo(mc.player.position())));
            }
            allTotems = out;
            boolean onlyOwn = opt(TOTEM, "own_only").bool();
            List<Totem> shown = new ArrayList<>();
            for (Totem t : out) if (!onlyOwn || t.own()) shown.add(t);
            totems = shown;
            int warn = (int) opt(TOTEM, "warn_seconds").number();
            warnedTotems.values().removeIf(at -> totemsAt - at > 120_000);
            for (Totem t : shown) {
                if (warn <= 0 || t.leftS() > warn || warnedTotems.containsKey(t.id())) continue;
                warnedTotems.put(t.id(), totemsAt);
                FishHooks.pling();
                FishHooks.title("§c§lTotem of Corruption §eabout to expire!");
            }
        }
    };

    private static boolean totemParticle(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p) {
        if (allTotems.isEmpty() || !on(TOTEM, "hide_particles") || p.particle().getType() != net.minecraft.core.particles.ParticleTypes.WITCH || p.xMaxSpeed() != 0f) return false;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        for (Totem t : allTotems) if (t.at().distanceTo(at) < 4) return true;
        return false;
    }

    private static Totem totemToShow() {
        double max = opt(TOTEM, "distance").number();
        Totem best = null;
        for (Totem t : totems) {
            if (t.distance() >= max) continue;
            if (t.own()) return t;
            if (best == null || t.distance() < best.distance()) best = t;
        }
        return best;
    }

    private static RichCard.Content totemContent(long left, String owner) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Left", "§e" + SbTime.dur(left * 1000)).raw().meter(Math.min(1, left / 300.0), 0xFFAA55FF), RichCard.Line.row("Owner", "§e" + owner).raw()));
    }

    private static final RichCard TOTEM_CARD = new RichCard(TOTEM, "Totem of Corruption", "Fishing", Zone.TOP_LEFT, 37, TOTEM) {
        @Override
        public boolean visible() {
            return (opt(TOTEM, "overlay").bool() && totemToShow() != null) || dev.zmuzy.sbc.SbcMod.DEV;
        }

        @Override
        protected Content live() {
            Totem t = totemToShow();
            return t == null ? new Content("", List.of()) : totemContent(t.leftS(), t.owner());
        }

        @Override
        protected Content sample() {
            return totemContent(150, "You");
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Left", "2m 30s", 0));
        }
    };

    private static void totemDraw(WorldDraw w) {
        if (opt(TOTEM, "area").choice().equals("Off")) return;
        int color = 0xFF000000 | (opt(TOTEM, "color").color() & 0xFFFFFF);
        for (Totem t : totems) FishWorld.sphere(w, t.at().add(0, 1, 0), 16, color);
    }

    // --- shark counter ---

    private static final String SHARK = "fishing_shark";
    private static final String[][] SHARKS = {{"Nurse", "§a"}, {"Blue", "§9"}, {"Tiger", "§5"}, {"Great White", "§6"}};
    private static final int[] sharks = new int[4];

    private static RichCard.Content sharkContent(int[] counts) {
        int total = 0;
        for (int c : counts) total += c;
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Total", "§e" + total).raw());
        for (int i = 0; i < 4; i++) out.add(RichCard.Line.row(SHARKS[i][0], SHARKS[i][1] + counts[i]).raw());
        return new RichCard.Content("", out);
    }

    private static final RichCard SHARK_CARD = new RichCard(SHARK, "Sharks", "Fishing", Zone.TOP_LEFT, 37, SHARK) {
        @Override
        public boolean visible() {
            return (FishState.holdingWater && (sharks[0] + sharks[1] + sharks[2] + sharks[3] > 0)) || dev.zmuzy.sbc.SbcMod.DEV;
        }

        @Override
        protected Content live() {
            return sharkContent(sharks);
        }

        @Override
        protected Content sample() {
            return sharkContent(new int[]{41, 18, 6, 2});
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Total", "67", 0));
        }
    };

    // --- creature health ---

    private static final String HEALTH = "fishing_mob_health";

    private static RichCard.Content healthContent(List<String[]> rows) {
        List<RichCard.Line> out = new ArrayList<>();
        for (String[] r : rows) out.add(RichCard.Line.row(r[0], r[1]).raw().meter(Double.parseDouble(r[2]), Modern.BAD));
        return new RichCard.Content("", out);
    }

    private static List<String[]> healthRows() {
        List<String[]> rows = new ArrayList<>();
        boolean rare = opt(HEALTH, "only_rare").bool();
        int limit = (int) opt(HEALTH, "limit").number();
        for (FishState.Live l : FishState.live()) {
            if (l.maxHp <= 0 || (rare && !l.kind.rare())) continue;
            rows.add(new String[]{(l.own ? "§a" : "§c") + l.kind.name(), "§f" + Vitals.compact(l.hp) + "§7/§a" + Vitals.compact(l.maxHp), String.valueOf(Math.max(0, Math.min(1, l.hp / (double) l.maxHp)))});
            if (rows.size() >= limit) break;
        }
        return rows;
    }

    private static final RichCard HEALTH_CARD = new RichCard(HEALTH, "Sea creature health", "Fishing", Zone.TOP_LEFT, 37, HEALTH) {
        @Override
        public boolean visible() {
            return !healthRows().isEmpty() || dev.zmuzy.sbc.SbcMod.DEV;
        }

        @Override
        protected Content live() {
            return healthContent(healthRows());
        }

        @Override
        protected Content sample() {
            return healthContent(List.of(new String[]{"§aWater Hydra", "§f620k§7/§a1M", "0.62"}, new String[]{"§cSea Emperor", "§f810k§7/§a3M", "0.27"}));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Water Hydra", "620k/1M", 0));
        }
    };

    // --- trophy fish ---

    private static final String TROPHY = "fishing_trophy", TROPHY_CHAT = "fishing_trophy_chat";
    private static final String[] RANKS = {"Bronze", "Silver", "Gold", "Diamond"}, RANK_CODE = {"§c", "§7", "§6", "§b"};
    public static net.minecraft.client.KeyMapping trophyKey;
    private static JsonObject trophyInfo;
    private static String newFish = "", newRank = "";
    private static long newAt;

    private static JsonObject info() {
        if (trophyInfo == null) {
            JsonObject o = Repo.constant("TrophyFish.json");
            if (o == null || !o.has("trophy_fish")) return new JsonObject();
            trophyInfo = o.getAsJsonObject("trophy_fish");
        }
        return trophyInfo;
    }

    // from skyhanni TrophyFishApi.getInternalName
    private static String key(String display) {
        return display.replace("Obfuscated", "Obfuscated Fish").replaceAll("[- ]", "").toLowerCase(Locale.ROOT);
    }

    private static String name(String key) {
        JsonObject i = info();
        return i.has(key) ? GameState.strip(i.getAsJsonObject(key).get("displayName").getAsString()) : key;
    }

    private static int count(Map<String, Integer> have, int rank) {
        return have == null ? 0 : have.getOrDefault(RANKS[rank], 0);
    }

    private static int total(Map<String, Integer> have) {
        int n = 0;
        for (int i = 0; i < 4; i++) n += count(have, i);
        return n;
    }

    private static RichCard.Line trophyRow(String key, Map<String, Integer> have, boolean cross, boolean check, String recentRank) {
        StringBuilder cells = new StringBuilder();
        for (int r = 0; r < 4; r++) {
            int n = count(have, r);
            if (r > 0) cells.append(' ');
            if (cross && n == 0) cells.append("§c✖");
            else cells.append(RANKS[r].equals(recentRank) ? "§a" : RANK_CODE[r]).append(check && n >= 1 ? "§l✔§r" : Vitals.compact(n));
        }
        cells.append(" §d").append(Vitals.compact(total(have)));
        return RichCard.Line.row(name(key), cells.toString()).key(name(key)).raw();
    }

    private static List<Map.Entry<String, Map<String, Integer>>> order(Map<String, Map<String, Integer>> all) {
        Map<String, Map<String, Integer>> full = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : info().entrySet()) full.put(e.getKey(), all.getOrDefault(e.getKey(), Map.of()));
        for (var e : all.entrySet()) full.putIfAbsent(e.getKey(), e.getValue());
        List<Map.Entry<String, Map<String, Integer>>> list = new ArrayList<>(full.entrySet());
        String sort = opt(TROPHY, "sort").choice();
        java.util.Comparator<Map.Entry<String, Map<String, Integer>>> c = switch (sort) {
            case "Bronze" -> java.util.Comparator.comparingInt(e -> count(e.getValue(), 0));
            case "Silver" -> java.util.Comparator.comparingInt(e -> count(e.getValue(), 1));
            case "Gold" -> java.util.Comparator.comparingInt(e -> count(e.getValue(), 2));
            case "Diamond" -> java.util.Comparator.comparingInt(e -> count(e.getValue(), 3));
            case "Name" -> java.util.Comparator.comparing(e -> name(e.getKey()));
            case "Highest tier" -> java.util.Comparator.comparingInt(e -> {
                for (int r = 3; r >= 0; r--) if (count(e.getValue(), r) > 0) return r;
                return -1;
            });
            default -> java.util.Comparator.comparingInt(e -> total(e.getValue()));
        };
        list.sort(c);
        if (opt(TROPHY, "reverse").bool()) java.util.Collections.reverse(list);
        return list;
    }

    private static RichCard.Content trophyContent(Map<String, Map<String, Integer>> all) {
        if (all == null || all.isEmpty()) return new RichCard.Content("", List.of(RichCard.Line.row("No data", "§eTalk to Odger").raw()));
        String missing = opt(TROPHY, "missing").choice();
        int atLeast = java.util.Arrays.asList(RANKS).indexOf(missing);
        boolean higher = opt(TROPHY, "higher").bool(), cross = opt(TROPHY, "cross").bool(), check = opt(TROPHY, "check").bool(), recent = opt(TROPHY, "new").bool();
        List<RichCard.Line> out = new ArrayList<>();
        for (var e : order(all)) {
            if (atLeast >= 0) {
                boolean has = false;
                for (int r = 0; r < 4; r++) if ((r == atLeast || (!higher && r > atLeast)) && count(e.getValue(), r) > 0) has = true;
                if (has) continue;
            }
            boolean justNow = recent && e.getKey().equals(newFish) && System.currentTimeMillis() - newAt < 5000;
            out.add(trophyRow(e.getKey(), e.getValue(), cross, check, justNow ? newRank : ""));
        }
        if (out.isEmpty()) out.add(RichCard.Line.row("All caught", "§e" + missing).raw());
        return new RichCard.Content("", out);
    }

    private static boolean trophyWhen() {
        return switch (opt(TROPHY, "when").choice()) {
            case "Always" -> true;
            case "With the key" -> FishHooks.held(trophyKey);
            default -> FishState.holdingLava;
        };
    }

    private static final RichCard TROPHY_CARD = new RichCard(TROPHY, "Trophy fish", "Fishing", Zone.TOP_RIGHT, 30, TROPHY) {
        @Override
        public boolean visible() {
            if (dev.zmuzy.sbc.SbcMod.DEV) return true;
            if (!FishCards.goldenIsland() || !trophyWhen() || FishState.treasureHook) return false;
            return !opt(TROPHY, "armor").bool() || FishState.trophyFishing();
        }

        @Override
        protected Content live() {
            Seen s = GameProfile.current();
            return trophyContent(s == null ? null : s.trophyFish);
        }

        @Override
        protected Content sample() {
            Map<String, Map<String, Integer>> all = new LinkedHashMap<>();
            int n = 3;
            for (String fish : new String[]{"sulphurskitter", "obfuscatedfish1", "steaminghotflounder", "gusher", "blobfish", "obfuscatedfish2", "slugfish", "flyfish", "lavahorse", "manaray", "volcanicstonefish", "vanille", "skeletonfish", "moldfin", "soulfish", "karatefish", "goldenfish", "obfuscatedfish3"}) {
                Map<String, Integer> have = new HashMap<>();
                have.put("Bronze", 40 * n % 233);
                have.put("Silver", 12 * n % 71);
                have.put("Gold", n % 9);
                have.put("Diamond", n % 4 == 0 ? 1 : 0);
                all.put(fish, have);
                n += 3;
            }
            return trophyContent(all);
        }

        @Override
        public List<Content> states() {
            return List.of(trophyContent(null));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Blobfish", "4 2 1 0", 0));
        }
    };

    private static String ordinal(int n) {
        int m = n % 100;
        if (m >= 11 && m <= 13) return "th";
        return switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    // from skyhanni TrophyFishApi.hoverInfo
    private static Component hover(String key, Map<String, Integer> have) {
        JsonObject i = info();
        if (!i.has(key)) return null;
        JsonObject o = i.getAsJsonObject(key);
        StringBuilder b = new StringBuilder(o.get("displayName").getAsString());
        if (o.has("rate")) b.append(" §8[§7").append(o.get("rate").getAsString()).append("%§8]");
        b.append('\n').append(o.get("description").getAsString()).append("\n");
        for (int r = 3; r >= 0; r--) {
            int n = count(have, r);
            b.append('\n').append(RANK_CODE[r]).append(RANKS[r]).append(": ").append(n > 0 ? "§6" + String.format(Locale.ROOT, "%,d", n) : "§c✖");
        }
        b.append("\n\n§7Total: §f").append(String.format(Locale.ROOT, "%,d", total(have)));
        return Component.literal(b.toString());
    }

    // from skyhanni TrophyFishMessages. GameProfiles reader is first in the chain and already counted this line
    private static ChatBus.Result trophyChat(ChatBus.Line line) {
        String t = line.plain().trim();
        goldenChat(t);
        Matcher m = FishForageHunt.TROPHY.matcher(t);
        if (!m.matches()) return ChatBus.PASS;
        String display = m.group(1), rankUpper = m.group(2), rank = rankUpper.charAt(0) + rankUpper.substring(1).toLowerCase(Locale.ROOT);
        String key = key(display);
        int r = java.util.Arrays.asList(RANKS).indexOf(rank);
        newFish = key;
        newRank = rank;
        newAt = System.currentTimeMillis();
        if (!Features.on(TROPHY_CHAT)) return ChatBus.PASS;
        Seen s = GameProfile.current();
        Map<String, Integer> have = s == null ? null : s.trophyFish.get(key);
        int amount = count(have, r);
        if (have != null && amount > 1 && ((r == 0 && opt(TROPHY_CHAT, "hide_bronze").bool()) || (r == 1 && opt(TROPHY_CHAT, "hide_silver").bool()))) return ChatBus.CANCEL;
        String shownName = "§f" + display, shownRank = RANK_CODE[r] + "§l" + rankUpper;
        if ((r == 2 && opt(TROPHY_CHAT, "gold_alert").bool()) || (r == 3 && opt(TROPHY_CHAT, "diamond_alert").bool())) {
            FishHooks.title(shownName + " " + shownRank + (amount > 0 ? " §8" + amount : "") + "§c!");
            if (opt(TROPHY_CHAT, "sound").bool()) FishHooks.pling();
        }
        // from skyhanni TrophyFishMessages.chatLineId: the new line replaces the last one of this fish + tier
        if (opt(TROPHY_CHAT, "hide_repeated").bool()) dev.zmuzy.sbc.chat.ChatHistory.deleteOlder(l -> l.contains("TROPHY FISH!") && l.contains(display) && l.contains(rankUpper));
        if (have == null) return ChatBus.PASS;
        String head = "§6§lTROPHY FISH! ", body = switch (opt(TROPHY_CHAT, "design").choice()) {
            case "Plain" -> "§bYou caught a " + shownName + " " + shownRank + "§b. §7(" + String.format(Locale.ROOT, "%,d", amount) + ")";
            case "Sentence" -> "§bYou caught your " + String.format(Locale.ROOT, "%,d", amount) + ordinal(amount) + " " + shownRank + " " + shownName + "§b.";
            default -> amount == 1 ? "§c§lFIRST §r" + shownRank + " " + shownName : "§7" + amount + ordinal(amount) + " §r" + shownRank + " " + shownName;
        };
        String text = head + body;
        if (opt(TROPHY_CHAT, "total").bool()) {
            int all = total(have);
            text += " §7(" + String.format(Locale.ROOT, "%,d", all) + ordinal(all) + " total)";
        }
        var out = Component.literal(text);
        if (opt(TROPHY_CHAT, "hover").bool()) {
            Component h = hover(key, have);
            if (h != null) out = out.withStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(h)));
        }
        return new ChatBus.Replace(out);
    }

    // from skyhanni TrophyFishFillet + OdgerTotalCaught
    private static void tooltip(ItemStack stack, List<Component> tip) {
        Minecraft mc = Minecraft.getInstance();
        String id = Gain.sbId(stack);
        int cut = id.lastIndexOf('_');
        if (cut > 0 && opt(TROPHY, "fillet").bool() && !(mc.gui.screen() != null && mc.gui.screen().getTitle().getString().contains("Sack"))) {
            String fish = id.substring(0, cut).replace("_", "").toLowerCase(Locale.ROOT), rank = id.substring(cut + 1).toLowerCase(Locale.ROOT);
            JsonObject i = info();
            if (i.has(fish) && i.getAsJsonObject(fish).getAsJsonObject("fillet").has(rank)) {
                int mult = mc.hasShiftDown() ? stack.getCount() : 1;
                int fillet = i.getAsJsonObject(fish).getAsJsonObject("fillet").get(rank).getAsInt() * mult;
                tip.add(Component.literal("§7Fillet: §8" + String.format(Locale.ROOT, "%,d", fillet) + " Magmafish §7(§6" + Vitals.compact(fillet * Gain.ofId("MAGMA_FISH")) + "§7)"));
            }
        }
        if (opt(TROPHY, "total_tooltip").bool() && mc.gui.screen() != null && Patterns.get("SH.fishing.trophyfish.odger.inventory").matcher(mc.gui.screen().getTitle().getString()).matches()) {
            Seen s = GameProfile.current();
            Map<String, Integer> have = s == null ? null : s.trophyFish.get(key(GameState.strip(stack.getHoverName().getString())));
            if (have == null) return;
            for (int i = 0; i < tip.size(); i++) {
                if (!Patterns.get("SH.fishing.trophy.odger.bronze.new").matcher(GameState.strip(tip.get(i).getString())).find()) continue;
                tip.add(i + 1, Component.empty());
                tip.add(i + 2, Component.literal("§7Total: §f" + String.format(Locale.ROOT, "%,d", total(have))));
                break;
            }
        }
    }

    // --- register ---

    public static void register() {
        Features.add(new Feature("fishing_state", "Fishing", "Rods, bait and sea creatures", "What the fishing helpers read: your rod, the bobber, the bait and which sea creatures are alive.", "").alwaysOn().when(SKYBLOCK).tick(FishState::tick).left(FishState::reset));
        ChatBus.register(ChatBus.OBSERVE + 7, "fishing_state", null, FishState::chat);
        FishHooks.scan(FishState.SCAN);

        Features.add(new Feature(BAIT, "Fishing", "Bait", "The bait your rod uses and how much is left, while you hold a rod.", "SkyHanni: Fishing Bait Display").when(SKYBLOCK)
                .option(Option.bool("fishing_bait.icon", "Show the icon", "", true))
                .option(Option.bool("fishing_bait.warn_change", "Warn when the bait changes", "A title, a sound and a chat line.", true))
                .option(Option.bool("fishing_bait.warn_none", "Warn when casting without bait", "A title and a sound.", false)));
        FishState.ON_CAST.add(FishCards::noBait);
        HudLayout.add(BAIT_CARD);

        Features.add(new Feature(REEL, "Fishing", "Reel timer", "Hypixel's countdown over your bobber, on your screen, and how long the line has been in the water.", "SkyHanni: Fishing Hook Display, Fishing Bobber Timer").when(SKYBLOCK)
                .option(Option.bool("fishing_reel.title", "Title at the bite", "Reel now, in the middle of the screen.", true))
                .option(Option.bool("fishing_reel.hide_stand", "Hide the countdown over the bobber", "While the card shows it.", true))
                .option(Option.text("fishing_reel.alert_text", "Text at the bite", "& works as the colour sign.", "&c&l!!!"))
                .option(Option.number("fishing_reel.reel_in", "Say it this many seconds early", "With a line that can be reeled in early.", 0, 0, 5, 0.5))
                .option(Option.bool("fishing_reel.from_liquid", "Count from when the bobber touches water", "Otherwise from the cast.", true)));
        FishHooks.scan(REEL_SCAN);
        HudLayout.add(REEL_CARD);

        Features.add(new Feature(CAP, "Fishing", "Sea creature cap", "How many sea creatures are yours and around you against the caps, and the time until the first one despawns. Warns before you lose them.", "SkyHanni: Barn Fishing Timer").when(SKYBLOCK).tick(FishCards::capTick)
                .option(Option.bool("fishing_cap.time_alert", "Warn when time is nearly up", "", true))
                .option(Option.integer("fishing_cap.alert_seconds", "Warn after (seconds)", "They despawn after six minutes.", 300, 60, 360))
                .option(Option.bool("fishing_cap.warn_personal", "Warn at your cap of 10", "", true))
                .option(Option.bool("fishing_cap.warn_global", "Warn at the global cap of 60", "", true))
                .option(Option.bool("fishing_cap.crystal_hollows", "Worm fishing in the Crystal Hollows", "", true))
                .option(Option.bool("fishing_cap.crimson", "On the Crimson Isle", "", true))
                .option(Option.bool("fishing_cap.winter", "On the Winter Island", "", true))
                .option(Option.bool("fishing_cap.stranded", "On a Stranded island", "", false))
                .option(Option.bool("fishing_cap.anywhere", "Everywhere", "Otherwise the barn, the Crystal Hollows and the islands above.", false)));
        HudLayout.add(CAP_CARD);

        Features.add(new Feature(GOLDEN, "Fishing", "Golden Fish", "When the Golden Fish can come, how likely it is by now, and its hits and despawn once it is up.", "SkyHanni: Golden Fish Timer").when(SKYBLOCK).tick(FishCards::goldenTick).world(FishCards::goldenDraw).left(FishCards::goldenReset)
                .option(Option.bool("fishing_golden.nametag", "Name over the fish", "Hits and despawn time.", true))
                .option(Option.bool("fishing_golden.highlight", "Mark it when it is weak", "", true))
                .option(Option.bool("fishing_golden.throw_warning", "Warn to throw the rod", "Before the three minutes of the last throw end.", false))
                .option(Option.integer("fishing_golden.warn_seconds", "Warn this many seconds before", "", 20, 1, 60)));
        FishHooks.scan(GOLDEN_SCAN);
        HudLayout.add(GOLDEN_CARD);

        Features.add(new Feature(TOTEM, "Fishing", "Totem of Corruption", "Time left and owner of the Totem near you, and how far it reaches.", "SkyHanni: Totem of Corruption").when(SKYBLOCK).world(FishCards::totemDraw).left(() -> {
                    allTotems = List.of();
                    totems = List.of();
                })
                .option(Option.bool("fishing_totem.overlay", "Show the card", "", true))
                .option(Option.bool("fishing_totem.own_only", "Only your own Totem", "", true))
                .option(Option.integer("fishing_totem.distance", "Show within (blocks)", "", 16, 5, 100))
                .option(Option.integer("fishing_totem.warn_seconds", "Warn this many seconds before it ends", "0 for no warning.", 5, 0, 60))
                .option(Option.bool("fishing_totem.hide_particles", "Hide its particles", "", true))
                .option(Option.choice("fishing_totem.area", "Effective area", "", "Rings", "Rings", "Off"))
                .option(Option.color("fishing_totem.color", "Area colour", "", 0x60129F8E)));
        FishHooks.scan(TOTEM_SCAN);
        FishHooks.hideParticles(FishCards::totemParticle);
        HudLayout.add(TOTEM_CARD);

        Feature shark = Features.add(new Feature(SHARK, "Fishing", "Shark counter", "Sharks caught this Fishing Festival by kind, with a line in chat when it ends.", "SkyHanni: Shark Fish Counter").when(SKYBLOCK).defaultOff());
        FishState.ON_CATCH_OWN.add(l -> {
            if (!Features.on(SHARK) || !l.kind.name().contains("Shark")) return;
            for (int i = 0; i < 4; i++) if (l.kind.name().contains(SHARKS[i][0])) sharks[i] += FishState.doubleHook() ? 2 : 1;
        });
        ChatBus.register(ChatBus.OBSERVE + 7, SHARK, shark, line -> {
            if (!line.plain().trim().equals("FISHING FESTIVAL The festival has concluded! Time to dry off and repair your rods!")) return ChatBus.PASS;
            int total = sharks[0] + sharks[1] + sharks[2] + sharks[3];
            if (total > 0) FishHooks.say("§b[SBC] §fYou caught " + total + " sharks §7(§a" + sharks[0] + " §9" + sharks[1] + " §5" + sharks[2] + " §6" + sharks[3] + "§7) §fduring this Fishing Festival.");
            java.util.Arrays.fill(sharks, 0);
            return ChatBus.PASS;
        });
        HudLayout.add(SHARK_CARD);

        Features.add(new Feature(HEALTH, "Fishing", "Sea creature health", "The health of the sea creatures around you, yours in green.", "SkyHanni: Sea Creature Health Display").when(SKYBLOCK).defaultOff()
                .option(Option.integer("fishing_mob_health.limit", "How many", "", 5, 1, 15))
                .option(Option.bool("fishing_mob_health.only_rare", "Only rare ones", "", true)));
        HudLayout.add(HEALTH_CARD);

        Features.add(new Feature("fishing_kill_time", "Fishing", "Sea creature kill time", "How long a rare sea creature took to die, in chat.", "SkyHanni: Sea Creature Kill Time").when(SKYBLOCK).defaultOff()
                .option(Option.bool("fishing_kill_time.own_only", "Only your own", "", true)));
        FishState.ON_DEATH.add(l -> {
            if (!Features.on("fishing_kill_time") || !l.kind.rare() || (!l.own && opt("fishing_kill_time", "own_only").bool())) return;
            FishHooks.say("§b[SBC] §f" + l.kind.name() + " took §e" + SbTime.dur(System.currentTimeMillis() - l.spawned) + " §fto die.");
        });

        Features.add(new Feature(TROPHY, "Fishing", "Trophy fish", "Which trophy fish you caught at which tier, from Odger's menu and your catches, and what a fish fillets for.", "SkyHanni: Trophy Fish Display").when(SKYBLOCK).tooltip(FishCards::tooltip)
                .option(Option.choice("fishing_trophy.when", "Show the card", "", "With a lava rod", "With a lava rod", "Always", "With the key"))
                .option(Option.choice("fishing_trophy.missing", "Only fish missing", "", "Any tier", "Any tier", "Bronze", "Silver", "Gold", "Diamond"))
                .option(Option.bool("fishing_trophy.higher", "Show it if caught at a higher tier", "", false))
                .option(Option.choice("fishing_trophy.sort", "Sorted by", "", "Total", "Total", "Bronze", "Silver", "Gold", "Diamond", "Highest tier", "Name"))
                .option(Option.bool("fishing_trophy.reverse", "Reverse the order", "", false))
                .option(Option.bool("fishing_trophy.cross", "A cross for none", "", false))
                .option(Option.bool("fishing_trophy.check", "A check mark for caught", "Instead of the count.", false))
                .option(Option.bool("fishing_trophy.new", "Mark what you just caught", "", true))
                .option(Option.bool("fishing_trophy.armor", "Only with trophy fishing gear", "", false))
                .option(Option.bool("fishing_trophy.fillet", "Fillet in the tooltip", "", true))
                .option(Option.bool("fishing_trophy.total_tooltip", "Total in Odger's menu", "", true)));
        HudLayout.add(TROPHY_CARD);

        Feature tc = Features.add(new Feature(TROPHY_CHAT, "Fishing", "Trophy fish in chat", "Each catch says how many of that fish and tier you have, with all your counts on hover.", "SkyHanni: Trophy Counter").when(SKYBLOCK)
                .option(Option.choice("fishing_trophy_chat.design", "Line", "", "Short", "Short", "Plain", "Sentence"))
                .option(Option.bool("fishing_trophy_chat.total", "Say the total of that fish", "", false))
                .option(Option.bool("fishing_trophy_chat.hover", "Counts on hover", "", true))
                .option(Option.bool("fishing_trophy_chat.hide_repeated", "Only the last catch of each", "A new catch takes out the last line of the same fish and tier.", false))
                .option(Option.bool("fishing_trophy_chat.hide_bronze", "Hide repeated bronze", "Every catch after the first.", false))
                .option(Option.bool("fishing_trophy_chat.hide_silver", "Hide repeated silver", "Every catch after the first.", false))
                .option(Option.bool("fishing_trophy_chat.gold_alert", "Title on gold", "", true))
                .option(Option.bool("fishing_trophy_chat.diamond_alert", "Title on diamond", "", true))
                .option(Option.bool("fishing_trophy_chat.sound", "Sound with the title", "", true)));
        ChatBus.register(ChatBus.FILTER + 20, "fishing_trophy_chat", null, FishCards::trophyChat);
    }
}
