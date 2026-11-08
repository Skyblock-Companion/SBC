// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.commands;

import dev.zmuzy.sbc.core.Commands;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni TpsCounter + CurrentPing: /sbc tps and /sbc ping in chat
// tps from hypixels ping packets over a rolling window, skipped the first few sec in a world
// /sbc ping sends one request when you type it, the answer lands in the games own ping log
public final class ServerStats {
    // ms per tick over the last ticks, and how long after a world change its too early to say
    private static final int WINDOW = 100;
    private static final long WORLD_SWITCH_DELAY = 5000;

    private static final double[] msPerTick = new double[WINDOW];
    private static int count, at, lastPingId;
    private static long lastTick, worldAt;
    private static Object lastLevel;
    private static long pingAsked;
    private static int pingLogSize;

    private static final java.util.List<Runnable> TICK = new java.util.ArrayList<>();

    private ServerStats() {}

    // l runs once per server tick, on the game thread
    public static void onTick(Runnable l) {
        TICK.add(l);
    }

    // from skyhanni MinecraftData.onPacket. same id again isnt a new tick
    public static void onServerTick(int id) {
        if (id == lastPingId) return;
        lastPingId = id;
        for (Runnable l : TICK) l.run();
        long now = System.nanoTime();
        if (lastTick != 0) {
            msPerTick[at] = (now - lastTick) / 1e6;
            at = (at + 1) % WINDOW;
            if (count < WINDOW) count++;
        }
        lastTick = now;
    }

    // rawTps: ticks per sec over the window, max 20. null if we know nothing yet
    static Double tps(double[] ms, int n) {
        if (n == 0) return null;
        double sum = 0;
        for (int i = 0; i < n; i++) sum += ms[i];
        return sum <= 0 ? null : Math.min(20, 1000.0 / (sum / n));
    }

    // TpsCounter.tps for the card. -1 for 5 sec after a new world or when unknown, 0 when the server is frozen
    public static double currentTps() {
        if (System.currentTimeMillis() - worldAt < WORLD_SWITCH_DELAY) return -1;
        if (stalled()) return 0;
        Double tps = tps(msPerTick, count);
        return tps == null ? -1 : tps;
    }

    // rawTps: no tick for a sec = server is frozen
    private static boolean stalled() {
        return lastTick != 0 && System.nanoTime() - lastTick >= 1_000_000_000L;
    }

    // from skyhanni TpsCounter.getColor
    static String color(double tps) {
        return tps > 19.8 ? "§2" : tps > 19 ? "§a" : tps > 17.5 ? "§6" : tps > 12 ? "§c" : "§4";
    }

    private static void say(String text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    private static int tpsCommand() {
        if (!GameState.onHypixel) {
            say("§cThis command only works on Hypixel!");
            return 0;
        }
        long wait = worldAt + WORLD_SWITCH_DELAY - System.currentTimeMillis();
        Double tps = stalled() ? (Double) 0.0 : tps(msPerTick, count);
        if (tps == null || wait > 0) say("§eTPS: §fCalculating... §7(" + Math.max(1, (wait + 999) / 1000) + "s)");
        else say("§eTPS: " + color(tps) + String.format(java.util.Locale.ROOT, "%.1f", tps));
        return 1;
    }

    private static int pingCommand() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return 0;
        pingLogSize = mc.getDebugOverlay().getPingLogger().size();
        pingAsked = System.currentTimeMillis();
        mc.getConnection().send(new ServerboundPingRequestPacket(net.minecraft.util.Util.getMillis()));
        return 1;
    }

    private static void tick(Minecraft mc) {
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldAt = System.currentTimeMillis();
            count = at = 0;
            lastTick = 0;
        }
        if (pingAsked == 0) return;
        var log = mc.getDebugOverlay().getPingLogger();
        if (log.size() != pingLogSize || log.size() == log.capacity() && System.currentTimeMillis() - pingAsked > 1500) {
            pingAsked = 0;
            if (log.size() > 0) say("Current Ping: " + log.get(log.size() - 1) + " ms");
        } else if (System.currentTimeMillis() - pingAsked > 3000) {
            pingAsked = 0;
            say("§cThe server did not answer in time.");
        }
    }

    public static void register() {
        Commands.describe("tps", "How fast this server runs");
        Commands.describe("ping", "Your ping to this server");
        Safe.endTick("ServerStats.tick", ServerStats::tick);
        Safe.commands("ServerStats.commands", (d, r) -> d.register(literal("sbc")
                .then(literal("tps").executes(c -> tpsCommand()))
                .then(literal("ping").executes(c -> pingCommand()))));
    }
}
