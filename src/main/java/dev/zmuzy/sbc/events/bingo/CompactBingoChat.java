// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.bingo;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.combat.Combat;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.events.EventPack;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// from skyhanni CompactBingoChat: skill, skyblock and collection level ups + new areas in fewer lines
// text the game has no pattern file for is written in the source the same way
public final class CompactBingoChat {
    public static final String ID = "bingo_compact_chat";

    private boolean inSkillLevelUp, inSkyBlockLevelUp, inCollectionLevelUp;
    private String collectionLevelUpLastLine;
    private int newArea;
    private long headAt;
    // a level up arrives all at once
    private static final long HEAD_MS = 2_000;
    // what a collection line that stays says first, as the game sent it
    String print;

    // onChat: does this line go. border by its colour (no resets), rest by words bc hypixel indents them
    // differently sometimes
    boolean blocks(String message, boolean hideBorder) {
        print = null;
        if (Patterns.get("SH.bingo.compactchat.border").matcher(message.replace("§r", "")).matches()) {
            inSkillLevelUp = inSkyBlockLevelUp = inCollectionLevelUp = false;
            return hideBorder;
        }
        String clean = GameState.strip(message);
        String words = clean.trim();
        // skyhanni waits for the border to end these. if it never comes the next line thats not part of them ends it,
        // or it'd eat all chat
        if (!clean.isEmpty() && !clean.startsWith(" ") || System.currentTimeMillis() - headAt > HEAD_MS) inSkillLevelUp = inSkyBlockLevelUp = inCollectionLevelUp = false;
        boolean block = skill(clean, words);
        block |= skyBlock(clean, words);
        block |= collection(message, clean, words);
        block |= area(clean, words);
        return block;
    }

    private boolean skill(String message, String words) {
        if (words.startsWith("SKILL LEVEL UP ")) {
            inSkillLevelUp = true;
            headAt = System.currentTimeMillis();
            return false;
        }
        return inSkillLevelUp && !message.contains("Access to") && !message.endsWith(" Enchantment");
    }

    private boolean skyBlock(String message, String words) {
        if (words.startsWith("SKYBLOCK LEVEL UP")) {
            inSkyBlockLevelUp = true;
            headAt = System.currentTimeMillis();
            return false;
        }
        if (!inSkyBlockLevelUp) return false;
        if (words.equals("REWARDS")) return true;
        if (EventPack.plain("SH.bingo.compactchat.health").matcher(message).matches()) return true;
        if (EventPack.plain("SH.bingo.compactchat.strength").matcher(message).matches()) return true;
        if (words.equals("Access to Bazaar") || words.equals("Access to Community Shop")) return true;
        return words.equals("+Auto-pickup block and mob drops");
    }

    private boolean collection(String message, String clean, String words) {
        if (words.startsWith("COLLECTION LEVEL UP ")) {
            inCollectionLevelUp = true;
            headAt = System.currentTimeMillis();
            return false;
        }
        if (!inCollectionLevelUp) return false;
        if (clean.contains("Trade") || clean.contains("Recipe")) {
            String text = clean.replace(" ", "");
            if (text.equals("Trade") || text.equals("Recipe")) print = collectionLevelUpLastLine;
            return false;
        }
        collectionLevelUpLastLine = message;
        return true;
    }

    private boolean area(String message, String words) {
        if (words.equals("NEW AREA DISCOVERED!")) {
            newArea = 1;
            return false;
        }
        if (!message.isEmpty()) {
            if (newArea == 1) {
                newArea = 2;
                return false;
            }
            if (newArea == 2) {
                if (message.startsWith("   ")) return true;
                newArea = 0;
            }
        }
        return false;
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Chat", "Compact Bingo level ups", "On a Bingo profile, skill, SkyBlock and collection level ups and new areas take fewer lines of chat.", "SkyHanni: Compact Chat Messages")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)
                .option(Option.bool("bingo_compact_chat.hide_border", "Hide the border lines", "", true))
                .option(Option.bool("bingo_compact_chat.outside", "Outside Bingo too", "", false)));
        CompactBingoChat state = new CompactBingoChat();
        ChatBus.register(ChatBus.FILTER + 27, ID, f, line -> {
            if (!BingoCard.onBingo() && !Combat.opt(ID, "outside")) return ChatBus.PASS;
            boolean block = state.blocks(LegacyText.of(line.component()), Combat.opt(ID, "hide_border"));
            if (state.print != null) {
                var p = Minecraft.getInstance().player;
                if (p != null) p.sendSystemMessage(Component.literal(state.print));
            }
            return block ? ChatBus.CANCEL : ChatBus.PASS;
        });
    }
}
