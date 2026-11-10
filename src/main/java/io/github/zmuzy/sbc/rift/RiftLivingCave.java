// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.ParticleHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// the living cave, from skyhanni rift/area/livingcave: DefenseBlocks, LivingMetalHelper, SnakeFeatures +
// snake/LivingCaveSnake.kt, LivingMetalSuitProgress
public final class RiftLivingCave {
    public static final String ID = "rift_living_cave", SUIT = "rift_living_suit";
    private static final Set<String> AUTOMATONS = Set.of("Autonull ", "Autocap ", "Autochest ", "Autopants ", "Autoboots ");
    private static final String PUNGI = "FROZEN_WATER_PUNGI";
    private static final Set<String> PICKAXES = Set.of("SELF_RECURSIVE_PICKAXE", "ANTI_SENTIENT_PICKAXE", "EON_PICKAXE", "CHRONO_PICKAXE");

    private RiftLivingCave() {}

    private static boolean on(String key, boolean def) {
        return RiftScan.on(ID, key, def);
    }

    private static boolean inCave() {
        return RiftApi.inLivingCave();
    }

    private static boolean inCaveOrStillness() {
        return RiftApi.inLivingCave() || RiftApi.inLivingStillness();
    }

    // --- defense blocks ---

    private static final class DefenseBlock {
        final Player entity;
        final Vec3 location;
        boolean hidden;

        DefenseBlock(Player entity, Vec3 location) {
            this.entity = entity;
            this.location = location;
        }
    }

    private static final Map<DefenseBlock, Long> MOVING = new HashMap<>();
    private static final List<DefenseBlock> STATIC = new ArrayList<>();

    private static DefenseBlock nearest(Iterable<DefenseBlock> blocks, Vec3 at) {
        DefenseBlock best = null;
        double bestD = 15;
        for (DefenseBlock b : blocks) {
            double d = b.location.distanceTo(at);
            if (d < bestD) {
                best = b;
                bestD = d;
            }
        }
        return best;
    }

    private static boolean near(Iterable<DefenseBlock> blocks, Vec3 at, double range) {
        for (DefenseBlock b : blocks) if (b.location.distanceTo(at) < range) return true;
        return false;
    }

    private static void onDefenseParticle(ParticleHooks.Particle ev) {
        if (!inCave() || !on("rift_living_cave.defense", true)) return;
        long now = System.currentTimeMillis();
        MOVING.values().removeIf(t -> now > t + 2000);
        MOVING.keySet().removeIf(m -> near(STATIC, m.location, 1.5));
        Vec3 raw = new Vec3(ev.x(), ev.y(), ev.z()), location = raw.add(-0.5, 0, -0.5);
        if (near(STATIC, location, 3) || !ev.is("minecraft:enchanted_hit")) return;
        Player entity = null;
        DefenseBlock old = nearest(MOVING.keySet(), location);
        if (old != null && old.location.distanceTo(location) < 0.5) {
            old.hidden = true;
            entity = old.entity;
        }
        var mc = Minecraft.getInstance();
        if (entity == null && mc.level != null) {
            Vec3 compare = raw.add(-0.5, -1.5, -0.5);
            double best = 2;
            for (Player p : mc.level.players()) {
                if (!(p instanceof RemotePlayer) || !AUTOMATONS.contains(p.getName().getString()) || p.getHealth() >= p.getMaxHealth()) continue;
                double d = p.position().distanceTo(compare);
                if (d < best) {
                    best = d;
                    entity = p;
                }
            }
        }
        if (entity != null) MOVING.put(new DefenseBlock(entity, location), now + 250);
    }

    private static void defenseBlockChange(BlockPos pos, String old, String now) {
        Vec3 at = Vec3.atLowerCornerOf(pos);
        if (old.equals("air") && (now.endsWith("stained_glass") || now.equals("diamond_block"))) {
            DefenseBlock moving = nearest(MOVING.keySet(), at);
            if (moving != null) STATIC.add(new DefenseBlock(moving.entity, at));
        }
        DefenseBlock nearestStatic = nearest(STATIC, at);
        if (now.equals("air") && nearestStatic != null && nearestStatic.location.equals(at)) STATIC.remove(nearestStatic);
    }

