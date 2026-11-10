// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core;

import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// when your booster cookie runs out, per profile, like skyhanni BitsApi keeps it: eating one = +4 days, the
// sb menu says whats left. the tab footer says it too when it has a cookie buff line
public final class CookieBuff {
    private static final long FOUR_DAYS = 4 * 86_400_000L;
    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(days?|d|hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b", Pattern.CASE_INSENSITIVE);

    private static String lastTabText = "";
    private static long ateAt;

    private CookieBuff() {}

    private static void store(long until) {
        Store.I.cookieUntil.put(GameState.profileName, until);
        Store.markDirty();
    }

    // "3d 14h 22m", "12 Minutes", "4 Days, 2 Hours" -> ms, -1 = no time in it
    public static long parse(String text) {
        Matcher m = PART.matcher(text);
        long ms = -1;
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            char unit = m.group(2).toLowerCase(Locale.ROOT).charAt(0);
            ms = Math.max(ms, 0) + n * switch (unit) {
                case 'd' -> 86_400_000L;
                case 'h' -> 3_600_000L;
                case 'm' -> 60_000L;
                default -> 1000L;
            };
        }
        return ms;
    }

    // from skyhanni boosterCookieAte: +4 days on top of whats left
    static void ate(long now) {
        Long until = Store.I.cookieUntil.get(GameState.profileName);
        store(Math.max(until == null ? now : until, now) + FOUR_DAYS);
        ateAt = now;
    }

    // chat said you have no cookie
    static void off() {
        store(0);
    }

    // what tab says after "Cookie Buff". only a change counts: right after you eat, a footer the server didnt
    // refresh yet still shows the old time and must not take the 4 days back
    static void fromTab(String text, long now) {
        boolean fresh = !text.equals(lastTabText);
        lastTabText = text;
        if (!fresh || now - ateAt < 15_000) return;
        if (text.toLowerCase(Locale.ROOT).contains("not active")) {
            off();
            return;
        }
        long left = parse(text);
        if (left >= 0) store(now + left);
    }

    // tab has no cookie buff line rn
    static void noTabText() {
        lastTabText = "";
    }

    // from skyhanni handleSkyBlockMenu, on the booster cookie lore
    static void fromMenu(List<String> lore, long now) {
        for (String line : lore) {
            Matcher m = Patterns.get("SH.data.bits.gui.cookieduration").matcher(line);
            if (m.matches()) {
                long left = parse(m.group("time"));
                if (left >= 0) store(now + left);
                return;
            }
            if (Patterns.get("SH.data.bits.gui.sbmenunocookieactive").matcher(line).matches()) {
                off();
                return;
            }
        }
    }

    // checks the sb menu while its open
    public static void tick(Minecraft mc) {
        var screen = mc.gui.screen();
        if (screen == null || mc.player == null || !GameState.strip(screen.getTitle().getString()).trim().equals("SkyBlock Menu")) return;
        AbstractContainerMenu menu = mc.player.containerMenu;
        for (var slot : menu.slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !Patterns.get("SH.data.bits.gui.mainmenustack").matcher(GameState.strip(stack.getHoverName().getString()).trim()).matches()) continue;
            fromMenu(LegacyText.lore(stack), System.currentTimeMillis());
            return;
        }
    }

    // whats left ("4d 0h"), "Not active", or "" when nothing told us yet
    static String status(long now) {
        Long until = Store.I.cookieUntil.get(GameState.profileName);
        if (until == null) return "";
        return until > now ? SbTime.dur(until - now) : "Not active";
    }

    public static String status() {
        return status(System.currentTimeMillis());
    }
}
