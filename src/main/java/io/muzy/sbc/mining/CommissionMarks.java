// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// what your commissions point at. from skyblocker CommissionLabels + MiningLocationLabel (where its done,
// name + distance, the emissaries)
// skyblocker CommissionHighlight (done ones green in the menu)
// skyhanni HighlightMiningCommissionMobs (box around the mobs a commission wants, skyhanni tints them instead)
public final class CommissionMarks {
    static final String WAYPOINTS = "mining_commission_waypoints", MENU = "mining_commission_menu", MOBS = "mining_commission_mobs";

    private record Label(String name, char color, Vec3 pos) {}

    // skyblocker MiningLocationLabel.DwarvenCategory. every color is the blocks color
    private static final Map<String, Vec3> DWARVEN = new LinkedHashMap<>();
    private static final Vec3[] EMISSARIES = {new Vec3(58, 198, -8), new Vec3(42, 134, 22), new Vec3(-72, 153, -10), new Vec3(-132, 174, -50), new Vec3(171, 150, 31), new Vec3(-37, 200, -131), new Vec3(89, 198, -92)};
    private static final Map<String, Vec3[]> GLACITE = new LinkedHashMap<>();
    private static final Map<String, Character> GLACITE_COLOR = Map.of("Aquamarine", '9', "Onyx", '8', "Peridot", '2', "Citrine", '6');
    private static final Vec3 BASE_CAMP = new Vec3(-7, 126, 229);

    static {
        DWARVEN.put("Lava Springs", new Vec3(60, 197, -15));
        DWARVEN.put("Cliffside Veins", new Vec3(40, 128, 40));
        DWARVEN.put("Rampart's Quarry", new Vec3(-100, 150, -20));
        DWARVEN.put("Upper Mines", new Vec3(-130, 174, -50));
        DWARVEN.put("Royal Mines", new Vec3(130, 154, 30));
        DWARVEN.put("Glacite Walker", new Vec3(0, 128, 150));
        GLACITE.put("Aquamarine", new Vec3[]{new Vec3(20, 136, 370), new Vec3(-14, 132, 386), new Vec3(6, 137, 411), new Vec3(50, 117, 302)});
        GLACITE.put("Onyx", new Vec3[]{new Vec3(4, 127, 307), new Vec3(-3, 139, 434), new Vec3(77, 118, 411), new Vec3(-68, 130, 404)});
        GLACITE.put("Peridot", new Vec3[]{new Vec3(66, 144, 284), new Vec3(94, 154, 284), new Vec3(-62, 147, 303), new Vec3(-77, 119, 283), new Vec3(87, 122, 394), new Vec3(-73, 122, 456)});
        GLACITE.put("Citrine", new Vec3[]{new Vec3(-86, 143, 261), new Vec3(74, 150, 327), new Vec3(63, 137, 343), new Vec3(38, 119, 386), new Vec3(55, 150, 400), new Vec3(-45, 127, 415), new Vec3(-60, 144, 424), new Vec3(-54, 132, 410)});
    }

    // skyhanni commissionMobs: start of the tab line -> the mob
    private static final Map<String, Predicate<LivingEntity>> COMMISSION_MOBS = new LinkedHashMap<>();

    private static boolean maxHealth(LivingEntity e, int... values) {
        for (int v : values) if (e.getMaxHealth() == v) return true;
        return false;
    }

    static {
        COMMISSION_MOBS.put("Goblin Slayer", e -> name(e).equals("Goblin ") || name(e).equals("Weakling "));
        COMMISSION_MOBS.put("Star Sentry Puncher", e -> name(e).equals("Crystal Sentry"));
        COMMISSION_MOBS.put("Glacite Walker Slayer", e -> name(e).equals("Ice Walker"));
        COMMISSION_MOBS.put("Golden Goblin Slayer", e -> name(e).contains("Golden Goblin"));
        // the typo is hypixels, dont fix it
        COMMISSION_MOBS.put("Treasure Hoarder Puncher", e -> name(e).equals("Treasuer Hunter"));
        COMMISSION_MOBS.put("Automaton Slayer", e -> e instanceof IronGolem && maxHealth(e, 15_000, 20_000));
        COMMISSION_MOBS.put("Team Treasurite Member Slayer", e -> name(e).equals("Team Treasurite"));
        COMMISSION_MOBS.put("Yog Slayer", e -> e instanceof MagmaCube && maxHealth(e, 35_000));
        COMMISSION_MOBS.put("Thyst Slayer", e -> e instanceof Endermite && maxHealth(e, 5_000));
        COMMISSION_MOBS.put("Corleone Slayer", e -> maxHealth(e, 1_000_000) && name(e).equals("Team Treasurite"));
        COMMISSION_MOBS.put("Sludge Slayer", e -> e instanceof Slime && maxHealth(e, 5_000, 10_000, 25_000));
    }

    private static final List<Label> LABELS = new ArrayList<>();
    private static final List<LivingEntity> MARKED = new ArrayList<>();
    private static long nextLabels, nextMobs;

    private CommissionMarks() {}

    private static String name(Entity e) {
        return e.getName().getString();
    }

    static boolean glaciteSide(String area) {
        return area.equals("Dwarven Base Camp") || area.equals("Glacite Tunnels") || area.equals("Glacite Mineshafts") || area.equals("Great Glacite Lake");
    }

