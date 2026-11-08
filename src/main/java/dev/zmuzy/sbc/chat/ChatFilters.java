// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// chat suite on top of ChatBus: ready filters (one switch each), a repeat filter, and your own rules
// (hide/replace/colour/toast/sound on a regex) via /sbc rule
public final class ChatFilters {
    // action: hide, replace, color, toast or sound
    public static final class Rule {
        public String action = "hide", regex = "", text = "";
        public boolean on = true;
        // rule screen fields. those rules have action "rule" and ChatRules reads them
        public String name = "", chat = "", actionBar = "", announcement = "", toast = "", toastIcon = "", sound = "";
        public boolean partial = false, useRegex = false, ignoreCase = true, formatting = false, hide = true;
        public int announcementTicks = 0, toastSeconds = 0;
        public java.util.List<String> locations = new ArrayList<>();
    }

    // a filter category = feature switch + patterns it hides (on stripped text). loaded from chatfilters.json
    public record Category(String id, String title, String description, boolean def, String[] patterns) {}

    private static List<Category> CATEGORIES = List.of();

    // built-in ones with the override file laid over them by id
    public static synchronized List<Category> categories() {
        if (!CATEGORIES.isEmpty()) return CATEGORIES;
        java.util.LinkedHashMap<String, Category> map = new java.util.LinkedHashMap<>();
        loadInto(map, readResource("/assets/sbcompanion/patterns/chatfilters.json"));
        try {
            java.nio.file.Path f = Config.DIR.resolve("patterns").resolve("chatfilters.json");
            if (java.nio.file.Files.exists(f)) loadInto(map, java.nio.file.Files.readString(f));
        } catch (Throwable ignored) {
        }
        CATEGORIES = new ArrayList<>(map.values());
        return CATEGORIES;
    }

