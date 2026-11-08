// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.market;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// super crafting. from skyhanni SuperCraftingInventory: a click on the golden pickaxe that wastes coins vs
// buying the result and selling the mats is refused, ctrl goes through
// skyhanni SuperCraftPresets (the list behind the amount sign buttons, drawn by BazaarSign, + its command),
// skyblocker SupercraftReminder
public final class SuperCraft {
    public static final String ID = "super_craft";
    private static final int[] MATERIAL_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int PICKAXE_SLOT = 32, RESULT_SLOT = 25;
    private static final Patterns.P CRAFTING_COUNT = Patterns.p("SH.supercrafting-inventory.crafting.count");
    private static final Patterns.P CRAFTING_RESOURCE = Patterns.p("SH.supercrafting-inventory.crafting.resource");
    private static final Patterns.P INVENTORY = Patterns.p("SH.supercrafting-inventory.inventory.name");
    private static final Pattern RECIPE_MENU = Pattern.compile("^.+ Recipe$");

    private SuperCraft() {}

    private static boolean b(String key, boolean def) {
        return Market.b(ID, key, def);
    }

    private static double millions(String key, double def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return (o == null ? def : o.number()) * 1_000_000d;
    }

    // skyhanni calculatePriceOfAvailableOrders: walks the book bc the top offer alone can be inflated. null when
    // the book runs out
    static Double priceOfOrders(double[][] offers, long count) {
        long remaining = count;
        double total = 0;
        for (double[] offer : offers) {
            long take = Math.min((long) offer[0], remaining);
            total += take * offer[1];
            remaining -= take;
            if (remaining <= 0) break;
        }
        return remaining > 0 ? null : total;
    }

    private static Double bookPrice(String id, long count, boolean sellOffer) {
        Prices.Book book = Prices.book(id);
        return book == null ? null : priceOfOrders(sellOffer ? book.buySummary() : book.sellSummary(), count);
    }

    // skyhanni calculateMaxPossible on one lore line
    static Long maxPossible(String cleanLine, long craftingAmount, int craftMultiplier) {
        Matcher m = CRAFTING_RESOURCE.matcher(cleanLine);
        if (!m.matches()) return null;
        long owned = Long.parseLong(m.group("owned").replace(",", "")), used = Long.parseLong(m.group("used").replace(",", ""));
        if (used == 0 || owned == 0) return null;
        long matsPerCraft = used / (craftingAmount / craftMultiplier);
        return matsPerCraft == 0 ? null : owned / matsPerCraft;
    }

    // skyhanni blockWasteClick minus the modifier key
    static boolean wasteful(double profit, long craftingAmount, long maxCraftingAmount, double warn, double bulkWarn) {
        if (profit < -warn) return true;
        return profit < -bulkWarn && craftingAmount == maxCraftingAmount;
    }

