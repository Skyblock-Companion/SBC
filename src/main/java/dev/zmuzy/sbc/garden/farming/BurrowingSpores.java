// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.farming;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.garden.GardenPack;

// from skyhanni GardenBurrowingSporesNotifier: a title when burrowing spores drop while farming mushrooms.
// skyhanni can also blink the item, a title says the same
public final class BurrowingSpores {
    public static final String ID = "garden_burrowing_spores";

    private BurrowingSpores() {}

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (GardenPack.garden() && Patterns.get("SH.garden.burrowingspores.drop").matcher(LegacyText.of(line.component())).matches()) GardenPack.title("§9Burrowing Spores!", 2);
        return ChatBus.PASS;
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Burrowing Spores title", "A title when Burrowing Spores drop while you farm mushrooms.", "SkyHanni: Burrowing Spores").when(GardenPack::garden));
        ChatBus.register(ChatBus.OBSERVE + 18, ID, f, BurrowingSpores::onChat);
    }
}
