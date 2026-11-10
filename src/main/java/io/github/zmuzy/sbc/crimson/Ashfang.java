// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.crimson;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.rift.RiftScan;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// the ashfang fight, from skyhanni AshfangManager, AshfangFreezeCooldown, AshfangNextResetCooldown,
// AshfangHighlights, AshfangHider
// boss + blazes are found by their name stands (no skyhanni mob detection here). all off by default. skyhanni
// tints the blazes, here they get a box
public final class Ashfang {
    public static final String ID = "crimson_ashfang";
    static final Patterns.P CRYOGENIC = Patterns.p("SH.ashfang.freeze.cryogenic");
    private static final long FREEZE = 3000, RESET = 46_100, SEEN_GAP = 10_000;
    private static final double MAX_LABEL_DISTANCE = 15;

    private static boolean active;
    private static long unfrozenAt = -1, lastSpawn = -1;
    private static final Map<LivingEntity, Integer> BLAZES = new HashMap<>();
    private static final Set<Integer> SEEN = new HashSet<>();
    private static final Set<ArmorStand> FULL_NAMES = new HashSet<>(), GLOWSTONE = new HashSet<>();
    private static final List<ArmorStand> SOULS = new ArrayList<>(), ORBS = new ArrayList<>();

    private Ashfang() {}

    private static void reset() {
        active = false;
        unfrozenAt = lastSpawn = -1;
        BLAZES.clear();
        SEEN.clear();
        FULL_NAMES.clear();
        GLOWSTONE.clear();
        SOULS.clear();
        ORBS.clear();
    }

    private static boolean on(String key) {
        return RiftScan.on(ID, key, true);
    }

    // the boss itself, not a blaze. "Ashfang Follower", "Ashfang Underling" and "Ashfang Acolyte" are the blazes
    static int blazeColor(String plainName) {
        if (plainName.contains("Ashfang Follower")) return 0xFF555555;
        if (plainName.contains("Ashfang Underling")) return 0xFFFF5555;
        if (plainName.contains("Ashfang Acolyte")) return 0xFF5555FF;
        return 0;
    }

