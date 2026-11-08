// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni MouseSensitivityReducer + skyblocker LowerSensitivity: lock or lower mouse sensitivity while
// farming, by command or while something is true
// turns nothing itself, just makes your own mouse do less
public final class MouseLock {
    public static final String ID = "garden_mouse_lock";
    private enum State { LOCKED, REDUCED }
    private static State manual, auto, active;
    private static KeyMapping key;
    private static final String LOCK_ON = "§7Mouse rotation is now locked. /sbc mouselock unlocks it.", LOCK_OFF = "§7Mouse rotation is now unlocked.",
            REDUCE_ON = "§7Mouse sensitivity is now lowered. /sbc sensreduce restores it.", REDUCE_OFF = "§7Mouse sensitivity is back.";

    private MouseLock() {}

    // remapSensitivity, the mouse mixin calls it for each of the 2 turn numbers
    public static double remap(double original) {
        State s = active;
        if (s == null) return original;
        // whatever happens, a feature that isnt running holds nobody's camera
        if (!Features.on(ID)) {
            clear();
            return original;
        }
        if (s == State.LOCKED) return 0;
        return original * Math.max(0, Math.min(1, GardenPack.number(ID, "percent", 10) / 100.0));
    }

    private static void update() {
        active = manual != null ? manual : auto;
    }

    // skyhanni onIslandLeave: from any island, also when the feature gets turned off or paused or you leave the server
    private static void clear() {
        manual = null;
        auto = null;
        update();
    }

    private static void setManual(State s, String message) {
        manual = s;
        update();
        if (message != null && GardenPack.on(ID, "chat")) GardenPack.chat(message);
    }

    // skyblocker isInBarn. skyhanni asks its plot data, a box around the barn says the same
    private static boolean inBarn(Minecraft mc) {
        double x = mc.player.getX(), z = mc.player.getZ();
        return x <= 35.5 && x >= -32.5 && z <= -4.5 && z >= -46.5;
    }

    private static boolean onGround(Minecraft mc) {
        if (mc.player.onGround()) return true;
        double tolerance = GardenPack.number(ID, "tolerance_pixels", 2) / 16.0;
        if (tolerance == 0 || mc.player.getAbilities().flying || mc.player.isPassenger() || mc.player.isFallFlying()) return false;
        Vec3 at = mc.player.position();
        BlockHitResult hit = mc.level.clip(new ClipContext(at, at.add(0, -tolerance, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() != HitResult.Type.MISS;
    }

    private static boolean autoCondition(Minecraft mc) {
        if (GardenPack.on(ID, "auto_key") && key != null && key.isDown() && mc.gui.screen() == null) return true;
        if (GardenPack.on(ID, "auto_tool") && GardenMilestones.hasToolInHand()) return true;
        if (GardenPack.on(ID, "auto_rod") && mc.player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)) return true;
        if (GardenPack.on(ID, "auto_mousemat") && GardenPack.mousematInHand()) return true;
        if (GardenPack.on(ID, "auto_vacuum") && GardenPack.vacuumInHand()) return true;
        return GardenPack.on(ID, "auto_sprayonator") && GardenPack.sprayonatorMinutes(GardenPack.heldId()) > 0;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        // onTick(onlyOnIsland = GARDEN): by itself only there, a command stays until the next island
        if (!GardenPack.garden()) {
            auto = null;
            update();
            return;
        }
        boolean on = GardenPack.on(ID, "auto") && autoCondition(mc) && !(GardenPack.on(ID, "only_plot") && (inBarn(mc) || dev.zmuzy.sbc.garden.plot.GardenPlotInfo.onUnfarmablePlot(mc.player.getX(), mc.player.getZ()))) && !(GardenPack.on(ID, "ground") && !onGround(mc));
        auto = !on ? null : GardenPack.on(ID, "lock") ? State.LOCKED : State.REDUCED;
        update();
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!GardenPack.garden()) return ChatBus.PASS;
        String clean = GameState.strip(line.plain());
        String mode = GardenPack.choice(ID, "unlock_teleport", "Always");
        if (!mode.equals("Never") && manual != null) {
            for (String p : new String[]{"SH.garden.mouse-sensitivity-reducer.chat.teleport.list.0", "SH.garden.mouse-sensitivity-reducer.chat.teleport.list.1", "SH.garden.mouse-sensitivity-reducer.chat.teleport.list.2"}) {
                var m = Patterns.get(p).matcher(clean);
                if (!m.matches()) continue;
                String plot = m.group("plot");
                if (mode.equals("Always") ? !plot.equals("Warping...") : plot.equals("The Barn")) setManual(null, "§7Mouse rotation is now unlocked because you teleported.");
                return ChatBus.PASS;
            }
        }
        if (GardenPack.on(ID, "lock_mousemat") && manual != State.LOCKED && Patterns.get("SH.garden.mouse-sensitivity-reducer.chat.mousemat").matcher(clean).matches())
            setManual(State.LOCKED, "§7Mouse rotation is now locked because you snapped to the mousemat.");
        return ChatBus.PASS;
    }

    // /sbc mouselock + /sbc sensreduce. work on every island like skyhanni, the settings switch still comes first
    private static int command(State s, String on, String off) {
        if (!Features.on(ID)) {
            GardenPack.chat("§cTurn on Mouse lock in the settings first.");
            return 1;
        }
        if (manual != s) setManual(s, on);
        else setManual(null, off);
        return 1;
    }

    public static void keys(KeyMapping.Category category) {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.mouse_lock", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), category));
    }

    private static final RichCard CARD = new RichCard(ID, "Mouse lock", "Garden", Zone.TOP_LEFT, 39, ID) {
        @Override
        public boolean visible() {
            return active != null && GardenPack.on(ID, "card");
        }

        @Override
        protected Content live() {
            return new Content("", List.of(RichCard.Line.row(active == State.LOCKED ? "§eMouse Locked" : "§eSensitivity Lowered", "").key("State")));
        }

        @Override
        protected Content sample() {
            return new Content("", List.of(RichCard.Line.row("§eMouse Locked", "").key("State")));
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row(active == State.LOCKED ? "Mouse Locked" : "Sensitivity Lowered"));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Mouse Locked"));
        }
    };

