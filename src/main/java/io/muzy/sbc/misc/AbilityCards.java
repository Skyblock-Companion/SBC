// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.misc;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.events.diana.DianaApi;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.enchants.EnchantParser;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// from skyhanni features/itemabilities ChickenHeadTimer + CrownOfAvariceCounter, each a card while you wear the helmet
public final class AbilityCards {
    public static final String CHICKEN = "chicken_head", CROWN = "crown_avarice";
    private static final long EGG_COOLDOWN = 5000, MAX_COINS = 1_000_000_000L;

    private static long lastEgg;
    private static boolean wearsChicken, wearsCrown;
    private static int tick, chickenTicks;

    private static String uuid = "";
    private static long total = -1, earned, difference;
    // session clock: time passed before the last start + when that was. started 0 = paused
    private static long passedMs, startedAt, lastCoinsAt;

    private AbilityCards() {}

    private static boolean sb() {
        return GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV;
    }

    private static ItemStack helmet() {
        var p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getItemBySlot(EquipmentSlot.HEAD);
    }

    // --- chicken head ---

    private static RichCard.Content chickenCard(long left) {
        return new RichCard.Content("", List.of(left <= 0 ? RichCard.Line.row("Next egg", "§aNow").icon("minecraft:egg").raw() : RichCard.Line.row("Next egg", "§b" + SmallMisc.time(left + 999)).icon("minecraft:egg").raw()));
    }

    private static final HudElement CHICKEN_CARD = new RichCard(CHICKEN, "Chicken Head", "Items", Zone.TOP_LEFT, 54, CHICKEN) {
        @Override public boolean visible() {
            return sb() && wearsChicken;
        }

        @Override protected Content live() {
            return chickenCard(EGG_COOLDOWN - (System.currentTimeMillis() - lastEgg));
        }

        @Override protected Content sample() {
            return chickenCard(3000);
        }

        @Override public List<Content> states() {
            return List.of(chickenCard(0));
        }

        @Override protected List<Row> classicRows() {
            long left = EGG_COOLDOWN - (System.currentTimeMillis() - lastEgg);
            return List.of(new Row("Next egg", left <= 0 ? "Now" : SmallMisc.time(left + 999), left <= 0 ? Theme.current().good() : Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Next egg", "3s", Theme.current().text()));
        }
    };

    // --- crown of avarice ---

    static long sessionMs(long now) {
        return passedMs + (startedAt > 0 ? now - startedAt : 0);
    }

    // from skyhanni onInventoryArmorUpdate: coins on the crown you wear
    static void coins(String id, long coins, long now) {
        if (!id.equals(uuid)) {
            // swapping to another crown isnt a gain
            uuid = id;
            total = coins;
            return;
        }
        if (total < 0) total = coins;
        difference = coins - total;
        if (difference == 0) return;
        if (difference < 0) {
            reset();
            total = coins;
            return;
        }
        if (startedAt == 0) startedAt = now;
        lastCoinsAt = now;
        earned += difference;
        total = coins;
    }

    static void reset() {
        earned = difference = 0;
        passedMs = startedAt = lastCoinsAt = 0;
    }

    // from skyhanni update: no coins for a while pauses the clock till the next coins
    static void pauseIfIdle(long now, long afkMs) {
        if (startedAt > 0 && now - lastCoinsAt > afkMs) {
            passedMs += now - startedAt;
            startedAt = 0;
        }
    }

    private static String fmt(long n, boolean shortForm) {
        return shortForm ? EnchantParser.shortFormat(n) : String.format(Locale.ROOT, "%,d", n);
    }

    private static RichCard.Content crownCard(long coins, long gain, long last, long ms, boolean calculating, boolean paused, boolean shortCoins, boolean shortPerHour) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Coins", "§6" + fmt(coins, shortCoins)).icon("minecraft:golden_helmet").meter((double) coins / MAX_COINS, Modern.GOLD).raw());
        double perHour = ms > 0 ? gain * 3_600_000d / ms : 0;
        String wait = "§7Calculating";
        out.add(RichCard.Line.row("Coins per hour", calculating ? wait : "§6" + fmt((long) perHour, shortPerHour)).raw().brief("Coins/h"));
        out.add(RichCard.Line.row("Time until max", calculating ? wait : perHour <= 0 ? "§bForever" : "§b" + SmallMisc.time((long) ((MAX_COINS - coins) / perHour * 3_600_000d))).raw().brief("Until max"));
        out.add(RichCard.Line.row("Last coins gained", last == 0 ? "§cnever" : "§6" + fmt(last, true)).raw().brief("Last gain"));
        out.add(RichCard.Line.row("Coins this session", "§6" + fmt(gain, false)).raw().brief("Session"));
        out.add(RichCard.Line.row("Session time", "§b" + SmallMisc.time(ms)).raw().brief("Time"));
        return new RichCard.Content(paused ? "§cPaused" : "", out);
    }

    private static final HudElement CROWN_CARD = new RichCard(CROWN, "Crown of Avarice", "Items", Zone.TOP_LEFT, 54, CROWN) {
        @Override public boolean visible() {
            return sb() && wearsCrown && DianaApi.isRitualActive();
        }

        @Override protected Content live() {
            Feature f = Features.get(CROWN);
            long now = System.currentTimeMillis();
            long ms = sessionMs(now);
            return crownCard(Math.max(0, total), earned, difference, ms, ms < f.option("crown_avarice.sessionActiveTime").integer() * 1000L, startedAt == 0, f.option("crown_avarice.shortFormat").bool(), f.option("crown_avarice.shortFormatCPH").bool());
        }

        @Override protected Content sample() {
            return crownCard(482_000_000L, 12_400_000L, 240_000L, 1_860_000L, false, false, true, true);
        }

        @Override public List<Content> states() {
            return List.of(crownCard(1_000_000L, 0, 0, 3000, true, false, true, true), crownCard(999_000_000L, 2_000_000L, 40_000L, 600_000L, false, true, false, false));
        }

        @Override protected List<Row> classicRows() {
            return List.of(new Row("Coins", fmt(Math.max(0, total), true), Theme.current().accent()), new Row("Coins this session", fmt(earned, false), Theme.current().text()));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Coins", "482M", Theme.current().accent()), new Row("Coins this session", "12,400,000", Theme.current().text()));
        }
    };

    private static void chickenTick(Minecraft mc) {
        if (++chickenTicks % 5 == 0) wearsChicken = Gain.sbId(helmet()).equals("CHICKEN_HEAD");
    }

    private static void crownTick(Minecraft mc) {
        if (++tick % 5 != 0) return;
        ItemStack head = helmet();
        wearsCrown = Gain.sbId(head).equals("CROWN_OF_AVARICE");
        if (!wearsCrown) return;
        var data = head.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = data.copyTag();
            long coins = tag.getLongOr("collected_coins", -1);
            String crown = tag.getStringOr("uuid", "");
            if (coins >= 0 && !crown.isEmpty()) coins(crown, coins, System.currentTimeMillis());
        }
        if (tick % 20 == 0) pauseIfIdle(System.currentTimeMillis(), Features.get(CROWN).option("crown_avarice.afkTimeout").integer() * 1000L);
    }