    static boolean isBoss(String plainName) {
        return plainName.contains("Ashfang") && blazeColor(plainName) == 0 && (plainName.contains("[Lv") || plainName.contains("❤"));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.tickCount % 5 != 0) return;
        boolean boss = false;
        BLAZES.clear();
        FULL_NAMES.clear();
        GLOWSTONE.clear();
        SOULS.clear();
        ORBS.clear();
        Set<Integer> alive = new HashSet<>();
        String soul = RiftScan.skull("ASHFANG_BLAZING_SOUL"), orb = RiftScan.skull("ASHFANG_GRAVITY_ORB");
        long now = System.currentTimeMillis();
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(64))) {
            if (st.hasCustomName()) {
                String name = GameState.strip(st.getName().getString());
                if (isBoss(name)) boss = true;
                int color = blazeColor(name);
                if (color != 0) {
                    alive.add(st.getId());
                    // onMobFirstSeen: new wave of blazes after a pause = reset just happened
                    if (SEEN.add(st.getId()) && (lastSpawn < 0 || now - lastSpawn >= SEEN_GAP)) lastSpawn = now;
                    LivingEntity mob = RiftScan.mobUnder(st);
                    if (mob != null) {
                        BLAZES.put(mob, color);
                        if (mob.getHealth() >= mob.getMaxHealth()) FULL_NAMES.add(st);
                    }
                }
            }
            String tex = RiftScan.texture(st.getItemBySlot(EquipmentSlot.HEAD));
            if (tex != null && tex.equals(soul)) SOULS.add(st);
            else if (tex != null && tex.equals(orb)) ORBS.add(st);
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = st.getItemBySlot(slot);
                if (!item.isEmpty() && item.getHoverName().getString().equals("Glowstone")) {
                    GLOWSTONE.add(st);
                    break;
                }
            }
        }
        SEEN.retainAll(alive);
        if (active && !boss) lastSpawn = -1;
        active = boss;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (active && CRYOGENIC.matcher(io.github.zmuzy.sbc.core.LegacyText.of(line.component())).matches()) unfrozenAt = System.currentTimeMillis() + FREEZE;
        return ChatBus.PASS;
    }

    // blendRGB(GREEN, RED, distance / 15) as a colour code is too coarse, so just green yellow red
    private static String near(double distance) {
        return distance < MAX_LABEL_DISTANCE / 3 ? "§a" : distance < MAX_LABEL_DISTANCE * 2 / 3 ? "§e" : "§c";
    }

    private static void draw(WorldDraw w) {
        if (!active) return;
        if (on("crimson_ashfang.blazes")) for (var e : BLAZES.entrySet()) if (!e.getKey().isRemoved()) w.box(e.getKey().getBoundingBox(), (e.getValue() & 0xFFFFFF) | 0x28000000, true, false);
        if (on("crimson_ashfang.souls")) {
            int color = RiftScan.color(ID, "crimson_ashfang.souls_color", 0xF555FF55);
            for (ArmorStand st : SOULS) {
                Vec3 at = st.position();
                w.waypointBox(at.add(-0.5, 1.25, -0.5), color, false, false, -0.15);
                double d = at.distanceTo(w.eye);
                if (d < MAX_LABEL_DISTANCE) w.dynamicText(at.add(0, 2.5, 0), near(d) + "Blazing Soul", 1, 0);
            }
        }
        if (on("crimson_ashfang.orbs")) {
            int color = RiftScan.color(ID, "crimson_ashfang.orbs_color", 0x78FF5555);
            for (ArmorStand st : ORBS) {
                Vec3 at = st.position();
                RiftScan.cylinder(w, at.add(-0.5, -2, -0.5), 3.5, 4.5, color);
                double d = at.distanceTo(w.eye);
                if (d < MAX_LABEL_DISTANCE) w.dynamicText(at.add(0, 2.5, 0), near(d) + "Gravity Orb", 1, 0);
            }
        }
    }

    // AshfangManager.onRenderLiving + AshfangHider.onCheckRender
    public static boolean hidden(Entity e) {
        if (!active || !(e instanceof ArmorStand st) || !Features.on(ID)) return false;
        if (FULL_NAMES.contains(st) && on("crimson_ashfang.hide_names")) return true;
        return GLOWSTONE.contains(st) && on("crimson_ashfang.hide_particles");
    }

    // AshfangHider.onParticle: every particle while the fight is on
    public static boolean hidesParticles() {
        return active && Features.on(ID) && on("crimson_ashfang.hide_particles");
    }

    private static RichCard.Content card(long freezeLeft, long resetLeft) {
        List<RichCard.Line> out = new ArrayList<>();
        if (freezeLeft >= 0) out.add(RichCard.Line.row("Freeze", String.format(Locale.ROOT, "§a%.1fs", freezeLeft / 1000.0)).raw().meter(freezeLeft / (double) FREEZE, Modern.AQUA));
        if (resetLeft != Long.MIN_VALUE) {
            String v = resetLeft <= 0 ? "§aNow!" : String.format(Locale.ROOT, "§b%.1fs", resetLeft / 1000.0);
            out.add(RichCard.Line.row("Next reset", v).raw().meter(resetLeft <= 0 ? 0 : resetLeft / (double) RESET, Modern.GOLD));
        }
        return new RichCard.Content("", out);
    }

    private static long freezeLeft() {
        long left = unfrozenAt - System.currentTimeMillis();
        return left > 0 && on("crimson_ashfang.freeze") ? left : -1;
    }

    private static long resetLeft() {
        return lastSpawn >= 0 && on("crimson_ashfang.reset") ? lastSpawn + RESET - System.currentTimeMillis() : Long.MIN_VALUE;
    }

    private static final HudElement CARD = new RichCard(ID, "Ashfang", "Crimson Isle", Zone.TOP_LEFT, 46, ID) {
        @Override public boolean visible() {
            return active && (freezeLeft() >= 0 || resetLeft() != Long.MIN_VALUE);
        }

        @Override protected Content live() {
            return card(freezeLeft(), resetLeft());
        }

        @Override protected Content sample() {
            return card(1800, 23_400);
        }

        @Override public List<Content> states() {
            return List.of(card(-1, -500));
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            long f = freezeLeft(), r = resetLeft();
            if (f >= 0) out.add(new Row("Freeze", String.format(Locale.ROOT, "%.1fs", f / 1000.0), 0));
            if (r != Long.MIN_VALUE) out.add(new Row("Next reset", r <= 0 ? "Now!" : String.format(Locale.ROOT, "%.1fs", r / 1000.0), 0));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Freeze", "1.8s", 0), new Row("Next reset", "23.4s", 0));
        }
    };

    static HudElement card() {
        return CARD;
    }

    public static void register() {
        Features.add(new Feature(ID, "Crimson Isle", "Ashfang", "In the Ashfang fight: how long you are frozen and when he pulls his blazes back, the blazes by colour, Blazing Souls and Gravity Orbs marked.", "SkyHanni: Ashfang")
                .when(Crimson::crimson).defaultOff().tick(Ashfang::tick).world(Ashfang::draw).left(Ashfang::reset)
                .option(Option.bool("crimson_ashfang.freeze", "Freeze time", "How long Ashfang blocks your abilities.", true))
                .option(Option.bool("crimson_ashfang.reset", "Reset time", "When Ashfang pulls his underlings back.", true))
                .option(Option.bool("crimson_ashfang.blazes", "Blazes by colour", "", true))
                .option(Option.bool("crimson_ashfang.souls", "Blazing Souls", "", true))
                .option(Option.color("crimson_ashfang.souls_color", "Blazing Soul colour", "", 0xF555FF55))
                .option(Option.bool("crimson_ashfang.orbs", "Gravity Orbs", "", true))
                .option(Option.color("crimson_ashfang.orbs_color", "Gravity Orb colour", "", 0x78FF5555))
                .option(Option.bool("crimson_ashfang.hide_names", "Hide full health names", "The names of blazes that are not hurt.", true))
                .option(Option.bool("crimson_ashfang.hide_particles", "Hide particles", "All particles around the boss.", true)));
        ChatBus.register(ChatBus.OBSERVE + 33, "crimson_ashfang", Features.get(ID), Ashfang::onChat);
    }
}
