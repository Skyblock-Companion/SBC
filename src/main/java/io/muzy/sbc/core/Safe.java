// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import io.muzy.sbc.SbcMod;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

// an exception in the mod must never close the game or kick you off hypixel. everything the game calls goes
// through here (like Features.guard for hooks): a throw gets logged and that listener sits out a minute
public final class Safe {
    static final long WINDOW = 30_000, REST = 60_000;
    static final int STRIKES = 5;
    // clock, so a test doesnt have to wait a minute
    static LongSupplier clock = System::currentTimeMillis;

    private static final class State {
        long windowStart = Long.MIN_VALUE / 2, lastLog = Long.MIN_VALUE / 2, restUntil = Long.MIN_VALUE / 2;
        int strikes;
    }

    private static final Map<String, State> STATES = new ConcurrentHashMap<>();
    private static volatile boolean anyResting;
    private static volatile long lastWake = Long.MIN_VALUE / 2;
    private Safe() {}

    // is this listener sitting out its minute. hooks run on worker threads too
    public static boolean resting(String name) {
        return anyResting && restingNow(name);
    }

    private static boolean restingNow(String name) {
        long now = clock.getAsLong();
        if (now >= lastWake) {
            anyResting = false;
            return false;
        }
        State s = STATES.get(name);
        if (s == null) return false;
        synchronized (s) {
            return now < s.restUntil;
        }
    }

    // takes a throw from a listener. returns normally for everything except stuff nothing can survive
    public static void caught(String name, Throwable t) {
        if (t instanceof VirtualMachineError e) throw e;
        long now = clock.getAsLong();
        State s = STATES.computeIfAbsent(name, k -> new State());
        boolean log, rest = false;
        synchronized (s) {
            if (now - s.windowStart > WINDOW) {
                s.windowStart = now;
                s.strikes = 0;
            }
            log = now - s.lastLog >= WINDOW;
            if (log) s.lastLog = now;
            if (++s.strikes >= STRIKES) {
                rest = true;
                s.strikes = 0;
                s.windowStart = now;
                s.restUntil = now + REST;
            }
        }
        if (rest) {
            lastWake = Math.max(lastWake, now + REST);
            anyResting = true;
        }
        try {
            if (log) SbcMod.LOG.warn("{} failed", name, t);
            if (rest) SbcMod.LOG.warn("{} failed {} times within {} seconds: it rests for a minute", name, STRIKES, WINDOW / 1000);
        } catch (Exception ignored) {
            // a throwable whose own toString throws must not undo all this
        }
    }

    // forget every state, for tests
    static void reset() {
        STATES.clear();
        anyResting = false;
        lastWake = Long.MIN_VALUE / 2;
    }

    public static void run(String name, Runnable r) {
        if (resting(name)) return;
        long t0 = Perf.begin();
        try {
            r.run();
        } catch (Throwable t) {
            caught(name, t);
        } finally {
            if (t0 != 0) Perf.end("hook:" + name, t0);
        }
    }

    // the suppliers answer, or fallback when it throws or rests
    public static boolean test(String name, boolean fallback, BooleanSupplier s) {
        if (resting(name)) return fallback;
        try {
            return s.getAsBoolean();
        } catch (Throwable t) {
            caught(name, t);
            return fallback;
        }
    }

    // game ticks, a throw here ends the game

