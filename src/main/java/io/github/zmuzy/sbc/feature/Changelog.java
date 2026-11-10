// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.feature;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// docs/CHANGELOG.md, packed in the jar by the build. what's new shows a version's part of it, so the page in the game
// and the changelog on github never say different things
public final class Changelog {
    public record Entry(String area, String text, List<String> more) {}

    public record Section(String name, List<Entry> entries) {}

    private static final Pattern VERSION = Pattern.compile("^## \\[?([^\\]]+?)\\]?(?:\\(.*\\))?\\s*$");
    private static final Pattern LINK = Pattern.compile("\\s+-\\s+\\[[0-9a-f]{7,}\\]\\([^)]*\\)\\s*$");
    private static Map<String, List<Section>> versions;

    private Changelog() {}

    // "Beta 0.2" -> "beta-0.2", the way Releases and the update check name versions
    static String key(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
    }

    // a version's sections in changelog order, empty when the changelog does not have it
    public static List<Section> of(String version) {
        if (versions == null) versions = load();
        return versions.getOrDefault(version, List.of());
    }

    private static Map<String, List<Section>> load() {
        try (InputStream in = Changelog.class.getResourceAsStream("/assets/sbcompanion/changelog.md")) {
            if (in == null) return Map.of();
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Map.of();
        }
    }

    // pure, for tests: "## [Beta 0.2](link)", "### New Features", "#### Items", "+ line - [abc1234](link)", "    + more"
    static Map<String, List<Section>> parse(String text) {
        Map<String, List<Section>> out = new LinkedHashMap<>();
        List<Section> sections = null;
        Section section = null;
        String area = "";
        List<String> more = null;
        for (String raw : text.split("\r?\n")) {
            String line = raw.stripTrailing();
            Matcher v = VERSION.matcher(line);
            if (line.startsWith("## ") && v.matches()) {
                sections = new ArrayList<>();
                out.put(key(v.group(1)), sections);
                section = null;
                area = "";
                continue;
            }
            if (sections == null) continue;
            if (line.startsWith("### ")) {
                section = new Section(line.substring(4).trim(), new ArrayList<>());
                sections.add(section);
                area = "";
                continue;
            }
            if (line.startsWith("#### ")) {
                area = line.substring(5).trim();
                continue;
            }
            String t = line.stripLeading();
            if (!t.startsWith("+ ") || section == null) continue;
            String clean = plain(t.substring(2));
            if (line.startsWith(" ") && more != null) more.add(clean);
            else {
                more = new ArrayList<>();
                section.entries().add(new Entry(area, clean, more));
            }
        }
        return out;
    }

    // the line as a player reads it: no commit link, no markdown marks
    static String plain(String s) {
        return LINK.matcher(s).replaceAll("").replace("**", "").replace("`", "").trim();
    }
}
