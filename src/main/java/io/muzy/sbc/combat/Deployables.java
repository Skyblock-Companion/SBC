// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// power orbs, lanterns, black hole, umberella in range. from skyhanni Deployable, DeployableDisplay, DeployableReminder
// totem of corruption has its own display in the fishing pack
// their reminder checks FishingApi.isTrophyFishing first, here any trophy fish caught counts
public final class Deployables {
    public static final String ID = "deployables", REMINDER = "deployable_reminder";
    private static final Patterns.P TROPHY = Patterns.p("SH.fishing.trophy.trophyfish");

    enum Type {FLUX, LANTERN, BLACK_HOLE, UMBERELLA}

    // from skyhanni Deployable
    enum Item {
        RADIANT("radiant", "§aRadiant", 18, Type.FLUX, 1, false), MANA_FLUX("mana-flux", "§9Mana Flux", 18, Type.FLUX, 2, false), OVERFLUX("overflux", "§5Overflux", 18, Type.FLUX, 3, false), PLASMAFLUX("plasmaflux", "§d§lPlasmaflux", 20, Type.FLUX, 4, false),
        DWARVEN_LANTERN("dwarven-lantern", "§fDwarven Lantern", 30, Type.LANTERN, 1, false), MITHRIL_LANTERN("mithril-lantern", "§aMithril Lantern", 30, Type.LANTERN, 2, false), TITANIUM_LANTERN("titanium-lantern", "§9Titanium Lantern", 30, Type.LANTERN, 3, false),
        GLACITE_LANTERN("glacite-lantern", "§5Glacite Lantern", 30, Type.LANTERN, 4, true), WILL_O_WISP("will-o-wisp", "§6§lWill-o'-wisp", 30, Type.LANTERN, 5, true),
        BLACK_HOLE("black-hole", "§5Black Hole", 20, Type.BLACK_HOLE, 0, false), UMBERELLA("umberella", "§9Umberella", 30, Type.UMBERELLA, 0, false);

        final Patterns.P pattern;
        final String shown;
        final int range, tier;
        final Type type;
        final boolean fullShaft;

        Item(String key, String shown, int range, Type type, int tier, boolean fullShaft) {
            this.pattern = Patterns.p("SH.combat.deployable." + key);
            this.shown = shown;
            this.range = range;
            this.type = type;
            this.tier = tier;
            this.fullShaft = fullShaft;
        }
    }

    private static final class Live {
        ArmorStand entity;
        long expiry;
    }

    private static final Map<Item, Live> LIVE = new EnumMap<>(Item.class);
    private static long warnSlayerAt, warnShaftAt, warnFishAt, warningUntil;
    private static String warning;
    private static String wasIsland = "";

    private Deployables() {}

    private static boolean shaftBuff(Item i) {
        return i.fullShaft && "MINESHAFT".equals(GameState.island);
    }

    private static boolean inRange(Item i, ArmorStand e, Minecraft mc) {
        return shaftBuff(i) || i.range > e.position().distanceTo(mc.player.position());
    }

    private static boolean isActive(Item i, Live l, Minecraft mc) {
        if (System.currentTimeMillis() > l.expiry) return false;
        if (shaftBuff(i)) return true;
        return l.entity != null && !l.entity.isRemoved() && inRange(i, l.entity, mc);
    }

    // does something of this kind work here rn
    private static boolean activeOf(Type t, Minecraft mc) {
        for (var e : LIVE.entrySet()) if (e.getKey().type == t && isActive(e.getKey(), e.getValue(), mc)) return true;
        return false;
    }

