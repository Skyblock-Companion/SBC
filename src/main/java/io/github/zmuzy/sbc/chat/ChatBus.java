// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.chat;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// every chat line goes through this chain: observers first (trackers, routines), then filters, compactors,
// rules, npc dialogue
// a listener can pass, cancel or replace the line. first cancel stops the chain
public final class ChatBus {
    // lower runs first
    public static final int OBSERVE = 10, FILTER = 100, COMPACT = 200, RULE = 300, DIALOGUE = 400;

    public sealed interface Result permits Pass, Cancel, Replace {}

    public record Pass() implements Result {}

    public record Cancel() implements Result {}

    public record Replace(Component with) implements Result {}

    public static final Result PASS = new Pass(), CANCEL = new Cancel();

    public record Line(Component component, String plain) {}

    public interface Listener {
        Result on(Line line);
    }

    private record Entry(int order, String name, Feature feature, Listener listener) {}

    private static final List<Entry> CHAIN = new ArrayList<>();
    // set while a replaced line gets re-added so it doesnt run through the chain twice
    public static boolean replaying;

    private ChatBus() {}

    // feature can be null for listeners that always run
    public static void register(int order, String name, Feature feature, Listener l) {
        CHAIN.add(new Entry(order, name, feature, l));
        CHAIN.sort(Comparator.comparingInt(Entry::order));
    }

    // null = some listener cancelled it
    public static Component process(Component message) {
        if (replaying || CHAIN.isEmpty()) return message;
        Component cur = message;
        String plain = GameState.strip(cur.getString());
        for (Entry e : CHAIN) {
            if (e.feature != null && !e.feature.active() || e.feature == null && Safe.resting(e.name)) continue;
            Result[] out = {PASS};
            Line line = new Line(cur, plain);
            Runnable run = () -> out[0] = e.listener.on(line);
            if (e.feature != null) Features.guard(e.feature, run);
            else {
                try {
                    run.run();
                } catch (Throwable t) {
                    // line stays as it was, a filter that throws didnt ask for anything
                    Safe.caught(e.name, t);
                }
            }
            if (out[0] instanceof Cancel) return null;
            if (out[0] instanceof Replace r && r.with() != null) {
                cur = r.with();
                plain = GameState.strip(cur.getString());
            }
        }
        return cur;
    }
}
