// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.winter;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.areas.FishHooks;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.combat.Combat;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.events.EventData;
import io.muzy.sbc.events.EventSheet;
import io.muzy.sbc.events.bingo.BingoCard;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// gifting. from skyhanni GiftApi, UniqueGiftCounter, UniqueGiftingOpportunitiesFeatures, GiftCleanDisplay
// skyhanni tints players you havent gifted, here they glow where you can see them, same info
public final class Gifting {
    public static final String COUNTER = "gift_unique_counter", PLAYERS = "gift_players", CLEAN = "gift_clean_display", FILTER = "gift_chat_filter";
    // GiftProfitTracker.spamPatterns without colours
    private static final java.util.regex.Pattern GIFT_SPAM = java.util.regex.Pattern.compile("This player is playing a profile mode which doesn't allow gifting!|An error occurred!|Can't place gifts this close to spawn!|You cannot place a gift so close to an NPC!|Click a player to gift them! This isn't a player!|.*disconnected, gift refunded!|This gift is for .*, sorry!");
    private static final int MAX = 600, GENEROW_SLOT = 40;
    private static final String GIFTED_NAMETAG = "§c§lꤥ";

    private Gifting() {}

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    // GiftApi.isHoldingGift: the giver one, the counter takes every _GIFT id
    private static boolean holdingGift() {
        var p = Minecraft.getInstance().player;
        return p != null && Patterns.get("SH.event.winter.giftapi.giftname").matcher(Gain.sbId(p.getMainHandItem())).matches();
    }

    private static boolean holdingAnyGift() {
        var p = Minecraft.getInstance().player;
        return p != null && Gain.sbId(p.getMainHandItem()).endsWith("_GIFT");
    }

    // player a line says you gifted, or null
    static String giftedPlayer(String clean) {
        Matcher m = Patterns.get("SH.event.winter.uniquegifts.gifted.colorless").matcher(clean);
        return m.matches() ? m.group("player") : null;
    }

    // ChatFilter winterGiftPatterns: what gifts give + what it says when you cant give one
    static boolean giftChat(String clean) {
        return io.muzy.sbc.events.EventPack.plain("SH.misc.gifting.reward.generic").matcher(clean).matches() || io.muzy.sbc.events.EventPack.plain("SH.misc.gifting.reward.northstars").matcher(clean).matches() || GIFT_SPAM.matcher(clean).matches();
    }

