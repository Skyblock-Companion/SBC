// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni and Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.areas;

//? if >=26.3 {
import com.mojang.blaze3d.platform.InputConstants;
//?}
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.dungeon.DungeonMarks;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.mc.ParticleHooks;
import io.muzy.sbc.nav.Graph;
import io.muzy.sbc.nav.Navigator;
import io.muzy.sbc.render.WorldDraw;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.theme.Modern;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// hunting. skyblockers galatea, torrhus + safari glow adders as boxes that stay behind walls with skyhannis
// distance limits, InvisibugHighlighter with skyblockers particle check, FloorDrops...
public final class HuntHelpers {
    private static final BooleanSupplier SKYBLOCK = () -> GameState.inSkyBlock || SbcMod.DEV;

    private HuntHelpers() {}

    private static boolean opt(String id, String key) {
        return Features.get(id).option(id + "." + key).bool();
    }

    private static boolean galatea() {
        return "GALATEA".equals(GameState.island);
    }

    private static boolean torrhus() {
        return "TORRHUS_CANYON".equals(GameState.island);
    }

    private static boolean safari() {
        return "SAFARI".equals(GameState.island);
    }

    private static boolean biome(String name) {
        return safari() && GameState.area.contains(name);
    }

    // --- highlights ---

    private static final String HIGH = "hunting_highlights", SAFARI = "hunting_safari";

    private record Mark(Entity e, String name) {}

    private static List<Mark> marks = List.of();
    private static final String DUPLICO = "Duplico";
    // from skyblocker HeadTextures.ROCKMITE_MOUND
    private static final String ROCKMITE_MOUND = "ewogICJ0aW1lc3RhbXAiIDogMTc4MjgzMjk3Nzg1MSwKICAicHJvZmlsZUlkIiA6ICI3MDYwMDk0OTgyZDc0MTczYTNjZjg1Zjc1NjQ3MGE5YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJpbmV4YWN0b3N0ZW50YXQiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWRiYWFiNzRkMWFjZDBhYmU5ZDA0YWJlOTkyODcyNWRlNWQ0NDk1ZmNiNjNiNjQ3MjI4Y2FmNjk0NGMyMDgwMCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";

    private static String markOf(Entity e) {
        if (galatea() && Features.on(HIGH)) {
            if (e instanceof Shulker s && s.getVariant().orElse(null) == DyeColor.GREEN && opt(HIGH, "hideonleaf")) return "Hideonleaf";
            if (e instanceof Turtle && opt(HIGH, "shellwise")) return "Shellwise";
            if (e instanceof Axolotl && opt(HIGH, "coralot")) return "Coralot";
        }
        if (torrhus() && Features.on(HIGH)) {
            if (e instanceof Shulker s && opt(HIGH, "hideonsun")) {
                DyeColor c = s.getVariant().orElse(null);
                if (c == DyeColor.YELLOW || c == DyeColor.ORANGE) return "Hideonsun";
            }
            if (e instanceof Parrot p && p.getVariant() == Parrot.Variant.BLUE && opt(HIGH, "blue_jay")) return "Blue Jay";
        }
        if (safari() && Features.on(SAFARI)) {
            if (biome("Cavern") && e instanceof Display.ItemDisplay d && opt(SAFARI, "rockmite") && d.itemRenderState() != null && DungeonMarks.headTexture(d.itemRenderState().itemStack()).equals(ROCKMITE_MOUND)) return "Rockmite Mound";
            if (biome("Forest") && opt(SAFARI, "hideonfloor")) {
                if (e instanceof Shulker s && s.getVariant().orElse(null) == DyeColor.GREEN) return "Hideonfloor";
                if (e instanceof Display.ItemDisplay d && d.itemRenderState() != null && d.itemRenderState().itemStack().is(Items.DYED_SHULKER_BOX.green())) return "Hideonfloor";
            }
            if (biome("Haunted") && e instanceof Bat && opt(SAFARI, "bloodbat")) return "Bloodbat";
            // from skyblocker SafariGlowAdder.isNotDuplico: something that moves like one but is a gimmiegold or a moving
            // hideonwall doesnt count
            if (biome("Haunted") && e instanceof Display.ItemDisplay d && opt(SAFARI, "duplico") && d.getPosRotInterpolationDuration() == 3 && d.itemRenderState() != null
                    && !d.itemRenderState().itemStack().is(Items.PLAYER_HEAD) && !d.itemRenderState().itemStack().is(Items.DYED_SHULKER_BOX.purple())) return DUPLICO;
        }
        return null;
    }

