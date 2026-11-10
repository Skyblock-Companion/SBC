// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// one card for the 3 dungeon clocks: skyblocker FireFreezeStaffTimer (f3/m3), skyhanni BloodTimer (when to
// kill blood mobs) + DungeonCreationCooldown (when you can start another run)
public final class DungeonTimers {
    private static final String FEATURE = "dungeon_timers";
    private static final String FIRE_FREEZE = "[BOSS] The Professor: Oh? You found my Guardians' one weakness?";
    // from skyhanni DungeonCreationCooldown.join without colors, like its own test lines read
    private static final Pattern ENTERED = Pattern.compile("(?m)^(?:\\[[^\\]]+\\] )?(\\S+) entered (?:MM )?The Catacombs, .*$");
    private static final String ENTRANCE_ID = "102,66";
    private static final Patterns.P ROOM = Patterns.p("SH.dungeon.room");
    private static final Patterns.P BLOOD_MOVE = Patterns.p("SH.dungeons.bloodtimer.moving");

    private static long fireFreezeAt, bloodOpenAt, bloodKillAt, cooldownUntil;
    private static double prediction = -1;
    private static boolean bloodTold, warned;

    private DungeonTimers() {}

    // from skyhanni BloodTimer.selectMoveTime: when the 4th + 5th mob move, from how long the watcher took for
    // his line. -1 = out of range
    static double selectMoveTime(double seconds) {
        if (seconds >= 31 && seconds <= 34) return 36;
        if (seconds >= 28 && seconds < 31) return 33;
        if (seconds >= 25 && seconds < 28) return 30;
        if (seconds >= 22 && seconds < 25) return 27;
        if (seconds >= 1 && seconds < 22) return 24;
        return -1;
    }

    private static boolean bloodOpenLine(String t) {
        for (int i = 0; i <= 6; i++) if (Patterns.get("SH.dungeons.bloodtimer.open." + i).matcher(t).matches()) return true;
        return false;
    }

    // who entered, from a chat messages whole text. null = not that line
    static String enteredName(String plain) {
        Matcher m = ENTERED.matcher(plain);
        return m.find() ? m.group(1) : null;
    }

    static void feed(String plain, String me, long now) {
        String t = plain.trim();
        if (DungeonMarks.opt(FEATURE, "fire_freeze") && t.equals(FIRE_FREEZE)) fireFreezeAt = now + 5700;
        if (DungeonMarks.opt(FEATURE, "blood_move")) {
            if (bloodOpenLine(t)) bloodOpenAt = now;
            if (bloodOpenAt != 0 && BLOOD_MOVE.matcher(t).matches()) {
                double took = (now - bloodOpenAt) / 1000.0 + 0.1;
                prediction = selectMoveTime(took);
                if (prediction > 0) {
                    title("", String.format(Locale.ROOT, "§7Move Prediction: §f%.2fs", prediction));
                    bloodKillAt = bloodOpenAt + (long) ((prediction - 0.15) * 1000) - 100;
                    bloodTold = false;
                }
            }
        }
        String who = enteredName(plain);
        if (who != null) {
            if (who.equals(me)) cooldownUntil = now + 30_000;
            warned = false;
        }
    }