    private static String readResource(String path) {
        try (java.io.InputStream in = ChatFilters.class.getResourceAsStream(path)) {
            return in == null ? "{}" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static void loadInto(Map<String, Category> map, String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("categories")) return;
            for (com.google.gson.JsonElement e : root.getAsJsonArray("categories")) {
                com.google.gson.JsonObject o = e.getAsJsonObject();
                List<String> ps = new ArrayList<>();
                for (com.google.gson.JsonElement p : o.getAsJsonArray("patterns")) ps.add(p.getAsString());
                map.put(o.get("id").getAsString(), new Category(o.get("id").getAsString(), o.get("title").getAsString(),
                        o.has("description") ? o.get("description").getAsString() : "", !o.has("default") || o.get("default").getAsBoolean(), ps.toArray(new String[0])));
            }
        } catch (Exception ex) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("Bad chatfilters.json", ex);
        }
    }

    // pure, for tests. id of the category that hides this stripped line, or null
    public static String hiddenBy(String plain) {
        String t = plain.trim();
        for (Category c : categories()) {
            Pattern[] ps = COMPILED.computeIfAbsent(c.id, k -> compile(c));
            for (Pattern p : ps) if (p.matcher(t).find()) return c.id;
        }
        return null;
    }

    private static Pattern[] compile(Category c) {
        Pattern[] ps = new Pattern[c.patterns.length];
        for (int i = 0; i < ps.length; i++) {
            try {
                ps[i] = Pattern.compile(c.patterns[i]);
            } catch (Exception e) {
                dev.zmuzy.sbc.SbcMod.LOG.warn("Bad chat filter pattern in {}: {}", c.id, c.patterns[i]);
                ps[i] = Pattern.compile("(?!)");
            }
        }
        return ps;
    }

    private static final Map<String, Pattern[]> COMPILED = new HashMap<>();
    private static final Map<String, Pattern> RULES = new HashMap<>();
    private static final Map<String, Long> RECENT = new HashMap<>();

    private static final java.util.ArrayDeque<String> HISTORY = new java.util.ArrayDeque<>();

    private ChatFilters() {}

    public static int copyRecent(int n) {
        List<String> all = new ArrayList<>(HISTORY);
        List<String> last = all.subList(Math.max(0, all.size() - n), all.size());
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", last));
        return last.size();
    }

    public static void register() {
        for (Category c : categories()) {
            COMPILED.put(c.id, compile(c));
            Feature f = Features.add(new Feature(c.id, "Chat", c.title, c.description, "").when(where(c.id)));
            if (!c.def) f.defaultOff();
            ChatBus.register(ChatBus.FILTER, c.id, f, line -> {
                for (Pattern p : COMPILED.get(c.id)) {
                    if (!p.matcher(line.plain().trim()).find()) continue;
                    ChatExtras.filtered(line);
                    return ChatBus.CANCEL;
                }
                return ChatBus.PASS;
            });
        }
        ChatBlocks.register();
        StashCompact.register();
        ChatExtras.register();
        ChatBus.register(ChatBus.FILTER + 5, "hiderank", null, line -> {
            if (!Features.on("chat_hiderank")) return ChatBus.PASS;
            String raw = dev.zmuzy.sbc.core.LegacyText.of(line.component());
            // "§b[MVP§c+§b] Name§f: msg" -> "§bName§f: msg", only player lines (colon in the first 60 chars)
            int colon = raw.indexOf(": ");
            if (colon < 0 || colon > 70 || !raw.contains("[")) return ChatBus.PASS;
            String out = raw.replaceFirst("^((?:\\u00A7.)*)\\[(?:\\u00A7.|[A-Z+]){1,16}\\] ", "$1");
            return out.equals(raw) ? ChatBus.PASS : new ChatBus.Replace(net.minecraft.network.chat.Component.literal(out));
        });
        ChatBus.register(ChatBus.OBSERVE + 3, "history", null, line -> {
            HISTORY.addLast(line.plain());
            while (HISTORY.size() > 60) HISTORY.removeFirst();
            return ChatBus.PASS;
        });
        Feature rep = Features.add(new Feature("chat_repeat", "Chat", "Hide repeated lines", "The same server line twice within 4 seconds is shown once.", "SkyHanni: chat compacting")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        ChatBus.register(ChatBus.COMPACT, "repeat", rep, line -> {
            String t = line.plain().trim();
            if (t.length() < 8) return ChatBus.PASS;
            int colon = t.indexOf(": ");
            if (colon > 0 && colon < 40) return ChatBus.PASS; // players talking is left alone
            long now = System.currentTimeMillis();
            RECENT.values().removeIf(at -> now - at > 4000);
            return RECENT.put(t, now) != null ? ChatBus.CANCEL : ChatBus.PASS;
        });
        Feature rules = Features.add(new Feature("chat_rules", "Chat", "Your own chat rules", "Hide, replace, announce, toast or play a sound on lines that match your text. /sbc rules opens the list; /sbc rule adds one by command.", "SkyHanni: chat rules")
                .when(() -> true)
                .option(Option.integer("chat_rules.announcement_ticks", "Default announcement time", "How long a rule's announcement stays, in ticks, when the rule does not say.", 60, 5, 400)));
        ChatBus.register(ChatBus.RULE, "rules", rules, ChatFilters::applyRules);
    }

    // most work in skyblock, sky mall lines away from the mining islands, lobby lines in lobbies
    private static java.util.function.BooleanSupplier where(String id) {
        return switch (id) {
            case "chat_skymall" -> () -> (GameState.inSkyBlock || SbcMod.DEV) && !dev.zmuzy.sbc.mining.MiningEnv.isMiningIsland();
            case "chat_lobbies" -> () -> (GameState.onHypixel && !GameState.inSkyBlock) || SbcMod.DEV;
            default -> () -> GameState.inSkyBlock || SbcMod.DEV;
        };
    }

    private static Pattern pattern(Rule r) {
        return RULES.computeIfAbsent(r.regex, k -> {
            try {
                return Pattern.compile(k);
            } catch (Exception e) {
                return Pattern.compile("(?!)");
            }
        });
    }

    private static ChatBus.Result applyRules(ChatBus.Line line) {
        ChatBus.Result out = ChatBus.PASS;
        for (Rule r : new ArrayList<>(Config.I.chatRules)) {
            if (!r.on || r.regex.isEmpty()) continue;
            if ("rule".equals(r.action)) {
                ChatBus.Result done = ChatRules.apply(r, line);
                if (done == ChatBus.CANCEL) return done;
                if (done != ChatBus.PASS) out = done;
                continue;
            }
            var m = pattern(r).matcher(line.plain());
            if (!m.find()) continue;
            switch (r.action) {
                case "hide" -> {
                    return ChatBus.CANCEL;
                }
                case "replace" -> out = new ChatBus.Replace(Component.literal(m.replaceAll(r.text.replace("&", "§"))));
                case "color" -> out = new ChatBus.Replace(Component.literal(r.text.replace("&", "§") + line.plain()));
                case "toast" -> Toasts.push("Chat", line.plain().length() > 80 ? line.plain().substring(0, 80) + "…" : line.plain(), 0xFF39D0FF);
                case "sound" -> {
                    var mc = Minecraft.getInstance();
                    if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
                }
                default -> {
                }
            }
        }
        return out;
    }

    // /sbc rule add <action> <regex> [=> text]
    public static String add(String action, String rest) {
        action = action.toLowerCase(java.util.Locale.ROOT);
        if (!List.of("hide", "replace", "color", "toast", "sound").contains(action)) return "Actions: hide, replace, color, toast, sound";
        Rule r = new Rule();
        r.action = action;
        int arrow = rest.indexOf("=>");
        if (arrow >= 0) {
            r.regex = rest.substring(0, arrow).trim();
            r.text = rest.substring(arrow + 2).trim();
        } else r.regex = rest.trim();
        try {
            Pattern.compile(r.regex);
        } catch (Exception e) {
            return "That is not a valid pattern: " + e.getMessage();
        }
        if ((action.equals("replace") || action.equals("color")) && r.text.isEmpty()) return "Use: /sbc rule add " + action + " <pattern> => <text>";
        Config.I.chatRules.add(r);
        Config.save();
        return "Rule " + Config.I.chatRules.size() + " added";
    }

    public static String remove(int n) {
        if (n < 1 || n > Config.I.chatRules.size()) return "No rule " + n;
        Config.I.chatRules.remove(n - 1);
        Config.save();
        return "Rule " + n + " removed";
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        int i = 1;
        for (Rule r : Config.I.chatRules) out.add((i++) + ". " + ("rule".equals(r.action) ? r.name + ":" : r.action) + " /" + r.regex + "/" + (r.text.isEmpty() ? "" : " => " + r.text) + (r.on ? "" : " (off)"));
        return out;
    }
}
