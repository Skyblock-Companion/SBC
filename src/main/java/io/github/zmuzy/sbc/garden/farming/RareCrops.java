// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.farming;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.ui.Toasts;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker RareCropFilter: the "RARE CROP!" overbloom lines can stay, go, or become a toast
public final class RareCrops {
    public static final String ID = "garden_rare_crops";
    // SkyBlockIcons.OVERBLOOM is hypixel's icon U+E02B
    static final Pattern LINE = Pattern.compile("^RARE CROP!\\s+(?<crop>[\\w\\s]+)\\s+\\(\\+\\d+(?:\\.\\d+)?\\uE02B\\).*");

    private RareCrops() {}

    // crop the line names, or null
    static String crop(String clean) {
        Matcher m = LINE.matcher(clean);
        return m.matches() ? m.group("crop").trim() : null;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String how = GardenPack.choice(ID, "how", "Chat");
        if (how.equals("Chat")) return ChatBus.PASS;
        String crop = crop(GameState.strip(line.plain()));
        if (crop == null) return ChatBus.PASS;
        if (how.equals("Toast")) Toasts.push("Rare crop", crop, 0xFF9CE37D);
        return ChatBus.CANCEL;
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Rare crop lines", "What the RARE CROP! lines do: stay in chat, go away, or turn into a toast.", "Skyblocker: Hide Rare Crop Messages")
                .when(GardenPack::skyblock)
                .option(Option.choice("garden_rare_crops.how", "Rare crop lines", "", "Chat", "Chat", "Hidden", "Toast")));
        ChatBus.register(ChatBus.FILTER + 5, ID, f, RareCrops::onChat);
    }
}