    // from skyblocker CommissionLabels.update
    static List<Label> labels(List<String> commissions, boolean anyDone, String area, String mode, boolean baseCamp, boolean emissary, boolean pigeon) {
        List<Label> out = new ArrayList<>();
        if (glaciteSide(area)) {
            if (!mode.equals("Both") && !mode.equals("Glacite")) return out;
            for (String c : commissions) for (var e : GLACITE.entrySet()) if (c.contains(e.getKey())) for (Vec3 v : e.getValue()) out.add(new Label(e.getKey(), GLACITE_COLOR.get(e.getKey()), v));
            if (baseCamp) out.add(new Label("Base Camp", 'c', BASE_CAMP));
            return out;
        }
        if (!mode.equals("Both") && !mode.equals("Dwarven")) return out;
        for (String c : commissions) for (var e : DWARVEN.entrySet()) if (c.contains(e.getKey())) out.add(new Label(e.getKey(), c.contains("Titanium") ? 'f' : 'b', e.getValue()));
        if (emissary && anyDone && !pigeon) for (Vec3 v : EMISSARIES) out.add(new Label("Emissary", 'f', v));
        return out;
    }

    private static void tickLabels(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextLabels) return;
        nextLabels = now + 1000;
        List<String> names = new ArrayList<>();
        boolean done = false;
        for (Commissions.Commission c : Commissions.current()) {
            names.add(c.name());
            done |= c.done();
        }
        boolean pigeon = MiningPack.on(WAYPOINTS, "mining_commission_waypoints.hide_on_pigeon") && MiningPack.inInventory("ROYAL_PIGEON");
        LABELS.clear();
        LABELS.addAll(labels(names, done, GameState.area, MiningPack.choice(WAYPOINTS, "mining_commission_waypoints.mode", "Both"),
                MiningPack.on(WAYPOINTS, "mining_commission_waypoints.base_camp"), MiningPack.on(WAYPOINTS, "mining_commission_waypoints.emissary"), pigeon));
    }

    private static void drawLabels(WorldDraw w) {
        boolean color = MiningPack.on(WAYPOINTS, "mining_commission_waypoints.color");
        double scale = MiningPack.number(WAYPOINTS, "mining_commission_waypoints.text_scale", 100) / 100.0;
        for (Label l : LABELS) {
            Vec3 at = l.pos().add(0.5, 0.5, 0.5);
            w.dynamicText(at, (color ? "§" + l.color() : "§f") + l.name(), 1.5 * scale, 0);
            w.dynamicText(at, "§e" + Math.round(at.distanceTo(w.eye)) + " blocks", 1.2 * scale, -1);
        }
    }

    // from skyblocker CommissionHighlight.getColors
    static void markMenu(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) if (v.item(slot).cleanHas("COMPLETED")) v.back(slot, MenuView.GREEN);
    }

    private static void tickMobs(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now < nextMobs || mc.level == null) return;
        nextMobs = now + 2000;
        List<Predicate<LivingEntity>> active = new ArrayList<>();
        for (var e : COMMISSION_MOBS.entrySet()) {
            for (Commissions.Commission c : Commissions.current()) if (c.name().startsWith(e.getKey()) && !c.done()) active.add(e.getValue());
        }
        MARKED.clear();
        if (active.isEmpty()) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || e == mc.player) continue;
            for (Predicate<LivingEntity> test : active) {
                if (test.test(le)) {
                    MARKED.add(le);
                    break;
                }
            }
        }
    }

    private static void drawMobs(WorldDraw w) {
        for (LivingEntity e : MARKED) if (!e.isRemoved()) w.box(e.getBoundingBox(), 0xFFFFFF55, false, false);
    }

    public static void register() {
        Features.add(new Feature(WAYPOINTS, "Mining", "Commission places", "Where your commissions are done, with name and distance, and the Emissaries when one is done.", "Skyblocker: Commission Waypoints")
                .when(() -> "DWARVEN_MINES".equals(GameState.island)).tick(CommissionMarks::tickLabels).world(CommissionMarks::drawLabels).left(LABELS::clear)
                .option(Option.choice("mining_commission_waypoints.mode", "Where", "", "Both", "Both", "Dwarven", "Glacite"))
                .option(Option.bool("mining_commission_waypoints.color", "In the colour of the block", "", true))
                .option(Option.bool("mining_commission_waypoints.emissary", "Show the Emissaries", "When a commission is done.", true))
                .option(Option.bool("mining_commission_waypoints.hide_on_pigeon", "Not with a Royal Pigeon", "No Emissaries while you carry one.", true))
                .option(Option.bool("mining_commission_waypoints.base_camp", "Show the Base Camp", "", false))
                .option(Option.integer("mining_commission_waypoints.text_scale", "Text size (%)", "", 100, 50, 300)));
        Features.add(new Feature(MENU, "Mining", "Finished commissions in the menu", "Commissions that are done stand out green in the Commissions menu.", "Skyblocker: Highlights Completed Commissions")
                .when(MiningPack::skyblock));
        MenuMarks.add(MENU, "Commissions", CommissionMarks::markMenu);
        Features.add(new Feature(MOBS, "Mining", "Commission mobs", "A box around the mobs your commissions ask for, where you can see them. It starts off.", "SkyHanni: Highlight Commission Mobs")
                .when(() -> "DWARVEN_MINES".equals(GameState.island) || "CRYSTAL_HOLLOWS".equals(GameState.island)).defaultOff().tick(CommissionMarks::tickMobs).world(CommissionMarks::drawMobs).left(MARKED::clear));
    }
}