    // from skyblocker HudMixin.hideSafariColdOverlay
    public static boolean hidesCold() {
        return safari() && biome("Icy") && Features.on(SAFARI) && opt(SAFARI, "hide_cold");
    }

    // skyhanni mob by name label: the living thing under a stand saying Birries, or Sparkling for skyblockers critters
    private static final FishHooks.Scan HIGH_SCAN = new FishHooks.Scan() {
        private final List<Mark> found = new ArrayList<>();

        @Override
        public boolean wanted() {
            return (Features.on(HIGH) && (galatea() || torrhus())) || (Features.on(SAFARI) && safari());
        }

        @Override
        public void begin() {
            found.clear();
        }

        @Override
        public void entity(Entity e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || e == mc.player) return;
            if (e instanceof ArmorStand st && st.hasCustomName()) {
                String n = GameState.strip(st.getCustomName().getString());
                boolean birries = galatea() && Features.on(HIGH) && opt(HIGH, "birries") && n.contains("Birries");
                boolean sparkling = safari() && Features.on(SAFARI) && opt(SAFARI, "sparkling") && n.contains("SPARKLING");
                if (!birries && !sparkling) return;
                for (Entity b : mc.level.getEntities(st, st.getBoundingBox().inflate(0.5, 3, 0.5))) {
                    if (b instanceof LivingEntity && !(b instanceof ArmorStand) && !(b instanceof Player)) {
                        found.add(new Mark(b, birries ? "Birries" : "Sparkling"));
                        break;
                    }
                }
                return;
            }
            String name = markOf(e);
            if (name != null) found.add(new Mark(e, name));
        }

