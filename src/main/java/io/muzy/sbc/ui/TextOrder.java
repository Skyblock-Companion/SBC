// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import net.minecraft.locale.Language;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// vanilla runs bidi on every string every frame, tons of garbage = gc stutter. cache it per string
public final class TextOrder {
    static final int MAX = 4096;
    private static final Map<String, FormattedCharSequence> SEEN = new HashMap<>();
    private static Language language;
    private static Thread owner;

    private TextOrder() {}

    public static FormattedCharSequence get(Language l, String s, Function<String, FormattedCharSequence> work) {
        Thread t = Thread.currentThread();
        if (owner == null) owner = t;
        if (t != owner || s == null) return work.apply(s);
        if (l != language || SEEN.size() >= MAX) {
            SEEN.clear();
            language = l;
        }
        FormattedCharSequence f = SEEN.get(s);
        if (f == null) {
            f = work.apply(s);
            SEEN.put(s, f);
        }
        return f;
    }

    static int size() {
        return SEEN.size();
    }

    static void reset() {
        SEEN.clear();
        language = null;
        owner = null;
    }
}