    // onInventoryFullyOpened, -1 if the lore doesnt say
    static int generowAmount(MenuView.Item item) {
        Matcher m = item.firstClean(io.muzy.sbc.events.EventPack.plain("SH.event.winter.uniqugifts.counter.amount"));
        if (m == null) return -1;
        try {
            return Integer.parseInt(m.group("amount").replace(",", "").trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // isBingo + isIronman: players you cant give to
    static boolean cannotGive(String displayName, boolean youBingo, boolean youNoTrade) {
        return !youBingo && displayName.endsWith("Ⓑ") || !youNoTrade && displayName.endsWith("♲");
    }

    private static boolean noTrade() {
        String t = GameState.profileType == null ? "" : GameState.profileType.toLowerCase(Locale.ROOT);
        return t.contains("ironman") || t.contains("stranded") || t.contains("bingo");
    }

    private static void counterLine(EventSheet s, int amount) {
        s.line("Unique players gifted", amount + "/" + MAX, amount >= MAX ? Modern.GOOD : 0xFFFFFF55).tail(Math.min(100, amount * 100 / MAX) + "%").meter(Math.min(1, amount / (double) MAX)).brief("Unique gifted").icon("chest");
    }

    private static final HudElement CARD = new EventSheet.Card(COUNTER, "Unique gifts", "Events", Zone.TOP_LEFT, 49, COUNTER) {
        @Override
        public boolean visible() {
            return sb() && Winter.december() && holdingAnyGift();
        }

        @Override
        protected void live(EventSheet s) {
            counterLine(s, EventData.get().amountGifted);
        }

        @Override
        protected void sample(EventSheet s) {
            counterLine(s, 412);
        }

        @Override
        protected List<Row> plainRows() {
            return List.of(new Row("Unique players gifted", EventData.get().amountGifted + "/" + MAX, 0));
        }

        @Override
        protected List<Row> plainDemo() {
            return List.of(new Row("Unique players gifted", "412/" + MAX, 0));
        }
    };

    private static final FishHooks.Scan SCAN = new FishHooks.Scan() {
        private boolean highlight, armorStands;

        @Override
        public boolean wanted() {
            armorStands = Features.on(PLAYERS) && Combat.opt(PLAYERS, "armor_stands");
            highlight = Features.on(PLAYERS) && Winter.december() && (!Combat.opt(PLAYERS, "only_holding") || holdingGift());
            return highlight || armorStands || Features.on(CLEAN);
        }

        @Override
        public void entity(Entity e) {
            if (e instanceof ArmorStand a) {
                if (!a.hasCustomName()) return;
                String clean = a.getCustomName().getString();
                if (Features.on(CLEAN) && (clean.startsWith("From:") || clean.startsWith("To:"))) FishHooks.hide(a);
                if (armorStands && LegacyText.of(a.getName(), true, false).replace("§r", "").equals(GIFTED_NAMETAG)) analyze(a);
                return;
            }
            if (!highlight || !(e instanceof RemotePlayer p) || p.getUUID().version() == 2) return;
            if (cannotGive(p.getDisplayName().getString(), BingoCard.onBingo(), noTrade())) return;
            if (EventData.get().gifted.contains(p.getName().getString())) return;
            if (Combat.seen(p, 64, p.getBbHeight() * 0.8)) FishHooks.glow(p, 0x00AA00);
        }

        // analyzeArmorStand: the gifted mark, over one player and no other
        private void analyze(ArmorStand a) {
            Player only = null;
            for (Player p : a.level().getEntitiesOfClass(Player.class, a.getBoundingBox().inflate(2))) {
                if (p.getUUID().version() == 2) continue;
                if (only != null) return;
                only = p;
            }
            if (only != null && EventData.get().gifted.add(only.getName().getString())) Store.markDirty();
        }
    };

    public static void register() {
        Features.add(new Feature(COUNTER, "Events", "Unique gift counter", "While you hold a gift in December, how many different players you have given one to. Open Generow's menu to set it.", "SkyHanni: Unique Gift Counter").when(Gifting::sb));
        Features.add(new Feature(PLAYERS, "Events", "Players you have not gifted", "In December, a glow on players you have not given a gift to, where you can see them.", "SkyHanni: Unique Gifting Opportunities")
                .when(Gifting::sb)
                .option(Option.bool("gift_players.only_holding", "Only while holding a gift", "", true))
                .option(Option.bool("gift_players.armor_stands", "Learn from the marks over players", "A little inaccurate, but it knows people you gifted before you had this.", false)));
        Features.add(new Feature(CLEAN, "Events", "Clean gifts", "Hides the From and To lines over gifts in the world, leaving only the line to open them.", "SkyHanni: Gift Clean Display").when(Gifting::sb).defaultOff());
        Feature filter = Features.add(new Feature(FILTER, "Chat", "Hide gift rewards", "Hides what gifts give you and the lines about gifts you cannot place. The Gift profit card still counts them.", "SkyHanni: Winter Gifts").when(Gifting::sb).defaultOff());
        ChatBus.register(ChatBus.FILTER + 29, FILTER, filter, line -> giftChat(GameState.strip(line.plain())) ? ChatBus.CANCEL : ChatBus.PASS);
        MenuMarks.add(COUNTER, "Generow", v -> {
            int amount = generowAmount(v.item(GENEROW_SLOT));
            if (amount >= 0) {
                EventData.get().amountGifted = amount;
                Store.markDirty();
            }
        });
        ChatBus.register(ChatBus.OBSERVE + 28, "gifts given", null, line -> {
            if (!GameState.inSkyBlock) return ChatBus.PASS;
            String player = giftedPlayer(GameState.strip(line.plain()));
            if (player != null) {
                EventData d = EventData.get();
                d.gifted.add(player);
                d.amountGifted++;
                Store.markDirty();
            }
            return ChatBus.PASS;
        });
        FishHooks.scan(SCAN);
        HudLayout.add(CARD);
    }
}
