// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.visitor;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

// chat around visitors, from skyhanni GardenVisitorChat (hypixel's arrival line + visitor talk hidden, our
// own notice instead) and GardenVisitorCompactChat (an accepted offer block as one line, full block on hover)
public final class VisitorChat {
    public static final String FEATURE = "garden_visitor_chat";
    private static final AABB BARN_AREA = new AABB(35.5, 70, -4.5, -32.5, 100, -46.5);

    private static List<String> block = new ArrayList<>();
    private static final List<String> rewards = new ArrayList<>();
    private static String visitorName = "";
    private static long sendAt;

    private VisitorChat() {}

    private static boolean option(String key, boolean otherwise) {
        Feature f = Features.get(FEATURE);
        if (f == null) return otherwise;
        Option o = f.option(key);
        return o == null ? otherwise : o.bool();
    }

    static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §r" + text));
    }

    // GardenVisitorChat.sendArrivalNotification
    static void arrived(Visitors.Visitor v) {
        if (Visitors.sinceWorldSwitch() < 3000 || !Features.on(FEATURE)) return;
        var mc = Minecraft.getInstance();
        if (option("garden_visitor_chat.notify_title", true) && mc.gui != null && mc.gui.hud != null) mc.gui.hud.setTitle(Component.literal("§eNew Visitor"));
        if (option("garden_visitor_chat.notify_chat", true)) say(VisitorRepo.colored(v.name) + " §fis visiting your garden!");
    }

    // GardenVisitorTooltip.notifyFoundRewards
    static void foundRewards(Visitors.Visitor v) {
        if (!Features.on(VisitorBlock.FEATURE)) return;
        Feature f = Features.get(VisitorBlock.FEATURE);
        if (f != null && f.option("garden_visitor_block.notify_chat") != null && !f.option("garden_visitor_block.notify_chat").bool()) return;
        for (VisitorReward r : v.warned()) say("§fFound Visitor Reward " + r.displayName + "§f!");
    }

    // GardenVisitorChat.hideVisitorMessage
    static boolean hideVisitorMessage(String message) {
        Matcher m = Patterns.get("SH.garden.visitor.chat.visitorchat").matcher(message);
        if (!m.matches()) return false;
        String color = m.group("color");
        // not a visitor, probably jacob
        if (color == null || color.equals("§e")) return false;
        String name = m.group("name");
        VisitorRepo.Info info = VisitorRepo.all().get(name);
        if (info != null && info.showChatMessage()) return false;
        for (Visitors.Visitor v : Visitors.all()) if (v.name.equals(name)) return true;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return false;
        for (RemotePlayer p : mc.level.getEntitiesOfClass(RemotePlayer.class, BARN_AREA)) if (GameState.strip(p.getName().getString()).trim().equalsIgnoreCase(name)) return true;
        return false;
    }

    static ChatBus.Result onChat(ChatBus.Line line) {
        String message = LegacyText.of(line.component());
        if (Patterns.get("SH.garden.barn.skin.change").matcher(message).matches()) Visitors.barnSkinChanged();
        if (option("garden_visitor_chat.hide_arrival", true) && Patterns.get("SH.garden.visitor.chat.visitorarrive").matcher(message).matches()) return ChatBus.CANCEL;
        if (!Visitors.garden()) return ChatBus.PASS;
        if (option("garden_visitor_chat.hide_chat", true) && hideVisitorMessage(message)) return ChatBus.CANCEL;
        if (Features.on(VisitorCards.SHOPPING) && Patterns.get("SH.garden.visitor.chat.partialaccepted").matcher(message).matches())
            say("§eTalk to the visitor again to update the number of items needed!");
        if (option("garden_visitor_chat.compact", true) && compact(message.replace("§r", ""), message)) return ChatBus.CANCEL;
        return ChatBus.PASS;
    }

    // GardenVisitorCompactChat.handleChat, true = line goes into the block
    static boolean compact(String transformed, String message) {
        Matcher accepted = Patterns.get("SH.garden.visitor.compact.fullyaccepted").matcher(transformed);
        Matcher reward = Patterns.get("SH.garden.visitor.compact.visitorreward").matcher(transformed);
        boolean rewardsText = Patterns.get("SH.garden.visitor.compact.rewardstext").matcher(transformed).find();
        boolean isAccepted = accepted.matches(), isReward = reward.matches();
        if (!isAccepted && !isReward && !rewardsText) return false;
        if (isAccepted) {
            block = new ArrayList<>();
            rewards.clear();
            String color = accepted.group("color");
            visitorName = (color == null ? "§7" : color) + accepted.group("name");
        }
        if (visitorName.isBlank()) return false;
        if (isReward) addReward(reward);
        block.add(message);
        if (block.size() == 3) sendAt = System.currentTimeMillis() + 200;
        return true;
    }

    private static void addReward(Matcher m) {
        String rewardColor = m.group("rewardcolor"), amountColor = m.group("amountcolor"), amount = m.group("amount"), alt = m.group("altamount"), reward = m.group("reward");
        String color = rewardColor == null || rewardColor.isBlank() || rewardColor.equals("§7") ? (amountColor == null || amountColor.isBlank() ? "§f" : amountColor) : rewardColor;
        boolean discard = Patterns.get("SH.garden.visitor.compact.disregardrewardname").matcher(reward).matches();
        String amountText = amount != null ? (discard ? amount : amount + " ") : alt == null ? "" : alt + " ";
        rewards.add(color + amountText + (discard ? "" : reward));
    }

    // what createCompactVisitorMessage makes of it
    static String compactLine() {
        return "§6§lOFFER ACCEPTED §7w/§r" + visitorName + "§6§l!§r " + String.join("§7, ", rewards);
    }

    static void tick() {
        if (sendAt == 0 || System.currentTimeMillis() < sendAt) return;
        sendAt = 0;
        var p = Minecraft.getInstance().player;
        if (p != null && !visitorName.isBlank() && !block.isEmpty()) {
            boolean was = ChatBus.replaying;
            ChatBus.replaying = true;
            try {
                String hover = String.join("\n", block);
                p.sendSystemMessage(Component.literal(compactLine()).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover)))));
            } finally {
                ChatBus.replaying = was;
            }
        }
        visitorName = "";
        rewards.clear();
    }

    // for a test
    static void resetCompact() {
        block = new ArrayList<>();
        rewards.clear();
        visitorName = "";
        sendAt = 0;
    }

    static Feature feature() {
        return new Feature(FEATURE, "Garden", "Visitor chat", "A title and a chat line when a visitor arrives, in place of Hypixel's line. The visitors' talk is hidden, except for the few with long dialogues. An accepted offer becomes one line, with the whole block on hover.", "SkyHanni: Garden Visitor Chat")
                .option(Option.bool("garden_visitor_chat.notify_chat", "Notification in chat", "A line in chat when a new visitor is visiting your Garden.", true))
                .option(Option.bool("garden_visitor_chat.notify_title", "Notification title", "A title when a new visitor is visiting your Garden.", true))
                .option(Option.bool("garden_visitor_chat.hide_arrival", "Hide Hypixel's arrival line", "Hide the chat message from Hypixel that a new visitor has arrived.", true))
                .option(Option.bool("garden_visitor_chat.hide_chat", "Hide visitor talk", "Hide chat messages from the visitors in the Garden, except those with long dialogues.", true))
                .option(Option.bool("garden_visitor_chat.compact", "Compact accepted offers", "The rewards of an accepted offer as one line.", true));
    }
}
