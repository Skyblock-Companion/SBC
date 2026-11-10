// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// warning, alert and SOS flares. from skyhanni FlareDisplay: skull seen on a stand, 3 min from when it spawned
// skipped the full screen flash and the 40 block sphere, only a ring around you bc our world drawing has no sphere
public final class Flares {
    public static final String ID = "flare_display";
    private static final long MAX_MS = 180_000;

    // skyhanni FlareType, best first
    enum Type {
        SOS("§5SOS Flare", "+125%", "FLARE_SOS", "sos_color", 0xFF9F0005), ALERT("§9Alert Flare", "+50%", "FLARE_ALERT", "alert_color", 0xFF009F89), WARNING("§aWarning Flare", null, "FLARE_WARNING", "warning_color", 0xFF1DFF88);

        final String shown, buff, skull, colorKey;
        final int color;

        Type(String shown, String buff, String skull, String colorKey, int color) {
            this.shown = shown;
            this.buff = buff;
            this.skull = skull;
            this.colorKey = colorKey;
            this.color = color;
        }
    }

    private record Flare(Type type, ArmorStand entity, Vec3 at) {}

    private static final List<Flare> FLARES = new ArrayList<>();

    private Flares() {}

    private static long left(Flare f) {
        return MAX_MS - f.entity().tickCount * 50L;
    }

    private static Flare best() {
        for (Type t : Type.values()) for (Flare f : FLARES) if (f.type() == t) return f;
        return null;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount % 10 == 0) {
            for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(64))) {
                if (a.tickCount * 50L > MAX_MS || known(a)) continue;
                String tex = DungeonMarks.headTexture(a.getItemBySlot(EquipmentSlot.HEAD));
                if (tex.isEmpty()) continue;
                for (Type t : Type.values()) if (tex.equals(DungeonMarks.skull(t.skull))) FLARES.add(new Flare(t, a, a.position()));
            }
        }
        if (mc.player.tickCount % 20 != 0) return;
        FLARES.removeIf(f -> !f.entity().isAlive());
        int warn = Combat.integer(ID, "warn_seconds", 5);
        for (Type t : Type.values()) {
            for (Flare f : FLARES) {
                if (f.type() != t) continue;
                long s = left(f) / 1000;
                if (s < 0 || s > warn) break;
                String msg = t.shown + " §eexpires in: §b" + s + "s";
                String how = Combat.choice(ID, "alert", "Chat");
                if (how.contains("Chat")) Combat.say(msg);
                if (how.contains("Title")) Combat.title(msg, 20);
                if (Combat.opt(ID, "sound")) Combat.pling(1f);
                break;
            }
        }
    }

    private static boolean known(ArmorStand a) {
        for (Flare f : FLARES) if (f.entity() == a) return true;
        return false;
    }

    private static void draw(WorldDraw w) {
        boolean world = !"Card".equals(Combat.choice(ID, "where", "Card"));
        boolean ring = Combat.opt(ID, "ring");
        if (!world && !ring) return;
        for (Flare f : FLARES) {
            if (world && Combat.seen(f.at().add(0, 1, 0), 64)) {
                w.dynamicText(f.at(), f.type().shown, 1.5, 0);
                w.dynamicText(f.at(), "§b" + Combat.clock(left(f)), 1.5, -1);
            }
            if (ring) {
                int color = Combat.color(ID, f.type().colorKey, f.type().color);
                Vec3 last = null;
                for (int i = 0; i <= 48; i++) {
                    double a = i * Math.PI * 2 / 48;
                    Vec3 p = f.at().add(Math.cos(a) * 40, 0, Math.sin(a) * 40);
                    if (last != null) w.line(last, p, color, 2, false);
                    last = p;
                }
            }
        }
    }

    private static void left() {
        FLARES.clear();
    }

    private static RichCard.Content card(String name, String time, String buff) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row(name, time).raw());
        if (buff != null) out.add(RichCard.Line.row("Mana regen", "§b" + buff).raw());
        return new RichCard.Content("", out);
    }

    private static final HudElement CARD = new RichCard(ID, "Flare", "Combat", Zone.TOP_RIGHT, 42, ID) {
        @Override public boolean visible() {
            return best() != null && !"World".equals(Combat.choice(ID, "where", "Card"));
        }

        @Override public String heading() {
            return "";
        }

        @Override protected Content live() {
            Flare f = best();
            if (f == null) return new Content("", List.of());
            long l = left(f);
            return card(f.type().shown, l < 0 ? "§eSoon" : "§b" + Combat.clock(l), Combat.opt(ID, "show_buff") ? f.type().buff : null);
        }

        @Override protected Content sample() {
            return card("§5SOS Flare", "§b2m 14s", "+125%");
        }

        @Override protected List<Row> classicRows() {
            Flare f = best();
            return f == null ? List.of() : List.of(new Row(f.type().shown, Combat.clock(left(f)), Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("§5SOS Flare", "2m 14s", Theme.current().text()));
        }
    };

    static void register() {
        Features.add(new Feature(ID, "Combat", "Flares", "The flare nearest in power, its time and buff, and a warning before it ends.", "SkyHanni: Flare Display").when(Combat::sb).defaultOff().tick(Flares::tick).world(Flares::draw).left(Flares::left)
                .option(Option.choice("flare_display.where", "Where", "", "Card", "Card", "World", "Both"))
                .option(Option.bool("flare_display.show_buff", "Show the buff", "", true))
                .option(Option.integer("flare_display.warn_seconds", "Warn this many seconds before", "", 5, 0, 60))
                .option(Option.choice("flare_display.alert", "Warning as", "", "Chat", "None", "Chat", "Title", "Chat and Title"))
                .option(Option.bool("flare_display.sound", "Warning sound", "", false))
                .option(Option.bool("flare_display.hide_particles", "Hide its flames", "", false))
                .option(Option.bool("flare_display.ring", "Ring of its range", "40 blocks around it.", false))
                .option(Option.color("flare_display.warning_color", "Warning Flare colour", "", 0xFF1DFF88))
                .option(Option.color("flare_display.alert_color", "Alert Flare colour", "", 0xFF009F89))
                .option(Option.color("flare_display.sos_color", "SOS Flare colour", "", 0xFF9F0005)));
        CombatMarks.particles(() -> Features.on(ID) && Combat.opt(ID, "hide_particles") && !FLARES.isEmpty(), (type, x, y, z) -> {
            if (!type.equals("flame")) return false;
            for (Flare f : FLARES) if (f.at().distanceToSqr(x, y, z) < 2.5 * 2.5) return true;
            return false;
        });
        HudLayout.add(CARD);
    }
}
