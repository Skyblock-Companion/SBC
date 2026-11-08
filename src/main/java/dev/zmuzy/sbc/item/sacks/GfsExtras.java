// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.sacks;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.calc.Calculator;
import dev.zmuzy.sbc.item.market.Market;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// from skyhanni GetFromSackApi: your own /gfs commands queued so they all arrive, an offer to buy what the
// sacks dont have
// the message after a super craft (SuperCraftFeatures) and after your piggy bank breaks (GFSPiggyBank)
// only sends commands you typed or clicked. we dont have the repos sack item list, so a command counts if its
// item is known at all
public final class GfsExtras {
    private static final String KEY = "gfs_key";
    private static final long MINIMUM_DELAY_MS = 1650;
    private static final Patterns.P FROM_SACKS = Patterns.p("SH.gfs.chat.from");
    private static final Patterns.P MISSING = Patterns.p("SH.gfs.chat.missing");
    private static final Patterns.P SUPERCRAFTED = Patterns.p("SH.inventory.supercrafting.craft.new");
    private static final Patterns.P PIGGY_CRACKED = Patterns.p("SH.misc.piggybank.cracked");
    private static final Patterns.P PIGGY_BROKE = Patterns.p("SH.misc.piggybank.broke");

    // what a command asks for
    record Stack(String id, int amount) {}

    private static final Deque<Stack> QUEUE = new ArrayDeque<>();
    private static long lastCommandAt;
    private static Stack lastItemStack;
    private static boolean sendingOurOwn;

    private GfsExtras() {}

    private static boolean b(String key, boolean def) {
        return Market.b(KEY, key, def);
    }

    private static Double calculateOrNull(String s) {
        try {
            return Calculator.calculate(s);
        } catch (Calculator.CalculatorException | RuntimeException e) {
            return null;
        }
    }

    // skyhanni commandValidator: what comes after /gfs. null = send it to the server as is
    static Stack validate(List<String> args, int defaultAmount, java.util.function.Predicate<String> knownId, java.util.function.Function<String, String> idOfName) {
        if (args.isEmpty()) return null;
        Double pre = calculateOrNull(args.getLast());
        double amount = pre == null ? defaultAmount : pre;
        List<String> name = pre == null ? args : args.subList(0, args.size() - 1);
        if (name.isEmpty()) return null;
        String item = String.join(" ", name).toUpperCase(Locale.ROOT).replace(':', '-');
        if (knownId.test(item)) return new Stack(item, (int) amount);
        String id = idOfName.apply(item.replace("_", " "));
        return id == null || id.isEmpty() ? null : new Stack(id, (int) amount);
    }

    private static Stack validate(String[] args) {
        Option def = Features.get(KEY).option("gfs_key.defaultAmount");
        return validate(Arrays.asList(args).subList(1, args.length), def == null ? 1 : def.integer(), id -> ItemRepo.get(id) != null, ItemRepo::idForName);
    }

    // skyhanni queuedHandler + bazaarHandler. false holds the command back for now
    private static boolean onCommand(String command) {
        if (sendingOurOwn || !(GameState.inSkyBlock || SbcMod.DEV) || !Features.on(KEY)) return true;
        String[] parts = command.trim().split(" +");
        if (parts.length == 0 || !(parts[0].equalsIgnoreCase("gfs") || parts[0].equalsIgnoreCase("getfromsacks"))) return true;
        Stack stack = validate(parts);
        if (stack == null) return true;
        if (b("gfs_key.bazaar", false) && !Market.noTrade()) lastItemStack = stack;
        if (!b("gfs_key.queued", false)) return true;
        QUEUE.add(stack);
        return false;
    }

    // skyhanni GetFromSackApi.onTick: one at a time as the server takes them
    private static void tick(Minecraft mc) {
        if (QUEUE.isEmpty() || mc.getConnection() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCommandAt < MINIMUM_DELAY_MS) return;
        Stack item = QUEUE.poll();
        lastCommandAt = now;
        sendingOurOwn = true;
        try {
            mc.getConnection().sendCommand("gfs " + item.id().replace('-', ':') + " " + item.amount());
        } finally {
            sendingOurOwn = false;
        }
    }

