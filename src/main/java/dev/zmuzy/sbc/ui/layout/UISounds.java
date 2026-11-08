// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.util.UISounds, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 * The interaction sound, assets/sbcompanion/sounds/ui_interaction.ogg, is owo-lib's own, from the same licence.
 */
package dev.zmuzy.sbc.ui.layout;

import java.util.function.Consumer;

public final class UISounds {
    enum Kind {BUTTON, INTERACTION}

    // sounds go here, tests listen here since they have no sound system
    static Consumer<Kind> sink = UISounds::toGame;

    private UISounds() {}

    public static void playButtonSound() {
        sink.accept(Kind.BUTTON);
    }

    public static void playInteractionSound() {
        sink.accept(Kind.INTERACTION);
    }

    private static void toGame(Kind kind) {
        if (!GameLink.hasSounds()) return;
        if (kind == Kind.BUTTON) GameLink.playButtonClick();
        else GameLink.playInteraction();
    }
}
