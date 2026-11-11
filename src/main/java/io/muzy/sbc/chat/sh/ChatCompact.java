// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.chat.sh;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// glue for the skyhanni chat ports in chat/sh: the ChatBus listener in skyhanni's order, action bar reader
// for skyblock xp, nearby players for far deaths, experiment menu close time, delayed sends
public final class ChatCompact {
    public static final String POTIONS = "chat_compact_potions", BESTIARY = "chat_compact_bestiary", EXPERIMENTS = "chat_compact_experiments", SB_XP = "chat_skyblock_xp",
            PLAYER = "chat_player_messages", COINS = "chat_shorten_coins", PET_RARITY = "chat_pet_rarity", FAR_DEATHS = "chat_hide_far_deaths";

    private static final Map<String, Long> lastTimePlayerSeen = new HashMap<>();
    private static long lastSecond, flushAt = Long.MAX_VALUE;
    private static String lastActionBar = "";
    private static int lastMenuId = -1;
    private static boolean lastMenuWasExperiment;

    private ChatCompact() {}

    static boolean b(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    private static String ownName() {
        var mc = Minecraft.getInstance();
        return mc == null || mc.getUser() == null ? "" : mc.getUser().getName();
    }

    // ChatUtils.hoverableChat / chat(prefix = false): straight to the chat box, not through the chain
    static void send(CompactMessages.Out out) {
        if (out != null) sendTo.accept(out);
    }

    // tests read compact lines here
    static java.util.function.Consumer<CompactMessages.Out> sendTo = ChatCompact::toChat;

    private static void toChat(CompactMessages.Out out) {
        var mc = Minecraft.getInstance();
        var p = mc == null ? null : mc.player;
        if (p == null) return;
        MutableComponent c = Component.literal(out.text());
        Style st = Style.EMPTY;
        if (out.hover() != null && !out.hover().isEmpty()) st = st.withHoverEvent(new HoverEvent.ShowText(Component.literal(String.join("\n", out.hover()))));
        if (out.command() != null && !out.command().isEmpty()) st = st.withClickEvent(new ClickEvent.RunCommand(out.command()));
        c.setStyle(st);
        boolean was = ChatBus.replaying;
        ChatBus.replaying = true;
        try {
            p.sendSystemMessage(c);
        } finally {
            ChatBus.replaying = was;
        }
    }

    private static String firstClickCommand(Component c) {
        for (Component sib : c.getSiblings()) {
            var ce = sib.getStyle().getClickEvent();
            if (ce instanceof ClickEvent.RunCommand rc) return rc.command();
            String deeper = firstClickCommand(sib);
            if (deeper != null) return deeper;
        }
        return null;
    }

    private static Component withRootStyle(Component original, String text) {
        // line goes out again as one piece, hover/click from its pieces stays
        return Component.literal(text).withStyle(PlayerFormat.events(original).applyTo(original.getStyle()));
    }

    // title and border only have their words, however hypixel colours them
    static String bestiaryLine(String formatted, String clean) {
        String t = clean.trim();
        if (t.equals("BESTIARY")) return CompactMessages.BESTIARY_TITLE;
        return t.matches("▬{20,}") ? CompactMessages.BESTIARY_BORDER : formatted;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!sb()) return ChatBus.PASS;
        // skyhanni's event.message is the styled form, its player patterns read the text as it came (ComponentSpan.getText)
        String formatted = io.muzy.sbc.core.LegacyText.of(line.component());
        String span = line.component().getString();
        String clean = line.plain();
        long now = System.currentTimeMillis();
        // allow-phase blockers, skyhanni's module order
        if (Features.on(POTIONS)) {
            CompactMessages.Out out = CompactMessages.potion(formatted, b(POTIONS, "clickableChatMessage", true));
            if (out != null) {
                send(out);
                return ChatBus.CANCEL;
            }
        }
        if (Features.on(BESTIARY)) {
            String bestiaryLine = bestiaryLine(formatted, clean);
            // CompactBestiaryChatMessage: opening border + blank line came before the words that say its this block
            if (bestiaryLine.equals(CompactMessages.BESTIARY_TITLE)) io.muzy.sbc.chat.ChatHistory.deleteRecent(2, 10, s -> s.isBlank() || s.trim().matches("▬{20,}"));
            if (CompactMessages.bestiary(bestiaryLine, firstClickCommand(line.component()), ChatCompact::send)) return ChatBus.CANCEL;
        }
        if (Features.on(EXPERIMENTS) && CompactMessages.experiment(formatted, now)) {
            flushAt = now + 100;
            return ChatBus.CANCEL;
        }
        if (Features.on(FAR_DEATHS) && !"CATACOMBS".equals(GameState.island) && !"KUUDRA".equals(GameState.island)
                && CompactMessages.hideFarDeath(clean, ownName(), n -> lastTimePlayerSeen.getOrDefault(n, Long.MIN_VALUE / 4), now, false)) return ChatBus.CANCEL;
        if (Features.on(PLAYER)) {
            PlayerChat.Kind kind = PlayerChat.kind(span, ownName());
            if (kind == PlayerChat.Kind.ALL && b(PLAYER, "chatFilter", false) && PlayerChat.shouldChatFilter(formatted) != null) return ChatBus.CANCEL;
            if (kind != PlayerChat.Kind.NONE && kind != PlayerChat.Kind.NPC && b(PLAYER, "playerRankHider", false)) {
                String cut = PlayerChat.hideRanks(formatted);
                if (!cut.equals(formatted)) return new ChatBus.Replace(withRootStyle(line.component(), cut));
            }
        }
        // modify-phase
        if (Features.on(PlayerFormat.ID)) {
            Component done = PlayerFormat.apply(line.component(), span, ownName());
            if (done != null) return new ChatBus.Replace(done);
        }
        if (Features.on(PET_RARITY)) {
            String r = CompactMessages.petRarity(formatted);
            if (r != null) return new ChatBus.Replace(withRootStyle(line.component(), r));
        }
        if (Features.on(COINS)) {
            String s = ShortenCoins.shorten(formatted);
            if (s != null) return new ChatBus.Replace(withRootStyle(line.component(), s));
        }
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !sb()) return;
        long now = System.currentTimeMillis();
        if (now >= flushAt) {
            flushAt = Long.MAX_VALUE;
            send(CompactMessages.experimentFlush());
        }
        if (Features.on(SB_XP)) {
            String bar = Vitals.actionBarRaw;
            if (!bar.equals(lastActionBar)) {
                lastActionBar = bar;
                String xp = CompactMessages.skyblockXp(bar);
                if (xp != null) send(new CompactMessages.Out("§b[SBC] §r" + xp, List.of(), null));
            }
        }
        // CompactExperimentRewards.onInventoryClose
        var screen = mc.gui.screen();
        boolean open = screen != null && mc.player.containerMenu instanceof ChestMenu;
        if (open) {
            int id = mc.player.containerMenu.containerId;
            if (id != lastMenuId) {
                lastMenuId = id;
                lastMenuWasExperiment = CompactMessages.isExperimentInventory(GameState.strip(screen.getTitle().getString()).trim());
            }
        } else if (lastMenuId != -1) {
            lastMenuId = -1;
            if (lastMenuWasExperiment) CompactMessages.onExperimentInventoryClose(now);
            lastMenuWasExperiment = false;
        }
        if (now - lastSecond >= 1000) {
            lastSecond = now;
            if (Features.on(FAR_DEATHS)) {
                for (Player p : mc.level.players()) {
                    if (p == mc.player || p.getUUID().version() == 2 || p.distanceTo(mc.player) > 25) continue;
                    lastTimePlayerSeen.put(GameState.strip(p.getName().getString()), now);
                }
            }
        }
    }

    public static void register() {
        PlayerChat.load();
        Features.add(new Feature(POTIONS, "Chat", "Compact potion messages", "The long BUFF! splash lines become one short \"Potion Effect! <effect> by <player>\" line (click for /effects).", "SkyHanni: Compact Potion Messages")
                .when(ChatCompact::sb)
                .option(Option.bool("clickableChatMessage", "Clickable", "Click the line to open /effects.", true)));
        Features.add(new Feature(BESTIARY, "Chat", "Compact bestiary", "The multi-line BESTIARY level-up block becomes one line with the details on hover (milestones get their own line).", "SkyHanni: Compact Bestiary Message")
                .when(ChatCompact::sb));
        Features.add(new Feature(EXPERIMENTS, "Chat", "Compact experiment rewards", "After an Experimentation Table menu, the claim line carries the rewards on hover instead of one line each.", "SkyHanni: Compact Experiment Rewards")
                .when(ChatCompact::sb));
        Features.add(new Feature(SB_XP, "Chat", "SkyBlock XP in chat", "Writes the action bar's \"+5 SkyBlock XP (...)\" into the chat when it appears.", "SkyHanni: SkyBlock XP in Chat")
                .when(ChatCompact::sb));
        Features.add(new Feature(PLAYER, "Chat", "Player messages", "Hides all-chat trade spam (lowballing, buying/selling, ...) and can strip ranks from player names.", "SkyHanni: Player chat filter, Player rank hider")
                .when(ChatCompact::sb)
                .option(Option.bool("chatFilter", "Hide trade spam", "All-chat lines on the list of lowballing, buying and selling.", false))
                .option(Option.bool("playerRankHider", "Hide ranks", "Show every player name in aqua without the rank.", false)));
        Features.add(new Feature(COINS, "Chat", "Shorten coin amounts", "Gold coin amounts in chat become 2.65M instead of 2,650,000.", "SkyHanni: Shorten Coin Amounts")
                .when(ChatCompact::sb).defaultOff());
        Features.add(new Feature(PET_RARITY, "Chat", "Pet rarity in drop messages", "PET DROP!, catch, claim and Oringo lines get the pet's rarity written before its name.", "SkyHanni: Rare Drop Messages (pet rarity)")
                .when(ChatCompact::sb));
        Features.add(new Feature(FAR_DEATHS, "Chat", "Hide far death messages", "Hides the death of players who were not within 25 blocks of you in the last 30 seconds (not in dungeons or Kuudra).", "SkyHanni: Hide Far Death Messages")
                .when(ChatCompact::sb).defaultOff());
        PlayerFormat.register();
        ChatBus.register(ChatBus.COMPACT - 8, "sh_compact", null, ChatCompact::onChat);
        Safe.endTick("ChatCompact.tick", ChatCompact::tick);
    }

    static List<String> emptyList() {
        return new ArrayList<>();
    }
}
