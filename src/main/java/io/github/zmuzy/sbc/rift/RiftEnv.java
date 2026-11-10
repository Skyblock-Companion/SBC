// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.Store;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

// what the rift port needs from the game, behind an interface so the logic runs headless in tests. the mc
// side is in Rift, Headless is the test double
public final class RiftEnv {
    public interface World {
        boolean inSkyBlock();

        // from skyhanni SkyBlockUtils.currentIsland as our id (THE_RIFT, ...)
        String island();

        // from skyhanni scoreboardArea / graphArea: sidebar area line minus the icon
        String area();

        // from skyhanni ScoreboardData.sidebarLinesRaw (with color codes)
        List<String> scoreboardRaw();

        Vec3 playerPos();

        boolean onGround();

        // your xp level (hypixel shows rift time there during the race)
        int experienceLevel();

        void chat(String message);

        void clickableChat(String message, String command, String hover);

        void title(String title, String subtitle, int ticks);

        void sendCommand(String command);

        // from skyhanni IslandGraphs.pathFind: nav to a spot
        default void pathFind(String name, Vec3 pos) {}

        // from skyhanni IslandGraphs.stopNavigation, for what pathFind started
        default void stopPath() {}

        Store.RiftProfile profile();
    }

    // test double, everything is a field
    public static final class Headless implements World {
        public boolean inSkyBlock = true;
        public String island = "THE_RIFT", area = "";
        public List<String> scoreboardRaw = new ArrayList<>();
        public Vec3 playerPos = Vec3.ZERO;
        public boolean onGround = true;
        public int experienceLevel;
        public final List<String> chats = new ArrayList<>(), titles = new ArrayList<>(), commands = new ArrayList<>();
        public Store.RiftProfile profile = new Store.RiftProfile();

        @Override public boolean inSkyBlock() { return inSkyBlock; }
        @Override public String island() { return island; }
        @Override public String area() { return area; }
        @Override public List<String> scoreboardRaw() { return scoreboardRaw; }
        @Override public Vec3 playerPos() { return playerPos; }
        @Override public boolean onGround() { return onGround; }
        @Override public int experienceLevel() { return experienceLevel; }
        @Override public void chat(String message) { chats.add(message); }
        @Override public void clickableChat(String m, String command, String hover) { chats.add(m + " -> " + command); }
        @Override public void title(String t, String s, int ticks) { titles.add(t + "|" + s); }
        @Override public void sendCommand(String command) { commands.add(command); }
        @Override public Store.RiftProfile profile() { return profile; }
    }

    public static final long FAR_PAST = Long.MIN_VALUE / 4, FAR_FUTURE = Long.MAX_VALUE / 4;

    public static World world = new Headless();
    public static LongSupplier clock = System::currentTimeMillis;

    private RiftEnv() {}

    public static long now() {
        return clock.getAsLong();
    }

    public static Store.RiftProfile profile() {
        return world.profile();
    }

    // from skyhanni LorenzVec.decodeFromString: "x:y:z" (ints or doubles)
    public static Vec3 vec(String s) {
        String[] p = s.split(":");
        return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
    }
}
