// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.ui.CompanionScreen;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// other players in chat, short: /nw and /cata for you, for a name, or (no name) whoever the profile page
// shows. /pv opens that page
// in a dungeon party, what everyone who joins brings so you can decide to keep them
public final class ProfileChat {
    private static final String PREFIX = "§8[§bSBC§8] ";
    private static final Map<String, Long> CHECKED = new HashMap<>();

    private ProfileChat() {}

    private static void say(String line) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.gui.hud.getChat().addClientSystemMessage(Component.literal(line));
        });
    }

    private static String num(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    private static String levelCode(int level) {
        return level >= 50 ? "§6" : level >= 40 ? "§d" : level >= 30 ? "§b" : level >= 20 ? "§a" : "§7";
    }

    // "5:21", like skyblocker's profile viewer
    private static String time(long ms) {
        long seconds = ms / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // one line of a list: " ▸ Museum: 600M"
    private static String item(String label, String value) {
        return " §8▸ §7" + label + ": " + value;
    }

    static List<String> networth(Profile.Data d) {
        List<String> out = new ArrayList<>();
        boolean partial = d.fromGame || !d.knows(io.github.zmuzy.sbc.core.game.GameProfile.NETWORTH);
        out.add(PREFIX + "§f" + d.player + " §7networth §6" + Prices.coins(d.networth()) + (partial ? " §8(only what the game shows)" : ""));
        List<Map.Entry<String, Double>> parts;
        synchronized (d.worth) {
            parts = new ArrayList<>(d.worth.entrySet());
        }
        parts.removeIf(e -> e.getValue() <= 0);
        parts.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        for (var e : parts) out.add(item(e.getKey(), "§6" + Prices.coins(e.getValue())));
        return out;
    }

    // floors with runs, master mode + highest first, a line each: "M7: 30 runs, best S+ 5:21"
    private static List<String> runs(Profile.Data d) {
        if (d.fromGame) return bossKills(d);
        List<Map.Entry<String, Integer>> floors = new ArrayList<>(d.floorCompletions.entrySet());
        floors.removeIf(e -> e.getValue() <= 0);
        floors.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> e.getKey().startsWith("M") ? 0 : 1)
                .thenComparingInt(e -> -Integer.parseInt(e.getKey().substring(1))));
        List<String> out = new ArrayList<>();
        if (floors.isEmpty()) {
            out.add(item("Runs", "§8none"));
            return out;
        }
        int total = 0;
        for (var e : floors) {
            total += e.getValue();
            Profile.Best best = d.bests.get(e.getKey());
            String value = "§f" + num(e.getValue()) + " §7runs" + (best != null && best.sPlus() > 0 ? "§8, §7best S+ §f" + time(best.sPlus()) : "");
            out.add(" §8▸ " + (e.getKey().startsWith("M") ? "§c" : "§a") + e.getKey() + "§7: " + value);
        }
        out.add(item("Total runs", "§f" + num(total)));
        return out;
    }

    // from the game theres no runs to count, boss collections says kills, floor + its master mode together
    private static List<String> bossKills(Profile.Data d) {
        List<String> out = new ArrayList<>();
        if (d.bossKills.isEmpty()) {
            out.add(item("Boss kills", "§8open your Boss Collections once"));
            return out;
        }
        List<Map.Entry<String, Integer>> floors = new ArrayList<>(d.bossKills.entrySet());
        floors.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -Integer.parseInt(e.getKey().substring(1))));
        for (var e : floors) out.add(" §8▸ §a" + e.getKey() + "§7: §f" + num(e.getValue()) + " §7boss kills");
        return out;
    }

    // one line for the party message, a line each for /cata
    private static String classes(Profile.Data d) {
        List<Map.Entry<String, Profile.Level>> list = new ArrayList<>(d.classes.entrySet());
        list.sort(Comparator.comparingInt(e -> -e.getValue().level()));
        StringBuilder sb = new StringBuilder();
        for (var e : list) {
            int level = e.getValue().level();
            sb.append(sb.isEmpty() ? "" : " §8| ").append(levelCode(level)).append(e.getKey()).append(' ').append(level);
        }
        return sb.toString();
    }

    private static List<String> classLines(Profile.Data d) {
        List<Map.Entry<String, Profile.Level>> list = new ArrayList<>(d.classes.entrySet());
        list.sort(Comparator.comparingInt(e -> -e.getValue().level()));
        List<String> out = new ArrayList<>();
        for (var e : list) out.add(item(e.getKey(), levelCode(e.getValue().level()) + e.getValue().level()));
        return out;
    }

    private static String cataHead(Profile.Data d) {
        if (d.catacombs == null) return "§7no Catacombs";
        int level = d.catacombs.level();
        return "§cCata " + levelCode(level) + level + (d.catacombs.progress() < 0 ? "" : " §7" + Math.round(d.catacombs.progress() * 100) + "%");
    }

    private static String secrets(Profile.Data d) {
        if (!d.knows(io.github.zmuzy.sbc.core.game.GameProfile.SECRETS)) return "§7Secrets §8?";
        int runs = d.floorCompletions.values().stream().mapToInt(Integer::intValue).sum();
        String each = runs > 0 ? String.format(Locale.ROOT, " §8(%.1f/run)", (double) d.secrets / runs) : "";
        return "§7Secrets §f" + num(d.secrets) + each;
    }

    static List<String> cata(Profile.Data d) {
        List<String> out = new ArrayList<>();
        out.add(PREFIX + "§f" + d.player + " §8- " + cataHead(d));
        out.add(" §8▸ " + secrets(d).replaceFirst("^§7Secrets ", "§7Secrets: "));
        List<String> classes = classLines(d);
        if (!classes.isEmpty()) {
            out.add("§7Classes");
            out.addAll(classes);
        }
        out.add("§7Floors");
        out.addAll(runs(d));
        return out;
    }

    private static boolean has(Profile.Data d, String id) {
        for (List<Profile.Item> list : d.inventories.values()) for (Profile.Item it : list) if (it != null && id.equals(it.sbId())) return true;
        return false;
    }

    private static String itemName(String id) {
        String n = ItemRepo.name(id);
        return n == null || n.isEmpty() ? id : GameState.strip(n);
    }

    // "Necron's 4/4" for a set, else the full name of the piece
    private static String armor(List<Profile.Item> armor) {
        Map<String, List<String>> sets = new LinkedHashMap<>();
        if (armor != null) for (int i = armor.size() - 1; i >= 0; i--) {
            Profile.Item it = armor.get(i);
            if (it == null || it.sbId() == null || it.sbId().isEmpty()) continue;
            String name = itemName(it.sbId());
            int cut = name.lastIndexOf(' ');
            sets.computeIfAbsent(cut > 0 ? name.substring(0, cut) : name, k -> new ArrayList<>()).add(name);
        }
        StringBuilder sb = new StringBuilder();
        for (var set : sets.entrySet()) {
            sb.append(sb.isEmpty() ? "" : "§8, ").append("§f");
            if (set.getValue().size() > 1) sb.append(set.getKey()).append(" §7").append(set.getValue().size()).append("/4");
            else sb.append(set.getValue().get(0));
        }
        return sb.toString();
    }

    // what someone who joined your dungeon party brings
    static List<String> party(Profile.Data d, String items) {
        List<String> out = new ArrayList<>();
        out.add(PREFIX + "§f" + d.player + " §8- " + cataHead(d) + " §8| §dMP " + (d.knows(io.github.zmuzy.sbc.core.game.GameProfile.MAGICAL_POWER) ? num(d.magicalPower) : "§8?") + " §8| " + secrets(d));
        String classes = classes(d);
        if (!classes.isEmpty()) out.add(classes);
        if (d.inventories.isEmpty()) {
            out.add("§8Their inventory API is off.");
            out.addAll(runs(d));
            return out;
        }
        String armor = armor(d.inventories.get("Armor"));
        out.add(armor.isEmpty() ? "§c✘ §7no armor on" : "§7Armor: " + armor);
        StringBuilder keys = new StringBuilder();
        for (String raw : items.split(",")) {
            String id = raw.trim().toUpperCase(Locale.ROOT);
            if (id.isEmpty()) continue;
            keys.append(keys.isEmpty() ? "" : "  ").append(has(d, id) ? "§a✔ " : "§c✘ ").append("§f").append(itemName(id));
        }
        if (!keys.isEmpty()) out.add(keys.toString());
        out.addAll(runs(d));
        return out;
    }

    private static void show(String name, Function<Profile.Data, List<String>> lines) {
        Profile.Data me = Profile.current, shown = Profile.viewed;
        if (name == null && shown != null) {
            lines.apply(shown).forEach(ProfileChat::say);
            return;
        }
        if (name == null && me != null) {
            lines.apply(me).forEach(ProfileChat::say);
            return;
        }
        var p = Minecraft.getInstance().player;
        String who = name != null ? name : p == null ? null : p.getGameProfile().name();
        if (who == null) return;
        say(PREFIX + "§7Looking up §f" + who + "§7...");
        Profile.fetch(who, d -> lines.apply(d).forEach(ProfileChat::say), error -> say(PREFIX + "§c" + error));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> named(String command, Consumer<String> run) {
        return literal(command).executes(c -> {
            run.accept(null);
            return 1;
        }).then(argument("player", StringArgumentType.word()).suggests((c, b) -> {
            var conn = Minecraft.getInstance().getConnection();
            if (conn != null) for (var i : conn.getOnlinePlayers()) if (i.getProfile().name().toLowerCase(Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(i.getProfile().name());
            return b.buildFuture();
        }).executes(c -> {
            run.accept(StringArgumentType.getString(c, "player"));
            return 1;
        }));
    }

    private static void openProfile(String name) {
        if (name == null) {
            Profile.viewed = null;
            Profile.loadSelfIfOld();
        } else Profile.lookup(name);
        CompanionScreen.page = 7;
        SbcMod.openNextTick = true;
    }

    // --- who joined ---

    private static boolean dungeons() {
        return "DUNGEON_HUB".equals(GameState.island) || "CATACOMBS".equals(GameState.island);
    }

    private static void joined(String name) {
        if (!Profile.serverUp()) return; // other players only readable through the server, nothing to say on every join
        var p = Minecraft.getInstance().player;
        if (p != null && p.getGameProfile().name().equalsIgnoreCase(name)) return;
        long now = System.currentTimeMillis();
        Long at = CHECKED.get(name);
        if (at != null && now - at < 60_000) return;
        CHECKED.put(name, now);
        Feature f = Features.get("party_check");
        String items = f == null || f.option("items") == null ? "HYPERION,TERMINATOR" : f.option("items").text();
        Profile.fetch(name, d -> party(d, items).forEach(ProfileChat::say), error -> say(PREFIX + "§7" + name + ": §c" + error));
    }

    // name of whoever joined your dungeon party per this line, or null
    static String joinedName(String formatted, String plain, boolean inDungeons) {
        Matcher m = ChatMatch.full("SH.data.party.dungeonfinder.join", formatted, plain);
        if (m != null) return GameState.strip(m.group("name")).trim();
        if (!inDungeons) return null;
        m = Patterns.get("Party.JOINED").matcher(plain.trim());
        return m.matches() ? m.group(1) : null;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String name = joinedName(LegacyText.of(line.component()), GameState.strip(line.plain()), dungeons());
        if (name != null) joined(name);
        return ChatBus.PASS;
    }

    public static void register() {
        Features.add(new Feature("party_check", "Dungeons", "Who joined your party", "When someone joins your dungeon party: their Cata and class levels, Magical Power, armor, whether they own the key items, and their runs, in chat. It reads other players, so it stays quiet without the Advanced setting.", "")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.text("party_check.items", "Key items", "SkyBlock item ids with commas between them.", "HYPERION,TERMINATOR")));
        ChatBus.register(ChatBus.OBSERVE + 22, "party_check", Features.get("party_check"), ProfileChat::onChat);
        Safe.commands("ProfileChat.commands", (dispatcher, registry) -> {
            dispatcher.register(named("pv", ProfileChat::openProfile));
            dispatcher.register(named("nw", n -> show(n, ProfileChat::networth)));
            dispatcher.register(named("networth", n -> show(n, ProfileChat::networth)));
            dispatcher.register(named("cata", n -> show(n, ProfileChat::cata)));
        });
    }
}
