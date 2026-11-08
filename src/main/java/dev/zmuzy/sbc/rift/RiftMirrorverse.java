// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import com.google.gson.JsonElement;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// mirrorverse rooms, from skyhanni rift/area/mirrorverse: DanceRoomHelper.kt (steps from
// DanceRoomInstructions.json, count from the room sounds) + CraftRoomHolographicMob.kt
public final class RiftMirrorverse {
    public static final String DANCE = "rift_dance_room", CRAFT = "rift_craft_room";
    private static final AABB DANCE_ROOM = new AABB(-267, 32, -110, -260, 40, -102);
    private static final AABB CRAFT_ROOM = new AABB(-117, 51, -128, -108, 58, -106);
    private static final double WALL_Z = -116.5;

    private static List<String> instructions;
    private static int index, countdownTicks;
    private static boolean inRoom;

    private record Hologram(AABB box, String label) {}

    private static final List<Hologram> HOLOGRAMS = new ArrayList<>();
    private static boolean inCraftRoom;

    private RiftMirrorverse() {}

    private static List<String> instructions() {
        if (instructions == null) {
            var o = Repo.constant("DanceRoomInstructions.json");
            if (o == null) return List.of();
            List<String> out = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("instructions")) out.add(e.getAsString());
            instructions = out;
        }
        return instructions;
    }

    // from skyhanni String.addColor: color of one word of a step
    static String colored(String word) {
        String w = word.isEmpty() ? word : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1);
        String code = switch (w) {
            case "Move", "Stand" -> "§e";
            case "Sneak" -> "§9";
            case "Jump" -> "§b";
            case "Punch" -> "§d";
            default -> "§f";
        };
        return code + w;
    }

    static String step(String line) {
        StringBuilder out = new StringBuilder();
        for (String w : line.split(" ")) {
            if (out.length() > 0) out.append(' ');
            out.append(colored(w));
        }
        return out.toString();
    }

    // from skyhanni countdownStr
    static String countdown(int ticks) {
        if (ticks <= 0) return "";
        int ms = ticks * 50;
        return String.format(Locale.ROOT, "§f%01d:%03d", ms / 1000, ms % 1000);
    }

    private static boolean success(SoundHooks.Sound s) {
        return s.name().equals("block.note_block.bass") && (s.pitch() == 0.6984127f || s.pitch() == 0.52380955f) && s.volume() == 1f;
    }

    private static boolean failure(SoundHooks.Sound s) {
        return (s.name().equals("entity.player.burp") && s.volume() == 0.8f) || (s.name().equals("entity.player.levelup") && s.pitch() == 1.8412699f && s.volume() == 1f);
    }

    private static boolean onSound(SoundHooks.Sound s) {
        if (!inRoom || !Features.on(DANCE)) return false;
        if (success(s)) {
            index++;
            countdownTicks = 20;
        } else if (failure(s) && (index > 0 || countdownTicks > 0)) {
            index = 0;
            countdownTicks = 0;
        }
        return false;
    }

    private static void tickDance(Minecraft mc) {
        if (mc.player == null) return;
        if (mc.player.tickCount % 10 == 0) inRoom = RiftApi.inMirrorVerse() && DANCE_ROOM.contains(mc.player.position());
        if (!inRoom) {
            index = 0;
            countdownTicks = 0;
        } else if (countdownTicks > 0) countdownTicks--;
    }

    private static RichCard.Content dance(List<String> steps, int now, int ticks, int lines) {
        List<RichCard.Line> out = new ArrayList<>();
        if (steps.isEmpty()) {
            out.add(RichCard.Line.row("Steps", "§cNo data").raw());
            return new RichCard.Content("", out);
        }
        for (int i = now; i <= now + lines && i < steps.size(); i++) {
            String label = i == now ? "Now" : i == now + 1 ? "Next" : "Later";
            String value = step(steps.get(i)) + (i == now && ticks > 0 ? " " + countdown(ticks) : "");
            out.add(RichCard.Line.row(label, value).key(label + " " + (i - now)).raw());
        }
        return new RichCard.Content(Math.min(now, steps.size()) + "/" + steps.size(), out);
    }

    private static int lines() {
        Feature f = Features.get(DANCE);
        Option o = f == null ? null : f.option("rift_dance_room.lines");
        return o == null ? 3 : o.integer();
    }

    private static final HudElement DANCE_CARD = new RichCard(DANCE, "Dance Room", "Rift", Zone.TOP_LEFT, 54, DANCE) {
        @Override public boolean visible() {
            return Rift.rift() && inRoom;
        }

        @Override protected Content live() {
            return dance(instructions(), index, countdownTicks, lines());
        }

        @Override protected Content sample() {
            return dance(List.of("move", "sneak", "stand", "sneak jump", "punch"), 0, 16, 3);
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            List<String> steps = instructions();
            for (int i = index; i <= index + lines() && i < steps.size(); i++) out.add(new Row(i == index ? "Now" : i == index + 1 ? "Next" : "Later", step(steps.get(i)), 0));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Now", step("move"), 0), new Row("Next", step("sneak"), 0));
        }
    };

    // from skyhanni LorenzVec.mirror
    static Vec3 mirror(Vec3 v) {
        return v.add(0, 0, (WALL_Z - v.z) * 2);
    }

    private static void tickCraft(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        inCraftRoom = RiftApi.inMirrorVerse() && CRAFT_ROOM.contains(mc.player.position());
        HOLOGRAMS.clear();
        if (!inCraftRoom) return;
        boolean name = RiftScan.on(CRAFT, "rift_craft_room.name", true), health = RiftScan.on(CRAFT, "rift_craft_room.health", true);
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(25))) {
            if (!(e instanceof Zombie || e instanceof Slime || e instanceof CaveSpider) || !CRAFT_ROOM.contains(e.position()) || e.getZ() > WALL_Z) continue;
            AABB b = e.getBoundingBox();
            Vec3 m = mirror(e.position());
            double dz = m.z - e.getZ();
            String label = (name ? "§a" + e.getDisplayName().getString() + " " : "") + (health ? String.format(Locale.ROOT, "§c%.1f♥", e.getHealth()) : "");
            HOLOGRAMS.add(new Hologram(b.move(0, 0, dz), label.trim()));
        }
    }

    private static void drawCraft(WorldDraw w) {
        for (Hologram h : HOLOGRAMS) {
            w.box(h.box(), 0x8055FFFF, true, false);
            if (!h.label().isEmpty()) w.dynamicText(new Vec3(h.box().getCenter().x, h.box().maxY + 0.5, h.box().getCenter().z), h.label(), 1, 0);
        }
    }

    // from skyhanni onPlayerRender, onCheckRender + the parkours hide options
    public static boolean hidden(Entity e) {
        if (!(e instanceof Player p) || p == Minecraft.getInstance().player || p.getUUID().version() == 2) return false;
        if (inRoom && RiftScan.on(DANCE, "rift_dance_room.hide_players", false)) return true;
        if (inCraftRoom && RiftScan.on(CRAFT, "rift_craft_room.hide_players", false)) return true;
        return (RiftParkours.inMirrorverseParkour() || RiftApi.inRiftRace) && RiftScan.on(Rift.PARKOURS, "rift_parkours.hide_players", false);
    }

    // from skyhanni onTitleReceived
    public static boolean hidesTitle() {
        return inRoom && Features.on(DANCE) && RiftScan.on(DANCE, "rift_dance_room.hide_title", false);
    }

    public static void register() {
        Features.add(new Feature(DANCE, "Rift", "Dance Room", "In the Mirrorverse dance room, the step to do now, the next ones and the time left on the step.", "SkyHanni: Dance Room Helper")
                .when(Rift::rift).tick(RiftMirrorverse::tickDance)
                .left(() -> {
                    inRoom = false;
                    index = 0;
                    countdownTicks = 0;
                })
                .option(Option.integer("rift_dance_room.lines", "Later steps", "How many steps to show after the next one.", 3, 1, 10))
                .option(Option.bool("rift_dance_room.hide_players", "Hide other players", "", false))
                .option(Option.bool("rift_dance_room.hide_title", "Hide the room's titles", "Instructions, It's happening! and Keep it up!", false)));
        Features.add(new Feature(CRAFT, "Rift", "Crafting Room", "Shows the mob on the far side of the Mirrorverse crafting room at its mirrored spot on your side, with its name and health.", "SkyHanni: Craft Room Holographic Mob")
                .when(Rift::rift).tick(RiftMirrorverse::tickCraft).world(RiftMirrorverse::drawCraft)
                .left(() -> {
                    inCraftRoom = false;
                    HOLOGRAMS.clear();
                })
                .option(Option.bool("rift_craft_room.name", "Name", "", true))
                .option(Option.bool("rift_craft_room.health", "Health", "", true))
                .option(Option.bool("rift_craft_room.hide_players", "Hide other players", "", false)));
        HudLayout.add(DANCE_CARD);
        SoundHooks.listen(RiftMirrorverse::onSound);
    }
}