    private static void clickable(String text, String command, String hover) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        p.sendSystemMessage(Component.literal(text).withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))));
    }

    // from skyhanni getFromChatMessageSackItems
    private static void offerFromSacks(String id, int amount) {
        clickable("§b[SBC] §e§lCLICK HERE§r§e to grab §ax" + amount + " §9" + ItemRepo.name(id) + "§e from sacks!", "/gfs " + id.replace('-', ':') + " " + amount, "§eClick to get from sacks!");
    }

    // from skyhanni bazaarMessage
    private static void offerFromBazaar(String id, int amount, boolean remaining) {
        String name = GameState.strip(ItemRepo.name(id));
        Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(amount));
        clickable("§b[SBC] §e§lCLICK §r§eto get the " + (remaining ? "remaining " : "") + "§ax" + amount + " §9" + name + " §efrom bazaar", "/bz " + name, "§eClick to find on the bazaar!");
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!(GameState.inSkyBlock || SbcMod.DEV) || !Features.on(KEY)) return ChatBus.PASS;
        String message = LegacyText.of(line.component());
        Stack stack = lastItemStack;
        if (stack != null && b("gfs_key.bazaar", false)) {
            Matcher m = FROM_SACKS.matcher(message);
            if (m.matches()) {
                int diff = stack.amount() - Integer.parseInt(m.group("amount"));
                lastItemStack = null;
                if (diff > 0 && Prices.get(stack.id()) != null) offerFromBazaar(stack.id(), diff, true);
                return ChatBus.PASS;
            }
            if (MISSING.matcher(message).matches()) {
                lastItemStack = null;
                offerFromBazaar(stack.id(), stack.amount(), false);
                return ChatBus.PASS;
            }
        }
        if (b("gfs_key.superCraft", true)) {
            Matcher m = SUPERCRAFTED.matcher(message);
            if (m.matches()) {
                String id = ItemRepo.idForName(m.group("item"));
                String amount = m.group("amount");
                if (id != null && SackApi.sackData.containsKey(id)) offerFromSacks(id, amount == null ? 1 : Integer.parseInt(amount.replace(",", "")));
                return ChatBus.PASS;
            }
        }
        if (b("gfs_key.piggyBank", true) && (PIGGY_CRACKED.matcher(message).matches() || PIGGY_BROKE.matcher(message).matches())) offerFromSacks("ENCHANTED_PORK", 8);
        return ChatBus.PASS;
    }

    // options for the get from sack key, the key itself stays in Builtin
    public static void register() {
        Feature f = Features.get(KEY);
        f.option(Option.bool("gfs_key.fill", "The key fills your inventory", "The key asks for as many as fit instead of one stack.", false));
        f.option(Option.bool("gfs_key.queued", "Queue /gfs", "Your own /gfs commands are sent one by one so that all of them arrive.", false));
        f.option(Option.bool("gfs_key.bazaar", "Offer the Bazaar for what is missing", "When the sacks do not have enough, a message you can click to look it up.", false));
        f.option(Option.bool("gfs_key.superCraft", "Offer it after a super craft", "A message you can click to take what you just crafted out of your sacks.", true));
        f.option(Option.bool("gfs_key.piggyBank", "Offer pork when your piggy bank breaks", "A message you can click to take Enchanted Pork out of your sacks.", true));
        f.option(Option.integer("gfs_key.defaultAmount", "Default amount", "What /gfs asks for when you name no amount.", 1, 1, 2240));
        Safe.allowCommand("GfsExtras.onCommand", GfsExtras::onCommand);
        Safe.endTick("GfsExtras.tick", GfsExtras::tick);
        ChatBus.register(ChatBus.OBSERVE + 27, "gfs", null, GfsExtras::onChat);
    }
}
