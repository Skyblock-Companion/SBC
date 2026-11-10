// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.core.Commands;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni reminders: /sbc remind 1h30m Check the forge. ReminderManager, Reminder.shouldRemind,
// ReminderHudDisplay
// comes back every few min until you dismiss it
public final class Reminders {
    public static final String ID = "reminders", HUD = "reminders_hud";
    // from skyhanni UtilsPatterns.timeAmountPattern, the short form preFixDurationString makes
    private static final Pattern AMOUNT = Pattern.compile("(\\d+)\\s*([ydhms])");

    public static final class Reminder {
        public String id = "", reason = "";
        public long remindAt, lastReminder;

        public boolean shouldRemind(long now, long intervalMs) {
            return remindAt <= now && now - lastReminder >= intervalMs;
        }
    }

    private static int ticks, nextId;
    private Reminders() {}

    // from skyhanni TimeUtils.getDurationOrNull, has to be over 1 sec like parseDuration. 1h30m, 1h 30m, 90s,
    // also 1:30 and 1:30:00. null = unreadable
    static Long parse(String text) {
        String t = text.trim().toLowerCase(Locale.ROOT);
        long ms = -1;
        if (t.contains(":")) {
            String[] p = t.split(":");
            try {
                if (p.length == 3) ms = Long.parseLong(p[0]) * 3_600_000 + Long.parseLong(p[1]) * 60_000 + Long.parseLong(p[2]) * 1000;
                else if (p.length == 2) ms = Long.parseLong(p[0]) * 60_000 + Long.parseLong(p[1]) * 1000;
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            Matcher m = AMOUNT.matcher(t);
            int end = 0;
            ms = 0;
            while (m.find()) {
                if (!t.substring(end, m.start()).isBlank()) return null;
                end = m.end();
                long n = Long.parseLong(m.group(1));
                ms += n * switch (m.group(2)) {
                    case "y" -> 365L * 86_400_000;
                    case "d" -> 86_400_000L;
                    case "h" -> 3_600_000L;
                    case "m" -> 60_000L;
                    default -> 1000L;
                };
            }
            if (end == 0 || !t.substring(end).isBlank()) return null;
        }
        return ms <= 1000 ? null : (Long) ms;
    }

    // like skyhanni Duration.format(maxUnits = 2)
    static String format(long ms) {
        long s = Math.max(0, ms / 1000);
        long[] amounts = {s / 86400, s % 86400 / 3600, s % 3600 / 60, s % 60};
        String[] units = {"d", "h", "m", "s"};
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < 4 && used < 2; i++) {
            if (amounts[i] == 0 && !(i == 3 && used == 0)) continue;
            if (used++ > 0) out.append(' ');
            out.append(amounts[i]).append(units[i]);
        }
        return out.toString();
    }

    private static List<Reminder> sorted() {
        List<Reminder> out = new ArrayList<>(Saved.get().reminders);
        out.sort(Comparator.comparingLong(r -> r.remindAt));
        return out;
    }

    private static Reminder find(String id) {
        for (Reminder r : Saved.get().reminders) if (r.id.equals(id)) return r;
        return null;
    }

