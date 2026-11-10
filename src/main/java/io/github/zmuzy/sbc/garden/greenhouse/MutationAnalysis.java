// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.garden.greenhouse;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.garden.GardenData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// which greenhouse mutations you have analyzed, from "ALL-IN ALOE ANALYSIS COMPLETE" in chat, per profile.
// a mutation is any item whose last line is Hypixel's "... MUTATION" rarity, and its tooltip says whether it is done
public final class MutationAnalysis {
    public static final String ID = "garden_mutation_analyzed";
    private static final Pattern DONE = Pattern.compile("^\\s*([A-Z0-9][A-Z0-9'\\- ]*?) ANALYSIS COMPLETE\\s*$");

    private MutationAnalysis() {}

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Analyzed mutations", "A greenhouse mutation's tooltip says whether you have analyzed it. It learns from the message when an analysis completes.", "")
                .tooltip(MutationAnalysis::tooltip));
        ChatBus.register(ChatBus.OBSERVE + 19, ID, f, MutationAnalysis::onChat);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String name = analyzed(GameState.strip(line.plain()));
        if (name != null && GardenData.get().analyzedMutations.add(name)) Store.markDirty();
        return ChatBus.PASS;
    }

    // "  ALL-IN ALOE ANALYSIS COMPLETE" -> "all-in aloe". pure, for tests
    static String analyzed(String plain) {
        Matcher m = DONE.matcher(plain);
        return m.matches() ? key(m.group(1)) : null;
    }

    static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    // a mutation by its rarity line: "LEGENDARY MUTATION", "RARE MUTATION". pure, for tests
    static boolean isMutation(List<String> plainLore) {
        for (int i = plainLore.size() - 1; i >= 0; i--) {
            String l = plainLore.get(i).trim();
            if (l.isEmpty()) continue;
            return l.endsWith(" MUTATION");
        }
        return false;
    }

    // the line under a mutation's name
    static String line(boolean done) {
        return done ? "§aAnalyzed" : "§7Not analyzed yet";
    }

    private static void tooltip(ItemStack st, List<Component> lines) {
        List<String> lore = LegacyText.lore(st).stream().map(GameState::strip).toList();
        if (!isMutation(lore)) return;
        String name = key(GameState.strip(st.getHoverName().getString()));
        lines.add(Math.min(1, lines.size()), Component.literal(line(GardenData.get().analyzedMutations.contains(name))));
    }
}
