// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.carnival;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.events.EventSheet;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.BlockHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// zombie shootout at the carnival. from skyhanni CarnivalZombieShootout + skyblocker ZombieShootout: which
// zombie is worth what and how long it stays, by helmet (chestplate if none, like skyblocker), and the lit
// lamp
// boxes only for what you can see unless you say otherwise
public final class ZombieShootout {
    public static final String ID = "carnival_zombies";
    private static final long LAMP_MS = 6_000;

    enum ZombieType {
        LEATHER(30, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, 0xFFA52A2A, 8_000), IRON(50, Items.IRON_HELMET, Items.IRON_CHESTPLATE, 0xFFC0C0C0, 7_000),
        GOLD(80, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, 0xFFFFD700, 6_000), DIAMOND(120, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, 0xFF2CD6FA, 5_000);

        final int points, color;
        final Item helmet, chest;
        final long lifetime;

        ZombieType(int points, Item helmet, Item chest, int color, long lifetime) {
            this.points = points;
            this.helmet = helmet;
            this.chest = chest;
            this.color = color;
            this.lifetime = lifetime;
        }
    }

    private record Seen(Zombie entity, ZombieType type) {}

    private static boolean started;
    private static BlockPos lamp;
    private static long lampAt;
    private static List<Seen> zombies = List.of();
    private static final Map<Integer, Long> FIRST_SEEN = new HashMap<>();
    private static ZombieType maxType;

    private ZombieShootout() {}

    private static boolean on() {
        return started && Carnival.inArea();
    }

    // determinePrefix: green first third of the time, yellow second, red last
    static String prefix(long left, long whole) {
        long third = whole / 3;
        return left >= whole - third ? "§a" : left >= whole - third * 2 ? "§e" : "§c";
    }

    static ZombieType typeOf(ItemStack helmet, ItemStack chest) {
        for (ZombieType t : ZombieType.values()) if (helmet.is(t.helmet)) return t;
        for (ZombieType t : ZombieType.values()) if (chest.is(t.chest)) return t;
        return null;
    }

    private static void tick(Minecraft mc) {
        if (!on() || mc.level == null || mc.player == null || mc.player.tickCount % 2 != 0) return;
        List<Seen> out = new ArrayList<>();
        ZombieType max = ZombieType.LEATHER;
        for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(50))) {
            if (Combat.health(z) <= 0) continue;
            ZombieType t = typeOf(z.getItemBySlot(EquipmentSlot.HEAD), z.getItemBySlot(EquipmentSlot.CHEST));
            if (t == null) continue;
            out.add(new Seen(z, t));
            if (t.points > max.points) max = t;
            FIRST_SEEN.putIfAbsent(z.getId(), System.currentTimeMillis());
        }
        zombies = out;
        maxType = max;
        FIRST_SEEN.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    private static void draw(WorldDraw w) {
        if (!on()) return;
        boolean walls = Combat.opt(ID, "through_walls"), highest = Combat.opt(ID, "highest_only");
        long now = System.currentTimeMillis();
        if (lamp != null && Combat.opt(ID, "boxes")) w.box(new AABB(lamp), 0xFFFF0000, true, walls);
        if (lamp != null && Combat.opt(ID, "lines")) w.line(Combat.fromCrosshair(w), Vec3.atCenterOf(lamp), 0xFFFF0000, 3, walls);
        for (Seen s : zombies) {
            if (highest && s.type() != maxType) continue;
            Zombie z = s.entity();
            if (Combat.opt(ID, "boxes")) w.box(z.getBoundingBox().inflate(0.1, 0.05, 0).move(0, 0.05, 0), s.type().color, false, walls);
            if (Combat.opt(ID, "timers")) {
                Long at = FIRST_SEEN.get(z.getId());
                if (at == null) continue;
                long left = s.type().lifetime - (now - at);
                if (left <= 0) continue;
                double height = z.isBaby() ? z.getBbHeight() / 2 : z.getBbHeight();
                w.label(prefix(left, s.type().lifetime) + String.format(Locale.ROOT, "%.1fs", left / 1000.0), z.position().add(0, height + 0.5, 0), false);
            }
        }
    }

    private static void lampLine(EventSheet s, long left) {
        s.line("Lamp goes in", String.format(Locale.ROOT, "%.1fs", Math.max(0, left) / 1000.0), left >= 4_000 ? Modern.GOOD : left >= 2_000 ? Modern.WARN : Modern.BAD).icon("redstone_lamp").brief("Lamp").meter(Math.max(0, left) / (double) LAMP_MS);
    }

    private static final HudElement CARD = new EventSheet.Card(ID, "Zombie Shootout", "Events", Zone.TOP_LEFT, 47, ID) {
        @Override
        public boolean visible() {
            return on() && lamp != null && Combat.opt(ID, "lamp_timer");
        }

        @Override
        protected void live(EventSheet s) {
            lampLine(s, LAMP_MS - (System.currentTimeMillis() - lampAt));
        }

        @Override
        protected void sample(EventSheet s) {
            lampLine(s, 4_200);
        }

        @Override
        protected List<Row> plainRows() {
            return List.of(new Row("Lamp goes in", String.format(Locale.ROOT, "%.1fs", Math.max(0, LAMP_MS - (System.currentTimeMillis() - lampAt)) / 1000.0), 0));
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Lamp goes in", "4.2s", 0));
        }
    };

    static void register() {
        Feature f = Features.add(new Feature(ID, "Events", "Zombie Shootout", "A coloured box on each zombie by its worth, with how long it stays; the lit lamp boxed with a line to it, and how long it stays lit.", "SkyHanni: Zombie Shootout, Skyblocker: Zombie Shootout Helper")
                .when(Carnival::inArea)
                .tick(ZombieShootout::tick)
                .world(ZombieShootout::draw)
                .left(() -> {
                    started = false;
                    lamp = null;
                    zombies = List.of();
                    FIRST_SEEN.clear();
                })
                .option(Option.bool("carnival_zombies.boxes", "Coloured boxes", "On the zombies and the lamp.", true))
                .option(Option.bool("carnival_zombies.lines", "Line to the lamp", "", true))
                .option(Option.bool("carnival_zombies.timers", "Zombie timers", "", true))
                .option(Option.bool("carnival_zombies.lamp_timer", "Lamp timer", "", true))
                .option(Option.bool("carnival_zombies.highest_only", "Only the highest worth", "Only box and time the zombies worth most.", false))
                .option(Option.bool("carnival_zombies.through_walls", "Through walls", "", false)));
        ChatBus.register(ChatBus.OBSERVE + 25, ID, f, line -> {
            String clean = GameState.strip(line.plain());
            if (Patterns.get("SH.event.carnival.shootout.start").matcher(clean).matches()) started = true;
            else if (Patterns.get("SH.event.carnival.shootout.end").matcher(clean).matches()) started = false;
            return ChatBus.PASS;
        });
        BlockHooks.listen((pos, old, now) -> {
            if (!started || !old.is(Blocks.REDSTONE_LAMP) || !now.is(Blocks.REDSTONE_LAMP) || !Features.on(ID)) return;
            boolean was = old.getValue(BlockStateProperties.LIT), is = now.getValue(BlockStateProperties.LIT);
            if (!was && is) {
                lamp = pos.immutable();
                lampAt = System.currentTimeMillis();
            } else if (was && !is) {
                lamp = null;
            }
        });
        HudLayout.add(CARD);
    }
}
