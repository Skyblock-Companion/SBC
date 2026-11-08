// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.areas;

import com.mojang.math.Transformation;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.dungeon.DungeonMarks;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;

// fishing in the world + chat. from skyhanni ShowFishingItemName, WormholeFinder, ThunderSparksHighlight,
// GeyserFishing, OdgerWaypoint, LootshareSphere, FlamingFlayHider, MuteBanshee, ChumBucketHider...
public final class FishWorld {
    private static final BooleanSupplier SKYBLOCK = () -> GameState.inSkyBlock || SbcMod.DEV;

    private FishWorld() {}

    private static boolean opt(String id, String key) {
        return Features.get(id).option(id + "." + key).bool();
    }

    // from skyhanni drawSphereWireframeInWorld as rings: WorldDraw has lines + boxes and rings show the same reach
    static void sphere(WorldDraw w, Vec3 c, double r, int argb) {
        if (c.distanceTo(w.eye) > r + 64) return;
        for (int lat = -2; lat <= 2; lat++) {
            double y = r * lat / 3.0, ring = Math.sqrt(Math.max(0, r * r - y * y));
            Vec3 last = null;
            for (int i = 0; i <= 32; i++) {
                double a = Math.PI * 2 * i / 32;
                Vec3 p = c.add(Math.cos(a) * ring, y, Math.sin(a) * ring);
                if (last != null) w.line(last, p, argb, 1.5f, false);
                last = p;
            }
        }
        for (int lon = 0; lon < 4; lon++) {
            double a = Math.PI * lon / 4;
            Vec3 last = null;
            for (int i = 0; i <= 32; i++) {
                double t = Math.PI * 2 * i / 32;
                Vec3 p = c.add(Math.cos(a) * Math.cos(t) * r, Math.sin(t) * r, Math.sin(a) * Math.cos(t) * r);
                if (last != null) w.line(last, p, argb, 1.5f, false);
                last = p;
            }
        }
    }

    // --- skyhanni ShowFishingItemName ---

    private static final String NAMES = "fishing_item_names";
    private static final Map<Integer, String> onGround = new HashMap<>();
    private static final Map<Integer, Long> groundAt = new HashMap<>();
    private static final Map<Integer, ItemEntity> groundEntity = new HashMap<>();

    private static boolean namesHere() {
        if ("THE_END".equals(GameState.island)) return false;
        if (!"HUB".equals(GameState.island)) return true;
        String area = GameState.area;
        return !(area.endsWith(" Atrium") || area.endsWith(" Museum") || area.equals("Fashion Shop") || area.equals("Shen's Auction"));
    }

