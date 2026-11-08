// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.events.hoppity;

import dev.zmuzy.sbc.core.Store;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

// the game as hoppity sees it (same idea as diana's DianaEnv), tests put a Headless
public final class HoppityEnv {
    public interface World {
        boolean inSkyBlock();

        // SkyBlockUtils.currentIsland as our island id (HUB, THE_PARK, ...)
        String island();

        Vec3 playerPos();

        // egglocator (EGGLOCATOR) in main hand / anywhere in the hotbar
        boolean locatorInHand();

        boolean locatorInHotbar();

        // CurrentPetApi.isCurrentPet("RABBIT;5"): mythic rabbit pet
        boolean hasMythicRabbitPet();

        // ReminderUtils.isBusy: dungeon, kuudra or jacob contest
        boolean isBusy();

        boolean isStrandedProfile();

        String playerName();

        void sendCommand(String command);

        void chat(String message);

        // chat line that runs command on click
        void clickableChat(String message, String command, String hover);

        // our own chat line without prefix, with hover and click command (both can be null)
        default void richChat(String message, String hover, String command) {}

        void title(String text);

        void playSound(String id, float pitch);

        Store.HoppityProfile profile();
    }

    public static class Headless implements World {
        public boolean skyblock = true, locatorHand, locatorHotbar, mythicRabbit = true, busy, stranded;
        public String island = "HUB";
        public Vec3 player = Vec3.ZERO;
        public final List<String> commands = new ArrayList<>(), chats = new ArrayList<>(), titles = new ArrayList<>(), sounds = new ArrayList<>();
        public Store.HoppityProfile profile = new Store.HoppityProfile();

        @Override public boolean inSkyBlock() { return skyblock; }
        @Override public String island() { return island; }
        @Override public Vec3 playerPos() { return player; }
        @Override public boolean locatorInHand() { return locatorHand; }
        @Override public boolean locatorInHotbar() { return locatorHotbar; }
        @Override public boolean hasMythicRabbitPet() { return mythicRabbit; }
        @Override public boolean isBusy() { return busy; }
        @Override public boolean isStrandedProfile() { return stranded; }
        @Override public String playerName() { return "Me"; }
        @Override public void sendCommand(String c) { commands.add(c); }
        @Override public void chat(String m) { chats.add(m); }
        @Override public void clickableChat(String m, String command, String hover) { chats.add(m + " -> " + command); }
        @Override public void richChat(String m, String hover, String command) { chats.add(m); }
        @Override public void title(String t) { titles.add(t); }
        @Override public void playSound(String id, float pitch) { sounds.add(id); }
        @Override public Store.HoppityProfile profile() { return profile; }
    }

    public static volatile World world = new Headless();
    public static volatile LongSupplier clock = System::currentTimeMillis;
    public static final long FAR_PAST = Long.MIN_VALUE / 4, FAR_FUTURE = Long.MAX_VALUE / 4;

    private HoppityEnv() {}

    public static long now() {
        return clock.getAsLong();
    }

    public static long passedSince(long mark) {
        return now() - mark;
    }

    public static boolean isInPast(long mark) {
        return mark <= now();
    }

    public static boolean isFarPast(long mark) {
        return mark <= FAR_PAST;
    }

    public static Store.HoppityProfile profile() {
        return world.profile();
    }

    // TimeUtils.format: "1h 2m 3s" style, biggest units first, max maxUnits units
    public static String format(long ms, int maxUnits) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long d = s / 86400, h = s / 3600 % 24, m = s / 60 % 60, sec = s % 60;
        List<String> parts = new ArrayList<>();
        if (d > 0) parts.add(d + "d");
        if (h > 0) parts.add(h + "h");
        if (m > 0) parts.add(m + "m");
        if (sec > 0 || parts.isEmpty()) parts.add(sec + "s");
        return String.join(" ", parts.subList(0, Math.min(parts.size(), maxUnits)));
    }

    public static String format(long ms) {
        return format(ms, 4);
    }

    // TimeUtils.getDurationOrNull: "8h 8m 6s", "7h59m58s", "59m58s", "8m 6s" -> ms, null if nothing parses
    public static Long duration(String text) {
        if (text == null) return null;
        // TimeUtils.tryAlternativeFormat: "01:03" (m:s) or "1:02:03" (h:m:s)
        String[] colon = text.trim().split(":");
        if (colon.length == 2 || colon.length == 3) {
            try {
                long t = 0;
                for (String c : colon) t = t * 60 + Long.parseLong(c.trim());
                return t * 1000;
            } catch (NumberFormatException ignored) {
            }
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*([dhms])").matcher(text.toLowerCase(java.util.Locale.ROOT));
        long total = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            long v = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "d" -> v * 86_400_000L;
                case "h" -> v * 3_600_000L;
                case "m" -> v * 60_000L;
                default -> v * 1000L;
            };
        }
        return any ? total : null;
    }

    // NumberUtil.formatLong: "7,645,879,859" / "4B" / "60B" / "2M" / "1k" -> number
    public static long parseLong(String s) {
        String t = s.trim().replace(",", "");
        if (t.isEmpty()) return 0;
        char last = Character.toLowerCase(t.charAt(t.length() - 1));
        double mul = switch (last) {
            case 'k' -> 1_000;
            case 'm' -> 1_000_000;
            case 'b' -> 1_000_000_000;
            case 't' -> 1_000_000_000_000L;
            default -> 1;
        };
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return (long) (Double.parseDouble(t) * mul);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String sep(long n) {
        return String.format(java.util.Locale.ROOT, "%,d", n);
    }

    // NumberUtil.shortFormat: 1.2k, 3.4M, 56B
    public static String shortFormat(long n) {
        if (n < 1000) return String.valueOf(n);
        String[] units = {"k", "M", "B", "T"};
        double v = n;
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        String f = v >= 100 ? String.format(java.util.Locale.ROOT, "%.0f", v) : String.format(java.util.Locale.ROOT, "%.1f", v).replace(".0", "");
        return f + units[u];
    }
}
