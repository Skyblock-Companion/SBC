// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.visitor;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenData;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.SideHelper;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// puts the visitor stuff together: settings, hooks, cards
public final class VisitorFeatures {
    public static final String OFFER = "garden_visitor_offer";

    private VisitorFeatures() {}

    private static boolean offerOption(String key, boolean otherwise) {
        Feature f = Features.get(OFFER);
        Option o = f == null ? null : f.option(key);
        return o == null ? otherwise : o.bool();
    }

    // GardenVisitorTooltip.onTooltip: Accept Offer button with what it costs
    static void offerTooltip(ItemStack stack, List<Component> lines) {
        Visitors.Visitor v = Visitors.open();
        if (v == null || v.offer == null || !GameState.strip(stack.getHoverName().getString()).equals("Accept Offer")) return;
        List<String> legacy = new ArrayList<>(lines.size());
        for (int i = 1; i < lines.size(); i++) legacy.add(LegacyText.of(lines.get(i)));
        VisitorOffer.Show show = new VisitorOffer.Show(offerOption("garden_visitor_offer.price", true), offerOption("garden_visitor_offer.exact", true),
                offerOption("garden_visitor_offer.copper_price", true), offerOption("garden_visitor_offer.copper_time", false), offerOption("garden_visitor_offer.exp_price", false));
        List<String> enriched = v.offer.enrich(legacy, show);
        if (enriched.equals(legacy)) return;
        Component name = lines.get(0);
        lines.clear();
        lines.add(name);
        for (String l : enriched) lines.add(Component.literal(l));
    }

    // called from SbcMod where the keys get made
    public static void keys(KeyMapping.Category category) {
        VisitorBlock.bypassKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.visitor_bypass", InputConstants.Type.KEYBOARD, InputConstants.KEY_LCONTROL, category));
        VisitorBlock.acceptKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.visitor_accept", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
    }

    public static void register() {
        Features.add(new Feature(OFFER, "Garden", "Visitor offer prices", "In a visitor's menu, the Accept Offer button says what the items cost, what you pay per copper, how many crops they are and how long you farm them.", "SkyHanni: Visitor Inventory")
                .when(Visitors::garden)
                .tooltip(VisitorFeatures::offerTooltip)
                .option(Option.bool("garden_visitor_offer.price", "Item prices", "The Bazaar price of each item the visitor wants.", true))
                .option(Option.bool("garden_visitor_offer.exact", "Amount and time", "The exact crop amount and the time when farmed by hand.", true))
                .option(Option.bool("garden_visitor_offer.copper_price", "Price per copper", "", true))
                .option(Option.bool("garden_visitor_offer.copper_time", "Time per copper", "", false))
                .option(Option.bool("garden_visitor_offer.exp_price", "Price per Garden XP", "", false)));
        Feature block = VisitorBlock.feature();
        block.tooltip(VisitorBlock::tooltip);
        Features.add(block);
        // reads every visitor's menu for all the visitor features, whatever else is switched off
        Features.add(new Feature("visitor_engine", "Garden", "Visitor engine", "Reads what each visitor wants from their menu, for the shopping list, the buy list, the block and the stats.", "").alwaysOn().when(Visitors::garden));
        MenuMarks.add("visitor_engine", t -> true, Visitors::onMenu);
        MenuMarks.add("visitor_engine", t -> VisitorLogbook.page(t) > 0, VisitorLogbook::read);
        MenuMarks.add(VisitorBlock.FEATURE, t -> true, VisitorBlock::mark);
        Interactions.onSlotClick(Visitors::onSlotClick);

        Feature chat = VisitorChat.feature();
        Features.add(chat);
        ChatBus.register(ChatBus.FILTER + 6, VisitorChat.FEATURE, chat, VisitorChat::onChat);

        Features.add(VisitorCards.shoppingFeature());
        Features.add(VisitorCards.dropsFeature());
        Features.add(VisitorCards.charmedFeature());
        for (Feature f : VisitorWorld.features()) Features.add(f);
        VisitorWorld.registerClicks();

        ChatBus.register(ChatBus.OBSERVE + 15, "garden_visitor_drops", Features.get(VisitorCards.DROPS), VisitorCards::onDropChat);
        ChatBus.register(ChatBus.OBSERVE + 16, "garden_visitor_timer", null, VisitorTimer::onChat);
        Visitors.onArrival = v -> {
            VisitorTimer.arrived();
            VisitorChat.arrived(v);
        };
        Visitors.onAccepted = VisitorCards::accepted;
        Visitors.onRefused = VisitorCards::refused;
        SideHelper.add(VisitorPanel::of);

        Safe.endTick("VisitorFeatures.endTick", mc -> {
            if (mc.player == null || !GameState.inSkyBlock) return;
            try {
                Visitors.tick(mc);
                VisitorChat.tick();
                if (Visitors.open() != null) VisitorBlock.acceptKeyTick(mc);
                if (mc.level != null && mc.level.getGameTime() % 20 == 0) {
                    VisitorTimer.tick(mc);
                    VisitorCards.syncBuyList();
                }
            } catch (Throwable t) {
                io.github.zmuzy.sbc.SbcMod.LOG.warn("visitor tick failed", t);
            }
        });

        HudLayout.add(VisitorCards.DROPS_CARD);
        HudLayout.add(VisitorCards.CHARMED_CARD);

        Safe.commands("VisitorFeatures.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("resetvisitordrops").executes(c -> {
                    GardenData.get().resetDrops();
                    Store.markDirty();
                    c.getSource().sendFeedback(Component.literal("§b[SBC] §fVisitor statistics reset."));
                    return 1;
                }))));
        io.github.zmuzy.sbc.core.Commands.describe("resetvisitordrops", "Reset the visitor statistics of this profile");
    }
}
