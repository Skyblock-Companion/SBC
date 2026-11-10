// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.core.Store;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

// the game as the mining logic sees it (same idea as events.diana.DianaEnv). tests install a Headless
public final class MiningEnv {
    public interface World {
        boolean inSkyBlock();

        // skyhanni currentIsland as our id (DWARVEN_MINES, CRYSTAL_HOLLOWS, MINESHAFT, ...)
        String island();

        // skyhanni graphArea / skyBlockArea: scoreboard area line without the icon
        String area();

        // skyhanni sidebarLinesFormatted (with color codes)
        List<String> scoreboardFormatted();

        // plain tab lines
        List<String> tab();

        // skyhanni BossbarData.getBossbar(): first boss bars legacy text, "" if none
        String bossbar();

        Vec3 playerPos();

        // horizontal unit vector the player faces (Direction.unitVec3i)
        Vec3 facing();

        String playerName();

        boolean inParty();

        void sendCommand(String command);

        void chat(String message);

        void clickableChat(String message, String command, String hover);

        void title(String text);

        void playSound(String id, float pitch);

        Store.MiningProfile profile();
    }

    public static class Headless implements World {
        public boolean skyblock = true, party;
        public String island = "DWARVEN_MINES", area = "";
        public List<String> scoreboard = new ArrayList<>(), tab = new ArrayList<>();
        public String bossbar = "";
        public Vec3 player = Vec3.ZERO, facing = new Vec3(0, 0, 1);
        public final List<String> commands = new ArrayList<>(), chats = new ArrayList<>(), titles = new ArrayList<>(), sounds = new ArrayList<>();
        public Store.MiningProfile profile = new Store.MiningProfile();

        @Override public boolean inSkyBlock() { return skyblock; }
        @Override public String island() { return island; }
        @Override public String area() { return area; }
        @Override public List<String> scoreboardFormatted() { return scoreboard; }
        @Override public List<String> tab() { return tab; }
        @Override public String bossbar() { return bossbar; }
        @Override public Vec3 playerPos() { return player; }
        @Override public Vec3 facing() { return facing; }
        @Override public String playerName() { return "Me"; }
        @Override public boolean inParty() { return party; }
        @Override public void sendCommand(String c) { commands.add(c); }
        @Override public void chat(String m) { chats.add(m); }
        @Override public void clickableChat(String m, String command, String hover) { chats.add(m + " -> " + command); }
        @Override public void title(String t) { titles.add(t); }
        @Override public void playSound(String id, float pitch) { sounds.add(id); }
        @Override public Store.MiningProfile profile() { return profile; }
    }

    public static volatile World world = new Headless();
    public static volatile LongSupplier clock = System::currentTimeMillis;
    public static final long FAR_PAST = Long.MIN_VALUE / 4;

    private MiningEnv() {}

    public static long now() {
        return clock.getAsLong();
    }

    public static long passedSince(long mark) {
        return now() - mark;
    }

    public static boolean isFarPast(long mark) {
        return mark <= FAR_PAST;
    }

    public static Store.MiningProfile profile() {
        return world.profile();
    }

    // from skyhanni IslandTypeTag.IS_COLD
    public static boolean isColdIsland() {
        String i = world.island();
        return i.equals("DWARVEN_MINES") || i.equals("MINESHAFT") || i.equals("SAFARI");
    }

    // from skyhanni IslandTypeTag.MINING
    public static boolean isMiningIsland() {
        String i = world.island();
        return i.equals("GOLD_MINES") || i.equals("DEEP_CAVERNS") || i.equals("DWARVEN_MINES") || i.equals("MINESHAFT") || i.equals("CRYSTAL_HOLLOWS");
    }

    public static String format(long ms) {
        return io.github.zmuzy.sbc.events.hoppity.HoppityEnv.format(ms);
    }

    public static Long duration(String s) {
        return io.github.zmuzy.sbc.events.hoppity.HoppityEnv.duration(s);
    }

    public static long parseLong(String s) {
        return io.github.zmuzy.sbc.events.hoppity.HoppityEnv.parseLong(s);
    }

    public static String sep(long n) {
        return io.github.zmuzy.sbc.events.hoppity.HoppityEnv.sep(n);
    }
}
