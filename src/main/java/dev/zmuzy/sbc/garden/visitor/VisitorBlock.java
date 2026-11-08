// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.visitor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.visitor.Visitors.BlockReason;
import dev.zmuzy.sbc.garden.visitor.Visitors.Visitor;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// from skyhanni VisitorRewardWarning + VisitorApi.blockReason: the offer button that would lose you something
// is greyed and eats the click, the other gets an outline, tooltip says why. a key lets the click through
public final class VisitorBlock {
    public static final String FEATURE = "garden_visitor_block";
    // RewardWarningConfig.bypassKey, set when keys get made
    public static KeyMapping bypassKey;
    // VisitorConfig.acceptHotkey
    public static KeyMapping acceptKey;
    private static boolean acceptWasDown, bypassWasDown;
    private static long lastAccept;

    private VisitorBlock() {}

    private static Option option(String key) {
        Feature f = Features.get(FEATURE);
        return f == null ? null : f.option(key);
    }

    private static boolean on(String key, boolean otherwise) {
        Option o = option(key);
        return o == null ? otherwise : o.bool();
    }

    private static int number(String key, int otherwise) {
        Option o = option(key);
        return o == null ? otherwise : (int) o.number();
    }

    static boolean warnsFor(VisitorReward r) {
        return on(r.optionKey(), r.warnsAtFirst);
    }

    static boolean bingo() {
        return GameState.bingo;
    }

    // settings blockReason looks at
    public record Rules(boolean reward, boolean fresh, boolean refuseCheap, boolean acceptExpensive, boolean refuseLowLoss, boolean acceptHighLoss, int coinsPerCopper, int lossThreshold) {}

    static Rules rules() {
        return new Rules(on("garden_visitor_block.prevent_reward", true), on("garden_visitor_block.prevent_new", true), on("garden_visitor_block.prevent_cheap_copper", false),
                on("garden_visitor_block.prevent_expensive_copper", false), on("garden_visitor_block.prevent_low_loss", false), on("garden_visitor_block.prevent_high_loss", false),
                number("garden_visitor_block.coins_per_copper", 6_000), number("garden_visitor_block.loss_threshold", 150_000));
    }

    // VisitorApi.blockReason. an offer without copper has no price per copper to go by
    public static BlockReason reason(boolean rareReward, Integer offersAccepted, boolean bingo, Integer pricePerCopper, double totalPrice, Double totalReward, Rules r) {
        if (r.reward() && rareReward) return BlockReason.RARE_REWARD;
        if (r.fresh() && !bingo && offersAccepted != null && offersAccepted == 0) return BlockReason.NEVER_ACCEPTED;
        if (pricePerCopper == null || totalReward == null) return null;
        double loss = totalPrice - totalReward;
        if (r.refuseCheap() && pricePerCopper <= r.coinsPerCopper()) return BlockReason.CHEAP_COPPER;
        if (r.acceptExpensive() && pricePerCopper > r.coinsPerCopper()) return BlockReason.EXPENSIVE_COPPER;
        if (r.refuseLowLoss() && loss <= r.lossThreshold()) return BlockReason.LOW_LOSS;
        if (r.acceptHighLoss() && loss > r.lossThreshold()) return BlockReason.HIGH_LOSS;
        return null;
    }

    static BlockReason reason(Visitor v) {
        if (v.offer == null) return null;
        return reason(!v.warned().isEmpty(), v.offersAccepted, bingo(), v.offer.pricePerCopper, v.offer.totalPrice, v.offer.totalReward, rules());
    }

    static boolean bypass() {
        return keyDown(bypassKey);
    }

    private static boolean keyDown(KeyMapping key) {
        return dev.zmuzy.sbc.mc.KeyHeld.held(key);
    }

    // swallow this button click?
    static boolean blocks(Visitor v, boolean refuse) {
        if (!Features.on(FEATURE) || v.blockReason == null || bypass()) return false;
        return v.blockReason.blockRefusing == refuse;
    }

    // onForegroundDrawn: the button you shouldnt press darker, frame around the better one
    static void mark(MenuView v) {
        Visitors.onMenu(v);
        Visitor visitor = Visitors.open();
        if (visitor == null || visitor.blockReason == null) return;
        int blocked = visitor.blockReason.blockRefusing ? Visitors.REFUSE_SLOT : Visitors.ACCEPT_SLOT;
        int other = visitor.blockReason.blockRefusing ? Visitors.ACCEPT_SLOT : Visitors.REFUSE_SLOT;
        int alpha = Math.max(0, Math.min(255, number("garden_visitor_block.transparency", 180)));
        // onForegroundDrawn: not while the bypass key is held
        if (!bypass()) v.mark(blocked).front = alpha << 24 | 0x555555;
        if (on("garden_visitor_block.outline", true)) v.mark(other).border = 200 << 24 | (visitor.blockReason.blockRefusing ? 0x55FF55 : 0xFF5555);
    }

