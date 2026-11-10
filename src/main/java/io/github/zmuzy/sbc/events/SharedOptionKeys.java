// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.events;

import java.util.Map;

// three settings shared one bare key, so changing one changed the others. each has its own key now and the
// old saved value gets copied to each once (same trick as garden showOnlyBest)
public final class SharedOptionKeys {
    private static final Map<String, String[]> SPLITS = Map.of(
            "on", new String[]{"diana_pet_warning.on", "diana_mute_spade.on", "mining_notifications.on"},
            "color", new String[]{"hoppity_eggs.color", "rift_enigma.color", "rift_punchcard.color"},
            "instantShare", new String[]{"crimson_vanquisher.instantShare", "diana_inquisitor.instantShare"});

    private SharedOptionKeys() {}

    public static void split(Map<String, Object> options) {
        SPLITS.forEach((old, names) -> {
            Object saved = options.remove(old);
            if (saved == null) return;
            for (String name : names) options.putIfAbsent(name, saved);
        });
    }
}
