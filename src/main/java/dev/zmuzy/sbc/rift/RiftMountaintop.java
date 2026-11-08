// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.events.hoppity.HoppityEnv;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// the mountaintop, from skyhanni rift/area/mountaintop: EnigmaRoseFlowerpot, UbikQuickClose, TimiteHelper,
// SunGeckoHelper as a card
public final class RiftMountaintop {
    public static final String ID = "rift_mountaintop", GECKO = "rift_sun_gecko";
    private static final AABB FLOWERPOT_AREA = new AABB(25, 165, 90, 52, 185, 120);
    private static final Vec3 FLOWERPOT_DROP = new Vec3(40, 161, 116);
    private static final String TIME_GUN = "TIME_GUN", COMBO_MANIA_TALISMAN = "COMBO_MANIA_TALISMAN";
    static final Patterns.P UBIK_MENU = Patterns.p("SH.rift.ubik.inventory-name");
    static final Patterns.P GECKO_BAR = Patterns.p("SH.rift.area.mountaintop.sun-gecko.actionbar"), GECKO_MODIFIERS = Patterns.p("SH.rift.area.mountaintop.sun-gecko.modifiers"),
            GECKO_LINE = Patterns.p("SH.rift.area.mountaintop.sun-gecko.chatline");

    private RiftMountaintop() {}

    private static boolean on(String key, boolean def) {
        return RiftScan.on(ID, key, def);
    }

    // --- timite ---

    private static long holdingClick = -1, lastClick = -1;
    private static BlockPos currentPos;
    private static BlockState currentState;
    private static boolean doubleShot;
    private static final Map<BlockPos, Long> EXPIRY = new HashMap<>();

    private static boolean timitePane(BlockState s) {
        return s.is(Blocks.STAINED_GLASS_PANE.blue()) || s.is(Blocks.STAINED_GLASS_PANE.lightBlue());
    }