    private static void title(String head, String sub) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        mc.gui.hud.setTitle(Component.literal(head));
        mc.gui.hud.setSubtitle(Component.literal(sub));
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        var p = Minecraft.getInstance().player;
        feed(line.plain(), p == null ? "" : p.getGameProfile().name(), System.currentTimeMillis());
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (!DungeonRun.inDungeon()) {
            fireFreezeAt = bloodOpenAt = bloodKillAt = 0;
            prediction = -1;
        }
        if (fireFreezeAt != 0 && now - fireFreezeAt > 5000) fireFreezeAt = 0;
        if (bloodKillAt != 0 && !bloodTold && now >= bloodKillAt) {
            bloodTold = true;
            title("", "§cKill Blood");
        }
        if (bloodKillAt != 0 && now - bloodKillAt > 5000) {
            bloodKillAt = 0;
            prediction = -1;
        }
        if (!warned && cooldownUntil != 0 && now >= cooldownUntil && DungeonMarks.opt(FEATURE, "creation_chat") && mc.player != null) {
            warned = true;
            mc.player.sendSystemMessage(Component.literal("§b[SBC] §fDungeon creation cooldown is over!"));
        }
    }

    // from skyhanni DungeonCreationCooldown.isEntrance: before the sidebar names a room youre still at the entrance
    private static boolean atEntrance() {
        for (String l : GameState.scoreboardFormatted) {
            Matcher m = ROOM.matcher(l);
            if (m.matches()) return m.group("roomId").equals(ENTRANCE_ID);
        }
        return true;
    }

    private static boolean showCreation() {
        if (!DungeonMarks.opt(FEATURE, "creation") || System.currentTimeMillis() >= cooldownUntil) return false;
        if (DungeonRun.inDungeon()) return !DungeonMarks.opt(FEATURE, "creation_entrance") || atEntrance();
        return GameState.inSkyBlock && DungeonMarks.opt(FEATURE, "creation_outside");
    }

    private static boolean onCommand(String command) {
        if (!command.startsWith("joininstance") || System.currentTimeMillis() >= cooldownUntil || !Features.on(FEATURE) || !DungeonMarks.opt(FEATURE, "creation_block")) return true;
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §fBlocked instance creation due to cooldown! Cooldown: §b" + (cooldownUntil - System.currentTimeMillis()) / 1000 + "s"));
        return false;
    }

    private static String fireFreezeText(long left) {
        return left > 0 ? String.format(Locale.ROOT, "§ein %.2fs", left / 1000f) : "§cNOW";
    }

    private static RichCard.Content liveContent() {
        long now = System.currentTimeMillis();
        List<RichCard.Line> out = new ArrayList<>();
        if (fireFreezeAt != 0) out.add(RichCard.Line.row("Fire Freeze", fireFreezeText(fireFreezeAt - now)).noMeter());
        if (bloodKillAt != 0 && now < bloodKillAt) out.add(RichCard.Line.row("Kill blood", String.format(Locale.ROOT, "§ein %.1fs", (bloodKillAt - now) / 1000f)).noMeter());
        else if (bloodKillAt != 0) out.add(RichCard.Line.row("Kill blood", "§cNOW").noMeter());
        if (showCreation()) out.add(RichCard.Line.row("New run", "§bin " + (cooldownUntil - now + 999) / 1000 + "s").noMeter());
        return new RichCard.Content("", out);
    }

    private static RichCard.Content sampleOf(String freeze, String blood, String creation) {
        List<RichCard.Line> out = new ArrayList<>();
        if (freeze != null) out.add(RichCard.Line.row("Fire Freeze", freeze).noMeter());
        if (blood != null) out.add(RichCard.Line.row("Kill blood", blood).noMeter());
        if (creation != null) out.add(RichCard.Line.row("New run", creation).noMeter());
        return new RichCard.Content("", out);
    }

    public static final HudElement CARD = new RichCard("dungeon_timers", "Dungeon clocks", "Dungeon", Zone.TOP_LEFT, 38, FEATURE) {
        @Override public boolean visible() {
            return !liveContent().lines().isEmpty();
        }

        @Override protected Content live() {
            return liveContent();
        }

        @Override protected Content sample() {
            return sampleOf("§ein 3.20s", "§ein 12.4s", "§bin 18s");
        }

        @Override public List<Content> states() {
            return List.of(sampleOf("§cNOW", null, null));
        }

        @Override protected List<Row> classicRows() {
            List<Row> rows = new ArrayList<>();
            long now = System.currentTimeMillis();
            if (fireFreezeAt != 0) rows.add(new Row("Fire Freeze", fireFreezeText(fireFreezeAt - now), 0));
            if (bloodKillAt != 0) rows.add(new Row("Kill blood", now < bloodKillAt ? String.format(Locale.ROOT, "in %.1fs", (bloodKillAt - now) / 1000f) : "NOW", 0));
            if (showCreation()) rows.add(new Row("New run", "in " + (cooldownUntil - now + 999) / 1000 + "s", 0));
            return rows;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Fire Freeze", "in 3.20s", 0), new Row("Kill blood", "in 12.4s", 0), new Row("New run", "in 18s", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(FEATURE, "Dungeons", "Dungeon clocks", "One card with the Fire Freeze moment in the Floor 3 boss, when to kill the blood mobs, and when you can start another run.", "SkyHanni: Blood Camp Move, Creation Cooldown; Skyblocker: Fire Freeze Staff Timer")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tick(DungeonTimers::tick)
                .option(Option.bool("dungeon_timers.fire_freeze", "Fire Freeze", "A countdown to when to use a Fire Freeze Staff in the Floor 3 boss.", true))
                .option(Option.bool("dungeon_timers.blood_move", "Blood move", "Predicts when to kill the blood mobs to skip the Watcher's wait.", true))
                .option(Option.bool("dungeon_timers.creation", "Creation cooldown", "The time until another dungeon can be started.", true))
                .option(Option.bool("dungeon_timers.creation_entrance", "Only at the entrance", "In a dungeon, only show the cooldown in the entrance room.", true))
                .option(Option.bool("dungeon_timers.creation_outside", "Also outside dungeons", "Show the cooldown on other islands too.", false))
                .option(Option.bool("dungeon_timers.creation_chat", "Say when it ends", "A line in your chat when the cooldown is over.", false))
                .option(Option.bool("dungeon_timers.creation_block", "Stop /joininstance", "Your own /joininstance does not go out while the cooldown runs.", false)));
        ChatBus.register(ChatBus.OBSERVE + 16, "dungeontimers", f, DungeonTimers::onChat);
        Safe.allowCommand("DungeonTimers.onCommand", DungeonTimers::onCommand);
        HudLayout.add(CARD);
    }
}
