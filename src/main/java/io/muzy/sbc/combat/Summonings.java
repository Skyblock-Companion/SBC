// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.dungeon.DungeonMarks;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// summoning ring. from skyhanni SummoningMobManager + SummoningSoulsName, one switch (starts off) with the
// parts under it
public final class Summonings {
    public static final String ID = "summonings";
    private static final Patterns.P SPAWN = Patterns.p("SH.summoning.mobs.spawn"), DESPAWN = Patterns.p("SH.summoning.mobs.despawn"), RECALL = Patterns.p("SH.summoning.mobs.seraphrecall");

    private static final List<Mobs.Mob> MOBS = new ArrayList<>();
    private static final Map<ArmorStand, String> SOULS = new HashMap<>();
    private static final Map<Integer, Vec3> lastAt = new HashMap<>();
    private static final Map<Integer, String> lastName = new HashMap<>();
    private static long lastChat;

    private Summonings() {}

    private static void onSpawn(Mobs.Mob m) {
        var mc = Minecraft.getInstance();
        if (m.category == Mobs.Category.SUMMON && mc.getUser().getName().equals(m.owner())) MOBS.add(m);
    }

    private static void onGone(Mobs.Mob m) {
        if (!MOBS.remove(m) || !Features.on(ID) || !Combat.opt(ID, "messages")) return;
        var p = Minecraft.getInstance().player;
        if (p == null || m.base.position().distanceTo(p.position()) >= 48) return;
        if (System.currentTimeMillis() - lastChat > 2000) Combat.say("Your Summoning Mob just §cdied!");
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!Combat.opt(ID, "messages")) return ChatBus.PASS;
        String m = LegacyText.of(line.component());
        if (SPAWN.matcher(m).matches()) return ChatBus.CANCEL;
        if (DESPAWN.matcher(m).matches() || RECALL.matcher(m).matches()) {
            lastChat = System.currentTimeMillis();
            return ChatBus.CANCEL;
        }
        return ChatBus.PASS;
    }

    // skyhanni SummoningSoulsName.check: a soul gets the name of the mob that died nearest to it
    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 5 != 0 || !Combat.opt(ID, "souls")) return;
        String soul = DungeonMarks.skull("SUMMONING_SOUL");
        var around = mc.player.getBoundingBox().inflate(48);
        for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, around)) {
            if (SOULS.containsKey(a) || !DungeonMarks.headTexture(a.getItemBySlot(EquipmentSlot.HEAD)).equals(soul)) continue;
            int best = -1;
            double dist = Double.MAX_VALUE;
            for (var e : lastAt.entrySet()) {
                double d = e.getValue().distanceTo(a.position());
                if (d < dist) {
                    dist = d;
                    best = e.getKey();
                }
            }
            if (lastName.containsKey(best)) SOULS.put(a, lastName.get(best));
        }
        for (Mob m : mc.level.getEntitiesOfClass(Mob.class, around)) {
            ArmorStand tag = Combat.tagWith(m, 2, "§c❤");
            if (tag == null) continue;
            String name = LegacyText.of(tag.getName(), true, false);
            if (name.contains("§e0")) continue;
            lastAt.put(m.getId(), m.position());
            lastName.put(m.getId(), name);
        }
        SOULS.keySet().removeIf(a -> !a.isAlive());
        if (lastAt.size() > 512) {
            lastAt.clear();
            lastName.clear();
        }
    }

    private static void draw(WorldDraw w) {
        if (Combat.opt(ID, "souls")) for (var e : SOULS.entrySet()) if (Combat.seen(e.getKey(), 64, 2.5)) w.label(e.getValue(), e.getKey().position().add(0, 2.5, 0), false);
    }

    private static void left() {
        MOBS.clear();
        SOULS.clear();
        lastAt.clear();
        lastName.clear();
    }

    private static RichCard.Content card(List<RichCard.Line> lines, int n) {
        return new RichCard.Content("§7" + n, lines);
    }

    private static final HudElement CARD = new RichCard(ID, "Summoning mobs", "Combat", Zone.TOP_LEFT, 45, ID) {
        @Override public boolean visible() {
            return !MOBS.isEmpty() && Combat.opt(ID, "display");
        }

        @Override protected Content live() {
            List<Line> out = new ArrayList<>();
            for (Mobs.Mob m : MOBS) {
                long hp = (long) Combat.health(m.base), max = Combat.maxHealth(m.base);
                out.add(Line.row("§a" + m.name, Combat.percentColor(hp, max) + Combat.shortNumber(hp) + "§2/" + Combat.shortNumber(max) + "§c❤").raw());
            }
            return card(out, out.size());
        }

        @Override protected Content sample() {
            return card(List.of(Line.row("§aTank Zombie", "§a8.4k§2/10k§c❤").raw(), Line.row("§aSuper Tank Zombie", "§61.2M§2/3M§c❤").raw()), 2);
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("Summoning mobs", String.valueOf(MOBS.size()), Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Summoning mobs", "2", Theme.current().text()));
        }
    };

    static void register() {
        Feature f = Features.add(new Feature(ID, "Combat", "Summoning mobs", "Your summoned mobs and their health, what a soul on the ground was, and a line when one dies.", "SkyHanni: Summonings").when(Combat::sb).defaultOff().tick(Summonings::tick).world(Summonings::draw).left(Summonings::left)
                .option(Option.bool("summonings.display", "Card with their health", "", true))
                .option(Option.bool("summonings.souls", "Name over a soul", "", true))
                .option(Option.bool("summonings.color", "Glow on your mobs", "", true))
                .option(Option.bool("summonings.hide_nametag", "Hide their name tags", "", false))
                .option(Option.bool("summonings.messages", "Shorter chat", "Hides the spawn and despawn lines, tells when one dies.", false)));
        ChatBus.register(ChatBus.FILTER, ID, f, Summonings::onChat);
        Mobs.onSpawn(Summonings::onSpawn);
        Mobs.onGone(Summonings::onGone);
        CombatMarks.glowing(mark -> {
            if (Features.on(ID) && Combat.opt(ID, "color")) for (Mobs.Mob m : MOBS) CombatMarks.seenGlow(mark, m.base, 0x55FF55);
        });
        CombatMarks.hiding(() -> Features.on(ID) && Combat.opt(ID, "hide_nametag") && !MOBS.isEmpty(), e -> {
            if (!(e instanceof ArmorStand)) return false;
            for (Mobs.Mob m : MOBS) if (m.stand == e) return true;
            return false;
        });
        HudLayout.add(CARD);
    }
}
