// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.crimson;

import io.github.zmuzy.sbc.core.Store;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

// what the crimson port needs from the game. mc side is in Crimson, Headless is the test double
public final class CrimsonEnv {
    public interface World {
        boolean inSkyBlock();

        String island();

        // SkyBlockUtils.graphArea / scoreboardArea
        String area();

        // ScoreboardData.sidebarLinesFormatted
        List<String> scoreboardFormatted();

        // plain tab lines
        List<String> tab();

        Vec3 playerPos();

        String playerName();

        boolean inParty();

        void chat(String message);

        void clickableChat(String message, String command, String hover);

        void title(String title, String subtitle);

        void sendCommand(String command);

        // your own inventory: clean names + count each
        List<ItemCount> lowerInventory();

        Store.CrimsonProfile profile();
    }

    public record ItemCount(String cleanName, String internalName, int count) {}

    public static final class Headless implements World {
        public boolean inSkyBlock = true, inParty;
        public String island = "CRIMSON_ISLE", area = "", playerName = "Player_Name";
        public List<String> scoreboardFormatted = new ArrayList<>(), tab = new ArrayList<>();
        public Vec3 playerPos = Vec3.ZERO;
        public final List<String> chats = new ArrayList<>(), titles = new ArrayList<>(), commands = new ArrayList<>();
        public List<ItemCount> inventory = new ArrayList<>();
        public Store.CrimsonProfile profile = new Store.CrimsonProfile();

        @Override public boolean inSkyBlock() { return inSkyBlock; }
        @Override public String island() { return island; }
        @Override public String area() { return area; }
        @Override public List<String> scoreboardFormatted() { return scoreboardFormatted; }
        @Override public List<String> tab() { return tab; }
        @Override public Vec3 playerPos() { return playerPos; }
        @Override public String playerName() { return playerName; }
        @Override public boolean inParty() { return inParty; }
        @Override public void chat(String message) { chats.add(message); }
        @Override public void clickableChat(String m, String command, String hover) { chats.add(m + " -> " + command); }
        @Override public void title(String t, String s) { titles.add(t + "|" + s); }
        @Override public void sendCommand(String command) { commands.add(command); }
        @Override public List<ItemCount> lowerInventory() { return inventory; }
        @Override public Store.CrimsonProfile profile() { return profile; }
    }

    public static final long FAR_PAST = Long.MIN_VALUE / 4;

    public static World world = new Headless();
    public static LongSupplier clock = System::currentTimeMillis;

    private CrimsonEnv() {}

    public static long now() {
        return clock.getAsLong();
    }

    public static Store.CrimsonProfile profile() {
        return world.profile();
    }

    public static boolean inCrimson() {
        return "CRIMSON_ISLE".equals(world.island());
    }

    // LorenzVec from "x:y:z"
    public static Vec3 vec(String s) {
        String[] p = s.split(":");
        return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
    }
}
