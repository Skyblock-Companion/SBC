// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.events.diana;

import net.minecraft.world.phys.Vec3;

import java.util.function.LongSupplier;

// what diana needs from the game, behind one interface so the ported stuff runs unchanged in junit
// player pos + look, block checks, held item, the few outputs (chat, title, commands). game side is in Diana,
// tests put their own
public final class DianaEnv {
    public interface World {
        // LocationUtils.playerLocation(), the feet
        Vec3 playerPos();

        Vec3 eyePos();

        Vec3 look();

        // BlockUtils.isInLoadedChunk
        boolean inLoadedChunk(Vec3 block);

        // pos.getBlockAt() == Blocks.GRASS_BLOCK
        boolean isGrass(Vec3 block);

        // pos.up().getBlockAt() in GriffinBurrowHelper.allowedBlocksAboveGround
        boolean isAllowedAbove(Vec3 block);

        // DianaApi.hasSpadeInHand(): main hand is a diana spade
        boolean spadeInHand();

        // DianaApi.hasSpadeInHotbar()
        boolean spadeInHotbar();

        // IslandType.HUB.isInIsland()
        boolean onHub();

        // Perk.MYTHOLOGICAL_RITUAL.isActive || Perk.PERKPOCALYPSE.isActive
        boolean ritualActiveByMayor();

        // CurrentPetApi.isCurrentPet("Griffin")
        boolean hasGriffinPet();

        // InventoryUtils.lastItemChangeTime, ms
        long lastItemChange();

        String playerName();

        boolean screenOpen();

        // HypixelCommands.send: server command without the slash
        void sendCommand(String command);

        // ChatUtils.chat: local chat message
        void chat(String message);

        // ChatUtils.clickableChat: local line that runs a command on click
        void clickableChat(String message, String command, String hover);

        // TitleManager.sendTitle
        void title(String text);

        // TitleManager.conditionallyStopTitle
        void stopTitle(String text);
    }

    // empty world for tests, every query says no and every output gets dropped
    public static class Headless implements World {
        public Vec3 player = Vec3.ZERO, look = new Vec3(0, 0, 1);
        public boolean hub = true, ritual = true, spadeHand = true, spadeHotbar = true, griffin = true, loaded = true, grass = true, above = true, screen;
        public long itemChange = Long.MIN_VALUE / 2;
        public final java.util.List<String> commands = new java.util.ArrayList<>(), chats = new java.util.ArrayList<>(), titles = new java.util.ArrayList<>();
        public java.util.function.Predicate<Vec3> grassAt, allowedAboveAt;

        @Override public Vec3 playerPos() { return player; }
        @Override public Vec3 eyePos() { return player.add(0, 1.62, 0); }
        @Override public Vec3 look() { return look; }
        @Override public boolean inLoadedChunk(Vec3 b) { return loaded; }
        @Override public boolean isGrass(Vec3 b) { return grassAt != null ? grassAt.test(b) : grass; }
        @Override public boolean isAllowedAbove(Vec3 b) { return allowedAboveAt != null ? allowedAboveAt.test(b) : above; }
        @Override public boolean spadeInHand() { return spadeHand; }
        @Override public boolean spadeInHotbar() { return spadeHotbar; }
        @Override public boolean onHub() { return hub; }
        @Override public boolean ritualActiveByMayor() { return ritual; }
        @Override public boolean hasGriffinPet() { return griffin; }
        @Override public long lastItemChange() { return itemChange; }
        @Override public String playerName() { return "Me"; }
        @Override public boolean screenOpen() { return screen; }
        @Override public void sendCommand(String c) { commands.add(c); }
        @Override public void chat(String m) { chats.add(m); }
        @Override public void clickableChat(String m, String command, String hover) { chats.add(m + " -> " + command); }
        @Override public void title(String t) { titles.add(t); }
        @Override public void stopTitle(String t) { titles.remove(t); }
    }

    public static volatile World world = new Headless();
    // SimpleTimeMark.now() in ms, tests move it by hand
    public static volatile LongSupplier clock = System::currentTimeMillis;

    private DianaEnv() {}

    public static long now() {
        return clock.getAsLong();
    }

    // SimpleTimeMark.farPast()
    public static final long FAR_PAST = Long.MIN_VALUE / 4;

    public static long passedSince(long mark) {
        return now() - mark;
    }
}