    private static void onTimeGun(Vec3 pos, boolean right) {
        if (!right || !Rift.rift() || !Features.on(ID) || !RiftApi.inMountainTop() || !on("rift_mountaintop.timite_evolution", true)) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || !RiftScan.heldId(mc).equals(TIME_GUN)) return;
        long now = System.currentTimeMillis();
        BlockPos p = BlockPos.containing(pos);
        BlockState state = mc.level.getBlockState(p);
        if (!p.equals(currentPos) || currentState != state) {
            lastClick = -1;
            if (p.equals(currentPos) && currentState != state) {
                EXPIRY.put(p, now);
                doubleShot = true;
            } else doubleShot = false;
        }
        currentPos = p;
        currentState = state;
        if (!timitePane(state)) return;
        if (lastClick >= 0 && lastClick + 300 > now) {
            lastClick = now;
            return;
        }
        lastClick = now;
        holdingClick = now;
    }

    private static void tickTimite(Minecraft mc) {
        if (!on("rift_mountaintop.timite_expiry", true) || mc.player.tickCount % 20 != 0) return;
        long now = System.currentTimeMillis();
        BlockPos c = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-15, -15, -15), c.offset(15, 15, 15)))
            if (timitePane(mc.level.getBlockState(p))) EXPIRY.putIfAbsent(p.immutable(), now);
        EXPIRY.keySet().removeIf(p -> {
            BlockState s = mc.level.getBlockState(p);
            return s.isAir() || s.is(Blocks.STAINED_GLASS_PANE.lightBlue());
        });
    }

    private static void drawTimite(WorldDraw w) {
        long now = System.currentTimeMillis();
        if (on("rift_mountaintop.timite_evolution", true) && RiftScan.heldId(w.mc).equals(TIME_GUN) && currentPos != null) {
            if (lastClick + 400 < now) {
                holdingClick = -1;
                doubleShot = false;
            }
            if (holdingClick >= 0 && timitePane(currentState)) {
                long left = holdingClick + (doubleShot ? 1800 : 2000) - now;
                if (left > 0) w.dynamicText(Vec3.atCenterOf(currentPos).add(0, 1, 0), "§b" + RiftScan.seconds(left), 1.5, 0);
            }
        }
        if (on("rift_mountaintop.timite_expiry", true)) {
            for (var e : EXPIRY.entrySet()) {
                long left = e.getValue() + 31_000 - now;
                if (left < 6000) w.dynamicText(Vec3.atLowerCornerOf(e.getKey()), "§c" + HoppityEnv.format(Math.max(0, left)), 1.5, 0);
            }
        }
    }

    public static void onBlockChange(BlockPos pos, String old, String now) {
        if (now.equals("air")) EXPIRY.remove(pos);
    }

    // --- sun gecko ---

    enum Modifier {
        REVIVAL(19, "Revival"), COMBO_MANIC(20, "Combo Manic"), TIME_SLICED(21, "Time Sliced"), BUFFANTICS(22, "Buffantics"), COLLECTIVE(23, "Collective"),
        BRAND_NEW_DANCE(24, "Brand New Dance"), CULMINATION(25, "Culmination");
        final int slot;
        final String label;

        Modifier(int slot, String label) {
            this.slot = slot;
            this.label = label;
        }
    }

    private static final Set<Modifier> MODIFIERS = EnumSet.noneOf(Modifier.class);
    private static int healthLeft = 250, totalHealth = 250, combo = 1, comboHits, comboTotal = 10;
    private static boolean firstPhase = true, scanningChat, inTimeChamber, hasTalisman;
    private static long timeSliceSeconds, lastHit = -1;
    private static LivingEntity boss;
    private static String lastArea = "", lastBar = "";
    private static final List<Entity> CLONES = new ArrayList<>(), REAL = new ArrayList<>();

    private static void resetGecko() {
        healthLeft = totalHealth = 250;
        combo = 1;
        comboHits = 0;
        comboTotal = 10;
        firstPhase = true;
        timeSliceSeconds = 0;
        lastHit = -1;
        boss = null;
    }

    // hits, of, combo, hit just now from one action bar. null = not a gecko bar
    static int[] parseBar(String bar, Set<Modifier> modifiers, long sliceSeconds) {
        Matcher m = GECKO_BAR.matcher(bar);
        if (!m.find()) return null;
        int hits = count(m.group("firstHalf")) + count(m.group("secondHalf")), total = 10;
        if (modifiers.contains(Modifier.CULMINATION)) total--;
        if (modifiers.contains(Modifier.TIME_SLICED) && sliceSeconds > 150) total--;
        // hypixel bug: goes 8/8 -> 9/8 -> 2/8 and the combo doesnt go up at 9/8
        if (hits == 9 && total == 8) hits = 1;
        return new int[]{hits, total, Integer.parseInt(m.group("combo")), m.group().contains("§c") ? 0 : 1};
    }

    private static int count(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '⬛') n++;
        return n;
    }

    // skyhannis guess in updateDisplay: 4.7 sec, more with the talisman and with collective
    static long comboExpiry(boolean talisman, Set<Modifier> modifiers) {
        long ms = 4700;
        if (talisman) ms += 500;
        if (modifiers.contains(Modifier.COLLECTIVE)) ms += modifiers.size() * 200L;
        return ms;
    }

    private static void onGeckoChat(String formatted) {
        if (GECKO_MODIFIERS.matcher(formatted).matches()) {
            scanningChat = true;
            MODIFIERS.clear();
        } else if (GECKO_LINE.matcher(formatted).matches()) scanningChat = false;
        if (scanningChat) for (Modifier m : Modifier.values()) if (formatted.contains(m.label)) MODIFIERS.add(m);
    }

    // from skyhanni onInventoryUpdated: lime glass = on
    static void onMenu(String title, ChestMenu menu, int n) {
        if (!title.equals("Modifiers") || !RiftApi.inMountainTop() || !Features.on(GECKO)) return;
        MODIFIERS.clear();
        for (Modifier m : Modifier.values()) if (m.slot < n && menu.slots.get(m.slot).getItem().is(Items.STAINED_GLASS_PANE.lime())) MODIFIERS.add(m);
    }

    private static void tickGecko(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!GameState.area.equals(lastArea)) {
            lastArea = GameState.area;
            resetGecko();
            inTimeChamber = lastArea.equals("Time Chamber");
        }
        if (!RiftApi.inMountainTop()) return;
        String bar = Vitals.actionBarRaw;
        if (!bar.equals(lastBar)) {
            lastBar = bar;
            int[] b = parseBar(bar, MODIFIERS, timeSliceSeconds);
            if (b != null) {
                comboHits = b[0];
                comboTotal = b[1];
                combo = b[2];
                if (b[3] == 1) lastHit = System.currentTimeMillis();
            }
        }
        if (mc.player.tickCount % 5 == 0) {
            for (String line : GameState.scoreboardFormatted) {
                if (!line.startsWith(" Big damage in: §d")) continue;
                MODIFIERS.add(Modifier.TIME_SLICED);
                Long d = HoppityEnv.duration(line.replace(" Big damage in: §d", ""));
                if (d != null) timeSliceSeconds = d / 1000;
            }
            CLONES.clear();
            REAL.clear();
            for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48), Entity::hasCustomName)) {
                String name = GameState.strip(st.getName().getString());
                if (!name.contains("Sun Gecko")) continue;
                LivingEntity mob = RiftScan.mobUnder(st);
                if (mob == null) continue;
                if (name.contains("?")) CLONES.add(mob);
                else {
                    REAL.add(mob);
                    if (boss == null || boss.isDeadOrDying() || boss.isRemoved() || boss.getHealth() < 20) boss = mob;
                }
            }
            hasTalisman = false;
            var inv = mc.player.getInventory();
            for (int s = 0; s < inv.getContainerSize(); s++) if (COMBO_MANIA_TALISMAN.equals(Gain.sbId(inv.getItem(s)))) hasTalisman = true;
        }
        if (!inTimeChamber) return;
        if (boss != null && (boss.isDeadOrDying() || boss.isRemoved())) boss = null;
        if (boss == null) return;
        int health = (int) boss.getHealth();
        if (health > healthLeft) {
            firstPhase = false;
            MODIFIERS.add(Modifier.REVIVAL);
        }
        healthLeft = health;
        totalHealth = (int) boss.getMaxHealth();
    }

    private static void drawGecko(WorldDraw w) {
        if (!RiftApi.inMountainTop()) return;
        if (RiftScan.on(GECKO, "rift_sun_gecko.clones", true)) for (Entity e : CLONES) if (!e.isRemoved()) w.box(e.getBoundingBox(), 0x50FF5555, true, false);
        if (RiftScan.on(GECKO, "rift_sun_gecko.real", false)) for (Entity e : REAL) if (!e.isRemoved()) w.box(e.getBoundingBox(), 0x5055FF55, true, false);
    }

    private static RichCard.Content gecko(int left, int total, boolean revival, boolean firstHalf, int hits, int of, int comboNow, long comboLeft, long expiry, Set<Modifier> modifiers, boolean showModifiers) {
        int shownLeft = left, shownTotal = total;
        if (revival) {
            if (firstHalf) shownLeft += total;
            shownTotal *= 2;
        }
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Health", (shownLeft <= shownTotal / 2 ? "§e" : "§a") + shownLeft + "§f/§a" + shownTotal + "§c❤").raw().meter(shownTotal <= 0 ? 0 : (double) shownLeft / shownTotal, 0xFFFF6B6B));
        out.add(RichCard.Line.row("Combo", "§a" + hits + "/" + of + " §e§lx" + comboNow).raw());
        out.add(RichCard.Line.row("Combo time", "§a" + RiftScan.seconds(comboLeft) + "§7/" + RiftScan.seconds(expiry)).raw().meter(expiry <= 0 ? 0 : (double) comboLeft / expiry, 0xFF5FEA8C));
        if (showModifiers) {
            if (modifiers.isEmpty()) out.add(RichCard.Line.row("Modifiers", "§eNone").raw());
            boolean first = true;
            for (Modifier m : modifiers) {
                out.add(RichCard.Line.row(first ? "Modifiers" : "", "§e" + m.label).key("Modifier " + m.label).raw());
                first = false;
            }
        }
        return new RichCard.Content("", out);
    }

    private static long comboLeft(long expiry) {
        long now = System.currentTimeMillis();
        long end = lastHit < 0 ? 0 : lastHit + expiry;
        long left = end - now;
        // from skyhanni updateDisplay: the first 800ms and a run out timer both read full
        return left > expiry - 800 || left < 0 ? expiry : left;
    }

    private static final HudElement GECKO_CARD = new RichCard(GECKO, "Sun Gecko", "Rift", Zone.TOP_LEFT, 55, GECKO) {
        @Override public boolean visible() {
            return Rift.rift() && inTimeChamber;
        }

        @Override protected Content live() {
            long expiry = comboExpiry(hasTalisman, MODIFIERS);
            return gecko(healthLeft, totalHealth, MODIFIERS.contains(Modifier.REVIVAL), firstPhase, comboHits, comboTotal, combo, comboLeft(expiry), expiry, MODIFIERS, RiftScan.on(GECKO, "rift_sun_gecko.modifiers", true));
        }

        @Override protected Content sample() {
            return gecko(180, 250, true, true, 7, 9, 3, 3200, 5200, EnumSet.of(Modifier.REVIVAL, Modifier.COLLECTIVE), true);
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            out.add(new Row("Sun Gecko", healthLeft + "/" + totalHealth, 0));
            out.add(new Row("Combo", comboHits + "/" + comboTotal + " x" + combo, 0));
            for (Modifier m : MODIFIERS) out.add(new Row(m.label, "", 0));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Sun Gecko", "180/250", 0), new Row("Combo", "7/9 x3", 0));
        }
    };

    static void onChat(String formatted) {
        if (Features.on(GECKO) && RiftApi.inMountainTop()) onGeckoChat(formatted);
    }

    // ubiks slot 4 is a clock till the game ended, a click after that closes the menu
    private static boolean onSlot(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button) {
        if (!Rift.rift() || !Features.on(ID) || !on("rift_mountaintop.ubik_close", true)) return false;
        if (!UBIK_MENU.matcher(screen.getTitle().getString()).matches()) return false;
        var slots = screen.getMenu().slots;
        if (slots.size() <= 4) return false;
        ItemStack marker = slots.get(4).getItem();
        if (marker.is(Items.CLOCK)) return false;
        screen.onClose();
        return true;
    }

    public static void register() {
        Features.add(new Feature(ID, "Rift", "Mountaintop", "Where to drop for the flower pot soul, how long Timite takes to evolve and when it expires, and a click anywhere to leave Ubik's game once it has ended.", "SkyHanni: Mountaintop")
                .when(Rift::rift)
                .tick(mc -> {
                    if (mc.player != null && mc.level != null && RiftApi.inMountainTop()) tickTimite(mc);
                })
                .world(w -> {
                    if (RiftApi.inMountainTop()) drawTimite(w);
                    if (on("rift_mountaintop.flowerpot", true) && FLOWERPOT_AREA.contains(w.mc.player.position())) {
                        w.waypointBox(FLOWERPOT_DROP, 0xFFFFFFFF, true, false, 0);
                        w.dynamicText(FLOWERPOT_DROP, "Drop", 1.5, 0);
                    }
                })
                .left(() -> {
                    EXPIRY.clear();
                    currentPos = null;
                    currentState = null;
                    holdingClick = lastClick = -1;
                    doubleShot = false;
                })
                .option(Option.bool("rift_mountaintop.flowerpot", "Flower pot soul", "The spot to drop from during the parkour to the hard to reach flower pot.", true))
                .option(Option.bool("rift_mountaintop.ubik_close", "Close Ubik's game with a click", "Once Split or Steal has ended, any click in it closes it.", true))
                .option(Option.bool("rift_mountaintop.timite_evolution", "Timite evolution time", "On the block you shoot with the Time Gun.", true))
                .option(Option.bool("rift_mountaintop.timite_expiry", "Timite expiry time", "On Timite and Obsolite about to expire.", true)));
        Features.add(new Feature(GECKO, "Rift", "Sun Gecko", "In the Time Chamber, the boss's health, your combo and its time left and the modifiers on; marks the fake bosses.", "SkyHanni: Sun Gecko Helper")
                .when(Rift::rift).tick(RiftMountaintop::tickGecko).world(RiftMountaintop::drawGecko)
                .left(() -> {
                    resetGecko();
                    inTimeChamber = false;
                    scanningChat = false;
                    lastArea = "";
                    lastBar = "";
                    MODIFIERS.clear();
                    CLONES.clear();
                    REAL.clear();
                })
                .option(Option.bool("rift_sun_gecko.modifiers", "Show the modifiers", "", true))
                .option(Option.bool("rift_sun_gecko.clones", "Mark the clones", "A red box.", true))
                .option(Option.bool("rift_sun_gecko.real", "Mark the real boss", "A green box.", false)));
        HudLayout.add(GECKO_CARD);
        Interactions.onBlockClick(RiftMountaintop::onTimeGun);
        Interactions.onSlotClick(RiftMountaintop::onSlot);
    }
}
