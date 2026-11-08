// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.event.MouseDown with
 * util.EventStream and EventSource folded into it, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package dev.zmuzy.sbc.ui.layout;

import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

public interface MouseDown {
    boolean onMouseDown(MouseButtonEvent click, boolean doubled);

    // subscribers of one component. all of them hear the click, handled if any handled it
    final class Source {
        private final List<MouseDown> subscribers = new ArrayList<>();

        public void subscribe(MouseDown subscriber) {
            this.subscribers.add(subscriber);
        }

        boolean fire(MouseButtonEvent click, boolean doubled) {
            var anyTriggered = false;
            for (var subscriber : this.subscribers) {
                anyTriggered |= subscriber.onMouseDown(click, doubled);
            }
            return anyTriggered;
        }
    }
}
