// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import com.google.gson.JsonObject;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// the quiver. from skyhanni QuiverApi, QuiverDisplay, QuiverWarning
// which arrow + how many (slot lore, quiver menu, chat), a card, low warning, reminder after a dungeon or kuudra run
// reading is its own always on feature so the card and warning dont depend on each other
public final class Quiver {
    public static final String DISPLAY = "quiver_display", WARNING = "quiver_warning";
    private static final String NONE = "NONE", FLINT = "ARROW", SKELETON_MASTER = "SKELETON_MASTER_CHESTPLATE";
    private static final int MAX = 2880;
    private static final Patterns.P SELECT = Patterns.p("SH.data.quiver.chat.select"), JAX = Patterns.p("SH.data.quiver.chat.fillupjax"), FILL = Patterns.p("SH.data.quiver.chat.fillup"), CLEARED = Patterns.p("SH.data.quiver.chat.cleared"),
            RAN_OUT = Patterns.p("SH.data.quiver.chat.ranout"), RESET = Patterns.p("SH.data.quiver.chat.arrowreset"), ADDED = Patterns.p("SH.data.quiver.chat.addedtoquiver"), FAKE_BOWS = Patterns.p("SH.data.quiver.fakebows"),
            ACTIVE = Patterns.p("SH.data.quiver.quiver.inventory"), PREVIEW = Patterns.p("SH.data.quiver.quiver.preview.amount");

    private static Map<String, String> arrows;
    private static boolean hasBow, skeletonMaster;
    private static long lastLow;
    private static final Set<String> usedInInstance = new HashSet<>();

    private Quiver() {}

    // repo ArrowTypes: internal name -> name chat uses
    private static Map<String, String> arrows() {
        if (arrows != null) return arrows;
        Map<String, String> out = new LinkedHashMap<>();
        JsonObject o = Repo.constant("ArrowTypes.json");
        if (o != null && o.has("arrows")) for (var e : o.getAsJsonObject("arrows").entrySet()) out.put(e.getKey(), e.getValue().getAsJsonObject().get("arrow").getAsString());
        return arrows = out;
    }

    static String idOf(String name) {
        for (var e : arrows().entrySet()) if (e.getValue().equals(name)) return e.getKey();
        return null;
    }

    private static CombatData data() {
        return CombatData.get();
    }

    private static int amount(String id) {
        return data().arrows.getOrDefault(id, 0);
    }

    // skyhanni ScoreboardElementQuiver: "Flint Arrow: 1,234", empty when no arrow known
    public static String boardName() {
        String id = data().currentArrow;
        if (id == null || id.isEmpty() || NONE.equals(id)) return "";
        return arrows().getOrDefault(id, id);
    }

    public static int boardAmount() {
        String id = data().currentArrow;
        return id == null ? 0 : amount(id);
    }

    public static boolean boardEndless() {
        return skeletonMaster;
    }

    public static int boardMax() {
        return MAX;
    }

    private static void changed() {
        CombatData.changed();
        String id = data().currentArrow;
        if (id.isEmpty() || NONE.equals(id)) return;
        int have = amount(id);
        if (inInstance()) usedInInstance.add(id);
        int low = Combat.integer(WARNING, "low_amount", 100);
        if (have > low || !Features.on(WARNING) || !Combat.opt(WARNING, "low")) return;
        long now = System.currentTimeMillis();
        if (now - lastLow < 30_000) return;
        lastLow = now;
        Combat.title("§cLow on arrows!", 40);
        Combat.say(String.format(Locale.ROOT, "Low on arrows §e(%,d left)", have));
    }

    private static boolean inInstance() {
        return "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island);
    }