    private static boolean shows(Type t) {
        return switch (t) {
            case FLUX -> Combat.opt(ID, "flux");
            case LANTERN -> Combat.opt(ID, "lantern");
            case BLACK_HOLE -> Combat.opt(ID, "black_hole");
            case UMBERELLA -> Combat.opt(ID, "umberella");
        };
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (mc.player.tickCount % 10 == 0) {
            for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(32), ArmorStand::hasCustomName)) {
                String clean = GameState.strip(a.getName().getString());
                for (Item i : Item.values()) {
                    Matcher m = i.pattern.matcher(clean);
                    if (!m.matches() || !inRange(i, a, mc)) continue;
                    long time = now + Integer.parseInt(m.group("time")) * 1000L;
                    Live l = LIVE.computeIfAbsent(i, k -> new Live());
                    if (l.expiry > time && isActive(i, l, mc)) continue;
                    l.entity = a;
                    l.expiry = time;
                }
            }
            LIVE.entrySet().removeIf(e -> !isActive(e.getKey(), e.getValue(), mc));
        }
        if (!Features.on(REMINDER)) return;
        long delay = Combat.integer(REMINDER, "delay", 5) * 1000L;
        if (warnSlayerAt != 0 && now - warnSlayerAt >= delay) {
            warnSlayerAt = 0;
            if (Combat.opt(REMINDER, "slayer") && SlayerApi.inBossFight() && !activeOf(Type.FLUX, mc)) warn("Place Down Power Orb!");
        }
        if (!GameState.island.equals(wasIsland)) {
            wasIsland = GameState.island;
            if ("MINESHAFT".equals(GameState.island)) warnShaftAt = now;
        }
        if (warnShaftAt != 0 && now - warnShaftAt >= delay) {
            warnShaftAt = 0;
            if (Combat.opt(REMINDER, "mineshaft") && "MINESHAFT".equals(GameState.island) && !activeOf(Type.LANTERN, mc)) warn("Place Down Lantern!");
        }
        if (warnFishAt != 0) {
            warnFishAt = 0;
            if (Combat.opt(REMINDER, "trophy") && !activeOf(Type.UMBERELLA, mc)) warn("Place Down Umberella!");
        }
        if (warning != null && now > warningUntil) warning = null;
    }

    private static void warn(String text) {
        warning = text;
        warningUntil = System.currentTimeMillis() + (long) (Combat.integer(REMINDER, "duration", 3) * 1000L);
        Combat.title("§4§l" + text, Combat.integer(REMINDER, "duration", 3) * 20);
        Combat.play("block.note_block.bass", 1f, 0.5f);
    }

    private static void onState(SlayerApi.State s) {
        if (s == SlayerApi.State.BOSS_FIGHT && Features.on(REMINDER) && warnSlayerAt == 0) warnSlayerAt = System.currentTimeMillis();
    }

    private static void left() {
        LIVE.clear();
        warnSlayerAt = warnShaftAt = warnFishAt = 0;
        warning = null;
        wasIsland = "";
    }

    private static List<RichCard.Line> lines(Map<Item, Long> left) {
        List<RichCard.Line> out = new ArrayList<>();
        boolean highest = Combat.opt(ID, "highest_only");
        for (var e : left.entrySet()) {
            Item i = e.getKey();
            if (!shows(i.type)) continue;
            boolean better = false;
            for (Item o : left.keySet()) if (o.type == i.type && o.tier > i.tier) better = true;
            if (highest && better) continue;
            out.add(RichCard.Line.row(i.shown, "§e" + Combat.clock(e.getValue())).key(io.muzy.sbc.ui.hud.RowText.plain(i.shown)).raw());
        }
        return out;
    }

    private static final HudElement CARD = new RichCard(ID, "Deployables", "Combat", Zone.TOP_RIGHT, 40, ID) {
        @Override public boolean visible() {
            return !LIVE.isEmpty() && !live().lines().isEmpty();
        }

        @Override public String heading() {
            return "";
        }

        @Override protected Content live() {
            long now = System.currentTimeMillis();
            Map<Item, Long> left = new EnumMap<>(Item.class);
            LIVE.forEach((i, l) -> left.put(i, l.expiry - now));
            return new Content("", lines(left));
        }

        @Override protected Content sample() {
            Map<Item, Long> left = new EnumMap<>(Item.class);
            left.put(Item.PLASMAFLUX, 58_000L);
            left.put(Item.GLACITE_LANTERN, 214_000L);
            return new Content("", lines(left));
        }

        @Override protected List<Row> classicRows() {
            long now = System.currentTimeMillis();
            List<Row> out = new ArrayList<>();
            LIVE.forEach((i, l) -> out.add(new Row(i.shown, Combat.clock(l.expiry - now), Theme.current().text())));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("§d§lPlasmaflux", "58s", Theme.current().text()));
        }
    };

    static void register() {
        Features.add(new Feature("deployables_tick", "Combat", "Deployable reading", "Keeps track of the deployables in range, for the card and the reminder.", "").alwaysOn().when(Combat::sb).tick(Deployables::tick).left(Deployables::left));
        Features.add(new Feature(ID, "Combat", "Deployables", "The power orb, lantern, Black Hole and Umberella in your range, with the time they have left.", "SkyHanni: Deployable Display").when(Combat::sb)
                .option(Option.bool("deployables.flux", "Power orbs", "", true))
                .option(Option.bool("deployables.lantern", "Lanterns", "", true))
                .option(Option.bool("deployables.black_hole", "Black Hole", "", true))
                .option(Option.bool("deployables.umberella", "Umberella", "", true))
                .option(Option.bool("deployables.highest_only", "Only the highest of a kind", "", true)));
        Features.add(new Feature(REMINDER, "Combat", "Deployable reminder", "When you start a fight, a Mineshaft or trophy fishing without what belongs to it.", "SkyHanni: Missing Deployable Warning").when(Combat::sb)
                .option(Option.bool("deployable_reminder.slayer", "Slayer boss: a power orb", "", true))
                .option(Option.bool("deployable_reminder.mineshaft", "Mineshaft: a lantern", "", true))
                .option(Option.bool("deployable_reminder.trophy", "Trophy fishing: an Umberella", "", true))
                .option(Option.integer("deployable_reminder.delay", "Wait (seconds)", "", 5, 0, 10))
                .option(Option.integer("deployable_reminder.duration", "Show for (seconds)", "", 3, 1, 10)));
        SlayerApi.onState(Deployables::onState);
        ChatBus.register(ChatBus.OBSERVE + 22, "deployables", null, line -> {
            // skyhanni TrophyFishMessages catch line
            if (Combat.sb() && Features.on(REMINDER) && TROPHY.matcher(io.muzy.sbc.core.LegacyText.of(line.component())).matches()) warnFishAt = System.currentTimeMillis();
            return ChatBus.PASS;
        });
        HudLayout.add(CARD);
    }
}