    private static void chat(Component c) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(c);
    }

    private static void say(String text) {
        chat(Component.literal("§e[Reminder] " + text));
    }

    private static MutableComponent action(String icon, String hover, ClickEvent click) {
        return Component.literal("§8[" + icon + "§8]").withStyle(s -> s.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(Component.literal("§7" + hover))));
    }

    private static int create(String time, String reason) {
        Long ms = parse(time);
        if (ms == null) {
            say("§cInvalid time format. Try 1h30m.");
            return 0;
        }
        Reminder r = new Reminder();
        do r.id = Integer.toString(++nextId, 36); while (find(r.id) != null);
        r.reason = reason;
        r.remindAt = System.currentTimeMillis() + ms;
        Saved.get().reminders.add(r);
        Saved.save();
        say("§6Reminder set for " + format(ms));
        return 1;
    }

    private static int list() {
        List<Reminder> all = sorted();
        if (all.isEmpty()) say("§7No reminders found.");
        DateFormat fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        for (Reminder r : all) {
            chat(Component.empty()
                    .append(action("§c✕", "Click to remove", new ClickEvent.RunCommand("/sbc remind remove " + r.id))).append(" ")
                    .append(action("§e✎", "Click to start editing", new ClickEvent.SuggestCommand("/sbc remind edit " + r.id + " " + r.reason))).append(" ")
                    .append(Component.literal("§8[§6" + fmt.format(new Date(r.remindAt)) + "§8] §7" + r.reason)));
        }
        return 1;
    }

    private static void due(Minecraft mc) {
        long now = System.currentTimeMillis();
        Feature f = Features.get(ID);
        long interval = (long) (f.option("reminders.interval").number() * 60_000);
        boolean autoDelete = f.option("reminders.auto_delete").bool();
        List<String> fired = new ArrayList<>();
        for (Reminder r : sorted()) {
            if (!r.shouldRemind(now, interval)) continue;
            r.lastReminder = now;
            fired.add(r.reason);
            MutableComponent line = Component.literal("§e[Reminder]");
            if (autoDelete) Saved.get().reminders.remove(r);
            else line.append(" ")
                    .append(action("§a✔", "Click to dismiss", new ClickEvent.RunCommand("/sbc remind remove " + r.id))).append(" ")
                    .append(action("§e§l⟳", "Click to move", new ClickEvent.SuggestCommand("/sbc remind move " + r.id + " 1m")));
            chat(line.append(Component.literal(" §6" + r.reason)));
        }
        if (fired.isEmpty()) return;
        Saved.save();
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        if (f.option("reminders.title").bool()) {
            mc.gui.hud.setTitle(Component.literal(fired.size() == 1 ? "§cReminder!" : "§c" + fired.size() + " Reminders!"));
            if (fired.size() == 1) mc.gui.hud.setSubtitle(Component.literal("§e" + fired.get(0)));
        }
    }

    private static RichCard.Line line(long left, String reason) {
        return RichCard.Line.row("§6" + reason, left <= 0 ? "§cNow!" : "§e" + format(left)).raw();
    }

    public static final HudElement CARD = new RichCard(HUD, "Reminders", "Commands", Zone.TOP_LEFT, 69, HUD) {
        @Override
        public boolean visible() {
            return GameState.inSkyBlock && !Saved.get().reminders.isEmpty();
        }

        @Override
        protected Content live() {
            List<Line> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Reminder r : sorted()) out.add(line(r.remindAt - now, r.reason));
            return new Content("", out);
        }

        @Override
        protected Content sample() {
            return new Content("", List.of(line(95 * 60_000L, "Check the forge"), line(0, "Claim the bits")));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Reminder r : sorted()) out.add(new Row(r.reason, r.remindAt <= now ? "§cNow!" : format(r.remindAt - now), 0));
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Check the forge", "1h 35m", 0), new Row("Claim the bits", "§cNow!", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Commands", "Reminders", "/sbc remind 1h30m Check the forge says so when the time comes, here or in any later game, until you dismiss it.", "SkyHanni: Reminders")
                .option(Option.bool("reminders.title", "Title on screen", "When a reminder is due.", true))
                .option(Option.bool("reminders.auto_delete", "Only once", "A reminder is gone after it has been shown.", false))
                .option(Option.number("reminders.interval", "Shown again after", "Minutes until a reminder you did not dismiss comes back.", 5, 1, 120, 1)));
        Features.add(new Feature(HUD, "Commands", "Reminders card", "What reminders are coming and in how long.", "SkyHanni: Show Reminders HUD")
                .when(() -> GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV).defaultOff());
        HudLayout.add(CARD);
        Commands.describe("remind", "A reminder: /sbc remind <time> <text>, list, remove <id>, edit <id> <text>, move <id> <time>");
        Safe.endTick("Reminders.endTick", mc -> {
            if (++ticks % 20 == 0 && Features.on(ID) && !Saved.get().reminders.isEmpty()) due(mc);
        });
        Safe.commands("Reminders.commands", (d, r) -> d.register(literal("sbc").then(literal("remind")
                .executes(c -> list())
                .then(literal("list").executes(c -> list()))
                .then(literal("remove").then(argument("id", StringArgumentType.word()).executes(c -> {
                    Reminder x = find(StringArgumentType.getString(c, "id"));
                    if (x == null) {
                        say("§cReminder not found!");
                        return 0;
                    }
                    Saved.get().reminders.remove(x);
                    Saved.save();
                    say("§cReminder deleted.");
                    return 1;
                })))
                .then(literal("edit").then(argument("id", StringArgumentType.word()).then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    Reminder x = find(StringArgumentType.getString(c, "id"));
                    if (x == null) {
                        say("§cReminder not found!");
                        return 0;
                    }
                    x.reason = StringArgumentType.getString(c, "text");
                    Saved.save();
                    say("§6Reminder edited.");
                    return 1;
                }))))
                .then(literal("move").then(argument("id", StringArgumentType.word()).then(argument("time", StringArgumentType.greedyString()).executes(c -> {
                    Reminder x = find(StringArgumentType.getString(c, "id"));
                    Long ms = parse(StringArgumentType.getString(c, "time"));
                    if (x == null || ms == null) {
                        say(x == null ? "§cReminder not found!" : "§cInvalid time format!");
                        return 0;
                    }
                    x.remindAt = System.currentTimeMillis() + ms;
                    x.lastReminder = 0;
                    Saved.save();
                    say("§6Reminder moved to " + format(ms));
                    return 1;
                }))))
                .then(argument("time", io.github.zmuzy.sbc.core.WordArgument.except("list", "remove", "edit", "move")).then(argument("text", StringArgumentType.greedyString())
                        .executes(c -> create(StringArgumentType.getString(c, "time"), StringArgumentType.getString(c, "text"))))))));
    }
}