    // no when: the commands work on every island so what they set has to end on every island too
    static Feature feature() {
        return new Feature(ID, "Garden", "Mouse lock", "Lock your mouse with /sbc mouselock or lower its sensitivity with /sbc sensreduce, anywhere, until you change island; or let it happen by itself on your Garden while something below is true. Only your own mouse does less.", "SkyHanni: Mouse Sensitivity Reducer · Skyblocker: Lock when holding a farming tool")
                .defaultOff().tick(MouseLock::tick).left(MouseLock::clear)
                .option(Option.bool("garden_mouse_lock.auto", "By itself", "On your Garden, also without a command, while one of the things below is true.", false))
                .option(Option.bool("garden_mouse_lock.auto_key", "While holding the key", "The Mouse lock key in the game's Controls.", true))
                .option(Option.bool("garden_mouse_lock.auto_tool", "With a farming tool", "", true))
                .option(Option.bool("garden_mouse_lock.auto_rod", "With a fishing rod", "", false))
                .option(Option.bool("garden_mouse_lock.auto_mousemat", "With a Squeaky Mousemat", "", false))
                .option(Option.bool("garden_mouse_lock.auto_vacuum", "With a vacuum", "", false))
                .option(Option.bool("garden_mouse_lock.auto_sprayonator", "With a Sprayonator", "", false))
                .option(Option.bool("garden_mouse_lock.lock", "Lock instead of lowering", "By itself the mouse locks instead of slowing down.", false))
                .option(Option.integer("garden_mouse_lock.percent", "Lowered to (%)", "Of your normal sensitivity.", 10, 0, 100))
                .option(Option.bool("garden_mouse_lock.ground", "Only on the ground", "By itself, only while on or near the ground.", true))
                .option(Option.integer("garden_mouse_lock.tolerance_pixels", "Near the ground (pixels)", "How close to it counts, in sixteenths of a block. Dirt to soul sand is 2.", 2, 0, 16))
                .option(Option.bool("garden_mouse_lock.only_plot", "Not in the barn or a greenhouse", "By itself not on the barn plot or in a greenhouse.", true))
                .option(Option.choice("garden_mouse_lock.unlock_teleport", "Unlock when you teleport", "After a command, when you teleport to a plot.", "Always", "Always", "Barn only", "Never"))
                .option(Option.bool("garden_mouse_lock.lock_mousemat", "Lock on the mousemat", "Lock when you snap to a Squeaky Mousemat.", false))
                .option(Option.bool("garden_mouse_lock.chat", "Say it in chat", "When a command or a teleport changes it.", true))
                .option(Option.bool("garden_mouse_lock.card", "Show a card", "While it is locked or lowered.", true));
    }

    // tests: what the 2 commands do
    static void commandLock() {
        command(State.LOCKED, LOCK_ON, LOCK_OFF);
    }

    static void commandReduce() {
        command(State.REDUCED, REDUCE_ON, REDUCE_OFF);
    }

    // tests: what the tick sets on the garden while something below is true
    static void autoForTest(boolean locked) {
        auto = locked ? State.LOCKED : State.REDUCED;
        update();
    }

    public static void register() {
        Feature f = Features.add(feature());
        ChatBus.register(ChatBus.OBSERVE + 21, ID, f, MouseLock::onChat);
        Safe.commands("MouseLock.commands", (d, r) -> d.register(literal("sbc")
                .then(literal("mouselock").executes(c -> command(State.LOCKED, LOCK_ON, LOCK_OFF)))
                .then(literal("sensreduce").executes(c -> command(State.REDUCED, REDUCE_ON, REDUCE_OFF)))));
        dev.zmuzy.sbc.core.Commands.describe("mouselock", "Lock or unlock your mouse for farming");
        dev.zmuzy.sbc.core.Commands.describe("sensreduce", "Lower or restore your mouse sensitivity for farming");
        HudLayout.add(CARD);
    }
}