    // skyhanni SuperCraftingInventory.onSlotClick, true eats the click
    private static boolean onClick(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, int slotId, int button) {
        if (!Features.on(ID) || !b("super_craft.waste", false) || button != 0 || slotId != PICKAXE_SLOT || Market.noTrade()) return false;
        String title = GameState.strip(screen.getTitle().getString());
        if (!INVENTORY.matcher(title).matches()) return false;
        var slots = screen.getMenu().slots;
        if (slots.size() <= PICKAXE_SLOT) return false;
        List<String> pickaxe = new ArrayList<>();
        for (String l : LegacyText.lore(slots.get(PICKAXE_SLOT).getItem())) pickaxe.add(GameState.strip(l));
        Matcher count = CRAFTING_COUNT.matcher(String.join(" ", pickaxe));
        if (!count.matches()) return false;
        long craftingAmount = Long.parseLong(count.group("count").replace(",", ""));
        ItemStack result = slots.get(RESULT_SLOT).getItem();
        int craftMultiplier = result.getCount();
        String resultId = Gain.sbId(result);
        if (craftMultiplier == 0 || resultId.isEmpty()) return false;
        Map<String, Long> materials = new LinkedHashMap<>();
        for (int s : MATERIAL_SLOTS) {
            ItemStack st = slots.get(s).getItem();
            if (st.isEmpty()) continue;
            String id = Gain.sbId(st);
            if (id.isEmpty()) return false;
            materials.merge(id, (long) st.getCount(), Long::sum);
        }
        double itemsPrice = 0;
        for (var e : materials.entrySet()) {
            Double p = bookPrice(e.getKey(), e.getValue() * (craftingAmount / craftMultiplier), false);
            if (p == null) return false;
            itemsPrice += p;
        }
        Double resultPrice = bookPrice(resultId, craftingAmount, true);
        if (resultPrice == null) return false;
        double profit = resultPrice - itemsPrice;
        long max = Long.MAX_VALUE;
        for (String l : pickaxe) {
            Long possible = maxPossible(l, craftingAmount, craftMultiplier);
            if (possible != null) max = Math.min(max, possible);
        }
        if (max == Long.MAX_VALUE) return false;
        var mc = Minecraft.getInstance();
        boolean cookie = GameState.hasCookie();
        double warn = millions(cookie ? "super_craft.threshold" : "super_craft.noCookieThreshold", cookie ? 10 : 20);
        double bulk = millions(cookie ? "super_craft.bulkThreshold" : "super_craft.noCookieBulkThreshold", cookie ? 5 : 10);
        if (InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_RCONTROL)) return false;
        if (!wasteful(profit, craftingAmount, max, warn, bulk)) return false;
        String diff = Prices.coins(-profit);
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.6f);
            mc.player.sendSystemMessage(Component.literal("§b[SBC] §eSuper Craft blocked: selling the materials and buying the result is cheaper by §c" + diff + "§e. Hold §fCtrl §eto craft anyway."));
        }
        mc.gui.hud.setTimes(5, 40, 10);
        mc.gui.hud.setTitle(Component.literal("§cSuper Crafting Blocked"));
        mc.gui.hud.setSubtitle(Component.literal("§7Hold §eCtrl §7to bypass. Potential loss: §c" + diff));
        return true;
    }

    // skyblocker SupercraftReminder: on the golden pickaxe of a recipe whose result stacks
    private static void tooltip(ItemStack stack, List<Component> lines) {
        if (!b("super_craft.reminder", true) || !stack.is(Items.GOLDEN_PICKAXE) || lines.size() < 2) return;
        var mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs) || !(cs instanceof dev.zmuzy.sbc.mc.mixin.ContainerAccess acc)) return;
        Slot focused = acc.sbc$hovered();
        if (focused == null || focused.index != PICKAXE_SLOT || focused.getItem() != stack || !RECIPE_MENU.matcher(GameState.strip(cs.getTitle().getString())).matches()) return;
        // items with a uuid cant stack, so shift click changes nothing for them
        var cd = cs.getMenu().slots.get(RESULT_SLOT).getItem().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (cd != null && !cd.copyTag().getStringOr("uuid", "").isEmpty()) return;
        int index = lines.size() - 1;
        if (lines.get(lines.size() - 2).getString().equals("Recipe not unlocked!")) index--;
        lines.add(index, Component.literal("Shift-Click to maximize the amount!").withStyle(ChatFormatting.GOLD));
    }

    // skyhanni togglePreset: a number thats there goes, one thats not gets added
    static String togglePreset(String list, int number) {
        List<Integer> presets = new ArrayList<>(BazaarSign.presets(list));
        if (!presets.remove(Integer.valueOf(number))) presets.add(number);
        presets.sort(null);
        StringBuilder sb = new StringBuilder();
        for (int v : presets) sb.append(sb.isEmpty() ? "" : ",").append(v);
        return sb.toString();
    }

    private static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal(text));
    }

    private static void showPresets() {
        Option o = Features.get(ID).option("super_craft.presetList");
        List<Integer> presets = BazaarSign.presets(o.text());
        if (presets.isEmpty()) say("§b[SBC] §7Current presets: §cnone");
        else {
            StringBuilder sb = new StringBuilder();
            for (int v : presets) sb.append(sb.isEmpty() ? "§e" : "§7, §e").append(String.format(Locale.ROOT, "%,d", v));
            say("§b[SBC] §7Current presets: " + sb);
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Menus", "Super craft helpers", "Puts your amounts as buttons in the amount sign and reminds you that Shift-Click maximizes. If you switch it on, stops a super craft that wastes coins against buying the result and selling the materials (hold Ctrl to go through).", "SkyHanni: super craft presets, waste warning · Skyblocker: supercraft reminder")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tooltip(SuperCraft::tooltip)
                .option(Option.bool("super_craft.waste", "Waste warning", "Refuse the click when the craft loses more than the amounts below.", false))
                .option(Option.number("super_craft.threshold", "Loss to stop at (millions)", "With a Booster Cookie.", 10, 0, 1000, 1))
                .option(Option.number("super_craft.bulkThreshold", "Loss to stop a full craft at (millions)", "With a Booster Cookie, when crafting all you can.", 5, 0, 1000, 1))
                .option(Option.number("super_craft.noCookieThreshold", "Loss to stop at, no cookie (millions)", "", 20, 0, 1000, 1))
                .option(Option.number("super_craft.noCookieBulkThreshold", "Loss to stop a full craft at, no cookie (millions)", "", 10, 0, 1000, 1))
                .option(Option.bool("super_craft.presets", "Amount buttons in the sign", "Your amounts as buttons in the of crafts sign.", true))
                .option(Option.text("super_craft.presetList", "Amounts", "Separated by commas, or use /sbc supercraftpreset <number>.", "4,8,16,32,64,128,256,512"))
                .option(Option.bool("super_craft.reminder", "Shift-Click reminder", "On the golden pickaxe of a recipe.", true)));
        Interactions.onSlotClick((screen, slot, slotId, button) -> {
            try {
                return onClick(screen, slotId, button);
            } catch (RuntimeException e) {
                SbcMod.LOG.warn("super craft check failed", e);
                return false;
            }
        });
        Safe.commands("SuperCraft.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("supercraftpreset")
                        .executes(c -> {
                            showPresets();
                            say("§6/sbc supercraftpreset <number> §7- Add or remove a preset amount.");
                            return 1;
                        })
                        .then(argument("number", integer(1)).executes(c -> {
                            Option o = Features.get(ID).option("super_craft.presetList");
                            int number = getInteger(c, "number");
                            boolean had = BazaarSign.presets(o.text()).contains(number);
                            o.set(togglePreset(o.text(), number));
                            dev.zmuzy.sbc.core.Config.save();
                            say(had ? "§b[SBC] §cRemoved §e" + String.format(Locale.ROOT, "%,d", number) + " §cfrom supercraft presets." : "§b[SBC] §aAdded §e" + String.format(Locale.ROOT, "%,d", number) + " §ato supercraft presets.");
                            showPresets();
                            return 1;
                        })))));
        dev.zmuzy.sbc.core.Commands.describe("supercraftpreset", "Add or remove an amount button of the super craft sign");
        Safe.commands("SuperCraft.commands#2", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("preventsell").executes(c -> {
                    dev.zmuzy.sbc.item.SlotLocks.toggleHeldForSale(Minecraft.getInstance().player);
                    return 1;
                }))));
        dev.zmuzy.sbc.core.Commands.describe("preventsell", "Protect the held item from being sold, or stop protecting it");
    }
}
