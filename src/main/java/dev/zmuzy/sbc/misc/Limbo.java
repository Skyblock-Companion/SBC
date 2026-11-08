// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.misc;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

// from skyhanni misc/limbo LimboTimeTracker (time in limbo, pb, what /playtime answers there) + LimboPlaytime
// (a limbo line in detailed /playtime). skyhannis User Luck bit is left out
public final class Limbo {
    public static final String ID = "limbo_time";
    private static final Patterns.P HOURS = Patterns.p("SH.misc.limbo.tooltip.hours.new"), MINUTES = Patterns.p("SH.misc.limbo.tooltip.minutes.new");
    // from skyhanni bedWarsLobbyLimbo: the room under a bed wars lobby that counts as limbo
    private static final AABB BEDWARS_LIMBO = new AABB(-662, 43, -76, -619, 86, -27);
    private static boolean inLimbo, shownBest;
    private static long joinedAt;

    private Limbo() {}

    private static boolean check(Minecraft mc) {
        if ("limbo".equals(GameState.apiServer)) return true;
        return mc.player != null && GameState.apiLobby.startsWith("bedwarslobby") && BEDWARS_LIMBO.contains(mc.player.position());
    }

    private static void leave() {
        if (!inLimbo) return;
        inLimbo = false;
        shownBest = false;
        long seconds = (System.currentTimeMillis() - joinedAt) / 1000;
        MiscStore s = MiscStore.get();
        String time = SmallMisc.time(seconds * 1000);
        if (seconds > s.limboBest) {
            long old = s.limboBest;
            s.limboBest = seconds;
            SmallMisc.say("You were in Limbo for §e" + time + "§f! §d§lPERSONAL BEST§r§f!");
            if (old != 0) SmallMisc.say("Your previous Personal Best was §e" + SmallMisc.time(old * 1000) + "§f.");
        } else SmallMisc.say("You were in Limbo for §e" + time + "§f.");
        s.limboSeconds += seconds;
        MiscStore.save();
    }

    private static void tick(Minecraft mc) {
        boolean now = check(mc);
        if (now && !inLimbo) {
            inLimbo = true;
            joinedAt = System.currentTimeMillis();
        } else if (!now && inLimbo) leave();
        if (!inLimbo) return;
        long passed = System.currentTimeMillis() - joinedAt;
        long best = MiscStore.get().limboBest * 1000;
        if (!shownBest && best != 0 && passed >= best) {
            shownBest = true;
            SmallMisc.say("§d§lPERSONAL BEST§r§f! You've passed your previous record of §e" + SmallMisc.time(best) + "§f! Keep it up!");
        }
        if (mc.gui != null && Features.get(ID).option("limbo_time.hud").bool()) mc.gui.hud.setOverlayMessage(Component.literal("§eIn Limbo since §b" + SmallMisc.time(passed)), false);
    }

    // /playtime doesnt work in limbo so we answer it
    private static boolean onCommand(String command) {
        if (!inLimbo || !command.startsWith("playtime") || !Features.on(ID)) return true;
        long total = MiscStore.get().limboSeconds + (System.currentTimeMillis() - joinedAt) / 1000;
        SmallMisc.say("§aYou have " + total / 3600 + " hours and " + total % 3600 / 60 + " minutes of playtime in Limbo!");
        return false;
    }

    // from skyhanni formatMinutesAsHours: tenths of an hour, rounded
    static String hours(int minutes) {
        int tenths = (minutes + 3) / 6;
        return tenths % 10 == 0 ? String.valueOf(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }

    // from skyhanni addLimbo + remakeList: limbo goes in the sorted "N hours" lines once it hits an hour, in the
    // "N minutes" lines before that. pure, for the test
    static List<Component> withLimbo(List<Component> lines, long seconds) {
        int minutes = (int) (seconds / 60);
        boolean inHours = minutes >= 60;
        Patterns.P block = inHours ? HOURS : MINUTES;
        double mine = inHours ? minutes / 60d : minutes;
        Component line = Component.literal(inHours ? "§b" + hours(minutes) + " hours §7on Limbo" : "§a" + minutes + " minutes §7on Limbo");
        List<Component> out = new ArrayList<>(lines.size() + 1);
        boolean placed = false;
        int lastBlock = -1;
        for (int i = 0; i < lines.size(); i++) if (block.matcher(lines.get(i).getString()).matches()) lastBlock = i;
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = block.matcher(lines.get(i).getString());
            if (!placed && m.matches()) {
                double value;
                try {
                    value = Double.parseDouble(m.group(inHours ? "hours" : "minutes").replace(",", ""));
                } catch (NumberFormatException e) {
                    value = 0;
                }
                if (value < mine) {
                    out.add(line);
                    placed = true;
                }
            }
            out.add(lines.get(i));
            if (!placed && i == lastBlock) {
                out.add(line);
                placed = true;
            }
        }
        if (!placed) out.add(line);
        return out;
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        var screen = Minecraft.getInstance().gui.screen();
        if (screen == null || !GameState.strip(screen.getTitle().getString()).startsWith(SmallMisc.PLAYTIME_MENU)) return;
        if (dev.zmuzy.sbc.item.menus.MenuMarks.hoveredSlot() != 4 || !Features.get(ID).option("limbo_time.playtime").bool()) return;
        long seconds = MiscStore.get().limboSeconds;
        if (seconds <= 120) return;
        List<Component> out = withLimbo(lines, seconds);
        lines.clear();
        lines.addAll(out);
    }

    public static void register() {
        Features.add(new Feature(ID, "Misc", "Time in Limbo", "How long you have been in Limbo, your longest stay, and your Limbo time in Detailed /playtime.", "SkyHanni: Time In Limbo, Limbo Playtime Detailed")
                .when(() -> GameState.onHypixel).tick(Limbo::tick).tooltip(Limbo::tooltip)
                .option(Option.bool("limbo_time.hud", "Time in the action bar", "", true))
                .option(Option.bool("limbo_time.playtime", "In Detailed /playtime", "", true)));
        Safe.allowCommand("Limbo.onCommand", Limbo::onCommand);
    }
}
