// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.feature;

import java.util.ArrayList;
import java.util.List;

// every version after the first and the features it added. what's new after an update and the version a feature
// came with both read this. a version goes at the top when it is released
public final class Releases {
    // what changed is in docs/CHANGELOG.md (Changelog). this only says which features a version brought
    public record Release(String version, String name, List<String> features) {}

    public static final String FIRST = "beta-0.1";

    public static final List<Release> ALL = List.of(
            new Release("beta-0.2", "Beta 0.2", List.of("sack_helper", "garden_mutation_analyzed")));

    private Releases() {}

    public static String current() {
        return io.github.zmuzy.sbc.core.ProfileServer.VERSION;
    }

    // the version a feature came with, the first one when no later version lists it
    public static String since(String featureId) {
        for (Release r : ALL) if (r.features().contains(featureId)) return r.version();
        return FIRST;
    }

    // "beta-0.10" as numbers, so 0.10 comes after 0.9. a version that cannot be read counts as the oldest
    static int[] numbers(String version) {
        String v = version == null ? "" : version.replaceAll("^[^0-9]*", "");
        if (v.isEmpty()) return new int[]{-1};
        String[] parts = v.split("[^0-9]+");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    public static int compare(String a, String b) {
        int[] x = numbers(a), y = numbers(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return Integer.compare(p, q);
        }
        return 0;
    }

    // the versions after last, up to this one, newest first: everything someone who skipped versions has not seen
    public static List<Release> newerThan(String last) {
        List<Release> out = new ArrayList<>();
        for (Release r : ALL) if (compare(r.version(), last) > 0 && compare(r.version(), current()) <= 0) out.add(r);
        out.sort((a, b) -> compare(b.version(), a.version()));
        return out;
    }
}
