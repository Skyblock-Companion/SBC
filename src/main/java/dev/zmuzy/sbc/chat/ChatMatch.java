// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.chat;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// colour-coded pattern vs a chat line that might not carry the codes the same way
// hypixel sends some lines as text with codes and others as styled pieces, so try with codes first, then the
// pattern without codes on the stripped line
public final class ChatMatch {
    private static final Pattern ANY_CODE = Pattern.compile("§(?:\\.|\\[[^\\]]*\\]|.)");
    private static final Map<String, Pattern> PLAIN = new ConcurrentHashMap<>();

    private ChatMatch() {}

    // pattern with its codes stripped. a pattern that captures a code like §(?<c>.) cant work without colours,
    // dont put those here
    static String withoutCodes(String regex) {
        String out = ANY_CODE.matcher(regex).replaceAll("");
        return out.replace("(?:)*", "").replace("(?:)+", "").replace("(?:){0,2}", "").replace("(?:)", "");
    }

    // whole-line match or null
    public static Matcher full(String key, String formatted, String plain) {
        Pattern p = Patterns.get(key);
        Matcher m = p.matcher(formatted);
        if (m.matches()) return m;
        Pattern bare = PLAIN.computeIfAbsent(p.pattern(), ChatMatch::compile);
        m = bare.matcher(plain);
        if (m.matches()) return m;
        m = bare.matcher(plain.trim());
        return m.matches() ? m : null;
    }

    public static Matcher full(String key, String formatted) {
        return full(key, formatted, GameState.strip(formatted));
    }

    private static Pattern compile(String regex) {
        return Pattern.compile(withoutCodes(regex));
    }
}
