// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.misc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.areas.FishHooks;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni misc/EnchantedClockHelper.kt: the enchanted time clock boosts from its menu + the TIME WARP
// line, and a chat line + sound when one is ready again
public final class EnchantedClock {
    public static final String ID = "enchanted_clock";
    private static final Patterns.P STATUS = Patterns.p("SH.misc.eclock.boost.status"), COOLDOWN = Patterns.p("SH.misc.eclock.boost.cooldown"), USED = Patterns.p("SH.misc.eclock.chat.boostused");

    public static final class Status {
        public String state;
        public long availableAt;
        public boolean exactTime, warned;
    }

    record Boost(String name, String display, String usage, String colour, int statusSlot, long cooldownMs) {}

    private static List<Boost> boosts;
    private static long lastCheck;
    // after a reminder: when to remind again, per boost
    private static final Map<String, Long> REPEAT = new HashMap<>();

    private EnchantedClock() {}

    // skyhanni LorenzColor names -> the chat code the card lines use
    private static String code(String colour) {
        return switch (colour) {
            case "AQUA" -> "§b";
            case "GOLD" -> "§6";
            case "LIGHT_PURPLE" -> "§d";
            case "YELLOW" -> "§e";
            case "GREEN" -> "§a";
            case "RED" -> "§c";
            case "BLUE" -> "§9";
            default -> "§f";
        };
    }

