// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.ParticleHooks;
import io.muzy.sbc.render.WorldDraw;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
//? if >=26.3 {
import net.minecraft.world.entity.monster.Enderman;
//?} else {
/*import net.minecraft.world.entity.monster.EnderMan;
*///?}
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the end. from skyhanni DragonFeatures (weight in chat + card, dragons + protector, superior title) and DragonFightAPI
// skyblocker TheEnd, EndHudWidget, EndStatsBestiaryUpdater, EnderNodes
// dragon reading is its own always on listener bc damage indicator and 2 features ask it. top damage on the
// weight card comes from the tabs dragon widget
public final class EndIsland {
    public static final String WEIGHT = "dragon_weight", ZEALOTS = "end_zealots", PROTECTOR = "end_protector", NODES = "ender_node_helper";
    private static final Patterns.P SPAWN = Patterns.p("SH.combat.boss.dragon.1.chat.spawn"), EYE_ON = Patterns.p("SH.combat.boss.dragon.1.chat.eye.placed.you"), EYE_OFF = Patterns.p("SH.combat.boss.dragon.1.chat.eye.removed.you"),
            DRAGON_END = Patterns.p("SH.combat.boss.dragon.1.chat.end.boss"), PLACE = Patterns.p("SH.combat.boss.dragon.1.chat.end.place"), POSITION = Patterns.p("SH.combat.boss.dragon.1.chat.end.position"),
            PROTECTOR_END = Patterns.p("SH.combat.boss.protector.1.chat.end.boss"), PROTECTOR_ZEALOTS = Patterns.p("SH.combat.boss.protector.1.chat.end.zealot"), TAB_DAMAGE = Patterns.p("SH.combat.boss.dragon.1.tablist-nocolor.fight.player");
    private static final Pattern HP_LINE = Pattern.compile("^Dragon HP: ([\\d,]+).*$"), DAMAGE_LINE = Pattern.compile("^Your Damage: ([\\d,.]+)$");
    // from skyblocker TheEnd
    private static final Pattern TREMOR = Pattern.compile("^You feel a tremor from beneath the earth!$"), RISES = Pattern.compile("^The ground begins to shake as an End Stone Protector rises from below!$"),
            FIGHT = Pattern.compile("^BEWARE - An (?:Endstone|End Stone) Protector has risen!$"), SPECIAL = Pattern.compile("^A special Zealot has spawned nearby!$"), KILLS = Pattern.compile("Kills: ([0-9,]+)");
    private static final int ALL_ZEALOTS_SLOT = 4, SPECIAL_ZEALOT_SLOT = 22;

    private record Place(int x, int z, String name) {}

    private static final List<Place> PLACES = List.of(new Place(-649, -219, "Left"), new Place(-644, -269, "Front"), new Place(-689, -273, "Center"), new Place(-727, -284, "Back"), new Place(-639, -328, "Right Front"), new Place(-678, -332, "Right Back"));

    // the dragon fight
    private static String dragonType = "";
    private static int eyes;
    private static String endType;
    private static double endTop, endDamage;
    private static int endPlace;
    private static long dragonHp = -1;
    private static double currentDamage, topDog;
    private static Integer whereIRank;
    private static boolean dragonUp;
    // the protector and zealots
    private static Place place;
    private static int stage;
    private static final Set<java.util.UUID> hitZealots = new HashSet<>();
    // nodes
    private static final Map<BlockPos, Node> NODE_MAP = new HashMap<>();

    private EndIsland() {}

    public static String dragonType() {
        return dragonType;
    }

    public static long dragonHp() {
        return dragonHp;
    }

    // weight

    static int placementWeight(int place) {
        return switch (place) {
            case -1 -> 10;
            case 1 -> 200;
            case 2 -> 175;
            case 3 -> 150;
            case 4 -> 125;
            case 5 -> 110;
            case 6, 7, 8 -> 100;
            case 9, 10 -> 90;
            case 11, 12 -> 80;
            default -> 70;
        };
    }

    static double dragonWeight(int eyes, int place, double first, double yours) {
        return placementWeight(yours == 0 ? -1 : place) + 100 * (eyes + yours / (first != 0 ? first : 1));
    }

