// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.misc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.combat.SlayerApi;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni misc/CarryTracker + OtherPlayersSlayerApi: the slayer carries you do on a card (who, what,
// how many done, whats paid). /sbc carry adds them. a boss counts when its owner is someone you carry
public final class CarryTracker {
    public static final String ID = "carry_tracker";
    private static final Patterns.P TRADE = Patterns.p("SH.carry.trade.completed"), COINS = Patterns.p("SH.carry.trade.coins.gained"), SLAYER = Patterns.p("SH.mob.detection.filter.slayer");
    // from skyhanni MobFilter pattern.summon.owner
    private static final Pattern SPAWNED_BY = Pattern.compile(".*Spawned by: (?<name>\\w+).*");

    static final class Carry {
        final String type;
        final int tier;
        int requested, done;

        Carry(String type, int tier, int requested) {
            this.type = type;
            this.tier = tier;
            this.requested = requested;
        }

        String label() {
            return type + " " + tier;
        }
    }

    static final class Customer {
        final String name;
        double paid;
        final List<Carry> carries = new ArrayList<>();

        Customer(String name) {
            this.name = name;
        }
    }

    private record Boss(ArmorStand tag, LivingEntity body, String type, int tier, String owner) {}

    static final List<Customer> CUSTOMERS = new ArrayList<>();
    // "Revenant 4" -> price of one
    static final Map<String, Double> PRICES = new HashMap<>();
    private static final Map<Integer, Boss> BOSSES = new HashMap<>();
    private static String lastTraded;
    private CarryTracker() {}

