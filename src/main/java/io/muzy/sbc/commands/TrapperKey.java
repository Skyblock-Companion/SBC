// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.commands;

//? if >=26.3 {
import com.mojang.blaze3d.platform.InputConstants;
//?}
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

// from skyhanni TrevorFeatures.onKeyPress: trapper key warps to trevors den, or says YES to his question within 5 sec
// only when you press it. key starts unbound
public final class TrapperKey {
    public static final String ID = "trevor_key";
    private static final Patterns.P CLICK_OPTION = Patterns.p("SH.misc.trevor.clickoption.colorless");
    private static KeyMapping key;
    private static String lastPrompt = "";
    private static long lastPromptAt, lastWarped;

    private TrapperKey() {}

    public static void keys(KeyMapping.Category category) {
        //? if >=26.3 {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.trapper", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));
        //?} else {
        /*key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.trapper", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue(), category));
        *///?}
    }

    // skyhanni clickOptionPattern: what the [YES] part runs, after its first word. null if nothing says YES
    static String prompt(Component line) {
        for (Component sibling : line.getSiblings()) {
            if (sibling.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run && run.command().contains("YES")) {
                int space = run.command().indexOf(' ');
                return space < 0 ? "" : run.command().substring(space + 1);
            }
        }
        return null;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (CLICK_OPTION.matcher(line.plain()).find()) {
            String p = prompt(line.component());
            if (p != null) {
                lastPrompt = p;
                lastPromptAt = System.currentTimeMillis();
            }
        }
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        if (key == null) return;
        boolean pressed = false;
        while (key.consumeClick()) pressed = true;
        if (!pressed || mc.gui.screen() != null || !Features.on(ID) || !"THE_FARMING_ISLANDS".equals(GameState.island)) return;
        var conn = mc.getConnection();
        if (conn == null) return;
        Feature f = Features.get(ID);
        long now = System.currentTimeMillis();
        if (f.option(ID + ".accept").bool()) {
            long since = now - lastPromptAt;
            if (since > 200 && since < 5000) {
                lastPromptAt = 0;
                conn.sendCommand("chatprompt " + lastPrompt);
                lastPrompt = "";
                lastWarped = now;
                return;
            }
        }
        if (f.option(ID + ".warp").bool() && now - lastWarped > 3000) {
            conn.sendCommand("warp trapper");
            lastWarped = now;
        }
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Farming Islands", "Trapper key", "On the Farming Islands, the Trapper key in Controls warps to Trevor's Den, or says yes to his question. It starts unbound.", "SkyHanni: Trapper Hotkey").defaultOff()
                .when(() -> "THE_FARMING_ISLANDS".equals(GameState.island) || SbcMod.DEV)
                .option(Option.bool(ID + ".warp", "Warp to the Trapper", "", true))
                .option(Option.bool(ID + ".accept", "Accept the quest", "Within 5 seconds of his question.", true)));
        ChatBus.register(ChatBus.OBSERVE + 33, ID, f, TrapperKey::onChat);
        Safe.endTick("TrapperKey.tick", TrapperKey::tick);
    }
}