    // onTooltip
    static void tooltip(ItemStack stack, List<Component> lines) {
        Visitor visitor = Visitors.open();
        if (visitor == null || visitor.blockReason == null || bypass() || visitor.offer == null) return;
        String name = GameState.strip(stack.getHoverName().getString());
        BlockReason reason = visitor.blockReason;
        if (reason.blockRefusing ? !name.equals("Refuse Offer") : !name.equals("Accept Offer")) return;
        for (int i = 1; i < lines.size(); i++) lines.set(i, Component.literal("§8" + GameState.strip(lines.get(i).getString())));
        lines.add(Component.empty());
        lines.add(Component.literal(description(reason, visitor)));
        lines.add(Component.literal("  §7(Bypass by holding " + (bypassKey == null ? "the key" : bypassKey.getTranslatedKeyMessage().getString()) + ")"));
    }

    private static String description(BlockReason reason, Visitor v) {
        VisitorOffer o = v.offer;
        return reason.description + switch (reason) {
            case CHEAP_COPPER, EXPENSIVE_COPPER -> " §7(paying §6" + Prices.coins(o.pricePerCopper) + " §7per)";
            case LOW_LOSS, HIGH_LOSS -> {
                double loss = o.totalPrice - o.totalReward;
                yield " §7(§6" + Prices.coins(Math.abs(loss)) + " §7" + (loss > 0 ? "loss" : "profit") + " selling §9Green Thumb I§7)";
            }
            default -> "";
        };
    }

    // VisitorListener.onKeybind: your key press clicks Accept Offer, once per press. blocked stays blocked
    static void acceptKeyTick(Minecraft mc) {
        boolean bypassing = bypass();
        if (bypassing != bypassWasDown) {
            bypassWasDown = bypassing;
            MenuMarks.invalidate();
        }
        boolean down = keyDown(acceptKey);
        boolean pressed = down && !acceptWasDown;
        acceptWasDown = down;
        Visitor visitor = Visitors.open();
        if (!pressed || visitor == null || mc.gameMode == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (now - lastAccept < 300 || blocks(visitor, false)) return;
        lastAccept = now;
        var menu = mc.player.containerMenu;
        var slot = menu.getSlot(Visitors.ACCEPT_SLOT);
        if (MenuView.Item.of(slot.getItem()).cleanHas("Click to give!")) Visitors.changeStatus(visitor, Visitors.Status.ACCEPTED);
        mc.gameMode.handleContainerInput(menu.containerId, Visitors.ACCEPT_SLOT, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, mc.player);
    }

    static Feature feature() {
        Feature f = new Feature(FEATURE, "Garden", "Visitor offer block", "In a visitor's menu, Refuse Offer takes no click when the visitor has a reward on your list or you never accepted an offer from it. Hold the bypass key to click anyway. The copper and coin limits are off until you switch them on.", "SkyHanni: Visitor Reward Warning")
                .when(Visitors::garden)
                .option(Option.bool("garden_visitor_block.prevent_reward", "Block refusing a reward", "Refuse Offer is blocked when the visitor has a reward from the list below.", true))
                .option(Option.bool("garden_visitor_block.prevent_new", "Block refusing new visitors", "Refuse Offer is blocked for a visitor you never accepted an offer from. Not on Bingo.", true))
                .option(Option.integer("garden_visitor_block.coins_per_copper", "Coins per copper", "The limit of the two copper blocks below.", 6_000, 1, 50_000))
                .option(Option.bool("garden_visitor_block.prevent_cheap_copper", "Block refusing cheap copper", "Refuse Offer is blocked at this many coins per copper or less.", false))
                .option(Option.bool("garden_visitor_block.prevent_expensive_copper", "Block accepting expensive copper", "Accept Offer is blocked above this many coins per copper.", false))
                .option(Option.integer("garden_visitor_block.loss_threshold", "Acceptable coin loss", "The limit of the two loss blocks below, against selling Green Thumb I.", 150_000, 1, 10_000_000))
                .option(Option.bool("garden_visitor_block.prevent_low_loss", "Block refusing a low loss", "Refuse Offer is blocked at this loss or less.", false))
                .option(Option.bool("garden_visitor_block.prevent_high_loss", "Block accepting a high loss", "Accept Offer is blocked above this loss.", false))
                .option(Option.integer("garden_visitor_block.transparency", "Darkness of the blocked button", "0 is not darker at all, 255 is fully grey.", 180, 0, 255))
                .option(Option.bool("garden_visitor_block.outline", "Outline the other button", "A green or red frame around the button to press.", true))
                .option(Option.bool("garden_visitor_block.notify_chat", "Say a found reward in chat", "A line in chat when you talk to a visitor with a reward on the list.", true))
                .option(Option.bool("garden_visitor_block.over_name", "Show a reward over the visitor", "The reward's name above the visitor in the world.", true));
        for (VisitorReward r : VisitorReward.values())
            f.option(Option.bool(r.optionKey(), "Reward: " + r.plain, "Warn about this reward.", r.warnsAtFirst));
        return f;
    }
}
