//? if >=26.3 {
// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;

// since 26.3 (sdl) typed letters only come while something asked for text input, and the game asks only for
// its own focused text boxes. our search fields draw themselves, so while any screen is open we keep asking,
// the way every key reached them before. the game turns it off again when the screen closes
public final class TextInput {
    private static final Object OWNER = new Object();

    private TextInput() {}

    public static void register() {
        Safe.afterInit("TextInput.afterInit", (client, screen, w, h) -> {
            keep(client);
            // a vanilla box that loses focus stops it for everyone, so ask again each tick
            Safe.afterTick("TextInput.afterTick", screen, s -> keep(client));
        });
    }

    private static void keep(Minecraft mc) {
        try {
            mc.textInputManager().startTextInput(OWNER);
        } catch (Throwable t) {
            Safe.caught("TextInput.keep", t);
        }
    }
}

//?}