        @Override
        public void end() {
            marks = new ArrayList<>(found);
        }
    };

    private static void highDraw(WorldDraw w) {
        if (marks.isEmpty()) return;
        int color = Features.get(HIGH).option(HIGH + ".color").color() | 0xFF000000;
        for (Mark m : marks) {
            if (m.e().isRemoved()) continue;
            double d = m.e().position().distanceTo(w.eye);
            double limit = m.name().equals("Birries") ? 10 : 20;
            if (d > limit) continue;
            if (m.name().equals(DUPLICO)) {
                w.box(AABB.ofSize(m.e().position(), 1, 1, 1), Features.get(SAFARI).option(SAFARI + ".duplico_color").color() | 0xFF000000, false, false);
                w.label("§f" + m.name() + " §7" + (int) d + "m", m.e().position().add(0, 0.9, 0), false);
                continue;
            }
            w.box(m.e().getBoundingBox().inflate(0.05), color, false, false);
            w.label("§f" + m.name() + " §7" + (int) d + "m", m.e().position().add(0, m.e().getBbHeight() + 0.4, 0), false);
        }
    }

    // --- invisibug ---

    private static final Map<Vec3, Long> invisibugs = new HashMap<>();

    private static boolean nearPet(Minecraft mc, Vec3 pos) {
        ArmorStand near = null;
        double best = Double.MAX_VALUE;
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, AABB.ofSize(pos, 10, 10, 10))) {
            double d = a.distanceToSqr(pos);
            if (d < best) {
                best = d;
                near = a;
            }
        }
        return near != null && (!near.getMainHandItem().isEmpty() || near.getCustomName() != null);
    }

    private static void particle(ParticleHooks.Particle p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (Features.on(HIGH) && galatea() && opt(HIGH, "invisibug") && p.is("minecraft:crit")) {
            Vec3 pos = new Vec3(p.x(), p.y(), p.z());
            if (mc.level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(pos, 1, 1, 1)).isEmpty() && !nearPet(mc, pos)) {
                invisibugs.keySet().removeIf(v -> v.distanceToSqr(pos) <= 1);
                invisibugs.put(pos, System.currentTimeMillis());
            }
        }
        if (Features.on(FLOOR) && (galatea() || torrhus() || safari()) && p.is("minecraft:happy_villager")) {
            BlockPos at = BlockPos.containing(p.x(), p.y() - 1, p.z());
            if (strings(mc, at) == 3) floorDrops.computeIfAbsent(at, k -> new long[1])[0] = System.currentTimeMillis();
        }
    }

    private static void invisibugDraw(WorldDraw w) {
        if (invisibugs.isEmpty()) return;
        long now = System.currentTimeMillis();
        invisibugs.values().removeIf(t -> now - t > 500);
        int color = Features.get(HIGH).option(HIGH + ".color").color();
        for (Vec3 p : invisibugs.keySet()) w.filledBox(AABB.ofSize(p, 0.5, 0.5, 0.5), color & 0xFFFFFF, 0.3f, false);
    }

    // --- skyhanni FloorDrops ---

    private static final String FLOOR = "hunting_floor_drops";
    private static final Map<BlockPos, long[]> floorDrops = new HashMap<>();
    private static int floorTicks;

    private static int strings(Minecraft mc, BlockPos pos) {
        int n = 0;
        for (Display.ItemDisplay d : mc.level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pos))) {
            if (d.itemRenderState() != null && d.itemRenderState().itemStack().is(Items.STRING)) n++;
        }
        return n;
    }

    private static void floorTick(Minecraft mc) {
        if (++floorTicks % 20 != 0 || mc.level == null) return;
        long now = System.currentTimeMillis();
        floorDrops.entrySet().removeIf(e -> {
            if (now - e.getValue()[0] >= 2000 && strings(mc, e.getKey()) == 3) e.getValue()[0] = now;
            return now - e.getValue()[0] > 5000;
        });
    }

    private static void floorDraw(WorldDraw w) {
        int color = Features.get(FLOOR).option(FLOOR + ".color").color();
        for (BlockPos p : floorDrops.keySet()) w.filledBox(new AABB(p), color & 0xFFFFFF, 0.5f, false);
    }

    // --- lasso ---

    private static final String LASSO = "hunting_lasso";
    private static final String COUNT_DOWN = "                    ";
    private static int lassoPercent = -1;
    private static boolean lassoReel, lassoShown;

    private static int reelValue(String id) {
        return switch (id) {
            case "ABYSMAL_LASSO" -> 2;
            case "VINERIP_LASSO", "ENTANGLER_LASSO" -> 3;
            case "EVERSTRETCH_LASSO" -> 4;
            default -> 0;
        };
    }

    private static final FishHooks.Scan LASSO_SCAN = new FishHooks.Scan() {
        private boolean reel, tied;
        private int percent;
        private String held = "";

        @Override
        public boolean wanted() {
            return Features.on(LASSO);
        }

        @Override
        public void begin() {
            reel = tied = false;
            percent = -1;
            Minecraft mc = Minecraft.getInstance();
            held = mc.player == null ? "" : Gain.sbId(mc.player.getMainHandItem());
        }

        @Override
        public void entity(Entity e) {
            Minecraft mc = Minecraft.getInstance();
            if (!(e instanceof Leashable l) || l.getLeashHolder() != mc.player || !MenuView.Item.of(mc.player.getMainHandItem()).lastClean().endsWith(" LASSO")) return;
            tied = true;
            int r = reelValue(held);
            for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, e.getBoundingBox().inflate(3))) {
                Component name = st.getCustomName();
                if (name == null) continue;
                if (GameState.strip(name.getString()).trim().equals("REEL")) reel = true;
                if (name.getString().equals(COUNT_DOWN) && name.getSiblings().size() == 2) percent = Math.max(0, (int) ((name.getSiblings().get(0).getString().length() - r) / (20f - r) * 100));
            }
        }

        @Override
        public void end() {
            lassoShown = tied;
            lassoReel = reel;
            lassoPercent = percent;
        }
    };

    private static RichCard.Content lassoContent(boolean reel, int percent) {
        if (reel || percent == 0) return new RichCard.Content("", List.of(RichCard.Line.row("Lasso", "§e§lREEL").raw().meter(1, Modern.GOLD)));
        return new RichCard.Content("", List.of(RichCard.Line.row("Lasso", percent < 0 ? "§7Waiting" : "§f" + percent + "%").raw().meter(Math.max(0, percent) / 100.0, 0xFF4FC3F7)));
    }

    private static final RichCard LASSO_CARD = new RichCard(LASSO, "Lasso", "Hunting", Zone.TOP_LEFT, 38, LASSO) {
        @Override
        public boolean visible() {
            return lassoShown || SbcMod.DEV;
        }

        @Override
        public String heading() {
            return "";
        }

        @Override
        protected Content live() {
            return lassoContent(lassoReel, lassoPercent);
        }

        @Override
        protected Content sample() {
            return lassoContent(false, 62);
        }

        @Override
        public List<Content> states() {
            return List.of(lassoContent(true, -1), lassoContent(false, -1));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Lasso", "62%", 0));
        }
    };

    // --- skyhanni FusionDisplay + FusionKeybinds ---

    private static final String FUSION = "hunting_fusion_panel";
    public static KeyMapping repeatKey, confirmKey, shulkerKey, huntingChecklistKey;
    private static String lastFused = "";
    private static int pureReptiles;
    private static boolean repeatWas, confirmWas, shulkerWas;

    private static boolean fusionMenu(String t) {
        return Patterns.get("SH.inventory.attributeshards.fusion-box").matcher(t).matches() || Patterns.get("SH.inventory.attributeshards.confirm-fusion").matcher(t).matches() || Patterns.get("SH.inventory.attributeshards.shard-fusion").matcher(t).matches();
    }

    // reads every fusion for the panel + pure reptiles, the panel switch only says if it shows
    private static ChatBus.Result fusionChat(ChatBus.Line line) {
        String t = line.plain().trim();
        if (t.equals("PURE REPTILE")) {
            pureReptiles++;
            return ChatBus.PASS;
        }
        Matcher m = Patterns.get("SH.inventory.attributeshards.fusion.shard.colorless").matcher(t);
        if (m.matches() && !m.group("shardName").equals(lastFused)) {
            lastFused = m.group("shardName");
            pureReptiles = 0;
        }
        return ChatBus.PASS;
    }

    private static List<AreaPanel.Row> fusionRows(AbstractContainerScreen<?> s) {
        List<AreaPanel.Row> rows = new ArrayList<>();
        if (!lastFused.isEmpty()) rows.add(new AreaPanel.Row(lastFused, String.valueOf(io.muzy.sbc.item.menus.AttributeShards.inBox(lastFused)), Modern.GOOD));
        if (pureReptiles > 0) rows.add(new AreaPanel.Row("Pure Reptile", String.valueOf(pureReptiles), Modern.GOLD));
        return rows;
    }

    private static final AreaPanel FUSION_PANEL = new AreaPanel("Fusion", "Last", () -> new ItemStack(Items.PRISMARINE_SHARD), Modern.GOOD, HuntHelpers::fusionRows);

    private static SideHelper.Content fusionPanel(Screen s) {
        return Features.on(FUSION) && fusionMenu(GameState.strip(s.getTitle().getString())) ? FUSION_PANEL : null;
    }

    // from skyhanni FusionKeybinds: only on a key the player bound, both on one key does nothing. middle click
    // like the menu wants
    private static void keysTick(Minecraft mc) {
        Screen s = mc.gui.screen();
        boolean repeat = FishHooks.held(repeatKey), confirm = FishHooks.held(confirmKey), shulker = FishHooks.held(shulkerKey);
        if (s != null && mc.gameMode != null && mc.player != null) {
            String t = GameState.strip(s.getTitle().getString());
            int slot = -1;
            if (repeat && !repeatWas && !confirm && Patterns.get("SH.inventory.attributeshards.fusion-box").matcher(t).matches()) slot = 47;
            if (confirm && !confirmWas && !repeat && Patterns.get("SH.inventory.attributeshards.confirm-fusion").matcher(t).matches()) slot = 33;
            if (slot >= 0) mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, 2, ContainerInput.CLONE, mc.player);
        }
        if (s == null && shulker && !shulkerWas) nextShulker(mc);
        repeatWas = repeat;
        confirmWas = confirm;
        shulkerWas = shulker;
        shulkerStep(mc);
    }

    // --- skyhanni ShulkerFinder ---

    private static final String SHULKER = "hunting_shulker";
    private static List<Vec3> route;
    private static int routeAt;
    private static Vec3 goal;

    private static List<Vec3> route(Minecraft mc, String tag) {
        List<Vec3> left = new ArrayList<>(), out = new ArrayList<>();
        Graph g = Repo.graph(GameState.island);
        if (g != null) for (Graph.Node n : g.nodes) if (n.tags().contains(tag)) left.add(n.pos());
        Vec3 at = mc.player.position();
        while (!left.isEmpty()) {
            Vec3 best = left.get(0);
            for (Vec3 v : left) if (v.distanceToSqr(at) < best.distanceToSqr(at)) best = v;
            left.remove(best);
            out.add(best);
            at = best;
        }
        return out;
    }

    private static void nextShulker(Minecraft mc) {
        if (!Features.on(SHULKER) || mc.player == null || goal != null) return;
        String tag = galatea() ? "hideonleaf" : torrhus() ? "hideonsun" : null;
        if (tag == null) return;
        if (route == null) route = route(mc, tag);
        if (route.isEmpty()) return;
        goal = route.get(routeAt++ % route.size());
        Navigator.navigate(new Navigator.Target(GameState.island, galatea() ? "Hideonleaf" : "Hideonsun", goal));
    }

    // on arrival: a shulker there ends it, empty spot = go to the next
    private static void shulkerStep(Minecraft mc) {
        if (goal == null || mc.player == null || mc.level == null || goal.distanceToSqr(mc.player.position()) > 16) return;
        boolean there = !mc.level.getEntitiesOfClass(Shulker.class, AABB.ofSize(goal, 6, 6, 6)).isEmpty();
        goal = null;
        if (!there) nextShulker(mc);
    }

    // --- skyhanni HuntingBoxHelper ---

    private static final Pattern SYPHON = Pattern.compile("Syphon (\\d+) more to level up!");

    private static void boxMarks(MenuView v) {
        Pattern owned = Patterns.get("SH.inventory.attributeshards.owned.colorless");
        for (int slot = 9; slot < Math.min(v.top, 45); slot++) {
            if (slot % 9 == 0 || slot % 9 == 8) continue;
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            int have = -1, need = -1;
            for (String l : it.cleanLore) {
                if (have < 0) {
                    Matcher m = owned.matcher(l);
                    if (m.matches()) have = Integer.parseInt(m.group("amount").replace(",", ""));
                } else {
                    Matcher m = SYPHON.matcher(l);
                    if (m.matches()) need = Integer.parseInt(m.group(1));
                    break;
                }
            }
            if (have < 0 || need < 0 || have < need) continue;
            v.back(slot, it.lastClean().startsWith("Requires") ? MenuView.YELLOW : MenuView.GREEN);
        }
    }

    // --- chat ---

    private static final Pattern COMMENTS = Pattern.compile("^(?:Mochibear ate too much and passed out! You caught it!|You caught yourself an invisibug! The shard was sent to your Hunting Box!|The Frog is exhausted\\.\\.\\.)$"),
            SWOOP = Pattern.compile("^\\[NPC] Swoop: Wow! I forgot to tell you, monsters around here can only take damage from Axes!$");

    // from skyblocker CritterCaptureFilter
    private static final Pattern CRITTERS = Pattern.compile("^(CAPTURE|LOOT SHARE)! .*$");

    private static ChatBus.Result chat(ChatBus.Line line) {
        String t = line.plain().trim();
        if (opt("hunting_chat", "comments") && galatea() && COMMENTS.matcher(t).matches()) return ChatBus.CANCEL;
        if (opt("hunting_chat", "swoop") && SWOOP.matcher(t).matches()) return ChatBus.CANCEL;
        if (opt("hunting_chat", "critters") && safari() && CRITTERS.matcher(t).matches()) return ChatBus.CANCEL;
        return ChatBus.PASS;
    }

    // --- safari ---

    private static final Map<String, String[]> SAFARI_SHARDS = new LinkedHashMap<>();

    static {
        SAFARI_SHARDS.put("Cavern", new String[]{"Cavernfish", "Flitter", "Shyworm", "Driftling", "Chuckwalla", "Rockmite", "Scrappy", "Snoozle", "Gemzie"});
        SAFARI_SHARDS.put("Forest", new String[]{"Foxtrot", "Honeybug", "Hideonfloor", "Treefrog", "Woodchucker", "Fluffling", "Bluebird", "Parakeet", "Macaw"});
        SAFARI_SHARDS.put("Haunted", new String[]{"Solsnatcher", "Bloodbat", "Litterbug", "Areita", "Duplico", "Gazer", "Hideonwall", "Gimmiegold", "Hideyho", "Doomspiral"});
        SAFARI_SHARDS.put("Icy", new String[]{"Strongarm", "Polaris", "Billygoat", "Tepid", "Nozzlenose", "Mantis Shrimp", "Shuddersquid", "Troodon", "Wumpa"});
    }

    private static final Map<String, Integer> safariCaught = new HashMap<>();
    private static final Pattern CAUGHT = Patterns.get("SH.inventory.attributeshards.caught.shards.colorless"), CAPTURED = Patterns.get("SH.inventory.attributeshards.captured.shard");

    private static ChatBus.Result safariChat(ChatBus.Line line) {
        if (!safari()) return ChatBus.PASS;
        String t = line.plain().trim();
        Matcher m = CAUGHT.matcher(t);
        if (!m.matches()) m = CAPTURED.matcher(t);
        else {
            safariCaught.merge(m.group("shardName"), m.group("amount") == null ? 1 : Integer.parseInt(m.group("amount")), Integer::sum);
            return ChatBus.PASS;
        }
        if (m.matches()) safariCaught.merge(m.group("shardName"), 1, Integer::sum);
        return ChatBus.PASS;
    }

    private static RichCard.Content safariContent(Map<String, Integer> caught, String here, String mode, boolean hide) {
        List<RichCard.Line> out = new ArrayList<>();
        List<String> order = new ArrayList<>(SAFARI_SHARDS.keySet());
        if (mode.equals("This one on top") && here != null) {
            order.remove(here);
            order.add(0, here);
        }
        for (String biome : order) {
            String[] shards = SAFARI_SHARDS.get(biome);
            int got = 0;
            for (String s : shards) if (caught.getOrDefault(s, 0) > 0) got++;
            out.add(RichCard.Line.section(biome, (got == shards.length ? "§a" : "§c") + got + "/" + shards.length));
            if (mode.equals("Only this one") && !biome.equals(here)) continue;
            for (String s : shards) {
                boolean has = caught.getOrDefault(s, 0) > 0;
                if (hide && has) continue;
                out.add(RichCard.Line.row(s, has ? "§a✔" : "§c✖").key(biome).raw());
            }
        }
        return new RichCard.Content("", out);
    }

    private static final RichCard SAFARI_CARD = new RichCard("safari_checklist", "Safari shards", "Hunting", Zone.TOP_RIGHT, 30, "safari_checklist") {
        private String here() {
            for (String b : SAFARI_SHARDS.keySet()) if (GameState.area.contains(b)) return b;
            return null;
        }

        @Override
        public boolean visible() {
            return safari() || SbcMod.DEV;
        }

        @Override
        protected Content live() {
            return safariContent(safariCaught, here(), Features.get("safari_checklist").option("safari_checklist.mode").choice(), opt("safari_checklist", "hide_caught"));
        }

        @Override
        protected Content sample() {
            return safariContent(Map.of("Flitter", 1, "Rockmite", 2, "Macaw", 1), "Cavern", "Only this one", true);
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Cavern", "2/9", 0));
        }
    };

    private static final Map<Vec3, String> CENTER = Map.of(new Vec3(-27.1, 66, 22.8), "§2Forest Biome", new Vec3(-25.5, 66, -23.2), "§5Haunted Biome", new Vec3(-73.3, 65, -23.4), "§9Icy Biome", new Vec3(-72.6, 65.5, 23.9), "§6Cavern Biome");

    // from skyhanni CritterCapsuleHelper.throwLine with its numbers for a capsules fall + SafariNamesInCenter
    private static void safariDraw(WorldDraw w) {
        if (opt(SAFARI, "names") && (GameState.area.isEmpty() || GameState.area.equals("None"))) {
            for (var e : CENTER.entrySet()) if (e.getKey().distanceToSqr(w.eye) > 50) w.dynamicText(e.getKey(), e.getValue(), 2.5, 0);
        }
        if (!opt(SAFARI, "capsule_path") || w.mc.player == null || w.mc.level == null) return;
        String id = Gain.sbId(w.mc.player.getMainHandItem());
        if (!id.equals("CRITTER_CAPSULE") && !id.equals("MASTERFUL_CRITTER_CAPSULE")) return;
        int color = Features.get(SAFARI).option(SAFARI + ".capsule_color").color() | 0xFF000000;
        Vec3 start = w.mc.player.getEyePosition(1f), look = w.mc.player.getViewVector(1f), last = start;
        for (int i = 0; i < 50; i++) {
            double fall = (-0.85614) * Math.exp(-0.29696 * 0.093157 * i) * Math.sin(0.093157 * Math.sqrt(1 - 0.29696 * 0.29696) * i + 3.6507) - 36.584 + 35.962 * Math.sin((-0.027059) * i + 14.087);
            Vec3 pos = start.add(look.scale(i)).add(0, fall, 0);
            if (i > 1) w.line(last, pos, color, 2f, false);
            BlockPos block = BlockPos.containing(pos);
            BlockState state = w.mc.level.getBlockState(block);
            if (!state.isAir() && !state.is(Blocks.WATER)) {
                w.filledBox(new AABB(block), color & 0xFFFFFF, 0.5f, false);
                return;
            }
            for (Entity e : w.mc.level.getEntities((Entity) null, new AABB(last, pos).inflate(0.05))) {
                if (e instanceof Player) continue;
                w.box(e.getBoundingBox().inflate(0.05), color, false, false);
                return;
            }
            last = pos;
        }
    }

    // from skyhanni CritterCapsuleHider
    private static final FishHooks.Scan CAPSULE_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            return Features.on(SAFARI) && safari();
        }

        @Override
        public void entity(Entity e) {
            Minecraft mc = Minecraft.getInstance();
            if (e instanceof ItemEntity item && opt(SAFARI, "capsule_ground") && "CRITTER_CAPSULE".equals(Gain.sbId(item.getItem()))) FishHooks.hide(e);
            if (!(e instanceof Display.ItemDisplay d) || d.itemRenderState() == null) return;
            String mode = Features.get(SAFARI).option(SAFARI + ".capsule_flying").choice();
            if (mode.equals("Never")) return;
            Vec3 cam = mc.player.getEyePosition();
            double close = Features.get(SAFARI).option(SAFARI + ".capsule_close").number();
            boolean near = d.distanceToSqr(cam) <= close * close;
            if (!near && !mode.equals("Always")) return;
            String id = Gain.sbId(d.itemRenderState().itemStack());
            if (id.equals("CRITTER_CAPSULE") || (id.equals("MASTERFUL_CRITTER_CAPSULE") && near)) FishHooks.hide(e);
        }
    };

    // --- register ---

    // from Menus.keys, where the keys get made
    public static void keys(KeyMapping.Category category) {
        repeatKey = key("fusion_repeat", category);
        confirmKey = key("fusion_confirm", category);
        shulkerKey = key("next_shulker", category);
        FishCards.trophyKey = key("trophy_card", category);
    }

    private static KeyMapping key(String name, KeyMapping.Category category) {
        //? if >=26.3 {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion." + name, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
        //?} else {
        /*return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion." + name, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN, category));
        *///?}
    }

    public static void register() {
        Features.add(new Feature(HIGH, "Hunting", "Hunting highlights", "A box with name and distance on the mobs you hunt on Galatea and in the Torrhus Canyon, when you are near them.", "SkyHanni: Galatea Mob Highlight").when(() -> galatea() || torrhus() || SbcMod.DEV)
                .world(w -> {
                    highDraw(w);
                    invisibugDraw(w);
                }).left(() -> {
                    marks = List.of();
                    invisibugs.clear();
                })
                .option(Option.color("hunting_highlights.color", "Colour", "", 0xFF55FF55))
                .option(Option.bool("hunting_highlights.birries", "Birries", "", true))
                .option(Option.bool("hunting_highlights.hideonleaf", "Hideonleaf", "", true))
                .option(Option.bool("hunting_highlights.invisibug", "Invisibugs", "", true))
                .option(Option.bool("hunting_highlights.shellwise", "Shellwise", "", true))
                .option(Option.bool("hunting_highlights.coralot", "Coralot", "", true))
                .option(Option.bool("hunting_highlights.hideonsun", "Hideonsun", "", true))
                .option(Option.bool("hunting_highlights.blue_jay", "Blue Jay", "", true)));
        FishHooks.scan(HIGH_SCAN);
        ParticleHooks.listen(HuntHelpers::particle);

        Features.add(new Feature(FLOOR, "Hunting", "Floor drops", "Marks a shard lying on the floor while it sparkles.", "Skyblocker: Highlight Floor Drops").when(() -> galatea() || torrhus() || safari() || SbcMod.DEV).tick(HuntHelpers::floorTick).world(HuntHelpers::floorDraw).left(floorDrops::clear)
                .option(Option.color("hunting_floor_drops.color", "Colour", "", 0xFFFFAA00)));
        Interactions.onBlockClick((pos, right) -> floorDrops.remove(BlockPos.containing(pos)));

        Features.add(new Feature(LASSO, "Hunting", "Lasso", "How far your lasso's bar has gone, and when to reel.", "SkyHanni: Lasso Display").when(SKYBLOCK));
        FishHooks.scan(LASSO_SCAN);
        HudLayout.add(LASSO_CARD);

        Features.add(new Feature(FUSION, "Hunting", "Fusion", "Beside the Fusion Box, how many of the shard you last fused are in your Hunting Box, and your Pure Reptiles since. Keys to repeat and confirm a fusion can be bound in the controls.", "SkyHanni: Fusion Display, Fusion Keybinds").when(SKYBLOCK));
        ChatBus.register(ChatBus.OBSERVE + 7, FUSION, null, HuntHelpers::fusionChat);
        SideHelper.add(HuntHelpers::fusionPanel);
        Features.add(new Feature("hunting_keys", "Hunting", "Hunting keys", "The keys to repeat and confirm a fusion and to show the next Shulker. Bind them in the controls.", "").alwaysOn().when(SKYBLOCK).tick(HuntHelpers::keysTick).left(() -> {
            route = null;
            goal = null;
            routeAt = 0;
        }));

        Features.add(new Feature(SHULKER, "Hunting", "Shulker finder", "The Show next Shulker key walks you along the spots where Hideonleaf and Hideonsun can be.", "SkyHanni: Shulker Finder").when(SKYBLOCK).defaultOff());

        Features.add(new Feature("hunting_box_marks", "Hunting", "Hunting Box ready", "A shard you hold enough of to level its attribute goes green, yellow when you still need to unlock it.", "Skyblocker: Hunting Box Helper").when(SKYBLOCK));
        MenuMarks.add("hunting_box_marks", Patterns.get("SH.inventory.attributeshards.hunting-box"), HuntHelpers::boxMarks);

        Feature chat = Features.add(new Feature("hunting_chat", "Hunting", "Hunting chat", "Lines of hunting that only clutter your chat.", "SkyHanni: Redundant Comments, Swoop Huntaxe").when(SKYBLOCK)
                .option(Option.bool("hunting_chat.comments", "Redundant comments", "", false))
                .option(Option.bool("hunting_chat.swoop", "Swoop's line about axes", "", false))
                .option(Option.bool("hunting_chat.critters", "Critter captures on the Safari", "CAPTURE! and LOOT SHARE! lines.", false)));
        ChatBus.register(ChatBus.FILTER + 24, "hunting_chat", chat, HuntHelpers::chat);

        Features.add(new Feature(SAFARI, "Hunting", "Safari helpers", "Critter Safari: boxes on what you hunt, the capsule's path, biome names and fewer capsules in your view.", "SkyHanni: Safari; Skyblocker: Safari").when(HuntHelpers::safari).defaultOff().world(HuntHelpers::safariDraw)
                .option(Option.bool("hunting_safari.names", "Biome names in the centre", "", true))
                .option(Option.bool("hunting_safari.rockmite", "Rockmite Mounds", "", true))
                .option(Option.bool("hunting_safari.hideonfloor", "Hideonfloor", "", true))
                .option(Option.bool("hunting_safari.bloodbat", "Bloodbats", "", true))
                .option(Option.bool("hunting_safari.hide_cold", "Hide the frost in the Icy biome", "The frost on your screen when you are cold.", false))
                .option(Option.bool("hunting_safari.duplico", "Duplico", "Hides as furniture in the Haunted house.", true))
                .option(Option.color("hunting_safari.duplico_color", "Duplico colour", "", 0xFFB02E26))
                .option(Option.bool("hunting_safari.sparkling", "Sparkling critters", "", false))
                .option(Option.bool("hunting_safari.capsule_path", "Path of a held capsule", "", false))
                .option(Option.color("hunting_safari.capsule_color", "Path colour", "", 0xFF55FFFF))
                .option(Option.choice("hunting_safari.capsule_flying", "Hide flying capsules", "", "Never", "Never", "Only close", "Always"))
                .option(Option.number("hunting_safari.capsule_close", "Close means (blocks)", "", 2, 1, 8, 0.5))
                .option(Option.bool("hunting_safari.capsule_ground", "Hide capsules on the ground", "", false)));
        FishHooks.scan(CAPSULE_SCAN);
        Feature sc = Features.add(new Feature("safari_checklist", "Hunting", "Safari shard checklist", "Which shards of each biome you caught this run.", "SkyHanni: Safari Checklist").when(HuntHelpers::safari).defaultOff().left(safariCaught::clear)
                .option(Option.choice("safari_checklist.mode", "Show which biomes", "", "Only this one", "Only this one", "This one on top", "All"))
                .option(Option.bool("safari_checklist.hide_caught", "Hide what you caught", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 7, "safari_checklist", sc, HuntHelpers::safariChat);
        HudLayout.add(SAFARI_CARD);
    }
}