    static void feed(String message, CombatData d) {
        Matcher m = SELECT.matcher(message);
        if (m.matches()) {
            String id = idOf(m.group("arrow"));
            if (id != null) d.currentArrow = id;
            return;
        }
        m = RAN_OUT.matcher(message);
        if (m.matches()) {
            String id = idOf(m.group("type"));
            if (id != null) d.arrows.put(id, 0);
        }
        m = JAX.matcher(message);
        if (m.matches()) added(m, d);
        m = FILL.matcher(message);
        if (m.matches()) {
            d.arrows.merge(FLINT, Integer.parseInt(m.group("flintAmount").replace(",", "")), Integer::sum);
            return;
        }
        m = ADDED.matcher(message);
        if (m.matches()) added(m, d);
        if (CLEARED.matcher(message).matches()) {
            d.arrows.clear();
            return;
        }
        if (RESET.matcher(message).matches()) {
            d.currentArrow = NONE;
            d.arrows.remove(NONE);
        }
    }

    private static void added(Matcher m, CombatData d) {
        String id = idOf(m.group("type"));
        if (id != null) d.arrows.merge(id, Integer.parseInt(m.group("amount").replace(",", "")), Integer::sum);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!Combat.sb()) return ChatBus.PASS;
        String message = LegacyText.of(line.component()).trim().replace("§r", "");
        CombatData d = data();
        Map<String, Integer> before = new HashMap<>(d.arrows);
        String arrow = d.currentArrow;
        feed(message, d);
        if (!before.equals(d.arrows) || !arrow.equals(d.currentArrow)) changed();
        String plain = line.plain();
        // skyhanni DungeonCompleteEvent + KuudraCompleteEvent
        if (inInstance() && (Patterns.get("SH.dungeon.completecolorless").matcher(plain).matches() || plain.trim().equals("KUUDRA DOWN!"))) afterRun();
        return ChatBus.PASS;
    }

    // from skyhanni instanceAlert
    private static void afterRun() {
        Set<String> low = new HashSet<>();
        int limit = Combat.integer(WARNING, "low_amount", 100);
        for (String id : usedInInstance) if (amount(id) <= limit) low.add(id);
        usedInInstance.clear();
        if (low.isEmpty() || !Features.on(WARNING) || !Combat.opt(WARNING, "after_run")) return;
        StringBuilder names = new StringBuilder();
        for (String id : low) names.append(names.isEmpty() ? "" : ", ").append(arrows().getOrDefault(id, id));
        Combat.title("§cLow on arrows!", 40);
        Combat.say("Low on " + names + "!");
        Combat.pling(1f);
    }

    // skyhanni onOwnInventoryMenuUpdate for your quiver slot, onInventoryFullyOpened for the quiver menu
    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.player.tickCount % 20 != 0) return;
        boolean bow = false;
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) {
            if (st.isEmpty()) continue;
            if (st.getItem() instanceof BowItem && !fake(st)) bow = true;
            readSlot(st);
        }
        hasBow = bow;
        skeletonMaster = SKELETON_MASTER.equals(Gain.sbId(mc.player.getItemBySlot(EquipmentSlot.CHEST)));
    }

    private static boolean fake(ItemStack st) {
        String id = Gain.sbId(st);
        return id != null && FAKE_BOWS.matcher(id).matches();
    }

    private static void readSlot(ItemStack st) {
        List<String> lore = LegacyText.lore(st);
        for (String l : lore) {
            Matcher m = ACTIVE.matcher(GameState.strip(l).trim());
            if (m.matches()) {
                String id = idOf(m.group("type"));
                if (id != null) update(id, Integer.parseInt(m.group("amount").replace(",", "")));
                return;
            }
        }
        var tag = MenuView.Item.of(st).tag;
        if (!tag.contains("quiver_arrow")) return;
        for (String l : lore) {
            Matcher m = PREVIEW.matcher(GameState.strip(l).trim());
            if (!m.matches()) continue;
            String id = idOf(GameState.strip(st.getHoverName().getString()).trim());
            if (id == null) for (String other : lore) if (id == null) id = idOf(GameState.strip(other).trim());
            if (id != null) update(id, Integer.parseInt(m.group("amount").replace(",", "")));
            return;
        }
    }

    private static void update(String id, int amount) {
        CombatData d = data();
        if (id.equals(d.currentArrow) && amount(id) == amount) return;
        d.currentArrow = id;
        d.arrows.put(id, amount);
        changed();
    }

    // quiver menu = recount everything
    private static void menu(MenuView v) {
        CombatData d = data();
        Map<String, Integer> seen = new HashMap<>();
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (!it.empty() && arrows().containsKey(it.id)) seen.merge(it.id, it.count, Integer::sum);
        }
        if (!seen.isEmpty() && !seen.equals(d.arrows)) {
            d.arrows.clear();
            d.arrows.putAll(seen);
            changed();
        }
    }

    private static boolean holdingBow() {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        ItemStack st = p.getMainHandItem();
        return st.getItem() instanceof BowItem && !fake(st);
    }

    private static boolean shown() {
        CombatData d = data();
        if (d.currentArrow.isEmpty()) return false;
        return switch (Combat.choice(DISPLAY, "when", "Bow in hand")) {
            case "Always" -> true;
            case "Bow in inventory" -> hasBow;
            default -> holdingBow();
        };
    }

    private static String rarity(String id) {
        String name = ItemRepo.coloredName(id);
        return name != null && name.length() > 1 && name.charAt(0) == '§' ? name.substring(0, 2) : "§f";
    }

    private static RichCard.Content card(String id, String name, int amount, boolean hideAmount, boolean icon) {
        String shown = rarity(id) + name + (!hideAmount && amount != 1 && !NONE.equals(id) ? "s" : "");
        RichCard.Line row = hideAmount || NONE.equals(id) ? RichCard.Line.row(shown, "") : RichCard.Line.row(shown, String.format(Locale.ROOT, "%,d/%,d", amount, MAX)).meter(Math.min(1, amount / (double) MAX), amount <= Combat.integer(WARNING, "low_amount", 100) ? Modern.BAD : Modern.AQUA);
        if (icon) row.icon(LegacyItems.stack("minecraft:arrow"));
        return new RichCard.Content("", List.of(row.key("Arrows")));
    }

    private static final HudElement CARD = new RichCard(DISPLAY, "Quiver", "Combat", Zone.TOP_RIGHT, 41, DISPLAY) {
        @Override public boolean visible() {
            return shown();
        }

        @Override public String heading() {
            return "";
        }

        @Override protected Content live() {
            String id = data().currentArrow;
            return card(id, arrows().getOrDefault(id, id), amount(id), skeletonMaster, Combat.opt(DISPLAY, "icon"));
        }

        @Override protected Content sample() {
            return card(FLINT, "Flint Arrow", 1327, false, true);
        }

        @Override public List<Content> states() {
            return List.of(card("REDSTONE_TIPPED_ARROW", "Redstone-tipped Arrow", 64, false, true));
        }

        @Override protected List<Row> classicRows() {
            String id = data().currentArrow;
            return List.of(new Row(arrows().getOrDefault(id, id), String.format(Locale.ROOT, "%,d", amount(id)), Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Flint Arrow", "1,327", Theme.current().text()));
        }
    };

    private static void left() {
        usedInInstance.clear();
    }

    static void register() {
        Features.add(new Feature("quiver_reading", "Combat", "Quiver reading", "Keeps track of your arrows, for the card and the warning.", "").alwaysOn().when(Combat::sb).tick(Quiver::tick).left(Quiver::left));
        Features.add(new Feature(DISPLAY, "Combat", "Quiver display", "The arrow you are using and how many are left.", "SkyHanni: Quiver Display").when(Combat::sb)
                .option(Option.choice("quiver_display.when", "When to show", "", "Bow in hand", "Always", "Bow in inventory", "Bow in hand"))
                .option(Option.bool("quiver_display.icon", "Arrow icon", "", true)));
        Features.add(new Feature(WARNING, "Combat", "Low quiver warning", "When your arrows run low, and after a dungeon or Kuudra run for the arrows you used.", "SkyHanni: Low Quiver Alert").when(Combat::sb)
                .option(Option.integer("quiver_warning.low_amount", "Low at", "Arrows.", 100, 0, 2880))
                .option(Option.bool("quiver_warning.low", "Low quiver alert", "", true))
                .option(Option.bool("quiver_warning.after_run", "Reminder after a run", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 23, "quiver", null, Quiver::onChat);
        MenuMarks.add(DISPLAY, "Quiver", Quiver::menu);
        HudLayout.add(CARD);
    }
}
