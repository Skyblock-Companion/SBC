// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni StashCompact: the stash reminders ("You have 226 materials stashed away!", the types line,
// the CLICK HERE banner) become one short clickable line when the banner comes, repeated only when the amount
// changes
// click runs the command, only from your click. patterns are skyhanni's without colours, the numbers tell them apart
public final class StashCompact {
    private static final Pattern COUNT = Pattern.compile("^You have ([\\d,]+) (item|material)s? stashed away!.*$");
    private static final Pattern TYPES = Pattern.compile("^\\(This totals ([\\d,]+) types? of (item|material)s? stashed!\\).*$");
    private static final Pattern CLICK = Pattern.compile("^>>> CLICK HERE to pick (?:them|it) up! <<<.*$");
    private static final Pattern ADDED = Pattern.compile("^One or more (?:item|material)s? didn't fit in your inventory and were added to your (?:item|material) stash! Click here to pick them up!$");

    private static final class Message {
        final int count;
        Integer types;

        Message(int count) {
            this.count = count;
        }

        boolean same(Message o) {
            return o != null && o.count == count && java.util.Objects.equals(o.types, types);
        }
    }

    private static final Map<String, Message> CURRENT = new HashMap<>(), LAST = new HashMap<>();
    private static String type;
    private static boolean yeetNextEmpty;

    // settings as a record so the pure part tests without a game
    public record Settings(boolean hideDuplicates, int threshold, boolean hideAdded, boolean viewStash) {
        public static final Settings DEFAULT = new Settings(true, 0, true, false);
    }

    // null = pass, "" = hide, else the compact line
    public static String feed(String plain) {
        return feed(plain, Settings.DEFAULT);
    }

    public static String feed(String plain, Settings s) {
        String t = plain.trim();
        if (yeetNextEmpty) {
            yeetNextEmpty = false;
            if (t.isEmpty()) return "";
        }
        Matcher m = COUNT.matcher(t);
        if (m.matches()) {
            type = m.group(2);
            CURRENT.put(type, new Message(number(m.group(1))));
            return "";
        }
        m = TYPES.matcher(t);
        if (m.matches()) {
            type = m.group(2);
            Message current = CURRENT.get(type);
            if (current != null) current.types = number(m.group(1));
            return "";
        }
        if (CLICK.matcher(t).matches()) {
            yeetNextEmpty = true;
            Message current = type == null ? null : CURRENT.get(type);
            if (current == null || current.count <= s.threshold()) return "";
            if (s.hideDuplicates() && current.same(LAST.get(type))) return "";
            CURRENT.remove(type);
            LAST.put(type, current);
            return line(type, current, s.viewStash());
        }
        if (s.hideAdded() && ADDED.matcher(t).matches()) {
            yeetNextEmpty = true;
            return "";
        }
        return null;
    }

    private static int number(String s) {
        return Integer.parseInt(s.replace(",", ""));
    }

    private static String line(String type, Message m, boolean view) {
        String main = type.equals("item") ? "§e" : "§b", accent = type.equals("item") ? "§6" : "§3";
        String extra = m.types == null ? "" : ", " + main + "totalling " + accent + m.types + " type" + (m.types == 1 ? "" : "s") + main;
        return main + "You have " + accent + String.format(java.util.Locale.ROOT, "%,d", m.count) + " " + main + type + (m.count == 1 ? "" : "s") + " in stash" + extra + ". " + main + "Click to " + accent + (view ? "view" : "pickup") + " " + main + "your stash!";
    }

    public static void reset() {
        CURRENT.clear();
        LAST.clear();
        type = null;
        yeetNextEmpty = false;
    }

    private static boolean opt(String key, boolean def) {
        Feature f = Features.get("chat_stash_compact");
        Option o = f == null ? null : f.option("chat_stash_compact." + key);
        return o == null ? def : o.bool();
    }

    private static Settings settings() {
        Feature f = Features.get("chat_stash_compact");
        Option low = f == null ? null : f.option("chat_stash_compact.low");
        return new Settings(opt("duplicates", true), low == null ? 0 : (int) low.number(), opt("added", true), opt("view", false));
    }

    public static void register() {
        Feature f = Features.add(new Feature("chat_stash_compact", "Chat", "Compact stash reminders",
                "The stash reminder becomes one clickable line, repeated only when the amount changes.", "SkyHanni: Stash Warnings")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("chat_stash_compact.duplicates", "Hide repeats", "A reminder with the same amounts as the last one stays away.", true))
                .option(Option.bool("chat_stash_compact.world", "Once per world", "A repeat shows again after changing worlds.", true))
                .option(Option.bool("chat_stash_compact.added", "Hide added lines", "The line that something went to your stash.", true))
                .option(Option.integer("chat_stash_compact.low", "Hide up to this amount", "Reminders with this many or fewer stay away.", 0, 0, 100000))
                .option(Option.bool("chat_stash_compact.view", "Click only shows the stash", "/viewstash instead of /pickupstash.", false))
                .option(Option.bool("chat_stash_compact.tab", "Tab-complete /viewstash", "Suggests item and material.", true))
                .left(() -> {
                    // onIslandChange, yeetNextEmpty dies with the world too
                    yeetNextEmpty = false;
                    if (opt("world", true)) reset();
                }));
        ChatBus.register(ChatBus.COMPACT - 5, "stash", f, line -> {
            String had = type;
            String r = feed(line.plain(), settings());
            if (r == null) return ChatBus.PASS;
            // the blank line hypixel puts before the block is already in the box
            if (COUNT.matcher(line.plain().trim()).matches()) ChatHistory.deleteRecent(2, 10, String::isBlank);
            if (r.isEmpty()) return ChatBus.CANCEL;
            String shown = type == null ? had : type;
            boolean view = opt("view", false);
            Style style = Style.EMPTY.withClickEvent(new ClickEvent.RunCommand(view ? "/viewstash " + shown : "/pickupstash"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to " + (view ? "view" : "pickup") + " your " + shown + " stash!")));
            return new ChatBus.Replace(Component.literal(r).withStyle(style));
        });
        // TabCompletionEvent "viewstash": goes to the server as you typed it
        Safe.commands("StashCompact.commands", (d, reg) -> d.register(literal("viewstash")
                .executes(c -> io.muzy.sbc.core.ServerCommand.toServer())
                .then(argument("type", StringArgumentType.greedyString()).suggests((c, b) -> {
                    if (opt("tab", true)) for (String s : new String[]{"item", "material"}) if (s.startsWith(b.getRemainingLowerCase())) b.suggest(s);
                    return b.buildFuture();
                }).executes(c -> io.muzy.sbc.core.ServerCommand.toServer()))));
    }
}
