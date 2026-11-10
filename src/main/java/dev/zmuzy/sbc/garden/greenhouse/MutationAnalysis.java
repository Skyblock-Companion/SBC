// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.garden.greenhouse;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.garden.GardenData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// which greenhouse mutations you have analyzed, from "CHOCOBERRY ANALYSIS COMPLETE" in chat, per profile. a mutation's
// tooltip then says whether it is done
public final class MutationAnalysis {
    public static final String ID = "garden_mutation_analyzed";
    private static final Pattern DONE = Pattern.compile("^\\s*([A-Z][A-Z' ]+?) ANALYSIS COMPLETE\\s*$");

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

    // "  CHOCOBERRY ANALYSIS COMPLETE" -> "chocoberry", for a greenhouse crop only. pure, for tests
    static String analyzed(String plain) {
        Matcher m = DONE.matcher(plain);
        if (!m.matches()) return null;
        String key = key(m.group(1));
        for (GreenhouseCrops.Crop c : GreenhouseCrops.ALL) if (key(c.name()).equals(key)) return key;
        return null;
    }

    static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    // the line under a mutation's name
    static String line(boolean done) {
        return done ? "§aAnalyzed" : "§7Not analyzed yet";
    }

    private static void tooltip(ItemStack st, List<Component> lines) {
        String name = key(GameState.strip(st.getHoverName().getString()));
        for (GreenhouseCrops.Crop c : GreenhouseCrops.ALL) {
            if (!key(c.name()).equals(name)) continue;
            lines.add(Math.min(1, lines.size()), Component.literal(line(GardenData.get().analyzedMutations.contains(name))));
            return;
        }
    }
}
