// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.chat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the smaller chat ports, one switch each
// from skyhanni ChatFilter (Others, Empty), WatchdogHider, DarkAuctionItemDisplay, RareDropMessages (book
// names), SackApi (sack hider), TrevorFeatures (mob died)
// from skyblocker chat/filters, ChatPositionShare, ConfirmationPromptHelper, CallTrevor
public final class ChatExtras {
    public static final String OTHERS = "chat_others", EMPTY = "chat_empty", SACKS = "chat_hide_sacks", AUCTION = "chat_dark_auction", BOOKS = "chat_book_names", PROMPT = "chat_confirm_prompt", TREVOR = "trevor_chat", OUTPUT = "chat_filtered_lines", MANA_USE = "chat_hide_mana_use";

    private record Group(Set<String> exact, Set<String> coloured, List<Pattern> regex) {
        boolean has(String plain, String formatted) {
            if (exact.contains(plain)) return true;
            if (formatted != null && coloured.contains(formatted)) return true;
            for (Pattern p : regex) if (p.matcher(plain).matches()) return true;
            return false;
        }
    }

    private static final Map<String, Group> GROUPS = new HashMap<>();
    private static final String[] GROUP_IDS = {"slayer", "slayer_drops", "useless_drops", "notifications", "separators", "money", "snow_cannon", "warnings", "spam"};

    private ChatExtras() {}

    // --- others ---