    private static ChatBus.Result chickenChat(ChatBus.Line line) {
        if (wearsChicken && "You laid an egg!".equals(line.plain().trim())) lastEgg = System.currentTimeMillis();
        return ChatBus.PASS;
    }

    public static void register() {
        Feature chicken = Features.add(new Feature(CHICKEN, "Items", "Chicken Head timer", "While you wear a Chicken Head: when you can lay the next egg.", "SkyHanni: Chicken Head Timer")
                .when(AbilityCards::sb).tick(AbilityCards::chickenTick).left(() -> lastEgg = System.currentTimeMillis()));
        ChatBus.register(ChatBus.OBSERVE + 21, CHICKEN, chicken, AbilityCards::chickenChat);
        HudLayout.add(CHICKEN_CARD);
        Features.add(new Feature(CROWN, "Events", "Crown of Avarice counter", "While you wear the crown in a Mythological Ritual: its coins, how fast they come and when it is full. /sbc resetavarice starts the session over.", "SkyHanni: Crown of Avarice Counter")
                .when(AbilityCards::sb).tick(AbilityCards::crownTick).left(() -> {
                    if (Features.get(CROWN).option("crown_avarice.resetOnWorldChange").bool()) reset();
                    total = -1;
                })
                .option(Option.integer("crown_avarice.afkTimeout", "Pause after no coins for (seconds)", "", 120, 10, 600))
                .option(Option.integer("crown_avarice.sessionActiveTime", "Wait before the numbers (seconds)", "", 10, 0, 60))
                .option(Option.bool("crown_avarice.resetOnWorldChange", "Start over on a world change", "", false))
                .option(Option.bool("crown_avarice.shortFormat", "Short counter", "7.3M instead of every digit.", true))
                .option(Option.bool("crown_avarice.shortFormatCPH", "Short coins per hour", "", true)));
        HudLayout.add(CROWN_CARD);
        Safe.commands("AbilityCards.commands", (d, b) -> d.register(ClientCommands.literal("sbc").then(ClientCommands.literal("resetavarice").executes(c -> {
            reset();
            SmallMisc.say("The Crown of Avarice session starts over.");
            return 1;
        }))));
        io.muzy.sbc.core.Commands.describe("resetavarice", "Start the Crown of Avarice session over");
    }
}
