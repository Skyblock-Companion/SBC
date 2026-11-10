// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.diana;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// from skyhanni TimeLimitedSet: entries expire a fixed time after written. add returns if it was already
// there (CacheSet.add)
final class TimeLimitedSet<T> {
    private final long expireMs;
    private final Map<T, Long> map = new LinkedHashMap<>();

    TimeLimitedSet(long expireMs) {
        this.expireMs = expireMs;
    }

    private void sweep() {
        long now = DianaEnv.now();
        for (Iterator<Map.Entry<T, Long>> it = map.entrySet().iterator(); it.hasNext(); ) if (now - it.next().getValue() > expireMs) it.remove();
    }

    // true if it was already in (timer restarts either way)
    synchronized boolean add(T t) {
        sweep();
        boolean had = map.containsKey(t);
        map.put(t, DianaEnv.now());
        return had;
    }

    synchronized void addAll(Iterable<? extends T> ts) {
        for (T t : ts) add(t);
    }

    synchronized boolean contains(T t) {
        sweep();
        return map.containsKey(t);
    }

    synchronized int size() {
        sweep();
        return map.size();
    }

    synchronized List<T> toList() {
        sweep();
        return new ArrayList<>(map.keySet());
    }

    synchronized void clear() {
        map.clear();
    }
}