    private static final FishHooks.Scan NAMES_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            return Features.on(NAMES) && FishState.holdingRod && namesHere();
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof ItemEntity item)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || item.distanceToSqr(mc.player) > 225) return;
            ItemStack stack = item.getItem();
            MenuView.Item it = MenuView.Item.of(stack);
            boolean bait = stack.getCount() == 1 && FishState.isBait(it);
            if (bait && !opt(NAMES, "baits")) return;
            String texture = DungeonMarks.headTexture(stack), text;
            if (!texture.isEmpty() && (texture.equals(DungeonMarks.skull("COINS_1")) || texture.equals(DungeonMarks.skull("COINS_2")))) text = "§6Coins";
            else {
                text = bait ? "§c§l- §r" : "§a§l+ §r";
                if (stack.getCount() != 1) text += "§7x" + stack.getCount() + " §r";
                text += bait ? "§7" + it.clean : GameState.formatLine(stack.getHoverName().getString());
            }
            onGround.put(item.getId(), text);
            groundAt.put(item.getId(), System.currentTimeMillis());
            groundEntity.put(item.getId(), item);
        }
    };

    private static void namesDraw(WorldDraw w) {
        if (onGround.isEmpty()) return;
        long now = System.currentTimeMillis();
        groundAt.entrySet().removeIf(e -> {
            if (now - e.getValue() <= 750) return false;
            onGround.remove(e.getKey());
            groundEntity.remove(e.getKey());
            return true;
        });
        for (var e : onGround.entrySet()) {
            ItemEntity item = groundEntity.get(e.getKey());
            if (item != null && !item.isRemoved()) w.label(e.getValue(), item.position().add(0, 0.8, 0), false);
        }
    }

    // --- skyhanni WormholeFinder ---

    private static final String WORMHOLE = "fishing_wormhole";
    private static final double DIRECTION_TOLERANCE = 0.98;
    private static List<Vec3> wormholes = List.of();
    private static long lastDeparture;

    private static boolean wormholeIsland() {
        return "LOTUS_ATOLL".equals(GameState.island) || "CRIMSON_ISLE".equals(GameState.island);
    }

    private static boolean froggles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String id = Gain.sbId(mc.player.getItemBySlot(EquipmentSlot.HEAD));
        return id.equals("FROGGLES_DIAMOND") || id.equals("FROGGLES_GOLD");
    }

    // from skyhanni DisplayEntityUtils.arrowForwardVec: the arrows up, turned by its rotation and laid flat
    private static Vec3 arrowForward(Display.TextDisplay arrow) {
        Display.RenderState state = arrow.renderState();
        if (state == null) return new Vec3(0, 0, 1);
        Transformation t = state.transformation().get(0f);
        Vector3f up = new Vector3f(0, 1, 0);
        t.leftRotation().transform(up);
        return new Vec3(up.x, 0, up.z).normalize();
    }

    private static final FishHooks.Scan WORMHOLE_SCAN = new FishHooks.Scan() {
        private final Set<Vec3> found = new HashSet<>();
        private int ticks;
        private boolean run;

        @Override
        public boolean wanted() {
            run = ++ticks % 3 == 0;
            return run && Features.on(WORMHOLE) && wormholeIsland() && froggles();
        }

        @Override
        public void begin() {
            found.clear();
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof Display.TextDisplay arrow)) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || arrow.distanceToSqr(mc.player) > 9) return;
            Graph g = Repo.graph(GameState.island);
            if (g == null) return;
            Vec3 forward = arrowForward(arrow), origin = arrow.position();
            Vec3 best = null;
            double bestScore = DIRECTION_TOLERANCE;
            for (Graph.Node n : g.nodes) {
                if (!n.tags().contains("fishing_wormhole")) continue;
                Vec3 flat = new Vec3(n.pos().x - origin.x, 0, n.pos().z - origin.z);
                if (flat.lengthSqr() == 0) continue;
                double score = forward.dot(flat.normalize());
                if (score >= bestScore) {
                    bestScore = score;
                    best = n.pos();
                }
            }
            if (best != null) found.add(best);
        }

        @Override
        public void end() {
            wormholes = new ArrayList<>(found);
        }
    };

    private static void wormholeDraw(WorldDraw w) {
        if (wormholes.isEmpty() || !wormholeIsland() || !froggles()) return;
        for (Vec3 p : wormholes) {
            // you cant see where it is otherwise, so its drawn in the world not through it
            w.waypointBox(p, 0xFFFF55FF, false, opt(WORMHOLE, "through_walls"), 0);
            w.dynamicText(p.add(0, 1, 0), "§dWormhole", 1.5, 0);
        }
    }

    private static boolean wormholeSound(SoundHooks.Sound s) {
        if (!Features.on(WORMHOLE) || !opt(WORMHOLE, "departure") || !wormholeIsland() || !froggles()) return false;
        if (!s.name().equals("entity.enderman.teleport") || s.pitch() != 0.6984127f) return false;
        long now = System.currentTimeMillis();
        if (now - lastDeparture >= 3000) {
            lastDeparture = now;
            FishHooks.title("§cWormhole closed!");
        }
        return false;
    }

    // --- skyhanni ThunderSparksHighlight ---

    private static final String SPARK = "fishing_thunder_spark";
    private static List<ArmorStand> sparks = List.of();

    private static final FishHooks.Scan SPARK_SCAN = new FishHooks.Scan() {
        private final List<ArmorStand> found = new ArrayList<>();

        @Override
        public boolean wanted() {
            return Features.on(SPARK) && ("CRIMSON_ISLE".equals(GameState.island) || FishState.stranded());
        }

        @Override
        public void begin() {
            found.clear();
        }

        @Override
        public void entity(Entity e) {
            if (e instanceof ArmorStand st && DungeonMarks.headTexture(st.getMainHandItem()).equals(DungeonMarks.skull("THUNDER_SPARK"))) found.add(st);
        }

        @Override
        public void end() {
            sparks = new ArrayList<>(found);
        }
    };

    private static void sparkDraw(WorldDraw w) {
        int color = 0xFF000000 | (Features.get(SPARK).option(SPARK + ".color").color() & 0xFFFFFF);
        for (ArmorStand st : sparks) {
            if (st.isRemoved()) continue;
            Vec3 at = st.position();
            boolean inLava = w.mc.level != null && w.mc.level.getBlockState(st.blockPosition()).is(net.minecraft.world.level.block.Blocks.LAVA);
            boolean through = at.distanceTo(w.eye) < 6 && inLava;
            w.filledBox(new AABB(at.x - 0.375, at.y, at.z - 0.375, at.x + 0.375, at.y + 0.75, at.z + 0.375), color & 0xFFFFFF, 0.5f, through);
            if (at.distanceTo(w.eye) < 10) w.label("Thunder Spark", at.add(0, 1.5, 0), through);
        }
    }

    // --- skyhanni GeyserFishing ---

    private static final String GEYSER = "fishing_geyser";
    private static Vec3 geyser;
    private static AABB geyserBox;

    private static boolean geyserParticle(ClientboundLevelParticlesPacket p) {
        if (!Features.on(GEYSER) || !"CRIMSON_ISLE".equals(GameState.island) || !"Blazing Volcano".equals(GameState.area)) return false;
        if (p.getParticle().getType() != ParticleTypes.CLOUD || p.getCount() != 15 || p.getMaxSpeed() != 0.05f) return false;
        if (p.getXDist() != 0.1f || p.getYDist() != 0.6f || p.getZDist() != 0.1f) return false;
        geyser = new Vec3(p.getX(), p.getY(), p.getZ());
        geyserBox = new AABB(p.getX() - 2, 118 - 0.1, p.getZ() - 2, p.getX() + 2, 118 - 0.09, p.getZ() + 2);
        Minecraft mc = Minecraft.getInstance();
        if (!opt(GEYSER, "hide_particles") || mc.player == null || mc.player.fishing == null) return false;
        return mc.player.fishing.position().distanceTo(geyser) < 3;
    }

    private static void geyserDraw(WorldDraw w) {
        if (geyserBox == null || !opt(GEYSER, "box") || !"CRIMSON_ISLE".equals(GameState.island)) return;
        double dx = geyser.x - w.eye.x, dz = geyser.z - w.eye.z;
        if (dx * dx + dz * dz > 96 * 96 || (opt(GEYSER, "only_rod") && !FishState.holdingLava)) return;
        int color = Features.get(GEYSER).option(GEYSER + ".color").color();
        w.filledBox(geyserBox, color & 0xFFFFFF, 0.4f, false);
    }

    // --- skyhanni OdgerWaypoint ---

    private static final String ODGER = "fishing_odger";
    private static final Vec3 ODGER_AT = new Vec3(-373, 207, -808);
    private static boolean fishInBag;
    private static int odgerTicks;

    private static void odgerTick(Minecraft mc) {
        if (++odgerTicks % 10 != 0 || mc.player == null) return;
        fishInBag = false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36 && !fishInBag; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) fishInBag = MenuView.Item.of(st).lastClean().endsWith(" TROPHY FISH");
        }
    }

    private static void odgerDraw(WorldDraw w) {
        if (fishInBag && !FishState.holdingLava) w.waypoint(ODGER_AT, 0xFFFFFFFF, "Odger", false, true);
    }

    // --- skyhanni SeaCreatureLootshareSphere ---

    private static void lootshareDraw(WorldDraw w) {
        Vec3 me = w.mc.player == null ? w.eye : w.mc.player.position();
        int drawn = 0;
        for (FishState.Live l : FishState.live()) {
            if (l.pos == null || (opt("fishing_lootshare", "only_rare") && !l.kind.rare())) continue;
            boolean mine = l.own || l.pos.distanceTo(me) <= 30;
            sphere(w, l.pos, 30, mine ? 0xFF55FF55 : 0xFFFFFFFF);
            if (++drawn >= 3) break;
        }
    }

    // --- skyhanni FlamingFlayHider ---

    private static boolean flayParticle(ClientboundLevelParticlesPacket p) {
        if (!Features.on("fishing_flay") || p.getParticle().getType() != ParticleTypes.DUST || p.getCount() != 0 || p.getMaxSpeed() != 1f) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double max = Features.get("fishing_flay").option("fishing_flay.distance").number();
        if (mc.player.position().distanceToSqr(p.getX(), p.getY(), p.getZ()) > max * max) return false;
        double x = p.getXDist(), y = p.getYDist(), z = p.getZDist();
        return (x == 1 && y == 1 && z == 0) || (x == 1 && y == 0 && z == 0)
                || (x == 0.23137255012989044 && y == 0.05098039284348488 && z == 0.0313725508749485)
                || (x == 0.003921568859368563 && y == 0.003921568859368563 && z == 0.003921568859368563);
    }

    // --- skyhanni MuteBanshee ---

    private static final float[] BANSHEE = {0.1904762f, 0.20634921f, 0.22222222f, 0.23809524f, 0.25396827f, 0.26984128f, 0.2857143f, 0.3015873f, 0.31746033f, 0.33333334f, 0.34920636f, 0.36507937f, 0.3809524f, 0.3968254f, 0.41269842f, 0.42857143f, 0.44444445f, 0.46031746f, 0.47619048f, 0.4920635f};

    private static boolean bansheeSound(SoundHooks.Sound s) {
        if (!Features.on("fishing_banshee") || !"BACKWATER_BAYOU".equals(GameState.island) || !s.name().equals("entity.ghast.ambient")) return false;
        for (float f : BANSHEE) if (f == s.pitch()) return true;
        return false;
    }

    // --- skyhanni ChumBucketHider ---

    private static final String CHUM = "fishing_chum";

    private static final FishHooks.Scan CHUM_SCAN = new FishHooks.Scan() {
        private final List<ArmorStand> titles = new ArrayList<>(), others = new ArrayList<>();

        @Override
        public boolean wanted() {
            return Features.on(CHUM);
        }

        @Override
        public void begin() {
            titles.clear();
            others.clear();
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof ArmorStand st)) return;
            String name = st.hasCustomName() ? GameState.strip(st.getCustomName().getString()) : "";
            if (name.endsWith("'s Chum Bucket") || name.endsWith("'s Chumcap Bucket")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && name.contains(mc.player.getName().getString()) && !opt(CHUM, "own")) return;
                titles.add(st);
                FishHooks.hide(st);
            } else if (name.contains("/10 Chums") || (opt(CHUM, "bucket") && wearsBucket(st))) others.add(st);
        }

        private boolean wearsBucket(ArmorStand st) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                String n = st.getItemBySlot(slot).getHoverName().getString();
                if (n.equals("Empty Chum Bucket") || n.equals("Empty Chumcap Bucket")) return true;
            }
            return false;
        }

        @Override
        public void end() {
            for (ArmorStand o : others)
                for (ArmorStand t : titles)
                    if (Math.abs(o.getX() - t.getX()) < 0.01 && Math.abs(o.getZ() - t.getZ()) < 0.01) {
                        FishHooks.hide(o);
                        break;
                    }
        }
    };

    // --- skyhanni SeaCreatureManager lines ---

    private static final String CHAT = "fishing_chat";
    private static boolean doubleLine;

    private static ChatBus.Result chat(ChatBus.Line line) {
        String plain = line.plain().trim();
        if (Patterns.get("SH.fishing.seacreature.doublehook.colorless").matcher(plain).matches()) {
            doubleLine = true;
            return opt(CHAT, "double") ? ChatBus.CANCEL : ChatBus.PASS;
        }
        FishState.Kind k = FishState.byMessage(plain);
        if (k == null) return ChatBus.PASS;
        boolean twice = doubleLine;
        doubleLine = false;
        boolean shortLine = opt(CHAT, "short"), mark = opt(CHAT, "double") && twice;
        if (!shortLine && !mark) return ChatBus.PASS;
        boolean left = mark && Features.get(CHAT).option(CHAT + ".position").choice().equals("Left");
        if (!shortLine) return new ChatBus.Replace(doubleHookMark(line.component(), left));
        String name = k.name(), an = "AEIOUaeiou".indexOf(name.charAt(0)) >= 0 ? "an" : "a";
        String body = "§9You caught " + an + " " + k.chatColor() + name + "§9!";
        return new ChatBus.Replace(Component.literal(mark ? (left ? "§e§lDOUBLE HOOK! §r" + body : body + " §e§lDOUBLE HOOK!") : body));
    }

    // mark added to hypixels own line, which stays as sent with its colors
    static Component doubleHookMark(Component line, boolean left) {
        return left ? Component.literal("§e§lDOUBLE HOOK! §r").append(line) : line.copy().append(Component.literal(" §e§lDOUBLE HOOK!"));
    }

    // --- skyhanni SeaCreatureCocoonWarning ---

    private static ChatBus.Result cocoon(ChatBus.Line line) {
        Matcher m = Patterns.get("SH.combat.cocoon.spawn").matcher(line.plain().trim());
        if (!m.matches()) return ChatBus.PASS;
        FishState.Kind k = FishState.kinds().get(m.group("name"));
        if (k == null || !k.rare()) return ChatBus.PASS;
        FishHooks.title("§c" + k.name() + " Has Been Cocooned");
        FishHooks.say("§c" + k.name() + " Has Been Cocooned");
        FishHooks.pling();
        return ChatBus.PASS;
    }

    // --- other players rods + carnival fish ---

    private static final String CATCH = "fishing_catch_a_fish";
    private static final AABB CATCH_AREA = new AABB(-87, 65, 25, -68, 85, 53);

    private static final FishHooks.Scan CATCH_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            Minecraft mc = Minecraft.getInstance();
            return Features.on(CATCH) && "HUB".equals(GameState.island) && mc.player != null && CATCH_AREA.contains(mc.player.position());
        }

        @Override
        public void entity(Entity e) {
            if (e instanceof ArmorStand st && DungeonMarks.headTexture(st.getItemBySlot(EquipmentSlot.HEAD)).equals(CARNIVAL_YELLOW_FISH)) FishHooks.glow(st, 0xFFFF00);
        }
    };
    // from skyblocker HeadTextures.CARNIVAL_YELLOW_FISH
    private static final String CARNIVAL_YELLOW_FISH = "ewogICJ0aW1lc3RhbXAiIDogMTcyMDA1MjU4MjAwMSwKICAicHJvZmlsZUlkIiA6ICIzM2Y4ZGExMTU1MzQ0YWQ5OWQ0Y2Q2ZjNhYjFjMjNhYSIsCiAgInByb2ZpbGVOYW1lIiA6ICJCXzFfUl9CIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzM2MGY0Zjk5Yzc4YWRkZGVhYjI3NmViZGY2YWI5YTBmY2ZmYWJkNDlmMGI1NDNlMTk4MWFjN2JkM2Q1NWIxOGEiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";

    // from skyblocker RenderFishMixin: a bobber thats not yours doesnt get drawn
    private static final FishHooks.Scan RODS_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            return Features.on("fishing_other_rods");
        }

        @Override
        public void entity(Entity e) {
            Minecraft mc = Minecraft.getInstance();
            if (e instanceof net.minecraft.world.entity.projectile.FishingHook hook && hook.getPlayerOwner() != mc.player) FishHooks.hide(hook);
        }
    };

    // rare sea creature highlight, outline like skyhannis, up to 30 blocks
    private static final FishHooks.Scan RARE_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            return dev.zmuzy.sbc.feature.Features.get("fishing_catches") != null && Features.on("fishing_catches") && opt("fishing_catches", "highlight");
        }

        @Override
        public void entity(Entity e) {
            FishState.Live l = FishState.byStand(e.getId());
            Minecraft mc = Minecraft.getInstance();
            if (l == null || !l.kind.rare() || mc.player == null || mc.level == null || e.distanceToSqr(mc.player) > 900) return;
            for (Entity below : mc.level.getEntities(e, e.getBoundingBox().inflate(0.5, 3, 0.5))) {
                if (below instanceof net.minecraft.world.entity.LivingEntity && !(below instanceof ArmorStand)) FishHooks.glow(below, 0x55FF55);
            }
        }
    };

    // --- skyhanni CoralFishHelper ---

    private static final AreaPanel CORAL = new AreaPanel("Coral fish", "Missing", () -> new ItemStack(Items.TROPICAL_FISH), Modern.GOLD, FishWorld::coralRows);

    private static List<AreaPanel.Row> coralRows(AbstractContainerScreen<?> screen) {
        List<Object[]> needed = new ArrayList<>();
        var slots = screen.getMenu().slots;
        int size = Math.max(0, slots.size() - 36);
        for (int i = 0; i < size; i++) {
            ItemStack st = slots.get(i).getItem();
            MenuView.Item it = MenuView.Item.of(st);
            if (!Patterns.get("SH.misc.coral.fish.name.colorless").matcher(it.clean).matches() || !st.is(Items.DYE.gray())) continue;
            double price = Gain.ofId(it.clean.toUpperCase(java.util.Locale.ROOT).replace(' ', '_'));
            needed.add(new Object[]{it.clean, price});
        }
        needed.sort((a, b) -> Double.compare((Double) a[1], (Double) b[1]));
        List<AreaPanel.Row> rows = new ArrayList<>();
        for (Object[] n : needed) {
            double p = (Double) n[1];
            rows.add(new AreaPanel.Row((String) n[0], p > 0 ? dev.zmuzy.sbc.core.Vitals.compact(p) : "no price", p > 0 ? Modern.GOLD : Modern.LABEL));
        }
        return rows;
    }

    private static SideHelper.Content coralPanel(Screen s) {
        if (!Features.on("fishing_coral") || !"GALATEA".equals(GameState.island) && !SbcMod.DEV) return null;
        return Patterns.get("SH.misc.coral.fish.inventory").matcher(GameState.strip(s.getTitle().getString())).matches() ? CORAL : null;
    }

    // --- skyhanni FishyTreatProfit ---

    private static final AreaPanel FISHY = new AreaPanel("Fishy Treat profit", "Items", () -> new ItemStack(Items.TROPICAL_FISH), Modern.GOLD, FishWorld::fishyRows);

    private static List<AreaPanel.Row> fishyRows(AbstractContainerScreen<?> screen) {
        List<Object[]> out = new ArrayList<>();
        var slots = screen.getMenu().slots;
        for (int i = 0; i < Math.min(45, slots.size() - 36); i++) {
            MenuView.Item it = MenuView.Item.of(slots.get(i).getItem());
            if (it.empty() || !dev.zmuzy.sbc.item.market.LoreCosts.hasTradeLine(it.lore)) continue;
            long treats = 0;
            double other = 0;
            for (var c : dev.zmuzy.sbc.item.market.LoreCosts.read(it.lore)) {
                if (c.id().equals("FISHY_TREAT")) treats = c.amount();
                else other += Gain.ofId(c.id()) * c.amount();
            }
            double price = Gain.ofId(it.id) * Math.max(1, slots.get(i).getItem().getCount());
            if (treats <= 0 || price <= 0) continue;
            out.add(new Object[]{it.clean, (price - other) / treats});
        }
        out.sort((a, b) -> Double.compare((Double) b[1], (Double) a[1]));
        List<AreaPanel.Row> rows = new ArrayList<>();
        for (Object[] o : out) {
            double p = (Double) o[1];
            rows.add(new AreaPanel.Row((String) o[0], dev.zmuzy.sbc.core.Vitals.compact(p), p > 0 ? Modern.GOLD : Modern.BAD));
        }
        return rows;
    }

    private static SideHelper.Content fishyPanel(Screen s) {
        if (!Features.on("fishy_treat_profit")) return null;
        return Patterns.get("SH.event.year-of-the-seal.fishy-treat.inventory").matcher(GameState.strip(s.getTitle().getString())).matches() ? FISHY : null;
    }

    // --- register ---

    public static void register() {
        Features.add(new Feature(NAMES, "Fishing", "Caught item name", "The name and count of an item floating near you while you hold a rod.", "SkyHanni: Fished Item Name").when(SKYBLOCK).world(FishWorld::namesDraw)
                .option(Option.bool("fishing_item_names.baits", "Also for bait", "", false)));
        FishHooks.scan(NAMES_SCAN);

        Features.add(new Feature(WORMHOLE, "Fishing", "Wormhole finder", "Wearing Froggles, the arrows point at a wormhole: its place from the island map is marked, and you are told when it closes.", "SkyHanni: Wormhole Finder").when(SKYBLOCK).world(FishWorld::wormholeDraw).left(() -> wormholes = List.of())
                .option(Option.bool("fishing_wormhole.departure", "Say when it closes", "", true))
                .option(Option.bool("fishing_wormhole.through_walls", "Mark it through walls", "", false)));
        FishHooks.scan(WORMHOLE_SCAN);
        SoundHooks.listen(FishWorld::wormholeSound);

        Features.add(new Feature(SPARK, "Fishing", "Thunder Spark highlight", "Marks Thunder Sparks, seen through lava when you are close.", "SkyHanni: Thunder Spark Highlight").when(SKYBLOCK).defaultOff().world(FishWorld::sparkDraw).left(() -> sparks = List.of())
                .option(Option.color("fishing_thunder_spark.color", "Colour", "", 0xFFFFFFFF)));
        FishHooks.scan(SPARK_SCAN);

        Features.add(new Feature(GEYSER, "Fishing", "Geyser", "A box on the lava where the Blazing Volcano's geyser is, and its particles out of the way of your bobber.", "SkyHanni: Geyser Fishing").when(SKYBLOCK).world(FishWorld::geyserDraw).left(() -> {
                    geyser = null;
                    geyserBox = null;
                })
                .option(Option.bool("fishing_geyser.box", "Draw the box", "", true))
                .option(Option.bool("fishing_geyser.only_rod", "Only with a lava rod", "", true))
                .option(Option.bool("fishing_geyser.hide_particles", "Hide the particles at your bobber", "", true))
                .option(Option.color("fishing_geyser.color", "Colour", "", 0x60FFAA00)));
        FishHooks.hideParticles(FishWorld::geyserParticle);

        Features.add(new Feature(ODGER, "Fishing", "Odger waypoint", "Where Odger stands, while you carry trophy fish and do not hold a lava rod.", "SkyHanni: Odger Waypoint").when(() -> "CRIMSON_ISLE".equals(GameState.island)).tick(FishWorld::odgerTick).world(FishWorld::odgerDraw));

        Features.add(new Feature("fishing_lootshare", "Fishing", "Lootshare range", "A sphere of 30 blocks around sea creatures: green where you share the loot.", "SkyHanni: Lootshare Range").when(SKYBLOCK).defaultOff().world(FishWorld::lootshareDraw)
                .option(Option.bool("fishing_lootshare.only_rare", "Only rare ones", "", true)));

        Features.add(new Feature("fishing_flay", "Fishing", "Hide Flay and Soul Whip particles", "Their particles close to you stay out of your view.", "SkyHanni: Flay/Whip Hider").when(SKYBLOCK).defaultOff()
                .option(Option.number("fishing_flay.distance", "Up to (blocks)", "", 2, 1, 16, 0.5)));
        FishHooks.hideParticles(FishWorld::flayParticle);

        Features.add(new Feature("fishing_banshee", "Fishing", "Mute Banshees", "The Banshee's wail in the Backwater Bayou is silent.", "SkyHanni: Mute Banshee").when(SKYBLOCK).defaultOff());
        SoundHooks.listen(FishWorld::bansheeSound);

        Features.add(new Feature(CHUM, "Fishing", "Hide Chum Buckets", "The name and count over Chum and Chumcap Buckets are not drawn.", "SkyHanni: Chum Bucket Hider").when(SKYBLOCK).defaultOff()
                .option(Option.bool("fishing_chum.bucket", "Hide the bucket too", "", false))
                .option(Option.bool("fishing_chum.own", "Hide your own too", "", false)));
        FishHooks.scan(CHUM_SCAN);

        Feature chat = Features.add(new Feature(CHAT, "Fishing", "Sea creature lines", "A Double Hook goes on the line of what you caught, instead of a line of its own.", "SkyHanni: Compact Double Hook").when(SKYBLOCK)
                .option(Option.bool("fishing_chat.double", "Join the Double Hook line", "", true))
                .option(Option.choice("fishing_chat.position", "Double Hook goes", "", "Left", "Left", "Right"))
                .option(Option.bool("fishing_chat.short", "Short catch lines", "You caught a Sea Walker!", false)));
        ChatBus.register(ChatBus.FILTER + 60, CHAT, chat, FishWorld::chat);

        Feature cocoon = Features.add(new Feature("fishing_cocoon", "Fishing", "Cocoon warning", "A title and a sound when a rare sea creature of yours gets cocooned.", "SkyHanni: Sea Creature Cocoon Warning").when(SKYBLOCK).defaultOff());
        ChatBus.register(ChatBus.OBSERVE + 7, "fishing_cocoon", cocoon, FishWorld::cocoon);

        Features.add(new Feature(CATCH, "Events", "Catch a Fish", "Outlines the yellow fish at the Carnival's Catch a Fish.", "Skyblocker: Catch a Fish Helper").when(SKYBLOCK).defaultOff());
        FishHooks.scan(CATCH_SCAN);

        Features.add(new Feature("fishing_other_rods", "Fishing", "Hide other players' rods", "Bobbers of other players are not drawn.", "Skyblocker: Hide Other Players Rods").when(SKYBLOCK).defaultOff());
        FishHooks.scan(RODS_SCAN);
        FishHooks.scan(RARE_SCAN);

        Features.add(new Feature("fishing_coral", "Fishing", "Coral fish helper", "Beside the Fish Family menu, the fish you have not shown yet, cheapest first.", "SkyHanni: Coral Fish Helper").when(SKYBLOCK));
        SideHelper.add(FishWorld::coralPanel);

        Features.add(new Feature("fishy_treat_profit", "Events", "Fishy Treat profit", "Beside Lukas the Aquarist, what each item is worth per Fishy Treat.", "SkyHanni: Fishy Treat Profit").when(SKYBLOCK));
        SideHelper.add(FishWorld::fishyPanel);
    }
}