    private static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] " + text));
    }

    static Customer customer(String name) {
        for (Customer c : CUSTOMERS) if (c.name.equalsIgnoreCase(name)) return c;
        return null;
    }

    // from skyhanni createCarryType: rev5, sven4, eman3 -> type name + tier, null = unreadable
    static Object[] readType(String input) {
        if (input.length() < 2 || !Character.isDigit(input.charAt(input.length() - 1))) return null;
        String raw = input.substring(0, input.length() - 1).toLowerCase(Locale.ROOT);
        JsonObject data = Repo.constant("CarryTracker.json");
        if (data == null || !data.has("slayer_names")) return null;
        for (Map.Entry<String, JsonElement> e : data.getAsJsonObject("slayer_names").entrySet()) {
            for (JsonElement n : e.getValue().getAsJsonArray()) {
                if (!n.getAsString().equals(raw)) continue;
                String kind = e.getKey().charAt(0) + e.getKey().substring(1).toLowerCase(Locale.ROOT);
                return new Object[]{kind, input.charAt(input.length() - 1) - '0'};
            }
        }
        return null;
    }

    // from skyhanni addCarry
    static String add(String who, String rawType, int amount) {
        Object[] t = readType(rawType);
        if (t == null) return "§cUnknown carry type: " + rawType + "! Use e.g. rev5, sven4, eman3, blaze2.";
        Customer c = customer(who);
        for (Carry carry : c == null ? List.<Carry>of() : c.carries) {
            if (!carry.type.equals(t[0]) || carry.tier != (Integer) t[1]) continue;
            if (carry.requested + amount < 1) return "§cNew carry amount requested must be positive!";
            carry.requested += amount;
            return "§fUpdated carry: §b" + who + " §8x" + carry.requested + " §d" + carry.label();
        }
        if (amount < 1) return "§cCarry amount requested must be positive!";
        if (c == null) {
            c = new Customer(who);
            CUSTOMERS.add(c);
        }
        Carry carry = new Carry((String) t[0], (Integer) t[1], amount);
        c.carries.add(carry);
        return "§fStarted carry: §b" + who + " §8x" + amount + " §d" + carry.label();
    }

    // from skyhanni setPrice
    static String price(String rawType, double price) {
        Object[] t = readType(rawType);
        if (t == null) return "§cUnknown carry type: " + rawType + "! Use e.g. rev5, sven4, eman3, blaze2.";
        PRICES.put(t[0] + " " + t[1], price);
        return "§fSet the carry price for §d" + t[0] + " " + t[1] + " §fto §6" + Prices.coins(price) + " coins.";
    }

    // from skyhanni SlayerDeathEvent: a boss of someone you carry died
    static boolean bossDied(String owner, String type, int tier) {
        Customer c = customer(owner);
        if (c == null) return false;
        for (Carry carry : c.carries) {
            if (!type.contains(carry.type) && !carry.type.contains(type) || carry.tier != tier) continue;
            carry.done++;
            if (carry.done == carry.requested) {
                say("§fCarry done for §b" + c.name + "§f!");
                var mc = Minecraft.getInstance();
                mc.gui.hud.setTitle(Component.literal("§eCarry done!"));
            }
            return true;
        }
        return false;
    }

    private static double totalOf(Customer c) {
        double total = 0;
        for (Carry carry : c.carries) total += carry.requested * PRICES.getOrDefault(carry.label(), 0.0);
        return total;
    }

    private static int roman(String r) {
        return switch (r) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            default -> 0;
        };
    }

    // from skyhanni MobFilter slayer stand + OtherPlayersSlayerApi.onMobDespawn: counts when the body has no
    // health left, not just out of range
    private static void scan(Minecraft mc) {
        for (Iterator<Boss> it = BOSSES.values().iterator(); it.hasNext(); ) {
            Boss b = it.next();
            if (!b.tag().isRemoved()) continue;
            it.remove();
            if (b.body() != null && b.body().getHealth() <= 0) bossDied(b.owner(), b.type(), b.tier());
        }
        if (CUSTOMERS.isEmpty() || mc.player == null || mc.level == null) return;
        for (ArmorStand by : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(40), ArmorStand::hasCustomName)) {
            if (BOSSES.containsKey(by.getId())) continue;
            Matcher owner = SPAWNED_BY.matcher(GameState.strip(by.getCustomName().getString()));
            if (!owner.matches() || customer(owner.group("name")) == null) continue;
            AABB near = by.getBoundingBox().inflate(1, 3, 1);
            for (ArmorStand tag : mc.level.getEntitiesOfClass(ArmorStand.class, near, ArmorStand::hasCustomName)) {
                Matcher m = SLAYER.matcher(GameState.strip(tag.getCustomName().getString()));
                if (!m.matches() || m.group("tier") == null) continue;
                SlayerApi.Type type = SlayerApi.Type.byName(m.group("name"));
                if (type == null) continue;
                LivingEntity body = null;
                for (Entity e : mc.level.getEntities(by, near)) {
                    if (e instanceof LivingEntity l && !(e instanceof ArmorStand) && !(e instanceof Player)) {
                        body = l;
                        break;
                    }
                }
                BOSSES.put(by.getId(), new Boss(by, body, type.name().charAt(0) + type.name().substring(1).toLowerCase(Locale.ROOT), roman(m.group("tier")), owner.group("name")));
                break;
            }
        }
    }

    private static ChatBus.Result chat(ChatBus.Line line) {
        String coded = LegacyText.of(line.component());
        Matcher m = TRADE.matcher(coded);
        if (m.matches()) lastTraded = io.muzy.sbc.events.diana.RareMobWaypointShare.cleanPlayerName(GameState.strip(m.group("name")));
        m = COINS.matcher(coded);
        if (m.matches() && lastTraded != null) {
            Customer c = customer(lastTraded);
            if (c != null) c.paid += shortNumber(m.group("coins"));
        }
        return ChatBus.PASS;
    }

    // "500k", "1.5M"
    static double shortNumber(String s) {
        String t = s.replace(",", "").trim().toLowerCase(Locale.ROOT);
        double mul = t.endsWith("k") ? 1e3 : t.endsWith("m") ? 1e6 : t.endsWith("b") ? 1e9 : 1;
        if (mul > 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static RichCard.Content content(List<Customer> customers, Map<String, Double> prices) {
        List<RichCard.Line> lines = new ArrayList<>();
        for (Customer c : customers) {
            if (c.carries.isEmpty()) continue;
            double total = 0;
            for (Carry carry : c.carries) total += carry.requested * prices.getOrDefault(carry.label(), 0.0);
            lines.add(RichCard.Line.row("§b" + c.name, total > 0 ? "§6" + Prices.coins(c.paid) + "§8/§6" + Prices.coins(total) : "").raw());
            for (Carry carry : c.carries) {
                String colour = carry.done > carry.requested ? "§c" : carry.done == carry.requested ? "§a" : "§e";
                lines.add(RichCard.Line.row("§d" + carry.label(), colour + carry.done + "§8/" + colour + carry.requested).raw());
            }
        }
        return new RichCard.Content("", lines);
    }

    private static List<Customer> sampleCustomers() {
        Customer c = new Customer("Customer");
        c.paid = 500_000;
        Carry a = new Carry("Revenant", 5, 10);
        a.done = 3;
        Carry b = new Carry("Voidgloom", 4, 2);
        b.done = 2;
        c.carries.add(a);
        c.carries.add(b);
        return List.of(c);
    }

    private static final HudElement CARD = new RichCard(ID, "Carries", "Misc", Zone.TOP_LEFT, 56, ID) {
        @Override public boolean visible() {
            for (Customer c : CUSTOMERS) if (!c.carries.isEmpty()) return true;
            return false;
        }

        @Override protected Content live() {
            return content(CUSTOMERS, PRICES);
        }

        @Override protected Content sample() {
            return content(sampleCustomers(), Map.of("Revenant 5", 200_000.0));
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            for (Customer c : CUSTOMERS) for (Carry carry : c.carries) out.add(new Row(c.name + " " + carry.label(), carry.done + "/" + carry.requested, 0));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Customer Revenant 5", "3/10", 0), new Row("Customer Voidgloom 4", "2/2", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Slayer", "Carry tracker", "The slayer carries you do on a card: who, what, how many done and what is paid. /sbc carry <player> <type like rev5> <amount> adds one, /sbc carry <type> <price> sets its price.", "SkyHanni: Carry Tracker")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .tick(mc -> {
                    if (mc.player != null && mc.player.tickCount % 10 == 0) scan(mc);
                })
                .left(BOSSES::clear));
        ChatBus.register(ChatBus.OBSERVE + 22, ID, f, CarryTracker::chat);
        HudLayout.add(CARD);
        io.muzy.sbc.core.Commands.describe("carry", "Keep track of slayer carries: /sbc carry <player> <type> <amount>, <type> <price>, remove <player>, party <player>");
        Safe.commands("CarryTracker.commands", (d, r) -> d.register(literal("sbc").then(literal("carry")
                .then(literal("remove").then(argument("player", StringArgumentType.word()).executes(c -> {
                    String who = StringArgumentType.getString(c, "player");
                    Customer found = customer(who);
                    if (found == null) {
                        say("§cCustomer not found: §b" + who);
                        return 0;
                    }
                    CUSTOMERS.remove(found);
                    say("§fRemoved customer: §b" + who);
                    return 1;
                })))
                .then(literal("party").then(argument("player", StringArgumentType.word()).executes(c -> {
                    Customer found = customer(StringArgumentType.getString(c, "player"));
                    var conn = Minecraft.getInstance().getConnection();
                    if (found == null || conn == null) return 0;
                    StringBuilder text = new StringBuilder(found.name).append(" carry:");
                    for (Carry carry : found.carries) text.append(' ').append(carry.label()).append(' ').append(carry.done).append('/').append(carry.requested);
                    double total = totalOf(found);
                    if (total > 0) text.append(", paid ").append(Prices.coins(found.paid)).append(", missing ").append(Prices.coins(total - found.paid));
                    conn.sendCommand("pc " + text);
                    return 1;
                })))
                .then(argument("first", io.muzy.sbc.core.WordArgument.except("remove", "party"))
                        .then(argument("second", StringArgumentType.word())
                                .executes(c -> {
                                    String price = StringArgumentType.getString(c, "second");
                                    double v = shortNumber(price);
                                    if (v <= 0) {
                                        say("§cUsage: /sbc carry <player> <type> <amount>, /sbc carry <type> <price>, /sbc carry remove <player>");
                                        return 0;
                                    }
                                    say(price(StringArgumentType.getString(c, "first"), v));
                                    return 1;
                                })
                                .then(argument("amount", IntegerArgumentType.integer()).executes(c -> {
                                    say(add(StringArgumentType.getString(c, "first"), StringArgumentType.getString(c, "second"), IntegerArgumentType.getInteger(c, "amount")));
                                    return 1;
                                })))))));
    }
}
