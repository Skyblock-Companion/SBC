// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.chat.Party;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.data.CommandLists;
import dev.zmuzy.sbc.core.data.DataFiles;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// suggestions for hypixels own commands from data already in the jar
// from skyhanni tabcomplete (TabComplete, PlayerTabComplete, WarpTabComplete, GetFromSacksTabComplete,
// ShowItemTabComplete, ViewRecipeCommand)
// and skyblocker Call/Warp/SackItem/RngMeter/JoinInstance/AuctionBazaar Autocomplete, but never their server
// nodes here have no executes on purpose: fabric then sends what you typed to hypixel untouched (unknown
// command). dont add any
public final class TabComplete {
    public static final String ID = "tab_complete";
    // skyblocker SackItemAutocomplete.BAD_CHARACTERS, the stat icons some item names have
    private static final Pattern BAD_CHARACTERS = Pattern.compile("[⸕✧☘✎❈❤❂❁☠α]");
    private static final List<String> SHOW = List.of("item", "helmet", "chestplate", "leggings", "boots", "necklace", "cloak", "belt", "bracelet", "gloves", "pet");

    private static List<String> callNames, sackNames, recipeIds, vipHangouts;

    private TabComplete() {}

    private static boolean opt(String key) {
        Feature f = Features.get(ID);
        if (f == null || !f.active()) return false;
        Option o = f.option(ID + "." + key);
        return o == null || o.bool();
    }

    // players

    // real players on the island, not the npcs that look like them
    private static List<String> islandPlayers() {
        List<String> out = new ArrayList<>();
        var mc = Minecraft.getInstance();
        if (mc.level == null) return out;
        for (var p : mc.level.players()) {
            if (p == mc.player || p.getUUID().version() != 4) continue;
            out.add(p.getGameProfile().name());
        }
        return out;
    }

    private enum Source { ISLAND, PARTY, GUILD, FRIENDS }

    // PlayerTabComplete.getExcluding: everyone the settings allow except these
    private static List<String> players(Source... excluded) {
        Set<Source> skip = Set.of(excluded);
        Set<String> out = new LinkedHashSet<>();
        if (!skip.contains(Source.PARTY)) out.addAll(Party.members());
        if (!skip.contains(Source.FRIENDS) && opt("friends")) out.addAll(PlayerLists.friends(opt("best_friends")));
        if (!skip.contains(Source.GUILD) && opt("guild")) out.addAll(PlayerLists.guild());
        if (!skip.contains(Source.ISLAND) && opt("island_players")) out.addAll(islandPlayers());
        return new ArrayList<>(out);
    }

    // VipVisits.json
    private static List<String> vips() {
        if (vipHangouts == null) {
            vipHangouts = new ArrayList<>();
            JsonObject o = DataFiles.object(DataFiles.Source.SKYHANNI, "VipVisits.json");
            if (o != null && o.has("vipVisits")) for (JsonElement e : o.getAsJsonArray("vipVisits")) vipHangouts.add(e.getAsString());
        }
        return vipHangouts;
    }

    // PlayerTabComplete.suggestions minus /p and /party (chat/Party does those) and /pv (ours)
    private static final CommandSuggestions PLAYERS = new CommandSuggestions(root -> root
            .parent(List.of("f", "friend"), b -> b
                    .parent(List.of("accept", "add", "deny"), c -> c.lazy(() -> players(Source.FRIENDS)))
                    .parent(List.of("best"), c -> c.lazy(() -> PlayerLists.friends(false)))
                    .parent(List.of("remove", "nickname"), c -> c.lazy(() -> PlayerLists.friends(false)))
                    .parent(List.of("list"), c -> c.literal("best"))
                    .literal("help", "notifications", "removeall", "requests"))
            .parent(List.of("g", "guild"), b -> b
                    .parent(List.of("invite"), c -> c.lazy(() -> players(Source.GUILD)))
                    .parent(List.of("kick", "transfer", "setrank", "promote", "demote"), c -> c.lazy(PlayerLists::guild))
                    .parent(List.of("mute", "unmute"), c -> c.lazy(PlayerLists::guild).literal("everyone"))
                    .parent(List.of("member"), c -> c.lazy(PlayerLists::guild))
                    .literal("top", "toggle", "tagcolor", "tag", "slow", "settings", "rename", "quest", "permissions", "party", "onlinemode",
                            "online", "officerchat", "notifications", "mypermissions", "motd", "menu", "members", "log", "leave", "info", "history",
                            "help", "discord", "disband", "create", "chat", "accept"))
            .parent(List.of("w", "msg", "tell", "boop", "boo"), b -> b.lazy(TabComplete::players))
            .parent(List.of("visit"), b -> b.lazy(TabComplete::players).conditional(() -> opt("vip_visits"), c -> c.lazy(TabComplete::vips)))
            .parent(List.of("invite"), b -> b.lazy(TabComplete::players))
            .parent(List.of("ah"), b -> b.lazy(TabComplete::players))
            .parent(List.of("trade"), b -> b.lazy(TabComplete::islandPlayers)));

