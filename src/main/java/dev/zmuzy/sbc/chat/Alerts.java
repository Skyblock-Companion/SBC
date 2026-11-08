// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// alerts from chat: pests showing up in the garden. just a toast + sound, nothing done for you
public final class Alerts {
    private static final dev.zmuzy.sbc.core.Patterns.P PESTS_MANY = dev.zmuzy.sbc.core.Patterns.p("Alerts.PESTS_MANY");
    private static final dev.zmuzy.sbc.core.Patterns.P PEST_ONE = dev.zmuzy.sbc.core.Patterns.p("Alerts.PEST_ONE");

    private Alerts() {}

    public static void register() {
        Feature pests = Features.add(new Feature("alert_pests", "Garden", "Pest toast", "A toast and a sound when pests spawn on your Garden, with the plot name.", "SkyHanni: pest spawn")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        ChatBus.register(ChatBus.OBSERVE + 1, "alert_pests", pests, line -> {
            String t = line.plain().trim();
            Matcher m = PESTS_MANY.matcher(t);
            String text = null;
            if (m.find()) text = m.group(1) + " pests in " + m.group(2);
            else if ((m = PEST_ONE.matcher(t)).find()) text = "A pest in " + m.group(1);
            if (text != null) {
                Toasts.push("Pests", text, 0xFF97C459);
                var mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.2f);
            }
            return ChatBus.PASS;
        });
    }
}
