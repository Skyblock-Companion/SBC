// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.Dump;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.network.chat.Component;

// every game message goes through ChatBus once via fabrics message events
// ALLOW decides if the line shows at all and remembers the bus answer, MODIFY hands back the rewrite if there is one
public final class ChatHooks {
    private static Component lastIn, lastOut;

    private ChatHooks() {}

    public static void register() {
        Safe.allowGame("ChatHooks.allowGame", (message, overlay) -> {
            if (overlay) {
                Safe.run("ChatHooks.actionBar", () -> actionBar(message));
                return true;
            }
            // read before the chat filters bc they might hide these two. a reader blowing up must not keep the line from them
            Safe.run("ChatHooks.readers", () -> {
                Dump.chat(message);
                String plainLine = dev.zmuzy.sbc.core.GameState.strip(message.getString()).trim();
                dev.zmuzy.sbc.item.StorageCache.onChat(plainLine);
                dev.zmuzy.sbc.ui.screens.WarpScreen.onChat(plainLine);
            });
            return allow(message);
        });
        Safe.modifyGame("ChatHooks.modifyGame", (message, overlay) -> {
            if (overlay) {
                boolean bars = dev.zmuzy.sbc.ui.hud.BarsElement.filtersActionBar(), skill = dev.zmuzy.sbc.ui.hud.SkillElement.hidesActionBar();
                boolean manaUse = dev.zmuzy.sbc.feature.Features.on(dev.zmuzy.sbc.chat.ChatExtras.MANA_USE);
                if (!bars && !skill && !manaUse) return message;
                String s = message.getString();
                String f = dev.zmuzy.sbc.core.ActionBarFilter.filter(s, bars && dev.zmuzy.sbc.ui.hud.BarsElement.showsHealth(), bars && dev.zmuzy.sbc.ui.hud.BarsElement.showsDefense(),
                        bars && dev.zmuzy.sbc.ui.hud.BarsElement.showsMana(), skill, "THE_RIFT".equals(dev.zmuzy.sbc.core.GameState.island));
                if (manaUse) f = dev.zmuzy.sbc.core.ActionBarFilter.dropManaUse(f);
                return f.equals(s) ? message : Component.literal(f);
            }
            return shown(message);
        });
    }

    private static void actionBar(Component message) {
        Dump.actionBar(message);
        String plain = dev.zmuzy.sbc.core.GameState.strip(message.getString());
        dev.zmuzy.sbc.core.Vitals.actionBarRaw = message.getString();
        dev.zmuzy.sbc.core.Vitals.actionBarAt = System.currentTimeMillis();
        dev.zmuzy.sbc.track.Tracker.onActionBar(plain);
        dev.zmuzy.sbc.core.Vitals.onActionBar(plain);
        dev.zmuzy.sbc.core.game.GameProfile.onActionBar(plain);
        dev.zmuzy.sbc.item.AbilityCooldowns.onActionBar(dev.zmuzy.sbc.core.LegacyText.of(message));
    }

    // forget the last rewrite first: if the chain throws, Safe lets the line through and shown has to give back
    // this line, not the rewrite of the one before
    static boolean allow(Component message) {
        lastIn = null;
        lastOut = null;
        Component out = ChatBus.process(message);
        lastIn = message;
        lastOut = out;
        return out != null;
    }

    // the line to show for one that allow let through
    static Component shown(Component message) {
        if (message != lastIn || lastOut == null) return message;
        return lastOut;
    }
}
