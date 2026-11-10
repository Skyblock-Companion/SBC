// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.diana;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// from skyhanni DelayedRun.runDelayed: runnables fire on the first tick after their delay (runOrNextTick = run now)
final class Delayed {
    private record Job(long at, Runnable run) {}

    private static final List<Job> JOBS = new ArrayList<>();

    private Delayed() {}

    static synchronized void run(long delayMs, Runnable r) {
        JOBS.add(new Job(DianaEnv.now() + delayMs, r));
    }

    // once per tick, tests call it too
    static void tick() {
        List<Job> due = new ArrayList<>();
        synchronized (Delayed.class) {
            long now = DianaEnv.now();
            for (Iterator<Job> it = JOBS.iterator(); it.hasNext(); ) {
                Job j = it.next();
                if (j.at <= now) {
                    due.add(j);
                    it.remove();
                }
            }
        }
        for (Job j : due) j.run.run();
    }

    static synchronized void clear() {
        JOBS.clear();
    }
}
