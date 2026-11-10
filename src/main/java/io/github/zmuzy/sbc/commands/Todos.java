// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.Commands;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemIcons;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.core.data.DataFiles;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni customtodos: CustomTodosGui (what starts a timer: chat, action bar, tab list, sidebar),
// CustomTodos, CustomTodoDownload
// /sbc todos + the community list from community/CommunityTodos.json. one card here, skyhanni has one per todo
public final class Todos {
    public static final String ID = "custom_todos";
    private static String lastActionBar = "";
    private static List<String> lastTab = List.of(), lastSidebar = List.of();
    private static int ticks;
    private static Map<String, String> community;

    private Todos() {}

    private static String profile() {
        return GameState.profileName;
    }

    // first matchString line, then what it does to the todo. each target only runs its own todos
    private static void feed(Todo.TriggerTarget target, String formatted, String plain, boolean anti) {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Todo t : Saved.get().todos) {
            if (t.triggerTarget != target) continue;
            Todo.MatchType m = t.match(t.ignoreColorCodes ? plain : formatted);
            if (m == Todo.MatchType.MATCH) {
                t.setDoneNow(profile(), now);
                changed = true;
            } else if (anti && m == Todo.MatchType.ANTI_MATCH) {
                t.antiTriggered(profile(), now);
                changed = true;
            }
        }
        if (changed) Saved.save();
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!Saved.get().todos.isEmpty() && GameState.inSkyBlock) feed(Todo.TriggerTarget.CHAT, LegacyText.of(line.component()), line.plain(), true);
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        if (Saved.get().todos.isEmpty()) return;
        String bar = Vitals.actionBarRaw;
        if (bar != null && !bar.equals(lastActionBar)) {
            lastActionBar = bar;
            feed(Todo.TriggerTarget.ACTION_BAR, bar, GameState.strip(bar), true);
        }
        if (++ticks % 20 != 0) return;
        List<String> tab = GameState.tab;
        if (!tab.equals(lastTab)) {
            lastTab = new ArrayList<>(tab);
            for (String line : lastTab) feed(Todo.TriggerTarget.TAB_LIST, line, GameState.strip(line), false);
        }
        List<String> side = GameState.scoreboardFormatted;
        if (!side.equals(lastSidebar)) {
            lastSidebar = new ArrayList<>(side);
            for (String line : lastSidebar) feed(Todo.TriggerTarget.SIDEBAR, line, GameState.strip(line), false);
        }
    }

    // CommunityTodos.json: id -> template
    static Map<String, String> community() {
        if (community == null) {
            community = new LinkedHashMap<>();
            JsonObject o = DataFiles.object(DataFiles.Source.SKYHANNI, "community/CommunityTodos.json");
            if (o != null && o.has("community_todos")) {
                for (JsonElement e : o.getAsJsonArray("community_todos")) {
                    JsonObject t = e.getAsJsonObject();
                    community.put(t.get("id").getAsString(), t.get("todo_data").getAsString());
                }
            }
        }
        return community;
    }

    // skyhanni CustomTodoDownload: adds the community todo with this id, null if none
    static Todo download(String id) {
        for (Map.Entry<String, String> e : community().entrySet()) {
            if (!e.getKey().equalsIgnoreCase(id)) continue;
            Todo t = Todo.fromTemplate(e.getValue());
            if (t == null) return null;
            t.downloaded = true;
            t.downloadedId = e.getKey();
            Saved.get().todos.add(t);
            Saved.save();
            return t;
        }
        return null;
    }

    private static RichCard.Line line(String label, String value, String icon) {
        RichCard.Line l = RichCard.Line.row(label.replace("&&", "§"), value).raw();
        if (icon != null && !icon.isEmpty()) {
            String id = ItemRepo.get(icon.toUpperCase(java.util.Locale.ROOT).replace(' ', '_')) != null ? icon.toUpperCase(java.util.Locale.ROOT).replace(' ', '_') : ItemRepo.idForName(icon);
            if (id != null && !id.isEmpty()) l.icon(ItemIcons.of(id));
        }
        return l;
    }

    public static final HudElement CARD = new RichCard(ID, "Todos", "Commands", Zone.TOP_LEFT, 68, ID) {
        private List<Line> lines() {
            List<Line> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Todo t : Saved.get().todos) {
                String timer;
                try {
                    timer = t.timer(profile(), now);
                } catch (RuntimeException e) {
                    continue;
                }
                if (timer != null) out.add(line(t.label, timer, t.icon));
            }
            return out;
        }

        @Override
        public boolean visible() {
            if (!GameState.inSkyBlock) return false;
            long now = System.currentTimeMillis();
            for (Todo t : Saved.get().todos) if (t.timer(profile(), now) != null) return true;
            return false;
        }

        @Override
        protected Content live() {
            return new Content("", lines());
        }

        @Override
        protected Content sample() {
            return new Content("", List.of(line("Jerry", "§c4m 12s", ""), line("Fetchur", "§aReady", ""), line("Puzzler", "§a2 Left", "")));
        }

        @Override
        protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (Todo t : Saved.get().todos) {
                String timer = t.timer(profile(), now);
                if (timer != null) out.add(new Row(t.label.replace("&&", "§"), timer, 0));
            }
            return out;
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Jerry", "§c4m 12s", 0), new Row("Fetchur", "§aReady", 0));
        }
    };

    private static void say(String text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Commands", "Todos", "Your own timers. A line in chat, the action bar, the tab list or the sidebar starts one, per profile; the card says when it is ready. /sbc todos edits them.", "SkyHanni: Custom Todos")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tick(Todos::tick));
        ChatBus.register(ChatBus.OBSERVE + 32, ID, f, Todos::onChat);
        HudLayout.add(CARD);
        Commands.describe("todos", "Edit your todos");
        Commands.describe("downloadtodo", "Add a todo from the community list: /sbc downloadtodo <name>");
        Safe.commands("Todos.commands", (d, r) -> d.register(literal("sbc")
                .then(literal("todos").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new TodosScreen(mc.gui.screen())));
                    return 1;
                }))
                .then(literal("downloadtodo").executes(c -> {
                    community().keySet().forEach(id -> say("§e" + id));
                    return 1;
                }).then(argument("name", StringArgumentType.greedyString()).suggests((c, b) -> {
                    for (String id : community().keySet()) if (id.toLowerCase(java.util.Locale.ROOT).startsWith(b.getRemainingLowerCase())) b.suggest(id);
                    return b.buildFuture();
                }).executes(c -> {
                    Todo t = download(StringArgumentType.getString(c, "name"));
                    say(t == null ? "§cTodo not found." : "Todo added. /sbc todos edits it.");
                    return t == null ? 0 : 1;
                })))));
    }
}