    private static void loadGroups() {
        if (!GROUPS.isEmpty()) return;
        try (var in = ChatExtras.class.getResourceAsStream("/sbc/chat_others.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String id : GROUP_IDS) {
                JsonObject o = root.getAsJsonObject(id);
                Set<String> exact = new HashSet<>(), coloured = new HashSet<>();
                List<Pattern> regex = new ArrayList<>();
                for (JsonElement e : o.getAsJsonArray("exact")) exact.add(e.getAsString());
                if (o.has("coloured")) for (JsonElement e : o.getAsJsonArray("coloured")) coloured.add(e.getAsString());
                for (JsonElement e : o.getAsJsonArray("regex")) regex.add(Pattern.compile(e.getAsString()));
                GROUPS.put(id, new Group(exact, coloured, regex));
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read chat_others.json", e);
        }
    }

    // the group that hides this line or null. pure
    public static String otherGroup(String plain, String formatted) {
        loadGroups();
        String t = plain.trim();
        for (String id : GROUP_IDS) if (GROUPS.containsKey(id) && GROUPS.get(id).has(t, formatted)) return id;
        return null;
    }

    private static boolean opt(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(feature + "." + key);
        return o == null ? def : o.bool();
    }

    private static ChatBus.Result onOthers(ChatBus.Line line) {
        String group = otherGroup(line.plain(), LegacyText.of(line.component()));
        if (group == null || !opt(OTHERS, group, true)) return ChatBus.PASS;
        filtered(line);
        return ChatBus.CANCEL;
    }

    // --- what a filtered line turns into ---

    // from skyblocker ChatFilterResult: a filter hid the line, it can still go to the action bar or a toast
    static void filtered(ChatBus.Line line) {
        Feature f = Features.get(OUTPUT);
        Option where = f == null ? null : f.option(OUTPUT + ".where");
        if (where == null || where.choice().equals("Gone")) return;
        Minecraft mc = Minecraft.getInstance();
        if (where.choice().equals("Action bar")) {
            if (mc.player != null) mc.player.sendOverlayMessage(line.component());
            return;
        }
        Option seconds = f.option(OUTPUT + ".seconds");
        Toasts.push("Chat", line.plain().trim(), 0xFF39D0FF, (seconds == null ? 4 : (int) seconds.number()) * 1000L, "");
    }

    // --- watchdog ---

    private static boolean inWatchdog;
    private static int watchdogLines;
    private static long watchdogAt;
    private static final long WATCHDOG_MS = 2_000;
    private static final Pattern PERSON = Pattern.compile("(?:Party|Guild|Officer|Co-op) > |(?:From|To) \\S");

    // from skyhanni WatchdogHider: announcement up to the blank line that closes it, max 10 lines
    // text only bc a blank line has no colours to compare, hypixel sometimes sends it as a styled piece. first
    // blank after the announcement closes it
    public static boolean watchdog(String plain) {
        String t = plain.trim();
        if (t.equals("[WATCHDOG ANNOUNCEMENT]")) {
            ChatHistory.deleteRecent(1, 10, String::isBlank);
            inWatchdog = true;
            watchdogLines = 0;
            watchdogAt = System.currentTimeMillis();
            return true;
        }
        if (!inWatchdog) return false;
        // its lines come all at once and nobody talks inside. if the blank never comes we dont want to hide whoever talks next
        if (System.currentTimeMillis() - watchdogAt > WATCHDOG_MS || t.contains(": ") || PERSON.matcher(t).lookingAt()) {
            inWatchdog = false;
            return false;
        }
        if (t.isEmpty() || ++watchdogLines > 10) inWatchdog = false;
        return true;
    }

    // --- dark auction ---

    private static final Patterns.P NEXT_ITEM = Patterns.p("SH.chat.darkauction.itemdisplay.next-item"), HIGHEST_BID = Patterns.p("SH.chat.darkauction.itemdisplay.highest-bid");
    private static String lastAuctionItem;

    // from skyhanni DarkAuctionItemDisplay: what to add behind the line, or null
    public static String darkAuction(String plain) {
        Matcher m = NEXT_ITEM.matcher(plain);
        if (m.matches()) {
            lastAuctionItem = m.group("item");
            return null;
        }
        if (HIGHEST_BID.matcher(plain).matches() && lastAuctionItem != null) {
            String item = lastAuctionItem;
            lastAuctionItem = null;
            return " (" + item + ")";
        }
        return null;
    }

    static void resetDarkAuction() {
        lastAuctionItem = null;
    }

    // --- coords in chat ---

    private static final List<Pattern> COORDS = List.of(
            Pattern.compile("A MINOS INQUISITOR has spawned near \\[(?<area>[^]]*)] at Coords (?<x>-?\\d+)(?:\\.\\d*)? (?<y>\\d+)(?:\\.\\d*)? (?<z>-?\\d+)(?:\\.\\d*)?"),
            Pattern.compile("x: (?<x>-?\\d+)(?:\\.\\d*)?, y: (?<y>\\d+)(?:\\.\\d*)?, z: (?<z>-?\\d+)(?:\\.\\d*)?"));
    private static final List<Pattern> LOOSE_COORDS = List.of(
            Pattern.compile("(?<x>-?\\d+)(?:\\.\\d*)?, (?<y>\\d+)(?:\\.\\d*)?, (?<z>-?\\d+)(?:\\.\\d*)?"),
            Pattern.compile("(?<x>-?\\d+)(?:\\.\\d*)? (?<y>\\d+)(?:\\.\\d*)? (?<z>-?\\d+)(?:\\.\\d*)?"));

    // from skyblocker ChatPositionShare: coords in this line or null. pure
    public static int[] coords(String plain) {
        if (plain.startsWith("[SBC]") || plain.startsWith("[NPC] Treasure Hunter:")) return null;
        int[] found = firstIn(COORDS, plain);
        if (found != null) return found;
        // 3 numbers in a row show up in lots of hypixel's own lines (500 of 38,200 real ones), so only in player
        // lines and at a height that exists
        int colon = plain.indexOf(": ");
        if (colon < 0) return null;
        found = firstIn(LOOSE_COORDS, plain.substring(colon + 2));
        return found != null && found[1] <= 320 ? found : null;
    }

    private static int[] firstIn(List<Pattern> patterns, String text) {
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (!m.find()) continue;
            try {
                return new int[]{Integer.parseInt(m.group("x")), Integer.parseInt(m.group("y")), Integer.parseInt(m.group("z"))};
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // --- book names ---

    private static final Patterns.P BOOK_LINE = Patterns.p("SH.raredrop.enchantedbook.colorless");
    // from skyhanni RareDropMessages slayerBookIDPattern
    private static final Pattern SLAYER_BOOK = Patterns.get("SH.raredrop.slayerbook");
    private static final Set<String> NO_BOOK_ISLANDS = Set.of("DARK_AUCTION", "DUNGEON_HUB", "CATACOMBS", "KUUDRA");
    private static Map<String, Integer> books;
    private static int bookTick, screenTick;

    // "ANGLER;6" for a book with one enchant, else ""
    static String bookKey(ItemStack st) {
        if (!"ENCHANTED_BOOK".equals(Gain.sbId(st))) return "";
        var data = st.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        CompoundTag enchants = data.copyTag().getCompoundOrEmpty("enchantments");
        if (enchants.size() != 1) return "";
        String name = enchants.keySet().iterator().next();
        return name.toUpperCase(java.util.Locale.ROOT) + ";" + enchants.getIntOr(name, 0);
    }

    private static void books(Minecraft mc) {
        if (mc.player == null) {
            books = null;
            return;
        }
        // menus move items around, a book taken out of one isnt a drop
        if (mc.gui.screen() != null) {
            books = null;
            screenTick = 0;
            return;
        }
        if (++bookTick % 5 != 0) return;
        Map<String, Integer> now = new HashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            String k = bookKey(inv.getItem(i));
            if (!k.isEmpty()) now.merge(k, inv.getItem(i).getCount(), Integer::sum);
        }
        Map<String, Integer> before = books;
        books = now;
        if (before == null || ++screenTick < 4) return;
        for (var e : now.entrySet()) if (e.getValue() - before.getOrDefault(e.getKey(), 0) == 1) bookAdded(e.getKey());
    }

    private static void bookAdded(String id) {
        if (NO_BOOK_ISLANDS.contains(GameState.island)) return;
        String name = ItemRepo.name(id);
        if (name == null || name.contains(";")) return;
        boolean edited = ChatHistory.editRecent(20, s -> BOOK_LINE.matcher(s).matches() && !s.contains(name),
                c -> Component.literal(LegacyText.of(c).replace("Enchanted Book", name)).withStyle(c.getStyle()));
        // hypixel sends slayer book messages late so these get their own line
        if (!edited && opt(BOOKS, "missing", false) && SLAYER_BOOK.matcher(id).matches()) {
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§6§lRARE DROP! §r" + name));
        }
    }

    // --- confirmation prompts ---

    private static final List<String> PHRASES = List.of("[Aye sure do!]", "[You guessed it!]", "[Sure thing, partner!]", "YES", "Yes");
    // the phrases without colours, the piece can have them in its style instead
    private static final Set<String> PHRASES_PLAIN = Set.of("➜ [Aye sure do!]", "➜ [You guessed it!]", "➜ [Sure thing, partner!]", "[YES]", "[Yes]");
    private static String promptCommand;
    private static long promptAt;

    // the command behind the yes in this line, or null
    static String promptIn(Component message) {
        String text = message.getString();
        if (PHRASES.stream().noneMatch(text::contains)) return null;
        return message.<String>visit((style, part) -> {
            String t = GameState.strip(part).replaceAll("\\s+", " ").trim();
            if (PHRASES_PLAIN.contains(t) && style.getClickEvent() instanceof ClickEvent.RunCommand(String command) && (command.startsWith("/chatprompt") || command.startsWith("/selectnpcoption"))) return Optional.of(command);
            return Optional.empty();
        }, Style.EMPTY).orElse(null);
    }

    private static ChatBus.Result onPrompt(ChatBus.Line line) {
        String command = promptIn(line.component());
        if (command != null) {
            promptCommand = command;
            promptAt = System.currentTimeMillis();
            Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §7Open chat and click anywhere to accept this prompt."));
        }
        return ChatBus.PASS;
    }

    // a click in open chat not on anything else, within the minute
    private static void promptClick(net.minecraft.client.input.MouseButtonEvent event) {
        if (promptCommand == null || promptAt + 60_000 <= System.currentTimeMillis()) return;
        Minecraft mc = Minecraft.getInstance();
        var finder = new ActiveTextCollector.ClickableStyleFinder(mc.font, (int) event.x(), (int) event.y()).includeInsertions(false);
        mc.gui.hud.getChat().captureClickableText(finder, mc.getWindow().getGuiScaledHeight(), mc.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
        Style clicked = finder.result();
        if (clicked != null && clicked.getClickEvent() != null) return;
        var conn = mc.getConnection();
        if (conn != null) conn.sendCommand(promptCommand.substring(1));
        promptCommand = null;
    }

    // --- trevor ---

    private static final Patterns.P MOB_DIED = Patterns.p("SH.misc.trevor.mob.died.colorless");

    private static ChatBus.Result onTrevor(ChatBus.Line line) {
        if (!MOB_DIED.matcher(line.plain().trim()).matches()) return ChatBus.PASS;
        Minecraft mc = Minecraft.getInstance();
        if (opt(TREVOR, "died", true)) {
            mc.gui.hud.setTimes(2, 40, 4);
            mc.gui.hud.setTitle(Component.literal("§2Mob Died"));
            if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
        }
        // CallTrevor: not sent for you, just a line to click
        if (opt(TREVOR, "call", true)) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §bClick here to call Trevor the Trapper!").withStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/call trevor"))));
        }
        return ChatBus.PASS;
    }

    // --- colours ---

    // from skyblocker ColorFormattingHelper: codes for your own rules and lines
    public static void colors() {
        var chat = Minecraft.getInstance().gui.hud.getChat();
        for (String l : List.of("§f&0 = §0Black   §f&1 = §1Dark Blue   §f&2 = §2Dark Green   §f&3 = §3Dark Aqua",
                "§f&4 = §4Dark Red   §f&5 = §5Dark Purple   §f&6 = §6Gold   §f&7 = §7Gray",
                "§f&8 = §8Dark Gray   §f&9 = §9Blue   §f&a = §aGreen   §f&b = §bAqua",
                "§f&c = §cRed   §f&d = §dLight Purple   §f&e = §eYellow   §f&f = §fWhite",
                "§f&k = Obfuscated (§khellspawn§r)   §f&l = §lBold   §r&m = §mStrikethrough",
                "§f&o = §oItalic   §r&n = §nUnderline   §r&r = Reset")) chat.addClientSystemMessage(Component.literal(l));
    }

    // --- switches ---

    public static void register() {
        Feature others = new Feature(OTHERS, "Chat", "Other annoying lines", "Hides what repeats and rarely matters, by kind below. It hides lines that some players want, so it starts off.", "SkyHanni: Chat Filter Others")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff()
                .option(Option.bool("chat_others.slayer", "Slayer quest lines", "Started, complete, boss slain, talk to Maddox, the phone ringing.", true))
                .option(Option.bool("chat_others.slayer_drops", "Common slayer drops", "Viscera, flesh, arrow poison and the like.", true))
                .option(Option.bool("chat_others.useless_drops", "Useless rare drops", "Carrots, potatoes, Crystal Fragments and the like.", true))
                .option(Option.bool("chat_others.notifications", "Notifications", "Mining Speed Boost, 0 coins of interest, tips.", true))
                .option(Option.bool("chat_others.separators", "Party and auction separators", "The long lines above and below those messages.", true))
                .option(Option.bool("chat_others.money", "Orders set up and cancelled", "Bazaar orders and the Auction House reminder.", true))
                .option(Option.bool("chat_others.snow_cannon", "Snow Cannons", "Someone mounted one.", true))
                .option(Option.bool("chat_others.warnings", "Warnings", "Commands too fast, cannot use this in combat, radio signal and the like.", true))
                .option(Option.bool("chat_others.spam", "Repeating lines", "Ability damage, Blessing doubles, cheap Good Catches, needing a stronger tool.", true));
        Features.add(others);
        ChatBus.register(ChatBus.FILTER + 1, OTHERS, others, ChatExtras::onOthers);

        Features.add(new Feature(OUTPUT, "Chat", "Filtered lines", "Where a line goes that a chat filter here takes away: gone, or on the action bar, or a toast.", "Skyblocker: Chat Filter Result")
                .alwaysOn()
                .option(Option.choice("chat_filtered_lines.where", "They go", "", "Gone", "Gone", "Action bar", "Toast"))
                .option(Option.integer("chat_filtered_lines.seconds", "Toast seconds", "", 4, 1, 15)));

        // after the compactors bc some of them read a blank line as the end of their block
        Feature empty = Features.add(new Feature(EMPTY, "Chat", "Empty lines", "Hides every empty line in chat.", "SkyHanni: Empty").when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff());
        ChatBus.register(ChatBus.RULE - 1, EMPTY, empty, line -> line.plain().trim().isEmpty() ? ChatBus.CANCEL : ChatBus.PASS);

        Feature sacks = Features.add(new Feature(SACKS, "Chat", "Hide sack changes", "Hides the [Sacks] line; everything that reads it still does. Use this instead of Hypixel's own setting.", "SkyHanni: Sack Change Hider")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff()
                .option(Option.bool("chat_hide_sacks.garden_only", "Only on the Garden", "", false)));
        ChatBus.register(ChatBus.FILTER + 2, SACKS, sacks, line -> {
            if (!line.plain().startsWith("[Sacks]")) return ChatBus.PASS;
            return !opt(SACKS, "garden_only", false) || "GARDEN".equals(GameState.island) ? ChatBus.CANCEL : ChatBus.PASS;
        });

        Features.add(new Feature(MANA_USE, "Chat", "Hide mana use", "Takes -20 Mana (Ability) off the action bar. Goes well with the bars on the HUD.", "Skyblocker: Hide Mana Consumption Messages")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff());

        Feature auction = Features.add(new Feature(AUCTION, "Chat", "Item in Dark Auction results", "The line that says who won adds which item it was.", "SkyHanni: Dark Auction Item Display")
                .when(() -> "DARK_AUCTION".equals(GameState.island) || SbcMod.DEV).left(ChatExtras::resetDarkAuction));
        ChatBus.register(ChatBus.COMPACT + 5, AUCTION, auction, line -> {
            String more = darkAuction(line.plain());
            return more == null ? ChatBus.PASS : new ChatBus.Replace(line.component().copy().append(Component.literal("§7" + more)));
        });

        Features.add(new Feature(BOOKS, "Chat", "Enchanted book names", "A RARE DROP! Enchanted Book line says which book it was, once it is in your inventory.", "SkyHanni: Enchanted Book Name")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .tick(ChatExtras::books)
                .left(() -> books = null)
                .option(Option.bool("chat_book_names.missing", "Line for silent slayer books", "A slayer book that came without a line gets one.", false)));

        Feature prompt = Features.add(new Feature(PROMPT, "Chat", "Accept prompts with a click", "A yes or no prompt in chat: with chat open, a click anywhere within 60 seconds says yes. Only your click sends it.", "Skyblocker: Chat Confirmation Prompt Helper")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff().left(() -> promptCommand = null));
        ChatBus.register(ChatBus.OBSERVE + 15, PROMPT, prompt, ChatExtras::onPrompt);
        Safe.afterInit("ChatExtras.afterInit", (client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            Safe.allowMouseClick("ChatExtras.allowMouseClick", screen, (s, event) -> {
                if (Features.on(PROMPT)) promptClick(event);
                return true;
            });
        });

        Feature trevor = Features.add(new Feature(TREVOR, "Farming Islands", "Trevor's animal died", "When the animal dies: a title with a sound, and a line to click that calls Trevor.", "SkyHanni: Mob Dead Warning; Skyblocker: Call Trevor")
                .when(() -> "THE_FARMING_ISLANDS".equals(GameState.island) || SbcMod.DEV)
                .option(Option.bool("trevor_chat.died", "Title and sound", "", true))
                .option(Option.bool("trevor_chat.call", "Line to call Trevor", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 16, TREVOR, trevor, ChatExtras::onTrevor);

        // before the filters, they'd eat the announcement before this sees the block start
        ChatBus.register(ChatBus.FILTER - 2, "chat_watchdog_block", Features.get("chat_watchdog"), line -> watchdog(line.plain()) ? ChatBus.CANCEL : ChatBus.PASS);

        CopyChat.register();
        MarkedPlayers.register();
        CurrentChat.register();
        PartyMarks.register();
    }
}