    public static void startTick(String name, ClientTickEvents.StartTick l) {
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (resting(name)) return;
            long t0 = Perf.begin();
            try {
                l.onStartTick(mc);
            } catch (Throwable t) {
                caught(name, t);
            } finally {
                if (t0 != 0) Perf.end("tick:" + name, t0);
            }
        });
    }

    public static void endTick(String name, ClientTickEvents.EndTick l) {
        ClientTickEvents.END_CLIENT_TICK.register(guarded(name, l));
    }

    static ClientTickEvents.EndTick guarded(String name, ClientTickEvents.EndTick l) {
        return mc -> {
            if (resting(name)) return;
            long t0 = Perf.begin();
            try {
                l.onEndTick(mc);
            } catch (Throwable t) {
                caught(name, t);
            } finally {
                if (t0 != 0) Perf.end("tick:" + name, t0);
            }
        };
    }

    // commands you send: on a throw the command goes out as typed

    public static void allowCommand(String name, ClientSendMessageEvents.AllowCommand l) {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> test(name, true, () -> l.allowSendCommandMessage(command)));
    }

    public static void modifyCommand(String name, ClientSendMessageEvents.ModifyCommand l) {
        ClientSendMessageEvents.MODIFY_COMMAND.register(command -> {
            if (resting(name)) return command;
            try {
                return l.modifySendCommandMessage(command);
            } catch (Throwable t) {
                caught(name, t);
                return command;
            }
        });
    }

    // the command tree gets rebuilt inside the servers packet, a throw there disconnects, so one bad branch only
    // loses itself
    public static void commands(String name, ClientCommandRegistrationCallback l) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> run(name, () -> l.register(dispatcher, context)));
    }

    // server messages inside the packet handler: on a throw the line stays as it came

    public static void allowGame(String name, ClientReceiveMessageEvents.AllowGame l) {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> test(name, true, () -> l.allowReceiveGameMessage(message, overlay)));
    }

    public static void modifyGame(String name, ClientReceiveMessageEvents.ModifyGame l) {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (resting(name)) return message;
            try {
                return l.modifyReceivedGameMessage(message, overlay);
            } catch (Throwable t) {
                caught(name, t);
                return message;
            }
        });
    }

    public static void game(String name, ClientReceiveMessageEvents.Game l) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> run(name, () -> l.onReceiveGameMessage(message, overlay)));
    }

    // what you do in the world: on a throw the game goes on like nothing listened

    private interface Interact {
        InteractionResult get();
    }

    private static InteractionResult pass(String name, Interact i) {
        if (resting(name)) return InteractionResult.PASS;
        try {
            return i.get();
        } catch (Throwable t) {
            caught(name, t);
            return InteractionResult.PASS;
        }
    }

    public static void attackEntity(String name, AttackEntityCallback l) {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> pass(name, () -> l.interact(player, level, hand, entity, hit)));
    }

    public static void useEntity(String name, UseEntityCallback l) {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> pass(name, () -> l.interact(player, level, hand, entity, hit)));
    }

    public static void useBlock(String name, UseBlockCallback l) {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> pass(name, () -> l.interact(player, level, hand, hit)));
    }

    public static void useItem(String name, UseItemCallback l) {
        UseItemCallback.EVENT.register((player, level, hand) -> pass(name, () -> l.interact(player, level, hand)));
    }

    public static void attackBlock(String name, AttackBlockCallback l) {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> pass(name, () -> l.interact(player, level, hand, pos, direction)));
    }

    // screens: on a throw the key/click reaches the screen like nothing listened

    public static void afterInit(String name, ScreenEvents.AfterInit l) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> run(name, () -> l.afterInit(client, screen, width, height)));
    }

    public static void afterTick(String name, Screen screen, ScreenEvents.AfterTick l) {
        ScreenEvents.afterTick(screen).register(s -> run(name, () -> l.afterTick(s)));
    }

    public static void afterExtract(String name, Screen screen, ScreenEvents.AfterExtract l) {
        ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> {
            if (resting(name)) return;
            try {
                l.afterExtract(s, g, mouseX, mouseY, delta);
            } catch (Throwable t) {
                caught(name, t);
            }
        });
    }

    public static void remove(String name, Screen screen, ScreenEvents.Remove l) {
        ScreenEvents.remove(screen).register(s -> run(name, () -> l.onRemove(s)));
    }

    public static void allowMouseClick(String name, Screen screen, ScreenMouseEvents.AllowMouseClick l) {
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> test(name, true, () -> l.allowMouseClick(s, event)));
    }

    public static void allowMouseRelease(String name, Screen screen, ScreenMouseEvents.AllowMouseRelease l) {
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> test(name, true, () -> l.allowMouseRelease(s, event)));
    }

    public static void allowMouseScroll(String name, Screen screen, ScreenMouseEvents.AllowMouseScroll l) {
        ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> test(name, true, () -> l.allowMouseScroll(s, x, y, h, v)));
    }

    public static void allowKeyPress(String name, Screen screen, ScreenKeyboardEvents.AllowKeyPress l) {
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> test(name, true, () -> l.allowKeyPress(s, event)));
    }

    public static void allowCharType(String name, Screen screen, ScreenKeyboardEvents.AllowCharType l) {
        ScreenKeyboardEvents.allowCharType(screen).register((s, event) -> test(name, true, () -> l.allowCharType(s, event)));
    }

    // the rest the game hands out. an entity arriving comes inside a packet handler

    public static void entityLoad(String name, ClientEntityEvents.Load l) {
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> run(name, () -> l.onLoad(entity, level)));
    }

    public static void entityUnload(String name, ClientEntityEvents.Unload l) {
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> run(name, () -> l.onUnload(entity, level)));
    }

    public static void join(String name, ClientPlayConnectionEvents.Join l) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> run(name, () -> l.onPlayReady(handler, sender, client)));
    }

    public static void disconnect(String name, ClientPlayConnectionEvents.Disconnect l) {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> run(name, () -> l.onPlayDisconnect(handler, client)));
    }

    public static void stopping(String name, ClientLifecycleEvents.ClientStopping l) {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> run(name, () -> l.onClientStopping(client)));
    }

    public static void tooltip(String name, ItemTooltipCallback l) {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> run(name, () -> l.getTooltip(stack, context, flag, lines)));
    }

    // a hud layer drawn every frame, so no lambda per frame
    public static HudElement hud(String name, HudElement l) {
        return (g, delta) -> {
            if (resting(name)) return;
            try {
                l.extractRenderState(g, delta);
            } catch (Throwable t) {
                caught(name, t);
            }
        };
    }
}
