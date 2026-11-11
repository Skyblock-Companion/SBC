// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

//? if >=26.3 {
import com.mojang.blaze3d.platform.InputConstants;
//?}
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemIcons;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Mayor;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.market.BazaarApi;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.mc.mixin.ContainerAccess;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
//? if <26.3 {
/*import org.lwjgl.glfw.GLFW;
*///?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the small ones, each one ports a skyhanni or skyblocker class named above its part
// calendar, stock of stonks, tias relays, melodys harp, personal compactor, fann, focus mode, page scrolling,
// shift clicks, quick craft guard etc
public final class MenuExtras {
    private MenuExtras() {}

    private static Pattern sh(String key) {
        return Patterns.get("SH." + key);
    }

    private static MenuView menu() {
        return MenuMarks.current();
    }

    // skyhanni InventoryUtils.clickSlot: a click the players own key press or wheel asked for
    private static void click(int slot, int button, ContainerInput input) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return;
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, button, input, mc.player);
    }

    // skyhanni MinisterInCalendar, TravelingZooPetInCalendar

    private static final String[] ORINGO_PETS = {"Lion", "Monkey", "Elephant", "Giraffe", "Blue Whale", "Tiger"};
    private static final int MINISTER_SLOT = 38;
    private static final Map<String, String> MAYOR_COLOR = Map.ofEntries(Map.entry("Aatrox", "§3"), Map.entry("Cole", "§e"), Map.entry("Diana", "§2"), Map.entry("Diaz", "§6"), Map.entry("Finnegan", "§c"), Map.entry("Foxy", "§d"),
            Map.entry("Marina", "§b"), Map.entry("Paul", "§c"), Map.entry("Scorpius", "§d"), Map.entry("Jerry", "§d"), Map.entry("Derpy", "§d"), Map.entry("Aura", "§5"));

    // minister perks, core/Mayor keeps them marked
    static List<String> ministerLore(String minister, List<Mayor.Perk> perks) {
        String color = MAYOR_COLOR.getOrDefault(minister, "§c");
        List<String> out = new ArrayList<>();
        out.add(color + "Minister " + minister);
        out.add("§8§m--------------------------");
        boolean any = false;
        for (Mayor.Perk p : perks) {
            if (!p.name().endsWith(" (Minister)")) continue;
            any = true;
            out.add(color + p.name().substring(0, p.name().length() - " (Minister)".length()));
            out.addAll(wrap(p.description(), 40));
        }
        if (!any) out.add("§eThis minister has no perks known yet.");
        out.add("§8§m--------------------------");
        out.add("");
        out.add("§7The Minister is who came in 2nd place");
        out.add("§7during the election. They have one");
        out.add("§7of their perks active.");
        return out;
    }

    private static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder("§7");
        for (String word : text.split(" ")) {
            if (line.length() - 2 + word.length() > width && line.length() > 2) {
                out.add(line.toString());
                line = new StringBuilder("§7");
            }
            line.append(line.length() > 2 ? " " : "").append(word);
        }
        if (line.length() > 2) out.add(line.toString());
        return out;
    }

    static void minister(MenuView v) {
        if (!MenuMarks.on("calendar_extras", "calendar_extras.minister") || Mayor.minister.isEmpty()) return;
        MenuView.Mark m = v.mark(MINISTER_SLOT);
        m.icon = ItemIcons.of(Mayor.minister.toUpperCase(Locale.ROOT) + "_MAYOR_MONSTER");
        m.tip = ministerLore(Mayor.minister, Mayor.perks);
    }

    // skyhanni getTravelZooNumber: whole half years so a time near the border still lands right
    static String zooPet(int year, int month) {
        int zooOfYear = month / 3 <= 1 ? 0 : 1;
        return "§7Pet available: §6" + ORINGO_PETS[(year * 2 + zooOfYear) % ORINGO_PETS.length];
    }

    private static final Pattern DURATION = Pattern.compile("(\\d+)([dhms])");

    static long duration(String text) {
        long ms = 0;
        Matcher m = DURATION.matcher(text);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += switch (m.group(2)) {
                case "d" -> n * 86_400_000L;
                case "h" -> n * 3_600_000L;
                case "m" -> n * 60_000L;
                default -> n * 1000L;
            };
        }
        return ms;
    }

    private static int seasonMonth(String season) {
        for (int i = 0; i < SbTime.MONTHS.length; i++) if (SbTime.MONTHS[i].equals(season)) return i;
        return 0;
    }

    private static void calendarTooltip(ItemStack stack, List<Component> lines) {
        if (!MenuMarks.on("calendar_extras", "calendar_extras.oringo")) return;
        MenuView v = menu();
        int slot = MenuMarks.hoveredSlot();
        if (v == null || slot < 0) return;
        MenuView.Item it = v.item(slot);
        Matcher date = sh("calendarapi.date").matcher(v.title);
        if (date.matches() && sh("calendarapi.day-header").matcher(it.clean).matches()) {
            for (String line : it.cleanLore) {
                Matcher e = sh("calendarapi.event-line").matcher(line);
                if (e.matches() && e.group("eventName") != null && e.group("eventName").trim().equals("Traveling Zoo")) {
                    lines.add(Component.empty());
                    lines.add(Component.literal(zooPet(Integer.parseInt(date.group("year")), seasonMonth(date.group("season")))));
                }
            }
        }
        if (sh("calendarapi.gui").matcher(v.title).matches() && it.clean.equals("Traveling Zoo")) {
            Matcher start = it.firstClean(sh("calendarapi.main.startsin"));
            if (start != null && it.firstClean(sh("calendarapi.main.duration")) != null) {
                SbTime.SbDate at = SbTime.sbDate(System.currentTimeMillis() + duration(start.group("time")));
                lines.add(Component.empty());
                lines.add(Component.literal(zooPet(at.year(), at.month())));
            }
        }
    }

    // from skyhanni StockOfStonkFeature

    private static void stonksTooltip(ItemStack stack, List<Component> lines) {
        MenuView v = menu();
        if (v == null || !sh("inventory.stockofstonks.inventory").matcher(v.title).matches()) return;
        if (!sh("inventory.stockofstonks.inventory").matcher(GameState.strip(stack.getHoverName().getString()).trim()).matches()) return;
        stonks(lines);
    }

    // skyhanni onToolTip: what you pay per stock at each rank, best deal is green
    static void stonks(List<Component> lines) {
        int reward = 0, best = -1;
        long bestRatio = Long.MAX_VALUE;
        Map<Integer, Long> ratios = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).getString();
            Matcher top = sh("inventory.stockofstonks.top.new").matcher(line);
            if (top.matches()) {
                reward = Integer.parseInt(top.group("amount"));
                continue;
            }
            Matcher bid = sh("inventory.stockofstonks.bid.new").matcher(line);
            if (!bid.matches()) continue;
            long cost = Math.max(2_000_000L, Long.parseLong(bid.group("amount").replace(",", "")));
            long ratio = cost / (reward == 0 ? 1 : reward);
            ratios.put(i, ratio);
            if (ratio < bestRatio) {
                best = i;
                bestRatio = ratio;
            }
        }
        for (Map.Entry<Integer, Long> e : ratios.entrySet()) {
            String color = e.getKey() == best ? "§a" : "§6";
            MutableComponent line = lines.get(e.getKey()).copy().append(Component.literal(" §7(paying " + color + String.format(Locale.ROOT, "%,d", e.getValue()) + " §7per)"));
            lines.set(e.getKey(), line);
        }
    }

    // skyhanni Relay, TiaRelayHelper, TiaRelayWaypoints

    private record Relay(String name, Vec3 at, String island, String chat) {}

    private static final List<Relay> RELAYS = List.of(
            new Relay("1st Relay", new Vec3(144.0, 108.0, 93.5), "HUB", "relay.chat.1st"),
            new Relay("2nd Relay", new Vec3(-246.0, 123.0, 56.0), "HUB", "relay.chat.2nd"),
            new Relay("3rd Relay", new Vec3(129.0, 232.0, 201.0), "DWARVEN_MINES", "relay.chat.3rd"),
            new Relay("4th Relay", new Vec3(-559.5, 164.0, -286.5), "THE_END", "relay.chat.4th"),
            new Relay("5th Relay", new Vec3(-375.0, 207.0, -798.5), "CRIMSON_ISLE", "relay.chat.5th"),
            new Relay("6th Relay", new Vec3(-68.5, 157.5, -878.5), "CRIMSON_ISLE", "relay.chat.6th"),
            new Relay("7th Relay", new Vec3(93.5, 85.0, 187.5), "HUB", "relay.chat.7th"),
            new Relay("8th Relay", new Vec3(0.5, 146.5, -75.5), "DUNGEON_HUB", "relay.chat.8th"),
            new Relay("9th Relay", new Vec3(-23.0, 86.5, -12.5), "HUB", "relay.chat.9th"));

    private static Relay nextRelay;
    private static int relayYeet;
    private static long relayClickAt;
    private static final Map<Integer, float[]> RELAY_BEEPBOOPS = new LinkedHashMap<>();
    private static final Map<Integer, String> RELAY_SOUND_NAMES = new HashMap<>();
    private static final Map<Integer, Integer> RELAY_RESULT = new HashMap<>();

    private static boolean inRelay() {
        MenuView v = menu();
        return v != null && v.title.contains("Network Relay");
    }

    private static boolean relaySound(SoundHooks.Sound s) {
        if (!Features.on("tia_relay")) return false;
        boolean mute = MenuMarks.on("tia_relay", "tia_relay.mute") && s.name().equals("entity.wolf.whine");
        if (!MenuMarks.on("tia_relay", "tia_relay.sound_helper") || !inRelay() || relayYeet == 0) return mute;
        var p = Minecraft.getInstance().player;
        if (p == null || p.distanceToSqr(s.x(), s.y(), s.z()) >= 4) return mute;
        if (System.currentTimeMillis() - relayClickAt > 60_000 || RELAY_BEEPBOOPS.containsKey(relayYeet)) return mute;
        RELAY_BEEPBOOPS.put(relayYeet, new float[]{s.pitch()});
        RELAY_SOUND_NAMES.put(relayYeet, s.name());
        relayYeet = 0;
        relayResult();
        MenuMarks.invalidate();
        return mute;
    }

    // skyhanni tryResult: the 4 sounds by pitch
    private static void relayResult() {
        if (RELAY_BEEPBOOPS.size() < 4) return;
        String name = RELAY_SOUND_NAMES.values().iterator().next();
        for (String other : RELAY_SOUND_NAMES.values()) {
            if (!other.equals(name)) {
                var p = Minecraft.getInstance().player;
                if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §cToo much background noise for the relay! Turn off the music (/togglemusic) and try again."));
                RELAY_BEEPBOOPS.clear();
                RELAY_SOUND_NAMES.clear();
                return;
            }
        }
        List<Map.Entry<Integer, float[]>> sorted = new ArrayList<>(RELAY_BEEPBOOPS.entrySet());
        sorted.sort((a, b) -> Float.compare(a.getValue()[0], b.getValue()[0]));
        RELAY_RESULT.clear();
        int i = 1;
        for (Map.Entry<Integer, float[]> e : sorted) RELAY_RESULT.put(e.getKey(), i++);
        RELAY_BEEPBOOPS.clear();
        RELAY_SOUND_NAMES.clear();
    }

    static void relayMenu(MenuView v) {
        if (!MenuMarks.on("tia_relay", "tia_relay.sound_helper")) return;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            Integer position = RELAY_RESULT.get(slot);
            if (position != null) {
                if (it.cleanHas("Done!")) {
                    RELAY_RESULT.clear();
                    return;
                }
                v.number(slot, "#" + position);
            } else if (!RELAY_BEEPBOOPS.containsKey(slot) && it.cleanHas("Hear!")) v.label(slot, "Hear!", 22, 4, 1f);
        }
    }

    private static ChatBus.Result relayChat(ChatBus.Line line) {
        if (!MenuMarks.on("tia_relay", "tia_relay.next_waypoint")) return ChatBus.PASS;
        String message = LegacyText.of(line.component());
        for (Relay r : RELAYS) {
            if (sh(r.chat()).matcher(message).matches()) {
                nextRelay = r;
                return ChatBus.PASS;
            }
        }
        if (message.equals("§aYou completed the maintenance on the relay!")) nextRelay = null;
        return ChatBus.PASS;
    }

    private static void relayWorld(WorldDraw d) {
        if (MenuMarks.on("tia_relay", "tia_relay.all_waypoints")) {
            for (Relay r : RELAYS) if (r.island().equals(GameState.island)) d.waypoint(r.at(), 0xFFFF55FF, "§d" + r.name(), true, true);
            return;
        }
        if (nextRelay != null && nextRelay.island().equals(GameState.island) && MenuMarks.on("tia_relay", "tia_relay.next_waypoint")) d.waypoint(nextRelay.at(), 0xFFFF55FF, "§d" + nextRelay.name(), true, true);
    }

    // from skyhanni HarpFeatures

    private static final char[] HARP_COLORS = {'d', 'e', 'a', '2', '5', '9', 'b'};
    private static long harpClick, harpOpen;
    private static int guiScaleBefore = -1;
    private static Screen harpScreen;

    private static boolean harpTitle(String title) {
        return sh("harp.inventory").matcher(title).matches();
    }

    private static boolean melodyTitle(String title) {
        return sh("harp.menu").matcher(title).matches();
    }

    private static String harpKeys() {
        String keys = Features.get("harp").option("harp.keys").text().toUpperCase(Locale.ROOT);
        return keys.length() >= 7 ? keys : "1234567";
    }

    //? if <26.3 {
    /*private static int glfwKey(char c) {
        if (c >= '0' && c <= '9') return GLFW.GLFW_KEY_0 + (c - '0');
        if (c >= 'A' && c <= 'Z') return GLFW.GLFW_KEY_A + (c - 'A');
        return -1;
    }

    *///?}
    static void harpMenu(MenuView v) {
        if (!MenuMarks.on("harp", "harp.numbers") || !harpTitle(v.title)) return;
        String keys = harpKeys();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (!it.model.endsWith("_terracotta") || it.name.length() < 2) continue;
            char color = it.name.charAt(1);
            for (int i = 0; i < HARP_COLORS.length; i++) if (HARP_COLORS[i] == color) v.number(slot, String.valueOf(keys.charAt(i)));
        }
    }

    // was this key press the harps
    private static boolean harpKey(int key) {
        MenuView v = menu();
        if (v == null || !harpTitle(v.title) || !MenuMarks.on("harp", "harp.keybinds")) return false;
        String keys = harpKeys();
        for (int i = 0; i < 7; i++) {
            //? if >=26.3 {
            if (io.muzy.sbc.mc.KeyHeld.keyOf(keys.charAt(i)) != key) continue;
            //?} else {
            /*if (glfwKey(keys.charAt(i)) != key) continue;
            *///?}
            long now = System.currentTimeMillis();
            if (now - harpClick >= 200) {
                click(37 + i, 2, ContainerInput.CLONE);
                harpClick = now;
            }
            return true;
        }
        return false;
    }

    private static void harpTick(Minecraft mc) {
        Screen s = mc.gui == null ? null : mc.gui.screen();
        boolean inside = false;
        if (s instanceof AbstractContainerScreen<?>) {
            String t = GameState.strip(s.getTitle().getString()).trim();
            inside = melodyTitle(t) || harpTitle(t);
            if (s != harpScreen && melodyTitle(t) && MenuMarks.on("harp", "harp.quick_restart")) harpOpen = System.currentTimeMillis();
        }
        harpScreen = s;
        // skyhanni setGuiScale / unSetGuiScale: biggest gui while the harp is open, then back
        if (inside && MenuMarks.on("harp", "harp.gui_scale") && guiScaleBefore < 0) {
            guiScaleBefore = mc.options.guiScale().get();
            mc.options.guiScale().set(0);
            mc.resizeGui();
        } else if (!inside && guiScaleBefore >= 0) {
            mc.options.guiScale().set(guiScaleBefore);
            guiScaleBefore = -1;
            mc.resizeGui();
        }
    }

    // skyhanni quick restart: close within 2 sec of opening starts the song again
    private static boolean harpClick(int slotId, int button) {
        MenuView v = menu();
        if (v == null || !melodyTitle(v.title) || slotId != 40 || !MenuMarks.on("harp", "harp.quick_restart") || System.currentTimeMillis() - harpOpen > 2000) return false;
        Pattern selected = sh("harp.song.selected");
        for (int i = 0; i < v.itemsLength(); i++) {
            if (v.items[i].first(selected) != null) {
                click(i, button, ContainerInput.PICKUP);
                return true;
            }
        }
        return false;
    }

    // from skyhanni PersonalCompactorOverlay

    private static boolean compactorShows() {
        Feature f = Features.get("personal_compactor");
        boolean held = Keys.modifier(f.option("personal_compactor.key").choice());
        return switch (f.option("personal_compactor.visibility").choice()) {
            case "Always" -> true;
            case "With the key" -> held;
            default -> !held;
        };
    }

    private static void compactorTooltip(ItemStack stack, List<Component> lines) {
        String id = Gain.sbId(stack);
        if (!sh("inventory.personalcompactor.internalname").matcher(id).matches() || !compactorShows()) return;
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) return;
        lines.add(Math.min(1, lines.size()), Component.literal("§7Status: " + (ItemMarks.active(data.copyTag()) ? "§aEnabled" : "§cDisabled")));
    }

    // from skyhanni FannCost

    private static String fannCoins = "", fannBits = "";
    private static int fannHash;

    private static double number(Matcher m, String group) {
        return Double.parseDouble(m.group(group).replace(",", ""));
    }

    // skyhanni generateNewTooltips over the training menus own numbers. returns {coins per xp line, xp per bit
    // line}, null if its free
    static String[] fann(List<String> beginLore, String mode, String type, List<String> dayLore) {
        if (type.equals("Free") || beginLore.isEmpty()) return null;
        double coins = 0, bits = 0;
        Double earned = null, daily = null;
        long durationMs = -1;
        for (String l : beginLore) {
            Matcher m = sh("fann.inventory.coin").matcher(l);
            if (m.matches() && coins == 0) coins = number(m, "coins");
            m = sh("fann.inventory.bits").matcher(l);
            if (m.matches() && bits == 0) bits = number(m, "bits");
            m = sh("fann.inventory.exp.total").matcher(l);
            if (m.matches() && earned == null) earned = number(m, "expEarned");
            m = sh("fann.inventory.exp.daily").matcher(l);
            if (m.matches() && daily == null) daily = number(m, "expDaily");
            m = sh("fann.inventory.training.duration.pattern").matcher(l);
            if (m.matches() && durationMs < 0) durationMs = duration(m.group("time"));
        }
        if (durationMs < 0) {
            for (String l : dayLore) {
                Matcher m = sh("fann.inventory.lore.day-user-input").matcher(l);
                if (m.matches()) {
                    durationMs = Long.parseLong(m.group("days").replace(",", "")) * 86_400_000L;
                    break;
                }
            }
        }
        Double gained = mode.equals("Amount of Days") ? earned : daily == null ? null : Double.valueOf(daily * Math.max(0, durationMs) / 86_400_000.0);
        if (gained == null || gained <= 0) return null;
        return new String[]{"§7 = §6" + String.format(Locale.ROOT, "%.2f", coins / gained) + " Coins§7/§bXP", "§7 = §b" + String.format(Locale.ROOT, "%.2f", gained / Math.max(1, bits)) + " XP§7/§bBit"};
    }

    private static void fannMenu(MenuView v) {
        List<String> begin = v.item(22).lore;
        if (begin.isEmpty() || begin.hashCode() == fannHash) return;
        fannHash = begin.hashCode();
        fannCoins = fannBits = "";
        Matcher mode = v.item(15).first(sh("fann.inventory.lore.training-mode")), type = v.item(33).first(sh("fann.inventory.lore.training-type"));
        if (mode == null || type == null) return;
        String[] lines = fann(begin, mode.group("selection"), type.group("type"), v.item(24).lore);
        if (lines == null) return;
        fannCoins = lines[0];
        fannBits = lines[1];
    }

    private static void fannTooltip(ItemStack stack, List<Component> lines) {
        MenuView v = menu();
        if (v == null || !sh("fann.inventory.training.slot.inventory").matcher(v.title).matches() || fannCoins.isEmpty()) return;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = LegacyText.of(lines.get(i));
            if (MenuMarks.on("fann_cost", "fann_cost.coins_xp") && sh("fann.inventory.coin").matcher(line).matches()) lines.add(i + 1, Component.literal(fannCoins));
            else if (MenuMarks.on("fann_cost", "fann_cost.xp_bits") && sh("fann.inventory.bits").matcher(line).matches()) lines.add(i + 1, Component.literal(fannBits));
        }
    }

    // from skyhanni FocusMode

    public static net.minecraft.client.KeyMapping focusKey;
    private static boolean focusActive;

    // last hook on a tooltip, only the name stays
    private static void focusTooltip(ItemStack stack, List<Component> lines) {
        if (lines.isEmpty() || !(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>)) return;
        Feature f = Features.get("focus_mode");
        if (f.option("focus_mode.hide_menu_items").bool()) {
            String id = Gain.sbId(stack);
            if (id.isEmpty() || id.equals("SKYBLOCK_MENU")) return;
            MenuView v = menu();
            int hover = MenuMarks.hoveredSlot();
            if (BazaarApi.inBazaarInventory && v != null && hover >= 0 && hover < v.top) return;
        }
        boolean always = f.option("focus_mode.always").bool();
        String keyName = focusKey == null || focusKey.isUnbound() ? "NONE" : focusKey.getTranslatedKeyMessage().getString();
        boolean hint = !f.option("focus_mode.no_hint").bool() && !always && !keyName.equals("NONE");
        if (!focusActive && !always) {
            if (hint) lines.add(Math.min(1, lines.size()), Component.literal("§7Press " + keyName + " to enable Focus Mode"));
            return;
        }
        List<Component> kept = new ArrayList<>();
        kept.add(lines.get(0));
        if (hint) {
            kept.add(Component.literal("§7Focus Mode is on"));
            kept.add(Component.literal("§7Press " + keyName + " to disable it"));
        }
        MenuView v = menu();
        if (v != null && v.title.startsWith("Auctions")) {
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).getString().contains("-----------------")) continue;
                for (int j = i; j < Math.min(lines.size(), i + 20); j++) kept.add(lines.get(j));
                break;
            }
        }
        lines.clear();
        lines.addAll(kept);
    }

    // from skyhanni PageScrolling

    private static long scrollCooldown;
    private static Screen scrollScreen;
    private static boolean scrollable;

    // skyhanni onTick with the wheel delta, returns if the wheel got used
    public static boolean scroll(Screen s, double delta) {
        if (!Features.on("page_scrolling") || delta == 0) return false;
        MenuView v = menu();
        if (v == null || v.title.isEmpty()) return false;
        if (s != scrollScreen) {
            scrollScreen = s;
            scrollable = true;
        }
        long now = System.currentTimeMillis();
        if (!scrollable && scrollCooldown > now) return false;
        Feature f = Features.get("page_scrolling");
        if (sh("inventory.pagescrolling.illegal.0").matcher(v.title).matches() || sh("inventory.pagescrolling.illegal.1").matcher(v.title).matches()) return false;
        if (s instanceof AbstractContainerScreen<?> c && ((ContainerAccess) c).sbc$hovered() != null && ((ContainerAccess) c).sbc$hovered().hasItem()) {
            if (!(f.option("page_scrolling.invert_bypass").bool() ^ Keys.modifier(f.option("page_scrolling.bypass_key").choice()))) return false;
        }
        boolean forward = (delta > 0) ^ f.option("page_scrolling.invert_scroll").bool();
        int slot = pageSlot(v, forward);
        if (slot < 0) return false;
        click(slot, 0, ContainerInput.PICKUP);
        scrollable = false;
        scrollCooldown = now + 1000;
        return true;
    }

    static int pageSlot(MenuView v, boolean forward) {
        String direction = forward ? "forward" : "backward";
        for (int slot = 0; slot < v.top; slot++) {
            String name = v.items[slot].name;
            if (name.isEmpty()) continue;
            for (int i = 0; i < 5; i++) if (sh("inventory.pagescrolling." + direction + "." + i).matcher(name).matches()) return slot;
        }
        return -1;
    }

    // skyhanni ShiftClickBrewing, ShiftClickEquipment, ShiftClickNpcSell

    private static boolean shiftClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        MenuView v = menu();
        if (v == null || slot == null || !Features.on("shift_clicks")) return false;
        boolean own = slot.container instanceof Inventory;
        boolean make = MenuMarks.on("shift_clicks", "shift_clicks.brewing") && slotId != 49 && v.title.startsWith("Brewing Stand")
                || MenuMarks.on("shift_clicks", "shift_clicks.equipment") && own && !slot.getItem().isEmpty() && sh("data.equipment.equipment-and-stats-menu").matcher(v.title).matches()
                || MenuMarks.on("shift_clicks", "shift_clicks.npc_sell") && own && NotClickable.npcMenu(v);
        if (!make) return false;
        // skyhanni makeShiftClick: a right click on a sack keeps doing what it does
        if (button == 1 && ReforgeHelper.categoryAndRarity(v.item(slotId))[0].equals("SACK")) return false;
        click(slotId, 0, ContainerInput.QUICK_MOVE);
        return true;
    }

    // from skyhanni QuickCraftFeatures

    private static Set<String> quickCraftable;

    private static Set<String> quickCraftable() {
        if (quickCraftable == null) {
            quickCraftable = new HashSet<>();
            String text = Repo.bundled("QuickCraftableItems.json");
            if (text != null) for (JsonElement e : com.google.gson.JsonParser.parseString(text).getAsJsonArray()) quickCraftable.add(e.getAsString());
        }
        return quickCraftable;
    }

    private static boolean quickCraftSlot(String title, int slot) {
        return title.equals("Craft Item") ? slot == 16 || slot == 25 || slot == 34 : title.equals("Quick Crafting") && slot >= 10 && slot <= 44;
    }

    static void quickCraft(MenuView v) {
        if (!MenuMarks.on("quick_craft_guard", "quick_craft_guard.on") || ReforgeHelper.modifierDown()) return;
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (!quickCraftSlot(v.title, slot) || it.empty() || it.clean.equals("Quick Crafting Slot")) continue;
            if (!quickCraftable().contains(it.clean)) v.mark(slot).front = 0xB4555555;
        }
    }

    // skyhanni FavoritePowerStone, SnakeGame, HuntrapMisclickPrevention, HighlightBonzoMasks

    private static long snakeClick, trapNotified;
    private static final int[] SNAKE = {24, 16, 26, 34};

    private static boolean snakeKey(int key) {
        MenuView v = menu();
        if (v == null || !Features.on("snake_keys") || !sh("abiphone.snake.name").matcher(v.title).matches()) return false;
        var o = Minecraft.getInstance().options;
        net.minecraft.client.KeyMapping[] keys = {o.keyLeft, o.keyUp, o.keyRight, o.keyDown};
        for (int i = 0; i < 4; i++) {
            //? if >=26.3 {
            if (InputConstants.getKey(keys[i].saveString()).getValue() != key) continue;
            //?} else {
            /*if (com.mojang.blaze3d.platform.InputConstants.getKey(keys[i].saveString()).getValue() != key) continue;
            *///?}
            long now = System.currentTimeMillis();
            if (now - snakeClick >= 100) {
                click(SNAKE[i], 2, ContainerInput.CLONE);
                snakeClick = now;
            }
            return true;
        }
        return false;
    }

    private static boolean trapClick(int slotId) {
        MenuView v = menu();
        if (v == null || !v.title.startsWith("Hunting Toolkit") || !v.item(slotId).cleanHas("Status: EMPTY")) return false;
        long now = System.currentTimeMillis();
        if (now - trapNotified >= 10_000) {
            trapNotified = now;
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §fKept you from clicking an empty trap."));
        }
        return true;
    }

    private static final Map<String, long[]> MASKS = new HashMap<>();

    // mask cooldowns as a hue from red to green
    public static void maskBack(net.minecraft.client.gui.GuiGraphicsExtractor g, Slot slot) {
        if (MASKS.isEmpty() || !Features.on("mask_cooldowns")) return;
        String id = Gain.sbId(slot.getItem());
        String type = id.endsWith("BONZO_MASK") ? "BONZO" : id.endsWith("SPIRIT_MASK") ? "SPIRIT" : null;
        if (type == null) return;
        long[] m = MASKS.get(type);
        if (m == null) return;
        long left = m[0] - System.currentTimeMillis();
        if (left <= 0) return;
        float t = left / (float) m[1];
        int color = java.awt.Color.HSBtoRGB((1 - t) * (120f / 360f), 1f, 1f);
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color | 0xFF000000);
    }

    private static ChatBus.Result maskChat(ChatBus.Line line) {
        String plain = GameState.strip(line.plain());
        if (sh("inventory.masks.timers.bonzo").matcher(plain).matches()) MASKS.put("BONZO", new long[]{System.currentTimeMillis() + 360_000, 360_000});
        else if (sh("inventory.masks.timers.spirit").matcher(plain).matches()) MASKS.put("SPIRIT", new long[]{System.currentTimeMillis() + 30_000, 30_000});
        return ChatBus.PASS;
    }

    // skyhanni ColorHexInLore, ItemStars

    private static void hexTooltip(ItemStack stack, List<Component> lines) {
        MenuView v = menu();
        boolean dyeMenu = v != null && v.title.startsWith("Dye");
        String category = "";
        if (!dyeMenu) {
            List<String> clean = new ArrayList<>();
            for (Component c : lines) clean.add(c.getString());
            for (int i = clean.size() - 1; i >= 0 && category.isEmpty(); i--) {
                Matcher m = sh("utils.item.lore.rarity.line.colorless").matcher(clean.get(i));
                if (m.matches()) category = m.group("itemCategory");
            }
            if (!category.equals("DYE") && !Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS").contains(category)) return;
        }
        Pattern hex = sh("color.item.hex.lore.code");
        for (Component line : lines) {
            for (Component sibling : line.getSiblings()) {
                Matcher m = hex.matcher(sibling.getString());
                if (!m.matches() || !(sibling instanceof MutableComponent mutable)) continue;
                mutable.withColor(Integer.parseInt(m.group("hex").substring(1), 16));
            }
        }
    }

    // skyhanni ItemStars.grabStarCount + the tooltip name: "name §cN✪"
    private static void starsTooltip(ItemStack stack, List<Component> lines) {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || stack.getCount() != 1 || lines.isEmpty()) return;
        CompoundTag tag = data.copyTag();
        Integer stars = tag.contains("dungeon_item_level") ? Integer.valueOf(tag.getIntOr("dungeon_item_level", 0)) : tag.contains("upgrade_level") ? Integer.valueOf(tag.getIntOr("upgrade_level", 0)) : null;
        String id = tag.getStringOr("id", "");
        Matcher kuudra = sh("data.kuudra.internalname.armor").matcher(id);
        if (kuudra.matches()) {
            int tier = switch (kuudra.group("tier")) {
                case "HOT" -> 2;
                case "BURNING" -> 3;
                case "FIERY" -> 4;
                case "INFERNAL" -> 5;
                default -> 1;
            };
            stars = (stars == null ? 0 : stars) + (tier - 1) * 10;
        }
        if (stars == null) return;
        Matcher m = sh("inventory.itemstars.stars").matcher(LegacyText.name(stack));
        if (m.find()) lines.set(0, Component.literal(m.group("name") + " §c" + stars + "✪"));
    }

    // skyblocker: hide empty tooltips, swing on abilities

    private static final Set<String> FILLER = Set.of("", "Locked Page", "Quick Crafting Slot", "Locked Backpack Slot 2", "Locked Backpack Slot 3", "Locked Backpack Slot 4", "Locked Backpack Slot 5", "Locked Backpack Slot 6", "Locked Backpack Slot 7", "Locked Backpack Slot 8", "Locked Backpack Slot 9", "Locked Backpack Slot 10", "Locked Backpack Slot 11", "Locked Backpack Slot 12", "Locked Backpack Slot 13", "Locked Backpack Slot 14", "Locked Backpack Slot 15", "Locked Backpack Slot 16", "Locked Backpack Slot 17", "Locked Backpack Slot 18");

    // called before a slot tooltip, true = it shows none of its own
    public static boolean noTooltip(Slot slot) {
        if (slot == null) return false;
        MenuView v = MenuMarks.current();
        if (v != null && slot.index >= 0 && slot.index < v.marks.length && v.marks[slot.index] != null && v.marks[slot.index].tip != null) return true;
        if (v != null && harpTitle(v.title) && !(slot.container instanceof Inventory) && MenuMarks.on("harp", "harp.hide_tooltip")) return true;
        return slot.hasItem() && Features.on("hide_empty_tooltips") && slot.getItem().getHoverName().getString().isBlank();
    }

    private static boolean fillerClick(Slot slot) {
        if (slot == null || !slot.hasItem() || !Features.on("hide_empty_tooltips")) return false;
        MenuView v = menu();
        // ultrasequencer and superpairs are played on what look like empty items
        if (v != null && (v.title.startsWith("Ultrasequencer") || v.title.startsWith("Superpairs"))) return false;
        ItemStack st = slot.getItem();
        if (!FILLER.contains(st.getHoverName().getString())) return false;
        for (String l : LegacyText.lore(st)) if (!GameState.strip(l).isBlank() && !FILLER.contains(GameState.strip(l))) return false;
        return true;
    }

    // skyblocker SwingAnimation.hasAbility: right click ability in the lore, swing only on our side
    private static boolean swing() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !Features.on("swing_abilities")) return false;
        for (String l : LegacyText.lore(mc.player.getMainHandItem())) {
            String plain = GameState.strip(l);
            if (plain.startsWith("Ability:") && plain.contains("RIGHT CLICK")) {
                //? if >=26.3 {
                mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                //?} else {
                /*mc.player.swing(InteractionHand.MAIN_HAND, false);
                *///?}
                break;
            }
        }
        return false;
    }

    // FavoritePowerStone

    private static Set<String> powers;

    private static boolean isPower(String name) {
        if (powers == null) {
            powers = new HashSet<>();
            JsonObject o = Repo.constant("MaxwellPowers.json");
            if (o != null && o.has("powers")) for (JsonElement e : o.getAsJsonArray("powers")) powers.add(e.getAsString());
        }
        return powers.contains(name);
    }

    static void favoritePowers(MenuView v) {
        if (!MenuMarks.on("favorite_power_stone", "favorite_power_stone.on")) return;
        for (int slot = 0; slot < v.top; slot++) if (io.muzy.sbc.core.Store.I.favoritePowers.contains(v.items[slot].clean)) v.back(slot, MenuView.AQUA);
    }

    // shift click marks a power instead of picking it
    private static boolean favoriteClick(MenuView v, int slotId) {
        if (!sh("data.maxwell.gui.thaumaturgy").matcher(v.title).matches() || !MenuMarks.on("favorite_power_stone", "favorite_power_stone.on") || !Keys.modifier("Shift")) return false;
        String name = v.item(slotId).clean;
        if (!isPower(name)) return false;
        var favorites = io.muzy.sbc.core.Store.I.favoritePowers;
        if (!favorites.remove(name)) favorites.add(name);
        io.muzy.sbc.core.Store.markDirty();
        MenuMarks.invalidate();
        return true;
    }

    // from skyhanni OldSkyblockMenu

    // one shortcut: slot, name, command, needs a cookie or not, icon, lines
    private record OldButton(int slot, String name, String command, boolean cookie, String icon, String skull, String... lines) {}

    private static final List<OldButton> OLD_MENU = List.of(
            new OldButton(40, "Trades", "trades", false, "emerald", "", "View your available trades.", "These trades are always", "available and accessible through", "the SkyBlock Menu."),
            new OldButton(53, "Accessory Bag", "accessories", true, "player_head", "SB_MENU_ACCESSORY", "A special bag which can hold", "Talismans, Rings, Artifacts, Relics, and", "Orbs within it. All will still", "work while in this bag!"),
            new OldButton(52, "Potion Bag", "potionbag", true, "player_head", "SB_MENU_POTION", "A handy bag for holding your", "Potions in."),
            new OldButton(44, "Quiver", "quiver", true, "player_head", "SB_MENU_QUIVER", "A masterfully crafted Quiver", "which holds any kind of", "projectile you can think of!"),
            new OldButton(43, "Fishing Bag", "baitsack", true, "player_head", "SB_MENU_FISHING", "A useful bag which can hold all", "types of fish, baits, and fishing", "loot!"),
            new OldButton(35, "Sack of Sacks", "sax", false, "player_head", "SB_MENU_SACK_OF_SACKS", "A sack which contains other", "sacks. Sackception!"));

    private static OldButton oldButton(MenuView v, int slot) {
        if (!sh("utils.inventory.skyblockmenu").matcher(v.title).matches() || "THE_RIFT".equals(GameState.island) || !v.item(slot).model.endsWith("_stained_glass_pane")) return null;
        for (OldButton b : OLD_MENU) if (b.slot() == slot) return b;
        return null;
    }

    static void oldMenu(MenuView v) {
        for (OldButton b : OLD_MENU) {
            if (oldButton(v, b.slot()) == null) continue;
            MenuView.Mark m = v.mark(b.slot());
            m.icon = b.skull().isEmpty() ? io.muzy.sbc.core.LegacyItems.stack(b.icon()) : skull(b.skull());
            List<String> tip = new ArrayList<>();
            tip.add("§a" + b.name());
            for (String line : b.lines()) tip.add("§7" + line);
            tip.add("");
            if (b.cookie() && !GameState.hasCookie()) {
                tip.add("§cYou need a booster cookie active");
                tip.add("§cto use this shortcut!");
            } else tip.add("§eClick to execute /" + b.command());
            m.tip = tip;
        }
    }

    private static ItemStack skull(String id) {
        JsonObject skulls = Repo.constant("Skulls.json");
        String texture = skulls != null && skulls.has(id) ? skulls.get(id).getAsString() : "";
        return ItemIcons.of(id, "player_head", texture, 0, false);
    }

    private static boolean oldMenuClick(MenuView v, int slotId) {
        if (!Features.on("old_skyblock_menu")) return false;
        OldButton b = oldButton(v, slotId);
        if (b == null) return false;
        if (!b.cookie() || GameState.hasCookie()) MenuHighlights.command(b.command());
        else ReforgeHelper.beep();
        return true;
    }

    // all the keys and clicks in one place

    // from the screens key press. true = used here, the game shouldnt see it
    public static boolean key(int key, net.minecraft.client.input.KeyEvent event) {
        if (AttributeShards.trackHovered(event)) return true;
        if (focusKey != null && Features.on("focus_mode") && focusKey.matches(event) && !Features.get("focus_mode").option("focus_mode.always").bool()) {
            focusActive = !focusActive;
            return true;
        }
        if (SlotTexts.key != null && SlotTexts.key.matches(event) && Features.get("slot_text").option("slot_text.mode").choice().equals("Press to toggle")) {
            SlotTexts.pressed();
            return true;
        }
        return harpKey(key) || snakeKey(key);
    }

    private static boolean slotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        io.muzy.sbc.core.game.GameProfile.menuClick(screen, slot, slotId, button, net.minecraft.client.Minecraft.getInstance().hasShiftDown());
        MenuView v = menu();
        if (v != null) {
            if (v.title.contains("Network Relay") && button == 1 && MenuMarks.on("tia_relay", "tia_relay.sound_helper") && Features.on("tia_relay")) {
                relayYeet = slotId;
                relayClickAt = System.currentTimeMillis();
            }
            if (quickCraftSlot(v.title, slotId) && Features.on("quick_craft_guard") && MenuMarks.on("quick_craft_guard", "quick_craft_guard.on") && !ReforgeHelper.modifierDown()) {
                MenuView.Item it = v.item(slotId);
                if (!it.empty() && !quickCraftable().contains(it.clean)) return true;
            }
            if (Features.on("harp") && harpClick(slotId, button)) return true;
            if (Features.on("trap_guard") && trapClick(slotId)) return true;
            if (Features.on("favorite_power_stone") && favoriteClick(v, slotId)) return true;
            if (oldMenuClick(v, slotId)) return true;
        }
        return fillerClick(slot) || shiftClick(screen, slot, slotId, button);
    }

    static void register(BooleanSupplier skyblock) {
        Features.add(new Feature("calendar_extras", "Menus", "Calendar extras", "The Minister and their perk in the main calendar, and which pet Oringo brings to a Traveling Zoo.", "SkyHanni: Minister in Calendar, Oringo Pet in Calendar")
                .when(skyblock)
                .tooltip(MenuExtras::calendarTooltip)
                .option(Option.bool("calendar_extras.minister", "Minister", "", true))
                .option(Option.bool("calendar_extras.oringo", "Oringo's pet", "", true)));
        MenuMarks.add("calendar_extras", sh("calendarapi.gui"), MenuExtras::minister);

        Features.add(new Feature("stonks_price", "Events", "Stock of Stonks price", "In the Stonks Auction, what you pay per Stock at each rank; the best one is green.", "SkyHanni: Stonk of Stonk Price").when(skyblock).tooltip(MenuExtras::stonksTooltip));

        Feature tia = new Feature("tia_relay", "Menus", "Tia's relays", "For Tia's Abiphone Network: which relay is next as a waypoint, and the order of the four sounds in the puzzle.", "SkyHanni: Tia Relay Abiphone Network Maintenance")
                .when(skyblock)
                .world(MenuExtras::relayWorld)
                .tick(mc -> {
                    if (!RELAY_BEEPBOOPS.isEmpty() || !RELAY_RESULT.isEmpty()) {
                        if (!inRelay()) {
                            RELAY_BEEPBOOPS.clear();
                            RELAY_SOUND_NAMES.clear();
                            RELAY_RESULT.clear();
                        }
                    }
                })
                .option(Option.bool("tia_relay.next_waypoint", "Next relay", "", true))
                .option(Option.bool("tia_relay.all_waypoints", "All relays", "", false))
                .option(Option.bool("tia_relay.sound_helper", "Sound puzzle", "Right click each and it numbers them from low to high.", true))
                .option(Option.bool("tia_relay.mute", "Mute the whine", "", true));
        Features.add(tia);
        ChatBus.register(ChatBus.OBSERVE, "tia_relay", tia, MenuExtras::relayChat);
        SoundHooks.listen(MenuExtras::relaySound);
        MenuMarks.add("tia_relay", t -> t.contains("Network Relay"), MenuExtras::relayMenu);

        Features.add(new Feature("harp", "Menus", "Melody's Harp", "Melody's Harp, with your own keys instead of clicks.", "SkyHanni: Melody's Hair Harp")
                .alwaysOn()
                .tick(MenuExtras::harpTick)
                .option(Option.bool("harp.keybinds", "Play with keys", "A key press clicks the note's row.", false))
                .option(Option.text("harp.keys", "Keys", "Seven keys in a row, like 1234567 or QWERTYU.", "1234567"))
                .option(Option.bool("harp.numbers", "Show the keys on the notes", "", false))
                .option(Option.bool("harp.quick_restart", "Quick restart", "Close within two seconds of opening starts the song again.", false))
                .option(Option.bool("harp.gui_scale", "Biggest GUI while playing", "", false))
                .option(Option.bool("harp.hide_tooltip", "Hide tooltips", "", false)));
        MenuMarks.add("harp", t -> harpTitle(t), MenuExtras::harpMenu);

        Features.add(new Feature("personal_compactor", "Items", "Personal Compactor status", "Whether a Personal Compactor or Deletor is on, as a mark on the item and a line on its tooltip.", "SkyHanni: Personal Compactor Overlay")
                .when(skyblock)
                .tooltip(MenuExtras::compactorTooltip)
                .option(Option.bool("personal_compactor.show_toggle", "Mark on the item", "", true))
                .option(Option.choice("personal_compactor.visibility", "Tooltip line", "", "Without the key", "Always", "With the key", "Without the key"))
                .option(Option.choice("personal_compactor.key", "Key", "", "Shift", "Shift", "Ctrl", "Alt")));

        Features.add(new Feature("fann_cost", "Menus", "Fann's training cost", "In a Fann training slot, what each XP costs in coins and what each Bit gives.", "SkyHanni: Fann Cost Per XP/Bits")
                .when(skyblock).alwaysOn()
                .tooltip(MenuExtras::fannTooltip)
                .option(Option.bool("fann_cost.coins_xp", "Coins per XP", "", false))
                .option(Option.bool("fann_cost.xp_bits", "XP per Bit", "", false)));
        MenuMarks.add("fann_cost", sh("fann.inventory.training.slot.inventory"), MenuExtras::fannMenu);

        Features.add(new Feature("page_scrolling", "Menus", "Pages with the wheel", "The mouse wheel turns the page in Hypixel's menus; over an item it needs the key.", "SkyHanni: Page Scrolling")
                .when(skyblock).defaultOff()
                .option(Option.choice("page_scrolling.bypass_key", "Key over an item", "", "Shift", "Shift", "Ctrl", "Alt"))
                .option(Option.bool("page_scrolling.invert_bypass", "Over an item, only without the key", "", false))
                .option(Option.bool("page_scrolling.invert_scroll", "Invert the wheel", "", false)));

        Features.add(new Feature("shift_clicks", "Menus", "Clicks count as Shift clicks", "In the menus you choose, a plain click moves the item as a Shift click would.", "SkyHanni: Shift Click Brewing, Shift Click Equipment, Shift Click NPC sell")
                .when(skyblock).alwaysOn()
                .option(Option.bool("shift_clicks.brewing", "Brewing stand", "", false))
                .option(Option.bool("shift_clicks.equipment", "Equipment menu", "", false))
                .option(Option.bool("shift_clicks.npc_sell", "NPC shops", "", false)));

        Features.add(new Feature("quick_craft_guard", "Menus", "Quick craft guard", "Keeps you from quick crafting something by accident: dim, and Ctrl and click does it.", "SkyHanni: Quick Craft Confirmation")
                .when(skyblock).alwaysOn()
                .option(Option.bool("quick_craft_guard.on", "Ask for Ctrl", "", false)));
        MenuMarks.add("quick_craft_guard", t -> t.equals("Craft Item") || t.equals("Quick Crafting"), MenuExtras::quickCraft);

        Features.add(new Feature("favorite_power_stone", "Menus", "Favourite power stones", "In Thaumaturgy, Shift and click marks a power as a favourite, in light blue.", "SkyHanni: Favorite Power Stone")
                .when(skyblock).alwaysOn()
                .option(Option.bool("favorite_power_stone.on", "Favourites", "", false)));
        MenuMarks.add("favorite_power_stone", sh("data.maxwell.gui.thaumaturgy"), MenuExtras::favoritePowers);
        Features.add(new Feature("old_skyblock_menu", "Menus", "Old SkyBlock Menu shortcuts", "The empty corners of the SkyBlock Menu get the old shortcuts: Trades, Accessory Bag, Potion Bag, Quiver, Fishing Bag, Sack of Sacks.", "SkyHanni: Old SkyBlock Menu")
                .when(skyblock).defaultOff());
        MenuMarks.add("old_skyblock_menu", sh("utils.inventory.skyblockmenu"), MenuExtras::oldMenu);
        Features.add(new Feature("snake_keys", "Menus", "Snake with keys", "Your movement keys steer the Abiphone's Snake.", "SkyHanni: Snake Game Keybinds").when(skyblock).defaultOff());
        Features.add(new Feature("trap_guard", "Hunting", "Empty trap guard", "Stops a click on an empty trap in the Hunting Toolkit.", "SkyHanni: Huntrap Misclick Prevention").defaultOff().when(skyblock));
        Feature masks = new Feature("mask_cooldowns", "Items", "Mask cooldowns", "A Bonzo or Spirit Mask that just saved you goes from red to green behind its slot.", "SkyHanni: Depleted Bonzo's Masks").when(skyblock);
        Features.add(masks);
        ChatBus.register(ChatBus.OBSERVE, "mask_cooldowns", masks, MenuExtras::maskChat);

        Features.add(new Feature("hex_colors", "Items", "Hex colours in their colour", "Hex codes on dyes and armor are written in the colour they stand for.", "SkyHanni: Show hex as actual color").when(skyblock).tooltip(MenuExtras::hexTooltip));
        Features.add(new Feature("item_stars", "Items", "Stars as a number", "On a tooltip the stars of an item as one number, with Crimson tiers counted.", "SkyHanni: Item Stars").when(skyblock).defaultOff().tooltip(MenuExtras::starsTooltip));
        Features.add(new Feature("hide_empty_tooltips", "Menus", "No tooltips on empty items", "The empty panes in Hypixel's menus show no tooltip and do not take clicks.", "Skyblocker: Hide empty item tooltips in menus").when(skyblock));
        Features.add(new Feature("swing_abilities", "Items", "Swing on abilities", "Your hand swings when you use a right-click ability, on your screen only.", "Skyblocker: Swing On Abilities").when(skyblock).defaultOff());
        Features.add(new Feature("middle_click_fix", "Menus", "Middle click in menus", "Middle click works on Hypixel's menus as it does in creative mode.", "SkyHanni: Middle Click Fix").when(skyblock));
        Features.add(new Feature("cancel_item_update", "Items", "No item bob on updates", "An item whose data changes, like a drill's fuel, no longer plays the equip animation.", "Skyblocker: Cancel Component Update Animation").when(skyblock));
        io.muzy.sbc.render.HeldItem.register();
        Features.add(new Feature("focus_mode", "Items", "Focus Mode", "Item tooltips show only the name; the Focus Mode key switches it in a menu.", "SkyHanni: Focus Mode")
                .when(skyblock).defaultOff()
                .option(Option.bool("focus_mode.always", "Always on", "No key needed.", false))
                .option(Option.bool("focus_mode.no_hint", "No hint about the key", "", false))
                .option(Option.bool("focus_mode.hide_menu_items", "Keep tooltips of menu items", "Buttons and the Bazaar's items keep their text.", true))
                .tooltip(MenuExtras::focusTooltip));
        Interactions.onSlotClick(MenuExtras::slotClick);
        Interactions.onItemClick(right -> right && swing());
    }
}
