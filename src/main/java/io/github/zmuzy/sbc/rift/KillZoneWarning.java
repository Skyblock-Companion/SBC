// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.Patterns;

import java.util.Locale;
import java.util.regex.Matcher;

// port of skyhanni rift/area/colosseum/KillZoneWarning.kt: every "Get back in the arena or you will DIE!!!"
// line has the warning level in its exclamation marks
public final class KillZoneWarning {
    static final Patterns.P KILL_ZONE = Patterns.p("SH.rift.colosseum.bacte.chat.kill-zone");

    static long lastMessageTime = RiftEnv.FAR_PAST, killDeadline = RiftEnv.FAR_PAST;

    private KillZoneWarning() {}

    // from skyhanni onChatMessage with the formatted line. true = matched (caller plays the sound)
    public static boolean onChat(String formatted) {
        if (!isEnabled()) return false;
        Matcher m = KILL_ZONE.matcher(formatted);
        if (!m.matches()) return false;
        lastMessageTime = RiftEnv.now();
        int warningLevel = m.group("exclamation").length();
        killDeadline = lastMessageTime + 250L * (12 - warningLevel);
        return true;
    }

    // from skyhanni onTick: title to show rn, or null
    public static String[] title() {
        if (!isEnabled()) return null;
        long now = RiftEnv.now();
        if (now - lastMessageTime > 250) return null;
        if (killDeadline < now) return null;
        return new String[]{"§cGet back in the arena!", String.format(Locale.US, "§7%.2fs left", (killDeadline - now) / 1000.0)};
    }

    public static boolean isEnabled() {
        return RiftApi.inColosseum();
    }
}