    static List<Boost> parse(JsonObject json) {
        List<Boost> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("boosts")) {
            JsonObject o = e.getAsJsonObject();
            String display = o.get("display_name").getAsString();
            long hours = o.has("cooldown_hours") ? o.get("cooldown_hours").getAsLong() : 48;
            out.add(new Boost(o.get("name").getAsString(), display, o.has("usage_string") ? o.get("usage_string").getAsString() : display, o.get("color").getAsString(), o.get("status_slot").getAsInt(), (hours > 0 ? hours : 48) * 3_600_000L));
        }
        return out;
    }

    private static List<Boost> boosts() {
        if (boosts == null) {
            JsonObject json = Repo.constant("misc/EnchantedClock.json");
            boosts = json == null ? List.of() : parse(json);
        }
        return boosts;
    }

    private static Map<String, Status> storage() {
        if (GameState.profileName.isEmpty()) return null;
        return MiscStore.get().clock.computeIfAbsent(GameState.profileName, k -> new HashMap<>());
    }

    private static boolean wanted(String boost) {
        Option o = Features.get(ID).option("enchanted_clock." + boost.toLowerCase(Locale.ROOT));
        return o == null || o.bool();
    }

    // from skyhanni loadBoostsReadyNow: what came off cooldown and wasnt said yet
    static List<String> ready(Map<String, Status> storage, long now, java.util.function.Predicate<String> wanted) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Status> e : storage.entrySet()) {
            Status s = e.getValue();
            if (s.warned || !wanted.test(e.getKey()) || !"CHARGING".equals(s.state) || s.availableAt == 0 || s.availableAt > now) continue;
            s.state = "READY";
            s.availableAt = 0;
            s.warned = true;
            out.add(e.getKey());
        }
        return out;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return;
        lastCheck = now;
        Map<String, Status> storage = storage();
        if (storage == null) return;
        Feature f = Features.get(ID);
        int repeat = f.option("enchanted_clock.repeatReminder").integer();
        REPEAT.entrySet().removeIf(e -> {
            if (e.getValue() > now) return false;
            Status s = storage.get(e.getKey());
            // still not used, say it again
            if (s != null && "READY".equals(s.state)) {
                s.state = "CHARGING";
                s.availableAt = now;
                s.warned = false;
            }
            return true;
        });
        List<String> ready = ready(storage, now, EnchantedClock::wanted);
        if (ready.isEmpty()) return;
        List<String> names = new ArrayList<>();
        for (Boost b : boosts()) {
            if (!ready.contains(b.name())) continue;
            names.add(code(b.colour()) + b.display());
            if (repeat > 0) REPEAT.put(b.name(), now + repeat * 60_000L);
        }
        if (names.isEmpty()) return;
        MiscStore.save();
        SmallMisc.say("§6§lTIME WARP! §r§aThe following " + (names.size() == 1 ? "boost is ready" : "boosts are ready") + ": " + String.join("§7, ", names));
        FishHooks.pling();
    }

    // from skyhanni onChat: the boost you just used starts its cooldown, to the minute
    private static ChatBus.Result chat(ChatBus.Line line) {
        Matcher m = USED.matcher(LegacyText.of(line.component()));
        if (!m.matches()) return ChatBus.PASS;
        String usage = m.group("usagestring");
        Map<String, Status> storage = storage();
        if (storage == null) return ChatBus.PASS;
        for (Boost b : boosts()) {
            if (!b.usage().equals(usage)) continue;
            Status s = new Status();
            s.state = "CHARGING";
            s.availableAt = System.currentTimeMillis() + b.cooldownMs();
            s.exactTime = true;
            storage.put(b.name(), s);
            REPEAT.remove(b.name());
            MiscStore.save();
        }
        return ChatBus.PASS;
    }

    // one status item: READY, PROBLEM, or how long left. pure, for the test
    static Status read(MenuView.Item item, Status old, long now) {
        String state = null;
        for (String l : item.lore) {
            Matcher m = STATUS.matcher(l);
            if (m.matches()) state = m.group("status");
        }
        // an exact time stays. READY + PROBLEM only say its time, and the next secs check turns a charging boost into
        // a reminder
        if (state == null || (old != null && old.exactTime)) return old;
        if (!state.equals("CHARGING")) {
            if (old != null) old.availableAt = now;
            return old;
        }
        Status s = new Status();
        s.state = state;
        for (String l : item.lore) {
            Matcher m = COOLDOWN.matcher(l);
            if (!m.find()) continue;
            int count = Integer.parseInt(m.group("count").replace(",", ""));
            String type = m.group("type");
            s.exactTime = type.equals("minute");
            s.availableAt = now + switch (type) {
                case "hour" -> count * 3_600_000L;
                case "minute" -> count * 60_000L;
                default -> 0L;
            };
            break;
        }
        return s;
    }

    private static void menu(MenuView v) {
        Map<String, Status> storage = storage();
        if (storage == null) return;
        long now = System.currentTimeMillis();
        for (Boost b : boosts()) {
            MenuView.Item item = v.item(b.statusSlot());
            if (item.empty() || !b.display().equals(item.clean.trim())) continue;
            Status s = read(item, storage.get(b.name()), now);
            if (s != null) storage.put(b.name(), s);
        }
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Misc", "Enchanted Clock reminder", "A chat line and a sound when a boost of your Enchanted Time Clock is ready again. Open the clock once so it knows the times.", "SkyHanni: Enchanted Clock Reminder")
                .when(() -> GameState.inSkyBlock).tick(EnchantedClock::tick)
                .option(Option.integer("enchanted_clock.repeatReminder", "Repeat every (minutes)", "Until you use it. 0: once.", 0, 0, 120))
                .option(Option.bool("enchanted_clock.minions", "Minions", "", true))
                .option(Option.bool("enchanted_clock.chocolate_factory", "Chocolate Factory", "", true))
                .option(Option.bool("enchanted_clock.pet_training", "Pet Training", "", true))
                .option(Option.bool("enchanted_clock.pet_sitter", "Pet Sitter", "", true))
                .option(Option.bool("enchanted_clock.aging_items", "Aging Items", "", true))
                .option(Option.bool("enchanted_clock.forge", "Forge", "", true)));
        ChatBus.register(ChatBus.OBSERVE + 21, ID, f, EnchantedClock::chat);
        MenuMarks.add(ID, "Enchanted Time Clock", EnchantedClock::menu);
    }
}