    private static String withoutSlash(String input) {
        return input.startsWith("/") ? input.substring(1) : input;
    }

    private static CompletableFuture<Suggestions> tree(CommandContext<FabricClientCommandSource> c, SuggestionsBuilder b) {
        Feature f = Features.get(ID);
        if (f == null || !f.active()) return b.buildFuture();
        String input = b.getInput();
        SuggestionsBuilder word = new SuggestionsBuilder(input, input.lastIndexOf(' ') + 1);
        for (String s : PLAYERS.get(withoutSlash(input))) word.suggest(s);
        return word.buildFuture();
    }

    // lists

    // skyblocker CallAutocomplete: a contacts call names, or its name lowercased
    static List<String> callNames(JsonObject contacts) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : contacts.entrySet()) {
            JsonElement names = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject().get("callNames") : null;
            if (names != null && names.isJsonArray()) for (JsonElement n : names.getAsJsonArray()) out.add(n.getAsString());
            else out.add(e.getKey().toLowerCase(Locale.ENGLISH));
        }
        return out;
    }

    private static List<String> callNames() {
        if (callNames == null) {
            JsonObject o = DataFiles.object(DataFiles.Source.NEU, "abiphone.json");
            callNames = o == null ? List.of() : callNames(o);
        }
        return callNames;
    }

    // skyblocker SackItemAutocomplete.loadSackItems, once the item list is loaded
    private static List<String> sackNames() {
        if (sackNames != null) return sackNames;
        JsonObject o = DataFiles.object(DataFiles.Source.NEU, "sacks.json");
        if (o == null || !o.has("sacks") || ItemRepo.ids().isEmpty()) return List.of();
        Set<String> names = new TreeSet<>();
        for (Map.Entry<String, JsonElement> sack : o.getAsJsonObject("sacks").entrySet())
            for (JsonElement id : sack.getValue().getAsJsonObject().getAsJsonArray("contents"))
                names.add(BAD_CHARACTERS.matcher(GameState.strip(ItemRepo.name(id.getAsString()))).replaceAll("").trim());
        return sackNames = new ArrayList<>(names);
    }

    // skyhanni ViewRecipeCommand.toSkyblockCommandId, book = enchanted book id
    static String commandId(String id, boolean book) {
        int semi = id.indexOf(';');
        if (semi < 0) return id;
        return book ? "ENCHANTED_BOOK_" + id.substring(0, semi) + "_" + id.substring(semi + 1) : id.substring(0, semi);
    }

    // skyhanni ViewRecipeCommand.list, stuff with a recipe
    private static List<String> recipeIds() {
        if (recipeIds != null) return recipeIds;
        if (ItemRepo.ids().isEmpty()) return List.of();
        Set<String> out = new TreeSet<>();
        for (String id : ItemRepo.ids()) {
            if (ItemInfo.recipes(id).isEmpty()) continue;
            ItemRepo.Entry e = ItemRepo.get(id);
            out.add(commandId(id, e != null && e.material() != null && e.material().toLowerCase(Locale.ROOT).contains("enchanted_book")));
        }
        if (out.isEmpty()) return List.of();
        return recipeIds = new ArrayList<>(out);
    }

    // whole name suggestions from a list, for what was typed so far of the rest of the line
    private static SuggestionProvider<FabricClientCommandSource> list(String option, Supplier<? extends Iterable<String>> names) {
        return (c, b) -> {
            if ((GameState.inSkyBlock || SbcMod.DEV) && opt(option)) {
                String typed = b.getRemainingLowerCase();
                for (String n : names.get()) if (n.toLowerCase(Locale.ROOT).startsWith(typed)) b.suggest(n);
            }
            return b.buildFuture();
        };
    }

    // skyblocker RngMeterAutocomplete: /rng type subtype
    private static CompletableFuture<Suggestions> rng(CommandContext<FabricClientCommandSource> c, SuggestionsBuilder b) {
        if (!opt("menus")) return b.buildFuture();
        Map<String, List<String>> meters = CommandLists.rngMeters();
        String rest = b.getRemaining();
        int space = rest.indexOf(' ');
        if (space < 0) {
            for (String t : meters.keySet()) if (t.toLowerCase(Locale.ROOT).startsWith(rest.toLowerCase(Locale.ROOT))) b.suggest(t);
            return b.buildFuture();
        }
        String type = rest.substring(0, space), typed = rest.substring(space + 1).toLowerCase(Locale.ROOT);
        SuggestionsBuilder sub = b.createOffset(b.getStart() + space + 1);
        for (Map.Entry<String, List<String>> e : meters.entrySet())
            if (e.getKey().equalsIgnoreCase(type)) for (String s : e.getValue()) if (s.toLowerCase(Locale.ROOT).startsWith(typed)) sub.suggest(s);
        return sub.buildFuture();
    }

    private static void only(CommandDispatcher<FabricClientCommandSource> d, SuggestionProvider<FabricClientCommandSource> provider, String... roots) {
        for (String root : roots) d.register(literal(root).executes(c -> dev.zmuzy.sbc.core.ServerCommand.toServer()).then(argument("args", StringArgumentType.greedyString()).suggests(provider).executes(c -> dev.zmuzy.sbc.core.ServerCommand.toServer())));
    }

    // skyhanni GetFromSacksTabComplete.handleUnderlineReplace: /gfs enchanted_coal 10 asks for Enchanted Coal
    static String gfsUnderlines(String command, Set<String> upperNames) {
        String[] words = command.split(" ");
        if (words.length < 3 || !(words[0].equalsIgnoreCase("gfs") || words[0].equalsIgnoreCase("getfromsacks"))) return command;
        String raw = String.join(" ", List.of(words).subList(1, words.length - 1));
        String real = raw.replace('_', ' ');
        if (real.equals(raw) || !upperNames.contains(real.toUpperCase(Locale.ROOT))) return command;
        return words[0] + " " + real + " " + words[words.length - 1];
    }

    public static void register() {
        Features.add(new Feature(ID, "Commands", "Tab-complete", "Hypixel's commands suggest what they take: warps, contacts, sack items, recipes, players. What you type still goes to Hypixel as typed.", "SkyHanni: Tab Complete; Skyblocker: command autocomplete")
                .when(() -> GameState.onHypixel || SbcMod.DEV)
                .option(Option.bool("tab_complete.warps", "Warps", "/warp and its warp points.", true))
                .option(Option.bool("tab_complete.call", "Contacts", "/call and your Abiphone's contact names.", true))
                .option(Option.bool("tab_complete.gfs", "Sack items", "/gfs and /getfromsacks.", true))
                .option(Option.bool("tab_complete.show", "/show", "Item, helmet, pet and the rest.", true))
                .option(Option.bool("tab_complete.viewrecipe", "/viewrecipe", "Only items with a recipe.", true))
                .option(Option.bool("tab_complete.menus", "/rng and /joininstance", "Meter types and subtypes, instances.", true))
                .option(Option.bool("tab_complete.market", "/bz", "Item names after /bz and /bazaar.", true))
                .option(Option.bool("tab_complete.friends", "Friends", "From /f list and friend messages.", true))
                .option(Option.bool("tab_complete.best_friends", "Only best friends", "Of your friends, only the best ones.", false))
                .option(Option.bool("tab_complete.guild", "Guild members", "From /g list.", true))
                .option(Option.bool("tab_complete.island_players", "Players here", "Other players on your island.", true))
                .option(Option.bool("tab_complete.vip_visits", "VIP visits", "The special users with cake souls, on /visit.", true)));
        ChatBus.register(ChatBus.OBSERVE + 31, "player_lists", null, PlayerLists::onChat);
        Safe.modifyCommand("TabComplete.modifyCommand", command -> {
            if (!command.contains("_") || !opt("gfs")) return command;
            Set<String> upper = new HashSet<>();
            for (String n : sackNames()) upper.add(n.toUpperCase(Locale.ROOT));
            return gfsUnderlines(command, upper);
        });
        Safe.commands("TabComplete.commands", (d, r) -> {
            only(d, list("warps", CommandLists::warps), "warp");
            only(d, list("call", TabComplete::callNames), "call");
            only(d, list("gfs", TabComplete::sackNames), "gfs", "getfromsacks");
            only(d, list("show", () -> SHOW), "show", "showitem", "showoff");
            only(d, list("viewrecipe", TabComplete::recipeIds), "viewrecipe");
            only(d, TabComplete::rng, "rng", "rngmeter");
            only(d, list("menus", () -> CommandLists.joinInstances().keySet()), "joininstance");
            only(d, list("menus", () -> CommandLists.joinInstances("Catacombs")), "joindungeon");
            only(d, list("menus", () -> CommandLists.joinInstances("Kuudra")), "joinkuudra");
            only(d, list("market", dev.zmuzy.sbc.ui.MarketSearchScreen::bazaarNames), "bz", "bazaar");
            only(d, TabComplete::tree, "f", "friend", "g", "guild", "w", "msg", "tell", "boop", "boo", "visit", "invite", "ah", "trade");
        });
    }
}