    static double protectorWeight(int zealots, int place, double first, double yours) {
        return placementWeight(yours == 0 ? -1 : place) + 50 * (yours / (first != 0 ? first : 1)) + Math.min(zealots, 100);
    }

    private static void printWeight(double weight) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal(" ".repeat(30) + String.format(Locale.ROOT, "§eYour Weight: §a%,d", Math.round(weight))));
    }

    private static double number(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!Combat.island("THE_END")) return ChatBus.PASS;
        String m = LegacyText.of(line.component()), plain = line.plain().trim();
        protectorLine(plain);
        Matcher x = SPAWN.matcher(m);
        if (x.find()) {
            dragonType = x.group("dragon");
            dragonUp = true;
            if (Features.on(WEIGHT) && Combat.opt(WEIGHT, "superior") && dragonType.equals("Superior")) Combat.title("§6Superior Dragon Spawned!", 30);
            return ChatBus.PASS;
        }
        if (EYE_ON.matcher(m).find()) eyes++;
        else if (EYE_OFF.matcher(m).find()) eyes--;
        else if (DRAGON_END.matcher(m).find()) {
            endType = "dragon";
            endTop = endDamage = 0;
            endPlace = 0;
        } else if (PROTECTOR_END.matcher(m).find()) {
            endType = "protector";
            endTop = endDamage = 0;
            endPlace = 0;
        } else if (endType != null) {
            if ((x = PLACE.matcher(m)).find()) {
                if (x.group("position").equals("1")) endTop = number(x.group("damage"));
            } else if ((x = POSITION.matcher(m)).find()) {
                endPlace = Integer.parseInt(x.group("position"));
                endDamage = number(x.group("damage"));
                if (endType.equals("dragon")) {
                    if (Features.on(WEIGHT) && Combat.opt(WEIGHT, "chat")) printWeight(dragonWeight(eyes, endPlace, endTop, endDamage));
                    endFight();
                }
            } else if ((x = PROTECTOR_ZEALOTS.matcher(m)).find() && endType.equals("protector")) {
                if (Features.on(WEIGHT) && Combat.opt(WEIGHT, "protector")) printWeight(protectorWeight(Integer.parseInt(x.group("amount")), endPlace, endTop, endDamage));
                endType = null;
            }
        }
        return ChatBus.PASS;
    }

    private static void endFight() {
        endType = null;
        eyes = 0;
        dragonType = "";
        dragonUp = false;
        dragonHp = -1;
        currentDamage = topDog = 0;
        whereIRank = null;
    }

    // skyhanni onScoreBoard + DragonFightAPI.onScoreboardChange, and onTabList
    private static void readFight(Minecraft mc) {
        long hp = -1;
        for (String l : GameState.scoreboard) {
            String t = l.trim();
            Matcher m = HP_LINE.matcher(t);
            if (m.matches()) {
                hp = (long) number(m.group(1));
                continue;
            }
            m = DAMAGE_LINE.matcher(t);
            if (m.matches()) currentDamage = number(m.group(1));
        }
        dragonHp = hp;
        if (hp >= 0) dragonUp = true;
        String me = mc.getUser().getName();
        for (var w : io.muzy.sbc.core.TabWidgets.parse(GameState.tab)) {
            if (!w.def().id().equals("DRAGON")) continue;
            int i = 0;
            for (int at : w.lines()) {
                i++;
                Matcher m = TAB_DAMAGE.matcher(GameState.tab.get(at));
                if (!m.matches()) continue;
                if (i == 1) topDog = SlayerFeatures.shortNumber(m.group("damage"));
                if (m.group("name").equals(me)) whereIRank = i > 3 ? null : i;
            }
        }
    }

    // protector and zealots

    private static void protectorLine(String t) {
        if (TREMOR.matcher(t).matches()) {
            if (stage == 0) findProtector();
            else stage++;
        } else if (RISES.matcher(t).matches()) {
            if (place == null) findProtector();
            stage = 5;
        } else if (FIGHT.matcher(t).matches()) {
            stage = 0;
            place = null;
        } else if (SPECIAL.matcher(t).matches()) {
            CombatData d = CombatData.get();
            d.zealotsSinceLastEye = 0;
            d.eyes++;
            CombatData.changed();
        }
    }

    // skyhanni isProtectorHere over the 6 spots: a player head between y 5 and 9
    private static void findProtector() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Place p : PLACES) {
            if (!level.hasChunk(p.x() >> 4, p.z() >> 4)) continue;
            for (int i = 0; i < 5; i++) {
                var state = level.getBlockState(new BlockPos(p.x(), i + 5, p.z()));
                if (state.is(Blocks.PLAYER_WALL_HEAD) || state.is(Blocks.PLAYER_HEAD)) {
                    stage = i + 1;
                    place = p;
                    return;
                }
            }
        }
    }

    //? if >=26.3 {
    private static boolean isZealot(Enderman e) {
    //?} else {
    /*private static boolean isZealot(EnderMan e) {
    *///?}
        var level = Minecraft.getInstance().level;
        return level != null && !level.getEntitiesOfClass(ArmorStand.class, e.getBoundingBox().inflate(1), a -> a.getName().getString().toLowerCase(Locale.ROOT).contains("zealot")).isEmpty();
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount % 10 == 0) readFight(mc);
        if (mc.player.tickCount % 2 == 0 && !hitZealots.isEmpty()) {
            //? if >=26.3 {
            for (Enderman e : mc.level.getEntitiesOfClass(Enderman.class, mc.player.getBoundingBox().inflate(16), x -> hitZealots.contains(x.getUUID()))) {
            //?} else {
            /*for (EnderMan e : mc.level.getEntitiesOfClass(EnderMan.class, mc.player.getBoundingBox().inflate(16), x -> hitZealots.contains(x.getUUID()))) {
            *///?}
                if (e.getHealth() > 0 && e.isAlive()) continue;
                hitZealots.remove(e.getUUID());
                CombatData d = CombatData.get();
                d.totalZealotKills++;
                d.zealotsSinceLastEye++;
                CombatData.changed();
            }
        }
        if (mc.player.tickCount % 20 == 0 && Features.on(NODES)) for (Node n : NODE_MAP.values()) n.confirm(mc);
    }

    private static void onAttack(net.minecraft.world.entity.Entity e) {
        //? if >=26.3 {
        if (Combat.island("THE_END") && e instanceof Enderman m && isZealot(m)) hitZealots.add(m.getUUID());
        //?} else {
        /*if (Combat.island("THE_END") && e instanceof EnderMan m && isZealot(m)) hitZealots.add(m.getUUID());
        *///?}
    }

    // from skyblocker EndStatsBestiaryUpdater
    private static void zealotMenu(MenuView v) {
        readKills(v, ALL_ZEALOTS_SLOT, true);
        readKills(v, SPECIAL_ZEALOT_SLOT, false);
    }

    private static void readKills(MenuView v, int slot, boolean all) {
        if (slot >= v.top || v.items[slot].empty() || !v.items[slot].model.contains("ender_chest")) return;
        Matcher m = v.items[slot].firstClean(KILLS);
        if (m == null) return;
        int kills = Integer.parseInt(m.group(1).replace(",", ""));
        CombatData d = CombatData.get();
        if (all && d.totalZealotKills != kills) d.totalZealotKills = kills;
        else if (!all && d.eyes != kills) d.eyes = kills;
        else return;
        CombatData.changed();
    }

    // ender nodes

    // skyblocker EnderNodes.EnderNode: block with 5 portal + 5 witch particles on each open side
    private static final class Node {
        final BlockPos pos;
        final Map<Direction, int[]> particles = new EnumMap<>(Direction.class);
        long confirmed;

        Node(BlockPos pos) {
            this.pos = pos;
            for (Direction d : Direction.values()) particles.put(d, new int[2]);
        }

        void confirm(Minecraft mc) {
            long now = System.currentTimeMillis();
            if (confirmed + 2000 > now) return;
            for (var e : particles.entrySet()) if ((e.getValue()[0] < 5 || e.getValue()[1] < 5) && mc.level.getBlockState(pos.relative(e.getKey())).isAir()) return;
            confirmed = now;
            for (int[] p : particles.values()) p[0] = p[1] = 0;
        }
    }

    private static void onParticle(ParticleHooks.Particle p) {
        if (!Features.on(NODES)) return;
        boolean portal = p.is("minecraft:portal"), witch = p.is("minecraft:witch");
        if (!portal && !witch) return;
        double x = p.x(), y = p.y(), z = p.z();
        double fx = Mth.positiveModulo(x, 1), fy = Mth.positiveModulo(y, 1), fz = Mth.positiveModulo(z, 1);
        BlockPos pos;
        Direction d;
        if (fy == 0.25) {
            pos = BlockPos.containing(x, y - 1, z);
            d = Direction.UP;
        } else if (fy == 0.75) {
            pos = BlockPos.containing(x, y + 1, z);
            d = Direction.DOWN;
        } else if (fx == 0.25) {
            pos = BlockPos.containing(x - 1, y, z);
            d = Direction.EAST;
        } else if (fx == 0.75) {
            pos = BlockPos.containing(x + 1, y, z);
            d = Direction.WEST;
        } else if (fz == 0.25) {
            pos = BlockPos.containing(x, y, z - 1);
            d = Direction.SOUTH;
        } else if (fz == 0.75) {
            pos = BlockPos.containing(x, y, z + 1);
            d = Direction.NORTH;
        } else return;
        NODE_MAP.computeIfAbsent(pos, Node::new).particles.get(d)[portal ? 0 : 1]++;
    }

    private static void draw(WorldDraw w) {
        long now = System.currentTimeMillis();
        if (Features.on(NODES)) {
            int color = Combat.color(NODES, "color", 0xFF55FFFF);
            for (Node n : NODE_MAP.values()) if (n.confirmed + 5000 > now) w.box(new net.minecraft.world.phys.AABB(n.pos), color, false, false);
            if (NODE_MAP.size() > 256) NODE_MAP.values().removeIf(n -> n.confirmed + 5000 < now);
        }
        if (Features.on(PROTECTOR) && Combat.opt(PROTECTOR, "waypoint") && place != null && stage == 5) w.waypoint(new Vec3(place.x(), 0, place.z()), 0xFFFF55FF, "Protector", true, true);
    }

    private static void left() {
        endFight();
        place = null;
        stage = 0;
        hitZealots.clear();
        NODE_MAP.clear();
    }

    // cards

    private static RichCard.Content weightCard(double weight, int eyes, String place, String ratio) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Weight", String.format(Locale.ROOT, "§f%,.1f", weight)).raw(), RichCard.Line.row("Eyes", String.valueOf(eyes)).raw(), RichCard.Line.row("Place", place).raw(), RichCard.Line.row("Damage", ratio).raw()));
    }

    private static final HudElement WEIGHT_CARD = new RichCard(WEIGHT, "Dragon weight", "Combat", Zone.TOP_LEFT, 44, WEIGHT) {
        @Override public boolean visible() {
            return dragonUp && Combat.opt(WEIGHT, "display");
        }

        @Override protected Content live() {
            String placeText = whereIRank != null ? "#" + whereIRank : currentDamage != 0 ? "6th?" : "no damage";
            return weightCard(dragonWeight(eyes, whereIRank != null ? whereIRank : 6, topDog, currentDamage), eyes, placeText, Math.round(100 * currentDamage / (topDog != 0 ? topDog : 1)) + "%");
        }

        @Override protected Content sample() {
            return weightCard(412.6, 2, "#3", "36%");
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("Weight", String.format(Locale.ROOT, "%,.1f", dragonWeight(eyes, whereIRank != null ? whereIRank : 6, topDog, currentDamage)), Theme.current().accent()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Weight", "412.6", Theme.current().accent()));
        }
    };

    private static RichCard.Content zealotCard(int since, int total, int eyes) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Since last eye", String.format(Locale.ROOT, "%,d", since)).brief("Since eye").raw());
        out.add(RichCard.Line.row("Total kills", String.format(Locale.ROOT, "%,d", total)).brief("Total").raw());
        out.add(RichCard.Line.row("Kills per eye", eyes == 0 ? "?" : String.format(Locale.ROOT, "%,.2f", total / (double) eyes)).brief("Per eye").raw());
        return new RichCard.Content("", out);
    }

    private static final HudElement ZEALOT_CARD = new RichCard(ZEALOTS, "Zealots", "Combat", Zone.TOP_LEFT, 44, ZEALOTS) {
        @Override public boolean visible() {
            return Combat.island("THE_END");
        }

        @Override protected Content live() {
            CombatData d = CombatData.get();
            return zealotCard(d.zealotsSinceLastEye, d.totalZealotKills, d.eyes);
        }

        @Override protected Content sample() {
            return zealotCard(214, 24_850, 61);
        }

        @Override protected List<Row> classicRows() {
            CombatData d = CombatData.get();
            return List.of(new Row("Since last eye", String.valueOf(d.zealotsSinceLastEye), Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Since last eye", "214", Theme.current().text()));
        }
    };

    private static RichCard.Content protectorCard(int stage, String where) {
        return new RichCard.Content("", List.of(stage == 5 ? RichCard.Line.row("Stage", "IMMINENT", Modern.BAD).raw() : RichCard.Line.row("Stage", stage + "/5").meter(stage / 5.0, Modern.WARN), RichCard.Line.row("Spot", where).raw()));
    }

    private static final HudElement PROTECTOR_CARD = new RichCard(PROTECTOR, "End Stone Protector", "Combat", Zone.TOP_LEFT, 44, PROTECTOR) {
        @Override public boolean visible() {
            return Combat.island("THE_END") && stage > 0;
        }

        @Override protected Content live() {
            return protectorCard(stage, place == null ? "?" : place.name());
        }

        @Override protected Content sample() {
            return protectorCard(3, "Center");
        }

        @Override public List<Content> states() {
            return List.of(protectorCard(5, "Right Front"));
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("Protector", stage + "/5", Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Protector", "3/5", Theme.current().text()));
        }
    };

    static void register() {
        Features.add(new Feature("end_reading", "Combat", "The End reading", "Follows the dragon, the Protector and your Zealot kills, for the helpers.", "").alwaysOn().when(() -> Combat.island("THE_END")).tick(EndIsland::tick).world(EndIsland::draw).left(EndIsland::left));
        ChatBus.register(ChatBus.OBSERVE + 24, "endisland", null, EndIsland::onChat);
        Features.add(new Feature(WEIGHT, "Combat", "Dragon weight", "Your weight when a dragon or the Protector goes down, while it fights on a card, and a title for a Superior.", "SkyHanni: Dragon Features").when(() -> Combat.island("THE_END"))
                .option(Option.bool("dragon_weight.chat", "In chat after a dragon", "", true))
                .option(Option.bool("dragon_weight.protector", "In chat after the Protector", "", true))
                .option(Option.bool("dragon_weight.display", "Card during the fight", "", true))
                .option(Option.bool("dragon_weight.superior", "Superior title", "", true)));
        Features.add(new Feature(ZEALOTS, "Combat", "Zealot kills", "Your Zealot kills, those since the last Summoning Eye and how many an eye takes. The Bestiary's Zealot page corrects them.", "Skyblocker: Zealot Kills Information").when(() -> Combat.island("THE_END")));
        MenuMarks.add(ZEALOTS, "The End ➜ Zealot", EndIsland::zealotMenu);
        Features.add(new Feature(PROTECTOR, "Combat", "End Stone Protector", "Its stage and spot, and a waypoint when it is about to rise.", "Skyblocker: Protector Location, End Protector Waypoint").when(() -> Combat.island("THE_END"))
                .option(Option.bool("end_protector.waypoint", "Waypoint when it rises", "", true)));
        Features.add(new Feature(NODES, "Combat", "Ender Node boxes", "A box around the Ender Nodes near you, found by their particles.", "Skyblocker: Ender Node Helper").when(() -> Combat.island("THE_END"))
                .option(Option.color("ender_node_helper.color", "Colour", "", 0xFF55FFFF)));
        ParticleHooks.listen(EndIsland::onParticle);
        io.muzy.sbc.mc.Interactions.onBlockClick((pos, right) -> {
            if (!right) NODE_MAP.remove(BlockPos.containing(pos));
        });
        SlayerApi.onAttack(EndIsland::onAttack);
        HudLayout.add(WEIGHT_CARD);
        HudLayout.add(ZEALOT_CARD);
        HudLayout.add(PROTECTOR_CARD);
    }
}