    private static void drawDefense(WorldDraw w) {
        int color = RiftScan.color(ID, "rift_living_cave.defense_color", 0xFF4D68FF);
        long now = System.currentTimeMillis();
        for (var e : MOVING.entrySet()) {
            DefenseBlock b = e.getKey();
            if (b.hidden || e.getValue() <= now) continue;
            w.waypointBox(b.location, color, false, false, 0);
            w.line(w.eye.add(w.mc.player.getLookAngle()), b.location.add(0.5, 0.5, 0.5), color, 1f, false);
        }
        STATIC.removeIf(b -> b.entity.isRemoved() || b.entity.isDeadOrDying());
        for (DefenseBlock b : STATIC) {
            w.dynamicText(b.location, "§bBreak!", 1.5, 0);
            w.waypointBox(b.location, color, false, false, 0);
            w.box(b.entity.getBoundingBox(), (color & 0xFFFFFF) | 0x32000000, true, false);
            w.line(b.entity.position().add(0, 0.5, 0), b.location.add(0.5, 0.5, 0.5), color, 3f, true);
        }
    }

    // --- living metal ---

    private static Vec3 lastClicked, metalFrom, metalTo;
    private static long metalStart;

    private static void metalBlockChange(BlockPos pos, String old, String now) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 at = Vec3.atLowerCornerOf(pos);
        if (at.distanceTo(mc.player.position()) >= 7) return;
        if (old.equals("lapis_ore") && at.equals(metalTo)) metalFrom = metalTo = null;
        if (!now.equals("lapis_ore") || lastClicked == null) return;
        if (at.distanceTo(lastClicked) < 2) {
            metalFrom = lastClicked;
            metalTo = at;
            metalStart = System.currentTimeMillis();
        }
    }

    private static void drawMetal(WorldDraw w) {
        if (metalTo == null) return;
        long now = System.currentTimeMillis();
        if (now - metalStart > 4000) return;
        Vec3 at = RiftScan.lerp(metalStart, 500, metalFrom, metalTo, now);
        w.waypointBox(at, RiftScan.color(ID, "rift_living_cave.metal_color", 0xFF55FFFF), false, at.distanceTo(w.eye) < 10, 0);
    }

    // from skyhanni onTitleReceived
    public static void onTitle(String text) {
        if (text.contains("Living Metal") && inCaveOrStillness()) metalFrom = metalTo = null;
    }

    // --- snakes ---

    private enum SnakeState {
        SPAWNING(0xFF55FFFF, "§bSpawning snake"), ACTIVE(0xFFFFFF55, "§eActive snake"), NOT_TOUCHING_AIR(0xFFFF5555, "§cNot touching air snake"), CALM(0xFF55FF55, "§aCalm snake");
        final int color;
        final String display;

        SnakeState(int color, String display) {
            this.color = color;
            this.display = display;
        }
    }

    private enum Role { BREAK, CALM }

    // from skyhanni LivingCaveSnake
    static final class Snake {
        final List<Vec3> blocks = new ArrayList<>();
        long lastRemove = Long.MIN_VALUE / 4, lastAdd = Long.MIN_VALUE / 4, invalidHeadSince = -1;
        SnakeState state = SnakeState.SPAWNING;
        Vec3 lastBroken;

        Snake(Vec3 first) {
            blocks.add(first);
        }

        Vec3 head() {
            return blocks.get(0);
        }

        Vec3 tail() {
            return blocks.get(blocks.size() - 1);
        }

        boolean invalidShape() {
            if (blocks.isEmpty()) return true;
            for (int i = 0; i + 1 < blocks.size(); i++) if (blocks.get(i).distanceTo(blocks.get(i + 1)) > 3) return true;
            return false;
        }

        boolean removeBlock(Vec3 at, Vec3 lastClickedBlock, long now) {
            // hypixel sends the packet again when clicking
            if (head().equals(at) && at.equals(lastClickedBlock) && blocks.size() > 1) return false;
            blocks.remove(at);
            if (blocks.isEmpty()) return true;
            if (state == SnakeState.SPAWNING) state = SnakeState.ACTIVE;
            lastRemove = now;
            lastBroken = at;
            return false;
        }

        void tick(Minecraft mc, long now) {
            boolean headLapis = mc.level.getBlockState(BlockPos.containing(head())).is(Blocks.LAPIS_BLOCK);
            if (headLapis) invalidHeadSince = -1;
            else if (invalidHeadSince < 0) invalidHeadSince = now;
            if (state == SnakeState.SPAWNING) return;
            boolean buried = false;
            for (Vec3 b : blocks) {
                BlockPos p = BlockPos.containing(b);
                if (!mc.level.getBlockState(p.east()).isAir() && !mc.level.getBlockState(p.west()).isAir() && !mc.level.getBlockState(p.above()).isAir()
                        && !mc.level.getBlockState(p.below()).isAir() && !mc.level.getBlockState(p.north()).isAir() && !mc.level.getBlockState(p.south()).isAir()) {
                    buried = true;
                    break;
                }
            }
            state = buried ? SnakeState.NOT_TOUCHING_AIR : now - lastAdd > 200 ? SnakeState.CALM : SnakeState.ACTIVE;
        }
    }

    private static final List<Snake> SNAKES = new ArrayList<>();
    private static final Map<Vec3, Block> ORIGINAL = new LinkedHashMap<>();
    private static Snake selected;
    private static Role role;
    private static Vec3 lastClickedBlock;
    private static boolean bothItems;

    // from skyhanni findNearbySnakeHeads + fixCollisions
    private static Snake headNear(Vec3 at) {
        List<Snake> found = new ArrayList<>();
        for (Snake s : SNAKES) if (!s.blocks.isEmpty() && s.head().distanceTo(at) < 1.74) found.add(s);
        found.sort(java.util.Comparator.comparingDouble(s -> s.head().distanceTo(at)));
        if (found.size() <= 1) return found.isEmpty() ? null : found.get(0);
        List<Snake> notCalm = new ArrayList<>();
        for (Snake s : found) if (s.state != SnakeState.CALM) notCalm.add(s);
        return notCalm.size() < found.size() ? (notCalm.isEmpty() ? null : notCalm.get(0)) : found.get(0);
    }

    private static void snakeBlockChange(BlockPos pos, BlockState old, BlockState now) {
        Vec3 at = Vec3.atLowerCornerOf(pos);
        long time = System.currentTimeMillis();
        if (now.is(Blocks.LAPIS_BLOCK)) {
            ORIGINAL.put(at, old.getBlock());
            Snake snake = headNear(at);
            if (snake == null) SNAKES.add(new Snake(at));
            else if (!snake.blocks.contains(at)) {
                snake.blocks.add(0, at);
                snake.lastAdd = time;
                snake.invalidHeadSince = -1;
            }
        }
        if (ORIGINAL.get(at) == now.getBlock()) {
            ORIGINAL.remove(at);
            for (Iterator<Snake> it = SNAKES.iterator(); it.hasNext(); ) {
                Snake s = it.next();
                if (s.blocks.contains(at) && s.removeBlock(at, lastClickedBlock, time)) it.remove();
            }
        }
    }

    private static void tickSnakes(Minecraft mc) {
        if (!on("rift_living_cave.snakes", true) || !inCaveOrStillness()) return;
        String held = RiftScan.heldId(mc);
        role = held.equals(PUNGI) ? Role.CALM : PICKAXES.contains(held) ? Role.BREAK : null;
        long now = System.currentTimeMillis();
        if (mc.player.tickCount % 20 == 0) {
            boolean breaker = false, pungi = false;
            for (int i = 0; i < 9; i++) {
                String id = Gain.sbId(mc.player.getInventory().getItem(i));
                if (PICKAXES.contains(id)) breaker = true;
                if (id.equals(PUNGI)) pungi = true;
            }
            bothItems = breaker && pungi;
        }
        SNAKES.removeIf(s -> s.invalidShape() || (s.invalidHeadSince >= 0 && now - s.invalidHeadSince > 1000));
        for (Snake s : SNAKES) s.tick(mc, now);
    }

    private static void drawBlock(WorldDraw w, Snake s, Vec3 at, int color) {
        boolean chosen = s == selected;
        w.filledBox(new AABB(at, at.add(1, 1, 1)), color, 1f, chosen);
        if (!chosen) return;
        w.dynamicText(at.add(0.5, 0.5, 0.5), s.state.display, 1, 0);
        w.dynamicText(at.add(0.5, 0.2, 0.5), "§b" + s.blocks.size() + " blocks", 1, -1);
    }

    private static void drawSnake(WorldDraw w, Snake s, long now) {
        if (s.blocks.isEmpty()) return;
        int size = s.blocks.size();
        boolean solo = on("rift_living_cave.snake_solo", true) && bothItems;
        Vec3 movingHead = size > 1 ? RiftScan.lerp(s.lastAdd, 200, s.blocks.get(1), s.head(), now) : s.head();
        Vec3 tail = s.lastBroken != null ? RiftScan.lerp(s.lastRemove, 300, s.lastBroken, s.tail(), now) : s.tail();
        if (solo) {
            if (size > 1) drawBlock(w, s, tail, 0xFF0000AA);
        } else {
            if (size > 1 && s.state == SnakeState.CALM && role == Role.BREAK) drawBlock(w, s, tail, s.state.color);
        }
        drawBlock(w, s, movingHead, s.state.color);
        for (Vec3 b : s.blocks) {
            if (b.equals(s.head()) && now - s.lastAdd < 200) continue;
            w.box(new AABB(b, b.add(1, 1, 1)), s.state.color, false, false);
        }
    }

    // --- suit ---

    // name -> progress 0..1, -1 = not started. helmet to boots
    private static final Map<String, Double> SUIT_PROGRESS = new LinkedHashMap<>();

    private static void tickSuit(Minecraft mc) {
        if (mc.player == null || mc.player.tickCount % 20 != 0) return;
        SUIT_PROGRESS.clear();
        boolean any = false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = mc.player.getItemBySlot(slot);
            if (st.isEmpty()) continue;
            var cd = st.get(DataComponents.CUSTOM_DATA);
            int evo = cd == null ? -1 : cd.copyTag().getIntOr("lm_evo", -1);
            if (evo >= 0) any = true;
            SUIT_PROGRESS.put(LegacyText.name(st), evo < 0 ? -1.0 : Math.min(evo, 100) / 100.0);
        }
        if (!any) SUIT_PROGRESS.clear();
    }

    // from skyhanni drawDisplay: pieces not started count as done in the average, same as skyhanni
    static double suitTotal(Iterable<Double> pieces) {
        double sum = 0;
        int n = 0;
        for (double p : pieces) {
            sum += p < 0 ? 1 : p;
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private static RichCard.Content suit(Map<String, Double> pieces, boolean compact) {
        double total = suitTotal(pieces.values());
        int maxed = 0;
        for (double p : pieces.values()) if (p >= 1) maxed++;
        boolean isMaxed = maxed == 4;
        List<RichCard.Line> out = new ArrayList<>();
        if (!(compact && isMaxed)) {
            for (var e : pieces.entrySet()) {
                double p = e.getValue();
                RichCard.Line line = RichCard.Line.row(e.getKey(), p < 0 ? "§cStart it" : String.format(Locale.ROOT, "§b%.0f%%", p * 100)).raw();
                out.add(p < 0 ? line.noMeter() : line.meter(p, Modern.AQUA));
            }
        }
        return new RichCard.Content(isMaxed ? "§a§lMAXED" : String.format(Locale.ROOT, "§a%.0f%%", total * 100), out);
    }

    private static final HudElement SUIT_CARD = new RichCard(SUIT, "Living Metal Suit", "Rift", Zone.TOP_LEFT, 53, SUIT) {
        @Override public boolean visible() {
            return Rift.rift() && !SUIT_PROGRESS.isEmpty();
        }

        @Override protected Content live() {
            return suit(SUIT_PROGRESS, RiftScan.on(SUIT, "rift_living_suit.compact", false));
        }

        private Map<String, Double> sampleSuit() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("§9Living Metal Cap", 0.84);
            m.put("§9Living Metal Chest", 0.6);
            m.put("§9Living Metal Pants", 1.0);
            m.put("§9Living Metal Boots", -1.0);
            return m;
        }

        @Override protected Content sample() {
            return suit(sampleSuit(), false);
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            for (var e : SUIT_PROGRESS.entrySet()) out.add(new Row(e.getKey(), e.getValue() < 0 ? "Start it" : String.format(Locale.ROOT, "%.0f%%", e.getValue() * 100), 0));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Living Metal Cap", "84%", 0), new Row("Living Metal Boots", "Start it", 0));
        }
    };

    // --- hooks ---

    public static void onBlockChange(BlockPos pos, BlockState old, BlockState now, String oldName, String newName) {
        if (!Features.on(ID) || !inCaveOrStillness()) return;
        if (inCave() && on("rift_living_cave.defense", true)) defenseBlockChange(pos, oldName, newName);
        if (on("rift_living_cave.metal", true)) metalBlockChange(pos, oldName, newName);
        if (on("rift_living_cave.snakes", true)) snakeBlockChange(pos, old, now);
    }

    // particle halves of skyhanni LivingCaveDefenseBlocks + LivingMetalHelper
    public static boolean hidesParticle(String type, double x, double y, double z) {
        if (!Features.on(ID) || !inCaveOrStillness()) return false;
        Vec3 raw = new Vec3(x, y, z);
        if (on("rift_living_cave.metal_particles", false) && metalTo != null && metalTo.distanceTo(raw) < 3) return true;
        if (!inCave() || !on("rift_living_cave.defense_particles", false)) return false;
        Vec3 at = raw.add(-0.5, 0, -0.5);
        return type.equals("enchanted_hit") || near(STATIC, at, 3) || near(MOVING.keySet(), at, 3);
    }

    private static void onClick(Vec3 pos, boolean right) {
        if (!Rift.rift() || !Features.on(ID) || !inCaveOrStillness()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (!right && on("rift_living_cave.metal", true) && mc.level.getBlockState(BlockPos.containing(pos)).is(Blocks.LAPIS_ORE)) lastClicked = pos;
        lastClickedBlock = pos;
        for (Snake s : SNAKES) {
            if (s.blocks.contains(pos)) {
                selected = s;
                return;
            }
        }
    }

    private static void reset() {
        MOVING.clear();
        STATIC.clear();
        SNAKES.clear();
        ORIGINAL.clear();
        selected = null;
        role = null;
        bothItems = false;
        lastClicked = metalFrom = metalTo = lastClickedBlock = null;
    }

    public static void register() {
        Features.add(new Feature(ID, "Rift", "Living Cave", "Defense blocks and the mob they guard, where Living Metal moves to next, and the Living Metal snakes coloured by their state while you hold a pickaxe or the pungi.", "SkyHanni: Living Cave")
                .when(Rift::rift)
                .tick(mc -> {
                    if (mc.player == null || mc.level == null) return;
                    tickSnakes(mc);
                })
                .world(w -> {
                    if (inCave() && on("rift_living_cave.defense", true)) drawDefense(w);
                    if (inCaveOrStillness() && on("rift_living_cave.metal", true)) drawMetal(w);
                    if (inCaveOrStillness() && on("rift_living_cave.snakes", true) && role != null) {
                        long now = System.currentTimeMillis();
                        for (Snake s : SNAKES) drawSnake(w, s, now);
                    }
                })
                .left(RiftLivingCave::reset)
                .option(Option.bool("rift_living_cave.defense", "Defense blocks", "A line from each defense block to its mob, and Break! on the block.", true))
                .option(Option.color("rift_living_cave.defense_color", "Defense block colour", "", 0xFF4D68FF))
                .option(Option.bool("rift_living_cave.defense_particles", "Hide defense particles", "", false))
                .option(Option.bool("rift_living_cave.metal", "Living Metal", "Shows Living Metal moving to the next block.", true))
                .option(Option.color("rift_living_cave.metal_color", "Living Metal colour", "", 0xFF55FFFF))
                .option(Option.bool("rift_living_cave.metal_particles", "Hide Living Metal particles", "", false))
                .option(Option.bool("rift_living_cave.snakes", "Snakes", "Colours each snake by its state: active, calm or not touching air.", true))
                .option(Option.bool("rift_living_cave.snake_solo", "Solo snakes", "With both the pickaxe and the pungi in your hotbar, marks head and tail together.", true)));
        Features.add(new Feature(SUIT, "Rift", "Living Metal Suit progress", "How far each piece of the suit has evolved, while you wear Living Metal armor.", "SkyHanni: Living Metal Suit Progress")
                .when(Rift::rift).tick(RiftLivingCave::tickSuit).left(SUIT_PROGRESS::clear)
                .option(Option.bool("rift_living_suit.compact", "Only the total when maxed", "Once all four are done, the card says only that.", false)));
        HudLayout.add(SUIT_CARD);
        ParticleHooks.listen(ev -> {
            if (Rift.rift() && Features.on(ID)) onDefenseParticle(ev);
        });
        Interactions.onBlockClick(RiftLivingCave::onClick);
    }
}
